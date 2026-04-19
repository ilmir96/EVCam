package com.kooo.evcam.camera;


import com.kooo.evcam.AppConfig;
import com.kooo.evcam.AppLog;
import com.kooo.evcam.StorageHelper;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Build;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * 单 шт.Камерауправление类
 */
public class SingleCamera {
    private static final String TAG = "SingleCamera";

    private final Context context;
    private final String cameraId;
    private TextureView textureView;
    private CameraCallback callback;
    private String cameraPosition;  // КамераПозиция（front/back/left/right)
    private int customRotation = 0;  // 自定义Поворот 角度（только用于Своя модель)

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private Size previewSize;
    private Surface recordSurface;  // ЗаписьSurface
    private Surface mainFloatingSurface; // 主屏悬浮窗Surface
    private android.graphics.SurfaceTexture mainFloatingSurfaceTexture; // 主屏悬浮窗SurfaceTexture（用于Настройкиbuffer尺寸)
    private Surface secondaryDisplaySurface; // 副屏预览Surface
    private android.graphics.SurfaceTexture secondaryDisplaySurfaceTexture; // 副屏SurfaceTexture（用于Настройкиbuffer尺寸)
    private OutputConfiguration activePreviewConfig; // Всего 享预览конфигурация，用于动态 Surface 增减
    private Surface previewSurface;  // 预览Surface（缓存以避免重复创建)
    private ImageReader imageReader;  // 用于Фото ImageReader
    private boolean singleOutputMode = false;  // 单一输出режим（用于не поддерживается多 кам.输出 车机平台)
    
    // 鱼眼矫正
    private FisheyeCorrector fisheyeCorrector;
    private boolean fisheyePrimaryIsFloating = false; // 鱼眼矫正主输出是否为悬浮窗 Surface（无 TextureView 时）
    
    // 亮度/Шумоподавление调节相Выкл
    private CaptureRequest.Builder currentRequestBuilder;  // Текущий 求构建器（用于实时обновление参数)
    private CameraCharacteristics cameraCharacteristics;  // Камера特性（缓存)
    private boolean imageAdjustEnabled = false;  //  否Включить亮度/Шумоподавление调节
    
    // Текущий相机实际использование 参数（ от  CaptureResult 读取)
    private int actualExposureCompensation = 0;
    private int actualAwbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO;
    private int actualEdgeMode = CameraMetadata.EDGE_MODE_OFF;
    private int actualNoiseReductionMode = CameraMetadata.NOISE_REDUCTION_MODE_OFF;
    private int actualEffectMode = CameraMetadata.CONTROL_EFFECT_MODE_OFF;
    private int actualTonemapMode = CameraMetadata.TONEMAP_MODE_FAST;
    private boolean hasReadActualParams = false;  //  否读取过实际参数

    // отладка：帧捕获监控
    private long frameCount = 0;  // 总帧数
    private long lastFrameLogTime = 0;  //  раз输出帧计数 时间
    private static final long FRAME_LOG_INTERVAL_MS = 5000;  // 每5 сек.输出一 раз帧计数

    // 实时 FPS（1 сек.滚动窗口，供отладкаИнформация展示)
    private float currentFps = 0f;
    private long fpsWindowFrameCount = 0;
    private long fpsWindowStartTime = 0;

    private long lastFrameTimestampMs = 0;
    private long lastStallRecoveryMs = 0;
    private int stallRecoveryLevel = 0;
    private Runnable healthCheckRunnable;
    private static final long HEALTH_CHECK_INTERVAL_MS = 1000;
    private static final long STALL_TIMEOUT_MS = 2500;
    private static final long MIN_RECOVERY_INTERVAL_MS = 2000;

    private boolean shouldReconnect = false;  //  否应该重连
    private int reconnectAttempts = 0;  // 重连попытка раз数
    private static final int MAX_RECONNECT_ATTEMPTS = 90;  // максимум重连 раз数（90 раз × 2 сек. = 3 мин.)
    private static final long RECONNECT_DELAY_MS = 2000;  // 重连延迟（毫 сек.)
    private long reconnectDelayFloorMs = 0;
    private Runnable reconnectRunnable;  // 重连задача
    private boolean isPausedByLifecycle = false;  //  否因生命周期Пауза（用于区分主动Закрыто и Система剥夺)
    private boolean isReconnecting = false;  //  否Выполняется 重连（防止多 шт.重连задача同时Работа)
    private volatile boolean isOpening = false;  //  否Выполняется открыть（防止并行触发时重复调用 openCamera)
    private volatile boolean deferSessionCreation = false;  // 延迟 Session 创建（ и  Surface 并行открыть相机时использование)
    private final Object reconnectLock = new Object();  // 重连锁
    private boolean isPrimaryInstance = true;  //  否 主实例（用于多实例Всего 享同一 шт.cameraId时，只有主实例负责重连)
    private boolean isConfiguring = false; // 新增：标记 否Выполняется конфигурация
    private boolean isPendingReconfiguration = false; // 新增：标记 否有待处理 конфигурация求
    private boolean isSessionClosing = false; // 新增：标记 Session  否Выполняется Закрыто
    private int configFailRetryCount = 0; // session конфигурацияОшибка重试计数
    private static final int MAX_CONFIG_FAIL_RETRIES = 3; // максимум重试 раз数
    private final Object sessionLock = new Object(); // 新增：用于同步 Session операция

    // 相机открыть后 一 раз性回调（用于副屏ожидание相机绪后立т.е.绑定 Surface)
    private final java.util.List<Runnable> onCameraOpenedCallbacks = new java.util.ArrayList<>();

    public SingleCamera(Context context, String cameraId, TextureView textureView) {
        this.context = context;
        this.cameraId = cameraId;
        this.textureView = textureView;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public void setCallback(CameraCallback callback) {
        this.callback = callback;
    }

    public void setCameraPosition(String position) {
        this.cameraPosition = position;

        // Если  Задняя камера，Приложение左右镜像变换
        if ("back".equals(position) && textureView != null) {
            applyMirrorTransform();
        }
    }

    public void setTextureView(TextureView textureView) {
        this.textureView = textureView;
        clearPreviewSurface();
        if ("back".equals(cameraPosition) && this.textureView != null) {
            applyMirrorTransform();
        }
        if (customRotation != 0 && this.textureView != null && this.textureView.isAvailable()) {
            applyCustomRotation();
        }
    }

    public void clearPreviewSurface() {
        if (previewSurface != null) {
            try {
                previewSurface.release();
            } catch (Exception e) {
            }
            previewSurface = null;
        }
        releaseFisheyeCorrector();
    }

    /**
     * Настройки自定义Поворот 角度（только用于Своя модель)
     * @param rotation Поворот 角度（0/90/180/270)
     */
    public void setCustomRotation(int rotation) {
        this.customRotation = rotation;
        AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") custom rotation set to " + rotation + "°");

        // Если TextureView经Доступно，立т.е.ПриложениеПоворот 
        if (textureView != null && textureView.isAvailable()) {
            applyCustomRotation();
        }
    }

