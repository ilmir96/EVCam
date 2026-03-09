package com.kooo.evcam.telegram;

import com.kooo.evcam.AppLog;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Telegram Изображение传Сервис
 * 负责将拍 Фото传 до  Telegram
 */
public class TelegramPhotoUploadService {
    private static final String TAG = "TelegramPhotoUpload";

    private final Context context;
    private final TelegramApiClient apiClient;

    public interface UploadCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    public TelegramPhotoUploadService(Context context, TelegramApiClient apiClient) {
        this.context = context;
        this.apiClient = apiClient;
    }

    /**
     * 传ИзображениеФайл до  Telegram
     * @param photoFiles ИзображениеФайл列表
     * @param chatId Telegram Chat ID
     * @param callback 传回调
     */
    public void uploadPhotos(List<File> photoFiles, long chatId, UploadCallback callback) {
        new Thread(() -> {
            try {
                if (photoFiles == null || photoFiles.isEmpty()) {
                    callback.onError("Нет фото для отправки");
                    return;
                }

                callback.onProgress("Начало отправки " + photoFiles.size() + " фото...");

                // Отправка "Выполняется 传Фото" Статус
                apiClient.sendChatAction(chatId, "upload_photo");

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

                    // 重试传（最多2 раз，减少ожидание时间)
                    boolean uploadSuccess = false;
                    int retryCount = 0;
                    int maxRetries = 2;
                    String lastError = "";

                    while (!uploadSuccess && retryCount < maxRetries) {
                        try {
                            if (retryCount > 0) {
                                callback.onProgress("Повтор #" + retryCount + "  раз: " + photoFile.getName());
                                Thread.sleep(1500); // 重试前ожидание1.5 сек.
                            }

                            // Отправка "Выполняется 传Фото" Статус
                            apiClient.sendChatAction(chatId, "upload_photo");

                            // 直接传并ОтправкаИзображение
                            String caption = "Фото " + (i + 1) + "/" + photoFiles.size();
                            apiClient.sendPhoto(chatId, photoFile, caption);

                            uploadedFiles.add(photoFile.getName());
                            AppLog.d(TAG, "Изображение传Успешно: " + photoFile.getName());
                            uploadSuccess = true;

                        } catch (Exception e) {
                            retryCount++;
                            lastError = e.getMessage();
                            AppLog.e(TAG, "传ИзображениеОшибка (попытка " + retryCount + "/" + maxRetries + "): " + photoFile.getName(), e);

                            if (retryCount >= maxRetries) {
                                // 达 до максимум重试 раз数，记录 до Список ошибок
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
                    // 所有ФайлвсеОшибка
                    String errorMsg = "❌ Ошибка загрузки всех фото\nСписок ошибок:\n" + String.join("\n", failedFiles);
                    callback.onError(errorMsg);
                    apiClient.sendMessage(chatId, errorMsg);
                } else if (failedFiles.isEmpty()) {
                    // ВсеУспешно
                    String successMessage = "✅ Загрузка фото завершена！Всего загружено " + uploadedFiles.size() + " фото";
                    callback.onSuccess(successMessage);
                    // ожидание2 сек.，确保Изображение消息投递завершение后再Отправказавершение消息
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                    apiClient.sendMessage(chatId, successMessage);
                } else {
                    // 部分Успешно，Частичная ошибка
                    String mixedMessage = "⚠️ Загрузка завершена（Частичная ошибка)\n" +
                            "Успешно: " + uploadedFiles.size() + "  шт.\n" +
                            "Ошибка: " + failedFiles.size() + "  шт.\n\n" +
                            "Список ошибок:\n" + String.join("\n", failedFiles);
                    callback.onSuccess(mixedMessage); // 仍然视为Успешно（至少有部分传)
                    // ожидание2 сек.，确保Изображение消息投递завершение后再Отправказавершение消息
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                    apiClient.sendMessage(chatId, mixedMessage);
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
    public void uploadPhoto(File photoFile, long chatId, UploadCallback callback) {
        List<File> files = new ArrayList<>();
        files.add(photoFile);
        uploadPhotos(files, chatId, callback);
    }
}
