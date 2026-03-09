package com.kooo.evcam.feishu;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * FeishuконфигурацияХранилищеинструмент类
 */
public class FeishuConfig {
    private static final String PREF_NAME = "feishu_config";
    private static final String KEY_APP_ID = "app_id";
    private static final String KEY_APP_SECRET = "app_secret";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_TOKEN_EXPIRE_TIME = "token_expire_time";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_ALLOWED_USER_IDS = "allowed_user_ids";

    private final SharedPreferences prefs;

    public FeishuConfig(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Сохранитьконфигурация
     * @param appId Приложение App ID
     * @param appSecret Приложение App Secret
     */
    public void saveConfig(String appId, String appSecret) {
        prefs.edit()
                .putString(KEY_APP_ID, appId)
                .putString(KEY_APP_SECRET, appSecret)
                .apply();
    }

    /**
     * Сохранитьконфигурация（содержитразрешить 用户ID)
     */
    public void saveConfig(String appId, String appSecret, String allowedUserIds) {
        prefs.edit()
                .putString(KEY_APP_ID, appId)
                .putString(KEY_APP_SECRET, appSecret)
                .putString(KEY_ALLOWED_USER_IDS, allowedUserIds)
                .apply();
    }

    public String getAppId() {
        return prefs.getString(KEY_APP_ID, "");
    }

    public String getAppSecret() {
        return prefs.getString(KEY_APP_SECRET, "");
    }

    /**
     * Получениеразрешить 用户ID列表
     * @return 逗号分隔 用户ID字符串
     */
    public String getAllowedUserIds() {
        return prefs.getString(KEY_ALLOWED_USER_IDS, "");
    }

    /**
     * проверка用户ID 否 разрешить
     * Если Не конфигурация任何用户ID，则разрешить所有
     */
    public boolean isUserIdAllowed(String userId) {
        String allowedIds = getAllowedUserIds();
        if (allowedIds.isEmpty()) {
            return true; // Не конфигурация时разрешить所有
        }

        String[] ids = allowedIds.split(",");
        for (String id : ids) {
            if (id.trim().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    public boolean isConfigured() {
        return !getAppId().isEmpty() && !getAppSecret().isEmpty();
    }

    /**
     * Сохранить Access Token
     */
    public void saveAccessToken(String token, long expireTime) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, token)
                .putLong(KEY_TOKEN_EXPIRE_TIME, expireTime)
                .apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, "");
    }

    public boolean isTokenValid() {
        long expireTime = prefs.getLong(KEY_TOKEN_EXPIRE_TIME, 0);
        return System.currentTimeMillis() < expireTime;
    }

    /**
     * очистка缓存  AccessToken
     */
    public void clearAccessToken() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_TOKEN_EXPIRE_TIME)
                .apply();
    }

    public void setAutoStart(boolean autoStart) {
        prefs.edit()
                .putBoolean(KEY_AUTO_START, autoStart)
                .apply();
    }

    public boolean isAutoStart() {
        return prefs.getBoolean(KEY_AUTO_START, false);
    }

    public void clearConfig() {
        prefs.edit().clear().apply();
    }
}
