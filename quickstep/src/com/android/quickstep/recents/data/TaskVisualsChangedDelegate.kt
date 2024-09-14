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

import android.os.UserHandle
import com.android.quickstep.TaskThumbnailCache.HighResLoadingState.HighResLoadingStateChangedCallback
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate.TaskIconChangedCallback
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate.TaskThumbnailChangedCallback
import com.android.quickstep.util.TaskVisualsChangeListener
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData

/** Delegates the checking of task visuals (thumbnails, high res changes, icons) */
interface TaskVisualsChangedDelegate :
    TaskVisualsChangeListener, HighResLoadingStateChangedCallback {
    /** Registers a callback for visuals relating to icons */
    fun registerTaskIconChangedCallback(
        taskKey: Task.TaskKey,
        taskIconChangedCallback: TaskIconChangedCallback
    )

    /** Unregisters a callback for visuals relating to icons */
    fun unregisterTaskIconChangedCallback(taskKey: Task.TaskKey)

    /** Registers a callback for visuals relating to thumbnails */
    fun registerTaskThumbnailChangedCallback(
        taskKey: Task.TaskKey,
        taskThumbnailChangedCallback: TaskThumbnailChangedCallback
    )

    /** Unregisters a callback for visuals relating to thumbnails */
    fun unregisterTaskThumbnailChangedCallback(taskKey: Task.TaskKey)

    /** A callback for task icon changes */
    interface TaskIconChangedCallback {
        /** Informs the listener that the task icon has changed */
        fun onTaskIconChanged()
    }

    /** A callback for task thumbnail changes */
    interface TaskThumbnailChangedCallback {
        /** Informs the listener that the task thumbnail data has changed to [thumbnailData] */
        fun onTaskThumbnailChanged(thumbnailData: ThumbnailData?)

        /** Informs the listener that the default resolution for loading thumbnails has changed */
        fun onHighResLoadingStateChanged()
    }
}

class TaskVisualsChangedDelegateImpl(
    private val taskVisualsChangeNotifier: TaskVisualsChangeNotifier,
    private val highResLoadingStateNotifier: HighResLoadingStateNotifier,
) : TaskVisualsChangedDelegate {
    private val taskIconChangedCallbacks =
        mutableMapOf<Int, Pair<Task.TaskKey, TaskIconChangedCallback>>()
    private val taskThumbnailChangedCallbacks =
        mutableMapOf<Int, Pair<Task.TaskKey, TaskThumbnailChangedCallback>>()
    private var isListening = false

    @Synchronized
    private fun onCallbackRegistered() {
        if (isListening) return

        taskVisualsChangeNotifier.addThumbnailChangeListener(this)
        highResLoadingStateNotifier.addCallback(this)
        isListening = true
    }

    @Synchronized
    private fun onCallbackUnregistered() {
        if (!isListening) return

        if (taskIconChangedCallbacks.size + taskThumbnailChangedCallbacks.size == 0) {
            taskVisualsChangeNotifier.removeThumbnailChangeListener(this)
            highResLoadingStateNotifier.removeCallback(this)
        }

        isListening = false
    }

    override fun onTaskIconChanged(taskId: Int) {
        taskIconChangedCallbacks[taskId]?.let { (_, callback) -> callback.onTaskIconChanged() }
    }

    override fun onTaskIconChanged(pkg: String, user: UserHandle) {
        taskIconChangedCallbacks.values
            .filter { (taskKey, _) ->
                pkg == taskKey.packageName && user.identifier == taskKey.userId
            }
            .forEach { (_, callback) -> callback.onTaskIconChanged() }
    }

    override fun onTaskThumbnailChanged(taskId: Int, thumbnailData: ThumbnailData?): Task? {
        taskThumbnailChangedCallbacks[taskId]?.let { (_, callback) ->
            callback.onTaskThumbnailChanged(thumbnailData)
        }
        return null
    }

    override fun onHighResLoadingStateChanged(enabled: Boolean) {
        taskThumbnailChangedCallbacks.values.forEach { (_, callback) ->
            callback.onHighResLoadingStateChanged()
        }
    }

    override fun registerTaskIconChangedCallback(
        taskKey: Task.TaskKey,
        taskIconChangedCallback: TaskIconChangedCallback
    ) {
        taskIconChangedCallbacks[taskKey.id] = taskKey to taskIconChangedCallback
        onCallbackRegistered()
    }

    override fun unregisterTaskIconChangedCallback(taskKey: Task.TaskKey) {
        taskIconChangedCallbacks.remove(taskKey.id)
        onCallbackUnregistered()
    }

    override fun registerTaskThumbnailChangedCallback(
        taskKey: Task.TaskKey,
        taskThumbnailChangedCallback: TaskThumbnailChangedCallback
    ) {
        taskThumbnailChangedCallbacks[taskKey.id] = taskKey to taskThumbnailChangedCallback
        onCallbackRegistered()
    }

    override fun unregisterTaskThumbnailChangedCallback(taskKey: Task.TaskKey) {
        taskThumbnailChangedCallbacks.remove(taskKey.id)
        onCallbackUnregistered()
    }
}