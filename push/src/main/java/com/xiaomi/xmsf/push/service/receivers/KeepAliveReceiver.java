package com.xiaomi.xmsf.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.xmsf.push.control.PushControllerUtils;
import com.xiaomi.xmsf.pushbroker.ProviderActivationStore;



/**
 * @author zts
 */
public class KeepAliveReceiver extends BroadcastReceiver {
    private final Logger logger = XLog.tag(KeepAliveReceiver.class.getSimpleName()).build();

    private long lastActive = System.currentTimeMillis();

    public KeepAliveReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (!ProviderActivationStore.isProviderEnabled(context)) {
                logger.i("Ignore legacy keepalive wake because Broker provider is disabled");
                return;
            }
            long now = System.currentTimeMillis();

            if ((now - lastActive) < (1000 * 60 * 2)) {
                return;
            }

            lastActive = now;

            logger.d("start service when " + intent.getAction());
            Intent localIntent = new Intent(context, com.xiaomi.push.service.XMPushService.class);
            localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, now);
            localIntent.setAction(PushServiceConstants.ACTION_CHECK_ALIVE);
            PushControllerUtils.startLegacyPushService(context, localIntent, "keepalive");
        } catch (Exception localException) {
            MyLog.e(localException);
        }
    }
}
