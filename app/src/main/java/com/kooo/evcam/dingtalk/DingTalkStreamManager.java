package com.kooo.evcam.dingtalk;


import com.kooo.evcam.AppLog;
import com.kooo.evcam.WakeUpHelper;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.dingtalk.open.app.stream.protocol.event.EventAckStatus;

import org.json.JSONObject;

/**
 * DingTalk Stream 客户端управление器
 * использование官方 app-stream-client SDK
 */
public class DingTalkStreamManager {
    private static final String TAG = "DingTalkStreamManager";
    private static final long RECONNECT_DELAY_MS = 5000; // 初始重连延迟5 сек.
    private static final long MAX_RECONNECT_DELAY_MS = 60_000; // максимум重连延迟60 сек.（指数退避限)

    // DingTalk官方事件主题
    private static final String BOT_MESSAGE_TOPIC = "/v1.0/im/bot/messages/get";

    private final Context context;
    private final DingTalkConfig config;
    private final DingTalkApiClient apiClient;
    private final ConnectionCallback callback;
    private final Handler mainHandler;

    private OpenDingTalkClient streamClient;
    private ChatbotMessageListener messageListener;
    private boolean isRunning = false;
    private boolean autoReconnect = false;
    private int reconnectAttempts = 0;
    private CommandCallback currentCommandCallback;

