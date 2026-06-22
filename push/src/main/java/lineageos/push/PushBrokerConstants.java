package lineageos.push;

public final class PushBrokerConstants {
    public static final String ACTION_PUSH_PROVIDER_SERVICE =
            "org.lineageos.pushbroker.action.PUSH_PROVIDER";

    public static final String META_PROVIDER_ID =
            "org.lineageos.pushbroker.provider_id";
    public static final String META_API_VERSION =
            "org.lineageos.pushbroker.api_version";
    public static final String META_DISPLAY_NAME =
            "org.lineageos.pushbroker.display_name";
    public static final String META_PROVIDER_TYPE =
            "org.lineageos.pushbroker.provider_type";
    public static final String META_CAPABILITIES =
            "org.lineageos.pushbroker.capabilities";
    public static final String META_SETTINGS_ACTIVITY =
            "org.lineageos.pushbroker.settings_activity";
    public static final String META_SETUP_ACTIVITY =
            "org.lineageos.pushbroker.setup_activity";
    public static final String META_DIAGNOSTICS_ACTIVITY =
            "org.lineageos.pushbroker.diagnostics_activity";

    public static final int API_VERSION_1 = 1;
    public static final int API_VERSION_2 = 2;
    public static final int API_VERSION_3 = 3;
    public static final int API_VERSION_4 = 4;

    public static final int RESULT_OK = 0;
    public static final int RESULT_UNSUPPORTED = 1;
    public static final int RESULT_DISABLED = 2;
    public static final int RESULT_NOT_READY = 3;
    public static final int RESULT_BAD_REQUEST = 4;
    public static final int RESULT_TEMPORARY_FAILURE = 5;
    public static final int RESULT_INTERNAL_ERROR = 6;
    public static final int RESULT_APP_NOT_REGISTERED = 7;

    public static final String APP_IDENTITY_PACKAGE_NAME = "packageName";
    public static final String APP_IDENTITY_UID = "uid";
    public static final String APP_IDENTITY_USER_ID = "userId";

    public static final String REQUEST_REASON = "reason";

    public static final String HEALTH_CONNECTION_STATUS = "connectionStatus";
    public static final String HEALTH_STATUS_CONNECTED = "connected";
    public static final String HEALTH_STATUS_CONNECTING = "connecting";
    public static final String HEALTH_STATUS_DISCONNECTED = "disconnected";
    public static final String HEALTH_STATUS_UNKNOWN = "unknown";
    public static final String HEALTH_TIMESTAMP = "healthTimestamp";
    public static final String HEALTH_PROVIDER_ENABLED = "providerEnabled";
    public static final String HEALTH_API_VERSION = "apiVersion";
    public static final String HEALTH_LAST_CONNECTED_AT = "lastConnectedAt";
    public static final String HEALTH_LAST_DISCONNECTED_AT = "lastDisconnectedAt";
    public static final String HEALTH_LAST_CONNECT_ATTEMPT_AT = "lastConnectAttemptAt";
    public static final String HEALTH_LAST_RECOVER_ATTEMPT_AT = "lastRecoverAttemptAt";
    public static final String HEALTH_LAST_RECOVER_SUCCEEDED_AT = "lastRecoverSucceededAt";
    public static final String HEALTH_LAST_RECOVER_FAILED_AT = "lastRecoverFailedAt";
    public static final String HEALTH_LAST_DISCONNECT_REASON_CODE = "lastDisconnectReasonCode";
    public static final String HEALTH_LAST_DISCONNECT_REASON_NAME = "lastDisconnectReasonName";
    public static final String HEALTH_CONSECUTIVE_RECOVER_FAILURES =
            "consecutiveRecoverFailures";
    public static final String HEALTH_BUSINESS_HEALTHY = "businessHealthy";
    public static final String HEALTH_BUSINESS_DEGRADED_REASON = "businessDegradedReason";
    public static final String HEALTH_REQUIRES_USER_ACTION = "requiresUserAction";
    public static final String HEALTH_USER_ACTION_REASON = "userActionReason";
    public static final String HEALTH_USER_ACTION_SETUP_ACTIVITY = "setupActivity";
    public static final String HEALTH_USER_ACTION_SETTINGS_ACTIVITY = "settingsActivity";
    public static final String HEALTH_USER_ACTION_DIAGNOSTICS_ACTIVITY = "diagnosticsActivity";
    public static final String HEALTH_DEGRADED_REASON_UNKNOWN = "unknown";
    public static final String HEALTH_USER_ACTION_REASON_SETUP_REQUIRED = "setup_required";
    public static final String HEALTH_USER_ACTION_REASON_PERMISSION_REQUIRED = "permission_required";
    public static final String HEALTH_USER_ACTION_REASON_ACCOUNT_REQUIRED = "account_required";
    public static final String HEALTH_USER_ACTION_REASON_PROVIDER_DISABLED = "provider_disabled";
    public static final String HEALTH_USER_ACTION_REASON_STORAGE_ERROR = "storage_error";
    public static final String HEALTH_DEGRADED_REASON_TRANSPORT_STALE = "transport_stale";
    public static final String HEALTH_DEGRADED_REASON_HEARTBEAT_TIMEOUT = "heartbeat_timeout";
    public static final String HEALTH_DEGRADED_REASON_AUTH_FAILED = "auth_failed";
    public static final String HEALTH_DEGRADED_REASON_STORAGE_ERROR = "storage_error";
    public static final String HEALTH_DEGRADED_REASON_APP_REGISTRY_UNAVAILABLE =
            "app_registry_unavailable";
    public static final String HEALTH_DEGRADED_REASON_RATE_LIMITED = "rate_limited";

