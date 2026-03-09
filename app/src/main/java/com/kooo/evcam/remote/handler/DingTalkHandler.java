package com.kooo.evcam.remote.handler;

import android.content.Context;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.dingtalk.DingTalkApiClient;
import com.kooo.evcam.dingtalk.PhotoUploadService;
import com.kooo.evcam.dingtalk.VideoUploadService;
import com.kooo.evcam.remote.core.ChatIdentifier;
import com.kooo.evcam.remote.core.RemotePlatform;
import com.kooo.evcam.remote.core.RemoteUploadCallback;
import com.kooo.evcam.remote.upload.MediaUploadService;

import java.io.File;
import java.util.List;

/**
 * DingTalkУдалённыйкоманда处理器
 * 实现DingTalk平台特定 функция
 */
public class DingTalkHandler extends RemoteCommandHandler {
    private static final String TAG = "DingTalkHandler";
    
    private DingTalkApiClient apiClient;
    
    public DingTalkHandler(Context context) {
        super(context);
    }
    
    public void setApiClient(DingTalkApiClient apiClient) {
        this.apiClient = apiClient;
    }
    
    @Override
    protected String getPlatformName() {
        return "DingTalk";
    }
    
    @Override
    protected RemotePlatform getPlatform() {
        return RemotePlatform.DINGTALK;
    }
    
    @Override
    protected boolean isApiClientReady() {
        return apiClient != null;
    }
    
    @Override
    public void sendMessage(ChatIdentifier chatId, String message) {
        if (apiClient == null) {
            AppLog.e(TAG, "DingTalk API 客户端Не инициализация");
            return;
        }
        
        ChatIdentifier.DingTalkChatId dingTalkId = (ChatIdentifier.DingTalkChatId) chatId;
        new Thread(() -> {
            try {
                apiClient.sendTextMessage(
                    dingTalkId.getConversationId(),
                    dingTalkId.getConversationType(),
                    message
                );
            } catch (Exception e) {
                AppLog.e(TAG, "ОтправкаDingTalkсообщения — ошибка", e);
            }
        }).start();
    }
    
    @Override
    public void sendError(ChatIdentifier chatId, String error) {
        sendMessage(chatId, "❌ " + error);
    }
    
    @Override
    protected MediaUploadService createVideoUploadService() {
        return new DingTalkVideoUploadAdapter(context, apiClient);
    }
    
    @Override
    protected MediaUploadService createPhotoUploadService() {
        return new DingTalkPhotoUploadAdapter(context, apiClient);
    }
    
    // ==================== 传Сервис适配器 ====================
    
    /**
     * DingTalkВидео传适配器
     */
    private static class DingTalkVideoUploadAdapter implements MediaUploadService {
        private final VideoUploadService uploadService;
        
        DingTalkVideoUploadAdapter(Context context, DingTalkApiClient apiClient) {
            this.uploadService = new VideoUploadService(context, apiClient);
        }
        
        @Override
        public void uploadVideos(List<File> videoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            ChatIdentifier.DingTalkChatId dingTalkId = (ChatIdentifier.DingTalkChatId) chatId;
            uploadService.uploadVideos(videoFiles, 
                    dingTalkId.getConversationId(), 
                    dingTalkId.getConversationType(),
                    dingTalkId.getUserId(),
                    new VideoUploadService.UploadCallback() {
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
     * DingTalkФото传适配器
     */
    private static class DingTalkPhotoUploadAdapter implements MediaUploadService {
        private final PhotoUploadService uploadService;
        
        DingTalkPhotoUploadAdapter(Context context, DingTalkApiClient apiClient) {
            this.uploadService = new PhotoUploadService(context, apiClient);
        }
        
        @Override
        public void uploadVideos(List<File> videoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            // Фото传Сервис不处理Видео
        }
        
        @Override
        public void uploadPhotos(List<File> photoFiles, ChatIdentifier chatId, RemoteUploadCallback callback) {
            ChatIdentifier.DingTalkChatId dingTalkId = (ChatIdentifier.DingTalkChatId) chatId;
            uploadService.uploadPhotos(photoFiles,
                    dingTalkId.getConversationId(),
                    dingTalkId.getConversationType(),
                    dingTalkId.getUserId(),
                    new PhotoUploadService.UploadCallback() {
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
