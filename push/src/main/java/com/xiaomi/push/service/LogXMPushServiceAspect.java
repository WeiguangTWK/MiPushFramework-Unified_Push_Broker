package com.xiaomi.push.service;

import android.content.Intent;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.nihility.Global;
import com.nihility.service.ForegroundHelper;
import com.nihility.service.XMPushServiceAbility;
import com.xiaomi.xmsf.pushbroker.ProviderActivationStore;
import com.xiaomi.smack.Connection;
import com.xiaomi.xmsf.utils.ConvertUtils;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;

public class LogXMPushServiceAspect {
    private static final String TAG = LogXMPushServiceAspect.class.getSimpleName();
    private static final Logger logger = XLog.tag(TAG).build();

    public void onCreate(final JoinPoint joinPoint, XMPushService pushService) throws Throwable {
        logger.d(joinPoint.getSignature());
        logger.d("Service started");
        if (!ProviderActivationStore.isProviderEnabled(pushService)) {
            shutdownDisabledService(pushService, "on create");
        }
    }

    public void onStartCommand(final JoinPoint joinPoint) {
        logger.d(joinPoint.getSignature());
    }

    public void onStart(final JoinPoint joinPoint, Intent intent, int startId) {
        logger.d(joinPoint.getSignature());
        logIntent(intent);
        final com.xiaomi.push.service.XMPushService pushService = XMPushServiceAbility.xmPushService;
        if (pushService != null && !ProviderActivationStore.isProviderEnabled(pushService)) {
            logger.w("Broker provider is disabled; shutting down XMPushService on start. intent="
                    + ConvertUtils.toJson(intent));
            shutdownDisabledService(pushService, "on start");
            return;
        }
        Global.MiPushEventListener().receiveFromApplication(intent);
    }

    public void onBind(final JoinPoint joinPoint, Intent intent) {
        logger.d(joinPoint.getSignature());
        logIntent(intent);
    }

    public void onDestroy(final JoinPoint joinPoint) {
        logger.d(joinPoint.getSignature());
        logger.d("Service stopped");
    }

    public void setConnectionStatus(final JoinPoint joinPoint,
                                    int newStatus, int reason, Exception e) {
        logger.d(joinPoint.getSignature());
        final com.xiaomi.push.service.XMPushService pushService = XMPushServiceAbility.xmPushService;
        if (pushService == null) {
            logger.w("Skip connection status broadcast because XMPushService is null");
            return;
        }
        if (!ProviderActivationStore.isProviderEnabled(pushService)) {
            logger.w("Ignore connection status because Broker provider is disabled status="
                    + getDesc(newStatus) + " reason=" + reason);
            return;
        }

        final Intent intent = new Intent(XMPushServiceMessenger.IntentSetConnectionStatus);
        intent.putExtra("status", getDesc(newStatus));
        intent.putExtra("reason", reason);
        final Connection currentConnection = pushService.getCurrentConnection();
        if (currentConnection != null) {
            intent.putExtra("host", currentConnection.getHost());
        }
        pushService.sendBroadcast(intent);
        logger.i("Broadcast connection status=" + getDesc(newStatus) + " reason=" + reason);
    }

    public Object scheduleConnect(final ProceedingJoinPoint joinPoint, XMPushService pushService,
                                  boolean immediate) throws Throwable {
        if (!ProviderActivationStore.isProviderEnabled(pushService)) {
            logger.w("Skip scheduleConnect because Broker provider is disabled immediate="
                    + immediate);
            return null;
        }
        return joinPoint.proceed();
    }

    public void sendMessage(final JoinPoint joinPoint, Intent intent) {
        logger.d(joinPoint.getSignature());
        Global.MiPushEventListener().transferToServer(intent);
    }

    private void logIntent(Intent intent) {
        logger.d("Intent" + " " + ConvertUtils.toJson(intent));
    }

    private String getDesc(int status) {
        switch (status) {
            case 0:
                return "connecting";
            case 1:
                return "connected";
            case 2:
                return "disconnected";
            default:
                return "unknown";
        }
    }

    private void shutdownDisabledService(XMPushService pushService, String reason) {
        logger.w("Broker provider is disabled; stopping XMPushService " + reason);
        try {
            pushService.disconnect(10, null);
        } catch (Throwable t) {
            logger.w("Failed to disconnect disabled XMPushService", t);
        }
        try {
            new ForegroundHelper(pushService).stopForegroundNotification();
        } catch (Throwable t) {
            logger.w("Failed to stop foreground for disabled XMPushService", t);
        }
        pushService.stopSelf();
    }
}
