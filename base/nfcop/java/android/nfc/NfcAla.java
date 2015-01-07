package android.nfc;

import android.nfc.tech.TagTechnology;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;

/**
 * This class provides the primary API for managing Applet load applet.
*/
public final class NfcAla {
    private static final String TAG = "NfcAla";
    private INfcAla mService;

    public NfcAla(INfcAla mAlaService) {
        mService = mAlaService;
    }
    public int appletLoadApplet(String pkg, String choice) throws IOException {
        try {
            int status = mService.appletLoadApplet(pkg, choice);
            // Handle potential errors
            if (status == 0x00) {
                return status;
            } else {
                throw new IOException("Unable to Load applet");
            }
         } catch (RemoteException e) {
               Log.e(TAG, "RemoteException in AppletLoadApplet(): ", e);
               throw new IOException("RemoteException in AppletLoadApplet()");
         }
    }

    public int getListofApplets(String pkg, String[] name) throws IOException {
        try {
            int num = mService.getListofApplets(pkg, name);
            // Handle potential error
            return num;

         } catch (RemoteException e) {
               Log.e(TAG, "RemoteException in GetListofApplets(): ", e);
               throw new IOException("RemoteException in GetListofApplets()");
         }
    }

    public byte[] getKeyCertificate() throws IOException {
        try{
            byte[] data = mService.getKeyCertificate();
            if((data != null) && (data.length != 0x00))
                return data;
            else
                throw new IOException("invalid data received");
        } catch (RemoteException e) {
              Log.e(TAG, "RemoteException in getKeyCertificate(): ", e);
              throw new IOException("RemoteException in getKeyCertificate()");
        }
    }
}

