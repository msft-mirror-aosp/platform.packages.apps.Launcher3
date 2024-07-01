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

package com.android.launcher3.taskbar

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.taskbar.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.TaskbarModeRule.Mode.THREE_BUTTONS
import com.android.launcher3.taskbar.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.TaskbarModeRule.TaskbarMode
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext
import com.android.launcher3.util.NavigationMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class TaskbarModeRuleTest {

    private val context = SandboxContext(getInstrumentation().targetContext)

    @get:Rule val taskbarModeRule = TaskbarModeRule(context)

    @Test
    @TaskbarMode(TRANSIENT)
    fun testTaskbarMode_transient_overridesDisplayController() {
        assertThat(DisplayController.isTransientTaskbar(context)).isTrue()
        assertThat(DisplayController.isPinnedTaskbar(context)).isFalse()
        assertThat(DisplayController.getNavigationMode(context)).isEqualTo(NavigationMode.NO_BUTTON)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testTaskbarMode_transient_overridesDeviceProfile() {
        val dp = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context)
        assertThat(dp.isTransientTaskbar).isTrue()
        assertThat(dp.isGestureMode).isTrue()
    }

    @Test
    @TaskbarMode(PINNED)
    fun testTaskbarMode_pinned_overridesDisplayController() {
        assertThat(DisplayController.isTransientTaskbar(context)).isFalse()
        assertThat(DisplayController.isPinnedTaskbar(context)).isTrue()
        assertThat(DisplayController.getNavigationMode(context)).isEqualTo(NavigationMode.NO_BUTTON)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testTaskbarMode_pinned_overridesDeviceProfile() {
        val dp = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context)
        assertThat(dp.isTransientTaskbar).isFalse()
        assertThat(dp.isGestureMode).isTrue()
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testTaskbarMode_threeButtons_overridesDisplayController() {
        assertThat(DisplayController.isTransientTaskbar(context)).isFalse()
        assertThat(DisplayController.isPinnedTaskbar(context)).isFalse()
        assertThat(DisplayController.getNavigationMode(context))
            .isEqualTo(NavigationMode.THREE_BUTTONS)
    }

    @Test
    @TaskbarMode(THREE_BUTTONS)
    fun testTaskbarMode_threeButtons_overridesDeviceProfile() {
        val dp = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context)
        assertThat(dp.isTransientTaskbar).isFalse()
        assertThat(dp.isGestureMode).isFalse()
    }
}
