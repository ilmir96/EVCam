package com.kooo.evcam.camera;


import com.kooo.evcam.AppConfig;
import com.kooo.evcam.AppLog;
import com.kooo.evcam.FileTransferManager;
import com.kooo.evcam.StorageHelper;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 四 кам.Камерауправление器
 */
public class MultiCameraManager {
    private static final String TAG = "MultiCameraManager";

    private static final int DEFAULT_MAX_OPEN_CAMERAS = 4;
    private static final long RECORDING_STABLE_FRAME_MAX_AGE_MS = 1500;
    private static final int MAX_STABLE_WAIT_ATTEMPTS = 10;
    private static final long STABLE_WAIT_INTERVAL_MS = 200;
    // ЗаписьРазрешение将использование预览 实际Разрешение，不再硬编码

    private final Context context;
    private final Map<String, SingleCamera> cameras = new LinkedHashMap<>();
    private final Map<String, VideoRecorder> recorders = new LinkedHashMap<>();
    private final Map<String, CodecVideoRecorder> codecRecorders = new LinkedHashMap<>();  // 软编码Запись器
    private final List<String> activeCameraKeys = new ArrayList<>();
    private int maxOpenCameras = DEFAULT_MAX_OPEN_CAMERAS;

    private boolean isRecording = false;
    private volatile boolean repairSuppressed = false;  // 主动ЗакрытоКамера时抑制 repair loop
    private boolean useCodecRecording = false;  //  否использование软编码Запись（用于 L6/L7)
    private boolean useRelayWrite = false;      //  否использование转写入（Запись до Внутренняя память，异步传输 до USB-накопитель)
    private File finalSaveDir = null;           // 最终Хранилищекаталог（用于转写入режим)
    private volatile int lastNotifiedSegmentIndex = -1;  // Уведомление 分索引，避免重复Уведомление
    private long overrideSegmentDurationMs = 0;  // временно覆盖分时长（0=использованиеконфигурация值，>0=использование此值)
    
    // 统一分时间戳управление（解决多 кам.Камера分切换时时间戳差1 сек. 问题)
    private String cachedSegmentTimestamp = null;  // 缓存 分时间戳
    private long timestampGeneratedTime = 0;  // 时间戳生成时间（毫 сек.)
    private static final long TIMESTAMP_CACHE_DURATION_MS = 10000;  // 时间戳缓存действует期（10 сек.，需覆盖各Камера首 раз写入 时间差)
    private final Object timestampLock = new Object();  // 时间戳доступ锁
    
    // Watchdog 回退相Выкл
    private String currentRecordingTimestamp = null;  // ТекущийЗапись 时间戳（用于重建时продолжитьЗапись)
    private Set<String> currentEnabledCameras = null;  // ТекущийВключить Камера集合
    private int rebuildAttemptCount = 0;  // 重建попытка раз数（0=首 раз, 1=重建MediaRecorder, 2+=回退Codec)
    private static final int CODEC_FALLBACK_THRESHOLD = 2;  // 触发 Codec 回退 阈值
    private volatile boolean isRebuildingRecording = false;  //  否Выполняется 重建Запись（防止多Камера并发触发)
    private StatusCallback statusCallback;
    private PreviewSizeCallback previewSizeCallback;
    private volatile int sessionConfiguredCount = 0;
    private volatile int expectedSessionCount = 0;
    private Runnable pendingRecordingStart = null;
    private android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable sessionTimeoutRunnable = null;
    private final Object sessionLock = new Object();  // 用于同步 session конфигурация计数
    
    // 按Камера维度跟踪конфигурацияСтатус（解决таймаут强制Запуск问题)
    private final Map<String, Boolean> cameraSessionReady = new LinkedHashMap<>();
    private final Map<String, Boolean> cameraRecordingActive = new LinkedHashMap<>();
    private RecordingStatusCallback recordingStatusCallback;

    public interface StatusCallback {
        void onCameraStatusUpdate(String cameraId, String status);
    }

    public interface PreviewSizeCallback {
        void onPreviewSizeChosen(String cameraKey, String cameraId, Size previewSize);
    }
    
    public interface SegmentSwitchCallback {
        void onSegmentSwitch(int newSegmentIndex);
    }

    /**
     * 损坏Файл删除回调
     */
    public interface CorruptedFilesCallback {
        void onCorruptedFilesDeleted(List<String> deletedFiles);
    }

    /**
     * Codec 回退Уведомление回调
     */
    public interface CodecFallbackCallback {
        void onCodecFallback();
    }
    
    /**
     * ЗаписьСтатус回调（用于Уведомление部分КамераЗаписьОшибка)
     */
    public interface RecordingStatusCallback {
        /**
         * 当部分КамераНачать записьУспешно，Частичная ошибка时调用
         * @param activeCameras УспешноНачать запись Камера key 集合
         * @param failedCameras Начать записьОшибка Камера key 集合
         */
        void onPartialRecordingStart(Set<String> activeCameras, Set<String> failedCameras);
    }

    /**
     * 首 раз数据写入回调
     * 用于УведомлениеВнешнееЗапись真正Вкл始（有数据写入)，可以Вкл始计时
     */
    public interface FirstDataWrittenCallback {
        /**
         * 当任一Камера首 разУспешно写入数据时调用（只Уведомление一 раз)
         */
        void onFirstDataWritten();
    }

    /**
     * Запись时间戳обновление回调
     * 当 Watchdog 触发重建Запись时，时间戳会改变，необходимоУведомлениеВнешнееобновление
     */
    public interface TimestampUpdateCallback {
        /**
         * 当Запись时间戳обновление时调用（通常  Watchdog 重建后)
         * @param newTimestamp 新 Запись时间戳
         */
        void onTimestampUpdated(String newTimestamp);
    }

    public MultiCameraManager(Context context) {
        this.context = context;
    }

    /**
     * 统一 分时间戳提供者
     * 确保 短时间内（3 сек.)所有КамераПолучение до 相同 时间戳
     * 解决多 кам.Камера分切换时因Запуск时机不同导致时间戳差1 сек. 问题
     */
    private final VideoRecorder.SegmentTimestampProvider segmentTimestampProvider = 
            new VideoRecorder.SegmentTimestampProvider() {
        @Override
        public String getSegmentTimestamp() {
            synchronized (timestampLock) {
                long now = System.currentTimeMillis();
                // Если 缓存 时间戳仍 действует期内，返回缓存值
                if (cachedSegmentTimestamp != null && 
                    (now - timestampGeneratedTime) < TIMESTAMP_CACHE_DURATION_MS) {
                    AppLog.d(TAG, "Using cached segment timestamp: " + cachedSegmentTimestamp + 
                            " (age: " + (now - timestampGeneratedTime) + "ms)");
                    return cachedSegmentTimestamp;
                }
                // 生成新 时间戳
                cachedSegmentTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(new Date());
                timestampGeneratedTime = now;
                AppLog.d(TAG, "Generated new segment timestamp: " + cachedSegmentTimestamp);
                return cachedSegmentTimestamp;
            }
        }
    };

    /**
     * очистка缓存 分时间戳
     *  Вкл始新 Запись时调用，确保использование新 时间戳
     */
    private void clearCachedSegmentTimestamp() {
        synchronized (timestampLock) {
            cachedSegmentTimestamp = null;
            timestampGeneratedTime = 0;
        }
    }
    
    private SegmentSwitchCallback segmentSwitchCallback;
    private CorruptedFilesCallback corruptedFilesCallback;
    private CodecFallbackCallback codecFallbackCallback;
    private FirstDataWrittenCallback firstDataWrittenCallback;
    private TimestampUpdateCallback timestampUpdateCallback;
    private boolean hasNotifiedFirstDataWritten = false;  //  否Уведомление首 раз写入（每 разЗапись只Уведомление一 раз)

    public void setStatusCallback(StatusCallback callback) {
        this.statusCallback = callback;
    }

    public void setPreviewSizeCallback(PreviewSizeCallback callback) {
        this.previewSizeCallback = callback;
    }
    
    public void setCorruptedFilesCallback(CorruptedFilesCallback callback) {
        this.corruptedFilesCallback = callback;
    }
    
    public void setRecordingStatusCallback(RecordingStatusCallback callback) {
        this.recordingStatusCallback = callback;
    }
    
    public void setSegmentSwitchCallback(SegmentSwitchCallback callback) {
        this.segmentSwitchCallback = callback;
    }

    /**
     * Настройкивременно分时长覆盖值
     * 用于Удалённая запись时Отключить分（Настройки为Запись时长+余量)
     * @param durationMs 分时长（毫 сек.)，0表示использованиеконфигурация值
     */
    public void setSegmentDurationOverride(long durationMs) {
        this.overrideSegmentDurationMs = durationMs;
        AppLog.d(TAG, "Segment duration override set to: " + (durationMs > 0 ? (durationMs / 1000) + " seconds" : "disabled"));
    }

    /**
     * очистка分时长覆盖，Восстановлениеиспользованиеконфигурация值
     */
    public void clearSegmentDurationOverride() {
        this.overrideSegmentDurationMs = 0;
        AppLog.d(TAG, "Segment duration override cleared, using config value");
    }

    public void setCodecFallbackCallback(CodecFallbackCallback callback) {
        this.codecFallbackCallback = callback;
    }

    public void setFirstDataWrittenCallback(FirstDataWrittenCallback callback) {
        this.firstDataWrittenCallback = callback;
    }

    public void setTimestampUpdateCallback(TimestampUpdateCallback callback) {
        this.timestampUpdateCallback = callback;
    }

    public void setMaxOpenCameras(int maxOpenCameras) {
        this.maxOpenCameras = Math.max(1, maxOpenCameras);
    }

    /**
     * Настройки单一输出режим（用于не поддерживается多 кам.输出 车机平台，если L6/L7)
     *  此режим，Запись时只использование MediaRecorder Surface，不использование TextureView Surface
     * 这会导致Запись期间预览冻结，但能确保Записьнормально工作
     * 
     * @param enabled true 表示Включить单一输出режим
     * @deprecated использование setCodecRecordingMode(true) 代替，它использование OpenGL 渲染方案
     */
    @Deprecated
    public void setSingleOutputMode(boolean enabled) {
        AppLog.d(TAG, "Setting single output mode: " + (enabled ? "ENABLED" : "DISABLED"));
        for (SingleCamera camera : cameras.values()) {
            camera.setSingleOutputMode(enabled);
        }
    }

