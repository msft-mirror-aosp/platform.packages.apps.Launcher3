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

import android.animation.AnimatorTestRule
import android.view.KeyEvent
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.taskbar.rules.TaskbarModeRule
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.PINNED
import com.android.launcher3.taskbar.rules.TaskbarModeRule.Mode.TRANSIENT
import com.android.launcher3.taskbar.rules.TaskbarModeRule.TaskbarMode
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.quickstep.SystemUiProxy
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE
import com.android.wm.shell.shared.bubbles.BubbleConstants.BUBBLE_EXPANDED_SCRIM_ALPHA
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelTablet2023"])
class TaskbarScrimViewControllerTest {
    @get:Rule(order = 0)
    val context =
        TaskbarWindowSandboxContext.create { builder ->
            builder.bindSystemUiProxy(
                object : SystemUiProxy(this) {
                    override fun onBackEvent(backEvent: KeyEvent?) {
                        super.onBackEvent(backEvent)
                        backPressed = true
                    }
                }
            )
        }
    @get:Rule(order = 1) val taskbarModeRule = TaskbarModeRule(context)
    @get:Rule(order = 2) val animatorTestRule = AnimatorTestRule(this)
    @get:Rule(order = 3) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var scrimViewController: TaskbarScrimViewController

    // Default animation duration.
    private val animationDuration =
        context.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()

    private var backPressed = false

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibleChanged_onlyTaskbarVisible_noScrim() {
        getInstrumentation().runOnMainSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            scrimViewController.updateStateForSysuiFlags(0, true)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarVisibleWithBubblesExpanded_showsScrim() {
        getInstrumentation().runOnMainSync {
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            animatorTestRule.advanceTimeBy(animationDuration)
        }

        assertThat(scrimViewController.scrimAlpha).isEqualTo(BUBBLE_EXPANDED_SCRIM_ALPHA)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarHiddenDuringScrim_hidesScrim() {
        getInstrumentation().runOnMainSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(BUBBLE_EXPANDED_SCRIM_ALPHA)

        getInstrumentation().runOnMainSync {
            scrimViewController.onTaskbarVisibilityChanged(GONE)
            animatorTestRule.advanceTimeBy(animationDuration)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_notificationsOverPinnedTaskbarAndBubbles_noScrim() {
        getInstrumentation().runOnMainSync {
            scrimViewController.updateStateForSysuiFlags(
                SYSUI_STATE_BUBBLES_EXPANDED or SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                true,
            )
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnTaskbarVisibilityChanged_pinnedTaskbarWithBubbleMenu_darkerScrim() {
        getInstrumentation().runOnMainSync {
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
            scrimViewController.updateStateForSysuiFlags(
                SYSUI_STATE_BUBBLES_EXPANDED or SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED,
                true,
            )
        }
        assertThat(scrimViewController.scrimAlpha).isGreaterThan(BUBBLE_EXPANDED_SCRIM_ALPHA)
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testOnTaskbarVisibilityChanged_stashedTaskbarWithBubbles_noScrim() {
        getInstrumentation().runOnMainSync {
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
        }
        assertThat(scrimViewController.scrimAlpha).isEqualTo(0)
    }

    @Test
    @TaskbarMode(PINNED)
    fun testOnClick_scrimShown_performsSystemBack() {
        getInstrumentation().runOnMainSync {
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
        }
        assertThat(scrimViewController.scrimView.isClickable).isTrue()

        getInstrumentation().runOnMainSync { scrimViewController.scrimView.performClick() }
        assertThat(backPressed).isTrue()
    }

    @Test
    @TaskbarMode(TRANSIENT)
    fun testOnClick_scrimHidden_notClickable() {
        getInstrumentation().runOnMainSync {
            scrimViewController.updateStateForSysuiFlags(SYSUI_STATE_BUBBLES_EXPANDED, true)
            scrimViewController.onTaskbarVisibilityChanged(VISIBLE)
        }
        assertThat(scrimViewController.scrimView.isClickable).isFalse()
    }
}
