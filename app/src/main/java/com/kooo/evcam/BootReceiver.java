package com.kooo.evcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * Вкл机Запуск广播接收器
 * 监听СистемаВкл机广播，автоматическиЗапуск必要 Сервис
 * 
 * Выкл键改进（参考保活效果好 Приложение)：
 * 1. 直接ЗапускПередний планСервис，不依赖 Activity（Android 10+ Фоновый режимЗапуск Activity 受限)
 * 2. 简化Запуск逻辑，减少Ошибка点
 * 3. 延迟Запуск，ожиданиеСистема稳定
 * 4. 注册 TIME_TICK 广播，建立保活机制
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    // Вкл机后延迟Запуск时间（毫 сек.)，ожиданиеСистема稳定
    private static final long BOOT_DELAY_MS = 5000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        AppLog.d(TAG, "Получена команда: 广播: " + action);

        // 监听Вкл机завершение广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            AppLog.d(TAG, "СистемаВкл机завершение！");
            
            // 立т.е.ЗапускПередний планСервис（最重要！参考Приложение0 做法)
            startForegroundServiceImmediately(context);
            
            // 延迟выполнениеДругоеинициализация（ожиданиеСистема稳定)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                performDelayedInit(context);
            }, BOOT_DELAY_MS);
        }
    }
    
    /**
     * 立т.е.ЗапускПередний планСервис（Выкл键！)
     * 参考Приложение0：Получена команда: 广播后直接ЗапускСервис，不做任何проверка
     */
    private void startForegroundServiceImmediately(Context context) {
        try {
            AppLog.d(TAG, "立т.е.ЗапускПередний планСервис...");
            
            // 直接ЗапускПередний планСервис，不проверка任何конфигурация
            // 这 保活Приложение Выкл键做法：无条件Запуск
            Intent serviceIntent = new Intent(context, CameraForegroundService.class);
            serviceIntent.putExtra("title", "EVCam автозапуск");
            serviceIntent.putExtra("content", "Сервис работает");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            AppLog.d(TAG, "Передний планСервисЗапускУспешно");
        } catch (Exception e) {
            AppLog.e(TAG, "ЗапускПередний планСервисОшибка: " + e.getMessage(), e);
        }
    }
    
    /**
     * 延迟выполнение инициализациязадача
     * ожиданиеСистема稳定后再выполнение复杂 инициализация
     */
    private void performDelayedInit(Context context) {
        AppLog.d(TAG, "выполнение延迟инициализация...");
        
        try {
            // 注册 TIME_TICK 广播（建立每 мин.唤醒机制)
            KeepAliveReceiver.registerTimeTick(context);
            AppLog.d(TAG, "TIME_TICK 广播注册");
        } catch (Exception e) {
            AppLog.e(TAG, "注册 TIME_TICK Ошибка: " + e.getMessage(), e);
        }
        
        try {
            // проверка 否необходимоЗапускДругоеСервис
            AppConfig appConfig = new AppConfig(context);
            
            // Запуск WorkManager 保活задача（车机必需，始终Вкл启)
            KeepAliveManager.startKeepAliveWork(context);
            AppLog.d(TAG, "WorkManager 保活задачаЗапущено");
            
            // Если 用户ВключитьВкл机自Запуск，попыткаЗапуск完整Приложение
            if (appConfig.isAutoStartOnBoot()) {
                AppLog.d(TAG, "попыткаЗапуск完整Приложение...");
                tryStartMainActivity(context);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "延迟инициализацияОшибка: " + e.getMessage(), e);
        }
        
        AppLog.d(TAG, "Вкл机自Запускинициализациязавершение");
    }
    
    /**
     * попыткаЗапуск MainActivity
     * Android 10+ Фоновый режимЗапуск Activity 受限，可能Ошибка，但不影响СервисРабота
     */
    private void tryStartMainActivity(Context context) {
        try {
            // 方案1：попыткаЗапуск透明 Activity
            Intent transparentIntent = new Intent(context, TransparentBootActivity.class);
            transparentIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | 
                Intent.FLAG_ACTIVITY_NO_ANIMATION |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            );
            context.startActivity(transparentIntent);
            AppLog.d(TAG, "透明 Activity Запущено");
        } catch (Exception e) {
            AppLog.w(TAG, "Запуск Activity Ошибка（Android 10+ Фоновый режим限制): " + e.getMessage());
            // Ошибкатакже没Выкл系，Передний планСервис经 Работа
        }
    }
}
