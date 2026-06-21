package com.xiaomi.xmsf.pushbroker;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.ArrayMap;

import androidx.annotation.Nullable;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.InternalMessenger;
import com.nihility.service.XMPushServiceAbility;
import com.nihility.utils.RegistrationHelper;
import com.xiaomi.push.service.PullAllApplicationDataFromServerJob;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.push.service.XMPushServiceMessenger;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.BuildConfig;
import com.xiaomi.xmsf.SettingUtils;
import com.xiaomi.xmsf.push.control.PushControllerUtils;
import com.xiaomi.xmsf.utils.ConfigCenter;

import java.util.List;

import lineageos.push.IPushBrokerHost;
import lineageos.push.IPushProviderService;
import lineageos.push.PushBrokerConstants;
import lineageos.push.PushProviderInfo;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipush.provider.db.RegisteredApplicationDb;
import top.trumeet.mipush.provider.entities.RegisteredApplication;

public class MiPushProviderService extends Service {
    private static final Logger LOGGER = XLog.tag("MiPushProviderService").build();

    private static final String PROVIDER_ID = "com.xiaomi.xmsf.mipush";
    private static final int API_VERSION = 4;
    private static final String APP_IDENTITY_PACKAGE_NAME = "packageName";
    private static final String SETTINGS_ACTIVITY =
            "top.trumeet.mipushframework.main.AdvancedSettingsPage";
    private static final String DIAGNOSTICS_ACTIVITY =
            "top.trumeet.mipushframework.main.MainPage";
    private static final String MIPUSH_RECEIVE_MESSAGE_ACTION =
            "com.xiaomi.mipush.RECEIVE_MESSAGE";
    private static final String MIPUSH_XM_PUSH_SERVICE =
            "com.xiaomi.push.service.XMPushService";
    private static final String MIPUSH_XM_JOB_SERVICE =
            "com.xiaomi.push.service.XMJobService";
    private static final String MIPUSH_PING_RECEIVER =
            "com.xiaomi.push.service.receivers.PingReceiver";
    private static final String[] CAPABILITIES = new String[] {
            "vendor_registration", "provider_settings_ui", "provider_diagnostics_ui"
    };
    private static final String STATUS_CONNECTING = "connecting";
    private static final String STATUS_CONNECTED = "connected";
    private static final String STATUS_DISCONNECTED = "disconnected";
    private static final int PER_USER_RANGE = 100000;
    private static final long APP_REGISTERED_EVENT_DEDUPE_MILLIS = 60_000L;
    private static final String HEALTH_REASON_PROVIDER_DISABLED = "provider_disabled";
    private static final String HEALTH_REASON_PUSH_DISABLED = "push_disabled";
    private static final String HEALTH_REASON_PUSH_NOT_REGISTERED = "push_not_registered";

    private final Object mLock = new Object();
    private final ArrayMap<String, Long> mLastAppRegisteredEventAt = new ArrayMap<>();
    private static volatile MiPushProviderService sInstance;

    private IPushBrokerHost mHost;
    private boolean mInitialized;
    private boolean mEnabled;
    private int mLastBootPhase = -1;
    private String mLastPreparedPackage;
    private String mLastTokenRequestPackage;
    private String mConnectionStatus = "unknown";
    private long mLastConnectedAt;
    private long mLastDisconnectedAt;
    private long mLastRecoverAttemptAt;
    private int mConsecutiveRecoverFailures;
    private boolean mRecoverInFlight;
    private String mLastRecoverReason;

