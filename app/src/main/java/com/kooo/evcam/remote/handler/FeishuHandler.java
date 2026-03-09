package com.kooo.evcam.remote.handler;

import android.content.Context;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.feishu.FeishuApiClient;
import com.kooo.evcam.feishu.FeishuPhotoUploadService;
import com.kooo.evcam.feishu.FeishuVideoUploadService;
import com.kooo.evcam.remote.core.ChatIdentifier;
import com.kooo.evcam.remote.core.RemotePlatform;
import com.kooo.evcam.remote.core.RemoteUploadCallback;
import com.kooo.evcam.remote.upload.MediaUploadService;

import java.io.File;
import java.util.List;

/**
 * FeishuУдалённыйкоманда处理器
 * 实现Feishu平台特定 функция
 */
public class FeishuHandler extends RemoteCommandHandler {
    private static final String TAG = "FeishuHandler";
    
    // FeishuФайл大小限制
    private static final long MAX_FILE_SIZE_BYTES = 30 * 1024 * 1024; // 30MB
    
    private FeishuApiClient apiClient;
    
    public FeishuHandler(Context context) {
        super(context);
    }
    
    public void setApiClient(FeishuApiClient apiClient) {
        this.apiClient = apiClient;
    }
    
    @Override
    protected String getPlatformName() {
        return "Feishu";
    }
    
    @Override
    protected RemotePlatform getPlatform() {
        return RemotePlatform.FEISHU;
    }
    
    @Override
    protected boolean isApiClientReady() {
        return apiClient != null;
    }
    
    @Override
    public void sendMessage(ChatIdentifier chatId, String message) {
        if (apiClient == null) {
            AppLog.e(TAG, "Feishu API 客户端Не инициализация");
            return;
        }
        
        String feishuChatId = ((ChatIdentifier.FeishuChatId) chatId).getChatId();
        new Thread(() -> {
            try {
                apiClient.sendTextMessage("chat_id", feishuChatId, message);
            } catch (Exception e) {
                AppLog.e(TAG, "ОтправкаFeishuсообщения — ошибка", e);
            }
        }).start();
    }
    
    @Override
    public void sendError(ChatIdentifier chatId, String error) {
        sendMessage(chatId, "❌ " + error);
    }
    
    @Override
    protected MediaUploadService createVideoUploadService() {
        return new FeishuVideoUploadAdapter(context, apiClient);
    }
    
    @Override
    protected MediaUploadService createPhotoUploadService() {
        return new FeishuPhotoUploadAdapter(context, apiClient);
    }
    
    /**
     * 处理传Ошибка - Feishu特有 Файл大小限制Уведомление
     */
    @Override
    protected void handleUploadError(ChatIdentifier chatId, String error) {
        if (error.contains("413") || 
            error.contains("99991663") || 
            error.contains("file size")) {
            sendMessage(chatId, "Feishu ограничивает размер файла до 30 МБ. Файл превышает лимит.");
        }
    }
    
    // ==================== 传Сервис适配器 ====================
    
    /**
     * FeishuВидео传适配器
     */
    private static class FeishuVideoUploadAdapter implements MediaUploadService {
        private final FeishuVideoUploadService uploadService;
        
        FeishuVideoUploadAdapter(Context context, FeishuApiClient apiClient) {
            this.uploadService = new FeishuVideoUploadService(context, apiClient);
        }
        
        @Override
        public void uploadVideos(List<File> videoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            String feishuChatId = ((ChatIdentifier.FeishuChatId) chatId).getChatId();
            uploadService.uploadVideos(videoFiles, feishuChatId,
                    new FeishuVideoUploadService.UploadCallback() {
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
     * FeishuФото传适配器
     */
    private static class FeishuPhotoUploadAdapter implements MediaUploadService {
        private final FeishuPhotoUploadService uploadService;
        
        FeishuPhotoUploadAdapter(Context context, FeishuApiClient apiClient) {
            this.uploadService = new FeishuPhotoUploadService(context, apiClient);
        }
        
        @Override
        public void uploadVideos(List<File> videoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            // Фото传Сервис不处理Видео
        }
        
        @Override
        public void uploadPhotos(List<File> photoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            String feishuChatId = ((ChatIdentifier.FeishuChatId) chatId).getChatId();
            uploadService.uploadPhotos(photoFiles, feishuChatId,
                    new FeishuPhotoUploadService.UploadCallback() {
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
