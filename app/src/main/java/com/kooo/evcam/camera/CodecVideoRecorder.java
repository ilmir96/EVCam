package com.kooo.evcam.camera;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;

import com.kooo.evcam.AppLog;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * использование MediaCodec + MediaMuxer 进行Видео编码 и Запись
 * 用于 L6/L7 等не поддерживается MediaRecorder 直接Запись 车机平台
 * 
 * 工作流程：
 * 1. 创建 MediaCodec 编码器，Получение其Ввести Surface
 * 2. использование EglSurfaceEncoder 将 Camera  帧渲染 до 编码器Ввести Surface
 * 3.  от  MediaCodec Получение编码后 数据
 * 4. 通过 MediaMuxer 写入 MP4 Файл
 */
public class CodecVideoRecorder {
    private static final String TAG = "CodecVideoRecorder";

    // 编码参数（常量)
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;  // H.264
    private static final int I_FRAME_INTERVAL = 1;  // I帧间隔（ сек.)
    
    // 编码参数（可конфигурация)
    private int frameRate = 30;       // По умолчанию 30fps
    private int bitRate = 3000000;    // По умолчанию 3Mbps

    private final String cameraId;
    private final int width;
    private final int height;

    // MediaCodec 相Выкл
    private MediaCodec encoder;
    private Surface encoderInputSurface;
    private MediaCodec.BufferInfo bufferInfo;

    // MediaMuxer 相Выкл
    private MediaMuxer muxer;
    private int videoTrackIndex = -1;
    private boolean muxerStarted = false;

    // EGL 渲染器
    private EglSurfaceEncoder eglEncoder;
    private SurfaceTexture inputSurfaceTexture;
    private int textureId;

    // 编码线程
    private HandlerThread encoderThread;
    private Handler encoderHandler;

    // Статус
    private final AtomicBoolean isRecording = new AtomicBoolean(false);  // использование AtomicBoolean 确保线程安全
    private volatile boolean isReleased = false;
    private String currentFilePath;
    
    // 缓存 Запись Surface，避免重复创建导致内存泄漏
    private Surface cachedRecordSurface = null;
    
    // 时间戳基准（用于计算相 时间戳，供Ввести端использование)
    private long firstFrameTimestampNs = -1;
    
    // 分Вкл始时间（用于计算 PTS，基于Система时间而非帧数)
    // 这样可以准确反映实际Запись时长，不受帧率波动影响
    private long segmentStartTimeNs = 0;
    
    // 编码器输出帧计数（только用于 д.志 и 统计，不再用于 PTS 计算)
    private long encodedOutputFrameCount = 0;

    // 分Запись相Выкл
    private long segmentDurationMs = 60000;  // 分时长，По умолчанию1 мин.，可通过 setSegmentDuration конфигурация
    private static final long SEGMENT_DURATION_COMPENSATION_MS = 0;  // 分时长补偿（H3修复后定时器更精确，不再необходимо补偿)
    private static final long MIN_VALID_FILE_SIZE = 10 * 1024;  // минимумдействуетФайл大小 10KB
    
    // использование独立 Фоновый режим线程处理分 и Файл I/O операция，避免阻塞主线程导致 ANR
    private HandlerThread segmentThread;
    private Handler segmentHandler;
    
    private Runnable segmentRunnable;
    private int segmentIndex = 0;
    private String saveDirectory;
    private String cameraPosition;
    private VideoRecorder.SegmentTimestampProvider timestampProvider;  // 分时间戳提供者（用于多 кам.同步)
    private long lastFileSize = 0;
    private static final long FILE_SIZE_CHECK_INTERVAL_MS = 5000;
    private static final long FIRST_CHECK_DELAY_MS = 500;  // 首 разпроверка延迟（更快检测首 раз写入)
    private Runnable fileSizeCheckRunnable;
    private long recordedFrameCount = 0;
    private List<String> recordedFilePaths = new ArrayList<>();  // 本 разЗапись 所有ФайлПуть
    
    // 首 раз写入检测（ и  VideoRecorder 保持一致)
    private static final long FIRST_WRITE_TIMEOUT_MS = 10000;  // 首 раз写入таймаут（10 сек.)
    private boolean hasFirstWrite = false;  //  否有首 раз写入
    private Runnable firstWriteTimeoutRunnable;  // 首 раз写入таймаутпроверказадача
    
    // 快速Восстановление机制
    private static final long RECOVERY_RETRY_INTERVAL_MS = 5000;  // Восстановление重试间隔：5 сек.
    private static final int MAX_RECOVERY_ATTEMPTS = 60;  // максимум重试 раз数（5 сек. × 60 = 5 мин.内重试)
    private int recoveryAttempts = 0;  // Текущий重试 раз数
    private Runnable recoveryRunnable;  // Восстановление重试задача

    // 编码器健康проверка
    private static final long ENCODER_HEALTH_CHECK_INTERVAL_MS = 3000;  // 健康проверка间隔：3 сек.
    private static final int MAX_FRAMES_WITHOUT_OUTPUT = 30;  // 无输出 максимум帧数阈值
    private long lastEncoderOutputTime = 0;  // 最后一 раз编码器输出时间
    private int framesWithoutEncoderOutput = 0;  // 无编码器输出 连续帧数
    private volatile boolean encoderHealthy = true;  // 编码器 否健康
    private Runnable healthCheckRunnable;  // 健康проверказадача

    // 回调
    private RecordCallback callback;

    // 时间水印Настройки
    private boolean watermarkEnabled = false;

    // 注意：帧同步变量移除，帧处理现 直接  onFrameAvailable 回调завершение

    public CodecVideoRecorder(String cameraId, int width, int height) {
        this.cameraId = cameraId;
        this.width = width;
        this.height = height;
        // 创建独立 Фоновый режим线程用于分处理 и Файл I/O операция
        segmentThread = new HandlerThread("CodecRecorder-Segment-" + cameraId);
        segmentThread.start();
        this.segmentHandler = new Handler(segmentThread.getLooper());
    }

