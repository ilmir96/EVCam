package com.kooo.evcam.heartbeat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.MainActivity;
import com.kooo.evcam.R;

/**
 * Мониторингконфигурация界面
 */
public class HeartbeatFragment extends Fragment implements HeartbeatManager.HeartbeatListener {
    private static final String TAG = "HeartbeatFragment";
    
    // UI  групп件
    private Button btnMenu, btnHome;
    private EditText etServerUrl, etSecretKey;
    private ImageButton btnToggleKeyVisibility;
    private RadioGroup rgInterval;
    private RadioButton rbInterval30, rbInterval60, rbInterval120, rbInterval300;
    private RadioGroup rgImageQuality;
    private RadioButton rbQuality100k, rbQuality500k, rbQuality1m, rbQualityNoCompress;
    private SwitchCompat switchScreenOnPush, switchScreenOffPush;
    private SwitchCompat switchAutoStart;
    private TextView tvVehicleId;
    private Button btnCopyId;
    private Button btnSaveConfig, btnStartService, btnStopService, btnTest, btnResetStats;
    private TextView tvStatus, tvCameraCount, tvLastUpload, tvStatistics;
    
    private HeartbeatConfig config;
    private boolean isKeyVisible = false;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_heartbeat, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadConfig();
        setupListeners();
        
