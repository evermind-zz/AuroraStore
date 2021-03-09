/*
 * Aurora Store
 * Copyright (C) 2019, Rahul Kumar Patel <whyorean@gmail.com>
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

package com.aurora.store.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import com.aurora.store.Constants;
import com.aurora.store.model.App;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class PathUtil {

    static public File getApkPath(String packageName, int version) {
        String filename = getApkFileName(packageName, version);
        return new File(getRootApkCopyPath(), filename);
    }

    static public String getApkFileName(String packageName, int version) {
        return packageName + "." + version + ".apk";
    }

    static public String getRootApkPath(Context context) {
        if (isCustomPath(context))
            return PrefUtil.getString(context, Constants.PREFERENCE_DOWNLOAD_DIRECTORY);
        else
            return getBaseDirectory(context);
    }

    static public String getRootApkCopyPath() {
        return getBaseCopyDirectory();
    }

    static public String getLocalApkPath(Context context, App app) {
        return getLocalApkPath(context, app.getPackageName(), app.getVersionCode());
    }

    static public String getLocalSplitPath(Context context, App app, String tag) {
        return getLocalSplitPath(context, app.getPackageName(), app.getVersionCode(), tag);
    }

    static public String getObbPath(App app, boolean main, boolean isGZipped) {
        return getObbPath(app.getPackageName(), app.getVersionCode(), main, isGZipped);
    }

    static public String getLocalApkPath(Context context, String packageName, int versionCode) {
        return getRootApkPath(context) + "/" + packageName + "." + versionCode + ".apk";
    }

    static private String getLocalSplitPath(Context context, String packageName, int versionCode, String tag) {
        return getRootApkPath(context) + "/" + packageName + "." + versionCode + "." + tag + ".apk";
    }

    static public String getObbPath(String packageName, int version, boolean main, boolean isGZipped) {
        String obbDir = Environment.getExternalStorageDirectory() + "/Android/obb/" + packageName;
        String ext = isGZipped ? ".gzip" : ".obb";
        String filename = (main ? "/main" : "/patch") + "." + version + "." + packageName + ext;
        return obbDir + filename;
    }

    static public boolean isCustomPath(Context context) {
        return (!getCustomPath(context).isEmpty());
    }

    static public String getCustomPath(Context context) {
        return PrefUtil.getString(context, Constants.PREFERENCE_DOWNLOAD_DIRECTORY);
    }

    public static boolean checkBaseDirectory(Context context) {
        boolean success = new File(getRootApkPath(context)).exists();
        return success || createBaseDirectory(context);
    }

    public static boolean createBaseDirectory(Context context) {
        return new File(getRootApkPath(context)).mkdir();
    }

    static public String getBaseDirectory(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Util.isRootInstallEnabled(context)) {
            return context.getFilesDir().getPath();
        } else
            return Environment.getExternalStorageDirectory().getPath() + "/Aurora";
    }

    static public String getExtBaseDirectory(Context context) {
        return Environment.getExternalStorageDirectory().getPath() + "/Aurora";
    }

    static public String getBaseCopyDirectory() {
        return Environment.getExternalStorageDirectory().getPath() + "/Aurora/Copy/APK";
    }

    public static boolean fileExists(Context context, App app) {
        return new File(PathUtil.getLocalApkPath(context, app)).exists();
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    public static String getPathForUri(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static String getPathForUriWrapper(final Context context, final Uri uri) {
        try {
            return getPathForUri(context, uri);
        } catch (Exception e) {
            Log.d(e.toString());
        }

        return testPathSegmentsAndGetValidPathIfPossible(uri);
    }

    /**
     *
     * @param packageUri
     * @return null if no valid path was found
     */
   private static String testPathSegmentsAndGetValidPathIfPossible(Uri packageUri) {

       List<String> listCopy = new ArrayList<>();
       List<String> pathSegments = packageUri.getPathSegments();

       ListIterator<String> listIterator = pathSegments.listIterator();
       while(listIterator.hasNext()) {
           listCopy.add(listIterator.next());
       }

       // reduce the path each time trying to get a match.
       while (listCopy.size() > 1) { // > 1 because otherwise we have nothing after remove(0) is called
           listCopy.remove(0);
           String tempPath = "/" + StringUtils.join(listCopy, "/");

           if (tempPath != null) {
               File file = new File(tempPath);
               if (file.exists())
                   return tempPath;
           }
       }
       return null;
   }

    public String getImagePath(Context context, Uri uri){
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        String document_id = cursor.getString(0);
        document_id = document_id.substring(document_id.lastIndexOf(":")+1);
        cursor.close();

        cursor = context.getContentResolver().query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null, MediaStore.Images.Media._ID + " = ? ", new String[]{document_id}, null);
        cursor.moveToFirst();
        String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
        cursor.close();

        return path;
    }


    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private static String getDataColumn(Context context, Uri uri, String selection,
                                        String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
