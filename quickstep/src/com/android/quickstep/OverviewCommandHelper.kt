/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.graphics.PointF
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.View
import androidx.annotation.BinderThread
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.android.internal.jank.Cuj
import com.android.launcher3.Flags.enableOverviewCommandHelperTimeout
import com.android.launcher3.PagedView
import com.android.launcher3.logger.LauncherAtom
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_3_BUTTON
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_QUICK_SWITCH
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_SHORTCUT
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.launcher3.util.coroutines.ProductionDispatchers
import com.android.quickstep.OverviewCommandHelper.CommandInfo.CommandStatus
import com.android.quickstep.OverviewCommandHelper.CommandType.HIDE
import com.android.quickstep.OverviewCommandHelper.CommandType.HOME
import com.android.quickstep.OverviewCommandHelper.CommandType.KEYBOARD_INPUT
import com.android.quickstep.OverviewCommandHelper.CommandType.SHOW
import com.android.quickstep.OverviewCommandHelper.CommandType.TOGGLE
import com.android.quickstep.util.ActiveGestureLog
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import java.io.PrintWriter
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** Helper class to handle various atomic commands for switching between Overview. */
class OverviewCommandHelper
@JvmOverloads
constructor(
    private val touchInteractionService: TouchInteractionService,
    private val overviewComponentObserver: OverviewComponentObserver,
    private val taskAnimationManager: TaskAnimationManager,
    private val dispatcherProvider: DispatcherProvider = ProductionDispatchers,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    private val commandQueue = ConcurrentLinkedDeque<CommandInfo>()

    /**
     * Index of the TaskView that should be focused when launching Overview. Persisted so that we do
     * not lose the focus across multiple calls of [OverviewCommandHelper.executeCommand] for the
     * same command
     */
    private var keyboardTaskFocusIndex = -1

    /**
     * Whether we should incoming toggle commands while a previous toggle command is still ongoing.
     * This serves as a rate-limiter to prevent overlapping animations that can clobber each other
     * and prevent clean-up callbacks from running. This thus prevents a recurring set of bugs with
     * janky recents animations and unresponsive home and overview buttons.
     */
    private var waitForToggleCommandComplete = false

    private val activityInterface: BaseActivityInterface<*, *>
        get() = overviewComponentObserver.activityInterface

    private val visibleRecentsView: RecentsView<*, *>?
        get() = activityInterface.getVisibleRecentsView<RecentsView<*, *>>()

    /**
     * Adds a command to be executed next, after all pending tasks are completed. Max commands that
     * can be queued is [.MAX_QUEUE_SIZE]. Requests after reaching that limit will be silently
     * dropped.
     */
    @BinderThread
    fun addCommand(type: CommandType): CommandInfo? {
        if (commandQueue.size >= MAX_QUEUE_SIZE) {
            Log.d(TAG, "command not added: $type - queue is full ($commandQueue).")
            return null
        }

        val command = CommandInfo(type)
        commandQueue.add(command)
        Log.d(TAG, "command added: $command")

        if (commandQueue.size == 1) {
            Log.d(TAG, "execute: $command - queue size: ${commandQueue.size}")
            if (enableOverviewCommandHelperTimeout()) {
                coroutineScope.launch(dispatcherProvider.main) { processNextCommand() }
            } else {
                Executors.MAIN_EXECUTOR.execute { processNextCommand() }
            }
        } else {
            Log.d(TAG, "not executed: $command - queue size: ${commandQueue.size}")
        }

        return command
    }

    fun canStartHomeSafely(): Boolean = commandQueue.isEmpty() || commandQueue.first().type == HOME

    /** Clear pending or completed commands from the queue */
    fun clearPendingCommands() {
        Log.d(TAG, "clearing pending commands: $commandQueue")
        commandQueue.removeAll { it.status != CommandStatus.PROCESSING }
    }

    /**
     * Executes the next command from the queue. If the command finishes immediately (returns true),
     * it continues to execute the next command, until the queue is empty of a command defer's its
     * completion (returns false).
     */
    @UiThread
    private fun processNextCommand() {
        val command: CommandInfo =
            commandQueue.firstOrNull()
                ?: run {
                    Log.d(TAG, "no pending commands to be executed.")
                    return
                }

        command.status = CommandStatus.PROCESSING
        Log.d(TAG, "executing command: $command")

        if (enableOverviewCommandHelperTimeout()) {
            coroutineScope.launch(dispatcherProvider.main) {
                withTimeout(QUEUE_WAIT_DURATION_IN_MS) {
                    executeCommandSuspended(command)
                    ensureActive()
                    onCommandFinished(command)
                }
            }
        } else {
            val result = executeCommand(command, onCallbackResult = { onCommandFinished(command) })
            Log.d(TAG, "command executed: $command with result: $result")
            if (result) {
                onCommandFinished(command)
            } else {
                Log.d(TAG, "waiting for command callback: $command")
            }
        }
    }

    /**
     * Executes the task and returns true if next task can be executed. If false, then the next task
     * is deferred until [.scheduleNextTask] is called
     */
    @VisibleForTesting
    fun executeCommand(command: CommandInfo, onCallbackResult: () -> Unit): Boolean {
        // This shouldn't happen if we execute 1 command per time.
        if (waitForToggleCommandComplete && command.type == TOGGLE) {
            Log.d(TAG, "executeCommand: $command - waiting for toggle command complete")
            return true
        }

        val recentsView = visibleRecentsView
        Log.d(TAG, "executeCommand: $command - visibleRecentsView: $recentsView")
        return if (recentsView != null) {
            executeWhenRecentsIsVisible(command, recentsView, onCallbackResult)
        } else {
            executeWhenRecentsIsNotVisible(command, onCallbackResult)
        }
    }

    /**
     * Executes the task and returns true if next task can be executed. If false, then the next task
     * is deferred until [.scheduleNextTask] is called
     */
    private suspend fun executeCommandSuspended(command: CommandInfo) =
        suspendCancellableCoroutine { continuation ->
            fun processResult(isCompleted: Boolean) {
                Log.d(TAG, "command executed: $command with result: $isCompleted")
                if (isCompleted) {
                    continuation.resume(Unit)
                } else {
                    Log.d(TAG, "waiting for command callback: $command")
                }
            }

            val result = executeCommand(command, onCallbackResult = { processResult(true) })
            processResult(result)

            continuation.invokeOnCancellation { cancelCommand(command, it) }
        }

    private fun executeWhenRecentsIsVisible(
        command: CommandInfo,
        recentsView: RecentsView<*, *>,
        onCallbackResult: () -> Unit,
    ): Boolean =
        when (command.type) {
            SHOW -> true // already visible
            KEYBOARD_INPUT,
            HIDE -> {
                if (recentsView.isHandlingTouch) {
                    true
                } else {
                    keyboardTaskFocusIndex = PagedView.INVALID_PAGE
                    val currentPage = recentsView.nextPage
                    val taskView = recentsView.getTaskViewAt(currentPage)
                    launchTask(recentsView, taskView, command, onCallbackResult)
                }
            }
            TOGGLE -> {
                val taskView =
                    if (recentsView.runningTaskView == null) {
                        recentsView.getTaskViewAt(0)
                    } else {
                        recentsView.nextTaskView ?: recentsView.runningTaskView
                    }
                launchTask(recentsView, taskView, command, onCallbackResult)
            }
            HOME -> {
                recentsView.startHome()
                true
            }
        }

    private fun launchTask(
        recents: RecentsView<*, *>,
        taskView: TaskView?,
        command: CommandInfo,
        onCallbackResult: () -> Unit,
    ): Boolean {
        var callbackList: RunnableList? = null
        if (taskView != null) {
            waitForToggleCommandComplete = true
            taskView.isEndQuickSwitchCuj = true
            callbackList = taskView.launchWithAnimation()
        }

        if (callbackList != null) {
            callbackList.add {
                Log.d(TAG, "launching task callback: $command")
                onCallbackResult()
                waitForToggleCommandComplete = false
            }
            Log.d(TAG, "launching task - waiting for callback: $command")
            return false
        } else {
            recents.startHome()
            waitForToggleCommandComplete = false
            return true
        }
    }

    private fun executeWhenRecentsIsNotVisible(
        command: CommandInfo,
        onCallbackResult: () -> Unit,
    ): Boolean {
        val recentsViewContainer = activityInterface.getCreatedContainer() as? RecentsViewContainer
        val recentsView: RecentsView<*, *>? = recentsViewContainer?.getOverviewPanel()
        val deviceProfile = recentsViewContainer?.getDeviceProfile()
        val uiController = activityInterface.getTaskbarController()
        val allowQuickSwitch =
            uiController != null &&
                deviceProfile != null &&
                (deviceProfile.isTablet || deviceProfile.isTwoPanels)

        when (command.type) {
            HIDE -> {
                if (!allowQuickSwitch) return true
                keyboardTaskFocusIndex = uiController!!.launchFocusedTask()
                if (keyboardTaskFocusIndex == -1) return true
            }
            KEYBOARD_INPUT ->
                if (allowQuickSwitch) {
                    uiController!!.openQuickSwitchView()
                    return true
                } else {
                    keyboardTaskFocusIndex = 0
                }
            HOME -> {
                ActiveGestureLog.INSTANCE.addLog("OverviewCommandHelper.executeCommand(HOME)")
                // Although IActivityTaskManager$Stub$Proxy.startActivity is a slow binder call,
                // we should still call it on main thread because launcher is waiting for
                // ActivityTaskManager to resume it. Also calling startActivity() on bg thread
                // could potentially delay resuming launcher. See b/348668521 for more details.
                touchInteractionService.startActivity(overviewComponentObserver.homeIntent)
                return true
            }
            SHOW ->
                // When Recents is not currently visible, the command's type is SHOW
                // when overview is triggered via the keyboard overview button or Action+Tab
                // keys (Not Alt+Tab which is KQS). The overview button on-screen in 3-button
                // nav is TYPE_TOGGLE.
                keyboardTaskFocusIndex = 0
            TOGGLE -> {}
        }

        recentsView?.setKeyboardTaskFocusIndex(keyboardTaskFocusIndex)
        // Handle recents view focus when launching from home
        val animatorListener: Animator.AnimatorListener =
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    Log.d(TAG, "switching to Overview state - onAnimationStart: $command")
                    super.onAnimationStart(animation)
                    updateRecentsViewFocus(command)
                    logShowOverviewFrom(command.type)
                }

                override fun onAnimationEnd(animation: Animator) {
                    Log.d(TAG, "switching to Overview state - onAnimationEnd: $command")
                    super.onAnimationEnd(animation)
                    onRecentsViewFocusUpdated(command)
                    onCallbackResult()
                }
            }
        if (activityInterface.switchToRecentsIfVisible(animatorListener)) {
            Log.d(TAG, "switching to Overview state - waiting: $command")
            // If successfully switched, wait until animation finishes
            return false
        }

        val activity = activityInterface.getCreatedContainer()
        if (activity != null) {
            InteractionJankMonitorWrapper.begin(activity.rootView, Cuj.CUJ_LAUNCHER_QUICK_SWITCH)
        }

        val gestureState =
            touchInteractionService.createGestureState(
                GestureState.DEFAULT_STATE,
                GestureState.TrackpadGestureType.NONE,
            )
        gestureState.isHandlingAtomicEvent = true
        val interactionHandler =
            touchInteractionService.swipeUpHandlerFactory.newHandler(
                gestureState,
                command.createTime,
            )
        interactionHandler.setGestureEndCallback {
            onTransitionComplete(command, interactionHandler, onCallbackResult)
        }
        interactionHandler.initWhenReady("OverviewCommandHelper: command.type=${command.type}")

        val recentAnimListener: RecentsAnimationCallbacks.RecentsAnimationListener =
            object : RecentsAnimationCallbacks.RecentsAnimationListener {
                override fun onRecentsAnimationStart(
                    controller: RecentsAnimationController,
                    targets: RecentsAnimationTargets,
                ) {
                    Log.d(TAG, "recents animation started: $command")
                    updateRecentsViewFocus(command)
                    logShowOverviewFrom(command.type)
                    activityInterface.runOnInitBackgroundStateUI {
                        Log.d(TAG, "recents animation started - onInitBackgroundStateUI: $command")
                        interactionHandler.onGestureEnded(0f, PointF())
                    }
                    command.removeListener(this)
                }

                override fun onRecentsAnimationCanceled(
                    thumbnailDatas: HashMap<Int, ThumbnailData>
                ) {
                    Log.d(TAG, "recents animation canceled: $command")
                    interactionHandler.onGestureCancelled()
                    command.removeListener(this)

                    activityInterface.getCreatedContainer() ?: return
                    recentsView?.onRecentsAnimationComplete()
                }
            }

        if (taskAnimationManager.isRecentsAnimationRunning) {
            command.setAnimationCallbacks(
                taskAnimationManager.continueRecentsAnimation(gestureState)
            )
            command.addListener(interactionHandler)
            taskAnimationManager.notifyRecentsAnimationState(interactionHandler)
            interactionHandler.onGestureStarted(true /*isLikelyToStartNewTask*/)

            command.addListener(recentAnimListener)
            taskAnimationManager.notifyRecentsAnimationState(recentAnimListener)
        } else {
            val intent =
                Intent(interactionHandler.getLaunchIntent())
                    .putExtra(ActiveGestureLog.INTENT_EXTRA_LOG_TRACE_ID, gestureState.gestureId)
            command.setAnimationCallbacks(
                taskAnimationManager.startRecentsAnimation(gestureState, intent, interactionHandler)
            )
            interactionHandler.onGestureStarted(false /*isLikelyToStartNewTask*/)
            command.addListener(recentAnimListener)
        }
        Trace.beginAsyncSection(TRANSITION_NAME, 0)
        Log.d(TAG, "switching via recents animation - onGestureStarted: $command")
        return false
    }

    private fun onTransitionComplete(
        command: CommandInfo,
        handler: AbsSwipeUpHandler<*, *, *>,
        onCommandResult: () -> Unit,
    ) {
        Log.d(TAG, "switching via recents animation - onTransitionComplete: $command")
        command.removeListener(handler)
        Trace.endAsyncSection(TRANSITION_NAME, 0)
        onRecentsViewFocusUpdated(command)
        onCommandResult()
    }

    /** Called when the command finishes execution. */
    private fun onCommandFinished(command: CommandInfo) {
        command.status = CommandStatus.COMPLETED
        if (commandQueue.firstOrNull() !== command) {
            Log.d(
                TAG,
                "next task not scheduled. First pending command type " +
                    "is ${commandQueue.firstOrNull()} - command type is: $command",
            )
            return
        }

        Log.d(TAG, "command executed successfully! $command")
        commandQueue.remove(command)
        processNextCommand()
    }

    private fun cancelCommand(command: CommandInfo, throwable: Throwable?) {
        command.status = CommandStatus.CANCELED
        Log.e(TAG, "command cancelled: $command - $throwable")
        commandQueue.remove(command)
        processNextCommand()
    }

    private fun updateRecentsViewFocus(command: CommandInfo) {
        val recentsView: RecentsView<*, *> = visibleRecentsView ?: return
        if (command.type != KEYBOARD_INPUT && command.type != HIDE && command.type != SHOW) {
            return
        }

        // When the overview is launched via alt tab (command type is TYPE_KEYBOARD_INPUT),
        // the touch mode somehow is not change to false by the Android framework.
        // The subsequent tab to go through tasks in overview can only be dispatched to
        // focuses views, while focus can only be requested in
        // {@link View#requestFocusNoSearch(int, Rect)} when touch mode is false. To note,
        // here we launch overview with live tile.
        recentsView.viewRootImpl.touchModeChanged(false)
        // Ensure that recents view has focus so that it receives the followup key inputs
        // Stops requesting focused after first view gets focused.
        recentsView.getTaskViewAt(keyboardTaskFocusIndex).requestFocus() ||
            recentsView.nextTaskView.requestFocus() ||
            recentsView.getTaskViewAt(0).requestFocus() ||
            recentsView.requestFocus()
    }

    private fun onRecentsViewFocusUpdated(command: CommandInfo) {
        val recentsView: RecentsView<*, *> = visibleRecentsView ?: return
        if (command.type != HIDE || keyboardTaskFocusIndex == PagedView.INVALID_PAGE) {
            return
        }
        recentsView.setKeyboardTaskFocusIndex(PagedView.INVALID_PAGE)
        recentsView.currentPage = keyboardTaskFocusIndex
        keyboardTaskFocusIndex = PagedView.INVALID_PAGE
    }

    private fun View?.requestFocus(): Boolean {
        if (this == null) return false
        post {
            requestFocus()
            requestAccessibilityFocus()
        }
        return true
    }

    private fun logShowOverviewFrom(commandType: CommandType) {
        val container = activityInterface.getCreatedContainer() as? RecentsViewContainer ?: return
        val event =
            when (commandType) {
                SHOW -> LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_SHORTCUT
                HIDE -> LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_KEYBOARD_QUICK_SWITCH
                TOGGLE -> LAUNCHER_OVERVIEW_SHOW_OVERVIEW_FROM_3_BUTTON
                else -> return
            }
        StatsLogManager.newInstance(container.asContext())
            .logger()
            .withContainerInfo(
                LauncherAtom.ContainerInfo.newBuilder()
                    .setTaskSwitcherContainer(
                        LauncherAtom.TaskSwitcherContainer.getDefaultInstance()
                    )
                    .build()
            )
            .log(event)
    }

    fun dump(pw: PrintWriter) {
        pw.println("OverviewCommandHelper:")
        pw.println("  pendingCommands=${commandQueue.size}")
        if (commandQueue.isNotEmpty()) {
            pw.println("    pendingCommandType=${commandQueue.first().type}")
        }
        pw.println("  keyboardTaskFocusIndex=$keyboardTaskFocusIndex")
        pw.println("  waitForToggleCommandComplete=$waitForToggleCommandComplete")
    }

    @VisibleForTesting
    data class CommandInfo(
        val type: CommandType,
        var status: CommandStatus = CommandStatus.IDLE,
        val createTime: Long = SystemClock.elapsedRealtime(),
        private var animationCallbacks: RecentsAnimationCallbacks? = null,
    ) {
        fun setAnimationCallbacks(recentsAnimationCallbacks: RecentsAnimationCallbacks) {
            this.animationCallbacks = recentsAnimationCallbacks
        }

        fun addListener(listener: RecentsAnimationCallbacks.RecentsAnimationListener) {
            animationCallbacks?.addListener(listener)
        }

        fun removeListener(listener: RecentsAnimationCallbacks.RecentsAnimationListener?) {
            animationCallbacks?.removeListener(listener)
        }

        enum class CommandStatus {
            IDLE,
            PROCESSING,
            COMPLETED,
            CANCELED,
        }
    }

    enum class CommandType {
        SHOW,
        KEYBOARD_INPUT,
        HIDE,
        TOGGLE, // Navigate to Overview
        HOME, // Navigate to Home
    }

    companion object {
        private const val TAG = "OverviewCommandHelper"
        private const val TRANSITION_NAME = "Transition:toOverview"

        /**
         * Use case for needing a queue is double tapping recents button in 3 button nav. Size of 2
         * should be enough. We'll toss in one more because we're kind hearted.
         */
        private const val MAX_QUEUE_SIZE = 3
        private const val QUEUE_WAIT_DURATION_IN_MS = 5000L
    }
}
