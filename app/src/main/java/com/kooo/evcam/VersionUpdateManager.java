package com.kooo.evcam;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 版本обновлениеуправление器
 * 负责проверка新版本 и скачивание APK Файл
 */
public class VersionUpdateManager {
    private static final String TAG = "VersionUpdateManager";
    
    // По умолчаниюобновлениеСервис器конфигурация
    private static final String VERSION_FILE = "version.txt";
    // APK Файл名格式：EVCam-v{版本号}-release.apk
    private static final String APK_FILE_PATTERN = "EVCam-v%s-release.apk";
    
    private final Context context;
    private final AppConfig appConfig;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;
    
    // Текущийскачиваниезадача，用于Отмена
    private Call currentDownloadCall;
    
    /**
     * 版本проверка回调
     */
    public interface UpdateCheckCallback {
        /**
         * 发现新版本
         * @param newVersion 新版本号
         */
        void onUpdateAvailable(String newVersion);
        
        /**
         *  Последняя версия
         */
        void onNoUpdate();
        
        /**
         * проверкаОшибка
         * @param error ОшибкаИнформация
         */
        void onError(String error);
    }
    
    /**
     * скачивание回调
     */
    public interface DownloadCallback {
        /**
         * скачивание进度обновление
         * @param progress 进度百分比 (0-100)
         */
        void onProgress(int progress);
        
        /**
         * скачиваниезавершение
         * @param apkFile скачивание  APK Файл
         */
        void onComplete(File apkFile);
        
        /**
         * скачиваниеОшибка
         * @param error ОшибкаИнформация
         */
        void onError(String error);
    }
    