    // СетьСтатус监控（用于深度休眠唤醒后автоматически重连)
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile boolean networkWasLost = false;
    private Runnable pendingReconnectRunnable;
    private Runnable reconnectCheckRunnable;
    private static final long RECONNECT_AFTER_NETWORK_DELAY_MS = 10_000; // СетьВосстановление后等10 сек.再重连（深度休眠唤醒后Сеть栈необходимо时间绪)
    private static final long RECONNECT_CHECK_INTERVAL_MS = 120_000; // 2 мин.安全网проверка

    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public interface CommandCallback {
        void onRecordCommand(String conversationId, String conversationType, String userId, int durationSeconds);
        void onPhotoCommand(String conversationId, String conversationType, String userId);
        
        /**
         * ПолучениеПриложениеСтатусИнформация
         * @return СтатусИнформация字符串
         */
        default String getStatusInfo() {
            return "Информация о статусе недоступна";
        }
        
        /**
         * ЗапускНепрерывная запись（模拟点击Запись按钮)
         * @return выполнение结果消息
         */
        default String onStartRecordingCommand() {
            return "Функция недоступна";
        }
        
        /**
         * Остановить запись并退 до Фоновый режим
         * @return выполнение结果消息
         */
        default String onStopRecordingCommand() {
            return "Функция недоступна";
        }
        
        /**
         * Выход из приложения（需二 разПодтвердить)
         * @param confirmed  否Подтвердить
         * @return выполнение结果消息
         */
        default String onExitCommand(boolean confirmed) {
            return "Функция недоступна";
        }
        
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

    public DingTalkStreamManager(Context context, DingTalkConfig config,
                                  DingTalkApiClient apiClient, ConnectionCallback callback) {
        this.context = context;
        this.config = config;
        this.apiClient = apiClient;
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Запуск Stream Подключение
     * @param commandCallback команда回调
     */
    public void start(CommandCallback commandCallback) {
        start(commandCallback, false);
    }

    /**
     * Запуск Stream Подключение
     * @param commandCallback команда回调
     * @param enableAutoReconnect  否Включитьавтоматически重连
     */
    public void start(CommandCallback commandCallback, boolean enableAutoReconnect) {
        if (isRunning) {
            AppLog.w(TAG, "Stream 客户端 Работа");
            return;
        }

        this.currentCommandCallback = commandCallback;
        this.autoReconnect = enableAutoReconnect;
        this.reconnectAttempts = 0;

        startConnection();
    }

    /**
     * Внутреннее方法：ЗапускПодключение
     */
    private void startConnection() {
        if (isRunning) {
            AppLog.w(TAG, "Stream 客户端 Работа");
            return;
        }

        new Thread(() -> {
            try {
                AppLog.d(TAG, "Выполняется инициализацияDingTalk Stream 客户端...");

                // 创建消息监听器
                messageListener = new ChatbotMessageListener(context, apiClient, currentCommandCallback, mainHandler);

                // использование官方 SDK 构建客户端
                streamClient = OpenDingTalkStreamClientBuilder.custom()
                        .credential(new AuthClientCredential(
                                config.getClientId(),
                                config.getClientSecret()
                        ))
                        .registerCallbackListener(BOT_MESSAGE_TOPIC, messageListener)
                        .build();

                AppLog.d(TAG, "Stream 客户端创建，Выполняется ЗапускПодключение...");

                // ЗапускПодключение
                streamClient.start();

                isRunning = true;
                reconnectAttempts = 0; // Сброс重连计数
                AppLog.d(TAG, "Stream 客户端Запущено");

                // УведомлениеПодключениеУспешно
                mainHandler.post(() -> {
                    callback.onConnected();
                    // 注册СетьСтатус监控（用于深度休眠唤醒后автоматически重连)
                    registerNetworkCallback();
                    startReconnectCheck();
                });

            } catch (Exception e) {
                AppLog.e(TAG, "Запуск Stream 客户端Ошибка", e);
                isRunning = false;

                // Если Включитьавтоматически重连，использование指数退避无限重试
                if (autoReconnect) {
                    reconnectAttempts++;
                    // 指数退避：5s, 10s, 20s, 40s, 60s, 60s, ...
                    long delay = Math.min(RECONNECT_DELAY_MS * (1L << Math.min(reconnectAttempts - 1, 4)), MAX_RECONNECT_DELAY_MS);
                    AppLog.d(TAG, "将  " + delay + "ms 后попытка第 " + reconnectAttempts + "  раз重连");
                    mainHandler.postDelayed(() -> {
                        if (autoReconnect) {
                            startConnection();
                        }
                    }, delay);
                } else {
                    mainHandler.post(() -> callback.onError("Ошибка запуска: " + e.getMessage()));
                }
            }
        }).start();
    }

    /**
     * Остановка Stream Подключение
     */
    public void stop() {
        if (!isRunning) {
            return;
        }

        // Отключитьавтоматически重连
        autoReconnect = false;
        reconnectAttempts = 0;

        // Очистка Сеть监控 и Плановая проверка
        unregisterNetworkCallback();
        stopReconnectCheck();
        networkWasLost = false;
        if (pendingReconnectRunnable != null) {
            mainHandler.removeCallbacks(pendingReconnectRunnable);
            pendingReconnectRunnable = null;
        }

        new Thread(() -> {
            try {
                if (streamClient != null) {
                    AppLog.d(TAG, "Выполняется Остановка Stream 客户端...");
                    // OpenDingTalkClient doesn't have a close() method
                    // Just set to null to allow garbage collection
                    streamClient = null;
                }

                isRunning = false;
                AppLog.d(TAG, "Stream 客户端Остановлено");

                mainHandler.post(() -> callback.onDisconnected());

            } catch (Exception e) {
                AppLog.e(TAG, "Остановка Stream 客户端Ошибка", e);
            }
        }).start();
    }

    /**
     * проверка 否Выполняется Работа
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 强制重连（用于深度休眠唤醒后Подключение丢失 场景)
     * @param reason 重连原因（用于 д.志)
     */
    public synchronized void forceReconnect(String reason) {
        if (!autoReconnect) {
            AppLog.w(TAG, "автоматически重连Не Включить，跳过强制重连");
            return;
        }

        AppLog.d(TAG, "强制重连DingTalk Stream (" + reason + ")");

        // Очистка 旧Подключение Сеть监控（安全网проверка保留，让它持续守护)
        unregisterNetworkCallback();

        // 销毁旧Подключение
        try {
            streamClient = null;
        } catch (Exception e) {
            AppLog.e(TAG, "销毁旧 Stream 客户端Ошибка", e);
        }

        boolean wasRunning = isRunning;
        isRunning = false;
        reconnectAttempts = 0;

        // толькодо Работа时Уведомлениеотключено
        if (wasRunning) {
            mainHandler.post(() -> callback.onDisconnected());
        }

        // Отменадо可能существует 重连задача
        if (pendingReconnectRunnable != null) {
            mainHandler.removeCallbacks(pendingReconnectRunnable);
        }

        // 延迟后Запуск新Подключение
        pendingReconnectRunnable = () -> {
            if (autoReconnect && !isRunning) {
                AppLog.d(TAG, "Вкл始重新建立 Stream Подключение...");
                startConnection();
            }
        };
        mainHandler.postDelayed(pendingReconnectRunnable, RECONNECT_DELAY_MS);
    }

    /**
     * 注册СетьСтатус回调
     * 用于检测深度休眠唤醒后СетьВосстановление，автоматически触发重连
     */
    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                AppLog.w(TAG, "ConnectivityManager 不Доступно，跳过Сеть监控");
                return;
            }

            unregisterNetworkCallback();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (networkWasLost && autoReconnect) {
                        AppLog.d(TAG, "СетьВосстановление（深度休眠唤醒)，" + RECONNECT_AFTER_NETWORK_DELAY_MS + "ms 后重连");
                        networkWasLost = false;
                        mainHandler.postDelayed(() -> forceReconnect("Восстановление сети (пробуждение из сна)"), RECONNECT_AFTER_NETWORK_DELAY_MS);
                    }
                }

