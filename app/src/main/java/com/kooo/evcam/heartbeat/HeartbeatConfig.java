package com.kooo.evcam.heartbeat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import com.kooo.evcam.AppLog;

import java.security.MessageDigest;

/**
 * Мониторингконфигурацияуправление类
 * управление实时监控推送 конфигурация项
 */
public class HeartbeatConfig {
    private static final String TAG = "HeartbeatConfig";
    private static final String PREF_NAME = "heartbeat_config";
    
    // конфигурация项键名
    private static final String KEY_ENABLED = "enabled";                      // функцияВклВыкл
    private static final String KEY_INTERVAL_SECONDS = "interval_seconds";    // 推送间隔（ сек.)
    private static final String KEY_SERVER_URL = "server_url";                // Адрес сервера
    private static final String KEY_VEHICLE_ID = "vehicle_id";                // 车辆ID
    private static final String KEY_SECRET_KEY = "secret_key";                // 通信密钥
    private static final String KEY_TARGET_SIZE_KB = "target_size_kb";        // 目标压缩大小
    private static final String KEY_SCREEN_ON_PUSH = "screen_on_push";        // 亮屏推图ВклВыкл
    private static final String KEY_SCREEN_OFF_PUSH = "screen_off_push";      // 息屏推图ВклВыкл
    private static final String KEY_AUTO_START = "auto_start";                // автоматическиЗапускСервис
    
    // 统计Информация
    private static final String KEY_LAST_UPLOAD_TIME = "last_upload_time";    //  раз传时间
    private static final String KEY_SUCCESS_COUNT = "success_count";          // Успешно раз数
    private static final String KEY_FAIL_COUNT = "fail_count";                // Ошибка раз数
    private static final String KEY_LAST_ERROR = "last_error";                // 最后一 разОшибкаИнформация
    
    // 推送间隔常量（ сек.)
    public static final int INTERVAL_30_SECONDS = 30;
    public static final int INTERVAL_60_SECONDS = 60;
    public static final int INTERVAL_120_SECONDS = 120;
    public static final int INTERVAL_300_SECONDS = 300;
    
    // По умолчанию值
    private static final int DEFAULT_INTERVAL = INTERVAL_60_SECONDS;
    private static final boolean DEFAULT_SCREEN_ON_PUSH = true;   // 亮屏推图По умолчаниюВкл
    private static final boolean DEFAULT_SCREEN_OFF_PUSH = false; // 息屏推图По умолчаниюВыкл
    
    // 压缩目标大小选项
    public static final int TARGET_SIZE_100KB = 100;
    public static final int TARGET_SIZE_500KB = 500;
    public static final int TARGET_SIZE_1MB = 1024;
    public static final int TARGET_SIZE_NO_COMPRESS = 0;  // 0 表示不压缩
    private static final int DEFAULT_TARGET_SIZE_KB = TARGET_SIZE_100KB;
    
    private final SharedPreferences prefs;
    private final Context context;
    
    public HeartbeatConfig(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // 确保车辆ID生成
        ensureVehicleId();
    }
    
    // ==================== 基本конфигурация ====================
    
