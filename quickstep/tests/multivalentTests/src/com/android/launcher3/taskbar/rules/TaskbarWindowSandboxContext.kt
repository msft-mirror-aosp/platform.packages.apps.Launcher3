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

package com.android.launcher3.taskbar.rules

import android.content.ContextWrapper
import com.android.launcher3.util.MainThreadInitializedObject.ObjectSandbox
import com.android.launcher3.util.SandboxApplication
import org.junit.rules.TestRule

/** Sandbox Context for running Taskbar tests. */
class TaskbarWindowSandboxContext private constructor(base: SandboxApplication) :
    ContextWrapper(base), ObjectSandbox by base, TestRule by base {

    companion object {
        /** Creates a [SandboxApplication] for Taskbar tests. */
        fun create(): TaskbarWindowSandboxContext {
            return TaskbarWindowSandboxContext(SandboxApplication())
        }
    }
}
