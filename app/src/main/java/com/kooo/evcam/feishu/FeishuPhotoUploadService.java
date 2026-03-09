package com.kooo.evcam.feishu;

import com.kooo.evcam.AppLog;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * FeishuИзображение传Сервис
 * 负责将拍 Фото传 до Feishu
 */
public class FeishuPhotoUploadService {
    private static final String TAG = "FeishuPhotoUpload";

    private final Context context;
    private final FeishuApiClient apiClient;

    public interface UploadCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    public FeishuPhotoUploadService(Context context, FeishuApiClient apiClient) {
        this.context = context;
        this.apiClient = apiClient;
    }

    /**
     * 传ИзображениеФайл до Feishu
     * @param photoFiles ИзображениеФайл列表
     * @param chatId Feishu会话 ID
     * @param callback 传回调
     */
    public void uploadPhotos(List<File> photoFiles, String chatId, UploadCallback callback) {
        new Thread(() -> {
            try {
                if (photoFiles == null || photoFiles.isEmpty()) {
                    callback.onError("Нет фото для отправки");
                    return;
                }

                callback.onProgress("Начало отправки " + photoFiles.size() + " фото...");

                List<String> uploadedFiles = new ArrayList<>();
                List<String> failedFiles = new ArrayList<>();

                for (int i = 0; i < photoFiles.size(); i++) {
                    File photoFile = photoFiles.get(i);

                    if (!photoFile.exists()) {
                        AppLog.w(TAG, "ИзображениеФайлне существует: " + photoFile.getPath());
                        failedFiles.add(photoFile.getName() + " (файл не найден)");
                        continue;
                    }

                    callback.onProgress("Отправка (" + (i + 1) + "/" + photoFiles.size() + "): " + photoFile.getName());

                    // 重试传（最多2 раз)
                    boolean uploadSuccess = false;
                    int retryCount = 0;
                    int maxRetries = 2;
                    String lastError = "";

                    while (!uploadSuccess && retryCount < maxRetries) {
                        try {
                            if (retryCount > 0) {
                                callback.onProgress("Повтор #" + retryCount + "  раз: " + photoFile.getName());
                                Thread.sleep(1500);
                            }

                            // 1. 传ИзображениеПолучение image_key
                            String imageKey = apiClient.uploadImage(photoFile);

                            // 2. ОтправкаИзображение消息
                            apiClient.sendImageMessage("chat_id", chatId, imageKey);

                            uploadedFiles.add(photoFile.getName());
                            AppLog.d(TAG, "Изображение传Успешно: " + photoFile.getName());
                            uploadSuccess = true;

                        } catch (Exception e) {
                            retryCount++;
                            lastError = e.getMessage();
                            AppLog.e(TAG, "传ИзображениеОшибка (попытка " + retryCount + "/" + maxRetries + "): " + photoFile.getName(), e);

                            if (retryCount >= maxRetries) {
                                failedFiles.add(photoFile.getName() + " (" + (lastError != null ? lastError : "НеизвестноОшибка") + ")");
                                break;
                            }
                        }
                    }

                    // 延迟500ms后再传一 фото
                    if (i < photoFiles.size() - 1) {
                        Thread.sleep(500);
                    }
                }

                // 统一处理传结果
                if (uploadedFiles.isEmpty()) {
                    String errorMsg = "❌ Ошибка загрузки всех фото\nСписок ошибок:\n" + String.join("\n", failedFiles);
                    callback.onError(errorMsg);
                    apiClient.sendTextMessage("chat_id", chatId, errorMsg);
                } else if (failedFiles.isEmpty()) {
                    String successMessage = "✅ Загрузка фото завершена！Всего загружено " + uploadedFiles.size() + " фото";
                    callback.onSuccess(successMessage);
                    Thread.sleep(2000);
                    apiClient.sendTextMessage("chat_id", chatId, successMessage);
                } else {
                    String mixedMessage = "⚠️ Загрузка завершена（Частичная ошибка)\n" +
                            "Успешно: " + uploadedFiles.size() + "  шт.\n" +
                            "Ошибка: " + failedFiles.size() + "  шт.\n\n" +
                            "Список ошибок:\n" + String.join("\n", failedFiles);
                    callback.onSuccess(mixedMessage);
                    Thread.sleep(2000);
                    apiClient.sendTextMessage("chat_id", chatId, mixedMessage);
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
    public void uploadPhoto(File photoFile, String chatId, UploadCallback callback) {
        List<File> files = new ArrayList<>();
        files.add(photoFile);
        uploadPhotos(files, chatId, callback);
    }
}
