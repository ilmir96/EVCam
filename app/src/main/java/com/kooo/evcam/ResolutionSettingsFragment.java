package com.kooo.evcam;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.util.Range;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kooo.evcam.camera.ImageAdjustManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 画质Настройки界面 Fragment（Разрешение、码率)
 */
public class ResolutionSettingsFragment extends Fragment {

    private static final String TAG = "ResolutionSettings";

    private AppConfig appConfig;
    
    // Разрешение相Выкл
    private Spinner resolutionSpinner;
    private TextView resolutionDescText;
    private List<String> resolutionOptions = new ArrayList<>();
    private String selectedResolution;
    
    // 码率相Выкл
    private Spinner bitrateSpinner;
    private TextView bitrateDescText;
    private List<String> bitrateOptions = new ArrayList<>();
    private String selectedBitrateLevel;
    
    // 帧率相Выкл
    private Spinner framerateSpinner;
    private TextView framerateDescText;
    private List<String> framerateOptions = new ArrayList<>();
    private String selectedFramerateLevel;
    
    // Информация显示
    private TextView currentParamsText;
    private TextView hardwareInfoText;
    
    // КамераИнформация
    private Map<String, CameraInfo> cameraInfoMap = new LinkedHashMap<>();
    
    //  否Выполняется инициализация
    private boolean isInitializing = false;
    
    // 亮度/Шумоподавление调节相Выкл
    private Button openAdjustWindowButton;
    private Button resetAdjustButton;
    private TextView imageAdjustCurrentText;
    private TextView imageAdjustSupportedText;
    private ImageAdjustManager imageAdjustManager;

    /**
     * КамераИнформация类
     */
    private static class CameraInfo {
        String cameraId;
        List<Size> supportedResolutions = new ArrayList<>();
        int maxFps = 30;  // максимум帧率
        int minFps = 15;  // минимум帧率
        String facing = "Неизвестно";  // направление
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_resolution_settings, container, false);

        // инициализацияПриложениеконфигурация
        if (getContext() != null) {
            appConfig = new AppConfig(getContext());
        }

        // инициализация控件
        initViews(view);

        // 检测КамераИнформация
        detectCameraInfo();

        // инициализацияРазрешениеВыбрать器
        initResolutionSpinner();
        
        // инициализация帧率Выбрать器（必须 码率до，因为码率计算依赖帧率)
        initFramerateSpinner();
        
        // инициализация码率Выбрать器
        initBitrateSpinner();
        
        // инициализация亮度/Шумоподавление调节
        initImageAdjust();

        // 显示отладкаИнформация
        displayDebugInfo();

        // Настройки返回按钮
        Button btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
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

