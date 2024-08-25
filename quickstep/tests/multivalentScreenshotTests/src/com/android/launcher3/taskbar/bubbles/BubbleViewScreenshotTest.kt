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

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.PathParser
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.R
import com.android.wm.shell.common.bubbles.BubbleInfo
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.ViewScreenshotTestRule
import platform.test.screenshot.getEmulatedDevicePathConfig

/** Screenshot tests for [BubbleView]. */
@RunWith(ParameterizedAndroidJunit4::class)
class BubbleViewScreenshotTest(emulationSpec: DeviceEmulationSpec) {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs() =
            DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                isDarkTheme = false,
                isLandscape = false
            )
    }

    @get:Rule
    val screenshotRule =
        ViewScreenshotTestRule(
            emulationSpec,
            ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec))
        )

    @Test
    fun bubbleView_hasUnseenContent() {
        screenshotRule.screenshotTest("bubbleView_hasUnseenContent") { activity ->
            activity.actionBar?.hide()
            setupBubbleView()
        }
    }

    @Test
    fun bubbleView_seen() {
        screenshotRule.screenshotTest("bubbleView_seen") { activity ->
            activity.actionBar?.hide()
            setupBubbleView(suppressNotification = true)
        }
    }

    @Test
    fun bubbleView_badgeHidden() {
        screenshotRule.screenshotTest("bubbleView_badgeHidden") { activity ->
            activity.actionBar?.hide()
            setupBubbleView().apply { setBadgeScale(0f) }
        }
    }

    private fun setupBubbleView(suppressNotification: Boolean = false): BubbleView {
        val inflater = LayoutInflater.from(context)

        val iconSize = 100
        // BubbleView uses launcher's badge to icon ratio and expects the badge image to already
        // have the right size
        val badgeToIconRatio = 0.444f
        val badgeRadius = iconSize * badgeToIconRatio / 2
        val icon = createCircleBitmap(radius = iconSize / 2, color = Color.LTGRAY)
        val badge = createCircleBitmap(radius = badgeRadius.toInt(), color = Color.RED)

        val flags =
            if (suppressNotification) Notification.BubbleMetadata.FLAG_SUPPRESS_NOTIFICATION else 0
        val bubbleInfo =
            BubbleInfo("key", flags, null, null, 0, context.packageName, null, null, false)
        val bubbleView = inflater.inflate(R.layout.bubblebar_item_view, null) as BubbleView
        val dotPath =
            PathParser.createPathFromPathData(
                context.resources.getString(com.android.internal.R.string.config_icon_mask)
            )
        val bubble =
            BubbleBarBubble(bubbleInfo, bubbleView, badge, icon, Color.BLUE, dotPath, "test app")
        bubbleView.setBubble(bubble)
        bubbleView.showDotIfNeeded(1f)
        return bubbleView
    }

    private fun createCircleBitmap(radius: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawARGB(0, 0, 0, 0)
        val paint = Paint()
        paint.color = color
        canvas.drawCircle(radius.toFloat(), radius.toFloat(), radius.toFloat(), paint)
        return bitmap
    }
}
