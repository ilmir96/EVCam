package com.kooo.evcam.remote.core;

/**
 * Удалённый传回调接口
 * 统一所有平台 传回调
 */
public interface RemoteUploadCallback {
    
    /**
     * 传进度обновление
     * @param message 进度消息
     */
    void onProgress(String message);
    
    /**
     * 传Успешно
     * @param message Успешно消息
     */
    void onSuccess(String message);
    
    /**
     * Ошибка загрузки
     * @param error ОшибкаИнформация
     */
    void onError(String error);
}
