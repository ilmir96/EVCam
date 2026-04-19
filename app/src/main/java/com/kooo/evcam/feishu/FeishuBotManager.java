package com.kooo.evcam.feishu;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.WakeUpHelper;
import com.kooo.evcam.feishu.pb.Pbbp2Frame;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Feishu Bot управление器（轻量级实现)
 * использование OkHttp WebSocket + 轻量级 Protobuf 实现，不依赖官方 SDK
 */
public class FeishuBotManager {
    private static final String TAG = "FeishuBotManager";
    private static final int PING_INTERVAL_MS = 120000; // 2 мин.Отправка一 раз ping
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 5000; // 5 сек.

    private final Context context;
    private final FeishuConfig config;
    private final FeishuApiClient apiClient;
    private final ConnectionCallback connectionCallback;
    private final Handler mainHandler;
    private final Gson gson;

    private OkHttpClient wsClient;
    private WebSocket webSocket;
    private volatile boolean isRunning = false;
    private volatile boolean shouldStop = false;
    private int reconnectAttempts = 0;
    private CommandCallback currentCommandCallback;

    // WebSocket ПодключениеИнформация
    private int serviceId = 0;
    private String connId = "";

    // 消息分包缓存
    private final ConcurrentHashMap<String, byte[][]> messageCache = new ConcurrentHashMap<>();

    // 心跳定时器
    private Handler pingHandler;
    private Runnable pingRunnable;

    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public interface CommandCallback {
        void onRecordCommand(String chatId, String messageId, int durationSeconds);
        void onPhotoCommand(String chatId, String messageId);
        String getStatusInfo();
        String onStartRecordingCommand();
        String onStopRecordingCommand();
        String onExitCommand(boolean confirmed);
        
        /**
         * переключиться на передний план
         * @return выполнение结果消息
         */
        default String onForegroundCommand() {
            return "Функция недоступна";
        }
        
        /**
         * переключиться в фоновый режим
         * @return выполнение结果消息
         */
        default String onBackgroundCommand() {
            return "Функция недоступна";
        }
    }

    public FeishuBotManager(Context context, FeishuConfig config,
                            FeishuApiClient apiClient, ConnectionCallback callback) {
        this.context = context;
        this.config = config;
        this.apiClient = apiClient;
        this.connectionCallback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
        this.pingHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Запуск WebSocket Подключение
     */
    public void start(CommandCallback commandCallback) {
        if (isRunning) {
            AppLog.w(TAG, "Bot  Работа");
            return;
        }

        this.currentCommandCallback = commandCallback;
        this.shouldStop = false;
        this.reconnectAttempts = 0;

        String appId = config.getAppId();
        String appSecret = config.getAppSecret();

        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            AppLog.e(TAG, "App ID или App Secret не настроены");
            mainHandler.post(() -> connectionCallback.onError("App ID или App Secret не настроены"));
            return;
        }

        AppLog.d(TAG, "Выполняется инициализацияFeishu WebSocket Подключение...");
        startConnection();
    }

