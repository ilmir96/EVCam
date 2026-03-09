package com.kooo.evcam.playback;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import android.widget.PopupMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kooo.evcam.MainActivity;
import com.kooo.evcam.R;
import com.kooo.evcam.StorageHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Изображение回看Fragment（新版)
 * поддержка左右分栏、四宫格预览、单 кам./多 кам.切换
 */
public class PhotoPlaybackFragmentNew extends Fragment {

    // UI  групп件
    private RecyclerView photoList;
    private TextView emptyText;
    private TextView currentDatetime;
    private View noSelectionHint;
    private Button btnMenu, btnRefresh, btnMultiSelect, btnHome;
    private Button btnSelectAll, btnDeleteSelected, btnCancelSelect;
    private TextView selectedCount;
    private View toolbar, multiSelectToolbar;

    // 预览区 групп件
    private View multiViewLayout, singleViewLayout;
    private ImageView imageFront, imageBack, imageLeft, imageRight, imageSingle;
    private FrameLayout frameFront, frameBack, frameLeft, frameRight;
    private TextView labelFront, labelBack, labelLeft, labelRight, labelSingle;
    private TextView placeholderFront, placeholderBack, placeholderLeft, placeholderRight;
    private Button btnViewMode;
    private View controlsLayout;

    // 数据
    private List<DateSection<PhotoGroup>> dateSections = new ArrayList<>();
    private ExpandablePhotoGroupAdapter adapter;
    private PhotoGroup currentGroup;

