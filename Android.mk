#
# Copyright (C) 2019 The OmniROM Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/app/src/main/res
LOCAL_MANIFEST_FILE := app/src/main/AndroidManifest.xml
LOCAL_SRC_FILES := $(call all-java-files-under, app/src/main/java)

LOCAL_STATIC_ANDROID_LIBRARIES := \
    androidx.appcompat_appcompat \
    com.google.android.material_material
LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_USE_AAPT2 := true

LOCAL_PACKAGE_NAME := OmniRemote
LOCAL_CERTIFICATE := platform
LOCAL_MODULE_TAGS := optional
LOCAL_DEX_PREOPT := false

include $(BUILD_PACKAGE)
