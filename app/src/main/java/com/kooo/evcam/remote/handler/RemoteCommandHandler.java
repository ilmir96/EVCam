package com.kooo.evcam.remote.handler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.kooo.evcam.AppConfig;
import com.kooo.evcam.AppLog;
import com.kooo.evcam.CameraForegroundService;
import com.kooo.evcam.FloatingWindowService;
import com.kooo.evcam.WakeUpHelper;
import com.kooo.evcam.remote.core.ChatIdentifier;
import com.kooo.evcam.remote.core.RecordingContext;
import com.kooo.evcam.remote.core.RemotePlatform;
import com.kooo.evcam.remote.core.RemoteUploadCallback;
import com.kooo.evcam.remote.upload.MediaFileFinder;
import com.kooo.evcam.remote.upload.MediaUploadService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Удалённыйкоманда处理器抽象基类
 * содержит所有平台公Всего  Удалённая запись/Фото逻辑（约90% 代码)
 * 子类只需实现平台特定 方法
 */
public abstract class RemoteCommandHandler {
    private static final String TAG = "RemoteCommandHandler";
    
    protected final Context context;
    protected final AppConfig appConfig;
    protected final MediaFileFinder mediaFileFinder;
    protected final Handler mainHandler;
    
    // Статусуправление
    private volatile boolean isRemoteRecording = false;
    private volatile boolean isPreparingRecording = false;
    private RecordingContext currentContext = null;
    
    // автоматическиОстановка相Выкл
    private Handler autoStopHandler;
    private Runnable autoStopRunnable;
    private int pendingDurationSeconds = 0;
    
    // Камера控制器接口（由 MainActivity 提供)
    private CameraController cameraController;
    
    // ЗаписьСтатус监听器（由 MainActivity 提供)
    private RecordingStateListener recordingStateListener;
    
    /**
     * Камера控制器接口
     * 由 MainActivity 实现，提供Камераоперация能力
     */
    public interface CameraController {
        boolean isRecording();
        boolean hasConnectedCameras();
        boolean startRecording(String timestamp);
        void stopRecording(boolean skipTransfer);
        void takePicture(String timestamp);
        void stopRecordingTimer();
        void stopBlinkAnimation();
        void startRecording();  // ВосстановлениевручнуюЗапись
        void setSegmentDurationOverride(long durationMs);  // Настройки分时长覆盖（用于Удалённая запись)
        void clearSegmentDurationOverride();  // очистка分时长覆盖
    }
    
    /**
     * ЗаписьСтатус监听器
     * 由 MainActivity 实现，用于обновление UI Статус
     */
    public interface RecordingStateListener {
        void onRemoteRecordingStart();
        void onRemoteRecordingStop();
        void onPreparing();
        void onPreparingComplete();
        void returnToBackgroundIfRemoteWakeUp();
        boolean isRemoteWakeUp();
    }
    
