/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.DeviceFlag;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a set of flags used to control various launcher behaviors.
 *
 * <p>All the flags should be defined here with appropriate default values.
 */
public final class FeatureFlags {

    private static final List<DebugFlag> sDebugFlags = new ArrayList<>();

    public static final String FLAGS_PREF_NAME = "featureFlags";

    private FeatureFlags() {
    }

    public static boolean showFlagTogglerUi(Context context) {
        return BuildConfig.IS_DEBUG_DEVICE && Utilities.isDevelopersOptionsEnabled(context);
    }

    /**
     * True when the build has come from Android Studio and is being used for local debugging.
     * @deprecated Use {@link BuildConfig#IS_STUDIO_BUILD} directly
     */
    @Deprecated
    public static final boolean IS_STUDIO_BUILD = BuildConfig.IS_STUDIO_BUILD;

    /**
     * Enable moving the QSB on the 0th screen of the workspace. This is not a configuration feature
     * and should be modified at a project level.
     * @deprecated Use {@link BuildConfig#QSB_ON_FIRST_SCREEN} directly
     */
    @Deprecated
    public static final boolean QSB_ON_FIRST_SCREEN = BuildConfig.QSB_ON_FIRST_SCREEN;

    /**
     * Feature flag to handle define config changes dynamically instead of killing the process.
     *
     *
     * To add a new flag that can be toggled through the flags UI:
     *
     * Declare a new ToggleableFlag below. Give it a unique key (e.g. "QSB_ON_FIRST_SCREEN"),
     * and set a default value for the flag. This will be the default value on Debug builds.
     */
    // b/270390028
    public static final BooleanFlag ENABLE_INPUT_CONSUMER_REASON_LOGGING = getDebugFlag(
            "ENABLE_INPUT_CONSUMER_REASON_LOGGING",
            true,
            "Log the reason why an Input Consumer was selected for a gesture.");

    // b/270389990
    public static final BooleanFlag ENABLE_GESTURE_ERROR_DETECTION = getDebugFlag(
            "ENABLE_GESTURE_ERROR_DETECTION",
            true,
            "Analyze gesture events and log detected errors");

    // When enabled the promise icon is visible in all apps while installation an app.
    // b/270390012
    public static final BooleanFlag PROMISE_APPS_IN_ALL_APPS = getDebugFlag(
            "PROMISE_APPS_IN_ALL_APPS", false, "Add promise icon in all-apps");

    // b/270390904
    public static final BooleanFlag KEYGUARD_ANIMATION = getDebugFlag(
            "KEYGUARD_ANIMATION", false, "Enable animation for keyguard going away on wallpaper");

    // b/270390907
    public static final BooleanFlag ENABLE_DEVICE_SEARCH = new DeviceFlag(
            "ENABLE_DEVICE_SEARCH", true, "Allows on device search in all apps");

    // b/270390286
    public static final BooleanFlag ENABLE_FLOATING_SEARCH_BAR =
            new DeviceFlag("ENABLE_FLOATING_SEARCH_BAR", false,
                    "Keep All Apps search bar at the bottom (but above keyboard if open)");

    // b/270390930
    public static final BooleanFlag ENABLE_HIDE_HEADER = new DeviceFlag("ENABLE_HIDE_HEADER",
            true, "Hide header on keyboard before typing in all apps");

    // b/270390779
    public static final BooleanFlag ENABLE_EXPANDING_PAUSE_WORK_BUTTON = getDebugFlag(
            "ENABLE_EXPANDING_PAUSE_WORK_BUTTON", true,
            "Expand and collapse pause work button while scrolling");

    // b/270390950
    public static final BooleanFlag ENABLE_RECENT_BLOCK = getDebugFlag("ENABLE_RECENT_BLOCK",
            false, "Show recently tapped search target block in zero state");

    // b/270391455
    public static final BooleanFlag COLLECT_SEARCH_HISTORY = new DeviceFlag(
            "COLLECT_SEARCH_HISTORY", false, "Allow launcher to collect search history for log");

    // b/270390937
    public static final BooleanFlag ENABLE_TWOLINE_ALLAPPS = getDebugFlag(
            "ENABLE_TWOLINE_ALLAPPS", false, "Enables two line label inside all apps.");

