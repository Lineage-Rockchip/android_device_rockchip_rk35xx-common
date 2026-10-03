#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

COMMON_PATH := device/rockchip/rk35xx-common

# Included from <soc>/BoardConfigSoc.mk, never directly.
ifeq ($(filter rk3576 rk3588,$(TARGET_BOARD_PLATFORM)),)
$(error include rk35xx-common through <soc>/BoardConfigSoc.mk)
endif

## Architecture
# Arch and CPU variants are per SoC.
TARGET_ARCH := arm64
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_ABI2 :=

TARGET_2ND_ARCH := arm
TARGET_2ND_CPU_ABI := armeabi-v7a
TARGET_2ND_CPU_ABI2 := armeabi

TARGET_SUPPORTS_64_BIT_APPS := true

## Bootloader
# MiniLoaderAll.bin + uboot.img are never rebuilt here.
TARGET_NO_BOOTLOADER := false
TARGET_BOOTLOADER_BOARD_NAME := rk30board

## Kernel
# Built from source out of kernel/rockchip/kernel-6.1, which is Khadas' RK3576
# BSP tree (linux 6.1.141, non-GKI) -- the same source the Edge-2L RKR8 blobs
# were built against, so the in-kernel Mali matches libGLES_mali.
TARGET_NO_KERNEL := false
TARGET_KERNEL_SOURCE := kernel/rockchip/kernel-6.1

# rockchip_defconfig is the base; the rest are fragments merged in this order.
# The SoC, then the board, append their own fragments after this file.
# The paths are spelled out rather than using TARGET_KERNEL_CONFIG for the
# fragments because android-14.config does not live in arch/arm64/configs, and
# because ALL_KERNEL_DEFCONFIG_SRCS puts TARGET_KERNEL_CONFIG_EXT last.
TARGET_KERNEL_CONFIG := rockchip_defconfig
TARGET_KERNEL_CONFIG_EXT := \
    $(TARGET_KERNEL_SOURCE)/kernel/configs/android-14.config

BOARD_KERNEL_IMAGE_NAME := Image

# Matches the stock boot.img header exactly (header v2, 2048 byte pages).
BOARD_BOOT_HEADER_VERSION := 2
BOARD_KERNEL_BASE := 0x10000000
BOARD_KERNEL_PAGESIZE := 2048
BOARD_KERNEL_OFFSET := 0x00008000
BOARD_RAMDISK_OFFSET := 0x01000000
BOARD_KERNEL_SECOND_OFFSET := 0x00f00000
BOARD_KERNEL_TAGS_OFFSET := 0x00000100
BOARD_DTB_OFFSET := 0x01f00000

BOARD_MKBOOTIMG_ARGS += --header_version $(BOARD_BOOT_HEADER_VERSION)
BOARD_MKBOOTIMG_ARGS += --kernel_offset $(BOARD_KERNEL_OFFSET)
BOARD_MKBOOTIMG_ARGS += --ramdisk_offset $(BOARD_RAMDISK_OFFSET)
BOARD_MKBOOTIMG_ARGS += --second_offset $(BOARD_KERNEL_SECOND_OFFSET)
BOARD_MKBOOTIMG_ARGS += --tags_offset $(BOARD_KERNEL_TAGS_OFFSET)
BOARD_MKBOOTIMG_ARGS += --dtb_offset $(BOARD_DTB_OFFSET)
BOARD_MKBOOTIMG_ARGS += --pagesize $(BOARD_KERNEL_PAGESIZE)

# Rewrites the boot image header SHA1 with the upstream digest, which the
# Rockchip bootloader requires. See BRINGUP-NOTES.md section 2.
BOARD_CUSTOM_MKBOOTIMG := $(COMMON_PATH)/tools/mkbootimg-rk

# U-Boot appends androidboot.storagemedia/mode/serialno and earlycon at runtime.
BOARD_KERNEL_CMDLINE := console=ttyFIQ0
BOARD_KERNEL_CMDLINE += firmware_class.path=/vendor/etc/firmware
BOARD_KERNEL_CMDLINE += init=/init
BOARD_KERNEL_CMDLINE += rootwait ro
BOARD_KERNEL_CMDLINE += loop.max_part=7
BOARD_KERNEL_CMDLINE += printk.devkmsg=on
BOARD_KERNEL_CMDLINE += kvm-arm.mode=none
BOARD_KERNEL_CMDLINE += androidboot.console=ttyFIQ0
BOARD_KERNEL_CMDLINE += androidboot.wificountrycode=CN
BOARD_KERNEL_CMDLINE += androidboot.hardware=rk30board
BOARD_KERNEL_CMDLINE += androidboot.boot_devices=$(RK_BOOT_DEVICES)
# BRING-UP ONLY
BOARD_KERNEL_CMDLINE += androidboot.selinux=permissive

