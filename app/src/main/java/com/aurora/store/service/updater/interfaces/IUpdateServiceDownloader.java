package com.aurora.store.service.updater.interfaces;

import com.aurora.store.model.App;
import com.tonyodev.fetch2.Request;

import java.util.List;

public interface IUpdateServiceDownloader {
    void enqueueAppsForDownload(List<Request> requestList, App app);
    void cancelAppsDownload(List<App> cancelApps);
    void cancelAppDownload(String packageName);

    /**
     * cleanup all the stuff you want to free
     */
    void onDestroy();
}
