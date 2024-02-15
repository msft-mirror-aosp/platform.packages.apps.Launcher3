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

package com.android.launcher3.allapps;

import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.PrivateProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.PrivateProfileManager.STATE_ENABLED;
import static com.android.launcher3.allapps.PrivateProfileManager.STATE_TRANSITION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_LOCK_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_SETTINGS_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNLOCK_TAP;

import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.allapps.UserProfileManager.UserProfileState;

/**
 * Controller which returns views to be added to Private Space Header based upon
 * {@link UserProfileState}
 */
public class PrivateSpaceHeaderViewController {
    private static final int ANIMATION_DURATION = 2000;
    private final ActivityAllAppsContainerView mAllApps;
    private final PrivateProfileManager mPrivateProfileManager;

    public PrivateSpaceHeaderViewController(ActivityAllAppsContainerView allApps,
            PrivateProfileManager privateProfileManager) {
        this.mAllApps = allApps;
        this.mPrivateProfileManager = privateProfileManager;
    }

    /** Add Private Space Header view elements based upon {@link UserProfileState} */
    public void addPrivateSpaceHeaderViewElements(RelativeLayout parent) {
        //Add quietMode image and action for lock/unlock button
        ImageButton quietModeButton = parent.findViewById(R.id.ps_lock_unlock_button);
        assert quietModeButton != null;
        addQuietModeButton(quietModeButton);

        //Trigger lock/unlock action from header.
        addHeaderOnClickListener(parent);

        //Add image and action for private space settings button
        ImageButton settingsButton = parent.findViewById(R.id.ps_settings_button);
        assert settingsButton != null;
        addPrivateSpaceSettingsButton(settingsButton);

        //Add image for private space transitioning view
        ImageView transitionView = parent.findViewById(R.id.ps_transition_image);
        assert transitionView != null;
        addTransitionImage(transitionView);
    }

    private void addQuietModeButton(ImageButton quietModeButton) {
        switch (mPrivateProfileManager.getCurrentState()) {
            case STATE_ENABLED -> {
                quietModeButton.setVisibility(View.VISIBLE);
                quietModeButton.setImageResource(R.drawable.bg_ps_lock_button);
                quietModeButton.setOnClickListener(view -> lockAction());
            }
            case STATE_DISABLED -> {
                quietModeButton.setVisibility(View.VISIBLE);
                quietModeButton.setImageResource(R.drawable.bg_ps_unlock_button);
                quietModeButton.setOnClickListener(view -> unLockAction());
            }
            default -> quietModeButton.setVisibility(View.GONE);
        }
    }

    private void addHeaderOnClickListener(RelativeLayout header) {
        if (mPrivateProfileManager.getCurrentState() == STATE_DISABLED) {
            header.setOnClickListener(view -> unLockAction());
        } else {
            header.setOnClickListener(null);
        }
    }

    private void unLockAction() {
        mPrivateProfileManager.logEvents(LAUNCHER_PRIVATE_SPACE_UNLOCK_TAP);
        mPrivateProfileManager.unlockPrivateProfile((this::onPrivateProfileUnlocked));
    }

    private void lockAction() {
        mPrivateProfileManager.logEvents(LAUNCHER_PRIVATE_SPACE_LOCK_TAP);
        mPrivateProfileManager.lockPrivateProfile();
    }

    private void addPrivateSpaceSettingsButton(ImageButton settingsButton) {
        if (mPrivateProfileManager.getCurrentState() == STATE_ENABLED
                && mPrivateProfileManager.isPrivateSpaceSettingsAvailable()) {
            settingsButton.setVisibility(View.VISIBLE);
            settingsButton.setOnClickListener(
                    view -> {
                        mPrivateProfileManager.logEvents(LAUNCHER_PRIVATE_SPACE_SETTINGS_TAP);
                        mPrivateProfileManager.openPrivateSpaceSettings();
                    });
        } else {
            settingsButton.setVisibility(View.GONE);
        }
    }

    private void addTransitionImage(ImageView transitionImage) {
        if (mPrivateProfileManager.getCurrentState() == STATE_TRANSITION) {
            transitionImage.setVisibility(View.VISIBLE);
        } else {
            transitionImage.setVisibility(View.GONE);
        }
    }

    private void onPrivateProfileUnlocked() {
        // If we are on main adapter view, we apply the PS Container expansion animation and
        // then scroll down to load the entire container, making animation visible.
        ActivityAllAppsContainerView<?>.AdapterHolder mainAdapterHolder =
                (ActivityAllAppsContainerView<?>.AdapterHolder) mAllApps.mAH.get(MAIN);
        if (Flags.enablePrivateSpace() && Flags.privateSpaceAnimation()
                && mAllApps.getActiveRecyclerView() == mainAdapterHolder.mRecyclerView) {
            mAllApps.getActiveRecyclerView().scrollToBottomWithMotion(ANIMATION_DURATION);
        }
    }

    PrivateProfileManager getPrivateProfileManager() {
        return mPrivateProfileManager;
    }
}
