/**
* Copyright 2008-2010 OPPO Mobile Comm Corp., Ltd, All rights reserved.
* FileName: ASSERT.java
* ModuleName: ASSERT
* Author : LanceYao@Plf.Framework.oppo_debug.
* VENDOR_EDIT
* Create Date:
* Description:

* History:
<version >  <time>  <author>  <desc>
*/
package com.oppo.debug;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * A set of assert methods.  Messages are displayed when an assert fails.
 */
public class ASSERT {
    private static final String TAG = "java.lang.ASSERT";
    private static final int MAX_CONTEXT_LENGTH = 20;
    private static final String ASSERT_ENABLE_PROP = "persist.sys.assert.enable";
    private static final String FILTEDPROC_PROP = "persist.assert.filtedproc";
    private static final String ASSERT_CACHE_PATH = "/cache/admin/assertlog";
    private static final Runtime rt = Runtime.getRuntime();
    private final static String ASSERT_PANIC_PROP = "persist.sys.assert.panic";

    //NOTE: Following flag must be same with DropBoxManager
    private static final int IS_EMPTY = 1;

    /** Flag value: Content is human-readable UTF-8 text (can be combined with IS_GZIPPED). */
    private static final int IS_TEXT = 2;

    /** Flag value: Content can be decompressed with {@link java.util.zip.GZIPOutputStream}. */
    private static final int IS_GZIPPED = 4;

    /**
     * Protect constructor since it is a static only class
     */
    protected ASSERT() {
    }

    static public void ASSERT(boolean condition) {
        assertTrue(condition);
    }

    static public void ASSERT(String message, boolean condition) {
        assertTrue(message, condition);
    }

    static public void ASSERTEXCEPTION(Throwable t) {
        fail(t);
    }

    static public void fail(Throwable t) {
        if (getAssertEnable() && !isFiltedProcess(null)) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);

            String stacktrace = sw.toString();

            String processname = getProcessName_native();

            if (processname == null) {
                processname = "NONE";
            }

            disableInterrupt();

            String displayinfo = getDisplayInfo(processname, stacktrace);
            String fn = getPath(processname);
            File logfile = new File(fn);
            saveErrorLog(logfile, displayinfo);

