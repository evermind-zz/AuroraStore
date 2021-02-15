package com.aurora.store.service.updater.interfaces;

import android.app.Service;

import com.aurora.store.model.App;
import com.aurora.store.task.ObservableDeliveryData;

import java.util.List;

public abstract class IUpdateService extends Service {
    // call this always from the mainThread
    public abstract void installOnly(App app);
    public abstract void downloadAndInstallBundle(ObservableDeliveryData.DeliveryDataBundle bundle);
    public abstract void downloadAndInstall(List<App> appsToBeUpdated);
    public abstract void cancelUpdate(List<App> cancelUpdateApps);
}
