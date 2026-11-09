/*
 * Copyright (C) 2019 The PixelExperience Project
 * Copyright (C) 2023-2026 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import android.os.Binder;
import android.os.UserHandle;
import android.provider.Settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for hiding Developer Options and ADB debugging status from selected applications.
 *
 * @hide
 */
public final class HideDeveloperStatusUtils {

    private static final Set<String> SETTINGS_TO_HIDE = new HashSet<>(Arrays.asList(
            Settings.Global.ADB_ENABLED,
            Settings.Global.ADB_WIFI_ENABLED,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
    ));

    private static final Set<String> PROTECTED_PACKAGES = new HashSet<>(Arrays.asList(
            "android",
            "com.android.settings",
            "com.android.systemui",
            "com.android.shell"
    ));

    private HideDeveloperStatusUtils() {}

    public static boolean isProtectedPackage(String packageName) {
        return packageName != null && PROTECTED_PACKAGES.contains(packageName);
    }

    public static boolean isHideableSetting(String name) {
        return name != null && SETTINGS_TO_HIDE.contains(name);
    }

    public static Set<String> getSettingKeysToHide() {
        return Collections.unmodifiableSet(SETTINGS_TO_HIDE);
    }

    public static boolean shouldHideDevStatus(ContentResolver cr, String packageName, String name) {
        if (cr == null) return false;
        return shouldHideDevStatus(cr, packageName, name, cr.getUserId());
    }

    public static boolean shouldHideDevStatus(
            ContentResolver cr, String packageName, String name, int userId) {
        if (cr == null || packageName == null || name == null) {
            return false;
        }
        if (!SETTINGS_TO_HIDE.contains(name) || PROTECTED_PACKAGES.contains(packageName)) {
            return false;
        }
        final int resolvedUserId = (userId == UserHandle.USER_CURRENT)
                ? UserHandle.myUserId() : userId;
        final long token = Settings.isInSystemServer() ? Binder.clearCallingIdentity() : -1L;
        try {
            boolean enabled = Settings.Secure.getIntForUser(
                    cr, Settings.Secure.HIDE_DEVELOPER_STATUS_ENABLED, 0, resolvedUserId) == 1;
            if (!enabled) {
                return false;
            }
            Set<String> apps = getApps(cr, resolvedUserId);
            return apps.contains(packageName);
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (token != -1L) {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    public static Set<String> getApps(Context context) {
        if (context == null) return Collections.emptySet();
        return getApps(context.getContentResolver(), UserHandle.myUserId());
    }

    public static Set<String> getApps(ContentResolver cr, int userId) {
        if (cr == null) return Collections.emptySet();
        String raw = Settings.Secure.getStringForUser(
                cr, Settings.Secure.HIDE_DEVELOPER_STATUS, userId);
        if (raw == null || raw.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public static void setApps(Context context, Set<String> apps) {
        if (context == null) return;
        String value = (apps == null || apps.isEmpty()) ? "" : String.join(",", apps);
        Settings.Secure.putStringForUser(
                context.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS,
                value,
                UserHandle.myUserId());
    }

    public static void addApp(Context context, String packageName) {
        if (context == null || packageName == null || isProtectedPackage(packageName)) return;
        Set<String> apps = new HashSet<>(getApps(context));
        if (apps.add(packageName)) {
            setApps(context, apps);
        }
    }

    public static void removeApp(Context context, String packageName) {
        if (context == null || packageName == null) return;
        Set<String> apps = new HashSet<>(getApps(context));
        if (apps.remove(packageName)) {
            setApps(context, apps);
        }
    }
}
