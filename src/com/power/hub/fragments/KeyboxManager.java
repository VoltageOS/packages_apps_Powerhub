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
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class KeyboxManager {

    private static final String TAG = "KeyboxManager";
    private static final String LEGACY_TRICKY_DIR = "/data/system/tricky_store";
    private static final String KEYBOX_FILE = "keybox.xml";
    private static final String TARGET_FILE = "target.txt";
    private static final String KEYBOX_KEY = "spoof_trickystore_keybox";
    private static final String TARGET_KEY = "spoof_trickystore_target";
    private static final String VENDING_PKG = "com.android.vending";

    private final Context mContext;

    public KeyboxManager(Context context) {
        mContext = context.getApplicationContext();
        migrateLegacyDataIfNeeded();
    }

    public boolean keyboxExists() {
        String stored = Settings.Secure.getString(mContext.getContentResolver(), KEYBOX_KEY);
        return stored != null && !stored.isEmpty();
    }

    public void importKeybox(Uri uri) throws Exception {
        try (InputStream inputStream = mContext.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IllegalStateException("Unable to open file");
            }
            byte[] bytes = inputStream.readAllBytes();
            Settings.Secure.putString(
                    mContext.getContentResolver(),
                    KEYBOX_KEY,
                    Base64.encodeToString(bytes, Base64.NO_WRAP));
        }
        killVending();
    }

    public void deleteKeybox() {
        Settings.Secure.putString(mContext.getContentResolver(), KEYBOX_KEY, null);
        killVending();
    }

    public int getTargetAppCount() {
        return readTargetLines().size();
    }

    public void importTargetFile(Uri uri) throws Exception {
        try (InputStream inputStream = mContext.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IllegalStateException("Unable to open file");
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Settings.Secure.putString(mContext.getContentResolver(), TARGET_KEY, content);
        }
        killVending();
    }

    public void saveTargetLines(List<String> lines) {
        try {
            Settings.Secure.putString(
                    mContext.getContentResolver(),
                    TARGET_KEY,
                    String.join("\n", lines));
            killVending();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save target lines", e);
        }
    }

    public List<String> readTargetLines() {
        List<String> result = new ArrayList<>();
        String content = Settings.Secure.getString(mContext.getContentResolver(), TARGET_KEY);
        if (content == null || content.isEmpty()) {
            return result;
        }

        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private void migrateLegacyDataIfNeeded() {
        if (!keyboxExists()) {
            File keyboxFile = new File(LEGACY_TRICKY_DIR, KEYBOX_FILE);
            if (keyboxFile.exists() && keyboxFile.canRead()) {
                try {
                    byte[] bytes = Files.readAllBytes(keyboxFile.toPath());
                    Settings.Secure.putString(
                            mContext.getContentResolver(),
                            KEYBOX_KEY,
                            Base64.encodeToString(bytes, Base64.NO_WRAP));
                    Log.i(TAG, "Migrated legacy keybox.xml");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to migrate legacy keybox", e);
                }
            }
        }

        String targetContent = Settings.Secure.getString(mContext.getContentResolver(), TARGET_KEY);
        if (targetContent == null || targetContent.isEmpty()) {
            File targetFile = new File(LEGACY_TRICKY_DIR, TARGET_FILE);
            if (targetFile.exists() && targetFile.canRead()) {
                try {
                    String content = new String(Files.readAllBytes(targetFile.toPath()),
                            StandardCharsets.UTF_8);
                    Settings.Secure.putString(
                            mContext.getContentResolver(),
                            TARGET_KEY,
                            content);
                    Log.i(TAG, "Migrated legacy target.txt");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to migrate legacy target list", e);
                }
            }
        }
    }

    private void killVending() {
        try {
            ActivityManager activityManager = (ActivityManager)
                    mContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                activityManager.forceStopPackage(VENDING_PKG);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to stop Play Store", e);
        }
    }
}
