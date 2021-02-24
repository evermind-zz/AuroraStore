package com.aurora.store.service.updater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.aurora.store.AuroraApplication;
import com.aurora.store.R;
import com.aurora.store.events.Event;
import com.aurora.store.exception.CredentialsEmptyException;
import com.aurora.store.exception.MalformedRequestException;
import com.aurora.store.exception.NotPurchasedException;
import com.aurora.store.exception.TooManyRequestsException;
import com.aurora.store.model.App;
import com.aurora.store.notification.QuickNotification;
import com.aurora.store.service.updater.interfaces.IRequestListBuilder;
import com.aurora.store.service.updater.interfaces.IUpdateService;
import com.aurora.store.service.updater.interfaces.IUpdateServiceDownloader;
import com.aurora.store.service.updater.misc.RequestListBuilder;
import com.aurora.store.service.updater.misc.UpdateServiceDownloader;
import com.aurora.store.task.ObservableDeliveryData;
import com.aurora.store.ui.main.AuroraActivity;
import com.aurora.store.util.Log;
import com.dragons.aurora.playstoreapiv2.AuthException;
import com.ivianuu.rxserviceconnection.RxBinder;
import com.tonyodev.fetch2.Request;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;


/**
 * This service will automatically shutdown if the following took place:
 - if it could not download anything
 - if it has all installed/failed to install
 - all went right
 - no active ServiceConnection are present
   use only {@code AccessUpdateService} to access this Service
 */
public class UpdateService extends IUpdateService {

    private static final String CHANNEL_ID = "mychannel";
    private static MutableLiveData<Boolean> updateOngoing;

    //for verification that we have no more downloads and installation completed
    private Map<String, App> trackUpdateApps = Collections.synchronizedMap(new HashMap<>());

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private IUpdateServiceDownloader downloader;
    private Subject<ObservableDeliveryData.DeliveryDataBundle> publisher;
    private IRequestListBuilder requestListBuilder;

    private RxBinder<IUpdateService> binder = new RxBinder<IUpdateService>() {
        @Override
        public IUpdateService getService() {
            return UpdateService.this;
        }
    };

    public static LiveData<Boolean> isUpdateOngoing() {
        if (updateOngoing == null) {
             updateOngoing = new MutableLiveData<>(false);
        }
        return updateOngoing;
    }

    private void postUpdateOngoing(boolean isUpdateOngoing) {
        if (updateOngoing == null) {
            updateOngoing = new MutableLiveData<>();
        }
        updateOngoing.postValue(isUpdateOngoing);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        downloader = new UpdateServiceDownloader(getApplicationContext(),this);

        initPublisher();
        subscribeToPublisher(observeEnqueuedApp());

        AuroraApplication.rxNotify(new Event(Event.SubType.BULK_UPDATE_STARTED));
        postUpdateOngoing(true);
        subscribeToRxBus();
    }

    private void initPublisher() {
        publisher = PublishSubject.<ObservableDeliveryData.DeliveryDataBundle>create().toSerialized();
    }

    private void subscribeToPublisher(Observer<ObservableDeliveryData.DeliveryDataBundle> observer) {
        publisher
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .subscribe(observer);
    }

    private Observer<ObservableDeliveryData.DeliveryDataBundle> observeEnqueuedApp() {
        return new Observer<ObservableDeliveryData.DeliveryDataBundle>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                compositeDisposable.add(d);
            }

            @Override
            public void onNext(@NonNull ObservableDeliveryData.DeliveryDataBundle bundle) {
                Log.d(bundle.getApp().getPackageName() + " " + Thread.currentThread().getName());
                enqueueFilesToDownloader(bundle);
            }

            @Override
            public void onError(@NonNull Throwable e) { }

