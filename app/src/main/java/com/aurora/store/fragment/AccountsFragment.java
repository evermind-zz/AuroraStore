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

package com.aurora.store.fragment;

import android.content.Context;
import android.content.Intent;
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
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aurora.store.Constants;
import com.aurora.store.GlideApp;
import com.aurora.store.R;
import com.aurora.store.activity.AuroraActivity;
import com.aurora.store.activity.IntroActivity;
import com.aurora.store.api.PlayStoreApiAuthenticator;
import com.aurora.store.task.UserProfiler;
import com.aurora.store.utility.Accountant;
import com.aurora.store.utility.Log;
import com.aurora.store.utility.PrefUtil;
import com.dragons.aurora.playstoreapiv2.GooglePlayAPI;
import com.dragons.aurora.playstoreapiv2.Image;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import org.jetbrains.annotations.NotNull;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static com.aurora.store.utility.ContextUtil.runOnUiThread;

public class AccountsFragment extends BaseFragment implements BaseFragment.EventListenerImpl {

    @BindView(R.id.view_switcher_top)
    ViewSwitcher mViewSwitcherTop;
    @BindView(R.id.view_switcher_bottom)
    ViewSwitcher mViewSwitcherBottom;
    @BindView(R.id.view_switcher_login)
    ViewSwitcher mViewSwitcherLogin;
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
    @BindView(R.id.login_dummy)
    RelativeLayout loginDummy;
    @BindView(R.id.avatar)
    ImageView imgAvatar;
    @BindView(R.id.user_name)
    TextView txtName;
    @BindView(R.id.user_mail)
    TextView txtMail;
    @BindView(R.id.txt_input_email)
    TextInputEditText txtInputEmail;
    @BindView(R.id.txt_input_password)
    TextInputEditText txtInputPassword;
    @BindView(R.id.user_account_chip)
    Chip accountSwitch;
    @BindView(R.id.progress_bar)
    ProgressBar mProgressBar;
    @BindView(R.id.btn_positive)
    Button btnPositive;
    @BindView(R.id.btn_positive_alt)
    Button btnPositiveAlt;
    @BindView(R.id.btn_negative)
    Button btnNegative;

