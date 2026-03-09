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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.PopupMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kooo.evcam.MainActivity;
import com.kooo.evcam.R;
import com.kooo.evcam.StorageHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Видео回看Fragment（新版)
 * поддержка左右分栏、四宫格预览、单 кам./多 кам.切换、倍速Воспр.
 */
public class PlaybackFragmentNew extends Fragment {

    // UI  групп件
    private RecyclerView videoList;
    private TextView emptyText;
    private TextView currentDatetime;
    private View noSelectionHint;
    private Button btnMenu, btnRefresh, btnMultiSelect, btnHome;
    private Button btnSelectAll, btnDeleteSelected, btnCancelSelect;
    private TextView selectedCount;
    private View toolbar, multiSelectToolbar;

    // 预览区 групп件
    private View multiViewLayout, singleViewLayout;
    private VideoView videoFront, videoBack, videoLeft, videoRight, videoSingle;
    private FrameLayout frameFront, frameBack, frameLeft, frameRight;
    private TextView labelFront, labelBack, labelLeft, labelRight, labelSingle;
    private TextView placeholderFront, placeholderBack, placeholderLeft, placeholderRight;

    // Воспр.控制 групп件
    private Button btnPlayPause, btnViewMode, btnSpeed;
    private SeekBar seekBar;
    private TextView currentTime, totalTime;

    // 数据
    private List<DateSection<VideoGroup>> dateSections = new ArrayList<>();
    private VideoGroup currentGroup;
    private ExpandableVideoGroupAdapter adapter;
    private MultiVideoPlayerManager playerManager;

