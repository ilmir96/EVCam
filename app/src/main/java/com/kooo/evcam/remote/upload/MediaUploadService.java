package com.kooo.evcam.remote.upload;

import com.kooo.evcam.remote.core.ChatIdentifier;
import com.kooo.evcam.remote.core.RemoteUploadCallback;

import java.io.File;
import java.util.List;

/**
 * 媒体传Сервис接口
 * 定义统一 媒体Файл传接口
 */
public interface MediaUploadService {
    
    /**
     * 传ВидеоФайл
     * @param videoFiles ВидеоФайл列表
     * @param chatId 聊天标识
     * @param callback 传回调
     */
    void uploadVideos(List<File> videoFiles, ChatIdentifier chatId, RemoteUploadCallback callback);
    
    /**
     * 传ФотоФайл
     * @param photoFiles ФотоФайл列表
     * @param chatId 聊天标识
     * @param callback 传回调
     */
    void uploadPhotos(List<File> photoFiles, ChatIdentifier chatId, RemoteUploadCallback callback);
}
