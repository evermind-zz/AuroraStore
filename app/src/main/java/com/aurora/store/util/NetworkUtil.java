/*
 * Aurora Store
 * Copyright (C) 2019, Rahul Kumar Patel <whyorean@gmail.com>
 *
 * Aurora Store is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Aurora Store is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package com.aurora.store.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

public class NetworkUtil {

    public static boolean isConnected(Context context) {
        final Object object = context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final ConnectivityManager manager = (ConnectivityManager) object;

        if (manager != null) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
                if (capabilities != null) {
                    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)
                            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE);
                }
            } else {
                try {
                    final NetworkInfo networkInfo = manager.getActiveNetworkInfo();
                    return networkInfo != null && networkInfo.isConnected();
                } catch (Exception e) {
                    Log.d(e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }

    public static OkHttpClient.Builder createOkHttpClientBuilder() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            builder.sslSocketFactory(TLSSocketFactory.getInstance(), TLSSocketFactory.getTrustManager());
            enableMoreCipherSuites(builder);
        }

        return builder;
    }

    private static OkHttpClient.Builder enableMoreCipherSuites(OkHttpClient.Builder builder) {
        // Try to enable all modern CipherSuites (+2 more)
        // that are supported on the device.
        // https://github.com/square/okhttp/issues/4053#issuecomment-402579554
        final List<CipherSuite> cipherSuites =
                new ArrayList<>(ConnectionSpec.MODERN_TLS.cipherSuites());
        cipherSuites.add(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA);
        cipherSuites.add(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA);
        final ConnectionSpec legacyTLS = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .cipherSuites(cipherSuites.toArray(new CipherSuite[0]))
                .build();

        builder.connectionSpecs(Arrays.asList(legacyTLS, ConnectionSpec.CLEARTEXT));

        return builder;
    }
}
