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
import com.aurora.store.util.AppUtil;
import com.aurora.store.util.Log;
import com.aurora.store.util.PathUtil;
import com.aurora.store.util.ViewUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;

import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ExternalApkInstallerActivity extends AppCompatActivity {

    private static final String SCHEME_PACKAGE = "package";
    private Disposable disposable;
    boolean isCopyOfApkDueToNoDirectPathAvailable = false;
    List<String> pathsOfCopiedApks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        Callable installerCallable = new Callable<List<App>>() {
            @Override
            public List<App>call() throws Exception {
                return getInstallPackages(intent);
            }
        };

        disposable = Observable.<List<App>>fromCallable(installerCallable)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe( apps -> askInstallDialog((List<App>) apps),
                        error -> {
                            Log.e(error.toString());
                            cleanup();
                            finish();
                        }
                );
    }

    private final String checkPackageUri(Uri packageUri) {
        if (packageUri == null) {
            throw new IllegalArgumentException("unexpected: Uri was null.");
        }

        String schema = packageUri.getScheme();

        switch (schema) {
            case ContentResolver.SCHEME_FILE:
            case ContentResolver.SCHEME_CONTENT:
            case ExternalApkInstallerActivity.SCHEME_PACKAGE:
                break;
            default:
                throw new IllegalArgumentException("unexpected scheme " + schema);
        }

        Log.d("whe have \"" + schema + "\" scheme");
        return schema;
    }

    private boolean copyFile(Context context, Uri packageUri, File targetFile) {
        try (InputStream in = context.getContentResolver().openInputStream(packageUri)) {
            if (null == in) {
                return false;
            }
            try (OutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[1024 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException | SecurityException | IllegalStateException e) {
            Log.w("Error copying apk from content URI", e);
            return false;
        }
        return true;
    }

    private List<App> getInstallPackages(Intent intent) throws IOException {

        Uri packageUri = null;
        List<App> apps = new ArrayList<>();

        switch (intent.getAction()) {
            case Intent.ACTION_VIEW:
            case Intent.ACTION_INSTALL_PACKAGE:
                packageUri = intent.getData();
                apps.add(getAppForPackageUri(packageUri));
                break;
            case Intent.ACTION_SEND:
                packageUri = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
                apps.add(getAppForPackageUri(packageUri));
                break;
            case Intent.ACTION_SEND_MULTIPLE: // here we may handle split apk
                ArrayList<Uri> packageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                Iterator<Uri> listIterator = packageUris.iterator();

                while (listIterator.hasNext()) {
                    App result = getAppForPackageUri(listIterator.next());
                    if (null != result)
                        apps.add(result);

                }
                break;
            default:
        }

        return apps;
    }

    /**
     *
     * @param packageUri
     * @return  null will be returned if it is a split apk. We don't create a App object for them
     * @throws IOException
     */
    private App getAppForPackageUri(Uri packageUri) throws IOException {

        String path = null;
        App app = null;

        checkPackageUri(packageUri);

        try {
            path = PathUtil.getPathForUriWrapper(this, packageUri);
        } catch (Exception e){
            Log.e(e.toString());
        }

        if (path == null) { // could not extract path so create one taking inputStream from *Provider

            File apkDirectory = new File(PathUtil.getRootApkPath(this));
            File outputFile = File.createTempFile("appreciator", ".apk", apkDirectory);
            path = outputFile.getAbsolutePath();
            pathsOfCopiedApks.add(path);

            if (!copyFile(this, packageUri, outputFile)) {
                outputFile.delete();
                throw new RuntimeException("could not copy file: " + path);
            }
            isCopyOfApkDueToNoDirectPathAvailable = true;
        }

        ApkFile apkFile = new ApkFile(new File(path));
        ApkMeta apkMeta = apkFile.getApkMeta();
        apkFile.close();

        String packageName = apkMeta.getPackageName();
        String displayName = apkMeta.getLabel();
        String versionName = apkMeta.getVersionName();
        int versionCode = safeLongToInt(apkMeta.getVersionCode());
        String split = apkMeta.getSplit();


        if (isCopyOfApkDueToNoDirectPathAvailable) { // rename file here as we need the metadata
            File apkDirectory = new File(PathUtil.getRootApkPath(this));
            String newFilename =  PathUtil.getApkFileName(packageName, versionCode, split );
            File fileWithNewName = new File(apkDirectory, newFilename);
            File fileWithOldName = new File(path);
            fileWithOldName.renameTo(fileWithNewName);

            path = fileWithNewName.getAbsolutePath();
            pathsOfCopiedApks.add(path); // also store it here again as the path differs now
        }

        if (null == split) { // assume that this is the main apk and non split so we build a App object
            app = new App();
            app.setPackageName(packageName);
            app.setDisplayName(displayName);
            app.setVersionName(versionName);
            app.setVersionCode(versionCode);
            app.setLocalFilePathUri(path);
        }

        return app;
    }

    public int safeLongToInt(long number) {
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                    (number + " cannot be cast to int without changing its value.");
        }
        return (int) number;
    }

    private void askInstallDialog(List<App> apps) {

        ListIterator<App> listIterator = apps.listIterator();
        List<String> titles = new ArrayList<>();
        while (listIterator.hasNext()) {
            App app = listIterator.next();

            String versionString = AppUtil.getOldAndNewVersionsAsSingleString(this, app, app.getVersionName(), app.getVersionCode());
            if (null == versionString)
                versionString = AppUtil.getVersionString(app);
            titles.add(app.getDisplayName() + versionString);
        }

        // Process: com.aurora.store.legacy.testing, PID: 11379 java.lang.RuntimeException: Unable to start activity ComponentInfo{com.aurora.store.legacy.testing/com.aurora.store.ui.misc.ExternalApkInstallerActivity2}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
        // Getting above Exceptions if we use getApplicationContext() for the AlertDialog
        // so we use 'this'
        // more info see answer from A.K.: https://stackoverflow.com/questions/21814825/you-need-to-use-a-theme-appcompat-theme-or-descendant-with-this-activity
        Context context = this;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(StringUtils.join(titles,'\n'))
                .setMessage(context.getString(R.string.dialog_install_confirmation))
                .setPositiveButton(context.getString(android.R.string.ok), (dialog, which) -> {
                    ListIterator<App> listIterator2 = apps.listIterator();
                    while (listIterator2.hasNext()) {
                        App app = listIterator2.next();
                        AccessUpdateService.installAppOnly(getApplicationContext(), app);
                    }
                    finish();
                })
                .setNegativeButton(context.getString(android.R.string.cancel), (dialog, which) -> {
                    dialog.dismiss();
                    cleanup();
                    finish();
                });
        int backGroundColor = ViewUtil.getStyledAttribute(context, android.R.attr.colorBackground);
        builder.setBackground(new ColorDrawable(backGroundColor));
        // source: https://stackoverflow.com/questions/6120567/android-how-to-get-a-modal-dialog-or-similar-modal-behavior
        //         and more interesting stuff in the comments there
        builder.setCancelable(false);
        builder.create();
        builder.show();
    }

    private void cleanup() {
        if (isCopyOfApkDueToNoDirectPathAvailable) {
            if (null != pathsOfCopiedApks) {
                ListIterator<String> listIterator = pathsOfCopiedApks.listIterator();
                while (listIterator.hasNext()) {
                    File copiedApk = new File(listIterator.next());
                    copiedApk.delete();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        disposable.dispose();
        super.onDestroy();
    }
}
