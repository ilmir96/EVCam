package com.kooo.evcam.dingtalk;


import com.kooo.evcam.AppLog;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Видео传Сервис
 * 负责将Запись Видео传 до DingTalk
 */
public class VideoUploadService {
    private static final String TAG = "VideoUploadService";

    private final Context context;
    private final DingTalkApiClient apiClient;

    public interface UploadCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    public VideoUploadService(Context context, DingTalkApiClient apiClient) {
        this.context = context;
        this.apiClient = apiClient;
    }

    /**
     * 传ВидеоФайл до DingTalk
     * @param videoFiles ВидеоФайл列表
     * @param conversationId DingTalk会话 ID
     * @param conversationType 会话类型（"1"=личный чат，"2"=групповой чат)
     * @param userId DingTalk用户 ID（用于ОтправкаВидео消息)
     * @param callback 传回调
     */
    public void uploadVideos(List<File> videoFiles, String conversationId, String conversationType, String userId, UploadCallback callback) {
        new Thread(() -> {
            try {
                if (videoFiles == null || videoFiles.isEmpty()) {
                    callback.onError("Нет видео для отправки");
                    return;
                }

                callback.onProgress("Начало отправки " + videoFiles.size() + " видеофайл(ов)...");

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
                            AppLog.w(TAG, "封面提取Ошибка，跳过Видео: " + videoFile.getName());
                            callback.onError("Ошибка извлечения обложки: " + videoFile.getName());
                            continue;
                        }

                        // 2. ПолучениеВидео时长
                        int duration = VideoThumbnailExtractor.getVideoDuration(videoFile);
                        if (duration == 0) {
                            duration = 60; // По умолчанию 60  сек.
                        }

                        // 3. 传ВидеоФайл до DingTalk
                        callback.onProgress("Отправка видео (" + (i + 1) + "/" + videoFiles.size() + ")...");
                        String videoMediaId = apiClient.uploadFile(videoFile);

                        // 4. 传封面图 до DingTalk
                        callback.onProgress("Загрузка обложки (" + (i + 1) + "/" + videoFiles.size() + ")...");
                        String picMediaId = apiClient.uploadImage(thumbnailFile);

                        // 5. ОтправкаВидео消息
                        callback.onProgress("Отправка видео-сообщения (" + (i + 1) + "/" + videoFiles.size() + ")...");
                        apiClient.sendVideoMessage(conversationId, conversationType, videoMediaId, picMediaId, duration, userId);

                        uploadedFiles.add(videoFile.getName());
                        AppLog.d(TAG, "Видео传Успешно: " + videoFile.getName());

                        // 6. Очистка временно封面Файл
                        if (thumbnailFile.exists()) {
                            thumbnailFile.delete();
                        }

                        // 7. 延迟2 сек.后再传一 шт.Видео，减少Сеть и Система压力
                        if (i < videoFiles.size() - 1) {  // 不 最后一 шт.Видео
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
                    String successMessage = "Загрузка видео завершена！Всего загружено " + uploadedFiles.size() + " файл(ов)";
                    callback.onSuccess(successMessage);

                    // ожидание5 сек.，确保Видео消息 DingTalkСервис器处理完毕后再Отправказавершение消息
                    // Видео处理比Изображение更慢，необходимо更长 ожидание时间
                    try {
                        Thread.sleep(5000);
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
     * 传单 шт.ВидеоФайл
     */
    public void uploadVideo(File videoFile, String conversationId, String conversationType, String userId, UploadCallback callback) {
        List<File> files = new ArrayList<>();
        files.add(videoFile);
        uploadVideos(files, conversationId, conversationType, userId, callback);
    }
}
