/*
 * Copyright (C) 2024-2026 the risingOS Android Project
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

package com.android.internal.util.android;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Debug;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import java.util.Set;

/**
 * Utility to deep-hide Developer Options, ADB, and Debug APIs from selected apps.
 * @hide
 */
public final class HideDevUtils {
    public static final String HIDE_DEVELOPER_STATUS_ENABLED = "hide_developer_status_enabled";
    public static final String HIDE_DEVELOPER_STATUS_APPS = "hide_developer_status_apps";

    private static final String TAG = "HideDevUtils";
    private static volatile boolean sHideDevMode = false;

    /** @hide */
    public static boolean isHideDevMode() {
        return sHideDevMode;
    }

    /** @hide */
    public static boolean isDevSettingKey(String name) {
        if (name == null) return false;
        return name.equals("development_settings_enabled")
                || name.equals("adb_enabled")
                || name.equals("adb_wifi_enabled")
                || name.equals("adb_port")
                || name.equals("package_verifier_user_consent")
                || name.equals("verify_apps_over_usb")
                || name.equals("wait_for_debugger")
                || name.equals("mock_location")
                || name.equals("allow_mock_location")
                || name.equals("art_verifier_verify_debug_only")
                || name.equals("show_touches")
                || name.equals("pointer_location")
                || name.equals("debug_app")
                || name.equals("device_provisioned");
    }

    /** @hide */
    public static String getSpoofedSetting(String name) {
        if ("device_provisioned".equals(name)) {
            return "1";
        }
        if ("debug_app".equals(name)) {
            return "";
        }
        return "0";
    }

    public static void apply(Context context) {
        if (context == null || Process.isIsolated()) return;

        try {
            final String packageName = context.getPackageName();
            if (TextUtils.isEmpty(packageName)) return;

            if (packageName.equals("android")
                    || packageName.startsWith("com.android.settings")
                    || packageName.startsWith("com.android.systemui")
                    || packageName.startsWith("com.google.android.gms")) {
                return;
            }

            final ContentResolver cr = context.getContentResolver();
            final boolean enabled = Settings.Secure.getInt(cr, HIDE_DEVELOPER_STATUS_ENABLED, 0) == 1;
            if (!enabled) return;

            final String appsList = Settings.Secure.getString(cr, HIDE_DEVELOPER_STATUS_APPS);
            if (TextUtils.isEmpty(appsList)) return;

            final Set<String> targetPkgs = new ArraySet<>();
            for (String pkg : appsList.split(",")) {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty()) {
                    targetPkgs.add(trimmed);
                }
            }

            if (targetPkgs.contains(packageName)) {
                sHideDevMode = true;
                SystemProperties.setHideDevProps(true);
                Debug.setHideDevConnected(true);

                // Spoof Build flags
                PixelPropsUtils.setPropValue("TYPE", "user");
                PixelPropsUtils.setPropValue("TAGS", "release-keys");
                PixelPropsUtils.setPropValue("IS_DEBUGGABLE", false);
                PixelPropsUtils.setPropValue("IS_USERDEBUG", false);
                PixelPropsUtils.setPropValue("IS_USER", true);
                PixelPropsUtils.setPropValue("IS_ENG", false);

                final ApplicationInfo appInfo = context.getApplicationInfo();
                if (appInfo != null) {
                    appInfo.flags &= ~ApplicationInfo.FLAG_DEBUGGABLE;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to apply hide developer status", t);
        }
    }
}
