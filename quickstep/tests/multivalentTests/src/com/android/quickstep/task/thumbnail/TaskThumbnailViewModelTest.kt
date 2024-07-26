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

package com.android.quickstep.task.thumbnail

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.quickstep.recents.data.TaskIconQueryResponse
import com.android.quickstep.recents.usecase.GetThumbnailPositionUseCase
import com.android.quickstep.recents.usecase.ThumbnailPositionState.MatrixScaling
import com.android.quickstep.recents.usecase.ThumbnailPositionState.MissingThumbnail
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.SnapshotSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Splash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.quickstep.task.viewmodel.TaskContainerData
import com.android.quickstep.task.viewmodel.TaskThumbnailViewModel
import com.android.quickstep.task.viewmodel.TaskViewData
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TaskThumbnailViewModelTest {
    private var taskViewType = TaskViewType.SINGLE
    private val recentsViewData = RecentsViewData()
    private val taskViewData by lazy { TaskViewData(taskViewType) }
    private val taskContainerData = TaskContainerData()
    private val tasksRepository = FakeTasksRepository()
    private val mGetThumbnailPositionUseCase = mock<GetThumbnailPositionUseCase>()
    private val splashAlphaUseCase: SplashAlphaUseCase = mock()
    private val getSplashSizeUseCase: GetSplashSizeUseCase = mock()
    private val systemUnderTest by lazy {
        TaskThumbnailViewModel(
            recentsViewData,
            taskViewData,
            taskContainerData,
            tasksRepository,
            mGetThumbnailPositionUseCase,
            splashAlphaUseCase,
            getSplashSizeUseCase,
        )
    }

    private val tasks = (0..5).map(::createTaskWithId)

    @Before
    fun setUp() {
        whenever(getSplashSizeUseCase.execute(any())).thenReturn(Point())
    }

    @Test
    fun initialStateIsUninitialized() = runTest {
        assertThat(systemUnderTest.uiState.first()).isEqualTo(Uninitialized)
    }

    @Test
    fun bindRunningTask_thenStateIs_LiveTile() = runTest {
        tasksRepository.seedTasks(tasks)
        val taskThumbnail = TaskThumbnail(taskId = 1, isRunning = true)
        systemUnderTest.bind(taskThumbnail)

        assertThat(systemUnderTest.uiState.first()).isEqualTo(LiveTile)
    }

    @Test
    fun setRecentsFullscreenProgress_thenCornerRadiusProgressIsPassedThrough() = runTest {
        recentsViewData.fullscreenProgress.value = 0.5f

        assertThat(systemUnderTest.cornerRadiusProgress.first()).isEqualTo(0.5f)

        recentsViewData.fullscreenProgress.value = 0.6f

        assertThat(systemUnderTest.cornerRadiusProgress.first()).isEqualTo(0.6f)
    }

    @Test
    fun setRecentsFullscreenProgress_thenCornerRadiusProgressIsConstantForDesktop() = runTest {
        taskViewType = TaskViewType.DESKTOP
        recentsViewData.fullscreenProgress.value = 0.5f

        assertThat(systemUnderTest.cornerRadiusProgress.first()).isEqualTo(1f)

        recentsViewData.fullscreenProgress.value = 0.6f

        assertThat(systemUnderTest.cornerRadiusProgress.first()).isEqualTo(1f)
    }

    @Test
    fun setAncestorScales_thenScaleIsCalculated() = runTest {
        recentsViewData.scale.value = 0.5f
        taskViewData.scale.value = 0.6f

        assertThat(systemUnderTest.inheritedScale.first()).isEqualTo(0.3f)
    }

    @Test
    fun bindRunningTaskThenStoppedTaskWithoutThumbnail_thenStateChangesToBackgroundOnly() =
        runTest {
            tasksRepository.seedTasks(tasks)
            val runningTask = TaskThumbnail(taskId = 1, isRunning = true)
            val stoppedTask = TaskThumbnail(taskId = 2, isRunning = false)
            systemUnderTest.bind(runningTask)
            assertThat(systemUnderTest.uiState.first()).isEqualTo(LiveTile)

            systemUnderTest.bind(stoppedTask)
            assertThat(systemUnderTest.uiState.first())
                .isEqualTo(BackgroundOnly(backgroundColor = Color.rgb(2, 2, 2)))
        }

    @Test
    fun bindStoppedTaskWithoutThumbnail_thenStateIs_BackgroundOnly_withAlphaRemoved() = runTest {
        tasksRepository.seedTasks(tasks)
        val stoppedTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(stoppedTask)
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(BackgroundOnly(backgroundColor = Color.rgb(2, 2, 2)))
    }

    @Test
    fun bindLockedTaskWithThumbnail_thenStateIs_BackgroundOnly() = runTest {
        tasksRepository.seedThumbnailData(mapOf(2 to createThumbnailData()))
        tasks[2].isLocked = true
        tasksRepository.seedTasks(tasks)
        val recentTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(recentTask)
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(BackgroundOnly(backgroundColor = Color.rgb(2, 2, 2)))
    }

    @Test
    fun bindStoppedTaskWithThumbnail_thenStateIs_SnapshotSplash_withAlphaRemoved() = runTest {
        val expectedThumbnailData = createThumbnailData(rotation = Surface.ROTATION_270)
        tasksRepository.seedThumbnailData(mapOf(2 to expectedThumbnailData))
        val expectedIconData = createIconData("Task 2")
        tasksRepository.seedIconData(mapOf(2 to expectedIconData))
        tasksRepository.seedTasks(tasks)
        tasksRepository.setVisibleTasks(listOf(2))
        val recentTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(recentTask)
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(
                SnapshotSplash(
                    Snapshot(
                        backgroundColor = Color.rgb(2, 2, 2),
                        bitmap = expectedThumbnailData.thumbnail!!,
                        thumbnailRotation = Surface.ROTATION_270,
                    ),
                    Splash(expectedIconData.icon, Point())
                )
            )
    }

    @Test
    fun bindNonVisibleStoppedTask_whenMadeVisible_thenStateIsSnapshotSplash() = runTest {
        val expectedThumbnailData = createThumbnailData()
        tasksRepository.seedThumbnailData(mapOf(2 to expectedThumbnailData))
        val expectedIconData = createIconData("Task 2")
        tasksRepository.seedIconData(mapOf(2 to expectedIconData))
        tasksRepository.seedTasks(tasks)
        val recentTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(recentTask)
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(BackgroundOnly(backgroundColor = Color.rgb(2, 2, 2)))
        tasksRepository.setVisibleTasks(listOf(2))
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(
                SnapshotSplash(
                    Snapshot(
                        backgroundColor = Color.rgb(2, 2, 2),
                        bitmap = expectedThumbnailData.thumbnail!!,
                        thumbnailRotation = Surface.ROTATION_0,
                    ),
                    Splash(expectedIconData.icon, Point())
                )
            )
    }

    @Test
    fun bindStoppedTask_thenStateContainsSplashSizeFromUseCase() = runTest {
        val expectedSplashSize = Point(100, 150)
        whenever(getSplashSizeUseCase.execute(any())).thenReturn(expectedSplashSize)
        val expectedThumbnailData = createThumbnailData(rotation = Surface.ROTATION_270)
        tasksRepository.seedThumbnailData(mapOf(2 to expectedThumbnailData))
        val expectedIconData = createIconData("Task 2")
        tasksRepository.seedIconData(mapOf(2 to expectedIconData))
        tasksRepository.seedTasks(tasks)
        tasksRepository.setVisibleTasks(listOf(2))
        val recentTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(recentTask)
        val uiState = systemUnderTest.uiState.first() as SnapshotSplash
        assertThat(uiState.splash.size).isEqualTo(expectedSplashSize)
    }

    @Test
    fun getSnapshotMatrix_MissingThumbnail() = runTest {
        val taskId = 2
        val recentTask = TaskThumbnail(taskId = taskId, isRunning = false)
        val isRtl = true

        whenever(mGetThumbnailPositionUseCase.run(taskId, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .thenReturn(MissingThumbnail)

        systemUnderTest.bind(recentTask)
        assertThat(systemUnderTest.getThumbnailPositionState(CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .isEqualTo(Matrix.IDENTITY_MATRIX)
    }

    @Test
    fun getSnapshotMatrix_MatrixScaling() = runTest {
        val taskId = 2
        val recentTask = TaskThumbnail(taskId = taskId, isRunning = false)
        val isRtl = true

        whenever(mGetThumbnailPositionUseCase.run(taskId, CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .thenReturn(MatrixScaling(MATRIX, isRotated = false))

        systemUnderTest.bind(recentTask)
        assertThat(systemUnderTest.getThumbnailPositionState(CANVAS_WIDTH, CANVAS_HEIGHT, isRtl))
            .isEqualTo(MATRIX)
    }

    @Test
    fun getForegroundScrimDimProgress_returnsForegroundMaxScrim() = runTest {
        recentsViewData.tintAmount.value = 0.32f
        taskContainerData.taskMenuOpenProgress.value = 0f
        assertThat(systemUnderTest.dimProgress.first()).isEqualTo(0.32f)
    }

    @Test
    fun getTaskMenuScrimDimProgress_returnsTaskMenuScrim() = runTest {
        recentsViewData.tintAmount.value = 0f
        taskContainerData.taskMenuOpenProgress.value = 1f
        assertThat(systemUnderTest.dimProgress.first()).isEqualTo(0.4f)
    }

    @Test
    fun getForegroundScrimDimProgress_returnsNoScrim() = runTest {
        recentsViewData.tintAmount.value = 0f
        taskContainerData.taskMenuOpenProgress.value = 0f
        assertThat(systemUnderTest.dimProgress.first()).isEqualTo(0f)
    }

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
            colorBackground = Color.argb(taskId, taskId, taskId, taskId)
        }

    private fun createThumbnailData(rotation: Int = Surface.ROTATION_0): ThumbnailData {
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(THUMBNAIL_WIDTH)
        whenever(bitmap.height).thenReturn(THUMBNAIL_HEIGHT)

        return ThumbnailData(thumbnail = bitmap, rotation = rotation)
    }

    private fun createIconData(title: String) = TaskIconQueryResponse(mock<Drawable>(), "", title)

    companion object {
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200
        const val CANVAS_WIDTH = 300
        const val CANVAS_HEIGHT = 600
        val MATRIX =
            Matrix().apply {
                setValues(floatArrayOf(2.3f, 4.5f, 2.6f, 7.4f, 3.4f, 2.3f, 2.5f, 6.0f, 3.4f))
            }
    }
}
