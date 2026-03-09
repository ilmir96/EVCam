package com.kooo.evcam;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.app.NotificationCompat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 增强版保活无障碍Сервис
 * 
 * 用途：提ВысокийПриложениеФоновый режимРабота优先级，防止 СистемаОчистка 
 * 
 * 保活策略：
 * 1. 辅助Сервис本身由Системауправление，具有最Высокий优先级
 * 2. 配合Передний планУведомление，双重保护
 * 3. 心跳定时器，防止进程休眠
 * 4. автоматически拉起Передний планСервис
 * 
 * 注意：此Сервис不会读取илиоперация任何用户界面内容，только利用Разрешение提升进程优先级
 */
public class KeepAliveAccessibilityService extends AccessibilityService {
    private static final String TAG = "KeepAliveAccessibility";
    private static final String CHANNEL_ID = "keep_alive_channel";
    private static final int NOTIFICATION_ID = 9527;
    private static final long HEARTBEAT_INTERVAL_MS = 60000; // 60 сек.心跳
    
    private static KeepAliveAccessibilityService instance;
    private static boolean isServiceRunning = false;
    
    private ScheduledExecutorService heartbeatExecutor;
    private long startTime;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        isServiceRunning = true;
        startTime = System.currentTimeMillis();
        
        AppLog.d(TAG, "无障碍Сервис запущен（增强保活режим)");
        
        // 注意：辅助Сервис本身 Система级Сервис，不необходимоПередний планУведомлениетакже有最Высокий优先级
        // Передний планУведомление由 CameraForegroundService 提供，避免重复Уведомление
        // startForegroundNotification();  // 移除，减少重复Уведомление
        
        // Запуск心跳定时器
        startHeartbeat();
        
        // 动态注册 TIME_TICK 广播（每 мин.触发)
        registerTimeTickBroadcast();
        
        // 确保Передний планСервистакже Работа
        ensureForegroundServiceRunning();
    }
    
    /**
     * 动态注册 TIME_TICK 广播
     * TIME_TICK   Android 8.0+ 只能动态注册，每 мин.触发一 раз
     * 这 保活 Выкл键手之一
     */
    private void registerTimeTickBroadcast() {
        try {
            KeepAliveReceiver.registerTimeTick(this);
            AppLog.d(TAG, "TIME_TICK 广播注册");
        } catch (Exception e) {
            AppLog.e(TAG, "注册 TIME_TICK 广播Ошибка: " + e.getMessage(), e);
        }
    }
    
    /**
     * 注销 TIME_TICK 广播
     */
    private void unregisterTimeTickBroadcast() {
        try {
            KeepAliveReceiver.unregisterTimeTick(this);
            AppLog.d(TAG, "TIME_TICK 广播注销");
        } catch (Exception e) {
            AppLog.e(TAG, "注销 TIME_TICK 广播Ошибка: " + e.getMessage(), e);
        }
    }

    /**
     * 创建Передний планУведомление
     * 辅助Сервис配合Передний планУведомление可以获得最Высокий 进程优先级
     */
    private void startForegroundNotification() {
        try {
            // 创建Уведомление渠道（Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Служба поддержания",
                        NotificationManager.IMPORTANCE_LOW  // Низкий重要性，不打扰用户
                );
                channel.setDescription("Поддержание работы в фоне");
                channel.enableLights(false);
                channel.enableVibration(false);
                channel.setSound(null, null);
                channel.setShowBadge(false);
                
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
            
            // 创建点击Уведомление时открытьПриложение  Intent
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // 构建Уведомление
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setContentTitle("EVCam")
                    .setContentText("Служба поддержания работает")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setContentIntent(pendingIntent)
                    .build();
            
            // ЗапускПередний планСервис（辅助СервистакжеПоддерживаемые startForeground)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, notification);
                AppLog.d(TAG, "Передний планУведомлениеЗапущено");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "ЗапускПередний планУведомлениеОшибка: " + e.getMessage(), e);
        }
    }

    /**
     * Запуск心跳定时器
     * 定期выполнениезадача，防止进程 Система判定пусто闲而Очистка 
     */
    private void startHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            return;
        }
        
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                long runningMinutes = (System.currentTimeMillis() - startTime) / 60000;
                AppLog.d(TAG, "心跳: СервисРабота " + runningMinutes + "  мин.");
                
                // проверка并确保Передний планСервисРабота
                ensureForegroundServiceRunning();
            } catch (Exception e) {
                AppLog.e(TAG, "心跳задачааномалия: " + e.getMessage(), e);
            }
        }, 5000, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        AppLog.d(TAG, "心跳定时器Запущено，间隔: " + (HEARTBEAT_INTERVAL_MS / 1000) + " сек.");
    }

    /**
     * Остановка心跳定时器
     */
    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
            }
            heartbeatExecutor = null;
            AppLog.d(TAG, "心跳定时器Остановлено");
        }
    }

    /**
     * 确保Передний планСервисВыполняется Работа
     * 辅助Сервис拉起Передний планСервис，形成双重保活
     */
    private void ensureForegroundServiceRunning() {
        try {
            // ЗапускКамераПередний планСервис
            CameraForegroundService.start(this, "EVCam работает в фоне", "Нажмите для возврата");
        } catch (Exception e) {
            AppLog.e(TAG, "拉起Передний планСервисОшибка: " + e.getMessage(), e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不处理任何无障碍事件，только用于保活
        // 虽然конфигурацияразрешитьПолучение窗口内容，但代码不会实际读取
        // 这样既能获得Высокий优先级，又能保护用户隐私
    }

    @Override
    public void onInterrupt() {
        AppLog.d(TAG, "无障碍Сервис 断");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.d(TAG, "无障碍Сервис onStartCommand");
        return START_STICKY; // 确保 杀后перезагрузка
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AppLog.d(TAG, "无障碍СервисПодключено до Система");
        
        // СервисПодключение后再 раз确保所有保活 групп件Работа
        ensureForegroundServiceRunning();
    }

    @Override
    public void onDestroy() {
        AppLog.d(TAG, "无障碍СервисВыполняется 销毁...");
        
        // Остановка心跳
        stopHeartbeat();
        
        // 注销 TIME_TICK 广播
        unregisterTimeTickBroadcast();
        
        // Передний планУведомление由 CameraForegroundService управление，这里不необходимоОстановка
        
        instance = null;
        isServiceRunning = false;
        
        super.onDestroy();
        AppLog.d(TAG, "无障碍Сервис销毁");
    }

    /**
     * проверкаСервис 否Выполняется Работа
     */
    public static boolean isRunning() {
        return isServiceRunning && instance != null;
    }

    /**
     * ПолучениеСервис实例
     */
    public static KeepAliveAccessibilityService getInstance() {
        return instance;
    }
    
    /**
     * ПолучениеСервисРабота时长（ мин.)
     */
    public static long getRunningMinutes() {
        if (instance != null) {
            return (System.currentTimeMillis() - instance.startTime) / 60000;
        }
        return 0;
    }
}
