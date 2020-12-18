package com.aurora.store.task;

import android.content.Context;
import android.content.ContextWrapper;

import com.aurora.store.BuildConfig;
import com.aurora.store.util.NetworkInterceptor;
import com.aurora.store.util.NetworkUtil;
import com.aurora.store.util.Util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkTask extends ContextWrapper {

    private Context context;

    public NetworkTask(Context context) {
        super(context);
        this.context = context;
    }

    private static OkHttpClient getOkHttpClient(Context context) {
        final OkHttpClient.Builder builder = NetworkUtil.createOkHttpClientBuilder();
        if (Util.isNetworkProxyEnabled(context))
            builder.proxy(Util.getNetworkProxy(context));
        if (BuildConfig.DEBUG) {
            builder.addNetworkInterceptor(new NetworkInterceptor());
        }
        return builder.build();
    }

    public String get(String url) throws Exception {
        OkHttpClient client = getOkHttpClient(context);
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
}
