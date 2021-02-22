package com.aurora.store.service.updater.misc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.ArrayMap;

import com.aurora.store.Constants;
import com.aurora.store.GlideApp;
import com.aurora.store.R;
import com.aurora.store.ui.details.DetailsActivity;
import com.aurora.store.ui.single.activity.DownloadsActivity;
import com.aurora.store.util.Log;
import com.aurora.store.util.NotificationUtil;
import com.aurora.store.util.Util;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.tonyodev.fetch2.AbstractFetchGroupListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.FetchGroup;
import com.tonyodev.fetch2.Status;
import com.tonyodev.fetch2core.Extras;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import lombok.Getter;

import static android.content.Context.NOTIFICATION_SERVICE;

public class DownloadNotifications extends  AbstractFetchGroupListener {

    public static final String FETCH_GROUP_ID = "FETCH_GROUP_ID";
    public static final String FETCH_PAUSE = "com.aurora.store.service.updater.misc.DownloadNotifications.FETCH_PAUSE";
    public static final String FETCH_RESUME = "com.aurora.store.service.updater.misc.DownloadNotifications.FETCH_RESUME";
    public static final String FETCH_CANCEL = "com.aurora.store.service.updater.misc.DownloadNotifications.FETCH_CANCEL";
    public static final String FETCH_PACKAGE_NAME = "FETCH_PACKAGE_NAME";

    private final ArrayMap<String, DownloadBundle> bundleArrayMap = new ArrayMap<>();
    private final Context mContext;

    private NotificationManager notificationManager;

    public DownloadNotifications(Context context ) {
        mContext = context;

        Log.i("Notification Service Started");


        //Create Notification Channels : General & Alert
        createNotificationChannel();

        notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    }

