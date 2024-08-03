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

package com.android.quickstep.recents.viewmodel

import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

class RecentsViewModel(
    private val recentsTasksRepository: RecentTasksRepository,
    private val recentsViewData: RecentsViewData
) {
    fun refreshAllTaskData() {
        recentsTasksRepository.getAllTaskData(true)
    }

    fun updateVisibleTasks(visibleTaskIdList: List<Int>) {
        recentsTasksRepository.setVisibleTasks(visibleTaskIdList)
    }

    fun updateScale(scale: Float) {
        recentsViewData.scale.value = scale
    }

    fun updateFullscreenProgress(fullscreenProgress: Float) {
        recentsViewData.fullscreenProgress.value = fullscreenProgress
    }

    fun updateTasksFullyVisible(taskIds: Set<Int>) {
        recentsViewData.settledFullyVisibleTaskIds.value = taskIds
    }

    fun setOverlayEnabled(isOverlayEnabled: Boolean) {
        recentsViewData.overlayEnabled.value = isOverlayEnabled
    }

    fun setTintAmount(tintAmount: Float) {
        recentsViewData.tintAmount.value = tintAmount
    }

    fun updateThumbnailSplashProgress(taskThumbnailSplashAlpha: Float) {
        recentsViewData.thumbnailSplashProgress.value = taskThumbnailSplashAlpha
    }

    fun addOrUpdateThumbnailOverride(thumbnailOverride: Map<Int, ThumbnailData>) {
        recentsTasksRepository.addOrUpdateThumbnailOverride(thumbnailOverride)
    }

    suspend fun waitForThumbnailsToUpdate(updatedThumbnails: Map<Int, ThumbnailData>) {
        combine(
                updatedThumbnails.map {
                    recentsTasksRepository.getThumbnailById(it.key).filter { thumbnailData ->
                        thumbnailData == it.value
                    }
                }
            ) {}
            .first()
    }

    suspend fun waitForRunningTaskShowScreenshotToUpdate() {
        recentsViewData.runningTaskShowScreenshot.filter { it }.first()
    }

    fun onReset() {
        updateVisibleTasks(emptyList())
    }

    fun updateRunningTask(taskIds: Set<Int>) {
        recentsViewData.runningTaskIds.value = taskIds
    }

    fun setRunningTaskShowScreenshot(showScreenshot: Boolean) {
        recentsViewData.runningTaskShowScreenshot.value = showScreenshot
    }
}
