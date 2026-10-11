package org.lineageos.rockchip.settings;

import android.content.Context;
import android.content.Intent;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;

public class OutputFragment extends RkPreferenceFragment {

    private ListPreference mMode;
    private ListPreference mColor;
    private SwitchPreferenceCompat mHdr;
    private FooterPreference mUnavailable;

    @Override
    int titleRes() {
        return R.string.res_title;
    }

    @Override
    void build(Context c, PreferenceScreen screen) {
        mMode = addList(c, screen, "res", R.string.res_header,
                v -> apply(c, ResolutionConfirmActivity.KIND_MODE, v));
        mColor = addList(c, screen, "color", R.string.color_header,
                v -> apply(c, ResolutionConfirmActivity.KIND_COLOR, v));
        mHdr = addSwitch(c, screen, "hdr", R.string.hdr_title, R.string.hdr_summary,
                RkOutputClient::setHdrEnabled);
        mUnavailable = addFooter(c, screen, "res_unavailable", R.string.disp_unavailable);
    }

    private static void apply(Context c, String kind, String value) {
        int dpy = RkOutputClient.mainDisplay();
        boolean color = ResolutionConfirmActivity.KIND_COLOR.equals(kind);
        String previous = color ? RkOutputClient.getColorMode(dpy) : RkOutputClient.getMode(dpy);
        if (value == null || value.equals(previous)) {
            return;
        }
        if (color) {
            RkOutputClient.setColorMode(dpy, value);
        } else {
            RkOutputClient.setMode(dpy, value);
        }
        c.startActivity(new Intent(c, ResolutionConfirmActivity.class)
                .putExtra(ResolutionConfirmActivity.EXTRA_KIND, kind)
                .putExtra(ResolutionConfirmActivity.EXTRA_MODE, value)
                .putExtra(ResolutionConfirmActivity.EXTRA_PREVIOUS, previous));
    }

    @Override
    void refresh(Context c) {
        int dpy = RkOutputClient.mainDisplay();
        String[] modes = RkOutputClient.getModes(dpy);
        boolean available = modes != null && modes.length > 0;
        mMode.setVisible(available);
        if (available) {
            CharSequence[] entries = new CharSequence[modes.length];
            for (int i = 0; i < modes.length; i++) {
                entries[i] = RkLabels.modeTitle(c, modes[i]);
            }
            mMode.setEntries(entries);
            mMode.setEntryValues(modes);
            String mode = RkOutputClient.getMode(dpy);
            setListValue(mMode, mode, RkLabels.modeTitle(c, mode));
        }

        String[] formats = RkOutputClient.getColorModes(dpy);
        mColor.setVisible(formats != null && formats.length > 0);
        if (mColor.isVisible()) {
            CharSequence[] entries = new CharSequence[formats.length];
            for (int i = 0; i < formats.length; i++) {
                entries[i] = RkLabels.formatTitle(c, formats[i]);
            }
            mColor.setEntries(entries);
            mColor.setEntryValues(formats);
            String format = RkOutputClient.getColorMode(dpy);
            setListValue(mColor, format, RkLabels.formatTitle(c, format));
        }

        mHdr.setChecked(RkOutputClient.isHdrEnabled());
        mUnavailable.setVisible(!available);
    }
}
