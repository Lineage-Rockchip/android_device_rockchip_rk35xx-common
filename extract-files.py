#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2025 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import struct

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)

from extract_utils.fixups_lib import (
    lib_fixups as lib_fixups_default,
    lib_fixups_user_type,
)

from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

# libshims/ is behind a soong_namespace, so the generated vendor blueprint has
# to import it.
namespace_imports = [
    'device/rockchip/rk35xx-common',
]

GRAPHICS_COMMON_V4 = 'android.hardware.graphics.common-V4-ndk.so'
GRAPHICS_COMMON_CURRENT = 'android.hardware.graphics.common-V7-ndk.so'


# --- V1_1::utils::ComponentStore grew between Android 14 and 16 -------------
# sizeof went 264 -> 288 and the RefBase subobject moved 248 -> 272. The codec
# service inlined the complete object constructor, so it carries both numbers
# twice: as immediates in main(), and as the virtual-base offsets inside the
# ComponentStore vtable family it emits into .data.rel.ro. Patching only the
# immediates fixes construction and then dies in registerAsService(), which
# reads the offset back out of the vtable. BRINGUP-NOTES.md 7.28.
#
# Every edit asserts its old value, so a dump or platform change fails the
# extraction instead of quietly producing a blob that crashes on the device.
COMPONENTSTORE_VBASE_OLD = 248
COMPONENTSTORE_VBASE_NEW = 272
COMPONENTSTORE_VBASE_COUNT = 15

# (anchored pattern, replacement). Each must occur exactly once.
COMPONENTSTORE_INSNS = [
    # mov w0, #0x108 -> #0x120        operator new(sizeof)
    ('00218052ea040094', '00248052ea040094'),
    # add x20, x19, #0xf8 -> #0x110   RefBase subobject
    ('0305009474e20391', '0305009474420491'),
    # str x9, [x19, #248] -> [x19, #272]   secondary vptr
    ('680200f9697e00f9f60b40f9', '680200f9698a00f9f60b40f9'),
]


def _data_rel_ro(data: bytes):
    """Return (offset, size) of .data.rel.ro from the ELF64 section headers."""
    e_shoff = struct.unpack_from('<Q', data, 0x28)[0]
    e_shentsize, e_shnum, e_shstrndx = struct.unpack_from('<HHH', data, 0x3a)
    str_off = struct.unpack_from(
        '<Q', data, e_shoff + e_shstrndx * e_shentsize + 0x18
    )[0]
    for i in range(e_shnum):
        sh = e_shoff + i * e_shentsize
        name_off = struct.unpack_from('<I', data, sh)[0]
        end = data.index(b'\0', str_off + name_off)
        if data[str_off + name_off:end] == b'.data.rel.ro':
            off, size = struct.unpack_from('<QQ', data, sh + 0x18)
            return off, size
    raise AssertionError('.data.rel.ro not found')


def patch_componentstore_layout(ctx, file, file_path, *args, **kwargs):
    with open(file_path, 'rb') as f:
        data = bytearray(f.read())

    for search, replace in COMPONENTSTORE_INSNS:
        sb, rb = bytes.fromhex(search), bytes.fromhex(replace)
        assert len(sb) == len(rb)
        n = data.count(sb)
        assert n == 1, f'{file_path}: {search} occurs {n} times, expected 1'
        data[:] = data.replace(sb, rb)

    off, size = _data_rel_ro(data)
    patched = 0
    for i in range(off, off + size, 8):
        word = struct.unpack_from('<q', data, i)[0]
        if abs(word) != COMPONENTSTORE_VBASE_OLD:
            continue
        sign = 1 if word > 0 else -1
        struct.pack_into('<q', data, i, sign * COMPONENTSTORE_VBASE_NEW)
        patched += 1
    assert patched == COMPONENTSTORE_VBASE_COUNT, (
        f'{file_path}: patched {patched} virtual-base offsets, '
        f'expected {COMPONENTSTORE_VBASE_COUNT}'
    )

    with open(file_path, 'wb') as f:
        f.write(data)


# libbt-vendor's bt_lpm_param_t (HCI Write_Sleep_Mode); sleep_mode 0 keeps the controller awake.
BT_LPM_PARAM = bytes.fromhex('01 01 01 01 01 01 01 00 00 00 00 00')


def patch_bt_lpm_sleep_mode(ctx, file, file_path, *args, **kwargs):
    with open(file_path, 'rb') as f:
        data = bytearray(f.read())
    n = data.count(BT_LPM_PARAM)
    assert n == 1, f'{file_path}: LPM param block occurs {n} times, expected 1'
    data[data.index(BT_LPM_PARAM)] = 0
    with open(file_path, 'wb') as f:
        f.write(data)


def both(*paths):
    """Expand vendor/lib64 paths to both bitnesses. BRINGUP-NOTES.md 7.25."""
    out = []
    for path in paths:
        out.append(path)
        out.append(path.replace('vendor/lib64/', 'vendor/lib/', 1))
    return tuple(out)