## DTB / DTBO
# The device tree is appended to boot.img rather than shipped in a vendor_boot.
# BOARD_PREBUILT_DTBIMAGE_DIR is deliberately unset: the dtb is built with the
# kernel now. dtb.img is exactly one dtb, named per board as TARGET_DTB_NAME --
# which is how the M9 and the M9S pick their own IR key tables off a shared
# board dtsi -- and build/tasks/dtbimage.mk copies it into place.
BOARD_INCLUDE_DTB_IN_BOOTIMG := true

# Switches off kernel.mk's own, competing dtb.img rule. See the file.
BOARD_CUSTOM_DTBIMG_MK := $(COMMON_PATH)/build/no-dtbimage.mk

# BOARD_PREBUILT_DTBOIMAGE is per board.

## Rockchip resource.img
# RSCE (logo/battery bitmaps + rk-kernel.dtb), passed to mkbootimg as --second.
#
# rk-kernel.dtb inside it is the copy U-Boot actually reads, so it has to be
# repacked around the freshly built dtb on every build or the board runs a new
# kernel against an old device tree. Android.mk does that; the stock image is
# only the source of the bitmaps, which are not built from source.
TARGET_BOOTLOADER_IS_2ND := true

## Kernel modules
# Built with the kernel and installed by kernel.mk; all of them live in
# vendor_dlkm, and odm_dlkm and system_dlkm ship empty. BOARD_VENDOR_KERNEL_MODULES
# is for prebuilt .ko files and stays unset.
#
# Boards add their WiFi driver as TARGET_KERNEL_EXT_MODULES += wifi/<driver>:kbuild.
TARGET_KERNEL_EXT_MODULE_ROOT := kernel/rockchip/kernel-modules

## Verified Boot
# Stock ships a completely unsigned vbmeta. Keep it that way.
BOARD_AVB_ENABLE := false

## Partitions
# Non-A/B device with a dedicated recovery partition.
AB_OTA_UPDATER := false

BOARD_USES_METADATA_PARTITION := true

TARGET_USERIMAGES_USE_EXT4 := true
TARGET_USERIMAGES_USE_F2FS := true
TARGET_USES_MKE2FS := true

BOARD_FLASH_BLOCK_SIZE := 131072

# Per board; must be set before this file is included.
ifeq ($(BOARD_SUPER_PARTITION_SIZE),)
$(error BOARD_SUPER_PARTITION_SIZE must be set by the board BoardConfig.mk)
endif

SSI_PARTITIONS := product system system_dlkm system_ext
TREBLE_PARTITIONS := odm odm_dlkm vendor vendor_dlkm
ALL_PARTITIONS := $(SSI_PARTITIONS) $(TREBLE_PARTITIONS)

BOARD_SUPER_PARTITION_GROUPS := rockchip_dynamic_partitions
BOARD_ROCKCHIP_DYNAMIC_PARTITIONS_PARTITION_LIST := $(ALL_PARTITIONS)
# BOARD_SUPER_PARTITION_SIZE - "reasonable overhead of 4 MiB" 4194304
include vendor/lineage/config/BoardConfigReservedSize.mk
BOARD_ROCKCHIP_DYNAMIC_PARTITIONS_SIZE := $(shell echo $$(($(BOARD_SUPER_PARTITION_SIZE) - 4194304)))

BOARD_BUILD_SUPER_IMAGE_BY_DEFAULT := true

BOARD_BOOTIMAGE_PARTITION_SIZE := 67108864
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 100663296
BOARD_DTBOIMG_PARTITION_SIZE := 4194304

# Real 384 MiB cache partition (mmcblk2p10). Both variables are required, or the
# OTA generator dies on "assert cache_size is not None".
BOARD_CACHEIMAGE_PARTITION_SIZE := 402653184
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4

BOARD_SYSTEMIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_SYSTEM_EXTIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_PRODUCTIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_VENDORIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_ODMIMAGE_FILE_SYSTEM_TYPE := ext4

