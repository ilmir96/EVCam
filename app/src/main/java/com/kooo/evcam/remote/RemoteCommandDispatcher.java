package com.kooo.evcam.remote;

import android.content.Context;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.dingtalk.DingTalkApiClient;
import com.kooo.evcam.feishu.FeishuApiClient;
import com.kooo.evcam.remote.core.ChatIdentifier;
import com.kooo.evcam.remote.core.RemotePlatform;
import com.kooo.evcam.remote.handler.DingTalkHandler;
import com.kooo.evcam.remote.handler.FeishuHandler;
import com.kooo.evcam.remote.handler.RemoteCommandHandler;
import com.kooo.evcam.remote.handler.TelegramHandler;
import com.kooo.evcam.telegram.TelegramApiClient;

import java.util.EnumMap;
import java.util.Map;

/**
 * Удалённыйкоманда分发器
 * 作为 MainActivity 调用Удалённыйфункция 统一入口
 * 负责将команда分发 до  应平台 处理器
 */
public class RemoteCommandDispatcher {
    private static final String TAG = "RemoteCommandDispatcher";
    
    private final Context context;
    private final Map<RemotePlatform, RemoteCommandHandler> handlers;
    
    // Камера控制器 и Статус监听器（由 MainActivity 提供)
    private RemoteCommandHandler.CameraController cameraController;
    private RemoteCommandHandler.RecordingStateListener recordingStateListener;
    
    public RemoteCommandDispatcher(Context context) {
        this.context = context.getApplicationContext();
        this.handlers = new EnumMap<>(RemotePlatform.class);
        
        // 预创建各平台处理器（但不Настройки API 客户端)
        handlers.put(RemotePlatform.DINGTALK, new DingTalkHandler(context));
        handlers.put(RemotePlatform.TELEGRAM, new TelegramHandler(context));
        handlers.put(RemotePlatform.FEISHU, new FeishuHandler(context));
        
        AppLog.d(TAG, "RemoteCommandDispatcher инициализациязавершение");
    }
    
    // ==================== 依赖注入 ====================
    
    /**
     * НастройкиКамера控制器
     * 必须 использование前调用
     */
    public void setCameraController(RemoteCommandHandler.CameraController controller) {
        this.cameraController = controller;
        // 传递 所有处理器
        for (RemoteCommandHandler handler : handlers.values()) {
            handler.setCameraController(controller);
        }
        AppLog.d(TAG, "CameraController Настройки");
    }
    
    /**
     * НастройкиЗаписьСтатус监听器
     */
    public void setRecordingStateListener(RemoteCommandHandler.RecordingStateListener listener) {
        this.recordingStateListener = listener;
        // 传递 所有处理器
        for (RemoteCommandHandler handler : handlers.values()) {
            handler.setRecordingStateListener(listener);
        }
        AppLog.d(TAG, "RecordingStateListener Настройки");
    }
    
    /**
     * НастройкиDingTalk API 客户端
     */
    public void setDingTalkApiClient(DingTalkApiClient apiClient) {
        DingTalkHandler handler = (DingTalkHandler) handlers.get(RemotePlatform.DINGTALK);
        if (handler != null) {
            handler.setApiClient(apiClient);
            AppLog.d(TAG, "DingTalk API 客户端Настройки");
        }
    }
    
    /**
     * Настройки Telegram API 客户端
     */
    public void setTelegramApiClient(TelegramApiClient apiClient) {
        TelegramHandler handler = (TelegramHandler) handlers.get(RemotePlatform.TELEGRAM);
        if (handler != null) {
            handler.setApiClient(apiClient);
            AppLog.d(TAG, "Telegram API 客户端Настройки");
        }
    }
    
    /**
     * НастройкиFeishu API 客户端
     */
    public void setFeishuApiClient(FeishuApiClient apiClient) {
        FeishuHandler handler = (FeishuHandler) handlers.get(RemotePlatform.FEISHU);
        if (handler != null) {
            handler.setApiClient(apiClient);
            AppLog.d(TAG, "Feishu API 客户端Настройки");
        }
    }
    
    // ==================== команда分发 ====================
    
    /**
     * ЗапускУдалённая запись
     */
    public void startRemoteRecording(RemotePlatform platform, ChatIdentifier chatId, int durationSeconds) {
        RemoteCommandHandler handler = getHandler(platform);
        if (handler != null) {
            AppLog.d(TAG, "分发Удалённая записькоманда до  " + platform.getDisplayName());
            handler.startRemoteRecording(chatId, durationSeconds);
        } else {
            AppLog.e(TAG, "Не 找 до  " + platform.getDisplayName() + " 处理器");
        }
    }
    
