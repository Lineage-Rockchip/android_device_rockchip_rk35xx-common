# device/rockchip/rk35xx-common

Shared LineageOS 23.2 tree for Rockchip RK3576 and RK3588 Android TV devices.

It is modelled on `device/amlogic/ne-common`, with one extra layer:

| Layer | Holds |
| --- | --- |
| this directory | everything shared by both SoCs: `BoardConfigCommon.mk`, `common.mk`, the blob list and extract scripts, shims, tools, sepolicy, configs |
| `rk3576/`, `rk3588/` | what differs per SoC: `BoardConfigSoc.mk` (platform, CPU variants, boot devices, kernel config fragment), `<soc>.mk`, `vendor.prop`, `init.<soc>.rc`; for RK3576 also the AI-PQ settings app |
| board tree (e.g. `device/h96/m9s`) | what the board wires up |

A board's `BoardConfig.mk` includes `rk35xx-common/<soc>/BoardConfigSoc.mk`,
which includes `BoardConfigCommon.mk`; its `device.mk` inherits
`rk35xx-common/<soc>/<soc>.mk`, which inherits `common.mk`. Both SoCs share
one vendor blob tree, `vendor/rockchip/rk35xx-common`.

The board tree owns the super partition size, the screen density, the kernel
config fragment and dtb name, the WiFi external module and the module load
list, the stock `dtbo.img` and `resource.img` template, `fstab.rk30board`,
`audio_policy_configuration.xml`, the Bluetooth UART (`bt_vendor.conf` and
`init.connectivity.rc`, which must move together), the ethernet modules in
`init.insmod.cfg`, and the IR remote keylayout.

## Kernel

The kernel is built in-tree by LineageOS' own `kernel.mk`, out of
`kernel/rockchip/kernel-6.1` -- Khadas' RK3576 BSP, linux 6.1.141, non-GKI. Its
in-kernel Mali is `g25p0-00eac0`, which is what both halves of `libGLES_mali` in
the current dump report, and the reason for preferring it over the 6.1.99
Rockchip drop this port used earlier.

| What | Where |
| --- | --- |
| Source | `kernel/rockchip/kernel-6.1` (`TARGET_KERNEL_SOURCE`) |
| Config | `rockchip_defconfig` + `android-14.config`, then the SoC fragment (`rk3576.config`), then the board's own fragment (`rk3576_m9s.config`), in that order |
| Board dts | `arch/arm64/boot/dts/rockchip/rk3576-m9{,s}.dts` on `rk3576-h96-max.dtsi` |
| External modules | `kernel/rockchip/kernel-modules/wifi/aic8800` |
| `dtbo.img` | still the stock one (a single empty overlay) |
| `resource.img` bitmaps | still from the stock image |

`rk3576_m9s.config` is the board delta, derived by diffing the merged config
against the stock 6.1.75 one; it turns off Edge-2L hardware and debug options
and matches stock's BCMDHD bus choice. The M9 and the M9S share it -- the two
boards are electrically identical and differ only in their IR remote. The board
tree appends it to `TARGET_KERNEL_CONFIG_EXT` *after* including
`<soc>/BoardConfigSoc.mk`, which is what keeps it last in the merge.

The board dts to build is named once, per board, as `TARGET_DTB_NAME`;
`TARGET_DTB_LIST_WILDCARD` turns it into the single dtb that goes into
`dtb.img`.

The AIC8800 WiFi/BT driver is not part of the kernel tree -- Rockchip ships its
third-party WiFi drivers in `external/wifi_driver`, mirrored here as
`kernel/rockchip/kernel-modules/wifi`. It is built as an out-of-tree module via
`TARGET_KERNEL_EXT_MODULES`, the way `device/amlogic/g12-common` builds mali and
media. The driver's own Makefiles were taught to keep their hardcoded vendor
toolchain paths and recursive `make` rules behind `ifeq ($(KERNELRELEASE),)`, so
they apply only to a standalone build and not when kbuild drives them.

Rockchip stores `resource.img` in the boot image "second" area, so
`TARGET_BOOTLOADER_IS_2ND := true` and `build/tasks/resource_img.mk` builds it as
`$(PRODUCT_OUT)/2ndbootloader` for `mkbootimg --second`. **It is repacked around
the freshly built dtb on every build.** The `rk-kernel.dtb` entry inside that
container is the copy U-Boot actually reads; leaving a stale one there boots the
new kernel against the old device tree, with no error anywhere.

## Vendor blobs

`proprietary-files.txt` is maintained by hand against a stock dump.
`proprietary-files-rk3576.txt` holds the blobs only RK3576 installs; their
product makefile entries are guarded by `ROCKCHIP_SOC`, which `rk3576/rk3576.mk`
and `rk3588/rk3588.mk` set before inheriting `common.mk`.

Because that ROM is already Android 14 / SDK 34 with an FCM target-level 8
vendor image, essentially the whole vendor partition is reused; only VNDK/AOSP-
built libraries and source-built infrastructure are excluded.

