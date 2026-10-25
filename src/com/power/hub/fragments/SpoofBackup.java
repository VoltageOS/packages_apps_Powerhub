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
import android.os.UserHandle;
import android.provider.Settings;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

final class SpoofBackup {

    private static final List<String> KEYS = Arrays.asList(
            "spoof_pif_config",
            "spoof_pif_photos",
            "spoof_pif_presets",
            "spoof_gameprops_config",
            "spoof_appstate_config",
            "spoof_trickystore_target",
            "spoof_trickystore_keybox",
            "spoof_trickystore_patch",
            "spoof_trickystore_patch_mode",
            "game_spoofing_user_presets");

    private SpoofBackup() {
    }

    static String export(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONObject values = new JSONObject();
        for (String key : KEYS) {
            String value = read(context, key);
            if (value != null && !value.isEmpty()) {
                values.put(key, value);
            }
        }
        root.put("values", values);
        return root.toString(2);
    }

    static int restore(Context context, String content) throws Exception {
        JSONObject root = new JSONObject(content);
        JSONObject values = root.optJSONObject("values");
        if (values == null) {
            throw new IllegalArgumentException("Not a Power Hub spoof backup");
        }
        int count = 0;
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!KEYS.contains(key)) {
                continue;
            }
            String value = values.optString(key, "");
            write(context, key, value.isEmpty() ? null : value);
            count++;
        }
        return count;
    }

    private static String read(Context context, String key) {
        String value = Settings.Secure.getStringForUser(context.getContentResolver(), key,
                UserHandle.myUserId());
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return Settings.Secure.getStringForUser(context.getContentResolver(), key,
                UserHandle.USER_SYSTEM);
    }

    private static void write(Context context, String key, String value) {
        Settings.Secure.putStringForUser(context.getContentResolver(), key, value,
                UserHandle.myUserId());
        Settings.Secure.putStringForUser(context.getContentResolver(), key, value,
                UserHandle.USER_SYSTEM);
    }
}
