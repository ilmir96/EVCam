package com.kooo.evcam.camera;

import android.content.Context;
import android.util.Range;

import com.kooo.evcam.AppConfig;
import com.kooo.evcam.AppLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 亮度/Шумоподавление调节управление器
 * управление所有Камера 亮度/Шумоподавление参数，协调多 шт.Камера 参数同步обновление
 */
public class ImageAdjustManager {
    private static final String TAG = "ImageAdjustManager";
    
    private final Context context;
    private final AppConfig appConfig;
    private final List<SingleCamera> cameras = new ArrayList<>();
    
    // Текущий参数值（内存缓存，用于实时调节时同步 до 所有Камера)
    private int exposureCompensation = 0;
    private int awbMode = AppConfig.AWB_MODE_DEFAULT;
    private int tonemapMode = AppConfig.TONEMAP_MODE_DEFAULT;
    private int edgeMode = AppConfig.EDGE_MODE_DEFAULT;
    private int noiseReductionMode = AppConfig.NOISE_REDUCTION_DEFAULT;
    private int effectMode = AppConfig.EFFECT_MODE_DEFAULT;
    
    // 参数范围（ от Первый шт.КамераПолучение，假设所有Камера范围相同)
    private Range<Integer> exposureRange = null;
    private int[] supportedAwbModes = null;
    private int[] supportedTonemapModes = null;
    private int[] supportedEdgeModes = null;
    private int[] supportedNoiseReductionModes = null;
    private int[] supportedEffectModes = null;
    
    // 回调接口
    public interface OnParamsChangedListener {
        void onParamsChanged();
    }
    
    private OnParamsChangedListener listener;
    
    public ImageAdjustManager(Context context) {
        this.context = context;
        this.appConfig = new AppConfig(context);
        
        //  от конфигурациязагрузкаСохранить 参数
        loadParamsFromConfig();
    }
    
    /**
     * Настройки参数变化监听器
     */
    public void setOnParamsChangedListener(OnParamsChangedListener listener) {
        this.listener = listener;
    }
    
    /**
     *  от конфигурациязагрузка参数
     */
    private void loadParamsFromConfig() {
        exposureCompensation = appConfig.getExposureCompensation();
        awbMode = appConfig.getAwbMode();
        tonemapMode = appConfig.getTonemapMode();
        edgeMode = appConfig.getEdgeMode();
        noiseReductionMode = appConfig.getNoiseReductionMode();
        effectMode = appConfig.getEffectMode();
        
        AppLog.d(TAG, "Loaded params from config: exposure=" + exposureCompensation + 
                ", awb=" + awbMode + ", tonemap=" + tonemapMode);
    }
    
    /**
     * Сохранить参数 до конфигурация
     */
    public void saveParamsToConfig() {
        appConfig.setExposureCompensation(exposureCompensation);
        appConfig.setAwbMode(awbMode);
        appConfig.setTonemapMode(tonemapMode);
        appConfig.setEdgeMode(edgeMode);
        appConfig.setNoiseReductionMode(noiseReductionMode);
        appConfig.setEffectMode(effectMode);
        
        AppLog.d(TAG, "Saved params to config");
    }
    
    /**
     * 注册Камера
     * @param camera 要注册 Камера
     */
    public void registerCamera(SingleCamera camera) {
        if (camera != null && !cameras.contains(camera)) {
            cameras.add(camera);
            
            // Если Включить亮度/Шумоподавление调节，НастройкиКамера ВключитьСтатус
            if (appConfig.isImageAdjustEnabled()) {
                camera.setImageAdjustEnabled(true);
            }
            
            //  от Первый шт.КамераПолучение参数范围
            if (cameras.size() == 1) {
                detectSupportedParams(camera);
            }
            
            AppLog.d(TAG, "Registered camera: " + camera.getCameraId() + ", total: " + cameras.size());
        }
    }
    
    /**
     * 注销Камера
     * @param camera 要注销 Камера
     */
    public void unregisterCamera(SingleCamera camera) {
        if (camera != null) {
            cameras.remove(camera);
            AppLog.d(TAG, "Unregistered camera: " + camera.getCameraId() + ", remaining: " + cameras.size());
        }
    }
    
    /**
     * 清空所有注册 Камера
     */
    public void clearCameras() {
        cameras.clear();
        AppLog.d(TAG, "Cleared all cameras");
    }
    
