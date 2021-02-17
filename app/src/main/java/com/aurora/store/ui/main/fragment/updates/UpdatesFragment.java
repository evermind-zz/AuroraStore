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

package com.aurora.store.ui.main.fragment.updates;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.OneTimeWorkRequest;

import com.aurora.store.AuroraApplication;
import com.aurora.store.Constants;
import com.aurora.store.R;
import com.aurora.store.manager.IgnoreListManager;
import com.aurora.store.model.App;
import com.aurora.store.model.items.UpdatesItem;
import com.aurora.store.sheet.AppMenuSheet;
import com.aurora.store.ui.details.DetailsActivity;
import com.aurora.store.ui.single.fragment.BaseFragment;
import com.aurora.store.ui.view.ViewFlipper2;
import com.aurora.store.util.Log;
import com.aurora.store.util.ViewUtil;
import com.aurora.store.util.WorkerUtil;
import com.aurora.store.util.diff.UpdatesDiffCallback;
import com.aurora.store.worker.ApiValidator;
import com.google.android.material.button.MaterialButton;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil;
import com.mikepenz.fastadapter.select.SelectExtension;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;


public class UpdatesFragment extends BaseFragment {
    @BindView(R.id.coordinator)
    CoordinatorLayout coordinator;
    @BindView(R.id.swipe_layout)
    SwipeRefreshLayout swipeLayout;
    @BindView(R.id.view_flipper)
    ViewFlipper2 viewFlipper;
    @BindView(R.id.recycler)
    RecyclerView recyclerView;
    @BindView(R.id.txt_update_all)
    AppCompatTextView txtUpdateAll;
    @BindView(R.id.btn_action)
    MaterialButton btnAction;

    private UpdatableAppsModel model;
    private FastAdapter<UpdatesItem> fastAdapter;
    private ItemAdapter<UpdatesItem> itemAdapter;
    private SelectExtension<UpdatesItem> selectExtension;

    private CompositeDisposable disposable = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_updates, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupRecycler();

        model = new ViewModelProvider(this).get(UpdatableAppsModel.class);

        model.getUpdatesList().observe(getViewLifecycleOwner(), updatesItems -> {
            dispatchAppsToAdapter(updatesItems);
            swipeLayout.setRefreshing(false);
        });

        model.getError().observe(getViewLifecycleOwner(), errorType -> {
            switch (errorType) {
                case NO_API:
                case SESSION_EXPIRED:
                    awaiting = true;
                    buildAndTestApi();
                    break;

                case NO_NETWORK:
                    awaiting = true;
                    break;
            }
        });

        initObserveBulkUpdate();

        swipeLayout.setRefreshing(true);
        swipeLayout.setOnRefreshListener(() -> model.fetchUpdatesList(true));

