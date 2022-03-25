/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.quickstep.util;

import android.annotation.NonNull;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.widget.NavigableAppWidgetHostView;
import com.android.systemui.shared.animation.UnfoldMoveFromCenterAnimator.TranslationApplier;

/**
 * Class that allows to set translations for move from center animation independently
 * from other translations for certain launcher views
 */
public class LauncherViewsMoveFromCenterTranslationApplier implements TranslationApplier {

    @Override
    public void apply(@NonNull View view, float x, float y) {
        if (view instanceof NavigableAppWidgetHostView) {
            ((NavigableAppWidgetHostView) view).setTranslationForMoveFromCenterAnimation(x, y);
        } else if (view instanceof BubbleTextView) {
            ((BubbleTextView) view).setTranslationForMoveFromCenterAnimation(x, y);
        } else if (view instanceof FolderIcon) {
            ((FolderIcon) view).setTranslationForMoveFromCenterAnimation(x, y);
        } else {
            view.setTranslationX(x);
            view.setTranslationY(y);
        }
    }
}
