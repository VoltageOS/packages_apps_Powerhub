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

import com.android.settings.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes the per-app spoof configuration and custom profiles. */
public class AppSpoofStore {

    private static final String TAG = "AppSpoofStore";
    private static final String LEGACY_CONFIG_DIR = "/data/system/gameprops";
    private static final String LEGACY_CONFIG_FILE = "gameprops.json";
    private static final String CONFIG_KEY = "spoof_gameprops_config";
    private static final String PRESETS_KEY = "game_spoofing_user_presets";

    public static final class Config {
        public final String packageName;
        public final String appName;
        public final String profileName;
        public final Map<String, String> props;

        Config(String packageName, String appName, String profileName,
                Map<String, String> props) {
            this.packageName = packageName;
            this.appName = appName;
            this.profileName = profileName;
            this.props = props;
        }
    }

    public static final class Profile {
        public final String name;
        public final Map<String, String> props;
        public final boolean isCustom;

        Profile(String name, Map<String, String> props, boolean isCustom) {
            this.name = name;
            this.props = props;
            this.isCustom = isCustom;
        }
    }

    private final Context mContext;
    private final List<Config> mConfigs = new ArrayList<>();
    private final List<Profile> mProfiles = new ArrayList<>();
    private boolean mEnabled;
    private boolean mDebug;

    public AppSpoofStore(@NonNull Context context) {
        mContext = context.getApplicationContext();
        migrateLegacyConfigIfNeeded(mContext);
        reload();
    }

    public void reload() {
        loadProfiles();
        loadConfig();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
        saveConfig();
    }

    public boolean isDebug() {
        return mDebug;
    }

    public void setDebug(boolean debug) {
        mDebug = debug;
        saveConfig();
    }

    public int getConfiguredAppCount() {
        return mConfigs.size();
    }

    public List<Config> getConfigs() {
        List<Config> sorted = new ArrayList<>(mConfigs);
        Collections.sort(sorted, (left, right) ->
                left.appName.compareToIgnoreCase(right.appName));
        return sorted;
    }

    public List<Profile> getProfiles() {
        return new ArrayList<>(mProfiles);
    }

    @Nullable
    public Config findConfig(String packageName) {
        for (Config config : mConfigs) {
            if (config.packageName.equals(packageName)) {
                return config;
            }
        }
        return null;
    }

    @Nullable
    public Profile findProfile(String name) {
        for (Profile profile : mProfiles) {
            if (profile.name.equals(name)) {
                return profile;
            }
        }
        return null;
    }

    public boolean isCustomProfile(String name) {
        Profile profile = findProfile(name);
        return profile != null && profile.isCustom;
    }

    public void applyProfile(String packageName, String appName, @NonNull Profile profile) {
        Config existing = findConfig(packageName);
        if (existing != null) {
            mConfigs.remove(existing);
        }
        mConfigs.add(new Config(packageName, appName, profile.name,
                new LinkedHashMap<>(profile.props)));
        saveConfig();
    }

    public void updateProperties(String packageName, @NonNull Map<String, String> props) {
        Config existing = findConfig(packageName);
        if (existing == null) {
            return;
        }
        setProperties(packageName, existing.appName, props);
    }

    public void setProperties(String packageName, String appName,
            @NonNull Map<String, String> props) {
        Config existing = findConfig(packageName);
        if (existing != null) {
            mConfigs.remove(existing);
        }
        mConfigs.add(new Config(packageName, appName, matchProfileName(props),
                new LinkedHashMap<>(props)));
        saveConfig();
    }

    public void removeConfig(String packageName) {
        Config existing = findConfig(packageName);
        if (existing != null) {
            mConfigs.remove(existing);
            saveConfig();
        }
    }

    public void saveProfile(@Nullable String originalName, String name,
            @NonNull Map<String, String> props) {
        if (originalName != null) {
            Profile previous = findProfile(originalName);
            if (previous != null && previous.isCustom) {
                mProfiles.remove(previous);
            }
        }
        mProfiles.add(new Profile(name, new LinkedHashMap<>(props), true));
        saveProfiles();
        loadConfig();
    }

    public void deleteProfile(String name) {
        Profile profile = findProfile(name);
        if (profile == null || !profile.isCustom) {
            return;
        }
        mProfiles.remove(profile);
        saveProfiles();
        loadConfig();
    }

    public String matchProfileName(@NonNull Map<String, String> props) {
        for (Profile profile : mProfiles) {
            if (profile.props.equals(props)) {
                return profile.name;
            }
        }
        String manufacturer = props.get("MANUFACTURER");
        String model = props.get("MODEL");
        if (manufacturer != null && model != null) {
            return manufacturer + " " + model;
        }
        if (model != null) {
            return model;
        }
        return mContext.getString(R.string.app_spoof_custom_profile);
    }

