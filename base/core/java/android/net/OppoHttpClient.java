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

package android.net;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

//refresh time from oppo server
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URL;
import java.net.Proxy;
import org.xml.sax.SAXException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.XMLReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import java.io.StringReader;
import android.text.format.Time;
import java.util.TimeZone;
import org.xml.sax.helpers.DefaultHandler;


/**
 * {@link TrustedTime} that connects with a remote OPPO HTTP server as its trusted
 * time source.
 *
 * @hide
 */
public class OppoHttpClient {
    private static final String TAG = "OppoHttpClient";
	private static final boolean DEBUG = true;
    private long mHttpTime;
	private long mHttpTimeReference;
	// round trip time in milliseconds
    private long mRoundTripTime;
		
	private static final String oppoServerURL_RANDOM = "http://newds01.myoppo.com/autotime/dateandtime.xml?number=";
	private static final String oppoServerURL_RANDOM2 = "http://newds02.myoppo.com/autotime/dateandtime.xml?number=";
    private static final long GMT_BEIJING_OFFSET = 28800000;
    private static final long AVERAGE_RECEIVE_TIME = 832;
    
    public boolean requestTime(Context context, int selServerUrl, int timeout) {
    	return forceRefreshTimeFromOppoServer(context, selServerUrl, timeout);
    }
	
    private boolean forceRefreshTimeFromOppoServer(Context context, int selServerUrl, int timeout){
        boolean returnFlag = false;
		URL url = null;
        Log.d(TAG, "Enter forceRefreshTimeFromOppoServer run");
    	try {   
		    String oppoServerURL = oppoServerURL_RANDOM;
            if(selServerUrl > 0) {
				oppoServerURL = oppoServerURL_RANDOM2;
            }
            oppoServerURL += System.currentTimeMillis();
			url = new URL(oppoServerURL);
			if (DEBUG) {
				Log.i(TAG, "Cur http request:" + oppoServerURL);
			}
			HttpURLConnection httpconn = null;
			String proxyHost = android.net.Proxy.getDefaultHost();
			int proxyPort = android.net.Proxy.getDefaultPort();
			if (DEBUG) {
				Log.d(TAG, "OppoServer proxyHost = " + proxyHost + " proxyPort = " + proxyPort);
			}
			//establish the connection
			if (getNetType(context)) {
                Log.d(TAG, "Get network type success!");
				httpconn  = (HttpURLConnection) url.openConnection();
                Log.d(TAG, "HttpURLConnection open openConnection success!");
			} else {
				Log.d(TAG, "Use http proxy!");
				Proxy proxy = new Proxy(Proxy.Type.HTTP, 
						new InetSocketAddress(proxyHost, proxyPort));
				httpconn  = (HttpURLConnection)url.openConnection(proxy);
			}
			httpconn.setDoInput(true);
			//#ifdef VENDOR_EDIT
			//kecheng.Shang@SysApp.BT, 2012/12/17, Add for 
			httpconn.setUseCaches(false);   
			//#endif /* VENDOR_EDIT */
			if(selServerUrl > 0) {
				timeout = 3 * timeout;
			}
			Log.d(TAG, "timeout:" + timeout);
			httpconn.setConnectTimeout(timeout);
			httpconn.setReadTimeout(timeout);
			long requestTicks = SystemClock.elapsedRealtime();
			if (DEBUG) {
				Log.d(TAG, "Strart to connect http server!");
			}
			httpconn.connect();
			if (DEBUG) {
				Log.d(TAG, "Connect http server success!");
				/*ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				if(conn != null) {
					NetworkInfo info = conn.getActiveNetworkInfo();
					if(info != null) {
						Log.e(TAG, "State:" + info.getState() + " Type:" +  info.getType());
					}
				}*/
			}
			
			InputStreamReader mInputStreamReader = null;
			BufferedReader mBufferedReader = null;
			String mDateTimeXmlString = "";
			long mBeginParseTime = 0;
			long responseTicks = 0;
			mHttpTimeReference = 0;
			
			int responseCode = httpconn.getResponseCode();
			Log.d(TAG, "Http responseCode:" + responseCode);
			if (responseCode == HttpURLConnection.HTTP_OK) {
				//get the time when begin parse the xml file
				mBeginParseTime = System.currentTimeMillis();
				// read data from net stream
				mInputStreamReader= new InputStreamReader(httpconn.getInputStream(), "utf-8"); //GB2312
				mBufferedReader = new BufferedReader(mInputStreamReader);
				String lineString = "";
				while ((lineString = mBufferedReader.readLine()) != null) {
					mDateTimeXmlString = lineString;
				}
                Log.d(TAG, "Read response data success!");
			}
			responseTicks = SystemClock.elapsedRealtime();
			mHttpTimeReference = SystemClock.elapsedRealtime();
			//disconnect
			mBufferedReader.close();		
			mInputStreamReader.close();
			httpconn.disconnect();
			if (DEBUG) {
				Log.d(TAG, "Start to parser http response data!");
			}
			SAXParserFactory mSaxParserFactory = SAXParserFactory.newInstance();
	    	SAXParser mSaxParser = mSaxParserFactory.newSAXParser();
	    	XMLReader mXmlReader = mSaxParser.getXMLReader();
	    	DateTimeXmlParseHandler mDateTimeXmlParseHandler = new DateTimeXmlParseHandler();
	    	mXmlReader.setContentHandler(mDateTimeXmlParseHandler);
	    	mXmlReader.parse(new InputSource(new StringReader(mDateTimeXmlString)));
	    				    	
	    	String mDateString = mDateTimeXmlParseHandler.getDate();
	    	String[] dateStrings = mDateString.split("-");
	    	int[] mIntDateData = new int[3];
	    	for (int i = 0; i < dateStrings.length; i++) {
	         	mIntDateData[i] = Integer.parseInt(dateStrings[i]);
	        }
	    	
	    	String mTimeString = mDateTimeXmlParseHandler.getTime(); 
	    	String[] timeStrings = mTimeString.split(":");
	    	int[] mIntTimeData = new int[3];
	    	for (int i = 0; i < timeStrings.length; i++) {
	         	mIntTimeData[i] = Integer.parseInt(timeStrings[i]);
	        }
	    	
	    	Time mOppoServerTime = new Time();
	    	Log.d(TAG, "Parser time success, hour= " + mIntTimeData[0] + " minute = " + mIntTimeData[1]
	    	        + "seconds =" + mIntTimeData[2]);
	    	mOppoServerTime.set(mIntTimeData[2], mIntTimeData[1], mIntTimeData[0],
	    			mIntDateData[2], mIntDateData[1] - 1, mIntDateData[0]);
	    	long mGMTTime = mOppoServerTime.toMillis(true) - GMT_BEIJING_OFFSET;
	    	//get the time when end deal with the data
	    	long mEndParseTime = System.currentTimeMillis();
	    	long mNow = mGMTTime + TimeZone.getDefault().getRawOffset() 
	    		+ (mEndParseTime - mBeginParseTime) + AVERAGE_RECEIVE_TIME;
	    	// this calculate the daylight saving time(if the TimeZone uses it)
	    	int daylightOffset = TimeZone.getDefault().getOffset(mNow) - TimeZone.getDefault().getRawOffset();
	    	//addAutoTimeSettingPref(mNow + daylightOffset);
	        mHttpTime = mNow + daylightOffset;
	    	//SystemClock.setCurrentTimeMillis(mCachedHttpTime);  
			mRoundTripTime = responseTicks - requestTicks;
	    	returnFlag = true;
			
			//Solve http page cached by carrieroperator or others
			if (android.os.SystemProperties.getLong("persist.sys.lasttime", 0) >= mGMTTime) {
				Log.d(TAG, "Cached by carrieroperator or others, Need Ntp algin time!");
				Log.d(TAG, "mGMTTime:" + mGMTTime);
				returnFlag = false;
			} else {
				android.os.SystemProperties.set("persist.sys.lasttime", Long.toString(mGMTTime));
			}
		} catch (Exception e) {
			Log.e(TAG, "oppoServer exception: " + e);	
			returnFlag = false;
		} 
    	
    	return returnFlag;
	}

