package com.kooo.evcam;

import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * 补盲选项Настройки界面
 */
public class BlindSpotSettingsFragment extends Fragment {
    private static final String TAG = "BlindSpotSettingsFragment";

    private Button openLabButton;

    private SwitchMaterial turnSignalLinkageSwitch;
    private SeekBar turnSignalTimeoutSeekBar;
    private TextView tvTurnSignalTimeout;
    private RadioGroup turnSignalPresetGroup;
    private LinearLayout customKeywordsLayout;
    private EditText turnSignalLeftLogEditText;
    private EditText turnSignalRightLogEditText;
    private boolean isUpdatingFromPreset = false; // 防止 TextWatcher  预设填充时触发
    
    // 车门联动UI控件
    private LinearLayout doorLinkageSectionLayout; // 车门联动区域
    private SwitchMaterial doorLinkageSwitch; // 车门联动ВклВыкл

    // 全景影像避让UI控件
    private SwitchMaterial avmAvoidanceSwitch;
    private LinearLayout avmAvoidanceDetailLayout;
    private EditText avmAvoidanceActivityEditText;

    // 转 к 灯触发log预设方案
    private static final String[][] TURN_SIGNAL_PRESETS = {
        // { presetId, leftKeyword, rightKeyword }
        { "xinghan7", "left front turn signal:1", "right front turn signal:1" },
    };

    private TextView carApiStatusText;

    private SwitchMaterial blindSpotGlobalSwitch;
    private android.widget.LinearLayout subFeaturesContainer;
    private SwitchMaterial secondaryBlindSpotSwitch;
    private Button adjustSecondaryBlindSpotWindowButton;
    private SwitchMaterial mockFloatingSwitch;
    private SwitchMaterial floatingWindowAnimationSwitch;
    private RadioGroup statusBarStyleGroup;
    private View statusBarColorPreview;
    private Button pickStatusBarColorButton;
    private SeekBar statusBarOpacitySeekBar;
    private TextView tvStatusBarOpacityValue;
    private SwitchMaterial blindSpotCorrectionSwitch;
    private Button adjustBlindSpotCorrectionButton;
    private SwitchMaterial mainFloatingAspectRatioLockSwitch;
    private SwitchMaterial mainFloatingLongPressDragSwitch;
    private Button resetMainFloatingButton;
    private Button logcatDebugButton;
    private android.widget.EditText logFilterEditText;
    private Button menuButton;
    private Button homeButton;

