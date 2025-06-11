/*
  * Copyright (C) 2019-2024 The Evolution X Project
  * SPDX-License-Identifier: Apache-2.0
  */
 
 package com.power.hub.fragments;
 
 import android.app.Activity;
 import android.app.AlertDialog;
 import android.content.ContentResolver;
 import android.content.Context;
 import android.content.pm.ApplicationInfo;
 import android.content.pm.PackageManager;
 import android.content.res.Resources;
 import android.content.Intent;
 import android.net.Uri;
 import android.os.Bundle;
 import android.os.Handler;
 import android.os.SystemProperties;
 import android.text.Editable;
 import android.text.TextWatcher;
 import android.util.Log;
 import android.widget.ArrayAdapter;
 import android.widget.EditText;
 import android.widget.LinearLayout;
 import android.widget.ListView;
 import android.widget.Toast;
 import android.provider.Settings;
 import android.text.TextUtils;
 
 import androidx.activity.result.ActivityResultLauncher;
 import androidx.activity.result.contract.ActivityResultContracts;
 import androidx.preference.Preference;
 import androidx.preference.Preference.OnPreferenceChangeListener;
 import androidx.preference.PreferenceCategory;
 import androidx.preference.PreferenceScreen;
 
 import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
 import com.android.internal.util.voltage.SystemRestartUtils;
 import com.android.settings.R;
 import com.android.settings.search.BaseSearchIndexProvider;
 import com.android.settings.SettingsPreferenceFragment;
 import com.android.settingslib.search.SearchIndexable;
 
 import org.w3c.dom.*;
 import javax.xml.parsers.*;

 import java.io.InputStream;
 import java.io.OutputStream;
 import java.net.HttpURLConnection;
 import java.net.URL;
 import java.nio.charset.StandardCharsets;
 import java.util.Arrays;
 import java.util.Comparator;
 import java.util.HashMap;
 import java.util.HashSet;
 import java.util.Iterator;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 import java.util.stream.Collectors;
 
 import com.voltage.support.preferences.SystemPropertySwitchPreference;
 import com.power.hub.utils.Utils;
 
 import org.json.JSONArray;
 import org.json.JSONException;
 import org.json.JSONObject;
 
 @SearchIndexable
 public class Spoofing extends SettingsPreferenceFragment implements
         Preference.OnPreferenceChangeListener {
 
     private static final String TAG = "Spoofing";
 
     private static final String KEY_SYSTEM_WIDE_CATEGORY = "spoofing_system_wide_category";
     private static final String KEY_PIF_JSON_FILE_PREFERENCE = "pif_json_file_preference";
     private static final String KEY_GAME_PROPS_JSON_FILE_PREFERENCE = "game_props_json_file_preference";
     private static final String KEY_UPDATE_JSON_BUTTON = "update_pif_json";
     private static final String KEY_IMPORT_KEYBOX = "import_keybox";
     private static final String KEY_CLEAR_KEYBOX = "clear_keybox";
     private static final String KEYBOX_PATH = "/data/misc/keybox/keybox.xml";
     private static final String SYS_GMS_SPOOF = "persist.sys.pixelprops.gms";
     private static final String SYS_GOOGLE_SPOOF = "persist.sys.pphooks.enable";
     private static final String SYS_GAMEPROP_SPOOF = "persist.sys.gameprops.enabled";
     private static final String SYS_GPHOTOS_SPOOF = "persist.sys.gphooks.enable";
     private static final String SYS_SNAP_SPOOF = "persist.sys.snap.enable";
     private static final String SYS_VENDING_SPOOF = "persist.sys.vending.enable";
     private static final String SYS_ENABLE_TENSOR_FEATURES = "persist.sys.features.tensor";

     private Preference mGamePropsJsonFilePreference;
     private Preference mPifJsonFilePreference;
     private Preference mUpdateJsonButton;
     private Preference mImportKeybox;
     private Preference mClearKeybox;
     private PreferenceCategory mSystemWideCategory;
     private SystemPropertySwitchPreference mGmsSpoof;
     private SystemPropertySwitchPreference mGoogleSpoof;
     private SystemPropertySwitchPreference mGamePropsSpoof;
     private SystemPropertySwitchPreference mGphotosSpoof;
     private SystemPropertySwitchPreference mSnapSpoof;
     private SystemPropertySwitchPreference mVendingSpoof;
     private SystemPropertySwitchPreference mTensorFeaturesToggle;
 
     private Handler mHandler;

     private final ActivityResultLauncher<String> mImportKeyboxLauncher = registerForActivityResult(
             new ActivityResultContracts.GetContent(),
             uri -> {
                 if (uri != null) {
                     handleKeyboxImport(uri);
                 }
             });
 
     @Override
     public void onCreate(Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);
         mHandler = new Handler();
         addPreferencesFromResource(R.xml.spoofing);
 
         final Context context = getContext();
         final ContentResolver resolver = context.getContentResolver();
         final PreferenceScreen prefScreen = getPreferenceScreen();
         final Resources resources = context.getResources();
 
         mSystemWideCategory = (PreferenceCategory) findPreference(KEY_SYSTEM_WIDE_CATEGORY);
         mGamePropsSpoof = (SystemPropertySwitchPreference) findPreference(SYS_GAMEPROP_SPOOF);
         mGphotosSpoof = (SystemPropertySwitchPreference) findPreference(SYS_GPHOTOS_SPOOF);
         mGmsSpoof = (SystemPropertySwitchPreference) findPreference(SYS_GMS_SPOOF);
         mGoogleSpoof = (SystemPropertySwitchPreference) findPreference(SYS_GOOGLE_SPOOF);
         mGamePropsJsonFilePreference = findPreference(KEY_GAME_PROPS_JSON_FILE_PREFERENCE);
         mPifJsonFilePreference = findPreference(KEY_PIF_JSON_FILE_PREFERENCE);
         mSnapSpoof = (SystemPropertySwitchPreference) findPreference(SYS_SNAP_SPOOF);
         mVendingSpoof = (SystemPropertySwitchPreference) findPreference(SYS_VENDING_SPOOF);
         mUpdateJsonButton = findPreference(KEY_UPDATE_JSON_BUTTON);
         mTensorFeaturesToggle = (SystemPropertySwitchPreference) findPreference(SYS_ENABLE_TENSOR_FEATURES);
 
         String model = SystemProperties.get("ro.product.model");
         boolean isTensorDevice = model.matches("Pixel [6-9][a-zA-Z ]*");
         boolean isPixelGmsEnabled = SystemProperties.getBoolean(SYS_GMS_SPOOF, true); // Default to Pixel GMS
 
         if (Utils.isCurrentlySupportedPixel()) {
             mGoogleSpoof.setDefaultValue(false);
             if (isMainlineTensorModel(model)) {
                 mSystemWideCategory.removePreference(mGoogleSpoof);
             }
         }
 
         if (isTensorDevice) {
             mSystemWideCategory.removePreference(mTensorFeaturesToggle);
         }
 
         mGmsSpoof.setOnPreferenceChangeListener(this);
         mGoogleSpoof.setOnPreferenceChangeListener(this);
         mGphotosSpoof.setOnPreferenceChangeListener(this);
         mGamePropsSpoof.setOnPreferenceChangeListener(this);
         mSnapSpoof.setOnPreferenceChangeListener(this);
         mVendingSpoof.setOnPreferenceChangeListener(this);
         mTensorFeaturesToggle.setOnPreferenceChangeListener(this);
 
         mPifJsonFilePreference.setOnPreferenceClickListener(preference -> {
             openFileSelector(10001);
             return true;
         });
 
         mGamePropsJsonFilePreference.setOnPreferenceClickListener(preference -> {
             openFileSelector(10002);
             return true;
         });
 
         mUpdateJsonButton.setOnPreferenceClickListener(preference -> {
             updatePropertiesFromUrl("https://raw.githubusercontent.com/VoltageOS/.github/refs/heads/main/profile/pif.json");
             return true;
         });
 
         Preference showPropertiesPref = findPreference("show_pif_properties");
         if (showPropertiesPref != null) {
             showPropertiesPref.setOnPreferenceClickListener(preference -> {
                 showPropertiesDialog();
                 return true;
             });
         }

         mClearKeybox = findPreference(KEY_CLEAR_KEYBOX);
         mClearKeybox.setOnPreferenceClickListener(preference -> {
             clearKeybox();
             return true;
         });

         mImportKeybox = findPreference(KEY_IMPORT_KEYBOX);
         mImportKeybox.setOnPreferenceClickListener(preference -> {
             mImportKeyboxLauncher.launch("text/xml");
             return true;
         });
     }
 
     private boolean isMainlineTensorModel(String model) {
         return model.matches("Pixel [8-9][a-zA-Z ]*");
     }
 
     private void openFileSelector(int requestCode) {
         Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
         intent.setType("application/json");
         startActivityForResult(intent, requestCode);
     }

    @Override
     public void onActivityResult(int requestCode, int resultCode, Intent data) {
         super.onActivityResult(requestCode, resultCode, data);
         if (resultCode == Activity.RESULT_OK && data != null) {
             Uri uri = data.getData();
             if (uri != null) {
                 if (requestCode == 10001) {
                     loadPifJson(uri);
                 } else if (requestCode == 10002) {
                     loadGameSpoofingJson(uri);
                 }
             }
         }
     }
 
     private void showPropertiesDialog() {
         StringBuilder properties = new StringBuilder();
         try {
             JSONObject jsonObject = new JSONObject();
             String[] keys = {
                 "persist.sys.pihooks_ID",
                 "persist.sys.pihooks_BRAND",
                 "persist.sys.pihooks_DEVICE",
                 "persist.sys.pihooks_FINGERPRINT",
                 "persist.sys.pihooks_MANUFACTURER",
                 "persist.sys.pihooks_MODEL",
                 "persist.sys.pihooks_PRODUCT",
                 "persist.sys.pihooks_SECURITY_PATCH",
                 "persist.sys.pihooks_DEVICE_INITIAL_SDK_INT"
             };
             for (String key : keys) {
                 String value = SystemProperties.get(key, null);
                 if (value != null) {
                     String buildKey = key.replace("persist.sys.pihooks_", "");
                     jsonObject.put(buildKey, value);
                 }
             }
             properties.append(jsonObject.toString(4));
         } catch (JSONException e) {
             Log.e(TAG, "Error creating JSON from properties", e);
             properties.append(getString(R.string.error_loading_properties));
         }
         new AlertDialog.Builder(getContext())
             .setTitle(R.string.show_pif_properties_title)
             .setMessage(properties.toString())
             .setPositiveButton(android.R.string.ok, null)
             .show();
     }
 
     private void updatePropertiesFromUrl(String urlString) {
         new Thread(() -> {
             try {
                 URL url = new URL(urlString);
                 HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                 try (InputStream inputStream = urlConnection.getInputStream()) {
                     String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                     Log.d(TAG, "Downloaded JSON data: " + json);
                     JSONObject jsonObject = new JSONObject(json);
                     String spoofedModel = jsonObject.optString("MODEL", "Unknown model");
                     for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                         String key = it.next();
                         String value = jsonObject.getString(key);
                         Log.d(TAG, "Setting property: persist.sys.pihooks_" + key + " = " + value);
                         SystemProperties.set("persist.sys.pihooks_" + key, value);
                     }
                     mHandler.post(() -> {
                         String toastMessage = getString(R.string.toast_spoofing_success, spoofedModel);
                         Toast.makeText(getContext(), toastMessage, Toast.LENGTH_LONG).show();
                     });
 
                 } finally {
                     urlConnection.disconnect();
                 }
             } catch (Exception e) {
                 Log.e(TAG, "Error downloading JSON or setting properties", e);
                 mHandler.post(() -> {
                     Toast.makeText(getContext(), R.string.toast_spoofing_failure, Toast.LENGTH_LONG).show();
                 });
             }
             mHandler.postDelayed(() -> {
                 SystemRestartUtils.showSystemRestartDialog(getContext());
             }, 1250);
         }).start();
     }
 
     private void loadPifJson(Uri uri) {
         Log.d(TAG, "Loading PIF JSON from URI: " + uri.toString());
         try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
             if (inputStream != null) {
                 String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                 Log.d(TAG, "PIF JSON data: " + json);
                 JSONObject jsonObject = new JSONObject(json);
                 for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                     String key = it.next();
                     String value = jsonObject.getString(key);
                     Log.d(TAG, "Setting PIF property: persist.sys.pihooks_" + key + " = " + value);
                     SystemProperties.set("persist.sys.pihooks_" + key, value);
                 }
             }
         } catch (Exception e) {
             Log.e(TAG, "Error reading PIF JSON or setting properties", e);
         }
         mHandler.postDelayed(() -> {
             SystemRestartUtils.showSystemRestartDialog(getContext());
         }, 1250);
     }

     private void handleKeyboxImport(Uri uri) {
         try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
             DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
             DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
             Document doc = dBuilder.parse(in);
             doc.getDocumentElement().normalize();

             Element root = doc.getDocumentElement();
             if (root == null || !"AndroidAttestation".equals(root.getNodeName())) {
                 Log.e(TAG, "Invalid root element. Expected <AndroidAttestation>");
                 showToast(R.string.import_failed);
                 return;
             }

             NodeList keyboxes = doc.getElementsByTagName("Keybox");
             if (keyboxes.getLength() == 0) {
                 Log.e(TAG, "No <Keybox> element found in XML.");
                 showToast(R.string.import_failed);
                 return;
             }

             JSONObject keyboxJson = new JSONObject();

             for (int i = 0; i < keyboxes.getLength(); i++) {
                 Element keyboxElement = (Element) keyboxes.item(i);
                 NodeList keys = keyboxElement.getElementsByTagName("Key");

                 if (keys.getLength() == 0) {
                     Log.w(TAG, "No <Key> entries in <Keybox>. Skipping.");
                     continue;
                 }

                 for (int j = 0; j < keys.getLength(); j++) {
                     Element keyElement = (Element) keys.item(j);
                     String algorithm = keyElement.getAttribute("algorithm").toUpperCase();
                     if (TextUtils.isEmpty(algorithm)) {
                         Log.w(TAG, "Missing 'algorithm' attribute in <Key>. Skipping.");
                         continue;
                     }

                     if (algorithm.equals("ECDSA")) algorithm = "EC";

                     Element privKeyElem = (Element) keyElement.getElementsByTagName("PrivateKey").item(0);
                     if (privKeyElem == null) {
                         Log.w(TAG, "No <PrivateKey> found for algorithm " + algorithm + ". Skipping.");
                         continue;
                     }

                     String privKeyRaw = getRawText(privKeyElem);
                     String privKey = extractBase64FromPEM(privKeyRaw);
                     if (TextUtils.isEmpty(privKey)) {
                         Log.w(TAG, "Empty private key for " + algorithm + ". Skipping.");
                         continue;
                     }
                     keyboxJson.put(algorithm + ".PRIV", privKey);

                     NodeList certList = keyElement.getElementsByTagName("Certificate");
                     for (int k = 0; k < certList.getLength(); k++) {
                         Element certElem = (Element) certList.item(k);
                         String certRaw = getRawText(certElem);
                         String cert = extractBase64FromPEM(certRaw);
                         if (!TextUtils.isEmpty(cert)) {
                             keyboxJson.put(algorithm + ".CERT_" + (k + 1), cert);
                         } else {
                             Log.w(TAG, "Empty certificate #" + (k + 1) + " for " + algorithm);
                         }
                     }
                 }
             }

             if (keyboxJson.length() == 0) {
                 Log.e(TAG, "Parsed keybox is empty. Import failed.");
                 showToast(R.string.import_failed);
                 return;
             }

             Settings.System.putString(requireContext().getContentResolver(),
                     "custom_keybox_data", keyboxJson.toString());

             showToast(R.string.import_success);
             SystemRestartUtils.showSystemRestartDialog(getContext());

         } catch (Exception e) {
             Log.e(TAG, "Keybox import failed", e);
             showToast(R.string.import_failed);
         }
     }

     private String getRawText(Element element) {
         StringBuilder builder = new StringBuilder();
         NodeList children = element.getChildNodes();
         for (int i = 0; i < children.getLength(); i++) {
             Node node = children.item(i);
             if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                 builder.append(node.getNodeValue());
             }
         }
         return builder.toString().trim();
     }

     private String extractBase64FromPEM(String pem) {
         return pem.replaceAll("-----BEGIN [^-]+-----", "")
                   .replaceAll("-----END [^-]+-----", "")
                   .replaceAll("[\\r\\n\\s]+", "");
     }

     private void showToast(int resId) {
         getActivity().runOnUiThread(() -> 
             Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT).show()
         );
     }

     private void clearKeybox() {
         try {
             Settings.System.putString(requireContext().getContentResolver(), "custom_keybox_data", null);
             showToast(R.string.clear_success);
             SystemRestartUtils.showSystemRestartDialog(getContext());
         } catch (Exception e) {
             Log.e(TAG, "Failed to clear keybox", e);
             showToast(R.string.clear_failed);
         }
     }
 
     private void loadGameSpoofingJson(Uri uri) {
         Log.d(TAG, "Loading Game Props JSON from URI: " + uri.toString());
         try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
             if (inputStream != null) {
                 String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                 Log.d(TAG, "Game Props JSON data: " + json);
                 JSONObject jsonObject = new JSONObject(json);
                 for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                     String key = it.next();
                     if (key.startsWith("PACKAGES_") && !key.endsWith("_DEVICE")) {
                         String deviceKey = key + "_DEVICE";
                         if (jsonObject.has(deviceKey)) {
                             JSONObject deviceProps = jsonObject.getJSONObject(deviceKey);
                             JSONArray packages = jsonObject.getJSONArray(key);
                             for (int i = 0; i < packages.length(); i++) {
                                 String packageName = packages.getString(i);
                                 Log.d(TAG, "Spoofing package: " + packageName);
                                 setGameProps(packageName, deviceProps);
                             }
                         }
                     }
                 }
             }
         } catch (Exception e) {
             Log.e(TAG, "Error reading Game Props JSON or setting properties", e);
         }
         mHandler.postDelayed(() -> {
             SystemRestartUtils.showSystemRestartDialog(getContext());
         }, 1250);
     }
 
     private void setGameProps(String packageName, JSONObject deviceProps) {
         try {
             for (Iterator<String> it = deviceProps.keys(); it.hasNext(); ) {
                 String key = it.next();
                 String value = deviceProps.getString(key);
                 String systemPropertyKey = "persist.sys.gameprops." + packageName + "." + key;
                 SystemProperties.set(systemPropertyKey, value);
                 Log.d(TAG, "Set system property: " + systemPropertyKey + " = " + value);
             }
         } catch (JSONException e) {
             Log.e(TAG, "Error parsing device properties", e);
         }
     }
 
     @Override
     public boolean onPreferenceChange(Preference preference, Object newValue) {
         final Context context = getContext();
         final ContentResolver resolver = context.getContentResolver();
         if (preference == mGmsSpoof
             || preference == mGoogleSpoof
             || preference == mGphotosSpoof
             || preference == mGamePropsSpoof
             || preference == mSnapSpoof
             || preference == mVendingSpoof
             || preference == mImportKeybox
             || preference == mClearKeybox) {
             SystemRestartUtils.showSystemRestartDialog(getContext());
             return true;
         }
         if (preference == mTensorFeaturesToggle) {
             boolean enabled = (Boolean) newValue;
             SystemProperties.set(SYS_ENABLE_TENSOR_FEATURES, enabled ? "true" : "false");
             SystemRestartUtils.showSystemRestartDialog(getContext());
             return true;
         }
         return false;
     }
 
     @Override
     public int getMetricsCategory() {
         return MetricsEvent.VOLTAGE;
     }
 
     public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
         new BaseSearchIndexProvider(R.xml.spoofing) {
 
             @Override
             public List<String> getNonIndexableKeys(Context context) {
                 List<String> keys = super.getNonIndexableKeys(context);
                 final Resources resources = context.getResources();
 
                 return keys;
             }
         };
 }
