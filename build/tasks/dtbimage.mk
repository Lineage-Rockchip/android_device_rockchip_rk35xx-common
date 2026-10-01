#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
# dtb.img, which mkbootimg puts in the boot image's dtb area.
#
# This board needs exactly one device tree, named by the board tree as
# TARGET_DTB_NAME (e.g. rk3576-m9s), so the image is that one dtb and
# nothing else -- no concatenation, no dtbo overlays.
#
# Lives in build/tasks rather than in Android.mk because build/make/core/Makefile
# includes device/*/*/build/tasks/*.mk at its very end: INSTALLED_DTBIMAGE_TARGET
# does not exist yet while Android.mk files are being read, and a rule written
# against an empty target name silently does nothing. vendor/*/build/tasks comes
# first in that same glob, so kernel.mk has already run and KERNEL_OUT and
# TARGET_PREBUILT_INT_KERNEL are set.
#
# kernel.mk's own dtb.img rule is switched off through BOARD_CUSTOM_DTBIMG_MK;
# see build/no-dtbimage.mk for why.

ifneq ($(filter rk3576 rk3588,$(TARGET_BOARD_PLATFORM)),)
ifeq ($(BOARD_INCLUDE_DTB_IN_BOOTIMG),true)

RK_BUILT_DTB := $(KERNEL_OUT)/arch/$(KERNEL_ARCH)/boot/dts/rockchip/$(TARGET_DTB_NAME).dtb

$(INSTALLED_DTBIMAGE_TARGET): $(TARGET_PREBUILT_INT_KERNEL)
	@echo "Building dtb.img from $(TARGET_DTB_NAME).dtb"
	$(hide) mkdir -p $(dir $@)
	$(hide) cp $(RK_BUILT_DTB) $@

endif
endif
