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
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Reads and writes the per-app state (accessibility/isolation) configuration. */
public class AppStateStore {

    private static final String TAG = "AppStateStore";
    private static final String CONFIG_KEY = "spoof_appstate_config";
    private static final String LEGACY_GAMEPROPS_KEY = "spoof_gameprops_config";
    private static final String APPS_KEY = "apps";
    private static final String STASH_KEY = "appsDisabled";

    public static final class Entry {
        public final String packageName;
        public final String appName;
        public final boolean hideAccessibility;
        public final boolean isolation;
        public final boolean showRealSettings;

        Entry(String packageName, String appName, boolean hideAccessibility,
                boolean isolation, boolean showRealSettings) {
            this.packageName = packageName;
            this.appName = appName;
            this.hideAccessibility = hideAccessibility;
            this.isolation = isolation;
            this.showRealSettings = showRealSettings;
        }

        public boolean hasAnyFlag() {
            return hideAccessibility || isolation || showRealSettings;
        }
    }

    private final Context mContext;
    private final List<Entry> mEntries = new ArrayList<>();
    private boolean mEnabled;

    public AppStateStore(@NonNull Context context) {
        mContext = context.getApplicationContext();
        migrateFromGamePropsIfNeeded(mContext);
        reload();
    }

    public void reload() {
        loadConfig();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        saveConfig();
    }

    public int getConfiguredAppCount() {
        return mEntries.size();
    }

    public List<Entry> getEntries() {
        List<Entry> sorted = new ArrayList<>(mEntries);
        Collections.sort(sorted, (left, right) ->
                left.appName.compareToIgnoreCase(right.appName));
        return sorted;
    }

    @Nullable
    public Entry findEntry(String packageName) {
        for (Entry entry : mEntries) {
            if (entry.packageName.equals(packageName)) {
                return entry;
            }
        }
        return null;
    }

    public void putEntry(String packageName, String appName, boolean hideAccessibility,
            boolean isolation, boolean showRealSettings) {
        Entry existing = findEntry(packageName);
        if (existing != null) {
            mEntries.remove(existing);
        }
        mEntries.add(new Entry(packageName, appName, hideAccessibility, isolation,
                showRealSettings));
        saveConfig();
    }

    public void removeEntry(String packageName) {
        Entry existing = findEntry(packageName);
        if (existing != null) {
            mEntries.remove(existing);
            saveConfig();
        }
    }

    private void loadConfig() {
        mEntries.clear();
        mEnabled = false;
        String content = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                CONFIG_KEY, UserHandle.USER_SYSTEM);
        if (content == null || content.isEmpty()) {
            return;
        }
        try {
            JSONObject json = new JSONObject(content);
            mEnabled = json.optBoolean("enabled", false);
            JSONObject apps = json.optJSONObject(mEnabled ? APPS_KEY : STASH_KEY);
            boolean resave = false;
            if (apps == null && !mEnabled) {
                apps = json.optJSONObject(APPS_KEY);
                resave = apps != null && apps.length() > 0;
            }
            if (apps == null) {
                return;
            }
            PackageManager packageManager = mContext.getPackageManager();
            Iterator<String> packages = apps.keys();
            while (packages.hasNext()) {
                String packageName = packages.next();
                JSONObject obj = apps.getJSONObject(packageName);
                String appName;
                try {
                    ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
                    appName = packageManager.getApplicationLabel(info).toString();
                } catch (Exception e) {
                    appName = packageName;
                }
                mEntries.add(new Entry(packageName, appName,
                        obj.optBoolean("hideAccessibility", false),
                        obj.optBoolean("isolation", false),
                        obj.optBoolean("showRealSettings", false)));
            }
            if (resave) {
                saveConfig();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load app state config", e);
        }
    }

    private void saveConfig() {
        try {
            JSONObject root = new JSONObject();
            JSONObject apps = new JSONObject();
            root.put("enabled", mEnabled);
            for (Entry entry : mEntries) {
                JSONObject obj = new JSONObject();
                obj.put("hideAccessibility", entry.hideAccessibility);
                obj.put("isolation", entry.isolation);
                obj.put("showRealSettings", entry.showRealSettings);
                apps.put(entry.packageName, obj);
            }
            root.put(mEnabled ? APPS_KEY : STASH_KEY, apps);
            Settings.Secure.putStringForUser(mContext.getContentResolver(), CONFIG_KEY,
                    root.toString(2), UserHandle.USER_SYSTEM);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save app state config", e);
        }
    }

    private static void migrateFromGamePropsIfNeeded(@NonNull Context context) {
        String current = Settings.Secure.getStringForUser(context.getContentResolver(),
                CONFIG_KEY, UserHandle.USER_SYSTEM);
        String legacy = Settings.Secure.getStringForUser(context.getContentResolver(),
                LEGACY_GAMEPROPS_KEY, UserHandle.USER_SYSTEM);
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(legacy);
            JSONObject games = root.optJSONObject("games");
            if (games == null) {
                return;
            }
            if (current == null || current.isEmpty()) {
                JSONObject out = new JSONObject();
                JSONObject apps = new JSONObject();
                boolean found = false;
                Iterator<String> keys = games.keys();
                while (keys.hasNext()) {
                    String pkg = keys.next();
                    JSONObject entry = games.optJSONObject(pkg);
                    if (entry == null) {
                        continue;
                    }
                    boolean hide = entry.optBoolean("hideAccessibility", false);
                    boolean iso = entry.optBoolean("isolation", false);
                    boolean real = entry.optBoolean("showRealSettings", false);
                    if (hide || iso || real) {
                        JSONObject obj = new JSONObject();
                        obj.put("hideAccessibility", hide);
                        obj.put("isolation", iso);
                        obj.put("showRealSettings", real);
                        apps.put(pkg, obj);
                        found = true;
                    }
                }
                if (found) {
                    boolean enabled = root.optBoolean("enabled", false);
                    out.put("enabled", enabled);
                    out.put(enabled ? APPS_KEY : STASH_KEY, apps);
                    Settings.Secure.putStringForUser(context.getContentResolver(), CONFIG_KEY,
                            out.toString(2), UserHandle.USER_SYSTEM);
                }
            }
            stripLegacyAppStateFlags(context, root, games);
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate app state config", e);
        }
    }

    private static void stripLegacyAppStateFlags(@NonNull Context context, @NonNull JSONObject root,
            @NonNull JSONObject games) throws Exception {
        JSONObject cleaned = new JSONObject();
        boolean stripped = false;
        Iterator<String> keys = games.keys();
        while (keys.hasNext()) {
            String pkg = keys.next();
            JSONObject entry = games.optJSONObject(pkg);
            if (entry == null) {
                cleaned.put(pkg, games.get(pkg));
                continue;
            }
            if (entry.has("hideAccessibility") || entry.has("isolation")
                    || entry.has("showRealSettings")) {
                entry.remove("hideAccessibility");
                entry.remove("isolation");
                entry.remove("showRealSettings");
                stripped = true;
            }
            cleaned.put(pkg, entry);
        }
        if (!stripped) {
            return;
        }
        root.put("games", cleaned);
        Settings.Secure.putStringForUser(context.getContentResolver(), LEGACY_GAMEPROPS_KEY,
                root.toString(2), UserHandle.USER_SYSTEM);
    }
}
