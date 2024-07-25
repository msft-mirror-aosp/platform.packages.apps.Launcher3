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

package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.NavHandle;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.TopTaskTracker;
import com.android.systemui.shared.system.InputMonitorCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicBoolean;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NavHandleLongPressInputConsumerTest {

    private static final float TOUCH_SLOP = 10;
    private static final float SQUARED_TOUCH_SLOP = 100;

    private final AtomicBoolean mLongPressTriggered = new AtomicBoolean();
    private NavHandleLongPressInputConsumer mUnderTest;
    private float mScreenWidth;
    @Mock InputConsumer mDelegate;
    @Mock InputMonitorCompat mInputMonitor;
    @Mock RecentsAnimationDeviceState mDeviceState;
    @Mock NavHandle mNavHandle;
    @Mock GestureState mGestureState;
    @Mock NavHandleLongPressHandler mNavHandleLongPressHandler;
    @Mock TopTaskTracker mTopTaskTracker;
    @Mock TopTaskTracker.CachedTaskInfo mTaskInfo;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mTopTaskTracker.getCachedTopTask(anyBoolean())).thenReturn(mTaskInfo);
        when(mDeviceState.getSquaredTouchSlop()).thenReturn(SQUARED_TOUCH_SLOP);
        when(mDelegate.allowInterceptByParent()).thenReturn(true);
        mLongPressTriggered.set(false);
        when(mNavHandleLongPressHandler.getLongPressRunnable(any())).thenReturn(
                () -> mLongPressTriggered.set(true));
        SandboxContext context = new SandboxContext(getApplicationContext());
        context.putObject(TopTaskTracker.INSTANCE, mTopTaskTracker);
        mScreenWidth = DisplayController.INSTANCE.get(context).getInfo().currentSize.x;
        mUnderTest = new NavHandleLongPressInputConsumer(context, mDelegate, mInputMonitor,
                mDeviceState, mNavHandle, mGestureState);
        mUnderTest.setNavHandleLongPressHandler(mNavHandleLongPressHandler);
    }

    @Test
    public void testGetType() {
        assertThat(mUnderTest.getType() & InputConsumer.TYPE_NAV_HANDLE_LONG_PRESS).isNotEqualTo(0);
    }

    @Test
    public void testDelegateDisallowsTouchIntercept() {
        when(mDelegate.allowInterceptByParent()).thenReturn(false);
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));

        verify(mDelegate).onMotionEvent(any());
        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        verify(mNavHandleLongPressHandler, never()).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
    }

    @Test
    public void testDelegateDisallowsTouchInterceptAfterTouchDown() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));

        // Delegate should still get touches unless long press is triggered.
        verify(mDelegate).onMotionEvent(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());

        when(mDelegate.allowInterceptByParent()).thenReturn(false);
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_MOVE));

        // Delegate should still get motion events unless long press is triggered.
        verify(mDelegate, times(2)).onMotionEvent(any());
        // But our handler should be cancelled.
        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
    }

    @Test
    public void testLongPressTriggered() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_ACTIVE);
        assertTrue(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
    }

    @Test
    public void testLongPressTriggeredWithSlightVerticalMovement() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        mUnderTest.onMotionEvent(generateCenteredMotionEventWithYOffset(ACTION_MOVE,
                -(TOUCH_SLOP - 1)));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_ACTIVE);
        assertTrue(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
    }

    @Test
    public void testLongPressTriggeredWithSlightHorizontalMovement() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        mUnderTest.onMotionEvent(generateMotionEvent(ACTION_MOVE,
                mScreenWidth / 2f - (TOUCH_SLOP - 1), 0));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_ACTIVE);
        assertTrue(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
    }

    @Test
    public void testLongPressAbortedByTouchUp() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() - 10);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());

        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_UP));
        // Wait past the long press timeout, to be extra sure it wouldn't have triggered.
        SystemClock.sleep(20);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
    }

    @Test
    public void testLongPressAbortedByTouchCancel() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() - 10);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());

        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_CANCEL));
        // Wait past the long press timeout, to be extra sure it wouldn't have triggered.
        SystemClock.sleep(20);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
    }

    @Test
    public void testLongPressAbortedByTouchSlopPassedVertically() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() - 10);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());

        mUnderTest.onMotionEvent(generateCenteredMotionEventWithYOffset(ACTION_MOVE,
                -(TOUCH_SLOP + 1)));
        // Wait past the long press timeout, to be extra sure it wouldn't have triggered.
        SystemClock.sleep(20);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
    }

    @Test
    public void testLongPressAbortedByTouchSlopPassedHorizontally() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() - 10);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());

        mUnderTest.onMotionEvent(generateMotionEvent(ACTION_MOVE,
                mScreenWidth / 2f - (TOUCH_SLOP + 1), 0));
        // Wait past the long press timeout, to be extra sure it wouldn't have triggered.
        SystemClock.sleep(20);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
    }

    @Test
    public void testTouchOutsideNavHandleIgnored() {
        // Touch the far left side of the screen. (y=0 is top of navbar region, picked arbitrarily)
        mUnderTest.onMotionEvent(generateMotionEvent(ACTION_DOWN, 0, 0));
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Should be ignored because the x position was not centered in the navbar region.
        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, never()).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
    }

    @Test
    public void testHoverPassedToDelegate() {
        // Regardless of whether the delegate wants us to intercept, we tell it about hover events.
        when(mDelegate.allowInterceptByParent()).thenReturn(false);
        mUnderTest.onHoverEvent(generateCenteredMotionEvent(ACTION_HOVER_ENTER));

        verify(mDelegate).onHoverEvent(any());

        when(mDelegate.allowInterceptByParent()).thenReturn(true);
        mUnderTest.onHoverEvent(generateCenteredMotionEvent(ACTION_HOVER_ENTER));

        verify(mDelegate, times(2)).onHoverEvent(any());
    }

    /** Generate a motion event centered horizontally in the screen. */
    private MotionEvent generateCenteredMotionEvent(int motionAction) {
        return generateCenteredMotionEventWithYOffset(motionAction, 0);
    }

    /** Generate a motion event centered horizontally in the screen, with y offset. */
    private MotionEvent generateCenteredMotionEventWithYOffset(int motionAction, float y) {
        return generateMotionEvent(motionAction, mScreenWidth / 2f, y);
    }

    private static MotionEvent generateMotionEvent(int motionAction, float x, float y) {
        return MotionEvent.obtain(0, 0, motionAction, x, y, 0);
    }
}