    // b/270391397
    public static final BooleanFlag ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING = new DeviceFlag(
            "ENABLE_DEVICE_SEARCH_PERFORMANCE_LOGGING", false,
            "Allows on device search in all apps logging");

    // b/270391693
    public static final BooleanFlag IME_STICKY_SNACKBAR_EDU = getDebugFlag(
            "IME_STICKY_SNACKBAR_EDU", true, "Show sticky IME edu in AllApps");

    // b/270391653
    public static final BooleanFlag ENABLE_PEOPLE_TILE_PREVIEW = getDebugFlag(
            "ENABLE_PEOPLE_TILE_PREVIEW", false,
            "Experimental: Shows conversation shortcuts on home screen as search results");

    // b/270391638
    public static final BooleanFlag FOLDER_NAME_MAJORITY_RANKING = getDebugFlag(
            "FOLDER_NAME_MAJORITY_RANKING", true,
            "Suggests folder names based on majority based ranking.");

    // b/270391706
    public static final BooleanFlag INJECT_FALLBACK_APP_CORPUS_RESULTS = new DeviceFlag(
            "INJECT_FALLBACK_APP_CORPUS_RESULTS", false, "Inject "
            + "fallback app corpus result when AiAi fails to return it.");

    // b/270391641
    public static final BooleanFlag ASSISTANT_GIVES_LAUNCHER_FOCUS = getDebugFlag(
            "ASSISTANT_GIVES_LAUNCHER_FOCUS", false,
            "Allow Launcher to handle nav bar gestures while Assistant is running over it");

    // b/270392203
    public static final BooleanFlag ENABLE_BULK_WORKSPACE_ICON_LOADING = getDebugFlag(
            "ENABLE_BULK_WORKSPACE_ICON_LOADING",
            true,
            "Enable loading workspace icons in bulk.");

    // b/270392465
    public static final BooleanFlag ENABLE_BULK_ALL_APPS_ICON_LOADING = getDebugFlag(
            "ENABLE_BULK_ALL_APPS_ICON_LOADING",
            true,
            "Enable loading all apps icons in bulk.");

    // b/270392706
    public static final BooleanFlag ENABLE_DATABASE_RESTORE = getDebugFlag(
            "ENABLE_DATABASE_RESTORE", false,
            "Enable database restore when new restore session is created");

    // b/270391664
    public static final BooleanFlag ENABLE_SMARTSPACE_DISMISS = getDebugFlag(
            "ENABLE_SMARTSPACE_DISMISS", true,
            "Adds a menu option to dismiss the current Enhanced Smartspace card.");

    // b/270392629
    public static final BooleanFlag ENABLE_OVERLAY_CONNECTION_OPTIM = getDebugFlag(
            "ENABLE_OVERLAY_CONNECTION_OPTIM",
            false,
            "Enable optimizing overlay service connection");

    /**
     * Enables region sampling for text color: Needs system health assessment before turning on
     */
    // b/270391669
    public static final BooleanFlag ENABLE_REGION_SAMPLING = getDebugFlag(
            "ENABLE_REGION_SAMPLING", false,
            "Enable region sampling to determine color of text on screen.");

    // b/270393096
    public static final BooleanFlag ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS =
            getDebugFlag(
                    "ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS", false,
                    "Always use hardware optimization for folder animations.");

    // b/270392980
    public static final BooleanFlag SEPARATE_RECENTS_ACTIVITY = getDebugFlag(
            "SEPARATE_RECENTS_ACTIVITY", false,
            "Uses a separate recents activity instead of using the integrated recents+Launcher UI");

    // b/270392984
    public static final BooleanFlag ENABLE_MINIMAL_DEVICE = getDebugFlag(
            "ENABLE_MINIMAL_DEVICE", false,
            "Allow user to toggle minimal device mode in launcher.");

    // b/270392477
    public static final BooleanFlag ENABLE_TASKBAR_POPUP_MENU = getDebugFlag(
            "ENABLE_TASKBAR_POPUP_MENU", true, "Enables long pressing taskbar icons to show the"
                    + " popup menu.");

    // b/270392643
    public static final BooleanFlag ENABLE_TWO_PANEL_HOME = getDebugFlag(
            "ENABLE_TWO_PANEL_HOME", true,
            "Uses two panel on home screen. Only applicable on large screen devices.");

