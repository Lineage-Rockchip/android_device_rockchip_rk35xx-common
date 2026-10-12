package org.lineageos.rockchip.settings;

import androidx.fragment.app.Fragment;

public class CecSettingsActivity extends RkSettingsActivity {

    @Override
    Fragment rootFragment() {
        return new CecFragment();
    }
}
