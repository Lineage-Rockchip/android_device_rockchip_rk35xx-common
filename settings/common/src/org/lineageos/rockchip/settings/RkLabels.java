package org.lineageos.rockchip.settings;

import android.content.Context;

final class RkLabels {
    private RkLabels() {}

    static final int[] STRENGTH_LABELS = {R.string.aipq_strength_off,
            R.string.aipq_strength_low, R.string.aipq_strength_medium,
            R.string.aipq_strength_strong};
    static final int[] DEMO_LABELS = {R.string.aipq_strength_off,
            R.string.aipq_demo_on, R.string.aipq_demo_dynamic, R.string.aipq_demo_watermark};

    static String strengthLabel(Context c, int value) {
        for (int i = 0; i < AipqProps.STRENGTHS.length; i++) {
            if (AipqProps.STRENGTHS[i] == value) {
                return c.getString(STRENGTH_LABELS[i]);
            }
        }
        return value + "%";
    }

    static String modeTitle(Context c, String mode) {
        if (mode == null) {
            return "";
        }
        return RkOutputClient.MODE_AUTO.equals(mode) ? c.getString(R.string.res_auto)
                : RkOutputClient.modeLabel(mode);
    }

    static String formatTitle(Context c, String format) {
        if (format == null) {
            return "";
        }
        return RkOutputClient.MODE_AUTO.equals(format) ? c.getString(R.string.res_auto) : format;
    }
}
