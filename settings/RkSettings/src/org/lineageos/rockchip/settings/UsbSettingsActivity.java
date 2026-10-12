package org.lineageos.rockchip.settings;

import androidx.fragment.app.Fragment;

public class UsbSettingsActivity extends RkSettingsActivity {

    @Override
    Fragment rootFragment() {
        return new UsbFragment();
    }
}
