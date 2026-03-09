package com.kooo.evcam.telegram;

import com.kooo.evcam.AppLog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
 * Telegram Bot API 客户端
 * 负责 и  Telegram Сервис器进行 HTTP 通信
 */
public class TelegramApiClient {
    private static final String TAG = "TelegramApiClient";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final TelegramConfig config;

    public TelegramApiClient(TelegramConfig config) {
        this.config = config;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)  // Подключениетаймаут15 сек.
                .readTimeout(45, TimeUnit.SECONDS)     // 读取таймаут45 сек.
                .writeTimeout(60, TimeUnit.SECONDS)    // 写入таймаут60 сек.（Файл传)
                .build();
    }

    /**
     * 构建 API URL
     * использованиеконфигурация  API Host（поддержка自定义反 к 代理地址)
     */
    private String buildUrl(String method) {
        return config.getBotApiHost() + "/bot" + config.getBotToken() + "/" + method;
    }

    /**
     * Получение Bot Информация（用于验证 Token  否действует)
     */
    public JsonObject getMe() throws IOException {
        String url = buildUrl("getMe");

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            AppLog.d(TAG, "getMe 响应: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("getMe Ошибка: " + response.code() + ", " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            if (!jsonResponse.get("ok").getAsBoolean()) {
                throw new IOException("getMe Ошибка: " + responseBody);
            }

            return jsonResponse.getAsJsonObject("result");
        }
    }

    /**
     * Получениеобновление（Long Polling 方式)
     * @param offset  от 此 update_id Вкл始Получение
     * @param timeout 长轮询таймаут时间（ сек.)
     * @param limit 限制返回 обновление数量（1-100，По умолчанию100)
     */
    public JsonArray getUpdates(long offset, int timeout, int limit) throws IOException {
        String url = buildUrl("getUpdates") +
                "?offset=" + offset +
                "&timeout=" + timeout +
                "&limit=" + Math.max(1, Math.min(limit, 100)) +
                "&allowed_updates=" + "[\"message\"]";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        // 为 Long Polling 创建特殊 客户端，таймаут时间更长
        OkHttpClient longPollClient = httpClient.newBuilder()
                .readTimeout(timeout + 10, TimeUnit.SECONDS)
                .build();

        try (Response response = longPollClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("getUpdates Ошибка: " + response.code() + ", " + responseBody);
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            if (!jsonResponse.get("ok").getAsBoolean()) {
                throw new IOException("getUpdates Ошибка: " + responseBody);
            }

            return jsonResponse.getAsJsonArray("result");
        }
    }

    /**
     * Отправка文本消息
     */
    public void sendMessage(long chatId, String text) throws IOException {
        String url = buildUrl("sendMessage");

        JsonObject body = new JsonObject();
        body.addProperty("chat_id", chatId);
        body.addProperty("text", text);
        body.addProperty("parse_mode", "HTML");

        String requestJson = gson.toJson(body);
        AppLog.d(TAG, "Отправка消息: chatId=" + chatId + ", text=" + text);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestJson
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "Ошибка отправки сообщения，响应: " + responseBody);
                throw new IOException("Ошибка отправки сообщения: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "消息ОтправкаУспешно");
        }
    }

    /**
     * ОтправкаИзображение
     */
    public void sendPhoto(long chatId, File photoFile) throws IOException {
        sendPhoto(chatId, photoFile, null);
    }

    /**
     * ОтправкаИзображение（带说明文字)
     */
    public void sendPhoto(long chatId, File photoFile, String caption) throws IOException {
        String url = buildUrl("sendPhoto");

        RequestBody fileBody = RequestBody.create(
                MediaType.parse("image/jpeg"),
                photoFile
        );

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", String.valueOf(chatId))
                .addFormDataPart("photo", photoFile.getName(), fileBody);

        if (caption != null && !caption.isEmpty()) {
            builder.addFormDataPart("caption", caption);
        }

        Request request = new Request.Builder()
                .url(url)
                .post(builder.build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "ОтправкаИзображениеОшибка，响应: " + responseBody);
                throw new IOException("ОтправкаИзображениеОшибка: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "ИзображениеОтправкаУспешно: " + photoFile.getName());
        }
    }

    /**
     * ОтправкаВидео
     */
    public void sendVideo(long chatId, File videoFile, File thumbnailFile, int duration) throws IOException {
        sendVideo(chatId, videoFile, thumbnailFile, duration, null);
    }

    /**
     * ОтправкаВидео（带说明文字)
     */
    public void sendVideo(long chatId, File videoFile, File thumbnailFile, int duration, String caption) throws IOException {
        String url = buildUrl("sendVideo");

        RequestBody videoBody = RequestBody.create(
                MediaType.parse("video/mp4"),
                videoFile
        );

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", String.valueOf(chatId))
                .addFormDataPart("video", videoFile.getName(), videoBody)
                .addFormDataPart("duration", String.valueOf(duration))
                .addFormDataPart("supports_streaming", "true");

        if (thumbnailFile != null && thumbnailFile.exists()) {
            RequestBody thumbBody = RequestBody.create(
                    MediaType.parse("image/jpeg"),
                    thumbnailFile
            );
            builder.addFormDataPart("thumbnail", thumbnailFile.getName(), thumbBody);
        }

        if (caption != null && !caption.isEmpty()) {
            builder.addFormDataPart("caption", caption);
        }

        Request request = new Request.Builder()
                .url(url)
                .post(builder.build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "ОтправкаВидеоОшибка，响应: " + responseBody);
                throw new IOException("ОтправкаВидеоОшибка: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "ВидеоОтправкаУспешно: " + videoFile.getName());
        }
    }

    /**
     * Отправка文档/Файл
     */
    public void sendDocument(long chatId, File file) throws IOException {
        sendDocument(chatId, file, null);
    }

    /**
     * Отправка文档/Файл（带说明文字)
     */
    public void sendDocument(long chatId, File file, String caption) throws IOException {
        String url = buildUrl("sendDocument");

        RequestBody fileBody = RequestBody.create(
                MediaType.parse("application/octet-stream"),
                file
        );

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", String.valueOf(chatId))
                .addFormDataPart("document", file.getName(), fileBody);

        if (caption != null && !caption.isEmpty()) {
            builder.addFormDataPart("caption", caption);
        }

        Request request = new Request.Builder()
                .url(url)
                .post(builder.build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                AppLog.e(TAG, "ОтправкаФайлОшибка，响应: " + responseBody);
                throw new IOException("ОтправкаФайлОшибка: " + response.code() + ", " + responseBody);
            }
            AppLog.d(TAG, "ФайлОтправкаУспешно: " + file.getName());
        }
    }

    /**
     * Отправка聊天операция（если"Выполняется Ввести..."、"Выполняется 传Видео...")
     * @param action typing, upload_photo, upload_video, upload_document 等
     */
    public void sendChatAction(long chatId, String action) {
        try {
            String url = buildUrl("sendChatAction");

            JsonObject body = new JsonObject();
            body.addProperty("chat_id", chatId);
            body.addProperty("action", action);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(
                            MediaType.parse("application/json"),
                            gson.toJson(body)
                    ))
                    .build();

            // 异步Отправка，不ожидание响应
            httpClient.newCall(request).execute().close();
        } catch (Exception e) {
            AppLog.w(TAG, "Отправка ChatAction Ошибка: " + e.getMessage());
        }
    }
}