    /**
     * Настройки 否Включить时间水印
     * @param enabled true 表示Включить水印
     */
    public void setWatermarkEnabled(boolean enabled) {
        this.watermarkEnabled = enabled;
        // Если  EGL 编码器инициализация，同步Настройки
        if (eglEncoder != null) {
            eglEncoder.setWatermarkEnabled(enabled);
        }
        AppLog.d(TAG, "Camera " + cameraId + " Watermark " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * проверка 否Включить时间水印
     */
    public boolean isWatermarkEnabled() {
        return watermarkEnabled;
    }

    public void setCallback(RecordCallback callback) {
        this.callback = callback;
    }

    /**
     * Настройки分时间戳提供者
     * 用于多 кам.Камера分切换时использование统一 时间戳，避免时间戳差1 сек.导致分 группОшибка
     * @param provider 时间戳提供者
     */
    public void setTimestampProvider(VideoRecorder.SegmentTimestampProvider provider) {
        this.timestampProvider = provider;
    }

    /**
     * Настройки分时长
     * @param durationMs 分时长（毫 сек.)
     */
    public void setSegmentDuration(long durationMs) {
        this.segmentDurationMs = durationMs;
        AppLog.d(TAG, "Camera " + cameraId + " segment duration set to " + (durationMs / 1000) + " seconds");
    }

    /**
     * Получение分时长（毫 сек.)
     */
    public long getSegmentDuration() {
        return segmentDurationMs;
    }

    /**
     * НастройкиЗапись码率
     * @param bitrate 码率（bps)
     */
    public void setBitRate(int bitrate) {
        this.bitRate = bitrate;
        AppLog.d(TAG, "Camera " + cameraId + " bitrate set to " + (bitrate / 1000) + " Kbps");
    }

    /**
     * НастройкиЗапись帧率
     * @param fps 帧率（fps)
     */
    public void setFrameRate(int fps) {
        this.frameRate = fps;
        AppLog.d(TAG, "Camera " + cameraId + " frame rate set to " + fps + " fps");
    }

    /**
     * ПолучениеТекущие настройки 码率
     */
    public int getBitRate() {
        return bitRate;
    }

    /**
     * ПолучениеТекущие настройки 帧率
     */
    public int getFrameRate() {
        return frameRate;
    }

    /**
     * 准备Запись
     * 
     * 警告：此方法содержит阻塞операция（CountDownLatch.await)，不建议 主线程调用
     * Если 必须 主线程调用，可能导致 ANR。建议 Фоновый режим线程调用илииспользование prepareRecordingAsync()
     * 
     * @param filePath 输出ФайлПуть
     * @return 用于 Camera 输出  SurfaceTexture
     */
    public SurfaceTexture prepareRecording(String filePath) {
        // проверка 否 主线程调用（可能导致 ANR)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AppLog.w(TAG, "Camera " + cameraId + " WARNING: prepareRecording() called on MAIN THREAD! " +
                    "This may cause ANR due to blocking operations. Consider using prepareRecordingAsync().");
        }
        
        if (isRecording.get()) {
            AppLog.w(TAG, "Camera " + cameraId + " is already recording");
            return inputSurfaceTexture;
        }

        AppLog.d(TAG, "Camera " + cameraId + " Preparing codec recording: " + width + "x" + height);

        // СохранитьЗапись参数
        this.currentFilePath = filePath;
        this.segmentIndex = 0;
        this.recordedFrameCount = 0;
        this.firstFrameTimestampNs = -1;  // Сброс时间戳基准
        this.encodedOutputFrameCount = 0;  // Сброс编码输出帧计数

        // Сброс健康проверкаСтатус
        this.encoderHealthy = true;
        this.framesWithoutEncoderOutput = 0;
        this.lastEncoderOutputTime = System.currentTimeMillis();

        // 清空并инициализация本 разЗапись Файл列表
        recordedFilePaths.clear();
        recordedFilePaths.add(filePath);

        //  от ФайлПуть提取Сохранитькаталог и КамераПозиция
        File file = new File(filePath);
        this.saveDirectory = file.getParent();
        String fileName = file.getName();
        int lastUnderscoreIndex = fileName.lastIndexOf('_');
        if (lastUnderscoreIndex > 0 && fileName.endsWith(".mp4")) {
            this.cameraPosition = fileName.substring(lastUnderscoreIndex + 1, fileName.length() - 4);
        } else {
            this.cameraPosition = "unknown";
        }

        try {
            // 创建编码线程
            encoderThread = new HandlerThread("Encoder-" + cameraId);
            encoderThread.start();
            encoderHandler = new Handler(encoderThread.getLooper());

            // 创建 MediaCodec 编码器
            createEncoder();

            // 创建 MediaMuxer
            createMuxer(filePath);

            //  编码线程инициализация EGL  и  SurfaceTexture（重要：必须 同一线程)
            // использование CountDownLatch ожиданиеинициализациязавершение
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final int[] resultTextureId = {0};
            final Exception[] initException = {null};

            encoderHandler.post(() -> {
                try {
                    // 创建 EGL 渲染器（ 编码线程)
                    eglEncoder = new EglSurfaceEncoder(cameraId, width, height);
                    resultTextureId[0] = eglEncoder.initialize(encoderInputSurface);
                    textureId = resultTextureId[0];

                    // 创建 SurfaceTexture 供 Camera 输出（ 编码线程，绑定 до  EGL context)
                    inputSurfaceTexture = new SurfaceTexture(textureId);
                    inputSurfaceTexture.setDefaultBufferSize(width, height);

                    // Настройки帧Доступно回调（ 编码线程)
                    // 直接 回调处理帧，避免 Handler 死锁
                    inputSurfaceTexture.setOnFrameAvailableListener(surfaceTexture -> {
                        if (isReleased) {
                            return;
                        }

                        try {
                            // Выкл键修复：т.е.使不 ЗаписьСтатус，также必须调用 updateTexImage() 消费帧
                            // 否则 SurfaceTexture 会保持 pending Статус，不再触发后续回调
                            // updateTexImage   drawFrame Внутреннее调用，这里单独处理非ЗаписьСтатус
                            if (!isRecording.get()) {
                                // 不 ЗаписьСтатус时，仍需消费帧以保持 SurfaceTexture нормально工作
                                if (eglEncoder != null && eglEncoder.isInitialized()) {
                                    eglEncoder.consumeFrame();  // 只消费帧，不编码
                                }
                                return;
                            }

                            // проверка编码器健康Статус，不健康时只消费帧不编码
                            if (!encoderHealthy) {
                                if (eglEncoder != null && eglEncoder.isInitialized()) {
                                    eglEncoder.consumeFrame();  // 只消费帧，ожидание重建
                                }
                                return;
                            }

                            // Получение绝 时间戳（СистемаЗапуск以来 纳 сек.)
                            long absoluteTimestampNs = surfaceTexture.getTimestamp();
                            
                            // 计算相 时间戳（以Первый帧为基准)
                            // 注意：firstFrameTimestampNs  整 шт.Запись期间不Сброс
                            // 因为 eglPresentationTimeANDROID необходимо单调递增 时间戳
                            // 否则 GraphicBufferSource 会отклонить帧
                            if (firstFrameTimestampNs < 0) {
                                firstFrameTimestampNs = absoluteTimestampNs;
                                AppLog.d(TAG, "Camera " + cameraId + " First frame timestamp: " + absoluteTimestampNs + " ns");
                            }
                            long relativeTimestampNs = absoluteTimestampNs - firstFrameTimestampNs;

                            // 直接渲染帧 до 编码器（использование相 时间戳)
                            if (eglEncoder != null && eglEncoder.isInitialized()) {
                                eglEncoder.drawFrame(relativeTimestampNs);
                                recordedFrameCount++;

                                // 定期输出帧计数
                                if (recordedFrameCount % 100 == 0) {
                                    AppLog.d(TAG, "Camera " + cameraId + " Encoded frames: " + recordedFrameCount);
                                }
                            }

                            //  от 编码器Получение输出数据并写入 muxer
                            drainEncoder(false);

                        } catch (Exception e) {
                            AppLog.e(TAG, "Camera " + cameraId + " Error processing frame", e);
                            // 发生аномалия时标记编码器不健康
                            encoderHealthy = false;
                        }
                    }, encoderHandler);

                    // Настройки EGL 渲染器 Ввести
                    eglEncoder.setInputSurfaceTexture(inputSurfaceTexture);

                    // Настройки时间水印（Если Включить)
                    if (watermarkEnabled) {
                        eglEncoder.setWatermarkEnabled(true);
                    }

                    AppLog.d(TAG, "Camera " + cameraId + " EGL/SurfaceTexture initialized on encoder thread, textureId=" + textureId + ", watermark=" + watermarkEnabled);

                } catch (Exception e) {
                    AppLog.e(TAG, "Camera " + cameraId + " Failed to initialize EGL on encoder thread", e);
                    initException[0] = e;
                } finally {
                    latch.countDown();
                }
            });

            // ожиданиеинициализациязавершение（最多 5  сек.)
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout waiting for EGL initialization");
            }

            // проверка 否有инициализацияОшибка
            if (initException[0] != null) {
                throw initException[0];
            }

            AppLog.d(TAG, "Camera " + cameraId + " Codec recording prepared, textureId=" + textureId);

            return inputSurfaceTexture;

        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " Failed to prepare codec recording", e);
            release();
            if (callback != null) {
                callback.onRecordError(cameraId, e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * 准备Запись回调接口
     */
    public interface PrepareCallback {
        /**
         * 准备завершение回调
         * @param success  否Успешно
         * @param surfaceTexture Успешно时返回  SurfaceTexture，Ошибка时为 null
         * @param errorMessage Ошибка时 ОшибкаИнформация，Успешно时为 null
         */
        void onPrepareComplete(boolean success, SurfaceTexture surfaceTexture, String errorMessage);
    }
    
    /**
     * 异步准备Запись（рекомендуетсяиспользование)
     * 
     * 此方法 Фоновый режим线程выполнение准备операция，завершение后 主线程回调
     * 避免 主线程выполнение阻塞операция导致 ANR
     * 
     * @param filePath 输出ФайлПуть
     * @param callback 准备завершение回调
     */
    public void prepareRecordingAsync(String filePath, PrepareCallback callback) {
        new Thread(() -> {
            try {
                SurfaceTexture result = prepareRecording(filePath);
                if (callback != null) {
                    //  主线程回调
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (result != null) {
                            callback.onPrepareComplete(true, result, null);
                        } else {
                            callback.onPrepareComplete(false, null, "Preparation failed");
                        }
                    });
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Camera " + cameraId + " prepareRecordingAsync failed", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onPrepareComplete(false, null, e.getMessage()));
                }
            }
        }, "CodecRecorderPrepare-" + cameraId).start();
    }

