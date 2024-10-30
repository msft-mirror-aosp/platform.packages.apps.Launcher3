/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.graphics;

import static com.android.launcher3.LauncherPrefs.THEMED_ICONS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.Themes.isThemedIconEnabled;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder.DeathRecipient;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.GridOption;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.shapes.AppShape;
import com.android.launcher3.shapes.AppShapesProvider;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.RunnableList;
import com.android.systemui.shared.Flags;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Exposes various launcher grid options and allows the caller to change them.
 * APIs:
 *      /shape_options: List of various available shape options, where each has following fields
 *          shape_key: key of the shape option
 *          title: translated title of the shape option
 *          path: path of the shape, assuming drawn on 100x100 view port
 *          is_default: true if this shape option is currently set to the system
 *
 *      /grid_options: List the various available grid options, where each has following fields
 *          name: key of the grid option
 *          rows: number of rows in the grid
 *          cols: number of columns in the grid
 *          preview_count: number of previews available for this grid option. The preview uri
 *                         looks like /preview/<grid-name>/<preview index starting with 0>
 *          is_default: true if this grid option is currently set to the system
 *
 *     /get_preview: Open a file stream for the grid preview
 *
 *     /default_grid: Call update to set the current shape and grid, with values
 *          shape_key: key of the shape to apply
 *          name: key of the grid to apply
 */
public class GridCustomizationsProvider extends ContentProvider {

    private static final String TAG = "GridCustomizationsProvider";

    private static final String KEY_SHAPE_KEY = "shape_key";
    private static final String KEY_TITLE = "title";
    private static final String KEY_PATH = "path";
    // is_default means if a certain option is currently set to the system
    private static final String KEY_IS_DEFAULT = "is_default";
    // Key of grid option. We do not change the name to grid_key for backward compatibility
    private static final String KEY_GRID_KEY = "name";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_COLS = "cols";
    private static final String KEY_PREVIEW_COUNT = "preview_count";

    private static final String KEY_SHAPE_OPTIONS = "/shape_options";
    private static final String KEY_GRID_OPTIONS = "/grid_options";
    private static final String KEY_SHAPE_GRID = "/default_grid";

    private static final String METHOD_GET_PREVIEW = "get_preview";

    private static final String GET_ICON_THEMED = "/get_icon_themed";
    private static final String SET_ICON_THEMED = "/set_icon_themed";
    private static final String ICON_THEMED = "/icon_themed";
    private static final String BOOLEAN_VALUE = "boolean_value";

    private static final String KEY_SURFACE_PACKAGE = "surface_package";
    private static final String KEY_CALLBACK = "callback";
    public static final String KEY_HIDE_BOTTOM_ROW = "hide_bottom_row";

    private static final int MESSAGE_ID_UPDATE_PREVIEW = 1337;
    private static final int MESSAGE_ID_UPDATE_SHAPE = 2586;
    private static final int MESSAGE_ID_UPDATE_GRID = 7414;
    private static final int MESSAGE_ID_UPDATE_COLOR = 856;

