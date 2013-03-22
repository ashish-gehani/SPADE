LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := spadeAndroidAudit
LOCAL_SRC_FILES := spadeAndroidAudit.c

#LOCAL_CFLAGS    := -DDEBUG
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := dumpstream
LOCAL_SRC_FILES := dumpstream.c

#LOCAL_CFLAGS    := -DDEBUG

include $(BUILD_EXECUTABLE)

