package lineageos.push;

import android.os.Bundle;

interface IPushBrokerHost {
    int postNotification(in Bundle notification);
    int cancelNotification(String packageName, String tag, int id);
    int reportEvent(in Bundle event);
    int requestUserAction(in Bundle action);
}
