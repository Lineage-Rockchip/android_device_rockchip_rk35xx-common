#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

RK3576_PATH := device/rockchip/rk35xx-common/rk3576
ROCKCHIP_SOC := rk3576

## Composer
PRODUCT_PACKAGES += \
    hwcomposer.rk3576

## Picture quality: VOP HWPQ and AI-PQ
PRODUCT_PACKAGES += \
    aipq_config.json.rk3576 \
    librkhwpq \
    rkaipq_models.rk3576 \
    vop_base_config.json.rk3576

## Mali userspace (MaliG52)
PRODUCT_SOONG_NAMESPACES += vendor/rockchip/gpu/MaliG52
PRODUCT_PACKAGES += \
    libGLES_mali \
    libgpudataproducer \
    vulkan.mali

## Codec 2 component lists (Rockchip's media_codecs_*_rk3576.xml)
PRODUCT_COPY_FILES += \
    $(RK3576_PATH)/configs/media/media_codecs_c2_base.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_c2_base.xml \
    $(RK3576_PATH)/configs/media/media_codecs_google_c2.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_c2.xml \
    $(RK3576_PATH)/configs/media/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml

## Init
PRODUCT_COPY_FILES += \
    $(RK3576_PATH)/init.rk3576.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.rk3576.rc

$(call inherit-product, device/rockchip/rk35xx-common/common.mk)