    // b/270393276
    public static final BooleanFlag ENABLE_SCRIM_FOR_APP_LAUNCH = getDebugFlag(
            "ENABLE_SCRIM_FOR_APP_LAUNCH", false,
            "Enables scrim during app launch animation.");

    // b/270393258
    public static final BooleanFlag ENABLE_ENFORCED_ROUNDED_CORNERS = new DeviceFlag(
            "ENABLE_ENFORCED_ROUNDED_CORNERS", true, "Enforce rounded corners on all App Widgets");

    // b/270393108
    public static final BooleanFlag NOTIFY_CRASHES = getDebugFlag("NOTIFY_CRASHES", false,
            "Sends a notification whenever launcher encounters an uncaught exception.");

    // b/270393604
    public static final BooleanFlag ENABLE_WALLPAPER_SCRIM = getDebugFlag(
            "ENABLE_WALLPAPER_SCRIM", false,
            "Enables scrim over wallpaper for text protection.");

    // b/270393268
    public static final BooleanFlag WIDGETS_IN_LAUNCHER_PREVIEW = getDebugFlag(
            "WIDGETS_IN_LAUNCHER_PREVIEW", true,
            "Enables widgets in Launcher preview for the Wallpaper app.");

    // b/270393112
    public static final BooleanFlag QUICK_WALLPAPER_PICKER = getDebugFlag(
            "QUICK_WALLPAPER_PICKER", true,
            "Shows quick wallpaper picker in long-press menu");

    // b/270393426
    public static final BooleanFlag ENABLE_BACK_SWIPE_HOME_ANIMATION = getDebugFlag(
            "ENABLE_BACK_SWIPE_HOME_ANIMATION", true,
            "Enables home animation to icon when user swipes back.");

    // b/270393294
    public static final BooleanFlag ENABLE_ICON_LABEL_AUTO_SCALING = getDebugFlag(
            "ENABLE_ICON_LABEL_AUTO_SCALING", true,
            "Enables scaling/spacing for icon labels to make more characters visible");

    // b/270393897
    public static final BooleanFlag ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT = getDebugFlag(
            "ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT", false,
            "Enables displaying the all apps button in the hotseat.");

    // b/270393900
    public static final BooleanFlag ENABLE_ALL_APPS_ONE_SEARCH_IN_TASKBAR = getDebugFlag(
            "ENABLE_ALL_APPS_ONE_SEARCH_IN_TASKBAR", false,
            "Enables One Search box in Taskbar All Apps.");

    // b/270393449
    public static final BooleanFlag ENABLE_TASKBAR_IN_OVERVIEW = getDebugFlag(
            "ENABLE_TASKBAR_IN_OVERVIEW", true,
            "Enables accessing the system Taskbar in overview.");

    // b/270393906
    public static final BooleanFlag ENABLE_SPLIT_FROM_WORKSPACE = getDebugFlag(
            "ENABLE_SPLIT_FROM_WORKSPACE", true,
            "Enable initiating split screen from workspace.");

    // b/270394122
    public static final BooleanFlag ENABLE_SPLIT_FROM_FULLSCREEN_WITH_KEYBOARD_SHORTCUTS =
            getDebugFlag("ENABLE_SPLIT_FROM_FULLSCREEN_SHORTCUT", true,
                    "Enable splitting from fullscreen app with keyboard shortcuts");

    // b/270393453
    public static final BooleanFlag ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE = getDebugFlag(
            "ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE", false,
            "Enable initiating split screen from workspace to workspace.");

    // b/270393455
    public static final BooleanFlag ENABLE_NEW_MIGRATION_LOGIC = getDebugFlag(
            "ENABLE_NEW_MIGRATION_LOGIC", true,
            "Enable the new grid migration logic, keeping pages when src < dest");

    // b/270394384
    public static final BooleanFlag ENABLE_WIDGET_HOST_IN_BACKGROUND = getDebugFlag(
            "ENABLE_WIDGET_HOST_IN_BACKGROUND", false,
            "Enable background widget updates listening for widget holder");

    // b/270394223
    public static final BooleanFlag ENABLE_ONE_SEARCH_MOTION = new DeviceFlag(
            "ENABLE_ONE_SEARCH_MOTION", true, "Enables animations in OneSearch.");

