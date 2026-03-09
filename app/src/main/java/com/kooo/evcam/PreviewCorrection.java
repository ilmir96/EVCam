package com.kooo.evcam;

import android.graphics.Matrix;

/**
 * 预览画面矫正инструмент类
 *  主界面预览 TextureView  画面进行缩放/平移矫正
 *  基础变换（Поворот 等)после叠加Приложение，每 кам.Камера参数独立
 */
public final class PreviewCorrection {
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 8.0f;
    private static final float MIN_TRANSLATE = -5.0f;
    private static final float MAX_TRANSLATE = 5.0f;

    private PreviewCorrection() {}

    /**
     * 将预览矫正参数叠加 до 有  Matrix 
     * 应 基础变换（Поворот /缩放)после调用
     *
     * @param matrix     содержит基础变换  Matrix（会 地изменение)
     * @param appConfig  конфигурация
     * @param cameraPos  КамераПозиция（front/back/left/right)
     * @param viewWidth  TextureView 宽度
     * @param viewHeight TextureView Высокий度
     */
    public static void postApply(Matrix matrix, AppConfig appConfig, String cameraPos,
                                 int viewWidth, int viewHeight) {
        if (matrix == null || appConfig == null || cameraPos == null) return;
        if (!appConfig.isPreviewCorrectionEnabled()) return;
        if (viewWidth <= 0 || viewHeight <= 0) return;

        float scaleX = clamp(appConfig.getPreviewCorrectionScaleX(cameraPos), MIN_SCALE, MAX_SCALE);
        float scaleY = clamp(appConfig.getPreviewCorrectionScaleY(cameraPos), MIN_SCALE, MAX_SCALE);
        float translateX = clamp(appConfig.getPreviewCorrectionTranslateX(cameraPos), MIN_TRANSLATE, MAX_TRANSLATE);
        float translateY = clamp(appConfig.getPreviewCorrectionTranslateY(cameraPos), MIN_TRANSLATE, MAX_TRANSLATE);

        float centerX = viewWidth / 2f;
        float centerY = viewHeight / 2f;

        matrix.postScale(scaleX, scaleY, centerX, centerY);
        matrix.postTranslate(translateX * viewWidth, translateY * viewHeight);
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
