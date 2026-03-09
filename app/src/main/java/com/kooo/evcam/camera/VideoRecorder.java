package com.kooo.evcam.camera;


import com.kooo.evcam.AppLog;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ВидеоЗаписьуправление类
 */
public class VideoRecorder {
    private static final String TAG = "VideoRecorder";

    /**
     * 分时间戳提供者接口
     * 用于多 кам.Камера分切换时использование统一 时间戳
     */
    public interface SegmentTimestampProvider {
        /**
         * ПолучениеТекущий分 统一时间戳
         * @return 时间戳字符串，格式为 yyyyMMdd_HHmmss
         */
        String getSegmentTimestamp();
    }

    /**
     * ЗаписьСтатус枚举 - 用于解决分切换 и Остановить запись 竞态条件
     */
    public enum RecordingState {
        IDLE,                    // 空闲Статус
        PREPARING,               // 准备
        RECORDING,               // Запись
        SWITCHING_SEGMENT,       // 分切换（此Статус禁止Остановка)
        STOPPING                 // Остановка
    }

    // 编码器Разрешение限制（H.264 编码器通常有Разрешение限)
    // 大多数 Android 设备  H.264 编码器максимумПоддерживаемые 4096x4096 или类似
    // 但某些Камера可能输出超过 5000 宽度 Разрешение，导致编码Ошибка
    private static final int DEFAULT_MAX_ENCODE_WIDTH = 4096;   // По умолчаниюмаксимум编码宽度
    private static final int DEFAULT_MAX_ENCODE_HEIGHT = 4096;  // По умолчаниюмаксимум编码Высокий度
    private int maxEncodeWidth = DEFAULT_MAX_ENCODE_WIDTH;      // 可конфигурация максимум编码宽度
    private int maxEncodeHeight = DEFAULT_MAX_ENCODE_HEIGHT;    // 可конфигурация максимум编码Высокий度