    /**
     * Начать запись
     */
    public boolean startRecording() {
        if (encoder == null || eglEncoder == null) {
            AppLog.e(TAG, "Camera " + cameraId + " Encoder not prepared");
            return false;
        }

        if (isRecording.get()) {
            AppLog.w(TAG, "Camera " + cameraId + " Already recording");
            return false;
        }

        AppLog.d(TAG, "Camera " + cameraId + " Starting codec recording");

        // 记录分Вкл始时间（用于 PTS 计算)
        segmentStartTimeNs = System.nanoTime();
        encodedOutputFrameCount = 0;
        
        // Сброс首 раз写入Статус
        hasFirstWrite = false;
        lastFileSize = 0;
        
        isRecording.set(true);

        // 注意：不再использование单独 编码循环
        // 帧 处理直接  onFrameAvailable 回调завершение（该回调  encoderHandler выполнение)
        // 这样避免 Handler 死锁问题

        // 【重要】分定时器延迟 до 首 раз写入后Запуск
        // 这样可以确保：
        // 1. КамераЗапуск慢илинеобходимо修复时，用户只会感觉"Запуск慢"而不 Запись空Видео
        // 2. DingTalk指定时长Запись时，实际Запись时长 действует 
        // scheduleNextSegment() 将  scheduleFileSizeCheck() Обнаружено首 раз写入时调用

        // Запуск首 раз写入таймаутпроверка
        scheduleFirstWriteTimeout();

        // ЗапускФайл大小проверка
        scheduleFileSizeCheck();

        // Запуск编码器健康проверка
        scheduleEncoderHealthCheck();

        if (callback != null && segmentIndex == 0) {
            callback.onRecordStart(cameraId);
        }

        AppLog.d(TAG, "Camera " + cameraId + " Codec recording started");
        return true;
    }