    /**
     * 检测设备Поддерживаемые 参数范围
     */
    private void detectSupportedParams(SingleCamera camera) {
        exposureRange = camera.getExposureCompensationRange();
        supportedAwbModes = camera.getSupportedAwbModes();
        supportedTonemapModes = camera.getSupportedTonemapModes();
        supportedEdgeModes = camera.getSupportedEdgeModes();
        supportedNoiseReductionModes = camera.getSupportedNoiseReductionModes();
        supportedEffectModes = camera.getSupportedEffectModes();
        
        AppLog.d(TAG, "Detected supported params:");
        AppLog.d(TAG, "  Exposure range: " + (exposureRange != null ? exposureRange.toString() : "null"));
        AppLog.d(TAG, "  AWB modes: " + (supportedAwbModes != null ? supportedAwbModes.length : 0));
        AppLog.d(TAG, "  Tonemap modes: " + (supportedTonemapModes != null ? supportedTonemapModes.length : 0));
        AppLog.d(TAG, "  Edge modes: " + (supportedEdgeModes != null ? supportedEdgeModes.length : 0));
        AppLog.d(TAG, "  Noise reduction modes: " + (supportedNoiseReductionModes != null ? supportedNoiseReductionModes.length : 0));
        AppLog.d(TAG, "  Effect modes: " + (supportedEffectModes != null ? supportedEffectModes.length : 0));
    }
    
    /**
     * обновление所有Камера 参数（实时生效)
     * @return Успешнообновление Камера数量
     */
    public int updateAllCameras() {
        if (!appConfig.isImageAdjustEnabled()) {
            AppLog.d(TAG, "Image adjust not enabled, skip update");
            return 0;
        }
        
        int successCount = 0;
        for (SingleCamera camera : cameras) {
            boolean success = camera.updateImageAdjustParams(
                exposureCompensation,
                awbMode,
                tonemapMode,
                edgeMode,
                noiseReductionMode,
                effectMode
            );
            if (success) {
                successCount++;
            }
        }
        
        AppLog.d(TAG, "Updated " + successCount + "/" + cameras.size() + " cameras");
        
        // Уведомление监听器
        if (listener != null) {
            listener.onParamsChanged();
        }
        
        return successCount;
    }
    
    /**
     * Сброс所有参数为По умолчанию值
     */
    public void resetToDefault() {
        exposureCompensation = 0;
        awbMode = AppConfig.AWB_MODE_DEFAULT;
        tonemapMode = AppConfig.TONEMAP_MODE_DEFAULT;
        edgeMode = AppConfig.EDGE_MODE_DEFAULT;
        noiseReductionMode = AppConfig.NOISE_REDUCTION_DEFAULT;
        effectMode = AppConfig.EFFECT_MODE_DEFAULT;
        
        // Сохранить до конфигурация
        appConfig.resetImageAdjustParams();
        
        // обновление所有Камера
        updateAllCameras();
        
        AppLog.d(TAG, "Reset all params to default");
    }
    
    // ==================== Getter/Setter 方法 ====================
    
    public int getExposureCompensation() {
        return exposureCompensation;
    }
    
    public void setExposureCompensation(int value) {
        if (exposureRange != null) {
            this.exposureCompensation = Math.max(exposureRange.getLower(), 
                    Math.min(value, exposureRange.getUpper()));
        } else {
            this.exposureCompensation = value;
        }
    }
    
    public int getAwbMode() {
        return awbMode;
    }
    
    public void setAwbMode(int mode) {
        this.awbMode = mode;
    }
    
    public int getTonemapMode() {
        return tonemapMode;
    }
    
    public void setTonemapMode(int mode) {
        this.tonemapMode = mode;
    }
    
    public int getEdgeMode() {
        return edgeMode;
    }
    
    public void setEdgeMode(int mode) {
        this.edgeMode = mode;
    }
    
    public int getNoiseReductionMode() {
        return noiseReductionMode;
    }
    
    public void setNoiseReductionMode(int mode) {
        this.noiseReductionMode = mode;
    }
    
    public int getEffectMode() {
        return effectMode;
    }
    
    public void setEffectMode(int mode) {
        this.effectMode = mode;
    }
    
    // ==================== 参数范围 Getter ====================
    
    public Range<Integer> getExposureRange() {
        return exposureRange;
    }
    
    public int[] getSupportedAwbModes() {
        return supportedAwbModes;
    }
    
    public int[] getSupportedTonemapModes() {
        return supportedTonemapModes;
    }
    
    public int[] getSupportedEdgeModes() {
        return supportedEdgeModes;
    }
    
    public int[] getSupportedNoiseReductionModes() {
        return supportedNoiseReductionModes;
    }
    
    public int[] getSupportedEffectModes() {
        return supportedEffectModes;
    }
    
