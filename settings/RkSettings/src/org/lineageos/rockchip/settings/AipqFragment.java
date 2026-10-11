package org.lineageos.rockchip.settings;

import android.content.Context;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;

public class AipqFragment extends RkPreferenceFragment {

    private MainSwitchPreference mMaster;
    private ListPreference mSr;
    private SwitchPreferenceCompat mSrForce;
    private ListPreference mDc;
    private SwitchPreferenceCompat mMemc;
    private SwitchPreferenceCompat mFe;
    private SwitchPreferenceCompat mAcm;
    private SwitchPreferenceCompat mDci;
    private SwitchPreferenceCompat mSd;
    private ListPreference mDemo;

    @Override
    int titleRes() {
        return R.string.aipq_title;
    }

    @Override
    void build(Context c, PreferenceScreen screen) {
        mMaster = add(screen, new MainSwitchPreference(c), "aipq_master");
        mMaster.setTitle(R.string.aipq_master_title);
        mMaster.setOnPreferenceChangeListener((p, v) -> {
            AipqProps.applyMaster(c, (Boolean) v);
            changed();
            return true;
        });

        mSr = addStrengthList(c, screen, "aipq_sr", R.string.aipq_sr_title,
                v -> AipqProps.applySr(c, v));
        if (RkOutputClient.IS_RK3576) {
            mSrForce = addSwitch(c, screen, "aipq_srforce", R.string.aipq_sr_force_title,
                    R.string.aipq_sr_force_summary, v -> AipqProps.applySrForce(c, v));
        }
        mDc = addStrengthList(c, screen, "aipq_dc", R.string.aipq_dc_title,
                v -> AipqProps.applyDc(c, v));
        mMemc = addSwitch(c, screen, "aipq_memc", R.string.aipq_memc_title,
                R.string.aipq_memc_summary, v -> AipqProps.applyMemc(c, v));
        mFe = addSwitch(c, screen, "aipq_fe", R.string.aipq_fe_title,
                R.string.aipq_fe_summary, v -> AipqProps.applyFe(c, v));
        if (RkOutputClient.IS_RK3576) {
            mAcm = addSwitch(c, screen, "aipq_acm", R.string.aipq_acm_title,
                    R.string.aipq_acm_summary, v -> AipqProps.applyAcm(c, v));
            mDci = addSwitch(c, screen, "aipq_dci", R.string.aipq_dci_title,
                    R.string.aipq_dci_summary, v -> AipqProps.applyDci(c, v));
        }
        mSd = addSwitch(c, screen, "aipq_sd", R.string.aipq_sd_title,
                R.string.aipq_sd_summary, v -> AipqProps.applySd(c, v));

        mDemo = addList(c, screen, "aipq_demo", R.string.aipq_demo_title,
                v -> AipqProps.applyDemo(c, Integer.parseInt(v)));
        CharSequence[] entries = new CharSequence[RkLabels.DEMO_LABELS.length];
        CharSequence[] values = new CharSequence[RkLabels.DEMO_LABELS.length];
        for (int i = 0; i < values.length; i++) {
            entries[i] = c.getString(RkLabels.DEMO_LABELS[i]);
            values[i] = String.valueOf(i);
        }
        mDemo.setEntries(entries);
        mDemo.setEntryValues(values);

        addFooter(c, screen, "aipq_note", R.string.aipq_note);
    }

    @Override
    void refresh(Context c) {
        mMaster.setChecked(AipqProps.isMasterOn(c));

        int sr = AipqProps.srStrength(c);
        setListValue(mSr, String.valueOf(sr), c.getString(R.string.aipq_sr_summary)
                + "\n" + RkLabels.strengthLabel(c, sr));
        if (mSrForce != null) {
            mSrForce.setChecked(AipqProps.isSrForced(c));
            mSrForce.setEnabled(sr > 0);
        }
        int dc = AipqProps.dcStrength(c);
        setListValue(mDc, String.valueOf(dc), c.getString(R.string.aipq_dc_summary)
                + "\n" + RkLabels.strengthLabel(c, dc));
        mMemc.setChecked(AipqProps.isMemcOn(c));
        mFe.setChecked(AipqProps.isFeOn(c));
        if (mAcm != null) {
            mAcm.setChecked(AipqProps.isAcmOn(c));
            mDci.setChecked(AipqProps.isDciOn(c));
        }
        mSd.setChecked(AipqProps.isSdOn(c));

        int demo = AipqProps.demoMode(c);
        setListValue(mDemo, String.valueOf(demo), c.getString(R.string.aipq_demo_summary)
                + "\n" + c.getString(RkLabels.DEMO_LABELS[demo]));
    }
}
