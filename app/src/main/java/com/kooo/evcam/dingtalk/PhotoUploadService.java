package com.kooo.evcam.dingtalk;


import com.kooo.evcam.AppLog;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Изображение传Сервис
 * 负责将拍 Фото传 до DingTalk
 */
public class PhotoUploadService {
    private static final String TAG = "PhotoUploadService";

    private final Context context;
    private final DingTalkApiClient apiClient;

    public interface UploadCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    public PhotoUploadService(Context context, DingTalkApiClient apiClient) {
        this.context = context;
        this.apiClient = apiClient;
    }

    /**
     * 传ИзображениеФайл до DingTalk
     * @param photoFiles ИзображениеФайл列表
     * @param conversationId DingTalk会话 ID
     * @param conversationType 会话类型（"1"=личный чат，"2"=групповой чат)
     * @param userId DingTalk用户 ID（用于ОтправкаИзображение消息)
     * @param callback 传回调
     */
    public void uploadPhotos(List<File> photoFiles, String conversationId, String conversationType, String userId, UploadCallback callback) {
        new Thread(() -> {
            try {
                if (photoFiles == null || photoFiles.isEmpty()) {
                    callback.onError("Нет фото для отправки");
                    return;
                }

                callback.onProgress("Начало отправки " + photoFiles.size() + " фото...");

                List<String> uploadedFiles = new ArrayList<>();

                for (int i = 0; i < photoFiles.size(); i++) {
                    File photoFile = photoFiles.get(i);

                    if (!photoFile.exists()) {
                        AppLog.w(TAG, "ИзображениеФайлне существует: " + photoFile.getPath());
                        continue;
                    }

                    callback.onProgress("Отправка (" + (i + 1) + "/" + photoFiles.size() + "): " + photoFile.getName());

                    try {
                        // 1. 传Изображение до DingTalk（использование image 类型)
                        callback.onProgress("Отправка фото (" + (i + 1) + "/" + photoFiles.size() + ")...");
                        String mediaId = apiClient.uploadImage(photoFile);
                        AppLog.d(TAG, "Изображение传Успешно，mediaId: " + mediaId);

                        // 2. попыткаиспользование mediaId ОтправкаИзображение消息
                        callback.onProgress("Отправка фото-сообщения (" + (i + 1) + "/" + photoFiles.size() + ")...");
                        try {
                            // попытка直接использование mediaId 作为 photoURL (可能DingTalk会автоматически处理)
                            apiClient.sendImageMessage(conversationId, conversationType, mediaId, userId);
                            AppLog.d(TAG, "Изображение消息ОтправкаУспешно: " + photoFile.getName());
                        } catch (Exception imageError) {
                            // Если Изображениесообщения — ошибка,降级为Файл消息
                            AppLog.w(TAG, "Изображение消息Ошибка отправки,降级为Файл消息: " + imageError.getMessage());
                            apiClient.sendFileMessage(conversationId, conversationType, mediaId, photoFile.getName(), userId);
                            AppLog.d(TAG, "Файл消息ОтправкаУспешно: " + photoFile.getName());
                        }

                        uploadedFiles.add(photoFile.getName());

                        // 3. 延迟2 сек.后再传一 фото，减少Сеть и Система压力
                        if (i < photoFiles.size() - 1) {  // 不 最后一 фото
                            callback.onProgress("Загрузка следующего фото через 2 сек....");
                            Thread.sleep(2000);
                        }

                    } catch (Exception e) {
                        AppLog.e(TAG, "Ошибка загрузки изображения: " + photoFile.getName(), e);
                        callback.onError("Ошибка отправки: " + photoFile.getName() + " - " + e.getMessage());
                    }
                }

                if (uploadedFiles.isEmpty()) {
                    callback.onError("Ошибка загрузки всех фото");
                } else {
                    String successMessage = "Загрузка фото завершена！Всего загружено " + uploadedFiles.size() + " фото";
                    callback.onSuccess(successMessage);

                    // ожидание3 сек.，确保Изображение消息 DingTalkСервис器处理完毕后再Отправказавершение消息
                    // 避免"Отправка завершена"消息比Изображение先 до 达用户端
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {}

                    // Отправказавершение消息，传递 conversationType  и  userId
                    apiClient.sendTextMessage(conversationId, conversationType, successMessage, userId);
                }

            } catch (Exception e) {
                AppLog.e(TAG, "传过程出错", e);
                callback.onError("Ошибка при загрузке: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 传单 шт.Изображение
     */
    public void uploadPhoto(File photoFile, String conversationId, String conversationType, String userId, UploadCallback callback) {
        List<File> files = new ArrayList<>();
        files.add(photoFile);
        uploadPhotos(files, conversationId, conversationType, userId, callback);
    }
}
