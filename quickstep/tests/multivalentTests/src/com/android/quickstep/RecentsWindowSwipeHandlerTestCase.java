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

package com.android.quickstep;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.launcher3.util.LauncherMultivalentJUnit;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.fallback.window.RecentsWindowSwipeHandler;
import com.android.quickstep.views.RecentsViewContainer;

import org.junit.runner.RunWith;
import org.mockito.Mock;

@SmallTest
@RunWith(LauncherMultivalentJUnit.class)
public class RecentsWindowSwipeHandlerTestCase extends AbsSwipeUpHandlerTestCase<
        RecentsState,
        RecentsWindowManager,
        FallbackRecentsView<RecentsWindowManager>,
        RecentsWindowSwipeHandler,
        FallbackWindowInterface> {

    @Mock private RecentsWindowManager mRecentsWindowManager;
    @Mock private FallbackRecentsView<RecentsWindowManager> mRecentsView;

    @NonNull
    @Override
    protected RecentsWindowSwipeHandler createSwipeHandler(long touchTimeMs,
            boolean continuingLastGesture) {
        return new RecentsWindowSwipeHandler(
                mContext,
                mRecentsAnimationDeviceState,
                mTaskAnimationManager,
                mGestureState,
                touchTimeMs,
                continuingLastGesture,
                mInputConsumerController,
                mRecentsWindowManager);
    }

    @Nullable
    @Override
    protected RecentsWindowManager getRecentsWindowManager() {
        return mRecentsWindowManager;
    }

    @NonNull
    @Override
    protected RecentsViewContainer getRecentsContainer() {
        return mRecentsWindowManager;
    }

    @NonNull
    @Override
    protected FallbackRecentsView<RecentsWindowManager> getRecentsView() {
        return mRecentsView;
    }
}
