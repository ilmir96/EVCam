package com.kooo.evcam;


import com.kooo.evcam.AppLog;
import com.kooo.evcam.camera.MultiCameraManager;
// import android.app.AlarmManager;  // 移除，использование TIME_TICK 替代
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
// import android.os.SystemClock;  // 移除 AlarmManager
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Передний планСервис，用于 Фоновый режимиспользованиеКамера
 * Android 11+ 要求Фоновый режимиспользованиеКамера时必须有Передний планСервис
 * 
 * 增强保活функция：
 * - 当无障碍СервисНе Вкл启时，此Сервис会动态注册 TIME_TICK 广播
 * - TIME_TICK 每 мин.触发一 раз，可以保持Приложение活跃
 * - onTaskRemoved: 用户滑动очисткаПриложение时автоматическиперезагрузка
 * - onDestroy: Сервис 杀时Отправка延迟перезагрузка广播
 * - WakeLock: 防止Система休眠（需用户Вкл启)
 */
public class CameraForegroundService extends Service {
    private static final String TAG = "CameraForegroundService";
    private static final String CHANNEL_ID = "camera_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Сервисперезагрузка延迟时间
    private static final long RESTART_DELAY_MS = 1000;

    private static final long CAMERA_REPAIR_INTERVAL_MS = 10000;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable cameraRepairRunnable;

    private static volatile boolean isForegroundReady = false;
    private static final java.util.List<Runnable> pendingReadyCallbacks = new java.util.ArrayList<>();

