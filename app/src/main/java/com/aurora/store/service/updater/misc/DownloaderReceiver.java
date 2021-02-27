package com.aurora.store.service.updater.misc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.aurora.store.AuroraApplication;
import com.aurora.store.download.DownloadManager;
import com.aurora.store.events.Event;
import com.aurora.store.util.DownloadUtil;
import com.aurora.store.util.Log;
import com.tonyodev.fetch2.Fetch;

import static com.aurora.store.service.NotificationService.FETCH_GROUP_ID;
import static com.aurora.store.service.NotificationService.FETCH_CANCEL;
import static com.aurora.store.service.NotificationService.FETCH_PACKAGE_NAME;
import static com.aurora.store.service.NotificationService.FETCH_PAUSE;
import static com.aurora.store.service.NotificationService.FETCH_RESUME;

public class DownloaderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Fetch fetch = DownloadManager.getFetchInstance(context);
        Bundle extras = intent.getExtras();
        int groupId;

        String action = intent.getAction();
        if (null == action) {
            Log.e("No intent action given: " + intent );
            return;
        }

        groupId = extras.getInt(FETCH_GROUP_ID, -1);
        if ((action.equals(FETCH_RESUME))) {
            fetch.resumeGroup(groupId);
        } else if ((action.equals(FETCH_PAUSE))) {
            fetch.pauseGroup(groupId);
        } else if ((action.equals(FETCH_CANCEL))) {
            String packageName = extras.getString(FETCH_PACKAGE_NAME, "");
            DownloadUtil.cancel(fetch, packageName, groupId);
        }
    }
}