    public static final String SYSTEM_EVENT_TYPE = "type";
    public static final String SYSTEM_EVENT_NETWORK_AVAILABLE = "networkAvailable";
    public static final String SYSTEM_EVENT_NETWORK_LOST = "networkLost";
    public static final String SYSTEM_EVENT_RECOVER_CONNECTION = "recoverConnection";
    public static final String SYSTEM_EVENT_CANCEL_PROVIDER_NOTIFICATION =
            "cancelProviderNotification";
    public static final String SYSTEM_EVENT_NOTIFICATION_PACKAGE_NAME = "notificationPackageName";
    public static final String SYSTEM_EVENT_NOTIFICATION_TAG = "notificationTag";
    public static final String SYSTEM_EVENT_NOTIFICATION_ID = "notificationId";
    public static final String SYSTEM_EVENT_SYNC_REASON = "syncReason";

    public static final String PROVIDER_EVENT_PROVIDER_ID = "providerId";
    public static final String PROVIDER_EVENT_NAME = "event";
    public static final String PROVIDER_EVENT_STATUS = "status";
    public static final String PROVIDER_EVENT_TIMESTAMP = "timestamp";
    public static final String PROVIDER_EVENT_REASON_CODE = "reasonCode";
    public static final String PROVIDER_EVENT_REASON_NAME = "reasonName";
    public static final String PROVIDER_EVENT_PACKAGE_NAME = "packageName";
    public static final String PROVIDER_EVENT_USER_ID = "userId";
    public static final String PROVIDER_EVENT_PROVIDER_NOTIFICATION_TAG = "providerNotificationTag";
    public static final String PROVIDER_EVENT_PROVIDER_NOTIFICATION_ID = "providerNotificationId";
    public static final String PROVIDER_EVENT_MESSAGE_ID = "messageId";
    public static final String PROVIDER_EVENT_JOB_KEY = "jobKey";
    public static final String PROVIDER_EVENT_NOTIFY_ID = "notifyId";
    public static final String PROVIDER_EVENT_TITLE_HASH = "titleHash";
    public static final String PROVIDER_EVENT_TEXT_HASH = "textHash";

    public static final String EVENT_PROVIDER_HEALTH_CHANGED =
            "provider_health_changed";
    public static final String EVENT_PROVIDER_RECOVER_STARTED =
            "provider_recover_started";
    public static final String EVENT_PROVIDER_RECOVER_SUCCEEDED =
            "provider_recover_succeeded";
    public static final String EVENT_PROVIDER_RECOVER_FAILED =
            "provider_recover_failed";
    public static final String EVENT_PROVIDER_APP_REGISTERED =
            "provider_app_registered";
    public static final String EVENT_PROVIDER_NOTIFICATION_POSTED =
            "provider_notification_posted";

    public static final String SETTING_AVOID_DUPLICATE_PUSH =
            "lineage_push_broker_avoid_duplicate_push";

    private PushBrokerConstants() {
    }
}
