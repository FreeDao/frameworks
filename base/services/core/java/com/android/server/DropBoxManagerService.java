/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Debug;
import android.os.DropBoxManager;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.Time;
import android.util.Slog;

import com.android.internal.os.IDropBoxManagerService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;
//#ifdef VENDOR_EDIT
//Chenps@OnLineRD.framework.oppo_debug, 2012-05-29 add
import android.os.SystemProperties;
import android.os.Environment;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import android.app.ActivityManager;
import com.oppo.debug.ASSERT;
//#endif /* VENDOR_EDIT */
//#ifdef VENDOR_EDIT
//licx@OnLineRD.framework.oppo_debug, 2012.07.25 add
import android.app.ActivityManagerNative;
//#endif /* VENDOR_EDIT */
//#ifdef VENDOR_EDIT
//wangjw@OnLineRD.framework.oppo_debug, 2013/07/12, add for imei no 
import android.telephony.TelephonyManager;
//wangjw@OnLineRD.framework.oppo_debug, 2013/07/25, add for key log 
import android.os.OppoManager;
import org.apache.http.util.EncodingUtils;
//#endif /* VENDOR_EDIT */

/**
 * Implementation of {@link IDropBoxManagerService} using the filesystem.
 * Clients use {@link DropBoxManager} to access this service.
 */
public final class DropBoxManagerService extends IDropBoxManagerService.Stub {
    private static final String TAG = "DropBoxManagerService";
    private static final int DEFAULT_AGE_SECONDS = 3 * 86400;
    private static final int DEFAULT_MAX_FILES = 1000;
    private static final int DEFAULT_QUOTA_KB = 5 * 1024;
    private static final int DEFAULT_QUOTA_PERCENT = 10;
    private static final int DEFAULT_RESERVE_PERCENT = 10;
    private static final int QUOTA_RESCAN_MILLIS = 5000;

    // mHandler 'what' value.
    private static final int MSG_SEND_BROADCAST = 1;
	//#ifdef VENDOR_EDIT
	//wangjw@OnLineRD.framework.oppo_debug, 2013/07/12, add for imei no 
    // add a new message type to get imei_no 	
    private static final int MSG_GET_IMEI_NO = 2;
    private static final int GET_IMEI_NO_DELAY = 30*1000;	
	//#endif /* VENDOR_EDIT */
    private static final boolean PROFILE_DUMP = false;

    // TODO: This implementation currently uses one file per entry, which is
    // inefficient for smallish entries -- consider using a single queue file
    // per tag (or even globally) instead.

    // The cached context and derived objects

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final File mDropBoxDir;

    // Accounting of all currently written log files (set in init()).

    private FileList mAllFiles = null;
    private HashMap<String, FileList> mFilesByTag = null;

    // Various bits of disk information

    private StatFs mStatFs = null;
    private int mBlockSize = 0;
    private int mCachedQuotaBlocks = 0;  // Space we can use: computed from free space, etc.
    private long mCachedQuotaUptimeMillis = 0;

    private volatile boolean mBooted = false;

    // Provide a way to perform sendBroadcast asynchronously to avoid deadlocks.
    private final Handler mHandler;

