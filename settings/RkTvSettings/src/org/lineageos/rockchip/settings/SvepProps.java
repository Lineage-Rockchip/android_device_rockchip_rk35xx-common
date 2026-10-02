package org.lineageos.rockchip.settings;

import android.os.SystemProperties;

// RK3588 SVEP switches, read by the composer every 500 ms.
final class SvepProps {
    private SvepProps() {}

    static final String KNOB_MODE = "svep_mode";
    static final String KNOB_OSD = "svep_osd";

    static final int MODE_OFF = 0;
    static final int MODE_SR = 1;
    static final int MODE_MEMC = 2;

    private static final String P_SR = "persist.sys.svep.mode";
    private static final String P_MEMC = "persist.sys.memc.mode";
    private static final String P_MEMC_SR = "persist.sys.memc.enable_sr";
    private static final String P_SR_OSD_OFF = "persist.sys.svep.disable_sr_osd";
    private static final String P_MEMC_OSD_OFF = "persist.sys.svep.disable_memc_osd";

    static int mode() {
        if (SystemProperties.getInt(P_SR, 0) > 0) {
            return MODE_SR;
        }
        return SystemProperties.getInt(P_MEMC, 0) > 0 ? MODE_MEMC : MODE_OFF;
    }

    // MEMC+SR misses its per-frame budget on RK3588, so it is never enabled.
    static void applyMode(int mode) {
        SystemProperties.set(P_MEMC_SR, "0");
        SystemProperties.set(P_SR, mode == MODE_SR ? "1" : "0");
        SystemProperties.set(P_MEMC, mode == MODE_MEMC ? "1" : "0");
    }

    static boolean isOsdOn() {
        return SystemProperties.getInt(P_SR_OSD_OFF, 0) == 0;
    }

    static void applyOsd(boolean on) {
        String off = on ? "0" : "1";
        SystemProperties.set(P_SR_OSD_OFF, off);
        SystemProperties.set(P_MEMC_OSD_OFF, off);
    }
}
