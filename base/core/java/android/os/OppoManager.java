package android.os;

import android.os.IOppoService;
import android.util.Log;
import android.os.RemoteException;
import android.os.ServiceManager;
//#ifdef VENDOR_EDIT
//Jevons@OnLineRD.DeviceService, 2012-03-27, Add for record key log
import java.util.Date;
import java.text.SimpleDateFormat;
import android.os.SystemProperties;
//#endif /* VENDOR_EDIT */
/**
 * VENDOR_EDIT
 * @author licx@OnLineRD.Framework.oppo_service
 */
public final class OppoManager {
	public static final String TAG = "OppoManager";
	private static final boolean DEBUG = true;
	
	public static final String SERVICE_NAME = "OPPO";
	private static IOppoService sService;
	
	private static final int INIT_TRY_TIMES = 3;	// try 3 times
	
	public static final boolean init() {
		if (sService != null) {
		    return true;
		}
		int times = INIT_TRY_TIMES;
		do{
			if(DEBUG) Log.w(TAG, "Try to OppoService Instance! times = " + times);
			sService = IOppoService.Stub.asInterface(ServiceManager.getService(SERVICE_NAME));
			if (sService != null) {
				return true;
			}
		}while(--times > 0);
		return false;
	}
    //#ifdef VENDOR_EDIT
    //wangjw@OnLineRD.framework.oppo_debug, 2013.07.26 add for critical log	
    //critical data type	
    public static int TYPE_PANIC = 0;
    public static int TYPE_SHUTDOWN = 1;
    public static int TYPE_REBOOT = 2;
    public static int TYPE_LOGSIZE = 3;
    //every time read 16B critical data
    private static int DATA_SIZE = 16;	
    //normal shutdown
    public static final String SHUTDOWN_TAG ="SYSTEM_SHUTDOWN";
    public static final String SHUTDOWN_TAG_BEGIN ="<shutdown-begin>"+"\n";
    public static final String SHUTDOWN_TAG_END ="<shutdown-end>"+"\n";
    //kernel panic	
    public static final String KERNEL_PANIC_TAG ="SYSTEM_LAST_KMSG";
    public static final String KERNEL_PANIC_TAG_BEGIN ="<kernel-panic-begin>"+"\n";
    public static final String KERNEL_PANIC_TAG_END ="<kernel-panic-end>"+"\n";	
    //android panic
    public static final String ANDROID_PANIC_TAG ="SYSTEM_SERVER";
    public static final String ANDROID_PANIC_TAG_BEGIN ="<android-panic-begin>"+"\n";
    public static final String ANDROID_PANIC_TAG_END ="<android-panic-end>"+"\n";
    //engineeringmode test
    public static final String ENGINEERINGMODE_TEST_TAG ="ENGINEERINGMODE_TEST";
    public static final String ENGINEERINGMODE_TEST_BEGIN ="<engineeringmode-test-begin>"+"\n";
    public static final String ENGINEERINGMODE_TEST_END ="<engineeringmode-test-end>"+"\n";
    //#endif /* VENDOR_EDIT */
    
    //#ifdef VENDOR_EDIT
    //Horn@OnLineRD.DeviceService, 2012-12-20, Add for dump shutdown log	
    public static void dumpShutdownMsg(String reason)
    {
        if (DEBUG)
        {
            Log.w(TAG, "dumpShutdownMsg e");

        }
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String kid = SystemProperties.get("ro.build.kernel.id", "");
	  if(reason != null ||reason.length()>0){
		sb.append("shutdown reason:").append(reason).append("\n");
	  }	  	
        sb.append(sf.format(new Date()));
        sb.append("\n");
        sb.append("kernel id[" + kid + "]\n");
        Throwable t = new Throwable();
        StackTraceElement e[] = t.getStackTrace();
        for (int i = 0; i < e.length ; ++i)
        {
            sb.append(e[i].getFileName()).append("---");
	      sb.append(e[i].getClassName()).append("---");	
            sb.append(e[i].getMethodName()).append("---");
	      sb.append(e[i].getLineNumber());
            sb.append("\n");
        }
        String msg = sb.toString();
        int size = writeLogToPartition(msg,SHUTDOWN_TAG);
	  if (DEBUG)
        {
            Log.w(TAG, "write normal shutdown log size: "+size);

        }
	  //increment normal shutdown times
		if (incrementCriticalData(TYPE_SHUTDOWN) < 0) {
		Log.e(TAG, "increment normal shutdown times failed!!");
		}
    }
    //#endif /* VENDOR_EDIT */
    public static String readRawPartition(int offset, int size) {
		String res = null;
		if (null == sService && !init()) {
			return res;
		}
		try {
			res = sService.readRawPartition(offset, size);
		} catch (RemoteException e) {
			Log.e(TAG, "read Raw Partition exception!");
			if (DEBUG) e.printStackTrace();
		}
		return res;
    }
    
