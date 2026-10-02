#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

COMMON_PATH := device/rockchip/rk35xx-common

# libshims/ sits under this directory's soong_namespace.
PRODUCT_SOONG_NAMESPACES += $(COMMON_PATH)

## Shipping level
# Stock is Android 14 / SDK 34 with an FCM target-level 8 vendor image.
PRODUCT_SHIPPING_API_LEVEL := 34
PRODUCT_ENFORCE_VINTF_MANIFEST := true

## Dual-arch (ro.zygote=zygote64_32)
# The dump ships a 32-bit GPU driver and gralloc at the same Arm DDK release as
# the 64-bit ones. BRINGUP-NOTES.md 7.17 and 7.25.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)

## Dynamic partitions
PRODUCT_USE_DYNAMIC_PARTITIONS := true

PRODUCT_BUILD_SYSTEM_EXT_IMAGE := true
PRODUCT_BUILD_PRODUCT_IMAGE := true
PRODUCT_BUILD_VENDOR_DLKM_IMAGE := true
PRODUCT_BUILD_ODM_DLKM_IMAGE := true
PRODUCT_BUILD_SYSTEM_DLKM_IMAGE := true

##
## NOTE ON PACKAGE SELECTION
##
## Almost the entire vendor partition is taken from the stock ROM via
## proprietary-files.txt (HALs, their .rc files and VINTF fragments included).
## Only add PRODUCT_PACKAGES entries here for things that are NOT extracted,
## otherwise the build fails with duplicate install rules.
##
## extract-files.py emits every blob as a Soong prebuilt carrying its DT_NEEDED
## entries in shared_libs, and Soong installs those alongside it. So what is
## left below is only what no *prebuilt* links directly: libraries reached only
## through a source-built module, or only by dlopen. A missing one shows up at
## exec time, not at build time. To re-derive: tools/check-vendor-deps.py
## against the built image (BRINGUP-NOTES.md section 3).
##

# Codec 2 bufferpool AIDL, reached through the source-built libcodec2_vndk.
PRODUCT_PACKAGES += \
    android.hardware.media.bufferpool2-V1-ndk.vendor

# Rockchip display management service (drm_device_management), in system_server
$(call inherit-product, hardware/rockchip/fwkservices/fwkservices.mk)

# The two Wi-Fi blobs carry ;DISABLE_DEPS and declare nothing, so their
# dependencies cannot be expressed as shared_libs. libcrypto_shim_rk (sk_dup)
# and libcrypto_shim (CBS_init) are the two shims blob_fixups add to
# wpa_supplicant's NEEDED; an add_needed() alone installs nothing.
# libcrypto_shim takes the .vendor suffix because compat's module is
# system_ext_specific + vendor_available; ours in libshims/ is vendor: true.
PRODUCT_PACKAGES += \
    android.hardware.wifi-V1-ndk.vendor \
    android.hardware.wifi.supplicant-V2-ndk.vendor \
    android.system.keystore2-V1-ndk.vendor \
    libcrypto_shim.vendor \
    libcrypto_shim_rk \
    libkeystore-engine-wifi-hidl \
    libwifi-system-iface.vendor

# Legacy HIDL interfaces for the camera/bluetooth/audio/tv.input HIDL HALs that
# stock ships alongside the AIDL ones. These are pulled in by the source-built
# @x.y-impl wrappers further down, not by a blob.
PRODUCT_PACKAGES += \
    android.hardware.bluetooth.audio@2.0.vendor \
    android.hardware.bluetooth.audio@2.1.vendor \
    android.hardware.camera.common@1.0.vendor \
    android.hardware.camera.device@1.0.vendor \
    android.hardware.camera.device@3.2.vendor \
    android.hardware.camera.device@3.3.vendor \
    android.hardware.camera.device@3.4.vendor \
    android.hardware.camera.device@3.5.vendor \
    android.hardware.camera.device@3.6.vendor \
    android.hardware.camera.provider@2.4.vendor \
    android.hardware.keymaster@3.0.vendor \
    android.hardware.tv.input@1.0.vendor

# proprietary: true, so no .vendor suffix.
PRODUCT_PACKAGES += \
    android.hardware.camera.provider@2.4-external \
    android.hardware.camera.provider@2.4-legacy

##
## BLOBS REPLACED BY BUILDS FROM AOSP SOURCE
##
## Everything below used to be extracted and is upstream code the stock ROM
## shipped unmodified. Authorship was checked against Rockchip's own Android 14
## SDK tree; the exclusions in gen-proprietary-files.py carry the results,
## including the five places where Rockchip had touched the source and the blob
## therefore stayed (audio@7.1-impl, bluetooth@1.0, tv.hdmi.{cec,connection},
## wifi).
##
## Module names take a .vendor suffix when the module is only vendor_available;
## modules that are already "vendor: true" must not have one.

