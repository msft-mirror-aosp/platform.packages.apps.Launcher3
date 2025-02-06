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

import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.MainThreadInitializedObject.ObjectSandbox
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCacheSandbox
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.quickstep.SystemUiProxy
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import javax.inject.Inject
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Include additional bindings when building a [TaskbarSandboxComponent]. */
typealias TaskbarComponentBinder =
    TaskbarWindowSandboxContext.(TaskbarSandboxComponent.Builder) -> Unit

/**
 * [SandboxApplication] for running Taskbar tests.
 *
 * Tests need to run on a [VirtualDisplay] to avoid conflicting with Launcher's Taskbar on the
 * [DEFAULT_DISPLAY] (i.e. test is executing on a device).
 */
class TaskbarWindowSandboxContext
private constructor(
    private val base: SandboxApplication,
    val virtualDisplay: VirtualDisplay,
    private val componentBinder: TaskbarComponentBinder?,
) : ContextWrapper(base), ObjectSandbox by base, TestRule {

    val settingsCacheSandbox = SettingsCacheSandbox()

    private val virtualDisplayRule =
        object : ExternalResource() {
            override fun after() = virtualDisplay.release()
        }

    private val singletonSetupRule =
        object : ExternalResource() {
            override fun before() {
                val context = this@TaskbarWindowSandboxContext
                val builder =
                    DaggerTaskbarSandboxComponent.builder()
                        .bindSystemUiProxy(SystemUiProxy(context))
                        .bindSettingsCache(settingsCacheSandbox.cache)
                componentBinder?.invoke(context, builder)
                base.initDaggerComponent(builder)
            }
        }

    override fun apply(statement: Statement, description: Description): Statement {
        return RuleChain.outerRule(virtualDisplayRule)
            .around(base)
            .around(singletonSetupRule)
            .apply(statement, description)
    }

    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "TaskbarSandboxDisplay"

        /** Creates a [SandboxApplication] for Taskbar tests. */
        fun create(componentBinder: TaskbarComponentBinder? = null): TaskbarWindowSandboxContext {
            val base = ApplicationProvider.getApplicationContext<Context>()
            val displayManager = checkNotNull(base.getSystemService(DisplayManager::class.java))

            // Create virtual display to avoid clashing with Taskbar on default display.
            val virtualDisplay =
                base.resources.displayMetrics.let {
                    displayManager.createVirtualDisplay(
                        VIRTUAL_DISPLAY_NAME,
                        it.widthPixels,
                        it.heightPixels,
                        it.densityDpi,
                        /* surface= */ null,
                        /* flags= */ 0,
                    )
                }

            return TaskbarWindowSandboxContext(
                SandboxApplication(base.createDisplayContext(virtualDisplay.display)),
                virtualDisplay,
                componentBinder,
            )
        }
    }
}

/** A wrapper over display controller which allows modifying the underlying info */
@LauncherAppSingleton
class DisplayControllerSpy
@Inject
constructor(
    @ApplicationContext context: Context,
    wmProxy: WindowManagerProxy,
    prefs: LauncherPrefs,
    lifecycle: DaggerSingletonTracker,
) : DisplayController(context, wmProxy, prefs, lifecycle) {

    var infoModifier: ((Info) -> Info)? = null

    override fun getInfo(): Info = infoModifier?.invoke(super.getInfo()) ?: super.getInfo()
}

@Module
abstract class DisplayControllerModule {
    @Binds abstract fun bindDisplayController(controller: DisplayControllerSpy): DisplayController
}

@LauncherAppSingleton
@Component(
    modules = [AllModulesForTest::class, FakePrefsModule::class, DisplayControllerModule::class]
)
interface TaskbarSandboxComponent : LauncherAppComponent {

    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        @BindsInstance fun bindSystemUiProxy(proxy: SystemUiProxy): Builder

        @BindsInstance fun bindSettingsCache(settingsCache: SettingsCache): Builder

        override fun build(): TaskbarSandboxComponent
    }
}
