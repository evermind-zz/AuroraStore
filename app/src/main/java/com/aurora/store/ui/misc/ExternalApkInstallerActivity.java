package com.aurora.store.ui.misc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.aurora.store.model.App;
import com.aurora.store.service.updater.AccessUpdateService;
import com.aurora.store.util.Log;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import java.io.File;
import java.io.IOException;

public class ExternalApkInstallerActivity extends Activity {

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

        finish();
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

            AccessUpdateService.installAppOnly(getApplicationContext(),app);
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
}
