package com.kooo.evcam.dingtalk;


import com.kooo.evcam.AppLog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * DingTalkкоманда接收器
 * 负责解析 и 处理 от DingTalk接Получена команда:  команда
 */
public class DingTalkCommandReceiver implements DingTalkStreamClient.MessageCallback {
    private static final String TAG = "DingTalkCommandReceiver";

    private final Context context;
    private final DingTalkApiClient apiClient;
    private final CommandListener listener;
    private final Handler mainHandler;

    public interface CommandListener {
        void onRecordCommand(String conversationId, String conversationType, String userId, int durationSeconds);
        void onConnectionStatusChanged(boolean connected);
    }

    public DingTalkCommandReceiver(Context context, DingTalkApiClient apiClient, CommandListener listener) {
        this.context = context;
        this.apiClient = apiClient;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onConnected() {
        AppLog.d(TAG, "Stream Подключение建立");
        mainHandler.post(() -> listener.onConnectionStatusChanged(true));
    }

    @Override
    public void onDisconnected() {
        AppLog.d(TAG, "Stream Подключениеотключено");
        mainHandler.post(() -> listener.onConnectionStatusChanged(false));
    }

    @Override
    public void onMessageReceived(String conversationId, String conversationType, String senderUserId, String text) {
        AppLog.d(TAG, "Получена команда: 消息: " + text + " from " + senderUserId + " (type: " + conversationType + ")");

        // 解析команда
        String command = parseCommand(text);

        // 解析Запись时长（ сек.)
        int durationSeconds = parseRecordDuration(command);

        if (command.startsWith("Запись") || command.toLowerCase().startsWith("record")) {
            AppLog.d(TAG, "Получена команда: Записькоманда，时长: " + durationSeconds + "  сек.");

            // ОтправкаПодтвердить消息，传递 conversationType  и  senderUserId
            String confirmMsg = String.format("Получена команда записи, начинаю запись %d  сек. видео...", durationSeconds);
            sendResponse(conversationId, conversationType, senderUserId, confirmMsg);

            // Уведомление监听器выполнениеЗапись，传递 conversationType、senderUserId  и 时长
            mainHandler.post(() -> listener.onRecordCommand(conversationId, conversationType, senderUserId, durationSeconds));
        } else {
            AppLog.d(TAG, "Неизвестная команда: " + command);
            sendResponse(conversationId, conversationType, senderUserId, "Неизвестная команда。Отправьте「Запись」или「Запись+数字」Начать записьВидео（если：Запись30 表示Запись30 сек.，По умолчанию60 сек.)。");
        }
    }

    @Override
    public void onError(String error) {
        AppLog.e(TAG, "Ошибка: " + error);
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
    public void sendResponse(String conversationId, String conversationType, String userId, String message) {
        new Thread(() -> {
            try {
                apiClient.sendTextMessage(conversationId, conversationType, message, userId);
                AppLog.d(TAG, "响应消息Отправка: " + message);
            } catch (Exception e) {
                AppLog.e(TAG, "Отправка响应сообщения — ошибка", e);
            }
        }).start();
    }
}
