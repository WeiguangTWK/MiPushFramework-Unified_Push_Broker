package com.xiaomi.push.service;

import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nihility.XMPushUtils;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;

import java.util.Map;

import top.trumeet.common.utils.Utils;

public class PullAllApplicationDataFromServerJob extends XMPushService.Job {
    private static final String PREF_REGISTERED_PKG_NAMES = "pref_registered_pkg_names";
    private final XmPushActionOperator xmPushActionOperator;

    public PullAllApplicationDataFromServerJob(XMPushService xmPushService) {
        super(XMPushService.Job.TYPE_SEND_MSG);
         xmPushActionOperator = new XmPushActionOperator(xmPushService);
    }

    @Override
    public String getDesc() {
        return "pull all application data";
    }

    @Override
    public void process() {
        SharedPreferences sp = Utils.getApplication().getSharedPreferences(PREF_REGISTERED_PKG_NAMES, 0);
        for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
            String packageName = entry.getKey();
            String appId = entry.getValue() == null ? null : entry.getValue().toString();
            if (TextUtils.isEmpty(appId)) {
                continue;
            }

            sendPullForAppId(xmPushActionOperator, packageName, appId);
        }
    }

    public static @Nullable String getRegisteredAppId(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        final SharedPreferences sp = Utils.getApplication()
                .getSharedPreferences(PREF_REGISTERED_PKG_NAMES, 0);
        final String appId = sp.getString(packageName, null);
        return TextUtils.isEmpty(appId) ? null : appId;
    }

    public static boolean syncApplication(XMPushService xmPushService, String packageName) {
        if (xmPushService == null || TextUtils.isEmpty(packageName)) {
            return false;
        }
        final String appId = getRegisteredAppId(packageName);
        if (TextUtils.isEmpty(appId)) {
            return false;
        }
        sendPullForAppId(new XmPushActionOperator(xmPushService), packageName, appId);
        return true;
    }

    private static void sendPullForAppId(XmPushActionOperator actionOperator, String packageName,
            String appId) {
        actionOperator.sendMessage(
                XMPushUtils.packToContainer(getPullAction(appId), packageName),
                packageName);
    }

    public static @NonNull XmPushActionNotification getPullAction(String appId) {
        XmPushActionNotification notification2 = new XmPushActionNotification();
        notification2.setAppId(appId);
        notification2.setType("pull");
        notification2.setId("fake_pull_" + appId + "_" + System.currentTimeMillis());
        notification2.setRequireAck(false);
        return notification2;
    }
}
