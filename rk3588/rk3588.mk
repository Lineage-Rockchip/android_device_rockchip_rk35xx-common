#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

RK3588_PATH := device/rockchip/rk35xx-common/rk3588

## Mali CSF firmware, matched to the in-tree valhall kbase; loaded on first open of /dev/mali0
PRODUCT_COPY_FILES += \
    kernel/rockchip/kernel-6.1/drivers/gpu/arm/valhall/mali_csffw.bin:$(TARGET_COPY_OUT_VENDOR)/etc/firmware/g29p0-00eac0.mali_csffw.bin

## Mali userspace (MaliG610)
PRODUCT_SOONG_NAMESPACES += vendor/rockchip/gpu/MaliG610
PRODUCT_PACKAGES += \
    libGLES_mali \
    libgpudataproducer \
    vulkan.mali

## Composer
PRODUCT_PACKAGES += \
    hwcomposer.rk3588

## Codec 2 component lists (Rockchip's media_codecs_*_rk3588.xml)
PRODUCT_COPY_FILES += \
    $(RK3588_PATH)/configs/media/media_codecs_c2_base.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_c2_base.xml \
    $(RK3588_PATH)/configs/media/media_codecs_google_c2.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_c2.xml \
    $(RK3588_PATH)/configs/media/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml

## Init
PRODUCT_COPY_FILES += \
    $(RK3588_PATH)/init.rk3588.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.rk3588.rc

$(call inherit-product, device/rockchip/rk35xx-common/common.mk)
