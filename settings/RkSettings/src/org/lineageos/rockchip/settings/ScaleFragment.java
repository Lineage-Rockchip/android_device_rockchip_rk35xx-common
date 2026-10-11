package org.lineageos.rockchip.settings;

import android.content.Context;

import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SliderPreference;

public class ScaleFragment extends RkPreferenceFragment {

    // Rockchip's ScreenScaleActivity range
    private static final int SCALE_MIN = 80;
    private static final int SCALE_STEP = 2;

    private SliderPreference mHorizontal;
    private SliderPreference mVertical;
    private FooterPreference mUnavailable;

    @Override
    int titleRes() {
        return R.string.scale_title;
    }

    @Override
    void build(Context c, PreferenceScreen screen) {
        mHorizontal = addSlider(c, screen, AipqProps.KNOB_SCALE_H, R.string.scale_horizontal,
                SCALE_MIN, 100, SCALE_STEP,
                v -> RkOutputClient.setScale(RkOutputClient.mainDisplay(), true, v));
        mVertical = addSlider(c, screen, AipqProps.KNOB_SCALE_V, R.string.scale_vertical,
                SCALE_MIN, 100, SCALE_STEP,
                v -> RkOutputClient.setScale(RkOutputClient.mainDisplay(), false, v));
        mUnavailable = addFooter(c, screen, "scale_unavailable", R.string.disp_unavailable);
    }

    @Override
    void refresh(Context c) {
        int[] scale = RkOutputClient.getScale(RkOutputClient.mainDisplay());
        mHorizontal.setEnabled(scale != null);
        mVertical.setEnabled(scale != null);
        if (scale != null) {
            setSliderValue(mHorizontal, scale[0]);
            setSliderValue(mVertical, scale[1]);
        }
        mUnavailable.setVisible(scale == null);
    }
}