    /**
     * Настройки软编码Записьрежим（用于 L6/L7 等не поддерживается MediaRecorder 直接Запись 车机平台)
     *  此режим，использование OpenGL 渲染 + MediaCodec 编码 + MediaMuxer 写入Файл
     * 
     * 优点：
     * - 预览保持流畅，不会冻结
     * - 不依赖硬件  MediaRecorder Surface  поддержка
     * 
     * @param enabled true 表示Включить软编码Записьрежим
     */
    public void setCodecRecordingMode(boolean enabled) {
        this.useCodecRecording = enabled;
        AppLog.d(TAG, "Codec recording mode: " + (enabled ? "ENABLED" : "DISABLED"));
    }

    /**
     * проверка 否использование软编码Записьрежим
     */
    public boolean isCodecRecordingMode() {
        return useCodecRecording;
    }

    /**
     * Получение指定Позиция Камера实例
     * @param position Позиция（front/back/left/right)
     * @return SingleCamera实例，Если не существует则返回null
     */
    public SingleCamera getCamera(String position) {
        return cameras.get(position);
    }

    public void updatePreviewTextureViews(TextureView frontView,
                                          TextureView backView,
                                          TextureView leftView,
                                          TextureView rightView) {
        updatePreviewTextureView("front", frontView);
        updatePreviewTextureView("back", backView);
        updatePreviewTextureView("left", leftView);
        updatePreviewTextureView("right", rightView);
    }

    public void onPreviewTextureDestroyed(String cameraKey) {
        SingleCamera camera = cameras.get(cameraKey);
        if (camera == null) {
            return;
        }
        camera.setTextureView(null);
        camera.clearPreviewSurface();
        camera.recreateSession();
    }

    private void updatePreviewTextureView(String cameraKey, TextureView view) {
        SingleCamera camera = cameras.get(cameraKey);
        if (camera == null) {
            return;
        }
        camera.setTextureView(view);
        camera.recreateSession();
    }