    // b/270394041
    public static final BooleanFlag ENABLE_SEARCH_RESULT_BACKGROUND_DRAWABLES = new DeviceFlag(
            "ENABLE_SEARCH_RESULT_BACKGROUND_DRAWABLES", false,
            "Enable option to replace decorator-based search result backgrounds with drawables");

    // b/270394392
    public static final BooleanFlag ENABLE_SEARCH_RESULT_LAUNCH_TRANSITION = new DeviceFlag(
            "ENABLE_SEARCH_RESULT_LAUNCH_TRANSITION", false,
            "Enable option to launch search results using the new view container transitions");

    // b/270394225
    public static final BooleanFlag TWO_PREDICTED_ROWS_ALL_APPS_SEARCH = new DeviceFlag(
            "TWO_PREDICTED_ROWS_ALL_APPS_SEARCH", false,
            "Use 2 rows of app predictions in All Apps search zero-state");

    // b/270394468
    public static final BooleanFlag ENABLE_SHOW_KEYBOARD_OPTION_IN_ALL_APPS = new DeviceFlag(
            "ENABLE_SHOW_KEYBOARD_OPTION_IN_ALL_APPS", true,
            "Enable option to show keyboard when going to all-apps");

    // b/270394973
    public static final BooleanFlag USE_LOCAL_ICON_OVERRIDES = getDebugFlag(
            "USE_LOCAL_ICON_OVERRIDES", true,
            "Use inbuilt monochrome icons if app doesn't provide one");

    // b/270394476
    public static final BooleanFlag ENABLE_DISMISS_PREDICTION_UNDO = getDebugFlag(
            "ENABLE_DISMISS_PREDICTION_UNDO", false,
            "Show an 'Undo' snackbar when users dismiss a predicted hotseat item");

    // b/270395008
    public static final BooleanFlag ENABLE_CACHED_WIDGET = getDebugFlag(
            "ENABLE_CACHED_WIDGET", true,
            "Show previously cached widgets as opposed to deferred widget where available");

    // b/270395010
    public static final BooleanFlag USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES = getDebugFlag(
            "USE_SEARCH_REQUEST_TIMEOUT_OVERRIDES", false,
            "Use local overrides for search request timeout");

    // b/270395171
    public static final BooleanFlag CONTINUOUS_VIEW_TREE_CAPTURE = getDebugFlag(
            "CONTINUOUS_VIEW_TREE_CAPTURE", false, "Capture View tree every frame");

    // b/270395140
    public static final BooleanFlag SECONDARY_DRAG_N_DROP_TO_PIN = getDebugFlag(
            "SECONDARY_DRAG_N_DROP_TO_PIN", false,
            "Enable dragging and dropping to pin apps within secondary display");

    // b/270395070
    public static final BooleanFlag FOLDABLE_WORKSPACE_REORDER = getDebugFlag(
            "FOLDABLE_WORKSPACE_REORDER", false,
            "In foldables, when reordering the icons and widgets, is now going to use both sides");

    // b/270395073
    public static final BooleanFlag ENABLE_MULTI_DISPLAY_PARTIAL_DEPTH = getDebugFlag(
            "ENABLE_MULTI_DISPLAY_PARTIAL_DEPTH", false,
            "Allow bottom sheet depth to be smaller than 1 for multi-display devices.");

    // b/270395177
    public static final BooleanFlag SCROLL_TOP_TO_RESET = new DeviceFlag(
            "SCROLL_TOP_TO_RESET", true, "Bring up IME and focus on "
            + "input when scroll to top if 'Always show keyboard' is enabled or in prefix state");

    // b/270395516
    public static final BooleanFlag ENABLE_MATERIAL_U_POPUP = getDebugFlag(
            "ENABLE_MATERIAL_U_POPUP", false, "Switch popup UX to use material U");

    // b/270395269
    public static final BooleanFlag ENABLE_SEARCH_UNINSTALLED_APPS = new DeviceFlag(
            "ENABLE_SEARCH_UNINSTALLED_APPS", false, "Search uninstalled app results.");

    // b/270395183
    public static final BooleanFlag SHOW_HOME_GARDENING = getDebugFlag(
            "SHOW_HOME_GARDENING", false,
            "Show the new home gardening mode");

