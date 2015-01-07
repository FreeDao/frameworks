/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.SntpClient;
import android.os.SystemClock;
import android.provider.Settings;

//#ifdef VENDOR_EDIT
//Wangw@Swdp.Android.Boot&Charge, 2014/08/11, Solve algin time too slow when ntp server is not avaiable
import android.net.OppoHttpClient;
import android.os.SystemProperties;
//#endif /* VENDOR_EDIT */

/**
 * {@link TrustedTime} that connects with a remote NTP server as its trusted
 * time source.
 *
 * @hide
 */
public class NtpTrustedTime implements TrustedTime {
    private static final String TAG = "NtpTrustedTime";
    private static final boolean LOGD = false;

    private static NtpTrustedTime sSingleton;

    private final String mServer;
    private final long mTimeout;

    private boolean mHasCache;
    private long mCachedNtpTime;
    private long mCachedNtpElapsedRealtime;
    private long mCachedNtpCertainty;
	//#ifdef VENDOR_EDIT
    //Jiangtao.Guo@Prd.SysSrv.InputManager, 2014/03/25, Add for add ntp server for oppo
    private String[] oppoNTPserver = {  "",
                                        "cn.pool.ntp.org"/*,
                                        "2.android.pool.ntp.org"*/};
	private static Context mContext;
//#endif /* VENDOR_EDIT */

    private NtpTrustedTime(String server, long timeout) {
        if (LOGD) Log.d(TAG, "creating NtpTrustedTime using " + server);
        mServer = server;
        mTimeout = timeout;
		//#ifdef VENDOR_EDIT
        //Jiangtao.Guo@Prd.SysSrv.InputManager, 2014/03/25, Add for add ntp server for oppo
        oppoNTPserver[0] = mServer;
        //#endif /* VENDOR_EDIT */
    }

    public static synchronized NtpTrustedTime getInstance(Context context) {
        if (sSingleton == null) {
            final Resources res = context.getResources();
            final ContentResolver resolver = context.getContentResolver();

            final String defaultServer = res.getString(
                    com.android.internal.R.string.config_ntpServer);
            final long defaultTimeout = res.getInteger(
                    com.android.internal.R.integer.config_ntpTimeout);

            final String secureServer = Settings.Global.getString(
                    resolver, Settings.Global.NTP_SERVER);
            final long timeout = Settings.Global.getLong(
                    resolver, Settings.Global.NTP_TIMEOUT, defaultTimeout);

            final String server = secureServer != null ? secureServer : defaultServer;
            sSingleton = new NtpTrustedTime(server, timeout);
			
			mContext = context;
        }

        return sSingleton;
    }

    @Override
    public boolean forceRefresh() {
        if (mServer == null) {
            // missing server, so no trusted time available
            return false;
        }

        if (LOGD) Log.d(TAG, "forceRefresh() from cache miss");
		 
		//#ifdef VENDOR_EDIT
        //wangw@OnLineRD.DeviceService, 2014/08/21, Add for use Oppo http server first!
		final OppoHttpClient oppoHttpClient = new OppoHttpClient();
		if (oppoHttpClient.requestTime(mContext, 0, (int) mTimeout) 
				|| oppoHttpClient.requestTime(mContext, 1, (int) mTimeout)) {
			Log.d(TAG, "Use oppo http server algin time success!");
			mHasCache = true;
			mCachedNtpTime = oppoHttpClient.getHttpTime();
			mCachedNtpElapsedRealtime = oppoHttpClient.getHttpTimeReference();
			mCachedNtpCertainty = oppoHttpClient.getRoundTripTime() / 2;
			return true;
		} 
		//#endif /* VENDOR_EDIT */
		
        final SntpClient client = new SntpClient();
		//#ifndef VENDOR_EDIT
        //Jiangtao.Guo@Prd.SysSrv.InputManager, 2014/03/25, Modify for add ntp server for oppo
        /*
        if (client.requestTime(mServer, (int) mTimeout)) {
            mHasCache = true;
            mCachedNtpTime = client.getNtpTime();
            mCachedNtpElapsedRealtime = client.getNtpTimeReference();
            mCachedNtpCertainty = client.getRoundTripTime() / 2;
            return true;
        } else {
            return false;
        }
		*/
        //#else /* VENDOR_EDIT */
		Log.d(TAG, "set time out value:" + mTimeout);
        for (int i = 0;i < oppoNTPserver.length;i++){
			boolean isNtpError = "1".equals(SystemProperties.get("sys.ntp.exception", "0"));
			if(isNtpError) {
				SystemClock.sleep(mTimeout);
			} else {
	            if (client.requestTime(oppoNTPserver[i], (int) mTimeout)) {
	                Log.d(TAG, "mServer = " + oppoNTPserver[i]);
	                mHasCache = true;
	                mCachedNtpTime = client.getNtpTime();
	                mCachedNtpElapsedRealtime = client.getNtpTimeReference();
	                mCachedNtpCertainty = client.getRoundTripTime() / 2;
	                return true;
	            }
			}
        }

        return false;
        //#endif /* VENDOR_EDIT */
    }

    @Override
    public boolean hasCache() {
        return mHasCache;
    }

    @Override
    public long getCacheAge() {
        if (mHasCache) {
            return SystemClock.elapsedRealtime() - mCachedNtpElapsedRealtime;
        } else {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public long getCacheCertainty() {
        if (mHasCache) {
            return mCachedNtpCertainty;
        } else {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public long currentTimeMillis() {
        if (!mHasCache) {
            throw new IllegalStateException("Missing authoritative time source");
        }
        if (LOGD) Log.d(TAG, "currentTimeMillis() cache hit");

        // current time is age after the last ntp cache; callers who
        // want fresh values will hit makeAuthoritative() first.
        return mCachedNtpTime + getCacheAge();
    }

    public long getCachedNtpTime() {
        if (LOGD) Log.d(TAG, "getCachedNtpTime() cache hit");
        return mCachedNtpTime;
    }

    public long getCachedNtpTimeReference() {
        return mCachedNtpElapsedRealtime;
    }
}