    public RemoteCommandHandler(Context context) {
        this.context = context.getApplicationContext();
        this.appConfig = new AppConfig(context);
        this.mediaFileFinder = new MediaFileFinder(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.autoStopHandler = new Handler(Looper.getMainLooper());
    }
    
    // ==================== 依赖注入 ====================
    
    public void setCameraController(CameraController controller) {
        this.cameraController = controller;
    }
    
    public void setRecordingStateListener(RecordingStateListener listener) {
        this.recordingStateListener = listener;
    }
    
    // ==================== Статус查询 ====================
    
    public boolean isRemoteRecording() {
        return isRemoteRecording;
    }
    
    public boolean isPreparingRecording() {
        return isPreparingRecording;
    }
    
    public RecordingContext getCurrentContext() {
        return currentContext;
    }
    
    // ==================== Удалённая запись - 公Всего 逻辑 ====================
    
    /**
     * ЗапускУдалённая запись
     * 这 主入口方法，содержит完整 Запись流程
     */
    public void startRemoteRecording(ChatIdentifier chatId, int durationSeconds) {
        String platformName = getPlatformName();
        AppLog.d(TAG, platformName + " Удалённая запись: chatId=" + chatId.getId() + ", duration=" + durationSeconds);
        
        // 1. проверка 否有Удалённая записьзадачаВыполняется 进行
        if (isRemoteRecording) {
            AppLog.w(TAG, "Удалённая записьзадачаВыполняется выполняется，отклонить新 " + platformName + "Записькоманда");
            sendError(chatId, "Удалённая запись уже выполняется, дождитесь завершения");
            return;
        }
        
        // 2. проверкаКамера控制器
        if (cameraController == null) {
            AppLog.e(TAG, "Камера控制器Не Настройки");
            sendError(chatId, "Камера не инициализирована");
            returnToBackgroundIfNeeded();
            return;
        }
        
        // 3. проверка 否有Подключено Камера
        if (!cameraController.hasConnectedCameras()) {
            AppLog.e(TAG, "Нет доступных камер");
            sendError(chatId, "Нет доступных камер");
            returnToBackgroundIfNeeded();
            return;
        }
        
        // 4. 生成统一 时间戳
        String timestamp = generateTimestamp();
        AppLog.d(TAG, platformName + " Запись统一时间戳: " + timestamp);
        
        // 5. 创建Запись文
        currentContext = new RecordingContext(chatId, durationSeconds, timestamp);
        
        // 6. Если Выполняется вручнуюЗапись，记录Статус并Остановка
        if (cameraController.isRecording()) {
            currentContext.setWasManualRecordingBefore(true);
            AppLog.d(TAG, platformName + ": ОбнаруженовручнуюЗаписьВыполняется 进行，ПаузавручнуюЗапись");
            cameraController.stopRecording(false);
            cameraController.stopRecordingTimer();
            cameraController.stopBlinkAnimation();
            
            // ожиданиеОстановказавершение
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        // 7. 标记Вкл始Удалённая запись
        isRemoteRecording = true;
        
        // 8. Настройки分时长覆盖（Удалённая запись不分)
        // 将分时长Настройки为Запись时长 + 30 сек.余量，确保整 шт.Запись过程不会触发分
        long segmentOverrideMs = (durationSeconds + 30) * 1000L;
        cameraController.setSegmentDurationOverride(segmentOverrideMs);
        AppLog.d(TAG, platformName + " Настройки分时长覆盖: " + (segmentOverrideMs / 1000) + "  сек.（Отключить分)");
        
        // 9. Начать запись
        boolean success = cameraController.startRecording(timestamp);
        if (success) {
            onRecordingStarted(currentContext, durationSeconds);
        } else {
            onRecordingFailed(currentContext);
        }
    }
    
    /**
     * ЗаписьУспешноЗапуск后 处理
     */
    private void onRecordingStarted(RecordingContext ctx, int durationSeconds) {
        String platformName = getPlatformName();
        AppLog.d(TAG, platformName + " Удалённая записьВкл始");
        isPreparingRecording = true;
        
        // Уведомление监听器
        if (recordingStateListener != null) {
            recordingStateListener.onRemoteRecordingStart();
            recordingStateListener.onPreparing();
        }
        
        // ЗапускПередний планСервис保护
        CameraForegroundService.start(context, platformName + " Удалённая запись", 
                "Выполняется Запись " + durationSeconds + "  сек. видео...");
        
        // ОтправкаЗаписьСтатус广播
        FloatingWindowService.sendRecordingStateChanged(context, true);
        
        // НастройкиавтоматическиОстановка定时器
        setupAutoStop(ctx, durationSeconds);
    }
    
    /**
     * НастройкиавтоматическиОстановка定时器
     */
    private void setupAutoStop(RecordingContext ctx, int durationSeconds) {
        autoStopRunnable = () -> {
            String platformName = getPlatformName();
            AppLog.d(TAG, platformName + " " + durationSeconds + "  сек.Записьзавершение，Выполняется Остановка...");
            
            // Остановить запись（跳过автоматически传输，等Загрузка завершена后再传输)
            if (cameraController != null) {
                cameraController.stopRecording(true);
                // очистка分时长覆盖（Восстановление为用户конфигурация值)
                cameraController.clearSegmentDurationOverride();
            }
            
            // ОстановкаПередний планСервис
            CameraForegroundService.stop(context);
            
            // ОтправкаЗаписьСтатус广播
            FloatingWindowService.sendRecordingStateChanged(context, false);
            
            // обновлениеСтатус
            isPreparingRecording = false;
            isRemoteRecording = false;
            
            // Уведомление监听器Записьзавершить（Остановка闪烁动画、Восстановление按钮颜色等)
            if (recordingStateListener != null) {
                recordingStateListener.onRemoteRecordingStop();
            }
            
            // 延迟后处理传 и Восстановление
            mainHandler.postDelayed(() -> {
                handleRecordingComplete(ctx);
            }, 1000);
        };
        
        // 定时器延迟 до 首 раз数据写入后Запуск
        pendingDurationSeconds = durationSeconds;
        AppLog.d(TAG, getPlatformName() + " Запись定时器将 首 раз数据写入ЗЗапуск，时长: " + durationSeconds + "  сек.");
    }
    
    /**
     * Уведомление首 раз数据写入завершение，Запуск定时器
     * 由 MainActivity  ОбнаруженоЗапись数据写入时调用
     */
    public void onFirstDataWritten() {
        if (pendingDurationSeconds > 0 && autoStopRunnable != null) {
            AppLog.d(TAG, "首 раз数据写入，Запуск定时器: " + pendingDurationSeconds + "  сек.");
            autoStopHandler.postDelayed(autoStopRunnable, pendingDurationSeconds * 1000L);
            pendingDurationSeconds = 0;
        }
    }
    
    /**
     * Уведомление时间戳обновление（Watchdog 重建Запись后调用)
     * 由 MainActivity  Запись时间戳变化时调用
     */
    public void onTimestampUpdated(String newTimestamp) {
        if (isRemoteRecording && currentContext != null) {
            String oldTimestamp = currentContext.getTimestamp();
            currentContext.setTimestamp(newTimestamp);
            AppLog.d(TAG, getPlatformName() + " Удалённая запись时间戳обновление: " + oldTimestamp + " -> " + newTimestamp);
        }
    }
    
    /**
     * Записьзавершение后 处理（传 и Восстановление)
     */
    private void handleRecordingComplete(RecordingContext ctx) {
        final boolean shouldResumeRecording = ctx.wasManualRecordingBefore();
        
        // 传Видео
        uploadVideos(ctx);
        
        // ВосстановлениевручнуюЗапись（Если до有)
        if (shouldResumeRecording && cameraController != null) {
            mainHandler.postDelayed(() -> {
                if (!isRemoteRecording && cameraController != null && !cameraController.isRecording()) {
                    AppLog.d(TAG, "Восстановлениедо вручнуюЗапись");
                    cameraController.startRecording();
                }
            }, 500);
        }
    }
    
    /**
     * ЗаписьЗапускОшибка 处理
     */
    private void onRecordingFailed(RecordingContext ctx) {
        String platformName = getPlatformName();
        AppLog.e(TAG, platformName + " Удалённая записьЗапускОшибка");
        isRemoteRecording = false;
        
        // очистка分时长覆盖
        if (cameraController != null) {
            cameraController.clearSegmentDurationOverride();
        }
        
        // Если до有вручнуюЗапись，попыткаВосстановление
        if (ctx.wasManualRecordingBefore() && cameraController != null) {
            AppLog.d(TAG, platformName + " Удалённая записьЗапускОшибка，попыткаВосстановлениевручнуюЗапись");
            cameraController.startRecording();
        }
        
        sendError(ctx.getChatId(), "ЗаписьЗапускОшибка");
        returnToBackgroundIfNeeded();
    }
    
    // ==================== УдалённыйФото - 公Всего 逻辑 ====================
    
    /**
     * ЗапускУдалённыйФото
     */
    public void startRemotePhoto(ChatIdentifier chatId) {
        String platformName = getPlatformName();
        AppLog.d(TAG, platformName + " УдалённыйФото: chatId=" + chatId.getId());
        
        // 1. проверкаКамера控制器
        if (cameraController == null) {
            AppLog.e(TAG, "Камера控制器Не Настройки");
            sendError(chatId, "Камера не инициализирована");
            returnToBackgroundIfNeeded();
            return;
        }
        
        // 2. проверкаКамераПодключение
        if (!cameraController.hasConnectedCameras()) {
            AppLog.e(TAG, "Нет доступных камер");
            sendError(chatId, "Нет доступных камер");
            returnToBackgroundIfNeeded();
            return;
        }
        
        // 3. 生成时间戳
        String timestamp = generateTimestamp();
        AppLog.d(TAG, platformName + " Фото时间戳: " + timestamp);
        
        // 4. выполнениеФото
        cameraController.takePicture(timestamp);
        AppLog.d(TAG, platformName + " УдалённыйФотовыполнение");
        
        // 5. ожиданиеФотозавершение后传（5 сек.延迟)
        final String finalTimestamp = timestamp;
        mainHandler.postDelayed(() -> {
            uploadPhotos(chatId, finalTimestamp);
        }, 5000);
    }
    
    // ==================== 传逻辑 ====================
    
    /**
     * 传Запись Видео
     */
    private void uploadVideos(RecordingContext ctx) {
        String platformName = getPlatformName();
        List<String> allTimestamps = ctx.getAllTimestamps();
        ChatIdentifier chatId = ctx.getChatId();
        
        // проверка API 客户端
        if (!isApiClientReady()) {
            AppLog.e(TAG, platformName + " API 客户端Не инициализация");
            returnToBackgroundIfNeeded();
            return;
        }
        
        // 查找ВидеоФайл（использование所有时间戳，包括 Watchdog 重建前后 )
        List<File> videoFiles = mediaFileFinder.findVideoFiles(allTimestamps);
        if (videoFiles.isEmpty()) {
            AppLog.e(TAG, "Не найдены записанные видеофайлы，时间戳: " + allTimestamps);
            sendError(chatId, "Не найдены записанные видеофайлы");
            returnToBackgroundIfNeeded();
            return;
        }
        
        AppLog.d(TAG, "找 до  " + videoFiles.size() + " видеофайл(ов)，Вкл始传 до " + platformName);
        
        // 创建传Сервис并传
        MediaUploadService uploadService = createVideoUploadService();
        uploadService.uploadVideos(videoFiles, chatId, new RemoteUploadCallback() {
            @Override
            public void onProgress(String message) {
                AppLog.d(TAG, platformName + " Видео传进度: " + message);
            }
            
            @Override
            public void onSuccess(String message) {
                AppLog.d(TAG, platformName + " Видео传Успешно: " + message);
                
                // 传输временноФайл до 最终каталог
                mediaFileFinder.transferToFinalDir(videoFiles);
                
                returnToBackgroundIfNeeded();
            }
            
            @Override
            public void onError(String error) {
                AppLog.e(TAG, platformName + " ВидеоОшибка загрузки: " + error);
                
                // т.е.使Ошибка загрузки，также要传输Файл до 最终ХранилищеПозиция（保留Видео)
                mediaFileFinder.transferToFinalDir(videoFiles);
                
                // 平台特定 Ошибка处理（еслиФайл大小限制Уведомление)
                handleUploadError(chatId, error);
                
                returnToBackgroundIfNeeded();
            }
        });
    }
    
    /**
     * 传拍 Фото
     */
    private void uploadPhotos(ChatIdentifier chatId, String timestamp) {
        String platformName = getPlatformName();
        
        // проверка API 客户端
        if (!isApiClientReady()) {
            AppLog.e(TAG, platformName + " API 客户端Не инициализация");
            returnToBackgroundIfNeeded();
            return;
        }
        
        // 查找ФотоФайл
        List<File> photoFiles = mediaFileFinder.findPhotoFiles(timestamp);
        if (photoFiles.isEmpty()) {
            AppLog.e(TAG, "Не найдены сделанные фото，时间戳: " + timestamp);
            sendError(chatId, "Не найдены сделанные фото");
            returnToBackgroundIfNeeded();
            return;
        }
        
        AppLog.d(TAG, "找 до  " + photoFiles.size() + " фото，Вкл始传 до " + platformName);
        
        // 创建传Сервис并传
        MediaUploadService uploadService = createPhotoUploadService();
        uploadService.uploadPhotos(photoFiles, chatId, new RemoteUploadCallback() {
            @Override
            public void onProgress(String message) {
                AppLog.d(TAG, platformName + " Фото传进度: " + message);
            }
            
            @Override
            public void onSuccess(String message) {
                AppLog.d(TAG, platformName + " Фото传Успешно: " + message);
                returnToBackgroundIfNeeded();
            }
            
            @Override
            public void onError(String error) {
                AppLog.e(TAG, platformName + " ФотоОшибка загрузки: " + error);
                returnToBackgroundIfNeeded();
            }
        });
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 生成时间戳
     */
    protected String generateTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
    }
    
    /**
     * 返回Фоновый режим（Если  Удалённый唤醒 )
     */
    protected void returnToBackgroundIfNeeded() {
        if (recordingStateListener != null) {
            recordingStateListener.returnToBackgroundIfRemoteWakeUp();
        }
    }
    
    /**
     * Очистка 资源
     */
    public void cleanup() {
        if (autoStopHandler != null && autoStopRunnable != null) {
            autoStopHandler.removeCallbacks(autoStopRunnable);
        }
        isRemoteRecording = false;
        isPreparingRecording = false;
        currentContext = null;
    }
    
    // ==================== 抽象方法 - 平台特定实现 ====================
    
    /**
     * Получение平台名称
     */
    protected abstract String getPlatformName();
    
    /**
     * Получение平台类型
     */
    protected abstract RemotePlatform getPlatform();
    
    /**
     * проверка API 客户端 否绪
     */
    protected abstract boolean isApiClientReady();
    
    /**
     * Отправка消息
     */
    public abstract void sendMessage(ChatIdentifier chatId, String message);
    
    /**
     * ОтправкаОшибка消息
     */
    public abstract void sendError(ChatIdentifier chatId, String error);
    
    /**
     * 创建Видео传Сервис
     */
    protected abstract MediaUploadService createVideoUploadService();
    
    /**
     * 创建Фото传Сервис
     */
    protected abstract MediaUploadService createPhotoUploadService();
    
    /**
     * 处理传Ошибка（平台特定，еслиФайл大小限制Уведомление)
     * 子类可重写以添加平台特定 Ошибка处理
     */
    protected void handleUploadError(ChatIdentifier chatId, String error) {
        // По умолчанию不做额外处理，子类可重写
    }
}
