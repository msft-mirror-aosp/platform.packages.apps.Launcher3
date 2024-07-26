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

package com.android.quickstep.task.viewmodel

import android.annotation.ColorInt
import android.graphics.Matrix
import android.graphics.Point
import androidx.core.graphics.ColorUtils
import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.quickstep.recents.usecase.GetThumbnailPositionUseCase
import com.android.quickstep.recents.usecase.ThumbnailPositionState
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.thumbnail.GetSplashSizeUseCase
import com.android.quickstep.task.thumbnail.SplashAlphaUseCase
import com.android.quickstep.task.thumbnail.TaskThumbnail
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.SnapshotSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Splash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.systemui.shared.recents.model.Task
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class TaskThumbnailViewModel(
    recentsViewData: RecentsViewData,
    taskViewData: TaskViewData,
    taskContainerData: TaskContainerData,
    private val tasksRepository: RecentTasksRepository,
    private val getThumbnailPositionUseCase: GetThumbnailPositionUseCase,
    private val splashAlphaUseCase: SplashAlphaUseCase,
    private val getSplashSizeUseCase: GetSplashSizeUseCase,
) {
    private val task = MutableStateFlow<Flow<Task?>>(flowOf(null))
    private val splashProgress = MutableStateFlow(flowOf(0f))
    private lateinit var taskThumbnail: TaskThumbnail

    /**
     * Progress for changes in corner radius. progress: 0 = overview corner radius; 1 = fullscreen
     * corner radius.
     */
    val cornerRadiusProgress =
        if (taskViewData.isOutlineFormedByThumbnailView) recentsViewData.fullscreenProgress
        else MutableStateFlow(1f).asStateFlow()
    val inheritedScale =
        combine(recentsViewData.scale, taskViewData.scale) { recentsScale, taskScale ->
            recentsScale * taskScale
        }
    val dimProgress: Flow<Float> =
        combine(taskContainerData.taskMenuOpenProgress, recentsViewData.tintAmount) {
            taskMenuOpenProgress,
            tintAmount ->
            max(taskMenuOpenProgress * MAX_SCRIM_ALPHA, tintAmount)
        }
    val splashAlpha = splashProgress.flatMapLatest { it }
    val uiState: Flow<TaskThumbnailUiState> =
        task
            .flatMapLatest { taskFlow ->
                taskFlow.map { taskVal ->
                    when {
                        taskVal == null -> Uninitialized
                        taskThumbnail.isRunning -> LiveTile
                        isBackgroundOnly(taskVal) ->
                            BackgroundOnly(taskVal.colorBackground.removeAlpha())
                        isSnapshotSplashState(taskVal) ->
                            SnapshotSplash(createSnapshotState(taskVal), createSplashState(taskVal))
                        else -> Uninitialized
                    }
                }
            }
            .distinctUntilChanged()

    fun bind(taskThumbnail: TaskThumbnail) {
        this.taskThumbnail = taskThumbnail
        task.value = tasksRepository.getTaskDataById(taskThumbnail.taskId)
        splashProgress.value = splashAlphaUseCase.execute(taskThumbnail.taskId)
    }

    fun getThumbnailPositionState(width: Int, height: Int, isRtl: Boolean): Matrix {
        return runBlocking {
            when (
                val thumbnailPositionState =
                    getThumbnailPositionUseCase.run(taskThumbnail.taskId, width, height, isRtl)
            ) {
                is ThumbnailPositionState.MatrixScaling -> thumbnailPositionState.matrix
                is ThumbnailPositionState.MissingThumbnail -> Matrix.IDENTITY_MATRIX
            }
        }
    }

    private fun isBackgroundOnly(task: Task): Boolean = task.isLocked || task.thumbnail == null

    private fun isSnapshotSplashState(task: Task): Boolean {
        val thumbnailPresent = task.thumbnail?.thumbnail != null
        val taskLocked = task.isLocked

        return thumbnailPresent && !taskLocked
    }

    private fun createSnapshotState(task: Task): Snapshot {
        val thumbnailData = task.thumbnail
        val bitmap = thumbnailData?.thumbnail!!
        return Snapshot(bitmap, thumbnailData.rotation, task.colorBackground.removeAlpha())
    }

    private fun createSplashState(task: Task): Splash {
        val taskIcon = task.icon
        val size = if (taskIcon == null) Point() else getSplashSizeUseCase.execute(taskIcon)
        return Splash(taskIcon, size)
    }

    @ColorInt private fun Int.removeAlpha(): Int = ColorUtils.setAlphaComponent(this, 0xff)

    private companion object {
        const val MAX_SCRIM_ALPHA = 0.4f
    }
}
