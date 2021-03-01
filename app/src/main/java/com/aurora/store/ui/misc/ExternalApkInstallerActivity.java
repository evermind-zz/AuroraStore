package com.aurora.store.ui.misc;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;

import com.aurora.store.R;
import com.aurora.store.model.App;
import com.aurora.store.service.updater.AccessUpdateService;
import com.aurora.store.util.Log;
import com.aurora.store.util.PathUtil;
import com.aurora.store.util.ViewUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ExternalApkInstallerActivity extends AppCompatActivity {

    private static final String SCHEME_PACKAGE = "package";
    private Disposable disposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        Uri packageUri = intent.getData();

        checkPackageUri(packageUri);

        Callable installerCallable = new Callable<App>() {
            @Override
            public App call() throws Exception {
                return getInstallPackage(packageUri);
            }
        };

        disposable = Observable.<App>fromCallable(installerCallable)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe( app -> askInstallDialog((App) app),
                        error ->
                                Log.e(error.toString())
                );
    }

    private void checkPackageUri(Uri packageUri) {
        if (packageUri != null && (packageUri.getScheme().equals(ContentResolver.SCHEME_FILE)
                || packageUri.getScheme().equals(ContentResolver.SCHEME_CONTENT))) {
            Log.d("whe have \"file\" or \"content\" scheme");
        } else if (packageUri != null && packageUri.getScheme().equals(
                ExternalApkInstallerActivity.SCHEME_PACKAGE)) {
            Log.d("whe have \"package\" scheme");
        } else if (packageUri != null && !"file".equals(packageUri.getScheme())) {
            throw new IllegalArgumentException("unexpected scheme " + packageUri.getScheme());
        }
    }

    private App getInstallPackage(Uri packageUri) throws IOException {
        App app = new App();
        String path = PathUtil.getPathForUri(this, packageUri);
        app.setLocalFilePathUri(path);

        ApkFile apkFile = new ApkFile(new File(path));

        ApkMeta apkMeta = apkFile.getApkMeta();
        app.setPackageName(apkMeta.getPackageName());
        app.setDisplayName(apkMeta.getLabel());
        app.setVersionCode(safeLongToInt(apkMeta.getVersionCode()));
        return app;
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

    @Override
    protected void onDestroy() {
        disposable.dispose();
        super.onDestroy();
    }
}
