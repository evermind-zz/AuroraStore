package com.aurora.store.ui.misc;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.aurora.store.model.App;
import com.aurora.store.util.Log;
import com.aurora.store.util.PathUtil;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class AppObjectsFromIntentExtraData {

    private static final String SCHEME_PACKAGE = "package";
    boolean isCopyOfApkDueToNoDirectPathAvailable = false;
    List<String> pathsOfCopiedApks = new ArrayList<>();
    private final Intent intent;
    Context context;

    AppObjectsFromIntentExtraData(Context context, Intent intent) {
        this.context = context;
        this.intent = intent;
    }

    public List<App> getInstallableApks()throws IOException {
        return getInstallPackages(intent);
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
            path = PathUtil.getPathForUriWrapper(context, packageUri);
        } catch (Exception e){
            Log.e(e.toString());
        }

        if (path == null) { // could not extract path so create one taking inputStream from *Provider

            File apkDirectory = new File(PathUtil.getRootApkPath(context));
            File outputFile = File.createTempFile("appreciator", ".apk", apkDirectory);
            path = outputFile.getAbsolutePath();
            pathsOfCopiedApks.add(path);

            if (!copyFile(context, packageUri, outputFile)) {
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
            File apkDirectory = new File(PathUtil.getRootApkPath(context));
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

    private int safeLongToInt(long number) {
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                    (number + " cannot be cast to int without changing its value.");
        }
        return (int) number;
    }

    private final String checkPackageUri(Uri packageUri) {
        if (packageUri == null) {
            throw new IllegalArgumentException("unexpected: Uri was null.");
        }

        String schema = packageUri.getScheme();

        switch (schema) {
            case ContentResolver.SCHEME_FILE:
            case ContentResolver.SCHEME_CONTENT:
            case AppObjectsFromIntentExtraData.SCHEME_PACKAGE:
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

    /**
     * call this only if something went wrong
     */
    public void cleanup() {
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
}
