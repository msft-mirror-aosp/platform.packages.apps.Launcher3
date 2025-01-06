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

package com.android.quickstep.views

import android.view.View
import androidx.core.view.children
import com.android.launcher3.Flags.enableLargeDesktopWindowingTile
import com.android.quickstep.util.GroupTask
import com.android.quickstep.views.RecentsView.RUNNING_TASK_ATTACH_ALPHA
import com.android.systemui.shared.recents.model.ThumbnailData
import java.util.function.BiConsumer

/**
 * Helper class for [RecentsView]. This util class contains refactored and extracted functions from
 * RecentsView to facilitate the implementation of unit tests.
 */
class RecentsViewUtils(private val recentsView: RecentsView<*, *>) {
    val taskViews = TaskViewsIterable(recentsView)

    /** Takes a screenshot of all [taskView] and return map of taskId to the screenshot */
    fun screenshotTasks(taskView: TaskView): Map<Int, ThumbnailData> {
        val recentsAnimationController = recentsView.recentsAnimationController ?: return emptyMap()
        return taskView.taskContainers.associate {
            it.task.key.id to recentsAnimationController.screenshotTask(it.task.key.id)
        }
    }

    /**
     * Sorts task groups to move desktop tasks to the end of the list.
     *
     * @param tasks List of group tasks to be sorted.
     * @return Sorted list of GroupTasks to be used in the RecentsView.
     */
    fun sortDesktopTasksToFront(tasks: List<GroupTask>): List<GroupTask> {
        val (desktopTasks, otherTasks) = tasks.partition { it.taskViewType == TaskViewType.DESKTOP }
        return otherTasks + desktopTasks
    }

    class TaskViewsIterable(val recentsView: RecentsView<*, *>) : Iterable<TaskView> {
        /** Iterates TaskViews when its index inside the RecentsView is needed. */
        fun forEachWithIndexInParent(consumer: BiConsumer<Int, TaskView>) {
            recentsView.children.forEachIndexed { index, child ->
                (child as? TaskView)?.let { consumer.accept(index, it) }
            }
        }

        override fun iterator(): Iterator<TaskView> =
            recentsView.children.mapNotNull { it as? TaskView }.iterator()
    }

    /** Counts [TaskView]s that are [DesktopTaskView] instances. */
    fun getDesktopTaskViewCount(): Int = taskViews.count { it is DesktopTaskView }

    /** Returns a list of all large TaskView Ids from [TaskView]s */
    fun getLargeTaskViewIds(): List<Int> = taskViews.filter { it.isLargeTile }.map { it.taskViewId }

    /** Counts [TaskView]s that are large tiles. */
    fun getLargeTileCount(): Int = taskViews.count { it.isLargeTile }

    /** Returns the first TaskView that should be displayed as a large tile. */
    fun getFirstLargeTaskView(): TaskView? =
        taskViews.firstOrNull {
            it.isLargeTile && !(recentsView.isSplitSelectionActive && it is DesktopTaskView)
        }

    /** Returns the expected focus task. */
    fun getExpectedFocusedTask(): TaskView? =
        if (enableLargeDesktopWindowingTile()) taskViews.firstOrNull { it !is DesktopTaskView }
        else taskViews.firstOrNull()

    /**
     * Returns the [TaskView] that should be the current page during task binding, in the following
     * priorities:
     * 1. Running task
     * 2. Focused task
     * 3. First non-desktop task
     * 4. Last desktop task
     * 5. null otherwise
     */
    fun getExpectedCurrentTask(runningTaskView: TaskView?, focusedTaskView: TaskView?): TaskView? =
        runningTaskView
            ?: focusedTaskView
            ?: taskViews.firstOrNull { it !is DesktopTaskView }
            ?: taskViews.lastOrNull()

    /** Returns the first TaskView if it exists, or null otherwise. */
    fun getFirstTaskView(): TaskView? = taskViews.firstOrNull()

    /** Returns the last TaskView if it exists, or null otherwise. */
    fun getLastTaskView(): TaskView? = taskViews.lastOrNull()

    /** Returns the first TaskView that is not large */
    fun getFirstSmallTaskView(): TaskView? = taskViews.firstOrNull { !it.isLargeTile }

    /** Returns the last TaskView that should be displayed as a large tile. */
    fun getLastLargeTaskView(): TaskView? = taskViews.lastOrNull { it.isLargeTile }

    /**
     * Gets the list of accessibility children. Currently all the children of RecentsViews are
     * added, and in the reverse order to the list.
     */
    fun getAccessibilityChildren(): List<View> = recentsView.children.toList().reversed()

    @JvmOverloads
    /** Returns the first [TaskView], with some tasks possibly hidden in the carousel. */
    fun getFirstTaskViewInCarousel(
        nonRunningTaskCarouselHidden: Boolean,
        runningTaskView: TaskView? = recentsView.runningTaskView,
    ): TaskView? =
        taskViews.firstOrNull {
            it.isVisibleInCarousel(runningTaskView, nonRunningTaskCarouselHidden)
        }

    /** Returns the last [TaskView], with some tasks possibly hidden in the carousel. */
    fun getLastTaskViewInCarousel(nonRunningTaskCarouselHidden: Boolean): TaskView? =
        taskViews.lastOrNull {
            it.isVisibleInCarousel(recentsView.runningTaskView, nonRunningTaskCarouselHidden)
        }

    /** Returns if any small tasks are fully visible */
    fun isAnySmallTaskFullyVisible(): Boolean =
        taskViews.any { !it.isLargeTile && recentsView.isTaskViewFullyVisible(it) }

    /** Apply attachAlpha to all [TaskView] accordingly to different conditions. */
    fun applyAttachAlpha(nonRunningTaskCarouselHidden: Boolean) {
        taskViews.forEach { taskView ->
            taskView.attachAlpha =
                if (taskView == recentsView.runningTaskView) {
                    RUNNING_TASK_ATTACH_ALPHA.get(recentsView)
                } else {
                    if (
                        taskView.isVisibleInCarousel(
                            recentsView.runningTaskView,
                            nonRunningTaskCarouselHidden,
                        )
                    )
                        1f
                    else 0f
                }
        }
    }

    fun TaskView.isVisibleInCarousel(
        runningTaskView: TaskView?,
        nonRunningTaskCarouselHidden: Boolean,
    ): Boolean =
        if (!nonRunningTaskCarouselHidden) true
        else getCarouselType() == runningTaskView.getCarouselType()

    /** Returns the carousel type of the TaskView, and default to fullscreen if it's null. */
    private fun TaskView?.getCarouselType(): TaskViewCarousel =
        if (this is DesktopTaskView) TaskViewCarousel.DESKTOP else TaskViewCarousel.FULL_SCREEN

    private enum class TaskViewCarousel {
        FULL_SCREEN,
        DESKTOP,
    }
}