    public static int writeRawPartition(String content) {
		int res = -1;
		if (null == sService && !init()) {
			return res;
		}
		try {
			res = sService.writeRawPartition(content);
		} catch (RemoteException e) {
			Log.e(TAG, "write Raw Partition exception!");
			if (DEBUG) e.printStackTrace();
		}
		return res;
    }

    //#ifdef VENDOR_EDIT
    //wangjw@OnLineRD.framework.oppo_debug, 2013.07.26 add for critical log
    public static int incrementCriticalData(int type){
	  int res = -1;	
		if (type < 0) {
		   return res;
		}
	   //read times
         String timesString = readCriticalData(type, DATA_SIZE).trim();
	   try {
			int times = (null == timesString || timesString.length() == 0) ? 0 : Integer
							.parseInt(timesString);
			res = OppoManager.writeCriticalData(type, String.valueOf(times + 1));
		} catch (Exception e) {
			Log.e(TAG, "increment critical data failed!!");
			if (DEBUG) e.printStackTrace();
		}
	    return res;
	}

      public static int readCriticalData(int type){
	  	int res = 0;
		String dataString = readCriticalData(type,DATA_SIZE).trim();
		if (null == dataString || dataString.length() == 0) {
			return res;
		}
		try{
			res =  Integer.parseInt(dataString);
		}catch(Exception e){
			Log.e(TAG, "read critical data failed!!");
			if (DEBUG) e.printStackTrace();
		}
		return res;
	}
	  
	public static int writeLogToPartition(String logstring,String tagString){
		int res = -1;
		if (null == logstring) {
			return res;
		}
		String tagbegin = "";
		String tagend = "";
		if(tagString.equals(SHUTDOWN_TAG)){
			tagbegin = SHUTDOWN_TAG_BEGIN;
			tagend = SHUTDOWN_TAG_END;
		}else if(tagString.equals(KERNEL_PANIC_TAG)){
			tagbegin = KERNEL_PANIC_TAG_BEGIN;
			tagend = KERNEL_PANIC_TAG_END;
		}else if(tagString.equals(ANDROID_PANIC_TAG)){
			tagbegin = ANDROID_PANIC_TAG_BEGIN;
			tagend = ANDROID_PANIC_TAG_END;
		}else if(tagString.equals(ENGINEERINGMODE_TEST_TAG)){
			tagbegin = ENGINEERINGMODE_TEST_BEGIN;
			tagend = ENGINEERINGMODE_TEST_END;
		}
		String log = tagbegin+logstring+tagend;
		return writeRawPartition(log);
	}
	

    //for compile apps
   // public static int writeRawPartition(int id,String content) {
   //		return 0;
   //  }

    public static String readCriticalData(int id, int size){
		String res = null;
		if (null == sService && !init()) {
			return res;
		}
		try{
			res = sService.readCriticalData(id,size);
		}catch(RemoteException e){
			Log.e(TAG, "read Critical Data exception!\n");
			if(DEBUG) e.printStackTrace();
		}
		return res;
    }

    public static int writeCriticalData(int id,String content){
		int res = -1;
		if (null == sService && !init()) {
			return res;
		}
		try {
			res = sService.writeCriticalData(id,content);
		} catch (RemoteException e) {
			Log.e(TAG, "write Critical Data exception!\n");
			if (DEBUG) e.printStackTrace();
		}
		return res;
    }
    //#endif /* VENDOR_EDIT */
}
