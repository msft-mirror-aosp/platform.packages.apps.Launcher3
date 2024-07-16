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
import com.android.launcher3.util.CancellableTask
import com.android.quickstep.TaskIconCache
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import org.mockito.kotlin.mock

class FakeTaskIconDataSource : TaskIconDataSource {

    val taskIdToDrawable: Map<Int, Drawable> = (0..10).associateWith { mock() }
    val taskIdToUpdatingTask: MutableMap<Int, () -> Unit> = mutableMapOf()
    var shouldLoadSynchronously: Boolean = true

    /** Retrieves and sets an icon on [task] from [taskIdToDrawable]. */
    override fun getIconInBackground(
        task: Task,
        callback: TaskIconCache.GetTaskIconCallback
    ): CancellableTask<*>? {
        val wrappedCallback = {
            callback.onTaskIconReceived(
                taskIdToDrawable.getValue(task.key.id),
                "content desc ${task.key.id}",
                "title ${task.key.id}"
            )
        }
        if (shouldLoadSynchronously) {
            wrappedCallback()
        } else {
            taskIdToUpdatingTask[task.key.id] = wrappedCallback
        }
        return null
    }
}

fun Task.assertHasIconDataFromSource(fakeTaskIconDataSource: FakeTaskIconDataSource) {
    assertThat(icon).isEqualTo(fakeTaskIconDataSource.taskIdToDrawable[key.id])
    assertThat(titleDescription).isEqualTo("content desc ${key.id}")
    assertThat(title).isEqualTo("title ${key.id}")
}
