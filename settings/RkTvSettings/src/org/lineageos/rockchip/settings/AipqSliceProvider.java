package org.lineageos.rockchip.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.List;

import com.android.tv.twopanelsettings.slices.TvSettingsSliceProvider;
import com.android.tv.twopanelsettings.slices.builders.PreferenceSliceBuilder;
import com.android.tv.twopanelsettings.slices.builders.PreferenceSliceBuilder.RowBuilder;

/**
 * Hosts the AI PQ slice. The two-panel TvSettings opens it through
 * com.android.tv.twopanelsettings.slices.SliceFragment (the entry row is added
 * to the Device & sound page's Advanced display screen by the
 * TvSettingsRockchipOverlay RRO) and renders the rows as native preferences:
 * addSwitch -> SliceSwitchPreference, addRadioButton -> SliceRadioPreference.
 *
 * Strengths are flat top-level radio rows, NOT category rows with children:
 * the category+children shape adds every child twice on the renderer side
 * (PreferenceGroup "duplicated key", logcat13 13:01:55) and breaks the radio
 * group. The in-tree producer to copy is ConnectedDevicesSliceProvider's
 * backlight radios -- one addPreference per option. Seekbars are still
 * avoided on purpose: the renderer's seekbar dispatch fires the row action
 * with only extra_preference_key filled in, no value and no direction
 * (SliceShard.onSeekbarPreferenceChanged), so a stateless provider cannot
 * learn the target value.
 */
public class AipqSliceProvider extends TvSettingsSliceProvider {

    public static final String AUTHORITY = "org.lineageos.rockchip.settings";
    public static final Uri SLICE_URI =
            Uri.parse("content://" + AUTHORITY + "/main");
    public static final Uri DISPLAY_URI =
            Uri.parse("content://" + AUTHORITY + "/display");
    public static final Uri RESOLUTION_URI =
            Uri.parse("content://" + AUTHORITY + "/resolution");
    public static final Uri SCALE_URI =
            Uri.parse("content://" + AUTHORITY + "/scale");
    public static final Uri SR_URI =
            Uri.parse("content://" + AUTHORITY + "/sr");
    public static final Uri DC_URI =
            Uri.parse("content://" + AUTHORITY + "/dc");
    public static final Uri DEMO_URI =
            Uri.parse("content://" + AUTHORITY + "/demo");
    public static final Uri USB_URI =
            Uri.parse("content://" + AUTHORITY + "/usb");
    public static final Uri DOCKER_URI =
            Uri.parse("content://" + AUTHORITY + "/docker");

    static Uri pageUri(Uri parent, String knob) {
        return parent.buildUpon().appendPath(knob).build();
    }

    static void invalidatePage(Context c, Uri parent, String knob) {
        TvSettingsSliceProvider.invalidateSlice(c, pageUri(parent, knob));
        TvSettingsSliceProvider.invalidateSlice(c, parent);
    }

    /** The renderer fills in this extra (SlicesConstants.EXTRA_PREFERENCE_KEY). */
    static final String EXTRA_PREFERENCE_KEY = "extra_preference_key";

    private static final int PAGE_ID = 990001; // TvSettingsEnums-namespace-free logging id

    // Rockchip's ScreenScaleActivity range
    private static final int SCALE_MIN = 80;
    private static final int SCALE_STEP = 2;

    @Override
    public boolean onCreateSliceProvider() {
        return true;
    }

    @Override
    public void onSlicePinned(Uri sliceUri) {
        // Same platform signature as TvSettings, so the slice is readable
        // already; grant explicitly anyway in case the signature policy is
        // ever tightened.
        try {
            getContext().getSystemService(android.app.slice.SliceManager.class)
                    .grantSlicePermission("com.android.tv.settings", sliceUri);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected boolean createSlice(PreferenceSliceBuilder builder, Uri sliceUri) {
        List<String> path = sliceUri.getPathSegments();
        if (path.size() == 2) {
            return createSubSlice(builder, path.get(0), path.get(1));
        }
        if (DISPLAY_URI.equals(sliceUri)) {
            return createDisplaySlice(builder);
        }
        if (RESOLUTION_URI.equals(sliceUri)) {
            return createResolutionSlice(builder);
        }
        if (SCALE_URI.equals(sliceUri)) {
            return createScaleSlice(builder);
        }
        if (SR_URI.equals(sliceUri)) {
            return createSrSlice(builder);
        }
        if (DC_URI.equals(sliceUri)) {
            return createDcSlice(builder);
        }
        if (DEMO_URI.equals(sliceUri)) {
            return createDemoSlice(builder);
        }
        if (USB_URI.equals(sliceUri)) {
            return createUsbSlice(builder);
        }
        if (DOCKER_URI.equals(sliceUri)) {
            return createDockerSlice(builder);
        }
        if (!SLICE_URI.equals(sliceUri)) {
            return false;
        }
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.aipq_title))
                .setPageId(PAGE_ID));

