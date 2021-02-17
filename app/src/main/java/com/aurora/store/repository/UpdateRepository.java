package com.aurora.store.repository;

import android.content.Context;

import com.aurora.store.AuroraApplication;
import com.aurora.store.events.Event;
import com.aurora.store.model.App;
import com.aurora.store.task.UpdatableAppsTask;
import com.dragons.aurora.playstoreapiv2.GooglePlayAPI;

import java.io.IOException;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import rx.functions.Func0;

// TODO evermind check compositeDisposable. When we are able to dispose. I guess over time the compositeDisposable will be filled with disposables
public class UpdateRepository {

    private static UpdateRepository mInstance;
    private List<App> updatableAppList;
    private CompositeDisposable compositeDisposable;
    private Subject<List<App>> subject;

    public static synchronized UpdateRepository getInstance()
    {
        if (mInstance == null) {
            mInstance = new UpdateRepository();
        }
        return mInstance;
    }

    private UpdateRepository() {
        compositeDisposable = new CompositeDisposable();
        initPublisherSubject();
        subscribeToRxBus();
    }

    private void initPublisherSubject() {
        subject = PublishSubject.<List<App>>create().toSerialized();
    }

    private List<App> getUpdatableAppListFromTask(Context context, boolean doUpdate) throws Exception
    {
        if (doUpdate || updatableAppList == null ) {
            GooglePlayAPI api = AuroraApplication.api;
                updatableAppList = new UpdatableAppsTask(api, context)
                        .getUpdatableApps();
        }

        return updatableAppList;
    }

    public void observeData(Observer<List<App>> observer) {
        subject.subscribe(observer);
    }

    public void fetchData(Context context, boolean doUpdate) {
        Disposable dis = fetchUpdatableAppsFromTaskWrapper(context,doUpdate)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe( list -> {
                    subject.onNext(list);
                });

        compositeDisposable.add(dis);
    }

    private Observable<List<App>> fetchUpdatableAppsFromTaskWrapper(Context context, boolean doUpdate) {
        return Observable.defer(new Func0<Observable<List<App>>>() {
            @Override
            public Observable<List<App>> call() {
                try {
                    return Observable.just(getUpdatableAppListFromTask(context,doUpdate));
                } catch (IOException e) {
                    return Observable.error(e);
                } catch (Exception e) {
                    return Observable.error(e);
                }
            }
        });
    }

    /**
     * on installation success remove from fetched data
     * @param removeApp
     */
    private void updateUpdatableList(String removeApp) {
        Disposable dis = Observable.just(removeApp)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe( appToRemove -> {
                    if (null != updatableAppList) {
                        for ( App app : updatableAppList ) {
                            if ( app.getPackageName() == appToRemove ) {
                                updatableAppList.remove(app);
                                subject.onNext(updatableAppList);
                                break;
                            }
                        }
                    }
                });

        compositeDisposable.add(dis);
    }

    private void subscribeToRxBus() {
        compositeDisposable.add(AuroraApplication
                .getRelayBus()
                .observeOn(io.reactivex.schedulers.Schedulers.io())
                .subscribe(event -> {
                    //Handle list update events
                    switch (event.getSubType()) {
                        case INSTALLED:
                            if (event.getStatus() == Event.StatusType.SUCCESS.ordinal()) {
                                updateUpdatableList(event.getStringExtra());
                            }
                            break;
                        case UNINSTALLED:
                            updateUpdatableList(event.getStringExtra());
                            break;
                    }
                }));
    }
}
