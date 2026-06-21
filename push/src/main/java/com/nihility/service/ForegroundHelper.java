package com.nihility.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.xmsf.R;

public class ForegroundHelper {
    public static final String CHANNEL_STATUS = "status";
    public static final int NOTIFICATION_ALIVE_ID = 1;
    private static final Logger LOGGER = XLog.tag("ForegroundHelper").build();
    private final Service service;
    private boolean mForegroundStarted;

    public ForegroundHelper(Service service) {
        this.service = service;
    }

    public void startForeground() {
        createNotificationGroupForPushStatus();
        startForeground(service.getString(R.string.notification_connecting));
    }

    public void startForeground(String statusText) {
        createNotificationGroupForPushStatus();
        // XMPushService is started through Context.startForegroundService() from multiple
        // receivers and recovery paths. Once that API is used, Android requires the service
        // to call startForeground() promptly, otherwise the system raises an ANR.
        startForegroundInternal(buildForegroundNotification(statusText), statusText);
    }

    public void updateForegroundNotification(String statusText) {
        if (!mForegroundStarted) {
            return;
        }
        final Notification notification = buildForegroundNotification(statusText);
        LOGGER.i("Updating foreground notification status="
                + notification.extras.getCharSequence(Notification.EXTRA_TITLE));
        NotificationManagerCompat.from(service.getApplicationContext())
                .notify(NOTIFICATION_ALIVE_ID, notification);
    }

    public void stopForegroundNotification() {
        LOGGER.i("Stopping foreground notification");
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE);
        mForegroundStarted = false;
    }

    private void startForegroundInternal(Notification notification, String statusText) {
        LOGGER.i("Starting foreground notification status="
                + notification.extras.getCharSequence(Notification.EXTRA_TITLE));
        service.startForeground(NOTIFICATION_ALIVE_ID, notification);
        mForegroundStarted = true;
    }

    private Notification buildForegroundNotification(String statusText) {
        final String contentTitle = TextUtils.isEmpty(statusText)
                ? service.getString(R.string.notification_alive)
                : statusText;
        return new NotificationCompat.Builder(service, CHANNEL_STATUS)
                .setContentTitle(contentTitle)
                .setContentText(service.getString(R.string.notification_category_alive))
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(true)
                .build();
    }

    void createNotificationGroupForPushStatus() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(service.getApplicationContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String groupId = "status_group";
            NotificationChannelGroupCompat.Builder group =
                    new NotificationChannelGroupCompat.Builder(groupId)
                            .setName(CHANNEL_STATUS);
            manager.createNotificationChannelGroup(group.build());

            NotificationChannelCompat.Builder channel = new NotificationChannelCompat.Builder(
                    CHANNEL_STATUS, NotificationManager.IMPORTANCE_MIN)
                    .setName(service.getString(R.string.notification_category_alive))
                    .setDescription(service.getString(R.string.notification_category_alive))
                    .setGroup(groupId)
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setLightsEnabled(false);
            manager.createNotificationChannel(channel.build());
        }
    }
}
