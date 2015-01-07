package com.android.server.oppo;

import android.os.IOppoService;
import android.util.Slog;
import android.util.Log;
import android.content.Context;
import android.os.Handler;
import android.os.Message;


import com.android.internal.telephony.IPhoneSubInfo;
import android.telephony.TelephonyManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;

/**
 * VENDOR_EDIT
 * @author licx@OnLineRD.Framework.oppo_service
 */
public final class OppoService extends IOppoService.Stub{
    private static final String TAG = "OppoService";
	private static final boolean DEBUG = true;
	private Context mContext;
	
	//wangw@OnLineRD.DeviceService, 2013/08/20, Add open diag com when imei is equal 0 (0 means at factory mode)
	//oppo wangw add to set try times	
	private int mRetry = 7;
    private static final int MSG_GET_IMEI_NO = 2;
    private static final int GET_IMEI_NO_DELAY = 20 * 1000;	
	
    private final Handler mHandler = new Handler() {
	
        @Override
        public void handleMessage(Message msg) {
        	if(msg.what == MSG_GET_IMEI_NO) {
				if (mRetry == 0) {
					return;
				}
				if (isFactoryMode()) {
			 	    //Log.d(TAG, "cur isFactoryMode");
				    // SystemProperties.set("sys.usb.config", "diag,diag_mdm,serial_hsic,serial_tty,rmnet_hsic,mass_storage,adb"); 	
				    SystemProperties.set("sys.usb.config", "diag_mdm,adb");
				  	SystemClock.sleep(100);
				  	SystemProperties.set("sys.dial.enable", Boolean.toString(true));
				  	mRetry = 0;
				} else {
				    mRetry--;
					sendMessageDelayed(obtainMessage(MSG_GET_IMEI_NO),GET_IMEI_NO_DELAY / 2);
				}
				//Log.d(TAG, "cur mRetry:" + mRetry);
			}
        }
    };

    private boolean isFactoryMode() {
	   	String imei = null;
		boolean result = false;
		try {
			IPhoneSubInfo iPhoneSubInfo = IPhoneSubInfo.Stub.asInterface(ServiceManager.getService("iphonesubinfo"));			
			if (iPhoneSubInfo == null) {
				Log.e(TAG, "iphonesubinfo service is not ready!" );
				return false;
			}
			imei = iPhoneSubInfo.getDeviceId();	
			if (imei == null || (imei != null && "0".equals(imei))) {	
				result = true;
			}
		} catch (RemoteException ex) { 
		    result = false;
			Log.e(TAG, "getDeviceId remoteException:" + ex.getMessage());			
		} 
		
		return result;
    }
    
	public OppoService(Context context){
		mContext = context;
         
		//wangw@OnLineRD.DeviceService, 2013/08/20, Add open diag com when imei is equal 0 (0 means at factory mode)
	    //mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_GET_IMEI_NO), GET_IMEI_NO_DELAY);
		
        boolean b = native_initRawPartition();
        if (!b)
            Slog.e(TAG, "RawPartition init failed!");
	}

    protected void finalize() throws Throwable {
        native_finalizeRawPartition();
        super.finalize();
    }
	
    /******************************************************
     * 
     * This part is for writing raw partition
     * 
     ******************************************************/
    private native boolean native_initRawPartition();
    private native void native_finalizeRawPartition();
    
    private native String native_readRawPartition(int id, int size);
    private native int native_writeRawPartition(String content);

    private native String native_readCriticalData(int id, int size);
    private native int native_writeCriticalData(int id, String content);
	
    public String readRawPartition(int offset, int size) {
        return native_readRawPartition(offset, size);
    }
    
    public int writeRawPartition(String content) {
        return native_writeRawPartition(content);
    }

    public String readCriticalData(int id, int size){
	  return native_readCriticalData(id, size);
    }
	
    public int writeCriticalData(int id, String content){
        return native_writeCriticalData(id,content);
    }
    
}