    /**
     * вручную触发所有有 previewSize  Камера  PreviewSizeCallback。
     * 用于Фоновый режиминициализация（CameraManagerHolder)复用场景：
     * Камера  BlindSpotService открыть并确定预览尺寸，
     * 但 MainActivity  回调（Поворот 变换等)此时尚Не 注册。
     *   MainActivity 注册回调后调用此方法，补偿缺失 回调触发。
     */
    public void firePreviewSizeCallbacks() {
        if (previewSizeCallback == null) return;
        for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
            SingleCamera camera = entry.getValue();
            Size size = camera.getPreviewSize();
            if (size != null) {
                previewSizeCallback.onPreviewSizeChosen(entry.getKey(), camera.getCameraId(), size);
            }
        }
    }

    /**
     * инициализацияКамера
     * Поддерживаемые null 参数以适配不同数量 Камераконфигурация（1/2/4)
     */
    public void initCameras(String frontId, TextureView frontView,
                           String backId, TextureView backView,
                           String leftId, TextureView leftView,
                           String rightId, TextureView rightView) {

        // 清空до Камера实例
        cameras.clear();
        
        // 根据参数创建Камера实例（Поддерживаемые null TextureView 用于Фоновый режиминициализация)
        if (frontId != null) {
            SingleCamera frontCamera = new SingleCamera(context, frontId, frontView);
            frontCamera.setCameraPosition("front");
            cameras.put("front", frontCamera);
            AppLog.d(TAG, "инициализация前Камера: ID=" + frontId);
        }

        if (backId != null) {
            SingleCamera backCamera = new SingleCamera(context, backId, backView);
            backCamera.setCameraPosition("back");
            cameras.put("back", backCamera);
            AppLog.d(TAG, "инициализацияЗадняя камера: ID=" + backId);
        }

        if (leftId != null) {
            SingleCamera leftCamera = new SingleCamera(context, leftId, leftView);
            leftCamera.setCameraPosition("left");
            cameras.put("left", leftCamera);
            AppLog.d(TAG, "инициализацияЛевая камера: ID=" + leftId);
        }

        if (rightId != null) {
            SingleCamera rightCamera = new SingleCamera(context, rightId, rightView);
            rightCamera.setCameraPosition("right");
            cameras.put("right", rightCamera);
            AppLog.d(TAG, "инициализацияПравая камера: ID=" + rightId);
        }
        
        AppLog.d(TAG, "Всего инициализация " + cameras.size() + " камер(ы)");

        // 检测重复 cameraId，只让Первый шт.实例成为主实例
        Set<String> primaryIds = new HashSet<>();
        for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
            SingleCamera camera = entry.getValue();
            String id = camera.getCameraId();
            
            if (primaryIds.add(id)) {
                // Первый раз遇 до 这 шт.ID，设为主实例
                camera.setPrimaryInstance(true);
                AppLog.d(TAG, "Camera " + id + " at position " + entry.getKey() + " set as PRIMARY");
            } else {
                // 重复 ID，设为 от 属实例
                camera.setPrimaryInstance(false);
                AppLog.d(TAG, "Camera " + id + " at position " + entry.getKey() + " set as SECONDARY (sharing with primary)");
            }
        }

        // 为每 шт.КамераНастройки回调
        CameraCallback callback = new CameraCallback() {
            @Override
            public void onCameraOpened(String cameraId) {
                AppLog.d(TAG, "Callback: Camera " + cameraId + " opened");
                if (statusCallback != null) {
                    statusCallback.onCameraStatusUpdate(cameraId, "открыть");
                }
            }

            @Override
            public void onCameraConfigured(String cameraId) {
                AppLog.d(TAG, "Callback: Camera " + cameraId + " configured");
                if (statusCallback != null) {
                    statusCallback.onCameraStatusUpdate(cameraId, "Предпросмотр запущен");
                }

                // проверка 否有Запись器Выполняется ожидание会话重新конфигурация（分切换)
                for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
                    if (entry.getValue().getCameraId().equals(cameraId)) {
                        String key = entry.getKey();
                        VideoRecorder recorder = recorders.get(key);

                        if (recorder != null && recorder.isWaitingForSessionReconfiguration()) {
                            AppLog.d(TAG, "Camera " + cameraId + " session reconfigured, starting next segment recording");
                            recorder.clearWaitingForSessionReconfiguration();
                            recorder.startRecording();
                        }
                        break;
                    }
                }

                // проверка 否所有会话всеконфигурациязавершение（线程安全处理)
                synchronized (sessionLock) {
                    if (expectedSessionCount > 0) {
                        // 找 до  应 Камера key 并标记为绪
                        String cameraKey = null;
                        for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
                            if (entry.getValue().getCameraId().equals(cameraId)) {
                                cameraKey = entry.getKey();
                                break;
                            }
                        }
                        if (cameraKey != null) {
                            Boolean wasReady = cameraSessionReady.get(cameraKey);
                            if (wasReady != null && wasReady) {
                                AppLog.d(TAG, "Camera " + cameraKey + " (id=" + cameraId + ") session already marked ready, skipping count");
                            } else {
                                cameraSessionReady.put(cameraKey, true);
                                sessionConfiguredCount++;
                                AppLog.d(TAG, "Camera " + cameraKey + " (id=" + cameraId + ") session marked as ready");
                                AppLog.d(TAG, "Session configured: " + sessionConfiguredCount + "/" + expectedSessionCount);
                            }
                        }

                        if (sessionConfiguredCount >= expectedSessionCount) {
                            // 所有会话всеконфигурациязавершение，выполнение待处理 ЗаписьЗапуск
                            final Runnable recordingTask = pendingRecordingStart;
                            if (recordingTask != null) {
                                AppLog.d(TAG, "All sessions configured, starting recording...");
                                // Отменатаймаутзадача
                                if (sessionTimeoutRunnable != null) {
                                    mainHandler.removeCallbacks(sessionTimeoutRunnable);
                                    sessionTimeoutRunnable = null;
                                }
                                pendingRecordingStart = null;
                                sessionConfiguredCount = 0;
                                expectedSessionCount = 0;
                                // 延迟 300ms 再Начать запись，让 Camera Session 稳定
                                // 某些车机设备необходимо这 шт.延迟才能正确将帧Отправка до  MediaRecorder Surface
                                mainHandler.postDelayed(recordingTask, 300);
                            }
                        }
                    }
                }
            }

            @Override
            public void onCameraClosed(String cameraId) {
                AppLog.d(TAG, "Callback: Camera " + cameraId + " closed");
                if (statusCallback != null) {
                    statusCallback.onCameraStatusUpdate(cameraId, "Закрыто");
                }
            }

            @Override
            public void onCameraError(String cameraId, int errorCode) {
                String errorMsg = getErrorMessage(errorCode);
                AppLog.e(TAG, "Callback: Camera " + cameraId + " error: " + errorCode + " - " + errorMsg);
                if (statusCallback != null) {
                    statusCallback.onCameraStatusUpdate(cameraId, "Ошибка: " + errorMsg);
                }

                // Если  ожидание会话конфигурация期间发生Ошибка，减少期望计数（线程安全处理)
                synchronized (sessionLock) {
                    if (expectedSessionCount > 0 && errorCode == -3) {
                        expectedSessionCount--;
                        AppLog.d(TAG, "Session configuration failed, adjusted expected count: " + sessionConfiguredCount + "/" + expectedSessionCount);

                        // проверка 否所有剩余会话всеконфигурациязавершение
                        if (sessionConfiguredCount >= expectedSessionCount && expectedSessionCount > 0) {
                            final Runnable recordingTask = pendingRecordingStart;
                            if (recordingTask != null) {
                                AppLog.d(TAG, "Remaining sessions configured, starting recording...");
                                // Отменатаймаутзадача
                                if (sessionTimeoutRunnable != null) {
                                    mainHandler.removeCallbacks(sessionTimeoutRunnable);
                                    sessionTimeoutRunnable = null;
                                }
                                pendingRecordingStart = null;
                                // 延迟 300ms 再Начать запись，让 Camera Session 稳定
                                mainHandler.postDelayed(recordingTask, 300);
                            }
                            sessionConfiguredCount = 0;
                            expectedSessionCount = 0;
                        } else if (expectedSessionCount == 0) {
                            // 所有会话всеОшибка
                            AppLog.e(TAG, "All sessions failed to configure");
                            if (sessionTimeoutRunnable != null) {
                                mainHandler.removeCallbacks(sessionTimeoutRunnable);
                                sessionTimeoutRunnable = null;
                            }
                            sessionConfiguredCount = 0;
                            expectedSessionCount = 0;
                            pendingRecordingStart = null;
                        }
                    }
                }
            }

            @Override
            public void onPreviewSizeChosen(String cameraId, Size previewSize) {
                AppLog.d(TAG, "Callback: Camera " + cameraId + " preview size: " + previewSize);
                // 找 до  应  camera key
                for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
                    if (entry.getValue().getCameraId().equals(cameraId)) {
                        if (previewSizeCallback != null) {
                            previewSizeCallback.onPreviewSizeChosen(entry.getKey(), cameraId, previewSize);
                        }
                    }
                }
            }
        };

        // 为инициализация КамераНастройки回调
        for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
            entry.getValue().setCallback(callback);
        }

        // 为инициализация Камера创建Запись器实例
        recorders.clear();
        if (frontId != null && cameras.containsKey("front")) {
            VideoRecorder recorder = new VideoRecorder(frontId);
            recorder.setTimestampProvider(segmentTimestampProvider);  // Настройки统一时间戳提供者
            recorders.put("front", recorder);
        }
        if (backId != null && cameras.containsKey("back")) {
            VideoRecorder recorder = new VideoRecorder(backId);
            recorder.setTimestampProvider(segmentTimestampProvider);  // Настройки统一时间戳提供者
            recorders.put("back", recorder);
        }
        if (leftId != null && cameras.containsKey("left")) {
            VideoRecorder recorder = new VideoRecorder(leftId);
            recorder.setTimestampProvider(segmentTimestampProvider);  // Настройки统一时间戳提供者
            recorders.put("left", recorder);
        }
        if (rightId != null && cameras.containsKey("right")) {
            VideoRecorder recorder = new VideoRecorder(rightId);
            recorder.setTimestampProvider(segmentTimestampProvider);  // Настройки统一时间戳提供者
            recorders.put("right", recorder);
        }

        // 为每 шт.Запись器Настройки回调
        RecordCallback recordCallback = new RecordCallback() {
            @Override
            public void onRecordStart(String cameraId) {
                AppLog.d(TAG, "Recording started for camera " + cameraId);
            }

            @Override
            public void onRecordStop(String cameraId) {
                AppLog.d(TAG, "Recording stopped for camera " + cameraId);
            }

            @Override
            public void onRecordError(String cameraId, String error) {
                AppLog.e(TAG, "Recording error for camera " + cameraId + ": " + error);
            }

            @Override
            public void onPrepareSegmentSwitch(String cameraId, int currentSegmentIndex) {
                AppLog.d(TAG, "Prepare segment switch for camera " + cameraId + " (current segment: " + currentSegmentIndex + ")");
                // 找 до  应  camera 并切换 до только预览режим
                // использование优化  switchToPreviewOnlyMode() 方法：预览продолжить流畅，只Остановка к Запись Surface Отправка帧
                for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
                    if (entry.getValue().getCameraId().equals(cameraId)) {
                        SingleCamera camera = entry.getValue();
                        // 优先использование新 только预览режим（保持预览不卡顿)
                        boolean success = camera.switchToPreviewOnlyMode();
                        AppLog.d(TAG, "Camera " + cameraId + " switched to preview-only mode: " + (success ? "success" : "fallback to pause"));
                        break;
                    }
                }
            }

            @Override
            public void onSegmentSwitch(String cameraId, int newSegmentIndex, String completedFilePath) {
                AppLog.d(TAG, "Segment switch for camera " + cameraId + " to segment " + newSegmentIndex);
                // 找 до  应  camera key  и  camera
                for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
                    if (entry.getValue().getCameraId().equals(cameraId)) {
                        String key = entry.getKey();
                        SingleCamera camera = entry.getValue();
                        VideoRecorder recorder = recorders.get(key);

                        if (camera != null && recorder != null) {
                            // Если использование转写入，将一 шт.分 Файл传输 до 最终каталог
                            if (useRelayWrite && finalSaveDir != null && newSegmentIndex > 0 && completedFilePath != null) {
                                // 传输завершение Файл（由回调提供确切Путь，避免传输Выполняется Запись 新Файл)
                                scheduleRelayTransfer(completedFilePath);
                            }
                            
                            // обновлениеЗапись Surface 并重新创建会话（MediaRecorder режим)
                            camera.setRecordSurface(recorder.getSurface(), false);
                            camera.recreateSession();
                            AppLog.d(TAG, "Recreated session for camera " + cameraId + " after segment switch");
                        }
                        
                        // Уведомление分切换回调（只Уведомление一 раз，Первый шт.触发 Камера会Уведомление)
                        if (segmentSwitchCallback != null && newSegmentIndex > lastNotifiedSegmentIndex) {
                            lastNotifiedSegmentIndex = newSegmentIndex;
                            segmentSwitchCallback.onSegmentSwitch(newSegmentIndex);
                        }
                        break;
                    }
                }
            }

            @Override
            public void onCorruptedFilesDeleted(String cameraId, List<String> deletedFiles) {
                if (deletedFiles != null && !deletedFiles.isEmpty()) {
                    AppLog.w(TAG, "Corrupted files deleted for camera " + cameraId + ": " + deletedFiles.size() + " file(s)");
                    for (String file : deletedFiles) {
                        AppLog.d(TAG, "  Deleted: " + file);
                    }
                    // Уведомление MainActivity 显示弹窗
                    if (corruptedFilesCallback != null) {
                        mainHandler.post(() -> corruptedFilesCallback.onCorruptedFilesDeleted(deletedFiles));
                    }
                }
            }

            @Override
            public void onRecordingRebuildRequested(String cameraId, String reason) {
                AppLog.e(TAG, "Recording rebuild requested for camera " + cameraId + ", reason: " + reason);
                handleRecordingRebuildRequest(cameraId, reason);
            }

            @Override
            public void onFirstDataWritten(String cameraId) {
                AppLog.d(TAG, "First data written for camera " + cameraId);
                // 只 Первый шт.Камера首 раз写入时УведомлениеВнешнее（每 разЗапись只Уведомление一 раз)
                if (!hasNotifiedFirstDataWritten && firstDataWrittenCallback != null) {
                    hasNotifiedFirstDataWritten = true;
                    AppLog.d(TAG, "Notifying external: first data written, recording truly started");
                    mainHandler.post(() -> firstDataWrittenCallback.onFirstDataWritten());
                }
            }
        };

        // 为创建 Запись器Настройки回调
        for (Map.Entry<String, VideoRecorder> entry : recorders.entrySet()) {
            entry.getValue().setCallback(recordCallback);
        }

        AppLog.d(TAG, "Cameras initialized");
    }

    /**
     * ПолучениеОшибкаИнформация描述
     */
    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case 1: // ERROR_CAMERA_IN_USE
                return "КамераВыполняется  использование";
            case 2: // ERROR_MAX_CAMERAS_IN_USE
                return "Достигнуто максимальное количество камер";
            case 3: // ERROR_CAMERA_DISABLED
                return "Камера Отключить";
            case 4: // ERROR_CAMERA_DEVICE
                return "Ошибка камеры (недостаточно ресурсов?)";
            case 5: // ERROR_CAMERA_SERVICE
                return "КамераСервисОшибка";
            case -1:
                return "доступОшибка";
            case -2:
                return "Недостаточно разрешений";
            case -3:
                return "Ошибка конфигурации сессии";
            case -4:
                return "Камера отключена (ресурсы исчерпаны)";
            default:
                return "НеизвестноОшибка(" + errorCode + ")";
        }
    }

    /**
     * открыть所有Камера
     */
    public void openAllCameras() {
        AppLog.d(TAG, "Opening all cameras...");
        repairSuppressed = false;

        activeCameraKeys.clear();
        int opened = 0;
        Set<String> openedIds = new HashSet<>();
        for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
            if (opened >= maxOpenCameras) {
                break;
            }
            SingleCamera camera = entry.getValue();
            String id = camera.getCameraId();
            if (!openedIds.add(id)) {
                continue;
            }
            activeCameraKeys.add(entry.getKey());
            camera.openCamera();
            opened++;
        }

        AppLog.d(TAG, "Requested open cameras: " + activeCameraKeys);
    }

    /**
     * Закрыто所有Камера
     */
    public void closeAllCameras() {
        repairSuppressed = true;
        for (SingleCamera camera : cameras.values()) {
            camera.closeCamera();
        }
        AppLog.d(TAG, "All cameras closed (repair suppressed)");
    }

    /**
     * Начать запись所有Камера（автоматически生成时间戳)
     */
    public boolean startRecording() {
        // 生成统一 时间戳
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return startRecording(timestamp);
    }

    /**
     * Начать запись所有Камера（использование指定 时间戳)
     * @param timestamp 统一 时间戳，用于所有Камера Файл命名
     */
    public boolean startRecording(String timestamp) {
        if (isRecording) {
            AppLog.w(TAG, "Already recording");
            return false;
        }

        // очистка缓存 分时间戳，Вкл始新 Запись周期
        clearCachedSegmentTimestamp();

        // 根据режимВыбратьЗапись方式
        if (useCodecRecording) {
            return startCodecRecording(timestamp, null);
        } else {
            return startMediaRecorderRecording(timestamp, null);
        }
    }

    /**
     * Начать запись指定 Камера（использование指定 时间戳 и Камера列表)
     * @param timestamp 统一 时间戳，用于所有Камера Файл命名
     * @param enabledCameras 要Запись КамераПозиция集合（если ["front", "back"])，为 null 时Запись所有Камера
     */
    public boolean startRecording(String timestamp, Set<String> enabledCameras) {
        if (isRecording) {
            AppLog.w(TAG, "Already recording");
            return false;
        }

        // очистка缓存 分时间戳，Вкл始新 Запись周期
        clearCachedSegmentTimestamp();

        // 根据режимВыбратьЗапись方式
        if (useCodecRecording) {
            return startCodecRecording(timestamp, enabledCameras);
        } else {
            return startMediaRecorderRecording(timestamp, enabledCameras);
        }
    }

    /**
     * использование MediaRecorder Начать запись（Стандартрежим)
     * @param timestamp 时间戳
     * @param enabledCameras 要Запись КамераПозиция集合，为 null 时Запись所有Камера
     */
    private boolean startMediaRecorderRecording(String timestamp, Set<String> enabledCameras) {
        AppLog.d(TAG, "Starting MediaRecorder recording with timestamp: " + timestamp);

        // Сброс首 раз写入Уведомление标志（每 разЗапись只Уведомление一 раз)
        hasNotifiedFirstDataWritten = false;

        // 记录ТекущийЗапись参数（用于 Watchdog 重建)
        currentRecordingTimestamp = timestamp;
        currentEnabledCameras = enabledCameras;

        // проверка 否использование转写入режим
        AppConfig appConfig = new AppConfig(context);
        useRelayWrite = appConfig.shouldUseRelayWrite();
        
        // ПолучениеЗаписькаталог（可能 временнокаталогили最终каталог)
        File saveDir = StorageHelper.getRecordingDir(context);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        
        // Если использование转写入，记录最终каталог
        if (useRelayWrite) {
            finalSaveDir = StorageHelper.getFinalVideoDir(context);
            if (!finalSaveDir.exists()) {
                finalSaveDir.mkdirs();
            }
            AppLog.d(TAG, "Relay write mode: recording to " + saveDir.getAbsolutePath() + 
                    ", will transfer to " + finalSaveDir.getAbsolutePath());
        } else {
            finalSaveDir = null;
        }

        List<String> allKeys = getActiveCameraKeys();
        if (allKeys.isEmpty()) {
            AppLog.e(TAG, "No active cameras for recording");
            return false;
        }

        // Если 指定Камера列表，过滤 keys
        final List<String> keys;
        if (enabledCameras != null && !enabledCameras.isEmpty()) {
            List<String> filteredKeys = new ArrayList<>();
            for (String key : allKeys) {
                if (enabledCameras.contains(key)) {
                    filteredKeys.add(key);
                }
            }
            keys = filteredKeys;
            AppLog.d(TAG, "Filtered recording cameras: " + keys);
        } else {
            keys = allKeys;
        }

        if (keys.isEmpty()) {
            AppLog.e(TAG, "No enabled cameras for recording after filtering");
            return false;
        }

        // ПолучениеЗаписьконфигурация（использование面创建  appConfig)
        // Если 有временно覆盖值（Удалённая запись)，использование覆盖值；否则использованиеконфигурация值
        long segmentDurationMs = (overrideSegmentDurationMs > 0) 
                ? overrideSegmentDurationMs 
                : appConfig.getSegmentDurationMs();
        if (overrideSegmentDurationMs > 0) {
            AppLog.d(TAG, "Segment duration (override for remote recording): " + (segmentDurationMs / 1000) + " seconds");
        } else {
            AppLog.d(TAG, "Segment duration: " + (segmentDurationMs / 1000) + " seconds (" + appConfig.getSegmentDurationMinutes() + " minutes)");
        }
        
        // Получение帧率конфигурация（根据Уровень частоты кадровНастройки计算)
        int targetFrameRate = appConfig.getActualFrameRate(30);  // 假设硬件поддержка30fps
        AppLog.d(TAG, "Target frame rate: " + targetFrameRate + " fps (level: " + appConfig.getFramerateLevel() + ")");

        // Первый步：准备所有 MediaRecorder（但不Запуск)
        // использование每 шт.Камера 实际预览Разрешение，而不 硬编码 值
        boolean prepareSuccess = true;
        for (String key : keys) {
            SingleCamera camera = cameras.get(key);
            VideoRecorder recorder = recorders.get(key);
            if (camera == null || recorder == null) {
                continue;
            }
            
            // ПолучениеКамера 实际预览Разрешение
            Size previewSize = camera.getPreviewSize();
            if (previewSize == null) {
                AppLog.e(TAG, "Camera " + key + " preview size not available, using fallback 1280x720");
                previewSize = new Size(1280, 720);  // 回退 до 常见Разрешение
            }
            
            // 计算码率（基于Разрешение и 帧率)
            int bitrate = appConfig.getActualBitrate(
                    previewSize.getWidth(), 
                    previewSize.getHeight(), 
                    targetFrameRate);
            
            // НастройкиЗапись参数
            recorder.setSegmentDuration(segmentDurationMs);
            recorder.setVideoBitrate(bitrate);
            recorder.setVideoFrameRate(targetFrameRate);
            // 注：максимум编码Разрешение限制использование VideoRecorder ВнутреннееПо умолчанию值（4096x4096)
            
            AppLog.d(TAG, "Recording params for " + key + ": " + 
                    previewSize.getWidth() + "x" + previewSize.getHeight() + 
                    " @ " + targetFrameRate + "fps, " + AppConfig.formatBitrate(bitrate));
            
            // 所有Камераиспользование统一 时间戳： д.期_时间_КамераПозиция.mp4
            String path = new File(saveDir, timestamp + "_" + key + ".mp4").getAbsolutePath();
            // 只准备 MediaRecorder，Получение Surface，использование预览 实际Разрешение
            AppLog.d(TAG, "Preparing recording for " + key + " with size: " + previewSize.getWidth() + "x" + previewSize.getHeight());
            if (!recorder.prepareRecording(path, previewSize.getWidth(), previewSize.getHeight())) {
                prepareSuccess = false;
                break;
            }
        }

        if (!prepareSuccess) {
            AppLog.e(TAG, "Failed to prepare recording");
            // Очистка 准备 Запись器
            for (String key : keys) {
                VideoRecorder recorder = recorders.get(key);
                if (recorder != null) {
                    recorder.release();
                }
            }
            return false;
        }

        // Второй步：将Запись Surface 添加 до Камера会话并重新创建会话
        synchronized (sessionLock) {
            sessionConfiguredCount = 0;
            expectedSessionCount = keys.size();
            // инициализация每 шт.Камера конфигурацияСтатус跟踪
            cameraSessionReady.clear();
            cameraRecordingActive.clear();
        }

        for (String key : keys) {
            SingleCamera camera = cameras.get(key);
            VideoRecorder recorder = recorders.get(key);
            if (camera == null || recorder == null) {
                continue;
            }
            camera.setRecordSurface(recorder.getSurface(), false);  // MediaRecorder режим
            camera.recreateSession();
        }

        // Третий步：Настройки待处理 ЗаписьЗапускзадача（将  executeRecordingStart 替代)
        final List<String> recordingKeys = new ArrayList<>(keys);
        pendingRecordingStart = () -> executeRecordingStart(recordingKeys, false);

        // Настройкитаймаут机制：Если  3  сек.内没有所有会话конфигурациязавершение，只Запуск绪 Камера
        sessionTimeoutRunnable = () -> {
            AppLog.w(TAG, "Session configuration timeout after 3 seconds");
            synchronized (sessionLock) {
                // 标记Не 响应 Камера为Ошибка
                for (String key : recordingKeys) {
                    if (!cameraSessionReady.containsKey(key)) {
                        cameraSessionReady.put(key, false);
                        AppLog.w(TAG, "Camera " + key + " session not configured in time");
                    }
                }
                // выполнениеЗаписьЗапуск（только绪 Камера)
                executeRecordingStart(recordingKeys, true);
            }
        };
        mainHandler.postDelayed(sessionTimeoutRunnable, 3000);

        return true;
    }
    
    /**
     * выполнениеЗаписьЗапуск（толькоЗапуск绪 Камера)
     * @param keys 要Начать запись Камера key 列表
     * @param fromTimeout  否  от таймаут触发 
     */
    private void executeRecordingStart(List<String> keys, boolean fromTimeout) {
        executeRecordingStart(keys, fromTimeout, 0, false);
    }

    private void executeRecordingStart(List<String> keys, boolean fromTimeout, int stableAttempt, boolean forcedReopen) {
        if (!fromTimeout) {
            long now = System.currentTimeMillis();
            List<String> unstable = getUnstableCameras(keys, now);
            if (!unstable.isEmpty()) {
                if (stableAttempt < MAX_STABLE_WAIT_ATTEMPTS) {
                    AppLog.w(TAG, "Waiting for stable frames before recording, attempt " + (stableAttempt + 1) +
                            "/" + MAX_STABLE_WAIT_ATTEMPTS + ", unstable=" + unstable);
                    mainHandler.postDelayed(() -> executeRecordingStart(keys, false, stableAttempt + 1, forcedReopen), STABLE_WAIT_INTERVAL_MS);
                    return;
                }
                if (!forcedReopen) {
                    AppLog.w(TAG, "Frames still unstable after wait, forcing reopen all cameras once: " + unstable);
                    forceReopenAllCameras();
                    mainHandler.postDelayed(() -> executeRecordingStart(keys, false, 0, true), 500);
                    return;
                }
                AppLog.w(TAG, "Frames still unstable after force reopen, starting recording with stable subset: " + unstable);
                fromTimeout = true;
            }
        }

        Set<String> activeCameras = new HashSet<>();
        Set<String> failedCameras = new HashSet<>();
        
        AppLog.d(TAG, "Executing recording start for " + keys.size() + " cameras" + 
                (fromTimeout ? " (from timeout)" : ""));
        
        for (String key : keys) {
            // проверкаКамера会话 否绪
            Boolean ready = cameraSessionReady.get(key);
            if (ready == null || !ready) {
                // 会话Не 绪
                if (fromTimeout) {
                    failedCameras.add(key);
                    AppLog.w(TAG, "Camera " + key + " session not ready, skipping");
                }
                continue;
            }

            if (fromTimeout && !isFrameStable(key, System.currentTimeMillis())) {
                failedCameras.add(key);
                AppLog.w(TAG, "Camera " + key + " frame not stable, skipping");
                continue;
            }
            
            VideoRecorder recorder = recorders.get(key);
            if (recorder != null) {
                if (recorder.startRecording()) {
                    cameraRecordingActive.put(key, true);
                    activeCameras.add(key);
                } else {
                    cameraRecordingActive.put(key, false);
                    failedCameras.add(key);
                    AppLog.e(TAG, "Failed to start recording for " + key);
                }
            } else {
                failedCameras.add(key);
            }
        }
        
        if (!activeCameras.isEmpty()) {
            isRecording = true;
            lastNotifiedSegmentIndex = -1;
            AppLog.d(TAG, activeCameras.size() + " camera(s) started recording successfully: " + activeCameras);
            
            // Если 有Ошибка Камера，Уведомление层
            if (!failedCameras.isEmpty() && recordingStatusCallback != null) {
                AppLog.w(TAG, failedCameras.size() + " camera(s) failed to start: " + failedCameras);
                recordingStatusCallback.onPartialRecordingStart(activeCameras, failedCameras);
            }
        } else {
            AppLog.e(TAG, "All cameras failed to start recording");
            isRecording = false;
            // Очистка 所有Запись器
            for (String key : keys) {
                VideoRecorder recorder = recorders.get(key);
                if (recorder != null) {
                    recorder.release();
                }
            }
            // Уведомление层完全Ошибка
            if (statusCallback != null) {
                statusCallback.onCameraStatusUpdate("all", "recording_failed");
            }
        }
        
        // Очистка Статус
        pendingRecordingStart = null;
        sessionConfiguredCount = 0;
        expectedSessionCount = 0;
    }

    private boolean isFrameStable(String key, long nowMs) {
        SingleCamera camera = cameras.get(key);
        if (camera == null) {
            return false;
        }
        long last = camera.getLastFrameTimestampMs();
        return last > 0 && (nowMs - last) <= RECORDING_STABLE_FRAME_MAX_AGE_MS;
    }

    private List<String> getUnstableCameras(List<String> keys, long nowMs) {
        List<String> unstable = new ArrayList<>();
        for (String key : keys) {
            Boolean ready = cameraSessionReady.get(key);
            if (ready == null || !ready) {
                unstable.add(key);
                continue;
            }
            if (!isFrameStable(key, nowMs)) {
                unstable.add(key);
            }
        }
        return unstable;
    }

    /**
     * использование软编码Начать запись（L6/L7 режим)
     * использование OpenGL 渲染 + MediaCodec 编码 + MediaMuxer 写入
     * @param timestamp 时间戳
     * @param enabledCameras 要Запись КамераПозиция集合，为 null 时Запись所有Камера
     */
    private boolean startCodecRecording(String timestamp, Set<String> enabledCameras) {
        AppLog.d(TAG, "Starting CODEC recording with timestamp: " + timestamp);

        // Сброс首 раз写入Уведомление标志（每 разЗапись只Уведомление一 раз)
        hasNotifiedFirstDataWritten = false;

        // проверка 否использование转写入режим
        AppConfig appConfig = new AppConfig(context);
        useRelayWrite = appConfig.shouldUseRelayWrite();
        
        // ПолучениеЗаписькаталог（可能 временнокаталогили最终каталог)
        File saveDir = StorageHelper.getRecordingDir(context);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        
        // Если использование转写入，记录最终каталог
        if (useRelayWrite) {
            finalSaveDir = StorageHelper.getFinalVideoDir(context);
            if (!finalSaveDir.exists()) {
                finalSaveDir.mkdirs();
            }
            AppLog.d(TAG, "Codec relay write mode: recording to " + saveDir.getAbsolutePath() + 
                    ", will transfer to " + finalSaveDir.getAbsolutePath());
        } else {
            finalSaveDir = null;
        }

        List<String> allKeys = getActiveCameraKeys();
        if (allKeys.isEmpty()) {
            AppLog.e(TAG, "No active cameras for codec recording");
            return false;
        }

        // Если 指定Камера列表，过滤 keys
        final List<String> keys;
        if (enabledCameras != null && !enabledCameras.isEmpty()) {
            List<String> filteredKeys = new ArrayList<>();
            for (String key : allKeys) {
                if (enabledCameras.contains(key)) {
                    filteredKeys.add(key);
                }
            }
            keys = filteredKeys;
            AppLog.d(TAG, "Filtered codec recording cameras: " + keys);
        } else {
            keys = allKeys;
        }

        if (keys.isEmpty()) {
            AppLog.e(TAG, "No enabled cameras for codec recording after filtering");
            return false;
        }

        // ПолучениеЗаписьконфигурация（использование面创建  appConfig)
        // Если 有временно覆盖值（Удалённая запись)，использование覆盖值；否则использованиеконфигурация值
        long segmentDurationMs = (overrideSegmentDurationMs > 0) 
                ? overrideSegmentDurationMs 
                : appConfig.getSegmentDurationMs();
        if (overrideSegmentDurationMs > 0) {
            AppLog.d(TAG, "Codec segment duration (override for remote recording): " + (segmentDurationMs / 1000) + " seconds");
        } else {
            AppLog.d(TAG, "Codec segment duration: " + (segmentDurationMs / 1000) + " seconds (" + appConfig.getSegmentDurationMinutes() + " minutes)");
        }
        
        // Получение帧率конфигурация（根据Уровень частоты кадровНастройки计算)
        int targetFrameRate = appConfig.getActualFrameRate(30);
        AppLog.d(TAG, "Codec target frame rate: " + targetFrameRate + " fps (level: " + appConfig.getFramerateLevel() + ")");

        // Очистка до 软编码Запись器
        for (CodecVideoRecorder recorder : codecRecorders.values()) {
            recorder.release();
        }
        codecRecorders.clear();

        // 为每 шт.Камера创建软编码Запись器并准备
        boolean prepareSuccess = true;
        for (String key : keys) {
            SingleCamera camera = cameras.get(key);
            if (camera == null) {
                continue;
            }

            // ПолучениеКамера 实际预览Разрешение
            Size previewSize = camera.getPreviewSize();
            if (previewSize == null) {
                AppLog.e(TAG, "Camera " + key + " preview size not available, using fallback 1280x800");
                previewSize = new Size(1280, 800);
            }
            
            // максимум编码Разрешение限制（H.264 编码器硬件限制，固定值)
            final int MAX_ENCODE_SIZE = 4096;
            
            // 计算调整后 编码Разрешение（防止超大РазрешениеКамера导致编码Ошибка)
            int encodeWidth = previewSize.getWidth();
            int encodeHeight = previewSize.getHeight();
            if (encodeWidth > MAX_ENCODE_SIZE || encodeHeight > MAX_ENCODE_SIZE) {
                float widthRatio = (float) MAX_ENCODE_SIZE / encodeWidth;
                float heightRatio = (float) MAX_ENCODE_SIZE / encodeHeight;
                float scaleFactor = Math.min(widthRatio, heightRatio);
                encodeWidth = ((int) (encodeWidth * scaleFactor) / 2) * 2;  // 确保 偶数
                encodeHeight = ((int) (encodeHeight * scaleFactor) / 2) * 2;
                if (encodeWidth < 2) encodeWidth = 2;
                if (encodeHeight < 2) encodeHeight = 2;
                AppLog.w(TAG, "Camera " + key + " codec resolution adjusted: " + 
                        previewSize.getWidth() + "x" + previewSize.getHeight() + " -> " + 
                        encodeWidth + "x" + encodeHeight + " (max: " + MAX_ENCODE_SIZE + ")");
            }
            
            // 计算码率（基于调整后 Разрешение и 帧率)
            int bitrate = appConfig.getActualBitrate(encodeWidth, encodeHeight, targetFrameRate);

            // 创建软编码Запись器（использование调整后 Разрешение)
            CodecVideoRecorder codecRecorder = new CodecVideoRecorder(
                    camera.getCameraId(), 
                    encodeWidth, 
                    encodeHeight
            );

            // Настройки统一时间戳提供者（确保多 кам.Камера分切换时использование相同时间戳)
            codecRecorder.setTimestampProvider(segmentTimestampProvider);

            // НастройкиЗапись参数
            codecRecorder.setSegmentDuration(segmentDurationMs);
            codecRecorder.setBitRate(bitrate);
            codecRecorder.setFrameRate(targetFrameRate);
            
            AppLog.d(TAG, "Codec recording params for " + key + ": " + 
                    encodeWidth + "x" + encodeHeight + 
                    " @ " + targetFrameRate + "fps, " + AppConfig.formatBitrate(bitrate));

            // Настройки时间水印（ от конфигурация读取，использование方法Вкл头创建  appConfig)
            codecRecorder.setWatermarkEnabled(appConfig.isTimestampWatermarkEnabled());

            // Настройки回调
            codecRecorder.setCallback(new RecordCallback() {
                @Override
                public void onRecordStart(String cameraId) {
                    AppLog.d(TAG, "Codec recording started for camera " + cameraId);
                }

                @Override
                public void onRecordStop(String cameraId) {
                    AppLog.d(TAG, "Codec recording stopped for camera " + cameraId);
                }

                @Override
                public void onRecordError(String cameraId, String error) {
                    AppLog.e(TAG, "Codec recording error for camera " + cameraId + ": " + error);
                }

                @Override
                public void onPrepareSegmentSwitch(String cameraId, int currentSegmentIndex) {
                    AppLog.d(TAG, "Codec prepare segment switch for camera " + cameraId + " (current segment: " + currentSegmentIndex + ")");
                    // 软编码Запись器использование独立  SurfaceTexture，不необходимоПауза Camera CaptureSession
                    // 但为一致性，我们记录 д.志
                }

                @Override
                public void onSegmentSwitch(String cameraId, int newSegmentIndex, String completedFilePath) {
                    AppLog.d(TAG, "Codec segment switch for camera " + cameraId + " to segment " + newSegmentIndex);
                    
                    // Если использование转写入，将一 шт.分 Файл传输 до 最终каталог
                    if (useRelayWrite && finalSaveDir != null && newSegmentIndex > 0 && completedFilePath != null) {
                        // 传输завершение Файл（由回调提供确切Путь，避免传输Выполняется Запись 新Файл)
                        scheduleRelayTransfer(completedFilePath);
                    }
                    
                    // Уведомление分切换回调（只Уведомление一 раз，Первый шт.触发 Камера会Уведомление)
                    if (segmentSwitchCallback != null && newSegmentIndex > lastNotifiedSegmentIndex) {
                        lastNotifiedSegmentIndex = newSegmentIndex;
                        segmentSwitchCallback.onSegmentSwitch(newSegmentIndex);
                    }
                }

                @Override
                public void onCorruptedFilesDeleted(String cameraId, List<String> deletedFiles) {
                    if (deletedFiles != null && !deletedFiles.isEmpty()) {
                        AppLog.w(TAG, "Corrupted files deleted for codec camera " + cameraId + ": " + deletedFiles.size() + " file(s)");
                        for (String file : deletedFiles) {
                            AppLog.d(TAG, "  Deleted: " + file);
                        }
                        // Уведомление MainActivity 显示弹窗
                        if (corruptedFilesCallback != null) {
                            mainHandler.post(() -> corruptedFilesCallback.onCorruptedFilesDeleted(deletedFiles));
                        }
                    }
                }

                @Override
                public void onRecordingRebuildRequested(String cameraId, String reason) {
                    // CodecVideoRecorder 通常不会触发此回调，但为接口完整性实现
                    AppLog.e(TAG, "Codec recording rebuild requested for camera " + cameraId + ", reason: " + reason);
                    // Codec режим不необходимо回退，记录 д.志т.е.可
                }

                @Override
                public void onFirstDataWritten(String cameraId) {
                    AppLog.d(TAG, "Codec first data written for camera " + cameraId);
                    // 只 Первый шт.Камера首 раз写入时УведомлениеВнешнее（每 разЗапись只Уведомление一 раз)
                    if (!hasNotifiedFirstDataWritten && firstDataWrittenCallback != null) {
                        hasNotifiedFirstDataWritten = true;
                        AppLog.d(TAG, "Notifying external: first data written, recording truly started");
                        mainHandler.post(() -> firstDataWrittenCallback.onFirstDataWritten());
                    }
                }
            });

            // 准备Запись
            String path = new File(saveDir, timestamp + "_" + key + ".mp4").getAbsolutePath();
            AppLog.d(TAG, "Preparing codec recording for " + key + " with size: " + previewSize.getWidth() + "x" + previewSize.getHeight());

            android.graphics.SurfaceTexture surfaceTexture = codecRecorder.prepareRecording(path);
            if (surfaceTexture == null) {
                AppLog.e(TAG, "Failed to prepare codec recording for " + key);
                prepareSuccess = false;
                break;
            }

            // 将 SurfaceTexture Настройки  Camera（通过 Surface)
            android.view.Surface recordSurface = new android.view.Surface(surfaceTexture);
            camera.setRecordSurface(recordSurface, true);  // Codec режим

            codecRecorders.put(key, codecRecorder);
        }

        if (!prepareSuccess) {
            AppLog.e(TAG, "Failed to prepare codec recording");
            // Очистка 准备 Запись器
            for (CodecVideoRecorder recorder : codecRecorders.values()) {
                recorder.release();
            }
            codecRecorders.clear();
            return false;
        }

        // 重新创建Камера会话
        synchronized (sessionLock) {
            sessionConfiguredCount = 0;
            expectedSessionCount = keys.size();
            cameraSessionReady.clear();
            cameraRecordingActive.clear();
        }

        for (String key : keys) {
            SingleCamera camera = cameras.get(key);
            if (camera != null) {
                camera.recreateSession();
            }
        }

        final List<String> recordingKeys = new ArrayList<>(keys);
        pendingRecordingStart = () -> executeCodecRecordingStart(recordingKeys, 0, false);

        // Настройкитаймаут机制
        sessionTimeoutRunnable = () -> {
            AppLog.w(TAG, "Session configuration timeout, starting codec recording with available cameras");
            synchronized (sessionLock) {
                final Runnable recordingTask = pendingRecordingStart;
                if (recordingTask != null) {
                    pendingRecordingStart = null;
                    recordingTask.run();
                }
                sessionConfiguredCount = 0;
                expectedSessionCount = 0;
            }
        };
        mainHandler.postDelayed(sessionTimeoutRunnable, 3000);

        return true;
    }

    private void executeCodecRecordingStart(List<String> keys, int stableAttempt, boolean forcedReopen) {
        AppLog.d(TAG, "Attempting to start codec recording...");
        if (isRecording) {
            AppLog.w(TAG, "Codec recording already active, skipping duplicate start");
            synchronized (sessionLock) {
                pendingRecordingStart = null;
                sessionConfiguredCount = 0;
                expectedSessionCount = 0;
                cameraSessionReady.clear();
                cameraRecordingActive.clear();
            }
            if (sessionTimeoutRunnable != null) {
                mainHandler.removeCallbacks(sessionTimeoutRunnable);
                sessionTimeoutRunnable = null;
            }
            return;
        }

        long now = System.currentTimeMillis();
        List<String> unstable = getUnstableCameras(keys, now);
        if (!unstable.isEmpty()) {
            if (stableAttempt < MAX_STABLE_WAIT_ATTEMPTS) {
                AppLog.w(TAG, "Waiting for stable frames before codec recording, attempt " + (stableAttempt + 1) +
                        "/" + MAX_STABLE_WAIT_ATTEMPTS + ", unstable=" + unstable);
                mainHandler.postDelayed(() -> executeCodecRecordingStart(keys, stableAttempt + 1, forcedReopen), STABLE_WAIT_INTERVAL_MS);
                return;
            }
            if (!forcedReopen) {
                AppLog.w(TAG, "Codec frames still unstable after wait, forcing reopen all cameras once: " + unstable);
                forceReopenAllCameras();
                mainHandler.postDelayed(() -> executeCodecRecordingStart(keys, 0, true), 500);
                return;
            }
            AppLog.w(TAG, "Codec frames still unstable after force reopen, starting with stable subset: " + unstable);
        }

        boolean anyActive = false;
        int activeCount = 0;

        for (String key : keys) {
            Boolean ready = cameraSessionReady.get(key);
            if (ready == null || !ready) {
                continue;
            }
            if (!unstable.isEmpty() && !isFrameStable(key, System.currentTimeMillis())) {
                continue;
            }
            CodecVideoRecorder codecRecorder = codecRecorders.get(key);
            if (codecRecorder == null) {
                continue;
            }
            if (codecRecorder.isRecording()) {
                anyActive = true;
                activeCount++;
                continue;
            }
            if (codecRecorder.startRecording() || codecRecorder.isRecording()) {
                anyActive = true;
                activeCount++;
            } else {
                AppLog.e(TAG, "Failed to start codec recording for " + key);
            }
        }

        if (anyActive) {
            lastNotifiedSegmentIndex = -1;
            isRecording = true;
            AppLog.d(TAG, activeCount + " camera(s) started codec recording successfully");
        } else {
            AppLog.e(TAG, "Failed to start codec recording on all cameras");
            isRecording = false;
            for (CodecVideoRecorder recorder : codecRecorders.values()) {
                recorder.release();
            }
            codecRecorders.clear();
        }

        synchronized (sessionLock) {
            pendingRecordingStart = null;
            sessionConfiguredCount = 0;
            expectedSessionCount = 0;
            cameraSessionReady.clear();
            cameraRecordingActive.clear();
        }
        if (sessionTimeoutRunnable != null) {
            mainHandler.removeCallbacks(sessionTimeoutRunnable);
            sessionTimeoutRunnable = null;
        }
    }

    /**
     * Остановить запись所有Камера
     */
    public void stopRecording() {
        stopRecording(false);
    }

    /**
     * Остановить запись所有Камера
     * @param skipRelayTransfer  否跳过автоматически传输（用于Удалённая запись，Загрузка завершена后再传输)
     */
    public void stopRecording(boolean skipRelayTransfer) {
        AppLog.d(TAG, "stopRecording called, isRecording=" + isRecording + ", useCodecRecording=" + useCodecRecording + ", skipRelayTransfer=" + skipRelayTransfer);

        // Очистка 待处理 ЗаписьЗапускзадача и 会话计数器（线程安全处理)
        synchronized (sessionLock) {
            if (pendingRecordingStart != null) {
                AppLog.d(TAG, "Cancelling pending recording start");
                pendingRecordingStart = null;
            }

            // Сброс会话计数器
            sessionConfiguredCount = 0;
            expectedSessionCount = 0;
        }

        // Очистка таймаутзадача
        if (sessionTimeoutRunnable != null) {
            mainHandler.removeCallbacks(sessionTimeoutRunnable);
            sessionTimeoutRunnable = null;
        }

        List<String> keys = getActiveCameraKeys();

        if (!isRecording) {
            AppLog.w(TAG, "Not recording, but cleaning up anyway");
            // т.е.使不 ЗаписьСтатус，такжепопыткаОчистка Запись器
            for (String key : keys) {
                VideoRecorder recorder = recorders.get(key);
                if (recorder != null) {
                    recorder.release();
                }
                CodecVideoRecorder codecRecorder = codecRecorders.get(key);
                if (codecRecorder != null) {
                    codecRecorder.release();
                }
            }
            codecRecorders.clear();
            return;
        }

        // Остановка软编码Запись
        if (!codecRecorders.isEmpty()) {
            AppLog.d(TAG, "Stopping codec recorders...");
            for (String key : keys) {
                CodecVideoRecorder codecRecorder = codecRecorders.get(key);
                if (codecRecorder != null && codecRecorder.isRecording()) {
                    codecRecorder.stopRecording();
                }
            }
            // 释放软编码Запись器
            for (CodecVideoRecorder recorder : codecRecorders.values()) {
                recorder.release();
            }
            codecRecorders.clear();
        }

        // Остановка MediaRecorder Запись
        for (String key : keys) {
            VideoRecorder recorder = recorders.get(key);
            if (recorder != null && recorder.isRecording()) {
                recorder.stopRecording();
            }
        }

        // Очистка Камера会话
        for (String key : keys) {
            SingleCamera camera = cameras.get(key);
            if (camera != null) {
                camera.clearRecordSurface();
                camera.recreateSession();
            }
        }

        // Если использование转写入，将временнокаталог 所有Файл传输 до 最终каталог
        // Если  skipRelayTransfer=true（Удалённая запись)，则跳过автоматически传输，由传逻辑负责传输
        if (useRelayWrite && finalSaveDir != null && !skipRelayTransfer) {
            AppLog.d(TAG, "Scheduling relay transfer for remaining files...");
            // Сохранить引用，因为 finalSaveDir 会 延迟выполнение前 清空
            final File savedFinalDir = finalSaveDir;
            
            // 【重要】立т.е.收集Текущийнеобходимо传输 Файл列表，避免延迟выполнение时误传输新创建 Файл
            File tempDir = new File(context.getCacheDir(), FileTransferManager.TEMP_VIDEO_DIR);
            final File[] filesToTransfer;
            if (tempDir.exists()) {
                filesToTransfer = tempDir.listFiles((dir, name) -> name.endsWith(".mp4"));
            } else {
                filesToTransfer = null;
            }
            
            // 延迟一点выполнение，确保Файл经写入завершение
            mainHandler.postDelayed(() -> {
                transferSpecificTempFiles(savedFinalDir, filesToTransfer);
            }, 500);
        } else if (useRelayWrite && skipRelayTransfer) {
            AppLog.d(TAG, "Skipping relay transfer (will be handled after upload)");
        }

        isRecording = false;
        useRelayWrite = false;
        finalSaveDir = null;
        
        // Очистка  Watchdog 回退Статус
        currentRecordingTimestamp = null;
        currentEnabledCameras = null;
        rebuildAttemptCount = 0;
        isRebuildingRecording = false;  // Сброс重建标志
        
        AppLog.d(TAG, "All cameras stopped recording");
    }

    /**
     * 处理Запись重建求（Watchdog 触发)
     * 
     * 重建策略：
     * 1. Первый раз触发：попытка重建 MediaRecorder（不切换режим)
     * 2. Второй раз触发：Если Записьрежим为"автоматически"，则切换 до  Codec режим
     * 3.   Codec режимили非автоматическирежим：不再处理
     * 
     * 注意：多 шт.Камера可能同时触发此方法，необходимо防重入保护
     * 
     * @param cameraId 触发重建 相机ID
     * @param reason 重建原因
     */
    private void handleRecordingRebuildRequest(String cameraId, String reason) {
        // 【Выкл键】防重入保护：多 шт.Камера可能同时触发 Watchdog
        // 只处理Первый шт.触发 求，忽略后续 
        synchronized (this) {
            if (isRebuildingRecording) {
                AppLog.w(TAG, "Recording rebuild already in progress, ignoring request from camera " + cameraId);
                return;
            }
            isRebuildingRecording = true;
        }
        
        rebuildAttemptCount++;
        AppLog.w(TAG, "Handling recording rebuild request from camera " + cameraId + 
                ", reason: " + reason + ", attempt: " + rebuildAttemptCount);
        
        // Если 经  Codec режим，则不再处理
        if (useCodecRecording) {
            AppLog.w(TAG, "Already using Codec recording, no further fallback available");
            isRebuildingRecording = false;
            return;
        }
        
        // СохранитьТекущийЗапись参数
        final String savedTimestamp = currentRecordingTimestamp;
        final Set<String> savedEnabledCameras = currentEnabledCameras;
        
        if (savedTimestamp == null) {
            AppLog.w(TAG, "No recording timestamp saved, cannot rebuild");
            isRebuildingRecording = false;
            return;
        }
        
        // ОстановкаТекущийЗапись（不Очистка Статус)
        stopRecordingForRebuild();
        
        // 注意：不автоматическиочисткаотладка标志，让用户通过 UI вручную控制
        // отладкарежим作为持久ВклВыкл，直 до 用户вручнуюЗакрыто
        
        // проверка 否необходимо回退 до  Codec
        if (rebuildAttemptCount >= CODEC_FALLBACK_THRESHOLD) {
            // 达 до 阈值，проверка 否可以回退 до  Codec
            AppConfig appConfig = new AppConfig(context);
            String recordingMode = appConfig.getRecordingMode();
            
            if (AppConfig.RECORDING_MODE_AUTO.equals(recordingMode)) {
                // автоматическирежим：切换 до  Codec Запись
                AppLog.w(TAG, "Rebuild attempt " + rebuildAttemptCount + " failed, switching to Codec mode...");
                
                mainHandler.postDelayed(() -> {
                    try {
                        // 生成新 时间戳（避免Файл名冲突)
                        String newTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                        
                        AppLog.d(TAG, "Restarting recording with Codec mode, new timestamp: " + newTimestamp);
                        useCodecRecording = true;  // 切换 до  Codec режим
                        startCodecRecording(newTimestamp, savedEnabledCameras);
                        
                        // УведомлениеВнешнее时间戳обновление（用于Удалённая запись查找Файл)
                        if (timestampUpdateCallback != null) {
                            timestampUpdateCallback.onTimestampUpdated(newTimestamp);
                        }
                        
                        // УведомлениеВнешнее发生 Codec 回退
                        if (codecFallbackCallback != null) {
                            codecFallbackCallback.onCodecFallback();
                        }
                    } finally {
                        isRebuildingRecording = false;  // 重建завершение
                    }
                }, 500);
            } else {
                // 非автоматическирежим，只能再 разпопытка MediaRecorder
                AppLog.w(TAG, "Recording mode is '" + recordingMode + "' (not auto), retrying MediaRecorder...");
                
                mainHandler.postDelayed(() -> {
                    try {
                        String newTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                        AppLog.d(TAG, "Retrying MediaRecorder recording, new timestamp: " + newTimestamp);
                        startMediaRecorderRecording(newTimestamp, savedEnabledCameras);
                        
                        // УведомлениеВнешнее时间戳обновление（用于Удалённая запись查找Файл)
                        if (timestampUpdateCallback != null) {
                            timestampUpdateCallback.onTimestampUpdated(newTimestamp);
                        }
                    } finally {
                        isRebuildingRecording = false;  // 重建завершение
                    }
                }, 500);
            }
        } else {
            // Не 达 до 阈值，先попытка重建 MediaRecorder
            AppLog.w(TAG, "Rebuild attempt " + rebuildAttemptCount + ", retrying MediaRecorder first...");
            
            mainHandler.postDelayed(() -> {
                try {
                    // 生成新 时间戳（避免Файл名冲突)
                    String newTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    
                    AppLog.d(TAG, "Restarting recording with MediaRecorder, new timestamp: " + newTimestamp);
                    startMediaRecorderRecording(newTimestamp, savedEnabledCameras);
                    
                    // УведомлениеВнешнее时间戳обновление（用于Удалённая запись查找Файл)
                    if (timestampUpdateCallback != null) {
                        timestampUpdateCallback.onTimestampUpdated(newTimestamp);
                    }
                } finally {
                    isRebuildingRecording = false;  // 重建завершение
                }
            }, 500);
        }
    }
    
    /**
     * 为重建Остановить запись（不Очистка  Watchdog Статус)
     * использование reset() 而不  release()，以便保留 Handler/Thread 供重建时использование
     */
    private void stopRecordingForRebuild() {
        AppLog.d(TAG, "Stopping recording for rebuild...");
        
        List<String> keys = getActiveCameraKeys();
        
        // Сброс MediaRecorder Запись器（保留 Handler/Thread)
        for (String key : keys) {
            VideoRecorder recorder = recorders.get(key);
            if (recorder != null) {
                recorder.reset();  // Сброс而不 释放，保留 Handler/Thread
            }
        }
        
        // Очистка Камера会话
        for (String key : keys) {
            SingleCamera camera = cameras.get(key);
            if (camera != null) {
                camera.clearRecordSurface();
                camera.recreateSession();
            }
        }
        
        isRecording = false;
    }
    
    /**
     * 调度将指定 завершениеФайл传输 до 最终каталог
     * @param completedFilePath завершениеЗапись Файл完整Путь
     */
    private void scheduleRelayTransfer(String completedFilePath) {
        if (finalSaveDir == null || completedFilePath == null) {
            return;
        }
        
        File tempFile = new File(completedFilePath);
        if (!tempFile.exists()) {
            AppLog.w(TAG, "Completed file does not exist: " + completedFilePath);
            return;
        }
        
        // проверкаФайл大小，避免传输空Файлили损坏Файл
        if (tempFile.length() < 1024) {
            AppLog.w(TAG, "Completed file too small, skipping transfer: " + completedFilePath + " (" + tempFile.length() + " bytes)");
            return;
        }
        
        File targetFile = new File(finalSaveDir, tempFile.getName());
        
        AppLog.d(TAG, "Scheduling relay transfer: " + tempFile.getName() + 
                " -> " + targetFile.getAbsolutePath());
        
        FileTransferManager transferManager = FileTransferManager.getInstance(context);
        transferManager.addTransferTask(tempFile, targetFile, 
                new FileTransferManager.TransferCallback() {
            @Override
            public void onTransferComplete(File sourceFile, File targetFile) {
                AppLog.d(TAG, "Relay transfer complete: " + targetFile.getName());
            }
            
            @Override
            public void onTransferFailed(File sourceFile, File targetFile, String error) {
                AppLog.e(TAG, "Relay transfer failed: " + sourceFile.getName() + " - " + error);
            }
        });
    }
    
    /**
     * 将временнокаталог 所有ВидеоФайл传输 до 最终каталог
     * @param targetDir 目标каталог
     */
    private void transferAllTempFiles(File targetDir) {
        if (targetDir == null) {
            AppLog.w(TAG, "Target directory is null, skipping transfer");
            return;
        }
        
        File tempDir = new File(context.getCacheDir(), FileTransferManager.TEMP_VIDEO_DIR);
        if (!tempDir.exists()) {
            AppLog.d(TAG, "Temp directory does not exist");
            return;
        }
        
        File[] files = tempDir.listFiles((dir, name) -> name.endsWith(".mp4"));
        transferSpecificTempFiles(targetDir, files);
    }
    
    /**
     * 将指定 временноВидеоФайл传输 до 最终каталог
     * 【重要】此方法只传输预先指定 Файл列表，避免传输 调用后新创建 Файл
     * @param targetDir 目标каталог
     * @param files 要传输 Файл列表（ 调用前收集)
     */
    private void transferSpecificTempFiles(File targetDir, File[] files) {
        if (targetDir == null) {
            AppLog.w(TAG, "Target directory is null, skipping transfer");
            return;
        }
        
        if (files == null || files.length == 0) {
            AppLog.d(TAG, "No temp files to transfer");
            return;
        }
        
        AppLog.d(TAG, "Transferring " + files.length + " temp file(s) to " + targetDir.getAbsolutePath());
        
        FileTransferManager transferManager = FileTransferManager.getInstance(context);
        
        for (File tempFile : files) {
            // проверкаФайл 否仍然существует（可能经 删除или移动)
            if (!tempFile.exists()) {
                AppLog.d(TAG, "Skipping non-existent file: " + tempFile.getName());
                continue;
            }
            
            // 跳过空Файл（可能 Выполняется  ДругоеЗаписьиспользование 新Файл)
            if (tempFile.length() == 0) {
                AppLog.d(TAG, "Skipping empty file (may be in use): " + tempFile.getName());
                continue;
            }
            
            File targetFile = new File(targetDir, tempFile.getName());
            
            transferManager.addTransferTask(tempFile, targetFile, 
                    new FileTransferManager.TransferCallback() {
                @Override
                public void onTransferComplete(File sourceFile, File targetFile) {
                    AppLog.d(TAG, "Transfer complete: " + targetFile.getName());
                }
                
                @Override
                public void onTransferFailed(File sourceFile, File targetFile, String error) {
                    AppLog.e(TAG, "Transfer failed: " + sourceFile.getName() + " - " + error);
                }
            });
        }
    }

    /**
     * 释放所有资源
     * 添加完善 Очистка 逻辑 и аномалия保护
     */
    public void release() {
        AppLog.d(TAG, "Releasing MultiCameraManager resources");
        
        try {
            // 1. 首先Очистка 所有待выполнение  Handler задача（防止内存泄漏)
            if (mainHandler != null) {
                mainHandler.removeCallbacksAndMessages(null);
            }
            
            // 2. Очистка таймаут Runnable 引用
            if (sessionTimeoutRunnable != null) {
                sessionTimeoutRunnable = null;
            }
            pendingRecordingStart = null;
            
            // 3. Сброс会话конфигурация计数器
            synchronized (sessionLock) {
                sessionConfiguredCount = 0;
                expectedSessionCount = 0;
            }
            
            // 4. Остановить запись
            try {
                stopRecording();
            } catch (Exception e) {
                AppLog.e(TAG, "Error stopping recording during release", e);
            }
            
            // 5. Закрыто所有Камера
            try {
                closeAllCameras();
            } catch (Exception e) {
                AppLog.e(TAG, "Error closing cameras during release", e);
            }
            
            // 6. 释放 VideoRecorder
            for (VideoRecorder recorder : recorders.values()) {
                try {
                    recorder.release();
                } catch (Exception e) {
                    AppLog.e(TAG, "Error releasing VideoRecorder", e);
                }
            }
            
            // 7. 释放 CodecVideoRecorder
            for (CodecVideoRecorder codecRecorder : codecRecorders.values()) {
                try {
                    codecRecorder.release();
                } catch (Exception e) {
                    AppLog.e(TAG, "Error releasing CodecVideoRecorder", e);
                }
            }
            
        } catch (Exception e) {
            AppLog.e(TAG, "Unexpected error during release", e);
        } finally {
            // 8. Очистка 集合（确保выполнение)
            cameras.clear();
            recorders.clear();
            codecRecorders.clear();
            isRecording = false;
            isRebuildingRecording = false;
            currentRecordingTimestamp = null;
            currentEnabledCameras = null;
            AppLog.d(TAG, "All resources released");
        }
    }

    /**
     * После release() карта cameras пуста — метод позволяет проверить, что экземпляр уже недействителен.
     */
    public boolean isReleased() {
        return cameras.isEmpty();
    }

    /**
     *  否Выполняется Запись
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Фото（所有活动 Камера顺序Фото，避免资源耗尽)
     */
    /**
     * Фото（所有Камера，автоматически生成时间戳)
     */
    public void takePicture() {
        // 生成统一 时间戳
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        takePicture(timestamp);
    }

    /**
     * Фото（所有Камера，использование指定 时间戳)
     * @param timestamp 统一 时间戳，用于所有Камера Файл命名
     */
    public void takePicture(String timestamp) {
        List<String> keys = getActiveCameraKeys();
        if (keys.isEmpty()) {
            AppLog.e(TAG, "No active cameras for taking picture");
            return;
        }

        AppLog.d(TAG, "Taking picture with " + keys.size() + " camera(s) using timestamp: " + timestamp);

        // 快速Фото，每 шт.Камера间隔300ms触发Фото，但СохранитьФайл时按顺序延迟1 сек.
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            final int captureDelay = i * 300;      // Фото触发延迟：300ms（快速抓拍画面)
            final int saveDelay = i * 1000;        // ФайлСохранить延迟：1 сек.（分散磁 дискI/O)

            mainHandler.postDelayed(() -> {
                SingleCamera camera = cameras.get(key);
                if (camera != null && camera.isConnected()) {
                    AppLog.d(TAG, "Taking picture with camera " + key);
                    camera.takePicture(timestamp, saveDelay);  // 传递统一时间戳 и Сохранить延迟
                } else {
                    AppLog.w(TAG, "Camera " + key + " not available for taking picture");
                }
            }, captureDelay);
        }
    }

    private List<String> getActiveCameraKeys() {
        if (!activeCameraKeys.isEmpty()) {
            return new ArrayList<>(activeCameraKeys);
        }
        List<String> keys = new ArrayList<>();
        int opened = 0;
        Set<String> openedIds = new HashSet<>();
        for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
            if (opened >= maxOpenCameras) {
                break;
            }
            SingleCamera camera = entry.getValue();
            String id = camera.getCameraId();
            if (!openedIds.add(id)) {
                continue;
            }
            keys.add(entry.getKey());
            opened++;
        }
        return keys;
    }

    /**
     * проверка 否有Подключено 相机
     */
    public boolean hasConnectedCameras() {
        for (SingleCamera camera : cameras.values()) {
            if (camera.isConnected()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ПолучениеПодключено 相机数量
     */
    public int getConnectedCameraCount() {
        int count = 0;
        for (SingleCamera camera : cameras.values()) {
            if (camera.isConnected()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 生命周期：Пауза所有Камера（App退 до Фоновый режим时调用)
     * 注意：Если Выполняется Запись，不应该调用此方法
     */
    public void pauseAllCamerasByLifecycle() {
        AppLog.d(TAG, "Pausing all cameras by lifecycle");
        for (SingleCamera camera : cameras.values()) {
            camera.pauseByLifecycle();
        }
    }

    /**
     * 生命周期：Восстановление所有Камера（App返回Передний план时调用)
     */
    public void resumeAllCamerasByLifecycle() {
        AppLog.d(TAG, "Resuming all cameras by lifecycle");
        for (SingleCamera camera : cameras.values()) {
            camera.resumeByLifecycle();
        }
    }

    /**
     * проверка并修复КамераПодключение（返回Передний план时调用)
     * Если 发现Камераотключено，автоматически重新открыть
     * @return необходимо重新открыть Камера数量
     */
    public int checkAndRepairCameras() {
        if (repairSuppressed) {
            return 0;
        }
        int disconnectedCount = 0;
        
        for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
            SingleCamera camera = entry.getValue();
            if (!camera.isConnected()) {
                disconnectedCount++;
                AppLog.d(TAG, "Camera " + entry.getKey() + " reconnecting...");
                camera.forceReopen();
            }
        }
        
        if (disconnectedCount > 0) {
            AppLog.d(TAG, disconnectedCount + " camera(s) reconnecting");
        }
        
        return disconnectedCount;
    }

    /**
     * 强制重新открыть所有Камера（用于 от Фоновый режим返回Передний план时)
     */
    public void forceReopenAllCameras() {
        AppLog.d(TAG, "Force reopening all cameras...");
        repairSuppressed = false;
        for (SingleCamera camera : cameras.values()) {
            camera.forceReopen();
        }
    }

    /**
     * Получение所有КамераТекущийиспользование РазрешениеИнформация
     * @return 格式化 РазрешениеИнформация字符串
     */
    /**
     * Получение所有Камера 实时отладкаИнформация（FPS + Разрешение)
     */
    public String getDebugStats() {
        StringBuilder sb = new StringBuilder();
        String[] order = {"front", "back", "left", "right"};
        String[] labels = {"П", "З", "Л", "Пр"};
        for (int i = 0; i < order.length; i++) {
            SingleCamera camera = cameras.get(order[i]);
            if (camera == null) continue;
            if (sb.length() > 0) sb.append("\n");
            android.util.Size previewSize = camera.getPreviewSize();
            String res = previewSize != null
                    ? previewSize.getWidth() + "×" + previewSize.getHeight()
                    : "-";
            float fps = camera.getCurrentFps();
            sb.append(labels[i]).append("(").append(camera.getCameraId()).append(") ");
            sb.append(String.format(java.util.Locale.US, "%.1f fps  ", fps));
            sb.append(res);
        }
        return sb.toString();
    }

    public String getCameraResolutionsInfo() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, SingleCamera> entry : cameras.entrySet()) {
            String key = entry.getKey();
            SingleCamera camera = entry.getValue();
            String cameraId = camera.getCameraId();
            Size previewSize = camera.getPreviewSize();
            
            if (sb.length() > 0) {
                sb.append("\n");
            }
            
            sb.append(key).append(" (Камера").append(cameraId).append("): ");
            if (previewSize != null) {
                sb.append(previewSize.getWidth()).append("×").append(previewSize.getHeight());
            } else {
                sb.append("Не инициализация");
            }
        }
        return sb.toString();
    }
}