    /**
     * Внутреннее方法：ЗапускПодключение
     */
    private void startConnection() {
        new Thread(() -> {
            try {
                // 1. Получение WebSocket ПодключениеИнформация
                AppLog.d(TAG, "Выполняется Получение WebSocket Подключение地址...");
                FeishuApiClient.WebSocketConnection wsInfo = apiClient.getWebSocketConnection();
                String wsUrl = wsInfo.url;
                AppLog.d(TAG, "WebSocket URL: " + wsUrl);

                // 2.  от  URL 解析 service_id  и  device_id
                parseUrlParams(wsUrl);

                // 3. 创建 OkHttp WebSocket 客户端
                wsClient = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.SECONDS) // 无таймаут，保持长Подключение
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build();

                // 4. 建立 WebSocket Подключение
                Request request = new Request.Builder()
                        .url(wsUrl)
                        .build();

                webSocket = wsClient.newWebSocket(request, new FeishuWebSocketListener());
                AppLog.d(TAG, "WebSocket Подключение求Отправка");

            } catch (Exception e) {
                AppLog.e(TAG, "Запуск WebSocket Ошибка подключения", e);
                handleConnectionError(e.getMessage());
            }
        }).start();
    }

    /**
     *  от  WebSocket URL 解析参数
     */
    private void parseUrlParams(String wsUrl) {
        try {
            // 将 wss:// 替换为 https:// 以便использование Uri 解析
            String httpUrl = wsUrl.replace("wss://", "https://").replace("ws://", "http://");
            Uri uri = Uri.parse(httpUrl);

            String serviceIdStr = uri.getQueryParameter("service_id");
            if (serviceIdStr != null) {
                serviceId = Integer.parseInt(serviceIdStr);
            }

            connId = uri.getQueryParameter("device_id");
            if (connId == null) {
                connId = "";
            }

            AppLog.d(TAG, "解析 URL 参数: serviceId=" + serviceId + ", connId=" + connId);
        } catch (Exception e) {
            AppLog.e(TAG, "解析 URL 参数Ошибка", e);
        }
    }

    /**
     * WebSocket 监听器
     */
    private class FeishuWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            AppLog.d(TAG, "WebSocket Подключение建立");
            isRunning = true;
            reconnectAttempts = 0;

            // Запуск心跳定时器
            startPingTimer();

            mainHandler.post(() -> connectionCallback.onConnected());
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            // Feishuиспользование二进制 Protobuf 消息，文本消息可能 握手илиОшибка
            AppLog.d(TAG, "Получена команда: 文本消息: " + text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            // 处理二进制 Protobuf 消息
            AppLog.d(TAG, "Получена команда: 二进制消息: " + bytes.size() + " 字节");
            processProtobufMessage(bytes.toByteArray());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            AppLog.d(TAG, "WebSocket Выполняется Закрыто: code=" + code + ", reason=" + reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            AppLog.d(TAG, "WebSocket Закрыто: code=" + code + ", reason=" + reason);
            isRunning = false;
            stopPingTimer();

            if (!shouldStop) {
                // 非主动Закрыто，попытка重连
                attemptReconnect();
            } else {
                mainHandler.post(() -> connectionCallback.onDisconnected());
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            AppLog.e(TAG, "WebSocket Ошибка подключения", t);
            isRunning = false;
            stopPingTimer();
            handleConnectionError(t.getMessage());
        }
    }

    /**
     * 处理 Protobuf 消息
     */
    private void processProtobufMessage(byte[] data) {
        try {
            Pbbp2Frame frame = Pbbp2Frame.parseFrom(data);
            AppLog.d(TAG, "解析帧: " + frame.toString());

            if (frame.isControlFrame()) {
                handleControlFrame(frame);
            } else if (frame.isDataFrame()) {
                handleDataFrame(frame, data);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "处理 Protobuf сообщения — ошибка", e);
        }
    }

    /**
     * 处理控制帧
     */
    private void handleControlFrame(Pbbp2Frame frame) {
        String type = frame.getMessageType();

        if (Pbbp2Frame.TYPE_PING.equals(type)) {
            AppLog.d(TAG, "Получена команда: Сервис器 ping");
            return;
        }

        if (Pbbp2Frame.TYPE_PONG.equals(type)) {
            AppLog.d(TAG, "Получена команда: 心跳响应 pong");
            // 可以 от  payload Получениеконфигурацияобновление
            return;
        }
    }

    /**
     * 处理数据帧
     */
    private void handleDataFrame(Pbbp2Frame frame, byte[] rawData) {
        try {
            String msgId = frame.getHeaderValue(Pbbp2Frame.HEADER_MESSAGE_ID);
            String traceId = frame.getHeaderValue(Pbbp2Frame.HEADER_TRACE_ID);
            String sumStr = frame.getHeaderValue(Pbbp2Frame.HEADER_SUM);
            String seqStr = frame.getHeaderValue(Pbbp2Frame.HEADER_SEQ);
            String type = frame.getMessageType();

            int sum = sumStr != null ? Integer.parseInt(sumStr) : 1;
            int seq = seqStr != null ? Integer.parseInt(seqStr) : 0;

            byte[] payload = frame.getPayload();

            // 处理分包消息
            if (sum > 1) {
                payload = combinePackets(msgId, sum, seq, payload);
                if (payload == null) {
                    // 还有包Не  до 达
                    return;
                }
            }

            AppLog.d(TAG, "数据帧类型: " + type + ", msgId: " + msgId + ", traceId: " + traceId);

            // 处理事件消息
            if (Pbbp2Frame.TYPE_EVENT.equals(type)) {
                String payloadStr = new String(payload, StandardCharsets.UTF_8);
                AppLog.d(TAG, "事件 payload: " + payloadStr);

                long startTime = System.currentTimeMillis();
                processEventPayload(payloadStr);
                long bizRt = System.currentTimeMillis() - startTime;

                // Отправка响应
                sendEventResponse(frame, bizRt);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "处理数据帧Ошибка", e);
        }
    }

    /**
     * 合并分包消息
     */
    private byte[] combinePackets(String msgId, int sum, int seq, byte[] data) {
        byte[][] packets = messageCache.get(msgId);
        if (packets == null) {
            packets = new byte[sum][];
            messageCache.put(msgId, packets);
        }

        packets[seq] = data;

        // проверка 否所有包все до 达
        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        for (byte[] packet : packets) {
            if (packet == null) {
                return null; // 还有包Не  до 达
            }
            try {
                combined.write(packet);
            } catch (Exception ignored) {
            }
        }

        // очистка缓存
        messageCache.remove(msgId);
        return combined.toByteArray();
    }

    /**
     * Отправка事件响应
     */
    private void sendEventResponse(Pbbp2Frame requestFrame, long bizRt) {
        try {
            // 构建响应 JSON
            JsonObject response = new JsonObject();
            response.addProperty("code", 200);
            byte[] responsePayload = gson.toJson(response).getBytes(StandardCharsets.UTF_8);

            // 复制求帧并Настройки响应 payload
            Pbbp2Frame responseFrame = requestFrame.copyWithPayload(responsePayload);
            responseFrame.addHeader(Pbbp2Frame.HEADER_BIZ_RT, String.valueOf(bizRt));

            byte[] frameBytes = responseFrame.toByteArray();
            webSocket.send(ByteString.of(frameBytes));
            AppLog.d(TAG, "Отправка事件响应");
        } catch (Exception e) {
            AppLog.e(TAG, "Отправка事件响应Ошибка", e);
        }
    }

    /**
     * 处理事件 payload
     */
    private void processEventPayload(String payloadStr) {
        try {
            JsonObject payload = gson.fromJson(payloadStr, JsonObject.class);

            // проверка 否有 header  и  event
            if (!payload.has("header") || !payload.has("event")) {
                AppLog.d(TAG, "非事件消息格式");
                return;
            }

            JsonObject header = payload.getAsJsonObject("header");
            JsonObject event = payload.getAsJsonObject("event");

            String eventType = header.has("event_type") ? header.get("event_type").getAsString() : "";
            AppLog.d(TAG, "事件类型: " + eventType);

            // 只处理消息接收事件
            if (!"im.message.receive_v1".equals(eventType)) {
                AppLog.d(TAG, "非消息事件，忽略: " + eventType);
                return;
            }

            // 解析消息
            JsonObject messageObj = event.getAsJsonObject("message");
            String messageType = messageObj.has("message_type") ? messageObj.get("message_type").getAsString() : "";
            String chatId = messageObj.has("chat_id") ? messageObj.get("chat_id").getAsString() : "";
            String messageId = messageObj.has("message_id") ? messageObj.get("message_id").getAsString() : "";
            String chatType = messageObj.has("chat_type") ? messageObj.get("chat_type").getAsString() : "";

            // ПолучениеОтправка者Информация
            String senderId = "";
            if (event.has("sender")) {
                JsonObject sender = event.getAsJsonObject("sender");
                if (sender.has("sender_id")) {
                    JsonObject senderIdObj = sender.getAsJsonObject("sender_id");
                    senderId = senderIdObj.has("open_id") ? senderIdObj.get("open_id").getAsString() : "";
                }
            }

            AppLog.d(TAG, "消息类型: " + messageType + ", chatId: " + chatId + ", senderId: " + senderId);

            // проверка用户 否 разрешить
            if (!config.isUserIdAllowed(senderId)) {
                AppLog.d(TAG, "用户不 白名单: " + senderId);
                return;
            }

            // 只处理文本消息
            if (!"text".equals(messageType)) {
                AppLog.d(TAG, "非文本消息，忽略: " + messageType);
                return;
            }

            // 解析消息内容
            String content = messageObj.has("content") ? messageObj.get("content").getAsString() : "";
            Map<String, String> contentMap = new HashMap<>();
            try {
                contentMap = gson.fromJson(content, new TypeToken<Map<String, String>>() {}.getType());
            } catch (Exception e) {
                AppLog.e(TAG, "解析消息内容Ошибка", e);
                return;
            }

            String text = contentMap.get("text");
            if (text == null || text.isEmpty()) {
                AppLog.d(TAG, "消息内容пусто");
                return;
            }

            AppLog.d(TAG, "Получена команда: 文本消息: " + text);

            // 处理команда
            handleCommand(chatId, messageId, chatType, text);

        } catch (Exception e) {
            AppLog.e(TAG, "处理事件 payload Ошибка", e);
        }
    }

    /**
     * 处理команда
     */
    private void handleCommand(String chatId, String messageId, String chatType, String content) {
        // 移除 @机器人 部分
        String command = content.replaceAll("@\\S+\\s*", "").trim();
        AppLog.d(TAG, "解析команда: " + command);

        try {
            if (command.startsWith("Запись") || command.toLowerCase().startsWith("record")) {
                int durationSeconds = parseRecordDuration(command);
                AppLog.d(TAG, "Получена команда: Записькоманда，时长: " + durationSeconds + "  сек.");

                String confirmMsg = String.format("Получена команда записи, начинаю запись %d  сек. видео...", durationSeconds);
                sendReplyAndThen(chatId, messageId, chatType, confirmMsg, () -> {
                    WakeUpHelper.launchForRecordingFeishu(context, chatId, messageId, durationSeconds);
                });

            } else if ("Фото".equals(command) || "photo".equalsIgnoreCase(command)) {
                AppLog.d(TAG, "Получена команда: Фотокоманда");

                sendReplyAndThen(chatId, messageId, chatType, "Получена команда фото, делаю снимок...", () -> {
                    WakeUpHelper.launchForPhotoFeishu(context, chatId, messageId);
                });

            } else if ("Статус".equals(command) || "status".equalsIgnoreCase(command)) {
                AppLog.d(TAG, "Получена команда: Статускоманда");
                String statusInfo = currentCommandCallback != null ?
                        currentCommandCallback.getStatusInfo() : "✅ Bot работает";
                sendReply(chatId, messageId, chatType, statusInfo);

            } else if ("Начать запись".equals(command) || "Начать запись".equals(command) ||
                       "start".equalsIgnoreCase(command)) {
                AppLog.d(TAG, "Получена команда: Начать записькоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onStartRecordingCommand();
                    sendReply(chatId, messageId, chatType, result);
                } else {
                    sendReply(chatId, messageId, chatType, "❌ Функция недоступна");
                }

            } else if ("Остановить запись".equals(command) || "Остановить запись".equals(command) ||
                       "stop".equalsIgnoreCase(command)) {
                AppLog.d(TAG, "Получена команда: Остановить записькоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onStopRecordingCommand();
                    sendReply(chatId, messageId, chatType, result);
                } else {
                    sendReply(chatId, messageId, chatType, "❌ Функция недоступна");
                }

            } else if ("Выход".equals(command) || "exit".equalsIgnoreCase(command)) {
                AppLog.d(TAG, "Получена команда: Выходкоманда（需二 разПодтвердить)");
                sendReply(chatId, messageId, chatType,
                    "⚠️ Подтвердите выход из EVCam?\n\n" +
                    "После выхода все записи и удалённые сервисы будут остановлены。\n" +
                    "Отправьте «Подтвердить выход» для подтверждения。");

            } else if ("Подтвердить выход".equals(command)) {
                AppLog.d(TAG, "Получена команда: Подтвердить выходкоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onExitCommand(true);
                    sendReply(chatId, messageId, chatType, result);
                } else {
                    sendReply(chatId, messageId, chatType, "❌ Функция недоступна");
                }

            } else if ("Передний план".equals(command) || "foreground".equalsIgnoreCase(command)) {
                // Передний планкоманда：将Приложение переключено на передний план
                AppLog.d(TAG, "Получена команда: Передний планкоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onForegroundCommand();
                    sendReply(chatId, messageId, chatType, result);
                } else {
                    sendReply(chatId, messageId, chatType, "❌ Функция недоступна");
                }

            } else if ("Фоновый режим".equals(command) || "background".equalsIgnoreCase(command)) {
                // Фоновый режимкоманда：将Приложениепереключиться в фоновый режим
                AppLog.d(TAG, "Получена команда: Фоновый режимкоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onBackgroundCommand();
                    sendReply(chatId, messageId, chatType, result);
                } else {
                    sendReply(chatId, messageId, chatType, "❌ Функция недоступна");
                }

            } else if ("Помощь".equals(command) || "help".equalsIgnoreCase(command)) {
                sendReply(chatId, messageId, chatType,
                    "📋 EVCam Удалённое управление\n" +
                    "━━━━━━━━━━━━━━\n\n" +
                    "📹 Удалённая запись\n" +
                    "• Запись - Запись60 сек. видео\n" +
                    "• Запись30 - Запись30 сек. видео\n\n" +
                    "▶️ Непрерывная запись\n" +
                    "• Начать запись — запуск непрерывной записи\n" +
                    "• Остановить запись - Остановить запись\n\n" +
                    "📷 Фото\n" +
                    "• Фото — сделать снимок\n\n" +
                    "🔄 Переключение переднего/фонового режима\n" +
                    "• Передний план - переключиться на передний план\n" +
                    "• Фоновый режим - переключиться в фоновый режим\n\n" +
                    "ℹ️ Другое\n" +
                    "• Статус — просмотр состояния приложения\n" +
                    "• Выход - Выход из приложения\n" +
                    "• Помощь — показать справку");

            } else {
                AppLog.d(TAG, "Неизвестная команда: " + command);
                sendReply(chatId, messageId, chatType, "Неизвестная команда。Отправка「Помощь」ПросмотрДоступные команды。");
            }

        } catch (Exception e) {
            AppLog.e(TAG, "处理командаОшибка", e);
        }
    }

    /**
     * 解析Запись时长
     */
    private int parseRecordDuration(String command) {
        String durationStr = command.replaceAll("(?i)(Запись|record)", "").trim();

        if (durationStr.isEmpty()) {
            return 60; // По умолчанию 1  мин.
        }

        try {
            int duration = Integer.parseInt(durationStr);
            if (duration < 5) return 5;
            if (duration > 600) return 600;
            return duration;
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    /**
     * Отправка回复消息
     */
    private void sendReply(String chatId, String messageId, String chatType, String text) {
        new Thread(() -> {
            try {
                if ("p2p".equals(chatType)) {
                    // 私聊：использование create Отправка
                    apiClient.sendTextMessage("chat_id", chatId, text);
                } else {
                    // групповой чат：использование reply 回复
                    apiClient.replyMessage(messageId, text);
                }
                AppLog.d(TAG, "消息ОтправкаУспешно");
            } catch (Exception e) {
                AppLog.e(TAG, "Ошибка отправки сообщения", e);
            }
        }).start();
    }

    /**
     * Отправка回复消息并выполнение回调
     */
    private void sendReplyAndThen(String chatId, String messageId, String chatType, String text, Runnable callback) {
        new Thread(() -> {
            try {
                if ("p2p".equals(chatType)) {
                    apiClient.sendTextMessage("chat_id", chatId, text);
                } else {
                    apiClient.replyMessage(messageId, text);
                }
                AppLog.d(TAG, "回复消息Отправка");

                if (callback != null) {
                    callback.run();
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Отправка回复Ошибка", e);
                if (callback != null) {
                    callback.run();
                }
            }
        }).start();
    }

    /**
     * Запуск心跳定时器
     */
    private void startPingTimer() {
        pingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && webSocket != null) {
                    try {
                        // Отправка Protobuf 格式  ping 帧
                        Pbbp2Frame pingFrame = Pbbp2Frame.createPingFrame(serviceId);
                        byte[] frameBytes = pingFrame.toByteArray();
                        webSocket.send(ByteString.of(frameBytes));
                        AppLog.d(TAG, "Отправка心跳 ping");
                    } catch (Exception e) {
                        AppLog.e(TAG, "Отправка心跳Ошибка", e);
                    }

                    // продолжить一 раз心跳
                    pingHandler.postDelayed(this, PING_INTERVAL_MS);
                }
            }
        };
        pingHandler.postDelayed(pingRunnable, PING_INTERVAL_MS);
    }

    /**
     * Остановка心跳定时器
     */
    private void stopPingTimer() {
        if (pingRunnable != null) {
            pingHandler.removeCallbacks(pingRunnable);
        }
    }

    /**
     * 处理ПодключениеОшибка
     */
    private void handleConnectionError(String errorMsg) {
        if (!shouldStop && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            AppLog.d(TAG, "将  " + RECONNECT_DELAY_MS + "ms Зпопытка第 " + reconnectAttempts + "  раз重连");
            mainHandler.postDelayed(() -> {
                if (!shouldStop) {
                    startConnection();
                }
            }, RECONNECT_DELAY_MS);
        } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            AppLog.w(TAG, "达 до максимум重连 раз数（" + MAX_RECONNECT_ATTEMPTS + ")，Ошибка подключения");
            mainHandler.post(() -> connectionCallback.onError("Ошибка подключения: " + errorMsg));
        }
    }

    /**
     * попытка重连
     */
    private void attemptReconnect() {
        if (!shouldStop && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            AppLog.d(TAG, "Подключениеотключено，将  " + RECONNECT_DELAY_MS + "ms Зпопытка第 " + reconnectAttempts + "  раз重连");
            mainHandler.postDelayed(() -> {
                if (!shouldStop) {
                    startConnection();
                }
            }, RECONNECT_DELAY_MS);
        } else {
            mainHandler.post(() -> connectionCallback.onDisconnected());
        }
    }

    /**
     * Остановка Bot
     */
    public void stop() {
        AppLog.d(TAG, "Выполняется Остановка Bot...");
        shouldStop = true;
        isRunning = false;

        stopPingTimer();

        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
            webSocket = null;
        }

        if (wsClient != null) {
            wsClient.dispatcher().executorService().shutdown();
            wsClient = null;
        }

        // очистка消息缓存
        messageCache.clear();

        AppLog.d(TAG, "Bot Остановлено");
    }

    /**
     * проверка 否Выполняется Работа
     */
    public boolean isRunning() {
        return isRunning;
    }
}
