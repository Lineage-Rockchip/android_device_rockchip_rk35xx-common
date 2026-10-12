package org.lineageos.rockchip.settings;

import android.os.SystemProperties;

/** OTG/host role of the USB 3.0 port; init writes the property to the PHY's otg_mode. */
final class UsbMode {
    private UsbMode() {}

    static final String KNOB_HOST = "usbhost";

    private static final String P_MODE = "persist.vendor.usb.otg_mode";
    private static final String HOST = "host";
    private static final String OTG = "otg";

    static boolean isHost() {
        return HOST.equals(SystemProperties.get(P_MODE, OTG));
    }

    static void setHost(boolean host) {
        SystemProperties.set(P_MODE, host ? HOST : OTG);
    }
}
