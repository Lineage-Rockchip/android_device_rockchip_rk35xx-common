package org.lineageos.rockchip.settings;

import android.content.Context;

import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

public class DockerFragment extends RkPreferenceFragment {

    private SwitchPreferenceCompat mEnable;

    @Override
    int titleRes() {
        return R.string.docker_title;
    }

    @Override
    void build(Context c, PreferenceScreen screen) {
        mEnable = addSwitch(c, screen, Docker.KNOB_ENABLE, R.string.docker_enable_title,
                R.string.docker_enable_summary, Docker::setEnabled);
    }

    @Override
    void refresh(Context c) {
        mEnable.setChecked(Docker.isEnabled());
    }
}
