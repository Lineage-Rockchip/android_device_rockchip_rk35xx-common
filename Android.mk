#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

ifneq ($(filter rk3576 rk3588,$(TARGET_BOARD_PLATFORM)),)

LOCAL_PATH := $(call my-dir)

# dtb.img and resource.img (the boot image "second" area) are not built here.
# They need INSTALLED_DTBIMAGE_TARGET / INSTALLED_2NDBOOTLOADER_TARGET and the
# kernel build's KERNEL_OUT, none of which exist while Android.mk files are
# being read -- a rule written against an empty target name silently does
# nothing. They live in build/tasks/ instead, which build/make/core/Makefile
# includes at its very end, the way device/amlogic/common does it.

include $(call all-makefiles-under,$(LOCAL_PATH))

endif