    /**
     * Остановить запись
     */
    public void stopRecording() {
        if (!isRecording.get()) {
            AppLog.w(TAG, "Camera " + cameraId + " Not recording");
            return;
        }

        AppLog.d(TAG, "Camera " + cameraId + " Stopping codec recording");

        // Отмена定时器
        if (segmentRunnable != null) {
            segmentHandler.removeCallbacks(segmentRunnable);
            segmentRunnable = null;
        }
        if (fileSizeCheckRunnable != null) {
            segmentHandler.removeCallbacks(fileSizeCheckRunnable);
            fileSizeCheckRunnable = null;
        }
        // Отмена首 раз写入таймаутпроверка
        cancelFirstWriteTimeout();
        
        // ОтменаВосстановление重试задача
        if (recoveryRunnable != null) {
            segmentHandler.removeCallbacks(recoveryRunnable);
            recoveryRunnable = null;
        }
        recoveryAttempts = 0;

        // Отмена健康проверказадача
        if (healthCheckRunnable != null) {
            segmentHandler.removeCallbacks(healthCheckRunnable);
            healthCheckRunnable = null;
        }

        isRecording.set(false);

        // 稍等一让Выполняется 处理 帧завершение
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            // Ignore
        }

        // Отправказавершить信号 编码器
        if (encoder != null) {
            try {
                encoder.signalEndOfInputStream();
                // 排空编码器
                drainEncoder(true);
            } catch (Exception e) {
                AppLog.e(TAG, "Camera " + cameraId + " Error signaling end of stream", e);
            }
        }

        // Остановка muxer
        if (muxerStarted && muxer != null) {
            try {
                muxer.stop();
            } catch (Exception e) {
                AppLog.e(TAG, "Camera " + cameraId + " Error stopping muxer", e);
            }
            muxerStarted = false;
        }

        // 验证并Очистка 所有Запись Файл
        List<String> deletedFiles = validateAndCleanupAllFiles();

        AppLog.d(TAG, "Camera " + cameraId + " Codec recording stopped, frames recorded: " + recordedFrameCount);

        if (callback != null) {
            callback.onRecordStop(cameraId);
            // Уведомление损坏Файл 删除
            if (!deletedFiles.isEmpty()) {
                callback.onCorruptedFilesDeleted(cameraId, deletedFiles);
            }
        }
        
