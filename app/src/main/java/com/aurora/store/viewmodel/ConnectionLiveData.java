package com.aurora.store.viewmodel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import androidx.lifecycle.LiveData;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import com.aurora.store.model.ConnectionModel;
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork;

public class ConnectionLiveData extends LiveData<ConnectionModel> {

    private Context context;

    private BroadcastReceiver networkReceiver = new BroadcastReceiver() {

        private Disposable disposable = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getExtras() != null) {
                if (disposable != null)
                    disposable.dispose();

                disposable = ReactiveNetwork
                        .observeNetworkConnectivity(context)
                        .observeOn(Schedulers.io())
                        .flatMapSingle(connectivity -> ReactiveNetwork.checkInternetConnectivity())
                        .subscribe(isConnected -> {
                            if (isConnected) {
                                postValue(new ConnectionModel("ONLINE", true));
                            } else {
                                postValue(new ConnectionModel("OFFLINE", false));
                            }
                        });
            }
        }
    };

    public ConnectionLiveData(Context context) {
        this.context = context;
    }

    @Override
    protected void onActive() {
        super.onActive();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(networkReceiver, filter);
    }

    @Override
    protected void onInactive() {
        context.unregisterReceiver(networkReceiver);
        super.onInactive();
    }
}
