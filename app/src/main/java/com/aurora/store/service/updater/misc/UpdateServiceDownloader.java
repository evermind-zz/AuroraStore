package com.aurora.store.service.updater.misc;

import android.content.Context;

import com.aurora.store.AuroraApplication;
import com.aurora.store.download.DownloadManager;
import com.aurora.store.events.Event;
import com.aurora.store.model.App;
import com.aurora.store.service.updater.interfaces.IUpdateService;
import com.aurora.store.service.updater.interfaces.IUpdateServiceDownloader;
import com.aurora.store.util.Log;
import com.aurora.store.util.Util;
import com.tonyodev.fetch2.AbstractFetchGroupListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchGroup;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2core.Func;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

public class UpdateServiceDownloader implements IUpdateServiceDownloader {

    private final IUpdateService updateService;
    /**
     * track all listener. So we can remove them in case one is not deleted
     */
    private List<FetchListener> trackListener;
    Context context;
    private Fetch fetch;
    private DownloadNotifications downloadsNotifications;

    public UpdateServiceDownloader(Context applicationContext, IUpdateService updateService) {
        context = applicationContext;
        this.updateService = updateService;
        trackListener = new ArrayList<>();

        fetch = DownloadManager.getFetchInstance(context);
        fetch.cancelAll();

        downloadsNotifications = new DownloadNotifications(context);
        addFetchListener(downloadsNotifications);
    }

    @Override
    public void enqueueAppsForDownload(List<Request> requestList, App app) {
        // cleanup previous items that may have not been downloaded successfully
        int hashCode = app.getPackageName().hashCode();
        fetch.cancelGroup(hashCode);
        fetch.removeGroup(hashCode);

        // add listener for this app update
        addFetchListener(new AbstractFetchGroupListener() {
            @Override
            public void onCompleted (int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup){
                super.onCompleted(groupId, download, fetchGroup);
                if (groupId == app.getPackageName().hashCode() && fetchGroup.getGroupDownloadProgress() == 100) {
                    if (Util.shouldAutoInstallApk(context)) {
                        //Call the installer
                        Log.i("Download ready 100% to install: groupId: " + app.getPackageName().hashCode()+ " name: " +  app.getPackageName());
                        updateService.installOnly(app);
                    }
                    removeFetchListener(this);
                }
            }

            @Override
            public void onError(@NotNull Download download, @NotNull Error error, @org.jetbrains.annotations.Nullable Throwable throwable) {
                super.onError(download, error, throwable);
                if (download.getGroup() == app.getPackageName().hashCode()) {
                    Log.e("Download error for: " + app.getPackageName() + ". Message: " + error + ". Download obj: " + download);
                    AuroraApplication.rxNotify(new Event(Event.SubType.DOWNLOAD, app.getPackageName(), Event.StatusType.FAILURE.ordinal()));
                    removeFetchListener(this);
                }
            }
        });

        fetch.enqueue(requestList,updatedRequestList ->
                Log.i("Updating -> %s",app.getDisplayName()));
    }

    @Override
    public void cancelAppDownload(String packageName) {
        int hashcode = packageName.hashCode();

        Func<List<Download>>  cancelCallbackSuccess = new Func<List<Download>>() {
            @Override
            public void call(@NotNull List<Download> result) {
                AuroraApplication.rxNotify(new Event(Event.SubType.DOWNLOAD, packageName, Event.StatusType.CANCELED.ordinal()));
            }
        };

        Func<Error> cancelCallbackFailure =  new Func<Error>() {
            @Override
            public void call(@NotNull Error result) {
                Log.d("cancel download for package: " + packageName + " failed. With Error: " + result);
                // nevertheless we say also here it was canceled to keep the system working
                AuroraApplication.rxNotify(new Event(Event.SubType.DOWNLOAD, packageName, Event.StatusType.CANCELED.ordinal()));
            }
        };

        fetch.cancelGroup(hashcode, cancelCallbackSuccess, cancelCallbackFailure);
        fetch.deleteGroup(hashcode);
    }

    @Override
    public void cancelAppsDownload(List<App> cancelApps) {
        Observable.fromIterable(cancelApps)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnNext(app -> {
                    cancelAppDownload(app.getPackageName());
                })
        .subscribe();
    }

    private void addFetchListener(FetchListener listener) {
        trackListener.add(listener);
        fetch.addListener(listener);
    }

    private void removeFetchListener(FetchListener listener) {
        trackListener.remove(listener);
        fetch.removeListener(listener);
    }

    private void removeRemainingFetchListeners() {
        for ( FetchListener listener : trackListener ) {
            Log.d("remove stale listener: " + listener);
            fetch.removeListener(listener);
        }
        trackListener.clear();
    }

    @Override
    public void onDestroy() {
        removeFetchListener(downloadsNotifications);
        removeRemainingFetchListeners();
    }
}
