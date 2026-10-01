package org.lineageos.rk3576.aipq;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.android.tv.twopanelsettings.slices.TvSettingsSliceProvider;

public class ResolutionConfirmActivity extends Activity {

    static final String EXTRA_KIND = "kind";
    static final String KIND_MODE = "mode";
    static final String KIND_COLOR = "color";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_PREVIOUS = "previous";

    private static final int TIMEOUT_SECONDS = 15;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private AlertDialog mDialog;
    private boolean mColor;
    private String mMode;
    private String mPrevious;
    private int mRemaining = TIMEOUT_SECONDS;
    private boolean mDone;

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (--mRemaining <= 0) {
                finishWith(false);
                return;
            }
            updateMessage();
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mColor = KIND_COLOR.equals(getIntent().getStringExtra(EXTRA_KIND));
        mMode = getIntent().getStringExtra(EXTRA_MODE);
        mPrevious = getIntent().getStringExtra(EXTRA_PREVIOUS);
        if (mMode == null) {
            finish();
            return;
        }
        mDialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(mColor ? R.string.color_confirm_title : R.string.res_confirm_title)
                .setPositiveButton(R.string.res_confirm_keep, (d, w) -> finishWith(true))
                .setNegativeButton(R.string.res_confirm_revert, (d, w) -> finishWith(false))
                .setOnCancelListener(d -> finishWith(false))
                .create();
        updateMessage();
        mDialog.show();
        mDialog.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus();
        mHandler.postDelayed(mTick, 1000);
    }

    private void updateMessage() {
        mDialog.setMessage(getString(R.string.res_confirm_message,
                mColor ? mMode : RkOutputClient.modeLabel(mMode), mRemaining));
    }

    private void finishWith(boolean keep) {
        if (mDone) {
            return;
        }
        mDone = true;
        mHandler.removeCallbacks(mTick);
        if (keep || mPrevious == null) {
            RkOutputClient.saveConfig();
        } else {
            if (mColor) {
                RkOutputClient.setColorMode(RkOutputClient.DISPLAY_MAIN, mPrevious);
            } else {
                RkOutputClient.setMode(RkOutputClient.DISPLAY_MAIN, mPrevious);
            }
        }
        TvSettingsSliceProvider.invalidateSlice(this, AipqSliceProvider.RESOLUTION_URI);
        if (mDialog != null) {
            mDialog.dismiss();
        }
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            finishWith(false);
        }
    }
}
