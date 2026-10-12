package org.lineageos.rockchip.settings;

import android.content.Context;
import android.hardware.hdmi.HdmiControlManager;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;

/** The playback-device CEC settings; TvSettings' Inputs page shows the TV-side ones instead. */
public class CecFragment extends RkPreferenceFragment {

    private static final String[] POWER_MODES = {
            HdmiControlManager.POWER_CONTROL_MODE_TV,
            HdmiControlManager.POWER_CONTROL_MODE_TV_AND_AUDIO_SYSTEM,
            HdmiControlManager.POWER_CONTROL_MODE_BROADCAST,
            HdmiControlManager.POWER_CONTROL_MODE_NONE,
    };
    private static final int[] POWER_LABELS = {
            R.string.cec_power_tv,
            R.string.cec_power_tv_audio,
            R.string.cec_power_broadcast,
            R.string.cec_power_none,
    };

    private HdmiControlManager mHdmi;
    private SwitchPreferenceCompat mEnabled;
    private ListPreference mPower;
    private SwitchPreferenceCompat mSourceLost;
    private SwitchPreferenceCompat mVolume;
    private FooterPreference mUnavailable;

    @Override
    int titleRes() {
        return R.string.cec_title;
    }

    @Override
    void build(Context c, PreferenceScreen screen) {
        mHdmi = c.getSystemService(HdmiControlManager.class);
        mEnabled = addSwitch(c, screen, "cec_enabled", R.string.cec_enabled_title,
                R.string.cec_enabled_summary,
                on -> mHdmi.setHdmiCecEnabled(on
                        ? HdmiControlManager.HDMI_CEC_CONTROL_ENABLED
                        : HdmiControlManager.HDMI_CEC_CONTROL_DISABLED));
        mPower = addList(c, screen, "cec_power", R.string.cec_power_title,
                v -> mHdmi.setPowerControlMode(v));
        CharSequence[] entries = new CharSequence[POWER_MODES.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = c.getString(POWER_LABELS[i]);
        }
        mPower.setEntries(entries);
        mPower.setEntryValues(POWER_MODES);
        mSourceLost = addSwitch(c, screen, "cec_source_lost", R.string.cec_source_lost_title,
                R.string.cec_source_lost_summary,
                on -> mHdmi.setPowerStateChangeOnActiveSourceLost(on
                        ? HdmiControlManager.POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_STANDBY_NOW
                        : HdmiControlManager.POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_NONE));
        mVolume = addSwitch(c, screen, "cec_volume", R.string.cec_volume_title,
                R.string.cec_volume_summary,
                on -> mHdmi.setHdmiCecVolumeControlEnabled(on
                        ? HdmiControlManager.VOLUME_CONTROL_ENABLED
                        : HdmiControlManager.VOLUME_CONTROL_DISABLED));
        mUnavailable = addFooter(c, screen, "cec_unavailable", R.string.cec_unavailable);
    }

    @Override
    void refresh(Context c) {
        boolean available = mHdmi != null;
        mEnabled.setVisible(available);
        mPower.setVisible(available);
        mSourceLost.setVisible(available);
        mVolume.setVisible(available);
        mUnavailable.setVisible(!available);
        if (!available) {
            return;
        }
        boolean on = mHdmi.getHdmiCecEnabled() == HdmiControlManager.HDMI_CEC_CONTROL_ENABLED;
        mEnabled.setChecked(on);
        String mode = mHdmi.getPowerControlMode();
        for (int i = 0; i < POWER_MODES.length; i++) {
            if (POWER_MODES[i].equals(mode)) {
                setListValue(mPower, mode, c.getString(POWER_LABELS[i]));
            }
        }
        mSourceLost.setChecked(mHdmi.getPowerStateChangeOnActiveSourceLost().equals(
                HdmiControlManager.POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_STANDBY_NOW));
        mVolume.setChecked(mHdmi.getHdmiCecVolumeControlEnabled()
                == HdmiControlManager.VOLUME_CONTROL_ENABLED);
        mPower.setEnabled(on);
        mSourceLost.setEnabled(on);
        mVolume.setEnabled(on);
    }
}
