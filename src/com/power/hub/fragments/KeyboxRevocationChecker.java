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
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Checks a keybox against Google's revocation status list. */
public class KeyboxRevocationChecker {

    private static final String TAG = "KeyboxRevocation";
    private static final String PREFS = "powerhub_spoofing_keybox";
    private static final String PREF_STATE = "revocation_state";
    private static final String PREF_ROOTS = "roots";
    private static final String STATUS_URL =
            "https://android.googleapis.com/attestation/status?encrypted=0";
    private static final String ROOTS_URL = "https://android.googleapis.com/attestation/root";
    private static final long REVOKED_CACHE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final long ROOTS_CACHE_MS = 30L * 24 * 60 * 60 * 1000;
    private static final int EXPIRY_WARN_DAYS = 14;

    private static final Set<String> FALLBACK_ROOTS = new HashSet<>();
    static {
        FALLBACK_ROOTS.add("1EF1A04B8BA58AB94589AC498C8982A783F24EA7307E0159A0C3A73B377D87CC");
        FALLBACK_ROOTS.add("CEDB1CB6DC896AE5EC797348BCE9286753C2B38EE71CE0FBE34A9A1248800DFC");
        FALLBACK_ROOTS.add("6D9DB4CE6C5C0B293166D08986E05774A8776CEB525D9E4329520DE12BA4BCC0");
    }

    public enum Status {
        UNKNOWN, VALID, REVOKED, SUSPENDED, EXPIRING_SOON, EXPIRED, CHAIN_INVALID,
        UNTRUSTED_ROOT, UNVERIFIED
    }

    public static final class Result {
        public final Status status;
        public final String reason;
        public final long checkedAt;
        public final boolean offline;

        Result(Status status, String reason, long checkedAt, boolean offline) {
            this.status = status;
            this.reason = reason;
            this.checkedAt = checkedAt;
            this.offline = offline;
        }

        public boolean isProblem() {
            return status == Status.REVOKED || status == Status.SUSPENDED
                    || status == Status.EXPIRED || status == Status.CHAIN_INVALID
                    || status == Status.UNTRUSTED_ROOT;
        }

        public boolean isChecked() {
            return checkedAt > 0;
        }
    }

    private final Context mContext;

    public KeyboxRevocationChecker(Context context) {
        mContext = context.getApplicationContext();
    }

    public Result getLastResult() {
        String raw = prefs().getString(PREF_STATE, null);
        if (raw == null || raw.isEmpty()) {
            return new Result(Status.UNKNOWN, null, 0, false);
        }
        try {
            JSONObject obj = new JSONObject(raw);
            Status status;
            try {
                status = Status.valueOf(obj.optString("status", Status.UNKNOWN.name()));
            } catch (IllegalArgumentException e) {
                status = Status.UNKNOWN;
            }
            String reason = obj.isNull("reason") ? null : obj.optString("reason", null);
            return new Result(status, reason, obj.optLong("checkedAt", 0),
                    obj.optBoolean("offline", false));
        } catch (Exception e) {
            return new Result(Status.UNKNOWN, null, 0, false);
        }
    }

    public boolean shouldAutoCheck() {
        Result result = getLastResult();
        return result.checkedAt == 0
                || System.currentTimeMillis() - result.checkedAt > AUTO_CHECK_INTERVAL_MS;
    }

    public void clear() {
        prefs().edit().remove(PREF_STATE).apply();
    }

