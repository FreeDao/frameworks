/*
 * Copyright (C) 2010 The OPPO
 *
 * author : Lance Yao
 * oppo_debug
 */

#include "jni.h"
#include "JNIHelp.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <limits.h>
#include <utils/Log.h>
#include "cutils/properties.h"

#define IOCTL_OTRACER_PANIC          (1<<12)

static jstring getSystemProperties_native(JNIEnv *env, jobject,
                                      jstring keyJ, jstring defJ)
{
    int len;
    const char* key;
    char buf[PROPERTY_VALUE_MAX];
    jstring rvJ = NULL;

    if (keyJ == NULL) {
        jniThrowException(env, "java/lang/NullPointerException",
                                "key must not be null.");
        goto error;
    }

    key = env->GetStringUTFChars(keyJ, NULL);

    len = property_get(key, buf, "");
    if ((len <= 0) && (defJ != NULL)) {
        rvJ = defJ;
    } else if (len >= 0) {
        rvJ = env->NewStringUTF(buf);
    } else {
        rvJ = env->NewStringUTF("");
    }

    env->ReleaseStringUTFChars(keyJ, key);

error:
    return rvJ;
}

static void displayErrorInfo_native(JNIEnv* env, jobject, jstring info)
{
    const char * pinfo = env->GetStringUTFChars(info, NULL);

    int fd = -1;
    
    fd = open((const char*) "/dev/otracer", O_RDWR | O_SYNC);
    if (fd > 0) {
        write(fd, pinfo, strlen(pinfo));
        close(fd);
        sync();
    } else {
        ALOGE("Failed to open /dev/otracer");
    }
    env->ReleaseStringUTFChars(info, pinfo);
}

static jstring getProcessName_native(JNIEnv *env, jobject)
{
    int fd = -1;
    int pid = getpid();
    char * pbuffer = (char *)malloc(PATH_MAX * sizeof(char));

    sprintf((char *)pbuffer, "/proc/%d/cmdline", pid);
    fd = open((char *)pbuffer, O_RDONLY);
    if (fd < 0) {
        ALOGE("Failed to open /proc/ file");
        strcpy((char *)pbuffer, "???");
    } else {	
        int length = read(fd, (char *)pbuffer, PATH_MAX - 1);
        ((char *)pbuffer)[length] = '\0';
        close(fd);
    }
	
    return env->NewStringUTF(pbuffer);
}

static void panic_native(JNIEnv *, jobject)
{
    int fd = -1;
    
    fd = open((const char*) "/dev/otracer", O_RDWR);
    if (fd > 0) {
        sync();
        usleep(3000000);
        ioctl(fd, IOCTL_OTRACER_PANIC, 0);
        close(fd);
    } else {
        ALOGE("Failed to open /dev/otracer");
    }
}

static void setSystemProperties_native(JNIEnv *env, jobject,
                                      jstring keyJ, jstring valueJ)
{
	const char* key;
	const char* value;

	if (keyJ == NULL || valueJ == NULL) {
        jniThrowException(env, "java/lang/NullPointerException",
                                "key or value must not be null.");
		return;
	}
	key = env->GetStringUTFChars(keyJ, NULL);
	value = env->GetStringUTFChars(valueJ, NULL);
	property_set(key, value);
	env->ReleaseStringUTFChars(keyJ, key);
	env->ReleaseStringUTFChars(valueJ, value);	
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] =
{
    { "getSystemProperties_native", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
      (void*) getSystemProperties_native },
    { "getProcessName_native",     "()Ljava/lang/String;",      (void*)getProcessName_native},
    { "panic_native",              "()V",                       (void*)panic_native},
    { "displayErrorInfo_native",    "(Ljava/lang/String;)V",    (void *)displayErrorInfo_native},
    { "setSystemProperties_native", "(Ljava/lang/String;Ljava/lang/String;)V",
	     														(void *)setSystemProperties_native},
};
   
int register_oppo_debug_ASSERT(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/oppo/debug/ASSERT", gMethods, NELEM(gMethods));
}

