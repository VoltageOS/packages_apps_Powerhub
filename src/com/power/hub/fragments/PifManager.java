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

import android.app.ActivityManager;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PifManager {

    private static final String TAG = "PifManager";
    private static final String LEGACY_PIF_DIR = "/data/system/playintegrityfix";
    private static final String PIF_CONFIG_KEY = "spoof_pif_config";
    private static final String PIF_CONFIG_NAME = "pif.json";
    private static final String VENDING_PKG = "com.android.vending";
    private static final String PHOTOS_PKG = "com.google.android.apps.photos";
    private static final String PHOTOS_SPOOF_KEY = "spoofPhotos";
    private static final String PROPS_SPOOF_KEY = "spoofProps";
    private static final String PROVIDER_SPOOF_KEY = "spoofProvider";
    private static final String SIGNATURE_SPOOF_KEY = "spoofSignature";
    private static final String VENDING_BUILD_SPOOF_KEY = "spoofVendingBuild";

    private static final List<String> LEGACY_CONFIG_FILES = Arrays.asList(
            "custom.pif.prop",
            "custom.pif.json",
            "pif.prop",
            "pif.json");

    private final Context mContext;

    public PifManager(Context context) {
        mContext = context.getApplicationContext();
        migrateLegacyConfigIfNeeded();
    }

    public String getActiveConfigName() {
        return hasStoredConfig() ? PIF_CONFIG_NAME : "";
    }

    public String getCurrentModel() {
        return getCurrentProperties().getOrDefault("MODEL", "");
    }

    public List<ConfigState> getConfigStates() {
        Map<String, String> data = getCurrentProperties();
        boolean exists = !data.isEmpty();
        List<ConfigState> states = new ArrayList<>(1);
        states.add(new ConfigState(PIF_CONFIG_NAME, exists, exists, data));
        return states;
    }

    public Map<String, String> getCurrentProperties() {
        String content = Settings.Secure.getString(
                mContext.getContentResolver(), PIF_CONFIG_KEY);
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        return parseStoredConfig(content);
    }

    public void applyPif(JSONObject pifData) throws Exception {
        writeAutoSelectedJsonConfig(pifData);
    }

    public String importPifConfig(String sourceName, String content) throws Exception {
        return importPifConfig(null, sourceName, content);
    }

    public String importPifConfig(String targetFileName, String sourceName, String content)
            throws Exception {
        Map<String, String> props = parseConfigContent(sourceName, content);
        if (props.isEmpty()) {
            throw new IllegalArgumentException("Config contains no readable properties");
        }

        writeStoredConfig(props);
        killPackage(VENDING_PKG);
        Log.i(TAG, "Imported PIF config into Settings.Secure");
        return PIF_CONFIG_NAME;
    }

    public void writeJsonConfig(String fileName, JSONObject pifData) throws Exception {
        writeStoredConfig(jsonToMap(pifData));
        killPackage(VENDING_PKG);
    }

    public String writeAutoSelectedJsonConfig(JSONObject pifData) throws Exception {
        writeStoredConfig(jsonToMap(pifData));
        killPackage(VENDING_PKG);
        Log.i(TAG, "Applied generated PIF config into Settings.Secure");
        return PIF_CONFIG_NAME;
    }

    public void deleteConfig(String fileName) {
        if (!PIF_CONFIG_NAME.equals(fileName)) {
            return;
        }
        Settings.Secure.putString(mContext.getContentResolver(), PIF_CONFIG_KEY, null);
        killPackage(VENDING_PKG);
    }

    public boolean isSpoofPhotosEnabled() {
        return isTruthy(getCurrentProperties().get(PHOTOS_SPOOF_KEY));
    }

    public void setSpoofPhotos(boolean enabled) {
        updateToggle(PHOTOS_SPOOF_KEY, enabled, PHOTOS_PKG);
    }

    public boolean isSpoofPropsEnabled() {
        String val = getCurrentProperties().get(PROPS_SPOOF_KEY);
        return val != null ? isTruthy(val) : true;
    }

    public void setSpoofProps(boolean enabled) {
        updateToggle(PROPS_SPOOF_KEY, enabled, null);
    }

    public boolean isSpoofProviderEnabled() {
        String val = getCurrentProperties().get(PROVIDER_SPOOF_KEY);
        return val != null ? isTruthy(val) : true;
    }

    public void setSpoofProvider(boolean enabled) {
        updateToggle(PROVIDER_SPOOF_KEY, enabled, VENDING_PKG);
    }

    public boolean isSpoofSignatureEnabled() {
        String val = getCurrentProperties().get(SIGNATURE_SPOOF_KEY);
        return val != null ? isTruthy(val) : false;
    }

    public void setSpoofSignature(boolean enabled) {
        updateToggle(SIGNATURE_SPOOF_KEY, enabled, VENDING_PKG);
    }

    public boolean isSpoofVendingBuildEnabled() {
        String val = getCurrentProperties().get(VENDING_BUILD_SPOOF_KEY);
        return val != null ? isTruthy(val) : true;
    }

    public void setSpoofVendingBuild(boolean enabled) {
        updateToggle(VENDING_BUILD_SPOOF_KEY, enabled, VENDING_PKG);
    }

    private void updateToggle(String key, boolean enabled, String packageToKill) {
        Map<String, String> props = new LinkedHashMap<>(getCurrentProperties());
        props.put(key, String.valueOf(enabled));
        writeStoredConfig(props);
        if (packageToKill != null && !packageToKill.isEmpty()) {
            killPackage(packageToKill);
        }
    }

    public static boolean looksLikeJson(String fileName, String content) {
        if (fileName != null) {
            String lowerName = fileName.toLowerCase();
            if (lowerName.endsWith(".json")) {
                return true;
            }
            if (lowerName.endsWith(".prop")) {
                return false;
            }
        }
        String trimmed = content != null ? content.trim() : "";
        return trimmed.startsWith("{");
    }

    public static Map<String, String> parseConfigContent(String fileName, String content)
            throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        if (looksLikeJson(fileName, content)) {
            JSONObject json = new JSONObject(content);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = json.opt(key);
                result.put(key, value == null ? "" : String.valueOf(value));
            }
            return result;
        }

        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }

            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }

            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            int commentIndex = value.indexOf('#');
            if (commentIndex >= 0) {
                value = value.substring(0, commentIndex).trim();
            }

            result.put(key, stripWrappingQuotes(value));
        }
        return result;
    }

    private Map<String, String> jsonToMap(JSONObject pifData) {
        Map<String, String> props = new LinkedHashMap<>();
        Iterator<String> keys = pifData.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = pifData.opt(key);
            props.put(key, value == null ? "" : String.valueOf(value));
        }
        return props;
    }

    private Map<String, String> parseStoredConfig(String content) {
        try {
            String trimmed = content != null ? content.trim() : "";
            if (trimmed.isEmpty()) {
                return Collections.emptyMap();
            }
            return parseConfigContent(trimmed.startsWith("{") ? PIF_CONFIG_NAME : null, trimmed);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse stored PIF config", e);
            return Collections.emptyMap();
        }
    }

    private void writeStoredConfig(Map<String, String> props) {
        try {
            Settings.Secure.putString(
                    mContext.getContentResolver(),
                    PIF_CONFIG_KEY,
                    serializeJsonConfig(props));
        } catch (Exception e) {
            Log.e(TAG, "Failed to store PIF config", e);
        }
    }

    private boolean hasStoredConfig() {
        String content = Settings.Secure.getString(
                mContext.getContentResolver(), PIF_CONFIG_KEY);
        return content != null && !content.trim().isEmpty();
    }

    private void migrateLegacyConfigIfNeeded() {
        if (hasStoredConfig()) {
            return;
        }

        File legacyFile = findLegacyActiveFile();
        if (legacyFile == null) {
            return;
        }

        try {
            Map<String, String> props = parseConfigContent(
                    legacyFile.getName(), readFileToString(legacyFile));
            if (!props.isEmpty()) {
                writeStoredConfig(props);
                Log.i(TAG, "Migrated legacy PIF config from " + legacyFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate legacy PIF config", e);
        }
    }

    private File findLegacyActiveFile() {
        File dir = new File(LEGACY_PIF_DIR);
        for (String name : LEGACY_CONFIG_FILES) {
            File file = new File(dir, name);
            if (file.exists() && file.canRead()) {
                return file;
            }
        }
        return null;
    }

    private String readFileToString(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private String serializeJsonConfig(Map<String, String> props) throws Exception {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json.toString(2);
    }

    private static String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean isTruthy(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private void killPackage(String packageName) {
        try {
            ActivityManager activityManager = (ActivityManager)
                    mContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                activityManager.forceStopPackage(packageName);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to stop package: " + packageName, e);
        }
    }

    public static final class ConfigState {
        public final String fileName;
        public final boolean exists;
        public final boolean isActive;
        public final Map<String, String> data;

        ConfigState(String fileName, boolean exists, boolean isActive, Map<String, String> data) {
            this.fileName = fileName;
            this.exists = exists;
            this.isActive = isActive;
            this.data = data;
        }
    }
}
