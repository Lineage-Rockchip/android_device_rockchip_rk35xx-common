package org.lineageos.rockchip.settings;

import android.content.Context;

import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

public class UsbFragment extends RkPreferenceFragment {

    private SwitchPreferenceCompat mHost;

    @Override
    int titleRes() {
        return R.string.usb_title;
    }

    @Override
    void build(Context c, PreferenceScreen screen) {
        mHost = addSwitch(c, screen, UsbMode.KNOB_HOST, R.string.usb_host_title,
                R.string.usb_host_summary, UsbMode::setHost);
    }

    @Override
    void refresh(Context c) {
        mHost.setChecked(UsbMode.isHost());
    }
}
