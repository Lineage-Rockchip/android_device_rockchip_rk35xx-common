package org.lineageos.rk3576.aipq;

import android.graphics.Rect;
import android.os.RkDisplayOutputManager;
import android.os.ServiceManager;

// RK3576 must use the SW setters: with rkpq on, the PQ CSC overrides connector BCSH.
final class RkOutputClient {
    private static final String SERVICE = "drm_device_management";

    static final int DISPLAY_MAIN = 0;
    static final int[] DEFAULT_BCSH = {50, 50, 50, 50};

    private static RkDisplayOutputManager sManager;

    private RkOutputClient() {}

    private static synchronized RkDisplayOutputManager manager() {
        if (sManager == null && ServiceManager.getService(SERVICE) != null) {
            sManager = new RkDisplayOutputManager();
        }
        return sManager;
    }

    static int[] getBcsh(int dpy) {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return null;
        }
        return new int[] {
            m.getSWBrightness(dpy), m.getSWContrast(dpy), m.getSWSaturation(dpy), m.getSWHue(dpy)
        };
    }

    static void setBrightness(int dpy, int value) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            m.setSWBrightness(dpy, value);
            m.saveConfig();
        }
    }

    static void setContrast(int dpy, int value) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            m.setSWContrast(dpy, value);
            m.saveConfig();
        }
    }

    static void setSaturation(int dpy, int value) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            m.setSWSaturation(dpy, value);
            m.saveConfig();
        }
    }

    static void setHue(int dpy, int value) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            m.setSWHue(dpy, value);
            m.saveConfig();
        }
    }

    static final String MODE_AUTO = "Auto";

    // hw_output's hw_types.h codes, not RkDisplayOutputManager.DISPLAY_OVERSCAN_*
    private static final int OVERSCAN_LEFT = 0;
    private static final int OVERSCAN_TOP = 1;
    private static final int OVERSCAN_RIGHT = 2;
    private static final int OVERSCAN_BOTTOM = 3;

    static String[] getModes(int dpy) {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return null;
        }
        return m.getModeList(dpy, m.getCurrentInterface(dpy));
    }

    static String getMode(int dpy) {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return null;
        }
        return m.getCurrentMode(dpy, m.getCurrentInterface(dpy));
    }

    static void setMode(int dpy, String mode) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            m.setMode(dpy, m.getCurrentInterface(dpy), mode);
        }
    }

    static void saveConfig() {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            m.saveConfig();
        }
    }

    static String[] getColorModes(int dpy) {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return null;
        }
        return m.getSupportCorlorList(dpy, m.getCurrentInterface(dpy));
    }

    static String getColorMode(int dpy) {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return null;
        }
        return m.getCurrentColorMode(dpy, m.getCurrentInterface(dpy));
    }

    static void setColorMode(int dpy, String format) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            m.setColorMode(dpy, m.getCurrentInterface(dpy), format);
        }
    }

    static boolean isHdrEnabled() {
        RkDisplayOutputManager m = manager();
        return m == null || m.isHDR10Status();
    }

    static void setHdrEnabled(boolean enabled) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            m.setHDR10Enabled(enabled);
        }
    }

    // {horizontal, vertical} percent; the HAL keeps left == right and top == bottom
    static int[] getScale(int dpy) {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return null;
        }
        Rect r = m.getOverScan(dpy);
        return new int[] {r.left, r.top};
    }

    static void setScale(int dpy, boolean horizontal, int value) {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return;
        }
        if (horizontal) {
            m.setOverScan(dpy, OVERSCAN_LEFT, value);
            m.setOverScan(dpy, OVERSCAN_RIGHT, value);
        } else {
            m.setOverScan(dpy, OVERSCAN_TOP, value);
            m.setOverScan(dpy, OVERSCAN_BOTTOM, value);
        }
        m.saveConfig();
    }

    // "2560x1440p59.95-3" -> "2560x1440p59.95"
    static String modeLabel(String mode) {
        int dash = mode.indexOf('-');
        return dash > 0 ? mode.substring(0, dash) : mode;
    }
}
