#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
# Rockchip's resource.img, which rides in the boot image "second" area --
# mkbootimg --second, which the build system feeds from
# $(PRODUCT_OUT)/2ndbootloader (TARGET_BOOTLOADER_IS_2ND).
#
# It is an "RSCE" container holding the boot logo, the battery bitmaps and a
# copy of the device tree named rk-kernel.dtb. That copy is the one U-Boot
# actually reads, so it is repacked around the dtb this build produced. Leaving
# a stale one there boots the new kernel against the old device tree, silently.
#
# The bitmaps are not built from source, so the stock image is still needed as a
# template -- that is all TARGET_STOCK_RESOURCE_IMAGE is for.
#
# resource_tool is Rockchip's own packer, built as a host tool by any kernel
# build (hostprogs-always-$(CONFIG_ARCH_ROCKCHIP)). Depending on
# TARGET_PREBUILT_INT_KERNEL is what guarantees both it and the dtb exist.
#
# In build/tasks for the same reason as dtbimage.mk: INSTALLED_2NDBOOTLOADER_TARGET
# is not defined yet while Android.mk files are read.

ifneq ($(filter rk3576 rk3588,$(TARGET_BOARD_PLATFORM)),)
ifeq ($(TARGET_BOOTLOADER_IS_2ND),true)

RK_PACK_RESOURCE_IMG := $(COMMON_PATH)/tools/pack-resource-img.sh
RK_RESOURCE_TOOL := $(KERNEL_OUT)/scripts/resource_tool
RK_RESOURCE_DTB := $(KERNEL_OUT)/arch/$(KERNEL_ARCH)/boot/dts/rockchip/$(TARGET_DTB_NAME).dtb

$(INSTALLED_2NDBOOTLOADER_TARGET): $(RK_PACK_RESOURCE_IMG) $(TARGET_STOCK_RESOURCE_IMAGE) $(TARGET_PREBUILT_INT_KERNEL)
	@echo "Packing resource.img around $(TARGET_DTB_NAME).dtb"
	$(hide) mkdir -p $(dir $@)
	$(hide) $(RK_PACK_RESOURCE_IMG) $(TARGET_STOCK_RESOURCE_IMAGE) $(RK_RESOURCE_DTB) $(RK_RESOURCE_TOOL) $@

endif
endif