        disposable.add(AuroraApplication
                .getRelayBus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    //Handle list update events
                    switch (event.getSubType()) {
                        case BLACKLIST:
                            int adapterPosition = event.getIntExtra();
                            removeItemByAdapterPosition(adapterPosition);
                            break;
                    }

                    //Handle Network & API events
                    switch (event.getSubType()) {
                        case API_SUCCESS:
                        case NETWORK_AVAILABLE:
                            if (awaiting) {
                                model.fetchUpdatesList();
                                awaiting = false;
                            }
                            break;
                    }

                    //Handle misc events
                    switch (event.getSubType()) {
                        case BULK_UPDATE_STARTED:
                        case BULK_UPDATE_STOPPED:
                            updatePageData();
                            break;
                        case WHITELIST:
                            //TODO:Check for update and add app to list if update is available
                            break;
                    }
                }));
    }

    @Override
    public void onPause() {
        swipeLayout.setRefreshing(false);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        disposable.dispose();
        super.onDestroy();
    }

    private void removeItemByPackageName(String packageName) {
        int adapterPosition = -1;
        for (UpdatesItem updatesItem : itemAdapter.getAdapterItems()) {
            if (updatesItem.getPackageName().equals(packageName)) {
                adapterPosition = itemAdapter.getAdapterPosition(updatesItem);
                break;
            }
        }

        removeItemByAdapterPosition(adapterPosition);
    }

    private void removeItemByAdapterPosition(int adapterPosition) {
        if (adapterPosition >= 0 && itemAdapter != null) {
            UpdatesItem updatesItem = itemAdapter.getAdapterItem(adapterPosition);
            updateItemList(updatesItem);
            itemAdapter.remove(adapterPosition);
        }
    }

    private void updateItemList(UpdatesItem updatesItem) {
        AuroraApplication.removeFromOngoingUpdateList(updatesItem.getPackageName());
        updatePageData();
    }

    private void buildAndTestApi() {
        final OneTimeWorkRequest workRequest = WorkerUtil.getWorkRequest(ApiValidator.TAG,
                WorkerUtil.getNetworkConstraints(),
                ApiValidator.class);

        WorkerUtil.enqueue(requireContext(), getViewLifecycleOwner(), workRequest, workInfo -> {
            switch (workInfo.getState()) {
                case FAILED:
                    showSnackBar(coordinator, R.string.toast_api_build_failed, null);
                    break;
            }
        });
    }

    private void updatePageData() {
        updateButtons();

        if (itemAdapter != null && itemAdapter.getAdapterItems().size() > 0) {
            viewFlipper.switchState(ViewFlipper2.DATA);
        } else {
            viewFlipper.switchState(ViewFlipper2.EMPTY);
        }
    }

    private void dispatchAppsToAdapter(List<UpdatesItem> updatesItems) {
        final FastAdapterDiffUtil fastAdapterDiffUtil = FastAdapterDiffUtil.INSTANCE;
        final UpdatesDiffCallback diffCallback = new UpdatesDiffCallback();
        final DiffUtil.DiffResult diffResult = fastAdapterDiffUtil.calculateDiff(itemAdapter, updatesItems, diffCallback);
        fastAdapterDiffUtil.set(itemAdapter, diffResult);
        updatePageData();
    }

    private void setupRecycler() {
        fastAdapter = new FastAdapter<>();
        itemAdapter = new ItemAdapter<>();
        selectExtension = new SelectExtension<>(fastAdapter);

        fastAdapter.addAdapter(0, itemAdapter);

        fastAdapter.setOnClickListener((view, updatesItemIAdapter, updatesItem, integer) -> {
            final App app = updatesItem.getApp();
            final Intent intent = new Intent(requireContext(), DetailsActivity.class);
            intent.putExtra(Constants.INTENT_PACKAGE_NAME, app.getPackageName());
            intent.putExtra(Constants.STRING_EXTRA, gson.toJson(app));
            startActivity(intent, ViewUtil.getEmptyActivityBundle((AppCompatActivity) requireActivity()));
            return false;
        });

        fastAdapter.setOnLongClickListener((view, updatesItemIAdapter, updatesItem, position) -> {
            final AppMenuSheet menuSheet = new AppMenuSheet();
            final Bundle bundle = new Bundle();
            bundle.putInt(Constants.INT_EXTRA, position);
            bundle.putString(Constants.STRING_EXTRA, gson.toJson(updatesItem.getApp()));
            menuSheet.setArguments(bundle);
            menuSheet.show(getChildFragmentManager(), AppMenuSheet.TAG);
            return true;
        });

        fastAdapter.addExtension(selectExtension);
        fastAdapter.addEventHook(new UpdatesItem.CheckBoxClickEvent());

        selectExtension.setMultiSelect(true);

        selectExtension.setSelectionListener((item, selected) -> {
            updateText();
            updatePageData();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(fastAdapter);
    }

    private void updateText() {
        if (selectExtension.getSelectedItems().size() > 0) {
            btnAction.setText(getString(R.string.list_update_selected));
        } else {
            btnAction.setText(getString(R.string.list_update_all));
        }
    }

    private void updateButtons() {
        final int size = itemAdapter.getAdapterItemCount();
        btnAction.setVisibility(size == 0 ? View.INVISIBLE : View.VISIBLE);
        txtUpdateAll.setVisibility(size == 0 ? View.INVISIBLE : View.VISIBLE);

        if (size > 0) {
            txtUpdateAll.setText(new StringBuilder()
                    .append(size)
                    .append(StringUtils.SPACE)
                    .append(size == 1
                            ? requireContext().getString(R.string.list_update_all_txt_one)
                            : requireContext().getString(R.string.list_update_all_txt)));
        }
    }

    private void cancelDownloads() {
        boolean selectiveUpdate = selectExtension.getSelectedItems().size() > 0;
        Disposable d = Observable.fromIterable(selectiveUpdate
                ? selectExtension.getSelectedItems()
                : itemAdapter.getAdapterItems())
                .map(updatesItem -> updatesItem.getApp())
                .toList()
                .subscribe(model::cancelUpdate, throwable -> Log.e(throwable.getMessage()));
        disposable.add(d);
    }

    private void cancelUpdate(List<App> cancelApps) {
        model.cancelUpdate(cancelApps);
    }

    private void initObserveBulkUpdate() {
        model.isUpdateOngoing().observe(getViewLifecycleOwner(), isUpdateOngoing -> updateOngoing(isUpdateOngoing));
    }

    private void updateOngoing(Boolean isUpdateOngoing) {
        btnAction.setOnClickListener(null);
        btnAction.setEnabled(true);
        if (isUpdateOngoing) {
            btnAction.setText(getString(R.string.action_cancel));
            btnAction.setOnClickListener(v -> {
                cancelDownloads();
                btnAction.setEnabled(false);
            });
        } else {
            updateText();
            btnAction.setOnClickListener(v -> {
                boolean selectiveUpdate = selectExtension.getSelectedItems().size() > 0;
                btnAction.setEnabled(false);
                IgnoreListManager ignoreListManager = new IgnoreListManager(requireContext());
                Disposable comp = Observable.fromIterable(selectiveUpdate
                        ? selectExtension.getSelectedItems()
                        : itemAdapter.getAdapterItems())
                        .filter(updatesItem -> {
                            final App app = updatesItem.getApp();
                            return !ignoreListManager.isIgnored(app.getPackageName(), app.getVersionCode());
                        })
                        .map(UpdatesItem::getApp)
                        .toList()
                        .doOnSuccess(apps -> {
                            model.updateApps(apps);
                        })
                        .subscribe();
                disposable.add(comp);
            });
        }
    }
}
