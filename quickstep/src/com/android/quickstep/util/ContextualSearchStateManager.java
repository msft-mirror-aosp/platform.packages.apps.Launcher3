/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.app.contextualsearch.ContextualSearchManager.ACTION_LAUNCH_CONTEXTUAL_SEARCH;
import static android.app.contextualsearch.ContextualSearchManager.ENTRYPOINT_SYSTEM_ACTION;
import static android.app.contextualsearch.ContextualSearchManager.FEATURE_CONTEXTUAL_SEARCH;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_SUCCESSFUL_SYSTEM_ACTION;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;
import static com.android.quickstep.util.SystemActionConstants.SYSTEM_ACTION_ID_SEARCH_SCREEN;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.CallSuper;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.R;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.EventLogArray;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TopTaskTracker;

import java.io.PrintWriter;
import java.util.Optional;

/** Long-lived class to manage Contextual Search states like the user setting and availability. */
public class ContextualSearchStateManager implements ResourceBasedOverride, SafeCloseable {

    public static final MainThreadInitializedObject<ContextualSearchStateManager> INSTANCE =
            forOverride(ContextualSearchStateManager.class,
                    R.string.contextual_search_state_manager_class);

    private static final String TAG = "ContextualSearchStMgr";
    private static final int MAX_DEBUG_EVENT_SIZE = 20;
    private static final Uri SEARCH_ALL_ENTRYPOINTS_ENABLED_URI =
            Settings.Secure.getUriFor(Settings.Secure.SEARCH_ALL_ENTRYPOINTS_ENABLED);

    private final Runnable mSysUiStateChangeListener = this::updateOverridesToSysUi;
    private final SimpleBroadcastReceiver mContextualSearchPackageReceiver =
            new SimpleBroadcastReceiver(UI_HELPER_EXECUTOR, (unused) -> requestUpdateProperties());
    private final SettingsCache.OnChangeListener mContextualSearchSettingChangedListener =
            this::onContextualSearchSettingChanged;
    protected final EventLogArray mEventLogArray = new EventLogArray(TAG, MAX_DEBUG_EVENT_SIZE);

    // Cached value whether the ContextualSearch intent filter matched any enabled components.
    private boolean mIsContextualSearchIntentAvailable;
    private boolean mIsContextualSearchSettingEnabled;

    protected Context mContext;
    protected String mContextualSearchPackage;

    public ContextualSearchStateManager() {}

    public ContextualSearchStateManager(Context context) {
        mContext = context;
        mContextualSearchPackage = mContext.getResources().getString(
                com.android.internal.R.string.config_defaultContextualSearchPackageName);

        if (areAllContextualSearchFlagsDisabled()
                || !context.getPackageManager().hasSystemFeature(FEATURE_CONTEXTUAL_SEARCH)) {
            // If we had previously registered a SystemAction which is no longer valid, we need to
            // unregister it here.
            unregisterSearchScreenSystemAction();
            // Don't listen for stuff we aren't gonna use.
            return;
        }

        requestUpdateProperties();
        registerSearchScreenSystemAction();
        mContextualSearchPackageReceiver.registerPkgActions(
                context, mContextualSearchPackage, Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_REMOVED);

        SettingsCache.INSTANCE.get(context).register(SEARCH_ALL_ENTRYPOINTS_ENABLED_URI,
                mContextualSearchSettingChangedListener);
        onContextualSearchSettingChanged(
                SettingsCache.INSTANCE.get(context).getValue(SEARCH_ALL_ENTRYPOINTS_ENABLED_URI));
        SystemUiProxy.INSTANCE.get(mContext).addOnStateChangeListener(mSysUiStateChangeListener);
    }

    /** Return {@code true} if the Settings toggle is enabled. */
    public final boolean isContextualSearchSettingEnabled() {
        return mIsContextualSearchSettingEnabled;
    }

    private void onContextualSearchSettingChanged(boolean isEnabled) {
        mIsContextualSearchSettingEnabled = isEnabled;
    }

    /** Whether search supports showing on the lockscreen. */
    protected boolean supportsShowWhenLocked() {
        return false;
    }

    /** Whether ContextualSearchService invocation path is available. */
    @VisibleForTesting
    protected final boolean isContextualSearchIntentAvailable() {
        return mIsContextualSearchIntentAvailable;
    }

    /** Get the Launcher overridden long press nav handle duration to trigger Assistant. */
    public Optional<Long> getLPNHDurationMillis() {
        return Optional.empty();
    }

    /**
     * Get the Launcher overridden long press nav handle touch slop multiplier to trigger Assistant.
     */
    public Optional<Float> getLPNHCustomSlopMultiplier() {
        return Optional.empty();
    }

    /** Get the Launcher overridden long press home duration to trigger Assistant. */
    public Optional<Long> getLPHDurationMillis() {
        return Optional.empty();
    }