    // b/270395133
    public static final BooleanFlag HOME_GARDENING_WORKSPACE_BUTTONS = getDebugFlag(
            "HOME_GARDENING_WORKSPACE_BUTTONS", false,
            "Change workspace edit buttons to reflect home gardening");

    // b/270395134
    public static final BooleanFlag ENABLE_DOWNLOAD_APP_UX_V2 = new DeviceFlag(
            "ENABLE_DOWNLOAD_APP_UX_V2", true, "Updates the download app UX"
                    + " to have better visuals");

    // b/270395186
    public static final BooleanFlag ENABLE_DOWNLOAD_APP_UX_V3 = getDebugFlag(
            "ENABLE_DOWNLOAD_APP_UX_V3", false, "Updates the download app UX"
            + " to have better visuals, improve contrast, and color");

    // b/270395077
    public static final BooleanFlag FORCE_PERSISTENT_TASKBAR = getDebugFlag(
            "FORCE_PERSISTENT_TASKBAR", false, "Forces taskbar to be persistent, even in gesture"
                    + " nav mode and when transient taskbar is enabled.");

    // b/270395274
    public static final BooleanFlag FOLDABLE_SINGLE_PAGE = getDebugFlag(
            "FOLDABLE_SINGLE_PAGE", false,
            "Use a single page for the workspace");

    // b/270395798
    public static final BooleanFlag ENABLE_TRANSIENT_TASKBAR = getDebugFlag(
            "ENABLE_TRANSIENT_TASKBAR", true, "Enables transient taskbar.");

    public static final BooleanFlag ENABLE_TRACKPAD_GESTURE = getDebugFlag(
            "ENABLE_TRACKPAD_GESTURE", false, "Enables trackpad gesture.");

    // b/270395143
    public static final BooleanFlag ENABLE_ICON_IN_TEXT_HEADER = getDebugFlag(
            "ENABLE_ICON_IN_TEXT_HEADER", false, "Show icon in textheader");

    // b/270395087
    public static final BooleanFlag ENABLE_APP_ICON_FOR_INLINE_SHORTCUTS = getDebugFlag(
            "ENABLE_APP_ICON_IN_INLINE_SHORTCUTS", false, "Show app icon for inline shortcut");

    // b/270395278
    public static final BooleanFlag SHOW_DOT_PAGINATION = getDebugFlag(
            "SHOW_DOT_PAGINATION", false, "Enable showing dot pagination in workspace");

    // b/270395809
    public static final BooleanFlag LARGE_SCREEN_WIDGET_PICKER = getDebugFlag(
            "LARGE_SCREEN_WIDGET_PICKER", false, "Enable new widget picker that takes "
                    + "advantage of large screen format");

    // b/270396257
    public static final BooleanFlag ENABLE_NEW_GESTURE_NAV_TUTORIAL = getDebugFlag(
            "ENABLE_NEW_GESTURE_NAV_TUTORIAL", false,
            "Enable the redesigned gesture navigation tutorial");

    // b/270395567
    public static final BooleanFlag ENABLE_LAUNCH_FROM_STAGED_APP = getDebugFlag(
            "ENABLE_LAUNCH_FROM_STAGED_APP", true,
            "Enable the ability to tap a staged app during split select to launch it in full screen"
    );

    // b/270396358
    public static final BooleanFlag ENABLE_HAPTICS_ALL_APPS = getDebugFlag(
            "ENABLE_HAPTICS_ALL_APPS", false, "Enables haptics opening/closing All apps");

    // b/270396209
    public static final BooleanFlag ENABLE_FORCED_MONO_ICON = getDebugFlag(
            "ENABLE_FORCED_MONO_ICON", false,
            "Enable the ability to generate monochromatic icons, if it is not provided by the app"
    );

    // b/270396364
    public static final BooleanFlag ENABLE_DREAM_TRANSITION = getDebugFlag(
            "ENABLE_DREAM_TRANSITION", true,
            "Enable the launcher transition when the device enters a dream");

    // b/270396268
    public static final BooleanFlag ENABLE_TASKBAR_EDU_TOOLTIP = getDebugFlag(
            "ENABLE_TASKBAR_EDU_TOOLTIP", true,
            "Enable the tooltip version of the Taskbar education flow.");

