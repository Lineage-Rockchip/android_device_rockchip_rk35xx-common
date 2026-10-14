#!/usr/bin/env bash
#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
# Installs the Docker engine on an rk35xx device over adb and starts
# Jellyfin, Sonarr, Prowlarr and qBittorrent in it. Re-running it upgrades everything in place.
#
# Usage: docker-media-install.sh [--uninstall]
#
# Environment overrides:
#   ANDROID_SERIAL   which device adb talks to
#   DOCKER_VERSION   docker static release, e.g. 28.5.1 (default: latest)
#   MEDIA_DIR        device path mounted as /media (default: /data/media/0)
#   MEDIA_UID        uid/gid Sonarr and qBittorrent write files as
#                    (default: 1023, media_rw)
#   DNS              space-separated nameservers for dockerd and containers
#   DISK_GB          size of the sparse image holding images and containers
#                    (default: 80% of the free space on /data; only on creation)
#   WITH_JELLYFIN    0 to skip Jellyfin
#   WITH_SONARR      0 to skip Sonarr
#   WITH_PROWLARR    0 to skip Prowlarr
#   WITH_QBITTORRENT 0 to skip qBittorrent

set -euo pipefail

ROOT=/data/docker
MEDIA_DIR=${MEDIA_DIR:-/data/media/0}
MEDIA_UID=${MEDIA_UID:-1023}
WITH_JELLYFIN=${WITH_JELLYFIN:-1}
WITH_SONARR=${WITH_SONARR:-1}
WITH_PROWLARR=${WITH_PROWLARR:-1}
WITH_QBITTORRENT=${WITH_QBITTORRENT:-1}
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/docker-media-install
INDEX=https://download.docker.com/linux/static/stable/aarch64/

die() { echo "error: $*" >&2; exit 1; }
say() { echo "==> $*"; }
dsh() { adb shell "$@"; }

stop_dockerd() {
    say "Stopping dockerd"
    dsh setprop persist.vendor.docker.enable 0
    for _ in $(seq 40); do
        dsh "pidof dockerd >/dev/null || [ \"\$(getprop init.svc.dockerd-stop)\" = running ]" || return 0
        sleep 1
    done
    die "dockerd did not stop"
}

for tool in adb curl tar; do
    command -v $tool >/dev/null || die "$tool not found"
done

adb root >/dev/null 2>&1 || true
adb wait-for-device
[ "$(dsh id -u | tr -d '\r')" = 0 ] || die "adb is not root; this needs a userdebug build"

if [ "${1:-}" = "--uninstall" ]; then
    stop_dockerd
    say "Deleting $ROOT"
    dsh "rm -rf $ROOT/* $ROOT/.[!.]*"
    exit 0
fi

dsh "[ -x /vendor/bin/dockerd-launch.sh ]" || die "this build has no dockerd service; flash a newer one"
dsh "[ -e /proc/self/ns/pid ] && [ -e /proc/self/ns/ipc ]" \
    || die "the kernel lacks PID/IPC namespaces (rk35xx_docker.config)"
dsh "[ -d $ROOT ]" || die "$ROOT is missing; it is created at boot by init.docker.rc"

if [ -z "${DOCKER_VERSION:-}" ]; then
    DOCKER_VERSION=$(curl -fsSL "$INDEX" | grep -o 'docker-[0-9][0-9.]*\.tgz' \
        | sed 's/^docker-//; s/\.tgz$//' | sort -uV | tail -n1)
    [ -n "$DOCKER_VERSION" ] || die "cannot find the latest docker release at $INDEX"
fi

tgz=$CACHE/docker-$DOCKER_VERSION.tgz
if [ ! -s "$tgz" ]; then
    say "Downloading docker $DOCKER_VERSION"
    mkdir -p "$CACHE"
    curl -fL --progress-bar -o "$tgz.part" "${INDEX}docker-$DOCKER_VERSION.tgz"
    mv "$tgz.part" "$tgz"
fi

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
tar -xzf "$tgz" -C "$stage"

if [ -n "${DNS:-}" ]; then
    for ns in $DNS; do echo "nameserver $ns"; done > "$stage/resolv.conf"
fi

cat > "$stage/daemon.json" <<EOF
{
    "data-root": "$ROOT/lib",
    "exec-root": "$ROOT/run",
    "pidfile": "$ROOT/run/docker.pid",
    "hosts": ["unix://$ROOT/run/docker.sock"],
    "storage-driver": "overlay2",
    "bridge": "none",
    "iptables": false,
    "ip6tables": false,
    "ip-forward": false,
    "ip-masq": false,
    "userland-proxy": false,
    "log-driver": "local"
}
EOF

cat > "$stage/docker-cli" <<EOF
#!/system/bin/sh
export DOCKER_HOST=unix://$ROOT/run/docker.sock DOCKER_CONFIG=$ROOT/cli
exec $ROOT/bin/docker "\$@"
EOF

stop_dockerd

say "Installing docker $DOCKER_VERSION into $ROOT"
dsh "mkdir -p $ROOT/bin $ROOT/etc $ROOT/cli $ROOT/volumes"

if ! dsh "[ -f $ROOT/docker.img ]"; then
    if [ -z "${DISK_GB:-}" ]; then
        free_kb=$(dsh "df -k /data" | awk 'NR == 2 { print $4 }')
        DISK_GB=$((free_kb * 8 / 10 / 1024 / 1024))
    fi
    [ "$DISK_GB" -ge 4 ] || die "not enough free space on /data for the image (${DISK_GB}G)"
    say "Creating a ${DISK_GB}G sparse ext4 image for images and containers"
    dsh "rm -rf $ROOT/lib && truncate -s ${DISK_GB}G $ROOT/docker.img && mke2fs -q -t ext4 -m 0 $ROOT/docker.img"