    /** Receives events that might indicate a need to clean up files. */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                mBooted = true;
                return;
            }

            // Else, for ACTION_DEVICE_STORAGE_LOW:
            mCachedQuotaUptimeMillis = 0;  // Force a re-check of quota size

            // Run the initialization in the background (not this main thread).
            // The init() and trimToFit() methods are synchronized, so they still
            // block other users -- but at least the onReceive() call can finish.
            new Thread() {
                public void run() {
                    try {
                        init();
                        trimToFit();
                    } catch (IOException e) {
                        Slog.e(TAG, "Can't init", e);
                    }
                }
            }.start();
        }
    };

    /**
     * Creates an instance of managed drop box storage.  Normally there is one of these
     * run by the system, but others can be created for testing and other purposes.
     *
     * @param context to use for receiving free space & gservices intents
     * @param path to store drop box entries in
     */
    public DropBoxManagerService(final Context context, File path) {
        mDropBoxDir = path;

        // Set up intent receivers
        mContext = context;
        mContentResolver = context.getContentResolver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        context.registerReceiver(mReceiver, filter);

        mContentResolver.registerContentObserver(
            Settings.Global.CONTENT_URI, true,
            new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    mReceiver.onReceive(context, (Intent) null);
                }
            });

        mHandler = new Handler() {
			//#ifdef VENDOR_EDIT
			//wangjw@OnLineRD.framework.oppo_debug, 2013/07/12, add for imei no 
			// set try times	
			private  int mRetry = 3;
		   //#endif /* VENDOR_EDIT */
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_SEND_BROADCAST) {
                    mContext.sendBroadcastAsUser((Intent)msg.obj, UserHandle.OWNER,
                            android.Manifest.permission.READ_LOGS);
                }
				//#ifdef VENDOR_EDIT
				//wangjw@OnLineRD.framework.oppo_debug, 2013/07/12, add for imei no 
				// set imei no to property.
				else if(msg.what == MSG_GET_IMEI_NO){
					if(!setOppoDeviceImeiNO(mContext)&&(mRetry--)>0){
						sendMessageDelayed(obtainMessage(MSG_GET_IMEI_NO),GET_IMEI_NO_DELAY);
					}
				}
				//#endif /* VENDOR_EDIT */
            }
        };
		//#ifdef VENDOR_EDIT
		//wangjw@OnLineRD.framework.oppo_debug, 2013/07/12, add for imei no 
		// send a delay message
		mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_GET_IMEI_NO),GET_IMEI_NO_DELAY);
        // The real work gets done lazily in init() -- that way service creation always
        // succeeds, and things like disk problems cause individual method failures.
    }

    /** Unregisters broadcast receivers and any other hooks -- for test instances */
    public void stop() {
        mContext.unregisterReceiver(mReceiver);
    }

    //#ifdef VENDOR_EDIT
	//wangjw@OnLineRD.framework.oppo_debug, 2013/07/12,set imei no to property.
	private boolean setOppoDeviceImeiNO(Context ctx) {
		String INVLAID_IMEI = "invalid imei";
		String PERSIST_KEY = "persist.sys.oppo.device.imei";
		String id = SystemProperties.get(PERSIST_KEY, INVLAID_IMEI);
		if (INVLAID_IMEI.equals(id)||"0".equals(id)) {
			TelephonyManager telephonyManager = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
			if (null == telephonyManager) {
				Slog.w(TAG, "getOppoDeviceImeiNO:telephonyManager null, can not get imei NO!");
				return false;
			}
			String imei = telephonyManager.getDeviceId();
			if (null != imei && !("0".equals(imei))) {
				Slog.i(TAG, "getOppoDeviceImeiNO: set persist.sys.oppo.device.imei to:" + imei);
				SystemProperties.set(PERSIST_KEY, imei);
				return true;
			} else {
				Slog.w(TAG, "getOppoDeviceImeiNO:can not get right imei NO!");
				return false;
			}
		} else {
			Slog.i(TAG, "getOppoDeviceImeiNO imei:" + id);
			return true;
		}
	}
	//#endif /* VENDOR_EDIT */
	
    //#ifdef VENDOR_EDIT
    //licx@OnLineRD.framework.oppo_debug, 2012.07.25 add
    private static final String BOOT_REASON_FILE = "/sys/power/app_boot";
    private String readBootReason() {
        String res = "";
        try {
            FileInputStream fin = new FileInputStream(BOOT_REASON_FILE);
            int length = fin.available();
            byte[] buffer = new byte[length];
            fin.read(buffer);
            res = org.apache.http.util.EncodingUtils.getString(buffer, "UTF-8").trim();
            fin.close();
        } catch (Exception e) {
            e.printStackTrace();
        }	
        return res;
    }
    //#endif /* VENDOR_EDIT */
    
    //#ifdef VENDOR_EDIT
    //Chenps@OnLineRD.framework.oppo_debug, 2012-08-09 add
    private void sendFeedbackBroadcast(ArrayList<String> files) {
		String pakageName = null;
		try {
			pakageName = ActivityManagerNative.getDefault().getTopActivityComponentName().getPackageName();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// if feedback is top activity, do not send broadcast
		if(!"com.nearme.feedback".equals(pakageName)){
			String bootReason = readBootReason();
			Intent dropboxIntent = new Intent("com.nearme.feedback.feedback");										
			dropboxIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
			dropboxIntent.putStringArrayListExtra("filePath", files);		                    									
			dropboxIntent.putExtra("boot_reason", bootReason);
			if (!mBooted) {
				dropboxIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
			}												
			mContext.sendBroadcast(dropboxIntent);			
		}
	}
    //#endif /* VENDOR_EDIT */

    @Override
    public void add(DropBoxManager.Entry entry) {
        File temp = null;
        OutputStream output = null;
        final String tag = entry.getTag();
		
        //#ifdef VENDOR_EDIT
        //Jason.Lee@OnLineRD.Framework.oppo_debug, 2013/12/12, Add for oppo assert
        final boolean qtassert = SystemProperties.getBoolean("persist.sys.assert.enable", false);
        final boolean qeassert =  SystemProperties.getBoolean("persist.sys.assert.panic", false);
        final boolean assertEnable = qtassert || qeassert;
        //add assert information to logcat
        Slog.i(TAG,"QT Assert:"+qtassert+";QE Assert:"+qeassert);    
        //#endif /* VENDOR_EDIT */
		
        try {
            int flags = entry.getFlags();
            if ((flags & DropBoxManager.IS_EMPTY) != 0) throw new IllegalArgumentException();

            init();
            if (!isTagEnabled(tag)) return;

            //#ifdef VENDOR_EDIT
            //Chenps@OnLineRD.framework.oppo_debug, 2012-08-09 Chenps add begin for gz file because if
            //do this way, it will be gz once			
			if ( assertEnable && tag.equals("SYSTEM_SERVER_GZ")) {
	            String systemcrashFile = SystemProperties.get("persist.sys.send.file", "null");
				if (!systemcrashFile.equals("null")) {
                    ArrayList<String> gzFiles = new ArrayList<String>();
                    gzFiles.add(systemcrashFile);
                    sendFeedbackBroadcast(gzFiles);					
				}
			}
            //#endif /* VENDOR_EDIT */

            long max = trimToFit();
            long lastTrim = System.currentTimeMillis();

            byte[] buffer = new byte[mBlockSize];
            InputStream input = entry.getInputStream();

            // First, accumulate up to one block worth of data in memory before
            // deciding whether to compress the data or not.

            int read = 0;
            while (read < buffer.length) {
                int n = input.read(buffer, read, buffer.length - read);
                if (n <= 0) break;
                read += n;
            }

            // If we have at least one block, compress it -- otherwise, just write
            // the data in uncompressed form.

            temp = new File(mDropBoxDir, "drop" + Thread.currentThread().getId() + ".tmp");
            int bufferSize = mBlockSize;
            if (bufferSize > 4096) bufferSize = 4096;
            if (bufferSize < 512) bufferSize = 512;
            FileOutputStream foutput = new FileOutputStream(temp);
            output = new BufferedOutputStream(foutput, bufferSize);
            if (read == buffer.length && ((flags & DropBoxManager.IS_GZIPPED) == 0)) {
                output = new GZIPOutputStream(output);
                flags = flags | DropBoxManager.IS_GZIPPED;
            }
			//#ifndef VENDOR_EDIT
            //wangjw@OnLineRD.framework.oppo_debug, 2013-07-25 add for key log
			//very funny,write tag begin,then log,tag end!
            if(!assertEnable && tag.equals(OppoManager.ANDROID_PANIC_TAG)){
	          int size = OppoManager.writeRawPartition(OppoManager.ANDROID_PANIC_TAG_BEGIN);	
		    Slog.d(TAG, "write tag begin size : "+size);
			}
			//#endif /* VENDOR_EDIT */
            do {
                output.write(buffer, 0, read);
				//#ifndef VENDOR_EDIT
            	//wangjw@OnLineRD.framework.oppo_debug, 2013-07-25 add for key log
				// write data to raw area
				if(!assertEnable && tag.equals(OppoManager.ANDROID_PANIC_TAG)){
					String text = EncodingUtils.getString(buffer,0,read,"UTF-8");
					int size = OppoManager.writeRawPartition(text);	
					Slog.d(TAG, "write system server log size : "+size);
				}
				//#endif /* VENDOR_EDIT */

                long now = System.currentTimeMillis();
                if (now - lastTrim > 30 * 1000) {
                    max = trimToFit();  // In case data dribbles in slowly
                    lastTrim = now;
                }

                read = input.read(buffer);
                if (read <= 0) {
                    FileUtils.sync(foutput);
                    output.close();  // Get a final size measurement
                    output = null;
                } else {
                    output.flush();  // So the size measurement is pseudo-reasonable
                }

                long len = temp.length();
                if (len > max) {
                    Slog.w(TAG, "Dropping: " + tag + " (" + temp.length() + " > " + max + " bytes)");
                    temp.delete();
                    temp = null;  // Pass temp = null to createEntry() to leave a tombstone
                    break;
                }
            } while (read > 0);
			//#ifndef VENDOR_EDIT
			//wangjw@OnLineRD.framework.oppo_debug, 2013-07-25 add for key log
			// write end tag to raw partition	
			if(!assertEnable && tag.equals(OppoManager.ANDROID_PANIC_TAG)){
				int size = OppoManager.writeRawPartition("\n"+OppoManager.ANDROID_PANIC_TAG_END);	
				Slog.d(TAG, "write tag end size : "+size);
			}
			//#endif /* VENDOR_EDIT */
            long time = createEntry(temp, tag, flags);
            temp = null;

            //#ifndef VENDOR_EDIT
            //Chenps@OnLineRD.framework.oppo_debug, 2012-05-29 modify
            /*
            final Intent dropboxIntent = new Intent(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED);
            dropboxIntent.putExtra(DropBoxManager.EXTRA_TAG, tag);
            dropboxIntent.putExtra(DropBoxManager.EXTRA_TIME, time);
            if (!mBooted) {
                dropboxIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            }
            // Call sendBroadcast after returning from this call to avoid deadlock. In particular
            // the caller may be holding the WindowManagerService lock but sendBroadcast requires a
            // lock in ActivityManagerService. ActivityManagerService has been caught holding that
            // very lock while waiting for the WindowManagerService lock.
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SEND_BROADCAST, dropboxIntent));
            */
			//#else /* VENDOR_EDIT */
            File[] logFiles = new File("/data/system/dropbox").listFiles();
            String name = null;
            for (int i = 0; logFiles != null && i < logFiles.length; i++) {
                name = logFiles[i].getName();   
                if (name.endsWith(".gz")) {
                    name = name.substring(0, name.length() - 3);
                }
                if (name.endsWith(".lost")) {
                    name = name.substring(0, name.length() - 5);
                } else if (name.endsWith(".txt")) {
                    name = name.substring(0, name.length() - 4);
                } else if (name.endsWith(".dat")) {
                    name = name.substring(0, name.length() - 4);
                }
				
                if (name.equals(tag + "@" + time)) {
                    ArrayList<String> mFiles = new ArrayList<String>();
                    mFiles.add("/data/system/dropbox/" + logFiles[i].getName());
                    Slog.d(TAG, "file :: " + "/data/system/dropbox/" + logFiles[i].getName());
                    if (assertEnable) {
                        if (tag.startsWith("system_server", 0)                           
                            || tag.equals("system_app_crash")
                            || tag.equals("system_app_anr")
                            || tag.equals("data_app_crash")
                            || tag.equals("data_app_anr")) {				                    	
                            ASSERT.epitaph(logFiles[i], tag, flags);
                        }
                    } else {
                        if (tag.startsWith("system_server", 0)) {
						    if (SystemProperties.getLong("ro.runtime.panic", 0) == 0) {
							    SystemProperties.set("ro.runtime.panic", "1");
                                SystemProperties.set("persist.sys.panic.file", "/data/system/dropbox/" + logFiles[i].getName());
							}
                        } else if (tag.equals("SYSTEM_SERVER")
						            || tag.equals("SYSTEM_LAST_KMSG")) {
                            sendFeedbackBroadcast(mFiles);
                        }        
                    }
                }
            }
            //#endif /* VENDOR_EDIT */
        } catch (IOException e) {
            Slog.e(TAG, "Can't write: " + tag, e);
        } finally {
            try { if (output != null) output.close(); } catch (IOException e) {}
            entry.close();
            if (temp != null) temp.delete();
        }
    }

    public boolean isTagEnabled(String tag) {
        final long token = Binder.clearCallingIdentity();
        try {
            return !"disabled".equals(Settings.Global.getString(
                    mContentResolver, Settings.Global.DROPBOX_TAG_PREFIX + tag));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public synchronized DropBoxManager.Entry getNextEntry(String tag, long millis) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.READ_LOGS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("READ_LOGS permission required");
        }

        try {
            init();
        } catch (IOException e) {
            Slog.e(TAG, "Can't init", e);
            return null;
        }

        FileList list = tag == null ? mAllFiles : mFilesByTag.get(tag);
        if (list == null) return null;

        for (EntryFile entry : list.contents.tailSet(new EntryFile(millis + 1))) {
            if (entry.tag == null) continue;
            if ((entry.flags & DropBoxManager.IS_EMPTY) != 0) {
                return new DropBoxManager.Entry(entry.tag, entry.timestampMillis);
            }
            try {
                return new DropBoxManager.Entry(
                        entry.tag, entry.timestampMillis, entry.file, entry.flags);
            } catch (IOException e) {
                Slog.e(TAG, "Can't read: " + entry.file, e);
                // Continue to next file
            }
        }

        return null;
    }

    public synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: Can't dump DropBoxManagerService");
            return;
        }

        try {
            init();
        } catch (IOException e) {
            pw.println("Can't initialize: " + e);
            Slog.e(TAG, "Can't init", e);
            return;
        }

        if (PROFILE_DUMP) Debug.startMethodTracing("/data/trace/dropbox.dump");

        StringBuilder out = new StringBuilder();
        boolean doPrint = false, doFile = false;
        ArrayList<String> searchArgs = new ArrayList<String>();
        for (int i = 0; args != null && i < args.length; i++) {
            if (args[i].equals("-p") || args[i].equals("--print")) {
                doPrint = true;
            } else if (args[i].equals("-f") || args[i].equals("--file")) {
                doFile = true;
            } else if (args[i].startsWith("-")) {
                out.append("Unknown argument: ").append(args[i]).append("\n");
            } else {
                searchArgs.add(args[i]);
            }
        }

        out.append("Drop box contents: ").append(mAllFiles.contents.size()).append(" entries\n");

        if (!searchArgs.isEmpty()) {
            out.append("Searching for:");
            for (String a : searchArgs) out.append(" ").append(a);
            out.append("\n");
        }

        int numFound = 0, numArgs = searchArgs.size();
        Time time = new Time();
        out.append("\n");
        for (EntryFile entry : mAllFiles.contents) {
            time.set(entry.timestampMillis);
            String date = time.format("%Y-%m-%d %H:%M:%S");
            boolean match = true;
            for (int i = 0; i < numArgs && match; i++) {
                String arg = searchArgs.get(i);
                match = (date.contains(arg) || arg.equals(entry.tag));
            }
            if (!match) continue;

            numFound++;
            if (doPrint) out.append("========================================\n");
            out.append(date).append(" ").append(entry.tag == null ? "(no tag)" : entry.tag);
            if (entry.file == null) {
                out.append(" (no file)\n");
                continue;
            } else if ((entry.flags & DropBoxManager.IS_EMPTY) != 0) {
                out.append(" (contents lost)\n");
                continue;
            } else {
                out.append(" (");
                if ((entry.flags & DropBoxManager.IS_GZIPPED) != 0) out.append("compressed ");
                out.append((entry.flags & DropBoxManager.IS_TEXT) != 0 ? "text" : "data");
                out.append(", ").append(entry.file.length()).append(" bytes)\n");
            }

            if (doFile || (doPrint && (entry.flags & DropBoxManager.IS_TEXT) == 0)) {
                if (!doPrint) out.append("    ");
                out.append(entry.file.getPath()).append("\n");
            }

            if ((entry.flags & DropBoxManager.IS_TEXT) != 0 && (doPrint || !doFile)) {
                DropBoxManager.Entry dbe = null;
                InputStreamReader isr = null;
                try {
                    dbe = new DropBoxManager.Entry(
                             entry.tag, entry.timestampMillis, entry.file, entry.flags);

                    if (doPrint) {
                        isr = new InputStreamReader(dbe.getInputStream());
                        char[] buf = new char[4096];
                        boolean newline = false;
                        for (;;) {
                            int n = isr.read(buf);
                            if (n <= 0) break;
                            out.append(buf, 0, n);
                            newline = (buf[n - 1] == '\n');

                            // Flush periodically when printing to avoid out-of-memory.
                            if (out.length() > 65536) {
                                pw.write(out.toString());
                                out.setLength(0);
                            }
                        }
                        if (!newline) out.append("\n");
                    } else {
                        String text = dbe.getText(70);
                        boolean truncated = (text.length() == 70);
                        out.append("    ").append(text.trim().replace('\n', '/'));
                        if (truncated) out.append(" ...");
                        out.append("\n");
                    }
                } catch (IOException e) {
                    out.append("*** ").append(e.toString()).append("\n");
                    Slog.e(TAG, "Can't read: " + entry.file, e);
                } finally {
                    if (dbe != null) dbe.close();
                    if (isr != null) {
                        try {
                            isr.close();
                        } catch (IOException unused) {
                        }
                    }
                }
            }

            if (doPrint) out.append("\n");
        }

        if (numFound == 0) out.append("(No entries found.)\n");

        if (args == null || args.length == 0) {
            if (!doPrint) out.append("\n");
            out.append("Usage: dumpsys dropbox [--print|--file] [YYYY-mm-dd] [HH:MM:SS] [tag]\n");
        }

        pw.write(out.toString());
        if (PROFILE_DUMP) Debug.stopMethodTracing();
    }

    ///////////////////////////////////////////////////////////////////////////

    /** Chronologically sorted list of {@link #EntryFile} */
    private static final class FileList implements Comparable<FileList> {
        public int blocks = 0;
        public final TreeSet<EntryFile> contents = new TreeSet<EntryFile>();

        /** Sorts bigger FileList instances before smaller ones. */
        public final int compareTo(FileList o) {
            if (blocks != o.blocks) return o.blocks - blocks;
            if (this == o) return 0;
            if (hashCode() < o.hashCode()) return -1;
            if (hashCode() > o.hashCode()) return 1;
            return 0;
        }
    }

    /** Metadata describing an on-disk log file. */
    private static final class EntryFile implements Comparable<EntryFile> {
        public final String tag;
        public final long timestampMillis;
        public final int flags;
        public final File file;
        public final int blocks;

        /** Sorts earlier EntryFile instances before later ones. */
        public final int compareTo(EntryFile o) {
            if (timestampMillis < o.timestampMillis) return -1;
            if (timestampMillis > o.timestampMillis) return 1;
            if (file != null && o.file != null) return file.compareTo(o.file);
            if (o.file != null) return -1;
            if (file != null) return 1;
            if (this == o) return 0;
            if (hashCode() < o.hashCode()) return -1;
            if (hashCode() > o.hashCode()) return 1;
            return 0;
        }

        /**
         * Moves an existing temporary file to a new log filename.
         * @param temp file to rename
         * @param dir to store file in
         * @param tag to use for new log file name
         * @param timestampMillis of log entry
         * @param flags for the entry data
         * @param blockSize to use for space accounting
         * @throws IOException if the file can't be moved
         */
        public EntryFile(File temp, File dir, String tag,long timestampMillis,
                         int flags, int blockSize) throws IOException {
            if ((flags & DropBoxManager.IS_EMPTY) != 0) throw new IllegalArgumentException();

            this.tag = tag;
            this.timestampMillis = timestampMillis;
            this.flags = flags;
            this.file = new File(dir, Uri.encode(tag) + "@" + timestampMillis +
                    ((flags & DropBoxManager.IS_TEXT) != 0 ? ".txt" : ".dat") +
                    ((flags & DropBoxManager.IS_GZIPPED) != 0 ? ".gz" : ""));

            if (!temp.renameTo(this.file)) {
                throw new IOException("Can't rename " + temp + " to " + this.file);
            }
            this.blocks = (int) ((this.file.length() + blockSize - 1) / blockSize);
        }

        /**
         * Creates a zero-length tombstone for a file whose contents were lost.
         * @param dir to store file in
         * @param tag to use for new log file name
         * @param timestampMillis of log entry
         * @throws IOException if the file can't be created.
         */
        public EntryFile(File dir, String tag, long timestampMillis) throws IOException {
            this.tag = tag;
            this.timestampMillis = timestampMillis;
            this.flags = DropBoxManager.IS_EMPTY;
            this.file = new File(dir, Uri.encode(tag) + "@" + timestampMillis + ".lost");
            this.blocks = 0;
            new FileOutputStream(this.file).close();
        }

        /**
         * Extracts metadata from an existing on-disk log filename.
         * @param file name of existing log file
         * @param blockSize to use for space accounting
         */
        public EntryFile(File file, int blockSize) {
            this.file = file;
            this.blocks = (int) ((this.file.length() + blockSize - 1) / blockSize);

            String name = file.getName();
            int at = name.lastIndexOf('@');
            if (at < 0) {
                this.tag = null;
                this.timestampMillis = 0;
                this.flags = DropBoxManager.IS_EMPTY;
                return;
            }

            int flags = 0;
            this.tag = Uri.decode(name.substring(0, at));
            if (name.endsWith(".gz")) {
                flags |= DropBoxManager.IS_GZIPPED;
                name = name.substring(0, name.length() - 3);
            }
            if (name.endsWith(".lost")) {
                flags |= DropBoxManager.IS_EMPTY;
                name = name.substring(at + 1, name.length() - 5);
            } else if (name.endsWith(".txt")) {
                flags |= DropBoxManager.IS_TEXT;
                name = name.substring(at + 1, name.length() - 4);
            } else if (name.endsWith(".dat")) {
                name = name.substring(at + 1, name.length() - 4);
            } else {
                this.flags = DropBoxManager.IS_EMPTY;
                this.timestampMillis = 0;
                return;
            }
            this.flags = flags;

            long millis;
            try { millis = Long.valueOf(name); } catch (NumberFormatException e) { millis = 0; }
            this.timestampMillis = millis;
        }

        /**
         * Creates a EntryFile object with only a timestamp for comparison purposes.
         * @param timestampMillis to compare with.
         */
        public EntryFile(long millis) {
            this.tag = null;
            this.timestampMillis = millis;
            this.flags = DropBoxManager.IS_EMPTY;
            this.file = null;
            this.blocks = 0;
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    /** If never run before, scans disk contents to build in-memory tracking data. */
    private synchronized void init() throws IOException {
        if (mStatFs == null) {
            if (!mDropBoxDir.isDirectory() && !mDropBoxDir.mkdirs()) {
                throw new IOException("Can't mkdir: " + mDropBoxDir);
            }
            try {
                mStatFs = new StatFs(mDropBoxDir.getPath());
                mBlockSize = mStatFs.getBlockSize();
            } catch (IllegalArgumentException e) {  // StatFs throws this on error
                throw new IOException("Can't statfs: " + mDropBoxDir);
            }
        }

        if (mAllFiles == null) {
            File[] files = mDropBoxDir.listFiles();
            if (files == null) throw new IOException("Can't list files: " + mDropBoxDir);

            mAllFiles = new FileList();
            mFilesByTag = new HashMap<String, FileList>();

            // Scan pre-existing files.
            for (File file : files) {
                if (file.getName().endsWith(".tmp")) {
                    Slog.i(TAG, "Cleaning temp file: " + file);
                    file.delete();
                    continue;
                }

                EntryFile entry = new EntryFile(file, mBlockSize);
                if (entry.tag == null) {
                    Slog.w(TAG, "Unrecognized file: " + file);
                    continue;
                } else if (entry.timestampMillis == 0) {
                    Slog.w(TAG, "Invalid filename: " + file);
                    file.delete();
                    continue;
                }

                enrollEntry(entry);
            }
        }
    }

    /** Adds a disk log file to in-memory tracking for accounting and enumeration. */
    private synchronized void enrollEntry(EntryFile entry) {
        mAllFiles.contents.add(entry);
        mAllFiles.blocks += entry.blocks;

        // mFilesByTag is used for trimming, so don't list empty files.
        // (Zero-length/lost files are trimmed by date from mAllFiles.)

        if (entry.tag != null && entry.file != null && entry.blocks > 0) {
            FileList tagFiles = mFilesByTag.get(entry.tag);
            if (tagFiles == null) {
                tagFiles = new FileList();
                mFilesByTag.put(entry.tag, tagFiles);
            }
            tagFiles.contents.add(entry);
            tagFiles.blocks += entry.blocks;
        }
    }

    /** Moves a temporary file to a final log filename and enrolls it. */
    private synchronized long createEntry(File temp, String tag, int flags) throws IOException {
        long t = System.currentTimeMillis();

        // Require each entry to have a unique timestamp; if there are entries
        // >10sec in the future (due to clock skew), drag them back to avoid
        // keeping them around forever.

        SortedSet<EntryFile> tail = mAllFiles.contents.tailSet(new EntryFile(t + 10000));
        EntryFile[] future = null;
        if (!tail.isEmpty()) {
            future = tail.toArray(new EntryFile[tail.size()]);
            tail.clear();  // Remove from mAllFiles
        }

        if (!mAllFiles.contents.isEmpty()) {
            t = Math.max(t, mAllFiles.contents.last().timestampMillis + 1);
        }

        if (future != null) {
            for (EntryFile late : future) {
                mAllFiles.blocks -= late.blocks;
                FileList tagFiles = mFilesByTag.get(late.tag);
                if (tagFiles != null && tagFiles.contents.remove(late)) {
                    tagFiles.blocks -= late.blocks;
                }
                if ((late.flags & DropBoxManager.IS_EMPTY) == 0) {
                    enrollEntry(new EntryFile(
                            late.file, mDropBoxDir, late.tag, t++, late.flags, mBlockSize));
                } else {
                    enrollEntry(new EntryFile(mDropBoxDir, late.tag, t++));
                }
            }
        }

        if (temp == null) {
            enrollEntry(new EntryFile(mDropBoxDir, tag, t));
        } else {
            enrollEntry(new EntryFile(temp, mDropBoxDir, tag, t, flags, mBlockSize));
        }
        return t;
    }

    /**
     * Trims the files on disk to make sure they aren't using too much space.
     * @return the overall quota for storage (in bytes)
     */
    private synchronized long trimToFit() {
        // Expunge aged items (including tombstones marking deleted data).

        int ageSeconds = Settings.Global.getInt(mContentResolver,
                Settings.Global.DROPBOX_AGE_SECONDS, DEFAULT_AGE_SECONDS);
        int maxFiles = Settings.Global.getInt(mContentResolver,
                Settings.Global.DROPBOX_MAX_FILES, DEFAULT_MAX_FILES);
        long cutoffMillis = System.currentTimeMillis() - ageSeconds * 1000;
        while (!mAllFiles.contents.isEmpty()) {
            EntryFile entry = mAllFiles.contents.first();
            if (entry.timestampMillis > cutoffMillis && mAllFiles.contents.size() < maxFiles) break;

            FileList tag = mFilesByTag.get(entry.tag);
            if (tag != null && tag.contents.remove(entry)) tag.blocks -= entry.blocks;
            if (mAllFiles.contents.remove(entry)) mAllFiles.blocks -= entry.blocks;
            if (entry.file != null) entry.file.delete();
        }

        // Compute overall quota (a fraction of available free space) in blocks.
        // The quota changes dynamically based on the amount of free space;
        // that way when lots of data is available we can use it, but we'll get
        // out of the way if storage starts getting tight.

        long uptimeMillis = SystemClock.uptimeMillis();
        if (uptimeMillis > mCachedQuotaUptimeMillis + QUOTA_RESCAN_MILLIS) {
            int quotaPercent = Settings.Global.getInt(mContentResolver,
                    Settings.Global.DROPBOX_QUOTA_PERCENT, DEFAULT_QUOTA_PERCENT);
            int reservePercent = Settings.Global.getInt(mContentResolver,
                    Settings.Global.DROPBOX_RESERVE_PERCENT, DEFAULT_RESERVE_PERCENT);
            int quotaKb = Settings.Global.getInt(mContentResolver,
                    Settings.Global.DROPBOX_QUOTA_KB, DEFAULT_QUOTA_KB);

            mStatFs.restat(mDropBoxDir.getPath());
            int available = mStatFs.getAvailableBlocks();
            int nonreserved = available - mStatFs.getBlockCount() * reservePercent / 100;
            int maximum = quotaKb * 1024 / mBlockSize;
            mCachedQuotaBlocks = Math.min(maximum, Math.max(0, nonreserved * quotaPercent / 100));
            mCachedQuotaUptimeMillis = uptimeMillis;
        }

        // If we're using too much space, delete old items to make room.
        //
        // We trim each tag independently (this is why we keep per-tag lists).
        // Space is "fairly" shared between tags -- they are all squeezed
        // equally until enough space is reclaimed.
        //
        // A single circular buffer (a la logcat) would be simpler, but this
        // way we can handle fat/bursty data (like 1MB+ bugreports, 300KB+
        // kernel crash dumps, and 100KB+ ANR reports) without swamping small,
        // well-behaved data streams (event statistics, profile data, etc).
        //
        // Deleted files are replaced with zero-length tombstones to mark what
        // was lost.  Tombstones are expunged by age (see above).

        if (mAllFiles.blocks > mCachedQuotaBlocks) {
            // Find a fair share amount of space to limit each tag
            int unsqueezed = mAllFiles.blocks, squeezed = 0;
            TreeSet<FileList> tags = new TreeSet<FileList>(mFilesByTag.values());
            for (FileList tag : tags) {
                if (squeezed > 0 && tag.blocks <= (mCachedQuotaBlocks - unsqueezed) / squeezed) {
                    break;
                }
                unsqueezed -= tag.blocks;
                squeezed++;
            }
            int tagQuota = (mCachedQuotaBlocks - unsqueezed) / squeezed;

            // Remove old items from each tag until it meets the per-tag quota.
            for (FileList tag : tags) {
                if (mAllFiles.blocks < mCachedQuotaBlocks) break;
                while (tag.blocks > tagQuota && !tag.contents.isEmpty()) {
                    EntryFile entry = tag.contents.first();
                    if (tag.contents.remove(entry)) tag.blocks -= entry.blocks;
                    if (mAllFiles.contents.remove(entry)) mAllFiles.blocks -= entry.blocks;

                    try {
                        if (entry.file != null) entry.file.delete();
                        enrollEntry(new EntryFile(mDropBoxDir, entry.tag, entry.timestampMillis));
                    } catch (IOException e) {
                        Slog.e(TAG, "Can't write tombstone file", e);
                    }
                }
            }
        }

        return mCachedQuotaBlocks * mBlockSize;
    }
}