    /**
     * ЗапускУдалённыйФото
     */
    public void startRemotePhoto(RemotePlatform platform, ChatIdentifier chatId) {
        RemoteCommandHandler handler = getHandler(platform);
        if (handler != null) {
            AppLog.d(TAG, "分发УдалённыйФотокоманда до  " + platform.getDisplayName());
            handler.startRemotePhoto(chatId);
        } else {
            AppLog.e(TAG, "Не 找 до  " + platform.getDisplayName() + " 处理器");
        }
    }
    
    /**
     * Отправка消息
     */
    public void sendMessage(RemotePlatform platform, ChatIdentifier chatId, String message) {
        RemoteCommandHandler handler = getHandler(platform);
        if (handler != null) {
            handler.sendMessage(chatId, message);
        }
    }
    
    /**
     * ОтправкаОшибка消息
     */
    public void sendError(RemotePlatform platform, ChatIdentifier chatId, String error) {
        RemoteCommandHandler handler = getHandler(platform);
        if (handler != null) {
            handler.sendError(chatId, error);
        }
    }
    
    // ==================== 便捷方法 - DingTalk ====================
    
    /**
     * DingTalkУдалённая запись（便捷方法)
     */
    public void startDingTalkRecording(String conversationId, String conversationType, 
            String userId, int durationSeconds) {
        ChatIdentifier chatId = ChatIdentifier.dingtalk(conversationId, conversationType, userId);
        startRemoteRecording(RemotePlatform.DINGTALK, chatId, durationSeconds);
    }
    
    /**
     * DingTalkУдалённыйФото（便捷方法)
     */
    public void startDingTalkPhoto(String conversationId, String conversationType, String userId) {
        ChatIdentifier chatId = ChatIdentifier.dingtalk(conversationId, conversationType, userId);
        startRemotePhoto(RemotePlatform.DINGTALK, chatId);
    }
    
    // ==================== 便捷方法 - Telegram ====================
    
    /**
     * Telegram Удалённая запись（便捷方法)
     */
    public void startTelegramRecording(long chatId, int durationSeconds) {
        ChatIdentifier id = ChatIdentifier.telegram(chatId);
        startRemoteRecording(RemotePlatform.TELEGRAM, id, durationSeconds);
    }
    
    /**
     * Telegram УдалённыйФото（便捷方法)
     */
    public void startTelegramPhoto(long chatId) {
        ChatIdentifier id = ChatIdentifier.telegram(chatId);
        startRemotePhoto(RemotePlatform.TELEGRAM, id);
    }
    
    // ==================== 便捷方法 - Feishu ====================
    
    /**
     * FeishuУдалённая запись（便捷方法)
     */
    public void startFeishuRecording(String chatId, int durationSeconds) {
        ChatIdentifier id = ChatIdentifier.feishu(chatId);
        startRemoteRecording(RemotePlatform.FEISHU, id, durationSeconds);
    }
    
    /**
     * FeishuУдалённыйФото（便捷方法)
     */
    public void startFeishuPhoto(String chatId) {
        ChatIdentifier id = ChatIdentifier.feishu(chatId);
        startRemotePhoto(RemotePlatform.FEISHU, id);
    }
    
    // ==================== Статус查询 ====================
    
    /**
     * проверка 否有任何平台Выполняется 进行Удалённая запись
     */
    public boolean isAnyRemoteRecording() {
        for (RemoteCommandHandler handler : handlers.values()) {
            if (handler.isRemoteRecording()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * проверка 否Выполняется 准备Запись
     */
    public boolean isAnyPreparingRecording() {
        for (RemoteCommandHandler handler : handlers.values()) {
            if (handler.isPreparingRecording()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * ПолучениеТекущийВыполняется 进行Удалённая запись 平台
     */
    public RemotePlatform getActiveRecordingPlatform() {
        for (Map.Entry<RemotePlatform, RemoteCommandHandler> entry : handlers.entrySet()) {
            if (entry.getValue().isRemoteRecording()) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Уведомление首 раз数据写入завершение
     * 由 MainActivity  ОбнаруженоЗапись数据写入时调用
     */
    public void onFirstDataWritten() {
        for (RemoteCommandHandler handler : handlers.values()) {
            if (handler.isRemoteRecording()) {
                handler.onFirstDataWritten();
            }
        }
    }
    
    /**
     * Уведомление时间戳обновление（Watchdog 重建Запись后调用)
     * 由 MainActivity  Запись时间戳变化时调用
     */
    public void onTimestampUpdated(String newTimestamp) {
        for (RemoteCommandHandler handler : handlers.values()) {
            if (handler.isRemoteRecording()) {
                handler.onTimestampUpdated(newTimestamp);
            }
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private RemoteCommandHandler getHandler(RemotePlatform platform) {
        return handlers.get(platform);
    }
    
    /**
     * Очистка 资源
     */
    public void cleanup() {
        for (RemoteCommandHandler handler : handlers.values()) {
            handler.cleanup();
        }
        AppLog.d(TAG, "RemoteCommandDispatcher 资源Очистка ");
    }
}
