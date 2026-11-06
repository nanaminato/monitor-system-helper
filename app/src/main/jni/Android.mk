LOCAL_PATH := $(call my-dir)
PROJECT_ROOT := ../../../..

include $(CLEAR_VARS)
LOCAL_MODULE := zdtun
LOCAL_SRC_FILES := $(PROJECT_ROOT)/third_party/zdtun/zdtun.c \
                   $(PROJECT_ROOT)/third_party/zdtun/utils.c
LOCAL_CFLAGS := -DNO_DEBUG
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := capture
LOCAL_SRC_FILES := $(PROJECT_ROOT)/src/capture.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(PROJECT_ROOT)/third_party/zdtun
LOCAL_CFLAGS := -DNO_DEBUG
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
LOCAL_SHARED_LIBRARIES := zdtun
include $(BUILD_SHARED_LIBRARY)
