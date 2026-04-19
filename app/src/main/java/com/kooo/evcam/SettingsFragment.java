package com.kooo.evcam;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.switchmaterial.SwitchMaterial;

import android.net.Uri;

import java.io.File;
import java.util.List;

/**
 * 软件Настройки界面 Fragment
 */
public class SettingsFragment extends Fragment {

    private SwitchMaterial debugSwitch;
    private Button saveLogsButton;
    private Button uploadLogsButton;
    private LinearLayout logButtonsLayout;
    private SwitchMaterial autoStartSwitch;
    private SwitchMaterial autoStartRecordingSwitch;
    private SwitchMaterial screenOffRecordingSwitch;
    private LinearLayout screenOffRecordingLayout;
    // 定时保活 и 防止休眠改为始终Вкл启，无需用户Настройки（车机必需)
    // private SwitchMaterial keepAliveSwitch;
    // private SwitchMaterial preventSleepSwitch;
    private SwitchMaterial recordingStatsSwitch;
    private SwitchMaterial timestampWatermarkSwitch;
    
    // 预览画面矫正相Выкл
    private SwitchMaterial previewCorrectionSwitch;
    private LinearLayout previewCorrectionButtonsLayout;
    private Button openPreviewCorrectionFloatingButton;
    private Button resetPreviewCorrectionButton;
    private PreviewCorrectionFloatingWindow previewCorrectionFloatingWindow;
    
    // 鱼眼矫正相Выкл
    private SwitchMaterial fisheyeCorrectionSwitch;
    private LinearLayout fisheyeCorrectionButtonsLayout;
    private Button openFisheyeCorrectionFloatingButton;
    private Button resetFisheyeCorrectionButton;
    private FisheyeCorrectionFloatingWindow fisheyeCorrectionFloatingWindow;
    
    private AppConfig appConfig;
    
    // 悬浮窗相Выкл
    private SwitchMaterial floatingWindowSwitch;
    private LinearLayout floatingWindowSettingsLayout;
    private Spinner floatingWindowSizeSpinner;
    private SeekBar floatingWindowAlphaSeekBar;
    private TextView floatingWindowAlphaText;
    private static final String[] FLOATING_SIZE_OPTIONS = {"Очень маленький", "XS", "Маленький", "Средний", "Большой", "Очень большой", "XL", "XL", "PLUS", "MAX"};
    private boolean isInitializingFloatingSize = false;
    private int lastAppliedFloatingSize = -1;  // 记录 разПриложение 大小，避免重复触发
    
    // 车型конфигурация相Выкл
    private Spinner carModelSpinner;
    private Button customCameraConfigButton;
    private static final String[] CAR_MODEL_OPTIONS = {"Galaxy E5", "GalaxyE5-Мульти-кнопки", "GalaxyL6/L7", "GalaxyL7-Мульти-кнопки", "26 Starship7", "Телефон", "Своя модель", "Мульти-камерный вид"};
    private boolean isInitializingCarModel = false;
    private String lastAppliedCarModel = null;
    
    // Записьрежимконфигурация相Выкл
    private Spinner recordingModeSpinner;
    private TextView recordingModeDescText;
    private static final String[] RECORDING_MODE_OPTIONS = {"Авто (рекомендуется)", "MediaRecorder", "MediaCodec"};
    private boolean isInitializingRecordingMode = false;
    private String lastAppliedRecordingMode = null;
    
    // 分时长конфигурация相Выкл
    private Spinner segmentDurationSpinner;
    private static final String[] SEGMENT_DURATION_OPTIONS = {"1 мин", "3 мин", "5 мин"};
    private boolean isInitializingSegmentDuration = false;
    private int lastAppliedSegmentDuration = -1;
    
    // ХранилищеПозицияконфигурация相Выкл
    private Spinner storageLocationSpinner;
    private TextView storageLocationDescText;
    private Button storageDebugButton;
    private String[] storageLocationOptions;
    private boolean isInitializingStorageLocation = false;
    private String lastAppliedStorageLocation = null;
    private boolean hasExternalSdCard = false;
    
    
    // ХранилищеОчистка конфигурация相Выкл
    private EditText videoStorageLimitEdit;
    private EditText photoStorageLimitEdit;
    private TextView videoUsedSizeText;
    private TextView photoUsedSizeText;
    private boolean isInitializingStorageCleanup = false;
    
    // ЗаписьКамераВыбратьконфигурация相Выкл
    private android.widget.CheckBox cbRecordCameraFront;
    private android.widget.CheckBox cbRecordCameraBack;
    private android.widget.CheckBox cbRecordCameraLeft;
    private android.widget.CheckBox cbRecordCameraRight;
    private boolean isInitializingRecordingCameraSelection = false;
    
    // 版本обновление相Выкл
    private TextView currentVersionText;
    private Button checkUpdateButton;
    private VersionUpdateManager versionUpdateManager;

    // 定制键唤醒相Выкл
    private SwitchMaterial customKeyWakeupSwitch;
    private LinearLayout customKeyWakeupDetailLayout;
    private EditText customKeySpeedThresholdEditText;
    private EditText customKeySpeedPropIdEditText;
    private EditText customKeyButtonPropIdEditText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // инициализация控件
        debugSwitch = view.findViewById(R.id.switch_debug_to_info);
        saveLogsButton = view.findViewById(R.id.btn_save_logs);
        uploadLogsButton = view.findViewById(R.id.btn_upload_logs);
        logButtonsLayout = view.findViewById(R.id.layout_log_buttons);
        Button menuButton = view.findViewById(R.id.btn_menu);
        Button homeButton = view.findViewById(R.id.btn_home);