    /**
     * Настройки 否为主实例（用于多实例Всего 享同一 шт.cameraId时)
     * 只有主实例负责открытьКамера и 重连， от 属实例只负责显示
     */
    public void setPrimaryInstance(boolean isPrimary) {
        this.isPrimaryInstance = isPrimary;
        if (!isPrimary) {
            //  от 属实例不необходимо重连
            synchronized (reconnectLock) {
                shouldReconnect = false;
            }
        }
        AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") set as " + (isPrimary ? "PRIMARY" : "SECONDARY") + " instance");
    }

    /**
     * проверка 否 主实例
     */
    public boolean isPrimaryInstance() {
        return isPrimaryInstance;
    }

    /**
     * Приложение左右镜像变换 до TextureView
     */
    private void applyMirrorTransform() {
        if (textureView == null) {
            return;
        }

        //  主线程выполнениеUIоперация
        textureView.post(() -> {
            android.graphics.Matrix matrix = new android.graphics.Matrix();

            // ПолучениеTextureView 心点
            float centerX = textureView.getWidth() / 2f;
            float centerY = textureView.getHeight() / 2f;

            // Приложение水平镜像：scaleX = -1
            matrix.setScale(-1f, 1f, centerX, centerY);

            textureView.setTransform(matrix);
            AppLog.d(TAG, "Camera " + cameraId + " (back) applied mirror transform");
        });
    }

    /**
     * Приложение自定义Поворот 角度（только用于Своя модель)
     */
    private void applyCustomRotation() {
        if (textureView == null || customRotation == 0) {
            return;
        }

        //  主线程выполнениеUIоперация
        textureView.post(() -> {
            android.graphics.Matrix matrix = new android.graphics.Matrix();

            // ПолучениеTextureView 心点
            float centerX = textureView.getWidth() / 2f;
            float centerY = textureView.getHeight() / 2f;

            // ПриложениеПоворот 
            matrix.setRotate(customRotation, centerX, centerY);

            // Если  Задняя камера，还необходимоПриложение镜像
            if ("back".equals(cameraPosition)) {
                matrix.postScale(-1f, 1f, centerX, centerY);
            }

            textureView.setTransform(matrix);
            AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") applied custom rotation: " + customRotation + "°");
        });
    }

    public String getCameraId() {
        return cameraId;
    }

    /**
     * Камера硬件 否открыть
     */
    public boolean isCameraOpened() {
        return cameraDevice != null;
    }

    /**
     * 注册一 раз性回调：相机открыть后立т.е.выполнение（  createCameraPreviewSession до)。
     * Если 相机经открыть，立т.е.выполнение。
     */
    public void addOnCameraOpenedCallback(Runnable callback) {
        if (cameraDevice != null) {
            callback.run();
            return;
        }
        synchronized (onCameraOpenedCallbacks) {
            onCameraOpenedCallbacks.add(callback);
        }
    }

    private void fireOnCameraOpenedCallbacks() {
        java.util.List<Runnable> copy;
        synchronized (onCameraOpenedCallbacks) {
            if (onCameraOpenedCallbacks.isEmpty()) return;
            copy = new java.util.ArrayList<>(onCameraOpenedCallbacks);
            onCameraOpenedCallbacks.clear();
        }
        for (Runnable cb : copy) {
            try {
                cb.run();
            } catch (Exception e) {
                AppLog.e(TAG, "Camera " + cameraId + " onCameraOpened callback error: " + e.getMessage());
            }
        }
    }

    /**
     * Получение预览Разрешение
     */
    public Size getPreviewSize() {
        return previewSize;
    }

    public boolean isSecondaryDisplaySurfaceBound(Surface surface) {
        return surface != null && secondaryDisplaySurface == surface && secondaryDisplaySurface.isValid();
    }

    /**
     * Настройки单一输出режим（用于не поддерживается多 кам.输出 车机平台，если L6/L7)
     *  此режим，Запись时只использование MediaRecorder Surface，不использование TextureView Surface
     * 这会导致Запись期间预览冻结，但能确保Записьнормально工作
     */
    public void setSingleOutputMode(boolean enabled) {
        this.singleOutputMode = enabled;
        AppLog.d(TAG, "Camera " + cameraId + " single output mode: " + (enabled ? "ENABLED" : "DISABLED"));
    }

    /**
     * проверка 否Включить单一输出режим
     */
    public boolean isSingleOutputMode() {
        return singleOutputMode;
    }

    // ТекущийЗаписьрежим（用于отладкарежим区分)
    private boolean isCodecRecording = false;

    /**
     * НастройкиЗаписьSurface
     */
    public void setRecordSurface(Surface surface) {
        this.recordSurface = surface;
        if (surface != null) {
            AppLog.d(TAG, "Record surface set for camera " + cameraId + ": " + surface + ", isValid=" + surface.isValid());
        } else {
            AppLog.w(TAG, "Record surface set to NULL for camera " + cameraId);
        }
    }

    /**
     * НастройкиЗаписьSurface（带режим标识)
     * @param surface ЗаписьSurface
     * @param isCodec true 表示 Codec режим，false 表示 MediaRecorder режим
     */
    public void setRecordSurface(Surface surface, boolean isCodec) {
        this.recordSurface = surface;
        this.isCodecRecording = isCodec;
        if (surface != null) {
            AppLog.d(TAG, "Record surface set for camera " + cameraId + ": " + surface + 
                    ", isValid=" + surface.isValid() + ", mode=" + (isCodec ? "Codec" : "MediaRecorder"));
        } else {
            AppLog.w(TAG, "Record surface set to NULL for camera " + cameraId);
        }
    }

    /**
     * Настройки主屏悬浮窗Surface
     */
    public void setMainFloatingSurface(Surface surface) {
        setMainFloatingSurface(surface, null);
    }

    /**
     * Настройки主屏悬浮窗Surface（带SurfaceTexture引用，用于 创建Session时统一Настройкиbuffer尺寸)
     */
    public void setMainFloatingSurface(Surface surface, android.graphics.SurfaceTexture surfaceTexture) {
        this.mainFloatingSurface = surface;
        this.mainFloatingSurfaceTexture = surfaceTexture;
        // 鱼眼режим：очистка时立т.е. от  FisheyeCorrector 移除 EGL 输出，
        // 释放 native window Подключение，确保新Камера  FisheyeCorrector 能УспешноПодключение
        if (surface == null && fisheyeCorrector != null && fisheyeCorrector.isInitialized()) {
            if (backgroundHandler != null) {
                backgroundHandler.post(() -> {
                    if (fisheyeCorrector != null) fisheyeCorrector.removeOutputSurface("mainFloating");
                });
            }
        }
        if (surface != null) {
            AppLog.d(TAG, "Main floating surface set for camera " + cameraId + ": " + surface + ", isValid=" + surface.isValid());
        } else {
            AppLog.d(TAG, "Main floating surface cleared for camera " + cameraId);
        }
    }

    /**
     * Настройки副屏显示Surface
     */
    public void setSecondaryDisplaySurface(Surface surface) {
        setSecondaryDisplaySurface(surface, null);
    }

    /**
     * Настройки副屏显示Surface（带SurfaceTexture引用，用于 创建Session时统一Настройкиbuffer尺寸)
     */
    public void setSecondaryDisplaySurface(Surface surface, android.graphics.SurfaceTexture surfaceTexture) {
        this.secondaryDisplaySurface = surface;
        this.secondaryDisplaySurfaceTexture = surfaceTexture;
        // 鱼眼режим：очистка时立т.е. от  FisheyeCorrector 移除 EGL 输出，
        // 释放 native window Подключение，确保新Камера  FisheyeCorrector 能УспешноПодключение
        if (surface == null && fisheyeCorrector != null && fisheyeCorrector.isInitialized()) {
            if (backgroundHandler != null) {
                backgroundHandler.post(() -> {
                    if (fisheyeCorrector != null) fisheyeCorrector.removeOutputSurface("secondaryDisplay");
                });
            }
        }
        if (surface != null) {
            AppLog.d(TAG, "Secondary display surface set for camera " + cameraId + ": " + surface + ", isValid=" + surface.isValid());
        } else {
            AppLog.d(TAG, "Secondary display surface cleared for camera " + cameraId);
        }
    }

    /**
     * Настройки副屏预览Surface (保留совместимость性)
     * @param surface 副屏预览Surface
     * @deprecated использование setMainFloatingSurface или setSecondaryDisplaySurface
     */
    @Deprecated
    public void setSecondarySurface(Surface surface) {
        setSecondaryDisplaySurface(surface);
    }

    /**
     * очисткаЗаписьSurface
     */
    public void clearRecordSurface() {
        this.recordSurface = null;
        AppLog.d(TAG, "Record surface cleared for camera " + cameraId);
    }

    /**
     * Пауза к Запись Surface Отправка帧（旧方法，保留совместимость性)
     * 注意：此方法会Остановка整 шт.预览，导致画面卡顿，建议использование switchToPreviewOnlyMode() 代替
     */
    public void pauseRecordSurface() {
        if (captureSession != null) {
            try {
                // Остановка к 所有 Surface（包括 recordSurface)Отправка帧
                captureSession.stopRepeating();
                AppLog.d(TAG, "Camera " + cameraId + " paused recording surface (stopped repeating request)");
            } catch (CameraAccessException e) {
                AppLog.e(TAG, "Camera " + cameraId + " failed to pause recording surface", e);
            } catch (IllegalStateException e) {
                // Session 可能经Закрыто
                AppLog.w(TAG, "Camera " + cameraId + " session already closed when trying to pause");
            }
        } else {
            AppLog.w(TAG, "Camera " + cameraId + " captureSession is null, cannot pause recording surface");
        }
    }

    /**
     * 切换 до только预览режим（优化 分切换方法)
     * 
     *  и  pauseRecordSurface() 不同，此方法不会Остановка预览，而 ：
     * 1. 创建一 шт.只содержит预览 Surface  新求
     * 2. продолжить к 预览 Surface Отправка帧（预览不卡顿)
     * 3. Остановка к Запись Surface Отправка帧（安全Остановка MediaRecorder)
     * 
     * @return true Если Успешно切换，false Если Ошибка（将回退 до  pauseRecordSurface)
     */
    public boolean switchToPreviewOnlyMode() {
        if (captureSession == null || cameraDevice == null || previewSurface == null) {
            AppLog.w(TAG, "Camera " + cameraId + " cannot switch to preview-only mode: session/device/surface not ready");
            // 回退 до 旧方法
            pauseRecordSurface();
            return false;
        }

        try {
            // 创建一 шт.只содержит预览 Surface  求
            CaptureRequest.Builder previewOnlyBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewOnlyBuilder.addTarget(previewSurface);
            
            // ПриложениеТекущий 图像调节参数（Если Включить)
            if (imageAdjustEnabled && currentRequestBuilder != null) {
                // 复制Выкл键参数
                try {
                    Integer exposure = currentRequestBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
                    if (exposure != null) {
                        previewOnlyBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure);
                    }
                    Integer awbMode = currentRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE);
                    if (awbMode != null) {
                        previewOnlyBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
                    }
                } catch (Exception e) {
                    // 忽略参数复制Ошибка
                }
            }
            
            // 替换Текущий 重复求（预览продолжить，但不再 к Запись Surface Отправка帧)
            captureSession.setRepeatingRequest(previewOnlyBuilder.build(), null, backgroundHandler);
            AppLog.d(TAG, "Camera " + cameraId + " switched to preview-only mode (preview continues, recording paused)");
            return true;
            
        } catch (CameraAccessException e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to switch to preview-only mode", e);
            // 回退 до 旧方法
            pauseRecordSurface();
            return false;
        } catch (IllegalStateException e) {
            AppLog.w(TAG, "Camera " + cameraId + " session closed when switching to preview-only mode");
            return false;
        } catch (IllegalArgumentException e) {
            // 某些设备可能не поддерживается动态切换求目标
            AppLog.w(TAG, "Camera " + cameraId + " device may not support dynamic request change: " + e.getMessage());
            pauseRecordSurface();
            return false;
        }
    }

    public Surface getSurface() {
        if (textureView != null && textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null) {
                // 缓存 Surface 以避免重复创建 и 资源泄漏
                if (previewSurface == null) {
                    previewSurface = new Surface(surfaceTexture);
                    AppLog.d(TAG, "Camera " + cameraId + " created new preview surface");
                }
                return previewSurface;
            }
        }
        return null;
    }

    /**
     * Выбрать最优Разрешение
     * 根据用户конфигурация 目标Разрешение进行匹配：
     * - По умолчанию：优先1280x800，否则最接近 
     * - 指定Разрешение：优先精确匹配，否则最接近 
     */
    private Size chooseOptimalSize(Size[] sizes) {
        //  от конфигурацияПолучение目标Разрешение
        AppConfig appConfig = new AppConfig(context);
        String targetResolution = appConfig.getTargetResolution();
        
        int targetWidth;
        int targetHeight;
        
        if (AppConfig.RESOLUTION_DEFAULT.equals(targetResolution)) {
            // По умолчанию：1280x800 (guardappиспользование Разрешение)
            targetWidth = 1280;
            targetHeight = 800;
            AppLog.d(TAG, "Camera " + cameraId + " using default target resolution: " + targetWidth + "x" + targetHeight);
        } else {
            // 用户指定 Разрешение
            int[] parsed = AppConfig.parseResolution(targetResolution);
            if (parsed != null) {
                targetWidth = parsed[0];
                targetHeight = parsed[1];
                AppLog.d(TAG, "Camera " + cameraId + " using user-specified target resolution: " + targetWidth + "x" + targetHeight);
            } else {
                // 解析Ошибка，回退 до По умолчанию
                targetWidth = 1280;
                targetHeight = 800;
                AppLog.w(TAG, "Camera " + cameraId + " failed to parse resolution '" + targetResolution + "', using default 1280x800");
            }
        }

        // 首先попытка找 до 精确匹配
        for (Size size : sizes) {
            if (size.getWidth() == targetWidth && size.getHeight() == targetHeight) {
                AppLog.d(TAG, "Camera " + cameraId + " found exact match: " + targetWidth + "x" + targetHeight);
                return size;
            }
        }

        // 找 до 最接近目标Разрешение 
        Size bestSize = null;
        int minDiff = Integer.MAX_VALUE;

        for (Size size : sizes) {
            int width = size.getWidth();
            int height = size.getHeight();

            // 计算 и 目标Разрешение 差距
            int diff = Math.abs(targetWidth - width) + Math.abs(targetHeight - height);
            if (diff < minDiff) {
                minDiff = diff;
                bestSize = size;
            }
        }

        if (bestSize == null) {
            // Если 还 没找 до ，использованиеПервый шт.ДоступноРазрешение
            bestSize = sizes[0];
            AppLog.d(TAG, "Camera " + cameraId + " using first available size: " + bestSize.getWidth() + "x" + bestSize.getHeight());
        } else {
            AppLog.d(TAG, "Camera " + cameraId + " selected closest match: " + bestSize.getWidth() + "x" + bestSize.getHeight() + 
                    " (target was " + targetWidth + "x" + targetHeight + ")");
        }

        return bestSize;
    }

    /**
     * ЗапускФоновый режим线程
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera-" + cameraId);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /**
     * ОстановкаФоновый режим线程
     * 添加таймаут保护 и 完善 Очистка 逻辑
     */
    private static final long THREAD_JOIN_TIMEOUT_MS = 2000;  // 2 сек.таймаут
    
    private void stopBackgroundThread() {
        if (backgroundThread == null) {
            return;
        }
        
        backgroundThread.quitSafely();
        
        try {
            // использованиетаймаут  join，避免无限阻塞
            backgroundThread.join(THREAD_JOIN_TIMEOUT_MS);
            
            // проверка线程 否仍 Работа
            if (backgroundThread.isAlive()) {
                AppLog.w(TAG, "Camera " + cameraId + " background thread did not terminate in time, interrupting");
                backgroundThread.interrupt();
                // 再 一 раз机会（短таймаут)
                backgroundThread.join(500);
                
                if (backgroundThread.isAlive()) {
                    AppLog.e(TAG, "Camera " + cameraId + " background thread still alive after interrupt");
                }
            }
        } catch (InterruptedException e) {
            AppLog.e(TAG, "Camera " + cameraId + " interrupted while stopping background thread", e);
            // Восстановление断标志，让层知道发生断
            Thread.currentThread().interrupt();
        } finally {
            // 无论Успешно и 否всеОчистка 引用，避免内存泄漏
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    private void startHealthMonitor() {
        stopHealthMonitor();
        if (backgroundHandler == null) {
            return;
        }
        healthCheckRunnable = () -> {
            Handler handler = backgroundHandler;
            if (handler == null) {
                return;
            }
            if (cameraDevice == null || captureSession == null) {
                stopHealthMonitor();
                return;
            }
            if (isPausedByLifecycle) {
                Runnable next = healthCheckRunnable;
                if (next != null) {
                    handler.postDelayed(next, HEALTH_CHECK_INTERVAL_MS);
                }
                return;
            }
            synchronized (sessionLock) {
                if (isConfiguring || isSessionClosing) {
                    Runnable next = healthCheckRunnable;
                    if (next != null) {
                        handler.postDelayed(next, HEALTH_CHECK_INTERVAL_MS);
                    }
                    return;
                }
            }
            long now = System.currentTimeMillis();
            long last = lastFrameTimestampMs;
            boolean isStalled = last > 0 && (now - last) > STALL_TIMEOUT_MS;
            if (isStalled) {
                if (now - lastStallRecoveryMs >= MIN_RECOVERY_INTERVAL_MS) {
                    lastStallRecoveryMs = now;
                    if (stallRecoveryLevel == 0) {
                        stallRecoveryLevel = 1;
                        AppLog.w(TAG, "Camera " + cameraId + " stalled (" + (now - last) + "ms), recreating session");
                        recreateSession();
                    } else {
                        stallRecoveryLevel++;
                        AppLog.w(TAG, "Camera " + cameraId + " stalled (" + (now - last) + "ms), force reopening (level " + stallRecoveryLevel + ")");
                        forceReopen();
                    }
                }
            } else {
                stallRecoveryLevel = 0;
            }
            Runnable next = healthCheckRunnable;
            if (next != null) {
                handler.postDelayed(next, HEALTH_CHECK_INTERVAL_MS);
            }
        };
        backgroundHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
    }

    private void stopHealthMonitor() {
        if (backgroundHandler != null && healthCheckRunnable != null) {
            backgroundHandler.removeCallbacks(healthCheckRunnable);
        }
        healthCheckRunnable = null;
        stallRecoveryLevel = 0;
    }

    public long getLastFrameTimestampMs() {
        return lastFrameTimestampMs;
    }

    /**
     * ПолучениеТекущий实时 FPS（1 сек.滚动窗口)
     */
    public float getCurrentFps() {
        return currentFps;
    }

    /**
     * открытьКамера
     */
    public void openCamera() {
        // Если 不 主实例，不выполнениеоткрытьоперация
        if (!isPrimaryInstance) {
            AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") is SECONDARY instance, skipping openCamera");
            return;
        }

        // 经открыть，不重复открыть
        if (cameraDevice != null) {
            AppLog.d(TAG, "Camera " + cameraId + " already opened, skipping openCamera");
            return;
        }

        // Выполняется открыть，不重复触发
        if (isOpening) {
            AppLog.d(TAG, "Camera " + cameraId + " already opening, skipping duplicate openCamera");
            return;
        }
        isOpening = true;
        
        synchronized (reconnectLock) {
            // 安全措施：Очистка 可能残留 Запись Surface 引用（防止 Surface abandoned Ошибка)
            // 放 同步块内，避免 и  setRecordSurface()  竞态条件
            if (recordSurface != null) {
                AppLog.w(TAG, "Camera " + cameraId + " found stale recordSurface on open, clearing it");
                recordSurface = null;
            }
            
            // Если 经 重连，忽略新 открыть求
            if (isReconnecting) {
                AppLog.d(TAG, "Camera " + cameraId + " already reconnecting, ignoring openCamera call");
                isOpening = false;
                return;
            }
            
            AppLog.d(TAG, "openCamera: Starting for camera " + cameraId + " (PRIMARY instance)");
            shouldReconnect = true;  // Включитьавтоматически重连
            reconnectAttempts = 0;  // Сброс重连计数
        }
        
        try {
            startBackgroundThread();

            // 验证КамераID 否существует
            String[] availableCameraIds = cameraManager.getCameraIdList();
            boolean cameraExists = false;
            for (String id : availableCameraIds) {
                if (id.equals(cameraId)) {
                    cameraExists = true;
                    break;
                }
            }

            if (!cameraExists) {
                AppLog.e(TAG, "Camera ID " + cameraId + " does not exist on this device. Available IDs: " +
                         java.util.Arrays.toString(availableCameraIds));
                if (callback != null) {
                    callback.onCameraError(cameraId, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE);
                }
                isOpening = false;
                return;
            }

            // ПолучениеКамера特性（验证Камера 否真正Доступно)
            CameraCharacteristics characteristics;
            try {
                characteristics = cameraManager.getCameraCharacteristics(cameraId);
            } catch (Exception e) {
                AppLog.e(TAG, "Camera " + cameraId + " failed to get characteristics - camera may be virtual/invalid", e);
                if (callback != null) {
                    callback.onCameraError(cameraId, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE);
                }
                synchronized (reconnectLock) {
                    shouldReconnect = false;  // недействительноКамера不应重连
                }
                isOpening = false;
                return;
            }
            
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                // 优先использование SurfaceTexture  输出尺寸
                Size[] sizes = map.getOutputSizes(ImageFormat.PRIVATE);
                if (sizes == null || sizes.length == 0) {
                    sizes = map.getOutputSizes(SurfaceTexture.class);
                    if (sizes != null && sizes.length > 0) {
                        AppLog.w(TAG, "Camera " + cameraId + " no PRIVATE sizes, fallback to SurfaceTexture sizes");
                    }
                }
                if (sizes == null || sizes.length == 0) {
                    AppLog.e(TAG, "Camera " + cameraId + " has no output sizes for PRIVATE/SurfaceTexture - camera may be virtual/invalid");
                    if (callback != null) {
                        callback.onCameraError(cameraId, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE);
                    }
                    synchronized (reconnectLock) {
                        shouldReconnect = false;  // недействительноКамера不应重连
                    }
                    isOpening = false;
                    return;
                }

                // 打印所有ДоступноРазрешение
                AppLog.d(TAG, "Camera " + cameraId + " available sizes:");
                for (int i = 0; i < Math.min(sizes.length, 10); i++) {
                    AppLog.d(TAG, "  [" + i + "] " + sizes[i].getWidth() + "x" + sizes[i].getHeight());
                }

                // Выбрать合适 Разрешение
                previewSize = chooseOptimalSize(sizes);
                AppLog.d(TAG, "Camera " + cameraId + " selected preview size: " + previewSize);

                // 不 这里инициализацияImageReader，改为Фото时按需创建
                // 这样可以避免占用额外 缓冲区，防止超过Система限制(4 шт.buffer)
                AppLog.d(TAG, "Camera " + cameraId + " ImageReader will be created on demand when taking picture");

                // Уведомление回调预览尺寸确定
                if (callback != null && previewSize != null) {
                    callback.onPreviewSizeChosen(cameraId, previewSize);
                }
            } else {
                AppLog.e(TAG, "Camera " + cameraId + " StreamConfigurationMap is null - camera may be virtual/invalid!");
                if (callback != null) {
                    callback.onCameraError(cameraId, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE);
                }
                synchronized (reconnectLock) {
                    shouldReconnect = false;  // недействительноКамера不应重连
                }
                isOpening = false;
                return;
            }

            boolean textureAvailable = textureView != null && textureView.isAvailable();
            AppLog.d(TAG, "Camera " + cameraId + " TextureView available: " + textureAvailable);
            if (textureView != null && textureView.getSurfaceTexture() != null) {
                AppLog.d(TAG, "Camera " + cameraId + " SurfaceTexture exists");
            }

            // открытьКамера
            AppLog.d(TAG, "Camera " + cameraId + " calling openCamera...");
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            isOpening = false;
            AppLog.e(TAG, "Failed to open camera " + cameraId, e);
            if (callback != null) {
                callback.onCameraError(cameraId, -1);
            }
            // попытка重连（проверка 否经 重连)
            synchronized (reconnectLock) {
                if (shouldReconnect && !isReconnecting) {
                    scheduleReconnect();
                }
            }
        } catch (SecurityException e) {
            isOpening = false;
            AppLog.e(TAG, "No camera permission", e);
            if (callback != null) {
                callback.onCameraError(cameraId, -2);
            }
        } catch (IllegalArgumentException e) {
            isOpening = false;
            // 某些设备 открытьнедействительноКамера时会抛出 IllegalArgumentException
            AppLog.e(TAG, "Camera " + cameraId + " invalid argument - camera may be virtual/invalid", e);
            if (callback != null) {
                callback.onCameraError(cameraId, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE);
            }
            synchronized (reconnectLock) {
                shouldReconnect = false;  // недействительноКамера不应重连
            }
        } catch (RuntimeException e) {
            isOpening = false;
            // 捕获所有ДругоеРабота时аномалия，防止Приложение崩溃
            AppLog.e(TAG, "Camera " + cameraId + " runtime exception - camera may be virtual/invalid", e);
            if (callback != null) {
                callback.onCameraError(cameraId, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE);
            }
            synchronized (reconnectLock) {
                shouldReconnect = false;  // аномалия情况不应重连
            }
        }
    }

    /**
     * открыть相机，但延迟 Session 创建。
     * 用于 и  Surface 创建并行：camera открыть后不立т.е.创建 Session，
     * ожидание setMainFloatingSurface 后再创建，避免没有 floating Surface  空 Session необходимо重建。
     */
    public void openCameraDeferred() {
        if (cameraDevice != null) return; // открыть，无需延迟
        deferSessionCreation = true;
        openCamera();
    }

    /**
     * 调度重连задача
     */
    private void scheduleReconnect() {
        // Если 不 主实例，不выполнение重连
        if (!isPrimaryInstance) {
            AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") is SECONDARY instance, skipping reconnect");
            return;
        }
        
        synchronized (reconnectLock) {
            // проверка 否разрешить重连
            if (!shouldReconnect) {
                AppLog.d(TAG, "Camera " + cameraId + " reconnect disabled, skipping");
                return;
            }
            
            // Если 经 重连，忽略新 重连求
            if (isReconnecting) {
                AppLog.d(TAG, "Camera " + cameraId + " already reconnecting, skipping new request");
                return;
            }

            reconnectAttempts++;
            isReconnecting = true;
            long delayMs = Math.max(getReconnectDelayMs(reconnectAttempts), reconnectDelayFloorMs);
            AppLog.d(TAG, "Camera " + cameraId + " scheduling reconnect attempt " + reconnectAttempts + " in " + delayMs + "ms");

            // Отменадо 重连задача
            if (reconnectRunnable != null && backgroundHandler != null) {
                backgroundHandler.removeCallbacks(reconnectRunnable);
            }

            // 创建新 重连задача
            reconnectRunnable = () -> {
                synchronized (reconnectLock) {
                    try {
                        // 确保до 资源Очистка （捕获并忽略аномалия)
                        try {
                            if (captureSession != null) {
                                captureSession.close();
                                captureSession = null;
                            }
                        } catch (Exception e) {
                            // 忽略Закрытоsession时 аномалия（车机HAL可能не поддерживается某些операция)
                            AppLog.d(TAG, "Camera " + cameraId + " ignored exception while closing session: " + e.getMessage());
                        }

                        try {
                            if (cameraDevice != null) {
                                cameraDevice.close();
                                cameraDevice = null;
                            }
                        } catch (Exception e) {
                            AppLog.d(TAG, "Camera " + cameraId + " ignored exception while closing device: " + e.getMessage());
                        }
                        Handler handler = backgroundHandler;
                        if (handler == null) {
                            isReconnecting = false;
                            return;
                        }
                        handler.postDelayed(() -> {
                            synchronized (reconnectLock) {
                                try {
                                    cameraManager.openCamera(cameraId, stateCallback, handler);
                                } catch (CameraAccessException e) {
                                    AppLog.e(TAG, "Failed to reconnect camera " + cameraId + ": " + e.getMessage());
                                    isReconnecting = false;
                                    if (shouldReconnect) {
                                        scheduleReconnect();
                                    }
                                } catch (SecurityException e) {
                                    AppLog.e(TAG, "No camera permission during reconnect", e);
                                    shouldReconnect = false;
                                    isReconnecting = false;
                                } catch (IllegalArgumentException e) {
                                    AppLog.e(TAG, "Camera " + cameraId + " unknown during reconnect (camera service may have restarted): " + e.getMessage());
                                    shouldReconnect = false;
                                    isReconnecting = false;
                                } catch (RuntimeException e) {
                                    AppLog.e(TAG, "Camera " + cameraId + " runtime exception during reconnect: " + e.getMessage());
                                    isReconnecting = false;
                                    if (shouldReconnect) {
                                        scheduleReconnect();
                                    }
                                }
                            }
                        }, 150);
                        
                    } catch (SecurityException e) {
                        AppLog.e(TAG, "No camera permission during reconnect", e);
                        shouldReconnect = false;
                        isReconnecting = false;
                    }
                }
            };

            // 延迟выполнение重连
            if (backgroundHandler != null) {
                backgroundHandler.postDelayed(reconnectRunnable, delayMs);
            } else {
                isReconnecting = false;
            }
        }
    }

    private long getReconnectDelayMs(int attempt) {
        long baseDelayMs = 500;
        long maxDelayMs = 30000;
        long expMultiplier = 1L << Math.min(attempt - 1, 6);
        long delay = Math.min(baseDelayMs * expMultiplier, maxDelayMs);
        long jitter = (long) (delay * 0.2 * (Math.random() - 0.5) * 2);
        long result = delay + jitter;
        return Math.max(500, result);
    }

    /**
     * КамераСтатус回调
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            isOpening = false;
            synchronized (reconnectLock) {
                cameraDevice = camera;
                reconnectAttempts = 0;  // Сброс重连计数
                isReconnecting = false;  // 重连Успешно，очистка重连标志
                reconnectDelayFloorMs = 0;
                AppLog.d(TAG, "Camera " + cameraId + " opened");
                if (callback != null) {
                    callback.onCameraOpened(cameraId);
                }
            }
            // 触发一 раз性回调（副屏绑定等)，  createCameraPreviewSession довыполнение
            // 这样回调Настройки  Surface 能 Первый раз Session содержит，避免重建
            fireOnCameraOpenedCallbacks();
            if (deferSessionCreation) {
                deferSessionCreation = false;
                if (mainFloatingSurface != null && mainFloatingSurface.isValid()) {
                    // Surface 先于相机открыть绪，立т.е.创建 Session
                    createCameraPreviewSession();
                } else {
                    // 相机先于 Surface открыть，等 Surface  до 达后由调用方触发 Session 创建
                    AppLog.d(TAG, "Camera " + cameraId + " opened (deferred), waiting for surface");
                }
            } else {
                createCameraPreviewSession();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            isOpening = false;
            synchronized (reconnectLock) {
                try {
                    camera.close();
                } catch (Exception e) {
                    // 忽略Закрытоаномалия
                    AppLog.d(TAG, "Camera " + cameraId + " ignored exception while closing on disconnect: " + e.getMessage());
                }
                cameraDevice = null;
                AppLog.w(TAG, "Camera " + cameraId + " DISCONNECTED - will attempt to reconnect...");
                if (callback != null) {
                    callback.onCameraError(cameraId, -4); // 自定义Ошибка码：отключеноПодключение
                }

                // отключеноПодключение可能发生 重连过程（openCamera 后但конфигурация session 前)
                // необходимоСброс isReconnecting 标志以разрешитьпродолжить重试
                if (isReconnecting) {
                    AppLog.d(TAG, "Camera " + cameraId + " disconnected during reconnect, resetting flag");
                    isReconnecting = false;
                }
                
                // Запускавтоматически重连
                if (shouldReconnect) {
                    scheduleReconnect();
                }
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            isOpening = false;
            synchronized (reconnectLock) {
                try {
                    camera.close();
                } catch (Exception e) {
                    // 忽略Закрытоаномалия
                    AppLog.d(TAG, "Camera " + cameraId + " ignored exception while closing on error: " + e.getMessage());
                }
                cameraDevice = null;
                String errorMsg = "UNKNOWN";
                boolean shouldRetry = false;
                boolean shouldStopReconnect = false;

                switch (error) {
                    case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                        errorMsg = "ERROR_CAMERA_IN_USE (1) - Camera is being used by another app";
                        shouldRetry = true;  // Камера 占用，可以重试
                        reconnectDelayFloorMs = 500;
                        break;
                    case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                        errorMsg = "ERROR_MAX_CAMERAS_IN_USE (2) - Too many cameras open";
                        shouldRetry = true;  // Камера数量超限，可以重试
                        reconnectDelayFloorMs = 1000;
                        break;
                    case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                        errorMsg = "ERROR_CAMERA_DISABLED (3) - Camera disabled by policy (likely background restriction)";
                        shouldRetry = true;
                        // 冷Запуск时Передний планСервис可能刚Запуск还Не 完全建立，1.5 сек.后重试通常绪
                        reconnectDelayFloorMs = 1500;
                        break;
                    case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                        errorMsg = "ERROR_CAMERA_DEVICE (4) - Device error (may be temporary due to resource contention)";
                        reconnectDelayFloorMs = 8000;
                        shouldRetry = true;
                        shouldStopReconnect = false;
                        break;
                    case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                        errorMsg = "ERROR_CAMERA_SERVICE (5) - Camera service error";
                        shouldRetry = true;  // СервисОшибка，可以重试
                        reconnectDelayFloorMs = 2000;
                        break;
                }

                AppLog.e(TAG, "Camera " + cameraId + " error: " + errorMsg);
                if (callback != null) {
                    callback.onCameraError(cameraId, error);
                }

                if (shouldStopReconnect) {
                    shouldReconnect = false;
                    isReconnecting = false;
                    if (reconnectRunnable != null && backgroundHandler != null) {
                        backgroundHandler.removeCallbacks(reconnectRunnable);
                        reconnectRunnable = null;
                    }
                    return;
                }

                // 重连过程Получена команда: Ошибка，说明 openCamera 经выполнение完毕（通过回调返回Ошибка)
                // необходимоСброс isReconnecting 标志，以便可以продолжить一 раз重连попытка
                if (isReconnecting) {
                    AppLog.d(TAG, "Camera " + cameraId + " reconnect attempt completed with error, resetting flag");
                    isReconnecting = false;
                }
                
                // Если 应该重试且разрешить重连，则Запускавтоматически重连
                if (shouldRetry && shouldReconnect) {
                    scheduleReconnect();
                }
            }
        }
    };

    /**
     * 创建预览会话
     */
    private void createCameraPreviewSession() {
        if (cameraDevice == null) {
            AppLog.e(TAG, "createCameraPreviewSession: cameraDevice is null for camera " + cameraId);
            return;
        }

        synchronized (sessionLock) {
            if (isConfiguring) {
                AppLog.d(TAG, "Camera " + cameraId + " is already configuring, marking as pending");
                isPendingReconfiguration = true;
                return;
            }
            if (isSessionClosing) {
                AppLog.d(TAG, "Camera " + cameraId + " is closing old session, marking as pending and delaying");
                isPendingReconfiguration = true;
                if (backgroundHandler != null) {
                    backgroundHandler.postDelayed(this::createCameraPreviewSession, 200);
                }
                return;
            }
            isConfiguring = true;
            isPendingReconfiguration = false;
        }

        try {
            AppLog.d(TAG, "createCameraPreviewSession: Starting for camera " + cameraId);

            // 【Выкл键】Если 旧会话仍 Работа，必须先Закрыто它再创建新 session。
            // HAL 不разрешить Surface 同时绑定 до 多 шт. stream（"Surface already has a stream created for it")
            if (captureSession != null) {
                final CameraCaptureSession oldSession = captureSession;
                captureSession = null;
                try {
                    synchronized (sessionLock) {
                        isSessionClosing = true;
                    }
                    oldSession.stopRepeating();
                    oldSession.close();
                    AppLog.d(TAG, "Camera " + cameraId + " initiated session close (early, before surface prep)");
                } catch (Exception e) {
                    AppLog.e(TAG, "Camera " + cameraId + " error closing old session: " + e.getMessage());
                    synchronized (sessionLock) {
                        isSessionClosing = false;
                    }
                }

                // 通过 onClosed 回调触发重建；Настройки 300ms 安全兜底
                if (backgroundHandler != null) {
                    backgroundHandler.postDelayed(sessionCloseFallbackRunnable, 300);
                }
                synchronized (sessionLock) {
                    isConfiguring = false;
                }
                return;
            }

            SurfaceTexture surfaceTexture = null;
            if (textureView != null && textureView.isAvailable()) {
                surfaceTexture = textureView.getSurfaceTexture();
            }
            if (surfaceTexture != null) {
                if (previewSize != null) {
                    surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                    AppLog.d(TAG, "Camera " + cameraId + " buffer size set to: " + previewSize);
                } else {
                    AppLog.e(TAG, "Camera " + cameraId + " Cannot set buffer size - previewSize: " + previewSize + ", SurfaceTexture: " + surfaceTexture);
                }

                if ("back".equals(cameraPosition)) {
                    applyMirrorTransform();
                }

                if (customRotation != 0) {
                    applyCustomRotation();
                }

                if (previewSurface == null || !previewSurface.isValid()) {
                    if (previewSurface != null) {
                        try { previewSurface.release(); } catch (Exception e) {}
                        previewSurface = null;
                    }

                    // 鱼眼矫正：通过 GL 间层渲染 до  TextureView
                    AppConfig fisheyeConfig = new AppConfig(context);
                    if (fisheyeConfig.isFisheyeCorrectionEnabled()) {
                        try {
                            releaseFisheyeCorrector();
                            fisheyePrimaryIsFloating = false;
                            int pw = previewSize != null ? previewSize.getWidth() : textureView.getWidth();
                            int ph = previewSize != null ? previewSize.getHeight() : textureView.getHeight();
                            fisheyeCorrector = new FisheyeCorrector(cameraId, cameraPosition, pw, ph);
                            Surface tvSurface = new Surface(surfaceTexture);
                            previewSurface = fisheyeCorrector.initialize(tvSurface, backgroundHandler);
                            fisheyeCorrector.loadParams(fisheyeConfig);
                            AppLog.d(TAG, "Camera " + cameraId + " fisheye corrector active, using intermediate surface");
                        } catch (Exception e) {
                            AppLog.e(TAG, "Camera " + cameraId + " fisheye init failed, falling back", e);
                            releaseFisheyeCorrector();
                            previewSurface = new Surface(surfaceTexture);
                        }
                    } else {
                        releaseFisheyeCorrector();
                        previewSurface = new Surface(surfaceTexture);
                    }
                    AppLog.d(TAG, "Camera " + cameraId + " Created NEW preview surface: " + previewSurface);
                }
            } else if (fisheyeCorrector == null && mainFloatingSurface != null && mainFloatingSurface.isValid()) {
                // 无 TextureView（补盲等场景），但有悬浮窗 Surface 时，也初始化鱼眼矫正
                // 解决补盲冷启动时 FisheyeCorrector 未创建导致鱼眼矫正不生效的问题
                if (previewSurface != null) {
                    try { previewSurface.release(); } catch (Exception e) {}
                    previewSurface = null;
                }
                AppConfig fisheyeConfig = new AppConfig(context);
                if (fisheyeConfig.isFisheyeCorrectionEnabled()) {
                    try {
                        releaseFisheyeCorrector();
                        fisheyePrimaryIsFloating = true;
                        int pw = previewSize != null ? previewSize.getWidth() : 1920;
                        int ph = previewSize != null ? previewSize.getHeight() : 1080;
                        fisheyeCorrector = new FisheyeCorrector(cameraId, cameraPosition, pw, ph);
                        previewSurface = fisheyeCorrector.initialize(mainFloatingSurface, backgroundHandler);
                        fisheyeCorrector.loadParams(fisheyeConfig);
                        AppLog.d(TAG, "Camera " + cameraId + " fisheye corrector active (no textureView, using mainFloatingSurface)");
                    } catch (Exception e) {
                        AppLog.e(TAG, "Camera " + cameraId + " fisheye init failed (floating surface), falling back", e);
                        releaseFisheyeCorrector();
                    }
                }
            } else {
                if (previewSurface != null) {
                    try { previewSurface.release(); } catch (Exception e) {}
                    previewSurface = null;
                }
            }

            Surface surface = (previewSurface != null && previewSurface.isValid()) ? previewSurface : null;
            if (surface == null) {
                if (mainFloatingSurface != null && mainFloatingSurface.isValid()) {
                    surface = mainFloatingSurface;
                } else if (secondaryDisplaySurface != null && secondaryDisplaySurface.isValid()) {
                    surface = secondaryDisplaySurface;
                }
            }
            
            // проверка 否有Доступно 输出 Surface（Фоновый режиминициализация时可能Все为 null)
            boolean hasAnySurface = (surface != null && surface.isValid())
                    || (mainFloatingSurface != null && mainFloatingSurface.isValid())
                    || (secondaryDisplaySurface != null && secondaryDisplaySurface.isValid())
                    || (recordSurface != null && recordSurface.isValid());
            if (!hasAnySurface) {
                AppLog.d(TAG, "Camera " + cameraId + " no available surfaces, skipping session creation (waiting for surface)");
                // Закрыто旧 session，防止продолжить推帧 до 销毁  Surface（queueBuffer abandoned)
                if (captureSession != null) {
                    try {
                        captureSession.close();
                    } catch (Exception e) {
                        // 忽略
                    }
                    captureSession = null;
                    AppLog.d(TAG, "Camera " + cameraId + " closed old session (no surfaces)");
                }
                synchronized (sessionLock) {
                    isConfiguring = false;
                }
                return;
            }

            AppLog.d(TAG, "Camera " + cameraId + " Creating capture request...");
            int template = (recordSurface != null) ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW;
            final CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(template);
            
            // Сохранить求构建器引用（用于实时обновление亮度/Шумоподавление参数)
            currentRequestBuilder = previewRequestBuilder;
            
            // Если Включить亮度/Шумоподавление调节，ПриложениеконфигурацияСохранить 参数
            if (imageAdjustEnabled) {
                applyImageAdjustParamsFromConfig(previewRequestBuilder);
            }
            
            // 准备所有输出Surface
            java.util.List<Surface> surfaces = new java.util.ArrayList<>();
            java.util.List<OutputConfiguration> outputConfigs = new java.util.ArrayList<>();

            // 单一输出режим处理（用于 L6/L7 等не поддерживается多 кам.输出 车机平台)
            if (singleOutputMode && recordSurface != null && recordSurface.isValid()) {
                AppLog.d(TAG, "Camera " + cameraId + " SINGLE OUTPUT MODE: Using ONLY record surface");
                surfaces.add(recordSurface);
                previewRequestBuilder.addTarget(recordSurface);
                outputConfigs.add(new OutputConfiguration(recordSurface));
            } else {
                // нормальнорежим：использование OutputConfiguration 实现 Surface Sharing (API 28+)
                // 将所有预览性质  Surface (主预览、主悬浮、副悬浮)  групп合成一 шт.硬件流
                boolean fisheyeActive = (fisheyeCorrector != null && fisheyeCorrector.isInitialized());

                if (fisheyeActive) {
                    // 鱼眼矫正режим：Camera2 只输出 до  FisheyeCorrector  间 Surface（单 кам.)
                    // 悬浮窗/副屏由 FisheyeCorrector GL 管线统一输出（矫正后画面)
                    AppLog.d(TAG, "Camera " + cameraId + " FISHEYE MODE: single output to GL pipeline");

                    if (surface != null && surface.isValid()) {
                        OutputConfiguration previewConfig = new OutputConfiguration(surface);
                        activePreviewConfig = previewConfig;
                        surfaces.add(surface);
                        previewRequestBuilder.addTarget(surface);
                        outputConfigs.add(previewConfig);
                    }

                    // Синхронизация дополнительных выходов FisheyeCorrector с текущим состоянием Surface.
                    // Удалённые Surface должны быть отписаны (защита от гонки EGL "already connected").
                    // Если mainFloatingSurface уже основной выход коррекции (сценарий без TextureView),
                    // нельзя добавить его как дополнительный — иначе один и тот же Surface будет
                    // подключён к EGL дважды и упадёт.
                    if (fisheyePrimaryIsFloating) {
                        fisheyeCorrector.removeOutputSurface("mainFloating");
                    } else if (mainFloatingSurface != null && mainFloatingSurface.isValid()) {
                        fisheyeCorrector.addOutputSurface("mainFloating", mainFloatingSurface);
                        AppLog.d(TAG, "Registered main floating surface to fisheye GL pipeline");
                    } else {
                        fisheyeCorrector.removeOutputSurface("mainFloating");
                    }
                    if (secondaryDisplaySurface != null && secondaryDisplaySurface.isValid()) {
                        fisheyeCorrector.addOutputSurface("secondaryDisplay", secondaryDisplaySurface);
                        AppLog.d(TAG, "Registered secondary display surface to fisheye GL pipeline");
                    } else {
                        fisheyeCorrector.removeOutputSurface("secondaryDisplay");
                    }
                } else {
                    // 非鱼眼режим：использование Surface Sharing
                    AppLog.d(TAG, "Camera " + cameraId + " Using Surface Sharing for preview streams");

                    // 统一Настройки所有Всего 享 Surface   buffer 尺寸，确保 и 相机输出一致
                    // 避免悬浮窗/副屏 TextureView использование物理布局尺寸导致 OutputConfiguration 尺寸不匹配
                    if (previewSize != null) {
                        if (mainFloatingSurfaceTexture != null) {
                            mainFloatingSurfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                        }
                        if (secondaryDisplaySurfaceTexture != null) {
                            secondaryDisplaySurfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                        }
                    }

                    if (surface != null && surface.isValid()) {
                        OutputConfiguration previewSharedConfig = new OutputConfiguration(surface);
                        previewSharedConfig.enableSurfaceSharing();
                        activePreviewConfig = previewSharedConfig;
                        surfaces.add(surface);
                        previewRequestBuilder.addTarget(surface);

                        if (previewSurface != null && previewSurface.isValid() && previewSurface != surface &&
                            previewSurface != mainFloatingSurface && previewSurface != secondaryDisplaySurface) {
                            previewSharedConfig.addSurface(previewSurface);
                            surfaces.add(previewSurface);
                            previewRequestBuilder.addTarget(previewSurface);
                            AppLog.d(TAG, "Added preview surface to SHARED preview stream");
                        }

                        if (mainFloatingSurface != null && mainFloatingSurface.isValid() && mainFloatingSurface != surface) {
                            previewSharedConfig.addSurface(mainFloatingSurface);
                            surfaces.add(mainFloatingSurface);
                            previewRequestBuilder.addTarget(mainFloatingSurface);
                            AppLog.d(TAG, "Added main floating surface to SHARED preview stream");
                        }

                        if (secondaryDisplaySurface != null && secondaryDisplaySurface.isValid() &&
                            secondaryDisplaySurface != surface && secondaryDisplaySurface != mainFloatingSurface) {
                            previewSharedConfig.addSurface(secondaryDisplaySurface);
                            surfaces.add(secondaryDisplaySurface);
                            previewRequestBuilder.addTarget(secondaryDisplaySurface);
                            AppLog.d(TAG, "Added secondary display surface to SHARED preview stream");
                        }

                        outputConfigs.add(previewSharedConfig);
                    }
                }

                // Запись Surface 作为一 шт.独立 硬件流
                if (recordSurface != null && recordSurface.isValid()) {
                    outputConfigs.add(new OutputConfiguration(recordSurface));
                    surfaces.add(recordSurface);
                    previewRequestBuilder.addTarget(recordSurface);
                    AppLog.d(TAG, "Added record surface as SEPARATE stream");
                }
            }

            if (outputConfigs.isEmpty()) {
                AppLog.w(TAG, "Camera " + cameraId + " No valid surfaces for session, skipping configuration");
                if (captureSession != null) {
                    try {
                        captureSession.close();
                    } catch (Exception e) {
                    }
                    captureSession = null;
                }
                return;
            }

            AppLog.d(TAG, "Camera " + cameraId + " Total physical streams (OutputConfigs): " + outputConfigs.size() + 
                    ", Total Surfaces: " + surfaces.size());
            
            // 诊断：列出所有 surfaces
            for (int i = 0; i < surfaces.size(); i++) {
                Surface s = surfaces.get(i);
                AppLog.d(TAG, "Camera " + cameraId + " Surface[" + i + "]: " + s + ", isValid=" + s.isValid());
            }

            // 注：旧会话Закрыто提前 до 方法Вкл头处理（确保 SurfaceTexture отключеноПодключение后再创建 EGL Surface)

            // 创建会话 (использование OutputConfiguration)
            AppLog.d(TAG, "Camera " + cameraId + " Creating capture session with " + outputConfigs.size() + " streams...");
            
            CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    AppLog.d(TAG, "Camera " + cameraId + " Session configured!");
                    configFailRetryCount = 0; // Успешно，Сброс重试计数
                    
                    boolean pending;
                    synchronized (sessionLock) {
                        isConfiguring = false;
                        isSessionClosing = false;
                        pending = isPendingReconfiguration;
                    }

                    if (pending) {
                        AppLog.d(TAG, "Camera " + cameraId + " found pending configuration request, restarting...");
                        createCameraPreviewSession();
                        return;
                    }

                    if (cameraDevice == null) {
                        AppLog.e(TAG, "Camera " + cameraId + " cameraDevice is null in onConfigured");
                        return;
                    }

                    if (captureSession != null && captureSession != session) {
                        AppLog.w(TAG, "Camera " + cameraId + " Session already replaced by newer session, ignoring this callback");
                        try { session.close(); } catch (Exception e) {}
                        return;
                    }

                    captureSession = session;
                    try {
                        frameCount = 0;
                        lastFrameLogTime = System.currentTimeMillis();

                        if (captureSession != session) return;
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), activeCaptureCallback, backgroundHandler);
                        AppLog.d(TAG, "Camera " + cameraId + " preview started!");
                        lastFrameTimestampMs = System.currentTimeMillis();
                        stallRecoveryLevel = 0;
                        lastStallRecoveryMs = 0;
                        startHealthMonitor();
                        if (callback != null) callback.onCameraConfigured(cameraId);
                    } catch (CameraAccessException e) {
                        AppLog.e(TAG, "Failed to start preview", e);
                    } catch (IllegalStateException e) {
                        AppLog.w(TAG, "Session closed: " + e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    AppLog.e(TAG, "Failed to configure camera " + cameraId + " session!");
                    // ЗакрытоОшибка  session, освобождено Surface 绑定（否则重试会遇 до  "Surface already has a stream")
                    try {
                        session.close();
                    } catch (Exception ignored) {}
                    boolean pending;
                    synchronized (sessionLock) {
                        isConfiguring = false;
                        isSessionClosing = false;
                        pending = isPendingReconfiguration;
                    }

                    if (pending) {
                        AppLog.d(TAG, "Camera " + cameraId + " found pending configuration request after failure, retrying...");
                        createCameraPreviewSession();
                        return;
                    }
                    
                    // 重试逻辑
                    boolean fisheyeActive = (fisheyeCorrector != null && fisheyeCorrector.isInitialized());
                    if (recordSurface != null) {
                        // Запись：丢弃可选 Surface 后重试
                        // 注意：鱼眼режим floating/secondary 由 FisheyeCorrector управление，
                        // 不  Camera2 session ，очистка它们 Восстановление无Помощь
                        boolean droppedOptionalSurface = false;
                        if (!fisheyeActive && secondaryDisplaySurface != null) {
                            secondaryDisplaySurface = null;
                            droppedOptionalSurface = true;
                            AppLog.w(TAG, "Retrying without secondary display surface...");
                        }
                        if (!fisheyeActive && !droppedOptionalSurface && mainFloatingSurface != null) {
                            mainFloatingSurface = null;
                            droppedOptionalSurface = true;
                            AppLog.w(TAG, "Retrying without main floating surface...");
                        }
                        if (!droppedOptionalSurface) {
                            AppLog.w(TAG, "Retrying without recording surface...");
                            recordSurface = null;
                        }
                        if (backgroundHandler != null) {
                            backgroundHandler.postDelayed(() -> {
                                if (cameraDevice != null) createCameraPreviewSession();
                            }, 500);
                        }
                    } else {
                        configFailRetryCount++;
                        if (configFailRetryCount <= MAX_CONFIG_FAIL_RETRIES) {
                            // 可能  Surface Выполняется  от ДругоеКамера转移（connect: already connected)，
                            // 短暂延迟后重试，ожидание旧 session 释放 Surface
                            AppLog.w(TAG, "Camera " + cameraId + " session config failed, retry " + configFailRetryCount + "/" + MAX_CONFIG_FAIL_RETRIES + " in 200ms...");
                            if (backgroundHandler != null) {
                                backgroundHandler.postDelayed(() -> {
                                    if (cameraDevice != null) {
                                        AppLog.d(TAG, "Camera " + cameraId + " retrying session after config failure");
                                        createCameraPreviewSession();
                                    }
                                }, 200);
                            }
                        } else {
                            // 重试耗尽，丢弃副屏 Surface 后попытка只用主 Surface
                            // 鱼眼режим secondary 不  Camera2 session ，不необходимо丢弃
                            AppLog.e(TAG, "Camera " + cameraId + " config retries exhausted (" + configFailRetryCount + "), dropping secondary display surface");
                            configFailRetryCount = 0;
                            if (!fisheyeActive && secondaryDisplaySurface != null) {
                                secondaryDisplaySurface = null;
                                if (backgroundHandler != null) {
                                    backgroundHandler.postDelayed(() -> {
                                        if (cameraDevice != null) createCameraPreviewSession();
                                    }, 100);
                                }
                            }
                        }
                        if (callback != null) {
                            callback.onCameraError(cameraId, -3);
                        }
                    }
                }
                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    AppLog.d(TAG, "Camera " + cameraId + " Session CLOSED callback received");
                    boolean wasClosing;
                    synchronized (sessionLock) {
                        wasClosing = isSessionClosing;
                        isSessionClosing = false;
                    }
                    // CLOSED 回调后 HAL 仍需少量时间释放 Surface 绑定
                    // 延迟 50ms 重建（0ms 会触发 "Surface already has a stream" Ошибка)
                    if (wasClosing && backgroundHandler != null) {
                        // 移除所有待выполнение 重建задача，避免重复重建
                        backgroundHandler.removeCallbacks(sessionCloseFallbackRunnable);
                        backgroundHandler.removeCallbacks(recreateSessionRunnable);
                        backgroundHandler.postDelayed(recreateSessionRunnable, 50);
                    }
                }
            };

            // использование API 28   createCaptureSession (通过 OutputConfiguration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cameraDevice.createCaptureSessionByOutputConfigurations(outputConfigs, sessionCallback, backgroundHandler);
            } else {
                // 降级处理 (虽然 minSdk   28，但为健壮性保留)
                cameraDevice.createCaptureSession(surfaces, sessionCallback, backgroundHandler);
            }

        } catch (CameraAccessException e) {
            synchronized (sessionLock) { isConfiguring = false; isSessionClosing = false; }
            AppLog.e(TAG, "Failed to create preview session for camera " + cameraId, e);
            AppLog.e(TAG, "Exception details: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            synchronized (sessionLock) { isConfiguring = false; isSessionClosing = false; }
            // 特殊处理 "Surface was abandoned" Ошибка
            String message = e.getMessage();
            if (message != null && message.contains("abandoned")) {
                AppLog.e(TAG, "Camera " + cameraId + " detected abandoned Surface, attempting recovery...");
                // 鱼眼режим floating/secondary 由 FisheyeCorrector управление，不  Camera2 session 
                boolean fisheyeActive = (fisheyeCorrector != null && fisheyeCorrector.isInitialized());
                boolean cleared = false;
                if (!fisheyeActive && secondaryDisplaySurface != null) {
                    secondaryDisplaySurface = null;
                    cleared = true;
                    AppLog.w(TAG, "Camera " + cameraId + " cleared abandoned secondaryDisplaySurface and retrying");
                }
                if (!fisheyeActive && !cleared && mainFloatingSurface != null) {
                    mainFloatingSurface = null;
                    cleared = true;
                    AppLog.w(TAG, "Camera " + cameraId + " cleared abandoned mainFloatingSurface and retrying");
                }
                if (!cleared && recordSurface != null) {
                    recordSurface = null;
                    cleared = true;
                    AppLog.w(TAG, "Camera " + cameraId + " cleared abandoned recordSurface and retrying");
                }
                if (cleared && backgroundHandler != null) {
                    backgroundHandler.postDelayed(() -> {
                        if (cameraDevice != null) {
                            AppLog.d(TAG, "Camera " + cameraId + " retrying session creation after abandoning surface cleanup");
                            createCameraPreviewSession();
                        }
                    }, 100);
                    return;
                }
            }
            AppLog.e(TAG, "Unexpected IllegalArgumentException creating session for camera " + cameraId, e);
            e.printStackTrace();
        } catch (Exception e) {
            synchronized (sessionLock) { isConfiguring = false; isSessionClosing = false; }
            AppLog.e(TAG, "Unexpected exception creating session for camera " + cameraId, e);
            AppLog.e(TAG, "Exception details: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 安全兜底：Если  CLOSED 回调Не 触发，300ms 后проверка并重建
     */
    private void createCameraPreviewSessionIfClosePending() {
        synchronized (sessionLock) {
            if (isSessionClosing) {
                // 回调还没来，продолжить等
                return;
            }
        }
        // CLOSED 回调经来过但没触发重建（理论不该 до 这)，или回调丢失，兜底重建
        if (cameraDevice != null && captureSession == null) {
            AppLog.d(TAG, "Camera " + cameraId + " session close fallback triggered");
            createCameraPreviewSession();
        }
    }

    /**
     * 重新创建会话（用于Вкл始/Остановить запись时，или者悬浮窗切换时)
     * 增加防抖处理，避免频繁重建导致黑屏
     */
    private final Runnable recreateSessionRunnable = this::createCameraPreviewSession;
    private final Runnable sessionCloseFallbackRunnable = this::createCameraPreviewSessionIfClosePending;

    /** 帧捕获回调（复用实例，供动态 Surface обновление时 setRepeatingRequest использование) */
    private final CameraCaptureSession.CaptureCallback activeCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                      @NonNull CaptureRequest request,
                                      @NonNull TotalCaptureResult result) {
            frameCount++;
            long now = System.currentTimeMillis();
            lastFrameTimestampMs = now;
            if (!hasReadActualParams || frameCount == 1) {
                readActualParamsFromResult(result);
                hasReadActualParams = true;
            }
            fpsWindowFrameCount++;
            if (fpsWindowStartTime == 0) fpsWindowStartTime = now;
            long fpsElapsed = now - fpsWindowStartTime;
            if (fpsElapsed >= 1000) {
                currentFps = fpsWindowFrameCount * 1000f / fpsElapsed;
                fpsWindowFrameCount = 0;
                fpsWindowStartTime = now;
            }
            if (now - lastFrameLogTime >= FRAME_LOG_INTERVAL_MS) {
                long elapsed = now - lastFrameLogTime;
                float fps = frameCount * 1000f / elapsed;
                AppLog.d(TAG, "Camera " + cameraId + " FPS: " + String.format("%.1f", fps));
                frameCount = 0;
                lastFrameLogTime = now;
            }
        }
    };

    /**
     * 立т.е.ОстановкаТекущий会话  repeating request，防止帧продолжить推 до т.е.将销毁  Surface。
     * 用于悬浮窗 dismiss 前调用，避免 queueBuffer: BufferQueue has been abandoned 刷屏。
     */
    public void stopRepeatingNow() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                AppLog.d(TAG, "Camera " + cameraId + " stopRepeating (surface about to be removed)");
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    // ===== 动态 Surface управление（补盲优化：避免 ~300ms Session Закрытоожидание) =====

    /**
     * 动态添加 Surface  до Текущий预览 Session。
     * 利用 OutputConfiguration.addSurface() + finalizeOutputConfigurations() 实现
     *  不Закрыто旧 Session  情况添加新输出，跳过 ~300ms   HAL Закрытоожидание。
     * Ошибка时автоматически降级 до  recreateSession。
     *
     * @param surface 要添加  Surface
     * @param isMainFloating true=主屏悬浮窗, false=副屏
     */
    public void addDynamicSurface(Surface surface, boolean isMainFloating) {
        // 1. Хранилище引用（无论动态 否Успешно，后续 createCameraPreviewSession все能拿 до )
        if (isMainFloating) {
            this.mainFloatingSurface = surface;
            AppLog.d(TAG, "Main floating surface set for camera " + cameraId +
                    ": " + surface + ", isValid=" + (surface != null && surface.isValid()));
        } else {
            this.secondaryDisplaySurface = surface;
            AppLog.d(TAG, "Secondary display surface set for camera " + cameraId +
                    ": " + surface + ", isValid=" + (surface != null && surface.isValid()));
        }

        if (surface == null || !surface.isValid()) return;

        // 鱼眼矫正режим：通过 FisheyeCorrector GL 管线输出，无需重建 Camera2 session
        if (fisheyeCorrector != null && fisheyeCorrector.isInitialized()) {
            // 如果 mainFloatingSurface 已经是鱼眼矫正的主输出，不能再作为附加输出
            if (isMainFloating && fisheyePrimaryIsFloating) {
                AppLog.d(TAG, "Camera " + cameraId + " mainFloatingSurface is fisheye primary output, skipping addOutputSurface");
                return;
            }
            String tag = isMainFloating ? "mainFloating" : "secondaryDisplay";
            if (backgroundHandler != null) {
                backgroundHandler.post(() -> {
                    if (fisheyeCorrector != null && fisheyeCorrector.isInitialized()) {
                        fisheyeCorrector.addOutputSurface(tag, surface);
                    }
                });
            }
            return;
        }

        // 2. Если  Session 正忙，新 Surface 会 выполняется  createCameraPreviewSession автоматическисодержит
        synchronized (sessionLock) {
            if (isConfiguring || isSessionClosing) {
                AppLog.d(TAG, "Camera " + cameraId + " session busy, dynamic surface will be included in pending session");
                return;
            }
        }

        // 3. попытка动态添加（ Фоновый режим线程выполнение)
        if (backgroundHandler != null && captureSession != null && activePreviewConfig != null) {
            backgroundHandler.removeCallbacks(recreateSessionRunnable);
            backgroundHandler.post(() -> {
                if (!tryDynamicSurfaceAdd(surface, isMainFloating)) {
                    AppLog.d(TAG, "Camera " + cameraId + " dynamic add failed, falling back to full session rebuild");
                    createCameraPreviewSession();
                }
            });
        } else {
            // 没有现有 Session（еслиКамера刚открыть)，走нормально创建Путь
            recreateSession(true);
        }
    }

    /**
     * 动态移除 Surface（补盲隐藏优化)。
     * 利用 OutputConfiguration.removeSurface() + finalizeOutputConfigurations() 实现
     *  不Закрыто Session  情况移除输出。
     * Ошибка时автоматически降级 до  recreateSession。
     *
     * @param isMainFloating true=主屏悬浮窗, false=副屏
     */
    public void removeDynamicSurface(boolean isMainFloating) {
        // 1. 取出并очистка引用
        final Surface surfaceToRemove;
        if (isMainFloating) {
            surfaceToRemove = this.mainFloatingSurface;
            this.mainFloatingSurface = null;
            AppLog.d(TAG, "Main floating surface cleared for camera " + cameraId);
        } else {
            surfaceToRemove = this.secondaryDisplaySurface;
            this.secondaryDisplaySurface = null;
            AppLog.d(TAG, "Secondary display surface cleared for camera " + cameraId);
        }

        // 鱼眼矫正режим： от  GL 管线移除，无需碰 Camera2 session
        if (fisheyeCorrector != null && fisheyeCorrector.isInitialized()) {
            String tag = isMainFloating ? "mainFloating" : "secondaryDisplay";
            if (backgroundHandler != null) {
                backgroundHandler.post(() -> {
                    if (fisheyeCorrector != null && fisheyeCorrector.isInitialized()) {
                        fisheyeCorrector.removeOutputSurface(tag);
                    }
                });
            }
            return;
        }

        // 2. 立т.е.Остановка推帧，防止 Surface 销毁后 queueBuffer abandoned
        stopRepeatingNow();

        // 3. попытка动态移除（ Фоновый режим线程выполнение)
        if (surfaceToRemove != null && backgroundHandler != null
                && captureSession != null && activePreviewConfig != null) {
            backgroundHandler.removeCallbacks(recreateSessionRunnable);
            backgroundHandler.post(() -> {
                if (!tryDynamicSurfaceRemove(surfaceToRemove)) {
                    AppLog.d(TAG, "Camera " + cameraId + " dynamic remove failed, falling back to full session rebuild");
                    createCameraPreviewSession();
                }
            });
        } else {
            recreateSession(false);
        }
    }

    private boolean tryDynamicSurfaceAdd(Surface surface, boolean isMainFloating) {
        synchronized (sessionLock) {
            if (isConfiguring || isSessionClosing) return false;
        }
        if (captureSession == null || activePreviewConfig == null || currentRequestBuilder == null) {
            return false;
        }
        if (surface == null || !surface.isValid()) return false;

        try {
            // 1. 添加 до Всего 享 OutputConfiguration
            activePreviewConfig.addSurface(surface);

            // 2. Уведомление Session конфигурация变更
            captureSession.finalizeOutputConfigurations(
                    java.util.Collections.singletonList(activePreviewConfig));

            // 3. 将新 Surface 加入 CaptureRequest 目标
            currentRequestBuilder.addTarget(surface);

            // 4. обновление repeating request
            captureSession.setRepeatingRequest(
                    currentRequestBuilder.build(), activeCaptureCallback, backgroundHandler);

            AppLog.d(TAG, "Camera " + cameraId + " dynamic surface ADD succeeded (" +
                    (isMainFloating ? "main floating" : "secondary display") + ")");
            return true;
        } catch (Exception e) {
            AppLog.w(TAG, "Camera " + cameraId + " dynamic surface add failed: " + e.getMessage());
            // 回滚：尽力ВосстановлениеСтатус
            try { activePreviewConfig.removeSurface(surface); } catch (Exception ignored) {}
            try { currentRequestBuilder.removeTarget(surface); } catch (Exception ignored) {}
            return false;
        }
    }

    private boolean tryDynamicSurfaceRemove(Surface surface) {
        synchronized (sessionLock) {
            if (isConfiguring || isSessionClosing) return false;
        }
        if (captureSession == null || activePreviewConfig == null || currentRequestBuilder == null) {
            return false;
        }
        if (surface == null) return false;

        try {
            // 1.  от  CaptureRequest 移除目标（Остановка к 该 Surface 推帧)
            currentRequestBuilder.removeTarget(surface);

            // 2.  от Всего 享 OutputConfiguration 移除
            activePreviewConfig.removeSurface(surface);

            // 3. Уведомление Session конфигурация变更
            captureSession.finalizeOutputConfigurations(
                    java.util.Collections.singletonList(activePreviewConfig));

            // 4. Восстановление repeating request（толькосодержит剩余 Surface)
            captureSession.setRepeatingRequest(
                    currentRequestBuilder.build(), activeCaptureCallback, backgroundHandler);

            AppLog.d(TAG, "Camera " + cameraId + " dynamic surface REMOVE succeeded");
            return true;
        } catch (Exception e) {
            AppLog.w(TAG, "Camera " + cameraId + " dynamic surface remove failed: " + e.getMessage());
            return false;
        }
    }

    public void recreateSession() {
        recreateSession(false);
    }

    /**
     * 重新创建会话
     * @param urgent 紧急режим（если补盲悬浮窗)，跳过防抖延迟以最快速度重建
     */
    public void recreateSession(boolean urgent) {
        if (cameraDevice != null) {
            if (backgroundHandler != null) {
                // 移除待выполнение задача，实现防抖
                backgroundHandler.removeCallbacks(recreateSessionRunnable);
                
                int delay;
                if (urgent) {
                    // 紧急режим：минимум延迟，用于补盲悬浮窗等необходимо快速响应 场景
                    delay = isConfiguring ? 50 : 0;
                } else {
                    // 普通режим：保持防抖延迟
                    delay = isConfiguring ? 500 : 100;
                }

                if (delay == 0) {
                    backgroundHandler.post(recreateSessionRunnable);
                } else {
                    backgroundHandler.postDelayed(recreateSessionRunnable, delay);
                }
                AppLog.d(TAG, "Camera " + cameraId + " recreateSession scheduled (delay=" + delay + "ms, isConfiguring=" + isConfiguring + ", urgent=" + urgent + ")");
            } else {
                createCameraPreviewSession();
            }
        }
    }

    /**
     * ПолучениеТекущий TextureView（用于Мониторинг等функция)
     */
    public TextureView getTextureView() {
        return textureView;
    }

    /**
     * 实时捕获Текущий画面（不СохранитьФайл)
     * 用于Мониторинг等необходимо实时ПолучениеИзображение функция
     * 注意：必须 主线程调用
     * 
     * @return Текущий画面  Bitmap，ОшибкаВозвращает null（调用方负责回收)
     */
    public android.graphics.Bitmap captureBitmap() {
        if (textureView == null || !textureView.isAvailable()) {
            AppLog.w(TAG, "Camera " + cameraId + " TextureView not available for capture");
            return null;
        }

        if (previewSize == null) {
            AppLog.w(TAG, "Camera " + cameraId + " preview size not available for capture");
            return null;
        }

        try {
            android.graphics.Bitmap bitmap = textureView.getBitmap(
                    previewSize.getWidth(),
                    previewSize.getHeight()
            );
            
            if (bitmap != null) {
                AppLog.d(TAG, "Camera " + cameraId + " captured bitmap: " + 
                        bitmap.getWidth() + "x" + bitmap.getHeight());
            }
            return bitmap;
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to capture bitmap", e);
            return null;
        }
    }

    /**
     * Фото（автоматически生成时间戳)
     */
    public void takePicture() {
        // 生成时间戳
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        takePicture(timestamp);
    }

    /**
     * Фото（использование指定 时间戳)
     * @param timestamp Файл命名用 时间戳
     */
    public void takePicture(String timestamp) {
        takePicture(timestamp, 0);  // По умолчанию无延迟
    }

    /**
     * Фото（использование指定 时间戳 и Сохранить延迟)
     * @param timestamp Файл命名用 时间戳
     * @param saveDelayMs СохранитьФайл前 延迟时间（毫 сек.)
     */
    public void takePicture(String timestamp, int saveDelayMs) {
        if (textureView == null || !textureView.isAvailable()) {
            AppLog.e(TAG, "Camera " + cameraId + " TextureView not available");
            return;
        }

        if (previewSize == null) {
            AppLog.e(TAG, "Camera " + cameraId + " preview size not available");
            return;
        }

        //  Фоновый режим线程处理截图 и Сохранить
        if (backgroundHandler != null) {
            backgroundHandler.post(() -> {
                try {
                    // 1. 立т.е. от TextureViewПолучениеBitmap（快速抓拍)
                    android.graphics.Bitmap bitmap = textureView.getBitmap(
                            previewSize.getWidth(),
                            previewSize.getHeight()
                    );
                    
                    if (bitmap != null) {
                        AppLog.d(TAG, "Camera " + cameraId + " picture captured (" +
                              bitmap.getWidth() + "x" + bitmap.getHeight() + "), will save in " + saveDelayMs + "ms");
                        
                        // 2. 延迟后再Сохранить до 磁 диск（分散I/O压力)
                        if (saveDelayMs > 0) {
                            try {
                                Thread.sleep(saveDelayMs);
                            } catch (InterruptedException e) {
                                AppLog.w(TAG, "Save delay interrupted");
                            }
                        }
                        
                        // 3. СохранитьФайл
                        saveBitmapAsJPEG(bitmap, timestamp);
                        bitmap.recycle();
                        AppLog.d(TAG, "Camera " + cameraId + " picture saved");
                    } else {
                        AppLog.e(TAG, "Camera " + cameraId + " failed to get bitmap from TextureView");
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "Camera " + cameraId + " error capturing picture", e);
                }
            });
        }
    }

    /**
     * 将BitmapСохранить为JPEGФайл
     */
    private void saveBitmapAsJPEG(android.graphics.Bitmap bitmap) {
        // 生成时间戳
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        saveBitmapAsJPEG(bitmap, timestamp);
    }

    /**
     * 将BitmapСохранить为JPEGФайл（использование指定 时间戳)
     */
    private void saveBitmapAsJPEG(android.graphics.Bitmap bitmap, String timestamp) {
        File photoDir = StorageHelper.getPhotoDir(context);
        if (!photoDir.exists()) {
            photoDir.mkdirs();
        }

        // проверкаХранилище空间 否充足（至少необходимо 5MB)
        long availableSpace = StorageHelper.getAvailableSpace(photoDir);
        if (availableSpace >= 0 && availableSpace < 5 * 1024 * 1024) {
            AppLog.w(TAG, "Camera " + cameraId + " Хранилище空间不足，剩余: " + StorageHelper.formatSize(availableSpace));
            // 仍然попыткаСохранить，因为Фото通常只有几百KB
        }

        // использование传入 时间戳命名：yyyyMMdd_HHmmss_КамераПозиция.jpg
        String position = (cameraPosition != null) ? cameraPosition : cameraId;
        File photoFile = new File(photoDir, timestamp + "_" + position + ".jpg");

        // проверка 否необходимо添加时间角标
        android.graphics.Bitmap finalBitmap = bitmap;
        AppConfig appConfig = new AppConfig(context);
        if (appConfig.isTimestampWatermarkEnabled()) {
            finalBitmap = addTimestampWatermark(bitmap, timestamp);
        }

        FileOutputStream output = null;
        try {
            output = new FileOutputStream(photoFile);
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output);
            output.flush();
            AppLog.i(TAG, "Photo saved: " + photoFile.getAbsolutePath());
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("ENOSPC")) {
                AppLog.e(TAG, "Camera " + cameraId + " СохранитьФотоОшибка：Хранилище空间满");
            } else {
                AppLog.e(TAG, "Failed to save photo", e);
            }
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    // Закрыто流时  ENOSPC Ошибка通常表示ФайлСохранить，但空间紧 шт.
                    // 降Низкий д.志级别，避免误导用户以为СохранитьОшибка
                    if (e.getMessage() != null && e.getMessage().contains("ENOSPC")) {
                        AppLog.w(TAG, "Camera " + cameraId + " Хранилище空间满，Очистка Хранилище");
                    } else {
                        AppLog.e(TAG, "Failed to close output stream", e);
                    }
                }
            }
            // Если 创建新 bitmap用于水印，необходимо回收
            if (finalBitmap != bitmap && finalBitmap != null) {
                finalBitmap.recycle();
            }
        }
    }

    /**
     *  Bitmap添加时间角标
     * @param originalBitmap 原始Изображение
     * @param timestamp 时间戳字符串（格式：yyyyMMdd_HHmmss)
     * @return 带有时间角标 新Bitmap
     */
    private android.graphics.Bitmap addTimestampWatermark(android.graphics.Bitmap originalBitmap, String timestamp) {
        try {
            // 创建可编辑 副本
            android.graphics.Bitmap mutableBitmap = originalBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
            android.graphics.Canvas canvas = new android.graphics.Canvas(mutableBitmap);

            // 将时间戳转换为可读格式：yyyyMMdd_HHmmss -> yyyy-MM-dd HH:mm:ss
            String displayTime;
            try {
                java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                java.util.Date date = inputFormat.parse(timestamp);
                displayTime = outputFormat.format(date);
            } catch (Exception e) {
                // 解析Ошибка，использованиеТекущий时间
                displayTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new java.util.Date());
            }

            // 根据Изображение宽度动态计算字体大小（约为Изображение宽度 3%)
            float textSize = mutableBitmap.getWidth() * 0.03f;
            if (textSize < 16) textSize = 16;  // минимум16像素
            if (textSize > 48) textSize = 48;  // максимум48像素

            // Настройки画笔 - 阴影效果
            android.graphics.Paint shadowPaint = new android.graphics.Paint();
            shadowPaint.setColor(android.graphics.Color.BLACK);
            shadowPaint.setTextSize(textSize);
            shadowPaint.setAntiAlias(true);
            shadowPaint.setTypeface(android.graphics.Typeface.MONOSPACE);

            // Настройки画笔 - 主文字
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setColor(android.graphics.Color.WHITE);
            textPaint.setTextSize(textSize);
            textPaint.setAntiAlias(true);
            textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);

            // 计算Позиция（左角，留一定边距)
            float x = textSize * 0.5f;
            float y = textSize * 1.2f;

            // 绘制阴影（偏移2像素)
            canvas.drawText(displayTime, x + 2, y + 2, shadowPaint);
            // 绘制主文字
            canvas.drawText(displayTime, x, y, textPaint);

            AppLog.d(TAG, "Camera " + cameraId + " added timestamp watermark: " + displayTime);
            return mutableBitmap;

        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to add timestamp watermark", e);
            return originalBitmap;  // Ошибка时返回原图
        }
    }

    /**
     * ЗакрытоКамера
     */
    public void closeCamera() {
        // Если 不 主实例，不выполнениеЗакрытооперация
        if (!isPrimaryInstance) {
            AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") is SECONDARY instance, skipping closeCamera");
            return;
        }
        
        synchronized (reconnectLock) {
            shouldReconnect = false;  // Отключитьавтоматически重连
            reconnectAttempts = 0;  // Сброс重连计数
            isReconnecting = false;  // очистка重连Статус
            isOpening = false;  // очисткаоткрытьСтатус
            deferSessionCreation = false;  // очистка延迟标志
            stopHealthMonitor();

            // Отмена待处理 重连задача
            if (reconnectRunnable != null && backgroundHandler != null) {
                backgroundHandler.removeCallbacks(reconnectRunnable);
                reconnectRunnable = null;
            }

            // Отмена待处理  session 重建задача（防止 closeCamera 后仍попытка createCaptureSession)
            if (backgroundHandler != null) {
                backgroundHandler.removeCallbacks(recreateSessionRunnable);
                backgroundHandler.removeCallbacks(sessionCloseFallbackRunnable);
            }

            // Сброс Session Статус标志（防止重新открыть时残留Статус导致死循环)
            synchronized (sessionLock) {
                isSessionClosing = false;
                isConfiguring = false;
                isPendingReconfiguration = false;
            }

            // очисткаНе 触发 回调
            synchronized (onCameraOpenedCallbacks) {
                onCameraOpenedCallbacks.clear();
            }

            // Закрыто会话（捕获аномалия)
            if (captureSession != null) {
                try {
                    captureSession.close();
                } catch (Exception e) {
                    // 忽略Закрытоаномалия
                    AppLog.d(TAG, "Camera " + cameraId + " ignored exception while closing session: " + e.getMessage());
                }
                captureSession = null;
            }

            // Закрыто设备（捕获аномалия)
            if (cameraDevice != null) {
                try {
                    cameraDevice.close();
                } catch (Exception e) {
                    // 忽略Закрытоаномалия
                    AppLog.d(TAG, "Camera " + cameraId + " ignored exception while closing device: " + e.getMessage());
                }
                cameraDevice = null;
            }

            // 释放鱼眼矫正器
            releaseFisheyeCorrector();

            // 释放预览 Surface
            if (previewSurface != null) {
                try {
                    previewSurface.release();
                    AppLog.d(TAG, "Camera " + cameraId + " released preview surface");
                } catch (Exception e) {
                    AppLog.d(TAG, "Camera " + cameraId + " ignored exception while releasing preview surface: " + e.getMessage());
                }
                previewSurface = null;
            }

            // Очистка Запись Surface 引用（重要：防止 Surface abandoned Ошибка)
            // 注意：这里只 очистка引用，不 release()，因为 Surface 由 VideoRecorder управление
            if (recordSurface != null) {
                AppLog.d(TAG, "Camera " + cameraId + " clearing record surface reference");
                recordSurface = null;
            }

            // Очистка 悬浮窗 Surface 引用
            if (mainFloatingSurface != null) {
                AppLog.d(TAG, "Camera " + cameraId + " clearing main floating surface reference");
                mainFloatingSurface = null;
                mainFloatingSurfaceTexture = null;
            }
            if (secondaryDisplaySurface != null) {
                AppLog.d(TAG, "Camera " + cameraId + " clearing secondary display surface reference");
                secondaryDisplaySurface = null;
                secondaryDisplaySurfaceTexture = null;
            }

            // 释放ImageReader
            if (imageReader != null) {
                try {
                    imageReader.close();
                    AppLog.d(TAG, "Camera " + cameraId + " released image reader");
                } catch (Exception e) {
                    AppLog.d(TAG, "Camera " + cameraId + " ignored exception while closing image reader: " + e.getMessage());
                }
                imageReader = null;
            }

            stopBackgroundThread();

            AppLog.d(TAG, "Camera " + cameraId + " closed");
            if (callback != null) {
                callback.onCameraClosed(cameraId);
            }
        }
    }

    // ==================== 鱼眼矫正相Выкл方法 ====================

    /**
     * 释放鱼眼矫正器
     */
    private void releaseFisheyeCorrector() {
        if (fisheyeCorrector != null) {
            try {
                fisheyeCorrector.release();
            } catch (Exception e) {
                AppLog.d(TAG, "Camera " + cameraId + " ignored exception releasing fisheye corrector: " + e.getMessage());
            }
            fisheyeCorrector = null;
        }
        fisheyePrimaryIsFloating = false;
    }

    /**
     * 实时обновление鱼眼矫正参数（由悬浮窗调参时调用，无需重建 session)
     */
    public void updateFisheyeParams(AppConfig appConfig) {
        if (fisheyeCorrector != null && fisheyeCorrector.isInitialized()) {
            fisheyeCorrector.loadParams(appConfig);
        }
    }

    /**
     * 鱼眼矫正ВклВыкл切换后необходимо重建预览 session
     * 因为необходимо切换 Surface（直接 / 间 GL)
     *
     * 注意：不能直接 release previewSurface，因为旧 session 可能仍 использование它。
     * 只需释放 FisheyeCorrector 并置空 previewSurface 引用，
     * session Закрыто时会自然отключено SurfaceTexture   producer Подключение。
     */
    public void recreateForFisheyeToggle() {
        AppLog.d(TAG, "Camera " + cameraId + " recreating session for fisheye toggle");
        releaseFisheyeCorrector();
        previewSurface = null; // 不 release，让 session Закрыто时自然отключено
        recreateSession();
    }

    /**
     * вручную触发重连（Сброс重连计数)
     */
    public void reconnect() {
        // Если 不 主实例，不выполнение重连операция
        if (!isPrimaryInstance) {
            AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") is SECONDARY instance, skipping reconnect");
            return;
        }
        
        synchronized (reconnectLock) {
            AppLog.d(TAG, "Camera " + cameraId + " manual reconnect requested (PRIMARY instance)");
            
            // Отмена所有待выполнение 重连задача
            if (reconnectRunnable != null && backgroundHandler != null) {
                backgroundHandler.removeCallbacks(reconnectRunnable);
                reconnectRunnable = null;
            }
            
            reconnectAttempts = 0;
            shouldReconnect = true;
            isReconnecting = false;
        }
        closeCamera();
        openCamera();
    }

    /**
     * проверкаКамера 否Подключено
     */
    public boolean isConnected() {
        return cameraDevice != null;
    }

    /**
     * 生命周期：ПаузаКамера（App退 до Фоновый режим时调用)
     * Пауза时不会触发автоматически重连，因为 主动Пауза
     */
    public void pauseByLifecycle() {
        // Если 不 主实例，不выполнениеПаузаоперация
        if (!isPrimaryInstance) {
            AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") is SECONDARY instance, skipping pauseByLifecycle");
            return;
        }
        
        synchronized (reconnectLock) {
            AppLog.d(TAG, "Camera " + cameraId + " paused by lifecycle (PRIMARY instance)");
            isPausedByLifecycle = true;
            shouldReconnect = false;  // Отключитьавтоматически重连，因为 主动Пауза
            isReconnecting = false;  // очистка重连Статус
            
            // Отмена所有待выполнение 重连задача
            if (reconnectRunnable != null && backgroundHandler != null) {
                backgroundHandler.removeCallbacks(reconnectRunnable);
                reconnectRunnable = null;
            }
        }
        closeCamera();
    }

    /**
     * 生命周期：ВосстановлениеКамера（App返回Передний план时调用)
     * Если Камерадо ПаузаСтатус，会автоматически重新открыть
     */
    public void resumeByLifecycle() {
        // Если 不 主实例，不выполнениеВосстановлениеоперация
        if (!isPrimaryInstance) {
            AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") is SECONDARY instance, skipping resumeByLifecycle");
            return;
        }
        
        boolean shouldOpen = false;
        synchronized (reconnectLock) {
            AppLog.d(TAG, "Camera " + cameraId + " resume by lifecycle (PRIMARY instance)");
            if (isPausedByLifecycle) {
                isPausedByLifecycle = false;
                reconnectAttempts = 0;  // Сброс重连计数
                shouldReconnect = true;  // Включитьавтоматически重连
                isReconnecting = false;  // очистка重连Статус
                shouldOpen = true;
                
                // Отмена所有待выполнение 重连задача
                if (reconnectRunnable != null && backgroundHandler != null) {
                    backgroundHandler.removeCallbacks(reconnectRunnable);
                    reconnectRunnable = null;
                }
            }
        }
        if (shouldOpen) {
            openCamera();
        }
    }

    /**
     * 强制重新открытьКамера（用于 от Фоновый режим返回Передний план时)
     * т.е.使КамераТекущий ПодключениеСтатус，также会重新открыть
     */
    public void forceReopen() {
        // Если 不 主实例，不выполнение重Вклоперация
        if (!isPrimaryInstance) {
            AppLog.d(TAG, "Camera " + cameraId + " (" + cameraPosition + ") is SECONDARY instance, skipping forceReopen");
            return;
        }
        
        synchronized (reconnectLock) {
            AppLog.d(TAG, "Camera " + cameraId + " force reopen requested (PRIMARY instance)");
            
            // Отмена所有待выполнение 重连задача
            if (reconnectRunnable != null && backgroundHandler != null) {
                backgroundHandler.removeCallbacks(reconnectRunnable);
                reconnectRunnable = null;
            }
            
            // СбросСтатус
            reconnectAttempts = 0;
            shouldReconnect = true;
            isReconnecting = false;
            
            // Закрыто现有Подключение
            if (cameraDevice != null) {
                try {
                    if (captureSession != null) {
                        captureSession.close();
                        captureSession = null;
                    }
                } catch (Exception e) {
                    // 忽略Закрытоаномалия
                    AppLog.d(TAG, "Camera " + cameraId + " ignored exception during session close: " + e.getMessage());
                }
                
                try {
                    cameraDevice.close();
                    cameraDevice = null;
                } catch (Exception e) {
                    AppLog.d(TAG, "Camera " + cameraId + " ignored exception during device close: " + e.getMessage());
                }
            }
            
            // 延迟重新открыть，避免立т.е.операция
            if (backgroundHandler != null) {
                backgroundHandler.postDelayed(() -> {
                    synchronized (reconnectLock) {
                        try {
                            // 验证КамераID 否существует
                            String[] availableCameraIds = cameraManager.getCameraIdList();
                            boolean cameraExists = false;
                            for (String id : availableCameraIds) {
                                if (id.equals(cameraId)) {
                                    cameraExists = true;
                                    break;
                                }
                            }
                            
                            if (!cameraExists) {
                                AppLog.e(TAG, "Camera ID " + cameraId + " does not exist anymore. Available IDs: " +
                                         java.util.Arrays.toString(availableCameraIds));
                                shouldReconnect = false;
                                return;
                            }
                            
                            // 验证Камера 否真正Доступно
                            CameraCharacteristics characteristics;
                            try {
                                characteristics = cameraManager.getCameraCharacteristics(cameraId);
                            } catch (Exception e) {
                                AppLog.e(TAG, "Camera " + cameraId + " failed to get characteristics - camera may be invalid", e);
                                shouldReconnect = false;
                                return;
                            }
                            
                            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
                            AppLog.d(TAG, "Camera " + cameraId + " force reopen initiated");
                        } catch (CameraAccessException e) {
                            AppLog.e(TAG, "Failed to force reopen camera " + cameraId, e);
                            if (shouldReconnect) {
                                scheduleReconnect();
                            }
                        } catch (SecurityException e) {
                            AppLog.e(TAG, "No camera permission during force reopen", e);
                        } catch (IllegalArgumentException e) {
                            AppLog.e(TAG, "Camera " + cameraId + " invalid argument - camera may be virtual/invalid", e);
                            shouldReconnect = false;
                        } catch (RuntimeException e) {
                            AppLog.e(TAG, "Camera " + cameraId + " runtime exception - camera may be virtual/invalid", e);
                            shouldReconnect = false;
                        }
                    }
                }, 300);  // 延迟300ms， Система时间释放资源
            } else {
                // Если Фоновый режим线程не существует，重新Запуск
                startBackgroundThread();
                backgroundHandler.postDelayed(() -> {
                    openCamera();
                }, 300);
            }
        }
    }
    
    // ==================== 亮度/Шумоподавление调节相Выкл方法 ====================
    
    /**
     * Настройки 否Включить亮度/Шумоподавление调节
     * @param enabled true 表示Включить
     */
    public void setImageAdjustEnabled(boolean enabled) {
        this.imageAdjustEnabled = enabled;
        AppLog.d(TAG, "Camera " + cameraId + " image adjust: " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     *  от конфигурация读取并Приложение亮度/Шумоподавление调节参数
     * @param requestBuilder 求构建器
     */
    private void applyImageAdjustParamsFromConfig(CaptureRequest.Builder requestBuilder) {
        try {
            AppConfig appConfig = new AppConfig(context);
            
            // ПриложениеЭкспозиция
            int exposureComp = appConfig.getExposureCompensation();
            if (exposureComp != 0) {
                Range<Integer> range = getExposureCompensationRange();
                if (range != null) {
                    int clampedValue = Math.max(range.getLower(), Math.min(exposureComp, range.getUpper()));
                    requestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedValue);
                    AppLog.d(TAG, "Camera " + cameraId + " applied exposure compensation: " + clampedValue);
                }
            }
            
            // ПриложениеБаланс белогорежим
            int awbMode = appConfig.getAwbMode();
            if (awbMode >= 0) {
                int[] supportedModes = getSupportedAwbModes();
                if (supportedModes != null && isModeSupported(supportedModes, awbMode)) {
                    requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
                    AppLog.d(TAG, "Camera " + cameraId + " applied AWB mode: " + awbMode);
                }
            }
            
            // ПриложениеТональная компрессиярежим
            int tonemapMode = appConfig.getTonemapMode();
            if (tonemapMode >= 0) {
                int[] supportedModes = getSupportedTonemapModes();
                if (supportedModes != null && isModeSupported(supportedModes, tonemapMode)) {
                    requestBuilder.set(CaptureRequest.TONEMAP_MODE, tonemapMode);
                    AppLog.d(TAG, "Camera " + cameraId + " applied tonemap mode: " + tonemapMode);
                }
            }
            
            // ПриложениеРезкостьрежим
            int edgeMode = appConfig.getEdgeMode();
            if (edgeMode >= 0) {
                int[] supportedModes = getSupportedEdgeModes();
                if (supportedModes != null && isModeSupported(supportedModes, edgeMode)) {
                    requestBuilder.set(CaptureRequest.EDGE_MODE, edgeMode);
                    AppLog.d(TAG, "Camera " + cameraId + " applied edge mode: " + edgeMode);
                }
            }
            
            // ПриложениеШумоподавлениережим
            int noiseReductionMode = appConfig.getNoiseReductionMode();
            if (noiseReductionMode >= 0) {
                int[] supportedModes = getSupportedNoiseReductionModes();
                if (supportedModes != null && isModeSupported(supportedModes, noiseReductionMode)) {
                    requestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode);
                    AppLog.d(TAG, "Camera " + cameraId + " applied noise reduction mode: " + noiseReductionMode);
                }
            }
            
            // ПриложениеЭффектырежим
            int effectMode = appConfig.getEffectMode();
            if (effectMode >= 0) {
                int[] supportedModes = getSupportedEffectModes();
                if (supportedModes != null && isModeSupported(supportedModes, effectMode)) {
                    requestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, effectMode);
                    AppLog.d(TAG, "Camera " + cameraId + " applied effect mode: " + effectMode);
                }
            }
            
            AppLog.d(TAG, "Camera " + cameraId + " image adjust params applied from config");
            
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to apply image adjust params from config", e);
        }
    }
    
    /**
     * Получение 否Включить亮度/Шумоподавление调节
     */
    public boolean isImageAdjustEnabled() {
        return imageAdjustEnabled;
    }
    
    /**
     * ПолучениеЭкспозиция范围
     * @return Экспозиция范围 [min, max]，Если не поддерживаетсяВозвращает null
     */
    public Range<Integer> getExposureCompensationRange() {
        try {
            CameraCharacteristics chars = getCameraCharacteristics();
            if (chars != null) {
                return chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to get exposure compensation range", e);
        }
        return null;
    }
    
    /**
     * ПолучениеЭкспозиция步长
     * @return Экспозиция步长（EV 单位)，Если не поддерживаетсяВозвращает null
     */
    public android.util.Rational getExposureCompensationStep() {
        try {
            CameraCharacteristics chars = getCameraCharacteristics();
            if (chars != null) {
                return chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to get exposure compensation step", e);
        }
        return null;
    }
    
    /**
     * ПолучениеПоддерживаемые Баланс белогорежим
     * @return Поддерживаемые Баланс белогорежим数 групп，Если не поддерживаетсяВозвращает null
     */
    public int[] getSupportedAwbModes() {
        try {
            CameraCharacteristics chars = getCameraCharacteristics();
            if (chars != null) {
                return chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to get supported AWB modes", e);
        }
        return null;
    }
    
    /**
     * ПолучениеПоддерживаемые Тональная компрессиярежим
     * @return Поддерживаемые Тональная компрессиярежим数 групп，Если не поддерживаетсяВозвращает null
     */
    public int[] getSupportedTonemapModes() {
        try {
            CameraCharacteristics chars = getCameraCharacteristics();
            if (chars != null) {
                return chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to get supported tonemap modes", e);
        }
        return null;
    }
    
    /**
     * ПолучениеПоддерживаемые Резкостьрежим
     * @return Поддерживаемые Резкостьрежим数 групп，Если не поддерживаетсяВозвращает null
     */
    public int[] getSupportedEdgeModes() {
        try {
            CameraCharacteristics chars = getCameraCharacteristics();
            if (chars != null) {
                return chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to get supported edge modes", e);
        }
        return null;
    }
    
    /**
     * ПолучениеПоддерживаемые Шумоподавлениережим
     * @return Поддерживаемые Шумоподавлениережим数 групп，Если не поддерживаетсяВозвращает null
     */
    public int[] getSupportedNoiseReductionModes() {
        try {
            CameraCharacteristics chars = getCameraCharacteristics();
            if (chars != null) {
                return chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to get supported noise reduction modes", e);
        }
        return null;
    }
    
    /**
     * ПолучениеПоддерживаемые Эффектырежим
     * @return Поддерживаемые Эффектырежим数 групп，Если не поддерживаетсяВозвращает null
     */
    public int[] getSupportedEffectModes() {
        try {
            CameraCharacteristics chars = getCameraCharacteristics();
            if (chars != null) {
                return chars.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to get supported effect modes", e);
        }
        return null;
    }
    
    /**
     * ПолучениеКамера特性（带缓存)
     */
    private CameraCharacteristics getCameraCharacteristics() {
        if (cameraCharacteristics != null) {
            return cameraCharacteristics;
        }
        
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            return cameraCharacteristics;
        } catch (CameraAccessException e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to get characteristics", e);
            return null;
        }
    }
    
    /**
     * 实时обновление亮度/Шумоподавление调节参数
     * 参数会立т.е.Приложение до 预览 и Запись
     * 
     * @param exposureCompensation Экспозиция值（Integer.MIN_VALUE 表示不Настройки)
     * @param awbMode Баланс белогорежим（-1 表示不Настройки)
     * @param tonemapMode Тональная компрессиярежим（-1 表示不Настройки)
     * @param edgeMode Резкостьрежим（-1 表示不Настройки)
     * @param noiseReductionMode Шумоподавлениережим（-1 表示不Настройки)
     * @param effectMode Эффектырежим（-1 表示不Настройки)
     * @return true 表示Успешно，false 表示Ошибка
     */
    public boolean updateImageAdjustParams(int exposureCompensation, int awbMode, int tonemapMode,
                                           int edgeMode, int noiseReductionMode, int effectMode) {
        if (!imageAdjustEnabled) {
            AppLog.d(TAG, "Camera " + cameraId + " image adjust not enabled, skip update");
            return false;
        }
        
        if (cameraDevice == null || captureSession == null || currentRequestBuilder == null) {
            AppLog.w(TAG, "Camera " + cameraId + " not ready for image adjust update");
            return false;
        }
        
        try {
            // ПриложениеЭкспозиция
            if (exposureCompensation != Integer.MIN_VALUE) {
                Range<Integer> range = getExposureCompensationRange();
                if (range != null) {
                    // 确保值 действует范围内
                    int clampedValue = Math.max(range.getLower(), Math.min(exposureCompensation, range.getUpper()));
                    currentRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedValue);
                    AppLog.d(TAG, "Camera " + cameraId + " set exposure compensation: " + clampedValue + " (range: " + range + ")");
                }
            }
            
            // ПриложениеБаланс белогорежим
            if (awbMode >= 0) {
                int[] supportedModes = getSupportedAwbModes();
                if (supportedModes != null && isModeSupported(supportedModes, awbMode)) {
                    currentRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode);
                    AppLog.d(TAG, "Camera " + cameraId + " set AWB mode: " + awbMode);
                } else {
                    AppLog.w(TAG, "Camera " + cameraId + " AWB mode " + awbMode + " not supported");
                }
            }
            
            // ПриложениеТональная компрессиярежим
            if (tonemapMode >= 0) {
                int[] supportedModes = getSupportedTonemapModes();
                if (supportedModes != null && isModeSupported(supportedModes, tonemapMode)) {
                    currentRequestBuilder.set(CaptureRequest.TONEMAP_MODE, tonemapMode);
                    AppLog.d(TAG, "Camera " + cameraId + " set tonemap mode: " + tonemapMode);
                } else {
                    AppLog.w(TAG, "Camera " + cameraId + " tonemap mode " + tonemapMode + " not supported");
                }
            }
            
            // ПриложениеРезкостьрежим
            if (edgeMode >= 0) {
                int[] supportedModes = getSupportedEdgeModes();
                if (supportedModes != null && isModeSupported(supportedModes, edgeMode)) {
                    currentRequestBuilder.set(CaptureRequest.EDGE_MODE, edgeMode);
                    AppLog.d(TAG, "Camera " + cameraId + " set edge mode: " + edgeMode);
                } else {
                    AppLog.w(TAG, "Camera " + cameraId + " edge mode " + edgeMode + " not supported");
                }
            }
            
            // ПриложениеШумоподавлениережим
            if (noiseReductionMode >= 0) {
                int[] supportedModes = getSupportedNoiseReductionModes();
                if (supportedModes != null && isModeSupported(supportedModes, noiseReductionMode)) {
                    currentRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode);
                    AppLog.d(TAG, "Camera " + cameraId + " set noise reduction mode: " + noiseReductionMode);
                } else {
                    AppLog.w(TAG, "Camera " + cameraId + " noise reduction mode " + noiseReductionMode + " not supported");
                }
            }
            
            // ПриложениеЭффектырежим
            if (effectMode >= 0) {
                int[] supportedModes = getSupportedEffectModes();
                if (supportedModes != null && isModeSupported(supportedModes, effectMode)) {
                    currentRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, effectMode);
                    AppLog.d(TAG, "Camera " + cameraId + " set effect mode: " + effectMode);
                } else {
                    AppLog.w(TAG, "Camera " + cameraId + " effect mode " + effectMode + " not supported");
                }
            }
            
            // 重新提交求（实时生效)
            captureSession.setRepeatingRequest(currentRequestBuilder.build(), null, backgroundHandler);
            AppLog.d(TAG, "Camera " + cameraId + " image adjust params updated successfully");
            return true;
            
        } catch (CameraAccessException e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to update image adjust params", e);
            return false;
        } catch (IllegalStateException e) {
            AppLog.e(TAG, "Camera " + cameraId + " session invalid during image adjust update", e);
            return false;
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " unexpected error during image adjust update", e);
            return false;
        }
    }
    
    /**
     * проверкарежим 否 поддержка列表
     */
    private boolean isModeSupported(int[] supportedModes, int mode) {
        if (supportedModes == null) {
            return false;
        }
        for (int supported : supportedModes) {
            if (supported == mode) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * ПолучениеТекущий求构建器（用于Внешнееотладка)
     */
    public CaptureRequest.Builder getCurrentRequestBuilder() {
        return currentRequestBuilder;
    }
    
    /**
     *  от  CaptureResult 读取相机实际использование 参数
     */
    private void readActualParamsFromResult(TotalCaptureResult result) {
        try {
            // Экспозиция
            Integer exposure = result.get(TotalCaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            if (exposure != null) {
                actualExposureCompensation = exposure;
            }
            
            // Баланс белогорежим
            Integer awb = result.get(TotalCaptureResult.CONTROL_AWB_MODE);
            if (awb != null) {
                actualAwbMode = awb;
            }
            
            // Резкостьрежим
            Integer edge = result.get(TotalCaptureResult.EDGE_MODE);
            if (edge != null) {
                actualEdgeMode = edge;
            }
            
            // Шумоподавлениережим
            Integer noise = result.get(TotalCaptureResult.NOISE_REDUCTION_MODE);
            if (noise != null) {
                actualNoiseReductionMode = noise;
            }
            
            // Эффектырежим
            Integer effect = result.get(TotalCaptureResult.CONTROL_EFFECT_MODE);
            if (effect != null) {
                actualEffectMode = effect;
            }
            
            // Тональная компрессиярежим
            Integer tonemap = result.get(TotalCaptureResult.TONEMAP_MODE);
            if (tonemap != null) {
                actualTonemapMode = tonemap;
            }
            
            AppLog.d(TAG, "Camera " + cameraId + " actual params: exposure=" + actualExposureCompensation +
                    ", awb=" + actualAwbMode + ", edge=" + actualEdgeMode + 
                    ", noise=" + actualNoiseReductionMode + ", effect=" + actualEffectMode +
                    ", tonemap=" + actualTonemapMode);
        } catch (Exception e) {
            AppLog.e(TAG, "Camera " + cameraId + " failed to read actual params", e);
        }
    }
    
    // ==================== Получение实际参数 方法 ====================
    
    /**
     * Получение相机实际использование Экспозиция值
     */
    public int getActualExposureCompensation() {
        return actualExposureCompensation;
    }
    
    /**
     * Получение相机实际использование Баланс белогорежим
     */
    public int getActualAwbMode() {
        return actualAwbMode;
    }
    
    /**
     * Получение相机实际использование Резкостьрежим
     */
    public int getActualEdgeMode() {
        return actualEdgeMode;
    }
    
    /**
     * Получение相机实际использование Шумоподавлениережим
     */
    public int getActualNoiseReductionMode() {
        return actualNoiseReductionMode;
    }
    
    /**
     * Получение相机实际использование Эффектырежим
     */
    public int getActualEffectMode() {
        return actualEffectMode;
    }
    
    /**
     * Получение相机实际использование Тональная компрессиярежим
     */
    public int getActualTonemapMode() {
        return actualTonemapMode;
    }
    
    /**
     *  否读取过实际参数
     */
    public boolean hasActualParams() {
        return hasReadActualParams;
    }
}