BOARD_USES_VENDORIMAGE := true
TARGET_COPY_OUT_VENDOR := vendor

BOARD_USES_ODMIMAGE := true
TARGET_COPY_OUT_ODM := odm

BOARD_USES_PRODUCTIMAGE := true
TARGET_COPY_OUT_PRODUCT := product

BOARD_USES_SYSTEM_EXTIMAGE := true
TARGET_COPY_OUT_SYSTEM_EXT := system_ext

BOARD_USES_VENDOR_DLKMIMAGE := true
BOARD_VENDOR_DLKMIMAGE_FILE_SYSTEM_TYPE := ext4
TARGET_COPY_OUT_VENDOR_DLKM := vendor_dlkm

BOARD_USES_ODM_DLKMIMAGE := true
BOARD_ODM_DLKMIMAGE_FILE_SYSTEM_TYPE := ext4
TARGET_COPY_OUT_ODM_DLKM := odm_dlkm

# Stock ships an (empty) system_dlkm and the fstab mounts it.
BOARD_USES_SYSTEM_DLKMIMAGE := true
BOARD_SYSTEM_DLKMIMAGE_FILE_SYSTEM_TYPE := ext4
TARGET_COPY_OUT_SYSTEM_DLKM := system_dlkm

TARGET_USERIMAGES_SPARSE_EXT_DISABLED := false

## Recovery
TARGET_RECOVERY_PIXEL_FORMAT := RGBX_8888
# TARGET_RECOVERY_FSTAB is per board.
BOARD_USES_RECOVERY_AS_BOOT := false
TARGET_NO_RECOVERY := false
BOARD_INCLUDE_RECOVERY_DTBO := true

## Treble
# Stock vendor is already an Android 14 / FCM target-level 8 image.
# BOARD_VNDK_VERSION is deliberately unset.
PRODUCT_FULL_TREBLE_OVERRIDE := true
DEVICE_MANIFEST_FILE += $(COMMON_PATH)/manifest.xml
DEVICE_MATRIX_FILE += $(COMMON_PATH)/compatibility_matrix.xml
# Rockchip-only HIDL HALs (outputmanager, rockit)
DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE += \
    $(COMMON_PATH)/vendor_framework_compatibility_matrix.xml

## SELinux
# BRING-UP ONLY -- stock boots permissive too.
BOARD_SEPOLICY_DIRS += $(COMMON_PATH)/sepolicy/vendor
include hardware/rockchip/fwkservices/BoardConfig.mk
SELINUX_IGNORE_NEVERALLOWS := true

## Properties
TARGET_SYSTEM_PROP += $(COMMON_PATH)/system.prop
TARGET_VENDOR_PROP += $(COMMON_PATH)/vendor.prop
TARGET_PRODUCT_PROP += $(COMMON_PATH)/product.prop

## Graphics
TARGET_USES_HWC2 := true
BOARD_USES_DRM_HWCOMPOSER := true
TARGET_USES_GRALLOC4 := true
# Emits ro.hwui.use_vulkan; belongs here rather than in vendor.prop.
TARGET_USES_VULKAN := true

## Media
TARGET_USES_C2_COMPONENT := true

## SoC, for select() in blueprints
$(call soong_config_set,rockchip,soc,$(TARGET_BOARD_PLATFORM))

## Audio HAL service bitness
# Must match the extracted binary, or the two collide on the install path.
# The dump's is 32-bit, which is AOSP's prefer32 default. BRINGUP-NOTES.md 7.25.
SOONG_CONFIG_NAMESPACES += android_hardware_audio
SOONG_CONFIG_android_hardware_audio += run_64bit
SOONG_CONFIG_android_hardware_audio_run_64bit := false

## Wi-Fi
# The whole stack is extracted from stock. BOARD_WLAN_DEVICE is deliberately
# unset: Soong accepts no value matching aic8800, and any of the ones it does
# accept would build AOSP's libwifi-hal over the extracted one.
WIFI_DRIVER_SOCKET_IFACE := wlan0

## Bluetooth
BOARD_HAVE_BLUETOOTH := true
BOARD_HAVE_BLUETOOTH_ROCKCHIP := true

## BUILD_BROKEN_*
BUILD_BROKEN_DUP_RULES := false

## Include the common proprietary BoardConfig makefile
-include vendor/rockchip/rk35xx-common/BoardConfigVendor.mk
