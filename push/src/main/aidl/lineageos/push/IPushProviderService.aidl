package lineageos.push;

import android.os.Bundle;
import lineageos.push.PushProviderInfo;

interface IPushProviderService {
    PushProviderInfo getProviderInfo();
    Bundle getHealth();

    int initialize(in Bundle brokerContext);
    int enable();
    int disable();
    int onBootPhase(int phase);
    int onSystemEvent(in Bundle event);

    int supportsApp(in Bundle appIdentity);
    int canSyncApp(in Bundle appIdentity, in Bundle extras);
    int prepareApp(in Bundle appIdentity);
    int revokeApp(in Bundle appIdentity);

    int syncApp(in Bundle appIdentity, in Bundle extras);
    int requestToken(in Bundle appIdentity, in Bundle extras);

    Bundle dumpState();
}