    // b/270396680
    public static final BooleanFlag ENABLE_MULTI_INSTANCE = getDebugFlag(
            "ENABLE_MULTI_INSTANCE", false,
            "Enables creation and filtering of multiple task instances in overview");

    // b/270396583
    public static final BooleanFlag ENABLE_TASKBAR_PINNING = getDebugFlag(
            "ENABLE_TASKBAR_PINNING", false,
            "Enables taskbar pinning to allow user to switch between transient and persistent "
                    + "taskbar flavors");

    // b/270397206
    public static final BooleanFlag ENABLE_GRID_ONLY_OVERVIEW = getDebugFlag(
            "ENABLE_GRID_ONLY_OVERVIEW", false,
            "Enable a grid-only overview without a focused task.");

    // b/270397209
    public static final BooleanFlag RECEIVE_UNFOLD_EVENTS_FROM_SYSUI = getDebugFlag(
            "RECEIVE_UNFOLD_EVENTS_FROM_SYSUI", true,
            "Enables receiving unfold animation events from sysui instead of calculating "
                    + "them in launcher process using hinge sensor values.");

    // b/270396844
    public static final BooleanFlag ENABLE_KEYBOARD_QUICK_SWITCH = getDebugFlag(
            "ENABLE_KEYBOARD_QUICK_SWITCH", true,
            "Enables keyboard quick switching");

    public static void initialize(Context context) {
        synchronized (sDebugFlags) {
            for (DebugFlag flag : sDebugFlags) {
                flag.initialize(context);
            }

            sDebugFlags.sort((f1, f2) -> {
                // Sort first by any prefs that the user has changed, then alphabetically.
                int changeComparison = Boolean.compare(f2.mHasBeenChangedAtLeastOnce,
                        f1.mHasBeenChangedAtLeastOnce);
                return changeComparison != 0
                        ? changeComparison
                        : f1.key.compareToIgnoreCase(f2.key);
            });
        }
    }

    static List<DebugFlag> getDebugFlags() {
        synchronized (sDebugFlags) {
            return new ArrayList<>(sDebugFlags);
        }
    }

    public static void dump(PrintWriter pw) {
        pw.println("DeviceFlags:");
        synchronized (sDebugFlags) {
            for (DebugFlag flag : sDebugFlags) {
                if (flag instanceof DeviceFlag) {
                    pw.println("  " + flag.toString());
                }
            }
        }
        pw.println("DebugFlags:");
        synchronized (sDebugFlags) {
            for (DebugFlag flag : sDebugFlags) {
                if (!(flag instanceof DeviceFlag)) {
                    pw.println("  " + flag.toString());
                }
            }
        }
    }

    public static class BooleanFlag {

        public final String key;
        public final boolean defaultValue;

        public BooleanFlag(String key, boolean defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        public boolean get() {
            return defaultValue;
        }

        @Override
        public String toString() {
            return appendProps(new StringBuilder()).toString();
        }

        protected StringBuilder appendProps(StringBuilder src) {
            return src.append(key).append(", defaultValue=").append(defaultValue);
        }
    }

    public static class DebugFlag extends BooleanFlag {

        public final String description;
        protected boolean mHasBeenChangedAtLeastOnce;
        protected boolean mCurrentValue;

        public DebugFlag(String key, boolean defaultValue, String description) {
            super(key, defaultValue);
            this.description = description;
            mCurrentValue = this.defaultValue;
            synchronized (sDebugFlags) {
                sDebugFlags.add(this);
            }
        }

        @Override
        public boolean get() {
            return mCurrentValue;
        }

        public void initialize(Context context) {
            SharedPreferences prefs =
                    context.getSharedPreferences(FLAGS_PREF_NAME, Context.MODE_PRIVATE);
            mHasBeenChangedAtLeastOnce = prefs.contains(key);
            mCurrentValue = prefs.getBoolean(key, defaultValue);
        }

        @Override
        protected StringBuilder appendProps(StringBuilder src) {
            return super.appendProps(src).append(", mCurrentValue=").append(mCurrentValue);
        }
    }

    private static BooleanFlag getDebugFlag(String key, boolean defaultValue, String description) {
        return Utilities.IS_DEBUG_DEVICE
                ? new DebugFlag(key, defaultValue, description)
                : new BooleanFlag(key, defaultValue);
    }
}
