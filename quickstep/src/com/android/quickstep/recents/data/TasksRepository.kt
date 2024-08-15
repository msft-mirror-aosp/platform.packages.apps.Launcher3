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

package com.android.quickstep.recents.data

import android.graphics.drawable.Drawable
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.quickstep.util.GroupTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class TasksRepository(
    private val recentsModel: RecentTasksDataSource,
    private val taskThumbnailDataSource: TaskThumbnailDataSource,
    private val taskIconDataSource: TaskIconDataSource,
    recentsCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : RecentTasksRepository {
    private val groupedTaskData = MutableStateFlow(emptyList<GroupTask>())
    private val visibleTaskIds = MutableStateFlow(emptySet<Int>())
    private val thumbnailOverride = MutableStateFlow(mapOf<Int, ThumbnailData>())

    private val taskData =
        groupedTaskData.map { groupTaskList -> groupTaskList.flatMap { it.tasks } }
    private val visibleTasks =
        combine(taskData, visibleTaskIds) { tasks, visibleIds ->
            tasks.filter { it.key.id in visibleIds }
        }

    private val iconQueryResults: Flow<Map<Int, TaskIconQueryResponse?>> =
        visibleTasks
            .map { visibleTasksList -> visibleTasksList.map(::getIconDataRequest) }
            .flatMapLatest { iconRequestFlows: List<IconDataRequest> ->
                if (iconRequestFlows.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(iconRequestFlows) { it.toMap() }
                }
            }
            .distinctUntilChanged()

    private val thumbnailQueryResults: Flow<Map<Int, ThumbnailData?>> =
        visibleTasks
            .map { visibleTasksList -> visibleTasksList.map(::getThumbnailDataRequest) }
            .flatMapLatest { thumbnailRequestFlows: List<ThumbnailDataRequest> ->
                if (thumbnailRequestFlows.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(thumbnailRequestFlows) { it.toMap() }
                }
            }
            .distinctUntilChanged()

    private val augmentedTaskData: Flow<List<Task>> =
        combine(taskData, thumbnailQueryResults, iconQueryResults, thumbnailOverride) {
                tasks,
                thumbnailQueryResults,
                iconQueryResults,
                thumbnailOverride ->
                tasks.onEach { task ->
                    // Add retrieved thumbnails + remove unnecessary thumbnails (e.g. invisible)
                    task.thumbnail =
                        thumbnailOverride[task.key.id] ?: thumbnailQueryResults[task.key.id]

                    // TODO(b/352331675) don't load icons for DesktopTaskView
                    // Add retrieved icons + remove unnecessary icons
                    val iconQueryResult = iconQueryResults[task.key.id]
                    task.icon = iconQueryResult?.icon
                    task.titleDescription = iconQueryResult?.contentDescription
                    task.title = iconQueryResult?.title
                }
            }
            .flowOn(dispatcherProvider.io)
            .shareIn(recentsCoroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    override fun getAllTaskData(forceRefresh: Boolean): Flow<List<Task>> {
        if (forceRefresh) {
            recentsModel.getTasks { groupedTaskData.value = it }
        }
        return augmentedTaskData
    }

    override fun getTaskDataById(taskId: Int): Flow<Task?> =
        augmentedTaskData.map { taskList -> taskList.firstOrNull { it.key.id == taskId } }

    override fun getThumbnailById(taskId: Int): Flow<ThumbnailData?> =
        getTaskDataById(taskId).map { it?.thumbnail }.distinctUntilChangedBy { it?.snapshotId }

    override fun setVisibleTasks(visibleTaskIdList: List<Int>) {
        this.visibleTaskIds.value = visibleTaskIdList.toSet()
        addOrUpdateThumbnailOverride(emptyMap())
    }

    override fun addOrUpdateThumbnailOverride(thumbnailOverride: Map<Int, ThumbnailData>) {
        this.thumbnailOverride.value =
            this.thumbnailOverride.value
                .toMutableMap()
                .apply { putAll(thumbnailOverride) }
                .filterKeys(this.visibleTaskIds.value::contains)
    }

    /** Flow wrapper for [TaskThumbnailDataSource.getThumbnailInBackground] api */
    private fun getThumbnailDataRequest(task: Task): ThumbnailDataRequest = flow {
        emit(task.key.id to task.thumbnail)
        val thumbnailDataResult: ThumbnailData? =
            withContext(dispatcherProvider.main) {
                suspendCancellableCoroutine { continuation ->
                    val cancellableTask =
                        taskThumbnailDataSource.getThumbnailInBackground(task) {
                            continuation.resume(it)
                        }
                    continuation.invokeOnCancellation { cancellableTask?.cancel() }
                }
            }
        emit(task.key.id to thumbnailDataResult)
    }

    /** Flow wrapper for [TaskThumbnailDataSource.getThumbnailInBackground] api */
    private fun getIconDataRequest(task: Task): IconDataRequest =
        flow {
                emit(task.key.id to task.getTaskIconQueryResponse())
                val iconDataResponse: TaskIconQueryResponse? =
                    withContext(dispatcherProvider.main) {
                        suspendCancellableCoroutine { continuation ->
                            val cancellableTask =
                                taskIconDataSource.getIconInBackground(task) {
                                    icon,
                                    contentDescription,
                                    title ->
                                    icon.constantState?.let {
                                        continuation.resume(
                                            TaskIconQueryResponse(
                                                it.newDrawable().mutate(),
                                                contentDescription,
                                                title
                                            )
                                        )
                                    }
                                }
                            continuation.invokeOnCancellation { cancellableTask?.cancel() }
                        }
                    }
                emit(task.key.id to iconDataResponse)
            }
            .distinctUntilChanged()
}

data class TaskIconQueryResponse(
    val icon: Drawable,
    val contentDescription: String,
    val title: String
)

private fun Task.getTaskIconQueryResponse(): TaskIconQueryResponse? {
    val iconVal = icon ?: return null
    val titleDescriptionVal = titleDescription ?: return null
    val titleVal = title ?: return null

    return TaskIconQueryResponse(iconVal, titleDescriptionVal, titleVal)
}

private typealias ThumbnailDataRequest = Flow<Pair<Int, ThumbnailData?>>

private typealias IconDataRequest = Flow<Pair<Int, TaskIconQueryResponse?>>
