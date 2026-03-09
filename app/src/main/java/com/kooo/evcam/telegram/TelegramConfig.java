package com.kooo.evcam.telegram;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Telegram 机器人конфигурацияХранилищеинструмент类
 */
public class TelegramConfig {
    private static final String PREF_NAME = "telegram_config";
    private static final String KEY_BOT_TOKEN = "bot_token";
    private static final String KEY_ALLOWED_CHAT_IDS = "allowed_chat_ids";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_LAST_UPDATE_ID = "last_update_id";
    private static final String KEY_BOT_API_HOST = "bot_api_host";
    private static final String DEFAULT_API_HOST = "https://api.telegram.org";

    private final SharedPreferences prefs;

    public TelegramConfig(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Сохранитьконфигурация
     * @param botToken Bot Token ( от  @BotFather Получение)
     */
    public void saveConfig(String botToken) {
        prefs.edit()
                .putString(KEY_BOT_TOKEN, botToken)
                .apply();
    }

    /**
     * Сохранитьконфигурация（содержитразрешить  Chat ID 列表)
     * @param botToken Bot Token
     * @param allowedChatIds разрешить  Chat ID 列表（逗号分隔)
     */
    public void saveConfig(String botToken, String allowedChatIds) {
        prefs.edit()
                .putString(KEY_BOT_TOKEN, botToken)
                .putString(KEY_ALLOWED_CHAT_IDS, allowedChatIds)
                .apply();
    }

    public String getBotToken() {
        return prefs.getString(KEY_BOT_TOKEN, "");
    }

    /**
     * Получениеразрешить  Chat ID 列表
     * @return 逗号分隔  Chat ID 字符串
     */
    public String getAllowedChatIds() {
        return prefs.getString(KEY_ALLOWED_CHAT_IDS, "");
    }

    /**
     * Сохранить自定义 Bot API 地址（反 к 代理地址)
     * @param apiHost 自定义 API 地址，если https://a.tgpush.com，пусто则использование官方地址
     */
    public void saveBotApiHost(String apiHost) {
        prefs.edit()
                .putString(KEY_BOT_API_HOST, apiHost)
                .apply();
    }

    /**
     * Получение Bot API 地址
     * @return 自定义反 к 代理地址，若Не конфигурация则返回官方地址 https://api.telegram.org
     */
    public String getBotApiHost() {
        String customHost = prefs.getString(KEY_BOT_API_HOST, "");
        if (customHost == null || customHost.trim().isEmpty()) {
            return DEFAULT_API_HOST;
        }
        // 去除末尾 斜杠，保持一致性
        return customHost.endsWith("/") ? customHost.substring(0, customHost.length() - 1) : customHost;
    }

    /**
     * Получение原始конфигурация  API Host（不содержитПо умолчанию值)
     * @return 用户конфигурация 自定义 API 地址，Не конфигурация返回空字符串
     */
    public String getRawBotApiHost() {
        return prefs.getString(KEY_BOT_API_HOST, "");
    }

    /**
     * 验证 API Host 格式 否正确
     * 必须以 http:// или https:// Вкл头
     * @param apiHost 待验证 地址
     * @return  否действует
     */
    public static boolean isValidApiHost(String apiHost) {
        if (apiHost == null || apiHost.trim().isEmpty()) {
            return true; // 空值 разрешить ，会использованиеПо умолчанию地址
        }
        String trimmed = apiHost.trim().toLowerCase();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    /**
     * проверка Chat ID  否 разрешить
     * Если Не конфигурация任何 Chat ID，则разрешить所有
     */
    public boolean isChatIdAllowed(long chatId) {
        String allowedIds = getAllowedChatIds();
        if (allowedIds.isEmpty()) {
            return true; // Не конфигурация时разрешить所有
        }

        String[] ids = allowedIds.split(",");
        for (String id : ids) {
            try {
                if (Long.parseLong(id.trim()) == chatId) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    public boolean isConfigured() {
        return !getBotToken().isEmpty();
    }

    public void setAutoStart(boolean autoStart) {
        prefs.edit()
                .putBoolean(KEY_AUTO_START, autoStart)
                .apply();
    }

    public boolean isAutoStart() {
        return prefs.getBoolean(KEY_AUTO_START, false);
    }

    /**
     * Сохранить最后处理  update_id
     * 用于 Long Polling 时跳过处理 消息
     */
    public void saveLastUpdateId(long updateId) {
        prefs.edit()
                .putLong(KEY_LAST_UPDATE_ID, updateId)
                .apply();
    }

    public long getLastUpdateId() {
        return prefs.getLong(KEY_LAST_UPDATE_ID, 0);
    }

    public void clearConfig() {
        prefs.edit().clear().apply();
    }
}
