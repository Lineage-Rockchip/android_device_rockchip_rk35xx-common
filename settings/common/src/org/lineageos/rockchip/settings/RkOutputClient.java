package org.lineageos.rockchip.settings;

import android.graphics.Rect;
import android.os.RkDisplayOutputManager;
import android.os.ServiceManager;
import android.os.SystemProperties;

// RK3576 must use the SW setters: with rkpq on, the PQ CSC overrides connector BCSH.
final class RkOutputClient {
    private static final String SERVICE = "drm_device_management";

    static final boolean IS_RK3576 = "rk3576".equals(SystemProperties.get("ro.board.platform"));

    static final int[] DEFAULT_BCSH = {50, 50, 50, 50};

    // DRM connector types and state, as in getConnectorInfo()'s "type:T,id:I,state:S"
    private static final int CONNECTOR_VIRTUAL = 15;
    private static final int CONNECTOR_DSI = 16;
    private static final int CONNECTED = 1;

    private static RkDisplayOutputManager sManager;

    private RkOutputClient() {}

    private static synchronized RkDisplayOutputManager manager() {
        if (sManager == null && ServiceManager.getService(SERVICE) != null) {
            sManager = new RkDisplayOutputManager();
        }
        return sManager;
    }

    // hw_output numbers displays in connector order; use the first connected one.
    static int mainDisplay() {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return 0;
        }
        String[] infos = m.getConnectorInfo();
        int count = m.getDisplayNumber();
        if (infos == null || infos.length != count) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
            int type = -1;
            int state = -1;
            for (String field : infos[i].split(",")) {
                String[] kv = field.trim().split(":");
                if (kv.length != 2) {
                    continue;
                }
                try {
                    if ("type".equals(kv[0])) {
                        type = Integer.parseInt(kv[1].trim());
                    } else if ("state".equals(kv[0])) {
                        state = Integer.parseInt(kv[1].trim());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (state == CONNECTED && type != CONNECTOR_VIRTUAL && type != CONNECTOR_DSI) {
                return i;
            }
        }
        return 0;
    }

    static int[] getBcsh(int dpy) {
        RkDisplayOutputManager m = manager();
        if (m == null) {
            return null;
        }
        if (IS_RK3576) {
            return new int[] {
                m.getSWBrightness(dpy), m.getSWContrast(dpy), m.getSWSaturation(dpy), m.getSWHue(dpy)
            };
        }
        return new int[] {
            m.getBrightness(dpy), m.getContrast(dpy), m.getSaturation(dpy), m.getHue(dpy)
        };
    }

    static void setBrightness(int dpy, int value) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            if (IS_RK3576) m.setSWBrightness(dpy, value); else m.setBrightness(dpy, value);
            m.saveConfig();
        }
    }

    static void setContrast(int dpy, int value) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            if (IS_RK3576) m.setSWContrast(dpy, value); else m.setContrast(dpy, value);
            m.saveConfig();
        }
    }

    static void setSaturation(int dpy, int value) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            if (IS_RK3576) m.setSWSaturation(dpy, value); else m.setSaturation(dpy, value);
            m.saveConfig();
        }
    }

    static void setHue(int dpy, int value) {
        RkDisplayOutputManager m = manager();
        if (m != null) {
            if (IS_RK3576) m.setSWHue(dpy, value); else m.setHue(dpy, value);
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
