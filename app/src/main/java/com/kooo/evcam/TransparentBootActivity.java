package com.kooo.evcam;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.kooo.evcam.dingtalk.DingTalkConfig;
import com.kooo.evcam.telegram.TelegramConfig;

/**
 * 透明Запуск Activity
 * 用于Вкл机自Запуск时 Фоновый режиминициализацияСервис，用户完全无感知
 * 
 * 特点：
 * 1. 完全透明，用户看不 до 任何界面
 * 2. Запуск后立т.е.инициализацияСервис并 finish
 * 3. 不会 最近задача显示
 */
public class TransparentBootActivity extends Activity {
    private static final String TAG = "TransparentBootActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.d(TAG, "透明Запуск Activity 创建");
        
        // 不Настройки任何布局，保持完全透明
        
        // инициализацияСервис
        initServices();
        
        // 立т.е.завершить，用户完全无感知
        finish();
        
        // ОтключитьВыход动画
        overridePendingTransition(0, 0);
        
        AppLog.d(TAG, "透明Запуск Activity завершить");
    }
    
    /**
     * инициализация必要 Сервис
     */
    private void initServices() {
        AppLog.d(TAG, "Вкл始инициализацияФоновый режимСервис...");
        
        // 1. ЗапускПередний планСервис保持进程活跃
        // 【重要】УдалённыйСервис（DingTalk/Telegram)现   CameraForegroundService.onCreate() Запуск
        // 不再необходимо MainActivity 来ЗапускУдалённыйСервис
        CameraForegroundService.start(this, 
            "Автозапуск при включении", 
            "Приложение работает в фоне");
        AppLog.d(TAG, "Передний планСервис запущен（УдалённыйСервис将 其Запуск)");
        
        // 2. Запуск WorkManager 保活задача（车机必需，始终Вкл启)
        KeepAliveManager.startKeepAliveWork(this);
        AppLog.d(TAG, "WorkManager 保活задачаЗапущено");
        
        // 3. проверка 否необходимоЗапуск MainActivity
        // 【优化后】只有и ниже情况необходимоЗапуск MainActivity：
        // - 用户Включить"ЗапускавтоматическиЗапись"функция（необходимоКамера，必须Запуск Activity)
        // 【不再необходимоЗапуск MainActivity】：
        // - УдалённыйСервис（DingTalk/Telegram)  CameraForegroundService Запуск
        // - 悬浮窗  CameraForegroundService Запуск
        AppConfig appConfig = new AppConfig(this);
        
        boolean shouldAutoRecord = appConfig.isAutoStartRecording();
        boolean shouldShowFloatingWindow = appConfig.isFloatingWindowEnabled();
        
        // только用于 д.志记录
        DingTalkConfig dingTalkConfig = new DingTalkConfig(this);
        TelegramConfig telegramConfig = new TelegramConfig(this);
        boolean hasRemoteService = (dingTalkConfig.isConfigured() && dingTalkConfig.isAutoStart()) ||
                                   (telegramConfig.isConfigured() && telegramConfig.isAutoStart());
        
        if (hasRemoteService) {
            AppLog.d(TAG, "УдалённыйСервис  CameraForegroundService Запуск，无需Запуск MainActivity");
        }
        if (shouldShowFloatingWindow) {
            AppLog.d(TAG, "悬浮窗  CameraForegroundService Запуск，无需Запуск MainActivity");
        }
        
        // 只有автоматическиЗаписьнеобходимоЗапуск MainActivity（因为необходимоКамера)
        if (shouldAutoRecord) {
            AppLog.d(TAG, "ЗапускавтоматическиЗаписьфункцияВключено，необходимоЗапуск MainActivity（Камеранеобходимо Activity)");
            AppLog.d(TAG, "Запуск MainActivity（Фоновый режимрежим)...");
            
            // Запуск MainActivity инициализацияКамера（Фоновый режимрежим)
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mainIntent.putExtra("auto_start_from_boot", true);
            mainIntent.putExtra("silent_mode", true);
            startActivity(mainIntent);
            
            AppLog.d(TAG, "MainActivity Запущено（Фоновый режимрежим)");
        } else {
            AppLog.d(TAG, "无需Запуск MainActivity（автоматическиЗаписьНе Включить)，только保持Фоновый режимРабота");
        }
    }
    
    @Override
    public void onBackPressed() {
        // Отключить返回键
        finish();
    }
}
