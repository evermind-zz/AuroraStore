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

package com.aurora.store.ui.details.views;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.aurora.store.AuroraApplication;
import com.aurora.store.R;
import com.aurora.store.download.DownloadManager;
import com.aurora.store.download.RequestBuilder;
import com.aurora.store.exception.AppNotFoundException;
import com.aurora.store.exception.NotPurchasedException;
import com.aurora.store.installer.Uninstaller;
import com.aurora.store.model.App;
import com.aurora.store.task.DeliveryData;
import com.aurora.store.ui.details.DetailsActivity;
import com.aurora.store.util.Accountant;
import com.aurora.store.util.ContextUtil;
import com.aurora.store.util.Log;
import com.aurora.store.util.PackageUtil;
import com.aurora.store.util.PathUtil;
import com.aurora.store.util.Util;
import com.aurora.store.util.ViewUtil;
import com.dragons.aurora.playstoreapiv2.AndroidAppDeliveryData;
import com.google.android.material.button.MaterialButton;
import com.tonyodev.fetch2.AbstractFetchGroupListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchGroup;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2core.DownloadBlock;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class ActionButton extends AbstractDetails {

    @BindView(R.id.btn_positive)
    MaterialButton btnPositive;
    @BindView(R.id.btn_negative)
    MaterialButton btnNegative;
    @BindView(R.id.view_switcher_action)
    ViewSwitcher viewSwitcher;
    @BindView(R.id.view1)
    LinearLayout actions_layout;
    @BindView(R.id.view2)
    LinearLayout progress_layout;
    @BindView(R.id.progress_download)
    ProgressBar progressBar;
    @BindView(R.id.progress_txt)
    TextView progressTxt;
    @BindView(R.id.progress_status)
    TextView progressStatus;
    @BindView(R.id.btn_cancel)
    ImageButton btnCancel;

    private boolean isPaused = false;
    private int hashCode;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Fetch fetch;
    private FetchListener fetchListener;
    private int progress = 0;

    public ActionButton(DetailsActivity activity, App app) {
        super(activity, app);
    }

    @Override
    public void draw() {
        boolean isInstalled = PackageUtil.isInstalled(context, app);
        hashCode = app.getPackageName().hashCode();
        ViewUtil.setVisibility(btnNegative, isInstalled);
        btnNegative.setOnClickListener(uninstallAppListener());
        btnPositive.setOnClickListener(downloadAppListener());
        btnCancel.setOnClickListener(cancelDownloadListener());

        if (!app.isFree()) {
            checkPurchased();
        }

        if (isInstalled)
            runOrUpdate();

        setupFetch();
    }

    private void setupFetch() {
        fetch = DownloadManager.getFetchInstance(context);
        fetch.getFetchGroup(hashCode, fetchGroup -> {
            if (fetchGroup.getGroupDownloadProgress() == 100) {
                if (!app.isInstalled() && PathUtil.fileExists(context, app))
                    btnPositive.setOnClickListener(installAppListener());
            } else if (fetchGroup.getDownloadingDownloads().size() > 0) {
                switchViews(true);
                fetchListener = getFetchListener();
                fetch.addListener(fetchListener);
            } else if (fetchGroup.getPausedDownloads().size() > 0) {
                isPaused = true;
                btnPositive.setOnClickListener(resumeAppListener());
            }
        });
    }

    private void switchViews(boolean showDownloads) {
        if (viewSwitcher.getCurrentView() == actions_layout && showDownloads)
            viewSwitcher.showNext();
        else if (viewSwitcher.getCurrentView() == progress_layout && !showDownloads)
            viewSwitcher.showPrevious();
    }

    private void runOrUpdate() {
        String versionName = app.getVersionName();
        if (TextUtils.isEmpty(versionName)) {
            return;
        }
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            String currentVersion = info.versionName;
            btnPositive.setText(R.string.details_update);

            if (info.versionCode >= app.getVersionCode() || null == currentVersion) {
                btnPositive.setText(R.string.details_run);
                btnPositive.setOnClickListener(openAppListener());
                btnPositive.setVisibility(PackageUtil.isPackageLaunchable(context, app.getPackageName())
                        ? View.VISIBLE
                        : View.GONE);
            } else if (new File(PathUtil.getLocalApkPath(context, app.getPackageName(),
                    app.getVersionCode())).exists()) {
                btnPositive.setOnClickListener(installAppListener());
                btnPositive.setVisibility(View.VISIBLE);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    private View.OnClickListener uninstallAppListener() {
        return v -> new Uninstaller(context).uninstall(app);
    }

    private View.OnClickListener installAppListener() {
        btnPositive.setText(R.string.details_install);
        return v -> {
            btnPositive.setText(R.string.details_installing);
            btnPositive.setEnabled(false);
            //notification.notifyInstalling();
            AuroraApplication.getInstaller().install(app);
        };
    }

    private View.OnClickListener downloadAppListener() {
        if (Util.shouldAutoInstallApk(context))
            btnPositive.setText(R.string.details_install);
        else
            btnPositive.setText(R.string.details_download);
        btnPositive.setVisibility(View.VISIBLE);
        btnPositive.setEnabled(true);
        return v -> {
            switchViews(true);
            //Remove any previous requests
            if (!isPaused) {
                fetch.deleteGroup(hashCode);
            }

            Observable.fromCallable(() -> new DeliveryData(context)
                    .getDeliveryData(app))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext(androidAppDeliveryData -> {
                        initiateDownload(androidAppDeliveryData);
                    })
                    .doOnError(throwable -> {
                        ContextUtil.runOnUiThread(() -> {
                            if (throwable instanceof NotPurchasedException) {
                                Log.d("%s not purchased", app.getDisplayName());
                                showPurchaseDialog();
                            }
                            if (throwable instanceof AppNotFoundException) {
                                Log.d("%s not not found", app.getDisplayName());
                                showDialog(R.string.dialog_unavailable_title, R.string.dialog_unavailable_desc);
                            }
                            if (throwable instanceof NullPointerException) {
                                if (App.Restriction.RESTRICTED_GEO == app.getRestriction())
                                    showDialog(R.string.dialog_geores_title, R.string.dialog_geores_desc);
                                if (App.Restriction.INCOMPATIBLE_DEVICE == app.getRestriction())

                                    showDialog(R.string.dialog_incompat_title, R.string.dialog_incompat_desc);
                            }
                            draw();
                            switchViews(false);
                        });
                    })
                    .subscribe();
        };
    }

    private void checkPurchased() {
        if (Accountant.isAnonymous(context)) {
            btnPositive.setText(R.string.action_disabled);
            btnPositive.setEnabled(false);
        } else
            Observable.fromCallable(() -> new DeliveryData(context)
                    .getDeliveryData(app))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext(androidAppDeliveryData -> {
                        if (androidAppDeliveryData.hasDownloadUrl())
                            btnPositive.setText(R.string.details_install);
                        else
                            btnPositive.setText(R.string.details_purchase);
                    })
                    .doOnError(throwable -> {
                        btnPositive.setText(R.string.details_purchase);
                    })
                    .subscribe();
    }

    private View.OnClickListener resumeAppListener() {
        fetchListener = getFetchListener();
        fetch.addListener(fetchListener);
        btnPositive.setText(R.string.download_resume);
        return v -> {
            switchViews(true);
            fetch.resumeGroup(hashCode);
        };
    }

    private View.OnClickListener openAppListener() {
        btnPositive.setText(R.string.details_run);
        return v -> {
            Intent i = getLaunchIntent();
            if (null != i) {
                try {
                    context.startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Log.e(e.getMessage());
                }
            }
        };
    }

    private View.OnClickListener cancelDownloadListener() {
        return v -> {
            fetch.cancelGroup(hashCode);
            switchViews(false);
        };
    }

    @SuppressLint("NewApi")
    private Intent getLaunchIntent() {
        Intent mIntent = context.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        boolean isTv = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isTv();
        if (isTv) {
            Intent l = context.getPackageManager()
                    .getLeanbackLaunchIntentForPackage(app.getPackageName());
            if (null != l) {
                mIntent = l;
            }
        }
        if (mIntent == null) {
            return null;
        }
        mIntent.addCategory(isTv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        return mIntent;
    }

    private boolean isTv() {
        int uiMode = context.getResources().getConfiguration().uiMode;
        return (uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private void initiateDownload(AndroidAppDeliveryData deliveryData) {
        final Request request = RequestBuilder.buildRequest(context, app, deliveryData.getDownloadUrl());
        final List<Request> splitList = RequestBuilder.buildSplitRequestList(context, app, deliveryData);
        final List<Request> obbList = RequestBuilder.buildObbRequestList(context, app, deliveryData);

        final List<Request> requestList = new ArrayList<>();
        requestList.add(request);
        requestList.addAll(splitList);
        requestList.addAll(obbList);

        fetchListener = getFetchListener();
        fetch.addListener(fetchListener);
        fetch.enqueue(requestList, updatedRequestList ->
                Log.i("Downloading Splits : %s", app.getPackageName()));
    }

    private FetchListener getFetchListener() {
        return new AbstractFetchGroupListener() {

            @Override
            public void onQueued(int groupId, @NotNull Download download, boolean waitingNetwork, @NotNull FetchGroup fetchGroup) {
                if (groupId == hashCode) {
                    ContextUtil.runOnUiThread(() -> {
                        progressBar.setIndeterminate(true);
                        progressStatus.setText(R.string.download_queued);
                    });
                }
            }

            @Override
            public void onStarted(int groupId, @NotNull Download download, @NotNull List<? extends DownloadBlock> downloadBlocks, int totalBlocks, @NotNull FetchGroup fetchGroup) {
                if (groupId == hashCode) {
                    ContextUtil.runOnUiThread(() -> {
                        progressBar.setIndeterminate(true);
                        progressStatus.setText(R.string.download_waiting);
                        switchViews(true);
                    });
                }
            }

            @Override
            public void onResumed(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                if (groupId == hashCode) {
                    ContextUtil.runOnUiThread(() -> {
                        progressStatus.setText(R.string.download_progress);
                        progressBar.setIndeterminate(false);
                    });
                }
            }

            @Override
            public void onProgress(int groupId, @NotNull Download download, long etaInMilliSeconds, long downloadedBytesPerSecond, @NotNull FetchGroup fetchGroup) {
                if (groupId == hashCode) {
                    progress = fetchGroup.getGroupDownloadProgress();
                    if (progress < 0) progress = 0;
                    ContextUtil.runOnUiThread(() -> {
                        btnCancel.setVisibility(View.VISIBLE);
                        //Set intermediate to false, just in case xD
                        if (progressBar.isIndeterminate())
                            progressBar.setIndeterminate(false);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            progressBar.setProgress(progress, true);
                        } else
                            progressBar.setProgress(progress);
                        progressStatus.setText(R.string.download_progress);
                        progressTxt.setText(new StringBuilder().append(progress).append("%"));
                    });
                }
            }

            @Override
            public void onPaused(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                if (groupId == hashCode) {
                    ContextUtil.runOnUiThread(() -> {
                        switchViews(false);
                        progressStatus.setText(R.string.download_paused);
                    });
                }
            }

            @Override
            public void onCompleted(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                if (groupId == hashCode && fetchGroup.getGroupDownloadProgress() == 100) {
                    ContextUtil.runOnUiThread(() -> {
                        switchViews(false);
                        progressStatus.setText(R.string.download_completed);
                        btnPositive.setOnClickListener(installAppListener());
                    });

                    if (Util.shouldAutoInstallApk(context)) {
                        ContextUtil.runOnUiThread(() -> {
                            btnPositive.setText(R.string.details_installing);
                            btnPositive.setEnabled(false);
                        });
                        //Call the installer
                        AuroraApplication.getInstaller().install(app);
                    }
                    fetch.removeListener(this);
                }
            }

            @Override
            public void onCancelled(int groupId, @NotNull Download download,
                                    @NotNull FetchGroup fetchGroup) {
                if (groupId == hashCode) {
                    ContextUtil.runOnUiThread(() -> {
                        switchViews(false);
                        progressBar.setIndeterminate(true);
                        progressStatus.setText(R.string.download_canceled);
                    });
                }
            }
        };
    }
}
