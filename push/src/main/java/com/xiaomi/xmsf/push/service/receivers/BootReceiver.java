package com.xiaomi.xmsf.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.elvishew.xlog.XLog;

/**
 * Created by Trumeet on 2017/8/25.
 * @author Trumeet
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && "android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            XLog.tag("BootReceiver").build().i(
                    "Ignoring legacy boot self-start; runtime is broker-controlled");
        }
    }
}
