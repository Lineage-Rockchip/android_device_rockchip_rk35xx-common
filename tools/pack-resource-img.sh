#!/bin/bash
#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
# Build a resource.img around a freshly built device tree.
#
# Rockchip's resource.img is an "RSCE" container that rides in the boot image
# "second" area (BoardConfigCommon.mk sets TARGET_BOOTLOADER_IS_2ND). It holds
# the boot logo, the battery bitmaps and -- the part that matters here -- a copy
# of the device tree named rk-kernel.dtb, which U-Boot reads.
#
# That copy is live: it is not merely decorative. Back when the device tree was
# a prebuilt and had to be patched binary-wise to fix dr_mode, patching only the
# appended dtb changed nothing -- U-Boot read this one and won.
# So whenever the kernel device tree changes, this image has to be repacked or
# the board boots a new kernel against an old device tree.  Android.mk wires
# this into the build so that cannot be forgotten.
#
# The bitmaps are not built from source; they are lifted out of the stock image,
# which is why that image is still needed as a template.
#
# The packer is Rockchip's own scripts/resource_tool, built as a host tool by
# any kernel build (hostprogs-always-$(CONFIG_ARCH_ROCKCHIP)).
#
# Usage:
#   tools/pack-resource-img.sh <template.img> <rk-kernel.dtb> <resource_tool> <out.img>
#
# Entry order is preserved exactly as stock shipped it. U-Boot looks entries up
# by name so order should not matter, but there is no reason to find out.

set -euo pipefail

TEMPLATE=${1:?usage: $0 <template.img> <rk-kernel.dtb> <resource_tool> <out.img>}
DTB=${2:?usage: $0 <template.img> <rk-kernel.dtb> <resource_tool> <out.img>}
TOOL=${3:?usage: $0 <template.img> <rk-kernel.dtb> <resource_tool> <out.img>}
OUT=${4:?usage: $0 <template.img> <rk-kernel.dtb> <resource_tool> <out.img>}

TOOL=$(readlink -f "$TOOL")
TEMPLATE=$(readlink -f "$TEMPLATE")
DTB=$(readlink -f "$DTB")

[ -x "$TOOL" ]     || { echo "no resource_tool at $TOOL -- build the kernel first"; exit 1; }
[ -f "$DTB" ]      || { echo "no dtb at $DTB"; exit 1; }
[ -f "$TEMPLATE" ] || { echo "no template resource.img at $TEMPLATE"; exit 1; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# Unpack the template so the logo and battery bitmaps come along unchanged.
( cd "$WORK" && "$TOOL" --unpack --image="$TEMPLATE" >/dev/null )
cp "$DTB" "$WORK/out/rk-kernel.dtb"

# Every entry the template carries, in the template's own order.
mapfile -t ENTRIES < <("$TOOL" --print --unpack --image="$TEMPLATE" | sed -n 's/^[[:space:]]*path:\(.*\)$/\1/p')
[[ " ${ENTRIES[*]} " == *" rk-kernel.dtb "* ]] || { echo "template has no rk-kernel.dtb"; exit 1; }

( cd "$WORK/out" && "$TOOL" --pack --root=. --image="$WORK/new.img" "${ENTRIES[@]}" >/dev/null )

# Round-trip the result before letting it near the boot image.
( cd "$WORK" && mkdir -p check && cd check && "$TOOL" --unpack --image="$WORK/new.img" >/dev/null )
for f in "${ENTRIES[@]}"; do
    cmp "$WORK/check/out/$f" "$WORK/out/$f"
done
cmp "$WORK/check/out/rk-kernel.dtb" "$DTB"

mkdir -p "$(dirname "$OUT")"
cp "$WORK/new.img" "$OUT"
echo "resource.img: $(stat -c%s "$OUT") bytes, rk-kernel.dtb = $(stat -c%s "$DTB") bytes"
