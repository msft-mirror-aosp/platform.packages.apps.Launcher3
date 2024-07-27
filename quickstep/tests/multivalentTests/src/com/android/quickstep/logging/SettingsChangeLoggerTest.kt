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

package com.android.quickstep.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.THEMED_ICONS
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.logging.InstanceId
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ADD_NEW_APPS_TO_HOME_SCREEN_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALL_APPS_SUGGESTIONS_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_ROTATION_DISABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_ROTATION_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DOT_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_THEMED_ICON_DISABLED
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SettingsChangeLoggerTest {
    private val mContext: Context = ApplicationProvider.getApplicationContext()

    private val mInstanceId = InstanceId.fakeInstanceId(1)

    private lateinit var mSystemUnderTest: SettingsChangeLogger

    @Mock private lateinit var mStatsLogManager: StatsLogManager

    @Mock private lateinit var mMockLogger: StatsLogManager.StatsLogger

    @Captor private lateinit var mEventCaptor: ArgumentCaptor<StatsLogManager.EventEnum>

    private var mDefaultThemedIcons = false

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        whenever(mStatsLogManager.logger()).doReturn(mMockLogger)
        whenever(mStatsLogManager.logger().withInstanceId(any())).doReturn(mMockLogger)
        mDefaultThemedIcons = LauncherPrefs.get(mContext).get(THEMED_ICONS)
        // To match the default value of THEMED_ICONS
        LauncherPrefs.get(mContext).put(THEMED_ICONS, false)

        mSystemUnderTest = SettingsChangeLogger(mContext, mStatsLogManager)
    }

    @After
    fun tearDown() {
        LauncherPrefs.get(mContext).put(THEMED_ICONS, mDefaultThemedIcons)
        mSystemUnderTest.close()
    }

    @Test
    fun loggingPrefs_correctDefaultValue() {
        assertThat(mSystemUnderTest.loggingPrefs["pref_allowRotation"]!!.defaultValue).isFalse()
        assertThat(mSystemUnderTest.loggingPrefs["pref_add_icon_to_home"]!!.defaultValue).isTrue()
        assertThat(mSystemUnderTest.loggingPrefs["pref_overview_action_suggestions"]!!.defaultValue)
            .isTrue()
        assertThat(mSystemUnderTest.loggingPrefs["pref_smartspace_home_screen"]!!.defaultValue)
            .isTrue()
        assertThat(mSystemUnderTest.loggingPrefs["pref_enable_minus_one"]!!.defaultValue).isTrue()
    }

    @Test
    fun logSnapshot_defaultValue() {
        mSystemUnderTest.logSnapshot(mInstanceId)

        verify(mMockLogger, atLeastOnce()).log(mEventCaptor.capture())
        val capturedEvents = mEventCaptor.allValues
        assertThat(capturedEvents.isNotEmpty()).isTrue()
        verifyDefaultEvent(capturedEvents)
        // pref_allowRotation false
        assertThat(capturedEvents.any { it.id == LAUNCHER_HOME_SCREEN_ROTATION_DISABLED.id })
            .isTrue()
    }

    @Test
    fun logSnapshot_updateValue() {
        LauncherPrefs.get(mContext)
            .put(
                item =
                    backedUpItem(
                        sharedPrefKey = "pref_allowRotation",
                        defaultValue = false,
                    ),
                value = true
            )

        mSystemUnderTest.logSnapshot(mInstanceId)

        verify(mMockLogger, atLeastOnce()).log(mEventCaptor.capture())
        val capturedEvents = mEventCaptor.allValues
        assertThat(capturedEvents.isNotEmpty()).isTrue()
        verifyDefaultEvent(capturedEvents)
        assertThat(capturedEvents.any { it.id == LAUNCHER_HOME_SCREEN_ROTATION_ENABLED.id })
            .isTrue()
    }

    private fun verifyDefaultEvent(capturedEvents: MutableList<StatsLogManager.EventEnum>) {
        assertThat(capturedEvents.any { it.id == LAUNCHER_NOTIFICATION_DOT_ENABLED.id }).isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_THEMED_ICON_DISABLED.id }).isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_ADD_NEW_APPS_TO_HOME_SCREEN_ENABLED.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_ALL_APPS_SUGGESTIONS_ENABLED.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED.id })
            .isTrue()
        // LAUNCHER_GOOGLE_APP_SWIPE_LEFT_ENABLED
        assertThat(capturedEvents.any { it.id == 617 }).isTrue()
    }
}
