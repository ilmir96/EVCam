package com.kooo.evcam.camera;

import java.util.List;

/**
 * Запись回调接口
 */
public interface RecordCallback {
    /**
     * ЗаписьВкл始
     */
    void onRecordStart(String cameraId);

    /**
     * ЗаписьОстановка
     */
    void onRecordStop(String cameraId);

    /**
     * ЗаписьОшибка
     */
    void onRecordError(String cameraId, String error);

    /**
     * 预分切换（ ОстановкаТекущий MediaRecorder до调用)
     * 用于УведомлениеВнешнееПауза CaptureSession  Запись输出，避免 к т.е.将释放  Surface Отправка帧
     * 
     * @param cameraId 相机ID
     * @param currentSegmentIndex Текущий分索引（т.е.将завершить 分)
     */
    void onPrepareSegmentSwitch(String cameraId, int currentSegmentIndex);

    /**
     * 分切换（необходимо重新конфигурация相机会话)
     * @param cameraId 相机ID
     * @param newSegmentIndex 新 分索引
     * @param completedFilePath завершение ФайлПуть（Доступно于传输 до 最终каталог)
     */
    void onSegmentSwitch(String cameraId, int newSegmentIndex, String completedFilePath);

    /**
     * 损坏Файл 删除
     * @param cameraId 相机ID
     * @param deletedFiles  删除 Файл名列表
     */
    void onCorruptedFilesDeleted(String cameraId, List<String> deletedFiles);

    /**
     * 求重建Запись（Watchdog 触发)
     * 当ОбнаруженоЗаписьаномалия（连续无写入или首 раз写入таймаут)时调用
     * Внешнее应该ОстановкаТекущийЗапись并重新Вкл始，可选切换 до  Codec режим
     * 
     * @param cameraId 相机ID
     * @param reason 重建原因（"no_write" или "first_write_timeout")
     */
    void onRecordingRebuildRequested(String cameraId, String reason);

    /**
     * 首 раз数据写入Успешно
     * 当ОбнаруженоЗапись器首 разУспешно写入数据时调用
     * 用于УведомлениеВнешнееЗапись真正Вкл始，可以Вкл始计时（分计时、DingTalkЗапись计时等)
     * 
     * @param cameraId 相机ID
     */
    void onFirstDataWritten(String cameraId);
}
