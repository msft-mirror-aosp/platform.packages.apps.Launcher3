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

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.TestCase.assertNull;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.KeyguardManager;

import androidx.test.filters.SmallTest;

import com.android.launcher3.util.LooperExecutor;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.views.TaskViewType;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.util.GroupedRecentTaskInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SmallTest
public class RecentTasksListTest {

    @Mock
    private SystemUiProxy mockSystemUiProxy;
    @Mock
    private TopTaskTracker mTopTaskTracker;

    // Class under test
    private RecentTasksList mRecentTasksList;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        LooperExecutor mockMainThreadExecutor = mock(LooperExecutor.class);
        KeyguardManager mockKeyguardManager = mock(KeyguardManager.class);
        mRecentTasksList = new RecentTasksList(mockMainThreadExecutor, mockKeyguardManager,
                mockSystemUiProxy, mTopTaskTracker);
    }

    @Test
    public void onRecentTasksChanged_doesNotFetchTasks() throws Exception {
        mRecentTasksList.onRecentTasksChanged();
        verify(mockSystemUiProxy, times(0))
                .getRecentTasks(anyInt(), anyInt());
    }

    @Test
    public void loadTasksInBackground_onlyKeys_noValidTaskDescription() throws Exception  {
        GroupedRecentTaskInfo recentTaskInfos = GroupedRecentTaskInfo.forSplitTasks(
                new ActivityManager.RecentTaskInfo(), new ActivityManager.RecentTaskInfo(), null);
        when(mockSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                true);

        assertEquals(1, taskList.size());
        assertNull(taskList.get(0).task1.taskDescription.getLabel());
        assertNull(taskList.get(0).task2.taskDescription.getLabel());
    }

    @Test
    public void loadTasksInBackground_GetRecentTasksException() throws Exception  {
        when(mockSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenThrow(new SystemUiProxy.GetRecentTasksException("task load failed"));

        RecentTasksList.TaskLoadResult taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE, -1, false);

        assertThat(taskList.mRequestId).isEqualTo(-1);
        assertThat(taskList).isEmpty();
    }

    @Test
    public void loadTasksInBackground_moreThanKeys_hasValidTaskDescription() throws Exception  {
        String taskDescription = "Wheeee!";
        ActivityManager.RecentTaskInfo task1 = new ActivityManager.RecentTaskInfo();
        task1.taskDescription = new ActivityManager.TaskDescription(taskDescription);
        ActivityManager.RecentTaskInfo task2 = new ActivityManager.RecentTaskInfo();
        task2.taskDescription = new ActivityManager.TaskDescription();
        GroupedRecentTaskInfo recentTaskInfos = GroupedRecentTaskInfo.forSplitTasks(task1, task2,
                null);
        when(mockSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(Integer.MAX_VALUE, -1,
                false);

        assertEquals(1, taskList.size());
        assertEquals(taskDescription, taskList.get(0).task1.taskDescription.getLabel());
        assertNull(taskList.get(0).task2.taskDescription.getLabel());
    }

    @Test
    public void loadTasksInBackground_freeformTask_createsDesktopTask() throws Exception  {
        ActivityManager.RecentTaskInfo[] tasks = {
                createRecentTaskInfo(1 /* taskId */),
                createRecentTaskInfo(4 /* taskId */),
                createRecentTaskInfo(5 /* taskId */)};
        GroupedRecentTaskInfo recentTaskInfos = GroupedRecentTaskInfo.forFreeformTasks(
                tasks, Collections.emptySet() /* minimizedTaskIds */);
        when(mockSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE /* numTasks */, -1 /* requestId */, false /* loadKeysOnly */);

        assertEquals(1, taskList.size());
        assertEquals(TaskViewType.DESKTOP, taskList.get(0).taskViewType);
        List<Task> actualFreeformTasks = taskList.get(0).getTasks();
        assertEquals(3, actualFreeformTasks.size());
        assertEquals(1, actualFreeformTasks.get(0).key.id);
        assertEquals(4, actualFreeformTasks.get(1).key.id);
        assertEquals(5, actualFreeformTasks.get(2).key.id);
    }

    @Test
    public void loadTasksInBackground_freeformTask_onlyMinimizedTasks_doesNotCreateDesktopTask()
            throws Exception {
        ActivityManager.RecentTaskInfo[] tasks = {
                createRecentTaskInfo(1 /* taskId */),
                createRecentTaskInfo(4 /* taskId */),
                createRecentTaskInfo(5 /* taskId */)};
        Set<Integer> minimizedTaskIds =
                Arrays.stream(new Integer[]{1, 4, 5}).collect(Collectors.toSet());
        GroupedRecentTaskInfo recentTaskInfos =
                GroupedRecentTaskInfo.forFreeformTasks(tasks, minimizedTaskIds);
        when(mockSystemUiProxy.getRecentTasks(anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(recentTaskInfos)));

        List<GroupTask> taskList = mRecentTasksList.loadTasksInBackground(
                Integer.MAX_VALUE /* numTasks */, -1 /* requestId */, false /* loadKeysOnly */);

        assertEquals(0, taskList.size());
    }

    private ActivityManager.RecentTaskInfo createRecentTaskInfo(int taskId) {
        ActivityManager.RecentTaskInfo recentTaskInfo = new ActivityManager.RecentTaskInfo();
        recentTaskInfo.taskId = taskId;
        return recentTaskInfo;
    }
}
