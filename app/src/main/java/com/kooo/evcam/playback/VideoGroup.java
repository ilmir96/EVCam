package com.kooo.evcam.playback;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Видео分 групп模型
 * 将同一时间戳Запись 多 кам.Видео групп合 一起（前/后/左/右)
 * Файл命名格式：yyyyMMdd_HHmmss_{position}.mp4
 */
public class VideoGroup {
    
    /** КамераПозиция常量 */
    public static final String POSITION_FRONT = "front";
    public static final String POSITION_BACK = "back";
    public static final String POSITION_LEFT = "left";
    public static final String POSITION_RIGHT = "right";
    
    /** 时间戳前缀，если "20260131_1254" */
    private final String timestampPrefix;
    
    /** Запись时间（解析自Файл名) */
    private final Date recordTime;
    
    /** 各Позиция ВидеоФайл */
    private final Map<String, File> videoFiles;
    
    /** 总Файл大小（所有Позиция之 и ) */
    private long totalSize;
    
    public VideoGroup(String timestampPrefix) {
        this.timestampPrefix = timestampPrefix;
        this.videoFiles = new HashMap<>();
        this.totalSize = 0;
        this.recordTime = parseTimestamp(timestampPrefix);
    }
    
    /**
     * 添加ВидеоФайл до 分 групп
     * @param file ВидеоФайл
     */
    public void addFile(File file) {
        String position = extractPosition(file.getName());
        if (position != null) {
            videoFiles.put(position, file);
            totalSize += file.length();
        }
    }
    
    /**
     *  от Файл名提取时间戳前缀
     * @param fileName Файл名，если "20260131_125430_front.mp4"
     * @return 时间戳前缀，если "20260131_125430"
     */
    public static String extractTimestampPrefix(String fileName) {
        // 移除扩展名
        String nameWithoutExt = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
        }
        
        // 找 до 最后一 шт.划线，它до 时间戳
        int lastUnderscore = nameWithoutExt.lastIndexOf('_');
        if (lastUnderscore > 0) {
            return nameWithoutExt.substring(0, lastUnderscore);
        }
        return nameWithoutExt;
    }
    
    /**
     *  от Файл名提取КамераПозиция
     * @param fileName Файл名，если "20260131_125430_front.mp4"
     * @return Позиция，если "front"
     */
    public static String extractPosition(String fileName) {
        // 移除扩展名
        String nameWithoutExt = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
        }
        
        // 找 до 最后一 шт.划线，它после Позиция
        int lastUnderscore = nameWithoutExt.lastIndexOf('_');
        if (lastUnderscore > 0 && lastUnderscore < nameWithoutExt.length() - 1) {
            return nameWithoutExt.substring(lastUnderscore + 1).toLowerCase();
        }
        return null;
    }
    
    /**
     * 解析时间戳为 д.期
     */
    private Date parseTimestamp(String timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            return sdf.parse(timestamp);
        } catch (ParseException e) {
            return new Date(0);
        }
    }
    
    // Getters
    
    public String getTimestampPrefix() {
        return timestampPrefix;
    }
    
    public Date getRecordTime() {
        return recordTime;
    }
    
    /**
     * Получение格式化  д.期时间字符串
     */
    public String getFormattedDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(recordTime);
    }
    
    /**
     * Получение格式化  д.期字符串
     */
    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(recordTime);
    }
    
    /**
     * Получение格式化 时间字符串
     */
    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(recordTime);
    }
    
    /**
     * Получение指定Позиция ВидеоФайл
     * @param position Позиция（front/back/left/right)
     * @return Файл，可能为null
     */
    public File getVideoFile(String position) {
        return videoFiles.get(position);
    }
    
    /**
     * ПолучениеФронтальнаяКамераВидео
     */
    public File getFrontVideo() {
        return videoFiles.get(POSITION_FRONT);
    }
    
    /**
     * ПолучениеЗадняяКамераВидео
     */
    public File getBackVideo() {
        return videoFiles.get(POSITION_BACK);
    }
    
    /**
     * Получение左侧КамераВидео
     */
    public File getLeftVideo() {
        return videoFiles.get(POSITION_LEFT);
    }
    
    /**
     * Получение右侧КамераВидео
     */
    public File getRightVideo() {
        return videoFiles.get(POSITION_RIGHT);
    }
    
    /**
     * Получение所有ВидеоФайл
     */
    public Map<String, File> getAllVideoFiles() {
        return new HashMap<>(videoFiles);
    }
    
    /**
     * ПолучениеПервый шт.Доступно 缩略图Файл（用于列表显示)
     * 优先级：front > back > left > right
     */
    public File getThumbnailFile() {
        if (videoFiles.containsKey(POSITION_FRONT)) {
            return videoFiles.get(POSITION_FRONT);
        } else if (videoFiles.containsKey(POSITION_BACK)) {
            return videoFiles.get(POSITION_BACK);
        } else if (videoFiles.containsKey(POSITION_LEFT)) {
            return videoFiles.get(POSITION_LEFT);
        } else if (videoFiles.containsKey(POSITION_RIGHT)) {
            return videoFiles.get(POSITION_RIGHT);
        }
        return null;
    }
    
    /**
     * ПолучениеВидео кам.数
     */
    public int getVideoCount() {
        return videoFiles.size();
    }
    
    /**
     * Получение总Файл大小
     */
    public long getTotalSize() {
        return totalSize;
    }
    
    /**
     * Получение格式化 Файл大小字符串
     */
    public String getFormattedSize() {
        if (totalSize < 1024) {
            return totalSize + " B";
        } else if (totalSize < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.2f KB", totalSize / 1024.0);
        } else if (totalSize < 1024L * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.2f MB", totalSize / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.getDefault(), "%.2f GB", totalSize / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * проверка 否有指定Позиция Видео
     */
    public boolean hasVideo(String position) {
        return videoFiles.containsKey(position);
    }
    
    /**
     * 删除所有ВидеоФайл
     * @return Успешно删除 Файл数
     */
    public int deleteAll() {
        int deleted = 0;
        for (File file : videoFiles.values()) {
            if (file.delete()) {
                deleted++;
            }
        }
        if (deleted > 0) {
            videoFiles.clear();
            totalSize = 0;
        }
        return deleted;
    }
}