    public Result check(byte[] keybox) {
        long now = System.currentTimeMillis();
        List<KeyboxManager.KeyboxChain> chains;
        try {
            chains = KeyboxManager.parseChains(unwrap(keybox));
        } catch (Exception e) {
            return persist(new Result(Status.CHAIN_INVALID, "INVALID_XML", now, false), null);
        }
        if (chains.isEmpty()) {
            return persist(new Result(Status.CHAIN_INVALID, "NO_CERTIFICATES", now, false), null);
        }

        Set<String> serialKeys = new LinkedHashSet<>();
        for (KeyboxManager.KeyboxChain chain : chains) {
            if (!chain.details.isEmpty()) {
                addSerialKeys(serialKeys, chain.details.get(0));
            }
        }

        JSONObject entries = fetchEntries();
        boolean offline = entries == null;
        List<String> revoked = new ArrayList<>();
        if (entries != null) {
            Status listed = null;
            String reason = null;
            for (String serial : serialKeys) {
                JSONObject entry = entries.optJSONObject(serial);
                if (entry == null) {
                    continue;
                }
                String status = entry.optString("status", "");
                if ("REVOKED".equalsIgnoreCase(status) || "SUSPENDED".equalsIgnoreCase(status)) {
                    listed = "REVOKED".equalsIgnoreCase(status)
                            ? Status.REVOKED : Status.SUSPENDED;
                    reason = entry.isNull("reason") ? null : entry.optString("reason", null);
                    revoked.add(serial);
                }
            }
            if (listed != null) {
                return persist(new Result(listed, reason, now, false), revoked);
            }
        } else {
            String cachedMatch = findSerial(serialKeys, readRevokedSerials());
            if (cachedMatch != null) {
                return persist(new Result(Status.REVOKED, "CACHED", now, true), null);
            }
        }

        Date date = new Date(now);
        boolean expiring = false;
        for (KeyboxManager.KeyboxChain chain : chains) {
            X509Certificate leaf = chain.certificates.get(0);
            if (date.after(leaf.getNotAfter())) {
                return persist(new Result(Status.EXPIRED,
                        formatDate(leaf.getNotAfter().getTime()), now, offline), null);
            }
            long days = (leaf.getNotAfter().getTime() - now) / 86400000L;
            if (days <= EXPIRY_WARN_DAYS) {
                expiring = true;
            }
        }

        Set<String> roots = loadRoots();
        boolean rootsUnavailable = roots == null || roots.isEmpty();
        boolean rootUnknown = false;
        for (KeyboxManager.KeyboxChain chain : chains) {
            List<X509Certificate> certs = chain.certificates;
            try {
                if (certs.size() < 2) {
                    return persist(new Result(Status.CHAIN_INVALID, "CHAIN_TOO_SHORT", now,
                            false), null);
                }
                for (int i = 0; i + 1 < certs.size(); i++) {
                    certs.get(i).verify(certs.get(i + 1).getPublicKey());
                }
                X509Certificate root = certs.get(certs.size() - 1);
                root.verify(root.getPublicKey());
            } catch (Exception e) {
                return persist(new Result(Status.CHAIN_INVALID, "CHAIN_NOT_SIGNED", now, false),
                        null);
            }
            X509Certificate root = certs.get(certs.size() - 1);
            if (!rootsUnavailable) {
                if (!roots.contains(publicKeyDigest(root))) {
                    return persist(new Result(Status.UNTRUSTED_ROOT, "ROOT_NOT_GOOGLE",
                            now, false), null);
                }
            } else if (!FALLBACK_ROOTS.contains(derDigest(root))) {
                rootUnknown = true;
            }
        }

        if (expiring) {
            return persist(new Result(Status.EXPIRING_SOON, null, now, offline), null);
        }
        if (offline || rootsUnavailable || rootUnknown) {
            Result previous = getLastResult();
            if (previous.isProblem()) {
                return new Result(previous.status, previous.reason, previous.checkedAt, true);
            }
            return persist(new Result(Status.UNVERIFIED,
                    offline ? "OFFLINE" : "ROOTS_UNAVAILABLE", now, offline), null);
        }
        return persist(new Result(Status.VALID, null, now, false), revoked);
    }

    private static void addSerialKeys(Set<String> keys, KeyboxManager.KeyboxCertificate leaf) {
        if (leaf.serialHex != null && !leaf.serialHex.isEmpty()) {
            keys.add(leaf.serialHex);
            keys.add(leaf.serialHex.toUpperCase(Locale.US));
        }
        if (leaf.serialDecimal != null && !leaf.serialDecimal.isEmpty()) {
            keys.add(leaf.serialDecimal);
        }
    }

    private static String findSerial(Set<String> serialKeys, Set<String> cached) {
        for (String key : serialKeys) {
            if (cached.contains(key)) {
                return key;
            }
        }
        return null;
    }

