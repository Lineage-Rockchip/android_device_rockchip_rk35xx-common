package org.lineageos.rockchip.settings;

import android.content.Context;
import android.os.SystemProperties;

/**
 * Read/apply semantics of the Rockchip AI-PQ knobs.
 *
 * Sources, in order of authority for this blob set:
 *  - the RKR8 SDK's AiLabFragment source (edge2l-rk/packages/apps/TvSettings):
 *    sr/memc enables use "-1" for on, SR's apply writes the display-path DC
 *    props and the runtime trigger vendor.tvinput.rkpq.update_vdpp_cfg=1;
 *    hwpq_shp_en is a plain 1/0 (the reference board's live value is 1).
 *  - the MS dump's fork (decoded from its dex) for the rows the RKR8 source
 *    does not have (AIPQ master, ACM, DCI, FE, DC) and for the
 *    sculptor.slider demo group.
 *  - hardware observation (logcat13): the MS blobs run the full AI-PQ
 *    pipeline with persist.vendor.sculptor.test.sr unset/0; test.sr=1
 *    bypasses it. The fork's master switch writes 1 for "on" and is
 *    therefore inverted in effect; we write 0 for on.
 */
final class AipqProps {
    private AipqProps() {}

    // Knob identifiers carried on the slice action intents.
    static final String KNOB_MASTER = "master";
    static final String KNOB_SR = "sr";
    static final String KNOB_SR_FORCE = "srforce";
    static final String KNOB_DC = "dc";
    static final String KNOB_MEMC = "memc";
    static final String KNOB_FE = "fe";
    static final String KNOB_ACM = "acm";
    static final String KNOB_DCI = "dci";
    static final String KNOB_SD = "sd";
    static final String KNOB_DEMO = "demo";
    // Display picture knobs, applied over the outputmanager HIDL.
    static final String KNOB_BRIGHTNESS = "brightness";
    static final String KNOB_CONTRAST = "contrast";
    static final String KNOB_SATURATION = "saturation";
    static final String KNOB_HUE = "hue";
    static final String KNOB_RESOLUTION = "resolution";
    static final String KNOB_COLOR = "color";
    static final String KNOB_HDR = "hdr";
    static final String KNOB_SCALE_H = "scale_h";
    static final String KNOB_SCALE_V = "scale_v";

    // Strength values of the Off/Low/Medium/Strong lists.
    static final int[] STRENGTHS = {0, 50, 75, 100};

    private static final String P_TEST_SR = "persist.vendor.sculptor.test.sr";
    private static final String P_ASPECT = "persist.vendor.vpp.aspect_mode";
    private static final String P_SR_EN = "persist.vendor.rkpq.sr.enable";
    private static final String P_SR_STR = "persist.vendor.rkpq.sr.strength";
    private static final String P_DC_EN = "persist.vendor.rkpq.dc.enable";
    private static final String P_DC_STR = "persist.vendor.rkpq.dc.strength";
    private static final String P_SHP_EN = "persist.vendor.rkpq.hwpq_shp_en";
    private static final String P_LCE_RATIO = "persist.vendor.rkpq.hwpq_lce_ratio";
    private static final String P_MEMC_EN = "persist.vendor.rkpq.memc.enable";
    private static final String P_WATERMARK = "persist.vendor.rkpq.memc.watermark";
    private static final String P_SLIDER_EN = "persist.vendor.sculptor.slider.enable";
    private static final String P_SLIDER_DYN = "persist.vendor.sculptor.slider.dynamic";
    private static final String P_UPDATE_VDPP = "vendor.tvinput.rkpq.update_vdpp_cfg";
    private static final String P_FE = "persist.vendor.sculptor.enable.fe";
    private static final String P_ACM_EN = "persist.vendor.rkpq.hwpq_acm_en";
    private static final String P_ACM_DC = "persist.vendor.rkpq.hwpq_dc_acm_en";
    private static final String P_DCI_EN = "persist.vendor.rkpq.hwpq_dci_en";
    private static final String P_DCI_DC = "persist.vendor.rkpq.hwpq_dc_dci_en";
    private static final String P_SD_EN = "persist.vendor.rkpq.hwpq_aisd_enable";
    private static final String P_SD_HW = "persist.vendor.rkhwpq.aisd_enable";

    private static boolean flag(Context c, String prop) {
        return "1".equals(SystemProperties.get(prop, "0"));
    }

    private static void set(Context c, String prop, String value) {
        SystemProperties.set(prop, value);
    }

    private static void set(Context c, String prop, int value) {
        SystemProperties.set(prop, String.valueOf(value));
    }