# Audio: ALSA helpers, the effect factory and its effects, and the legacy
# libhardware audio modules. The real HAL is the audio.primary.rk30board blob.
PRODUCT_PACKAGES += \
    android.hardware.audio.effect@7.0-impl \
    audio.primary.default \
    audio.r_submix.default \
    audio.stub.default \
    audio.usb.default \
    audio.usbv2.default \
    libalsautils \
    libalsautilsv2 \
    libaudiopreprocessing \
    libbundlewrapper \
    libdownmix \
    libdynproc \
    libeffectproxy \
    libeffects \
    libhapticgenerator \
    libldnhncr \
    libnbaio_mono \
    libreverbwrapper \
    libtinyalsav2.vendor \
    libvibratorutils.vendor \
    libvisualizer

# Bluetooth audio HAL module and the HIDL session library it links
PRODUCT_PACKAGES += \
    audio.bluetooth.default \
    libbluetooth_audio_session

# Codec 2: the plugin store and the two bufferpool libraries
PRODUCT_PACKAGES += \
    libcodec2_hidl_plugin \
    libstagefright_aidl_bufferpool2.vendor \
    libstagefright_bufferpool@2.0.1.vendor

# Camera: AOSP's HIDL device and provider wrappers, which Rockchip's
# camera.device-*-impl-rk.so and the two legacy providers link against
PRODUCT_PACKAGES += \
    android.hardware.camera.provider@2.4-impl \
    camera.device@1.0-impl \
    camera.device@3.2-impl \
    camera.device@3.3-impl \
    camera.device@3.4-external-impl \
    camera.device@3.4-impl \
    camera.device@3.5-external-impl \
    camera.device@3.5-impl \
    camera.device@3.6-external-impl

# Graphics. libdrm is dropped from the blob list too -- nothing in the stock
# image uses Rockchip's additions to it -- and needs no line, since 18
# prebuilts name it in NEEDED.
PRODUCT_PACKAGES += \
    gralloc.default

# ClearKey DRM plugin, to go with android.hardware.drm-service.clearkey below
PRODUCT_PACKAGES += \
    libdrmclearkeyplugin

# TV input HIDL wrapper (rockchip.hardware.tv.input.* is the implementation)
PRODUCT_PACKAGES += \
    android.hardware.tv.input@1.0-impl

# Plain utility libraries reached by dlopen or through a source-built module
PRODUCT_PACKAGES += \
    libbinderdebug.vendor \
    libz_stable.vendor \
    local_time.default

# vndservicemanager. Stock ships the .rc but the binary was excluded, so every
# boot failed to start this shutdown-critical service. The module installs the
# identical .rc itself.
PRODUCT_PACKAGES += \
    vndservicemanager

## Audio
# The HAL itself is extracted; only the HIDL interface libraries the impl blob
# does not name in NEEDED are built here.
PRODUCT_PACKAGES += \
    android.hardware.audio.common@2.0.vendor \
    android.hardware.audio.common@5.0.vendor \
    android.hardware.audio.common@7.0-util.vendor \
    android.hardware.audio.effect@7.0.vendor \
    android.hardware.audio.effect@7.0-util.vendor

# audiohalservice dlopens android.hardware.bluetooth.audio-impl.so. AOSP's
# module carries its own VINTF fragment and libbluetooth_audio_session_aidl.
# The whole BT audio stack is on V4 now that audio.bluetooth.default is built
# from source too.
PRODUCT_PACKAGES += \
    android.hardware.bluetooth.audio-impl

PRODUCT_COPY_FILES += \
    frameworks/av/services/audiopolicy/config/a2dp_audio_policy_configuration_7_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/a2dp_audio_policy_configuration_7_0.xml \
    $(COMMON_PATH)/configs/audio/audio_effects.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects.xml \
    $(COMMON_PATH)/configs/audio/audio_policy_configuration_singlehal.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration_singlehal.xml \
    $(COMMON_PATH)/configs/audio/audio_policy_volumes_drc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes_drc.xml \
    frameworks/av/services/audiopolicy/config/bluetooth_audio_policy_configuration_7_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/bluetooth_audio_policy_configuration_7_0.xml \
    frameworks/av/services/audiopolicy/config/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml \
    frameworks/av/services/audiopolicy/config/r_submix_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/r_submix_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/usb_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/usb_audio_policy_configuration.xml

