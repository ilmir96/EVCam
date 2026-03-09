package com.kooo.evcam.playback;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Изображение分 групп模型
 * 将同一时间戳拍 多 кам.Изображение групп合 一起（前/后/左/右)
 * Файл命名格式：yyyyMMdd_HHmmss_{position}.jpg
 */
public class PhotoGroup {

    /** КамераПозиция常量 */
    public static final String POSITION_FRONT = "front";
    public static final String POSITION_BACK = "back";
    public static final String POSITION_LEFT = "left";
    public static final String POSITION_RIGHT = "right";

    /** 时间戳前缀，если "20260131_1254" */
    private final String timestampPrefix;

    /** 拍时间（解析自Файл名) */
    private final Date captureTime;

    /** 各Позиция ИзображениеФайл */
    private final Map<String, File> photoFiles;

    /** 总Файл大小（所有Позиция之 и ) */
    private long totalSize;

    public PhotoGroup(String timestampPrefix) {
        this.timestampPrefix = timestampPrefix;
        this.photoFiles = new HashMap<>();
        this.totalSize = 0;
        this.captureTime = parseTimestamp(timestampPrefix);
    }

    /**
     * 添加ИзображениеФайл до 分 групп
     */
    public void addFile(File file) {
        String position = extractPosition(file.getName());
        if (position != null) {
            photoFiles.put(position, file);
            totalSize += file.length();
        }
    }

    /**
     *  от Файл名提取时间戳前缀
     */
    public static String extractTimestampPrefix(String fileName) {
        String nameWithoutExt = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
        }

        int lastUnderscore = nameWithoutExt.lastIndexOf('_');
        if (lastUnderscore > 0) {
            return nameWithoutExt.substring(0, lastUnderscore);
        }
        return nameWithoutExt;
    }

    /**
     *  от Файл名提取КамераПозиция
     */
    public static String extractPosition(String fileName) {
        String nameWithoutExt = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = fileName.substring(0, dotIndex);
        }

        int lastUnderscore = nameWithoutExt.lastIndexOf('_');
        if (lastUnderscore > 0 && lastUnderscore < nameWithoutExt.length() - 1) {
            return nameWithoutExt.substring(lastUnderscore + 1).toLowerCase();
        }
        return null;
    }

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

    public Date getCaptureTime() {
        return captureTime;
    }

    public String getFormattedDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(captureTime);
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(captureTime);
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(captureTime);
    }

    public File getPhotoFile(String position) {
        return photoFiles.get(position);
    }

    public File getFrontPhoto() {
        return photoFiles.get(POSITION_FRONT);
    }

    public File getBackPhoto() {
        return photoFiles.get(POSITION_BACK);
    }

    public File getLeftPhoto() {
        return photoFiles.get(POSITION_LEFT);
    }

    public File getRightPhoto() {
        return photoFiles.get(POSITION_RIGHT);
    }

    public Map<String, File> getAllPhotoFiles() {
        return new HashMap<>(photoFiles);
    }

    public File getThumbnailFile() {
        if (photoFiles.containsKey(POSITION_FRONT)) {
            return photoFiles.get(POSITION_FRONT);
        } else if (photoFiles.containsKey(POSITION_BACK)) {
            return photoFiles.get(POSITION_BACK);
        } else if (photoFiles.containsKey(POSITION_LEFT)) {
            return photoFiles.get(POSITION_LEFT);
        } else if (photoFiles.containsKey(POSITION_RIGHT)) {
            return photoFiles.get(POSITION_RIGHT);
        }
        return null;
    }

    public int getPhotoCount() {
        return photoFiles.size();
    }

    public long getTotalSize() {
        return totalSize;
    }

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

    public boolean hasPhoto(String position) {
        return photoFiles.containsKey(position);
    }

    public int deleteAll() {
        int deleted = 0;
        for (File file : photoFiles.values()) {
            if (file.delete()) {
                deleted++;
            }
        }
        if (deleted > 0) {
            photoFiles.clear();
            totalSize = 0;
        }
        return deleted;
    }
}
