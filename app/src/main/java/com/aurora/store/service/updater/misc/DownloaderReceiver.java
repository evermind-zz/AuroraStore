package com.aurora.store.service.updater.misc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.aurora.store.AuroraApplication;
import com.aurora.store.download.DownloadManager;
import com.aurora.store.events.Event;
import com.aurora.store.util.Log;
import com.tonyodev.fetch2.Fetch;

import static com.aurora.store.service.updater.misc.DownloadNotifications.FETCH_GROUP_ID;
import static com.aurora.store.service.updater.misc.DownloadNotifications.FETCH_CANCEL;
import static com.aurora.store.service.updater.misc.DownloadNotifications.FETCH_PACKAGE_NAME;
import static com.aurora.store.service.updater.misc.DownloadNotifications.FETCH_PAUSE;
import static com.aurora.store.service.updater.misc.DownloadNotifications.FETCH_RESUME;

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

        if ((action.equals(FETCH_RESUME))) {
            groupId = extras.getInt(FETCH_GROUP_ID, -1);
            fetch.resumeGroup(groupId);
        } else if ((action.equals(FETCH_PAUSE))) {
            groupId = extras.getInt(FETCH_GROUP_ID, -1);
            fetch.pauseGroup(groupId);
        } else if ((action.equals(FETCH_CANCEL))) {
            // we don't call cancelGroup() here instead let Event.StatusType.CANCEL receiver handle
            // that. So we do canceling a download only in a single place
            String packageName = extras.getString(FETCH_PACKAGE_NAME, "");
            if (!packageName.equals(""))
                AuroraApplication.rxNotify(new Event(Event.SubType.DOWNLOAD, packageName, Event.StatusType.CANCEL.ordinal()));
        }
    }
}