            @Override
            public void onCancelled(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onCompleted(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onError(int groupId, @NotNull Download download, @NotNull Error error, @Nullable Throwable throwable, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onProgress(int groupId, @NotNull Download download, long etaInMilliSeconds, long downloadedBytesPerSecond, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onQueued(int groupId, @NotNull Download download, boolean waitingNetwork, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onPaused(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            final ArrayList<NotificationChannel> channels = new ArrayList<>();

            channels.add(new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ALERT,
                    mContext.getString(R.string.notification_channel_alert),
                    NotificationManager.IMPORTANCE_HIGH));

            channels.add(new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_GENERAL,
                    mContext.getString(R.string.notification_channel_general),
                    NotificationManager.IMPORTANCE_MIN));

            if (notificationManager != null) {
                notificationManager.createNotificationChannels(channels);
            }
        }
    }

    private void showNotification(int groupId, Download download, FetchGroup fetchGroup) {

        if (!NotificationUtil.isNotificationEnabled(mContext))
            return;

        final Status status = download.getStatus();

        //Ignore notifications for completion of sub-parts of a bundled apk
        if (status == Status.COMPLETED && fetchGroup.getGroupDownloadProgress() < 100)
            return;

        synchronized (bundleArrayMap) {
            DownloadBundle downloadBundle = bundleArrayMap.get(download.getTag());
            if (downloadBundle == null) {
                downloadBundle = new DownloadBundle(download);
                bundleArrayMap.put(download.getTag(), downloadBundle);
            }

            final NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, Constants.NOTIFICATION_CHANNEL_GENERAL);

            builder.setContentTitle(downloadBundle.getDisplayName());
            builder.setSmallIcon(R.drawable.ic_notification_outlined);
            builder.setColor(ContextCompat.getColor(mContext, R.color.colorAccent));
            builder.setWhen(download.getCreated());
            builder.setContentIntent(getContentIntentForDownloads());

            switch (status) {
                case PAUSED:
                    builder.setSmallIcon(R.drawable.ic_download_pause);
                    builder.setContentText(mContext.getString(R.string.download_paused));
                    break;
                case CANCELLED:
                    builder.setSmallIcon(R.drawable.ic_download_cancel);
                    builder.setContentText(mContext.getString(R.string.download_canceled));
                    builder.setColor(ContextCompat.getColor(mContext, R.color.colorRed));
                    break;
                case FAILED:
                    builder.setSmallIcon(R.drawable.ic_download_fail);
                    builder.setContentText(mContext.getString(R.string.download_failed));
                    builder.setColor(ContextCompat.getColor(mContext, R.color.colorRed));
                    break;
                case COMPLETED:
                    if (fetchGroup.getGroupDownloadProgress() == 100) {
                        builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
                        builder.setContentText(mContext.getString(R.string.download_completed));
                    }
                    break;
                default:
                    builder.setSmallIcon(android.R.drawable.stat_sys_download);
                    //builder.setContentText(mContext.getString(R.string.download_metadata));
                    builder.setContentText(download.getFileUri().getLastPathSegment());

                    break;
            }

            final int progress = fetchGroup.getGroupDownloadProgress();
            final NotificationCompat.BigTextStyle progressBigText = new NotificationCompat.BigTextStyle();

            //Set Notification data
            switch (status) {
                case QUEUED:
                    builder.setProgress(100, 0, true);
                    progressBigText.bigText(mContext.getString(R.string.download_queued));
                    builder.setStyle(progressBigText);
                    break;

                case DOWNLOADING:
                    final String contentString = mContext.getString(R.string.download_progress);
                    final String partString = StringUtils.joinWith("/",
                            fetchGroup.getCompletedDownloads().size() + 1,
                            fetchGroup.getDownloads().size());
                    final String speedString = Util.humanReadableByteSpeed(download.getDownloadedBytesPerSecond(), true);

                    progressBigText.bigText(StringUtils.joinWith(" \u2022 ",
                            contentString,
                            partString,
                            speedString));
                    builder.setStyle(progressBigText);

                    builder.addAction(new NotificationCompat.Action.Builder(R.drawable.ic_download_pause,
                            mContext.getString(R.string.action_pause),
                            getPauseIntent(groupId)).build());

                    builder.addAction(new NotificationCompat.Action.Builder(R.drawable.ic_download_cancel,
                            mContext.getString(R.string.action_cancel),
                            getCancelIntent(downloadBundle.getPackageName(),groupId)).build());

                    if (progress < 0)
                        builder.setProgress(100, 0, true);
                    else
                        builder.setProgress(100, progress, false);
                    break;

                case PAUSED:
                    final String pauseString = mContext.getString(R.string.download_paused);
                    final String filesString = StringUtils.joinWith("/",
                            fetchGroup.getCompletedDownloads().size(),
                            fetchGroup.getDownloads().size());
                    progressBigText.bigText(StringUtils.joinWith(" \u2022 ",
                            pauseString,
                            filesString));
                    builder.setStyle(progressBigText);
                    builder.addAction(new NotificationCompat.Action.Builder(R.drawable.ic_download_pause,
                            mContext.getString(R.string.action_resume),
                            getResumeIntent(groupId)).build());
                    break;

                case COMPLETED:
                    if (fetchGroup.getGroupDownloadProgress() == 100) {
                        builder.setAutoCancel(true);
                        builder.setContentIntent(getContentIntentForDetails(downloadBundle.getPackageName()));
                        //Check for Aurora Services or Root, if available do not show install notification.
                        if (Util.isPrivilegedInstall(mContext)) {
                            progressBigText.bigText(mContext.getString(R.string.details_installing));
                        } else {
                            //Check for Enforced Native & Add Install action via notification, only if app is not bundled.
                            if (Util.isNativeInstallerEnforced(mContext) && fetchGroup.getDownloads().size() > 1) {
                                progressBigText.bigText(mContext.getString(R.string.notification_installation_manual));
                            } else {
                                progressBigText.bigText(mContext.getString(R.string.notification_installation_auto));
                                builder.addAction(R.drawable.ic_installation,
                                        mContext.getString(R.string.details_install),
                                        getInstallIntent(downloadBundle.getPackageName(),
                                                downloadBundle.getVersionCode(),
                                                downloadBundle.getDisplayName()));
                            }
                        }
                        builder.setStyle(progressBigText);
                    }
                    break;
            }

            //Set Notification category
            switch (status) {
                case DOWNLOADING:
                    builder.setCategory(Notification.CATEGORY_PROGRESS);
                    break;
                case FAILED:
                case CANCELLED:
                    builder.setCategory(Notification.CATEGORY_ERROR);
                    break;
                default:
                    builder.setCategory(Notification.CATEGORY_STATUS);
                    break;
            }

            //Set icon
            GlideApp.with(mContext)
                    .asBitmap()
                    .load(downloadBundle.getIconUrl())
                    .circleCrop()
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NotNull Bitmap bitmap, Transition<? super Bitmap> transition) {
                            builder.setLargeIcon(bitmap);
                        }
                    });

