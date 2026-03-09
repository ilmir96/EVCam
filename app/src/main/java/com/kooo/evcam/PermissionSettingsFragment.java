package com.kooo.evcam;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/**
 * РазрешениеНастройки界面 Fragment
 */
public class PermissionSettingsFragment extends Fragment {

    // ADB 一键Получение
    private Button btnAdbGrantAll;
    private ScrollView scrollAdbLog;
    private TextView tvAdbLog;
    private AdbPermissionHelper adbHelper;
    private boolean isAdbRunning = false;
    private boolean autoScrollAdbLog = true;

    // 基础Разрешение
    private TextView tvCameraStatus;
    private Button btnCameraPermission;
    private TextView tvMicrophoneStatus;
    private Button btnMicrophonePermission;
    private TextView tvStorageStatus;
    private Button btnStoragePermission;
    
    // УведомлениеРазрешение（Android 13+)
    private LinearLayout layoutNotificationPermission;
    private TextView tvNotificationStatus;
    private Button btnNotificationPermission;
    
    // Высокий级Разрешение
    private LinearLayout layoutAllFilesPermission;
    private TextView tvUsageStatsStatus;
    private Button btnUsageStatsPermission;
    private TextView tvAllFilesStatus;
    private Button btnAllFilesPermission;
    private TextView tvOverlayStatus;
    private Button btnOverlayPermission;
    private TextView tvAccessibilityStatus;
    private Button btnAccessibilityPermission;
    private TextView tvBatteryStatus;
    private Button btnBatteryPermission;

    // Система白名单（E245)
    private Button btnSystemWhitelist;
    private TextView tvWhitelistStatus;
    private ScrollView scrollWhitelistLog;
    private TextView tvWhitelistLog;
    private SystemWhitelistHelper whitelistHelper;
    private boolean isWhitelistRunning = false;
    private boolean autoScrollWhitelistLog = true;

