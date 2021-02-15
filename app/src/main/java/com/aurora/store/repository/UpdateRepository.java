package com.aurora.store.repository;

import android.content.Context;

import com.aurora.store.AuroraApplication;
import com.aurora.store.model.App;
import com.aurora.store.task.UpdatableAppsTask;
import com.dragons.aurora.playstoreapiv2.GooglePlayAPI;

import java.io.IOException;
import java.util.List;

import io.reactivex.Observable;
import rx.functions.Func0;

public class UpdateRepository {
    private static UpdateRepository mInstance;
    List<App> updatableAppList;

    public static synchronized UpdateRepository getInstance()
    {
        if (mInstance == null) {
            mInstance = new UpdateRepository();
        };
        return mInstance;
    }

    private List<App> getUpdatableAppList(Context context, boolean doUpdate) throws Exception
    {
        if (doUpdate || updatableAppList == null)
        {
            GooglePlayAPI api = AuroraApplication.api;
                updatableAppList = new UpdatableAppsTask(api, context)
                        .getUpdatableApps();
        }

        return updatableAppList;
    }

    public Observable<List<App>> fetchUpdatableApps(Context context, boolean doUpdate) {
        return Observable.defer(new Func0<Observable<List<App>>>() {
            @Override
            public Observable<List<App>> call() {
                try {
                    return Observable.just(getUpdatableAppList(context,doUpdate));
                } catch (IOException e) {
                    return Observable.error(e);
                } catch (Exception e) {
                    return Observable.error(e);
                }
            }
        });
    }
}