        recordedFilePaths.clear();
    }

    /**
     * 释放资源
     */
    public void release() {
        if (isReleased) {
            return;
        }

        AppLog.d(TAG, "Camera " + cameraId + " Releasing CodecVideoRecorder");

        isReleased = true;

        if (isRecording.get()) {
            stopRecording();
        }

        // 释放 EGL 渲染器
        if (eglEncoder != null) {
            eglEncoder.release();
            eglEncoder = null;
        }

        // 释放缓存 Запись Surface（必须  SurfaceTexture до释放)
        if (cachedRecordSurface != null) {
            cachedRecordSurface.release();
            cachedRecordSurface = null;
        }

        // 释放 SurfaceTexture
        if (inputSurfaceTexture != null) {
            inputSurfaceTexture.release();
            inputSurfaceTexture = null;
        }

        // 释放编码器
        if (encoder != null) {
            try {
                encoder.stop();
            } catch (Exception e) {
                // Ignore
            }
            encoder.release();
            encoder = null;
        }

        // 释放编码器Ввести Surface
        if (encoderInputSurface != null) {
            encoderInputSurface.release();
            encoderInputSurface = null;
        }

        // 释放 muxer
        if (muxer != null) {
            try {
                if (muxerStarted) {
                    muxer.stop();
                }
            } catch (Exception e) {
                // Ignore
            }
            muxer.release();
            muxer = null;
        }

        // Остановка编码线程
        if (encoderThread != null) {
            encoderThread.quitSafely();
            try {
                encoderThread.join(1000);
            } catch (InterruptedException e) {
                // Ignore
            }
            encoderThread = null;
            encoderHandler = null;
        }

        // Очистка 分处理线程
        if (segmentHandler != null) {
            segmentHandler.removeCallbacksAndMessages(null);
        }
        if (segmentThread != null) {
            segmentThread.quitSafely();
            try {
                segmentThread.join(1000);  // 1 сек.таймаут
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                AppLog.w(TAG, "Camera " + cameraId + " segment thread join interrupted");
            }
            segmentThread = null;
        }
        segmentHandler = null;

        AppLog.d(TAG, "Camera " + cameraId + " CodecVideoRecorder released");
    }

    /**
     * ПолучениеЗапись用  Surface（供 Camera использование)
     * использование缓存режим避免重复创建 Surface 导致内存泄漏
     */
    public Surface getRecordSurface() {
        if (inputSurfaceTexture == null) {
            return null;
        }
        
        // проверка缓存  Surface  否действует
        if (cachedRecordSurface != null && cachedRecordSurface.isValid()) {
            return cachedRecordSurface;
        }
        
        // 释放旧 недействительно Surface
        if (cachedRecordSurface != null) {
            AppLog.d(TAG, "Camera " + cameraId + " releasing invalid cached record surface");
            cachedRecordSurface.release();
            cachedRecordSurface = null;
        }
        
        // 创建新  Surface 并缓存
        cachedRecordSurface = new Surface(inputSurfaceTexture);
        AppLog.d(TAG, "Camera " + cameraId + " created new record surface");
        return cachedRecordSurface;
    }

    /**
     * ПолучениеТекущийФайлПуть
     */
    public String getCurrentFilePath() {
        return currentFilePath;
    }

    /**
     * проверка 否Выполняется Запись
     */
    public boolean isRecording() {
        return isRecording.get();
    }

    // ===== 私有方法 =====

    /**
     * 创建 MediaCodec 编码器
     */
    private void createEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        encoderInputSurface = encoder.createInputSurface();
        encoder.start();

        bufferInfo = new MediaCodec.BufferInfo();

        AppLog.d(TAG, "Camera " + cameraId + " Encoder created: " + width + "x" + height + 
                " @ " + frameRate + "fps, " + (bitRate / 1000) + " Kbps");
    }

    /**
     * 创建 MediaMuxer
     */
    private void createMuxer(String filePath) throws IOException {
        muxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        videoTrackIndex = -1;
        muxerStarted = false;

        AppLog.d(TAG, "Camera " + cameraId + " Muxer created: " + filePath);
    }

    // 注意：encodingLoop() 方法 移除
    // 帧处理现 直接  onFrameAvailable 回调завершение
    // 这样可以避免 Handler 死锁问题

    /**
     * 排空编码器输出
     * 
     * 增强Ошибка处理：
     * - 捕获 IllegalStateException 并标记编码器不健康
     * - 跟踪无输出 帧数，用于健康проверка
     */
    private void drainEncoder(boolean endOfStream) {
        if (encoder == null) {
            return;
        }

        final int TIMEOUT_USEC = 10000;
        boolean gotOutput = false;

        try {
            while (true) {
                int outputBufferIndex;
                try {
                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                } catch (IllegalStateException e) {
                    // 编码器处于недействительноСтатус，标记为不健康
                    AppLog.e(TAG, "Camera " + cameraId + " Encoder in invalid state during dequeueOutputBuffer", e);
                    encoderHealthy = false;
                    return;
                }

                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) {
                        break;  // 没有数据
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 输出格式变化，添加Видео轨道
                    if (muxerStarted) {
                        AppLog.w(TAG, "Camera " + cameraId + " Format changed twice");
                    } else {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        videoTrackIndex = muxer.addTrack(newFormat);
                        muxer.start();
                        muxerStarted = true;
                        encoderHealthy = true;  // Получена команда: 格式变化说明编码器нормально
                        lastEncoderOutputTime = System.currentTimeMillis();
                        AppLog.d(TAG, "Camera " + cameraId + " Muxer started, track=" + videoTrackIndex);
                    }
                    gotOutput = true;
                } else if (outputBufferIndex >= 0) {
                    ByteBuffer encodedData = encoder.getOutputBuffer(outputBufferIndex);

                    if (encodedData == null) {
                        AppLog.e(TAG, "Camera " + cameraId + " Encoder output buffer " + outputBufferIndex + " was null");
                    } else if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // конфигурация数据，忽略（  FORMAT_CHANGED 处理)
                        bufferInfo.size = 0;
                    }

                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            AppLog.e(TAG, "Camera " + cameraId + " Muxer not started but got data");
                        } else {
                            // использованиеСистема时间计算 PTS，而不 基于帧数 и 假设帧率
                            // 优点：
                            //   1. Видео时长精确反映实际Запись时长
                            //   2. 不受帧率波动影响（实际帧率可能  25-30fps 不等)
                            //   3. 掉帧时时间轴仍然正确（只 画面会卡顿)
                            long currentTimeNs = System.nanoTime();
                            long calculatedPtsUs = (currentTimeNs - segmentStartTimeNs) / 1000;
                            
                            // отладка д.志（толькоПервый帧)
                            if (encodedOutputFrameCount == 0) {
                                AppLog.d(TAG, "Camera " + cameraId + " First frame PTS: " + calculatedPtsUs + " us");
                            }
                            
                            // использование计算 时间戳
                            bufferInfo.presentationTimeUs = calculatedPtsUs;
                            
                            encodedData.position(bufferInfo.offset);
                            encodedData.limit(bufferInfo.offset + bufferInfo.size);
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo);
                            
                            encodedOutputFrameCount++;
                            lastEncoderOutputTime = System.currentTimeMillis();
                            gotOutput = true;
                        }
                    }

                    try {
                        encoder.releaseOutputBuffer(outputBufferIndex, false);
                    } catch (IllegalStateException e) {
                        AppLog.e(TAG, "Camera " + cameraId + " Encoder in invalid state during releaseOutputBuffer", e);
                        encoderHealthy = false;
                        return;
                    }

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;  // 流завершить
                    }
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " Unexpected error in drainEncoder", e);
            encoderHealthy = false;
        }

        // обновление无输出帧计数器
        if (gotOutput) {
            framesWithoutEncoderOutput = 0;
        } else {
            framesWithoutEncoderOutput++;
        }
    }

    /**
     * 调度一Запись
     * 
     * 注意：分时长необходимо加补偿时间，因为：
     * 1. 编码器инициализациянеобходимо时间
     * 2. Остановка时необходимо排空编码器缓冲区
     * 3. 这样可以确保实际Запись Видео时长达 до 设定 分时长
     */
    private void scheduleNextSegment() {
        if (segmentRunnable != null) {
            segmentHandler.removeCallbacks(segmentRunnable);
        }

        segmentRunnable = () -> {
            if (isRecording.get() && encoderHandler != null) {
                AppLog.d(TAG, "Camera " + cameraId + " Scheduling segment switch on encoder thread");
                //  编码线程выполнение切换，避免线程冲突
                encoderHandler.post(() -> switchToNextSegment());
            }
        };

        // 延迟выполнение（использованиеконфигурация 分时长 + 补偿时间)
        // 补偿编码器инициализация延迟 и Остановка时 帧丢失
        long actualDelayMs = segmentDurationMs + SEGMENT_DURATION_COMPENSATION_MS;
        segmentHandler.postDelayed(segmentRunnable, actualDelayMs);
        AppLog.d(TAG, "Camera " + cameraId + " Scheduled next segment in " + (segmentDurationMs / 1000) + " seconds (actual delay: " + actualDelayMs + "ms)");
    }

    /**
     * 切换 до 一（ 编码线程выполнение)
     * 
     * 采用简单方案：完整ОстановкаТекущийЗапись，然后重新Вкл始
     * 类似 MediaRecorder  方式，虽然会丢失几帧，但更简单可靠
     * 
     * 快速Восстановление机制：
     * - Успешно时：СбросВосстановление计数器，调度нормально 1 мин.定时器
     * - Ошибка时：использование5 сек.快速重试，最多重试6 раз（30 сек.内)，после回 до нормально1 мин.间隔
     */
    private void switchToNextSegment() {
        // проверка 否仍 ЗаписьСтатус（防止 и  stopRecording 竞态)
        if (!isRecording.get() || isReleased) {
            AppLog.w(TAG, "Camera " + cameraId + " Skipping segment switch (not recording or released)");
            return;
        }
        
        AppLog.d(TAG, "Camera " + cameraId + " Starting segment switch on encoder thread");
        
        boolean switchSuccess = false;
        
        try {
            // 1. ОстановкаТекущийЗапись（会排空编码器、Остановка Muxer)
            stopRecordingForSegmentSwitch();
            
            // 2. 验证ТекущийФайл（ 主线程выполнение，因为  IO операция)
            final String previousFilePath = currentFilePath;
            segmentHandler.post(() -> validateAndCleanupFile(previousFilePath));

            // 3. 准备一
            segmentIndex++;
            String nextSegmentPath = generateSegmentPath();
            currentFilePath = nextSegmentPath;
            recordedFilePaths.add(nextSegmentPath);  // 记录新分Файл
            
            // Сброс分Вкл始时间 и 帧计数
            segmentStartTimeNs = System.nanoTime();
            encodedOutputFrameCount = 0;
            // 不Сброс firstFrameTimestampNs，保持 EGL 时间戳单调递增

            // 4. 创建新  Muxer
            createMuxer(nextSegmentPath);
            
            // 5. 重新Начать запись
            isRecording.set(true);
            switchSuccess = true;
            
            // Успешно：СбросВосстановление计数器
            recoveryAttempts = 0;
            
            AppLog.d(TAG, "Camera " + cameraId + " Switched to segment " + segmentIndex + ": " + nextSegmentPath);

            if (callback != null) {
                final int newIndex = segmentIndex;
                final String completedPath = previousFilePath;  // завершение ФайлПуть
                segmentHandler.post(() -> callback.onSegmentSwitch(cameraId, newIndex, completedPath));
            }

        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " Failed to switch segment (attempt " + (recoveryAttempts + 1) + ")", e);
            
            // 标记ЗаписьСтатус（разрешить帧回调продолжить消费帧)
            isRecording.set(false);
            
            if (callback != null) {
                final String errorMsg = e.getMessage();
                segmentHandler.post(() -> callback.onRecordError(cameraId, "Failed to switch segment: " + errorMsg));
            }
        }
        
        // 6. 根据结果调度一 разоперация
        if (switchSuccess) {
            // Успешно：调度нормально 1 мин.定时器
            segmentHandler.post(() -> scheduleNextSegment());
        } else {
            // Ошибка：Запуск快速Восстановление机制
            recoveryAttempts++;
            if (recoveryAttempts <= MAX_RECOVERY_ATTEMPTS) {
                // 快速重试（5 сек.后)
                AppLog.w(TAG, "Camera " + cameraId + " Segment switch failed, quick retry in " 
                    + (RECOVERY_RETRY_INTERVAL_MS / 1000) + "s (attempt " + recoveryAttempts + "/" + MAX_RECOVERY_ATTEMPTS + ")");
                scheduleRecoveryRetry();
            } else {
                // 超过максимум重试 раз数，回 до нормально分间隔
                AppLog.w(TAG, "Camera " + cameraId + " Max recovery attempts reached, will retry in " 
                    + (segmentDurationMs / 1000) + " seconds");
                recoveryAttempts = 0;  // Сброс计数器
                segmentHandler.post(() -> scheduleNextSegment());
            }
        }
    }
    
    /**
     * 调度快速Восстановление重试
     */
    private void scheduleRecoveryRetry() {
        // Отменадо Восстановлениезадача
        if (recoveryRunnable != null) {
            segmentHandler.removeCallbacks(recoveryRunnable);
        }
        
        recoveryRunnable = () -> {
            if (!isReleased && encoderHandler != null) {
                AppLog.d(TAG, "Camera " + cameraId + " Recovery retry triggered");
                //  编码线程выполнениеВосстановление
                encoderHandler.post(() -> attemptRecovery());
            }
        };
        
        segmentHandler.postDelayed(recoveryRunnable, RECOVERY_RETRY_INTERVAL_MS);
    }
    
    /**
     * попыткаВосстановлениеЗапись
     */
    private void attemptRecovery() {
        AppLog.d(TAG, "Camera " + cameraId + " Attempting recovery (attempt " + recoveryAttempts + "/" + MAX_RECOVERY_ATTEMPTS + ")");
        
        boolean recoverySuccess = false;
        
        try {
            // 确保编码器 и  EGL 准备好
            if (encoder == null) {
                createEncoder();
                if (eglEncoder != null && encoderInputSurface != null) {
                    eglEncoder.updateOutputSurface(encoderInputSurface);
                }
            }
            
            // 创建新  Muxer
            if (muxer == null) {
                String nextSegmentPath = generateSegmentPath();
                currentFilePath = nextSegmentPath;
                createMuxer(nextSegmentPath);
            }
            
            // Сброс分Вкл始时间 и 帧计数
            segmentStartTimeNs = System.nanoTime();
            encodedOutputFrameCount = 0;
            
            // ВосстановлениеЗапись
            isRecording.set(true);
            recoverySuccess = true;
            
            // Успешно：СбросВосстановление计数器
            recoveryAttempts = 0;
            
            AppLog.d(TAG, "Camera " + cameraId + " Recovery successful, recording resumed: " + currentFilePath);
            
            // 调度нормально 1 мин.定时器
            segmentHandler.post(() -> scheduleNextSegment());
            
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " Recovery attempt failed", e);
            isRecording.set(false);
            
            // продолжить快速重试или回 до нормально间隔
            recoveryAttempts++;
            if (recoveryAttempts <= MAX_RECOVERY_ATTEMPTS) {
                AppLog.w(TAG, "Camera " + cameraId + " Recovery failed, quick retry in " 
                    + (RECOVERY_RETRY_INTERVAL_MS / 1000) + "s (attempt " + recoveryAttempts + "/" + MAX_RECOVERY_ATTEMPTS + ")");
                scheduleRecoveryRetry();
            } else {
                AppLog.w(TAG, "Camera " + cameraId + " Max recovery attempts reached, will retry in " 
                    + (segmentDurationMs / 1000) + " seconds");
                recoveryAttempts = 0;
                segmentHandler.post(() -> scheduleNextSegment());
            }
        }
    }
    
    /**
     * 为分切换Остановить запись（ 编码线程выполнение)
     * 完整Остановка并重新创建编码器
     * 
     * 注意：此方法有完善 аномалия处理，т.е.使部分операцияОшибкатакже会продолжитьвыполнение
     */
    private void stopRecordingForSegmentSwitch() {
        AppLog.d(TAG, "Camera " + cameraId + " Stopping recording for segment switch");
        
        // 1. Остановить запись（阻止新帧写入)
        isRecording.set(false);
        
        // 2. 排空编码器（drainEncoder 现  同一线程выполнение，不会有竞争)
        if (encoder != null) {
            try {
                drainEncoder(false);  // 先排空有数据
            } catch (Exception e) {
                AppLog.e(TAG, "Camera " + cameraId + " Error draining encoder during segment switch", e);
            }
        }
        
        // 3. Остановка Muxer（т.е.使Ошибкатакжепродолжить)
        if (muxer != null) {
            try {
                if (muxerStarted) {
                    muxer.stop();
                }
                muxer.release();
            } catch (Exception e) {
                AppLog.e(TAG, "Camera " + cameraId + " Error stopping muxer during segment switch", e);
            }
            muxer = null;
            muxerStarted = false;
            videoTrackIndex = -1;
        }
        
        // 4. 释放旧编码器（т.е.使Ошибкатакжепродолжить)
        if (encoder != null) {
            try {
                encoder.stop();
            } catch (Exception e) {
                AppLog.w(TAG, "Camera " + cameraId + " Error stopping encoder: " + e.getMessage());
            }
            try {
                encoder.release();
            } catch (Exception e) {
                AppLog.w(TAG, "Camera " + cameraId + " Error releasing encoder: " + e.getMessage());
            }
            encoder = null;
        }
        
        if (encoderInputSurface != null) {
            try {
                encoderInputSurface.release();
            } catch (Exception e) {
                AppLog.w(TAG, "Camera " + cameraId + " Error releasing encoder surface: " + e.getMessage());
            }
            encoderInputSurface = null;
        }
        
        // 5. 重新创建编码器
        try {
            createEncoder();
            
            // 重新Настройки EGL  输出 Surface
            if (eglEncoder != null && encoderInputSurface != null) {
                eglEncoder.updateOutputSurface(encoderInputSurface);
            }
            
            AppLog.d(TAG, "Camera " + cameraId + " Encoder recreated for new segment");
            
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " Failed to recreate encoder", e);
            // 抛出аномалия，让调用者处理
            throw new RuntimeException("Failed to recreate encoder for segment switch", e);
        }
    }

    /**
     * 生成新 分ФайлПуть
     * 优先использование TimestampProvider Получение统一时间戳（多 кам.Камера同步)
     * Если 没有Настройки provider，则использованиеТекущий时间
     */
    private String generateSegmentPath() {
        String timestamp;
        if (timestampProvider != null) {
            // использование统一 时间戳提供者（确保多 кам.Камераиспользование相同时间戳)
            timestamp = timestampProvider.getSegmentTimestamp();
            AppLog.d(TAG, "Camera " + cameraId + " using provider timestamp: " + timestamp);
        } else {
            // 回退 до 独立生成时间戳（совместимость旧逻辑)
            timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            AppLog.d(TAG, "Camera " + cameraId + " using local timestamp: " + timestamp);
        }
        String fileName = timestamp + "_" + cameraPosition + ".mp4";
        return new File(saveDirectory, fileName).getAbsolutePath();
    }

    /**
     * 调度编码器健康проверка
     * 检测编码器 否нормально工作，Если 长时间无输出则попытка重建
     */
    private void scheduleEncoderHealthCheck() {
        if (healthCheckRunnable != null) {
            segmentHandler.removeCallbacks(healthCheckRunnable);
        }

        healthCheckRunnable = () -> {
            if (!isRecording.get() || isReleased) {
                return;
            }

            // проверка编码器健康Статус
            boolean needsRecovery = false;
            String reason = "";

            if (!encoderHealthy) {
                needsRecovery = true;
                reason = "encoder marked unhealthy";
            } else if (!muxerStarted && recordedFrameCount > MAX_FRAMES_WITHOUT_OUTPUT) {
                // Muxer  от Не Запуск，但经处理很多帧
                needsRecovery = true;
                reason = "muxer never started after " + recordedFrameCount + " frames";
            } else if (framesWithoutEncoderOutput > MAX_FRAMES_WITHOUT_OUTPUT) {
                needsRecovery = true;
                reason = "no encoder output for " + framesWithoutEncoderOutput + " frames";
            }

            if (needsRecovery) {
                AppLog.w(TAG, "Camera " + cameraId + " Encoder health check FAILED: " + reason);
                AppLog.w(TAG, "Camera " + cameraId + " Attempting to rebuild encoder...");

                //  编码线程выполнение重建
                if (encoderHandler != null) {
                    encoderHandler.post(() -> rebuildEncoder());
                }
            } else {
                // 编码器健康，продолжить调度一 разпроверка
                scheduleEncoderHealthCheck();
            }
        };

        segmentHandler.postDelayed(healthCheckRunnable, ENCODER_HEALTH_CHECK_INTERVAL_MS);
    }

    /**
     * 重建编码器（ 编码线程выполнение)
     * 当Обнаружено编码器不健康时调用
     */
    private void rebuildEncoder() {
        AppLog.d(TAG, "Camera " + cameraId + " Rebuilding encoder due to health check failure");

        // ПаузаЗапись
        isRecording.set(false);

        try {
            // 1. Очистка 旧  Muxer（可能损坏)
            if (muxer != null) {
                try {
                    if (muxerStarted) {
                        muxer.stop();
                    }
                    muxer.release();
                } catch (Exception e) {
                    AppLog.w(TAG, "Camera " + cameraId + " Error releasing old muxer: " + e.getMessage());
                }
                muxer = null;
                muxerStarted = false;
                videoTrackIndex = -1;
            }

            // 2. Очистка 旧 编码器
            if (encoder != null) {
                try {
                    encoder.stop();
                } catch (Exception e) {
                    // Ignore
                }
                try {
                    encoder.release();
                } catch (Exception e) {
                    // Ignore
                }
                encoder = null;
            }

            if (encoderInputSurface != null) {
                try {
                    encoderInputSurface.release();
                } catch (Exception e) {
                    // Ignore
                }
                encoderInputSurface = null;
            }

            // 3. 小延迟让Система释放资源
            Thread.sleep(100);

            // 4. 重新创建编码器
            createEncoder();

            // 5. обновление EGL 输出 Surface
            if (eglEncoder != null && encoderInputSurface != null) {
                eglEncoder.updateOutputSurface(encoderInputSurface);
            }

            // 6. 创建新  Muxer（生成新 Файл名)
            segmentIndex++;
            String newFilePath = generateSegmentPath();
            currentFilePath = newFilePath;
            recordedFilePaths.add(newFilePath);
            createMuxer(newFilePath);

            // 7. СбросСтатус
            segmentStartTimeNs = System.nanoTime();
            encodedOutputFrameCount = 0;
            framesWithoutEncoderOutput = 0;
            encoderHealthy = true;
            lastEncoderOutputTime = System.currentTimeMillis();

            // 8. ВосстановлениеЗапись
            isRecording.set(true);

            AppLog.d(TAG, "Camera " + cameraId + " Encoder rebuilt successfully, new file: " + newFilePath);

            // 9. продолжить健康проверка
            segmentHandler.post(() -> scheduleEncoderHealthCheck());

            // 10. 重新调度分定时器
            segmentHandler.post(() -> scheduleNextSegment());

        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " Failed to rebuild encoder", e);

            // 重建Ошибка，ЗапускВосстановление重试机制
            recoveryAttempts++;
            if (recoveryAttempts <= MAX_RECOVERY_ATTEMPTS) {
                AppLog.w(TAG, "Camera " + cameraId + " Will retry encoder rebuild in " 
                    + (RECOVERY_RETRY_INTERVAL_MS / 1000) + "s (attempt " + recoveryAttempts + "/" + MAX_RECOVERY_ATTEMPTS + ")");
                scheduleRecoveryRetry();
            } else {
                AppLog.e(TAG, "Camera " + cameraId + " Max recovery attempts reached, giving up");
                if (callback != null) {
                    final String errorMsg = e.getMessage();
                    segmentHandler.post(() -> callback.onRecordError(cameraId, "Encoder rebuild failed: " + errorMsg));
                }
            }
        }
    }

    /**
     * 调度Файл大小проверка
     */
    private void scheduleFileSizeCheck() {
        if (fileSizeCheckRunnable != null) {
            segmentHandler.removeCallbacks(fileSizeCheckRunnable);
        }

        fileSizeCheckRunnable = () -> {
            if (isRecording.get() && currentFilePath != null) {
                File file = new File(currentFilePath);
                long currentSize = file.exists() ? file.length() : 0;
                long sizeIncrease = currentSize - lastFileSize;

                // проверка 否有写入
                boolean hasWrite = (sizeIncrease > 0) || (currentSize > MIN_VALID_FILE_SIZE);
                
                if (hasWrite) {
                    // 首 раз写入检测
                    if (!hasFirstWrite) {
                        hasFirstWrite = true;
                        AppLog.d(TAG, "Camera " + cameraId + " first write detected! Size: " + currentSize + " bytes");
                        // Отмена首 раз写入таймаутпроверка
                        cancelFirstWriteTimeout();
                        
                        // 【核心改动】首 раз写入后才Запуск分定时器
                        // 这确保分时长 "действуетЗапись时长"而非"попыткаЗапись时长"
                        scheduleNextSegment();
                        AppLog.d(TAG, "Camera " + cameraId + " segment timer started after first write");
                        
                        // УведомлениеВнешнее：首 раз写入Успешно，Запись真正Вкл始
                        // Внешнее可以据此Вкл始DingTalkЗапись计时等
                        if (callback != null) {
                            callback.onFirstDataWritten(cameraId);
                        }
                    }
                    AppLog.d(TAG, "Camera " + cameraId + " file size: " + currentSize + " bytes (" + (currentSize / 1024) + " KB), frames: " + recordedFrameCount);
                } else if (sizeIncrease == 0 && lastFileSize > 0) {
                    AppLog.w(TAG, "Camera " + cameraId + " WARNING: File size not growing! Current: " + currentSize + " bytes");
                }

                lastFileSize = currentSize;
                
                // продолжить一 разпроверка（首 раз写入前用快速间隔，после用нормально间隔)
                long nextDelay = hasFirstWrite ? FILE_SIZE_CHECK_INTERVAL_MS : FIRST_CHECK_DELAY_MS;
                segmentHandler.postDelayed(fileSizeCheckRunnable, nextDelay);
            }
        };

        // 首 разпроверкаиспользование更短 延迟，快速检测首 раз写入
        long initialDelay = hasFirstWrite ? FILE_SIZE_CHECK_INTERVAL_MS : FIRST_CHECK_DELAY_MS;
        segmentHandler.postDelayed(fileSizeCheckRunnable, initialDelay);
    }

    /**
     * 调度首 раз写入таймаутпроверка
     */
    private void scheduleFirstWriteTimeout() {
        // Отменадо таймаутпроверка
        cancelFirstWriteTimeout();

        firstWriteTimeoutRunnable = () -> {
            if (isRecording.get() && !hasFirstWrite) {
                AppLog.e(TAG, "Camera " + cameraId + " FIRST WRITE TIMEOUT: No data written in " + (FIRST_WRITE_TIMEOUT_MS / 1000) + " seconds");
                // 触发编码器重建（通过健康проверка机制处理)
                encoderHealthy = false;
                // также可以通过回调УведомлениеВнешнее
                if (callback != null) {
                    segmentHandler.post(() -> callback.onRecordingRebuildRequested(cameraId, "first_write_timeout"));
                }
            }
        };

        segmentHandler.postDelayed(firstWriteTimeoutRunnable, FIRST_WRITE_TIMEOUT_MS);
        AppLog.d(TAG, "Camera " + cameraId + " first write timeout scheduled: " + (FIRST_WRITE_TIMEOUT_MS / 1000) + " seconds");
    }

    /**
     * Отмена首 раз写入таймаутпроверка
     */
    private void cancelFirstWriteTimeout() {
        if (firstWriteTimeoutRunnable != null) {
            segmentHandler.removeCallbacks(firstWriteTimeoutRunnable);
            firstWriteTimeoutRunnable = null;
        }
    }

    /**
     * 验证并Очистка 所有Запись Файл
     * @return  删除 Файл名列表
     */
    private List<String> validateAndCleanupAllFiles() {
        List<String> deletedFiles = new ArrayList<>();
        
        AppLog.d(TAG, "Camera " + cameraId + " validating " + recordedFilePaths.size() + " recorded files");
        
        for (String filePath : recordedFilePaths) {
            String deletedFileName = validateAndCleanupFile(filePath);
            if (deletedFileName != null) {
                deletedFiles.add(deletedFileName);
            }
        }
        
        if (!deletedFiles.isEmpty()) {
            AppLog.w(TAG, "Camera " + cameraId + " deleted " + deletedFiles.size() + " corrupted files: " + deletedFiles);
        }
        
        return deletedFiles;
    }

    /**
     * 验证并Очистка 损坏 Файл
     * @return Если Файл 删除，返回Файл名；否则Возвращает null
     */
    private String validateAndCleanupFile(String filePath) {
        if (filePath == null) {
            return null;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }

        long fileSize = file.length();

        if (fileSize < MIN_VALID_FILE_SIZE) {
            AppLog.w(TAG, "Camera " + cameraId + " Video file too small: " + filePath + " (" + fileSize + " bytes). Deleting...");
            file.delete();
            return file.getName();
        } else {
            AppLog.d(TAG, "Camera " + cameraId + " Video file validated: " + filePath + " (" + (fileSize / 1024) + " KB)");
            return null;
        }
    }
}
