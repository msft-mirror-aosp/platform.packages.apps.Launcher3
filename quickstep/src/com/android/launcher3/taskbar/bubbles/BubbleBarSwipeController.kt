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

package com.android.launcher3.taskbar.bubbles

import android.animation.ValueAnimator
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnEnd
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.anim.SpringAnimationBuilder
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarThresholdUtils
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.launcher3.touch.OverScroll

/** Handle swipe events on the bubble bar and handle */
class BubbleBarSwipeController {

    private val context: Context

    private var bubbleStashedHandleViewController: BubbleStashedHandleViewController? = null
    private var bubbleBarViewController: BubbleBarViewController? = null
    private var bubbleStashController: BubbleStashController? = null

    private var springAnimation: ValueAnimator? = null
    private val animatedSwipeTranslation = AnimatedFloat(this::onSwipeUpdate)

    private val unstashThreshold: Int
    private val expandThreshold: Int
    private val maxOverscroll: Int

    private var swipeState: SwipeState = SwipeState()

    constructor(tac: TaskbarActivityContext) : this(tac, DefaultDimensionProvider(tac))

    @VisibleForTesting
    constructor(context: Context, dimensionProvider: DimensionProvider) {
        this.context = context
        unstashThreshold = dimensionProvider.unstashThreshold
        expandThreshold = dimensionProvider.expandThreshold
        maxOverscroll = dimensionProvider.maxOverscroll
    }

    fun init(bubbleControllers: BubbleControllers) {
        bubbleStashedHandleViewController =
            bubbleControllers.bubbleStashedHandleViewController.orElse(null)
        bubbleBarViewController = bubbleControllers.bubbleBarViewController
        bubbleStashController = bubbleControllers.bubbleStashController
    }

    /** Start tracking a new swipe gesture */
    fun start() {
        if (springAnimation != null) reset()
        val stashed = bubbleStashController?.isStashed ?: false
        val barVisible = bubbleStashController?.isBubbleBarVisible() ?: false
        val expanded = bubbleBarViewController?.isExpanded ?: false

        swipeState =
            SwipeState(
                stashedOnStart = stashed,
                collapsedOnStart = !stashed && barVisible && !expanded,
                expandedOnStart = expanded,
            )
    }

    /** Update swipe distance to [dy] */
    fun swipeTo(dy: Float) {
        // Only handle swipe up and stashed or collapsed bar
        if (dy > 0 || swipeState.expandedOnStart) return

        animatedSwipeTranslation.updateValue(dy)

        val prevState = swipeState
        // We can pass unstash threshold once per gesture, keep it true if it happened once
        val passedUnstashThreshold = isUnstash(dy) || prevState.passedUnstashThreshold
        // Expand happens at the end of the gesture, always keep the current value
        val passedExpandThreshold = isExpand(dy)

        if (
            passedUnstashThreshold != prevState.passedUnstashThreshold ||
                passedExpandThreshold != prevState.passedExpandThreshold
        ) {
            swipeState =
                swipeState.copy(
                    passedUnstashThreshold = passedUnstashThreshold,
                    passedExpandThreshold = passedExpandThreshold,
                )
        }

        if (
            swipeState.stashedOnStart &&
                swipeState.passedUnstashThreshold &&
                !prevState.passedUnstashThreshold
        ) {
            bubbleStashController?.showBubbleBar(expandBubbles = false)
        }
    }

    /** Finish tracking swipe gesture. Animate views back to resting state */
    fun finish() {
        if (swipeState.passedExpandThreshold) {
            bubbleStashController?.showBubbleBar(expandBubbles = true)
        }
        springToRest()
    }

    /** Returns `true` if we are tracking a swipe gesture */
    fun isSwipeGesture(): Boolean {
        return swipeState.passedUnstashThreshold || swipeState.passedExpandThreshold
    }

    private fun isUnstash(dy: Float): Boolean {
        return dy < -unstashThreshold
    }

    private fun isExpand(dy: Float): Boolean {
        return dy < -expandThreshold
    }

    private fun reset() {
        springAnimation?.let {
            if (it.isRunning) {
                it.removeAllListeners()
                it.cancel()
                animatedSwipeTranslation.updateValue(0f)
            }
        }
        springAnimation = null
        swipeState = SwipeState()
    }

    private fun onSwipeUpdate(value: Float) {
        val dampedSwipe = -OverScroll.dampedScroll(-value, maxOverscroll).toFloat()
        bubbleStashedHandleViewController?.setTranslationYForSwipe(dampedSwipe)
        bubbleBarViewController?.setTranslationYForSwipe(dampedSwipe)
    }

    private fun springToRest() {
        springAnimation =
            SpringAnimationBuilder(context)
                .setStartValue(animatedSwipeTranslation.value)
                .setEndValue(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .build(animatedSwipeTranslation, AnimatedFloat.VALUE)
                .also { it.doOnEnd { reset() } }
        springAnimation?.start()
    }

    internal data class SwipeState(
        val stashedOnStart: Boolean = false,
        val collapsedOnStart: Boolean = false,
        val expandedOnStart: Boolean = false,
        val passedUnstashThreshold: Boolean = false,
        val passedExpandThreshold: Boolean = false,
    )

    /** Allows overriding the dimension provider for testing */
    @VisibleForTesting
    interface DimensionProvider {
        val unstashThreshold: Int
        val expandThreshold: Int
        val maxOverscroll: Int
    }

    private class DefaultDimensionProvider(taskbarActivityContext: TaskbarActivityContext) :
        DimensionProvider {
        override val unstashThreshold: Int
        override val expandThreshold: Int
        override val maxOverscroll: Int

        init {
            val resources = taskbarActivityContext.resources
            unstashThreshold =
                TaskbarThresholdUtils.getFromNavThreshold(
                    resources,
                    taskbarActivityContext.deviceProfile,
                )
            // TODO(325673340): review threshold with ux
            expandThreshold =
                TaskbarThresholdUtils.getAppWindowThreshold(
                    resources,
                    taskbarActivityContext.deviceProfile,
                )
            maxOverscroll = taskbarActivityContext.deviceProfile.heightPx - unstashThreshold
        }
    }
}
