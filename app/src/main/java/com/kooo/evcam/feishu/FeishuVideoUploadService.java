package com.kooo.evcam.feishu;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.dingtalk.VideoThumbnailExtractor;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * FeishuВидео传Сервис
 * 负责将Запись Видео传 до Feishu
 */
public class FeishuVideoUploadService {
    private static final String TAG = "FeishuVideoUpload";

    private final Context context;
    private final FeishuApiClient apiClient;

    public interface UploadCallback {
        void onProgress(String message);
        void onSuccess(String message);
        void onError(String error);
    }

    public FeishuVideoUploadService(Context context, FeishuApiClient apiClient) {
        this.context = context;
        this.apiClient = apiClient;
    }

    /**
     * 传ВидеоФайл до Feishu
     * @param videoFiles ВидеоФайл列表
     * @param chatId Feishu会话 ID
     * @param callback 传回调
     */
    public void uploadVideos(List<File> videoFiles, String chatId, UploadCallback callback) {
        new Thread(() -> {
            try {
                if (videoFiles == null || videoFiles.isEmpty()) {
                    callback.onError("Нет видео для отправки");
                    return;
                }

                callback.onProgress("Начало отправки " + videoFiles.size() + " видеофайл(ов)...");

                List<String> uploadedFiles = new ArrayList<>();
                List<String> failedFiles = new ArrayList<>();

                for (int i = 0; i < videoFiles.size(); i++) {
                    File videoFile = videoFiles.get(i);

                    if (!videoFile.exists()) {
                        AppLog.w(TAG, "ВидеоФайлне существует: " + videoFile.getPath());
                        failedFiles.add(videoFile.getName() + " (файл не найден)");
                        continue;
                    }

                    callback.onProgress("Обработка (" + (i + 1) + "/" + videoFiles.size() + "): " + videoFile.getName());

                    File thumbnailFile = null;
                    try {
                        // 1. 提取Видео封面缩略图 и Получение时长
                        callback.onProgress("Извлечение информации о видео (" + (i + 1) + "/" + videoFiles.size() + ")...");
                        thumbnailFile = new File(videoFile.getParent(),
                                videoFile.getName().replace(".mp4", "_thumb.jpg"));
                        boolean thumbnailExtracted = VideoThumbnailExtractor.extractThumbnail(videoFile, thumbnailFile);
                        if (!thumbnailExtracted) {
                            AppLog.w(TAG, "无法提取Видео缩略图，将不显示封面");
                            thumbnailFile = null;
                        }

                        // ПолучениеВидео时长（ сек.)，转换为毫 сек.
                        int durationSec = VideoThumbnailExtractor.getVideoDuration(videoFile);
                        int durationMs = durationSec * 1000;
                        AppLog.d(TAG, "Видео时长: " + durationSec + "  сек. (" + durationMs + " 毫 сек.)");

                        // 2. 传ВидеоФайлПолучение file_key（带时长参数)
                        callback.onProgress("Отправка видео (" + (i + 1) + "/" + videoFiles.size() + ")...");
                        String fileKey = apiClient.uploadFile(videoFile, "mp4", durationMs);

                        // 3. 传封面ИзображениеПолучение image_key（Если 有)
                        String imageKey = null;
                        if (thumbnailFile != null && thumbnailFile.exists()) {
                            callback.onProgress("Загрузка обложки видео...");
                            try {
                                imageKey = apiClient.uploadImage(thumbnailFile);
                                AppLog.d(TAG, "封面传Успешно: " + imageKey);
                            } catch (Exception e) {
                                AppLog.w(TAG, "封面Ошибка загрузки，Видео将没有封面", e);
                            }
                        }

                        // 4. ОтправкаВидео消息（带封面)
                        apiClient.sendVideoMessage("chat_id", chatId, fileKey, imageKey);

                        uploadedFiles.add(videoFile.getName());
                        AppLog.d(TAG, "Видео传Успешно: " + videoFile.getName());

                        // 5. 延迟2 сек.后再传一 шт.Видео
                        if (i < videoFiles.size() - 1) {
                            callback.onProgress("Загрузка следующего видео через 2 сек....");
                            Thread.sleep(2000);
                        }

                    } catch (Exception e) {
                        AppLog.e(TAG, "传ВидеоОшибка: " + videoFile.getName(), e);
                        failedFiles.add(videoFile.getName() + " (" + e.getMessage() + ")");
                    } finally {
                        // Очистка временно缩略图Файл
                        if (thumbnailFile != null && thumbnailFile.exists()) {
                            thumbnailFile.delete();
                        }
                    }
                }

                // 统一处理传结果
                if (uploadedFiles.isEmpty()) {
                    String errorMsg = "❌ Ошибка загрузки всех видео\nСписок ошибок:\n" + String.join("\n", failedFiles);
                    callback.onError(errorMsg);
                    apiClient.sendTextMessage("chat_id", chatId, errorMsg);
                } else if (failedFiles.isEmpty()) {
                    String successMessage = "✅ Загрузка видео завершена！Всего загружено " + uploadedFiles.size() + " файл(ов)";
                    callback.onSuccess(successMessage);
                    Thread.sleep(3000);
                    apiClient.sendTextMessage("chat_id", chatId, successMessage);
                } else {
                    String mixedMessage = "⚠️ Загрузка завершена（Частичная ошибка)\n" +
                            "Успешно: " + uploadedFiles.size() + "  шт.\n" +
                            "Ошибка: " + failedFiles.size() + "  шт.\n\n" +
                            "Список ошибок:\n" + String.join("\n", failedFiles);
                    callback.onSuccess(mixedMessage);
                    Thread.sleep(3000);
                    apiClient.sendTextMessage("chat_id", chatId, mixedMessage);
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
    public void uploadVideo(File videoFile, String chatId, UploadCallback callback) {
        List<File> files = new ArrayList<>();
        files.add(videoFile);
        uploadVideos(files, chatId, callback);
    }
}
