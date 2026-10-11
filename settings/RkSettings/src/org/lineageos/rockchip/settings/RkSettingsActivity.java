package org.lineageos.rockchip.settings;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class RkSettingsActivity extends CollapsingToolbarBaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new MainFragment())
                    .commit();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), pref.getFragment());
        getSupportFragmentManager().beginTransaction()
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }
}