    /** Get the Launcher overridden long press home touch slop multiplier to trigger Assistant. */
    public Optional<Float> getLPHCustomSlopMultiplier() {
        return Optional.empty();
    }

    /** Get the long press duration data source. */
    public int getDurationDataSource() {
        return 0;
    }

    /** Get the long press touch slop multiplier data source. */
    public int getSlopDataSource() {
        return 0;
    }

    /**
     * Get the User group based on the behavior to trigger Assistant.
     */
    public Optional<Integer> getLPUserGroup() {
        return Optional.empty();
    }

    /** Get the haptic bit overridden by AGSA. */
    public Optional<Boolean> getShouldPlayHapticOverride() {
        return Optional.empty();
    }

    protected boolean isInvocationAllowedOnKeyguard() {
        return false;
    }

    protected boolean isInvocationAllowedInSplitscreen() {
        return true;
    }

    @CallSuper
    protected boolean areAllContextualSearchFlagsDisabled() {
        return !DeviceConfigWrapper.get().getEnableLongPressNavHandle();
    }

    @CallSuper
    protected void requestUpdateProperties() {
        UI_HELPER_EXECUTOR.execute(() -> {
            // Check that Contextual Search intent filters are enabled.
            Intent csIntent = new Intent(ACTION_LAUNCH_CONTEXTUAL_SEARCH).setPackage(
                    mContextualSearchPackage);
            mIsContextualSearchIntentAvailable =
                    !mContext.getPackageManager().queryIntentActivities(csIntent,
                            PackageManager.MATCH_DIRECT_BOOT_AWARE
                                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE).isEmpty();

            addEventLog("Updated isContextualSearchIntentAvailable",
                    mIsContextualSearchIntentAvailable);
        });
    }

    protected final void updateOverridesToSysUi() {
        // LPH commit haptic is always enabled
        SystemUiProxy.INSTANCE.get(mContext).setOverrideHomeButtonLongPress(
                getLPHDurationMillis().orElse(0L), getLPHCustomSlopMultiplier().orElse(0f), true);
        Log.i(TAG, "Sent LPH override to sysui: " + getLPHDurationMillis().orElse(0L) + ";"
                + getLPHCustomSlopMultiplier().orElse(0f));
    }

    private void registerSearchScreenSystemAction() {
        PendingIntent searchScreenPendingIntent = new PendingIntent(new IIntentSender.Stub() {
            @Override
            public void send(int i, Intent intent, String s, IBinder iBinder,
                    IIntentReceiver iIntentReceiver, String s1, Bundle bundle)
                    throws RemoteException {
                // Delayed slightly to minimize chance of capturing the System Actions dialog.
                UI_HELPER_EXECUTOR.getHandler().postDelayed(
                        () -> {
                            boolean contextualSearchInvoked =
                                    ContextualSearchInvoker.newInstance(mContext).show(
                                            ENTRYPOINT_SYSTEM_ACTION);
                            if (contextualSearchInvoked) {
                                String runningPackage =
                                        TopTaskTracker.INSTANCE.get(mContext).getCachedTopTask(
                                                /* filterOnlyVisibleRecents */
                                                true).getPackageName();
                                StatsLogManager.newInstance(mContext).logger()
                                        .withPackageName(runningPackage)
                                        .log(LAUNCHER_LAUNCH_OMNI_SUCCESSFUL_SYSTEM_ACTION);
                            }
                        }, 200);
            }
        });

        mContext.getSystemService(AccessibilityManager.class).registerSystemAction(new RemoteAction(
                        Icon.createWithResource(mContext, R.drawable.ic_allapps_search),
                        mContext.getString(R.string.search_gesture_feature_title),
                        mContext.getString(R.string.search_gesture_feature_title),
                        searchScreenPendingIntent),
                SYSTEM_ACTION_ID_SEARCH_SCREEN);
    }

    private void unregisterSearchScreenSystemAction() {
        mContext.getSystemService(AccessibilityManager.class).unregisterSystemAction(
                SYSTEM_ACTION_ID_SEARCH_SCREEN);
    }

    /** Dump states. */
    public final void dump(String prefix, PrintWriter writer) {
        synchronized (mEventLogArray) {
            mEventLogArray.dump(prefix, writer);
        }
    }

    @Override
    public void close() {
        mContextualSearchPackageReceiver.unregisterReceiverSafely(mContext);
        unregisterSearchScreenSystemAction();
        SettingsCache.INSTANCE.get(mContext).unregister(SEARCH_ALL_ENTRYPOINTS_ENABLED_URI,
                mContextualSearchSettingChangedListener);
        SystemUiProxy.INSTANCE.get(mContext).removeOnStateChangeListener(mSysUiStateChangeListener);
    }

    protected final void addEventLog(String event) {
        synchronized (mEventLogArray) {
            mEventLogArray.addLog(event);
        }
    }

    protected final void addEventLog(String event, boolean extras) {
        synchronized (mEventLogArray) {
            mEventLogArray.addLog(event, extras);
        }
    }
}
