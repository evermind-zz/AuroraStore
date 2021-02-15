package com.aurora.store.service.updater.misc;

import android.content.Context;

import com.aurora.store.download.RequestBuilder;
import com.aurora.store.model.App;
import com.aurora.store.service.updater.interfaces.IRequestListBuilder;
import com.dragons.aurora.playstoreapiv2.AndroidAppDeliveryData;
import com.tonyodev.fetch2.Request;

import java.util.ArrayList;
import java.util.List;

public class RequestListBuilder implements IRequestListBuilder {

    public List<Request> buildRequestList(App app, AndroidAppDeliveryData deliveryData, Context context) {

        final Request request = RequestBuilder
                .buildRequest(context, app, deliveryData.getDownloadUrl());
        final List<Request> splitList = RequestBuilder
                .buildSplitRequestList(context, app, deliveryData);
        final List<Request> obbList = RequestBuilder
                .buildObbRequestList(context, app, deliveryData);

        List<Request> requestList = new ArrayList<>();
        requestList.add(request);
        requestList.addAll(splitList);
        requestList.addAll(obbList);

        return requestList;
    }
}