            notificationManager.notify(downloadBundle.getPackageName(),
                    downloadBundle.getPackageName().hashCode(),
                    builder.build());
        }
    }

    private PendingIntent getPauseIntent(int groupId) {
        final Intent intent = new Intent(mContext, DownloaderReceiver.class);
        intent.setAction(FETCH_PAUSE);
        intent.putExtra(FETCH_GROUP_ID, groupId);
        return PendingIntent.getBroadcast(mContext, groupId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getResumeIntent(int groupId) {
        final Intent intent = new Intent(mContext, DownloaderReceiver.class);
        intent.setAction(FETCH_RESUME);
        intent.putExtra(FETCH_GROUP_ID, groupId);
        return PendingIntent.getBroadcast(mContext, groupId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getCancelIntent(String packageName, int groupId) {
        final Intent intent = new Intent(mContext, DownloaderReceiver.class);
        intent.setAction(FETCH_CANCEL);
        intent.putExtra(FETCH_GROUP_ID, groupId);
        intent.putExtra(FETCH_PACKAGE_NAME,packageName);
        return PendingIntent.getBroadcast(mContext, groupId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getContentIntentForDetails(String packageName) {
        final Intent intent = new Intent(mContext, DetailsActivity.class);
        intent.putExtra(Constants.INTENT_PACKAGE_NAME, packageName);
        return PendingIntent.getActivity(mContext, packageName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getContentIntentForDownloads() {
        final Intent intent = new Intent(mContext, DownloadsActivity.class);
        return PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getInstallIntent(String packageName, String versionCode, String displayName) {
        //final Intent intent = new Intent(mContext, InstallReceiver.class);
        final Intent intent = new Intent(mContext, DownloaderReceiver.class); // TODO remove later -- just testing
        intent.putExtra(Constants.INTENT_PACKAGE_NAME, packageName);
        intent.putExtra(Constants.DOWNLOAD_DISPLAY_NAME, displayName);
        intent.putExtra(Constants.DOWNLOAD_VERSION_CODE, versionCode);
        return PendingIntent.getBroadcast(mContext, packageName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Getter
    private static class DownloadBundle {
        private String packageName;
        private String displayName;
        private String versionName;
        private String versionCode;
        private String iconUrl;

        public DownloadBundle(Download download) {
            Extras extras = download.getExtras();
            this.packageName = extras.getString(Constants.DOWNLOAD_PACKAGE_NAME, StringUtils.EMPTY);
            this.displayName = extras.getString(Constants.DOWNLOAD_DISPLAY_NAME, StringUtils.EMPTY);
            this.versionName = extras.getString(Constants.DOWNLOAD_VERSION_NAME, StringUtils.EMPTY);
            this.versionCode = extras.getString(Constants.DOWNLOAD_VERSION_CODE, StringUtils.EMPTY);
            this.iconUrl = extras.getString(Constants.DOWNLOAD_ICON_URL, StringUtils.EMPTY);
        }
    }
}
