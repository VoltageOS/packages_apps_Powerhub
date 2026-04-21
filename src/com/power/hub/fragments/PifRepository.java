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

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PifRepository {

    private static final String TAG = "PifRepository";
    private static final String GOOGLE_URL = "https://developer.android.com";
    private static final String FLASH_URL = "https://flash.android.com";
    private static final String FLASH_API = "https://content-flashstation-pa.googleapis.com/v1/builds";
    private static final String PIXEL_BULLETIN_URL =
            "https://source.android.com/docs/security/bulletin/pixel";
    private static final String VOLTAGE_PIF_URL =
            "https://github.com/VoltageOS/.github/raw/refs/heads/main/profile/pif.json";

    public abstract static class PifResult {
        private PifResult() {
        }

        public static final class Success extends PifResult {
            public final String model;
            public final JSONObject pifData;

            public Success(String model, JSONObject pifData) {
                this.model = model;
                this.pifData = pifData;
            }
        }

        public static final class Error extends PifResult {
            public final String message;
            public final Exception exception;

            public Error(String message) {
                this(message, null);
            }

            public Error(String message, Exception exception) {
                this.message = message != null ? message : "Unknown error";
                this.exception = exception;
            }
        }
    }

    public PifResult fetchBetaPif() {
        try {
            String versionsHtml = fetchUrl(GOOGLE_URL + "/about/versions");
            Integer latestVersion = extractLatestVersion(versionsHtml);
            if (latestVersion == null) {
                return new PifResult.Error("No Android version pages found");
            }

            String latestHtml = fetchUrl(GOOGLE_URL + "/about/versions/" + latestVersion);
            String qprPath = extractLatestQprPath(latestHtml, latestVersion);
            if (qprPath == null) {
                return new PifResult.Error("No QPR download page found");
            }

            String otaHtml = fetchUrl(GOOGLE_URL + qprPath);
            String[][] devices = extractBetaDevices(otaHtml);
            if (devices.length == 0) {
                return new PifResult.Error("No beta devices found");
            }

            String[] selected = devices[new Random().nextInt(devices.length)];
            String model = selected[0];
            String product = selected[1];
            String device = selected[2];

            Log.d(TAG, "Selected device: " + model + " (" + product + ")");

            String flashHtml = fetchUrl(FLASH_URL);
            String apiKey = extractRegex(flashHtml, "(AIza[0-9A-Za-z_-]{35})");
            if (apiKey == null || apiKey.isEmpty()) {
                return new PifResult.Error("Failed to extract Flash Tool API key");
            }

            String buildsJson = fetchFlashBuilds(product, apiKey);
            JSONObject root = new JSONObject(buildsJson);
            JSONArray buildsArray = root.optJSONArray("flashstationBuild");
            if (buildsArray == null) {
                return new PifResult.Error("No flashstationBuild array in Flash Tool response");
            }

            String releaseCandidate = null;
            String incremental = null;
            String androidVersion = "";
            String canaryId = null;

            for (int i = buildsArray.length() - 1; i >= 0; i--) {
                JSONObject build = buildsArray.optJSONObject(i);
                if (build == null) {
                    continue;
                }

                JSONObject preview = build.optJSONObject("previewMetadata");
                if (preview == null || !preview.optBoolean("canary")) {
                    continue;
                }

                String rc = build.optString("releaseCandidateName");
                String buildId = build.optString("buildId");
                if (rc.isEmpty() || buildId.isEmpty()) {
                    continue;
                }

                releaseCandidate = rc;
                incremental = buildId;
                androidVersion = preview.optString("releaseTrackVersionName");
                String previewId = preview.optString("id");
                if (previewId.contains("canary-")) {
                    canaryId = previewId;
                }
                break;
            }

            if (releaseCandidate == null || incremental == null) {
                return new PifResult.Error("No canary build found for " + product);
            }

            String fingerprint = "google/" + product + "/" + device + ":CANARY/"
                    + releaseCandidate + "/" + incremental + ":user/release-keys";
            Log.d(TAG, "Fingerprint: " + fingerprint + " (Android " + androidVersion + ")");

            String canaryMonth = extractCanaryMonth(canaryId);
            if (canaryMonth == null) {
                return new PifResult.Error("Failed to derive canary month id");
            }

            String securityPatch = resolveSecurityPatch(canaryMonth);
            Log.d(TAG, "Security Patch: " + securityPatch);

            JSONObject pifJson = new JSONObject();
            pifJson.put("MANUFACTURER", "Google");
            pifJson.put("MODEL", model);
            pifJson.put("PRODUCT", product);
            pifJson.put("DEVICE", device);
            pifJson.put("FINGERPRINT", fingerprint);
            pifJson.put("SECURITY_PATCH", securityPatch);
            pifJson.put("DEVICE_INITIAL_SDK_INT", "32");
            return new PifResult.Success(model, pifJson);
        } catch (Exception e) {
            Log.e(TAG, "Failed fetching canary PIF", e);
            return new PifResult.Error("Failed to fetch canary PIF: " + e.getMessage(), e);
        }
    }

    public PifResult fetchVoltagePif() {
        try {
            String response = fetchUrl(VOLTAGE_PIF_URL);
            JSONObject json = new JSONObject(response);
            sanitizeVoltagePif(json);
            String model = json.optString("MODEL", "Voltage profile");
            return new PifResult.Success(model, json);
        } catch (Exception e) {
            Log.e(TAG, "Failed fetching Voltage PIF", e);
            return new PifResult.Error("Failed to fetch from Voltage: " + e.getMessage(), e);
        }
    }

    private void sanitizeVoltagePif(JSONObject json) {
        try {
            json.put("spoofVendingSdk", "false");
            json.remove("SDK_INT");
        } catch (Exception e) {
            Log.w(TAG, "Failed to sanitize Voltage PIF", e);
        }
    }

    private Integer extractLatestVersion(String html) {
        Integer latest = null;
        Matcher matcher = Pattern.compile(
                "https://developer\\.android\\.com/about/versions/(\\d+)")
                .matcher(html);
        while (matcher.find()) {
            int version = Integer.parseInt(matcher.group(1));
            if (latest == null || version > latest) {
                latest = version;
            }
        }
        return latest;
    }

    private String extractLatestQprPath(String html, int version) {
        int highestQpr = -1;
        String latestPath = null;
        Matcher matcher = Pattern.compile(
                "href=\"(/about/versions/" + version + "/qpr(\\d+)/download-ota)\"")
                .matcher(html);
        while (matcher.find()) {
            int qpr = Integer.parseInt(matcher.group(2));
            if (qpr > highestQpr) {
                highestQpr = qpr;
                latestPath = matcher.group(1);
            }
        }
        return latestPath;
    }

    private String[][] extractBetaDevices(String html) {
        Pattern rowPattern = Pattern.compile(
                "<tr id=\"([^\"]+)\">\\s*<td[^>]*>([^<]+)</td>",
                Pattern.DOTALL);
        Matcher matcher = rowPattern.matcher(html);
        java.util.ArrayList<String[]> devices = new java.util.ArrayList<>();
        while (matcher.find()) {
            String device = matcher.group(1);
            String model = matcher.group(2).trim();
            devices.add(new String[] {model, device + "_beta", device});
        }
        return devices.toArray(new String[0][]);
    }

    private String fetchFlashBuilds(String product, String apiKey) throws Exception {
        String url = FLASH_API + "?product=" + product + "&key=" + apiKey;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36");
        connection.setRequestProperty("Referer", FLASH_URL);
        connection.setRequestProperty("X-Goog-Api-Key", apiKey);
        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractCanaryMonth(String canaryId) {
        if (canaryId == null || canaryId.isEmpty()) {
            return null;
        }
        Matcher matcher = Pattern.compile(
                "canary-(\\d{4})(\\d{2})",
                Pattern.CASE_INSENSITIVE).matcher(canaryId);
        if (!matcher.find()) {
            return null;
        }
        return String.format(Locale.US, "%s-%s", matcher.group(1), matcher.group(2));
    }

    private String resolveSecurityPatch(String canaryMonth) {
        try {
            String bulletinHtml = fetchUrl(PIXEL_BULLETIN_URL);
            Matcher matcher = Pattern.compile("<td>(" + Pattern.quote(canaryMonth) + "-\\d{2})</td>")
                    .matcher(bulletinHtml);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.d(TAG, "Bulletin fetch failed, using estimated patch", e);
        }
        return canaryMonth + "-05";
    }

    private String fetchUrl(String urlString) throws Exception {
        URLConnection connection = new URL(urlString).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36");
        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractRegex(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