    private final String cameraId;
    private MediaRecorder mediaRecorder;
    private Surface cachedSurface;  // 缓存 Запись Surface，确保整 шт.Запись周期использование同一 шт. 象
    private RecordCallback callback;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);  // использование AtomicBoolean 确保线程安全
    private volatile RecordingState state = RecordingState.IDLE;  // ЗаписьСтатус
    private final Object stateLock = new Object();  // Статус锁
    private boolean waitingForSessionReconfiguration = false;  // ожидание会话重新конфигурация
    private String currentFilePath;
    
    // Запись参数（可конфигурация)
    private int videoBitrate = 3000000;  // По умолчанию 3Mbps
    private int videoFrameRate = 30;     // По умолчанию 30fps
    
    // 实际использование 编码Разрешение（可能因限制而缩小)
    private int actualEncodeWidth;
    private int actualEncodeHeight;

    // 分Запись相Выкл
    private long segmentDurationMs = 60000;  // 分时长，По умолчанию1 мин.，可通过 setSegmentDuration конфигурация
    private static final long SEGMENT_DURATION_COMPENSATION_MS = 0;  // 分时长补偿（H3修复后定时器更精确，不再необходимо补偿)
    private static final long FILE_SIZE_CHECK_INTERVAL_MS = 3000;  // 每3 сек.проверка一 разФайл大小（加快检测)
    private static final long FIRST_CHECK_DELAY_MS = 500;  // 首 разпроверка延迟（更快检测首 раз写入)
    private static final long MIN_VALID_FILE_SIZE = 10 * 1024;  // минимумдействуетФайл大小 10KB
    
    // использование独立 Фоновый режим线程处理分 и Файл I/O операция，避免阻塞主线程导致 ANR
    private HandlerThread segmentThread;
    private Handler segmentHandler;
    
    private Runnable segmentRunnable;
    private Runnable fileSizeCheckRunnable;  // Файл大小проверказадача
    private Runnable pendingSegmentSwitchRunnable;  // 待выполнение 分切换задача（用于Отмена)
    private int segmentIndex = 0;
    private String saveDirectory;  // Сохранитькаталог
    private String cameraPosition;  // КамераПозиция（front/back/left/right)
    private SegmentTimestampProvider timestampProvider;  // 分时间戳提供者（用于多 кам.同步)
    private int recordWidth;
    private int recordHeight;
    private long lastFileSize = 0;  //  разпроверка Файл大小
    private List<String> recordedFilePaths = new ArrayList<>();  // 本 разЗапись 所有ФайлПуть

    // Watchdog 相Выкл：检测无写入并求重建
    private static final int WATCHDOG_NO_WRITE_THRESHOLD = 3;  // 连续 N  раз无写入则触发重建
    private static final long FIRST_WRITE_TIMEOUT_MS = 10000;  // 首 раз写入таймаут（10 сек.)
    private int noWriteCount = 0;  // 连续无写入计数
    private boolean hasFirstWrite = false;  //  否有首 раз写入
    private long recordingStartTime = 0;  // ЗаписьВкл始时间
    private Runnable firstWriteTimeoutRunnable;  // 首 раз写入таймаутпроверказадача

    public VideoRecorder(String cameraId) {
        this.cameraId = cameraId;
        // 创建独立 Фоновый режим线程用于分处理 и Файл I/O операция
        segmentThread = new HandlerThread("VideoRecorder-Segment-" + cameraId);
        segmentThread.start();
        this.segmentHandler = new Handler(segmentThread.getLooper());
    }

    public void setCallback(RecordCallback callback) {
        this.callback = callback;
    }

    /**
     * Настройки分时间戳提供者
     * 用于多 кам.Камера分切换时использование统一 时间戳，避免时间戳差1 сек.导致分 группОшибка
     * @param provider 时间戳提供者
     */
    public void setTimestampProvider(SegmentTimestampProvider provider) {
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
     * НастройкиЗапись码率
     * @param bitrate 码率（bps)
     */
    public void setVideoBitrate(int bitrate) {
        this.videoBitrate = bitrate;
        AppLog.d(TAG, "Camera " + cameraId + " bitrate set to " + (bitrate / 1000) + " Kbps");
    }

    /**
     * НастройкиЗапись帧率
     * @param frameRate 帧率（fps)
     */
    public void setVideoFrameRate(int frameRate) {
        this.videoFrameRate = frameRate;
        AppLog.d(TAG, "Camera " + cameraId + " frame rate set to " + frameRate + " fps");
    }

    /**
     * ПолучениеТекущие настройки 码率
     */
    public int getVideoBitrate() {
        return videoBitrate;
    }

    /**
     * ПолучениеТекущие настройки 帧率
     */
    public int getVideoFrameRate() {
        return videoFrameRate;
    }

    /**
     * Настройкимаксимум编码Разрешение（用于限制超大РазрешениеКамера)
     * @param maxWidth максимум宽度
     * @param maxHeight максимумВысокий度
     */
    public void setMaxEncodeResolution(int maxWidth, int maxHeight) {
        this.maxEncodeWidth = maxWidth;
        this.maxEncodeHeight = maxHeight;
        AppLog.d(TAG, "Camera " + cameraId + " max encode resolution set to " + maxWidth + "x" + maxHeight);
    }

    /**
     * Получениемаксимум编码宽度
     */
    public int getMaxEncodeWidth() {
        return maxEncodeWidth;
    }

    /**
     * Получениемаксимум编码Высокий度
     */
    public int getMaxEncodeHeight() {
        return maxEncodeHeight;
    }

    /**
     * Получение实际编码宽度（可能因Разрешение限制而缩小)
     */
    public int getActualEncodeWidth() {
        return actualEncodeWidth;
    }

    /**
     * Получение实际编码Высокий度（可能因Разрешение限制而缩小)
     */
    public int getActualEncodeHeight() {
        return actualEncodeHeight;
    }

    /**
     * 计算调整后 编码Разрешение（保持宽Высокий比，且宽Высокийвсе为偶数)
     * @param inputWidth Ввести宽度
     * @param inputHeight ВвестиВысокий度
     * @return int[2] содержит调整后  [宽度, Высокий度]
     */
    private int[] calculateAdjustedResolution(int inputWidth, int inputHeight) {
        int outputWidth = inputWidth;
        int outputHeight = inputHeight;
        
        // проверка 否необходимо缩小Разрешение
        boolean needsAdjustment = false;
        
        if (inputWidth > maxEncodeWidth || inputHeight > maxEncodeHeight) {
            needsAdjustment = true;
            
            // 计算缩放比例（取较大 缩放因子以确保两边все 限制内)
            float widthRatio = (float) maxEncodeWidth / inputWidth;
            float heightRatio = (float) maxEncodeHeight / inputHeight;
            float scaleFactor = Math.min(widthRatio, heightRatio);
            
            // 按比例缩小
            outputWidth = (int) (inputWidth * scaleFactor);
            outputHeight = (int) (inputHeight * scaleFactor);
            
            AppLog.w(TAG, "Camera " + cameraId + " resolution exceeds encoder limit! " +
                    "Input: " + inputWidth + "x" + inputHeight + 
                    ", Max: " + maxEncodeWidth + "x" + maxEncodeHeight +
                    ", Scale factor: " + String.format("%.3f", scaleFactor));
        }
        
        // 确保宽Высокийвсе 偶数（编码器要求)
        outputWidth = (outputWidth / 2) * 2;
        outputHeight = (outputHeight / 2) * 2;
        
        // 确保Разрешение不为 0
        if (outputWidth < 2) outputWidth = 2;
        if (outputHeight < 2) outputHeight = 2;
        
        if (needsAdjustment) {
            AppLog.w(TAG, "Camera " + cameraId + " resolution adjusted: " + 
                    inputWidth + "x" + inputHeight + " -> " + outputWidth + "x" + outputHeight);
        }
        
        return new int[] { outputWidth, outputHeight };
    }

    /**
     * Получение分时长（毫 сек.)
     */
    public long getSegmentDuration() {
        return segmentDurationMs;
    }

    public boolean isRecording() {
        return isRecording.get();
    }

    /**
     * проверкаЗапись器 否准备好（但Не Начать запись)
     * 用于判断 否可以Запуск初始Запись
     */
    public boolean isPrepared() {
        return mediaRecorder != null && cachedSurface != null && !isRecording.get() && !waitingForSessionReconfiguration;
    }

    public Surface getSurface() {
        // 优先返回缓存  Surface，确保传  CameraCaptureSession   同一 шт. 象
        if (cachedSurface != null) {
            AppLog.d(TAG, "Camera " + cameraId + " getSurface (cached): " + cachedSurface + ", isValid=" + cachedSurface.isValid());
            return cachedSurface;
        }
        // Если 没有缓存，попытка от  MediaRecorder Получение并缓存
        if (mediaRecorder != null) {
            Surface surface = mediaRecorder.getSurface();
            if (surface != null) {
                cachedSurface = surface;  // 缓存起来
                AppLog.d(TAG, "Camera " + cameraId + " getSurface (new, now cached): " + surface + ", isValid=" + surface.isValid());
            } else {
                AppLog.w(TAG, "Camera " + cameraId + " getSurface returned NULL");
            }
            return surface;
        }
        AppLog.w(TAG, "Camera " + cameraId + " getSurface: mediaRecorder is NULL");
        return null;
    }

    /**
     * ПолучениеТекущий索引
     */
    public int getCurrentSegmentIndex() {
        return segmentIndex;
    }

    /**
     * ПолучениеТекущийФайлПуть
     */
    public String getCurrentFilePath() {
        return currentFilePath;
    }

    /**
     * проверка 否Выполняется ожидание会话重新конфигурация
     */
    public boolean isWaitingForSessionReconfiguration() {
        return waitingForSessionReconfiguration;
    }

    /**
     * очисткаожидание会话重新конфигурация 标志
     */
    public void clearWaitingForSessionReconfiguration() {
        waitingForSessionReconfiguration = false;
    }

    /**
     * 准备Запись器
     */
    private void prepareMediaRecorder(String filePath, int width, int height) throws IOException {
        // 【Выкл键】проверка并调整Разрешение以适应编码器限制
        int[] adjusted = calculateAdjustedResolution(width, height);
        int encodeWidth = adjusted[0];
        int encodeHeight = adjusted[1];
        
        // Сохранить实际использование 编码Разрешение
        actualEncodeWidth = encodeWidth;
        actualEncodeHeight = encodeHeight;
        
        mediaRecorder = new MediaRecorder();
        
        // 添加监听器以监控 MediaRecorder Статус（отладка用)
        mediaRecorder.setOnInfoListener((mr, what, extra) -> {
            String info = "UNKNOWN";
            switch (what) {
                case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    info = "MAX_DURATION_REACHED";
                    break;
                case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                    info = "MAX_FILESIZE_REACHED";
                    break;
                case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
                    info = "INFO_UNKNOWN";
                    break;
            }
            AppLog.d(TAG, "Camera " + cameraId + " MediaRecorder INFO: " + info + " (what=" + what + ", extra=" + extra + ")");
        });
        
        mediaRecorder.setOnErrorListener((mr, what, extra) -> {
            String error = "UNKNOWN";
            switch (what) {
                case MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN:
                    error = "ERROR_UNKNOWN";
                    break;
                case MediaRecorder.MEDIA_ERROR_SERVER_DIED:
                    error = "SERVER_DIED";
                    break;
            }
            AppLog.e(TAG, "Camera " + cameraId + " MediaRecorder ERROR: " + error + " (what=" + what + ", extra=" + extra + ")");
        });
        
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(filePath);
        mediaRecorder.setVideoEncodingBitRate(videoBitrate);
        mediaRecorder.setVideoFrameRate(videoFrameRate);
        mediaRecorder.setVideoSize(encodeWidth, encodeHeight);  // использование调整后 Разрешение
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.prepare();
        
        //  д.志：显示原始 и 实际编码Разрешение
        if (width != encodeWidth || height != encodeHeight) {
            AppLog.w(TAG, "Camera " + cameraId + " MediaRecorder configured with ADJUSTED resolution: " + 
                    width + "x" + height + " -> " + encodeWidth + "x" + encodeHeight + 
                    " @ " + videoFrameRate + "fps, " + (videoBitrate / 1000) + " Kbps");
        } else {
            AppLog.d(TAG, "Camera " + cameraId + " MediaRecorder configured: " + encodeWidth + "x" + encodeHeight + 
                    " @ " + videoFrameRate + "fps, " + (videoBitrate / 1000) + " Kbps");
        }
        
        // 准备后立т.е.缓存 Surface，确保整 шт.Запись周期использование同一 шт. 象
        // 这 于某些车机平台很重要，因为 Camera2 API 可能无法识别不同  Surface 包装 象
        cachedSurface = mediaRecorder.getSurface();
        if (cachedSurface != null) {
            AppLog.d(TAG, "Camera " + cameraId + " MediaRecorder Surface created and cached: " + cachedSurface + 
                    ", isValid=" + cachedSurface.isValid());
        } else {
            AppLog.e(TAG, "Camera " + cameraId + " MediaRecorder Surface is NULL after prepare!");
        }
    }

    /**
     * 准备Запись器（不Запуск)
     */
    public boolean prepareRecording(String filePath, int width, int height) {
        if (isRecording.get()) {
            AppLog.w(TAG, "Camera " + cameraId + " is already recording");
            return false;
        }

        // 先释放旧  MediaRecorder（Если существует)
        releaseMediaRecorder();

        try {
            // СохранитьЗапись参数用于分
            this.recordWidth = width;
            this.recordHeight = height;
            this.segmentIndex = 0;

            //  от ФайлПуть提取Сохранитькаталог и КамераПозиция
            File file = new File(filePath);
            this.saveDirectory = file.getParent();
            String fileName = file.getName();
            // Файл名格式： д.期_时间_КамераПозиция.mp4
            // 提取КамераПозиция（最后一 шт.划线后 部分，去掉.mp4)
            int lastUnderscoreIndex = fileName.lastIndexOf('_');
            if (lastUnderscoreIndex > 0 && fileName.endsWith(".mp4")) {
                this.cameraPosition = fileName.substring(lastUnderscoreIndex + 1, fileName.length() - 4);
            } else {
                this.cameraPosition = "unknown";
            }

            // 清空并инициализация本 разЗапись Файл列表
            recordedFilePaths.clear();
            recordedFilePaths.add(filePath);

            // использование传入 ФайлПуть作为Первый
            prepareMediaRecorder(filePath, width, height);
            currentFilePath = filePath;
            AppLog.d(TAG, "Camera " + cameraId + " prepared recording to: " + filePath);
            return true;
        } catch (IOException e) {
            AppLog.e(TAG, "Failed to prepare recording for camera " + cameraId, e);
            releaseMediaRecorder();
            // 确保Статус Сброс
            isRecording.set(false);
            waitingForSessionReconfiguration = false;
            currentFilePath = null;
            segmentIndex = 0;
            if (callback != null) {
                callback.onRecordError(cameraId, e.getMessage());
            }
            return false;
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
     * Начать запись（必须先调用 prepareRecording)
     */
    public boolean startRecording() {
        if (mediaRecorder == null) {
            AppLog.e(TAG, "Camera " + cameraId + " MediaRecorder not prepared");
            return false;
        }

        if (isRecording.get()) {
            AppLog.w(TAG, "Camera " + cameraId + " is already recording");
            return false;
        }

        // 诊断：проверка缓存  Surface Статус
        if (cachedSurface == null) {
            AppLog.e(TAG, "Camera " + cameraId + " CRITICAL: Cached Surface is NULL before start!");
        } else if (!cachedSurface.isValid()) {
            AppLog.e(TAG, "Camera " + cameraId + " CRITICAL: Cached Surface is INVALID before start! Surface=" + cachedSurface);
        } else {
            AppLog.d(TAG, "Camera " + cameraId + " Cached Surface OK before start: " + cachedSurface + ", isValid=true");
        }

        try {
            AppLog.d(TAG, "Camera " + cameraId + " calling mediaRecorder.start()...");
            mediaRecorder.start();
            isRecording.set(true);
            lastFileSize = 0;  // СбросФайл大小计数
            recordingStartTime = System.currentTimeMillis();  // 记录Вкл始时间
            
            // Сброс Watchdog Статус
            noWriteCount = 0;
            hasFirstWrite = false;
            
            // обновлениеСтатус为 RECORDING
            synchronized (stateLock) {
                state = RecordingState.RECORDING;
            }
            
            AppLog.d(TAG, "Camera " + cameraId + " started recording segment " + segmentIndex);
            
            // 诊断：start() 后再 разпроверка缓存  Surface Статус（应该 同一 шт. 象)
            if (cachedSurface != null) {
                AppLog.d(TAG, "Camera " + cameraId + " Cached Surface after start: " + cachedSurface + 
                        ", isValid=" + cachedSurface.isValid());
            }
            
            if (callback != null && segmentIndex == 0) {
                // 只 Первый时УведомлениеНачать запись
                callback.onRecordStart(cameraId);
            }

            // 【重要】分定时器延迟 до 首 раз写入后Запуск
            // 这样可以确保：
            // 1. КамераЗапуск慢илинеобходимо修复时，用户只会感觉"Запуск慢"而不 Запись空Видео
            // 2. DingTalk指定时长Запись时，实际Запись时长 действует 
            // scheduleNextSegment() 将  scheduleFileSizeCheck() Обнаружено首 раз写入时调用
            
            // Запуск首 раз写入таймаутпроверка（每всенеобходимо，用于检测Запись 否нормально)
            scheduleFirstWriteTimeout();
            
            // ЗапускФайл大小проверка（用于诊断 MediaRecorder  否 接收帧)
            scheduleFileSizeCheck();

            return true;
        } catch (RuntimeException e) {
            AppLog.e(TAG, "Failed to start recording for camera " + cameraId, e);
            releaseMediaRecorder();
            // Ошибка时Восстановление до  IDLE Статус
            synchronized (stateLock) {
                state = RecordingState.IDLE;
            }
            if (callback != null) {
                callback.onRecordError(cameraId, e.getMessage());
            }
            return false;
        }
    }

    /**
     * 调度一Запись
     * 
     * 注意：分时长необходимо加补偿时间，因为：
     * 1. MediaRecorder.start() 后необходимо时间инициализация编码器
     * 2. MediaRecorder.stop() 时可能丢失Выполняется 编码 帧
     * 3. 这样可以确保实际Запись Видео时长达 до 设定 分时长
     */
    private void scheduleNextSegment() {
        // 防御性проверка：确保 Handler Доступно
        if (segmentHandler == null) {
            AppLog.w(TAG, "Camera " + cameraId + " segmentHandler is null, cannot schedule next segment");
            return;
        }
        
        // Отменадо 定时器
        if (segmentRunnable != null) {
            segmentHandler.removeCallbacks(segmentRunnable);
        }

        // 创建新 分задача
        segmentRunnable = () -> {
            if (isRecording.get()) {
                AppLog.d(TAG, "Camera " + cameraId + " switching to next segment");
                switchToNextSegment();
            }
        };

        // 延迟выполнение（использованиеконфигурация 分时长 + 补偿时间)
        // 补偿编码器инициализация延迟 и Остановка时 帧丢失
        long actualDelayMs = segmentDurationMs + SEGMENT_DURATION_COMPENSATION_MS;
        segmentHandler.postDelayed(segmentRunnable, actualDelayMs);
        AppLog.d(TAG, "Camera " + cameraId + " scheduled next segment in " + (segmentDurationMs / 1000) + " seconds (actual delay: " + actualDelayMs + "ms)");
    }

    /**
     * 调度Файл大小проверка（含 Watchdog 逻辑)
     * 
     * Watchdog 机制：
     * - 每 3  сек.проверка一 разФайл大小
     * - Если 连续 3  раз（9 сек.)无写入，触发重建求
     * - 首 раз写入таймаут保护：ЗаписьВкл始后 10  сек.内无写入также触发重建
     */
    private void scheduleFileSizeCheck() {
        // 防御性проверка：确保 Handler Доступно
        if (segmentHandler == null) {
            AppLog.w(TAG, "Camera " + cameraId + " segmentHandler is null, cannot schedule file size check");
            return;
        }
        
        // Отменадо проверка
        if (fileSizeCheckRunnable != null) {
            segmentHandler.removeCallbacks(fileSizeCheckRunnable);
        }

        fileSizeCheckRunnable = () -> {
            if (isRecording.get() && currentFilePath != null) {
                File file = new File(currentFilePath);
                long currentSize = file.exists() ? file.length() : 0;
                long sizeIncrease = currentSize - lastFileSize;
                
                // проверка 否有действует数据写入
                // Выкл键：Файл大小必须超过 MIN_VALID_FILE_SIZE 才算真正有Видео数据
                // 因为 MediaRecorder.start() 会立т.е.写入约 3232 bytes   MP4 Файл头
                boolean hasValidData = currentSize > MIN_VALID_FILE_SIZE;
                boolean hasNewWrite = sizeIncrease > 0;
                
                if (hasValidData) {
                    // Файл大小超过阈值，说明有真正 Видео数据
                    noWriteCount = 0;
                    if (!hasFirstWrite) {
                        hasFirstWrite = true;
                        AppLog.d(TAG, "Camera " + cameraId + " first VALID write detected! Size: " + currentSize + " bytes (>" + MIN_VALID_FILE_SIZE + ")");
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
                    AppLog.d(TAG, "Camera " + cameraId + " file size check: " + currentSize + " bytes (" + (currentSize / 1024) + " KB), increase: " + sizeIncrease + " bytes");
                } else if (hasNewWrite && !hasFirstWrite) {
                    // Файл大小增加但Не 超过阈值（可能只  MP4 Файл头)
                    // 不算首 раздействует写入，продолжитьожидание真正 Видео数据
                    AppLog.d(TAG, "Camera " + cameraId + " file header written: " + currentSize + " bytes, waiting for real video data (need >" + MIN_VALID_FILE_SIZE + ")...");
                } else if (!hasNewWrite) {
                    // 无新写入：增加计数器
                    noWriteCount++;
                    
                    if (currentSize == 0) {
                        AppLog.e(TAG, "Camera " + cameraId + " ERROR: File size is 0! No frames received! (count: " + noWriteCount + "/" + WATCHDOG_NO_WRITE_THRESHOLD + ")");
                    } else {
                        AppLog.w(TAG, "Camera " + cameraId + " WARNING: File size not growing! Current: " + currentSize + " bytes (count: " + noWriteCount + "/" + WATCHDOG_NO_WRITE_THRESHOLD + ")");
                    }
                    
                    // Watchdog：连续 N  раз无写入，触发重建
                    if (noWriteCount >= WATCHDOG_NO_WRITE_THRESHOLD) {
                        AppLog.e(TAG, "Camera " + cameraId + " WATCHDOG TRIGGERED: No write for " + (noWriteCount * FILE_SIZE_CHECK_INTERVAL_MS / 1000) + " seconds, requesting rebuild");
                        requestRecordingRebuild("no_write");
                        return;  // Остановкапроверка，ожидание重建
                    }
                }
                
                lastFileSize = currentSize;
                
                // продолжить一 разпроверка（首 раз写入前用快速间隔，после用нормально间隔)
                // 防御性проверка：确保 Handler 仍然Доступно
                if (segmentHandler != null) {
                    long nextDelay = hasFirstWrite ? FILE_SIZE_CHECK_INTERVAL_MS : FIRST_CHECK_DELAY_MS;
                    segmentHandler.postDelayed(fileSizeCheckRunnable, nextDelay);
                }
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
        
        // 防御性проверка：确保 Handler Доступно
        if (segmentHandler == null) {
            AppLog.w(TAG, "Camera " + cameraId + " segmentHandler is null, cannot schedule first write timeout");
            return;
        }
        
        firstWriteTimeoutRunnable = () -> {
            if (isRecording.get() && !hasFirstWrite) {
                AppLog.e(TAG, "Camera " + cameraId + " FIRST WRITE TIMEOUT: No data written in " + (FIRST_WRITE_TIMEOUT_MS / 1000) + " seconds, requesting rebuild");
                requestRecordingRebuild("first_write_timeout");
            }
        };
        
        segmentHandler.postDelayed(firstWriteTimeoutRunnable, FIRST_WRITE_TIMEOUT_MS);
        AppLog.d(TAG, "Camera " + cameraId + " first write timeout scheduled: " + (FIRST_WRITE_TIMEOUT_MS / 1000) + " seconds");
    }

    /**
     * Отмена首 раз写入таймаутпроверка
     */
    private void cancelFirstWriteTimeout() {
        if (firstWriteTimeoutRunnable != null && segmentHandler != null) {
            segmentHandler.removeCallbacks(firstWriteTimeoutRunnable);
            firstWriteTimeoutRunnable = null;
        } else if (firstWriteTimeoutRunnable != null) {
            // Handler 为 null，只очистка引用
            firstWriteTimeoutRunnable = null;
        }
    }

    /**
     * 求重建Запись（УведомлениеВнешнее)
     */
    private void requestRecordingRebuild(String reason) {
        // ОстановкаТекущийЗапись（不触发нормально  stop 回调)
        isRecording.set(false);
        cancelFileSizeCheck();
        cancelFirstWriteTimeout();
        
        // СбросСтатус为 IDLE（ожиданиеВнешнее重建)
        synchronized (stateLock) {
            state = RecordingState.IDLE;
        }
        
        // УведомлениеВнешнеенеобходимо重建
        if (callback != null) {
            callback.onRecordingRebuildRequested(cameraId, reason);
        }
    }

    /**
     * ОтменаФайл大小проверка
     */
    private void cancelFileSizeCheck() {
        if (fileSizeCheckRunnable != null && segmentHandler != null) {
            segmentHandler.removeCallbacks(fileSizeCheckRunnable);
            fileSizeCheckRunnable = null;
        } else if (fileSizeCheckRunnable != null) {
            // Handler 为 null，只очистка引用
            fileSizeCheckRunnable = null;
        }
    }

    /**
     * 切换 до 一
     * 注意：这 шт.方法необходимо通过回调УведомлениеВнешнее重新конфигурация相机会话
     * 
     * 【重要】为避免 Surface 竞态条件导致 CAPTURE FAILED，分切换流程если：
     * 1. 先УведомлениеВнешнееПауза CaptureSession  Запись输出（onPrepareSegmentSwitch)
     * 2. ожидание 300ms 让 CaptureSession 完全Остановка к 旧 Surface Отправка帧
     * 3. ОстановкаТекущий MediaRecorder
     * 4. 准备新  MediaRecorder
     * 5. УведомлениеВнешнее重新конфигурация会话（onSegmentSwitch)
     */
    private void switchToNextSegment() {
        // 【Статуспроверка】确保Текущий处于ЗаписьСтатус才能切换分
        synchronized (stateLock) {
            if (state != RecordingState.RECORDING) {
                AppLog.w(TAG, "Camera " + cameraId + " cannot switch segment: current state=" + state);
                return;
            }
            // 进入分切换Статус（此Статус stopRecording 会ожиданиеилиОтмена切换)
            state = RecordingState.SWITCHING_SEGMENT;
        }
        
        AppLog.d(TAG, "Camera " + cameraId + " initiating segment switch from segment " + segmentIndex);
        
        // 【Первый步】УведомлениеВнешнееПауза CaptureSession  Запись输出
        // 这会让 CaptureSession Остановка к Текущий  recordSurface Отправка帧
        if (callback != null) {
            AppLog.d(TAG, "Camera " + cameraId + " calling onPrepareSegmentSwitch to pause capture session");
            callback.onPrepareSegmentSwitch(cameraId, segmentIndex);
        }
        
        // 【Второй步】延迟выполнение实际 分切换，ожидание CaptureSession 完全Остановка
        // 300ms 足够让 Camera2 框架завершение帧缓冲区 清空
        // использование可Отмена  Runnable，以便  stopRecording() 时Отмена
        if (segmentHandler == null) {
            AppLog.w(TAG, "Camera " + cameraId + " segmentHandler is null, cannot schedule segment switch");
            synchronized (stateLock) {
                state = RecordingState.IDLE;
            }
            return;
        }
        if (pendingSegmentSwitchRunnable != null) {
            segmentHandler.removeCallbacks(pendingSegmentSwitchRunnable);
        }
        pendingSegmentSwitchRunnable = () -> {
            pendingSegmentSwitchRunnable = null;  // выполнение后очистка引用
            performActualSegmentSwitch();
        };
        // использование switchToPreviewOnlyMode() 后，不再необходимоожидание stopRepeating завершение
        // 50ms 足够让 Camera2 框架处理求切换
        segmentHandler.postDelayed(pendingSegmentSwitchRunnable, 50);
    }
    
    /**
     * выполнение实际 分切换операция（  CaptureSession Пауза后调用)
     */
    private void performActualSegmentSwitch() {
        // 【Статуспроверка】确保Текущий处于分切换Статус
        synchronized (stateLock) {
            if (state != RecordingState.SWITCHING_SEGMENT) {
                AppLog.w(TAG, "Camera " + cameraId + " performActualSegmentSwitch cancelled: current state=" + state);
                return;
            }
        }
        
        // 安全проверка：Если  MediaRecorder 释放，不выполнение切换
        if (mediaRecorder == null) {
            AppLog.w(TAG, "Camera " + cameraId + " performActualSegmentSwitch cancelled: MediaRecorder released");
            synchronized (stateLock) {
                state = RecordingState.IDLE;
            }
            return;
        }
        
        // СохранитьТекущий分 ФайлПуть（завершение Файл)
        String completedFilePath = currentFilePath;
        boolean completedFileValid = false;
        
        try {
            // 【Третий步】ОстановкаТекущий MediaRecorder
            if (mediaRecorder != null) {
                // 诊断：  stop() допроверкаФайл大小
                long fileSizeBeforeStop = 0;
                if (currentFilePath != null) {
                    File file = new File(currentFilePath);
                    fileSizeBeforeStop = file.exists() ? file.length() : 0;
                    AppLog.d(TAG, "Camera " + cameraId + " file size before stop: " + fileSizeBeforeStop + " bytes (" + (fileSizeBeforeStop / 1024) + " KB)");
                }
                
                try {
                    // Если Файл太小（<10KB)，说明 MediaRecorder 没有接Получена команда: 帧，跳过 stop()
                    if (fileSizeBeforeStop < MIN_VALID_FILE_SIZE) {
                        AppLog.e(TAG, "Camera " + cameraId + " file size too small (" + fileSizeBeforeStop + " bytes < " + MIN_VALID_FILE_SIZE + "), MediaRecorder may not be receiving frames. Skipping stop().");
                        isRecording.set(false);
                    } else {
                        mediaRecorder.stop();
                        isRecording.set(false);  // 立т.е.обновлениеСтатус
                        AppLog.d(TAG, "Camera " + cameraId + " stopped segment " + segmentIndex + ": " + currentFilePath);

                        // 验证并Очистка 损坏 Файл
                        validateAndCleanupFile(currentFilePath);
                        completedFileValid = true;  // 标记Файлдействует
                    }
                } catch (RuntimeException e) {
                    AppLog.e(TAG, "Error stopping segment for camera " + cameraId + " (file size was: " + fileSizeBeforeStop + " bytes)", e);
                    isRecording.set(false);  // т.е.使ОшибкатакжеобновлениеСтатус

                    // ОстановкаОшибка，删除损坏 Файл
                    if (currentFilePath != null) {
                        File file = new File(currentFilePath);
                        if (file.exists()) {
                            file.delete();
                            AppLog.w(TAG, "Deleted corrupted segment file: " + currentFilePath);
                        }
                    }
                    completedFilePath = null;  // ФайлУдалено，标记为недействительно
                }
                releaseMediaRecorder();
            }

            // 【第四步】准备一（использование新 时间戳)
            segmentIndex++;
            String nextSegmentPath = generateSegmentPath();
            prepareMediaRecorder(nextSegmentPath, recordWidth, recordHeight);
            currentFilePath = nextSegmentPath;
            recordedFilePaths.add(nextSegmentPath);  // 记录新分Файл

            // Настройкиожидание会话重新конфигурация 标志
            waitingForSessionReconfiguration = true;

            // 【第五步】УведомлениеВнешнеенеобходимо重新конфигурация相机会话（因为 MediaRecorder   Surface 经改变)
            // Внешнеенеобходимо调用 startRecording() 来Запуск新 Запись
            if (callback != null) {
                // 只传递действует завершениеФайлПуть
                callback.onSegmentSwitch(cameraId, segmentIndex, completedFileValid ? completedFilePath : null);
            }

            // 注意：不 这里调用 start()，而 ожиданиеВнешнее重新конфигурация相机会话后调用 startRecording()
            // 这样可以确保新  Surface 经添加 до  CaptureSession 
            AppLog.d(TAG, "Camera " + cameraId + " prepared segment " + segmentIndex + ": " + nextSegmentPath + ", waiting for session reconfiguration");

        } catch (Exception e) {
            AppLog.e(TAG, "Failed to switch segment for camera " + cameraId, e);
            isRecording.set(false);
            waitingForSessionReconfiguration = false;
            // 切换Ошибка，Восстановление до  IDLE Статус
            synchronized (stateLock) {
                state = RecordingState.IDLE;
            }
            if (callback != null) {
                callback.onRecordError(cameraId, "Failed to switch segment: " + e.getMessage());
            }
        }
    }

    /**
     * Начать запись（旧方法，保持совместимость性)
     */
    public boolean startRecording(String filePath, int width, int height) {
        if (prepareRecording(filePath, width, height)) {
            return startRecording();
        }
        return false;
    }

    /**
     * Остановить запись
     */
    public void stopRecording() {
        // 【Статус机проверка】处理不同Статус Остановка求
        synchronized (stateLock) {
            if (state == RecordingState.IDLE) {
                AppLog.w(TAG, "Camera " + cameraId + " is not recording (state=IDLE)");
                return;
            }
            
            if (state == RecordingState.STOPPING) {
                AppLog.w(TAG, "Camera " + cameraId + " is already stopping");
                return;
            }
            
            // Если Выполняется 分切换，Отмена切换задача并продолжитьОстановка
            if (state == RecordingState.SWITCHING_SEGMENT) {
                AppLog.w(TAG, "Camera " + cameraId + " stop requested during segment switch, cancelling switch");
                // Отмена待выполнение 分切换задача
                if (pendingSegmentSwitchRunnable != null && segmentHandler != null) {
                    segmentHandler.removeCallbacks(pendingSegmentSwitchRunnable);
                    pendingSegmentSwitchRunnable = null;
                }
            }
            
            // 进入ОстановкаСтатус
            state = RecordingState.STOPPING;
        }
        
        // Отмена分定时器
        if (segmentRunnable != null && segmentHandler != null) {
            segmentHandler.removeCallbacks(segmentRunnable);
            segmentRunnable = null;
        }
        
        // Отмена待выполнение 分切换задача（再 раз确保Отмена)
        if (pendingSegmentSwitchRunnable != null && segmentHandler != null) {
            segmentHandler.removeCallbacks(pendingSegmentSwitchRunnable);
            pendingSegmentSwitchRunnable = null;
            AppLog.d(TAG, "Camera " + cameraId + " cancelled pending segment switch");
        }
        
        // ОтменаФайл大小проверка и 首 раз写入таймаутпроверка
        cancelFileSizeCheck();
        cancelFirstWriteTimeout();

        // Если Выполняется ожидание会话重新конфигурация，说明MediaRecorder经stop过，只необходимоОчистка Статус
        if (waitingForSessionReconfiguration) {
            AppLog.d(TAG, "Camera " + cameraId + " is waiting for session reconfiguration, skipping stop");
            isRecording.set(false);
            waitingForSessionReconfiguration = false;
            releaseMediaRecorder();

            // 验证并Очистка 所有Запись Файл
            List<String> deletedFiles = validateAndCleanupAllFiles();
            notifyCorruptedFilesDeleted(deletedFiles);

            currentFilePath = null;
            segmentIndex = 0;
            recordedFilePaths.clear();
            // Восстановление до  IDLE Статус
            synchronized (stateLock) {
                state = RecordingState.IDLE;
            }
            if (callback != null) {
                callback.onRecordStop(cameraId);
            }
            return;
        }

        if (!isRecording.get()) {
            AppLog.w(TAG, "Camera " + cameraId + " is not recording");
            synchronized (stateLock) {
                state = RecordingState.IDLE;
            }
            return;
        }

        // 诊断：  stop() допроверкаФайл大小
        long fileSizeBeforeStop = 0;
        if (currentFilePath != null) {
            File file = new File(currentFilePath);
            fileSizeBeforeStop = file.exists() ? file.length() : 0;
            AppLog.d(TAG, "Camera " + cameraId + " file size before stop: " + fileSizeBeforeStop + " bytes (" + (fileSizeBeforeStop / 1024) + " KB)");
        }

        List<String> deletedFiles = new ArrayList<>();
        try {
            if (mediaRecorder != null) {
                // Если Файл太小（<10KB)，说明 MediaRecorder 没有接Получена команда: 帧，跳过 stop()
                if (fileSizeBeforeStop < MIN_VALID_FILE_SIZE) {
                    AppLog.e(TAG, "Camera " + cameraId + " file size too small (" + fileSizeBeforeStop + " bytes < " + MIN_VALID_FILE_SIZE + "), MediaRecorder may not be receiving frames. Skipping stop().");
                } else {
                    mediaRecorder.stop();
                    AppLog.d(TAG, "Camera " + cameraId + " stopped recording: " + currentFilePath + " (total segments: " + (segmentIndex + 1) + ")");
                }
            }
            isRecording.set(false);

            // 验证并Очистка 所有Запись Файл
            deletedFiles = validateAndCleanupAllFiles();

            if (callback != null) {
                callback.onRecordStop(cameraId);
            }
        } catch (RuntimeException e) {
            AppLog.e(TAG, "Failed to stop recording for camera " + cameraId + " (file size was: " + fileSizeBeforeStop + " bytes)", e);
            isRecording.set(false);

            // ЗаписьОшибка，删除损坏 Файл
            if (currentFilePath != null) {
                File file = new File(currentFilePath);
                if (file.exists()) {
                    file.delete();
                    deletedFiles.add(file.getName());
                    AppLog.w(TAG, "Deleted corrupted video file: " + currentFilePath);
                }
            }
        } finally {
            releaseMediaRecorder();
            currentFilePath = null;
            segmentIndex = 0;
            
            // Уведомление损坏Файл 删除
            notifyCorruptedFilesDeleted(deletedFiles);
            recordedFilePaths.clear();
            
            // Восстановление до  IDLE Статус
            synchronized (stateLock) {
                state = RecordingState.IDLE;
            }
        }
    }

    /**
     * 验证并Очистка 所有Запись Файл（包括ТекущийВыполняется Запись Файл)
     * @return  删除 Файл名列表
     */
    private List<String> validateAndCleanupAllFiles() {
        List<String> deletedFiles = new ArrayList<>();
        
        // 计算总Файл数（завершение 分 + ТекущийФайл)
        int totalFiles = recordedFilePaths.size();
        if (currentFilePath != null && !recordedFilePaths.contains(currentFilePath)) {
            totalFiles++;
        }
        
        AppLog.d(TAG, "Camera " + cameraId + " validating " + totalFiles + " files (recorded: " + recordedFilePaths.size() + ", current: " + (currentFilePath != null ? "1" : "0") + ")");
        
        // 验证завершение 分Файл
        for (String filePath : recordedFilePaths) {
            String deletedFileName = validateAndCleanupFile(filePath);
            if (deletedFileName != null) {
                deletedFiles.add(deletedFileName);
            }
        }
        
        // 【重要】验证ТекущийВыполняется Запись Файл（Если существует且Не содержит  recordedFilePaths )
        if (currentFilePath != null && !recordedFilePaths.contains(currentFilePath)) {
            String deletedFileName = validateAndCleanupFile(currentFilePath);
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
     * Уведомление损坏Файл 删除
     */
    private void notifyCorruptedFilesDeleted(List<String> deletedFiles) {
        if (!deletedFiles.isEmpty() && callback != null) {
            callback.onCorruptedFilesDeleted(cameraId, deletedFiles);
        }
    }

    /**
     * 验证并Очистка 损坏 ВидеоФайл
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
            AppLog.w(TAG, "Video file too small: " + filePath + " (size: " + fileSize + " bytes, minimum: " + MIN_VALID_FILE_SIZE + " bytes). Deleting...");
            file.delete();
            return file.getName();
        } else {
            AppLog.d(TAG, "Video file validated: " + filePath + " (size: " + (fileSize / 1024) + " KB)");
            return null;
        }
    }

    /**
     * 释放Запись器
     */
    private void releaseMediaRecorder() {
        // 先清空缓存  Surface
        cachedSurface = null;
        
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
            } catch (IllegalStateException e) {
                // MediaRecorder 可能处于недействительноСтатус（если Error Статус)，忽略此аномалия
                AppLog.w(TAG, "Camera " + cameraId + " MediaRecorder.reset() failed (may be in invalid state): " + e.getMessage());
            }
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                AppLog.w(TAG, "Camera " + cameraId + " MediaRecorder.release() failed: " + e.getMessage());
            }
            mediaRecorder = null;
        }
    }

    /**
     * СбросЗапись器Статус（用于 Watchdog 重建)
     * ОстановкаТекущийЗапись并释放 MediaRecorder，但保留 Handler/Thread 以便重新Начать запись
     */
    public void reset() {
        AppLog.d(TAG, "Camera " + cameraId + " resetting VideoRecorder for rebuild");
        
        // Отмена所有定时задача
        if (segmentHandler != null) {
            if (segmentRunnable != null) {
                segmentHandler.removeCallbacks(segmentRunnable);
                segmentRunnable = null;
            }
            if (pendingSegmentSwitchRunnable != null) {
                segmentHandler.removeCallbacks(pendingSegmentSwitchRunnable);
                pendingSegmentSwitchRunnable = null;
            }
        }
        
        // ОтменаФайл大小проверка и 首 раз写入таймаутпроверка
        cancelFileSizeCheck();
        cancelFirstWriteTimeout();
        
        // 释放 MediaRecorder（但不销毁 Handler/Thread)
        isRecording.set(false);
        waitingForSessionReconfiguration = false;
        releaseMediaRecorder();
        
        // 【重要】验证并Очистка 损坏Файл（ очисткаПуть记录до)
        List<String> deletedFiles = validateAndCleanupAllFiles();
        if (!deletedFiles.isEmpty()) {
            AppLog.w(TAG, "Camera " + cameraId + " cleaned up " + deletedFiles.size() + " corrupted files during reset");
            // УведомлениеВнешнее有损坏Файл 删除
            notifyCorruptedFilesDeleted(deletedFiles);
        }
        
        currentFilePath = null;
        segmentIndex = 0;
        recordedFilePaths.clear();
        
        // Сброс Watchdog Статус
        noWriteCount = 0;
        hasFirstWrite = false;
        lastFileSize = 0;
        
        // 确保СтатусСброс为 IDLE
        synchronized (stateLock) {
            state = RecordingState.IDLE;
        }
        
        // Если  Handler/Thread  销毁，重新创建
        if (segmentHandler == null || segmentThread == null || !segmentThread.isAlive()) {
            AppLog.d(TAG, "Camera " + cameraId + " recreating segment thread/handler");
            if (segmentThread != null) {
                segmentThread.quitSafely();
            }
            segmentThread = new HandlerThread("VideoRecorder-Segment-" + cameraId);
            segmentThread.start();
            segmentHandler = new Handler(segmentThread.getLooper());
        }
        
        AppLog.d(TAG, "Camera " + cameraId + " VideoRecorder reset complete");
    }

    /**
     * 释放资源
     */
    public void release() {
        // Отмена分定时器
        if (segmentHandler != null && segmentRunnable != null) {
            segmentHandler.removeCallbacks(segmentRunnable);
            segmentRunnable = null;
        }
        
        // Отмена待выполнение 分切换задача
        if (segmentHandler != null && pendingSegmentSwitchRunnable != null) {
            segmentHandler.removeCallbacks(pendingSegmentSwitchRunnable);
            pendingSegmentSwitchRunnable = null;
        }
        
        // ОтменаФайл大小проверка и 首 раз写入таймаутпроверка
        cancelFileSizeCheck();
        cancelFirstWriteTimeout();

        // 只有 真正Запись且mediaRecorder不为null时才调用stopRecording
        if (isRecording.get() && mediaRecorder != null) {
            stopRecording();
        } else {
            // 直接Очистка Статус
            isRecording.set(false);
            waitingForSessionReconfiguration = false;
            releaseMediaRecorder();
            currentFilePath = null;
            segmentIndex = 0;
            // 确保СтатусСброс为 IDLE
            synchronized (stateLock) {
                state = RecordingState.IDLE;
            }
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
    }
    
    /**
     * ПолучениеТекущийЗаписьСтатус
     * @return Текущий  RecordingState
     */
    public RecordingState getState() {
        synchronized (stateLock) {
            return state;
        }
    }
}