    public VersionUpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.appConfig = new AppConfig(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // конфигурация OkHttpClient
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * ПолучениеТекущийПриложение版本号
     */
    public String getCurrentVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            AppLog.e(TAG, "Получение版本号Ошибка: " + e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * ПолучениеобновлениеСервис器基础 URL
     */
    private String getBaseUrl() {
        String url = appConfig.getUpdateServerUrl();
        if (url == null || url.isEmpty()) {
            return null;
        }
        // 确保 URL 以 / 结尾
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }
    
    /**
     * проверка 否конфигурацияобновлениеСервис器
     */
    public boolean isUpdateServerConfigured() {
        String url = appConfig.getUpdateServerUrl();
        return url != null && !url.isEmpty();
    }
    
    /**
     * проверкаобновление
     */
    public void checkUpdate(UpdateCheckCallback callback) {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) {
            mainHandler.post(() -> callback.onError("Не указан адрес сервера обновлений"));
            return;
        }
        
        String versionUrl = baseUrl + VERSION_FILE;
        AppLog.d(TAG, "проверка版本обновление: " + versionUrl);
        
        Request request = new Request.Builder()
                .url(versionUrl)
                .get()
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                AppLog.e(TAG, "版本проверкаОшибка: " + e.getMessage());
                mainHandler.post(() -> callback.onError("СетьОшибка: " + e.getMessage()));
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        AppLog.e(TAG, "版本проверкаОшибка，HTTP Статус码: " + response.code());
                        mainHandler.post(() -> callback.onError("Ошибка сервера: " + response.code()));
                        return;
                    }
                    
                    ResponseBody body = response.body();
                    if (body == null) {
                        mainHandler.post(() -> callback.onError("Сервер вернул пустой ответ"));
                        return;
                    }
                    
                    String remoteVersion = body.string().trim();
                    AppLog.d(TAG, "Сервис器Версия: " + remoteVersion);
                    
                    // 验证版本号格式
                    if (!isValidVersionFormat(remoteVersion)) {
                        mainHandler.post(() -> callback.onError("Неверный формат версии: " + remoteVersion));
                        return;
                    }
                    
                    String currentVersion = getCurrentVersion();
                    AppLog.d(TAG, "ТекущийВерсия: " + currentVersion + ", УдалённыйВерсия: " + remoteVersion);
                    
                    if (isNewerVersion(remoteVersion, currentVersion)) {
                        mainHandler.post(() -> callback.onUpdateAvailable(remoteVersion));
                    } else {
                        mainHandler.post(() -> callback.onNoUpdate());
                    }
                } finally {
                    response.close();
                }
            }
        });
    }
    
    /**
     * скачивание APK Файл
     * @param newVersion 新版本号，用于构建Файл名 и 命名本地Файл
     */
    public void downloadApk(String newVersion, DownloadCallback callback) {
        String baseUrl = getBaseUrl();
        if (baseUrl == null) {
            mainHandler.post(() -> callback.onError("Не указан адрес сервера обновлений"));
            return;
        }
        
        // 根据版本号构建 APK Файл名
        String apkFileName = String.format(APK_FILE_PATTERN, newVersion);
        String apkUrl = baseUrl + apkFileName;
        AppLog.d(TAG, "Вкл始скачивание APK: " + apkUrl);
        
        Request request = new Request.Builder()
                .url(apkUrl)
                .get()
                .build();
        
        currentDownloadCall = httpClient.newCall(request);
        currentDownloadCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                currentDownloadCall = null;
                if (call.isCanceled()) {
                    AppLog.d(TAG, "Загрузка отменена");
                    mainHandler.post(() -> callback.onError("Загрузка отменена"));
                } else {
                    AppLog.e(TAG, "скачиваниеОшибка: " + e.getMessage());
                    mainHandler.post(() -> callback.onError("скачиваниеОшибка: " + e.getMessage()));
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                currentDownloadCall = null;
                
                if (!response.isSuccessful()) {
                    AppLog.e(TAG, "скачиваниеОшибка，HTTP Статус码: " + response.code());
                    mainHandler.post(() -> callback.onError("Ошибка сервера: " + response.code()));
                    response.close();
                    return;
                }
                
                ResponseBody body = response.body();
                if (body == null) {
                    mainHandler.post(() -> callback.onError("Сервер вернул пустой ответ"));
                    return;
                }
                
                try {
                    // ПолучениеФайл大小
                    long contentLength = body.contentLength();
                    AppLog.d(TAG, "APK Файл大小: " + contentLength + " bytes");
                    
                    // 创建目标Файл
                    File downloadDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }
                    
                    // Файл名：EVCam_版本号.apk
                    String fileName = "EVCam_" + newVersion + ".apk";
                    File apkFile = new File(downloadDir, fileName);
                    
                    // Если Файлсуществует，先删除
                    if (apkFile.exists()) {
                        apkFile.delete();
                    }
                    
                    // 写入Файл
                    InputStream inputStream = body.byteStream();
                    FileOutputStream outputStream = new FileOutputStream(apkFile);
                    
                    byte[] buffer = new byte[8192];
                    long downloadedBytes = 0;
                    int bytesRead;
                    int lastProgress = 0;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;
                        
                        // 计算进度
                        if (contentLength > 0) {
                            int progress = (int) (downloadedBytes * 100 / contentLength);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                final int finalProgress = progress;
                                mainHandler.post(() -> callback.onProgress(finalProgress));
                            }
                        }
                    }
                    
                    outputStream.flush();
                    outputStream.close();
                    inputStream.close();
                    
                    AppLog.d(TAG, "APK скачиваниезавершение: " + apkFile.getAbsolutePath());
                    mainHandler.post(() -> callback.onComplete(apkFile));
                    
                } catch (IOException e) {
                    AppLog.e(TAG, "СохранитьФайлОшибка: " + e.getMessage());
                    mainHandler.post(() -> callback.onError("СохранитьФайлОшибка: " + e.getMessage()));
                } finally {
                    response.close();
                }
            }
        });
    }
    
    /**
     * ОтменаТекущийскачивание
     */
    public void cancelDownload() {
        if (currentDownloadCall != null && !currentDownloadCall.isCanceled()) {
            currentDownloadCall.cancel();
            AppLog.d(TAG, "Отменаскачивание");
        }
    }
    
    /**
     * 验证版本号格式
     * поддержка格式：1.0.0、1.0.0-test-01301530 等
     */
    private boolean isValidVersionFormat(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        // 简单验证：至少содержит一 шт.数字 и 一 шт.点
        return version.matches("^\\d+\\.\\d+.*$");
    }
    
    /**
     * 比较版本号，判断 newVersion  否比 currentVersion обновление
     * поддержка格式：1.0.3、1.0.3-test-01301530
     * 
     * 规则：
     * 1. 主版本号不同时，数字大 обновление（1.0.4 > 1.0.3-test-xxx > 1.0.3)
     * 2. 主版本号相同时，有 -test- 后缀 比没有后缀 обновление（1.0.3-test-xxx > 1.0.3)
     * 3. все有 -test- 后缀时，比较时间戳（1.0.3-test-02032310 > 1.0.3-test-02031200)
     */
    private boolean isNewerVersion(String newVersion, String currentVersion) {
        try {
            // 提取主版本号部分（去掉 -test-xxx 后缀)
            String newMain = extractMainVersion(newVersion);
            String currentMain = extractMainVersion(currentVersion);
            
            // 分割版本号
            String[] newParts = newMain.split("\\.");
            String[] currentParts = currentMain.split("\\.");
            
            // 比较主版本号每 шт.部分
            int maxLength = Math.max(newParts.length, currentParts.length);
            for (int i = 0; i < maxLength; i++) {
                int newPart = i < newParts.length ? parseVersionPart(newParts[i]) : 0;
                int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
                
                if (newPart > currentPart) {
                    return true;
                } else if (newPart < currentPart) {
                    return false;
                }
            }
            
            // 主版本号相同，比较后缀
            boolean newIsTest = newVersion.contains("-test-");
            boolean currentIsTest = currentVersion.contains("-test-");
            
            // тестирование版 > 正式版（主版本号相同时)
            if (newIsTest && !currentIsTest) {
                return true;  // 新版本 тестирование版，Текущий 正式版，тестирование版обновление
            }
            
            if (!newIsTest && currentIsTest) {
                return false;  // 新版本 正式版，Текущий тестирование版，不算обновление
            }
            
            // 两者все тестирование版，比较时间戳
            if (newIsTest && currentIsTest) {
                String newTimestamp = extractTestTimestamp(newVersion);
                String currentTimestamp = extractTestTimestamp(currentVersion);
                return newTimestamp.compareTo(currentTimestamp) > 0;
            }
            
            // 两者все 正式版且版本号相同
            return false;
        } catch (Exception e) {
            AppLog.e(TAG, "版本比较Ошибка: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 提取主版本号（去掉 -test-xxx 后缀)
     */
    private String extractMainVersion(String version) {
        int testIndex = version.indexOf("-test-");
        if (testIndex > 0) {
            return version.substring(0, testIndex);
        }
        // 处理Другое可能 后缀（если -alpha、-beta)
        int dashIndex = version.indexOf("-");
        if (dashIndex > 0) {
            return version.substring(0, dashIndex);
        }
        return version;
    }
    
    /**
     * 提取тестирование版时间戳
     */
    private String extractTestTimestamp(String version) {
        int testIndex = version.indexOf("-test-");
        if (testIndex > 0 && testIndex + 6 < version.length()) {
            return version.substring(testIndex + 6);
        }
        return "";
    }
    
    /**
     * 解析版本号部分为整数
     */
    private int parseVersionPart(String part) {
        try {
            // 处理可能 非数字字符（если "3a" -> 3)
            StringBuilder digits = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else {
                    break;
                }
            }
            return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