        return view;
    }

    private void initViews(View view) {
        resolutionSpinner = view.findViewById(R.id.spinner_resolution);
        resolutionDescText = view.findViewById(R.id.tv_resolution_desc);
        bitrateSpinner = view.findViewById(R.id.spinner_bitrate);
        bitrateDescText = view.findViewById(R.id.tv_bitrate_desc);
        framerateSpinner = view.findViewById(R.id.spinner_framerate);
        framerateDescText = view.findViewById(R.id.tv_framerate_desc);
        currentParamsText = view.findViewById(R.id.tv_current_params);
        hardwareInfoText = view.findViewById(R.id.tv_hardware_info);
        
        // 亮度/Шумоподавление调节控件
        openAdjustWindowButton = view.findViewById(R.id.btn_open_adjust_window);
        resetAdjustButton = view.findViewById(R.id.btn_reset_adjust);
        imageAdjustCurrentText = view.findViewById(R.id.tv_image_adjust_current);
        imageAdjustSupportedText = view.findViewById(R.id.tv_image_adjust_supported);
    }

    /**
     * 检测所有КамераИнформация（Разрешение и 帧率)
     */
    private void detectCameraInfo() {
        if (getContext() == null) {
            return;
        }

        cameraInfoMap.clear();

        try {
            CameraManager cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIds = cameraManager.getCameraIdList();

            for (String cameraId : cameraIds) {
                try {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    CameraInfo info = new CameraInfo();
                    info.cameraId = cameraId;
                    
                    // ПолучениеКамеранаправление
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null) {
                        switch (facing) {
                            case CameraCharacteristics.LENS_FACING_FRONT:
                                info.facing = "Фронтальная";
                                break;
                            case CameraCharacteristics.LENS_FACING_BACK:
                                info.facing = "Задняя";
                                break;
                            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                                info.facing = "Внешняя";
                                break;
                        }
                    }
                    
                    // ПолучениеПоддерживаемые 帧率范围
                    Range<Integer>[] fpsRanges = characteristics.get(
                            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    if (fpsRanges != null && fpsRanges.length > 0) {
                        int maxFps = 0;
                        int minFps = Integer.MAX_VALUE;
                        for (Range<Integer> range : fpsRanges) {
                            if (range.getUpper() > maxFps) {
                                maxFps = range.getUpper();
                            }
                            if (range.getLower() < minFps) {
                                minFps = range.getLower();
                            }
                        }
                        info.maxFps = maxFps;
                        info.minFps = minFps;
                    }

                    // ПолучениеПоддерживаемые Разрешение
                    StreamConfigurationMap map = characteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        Size[] sizes = map.getOutputSizes(android.graphics.ImageFormat.PRIVATE);
                        if (sizes == null || sizes.length == 0) {
                            sizes = map.getOutputSizes(android.graphics.SurfaceTexture.class);
                        }

                        if (sizes != null && sizes.length > 0) {
                            for (Size size : sizes) {
                                info.supportedResolutions.add(size);
                            }
                            // 按Разрешение от 大 до 小排序
                            Collections.sort(info.supportedResolutions, (s1, s2) -> {
                                int pixels1 = s1.getWidth() * s1.getHeight();
                                int pixels2 = s2.getWidth() * s2.getHeight();
                                return pixels2 - pixels1;
                            });
                        }
                    }
                    
                    cameraInfoMap.put(cameraId, info);
                    
                } catch (CameraAccessException e) {
                    AppLog.e(TAG, "ПолучениеКамера " + cameraId + " 特性Ошибка", e);
                }
            }

            AppLog.d(TAG, "Обнаружено " + cameraInfoMap.size() + " камер(ы)");

        } catch (CameraAccessException e) {
            AppLog.e(TAG, "ПолучениеКамера列表Ошибка", e);
        }
    }

    /**
     * инициализацияРазрешениеВыбрать器
     */
    private void initResolutionSpinner() {
        if (resolutionSpinner == null || getContext() == null) {
            return;
        }

        isInitializing = true;

        // 构建Разрешение选项列表
        resolutionOptions.clear();
        resolutionOptions.add("По умолчанию (1280×800)");

        // 收集所有КамераПоддерживаемые Разрешение（去重)
        Set<String> allResolutions = new LinkedHashSet<>();
        for (CameraInfo info : cameraInfoMap.values()) {
            for (Size size : info.supportedResolutions) {
                allResolutions.add(size.getWidth() + "x" + size.getHeight());
            }
        }

        // 按像素数 от 大 до 小排序
        List<String> sortedResolutions = new ArrayList<>(allResolutions);
        Collections.sort(sortedResolutions, (r1, r2) -> {
            int[] p1 = AppConfig.parseResolution(r1);
            int[] p2 = AppConfig.parseResolution(r2);
            if (p1 == null || p2 == null) return 0;
            return (p2[0] * p2[1]) - (p1[0] * p1[1]);
        });

        resolutionOptions.addAll(sortedResolutions);

        // Настройки适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                resolutionOptions
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        resolutionSpinner.setAdapter(adapter);

        // НастройкиТекущий选项
        String currentResolution = (appConfig != null) ? appConfig.getTargetResolution() : AppConfig.RESOLUTION_DEFAULT;
        selectedResolution = currentResolution;
        int selectedIndex = 0;
        if (!AppConfig.RESOLUTION_DEFAULT.equals(currentResolution)) {
            for (int i = 1; i < resolutionOptions.size(); i++) {
                if (resolutionOptions.get(i).equals(currentResolution)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        resolutionSpinner.setSelection(selectedIndex);

        // НастройкиВыбрать监听器
        resolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isInitializing) {
                    return;
                }

                String newResolution;
                if (position == 0) {
                    newResolution = AppConfig.RESOLUTION_DEFAULT;
                    resolutionDescText.setText("По умолчанию: 1280×800, иначе ближайшее подходящее");
                } else {
                    newResolution = resolutionOptions.get(position);
                    resolutionDescText.setText("Приоритет: " + newResolution + ", если камера не поддерживает — ближайшее");
                }
                
                // 只 值变化时Сохранить
                if (!newResolution.equals(selectedResolution)) {
                    selectedResolution = newResolution;
                    saveResolution();
                }
                
                // обновление码率描述（因为Разрешение变化会影响рекомендуется码率)
                updateBitrateDescription();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        resolutionSpinner.post(() -> isInitializing = false);
    }

    /**
     * инициализация码率Выбрать器
     */
    private void initBitrateSpinner() {
        if (bitrateSpinner == null || getContext() == null) {
            return;
        }

        // 构建码率选项
        bitrateOptions.clear();
        bitrateOptions.add("Низкий (экономия места)");
        bitrateOptions.add("Стандартный (рекомендуется)");
        bitrateOptions.add("Высокий (высокое качество)");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                bitrateOptions
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        bitrateSpinner.setAdapter(adapter);

        // НастройкиТекущий选项
        String currentLevel = appConfig.getBitrateLevel();
        selectedBitrateLevel = currentLevel;
        int selectedIndex = 1;  // По умолчаниюСтандарт
        if (AppConfig.BITRATE_LOW.equals(currentLevel)) {
            selectedIndex = 0;
        } else if (AppConfig.BITRATE_HIGH.equals(currentLevel)) {
            selectedIndex = 2;
        }
        bitrateSpinner.setSelection(selectedIndex);

        // НастройкиВыбрать监听器
        bitrateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean isFirstSelection = true;
            
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newLevel;
                switch (position) {
                    case 0:
                        newLevel = AppConfig.BITRATE_LOW;
                        break;
                    case 2:
                        newLevel = AppConfig.BITRATE_HIGH;
                        break;
                    default:
                        newLevel = AppConfig.BITRATE_MEDIUM;
                        break;
                }
                
                // 只 值变化且非首 разВыбрать时Сохранить
                if (!isFirstSelection && !newLevel.equals(selectedBitrateLevel)) {
                    selectedBitrateLevel = newLevel;
                    saveBitrate();
                } else {
                    selectedBitrateLevel = newLevel;
                }
                isFirstSelection = false;
                
                updateBitrateDescription();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        // инициализация描述
        updateBitrateDescription();
    }

    /**
     * инициализация帧率Выбрать器
     */
    private void initFramerateSpinner() {
        if (framerateSpinner == null || getContext() == null) {
            return;
        }

        // 构建帧率选项
        framerateOptions.clear();
        framerateOptions.add("Стандартный (рекомендуется)");
        framerateOptions.add("Низкий (экономия места)");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                R.layout.spinner_item,
                framerateOptions
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        framerateSpinner.setAdapter(adapter);

        // НастройкиТекущий选项
        String currentLevel = appConfig.getFramerateLevel();
        selectedFramerateLevel = currentLevel;
        int selectedIndex = 0;  // По умолчаниюСтандарт
        if (AppConfig.FRAMERATE_LOW.equals(currentLevel)) {
            selectedIndex = 1;
        }
        framerateSpinner.setSelection(selectedIndex);

        // НастройкиВыбрать监听器
        framerateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean isFirstSelection = true;
            
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String newLevel = (position == 1) ? AppConfig.FRAMERATE_LOW : AppConfig.FRAMERATE_STANDARD;
                
                // 只 值变化且非首 разВыбрать时Сохранить
                if (!isFirstSelection && !newLevel.equals(selectedFramerateLevel)) {
                    selectedFramerateLevel = newLevel;
                    saveFramerate();
                } else {
                    selectedFramerateLevel = newLevel;
                }
                isFirstSelection = false;
                
                updateFramerateDescription();
                updateBitrateDescription();  // 帧率变化会影响码率计算
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        // инициализация描述
        updateFramerateDescription();
    }

    /**
     * обновление帧率描述
     */
    private void updateFramerateDescription() {
        if (framerateDescText == null) {
            return;
        }

        int standardFps = getStandardFrameRate();
        int lowFps = Math.max(10, standardFps / 2);

        String desc = String.format("Стандарт: %dfps | Низкий: %dfps", standardFps, lowFps);
        framerateDescText.setText(desc);
    }

    /**
     * ПолучениеСтандарт帧率（接近30fps 硬件поддержка帧率)
     */
    private int getStandardFrameRate() {
        int maxFps = 30;
        for (CameraInfo info : cameraInfoMap.values()) {
            if (info.maxFps > 0) {
                maxFps = info.maxFps;
                break;  // 假设所有Камера帧率相同
            }
        }
        return AppConfig.getStandardFrameRate(maxFps);
    }

    /**
     * 根据ТекущийВыбратьПолучение实际帧率
     */
    private int getSelectedFrameRate() {
        int standardFps = getStandardFrameRate();
        // 防止инициализация顺序导致  null 问题
        if (selectedFramerateLevel != null && AppConfig.FRAMERATE_LOW.equals(selectedFramerateLevel)) {
            return Math.max(10, standardFps / 2);
        }
        return standardFps;
    }

    /**
     * обновление码率描述（显示计算出 码率值)
     */
    private void updateBitrateDescription() {
        if (bitrateDescText == null) {
            return;
        }

        // Получение目标Разрешение
        int width = 1280;
        int height = 800;
        if (!AppConfig.RESOLUTION_DEFAULT.equals(selectedResolution)) {
            int[] parsed = AppConfig.parseResolution(selectedResolution);
            if (parsed != null) {
                width = parsed[0];
                height = parsed[1];
            }
        }

        // Получение帧率（根据用户Выбрать Уровень частоты кадров)
        int frameRate = getSelectedFrameRate();

        // 计算各等级码率
        int baseBitrate = AppConfig.calculateBitrate(width, height, frameRate);
        
        int lowBitrate = roundToHalfMbps(baseBitrate / 2);
        int mediumBitrate = roundToHalfMbps(baseBitrate);
        int highBitrate = roundToHalfMbps(baseBitrate * 3 / 2);

        String desc = String.format(
                "Разрешение %dx%d @ %dfps\nНизкий: %s | Стандарт: %s | Высокий: %s",
                width, height, frameRate,
                AppConfig.formatBitrate(lowBitrate),
                AppConfig.formatBitrate(mediumBitrate),
                AppConfig.formatBitrate(highBitrate)
        );
        bitrateDescText.setText(desc);
    }
    
    /**
     * 四舍五入 до  0.5Mbps
     */
    private int roundToHalfMbps(int bitrate) {
        int halfMbps = 500000;
        int rounded = ((bitrate + halfMbps / 2) / halfMbps) * halfMbps;
        return Math.max(halfMbps, Math.min(rounded, 20000000));
    }

    /**
     * 显示отладкаИнформация
     */
    private void displayDebugInfo() {
        displayCurrentParams();
        displayHardwareInfo();
    }

    /**
     * 显示ТекущийЗапись参数
     */
    private void displayCurrentParams() {
        if (currentParamsText == null || getActivity() == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        
        //  от  MainActivity ПолучениеТекущийРазрешениеИнформация
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            String resInfo = mainActivity.getCurrentCameraResolutionsInfo();
            if (resInfo != null && !resInfo.isEmpty()) {
                sb.append("【Фактическое разрешение】\n").append(resInfo).append("\n\n");
            }
        }
        
        // Текущие настройки
        String targetRes = appConfig.getTargetResolution();
        String bitrateLevel = AppConfig.getBitrateLevelDisplayName(appConfig.getBitrateLevel());
        String framerateLevel = AppConfig.getFramerateLevelDisplayName(appConfig.getFramerateLevel());
        int standardFps = getStandardFrameRate();
        int actualFps = appConfig.getActualFrameRate(standardFps);
        
        sb.append("【Текущие настройки】\n");
        sb.append("Целевое разрешение: ").append(AppConfig.RESOLUTION_DEFAULT.equals(targetRes) ? "По умолчанию (1280×800)" : targetRes).append("\n");
        sb.append("Уровень битрейта: ").append(bitrateLevel).append("\n");
        sb.append("Уровень частоты кадров: ").append(framerateLevel).append(" (").append(actualFps).append("fps)");

        currentParamsText.setText(sb.toString());
    }

    /**
     * 显示硬件Информация
     */
    private void displayHardwareInfo() {
        if (hardwareInfoText == null) {
            return;
        }

        if (cameraInfoMap.isEmpty()) {
            hardwareInfoText.setText("Камеры не обнаружены");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, CameraInfo> entry : cameraInfoMap.entrySet()) {
            String cameraId = entry.getKey();
            CameraInfo info = entry.getValue();

            sb.append("Камера ").append(cameraId).append(" (").append(info.facing).append(")\n");
            sb.append("  Частота кадров: ").append(info.minFps).append("-").append(info.maxFps).append(" fps\n");
            sb.append("  Разрешение:\n");
            
            int count = 0;
            for (Size size : info.supportedResolutions) {
                sb.append("    ").append(size.getWidth()).append("×").append(size.getHeight());
                count++;
                if (count >= 5) {
                    sb.append("\n    ... Всего  ").append(info.supportedResolutions.size()).append(" вариантов");
                    break;
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        hardwareInfoText.setText(sb.toString().trim());
    }

    /**
     * СохранитьРазрешениеНастройки
     */
    private void saveResolution() {
        if (appConfig == null || getContext() == null) {
            return;
        }
        
        String oldResolution = appConfig.getTargetResolution();
        appConfig.setTargetResolution(selectedResolution);
        
        String resolutionName = AppConfig.RESOLUTION_DEFAULT.equals(selectedResolution) 
                ? "По умолчанию (1280×800)" 
                : selectedResolution;
        
        Toast.makeText(getContext(), "Resolution set to: " + resolutionName + "\nRestart app to apply", Toast.LENGTH_SHORT).show();
        AppLog.d(TAG, "РазрешениеСохранить: " + oldResolution + " -> " + selectedResolution);
        
        // обновлениеТекущий参数显示
        displayCurrentParams();
    }
    
    /**
     * Сохранить码率Настройки
     */
    private void saveBitrate() {
        if (appConfig == null || getContext() == null) {
            return;
        }
        
        String oldBitrate = appConfig.getBitrateLevel();
        appConfig.setBitrateLevel(selectedBitrateLevel);
        
        String bitrateName = AppConfig.getBitrateLevelDisplayName(selectedBitrateLevel);
        
        Toast.makeText(getContext(), "Bitrate set to: " + bitrateName + "\nRestart app to apply", Toast.LENGTH_SHORT).show();
        AppLog.d(TAG, "码率Сохранить: " + oldBitrate + " -> " + selectedBitrateLevel);
        
        // обновлениеТекущий参数显示
        displayCurrentParams();
    }
    
    /**
     * Сохранить帧率Настройки
     */
    private void saveFramerate() {
        if (appConfig == null || getContext() == null) {
            return;
        }
        
        String oldFramerate = appConfig.getFramerateLevel();
        appConfig.setFramerateLevel(selectedFramerateLevel);
        
        String framerateName = AppConfig.getFramerateLevelDisplayName(selectedFramerateLevel);
        
        Toast.makeText(getContext(), "Frame rate set to: " + framerateName + "\nRestart app to apply", Toast.LENGTH_SHORT).show();
        AppLog.d(TAG, "帧率Сохранить: " + oldFramerate + " -> " + selectedFramerateLevel);
        
        // обновлениеТекущий参数显示
        displayCurrentParams();
    }
    
    // ==================== 亮度/Шумоподавление调节 ====================
    
    /**
     * инициализация亮度/Шумоподавление调节функция
     */
    private void initImageAdjust() {
        if (getContext() == null) {
            return;
        }
        
        // инициализация ImageAdjustManager
        imageAdjustManager = getImageAdjustManager();
        
        // 确保亮度/Шумоподавление调节始终Включить
        if (!appConfig.isImageAdjustEnabled()) {
            appConfig.setImageAdjustEnabled(true);
            // Уведомление MainActivity Включить亮度/Шумоподавление调节
            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                mainActivity.setImageAdjustEnabled(true);
            }
        }
        
        // открыть调节悬浮窗按钮
        if (openAdjustWindowButton != null) {
            openAdjustWindowButton.setOnClickListener(v -> openAdjustFloatingWindow());
        }
        
        // ВосстановлениеПо умолчанию按钮
        if (resetAdjustButton != null) {
            resetAdjustButton.setOnClickListener(v -> {
                if (imageAdjustManager != null) {
                    imageAdjustManager.resetToDefault();
                    updateImageAdjustParamsDisplay();
                    Toast.makeText(getContext(), "Brightness/denoise parameters reset to default", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // обновление参数显示
        updateImageAdjustParamsDisplay();
    }
    
    /**
     * Получение ImageAdjustManager 实例（ от  MainActivity Получение)
     */
    private ImageAdjustManager getImageAdjustManager() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            return mainActivity.getImageAdjustManager();
        }
        return null;
    }
    
    /**
     * обновление亮度/Шумоподавление参数显示
     */
    private void updateImageAdjustParamsDisplay() {
        // обновлениеТекущийНастройки显示
        if (imageAdjustCurrentText != null) {
            imageAdjustCurrentText.setText(buildCurrentParamsText());
        }
        
        // обновление设备поддержка显示
        if (imageAdjustSupportedText != null) {
            imageAdjustSupportedText.setText(buildSupportedParamsText());
        }
    }
    
    /**
     * 构建Текущий参数文本
     */
    private String buildCurrentParamsText() {
        StringBuilder sb = new StringBuilder();
        
        if (imageAdjustManager != null) {
            boolean hasActual = imageAdjustManager.hasActualParams();
            
            // Экспозиция
            int exposure = imageAdjustManager.getExposureCompensation();
            sb.append("Экспозиция: ").append(exposure > 0 ? "+" : "").append(exposure);
            
            // Баланс белого
            int awbMode = imageAdjustManager.getAwbMode();
            sb.append("\nБаланс белого: ").append(AppConfig.getAwbModeDisplayName(awbMode));
            if (awbMode == AppConfig.AWB_MODE_DEFAULT && hasActual) {
                sb.append("(факт.: ").append(AppConfig.getAwbModeDisplayName(imageAdjustManager.getActualAwbMode())).append(")");
            }
            
            // Тональная компрессия
            int tonemapMode = imageAdjustManager.getTonemapMode();
            sb.append("\nТональная компрессия: ").append(AppConfig.getTonemapModeDisplayName(tonemapMode));
            if (tonemapMode == AppConfig.TONEMAP_MODE_DEFAULT && hasActual) {
                sb.append("(факт.: ").append(AppConfig.getTonemapModeDisplayName(imageAdjustManager.getActualTonemapMode())).append(")");
            }
            
            // Резкость（锐度)
            int edgeMode = imageAdjustManager.getEdgeMode();
            sb.append("\nРезкость: ").append(AppConfig.getEdgeModeDisplayName(edgeMode));
            if (edgeMode == AppConfig.EDGE_MODE_DEFAULT && hasActual) {
                sb.append("(факт.: ").append(AppConfig.getEdgeModeDisplayName(imageAdjustManager.getActualEdgeMode())).append(")");
            }
            
            // Шумоподавление
            int noiseMode = imageAdjustManager.getNoiseReductionMode();
            sb.append("\nШумоподавление: ").append(AppConfig.getNoiseReductionModeDisplayName(noiseMode));
            if (noiseMode == AppConfig.NOISE_REDUCTION_DEFAULT && hasActual) {
                sb.append("(факт.: ").append(AppConfig.getNoiseReductionModeDisplayName(imageAdjustManager.getActualNoiseReductionMode())).append(")");
            }
            
            // Эффекты
            int effectMode = imageAdjustManager.getEffectMode();
            sb.append("\nЭффектырежим：").append(AppConfig.getEffectModeDisplayName(effectMode));
            if (effectMode == AppConfig.EFFECT_MODE_DEFAULT && hasActual) {
                sb.append("(факт.: ").append(AppConfig.getEffectModeDisplayName(imageAdjustManager.getActualEffectMode())).append(")");
            }
        } else {
            sb.append("Экспозиция: 0");
            sb.append("\nБаланс белого: По умолчанию");
            sb.append("\nТональная компрессия: По умолчанию");
            sb.append("\nРезкость: По умолчанию");
            sb.append("\nШумоподавление: По умолчанию");
            sb.append("\nЭффектырежим：По умолчанию");
        }
        
        return sb.toString();
    }
    
    /**
     * 构建设备Поддерживаемые 参数文本
     */
    private String buildSupportedParamsText() {
        StringBuilder sb = new StringBuilder();
        
        if (imageAdjustManager != null) {
            // Экспозиция范围
            if (imageAdjustManager.isExposureCompensationSupported()) {
                android.util.Range<Integer> range = imageAdjustManager.getExposureRange();
                if (range != null) {
                    int lower = range.getLower();
                    int upper = range.getUpper();
                    sb.append("Диапазон экспозиции: ").append(lower).append(" ~ ");
                    if (upper > 0) sb.append("+");
                    sb.append(upper);
                } else {
                    sb.append("Диапазон экспозиции: не поддерживается");
                }
            } else {
                sb.append("Диапазон экспозиции: не поддерживается");
            }
            
            // Поддерживаемые Баланс белогорежим
            sb.append("\nПоддерживаемые Баланс белого: ");
            int[] awbModes = imageAdjustManager.getSupportedAwbModes();
            if (awbModes != null && awbModes.length > 0) {
                sb.append(formatSupportedModes(awbModes, "awb"));
            } else {
                sb.append("не поддерживается");
            }
            
            // Поддерживаемые Тональная компрессиярежим
            sb.append("\nПоддерживаемые Тональная компрессия: ");
            int[] tonemapModes = imageAdjustManager.getSupportedTonemapModes();
            if (tonemapModes != null && tonemapModes.length > 0) {
                sb.append(formatSupportedModes(tonemapModes, "tonemap"));
            } else {
                sb.append("не поддерживается");
            }
            
            // Поддерживаемые Резкостьрежим
            sb.append("\nПоддерживаемые Резкость: ");
            int[] edgeModes = imageAdjustManager.getSupportedEdgeModes();
            if (edgeModes != null && edgeModes.length > 0) {
                sb.append(formatSupportedModes(edgeModes, "edge"));
            } else {
                sb.append("не поддерживается");
            }
            
            // Поддерживаемые Шумоподавлениережим
            sb.append("\nПоддерживаемые Шумоподавление: ");
            int[] noiseModes = imageAdjustManager.getSupportedNoiseReductionModes();
            if (noiseModes != null && noiseModes.length > 0) {
                sb.append(formatSupportedModes(noiseModes, "noise"));
            } else {
                sb.append("не поддерживается");
            }
            
            // Поддерживаемые Эффектырежим
            sb.append("\nПоддерживаемые Эффектырежим：");
            int[] effectModes = imageAdjustManager.getSupportedEffectModes();
            if (effectModes != null && effectModes.length > 0) {
                sb.append(formatSupportedModes(effectModes, "effect"));
            } else {
                sb.append("не поддерживается");
            }
        } else {
            sb.append("Камера не готова, невозможно получить параметры");
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化Поддерживаемые режим列表
     */
    private String formatSupportedModes(int[] modes, String type) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modes.length; i++) {
            if (i > 0) sb.append("、");
            switch (type) {
                case "awb":
                    sb.append(AppConfig.getAwbModeDisplayName(modes[i]));
                    break;
                case "tonemap":
                    sb.append(AppConfig.getTonemapModeDisplayName(modes[i]));
                    break;
                case "edge":
                    sb.append(AppConfig.getEdgeModeDisplayName(modes[i]));
                    break;
                case "noise":
                    sb.append(AppConfig.getNoiseReductionModeDisplayName(modes[i]));
                    break;
                case "effect":
                    sb.append(AppConfig.getEffectModeDisplayName(modes[i]));
                    break;
            }
        }
        return sb.toString();
    }
    
    /**
     * открыть调节悬浮窗
     * 悬浮窗由 MainActivity управление，这样т.е.使ВыходНастройки页面также能保持显示
     */
    private void openAdjustFloatingWindow() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.showImageAdjustFloatingWindow();
            
            // 返回主界面以便Просмотр预览效果
            Toast.makeText(getContext(), "Floating window opened, return to preview to see real-time effect", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 每 раз返回此页面时обновление参数显示
        updateImageAdjustParamsDisplay();
    }
}
