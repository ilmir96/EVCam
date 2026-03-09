package com.kooo.evcam.feishu;

import com.kooo.evcam.AppLog;

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
 * Feishu API 客户端
 * 负责 и FeishuСервис器进行 HTTP 通信
 */
public class FeishuApiClient {
    private static final String TAG = "FeishuApiClient";
    private static final String BASE_URL = "https://open.feishu.cn/open-apis";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final FeishuConfig config;

    public FeishuApiClient(FeishuConfig config) {
        this.config = config;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS) // 传大Файлнеобходимо更长时间
                .build();
    }

    /**
     * Получение Tenant Access Token
     */
    public String getTenantAccessToken() throws IOException {
        // проверка缓存  token  否действует
        if (config.isTokenValid()) {
            String cachedToken = config.getAccessToken();
            AppLog.d(TAG, "использование缓存  Access Token");
            return cachedToken;
        }

        // Получение新  token
        String url = BASE_URL + "/auth/v3/tenant_access_token/internal";

        JsonObject body = new JsonObject();
        body.addProperty("app_id", config.getAppId());
        body.addProperty("app_secret", config.getAppSecret());

        AppLog.d(TAG, "Выполняется Получение新  Access Token...");

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        gson.toJson(body)
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            AppLog.d(TAG, "Access Token 响应: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Ошибка получения Access Token: " + response.code() + " - " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            // проверкаОшибка码
            int code = jsonResponse.has("code") ? jsonResponse.get("code").getAsInt() : -1;
            if (code != 0) {
                String msg = jsonResponse.has("msg") ? jsonResponse.get("msg").getAsString() : "Unknown error";
                throw new IOException("Ошибка получения Access Token: code=" + code + ", msg=" + msg);
            }

            if (jsonResponse.has("tenant_access_token")) {
                String accessToken = jsonResponse.get("tenant_access_token").getAsString();
                int expire = jsonResponse.get("expire").getAsInt();

                // 提前 5  мин.истекло
                long expireTime = System.currentTimeMillis() + (expire - 300) * 1000L;
                config.saveAccessToken(accessToken, expireTime);

                AppLog.d(TAG, "Access Token ПолучениеУспешно");
                return accessToken;
            } else {
                throw new IOException("В ответе отсутствует tenant_access_token: " + responseBody);
            }
        }
    }

    /**
     * Получение WebSocket ПодключениеИнформация（用于长Подключение接收消息)
     * 注意：необходимо FeishuВкл发者Фоновый режимВкл启"长Подключение"режим
     * 
     * 根据Feishu官方 SDK 实现，此接口необходимо直接传递 AppID  и  AppSecret，
     * 而不 использование Bearer Token 认证。
     */
    public WebSocketConnection getWebSocketConnection() throws IOException {
        // 注意：WebSocket endpoint 不использование /open-apis 前缀
        // 正确  URL   https://open.feishu.cn/callback/ws/endpoint
        String url = "https://open.feishu.cn/callback/ws/endpoint";

        // Feishu官方 SDK использование 求格式：直接传递 AppID  и  AppSecret
        JsonObject body = new JsonObject();
        body.addProperty("AppID", config.getAppId());
        body.addProperty("AppSecret", config.getAppSecret());

        AppLog.d(TAG, "Выполняется Получение WebSocket Подключение地址...");

        Request request = new Request.Builder()
                .url(url)
                .header("locale", "zh")
                .post(RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        gson.toJson(body)
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            AppLog.d(TAG, "WebSocket ПодключениеИнформация响应: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("Получение WebSocket Ошибка подключения: " + response.code() + " - " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            int code = jsonResponse.has("code") ? jsonResponse.get("code").getAsInt() : -1;
            if (code != 0) {
                String msg = jsonResponse.has("msg") ? jsonResponse.get("msg").getAsString() : "Unknown error";
                throw new IOException("Получение WebSocket Ошибка подключения: code=" + code + ", msg=" + msg);
            }

            JsonObject data = jsonResponse.getAsJsonObject("data");
            // 注意：Feishu返回 字名 大写 "URL"
            String wsUrl = data.get("URL").getAsString();

            AppLog.d(TAG, "WebSocket URL ПолучениеУспешно: " + wsUrl);
            return new WebSocketConnection(wsUrl);
        }
    }

    /**
     * Отправка文本消息
     * @param receiveIdType 接收者类型：open_id, user_id, union_id, email, chat_id
     * @param receiveId 接收者ID
     * @param text 消息内容
     */
    public void sendTextMessage(String receiveIdType, String receiveId, String text) throws IOException {
        String accessToken = getTenantAccessToken();
        String url = BASE_URL + "/im/v1/messages?receive_id_type=" + receiveIdType;

        // 构建消息内容
        JsonObject content = new JsonObject();
        content.addProperty("text", text);

        JsonObject body = new JsonObject();
        body.addProperty("receive_id", receiveId);
        body.addProperty("msg_type", "text");
        body.addProperty("content", gson.toJson(content));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправка文本消息: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки сообщения: " + responseBody);
                throw new IOException("Ошибка отправки сообщения: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "消息ОтправкаУспешно: " + responseBody);
        }
    }

    /**
     * 回复消息
     * @param messageId 原消息ID
     * @param text 回复内容
     */
    public void replyMessage(String messageId, String text) throws IOException {
        String accessToken = getTenantAccessToken();
        String url = BASE_URL + "/im/v1/messages/" + messageId + "/reply";

        // 构建消息内容
        JsonObject content = new JsonObject();
        content.addProperty("text", text);

        JsonObject body = new JsonObject();
        body.addProperty("msg_type", "text");
        body.addProperty("content", gson.toJson(content));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "回复消息: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка ответа: " + responseBody);
                throw new IOException("Ошибка ответа: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "回复消息Успешно: " + responseBody);
        }
    }

    /**
     * 传Изображение
     * @param imageFile ИзображениеФайл
     * @return image_key
     */
    public String uploadImage(File imageFile) throws IOException {
        String accessToken = getTenantAccessToken();
        String url = BASE_URL + "/im/v1/images";

        RequestBody fileBody = RequestBody.create(
                MediaType.parse("image/jpeg"),
                imageFile
        );

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image_type", "message")
                .addFormDataPart("image", imageFile.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка загрузки изображения: " + responseBody);
                throw new IOException("Ошибка загрузки изображения: " + response.code() + ", " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonObject data = jsonResponse.getAsJsonObject("data");
            String imageKey = data.get("image_key").getAsString();

            AppLog.d(TAG, "Изображение传Успешно: " + imageKey);
            return imageKey;
        }
    }

    /**
     * ОтправкаИзображение消息
     */
    public void sendImageMessage(String receiveIdType, String receiveId, String imageKey) throws IOException {
        String accessToken = getTenantAccessToken();
        String url = BASE_URL + "/im/v1/messages?receive_id_type=" + receiveIdType;

        // 构建消息内容
        JsonObject content = new JsonObject();
        content.addProperty("image_key", imageKey);

        JsonObject body = new JsonObject();
        body.addProperty("receive_id", receiveId);
        body.addProperty("msg_type", "image");
        body.addProperty("content", gson.toJson(content));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "ОтправкаИзображение消息: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "ОтправкаИзображениесообщения — ошибка: " + responseBody);
                throw new IOException("ОтправкаИзображениесообщения — ошибка: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "Изображение消息ОтправкаУспешно: " + responseBody);
        }
    }

    /**
     * 传Файл（不带时长参数)
     * @param file Файл
     * @param fileType Файл类型：opus, mp4, pdf, doc, xls, ppt, stream
     * @return file_key
     */
    public String uploadFile(File file, String fileType) throws IOException {
        return uploadFile(file, fileType, -1);
    }

    /**
     * 传Файл（带时长参数，用于Видео/音频)
     * @param file Файл
     * @param fileType Файл类型：opus, mp4, pdf, doc, xls, ppt, stream
     * @param durationMs Файл时长（毫 сек.)，-1 表示不传递
     * @return file_key
     */
    public String uploadFile(File file, String fileType, int durationMs) throws IOException {
        String accessToken = getTenantAccessToken();
        String url = BASE_URL + "/im/v1/files";

        RequestBody fileBody = RequestBody.create(
                MediaType.parse("application/octet-stream"),
                file
        );

        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file_type", fileType)
                .addFormDataPart("file_name", file.getName())
                .addFormDataPart("file", file.getName(), fileBody);

        // Если 有时长参数，添加 до 求（Видео/音频Файлнеобходимо此参数才能显示时长)
        if (durationMs > 0) {
            bodyBuilder.addFormDataPart("duration", String.valueOf(durationMs));
            AppLog.d(TAG, "传Файл带时长参数: " + durationMs + "ms");
        }

        MultipartBody requestBody = bodyBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка загрузки файла: " + responseBody);
                throw new IOException("Ошибка загрузки файла: " + response.code() + ", " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonObject data = jsonResponse.getAsJsonObject("data");
            String fileKey = data.get("file_key").getAsString();

            AppLog.d(TAG, "Файл传Успешно: " + fileKey);
            return fileKey;
        }
    }

    /**
     * ОтправкаФайл消息（用于普通Файлесли pdf, doc 等)
     */
    public void sendFileMessage(String receiveIdType, String receiveId, String fileKey) throws IOException {
        String accessToken = getTenantAccessToken();
        String url = BASE_URL + "/im/v1/messages?receive_id_type=" + receiveIdType;

        // 构建消息内容
        JsonObject content = new JsonObject();
        content.addProperty("file_key", fileKey);

        JsonObject body = new JsonObject();
        body.addProperty("receive_id", receiveId);
        body.addProperty("msg_type", "file");
        body.addProperty("content", gson.toJson(content));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "ОтправкаФайл消息: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "ОтправкаФайлсообщения — ошибка: " + responseBody);
                throw new IOException("ОтправкаФайлсообщения — ошибка: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "Файл消息ОтправкаУспешно: " + responseBody);
        }
    }

    /**
     * ОтправкаВидео消息（用于 mp4 等ВидеоФайл)
     * @param receiveIdType 接收者类型
     * @param receiveId 接收者 ID
     * @param fileKey ВидеоФайл  file_key
     * @param imageKey Видео封面Изображение  image_key（可选，传 null 则不显示封面)
     */
    public void sendVideoMessage(String receiveIdType, String receiveId, String fileKey, String imageKey) throws IOException {
        String accessToken = getTenantAccessToken();
        String url = BASE_URL + "/im/v1/messages?receive_id_type=" + receiveIdType;

        // 构建消息内容
        JsonObject content = new JsonObject();
        content.addProperty("file_key", fileKey);
        if (imageKey != null && !imageKey.isEmpty()) {
            content.addProperty("image_key", imageKey);
        }

        JsonObject body = new JsonObject();
        body.addProperty("receive_id", receiveId);
        body.addProperty("msg_type", "media");
        body.addProperty("content", gson.toJson(content));

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "ОтправкаВидео消息: " + requestJson);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "ОтправкаВидеосообщения — ошибка: " + responseBody);
                throw new IOException("ОтправкаВидеосообщения — ошибка: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "Видео消息ОтправкаУспешно: " + responseBody);
        }
    }

    /**
     * WebSocket ПодключениеИнформация
     */
    public static class WebSocketConnection {
        public final String url;

        public WebSocketConnection(String url) {
            this.url = url;
        }
    }
}
