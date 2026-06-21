package com.nihility.service;

import android.content.Intent;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;

public class ForegroundAbility implements XMPushServiceListener {
    private static final Logger LOGGER = XLog.tag("ForegroundAbility").build();
    public final ForegroundHelper foregroundHelper;

    public ForegroundAbility(ForegroundHelper foregroundHelper) {
        this.foregroundHelper = foregroundHelper;
    }

    @Override
    public void created() {
        LOGGER.i("Skip legacy foreground start on XMPushService create; Broker/provider now own foreground control");
    }

    @Override
    public void start(Intent intent) {
        // Legacy self-keepalive foreground promotion is disabled. Broker/provider recover
        // flow is now the only path allowed to elevate XMPushService into foreground.
    }

    @Override
    public void destroy() {
        foregroundHelper.stopForegroundNotification();
    }

    @Override
    public void connectionStatusChanged(ConnectionStatus connectionStatus) {
        if (connectionStatus == ConnectionStatus.connected) {
            LOGGER.i("Stopping foreground after XMPushService reached connected state");
            foregroundHelper.stopForegroundNotification();
        }
    }
}
