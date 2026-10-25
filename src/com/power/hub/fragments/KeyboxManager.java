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
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import com.android.settings.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class KeyboxManager {

    private static final String TAG = "KeyboxManager";
    private static final String LEGACY_TRICKY_DIR = "/data/system/tricky_store";
    private static final String KEYBOX_FILE = "keybox.xml";
    private static final String TARGET_FILE = "target.txt";
    private static final String KEYBOX_KEY = "spoof_trickystore_keybox";
    private static final String TARGET_KEY = "spoof_trickystore_target";
    private static final String PATCH_KEY = "spoof_trickystore_patch";
    private static final String PATCH_MODE_KEY = "spoof_trickystore_patch_mode";
    private static final String PATCH_MODE_AUTO = "auto";
    private static final String PATCH_MODE_MANUAL = "manual";

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$");
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    public static final Set<String> XPOSED_PACKAGES = new LinkedHashSet<>(Arrays.asList(
            "org.lsposed.manager",
            "io.github.lsposed.manager",
            "de.robv.android.xposed.installer",
            "org.meowcat.edxposed.manager",
            "com.solohsu.android.edxp.manager",
            "io.va.exposed",
            "com.topjohnwu.lsplant.manager",
            "me.weishu.exposed"));

    public static final List<String> DEFAULT_TARGETS = Arrays.asList(
            "android",
            "com.android.vending",
            "com.google.android.gsf",
            "com.google.android.gms",
            "com.google.android.contactkeys",
            "com.google.android.ims",
            "com.google.android.safetycore",
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user",
            "com.revolut.revolut!",
            "io.github.qwq233.keyattestation?",
            "io.github.vvb2060.keyattestation?",
            "io.github.vvb2060.mahoshojo?",
            "icu.nullptr.nativetest?",
            "com.reveny.nativecheck?",
            "com.zhenxi.hunter?",
            "com.android.nativetest?",
            "io.liankong.riskdetector?",
            "luna.safe.luna?",
            "com.eltavine.duckdetector?",
            "com.rem01gaming.disclosure?",
            "wu.keyChain.test?",
            "com.kikyps.crackme?",
            "com.chunqiunativecheck?");

    private final Context mContext;

    public KeyboxManager(Context context) {
        mContext = context.getApplicationContext();
        migrateLegacyDataIfNeeded();
    }

    private String getString(String key) {
        return Settings.Secure.getString(mContext.getContentResolver(), key);
    }

    private void putString(String key, String value) {
        Settings.Secure.putString(mContext.getContentResolver(), key, value);
    }

    public boolean keyboxExists() {
        String stored = getString(KEYBOX_KEY);
        return stored != null && !stored.isEmpty();
    }

    public byte[] readKeybox(Uri uri) throws Exception {
        byte[] bytes;
        try (InputStream inputStream = mContext.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IllegalStateException("Unable to open file");
            }
            bytes = inputStream.readAllBytes();
        }
        String xml = normalizeKeybox(bytes);
        if (xml == null) {
            throw new IllegalArgumentException(mContext.getString(R.string.keybox_error_not_xml));
        }
        byte[] normalized = xml.getBytes(StandardCharsets.UTF_8);
        KeyboxInfo info = validateKeybox(normalized);
        if (!info.valid) {
            throw new IllegalArgumentException(info.error != null
                    ? info.error : mContext.getString(R.string.keybox_error_invalid));
        }
        if (info.expired) {
            throw new IllegalArgumentException(mContext.getString(R.string.keybox_error_expired));
        }
        return normalized;
    }

    public void saveKeybox(byte[] normalized) {
        putString(KEYBOX_KEY, Base64.encodeToString(normalized, Base64.NO_WRAP));
    }

    public void restoreDefaultTargets() {
        saveTargetLines(new ArrayList<>(DEFAULT_TARGETS));
    }

    public static String normalizeKeybox(byte[] bytes) {
        String text = stripBom(new String(bytes, StandardCharsets.UTF_8).trim());
        if (!text.startsWith("<")) {
            try {
                text = stripBom(new String(Base64.decode(text, Base64.DEFAULT),
                        StandardCharsets.UTF_8).trim());
            } catch (Exception e) {
                return null;
            }
        }
        return text.startsWith("<") ? text.trim() : null;
    }

    private static String stripBom(String text) {
        String result = text;
        while (result.startsWith("\uFEFF") || result.startsWith("\uFFFE")
                || result.startsWith("\u0000\uFEFF")) {
            result = result.startsWith("\u0000\uFEFF")
                    ? result.substring(2) : result.substring(1);
            result = result.trim();
        }
        return result.trim();
    }

    public void deleteKeybox() {
        putString(KEYBOX_KEY, null);
    }

    public int getTargetAppCount() {
        return readTargetLines().size();
    }

    public void importTargetFile(Uri uri) throws Exception {
        String content;
        try (InputStream inputStream = mContext.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IllegalStateException("Unable to open file");
            }
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> lines;
        try {
            lines = parseTargetContent(content);
        } catch (Exception e) {
            throw new IllegalArgumentException(mContext.getString(
                    R.string.target_error_invalid, e.getMessage() == null ? "-" : e.getMessage()));
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(mContext.getString(R.string.target_error_empty));
        }
        putString(TARGET_KEY, String.join("\n", normalizeTargetLines(lines)));
    }

    public void saveTargetLines(List<String> lines) {
        try {
            putString(TARGET_KEY, String.join("\n", normalizeTargetLines(lines)));
        } catch (Exception e) {
            Log.e(TAG, "Failed to save target lines", e);
        }
    }

    public List<String> readTargetLines() {
        String content = getString(TARGET_KEY);
        List<String> result = new ArrayList<>();
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

    public String getPatchConfig() {
        String value = getString(PATCH_KEY);
        return value != null ? value : "";
    }

    public void savePatchConfig(String config) {
        putString(PATCH_KEY, config == null || config.trim().isEmpty() ? null : config);
    }

    public boolean isPatchAuto() {
        String mode = getString(PATCH_MODE_KEY);
        if (mode != null) {
            return PATCH_MODE_AUTO.equals(mode);
        }
        return getPatchConfig().trim().isEmpty();
    }

    public void setPatchAuto(boolean auto) {
        putString(PATCH_MODE_KEY, auto ? PATCH_MODE_AUTO : PATCH_MODE_MANUAL);
    }

    public byte[] getKeyboxBytes() {
        String stored = getString(KEYBOX_KEY);
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return Base64.decode(stored, Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    public KeyboxInfo getKeyboxInfo() {
        String stored = getString(KEYBOX_KEY);
        if (stored == null || stored.isEmpty()) {
            return KeyboxInfo.absent();
        }
        try {
            return validateKeybox(Base64.decode(stored, Base64.DEFAULT));
        } catch (Exception e) {
            return new KeyboxInfo(false, false, new ArrayList<>(), 0, null, null,
                    e.getMessage());
        }
    }

    public static KeyboxInfo validateKeybox(byte[] bytes) {
        String xml = new String(bytes, StandardCharsets.UTF_8);
        if (!xml.contains("<Key algorithm=\"ecdsa\">")
                && !xml.contains("<Key algorithm=\"rsa\">")) {
            return new KeyboxInfo(false, false, new ArrayList<>(), 0, null, null,
                    "No ECDSA or RSA key block");
        }
        if (!xml.contains("<serial>") && !xml.contains("DeviceID")) {
            return new KeyboxInfo(false, false, new ArrayList<>(), 0, null, null,
                    "No serial or DeviceID");
        }

        List<KeyboxChain> chains;
        try {
            chains = parseChains(bytes);
        } catch (Exception e) {
            return new KeyboxInfo(false, false, new ArrayList<>(), 0, null, null,
                    "Invalid keybox XML");
        }
        boolean complete = false;
        for (KeyboxChain chain : chains) {
            if (chain.complete) {
                complete = true;
                break;
            }
        }
        if (!complete) {
            return new KeyboxInfo(false, false, new ArrayList<>(), 0, null, null,
                    "No complete key (private key + certificate)");
        }
        List<String> algorithms = new ArrayList<>();
        List<KeyboxCertificate> certificates = new ArrayList<>();
        List<KeyboxCertificate> leaves = new ArrayList<>();
        Date notBefore = null;
        Date notAfter = null;
        for (KeyboxChain chain : chains) {
            if (chain.algorithm != null && !algorithms.contains(chain.algorithm)) {
                algorithms.add(chain.algorithm);
            }
            certificates.addAll(chain.details);
            if (!chain.details.isEmpty()) {
                leaves.add(chain.details.get(0));
            }
            for (KeyboxCertificate cert : chain.details) {
                if (notBefore == null || cert.notBefore.after(notBefore)) notBefore = cert.notBefore;
                if (notAfter == null || cert.notAfter.before(notAfter)) notAfter = cert.notAfter;
            }
        }
        if (certificates.isEmpty()) {
            return new KeyboxInfo(false, false, algorithms, 0, null, null,
                    "Keybox contains no usable certificates");
        }
        Date now = new Date();
        boolean expired = (notAfter != null && now.after(notAfter))
                || (notBefore != null && now.before(notBefore));
        return new KeyboxInfo(true, expired, algorithms, certificates.size(), notBefore, notAfter,
                null, certificates, leaves);
    }

    public static List<KeyboxChain> parseChains(byte[] bytes) throws Exception {
        String xml = XML_COMMENT.matcher(
                new String(bytes, StandardCharsets.UTF_8)).replaceAll("");
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(xml));
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        List<KeyboxChain> chains = new ArrayList<>();
        String algorithm = null;
        boolean inCert = false;
        boolean inPrivateKey = false;
        boolean hasPrivateKey = false;
        StringBuilder certText = null;
        StringBuilder keyText = null;
        List<java.security.cert.X509Certificate> certs = null;
        List<KeyboxCertificate> details = null;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();
            if (event == XmlPullParser.START_TAG) {
                if ("Key".equals(tag)) {
                    algorithm = parser.getAttributeValue(null, "algorithm");
                    hasPrivateKey = false;
                    certs = new ArrayList<>();
                    details = new ArrayList<>();
                } else if ("Certificate".equals(tag) && certs != null) {
                    inCert = true;
                    certText = new StringBuilder();
                } else if ("PrivateKey".equals(tag) && certs != null) {
                    inPrivateKey = true;
                    keyText = new StringBuilder();
                }
            } else if (event == XmlPullParser.TEXT) {
                if (inCert && certText != null) {
                    certText.append(parser.getText());
                } else if (inPrivateKey && keyText != null) {
                    keyText.append(parser.getText());
                }
            } else if (event == XmlPullParser.END_TAG) {
                if ("Certificate".equals(tag) && inCert) {
                    inCert = false;
                    if (certText != null && certs != null) {
                        java.security.cert.X509Certificate cert =
                                decodeCertificate(certFactory, certText.toString());
                        if (cert != null) {
                            certs.add(cert);
                            details.add(new KeyboxCertificate(algorithm, cert));
                        }
                    }
                    certText = null;
                } else if ("PrivateKey".equals(tag) && inPrivateKey) {
                    inPrivateKey = false;
                    hasPrivateKey = keyText != null && !keyText.toString().trim().isEmpty();
                    keyText = null;
                } else if ("Key".equals(tag)) {
                    if (certs != null && !certs.isEmpty()) {
                        chains.add(new KeyboxChain(algorithm, certs, details, hasPrivateKey));
                    }
                    certs = null;
                    details = null;
                    algorithm = null;
                    hasPrivateKey = false;
                }
            }
            event = parser.next();
        }
        return chains;
    }

    private static java.security.cert.X509Certificate decodeCertificate(
            CertificateFactory factory, String text) {
        try {
            String pem = text.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            return (java.security.cert.X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(Base64.decode(pem, Base64.DEFAULT)));
        } catch (Exception e) {
            return null;
        }
    }

    public static String sha256Hex(java.security.cert.X509Certificate certificate) {
        try {
            return toHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(certificate.getEncoded()));
        } catch (Exception e) {
            return "";
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(java.util.Locale.US, "%02x", value));
        }
        return builder.toString();
    }

    public static List<String> parseTargetContent(String content) throws Exception {
        Set<String> lines = new LinkedHashSet<>();
        if (content == null) {
            return new ArrayList<>();
        }
        String text = content.trim();
        if (text.startsWith("[")) {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String packageName = object.getString("package").trim();
                if (!PACKAGE_PATTERN.matcher(packageName).matches()) {
                    throw new IllegalArgumentException(packageName);
                }
                String mode = object.optString("mode", "").trim();
                String symbol = "";
                if (!mode.isEmpty()) {
                    switch (mode.toUpperCase(Locale.US)) {
                        case "AUTO":
                            break;
                        case "LEAF_HACK":
                            symbol = "?";
                            break;
                        case "GENERATE":
                            symbol = "!";
                            break;
                        case "SKIP":
                            symbol = "-";
                            break;
                        default:
                            throw new IllegalArgumentException("mode: " + mode);
                    }
                }
                lines.add(packageName + symbol);
            }
            return new ArrayList<>(lines);
        }

        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            char suffix = 0;
            char last = line.charAt(line.length() - 1);
            if (last == '?' || last == '!' || last == '-') {
                suffix = last;
                line = line.substring(0, line.length() - 1).trim();
            }
            if (!PACKAGE_PATTERN.matcher(line).matches()) {
                throw new IllegalArgumentException(raw.trim());
            }
            lines.add(suffix == 0 ? line : line + suffix);
        }
        return new ArrayList<>(lines);
    }

    public static List<String> normalizeTargetLines(List<String> lines) {
        Set<String> seen = new LinkedHashSet<>();
        if (lines != null) {
            for (String raw : lines) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                char suffix = 0;
                char last = line.charAt(line.length() - 1);
                if (last == '?' || last == '!' || last == '-') {
                    suffix = last;
                    line = line.substring(0, line.length() - 1).trim();
                }
                if (PACKAGE_PATTERN.matcher(line).matches()) {
                    if (XPOSED_PACKAGES.contains(line)) {
                        suffix = '-';
                    }
                    seen.add(suffix == 0 ? line : line + suffix);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    public static final class KeyboxInfo {
        public final boolean valid;
        public final boolean expired;
        public final List<String> algorithms;
        public final int certificateCount;
        public final Date notBefore;
        public final Date notAfter;
        public final String error;
        public final List<KeyboxCertificate> certificates;
        public final List<KeyboxCertificate> leaves;

        KeyboxInfo(boolean valid, boolean expired, List<String> algorithms,
                int certificateCount, Date notBefore, Date notAfter, String error) {
            this(valid, expired, algorithms, certificateCount, notBefore, notAfter, error,
                    new ArrayList<>(), new ArrayList<>());
        }

        KeyboxInfo(boolean valid, boolean expired, List<String> algorithms,
                int certificateCount, Date notBefore, Date notAfter, String error,
                List<KeyboxCertificate> certificates, List<KeyboxCertificate> leaves) {
            this.valid = valid;
            this.expired = expired;
            this.algorithms = algorithms;
            this.certificateCount = certificateCount;
            this.notBefore = notBefore;
            this.notAfter = notAfter;
            this.error = error;
            this.certificates = certificates;
            this.leaves = leaves;
        }

        public static KeyboxInfo absent() {
            return new KeyboxInfo(false, false, new ArrayList<>(), 0, null, null, null);
        }
    }

    public static final class KeyboxChain {
        public final String algorithm;
        public final List<X509Certificate> certificates;
        public final List<KeyboxCertificate> details;
        public final boolean complete;

        KeyboxChain(String algorithm, List<X509Certificate> certificates,
                List<KeyboxCertificate> details, boolean complete) {
            this.algorithm = algorithm;
            this.certificates = certificates;
            this.details = details;
            this.complete = complete;
        }
    }

    public static final class KeyboxCertificate {
        public final String algorithm;
        public final String subject;
        public final String issuer;
        public final String serialHex;
        public final String serialDecimal;
        public final String sha256;
        public final Date notBefore;
        public final Date notAfter;

        KeyboxCertificate(String algorithm, X509Certificate certificate) {
            this.algorithm = algorithm;
            this.subject = certificate.getSubjectX500Principal().getName();
            this.issuer = certificate.getIssuerX500Principal().getName();
            this.serialHex = certificate.getSerialNumber().toString(16);
            this.serialDecimal = certificate.getSerialNumber().toString();
            this.sha256 = sha256Hex(certificate);
            this.notBefore = certificate.getNotBefore();
            this.notAfter = certificate.getNotAfter();
        }
    }

    private void migrateLegacyDataIfNeeded() {
        if (!keyboxExists()) {
            File keyboxFile = new File(LEGACY_TRICKY_DIR, KEYBOX_FILE);
            if (keyboxFile.exists() && keyboxFile.canRead()) {
                try {
                    String xml = normalizeKeybox(Files.readAllBytes(keyboxFile.toPath()));
                    if (xml != null) {
                        putString(KEYBOX_KEY, Base64.encodeToString(
                                xml.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                        Log.i(TAG, "Migrated legacy keybox.xml");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to migrate legacy keybox", e);
                }
            }
        }

        List<String> targets = readTargetLines();
        if (targets.isEmpty()) {
            File targetFile = new File(LEGACY_TRICKY_DIR, TARGET_FILE);
            if (targetFile.exists() && targetFile.canRead()) {
                try {
                    String content = new String(Files.readAllBytes(targetFile.toPath()),
                            StandardCharsets.UTF_8);
                    putString(TARGET_KEY, content);
                    Log.i(TAG, "Migrated legacy target.txt");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to migrate legacy target list", e);
                }
            }
        }
    }
}
