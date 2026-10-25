/*
 * Copyright (C) 2026 AxionOS
 * Copyright (C) 2026 VoltageOS
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
package com.power.hub.fragments;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Loads installed apps once for every spoofing picker. */
public final class SpoofApps {

    private SpoofApps() {
    }

    public static final class AppEntry {
        public final String packageName;
        public final String label;
        public final Drawable icon;
        public final boolean isSystem;
        public final boolean isOverlay;

        AppEntry(String packageName, String label, Drawable icon, boolean isSystem,
                boolean isOverlay) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.isSystem = isSystem;
            this.isOverlay = isOverlay;
        }
    }

    public interface LoadCallback {
        void onLoaded(List<AppEntry> apps);
    }

    public static void load(@NonNull Context context, @Nullable Set<String> excluded,
            @NonNull LoadCallback callback) {
        Context appContext = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            PackageManager packageManager = appContext.getPackageManager();
            List<ApplicationInfo> installed = packageManager.getInstalledApplications(
                    PackageManager.GET_META_DATA);
            List<AppEntry> entries = new ArrayList<>(installed.size());
            for (ApplicationInfo info : installed) {
                if (excluded != null && excluded.contains(info.packageName)) {
                    continue;
                }
                CharSequence label;
                try {
                    label = packageManager.getApplicationLabel(info);
                } catch (Exception ignored) {
                    label = info.packageName;
                }
                Drawable icon;
                try {
                    icon = info.loadIcon(packageManager);
                } catch (Exception ignored) {
                    icon = ContextCompat.getDrawable(appContext,
                            android.R.drawable.sym_def_app_icon);
                }
                boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean overlay;
                try {
                    overlay = info.isResourceOverlay();
                } catch (Exception ignored) {
                    overlay = false;
                }
                entries.add(new AppEntry(info.packageName, label.toString(), icon, system,
                        overlay));
            }
            Collections.sort(entries, Comparator.comparing(
                    entry -> entry.label.toLowerCase()));
            handler.post(() -> callback.onLoaded(entries));
        }, "SpoofAppsLoader").start();
    }

    public static Drawable loadIcon(@NonNull Context context, @NonNull String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getApplicationInfo(packageName, 0)
                    .loadIcon(packageManager);
        } catch (Exception ignored) {
            return ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon);
        }
    }

    public static String label(@NonNull Context context, @NonNull String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)).toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }
}
