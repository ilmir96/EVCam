package com.kooo.evcam;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 * Менеджер обновления версии через GitHub Releases API.
 * Проверяет наличие новой версии и скачивает APK.
 */
public class VersionUpdateManager {
    private static final String TAG = "VersionUpdateManager";

    private final Context context;
    private final AppConfig appConfig;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;

    // Текущая задача скачивания, для отмены
    private Call currentDownloadCall;

    // Кэшируем URL для скачивания APK из последнего ответа API
    private String cachedApkDownloadUrl;

    /**
     * Callback проверки версии
     */
    public interface UpdateCheckCallback {
        void onUpdateAvailable(String newVersion);
        void onNoUpdate();
        void onError(String error);
    }

    /**
     * Callback скачивания
     */
    public interface DownloadCallback {
        void onProgress(int progress);
        void onComplete(File apkFile);
        void onError(String error);
    }

    public VersionUpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.appConfig = new AppConfig(context);
        this.mainHandler = new Handler(Looper.getMainLooper());

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * Получение текущей версии приложения
     */
    public String getCurrentVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            AppLog.e(TAG, "Ошибка получения версии: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * Получение URL GitHub Releases API
     */
    private String getApiUrl() {
        String url = appConfig.getUpdateServerUrl();
        if (url == null || url.isEmpty()) {
            return null;
        }
        return url;
    }

    /**
     * Проверка, настроен ли сервер обновлений
     */
    public boolean isUpdateServerConfigured() {
        String url = appConfig.getUpdateServerUrl();
        return url != null && !url.isEmpty();
    }

    /**
     * Проверка обновления через GitHub Releases API
     */
    public void checkUpdate(UpdateCheckCallback callback) {
        String apiUrl = getApiUrl();
        if (apiUrl == null) {
            mainHandler.post(() -> callback.onError("Не указан адрес сервера обновлений"));
            return;
        }

        AppLog.d(TAG, "Проверка обновления: " + apiUrl);

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                AppLog.e(TAG, "Ошибка проверки версии: " + e.getMessage());
                mainHandler.post(() -> callback.onError("Ошибка сети: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        AppLog.e(TAG, "Ошибка проверки, HTTP: " + response.code());
                        mainHandler.post(() -> callback.onError("Ошибка сервера: " + response.code()));
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        mainHandler.post(() -> callback.onError("Сервер вернул пустой ответ"));
                        return;
                    }

                    String json = body.string();
                    JsonObject release = JsonParser.parseString(json).getAsJsonObject();

                    // Извлекаем tag_name (например "v1.2.1" или "1.2.1")
                    String tagName = release.get("tag_name").getAsString().trim();
                    String remoteVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;

                    AppLog.d(TAG, "Версия на GitHub: " + remoteVersion);

                    if (!isValidVersionFormat(remoteVersion)) {
                        mainHandler.post(() -> callback.onError("Неверный формат версии: " + remoteVersion));
                        return;
                    }

                    // Ищем APK в assets
                    JsonArray assets = release.getAsJsonArray("assets");
                    cachedApkDownloadUrl = null;
                    if (assets != null) {
                        for (int i = 0; i < assets.size(); i++) {
                            JsonObject asset = assets.get(i).getAsJsonObject();
                            String name = asset.get("name").getAsString();
                            if (name.endsWith(".apk")) {
                                cachedApkDownloadUrl = asset.get("browser_download_url").getAsString();
                                break;
                            }
                        }
                    }

                    String currentVersion = getCurrentVersion();
                    AppLog.d(TAG, "Текущая: " + currentVersion + ", Удалённая: " + remoteVersion);

                    if (isNewerVersion(remoteVersion, currentVersion)) {
                        mainHandler.post(() -> callback.onUpdateAvailable(remoteVersion));
                    } else {
                        mainHandler.post(() -> callback.onNoUpdate());
                    }
                } catch (Exception e) {
                    AppLog.e(TAG, "Ошибка парсинга ответа: " + e.getMessage());
                    mainHandler.post(() -> callback.onError("Ошибка парсинга: " + e.getMessage()));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Скачивание APK из GitHub Releases
     */
    public void downloadApk(String newVersion, DownloadCallback callback) {
        if (cachedApkDownloadUrl == null || cachedApkDownloadUrl.isEmpty()) {
            mainHandler.post(() -> callback.onError("APK не найден в релизе"));
            return;
        }

        AppLog.d(TAG, "Начинаем скачивание APK: " + cachedApkDownloadUrl);

        Request request = new Request.Builder()
                .url(cachedApkDownloadUrl)
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
                    AppLog.e(TAG, "Ошибка скачивания: " + e.getMessage());
                    mainHandler.post(() -> callback.onError("Ошибка скачивания: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                currentDownloadCall = null;

                if (!response.isSuccessful()) {
                    AppLog.e(TAG, "Ошибка скачивания, HTTP: " + response.code());
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
                    long contentLength = body.contentLength();
                    AppLog.d(TAG, "APK размер: " + contentLength + " bytes");

                    File downloadDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }

                    String fileName = "EVCam_" + newVersion + ".apk";
                    File apkFile = new File(downloadDir, fileName);

                    if (apkFile.exists()) {
                        apkFile.delete();
                    }

                    InputStream inputStream = body.byteStream();
                    FileOutputStream outputStream = new FileOutputStream(apkFile);

                    byte[] buffer = new byte[8192];
                    long downloadedBytes = 0;
                    int bytesRead;
                    int lastProgress = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

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

                    AppLog.d(TAG, "APK скачан: " + apkFile.getAbsolutePath());
                    mainHandler.post(() -> callback.onComplete(apkFile));

                } catch (IOException e) {
                    AppLog.e(TAG, "Ошибка сохранения: " + e.getMessage());
                    mainHandler.post(() -> callback.onError("Ошибка сохранения: " + e.getMessage()));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Отмена текущего скачивания
     */
    public void cancelDownload() {
        if (currentDownloadCall != null && !currentDownloadCall.isCanceled()) {
            currentDownloadCall.cancel();
            AppLog.d(TAG, "Отмена скачивания");
        }
    }

    /**
     * Валидация формата версии
     */
    private boolean isValidVersionFormat(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        return version.matches("^\\d+\\.\\d+.*$");
    }

    /**
     * Сравнение версий
     */
    private boolean isNewerVersion(String newVersion, String currentVersion) {
        try {
            String newMain = extractMainVersion(newVersion);
            String currentMain = extractMainVersion(currentVersion);

            String[] newParts = newMain.split("\\.");
            String[] currentParts = currentMain.split("\\.");

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

            boolean newIsTest = newVersion.contains("-test-");
            boolean currentIsTest = currentVersion.contains("-test-");

            if (newIsTest && !currentIsTest) {
                return true;
            }
            if (!newIsTest && currentIsTest) {
                return false;
            }
            if (newIsTest && currentIsTest) {
                String newTimestamp = extractTestTimestamp(newVersion);
                String currentTimestamp = extractTestTimestamp(currentVersion);
                return newTimestamp.compareTo(currentTimestamp) > 0;
            }

            return false;
        } catch (Exception e) {
            AppLog.e(TAG, "Ошибка сравнения версий: " + e.getMessage());
            return false;
        }
    }

    private String extractMainVersion(String version) {
        int testIndex = version.indexOf("-test-");
        if (testIndex > 0) {
            return version.substring(0, testIndex);
        }
        int dashIndex = version.indexOf("-");
        if (dashIndex > 0) {
            return version.substring(0, dashIndex);
        }
        return version;
    }

    private String extractTestTimestamp(String version) {
        int testIndex = version.indexOf("-test-");
        if (testIndex > 0 && testIndex + 6 < version.length()) {
            return version.substring(testIndex + 6);
        }
        return "";
    }

    private int parseVersionPart(String part) {
        try {
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