	private boolean getNetType(Context context) {
		ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if(conn == null) {
		   return false; 
		}
	   
		NetworkInfo info = conn.getActiveNetworkInfo();
		if(info == null) {
			return false;
		}

		String type = info.getTypeName();  //MOBILE(GPRS)WIFI
		if(type.equalsIgnoreCase("WIFI")) {
			return true;
		} else if(type.equalsIgnoreCase("MOBILE") || type.equalsIgnoreCase("GPRS")) {
			String apn = info.getExtraInfo();
			if(apn != null && apn.equalsIgnoreCase("cmwap")) {
				return false;
			} else {
				return true;
			}
		}
		
	   return true;
	}
	
	public class DateTimeXmlParseHandler extends DefaultHandler {
		private boolean mIsTimeZoneFlag = false;
		private boolean mIsDateFlag = false;
		private boolean mIsTimeFlag = false;
		private String mTimeZoneString = "";
		private String mDateString = "";
		private String mTimeString = "";
		
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			// TODO Auto-generated method stub
			super.characters(ch, start, length);
			if (mIsTimeZoneFlag) {
				mTimeZoneString = new String(ch, start, length);
			} else if (mIsDateFlag) {
				mDateString = new String(ch, start, length);
			} else if (mIsTimeFlag) {
				mTimeString = new String(ch, start, length);
			}
		}

		public void endDocument() throws SAXException {
			// TODO Auto-generated method stub
			super.endDocument();
		}

		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			// TODO Auto-generated method stub
			super.endElement(uri, localName, qName);
			if (localName.equals("TimeZone")) {
				mIsTimeZoneFlag = false;
			} else if (localName.equals("Date")) {
				mIsDateFlag = false;
			} else if (localName.equals("Time")) {
				mIsTimeFlag = false;
			}
		}
		
		public void startDocument() throws SAXException {
			// TODO Auto-generated method stub
			super.startDocument();
		}
		
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			// TODO Auto-generated method stub
			super.startElement(uri, localName, qName, attributes);
			if (localName.equals("TimeZone")) {
				mIsTimeZoneFlag = true;
			}
			else if (localName.equals("Date")) {
				mIsDateFlag = true;
			}
			else if (localName.equals("Time")) {
				mIsTimeFlag = true;
			}
		}
		
		public String getTimeZone() {
			return mTimeZoneString;
		}
		
		public String getDate() {
			return mDateString;
		}
		
		public String getTime() {
			return mTimeString;
		}
		
	}
	//#endif /* VENDOR_EDIT */
		
  /**
     * Returns the time computed from the NTP transaction.
     *
     * @return time value computed from NTP server response.
     */
    public long getHttpTime() {
        return mHttpTime;
    }
	
  /**
     * Returns the reference clock value (value of SystemClock.elapsedRealtime())
     * corresponding to the NTP time.
     *
     * @return reference clock corresponding to the NTP time.
     */
    public long getHttpTimeReference() {
        return mHttpTimeReference;
    }

    /**
     * Returns the round trip time of the NTP transaction
     *
     * @return round trip time in milliseconds.
     */
    public long getRoundTripTime() {
        return mRoundTripTime;
    }
	
}