    // ВосстановлениеСистема白名单
    private Button btnRestoreWhitelist;
    private ScrollView scrollRestoreLog;
    private TextView tvRestoreLog;
    private boolean isRestoreRunning = false;
    private boolean autoScrollRestoreLog = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_permission_settings, container, false);

        // 返回按钮
        Button btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // инициализация控件
        initViews(view);
        
        // Настройки点击事件
        setupClickListeners();
        
        // обновлениеРазрешениеСтатус
        updateAllPermissionStatus();

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

    private void initViews(View view) {
        // ADB 一键Получение
        btnAdbGrantAll = view.findViewById(R.id.btn_adb_grant_all);
        scrollAdbLog = view.findViewById(R.id.scroll_adb_log);
        tvAdbLog = view.findViewById(R.id.tv_adb_log);
        // 触摸 д.志区域时：1.阻止外层ScrollView拦截，让 д.志可滑动 2.Остановкаавтоматически滚动
        scrollAdbLog.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                autoScrollAdbLog = false;
            }
            return false;
        });

        // 基础Разрешение
        tvCameraStatus = view.findViewById(R.id.tv_camera_status);
        btnCameraPermission = view.findViewById(R.id.btn_camera_permission);
        tvMicrophoneStatus = view.findViewById(R.id.tv_microphone_status);
        btnMicrophonePermission = view.findViewById(R.id.btn_microphone_permission);
        tvStorageStatus = view.findViewById(R.id.tv_storage_status);
        btnStoragePermission = view.findViewById(R.id.btn_storage_permission);
        
        // УведомлениеРазрешение
        layoutNotificationPermission = view.findViewById(R.id.layout_notification_permission);
        tvNotificationStatus = view.findViewById(R.id.tv_notification_status);
        btnNotificationPermission = view.findViewById(R.id.btn_notification_permission);
        
        // Высокий级Разрешение
        layoutAllFilesPermission = view.findViewById(R.id.layout_all_files_permission);
        tvAllFilesStatus = view.findViewById(R.id.tv_all_files_status);
        btnAllFilesPermission = view.findViewById(R.id.btn_all_files_permission);
        tvOverlayStatus = view.findViewById(R.id.tv_overlay_status);
        btnOverlayPermission = view.findViewById(R.id.btn_overlay_permission);
        tvAccessibilityStatus = view.findViewById(R.id.tv_accessibility_status);
        btnAccessibilityPermission = view.findViewById(R.id.btn_accessibility_permission);
        tvBatteryStatus = view.findViewById(R.id.tv_battery_status);
        btnBatteryPermission = view.findViewById(R.id.btn_battery_permission);
        tvUsageStatsStatus = view.findViewById(R.id.tv_usage_stats_status);
        btnUsageStatsPermission = view.findViewById(R.id.btn_usage_stats_permission);
        
        // Система白名单（E245)
        btnSystemWhitelist = view.findViewById(R.id.btn_system_whitelist);
        tvWhitelistStatus = view.findViewById(R.id.tv_whitelist_status);
        scrollWhitelistLog = view.findViewById(R.id.scroll_whitelist_log);
        tvWhitelistLog = view.findViewById(R.id.tv_whitelist_log);
        // 触摸 д.志区域时：1.阻止外层ScrollView拦截，让 д.志可滑动 2.Остановкаавтоматически滚动
        scrollWhitelistLog.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                autoScrollWhitelistLog = false;
            }
            return false;
        });

        // ВосстановлениеСистема白名单
        btnRestoreWhitelist = view.findViewById(R.id.btn_restore_whitelist);
        scrollRestoreLog = view.findViewById(R.id.scroll_restore_log);
        tvRestoreLog = view.findViewById(R.id.tv_restore_log);
        scrollRestoreLog.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                autoScrollRestoreLog = false;
            }
            return false;
        });

        // 根据Система版本显示/隐藏某些选项
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            layoutNotificationPermission.setVisibility(View.VISIBLE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutAllFilesPermission.setVisibility(View.VISIBLE);
        }
    }

    private void setupClickListeners() {
        // ADB 一键ПолучениеРазрешение
        btnAdbGrantAll.setOnClickListener(v -> startAdbGrant());

        // Разрешение камеры
        btnCameraPermission.setOnClickListener(v -> openAppSettings());
        
        // Разрешение микрофона
        btnMicrophonePermission.setOnClickListener(v -> openAppSettings());
        
        // ХранилищеРазрешение
        btnStoragePermission.setOnClickListener(v -> openAppSettings());
        
        // УведомлениеРазрешение
        btnNotificationPermission.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                openNotificationSettings();
            } else {
                openAppSettings();
            }
        });
        
        // Доступ ко всем файлам
        btnAllFilesPermission.setOnClickListener(v -> requestAllFilesAccessPermission());
        
        // Разрешение плавающего окна
        btnOverlayPermission.setOnClickListener(v -> {
            if (getContext() != null) {
                WakeUpHelper.requestOverlayPermission(getContext());
                Toast.makeText(getContext(), "Please enable floating window permission", Toast.LENGTH_LONG).show();
            }
        });
        
        // 无障碍Сервис
        btnAccessibilityPermission.setOnClickListener(v -> openAccessibilitySettings());
        
        // Разрешение статистики использования
        btnUsageStatsPermission.setOnClickListener(v -> openUsageStatsSettings());

        // 电池优化
        btnBatteryPermission.setOnClickListener(v -> {
            if (getContext() != null) {
                WakeUpHelper.requestIgnoreBatteryOptimizations(getContext());
            }
        });
        
        // Система白名单（E245)
        btnSystemWhitelist.setOnClickListener(v -> showWhitelistRiskDialog());

        // ВосстановлениеСистема白名单
        btnRestoreWhitelist.setOnClickListener(v -> showRestoreConfirmDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每 раз返回时обновлениеРазрешениеСтатус
        updateAllPermissionStatus();
    }

    /**
     * обновление所有РазрешениеСтатус
     */
    private void updateAllPermissionStatus() {
        if (getContext() == null) return;
        
        updateCameraPermissionStatus();
        updateMicrophonePermissionStatus();
        updateStoragePermissionStatus();
        updateNotificationPermissionStatus();
        updateAllFilesPermissionStatus();
        updateOverlayPermissionStatus();
        updateAccessibilityServiceStatus();
        updateUsageStatsPermissionStatus();
        updateBatteryOptimizationStatus();
    }

    /**
     * обновлениеРазрешение камерыСтатус
     */
    private void updateCameraPermissionStatus() {
        if (getContext() == null) return;
        
        boolean granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
        
        if (granted) {
            tvCameraStatus.setText("Разрешено ✓");
            tvCameraStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnCameraPermission.setText("Разрешено");
            btnCameraPermission.setEnabled(false);
        } else {
            tvCameraStatus.setText("Не разрешено — необходимо для основных функций");
            tvCameraStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            btnCameraPermission.setText("Разрешить");
            btnCameraPermission.setEnabled(true);
        }
    }

    /**
     * обновлениеРазрешение микрофонаСтатус
     */
    private void updateMicrophonePermissionStatus() {
        if (getContext() == null) return;
        
        boolean granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED;
        
        if (granted) {
            tvMicrophoneStatus.setText("Разрешено ✓");
            tvMicrophoneStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnMicrophonePermission.setText("Разрешено");
            btnMicrophonePermission.setEnabled(false);
        } else {
            tvMicrophoneStatus.setText("Не разрешено — видео будет без звука");
            tvMicrophoneStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            btnMicrophonePermission.setText("Разрешить");
            btnMicrophonePermission.setEnabled(true);
        }
    }

    /**
     * обновлениеСтатус разрешений хранилища
     */
    private void updateStoragePermissionStatus() {
        if (getContext() == null) return;
        
        boolean granted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ использование媒体Разрешение
            granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO) 
                    == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12
            granted = true; // 分区Хранилище，不необходимо特殊Разрешение
        } else {
            // Android 9 及и ниже
            granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        
        if (granted) {
            tvStorageStatus.setText("Разрешено ✓");
            tvStorageStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnStoragePermission.setText("Разрешено");
            btnStoragePermission.setEnabled(false);
        } else {
            tvStorageStatus.setText("Не разрешено — невозможно сохранять видео и фото");
            tvStorageStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            btnStoragePermission.setText("Разрешить");
            btnStoragePermission.setEnabled(true);
        }
    }

    /**
     * обновлениеУведомлениеРазрешениеСтатус（Android 13+)
     */
    private void updateNotificationPermissionStatus() {
        if (getContext() == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        
        boolean granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED;
        
        if (granted) {
            tvNotificationStatus.setText("Разрешено ✓");
            tvNotificationStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnNotificationPermission.setText("Разрешено");
            btnNotificationPermission.setEnabled(false);
        } else {
            tvNotificationStatus.setText("Не разрешено — уведомления о записи недоступны");
            tvNotificationStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            btnNotificationPermission.setText("Разрешить");
            btnNotificationPermission.setEnabled(true);
        }
    }

    /**
     * обновлениеДоступ ко всем файламСтатус（Android 11+)
     */
    private void updateAllFilesPermissionStatus() {
        if (getContext() == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        
        boolean granted = android.os.Environment.isExternalStorageManager();
        
        if (granted) {
            tvAllFilesStatus.setText("Разрешено ✓");
            tvAllFilesStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnAllFilesPermission.setText("Разрешено");
            btnAllFilesPermission.setEnabled(false);
        } else {
            tvAllFilesStatus.setText("Не разрешено — запись на USB недоступна");
            tvAllFilesStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            btnAllFilesPermission.setText("Разрешить");
            btnAllFilesPermission.setEnabled(true);
        }
    }

    /**
     * обновлениеРазрешение плавающего окнаСтатус
     */
    private void updateOverlayPermissionStatus() {
        if (getContext() == null) return;
        
        boolean granted = WakeUpHelper.hasOverlayPermission(getContext());
        
        if (granted) {
            tvOverlayStatus.setText("Разрешено ✓");
            tvOverlayStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnOverlayPermission.setText("Разрешено");
            btnOverlayPermission.setEnabled(false);
        } else {
            tvOverlayStatus.setText("Не разрешено — плавающее окно и фоновое пробуждение недоступны");
            tvOverlayStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            btnOverlayPermission.setText("Разрешить");
            btnOverlayPermission.setEnabled(true);
        }
    }

    /**
     * обновление无障碍СервисСтатус
     */
    private void updateAccessibilityServiceStatus() {
        if (getContext() == null) return;
        
        boolean enabled = isAccessibilityServiceEnabled(getContext());
        
        if (enabled) {
            tvAccessibilityStatus.setText("Включено ✓");
            tvAccessibilityStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnAccessibilityPermission.setText("Включено");
            btnAccessibilityPermission.setEnabled(false);
        } else {
            tvAccessibilityStatus.setText("Не включено — приложение может быть завершено системой");
            tvAccessibilityStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            btnAccessibilityPermission.setText("Включить");
            btnAccessibilityPermission.setEnabled(true);
        }
    }

    /**
     * проверка无障碍Сервис 否Включено
     */
    private boolean isAccessibilityServiceEnabled(Context context) {
        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
            
            if (accessibilityEnabled == 1) {
                String services = Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                
                if (services != null) {
                    String serviceName = context.getPackageName() + "/" + KeepAliveAccessibilityService.class.getName();
                    return services.contains(serviceName);
                }
            }
        } catch (Exception e) {
            AppLog.e("PermissionSettings", "проверка无障碍СервисСтатусОшибка", e);
        }
        return false;
    }

    /**
     * обновление电池优化Статус
     */
    private void updateBatteryOptimizationStatus() {
        if (getContext() == null) return;
        
        boolean ignored = WakeUpHelper.isIgnoringBatteryOptimizations(getContext());
        
        if (ignored) {
            tvBatteryStatus.setText("Оптимизация отключена ✓");
            tvBatteryStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnBatteryPermission.setText("Задано");
            btnBatteryPermission.setEnabled(false);
        } else {
            tvBatteryStatus.setText("Оптимизация вкл. — приложение может быть усыплено");
            tvBatteryStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            btnBatteryPermission.setText("Настроить");
            btnBatteryPermission.setEnabled(true);
        }
    }

    /**
     * открытьПриложениеНастройки页面
     */
    private void openAppSettings() {
        if (getContext() == null) return;
        
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            startActivity(intent);
            Toast.makeText(getContext(), "Предоставьте необходимые разрешения в списке", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            AppLog.e("PermissionSettings", "открытьПриложениеНастройкиОшибка", e);
            Toast.makeText(getContext(), "Не удалось открыть настройки, используйте стороннее ПО", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * открытьУведомлениеНастройки页面
     */
    private void openNotificationSettings() {
        if (getContext() == null) return;
        
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            startActivity(intent);
            Toast.makeText(getContext(), "Включите разрешение на уведомления", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            openAppSettings();
        }
    }

    /**
     * 求Доступ ко всем файлам
     */
    private void requestAllFilesAccessPermission() {
        if (getContext() == null) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                startActivity(intent);
                Toast.makeText(getContext(), "Включите «Доступ ко всем файлам»", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                    Toast.makeText(getContext(), "Найдите приложение и включите разрешение", Toast.LENGTH_LONG).show();
                } catch (Exception e2) {
                    AppLog.e("PermissionSettings", "无法открытьРазрешениеНастройки页面", e2);
                    Toast.makeText(getContext(), "Не удалось открыть настройки, используйте стороннее ПО", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * обновлениеРазрешение статистики использованияСтатус
     */
    private void updateUsageStatsPermissionStatus() {
        if (getContext() == null) return;

        boolean granted = hasUsageStatsPermission(getContext());

        if (granted) {
            tvUsageStatsStatus.setText("Разрешено ✓");
            tvUsageStatsStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            btnUsageStatsPermission.setText("Разрешено");
            btnUsageStatsPermission.setEnabled(false);
        } else {
            tvUsageStatsStatus.setText("Не разрешено — панорамная система недоступна");
            tvUsageStatsStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            btnUsageStatsPermission.setText("Разрешить");
            btnUsageStatsPermission.setEnabled(true);
        }
    }

    /**
     * проверкаРазрешение статистики использования
     */
    private boolean hasUsageStatsPermission(Context context) {
        android.app.AppOpsManager appOps = (android.app.AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.unsafeCheckOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), context.getPackageName());
        return mode == android.app.AppOpsManager.MODE_ALLOWED;
    }

    /**
     * открытьРазрешение статистики использованияНастройки页面
     */
    private void openUsageStatsSettings() {
        if (getContext() == null) return;

        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            Toast.makeText(getContext(), "Найдите приложение и включите доступ к использованию", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            AppLog.e("PermissionSettings", "открытьиспользование情况доступНастройкиОшибка", e);
            Toast.makeText(getContext(), "Не удалось открыть настройки", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * открыть无障碍Настройки页面
     */
    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(getContext(), "Найдите «EVCam — Служба поддержки активности» и включите", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            AppLog.e("PermissionSettings", "открыть无障碍НастройкиОшибка", e);
            Toast.makeText(getContext(), "Не удалось открыть настройки, используйте стороннее ПО", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ADB 一键ПолучениеРазрешение ====================

    /**
     * Запуск ADB 一键ПолучениеРазрешение
     */
    private void startAdbGrant() {
        if (isAdbRunning) return;
        if (getContext() == null) return;

        isAdbRunning = true;
        autoScrollAdbLog = true;
        btnAdbGrantAll.setEnabled(false);
        btnAdbGrantAll.setText("Выполнение...");
        scrollAdbLog.setVisibility(View.VISIBLE);
        tvAdbLog.setText("");

        if (adbHelper == null) {
            adbHelper = new AdbPermissionHelper(getContext());
        }

        adbHelper.grantAllPermissions(new AdbPermissionHelper.Callback() {
            @Override
            public void onLog(String message) {
                if (getContext() == null) return;
                tvAdbLog.append(message + "\n");
                if (autoScrollAdbLog) {
                    scrollAdbLog.post(() -> scrollAdbLog.fullScroll(View.FOCUS_DOWN));
                }
            }

            @Override
            public void onComplete(boolean allSuccess) {
                isAdbRunning = false;
                btnAdbGrantAll.setEnabled(true);
                btnAdbGrantAll.setText("Получить все разрешения");
                // Обновить所有РазрешениеСтатус显示
                updateAllPermissionStatus();
            }
        });
    }

    // ==================== E245 Система白名单конфигурация ====================

    /**
     * 显示风险提醒 话框
     */
    private void showWhitelistRiskDialog() {
        if (getContext() == null) return;

        new MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Предупреждение")
                .setMessage("此операция将изменение车机Система分区 конфигурацияФайл，仔细阅读：\n\n"
                        + "1. только适用于GalaxyE5（E245)车机\n"
                        + "2. необходимо设备открытьUSBотладка\n"
                        + "3. 将изменение system  и  vendor 分区  3  шт. XML Файл\n"
                        + "4. изменение前会автоматическирезервное копирование原Файл до  /sdcard/evcam_backup/\n"
                        + "5. изменениезавершение后необходимоперезагрузка车机才能生效\n"
                        + "6. 本脚本理论不会 车机造成危害，但出现任何问题均自行承担后果\n"
                        + "7. Если 设备不  E245，脚本会автоматически检测并прервать。\n\n"
                        + "Подтвердить要продолжитьвыполнение?？")
                .setPositiveButton("Выполнить", (dialog, which) -> startWhitelistSetup())
                .setNegativeButton("Отмена", null)
                .show();
    }

    /**
     * ЗапускСистема白名单конфигурация
     */
    private void startWhitelistSetup() {
        if (isWhitelistRunning) return;
        if (getContext() == null) return;

        isWhitelistRunning = true;
        autoScrollWhitelistLog = true;
        btnSystemWhitelist.setEnabled(false);
        btnSystemWhitelist.setText("Выполнение...");
        scrollWhitelistLog.setVisibility(View.VISIBLE);
        tvWhitelistLog.setText("");

        if (whitelistHelper == null) {
            whitelistHelper = new SystemWhitelistHelper(getContext());
        }

        whitelistHelper.executeWhitelistSetup(new SystemWhitelistHelper.Callback() {
            @Override
            public void onLog(String message) {
                if (getContext() == null) return;
                tvWhitelistLog.append(message + "\n");
                if (autoScrollWhitelistLog) {
                    scrollWhitelistLog.post(() -> scrollWhitelistLog.fullScroll(View.FOCUS_DOWN));
                }
            }

            @Override
            public void onComplete(boolean success) {
                isWhitelistRunning = false;
                btnSystemWhitelist.setEnabled(true);
                btnSystemWhitelist.setText("Настроить автоматически");

                if (getContext() == null) return;

                if (success) {
                    tvWhitelistStatus.setText("Настройка выполнена — перезагрузите головное устройство");
                    tvWhitelistStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
                } else {
                    tvWhitelistStatus.setText("Ошибка настройки — проверьте логи");
                    tvWhitelistStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                }
            }
        });
    }

    // ==================== E245 ВосстановлениеСистема白名单 ====================

    /**
     * 显示ВосстановлениеПодтвердить 话框
     */
    private void showRestoreConfirmDialog() {
        if (getContext() == null) return;

        new MaterialAlertDialogBuilder(getContext(), R.style.Theme_Cam_MaterialAlertDialog)
                .setTitle("Подтверждение восстановления")
                .setMessage("Восстановить системную конфигурацию из резервной копии?\n\n"
                    + "1. Белый список EVCam будет удалён\n"
                    + "2. Системные файлы будут восстановлены\n"
                    + "3. Потребуется перезагрузка\n"
                    + "4. Если «Быстрая настройка» вызвала проблемы, всё нормализуется\n\n"
                    + "Восстановить?")
                .setPositiveButton("ПодтвердитьВосстановление", (dialog, which) -> startWhitelistRestore())
                .setNegativeButton("Отмена", null)
                .show();
    }

    /**
     * ЗапускСистема白名单Восстановление
     */
    private void startWhitelistRestore() {
        if (isRestoreRunning) return;
        if (getContext() == null) return;

        isRestoreRunning = true;
        autoScrollRestoreLog = true;
        btnRestoreWhitelist.setEnabled(false);
        btnRestoreWhitelist.setText("Восстановление...");
        scrollRestoreLog.setVisibility(View.VISIBLE);
        tvRestoreLog.setText("");

        if (whitelistHelper == null) {
            whitelistHelper = new SystemWhitelistHelper(getContext());
        }

        whitelistHelper.executeWhitelistRestore(new SystemWhitelistHelper.Callback() {
            @Override
            public void onLog(String message) {
                if (getContext() == null) return;
                tvRestoreLog.append(message + "\n");
                if (autoScrollRestoreLog) {
                    scrollRestoreLog.post(() -> scrollRestoreLog.fullScroll(View.FOCUS_DOWN));
                }
            }

            @Override
            public void onComplete(boolean success) {
                isRestoreRunning = false;
                btnRestoreWhitelist.setEnabled(true);
                btnRestoreWhitelist.setText("Восстановить белый список");

                if (getContext() == null) return;

                if (success) {
                    tvWhitelistStatus.setText("Восстановлено — перезагрузите головное устройство");
                    tvWhitelistStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
                } else {
                    tvWhitelistStatus.setText("Ошибка восстановления — проверьте логи");
                    tvWhitelistStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                }
            }
        });
    }
}
