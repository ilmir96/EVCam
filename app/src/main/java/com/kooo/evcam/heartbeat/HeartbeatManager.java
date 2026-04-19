package com.kooo.evcam.heartbeat;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.camera.SingleCamera;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Мониторингуправление器
 * 负责定时调度 и 生命周期управление
 */
public class HeartbeatManager {
    private static final String TAG = "HeartbeatManager";
    
    private final Context context;
    private final HeartbeatConfig config;
    private final HeartbeatImageProcessor imageProcessor;
    private final HeartbeatApiClient apiClient;
    private final Handler mainHandler;
    private final ExecutorService executor;
    
    // Статус
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isForeground = new AtomicBoolean(false);
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private final AtomicBoolean isScreenOn = new AtomicBoolean(true);  // 屏幕 否亮起
    
    private Runnable heartbeatRunnable;
    
    // 相机列表（由 MainActivity Настройки，использование强引用避免 GC)
    private List<SingleCamera> camerasList;
    
    // Статус提供者（由 MainActivity Настройки，использование强引用避免 GC)
    private StatusProvider statusProvider;
    
    // Activity 控制器（用于息屏推图时控制 Activity)
    private ActivityController activityController;
    
    // Статус监听器
    private WeakReference<HeartbeatListener> listenerRef;
    
    // 息屏推图相Выкл
    private Runnable screenOffHeartbeatRunnable;
    private static final long SCREEN_OFF_HEARTBEAT_DELAY_MS = 30000; // 息屏后30 сек.Вкл始推图
    private volatile boolean wakeUpByHeartbeat = false;  //  否由息屏推图唤醒 
    
    /**
     * App Статус提供者接口
     */
    public interface StatusProvider {
        /**
         * Получение App Статус JSON 字符串
         */
        String getAppStatusJson();
    }
    
    /**
     * Activity 控制器接口
     * 用于 息屏推图时控制 Activity  唤醒 и 退Фоновый режим
     */
    public interface ActivityController {
        /**  否 Фоновый режим */
        boolean isInBackground();
        /**  否Выполняется Запись */
        boolean isRecording();
        /**  否应该保持Передний план（если息屏Записьрежим) */
        boolean shouldKeepForeground();
        /** 唤醒 Activity  до Передний план */
        void wakeUpToForeground();
        /** 退 до Фоновый режим */
        void moveToBackground();
        /** открыть所有相机 */
        void openCameras();
        /** Закрыто所有相机 */
        void closeCameras();
        /** проверка相机 否Подключено */
        boolean hasCamerasConnected();
    }
    
    /**
     * 心跳Статус监听器
     */
    public interface HeartbeatListener {
        void onHeartbeatStarted();
        void onHeartbeatStopped();
        void onHeartbeatSuccess(long timestamp);
        void onHeartbeatFailed(String error);
    }
    
    public HeartbeatManager(Context context) {
        this.context = context.getApplicationContext();
        this.config = new HeartbeatConfig(context);
        this.imageProcessor = new HeartbeatImageProcessor();
        this.apiClient = new HeartbeatApiClient();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        
        // 检测Текущий屏幕Статус（Вкл机时可能 息屏Статус)
        detectInitialScreenState();
    }
    
