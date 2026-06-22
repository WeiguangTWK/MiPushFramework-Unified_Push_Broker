package lineageos.push;

import android.os.Parcel;
import android.os.Parcelable;

public final class PushProviderInfo implements Parcelable {
    public static final String PROVIDER_TYPE_UNKNOWN = "unknown";
    public static final String PROVIDER_TYPE_NATIVE = "native";
    public static final String PROVIDER_TYPE_COMPATIBILITY = "compatibility";

    public static final int TRUST_UNKNOWN = 0;
    public static final int TRUST_SYSTEM = 1;
    public static final int TRUST_USER_INSTALLED = 2;
    public static final int TRUST_REJECTED = 3;

    public final String providerId;
    public final String displayName;
    public final String packageName;
    public final String serviceName;
    public final String providerType;
    public final String versionName;
    public final long versionCode;
    public final int apiVersion;
    public final int trustLevel;
    public final String settingsActivity;
    public final String setupActivity;
    public final String diagnosticsActivity;
    public final String[] declaredCapabilities;

    public PushProviderInfo(String providerId, String displayName, String packageName,
            String serviceName, String providerType, String versionName, long versionCode,
            int apiVersion, int trustLevel, String settingsActivity, String setupActivity,
            String diagnosticsActivity, String[] declaredCapabilities) {
        this.providerId = providerId;
        this.displayName = displayName;
        this.packageName = packageName;
        this.serviceName = serviceName;
        this.providerType = providerType;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.apiVersion = apiVersion;
        this.trustLevel = trustLevel;
        this.settingsActivity = settingsActivity;
        this.setupActivity = setupActivity;
        this.diagnosticsActivity = diagnosticsActivity;
        this.declaredCapabilities = declaredCapabilities;
    }

    private PushProviderInfo(Parcel in) {
        providerId = in.readString();
        displayName = in.readString();
        packageName = in.readString();
        serviceName = in.readString();
        providerType = in.readString();
        versionName = in.readString();
        versionCode = in.readLong();
        apiVersion = in.readInt();
        trustLevel = in.readInt();
        settingsActivity = in.readString();
        setupActivity = in.readString();
        diagnosticsActivity = in.readString();
        declaredCapabilities = in.createStringArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(providerId);
        dest.writeString(displayName);
        dest.writeString(packageName);
        dest.writeString(serviceName);
        dest.writeString(providerType);
        dest.writeString(versionName);
        dest.writeLong(versionCode);
        dest.writeInt(apiVersion);
        dest.writeInt(trustLevel);
        dest.writeString(settingsActivity);
        dest.writeString(setupActivity);
        dest.writeString(diagnosticsActivity);
        dest.writeStringArray(declaredCapabilities);
    }

    public static final Creator<PushProviderInfo> CREATOR = new Creator<PushProviderInfo>() {
        @Override
        public PushProviderInfo createFromParcel(Parcel source) {
            return new PushProviderInfo(source);
        }

        @Override
        public PushProviderInfo[] newArray(int size) {
            return new PushProviderInfo[size];
        }
    };
}
