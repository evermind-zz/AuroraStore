package com.aurora.store.ui.misc;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import com.aurora.store.R;
import com.aurora.store.model.App;
import com.aurora.store.service.updater.AccessUpdateService;
import com.aurora.store.util.AppUtil;
import com.aurora.store.util.Log;
import com.aurora.store.util.ViewUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;

import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ExternalApkInstallerActivity extends AppCompatActivity {

    private Disposable disposable;
    AppObjectsFromIntentExtraData appObjectsFromIntentExtraData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        appObjectsFromIntentExtraData = new AppObjectsFromIntentExtraData(this, intent);

        Callable installerCallable = new Callable<List<App>>() {
            @Override
            public List<App>call() throws Exception {
                return appObjectsFromIntentExtraData.getInstallableApks();
            }
        };

        disposable = Observable.<List<App>>fromCallable(installerCallable)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe( apps -> askInstallDialog((List<App>) apps),
                        error -> {
                            Log.e(error.toString());
                            appObjectsFromIntentExtraData.cleanup();
                            finish();
                        }
                );
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
                    appObjectsFromIntentExtraData.cleanup();
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

    @Override
    protected void onDestroy() {
        disposable.dispose();
        super.onDestroy();
    }
}
