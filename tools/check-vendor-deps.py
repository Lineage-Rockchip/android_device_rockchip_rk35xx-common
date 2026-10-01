#!/usr/bin/env python3
#
# Copyright (C) 2025 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
"""Report DT_NEEDED libraries that no partition actually provides.

Every HAL on this device is a stock prebuilt, so nothing in the build declares a
dependency on the interface and support libraries they link against. Anything
that is not requested explicitly in common.mk simply is not installed, and the
blob dies at exec time:

    CANNOT LINK EXECUTABLE "/vendor/bin/hw/android.hardware.security.keymint-service.optee":
        library "android.hardware.keymaster@4.0.so" not found: needed by main executable

which takes down keymint, gralloc, the composer, surfaceflinger and the boot
with it. This script finds all of them in one pass instead of one reboot each.

Two things it is careful about, both of which produced false "all clear"
results when checked by hand:

  * It reads obj/PACKAGING/<part>_intermediates/file_list.txt, which is what
    build_image.py is actually handed via --input-directory-filter-file. That
    list is $(filter $(TARGET_OUT_<PART>)/%, $(ALL_DEFAULT_INSTALLED_MODULES)),
    so it holds only what the product explicitly asked for.

    Neither the staging directory nor installed-files-<part>.json will do.
    Staging additionally holds every library Soong installed as a dependency
    side-effect, and installed-files-<part>.json is just a listing of staging.
    $OUT/vendor/lib64 carried 249 .so files while vendor.img held 207 -- and
    android.hardware.keymaster@4.0.so, which the keymint HAL needs, was among
    the 42 that exist in staging, are named in the json, and are filtered out
    of the image. Checking either source reports it as present.

  * It matches bitness. A 64-bit executable cannot load a 32-bit library, so
    lib/ and lib64/ are tracked as separate namespaces.

Libraries in system/etc/llndk.libraries.txt are provided by /system to the
vendor namespace and count as available to both bitnesses.

Usage:
    check-vendor-deps.py [$OUT]        (defaults to $ANDROID_PRODUCT_OUT)
"""

import json  # noqa: F401
import os
import subprocess
import sys
import collections

PARTITIONS = ('vendor', 'odm', 'vendor_dlkm')
# Always resolvable: bionic comes from the runtime APEX.
ALWAYS = {'libc.so', 'libm.so', 'libdl.so', 'libstdc++.so', 'ld-android.so'}


def installed(out, partition):
    """Paths actually packed into <partition>.img, per the build's own list.

    Returns (image-relative path, staging path) pairs. The file_list is relative
    to the partition root, e.g. "lib64/libfoo.so" for /vendor/lib64/libfoo.so.
    """
    path = os.path.join(out, 'obj/PACKAGING', f'{partition}_intermediates',
                        'file_list.txt')
    if not os.path.exists(path):
        print(f'warning: no file_list.txt for {partition}, skipping',
              file=sys.stderr)
        return []
    entries = []
    for line in open(path):
        rel = line.strip()
        if rel:
            entries.append((f'/{partition}/{rel}',
                            os.path.join(out, partition, rel)))
    return entries


def bits(path):
    """64, 32, or None if not an ELF."""
    try:
        with open(path, 'rb') as f:
            head = f.read(5)
    except OSError:
        return None
    if head[:4] != b'\x7fELF':
        return None
    return 64 if head[4] == 2 else 32


def dt_needed(path):
    out = subprocess.run(['readelf', '-d', path], capture_output=True, text=True).stdout
    return [l.split('[')[1].split(']')[0]
            for l in out.splitlines() if 'NEEDED' in l and '[' in l]


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.environ.get('ANDROID_PRODUCT_OUT')
    if not out or not os.path.isdir(out):
        sys.exit('usage: check-vendor-deps.py [$OUT]  (or set ANDROID_PRODUCT_OUT)')

    llndk = set()
    llndk_path = os.path.join(out, 'system/etc/llndk.libraries.txt')
    if os.path.exists(llndk_path):
        llndk = {l.strip() for l in open(llndk_path) if l.strip()}
    else:
        print('warning: no llndk.libraries.txt, expect false positives',
              file=sys.stderr)

    # name -> set of bitnesses it is available in
    avail = collections.defaultdict(set)
    for name in llndk | ALWAYS:
        avail[name] = {32, 64}

    consumers = []
    for part in PARTITIONS:
        for name, staged in installed(out, part):
            b = bits(staged)
            if b is None:
                continue
            base = os.path.basename(name)
            if base.endswith('.so'):
                avail[base].add(b)
            consumers.append((name, staged, b))

    missing = collections.defaultdict(list)
    for name, staged, b in consumers:
        for lib in dt_needed(staged):
            if b not in avail.get(lib, ()):
                other = avail.get(lib, set())
                note = f' (only {sorted(other)[0]}-bit available)' if other else ''
                missing[(lib, b)].append((name, note))

    print(f'checked {len(consumers)} installed ELF files across '
          f'{", ".join(PARTITIONS)}')
    if not missing:
        print('all DT_NEEDED libraries resolve')
        return 0

    print(f'\n{len(missing)} unresolved:\n')
    for (lib, b), users in sorted(missing.items(), key=lambda kv: -len(kv[1])):
        note = users[0][1]
        print(f'  {lib}  [{b}-bit]{note}   {len(users)} user(s)')
        for name, _ in sorted(users)[:6]:
            print(f'      {name}')
        if len(users) > 6:
            print(f'      ... and {len(users) - 6} more')
    return 1


if __name__ == '__main__':
    sys.exit(main())
