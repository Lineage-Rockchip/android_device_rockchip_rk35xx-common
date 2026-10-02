#!/usr/bin/env python3
"""Find libraries whose C++ vtable layout drifted away from the stock ROM.

The third checker, after check-vendor-deps.py (does the DT_NEEDED library
exist?) and check-vendor-symbols.py (does every undefined symbol resolve?).
Both of those pass happily on a library that is ABI-incompatible in the one way
that matters most for a blob-based port: the layout of a C++ class.

The blobs on this device were compiled against Android 14 headers. When we
build one of their dependencies from AOSP source instead of extracting it, the
blob keeps calling virtual methods through the *old* slot indices and derives
its own subclasses assuming the *old* object size. If a virtual was inserted
into a base class upstream, every one of those calls now lands on the wrong
function. Nothing detects that at build time -- the symbol names are unchanged,
so check-vendor-symbols.py is silent -- and the failure at runtime is a bare
SIGSEGV or SIGABRT with no message.

The tell is the size of the vtable object itself. `_ZTV<class>` is emitted with
a st_size equal to the number of slots times 8, so comparing st_size between
the stock library and ours detects an insertion or removal without needing any
source. It cannot see a *reordering* that keeps the count, or a change to a
non-virtual data member; treat a clean run as "no evidence of drift", not proof.

Example of what this catches (the LineageOS 23 bring-up, section 7.7):

    lib64  libcppbor_external.so    11 /   11 vtables changed size

cppbor::Item gained two virtuals between Android 14 and 16, so all eleven
vtables grew by 16 bytes, and the OP-TEE KeyMint HAL -- whose support libraries
derive from cppbor::Item -- crashed on its first real generateKey().

Only libraries that an extracted blob actually links are reported. Drift inside
a module we build whole -- audio.bluetooth.default.so against the stock one, say
-- is expected and harmless, because every caller of those classes was built
from the same source.

Usage: check-vendor-vtables.py [OUT_DIR] [STOCK_DIR] [BLOB_DIR]
"""

import os
import re
import subprocess
import sys

OUT_DIR = "/home/tomin/btrfs-subvolumes/android/los-23.2/out/target/product/m9s"
STOCK_DIR = "/home/tomin/devel/AmlogicKitchen/edge-2l/level2"
BLOB_DIR = ("/home/tomin/btrfs-subvolumes/android/los-23.2/"
            "vendor/rockchip/rk35xx-common/proprietary")

# Where to look for a library, in (built subdir, stock subdir) pairs. Stock has
# no VNDK snapshot directories -- Rockchip builds vendor and system together --
# so a library we install to /vendor may live in stock's /system.
SEARCH = (
    ("vendor/lib64", ("vendor/lib64", "system/lib64")),
    ("vendor/lib", ("vendor/lib", "system/lib")),
    ("vendor/lib64/hw", ("vendor/lib64/hw",)),
    ("vendor/lib/hw", ("vendor/lib/hw",)),
)

VTABLE = re.compile(r"^_ZTV")


def vtable_sizes(path):
    """Map _ZTV* symbol name -> st_size for one ELF, or None if unreadable."""
    try:
        out = subprocess.run(
            ["readelf", "--dyn-syms", "-W", path],
            capture_output=True, text=True, check=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    sizes = {}
    for line in out.splitlines():
        f = line.split()
        # Num: Value Size Type Bind Vis Ndx Name
        if len(f) < 8 or not VTABLE.match(f[7]):
            continue
        try:
            sizes[f[7]] = int(f[2])
        except ValueError:
            continue
    return sizes


def blob_consumers(blob_dir):
    """Map library soname -> [blob paths that DT_NEEDED it]."""
    consumers = {}
    for root, _, files in os.walk(blob_dir):
        for name in files:
            path = os.path.join(root, name)
            try:
                out = subprocess.run(
                    ["readelf", "-d", path],
                    capture_output=True, text=True, check=True,
                ).stdout
            except (subprocess.CalledProcessError, FileNotFoundError):
                continue
            rel = os.path.relpath(path, blob_dir)
            for m in re.finditer(r"\(NEEDED\)[^\[]*\[([^\]]+)\]", out):
                consumers.setdefault(m.group(1), []).append(rel)
    return consumers


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else OUT_DIR
    stock_dir = sys.argv[2] if len(sys.argv) > 2 else STOCK_DIR
    blob_dir = sys.argv[3] if len(sys.argv) > 3 else BLOB_DIR

    consumers = blob_consumers(blob_dir)
    findings = []
    for built_sub, stock_subs in SEARCH:
        built_dir = os.path.join(out_dir, built_sub)
        if not os.path.isdir(built_dir):
            continue
        for name in sorted(os.listdir(built_dir)):
            if not name.endswith(".so"):
                continue
            built = os.path.join(built_dir, name)
            stock = next(
                (p for p in (os.path.join(stock_dir, s, name) for s in stock_subs)
                 if os.path.isfile(p)),
                None,
            )
            if stock is None or not os.path.isfile(built):
                continue
            linked_by = consumers.get(name, [])
            if not linked_by:
                continue
            a, b = vtable_sizes(stock), vtable_sizes(built)
            if not a or b is None:
                continue
            common = a.keys() & b.keys()
            # A zero size means the symbol went local or became a reference
            # rather than a definition; that is not a layout comparison.
            changed = sorted(s for s in common
                             if a[s] != b[s] and a[s] and b[s])
            if changed:
                findings.append((built_sub, name, changed, len(a), a, b, linked_by))

    for sub, name, changed, total, a, b, linked_by in findings:
        print(f"{sub:<16} {name:<42} {len(changed):4d} / {total:4d} vtables changed size")
        for s in changed[:3]:
            print(f"{'':16} {s} stock={a[s]} built={b[s]}")
        if len(changed) > 3:
            print(f"{'':16} ... and {len(changed) - 3} more")
        print(f"{'':16} linked by {len(linked_by)} blob(s): {', '.join(sorted(linked_by)[:4])}"
              + (" ..." if len(linked_by) > 4 else ""))

    print(f"\n{len(findings)} blob-linked librar{'y' if len(findings) == 1 else 'ies'}"
          " with vtable drift")
    print("Fix by listing the library in proprietary-files.txt"
          " so it is extracted instead.")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