fi
adb push -q "$stage"/docker/* $ROOT/bin/
adb push -q "$stage/daemon.json" $ROOT/daemon.json
adb push -q "$stage/docker-cli" $ROOT/docker
[ -f "$stage/resolv.conf" ] && adb push -q "$stage/resolv.conf" $ROOT/etc/resolv.conf
dsh "chmod 0755 $ROOT/bin/* $ROOT/docker"

say "Starting dockerd"
dsh setprop persist.vendor.docker.enable 1
for i in $(seq 60); do
    dsh "$ROOT/docker version >/dev/null 2>&1" && break
    [ "$i" = 60 ] && die "dockerd did not come up; see adb shell cat $ROOT/dockerd.log"
    sleep 1
done

tz=$(dsh getprop persist.sys.timezone | tr -d '\r')
tz=${tz:-UTC}

storage="-v $MEDIA_DIR:/media -v /mnt/media_rw:/mnt/usb:rslave"

run() {
    local name=$1; shift
    local image=${@: -1}
    say "Starting $name ($image)"
    dsh "$ROOT/docker pull $image"
    dsh "$ROOT/docker rm -f $name >/dev/null 2>&1; mkdir -p $ROOT/volumes/$name"
    dsh "$ROOT/docker run -d --name $name --restart unless-stopped --network host -e TZ=$tz $*"
}

if [ "$WITH_JELLYFIN" = 1 ]; then
    devices=$(dsh 'for d in /dev/dri /dev/dma_heap /dev/mpp_service /dev/rga /dev/mali0; do
        [ -e $d ] && echo -n "--device $d "; done' | tr -d '\r')
    # MPP identifies the SoC from /proc/device-tree, which Docker masks by default.
    run jellyfin \
        --security-opt systempaths=unconfined \
        -v $ROOT/volumes/jellyfin/config:/config \
        -v $ROOT/volumes/jellyfin/cache:/cache \
        $storage $devices \
        jellyfin/jellyfin:latest
fi

if [ "$WITH_SONARR" = 1 ]; then
    run sonarr \
        -e PUID=$MEDIA_UID -e PGID=$MEDIA_UID -e UMASK=002 \
        -v $ROOT/volumes/sonarr:/config \
        $storage \
        lscr.io/linuxserver/sonarr:latest
fi

if [ "$WITH_PROWLARR" = 1 ]; then
    run prowlarr \
        -e PUID=$MEDIA_UID -e PGID=$MEDIA_UID \
        -v $ROOT/volumes/prowlarr:/config \
        lscr.io/linuxserver/prowlarr:latest
fi

if [ "$WITH_QBITTORRENT" = 1 ]; then
    qbt_conf=$ROOT/volumes/qbittorrent/qBittorrent/qBittorrent.conf
    qbt_fresh=0
    dsh "[ -f $qbt_conf ]" || qbt_fresh=1
    dsh "mkdir -p $MEDIA_DIR/Download"
    run qbittorrent \
        -e PUID=$MEDIA_UID -e PGID=$MEDIA_UID -e UMASK=002 \
        -e WEBUI_PORT=8080 -e TORRENTING_PORT=6881 \
        -v $ROOT/volumes/qbittorrent:/config \
        $storage \
        lscr.io/linuxserver/qbittorrent:latest
    if [ $qbt_fresh = 1 ]; then
        for _ in $(seq 30); do
            dsh "[ -f $qbt_conf ]" && break
            sleep 1
        done
        dsh "$ROOT/docker stop qbittorrent >/dev/null && sed -i 's|/downloads/|/media/Download/|g' $qbt_conf && $ROOT/docker start qbittorrent >/dev/null"
        dsh "grep -q /media/Download/ $qbt_conf" \
            || echo "warning: set qBittorrent's save path to /media/Download/ by hand" >&2
    fi
    qbt_password=
    for _ in $(seq 20); do
        passwords=$(dsh "$ROOT/docker logs qbittorrent 2>&1" | tr -d '\r' \
            | sed -n 's/.*temporary password is provided for this session: *//p')
        if [ "$(echo "$passwords" | grep -c .)" -gt $qbt_fresh ]; then
            qbt_password=$(echo "$passwords" | tail -n1)
            break
        fi
        sleep 1
    done
fi

dsh "$ROOT/docker image prune -f >/dev/null"

ip=$(dsh "ip -4 -o addr show scope global" | awk '{sub("/.*", "", $4); print $4; exit}')
ip=${ip:-<device-ip>}
echo
[ "$WITH_JELLYFIN" = 1 ] && echo "Jellyfin: http://$ip:8096"
[ "$WITH_SONARR" = 1 ] && echo "Sonarr:   http://$ip:8989"
[ "$WITH_PROWLARR" = 1 ] && echo "Prowlarr: http://$ip:9696"
if [ "$WITH_QBITTORRENT" = 1 ]; then
    echo "qBittorrent: http://$ip:8080 (user admin, password ${qbt_password:-already set})"
    echo "             downloads go to /media/Download, the Android Download folder"
fi
echo "Media:    $MEDIA_DIR is /media, USB drives are under /mnt/usb"
echo "CLI:      adb shell $ROOT/docker ps"