            @Override
            public void onComplete() { }
        };
    }

    private void enqueueFilesToDownloader(ObservableDeliveryData.DeliveryDataBundle bundle) {
        final List<Request> requestList = requestListBuilder.buildRequestList(
                bundle.getApp(),
                bundle.getAndroidAppDeliveryData(),
                getApplicationContext()
        );

        downloader.enqueueAppsForDownload(requestList, bundle.getApp());
        postUpdateOngoing(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Example Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String input = intent.getStringExtra("inputExtra");
        requestListBuilder = new RequestListBuilder();


        // evermind: maybe set no pendingIntent
        Intent notificationIntent = new Intent(this, AuroraActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Example Service")
                .setContentText(input)
                //.setSmallIcon(R.drawable.ic_android)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void installOnly(App app) {
        trackUpdateApps.put(app.getPackageName(), app);
        AuroraApplication.getInstaller().NEWenqueueApp(app);
    }

    @Override
    public void downloadAndInstallBundle(ObservableDeliveryData.DeliveryDataBundle bundle) {
        trackUpdateApps.put(bundle.getApp().getPackageName(), bundle.getApp());
        publisher.onNext(bundle);
    }

    // cancel only those updates we are actually managing
    private List<App> filterCancelUpdate(List<App> cancelUpdateApps) {
        List<App> filteredCancelList = new LinkedList<>();

        for (App app : cancelUpdateApps) {
            if (null != trackUpdateApps.get(app.getPackageName())) {
                filteredCancelList.add(app);
            }
        }
        return filteredCancelList;
    }

    @Override
    public void cancelUpdate(List<App> cancelUpdateApps) {

        List<App> filteredCancelList = filterCancelUpdate(cancelUpdateApps);
        if (0 == filteredCancelList.size()) {
            shutdownServiceIfNoLongerNeeded();
        } else {
            downloader.cancelAppsDownload(cancelUpdateApps);
        }
    }

    @Override
    public void downloadAndInstall(List<App> appsToBeUpdated) {
        if (0 == appsToBeUpdated.size())
            shutdownServiceIfNoLongerNeeded();

        // here we have to get the delivery data first
        compositeDisposable.add(Observable.fromIterable(appsToBeUpdated)
                .flatMap(app -> new ObservableDeliveryData(getApplicationContext()).getDeliveryData(app)
                        .doOnError(err -> {
                            if (err instanceof MalformedRequestException
                                    || err instanceof NotPurchasedException
                                    || err instanceof TooManyRequestsException) {
                                QuickNotification.show(getApplication(),
                                        getString(R.string.action_updates),
                                        err.getMessage(),
                                        null);
                            }
                            processException(err);
                            AuroraApplication.rxNotify(new Event(Event.SubType.DOWNLOAD, app.getPackageName(), Event.StatusType.API_FAILURE.ordinal()));
                            Log.e(err.getMessage());
                        })
                        .onErrorResumeNext(Observable.empty())
                )
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(deliveryDataBundle -> downloadAndInstallBundle(deliveryDataBundle))
                .subscribe());
    }

    private void processException(Throwable e) {
        if (e instanceof CredentialsEmptyException) {
            AuroraApplication.rxNotify(new Event(Event.SubType.API_ERROR));
        } else if (e instanceof AuthException | e instanceof TooManyRequestsException) {
            AuroraApplication.rxNotify(new Event(Event.SubType.API_FAILED));
        } else if (e instanceof UnknownHostException) {
            AuroraApplication.rxNotify(new Event(Event.SubType.NETWORK_UNAVAILABLE));
        } else
            Log.e(e.getMessage());
    }

    private void subscribeToRxBus() {
        compositeDisposable.add(AuroraApplication
                .getRelayBus()
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    //Handle list update events
                    switch (event.getSubType()) {
                        case INSTALLED:
                            // package has been either installed or not. event.getIntExtra() would state this
                            // but we just don't care here as we just want to know if we can stop the service
                            cleanupAndShutdownServiceIfNoLongerNeeded(event.getStringExtra());
                            break;
                        case DOWNLOAD:
                            if ((event.getStatus() == Event.StatusType.FAILURE.ordinal())
                                || (event.getStatus() == Event.StatusType.CANCELED.ordinal())
                                || (event.getStatus() == Event.StatusType.API_FAILURE.ordinal())) {

                                cleanupAndShutdownServiceIfNoLongerNeeded(event.getStringExtra());

                            } else if (event.getStatus() == Event.StatusType.CANCEL.ordinal()) {
                                String packageName = event.getStringExtra();
                                downloader.cancelAppDownload(packageName);
                            }
                            break;
                    }
                }));
    }

    private void cleanupAndShutdownServiceIfNoLongerNeeded(String packageName) {
        trackUpdateApps.remove(packageName);
        shutdownServiceIfNoLongerNeeded();
    }

    private void shutdownServiceIfNoLongerNeeded() {
        if(trackUpdateApps.size()==0) {
            Log.d("UpdateService no longer needed shutdown. Info: ThreadName" + Thread.currentThread().getName());
            stopForeground(true);
            stopSelf();
        }
    }

    private void notifyStopped() {
        AuroraApplication.rxNotify(new Event(Event.SubType.BULK_UPDATE_STOPPED));
        postUpdateOngoing(false);
    }

    // make sure there are no more ServiceConnection active
    // or onDestroy() will never be called
    @Override
    public void onDestroy() {
        downloader.onDestroy();
        compositeDisposable.dispose();
        notifyStopped();
        super.onDestroy();
    }
}