    /**
     * ПолучениефункцияВклВыклСтатус
     */
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }
    
    /**
     * НастройкифункцияВклВыкл
     */
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        AppLog.d(TAG, "Мониторингфункция: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение推送间隔（ сек.)
     */
    public int getIntervalSeconds() {
        return prefs.getInt(KEY_INTERVAL_SECONDS, DEFAULT_INTERVAL);
    }
    
    /**
     * Настройки推送间隔（ сек.)
     */
    public void setIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_INTERVAL_SECONDS, seconds).apply();
        AppLog.d(TAG, "推送间隔Настройки: " + seconds + " сек.");
    }
    
    /**
     * ПолучениеАдрес сервера
     */
    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, "");
    }
    
    /**
     * НастройкиАдрес сервера
     */
    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
        AppLog.d(TAG, "Адрес сервераНастройки: " + url);
    }
    
    /**
     * проверкаАдрес сервера 否конфигурация
     */
    public boolean hasServerUrl() {
        String url = getServerUrl();
        return url != null && !url.trim().isEmpty();
    }
    
    // ==================== 认证конфигурация ====================
    
    /**
     * 确保车辆ID生成
     * использование设备唯一标识生成固定 车辆ID
     */
    private void ensureVehicleId() {
        String savedId = prefs.getString(KEY_VEHICLE_ID, "");
        String expectedId = generateVehicleId();
        
        // Если Сохранить ID и 基于设备生成 ID不一致，则обновление
        // 这确保т.е.использование户очистка数据，также能Восстановление до 相同 ID
        if (!expectedId.equals(savedId)) {
            prefs.edit().putString(KEY_VEHICLE_ID, expectedId).apply();
            AppLog.d(TAG, "车辆IDинициализация: " + expectedId);
        }
    }
    
    /**
     * 基于设备唯一标识生成固定 车辆ID
     * 算法：SHA256(ANDROID_ID + Build.FINGERPRINT + Build.BOARD) 取前8位
     * 
     * 特点：
     * - 同一设备始终生成相同 ID
     * - 不同设备生成不同 ID
     * - 用户无法изменение
     * 
     * @return 格式: EV-{8位十六进制}
     */
    private String generateVehicleId() {
        try {
            // Получение Android ID（每 шт.设备+用户+签名 唯一)
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), 
                    Settings.Secure.ANDROID_ID
            );
            
            //  групп合多 шт.设备特征，增加唯一性
            String deviceInfo = (androidId != null ? androidId : "") 
                    + Build.FINGERPRINT  // 设备指纹
                    + Build.BOARD        // 主板名
                    + Build.DEVICE       // 设备名
                    + Build.HARDWARE;    // 硬件名
            
            // использование SHA-256 哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(deviceInfo.getBytes("UTF-8"));
            
            // 取前4字节（8位十六进制)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02X", hash[i]));
            }
            
            return "EV-" + sb.toString();
            
        } catch (Exception e) {
            AppLog.e(TAG, "生成车辆IDОшибка: " + e.getMessage());
            // 降级方案：использование ANDROID_ID 直接截取
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), 
                    Settings.Secure.ANDROID_ID
            );
            if (androidId != null && androidId.length() >= 8) {
                return "EV-" + androidId.substring(0, 8).toUpperCase();
            }
            // 最后 降级：基于时间戳（不рекомендуется，但保证不崩溃)
            return "EV-" + String.format("%08X", System.currentTimeMillis() & 0xFFFFFFFFL);
        }
    }
    
    /**
     * Получение车辆ID
     * 基于设备唯一标识生成，固定不变
     */
    public String getVehicleId() {
        return prefs.getString(KEY_VEHICLE_ID, "");
    }
    
    /**
     * Получение通信密钥
     */
    public String getSecretKey() {
        return prefs.getString(KEY_SECRET_KEY, "");
    }
    
    /**
     * Настройки通信密钥
     */
    public void setSecretKey(String key) {
        prefs.edit().putString(KEY_SECRET_KEY, key).apply();
        AppLog.d(TAG, "通信密钥Настройки");
    }
    
    /**
     * проверка通信密钥 否конфигурация
     */
    public boolean hasSecretKey() {
        String key = getSecretKey();
        return key != null && !key.trim().isEmpty();
    }
    
    // ==================== Изображениеконфигурация ====================
    
    /**
     * Получение目标压缩大小（KB)
     */
    public int getTargetSizeKB() {
        return prefs.getInt(KEY_TARGET_SIZE_KB, DEFAULT_TARGET_SIZE_KB);
    }
    
    /**
     * Настройки目标压缩大小（KB)
     */
    public void setTargetSizeKB(int sizeKB) {
        prefs.edit().putInt(KEY_TARGET_SIZE_KB, sizeKB).apply();
        AppLog.d(TAG, "目标压缩大小Настройки: " + (sizeKB == 0 ? "不压缩" : sizeKB + "KB"));
    }
    
    /**
     * Получение目标大小 显示名称
     */
    public static String getTargetSizeDisplayName(int sizeKB) {
        switch (sizeKB) {
            case TARGET_SIZE_100KB:
                return "100 КБ (экономия трафика)";
            case TARGET_SIZE_500KB:
                return "500KB";
            case TARGET_SIZE_1MB:
                return "1MB";
            case TARGET_SIZE_NO_COMPRESS:
                return "Без сжатия (оригинальное качество)";
            default:
                return sizeKB + "KB";
        }
    }
    
    // ==================== 推图режимконфигурация ====================
    
    /**
     * Получение亮屏推图ВклВыкл
     * 亮屏Статус，Если  Передний план推图
     */
    public boolean isScreenOnPushEnabled() {
        return prefs.getBoolean(KEY_SCREEN_ON_PUSH, DEFAULT_SCREEN_ON_PUSH);
    }
    
    /**
     * Настройки亮屏推图ВклВыкл
     */
    public void setScreenOnPushEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCREEN_ON_PUSH, enabled).apply();
        AppLog.d(TAG, "亮屏推图Настройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение息屏推图ВклВыкл
     * 息屏Статус，定时唤醒 до Передний план推图
     */
    public boolean isScreenOffPushEnabled() {
        return prefs.getBoolean(KEY_SCREEN_OFF_PUSH, DEFAULT_SCREEN_OFF_PUSH);
    }
    
    /**
     * Настройки息屏推图ВклВыкл
     */
    public void setScreenOffPushEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCREEN_OFF_PUSH, enabled).apply();
        AppLog.d(TAG, "息屏推图Настройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * ПолучениеавтоматическиЗапускВклВыкл
     */
    public boolean isAutoStartEnabled() {
        return prefs.getBoolean(KEY_AUTO_START, false);
    }
    
    /**
     * НастройкиавтоматическиЗапускВклВыкл
     */
    public void setAutoStartEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply();
        AppLog.d(TAG, "автоматическиЗапускНастройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    // ==================== 统计Информация ====================
    
    /**
     * Получение раз传时间
     */
    public long getLastUploadTime() {
        return prefs.getLong(KEY_LAST_UPLOAD_TIME, 0);
    }
    
    /**
     * Настройки раз传时间
     */
    public void setLastUploadTime(long time) {
        prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, time).apply();
    }
    
    /**
     * ПолучениеУспешно раз数
     */
    public int getSuccessCount() {
        return prefs.getInt(KEY_SUCCESS_COUNT, 0);
    }
    
    /**
     * 增加Успешно раз数
     */
    public void incrementSuccessCount() {
        int count = getSuccessCount() + 1;
        prefs.edit().putInt(KEY_SUCCESS_COUNT, count).apply();
    }
    
    /**
     * Ошибка получения раз数
     */
    public int getFailCount() {
        return prefs.getInt(KEY_FAIL_COUNT, 0);
    }
    
    /**
     * 增加Ошибка раз数
     */
    public void incrementFailCount() {
        int count = getFailCount() + 1;
        prefs.edit().putInt(KEY_FAIL_COUNT, count).apply();
    }
    
    /**
     * Получение最后一 разОшибкаИнформация
     */
    public String getLastError() {
        return prefs.getString(KEY_LAST_ERROR, "");
    }
    
    /**
     * Настройки最后一 разОшибкаИнформация
     */
    public void setLastError(String error) {
        prefs.edit().putString(KEY_LAST_ERROR, error).apply();
    }
    
    /**
     * Сброс统计Информация
     */
    public void resetStatistics() {
        prefs.edit()
            .putLong(KEY_LAST_UPLOAD_TIME, 0)
            .putInt(KEY_SUCCESS_COUNT, 0)
            .putInt(KEY_FAIL_COUNT, 0)
            .remove(KEY_LAST_ERROR)
            .apply();
        AppLog.d(TAG, "统计ИнформацияСброс");
    }
    
    // ==================== конфигурацияпроверка ====================
    
    /**
     * проверкаконфигурация 否完整（可以ЗапускСервис)
     */
    public boolean isConfigured() {
        return hasServerUrl() && hasSecretKey();
    }
    
    /**
     * ПолучениеконфигурацияСтатус描述
     */
    public String getConfigStatus() {
        if (!hasServerUrl()) {
            return "Укажите адрес сервера";
        }
        if (!hasSecretKey()) {
            return "Укажите ключ связи";
        }
        return "конфигурациязавершение";
    }
    
    // ==================== 间隔显示名称 ====================
    
    /**
     * Получение间隔 显示名称
     */
    public static String getIntervalDisplayName(int seconds) {
        switch (seconds) {
            case INTERVAL_30_SECONDS:
                return "30 сек.";
            case INTERVAL_60_SECONDS:
                return "1 мин.（рекомендуется)";
            case INTERVAL_120_SECONDS:
                return "2 мин.";
            case INTERVAL_300_SECONDS:
                return "5 мин.";
            default:
                return seconds + " сек.";
        }
    }
}
