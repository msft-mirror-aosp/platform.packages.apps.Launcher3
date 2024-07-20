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

package com.android.quickstep.recents.data

import android.view.Surface.ROTATION_90
import com.android.quickstep.util.RecentsOrientedState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [RecentsRotationStateRepository] */
class RecentsRotationStateRepositoryTest {
    private val recentsOrientedState = mock<RecentsOrientedState>()

    private val systemUnderTest = RecentsRotationStateRepository(recentsOrientedState)

    @Test
    fun orientedStateMappedCorrectly() {
        whenever(recentsOrientedState.recentsActivityRotation).thenReturn(ROTATION_90)

        assertThat(systemUnderTest.getRecentsRotationState())
            .isEqualTo(
                RecentsRotationStateRepository.RecentsRotationState(activityRotation = ROTATION_90)
            )
    }
}