            displayErrorInfo(displayinfo + "\r\nPlease check" + fn +
                             ".txt for more information ...");
            //#ifndef VENDOR_EDIT
			//Fangfang.Hui@Prd.PlatSrv.OTA, 2014/03/13, Remove for lost some log when android reboot
			/*
				panicKernel();
			*/
			//#endif /* VENDOR_EDIT */ 
        }
    }

    /**
     * Fails a test with the given message.
     */
    static public void fail(String message) {
        //throw new AssertionFailedError(message);
        Error e = new Error(message);
        fail(e);
    }

    /**
     * Fails a test with no message.
     */
    static public void fail() {
        String msg = null;
        fail(msg);
    }

    /**
     * Asserts that a condition is true. If it isn't it throws
     * an AssertionFailedError with the given message.
     */
    static private void assertTrue(String message, boolean condition) {
        if (getAssertEnable() && !condition) {
            fail(message);
        }
    }

    /**
     * Asserts that a condition is true. If it isn't it throws
     * an AssertionFailedError.
     */
    static private void assertTrue(boolean condition) {
        assertTrue(null, condition);
    }

    /**
     * Asserts that a condition is false. If it isn't it throws
     * an AssertionFailedError with the given message.
     */
    static private void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    /**
     * Asserts that a condition is false. If it isn't it throws
     * an AssertionFailedError.
     */
    static private void assertFalse(boolean condition) {
        assertFalse(null, condition);
    }

    /**
     * Asserts that two objects are equal. If they are not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertEquals(String message, Object expected,
                                     Object actual) {
        if (!getAssertEnable() || ((expected == null) && (actual == null))) {
            return;
        }

        if ((expected != null) && expected.equals(actual)) {
            return;
        }

        failNotEquals(message, expected, actual);
    }

    /**
     * Asserts that two objects are equal. If they are not
     * an AssertionFailedError is thrown.
     */
    static private void assertEquals(Object expected, Object actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Asserts that two Strings are equal.
     */
    static private void assertEquals(String message, String expected,
                                     String actual) {
        if (!getAssertEnable() || ((expected == null) && (actual == null))) {
            return;
        }

        if ((expected != null) && expected.equals(actual)) {
            return;
        }

        //throw new ComparisonFailure(message, expected, actual);
        //
        fail(new ASSERTComparisonCompactor(MAX_CONTEXT_LENGTH, expected, actual).compact(
                 message));
    }

    /**
     * Asserts that two Strings are equal.
     */
    static private void assertEquals(String expected, String actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Asserts that two doubles are equal concerning a delta.  If they are not
     * an AssertionFailedError is thrown with the given message.  If the expected
     * value is infinity then the delta value is ignored.
     */
    static private void assertEquals(String message, double expected,
                                     double actual, double delta) {
        if (!getAssertEnable() || (Double.compare(expected, actual) == 0)) {
            return;
        }

        if (!(Math.abs(expected - actual) <= delta)) {
            failNotEquals(message, new Double(expected), new Double(actual));
        }
    }

    /**
     * Asserts that two doubles are equal concerning a delta. If the expected
     * value is infinity then the delta value is ignored.
     */
    static private void assertEquals(double expected, double actual,
                                     double delta) {
        assertEquals(null, expected, actual, delta);
    }

    /**
     * Asserts that two floats are equal concerning a delta. If they are not
     * an AssertionFailedError is thrown with the given message.  If the expected
     * value is infinity then the delta value is ignored.
     */
    static private void assertEquals(String message, float expected,
                                     float actual, float delta) {
        // handle infinity specially since subtracting to infinite values gives NaN and the
        // the following test fails
        if (getAssertEnable() && Float.isInfinite(expected)) {
            if (!(expected == actual)) {
                failNotEquals(message, new Float(expected), new Float(actual));
            }
        } else if (getAssertEnable() &&
                   !(Math.abs(expected - actual) <= delta)) {
            failNotEquals(message, new Float(expected), new Float(actual));
        }
    }

    /**
     * Asserts that two floats are equal concerning a delta. If the expected
     * value is infinity then the delta value is ignored.
     */
    static private void assertEquals(float expected, float actual, float delta) {
        assertEquals(null, expected, actual, delta);
    }

    /**
     * Asserts that two longs are equal. If they are not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertEquals(String message, long expected, long actual) {
        assertEquals(message, new Long(expected), new Long(actual));
    }

    /**
     * Asserts that two longs are equal.
     */
    static private void assertEquals(long expected, long actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Asserts that two booleans are equal. If they are not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertEquals(String message, boolean expected,
                                     boolean actual) {
        assertEquals(message, Boolean.valueOf(expected), Boolean.valueOf(actual));
    }

    /**
     * Asserts that two booleans are equal.
         */
    static private void assertEquals(boolean expected, boolean actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Asserts that two bytes are equal. If they are not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertEquals(String message, byte expected, byte actual) {
        assertEquals(message, new Byte(expected), new Byte(actual));
    }

    /**
         * Asserts that two bytes are equal.
     */
    static private void assertEquals(byte expected, byte actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Asserts that two chars are equal. If they are not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertEquals(String message, char expected, char actual) {
        assertEquals(message, new Character(expected), new Character(actual));
    }

    /**
     * Asserts that two chars are equal.
     */
    static private void assertEquals(char expected, char actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Asserts that two shorts are equal. If they are not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertEquals(String message, short expected,
                                     short actual) {
        assertEquals(message, new Short(expected), new Short(actual));
    }

    /**
    * Asserts that two shorts are equal.
    */
    static private void assertEquals(short expected, short actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Asserts that two ints are equal. If they are not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertEquals(String message, int expected, int actual) {
        assertEquals(message, new Integer(expected), new Integer(actual));
    }

    /**
    * Asserts that two ints are equal.
    */
    static private void assertEquals(int expected, int actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Asserts that an object isn't null.
     */
    static private void assertNotNull(Object object) {
        assertNotNull(null, object);
    }

    /**
     * Asserts that an object isn't null. If it is
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertNotNull(String message, Object object) {
        assertTrue(message, object != null);
    }

    /**
     * Asserts that an object is null.
     */
    static private void assertNull(Object object) {
        assertNull(null, object);
    }

    /**
     * Asserts that an object is null.  If it is not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertNull(String message, Object object) {
        assertTrue(message, object == null);
    }

    /**
     * Asserts that two objects refer to the same object. If they are not
     * an AssertionFailedError is thrown with the given message.
     */
    static private void assertSame(String message, Object expected,
                                   Object actual) {
        if (!getAssertEnable() || (expected == actual)) {
            return;
        }

        failNotSame(message, expected, actual);
    }

    /**
     * Asserts that two objects refer to the same object. If they are not
     * the same an AssertionFailedError is thrown.
     */
    static private void assertSame(Object expected, Object actual) {
        assertSame(null, expected, actual);
    }

    /**
     * Asserts that two objects do not refer to the same object. If they do
     * refer to the same object an AssertionFailedError is thrown with the
     * given message.
     */
    static private void assertNotSame(String message, Object expected,
                                      Object actual) {
        if (getAssertEnable() && (expected == actual)) {
            failSame(message);
        }
    }

    /**
     * Asserts that two objects do not refer to the same object. If they do
     * refer to the same object an AssertionFailedError is thrown.
     */
    static private void assertNotSame(Object expected, Object actual) {
        assertNotSame(null, expected, actual);
    }

    static private void failSame(String message) {
        String formatted = "";

        if (message != null) {
            formatted = message + " ";
        }

        fail(formatted + "expected not same");
    }

    static private void failNotSame(String message, Object expected,
                                    Object actual) {
        String formatted = "";

        if (message != null) {
            formatted = message + " ";
        }

        fail(formatted + "expected same:<" + expected + "> was not:<" + actual +
             ">");
    }

    static private void failNotEquals(String message, Object expected,
                                      Object actual) {
        fail(format(message, expected, actual));
    }

    static String format(String message, Object expected, Object actual) {
        String formatted = "";

        if (message != null) {
            formatted = message + " ";
        }

        return formatted + "expected:<" + expected + "> but was:<" + actual +
               ">\n";
    }

    //
    static private boolean isFiltedProcess(String process) {
        String filtedproc = null;
        String crashproc = (process != null) ? process : getProcessName_native();
        String prop = getSystemProperties_native(FILTEDPROC_PROP, "");

        if ((crashproc == null) || (prop == null)) {
            return false;
        }

        return prop.indexOf(crashproc) != -1;
    }

    static private boolean getAssertEnable() {
        return getSystemProperties_native(ASSERT_ENABLE_PROP, "false")
               .equals("true");
    }

    static private void disableInterrupt() {
        try {
            //rt.exec("/system/bin/chmod 0777 /proc/mask_input/mask_int");
            //rt.exec("echo mask > /proc/mask_input/mask_int");
            rt.exec("/system/bin/sh /system/bin/disableinterrupt.sh");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static private String getPath(String processName) {
        String logpath = null;
        String[] path = { ASSERT_CACHE_PATH, };

        for (String s : path) {
            File f = new File(s);

            if (f.exists() || f.mkdirs()) {
                logpath = s;

                break;
            }
        }

        if (logpath == null) {
            return null;
        }

        Date dt = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String now = "";
        now = df.format(dt);

        return (logpath + "/" + processName + "-" + now);
    }

    static private synchronized void saveErrorLog(File file, String trace) {
        writeFile(trace, file);
        execCommand("dumpstate -o " + file.toString() + " -t assert");
    }

    private static void execCommand(String command) {
        if (command == null) {
            return;
        }

        try {
            java.lang.Process process = rt.exec(command);
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    process.getInputStream()));

            try {
                /**
                * The methods that create processes may not work well for special processes on certain native platforms,
                * such as native windowing processes, daemon processes, Win16/DOS processes on Microsoft Windows, or shell scripts.
                * The created subprocess does not have its own terminal or console.
                * All its standard io(i.e. stdin, stdout, stderr) operations will be redirected to the parent process through
                * three streams(getOutputStream(), getInputStream(), getErrorStream()).
                * The parent process uses these streams to feed input to and get output from the subprocess.
                * Because some native platforms only provide limited buffer size for standard input and output streams,
                * failure to promptly write the input stream or read the output stream of the subprocess may cause the subprocess
                * to block, and even deadlock.
                */
                while (br.readLine() != null)
                    ;

                process.waitFor();
            } finally {
                br.close();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // copy a file from srcFile to destFile, return true if succeed, return
    // false if fail
    private static boolean copyFile(File srcFile, File destFile) {
        boolean result = false;

        try {
            InputStream in = new FileInputStream(srcFile);

            try {
                result = copy2File(in, destFile);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            result = false;
        }

        return result;
    }

    /**
     * Copy data from a source stream to destFile.
     * Return true if succeed, return false if failed.
     */
    private static boolean copy2File(InputStream inputStream, File destFile) {
        try {
            if (destFile.exists()) {
                destFile.delete();
            }

            OutputStream out = new FileOutputStream(destFile);

            try {
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            } finally {
                out.close();
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();

            return false;
        }
    }

    private static boolean copy2File(BufferedReader br, File destFile) {
        try {
            if (destFile.exists()) {
                destFile.delete();
            }

            BufferedWriter bw = new BufferedWriter(new FileWriter(destFile));

            try {
                String line = null;

                while ((line = br.readLine()) != null) {
                    bw.write(line, 0, line.length());
                    bw.newLine();
                    bw.flush();
                }
            } finally {
                bw.close();
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();

            return false;
        }
    }

    private static boolean writeFile(String longMsg, File destFile) {
        boolean result = false;

        if (longMsg != null) {
            try {
                if (destFile.exists()) {
                    destFile.delete();
                }

                BufferedWriter bw = new BufferedWriter(new FileWriter(destFile));

                try {
                    bw.write(longMsg, 0, longMsg.length());
                    bw.flush();
                    result = true;
                } finally {
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                result = false;
            }
        }

        return result;
    }

    private static String getDisplayInfo(String process, String info) {
        String assertPre = "java.lang.ASSERT.";
        StringBuilder sb = new StringBuilder(1024);
        String substr = null;
        char ch;
        int istart = 0;
        String st = info;
        int count = st.length();

        sb.append("JAVA ASSERT ERROR!!!\r\n" + "Process: " + process + "\r\n");

        for (int i = 0; i < count; i++) {
            ch = st.charAt(i);

            if (ch == '\n') {
                substr = st.substring(istart, i);

                if (substr.indexOf(assertPre) == -1) {
                    sb.append(substr);
                    sb.append("\r\n");
                }

                istart = i + 1;
                substr = null;
            } else {
                if (i == (count - 1)) {
                    sb.append(st.substring(istart, i));
                    sb.append("\r\n");
                }
            }
        }

        return sb.toString();
    }

    public static void panicKernel() {
        if (getSystemProperties_native(ASSERT_PANIC_PROP, "true").equals("false")) {
            panic_native();
        }
    }

    private static void displayErrorInfo(String info) {
        if (getSystemProperties_native(ASSERT_PANIC_PROP, "true").equals("false")) {
            if (getSystemProperties_native("ro.runtime.assert", "false")
                    .equals("true")) {
                return;
            } else {
                setSystemProperties_native("ro.runtime.assert", "true");
            }

            displayErrorInfo_native(info);
        }
    }

    /* This is just for dropbox */
    public static boolean epitaph(File temp, String tag, int flags) {
        InputStream is = null;
        BufferedReader br = null;
        BufferedWriter bw = null;
        String process = null;

        if (temp == null) {
            return false;
        }

        try {
            is = new FileInputStream(temp);

            if ((flags & IS_GZIPPED) != 0) {
                is = new GZIPInputStream(is);
            }

            br = new BufferedReader(new InputStreamReader(is));

            String line;
            StringBuilder sb = new StringBuilder(1024);
            int count = 0;

            try {
                while ((count < 1024) && (null != (line = br.readLine()))) {
                    if (line.startsWith("-----", 0)) {
                        break;
                    }

                    if (line.startsWith("Process: ", 0)) {
                        process = line.substring(line.indexOf(":") + 1).trim();
                    }

                    sb.append(line);
                    sb.append("\r\n");
                    count += line.length();
                }
            } finally {
                br.close();
            }

            if (isFiltedProcess(process)) {
                return false;
            }

            String fn = getPath(process) + ".txt";
            File dest = new File(fn);
            is = new FileInputStream(temp);

            if ((flags & IS_GZIPPED) != 0) {
                is = new GZIPInputStream(is);
            }

            copy2File(is, dest);
            is.close();

            sb.append("Please check " + fn + " file for more information ...");
            displayErrorInfo(sb.toString());
        //#ifndef VENDOR_EDIT
        //Fangfang.Hui@Prd.PlatSrv.OTA, 2014/03/13, Remove for lost some log when android reboot
        /*
            panicKernel();
        */
        //#endif /* VENDOR_EDIT */            
        } catch (IOException e) {
            e.printStackTrace();

            return false;
        }

        return true;
    }

    private static native String getSystemProperties_native(String key,
            String def);

    private static native String getProcessName_native();

    private static native void panic_native();

    private static native void displayErrorInfo_native(String info);

    private static native void setSystemProperties_native(String keyJ,
            String valueJ);
}