blob_fixups: blob_fixups_user_type = {
    # Rockchip's camera impls call two libui entry points Android 14 dropped:
    # the GraphicBufferMapper::lock overload taking outBytesPerPixel/
    # outBytesPerStride, and the single-argument unlock. compat's libui_shim
    # forwards both to the overloads that survived. Still true of the RKR8
    # blobs, which now link libui directly and pull in more of it -- including
    # unlockAsync, which Android 16 did not remove but did make inline, so
    # libui no longer exports it. libshims/ui_shim.cpp is that one symbol.
    both(
        'vendor/lib64/camera.device-external-impl-rk.so',
        'vendor/lib64/camera.device-internal-impl-rk.so',
    ): blob_fixup()
        .add_needed('libui_shim.so')
        .add_needed('libui_shim_rk.so'),
    # graphics.common is at V7 tree-side, and Soong rejects two versions of one
    # aidl_interface in a single dependency graph. The interface is types-only
    # and AIDL is ABI-compatible upwards.
    #
    # The list shrank with the RKR8 rebase: libGLES_mali and both camera impls
    # dropped graphics.common-V4 from their NEEDED entirely, so only the Arm
    # gralloc allocator and mapper still need the rewrite.
    both(
        'vendor/bin/hw/android.hardware.graphics.allocator-V1-service',
        'vendor/lib64/hw/android.hardware.graphics.allocator-V1-arm.so',
        'vendor/lib64/hw/android.hardware.graphics.allocator-V1-bifrost.so',
        'vendor/lib64/hw/android.hardware.graphics.mapper@4.0-impl-bifrost.so',
    ): blob_fixup()
        .replace_needed(GRAPHICS_COMMON_V4, GRAPHICS_COMMON_CURRENT),
    # Same problem for IAllocator: stock mixes V1 and V2 clients. Bumping a
    # client is safe; the V1 *service* is deliberately left alone, because
    # relinking a server would have it advertise methods it does not implement.
    both('vendor/lib64/hw/camera.rk30board.so'): blob_fixup()
        .replace_needed(
            'android.hardware.graphics.allocator-V1-ndk.so',
            'android.hardware.graphics.allocator-V2-ndk.so',
        ),
    # wpa_supplicant references sk_dup (renamed OPENSSL_sk_dup; see
    # libshims/crypto_shim.cpp) and CBS_init (now OPENSSL_INLINE, so no longer
    # exported; compat's libcrypto_shim is exactly that function). Deliberately
    # not libcrypto-v33: there is no libssl-v33, and wpa_supplicant links libssl
    # too, which would leave one BoringSSL linked against another.
    'vendor/bin/hw/wpa_supplicant': blob_fixup()
        .add_needed('libcrypto_shim_rk.so')
        .add_needed('libcrypto_shim.so'),
    both('vendor/lib64/libbt-vendor.so'): blob_fixup()
        .call(patch_bt_lpm_sleep_mode, need_tmp_dir=False),
    # BRINGUP-NOTES.md 7.28.
    'vendor/bin/hw/android.hardware.media.c2@1.1-service': blob_fixup()
        .call(patch_componentstore_layout, need_tmp_dir=False),
    # cppbor::Item gained two virtual methods since Android 14, so the Android
    # 14 library is extracted rather than built (BRINGUP-NOTES.md section 7.7).
    # It cannot keep its own name, hence the rename in proprietary-files.txt
    # and these NEEDED rewrites to match.
    (
        'vendor/bin/hw/android.hardware.security.keymint-service.optee',
        'vendor/lib64/libRkcppcose_rkp.so',
        'vendor/lib64/libRkkeymaster_portable.so',
        'vendor/lib64/libRkkeymint.so',
        'vendor/lib64/libRkpuresoftkeymasterdevice.so',
        'vendor/lib64/libRksoftkeymasterdevice.so',
    ): blob_fixup()
        .replace_needed(
            'libcppbor_external.so',
            'libcppbor_external_rk.so',
        ),
    # The whole KeyMint process onto the Android 13 VNDK snapshot in
    # hardware/lineage/compat. This list is the *whole* process, deliberately:
    # half-migrating would put two BoringSSLs in one address space.
    (
        'vendor/bin/hw/android.hardware.security.keymint-service.optee',
        'vendor/lib64/libRkTeeKeymaster.so',
        'vendor/lib64/libRkcppcose_rkp.so',
        'vendor/lib64/libRkkeymaster4.so',
        'vendor/lib64/libRkkeymaster_portable.so',
        'vendor/lib64/libRkkeymint.so',
        'vendor/lib64/libRkpuresoftkeymasterdevice.so',
        'vendor/lib64/libRksoftkeymasterdevice.so',
        'vendor/lib64/libcppbor_external_rk.so',
    ): blob_fixup()
        .replace_needed('libcrypto.so', 'libcrypto-v33.so'),
    # Same process, same reasoning, for libbase.
    (
        'vendor/bin/hw/android.hardware.security.keymint-service.optee',
        'vendor/lib64/libRkkeymaster4.so',
        'vendor/lib64/libRkkeymint.so',
        'vendor/lib64/libRkpuresoftkeymasterdevice.so',
        'vendor/lib64/libRksoftkeymasterdevice.so',
        'vendor/lib64/libcppbor_external_rk.so',
    ): blob_fixup()
        .replace_needed('libbase.so', 'libbase-v33.so'),
    # sizeof(tinyxml2::XMLDocument) went 776 -> 880 between 9.0.0 and 11.0.0.
    # Each of these blobs declares one as a *local*, so it reserves the Android
    # 14 size and the current library constructs 104 bytes past the end of it,
    # into the caller's frame. That is what crash-loops the composer, lights and
    # camera services. BRINGUP-NOTES.md section 7.12.
    #
    #
    # camera.rk30board.so is new to this list: RKR8 gave it a libtinyxml2
    # dependency the RKR5 build did not have.
    both(
        'vendor/bin/hw/android.hardware.camera.provider-V1-external-service-rk',
        'vendor/bin/hw/android.hardware.lights-service.rockchip',
        'vendor/lib64/android.hardware.camera.provider-V1-external-impl-rk.so',
        'vendor/lib64/camera.device-external-impl-rk.so',
        'vendor/lib64/camera.device-internal-impl-rk.so',
        'vendor/lib64/hw/camera.rk30board.so',
        'vendor/lib64/hw/hwcomposer.rk30board.so',
    ): blob_fixup()
        .replace_needed('libtinyxml2.so', 'libtinyxml2-v34.so'),
    # The same shape one layer up: ComposerResources embeds
    # ComposerHandleImporter by value, which lost 16 bytes since Android 14, and
    # the blob instantiates it itself through an inline create(). compat's
    # composer_utils is that Android 14 source under a -v34 name.
    # BRINGUP-NOTES.md section 7.13.
    # ...and the reason the same service also drops composer@2.2-resources.so.
    # That library exports *zero* symbols -- it is a header-only wrapper -- and
    # the service imports none of them, so the NEEDED entry does nothing except
    # pull in the Android 16 composer@2.1-resources.so, and with it libui.so.
    # Both then sit in the process's global group, which the linker searches
    # before a dlopen'd library's own, so they would win over the -v34 pair
    # that hwcomposer.rk30board.so and this service are supposed to be using.
    # Removing it is what makes both -v34 substitutions actually take effect.
    'vendor/bin/hw/android.hardware.graphics.composer3-service.rockchip': blob_fixup()
        .replace_needed(
            'android.hardware.graphics.composer@2.1-resources.so',
            'android.hardware.graphics.composer@2.1-resources-v34.so',
        )
        .remove_needed('android.hardware.graphics.composer@2.2-resources.so'),
    # sizeof(android::GraphicBuffer) went 256 -> 3376 between Android 14 and 16.
    # This blob does `operator new(0x100)` and then calls the constructor out of
    # libui, so the current library initialises 3120 bytes past the end of the
    # allocation. It survives until something actually composites a solid-colour
    # layer -- an app splash screen -- and then dies in ~GraphicBuffer reading a
    # member that was never inside the object. BRINGUP-NOTES.md section 7.19.
    #
    # One library is the whole fix: nothing else in the composer process touches
    # GraphicBuffer or GraphicBufferMapper. librga.so imports neither (which is
    # what made 7.9 hold off), and composer@2.1-resources-v34 imports neither.
    # All 8 libui symbols this blob needs are exported by the v34 snapshot.
    both('vendor/lib64/hw/hwcomposer.rk30board.so'): blob_fixup()
        .replace_needed('libui.so', 'libui-v34.so'),
    # librga.so declares libui.so and imports not one symbol from it. Left
    # alone it is the only remaining path by which the platform libui reaches
    # the composer process, so dropping it means that process holds exactly one
    # libui -- the v34 one -- instead of two with overlapping definitions. That
    # was 7.11's standing objection to using the snapshot here at all.
    both('vendor/lib64/librga.so'): blob_fixup()
        .remove_needed('libui.so'),
}  # fmt: skip

# Libraries extracted under -rockchip module names (;MODULE_SUFFIX= in
# proprietary-files.txt). A module rename does not rewrite the DT_NEEDED
# entries that name the library, so map them here too.
suffixed_libs = (
    'libwifi-hal-aic',
    'libwifi-hal-bcm',
    'libwifi-hal-bes',
    'libwifi-hal-rtk',
    'libwifi-hal-skw',
)


def lib_fixup_rockchip(lib: str, partition: str, *args, **kwargs):
    return f'{lib}-rockchip' if partition in ('odm', 'vendor') else lib


lib_fixups: lib_fixups_user_type = {
    **lib_fixups_default,
    suffixed_libs: lib_fixup_rockchip,
}

module = ExtractUtilsModule(
    'rk35xx-common',
    'rockchip',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
