package com.aurora.store.ui.single.activity;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.aurora.store.R;
import com.aurora.store.task.AuthTask;
import com.aurora.store.util.Accountant;
import com.aurora.store.util.Log;
import com.aurora.store.util.NetworkUtil;
import com.aurora.store.util.Util;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class GoogleLoginActivity extends BaseActivity {

    public static final String EMBEDDED_SETUP_URL = "https://accounts.google.com/EmbeddedSetup";
    public static final String OAUTH_TOKEN = "oauth_token";

    @BindView(R.id.webview)
    WebView webview;

    private CookieManager cookieManager = CookieManager.getInstance();
    private CompositeDisposable disposable = new CompositeDisposable();

    @Override
    public void applyOverrideConfiguration(final Configuration overrideConfiguration) {
        if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT < 25) {
            overrideConfiguration.uiMode &= ~Configuration.UI_MODE_NIGHT_MASK;
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);

        if (NetworkUtil.isConnected(this)) {
            setupWebView();
        } else {
            Toast.makeText(this, getString(R.string.error_no_network), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {

        if (!StringUtils.isEmpty(webview.getUrl()))
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
            cookieManager.acceptThirdPartyCookies(webview);
            cookieManager.setAcceptThirdPartyCookies(webview, true);
        } else {
            cookieManager.removeAllCookie();
            cookieManager.setAcceptCookie(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webview.getSettings().setSafeBrowsingEnabled(false);
        }

        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String cookies = CookieManager.getInstance().getCookie(url);
                Map<String, String> cookieMap = Util.parseCookieString(cookies);
                if (!cookieMap.isEmpty() && cookieMap.get(OAUTH_TOKEN) != null) {
                    String oauth_token = cookieMap.get(OAUTH_TOKEN);
                    webview.evaluateJavascript("(function() { return document.getElementById('profileIdentifier').innerHTML; })();",
                            email -> {
                                email = email.replaceAll("\"", "");
                                generateTokens(email, oauth_token);
                            });
                }
            }
        });

        webview.getSettings().setAllowContentAccess(true);
        webview.getSettings().setDatabaseEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setAppCacheEnabled(true);
        webview.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        webview.loadUrl(EMBEDDED_SETUP_URL);
    }

    private void generateTokens(String email, String token) {
        disposable.add(Observable.fromCallable(() -> new AuthTask(this).getAASToken(email, token))
                .subscribeOn(Schedulers.io())
                .map(aasToken -> new AuthTask(this).getAuthToken(email, aasToken))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(success -> {
                    if (success) {
                        Toast.makeText(this, getText(R.string.toast_login_success), Toast.LENGTH_SHORT).show();
                        Accountant.setLoggedIn(this);
                        Accountant.setAnonymous(this, false);
                        finish();
                    } else {
                        Toast.makeText(this, getText(R.string.toast_login_failed), Toast.LENGTH_LONG).show();
                    }
                }, err -> {
                    Toast.makeText(this, getText(R.string.toast_login_failed), Toast.LENGTH_LONG).show();
                    Log.e(err.getMessage());
                }));
    }
}