    /**
     * проверка 否поддержкаЭкспозиция调节
     */
    public boolean isExposureCompensationSupported() {
        return exposureRange != null && !exposureRange.getLower().equals(exposureRange.getUpper());
    }
    
    /**
     * проверка 否поддержкаБаланс белогорежим调节
     */
    public boolean isAwbModeSupported() {
        return supportedAwbModes != null && supportedAwbModes.length > 1;
    }
    
    /**
     * проверка 否поддержкаТональная компрессиярежим调节
     */
    public boolean isTonemapModeSupported() {
        return supportedTonemapModes != null && supportedTonemapModes.length > 1;
    }
    
    /**
     * проверка 否поддержкаРезкостьрежим调节
     */
    public boolean isEdgeModeSupported() {
        return supportedEdgeModes != null && supportedEdgeModes.length > 1;
    }
    
    /**
     * проверка 否поддержкаШумоподавлениережим调节
     */
    public boolean isNoiseReductionModeSupported() {
        return supportedNoiseReductionModes != null && supportedNoiseReductionModes.length > 1;
    }
    
    /**
     * проверка 否поддержкаЭффектырежим调节
     */
    public boolean isEffectModeSupported() {
        return supportedEffectModes != null && supportedEffectModes.length > 1;
    }
    
    /**
     * ПолучениеТекущий参数 摘要字符串（用于显示)
     */
    public String getParamsSummary() {
        StringBuilder sb = new StringBuilder();
        
        if (exposureCompensation != 0) {
            sb.append("Экспозиция: ").append(exposureCompensation > 0 ? "+" : "").append(exposureCompensation);
        }
        
        if (awbMode != AppConfig.AWB_MODE_DEFAULT) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Баланс белого: ").append(AppConfig.getAwbModeDisplayName(awbMode));
        }
        
        if (tonemapMode != AppConfig.TONEMAP_MODE_DEFAULT) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Тон: ").append(AppConfig.getTonemapModeDisplayName(tonemapMode));
        }
        
        if (edgeMode != AppConfig.EDGE_MODE_DEFAULT) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Резкость: ").append(AppConfig.getEdgeModeDisplayName(edgeMode));
        }
        
        if (noiseReductionMode != AppConfig.NOISE_REDUCTION_DEFAULT) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Шумоподавление: ").append(AppConfig.getNoiseReductionModeDisplayName(noiseReductionMode));
        }
        
        if (effectMode != AppConfig.EFFECT_MODE_DEFAULT && effectMode != AppConfig.EFFECT_MODE_OFF) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Эффекты: ").append(AppConfig.getEffectModeDisplayName(effectMode));
        }
        
        if (sb.length() == 0) {
            return "Параметры по умолчанию";
        }
        
        return sb.toString();
    }
    
    // ==================== Получение相机实际использование 参数 ====================
    
    /**
     * Получение相机实际использование Экспозиция值（ от Первый шт.注册 相机Получение)
     */
    public int getActualExposureCompensation() {
        if (!cameras.isEmpty()) {
            return cameras.get(0).getActualExposureCompensation();
        }
        return 0;
    }
    
    /**
     * Получение相机实际использование Баланс белогорежим
     */
    public int getActualAwbMode() {
        if (!cameras.isEmpty()) {
            return cameras.get(0).getActualAwbMode();
        }
        return 1; // AUTO
    }
    
    /**
     * Получение相机实际использование Тональная компрессиярежим
     */
    public int getActualTonemapMode() {
        if (!cameras.isEmpty()) {
            return cameras.get(0).getActualTonemapMode();
        }
        return 1; // FAST
    }
    
    /**
     * Получение相机实际использование Резкостьрежим
     */
    public int getActualEdgeMode() {
        if (!cameras.isEmpty()) {
            return cameras.get(0).getActualEdgeMode();
        }
        return 0; // OFF
    }
    
    /**
     * Получение相机实际использование Шумоподавлениережим
     */
    public int getActualNoiseReductionMode() {
        if (!cameras.isEmpty()) {
            return cameras.get(0).getActualNoiseReductionMode();
        }
        return 0; // OFF
    }
    
    /**
     * Получение相机实际использование Эффектырежим
     */
    public int getActualEffectMode() {
        if (!cameras.isEmpty()) {
            return cameras.get(0).getActualEffectMode();
        }
        return 0; // OFF
    }
    
    /**
     *  否Получение до 相机实际参数
     */
    public boolean hasActualParams() {
        if (!cameras.isEmpty()) {
            return cameras.get(0).hasActualParams();
        }
        return false;
    }
}