        // 注册监听器
        if (getActivity() instanceof MainActivity) {
            HeartbeatManager manager = ((MainActivity) getActivity()).getHeartbeatManager();
            if (manager != null) {
                manager.setListener(this);
            }
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 立т.е.обновление一 раз
        updateStatusDisplay();
        // 延迟再обновление一 раз，确保相机Статус绪
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded()) {
                updateStatusDisplay();
            }
        }, 1000);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 移除监听器
        if (getActivity() instanceof MainActivity) {
            HeartbeatManager manager = ((MainActivity) getActivity()).getHeartbeatManager();
            if (manager != null) {
                manager.setListener(null);
            }
        }
    }
    
    private void initViews(View view) {
        btnMenu = view.findViewById(R.id.btn_menu);
        btnHome = view.findViewById(R.id.btn_home);
        etServerUrl = view.findViewById(R.id.et_server_url);
        etSecretKey = view.findViewById(R.id.et_secret_key);
        btnToggleKeyVisibility = view.findViewById(R.id.btn_toggle_key_visibility);
        rgInterval = view.findViewById(R.id.rg_interval);
        rbInterval30 = view.findViewById(R.id.rb_interval_30);
        rbInterval60 = view.findViewById(R.id.rb_interval_60);
        rbInterval120 = view.findViewById(R.id.rb_interval_120);
        rbInterval300 = view.findViewById(R.id.rb_interval_300);
        rgImageQuality = view.findViewById(R.id.rg_image_quality);
        rbQuality100k = view.findViewById(R.id.rb_quality_100k);
        rbQuality500k = view.findViewById(R.id.rb_quality_500k);
        rbQuality1m = view.findViewById(R.id.rb_quality_1m);
        rbQualityNoCompress = view.findViewById(R.id.rb_quality_no_compress);
        switchScreenOnPush = view.findViewById(R.id.switch_screen_on_push);
        switchScreenOffPush = view.findViewById(R.id.switch_screen_off_push);
        switchAutoStart = view.findViewById(R.id.switch_auto_start);
        tvVehicleId = view.findViewById(R.id.tv_vehicle_id);
        btnCopyId = view.findViewById(R.id.btn_copy_id);
        btnSaveConfig = view.findViewById(R.id.btn_save_config);
        btnStartService = view.findViewById(R.id.btn_start_service);
        btnStopService = view.findViewById(R.id.btn_stop_service);
        btnTest = view.findViewById(R.id.btn_test);
        btnResetStats = view.findViewById(R.id.btn_reset_stats);
        tvStatus = view.findViewById(R.id.tv_status);
        tvCameraCount = view.findViewById(R.id.tv_camera_count);
        tvLastUpload = view.findViewById(R.id.tv_last_upload);
        tvStatistics = view.findViewById(R.id.tv_statistics);
        
        config = new HeartbeatConfig(requireContext());
    }
    
    private void loadConfig() {
        // Адрес сервера
        etServerUrl.setText(config.getServerUrl());
        
        // 通信密钥
        etSecretKey.setText(config.getSecretKey());
        
        // 推送间隔
        int interval = config.getIntervalSeconds();
        switch (interval) {
            case HeartbeatConfig.INTERVAL_30_SECONDS:
                rbInterval30.setChecked(true);
                break;
            case HeartbeatConfig.INTERVAL_120_SECONDS:
                rbInterval120.setChecked(true);
                break;
            case HeartbeatConfig.INTERVAL_300_SECONDS:
                rbInterval300.setChecked(true);
                break;
            default:
                rbInterval60.setChecked(true);
                break;
        }
        
        // Изображение质量
        int targetSize = config.getTargetSizeKB();
        switch (targetSize) {
            case HeartbeatConfig.TARGET_SIZE_500KB:
                rbQuality500k.setChecked(true);
                break;
            case HeartbeatConfig.TARGET_SIZE_1MB:
                rbQuality1m.setChecked(true);
                break;
            case HeartbeatConfig.TARGET_SIZE_NO_COMPRESS:
                rbQualityNoCompress.setChecked(true);
                break;
            default:
                rbQuality100k.setChecked(true);
                break;
        }
        
        // 推图режим
        switchScreenOnPush.setChecked(config.isScreenOnPushEnabled());
        switchScreenOffPush.setChecked(config.isScreenOffPushEnabled());
        
        // автоматическиЗапуск
        switchAutoStart.setChecked(config.isAutoStartEnabled());
        
        // 车辆ID
        tvVehicleId.setText(config.getVehicleId());
        
        // 统计Информация
        updateStatisticsDisplay();
    }
    
    private void setupListeners() {
        // 菜单按钮
        btnMenu.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    } else {
                        drawerLayout.openDrawer(GravityCompat.START);
                    }
                }
            }
        });
        
        // 主页按钮
        btnHome.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).goToRecordingInterface();
            }
        });
        
        // 密钥可见性切换
        btnToggleKeyVisibility.setOnClickListener(v -> {
            isKeyVisible = !isKeyVisible;
            if (isKeyVisible) {
                etSecretKey.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnToggleKeyVisibility.setImageResource(R.drawable.ic_visibility_off);
            } else {
                etSecretKey.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnToggleKeyVisibility.setImageResource(R.drawable.ic_visibility);
            }
            etSecretKey.setSelection(etSecretKey.getText().length());
        });
        
        // 复制车辆ID
        btnCopyId.setOnClickListener(v -> {
            String vehicleId = config.getVehicleId();
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("vehicle_id", vehicleId);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "Скопировано: " + vehicleId, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Сохранитьконфигурация
        btnSaveConfig.setOnClickListener(v -> saveConfig());
        
        // автоматическиЗапускВклВыкл
        switchAutoStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setAutoStartEnabled(isChecked);
        });
        
        // ЗапускСервис
        btnStartService.setOnClickListener(v -> {
            if (!config.isConfigured()) {
                Toast.makeText(requireContext(), "Сначала сохраните настройки", Toast.LENGTH_SHORT).show();
                return;
            }
            
            config.setEnabled(true);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onHeartbeatConfigChanged();
            }
            updateButtonStates();
            updateStatusDisplay();
            Toast.makeText(requireContext(), "Сервис запущен", Toast.LENGTH_SHORT).show();
        });
        
        // ОстановкаСервис
        btnStopService.setOnClickListener(v -> {
            config.setEnabled(false);
            if (getActivity() instanceof MainActivity) {
                HeartbeatManager manager = ((MainActivity) getActivity()).getHeartbeatManager();
                if (manager != null) {
                    manager.stop();
                }
                ((MainActivity) getActivity()).onHeartbeatConfigChanged();
            }
            updateButtonStates();
            updateStatusDisplay();
            Toast.makeText(requireContext(), "Сервис остановлен", Toast.LENGTH_SHORT).show();
        });
        
        // 立т.е.тестирование
        btnTest.setOnClickListener(v -> {
            if (!config.isConfigured()) {
                Toast.makeText(requireContext(), "Сначала сохраните настройки", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (getActivity() instanceof MainActivity) {
                HeartbeatManager manager = ((MainActivity) getActivity()).getHeartbeatManager();
                if (manager != null) {
                    Toast.makeText(requireContext(), "Тестирование...", Toast.LENGTH_SHORT).show();
                    manager.executeOnce();
                }
            }
        });
        
        // Сброс统计
        btnResetStats.setOnClickListener(v -> {
            config.resetStatistics();
            updateStatisticsDisplay();
            Toast.makeText(requireContext(), "Статистика сброшена", Toast.LENGTH_SHORT).show();
        });
    }
    
    /**
     * обновлениеЗапуск/Остановка按钮Статус
     */
    private void updateButtonStates() {
        boolean isEnabled = config.isEnabled();
        boolean isConfigured = config.isConfigured();
        
        btnStartService.setEnabled(!isEnabled && isConfigured);
        btnStopService.setEnabled(isEnabled);
    }
    
    private void saveConfig() {
        String serverUrl = etServerUrl.getText().toString().trim();
        String secretKey = etSecretKey.getText().toString().trim();
        
        // 验证
        if (serverUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Укажите адрес сервера", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            Toast.makeText(requireContext(), "Адрес сервера должен начинаться с http:// или https://", Toast.LENGTH_SHORT).show();
            return;
        }
        if (secretKey.isEmpty()) {
            Toast.makeText(requireContext(), "Укажите ключ связи", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Сохранитьконфигурация
        config.setServerUrl(serverUrl);
        config.setSecretKey(secretKey);
        config.setIntervalSeconds(getSelectedInterval());
        config.setTargetSizeKB(getSelectedTargetSize());
        config.setScreenOnPushEnabled(switchScreenOnPush.isChecked());
        config.setScreenOffPushEnabled(switchScreenOffPush.isChecked());
        config.setAutoStartEnabled(switchAutoStart.isChecked());
        
        Toast.makeText(requireContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show();
        
        // обновление按钮Статус
        updateButtonStates();
        updateStatusDisplay();
    }
    
    private int getSelectedInterval() {
        int checkedId = rgInterval.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_interval_30) {
            return HeartbeatConfig.INTERVAL_30_SECONDS;
        } else if (checkedId == R.id.rb_interval_120) {
            return HeartbeatConfig.INTERVAL_120_SECONDS;
        } else if (checkedId == R.id.rb_interval_300) {
            return HeartbeatConfig.INTERVAL_300_SECONDS;
        }
        return HeartbeatConfig.INTERVAL_60_SECONDS;
    }
    
    private int getSelectedTargetSize() {
        int checkedId = rgImageQuality.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_quality_500k) {
            return HeartbeatConfig.TARGET_SIZE_500KB;
        } else if (checkedId == R.id.rb_quality_1m) {
            return HeartbeatConfig.TARGET_SIZE_1MB;
        } else if (checkedId == R.id.rb_quality_no_compress) {
            return HeartbeatConfig.TARGET_SIZE_NO_COMPRESS;
        }
        return HeartbeatConfig.TARGET_SIZE_100KB;
    }
    
    /**
     * обновлениеСтатус显示
     */
    public void updateStatusDisplay() {
        if (getActivity() instanceof MainActivity) {
            HeartbeatManager manager = ((MainActivity) getActivity()).getHeartbeatManager();
            boolean isRunning = manager != null && manager.isRunning();
            boolean isEnabled = config.isEnabled();
            boolean isConfigured = config.isConfigured();
            boolean screenOnPush = config.isScreenOnPushEnabled();
            boolean screenOffPush = config.isScreenOffPushEnabled();
            
            if (isRunning) {
                // СервисВыполняется Работа
                tvStatus.setText("Работает");
                tvStatus.setTextColor(0xFF00CC00);  // 深绿色
            } else if (!isConfigured) {
                // конфигурация不完整
                tvStatus.setText("Не настроен");
                tvStatus.setTextColor(0xFFFF4444);  // 红色
            } else if (!isEnabled) {
                // 用户Не ВключитьСервис
                tvStatus.setText("Не запущен");
                tvStatus.setTextColor(0xFF888888);  // 灰色
            } else {
                // isEnabled=true 但 isRunning=false：конфигурацияВключено但СервисНе Работа
                // 这种情况通常 ：用户доВключитьСервис，但 app перезагрузка后没有автоматическиЗапуск
                if (!screenOnPush && !screenOffPush) {
                    tvStatus.setText("Сервис не запущен (все режимы отправки выключены)");
                    tvStatus.setTextColor(0xFFFF9900);  // 橙色
                } else if (screenOffPush && !screenOnPush) {
                    tvStatus.setText("Сервис не запущен (только при выкл. экране)");
                    tvStatus.setTextColor(0xFFFF9900);  // 橙色
                } else {
                    tvStatus.setText("Сервис не запущен");
                    tvStatus.setTextColor(0xFFFF9900);  // 橙色
                }
            }
            
            // Камера数量（ от  MainActivity Получение)
            int cameraCount = ((MainActivity) getActivity()).getConnectedCameraCount();
            int totalCameras = ((MainActivity) getActivity()).getTotalCameraCount();
            tvCameraCount.setText("Камеры: " + cameraCount + "/" + totalCameras + " подключено");
        }
        
        updateButtonStates();
        updateStatisticsDisplay();
    }
    
    private void updateStatisticsDisplay() {
        //  раз传时间
        long lastUpload = config.getLastUploadTime();
        tvLastUpload.setText("Последняя отправка: " + HeartbeatManager.formatTimestamp(lastUpload));
        
        // Успешно/Ошибка统计
        int success = config.getSuccessCount();
        int fail = config.getFailCount();
        tvStatistics.setText("Успешно: " + success + " | Ошибки: " + fail);
    }
    
    // ==================== HeartbeatListener 回调 ====================
    
    @Override
    public void onHeartbeatStarted() {
        if (isAdded()) {
            tvStatus.setText("Работает");
            tvStatus.setTextColor(0xFF00CC00);  // 深绿色
        }
    }
    
    @Override
    public void onHeartbeatStopped() {
        if (isAdded()) {
            updateStatusDisplay();
        }
    }
    
    @Override
    public void onHeartbeatSuccess(long timestamp) {
        if (isAdded()) {
            updateStatisticsDisplay();
            tvLastUpload.setText(HeartbeatManager.formatTimestamp(timestamp));
        }
    }
    
    @Override
    public void onHeartbeatFailed(String error) {
        if (isAdded()) {
            updateStatisticsDisplay();
            // 可选：显示ОшибкаУведомление
            // Toast.makeText(requireContext(), "Ошибка heartbeat: " + error, Toast.LENGTH_SHORT).show();
        }
    }
}
