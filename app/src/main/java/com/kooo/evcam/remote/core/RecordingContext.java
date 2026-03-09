package com.kooo.evcam.remote.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Запись文
 * 封装一 разУдалённая записьзадача 所有СтатусИнформация
 */
public class RecordingContext {
    
    private final ChatIdentifier chatId;
    private final int durationSeconds;
    private String timestamp;  // Текущий时间戳（可能因 Watchdog 重建而обновление)
    private final List<String> allTimestamps = new ArrayList<>();  // 所有использование过 时间戳（用于传时查找所有Файл)
    
    // Статус标志
    private boolean wasManualRecordingBefore = false;
    private boolean isCompleted = false;
    private boolean isCancelled = false;
    
    // ОшибкаИнформация
    private String errorMessage = null;
    
    public RecordingContext(ChatIdentifier chatId, int durationSeconds, String timestamp) {
        this.chatId = chatId;
        this.durationSeconds = durationSeconds;
        this.timestamp = timestamp;
        this.allTimestamps.add(timestamp);  // 初始时间戳также加入列表
    }
    
    // ==================== Getters ====================
    
    public ChatIdentifier getChatId() {
        return chatId;
    }
    
    public int getDurationSeconds() {
        return durationSeconds;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public boolean wasManualRecordingBefore() {
        return wasManualRecordingBefore;
    }
    
    public boolean isCompleted() {
        return isCompleted;
    }
    
    public boolean isCancelled() {
        return isCancelled;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public RemotePlatform getPlatform() {
        return chatId.getPlatform();
    }
    
    // ==================== Setters ====================
    
    public void setWasManualRecordingBefore(boolean wasManualRecordingBefore) {
        this.wasManualRecordingBefore = wasManualRecordingBefore;
    }
    
    public void setCompleted(boolean completed) {
        this.isCompleted = completed;
    }
    
    public void setCancelled(boolean cancelled) {
        this.isCancelled = cancelled;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * обновление时间戳（Watchdog 重建Запись后调用)
     * 同时将新时间戳加入历史列表，以便传时能找 до 所有ЗаписьФайл
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
        if (!allTimestamps.contains(timestamp)) {
            allTimestamps.add(timestamp);
        }
    }
    
    /**
     * Получение所有использование过 时间戳（包括 Watchdog 重建后 新时间戳)
     * 用于传时查找所有Запись Файл
     */
    public List<String> getAllTimestamps() {
        return new ArrayList<>(allTimestamps);
    }
    
    @Override
    public String toString() {
        return "RecordingContext{" +
                "platform=" + getPlatform().getDisplayName() +
                ", chatId=" + chatId.getId() +
                ", duration=" + durationSeconds + "s" +
                ", timestamp=" + timestamp +
                ", wasManualBefore=" + wasManualRecordingBefore +
                '}';
    }
}
