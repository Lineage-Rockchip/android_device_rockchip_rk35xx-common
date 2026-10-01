#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

RK3576_PATH := device/rockchip/rk35xx-common/rk3576

## AI-PQ settings
PRODUCT_PACKAGES += \
    RkAiPqSettings \
    TvSettingsAipqOverlay

## Init
PRODUCT_COPY_FILES += \
    $(RK3576_PATH)/init.rk3576.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.rk3576.rc

$(call inherit-product, device/rockchip/rk35xx-common/common.mk)