    /**
     * 检测初始屏幕Статус
     * 解决Вкл机时屏幕息屏但不会Получена команда:  ACTION_SCREEN_OFF 广播 问题
     */
    private void detectInitialScreenState() {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                boolean interactive = pm.isInteractive();
                isScreenOn.set(interactive);
                AppLog.d(TAG, "检测初始屏幕Статус: " + (interactive ? "亮屏" : "息屏"));
            }
        } catch (Exception e) {
            AppLog.e(TAG, "检测屏幕СтатусОшибка", e);
        }
    }
    
    /**
     * Настройки相机列表
     */
    public void setCameras(List<SingleCamera> cameras) {
        this.camerasList = cameras;
    }
    
    /**
     * НастройкиСтатус提供者
     */
    public void setStatusProvider(StatusProvider provider) {
        this.statusProvider = provider;
    }
    
    /**
     * Настройки Activity 控制器（用于息屏推图)
     */
    public void setActivityController(ActivityController controller) {
        this.activityController = controller;
    }
    
    /**
     * Настройки监听器
     */
    public void setListener(HeartbeatListener listener) {
        this.listenerRef = new WeakReference<>(listener);
    }
    
    /**
     * Получениеконфигурация 象
     */
    public HeartbeatConfig getConfig() {
        return config;
    }
    
    /**
     * конфигурация变更时调用
     * 根据Текущий屏幕Статус重新Запуск相应 推图режим
     */
    public void onConfigChanged() {
        AppLog.d(TAG, "конфигурация变更，重新评估推图Статус");
        
        // Остановка所有推图
        stop();
        stopScreenOffHeartbeat();
        
        // Если конфигурация不完整，直接返回
        if (!config.isConfigured()) {
            AppLog.d(TAG, "конфигурация不完整");
            return;
        }
        
        // Если Вкл启"автоматическиЗапускСервис"，автоматическиНастройки enabled = true
        if (config.isAutoStartEnabled() && !config.isEnabled()) {
            AppLog.d(TAG, "автоматическиЗапускСервисВкл启，автоматическиВключить心跳Сервис");
            config.setEnabled(true);
        }
        
        if (!config.isEnabled()) {
            AppLog.d(TAG, "心跳СервисНе Включить");
            return;
        }
        
        // 根据屏幕СтатусЗапуск相应推图
        if (isScreenOn.get()) {
            // 亮屏Статус
            if (config.isScreenOnPushEnabled() && activityController != null && !activityController.isInBackground()) {
                mainHandler.postDelayed(this::start, 500);
            }
        } else {
            // 息屏Статус
            if (config.isScreenOffPushEnabled()) {
                startScreenOffHeartbeat();
            }
        }
    }
    
    /**
     * проверка 否Выполняется Работа（亮屏推图или息屏推图)
     */
    public boolean isRunning() {
        // 亮屏推图定时器Работа，или息屏推图定时器Работа
        return isRunning.get() || screenOffHeartbeatRunnable != null;
    }
    
    /**
     * проверка亮屏推图 否Выполняется Работа
     */
    public boolean isScreenOnPushRunning() {
        return isRunning.get();
    }
    
    /**
     * проверка息屏推图 否Выполняется Работа
     */
    public boolean isScreenOffPushRunning() {
        return screenOffHeartbeatRunnable != null;
    }
    
    /**
     * 屏幕Закрыто时调用
     * Остановка亮屏推图，Запуск息屏推图定时器
     */
    public void onScreenOff() {
        isScreenOn.set(false);
        AppLog.d(TAG, "屏幕Статус: 息屏");
        
        // Остановка亮屏推图
        pause();
        
        // Запуск息屏推图
        startScreenOffHeartbeat();
    }
    
    /**
     * 屏幕открыть时调用
     * Остановка息屏推图，根据конфигурацияЗапуск亮屏推图
     */
    public void onScreenOn() {
        isScreenOn.set(true);
        AppLog.d(TAG, "屏幕Статус: 亮屏");
        
        // Остановка息屏推图
        stopScreenOffHeartbeat();
    }
    
    /**
     * Получение屏幕Статус
     */
    public boolean isScreenOn() {
        return isScreenOn.get();
    }
    
    // ==================== 息屏推图 ====================
    
    /**
     * Запуск息屏推图定时器
     * 息屏后30 сек.Вкл始，按设定间隔定时推图
     */
    private void startScreenOffHeartbeat() {
        if (!config.isEnabled() || !config.isConfigured() || !config.isScreenOffPushEnabled()) {
            AppLog.d(TAG, "息屏推图Не Включитьиликонфигурация不完整");
            return;
        }
        
        // Отмена有 定时器
        stopScreenOffHeartbeat();
        
        AppLog.d(TAG, "息屏推图将 30 сек.ЗЗапуск");
        
        // 30 сек.后Вкл始Первый раз推图
        screenOffHeartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScreenOn.get()) {
                    AppLog.d(TAG, "屏幕亮起，Остановка息屏推图");
                    return;
                }
                
                if (!config.isEnabled() || !config.isScreenOffPushEnabled()) {
                    AppLog.d(TAG, "息屏推图Отключено");
                    return;
                }
                
                // выполнение推图
                executeScreenOffHeartbeat();
                
                // 调度一 раз
                if (!isScreenOn.get() && config.isScreenOffPushEnabled()) {
                    mainHandler.postDelayed(this, config.getIntervalSeconds() * 1000L);
                }
            }
        };
        
        mainHandler.postDelayed(screenOffHeartbeatRunnable, SCREEN_OFF_HEARTBEAT_DELAY_MS);
    }
    
    /**
     * Остановка息屏推图定时器
     */
    private void stopScreenOffHeartbeat() {
        if (screenOffHeartbeatRunnable != null) {
            mainHandler.removeCallbacks(screenOffHeartbeatRunnable);
            screenOffHeartbeatRunnable = null;
            AppLog.d(TAG, "息屏推图定时器Остановлено");
        }
    }
    
    /**
     * выполнение息屏推图
     * Если  Фоновый режим，先唤醒 до Передний план，推图завершение后退回Фоновый режим
     */
    private void executeScreenOffHeartbeat() {
        if (activityController == null) {
            AppLog.w(TAG, "ActivityController Не Настройки，无法выполнение息屏推图");
            return;
        }
        
        boolean inBackground = activityController.isInBackground();
        boolean hasCameras = activityController.hasCamerasConnected();
        
        AppLog.d(TAG, "выполнение息屏推图, isInBackground=" + inBackground + ", hasCameras=" + hasCameras);
        
        // Если 相机Подключено，说明Приложение实际 Передний план工作Статус
        // （可能  DingTalk等Удалённыйкоманда唤醒 ，但 isInBackground 值不准确)
        // 此时直接推图，不необходимо退Фоновый режим
        if (hasCameras) {
            wakeUpByHeartbeat = false;
            AppLog.d(TAG, "息屏推图：相机Подключено，直接推图不退Фоновый режим");
            executeOnceInternal(false);
            return;
        }
        
        if (inBackground) {
            //  Фоновый режим且Камера не подключена：необходимо唤醒 до Передний план，标记为息屏推图唤醒
            wakeUpByHeartbeat = true;
            wakeUpForHeartbeat();
        } else {
            //  Передний план：直接推图，不необходимо退Фоновый режим
            wakeUpByHeartbeat = false;
            executeOnceInternal(false);
        }
    }
    
    /**
     * 为息屏推图唤醒 до Передний план
     * 流程：唤醒Activity → ожидание onResume автоматическиоткрыть相机 → 推图 → Закрыто相机 → 退Фоновый режим
     */
    private void wakeUpForHeartbeat() {
        if (activityController == null) {
            return;
        }
        
        AppLog.d(TAG, "息屏推图：唤醒 до Передний план");
        activityController.wakeUpToForeground();
        
        // ожидание Activity.onResume() автоматическиоткрыть相机（onResume 有 500ms 延迟открыть)
        // 这里ожидание 2  сек.，让 onResume  相机открыть流程завершение，避免重复调用
        mainHandler.postDelayed(() -> {
            if (activityController == null) return;
            
            AppLog.d(TAG, "息屏推图：проверка相机Статус");
            
            // проверка相机 否Подключено（由 onResume открыть)
            if (activityController.hasCamerasConnected()) {
                // 相机绪，直接推图
                AppLog.d(TAG, "息屏推图：相机绪");
                doHeartbeatAndReturnToBackground();
            } else {
                // 相机还没准备好，再等 2  сек.
                AppLog.d(TAG, "息屏推图：ожидание相机绪...");
                mainHandler.postDelayed(() -> {
                    if (activityController != null && activityController.hasCamerasConnected()) {
                        doHeartbeatAndReturnToBackground();
                    } else {
                        AppLog.w(TAG, "息屏推图：相机Не 能绪，跳过本 раз推图");
                        returnToBackgroundAfterHeartbeat();
                    }
                }, 2000);
            }
        }, 2000);  // ожидание onResume завершение相机открыть
    }
    
    /**
     * выполнение推图并退回Фоновый режим
     */
    private void doHeartbeatAndReturnToBackground() {
        AppLog.d(TAG, "息屏推图：выполнение推图");
        
        // выполнение推图
        executor.execute(() -> {
            isExecuting.set(true);
            try {
                doHeartbeat();
            } finally {
                isExecuting.set(false);
                // 推图завершение后退Фоновый режим
                mainHandler.postDelayed(this::returnToBackgroundAfterHeartbeat, 2000);
            }
        });
    }
    
    /**
     * 推图завершение后退回Фоновый режим
     */
    private void returnToBackgroundAfterHeartbeat() {
        if (activityController == null) return;
        
        // Сброс标志
        boolean wasWakeUpByHeartbeat = wakeUpByHeartbeat;
        wakeUpByHeartbeat = false;
        
        boolean currentlyInBackground = activityController.isInBackground();
        boolean screenOn = isScreenOn.get();
        boolean recording = activityController.isRecording();
        boolean keepForeground = activityController.shouldKeepForeground();
        
        AppLog.d(TAG, "returnToBackground проверка: wakeUpByHB=" + wasWakeUpByHeartbeat + 
                ", inBackground=" + currentlyInBackground + 
                ", screenOn=" + screenOn + 
                ", recording=" + recording + 
                ", keepForeground=" + keepForeground);
        
        // 只有由息屏推图唤醒 才考虑退Фоновый режим
        if (!wasWakeUpByHeartbeat) {
            AppLog.d(TAG, "非息屏推图唤醒，不退Фоновый режим");
            return;
        }
        
        // проверкаПриложениеТекущий 否 Передний план（Если  Передний план不退)
        // 这可以防止：DingTalk唤醒后，息屏推图又 Приложение退回Фоновый режим
        if (!currentlyInBackground) {
            AppLog.d(TAG, "ПриложениеТекущий Передний план，不退Фоновый режим");
            return;
        }
        
        // проверка 否仍然息屏
        if (screenOn) {
            AppLog.d(TAG, "屏幕亮起，不退Фоновый режим");
            return;
        }
        
        // проверка 否Выполняется Запись
        if (recording) {
            AppLog.d(TAG, "Выполняется Запись，不退Фоновый режим");
            return;
        }
        
        // проверка 否应该保持Передний план（если息屏Записьрежим)
        if (keepForeground) {
            AppLog.d(TAG, "息屏Записьрежим，保持Передний план不退Фоновый режим");
            return;
        }
        
        AppLog.d(TAG, "息屏推图завершение，Закрыто相机并退Фоновый режим");
        
        // Закрыто相机
        activityController.closeCameras();
        
        // 退Фоновый режим
        activityController.moveToBackground();
    }
    
    /**
     * Запуск心跳（App 进入Передний план时调用)
     * 此方法用于亮屏Статус 推图，由 MainActivity.onResume() 调用
     * 注意：息屏Статус不会Запуск，息屏推图由 screenOffHeartbeat 单独управление
     */
    public void start() {
        AppLog.d(TAG, "start() 调用, enabled=" + config.isEnabled() + 
                ", configured=" + config.isConfigured() + 
                ", screenOn=" + isScreenOn.get() +
                ", screenOnPush=" + config.isScreenOnPushEnabled());
        
        if (!config.isEnabled()) {
            AppLog.d(TAG, "МониторингфункцияНе Включить");
            return;
        }
        
        if (!config.isConfigured()) {
            AppLog.w(TAG, "Мониторингконфигурация不完整: " + config.getConfigStatus());
            return;
        }
        
        // 息屏Статус不Запуск（息屏推图由 screenOffHeartbeat управление)
        if (!isScreenOn.get()) {
            AppLog.d(TAG, "息屏Статус，不Запуск亮屏推图");
            return;
        }
        
        // проверка亮屏推图ВклВыкл
        if (!config.isScreenOnPushEnabled()) {
            AppLog.d(TAG, "亮屏推图Не Включить，跳过Запуск");
            return;
        }
        
        isForeground.set(true);
        
        if (isRunning.get()) {
            AppLog.d(TAG, "心跳 Работа");
            return;
        }
        
        isRunning.set(true);
        AppLog.i(TAG, "МониторингСервисЗапуск, 间隔: " + config.getIntervalSeconds() + " сек.");
        
        notifyStarted();
        scheduleNextHeartbeat(true); // 立т.е.выполнениеПервый раз
    }
    
    /**
     * Пауза心跳（App 进入Фоновый режим时调用)
     */
    public void pause() {
        AppLog.d(TAG, "pause() 调用");
        
        isForeground.set(false);
        
        if (heartbeatRunnable != null) {
            mainHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
        
        if (isRunning.getAndSet(false)) {
            AppLog.i(TAG, "МониторингСервисПауза（进入Фоновый режим)");
            notifyStopped();
        }
    }
    
    /**
     * Остановка心跳（用户вручнуюЗакрытоили销毁时)
     */
    public void stop() {
        AppLog.d(TAG, "stop() 调用");
        
        isForeground.set(false);
        
        if (heartbeatRunnable != null) {
            mainHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
        
        if (isRunning.getAndSet(false)) {
            AppLog.i(TAG, "МониторингСервис остановлен");
            notifyStopped();
        }
    }
    
    /**
     * 销毁управление器
     */
    public void destroy() {
        stop();
        stopScreenOffHeartbeat();
        executor.shutdown();
    }
    
    /**
     * 调度一 раз心跳（только用于亮屏推图)
     * 
     * @param immediate  否立т.е.выполнение（用于Запуск时)
     */
    private void scheduleNextHeartbeat(boolean immediate) {
        if (!isForeground.get() || !config.isEnabled()) {
            isRunning.set(false);
            return;
        }
        
        // 息屏СтатусОстановка（息屏推图由 screenOffHeartbeat управление)
        if (!isScreenOn.get()) {
            AppLog.d(TAG, "息屏Статус，Остановка亮屏推图调度");
            isRunning.set(false);
            notifyStopped();
            return;
        }
        
        // проверка亮屏推图ВклВыкл
        if (!config.isScreenOnPushEnabled()) {
            AppLog.d(TAG, "亮屏推图Не Включить，Остановка调度");
            isRunning.set(false);
            notifyStopped();
            return;
        }
        
        heartbeatRunnable = () -> {
            if (!isForeground.get() || !config.isEnabled()) {
                isRunning.set(false);
                notifyStopped();
                return;
            }
            
            // 息屏СтатусОстановка
            if (!isScreenOn.get()) {
                AppLog.d(TAG, "息屏Статус，Остановка亮屏推图");
                isRunning.set(false);
                notifyStopped();
                return;
            }
            
            // проверка亮屏推图ВклВыкл
            if (!config.isScreenOnPushEnabled()) {
                AppLog.d(TAG, "亮屏推图Не Включить，Остановка");
                isRunning.set(false);
                notifyStopped();
                return;
            }
            
            executeHeartbeat();
            scheduleNextHeartbeat(false); // 递归调度
        };
        
        long delay = immediate ? 0 : config.getIntervalSeconds() * 1000L;
        mainHandler.postDelayed(heartbeatRunnable, delay);
    }
    
    /**
     * выполнение一 раз心跳
     */
    private void executeHeartbeat() {
        // 防止重复выполнение
        if (isExecuting.getAndSet(true)) {
            AppLog.w(TAG, "一 раз心跳还 выполнение，跳过本 раз");
            return;
        }
        
        executor.execute(() -> {
            try {
                doHeartbeat();
            } finally {
                isExecuting.set(false);
            }
        });
    }
    
    /**
     * 实际выполнение心跳逻辑
     */
    private void doHeartbeat() {
        long startTime = System.currentTimeMillis();
        AppLog.d(TAG, "Вкл始выполнение心跳...");
        
        try {
            // 1. Получение相机列表
            List<SingleCamera> cameras = camerasList;
            AppLog.d(TAG, "相机列表: " + (cameras == null ? "null" : cameras.size() + " шт."));
            
            if (cameras == null || cameras.isEmpty()) {
                AppLog.w(TAG, "相机列表пусто，跳过本 раз心跳");
                notifyFailed("Камера не готова");
                return;
            }
            
            // 过滤Подключено 相机
            cameras = filterConnectedCameras(cameras);
            AppLog.d(TAG, "Подключено相机: " + cameras.size() + " шт.");
            
            if (cameras.isEmpty()) {
                AppLog.w(TAG, "没有Подключено 相机，跳过本 раз心跳");
                notifyFailed("Камера не подключена");
                return;
            }
            
            // 2.  主线程捕获Изображение（必须 主线程операция TextureView)
            final List<SingleCamera> finalCameras = cameras;
            final Bitmap[] mergedHolder = new Bitmap[1];
            final boolean[] completed = new boolean[1];
            
            mainHandler.post(() -> {
                synchronized (mergedHolder) {
                    try {
                        mergedHolder[0] = imageProcessor.captureAndMerge(finalCameras);
                    } catch (Exception e) {
                        AppLog.e(TAG, "捕获Изображениеаномалия: " + e.getMessage());
                    }
                    completed[0] = true;
                    mergedHolder.notifyAll();
                }
            });
            
            // ожидание主线程завершение
            synchronized (mergedHolder) {
                if (!completed[0]) {
                    try {
                        mergedHolder.wait(5000); // 最多ожидание5 сек.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        notifyFailed("Захват прерван");
                        return;
                    }
                }
            }
            
            Bitmap merged = mergedHolder[0];
            if (merged == null) {
                AppLog.w(TAG, "Ошибка захвата изображения");
                notifyFailed("Ошибка захвата изображения");
                return;
            }
            
            int imageWidth = merged.getWidth();
            int imageHeight = merged.getHeight();
            int cameraCount = cameras.size();
            
            // 3. 压缩Изображение
            byte[] imageBytes = imageProcessor.compressToTargetSize(merged, config.getTargetSizeKB());
            merged.recycle();
            
            if (imageBytes == null || imageBytes.length == 0) {
                AppLog.w(TAG, "Ошибка сжатия изображения");
                notifyFailed("Ошибка сжатия изображения");
                return;
            }
            
            // 4. Получение App Статус
            String appStatus = null;
            if (statusProvider != null) {
                appStatus = statusProvider.getAppStatusJson();
            }
            
            // 5. Отправка求
            HeartbeatApiClient.HeartbeatResult result = apiClient.sendHeartbeat(
                    config.getServerUrl(),
                    config.getVehicleId(),
                    config.getSecretKey(),
                    imageBytes,
                    imageWidth,
                    imageHeight,
                    cameraCount,
                    appStatus
            );
            
            // 6. обновление统计
            long now = System.currentTimeMillis();
            config.setLastUploadTime(now);
            
            if (result.success) {
                config.incrementSuccessCount();
                long duration = now - startTime;
                AppLog.i(TAG, "心跳Успешно，耗时: " + duration + "ms, Изображение: " + (imageBytes.length / 1024) + "KB");
                notifySuccess(now);
            } else {
                config.incrementFailCount();
                config.setLastError(result.message);
                AppLog.w(TAG, "Ошибка heartbeat: " + result.message);
                notifyFailed(result.message);
            }
            
        } catch (Exception e) {
            config.incrementFailCount();
            config.setLastError(e.getMessage());
            AppLog.e(TAG, "心跳выполнениеаномалия: " + e.getMessage(), e);
            notifyFailed(e.getMessage());
        }
    }
    
    /**
     * 过滤Подключено 相机
     */
    private List<SingleCamera> filterConnectedCameras(List<SingleCamera> cameras) {
        java.util.ArrayList<SingleCamera> connected = new java.util.ArrayList<>();
        for (SingleCamera camera : cameras) {
            if (camera != null && camera.isConnected()) {
                connected.add(camera);
            }
        }
        return connected;
    }
    
    /**
     * вручнуювыполнение一 раз心跳（用于тестирование)
     */
    /**
     * выполнение一 раз心跳（供вручнуютестирование按钮调用)
     */
    public void executeOnce() {
        executeOnceInternal(true);
    }
    
    /**
     * Внутреннеевыполнение一 раз心跳
     * @param isManualTest  否 вручнуютестирование
     */
    private void executeOnceInternal(boolean isManualTest) {
        if (isExecuting.get()) {
            AppLog.w(TAG, "心跳Выполняется выполнение");
            return;
        }
        
        if (isManualTest) {
            AppLog.i(TAG, "вручнуювыполнение心跳тестирование");
        }
        
        executor.execute(() -> {
            isExecuting.set(true);
            try {
                doHeartbeat();
            } finally {
                isExecuting.set(false);
            }
        });
    }
    
    // ==================== Уведомление方法 ====================
    
    private void notifyStarted() {
        mainHandler.post(() -> {
            HeartbeatListener listener = listenerRef != null ? listenerRef.get() : null;
            if (listener != null) {
                listener.onHeartbeatStarted();
            }
        });
    }
    
    private void notifyStopped() {
        mainHandler.post(() -> {
            HeartbeatListener listener = listenerRef != null ? listenerRef.get() : null;
            if (listener != null) {
                listener.onHeartbeatStopped();
            }
        });
    }
    
    private void notifySuccess(long timestamp) {
        mainHandler.post(() -> {
            HeartbeatListener listener = listenerRef != null ? listenerRef.get() : null;
            if (listener != null) {
                listener.onHeartbeatSuccess(timestamp);
            }
        });
    }
    
    private void notifyFailed(String error) {
        mainHandler.post(() -> {
            HeartbeatListener listener = listenerRef != null ? listenerRef.get() : null;
            if (listener != null) {
                listener.onHeartbeatFailed(error);
            }
        });
    }
    
    // ==================== инструмент方法 ====================
    
    /**
     * 格式化时间戳
     */
    public static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "-";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
