package com.kooo.evcam;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义Камераконфигурация界面 Fragment
 */
public class CustomCameraConfigFragment extends Fragment {

    private static final String TAG = "CustomCameraConfig";
    
    private AppConfig appConfig;
    
    // Камера数量Выбрать
    private Spinner cameraCountSpinner;
    private static final String[] CAMERA_COUNT_OPTIONS = {"4 камер(ы)", "2 камер(ы)", "1 камер(ы)"};

    // 按钮样式选项
    private static final String[] BUTTON_STYLE_OPTIONS = {"Стандартные кнопки", "Мульти-кнопки"};
    private static final String[] BUTTON_STYLE_VALUES = {AppConfig.BUTTON_STYLE_STANDARD, AppConfig.BUTTON_STYLE_MULTI};

    // Камераконфигурация区域
    private LinearLayout configFront, configBack, configLeft, configRight;
    
    // Камера编号Выбрать器
    private Spinner spinnerFrontId, spinnerBackId, spinnerLeftId, spinnerRightId;

    // Камера名称Ввести框
    private EditText editFrontName, editBackName, editLeftName, editRightName;

    // 自由操控конфигурация
    private SwitchMaterial switchFreeControl;
    private Spinner spinnerButtonStyle;
    
    // 布局数据编辑
    private EditText editLayoutData;
    private Button btnCopyLayout;
    private Button btnSaveLayout;
    
    // Доступно КамераID列表
    private List<String> availableCameraIds = new ArrayList<>();
    