    private AppConfig appConfig;
    private boolean disclaimerDialogShown = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_secondary_display_settings, container, false);
        appConfig = new AppConfig(requireContext());
        initViews(view);
        loadSettings();
        setupListeners();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        maybeShowDisclaimerDialog();
    }

    private void initViews(View view) {
        // 全局ВклВыкл
        blindSpotGlobalSwitch = view.findViewById(R.id.switch_blind_spot_global);
        subFeaturesContainer = view.findViewById(R.id.blind_spot_sub_features_container);

        openLabButton = view.findViewById(R.id.btn_open_lab);

        // 转 к 灯联动
        turnSignalLinkageSwitch = view.findViewById(R.id.switch_turn_signal_linkage);
        turnSignalTimeoutSeekBar = view.findViewById(R.id.seekbar_turn_signal_timeout);
        tvTurnSignalTimeout = view.findViewById(R.id.tv_turn_signal_timeout_value);
        turnSignalPresetGroup = view.findViewById(R.id.rg_turn_signal_preset);
        customKeywordsLayout = view.findViewById(R.id.layout_turn_signal_custom_keywords);
        turnSignalLeftLogEditText = view.findViewById(R.id.et_turn_signal_left_log);
        turnSignalRightLogEditText = view.findViewById(R.id.et_turn_signal_right_log);

        secondaryBlindSpotSwitch = view.findViewById(R.id.switch_secondary_blind_spot_display);
        adjustSecondaryBlindSpotWindowButton = view.findViewById(R.id.btn_adjust_secondary_blind_spot_window);
        
        // 车门联动UIинициализация
        doorLinkageSectionLayout = view.findViewById(R.id.ll_door_linkage_section);
        doorLinkageSwitch = view.findViewById(R.id.switch_door_linkage);

        // 全景影像避让UIинициализация
        avmAvoidanceSwitch = view.findViewById(R.id.switch_avm_avoidance);
        avmAvoidanceDetailLayout = view.findViewById(R.id.layout_avm_avoidance_detail);
        avmAvoidanceActivityEditText = view.findViewById(R.id.et_avm_avoidance_activity);

        mockFloatingSwitch = view.findViewById(R.id.switch_mock_floating);
        floatingWindowAnimationSwitch = view.findViewById(R.id.switch_floating_window_animation);
        statusBarStyleGroup = view.findViewById(R.id.rg_status_bar_style);
        statusBarColorPreview = view.findViewById(R.id.view_status_bar_color_preview);
        pickStatusBarColorButton = view.findViewById(R.id.btn_pick_status_bar_color);
        statusBarOpacitySeekBar = view.findViewById(R.id.seekbar_status_bar_opacity);
        tvStatusBarOpacityValue = view.findViewById(R.id.tv_status_bar_opacity_value);

        blindSpotCorrectionSwitch = view.findViewById(R.id.switch_blind_spot_correction);
        adjustBlindSpotCorrectionButton = view.findViewById(R.id.btn_adjust_blind_spot_correction);

        mainFloatingAspectRatioLockSwitch = view.findViewById(R.id.switch_main_floating_aspect_ratio_lock);
        mainFloatingLongPressDragSwitch = view.findViewById(R.id.switch_main_floating_long_press_drag);
        resetMainFloatingButton = view.findViewById(R.id.btn_reset_main_floating);

        carApiStatusText = view.findViewById(R.id.tv_car_api_status);

        logcatDebugButton = view.findViewById(R.id.btn_logcat_debug);
        logFilterEditText = view.findViewById(R.id.et_log_filter);
        menuButton = view.findViewById(R.id.btn_menu);
        homeButton = view.findViewById(R.id.btn_home);

        // загрузка抖音二维码
        ImageView douyinQrCode = view.findViewById(R.id.img_douyin_qrcode);
        loadAssetImage(douyinQrCode, "douyin.jpg");

        // загрузкаВторой шт.抖音二维码（阿卜IT老师)
        ImageView douyinQrCode2 = view.findViewById(R.id.img_douyin_qrcode2);
        loadAssetImage(douyinQrCode2, "douyin2.png");
    }

    private void loadAssetImage(ImageView imageView, String assetName) {
        try {
            AssetManager am = requireContext().getAssets();
            try (InputStream is = am.open(assetName)) {
                imageView.setImageBitmap(BitmapFactory.decodeStream(is));
            }
        } catch (Exception e) {
            imageView.setVisibility(View.GONE);
        }
    }

    private void loadSettings() {
        // 全局ВклВыкл
        boolean globalEnabled = appConfig.isBlindSpotGlobalEnabled();
        blindSpotGlobalSwitch.setChecked(globalEnabled);
        updateSubFeaturesVisibility(globalEnabled);

        // 转 к 灯联动
        turnSignalLinkageSwitch.setChecked(appConfig.isTurnSignalLinkageEnabled());
        int timeout = appConfig.getTurnSignalTimeout();
        turnSignalTimeoutSeekBar.setProgress(timeout);
        tvTurnSignalTimeout.setText(timeout + "s");
        String currentLeft = appConfig.getTurnSignalLeftTriggerLog();
        String currentRight = appConfig.getTurnSignalRightTriggerLog();
        turnSignalLeftLogEditText.setText(currentLeft);
        turnSignalRightLogEditText.setText(currentRight);

        // 根据触发режим и ТекущийВыкл键词匹配预设
        if (appConfig.isCarSignalManagerTriggerMode()) {
            // CarSignalManager режим：根据Сохранить 预设ВыбратьВосстановление RadioButton
            String presetSelection = appConfig.getTurnSignalPresetSelection();
            if ("boyue_l".equals(presetSelection)) {
                turnSignalPresetGroup.check(R.id.rb_preset_boyue_l);
            } else {
                // По умолчанию选 L6/L7
                turnSignalPresetGroup.check(R.id.rb_preset_l6l7);
            }
            customKeywordsLayout.setVisibility(View.GONE);
            carApiStatusText.setVisibility(View.VISIBLE);
            carApiStatusText.setText("Статус CarSignalManager: проверка...");
            checkCarSignalManagerConnection();
        } else if (appConfig.isVhalGrpcTriggerMode()) {
            turnSignalPresetGroup.check(R.id.rb_preset_car_api);
            customKeywordsLayout.setVisibility(View.GONE);
            carApiStatusText.setVisibility(View.VISIBLE);
            carApiStatusText.setText("Статус Vehicle API: проверка...");
            checkVhalGrpcConnection();
        } else {
            int matchedPreset = findMatchingPreset(currentLeft, currentRight);
            if (matchedPreset == 0) {
                turnSignalPresetGroup.check(R.id.rb_preset_xinghan7);
                customKeywordsLayout.setVisibility(View.GONE);
            } else {
                turnSignalPresetGroup.check(R.id.rb_preset_custom);
                customKeywordsLayout.setVisibility(View.VISIBLE);
            }
            carApiStatusText.setVisibility(View.GONE);
        }

        secondaryBlindSpotSwitch.setChecked(appConfig.isSecondaryDisplayEnabled());

        mockFloatingSwitch.setChecked(appConfig.isMockTurnSignalFloatingEnabled());
        floatingWindowAnimationSwitch.setChecked(appConfig.isFloatingWindowAnimationEnabled());

        int statusBarStyle = appConfig.getBlindSpotStatusBarStyle();
        switch (statusBarStyle) {
            case BlindSpotStatusBarView.STYLE_OFF:           statusBarStyleGroup.check(R.id.rb_style_off); break;
            case BlindSpotStatusBarView.STYLE_COMET:         statusBarStyleGroup.check(R.id.rb_style_comet); break;
            case BlindSpotStatusBarView.STYLE_RIPPLE:        statusBarStyleGroup.check(R.id.rb_style_ripple); break;
            case BlindSpotStatusBarView.STYLE_GRADIENT_FILL: statusBarStyleGroup.check(R.id.rb_style_gradient_fill); break;
            case BlindSpotStatusBarView.STYLE_ARROW_RIPPLE:  statusBarStyleGroup.check(R.id.rb_style_arrow_ripple); break;
            default:                                         statusBarStyleGroup.check(R.id.rb_style_sequential); break;
        }

        updateColorPreview(appConfig.getBlindSpotStatusBarColor());

        int opacity = appConfig.getBlindSpotStatusBarBgOpacity();
        statusBarOpacitySeekBar.setProgress(opacity);
        tvStatusBarOpacityValue.setText(opacity + "%");

        blindSpotCorrectionSwitch.setChecked(appConfig.isBlindSpotCorrectionEnabled());

        mainFloatingAspectRatioLockSwitch.setChecked(appConfig.isMainFloatingAspectRatioLocked());
        mainFloatingLongPressDragSwitch.setChecked(appConfig.isMainFloatingLongPressDragEnabled());
        
        // 车门联动конфигурациязагрузка
        doorLinkageSwitch.setChecked(appConfig.isDoorLinkageEnabled());
        
        // 根据转 к 联动 车型Выбрать，决定 否显示车门联动区域
        updateDoorLinkageVisibility();

        // 全景影像避让конфигурациязагрузка
        boolean avmEnabled = appConfig.isAvmAvoidanceEnabled();
        avmAvoidanceSwitch.setChecked(avmEnabled);
        avmAvoidanceDetailLayout.setVisibility(avmEnabled ? View.VISIBLE : View.GONE);
        avmAvoidanceActivityEditText.setText(appConfig.getAvmAvoidanceActivity());

    }

    private void updateSubFeaturesVisibility(boolean globalEnabled) {
        // 全局ВклВыклЗакрыто时，隐藏所有子функция区域
        subFeaturesContainer.setVisibility(globalEnabled ? View.VISIBLE : View.GONE);
    }
    
    /**
     * 根据转 к 联动 车型Выбрать，обновление车门联动区域 可见性
     * ВыбратьGalaxyL6/L7、博越Lили车载API(E5/星舰7)时，显示车门联动ВклВыкл
     */
    private void updateDoorLinkageVisibility() {
        // проверка车型Выбрать
        String turnSignalPreset = appConfig.getTurnSignalPresetSelection();
        boolean supportsDoorLinkage = "l6l7".equals(turnSignalPreset)
                || "boyue_l".equals(turnSignalPreset)
                || "car_api".equals(turnSignalPreset);

        // поддержка车门联动 车型才显示（不依赖转 к 联动ВклВыкл)
        doorLinkageSectionLayout.setVisibility(supportsDoorLinkage ? View.VISIBLE : View.GONE);

        // Если 不应该显示（切换 до Другое车型)，автоматическиЗакрыто车门联动
        if (!supportsDoorLinkage && appConfig.isDoorLinkageEnabled()) {
            appConfig.setDoorLinkageEnabled(false);
            doorLinkageSwitch.setChecked(false);
        }
    }

    private void setupListeners() {
        // 全局ВклВыкл
        blindSpotGlobalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appConfig.setBlindSpotGlobalEnabled(isChecked);
            updateSubFeaturesVisibility(isChecked);
            if (!isChecked) {
                // Закрыто时，Остановка补盲Сервис
                requireContext().stopService(new android.content.Intent(requireContext(), BlindSpotService.class));
            } else {
                // Вкл启时，Если 有子функцияконфигурация，ЗапускСервис
                BlindSpotService.update(requireContext());
            }
        });

        openLabButton.setOnClickListener(v -> {
            if (getActivity() == null) return;
            androidx.fragment.app.FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, new BlindSpotLabFragment());
            transaction.addToBackStack(null);
            transaction.commit();
        });

        turnSignalLinkageSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !WakeUpHelper.hasOverlayPermission(requireContext())) {
                turnSignalLinkageSwitch.setChecked(false);
                Toast.makeText(requireContext(), "Сначала предоставьте разрешение на плавающее окно", Toast.LENGTH_SHORT).show();
                WakeUpHelper.requestOverlayPermission(requireContext());
                return;
            }
            appConfig.setTurnSignalLinkageEnabled(isChecked);
            BlindSpotService.update(requireContext());
        });

        turnSignalTimeoutSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvTurnSignalTimeout.setText(progress + "s");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                appConfig.setTurnSignalTimeout(seekBar.getProgress());
                BlindSpotService.update(requireContext());
            }
        });

        // 预设方案Выбрать
        turnSignalPresetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_preset_l6l7 || checkedId == R.id.rb_preset_boyue_l) {
                // CarSignalManager API режим
                customKeywordsLayout.setVisibility(View.GONE);
                carApiStatusText.setVisibility(View.VISIBLE);
                carApiStatusText.setText("Статус CarSignalManager: проверка...");
                appConfig.setTurnSignalTriggerMode(AppConfig.TRIGGER_MODE_CAR_SIGNAL_MANAGER);
                
                // Сохранить具体Выбрать 预设（博越L или L6/L7)
                if (checkedId == R.id.rb_preset_boyue_l) {
                    appConfig.setTurnSignalPresetSelection("boyue_l");
                } else {
                    appConfig.setTurnSignalPresetSelection("l6l7");
                }
                
                // обновление车门联动区域可见性
                updateDoorLinkageVisibility();
                
                checkCarSignalManagerConnection();
                BlindSpotService.update(requireContext());
            } else if (checkedId == R.id.rb_preset_car_api) {
                // 车辆API режим
                customKeywordsLayout.setVisibility(View.GONE);
                carApiStatusText.setVisibility(View.VISIBLE);
                carApiStatusText.setText("Статус Vehicle API: проверка...");
                appConfig.setTurnSignalTriggerMode(AppConfig.TRIGGER_MODE_VHAL_GRPC);
                appConfig.setTurnSignalPresetSelection("car_api");
                
                // обновление车门联动区域可见性（会автоматически处理Закрыто逻辑)
                updateDoorLinkageVisibility();
                
                checkVhalGrpcConnection();
                BlindSpotService.update(requireContext());
            } else {
                // Logcat режим
                carApiStatusText.setVisibility(View.GONE);
                appConfig.setTurnSignalTriggerMode(AppConfig.TRIGGER_MODE_LOGCAT);
                
                // Сохранить具体Выбрать 预设
                if (checkedId == R.id.rb_preset_custom) {
                    appConfig.setTurnSignalPresetSelection("custom");
                    customKeywordsLayout.setVisibility(View.VISIBLE);
                } else if (checkedId == R.id.rb_preset_xinghan7) {
                    appConfig.setTurnSignalPresetSelection("xinghan7");
                    customKeywordsLayout.setVisibility(View.GONE);
                    applyPreset(0);
                } else {
                    appConfig.setTurnSignalPresetSelection("e5");
                    customKeywordsLayout.setVisibility(View.GONE);
                    applyPreset(1);
                }
                
                // обновление车门联动区域可见性（会автоматически处理Закрыто逻辑)
                updateDoorLinkageVisibility();
                
                BlindSpotService.update(requireContext());
            }
        });

        android.text.TextWatcher turnSignalLogWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isUpdatingFromPreset) return; // 预设填充时不触发Сохранить
                if (turnSignalLeftLogEditText.getEditableText() == s) {
                    appConfig.setTurnSignalCustomLeftTriggerLog(s.toString());
                } else if (turnSignalRightLogEditText.getEditableText() == s) {
                    appConfig.setTurnSignalCustomRightTriggerLog(s.toString());
                } else {
                    return;
                }
                BlindSpotService.update(requireContext());
            }
        };
        turnSignalLeftLogEditText.addTextChangedListener(turnSignalLogWatcher);
        turnSignalRightLogEditText.addTextChangedListener(turnSignalLogWatcher);

        secondaryBlindSpotSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !WakeUpHelper.hasOverlayPermission(requireContext())) {
                secondaryBlindSpotSwitch.setChecked(false);
                Toast.makeText(requireContext(), "Сначала предоставьте разрешение на плавающее окно", Toast.LENGTH_SHORT).show();
                WakeUpHelper.requestOverlayPermission(requireContext());
                return;
            }
            appConfig.setSecondaryDisplayEnabled(isChecked);
            BlindSpotService.update(requireContext());
        });

        floatingWindowAnimationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appConfig.setFloatingWindowAnimationEnabled(isChecked);
        });

        statusBarStyleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int style;
            if (checkedId == R.id.rb_style_off)            style = BlindSpotStatusBarView.STYLE_OFF;
            else if (checkedId == R.id.rb_style_comet)         style = BlindSpotStatusBarView.STYLE_COMET;
            else if (checkedId == R.id.rb_style_ripple)        style = BlindSpotStatusBarView.STYLE_RIPPLE;
            else if (checkedId == R.id.rb_style_gradient_fill) style = BlindSpotStatusBarView.STYLE_GRADIENT_FILL;
            else if (checkedId == R.id.rb_style_arrow_ripple)  style = BlindSpotStatusBarView.STYLE_ARROW_RIPPLE;
            else                                               style = BlindSpotStatusBarView.STYLE_SEQUENTIAL;
            appConfig.setBlindSpotStatusBarStyle(style);
            BlindSpotService.update(requireContext());
        });

        pickStatusBarColorButton.setOnClickListener(v -> showColorPickerDialog());

        statusBarOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvStatusBarOpacityValue.setText(progress + "%");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                appConfig.setBlindSpotStatusBarBgOpacity(seekBar.getProgress());
                BlindSpotService.update(requireContext());
            }
        });

        mockFloatingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !WakeUpHelper.hasOverlayPermission(requireContext())) {
                mockFloatingSwitch.setChecked(false);
                Toast.makeText(requireContext(), "Сначала предоставьте разрешение на плавающее окно", Toast.LENGTH_SHORT).show();
                WakeUpHelper.requestOverlayPermission(requireContext());
                return;
            }
            appConfig.setMockTurnSignalFloatingEnabled(isChecked);
            BlindSpotService.update(requireContext());
        });

        // ==================== 车门联动监听器 ====================
        
        doorLinkageSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !WakeUpHelper.hasOverlayPermission(requireContext())) {
                doorLinkageSwitch.setChecked(false);
                Toast.makeText(requireContext(), "Сначала предоставьте разрешение на плавающее окно", Toast.LENGTH_SHORT).show();
                WakeUpHelper.requestOverlayPermission(requireContext());
                return;
            }
            appConfig.setDoorLinkageEnabled(isChecked);
            BlindSpotService.update(requireContext());
        });

        blindSpotCorrectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appConfig.setBlindSpotCorrectionEnabled(isChecked);
            BlindSpotService.update(requireContext());
        });

        mainFloatingAspectRatioLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appConfig.setMainFloatingAspectRatioLocked(isChecked);
        });

        mainFloatingLongPressDragSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appConfig.setMainFloatingLongPressDragEnabled(isChecked);
        });

        resetMainFloatingButton.setOnClickListener(v -> {
            appConfig.resetMainFloatingBounds();
            BlindSpotService.update(requireContext());
            Toast.makeText(requireContext(), "Плавающее окно сброшено", Toast.LENGTH_SHORT).show();
        });

        adjustBlindSpotCorrectionButton.setOnClickListener(v -> {
            if (!WakeUpHelper.hasOverlayPermission(requireContext())) {
                Toast.makeText(requireContext(), "Сначала предоставьте разрешение на плавающее окно", Toast.LENGTH_SHORT).show();
                WakeUpHelper.requestOverlayPermission(requireContext());
                return;
            }
            if (getActivity() == null) return;
            androidx.fragment.app.FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, new BlindSpotCorrectionFragment());
            transaction.addToBackStack(null);
            transaction.commit();
        });

        adjustSecondaryBlindSpotWindowButton.setOnClickListener(v -> {
            if (!WakeUpHelper.hasOverlayPermission(requireContext())) {
                Toast.makeText(requireContext(), "Сначала предоставьте разрешение на плавающее окно", Toast.LENGTH_SHORT).show();
                WakeUpHelper.requestOverlayPermission(requireContext());
                return;
            }
            if (getActivity() == null) return;
            androidx.fragment.app.FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, new SecondaryBlindSpotAdjustFragment());
            transaction.addToBackStack(null);
            transaction.commit();
        });

        // 调整车门副屏悬浮窗Позиция按钮
        logcatDebugButton.setOnClickListener(v -> {
            String keyword = logFilterEditText.getText().toString().trim();
            if (keyword.isEmpty()) {
                // 没有ВвестиВыкл键词时弹窗Уведомление
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Cam_MaterialAlertDialog)
                    .setTitle("Подсказка")
                    .setMessage("Не Ввести过滤Выкл键字， д.志量可能很大，可能导致界面卡顿。\n\n建议ВвестиВыкл键字进行过滤， 否продолжить？")
                    .setPositiveButton("Продолжить", (dialog, which) -> {
                        android.content.Intent intent = new android.content.Intent(requireContext(), LogcatViewerActivity.class);
                        intent.putExtra("filter_keyword", "");
                        startActivity(intent);
                    })
                    .setNegativeButton("Назад", null)
                    .show();
            } else {
                android.content.Intent intent = new android.content.Intent(requireContext(), LogcatViewerActivity.class);
                intent.putExtra("filter_keyword", keyword);
                startActivity(intent);
            }
        });

        menuButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).toggleDrawer();
            }
        });

        homeButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).goToRecordingInterface();
            }
        });

        // ==================== 全景影像避让监听器 ====================

        avmAvoidanceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appConfig.setAvmAvoidanceEnabled(isChecked);
            avmAvoidanceDetailLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            BlindSpotService.update(requireContext());
        });

        avmAvoidanceActivityEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String activity = s.toString().trim();
                if (!activity.isEmpty()) {
                    appConfig.setAvmAvoidanceActivity(activity);
                    BlindSpotService.update(requireContext());
                }
            }
        });

    }

    /**
     * 根据ТекущийВыкл键词匹配预设方案
     * @return 预设索引（0=星舰7)，-1 表示自定义
     */
    private int findMatchingPreset(String leftKeyword, String rightKeyword) {
        if (leftKeyword == null || rightKeyword == null) return -1;
        for (int i = 0; i < TURN_SIGNAL_PRESETS.length; i++) {
            if (TURN_SIGNAL_PRESETS[i][1].equals(leftKeyword) && TURN_SIGNAL_PRESETS[i][2].equals(rightKeyword)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Приложение预设方案：填充Выкл键词并Сохранитьконфигурация
     */
    private void applyPreset(int presetIndex) {
        if (presetIndex < 0 || presetIndex >= TURN_SIGNAL_PRESETS.length) return;
        String leftKeyword = TURN_SIGNAL_PRESETS[presetIndex][1];
        String rightKeyword = TURN_SIGNAL_PRESETS[presetIndex][2];

        isUpdatingFromPreset = true;
        turnSignalLeftLogEditText.setText(leftKeyword);
        turnSignalRightLogEditText.setText(rightKeyword);
        isUpdatingFromPreset = false;

        appConfig.setTurnSignalCustomLeftTriggerLog(leftKeyword);
        appConfig.setTurnSignalCustomRightTriggerLog(rightKeyword);
        BlindSpotService.update(requireContext());
    }

    private void maybeShowDisclaimerDialog() {
        if (disclaimerDialogShown) return;
        if (appConfig == null) return;
        if (appConfig.isBlindSpotDisclaimerAccepted()) return;
        disclaimerDialogShown = true;
        new BlindSpotDisclaimerDialogFragment().show(getChildFragmentManager(), "blind_spot_disclaimer");
    }

    /**
     * 异步проверка车辆API СервисПодключениеСтатус并обновление UI
     */
    private void checkVhalGrpcConnection() {
        if (carApiStatusText == null) return;
        carApiStatusText.setText("Статус Vehicle API: проверка...");
        carApiStatusText.setTextColor(getResources().getColor(R.color.text_secondary, null));

        new Thread(() -> {
            boolean reachable = VhalSignalObserver.testConnection();
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    if (carApiStatusText == null) return;
                    if (reachable) {
                        carApiStatusText.setText("Vehicle API: ✓ Подключено");
                        carApiStatusText.setTextColor(0xFF4CAF50); // green
                    } else {
                        carApiStatusText.setText("Vehicle API: ✗ Сервис недоступен");
                        carApiStatusText.setTextColor(0xFFF44336); // red
                    }
                });
            }
        }).start();
    }

    /**
     * 异步проверка CarSignalManager СервисПодключениеСтатус并обновление UI
     */
    private void checkCarSignalManagerConnection() {
        if (carApiStatusText == null) return;
        carApiStatusText.setText("Статус CarSignalManager: проверка...");
        carApiStatusText.setTextColor(getResources().getColor(R.color.text_secondary, null));

        new Thread(() -> {
            boolean reachable = CarSignalManagerObserver.testConnection(requireContext());
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    if (carApiStatusText == null) return;
                    if (reachable) {
                        carApiStatusText.setText("CarSignalManager СервисСтатус: ✓ Подключено");
                        carApiStatusText.setTextColor(0xFF4CAF50); // green
                    } else {
                        carApiStatusText.setText("CarSignalManager СервисСтатус: ✗ Сервис недоступен");
                        carApiStatusText.setTextColor(0xFFF44336); // red
                    }
                });
            }
        }).start();
    }

    /**
     * проверка车门APIПодключениеСтатус
     */

    private void updateColorPreview(int color) {
        if (statusBarColorPreview == null) return;
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        gd.setColor(color);
        gd.setStroke((int) (1.5f * getResources().getDisplayMetrics().density), 0x40FFFFFF);
        statusBarColorPreview.setBackground(gd);
    }

    private void showColorPickerDialog() {
        ColorPickerView picker = new ColorPickerView(requireContext());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        picker.setPadding(pad, pad, pad, pad);
        picker.setColor(appConfig.getBlindSpotStatusBarColor());

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Выбор цвета анимации")
                .setView(picker)
                .setPositiveButton("ОК", (dialog, which) -> {
                    int color = picker.getColor();
                    appConfig.setBlindSpotStatusBarColor(color);
                    updateColorPreview(color);
                    BlindSpotService.update(requireContext());
                })
                .setNegativeButton("Отмена", null)
                .setNeutralButton("По умолчанию", (dialog, which) -> {
                    int defaultColor = 0xFFFFBF40;
                    appConfig.setBlindSpotStatusBarColor(defaultColor);
                    updateColorPreview(defaultColor);
                    BlindSpotService.update(requireContext());
                })
                .show();
    }
}
