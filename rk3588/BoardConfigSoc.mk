#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

RK3588_PATH := device/rockchip/rk35xx-common/rk3588

## Platform
TARGET_BOARD_PLATFORM := rk3588
TARGET_BOARD_PLATFORM_GPU := mali-g610

## Architecture
# 4x Cortex-A76 + 4x Cortex-A55
TARGET_ARCH_VARIANT := armv8-2a
TARGET_2ND_ARCH_VARIANT := armv8-2a
TARGET_CPU_VARIANT := cortex-a55
TARGET_CPU_VARIANT_RUNTIME := cortex-a76
TARGET_2ND_CPU_VARIANT := cortex-a55
TARGET_2ND_CPU_VARIANT_RUNTIME := cortex-a55

## Boot devices (eMMC, SD, PCIe NVMe, SATA)
RK_BOOT_DEVICES ?= fe2e0000.mmc,fe2c0000.mmc,fe190000.pcie,fe150000.pcie,fe180000.pcie,fe210000.sata,fe230000.sata

include device/rockchip/rk35xx-common/BoardConfigCommon.mk

## Kernel
TARGET_KERNEL_CONFIG_EXT += \
    $(TARGET_KERNEL_SOURCE)/arch/arm64/configs/rk3588.config \
    $(TARGET_KERNEL_SOURCE)/arch/arm64/configs/rk3588_android.config

## Properties
TARGET_VENDOR_PROP += $(RK3588_PATH)/vendor.prop
