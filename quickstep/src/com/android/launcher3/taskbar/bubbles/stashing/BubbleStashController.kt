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

package com.android.launcher3.taskbar.bubbles.stashing

import android.view.InsetsController
import android.view.MotionEvent
import android.view.View
import com.android.launcher3.taskbar.TaskbarInsetsController
import com.android.launcher3.taskbar.bubbles.BubbleBarView
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController
import com.android.wm.shell.common.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.animation.PhysicsAnimator
import java.io.PrintWriter

/** StashController that defines stashing behaviour for the taskbar modes. */
interface BubbleStashController {

    /**
     * Abstraction on the task bar activity context to only provide the dimensions required for
     * [BubbleBarView] translation Y computation.
     */
    interface TaskbarHotseatDimensionsProvider {

        /** Provides taskbar bottom space in pixels. */
        fun getTaskbarBottomSpace(): Int

        /** Provides taskbar height in pixels. */
        fun getTaskbarHeight(): Int

        /** Provides hotseat bottom space in pixels. */
        fun getHotseatBottomSpace(): Int

        /** Provides hotseat height in pixels. */
        fun getHotseatHeight(): Int
    }

    /** Execute passed action only after controllers are initiated. */
    interface ControllersAfterInitAction {
        /** Execute action after controllers are initiated. */
        fun runAfterInit(action: Runnable)
    }

    /** Whether bubble bar is currently stashed */
    val isStashed: Boolean

    /** Whether launcher enters or exits the home page. */
    var isBubblesShowingOnHome: Boolean

    /** Whether launcher enters or exits the overview page. */
    var isBubblesShowingOnOverview: Boolean

    /** Updated when sysui locked state changes, when locked, bubble bar is not shown. */
    var isSysuiLocked: Boolean

    /** Whether there is a transient taskbar mode */
    val isTransientTaskBar: Boolean

    /** Whether stash control has a handle view */
    val hasHandleView: Boolean

    /** Initialize controller */
    fun init(
        taskbarInsetsController: TaskbarInsetsController,
        bubbleBarViewController: BubbleBarViewController,
        bubbleStashedHandleViewController: BubbleStashedHandleViewController?,
        controllersAfterInitAction: ControllersAfterInitAction
    )

    /** Shows the bubble bar at [bubbleBarTranslationY] position immediately without animation. */
    fun showBubbleBarImmediate()

    /** Shows the bubble bar at [bubbleBarTranslationY] position immediately without animation. */
    fun showBubbleBarImmediate(bubbleBarTranslationY: Float)

    /** Stashes the bubble bar immediately without animation. */
    fun stashBubbleBarImmediate()

    /** Returns the touchable height of the bubble bar based on it's stashed state. */
    fun getTouchableHeight(): Int

    /** Whether bubble bar is currently visible */
    fun isBubbleBarVisible(): Boolean

    /**
     * Updates the values of the internal animators after the new bubble animation was interrupted
     *
     * @param isStashed whether the current state should be stashed
     * @param bubbleBarTranslationY the current bubble bar translation. this is only used if the
     *   bubble bar is showing to ensure that the stash animator runs smoothly.
     */
    fun onNewBubbleAnimationInterrupted(isStashed: Boolean, bubbleBarTranslationY: Float)

    /** Checks whether the motion event is over the stash handle or bubble bar. */
    fun isEventOverBubbleBarViews(ev: MotionEvent): Boolean

    /** Set a bubble bar location */
    fun setBubbleBarLocation(bubbleBarLocation: BubbleBarLocation)

    /**
     * Stashes the bubble bar (transform to the handle view), or just shrink width of the expanded
     * bubble bar based on the controller implementation.
     */
    fun stashBubbleBar()

    /** Shows the bubble bar, and expands bubbles depending on [expandBubbles]. */
    fun showBubbleBar(expandBubbles: Boolean)

    // TODO(b/354218264): Move to BubbleBarViewAnimator
    /**
     * The difference on the Y axis between the center of the handle and the center of the bubble
     * bar.
     */
    fun getDiffBetweenHandleAndBarCenters(): Float

    // TODO(b/354218264): Move to BubbleBarViewAnimator
    /** The distance the handle moves as part of the new bubble animation. */
    fun getStashedHandleTranslationForNewBubbleAnimation(): Float

    // TODO(b/354218264): Move to BubbleBarViewAnimator
    /** Returns the [PhysicsAnimator] for the stashed handle view. */
    fun getStashedHandlePhysicsAnimator(): PhysicsAnimator<View>?

    // TODO(b/354218264): Move to BubbleBarViewAnimator
    /** Notifies taskbar that it should update its touchable region. */
    fun updateTaskbarTouchRegion()

    // TODO(b/354218264): Move to BubbleBarViewAnimator
    /** Set the translation Y for the stashed handle. */
    fun setHandleTranslationY(translationY: Float)

    /** Returns the translation of the handle. */
    fun getHandleTranslationY(): Float?

    /**
     * Returns bubble bar Y position according to [isBubblesShowingOnHome] and
     * [isBubblesShowingOnOverview] values. Default implementation only analyse
     * [isBubblesShowingOnHome] and return translationY to align with the hotseat vertical center.
     * For Other cases align bubbles with the taskbar.
     */
    val bubbleBarTranslationY: Float
        get() =
            if (isBubblesShowingOnHome) {
                bubbleBarTranslationYForHotseat
            } else {
                bubbleBarTranslationYForTaskbar
            }

    /** Translation Y to align the bubble bar with the hotseat. */
    val bubbleBarTranslationYForTaskbar: Float

    /** Return translation Y to align the bubble bar with the taskbar. */
    val bubbleBarTranslationYForHotseat: Float

    /** Dumps the state of BubbleStashController. */
    fun dump(pw: PrintWriter) {
        pw.println("Bubble stash controller state:")
        pw.println("  isStashed: $isStashed")
        pw.println("  isBubblesShowingOnOverview: $isBubblesShowingOnOverview")
        pw.println("  isBubblesShowingOnHome: $isBubblesShowingOnHome")
        pw.println("  isSysuiLocked: $isSysuiLocked")
    }

    companion object {
        /** How long to stash/unstash. */
        const val BAR_STASH_DURATION = InsetsController.ANIMATION_DURATION_RESIZE.toLong()

        const val BAR_STASH_ALPHA_DURATION = 50L
        const val BAR_STASH_ALPHA_DELAY = 33L

        /** How long to translate Y coordinate of the BubbleBar. */
        const val BAR_TRANSLATION_DURATION = 300L
    }
}
