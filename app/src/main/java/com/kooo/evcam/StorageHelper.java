package com.kooo.evcam;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ХранилищеПомощь类
 * 提供USB-накопитель检测 и ХранилищеПутьуправлениефункция
 * 
 * 性能优化：использование内存缓存减少重复 ФайлСистема I/O операция
 */
public class StorageHelper {
    private static final String TAG = "StorageHelper";
    
    // Хранилищекаталог名称
    public static final String VIDEO_DIR_NAME = "EVCam_Video";
    public static final String PHOTO_DIR_NAME = "EVCam_Photo";
    public static final String LOG_DIR_NAME = "EVCam_Log";
    
    // ==================== 内存缓存（性能优化)====================
    // USB-накопительРезультат обнаружения缓存（避免重复 ФайлСистема I/O)
    private static volatile Boolean cachedHasSdCard = null;
    private static volatile File cachedSdCardRoot = null;
    private static volatile long cacheTimestamp = 0;
    private static final long CACHE_VALIDITY_MS = 5000;  // 缓存действует期：5 сек.
    
    // 用于同步 锁 象
    private static final Object cacheLock = new Object();
    
    /**
     * очистка内存缓存（USB-накопитель插拔时调用)
     */
    public static void clearCache() {
        synchronized (cacheLock) {
            cachedHasSdCard = null;
            cachedSdCardRoot = null;
            cacheTimestamp = 0;
            AppLog.d(TAG, "USB-накопитель检测缓存очистка");
        }
    }
    
    /**
     * проверка缓存 否действует
     */
    private static boolean isCacheValid() {
        return cacheTimestamp > 0 && (System.currentTimeMillis() - cacheTimestamp) < CACHE_VALIDITY_MS;
    }
    
    /**
     * 检测 否有USB-накопитель（и可以写入公Всего каталог)
     * использование内存缓存，5 сек.内不重复检测
     * @param context 文
     * @return true Если ОбнаруженоUSB-накопитель且доступен для записи入
     */
    public static boolean hasExternalSdCard(Context context) {
        // 先проверка缓存
        synchronized (cacheLock) {
            if (isCacheValid() && cachedHasSdCard != null) {
                return cachedHasSdCard;
            }
        }
        
        // 缓存недействительно，выполнение检测
        File sdCardRoot = getExternalSdCardRoot(context);
        boolean result = false;
        
        if (sdCardRoot != null && sdCardRoot.exists()) {
            // проверка DCIM каталог 否доступен для записи
            File dcimDir = new File(sdCardRoot, Environment.DIRECTORY_DCIM);
            if (!dcimDir.exists()) {
                // попытка创建 DCIM каталог
                boolean created = dcimDir.mkdirs();
                if (!created) {
                    AppLog.w(TAG, "无法 USB-накопитель创建 DCIM каталог");
                }
            }
            result = dcimDir.exists() && dcimDir.canWrite();
        }
        
        // обновление缓存
        synchronized (cacheLock) {
            cachedHasSdCard = result;
            cacheTimestamp = System.currentTimeMillis();
        }
        
        return result;
    }
    
    /**
     * 检测 否发生USB-накопитель回退
     * т.е.：用户ВыбратьUSB-накопительХранилище，但USB-накопитель недоступен，实际использованиеВнутренняя память
     * @param context 文
     * @return true Если 发生回退
     */
    public static boolean isSdCardFallback(Context context) {
        if (context == null) return false;
        
        AppConfig config = new AppConfig(context);
        // 只有当用户ВыбратьUSB-накопитель时才необходимо检测回退
        if (!config.isUsingExternalSdCard()) {
            return false;
        }
        
        // 检测USB-накопитель 否Доступно
        return !hasExternalSdCard(context);
    }
    