## Codec 2
# libcodec2_rk_{store,component}.so and libcodec2_hidl_plugin.so are extracted;
# these are the AOSP halves no blob names in NEEDED.
PRODUCT_PACKAGES += \
    libcodec2_soft_common.vendor \
    libstagefright_bufferpool@2.0.vendor

# media_codecs_{c2_base,google_c2,performance}.xml are per SoC.
PRODUCT_COPY_FILES += \
    $(COMMON_PATH)/configs/media/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    $(COMMON_PATH)/configs/media/media_profiles_V1_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml \
    $(COMMON_PATH)/configs/media/android.hardware.media.c2@1.1-extended-seccomp-policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/android.hardware.media.c2@1.1-extended-seccomp-policy

## DRM
# ClearKey is built from source; CAS is dropped (it lives in an APEX now).
PRODUCT_PACKAGES += \
    android.hardware.drm-service.clearkey

## Display
PRODUCT_COPY_FILES += \
    $(COMMON_PATH)/configs/display/HwComposerEnv.xml:$(TARGET_COPY_OUT_VENDOR)/etc/HwComposerEnv.xml \
    $(COMMON_PATH)/configs/display/display_settings.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings.xml \
    $(COMMON_PATH)/configs/display/package_uimode_config.xml:$(TARGET_COPY_OUT_VENDOR)/etc/package_uimode_config.xml

## Cgroups
# The vendor task profiles are AOSP's API-28 set, which is what stock ships.
# The matching cgroups_28.json is not usable as-is: it declares schedtune
# without "Optional", no Rockchip kernel has CONFIG_SCHED_TUNE, and under
# Android 16 that one failure aborts SetupCgroups and the whole boot. The local
# copy adds "Optional": true, as the stock vendor image does.
PRODUCT_COPY_FILES += \
    $(COMMON_PATH)/configs/cgroups.json:$(TARGET_COPY_OUT_VENDOR)/etc/cgroups.json \
    system/core/libprocessgroup/profiles/task_profiles_28.json:$(TARGET_COPY_OUT_VENDOR)/etc/task_profiles.json

## Init (system)
# fstab.rk30board is per board.
PRODUCT_COPY_FILES += \
    $(COMMON_PATH)/init-files/init.system.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/init.rockchip.rc


## Recovery init
# Switches recovery's USB gadget to configfs, without which neither adb nor
# fastbootd work over USB. See the file itself.
PRODUCT_COPY_FILES += \
    $(COMMON_PATH)/init-files/init.recovery.rk30board.rc:$(TARGET_COPY_OUT_RECOVERY)/root/init.recovery.rk30board.rc

## fastbootd
# init.rc declares the service unconditionally but the binary is only installed
# when the product asks for it. The example_recovery IFastboot is enough to
# flash logical partitions.
#
# Deliberately NO android.hardware.boot-service.default_recovery: it needs A/B
# slots, and installing it is what declares the interface, so fastbootd would
# then block forever waiting for a HAL that aborts on every respawn.
PRODUCT_PACKAGES += \
    fastbootd \
    android.hardware.fastboot-service.example_recovery

## Memory allocators for the Rockchip media/graphics blobs. libdmabufheap
## needs no line -- seven prebuilts name it in NEEDED.
PRODUCT_PACKAGES += \
    libion

## Wi-Fi
# HAL, wpa_supplicant and hostapd all come from stock; only the system-side
# daemon is built.
PRODUCT_PACKAGES += \
    wificond

## Permissions
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.camera.external.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.external.xml \
    frameworks/native/data/etc/android.hardware.ethernet.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.ethernet.xml \
    frameworks/native/data/etc/android.hardware.faketouch.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.faketouch.xml \
    frameworks/native/data/etc/android.hardware.hdmi.cec.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.hdmi.cec.xml \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.opengles.aep.xml \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml \
    frameworks/native/data/etc/android.hardware.vulkan.compute-0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.compute-0.xml \
    frameworks/native/data/etc/android.hardware.vulkan.level-1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.level-1.xml \
    frameworks/native/data/etc/android.hardware.vulkan.version-1_3.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.version-1_3.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.wifi.passpoint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.passpoint.xml \
    frameworks/native/data/etc/android.software.ipsec_tunnels.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.ipsec_tunnels.xml

## Overlays
DEVICE_PACKAGE_OVERLAYS += $(COMMON_PATH)/overlay

## Inherit from the common proprietary files makefile
$(call inherit-product-if-exists, vendor/rockchip/rk35xx-common/rk35xx-common-vendor.mk)
