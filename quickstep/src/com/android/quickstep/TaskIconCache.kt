/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.SparseArray
import androidx.annotation.WorkerThread
import com.android.launcher3.Flags.enableOverviewIconMenu
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.DisplayController.DisplayInfoChangeListener
import com.android.launcher3.util.Executors
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.Preconditions
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.quickstep.util.TaskKeyLruCache
import com.android.quickstep.util.TaskVisualsChangeListener
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.systemui.shared.system.PackageManagerWrapper
import java.util.concurrent.Executor

/** Manages the caching of task icons and related data. */
class TaskIconCache(
    private val context: Context,
    private val bgExecutor: Executor,
    private val iconProvider: IconProvider,
) : TaskIconDataSource, DisplayInfoChangeListener {
    private val iconCache =
        TaskKeyLruCache<TaskCacheEntry>(
            context.resources.getInteger(R.integer.recentsIconCacheSize)
        )
    private val defaultIcons = SparseArray<BitmapInfo>()
    private var defaultIconBase: BitmapInfo? = null

    private var _iconFactory: BaseIconFactory? = null
    @get:WorkerThread
    private val iconFactory: BaseIconFactory
        get() =
            _iconFactory
                ?: BaseIconFactory(
                        context,
                        DisplayController.INSTANCE[context].info.densityDpi,
                        context.resources.getDimensionPixelSize(
                            R.dimen.task_icon_cache_default_icon_size
                        ),
                    )
                    .also { _iconFactory = it }

    var taskVisualsChangeListener: TaskVisualsChangeListener? = null

    init {
        DisplayController.INSTANCE.get(context).addChangeListener(this)
    }

    override fun onDisplayInfoChanged(context: Context, info: DisplayController.Info, flags: Int) {
        if ((flags and DisplayController.CHANGE_DENSITY) != 0) {
            clearCache()
        }
    }

    /**
     * Asynchronously fetches the icon and other task data.
     *
     * @param task The task to fetch the data for
     * @param callback The callback to receive the task after its data has been populated.
     * @return A cancelable handle to the request
     */
    override fun getIconInBackground(
        task: Task,
        callback: GetTaskIconCallback,
    ): CancellableTask<*>? {
        Preconditions.assertUIThread()
        if (task.icon != null) {
            // Nothing to load, the icon is already loaded
            callback.onTaskIconReceived(task.icon, task.titleDescription ?: "", task.title ?: "")
            return null
        }
        val request =
            CancellableTask(
                { getCacheEntry(task) },
                Executors.MAIN_EXECUTOR,
                { result: TaskCacheEntry ->
                    task.icon = result.icon
                    task.titleDescription = result.contentDescription
                    task.title = result.title

                    callback.onTaskIconReceived(
                        result.icon,
                        result.contentDescription,
                        result.title,
                    )
                    dispatchIconUpdate(task.key.id)
                },
            )
        bgExecutor.execute(request)
        return request
    }

    /** Clears the icon cache */
    fun clearCache() {
        bgExecutor.execute { resetFactory() }
    }

    fun onTaskRemoved(taskKey: TaskKey) {
        iconCache.remove(taskKey)
    }

    fun invalidateCacheEntries(pkg: String, handle: UserHandle) {
        bgExecutor.execute {
            iconCache.removeAll { key: TaskKey ->
                pkg == key.packageName && handle.identifier == key.userId
            }
        }
    }

    @WorkerThread
    private fun getCacheEntry(task: Task): TaskCacheEntry {
        var entry = iconCache.getAndInvalidateIfModified(task.key)
        if (entry != null) {
            return entry
        }

        val desc = task.taskDescription
        val key = task.key
        var activityInfo: ActivityInfo? = null

        // Create new cache entry
        entry = TaskCacheEntry()

        // Load icon
        val icon = getIcon(desc, key.userId)
        entry.icon =
            if (icon != null) {
                getBitmapInfo(
                        BitmapDrawable(context.resources, icon),
                        key.userId,
                        desc.primaryColor,
                        false, /* isInstantApp */
                    )
                    .newIcon(context)
            } else {
                activityInfo =
                    PackageManagerWrapper.getInstance().getActivityInfo(key.component, key.userId)
                if (activityInfo != null) {
                    val bitmapInfo =
                        getBitmapInfo(
                            iconProvider.getIcon(activityInfo),
                            key.userId,
                            desc.primaryColor,
                            activityInfo.applicationInfo.isInstantApp,
                        )
                    bitmapInfo.newIcon(context)
                } else {
                    getDefaultIcon(key.userId)
                }
            }

        // Skip loading the content description if the activity no longer exists
        activityInfo =
            activityInfo
                ?: PackageManagerWrapper.getInstance().getActivityInfo(key.component, key.userId)

        if (activityInfo != null) {
            entry.contentDescription =
                getBadgedContentDescription(activityInfo, task.key.userId, task.taskDescription)
            if (enableOverviewIconMenu()) {
                entry.title = Utilities.trim(activityInfo.loadLabel(context.packageManager))
            }
        }

        iconCache.put(task.key, entry)
        return entry
    }

    private fun getIcon(desc: ActivityManager.TaskDescription, userId: Int): Bitmap? =
        desc.inMemoryIcon
            ?: ActivityManager.TaskDescription.loadTaskDescriptionIcon(desc.iconFilename, userId)

    private fun getBadgedContentDescription(
        info: ActivityInfo,
        userId: Int,
        taskDescription: ActivityManager.TaskDescription?,
    ): String {
        val packageManager = context.packageManager
        var taskLabel = taskDescription?.let { Utilities.trim(it.label) }
        if (taskLabel.isNullOrEmpty()) {
            taskLabel = Utilities.trim(info.loadLabel(packageManager))
        }

        val applicationLabel = Utilities.trim(info.applicationInfo.loadLabel(packageManager))
        val badgedApplicationLabel =
            if (userId != UserHandle.myUserId())
                packageManager
                    .getUserBadgedLabel(applicationLabel, UserHandle.of(userId))
                    .toString()
            else applicationLabel
        return if (applicationLabel == taskLabel) badgedApplicationLabel
        else "$badgedApplicationLabel $taskLabel"
    }

    @WorkerThread
    private fun getDefaultIcon(userId: Int): Drawable {
        synchronized(defaultIcons) {
            val defaultIconBase =
                defaultIconBase ?: iconFactory.use { it.makeDefaultIcon(iconProvider) }
            val index: Int = defaultIcons.indexOfKey(userId)
            return if (index >= 0) {
                defaultIcons.valueAt(index).newIcon(context)
            } else {
                val info =
                    defaultIconBase.withFlags(
                        UserCache.INSTANCE.get(context)
                            .getUserInfo(UserHandle.of(userId))
                            .applyBitmapInfoFlags(FlagOp.NO_OP)
                    )
                defaultIcons.put(userId, info)
                info.newIcon(context)
            }
        }
    }

    @WorkerThread
    private fun getBitmapInfo(
        drawable: Drawable,
        userId: Int,
        primaryColor: Int,
        isInstantApp: Boolean,
    ): BitmapInfo {
        iconFactory.use { iconFactory ->
            iconFactory.setWrapperBackgroundColor(primaryColor)
            // User version code O, so that the icon is always wrapped in an adaptive icon container
            return iconFactory.createBadgedIconBitmap(
                drawable,
                IconOptions()
                    .setUser(UserCache.INSTANCE.get(context).getUserInfo(UserHandle.of(userId)))
                    .setInstantApp(isInstantApp)
                    .setExtractedColor(0),
            )
        }
    }

    @WorkerThread
    private fun resetFactory() {
        _iconFactory = null
        iconCache.evictAll()
    }

    private data class TaskCacheEntry(
        var icon: Drawable? = null,
        var contentDescription: String = "",
        var title: String = "",
    )

    /** Callback used when retrieving app icons from cache. */
    fun interface GetTaskIconCallback {
        /** Called when task icon is retrieved. */
        fun onTaskIconReceived(icon: Drawable?, contentDescription: String, title: String)
    }

    fun registerTaskVisualsChangeListener(newListener: TaskVisualsChangeListener?) {
        taskVisualsChangeListener = newListener
    }

    fun removeTaskVisualsChangeListener() {
        taskVisualsChangeListener = null
    }

    private fun dispatchIconUpdate(taskId: Int) {
        taskVisualsChangeListener?.onTaskIconChanged(taskId)
    }
}
