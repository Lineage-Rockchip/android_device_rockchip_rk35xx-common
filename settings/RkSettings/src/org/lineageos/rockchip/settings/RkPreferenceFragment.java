package org.lineageos.rockchip.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.SliderPreference;

/** A page built in code and re-read from the HAL and properties on every resume. */
abstract class RkPreferenceFragment extends SettingsBasePreferenceFragment {

    abstract int titleRes();

    abstract void build(Context c, PreferenceScreen screen);

    abstract void refresh(Context c);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context c = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(c);
        build(c, screen);
        setPreferenceScreen(screen);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(titleRes());
        refresh(getPreferenceManager().getContext());
    }

    interface Apply<T> {
        void apply(T value);
    }

    final void changed() {
        refresh(getPreferenceManager().getContext());
    }

    static <P extends Preference> P add(PreferenceScreen screen, P pref, String key) {
        pref.setKey(key);
        pref.setPersistent(false);
        pref.setIconSpaceReserved(false);
        screen.addPreference(pref);
        return pref;
    }

    final SwitchPreferenceCompat addSwitch(Context c, PreferenceScreen screen, String key,
            int titleRes, int summaryRes, Apply<Boolean> apply) {
        SwitchPreferenceCompat pref = add(screen, new SwitchPreferenceCompat(c), key);
        pref.setTitle(titleRes);
        pref.setSummary(summaryRes);
        pref.setOnPreferenceChangeListener((p, v) -> {
            apply.apply((Boolean) v);
            changed();
            return true;
        });
        return pref;
    }

    final ListPreference addList(Context c, PreferenceScreen screen, String key, int titleRes,
            Apply<String> apply) {
        ListPreference pref = add(screen, new ListPreference(c), key);
        pref.setTitle(titleRes);
        pref.setDialogTitle(titleRes);
        pref.setOnPreferenceChangeListener((p, v) -> {
            apply.apply((String) v);
            changed();
            return false;
        });
        return pref;
    }

    final ListPreference addStrengthList(Context c, PreferenceScreen screen, String key,
            int titleRes, Apply<Integer> apply) {
        ListPreference pref = addList(c, screen, key, titleRes,
                v -> apply.apply(Integer.parseInt(v)));
        CharSequence[] entries = new CharSequence[AipqProps.STRENGTHS.length];
        CharSequence[] values = new CharSequence[AipqProps.STRENGTHS.length];
        for (int i = 0; i < values.length; i++) {
            entries[i] = c.getString(RkLabels.STRENGTH_LABELS[i]);
            values[i] = String.valueOf(AipqProps.STRENGTHS[i]);
        }
        pref.setEntries(entries);
        pref.setEntryValues(values);
        return pref;
    }

    final SliderPreference addSlider(Context c, PreferenceScreen screen, String key,
            int titleRes, int min, int max, int step, Apply<Integer> apply) {
        SliderPreference pref = add(screen, new SliderPreference(c), key);
        pref.setTitle(titleRes);
        pref.setMin(min);
        pref.setMax(max);
        pref.setSliderIncrement(step);
        pref.setShowSliderValue(true);
        pref.setUpdatesContinuously(false);
        pref.setOnPreferenceChangeListener((p, v) -> {
            apply.apply((Integer) v);
            changed();
            return true;
        });
        return pref;
    }

    static FooterPreference addFooter(Context c, PreferenceScreen screen, String key, int res) {
        FooterPreference pref = new FooterPreference(c);
        pref.setKey(key);
        pref.setTitle(res);
        pref.setSelectable(false);
        screen.addPreference(pref);
        return pref;
    }

    // Material's Slider throws on a value off its step grid; HAL values need not be on it.
    static void setSliderValue(SliderPreference pref, int value) {
        int min = pref.getMin();
        int step = pref.getSliderIncrement();
        int v = Math.max(min, Math.min(pref.getMax(), value));
        pref.setValue(min + Math.round((v - min) / (float) step) * step);
    }

    static void setListValue(ListPreference pref, String value, CharSequence summary) {
        pref.setValue(value);
        pref.setSummary(summary);
    }
}
