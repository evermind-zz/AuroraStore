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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.aurora.store.AuroraApplication;
import com.aurora.store.Constants;
import com.aurora.store.R;
import com.aurora.store.events.Event;
import com.aurora.store.model.App;
import com.aurora.store.notification.QuickNotification;
import com.aurora.store.ui.details.DetailsActivity;
import com.aurora.store.util.Log;
import com.aurora.store.util.PathUtil;
import com.aurora.store.util.PrefUtil;
import com.aurora.store.util.TextUtil;
import com.aurora.store.util.Util;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Installer implements AppInstallerAbstract.InstallationStatusListener {

    public static final String NATIVE = "0"; // the same value have to be here: R.string.INSTALLER_NATIVE
    public static final String ROOT = "1"; // the same value have to be here: R.string.INSTALLER_ROOT
    public static final String SERVICES = "2"; // the same value have to be here: R.string.INSTALLER_SERVICES
    private Context context;
    private Map<String, App> appHashMap = new HashMap<>();
    private AppInstallerAbstract packageInstaller;
    private List<App> installationQueue = new ArrayList<>();

    private boolean isInstalling = false;
    private boolean isWaiting = false;

    public Installer(Context context) {
        this.context = context;
        packageInstaller = getInstallationMethod(context.getApplicationContext());
    }

    public AppInstallerAbstract getPackageInstaller() {
        return packageInstaller;
    }

    public void install(App app) {
        appHashMap.put(app.getPackageName(), app);
        installationQueue.add(app);

        if (isInstalling)
            isWaiting = true;
        else
            processApp(app);
    }

    public void install(String packageName, int versionCode) {
        // just wrap a new App instance for installing purpose only
        App app = new App();
        app.setPackageName(packageName);
        app.setVersionCode(versionCode);
        install(app);
    }

    private void processApp(App app) {
        final String packageName = app.getPackageName();
        final int versionCode = app.getVersionCode();
        isInstalling = true;
        installationQueue.remove(app);
        if (Util.isNativeInstallerEnforced(context)
                || ((Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) &! Util.isPrivilegedInstall(context) ))
            installNativeEnforced(packageName, versionCode);
        else
            installSplit(packageName, versionCode);
    }

    private void installNativeEnforced(String packageName, int versionCode) {
        Log.i("Native Installer Called");
        Intent intent;
        File file = new File(PathUtil.getLocalApkPath(context, packageName, versionCode));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(FileProvider.getUriForFile(context, "com.aurora.store.fileProvider", file));
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void installSplit(String packageName, int versionCode) {
        Log.i("Split Installer Called");
        List<File> apkFiles = new ArrayList<>();
        File apkDirectory = new File(PathUtil.getRootApkPath(context));
        for (File splitApk : apkDirectory.listFiles()) {
            if (splitApk.getPath().contains(new StringBuilder()
                    .append(packageName)
                    .append(".")
                    .append(versionCode))) {
                apkFiles.add(splitApk);
            }
        }

        packageInstaller.addInstallationStatusListener(this);
        AsyncTask.execute(() -> packageInstaller.installApkFiles(packageName, apkFiles));
    }

    private void checkAndProcessQueuedApps() {
        if (installationQueue.isEmpty()) {
            isWaiting = false;
            isInstalling = false;
        }

        if (isWaiting)
            processApp(installationQueue.get(0));
    }

    private void clearInstallationFiles(@NonNull App app) {
        boolean success = false;
        File apkDirectory = new File(PathUtil.getRootApkPath(context));
        for (File file : apkDirectory.listFiles()) {
            if (file.getName().contains(app.getPackageName() + "." + app.getVersionCode())) {
                success = file.delete();
            }
        }
        if (success)
            Log.i("Installation files deleted");
        else
            Log.i("Could not delete installation files");
    }

    private void clearNotification(App app) {
        if (app == null)
            return;

        final Object object = context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationManager notificationManager = (NotificationManager) object;
        if (notificationManager != null)
            notificationManager.cancel(app.getPackageName(), app.getPackageName().hashCode());
    }

    private void sendStatusBroadcast(String packageName, int status) {
        AuroraApplication.rxNotify(new Event(Event.SubType.INSTALLED, packageName, status));
    }

    private PendingIntent getContentIntent(String packageName) {
        Intent intent = new Intent(context, DetailsActivity.class);
        intent.putExtra(Constants.INTENT_PACKAGE_NAME, packageName);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private AppInstallerAbstract getInstallationMethod(Context context) {
        String prefValue = PrefUtil.getString(context, Constants.PREFERENCE_INSTALLATION_METHOD);
        switch (prefValue) {
            case Installer.ROOT:
                return AppInstallerRooted.getInstance(context);
            case Installer.SERVICES:
                return AppInstallerPrivileged.getInstance(context);
            default:
                return AppInstaller.getInstance(context);
        }
    }

    private String getStatusString(int status) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE:
                return context.getString(R.string.installer_status_failure);
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return context.getString(R.string.installer_status_failure_aborted);
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return context.getString(R.string.installer_status_failure_blocked);
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return context.getString(R.string.installer_status_failure_conflict);
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return context.getString(R.string.installer_status_failure_incompatible);
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return context.getString(R.string.installer_status_failure_invalid);
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return context.getString(R.string.installer_status_failure_storage);
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                return context.getString(R.string.installer_status_user_action);
            case PackageInstaller.STATUS_SUCCESS:
                return context.getString(R.string.installer_status_success);
            default:
                return context.getString(R.string.installer_status_unknown);
        }
    }

    @Override
    public void onStatusChanged(int status, @Nullable String intentPackageName) {
        final String statusMessage = getStatusString(status);
        final App app = appHashMap.get(intentPackageName);

        String displayName = (app != null)
                ? TextUtil.emptyIfNull(app.getDisplayName())
                : TextUtil.emptyIfNull(intentPackageName);

        if (StringUtils.isEmpty(displayName))
            displayName = context.getString(R.string.app_name);

        Log.i("Package Installer -> %s : %s", displayName, TextUtil.emptyIfNull(statusMessage));

        clearNotification(app);

        if (status == PackageInstaller.STATUS_SUCCESS) {
            sendStatusBroadcast(intentPackageName, 1);
            if (app != null && Util.shouldDeleteApk(context)) {
                clearInstallationFiles(app);
            }
        } else {
            sendStatusBroadcast(intentPackageName, 0);
        }

        QuickNotification.show(
                context,
                displayName,
                statusMessage,
                getContentIntent(intentPackageName));

        appHashMap.remove(intentPackageName);
        checkAndProcessQueuedApps();
    }
}
