package com.kooo.evcam.telegram;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.dingtalk.VideoThumbnailExtractor;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Telegram Видео传Сервис
 * 负责将Запись Видео传 до  Telegram
 */
public class TelegramVideoUploadService {
    private static final String TAG = "TelegramVideoUpload";

    private final Context context;
    private final TelegramApiClient apiClient;

    public interface UploadCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    public TelegramVideoUploadService(Context context, TelegramApiClient apiClient) {
        this.context = context;
        this.apiClient = apiClient;
    }

    /**
     * 传ВидеоФайл до  Telegram
     * @param videoFiles ВидеоФайл列表
     * @param chatId Telegram Chat ID
     * @param callback 传回调
     */
    public void uploadVideos(List<File> videoFiles, long chatId, UploadCallback callback) {
        new Thread(() -> {
            try {
                if (videoFiles == null || videoFiles.isEmpty()) {
                    callback.onError("Нет видео для отправки");
                    return;
                }

                callback.onProgress("Начало отправки " + videoFiles.size() + " видеофайл(ов)...");

                // Отправка "Выполняется 传Видео" Статус
                apiClient.sendChatAction(chatId, "upload_video");

                List<String> uploadedFiles = new ArrayList<>();

                for (int i = 0; i < videoFiles.size(); i++) {
                    File videoFile = videoFiles.get(i);

                    if (!videoFile.exists()) {
                        AppLog.w(TAG, "ВидеоФайлне существует: " + videoFile.getPath());
                        continue;
                    }

                    callback.onProgress("Обработка (" + (i + 1) + "/" + videoFiles.size() + "): " + videoFile.getName());

                    try {
                        // 1. 提取Видео封面
                        File thumbnailFile = new File(videoFile.getParent(),
                                videoFile.getName().replace(".mp4", "_thumb.jpg"));

                        boolean thumbnailExtracted = VideoThumbnailExtractor.extractThumbnail(videoFile, thumbnailFile);
                        if (!thumbnailExtracted) {
                            AppLog.w(TAG, "封面提取Ошибка，将不использование缩略图");
                            thumbnailFile = null;
                        }

                        // 2. ПолучениеВидео时长
                        int duration = VideoThumbnailExtractor.getVideoDuration(videoFile);
                        if (duration == 0) {
                            duration = 60; // По умолчанию 60  сек.
                        }

                        // 3. Отправка "Выполняется 传Видео" Статус
                        apiClient.sendChatAction(chatId, "upload_video");

                        // 4. 直接传并ОтправкаВидео（Telegram API 合并这两步)
                        callback.onProgress("Отправка видео (" + (i + 1) + "/" + videoFiles.size() + ")...");

                        String caption = "Видео " + (i + 1) + "/" + videoFiles.size();
                        apiClient.sendVideo(chatId, videoFile, thumbnailFile, duration, caption);

                        uploadedFiles.add(videoFile.getName());
                        AppLog.d(TAG, "Видео传Успешно: " + videoFile.getName());

                        // 5. Очистка временно封面Файл
                        if (thumbnailFile != null && thumbnailFile.exists()) {
                            thumbnailFile.delete();
                        }

                        // 6. 延迟2 сек.后再传一 шт.Видео
                        if (i < videoFiles.size() - 1) {
                            callback.onProgress("Загрузка следующего видео через 2 сек....");
                            Thread.sleep(2000);
                        }

                    } catch (Exception e) {
                        AppLog.e(TAG, "传ВидеоОшибка: " + videoFile.getName(), e);
                        callback.onError("Ошибка отправки: " + videoFile.getName() + " - " + e.getMessage());
                    }
                }

                if (uploadedFiles.isEmpty()) {
                    callback.onError("Ошибка загрузки всех видео");
                } else {
                    String successMessage = "✅ Загрузка видео завершена！Всего загружено " + uploadedFiles.size() + " файл(ов)";
                    callback.onSuccess(successMessage);

                    // ожидание3 сек.，确保Видео消息投递завершение后再Отправказавершение消息
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {}

                    // Отправказавершение消息
                    apiClient.sendMessage(chatId, successMessage);
                }

            } catch (Exception e) {
                AppLog.e(TAG, "传过程出错", e);
                callback.onError("Ошибка при загрузке: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 传单 шт.ВидеоФайл
     */
    public void uploadVideo(File videoFile, long chatId, UploadCallback callback) {
        List<File> files = new ArrayList<>();
        files.add(videoFile);
        uploadVideos(files, chatId, callback);
    }
}
