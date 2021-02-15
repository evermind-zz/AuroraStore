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
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class UpdatableAppsModel extends BaseViewModel {

    private MutableLiveData<List<UpdatesItem>> data = new MutableLiveData<>();
    private MutableLiveData<Boolean> updateOngoing = new MutableLiveData<>();

    public UpdatableAppsModel(@NonNull Application application) {
        super(application);
        this.api = AuroraApplication.api;
        fetchUpdatableApps();
        updateOngoing.setValue(false);
    }

    public LiveData<List<UpdatesItem>> getData() {
        return data;
    }

    public LiveData<Boolean> isUpdateOngoing() {
        return UpdateService.isUpdateOngoing();
    }

    public void fetchUpdatableApps() {
        fetchUpdatableApps(false);
    }

    public void fetchUpdatableApps(boolean doRefresh) {

        disposable.add(UpdateRepository.getInstance().fetchUpdatableApps(getApplication(),doRefresh)
                .subscribeOn(Schedulers.computation())
                .map(this::sortList)
                .flatMap(apps -> Observable
                        .fromIterable(apps)
                        .map(UpdatesItem::new))
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(updatesItems -> data.setValue(updatesItems), this::handleError));
    }

    public void updateApps(List<App> apps) {
        AccessUpdateService.downloadAndInstall(getApplication().getApplicationContext(), apps);
    }

    public void cancelUpdate(List<App> cancelApps) {
        AccessUpdateService.cancelUpdate(getApplication().getApplicationContext(),cancelApps);
    }

    @Override
    protected void onCleared() {
        disposable.dispose();
        super.onCleared();
    }
}