    private Context context;
    private boolean isDummy = false;
    private boolean isLoggedIn = false;
    private CompositeDisposable mCompositeDisposable = new CompositeDisposable();

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_accounts, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        init();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCompositeDisposable.clear();
        mCompositeDisposable.dispose();
    }

    @Override
    public void onLoggedIn() {

    }

    @Override
    public void onLoginFailed() {

    }

    @Override
    public void onNetworkFailed() {

    }

    private void init() {
        isLoggedIn = Accountant.isLoggedIn(context);
        isDummy = Accountant.isDummy(context);
        mProgressBar.setVisibility(View.INVISIBLE);
        setupView();
        setupAccountType();
        setupActions();
    }

    private void setupView() {
        if (isLoggedIn) {
            if (isDummy)
                loadDummyData();
            else
                loadGoogleData();
        }
        switchTopViews(isLoggedIn);
        switchLoginViews(!isDummy);
        switchBottomViews(isLoggedIn);
        switchButtonState(isLoggedIn);
    }

    private void setupAccountType() {
        accountSwitch.setEnabled(!isLoggedIn);
        accountSwitch.setClickable(!isLoggedIn);
        accountSwitch.setText(isDummy ? R.string.account_dummy : R.string.account_google);
    }

    private void setupActions() {
        btnPositive.setOnClickListener(loginListener());
        btnPositiveAlt.setOnClickListener(loginListener());
        btnNegative.setOnClickListener(logoutListener());
        accountSwitch.setOnClickListener(switchAccountListener());
    }

    private void loadDummyData() {
        imgAvatar.setImageDrawable(context.getDrawable(R.drawable.ic_avatar_boy));
        txtName.setText(Accountant.getUserName(context));
        txtMail.setText(Accountant.getEmail(context));
    }

    private void loadGoogleData() {
        GlideApp
                .with(this)
                .load(Accountant.getImageURL(context))
                .circleCrop()
                .into(imgAvatar);
        txtName.setText(Accountant.getUserName(context));
        txtMail.setText(Accountant.getEmail(context));
    }

    private void switchTopViews(boolean showInfo) {
        if (mViewSwitcherTop.getCurrentView() == initLayout && showInfo)
            mViewSwitcherTop.showNext();
        else if (mViewSwitcherTop.getCurrentView() == infoLayout && !showInfo)
            mViewSwitcherTop.showPrevious();
    }

    private void switchBottomViews(boolean showLogout) {
        if (mViewSwitcherBottom.getCurrentView() == loginLayout && showLogout)
            mViewSwitcherBottom.showNext();
        else if (mViewSwitcherBottom.getCurrentView() == logoutLayout && !showLogout)
            mViewSwitcherBottom.showPrevious();
    }

    private void switchLoginViews(boolean showGoogle) {
        if (mViewSwitcherLogin.getCurrentView() == loginGoogle && !showGoogle)
            mViewSwitcherLogin.showNext();
        else if (mViewSwitcherLogin.getCurrentView() == loginDummy && showGoogle)
            mViewSwitcherLogin.showPrevious();
    }

    private void switchButtonState(boolean logging) {
        btnPositive.setText(logging ? R.string.action_logging_in : R.string.action_login);
        btnPositiveAlt.setText(logging ? R.string.action_logging_in : R.string.action_login);
        btnPositive.setEnabled(!logging);
        btnPositiveAlt.setEnabled(!logging);
    }

    private View.OnClickListener logoutListener() {
        return v -> {
            Accountant.completeCheckout(context);
            init();
        };
    }

    private View.OnClickListener loginListener() {
        return v -> {
            if (isDummy) {
                logInWithDummy();
            } else {
                final String email = txtInputEmail.getText().toString();
                final String password = txtInputPassword.getText().toString();
                if (email.isEmpty())
                    txtInputEmail.setError("?");
                if (password.isEmpty())
                    txtInputPassword.setError("?");
                if (!email.isEmpty() && !password.isEmpty())
                    logInWithGoogle(email, password);
            }
        };
    }

    private View.OnClickListener switchAccountListener() {
        return v -> {
            if (isDummy) {
                isDummy = false;
                accountSwitch.setText(R.string.account_google);
                if (!Accountant.isLoggedIn(context))
                    switchLoginViews(true);
            } else {
                isDummy = true;
                accountSwitch.setText(R.string.account_dummy);
                if (!Accountant.isLoggedIn(context))
                    switchLoginViews(false);
            }
        };
    }

    private void logInWithDummy() {
        switchButtonState(true);
        mCompositeDisposable.add(Observable.fromCallable(() ->
                new PlayStoreApiAuthenticator(context).login())
                .subscribeOn(Schedulers.io())
                .doOnSubscribe(sub -> mProgressBar.setVisibility(View.VISIBLE))
                .observeOn(Schedulers.computation())
                .subscribe((success) -> {
                    if (success) {
                        Log.i("Dummy Login Successful");
                        runOnUiThread(() -> {
                            Accountant.saveDummy(context);
                            init();
                            finishIntro();
                        });
                    } else {
                        Log.e("Dummy Login Failed Permanently");
                        switchButtonState(false);
                    }
                }, err -> {
                    Log.e("Dummy Login failed %s", err.getMessage());
                    switchButtonState(false);
                }));
    }

    private void logInWithGoogle(String email, String password) {
        switchButtonState(true);
        mCompositeDisposable.add(Observable.fromCallable(() ->
                new PlayStoreApiAuthenticator(context).login(email, password))
                .subscribeOn(Schedulers.io())
                .doOnSubscribe(sub -> mProgressBar.setVisibility(View.VISIBLE))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((success) -> {
                    if (success) {
                        Log.i("Google Login Successful");
                        runOnUiThread(() -> {
                            Accountant.saveGoogle(context);
                            getUserInfo();
                            finishIntro();
                        });
                    } else {
                        Log.e("Google Login Failed Permanently");
                        switchButtonState(false);
                    }
                }, err -> {
                    Log.e("Google Login failed : %s", err.getMessage());
                    mProgressBar.setVisibility(View.INVISIBLE);
                    txtInputPassword.setError("Check your password");
                    switchButtonState(false);
                }));
    }

    private void getUserInfo() {
        mCompositeDisposable.add(Observable.fromCallable(() ->
                new UserProfiler(context).getUserProfile())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((profile) -> {
                    if (profile != null) {
                        PrefUtil.putString(context, Accountant.GOOGLE_NAME, profile.getName());
                        for (Image image : profile.getImageList()) {
                            if (image.getImageType() == GooglePlayAPI.IMAGE_TYPE_APP_ICON) {
                                PrefUtil.putString(context, Accountant.GOOGLE_URL, image.getImageUrl());
                            }
                        }
                        runOnUiThread(this::init);
                    }
                }, err -> Log.e("Google Login failed : %s", err.getMessage())));
    }

    private void finishIntro() {
        if (getActivity() instanceof IntroActivity) {
            PrefUtil.putBoolean(context, Constants.PREFERENCE_DO_NOT_SHOW_INTRO, true);
            getActivity().startActivity(new Intent(context, AuroraActivity.class));
            getActivity().finish();
        }
    }
}
