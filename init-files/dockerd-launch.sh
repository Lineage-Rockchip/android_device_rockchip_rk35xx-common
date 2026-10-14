#!/vendor/bin/sh

root=/data/docker

case "$1" in
--stop)
    pid=$(cat $root/run/docker.pid 2>/dev/null)
    if [ -n "$pid" ] && [ "$(cat /proc/$pid/comm 2>/dev/null)" = dockerd ]; then
        kill -TERM $pid
        n=0
        while [ -d /proc/$pid ] && [ $n -lt 30 ]; do
            sleep 1
            n=$((n + 1))
        done
    fi
    setprop ctl.stop dockerd
    exit 0
    ;;
--in-ns)
    ;;
*)
    if [ ! -x $root/bin/dockerd ]; then
        setprop ctl.stop dockerd
        exit 0
    fi
    exec /vendor/bin/unshare -m /vendor/bin/sh "$0" --in-ns
    ;;
esac

fail() {
    echo "dockerd-launch: $1"
    setprop ctl.stop dockerd
    exit 1
}

log=$root/dockerd.log
[ -f $log ] && [ $(stat -c %s $log) -gt 10485760 ] && mv -f $log $log.old
exec >> $log 2>&1

mount -o rslave none /

mkdir -p $root/etc $root/tmp $root/lib
[ -s $root/etc/resolv.conf ] || printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > $root/etc/resolv.conf
[ -s $root/etc/nsswitch.conf ] || echo 'hosts: files dns' > $root/etc/nsswitch.conf

# overlayfs rejects casefold-capable filesystems such as /data, so layers live in ext4.
img=$root/docker.img
[ -f $img ] || fail "$img is missing; run docker-media-install.sh"
losetup -j $img | while IFS=: read dev rest; do losetup -d $dev; done
/system/bin/e2fsck -p $img
[ $? -lt 4 ] || fail "e2fsck $img failed"
dev=$(losetup -f -s $img) || fail "losetup $img failed"
mount -t ext4 -o noatime,discard $dev $root/lib || fail "mounting $img failed"

# dockerd hard-codes /run, which Android's read-only root lacks.
newroot=$root/rootfs
mkdir -p $newroot
mount -t tmpfs -o mode=0755 tmpfs $newroot
mount -o unbindable none $newroot
for path in /*; do
    name=${path#/}
    if [ $name = etc ]; then
        mkdir $newroot/etc
        ln -s /system/etc/* $newroot/etc/
        for file in $root/etc/*; do
            rm -f $newroot/etc/${file##*/}
            cp $file $newroot/etc/
        done
    elif [ -L $path ]; then
        ln -s "$(readlink $path)" $newroot/$name
    elif [ -d $path ]; then
        mkdir $newroot/$name
        mount -o rbind $path $newroot/$name
    fi
done
mount -o private none $newroot
mkdir -p $newroot/run $newroot/opt $newroot/var

export PATH=$root/bin:/vendor/bin:/system/bin
export HOME=$root TMPDIR=$root/tmp
export SSL_CERT_DIR=/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts
# setns() resets the root to the top mount on /, so docker exec needs it there.
cd $newroot
mount -o move $newroot / || fail "moving $newroot onto / failed"
exec chroot . $root/bin/dockerd --config-file $root/daemon.json