        // Настройки菜单按钮点击事件
        menuButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                DrawerLayout drawerLayout = getActivity().findViewById(R.id.drawer_layout);
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            }
        });

        // 主页按钮 - 返回预览界面
        homeButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).goToRecordingInterface();
            }
        });

        // инициализацияПриложениеконфигурация
        if (getContext() != null) {
            appConfig = new AppConfig(getContext());
            
            // инициализацияDebugВклВыклСтатус
            debugSwitch.setChecked(AppLog.isDebugToInfoEnabled(getContext()));
            
            // 根据 Debug Статус显示или隐藏Сохранить д.志按钮
            updateSaveLogsButtonVisibility(debugSwitch.isChecked());
            
            // инициализация车型конфигурация
            initCarModelConfig(view);
            
            // инициализацияЗаписьрежимконфигурация
            initRecordingModeConfig(view);
            
            // инициализация分时长конфигурация
            initSegmentDurationConfig(view);
            
            // инициализацияЗаписьКамераВыбратьконфигурация
            initRecordingCameraSelectionConfig(view);
            
            // инициализацияХранилищеПозицияконфигурация
            initStorageLocationConfig(view);
            
            // инициализацияХранилищеОчистка конфигурация
            initStorageCleanupConfig(view);
        }

        // НастройкиDebugВклВыкл监听器
        debugSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null) {
                AppLog.setDebugToInfoEnabled(getContext(), isChecked);
                updateSaveLogsButtonVisibility(isChecked);
                String message = isChecked ? "Debug logs will show as info" : "Debug logs will show as debug";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        // НастройкиСохранить д.志按钮监听器
        saveLogsButton.setOnClickListener(v -> {
            if (getContext() != null) {
                File logFile = AppLog.saveLogsToFile(getContext());
                if (logFile != null) {
                    Toast.makeText(getContext(), "Logs saved to: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getContext(), "Failed to save logs", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Настройки一键传 д.志按钮监听器
        uploadLogsButton.setOnClickListener(v -> {
            if (getContext() != null && appConfig != null) {
                // проверка 否Настройки设备名称
                if (!appConfig.hasDeviceNickname()) {
                    // 首 раз传，显示Ввести框
                    showDeviceNicknameInputDialog();
                } else {
                    // 有设备名称，显示Подтвердить 话框
                    showUploadConfirmDialog(appConfig.getDeviceNickname());
                }
            }
        });

        // инициализация版本обновлениефункция
        initVersionUpdate(view);
        
        // инициализацияиспользованиеУведомление入口
        Button btnUsageGuide = view.findViewById(R.id.btn_usage_guide);
        btnUsageGuide.setOnClickListener(v -> showUsageGuideDialog());

        // инициализацияРазрешениеНастройки入口
        Button btnPermissionSettings = view.findViewById(R.id.btn_permission_settings);
        btnPermissionSettings.setOnClickListener(v -> openPermissionSettings());

        // инициализацияРазрешениеНастройки入口
        Button btnResolutionSettings = view.findViewById(R.id.btn_resolution_settings);
        btnResolutionSettings.setOnClickListener(v -> openResolutionSettings());

        // инициализацияЗаписьСтатус显示ВклВыкл
        recordingStatsSwitch = view.findViewById(R.id.switch_recording_stats);
        if (getContext() != null && appConfig != null) {
            recordingStatsSwitch.setChecked(appConfig.isRecordingStatsEnabled());
        }

        // НастройкиЗаписьСтатус显示ВклВыкл监听器
        recordingStatsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null && appConfig != null) {
                appConfig.setRecordingStatsEnabled(isChecked);
                String message = isChecked ? "Индикатор записи включён" : "Индикатор записи выключен";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                AppLog.d("SettingsFragment", message);
                
                // Уведомление MainActivity ОбновитьНастройки
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshRecordingStatsSettings();
                }
            }
        });

        // инициализация时间角标ВклВыкл
        timestampWatermarkSwitch = view.findViewById(R.id.switch_timestamp_watermark);
        if (getContext() != null && appConfig != null) {
            timestampWatermarkSwitch.setChecked(appConfig.isTimestampWatermarkEnabled());
        }

        // Настройки时间角标ВклВыкл监听器
        timestampWatermarkSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null && appConfig != null) {
                appConfig.setTimestampWatermarkEnabled(isChecked);
                String message = isChecked ? "Метка времени включена" : "Метка времени выключена";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                AppLog.d("SettingsFragment", message);
            }
        });

        // инициализация预览画面矫正
        previewCorrectionSwitch = view.findViewById(R.id.switch_preview_correction);
        previewCorrectionButtonsLayout = view.findViewById(R.id.layout_preview_correction_buttons);
        openPreviewCorrectionFloatingButton = view.findViewById(R.id.btn_open_preview_correction_floating);
        resetPreviewCorrectionButton = view.findViewById(R.id.btn_reset_preview_correction);
        if (getContext() != null && appConfig != null) {
            boolean correctionEnabled = appConfig.isPreviewCorrectionEnabled();
            previewCorrectionSwitch.setChecked(correctionEnabled);
            previewCorrectionButtonsLayout.setVisibility(correctionEnabled ? View.VISIBLE : View.GONE);
        }
        previewCorrectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null && appConfig != null) {
                appConfig.setPreviewCorrectionEnabled(isChecked);
                previewCorrectionButtonsLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                // Обновить预览
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity != null) {
                    mainActivity.refreshPreviewCorrection();
                }
                String message = isChecked ? "Коррекция предпросмотра включена" : "Коррекция предпросмотра выключена";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        openPreviewCorrectionFloatingButton.setOnClickListener(v -> {
            if (getContext() == null) return;
            if (!WakeUpHelper.hasOverlayPermission(requireContext())) {
                Toast.makeText(requireContext(), "Сначала предоставьте разрешение на плавающее окно", Toast.LENGTH_SHORT).show();
                WakeUpHelper.requestOverlayPermission(requireContext());
                return;
            }
            // 先回 до 主界面再открыть悬浮窗，方便实时预览
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                mainActivity.goToRecordingInterface();
                mainActivity.showPreviewCorrectionFloating();
            }
        });
        resetPreviewCorrectionButton.setOnClickListener(v -> {
            if (getContext() != null && appConfig != null) {
                appConfig.resetAllPreviewCorrection();
                Toast.makeText(getContext(), "Все параметры коррекции превью сброшены", Toast.LENGTH_SHORT).show();
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity != null) {
                    mainActivity.refreshPreviewCorrection();
                }
            }
        });

        // инициализация鱼眼矫正
        fisheyeCorrectionSwitch = view.findViewById(R.id.switch_fisheye_correction);
        fisheyeCorrectionButtonsLayout = view.findViewById(R.id.layout_fisheye_correction_buttons);
        openFisheyeCorrectionFloatingButton = view.findViewById(R.id.btn_open_fisheye_correction_floating);
        resetFisheyeCorrectionButton = view.findViewById(R.id.btn_reset_fisheye_correction);
        if (getContext() != null && appConfig != null) {
            boolean fisheyeEnabled = appConfig.isFisheyeCorrectionEnabled();
            fisheyeCorrectionSwitch.setChecked(fisheyeEnabled);
            fisheyeCorrectionButtonsLayout.setVisibility(fisheyeEnabled ? View.VISIBLE : View.GONE);
        }
        fisheyeCorrectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null && appConfig != null) {
                appConfig.setFisheyeCorrectionEnabled(isChecked);
                fisheyeCorrectionButtonsLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                // необходимо重建 session 来切换 Surface（直接 / GL 间层)
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity != null) {
                    mainActivity.refreshFisheyeCorrection();
                }
                String message = isChecked ? "Коррекция рыбьего глаза включена" : "Коррекция рыбьего глаза выключена";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        openFisheyeCorrectionFloatingButton.setOnClickListener(v -> {
            if (getContext() == null) return;
            if (!WakeUpHelper.hasOverlayPermission(requireContext())) {
                Toast.makeText(requireContext(), "Сначала предоставьте разрешение на плавающее окно", Toast.LENGTH_SHORT).show();
                WakeUpHelper.requestOverlayPermission(requireContext());
                return;
            }
            // 先回 до 主界面再открыть悬浮窗，方便实时预览
            MainActivity mainActivity = MainActivity.getInstance();
            if (mainActivity != null) {
                mainActivity.goToRecordingInterface();
                mainActivity.showFisheyeCorrectionFloating();
            }
        });
        resetFisheyeCorrectionButton.setOnClickListener(v -> {
            if (getContext() != null && appConfig != null) {
                appConfig.resetAllFisheyeCorrection();
                Toast.makeText(getContext(), "Все параметры коррекции рыбий глаз сброшены", Toast.LENGTH_SHORT).show();
                MainActivity mainActivity = MainActivity.getInstance();
                if (mainActivity != null) {
                    mainActivity.refreshFisheyeCorrection();
                }
            }
        });

        // инициализацияВкл机自ЗапускВклВыкл
        autoStartSwitch = view.findViewById(R.id.switch_auto_start);
        if (getContext() != null && appConfig != null) {
            autoStartSwitch.setChecked(appConfig.isAutoStartOnBoot());
        }

        // НастройкиВкл机自ЗапускВклВыкл监听器
        autoStartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null && appConfig != null) {
                appConfig.setAutoStartOnBoot(isChecked);
                String message = isChecked ? "Автозапуск при загрузке включён" : "Автозапуск при загрузке отключён";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                AppLog.d("SettingsFragment", message);
            }
        });

        // инициализацияЗапускавтоматическиЗаписьВклВыкл
        autoStartRecordingSwitch = view.findViewById(R.id.switch_auto_start_recording);
        if (getContext() != null && appConfig != null) {
            autoStartRecordingSwitch.setChecked(appConfig.isAutoStartRecording());
        }

        // инициализация息屏ЗаписьВклВыкл
        screenOffRecordingSwitch = view.findViewById(R.id.switch_screen_off_recording);
        screenOffRecordingLayout = view.findViewById(R.id.layout_screen_off_recording);
        if (getContext() != null && appConfig != null) {
            screenOffRecordingSwitch.setChecked(appConfig.isScreenOffRecordingEnabled());
            // 根据ЗапускавтоматическиЗапись Статус决定 否显示息屏ЗаписьВклВыкл
            updateScreenOffRecordingVisibility(appConfig.isAutoStartRecording());
        }

        // НастройкиЗапускавтоматическиЗаписьВклВыкл监听器
        autoStartRecordingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null && appConfig != null) {
                appConfig.setAutoStartRecording(isChecked);
                String message = isChecked ? "Автозапись включена, вступит в силу при следующем запуске" : "Автозапись отключена";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                AppLog.d("SettingsFragment", message);
                
                // обновление息屏ЗаписьВклВыкл 可见性
                updateScreenOffRecordingVisibility(isChecked);
            }
        });

        // Настройки息屏ЗаписьВклВыкл监听器
        screenOffRecordingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() != null && appConfig != null) {
                appConfig.setScreenOffRecordingEnabled(isChecked);
                String message = isChecked ? "Запись при выключенном экране включена" : "Запись при выключенном экране выключена, запись остановится через 10 сек.";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                AppLog.d("SettingsFragment", message);
            }
        });

        // 定时保活改为始终Вкл启（车机必需)，无需НастройкиВклВыкл
        // 隐藏定时保活ВклВыкл
        View keepAliveSwitch = view.findViewById(R.id.switch_keep_alive);
        if (keepAliveSwitch != null) {
            View parent = (View) keepAliveSwitch.getParent();
            if (parent != null) {
                parent.setVisibility(View.GONE);
            }
        }
        // 确保定时保活задачаЗапущено
        if (getContext() != null) {
            KeepAliveManager.startKeepAliveWork(getContext());
        }

        // 防止休眠改为始终Вкл启（车机必需)，无需НастройкиВклВыкл
        // WakeLock   CameraForegroundService автоматическиПолучение
        // 隐藏防止休眠ВклВыкл
        View preventSleepLayout = view.findViewById(R.id.switch_prevent_sleep);
        if (preventSleepLayout != null) {
            // 隐藏整 шт.布局（包括ВклВыкл и 说明文字)
            View parent = (View) preventSleepLayout.getParent();
            if (parent != null) {
                parent.setVisibility(View.GONE);
            }
        }

        // инициализация悬浮窗Настройки
        initFloatingWindowSettings(view);

        // инициализация定制键唤醒Настройки
        initCustomKeyWakeupSettings(view);
        
        // 沉浸式Статус栏совместимость
        View toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            final int originalPaddingTop = toolbar.getPaddingTop();
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
                int statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
                v.setPadding(v.getPaddingLeft(), statusBarHeight + originalPaddingTop, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(toolbar);
        }

        return view;
    }
    
    /**
     * 显示использованиеУведомление 话框
     */
    private void showUsageGuideDialog() {
        if (getContext() == null) return;

        // 创建自定义 话框
        android.app.Dialog dialog = new android.app.Dialog(getContext());
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_first_launch_guide);
        dialog.setCancelable(true);

        // Настройки 话框窗口属性
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            // Настройки背景透明（让圆角生效)
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            // Настройки 话框宽度
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            window.setAttributes(params);
        }

        // загрузка二维码Изображение
        android.widget.ImageView ivQrcode = dialog.findViewById(R.id.iv_qrcode);
        loadQrcodeImage(ivQrcode);

        // НастройкиПодтвердить按钮点击事件
        dialog.findViewById(R.id.btn_confirm).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * загрузка打赏二维码Изображение（URL经过混淆处理)
     */
    private void loadQrcodeImage(android.widget.ImageView imageView) {
        if (getActivity() == null || getContext() == null) return;
        
        // 根据屏幕密度动态Настройки二维码尺寸
        // НизкийDPI大屏设备использование更大尺寸，ВысокийDPI设备использование适尺寸
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;
        int screenWidthPx = dm.widthPixels;
        
        // 计算二维码尺寸（像素)
        // density: mdpi=1.0, hdpi=1.5, xhdpi=2.0, xxhdpi=3.0, xxxhdpi=4.0
        int qrcodeSizePx;
        if (density <= 1.0f) {
            // mdpi или更Низкий密度（大屏НизкийDPI设备)：использование屏幕宽度 25%
            qrcodeSizePx = (int) (screenWidthPx * 0.25f);
        } else if (density <= 1.5f) {
            // hdpi：использование屏幕宽度 22%
            qrcodeSizePx = (int) (screenWidthPx * 0.22f);
        } else if (density <= 2.0f) {
            // xhdpi：использование屏幕宽度 20%
            qrcodeSizePx = (int) (screenWidthPx * 0.20f);
        } else {
            // xxhdpi 及и выше（Высокий密度设备)：использование屏幕宽度 18%
            qrcodeSizePx = (int) (screenWidthPx * 0.18f);
        }
        
        // НастройкиImageView尺寸
        android.view.ViewGroup.LayoutParams params = imageView.getLayoutParams();
        params.width = qrcodeSizePx;
        params.height = qrcodeSizePx;
        imageView.setLayoutParams(params);
        
        // URL混淆Хранилище，防止 轻易изменение
        // 原始URL经过Base64编码后分Хранилище
        final String[] p = {
            "aHR0cHM6Ly9ldmNhbS5jaGF0d2Vi", // Первый
            "LmNsb3VkLzE3Njk0NzcxOTc4NTUu", // Второй  
            "anBn"                           // Третий
        };
        
        new Thread(() -> {
            try {
                //  групп合并解码URL
                String encoded = p[0] + p[1] + p[2];
                String url = new String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT));
                
                // скачиваниеИзображение
                java.net.URL imageUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) imageUrl.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoInput(true);
                conn.connect();
                
                java.io.InputStream is = conn.getInputStream();
                final android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();
                
                //  主线程обновлениеUI
                if (bitmap != null && getActivity() != null) {
                    getActivity().runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception e) {
                AppLog.e("SettingsFragment", "загрузка二维码ИзображениеОшибка: " + e.getMessage());
            }
        }).start();
    }

    /**
     * открытьРазрешениеНастройки页面
     */
    private void openPermissionSettings() {
        if (getActivity() == null) return;
        
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new PermissionSettingsFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }
    
    /**
     * инициализация悬浮窗Настройки
     */
    private void initFloatingWindowSettings(View view) {
        floatingWindowSwitch = view.findViewById(R.id.switch_floating_window);
        floatingWindowSettingsLayout = view.findViewById(R.id.layout_floating_window_settings);
        floatingWindowSizeSpinner = view.findViewById(R.id.spinner_floating_window_size);
        floatingWindowAlphaSeekBar = view.findViewById(R.id.seekbar_floating_window_alpha);
        floatingWindowAlphaText = view.findViewById(R.id.tv_floating_window_alpha_value);
        
        if (floatingWindowSwitch == null || getContext() == null || appConfig == null) {
            return;
        }
        
        // инициализация悬浮窗ВклВыклСтатус
        boolean floatingEnabled = appConfig.isFloatingWindowEnabled();
        floatingWindowSwitch.setChecked(floatingEnabled);
        updateFloatingWindowSettingsVisibility(floatingEnabled);
        
        // Настройки悬浮窗ВклВыкл监听器
        floatingWindowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() == null || appConfig == null) {
                return;
            }
            
            // проверкаРазрешение плавающего окна
            if (isChecked && !WakeUpHelper.hasOverlayPermission(getContext())) {
                Toast.makeText(getContext(), "Сначала предоставьте разрешение на плавающее окно в настройках", Toast.LENGTH_SHORT).show();
                buttonView.setChecked(false);
                WakeUpHelper.requestOverlayPermission(getContext());
                return;
            }
            
            appConfig.setFloatingWindowEnabled(isChecked);
            updateFloatingWindowSettingsVisibility(isChecked);
            
            if (isChecked) {
                FloatingWindowService.start(getContext());
                Toast.makeText(getContext(), "Плавающее окно открыто", Toast.LENGTH_SHORT).show();
                
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).broadcastCurrentRecordingState();
                }
            } else {
                FloatingWindowService.stop(getContext());
                Toast.makeText(getContext(), "Плавающее окно закрыто", Toast.LENGTH_SHORT).show();
            }
        });
        
        // инициализация悬浮窗大小Выбрать器
        initFloatingWindowSizeSpinner();
        
        // инициализация悬浮窗透明度滑块
        initFloatingWindowAlphaSeekBar();
    }

    /**
     * инициализация定制键唤醒Настройки
     */
    private void initCustomKeyWakeupSettings(View view) {
        customKeyWakeupSwitch = view.findViewById(R.id.switch_custom_key_wakeup);
        customKeyWakeupDetailLayout = view.findViewById(R.id.layout_custom_key_wakeup_detail);
        customKeySpeedThresholdEditText = view.findViewById(R.id.et_custom_key_speed_threshold);
        customKeySpeedPropIdEditText = view.findViewById(R.id.et_custom_key_speed_prop_id);
        customKeyButtonPropIdEditText = view.findViewById(R.id.et_custom_key_button_prop_id);

        if (customKeyWakeupSwitch == null || getContext() == null || appConfig == null) return;

        // загрузкаконфигурация
        boolean enabled = appConfig.isCustomKeyWakeupEnabled();
        customKeyWakeupSwitch.setChecked(enabled);
        customKeyWakeupDetailLayout.setVisibility(enabled ? View.VISIBLE : View.GONE);
        customKeySpeedThresholdEditText.setText(String.valueOf(appConfig.getCustomKeySpeedThreshold()));
        customKeySpeedPropIdEditText.setText(String.valueOf(appConfig.getCustomKeySpeedPropId()));
        customKeyButtonPropIdEditText.setText(String.valueOf(appConfig.getCustomKeyButtonPropId()));

        // ВклВыкл监听
        customKeyWakeupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (getContext() == null || appConfig == null) return;
            appConfig.setCustomKeyWakeupEnabled(isChecked);
            customKeyWakeupDetailLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            BlindSpotService.update(requireContext());
        });

        // 速度阈值监听
        customKeySpeedThresholdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float threshold = Float.parseFloat(s.toString());
                    appConfig.setCustomKeySpeedThreshold(threshold);
                } catch (NumberFormatException ignored) {}
            }
        });

        // 速度属性ID监听
        customKeySpeedPropIdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int propId = Integer.parseInt(s.toString());
                    appConfig.setCustomKeySpeedPropId(propId);
                } catch (NumberFormatException ignored) {}
            }
        });

        // 按钮属性ID监听
        customKeyButtonPropIdEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int propId = Integer.parseInt(s.toString());
                    appConfig.setCustomKeyButtonPropId(propId);
                } catch (NumberFormatException ignored) {}
            }
        });
    }
    
    /**
     * инициализация悬浮窗大小Выбрать器
     */
    private void initFloatingWindowSizeSpinner() {
        if (floatingWindowSizeSpinner == null || getContext() == null) {
            return;
        }
        
        isInitializingFloatingSize = true;
        
        // 记录ТекущийСохранить 大小值
        lastAppliedFloatingSize = appConfig.getFloatingWindowSize();
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                FLOATING_SIZE_OPTIONS
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        floatingWindowSizeSpinner.setAdapter(adapter);
        
        floatingWindowSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int sizeDp;
                String sizeName;
                switch (position) {
                    case 0:
                        sizeDp = AppConfig.FLOATING_SIZE_TINY;
                        sizeName = "Очень маленький";
                        break;
                    case 1:
                        sizeDp = AppConfig.FLOATING_SIZE_EXTRA_SMALL;
                        sizeName = "XS";
                        break;
                    case 2:
                        sizeDp = AppConfig.FLOATING_SIZE_SMALL;
                        sizeName = "Маленький";
                        break;
                    case 3:
                        sizeDp = AppConfig.FLOATING_SIZE_MEDIUM;
                        sizeName = "Средний";
                        break;
                    case 4:
                        sizeDp = AppConfig.FLOATING_SIZE_LARGE;
                        sizeName = "Большой";
                        break;
                    case 5:
                        sizeDp = AppConfig.FLOATING_SIZE_EXTRA_LARGE;
                        sizeName = "Очень большой";
                        break;
                    case 6:
                        sizeDp = AppConfig.FLOATING_SIZE_HUGE;
                        sizeName = "XL";
                        break;
                    case 7:
                        sizeDp = AppConfig.FLOATING_SIZE_GIANT;
                        sizeName = "XL";
                        break;
                    case 8:
                        sizeDp = AppConfig.FLOATING_SIZE_PLUS;
                        sizeName = "PLUS";
                        break;
                    default:
                        sizeDp = AppConfig.FLOATING_SIZE_MAX;
                        sizeName = "MAX";
                        break;
                }
                
                // инициализация阶不处理
                if (isInitializingFloatingSize) {
                    return;
                }
                
                //  и  разПриложение 值相同，不重复处理
                if (sizeDp == lastAppliedFloatingSize) {
                    return;
                }
                
                lastAppliedFloatingSize = sizeDp;
                appConfig.setFloatingWindowSize(sizeDp);
                
                if (getContext() != null && appConfig.isFloatingWindowEnabled()) {
                    FloatingWindowService.sendUpdateFloatingWindow(getContext());
                    Toast.makeText(getContext(), "Размер плавающего окна: " + sizeName, Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        // 根据ТекущийСохранить 尺寸确定选项
        int currentSize = appConfig.getFloatingWindowSize();
        int selectedIndex;
        if (currentSize <= AppConfig.FLOATING_SIZE_TINY) {
            selectedIndex = 0;  // 超小
        } else if (currentSize <= AppConfig.FLOATING_SIZE_EXTRA_SMALL) {
            selectedIndex = 1;  // XS
        } else if (currentSize <= AppConfig.FLOATING_SIZE_SMALL) {
            selectedIndex = 2;  // 小
        } else if (currentSize <= AppConfig.FLOATING_SIZE_MEDIUM) {
            selectedIndex = 3;  // 
        } else if (currentSize <= AppConfig.FLOATING_SIZE_LARGE) {
            selectedIndex = 4;  // 大
        } else if (currentSize <= AppConfig.FLOATING_SIZE_EXTRA_LARGE) {
            selectedIndex = 5;  // 超大
        } else if (currentSize <= AppConfig.FLOATING_SIZE_HUGE) {
            selectedIndex = 6;  // XL
        } else if (currentSize <= AppConfig.FLOATING_SIZE_GIANT) {
            selectedIndex = 7;  // XL
        } else if (currentSize <= AppConfig.FLOATING_SIZE_PLUS) {
            selectedIndex = 8;  // PLUS
        } else {
            selectedIndex = 9;  // MAX
        }
        floatingWindowSizeSpinner.setSelection(selectedIndex);
        
        floatingWindowSizeSpinner.post(() -> {
            isInitializingFloatingSize = false;
        });
    }
    
    /**
     * инициализация悬浮窗透明度滑块
     */
    private void initFloatingWindowAlphaSeekBar() {
        if (floatingWindowAlphaSeekBar == null || floatingWindowAlphaText == null || getContext() == null) {
            return;
        }
        
        floatingWindowAlphaSeekBar.setMax(80);
        
        int currentAlpha = appConfig.getFloatingWindowAlpha();
        floatingWindowAlphaSeekBar.setProgress(currentAlpha - 20);
        floatingWindowAlphaText.setText(currentAlpha + "%");
        
        floatingWindowAlphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int alpha = progress + 20;
                floatingWindowAlphaText.setText(alpha + "%");
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int alpha = seekBar.getProgress() + 20;
                appConfig.setFloatingWindowAlpha(alpha);
                
                if (getContext() != null && appConfig.isFloatingWindowEnabled()) {
                    FloatingWindowService.sendUpdateFloatingWindow(getContext());
                }
            }
        });
    }
    
    /**
     * обновление悬浮窗Настройки区域 可见性
     */
    private void updateFloatingWindowSettingsVisibility(boolean visible) {
        if (floatingWindowSettingsLayout != null) {
            floatingWindowSettingsLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * обновление息屏ЗаписьВклВыкл 可见性
     * только当ЗапускавтоматическиЗаписьВкл启时才显示
     */
    private void updateScreenOffRecordingVisibility(boolean autoStartRecordingEnabled) {
        if (screenOffRecordingLayout != null) {
            screenOffRecordingLayout.setVisibility(autoStartRecordingEnabled ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // 重新检测 USB-накопитель（可能 授权后返回илиUSB-накопитель插拔)- 异步выполнение避免卡顿
        if (getContext() != null) {
            final Context context = getContext();
            final String currentLocation = appConfig != null ? appConfig.getStorageLocation() : AppConfig.STORAGE_INTERNAL;
            
            // 异步检测 USB-накопитель
            new Thread(() -> {
                boolean newHasSdCard = StorageHelper.hasExternalSdCard(context);
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getContext() == null) return;
                        
                        if (newHasSdCard != hasExternalSdCard) {
                            hasExternalSdCard = newHasSdCard;
                            if (storageDebugButton != null) {
                                storageDebugButton.setVisibility(hasExternalSdCard ? View.GONE : View.VISIBLE);
                            }
                            
                            // обновление Spinner 选项文字
                            if (storageLocationSpinner != null) {
                                isInitializingStorageLocation = true;
                                refreshStorageSpinnerOptions();

                                // Восстановление用户до Выбрать
                                int selectedIndex = 0;
                                if (AppConfig.STORAGE_EXTERNAL_SD.equals(currentLocation)) {
                                    selectedIndex = 1;
                                } else if (AppConfig.STORAGE_CUSTOM.equals(currentLocation)) {
                                    selectedIndex = 2;
                                }
                                storageLocationSpinner.setSelection(selectedIndex);
                                storageLocationSpinner.post(() -> isInitializingStorageLocation = false);
                                // 注意：这里不弹 Toast，因为 onResume 不代表 U  диск刚插入
                                // 只 界面切换后重新检测Статус，避免每 разоткрытьНастройкивсеУведомление"ОбнаруженоUSB-накопитель"
                            }
                        }
                        
                        // 始终обновление描述文字（可能USB-накопительСтатус变化или空间变化)
                        updateStorageLocationDescriptionAsync(currentLocation);
                    });
                }
            }).start();
            
            // обновлениеХранилище占用大小显示（经 异步 )
            updateStorageUsedSizeDisplay();
        }
        
        // обновление悬浮窗ВклВыклСтатус
        if (floatingWindowSwitch != null && getContext() != null && appConfig != null) {
            boolean hasPermission = WakeUpHelper.hasOverlayPermission(getContext());
            boolean isEnabled = appConfig.isFloatingWindowEnabled();
            
            if (isEnabled && hasPermission) {
                FloatingWindowService.start(getContext());
            }
        }
    }
    
    /**
     * инициализация车型конфигурация
     */
    private void initCarModelConfig(View view) {
        carModelSpinner = view.findViewById(R.id.spinner_car_model);
        customCameraConfigButton = view.findViewById(R.id.btn_custom_camera_config);
        
        if (carModelSpinner == null || customCameraConfigButton == null || getContext() == null) {
            return;
        }

        isInitializingCarModel = true;
        lastAppliedCarModel = (appConfig != null) ? appConfig.getCarModel() : null;
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                CAR_MODEL_OPTIONS
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        carModelSpinner.setAdapter(adapter);
        
        carModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newModel;
                String modelName;
                
                if (position == 0) {
                    newModel = AppConfig.CAR_MODEL_GALAXY_E5;
                    modelName = "Galaxy E5";
                } else if (position == 1) {
                    newModel = AppConfig.CAR_MODEL_E5_MULTI;
                    modelName = "GalaxyE5-Мульти-кнопки";
                } else if (position == 2) {
                    newModel = AppConfig.CAR_MODEL_L7;
                    modelName = "GalaxyL6/L7";
                } else if (position == 3) {
                    newModel = AppConfig.CAR_MODEL_L7_MULTI;
                    modelName = "GalaxyL7-Мульти-кнопки";
                } else if (position == 4) {
                    newModel = AppConfig.CAR_MODEL_XINGHAN_7;
                    modelName = "26 Starship7";
                } else if (position == 5) {
                    newModel = AppConfig.CAR_MODEL_PHONE;
                    modelName = "Телефон";
                } else if (position == 7) {
                    newModel = AppConfig.CAR_MODEL_MULTIVIEW;
                    modelName = "Мульти-камерный вид";
                } else {
                    newModel = AppConfig.CAR_MODEL_CUSTOM;
                    modelName = "Своя модель";
                }

                // Своя модель и Мульти-камерный вид显示конфигурация按钮
                updateCustomConfigButtonVisibility(position == 6 || position == 7);

                if (isInitializingCarModel) {
                    return;
                }

                if (newModel.equals(lastAppliedCarModel)) {
                    return;
                }

                lastAppliedCarModel = newModel;
                appConfig.setCarModel(newModel);
                
                // 切换车型时СбросЗаписьКамераВыбрать为全选（避免до Настройки导致无法Запись)
                appConfig.resetRecordingCameraSelection();
                
                // обновлениеЗаписьКамераВыбрать  UI（Камера数量由 AppConfig.getCameraCount() автоматически根据车型返回)
                updateRecordingCameraSelectionUI();
                
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Переключено на " + modelName + ", перезапустите приложение", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        String currentModel = appConfig.getCarModel();
        int selectedIndex = 0;
        if (AppConfig.CAR_MODEL_E5_MULTI.equals(currentModel)) {
            selectedIndex = 1;
        } else if (AppConfig.CAR_MODEL_L7.equals(currentModel)) {
            selectedIndex = 2;
        } else if (AppConfig.CAR_MODEL_L7_MULTI.equals(currentModel)) {
            selectedIndex = 3;
        } else if (AppConfig.CAR_MODEL_XINGHAN_7.equals(currentModel)) {
            selectedIndex = 4;
        } else if (AppConfig.CAR_MODEL_PHONE.equals(currentModel)) {
            selectedIndex = 5;
        } else if (AppConfig.CAR_MODEL_CUSTOM.equals(currentModel)) {
            selectedIndex = 6;
        } else if (AppConfig.CAR_MODEL_MULTIVIEW.equals(currentModel)) {
            selectedIndex = 7;
        }
        carModelSpinner.setSelection(selectedIndex);
        
        carModelSpinner.post(() -> {
            isInitializingCarModel = false;
        });
        
        customCameraConfigButton.setOnClickListener(v -> {
            openCustomCameraConfig();
        });
    }
    
    /**
     * обновление自定义конфигурация按钮 可见性
     */
    private void updateCustomConfigButtonVisibility(boolean visible) {
        if (customCameraConfigButton != null) {
            customCameraConfigButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * инициализацияЗаписьрежимконфигурация
     */
    private void initRecordingModeConfig(View view) {
        recordingModeSpinner = view.findViewById(R.id.spinner_recording_mode);
        recordingModeDescText = view.findViewById(R.id.tv_recording_mode_desc);
        
        if (recordingModeSpinner == null || getContext() == null) {
            return;
        }
        
        isInitializingRecordingMode = true;
        lastAppliedRecordingMode = (appConfig != null) ? appConfig.getRecordingMode() : null;
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                RECORDING_MODE_OPTIONS
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        recordingModeSpinner.setAdapter(adapter);
        
        recordingModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newMode;
                String modeName;
                String modeDesc;
                
                if (position == 0) {
                    newMode = AppConfig.RECORDING_MODE_AUTO;
                    modeName = "Авто";
                    // 显示Текущий实际использование режим
                    String actualMode = appConfig.shouldUseCodecRecording() ? "MediaCodec" : "MediaRecorder";
                    modeDesc = "MediaRecorder стабильнее, MediaCodec совместимее. Если видео не сохраняется, попробуйте изменить\nТекущий автовыбор：" + actualMode;
                } else if (position == 1) {
                    newMode = AppConfig.RECORDING_MODE_MEDIA_RECORDER;
                    modeName = "MediaRecorder";
                    modeDesc = "Аппаратный кодировщик, хорошая совместимость";
                } else {
                    newMode = AppConfig.RECORDING_MODE_CODEC;
                    modeName = "MediaCodec";
                    modeDesc = "Программное кодирование, для проблемных устройств";
                }
                
                updateRecordingModeDescription(modeDesc);
                
                if (isInitializingRecordingMode) {
                    return;
                }
                
                if (newMode.equals(lastAppliedRecordingMode)) {
                    return;
                }
                
                lastAppliedRecordingMode = newMode;
                appConfig.setRecordingMode(newMode);
                
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Переключено на " + modeName + ", будет применено при следующей записи", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        String currentMode = appConfig.getRecordingMode();
        int selectedIndex = 0;
        if (AppConfig.RECORDING_MODE_MEDIA_RECORDER.equals(currentMode)) {
            selectedIndex = 1;
        } else if (AppConfig.RECORDING_MODE_CODEC.equals(currentMode)) {
            selectedIndex = 2;
        }
        recordingModeSpinner.setSelection(selectedIndex);
        
        recordingModeSpinner.post(() -> {
            isInitializingRecordingMode = false;
        });
    }
    
    /**
     * обновлениеЗаписьрежим描述文字
     */
    private void updateRecordingModeDescription(String desc) {
        if (recordingModeDescText != null) {
            recordingModeDescText.setText(desc);
        }
    }
    
    /**
     * инициализация分时长конфигурация
     */
    private void initSegmentDurationConfig(View view) {
        segmentDurationSpinner = view.findViewById(R.id.spinner_segment_duration);
        
        if (segmentDurationSpinner == null || getContext() == null) {
            return;
        }
        
        isInitializingSegmentDuration = true;
        lastAppliedSegmentDuration = (appConfig != null) ? appConfig.getSegmentDurationMinutes() : AppConfig.SEGMENT_DURATION_1_MIN;
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                SEGMENT_DURATION_OPTIONS
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        segmentDurationSpinner.setAdapter(adapter);
        
        segmentDurationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int newDuration;
                String durationName;
                
                if (position == 0) {
                    newDuration = AppConfig.SEGMENT_DURATION_1_MIN;
                    durationName = "1 мин";
                } else if (position == 1) {
                    newDuration = AppConfig.SEGMENT_DURATION_3_MIN;
                    durationName = "3 мин";
                } else {
                    newDuration = AppConfig.SEGMENT_DURATION_5_MIN;
                    durationName = "5 мин";
                }
                
                if (isInitializingSegmentDuration) {
                    return;
                }
                
                if (newDuration == lastAppliedSegmentDuration) {
                    return;
                }
                
                lastAppliedSegmentDuration = newDuration;
                appConfig.setSegmentDurationMinutes(newDuration);
                
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Длительность сегмента: " + durationName + ", будет применено при следующей записи", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        // 根据Текущие настройкиНастройки选项
        int currentDuration = appConfig.getSegmentDurationMinutes();
        int selectedIndex = 0;  // По умолчанию1 мин.
        if (currentDuration == AppConfig.SEGMENT_DURATION_3_MIN) {
            selectedIndex = 1;
        } else if (currentDuration == AppConfig.SEGMENT_DURATION_5_MIN) {
            selectedIndex = 2;
        }
        segmentDurationSpinner.setSelection(selectedIndex);
        
        segmentDurationSpinner.post(() -> {
            isInitializingSegmentDuration = false;
        });
    }
    
    /**
     * инициализацияЗаписьКамераВыбратьконфигурация
     */
    private void initRecordingCameraSelectionConfig(View view) {
        cbRecordCameraFront = view.findViewById(R.id.cb_record_camera_front);
        cbRecordCameraBack = view.findViewById(R.id.cb_record_camera_back);
        cbRecordCameraLeft = view.findViewById(R.id.cb_record_camera_left);
        cbRecordCameraRight = view.findViewById(R.id.cb_record_camera_right);
        
        if (cbRecordCameraFront == null || getContext() == null || appConfig == null) {
            return;
        }
        
        isInitializingRecordingCameraSelection = true;
        
        // 根据Камера数量显示/隐藏 应  CheckBox
        int cameraCount = appConfig.getCameraCount();
        
        // 前Камера（1及и вышевсе有)
        cbRecordCameraFront.setVisibility(cameraCount >= 1 ? View.VISIBLE : View.GONE);
        cbRecordCameraFront.setText(appConfig.getRecordingCameraDisplayName("front", 1));
        cbRecordCameraFront.setChecked(appConfig.isRecordingCameraEnabled("front"));
        
        // Задняя камера（2及и выше才有)
        cbRecordCameraBack.setVisibility(cameraCount >= 2 ? View.VISIBLE : View.GONE);
        cbRecordCameraBack.setText(appConfig.getRecordingCameraDisplayName("back", 2));
        cbRecordCameraBack.setChecked(appConfig.isRecordingCameraEnabled("back"));
        
        // Левая камера（4才有)
        cbRecordCameraLeft.setVisibility(cameraCount >= 4 ? View.VISIBLE : View.GONE);
        cbRecordCameraLeft.setText(appConfig.getRecordingCameraDisplayName("left", 3));
        cbRecordCameraLeft.setChecked(appConfig.isRecordingCameraEnabled("left"));
        
        // Правая камера（4才有)
        cbRecordCameraRight.setVisibility(cameraCount >= 4 ? View.VISIBLE : View.GONE);
        cbRecordCameraRight.setText(appConfig.getRecordingCameraDisplayName("right", 4));
        cbRecordCameraRight.setChecked(appConfig.isRecordingCameraEnabled("right"));
        
        // Настройки监听器
        android.widget.CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            if (isInitializingRecordingCameraSelection) {
                return;
            }
            
            // проверка 否至少有一 шт.勾选
            if (!isChecked && !hasAtLeastOneRecordingCameraEnabled(buttonView)) {
                // Восстановление勾选Статус
                buttonView.setChecked(true);
                Toast.makeText(getContext(), "Выберите хотя бы одну камеру", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // СохранитьНастройки
            String position = getPositionFromCheckBox(buttonView);
            if (position != null) {
                appConfig.setRecordingCameraEnabled(position, isChecked);
                String cameraName = ((android.widget.CheckBox) buttonView).getText().toString();
                String message = isChecked ? "Включена запись «" + cameraName + "»" : "Отключена запись «" + cameraName + "»";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        };
        
        cbRecordCameraFront.setOnCheckedChangeListener(listener);
        cbRecordCameraBack.setOnCheckedChangeListener(listener);
        cbRecordCameraLeft.setOnCheckedChangeListener(listener);
        cbRecordCameraRight.setOnCheckedChangeListener(listener);
        
        // 延迟завершитьинициализация标记
        cbRecordCameraFront.post(() -> {
            isInitializingRecordingCameraSelection = false;
        });
    }
    
    /**
     * обновлениеЗаписьКамераВыбрать  UI（车型切换时调用)
     */
    private void updateRecordingCameraSelectionUI() {
        if (cbRecordCameraFront == null || getContext() == null || appConfig == null) {
            return;
        }
        
        isInitializingRecordingCameraSelection = true;
        
        // 根据Камера数量显示/隐藏 应  CheckBox
        int cameraCount = appConfig.getCameraCount();
        
        // 前Камера（1及и вышевсе有)
        cbRecordCameraFront.setVisibility(cameraCount >= 1 ? View.VISIBLE : View.GONE);
        cbRecordCameraFront.setText(appConfig.getRecordingCameraDisplayName("front", 1));
        cbRecordCameraFront.setChecked(appConfig.isRecordingCameraEnabled("front"));
        
        // Задняя камера（2及и выше才有)
        cbRecordCameraBack.setVisibility(cameraCount >= 2 ? View.VISIBLE : View.GONE);
        cbRecordCameraBack.setText(appConfig.getRecordingCameraDisplayName("back", 2));
        cbRecordCameraBack.setChecked(appConfig.isRecordingCameraEnabled("back"));
        
        // Левая камера（4才有)
        cbRecordCameraLeft.setVisibility(cameraCount >= 4 ? View.VISIBLE : View.GONE);
        cbRecordCameraLeft.setText(appConfig.getRecordingCameraDisplayName("left", 3));
        cbRecordCameraLeft.setChecked(appConfig.isRecordingCameraEnabled("left"));
        
        // Правая камера（4才有)
        cbRecordCameraRight.setVisibility(cameraCount >= 4 ? View.VISIBLE : View.GONE);
        cbRecordCameraRight.setText(appConfig.getRecordingCameraDisplayName("right", 4));
        cbRecordCameraRight.setChecked(appConfig.isRecordingCameraEnabled("right"));
        
        // 延迟завершитьинициализация标记
        cbRecordCameraFront.post(() -> {
            isInitializingRecordingCameraSelection = false;
        });
    }
    
    /**
     * проверка除Текущий按钮外， 否还有至少一 шт.Камера 勾选
     */
    private boolean hasAtLeastOneRecordingCameraEnabled(View excludeButton) {
        if (cbRecordCameraFront != excludeButton && cbRecordCameraFront.getVisibility() == View.VISIBLE && cbRecordCameraFront.isChecked()) {
            return true;
        }
        if (cbRecordCameraBack != excludeButton && cbRecordCameraBack.getVisibility() == View.VISIBLE && cbRecordCameraBack.isChecked()) {
            return true;
        }
        if (cbRecordCameraLeft != excludeButton && cbRecordCameraLeft.getVisibility() == View.VISIBLE && cbRecordCameraLeft.isChecked()) {
            return true;
        }
        if (cbRecordCameraRight != excludeButton && cbRecordCameraRight.getVisibility() == View.VISIBLE && cbRecordCameraRight.isChecked()) {
            return true;
        }
        return false;
    }
    
    /**
     * 根据 CheckBox Получение 应 КамераПозиция
     */
    private String getPositionFromCheckBox(View checkBox) {
        if (checkBox == cbRecordCameraFront) {
            return "front";
        } else if (checkBox == cbRecordCameraBack) {
            return "back";
        } else if (checkBox == cbRecordCameraLeft) {
            return "left";
        } else if (checkBox == cbRecordCameraRight) {
            return "right";
        }
        return null;
    }
    
    /**
     * инициализацияХранилищеПозицияконфигурация
     * 注意：USB-накопитель检测涉及ФайлСистемаоперация，необходимо异步выполнение避免卡顿
     */
    private void initStorageLocationConfig(View view) {
        storageLocationSpinner = view.findViewById(R.id.spinner_storage_location);
        storageLocationDescText = view.findViewById(R.id.tv_storage_location_desc);
        storageDebugButton = view.findViewById(R.id.btn_storage_debug);
        
        if (storageLocationSpinner == null || getContext() == null) {
            return;
        }
        
        isInitializingStorageLocation = true;
        lastAppliedStorageLocation = (appConfig != null) ? appConfig.getStorageLocation() : null;
        
        // 先использованиеПо умолчаниюСтатусинициализация UI（假设没有USB-накопитель，避免主线程阻塞)
        hasExternalSdCard = false;
        
        // Настройкиотладка按钮点击事件（先显示，检测完后可能隐藏)
        if (storageDebugButton != null) {
            storageDebugButton.setVisibility(View.VISIBLE);
            storageDebugButton.setOnClickListener(v -> showStorageDebugInfo());
        }
        
        // инициализация Spinner（использованиеПо умолчанию选项，后续异步обновление)
        storageLocationOptions = new String[] {"Внутреннее хранилище", "USB-накопитель (поиск...)", "Произвольный путь"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                storageLocationOptions
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        storageLocationSpinner.setAdapter(adapter);

        storageLocationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newLocation;
                String locationName;

                if (position == 0) {
                    newLocation = AppConfig.STORAGE_INTERNAL;
                    locationName = "Внутреннее хранилище";
                } else if (position == 1) {
                    newLocation = AppConfig.STORAGE_EXTERNAL_SD;
                    locationName = "USB";
                    // Если USB-накопитель недоступен，显示警告但仍然разрешить用户Выбрать
                    if (!hasExternalSdCard && !isInitializingStorageLocation && getContext() != null) {
                        Toast.makeText(getContext(), "USB-накопитель не обнаружен, временно используется внутреннее хранилище", Toast.LENGTH_LONG).show();
                    }
                } else {
                    newLocation = AppConfig.STORAGE_CUSTOM;
                    locationName = "Произвольный путь";
                    // 如果还没有设置произвольный путь，显示对话框
                    if (!isInitializingStorageLocation && getContext() != null) {
                        String existingPath = appConfig.getCustomStoragePath();
                        if (existingPath == null || existingPath.isEmpty()) {
                            showCustomStoragePathDialog();
                        }
                    }
                }

                updateStorageLocationDescriptionAsync(newLocation);

                if (isInitializingStorageLocation) {
                    return;
                }

                if (newLocation.equals(lastAppliedStorageLocation)) {
                    return;
                }

                lastAppliedStorageLocation = newLocation;
                appConfig.setStorageLocation(newLocation);

                if (getContext() != null) {
                    Toast.makeText(getContext(), "Место хранения: " + locationName, Toast.LENGTH_SHORT).show();
                    // 异步ПолучениеПуть描述
                    new Thread(() -> {
                        String pathDesc = StorageHelper.getCurrentStoragePathDesc(getContext());
                        AppLog.d("SettingsFragment", "ХранилищеПозиция切换为: " + newLocation + "，Путь: " + pathDesc);
                    }).start();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        String currentLocation = appConfig.getStorageLocation();
        int selectedIndex = 0;
        // 保持用户Выбрать ХранилищеПозиция，т.е.使USB-накопитель недоступентакже显示选Статус
        if (AppConfig.STORAGE_EXTERNAL_SD.equals(currentLocation)) {
            selectedIndex = 1;
        } else if (AppConfig.STORAGE_CUSTOM.equals(currentLocation)) {
            selectedIndex = 2;
        }
        storageLocationSpinner.setSelection(selectedIndex);
        
        // 显示загрузкаСтатус
        if (storageLocationDescText != null) {
            storageLocationDescText.setText("Обнаружение накопителей...");
        }
        
        // 异步检测 USB-накопитель并обновление UI
        final String finalCurrentLocation = currentLocation;
        final int finalSelectedIndex = selectedIndex;
        new Thread(() -> {
            //  Фоновый режим线程выполнение耗时  I/O операция
            boolean detected = StorageHelper.hasExternalSdCard(getContext());
            
            // 回 до 主线程обновление UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (getContext() == null || storageLocationSpinner == null) {
                        return;
                    }
                    
                    hasExternalSdCard = detected;
                    
                    // обновлениеотладка按钮可见性
                    if (storageDebugButton != null) {
                        storageDebugButton.setVisibility(hasExternalSdCard ? View.GONE : View.VISIBLE);
                    }
                    
                    // обновление Spinner 选项文字
                    refreshStorageSpinnerOptions();

                    // Восстановление用户Выбрать
                    storageLocationSpinner.setSelection(finalSelectedIndex);

                    // 异步обновление描述文字
                    updateStorageLocationDescriptionAsync(finalCurrentLocation);

                    storageLocationSpinner.post(() -> {
                        isInitializingStorageLocation = false;
                    });
                });
            }
        }).start();
    }
    
    /**
     * обновлениеХранилищеПозиция描述文字（同步版本，только 有数据时использование)
     * @deprecated использование {@link #updateStorageLocationDescriptionAsync(String)} 避免主线程阻塞
     */
    @Deprecated
    private void updateStorageLocationDescription(String location) {
        // 直接调用异步版本
        updateStorageLocationDescriptionAsync(location);
    }
    
    /**
     * 异步обновлениеХранилищеПозиция描述文字
     * 避免 主线程выполнениеФайлСистема I/O операция导致卡顿
     */
    private void updateStorageLocationDescriptionAsync(String location) {
        if (storageLocationDescText == null || getContext() == null) {
            return;
        }

        // 先显示загрузкаСтатус
        storageLocationDescText.setText("Получение информации о хранилище...");

        final Context context = getContext();
        final boolean useExternal = AppConfig.STORAGE_EXTERNAL_SD.equals(location);
        final boolean useCustom = AppConfig.STORAGE_CUSTOM.equals(location);
        final boolean isFallback = useExternal && !hasExternalSdCard;

        new Thread(() -> {
            //  Фоновый режим线程выполнение耗时  I/O операция
            java.io.File videoDir;
            if (useCustom) {
                videoDir = StorageHelper.getVideoDir(context);
            } else {
                videoDir = useExternal ?
                        StorageHelper.getVideoDir(context, true) :
                        StorageHelper.getVideoDir(context, false);
            }
            String path = videoDir.getAbsolutePath();

            // ПолучениеВнутренняя память根Путь用于判断
            String internalRoot = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();

            // 简化Путь显示
            String displayPath;
            if (useCustom) {
                AppConfig config = new AppConfig(context);
                String customPath = config.getCustomStoragePath();
                if (customPath != null && path.startsWith(customPath)) {
                    displayPath = path;
                } else {
                    // fallback到内部存储
                    displayPath = "⚠ Путь недоступен, используется внутреннее хранилище\n" + path;
                }
            } else if (path.startsWith(internalRoot + "/")) {
                //  Внутренняя память
                displayPath = path.replace(internalRoot + "/", "Внутренняя память/");
            } else if (path.startsWith("/storage/emulated/")) {
                // Другое emulated Путьтакже Внутренняя память
                displayPath = "Внутреннее хранилище" + path.substring(path.indexOf("/", "/storage/emulated/".length()));
            } else if (path.matches("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/.*")) {
                // XXXX-XXXX 格式  SD 卡
                int dcimIndex = path.indexOf("/DCIM/");
                if (dcimIndex > 0) {
                    displayPath = "USB" + path.substring(dcimIndex);
                } else {
                    displayPath = "USB-накопитель/" + path.substring(path.lastIndexOf("/") + 1);
                }
            } else {
                // ДругоеПуть原样显示
                displayPath = path;
            }
            
            // Получение容量Информация
            long availableSpace = StorageHelper.getAvailableSpace(videoDir);
            long totalSpace = StorageHelper.getTotalSpace(videoDir);
            String availableStr = StorageHelper.formatSize(availableSpace);
            String totalStr = StorageHelper.formatSize(totalSpace);
            
            // 构建最终显示文字
            final String finalText;
            if (isFallback) {
                finalText = "⚠ USB-накопитель недоступен, временно используется внутренняя память\n" + displayPath + "\nДоступно: " + availableStr + " / Всего : " + totalStr;
            } else if (useCustom) {
                finalText = displayPath + "\nДоступно: " + availableStr + " / Всего : " + totalStr + "\n(нажмите для изменения пути)";
            } else {
                finalText = displayPath + "\nДоступно: " + availableStr + " / Всего : " + totalStr;
            }

            // 回 до 主线程обновление UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (storageLocationDescText != null) {
                        storageLocationDescText.setText(finalText);
                        if (useCustom) {
                            storageLocationDescText.setOnClickListener(v -> showCustomStoragePathDialog());
                        } else {
                            storageLocationDescText.setOnClickListener(null);
                            storageLocationDescText.setClickable(false);
                        }
                    }
                });
            }
        }).start();
    }
    
    /**
     * 刷新存储位置 Spinner 选项（避免重复代码）
     */
    private void refreshStorageSpinnerOptions() {
        if (storageLocationSpinner == null || getContext() == null) {
            return;
        }
        if (hasExternalSdCard) {
            storageLocationOptions = new String[] {"Внутреннее хранилище", "USB", "Произвольный путь"};
        } else {
            storageLocationOptions = new String[] {"Внутреннее хранилище", "USB-накопитель (не найден)", "Произвольный путь"};
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                storageLocationOptions
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        storageLocationSpinner.setAdapter(adapter);
    }

    /**
     * 显示произвольный путь输入对话框
     */
    private void showCustomStoragePathDialog() {
        if (getContext() == null) return;

        android.widget.EditText input = new android.widget.EditText(getContext());
        input.setHint("Например: /storage/emulated/0/MyDashcam");
        input.setSingleLine(true);
        input.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        input.setHintTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        input.setBackgroundResource(R.drawable.edit_text_background);

        // 显示当前设置的路径
        String currentPath = appConfig.getCustomStoragePath();
        if (currentPath != null) {
            input.setText(currentPath);
        }

        // 设置边距
        android.widget.FrameLayout container = new android.widget.FrameLayout(getContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 48;
        params.rightMargin = 48;
        input.setLayoutParams(params);
        container.addView(input);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Произвольный путь хранения")
                .setMessage("Укажите каталог для хранения записей и фото.\n\n" +
                        "Файлы будут записываться в:\n" +
                        "  путь/DCIM/EVCam_Video/\n" +
                        "  путь/DCIM/EVCam_Photo/\n\n" +
                        "Оставьте пустым для возврата к внутреннему хранилищу.")
                .setView(container)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String path = input.getText().toString().trim();
                    if (path.isEmpty()) {
                        // 空路径 — 回退到internal
                        appConfig.setCustomStoragePath(null);
                        appConfig.setStorageLocation(AppConfig.STORAGE_INTERNAL);
                        lastAppliedStorageLocation = AppConfig.STORAGE_INTERNAL;
                        if (storageLocationSpinner != null) {
                            isInitializingStorageLocation = true;
                            storageLocationSpinner.setSelection(0);
                            storageLocationSpinner.post(() -> isInitializingStorageLocation = false);
                        }
                        Toast.makeText(getContext(), "Используется внутреннее хранилище", Toast.LENGTH_SHORT).show();
                    } else {
                        java.io.File testDir = new java.io.File(path);
                        if (!testDir.exists()) {
                            Toast.makeText(getContext(), "Внимание: путь не существует, но сохранён", Toast.LENGTH_LONG).show();
                        } else if (!testDir.isDirectory()) {
                            Toast.makeText(getContext(), "Внимание: это не каталог, но путь сохранён", Toast.LENGTH_LONG).show();
                        } else if (!testDir.canWrite()) {
                            Toast.makeText(getContext(), "Внимание: нет прав на запись, но путь сохранён", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Путь хранения установлен", Toast.LENGTH_SHORT).show();
                        }
                        appConfig.setCustomStoragePath(path);
                        appConfig.setStorageLocation(AppConfig.STORAGE_CUSTOM);
                        lastAppliedStorageLocation = AppConfig.STORAGE_CUSTOM;
                    }

                    updateStorageLocationDescriptionAsync(appConfig.getStorageLocation());
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    // 如果取消且没有路径，回退到internal
                    String existingPath = appConfig.getCustomStoragePath();
                    if (existingPath == null || existingPath.isEmpty()) {
                        appConfig.setStorageLocation(AppConfig.STORAGE_INTERNAL);
                        lastAppliedStorageLocation = AppConfig.STORAGE_INTERNAL;
                        if (storageLocationSpinner != null) {
                            isInitializingStorageLocation = true;
                            storageLocationSpinner.setSelection(0);
                            storageLocationSpinner.post(() -> isInitializingStorageLocation = false);
                        }
                        updateStorageLocationDescriptionAsync(AppConfig.STORAGE_INTERNAL);
                    }
                })
                .show();
    }

    /**
     * 显示Хранилище设备отладкаИнформация
     */
    private void showStorageDebugInfo() {
        if (getContext() == null) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 首先检测Статус разрешений хранилища
        sb.append("=== Статус разрешений хранилища ===\n");
        
        // проверкаДоступ ко всем файлам（Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasAllFilesAccess = android.os.Environment.isExternalStorageManager();
            sb.append("Доступ ко всем файлам (Android 11+): ");
            if (hasAllFilesAccess) {
                sb.append("Разрешено ✓\n");
            } else {
                sb.append("Не разрешено ✗\n");
                sb.append("⚠️ Для доступа к USB-накопителю нужно это разрешение!\n");
                sb.append("   Перейдите в «Настройки разрешений» и предоставьте «Доступ ко всем файлам»\n");
            }
        } else {
            sb.append("Android версия ниже 11, «Доступ ко всем файлам» не требуется\n");
        }
        
        // проверка基础ХранилищеРазрешение
        boolean hasStoragePermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasStoragePermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    getContext(), android.Manifest.permission.READ_MEDIA_VIDEO) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            sb.append("Права на медиафайлы (Android 13+): ");
        } else {
            hasStoragePermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    getContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            sb.append("Права чтения/записи хранилища: ");
        }
        sb.append(hasStoragePermission ? "Разрешено ✓\n" : "Не разрешено ✗\n");
        
        // 显示ТекущийПользовательский путь
        String customPath = appConfig.getCustomSdCardPath();
        sb.append("\n=== Пользовательский путь USB-накопителя ===\n");
        if (customPath != null) {
            sb.append("Текущая настройка: " + customPath + "\n");
            java.io.File customDir = new java.io.File(customPath);
            sb.append("Статус пути: " + (customDir.exists() ? "существует" : "не существует") + 
                    ", " + (customDir.canWrite() ? "доступен для записи" : "недоступен для записи") + "\n");
        } else {
            sb.append("Не задано (используется автоопределение)\n");
        }
        
        sb.append("\n");
        
        // 然后显示Хранилище设备检测Информация
        List<String> debugInfo = StorageHelper.getStorageDebugInfo(getContext());
        for (String line : debugInfo) {
            sb.append(line).append("\n");
        }
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Информация о накопителях")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Копировать", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = 
                            (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Отладочная информация хранилища", sb.toString());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Указать путь вручную", (dialog, which) -> {
                    showManualSdCardPathDialog();
                })
                .show();
    }
    
    /**
     * 显示вручнуюНастройкиUSB-накопительПуть 话框
     */
    private void showManualSdCardPathDialog() {
        if (getContext() == null) return;
        
        android.widget.EditText input = new android.widget.EditText(getContext());
        input.setHint("Например: /storage/ABCD-1234");
        input.setSingleLine(true);
        // 适配夜间режим
        input.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        input.setHintTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        input.setBackgroundResource(R.drawable.edit_text_background);
        
        // 显示ТекущийНастройки Путь
        String currentPath = appConfig.getCustomSdCardPath();
        if (currentPath != null) {
            input.setText(currentPath);
        }
        
        // Настройки边距
        android.widget.FrameLayout container = new android.widget.FrameLayout(getContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 48;
        params.rightMargin = 48;
        input.setLayoutParams(params);
        container.addView(input);
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Ручная настройка пути USB")
                .setMessage("Если автоопределение не работает, введите путь монтирования USB вручную。\n\n" +
                        "Формат: /storage/XXXX-XXXX (шестнадцатеричный ID)\n\n" +
                        "Оставьте пустым для автоопределения。")
                .setView(container)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String path = input.getText().toString().trim();
                    if (path.isEmpty()) {
                        appConfig.setCustomSdCardPath(null);
                        Toast.makeText(getContext(), "Custom path cleared, using auto detection", Toast.LENGTH_SHORT).show();
                    } else {
                        java.io.File testDir = new java.io.File(path);
                        if (!testDir.exists()) {
                            Toast.makeText(getContext(), "Warning: path does not exist, but saved", Toast.LENGTH_LONG).show();
                        } else if (!testDir.isDirectory()) {
                            Toast.makeText(getContext(), "Warning: path is not a directory, but saved", Toast.LENGTH_LONG).show();
                        } else if (!testDir.canWrite()) {
                            Toast.makeText(getContext(), "Warning: path is not writable, but saved", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "USB path set", Toast.LENGTH_SHORT).show();
                        }
                        appConfig.setCustomSdCardPath(path);
                    }
                    
                    // 重新检测并обновлениеUI
                    hasExternalSdCard = StorageHelper.hasExternalSdCard(getContext());
                    if (storageDebugButton != null) {
                        storageDebugButton.setVisibility(hasExternalSdCard ? View.GONE : View.VISIBLE);
                    }
                    refreshStorageSpinnerOptions();
                    String currentLocation = appConfig.getStorageLocation();
                    updateStorageLocationDescriptionAsync(currentLocation);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    /**
     * инициализацияХранилищеОчистка конфигурация
     */
    private void initStorageCleanupConfig(View view) {
        videoStorageLimitEdit = view.findViewById(R.id.et_video_storage_limit);
        photoStorageLimitEdit = view.findViewById(R.id.et_photo_storage_limit);
        videoUsedSizeText = view.findViewById(R.id.tv_video_used_size);
        photoUsedSizeText = view.findViewById(R.id.tv_photo_used_size);
        
        if (videoStorageLimitEdit == null || photoStorageLimitEdit == null || getContext() == null) {
            return;
        }
        
        isInitializingStorageCleanup = true;
        
        // загрузкаТекущийНастройки
        int videoLimit = appConfig.getVideoStorageLimitGb();
        int photoLimit = appConfig.getPhotoStorageLimitGb();
        
        // Настройки初始值（0显示пусто)
        if (videoLimit > 0) {
            videoStorageLimitEdit.setText(String.valueOf(videoLimit));
        } else {
            videoStorageLimitEdit.setText("");
        }
        
        if (photoLimit > 0) {
            photoStorageLimitEdit.setText(String.valueOf(photoLimit));
        } else {
            photoStorageLimitEdit.setText("");
        }
        
        // обновлениеТекущий占用大小显示
        updateStorageUsedSizeDisplay();
        
        // 添加文本变化监听器 - Видео
        videoStorageLimitEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                if (isInitializingStorageCleanup) {
                    return;
                }
                
                int limit = 0;
                String text = s.toString().trim();
                if (!text.isEmpty()) {
                    try {
                        limit = Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        // 忽略недействительноВвести
                    }
                }
                
                appConfig.setVideoStorageLimitGb(limit);
                AppLog.d("SettingsFragment", "ВидеоХранилище限制Настройки为: " + limit + " GB");
                
                // Уведомление MainActivity перезагрузкаОчистка задача
                notifyStorageCleanupConfigChanged();
            }
        });
        
        // 添加文本变化监听器 - Изображение
        photoStorageLimitEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                if (isInitializingStorageCleanup) {
                    return;
                }
                
                int limit = 0;
                String text = s.toString().trim();
                if (!text.isEmpty()) {
                    try {
                        limit = Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        // 忽略недействительноВвести
                    }
                }
                
                appConfig.setPhotoStorageLimitGb(limit);
                AppLog.d("SettingsFragment", "ИзображениеХранилище限制Настройки为: " + limit + " GB");
                
                // Уведомление MainActivity перезагрузкаОчистка задача
                notifyStorageCleanupConfigChanged();
            }
        });
        
        // 延迟завершитьинициализация标记
        videoStorageLimitEdit.post(() -> {
            isInitializingStorageCleanup = false;
        });
    }
    
    /**
     * обновлениеХранилище占用大小显示
     */
    private void updateStorageUsedSizeDisplay() {
        if (getContext() == null) {
            return;
        }
        
        //  Фоновый режим线程计算大小，避免阻塞UI
        new Thread(() -> {
            StorageCleanupManager cleanupManager = new StorageCleanupManager(getContext());
            long videoSize = cleanupManager.getVideoUsedSize();
            long photoSize = cleanupManager.getPhotoUsedSize();
            
            String videoSizeStr = "Использовано: " + StorageHelper.formatSize(videoSize);
            String photoSizeStr = "Использовано: " + StorageHelper.formatSize(photoSize);
            
            // 回 до 主线程обновлениеUI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (videoUsedSizeText != null) {
                        videoUsedSizeText.setText(videoSizeStr);
                    }
                    if (photoUsedSizeText != null) {
                        photoUsedSizeText.setText(photoSizeStr);
                    }
                });
            }
        }).start();
    }
    
    /**
     * Уведомление MainActivity ХранилищеОчистка конфигурация更改
     */
    private void notifyStorageCleanupConfigChanged() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).restartStorageCleanupTask();
        }
    }
    
    /**
     * обновление д.志按钮区域 可见性（только Debug Вкл启时显示)
     */
    private void updateSaveLogsButtonVisibility(boolean visible) {
        if (logButtonsLayout != null) {
            logButtonsLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * открыть自定义Камераконфигурация界面
     */
    private void openCustomCameraConfig() {
        if (getActivity() == null) {
            return;
        }
        
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new CustomCameraConfigFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }
    
    /**
     * открытьРазрешениеНастройки界面
     */
    private void openResolutionSettings() {
        if (getActivity() == null) {
            return;
        }
        
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new ResolutionSettingsFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }
    
    // ==================== 版本обновление相Выкл方法 ====================

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Возврат с экрана разрешения «Установка из неизвестных источников»:
        // если разрешение выдано — повторяем установку запомненного APK.
        if (requestCode == ApkInstallHelper.REQUEST_CODE_INSTALL_PERMISSION) {
            File pending = ApkInstallHelper.getPendingApk(getContext());
            if (pending != null) {
                ApkInstallHelper.installApk(this, pending);
            }
        }
    }

    /**
     * инициализация版本обновлениефункция
     */
    private void initVersionUpdate(View view) {
        currentVersionText = view.findViewById(R.id.tv_current_version);
        checkUpdateButton = view.findViewById(R.id.btn_check_update);
        
        if (currentVersionText == null || checkUpdateButton == null || getContext() == null) {
            return;
        }
        
        versionUpdateManager = new VersionUpdateManager(getContext());
        
        // 显示Текущий版本号
        String currentVersion = versionUpdateManager.getCurrentVersion();
        currentVersionText.setText("Текущая версия: v" + currentVersion);
        
        // Настройкипроверкаобновление按钮点击事件（直接проверка，有По умолчаниюСервис器)
        checkUpdateButton.setOnClickListener(v -> performCheckUpdate());
        
        // 长按可以изменениеобновлениеАдрес сервера（Высокий级用户)
        checkUpdateButton.setOnLongClickListener(v -> {
            showUpdateServerConfigDialog();
            return true;
        });
    }
    
    /**
     * 显示обновлениеСервис器конфигурация 话框
     */
    private void showUpdateServerConfigDialog() {
        if (getContext() == null) return;
        
        EditText inputEditText = new EditText(getContext());
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        inputEditText.setHint("Например: https://example.com/update/");
        inputEditText.setPadding(48, 32, 48, 32);
        inputEditText.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        inputEditText.setHintTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        inputEditText.setBackgroundResource(R.drawable.edit_text_background);
        
        // 显示ТекущийНастройки 地址
        String currentUrl = appConfig.getUpdateServerUrl();
        if (currentUrl != null) {
            inputEditText.setText(currentUrl);
        }
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Настройка сервера обновлений")
                .setMessage("Введите адрес сервера обновлений。\n\nКаталог сервера должен содержать：\n• version.txt（файл версии)\n• EVCam.apk（установочный пакет)")
                .setView(inputEditText)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String url = inputEditText.getText().toString().trim();
                    if (url.isEmpty()) {
                        appConfig.setUpdateServerUrl(null);
                        Toast.makeText(getContext(), "Адрес сервера обновлений очищен", Toast.LENGTH_SHORT).show();
                    } else {
                        // 基本 URL 验证
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            Toast.makeText(getContext(), "Введите корректный HTTP/HTTPS адрес", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        appConfig.setUpdateServerUrl(url);
                        Toast.makeText(getContext(), "Адрес сервера обновлений сохранён", Toast.LENGTH_SHORT).show();
                        // Сохранить后автоматическипроверкаобновление
                        performCheckUpdate();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    /**
     * выполнение版本проверка
     */
    private void performCheckUpdate() {
        if (getContext() == null || versionUpdateManager == null) return;
        
        // Отключить按钮防止重复点击
        checkUpdateButton.setEnabled(false);
        checkUpdateButton.setText("Проверка...");
        
        versionUpdateManager.checkUpdate(new VersionUpdateManager.UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion) {
                checkUpdateButton.setEnabled(true);
                checkUpdateButton.setText("Проверить →");
                showUpdateAvailableDialog(newVersion);
            }
            
            @Override
            public void onNoUpdate() {
                checkUpdateButton.setEnabled(true);
                checkUpdateButton.setText("Проверить →");
                Toast.makeText(getContext(), "Установлена последняя версия", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(String error) {
                checkUpdateButton.setEnabled(true);
                checkUpdateButton.setText("Проверить →");
                Toast.makeText(getContext(), "Ошибка проверки обновлений: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * 显示发现新版本 话框
     */
    private void showUpdateAvailableDialog(String newVersion) {
        if (getContext() == null) return;
        
        String currentVersion = versionUpdateManager.getCurrentVersion();
        String message = "Текущая версия: v" + currentVersion + "\nПоследняя версия：v" + newVersion + "\n\nСкачать новую версию？";
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Доступна новая версия")
                .setMessage(message)
                .setPositiveButton("Скачивание обновления", (dialog, which) -> {
                    startDownload(newVersion);
                })
                .setNegativeButton("Позже", null)
                .show();
    }
    
    /**
     * Вкл始скачивание APK
     */
    private void startDownload(String newVersion) {
        if (getContext() == null) return;
        
        // 创建скачивание进度 话框
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getContext());
        progressDialog.setTitle("Скачивание обновления");
        progressDialog.setMessage("Скачивание EVCam v" + newVersion + "...");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setCancelable(false);
        progressDialog.setButton(android.app.ProgressDialog.BUTTON_NEGATIVE, "Отмена", (dialog, which) -> {
            versionUpdateManager.cancelDownload();
            dialog.dismiss();
        });
        progressDialog.show();
        
        versionUpdateManager.downloadApk(newVersion, new VersionUpdateManager.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                progressDialog.setProgress(progress);
            }
            
            @Override
            public void onComplete(java.io.File apkFile) {
                progressDialog.dismiss();
                // Автозапуск стандартной установки APK через системный установщик.
                // Перед установкой запоминаем путь — после MY_PACKAGE_REPLACED файл будет удалён.
                ApkInstallHelper.markPendingApk(getContext(), apkFile);
                ApkInstallHelper.installApk(SettingsFragment.this, apkFile);
            }
            
            @Override
            public void onError(String error) {
                progressDialog.dismiss();
                if (!"Загрузка отменена".equals(error)) {
                    Toast.makeText(getContext(), "Ошибка скачивания: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    // ====================  д.志传相Выкл方法 ====================
    
    /**
     * 显示设备名称Ввести 话框（首 раз传时)
     */
    private void showDeviceNicknameInputDialog() {
        if (getContext() == null) return;
        
        EditText inputEditText = new EditText(getContext());
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        inputEditText.setHint("Например: Galaxy E5 Ивана");
        inputEditText.setPadding(48, 32, 48, 32);
        // 适配夜间режим
        inputEditText.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        inputEditText.setHintTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        inputEditText.setBackgroundResource(R.drawable.edit_text_background);
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Название устройства")
                .setMessage("Введите имя устройства для идентификации в логах：")
                .setView(inputEditText)
                .setPositiveButton("Подтвердить", (dialog, which) -> {
                    String nickname = inputEditText.getText().toString().trim();
                    if (nickname.isEmpty()) {
                        Toast.makeText(getContext(), "Название не может быть пустым", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 显示二 разПодтвердить
                    showNicknameConfirmDialog(nickname);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    /**
     * 显示设备名称二 разПодтвердить 话框（首 разНастройки名称后)
     */
    private void showNicknameConfirmDialog(String nickname) {
        if (getContext() == null) return;
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Подтвердите название устройства")
                .setMessage("Имя устройства：\n\n「" + nickname + "」\n\nИспользовать это имя？")
                .setPositiveButton("Подтвердить", (dialog, which) -> {
                    // Сохранить名称，然后显示传Подтвердить框
                    if (appConfig != null) {
                        appConfig.setDeviceNickname(nickname);
                    }
                    showUploadConfirmDialog(nickname);
                })
                .setNegativeButton("Ввести заново", (dialog, which) -> {
                    // 重新显示Ввести框
                    showDeviceNicknameInputDialog();
                })
                .show();
    }
    
    /**
     * 显示传Подтвердить 话框（содержит名称Подтвердить и 问题描述Ввести)
     */
    private void showUploadConfirmDialog(String nickname) {
        if (getContext() == null) return;
        
        // 创建содержит名称显示 и 问题描述Ввести 布局
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);
        
        // 名称显示 - 适配夜间режим
        TextView nicknameLabel = new TextView(getContext());
        nicknameLabel.setText("Отправитель: «" + nickname + "»");
        nicknameLabel.setTextSize(16);
        nicknameLabel.setPadding(0, 0, 0, 24);
        nicknameLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        layout.addView(nicknameLabel);
        
        //  д.志Выбрать标签
        TextView logTypeLabel = new TextView(getContext());
        logTypeLabel.setText("Выберите лог:");
        logTypeLabel.setTextSize(14);
        logTypeLabel.setPadding(0, 0, 0, 8);
        logTypeLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        layout.addView(logTypeLabel);
        
        //  д.志Выбрать RadioGroup
        RadioGroup logTypeGroup = new RadioGroup(getContext());
        logTypeGroup.setOrientation(RadioGroup.VERTICAL);
        logTypeGroup.setPadding(0, 0, 0, 16);
        
        // логи текущего сеанса选项
        RadioButton currentLogRadio = new RadioButton(getContext());
        currentLogRadio.setId(View.generateViewId());
        currentLogRadio.setText("Логи текущей сессии");
        currentLogRadio.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        currentLogRadio.setChecked(true);
        logTypeGroup.addView(currentLogRadio);
        
        // предыдущий сеанс д.志选项
        RadioButton previousLogRadio = new RadioButton(getContext());
        previousLogRadio.setId(View.generateViewId());
        boolean hasPrevious = AppLog.hasPreviousSessionLogs(getContext());
        if (hasPrevious) {
            String prevInfo = AppLog.getPreviousSessionLogInfo(getContext());
            previousLogRadio.setText("Логи прошлой сессии" + (prevInfo != null ? "\n  " + prevInfo : ""));
            previousLogRadio.setEnabled(true);
        } else {
            previousLogRadio.setText("Логи прошлой сессии (недоступны)");
            previousLogRadio.setEnabled(false);
        }
        previousLogRadio.setTextColor(ContextCompat.getColor(getContext(), 
                hasPrevious ? R.color.text_primary : R.color.text_secondary));
        logTypeGroup.addView(previousLogRadio);
        
        layout.addView(logTypeGroup);
        
        // 问题描述标签 - 适配夜间режим
        TextView descLabel = new TextView(getContext());
        descLabel.setText("Описание проблемы:");
        descLabel.setTextSize(14);
        descLabel.setPadding(0, 0, 0, 8);
        descLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        layout.addView(descLabel);
        
        // 问题描述Ввести框 - 适配夜间режим
        EditText inputEditText = new EditText(getContext());
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputEditText.setMinLines(3);
        inputEditText.setMaxLines(6);
        inputEditText.setHint("Опишите проблему...");
        inputEditText.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        inputEditText.setHintTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        inputEditText.setBackgroundResource(R.drawable.edit_text_background);
        layout.addView(inputEditText);
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Отправить логи")
                .setView(layout)
                .setPositiveButton("Отправить", (dialog, which) -> {
                    String problemDesc = inputEditText.getText().toString().trim();
                    if (problemDesc.isEmpty()) {
                        problemDesc = "（Пользователь не указал описание проблемы)";
                    }
                    // 判断Выбрать哪 шт. д.志
                    boolean uploadPreviousSession = previousLogRadio.isChecked();
                    performLogUpload(nickname, problemDesc, uploadPreviousSession);
                })
                .setNeutralButton("Изменить имя", (dialog, which) -> {
                    showDeviceNicknameInputDialog();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    /**
     * выполнение д.志传（По умолчанию传логи текущего сеанса)
     */
    private void performLogUpload(String deviceNickname, String problemDescription) {
        performLogUpload(deviceNickname, problemDescription, false);
    }
    
    /**
     * выполнение д.志传
     * @param uploadPreviousSession  否传предыдущий сеанс  д.志
     */
    private void performLogUpload(String deviceNickname, String problemDescription, boolean uploadPreviousSession) {
        if (getContext() == null) return;
        
        // Отключить按钮防止重复点击
        uploadLogsButton.setEnabled(false);
        uploadLogsButton.setText("Отправка...");
        
        String logType = uploadPreviousSession ? "предыдущий сеанс" : "текущий сеанс";
        
        AppLog.uploadLogsToServer(getContext(), deviceNickname, problemDescription, uploadPreviousSession, new AppLog.UploadCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        uploadLogsButton.setEnabled(true);
                        uploadLogsButton.setText("Отправить логи");
                        Toast.makeText(getContext(), "Автор получил " + logType + "логи", Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        uploadLogsButton.setEnabled(true);
                        uploadLogsButton.setText("Отправить логи");
                        Toast.makeText(getContext(), "Ошибка отправки: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
}
