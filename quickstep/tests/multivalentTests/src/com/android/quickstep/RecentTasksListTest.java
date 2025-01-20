/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.Flags.FLAG_ENABLE_SEPARATE_EXTERNAL_DISPLAY_TASKS;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.TestCase.assertNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.KeyguardManager;
import android.app.TaskInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.launcher3.util.LooperExecutor;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.views.TaskViewType;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.shared.split.SplitBounds;
import com.android.wm.shell.shared.split.SplitScreenConstants;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RecentTasksListTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;
    @Mock
    private SystemUiProxy mSystemUiProxy;
    @Mock
    private TopTaskTracker mTopTaskTracker;

    // Class under test
    private RecentTasksList mRecentTasksList;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        LooperExecutor mockMainThreadExecutor = mock(LooperExecutor.class);
        KeyguardManager mockKeyguardManager = mock(KeyguardManager.class);

        // Set desktop mode supported
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(R.bool.config_isDesktopModeSupported)).thenReturn(true);

        mRecentTasksList = new RecentTasksList(mContext, mockMainThreadExecutor,
                mockKeyguardManager, mSystemUiProxy, mTopTaskTracker);
    }

    @Test
    public void onRecentTasksChanged_doesNotFetchTasks() throws Exception {
        mRecentTasksList.onRecentTasksChanged();
        verify(mSystemUiProxy, times(0))
                .getRecentTasks(anyInt(), anyInt());
    }

    @Test
    public void loadTasksInBackground_onlyKeys_noValidTaskDescription() throws Exception  {
        GroupedTaskInfo recentTaskInfos = GroupedTaskInfo.forSplitTasks(
                new RecentTaskInfo(), new RecentTaskInfo(), new SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ -1,
                        /* rightBottomTaskId = */ -1,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50));
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                true);

        assertEquals(1, taskList.size());
        taskList.get(0).getTasks().forEach(t -> assertNull(t.taskDescription.getLabel()));
    }

    @Test
    public void loadTasksInBackground_GetRecentTasksException() throws Exception  {
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenThrow(new SystemUiProxy.GetRecentTasksException("task load failed"));

        RecentTasksList.TaskLoadResult taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE, -1, false);

        assertThat(taskList.mRequestId).isEqualTo(-1);
        assertThat(taskList).isEmpty();
    }

    @Test
    public void loadTasksInBackground_moreThanKeys_hasValidTaskDescription() throws Exception  {
        String taskDescription = "Wheeee!";
        RecentTaskInfo task1 = new RecentTaskInfo();
        task1.taskDescription = new ActivityManager.TaskDescription(taskDescription);
        RecentTaskInfo task2 = new RecentTaskInfo();
        task2.taskDescription = new ActivityManager.TaskDescription();
        GroupedTaskInfo recentTaskInfos = GroupedTaskInfo.forSplitTasks(task1, task2,
                new SplitBounds(
                        /* leftTopBounds = */ new Rect(),
                        /* rightBottomBounds = */ new Rect(),
                        /* leftTopTaskId = */ -1,
                        /* rightBottomTaskId = */ -1,
                        /* snapPosition = */ SplitScreenConstants.SNAP_TO_2_50_50));
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                false);

        assertEquals(1, taskList.size());
        var tasks = taskList.get(0).getTasks();
        assertEquals(2, tasks.size());
        assertEquals(taskDescription, tasks.get(0).taskDescription.getLabel());
        assertNull(tasks.get(1).taskDescription.getLabel());
    }

    @Test
    public void loadTasksInBackground_freeformTask_createsDesktopTask() throws Exception  {
        List<TaskInfo> tasks = Arrays.asList(
                createRecentTaskInfo(1 /* taskId */, DEFAULT_DISPLAY),
                createRecentTaskInfo(4 /* taskId */, DEFAULT_DISPLAY),
                createRecentTaskInfo(5 /* taskId */, 1 /* displayId */),
                createRecentTaskInfo(6 /* taskId */, 1 /* displayId */));
        GroupedTaskInfo recentTaskInfos = GroupedTaskInfo.forFreeformTasks(
                tasks, Collections.emptySet() /* minimizedTaskIds */);
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE /* numTasks */, -1 /* requestId */, false /* loadKeysOnly */);

        assertEquals(1, taskList.size());
        assertEquals(TaskViewType.DESKTOP, taskList.get(0).taskViewType);
        List<Task> actualFreeformTasks = taskList.get(0).getTasks();
        assertEquals(4, actualFreeformTasks.size());
        assertEquals(1, actualFreeformTasks.get(0).key.id);
        assertFalse(actualFreeformTasks.get(0).isMinimized);
        assertEquals(4, actualFreeformTasks.get(1).key.id);
        assertFalse(actualFreeformTasks.get(1).isMinimized);
        assertEquals(5, actualFreeformTasks.get(2).key.id);
        assertFalse(actualFreeformTasks.get(2).isMinimized);
        assertEquals(6, actualFreeformTasks.get(3).key.id);
        assertFalse(actualFreeformTasks.get(3).isMinimized);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SEPARATE_EXTERNAL_DISPLAY_TASKS)
    public void loadTasksInBackground_freeformTask_createsDesktopTaskPerDisplay() throws Exception {
        List<TaskInfo> tasks = Arrays.asList(
                createRecentTaskInfo(1 /* taskId */, DEFAULT_DISPLAY),
                createRecentTaskInfo(4 /* taskId */, DEFAULT_DISPLAY),
                createRecentTaskInfo(5 /* taskId */, 1 /* displayId */),
                createRecentTaskInfo(6 /* taskId */, 1 /* displayId */));
        GroupedTaskInfo recentTaskInfos = GroupedTaskInfo.forFreeformTasks(
                tasks, Collections.emptySet() /* minimizedTaskIds */);
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE /* numTasks */, -1 /* requestId */, false /* loadKeysOnly */);

        assertEquals(2, taskList.size());
        assertEquals(TaskViewType.DESKTOP, taskList.get(0).taskViewType);
        List<Task> actualFreeformTasksDefaultDisplay = taskList.get(0).getTasks();
        assertEquals(2, actualFreeformTasksDefaultDisplay.size());
        assertEquals(1, actualFreeformTasksDefaultDisplay.get(0).key.id);
        assertFalse(actualFreeformTasksDefaultDisplay.get(0).isMinimized);
        assertEquals(4, actualFreeformTasksDefaultDisplay.get(1).key.id);
        assertFalse(actualFreeformTasksDefaultDisplay.get(1).isMinimized);

        List<Task> actualFreeformTasksExternalDisplay = taskList.get(1).getTasks();
        assertEquals(2, actualFreeformTasksExternalDisplay.size());
        assertEquals(5, actualFreeformTasksExternalDisplay.get(0).key.id);
        assertFalse(actualFreeformTasksExternalDisplay.get(0).isMinimized);
        assertEquals(6, actualFreeformTasksExternalDisplay.get(1).key.id);
        assertFalse(actualFreeformTasksExternalDisplay.get(1).isMinimized);
    }

    @Test
    public void loadTasksInBackground_freeformTask_onlyMinimizedTasks_createDesktopTask()
            throws Exception {
        List<TaskInfo> tasks = Arrays.asList(
                createRecentTaskInfo(1 /* taskId */, DEFAULT_DISPLAY),
                createRecentTaskInfo(4 /* taskId */, DEFAULT_DISPLAY),
                createRecentTaskInfo(5 /* taskId */, DEFAULT_DISPLAY));
        Set<Integer> minimizedTaskIds =
                Arrays.stream(new Integer[]{1, 4, 5}).collect(Collectors.toSet());
        GroupedTaskInfo recentTaskInfos = GroupedTaskInfo.forFreeformTasks(tasks, minimizedTaskIds);
        when(mSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE /* numTasks */, -1 /* requestId */, false /* loadKeysOnly */);

        assertEquals(1, taskList.size());
        assertEquals(TaskViewType.DESKTOP, taskList.get(0).taskViewType);
        List<Task> actualFreeformTasks = taskList.get(0).getTasks();
        assertEquals(3, actualFreeformTasks.size());
        assertEquals(1, actualFreeformTasks.get(0).key.id);
        assertTrue(actualFreeformTasks.get(0).isMinimized);
        assertEquals(4, actualFreeformTasks.get(1).key.id);
        assertTrue(actualFreeformTasks.get(1).isMinimized);
        assertEquals(5, actualFreeformTasks.get(2).key.id);
        assertTrue(actualFreeformTasks.get(2).isMinimized);
    }

    private TaskInfo createRecentTaskInfo(int taskId, int displayId) {
        RecentTaskInfo recentTaskInfo = new RecentTaskInfo();
        recentTaskInfo.taskId = taskId;
        recentTaskInfo.displayId = displayId;
        return recentTaskInfo;
    }
}