    private Result persist(Result result, @Nullable Collection<String> revokedSerials) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("status", result.status.name());
            if (result.reason != null) {
                obj.put("reason", result.reason);
            }
            obj.put("checkedAt", result.checkedAt);
            obj.put("offline", result.offline);
            if (revokedSerials != null) {
                JSONArray serials = new JSONArray();
                for (String serial : revokedSerials) {
                    serials.put(serial);
                }
                obj.put("revokedSerials", serials);
                obj.put("serialsAt", System.currentTimeMillis());
            } else {
                String previous = prefs().getString(PREF_STATE, null);
                if (previous != null && !previous.isEmpty()) {
                    JSONObject old = new JSONObject(previous);
                    JSONArray serials = old.optJSONArray("revokedSerials");
                    if (serials != null) {
                        obj.put("revokedSerials", serials);
                        obj.put("serialsAt", old.optLong("serialsAt", 0));
                    }
                }
            }
            prefs().edit().putString(PREF_STATE, obj.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist revocation state", e);
        }
        return result;
    }

    private Set<String> readRevokedSerials() {
        Set<String> result = new HashSet<>();
        String raw = prefs().getString(PREF_STATE, null);
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        try {
            JSONObject obj = new JSONObject(raw);
            if (System.currentTimeMillis() - obj.optLong("serialsAt", 0) > REVOKED_CACHE_MS) {
                return result;
            }
            JSONArray array = obj.optJSONArray("revokedSerials");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    result.add(array.optString(i));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    @Nullable
    private Set<String> loadRoots() {
        Set<String> cached = readRoots();
        if (cached != null) {
            return cached;
        }
        byte[] bytes = fetch(ROOTS_URL);
        if (bytes == null) {
            return null;
        }
        try {
            JSONArray array = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Set<String> roots = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                try {
                    X509Certificate cert = (X509Certificate) factory.generateCertificate(
                            new ByteArrayInputStream(
                                    array.getString(i).getBytes(StandardCharsets.UTF_8)));
                    roots.add(publicKeyDigest(cert));
                } catch (Exception ignored) {
                }
            }
            if (!roots.isEmpty()) {
                saveRoots(roots);
                return roots;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse root certificates", e);
        }
        return null;
    }

    @Nullable
    private Set<String> readRoots() {
        String raw = prefs().getString(PREF_ROOTS, null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(raw);
            if (System.currentTimeMillis() - obj.optLong("at", 0) > ROOTS_CACHE_MS) {
                return null;
            }
            JSONArray array = obj.optJSONArray("fingerprints");
            if (array == null) {
                return null;
            }
            Set<String> roots = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                roots.add(array.optString(i).toLowerCase(Locale.US));
            }
            return roots;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveRoots(Set<String> roots) {
        try {
            JSONObject obj = new JSONObject();
            JSONArray array = new JSONArray();
            for (String root : roots) {
                array.put(root);
            }
            obj.put("at", System.currentTimeMillis());
            obj.put("fingerprints", array);
            prefs().edit().putString(PREF_ROOTS, obj.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to cache roots", e);
        }
    }

    @Nullable
    private JSONObject fetchEntries() {
        byte[] body = fetch(STATUS_URL);
        if (body == null) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(new String(body, StandardCharsets.UTF_8));
            return root.optJSONObject("entries");
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse status response", e);
            return null;
        }
    }

    @Nullable
    private byte[] fetch(String url) {
        HttpURLConnection connection = null;
        try {
            String busted = url + (url.contains("?") ? "&" : "?") + "_=" + System.nanoTime();
            connection = (HttpURLConnection) new URL(busted).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                return input.readAllBytes();
            }
        } catch (Exception e) {
            Log.w(TAG, "Network request failed: " + url, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] unwrap(byte[] keybox) {
        String text = new String(keybox, StandardCharsets.UTF_8).trim();
        if (!text.startsWith("<")) {
            try {
                return Base64.decode(text, Base64.DEFAULT);
            } catch (Exception ignored) {
            }
        }
        return keybox;
    }

    private static String publicKeyDigest(X509Certificate certificate) {
        try {
            return sha256Hex(certificate.getPublicKey().getEncoded());
        } catch (Exception e) {
            return "";
        }
    }

    private static String derDigest(X509Certificate certificate) {
        try {
            return sha256Hex(certificate.getEncoded()).toUpperCase(Locale.US);
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                builder.append(String.format(Locale.US, "%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String formatDate(long millis) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new Date(millis));
    }

    private SharedPreferences prefs() {
        return mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}