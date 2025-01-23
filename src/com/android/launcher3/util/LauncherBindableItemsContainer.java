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
package com.android.launcher3.util;

import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.PendingAppWidgetHostView;

import java.util.Set;

/**
 * Interface representing a container which can bind Launcher items with some utility methods
 */
public interface LauncherBindableItemsContainer {

    /**
     * Called to update workspace items as a result of
     * {@link com.android.launcher3.model.BgDataModel.Callbacks#bindItemsUpdated(Set)}
     */
    default void updateContainerItems(Set<ItemInfo> updates, ActivityContext context) {
        ItemOperator op = (info, v) -> {
            if (v instanceof BubbleTextView shortcut
                    && info instanceof WorkspaceItemInfo wii
                    && updates.contains(info)) {
                shortcut.applyFromWorkspaceItem(wii);
            } else if (info instanceof FolderInfo && v instanceof FolderIcon folderIcon) {
                folderIcon.updatePreviewItems(updates::contains);
            } else if (info instanceof AppPairInfo && v instanceof AppPairIcon appPairIcon) {
                appPairIcon.maybeRedrawForWorkspaceUpdate(updates::contains);
            } else if (v instanceof PendingAppWidgetHostView pendingView
                    && updates.contains(info)) {
                pendingView.applyState();
                pendingView.postProviderAvailabilityCheck();
            }

            // Iterate all items
            return false;
        };

        mapOverItems(op);
        Folder openFolder = Folder.getOpen(context);
        if (openFolder != null) {
            openFolder.iterateOverItems(op);
        }
    }

    /**
     * Map the operator over the shortcuts and widgets.
     *
     * @param op the operator to map over the shortcuts
     */
    void mapOverItems(ItemOperator op);

    interface ItemOperator {
        /**
         * Process the next itemInfo, possibly with side-effect on the next item.
         *
         * @param info info for the shortcut
         * @param view view for the shortcut
         * @return true if done, false to continue the map
         */
        boolean evaluate(ItemInfo info, View view);
    }
}
