package org.lineageos.rockchip.settings;

import androidx.fragment.app.Fragment;

public class DockerSettingsActivity extends RkSettingsActivity {

    @Override
    Fragment rootFragment() {
        return new DockerFragment();
    }
}
