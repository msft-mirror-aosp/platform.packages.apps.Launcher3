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

package com.android.launcher3.taskbar.customization

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color.TRANSPARENT
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.view.setPadding
import com.android.launcher3.R
import com.android.launcher3.Utilities.dpToPx
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarViewCallbacks
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.IconButtonView

/** Taskbar divider view container for customizable taskbar. */
class TaskbarDividerContainer
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : IconButtonView(context, attrs), TaskbarContainer {
    private val activityContext: TaskbarActivityContext = ActivityContext.lookupContext(context)

    override val spaceNeeded: Int
        get() {
            return dpToPx(activityContext.taskbarSpecsEvaluator.taskbarIconSize.size.toFloat())
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.taskbar_divider, null, false)
        setUpIcon()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun setUpIcon() {
        backgroundTintList = ColorStateList.valueOf(TRANSPARENT)
        val drawable = resources.getDrawable(R.drawable.taskbar_divider_button)
        setIconDrawable(drawable)
        setPadding(dpToPx(activityContext.taskbarSpecsEvaluator.taskbarIconPadding.toFloat()))
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setUpCallbacks(callbacks: TaskbarViewCallbacks) {
        setOnLongClickListener(callbacks.taskbarDividerLongClickListener)
        setOnTouchListener(callbacks.taskbarDividerRightClickListener)
    }
}