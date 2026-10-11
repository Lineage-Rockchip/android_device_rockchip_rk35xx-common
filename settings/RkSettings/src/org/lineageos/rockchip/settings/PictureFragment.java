package org.lineageos.rockchip.settings;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SliderPreference;

public class PictureFragment extends RkPreferenceFragment {

    private static final int[] TITLES = {R.string.disp_brightness, R.string.disp_contrast,
            R.string.disp_saturation, R.string.disp_hue};
    private static final String[] KEYS = {AipqProps.KNOB_BRIGHTNESS, AipqProps.KNOB_CONTRAST,
            AipqProps.KNOB_SATURATION, AipqProps.KNOB_HUE};

    private final SliderPreference[] mSliders = new SliderPreference[KEYS.length];
    private Preference mReset;
    private FooterPreference mUnavailable;

    @Override
    int titleRes() {
        return R.string.disp_title;
    }

    @Override
    void build(Context c, PreferenceScreen screen) {
        for (int i = 0; i < KEYS.length; i++) {
            final int index = i;
            mSliders[i] = addSlider(c, screen, "disp_" + KEYS[i], TITLES[i], 0, 100, 1,
                    v -> set(index, v));
        }
        mReset = add(screen, new Preference(c), "disp_reset");
        mReset.setTitle(R.string.disp_reset);
        mReset.setOnPreferenceClickListener(p -> {
            for (int i = 0; i < KEYS.length; i++) {
                set(i, RkOutputClient.DEFAULT_BCSH[i]);
            }
            changed();
            return true;
        });
        mUnavailable = addFooter(c, screen, "disp_unavailable", R.string.disp_unavailable);
    }

    private static void set(int index, int value) {
        int dpy = RkOutputClient.mainDisplay();
        switch (index) {
            case 0 -> RkOutputClient.setBrightness(dpy, value);
            case 1 -> RkOutputClient.setContrast(dpy, value);
            case 2 -> RkOutputClient.setSaturation(dpy, value);
            default -> RkOutputClient.setHue(dpy, value);
        }
    }

    @Override
    void refresh(Context c) {
        int[] bcsh = RkOutputClient.getBcsh(RkOutputClient.mainDisplay());
        for (int i = 0; i < mSliders.length; i++) {
            mSliders[i].setEnabled(bcsh != null);
            setSliderValue(mSliders[i],
                    bcsh != null ? bcsh[i] : RkOutputClient.DEFAULT_BCSH[i]);
        }
        mReset.setEnabled(bcsh != null);
        mUnavailable.setVisible(bcsh == null);
    }
}
