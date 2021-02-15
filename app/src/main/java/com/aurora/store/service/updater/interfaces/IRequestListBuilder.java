package com.aurora.store.service.updater.interfaces;

import android.content.Context;

import com.aurora.store.model.App;
import com.dragons.aurora.playstoreapiv2.AndroidAppDeliveryData;
import com.tonyodev.fetch2.Request;

import java.util.List;

public interface IRequestListBuilder {
    List<Request> buildRequestList(App app, AndroidAppDeliveryData deliveryData, Context context);
}