The dump is the **FriendlyELEC NanoPi RK3576**, built 2026-06-01, Rockchip SDK
`ANDROID14_MS_RKR3` -- the MS (media / set-top-box) SDK line, not the mainline
RKR one, and not the H96 Max M9S ROM this port started from (`ANDROID14_RKR5`,
2025-03-14) nor the Khadas Edge-2L `ANDROID14_RKR8` dump used from 7.16. Same
SoC, same vendor API level, same vendor security patch (2025-06-05) and the
same Mali release (`g25p0-00eac0`) as that Edge-2L dump, so the compat fixups
below still apply and the userspace still matches the kbase this tree builds.

Two things make it the base rather than the Edge-2L dump:

**It ships both bitnesses,** including `lib/egl/libGLES_mali.so` and
`lib/hw/android.hardware.graphics.mapper@4.0-impl-bifrost.so`, at the *same*
Arm DDK release as the 64-bit halves. That is what lets the product inherit
`core_64_bit.mk` again: the Edge-2L dump had no 32-bit vendor libraries at all,
so under `zygote64_32` the secondary zygote aborted during preload with
"couldn't find an OpenGL ES implementation" and the boot looped, forcing
`core_64_bit_only.mk` and losing 32-bit-only apps. Taking the two files from an
RKR5 dump instead would have split the Arm DDK and gralloc across bitnesses
(`g15p0` against `g25p0`); this dump makes them a matched set.

Because of that, **a blob fixup written for a `vendor/lib64` path almost always
has a `vendor/lib` twin.** `extract-files.py` has a `both()` helper that expands
one into both, and the fixups go through it; an ABI break in a platform library
is an ABI break in both arches.

**It ships the AI-PQ stack** -- `libpq`, `librkswpq`, `librkhwpq`, `libvdpp`,
`librknnrt`, `libsculptor`, `aipq_config.json` and the 57 `rkaipq_mssr_*` RKNN
models -- which no RKR dump for this SoC carries. See the picture-quality block
in `vendor.prop` for how it is wired and why Sculptor ships disabled.

`vendor.prop` follows the same dump. Before adding or keeping a Rockchip
property there, check that a blob in the dump actually contains the string --
`strings -a` over `vendor/lib64` and `vendor/bin/hw` -- because the vendor
ROM's build.prop covers every board the SDK supports. Roughly a third of what
the M9S ROM set addressed libraries this one does not ship.

Blobs that are not in that dump are *pinned*: listed as `path|sha1`, hashed
from the second dump they come from. The only entries today are the five AIC8800 Bluetooth firmware
files from the M9S ROM. This dump *does* ship an AIC8800 set, but a different
build of it, and the M9S files are the ones Bluetooth is known to work with on
this board (7.20) -- so they stay pinned until the dump's are tried. extract-utils backs pinned files up out of `vendor/` before it cleans and
restores them when the hash matches, so they live in the vendor repo and survive
every extraction against the main dump. Seeding is by hand, once: copy the
files into `vendor/rockchip/rk35xx-common/proprietary/`, then run
`./setup-makefiles.py`.

Extract with the board tree's script, which pulls in this common tree too:

    cd ../../h96/m9s && ./extract-files.py /path/to/stock/dump

Point it at a directory holding **only `vendor/`**. If the dump directory also
has a system-as-root `system/` (one containing `system/system/`),
extract-utils moves it to `system_root/` by copy-then-delete, which empties
the source if that is a writable mount.

`extract-files.py` uses `tools/extract-utils` (the Python one; the shell
`extract_utils.sh` is gone as of LineageOS 22). `./setup-makefiles.py` is the
same script with `--regenerate_makefiles`: it rewrites `vendor/…` from the
blobs already extracted, without touching the dump. Useful flags:
`--only-common`, `--only-target`, `-s <section>`, `-n` (keep `vendor/`).

Note that the Python extract-utils turns every ELF under `bin/`, `lib/` and
`lib64/` into a Soong prebuilt module with `check_elf_files: true`, instead of
a plain `PRODUCT_COPY_FILES` entry. Unresolved `DT_NEEDED` entries are
therefore build errors now, and are fixed with `blob_fixup()` chains
(`.replace_needed()`, `.add_needed()`) in `extract-files.py`.

## Blobs against a newer platform

The vendor image is Android 14 and the platform is Android 16, and **VNDK was
removed in Android 15** -- a blob no longer links a frozen snapshot of the C++
platform libraries, it links whatever the tree builds today. Symbol names do not
change, so `check_elf_files` passes and the failure is a bare SIGSEGV or SIGABRT
at runtime.

Two things exist for this. `tools/check-vendor-vtables.py` finds it, by comparing
`_ZTV*` sizes against the stock ROM and reporting only libraries a blob actually
links. `hardware/lineage/compat/vndk/` fixes it, with prebuilt VNDK snapshots
under versioned names (`libbase-v33`, `libcrypto-v33`, `libui-v34`, ...) that a
blob is pointed at with `.replace_needed('libbase.so', 'libbase-v33.so')`. Add
new snapshots with that repo's `vndk/copy_libs.py`.

Migrate a whole *process* at once, never half of one: two copies of libcrypto or
libui in one address space, with objects crossing between them, is worse than
either version alone.

## Verified boot

Stock ships a completely unsigned `vbmeta` (AVB0 header, algorithm 0, no
descriptors), so `BOARD_AVB_ENABLE := false`. Stock also boots with
`androidboot.selinux=permissive`, which is kept for bring-up.
