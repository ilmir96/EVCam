package com.kooo.evcam;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.kooo.evcam.camera.CameraManagerHolder;
import com.kooo.evcam.camera.MultiCameraManager;
import com.kooo.evcam.camera.SingleCamera;

/**
 * 主屏悬浮窗视图
 * поддержка平滑拖动 и 边缘缩放
 */
public class MainFloatingWindowView extends FrameLayout {
    private static final String TAG = "MainFloatingWindowView";
    private static final int RESIZE_THRESHOLD = 50; // 边缘触发缩放 阈值（像素)

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private AppConfig appConfig;
    private TextureView textureView;
    private Surface cachedSurface;
    private SingleCamera currentCamera;
    private String desiredCameraPos;
    private boolean urgentPending = false; //  раз startCameraPreview использование紧急режим

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable retryBindRunnable;
    private int retryBindCount = 0;
    private android.animation.ValueAnimator windowAnimator;
    private boolean pendingShowAnimation = false;
    private Runnable showAnimFallback;

    private float lastX, lastY;
    private float initialX, initialY;
    private int initialWidth, initialHeight;
    private boolean isResizing = false;
    private int resizeMode = 0; // 0: 拖动, 1: 左, 2: 右, 4: , 8:  (位运算 групп合)
    private boolean isCurrentlySwapped = false;
    private boolean isDragEnabled = false; // 长按后才разрешить拖动
    private static final long LONG_PRESS_DURATION = 1000; // 长按1 сек.后才能拖动
    private static final int TOUCH_SLOP = 20; // 移动超过此距离Отмена长按检测
    private final Runnable longPressRunnable = () -> {
        isDragEnabled = true;
        // 触发轻微震动反馈，Уведомление用户可以拖动
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
        } catch (Exception ignored) {}
    };

    private BlindSpotStatusBarView statusBar;

    public MainFloatingWindowView(Context context) {
        this(context, new AppConfig(context));
    }

    public MainFloatingWindowView(Context context, AppConfig appConfig) {
        super(context);
        this.appConfig = appConfig;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        desiredCameraPos = appConfig.getMainFloatingCamera();
        init();
    }

    private void init() {
        boolean isMultiview = appConfig.isMultiviewCarModel();
        int layoutRes = isMultiview
                ? R.layout.presentation_secondary_display_multiview
                : R.layout.presentation_secondary_display;
        LayoutInflater.from(getContext()).inflate(layoutRes, this);
        textureView = findViewById(R.id.secondary_texture_view);

        statusBar = findViewById(R.id.blind_spot_status_bar);
        applyStatusBarStyle();

        // 圆角裁切
        float cornerRadius = 8 * getContext().getResources().getDisplayMetrics().density;
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);

        params = new WindowManager.LayoutParams(
                appConfig.getMainFloatingWidth(),
                appConfig.getMainFloatingHeight(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = appConfig.getMainFloatingX();
        params.y = appConfig.getMainFloatingY();

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int width, int height) {
                String cameraPos = desiredCameraPos != null ? desiredCameraPos : appConfig.getMainFloatingCamera();
                MultiCameraManager cm = CameraManagerHolder.getInstance().getCameraManager();
                if (cm != null) {
                    SingleCamera camera = cm.getCamera(cameraPos);
                    if (camera != null) {
                        Size previewSize = camera.getPreviewSize();
                        if (previewSize != null) {
                            surface.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                        }
                    }
                }
                if (cachedSurface != null) {
                    cachedSurface.release();
                }
                cachedSurface = new Surface(surface);
                boolean urgent = urgentPending;
                urgentPending = false;
                startCameraPreview(cachedSurface, urgent);
                applyTransformNow();
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) {
                applyTransformNow();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) {
                cancelRetryBind();
                stopCameraPreview();
                if (cachedSurface != null) {
                    cachedSurface.release();
                    cachedSurface = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) {
                if (pendingShowAnimation) {
                    pendingShowAnimation = false;
                    if (showAnimFallback != null) {
                        mainHandler.removeCallbacks(showAnimFallback);
                        showAnimFallback = null;
                    }
                    playShowAnimation();
                }
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getRawX();
        float y = event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                initialX = params.x;
                initialY = params.y;
                initialWidth = params.width;
                initialHeight = params.height;

                // 判断 否 边缘，进入缩放режим
                float localX = event.getX();
                float localY = event.getY();
                resizeMode = 0;
                if (localX < RESIZE_THRESHOLD) resizeMode |= 1; // 左
                if (localX > getWidth() - RESIZE_THRESHOLD) resizeMode |= 2; // 右
                if (localY < RESIZE_THRESHOLD) resizeMode |= 4; // 
                if (localY > getHeight() - RESIZE_THRESHOLD) resizeMode |= 8; // 
                
                isResizing = resizeMode != 0;
                if (appConfig.isMainFloatingLongPressDragEnabled()) {
                    isDragEnabled = false;
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION);
                } else {
                    isDragEnabled = true;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = x - lastX;
                float dy = y - lastY;

                if (!isDragEnabled) {
                    // 长按Не 达标，проверка手指 否移动过远（Отмена长按检测)
                    if (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP) {
                        mainHandler.removeCallbacks(longPressRunnable);
                    }
                    return true;
                }

                if (isResizing) {
                    boolean aspectLocked = appConfig.isMainFloatingAspectRatioLocked();
                    if (aspectLocked) {
                        // 等比例缩放：использованиеКамера原始Разрешение比例，而非Текущий窗口比例
                        float aspectRatio = (float) initialWidth / initialHeight; // fallback
                        String cameraPos = desiredCameraPos != null ? desiredCameraPos : appConfig.getMainFloatingCamera();
                        MultiCameraManager cm = CameraManagerHolder.getInstance().getCameraManager();
                        if (cm != null) {
                            SingleCamera camera = cm.getCamera(cameraPos);
                            if (camera != null) {
                                Size previewSize = camera.getPreviewSize();
                                if (previewSize != null) {
                                    if (isCurrentlySwapped) {
                                        // 矫正Поворот 导致宽Высокий互换时，比例также互换
                                        aspectRatio = (float) previewSize.getHeight() / previewSize.getWidth();
                                    } else {
                                        aspectRatio = (float) previewSize.getWidth() / previewSize.getHeight();
                                    }
                                }
                            }
                        }
                        boolean horizontal = (resizeMode & 3) != 0; // 左или右
                        boolean vertical = (resizeMode & 12) != 0;  // или

                        int newWidth = initialWidth;
                        int newHeight = initialHeight;

                        if (horizontal && !vertical) {
                            // только水平边：宽度变化驱动Высокий度
                            int dw = (resizeMode & 1) != 0 ? (int) -dx : (int) dx;
                            newWidth = initialWidth + dw;
                            newHeight = Math.round(newWidth / aspectRatio);
                        } else if (vertical && !horizontal) {
                            // только垂直边：Высокий度变化驱动宽度
                            int dh = (resizeMode & 4) != 0 ? (int) -dy : (int) dy;
                            newHeight = initialHeight + dh;
                            newWidth = Math.round(newHeight * aspectRatio);
                        } else {
                            //  角：取水平/垂直变化量绝 值较大 作为主轴
                            int dw = (resizeMode & 1) != 0 ? (int) -dx : (int) dx;
                            int dh = (resizeMode & 4) != 0 ? (int) -dy : (int) dy;
                            if (Math.abs(dw) >= Math.abs(dh)) {
                                newWidth = initialWidth + dw;
                                newHeight = Math.round(newWidth / aspectRatio);
                            } else {
                                newHeight = initialHeight + dh;
                                newWidth = Math.round(newHeight * aspectRatio);
                            }
                        }

                        if (newWidth > 200 && newHeight > 200) {
                            params.width = newWidth;
                            params.height = newHeight;
                            if ((resizeMode & 1) != 0) params.x = (int) (initialX + (initialWidth - newWidth));
                            if ((resizeMode & 4) != 0) params.y = (int) (initialY + (initialHeight - newHeight));
                        }
                    } else {
                        // 自由缩放逻辑
                        if ((resizeMode & 1) != 0) { // 左
                            int newWidth = (int) (initialWidth - dx);
                            if (newWidth > 200) {
                                params.width = newWidth;
                                params.x = (int) (initialX + dx);
                            }
                        }
                        if ((resizeMode & 2) != 0) { // 右
                            int newWidth = (int) (initialWidth + dx);
                            if (newWidth > 200) params.width = newWidth;
                        }
                        if ((resizeMode & 4) != 0) { // 
                            int newHeight = (int) (initialHeight - dy);
                            if (newHeight > 200) {
                                params.height = newHeight;
                                params.y = (int) (initialY + dy);
                            }
                        }
                        if ((resizeMode & 8) != 0) { // 
                            int newHeight = (int) (initialHeight + dy);
                            if (newHeight > 200) params.height = newHeight;
                        }
                    }
                } else {
                    // 拖动逻辑
                    params.x = (int) (initialX + dx);
                    params.y = (int) (initialY + dy);
                }
                
                windowManager.updateViewLayout(this, params);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mainHandler.removeCallbacks(longPressRunnable);
                isDragEnabled = false;
                isResizing = false;
                // Сохранитьконфигурация：若宽Высокий因矫正Поворот 而交换过，Сохранить前还原为基础值
                int saveW = params.width;
                int saveH = params.height;
                if (isCurrentlySwapped) {
                    saveW = params.height;
                    saveH = params.width;
                }
                appConfig.setMainFloatingBounds(params.x, params.y, saveW, saveH);
                return true;
        }
        return true;
    }

    private void startCameraPreview(Surface surface) {
        startCameraPreview(surface, false);
    }

    private void startCameraPreview(Surface surface, boolean urgent) {
        if (surface == null || !surface.isValid()) {
            scheduleRetryBind();
            return;
        }
        String cameraPos = desiredCameraPos != null ? desiredCameraPos : appConfig.getMainFloatingCamera();
        MultiCameraManager cameraManager = CameraManagerHolder.getInstance().getCameraManager();
        if (cameraManager == null) {
            // Holder 只инициализация 象但可能还没 调用过 getOrInit
            cameraManager = CameraManagerHolder.getInstance().getOrInit(getContext());
            if (cameraManager == null) {
                scheduleRetryBind();
                return;
            }
        }

        currentCamera = cameraManager.getCamera(cameraPos);
        if (currentCamera == null) {
            scheduleRetryBind();
            return;
        }

        cancelRetryBind();

        // 传递 SurfaceTexture 引用，便于 createCameraPreviewSession 统一Настройки buffer 尺寸
        android.graphics.SurfaceTexture st = (textureView != null && textureView.isAvailable()) ? textureView.getSurfaceTexture() : null;

        // Если Камера硬件还Не открыть（Фоновый режиминициализация时不открыть)，先открыть
        if (!currentCamera.isCameraOpened()) {
            currentCamera.setMainFloatingSurface(surface, st);
            AppLog.d(TAG, "Camera not opened yet, opening now for " + cameraPos);
            // 确保Передний планСервис绪后再открыть相机（避免冷Запуск时 CAMERA_DISABLED)
            final SingleCamera cam = currentCamera;
            CameraForegroundService.whenReady(getContext(), cam::openCamera);
        } else {
            currentCamera.setMainFloatingSurface(surface, st);
            currentCamera.recreateSession(urgent);
        }
    }

    private void stopCameraPreview() {
        stopCameraPreview(false);
    }

    private void stopCameraPreview(boolean urgent) {
        if (currentCamera != null) {
            currentCamera.stopRepeatingNow();
            currentCamera.setMainFloatingSurface(null);
            currentCamera.recreateSession(urgent);
            currentCamera = null;
        }
    }

    public void show() {
        try {
            if (this.getParent() == null) {
                boolean animEnabled = appConfig.isFloatingWindowAnimationEnabled();

                // ожидание首帧画面 до 达后再显示，避免黑屏闪烁
                if (animEnabled) {
                    setScaleX(0.85f);
                    setScaleY(0.85f);
                }
                params.alpha = 0f;
                pendingShowAnimation = true;

                windowManager.addView(this, params);
                if (textureView != null && textureView.isAvailable() && cachedSurface != null && cachedSurface.isValid()) {
                    startCameraPreview(cachedSurface);
                } else {
                    scheduleRetryBind();
                }
                applyTransformNow();

                // 安全таймаут：Если Камера迟迟没有推帧，最多等 800ms 后также直接显示
                showAnimFallback = () -> {
                    if (pendingShowAnimation) {
                        pendingShowAnimation = false;
                        playShowAnimation();
                    }
                };
                mainHandler.postDelayed(showAnimFallback, 800);
            } else {
                updateLayout();
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Error showing main floating window: " + e.getMessage());
        }
    }

    private void playShowAnimation() {
        boolean animEnabled = appConfig.isFloatingWindowAnimationEnabled();

        if (!animEnabled) {
            // 无动效：直接显示
            setScaleX(1f);
            setScaleY(1f);
            params.alpha = 1f;
            try {
                if (getParent() != null) {
                    windowManager.updateViewLayout(MainFloatingWindowView.this, params);
                }
            } catch (Exception e) {}
            return;
        }

        // 有动效：缩放 + 淡入
        if (windowAnimator != null) windowAnimator.cancel();
        windowAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        windowAnimator.setDuration(100);
        windowAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        windowAnimator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            setScaleX(0.85f + 0.15f * val);
            setScaleY(0.85f + 0.15f * val);
            params.alpha = val;
            try {
                if (getParent() != null) {
                    windowManager.updateViewLayout(MainFloatingWindowView.this, params);
                }
            } catch (Exception e) {}
        });
        windowAnimator.start();
    }

    /**
     * обновление主屏悬浮窗布局
     */
    public void updateLayout() {
        params.x = appConfig.getMainFloatingX();
        params.y = appConfig.getMainFloatingY();
        params.width = appConfig.getMainFloatingWidth();
        params.height = appConfig.getMainFloatingHeight();
        try {
            windowManager.updateViewLayout(this, params);
        } catch (Exception e) {
            AppLog.e(TAG, "Error updating main floating window layout: " + e.getMessage());
        }
        applyTransformNow();
    }

    public void dismiss() {
        cancelRetryBind();
        stopCameraPreview();
        pendingShowAnimation = false;
        if (showAnimFallback != null) {
            mainHandler.removeCallbacks(showAnimFallback);
            showAnimFallback = null;
        }

        if (getParent() == null) return;

        boolean animEnabled = appConfig.isFloatingWindowAnimationEnabled();

        if (!animEnabled) {
            // 无动效：直接移除
            params.alpha = 1f;
            try {
                windowManager.removeView(this);
            } catch (Exception e) {}
            return;
        }

        // Закрыто动效：缩放 + 淡出
        if (windowAnimator != null) {
            windowAnimator.cancel();
            windowAnimator = null;
        }
        windowAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f);
        windowAnimator.setDuration(100);
        windowAnimator.setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f));
        windowAnimator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            setScaleX(0.85f + 0.15f * val);
            setScaleY(0.85f + 0.15f * val);
            params.alpha = val;
            try {
                if (getParent() != null) {
                    windowManager.updateViewLayout(MainFloatingWindowView.this, params);
                }
            } catch (Exception e1) {}
        });
        windowAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                params.alpha = 1f;
                try {
                    if (getParent() != null) {
                        windowManager.removeView(MainFloatingWindowView.this);
                    }
                } catch (Exception e) {}
                windowAnimator = null;
            }
        });
        windowAnimator.start();
    }

    /**
     * толькоНастройки目标КамераПозиция（不выполнениеКамераоперация)。
     * 用于 show() доНастройки，避免  TextureView Не 绪时白跑 updateCamera。
     * TextureView 绪后会автоматически通过 onSurfaceTextureAvailable 触发 startCameraPreview。
     */
    public void setDesiredCamera(String cameraPos, boolean urgent) {
        desiredCameraPos = cameraPos;
        if (urgent) urgentPending = true;
    }

    /**
     * обновлениеТекущий显示 Камера
     * @param cameraPos КамераПозиция
     */
    public void updateCamera(String cameraPos) {
        updateCamera(cameraPos, false);
    }

    /**
     * обновлениеТекущий显示 Камера
     * @param cameraPos КамераПозиция
     * @param urgent 紧急режим（补盲转 к 灯触发时использование，минимум化延迟)
     */
    public void updateCamera(String cameraPos, boolean urgent) {
        desiredCameraPos = cameraPos;
        if (urgent) urgentPending = true; // 保留紧急标记，供 SurfaceTexture 绪回调использование
        MultiCameraManager cameraManager = CameraManagerHolder.getInstance().getCameraManager();
        if (cameraManager == null) {
            scheduleRetryBind();
            return;
        }

        SingleCamera newCamera = cameraManager.getCamera(cameraPos);
        if (newCamera == currentCamera) {
            AppLog.d(TAG, "Camera same as current, skip updateCamera: " + cameraPos);
            return;
        }

        stopCameraPreview(urgent);
        currentCamera = newCamera;
        if (currentCamera != null && textureView.isAvailable()) {
            android.graphics.SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null) {
                Size previewSize = currentCamera.getPreviewSize();
                if (previewSize != null) {
                    surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
                }
            }
            if (cachedSurface == null || !cachedSurface.isValid()) {
                if (cachedSurface != null) cachedSurface.release();
                cachedSurface = new Surface(textureView.getSurfaceTexture());
            }
            currentCamera.setMainFloatingSurface(cachedSurface);
            currentCamera.recreateSession(urgent);
            cancelRetryBind();
            applyTransformNow();
        } else {
            scheduleRetryBind();
        }
    }

    public void applyTransformNow() {
        String cameraPos = desiredCameraPos != null ? desiredCameraPos : appConfig.getMainFloatingCamera();

        // 矫正Поворот 更接近竖屏时，悬浮窗宽Высокий互换，让画面自然填满不裁切
        int correctionRotation = 0;
        if (appConfig.isBlindSpotCorrectionEnabled() && cameraPos != null) {
            correctionRotation = appConfig.getBlindSpotCorrectionRotation(cameraPos);
        }
        int baseW = appConfig.getMainFloatingWidth();
        int baseH = appConfig.getMainFloatingHeight();
        boolean shouldSwap = BlindSpotCorrection.isCloserToPortrait(correctionRotation);
        isCurrentlySwapped = shouldSwap;
        int targetW = shouldSwap ? baseH : baseW;
        int targetH = shouldSwap ? baseW : baseH;

        // 用户Выполняется 拖动缩放时，不覆盖 params，以免打断手势
        if (!isResizing) {
            if (params.width != targetW || params.height != targetH) {
                params.width = targetW;
                params.height = targetH;
                try {
                    if (getParent() != null) {
                        windowManager.updateViewLayout(this, params);
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "Error updating layout for rotation: " + e.getMessage());
                }
            }
        }

        BlindSpotCorrection.apply(textureView, appConfig, cameraPos, 0, isCurrentlySwapped);
    }

    private void scheduleRetryBind() {
        cancelRetryBind();
        retryBindCount++;
        long delayMs;
        if (urgentPending && retryBindCount <= 5) {
            // 紧急режим前几 раз快速重试
            delayMs = 50;
        } else if (retryBindCount <= 10) {
            delayMs = 500;
        } else if (retryBindCount <= 30) {
            delayMs = 1000;
        } else {
            delayMs = 3000;
        }
        retryBindRunnable = () -> {
            if (getParent() == null) return;
            if (textureView == null || !textureView.isAvailable()) {
                scheduleRetryBind();
                return;
            }
            if (cachedSurface == null || !cachedSurface.isValid()) {
                android.graphics.SurfaceTexture st = textureView.getSurfaceTexture();
                if (st == null) {
                    scheduleRetryBind();
                    return;
                }
                if (cachedSurface != null) {
                    try { cachedSurface.release(); } catch (Exception e) {}
                }
                cachedSurface = new Surface(st);
            }
            boolean urgent = urgentPending;
            urgentPending = false;
            startCameraPreview(cachedSurface, urgent);
        };
        mainHandler.postDelayed(retryBindRunnable, delayMs);
    }

    private void cancelRetryBind() {
        if (retryBindRunnable != null) {
            mainHandler.removeCallbacks(retryBindRunnable);
            retryBindRunnable = null;
        }
        retryBindCount = 0;
    }

    private void applyStatusBarStyle() {
        if (statusBar == null) return;
        int style = appConfig.getBlindSpotStatusBarStyle();
        if (style == BlindSpotStatusBarView.STYLE_OFF) {
            statusBar.setVisibility(View.GONE);
        } else {
            statusBar.setVisibility(View.VISIBLE);
            statusBar.setAnimationStyle(style);
            statusBar.setEffectColor(appConfig.getBlindSpotStatusBarColor());
            int alpha = (int) (appConfig.getBlindSpotStatusBarBgOpacity() / 100f * 255);
            statusBar.setBackgroundColor(android.graphics.Color.argb(alpha, 0x1A, 0x1A, 0x1A));
        }
    }

    public void updateStatusLabel(String cameraPos) {
        if (statusBar != null) {
            applyStatusBarStyle();
            statusBar.setDirection(cameraPos);
        }
    }
}
