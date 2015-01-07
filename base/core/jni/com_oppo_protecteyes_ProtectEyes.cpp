/**
* Copyright 2014-2020 OPPO Mobile Comm Corp., Ltd, All rights reserved.
* FileName: com_oppo_protecteyes_ProtectEyes.cpp
* ModuleName: ProtectEyes
* Author : wenhua.Leng@MultiMedia.Graphics.oppo_protecteyes.
* VENDOR_EDIT
* Create Date:
* Description:

* History:
<version >  <time>  <author>  <desc>
*/
#include "jni.h"
#include "JNIHelp.h"
#include <android_runtime/AndroidRuntime.h>

#include <dlfcn.h>
#include <cutils/properties.h>
#include <utils/Log.h>

#define LOG_TAG "jniPro"

#define LIBRARY_PATH_NAME	"/system/lib/libprotecteyes.so"

#define PROEYES_NOT_INIT -2
#define PROEYES_FAILED -1
#define PROEYES_SUCESS 0

namespace android
{

// ----------------------------------------------------------------------------
static int  (*protecteyes_init)()                   		= NULL;
static int  (*protecteyes_enable)(double, double, double)	= NULL;
static int  (*protecteyes_disenable)()						= NULL;
static int  (*protecteyes_deinit)()                   		= NULL;
static void *dlhandle                               		= NULL;
// ----------------------------------------------------------------------------


static int protecteyesinit()
{
    const char *rc;

    dlhandle = dlopen(LIBRARY_PATH_NAME, RTLD_NOW | RTLD_LOCAL);
    if (dlhandle == NULL) {
		ALOGE("%s, dlopen dlhandle is null", __FUNCTION__);
        return PROEYES_FAILED;
    }

    //ALOGE("%s, %s ", __FUNCTION__, dlerror());

    protecteyes_enable = (int (*) (double, double, double))dlsym(dlhandle, "protectEyesEnable");
    if ((rc = dlerror()) != NULL) {
		ALOGE("%s, dlsym protectEyesEnable is null, rc=%s ", __FUNCTION__, rc);
        goto cleanup;
    }

    protecteyes_disenable = (int (*) ())dlsym(dlhandle, "protectEyesDisenable");
    if ((rc = dlerror()) != NULL) {
		ALOGE("%s, dlsym protectEyesDisenable is null, rc=%s ", __FUNCTION__, rc);
        goto cleanup;
    }

    protecteyes_deinit = (int (*) ())dlsym(dlhandle, "protectEyesDeinit");
    if ((rc = dlerror()) != NULL) {
		ALOGE("%s, dlsym protectEyesDeinit is null, rc=%s ", __FUNCTION__, rc);
        goto cleanup;
    }

    protecteyes_init = (int (*) ())dlsym(dlhandle, "protectEyesInit");
    if ((rc = dlerror()) != NULL) {
		ALOGE("%s, dlsym protectEyesInit is null, rc=%s ", __FUNCTION__, rc);
        goto cleanup;
    }

    return PROEYES_SUCESS;

cleanup:
    protecteyes_enable 		= NULL;
    protecteyes_disenable  	= NULL;
    protecteyes_deinit  	= NULL;
	protecteyes_init 		= NULL;
    if (dlhandle) {
        dlclose(dlhandle);
        dlhandle = NULL;
    }
	return PROEYES_FAILED;
}

jint com_oppo_protecteyes_initProtectEyes
	(JNIEnv *env, jobject obj){
  	if (dlhandle == NULL) {
		ALOGE("%s, dlhandle is null", __FUNCTION__);
		return PROEYES_NOT_INIT;
	}

	if(protecteyes_init){
		protecteyes_init();
		return PROEYES_SUCESS;
	}

	
	ALOGE("%s, protecteyes_init is null", __FUNCTION__);

	return PROEYES_FAILED;
}


jint com_oppo_protecteyes_EnableProtectEyes
	(JNIEnv *env, jobject obj, jdouble r, jdouble g, jdouble b){
  	if (dlhandle == NULL) {
		ALOGE("%s, dlhandle is null", __FUNCTION__);
		return PROEYES_NOT_INIT;
	}

	if(protecteyes_enable){
		protecteyes_enable(r, g, b);
		return PROEYES_SUCESS;
	}
	
	ALOGE("%s, protecteyes_enable is null", __FUNCTION__);

	return PROEYES_FAILED;
}

jint com_oppo_protecteyes_DisenableProtectEyes
  (JNIEnv *env, jobject obj){	
	if (dlhandle == NULL) {
		return PROEYES_NOT_INIT;
	}

	if(protecteyes_disenable){
		protecteyes_disenable();
		return PROEYES_SUCESS;
	}

	return PROEYES_FAILED;
}

jint com_oppo_protecteyes_Deinit
  (JNIEnv *env, jobject obj){	
	if (dlhandle == NULL) {
		return PROEYES_NOT_INIT;
	}

	if(protecteyes_deinit){
		protecteyes_deinit();
		return PROEYES_SUCESS;
	}

	return PROEYES_FAILED;
}


// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"initProtectEyes_native", "()I",	(void *)com_oppo_protecteyes_initProtectEyes},
    {"enableProtectEyes_native", "(DDD)I",
    										(void *)com_oppo_protecteyes_EnableProtectEyes},
    {"disableProtectEyes_native", "()I",	(void *)com_oppo_protecteyes_DisenableProtectEyes},
    {"exitProtectEyes_native",    "()I", (void *)com_oppo_protecteyes_Deinit},
};


int register_com_oppo_ProtectEyes(JNIEnv *env)
{
    protecteyesinit();

    return AndroidRuntime::registerNativeMethods(env,
            "com/oppo/protecteyes/ProtectEyes", gMethods, NELEM(gMethods));
}

}   // namespace android




