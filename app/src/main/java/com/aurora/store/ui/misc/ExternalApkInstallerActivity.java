package com.aurora.store.ui.misc;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;

import com.aurora.store.R;
import com.aurora.store.model.App;
import com.aurora.store.service.updater.AccessUpdateService;
import com.aurora.store.util.Log;
import com.aurora.store.util.ViewUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import java.io.File;
import java.io.IOException;

import androidx.appcompat.app.AppCompatActivity;

public class ExternalApkInstallerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        Uri packageUri = intent.getData();

        String scheme = packageUri.getScheme();
        if (scheme != null && !"file".equals(scheme)) {
            throw new IllegalArgumentException("unexpected scheme " + scheme);
        }

        installPackage(packageUri);
    }

    private void installPackage(Uri packageUri) {
        App app = new App();
        app.setLocalFilePathUri(packageUri.getPath());

        try {
            ApkFile apkFile = new ApkFile(new File(packageUri.getPath()));

            ApkMeta apkMeta = apkFile.getApkMeta();
            app.setPackageName(apkMeta.getPackageName());
            app.setDisplayName(apkMeta.getLabel());
            app.setVersionCode(safeLongToInt(apkMeta.getVersionCode()));

            askInstallDialog(app);

        } catch (IOException e) {
            Log.e(e.getMessage());
        }
    }

    public int safeLongToInt(long number) {
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                    (number + " cannot be cast to int without changing its value.");
        }
        return (int) number;
    }

    private void askInstallDialog(App app) {
        // Process: com.aurora.store.legacy.testing, PID: 11379 java.lang.RuntimeException: Unable to start activity ComponentInfo{com.aurora.store.legacy.testing/com.aurora.store.ui.misc.ExternalApkInstallerActivity2}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
        // Getting above Exceptions if we use getApplicationContext() for the AlertDialog
        // so we use 'this'
        // more info see answer from A.K.: https://stackoverflow.com/questions/21814825/you-need-to-use-a-theme-appcompat-theme-or-descendant-with-this-activity
        Context context = this;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(app.getDisplayName())
                .setMessage(context.getString(R.string.dialog_install_confirmation))
                .setPositiveButton(context.getString(android.R.string.ok), (dialog, which) -> {
                    AccessUpdateService.installAppOnly(getApplicationContext(), app);
                    finish();
                })
                .setNegativeButton(context.getString(android.R.string.cancel), (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                });
        int backGroundColor = ViewUtil.getStyledAttribute(context, android.R.attr.colorBackground);
        builder.setBackground(new ColorDrawable(backGroundColor));
        builder.create();
        builder.show();
    }
}