    // Set of all active previews used to track duplicate memory allocations
    private final Set<PreviewLifecycleObserver> mActivePreviews =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        String path = uri.getPath();
        if (context == null || path == null) {
            return null;
        }
        switch (path) {
            case KEY_SHAPE_OPTIONS: {
                if (Flags.newCustomizationPickerUi()) {
                    MatrixCursor cursor = new MatrixCursor(new String[]{
                            KEY_SHAPE_KEY, KEY_TITLE, KEY_PATH, KEY_IS_DEFAULT});
                    List<AppShape> shapes =  AppShapesProvider.INSTANCE.getShapes();
                    for (int i = 0; i < shapes.size(); i++) {
                        AppShape shape = shapes.get(i);
                        cursor.newRow()
                                .add(KEY_SHAPE_KEY, shape.getKey())
                                .add(KEY_TITLE, shape.getTitle())
                                .add(KEY_PATH, shape.getPath())
                                // TODO (b/348664593): We should fetch the currently-set shape
                                //  option from the preferences.
                                .add(KEY_IS_DEFAULT, i == 0);
                    }
                    return cursor;
                } else  {
                    return null;
                }
            }
            case KEY_GRID_OPTIONS: {
                MatrixCursor cursor = new MatrixCursor(new String[]{
                        KEY_GRID_KEY, KEY_ROWS, KEY_COLS, KEY_PREVIEW_COUNT, KEY_IS_DEFAULT});
                InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
                for (GridOption gridOption : idp.parseAllGridOptions(context)) {
                    cursor.newRow()
                            .add(KEY_GRID_KEY, gridOption.name)
                            .add(KEY_ROWS, gridOption.numRows)
                            .add(KEY_COLS, gridOption.numColumns)
                            .add(KEY_PREVIEW_COUNT, 1)
                            .add(KEY_IS_DEFAULT, idp.numColumns == gridOption.numColumns
                                    && idp.numRows == gridOption.numRows);
                }
                return cursor;
            }
            case GET_ICON_THEMED:
            case ICON_THEMED: {
                MatrixCursor cursor = new MatrixCursor(new String[]{BOOLEAN_VALUE});
                cursor.newRow().add(BOOLEAN_VALUE, isThemedIconEnabled(getContext()) ? 1 : 0);
                return cursor;
            }
            default:
                return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/launcher_grid";
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        String path = uri.getPath();
        Context context = getContext();
        if (path == null || context == null) {
            return 0;
        }
        switch (path) {
            case KEY_SHAPE_GRID: {
                if (Flags.newCustomizationPickerUi()) {
                    String shapeKey = values.getAsString(KEY_SHAPE_KEY);
                    Optional<AppShape> optionalShape = AppShapesProvider.INSTANCE.getShapes()
                            .stream().filter(shape -> shape.getKey().equals(shapeKey)).findFirst();
                    String pathToSet = optionalShape.map(AppShape::getPath).orElse(null);
                    // TODO (b/348664593): Apply shapeName to the system. This needs to be a
                    //  synchronous call.
                }
                String gridKey = values.getAsString(KEY_GRID_KEY);
                InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
                // Verify that this is a valid grid option
                GridOption match = null;
                for (GridOption option : idp.parseAllGridOptions(context)) {
                    String name = option.name;
                    if (name != null && name.equals(gridKey)) {
                        match = option;
                        break;
                    }
                }
                if (match == null) {
                    return 0;
                }

                idp.setCurrentGrid(context, gridKey);
                if (Flags.newCustomizationPickerUi()) {
                    try {
                        // Wait for device profile to be fully reloaded and applied to the launcher
                        loadModelSync(context);
                    } catch (ExecutionException | InterruptedException e) {
                        Log.e(TAG, "Fail to load model", e);
                    }
                }
                context.getContentResolver().notifyChange(uri, null);
                return 1;
            }
            case ICON_THEMED:
            case SET_ICON_THEMED: {
                LauncherPrefs.get(context)
                        .put(THEMED_ICONS, values.getAsBoolean(BOOLEAN_VALUE));
                context.getContentResolver().notifyChange(uri, null);
                return 1;
            }
            default:
                return 0;
        }
    }

    /**
     * Loads the model in memory synchronously
     */
    private void loadModelSync(Context context) throws ExecutionException, InterruptedException {
        Preconditions.assertNonUiThread();
        BgDataModel.Callbacks emptyCallbacks = new BgDataModel.Callbacks() { };
        LauncherModel launcherModel = LauncherAppState.getInstance(context).getModel();
        MAIN_EXECUTOR.submit(
                () -> launcherModel.addCallbacksAndLoad(emptyCallbacks)
        ).get();

        Executors.MODEL_EXECUTOR.submit(() -> { }).get();
        MAIN_EXECUTOR.submit(
                () -> launcherModel.removeCallbacks(emptyCallbacks)
        ).get();
    }

    @Override
    public Bundle call(@NonNull String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) {
            return null;
        }

        if (context.checkPermission("android.permission.BIND_WALLPAPER",
                Binder.getCallingPid(), Binder.getCallingUid())
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        if (METHOD_GET_PREVIEW.equals(method)) {
            return getPreview(extras);
        } else {
            return null;
        }
    }

