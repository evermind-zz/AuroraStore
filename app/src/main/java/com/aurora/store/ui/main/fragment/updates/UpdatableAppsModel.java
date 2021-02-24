package com.aurora.store.ui.main.fragment.updates;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aurora.store.AuroraApplication;
import com.aurora.store.model.App;
import com.aurora.store.model.items.UpdatesItem;
import com.aurora.store.repository.UpdateRepository;
import com.aurora.store.service.updater.UpdateService;
import com.aurora.store.service.updater.AccessUpdateService;
import com.aurora.store.viewmodel.BaseViewModel;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class UpdatableAppsModel extends BaseViewModel {

    private MutableLiveData<List<UpdatesItem>> data = new MutableLiveData<>();
    private MutableLiveData<Boolean> updateOngoing = new MutableLiveData<>();

    public UpdatableAppsModel(@NonNull Application application) {
        super(application);
        this.api = AuroraApplication.api;
        fetchUpdatesList();
        updateOngoing.setValue(false);

        initObserver();
    }

    public LiveData<List<UpdatesItem>> getUpdatesList() {
        return data;
    }

    public LiveData<Boolean> isUpdateOngoing() {
        return UpdateService.isUpdateOngoing();
    }

    public void fetchUpdatesList() {
        fetchUpdatesList(false);
    }

    public void fetchUpdatesList(boolean doUpdate) {
        UpdateRepository.getInstance().fetchData(getApplication(), doUpdate);
    }

    private Observer<List<App>> createListObserver(UpdatableAppsModel updatableAppsModel) {
        return new Observer<List<App>>() {
            @Override
            public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                disposable.add(d);
            }

            @Override
            public void onNext(@io.reactivex.annotations.NonNull List<App> list) {
                disposable.add(Observable.just(list)
                        .map(UpdatableAppsModel.this::sortList)
                        .flatMap(apps -> Observable
                                .fromIterable(apps)
                                .map(UpdatesItem::new))
                        .toList()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(updatesItems -> data.setValue(updatesItems),
                                throwable -> updatableAppsModel.handleError(throwable))
                );
            }

            @Override
            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
               handleError(e);
            }

            @Override
            public void onComplete() { }
        };
    }

    public void updateApps(List<App> apps) {
        AccessUpdateService.downloadAndInstall(getApplication().getApplicationContext(), apps);
    }

    public void cancelUpdate(List<App> cancelApps) {
        AccessUpdateService.cancelUpdate(getApplication().getApplicationContext(),cancelApps);
    }

    private void initObserver() {
        UpdateRepository.getInstance().observeData(createListObserver(this));
    }
    @Override
    protected void onCleared() {
        disposable.dispose();
        super.onCleared();
    }
}
