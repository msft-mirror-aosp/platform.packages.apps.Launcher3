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

package com.android.quickstep.task.thumbnail

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.isInvisible
import com.android.launcher3.Flags.enableDesktopExplodedView
import com.android.launcher3.R
import com.android.launcher3.util.ViewPool
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.recents.di.RecentsDependencies
import com.android.quickstep.recents.di.get
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.SnapshotSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.quickstep.task.viewmodel.TaskThumbnailViewModel
import com.android.quickstep.views.FixedSizeImageView
import com.android.quickstep.views.TaskThumbnailViewHeader
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TaskThumbnailView : FrameLayout, ViewPool.Reusable {
    private val recentsCoroutineScope: CoroutineScope = RecentsDependencies.get()
    private val dispatcherProvider: DispatcherProvider = RecentsDependencies.get()

    // This is initialised here and set in onAttachedToWindow because onLayout can be called before
    // onAttachedToWindow so this property needs to be initialised as it is used below.
    private var viewData: TaskThumbnailViewData = RecentsDependencies.get(this)

    private lateinit var viewModel: TaskThumbnailViewModel

    private lateinit var viewAttachedScope: CoroutineScope

    private val scrimView: View by lazy { findViewById(R.id.task_thumbnail_scrim) }
    private val liveTileView: LiveTileView by lazy { findViewById(R.id.task_thumbnail_live_tile) }
    private val thumbnailView: FixedSizeImageView by lazy { findViewById(R.id.task_thumbnail) }
    private val splashBackground: View by lazy { findViewById(R.id.splash_background) }
    private val splashIcon: FixedSizeImageView by lazy { findViewById(R.id.splash_icon) }

    private var taskThumbnailViewHeader: TaskThumbnailViewHeader? = null

    private var uiState: TaskThumbnailUiState = Uninitialized

    private val bounds = Rect()

    var cornerRadius: Float = 0f
        set(value) {
            field = value
            invalidateOutline()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    override fun onFinishInflate() {
        super.onFinishInflate()

        maybeCreateHeader()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewAttachedScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("TaskThumbnailView")
            )
        viewData = RecentsDependencies.get(this)
        updateViewDataValues()
        viewModel = RecentsDependencies.get(this)
        viewModel.dimProgress
            .dropWhile { it == 0f }
            .flowOn(dispatcherProvider.background)
            .onEach { dimProgress -> scrimView.alpha = dimProgress }
            .launchIn(viewAttachedScope)
        viewModel.splashAlpha
            .dropWhile { it == 0f }
            .flowOn(dispatcherProvider.background)
            .onEach { splashAlpha ->
                splashBackground.alpha = splashAlpha
                splashIcon.alpha = splashAlpha
            }
            .launchIn(viewAttachedScope)

        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(bounds, cornerRadius)
                }
            }
    }

    // TODO(b/391842220): Cancel scope in onDetach instead of having a specific method for this.
    fun destroyScopes() {
        val scopeToCancel = viewAttachedScope
        recentsCoroutineScope.launch(dispatcherProvider.background) {
            scopeToCancel.cancel("TaskThumbnailView detaching from window")
        }
    }

    override fun onRecycle() {
        uiState = Uninitialized
        resetViews()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            updateViewDataValues()
        }
    }

    fun setState(state: TaskThumbnailUiState) {
        Log.d(TAG, "viewModelUiState changed from: $uiState to: $state")
        if (uiState == state) return
        uiState = state
        resetViews()
        when (state) {
            is Uninitialized -> {}
            is LiveTile -> drawLiveWindow(state)
            is SnapshotSplash -> drawSnapshotSplash(state)
            is BackgroundOnly -> drawBackground(state.backgroundColor)
        }
    }

    private fun updateViewDataValues() {
        viewData.width.value = width
        viewData.height.value = height
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (uiState is SnapshotSplash) {
            setImageMatrix()
        }
        bounds.set(0, 0, w, h)
        invalidateOutline()
    }

    override fun setScaleX(scaleX: Float) {
        super.setScaleX(scaleX)
        // Splash icon should ignore scale on TTV
        splashIcon.scaleX = 1 / scaleX
    }

    override fun setScaleY(scaleY: Float) {
        super.setScaleY(scaleY)
        // Splash icon should ignore scale on TTV
        splashIcon.scaleY = 1 / scaleY
    }

    private fun resetViews() {
        liveTileView.isInvisible = true
        thumbnailView.isInvisible = true
        splashBackground.alpha = 0f
        splashIcon.alpha = 0f
        scrimView.alpha = 0f
        setBackgroundColor(Color.BLACK)
        taskThumbnailViewHeader?.isInvisible = true
    }

    private fun drawBackground(@ColorInt background: Int) {
        setBackgroundColor(background)
    }

    private fun drawLiveWindow(liveTile: LiveTile) {
        liveTileView.isInvisible = false

        if (liveTile is LiveTile.WithHeader) {
            taskThumbnailViewHeader?.isInvisible = false
            taskThumbnailViewHeader?.setHeader(liveTile.header)
        }
    }

    private fun drawSnapshotSplash(snapshotSplash: SnapshotSplash) {
        drawSnapshot(snapshotSplash.snapshot)

        splashBackground.setBackgroundColor(snapshotSplash.snapshot.backgroundColor)
        splashIcon.setImageDrawable(snapshotSplash.splash)
    }

    private fun drawSnapshot(snapshot: Snapshot) {
        if (snapshot is Snapshot.WithHeader) {
            taskThumbnailViewHeader?.isInvisible = false
            taskThumbnailViewHeader?.setHeader(snapshot.header)
        }

        drawBackground(snapshot.backgroundColor)
        thumbnailView.setImageBitmap(snapshot.bitmap)
        thumbnailView.isInvisible = false
        setImageMatrix()
    }

    private fun setImageMatrix() {
        thumbnailView.imageMatrix = viewModel.getThumbnailPositionState(width, height, isLayoutRtl)
    }

    private fun maybeCreateHeader() {
        if (enableDesktopExplodedView() && taskThumbnailViewHeader == null) {
            taskThumbnailViewHeader =
                LayoutInflater.from(context)
                    .inflate(R.layout.task_thumbnail_view_header, this, false)
                    as TaskThumbnailViewHeader
            addView(taskThumbnailViewHeader)
        }
    }

    private companion object {
        const val TAG = "TaskThumbnailView"
    }
}