    private final BroadcastReceiver mConnectionStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            final String status = intent.getStringExtra("status");
            final int reason = intent.getIntExtra("reason", -1);
            onConnectionStatusChanged(status, reason);
        }
    };

    private final IBinder mBinder = new IPushProviderService.Stub() {
        @Override
        public PushProviderInfo getProviderInfo() {
            return buildProviderInfo();
        }

        @Override
        public Bundle getHealth() {
            final Bundle health = new Bundle();
            final boolean pushEnabled = PushControllerUtils.isPrefsEnable(
                    MiPushProviderService.this);
            final boolean pushRegistered = PushControllerUtils.pushRegistered(
                    MiPushProviderService.this);
            final BusinessHealth businessHealth = resolveBusinessHealth(pushEnabled, pushRegistered);
            synchronized (mLock) {
                health.putBoolean("initialized", mInitialized);
                health.putBoolean("enabled", mEnabled);
                health.putLong(PushBrokerConstants.HEALTH_TIMESTAMP,
                        System.currentTimeMillis());
                health.putInt(PushBrokerConstants.HEALTH_API_VERSION, API_VERSION);
                health.putBoolean(PushBrokerConstants.HEALTH_PROVIDER_ENABLED, mEnabled);
                health.putInt("lastBootPhase", mLastBootPhase);
                health.putString("lastPreparedPackage", mLastPreparedPackage);
                health.putString("lastTokenRequestPackage", mLastTokenRequestPackage);
                health.putString(PushBrokerConstants.HEALTH_CONNECTION_STATUS,
                        mConnectionStatus);
                health.putLong(PushBrokerConstants.HEALTH_LAST_CONNECTED_AT,
                        mLastConnectedAt);
                health.putLong(PushBrokerConstants.HEALTH_LAST_DISCONNECTED_AT,
                        mLastDisconnectedAt);
                health.putLong(PushBrokerConstants.HEALTH_LAST_RECOVER_ATTEMPT_AT,
                        mLastRecoverAttemptAt);
                health.putInt(PushBrokerConstants.HEALTH_CONSECUTIVE_RECOVER_FAILURES,
                        mConsecutiveRecoverFailures);
                health.putBoolean(PushBrokerConstants.HEALTH_BUSINESS_HEALTHY,
                        businessHealth.healthy);
                if (!businessHealth.healthy) {
                    health.putString(PushBrokerConstants.HEALTH_BUSINESS_DEGRADED_REASON,
                            businessHealth.degradedReason);
                }
            }
            health.putBoolean("pushEnabledPreference", pushEnabled);
            health.putBoolean("pushRegistered", pushRegistered);
            final String regId = MiPushClient.getRegId(MiPushProviderService.this);
            if (!TextUtils.isEmpty(regId)) {
                health.putString("regId", regId);
            }
            appendRegistrationSnapshot(health);
            appendConfigSnapshot(health);
            return health;
        }

        @Override
        public int initialize(Bundle brokerContext) {
            if (brokerContext == null) {
                return PushBrokerConstants.RESULT_BAD_REQUEST;
            }
            final int brokerApiVersion = brokerContext.getInt("broker_api_version", 0);
            if (brokerApiVersion != API_VERSION) {
                return PushBrokerConstants.RESULT_UNSUPPORTED;
            }
            if (!TextUtils.equals(PROVIDER_ID, brokerContext.getString("provider_id"))) {
                return PushBrokerConstants.RESULT_BAD_REQUEST;
            }
            final IBinder hostBinder = brokerContext.getBinder("host_binder");
            if (hostBinder == null) {
                return PushBrokerConstants.RESULT_BAD_REQUEST;
            }

            synchronized (mLock) {
                mHost = IPushBrokerHost.Stub.asInterface(hostBinder);
                mInitialized = true;
            }
            LOGGER.i("Broker initialized provider");
            reportEvent("provider_initialized");
            return PushBrokerConstants.RESULT_OK;
        }

        @Override
        public int enable() {
            synchronized (mLock) {
                if (!mInitialized) {
                    return PushBrokerConstants.RESULT_NOT_READY;
                }
                mEnabled = true;
            }

            LOGGER.i("Enabling MiPush provider");
            ProviderActivationStore.setProviderEnabled(MiPushProviderService.this, true);
            PushControllerUtils.setAllEnable(true, MiPushProviderService.this);
            requestConnectionStatusSnapshot();
            reportEvent("provider_enabled");
            return PushBrokerConstants.RESULT_OK;
        }

        @Override
        public int disable() {
            synchronized (mLock) {
                mEnabled = false;
            }
            LOGGER.i("Disabling MiPush provider");
            ProviderActivationStore.setProviderEnabled(MiPushProviderService.this, false);
            SettingUtils.stopMiPushForegroundService(MiPushProviderService.this);
            PushControllerUtils.setAllEnable(false, MiPushProviderService.this);
            reportEvent("provider_disabled");
            return PushBrokerConstants.RESULT_OK;
        }

        @Override
        public int onBootPhase(int phase) {
            synchronized (mLock) {
                mLastBootPhase = phase;
            }
            LOGGER.i("Broker boot phase=" + phase);
            return PushBrokerConstants.RESULT_OK;
        }

        @Override
        public int onSystemEvent(Bundle event) {
            final String eventType = event == null ? null
                    : event.getString(PushBrokerConstants.SYSTEM_EVENT_TYPE);
            LOGGER.i("Broker system event=" + eventType);
            if (PushBrokerConstants.SYSTEM_EVENT_NETWORK_AVAILABLE.equals(eventType)) {
                performRecoverConnection("network_available", false);
                reportEvent("provider_network_available");
            } else if (PushBrokerConstants.SYSTEM_EVENT_RECOVER_CONNECTION.equals(eventType)) {
                performRecoverConnection("broker_recover_connection", true);
                reportEvent("provider_recover_connection");
            } else if (PushBrokerConstants.SYSTEM_EVENT_NETWORK_LOST.equals(eventType)) {
                reportEvent("provider_network_lost");
            } else if (PushBrokerConstants.SYSTEM_EVENT_CANCEL_PROVIDER_NOTIFICATION.equals(eventType)) {
                cancelProviderNotification(event);
            }
            return PushBrokerConstants.RESULT_OK;
        }

        @Override
        public int supportsApp(Bundle appIdentity) {
            final String packageName = getPackageName(appIdentity);
            final boolean supported = supportsPackage(packageName);
            LOGGER.i("supportsApp package=" + packageName + " result=" + supported);
            return supported ? PushBrokerConstants.RESULT_OK
                    : PushBrokerConstants.RESULT_UNSUPPORTED;
        }

        @Override
        public int prepareApp(Bundle appIdentity) {
            final String packageName = getPackageName(appIdentity);
            synchronized (mLock) {
                mLastPreparedPackage = packageName;
            }
            if (TextUtils.isEmpty(packageName)) {
                return PushBrokerConstants.RESULT_BAD_REQUEST;
            }
            if (!TextUtils.isEmpty(packageName)) {
                LOGGER.i("Preparing app " + packageName + " (warm-up only)");
                RegisteredApplicationDb.registerApplication(packageName);
            }
            return PushBrokerConstants.RESULT_OK;
        }

        @Override
        public int canSyncApp(Bundle appIdentity, Bundle extras) {
            return canSyncAppStateResult(appIdentity,
                    extras == null ? null
                            : extras.getString(PushBrokerConstants.SYSTEM_EVENT_SYNC_REASON));
        }

        @Override
        public int revokeApp(Bundle appIdentity) {
            final String packageName = getPackageName(appIdentity);
            synchronized (mLock) {
                if (TextUtils.equals(mLastPreparedPackage, packageName)) {
                    mLastPreparedPackage = null;
                }
            }
            if (!TextUtils.isEmpty(packageName)) {
                LOGGER.i("Revoking app " + packageName);
                final RegisteredApplication application =
                        RegisteredApplicationDb.getRegisteredApplication(packageName);
                if (application != null) {
                    application.setRegisteredType(RegisteredApplication.RegisteredType.Unregistered);
                    RegisteredApplicationDb.update(application);
                }
            }
            return PushBrokerConstants.RESULT_OK;
        }

        @Override
        public int requestToken(Bundle appIdentity, Bundle extras) {
            final String packageName = getPackageName(appIdentity);
            synchronized (mLock) {
                mLastTokenRequestPackage = packageName;
            }
            if (TextUtils.isEmpty(packageName)) {
                return PushBrokerConstants.RESULT_BAD_REQUEST;
            }
            LOGGER.i("Requesting token for " + packageName);
            new RegistrationHelper(MiPushProviderService.this, packageName)
                    .deleteRegistrationInfoAndRetryForceRegister();
            return PushBrokerConstants.RESULT_OK;
        }

        @Override
        public int syncApp(Bundle appIdentity, Bundle extras) {
            final String reason = extras == null ? null
                    : extras.getString(PushBrokerConstants.SYSTEM_EVENT_SYNC_REASON);
            return syncAppStateResult(appIdentity, reason);
        }

        @Override
        public Bundle dumpState() {
            final Bundle bundle = getHealth();
            bundle.putParcelable("providerInfo", buildProviderInfo());
            bundle.putString("pushServiceClass",
                    com.xiaomi.xmsf.push.service.XMPushService.class.getName());
            bundle.putString("settingsActivity", SETTINGS_ACTIVITY);
            bundle.putString("diagnosticsActivity", DIAGNOSTICS_ACTIVITY);
            final List<RegisteredApplication> apps = getRegisteredApplications();
            final String[] packages = new String[apps.size()];
            for (int i = 0; i < apps.size(); i++) {
                packages[i] = apps.get(i).getPackageName();
            }
            bundle.putStringArray("registeredPackages", packages);
            return bundle;
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        registerReceiver(mConnectionStatusReceiver,
                new IntentFilter(XMPushServiceMessenger.IntentSetConnectionStatus));
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        unregisterReceiver(mConnectionStatusReceiver);
        super.onDestroy();
    }

    private PushProviderInfo buildProviderInfo() {
        return new PushProviderInfo(
                PROVIDER_ID,
                "MiPush Framework",
                getPackageName(),
                new ComponentName(this, MiPushProviderService.class).flattenToShortString(),
                PushProviderInfo.PROVIDER_TYPE_NATIVE,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                API_VERSION,
                PushProviderInfo.TRUST_USER_APPROVED,
                SETTINGS_ACTIVITY,
                DIAGNOSTICS_ACTIVITY,
                CAPABILITIES);
    }

    private void reportEvent(String eventName) {
        reportEvent(eventName, null);
    }

    public static void reportAppRegistered(String packageName) {
        final MiPushProviderService service = sInstance;
        if (service == null || TextUtils.isEmpty(packageName)) {
            return;
        }
        final long now = System.currentTimeMillis();
        synchronized (service.mLock) {
            final Long lastReportedAt = service.mLastAppRegisteredEventAt.get(packageName);
            if (lastReportedAt != null
                    && now - lastReportedAt < APP_REGISTERED_EVENT_DEDUPE_MILLIS) {
                LOGGER.i("Skip duplicate app registered event package=" + packageName
                        + " remainingMillis="
                        + (APP_REGISTERED_EVENT_DEDUPE_MILLIS - (now - lastReportedAt)));
                return;
            }
            service.mLastAppRegisteredEventAt.put(packageName, now);
        }
        final Bundle event = new Bundle();
        event.putString(PushBrokerConstants.PROVIDER_EVENT_PACKAGE_NAME, packageName);
        event.putInt(PushBrokerConstants.PROVIDER_EVENT_USER_ID, service.resolveUserId(packageName));
        event.putLong(PushBrokerConstants.PROVIDER_EVENT_TIMESTAMP, now);
        service.reportEvent(PushBrokerConstants.EVENT_PROVIDER_APP_REGISTERED, event);
        LOGGER.i("Reported app registered package=" + packageName);
    }

    private void reportEvent(String eventName, @Nullable Bundle extras) {
        final IPushBrokerHost host;
        synchronized (mLock) {
            host = mHost;
        }
        if (host == null) {
            return;
        }

        final Bundle event = new Bundle();
        event.putString(PushBrokerConstants.PROVIDER_EVENT_PROVIDER_ID, PROVIDER_ID);
        event.putString(PushBrokerConstants.PROVIDER_EVENT_NAME, eventName);
        if (extras != null) {
            event.putAll(extras);
        }
        try {
            host.reportEvent(event);
        } catch (RemoteException e) {
            LOGGER.w("reportEvent failed", e);
        }
    }

    private BusinessHealth resolveBusinessHealth(boolean pushEnabled, boolean pushRegistered) {
        synchronized (mLock) {
            if (!mEnabled) {
                return BusinessHealth.degraded(HEALTH_REASON_PROVIDER_DISABLED);
            }
        }
        if (!pushEnabled) {
            return BusinessHealth.degraded(HEALTH_REASON_PUSH_DISABLED);
        }
        if (!pushRegistered) {
            return BusinessHealth.degraded(HEALTH_REASON_PUSH_NOT_REGISTERED);
        }
        try {
            top.trumeet.mipush.provider.DatabaseUtils.requireDaoSession(
                    Utils.getApplication());
        } catch (RuntimeException e) {
            LOGGER.w("Business health degraded because database is unavailable", e);
            return BusinessHealth.degraded(
                    PushBrokerConstants.HEALTH_DEGRADED_REASON_STORAGE_ERROR);
        }
        try {
            RegisteredApplicationDb.getList(null);
        } catch (RuntimeException e) {
            LOGGER.w("Business health degraded because app registry is unavailable", e);
            return BusinessHealth.degraded(
                    PushBrokerConstants.HEALTH_DEGRADED_REASON_APP_REGISTRY_UNAVAILABLE);
        }
        return BusinessHealth.healthy();
    }

    private static final class BusinessHealth {
        final boolean healthy;
        final String degradedReason;

        private BusinessHealth(boolean healthy, String degradedReason) {
            this.healthy = healthy;
            this.degradedReason = degradedReason;
        }

        static BusinessHealth healthy() {
            return new BusinessHealth(true, null);
        }

        static BusinessHealth degraded(String degradedReason) {
            return new BusinessHealth(false, degradedReason);
        }
    }

    private void onConnectionStatusChanged(String status, int reasonCode) {
        if (TextUtils.isEmpty(status)) {
            return;
        }

        final long now = System.currentTimeMillis();
        boolean stopForeground = false;
        String foregroundStatus = null;
        synchronized (mLock) {
            mConnectionStatus = status;
            if (STATUS_CONNECTED.equals(status)) {
                mLastConnectedAt = now;
                mConsecutiveRecoverFailures = 0;
                if (mRecoverInFlight) {
                    final Bundle recoverEvent = new Bundle();
                    recoverEvent.putLong(PushBrokerConstants.PROVIDER_EVENT_TIMESTAMP, now);
                    recoverEvent.putString(PushBrokerConstants.PROVIDER_EVENT_REASON_NAME,
                            mLastRecoverReason);
                    reportEvent(PushBrokerConstants.EVENT_PROVIDER_RECOVER_SUCCEEDED, recoverEvent);
                    mRecoverInFlight = false;
                    mLastRecoverReason = null;
                }
                stopForeground = true;
            } else if (STATUS_DISCONNECTED.equals(status)) {
                mLastDisconnectedAt = now;
                if (mLastRecoverAttemptAt > 0L) {
                    mConsecutiveRecoverFailures++;
                    foregroundStatus = getRecoverFailureStatusText(mConsecutiveRecoverFailures);
                }
            } else if (STATUS_CONNECTING.equals(status)) {
                foregroundStatus = getString(R.string.notification_connecting);
            }
        }
        if (stopForeground) {
            SettingUtils.stopMiPushForegroundService(this);
        } else if (!TextUtils.isEmpty(foregroundStatus)) {
            SettingUtils.updateMiPushForegroundStatus(this, foregroundStatus);
        }
        final Bundle event = new Bundle();
        event.putString(PushBrokerConstants.PROVIDER_EVENT_STATUS, status);
        event.putLong(PushBrokerConstants.PROVIDER_EVENT_TIMESTAMP, now);
        event.putInt(PushBrokerConstants.PROVIDER_EVENT_REASON_CODE, reasonCode);
        reportEvent(PushBrokerConstants.EVENT_PROVIDER_HEALTH_CHANGED, event);
        LOGGER.i("Connection status changed to " + status + " reason=" + reasonCode);
    }

    public static void reportBusinessNotificationPosted(XmPushActionContainer container,
            int notificationId, @Nullable String notificationTag,
            @Nullable Notification notification) {
        final MiPushProviderService service = sInstance;
        if (service == null || container == null) {
            return;
        }
        service.onBusinessNotificationPosted(container, notificationId, notificationTag,
                notification);
    }

    private void onBusinessNotificationPosted(XmPushActionContainer container, int notificationId,
            @Nullable String notificationTag, @Nullable Notification notification) {
        final String packageName = container.getPackageName();
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        final PushMetaInfo metaInfo = container.getMetaInfo();
        final Bundle event = new Bundle();
        event.putString(PushBrokerConstants.PROVIDER_EVENT_PACKAGE_NAME, packageName);
        event.putInt(PushBrokerConstants.PROVIDER_EVENT_USER_ID, resolveUserId(packageName));
        event.putLong(PushBrokerConstants.PROVIDER_EVENT_TIMESTAMP, System.currentTimeMillis());
        event.putString(PushBrokerConstants.PROVIDER_EVENT_PROVIDER_NOTIFICATION_TAG,
                notificationTag);
        event.putInt(PushBrokerConstants.PROVIDER_EVENT_PROVIDER_NOTIFICATION_ID, notificationId);
        if (metaInfo != null) {
            if (!TextUtils.isEmpty(metaInfo.getId())) {
                event.putString(PushBrokerConstants.PROVIDER_EVENT_MESSAGE_ID, metaInfo.getId());
            }
            if (metaInfo.getExtra() != null) {
                final String jobKey = metaInfo.getExtra().get("jobkey");
                if (!TextUtils.isEmpty(jobKey)) {
                    event.putString(PushBrokerConstants.PROVIDER_EVENT_JOB_KEY, jobKey);
                }
            }
            event.putInt(PushBrokerConstants.PROVIDER_EVENT_NOTIFY_ID, metaInfo.getNotifyId());
        }
        event.putInt(PushBrokerConstants.PROVIDER_EVENT_TITLE_HASH,
                resolveNotificationTitleHash(notification, metaInfo));
        event.putInt(PushBrokerConstants.PROVIDER_EVENT_TEXT_HASH,
                resolveNotificationTextHash(notification, metaInfo));
        reportEvent(PushBrokerConstants.EVENT_PROVIDER_NOTIFICATION_POSTED, event);
        LOGGER.i("Reported business notification package=" + packageName
                + " notificationId=" + notificationId + " tag=" + notificationTag
                + " titleHash=" + event.getInt(PushBrokerConstants.PROVIDER_EVENT_TITLE_HASH)
                + " textHash=" + event.getInt(PushBrokerConstants.PROVIDER_EVENT_TEXT_HASH));
    }

    private void performRecoverConnection(String reason, boolean forceServiceStart) {
        final long now = System.currentTimeMillis();
        synchronized (mLock) {
            mLastRecoverAttemptAt = now;
            mRecoverInFlight = true;
            mLastRecoverReason = reason;
        }
        LOGGER.i("Recovering MiPush connection reason=" + reason
                + " forceServiceStart=" + forceServiceStart);
        final Bundle event = new Bundle();
        event.putLong(PushBrokerConstants.PROVIDER_EVENT_TIMESTAMP, now);
        event.putString(PushBrokerConstants.PROVIDER_EVENT_REASON_NAME, reason);
        reportEvent(PushBrokerConstants.EVENT_PROVIDER_RECOVER_STARTED, event);
        final String statusText = getForegroundStatusForRecoverReason(reason);
        SettingUtils.startMiPushServiceAsForegroundService(this, statusText);
        SettingUtils.sendXMPPReconnectRequest(this);
        requestConnectionStatusSnapshot();
    }

    private String getForegroundStatusForRecoverReason(String reason) {
        if ("broker_recover_connection".equals(reason)) {
            return getString(R.string.notification_recovering);
        }
        if ("network_available".equals(reason)) {
            return getString(R.string.notification_connecting);
        }
        return getString(R.string.notification_alive);
    }

    private String getRecoverFailureStatusText(int consecutiveRecoverFailures) {
        if (consecutiveRecoverFailures <= 0) {
            return getString(R.string.notification_disconnected);
        }
        return getString(R.string.notification_recover_failed_retrying);
    }

    private void requestConnectionStatusSnapshot() {
        new InternalMessenger(this).send(new Intent(XMPushServiceMessenger.IntentGetConnectionStatus));
    }

    private static String getPackageName(Bundle appIdentity) {
        if (appIdentity == null) {
            return null;
        }
        return appIdentity.getString(APP_IDENTITY_PACKAGE_NAME);
    }

    private int resolveUserId(String packageName) {
        try {
            final ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(
                    packageName, 0);
            return applicationInfo.uid / PER_USER_RANGE;
        } catch (PackageManager.NameNotFoundException e) {
            LOGGER.w("resolveUserId missing package " + packageName, e);
            return Process.myUid() / PER_USER_RANGE;
        }
    }

    private static int stableTextHash(@Nullable String value) {
        return TextUtils.isEmpty(value) ? 0 : value.hashCode();
    }

    private void cancelProviderNotification(@Nullable Bundle event) {
        if (event == null) {
            return;
        }
        final String packageName = event.getString(
                PushBrokerConstants.SYSTEM_EVENT_NOTIFICATION_PACKAGE_NAME);
        final String tag = event.getString(PushBrokerConstants.SYSTEM_EVENT_NOTIFICATION_TAG);
        final int id = event.getInt(PushBrokerConstants.SYSTEM_EVENT_NOTIFICATION_ID, 0);
        com.nihility.notification.NotificationManagerEx.INSTANCE.cancel(packageName, tag, id);
        LOGGER.i("Canceled provider notification package=" + packageName
                + " tag=" + tag + " id=" + id);
    }

    private boolean canSyncAppState(@Nullable Bundle appIdentity, @Nullable String reason) {
        return canSyncAppStateResult(appIdentity, reason) == PushBrokerConstants.RESULT_OK;
    }

    private int canSyncAppStateResult(@Nullable Bundle appIdentity, @Nullable String reason) {
        final String packageName = getPackageName(appIdentity);
        final String resolvedReason = TextUtils.isEmpty(reason) ? "unknown" : reason;
        if (TextUtils.isEmpty(packageName)) {
            LOGGER.w("Skip app sync without package name");
            return PushBrokerConstants.RESULT_BAD_REQUEST;
        }

        synchronized (mLock) {
            if (!mEnabled) {
                LOGGER.i("Skip app sync because provider is disabled package="
                        + packageName + " reason=" + resolvedReason);
                return PushBrokerConstants.RESULT_DISABLED;
            }
            if (!STATUS_CONNECTED.equals(mConnectionStatus)) {
                LOGGER.i("Skip app sync because provider is not connected package="
                        + packageName + " status=" + mConnectionStatus
                        + " reason=" + resolvedReason);
                return PushBrokerConstants.RESULT_NOT_READY;
            }
        }

        if (TextUtils.isEmpty(PullAllApplicationDataFromServerJob.getRegisteredAppId(packageName))) {
            LOGGER.i("Skip app sync because package is not registered package=" + packageName
                    + " reason=" + resolvedReason);
            return PushBrokerConstants.RESULT_APP_NOT_REGISTERED;
        }
        return PushBrokerConstants.RESULT_OK;
    }

    private boolean syncAppState(@Nullable Bundle appIdentity, @Nullable String reason) {
        return syncAppStateResult(appIdentity, reason) == PushBrokerConstants.RESULT_OK;
    }

    private int syncAppStateResult(@Nullable Bundle appIdentity, @Nullable String reason) {
        final String packageName = getPackageName(appIdentity);
        final String resolvedReason = TextUtils.isEmpty(reason) ? "unknown" : reason;
        final int canSyncResult = canSyncAppStateResult(appIdentity, resolvedReason);
        if (canSyncResult != PushBrokerConstants.RESULT_OK) {
            return canSyncResult;
        }

        final XMPushService xmPushService = XMPushServiceAbility.xmPushService;
        if (xmPushService == null) {
            LOGGER.w("Skip app sync because XMPushService is unavailable package=" + packageName
                    + " reason=" + resolvedReason);
            return PushBrokerConstants.RESULT_NOT_READY;
        }

        final boolean synced = PullAllApplicationDataFromServerJob.syncApplication(
                xmPushService, packageName);
        if (!synced) {
            LOGGER.i("Skip app sync because package sync execution failed package=" + packageName
                    + " reason=" + resolvedReason);
            return PushBrokerConstants.RESULT_TEMPORARY_FAILURE;
        }

        LOGGER.i("Synced app state package=" + packageName + " reason=" + resolvedReason);
        return PushBrokerConstants.RESULT_OK;
    }

    private static int resolveNotificationTitleHash(@Nullable Notification notification,
            @Nullable PushMetaInfo metaInfo) {
        final CharSequence notificationTitle = getNotificationExtra(notification,
                Notification.EXTRA_TITLE_BIG);
        if (!TextUtils.isEmpty(notificationTitle)) {
            return stableTextHash(notificationTitle.toString());
        }
        final CharSequence fallbackTitle = getNotificationExtra(notification,
                Notification.EXTRA_TITLE);
        if (!TextUtils.isEmpty(fallbackTitle)) {
            return stableTextHash(fallbackTitle.toString());
        }
        return metaInfo == null ? 0 : stableTextHash(metaInfo.getTitle());
    }

    private static int resolveNotificationTextHash(@Nullable Notification notification,
            @Nullable PushMetaInfo metaInfo) {
        final CharSequence bigText = getNotificationExtra(notification, Notification.EXTRA_BIG_TEXT);
        if (!TextUtils.isEmpty(bigText)) {
            return stableTextHash(bigText.toString());
        }
        final CharSequence text = getNotificationExtra(notification, Notification.EXTRA_TEXT);
        if (!TextUtils.isEmpty(text)) {
            return stableTextHash(text.toString());
        }
        final CharSequence subText = getNotificationExtra(notification, Notification.EXTRA_SUB_TEXT);
        if (!TextUtils.isEmpty(subText)) {
            return stableTextHash(subText.toString());
        }
        return metaInfo == null ? 0 : stableTextHash(metaInfo.getDescription());
    }

    @Nullable
    private static CharSequence getNotificationExtra(@Nullable Notification notification,
            String key) {
        if (notification == null || notification.extras == null) {
            return null;
        }
        return notification.extras.getCharSequence(key);
    }

    private boolean supportsPackage(String packageName) {
        if (TextUtils.isEmpty(packageName) || TextUtils.equals(getPackageName(), packageName)) {
            return false;
        }

        final PackageInfo packageInfo;
        try {
            packageInfo = getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS);
        } catch (PackageManager.NameNotFoundException e) {
            LOGGER.w("supportsPackage missing package " + packageName, e);
            return false;
        }

        return !shouldSkipPackage(packageInfo.applicationInfo)
                && (declaresMiPushServices(packageInfo) || declaresMiPushReceiver(packageName));
    }

    private static boolean shouldSkipPackage(ApplicationInfo applicationInfo) {
        if (applicationInfo == null) {
            return true;
        }

        final int flags = applicationInfo.flags;
        if ((flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                || (flags & ApplicationInfo.FLAG_PERSISTENT) != 0) {
            return true;
        }

        return applicationInfo.uid < Process.FIRST_APPLICATION_UID;
    }

    private static boolean declaresMiPushServices(PackageInfo packageInfo) {
        if (packageInfo.services != null) {
            for (int i = 0; i < packageInfo.services.length; i++) {
                final String serviceName = packageInfo.services[i].name;
                if (MIPUSH_XM_PUSH_SERVICE.equals(serviceName)
                        || MIPUSH_XM_JOB_SERVICE.equals(serviceName)) {
                    return true;
                }
            }
        }

        if (packageInfo.receivers != null) {
            for (int i = 0; i < packageInfo.receivers.length; i++) {
                final String receiverName = packageInfo.receivers[i].name;
                if (MIPUSH_PING_RECEIVER.equals(receiverName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean declaresMiPushReceiver(String packageName) {
        final Intent intent = new Intent(MIPUSH_RECEIVE_MESSAGE_ACTION);
        intent.setPackage(packageName);
        final List<ResolveInfo> receivers = getPackageManager().queryBroadcastReceivers(intent, 0);
        return receivers != null && !receivers.isEmpty();
    }

    private static List<RegisteredApplication> getRegisteredApplications() {
        return RegisteredApplicationDb.getList(null);
    }

    private static void appendConfigSnapshot(Bundle bundle) {
        final ConfigCenter configCenter = new ConfigCenter();
        bundle.putBoolean("debugMode", configCenter.isDebugMode());
        final String xmppServer = configCenter.getXMPPServer(Utils.getApplication());
        if (!TextUtils.isEmpty(xmppServer)) {
            bundle.putString("xmppServer", xmppServer);
        }
    }

    private static void appendRegistrationSnapshot(Bundle bundle) {
        final List<RegisteredApplication> apps = getRegisteredApplications();
        int registeredCount = 0;
        int unregisteredCount = 0;
        int notRegisteredCount = 0;

        for (RegisteredApplication app : apps) {
            switch (app.getRegisteredType()) {
                case RegisteredApplication.RegisteredType.Registered:
                    registeredCount++;
                    break;
                case RegisteredApplication.RegisteredType.Unregistered:
                    unregisteredCount++;
                    break;
                case RegisteredApplication.RegisteredType.NotRegistered:
                default:
                    notRegisteredCount++;
                    break;
            }
        }

        bundle.putInt("registeredApplicationCount", apps.size());
        bundle.putInt("registeredCount", registeredCount);
        bundle.putInt("unregisteredCount", unregisteredCount);
        bundle.putInt("notRegisteredCount", notRegisteredCount);
    }
}
