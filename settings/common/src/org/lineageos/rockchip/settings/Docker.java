package org.lineageos.rockchip.settings;

import android.os.SystemProperties;

/** Docker engine on/off; init.docker.rc starts or stops dockerd on the property. */
final class Docker {
    private Docker() {}

    static final String KNOB_ENABLE = "docker";

    private static final String P_ENABLE = "persist.vendor.docker.enable";

    static boolean isEnabled() {
        return SystemProperties.getBoolean(P_ENABLE, false);
    }

    static void setEnabled(boolean enabled) {
        SystemProperties.set(P_ENABLE, enabled ? "1" : "0");
    }
}
