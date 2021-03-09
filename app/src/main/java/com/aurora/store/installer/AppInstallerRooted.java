/*
 * Aurora Store
 * Copyright (C) 2019, Rahul Kumar Patel <whyorean@gmail.com>
 *
 * Split APK Installer (SAI)
 * Copyright (C) 2018, Aefyr
 *
 * Aurora Store is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Aurora Store is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package com.aurora.store.installer;

import android.content.Context;
import android.content.pm.PackageInstaller;

import com.aurora.store.util.ContextUtil;
import com.aurora.store.util.Log;
import com.aurora.store.util.Root;
import com.aurora.store.util.Util;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppInstallerRooted extends AppInstallerAbstract implements AppUnInstallerCommon{

    private static volatile AppInstallerRooted instance;
    protected static Root root;

    protected AppInstallerRooted(Context context) {
        super(context);
        instance = this;
    }

    public static AppInstallerRooted getInstance(Context context) {
        if (instance == null) {
            synchronized (AppInstallerRooted.class) {
                if (instance == null) {
                    instance = new AppInstallerRooted(context);
                    root = new Root();
                }
            }
        }
        return instance;
    }

    private boolean doWeGetRootAccess(String packageName) {
        boolean retvalue = true;
        if (root.isTerminated() || !root.isAcquired()) {
            // recreate root access object and check if successful
            root = new Root();
            if (!root.isAcquired()) {
                ContextUtil.toastLong(getContext(), "Root access not available");
                dispatchSessionUpdate(PackageInstaller.STATUS_FAILURE, packageName);
                retvalue = false;
            }
        }
        return retvalue;
    }

    protected String installCommands(String packageName, List<File> apkFiles) throws Exception {
        int totalSize = 0;
        for (File apkFile : apkFiles)
            totalSize += apkFile.length();

        final String createSessionResult = ensureCommandSucceeded(root,root.exec(String.format(Locale.getDefault(),
                "pm install-create -i com.android.vending --user %s -r -S %d",
                Util.getInstallationProfile(getContext()),
                totalSize)));

        final Pattern sessionIdPattern = Pattern.compile("(\\d+)");
        final Matcher sessionIdMatcher = sessionIdPattern.matcher(createSessionResult);
        boolean found = sessionIdMatcher.find();
        int sessionId = Integer.parseInt(sessionIdMatcher.group(1));

        // evermind: try to find a rare twice install the same package case.
        // https://stackoverflow.com/questions/7841232/java-android-how-to-print-out-a-full-stack-trace#7841448
        android.util.Log.d("TestExcAuroraROOTInst1", android.util.Log.getStackTraceString(new Exception()));
        for (File apkFile : apkFiles)
            ensureCommandSucceeded(root,root.exec(String.format(Locale.getDefault(),
                    "cat \"%s\" | pm install-write -S %d %d \"%s\"",
                    apkFile.getAbsolutePath(),
                    apkFile.length(),
                    sessionId,
                    apkFile.getName())));

        String commitSessionResult = ensureCommandSucceeded(root,root.exec(String.format(Locale.getDefault(),
                "pm install-commit %d",
                sessionId)));

        return commitSessionResult;
    }

    @Override
    protected void installApkFiles(String packageName, List<File> apkFiles) {
        try {
            if (!doWeGetRootAccess(packageName))
                return;

            String commitSessionResult = installCommands(packageName, apkFiles);

            if (commitSessionResult.toLowerCase().contains("success"))
                dispatchSessionUpdate(PackageInstaller.STATUS_SUCCESS, packageName);
            else
                handleError(packageName, commitSessionResult);
        } catch (Exception e) {
            Log.w(e.getMessage());

            // evermind: try to find a rare twice install the same package case.
            if (null != e.getCause()) {
                if (null != e.getCause().getCause()) {
                    android.util.Log.d("TestExcAuroraROOTInst2", android.util.Log.getStackTraceString(e.getCause().getCause()));
                } else {
                    android.util.Log.d("TestExcAuroraROOTInst3", android.util.Log.getStackTraceString(e.getCause()));
                }
            }

            handleError(packageName, e.getMessage());
        }
    }

    private void handleError(String packageName, String errorMessage) {

        // java.lang.RuntimeException: Failure [INSTALL_FAILED_CONTAINER_ERROR: Failed to extract native libraries, res=-18]
        if (errorMessage.contains("INSTALL_FAILED_CONTAINER_ERROR")
                // java.lang.RuntimeException: Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]
                || errorMessage.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE")
                // java.lang.RuntimeException: Error: java.lang.IllegalStateException: ☃Requested internal only, but not enough space
                || errorMessage.contains("Requested internal only, but not enough space")) {
            dispatchSessionUpdate(PackageInstaller.STATUS_FAILURE_STORAGE, packageName);
        } else if (errorMessage.contains("INSTALL_FAILED_VERSION_DOWNGRADE")) {
            dispatchSessionUpdate(Installer.STATUS_FAILURE_DOWNGRADE, packageName);
        } else if (errorMessage.contains("INSTALL_FAILED_VERIFICATION_FAILURE")) {
            dispatchSessionUpdate(Installer.STATUS_FAILURE_VERIFICATION, packageName);
        } else if (errorMessage.contains("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES")
                || (errorMessage.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE"))) {
            dispatchSessionUpdate(PackageInstaller.STATUS_FAILURE_CONFLICT, packageName);
        } else {
            dispatchSessionUpdate(PackageInstaller.STATUS_FAILURE, packageName);
        }
    }
}
