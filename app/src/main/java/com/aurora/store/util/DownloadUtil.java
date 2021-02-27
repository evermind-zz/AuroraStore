package com.aurora.store.util;

import com.aurora.store.AuroraApplication;
import com.aurora.store.events.Event;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2core.Func;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DownloadUtil {

    /**
     * It is best to call this method to cancel a download. So we inform the system via the RxBus about that
     *
     * @param fetch
     * @param packageName
     * @param hashcode
     */
    static public void cancel(Fetch fetch, String packageName, int hashcode) {
        Func<List<Download>> cancelCallbackSuccess = new Func<List<Download>>() {
            @Override
            public void call(@NotNull List<Download> result) {
                AuroraApplication.rxNotify(new Event(Event.SubType.DOWNLOAD, packageName, Event.StatusType.CANCELED.ordinal()));
            }
        };

        Func<Error> cancelCallbackFailure = new Func<Error>() {
            @Override
            public void call(@NotNull Error result) {
                Log.d("cancel download for package: " + packageName + " failed. With Error: " + result);
                // nevertheless we say also here it was canceled to keep the system working
                AuroraApplication.rxNotify(new Event(Event.SubType.DOWNLOAD, packageName, Event.StatusType.CANCELED.ordinal()));
            }
        };

        fetch.cancelGroup(hashcode, cancelCallbackSuccess, cancelCallbackFailure);
    }
}