    private static int strength(Context c, String enProp, String onValue) {
        String en = SystemProperties.get(enProp, "0");
        if (!onValue.equals(en) && !"-1".equals(en)) {
            return 0;
        }
        return Math.max(0, SystemProperties.getInt(
                enProp.equals(P_SR_EN) ? P_SR_STR : P_DC_STR, 100));
    }

    /**
     * Master: the pipeline runs by default; test.sr=1 is the bypass/test
     * path. On = keep test.sr at 0, off = 1. The fork also resets the
     * aspect/demo mode on every master change; harmless, kept.
     */
    static boolean isMasterOn(Context c) {
        return !"1".equals(SystemProperties.get(P_TEST_SR, "0"));
    }

    static void applyMaster(Context c, boolean on) {
        set(c, P_TEST_SR, on ? 0 : 1);
        set(c, P_ASPECT, 0);
    }

    static int srStrength(Context c) {
        return strength(c, P_SR_EN, "1");
    }

    /** SR: -1 auto (skips 60 fps video), 1 forced, 0 off; strength kept on off. */
    static void applySr(Context c, int value) {
        if (value > 0) {
            set(c, P_SR_EN, isSrForced(c) ? 1 : -1);
            set(c, P_SR_STR, value);
        } else {
            set(c, P_SR_EN, 0);
        }
        set(c, P_UPDATE_VDPP, 1);
    }

    static boolean isSrForced(Context c) {
        return flag(c, P_SR_EN);
    }

    static void applySrForce(Context c, boolean on) {
        set(c, P_SR_EN, on ? 1 : -1);
        set(c, P_UPDATE_VDPP, 1);
    }

    static int dcStrength(Context c) {
        return strength(c, P_DC_EN, "1");
    }

    /** DC drives the hwpq sharpness pair; shp is a plain 1/0 here. */
    static void applyDc(Context c, int value) {
        set(c, P_DC_EN, value > 0 ? 1 : 0);
        if (value > 0) {
            set(c, P_DC_STR, value);
        }
        if (RkOutputClient.IS_RK3576) {
            set(c, P_SHP_EN, value > 0 ? 1 : 0);
            set(c, P_LCE_RATIO, value > 0 ? 10 : 0);
        }
        set(c, P_UPDATE_VDPP, 1);
    }

    /** MEMC: on is the blobs' -1, off is 0; applies from the next video. */
    static boolean isMemcOn(Context c) {
        return !"0".equals(SystemProperties.get(P_MEMC_EN, "-1"));
    }

    static void applyMemc(Context c, boolean on) {
        set(c, P_MEMC_EN, on ? -1 : 0);
        set(c, P_UPDATE_VDPP, 1);
    }

    static boolean isFeOn(Context c) {
        return flag(c, P_FE);
    }

    static void applyFe(Context c, boolean on) {
        set(c, P_FE, on ? 1 : 0);
    }

    static boolean isAcmOn(Context c) {
        return flag(c, P_ACM_EN);
    }

    static void applyAcm(Context c, boolean on) {
        set(c, P_ACM_EN, on ? 1 : 0);
        set(c, P_ACM_DC, on ? 1 : 0);
    }

    static boolean isDciOn(Context c) {
        return flag(c, P_DCI_EN);
    }

    static void applyDci(Context c, boolean on) {
        set(c, P_DCI_EN, on ? 1 : 0);
        set(c, P_DCI_DC, on ? 1 : 0);
    }

    /** Unset runs SD (RK3588 leaves it unset); hw_output writes -1 for on. */
    static boolean isSdOn(Context c) {
        return !"0".equals(SystemProperties.get(P_SD_EN, "1"));
    }

    static void applySd(Context c, boolean on) {
        set(c, P_SD_EN, on ? 1 : 0);
        if (RkOutputClient.IS_RK3576) {
            set(c, P_SD_HW, on ? 1 : 0);
        }
    }

    /**
     * Demo compare group: 0 off, 1 split compare, 2 split dynamic,
     * 3 watermark only (the MEMC motion overlay without the split).
     * Watermark and the slider group are independent inputs -- watermark is
     * read by the codec side ([MEMC_getEnvConfig]), the slider pair by
     * libsculptor -- so all four combinations are valid states.
     */
    static int demoMode(Context c) {
        boolean wm = flag(c, P_WATERMARK);
        boolean sl = flag(c, P_SLIDER_EN);
        if (wm && !sl) {
            return 3;
        }
        if (!wm && !sl) {
            return 0;
        }
        return flag(c, P_SLIDER_DYN) ? 2 : 1;
    }

    static void applyDemo(Context c, int mode) {
        set(c, P_WATERMARK, mode > 0 ? 1 : 0);
        set(c, P_SLIDER_EN, mode == 1 || mode == 2 ? 1 : 0);
        set(c, P_SLIDER_DYN, mode == 2 ? 1 : 0);
    }
}