    /**
     * ПолучениеUSB-накопительПуть
     * @param context 文
     * @return USB-накопитель根каталог，Если 没有则Возвращает null
     */
    public static File getExternalSdCardPath(Context context) {
        if (context == null) {
            return null;
        }
        
        try {
            // Получение所有Внешняя память设备
            File[] externalDirs = context.getExternalFilesDirs(null);
            
            if (externalDirs == null || externalDirs.length < 2) {
                AppLog.d(TAG, "Не ОбнаруженоUSB-накопитель（только有Внутренняя память)");
                return null;
            }
            
            // Первый шт. Внутренняя память，Второй шт.及以后 USB-накопитель
            for (int i = 1; i < externalDirs.length; i++) {
                File dir = externalDirs[i];
                if (dir != null && dir.exists()) {
                    // попыткаПолучениеUSB-накопитель根каталог（去掉 /Android/data/包名/files 部分)
                    String path = dir.getAbsolutePath();
                    int index = path.indexOf("/Android/data/");
                    if (index > 0) {
                        File sdRoot = new File(path.substring(0, index));
                        if (sdRoot.exists() && sdRoot.canRead()) {
                            AppLog.d(TAG, "ОбнаруженоUSB-накопитель: " + sdRoot.getAbsolutePath());
                            return sdRoot;
                        }
                    }
                    
                    // Если 无法Получение根каталог，返回Приложение专属каталог 级каталог
                    AppLog.d(TAG, "ОбнаруженоUSB-накопитель（Приложениекаталог): " + dir.getAbsolutePath());
                    return dir;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "检测USB-накопительОшибка", e);
        }
        
        return null;
    }
    
    /**
     * ПолучениеUSB-накопитель Приложение专属каталог
     * @param context 文
     * @return USB-накопитель Приложение专属каталог，Если 没有则Возвращает null
     */
    public static File getExternalSdCardAppDir(Context context) {
        if (context == null) {
            return null;
        }
        
        try {
            File[] externalDirs = context.getExternalFilesDirs(null);
            
            if (externalDirs != null && externalDirs.length >= 2) {
                File dir = externalDirs[1];
                if (dir != null) {
                    // 确保каталогсуществует
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    return dir;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "ПолучениеUSB-накопительПриложениекаталогОшибка", e);
        }
        
        return null;
    }
    
    /**
     * ПолучениеВидеоХранилищекаталог
     * @param context 文
     * @param useExternalSd  否использованиеUSB-накопитель
     * @return ВидеоХранилищекаталог
     */
    public static File getVideoDir(Context context, boolean useExternalSd) {
        return getStorageDir(context, useExternalSd, VIDEO_DIR_NAME, Environment.DIRECTORY_DCIM);
    }
    
    /**
     * ПолучениеИзображениеХранилищекаталог
     * @param context 文
     * @param useExternalSd  否использованиеUSB-накопитель
     * @return ИзображениеХранилищекаталог
     */
    public static File getPhotoDir(Context context, boolean useExternalSd) {
        return getStorageDir(context, useExternalSd, PHOTO_DIR_NAME, Environment.DIRECTORY_DCIM);
    }
    
    /**
     * Получение д.志Хранилищекаталог
     * @param context 文
     * @param useExternalSd  否использованиеUSB-накопитель
     * @return  д.志Хранилищекаталог
     */
    public static File getLogDir(Context context, boolean useExternalSd) {
        return getStorageDir(context, useExternalSd, LOG_DIR_NAME, Environment.DIRECTORY_DOWNLOADS);
    }
    
    /**
     * 根据 AppConfig конфигурацияПолучениеВидеоХранилищекаталог
     * @param context 文
     * @return ВидеоХранилищекаталог
     */
    public static File getVideoDir(Context context) {
        AppConfig config = new AppConfig(context);
        if (config.isUsingCustomPath()) {
            return getStorageDirFromCustomPath(config.getCustomStoragePath(), VIDEO_DIR_NAME, Environment.DIRECTORY_DCIM);
        }
        return getVideoDir(context, config.isUsingExternalSdCard());
    }
    
    /**
     * ПолучениеЗапись时实际写入 каталог
     * Если Включить转写入，返回временнокаталог；否则返回最终Хранилищекаталог
     * @param context 文
     * @return Запись写入каталог
     */
    public static File getRecordingDir(Context context) {
        AppConfig config = new AppConfig(context);
        
        // проверка 否应该использование转写入
        if (config.shouldUseRelayWrite()) {
            // использованиевременнокаталог（Внутренняя память 缓存каталог)
            File tempDir = new File(context.getCacheDir(), FileTransferManager.TEMP_VIDEO_DIR);
            if (!tempDir.exists()) {
                if (tempDir.mkdirs()) {
                    AppLog.d(TAG, "创建временноВидеокаталог: " + tempDir.getAbsolutePath());
                } else {
                    AppLog.e(TAG, "创建временноВидеокаталогОшибка，回退 до 普通каталог");
                    return getVideoDir(context);
                }
            }
            return tempDir;
        }
        
        // 不использование转写入，直接返回最终Хранилищекаталог
        return getVideoDir(context);
    }
    
    /**
     * ПолучениеВидео 最终Хранилищекаталог
     * т.е.使Включить转写入，这 шт.方法также返回最终 目标каталог
     * @param context 文
     * @return 最终Хранилищекаталог
     */
    public static File getFinalVideoDir(Context context) {
        AppConfig config = new AppConfig(context);
        if (config.isUsingCustomPath()) {
            return getStorageDirFromCustomPath(config.getCustomStoragePath(), VIDEO_DIR_NAME, Environment.DIRECTORY_DCIM);
        }
        return getVideoDir(context, config.isUsingExternalSdCard());
    }
    
    /**
     * проверкавременнокаталог 否有足够空间
     * @param context 文
     * @param requiredBytes необходимо 字节数
     * @return true Если 有足够空间
     */
    public static boolean hasSufficientTempSpace(Context context, long requiredBytes) {
        File cacheDir = context.getCacheDir();
        long available = getAvailableSpace(cacheDir);
        return available > requiredBytes;
    }
    
    /**
     * Получениевременнокаталог Доступно空间
     * @param context 文
     * @return Доступно空间（字节)
     */
    public static long getTempAvailableSpace(Context context) {
        return getAvailableSpace(context.getCacheDir());
    }
    
    /**
     * 根据 AppConfig конфигурацияПолучениеИзображениеХранилищекаталог
     * @param context 文
     * @return ИзображениеХранилищекаталог
     */
    public static File getPhotoDir(Context context) {
        AppConfig config = new AppConfig(context);
        if (config.isUsingCustomPath()) {
            return getStorageDirFromCustomPath(config.getCustomStoragePath(), PHOTO_DIR_NAME, Environment.DIRECTORY_DCIM);
        }
        return getPhotoDir(context, config.isUsingExternalSdCard());
    }
    
    /**
     * 根据произвольный путь获取存储目录
     * 如果произвольный путь不可用，回退到内部存储
     * @param customPath произвольный путь根目录
     * @param dirName 目录名称
     * @param parentDirType 父目录类型（如 DCIM, Downloads）
     * @return 存储目录
     */
    private static File getStorageDirFromCustomPath(String customPath, String dirName, String parentDirType) {
        if (customPath != null && !customPath.isEmpty()) {
            File customRoot = new File(customPath);
            if (customRoot.exists() && customRoot.isDirectory()) {
                File parentDir = new File(customRoot, parentDirType);
                File dir = new File(parentDir, dirName);
                if (!dir.exists()) {
                    boolean created = dir.mkdirs();
                    if (created) {
                        AppLog.d(TAG, "创建произвольный путь存储目录: " + dir.getAbsolutePath());
                    } else {
                        AppLog.e(TAG, "创建произвольный путь存储目录失败: " + dir.getAbsolutePath());
                    }
                }
                if (dir.exists()) {
                    return dir;
                }
            }
            AppLog.w(TAG, "произвольный путь不可用，回退到内部存储: " + customPath);
        }
        // fallback到内部存储
        File dir = new File(Environment.getExternalStoragePublicDirectory(parentDirType), dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * ПолучениеХранилищекаталог
     * @param context 文
     * @param useExternalSd  否использованиеUSB-накопитель
     * @param dirName каталог名称
     * @param parentDirType 父каталог类型（если DCIM, Downloads)
     * @return Хранилищекаталог
     */
    private static File getStorageDir(Context context, boolean useExternalSd, String dirName, String parentDirType) {
        File dir;
        
        if (useExternalSd) {
            // использованиеUSB-накопитель 公Всего каталог（USB-накопитель/DCIM/EVCam_Video или USB-накопитель/DCIM/EVCam_Photo)
            File sdCardRoot = getExternalSdCardRoot(context);
            if (sdCardRoot != null) {
                //  USB-накопитель 公Всего каталог创建子каталог（если /storage/xxxx-xxxx/DCIM/EVCam_Video)
                File parentDir = new File(sdCardRoot, parentDirType);
                dir = new File(parentDir, dirName);
            } else {
                // Если 没有USB-накопитель，回退 до Внутренняя память
                AppLog.w(TAG, "USB-накопитель недоступен，回退 до Внутренняя память");
                dir = new File(Environment.getExternalStoragePublicDirectory(parentDirType), dirName);
            }
        } else {
            // использованиеВнутренняя память 公Всего каталог
            dir = new File(Environment.getExternalStoragePublicDirectory(parentDirType), dirName);
        }
        
        // 确保каталогсуществует
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                AppLog.d(TAG, "创建Хранилищекаталог: " + dir.getAbsolutePath());
            } else {
                AppLog.e(TAG, "创建ХранилищекаталогОшибка: " + dir.getAbsolutePath());
            }
        }
        
        return dir;
    }
    
    /**
     * ПолучениеUSB-накопитель根каталог（用于写入公Всего каталог)
     * 优化检测逻辑：内存缓存优先 + SharedPreferences缓存 + 无感切换不同USB-накопитель
     * @param context 文
     * @return USB-накопитель根каталог，Если 没有则Возвращает null
     */
    public static File getExternalSdCardRoot(Context context) {
        if (context == null) {
            return null;
        }
        
        // 优先проверка内存缓存（最快，避免任何 I/O)
        synchronized (cacheLock) {
            if (isCacheValid() && cachedSdCardRoot != null) {
                // 快速验证缓存 Путь仍然действует
                if (cachedSdCardRoot.exists() && cachedSdCardRoot.canRead()) {
                    return cachedSdCardRoot;
                }
                // 缓存 Путь失效，очистка缓存продолжить检测
                cachedSdCardRoot = null;
                cachedHasSdCard = null;
            }
        }
        
        // 内存缓存Не 命，выполнение检测
        File result = getExternalSdCardRootInternal(context);
        
        // обновление内存缓存
        synchronized (cacheLock) {
            cachedSdCardRoot = result;
            cacheTimestamp = System.currentTimeMillis();
        }
        
        return result;
    }
    
    /**
     * 实际выполнениеUSB-накопитель检测（Внутреннее方法，不использование缓存)
     */
    private static File getExternalSdCardRootInternal(Context context) {
        AppConfig config = new AppConfig(context);
        
        // 方法0：优先использование用户вручнуюНастройки Путь
        String customPath = config.getCustomSdCardPath();
        if (customPath != null && !customPath.isEmpty()) {
            File customDir = new File(customPath);
            if (customDir.exists() && customDir.isDirectory() && customDir.canRead()) {
                return customDir;
            }
        }
        
        // 方法1：检测 раз SharedPreferences 缓存 Путь（比重新检测快)
        String spCachedPath = config.getLastDetectedSdPath();
        if (spCachedPath != null && !spCachedPath.isEmpty()) {
            File cachedDir = new File(spCachedPath);
            if (cachedDir.exists() && cachedDir.isDirectory() && cachedDir.canRead()) {
                return cachedDir;
            }
            // 缓存 Путь不Доступно（USB-накопитель拔出или更换)，продолжить检测
        }
        
        // 方法2：读取 /proc/mounts（快速可靠，能看 до 所有挂载 Хранилище设备)
        // 会检测任何 XXXX-XXXX 格式  SD 卡，实现无感切换
        File sdRoot = getSdCardFromMounts();
        if (sdRoot != null) {
            // ОбнаруженоUSB-накопитель，обновление SharedPreferences 缓存
            config.setLastDetectedSdPath(sdRoot.getAbsolutePath());
            return sdRoot;
        }
        
        // 方法3：通过 getExternalFilesDirs Получение（Стандарт API)
        sdRoot = getSdCardFromExternalFilesDirs(context);
        if (sdRoot != null) {
            // ОбнаруженоUSB-накопитель，обновление SharedPreferences 缓存
            config.setLastDetectedSdPath(sdRoot.getAbsolutePath());
            return sdRoot;
        }
        
        AppLog.d(TAG, "Не ОбнаруженоUSB-накопитель");
        return null;
    }
    
    /**
     * 方法1：读取 /proc/mounts 查找 SD 卡
     * 这 最可靠 方法，能看 до Система实际挂载 所有Хранилище设备
     * 只接受 /storage/XXXX-XXXX 格式
     */
    private static File getSdCardFromMounts() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/mounts"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                
                String mountPoint = parts[1];
                // 只接受 /storage/XXXX-XXXX 格式
                if (mountPoint.matches("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
                    File sdCard = new File(mountPoint);
                    if (sdCard.exists() && sdCard.isDirectory() && sdCard.canRead()) {
                        AppLog.d(TAG, "通过 /proc/mounts 找 до USB-накопитель: " + mountPoint);
                        reader.close();
                        return sdCard;
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            // 忽略Ошибка
        }
        return null;
    }
    
    /**
     * 方法2：通过Стандарт API getExternalFilesDirs Получение SD 卡
     * 只接受 /storage/XXXX-XXXX 格式 Путь
     */
    private static File getSdCardFromExternalFilesDirs(Context context) {
        try {
            File[] externalDirs = context.getExternalFilesDirs(null);
            
            if (externalDirs == null || externalDirs.length < 2) {
                return null;
            }
            
            // Первый шт. Внутренняя память，Второй шт.及以后可能 USB-накопитель
            for (int i = 1; i < externalDirs.length; i++) {
                File dir = externalDirs[i];
                if (dir != null && dir.exists()) {
                    String path = dir.getAbsolutePath();
                    int index = path.indexOf("/Android/data/");
                    if (index > 0) {
                        String sdRootPath = path.substring(0, index);
                        // 只接受 /storage/XXXX-XXXX 格式
                        if (sdRootPath.matches("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
                            File sdRoot = new File(sdRootPath);
                            if (sdRoot.exists() && sdRoot.canRead()) {
                                AppLog.d(TAG, "通过 getExternalFilesDirs 找 до USB-накопитель: " + sdRoot.getAbsolutePath());
                                return sdRoot;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略Ошибка
        }
        return null;
    }
    
    
    
    /**
     * Получение所有Обнаружено Хранилище设备Информация（用于отладка)
     * @param context 文
     * @return Хранилище设备Информация列表
     */
    public static List<String> getStorageDebugInfo(Context context) {
        List<String> info = new ArrayList<>();
        
        // 0. 显示Внутренняя памятьПуть（用于 比)
        info.add("=== Внутренняя память ===");
        String internalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        info.add("Путь: " + internalPath);
        info.add("");
        
        // 1. /proc/mounts 内容（最可靠 挂载Информация)
        info.add("=== /proc/mounts ===");
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/mounts"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    String mountPoint = parts[1];
                    // 只显示 /storage/ 相Выкл 挂载点
                    if (mountPoint.startsWith("/storage/")) {
                        String marker = "";
                        if (mountPoint.contains("emulated")) {
                            marker = " [Внутреннее]";
                        } else if (mountPoint.matches("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
                            marker = " [USB-накопитель]";
                        }
                        info.add(mountPoint + marker);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            info.add("Ошибка чтения: " + e.getMessage());
        }
        
        // 2. getExternalFilesDirs Информация
        info.add("");
        info.add("=== getExternalFilesDirs ===");
        try {
            File[] externalDirs = context.getExternalFilesDirs(null);
            if (externalDirs != null) {
                for (int i = 0; i < externalDirs.length; i++) {
                    File dir = externalDirs[i];
                    if (dir != null) {
                        String label = (i == 0) ? "[0] Внутреннее" : "[" + i + "] Внешнее";
                        info.add(label + ": " + dir.getAbsolutePath());
                    } else {
                        info.add("[" + i + "] null");
                    }
                }
            } else {
                info.add("Возвращает null");
            }
        } catch (Exception e) {
            info.add("Ошибка: " + e.getMessage());
        }
        
        // 3. Пользовательский путь
        info.add("");
        info.add("=== Пользовательский путь ===");
        AppConfig config = new AppConfig(context);
        String customPath = config.getCustomSdCardPath();
        if (customPath != null && !customPath.isEmpty()) {
            File customDir = new File(customPath);
            info.add("Путь: " + customPath);
            info.add("существует: " + customDir.exists() + ", чтение: " + customDir.canRead() + ", доступен для записи: " + customDir.canWrite());
        } else {
            info.add("Не Настройки");
        }
        
        // 4. Результат обнаружения
        info.add("");
        info.add("=== Результат обнаружения ===");
        File sdCard = getExternalSdCardRoot(context);
        if (sdCard != null) {
            info.add("ОбнаруженоUSB-накопитель: " + sdCard.getAbsolutePath());
            info.add("запись: " + sdCard.canWrite());
        } else {
            info.add("Не ОбнаруженоUSB-накопитель");
        }
        
        return info;
    }
    
    /**
     * ПолучениеХранилище空间Информация
     * @param path ХранилищеПуть
     * @return Доступно空间（字节)，Если Ошибка получения返回 -1
     */
    public static long getAvailableSpace(File path) {
        if (path == null || !path.exists()) {
            return -1;
        }
        
        try {
            StatFs stat = new StatFs(path.getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            AppLog.e(TAG, "ПолучениеХранилище空间ИнформацияОшибка", e);
            return -1;
        }
    }
    
    /**
     * Получение总Хранилище空间
     * @param path ХранилищеПуть
     * @return 总空间（字节)，Если Ошибка получения返回 -1
     */
    public static long getTotalSpace(File path) {
        if (path == null || !path.exists()) {
            return -1;
        }
        
        try {
            StatFs stat = new StatFs(path.getAbsolutePath());
            return stat.getBlockCountLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            AppLog.e(TAG, "Получение总Хранилище空间Ошибка", e);
            return -1;
        }
    }
    
    /**
     * 格式化Хранилище大小显示
     * @param bytes 字节数
     * @return 格式化后 字符串（если "1.5 GB")
     */
    public static String formatSize(long bytes) {
        if (bytes < 0) {
            return "Неизвестно";
        }
        
        final long KB = 1024;
        final long MB = KB * 1024;
        final long GB = MB * 1024;
        
        if (bytes >= GB) {
            return String.format("%.1f GB", (double) bytes / GB);
        } else if (bytes >= MB) {
            return String.format("%.1f MB", (double) bytes / MB);
        } else if (bytes >= KB) {
            return String.format("%.1f KB", (double) bytes / KB);
        } else {
            return bytes + " B";
        }
    }
    
    /**
     * ПолучениеХранилищеИнформация描述
     * @param context 文
     * @param useExternalSd  否использованиеUSB-накопитель
     * @return ХранилищеИнформация描述字符串
     */
    public static String getStorageInfoDesc(Context context, boolean useExternalSd) {
        File storageDir;
        String storageName;
        
        if (useExternalSd) {
            storageDir = getExternalSdCardRoot(context);
            storageName = "USB-накопитель";
            if (storageDir == null) {
                return "USB-накопитель недоступен";
            }
        } else {
            storageDir = Environment.getExternalStorageDirectory();
            storageName = "Внутренняя память";
        }
        
        long available = getAvailableSpace(storageDir);
        long total = getTotalSpace(storageDir);
        
        if (available < 0 || total < 0) {
            return storageName;
        }
        
        return String.format("%s（Доступно %s / Всего  %s)", 
                storageName, 
                formatSize(available), 
                formatSize(total));
    }
    
    /**
     * ПолучениеТекущийХранилищеПуть描述
     * @param context 文
     * @return ТекущийХранилищеПуть描述
     */
    public static String getCurrentStoragePathDesc(Context context) {
        AppConfig config = new AppConfig(context);

        File videoDir;
        if (config.isUsingCustomPath()) {
            videoDir = getStorageDirFromCustomPath(config.getCustomStoragePath(), VIDEO_DIR_NAME, Environment.DIRECTORY_DCIM);
        } else {
            boolean useExternalSd = config.isUsingExternalSdCard();
            videoDir = getVideoDir(context, useExternalSd);
        }
        return videoDir.getAbsolutePath();
    }
}
