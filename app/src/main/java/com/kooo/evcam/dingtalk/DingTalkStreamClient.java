package com.kooo.evcam.dingtalk;


import com.kooo.evcam.AppLog;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * DingTalk Stream 客户端
 * 通过 WebSocket 长Подключение接收DingTalk推送 消息
 */
public class DingTalkStreamClient extends WebSocketListener {
    private static final String TAG = "DingTalkStreamClient";
    private static final int RECONNECT_DELAY_MS = 5000;

    private final DingTalkApiClient apiClient;
    private final Gson gson;
    private final OkHttpClient httpClient;
    private final MessageCallback callback;

    private WebSocket webSocket;
    private boolean isRunning = false;

    public interface MessageCallback {
        void onConnected();
        void onDisconnected();
        void onMessageReceived(String conversationId, String conversationType, String senderUserId, String text);
        void onError(String error);
    }

    public DingTalkStreamClient(DingTalkApiClient apiClient, MessageCallback callback) {
        this.apiClient = apiClient;
        this.callback = callback;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // 长Подключение不Настройки读таймаут
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // 心跳
                .build();
    }

    /**
     * Запуск Stream Подключение
     */
    public void start() {
        if (isRunning) {
            AppLog.w(TAG, "Stream 客户端 Работа");
            return;
        }

        isRunning = true;
        new Thread(this::connect).start();
    }

    /**
     * Остановка Stream Подключение
     */
    public void stop() {
        isRunning = false;
        if (webSocket != null) {
            webSocket.close(1000, "Клиент закрыл соединение");
            webSocket = null;
        }
    }

    /**
     * 建立 WebSocket Подключение
     */
    private void connect() {
        try {
            // Получение Stream ПодключениеИнформация
            DingTalkApiClient.StreamConnection connection = apiClient.getStreamConnection();
            AppLog.d(TAG, "Выполняется Подключение до : " + connection.endpoint);

            // 构建 WebSocket URL，添加 ticket 参数
            String wsUrl = connection.endpoint + "?ticket=" + connection.ticket;
            AppLog.d(TAG, "WebSocket URL: " + wsUrl);

            Request request = new Request.Builder()
                    .url(wsUrl)
                    .build();

            webSocket = httpClient.newWebSocket(request, this);

        } catch (Exception e) {
            AppLog.e(TAG, "Ошибка подключения", e);
            callback.onError("Ошибка подключения: " + e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * 定时重连
     */
    private void scheduleReconnect() {
        if (!isRunning) {
            return;
        }

        AppLog.d(TAG, "将  " + RECONNECT_DELAY_MS + "ms 后重连");
        new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
                if (isRunning) {
                    connect();
                }
            } catch (InterruptedException e) {
                AppLog.e(TAG, "重连 断", e);
            }
        }).start();
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        AppLog.d(TAG, "WebSocket Подключение建立");
        callback.onConnected();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        AppLog.d(TAG, "Получена команда: 消息: " + text);

        try {
            JsonObject message = gson.fromJson(text, JsonObject.class);

            // 解析消息类型
            if (message.has("type")) {
                String type = message.get("type").getAsString();

                if ("SYSTEM".equals(type)) {
                    // Система消息（еслиПодключениеУспешно)
                    handleSystemMessage(message);
                } else if ("CALLBACK".equals(type)) {
                    // 回调消息（机器人消息)
                    handleCallbackMessage(message);
                }
            }

            // Отправка ACK Подтвердить
            if (message.has("messageId")) {
                sendAck(message.get("messageId").getAsString());
            }

        } catch (Exception e) {
            AppLog.e(TAG, "处理сообщения — ошибка", e);
        }
    }

    /**
     * 处理Система消息
     */
    private void handleSystemMessage(JsonObject message) {
        if (message.has("headers")) {
            JsonObject headers = message.getAsJsonObject("headers");
            if (headers.has("topic")) {
                String topic = headers.get("topic").getAsString();
                AppLog.d(TAG, "Система消息 topic: " + topic);
            }
        }
    }

    /**
     * 处理回调消息（机器人消息)
     */
    private void handleCallbackMessage(JsonObject message) {
        try {
            AppLog.d(TAG, "处理回调消息，完整消息: " + message.toString());

            if (!message.has("data")) {
                AppLog.w(TAG, "消息没有 data 字");
                return;
            }

            String dataStr = message.get("data").getAsString();
            AppLog.d(TAG, "data 字内容: " + dataStr);

            JsonObject data = gson.fromJson(dataStr, JsonObject.class);
            AppLog.d(TAG, "解析后  data: " + data.toString());

            // проверка 否 机器人  @  消息
            if (data.has("conversationType") && data.has("text")) {
                String conversationId = data.get("conversationId").getAsString();
                String conversationType = data.get("conversationType").getAsString();
                String senderUserId = data.has("senderStaffId") ?
                    data.get("senderStaffId").getAsString() : "unknown";

                JsonObject textObj = data.getAsJsonObject("text");
                String text = textObj.get("content").getAsString();

                AppLog.d(TAG, "Получена команда: 机器人消息 - conversationId: " + conversationId);
                AppLog.d(TAG, "Получена команда: 机器人消息 - conversationType: " + conversationType);
                AppLog.d(TAG, "Получена команда: 机器人消息 - senderUserId: " + senderUserId);
                AppLog.d(TAG, "Получена команда: 机器人消息 - text: " + text);

                // проверка 否содержит @机器人
                if (data.has("atUsers")) {
                    AppLog.d(TAG, "消息содержит @机器人，触发回调");
                    callback.onMessageReceived(conversationId, conversationType, senderUserId, text);
                } else {
                    AppLog.w(TAG, "消息不содержит atUsers 字，忽略");
                }
            } else {
                AppLog.w(TAG, "消息缺少必要字 - conversationType: " + data.has("conversationType") +
                    ", text: " + data.has("text"));
            }

        } catch (Exception e) {
            AppLog.e(TAG, "处理回调сообщения — ошибка", e);
            e.printStackTrace();
        }
    }

    /**
     * Отправка ACK Подтвердить
     */
    private void sendAck(String messageId) {
        JsonObject ack = new JsonObject();
        ack.addProperty("messageId", messageId);
        ack.addProperty("code", "200");
        ack.addProperty("message", "OK");

        String ackJson = gson.toJson(ack);
        webSocket.send(ackJson);
        AppLog.d(TAG, "Отправка ACK: " + messageId);
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        AppLog.d(TAG, "WebSocket Выполняется Закрыто: " + code + " - " + reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        AppLog.d(TAG, "WebSocket Закрыто: " + code + " - " + reason);
        callback.onDisconnected();

        if (isRunning) {
            scheduleReconnect();
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        AppLog.e(TAG, "WebSocket Ошибка подключения", t);
        callback.onError("Ошибка подключения: " + t.getMessage());
        callback.onDisconnected();

        if (isRunning) {
            scheduleReconnect();
        }
    }
}
