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

package com.android.quickstep

import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.TaskOverlayFactory.TaskOverlay
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.views.LauncherRecentsView
import com.android.quickstep.views.TaskContainer
import com.android.quickstep.views.TaskThumbnailViewDeprecated
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskViewIcon
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/** Test for [ExternalDisplaySystemShortcut] */
class ExternalDisplaySystemShortcutTest {

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val launcher: QuickstepLauncher = mock()
    private val statsLogManager: StatsLogManager = mock()
    private val statsLogger: StatsLogManager.StatsLogger = mock()
    private val recentsView: LauncherRecentsView = mock()
    private val taskView: TaskView = mock()
    private val abstractFloatingViewHelper: AbstractFloatingViewHelper = mock()
    private val overlayFactory: TaskOverlayFactory = mock()
    private val factory: TaskShortcutFactory =
        ExternalDisplaySystemShortcut.createFactory(abstractFloatingViewHelper)

    private lateinit var mockitoSession: StaticMockitoSession

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(DesktopModeStatus::class.java)
                .startMocking()
        whenever(DesktopModeStatus.canEnterDesktopMode(any())).thenReturn(true)
        whenever(overlayFactory.createOverlay(any())).thenReturn(mock<TaskOverlay<*>>())
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    @Test
    @EnableFlags(Flags.FLAG_MOVE_TO_EXTERNAL_DISPLAY_SHORTCUT)
    fun createExternalDisplayTaskShortcut_desktopModeDisabled() {
        `when`(DesktopModeStatus.canEnterDesktopMode(any())).thenReturn(false)

        val taskContainer = createTaskContainer(createTask())

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_MOVE_TO_EXTERNAL_DISPLAY_SHORTCUT)
    fun externalDisplaySystemShortcutClicked() {
        val task = createTask()
        val taskContainer = spy(createTaskContainer(task))

        whenever(launcher.getOverviewPanel<LauncherRecentsView>()).thenReturn(recentsView)
        whenever(launcher.statsLogManager).thenReturn(statsLogManager)
        whenever(statsLogManager.logger()).thenReturn(statsLogger)
        whenever(statsLogger.withItemInfo(any())).thenReturn(statsLogger)
        whenever(recentsView.moveTaskToExternalDisplay(any(), any())).thenAnswer {
            val successCallback = it.getArgument<Runnable>(1)
            successCallback.run()
        }
        val taskViewItemInfo = mock<TaskViewItemInfo>()
        doReturn(taskViewItemInfo).whenever(taskContainer).itemInfo

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).hasSize(1)
        assertThat(shortcuts!!.first()).isInstanceOf(ExternalDisplaySystemShortcut::class.java)

        val externalDisplayShortcut = shortcuts.first() as ExternalDisplaySystemShortcut

        externalDisplayShortcut.onClick(taskView)

        val allTypesExceptRebindSafe =
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv()
        verify(abstractFloatingViewHelper).closeOpenViews(launcher, true, allTypesExceptRebindSafe)
        verify(recentsView).moveTaskToExternalDisplay(eq(taskContainer), any())
        verify(statsLogger).withItemInfo(taskViewItemInfo)
        verify(statsLogger).log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_EXTERNAL_DISPLAY_TAP)
    }

    private fun createTask() = Task(TaskKey(1, 0, Intent(), ComponentName("", ""), 0, 2000))

    private fun createTaskContainer(task: Task) =
        TaskContainer(
            taskView,
            task,
            if (enableRefactorTaskThumbnail()) mock<TaskThumbnailView>()
            else mock<TaskThumbnailViewDeprecated>(),
            mock<TaskViewIcon>(),
            mock<TransformingTouchDelegate>(),
            SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
            digitalWellBeingToast = null,
            showWindowsView = null,
            overlayFactory,
        )
}