    // Статус
    private boolean isMultiSelectMode = false;
    private boolean isSingleMode = false;
    private String currentSinglePosition = PhotoGroup.POSITION_FRONT;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_playback_new, container, false);

        initViews(view);
        setupListeners();
        setupDoubleTapListeners();
        updatePhotoList();
        applyStatusBarInsets(view);

        return view;
    }

    private void initViews(View view) {
        // инструмент栏
        toolbar = view.findViewById(R.id.toolbar);
        multiSelectToolbar = view.findViewById(R.id.multi_select_toolbar);
        btnMenu = view.findViewById(R.id.btn_menu);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnMultiSelect = view.findViewById(R.id.btn_multi_select);
        btnHome = view.findViewById(R.id.btn_home);
        currentDatetime = view.findViewById(R.id.current_datetime);

        // 多选инструмент栏
        btnSelectAll = view.findViewById(R.id.btn_select_all);
        btnDeleteSelected = view.findViewById(R.id.btn_delete_selected);
        btnCancelSelect = view.findViewById(R.id.btn_cancel_select);
        selectedCount = view.findViewById(R.id.selected_count);

        // 列表
        photoList = view.findViewById(R.id.photo_list);
        emptyText = view.findViewById(R.id.empty_text);
        noSelectionHint = view.findViewById(R.id.no_selection_hint);

        // 四宫格预览
        multiViewLayout = view.findViewById(R.id.multi_view_layout);
        singleViewLayout = view.findViewById(R.id.single_view_layout);

        imageFront = view.findViewById(R.id.image_front);
        imageBack = view.findViewById(R.id.image_back);
        imageLeft = view.findViewById(R.id.image_left);
        imageRight = view.findViewById(R.id.image_right);
        imageSingle = view.findViewById(R.id.image_single);

        frameFront = view.findViewById(R.id.frame_front);
        frameBack = view.findViewById(R.id.frame_back);
        frameLeft = view.findViewById(R.id.frame_left);
        frameRight = view.findViewById(R.id.frame_right);

        labelFront = view.findViewById(R.id.label_front);
        labelBack = view.findViewById(R.id.label_back);
        labelLeft = view.findViewById(R.id.label_left);
        labelRight = view.findViewById(R.id.label_right);
        labelSingle = view.findViewById(R.id.label_single);

        placeholderFront = view.findViewById(R.id.placeholder_front);
        placeholderBack = view.findViewById(R.id.placeholder_back);
        placeholderLeft = view.findViewById(R.id.placeholder_left);
        placeholderRight = view.findViewById(R.id.placeholder_right);

        // Камера切换按钮 и 控制栏
        btnViewMode = view.findViewById(R.id.btn_view_mode);
        controlsLayout = view.findViewById(R.id.controls_layout);

        // Настройки列表（竖屏2列，横屏1列， д.期头部跨越所有列)
        adapter = new ExpandablePhotoGroupAdapter(getContext(), dateSections);
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    //  д.期头部占满2列，Изображение项占1列
                    return adapter.getItemViewType(position) == 0 ? 2 : 1;
                }
            });
            photoList.setLayoutManager(gridLayoutManager);
        } else {
            photoList.setLayoutManager(new LinearLayoutManager(getContext()));
        }
        photoList.setAdapter(adapter);

        // 初始Статус：隐藏四宫格，显示Уведомление
        multiViewLayout.setVisibility(View.GONE);
        singleViewLayout.setVisibility(View.GONE);
        noSelectionHint.setVisibility(View.VISIBLE);
    }

    private void setupListeners() {
        // 菜单按钮
        btnMenu.setOnClickListener(v -> {
            if (getActivity() != null) {
                DrawerLayout drawerLayout = getActivity().findViewById(R.id.drawer_layout);
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    } else {
                        drawerLayout.openDrawer(GravityCompat.START);
                    }
                }
            }
        });

        // 返回主界面
        btnHome.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).goToRecordingInterface();
            }
        });

        // Обновить
        btnRefresh.setOnClickListener(v -> updatePhotoList());

        // 多选режим
        btnMultiSelect.setOnClickListener(v -> toggleMultiSelectMode());
        btnSelectAll.setOnClickListener(v -> selectAll());
        btnCancelSelect.setOnClickListener(v -> exitMultiSelectMode());
        btnDeleteSelected.setOnClickListener(v -> deleteSelected());

        // 列表项点击
        adapter.setOnItemClickListener((group, position) -> {
            loadPhotoGroup(group);
        });

        adapter.setOnItemSelectedListener(group -> {
            updateSelectedCount();
        });

        // Камера切换按钮（循环切换)
        btnViewMode.setOnClickListener(v -> cycleViewMode());
    }

    /**
     * Настройки四宫格双击监听（双击放大 до 单 кам.)
     */
    private void setupDoubleTapListeners() {
        setupDoubleTap(frameFront, PhotoGroup.POSITION_FRONT, "П");
        setupDoubleTap(frameBack, PhotoGroup.POSITION_BACK, "З");
        setupDoubleTap(frameLeft, PhotoGroup.POSITION_LEFT, "Л");
        setupDoubleTap(frameRight, PhotoGroup.POSITION_RIGHT, "Пр");

        // 单 кам.режим双击返回多 кам.
        if (singleViewLayout != null) {
            GestureDetector detector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (isSingleMode) {
                        switchToMultiMode();
                    }
                    return true;
                }
            });
            singleViewLayout.setOnTouchListener((v, event) -> {
                detector.onTouchEvent(event);
                return true;
            });
        }
    }

    private void setupDoubleTap(View view, String position, String label) {
        if (view == null) return;

        GestureDetector detector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!isSingleMode && currentGroup != null && currentGroup.hasPhoto(position)) {
                    switchToSingleMode(position, label);
                }
                return true;
            }
        });

        view.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            return true;
        });
    }

    /**
     * 切换 до 单 кам.режим
     */
    private void switchToSingleMode(String position, String label) {
        isSingleMode = true;
        currentSinglePosition = position;

        multiViewLayout.setVisibility(View.GONE);
        singleViewLayout.setVisibility(View.VISIBLE);
        labelSingle.setText(label);
        btnViewMode.setText(label + "");

        // загрузка大图
        if (currentGroup != null) {
            File photoFile = currentGroup.getPhotoFile(position);
            loadImage(photoFile, imageSingle);
        }
    }

    /**
     * 切换 до 多 кам.режим
     */
    private void switchToMultiMode() {
        isSingleMode = false;

        multiViewLayout.setVisibility(View.VISIBLE);
        singleViewLayout.setVisibility(View.GONE);
        btnViewMode.setText("Все камеры");
    }

    /**
     * 循环切换视图режим：多 кам. → 前 → 后 → 左 → 右 → 多 кам....
     * 只切换 до 有Изображение Камера
     */
    private void cycleViewMode() {
        if (currentGroup == null) return;
        
        // 构建ДоступноПозиция列表
        java.util.List<String> availablePositions = new java.util.ArrayList<>();
        availablePositions.add("multi"); // 多 кам.始终Доступно
        if (currentGroup.hasPhoto(PhotoGroup.POSITION_FRONT)) availablePositions.add(PhotoGroup.POSITION_FRONT);
        if (currentGroup.hasPhoto(PhotoGroup.POSITION_BACK)) availablePositions.add(PhotoGroup.POSITION_BACK);
        if (currentGroup.hasPhoto(PhotoGroup.POSITION_LEFT)) availablePositions.add(PhotoGroup.POSITION_LEFT);
        if (currentGroup.hasPhoto(PhotoGroup.POSITION_RIGHT)) availablePositions.add(PhotoGroup.POSITION_RIGHT);
        
        // 找 до ТекущийПозиция 索引
        String currentPos = isSingleMode ? currentSinglePosition : "multi";
        int currentIndex = availablePositions.indexOf(currentPos);
        if (currentIndex < 0) currentIndex = 0;
        
        // 切换 до 一 шт.Позиция
        int nextIndex = (currentIndex + 1) % availablePositions.size();
        String nextPos = availablePositions.get(nextIndex);
        
        if ("multi".equals(nextPos)) {
            switchToMultiMode();
        } else {
            String label = getPositionLabel(nextPos);
            switchToSingleMode(nextPos, label);
        }
    }
    
    /**
     * ПолучениеПозиция 应 标签
     */
    private String getPositionLabel(String position) {
        switch (position) {
            case PhotoGroup.POSITION_FRONT: return "П";
            case PhotoGroup.POSITION_BACK: return "З";
            case PhotoGroup.POSITION_LEFT: return "Л";
            case PhotoGroup.POSITION_RIGHT: return "Пр";
            default: return "";
        }
    }

    /**
     * 切换单 кам./多 кам.режим（保留用于双击)
     */
    private void toggleViewMode() {
        cycleViewMode();
    }

    /**
     * загрузкаИзображение групп进行显示
     */
    private void loadPhotoGroup(PhotoGroup group) {
        this.currentGroup = group;
        noSelectionHint.setVisibility(View.GONE);

        // Если  单 кам.режим，проверкаТекущийВыбрать Камера 否有Изображение
        if (isSingleMode) {
            if (!group.hasPhoto(currentSinglePosition)) {
                // ТекущийКамера 新Изображение групп没有Изображение，切回多 кам.режим
                isSingleMode = false;
                btnViewMode.setText("Все камеры");
            }
        }

        // 显示四宫格（根据Текущийрежим)
        if (isSingleMode) {
            multiViewLayout.setVisibility(View.GONE);
            singleViewLayout.setVisibility(View.VISIBLE);
            // 重新загрузка单 кам.大图
            File photoFile = group.getPhotoFile(currentSinglePosition);
            loadImage(photoFile, imageSingle);
        } else {
            multiViewLayout.setVisibility(View.VISIBLE);
            singleViewLayout.setVisibility(View.GONE);
        }

        // 显示控制栏
        controlsLayout.setVisibility(View.VISIBLE);

        // обновление标题栏 д.期时间
        currentDatetime.setText(group.getFormattedDateTime());

        // обновление四宫格 占位符 и Изображение
        updatePhotoDisplay(group);
    }

    /**
     * обновлениеИзображение显示
     */
    private void updatePhotoDisplay(PhotoGroup group) {
        boolean hasFront = group.hasPhoto(PhotoGroup.POSITION_FRONT);
        boolean hasBack = group.hasPhoto(PhotoGroup.POSITION_BACK);
        boolean hasLeft = group.hasPhoto(PhotoGroup.POSITION_LEFT);
        boolean hasRight = group.hasPhoto(PhotoGroup.POSITION_RIGHT);

        // Фронтальная
        imageFront.setVisibility(hasFront ? View.VISIBLE : View.GONE);
        placeholderFront.setVisibility(hasFront ? View.GONE : View.VISIBLE);
        if (hasFront) loadImage(group.getFrontPhoto(), imageFront);

        // Задняя
        imageBack.setVisibility(hasBack ? View.VISIBLE : View.GONE);
        placeholderBack.setVisibility(hasBack ? View.GONE : View.VISIBLE);
        if (hasBack) loadImage(group.getBackPhoto(), imageBack);

        // 左侧
        imageLeft.setVisibility(hasLeft ? View.VISIBLE : View.GONE);
        placeholderLeft.setVisibility(hasLeft ? View.GONE : View.VISIBLE);
        if (hasLeft) loadImage(group.getLeftPhoto(), imageLeft);

        // 右侧
        imageRight.setVisibility(hasRight ? View.VISIBLE : View.GONE);
        placeholderRight.setVisibility(hasRight ? View.GONE : View.VISIBLE);
        if (hasRight) loadImage(group.getRightPhoto(), imageRight);
    }

    /**
     * загрузкаИзображение
     */
    private void loadImage(File photoFile, ImageView imageView) {
        if (photoFile == null || !photoFile.exists() || getContext() == null) {
            return;
        }

        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .signature(new ObjectKey(photoFile.lastModified()))
                .placeholder(android.R.color.black)
                .error(android.R.color.black);

        Glide.with(getContext())
                .load(photoFile)
                .apply(options)
                .into(imageView);
    }

    /**
     * обновлениеИзображение列表（按 д.期分 групп，然后按时间戳分 групп)
     */
    private void updatePhotoList() {
        dateSections.clear();

        File saveDir = StorageHelper.getPhotoDir(getContext());
        if (!saveDir.exists() || !saveDir.isDirectory()) {
            showEmptyState();
            return;
        }

        File[] files = saveDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        });

        if (files == null || files.length == 0) {
            showEmptyState();
            return;
        }

        // Первый步：按时间戳分 групп（同一 сек.拍 多 кам.Изображение)
        Map<String, PhotoGroup> groupMap = new HashMap<>();
        for (File file : files) {
            String timestamp = PhotoGroup.extractTimestampPrefix(file.getName());
            PhotoGroup group = groupMap.get(timestamp);
            if (group == null) {
                group = new PhotoGroup(timestamp);
                groupMap.put(timestamp, group);
            }
            group.addFile(file);
        }

        // 转为列表并排序（последний  前)
        List<PhotoGroup> allGroups = new ArrayList<>(groupMap.values());
        Collections.sort(allGroups, (g1, g2) -> g2.getCaptureTime().compareTo(g1.getCaptureTime()));

        // Второй步：按 д.期分 групп
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Map<String, DateSection<PhotoGroup>> dateSectionMap = new LinkedHashMap<>();
        
        for (PhotoGroup group : allGroups) {
            String dateString = dateFormat.format(group.getCaptureTime());
            DateSection<PhotoGroup> section = dateSectionMap.get(dateString);
            if (section == null) {
                section = new DateSection<>(dateString, group.getCaptureTime());
                dateSectionMap.put(dateString, section);
            }
            section.addItem(group);
        }

        //  д.期分 групп按 д.期排序（LinkedHashMap 保持插入顺序，而 allGroups 排序)
        dateSections.addAll(dateSectionMap.values());

        // обновлениеUI
        if (dateSections.isEmpty()) {
            showEmptyState();
        } else {
            photoList.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
        }

        adapter.buildFlattenedList();
        adapter.notifyDataSetChanged();
    }

    private void showEmptyState() {
        photoList.setVisibility(View.GONE);
        emptyText.setVisibility(View.VISIBLE);
    }

    private void toggleMultiSelectMode() {
        isMultiSelectMode = !isMultiSelectMode;
        adapter.clearSelection();
        adapter.setMultiSelectMode(isMultiSelectMode);
        adapter.notifyDataSetChanged();

        if (isMultiSelectMode) {
            toolbar.setVisibility(View.GONE);
            multiSelectToolbar.setVisibility(View.VISIBLE);
            updateSelectedCount();
        } else {
            toolbar.setVisibility(View.VISIBLE);
            multiSelectToolbar.setVisibility(View.GONE);
        }
    }

    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        adapter.clearSelection();
        adapter.setMultiSelectMode(false);
        adapter.notifyDataSetChanged();
        toolbar.setVisibility(View.VISIBLE);
        multiSelectToolbar.setVisibility(View.GONE);
    }

    private void selectAll() {
        adapter.selectAll();
        adapter.notifyDataSetChanged();
        updateSelectedCount();
    }

    private void updateSelectedCount() {
        selectedCount.setText("Выбрано: " + adapter.getSelectedCount() + "");
    }

    private void deleteSelected() {
        Set<PhotoGroup> selectedGroups = adapter.getSelectedGroups();
        if (selectedGroups.isEmpty()) {
            return;
        }

        new MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Подтвердите удаление")
                .setMessage("Удалить выбранные " + selectedGroups.size() + " групп(ы) фото? (включая все камеры)")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    int deletedCount = 0;
                    
                    // 删除选 Изображение групп
                    for (PhotoGroup group : selectedGroups) {
                        deletedCount += group.deleteAll();
                    }
                    
                    //  от  д.期分 групп移除Удалено  групп
                    for (DateSection<PhotoGroup> section : dateSections) {
                        section.getItems().removeAll(selectedGroups);
                    }
                    
                    // 移除空  д.期分 групп
                    dateSections.removeIf(section -> section.getItemCount() == 0);

                    adapter.clearSelection();
                    adapter.buildFlattenedList();
                    adapter.notifyDataSetChanged();
                    updateSelectedCount();

                    if (getContext() != null) {
                        android.widget.Toast.makeText(getContext(),
                                "Удалено " + deletedCount + " фото",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }

                    if (dateSections.isEmpty()) {
                        exitMultiSelectMode();
                        showEmptyState();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void applyStatusBarInsets(View view) {
        View toolbarView = view.findViewById(R.id.toolbar);
        if (toolbarView != null) {
            final int originalPaddingTop = toolbarView.getPaddingTop();
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbarView, (v, insets) -> {
                int statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                v.setPadding(v.getPaddingLeft(), statusBarHeight + originalPaddingTop, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(toolbarView);
        }
    }
}