    //  否завершение初始загрузка（避免загрузка时触发Сохранить)
    private boolean configInitialized = false;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_custom_camera_config, container, false);
        
        // Сбросинициализация标记
        configInitialized = false;
        
        // инициализацияПриложениеконфигурация
        if (getContext() != null) {
            appConfig = new AppConfig(getContext());
        }
        
        // инициализация控件
        initViews(view);
        
        // 检测Доступно Камера
        detectAvailableCameras();
        
        // инициализация拉Выбрать器
        initSpinners();
        
        // загрузкаСохранить конфигурация
        loadSavedConfig();
        
        // Настройки返回按钮
        Button btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });
        
        // НастройкиСброс所有конфигурация按钮
        Button btnResetAllConfig = view.findViewById(R.id.btn_reset_all_config);
        if (btnResetAllConfig != null) {
            btnResetAllConfig.setOnClickListener(v -> resetAllCustomConfig());
        }
        
        // Настройкиперезагрузка按钮
        Button btnRestartApp = view.findViewById(R.id.btn_restart_app);
        if (btnRestartApp != null) {
            btnRestartApp.setOnClickListener(v -> restartApp());
        }
        
        // НастройкиавтоматическиСохранить监听器
        setupAutoSaveListeners();
        
        // загрузка布局数据 до 编辑框
        loadLayoutData();
        
        // Настройки布局数据按钮事件
        setupLayoutDataButtons();
        
        // 延迟Настройкиинициализациязавершение标记，确保 loadSavedConfig 触发  Spinner Выбрать不会触发Сохранить
        view.postDelayed(() -> {
            configInitialized = true;
            AppLog.d(TAG, "конфигурация界面инициализациязавершение，автоматическиСохранитьВключено");
        }, 300);
        
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
        cameraCountSpinner = view.findViewById(R.id.spinner_camera_count);
        
        configFront = view.findViewById(R.id.config_front);
        configBack = view.findViewById(R.id.config_back);
        configLeft = view.findViewById(R.id.config_left);
        configRight = view.findViewById(R.id.config_right);
        
        spinnerFrontId = view.findViewById(R.id.spinner_front_id);
        spinnerBackId = view.findViewById(R.id.spinner_back_id);
        spinnerLeftId = view.findViewById(R.id.spinner_left_id);
        spinnerRightId = view.findViewById(R.id.spinner_right_id);

        editFrontName = view.findViewById(R.id.edit_front_name);
        editBackName = view.findViewById(R.id.edit_back_name);
        editLeftName = view.findViewById(R.id.edit_left_name);
        editRightName = view.findViewById(R.id.edit_right_name);

        // 自由操控конфигурация
        switchFreeControl = view.findViewById(R.id.switch_free_control);
        spinnerButtonStyle = view.findViewById(R.id.spinner_button_style);
        
        // 布局数据编辑
        editLayoutData = view.findViewById(R.id.edit_layout_data);
        btnCopyLayout = view.findViewById(R.id.btn_copy_layout);
        btnSaveLayout = view.findViewById(R.id.btn_save_layout);
    }
    
    /**
     * 检测Доступно Камера
     * 会验证每 шт.Камера 否真正Доступно（有действует 输出格式)
     */
    private void detectAvailableCameras() {
        if (getContext() == null) {
            return;
        }
        
        try {
            CameraManager cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIds = cameraManager.getCameraIdList();
            
            availableCameraIds.clear();
            int invalidCount = 0;
            
            for (String id : cameraIds) {
                // 验证Камера 否真正Доступно
                if (isCameraValid(cameraManager, id)) {
                    availableCameraIds.add(id);
                } else {
                    invalidCount++;
                    if (invalidCount <= 3) {  // 只记录前几 шт.недействительно ，避免 д.志过多
                        AppLog.d(TAG, "Камера " + id + " недействительно（虚拟Камера？)，跳过");
                    }
                }
            }
            
            if (invalidCount > 3) {
                AppLog.d(TAG, "还有 " + (invalidCount - 3) + "  шт.недействительноКамера跳过");
            }
            
            AppLog.d(TAG, "Обнаружено " + cameraIds.length + " камер(ы)ID，其 " + 
                    availableCameraIds.size() + "  шт.действует: " + availableCameraIds);
            
        } catch (CameraAccessException e) {
            AppLog.e(TAG, "检测КамераОшибка", e);
            // Если 检测Ошибка，提供По умолчанию选项
            availableCameraIds.clear();
            for (int i = 0; i < 4; i++) {
                availableCameraIds.add(String.valueOf(i));
            }
        }
    }
    
    /**
     * 验证Камера 否真正Доступно
     * проверкаКамера 否有действует 输出格式 и Разрешение
     * @param cameraManager CameraManager实例
     * @param cameraId 要验证 КамераID
     * @return trueЕсли КамераДоступно，falseЕсли  虚拟/недействительноКамера
     */
    private boolean isCameraValid(CameraManager cameraManager, String cameraId) {
        try {
            android.hardware.camera2.CameraCharacteristics characteristics = 
                    cameraManager.getCameraCharacteristics(cameraId);
            
            // проверка 否有действует 输出конфигурация
            android.hardware.camera2.params.StreamConfigurationMap map = 
                    characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            if (map == null) {
                return false;
            }
            
            // проверка 否有 PRIVATE или SurfaceTexture  输出尺寸
            android.util.Size[] privateSizes = map.getOutputSizes(android.graphics.ImageFormat.PRIVATE);
            android.util.Size[] textureSizes = map.getOutputSizes(android.graphics.SurfaceTexture.class);
            
            boolean hasValidOutput = (privateSizes != null && privateSizes.length > 0) ||
                                    (textureSizes != null && textureSizes.length > 0);
            
            return hasValidOutput;
            
        } catch (CameraAccessException e) {
            AppLog.d(TAG, "Камера " + cameraId + " доступОшибка: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            AppLog.d(TAG, "Камера " + cameraId + " 参数недействительно: " + e.getMessage());
            return false;
        } catch (Exception e) {
            AppLog.d(TAG, "Камера " + cameraId + " 验证аномалия: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * инициализация拉Выбрать器
     */
    private void initSpinners() {
        if (getContext() == null) {
            return;
        }
        
        // Камера数量Выбрать器
        ArrayAdapter<String> countAdapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                CAMERA_COUNT_OPTIONS
        );
        countAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        cameraCountSpinner.setAdapter(countAdapter);
        
        // КамераIDВыбрать器
        ArrayAdapter<String> idAdapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                availableCameraIds
        );
        idAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        
        spinnerFrontId.setAdapter(idAdapter);
        spinnerBackId.setAdapter(idAdapter);
        spinnerLeftId.setAdapter(idAdapter);
        spinnerRightId.setAdapter(idAdapter);

        // 按钮样式Выбрать器
        ArrayAdapter<String> buttonStyleAdapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                BUTTON_STYLE_OPTIONS
        );
        buttonStyleAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerButtonStyle.setAdapter(buttonStyleAdapter);
    }
    
    /**
     * НастройкиавтоматическиСохранить监听器
     * 当任何конфигурация项变更时автоматическиСохранить
     */
    /**
     * загрузка布局数据 до 编辑框
     */
    private void loadLayoutData() {
        if (editLayoutData != null && appConfig != null) {
            String layoutData = appConfig.getCustomLayoutData();
            if (layoutData != null && !layoutData.isEmpty()) {
                editLayoutData.setText(layoutData);
            } else {
                editLayoutData.setText("");
                editLayoutData.setHint("Нет данных макета");
            }
        }
    }
    
    /**
     * Настройки布局数据按钮事件
     */
    private void setupLayoutDataButtons() {
        // 复制按钮
        if (btnCopyLayout != null) {
            btnCopyLayout.setOnClickListener(v -> {
                if (editLayoutData != null && getContext() != null) {
                    String text = editLayoutData.getText().toString();
                    if (!text.isEmpty()) {
                        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("layout_data", text);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getContext(), "Данные макета скопированы", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Нет данных для копирования", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        // Сохранить按钮
        if (btnSaveLayout != null) {
            btnSaveLayout.setOnClickListener(v -> {
                if (editLayoutData != null && appConfig != null && getContext() != null) {
                    String text = editLayoutData.getText().toString().trim();
                    if (!text.isEmpty()) {
                        // 简单验证 JSON 格式
                        if (text.startsWith("{") && text.endsWith("}")) {
                            appConfig.setCustomLayoutData(text);
                            Toast.makeText(getContext(), "Данные макета сохранены, вступят в силу после перезагрузки", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Некорректный формат JSON", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // 清空布局数据
                        appConfig.clearCustomLayoutData();
                        Toast.makeText(getContext(), "Данные макета очищены", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
    
    private void setupAutoSaveListeners() {
        // 通用  Spinner 变更监听器
        AdapterView.OnItemSelectedListener autoSaveSpinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (configInitialized) {
                    saveConfig();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        
        // 为Камера数量 Spinner 添加监听
        cameraCountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int count;
                if (position == 0) {
                    count = 4;
                } else if (position == 1) {
                    count = 2;
                } else {
                    count = 1;
                }
                updateConfigVisibility(count);
                if (configInitialized) {
                    saveConfig();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        spinnerFrontId.setOnItemSelectedListener(autoSaveSpinnerListener);
        spinnerBackId.setOnItemSelectedListener(autoSaveSpinnerListener);
        spinnerLeftId.setOnItemSelectedListener(autoSaveSpinnerListener);
        spinnerRightId.setOnItemSelectedListener(autoSaveSpinnerListener);
        spinnerButtonStyle.setOnItemSelectedListener(autoSaveSpinnerListener);
        
        // Switch 变更监听
        switchFreeControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (configInitialized) {
                saveConfig();
            }
        });
        
        // EditText 变更监听（失去焦点时Сохранить)
        View.OnFocusChangeListener autoSaveFocusListener = (v, hasFocus) -> {
            if (!hasFocus && configInitialized) {
                saveConfig();
            }
        };
        
        editFrontName.setOnFocusChangeListener(autoSaveFocusListener);
        editBackName.setOnFocusChangeListener(autoSaveFocusListener);
        editLeftName.setOnFocusChangeListener(autoSaveFocusListener);
        editRightName.setOnFocusChangeListener(autoSaveFocusListener);
    }
    
    /**
     * 根据Камера数量обновлениеконфигурация区域 可见性
     */
    private void updateConfigVisibility(int count) {
        // Позиция1（前)始终显示
        configFront.setVisibility(View.VISIBLE);
        
        // Позиция2（后) 2 шт.или4 шт.Камера时显示
        configBack.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
        
        // Позиция3 и 4（左右)только 4 шт.Камера时显示
        configLeft.setVisibility(count >= 4 ? View.VISIBLE : View.GONE);
        configRight.setVisibility(count >= 4 ? View.VISIBLE : View.GONE);
    }
    
    /**
     * загрузкаСохранить конфигурация
     */
    private void loadSavedConfig() {
        if (appConfig == null) {
            return;
        }
        
        // загрузкаКамера数量
        int count = appConfig.getCameraCount();
        int countIndex;
        
        if (count == 4) {
            countIndex = 0;  // 4 шт.Камера
        } else if (count == 2) {
            countIndex = 1;  // 2 шт.Камера
        } else {
            countIndex = 2;  // 1 шт.Камера
        }
        
        cameraCountSpinner.setSelection(countIndex);
        updateConfigVisibility(count);
        
        // загрузкаКамераID
        setSpinnerSelection(spinnerFrontId, appConfig.getCameraId("front"));
        setSpinnerSelection(spinnerBackId, appConfig.getCameraId("back"));
        setSpinnerSelection(spinnerLeftId, appConfig.getCameraId("left"));
        setSpinnerSelection(spinnerRightId, appConfig.getCameraId("right"));

        // загрузкаКамера名称
        editFrontName.setText(appConfig.getCameraName("front"));
        editBackName.setText(appConfig.getCameraName("back"));
        editLeftName.setText(appConfig.getCameraName("left"));
        editRightName.setText(appConfig.getCameraName("right"));

        // загрузка自由操控конфигурация
        switchFreeControl.setChecked(appConfig.isCustomFreeControlEnabled());
        setButtonStyleSpinnerSelection(spinnerButtonStyle, appConfig.getCustomButtonStyle());
    }

    /**
     * Настройки按钮样式 Spinner  选项
     */
    private void setButtonStyleSpinnerSelection(Spinner spinner, String style) {
        int index = 0;
        for (int i = 0; i < BUTTON_STYLE_VALUES.length; i++) {
            if (BUTTON_STYLE_VALUES[i].equals(style)) {
                index = i;
                break;
            }
        }
        spinner.setSelection(index);
    }

    /**
     * Настройки Spinner  选项
     */
    private void setSpinnerSelection(Spinner spinner, String value) {
        int index = availableCameraIds.indexOf(value);
        if (index >= 0) {
            spinner.setSelection(index);
        } else if (!availableCameraIds.isEmpty()) {
            spinner.setSelection(0);
        }
    }

    /**
     * Сохранитьконфигурация
     */
    private void saveConfig() {
        if (appConfig == null || getContext() == null) {
            return;
        }
        
        // СохранитьКамера数量
        int countIndex = cameraCountSpinner.getSelectedItemPosition();
        int count;
        
        if (countIndex == 0) {
            count = 4;  // 4 шт.Камера
        } else if (countIndex == 1) {
            count = 2;  // 2 шт.Камера
        } else {
            count = 1;  // 1 шт.Камера
        }
        
        appConfig.setCameraCount(count);
        appConfig.setScreenOrientation("landscape");  // 统一использование横屏режим
        
        // СохранитьКамераID
        appConfig.setCameraId("front", getSpinnerValue(spinnerFrontId));
        if (count >= 2) {
            appConfig.setCameraId("back", getSpinnerValue(spinnerBackId));
        }
        if (count >= 4) {
            appConfig.setCameraId("left", getSpinnerValue(spinnerLeftId));
            appConfig.setCameraId("right", getSpinnerValue(spinnerRightId));
        }

        // СохранитьКамера名称
        appConfig.setCameraName("front", editFrontName.getText().toString().trim());
        if (count >= 2) {
            appConfig.setCameraName("back", editBackName.getText().toString().trim());
        }
        if (count >= 4) {
            appConfig.setCameraName("left", editLeftName.getText().toString().trim());
            appConfig.setCameraName("right", editRightName.getText().toString().trim());
        }

        // Сохранить自由操控конфигурация
        appConfig.setCustomFreeControlEnabled(switchFreeControl.isChecked());
        
        // Сохранить按钮样式
        String buttonStyleValue = getButtonStyleSpinnerValue();
        appConfig.setCustomButtonStyle(buttonStyleValue);
        
        AppLog.d(TAG, "конфигурацияавтоматическиСохранить: Камера数量=" + count + ", 自由操控=" + switchFreeControl.isChecked() + ", 按钮样式=" + buttonStyleValue);
    }
    
    /**
     * Получение按钮样式 Spinner  值
     */
    private String getButtonStyleSpinnerValue() {
        int position = spinnerButtonStyle.getSelectedItemPosition();
        if (position >= 0 && position < BUTTON_STYLE_VALUES.length) {
            return BUTTON_STYLE_VALUES[position];
        }
        return AppConfig.BUTTON_STYLE_STANDARD;
    }
    
    /**
     * Получение Spinner Текущий选 值
     */
    private String getSpinnerValue(Spinner spinner) {
        Object selectedItem = spinner.getSelectedItem();
        return selectedItem != null ? selectedItem.toString() : "0";
    }
    
    /**
     * Сброс所有自定义конфигурация
     */
    private void resetAllCustomConfig() {
        if (appConfig == null) return;
        
        // ОтключитьавтоматическиСохранить，防止Сброс过程触发Сохранить
        configInitialized = false;
        
        // СбросКамера映射
        appConfig.setCameraCount(4);
        appConfig.setCameraId("front", "0");
        appConfig.setCameraId("back", "1");
        appConfig.setCameraId("left", "2");
        appConfig.setCameraId("right", "3");
        appConfig.setCameraName("front", "П");
        appConfig.setCameraName("back", "З");
        appConfig.setCameraName("left", "Л");
        appConfig.setCameraName("right", "Пр");
        
        // СбросКамераПоворот  и 镜像
        appConfig.setCameraRotation("front", 0);
        appConfig.setCameraRotation("back", 0);
        appConfig.setCameraRotation("left", 0);
        appConfig.setCameraRotation("right", 0);
        appConfig.setCameraMirror("front", false);
        appConfig.setCameraMirror("back", false);
        appConfig.setCameraMirror("left", false);
        appConfig.setCameraMirror("right", false);
        
        // Сброс裁剪конфигурация
        appConfig.resetCameraCrop("front");
        appConfig.resetCameraCrop("back");
        appConfig.resetCameraCrop("left");
        appConfig.resetCameraCrop("right");
        
        // Сброс布局数据
        appConfig.clearCustomLayoutData();
        
        // Сброс自由操控ВклВыкл
        appConfig.setCustomFreeControlEnabled(false);
        
        // Сброс按钮样式 и 方 к 
        appConfig.setCustomButtonStyle(AppConfig.BUTTON_STYLE_STANDARD);
        appConfig.setCustomButtonOrientation(AppConfig.BUTTON_ORIENTATION_HORIZONTAL);
        
        AppLog.d(TAG, "所有自定义конфигурацияСброс");
        
        // 重新загрузкаконфигурация до 界面
        loadSavedConfig();
        
        // 重新ВключитьавтоматическиСохранить
        if (getView() != null) {
            getView().postDelayed(() -> {
                configInitialized = true;
            }, 300);
        }
        
        // Уведомление用户
        if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), "Настройки сброшены, перезапустите приложение", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * 重载界面（重新创建 Activity)
     */
    private void restartApp() {
        if (getActivity() == null) return;

        android.widget.Toast.makeText(getContext(), "Перезагрузка интерфейса...", android.widget.Toast.LENGTH_SHORT).show();
        com.kooo.evcam.camera.CameraManagerHolder.getInstance().setCameraManager(null);
        getActivity().recreate();
    }
}
