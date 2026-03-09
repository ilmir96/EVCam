package com.kooo.evcam;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * 无障碍Сервис辅助类
 * 提供无障碍СервисСтатуспроверка и 跳转функция
 */
public class AccessibilityHelper {
    private static final String TAG = "AccessibilityHelper";

    /**
     * проверка无障碍Сервис 否Включено
     * @param context 文
     * @return true 表示Включено
     */
    public static boolean isAccessibilityServiceEnabled(Context context) {
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
                    boolean enabled = services.contains(serviceName);
                    AppLog.d(TAG, "无障碍СервисСтатус: " + (enabled ? "Включено" : "Не Включить"));
                    return enabled;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "проверка无障碍СервисСтатусОшибка", e);
        }
        return false;
    }

    /**
     * открыть无障碍Настройки页面
     * @param context 文
     */
    public static void openAccessibilitySettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            AppLog.d(TAG, "открыть无障碍Настройки页面");
        } catch (Exception e) {
            AppLog.e(TAG, "открыть无障碍НастройкиОшибка", e);
        }
    }
}
