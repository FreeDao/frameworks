/**
* Copyright 2014-2020 OPPO Mobile Comm Corp., Ltd, All rights reserved.
* FileName: ProtectEyes.java
* ModuleName: ProtectEyes
* Author : wenhua.Leng@MultiMedia.Graphics.oppo_protecteyes.
* VENDOR_EDIT
* Create Date:
* Description:

* History:
<version >  <time>  <author>  <desc>
*/
package com.oppo.protecteyes;

import android.util.Log;

/*
    protect eyes, filter the blue light, make the LCD display soft
    how to use this class
    you can use it in anywhere but at first you should call initProtectEyes, the call enableProtectEyes function.
    for example:
    resume(){
        initProtectEyes();
        enableProtectEyes(1.0, 0.90, 0.85);
    }

    stop(){
        disableProtectEyes();
        exitProtectEyes();
    }
*/
public class ProtectEyes {
	private String TAG="protecteyes";
    	
	public static final int PROEYES_FAILED = -1;
	public static final int PROEYES_SUCCESS = 0;
	public static final int PROEYES_NO_INIT = 1;
	public static final int PROEYES_ALREADY_INIT = 2;
	
	private static int proEyesInit = PROEYES_FAILED;
	
	public ProtectEyes(){
	}
	
	public int initProtectEyes(){
        int ret = PROEYES_FAILED;
        
		if(proEyesInit == PROEYES_ALREADY_INIT){
			Log.d(TAG, "protectEyes already init");
            return PROEYES_SUCCESS;
		}

    	ret = initProtectEyes_native();
        if(ret != 0){
            ret = PROEYES_FAILED;
			Log.d(TAG, "protectEyes init failed");
        }else{
            ret = PROEYES_SUCCESS;
            proEyesInit=PROEYES_ALREADY_INIT;
        }
        
		return ret;
	}

    /*
        *enable protect eyes, the parameters are double, if you use 0-255, it should division 255;
        *r: red weight,        range 0-1.0
        *g:green weight,     range 0-1.0
        *b:blue weight,       range 0-1.0
     */
	public int enableProtectEyes(double r, double g, double b){
		int ret = PROEYES_FAILED;
		if(proEyesInit != PROEYES_ALREADY_INIT){
			Log.d(TAG, "protectEyes does not init");
			return ret;
        }

		ret = enableProtectEyes_native(r, g, b);
        if(ret < 0){
            Log.d(TAG, "enableProtectEyes failed");
            ret = PROEYES_FAILED;
        }else{
            ret = PROEYES_SUCCESS;
        }
        
		return ret;
	}

	public int disableProtectEyes(){
		int ret = PROEYES_FAILED;
		if(proEyesInit != PROEYES_ALREADY_INIT){
			Log.d(TAG, "protectEyes does not init");
            return ret;
		}
        
		ret = disableProtectEyes_native();
        if(ret < 0){
            Log.d(TAG, "enableProtectEyes failed");
            ret = PROEYES_FAILED;
        }else{
            ret = PROEYES_SUCCESS;
        }
        
		return ret;
	}
	
	public int exitProtectEyes(){
		int ret = PROEYES_FAILED;
		ret = exitProtectEyes_native();
        if(ret == 0){
		    proEyesInit=PROEYES_NO_INIT;
            ret = PROEYES_SUCCESS;
        }else{
            Log.d(TAG, "exitProtectEyes failed");
            ret = PROEYES_FAILED;
        }
        
		return ret;
	}	

    private native int initProtectEyes_native();

    private native int enableProtectEyes_native(double r, double g, double b);

    private native int disableProtectEyes_native();

    private native int exitProtectEyes_native();
}

