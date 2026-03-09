package com.kooo.evcam.remote.handler;

import android.content.Context;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.remote.core.ChatIdentifier;
import com.kooo.evcam.remote.core.RemotePlatform;
import com.kooo.evcam.remote.core.RemoteUploadCallback;
import com.kooo.evcam.remote.upload.MediaUploadService;
import com.kooo.evcam.telegram.TelegramApiClient;
import com.kooo.evcam.telegram.TelegramPhotoUploadService;
import com.kooo.evcam.telegram.TelegramVideoUploadService;

import java.io.File;
import java.util.List;

/**
 * Telegram Удалённыйкоманда处理器
 * 实现 Telegram 平台特定 функция
 */
public class TelegramHandler extends RemoteCommandHandler {
    private static final String TAG = "TelegramHandler";
    
    // Telegram Файл大小限制
    private static final long MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50MB
    
    private TelegramApiClient apiClient;
    
    public TelegramHandler(Context context) {
        super(context);
    }
    
    public void setApiClient(TelegramApiClient apiClient) {
        this.apiClient = apiClient;
    }
    
    @Override
    protected String getPlatformName() {
        return "Telegram";
    }
    
    @Override
    protected RemotePlatform getPlatform() {
        return RemotePlatform.TELEGRAM;
    }
    
    @Override
    protected boolean isApiClientReady() {
        return apiClient != null;
    }
    
    @Override
    public void sendMessage(ChatIdentifier chatId, String message) {
        if (apiClient == null) {
            AppLog.e(TAG, "Telegram API 客户端Не инициализация");
            return;
        }
        
        long telegramChatId = ((ChatIdentifier.TelegramChatId) chatId).getChatId();
        new Thread(() -> {
            try {
                apiClient.sendMessage(telegramChatId, message);
            } catch (Exception e) {
                AppLog.e(TAG, "Отправка Telegram сообщения — ошибка", e);
            }
        }).start();
    }
    
    @Override
    public void sendError(ChatIdentifier chatId, String error) {
        sendMessage(chatId, "❌ " + error);
    }
    
    @Override
    protected MediaUploadService createVideoUploadService() {
        return new TelegramVideoUploadAdapter(context, apiClient);
    }
    
    @Override
    protected MediaUploadService createPhotoUploadService() {
        return new TelegramPhotoUploadAdapter(context, apiClient);
    }
    
    /**
     * 处理传Ошибка - Telegram 特有 Файл大小限制Уведомление
     */
    @Override
    protected void handleUploadError(ChatIdentifier chatId, String error) {
        if (error.contains("413") || 
            error.toLowerCase().contains("too large") || 
            error.toLowerCase().contains("file is too big")) {
            sendMessage(chatId, "Telegram Bot API ограничивает размер файла до 50 МБ, этот файл превышает лимит.");
        }
    }
    
    // ==================== 传Сервис适配器 ====================
    
    /**
     * Telegram Видео传适配器
     */
    private static class TelegramVideoUploadAdapter implements MediaUploadService {
        private final TelegramVideoUploadService uploadService;
        
        TelegramVideoUploadAdapter(Context context, TelegramApiClient apiClient) {
            this.uploadService = new TelegramVideoUploadService(context, apiClient);
        }
        
        @Override
        public void uploadVideos(List<File> videoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            long telegramChatId = ((ChatIdentifier.TelegramChatId) chatId).getChatId();
            uploadService.uploadVideos(videoFiles, telegramChatId,
                    new TelegramVideoUploadService.UploadCallback() {
                        @Override
                        public void onProgress(String message) {
                            callback.onProgress(message);
                        }
                        
                        @Override
                        public void onSuccess(String message) {
                            callback.onSuccess(message);
                        }
                        
                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    });
        }
        
        @Override
        public void uploadPhotos(List<File> photoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            // Видео传Сервис不处理Фото
        }
    }
    
    /**
     * Telegram Фото传适配器
     */
    private static class TelegramPhotoUploadAdapter implements MediaUploadService {
        private final TelegramPhotoUploadService uploadService;
        
        TelegramPhotoUploadAdapter(Context context, TelegramApiClient apiClient) {
            this.uploadService = new TelegramPhotoUploadService(context, apiClient);
        }
        
        @Override
        public void uploadVideos(List<File> videoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            // Фото传Сервис不处理Видео
        }
        
        @Override
        public void uploadPhotos(List<File> photoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            long telegramChatId = ((ChatIdentifier.TelegramChatId) chatId).getChatId();
            uploadService.uploadPhotos(photoFiles, telegramChatId,
                    new TelegramPhotoUploadService.UploadCallback() {
                        @Override
                        public void onProgress(String message) {
                            callback.onProgress(message);
                        }
                        
                        @Override
                        public void onSuccess(String message) {
                            callback.onSuccess(message);
                        }
                        
                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    });
        }
    }
}
