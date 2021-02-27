package com.aurora.store.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.aurora.store.model.App;

public class AppUtil {
    /**
     * get a string with old and new version: 'old >> new'
     *
     * @param currentVersionName
     * @param currentVersionCode
     * @param updateVersionName
     * @param updateVersionCode
     * @return // will be null if there is no update available
     */
    public static String getOldAndNewVersionsAsSingleString(String currentVersionName, int currentVersionCode, String updateVersionName, int updateVersionCode) {
        String bothVersionsAsString = null;
        boolean updatable = false;

        if (currentVersionCode < updateVersionCode) {
            updatable = true;
        }

        if (updatable)
            bothVersionsAsString = new StringBuilder()
                    .append(currentVersionName)
                    .append("[")
                    .append(currentVersionCode)
                    .append("]")
                    .append(" >> ")
                    .append(updateVersionName)
                    .append("[")
                    .append(updateVersionCode)
                    .append("]")
                    .toString();

        return bothVersionsAsString;
    }

    /**
     * get a string with old and new version: 'old >> new'
     *
     * @param context
     * @param installedApp
     * @param updateVersionName
     * @param updateVersionCode
     * @return // will be null if there is no update available
     */
    public static String getOldAndNewVersionsAsSingleString(Context context, App installedApp, String updateVersionName, int updateVersionCode) {
        String stringWithBothVersions = null;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(installedApp.getPackageName(), 0);
            String currentVersion = info.versionName;
            int currentVersionCode = info.versionCode;

            stringWithBothVersions = AppUtil.getOldAndNewVersionsAsSingleString(currentVersion, currentVersionCode, updateVersionName, updateVersionCode);

        } catch (PackageManager.NameNotFoundException e) {
            // We've checked for that already
        }
        return stringWithBothVersions;
    }
}
