package com.kooo.evcam;

import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.kooo.evcam.dingtalk.DingTalkApiClient;
import com.kooo.evcam.dingtalk.DingTalkConfig;

public class RemoteViewFragment extends Fragment {
    private static final String TAG = "RemoteViewFragment";

    private EditText etClientId, etClientSecret;
    private Button btnSaveConfig, btnStartService, btnStopService, btnMenu;
    private Button btnTestConnection;
    private ImageButton btnToggleSecretVisibility;
    private TextView tvConnectionStatus;
    private SwitchCompat switchAutoStart;
    private boolean isSecretVisible = false;

    private DingTalkConfig config;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_remote_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadConfig();
        setupListeners();
    }

    private void initViews(View view) {
        btnMenu = view.findViewById(R.id.btn_menu);
        Button btnHome = view.findViewById(R.id.btn_home);
        etClientId = view.findViewById(R.id.et_client_id);
        etClientSecret = view.findViewById(R.id.et_client_secret);
        btnSaveConfig = view.findViewById(R.id.btn_save_config);
        btnTestConnection = view.findViewById(R.id.btn_test_connection);
        btnToggleSecretVisibility = view.findViewById(R.id.btn_toggle_secret_visibility);
        btnStartService = view.findViewById(R.id.btn_start_service);
        btnStopService = view.findViewById(R.id.btn_stop_service);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        switchAutoStart = view.findViewById(R.id.switch_auto_start);
        config = new DingTalkConfig(requireContext());

        // 主页按钮 - 返回预览界面
        btnHome.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).goToRecordingInterface();
            }
        });

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
    }

    private void loadConfig() {
        if (config.isConfigured()) {
            etClientId.setText(config.getClientId());
            etClientSecret.setText(config.getClientSecret());
        }
        // загрузкаавтоматическиЗапускНастройки
        switchAutoStart.setChecked(config.isAutoStart());
    }

    private void setupListeners() {
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
        btnSaveConfig.setOnClickListener(v -> saveConfig());
        btnTestConnection.setOnClickListener(v -> testConnection());
        btnStartService.setOnClickListener(v -> startService());
        btnStopService.setOnClickListener(v -> stopService());

        // 密码可见性切换
        btnToggleSecretVisibility.setOnClickListener(v -> toggleSecretVisibility());

        // автоматическиЗапускВклВыкл监听
        switchAutoStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setAutoStart(isChecked);
            Toast.makeText(requireContext(),
                isChecked ? "Автозапуск включён" : "Автозапуск выключен",
                Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 切换密码可见性
     */
    private void toggleSecretVisibility() {
        isSecretVisible = !isSecretVisible;
        if (isSecretVisible) {
            // 显示密码
            etClientSecret.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            btnToggleSecretVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            // 隐藏密码
            etClientSecret.setTransformationMethod(PasswordTransformationMethod.getInstance());
            btnToggleSecretVisibility.setImageResource(R.drawable.ic_visibility);
        }
        // 将光标移 до 末尾
        etClientSecret.setSelection(etClientSecret.getText().length());
    }

    /**
     * тестированиеПодключение - 通过Получение AccessToken 验证凭证 否正确
     */
    private void testConnection() {
        String clientId = etClientId.getText().toString().trim();
        String clientSecret = etClientSecret.getText().toString().trim();

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            Toast.makeText(requireContext(), "Укажите ClientId и ClientSecret", Toast.LENGTH_SHORT).show();
            return;
        }

        // Отключить按钮，防止重复点击
        btnTestConnection.setEnabled(false);
        btnTestConnection.setText("Тестирование...");

        //  Фоновый режим线程выполнениетестирование
        new Thread(() -> {
            try {
                // 创建временноконфигурация
                DingTalkConfig tempConfig = new DingTalkConfig(requireContext());
                tempConfig.saveConfig(clientId, clientSecret);
                
                // очистка缓存  token，强制重新Получение
                tempConfig.clearAccessToken();
                
                // попыткаПолучение AccessToken
                DingTalkApiClient apiClient = new DingTalkApiClient(tempConfig);
                apiClient.getAccessToken();
                
                // Успешно
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        btnTestConnection.setEnabled(true);
                        btnTestConnection.setText("тестированиеПодключение");
                        Toast.makeText(requireContext(), "✅ Подключение успешно! Данные верны", Toast.LENGTH_LONG).show();
                        tvConnectionStatus.setText("Данные верны");
                        tvConnectionStatus.setTextColor(0xFF66FF66);
                    });
                }
            } catch (Exception e) {
                // Ошибка
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("errcode")) {
                    // 解析DingTalkОшибкаИнформация
                    if (errorMsg.contains("40089") || errorMsg.contains("invalid appkey")) {
                        errorMsg = "ClientId/AppKey недействителен";
                    } else if (errorMsg.contains("43003") || errorMsg.contains("secret")) {
                        errorMsg = "ClientSecret/AppSecret недействителен";
                    }
                }
                final String finalErrorMsg = errorMsg;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        btnTestConnection.setEnabled(true);
                        btnTestConnection.setText("тестированиеПодключение");
                        Toast.makeText(requireContext(), "Ошибка подключения: " + finalErrorMsg, Toast.LENGTH_LONG).show();
                        tvConnectionStatus.setText("Данные недействительны");
                        tvConnectionStatus.setTextColor(0xFFFF6666);
                    });
                }
                AppLog.e(TAG, "тестированиеОшибка подключения", e);
            }
        }).start();
    }

    private void saveConfig() {
        String clientId = etClientId.getText().toString().trim();
        String clientSecret = etClientSecret.getText().toString().trim();

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            Toast.makeText(requireContext(), "Заполните все поля настроек", Toast.LENGTH_SHORT).show();
            return;
        }

        config.saveConfig(clientId, clientSecret);
        Toast.makeText(requireContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show();
    }

    private void startService() {
        if (!config.isConfigured()) {
            Toast.makeText(requireContext(), "Сначала сохраните настройки", Toast.LENGTH_SHORT).show();
            return;
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).startDingTalkService();
        }
    }

    private void stopService() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).stopDingTalkService();
        }
    }

    /**
     * обновлениеСервисСтатус显示（由 MainActivity 调用)
     */
    public void updateServiceStatus() {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            boolean isRunning = activity.isDingTalkServiceRunning();

            if (isRunning) {
                tvConnectionStatus.setText("Подключён");
                tvConnectionStatus.setTextColor(0xFF66FF66);
                btnStartService.setEnabled(false);
                btnStopService.setEnabled(true);
            } else {
                tvConnectionStatus.setText("Не подключён");
                tvConnectionStatus.setTextColor(0xFFFF6666);
                btnStartService.setEnabled(true);
                btnStopService.setEnabled(false);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每 раз显示时обновлениеСтатус
        updateServiceStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 不再 这里ОстановкаСервис，Сервис由 MainActivity управление
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 不再 这里ОстановкаСервис，Сервис由 MainActivity управление
    }
}
