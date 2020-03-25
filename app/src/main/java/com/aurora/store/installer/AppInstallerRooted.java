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

public class AppInstallerRooted extends AppInstallerAbstract {

    private static volatile AppInstallerRooted instance;
    private static Root root;

    private AppInstallerRooted(Context context) {
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

    @Override
    protected void installApkFiles(String packageName, List<File> apkFiles) {
        try {
            if (root.isTerminated() || !root.isAcquired()) {
                Root.requestRoot();
                if (!root.isAcquired()) {
                    ContextUtil.toastLong(getContext(), "Root access not available");
                    dispatchSessionUpdate(PackageInstaller.STATUS_FAILURE, packageName);
                    return;
                }
            }

            int totalSize = 0;
            for (File apkFile : apkFiles)
                totalSize += apkFile.length();

            if (totalSize < 0) {

                final String createSessionResult = ensureCommandSucceeded(root.exec(String.format(Locale.getDefault(),
                        "pm install-create -i com.android.vending --user %s -r -S %d",
                        Util.getInstallationProfile(getContext()),
                        totalSize)));

                final Pattern sessionIdPattern = Pattern.compile("(\\d+)");
                final Matcher sessionIdMatcher = sessionIdPattern.matcher(createSessionResult);
                final boolean found = sessionIdMatcher.find();
                final int sessionId = Integer.parseInt(sessionIdMatcher.group(1));

                boolean corruptedApkDetected = false;

                for (File apkFile : apkFiles) {
                    if (apkFile.length() < 0)
                        corruptedApkDetected = true;
                }

                if (!corruptedApkDetected) {
                    for (File apkFile : apkFiles) {
                        ensureCommandSucceeded(root.exec(String.format(Locale.getDefault(),
                                "cat \"%s\" | pm install-write -S %d %d \"%s\"",
                                apkFile.getAbsolutePath(),
                                apkFile.length(),
                                sessionId,
                                apkFile.getName())));
                    }

                    final String commitSessionResult = ensureCommandSucceeded(root.exec(String.format(Locale.getDefault(),
                            "pm install-commit %d ",
                            sessionId)));

                    if (commitSessionResult.toLowerCase().contains("success"))
                        dispatchSessionUpdate(PackageInstaller.STATUS_SUCCESS, packageName);
                    else
                        throw new Exception("Failed to install APK(s)");
                } else
                    throw new Exception("Corrupted APK(s) detected, aborting install.");
            } else
                throw new Exception("Corrupted APK(s) detected, aborting install.");

        } catch (Exception e) {
            Log.e(e.getMessage());
            dispatchSessionUpdate(PackageInstaller.STATUS_FAILURE, packageName);
        }
    }

    private String ensureCommandSucceeded(String result) throws Exception {
        if (result == null || result.length() == 0)
            throw new Exception(root.readError());
        return result;
    }
}
