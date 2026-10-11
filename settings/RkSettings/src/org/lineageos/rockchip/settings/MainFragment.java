package org.lineageos.rockchip.settings;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

public class MainFragment extends RkPreferenceFragment {

    private Preference mAipq;
    private Preference mPicture;
    private Preference mOutput;
    private Preference mScale;

    @Override
    int titleRes() {
        return R.string.rk_settings_title;
    }

    @Override
    void build(Context c, PreferenceScreen screen) {
        mAipq = addPage(c, screen, "aipq", R.string.aipq_title, AipqFragment.class);
        mPicture = addPage(c, screen, "picture", R.string.disp_title, PictureFragment.class);
        mOutput = addPage(c, screen, "output", R.string.res_title, OutputFragment.class);
        mScale = addPage(c, screen, "scale", R.string.scale_title, ScaleFragment.class);
    }

    private static Preference addPage(Context c, PreferenceScreen screen, String key,
            int titleRes, Class<?> fragment) {
        Preference pref = add(screen, new Preference(c), key);
        pref.setTitle(titleRes);
        pref.setFragment(fragment.getName());
        return pref;
    }

    @Override
    void refresh(Context c) {
        mAipq.setSummary(AipqProps.isMasterOn(c) ? R.string.aipq_on : R.string.aipq_off);

        int dpy = RkOutputClient.mainDisplay();
        int[] bcsh = RkOutputClient.getBcsh(dpy);
        mPicture.setSummary(bcsh == null ? c.getString(R.string.disp_unavailable)
                : bcsh[0] + " · " + bcsh[1] + " · " + bcsh[2] + " · " + bcsh[3]);

        mOutput.setSummary(RkLabels.modeTitle(c, RkOutputClient.getMode(dpy)));

        int[] scale = RkOutputClient.getScale(dpy);
        mScale.setSummary(scale == null ? c.getString(R.string.disp_unavailable)
                : c.getString(R.string.scale_summary, scale[0], scale[1]));
    }
}