    private void loadConfig() {
        mConfigs.clear();
        mEnabled = false;
        mDebug = false;
        String content = readConfig();
        if (content == null || content.isEmpty()) {
            return;
        }
        try {
            JSONObject json = new JSONObject(content);
            mEnabled = json.optBoolean("enabled", false);
            mDebug = json.optBoolean("debug", false);
            JSONObject apps = json.optJSONObject("games");
            if (apps == null) {
                return;
            }
            PackageManager packageManager = mContext.getPackageManager();
            Iterator<String> packages = apps.keys();
            while (packages.hasNext()) {
                String packageName = packages.next();
                JSONObject propsJson = apps.getJSONObject(packageName);
                if (propsJson.has("props")) {
                    propsJson = propsJson.optJSONObject("props");
                }
                Map<String, String> props = new LinkedHashMap<>();
                Iterator<String> propKeys = propsJson.keys();
                while (propKeys.hasNext()) {
                    String key = propKeys.next();
                    props.put(key, propsJson.getString(key));
                }
                String appName;
                try {
                    ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
                    appName = packageManager.getApplicationLabel(info).toString();
                } catch (Exception e) {
                    appName = packageName;
                }
                mConfigs.add(new Config(packageName, appName, matchProfileName(props), props));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load spoof config", e);
        }
    }

    private void saveConfig() {
        try {
            JSONObject root = new JSONObject();
            JSONObject apps = new JSONObject();
            root.put("enabled", mEnabled);
            root.put("debug", mDebug);
            for (Config config : mConfigs) {
                JSONObject props = new JSONObject();
                for (Map.Entry<String, String> entry : config.props.entrySet()) {
                    props.put(entry.getKey(), entry.getValue());
                }
                apps.put(config.packageName, props);
            }
            root.put("games", apps);
            String serialized = root.toString(2);
            Settings.Secure.putStringForUser(mContext.getContentResolver(), CONFIG_KEY,
                    serialized, UserHandle.myUserId());
            Settings.Secure.putStringForUser(mContext.getContentResolver(), CONFIG_KEY,
                    serialized, UserHandle.USER_SYSTEM);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save spoof config", e);
        }
    }

    private String readConfig() {
        String content = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                CONFIG_KEY, UserHandle.myUserId());
        if (content != null && !content.isEmpty()) {
            return content;
        }
        return Settings.Secure.getStringForUser(mContext.getContentResolver(),
                CONFIG_KEY, UserHandle.USER_SYSTEM);
    }

    private void loadProfiles() {
        mProfiles.clear();
        mProfiles.addAll(defaultProfiles());
        try {
            String jsonString = Settings.Secure.getString(mContext.getContentResolver(),
                    PRESETS_KEY);
            if (jsonString == null || jsonString.isEmpty()) {
                return;
            }
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String profileName = obj.getString("name");
                JSONObject propsJson = obj.getJSONObject("props");
                Map<String, String> props = new LinkedHashMap<>();
                Iterator<String> propKeys = propsJson.keys();
                while (propKeys.hasNext()) {
                    String key = propKeys.next();
                    props.put(key, propsJson.getString(key));
                }
                mProfiles.add(new Profile(profileName, props, true));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom profiles", e);
        }
    }

    private void saveProfiles() {
        try {
            JSONArray root = new JSONArray();
            for (Profile profile : mProfiles) {
                if (!profile.isCustom) {
                    continue;
                }
                JSONObject preset = new JSONObject();
                JSONObject props = new JSONObject();
                for (Map.Entry<String, String> entry : profile.props.entrySet()) {
                    props.put(entry.getKey(), entry.getValue());
                }
                preset.put("name", profile.name);
                preset.put("props", props);
                root.put(preset);
            }
            Settings.Secure.putString(mContext.getContentResolver(), PRESETS_KEY,
                    root.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save custom profiles", e);
        }
    }

    private static void migrateLegacyConfigIfNeeded(@NonNull Context context) {
        String current = Settings.Secure.getStringForUser(context.getContentResolver(),
                CONFIG_KEY, UserHandle.USER_SYSTEM);
        if (current != null && !current.isEmpty()) {
            return;
        }
        File file = new File(LEGACY_CONFIG_DIR, LEGACY_CONFIG_FILE);
        if (!file.exists() || !file.canRead()) {
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8);
            Settings.Secure.putStringForUser(context.getContentResolver(), CONFIG_KEY, content,
                    UserHandle.USER_SYSTEM);
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate legacy config", e);
        }
    }

    private static List<Profile> defaultProfiles() {
        List<Profile> profiles = new ArrayList<>();
        profiles.add(new Profile("ROG Phone 8 Pro",
                mapOf("MODEL", "ASUS_AI2401_A", "MANUFACTURER", "asus"), false));
        profiles.add(new Profile("Galaxy S24 Ultra",
                mapOf("MODEL", "SM-S928B", "MANUFACTURER", "samsung"), false));
        profiles.add(new Profile("Xiaomi 13 Pro",
                mapOf("MODEL", "2210132C", "MANUFACTURER", "Xiaomi"), false));
        profiles.add(new Profile("OnePlus 9 Pro",
                mapOf("MODEL", "LE2101", "MANUFACTURER", "OnePlus"), false));
        profiles.add(new Profile("Black Shark 4",
                mapOf("MODEL", "2SM-X706B", "MANUFACTURER", "blackshark"), false));
        profiles.add(new Profile("Lenovo Y700",
                mapOf("MODEL", "Lenovo TB-9707F", "MANUFACTURER", "Lenovo"), false));
        return profiles;
    }

    private static Map<String, String> mapOf(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
