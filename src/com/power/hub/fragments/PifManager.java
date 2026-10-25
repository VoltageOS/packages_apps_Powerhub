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
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.settings.R;

import org.json.JSONArray;
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
import java.util.regex.Pattern;

public class PifManager {

    private static final String TAG = "PifManager";
    private static final String LEGACY_PIF_DIR = "/data/system/playintegrityfix";
    private static final String PIF_CONFIG_KEY = "spoof_pif_config";
    private static final String PIF_CONFIG_NAME = "pif.json";
    private static final String VENDING_PKG = "com.android.vending";
    private static final String GMS_PKG = "com.google.android.gms";
    private static final String PHOTOS_PKG = "com.google.android.apps.photos";
    private static final String PHOTOS_SPOOF_SECURE_KEY = "spoof_pif_photos";
    private static final String PRESETS_KEY = "spoof_pif_presets";

    private static final List<String> FLAG_KEYS = Arrays.asList(
            "spoofBuild", "spoofProps", "spoofProvider", "spoofSignature",
            "spoofVendingBuild", "spoofVendingSdk",
            "DEBUG", "verboseLogs");

    private static final Map<String, String> DEFAULT_FLAGS = new LinkedHashMap<>();
    static {
        DEFAULT_FLAGS.put("spoofBuild", "true");
        DEFAULT_FLAGS.put("spoofProps", "true");
        DEFAULT_FLAGS.put("spoofProvider", "false");
        DEFAULT_FLAGS.put("spoofSignature", "false");
        DEFAULT_FLAGS.put("spoofVendingBuild", "true");
        DEFAULT_FLAGS.put("spoofVendingSdk", "false");
        DEFAULT_FLAGS.put("DEBUG", "false");
    }

    private static final List<String> LEGACY_CONFIG_FILES = Arrays.asList(
            "custom.pif.prop",
            "custom.pif.json",
            "pif.prop",
            "pif.json");

    private static final List<String> INT_KEYS = Arrays.asList("DEVICE_INITIAL_SDK_INT", "SDK_INT");
    private static final List<String> FINGERPRINT_KEYS = Arrays.asList(
            "BRAND", "PRODUCT", "DEVICE", "RELEASE", "ID", "INCREMENTAL", "TYPE", "TAGS");
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile(
            "[^/:\\s]+/[^/:\\s]+/[^/:\\s]+:[^/:\\s]+/[^/:\\s]+/[^/:\\s]+:[^/:\\s]+/[^/:\\s]+");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

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

        String fingerprint = props.get("FINGERPRINT");
        if (fingerprint == null || fingerprint.trim().isEmpty()) {
            throw new IllegalArgumentException("Config is missing FINGERPRINT");
        }

