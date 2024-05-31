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

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.util.Log
import android.util.SparseArray
import androidx.annotation.VisibleForTesting
import androidx.core.util.valueIterator
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.quickstep.RecentsModel
import kotlin.collections.filterNotNull

/**
 * Shows running apps when in Desktop Mode.
 *
 * Users can enter and exit Desktop Mode at run-time, meaning this class falls back to the default
 * recent-apps behaviour when outside of Desktop Mode.
 *
 * This class should only be used if
 * [com.android.window.flags.Flags.enableDesktopWindowingTaskbarRunningApps] is enabled.
 */
class DesktopTaskbarRunningAppsController(
    private val recentsModel: RecentsModel,
    // Pass a provider here instead of the actual DesktopVisibilityController instance since that
    // instance might not be available when this constructor is called.
    private val desktopVisibilityControllerProvider: () -> DesktopVisibilityController?,
) : TaskbarRecentAppsController() {

    private var apps: Array<AppInfo>? = null
    private var allRunningDesktopAppInfos: List<AppInfo>? = null
    private var allMinimizedDesktopAppInfos: List<AppInfo>? = null

    private val desktopVisibilityController: DesktopVisibilityController?
        get() = desktopVisibilityControllerProvider()

    private val isInDesktopMode: Boolean
        get() = desktopVisibilityController?.areDesktopTasksVisible() ?: false

    override fun onDestroy() {
        super.onDestroy()
        apps = null
    }

    @VisibleForTesting
    public override fun setApps(apps: Array<AppInfo>?) {
        this.apps = apps
    }

    override fun isEnabled() = true

    @VisibleForTesting
    public override fun updateHotseatItemInfos(hotseatItems: Array<ItemInfo?>): Array<ItemInfo?> {
        if (!isInDesktopMode) {
            Log.d(TAG, "updateHotseatItemInfos: not in Desktop Mode")
            return hotseatItems
        }
        val newHotseatItemInfos =
            hotseatItems
                .filterNotNull()
                // Ignore predicted apps - we show running apps instead
                .filter { itemInfo -> !itemInfo.isPredictedItem }
                .toMutableList()
        val runningDesktopAppInfos =
            allRunningDesktopAppInfos?.let {
                getRunningDesktopAppInfosExceptHotseatApps(it, newHotseatItemInfos.toList())
            }
        if (runningDesktopAppInfos != null) {
            newHotseatItemInfos.addAll(runningDesktopAppInfos)
        }
        return newHotseatItemInfos.toTypedArray()
    }

    override fun getRunningApps(): Set<String> {
        if (!isInDesktopMode) {
            return emptySet()
        }
        return allRunningDesktopAppInfos?.mapNotNull { it.targetPackage }?.toSet() ?: emptySet()
    }

    override fun getMinimizedApps(): Set<String> {
        if (!isInDesktopMode) {
            return emptySet()
        }
        return allMinimizedDesktopAppInfos?.mapNotNull { it.targetPackage }?.toSet() ?: emptySet()
    }

    @VisibleForTesting
    public override fun updateRunningApps() {
        if (!isInDesktopMode) {
            Log.d(TAG, "updateRunningApps: not in Desktop Mode")
            mControllers.taskbarViewController.commitRunningAppsToUI()
            return
        }
        val runningTasks = getDesktopRunningTasks()
        val runningAppInfo = getAppInfosFromRunningTasks(runningTasks)
        allRunningDesktopAppInfos = runningAppInfo
        updateMinimizedApps(runningTasks, runningAppInfo)
        mControllers.taskbarViewController.commitRunningAppsToUI()
    }

    private fun updateMinimizedApps(
        runningTasks: List<RunningTaskInfo>,
        runningAppInfo: List<AppInfo>,
    ) {
        val allRunningAppTasks =
            runningAppInfo
                .mapNotNull { appInfo -> appInfo.targetPackage?.let { appInfo to it } }
                .associate { (appInfo, targetPackage) ->
                    appInfo to
                        runningTasks
                            .filter { it.realActivity?.packageName == targetPackage }
                            .map { it.taskId }
                }
        val minimizedTaskIds = runningTasks.associate { it.taskId to !it.isVisible }
        allMinimizedDesktopAppInfos =
            allRunningAppTasks
                .filterValues { taskIds -> taskIds.all { minimizedTaskIds[it] ?: false } }
                .keys
                .toList()
    }

    private fun getRunningDesktopAppInfosExceptHotseatApps(
        allRunningDesktopAppInfos: List<AppInfo>,
        hotseatItems: List<ItemInfo>
    ): List<ItemInfo> {
        val hotseatPackages = hotseatItems.map { it.targetPackage }
        return allRunningDesktopAppInfos
            .filter { appInfo -> !hotseatPackages.contains(appInfo.targetPackage) }
            .map { WorkspaceItemInfo(it) }
    }

    private fun getDesktopRunningTasks(): List<RunningTaskInfo> =
        recentsModel.runningTasks.filter { taskInfo: RunningTaskInfo ->
            taskInfo.windowingMode == WindowConfiguration.WINDOWING_MODE_FREEFORM
        }

    // TODO(b/335398876) fetch app icons from Tasks instead of AppInfos
    private fun getAppInfosFromRunningTasks(tasks: List<RunningTaskInfo>): List<AppInfo> {
        // Early return if apps is empty, since we then have no AppInfo to compare to
        if (apps == null) {
            return emptyList()
        }
        val packageNames = tasks.map { it.realActivity?.packageName }.distinct().filterNotNull()
        return packageNames
            .map { packageName -> apps?.find { app -> packageName == app.targetPackage } }
            .filterNotNull()
    }

    private fun getAppInfosFromRunningTask(task: RunningTaskInfo): AppInfo? =
        apps?.firstOrNull { it.targetPackage == task.realActivity?.packageName }

    private fun <E> SparseArray<E>.toList(): List<E> = valueIterator().asSequence().toList()

    companion object {
        private const val TAG = "TabletDesktopTaskbarRunningAppsController"
    }
}
