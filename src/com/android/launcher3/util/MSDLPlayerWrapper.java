/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.util;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.os.Vibrator;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;

import com.google.android.msdl.data.model.MSDLToken;
import com.google.android.msdl.domain.InteractionProperties;
import com.google.android.msdl.domain.MSDLPlayer;

import javax.inject.Inject;

/**
 * Wrapper around {@link com.google.android.msdl.domain.MSDLPlayer} to perform MSDL feedback.
 */
@LauncherAppSingleton
public class MSDLPlayerWrapper {

    public static final DaggerSingletonObject<MSDLPlayerWrapper> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getMSDLPlayerWrapper);

    /** Internal player */
    private final MSDLPlayer mMSDLPlayer;

    @Inject
    public MSDLPlayerWrapper(@ApplicationContext Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        mMSDLPlayer = MSDLPlayer.Companion.createPlayer(vibrator, UI_HELPER_EXECUTOR, null);
    }

    /** Perform MSDL feedback for a token with interaction properties */
    public void playToken(MSDLToken token, InteractionProperties properties) {
        mMSDLPlayer.playToken(token, properties);
    }

    /** Perform MSDL feedback for a token without properties */
    public void playToken(MSDLToken token) {
        mMSDLPlayer.playToken(token, null);
    }
}