        builder.addPreference(new RowBuilder()
                .setKey("aipq_master")
                .setTitle(c.getString(R.string.aipq_master_title))
                .setSubtitle(c.getString(R.string.aipq_master_summary))
                .addSwitch(knobIntent(c, AipqProps.KNOB_MASTER),
                        c.getString(R.string.aipq_master_title),
                        AipqProps.isMasterOn(c)));

        String sr = RkLabels.strengthLabel(c, AipqProps.srStrength(c));
        if (RkOutputClient.IS_RK3576 && AipqProps.srStrength(c) > 0
                && AipqProps.isSrForced(c)) {
            sr += " · " + c.getString(R.string.aipq_sr_forced_short);
        }
        addPageRow(builder, "aipq_sr_page", c.getString(R.string.aipq_sr_title), sr, SR_URI);
        addPageRow(builder, "aipq_dc_page", c.getString(R.string.aipq_dc_title),
                RkLabels.strengthLabel(c, AipqProps.dcStrength(c)), DC_URI);
        addSwitchRow(builder, c, AipqProps.KNOB_MEMC,
                R.string.aipq_memc_title, R.string.aipq_memc_summary,
                AipqProps.isMemcOn(c));
        addSwitchRow(builder, c, AipqProps.KNOB_FE,
                R.string.aipq_fe_title, R.string.aipq_fe_summary,
                AipqProps.isFeOn(c));
        if (RkOutputClient.IS_RK3576) {
            addSwitchRow(builder, c, AipqProps.KNOB_ACM,
                    R.string.aipq_acm_title, R.string.aipq_acm_summary,
                    AipqProps.isAcmOn(c));
            addSwitchRow(builder, c, AipqProps.KNOB_DCI,
                    R.string.aipq_dci_title, R.string.aipq_dci_summary,
                    AipqProps.isDciOn(c));
        }
        addSwitchRow(builder, c, AipqProps.KNOB_SD,
                R.string.aipq_sd_title, R.string.aipq_sd_summary,
                AipqProps.isSdOn(c));

        addPageRow(builder, "aipq_demo_page", c.getString(R.string.aipq_demo_title),
                c.getString(RkLabels.DEMO_LABELS[AipqProps.demoMode(c)]), DEMO_URI);

