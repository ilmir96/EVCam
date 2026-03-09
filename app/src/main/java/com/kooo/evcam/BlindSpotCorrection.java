package com.kooo.evcam;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.TextureView;

public final class BlindSpotCorrection {
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 3.0f;
    private static final float MIN_TRANSLATE = -1.0f;
    private static final float MAX_TRANSLATE = 1.0f;

    private BlindSpotCorrection() {}

    /**
     * 便捷重载：窗口Не 互换宽Высокий时использование（если副屏 secondaryTextureView)。
     * 矫正Поворот только做纯Поворот ，不做比例修正，可能出现黑角 и 轻微形变。
     */
    public static void apply(TextureView textureView, AppConfig appConfig, String cameraPos, int baseRotation) {
        apply(textureView, appConfig, cameraPos, baseRotation, false);
    }

    /**
     * @param windowSwapped 调用方 否因矫正Поворот 互换窗口宽Высокий
     *                      （MainFloatingWindowView / BlindSpotFloatingWindowView 会互换，副屏不会)。
     *                      互换后необходимо将 correctionRotation 纳入 center-crop 计算并做比例修正，
     *                      否则 90° 时画面会 Ошибка放大裁切。
     */
    public static void apply(TextureView textureView, AppConfig appConfig, String cameraPos, int baseRotation, boolean windowSwapped) {
        if (textureView == null || appConfig == null) return;

        textureView.post(() -> {
            int viewWidth = textureView.getWidth();
            int viewHeight = textureView.getHeight();
            if (viewWidth <= 0 || viewHeight <= 0) return;

            float centerX = viewWidth / 2f;
            float centerY = viewHeight / 2f;

            Matrix matrix = new Matrix();

            // Получение预览尺寸
            int previewW = 0, previewH = 0;
            if (cameraPos != null) {
                com.kooo.evcam.camera.MultiCameraManager cm = com.kooo.evcam.camera.CameraManagerHolder.getInstance().getCameraManager();
                if (cm != null) {
                    com.kooo.evcam.camera.SingleCamera camera = cm.getCamera(cameraPos);
                    if (camera != null) {
                        android.util.Size previewSize = camera.getPreviewSize();
                        if (previewSize != null) {
                            previewW = previewSize.getWidth();
                            previewH = previewSize.getHeight();
                        }
                    }
                }
            }

            // Получение矫正Поворот 角度（0~360 任意角度)
            int correctionRotation = 0;
            if (appConfig.isBlindSpotCorrectionEnabled() && cameraPos != null) {
                correctionRotation = appConfig.getBlindSpotCorrectionRotation(cameraPos);
            }

            // center-crop 判断действует宽Высокий比时использование Поворот 角度：
            // - 悬浮窗（windowSwapped=true)：用 totalRotation（base + correction)，
            //   因为悬浮窗根据矫正Поворот 互换宽Высокий，center-crop 必须匹配，
            //   否则 90° 时会误判为Горизонтальная预览放进Вертикальная窗口而放大 3x。
            // - 副屏（windowSwapped=false)：только用 baseRotation，
            //   副屏不互换宽Высокий，Если также用 totalRotation 会  45° 处突变。
            int cropRotation = windowSwapped
                    ? ((baseRotation + correctionRotation) % 360 + 360) % 360
                    : ((baseRotation % 360) + 360) % 360;
            boolean isMorePortrait = isCloserToPortrait(cropRotation);

            // 居填充（center-crop)
            if (previewW > 0 && previewH > 0) {
                float effectivePreviewW = isMorePortrait ? previewH : previewW;
                float effectivePreviewH = isMorePortrait ? previewW : previewH;
                float previewAspect = effectivePreviewW / effectivePreviewH;
                float viewAspect = (float) viewWidth / viewHeight;
                float scaleXFill, scaleYFill;
                if (previewAspect > viewAspect) {
                    scaleYFill = 1.0f;
                    scaleXFill = previewAspect / viewAspect;
                } else {
                    scaleXFill = 1.0f;
                    scaleYFill = viewAspect / previewAspect;
                }
                matrix.postScale(scaleXFill, scaleYFill, centerX, centerY);
            }

            // Приложение baseRotation（副屏方 к 补偿)
            if (baseRotation != 0) {
                matrix.postRotate(baseRotation, centerX, centerY);
                if (baseRotation == 90 || baseRotation == 270) {
                    float scale = (float) viewWidth / (float) viewHeight;
                    matrix.postScale(1f / scale, scale, centerX, centerY);
                }
            }

            // Приложение矫正参数
            if (appConfig.isBlindSpotCorrectionEnabled() && cameraPos != null) {
                float scaleX = clamp(appConfig.getBlindSpotCorrectionScaleX(cameraPos), MIN_SCALE, MAX_SCALE);
                float scaleY = clamp(appConfig.getBlindSpotCorrectionScaleY(cameraPos), MIN_SCALE, MAX_SCALE);
                float translateX = clamp(appConfig.getBlindSpotCorrectionTranslateX(cameraPos), MIN_TRANSLATE, MAX_TRANSLATE);
                float translateY = clamp(appConfig.getBlindSpotCorrectionTranslateY(cameraPos), MIN_TRANSLATE, MAX_TRANSLATE);

                if (correctionRotation != 0 && windowSwapped && previewW > 0 && previewH > 0) {
                    // 悬浮窗互换宽Высокий且有Поворот  →   buffer 空间Поворот ，保证画面比例不变形
                    //
                    // TextureView По умолчанию将 buffer（если 1920×1080)非等比拉伸 до  view（если 360×640)。
                    // 直接 postRotate 会混合 X/Y 方 к 不同 拉伸比，导致画面变菱形。
                    //
                    // 正确做法：重建整 шт.矩阵
                    // 1. 还原 до  buffer 坐标系（消除非等比拉伸，像素变正方形)
                    // 2.   buffer 空间Поворот （比例绝 正确)
                    // 3. 等比缩放回 view（保持比例，填充窗口)
                    //
                    // 缩放因子用 90° 时 填充值（= max(vW/bH, vH/bW))，
                    // 保证 0°/90°/180°/270° 完美填满，间角度保持同等大小，
                    // 超出窗口 部分自然裁切。
                    matrix.reset();

                    // 还原 до  buffer 坐标系
                    matrix.postScale((float) previewW / viewWidth, (float) previewH / viewHeight, centerX, centerY);

                    //   buffer 空间Приложение baseRotation
                    if (baseRotation != 0) {
                        matrix.postRotate(baseRotation, centerX, centerY);
                    }

                    //   buffer 空间Приложение矫正Поворот 
                    matrix.postRotate(correctionRotation, centerX, centerY);

                    // 等比缩放填充 view（用 90°  填充因子作为常量)
                    float fillScale = Math.max((float) viewWidth / previewH, (float) viewHeight / previewW);
                    matrix.postScale(fillScale, fillScale, centerX, centerY);

                    // 用户矫正参数
                    matrix.postScale(scaleX, scaleY, centerX, centerY);
                    matrix.postTranslate(translateX * viewWidth, translateY * viewHeight);
                } else {
                    // Не 互换 / 无Поворот ：沿用原有逻辑
                    matrix.postScale(scaleX, scaleY, centerX, centerY);

                    if (correctionRotation != 0) {
                        // 副屏窗口不互换 → 纯Поворот ，保留黑角
                        matrix.postRotate(correctionRotation, centerX, centerY);
                    }

                    matrix.postTranslate(translateX * viewWidth, translateY * viewHeight);
                }
            }

            textureView.setTransform(matrix);
        });
    }

    /**
     * 判断Поворот 角度 否更接近竖屏（т.е.необходимо交换宽Высокий)
     * 45°~135°  и  225°~315° 范围视为更接近竖屏
     */
    public static boolean isCloserToPortrait(int rotation) {
        int normalized = ((rotation % 360) + 360) % 360; // 归一化 до  0~359
        int mod180 = normalized % 180; // 映射 до  0~179
        return (mod180 >= 45 && mod180 < 135);
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}

