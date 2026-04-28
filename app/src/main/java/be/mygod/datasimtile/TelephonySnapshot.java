package be.mygod.datasimtile;

import android.os.Parcel;

record TelephonySnapshot(int status, String error, int currentSubId, String currentName, int targetSubId,
                         String targetName, int simCount) {
    static final int STATUS_OK = 0;
    static final int STATUS_ERROR = 1;

    static TelephonySnapshot error(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isEmpty()) message = throwable.getClass().getSimpleName();
        return new TelephonySnapshot(STATUS_ERROR, message, -1, null, -1, null, 0);
    }

    boolean hasError() {
        return status != STATUS_OK;
    }

    boolean canSwitch() {
        return !hasError() && currentSubId >= 0 && targetSubId >= 0;
    }

    void writeToParcel(Parcel parcel) {
        parcel.writeInt(status);
        parcel.writeString(error);
        parcel.writeInt(currentSubId);
        parcel.writeString(currentName);
        parcel.writeInt(targetSubId);
        parcel.writeString(targetName);
        parcel.writeInt(simCount);
    }

    static TelephonySnapshot readFromParcel(Parcel parcel) {
        return new TelephonySnapshot(parcel.readInt(), parcel.readString(), parcel.readInt(),
                parcel.readString(), parcel.readInt(), parcel.readString(), parcel.readInt());
    }
}