        builder.addPreference(new RowBuilder()
                .setKey("aipq_note")
                .setTitle(c.getString(R.string.aipq_note))
                .setSelectable(false));
        return true;
    }

    private boolean createSrSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.aipq_sr_title))
                .setPageId(PAGE_ID + 4));
        addStrengthGroup(builder, c, AipqProps.KNOB_SR,
                R.string.aipq_sr_title, R.string.aipq_sr_summary,
                AipqProps.srStrength(c));
        if (RkOutputClient.IS_RK3576) {
            addSwitchRow(builder, c, AipqProps.KNOB_SR_FORCE,
                    R.string.aipq_sr_force_title, R.string.aipq_sr_force_summary,
                    AipqProps.isSrForced(c));
        }
        return true;
    }

    private boolean createDcSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.aipq_dc_title))
                .setPageId(PAGE_ID + 5));
        addStrengthGroup(builder, c, AipqProps.KNOB_DC,
                R.string.aipq_dc_title, R.string.aipq_dc_summary,
                AipqProps.dcStrength(c));
        return true;
    }

    private boolean createDemoSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.aipq_demo_title))
                .setPageId(PAGE_ID + 6));
        addDemoGroup(builder, c);
        return true;
    }

    private boolean createUsbSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.usb_title))
                .setPageId(PAGE_ID + 15));
        String title = c.getString(R.string.usb_host_title);
        builder.addPreference(new RowBuilder()
                .setKey("usb_" + UsbMode.KNOB_HOST)
                .setTitle(title)
                .setSubtitle(c.getString(R.string.usb_host_summary))
                .addSwitch(knobIntent(c, UsbMode.KNOB_HOST), title, UsbMode.isHost()));
        return true;
    }

    private boolean createDockerSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.docker_title))
                .setPageId(PAGE_ID + 16));
        String title = c.getString(R.string.docker_enable_title);
        builder.addPreference(new RowBuilder()
                .setKey("docker_" + Docker.KNOB_ENABLE)
                .setTitle(title)
                .setSubtitle(c.getString(R.string.docker_enable_summary))
                .addSwitch(knobIntent(c, Docker.KNOB_ENABLE), title, Docker.isEnabled()));
        return true;
    }

    /** Main-page row that opens a sub-page and shows its current value. */
    private static void addPageRow(PreferenceSliceBuilder builder, String key,
            String title, String value, Uri target) {
        builder.addPreference(new RowBuilder()
                .setKey(key)
                .setTitle(title)
                .setSubtitle(value)
                .setTargetSliceUri(target.toString()));
    }

    /** Non-selectable header row + flat radio rows, backlight style. */
    private void addStrengthGroup(PreferenceSliceBuilder builder, Context c,
            String knob, int titleRes, int summaryRes, int current) {
        builder.addPreference(new RowBuilder()
                .setKey("aipq_" + knob + "_header")
                .setTitle(c.getString(titleRes))
                .setSubtitle(c.getString(summaryRes))
                .setSelectable(false));
        int[] labels = RkLabels.STRENGTH_LABELS;
        for (int i = 0; i < AipqProps.STRENGTHS.length; i++) {
            int value = AipqProps.STRENGTHS[i];
            builder.addPreference(new RowBuilder()
                    .setKey("aipq_" + knob + "_" + value)
                    .setTitle(c.getString(labels[i]))
                    .setRadioGroup("aipq_" + knob)
                    .addRadioButton(
                            knobIntent(c, knob)
                                    .putExtra(AipqBroadcastReceiver.EXTRA_VALUE, value),
                            current == value));
        }
    }

    private void addDemoGroup(PreferenceSliceBuilder builder, Context c) {
        builder.addPreference(new RowBuilder()
                .setKey("aipq_demo_header")
                .setTitle(c.getString(R.string.aipq_demo_title))
                .setSubtitle(c.getString(R.string.aipq_demo_summary))
                .setSelectable(false));
        int[] labels = RkLabels.DEMO_LABELS;
        int current = AipqProps.demoMode(c);
        for (int i = 0; i < labels.length; i++) {
            builder.addPreference(new RowBuilder()
                    .setKey("aipq_demo_" + i)
                    .setTitle(c.getString(labels[i]))
                    .setRadioGroup("aipq_demo")
                    .addRadioButton(
                            knobIntent(c, AipqProps.KNOB_DEMO)
                                    .putExtra(AipqBroadcastReceiver.EXTRA_VALUE, i),
                            current == i));
        }
    }

    private void addSwitchRow(PreferenceSliceBuilder builder, Context c, String knob,
            int titleRes, int summaryRes, boolean checked) {
        String title = c.getString(titleRes);
        builder.addPreference(new RowBuilder()
                .setKey("aipq_" + knob)
                .setTitle(title)
                .setSubtitle(c.getString(summaryRes))
                .addSwitch(knobIntent(c, knob), title, checked));
    }

    private static Intent knobIntent(Context c, String knob) {
        Intent i = new Intent(AipqBroadcastReceiver.ACTION_KNOB);
        i.setClass(c, AipqBroadcastReceiver.class);
        i.putExtra(AipqBroadcastReceiver.EXTRA_KNOB, knob);
        return i;
    }

    private static final String[] BCSH_KNOBS = {AipqProps.KNOB_BRIGHTNESS,
            AipqProps.KNOB_CONTRAST, AipqProps.KNOB_SATURATION, AipqProps.KNOB_HUE};
    private static final int[] BCSH_TITLES = {R.string.disp_brightness,
            R.string.disp_contrast, R.string.disp_saturation, R.string.disp_hue};
    private static final int[] BCSH_SUMMARIES = {R.string.disp_brightness_summary,
            R.string.disp_contrast_summary, R.string.disp_saturation_summary,
            R.string.disp_hue_summary};

    private boolean createSubSlice(PreferenceSliceBuilder builder, String page, String knob) {
        if (DISPLAY_URI.getLastPathSegment().equals(page)) {
            for (int i = 0; i < BCSH_KNOBS.length; i++) {
                if (BCSH_KNOBS[i].equals(knob)) {
                    return createBcshSlice(builder, i);
                }
            }
        } else if (RESOLUTION_URI.getLastPathSegment().equals(page)) {
            if (AipqProps.KNOB_RESOLUTION.equals(knob)) {
                return createModeSlice(builder);
            }
            if (AipqProps.KNOB_COLOR.equals(knob)) {
                return createColorSlice(builder);
            }
        } else if (SCALE_URI.getLastPathSegment().equals(page)) {
            if (AipqProps.KNOB_SCALE_H.equals(knob)) {
                return createScaleSubSlice(builder, knob, R.string.scale_horizontal, 0);
            }
            if (AipqProps.KNOB_SCALE_V.equals(knob)) {
                return createScaleSubSlice(builder, knob, R.string.scale_vertical, 1);
            }
        }
        return false;
    }

    private boolean createDisplaySlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.disp_title))
                .setPageId(PAGE_ID + 1));
        int[] bcsh = displayBcsh(builder, c);
        for (int i = 0; i < BCSH_KNOBS.length; i++) {
            addPageRow(builder, "disp_" + BCSH_KNOBS[i] + "_page",
                    c.getString(BCSH_TITLES[i]), String.valueOf(bcsh[i]),
                    pageUri(DISPLAY_URI, BCSH_KNOBS[i]));
        }
        return true;
    }

    /**
     * One BCSH value as percent radios over the outputmanager HIDL. Steps of
     * 10 -- the renderer's seekbar dispatch is key-only (see the class
     * comment), so radios are the only stateless slider equivalent.
     */
    private boolean createBcshSlice(PreferenceSliceBuilder builder, int index) {
        Context c = getContext();
        String knob = BCSH_KNOBS[index];
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(BCSH_TITLES[index]))
                .setPageId(PAGE_ID + 7 + index));
        int current = displayBcsh(builder, c)[index];
        builder.addPreference(new RowBuilder()
                .setKey("disp_" + knob + "_header")
                .setTitle(c.getString(BCSH_TITLES[index]))
                .setSubtitle(c.getString(BCSH_SUMMARIES[index]))
                .setSelectable(false));
        for (int value = 0; value <= 100; value += 10) {
            builder.addPreference(new RowBuilder()
                    .setKey("disp_" + knob + "_" + value)
                    .setTitle(String.valueOf(value))
                    .setRadioGroup("disp_" + knob)
                    .addRadioButton(
                            knobIntent(c, knob)
                                    .putExtra(AipqBroadcastReceiver.EXTRA_VALUE, value),
                            current == value));
        }
        return true;
    }

    private static int[] displayBcsh(PreferenceSliceBuilder builder, Context c) {
        int[] bcsh = RkOutputClient.getBcsh(RkOutputClient.mainDisplay());
        if (bcsh != null) {
            return bcsh;
        }
        builder.addPreference(new RowBuilder()
                .setKey("disp_unavailable")
                .setTitle(c.getString(R.string.disp_unavailable))
                .setSelectable(false));
        return RkOutputClient.DEFAULT_BCSH;
    }

    private boolean createResolutionSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.res_title))
                .setPageId(PAGE_ID + 2));
        String[] modes = RkOutputClient.getModes(RkOutputClient.mainDisplay());
        if (modes == null || modes.length == 0) {
            addUnavailable(builder, c, "res_unavailable");
            return true;
        }
        addPageRow(builder, "res_page", c.getString(R.string.res_header),
                RkLabels.modeTitle(c, RkOutputClient.getMode(RkOutputClient.mainDisplay())),
                pageUri(RESOLUTION_URI, AipqProps.KNOB_RESOLUTION));
        String[] formats = RkOutputClient.getColorModes(RkOutputClient.mainDisplay());
        if (formats != null && formats.length > 0) {
            addPageRow(builder, "color_page", c.getString(R.string.color_header),
                    RkLabels.formatTitle(c,
                            RkOutputClient.getColorMode(RkOutputClient.mainDisplay())),
                    pageUri(RESOLUTION_URI, AipqProps.KNOB_COLOR));
        }
        String hdrTitle = c.getString(R.string.hdr_title);
        builder.addPreference(new RowBuilder()
                .setKey("hdr")
                .setTitle(hdrTitle)
                .setSubtitle(c.getString(R.string.hdr_summary))
                .addSwitch(knobIntent(c, AipqProps.KNOB_HDR), hdrTitle,
                        RkOutputClient.isHdrEnabled()));
        return true;
    }

    private boolean createModeSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.res_header))
                .setPageId(PAGE_ID + 11));
        String[] modes = RkOutputClient.getModes(RkOutputClient.mainDisplay());
        if (modes == null || modes.length == 0) {
            addUnavailable(builder, c, "res_unavailable");
            return true;
        }
        String current = RkOutputClient.getMode(RkOutputClient.mainDisplay());
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            builder.addPreference(new RowBuilder()
                    .setKey("res_" + i)
                    .setTitle(RkLabels.modeTitle(c, mode))
                    .setRadioGroup("res")
                    .addRadioButton(
                            knobIntent(c, AipqProps.KNOB_RESOLUTION)
                                    .putExtra(AipqBroadcastReceiver.EXTRA_MODE, mode),
                            mode.equals(current)));
        }
        return true;
    }

    private boolean createColorSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.color_header))
                .setPageId(PAGE_ID + 12));
        String[] formats = RkOutputClient.getColorModes(RkOutputClient.mainDisplay());
        if (formats == null || formats.length == 0) {
            addUnavailable(builder, c, "color_unavailable");
            return true;
        }
        String current = RkOutputClient.getColorMode(RkOutputClient.mainDisplay());
        for (int i = 0; i < formats.length; i++) {
            String format = formats[i];
            builder.addPreference(new RowBuilder()
                    .setKey("color_" + i)
                    .setTitle(RkLabels.formatTitle(c, format))
                    .setRadioGroup("color")
                    .addRadioButton(
                            knobIntent(c, AipqProps.KNOB_COLOR)
                                    .putExtra(AipqBroadcastReceiver.EXTRA_MODE, format),
                            format.equals(current)));
        }
        return true;
    }

    private boolean createScaleSlice(PreferenceSliceBuilder builder) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(R.string.scale_title))
                .setPageId(PAGE_ID + 3));
        int[] scale = RkOutputClient.getScale(RkOutputClient.mainDisplay());
        if (scale == null) {
            addUnavailable(builder, c, "scale_unavailable");
            return true;
        }
        addPageRow(builder, AipqProps.KNOB_SCALE_H + "_page",
                c.getString(R.string.scale_horizontal), scale[0] + "%",
                pageUri(SCALE_URI, AipqProps.KNOB_SCALE_H));
        addPageRow(builder, AipqProps.KNOB_SCALE_V + "_page",
                c.getString(R.string.scale_vertical), scale[1] + "%",
                pageUri(SCALE_URI, AipqProps.KNOB_SCALE_V));
        return true;
    }

    private boolean createScaleSubSlice(PreferenceSliceBuilder builder, String knob,
            int titleRes, int index) {
        Context c = getContext();
        builder.addScreenTitle(new RowBuilder()
                .setTitle(c.getString(titleRes))
                .setPageId(PAGE_ID + 13 + index));
        int[] scale = RkOutputClient.getScale(RkOutputClient.mainDisplay());
        if (scale == null) {
            addUnavailable(builder, c, "scale_unavailable");
            return true;
        }
        for (int value = 100; value >= SCALE_MIN; value -= SCALE_STEP) {
            builder.addPreference(new RowBuilder()
                    .setKey(knob + "_" + value)
                    .setTitle(value + "%")
                    .setRadioGroup(knob)
                    .addRadioButton(
                            knobIntent(c, knob)
                                    .putExtra(AipqBroadcastReceiver.EXTRA_VALUE, value),
                            scale[index] == value));
        }
        return true;
    }

    private static void addUnavailable(PreferenceSliceBuilder builder, Context c, String key) {
        builder.addPreference(new RowBuilder()
                .setKey(key)
                .setTitle(c.getString(R.string.disp_unavailable))
                .setSelectable(false));
    }
}
