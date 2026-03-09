package com.kooo.evcam.heartbeat;

import android.util.Base64;

import com.kooo.evcam.AppLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Мониторинг API 客户端
 * 负责 HTTPS 求Отправка и 签名生成
 */
public class HeartbeatApiClient {
    private static final String TAG = "HeartbeatApiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final OkHttpClient client;
    
    public HeartbeatApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)  // 写入таймаут较长（传Изображение)
                .build();
    }
    
    /**
     * Отправка心跳求
     * 
     * @param serverUrl Адрес сервера
     * @param vehicleId 车辆ID
     * @param secretKey 通信密钥
     * @param imageBytes Изображение数据
     * @param imageWidth Изображение宽度
     * @param imageHeight ИзображениеВысокий度
     * @param cameraCount Камера数量
     * @param appStatus App Статус JSON 字符串
     * @return 求 否Успешно
     */
    public HeartbeatResult sendHeartbeat(String serverUrl, String vehicleId, String secretKey,
                                          byte[] imageBytes, int imageWidth, int imageHeight,
                                          int cameraCount, String appStatus) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return new HeartbeatResult(false, "Адрес сервера не настроен");
        }
        
        if (imageBytes == null || imageBytes.length == 0) {
            return new HeartbeatResult(false, "Данные изображения пусты");
        }
        
        try {
            // 生成求参数
            long timestamp = System.currentTimeMillis();
            String nonce = generateNonce();
            String signature = generateSignature(vehicleId, timestamp, nonce, secretKey);
            
            if (signature == null) {
                return new HeartbeatResult(false, "Ошибка генерации подписи");
            }
            
            // 构建 JSON 求体
            String imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            String jsonBody = buildJsonBody(vehicleId, timestamp, nonce, signature,
                    imageBase64, imageWidth, imageHeight, imageBytes.length, cameraCount, appStatus);
            
            // Отправка求
            RequestBody body = RequestBody.create(jsonBody, JSON);
            Request request = new Request.Builder()
                    .url(serverUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Vehicle-Id", vehicleId)
                    .addHeader("X-Timestamp", String.valueOf(timestamp))
                    .addHeader("X-Nonce", nonce)
                    .addHeader("X-Signature", signature)
                    .build();
            
            AppLog.d(TAG, "Отправка心跳求: " + serverUrl + ", Изображение大小: " + (imageBytes.length / 1024) + "KB");
            
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    AppLog.d(TAG, "心跳求Успешно: " + code);
                    return new HeartbeatResult(true, "Успешно", code, responseBody);
                } else {
                    AppLog.w(TAG, "心跳求Ошибка: " + code + ", " + responseBody);
                    return new HeartbeatResult(false, "HTTP " + code + ": " + responseBody, code, responseBody);
                }
            }
            
        } catch (IOException e) {
            AppLog.e(TAG, "心跳求СетьОшибка: " + e.getMessage());
            return new HeartbeatResult(false, "СетьОшибка: " + e.getMessage());
        } catch (Exception e) {
            AppLog.e(TAG, "心跳求аномалия: " + e.getMessage(), e);
            return new HeartbeatResult(false, "аномалия: " + e.getMessage());
        }
    }
    
    /**
     * 生成求签名
     * signature = HMAC-SHA256(vehicleId + timestamp + nonce, secretKey)
     */
    public static String generateSignature(String vehicleId, long timestamp, String nonce, String secretKey) {
        if (secretKey == null || secretKey.isEmpty()) {
            return null;
        }
        
        String message = vehicleId + timestamp + nonce;
        
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            AppLog.e(TAG, "Ошибка генерации подписи: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 生成随机 nonce
     */
    public static String generateNonce() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 构建 JSON 求体
     */
    private String buildJsonBody(String vehicleId, long timestamp, String nonce, String signature,
                                  String imageBase64, int imageWidth, int imageHeight,
                                  int imageSizeBytes, int cameraCount, String appStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        // 认证Информация
        sb.append("\"vehicleId\":\"").append(escapeJson(vehicleId)).append("\",");
        sb.append("\"timestamp\":").append(timestamp).append(",");
        sb.append("\"nonce\":\"").append(escapeJson(nonce)).append("\",");
        sb.append("\"signature\":\"").append(escapeJson(signature)).append("\",");
        
        // Изображение数据
        sb.append("\"imageBase64\":\"").append(imageBase64).append("\",");
        sb.append("\"imageWidth\":").append(imageWidth).append(",");
        sb.append("\"imageHeight\":").append(imageHeight).append(",");
        sb.append("\"imageSizeBytes\":").append(imageSizeBytes).append(",");
        sb.append("\"cameraCount\":").append(cameraCount).append(",");
        
        // App Статус（经  JSON  象，直接嵌入)
        if (appStatus != null && !appStatus.isEmpty()) {
            sb.append("\"status\":").append(appStatus);
        } else {
            sb.append("\"status\":null");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 心跳求结果
     */
    public static class HeartbeatResult {
        public final boolean success;
        public final String message;
        public final int httpCode;
        public final String responseBody;
        
        public HeartbeatResult(boolean success, String message) {
            this(success, message, 0, null);
        }
        
        public HeartbeatResult(boolean success, String message, int httpCode, String responseBody) {
            this.success = success;
            this.message = message;
            this.httpCode = httpCode;
            this.responseBody = responseBody;
        }
    }
}
