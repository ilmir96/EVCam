package com.kooo.evcam.camera;

import android.util.Size;

/**
 * Камера回调接口
 */
public interface CameraCallback {
    /**
     * КамераоткрытьУспешно
     */
    void onCameraOpened(String cameraId);

    /**
     * Камераконфигурациязавершение，Вкл始预览
     */
    void onCameraConfigured(String cameraId);

    /**
     * КамераЗакрыто
     */
    void onCameraClosed(String cameraId);

    /**
     * КамераОшибка
     */
    void onCameraError(String cameraId, int errorCode);

    /**
     * 预览尺寸确定
     * @param cameraId КамераID
     * @param previewSize 预览尺寸
     */
    void onPreviewSizeChosen(String cameraId, Size previewSize);
}
