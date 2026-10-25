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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36";

    public static final String SOURCE_CANARY = "canary";
    public static final String SOURCE_RETAIL = "retail";
    public static final String SOURCE_VOLTAGE = "voltage";

    private static final Map<String, String> DEVICE_MODEL_MAP = new LinkedHashMap<>();
    static {
        DEVICE_MODEL_MAP.put("oriole", "Pixel 6");
        DEVICE_MODEL_MAP.put("raven", "Pixel 6 Pro");
        DEVICE_MODEL_MAP.put("bluejay", "Pixel 6a");
        DEVICE_MODEL_MAP.put("panther", "Pixel 7");
        DEVICE_MODEL_MAP.put("cheetah", "Pixel 7 Pro");
        DEVICE_MODEL_MAP.put("lynx", "Pixel 7a");
        DEVICE_MODEL_MAP.put("shiba", "Pixel 8");
        DEVICE_MODEL_MAP.put("tangorpro", "Pixel Tablet");
        DEVICE_MODEL_MAP.put("felix", "Pixel Fold");
        DEVICE_MODEL_MAP.put("husky", "Pixel 8 Pro");
        DEVICE_MODEL_MAP.put("akita", "Pixel 8a");
        DEVICE_MODEL_MAP.put("tokay", "Pixel 9");
        DEVICE_MODEL_MAP.put("caiman", "Pixel 9 Pro");
        DEVICE_MODEL_MAP.put("komodo", "Pixel 9 Pro XL");
        DEVICE_MODEL_MAP.put("comet", "Pixel 9 Pro Fold");
        DEVICE_MODEL_MAP.put("tegu", "Pixel 9a");
        DEVICE_MODEL_MAP.put("frankel", "Pixel 10");
        DEVICE_MODEL_MAP.put("blazer", "Pixel 10 Pro");
        DEVICE_MODEL_MAP.put("mustang", "Pixel 10 Pro XL");
        DEVICE_MODEL_MAP.put("rango", "Pixel 10 Pro Fold");
        DEVICE_MODEL_MAP.put("stallion", "Pixel 10a");
        DEVICE_MODEL_MAP.put("yogi", "Pixel 11 Pro Fold");
        DEVICE_MODEL_MAP.put("kodiak", "Pixel 11 Pro XL");
        DEVICE_MODEL_MAP.put("grizzly", "Pixel 11 Pro");
        DEVICE_MODEL_MAP.put("cubs", "Pixel 11");
        DEVICE_MODEL_MAP.put("formosan", "Pixel 11a");
    }

    public static final class PifDevice {
        public final String product;
        public final String device;
        public final String model;
        public final String otaUrl;

        public PifDevice(String product, String device, String model, String otaUrl) {
            this.product = product;
            this.device = device;
            this.model = model;
            this.otaUrl = otaUrl;
        }
    }

    public static final class DeviceList {
        public final List<PifDevice> devices;
        public final String apiKey;

        DeviceList(List<PifDevice> devices, String apiKey) {
            this.devices = devices;
            this.apiKey = apiKey;
        }

        public boolean isEmpty() {
            return devices.isEmpty();
        }
    }

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

    public DeviceList fetchDevices(String source) {
        if (SOURCE_CANARY.equals(source)) {
            return fetchCanaryDevices();
        }
        if (SOURCE_RETAIL.equals(source)) {
            return fetchRetailDevices();
        }
        return new DeviceList(Collections.emptyList(), null);
    }

    public PifResult buildProfile(String source, PifDevice device, String apiKey) {
        if (SOURCE_CANARY.equals(source)) {
            return buildCanaryPif(device, apiKey == null ? "" : apiKey);
        }
        if (SOURCE_RETAIL.equals(source)) {
            return buildRetailPif(device);
        }
        return new PifResult.Error("Unknown profile source");
    }

    private DeviceList fetchRetailDevices() {
        for (int version : candidateVersions()) {
            try {
                String html = fetchUrl(GOOGLE_URL + "/about/versions/" + version + "/download-ota");
                List<PifDevice> devices = new ArrayList<>();
                Set<String> seen = new LinkedHashSet<>();
                Matcher matcher = Pattern.compile(
                        "href=\"(https://dl\\.google\\.com/[^\"]*ota/([^/\"]+_beta)[^\"]*?)\"")
                        .matcher(html);
                while (matcher.find()) {
                    String otaUrl = matcher.group(1);
                    String product = matcher.group(2);
                    String device = product.replace("_beta", "");
                    if (!seen.add(device)) {
                        continue;
                    }
                    devices.add(new PifDevice(product, device, modelFor(device), otaUrl));
                }
                if (!devices.isEmpty()) {
                    return new DeviceList(devices, null);
                }
            } catch (Exception e) {
                Log.d(TAG, "Retail page for Android " + version + " failed", e);
            }
        }
        return new DeviceList(Collections.emptyList(), null);
    }

    private PifResult buildRetailPif(PifDevice pifDevice) {
        try {
            String partial = fetchPartialUrl(pifDevice.otaUrl, 4096);
            String fingerprint = extractField(partial, "post-build");
            if (fingerprint == null) {
                return new PifResult.Error("Could not extract fingerprint");
            }
            String securityPatch = extractField(partial, "security-patch-level");
            if (securityPatch == null) {
                return new PifResult.Error("Could not extract security patch");
            }

            String[] parts = fingerprint.split("/");
            String product = parts.length > 1 ? parts[1] : pifDevice.device;
            String release = parts.length > 2 ? substringAfter(parts[2], ":") : "";
            String buildId = parts.length > 3 ? parts[3] : "";

            JSONObject pifJson = new JSONObject();
            pifJson.put("MANUFACTURER", "Google");
            pifJson.put("MODEL", pifDevice.model);
            pifJson.put("PRODUCT", product);
            pifJson.put("DEVICE", pifDevice.device);
            pifJson.put("FINGERPRINT", fingerprint);
            pifJson.put("SECURITY_PATCH", securityPatch);
            pifJson.put("DEVICE_INITIAL_SDK_INT", initialSdkIntFor(pifDevice.device));
            pifJson.put("TYPE", "user");
            pifJson.put("TAGS", "release-keys");
            pifJson.put("ID", buildId);
            pifJson.put("BRAND", "google");
            pifJson.put("RELEASE", release);
            Log.d(TAG, "Fingerprint: " + fingerprint);
            return new PifResult.Success(pifDevice.model, pifJson);
        } catch (Exception e) {
            Log.e(TAG, "Failed fetching retail PIF", e);
            return new PifResult.Error("Failed to fetch retail PIF: " + e.getMessage(), e);
        }
    }

    private DeviceList fetchCanaryDevices() {
        for (int version : candidateVersions()) {
            try {
                String latestHtml = fetchUrl(GOOGLE_URL + "/about/versions/" + version);
                Matcher matcher = Pattern.compile(
                        "href=\"(/about/versions/" + version
                                + "(?:/qpr(\\d+))?/download(?:-ota)?)\"")
                        .matcher(latestHtml);
                Map<String, Integer> pages = new LinkedHashMap<>();
                int newestQpr = -1;
                while (matcher.find()) {
                    String path = matcher.group(1);
                    int qpr = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                    if (!pages.containsKey(path)) {
                        pages.put(path, qpr);
                        if (qpr > newestQpr) {
                            newestQpr = qpr;
                        }
                    }
                }
                if (pages.isEmpty()) {
                    continue;
                }

                List<PifDevice> best = null;
                for (Map.Entry<String, Integer> page : pages.entrySet()) {
                    if (page.getValue() != newestQpr) {
                        continue;
                    }
                    try {
                        List<PifDevice> devices =
                                parseDeviceTable(fetchUrl(GOOGLE_URL + page.getKey()));
                        if (best == null || devices.size() > best.size()) {
                            best = devices;
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Failed to read " + page.getKey(), e);
                    }
                }
                if (best == null || best.isEmpty()) {
                    continue;
                }

                String apiKey = extractFlashApiKey(fetchUrl(FLASH_URL));
                return new DeviceList(best, apiKey);
            } catch (Exception e) {
                Log.d(TAG, "Canary page for Android " + version + " failed", e);
            }
        }
        return new DeviceList(Collections.emptyList(), null);
    }

    private PifResult buildCanaryPif(PifDevice pifDevice, String apiKey) {
        try {
            if (apiKey.isEmpty()) {
                return new PifResult.Error("Flash Tool API key unavailable");
            }

            String buildsJson = fetchFlashBuilds(pifDevice.product, apiKey);
            JSONObject root = new JSONObject(buildsJson);
            JSONArray buildsArray = root.optJSONArray("flashstationBuild");
            if (buildsArray == null) {
                return new PifResult.Error("No flashstationBuild array in Flash Tool response");
            }

            String releaseCandidate = null;
            String incremental = null;
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
                String previewId = preview.optString("id");
                if (previewId.contains("canary-")) {
                    canaryId = previewId;
                }
                break;
            }

            if (releaseCandidate == null || incremental == null) {
                return new PifResult.Error("No canary build found for " + pifDevice.product);
            }

            String fingerprint = "google/" + pifDevice.product + "/" + pifDevice.device
                    + ":CANARY/" + releaseCandidate + "/" + incremental + ":user/release-keys";
            Log.d(TAG, "Fingerprint: " + fingerprint);

            String canaryMonth = extractCanaryMonth(canaryId);
            if (canaryMonth == null) {
                return new PifResult.Error("Failed to derive canary month id");
            }
            String securityPatch = resolveSecurityPatch(canaryMonth);
            Log.d(TAG, "Security Patch: " + securityPatch);

            JSONObject pifJson = new JSONObject();
            pifJson.put("MANUFACTURER", "Google");
            pifJson.put("MODEL", pifDevice.model);
            pifJson.put("PRODUCT", pifDevice.product);
            pifJson.put("DEVICE", pifDevice.device);
            pifJson.put("FINGERPRINT", fingerprint);
            pifJson.put("SECURITY_PATCH", securityPatch);
            pifJson.put("DEVICE_INITIAL_SDK_INT", initialSdkIntFor(pifDevice.device));
            pifJson.put("TYPE", "user");
            pifJson.put("TAGS", "release-keys");
            pifJson.put("ID", releaseCandidate);
            pifJson.put("BRAND", "google");
            pifJson.put("RELEASE", "CANARY");
            return new PifResult.Success(pifDevice.model, pifJson);
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

    private List<Integer> candidateVersions() {
        Set<Integer> known = new LinkedHashSet<>();
        try {
            String html = fetchUrl(GOOGLE_URL + "/about/versions");
            Matcher matcher = Pattern.compile(
                    "(?:https://developer\\.android\\.com)?/about/versions/(\\d+)")
                    .matcher(html);
            while (matcher.find()) {
                known.add(Integer.parseInt(matcher.group(1)));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to list Android versions", e);
        }
        if (known.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> sorted = new ArrayList<>(known);
        sorted.sort(Collections.reverseOrder());
        List<Integer> versions = new ArrayList<>();
        versions.add(sorted.get(0) + 1);
        versions.addAll(sorted);
        return versions;
    }

    private List<PifDevice> parseDeviceTable(String html) {
        List<PifDevice> devices = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("<tr id=\"([^\"]+)\">\\s*<td[^>]*>([^<]+)</td>",
                Pattern.DOTALL).matcher(html);
        while (matcher.find()) {
            String device = matcher.group(1);
            if (!seen.add(device)) {
                continue;
            }
            String model = matcher.group(2).trim();
            if (model.isEmpty()) {
                model = modelFor(device);
            }
            devices.add(new PifDevice(device + "_beta", device, model, ""));
        }
        return devices;
    }

    private String extractFlashApiKey(String html) {
        Matcher config = Pattern.compile("data-client-config=\"([^\"]*)\"").matcher(html);
        if (config.find()) {
            String key = extractRegex(config.group(1), "(AIza[0-9A-Za-z_-]{35})");
            if (key != null) {
                return key;
            }
        }
        return extractRegex(html, "(AIza[0-9A-Za-z_-]{35})");
    }

    private String fetchFlashBuilds(String product, String apiKey) throws Exception {
        String url = FLASH_API + "?product=" + product + "&key=" + apiKey;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", FLASH_URL);
        connection.setRequestProperty("X-Goog-Api-Key", apiKey);
        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String fetchPartialUrl(String urlString, int maxBytes) throws Exception {
        URLConnection connection = new URL(urlString).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try (InputStream inputStream = connection.getInputStream()) {
            byte[] buffer = new byte[512];
            StringBuilder builder = new StringBuilder();
            int total = 0;
            int read;
            while (total < maxBytes && (read = inputStream.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, read, StandardCharsets.ISO_8859_1));
                total += read;
            }
            return builder.toString();
        }
    }

    private String modelFor(String device) {
        String model = DEVICE_MODEL_MAP.get(device);
        return model != null ? model : device;
    }

    private String initialSdkIntFor(String device) {
        switch (device) {
            case "oriole":
            case "raven":
                return "31";
            case "bluejay":
                return "32";
            case "panther":
            case "cheetah":
            case "lynx":
            case "felix":
            case "tangorpro":
                return "33";
            case "shiba":
            case "husky":
            case "akita":
            case "tokay":
            case "caiman":
            case "komodo":
            case "comet":
                return "34";
            case "tegu":
            case "stallion":
                return "35";
            case "frankel":
            case "blazer":
            case "mustang":
            case "rango":
                return "36";
            case "cubs":
            case "grizzly":
            case "kodiak":
            case "yogi":
                return "37";
            default:
                return "32";
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

    private String extractField(String text, String key) {
        Matcher matcher = Pattern.compile(Pattern.quote(key) + "=(.*)").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String substringAfter(String value, String separator) {
        int index = value.indexOf(separator);
        return index >= 0 ? value.substring(index + separator.length()) : "";
    }

    private String fetchUrl(String urlString) throws Exception {
        URLConnection connection = new URL(urlString).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractRegex(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}