    /**
     * Передний планСервис绪后выполнение回调。
     * Если Сервис经Работа，立т.е. 主线程выполнение；否则排队ожидание startForeground завершение后выполнение。
     */
    public static void whenReady(Context context, Runnable callback) {
        if (isForegroundReady) {
            callback.run();
        } else {
            synchronized (pendingReadyCallbacks) {
                pendingReadyCallbacks.add(callback);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.d(TAG, "Service created");
        createNotificationChannel();
        
        // Если 无障碍СервисНе Работа，则 此注册 TIME_TICK 广播
        registerTimeTickIfNeeded();
        
        // Получение WakeLock 防止Система休眠（车机必须)
        acquireWakeLock();
        
        // ЗапускУдалённыйСервис（DingTalk/Telegram)
        // 这样УдалённыйСервис不依赖 MainActivity，т.е.使 Activity  杀также能продолжитьРабота
        startRemoteServicesIfNeeded();

        startCameraRepairLoop();
    }
    
    /**
     * ЗапускУдалённыйСервис（Если конфигурацияавтоматическиЗапуск)
     * 这 轻量优化 核心：УдалённыйСервис  Service Запуск，不依赖 MainActivity
     */
    private void startRemoteServicesIfNeeded() {
        try {
            AppConfig appConfig = new AppConfig(this);
            // 只有Вкл启Вкл机自Запуск才ЗапускУдалённыйСервис и 悬浮窗
            if (appConfig.isAutoStartOnBoot()) {
                AppLog.d(TAG, "Вкл机自ЗапускВкл启， от  Service ЗапускУдалённыйСервис...");
                RemoteServiceManager.getInstance().startRemoteServicesFromService(this);
                
                // Запуск悬浮窗（Если Включено)
                if (appConfig.isFloatingWindowEnabled()) {
                    AppLog.d(TAG, "悬浮窗Включено， от  Service Запуск悬浮窗...");
                    FloatingWindowService.start(this);
                }
                
                // Запуск补盲选项Сервис (副屏/主屏悬浮窗/转 к 灯联动/模拟按钮)
                if (appConfig.isSecondaryDisplayEnabled() || appConfig.isMainFloatingEnabled()
                        || appConfig.isTurnSignalLinkageEnabled() || appConfig.isMockTurnSignalFloatingEnabled()) {
                    AppLog.d(TAG, "补盲选项Включено， от  Service Запуск...");
                    BlindSpotService.update(this);
                }
                
                // Если ВключитьавтоматическиЗапись，Запуск MainActivity
                // 这确保杀Фоновый режимперезагрузка后также能автоматическиЗапись（ и Вкл机Запуск行为一致)
                if (appConfig.isAutoStartRecording()) {
                    startMainActivityForAutoRecording();
                }
            } else {
                AppLog.d(TAG, "Вкл机自ЗапускНе Вкл启，跳过УдалённыйСервисЗапуск");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "ЗапускУдалённыйСервисОшибка: " + e.getMessage(), e);
        }
    }
    
    /**
     * Запуск MainActivity 进行автоматическиЗапись
     * 用于：
     * 1. 杀Фоновый режим后Сервисперезагрузка时ВосстановлениеавтоматическиЗапись
     * 2.  и Вкл机Запуск（TransparentBootActivity)行为保持一致
     */
    private void startMainActivityForAutoRecording() {
        try {
            // проверка MainActivity  否经 Работа
            // 通过проверка静态引用判断（避免重复Запуск)
            if (MainActivity.getInstance() != null) {
                AppLog.d(TAG, "MainActivity  Работа，跳过Запуск");
                return;
            }
            
            AppLog.d(TAG, "автоматическиЗаписьВключено，Запуск MainActivity（Фоновый режимрежим)...");
            
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mainIntent.putExtra("auto_start_from_boot", true);  // 复用Вкл机自Запуск 逻辑
            mainIntent.putExtra("silent_mode", true);
            mainIntent.putExtra("from_service_restart", true);  // 标记来自Сервисперезагрузка
            startActivity(mainIntent);
            
            AppLog.d(TAG, "MainActivity Запущено（用于автоматическиЗапись)");
        } catch (Exception e) {
            AppLog.e(TAG, "Запуск MainActivity Ошибка: " + e.getMessage(), e);
        }
    }
    
    /**
     * Получение WakeLock 防止Система休眠
     * 只有Вкл启"Вкл机自Запуск"时才Получение，因为 WakeLock 会阻止 CPU 休眠
     * 用途：
     * 1. 息屏СтатуспродолжитьЗапись
     * 2. 保持 WebSocket Подключение接收Удалённыйкоманда
     * 3. выполнениеУдалённыйФото/Записьзадача
     */
    private void acquireWakeLock() {
        try {
            AppConfig appConfig = new AppConfig(this);
            if (appConfig.isAutoStartOnBoot()) {
                WakeUpHelper.acquirePersistentWakeLock(this);
                AppLog.d(TAG, "WakeLock acquired (Вкл机自ЗапускВкл启)");
            } else {
                // Если Вкл机自ЗапускЗакрыто，释放可能существует  WakeLock
                WakeUpHelper.releasePersistentWakeLock();
                AppLog.d(TAG, "WakeLock not acquired (Вкл机自ЗапускНе Вкл启)");
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to handle WakeLock: " + e.getMessage(), e);
        }
    }
    
    /**
     * Если 无障碍СервисНе Работа，则注册 TIME_TICK 广播
     * 作为резервное копирование保活方案（每 мин.触发，比 AlarmManager 更频繁更可靠)
     */
    private void registerTimeTickIfNeeded() {
        if (!KeepAliveAccessibilityService.isRunning() && !KeepAliveReceiver.isTimeTickRegistered()) {
            AppLog.d(TAG, "无障碍СервисНе Работа， Передний планСервис注册 TIME_TICK");
            KeepAliveReceiver.registerTimeTick(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLog.d(TAG, "Service started");
        
        // 每 разЗапуск时проверка并注册 TIME_TICK
        registerTimeTickIfNeeded();
        
        // 确保 WakeLock Получение
        acquireWakeLock();
        
        // 确保УдалённыйСервис и 悬浮窗Запущено（处理 START_STICKY автоматическиперезагрузка 情况)
        // onCreate 可能不会 调用（СервисавтоматическиВосстановление时)，所以这里также要проверка
        ensureRemoteServicesStarted();
        startCameraRepairLoop();

        //  от IntentПолучениеУведомление内容，Если 没有则использованиеПо умолчанию内容
        String title = intent != null ? intent.getStringExtra("title") : null;
        String content = intent != null ? intent.getStringExtra("content") : null;

        if (title == null) {
            title = "Сервис камер работает";
        }
        if (content == null) {
            content = "Обработка удалённого запроса фото/записи";
        }

        // 创建Уведомление
        Notification notification = createNotification(title, content);

        // ЗапускПередний планСервис
        startForeground(NOTIFICATION_ID, notification);

        // 标记绪，выполнение所有ожидание 回调
        isForegroundReady = true;
        synchronized (pendingReadyCallbacks) {
            for (Runnable cb : pendingReadyCallbacks) {
                mainHandler.post(cb);
            }
            pendingReadyCallbacks.clear();
        }

        return START_STICKY;
    }
    
    /**
     * 确保УдалённыйСервис и 悬浮窗Запущено
     * 用于处理 START_STICKY автоматическиперезагрузка 情况（此时 onCreate 不会 调用)
     */
    private void ensureRemoteServicesStarted() {
        try {
            AppConfig appConfig = new AppConfig(this);
            if (!appConfig.isAutoStartOnBoot()) {
                return;  // Не Вкл启Вкл机自Запуск，跳过
            }
            
            // проверка并Запуск悬浮窗
            if (appConfig.isFloatingWindowEnabled() && !FloatingWindowService.isRunning()) {
                AppLog.d(TAG, "悬浮窗Не Работа，重新Запуск...");
                FloatingWindowService.start(this);
            }
            
            // проверка并ЗапускУдалённыйСервис（Если Не Работа)
            RemoteServiceManager serviceManager = RemoteServiceManager.getInstance();
            if (!serviceManager.hasAnyServiceRunning()) {
                AppLog.d(TAG, "УдалённыйСервисНе Работа，重新Запуск...");
                serviceManager.startRemoteServicesFromService(this);
            }
            
            // проверка并Запуск MainActivity（Если ВключитьавтоматическиЗапись且 Activity Не Работа)
            if (appConfig.isAutoStartRecording() && MainActivity.getInstance() == null) {
                startMainActivityForAutoRecording();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "确保СервисОшибка запуска: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroy() {
        AppLog.d(TAG, "Service destroyed - попыткаперезагрузка...");
        isForegroundReady = false;
        stopCameraRepairLoop();

        // Сервис 杀时，Отправка延迟перезагрузка广播
        scheduleServiceRestart();

        super.onDestroy();
    }

    private void startCameraRepairLoop() {
        stopCameraRepairLoop();
        cameraRepairRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    com.kooo.evcam.camera.MultiCameraManager cameraManager = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
                    if (cameraManager != null) {
                        int repaired = cameraManager.checkAndRepairCameras();
                        if (repaired > 0) {
                            AppLog.w(TAG, "Camera repair triggered for " + repaired + " cameras");
                        }
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "Camera repair loop error: " + e.getMessage(), e);
                }
                mainHandler.postDelayed(this, CAMERA_REPAIR_INTERVAL_MS);
            }
        };
        mainHandler.postDelayed(cameraRepairRunnable, CAMERA_REPAIR_INTERVAL_MS);
    }

    private void stopCameraRepairLoop() {
        if (cameraRepairRunnable != null) {
            mainHandler.removeCallbacks(cameraRepairRunnable);
            cameraRepairRunnable = null;
        }
    }
    
    /**
     * 当用户 от 最近задача滑动очисткаПриложение时调用
     * 这 保活 Выкл键：  очистка时重新ЗапускСервис
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        AppLog.d(TAG, "onTaskRemoved - Приложение  от 最近задачаочистка，попыткаперезагрузкаСервис...");
        
        // 立т.е.перезагрузкаСервис
        scheduleServiceRestart();
        
        super.onTaskRemoved(rootIntent);
    }
    
    /**
     * 调度Сервисперезагрузка
     * использование Handler 延迟перезагрузка，避免立т.е.перезагрузка Система拦截
     */
    private void scheduleServiceRestart() {
        try {
            // 方案1：использование Handler 延迟перезагрузка
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    AppLog.d(TAG, "выполнение延迟перезагрузка...");
                    start(getApplicationContext(), "EVCam", "Автоматический перезапуск сервиса");
                } catch (Exception e) {
                    AppLog.e(TAG, "延迟перезагрузкаОшибка: " + e.getMessage(), e);
                }
            }, RESTART_DELAY_MS);
            
            // 方案2：Отправка保活广播（резервное копирование)
            KeepAliveReceiver.sendKeepAliveCheck(getApplicationContext());
            
        } catch (Exception e) {
            AppLog.e(TAG, "调度перезагрузкаОшибка: " + e.getMessage(), e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建Уведомление渠道（Android 8.0+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Сервис камер",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Фоновая запись видео и фото");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建Уведомление
     */
    private Notification createNotification(String title, String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * обновлениеУведомление内容
     */
    public void updateNotification(String title, String content) {
        Notification notification = createNotification(title, content);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * 静态方法：ЗапускПередний планСервис
     * @param context 文
     * @param title Уведомление标题
     * @param content Уведомление内容
     */
    public static void start(Context context, String title, String content) {
        Intent intent = new Intent(context, CameraForegroundService.class);
        intent.putExtra("title", title);
        intent.putExtra("content", content);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 13+ имеет особые требования к запуску foreground-сервиса камеры:
            // если приложение не в foreground — пропускаем запуск, иначе система выкинет
            // ForegroundServiceStartNotAllowedException.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && !isAppInForeground(context)) {
                AppLog.d(TAG, "Приложение не в foreground — пропуск запуска CameraForegroundService");
                return;
            }
            // На запуск foreground-сервиса есть ограничения, но при записи нужно стартовать принудительно.
            // startForegroundService обязывает сервис вызвать startForeground в течение 5 секунд.
            try {
                context.startForegroundService(intent);
                AppLog.d(TAG, "Starting foreground service: " + title);
            } catch (Exception e) {
                AppLog.e(TAG, "Не удалось запустить foreground-сервис: " + e.getMessage(), e);
                // Резервный вариант — обычный startService
                try {
                    context.startService(intent);
                    AppLog.d(TAG, "Резервный запуск обычного сервиса");
                } catch (Exception e2) {
                    AppLog.e(TAG, "Обычный запуск также не удался: " + e2.getMessage(), e2);
                }
            }
        } else {
            context.startService(intent);
            AppLog.d(TAG, "Starting service: " + title);
        }
    }
    
    /**
     * проверкаПриложение 否 Передний план
     */
    private static boolean isAppInForeground(Context context) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo process : processes) {
                        if (process.processName.equals(context.getPackageName())) {
                            // 优先проверка IMPORTANCE_FOREGROUND или IMPORTANCE_VISIBLE
                            return process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                                    || process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
                        }
                    }
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "проверкаПриложениеПередний планСтатусОшибка: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * 静态方法：ОстановкаПередний планСервис
     * @param context 文
     */
    public static void stop(Context context) {
        isForegroundReady = false;
        Intent intent = new Intent(context, CameraForegroundService.class);
        context.stopService(intent);
        AppLog.d(TAG, "Stopping foreground service");
    }
}
