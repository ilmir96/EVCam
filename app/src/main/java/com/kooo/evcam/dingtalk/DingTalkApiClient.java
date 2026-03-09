package com.kooo.evcam.dingtalk;


import com.kooo.evcam.AppLog;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * DingTalk API 客户端
 * 负责 и УдалённыйПросмотрСервис器进行 HTTP 通信
 */
public class DingTalkApiClient {
    private static final String TAG = "DingTalkApiClient";
    private static final String BASE_URL = "https://api.dingtalk.com";
    private static final String OAPI_URL = "https://oapi.dingtalk.com";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final DingTalkConfig config;

    public DingTalkApiClient(DingTalkConfig config) {
        this.config = config;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Получение Access Token (использование旧版 API)
     */
    public String getAccessToken() throws IOException {
        // проверка缓存  token  否действует
        if (config.isTokenValid()) {
            String cachedToken = config.getAccessToken();
            AppLog.d(TAG, "использование缓存  Access Token");
            return cachedToken;
        }

        // Получение新  token - использование旧版 API
        String url = OAPI_URL + "/gettoken?appkey=" + config.getClientId() +
                     "&appsecret=" + config.getClientSecret();

        AppLog.d(TAG, "Выполняется Получение新  Access Token...");

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            AppLog.d(TAG, "Access Token 响应: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Ошибка получения Access Token: " + response.code() + " - " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            // проверкаОшибка码
            if (jsonResponse.has("errcode")) {
                int errcode = jsonResponse.get("errcode").getAsInt();
                if (errcode != 0) {
                    String errmsg = jsonResponse.has("errmsg") ? jsonResponse.get("errmsg").getAsString() : "Unknown error";
                    throw new IOException("Ошибка получения Access Token: errcode=" + errcode + ", errmsg=" + errmsg);
                }
            }

            if (jsonResponse.has("access_token")) {
                String accessToken = jsonResponse.get("access_token").getAsString();
                long expireIn = jsonResponse.get("expires_in").getAsLong();

                // 提前 5  мин.истекло
                long expireTime = System.currentTimeMillis() + (expireIn - 300) * 1000;
                config.saveAccessToken(accessToken, expireTime);

                AppLog.d(TAG, "Access Token ПолучениеУспешно");
                return accessToken;
            } else {
                throw new IOException("В ответе отсутствует access_token: " + responseBody);
            }
        }
    }

    /**
     * 通过 sessionWebhook Отправка文本消息（рекомендуется方式)
     */
    public void sendMessageViaWebhook(String webhookUrl, String text) throws IOException {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            throw new IOException("Webhook URL пусто");
        }

        // 构建消息体 - 按照自定义机器人 格式
        JsonObject textObj = new JsonObject();
        textObj.addProperty("content", text);

        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "text");
        body.add("text", textObj);

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "通过 Webhook Отправка消息: " + requestJson);

        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Webhook Ошибка отправки сообщения，响应: " + responseBody);
                throw new IOException("Webhook Ошибка отправки сообщения: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "Webhook 消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * 判断 否为групповой чат会话
     * DingTalk  conversationType 值：
     * - "1": личный чат
     * - "2": групповой чат
     */
    private boolean isGroupConversation(String conversationType) {
        return "2".equals(conversationType);
    }

    /**
     * Отправка文本消息（автоматически判断групповой чатилиличный чат)
     * групповой чатиспользование Webhook，личный чатиспользование API
     */
    public void sendTextMessage(String conversationId, String conversationType, String text) throws IOException {
        sendTextMessage(conversationId, conversationType, text, null);
    }

    /**
     * Отправка文本消息 до групповой чатилиличный чат
     * @param conversationId 会话ID
     * @param conversationType 会话类型（"1"=личный чат，"2"=групповой чат)
     * @param text 消息内容
     * @param userId 用户ID（личный чат时必需)
     */
    public void sendTextMessage(String conversationId, String conversationType, String text, String userId) throws IOException {
        if (isGroupConversation(conversationType)) {
            // групповой чат：использование Webhook 方式
            String webhookUrl = config.getWebhookUrl();
            if (webhookUrl != null && !webhookUrl.isEmpty()) {
                sendMessageViaWebhook(webhookUrl, text);
                return;
            }
            // Если 没有 Webhook，использованиегрупповой чат API
            sendTextMessageToGroup(conversationId, text);
        } else {
            // личный чат：использованиеличный чат API
            if (userId == null || userId.isEmpty()) {
                throw new IOException("Для личного сообщения нужен userId");
            }
            sendTextMessageToUser(userId, text);
        }
    }

    /**
     * Отправка文本消息 до групповой чат（использование API 方式)
     */
    private void sendTextMessageToGroup(String conversationId, String text) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/groupMessages/send";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("content", text);

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.addProperty("openConversationId", conversationId);
        body.addProperty("msgKey", "sampleText");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправкагрупповой чат文本消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки текста в групповой чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки текста в групповой чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "групповой чат文本消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * Отправка文本消息 до личный чат
     */
    private void sendTextMessageToUser(String userId, String text) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/oToMessages/batchSend";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("content", text);

        // 构建 userIds 数 групп
        com.google.gson.JsonArray userIds = new com.google.gson.JsonArray();
        userIds.add(userId);

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.add("userIds", userIds);
        body.addProperty("msgKey", "sampleText");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправка в личный чат文本消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки текста в личный чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки текста в личный чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "личный чат文本消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * 传Файл до DingTalk
     */
    public String uploadFile(File file) throws IOException {
        return uploadMedia(file, "file");
    }

    /**
     * 传Изображение до DingTalk
     */
    public String uploadImage(File imageFile) throws IOException {
        return uploadMedia(imageFile, "image");
    }

    /**
     * 传媒体Файл до DingTalk
     * @param file Файл
     * @param type 类型：file, image, voice, video
     */
    private String uploadMedia(File file, String type) throws IOException {
        String accessToken = getAccessToken();
        String url = OAPI_URL + "/media/upload?access_token=" + accessToken + "&type=" + type;

        RequestBody fileBody = RequestBody.create(
                MediaType.parse("application/octet-stream"),
                file
        );

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("media", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ошибка загрузки медиафайла: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            if (jsonResponse.has("media_id")) {
                String mediaId = jsonResponse.get("media_id").getAsString();
                AppLog.d(TAG, type + " 传Успешно，media_id: " + mediaId);
                return mediaId;
            } else {
                throw new IOException("В ответе отсутствует media_id: " + responseBody);
            }
        }
    }

    /**
     * ОтправкаФайл消息（автоматически判断групповой чатилиличный чат)
     * @param conversationId 会话ID
     * @param conversationType 会话类型（"1"=личный чат，"2"=групповой чат)
     * @param mediaId 媒体ФайлID
     * @param fileName Файл名
     * @param userId 用户ID（личный чат时必需)
     */
    public void sendFileMessage(String conversationId, String conversationType, String mediaId, String fileName, String userId) throws IOException {
        if (isGroupConversation(conversationType)) {
            // групповой чат：использованиегрупповой чат API
            sendFileMessageToGroup(conversationId, mediaId, fileName);
        } else {
            // личный чат：использованиеличный чат API
            if (userId == null || userId.isEmpty()) {
                throw new IOException("Для отправки файла в личный чат нужен userId");
            }
            sendFileMessageToUser(userId, mediaId, fileName);
        }
    }

    /**
     * ОтправкаФайл消息 до групповой чат
     * использованиегрупповой чат消息 API (orgGroupSend)
     */
    private void sendFileMessageToGroup(String conversationId, String mediaId, String fileName) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/groupMessages/send";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("mediaId", mediaId);
        msgParam.addProperty("fileName", fileName);

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.addProperty("openConversationId", conversationId);
        body.addProperty("msgKey", "sampleFile");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправкагрупповой чатФайл消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки файла в групповой чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки файла в групповой чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "групповой чатФайл消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * ОтправкаФайл消息 до личный чат
     * использованиеличный чат消息 API (batchSendOTO)
     */
    public void sendFileMessageToUser(String userId, String mediaId, String fileName) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/oToMessages/batchSend";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("mediaId", mediaId);
        msgParam.addProperty("fileName", fileName);

        // 构建 userIds 数 групп
        com.google.gson.JsonArray userIds = new com.google.gson.JsonArray();
        if (userId != null && !userId.isEmpty()) {
            userIds.add(userId);
        } else {
            throw new IOException("Для отправки файла в личный чат нужен userId");
        }

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.add("userIds", userIds);
        body.addProperty("msgKey", "sampleFile");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправка в личный чатФайл消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки файла в личный чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки файла в личный чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "личный чатФайл消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * ОтправкаВидео消息（автоматически判断групповой чатилиличный чат)
     * @param conversationId 会话ID
     * @param conversationType 会话类型（"1"=личный чат，"2"=групповой чат)
     * @param videoMediaId Видео媒体ID
     * @param picMediaId 封面图媒体ID
     * @param duration Видео时长（ сек.)
     * @param userId 用户ID（личный чат时必需)
     */
    public void sendVideoMessage(String conversationId, String conversationType, String videoMediaId, String picMediaId,
                                  int duration, String userId) throws IOException {
        if (isGroupConversation(conversationType)) {
            // групповой чат：использованиегрупповой чат API
            sendVideoMessageToGroup(conversationId, videoMediaId, picMediaId, duration);
        } else {
            // личный чат：использованиеличный чат API
            if (userId == null || userId.isEmpty()) {
                throw new IOException("Для отправки видео в ЛС нужен userId");
            }
            sendVideoMessageToUser(userId, videoMediaId, picMediaId, duration);
        }
    }

    /**
     * ОтправкаВидео消息 до групповой чат
     */
    private void sendVideoMessageToGroup(String conversationId, String videoMediaId,
                                          String picMediaId, int duration) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/groupMessages/send";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("videoMediaId", videoMediaId);
        msgParam.addProperty("picMediaId", picMediaId);
        msgParam.addProperty("videoType", "mp4");
        msgParam.addProperty("duration", String.valueOf(duration));
        msgParam.addProperty("height", "200");  // Видео显示Высокий度

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.addProperty("openConversationId", conversationId);
        body.addProperty("msgKey", "sampleVideo");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправкагрупповой чатВидео消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки видео в групповой чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки видео в групповой чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "групповой чатВидео消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * ОтправкаВидео消息 до личный чат
     */
    private void sendVideoMessageToUser(String userId, String videoMediaId,
                                         String picMediaId, int duration) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/oToMessages/batchSend";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("videoMediaId", videoMediaId);
        msgParam.addProperty("picMediaId", picMediaId);
        msgParam.addProperty("videoType", "mp4");
        msgParam.addProperty("duration", String.valueOf(duration));
        msgParam.addProperty("height", "200");

        // 构建 userIds 数 групп
        com.google.gson.JsonArray userIds = new com.google.gson.JsonArray();
        userIds.add(userId);

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.add("userIds", userIds);
        body.addProperty("msgKey", "sampleVideo");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправка в личный чатВидео消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки видео в личный чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки видео в личный чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "личный чатВидео消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * ОтправкаИзображение消息（автоматически判断групповой чатилиличный чат)
     * @param conversationId 会话ID
     * @param conversationType 会话类型（"1"=личный чат，"2"=групповой чат)
     * @param photoURL ИзображениеURL（DingTalk传后 URL)
     * @param userId 用户ID（личный чат时必需)
     */
    public void sendImageMessage(String conversationId, String conversationType, String photoURL, String userId) throws IOException {
        if (isGroupConversation(conversationType)) {
            // групповой чат：использованиегрупповой чат API
            sendImageMessageToGroup(conversationId, photoURL);
        } else {
            // личный чат：использованиеличный чат API
            if (userId == null || userId.isEmpty()) {
                throw new IOException("Для отправки фото в ЛС нужен userId");
            }
            sendImageMessageToUser(userId, photoURL);
        }
    }

    /**
     * ОтправкаИзображение消息 до групповой чат
     */
    private void sendImageMessageToGroup(String conversationId, String photoURL) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/groupMessages/send";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("photoURL", photoURL);

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.addProperty("openConversationId", conversationId);
        body.addProperty("msgKey", "sampleImageMsg");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправкагрупповой чатИзображение消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки фото в групповой чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки фото в групповой чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "групповой чатИзображение消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * ОтправкаИзображение消息 до личный чат
     */
    private void sendImageMessageToUser(String userId, String photoURL) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/oToMessages/batchSend";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("photoURL", photoURL);

        // 构建 userIds 数 групп
        com.google.gson.JsonArray userIds = new com.google.gson.JsonArray();
        userIds.add(userId);

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.add("userIds", userIds);
        body.addProperty("msgKey", "sampleImageMsg");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправка в личный чатИзображение消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки фото в личный чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки фото в личный чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "личный чатИзображение消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * ОтправкаMarkdown消息（автоматически判断групповой чатилиличный чат)
     * @param conversationId 会话ID
     * @param conversationType 会话类型（"1"=личный чат，"2"=групповой чат)
     * @param title 标题
     * @param text Markdown文本
     * @param userId 用户ID（личный чат时必需)
     */
    public void sendMarkdownMessage(String conversationId, String conversationType, String title, String text, String userId) throws IOException {
        if (isGroupConversation(conversationType)) {
            // групповой чат：использованиегрупповой чат API
            sendMarkdownMessageToGroup(conversationId, title, text);
        } else {
            // личный чат：использованиеличный чат API
            if (userId == null || userId.isEmpty()) {
                throw new IOException("Для отправки Markdown в ЛС нужен userId");
            }
            sendMarkdownMessageToUser(userId, title, text);
        }
    }

    /**
     * ОтправкаMarkdown消息 до групповой чат
     */
    private void sendMarkdownMessageToGroup(String conversationId, String title, String text) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/groupMessages/send";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("title", title);
        msgParam.addProperty("text", text);

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.addProperty("openConversationId", conversationId);
        body.addProperty("msgKey", "sampleMarkdown");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправкагрупповой чатMarkdown消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки Markdown в групповой чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки Markdown в групповой чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "групповой чатMarkdown消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * ОтправкаMarkdown消息 до личный чат
     */
    private void sendMarkdownMessageToUser(String userId, String title, String text) throws IOException {
        String accessToken = getAccessToken();
        String url = BASE_URL + "/v1.0/robot/oToMessages/batchSend";

        // 构建消息参数
        JsonObject msgParam = new JsonObject();
        msgParam.addProperty("title", title);
        msgParam.addProperty("text", text);

        // 构建 userIds 数 групп
        com.google.gson.JsonArray userIds = new com.google.gson.JsonArray();
        userIds.add(userId);

        JsonObject body = new JsonObject();
        body.addProperty("robotCode", config.getClientId());
        body.add("userIds", userIds);
        body.addProperty("msgKey", "sampleMarkdown");
        body.addProperty("msgParam", gson.toJson(msgParam));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправка в личный чатMarkdown消息求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки Markdown в личный чат，响应: " + responseBody);
                throw new IOException("Ошибка отправки Markdown в личный чат: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "личный чатMarkdown消息ОтправкаУспешно，响应: " + responseBody);
        }
    }

    /**
     * Stream ПодключениеИнформация
     */
    public static class StreamConnection {
        public final String endpoint;
        public final String ticket;

        public StreamConnection(String endpoint, String ticket) {
            this.endpoint = endpoint;
            this.ticket = ticket;
        }
    }

    /**
     * Получение Stream ПодключениеИнформация
     */
    public StreamConnection getStreamConnection() throws IOException {
        String url = BASE_URL + "/v1.0/gateway/connections/open";

        // 构建 subscriptions 数 групп
        // 订阅机器人消息事件
        com.google.gson.JsonArray subscriptions = new com.google.gson.JsonArray();

        // 订阅所有事件（Если Вкл放平台конфигурация具体事件)
        JsonObject subscription1 = new JsonObject();
        subscription1.addProperty("type", "CALLBACK");
        subscription1.addProperty("topic", "/v1.0/im/bot/messages/get");
        subscriptions.add(subscription1);

        // также订阅通用回调
        JsonObject subscription2 = new JsonObject();
        subscription2.addProperty("type", "CALLBACK");
        subscription2.addProperty("topic", "*");
        subscriptions.add(subscription2);

        JsonObject body = new JsonObject();
        body.addProperty("clientId", config.getClientId());
        body.addProperty("clientSecret", config.getClientSecret());
        body.add("subscriptions", subscriptions);

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Stream 求: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            AppLog.d(TAG, "Stream 响应: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Ошибка получения Stream-соединения: " + response.code() + " - " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            if (jsonResponse.has("endpoint") && jsonResponse.has("ticket")) {
                String endpoint = jsonResponse.get("endpoint").getAsString();
                String ticket = jsonResponse.get("ticket").getAsString();
                AppLog.d(TAG, "Stream ПодключениеИнформацияПолучениеУспешно: " + endpoint);
                return new StreamConnection(endpoint, ticket);
            } else {
                throw new IOException("В ответе отсутствует endpoint или ticket: " + responseBody);
            }
        }
    }
}
