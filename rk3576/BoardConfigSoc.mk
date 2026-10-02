#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

RK3576_PATH := device/rockchip/rk35xx-common/rk3576

## Platform
TARGET_BOARD_PLATFORM := rk3576
TARGET_BOARD_PLATFORM_GPU := mali-g52

## Architecture
# 4x Cortex-A72 + 4x Cortex-A53
TARGET_ARCH_VARIANT := armv8-a
TARGET_2ND_ARCH_VARIANT := armv8-a
TARGET_CPU_VARIANT := cortex-a53
TARGET_CPU_VARIANT_RUNTIME := cortex-a72
TARGET_2ND_CPU_VARIANT := cortex-a53
TARGET_2ND_CPU_VARIANT_RUNTIME := cortex-a53

## Boot devices (UFS, eMMC)
RK_BOOT_DEVICES := 2a2d0000.ufs,2a330000.mmc

include device/rockchip/rk35xx-common/BoardConfigCommon.mk

## Kernel
TARGET_KERNEL_CONFIG_EXT += \
    $(TARGET_KERNEL_SOURCE)/arch/arm64/configs/rk3576.config

## AI-PQ model partition
BOARD_ROOT_EXTRA_FOLDERS += rkalgo

## SELinux
BOARD_SEPOLICY_DIRS += $(RK3576_PATH)/sepolicy/vendor

## Properties
TARGET_VENDOR_PROP += $(RK3576_PATH)/vendor.prop