        writeStoredConfig(props);
        Log.i(TAG, "Imported PIF config into Settings.Secure");
        return PIF_CONFIG_NAME;
    }

    public void writeJsonConfig(String fileName, JSONObject pifData) throws Exception {
        writeStoredConfig(jsonToMap(pifData));
    }

    public String writeAutoSelectedJsonConfig(JSONObject pifData) throws Exception {
        Map<String, String> existing = getCurrentProperties();
        for (String key : FLAG_KEYS) {
            if (!pifData.has(key) && existing.containsKey(key)) {
                pifData.put(key, existing.get(key));
            }
        }
        for (Map.Entry<String, String> entry : DEFAULT_FLAGS.entrySet()) {
            if (!pifData.has(entry.getKey())) {
                pifData.put(entry.getKey(), entry.getValue());
            }
        }
        writeStoredConfig(jsonToMap(pifData));
        Log.i(TAG, "Applied generated PIF config into Settings.Secure");
        return PIF_CONFIG_NAME;
    }

    public void deleteConfig(String fileName) {
        if (!PIF_CONFIG_NAME.equals(fileName)) {
            return;
        }
        Settings.Secure.putString(mContext.getContentResolver(), PIF_CONFIG_KEY, null);
    }

    public void saveProperties(Map<String, String> properties) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (key.isEmpty()) {
                continue;
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException(mContext.getString(R.string.pif_value_empty));
            }
            if (INT_KEYS.contains(key) && !isInteger(value)) {
                throw new IllegalArgumentException(
                        mContext.getString(R.string.pif_value_not_integer));
            }
            if ("SECURITY_PATCH".equals(key) && !DATE_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException(
                        mContext.getString(R.string.pif_invalid_patch_date));
            }
            if ("FINGERPRINT".equals(key) && !isValidFingerprint(value)) {
                throw new IllegalArgumentException(
                        mContext.getString(R.string.pif_invalid_fingerprint));
            }
            normalized.put(key, value);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(mContext.getString(R.string.pif_value_empty));
        }
        String fingerprint = normalized.get("FINGERPRINT");
        if (fingerprint != null) {
            normalized.putAll(fingerprintFields(fingerprint));
        }
        writeStoredConfig(normalized);
        Log.i(TAG, "Saved PIF properties");
    }

    public static boolean isValidFingerprint(String value) {
        return value != null && FINGERPRINT_PATTERN.matcher(value.trim()).matches();
    }

    public static Map<String, String> fingerprintFields(String fingerprint) {
        String[] parts = fingerprint.trim().split("[/:]");
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < FINGERPRINT_KEYS.size() && i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                fields.put(FINGERPRINT_KEYS.get(i), parts[i]);
            }
        }
        return fields;
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public List<String> getConfigWarnings() {
        Map<String, String> props = getCurrentProperties();
        List<String> warnings = new ArrayList<>();
        if (props.isEmpty()) {
            return warnings;
        }
        checkMissing(props, warnings, "FINGERPRINT");
        checkMissing(props, warnings, "MODEL");
        checkMissing(props, warnings, "SECURITY_PATCH");
        return warnings;
    }

    private static void checkMissing(Map<String, String> props, List<String> warnings, String key) {
        String value = props.get(key);
        if (value == null || value.trim().isEmpty()) {
            warnings.add(key);
        }
    }

    private String getSecureKey(String key) {
        return Settings.Secure.getString(mContext.getContentResolver(), key);
    }

    private void putSecureKey(String key, boolean enabled) {
        Settings.Secure.putString(mContext.getContentResolver(), key,
                String.valueOf(enabled));
    }

    public boolean isSpoofPhotosEnabled() {
        String val = getSecureKey(PHOTOS_SPOOF_SECURE_KEY);
        return val == null || isTruthy(val);
    }

    public void setSpoofPhotos(boolean enabled) {
        putSecureKey(PHOTOS_SPOOF_SECURE_KEY, enabled);
    }

    private boolean getConfigFlag(String key, boolean defaultValue) {
        String value = getCurrentProperties().get(key);
        return value == null ? defaultValue : isTruthy(value);
    }

    private void setConfigFlag(String key, boolean enabled) {
        setConfigValue(key, String.valueOf(enabled));
    }

    private void setConfigValue(String key, String value) {
        String content = Settings.Secure.getString(
                mContext.getContentResolver(), PIF_CONFIG_KEY);
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        try {
            Map<String, String> props = parseStoredConfig(content);
            if (props.isEmpty()) {
                return;
            }
            props.put(key, value);
            Settings.Secure.putString(mContext.getContentResolver(), PIF_CONFIG_KEY,
                    serializeJsonConfig(props));
        } catch (Exception e) {
            Log.e(TAG, "Failed to update PIF flag " + key, e);
        }
    }

    public boolean isSpoofBuildEnabled() {
        return getConfigFlag("spoofBuild", true);
    }

    public void setSpoofBuild(boolean enabled) {
        setConfigFlag("spoofBuild", enabled);
    }

    public boolean isDebugEnabled() {
        return getConfigFlag("DEBUG", false);
    }

    public void setDebugEnabled(boolean enabled) {
        setConfigFlag("DEBUG", enabled);
    }

    public int getVerboseLogsLevel() {
        String value = getCurrentProperties().get("verboseLogs");
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setVerboseLogsLevel(int level) {
        setConfigValue("verboseLogs", String.valueOf(level));
    }

    public boolean isSpoofPropsEnabled() {
        return getConfigFlag("spoofProps", true);
    }

    public void setSpoofProps(boolean enabled) {
        setConfigFlag("spoofProps", enabled);
    }

    public boolean isSpoofProviderEnabled() {
        return getConfigFlag("spoofProvider", false);
    }

    public void setSpoofProvider(boolean enabled) {
        setConfigFlag("spoofProvider", enabled);
    }

    public boolean isSpoofSignatureEnabled() {
        return getConfigFlag("spoofSignature", false);
    }

    public void setSpoofSignature(boolean enabled) {
        setConfigFlag("spoofSignature", enabled);
    }

    public boolean isSpoofVendingBuildEnabled() {
        return getConfigFlag("spoofVendingBuild", true);
    }

    public void setSpoofVendingBuild(boolean enabled) {
        setConfigFlag("spoofVendingBuild", enabled);
    }

    public void restartPifTargets() {
        stopPackage(VENDING_PKG);
        stopPackage(GMS_PKG);
    }

    public List<Preset> getPresets() {
        List<Preset> presets = new ArrayList<>();
        String raw = Settings.Secure.getString(mContext.getContentResolver(), PRESETS_KEY);
        if (raw == null || raw.isEmpty()) {
            return presets;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                JSONObject propsJson = obj == null ? null : obj.optJSONObject("props");
                String name = obj == null ? "" : obj.optString("name");
                if (propsJson == null || name.isEmpty()) {
                    continue;
                }
                presets.add(new Preset(name, jsonToMap(propsJson)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load PIF presets", e);
        }
        return presets;
    }

    public void savePreset(String name, Map<String, String> props) {
        List<Preset> presets = getPresets();
        presets.removeIf(preset -> preset.name.equals(name));
        presets.add(new Preset(name, new LinkedHashMap<>(props)));
        writePresets(presets);
    }

    public void deletePreset(String name) {
        List<Preset> presets = getPresets();
        presets.removeIf(preset -> preset.name.equals(name));
        writePresets(presets);
    }

    private void writePresets(List<Preset> presets) {
        try {
            JSONArray array = new JSONArray();
            for (Preset preset : presets) {
                JSONObject obj = new JSONObject();
                JSONObject propsJson = new JSONObject();
                for (Map.Entry<String, String> entry : preset.props.entrySet()) {
                    propsJson.put(entry.getKey(), entry.getValue());
                }
                obj.put("name", preset.name);
                obj.put("props", propsJson);
                array.put(obj);
            }
            Settings.Secure.putString(mContext.getContentResolver(), PRESETS_KEY,
                    array.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save PIF presets", e);
        }
    }

    public boolean applyPreset(String name) {
        for (Preset preset : getPresets()) {
            if (preset.name.equals(name)) {
                writeStoredConfig(preset.props);
                return true;
            }
        }
        return false;
    }

    public static final class Preset {
        public final String name;
        public final Map<String, String> props;

        Preset(String name, Map<String, String> props) {
            this.name = name;
            this.props = props;
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

    private void stopPackage(String packageName) {
        try {
            ActivityManager activityManager = (ActivityManager)
                    mContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                activityManager.forceStopPackageAsUser(packageName, UserHandle.USER_CURRENT);
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