                @Override
                public void onLost(Network network) {
                    AppLog.d(TAG, "СетьПодключение丢失（可能进入深度休眠)，标记необходимо重连");
                    networkWasLost = true;
                }
            };

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(request, networkCallback);
            AppLog.d(TAG, "СетьСтатус回调注册（监控深度休眠唤醒)");

        } catch (Exception e) {
            AppLog.e(TAG, "注册СетьСтатус回调Ошибка", e);
        }
    }

    /**
     * 注销СетьСтатус回调
     */
    private void unregisterNetworkCallback() {
        try {
            if (networkCallback != null) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
                networkCallback = null;
            }
        } catch (Exception e) {
            AppLog.e(TAG, "注销СетьСтатус回调Ошибка", e);
        }
    }

    /**
     * Запуск定时重连安全网проверка
     * 每隔一时间проверкаСетьСтатус，防止 onAvailable 回调 遗漏
     */
    private void startReconnectCheck() {
        stopReconnectCheck();
        reconnectCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!autoReconnect) return;

                try {
                    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    boolean hasNetwork = cm != null && cm.getActiveNetwork() != null;

                    if (!isRunning && hasNetwork) {
                        // Подключение断但СетьДоступно → 可能重连Ошибка，再 раз触发重连
                        AppLog.w(TAG, "安全网проверка：ПодключениеНе Работа但СетьДоступно，触发重连");
                        networkWasLost = false;
                        forceReconnect("Проверка безопасности (соединение разорвано)");
                    } else if (isRunning && networkWasLost && hasNetwork) {
                        // Работа但Сеть曾丢失且Восстановление → onAvailable 可能 遗漏
                        AppLog.w(TAG, "安全网проверка：СетьВосстановление但Не Получена команда: 回调，强制重连");
                        networkWasLost = false;
                        forceReconnect("Проверка (пропущенное восстановление сети)");
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "安全网проверкаОшибка", e);
                }

                // 无论если何всепродолжить一轮проверка
                mainHandler.postDelayed(this, RECONNECT_CHECK_INTERVAL_MS);
            }
        };
        mainHandler.postDelayed(reconnectCheckRunnable, RECONNECT_CHECK_INTERVAL_MS);
    }

    /**
     * Остановка定时重连安全网проверка
     */
    private void stopReconnectCheck() {
        if (reconnectCheckRunnable != null) {
            mainHandler.removeCallbacks(reconnectCheckRunnable);
            reconnectCheckRunnable = null;
        }
    }

    /**
     * 机器人消息监听器
     * 实现官方 SDK  回调接口
     */
    private static class ChatbotMessageListener implements OpenDingTalkCallbackListener<String, EventAckStatus> {
        private static final String TAG = "ChatbotMessageListener";

        private final Context context;
        private final DingTalkApiClient apiClient;
        private final CommandCallback commandCallback;
        private final Handler mainHandler;

        public ChatbotMessageListener(Context context, DingTalkApiClient apiClient,
                                       CommandCallback commandCallback, Handler mainHandler) {
            this.context = context;
            this.apiClient = apiClient;
            this.commandCallback = commandCallback;
            this.mainHandler = mainHandler;
        }

        @Override
        public EventAckStatus execute(String messageJson) {
            try {
                // 记录原始消息用于отладка
                AppLog.d(TAG, "Получена команда: 原始消息JSON: " + messageJson);

                // 解析 JSON 字符串
                JSONObject message = new JSONObject(messageJson);
                AppLog.d(TAG, "解析后 消息 象: " + message.toString());

                String content = null;
                String conversationId = null;
                String conversationType = null;
                String senderId = null;
                String sessionWebhook = null;

                // 解析文本内容 - DingTalk机器人消息格式
                if (message.has("text")) {
                    JSONObject textObj = message.getJSONObject("text");
                    content = textObj.optString("content", "");
                } else if (message.has("content")) {
                    // 有些情况可能直接  content 字
                    JSONObject contentObj = message.getJSONObject("content");
                    if (contentObj.has("text")) {
                        content = contentObj.optString("text", "");
                    }
                }

                // 解析会话ID、会话类型 и Отправка者ID
                conversationId = message.optString("conversationId", "");
                if (conversationId.isEmpty()) {
                    conversationId = message.optString("openConversationId", "");
                }

                // 解析会话类型：1=личный чат，2=групповой чат
                conversationType = message.optString("conversationType", "");

                senderId = message.optString("senderStaffId", "");
                if (senderId.isEmpty()) {
                    senderId = message.optString("senderId", "");
                }

                // Получение sessionWebhook（用于回复消息)
                sessionWebhook = message.optString("sessionWebhook", "");

                // Если 消息пусто，可能 Другое类型 事件（если加入групповой чат等)，直接返回Успешно
                if (content == null || content.isEmpty()) {
                    AppLog.d(TAG, "消息内容пусто，可能 非文本消息илиСистема事件");
                    AppLog.d(TAG, "完整消息结构: " + message.toString(2));
                    return EventAckStatus.SUCCESS;
                }

                AppLog.d(TAG, "解析Успешно - 内容: " + content);
                AppLog.d(TAG, "解析Успешно - 会话ID: " + conversationId);
                AppLog.d(TAG, "解析Успешно - 会话类型: " + conversationType);
                AppLog.d(TAG, "解析Успешно - Отправка者ID: " + senderId);
                AppLog.d(TAG, "解析Успешно - SessionWebhook: " + sessionWebhook);

                // проверка sessionWebhook  否действует
                if (sessionWebhook.isEmpty()) {
                    AppLog.w(TAG, "SessionWebhook пусто，无法回复");
                    return EventAckStatus.SUCCESS;
                }

                // 解析команда
                String command = parseCommand(content);
                AppLog.d(TAG, "解析 команда: " + command);

                // 判断 否 Записькоманда，只有Записькоманда才解析时长
                if (command.startsWith("Запись") || command.toLowerCase().startsWith("record")) {
                    int durationSeconds = parseRecordDuration(command);
                    AppLog.d(TAG, "Получена команда: Записькоманда，时长: " + durationSeconds + "  сек.");

                    // ОтправкаПодтвердить消息，并 Отправказавершение后выполнениеЗаписькоманда
                    String confirmMsg = String.format("Получена команда записи, начинаю запись %d  сек. видео...", durationSeconds);
                    String finalConversationId = conversationId;
                    String finalConversationType = conversationType;
                    String finalSenderId = senderId;
                    int finalDuration = durationSeconds;
                    
                    sendResponseAndThen(sessionWebhook, confirmMsg, () -> {
                        // использование WakeUpHelper 唤醒屏幕并Запуск Activity
                        // 这样可以确保 Фоновый режим时также能нормальноЗапись
                        AppLog.d(TAG, "использование WakeUpHelper Начать запись...");
                        WakeUpHelper.launchForRecording(context, 
                            finalConversationId, finalConversationType, finalSenderId, finalDuration);
                    });

                } else if ("Фото".equals(command) || "photo".equalsIgnoreCase(command)) {
                    AppLog.d(TAG, "Получена команда: Фотокоманда");

                    // ОтправкаПодтвердить消息，并 Отправказавершение后выполнениеФотокоманда
                    String finalConversationId = conversationId;
                    String finalConversationType = conversationType;
                    String finalSenderId = senderId;
                    
                    sendResponseAndThen(sessionWebhook, "Получена команда фото, делаю снимок...", () -> {
                        // использование WakeUpHelper 唤醒屏幕并Запуск Activity
                        // 这样可以确保 Фоновый режим时также能нормальноФото
                        AppLog.d(TAG, "использование WakeUpHelper ЗапускФото...");
                        WakeUpHelper.launchForPhoto(context, 
                            finalConversationId, finalConversationType, finalSenderId);
                    });

                } else if ("Статус".equals(command) || "status".equalsIgnoreCase(command)) {
                    // Статускоманда：显示ПриложениеСтатус
                    AppLog.d(TAG, "Получена команда: Статускоманда");
                    String statusInfo = commandCallback != null ? 
                            commandCallback.getStatusInfo() : "Информация о статусе недоступна";
                    sendResponse(sessionWebhook, statusInfo);

                } else if ("Начать запись".equals(command) || "Начать запись".equals(command) || 
                           "start".equalsIgnoreCase(command)) {
                    // Начать записькоманда：唤醒 до Передний план并Начать непрерывную запись
                    AppLog.d(TAG, "Получена команда: Начать записькоманда");
                    if (commandCallback != null) {
                        String result = commandCallback.onStartRecordingCommand();
                        sendResponse(sessionWebhook, result);
                    } else {
                        sendResponse(sessionWebhook, "❌ Функция недоступна");
                    }

                } else if ("Остановить запись".equals(command) || "Остановить запись".equals(command) || 
                           "stop".equalsIgnoreCase(command)) {
                    // Остановить записькоманда：Остановить запись并退 до Фоновый режим
                    AppLog.d(TAG, "Получена команда: Остановить записькоманда");
                    if (commandCallback != null) {
                        String result = commandCallback.onStopRecordingCommand();
                        sendResponse(sessionWebhook, result);
                    } else {
                        sendResponse(sessionWebhook, "❌ Функция недоступна");
                    }

                } else if ("Выход".equals(command) || "exit".equalsIgnoreCase(command)) {
                    // Выходкоманда：необходимо二 разПодтвердить
                    AppLog.d(TAG, "Получена команда: Выходкоманда（需二 разПодтвердить)");
                    sendResponse(sessionWebhook, 
                        "⚠️ Подтвердите выход из EVCam?\n\n" +
                        "После выхода все записи и удалённые сервисы будут остановлены。\n" +
                        "Отправьте «Подтвердить выход» для подтверждения。");

                } else if ("Подтвердить выход".equals(command)) {
                    // Подтвердить выходкоманда：выполнениеВыход
                    AppLog.d(TAG, "Получена команда: Подтвердить выходкоманда");
                    if (commandCallback != null) {
                        String result = commandCallback.onExitCommand(true);
                        sendResponse(sessionWebhook, result);
                    } else {
                        sendResponse(sessionWebhook, "❌ Функция недоступна");
                    }

                } else if ("Передний план".equals(command) || "foreground".equalsIgnoreCase(command)) {
                    // Передний планкоманда：将Приложение переключено на передний план
                    AppLog.d(TAG, "Получена команда: Передний планкоманда");
                    if (commandCallback != null) {
                        String result = commandCallback.onForegroundCommand();
                        sendResponse(sessionWebhook, result);
                    } else {
                        sendResponse(sessionWebhook, "❌ Функция недоступна");
                    }

                } else if ("Фоновый режим".equals(command) || "background".equalsIgnoreCase(command)) {
                    // Фоновый режимкоманда：将Приложениепереключиться в фоновый режим
                    AppLog.d(TAG, "Получена команда: Фоновый режимкоманда");
                    if (commandCallback != null) {
                        String result = commandCallback.onBackgroundCommand();
                        sendResponse(sessionWebhook, result);
                    } else {
                        sendResponse(sessionWebhook, "❌ Функция недоступна");
                    }

                } else if ("Помощь".equals(command) || "help".equalsIgnoreCase(command)) {
                    sendResponse(sessionWebhook,
                        "Доступные команды:\n" +
                        "• Статус — просмотр состояния приложения\n" +
                        "• Передний план — переключить приложение на передний план\n" +
                        "• Фоновый режим — переключить приложение в фоновый режим\n" +
                        "• Начать запись — запуск непрерывной записи\n" +
                        "• Остановить запись — остановка записи и переход в фон\n" +
                        "• Запись — запись 60 секунд видео\n" +
                        "• Запись+число — запись указанного кол-ва секунд (напр.: Запись30)\n" +
                        "• Фото — сделать снимок\n" +
                        "• Выход - Выход из приложения (с подтверждением)\n" +
                        "• Помощь — показать справку");

                } else {
                    AppLog.d(TAG, "Неизвестная команда: " + command);
                    sendResponse(sessionWebhook,
                        "Неизвестная команда。Отправка「Помощь」ПросмотрДоступные команды。");
                }

                return EventAckStatus.SUCCESS;

            } catch (Exception e) {
                AppLog.e(TAG, "处理机器人сообщения — ошибка", e);
                return EventAckStatus.LATER;
            }
        }

        /**
         * 解析команда文本
         * 移除 @机器人  部分，提取实际команда
         */
        private String parseCommand(String text) {
            if (text == null) {
                return "";
            }

            // 移除 @xxx 部分 и 多余空格
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
         * Отправка响应消息 до DingTalk（использование sessionWebhook)
         */
        private void sendResponse(String sessionWebhook, String message) {
            new Thread(() -> {
                try {
                    apiClient.sendMessageViaWebhook(sessionWebhook, message);
                    AppLog.d(TAG, "响应消息Отправка: " + message);
                } catch (Exception e) {
                    AppLog.e(TAG, "Отправка响应сообщения — ошибка", e);
                }
            }).start();
        }

        /**
         * Отправка响应消息 до DingTalk，并 Отправказавершение后выполнение回调
         * @param sessionWebhook Webhook URL
         * @param message 消息内容
         * @param callback Отправказавершение后 回调
         */
        private void sendResponseAndThen(String sessionWebhook, String message, Runnable callback) {
            new Thread(() -> {
                try {
                    // ОтправкаПодтвердить消息
                    apiClient.sendMessageViaWebhook(sessionWebhook, message);
                    AppLog.d(TAG, "响应消息Отправка: " + message);
                    
                    // ОтправкаУспешно后выполнение回调
                    if (callback != null) {
                        callback.run();
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "Отправка响应сообщения — ошибка", e);
                    // т.е.使Ошибка отправки，такжевыполнение回调（避免команда 阻塞)
                    if (callback != null) {
                        callback.run();
                    }
                }
            }).start();
        }
    }
}