    private synchronized Bundle getPreview(Bundle request) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        RunnableList lifeCycleTracker = new RunnableList();
        try {
            PreviewSurfaceRenderer renderer = new PreviewSurfaceRenderer(
                    getContext(), lifeCycleTracker, request);
            PreviewLifecycleObserver observer =
                    new PreviewLifecycleObserver(lifeCycleTracker, renderer);

            // Destroy previous renderers to avoid any duplicate memory
            mActivePreviews.stream().filter(observer::isSameRenderer).forEach(o ->
                    MAIN_EXECUTOR.execute(o.lifeCycleTracker::executeAllAndDestroy));

            renderer.loadAsync();
            lifeCycleTracker.add(() -> renderer.getHostToken().unlinkToDeath(observer, 0));
            renderer.getHostToken().linkToDeath(observer, 0);

            Bundle result = new Bundle();
            result.putParcelable(KEY_SURFACE_PACKAGE, renderer.getSurfacePackage());

            Messenger messenger =
                    new Messenger(new Handler(UI_HELPER_EXECUTOR.getLooper(), observer));
            Message msg = Message.obtain();
            msg.replyTo = messenger;
            result.putParcelable(KEY_CALLBACK, msg);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Unable to generate preview", e);
            MAIN_EXECUTOR.execute(lifeCycleTracker::executeAllAndDestroy);
            return null;
        }
    }

    private static class PreviewLifecycleObserver implements Handler.Callback, DeathRecipient {

        public final RunnableList lifeCycleTracker;
        public final PreviewSurfaceRenderer renderer;
        public boolean destroyed = false;

        PreviewLifecycleObserver(
                RunnableList lifeCycleTracker,
                PreviewSurfaceRenderer renderer) {
            this.lifeCycleTracker = lifeCycleTracker;
            this.renderer = renderer;
            lifeCycleTracker.add(() -> destroyed = true);
        }

        @Override
        public boolean handleMessage(Message message) {
            if (destroyed) {
                return true;
            }

            switch (message.what) {
                case MESSAGE_ID_UPDATE_PREVIEW:
                    renderer.hideBottomRow(message.getData().getBoolean(KEY_HIDE_BOTTOM_ROW));
                    break;
                case MESSAGE_ID_UPDATE_SHAPE:
                    if (Flags.newCustomizationPickerUi()) {
                        String shapeKey = message.getData().getString(KEY_SHAPE_KEY);
                        Optional<AppShape> optionalShape = AppShapesProvider.INSTANCE.getShapes()
                                .stream()
                                .filter(shape -> shape.getKey().equals(shapeKey))
                                .findFirst();
                        String pathToSet = optionalShape.map(AppShape::getPath).orElse(null);
                        // TODO (b/348664593): Update launcher preview with the given shape
                    }
                    break;
                case MESSAGE_ID_UPDATE_GRID:
                    String gridKey = message.getData().getString(KEY_GRID_KEY);
                    if (!TextUtils.isEmpty(gridKey)) {
                        renderer.updateGrid(gridKey);
                    }
                    break;
                case MESSAGE_ID_UPDATE_COLOR:
                    if (Flags.newCustomizationPickerUi()) {
                        renderer.previewColor(message.getData());
                    }
                    break;
                default:
                    // Unknown command, destroy lifecycle
                    Log.d(TAG, "Unknown preview command: " + message.what + ", destroying preview");
                    MAIN_EXECUTOR.execute(lifeCycleTracker::executeAllAndDestroy);
                    break;
            }

            return true;
        }

        @Override
        public void binderDied() {
            MAIN_EXECUTOR.execute(lifeCycleTracker::executeAllAndDestroy);
        }

        /**
         * Two renderers are considered same if they have the same host token and display Id
         */
        public boolean isSameRenderer(PreviewLifecycleObserver plo) {
            return plo != null
                    && plo.renderer.getHostToken().equals(renderer.getHostToken())
                    && plo.renderer.getDisplayId() == renderer.getDisplayId();
        }
    }
}
