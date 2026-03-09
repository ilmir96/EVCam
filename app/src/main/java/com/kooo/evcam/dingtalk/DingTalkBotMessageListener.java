package com.kooo.evcam.dingtalk;


import com.kooo.evcam.AppLog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;

/**
 * DingTalk机器人消息回调监听器（использование官方 SDK)
 */
public class DingTalkBotMessageListener implements OpenDingTalkCallbackListener<ChatbotMessage, JSONObject> {
    private static final String TAG = "DingTalkBotListener";

    private final Context context;
    private final DingTalkApiClient apiClient;
    private final CommandCallback callback;
    private final Handler mainHandler;

    public interface CommandCallback {
        void onRecordCommand(String conversationId, String userId, int durationSeconds);
        void onConnectionStatusChanged(boolean connected);
    }

    public DingTalkBotMessageListener(Context context, DingTalkApiClient apiClient, CommandCallback callback) {
        this.context = context;
        this.apiClient = apiClient;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public JSONObject execute(ChatbotMessage message) {
        try {
            MessageContent text = message.getText();
            if (text != null) {
                String msg = text.getContent();
                String senderId = message.getSenderId();
                String conversationId = message.getConversationId();

                AppLog.d(TAG, "Получена команда: 机器人消息 - senderId: " + senderId);
                AppLog.d(TAG, "Получена команда: 机器人消息 - conversationId: " + conversationId);
                AppLog.d(TAG, "Получена команда: 机器人消息 - text: " + msg);

                // 解析команда
                String command = parseCommand(msg);

                // 解析Запись时长（ сек.)
                int durationSeconds = parseRecordDuration(command);

                if (command.startsWith("Запись") || command.toLowerCase().startsWith("record")) {
                    AppLog.d(TAG, "Получена команда: Записькоманда，时长: " + durationSeconds + "  сек.");

                    // ОтправкаПодтвердить消息，传递 senderId
                    String confirmMsg = String.format("Получена команда записи, начинаю запись %d  сек. видео...", durationSeconds);
                    sendResponse(conversationId, senderId, confirmMsg);

                    // Уведомление监听器выполнениеЗапись，传递 senderId  и 时长
                    mainHandler.post(() -> callback.onRecordCommand(conversationId, senderId, durationSeconds));
                } else {
                    AppLog.d(TAG, "Неизвестная команда: " + command);
                    sendResponse(conversationId, senderId, "Неизвестная команда。Отправьте「Запись」или「Запись+数字」Начать записьВидео（если：Запись30 表示Запись30 сек.，По умолчанию60 сек.)。");
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "处理机器人сообщения — ошибка", e);
        }

        return new JSONObject();
    }

    /**
     * 解析команда文本
     * 移除 @机器人  部分，提取实际команда
     */
    private String parseCommand(String text) {
        if (text == null) {
            return "";
        }

        // 移除 @xxx 部分
        String command = text.replaceAll("@\\S+\\s*", "").trim();
        return command;
    }

    /**
     * 解析Запись时长（ сек.)
     * поддержка格式：Запись、Запись30、Запись 30、record、record 30
     * По умолчанию返回 60  сек.（1 мин.)
     */
    private int parseRecordDuration(String command) {
        if (command == null || command.isEmpty()) {
            return 60;
        }

        // 移除"Запись"или"record"Выкл键字，提取数字
        String durationStr = command.replaceAll("(?i)(Запись|record)", "").trim();

        if (durationStr.isEmpty()) {
            return 60; // По умолчанию 1  мин.
        }

        try {
            int duration = Integer.parseInt(durationStr);
            // 限制范围：最少 5  сек.，最多 600  сек.（10 мин.)
            if (duration < 5) {
                return 5;
            } else if (duration > 600) {
                return 600;
            }
            return duration;
        } catch (NumberFormatException e) {
            AppLog.w(TAG, "无法解析Запись时长: " + durationStr + "，использованиеПо умолчанию值 60  сек.");
            return 60;
        }
    }

    /**
     * Отправка响应消息 до DingTalk
     */
    public void sendResponse(String conversationId, String userId, String message) {
        new Thread(() -> {
            try {
                apiClient.sendTextMessage(conversationId, message, userId);
                AppLog.d(TAG, "响应消息Отправка: " + message);
            } catch (Exception e) {
                AppLog.e(TAG, "Отправка响应сообщения — ошибка", e);
            }
        }).start();
    }
}
