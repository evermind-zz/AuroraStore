package com.aurora.store.util;


import android.os.Build;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * the whole purpose of this class is to enable TLSv1.1 and TLSv1.2 on devices <=KITKAT
 */
public class TLSSocketFactory extends SSLSocketFactory {

    private final String TLS_v1_1 = "TLSv1.1";
    private final String TLS_v1_2 = "TLSv1.2";
    private final String[] tls_protocols = {TLS_v1_1, TLS_v1_2};

    private SSLSocketFactory internalSSLSocketFactory;

    public static SSLSocketFactory getInstance(){
        SSLSocketFactory socketFactory = null;
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                socketFactory = new TLSSocketFactory(context.getSocketFactory());
            } else {
                socketFactory = context.getSocketFactory();
            }

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            Log.d(e.getStackTrace().toString());
        }

        return socketFactory;
    }


    public static X509TrustManager getTrustManager() {
        TrustManagerFactory trustManagerFactory = null;
        TrustManager[] trustManagers = null;
        try {
            trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            e.printStackTrace();
        }

        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

        return trustManager;
    }

    private TLSSocketFactory(SSLSocketFactory delegate) {
        internalSSLSocketFactory = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return internalSSLSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return internalSSLSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
    }

    /*
     * Utility methods
     */

    private Socket enableTLSOnSocket(Socket socket) {
        if (socket != null && (socket instanceof SSLSocket)
                && isTLSServerEnabled((SSLSocket) socket)) { // skip the fix if server doesn't provide there TLS version
            ((SSLSocket) socket).setEnabledProtocols(tls_protocols);
        }

        return socket;
    }

    private boolean isTLSServerEnabled(SSLSocket sslSocket) {
        System.out.println("__prova__ :: " + sslSocket.getSupportedProtocols().toString());

        for (String protocol : sslSocket.getSupportedProtocols()) {
            if (protocol.equals(TLS_v1_1) || protocol.equals(TLS_v1_2)) {
                return true;
            }
        }

        return false;
    }
}

