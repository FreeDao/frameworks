package android.nfc;

import android.os.Parcel;
import android.os.Parcelable;
/**
 * Represents Routing information for single routing table entry.
 */
public class MultiSERoutingInfo implements Parcelable {
    /** Route Type */
    public static byte ROUTE_DEFAULT = 0x00;
    public static byte ROUTE_AID = 0x01;
    public static byte ROUTE_PROTOCOL = 0x02;
    public static byte ROUTE_TECHNOLOGY = 0x03;
    /** Location */
    public static byte LOCATION_UICC = 0x02;
    public static byte LOCATION_ESE = 0x01;
    public static byte LOCATION_HOST = 0x04;
    /** Power State in which Route is valid */
    public static byte POWER_STATE_BATTERY_OFF  = 0x04;
    public static byte POWER_STATE_SWITCHED_OFF = 0x02;
    public static byte POWER_STATE_SWITCHED_ON  = 0x01;
    /** Protocol */
    public static byte PROTOCOL_MIFARE = 0x01;
    /** Technology */
    public static byte TECHNOLOGY_A = 0x01;
    public static byte TECHNOLOGY_B = 0x02;
    public static byte TECHNOLOGY_F = 0x04;

    /** Type of Route entry - AID/PROTOCOL/DEFAULT
     * AID is the hexadecimal stream representation of AID
     * */
    private byte mRouteType;
    /** Type=AID then contains AID, Type=PROTOCOL then contains PROTOCOL, Type=DEFAULT then ignored */
    private byte[] mRouteDetail;
    /**Route destination UICC/ESE/HOST */
    private byte mLocation;
    /** BATTERY_OFF/SWITCHED_ON/SWITCHED_OFF */
    private byte mPowerState;

    public MultiSERoutingInfo() {

    }

    public MultiSERoutingInfo(byte routeType, byte[] routeDetail, byte location, byte powerState) {
        this.mRouteType = routeType;
        this.mRouteDetail = routeDetail;
        this.mLocation = location;
        this.mPowerState = powerState;
    }

    public byte getRouteType() {
        return mRouteType;
    }

    public void setRouteType(byte mRouteType) {
        this.mRouteType = mRouteType;
    }

    public byte[] getRouteDetail() {
        return mRouteDetail;
    }

    public void setRouteDetail(byte[] mRouteDetail) {
        this.mRouteDetail = mRouteDetail;
    }

    public byte getLocation() {
        return mLocation;
    }

    public void setLocation(byte mLocation) {
        this.mLocation = mLocation;
    }

    public byte getPowerState() {
        return mPowerState;
    }

    public void setPowerState(byte mPowerState) {
        this.mPowerState = mPowerState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLocation);
        dest.writeInt(mPowerState);
        dest.writeInt(mRouteType);
        if (null != mRouteDetail) {
            dest.writeInt(mRouteDetail.length);
            dest.writeByteArray(mRouteDetail);
        }
    }

    public static final Parcelable.Creator<MultiSERoutingInfo> CREATOR =
            new Parcelable.Creator<MultiSERoutingInfo>() {
                public MultiSERoutingInfo createFromParcel(Parcel in) {
                    byte location = (byte) in.readInt();
                    byte powerState = (byte) in.readInt();
                    byte routeType = (byte) in.readInt();
                    int routeDetailLength = in.readInt();
                    byte[] routeDetail = new byte[routeDetailLength];
                    in.readByteArray(routeDetail);

                    return new MultiSERoutingInfo(routeType, routeDetail, location, powerState);
                }

                public MultiSERoutingInfo[] newArray(int size) {
                    return new MultiSERoutingInfo[size];
                }
            };

}