    // Статус
    private boolean isMultiSelectMode = false;
    private boolean isSingleMode = false;
    private String currentSinglePosition = VideoGroup.POSITION_FRONT;
    private boolean isDraggingSeekBar = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playback_new, container, false);
        
        initViews(view);
        initPlayerManager();
        setupListeners();
        setupDoubleTapListeners();
        updateVideoList();
        
        // ПриложениеСтатус栏适配
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
        videoList = view.findViewById(R.id.video_list);
        emptyText = view.findViewById(R.id.empty_text);
        noSelectionHint = view.findViewById(R.id.no_selection_hint);

        // 四宫格预览
        multiViewLayout = view.findViewById(R.id.multi_view_layout);
        singleViewLayout = view.findViewById(R.id.single_view_layout);
        
        videoFront = view.findViewById(R.id.video_front);
        videoBack = view.findViewById(R.id.video_back);
        videoLeft = view.findViewById(R.id.video_left);
        videoRight = view.findViewById(R.id.video_right);
        videoSingle = view.findViewById(R.id.video_single);

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

        // Воспр.控制
        btnPlayPause = view.findViewById(R.id.btn_play_pause);
        btnViewMode = view.findViewById(R.id.btn_view_mode);
        btnSpeed = view.findViewById(R.id.btn_speed);
        seekBar = view.findViewById(R.id.seek_bar);
        currentTime = view.findViewById(R.id.current_time);
        totalTime = view.findViewById(R.id.total_time);

        // Настройки列表（竖屏2列，横屏1列， д.期头部跨越所有列)
        adapter = new ExpandableVideoGroupAdapter(getContext(), dateSections);
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    //  д.期头部占满2列，Видео项占1列
                    return adapter.getItemViewType(position) == 0 ? 2 : 1;
                }
            });
            videoList.setLayoutManager(gridLayoutManager);
        } else {
            videoList.setLayoutManager(new LinearLayoutManager(getContext()));
        }
        videoList.setAdapter(adapter);

        // 初始Статус：隐藏四宫格，显示Уведомление
        multiViewLayout.setVisibility(View.GONE);
        singleViewLayout.setVisibility(View.GONE);
        noSelectionHint.setVisibility(View.VISIBLE);
    }

    private void initPlayerManager() {
        playerManager = new MultiVideoPlayerManager(getContext());
        playerManager.setVideoViews(videoFront, videoBack, videoLeft, videoRight, videoSingle);
        
        playerManager.setPlaybackListener(new MultiVideoPlayerManager.OnPlaybackListener() {
            @Override
            public void onPrepared(int duration) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    seekBar.setMax(duration);
                    totalTime.setText(formatTime(duration));
                    currentTime.setText(formatTime(0));
                });
            }

            @Override
            public void onProgressUpdate(int currentPosition) {
                if (getActivity() == null || isDraggingSeekBar) return;
                getActivity().runOnUiThread(() -> {
                    seekBar.setProgress(currentPosition);
                    currentTime.setText(formatTime(currentPosition));
                });
            }

            @Override
            public void onPlaybackStateChanged(boolean isPlaying) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    btnPlayPause.setText(isPlaying ? "Пауза" : "Воспр.");
                });
            }

            @Override
            public void onCompletion() {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    seekBar.setProgress(0);
                    currentTime.setText(formatTime(0));
                });
            }

            @Override
            public void onError(String message) {
                // Ошибка处理
            }

            @Override
            public void onSingleVideoPrepared() {
                // 单 кам.Видео准备好后显示画面（防止闪烁旧画面)
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (videoSingle != null) {
                        videoSingle.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
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
        btnRefresh.setOnClickListener(v -> updateVideoList());

        // 多选режим
        btnMultiSelect.setOnClickListener(v -> toggleMultiSelectMode());
        btnSelectAll.setOnClickListener(v -> selectAll());
        btnCancelSelect.setOnClickListener(v -> exitMultiSelectMode());
        btnDeleteSelected.setOnClickListener(v -> deleteSelected());

        // 列表项点击
        adapter.setOnItemClickListener((group, position) -> {
            loadVideoGroup(group);
        });

        adapter.setOnItemSelectedListener(group -> {
            updateSelectedCount();
        });

        // Воспр.控制
        btnPlayPause.setOnClickListener(v -> playerManager.togglePlayPause());

        // Камера切换按钮（循环切换)
        btnViewMode.setOnClickListener(v -> cycleViewMode());

        // 倍速
        btnSpeed.setOnClickListener(v -> {
            float newSpeed = playerManager.cycleSpeed();
            btnSpeed.setText(String.format(Locale.getDefault(), "%.1fx", newSpeed));
        });

        // 进度条
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isDraggingSeekBar = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isDraggingSeekBar = false;
                playerManager.seekTo(seekBar.getProgress());
            }
        });
    }

    /**
     * Настройки四宫格双击监听（双击放大 до 单 кам.)
     */
    private void setupDoubleTapListeners() {
        setupDoubleTap(frameFront, VideoGroup.POSITION_FRONT, "П");
        setupDoubleTap(frameBack, VideoGroup.POSITION_BACK, "З");
        setupDoubleTap(frameLeft, VideoGroup.POSITION_LEFT, "Л");
        setupDoubleTap(frameRight, VideoGroup.POSITION_RIGHT, "Пр");

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
                if (!isSingleMode && playerManager.hasVideo(position)) {
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
        
        // 先 Фоновый режимзагрузкаВидео，延迟后再切换界面显示（防止闪烁旧画面или黑屏)
        labelSingle.setText(label);
        btnViewMode.setText(label + "");
        
        // 确保 videoSingle 可见（ 切换布局до)
        if (videoSingle != null) {
            videoSingle.setVisibility(View.VISIBLE);
        }
        
        // 先загрузкаВидео（此时 singleViewLayout 还  GONE，用户看不 до )
        playerManager.setSingleMode(true, position);
        
        // 延迟切换界面，等Видеозагрузказавершение后再显示（无动画，直接切换)
        if (multiViewLayout != null) {
            multiViewLayout.postDelayed(() -> {
                if (isSingleMode) {
                    // 直接切换，不做动画（避免透明过渡时看 до 十字背景)
                    multiViewLayout.setVisibility(View.GONE);
                    singleViewLayout.setVisibility(View.VISIBLE);
                }
            }, 200);
        }
    }

    /**
     * 切换 до 多 кам.режим
     */
    private void switchToMultiMode() {
        isSingleMode = false;
        btnViewMode.setText("Все камеры");
        
        playerManager.setSingleMode(false, null);
        
        // 直接切换，不做动画（避免透明过渡时看 до 十字背景)
        singleViewLayout.setVisibility(View.GONE);
        multiViewLayout.setVisibility(View.VISIBLE);
    }

    /**
     * 循环切换视图режим：多 кам. → 前 → 后 → 左 → 右 → 多 кам....
     * 只切换 до 有Видео Камера
     */
    private void cycleViewMode() {
        if (currentGroup == null) return;
        
        // 构建ДоступноПозиция列表
        java.util.List<String> availablePositions = new java.util.ArrayList<>();
        availablePositions.add("multi"); // 多 кам.始终Доступно
        if (currentGroup.hasVideo(VideoGroup.POSITION_FRONT)) availablePositions.add(VideoGroup.POSITION_FRONT);
        if (currentGroup.hasVideo(VideoGroup.POSITION_BACK)) availablePositions.add(VideoGroup.POSITION_BACK);
        if (currentGroup.hasVideo(VideoGroup.POSITION_LEFT)) availablePositions.add(VideoGroup.POSITION_LEFT);
        if (currentGroup.hasVideo(VideoGroup.POSITION_RIGHT)) availablePositions.add(VideoGroup.POSITION_RIGHT);
        
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
            case VideoGroup.POSITION_FRONT: return "П";
            case VideoGroup.POSITION_BACK: return "З";
            case VideoGroup.POSITION_LEFT: return "Л";
            case VideoGroup.POSITION_RIGHT: return "Пр";
            default: return "";
        }
    }

    /**
     * 切换单 кам./多 кам.режим
     */
    private void toggleViewMode() {
        if (isSingleMode) {
            // Текущий 单 кам.режим，切换回多 кам.
            switchToMultiMode();
        } else {
            // Текущий 多 кам.режим，弹出选项菜单Выбрать单 кам.
            showCameraSelectPopup();
        }
    }

    /**
     * 显示КамераВыбрать弹出菜单
     */
    private void showCameraSelectPopup() {
        // 构建可选Камера列表
        List<String> positions = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        
        if (playerManager.hasVideo(VideoGroup.POSITION_FRONT)) {
            positions.add(VideoGroup.POSITION_FRONT);
            labels.add("П");
        }
        if (playerManager.hasVideo(VideoGroup.POSITION_BACK)) {
            positions.add(VideoGroup.POSITION_BACK);
            labels.add("З");
        }
        if (playerManager.hasVideo(VideoGroup.POSITION_LEFT)) {
            positions.add(VideoGroup.POSITION_LEFT);
            labels.add("Л");
        }
        if (playerManager.hasVideo(VideoGroup.POSITION_RIGHT)) {
            positions.add(VideoGroup.POSITION_RIGHT);
            labels.add("Пр");
        }

        if (positions.isEmpty()) {
            return; // 没有可选项
        }

        String[] items = labels.toArray(new String[0]);
        
        new MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Выбрать камеру")
                .setItems(items, (dialog, which) -> {
                    String position = positions.get(which);
                    String label = labels.get(which).replace("", "");
                    switchToSingleMode(position, label);
                })
                .show();
    }

    /**
     * загрузкаВидео групп进行Воспр.
     */
    private void loadVideoGroup(VideoGroup group) {
        this.currentGroup = group;
        noSelectionHint.setVisibility(View.GONE);
        
        // Если  单 кам.режим，проверкаТекущийВыбрать Камера 否有Видео
        if (isSingleMode) {
            if (!group.hasVideo(currentSinglePosition)) {
                // ТекущийКамера 新Видео групп没有Видео，切回多 кам.режим
                isSingleMode = false;
            }
        }
        
        // 显示四宫格（根据Текущийрежим)
        if (isSingleMode) {
            multiViewLayout.setVisibility(View.GONE);
            singleViewLayout.setVisibility(View.VISIBLE);
        } else {
            multiViewLayout.setVisibility(View.VISIBLE);
            singleViewLayout.setVisibility(View.GONE);
            btnViewMode.setText("Все камеры");
        }
        
        // обновление标题栏 д.期时间
        currentDatetime.setText(group.getFormattedDateTime());
        
        // обновление四宫格 占位符显示
        updatePlaceholders(group);
        
        // 同步Воспр.器 режимНастройки（确保 singleModePosition  последний )
        playerManager.updateSingleModePosition(isSingleMode, currentSinglePosition);
        
        // загрузкаВидео
        playerManager.loadVideoGroup(group);
    }
    
    /**
     * 查找Первый шт.有Видео КамераПозиция
     */
    /**
     * обновление占位符显示（无Видео时显示)
     */
    private void updatePlaceholders(VideoGroup group) {
        boolean hasFront = group.hasVideo(VideoGroup.POSITION_FRONT);
        boolean hasBack = group.hasVideo(VideoGroup.POSITION_BACK);
        boolean hasLeft = group.hasVideo(VideoGroup.POSITION_LEFT);
        boolean hasRight = group.hasVideo(VideoGroup.POSITION_RIGHT);

        videoFront.setVisibility(hasFront ? View.VISIBLE : View.GONE);
        placeholderFront.setVisibility(hasFront ? View.GONE : View.VISIBLE);

        videoBack.setVisibility(hasBack ? View.VISIBLE : View.GONE);
        placeholderBack.setVisibility(hasBack ? View.GONE : View.VISIBLE);

        videoLeft.setVisibility(hasLeft ? View.VISIBLE : View.GONE);
        placeholderLeft.setVisibility(hasLeft ? View.GONE : View.VISIBLE);

        videoRight.setVisibility(hasRight ? View.VISIBLE : View.GONE);
        placeholderRight.setVisibility(hasRight ? View.GONE : View.VISIBLE);
    }

    /**
     * обновлениеВидео列表（按 д.期分 групп，然后按时间戳分 групп)
     */
    private void updateVideoList() {
        dateSections.clear();

        File saveDir = StorageHelper.getVideoDir(getContext());
        if (!saveDir.exists() || !saveDir.isDirectory()) {
            showEmptyState();
            return;
        }

        File[] files = saveDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
        if (files == null || files.length == 0) {
            showEmptyState();
            return;
        }

        // Первый步：按时间戳分 групп（同一 сек.Запись 多 кам.Видео)
        Map<String, VideoGroup> groupMap = new HashMap<>();
        for (File file : files) {
            String timestamp = VideoGroup.extractTimestampPrefix(file.getName());
            VideoGroup group = groupMap.get(timestamp);
            if (group == null) {
                group = new VideoGroup(timestamp);
                groupMap.put(timestamp, group);
            }
            group.addFile(file);
        }

        // 转为列表并排序（последний  前)
        List<VideoGroup> allGroups = new ArrayList<>(groupMap.values());
        Collections.sort(allGroups, (g1, g2) -> g2.getRecordTime().compareTo(g1.getRecordTime()));

        // Второй步：按 д.期分 групп
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Map<String, DateSection<VideoGroup>> dateSectionMap = new LinkedHashMap<>();
        
        for (VideoGroup group : allGroups) {
            String dateString = dateFormat.format(group.getRecordTime());
            DateSection<VideoGroup> section = dateSectionMap.get(dateString);
            if (section == null) {
                section = new DateSection<>(dateString, group.getRecordTime());
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
            videoList.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
        }

        adapter.buildFlattenedList();
        adapter.notifyDataSetChanged();
    }

    private void showEmptyState() {
        videoList.setVisibility(View.GONE);
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
        Set<VideoGroup> selectedGroups = adapter.getSelectedGroups();
        if (selectedGroups.isEmpty()) {
            return;
        }

        new MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Подтвердите удаление")
                .setMessage("Удалить выбранные " + selectedGroups.size() + " групп(ы) видео? (включая все камеры)")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    int deletedCount = 0;
                    
                    // 删除选 Видео групп
                    for (VideoGroup group : selectedGroups) {
                        deletedCount += group.deleteAll();
                    }
                    
                    //  от  д.期分 групп移除Удалено  групп
                    for (DateSection<VideoGroup> section : dateSections) {
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
                                "Удалено " + deletedCount + " видеофайл(ов)",
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

    /**
     * 格式化时间（毫 сек. -> mm:ss)
     */
    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
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

    @Override
    public void onPause() {
        super.onPause();
        if (playerManager != null && playerManager.isPlaying()) {
            playerManager.pause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (playerManager != null) {
            playerManager.release();
        }
    }
}
