package com.kooo.evcam.camera;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;

import com.kooo.evcam.AppConfig;
import com.kooo.evcam.AppLog;

/**
 * 全局单例，持有 MultiCameraManager 实例。
 * разрешить Фоновый режим（Service)инициализацияКамера，不依赖 MainActivity。
 * TextureView 可以  MainActivity открыть后再绑定。
 */
public class CameraManagerHolder {
    private static final String TAG = "CameraManagerHolder";
    private static CameraManagerHolder instance;
    private MultiCameraManager cameraManager;

    private CameraManagerHolder() {}

    public static synchronized CameraManagerHolder getInstance() {
        if (instance == null) {
            instance = new CameraManagerHolder();
        }
        return instance;
    }

    /**
     * Получениеинициализация  MultiCameraManager，Если Не инициализация则 Фоновый режиминициализация（TextureView=null)。
     * 可 от  Service или Activity 调用。
     */
    public synchronized MultiCameraManager getOrInit(Context context) {
        if (cameraManager != null && !cameraManager.isReleased()) {
            return cameraManager;
        }

        if (cameraManager != null) {
            AppLog.w(TAG, "CameraManager в Holder уже освобождён — пересоздаём");
            cameraManager = null;
        }

        AppLog.d(TAG, "Фоновая инициализация камеры (без TextureView)...");
        AppConfig appConfig = new AppConfig(context);

        cameraManager = new MultiCameraManager(context.getApplicationContext());

        // ПолучениеКамера数量
        int cameraCount = getCameraCount(appConfig);
        cameraManager.setMaxOpenCameras(cameraCount);

        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) {
                AppLog.e(TAG, "CameraManager service not available");
                return cameraManager;
            }
            String[] cameraIds = cm.getCameraIdList();
            if (cameraIds.length == 0) {
                AppLog.e(TAG, "No cameras available");
                return cameraManager;
            }

            // 根据车型конфигурацияинициализацияКамера（TextureView Все传 null)
            initCamerasByCarModel(appConfig, cameraIds);

            // НастройкиЗаписьрежим
            boolean useCodecRecording = appConfig.shouldUseCodecRecording();
            cameraManager.setCodecRecordingMode(useCodecRecording);

            // 注意：不 Фоновый режим调用 openAllCameras()
            // 部分设备/Система会禁止Фоновый режимПриложениедоступКамера（CAMERA_DISABLED by policy)
            // Камера会 悬浮窗Настройки Surface 并调用 recreateSession 时按需открыть

            AppLog.d(TAG, "Фоновый режимКамера 象инициализациязавершение，Всего  " + cameraCount + " камер(ы)（Не открыть硬件)");
        } catch (CameraAccessException e) {
            AppLog.e(TAG, "Фоновый режиминициализацияКамераОшибка: " + e.getMessage());
        }

        return cameraManager;
    }

    /**
     * Получениеинициализация  MultiCameraManager（不автоматическиинициализация)
     */
    public synchronized MultiCameraManager getCameraManager() {
        return cameraManager;
    }

    /**
     * Настройки有  MultiCameraManager（由 MainActivity инициализация时调用)
     */
    public synchronized void setCameraManager(MultiCameraManager manager) {
        this.cameraManager = manager;
    }

    /**
     * 释放资源
     */
    public synchronized void release() {
        if (cameraManager != null) {
            cameraManager.release();
            cameraManager = null;
        }
    }

    private int getCameraCount(AppConfig appConfig) {
        String carModel = appConfig.getCarModel();
        if (AppConfig.CAR_MODEL_PHONE.equals(carModel)) {
            return 2;
        } else if (appConfig.isCustomCarModel()) {
            return appConfig.getCameraCount();
        }
        return 4; // E5, L7, Xinghan7 等По умолчанию4
    }

    /**
     * 根据车型конфигурацияинициализацияКамера（ и  MainActivity  逻辑一致，但 TextureView Все传 null)
     */
    private void initCamerasByCarModel(AppConfig appConfig, String[] cameraIds) {
        String carModel = appConfig.getCarModel();

        if (AppConfig.CAR_MODEL_L7.equals(carModel) || AppConfig.CAR_MODEL_L7_MULTI.equals(carModel)) {
            initCamerasForL7(cameraIds);
        } else if (AppConfig.CAR_MODEL_PHONE.equals(carModel)) {
            initCamerasForPhone(cameraIds);
        } else if (AppConfig.CAR_MODEL_XINGHAN_7.equals(carModel)) {
            initCamerasForXinghan7(cameraIds);
        } else if (appConfig.isCustomCarModel()) {
            initCamerasForCustomModel(appConfig, cameraIds);
        } else {
            // GalaxyE5（По умолчанию)
            initCamerasForGalaxyE5(cameraIds);
        }
    }

    private void initCamerasForGalaxyE5(String[] cameraIds) {
        if (cameraIds.length >= 4) {
            cameraManager.initCameras(
                    cameraIds[2], null, cameraIds[1], null,
                    cameraIds[3], null, cameraIds[0], null);
        } else if (cameraIds.length >= 2) {
            cameraManager.initCameras(
                    null, null, null, null,
                    cameraIds[0], null, cameraIds[1], null);
        } else if (cameraIds.length == 1) {
            cameraManager.initCameras(
                    cameraIds[0], null, cameraIds[0], null,
                    cameraIds[0], null, cameraIds[0], null);
        }
    }

    private void initCamerasForL7(String[] cameraIds) {
        if (cameraIds.length >= 4) {
            cameraManager.initCameras(
                    cameraIds[2], null, cameraIds[3], null,
                    cameraIds[0], null, cameraIds[1], null);
        } else if (cameraIds.length >= 2) {
            cameraManager.initCameras(
                    cameraIds[0], null, cameraIds[1], null,
                    cameraIds[0], null, cameraIds[1], null);
        }
    }

    private void initCamerasForXinghan7(String[] cameraIds) {
        if (cameraIds.length >= 5) {
            cameraManager.initCameras(
                    cameraIds[3], null, cameraIds[2], null,
                    cameraIds[4], null, cameraIds[1], null);
        } else if (cameraIds.length >= 4) {
            cameraManager.initCameras(
                    cameraIds[3], null, cameraIds[2], null,
                    cameraIds[0], null, cameraIds[1], null);
        }
    }

    private void initCamerasForPhone(String[] cameraIds) {
        if (cameraIds.length >= 2) {
            cameraManager.initCameras(
                    cameraIds[1], null, cameraIds[0], null,
                    null, null, null, null);
        } else if (cameraIds.length == 1) {
            cameraManager.initCameras(
                    cameraIds[0], null, cameraIds[0], null,
                    null, null, null, null);
        }
    }

    private void initCamerasForCustomModel(AppConfig appConfig, String[] cameraIds) {
        String frontId = appConfig.getCameraId("front");
        String backId = appConfig.getCameraId("back");
        String leftId = appConfig.getCameraId("left");
        String rightId = appConfig.getCameraId("right");

        int count = appConfig.getCameraCount();
        switch (count) {
            case 1:
                cameraManager.initCameras(frontId, null, null, null, null, null, null, null);
                break;
            case 2:
                cameraManager.initCameras(frontId, null, backId, null, null, null, null, null);
                break;
            default:
                cameraManager.initCameras(frontId, null, backId, null, leftId, null, rightId, null);
                break;
        }
    }
}
