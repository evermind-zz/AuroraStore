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

package com.aurora.store.ui.details.views;

import android.content.Intent;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

import com.aurora.store.GlideApp;
import com.aurora.store.R;
import com.aurora.store.model.App;
import com.aurora.store.ui.details.DetailsActivity;
import com.aurora.store.ui.details.ReadMoreActivity;
import com.aurora.store.ui.view.DevInfoLayout;
import com.aurora.store.ui.view.FeatureChip;
import com.aurora.store.util.AppUtil;
import com.aurora.store.util.ContextUtil;
import com.aurora.store.util.TextUtil;
import com.aurora.store.util.Util;
import com.aurora.store.util.ViewUtil;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.dragons.aurora.playstoreapiv2.Feature;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;

public class GeneralDetails extends AbstractDetails {

    @BindView(R.id.root_layout)
    LinearLayout rootLayout;
    @BindView(R.id.icon)
    ImageView appIcon;
    @BindView(R.id.devName)
    TextView txtDevName;
    @BindView(R.id.app_desc_short)
    TextView txtDescShort;
    @BindView(R.id.more_layout)
    RelativeLayout moreLayout;
    @BindView(R.id.versionString)
    TextView app_version;
    @BindView(R.id.chip_group_info)
    ChipGroup chipGroupInfo;
    @BindView(R.id.chip_group_features)
    ChipGroup chipGroupFeatures;
    @BindView(R.id.txt_updated)
    Chip txtUpdated;
    @BindView(R.id.txt_google_dependencies)
    Chip txtDependencies;
    @BindView(R.id.txt_rating)
    Chip txtRating;
    @BindView(R.id.txt_installs)
    Chip txtInstalls;
    @BindView(R.id.txt_size)
    Chip txtSize;
    @BindView(R.id.category)
    Chip category;
    @BindView(R.id.txt_footer)
    TextView txtFooter;
    @BindView(R.id.layout_dev_web)
    DevInfoLayout txtDevWeb;
    @BindView(R.id.layout_dev_mail)
    DevInfoLayout txtDevEmail;
    @BindView(R.id.layout_dev_address)
    DevInfoLayout txtDevAddr;
    @BindView(R.id.btn_positive)
    MaterialButton btnPositive;
    @BindView(R.id.btn_negative)
    MaterialButton btnNegative;
    @BindView(R.id.layout_footer)
    RelativeLayout layoutFooter;

    public GeneralDetails(DetailsActivity activity, App app) {
        super(activity, app);
    }

    @Override
    public void draw() {
        ButterKnife.bind(this, activity);
        drawAppBadge();
        if (app.isInPlayStore()) {
            drawGeneralDetails();
            setupReadMore();
        }
    }

    private void drawAppBadge() {
        GlideApp.with(context)
                .asBitmap()
                .load(app.getIconUrl())
                .transition(new BitmapTransitionOptions().crossFade())
                .transforms(new CenterCrop(), new RoundedCorners(50))
                .into(appIcon);
        setText(R.id.displayName, app.getDisplayName());
        setText(R.id.packageName, app.getPackageName());
        setText(R.id.devName, app.getDeveloperName());
        txtDevName.setOnClickListener(v -> showDevApps());
        drawVersion();
    }

    private void drawGeneralDetails() {
        if (app.isEarlyAccess()) {
            setText(R.id.rating, R.string.early_access);
        } else {
            setText(R.id.rating, R.string.details_rating, app.getRating().getAverage());
        }

        setText(R.id.category, app.getCategoryName());

        if (app.getPrice() != null && app.getPrice().isEmpty())
            setText(R.id.price, R.string.category_appFree);
        else
            setText(R.id.price, app.getPrice());
        setText(R.id.contains_ads, app.isContainsAds() ? R.string.details_contains_ads : R.string.details_no_ads);

        txtUpdated.setText(app.getUpdated());
        txtDependencies.setText(app.getDependencies().isEmpty()
                ? R.string.list_app_independent_from_gsf
                : R.string.list_app_depends_on_gsf);
        txtRating.setText(app.getLabeledRating());
        txtInstalls.setText(app.getInstalls() <= 100 ? "Unknown" : Util.addDiPrefix(app.getInstalls()));
        txtSize.setText(app.getSize() == 0 ? "Unknown" : Formatter.formatShortFileSize(context, app.getSize()));

        setText(R.id.app_desc_short, TextUtil.emptyIfNull(app.getShortDescription()));
        setText(R.id.txt_footer, TextUtil.emptyIfNull(app.getFooterHtml()));
        layoutFooter.setVisibility(View.VISIBLE);

        if (StringUtils.isNotEmpty(app.getDeveloperAddress())) {
            txtDevAddr.setTxtSubtitle(HtmlCompat.fromHtml(app.getDeveloperAddress(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString());
            txtDevAddr.setVisibility(View.VISIBLE);
        }

        if (StringUtils.isNotEmpty(app.getDeveloperWebsite())) {
            txtDevWeb.setTxtSubtitle(app.getDeveloperWebsite());
            txtDevWeb.setVisibility(View.VISIBLE);
        }

        if (StringUtils.isNotEmpty(app.getDeveloperEmail())) {
            txtDevEmail.setTxtSubtitle(app.getDeveloperEmail());
            txtDevEmail.setVisibility(View.VISIBLE);
        }

        drawOfferDetails();
        drawFeatures();
        show(R.id.developer_container, R.id.chip_group_info, R.id.app_desc_short);
    }

    private void drawOfferDetails() {
        List<String> keyList = new ArrayList<>(app.getOfferDetails().keySet());
        Collections.reverse(keyList);
    }

    private void drawVersion() {
        String updateVersionName = app.getVersionName();
        int updateVersionCode = app.getVersionCode();

        if (TextUtils.isEmpty(updateVersionName)) {
            return;
        }

        app_version.setText(StringUtils.joinWith("[", updateVersionName, updateVersionCode) + "]");
        app_version.setVisibility(View.VISIBLE);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                ContextUtil.runOnUiThread(() -> {
                    app_version.setSelected(true);
                });
            }
        }, 3000);

        if (app.isInstalled()) { // in case app is installed we want to show both versions: 'old >> new'
            String stringWithBothVersions = AppUtil.getOldAndNewVersionsAsSingleString(context, app, updateVersionName, updateVersionCode);
            if (null != stringWithBothVersions) {
                app_version.setText(stringWithBothVersions);
            }
        }
    }

    private void drawFeatures() {
        if (app.getFeatures() == null)
            return;
        for (Feature feature : app.getFeatures().getFeaturePresenceList()) {
            FeatureChip chip = new FeatureChip(context);
            chip.setLabel(StringUtils.capitalize(feature.getLabel()));
            chipGroupFeatures.addView(chip);
        }
        show(R.id.chip_group_features);
    }

    private void setupReadMore() {
        if (TextUtils.isEmpty(app.getDescription())) {
            hide(R.id.more_layout);
        } else {
            show(R.id.more_layout);
            moreLayout.setOnClickListener(v -> {
                ReadMoreActivity.app = app;
                final Intent intent = new Intent(activity, ReadMoreActivity.class);
                activity.startActivity(intent, ViewUtil.getEmptyActivityBundle(activity));
            });
        }
    }
}