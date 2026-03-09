package com.kooo.evcam.telegram;

import com.kooo.evcam.AppLog;
import com.kooo.evcam.WakeUpHelper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Telegram Bot 消息轮询управление器
 * использование Long Polling 方式接收消息
 */
public class TelegramBotManager {
    private static final String TAG = "TelegramBotManager";
    private static final int POLL_TIMEOUT = 30; // 长轮询таймаут时间（ сек.)
    private static final int POLL_LIMIT = 5; // 每 раз拉取 消息数量限制
    private static final int MESSAGE_EXPIRE_SECONDS = 600; // 消息истекло时间（10 мин. = 600 сек.)
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 5000; // 5 сек.
    private static final long CONFLICT_RETRY_DELAY_MS = 10000; // 409 冲突时ожидание 10  сек.再重试
    private static final int MAX_CONFLICT_RETRIES = 3; // максимум冲突重试 раз数

    private final Context context;
    private final TelegramConfig config;
    private final TelegramApiClient apiClient;
    private final ConnectionCallback connectionCallback;
    private final Handler mainHandler;

    private volatile boolean isRunning = false;
    private volatile boolean shouldStop = false;
    private Thread pollingThread;
    private int reconnectAttempts = 0;
    private int conflictRetries = 0;
    private CommandCallback currentCommandCallback;

    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public interface CommandCallback {
        void onRecordCommand(long chatId, int durationSeconds);
        void onPhotoCommand(long chatId);
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

    public TelegramBotManager(Context context, TelegramConfig config,
                               TelegramApiClient apiClient, ConnectionCallback callback) {
        this.context = context;
        this.config = config;
        this.apiClient = apiClient;
        this.connectionCallback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Запуск消息轮询
     */
    public synchronized void start(CommandCallback commandCallback) {
        if (isRunning) {
            AppLog.w(TAG, "Bot  Работа");
            return;
        }

        // 立т.е.Настройки为РаботаСтатус，防止重复Запуск
        isRunning = true;
        
        this.currentCommandCallback = commandCallback;
        this.shouldStop = false;
        this.reconnectAttempts = 0;
        this.conflictRetries = 0;

        startPolling();
    }

    /**
     * Внутреннее方法：Запуск轮询线程
     */
    private void startPolling() {
        pollingThread = new Thread(() -> {
            try {
                AppLog.d(TAG, "Выполняется 验证 Bot Token...");

                // 验证 Token
                JsonObject botInfo = apiClient.getMe();
                String botUsername = botInfo.get("username").getAsString();
                AppLog.d(TAG, "Bot 验证Успешно: @" + botUsername);

                // очистка可能существует 旧Подключение（Отправка一 шт.无ожидание 求来"抢占"Подключение)
                AppLog.d(TAG, "очистка旧ПодключениеСтатус...");
                try {
                    // использование timeout=0 立т.е.返回，这会отключеноДругое可能существует 长轮询Подключение
                    apiClient.getUpdates(-1, 0, 1);
                    Thread.sleep(500); // 短暂ожидание
                } catch (Exception e) {
                    AppLog.d(TAG, "очистка旧Подключение: " + e.getMessage());
                    // Если   409 Ошибка，ожидание更长时间
                    if (e.getMessage() != null && e.getMessage().contains("409")) {
                        AppLog.d(TAG, "Обнаружено 409 冲突，ожидание旧Подключениеотключено...");
                        Thread.sleep(3000);
                    }
                }

                // isRunning   start() Настройки
                reconnectAttempts = 0;

                // УведомлениеПодключениеУспешно
                mainHandler.post(() -> connectionCallback.onConnected());

                // Вкл始长轮询
                long offset = config.getLastUpdateId() + 1;

                while (!shouldStop) {
                    try {
                        JsonArray updates = apiClient.getUpdates(offset, POLL_TIMEOUT, POLL_LIMIT);
                        long currentTime = System.currentTimeMillis() / 1000; // Текущий时间（ сек.)

                        for (int i = 0; i < updates.size(); i++) {
                            JsonObject update = updates.get(i).getAsJsonObject();
                            long updateId = update.get("update_id").getAsLong();

                            // 处理消息
                            if (update.has("message")) {
                                JsonObject message = update.getAsJsonObject("message");

                                // проверка消息时间，忽略超过 10  мин. 旧消息
                                if (message.has("date")) {
                                    long messageTime = message.get("date").getAsLong();
                                    long messageAge = currentTime - messageTime;

                                    if (messageAge > MESSAGE_EXPIRE_SECONDS) {
                                        AppLog.d(TAG, "忽略истекло消息，消息时间: " + messageTime +
                                                ", прошло " + messageAge + "  сек.");
                                        // 仍然обновление offset，避免重复拉取
                                        offset = updateId + 1;
                                        config.saveLastUpdateId(updateId);
                                        continue;
                                    }
                                }

                                processMessage(message);
                            }

                            // обновление offset
                            offset = updateId + 1;
                            config.saveLastUpdateId(updateId);
                        }

                    } catch (Exception e) {
                        if (!shouldStop) {
                            String errorMsg = e.getMessage();
                            AppLog.e(TAG, "轮询出错: " + errorMsg);
                            
                            // проверка 否  409 冲突Ошибка
                            if (errorMsg != null && errorMsg.contains("409")) {
                                conflictRetries++;
                                if (conflictRetries >= MAX_CONFLICT_RETRIES) {
                                    // 只记录 д.志，不弹窗（不影响实际Подключение)
                                    AppLog.w(TAG, "409 冲突Ошибка达 до максимум重试 раз数，可能有Другое设备 Работа此 Bot");
                                    shouldStop = true;
                                    break;
                                }
                                AppLog.d(TAG, "409 冲突，ожидание " + CONFLICT_RETRY_DELAY_MS + "ms 后重试（第 " + conflictRetries + "  раз)");
                                Thread.sleep(CONFLICT_RETRY_DELAY_MS);
                            } else {
                                // ДругоеОшибка，短暂休眠后продолжить
                                conflictRetries = 0; // Сброс冲突计数
                                Thread.sleep(1000);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                AppLog.e(TAG, "Запуск Bot Ошибка", e);
                isRunning = false;

                // попытка重连
                if (!shouldStop && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++;
                    AppLog.d(TAG, "将  " + RECONNECT_DELAY_MS + "ms 后попытка第 " + reconnectAttempts + "  раз重连");
                    mainHandler.postDelayed(() -> {
                        if (!shouldStop) {
                            startPolling();
                        }
                    }, RECONNECT_DELAY_MS);
                } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                    // 只记录 д.志，不弹窗
                    AppLog.w(TAG, "达 до максимум重连 раз数（" + MAX_RECONNECT_ATTEMPTS + ")，Ошибка запуска: " + e.getMessage());
                }
            }

            isRunning = false;
            if (shouldStop) {
                mainHandler.post(() -> connectionCallback.onDisconnected());
            }
        });

        pollingThread.setName("TelegramPolling");
        pollingThread.start();
    }

    /**
     * 处理Получена команда:  消息
     */
    private void processMessage(JsonObject message) {
        try {
            // Получение chat Информация
            JsonObject chat = message.getAsJsonObject("chat");
            long chatId = chat.get("id").getAsLong();
            String chatType = chat.get("type").getAsString(); // private, group, supergroup, channel

            // проверка 否разрешить此 chat
            if (!config.isChatIdAllowed(chatId)) {
                AppLog.d(TAG, "Chat ID 不 白名单: " + chatId);
                return;
            }

            // 获Отмена息文本
            if (!message.has("text")) {
                return; // 非文本消息，忽略
            }

            String text = message.get("text").getAsString();
            AppLog.d(TAG, "Получена команда: 消息 - chatId: " + chatId + ", type: " + chatType + ", text: " + text);

            // 解析команда
            String command = parseCommand(text);
            AppLog.d(TAG, "解析 команда: " + command);

            // 处理команда
            if (command.startsWith("/record") || command.startsWith("Запись") ||
                command.toLowerCase().startsWith("record")) {

                int durationSeconds = parseRecordDuration(command);
                AppLog.d(TAG, "Получена команда: Записькоманда，时长: " + durationSeconds + "  сек.");

                // ОтправкаПодтвердить消息
                String confirmMsg = String.format("Получена команда записи, начинаю запись %d  сек. видео...", durationSeconds);
                sendResponseAndThen(chatId, confirmMsg, () -> {
                    // использование WakeUpHelper 唤醒并Начать запись
                    AppLog.d(TAG, "использование WakeUpHelper Начать запись...");
                    WakeUpHelper.launchForRecordingTelegram(context, chatId, durationSeconds);
                });

            } else if ("/photo".equals(command) || "Фото".equals(command) ||
                       "photo".equalsIgnoreCase(command)) {

                AppLog.d(TAG, "Получена команда: Фотокоманда");

                // ОтправкаПодтвердить消息
                sendResponseAndThen(chatId, "Получена команда фото, делаю снимок...", () -> {
                    // использование WakeUpHelper 唤醒并ЗапускФото
                    AppLog.d(TAG, "использование WakeUpHelper ЗапускФото...");
                    WakeUpHelper.launchForPhotoTelegram(context, chatId);
                });

            } else if ("/status".equals(command) || "Статус".equals(command)) {
                // Статускоманда：显示Приложение详细Статус
                AppLog.d(TAG, "Получена команда: Статускоманда");
                String statusInfo = currentCommandCallback != null ? 
                        currentCommandCallback.getStatusInfo() : "✅ Bot работает";
                apiClient.sendMessage(chatId, statusInfo);

            } else if ("Начать запись".equals(command) || "Начать запись".equals(command) || 
                       "/start_rec".equals(command) || "start".equalsIgnoreCase(command)) {
                // Начать записькоманда：唤醒 до Передний план并Начать непрерывную запись
                AppLog.d(TAG, "Получена команда: Начать записькоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onStartRecordingCommand();
                    apiClient.sendMessage(chatId, result);
                } else {
                    apiClient.sendMessage(chatId, "❌ Функция недоступна");
                }

            } else if ("Остановить запись".equals(command) || "Остановить запись".equals(command) || 
                       "/stop_rec".equals(command) || "stop".equalsIgnoreCase(command)) {
                // Остановить записькоманда：Остановить запись并退 до Фоновый режим
                AppLog.d(TAG, "Получена команда: Остановить записькоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onStopRecordingCommand();
                    apiClient.sendMessage(chatId, result);
                } else {
                    apiClient.sendMessage(chatId, "❌ Функция недоступна");
                }

            } else if ("Выход".equals(command) || "/exit".equals(command) || 
                       "exit".equalsIgnoreCase(command)) {
                // Выходкоманда：необходимо二 разПодтвердить
                AppLog.d(TAG, "Получена команда: Выходкоманда（需二 разПодтвердить)");
                apiClient.sendMessage(chatId, 
                    "⚠️ Подтвердите выход из EVCam?\n\n" +
                    "После выхода все записи и удалённые сервисы будут остановлены。\n" +
                    "Отправка「Подтвердить выход」или /confirm_exit Выполняется выход。");

            } else if ("Подтвердить выход".equals(command) || "/confirm_exit".equals(command)) {
                // Подтвердить выходкоманда：выполнениеВыход
                AppLog.d(TAG, "Получена команда: Подтвердить выходкоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onExitCommand(true);
                    apiClient.sendMessage(chatId, result);
                } else {
                    apiClient.sendMessage(chatId, "❌ Функция недоступна");
                }

            } else if ("Передний план".equals(command) || "/foreground".equals(command) ||
                       "foreground".equalsIgnoreCase(command)) {
                // Передний планкоманда：将Приложение переключено на передний план
                AppLog.d(TAG, "Получена команда: Передний планкоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onForegroundCommand();
                    apiClient.sendMessage(chatId, result);
                } else {
                    apiClient.sendMessage(chatId, "❌ Функция недоступна");
                }

            } else if ("Фоновый режим".equals(command) || "/background".equals(command) ||
                       "background".equalsIgnoreCase(command)) {
                // Фоновый режимкоманда：将Приложениепереключиться в фоновый режим
                AppLog.d(TAG, "Получена команда: Фоновый режимкоманда");
                if (currentCommandCallback != null) {
                    String result = currentCommandCallback.onBackgroundCommand();
                    apiClient.sendMessage(chatId, result);
                } else {
                    apiClient.sendMessage(chatId, "❌ Функция недоступна");
                }

            } else if ("/help".equals(command) || "Помощь".equals(command) ||
                       "/start".equals(command)) {

                apiClient.sendMessage(chatId,
                    "📋 <b>EVCam Удалённое управление</b>\n" +
                    "━━━━━━━━━━━━━━\n\n" +
                    "📹 <b>Удалённая запись</b>\n" +
                    "/record ─ Запись60 сек. видео\n" +
                    "/record 30 ─ запись указанного кол-ва секунд\n" +
                    "Запись / Запись30 ─ команда\n\n" +
                    "▶️ <b>Непрерывная запись</b>\n" +
                    "/start_rec ─ Начать непрерывную запись\n" +
                    "/stop_rec ─ Остановить запись\n" +
                    "Начать запись / Остановить запись ─ RU команда\n\n" +
                    "📷 <b>Фото</b>\n" +
                    "/photo ─ Сделать фото\n" +
                    "Фото ─ команда\n\n" +
                    "🔄 <b>Переключение переднего/фонового режима</b>\n" +
                    "/foreground ─ переключиться на передний план\n" +
                    "/background ─ переключиться в фоновый режим\n" +
                    "Передний план / Фоновый режим ─ команда\n\n" +
                    "ℹ️ <b>Другое</b>\n" +
                    "/status ─ ПросмотрПриложениеСтатус\n" +
                    "/exit ─ Выход из приложения\n" +
                    "/help ─ показать Помощь\n\n" +
                    "━━━━━━━━━━━━━━\n" +
                    "💡 Все команды доступны на русском и английском");

            } else {
                AppLog.d(TAG, "Неизвестная команда: " + command);
                apiClient.sendMessage(chatId,
                    "Неизвестная команда。Отправка /help ПросмотрДоступные команды。");
            }

        } catch (Exception e) {
            AppLog.e(TAG, "处理сообщения — ошибка", e);
        }
    }

    /**
     * 解析команда文本
     * 移除 @ 机器人名称部分
     */
    private String parseCommand(String text) {
        if (text == null) {
            return "";
        }

        // 移除 @botname 部分
        String command = text.replaceAll("@\\S+", "").trim();
        return command;
    }

    /**
     * 解析Запись时长（ сек.)
     * поддержка格式：/record、/record 30、Запись、Запись30、Запись 30
     */
    private int parseRecordDuration(String command) {
        if (command == null || command.isEmpty()) {
            return 60;
        }

        // 移除командаВыкл键字，提取数字
        String durationStr = command
                .replaceAll("(?i)(/record|Запись|record)", "")
                .trim();

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
     * Отправка响应消息，并 Отправказавершение后выполнение回调
     */
    private void sendResponseAndThen(long chatId, String message, Runnable callback) {
        new Thread(() -> {
            try {
                apiClient.sendMessage(chatId, message);
                AppLog.d(TAG, "响应消息Отправка: " + message);

                if (callback != null) {
                    callback.run();
                }
            } catch (Exception e) {
                AppLog.e(TAG, "Отправка响应сообщения — ошибка", e);
                // т.е.使Ошибка отправки，такжевыполнение回调
                if (callback != null) {
                    callback.run();
                }
            }
        }).start();
    }

    /**
     * Остановка消息轮询
     */
    public void stop() {
        AppLog.d(TAG, "Выполняется Остановка Bot...");
        shouldStop = true;
        isRunning = false;

        if (pollingThread != null) {
            pollingThread.interrupt();
            
            // ожидание轮询线程完全завершить，最多ожидание 35  сек.（比 POLL_TIMEOUT 稍长)
            // 这样可以避免перезагрузка时新旧Подключение冲突导致 409 Ошибка
            try {
                pollingThread.join(35000);
                if (pollingThread.isAlive()) {
                    AppLog.w(TAG, "轮询线程Не 能 таймаут内завершить");
                } else {
                    AppLog.d(TAG, "轮询线程完全Остановка");
                }
            } catch (InterruptedException e) {
                AppLog.w(TAG, "ожидание轮询线程Остановка时 断");
                Thread.currentThread().interrupt();
            }
            pollingThread = null;
        }

        AppLog.d(TAG, "Bot Остановлено");
    }

    /**
     * проверка 否Выполняется Работа
     */
    public boolean isRunning() {
        return isRunning;
    }
}
