package com.aurora.store.service.updater;

import android.app.Service;
import android.content.Context;
import android.content.Intent;

import com.aurora.store.model.App;
import com.ivianuu.rxserviceconnection.RxServiceConnection;

import java.util.List;

import androidx.core.content.ContextCompat;
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import rx.functions.Func2;

public class AccessUpdateService {

    static public void downloadAndInstall(Context context, List<App> appsToBeUpdated) {
        connectServiceAndCallMethod(context, appsToBeUpdated, FuncDownloadAndInstall());
    }

    static public void cancelUpdate(Context context, List<App> appsToBeUpdated) {
        connectServiceAndCallMethod(context, appsToBeUpdated, FuncCancelUpdate());
    }

    static private Func2 FuncDownloadAndInstall() {
        return new Func2<UpdateService, List<App>, Void>() {
            @Override
            public Void call(UpdateService service, List<App> apps) {
                service.downloadAndInstall(apps);
                return null;
            }
        };
    }

    static private Func2 FuncCancelUpdate() {
        return new Func2<UpdateService, List<App>, Void>() {
            @Override
            public Void call(UpdateService service, List<App> apps) {
                service.cancelUpdate(apps);
                return null;
            }
        };
    }

    /**
     * This methods establish a ServiceConnection to the @see UpdateService and executes a function
     * and terminates the ServiceConnection afterwards.
     * @param context
     * @param apps the List of Apps you want to handle
     * @param func Implement what remote function should be called
     */
    static private void connectServiceAndCallMethod(Context context, List<App> apps, Func2 func) {
        startUpdateService(context, "", false);
        RxServiceConnection.bind(context, new Intent(context, UpdateService.class)) // bind the service
                .subscribe(new Observer<Service>() {
                    Disposable disposable;

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        disposable = d;
                    }

                    @Override
                    public void onNext(@NonNull Service service) {
                        UpdateService newUpdateService = (UpdateService) service;
                        func.call(service, apps);
                        disposable.dispose();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) { }
                    @Override
                    public void onComplete() { } });
    }

    /**
     *  never call this method from within the AuroraStore application
     *  they are just to be called via DebugActivity
     * @param context
     * @param input
     * @param testMode
     */
    static public void startUpdateService(Context context, String input, boolean testMode) {
        Intent serviceIntent = new Intent(context, UpdateService.class);
        serviceIntent.putExtra("inputExtra", input);

        if (testMode)
            serviceIntent.putExtra("testMode", true);

        ContextCompat.startForegroundService(context, serviceIntent);
    }

    /**
     *  never call this method from within the AuroraStore application
     *  they are just to be called via DebugActivity
     * @param context
     * @param input
     */
    static public void stopUpdateService(Context context, String input) {
        Intent serviceIntent = new Intent(context, UpdateService.class);
        serviceIntent.putExtra("inputExtra", input);
        context.stopService(serviceIntent);
    }
}
