package com.aurora.store.installer;

import android.content.Context;

import com.aurora.store.util.ContextUtil;
import com.aurora.store.util.Log;
import com.aurora.store.util.Root;
import com.aurora.store.util.Util;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppInstallerRootedKitkat extends AppInstallerRooted{
    private static volatile AppInstallerRootedKitkat instance;

    private AppInstallerRootedKitkat(Context context) {
        super(context);
        instance = this;
    }

    public static AppInstallerRootedKitkat getInstance(Context context) {
        if (instance == null) {
            synchronized (AppInstallerRootedKitkat.class) {
                if (instance == null) {
                    instance = new AppInstallerRootedKitkat(context);
                    root = new Root();
                }
            }
        }
        return instance;
    }

    @Override
    protected String installCommands(String packageName, List<File> apkFiles) throws Exception {

        if (apkFiles.size() > 1) {
            String message =  "Split apk is not supported for KitKat: " + packageName;
            ContextUtil.toastLong(getContext(), message);
            throw new Exception(message);
        }

        // evermind: try to find a rare twice install the same package case.
        // https://stackoverflow.com/questions/7841232/java-android-how-to-print-out-a-full-stack-trace#7841448
        android.util.Log.d("TestExcAuroraROOTInst1", android.util.Log.getStackTraceString(new Exception()));
        String commitSessionResult = null;
        for (File apkFile : apkFiles)
            commitSessionResult = ensureCommandSucceeded(root.exec(String.format(Locale.getDefault(),
                "pm install -i com.android.vending -r -d \"%s\"",
                apkFile.getAbsolutePath() )));

        return commitSessionResult;
    }
}
