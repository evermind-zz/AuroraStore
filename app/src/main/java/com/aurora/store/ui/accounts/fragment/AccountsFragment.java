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

package com.aurora.store.ui.accounts.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aurora.store.AuroraApplication;
import com.aurora.store.GlideApp;
import com.aurora.store.R;
import com.aurora.store.ui.single.activity.GoogleLoginActivity;
import com.aurora.store.util.Accountant;
import com.aurora.store.util.ApiBuilderUtil;
import com.aurora.store.util.ContextUtil;
import com.aurora.store.util.Log;
import com.aurora.store.util.NetworkUtil;
import com.aurora.store.util.PrefUtil;
import com.dragons.aurora.playstoreapiv2.GooglePlayAPI;
import com.dragons.aurora.playstoreapiv2.Image;
import com.dragons.aurora.playstoreapiv2.UserProfile;
import com.google.android.material.chip.Chip;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class AccountsFragment extends Fragment {

    private static final String URL_TOS = "https://www.google.com/mobile/android/market-tos.html";
    private static final String URL_LICENSE = "https://gitlab.com/AuroraOSS/AuroraStore/raw/master/LICENSE";
    private static final String URL_DISCLAIMER = "https://gitlab.com/AuroraOSS/AuroraStore/raw/master/DISCLAIMER";

    @BindView(R.id.view_switcher_top)
    ViewSwitcher viewSwitcherTop;
    @BindView(R.id.view_switcher_bottom)
    ViewSwitcher viewSwitcherBottom;
    @BindView(R.id.init)
    LinearLayout initLayout;
    @BindView(R.id.info)
    LinearLayout infoLayout;
    @BindView(R.id.login)
    LinearLayout loginLayout;
    @BindView(R.id.logout)
    LinearLayout logoutLayout;
    @BindView(R.id.login_google)
    RelativeLayout loginGoogle;
    @BindView(R.id.img)
    ImageView imgAvatar;
    @BindView(R.id.user_name)
    TextView txtName;
    @BindView(R.id.user_mail)
    TextView txtMail;
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    @BindView(R.id.btn_positive)
    Button btnPositive;
    @BindView(R.id.btn_negative)
    Button btnNegative;
    @BindView(R.id.btn_anonymous)
    Button btnAnonymous;
    @BindView(R.id.chip_tos)
    Chip chipTos;
    @BindView(R.id.chip_disclaimer)
    Chip chipDisclaimer;
    @BindView(R.id.chip_license)
    Chip chipLicense;

    private CompositeDisposable disposable = new CompositeDisposable();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_accounts, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Accountant.isLoggedIn(requireContext())) {
            init();
        }
    }

    @Override
    public void onDestroy() {
        disposable.clear();
        super.onDestroy();
    }

    @OnClick(R.id.btn_positive)
    public void openLoginActivity() {
        requireContext().startActivity(new Intent(requireContext(), GoogleLoginActivity.class));
    }

    @OnClick(R.id.btn_negative)
    public void clearAccountantData() {
        Accountant.completeCheckout(requireContext());
        init();
    }

    @OnClick(R.id.btn_anonymous)
    public void loginAnonymous() {
        if (NetworkUtil.isConnected(requireContext())) {
            disposable.add(Observable.fromCallable(() -> ApiBuilderUtil
                    .login(requireContext()))
                    .subscribeOn(Schedulers.io())
                    .map(api -> {
                        AuroraApplication.api = api;
                        return api.userProfile().getUserProfile();
                    })
                    .doOnSubscribe(d -> updateAnonymousAction(true))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(userProfile -> {
                        Toast.makeText(requireContext(), getString(R.string.toast_login_success), Toast.LENGTH_LONG).show();
                        Accountant.setAnonymous(requireContext(), true);
                        updateUI(userProfile);
                    }, err -> {
                        Toast.makeText(requireContext(), getString(R.string.toast_login_success), Toast.LENGTH_LONG).show();
                        ContextUtil.runOnUiThread(() -> updateAnonymousAction(false));
                        Log.e(err.getMessage());
                    }));
        } else {
            Toast.makeText(requireContext(), getString(R.string.error_no_network), Toast.LENGTH_SHORT).show();
        }
    }

    private void init() {
        boolean isLoggedIn = Accountant.isLoggedIn(requireContext());
        switchTopViews(isLoggedIn);
        switchBottomViews(isLoggedIn);
        setupChips();
        setupProfile();
        progressBar.setVisibility(View.INVISIBLE);
    }

    private void switchTopViews(boolean showInfo) {
        if (viewSwitcherTop.getCurrentView() == initLayout && showInfo)
            viewSwitcherTop.showNext();
        else if (viewSwitcherTop.getCurrentView() == infoLayout && !showInfo)
            viewSwitcherTop.showPrevious();
    }

    private void switchBottomViews(boolean showLogout) {
        if (viewSwitcherBottom.getCurrentView() == loginLayout && showLogout)
            viewSwitcherBottom.showNext();
        else if (viewSwitcherBottom.getCurrentView() == logoutLayout && !showLogout)
            viewSwitcherBottom.showPrevious();
    }

    private void setupChips() {
        chipTos.setOnClickListener(v -> {
            openWebView(URL_TOS);
        });
        chipDisclaimer.setOnClickListener(v -> {
            openWebView(URL_DISCLAIMER);
        });
        chipLicense.setOnClickListener(v -> {
            openWebView(URL_LICENSE);
        });
    }

    private void setupProfile() {
        GlideApp
                .with(this)
                .load(Accountant.getImageURL(requireContext()))
                .placeholder(R.drawable.circle_bg)
                .circleCrop()
                .into(imgAvatar);
        txtName.setText(Accountant.isAnonymous(requireContext())
                ? getText(R.string.account_dummy)
                : Accountant.getUserName(requireContext()));
        txtMail.setText(Accountant.isAnonymous(requireContext())
                ? "auroraoss@gmail.com"
                : Accountant.getEmail(requireContext()));
    }

    private void updateUI(UserProfile userProfile) {
        PrefUtil.putString(requireContext(), Accountant.PROFILE_NAME, userProfile.getName());
        for (Image image : userProfile.getImageList()) {
            if (image.getImageType() == GooglePlayAPI.IMAGE_TYPE_APP_ICON) {
                PrefUtil.putString(requireContext(), Accountant.PROFILE_AVATAR, image.getImageUrl());
            }
        }
        setupProfile();
        init();
    }

    private void updateAnonymousAction(boolean progress) {
        btnAnonymous.setEnabled(!progress);
        btnAnonymous.setText(progress ? getString(R.string.action_logging_in) : getText(R.string.account_dummy));
        progressBar.setVisibility(progress ? View.VISIBLE : View.INVISIBLE);
    }

    private void openWebView(String URL) {
        try {
            requireContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(URL)));
        } catch (Exception e) {
            Log.e("No WebView found !");
        }
    }
}
