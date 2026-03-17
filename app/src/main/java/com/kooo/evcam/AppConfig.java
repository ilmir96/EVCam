package com.kooo.evcam;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Приложениеконфигурацияуправление类
 * управлениеПриложение级别 конфигурация项
 */
public class AppConfig {
    private static final String TAG = "AppConfig";
    private static final String PREF_NAME = "app_config";
    
    // конфигурация项键名
    private static final String KEY_FIRST_LAUNCH = "first_launch";  // 首 разЗапуск标记
    private static final String KEY_DEVICE_NICKNAME = "device_nickname";  // 设备识别名称（用于 д.志传)
    private static final String KEY_AUTO_START_ON_BOOT = "auto_start_on_boot";  // Вкл机自Запуск
    private static final String KEY_AUTO_START_RECORDING = "auto_start_recording";  // ЗапускавтоматическиЗапись
    private static final String KEY_SCREEN_OFF_RECORDING = "screen_off_recording";  // 息屏Запись（锁车Запись)
    private static final String KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled";  // 保活Сервис
    private static final String KEY_PREVENT_SLEEP_ENABLED = "prevent_sleep_enabled";  // 防止休眠（持续WakeLock)
    private static final String KEY_RECORDING_MODE = "recording_mode";  // Записьрежим
    
    // ХранилищеПозицияконфигурация
    private static final String KEY_STORAGE_LOCATION = "storage_location";  // ХранилищеПозиция
    private static final String KEY_CUSTOM_SD_CARD_PATH = "custom_sd_card_path";  // вручнуюНастройки USB-накопительПуть
    private static final String KEY_CUSTOM_STORAGE_PATH = "custom_storage_path";  // Произвольный путь хранения
    private static final String KEY_CUSTOM_STORAGE_URI = "custom_storage_uri";  // SAF URI для выбранной папки
    private static final String KEY_LAST_DETECTED_SD_PATH = "last_detected_sd_path";  //  разавтоматическиОбнаружено USB-накопительПуть（缓存)
    
    // ХранилищеПозиция常量
    public static final String STORAGE_INTERNAL = "internal";  // Внутренняя память（По умолчанию)
    public static final String STORAGE_EXTERNAL_SD = "external_sd";  // USB-накопитель
    public static final String STORAGE_CUSTOM = "custom";  // Произвольный путь
    
    // USB-накопитель回退Уведомление标志（每 раз冷Запуск后Сброс)
    private static boolean sdFallbackShownThisSession = false;
    
    // 悬浮窗конфигурация
    private static final String KEY_FLOATING_WINDOW_ENABLED = "floating_window_enabled";  // 悬浮窗ВклВыкл
    private static final String KEY_FLOATING_WINDOW_SIZE = "floating_window_size";  // 悬浮窗大小
    private static final String KEY_FLOATING_WINDOW_ALPHA = "floating_window_alpha";  // 悬浮窗透明度
    private static final String KEY_FLOATING_WINDOW_X = "floating_window_x";  // 悬浮窗XПозиция
    private static final String KEY_FLOATING_WINDOW_Y = "floating_window_y";  // 悬浮窗YПозиция
    
    // ХранилищеОчистка конфигурация
    private static final String KEY_VIDEO_STORAGE_LIMIT_GB = "video_storage_limit_gb";  // ВидеоХранилище限制（GB)
    private static final String KEY_PHOTO_STORAGE_LIMIT_GB = "photo_storage_limit_gb";  // ИзображениеХранилище限制（GB)
    
    // 分Записьконфигурация
    private static final String KEY_SEGMENT_DURATION_MINUTES = "segment_duration_minutes";  // 分时长（ мин.)
    
    // ЗаписьСтатус显示конфигурация
    private static final String KEY_RECORDING_STATS_ENABLED = "recording_stats_enabled";  // ЗаписьСтатус显示ВклВыкл
    
    // 补盲функция全局ВклВыкл
    private static final String KEY_BLIND_SPOT_GLOBAL_ENABLED = "blind_spot_global_enabled";  // 补盲функция总ВклВыкл
    
    // 补盲选项конфигурация (原副屏显示)
    private static final String KEY_SECONDARY_DISPLAY_ENABLED = "secondary_display_enabled";  // 副屏显示ВклВыкл
    private static final String KEY_SECONDARY_DISPLAY_CAMERA = "secondary_display_camera";    // 副屏显示 КамераПозиция
    private static final String KEY_SECONDARY_DISPLAY_ID = "secondary_display_id";            // 副屏 Display ID
    private static final String KEY_SECONDARY_DISPLAY_X = "secondary_display_x";              // 副屏ПозицияX
    private static final String KEY_SECONDARY_DISPLAY_Y = "secondary_display_y";              // 副屏ПозицияY
    private static final String KEY_SECONDARY_DISPLAY_WIDTH = "secondary_display_width";      // 副屏宽度
    private static final String KEY_SECONDARY_DISPLAY_HEIGHT = "secondary_display_height";    // 副屏Высокий度
    private static final String KEY_SECONDARY_DISPLAY_ROTATION = "secondary_display_rotation"; // 副屏Поворот 角度
    private static final String KEY_SECONDARY_DISPLAY_BORDER = "secondary_display_border";    //  否显示白边框
    private static final String KEY_SECONDARY_DISPLAY_ORIENTATION = "secondary_display_orientation"; // 屏幕方 к （0/90/180/270)
    private static final String KEY_SECONDARY_DISPLAY_ALPHA = "secondary_display_alpha"; // 副屏补盲悬浮窗透明度（0-100)

    // 主屏悬浮窗конфигурация (补盲选项新增)
    private static final String KEY_MAIN_FLOATING_ENABLED = "main_floating_enabled";          // 主屏悬浮窗ВклВыкл
    private static final String KEY_MAIN_FLOATING_CAMERA = "main_floating_camera";            // 主屏悬浮窗Камера
    private static final String KEY_MAIN_FLOATING_X = "main_floating_x";                      // 主屏悬浮窗XПозиция
    private static final String KEY_MAIN_FLOATING_Y = "main_floating_y";                      // 主屏悬浮窗YПозиция
    private static final String KEY_MAIN_FLOATING_WIDTH = "main_floating_width";              // 主屏悬浮窗宽度
    private static final String KEY_MAIN_FLOATING_HEIGHT = "main_floating_height";            // 主屏悬浮窗Высокий度

    // 转 к 灯联动конфигурация (补盲选项新增)
    private static final String KEY_TURN_SIGNAL_LINKAGE_ENABLED = "turn_signal_linkage_enabled"; // 转 к 灯联动ВклВыкл
    private static final String KEY_TURN_SIGNAL_TIMEOUT = "turn_signal_timeout";               // 转 к 灯熄灭后延迟消失时间 ( сек.)
    
    // 车门联动конфигурация
    private static final String KEY_DOOR_LINKAGE_ENABLED = "door_linkage_enabled";             // 车门联动ВклВыкл
    private static final String KEY_DOOR_TIMEOUT = "door_timeout";                             // 车门Закрыто后延迟消失时间 ( сек.)
    private static final String KEY_DOOR_PRESET_SELECTION = "door_preset_selection";           // 车门联动车型Выбрать (l6l7/boyue_l)
    private static final String KEY_DOOR_REUSE_MAIN_FLOATING = "door_reuse_main_floating";     // 车门联动 否复用主屏悬浮窗
    private static final String KEY_DOOR_SECONDARY_DISPLAY_ENABLED = "door_secondary_display_enabled"; // 车门联动副屏显示ВклВыкл
    
    private static final String KEY_TURN_SIGNAL_REUSE_MAIN_FLOATING = "turn_signal_reuse_main_floating"; //  否复用主屏悬浮窗
    private static final String KEY_TURN_SIGNAL_FLOATING_X = "turn_signal_floating_x";          // 独立补盲悬浮窗X
    private static final String KEY_TURN_SIGNAL_FLOATING_Y = "turn_signal_floating_y";          // 独立补盲悬浮窗Y
    private static final String KEY_TURN_SIGNAL_FLOATING_WIDTH = "turn_signal_floating_width";  // 独立补盲悬浮窗宽度
    private static final String KEY_TURN_SIGNAL_FLOATING_HEIGHT = "turn_signal_floating_height"; // 独立补盲悬浮窗Высокий度
    private static final String KEY_TURN_SIGNAL_FLOATING_ROTATION = "turn_signal_floating_rotation"; // 独立补盲悬浮窗Поворот 
    private static final String KEY_TURN_SIGNAL_CUSTOM_LEFT_TRIGGER_LOG = "turn_signal_custom_left_trigger_log"; // 左转 к 灯触发logВыкл键字
    private static final String KEY_TURN_SIGNAL_CUSTOM_RIGHT_TRIGGER_LOG = "turn_signal_custom_right_trigger_log"; // 右转 к 灯触发logВыкл键字
    private static final String KEY_TURN_SIGNAL_TRIGGER_MODE = "turn_signal_trigger_mode"; // 转 к 灯触发режим
    private static final String KEY_TURN_SIGNAL_PRESET_SELECTION = "turn_signal_preset_selection"; // 用户Выбрать 预设选项（博越L/L6L7等)

    // 转 к 灯触发режим常量
    public static final String TRIGGER_MODE_LOGCAT = "logcat";            // Logcat  д.志触发
    public static final String TRIGGER_MODE_VHAL_GRPC = "vhal_grpc";      // 车辆API 触发（GalaxyE5/26 Starship7，По умолчанию)
    public static final String TRIGGER_MODE_CAR_SIGNAL_MANAGER = "car_signal_manager"; // CarSignalManager API 触发（GalaxyL6/L7)
    
    // совместимость性别名（保持 к 后совместимость)
    public static final String TRIGGER_MODE_CAR_API = TRIGGER_MODE_VHAL_GRPC;

    // 全景影像避让конфигурация
    private static final String KEY_AVM_AVOIDANCE_ENABLED = "avm_avoidance_enabled";  // 全景影像避让ВклВыкл
    private static final String KEY_AVM_AVOIDANCE_ACTIVITY = "avm_avoidance_activity"; // 全景影像避让 Activity名

    // 定制键唤醒конфигурация
    private static final String KEY_CUSTOM_KEY_WAKEUP_ENABLED = "custom_key_wakeup_enabled"; // 定制键唤醒ВклВыкл
    private static final String KEY_CUSTOM_KEY_SPEED_THRESHOLD = "custom_key_speed_threshold"; // 速度阈值（ сек.速 m/s)
    private static final String KEY_CUSTOM_KEY_SPEED_PROP_ID = "custom_key_speed_prop_id"; // 速度属性ID
    private static final String KEY_CUSTOM_KEY_BUTTON_PROP_ID = "custom_key_button_prop_id"; // 按钮属性ID

    // 桌面悬浮模拟按钮 (补盲选项新增)
    private static final String KEY_MOCK_TURN_SIGNAL_FLOATING_ENABLED = "mock_turn_signal_floating_enabled"; // 悬浮模拟按钮ВклВыкл
    private static final String KEY_MOCK_TURN_SIGNAL_FLOATING_X = "mock_turn_signal_floating_x";             // 悬浮模拟按钮X
    private static final String KEY_MOCK_TURN_SIGNAL_FLOATING_Y = "mock_turn_signal_floating_y";             // 悬浮模拟按钮Y

    // 补盲悬浮窗动效
    private static final String KEY_FLOATING_WINDOW_ANIMATION_ENABLED = "floating_window_animation_enabled"; // 悬浮窗Вкл启/Закрыто动效
    private static final String KEY_BLIND_SPOT_STATUS_BAR_STYLE = "blind_spot_status_bar_style";             // Статус栏动效样式 (0=Выкл, 1-5=五种动效)
    private static final String KEY_BLIND_SPOT_STATUS_BAR_COLOR = "blind_spot_status_bar_color";             // Статус栏动效颜色 (ARGB int)
    private static final String KEY_BLIND_SPOT_STATUS_BAR_BG_OPACITY = "blind_spot_status_bar_bg_opacity";   // Статус栏底色不透明度 0-100

    // 主屏悬浮窗比例锁定
    private static final String KEY_MAIN_FLOATING_ASPECT_RATIO_LOCKED = "main_floating_aspect_ratio_locked";

    // 主屏悬浮窗长按拖动
    private static final String KEY_MAIN_FLOATING_LONG_PRESS_DRAG = "main_floating_long_press_drag";

    // 补盲画面矫正 (Matrix)
    private static final String KEY_BLIND_SPOT_CORRECTION_ENABLED = "blind_spot_correction_enabled";
    private static final String KEY_BLIND_SPOT_CORRECTION_PREFIX = "blind_spot_correction_";
    private static final String KEY_BLIND_SPOT_DISCLAIMER_ACCEPTED = "blind_spot_disclaimer_accepted";
    
    // 预览画面矫正конфигурация
    private static final String KEY_PREVIEW_CORRECTION_ENABLED = "preview_correction_enabled";
    private static final String KEY_PREVIEW_CORRECTION_PREFIX = "preview_correction_";

    // 鱼眼矫正конфигурация
    private static final String KEY_FISHEYE_CORRECTION_ENABLED = "fisheye_correction_enabled";
    private static final String KEY_FISHEYE_CORRECTION_PREFIX = "fisheye_correction_";

    // 时间角标конфигурация
    private static final String KEY_TIMESTAMP_WATERMARK_ENABLED = "timestamp_watermark_enabled";  // 时间角标ВклВыкл
    
    // ЗаписьКамераВыбратьконфигурация
    private static final String KEY_RECORDING_CAMERA_FRONT_ENABLED = "recording_camera_front_enabled";  // 前Камера参 и Запись
    private static final String KEY_RECORDING_CAMERA_BACK_ENABLED = "recording_camera_back_enabled";    // Задняя камера参 и Запись
    private static final String KEY_RECORDING_CAMERA_LEFT_ENABLED = "recording_camera_left_enabled";    // Левая камера参 и Запись
    private static final String KEY_RECORDING_CAMERA_RIGHT_ENABLED = "recording_camera_right_enabled";  // Правая камера参 и Запись
    
    // 亮度/Шумоподавление调节конфигурация
    private static final String KEY_IMAGE_ADJUST_ENABLED = "image_adjust_enabled";  //  否Включить亮度/Шумоподавление调节
    private static final String KEY_EXPOSURE_COMPENSATION = "exposure_compensation";  // Экспозиция值
    private static final String KEY_AWB_MODE = "awb_mode";  // Баланс белогорежим
    private static final String KEY_TONEMAP_MODE = "tonemap_mode";  // Тональная компрессиярежим
    private static final String KEY_EDGE_MODE = "edge_mode";  // Резкостьрежим
    private static final String KEY_NOISE_REDUCTION_MODE = "noise_reduction_mode";  // Шумоподавлениережим
    private static final String KEY_EFFECT_MODE = "effect_mode";  // Эффектырежим
    private static final String KEY_SCENE_MODE = "scene_mode";  // 场景режим
    
    // Баланс белогорежим常量（ 应 CameraMetadata.CONTROL_AWB_MODE_*)
    public static final int AWB_MODE_DEFAULT = -1;  // По умолчанию（不Настройки)
    public static final int AWB_MODE_AUTO = 1;  // автоматически
    public static final int AWB_MODE_INCANDESCENT = 2;  // 白炽灯
    public static final int AWB_MODE_FLUORESCENT = 3;  // 荧光灯
    public static final int AWB_MODE_WARM_FLUORESCENT = 4;  // 暖荧光灯
    public static final int AWB_MODE_DAYLIGHT = 5;  //  д.光
    public static final int AWB_MODE_CLOUDY_DAYLIGHT = 6;  // 阴天
    public static final int AWB_MODE_TWILIGHT = 7;  // 黄昏
    public static final int AWB_MODE_SHADE = 8;  // 阴影
    
    // Тональная компрессиярежим常量（ 应 CameraMetadata.TONEMAP_MODE_*)
    public static final int TONEMAP_MODE_DEFAULT = -1;  // По умолчанию（不Настройки)
    public static final int TONEMAP_MODE_CONTRAST_CURVE = 0;  //  比度曲线
    public static final int TONEMAP_MODE_FAST = 1;  // 快速
    public static final int TONEMAP_MODE_HIGH_QUALITY = 2;  // Высокий质量
    
    // Резкостьрежим常量（ 应 CameraMetadata.EDGE_MODE_*)
    public static final int EDGE_MODE_DEFAULT = -1;  // По умолчанию（不Настройки)
    public static final int EDGE_MODE_OFF = 0;  // Закрыто
    public static final int EDGE_MODE_FAST = 1;  // 快速
    public static final int EDGE_MODE_HIGH_QUALITY = 2;  // Высокий质量
    
    // Шумоподавлениережим常量（ 应 CameraMetadata.NOISE_REDUCTION_MODE_*)
    public static final int NOISE_REDUCTION_DEFAULT = -1;  // По умолчанию（不Настройки)
    public static final int NOISE_REDUCTION_OFF = 0;  // Закрыто
    public static final int NOISE_REDUCTION_FAST = 1;  // 快速
    public static final int NOISE_REDUCTION_HIGH_QUALITY = 2;  // Высокий质量
    
    // Эффектырежим常量（ 应 CameraMetadata.CONTROL_EFFECT_MODE_*)
    public static final int EFFECT_MODE_DEFAULT = -1;  // По умолчанию（不Настройки)
    public static final int EFFECT_MODE_OFF = 0;  // Закрыто
    public static final int EFFECT_MODE_MONO = 1;  // 黑白
    public static final int EFFECT_MODE_NEGATIVE = 2;  // 负片
    public static final int EFFECT_MODE_SOLARIZE = 3;  // 曝光过度
    public static final int EFFECT_MODE_SEPIA = 4;  // 怀旧
    public static final int EFFECT_MODE_AQUA = 6;  // 水蓝
    
    // 分时长常量（ мин.)
    public static final int SEGMENT_DURATION_1_MIN = 1;
    public static final int SEGMENT_DURATION_3_MIN = 3;
    public static final int SEGMENT_DURATION_5_MIN = 5;
    
    // 悬浮窗大小常量
    public static final int FLOATING_SIZE_TINY = 32;        // 超小
    public static final int FLOATING_SIZE_EXTRA_SMALL = 40; // XS
    public static final int FLOATING_SIZE_SMALL = 48;       // 小
    public static final int FLOATING_SIZE_MEDIUM = 64;      // 
    public static final int FLOATING_SIZE_LARGE = 80;       // 大
    public static final int FLOATING_SIZE_EXTRA_LARGE = 96; // 超大
    public static final int FLOATING_SIZE_HUGE = 112;       // XL
    public static final int FLOATING_SIZE_GIANT = 128;      // XL
    public static final int FLOATING_SIZE_PLUS = 144;       // PLUS
    public static final int FLOATING_SIZE_MAX = 160;        // MAX
    
    // Записьрежим常量
    public static final String RECORDING_MODE_AUTO = "auto";  // автоматически（根据车型决定)
    public static final String RECORDING_MODE_MEDIA_RECORDER = "media_recorder";  // MediaRecorder（硬件编码)
    public static final String RECORDING_MODE_CODEC = "codec";  // MediaCodec（软编码)
    
    // Разрешениеконфигурация相Выкл键名
    private static final String KEY_TARGET_RESOLUTION = "target_resolution";  // 目标Разрешение
    
    // Разрешение常量
    public static final String RESOLUTION_DEFAULT = "default";  // По умолчанию（优先1280x800)
    
    // 码率конфигурация相Выкл键名
    private static final String KEY_BITRATE_LEVEL = "bitrate_level";  // Уровень битрейта
    
    // Уровень битрейта常量
    public static final String BITRATE_LOW = "low";        // Низкий码率（计算值 50%)
    public static final String BITRATE_MEDIUM = "medium";  // 码率（计算值，По умолчанию)
    public static final String BITRATE_HIGH = "high";      // Высокий码率（计算值 150%)
    
    // 帧率конфигурация相Выкл键名
    private static final String KEY_FRAMERATE_LEVEL = "framerate_level";  // Уровень частоты кадров
    
    // Уровень частоты кадров常量
    public static final String FRAMERATE_STANDARD = "standard";  // Стандарт帧率（По умолчанию)
    public static final String FRAMERATE_LOW = "low";            // Низкий帧率（Стандарт值 一半)
    
    // 车型конфигурация相Выкл键名
    private static final String KEY_CAR_MODEL = "car_model";  // 车型（galaxy_e5 / custom)
    private static final String KEY_CAMERA_COUNT = "camera_count";  // Камера数量（4/2/1)
    private static final String KEY_SCREEN_ORIENTATION = "screen_orientation";  // 屏幕方 к （landscape/portrait，только4Камера时действует)
    private static final String KEY_CAMERA_FRONT_ID = "camera_front_id";  // 前Камера编号
    private static final String KEY_CAMERA_BACK_ID = "camera_back_id";  // Задняя камера编号
    private static final String KEY_CAMERA_LEFT_ID = "camera_left_id";  // Левая камера编号
    private static final String KEY_CAMERA_RIGHT_ID = "camera_right_id";  // Правая камера编号
    private static final String KEY_CAMERA_FRONT_NAME = "camera_front_name";  // 前Камера名称
    private static final String KEY_CAMERA_BACK_NAME = "camera_back_name";  // Задняя камера名称
    private static final String KEY_CAMERA_LEFT_NAME = "camera_left_name";  // Левая камера名称
    private static final String KEY_CAMERA_RIGHT_NAME = "camera_right_name";  // Правая камера名称
    private static final String KEY_CAMERA_FRONT_ROTATION = "camera_front_rotation";  // 前КамераПоворот 角度
    private static final String KEY_CAMERA_BACK_ROTATION = "camera_back_rotation";  // Задняя камераПоворот 角度
    private static final String KEY_CAMERA_LEFT_ROTATION = "camera_left_rotation";  // Левая камераПоворот 角度
    private static final String KEY_CAMERA_RIGHT_ROTATION = "camera_right_rotation";  // Правая камераПоворот 角度
    private static final String KEY_CAMERA_FRONT_MIRROR = "camera_front_mirror";  // 前Камера镜像
    private static final String KEY_CAMERA_BACK_MIRROR = "camera_back_mirror";  // Задняя камера镜像
    private static final String KEY_CAMERA_LEFT_MIRROR = "camera_left_mirror";  // Левая камера镜像
    private static final String KEY_CAMERA_RIGHT_MIRROR = "camera_right_mirror";  // Правая камера镜像
    
    // Камера裁剪конфигурация（每 шт.方 к  裁剪像素值)
    private static final String KEY_CAMERA_CROP_PREFIX = "camera_crop_";  // 裁剪конфигурация前缀
    
    // Своя модель自由操控конфигурация
    private static final String KEY_CUSTOM_FREE_CONTROL_ENABLED = "custom_free_control_enabled";  // 自由操控ВклВыкл
    private static final String KEY_CUSTOM_BUTTON_STYLE = "custom_button_style";  // 按钮样式（standard/multi)
    private static final String KEY_CUSTOM_BUTTON_ORIENTATION = "custom_button_orientation";  // 按钮布局方 к （horizontal/vertical)
    private static final String KEY_CUSTOM_LAYOUT_DATA = "custom_layout_data";  // 布局Позиция数据（JSON格式)

    // 前轮/后轮режим视图конфигурация（用于Своя модель)
    private static final String KEY_FRONT_WHEEL_LEFT_WIDTH = "front_wheel_left_width";
    private static final String KEY_FRONT_WHEEL_LEFT_HEIGHT = "front_wheel_left_height";
    private static final String KEY_FRONT_WHEEL_LEFT_X = "front_wheel_left_x";
    private static final String KEY_FRONT_WHEEL_LEFT_Y = "front_wheel_left_y";
    private static final String KEY_FRONT_WHEEL_LEFT_ROTATION = "front_wheel_left_rotation";
    private static final String KEY_FRONT_WHEEL_RIGHT_WIDTH = "front_wheel_right_width";
    private static final String KEY_FRONT_WHEEL_RIGHT_HEIGHT = "front_wheel_right_height";
    private static final String KEY_FRONT_WHEEL_RIGHT_X = "front_wheel_right_x";
    private static final String KEY_FRONT_WHEEL_RIGHT_Y = "front_wheel_right_y";
    private static final String KEY_FRONT_WHEEL_RIGHT_ROTATION = "front_wheel_right_rotation";

    private static final String KEY_REAR_WHEEL_LEFT_WIDTH = "rear_wheel_left_width";
    private static final String KEY_REAR_WHEEL_LEFT_HEIGHT = "rear_wheel_left_height";
    private static final String KEY_REAR_WHEEL_LEFT_X = "rear_wheel_left_x";
    private static final String KEY_REAR_WHEEL_LEFT_Y = "rear_wheel_left_y";
    private static final String KEY_REAR_WHEEL_LEFT_ROTATION = "rear_wheel_left_rotation";
    private static final String KEY_REAR_WHEEL_RIGHT_WIDTH = "rear_wheel_right_width";
    private static final String KEY_REAR_WHEEL_RIGHT_HEIGHT = "rear_wheel_right_height";
    private static final String KEY_REAR_WHEEL_RIGHT_X = "rear_wheel_right_x";
    private static final String KEY_REAR_WHEEL_RIGHT_Y = "rear_wheel_right_y";
    private static final String KEY_REAR_WHEEL_RIGHT_ROTATION = "rear_wheel_right_rotation";

    // 普通режим（По умолчаниюрежим) 视图конфигурация
    private static final String KEY_NORMAL_LEFT_WIDTH = "normal_left_width";
    private static final String KEY_NORMAL_LEFT_HEIGHT = "normal_left_height";
    private static final String KEY_NORMAL_LEFT_X = "normal_left_x";
    private static final String KEY_NORMAL_LEFT_Y = "normal_left_y";
    private static final String KEY_NORMAL_LEFT_ROTATION = "normal_left_rotation";
    private static final String KEY_NORMAL_RIGHT_WIDTH = "normal_right_width";
    private static final String KEY_NORMAL_RIGHT_HEIGHT = "normal_right_height";
    private static final String KEY_NORMAL_RIGHT_X = "normal_right_x";
    private static final String KEY_NORMAL_RIGHT_Y = "normal_right_y";
    private static final String KEY_NORMAL_RIGHT_ROTATION = "normal_right_rotation";
    
    // 按钮样式常量
    public static final String BUTTON_STYLE_STANDARD = "standard";  // Стандартные кнопки（E5风格)
    public static final String BUTTON_STYLE_MULTI = "multi";        // Мульти-кнопки（L7-Мульти-кнопки风格)
    
    // 按钮方 к 常量
    public static final String BUTTON_ORIENTATION_HORIZONTAL = "horizontal";  // Горизонтальная
    public static final String BUTTON_ORIENTATION_VERTICAL = "vertical";      // Вертикальная
    
    // 版本обновлениеконфигурация
    private static final String KEY_UPDATE_SERVER_URL = "update_server_url";  // обновлениеАдрес сервера
    private static final String DEFAULT_UPDATE_SERVER_URL = "https://api.github.com/repos/ilmir96/EVCam/releases/latest";  // GitHub Releases API
    
    // 车型常量
    public static final String CAR_MODEL_GALAXY_E5 = "galaxy_e5";  // GalaxyE5
    public static final String CAR_MODEL_E5_MULTI = "galaxy_e5_multi";  // GalaxyE5-Мульти-кнопки
    public static final String CAR_MODEL_L7 = "galaxy_l7";  // GalaxyL6/L7
    public static final String CAR_MODEL_L7_MULTI = "galaxy_l7_multi";  // GalaxyL7-Мульти-кнопки
    public static final String CAR_MODEL_PHONE = "phone";  // Телефон
    public static final String CAR_MODEL_CUSTOM = "custom";  // Своя модель
    public static final String CAR_MODEL_XINGHAN_7 = "xinghan_7";  // 26 Starship7
    public static final String CAR_MODEL_MULTIVIEW = "multiview";  // Мульти-камерный вид
    
    private final SharedPreferences prefs;
    
    public AppConfig(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    // ==================== 首 разЗапуск相Выкл方法 ====================
    
    /**
     * проверка 否为首 разЗапуск
     * @return true 表示首 разЗапуск（新установка后Первый разоткрыть)
     */
    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }
    
    /**
     * 标记首 разЗапускзавершение
     */
    public void setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        AppLog.d(TAG, "首 разЗапуск标记Настройки为завершение");
    }
    
    // ==================== 设备识别名称相Выкл方法 ====================
    
    /**
     * Получение设备识别名称（用于 д.志传)
     * @return 设备名称，Если Не НастройкиВозвращает null
     */
    public String getDeviceNickname() {
        return prefs.getString(KEY_DEVICE_NICKNAME, null);
    }
    
    /**
     * Настройки设备识别名称
     * @param nickname 设备名称
     */
    public void setDeviceNickname(String nickname) {
        prefs.edit().putString(KEY_DEVICE_NICKNAME, nickname).apply();
        AppLog.d(TAG, "设备识别名称Настройки: " + nickname);
    }
    
    /**
     * проверка 否Настройки设备识别名称
     * @return true 表示Настройки
     */
    public boolean hasDeviceNickname() {
        String nickname = getDeviceNickname();
        return nickname != null && !nickname.trim().isEmpty();
    }
    
    // ==================== Вкл机自Запуск相Выкл方法 ====================
    
    /**
     * НастройкиВкл机自Запуск
     * @param enabled true 表示ВключитьВкл机自Запуск
     */
    public void setAutoStartOnBoot(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_START_ON_BOOT, enabled).apply();
        AppLog.d(TAG, "Вкл机自ЗапускНастройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * ПолучениеВкл机自ЗапускНастройки
     * @return true 表示ВключитьВкл机自Запуск
     */
    public boolean isAutoStartOnBoot() {
        // По умолчаниюВключитьВкл机自Запуск（车机Система场景)
        return prefs.getBoolean(KEY_AUTO_START_ON_BOOT, true);
    }
    
    /**
     * НастройкиЗапускавтоматическиЗапись
     * @param enabled true 表示ВключитьЗапускавтоматическиЗапись
     */
    public void setAutoStartRecording(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_START_RECORDING, enabled).apply();
        AppLog.d(TAG, "ЗапускавтоматическиЗаписьНастройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * ПолучениеЗапускавтоматическиЗаписьНастройки
     * @return true 表示ВключитьЗапускавтоматическиЗапись
     */
    public boolean isAutoStartRecording() {
        // По умолчаниюОтключитьЗапускавтоматическиЗапись（необходимо用户主动Вкл启)
        return prefs.getBoolean(KEY_AUTO_START_RECORDING, false);
    }
    
    /**
     * Настройки息屏Запись（锁车Запись)
     * @param enabled true 表示息屏时продолжитьЗапись
     */
    public void setScreenOffRecordingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SCREEN_OFF_RECORDING, enabled).apply();
        AppLog.d(TAG, "息屏ЗаписьНастройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение息屏ЗаписьНастройки
     * @return true 表示息屏时продолжитьЗапись
     */
    public boolean isScreenOffRecordingEnabled() {
        // По умолчаниюОтключить息屏Запись
        return prefs.getBoolean(KEY_SCREEN_OFF_RECORDING, false);
    }
    
    /**
     * Настройки保活Сервис
     * @param enabled true 表示Включить保活Сервис
     */
    public void setKeepAliveEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled).apply();
        AppLog.d(TAG, "保活СервисНастройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение保活СервисНастройки
     * @return true 表示Включить保活Сервис
     */
    public boolean isKeepAliveEnabled() {
        // По умолчаниюВключить保活Сервис
        return prefs.getBoolean(KEY_KEEP_ALIVE_ENABLED, true);
    }
    
    /**
     * Настройки防止休眠（持续WakeLock)
     * @param enabled true 表示Включить防止休眠
     */
    public void setPreventSleepEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREVENT_SLEEP_ENABLED, enabled).apply();
        AppLog.d(TAG, "防止休眠Настройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение防止休眠Настройки
     * @return true 表示Включить防止休眠
     */
    public boolean isPreventSleepEnabled() {
        // 车机ПриложениеПо умолчаниюВключить防止休眠
        // 原因：1. 车机использование车辆供电，不影响电池
        //       2. КамераПриложениенеобходимо 息屏时продолжитьЗапись
        //       3. Удалённое управлениенеобходимоФоновый режимРабота
        return prefs.getBoolean(KEY_PREVENT_SLEEP_ENABLED, true);
    }
    
    /**
     * НастройкиЗаписьрежим
     * @param mode Записьрежим（auto/media_recorder/codec)
     */
    public void setRecordingMode(String mode) {
        prefs.edit().putString(KEY_RECORDING_MODE, mode).apply();
        AppLog.d(TAG, "ЗаписьрежимНастройки: " + mode);
    }
    
    /**
     * ПолучениеЗаписьрежим
     * @return Записьрежим，По умолчанию为автоматически
     */
    public String getRecordingMode() {
        return prefs.getString(KEY_RECORDING_MODE, RECORDING_MODE_AUTO);
    }
    
    /**
     * 判断 否应该использование Codec Записьрежим
     * @return true 表示应该использование CodecVideoRecorder
     */
    public boolean shouldUseCodecRecording() {
        String mode = getRecordingMode();
        if (RECORDING_MODE_CODEC.equals(mode)) {
            // 强制использование Codec режим
            return true;
        } else if (RECORDING_MODE_MEDIA_RECORDER.equals(mode)) {
            // 强制использование MediaRecorder режим
            return false;
        } else {
            // автоматическирежим：所有车型По умолчаниюиспользование MediaCodec режим
            return true;
        }
    }
    
    /**
     * Сброс所有конфигурация为По умолчанию值
     */
    public void resetToDefault() {
        prefs.edit().clear().apply();
        AppLog.d(TAG, "конфигурацияСброс为По умолчанию值");
    }
    
    // ==================== Разрешениеконфигурация相Выкл方法 ====================
    
    /**
     * Настройки目标Разрешение
     * @param resolution Разрешение字符串（если "1280x720")или "default"
     */
    public void setTargetResolution(String resolution) {
        prefs.edit().putString(KEY_TARGET_RESOLUTION, resolution).apply();
        AppLog.d(TAG, "目标РазрешениеНастройки: " + resolution);
    }
    
    /**
     * Получение目标Разрешение
     * @return Разрешение字符串，По умолчанию为 "default"
     */
    public String getTargetResolution() {
        return prefs.getString(KEY_TARGET_RESOLUTION, RESOLUTION_DEFAULT);
    }
    
    /**
     *  否использованиеПо умолчаниюРазрешение
     */
    public boolean isDefaultResolution() {
        return RESOLUTION_DEFAULT.equals(getTargetResolution());
    }
    
    /**
     * 解析Разрешение字符串为宽Высокий数 групп
     * @param resolution Разрешение字符串（если "1280x720")
     * @return [width, height]，解析ОшибкаВозвращает null
     */
    public static int[] parseResolution(String resolution) {
        if (resolution == null || RESOLUTION_DEFAULT.equals(resolution)) {
            return null;
        }
        try {
            String[] parts = resolution.split("x");
            if (parts.length == 2) {
                int width = Integer.parseInt(parts[0].trim());
                int height = Integer.parseInt(parts[1].trim());
                return new int[]{width, height};
            }
        } catch (NumberFormatException e) {
            AppLog.w(TAG, "无法解析Разрешение: " + resolution);
        }
        return null;
    }
    
    // ==================== 码率конфигурация相Выкл方法 ====================
    
    /**
     * НастройкиУровень битрейта
     * @param level Уровень битрейта（low/medium/high)
     */
    public void setBitrateLevel(String level) {
        prefs.edit().putString(KEY_BITRATE_LEVEL, level).apply();
        AppLog.d(TAG, "Уровень битрейтаНастройки: " + level);
    }
    
    /**
     * ПолучениеУровень битрейта
     * @return Уровень битрейта，По умолчанию为 medium
     */
    public String getBitrateLevel() {
        return prefs.getString(KEY_BITRATE_LEVEL, BITRATE_MEDIUM);
    }
    
    /**
     * 根据Разрешение и 帧率计算码率（bps)
     * 公式：像素数 × 帧率 × 0.1
     * @param width 宽度
     * @param height Высокий度
     * @param frameRate 帧率
     * @return 计算出 码率（bps)
     */
    public static int calculateBitrate(int width, int height, int frameRate) {
        // 像素数 × 帧率 × 0.1
        long bitrate = (long) width * height * frameRate / 10;
        return (int) bitrate;
    }
    
    /**
     * 根据Текущие настройкиПолучение实际Приложение 码率（bps)
     * @param width 宽度
     * @param height Высокий度
     * @param frameRate 帧率
     * @return 实际码率（bps)
     */
    public int getActualBitrate(int width, int height, int frameRate) {
        int baseBitrate = calculateBitrate(width, height, frameRate);
        String level = getBitrateLevel();
        
        switch (level) {
            case BITRATE_LOW:
                // 50%，取整 до  0.5Mbps
                return roundToHalfMbps(baseBitrate / 2);
            case BITRATE_HIGH:
                // 150%，取整 до  0.5Mbps
                return roundToHalfMbps(baseBitrate * 3 / 2);
            case BITRATE_MEDIUM:
            default:
                // 100%，取整 до  0.5Mbps
                return roundToHalfMbps(baseBitrate);
        }
    }
    
    /**
     * 将码率四舍五入 до 最接近  0.5Mbps
     * @param bitrate 原始码率（bps)
     * @return 四舍五入后 码率（bps)
     */
    private static int roundToHalfMbps(int bitrate) {
        // 转换为 0.5Mbps  倍数
        int halfMbps = 500000;
        int rounded = ((bitrate + halfMbps / 2) / halfMbps) * halfMbps;
        // минимум 0.5Mbps，максимум 20Mbps
        return Math.max(halfMbps, Math.min(rounded, 20000000));
    }
    
    /**
     * ПолучениеУровень битрейта 显示名称
     */
    public static String getBitrateLevelDisplayName(String level) {
        switch (level) {
            case BITRATE_LOW:
                return "Низкое";
            case BITRATE_HIGH:
                return "Высокое";
            case BITRATE_MEDIUM:
            default:
                return "Стандарт";
        }
    }
    
    /**
     * 格式化码率为可读字符串
     * @param bitrate 码率（bps)
     * @return 格式化字符串，если "3.0 Mbps"
     */
    public static String formatBitrate(int bitrate) {
        float mbps = bitrate / 1000000f;
        if (mbps >= 1) {
            return String.format(java.util.Locale.getDefault(), "%.1f Mbps", mbps);
        } else {
            return String.format(java.util.Locale.getDefault(), "%d Kbps", bitrate / 1000);
        }
    }
    
    /**
     * 根据硬件максимум帧率计算Стандарт帧率（接近30fps 成倍降Низкий值)
     * @param hardwareMaxFps 硬件Поддерживаемые максимум帧率
     * @return Стандарт帧率
     */
    public static int getStandardFrameRate(int hardwareMaxFps) {
        if (hardwareMaxFps <= 0) {
            return 30;  // По умолчанию30fps
        }
        
        // Если 硬件帧率本身 30или接近30，直接использование
        if (hardwareMaxFps >= 25 && hardwareMaxFps <= 35) {
            return hardwareMaxFps;
        }
        
        // Если 超过30，降 до 30илии ниже 整数倍
        if (hardwareMaxFps > 35) {
            // 60fps -> 30fps, 120fps -> 30fps
            int divisor = (hardwareMaxFps + 29) / 30;  //  к 取整
            int result = hardwareMaxFps / divisor;
            // 确保结果 合理范围内
            return Math.max(15, Math.min(result, 30));
        }
        
        // Если ниже25，直接использование硬件帧率
        return hardwareMaxFps;
    }
    
    // ==================== 帧率конфигурация相Выкл方法 ====================
    
    /**
     * НастройкиУровень частоты кадров
     * @param level Уровень частоты кадров（standard/low)
     */
    public void setFramerateLevel(String level) {
        prefs.edit().putString(KEY_FRAMERATE_LEVEL, level).apply();
        AppLog.d(TAG, "Уровень частоты кадровНастройки: " + level);
    }
    
    /**
     * ПолучениеУровень частоты кадров
     * @return Уровень частоты кадров，По умолчанию为 standard
     */
    public String getFramerateLevel() {
        return prefs.getString(KEY_FRAMERATE_LEVEL, FRAMERATE_STANDARD);
    }
    
    /**
     * 根据конфигурация Уровень частоты кадровПолучение实际帧率
     * @param hardwareMaxFps 硬件Поддерживаемые максимум帧率
     * @return 实际использование 帧率
     */
    public int getActualFrameRate(int hardwareMaxFps) {
        int standardFps = getStandardFrameRate(hardwareMaxFps);
        String level = getFramerateLevel();
        
        if (FRAMERATE_LOW.equals(level)) {
            // Низкий帧率：Стандарт值除以2，最Низкий10fps
            return Math.max(10, standardFps / 2);
        }
        
        // Стандарт帧率
        return standardFps;
    }
    
    /**
     * ПолучениеУровень частоты кадров 显示名称
     */
    public static String getFramerateLevelDisplayName(String level) {
        if (FRAMERATE_LOW.equals(level)) {
            return "Низкое";
        }
        return "Стандарт";
    }
    
    // ==================== 车型конфигурация相Выкл方法 ====================
    
    /**
     * Настройки车型
     * @param carModel 车型标识（galaxy_e5 или custom)
     */
    public void setCarModel(String carModel) {
        prefs.edit().putString(KEY_CAR_MODEL, carModel).apply();
        AppLog.d(TAG, "车型Настройки: " + carModel);
    }
    
    /**
     * Получение车型
     * @return 车型标识，По умолчанию为GalaxyE5
     */
    public String getCarModel() {
        return prefs.getString(KEY_CAR_MODEL, CAR_MODEL_GALAXY_E5);
    }
    
    /**
     *  否为Своя модель
     */
    public boolean isCustomCarModel() {
        return CAR_MODEL_CUSTOM.equals(getCarModel());
    }
    
    /**
     *  否为Мульти-камерный вид
     */
    public boolean isMultiviewCarModel() {
        return CAR_MODEL_MULTIVIEW.equals(getCarModel());
    }
    
    /**
     *  否необходимо自定义布局управление器（Своя модель и 多视角всенеобходимо)
     */
    public boolean needsCustomLayoutManager() {
        return isCustomCarModel() || isMultiviewCarModel();
    }
    
    /**
     * НастройкиКамера数量
     * @param count Камера数量（4/2/1)
     */
    public void setCameraCount(int count) {
        prefs.edit().putInt(KEY_CAMERA_COUNT, count).apply();
        AppLog.d(TAG, "Камера数量Настройки: " + count);
    }
    
    /**
     * ПолучениеКамера数量
     *  于预设车型返回固定数量， 于Своя модель返回用户Настройки 数量
     * @return Камера数量
     */
    public int getCameraCount() {
        String carModel = getCarModel();
        // 预设车型返回固定 Камера数量
        switch (carModel) {
            case CAR_MODEL_PHONE:
                return 2;  // Телефон：2
            case CAR_MODEL_GALAXY_E5:
            case CAR_MODEL_E5_MULTI:
            case CAR_MODEL_L7:
            case CAR_MODEL_L7_MULTI:
            case CAR_MODEL_XINGHAN_7:
                return 4;  // GalaxyE5/L7/26 Starship7：4
            case CAR_MODEL_MULTIVIEW:
            case CAR_MODEL_CUSTOM:
            default:
                // Своя модельиспользование用户Настройки 数量
                return prefs.getInt(KEY_CAMERA_COUNT, 4);
        }
    }
    
    /**
     * Получение用户Настройки Камера数量（только用于Своя модель)
     * @return 用户Настройки Камера数量，По умолчанию为4
     */
    public int getCustomCameraCount() {
        return prefs.getInt(KEY_CAMERA_COUNT, 4);
    }
    
    /**
     * Настройки屏幕方 к （только4Камера时действует)
     * @param orientation 屏幕方 к （landscape/portrait)
     */
    public void setScreenOrientation(String orientation) {
        prefs.edit().putString(KEY_SCREEN_ORIENTATION, orientation).apply();
        AppLog.d(TAG, "屏幕方 к Настройки: " + orientation);
    }
    
    /**
     * Получение屏幕方 к （только4Камера时действует)
     * @return 屏幕方 к ，По умолчанию为横屏
     */
    public String getScreenOrientation() {
        return prefs.getString(KEY_SCREEN_ORIENTATION, "landscape");
    }
    
    /**
     * НастройкиКамера编号
     * @param position Позиция（front/back/left/right)
     * @param cameraId Камера编号
     */
    public void setCameraId(String position, String cameraId) {
        String key;
        switch (position) {
            case "front":
                key = KEY_CAMERA_FRONT_ID;
                break;
            case "back":
                key = KEY_CAMERA_BACK_ID;
                break;
            case "left":
                key = KEY_CAMERA_LEFT_ID;
                break;
            case "right":
                key = KEY_CAMERA_RIGHT_ID;
                break;
            default:
                AppLog.w(TAG, "Неизвестно КамераПозиция: " + position);
                return;
        }
        prefs.edit().putString(key, cameraId).apply();
        AppLog.d(TAG, "Камера编号Настройки: " + position + " = " + cameraId);
    }
    
    /**
     * ПолучениеКамера编号
     * @param position Позиция（front/back/left/right)
     * @return Камера编号，По умолчанию为 -1 表示автоматически检测
     */
    public String getCameraId(String position) {
        String key;
        String defaultValue;
        switch (position) {
            case "front":
                key = KEY_CAMERA_FRONT_ID;
                defaultValue = "2";  // GalaxyE5По умолчанию：前=2
                break;
            case "back":
                key = KEY_CAMERA_BACK_ID;
                defaultValue = "1";  // GalaxyE5По умолчанию：后=1
                break;
            case "left":
                key = KEY_CAMERA_LEFT_ID;
                defaultValue = "3";  // GalaxyE5По умолчанию：左=3
                break;
            case "right":
                key = KEY_CAMERA_RIGHT_ID;
                defaultValue = "0";  // GalaxyE5По умолчанию：右=0
                break;
            default:
                return "-1";
        }
        return prefs.getString(key, defaultValue);
    }
    
    /**
     * НастройкиКамера名称
     * @param position Позиция（front/back/left/right)
     * @param name Камера名称
     */
    public void setCameraName(String position, String name) {
        String key;
        switch (position) {
            case "front":
                key = KEY_CAMERA_FRONT_NAME;
                break;
            case "back":
                key = KEY_CAMERA_BACK_NAME;
                break;
            case "left":
                key = KEY_CAMERA_LEFT_NAME;
                break;
            case "right":
                key = KEY_CAMERA_RIGHT_NAME;
                break;
            default:
                AppLog.w(TAG, "Неизвестно КамераПозиция: " + position);
                return;
        }
        prefs.edit().putString(key, name).apply();
        AppLog.d(TAG, "Камера名称Настройки: " + position + " = " + name);
    }
    
    /**
     * ПолучениеКамера名称
     *  于预设车型返回По умолчанию名称， 于Своя модель返回用户Настройки 名称
     * @param position Позиция（front/back/left/right)
     * @return Камера名称
     */
    public String getCameraName(String position) {
        // 预设车型返回По умолчанию名称
        if (!isCustomCarModel()) {
            return getDefaultCameraName(position);
        }
        
        // Своя модель返回用户Настройки 名称
        String key;
        String defaultValue = getDefaultCameraName(position);
        switch (position) {
            case "front":
                key = KEY_CAMERA_FRONT_NAME;
                break;
            case "back":
                key = KEY_CAMERA_BACK_NAME;
                break;
            case "left":
                key = KEY_CAMERA_LEFT_NAME;
                break;
            case "right":
                key = KEY_CAMERA_RIGHT_NAME;
                break;
            default:
                return "Неизвестно";
        }
        return prefs.getString(key, defaultValue);
    }
    
    /**
     * Получение预设车型 По умолчаниюКамера名称
     * 新增预设车型时，Если 名称不同于По умолчанию值， 此添加
     * @param position Позиция（front/back/left/right)
     * @return По умолчанию名称
     */
    public String getDefaultCameraName(String position) {
        // По умолчанию名称（适用于大多数预设车型)
        switch (position) {
            case "front":
                return "П";
            case "back":
                return "З";
            case "left":
                return "Л";
            case "right":
                return "Пр";
            default:
                return "Неизвестно";
        }
    }

    /**
     * НастройкиКамераПоворот 角度（только用于Своя модель)
     * @param position Позиция（front/back/left/right)
     * @param rotation Поворот 角度（0/90/180/270)
     */
    public void setCameraRotation(String position, int rotation) {
        String key;
        switch (position) {
            case "front":
                key = KEY_CAMERA_FRONT_ROTATION;
                break;
            case "back":
                key = KEY_CAMERA_BACK_ROTATION;
                break;
            case "left":
                key = KEY_CAMERA_LEFT_ROTATION;
                break;
            case "right":
                key = KEY_CAMERA_RIGHT_ROTATION;
                break;
            default:
                AppLog.w(TAG, "Неизвестно КамераПозиция: " + position);
                return;
        }
        prefs.edit().putInt(key, rotation).apply();
        AppLog.d(TAG, "КамераПоворот 角度Настройки: " + position + " = " + rotation + "°");
    }

    /**
     * ПолучениеКамераПоворот 角度（только用于Своя модель)
     * @param position Позиция（front/back/left/right)
     * @return Поворот 角度，По умолчанию为0（不Поворот )
     */
    public int getCameraRotation(String position) {
        // Если 不 Своя модель，返回0（E5использование代码 固定Поворот )
        if (!isCustomCarModel()) {
            return 0;
        }

        String key;
        switch (position) {
            case "front":
                key = KEY_CAMERA_FRONT_ROTATION;
                break;
            case "back":
                key = KEY_CAMERA_BACK_ROTATION;
                break;
            case "left":
                key = KEY_CAMERA_LEFT_ROTATION;
                break;
            case "right":
                key = KEY_CAMERA_RIGHT_ROTATION;
                break;
            default:
                return 0;
        }
        return prefs.getInt(key, 0);
    }
    
    /**
     * НастройкиКамера镜像
     * @param position КамераПозиция（front/back/left/right)
     * @param mirror  否镜像
     */
    public void setCameraMirror(String position, boolean mirror) {
        String key;
        switch (position) {
            case "front":
                key = KEY_CAMERA_FRONT_MIRROR;
                break;
            case "back":
                key = KEY_CAMERA_BACK_MIRROR;
                break;
            case "left":
                key = KEY_CAMERA_LEFT_MIRROR;
                break;
            case "right":
                key = KEY_CAMERA_RIGHT_MIRROR;
                break;
            default:
                return;
        }
        prefs.edit().putBoolean(key, mirror).apply();
        AppLog.d(TAG, position + " Камера镜像Настройки: " + mirror);
    }

    /**
     * ПолучениеКамера镜像Настройки
     * @param position КамераПозиция（front/back/left/right)
     * @return  否镜像，По умолчанию为false（不镜像)
     */
    public boolean getCameraMirror(String position) {
        String key;
        switch (position) {
            case "front":
                key = KEY_CAMERA_FRONT_MIRROR;
                break;
            case "back":
                key = KEY_CAMERA_BACK_MIRROR;
                break;
            case "left":
                key = KEY_CAMERA_LEFT_MIRROR;
                break;
            case "right":
                key = KEY_CAMERA_RIGHT_MIRROR;
                break;
            default:
                return false;
        }
        return prefs.getBoolean(key, false);
    }

    /**
     * НастройкиКамера裁剪值
     * @param position Позиция（front/back/left/right)
     * @param direction 方 к （top/bottom/left/right)
     * @param pixels 裁剪像素值
     */
    public void setCameraCrop(String position, String direction, int pixels) {
        String key = KEY_CAMERA_CROP_PREFIX + position + "_" + direction;
        prefs.edit().putInt(key, Math.max(0, pixels)).apply();
    }

    /**
     * ПолучениеКамера裁剪值
     * @param position Позиция（front/back/left/right)
     * @param direction 方 к （top/bottom/left/right)
     * @return 裁剪像素值，По умолчанию为0
     */
    public int getCameraCrop(String position, String direction) {
        String key = KEY_CAMERA_CROP_PREFIX + position + "_" + direction;
        return prefs.getInt(key, 0);
    }

    /**
     * СбросКамера 所有裁剪值
     * @param position Позиция（front/back/left/right)
     */
    public void resetCameraCrop(String position) {
        setCameraCrop(position, "top", 0);
        setCameraCrop(position, "bottom", 0);
        setCameraCrop(position, "left", 0);
        setCameraCrop(position, "right", 0);
    }

    /**
     * Получение所有Камераконфигурация（用于Своя модель)
     * 返回格式：position -> [cameraId, cameraName]
     */
    public String[][] getAllCameraConfig() {
        int count = getCameraCount();
        String[][] config;
        
        if (count == 4) {
            config = new String[][] {
                {"front", getCameraId("front"), getCameraName("front")},
                {"back", getCameraId("back"), getCameraName("back")},
                {"left", getCameraId("left"), getCameraName("left")},
                {"right", getCameraId("right"), getCameraName("right")}
            };
        } else if (count == 2) {
            config = new String[][] {
                {"front", getCameraId("front"), getCameraName("front")},
                {"back", getCameraId("back"), getCameraName("back")}
            };
        } else {
            config = new String[][] {
                {"front", getCameraId("front"), getCameraName("front")}
            };
        }
        
        return config;
    }
    
    // ==================== ХранилищеПозицияконфигурация相Выкл方法 ====================
    
    /**
     * НастройкиХранилищеПозиция
     * @param location ХранилищеПозиция（internal или external_sd)
     */
    public void setStorageLocation(String location) {
        prefs.edit().putString(KEY_STORAGE_LOCATION, location).apply();
        AppLog.d(TAG, "ХранилищеПозицияНастройки: " + location);
    }
    
    /**
     * ПолучениеХранилищеПозиция
     * @return ХранилищеПозиция，По умолчанию为Внутренняя память
     */
    public String getStorageLocation() {
        return prefs.getString(KEY_STORAGE_LOCATION, STORAGE_INTERNAL);
    }
    
    /**
     *  否использованиеUSB-накопительХранилище
     * @return true 表示использованиеUSB-накопитель
     */
    public boolean isUsingExternalSdCard() {
        return STORAGE_EXTERNAL_SD.equals(getStorageLocation());
    }
    
    /**
     * НастройкиПользовательский путь USB-накопителя
     * @param path USB-накопительПуть，设为nullили空字符串表示использованиеавтоматически检测
     */
    public void setCustomSdCardPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            prefs.edit().remove(KEY_CUSTOM_SD_CARD_PATH).apply();
            AppLog.d(TAG, "очисткаПользовательский путь USB-накопителя，использованиеавтоматически检测");
        } else {
            prefs.edit().putString(KEY_CUSTOM_SD_CARD_PATH, path.trim()).apply();
            AppLog.d(TAG, "НастройкиПользовательский путь USB-накопителя: " + path.trim());
        }
    }
    
    /**
     * ПолучениеПользовательский путь USB-накопителя
     * @return Пользовательский путь，Если Не Настройки返回null
     */
    public String getCustomSdCardPath() {
        String path = prefs.getString(KEY_CUSTOM_SD_CARD_PATH, null);
        if (path != null && path.trim().isEmpty()) {
            return null;
        }
        return path;
    }
    
    /**
     *  否использованиеПользовательский путь USB-накопителя
     */
    public boolean hasCustomSdCardPath() {
        return getCustomSdCardPath() != null;
    }

    /**
     * 是否使用произвольный путь хранения
     * @return true 表示使用произвольный путь (включая SAF)
     */
    public boolean isUsingCustomPath() {
        return STORAGE_CUSTOM.equals(getStorageLocation()) || isUsingSafStorage();
    }

    /**
     * 设置произвольный путь хранения
     * @param path произвольный путь，设为null或空字符串表示清除
     */
    public void setCustomStoragePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            prefs.edit().remove(KEY_CUSTOM_STORAGE_PATH).apply();
            AppLog.d(TAG, "清除произвольный путь хранения");
        } else {
            prefs.edit().putString(KEY_CUSTOM_STORAGE_PATH, path.trim()).apply();
            AppLog.d(TAG, "设置произвольный путь хранения: " + path.trim());
        }
    }

    /**
     * 获取произвольный путь хранения
     * @return произвольный путь，如果未设置返回null
     */
    public String getCustomStoragePath() {
        String path = prefs.getString(KEY_CUSTOM_STORAGE_PATH, null);
        if (path != null && path.trim().isEmpty()) {
            return null;
        }
        return path;
    }

    /**
     * Сохранить SAF URI выбранной папки
     * @param uri URI выбранной папки (content://...)
     */
    public void setCustomStorageUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            prefs.edit().remove(KEY_CUSTOM_STORAGE_URI).apply();
            AppLog.d(TAG, "Очищен SAF URI");
        } else {
            prefs.edit().putString(KEY_CUSTOM_STORAGE_URI, uri.trim()).apply();
            AppLog.d(TAG, "Сохранён SAF URI: " + uri.trim());
        }
    }

    /**
     * Получить SAF URI выбранной папки
     * @return SAF URI или null
     */
    public String getCustomStorageUri() {
        return prefs.getString(KEY_CUSTOM_STORAGE_URI, null);
    }

    /**
     * Проверить, используется ли SAF для хранилища
     * @return true если выбрана папка через SAF
     */
    public boolean isUsingSafStorage() {
        return getCustomStorageUri() != null && !getCustomStorageUri().isEmpty();
    }

    /**
     * Настройки разавтоматическиОбнаружено USB-накопительПуть（缓存)
     * @param path USB-накопительПуть
     */
    public void setLastDetectedSdPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            prefs.edit().remove(KEY_LAST_DETECTED_SD_PATH).apply();
        } else {
            prefs.edit().putString(KEY_LAST_DETECTED_SD_PATH, path.trim()).apply();
            AppLog.d(TAG, "缓存USB-накопительПуть: " + path.trim());
        }
    }
    
    /**
     * Получение разавтоматическиОбнаружено USB-накопительПуть（缓存)
     * @return 缓存 Путь，Если Не Настройки返回null
     */
    public String getLastDetectedSdPath() {
        return prefs.getString(KEY_LAST_DETECTED_SD_PATH, null);
    }
    
    /**
     * проверка本 разЗапуск 否显示过USB-накопитель回退Уведомление
     */
    public static boolean isSdFallbackShownThisSession() {
        return sdFallbackShownThisSession;
    }
    
    /**
     * 标记本 разЗапуск显示过USB-накопитель回退Уведомление
     */
    public static void setSdFallbackShownThisSession(boolean shown) {
        sdFallbackShownThisSession = shown;
    }
    
    /**
     * СбросUSB-накопитель回退Уведомление标志（ПриложениеЗапуск时调用)
     */
    public static void resetSdFallbackFlag() {
        sdFallbackShownThisSession = false;
    }
    
    /**
     * проверкаТекущий 否应该использование转写入
     * 当ВыбратьUSB-накопительХранилище时，始终использование转写入以避免USB-накопитель慢速写入导致Запись卡顿
     * @return true 表示应该использование转写入
     */
    public boolean shouldUseRelayWrite() {
        return isUsingExternalSdCard();
    }
    
    // ==================== 悬浮窗конфигурация相Выкл方法 ====================
    
    /**
     * Настройки悬浮窗ВклВыкл
     * @param enabled true 表示Включить悬浮窗
     */
    public void setFloatingWindowEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FLOATING_WINDOW_ENABLED, enabled).apply();
        AppLog.d(TAG, "悬浮窗Настройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение悬浮窗ВклВыклСтатус
     * @return true 表示Включить悬浮窗
     */
    public boolean isFloatingWindowEnabled() {
        return prefs.getBoolean(KEY_FLOATING_WINDOW_ENABLED, false);
    }
    
    /**
     * Настройки悬浮窗大小（dp)
     * @param sizeDp 悬浮窗大小，单位dp
     */
    public void setFloatingWindowSize(int sizeDp) {
        prefs.edit().putInt(KEY_FLOATING_WINDOW_SIZE, sizeDp).apply();
        AppLog.d(TAG, "悬浮窗大小Настройки: " + sizeDp + "dp");
    }
    
    /**
     * Получение悬浮窗大小（dp)
     * @return 悬浮窗大小，По умолчанию为等大小
     */
    public int getFloatingWindowSize() {
        return prefs.getInt(KEY_FLOATING_WINDOW_SIZE, FLOATING_SIZE_MEDIUM);
    }
    
    /**
     * Настройки悬浮窗透明度（0-100)
     * @param alpha 透明度百分比，0为完全透明，100为完全不透明
     */
    public void setFloatingWindowAlpha(int alpha) {
        prefs.edit().putInt(KEY_FLOATING_WINDOW_ALPHA, alpha).apply();
        AppLog.d(TAG, "悬浮窗透明度Настройки: " + alpha + "%");
    }
    
    /**
     * Получение悬浮窗透明度（0-100)
     * @return 透明度百分比，По умолчанию为100（完全不透明)
     */
    public int getFloatingWindowAlpha() {
        return prefs.getInt(KEY_FLOATING_WINDOW_ALPHA, 100);
    }
    
    /**
     * Сохранить悬浮窗Позиция
     * @param x X坐标
     * @param y Y坐标
     */
    public void setFloatingWindowPosition(int x, int y) {
        prefs.edit()
            .putInt(KEY_FLOATING_WINDOW_X, x)
            .putInt(KEY_FLOATING_WINDOW_Y, y)
            .apply();
    }
    
    /**
     * Получение悬浮窗XПозиция
     * @return X坐标，По умолчанию-1表示Не Настройки
     */
    public int getFloatingWindowX() {
        return prefs.getInt(KEY_FLOATING_WINDOW_X, -1);
    }
    
    /**
     * Получение悬浮窗YПозиция
     * @return Y坐标，По умолчанию-1表示Не Настройки
     */
    public int getFloatingWindowY() {
        return prefs.getInt(KEY_FLOATING_WINDOW_Y, -1);
    }
    
    // ==================== ХранилищеОчистка конфигурация相Выкл方法 ====================
    
    /**
     * НастройкиВидеоХранилище限制（GB)
     * @param limitGb Хранилище限制，单位GB，0表示不限制
     */
    public void setVideoStorageLimitGb(int limitGb) {
        prefs.edit().putInt(KEY_VIDEO_STORAGE_LIMIT_GB, limitGb).apply();
        AppLog.d(TAG, "ВидеоХранилище限制Настройки: " + limitGb + " GB");
    }
    
    /**
     * ПолучениеВидеоХранилище限制（GB)
     * @return Хранилище限制，单位GB，0表示不限制，По умолчанию10GB
     */
    public int getVideoStorageLimitGb() {
        return prefs.getInt(KEY_VIDEO_STORAGE_LIMIT_GB, 10);
    }
    
    /**
     * НастройкиИзображениеХранилище限制（GB)
     * @param limitGb Хранилище限制，单位GB，0表示不限制
     */
    public void setPhotoStorageLimitGb(int limitGb) {
        prefs.edit().putInt(KEY_PHOTO_STORAGE_LIMIT_GB, limitGb).apply();
        AppLog.d(TAG, "ИзображениеХранилище限制Настройки: " + limitGb + " GB");
    }
    
    /**
     * ПолучениеИзображениеХранилище限制（GB)
     * @return Хранилище限制，单位GB，0表示不限制，По умолчанию10GB
     */
    public int getPhotoStorageLimitGb() {
        return prefs.getInt(KEY_PHOTO_STORAGE_LIMIT_GB, 10);
    }
    
    /**
     * проверка 否ВключитьХранилищеОчистка функция
     * @return true Если 至少有一项Хранилище限制Настройки大于0
     */
    public boolean isStorageCleanupEnabled() {
        return getVideoStorageLimitGb() > 0 || getPhotoStorageLimitGb() > 0;
    }
    
    // ==================== 分Записьконфигурация相Выкл方法 ====================
    
    /**
     * Настройки分时长（ мин.)
     * @param minutes 分时长，单位 мин.（1/3/5)
     */
    public void setSegmentDurationMinutes(int minutes) {
        prefs.edit().putInt(KEY_SEGMENT_DURATION_MINUTES, minutes).apply();
        AppLog.d(TAG, "分时长Настройки: " + minutes + "  мин.");
    }
    
    /**
     * Получение分时长（ мин.)
     * @return 分时长，单位 мин.，По умолчанию为1 мин.
     */
    public int getSegmentDurationMinutes() {
        return prefs.getInt(KEY_SEGMENT_DURATION_MINUTES, SEGMENT_DURATION_1_MIN);
    }
    
    /**
     * Получение分时长（毫 сек.)
     * @return 分时长，单位毫 сек.
     */
    public long getSegmentDurationMs() {
        return getSegmentDurationMinutes() * 60 * 1000L;
    }
    
    // ==================== ЗаписьСтатус显示конфигурация相Выкл方法 ====================
    
    /**
     * НастройкиЗаписьСтатус显示ВклВыкл
     * @param enabled true 表示显示Запись时间 и 分数
     */
    public void setRecordingStatsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_RECORDING_STATS_ENABLED, enabled).apply();
        AppLog.d(TAG, "ЗаписьСтатус显示Настройки: " + (enabled ? "显示" : "隐藏"));
    }
    
    /**
     * ПолучениеЗаписьСтатус显示ВклВыклСтатус
     * @return true 表示显示Запись时间 и 分数
     */
    public boolean isRecordingStatsEnabled() {
        // По умолчаниюВкл启ЗаписьСтатус显示
        return prefs.getBoolean(KEY_RECORDING_STATS_ENABLED, true);
    }
    
    // ==================== 补盲функция全局ВклВыкл ====================
    
    /**
     * Настройки补盲функция全局ВклВыкл
     * Закрыто时，所有补盲子функция（转 к 灯联动、主屏悬浮窗、副屏显示、模拟按钮、画面矫正)均不生效
     * @param enabled true 表示Включить补盲функция
     */
    public void setBlindSpotGlobalEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLIND_SPOT_GLOBAL_ENABLED, enabled).apply();
        AppLog.d(TAG, "补盲функция全局ВклВыкл: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение补盲функция全局ВклВыклСтатус
     * @return true 表示补盲функцияВключено
     */
    public boolean isBlindSpotGlobalEnabled() {
        return prefs.getBoolean(KEY_BLIND_SPOT_GLOBAL_ENABLED, false);
    }
    
    // ==================== 补盲选项конфигурация相Выкл方法 (原副屏显示) ====================
    
    /**
     * Настройки副屏显示ВклВыкл
     */
    public void setSecondaryDisplayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SECONDARY_DISPLAY_ENABLED, enabled).apply();
        AppLog.d(TAG, "副屏显示Настройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    public boolean isSecondaryDisplayEnabled() {
        return prefs.getBoolean(KEY_SECONDARY_DISPLAY_ENABLED, false);
    }
    
    /**
     * Настройки副屏显示 КамераПозиция
     */
    public void setSecondaryDisplayCamera(String position) {
        prefs.edit().putString(KEY_SECONDARY_DISPLAY_CAMERA, position).apply();
    }
    
    public String getSecondaryDisplayCamera() {
        return prefs.getString(KEY_SECONDARY_DISPLAY_CAMERA, "front");
    }
    
    /**
     * Настройки副屏 Display ID
     */
    public void setSecondaryDisplayId(int displayId) {
        prefs.edit().putInt(KEY_SECONDARY_DISPLAY_ID, displayId).apply();
    }
    
    public int getSecondaryDisplayId() {
        return prefs.getInt(KEY_SECONDARY_DISPLAY_ID, 0); // 0 为По умолчанию主屏，通常副屏 от 1Вкл始
    }
    
    /**
     * Настройки副屏Позиция и 大小
     */
    public void setSecondaryDisplayBounds(int x, int y, int width, int height) {
        prefs.edit()
            .putInt(KEY_SECONDARY_DISPLAY_X, x)
            .putInt(KEY_SECONDARY_DISPLAY_Y, y)
            .putInt(KEY_SECONDARY_DISPLAY_WIDTH, width)
            .putInt(KEY_SECONDARY_DISPLAY_HEIGHT, height)
            .apply();
    }
    
    public int getSecondaryDisplayX() {
        return prefs.getInt(KEY_SECONDARY_DISPLAY_X, 0);
    }
    
    public int getSecondaryDisplayY() {
        return prefs.getInt(KEY_SECONDARY_DISPLAY_Y, 139);
    }
    
    public int getSecondaryDisplayWidth() {
        return prefs.getInt(KEY_SECONDARY_DISPLAY_WIDTH, 318);
    }
    
    public int getSecondaryDisplayHeight() {
        return prefs.getInt(KEY_SECONDARY_DISPLAY_HEIGHT, 236);
    }
    
    /**
     * Настройки副屏Поворот 角度
     */
    public void setSecondaryDisplayRotation(int rotation) {
        prefs.edit().putInt(KEY_SECONDARY_DISPLAY_ROTATION, rotation).apply();
    }
    
    public int getSecondaryDisplayRotation() {
        return prefs.getInt(KEY_SECONDARY_DISPLAY_ROTATION, 0);
    }
    
    /**
     * Настройки 否显示白边框
     */
    public void setSecondaryDisplayBorderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SECONDARY_DISPLAY_BORDER, enabled).apply();
    }
    
    public boolean isSecondaryDisplayBorderEnabled() {
        return prefs.getBoolean(KEY_SECONDARY_DISPLAY_BORDER, false);
    }
    
    /**
     * Настройки屏幕方 к 
     */
    public void setSecondaryDisplayOrientation(int orientation) {
        prefs.edit().putInt(KEY_SECONDARY_DISPLAY_ORIENTATION, orientation).apply();
    }
    
    public int getSecondaryDisplayOrientation() {
        return prefs.getInt(KEY_SECONDARY_DISPLAY_ORIENTATION, 180);
    }

    /**
     * Настройки副屏补盲悬浮窗透明度（0-100)
     * @param alpha 透明度百分比，0为完全透明，100为完全不透明
     */
    public void setSecondaryDisplayAlpha(int alpha) {
        prefs.edit().putInt(KEY_SECONDARY_DISPLAY_ALPHA, Math.max(0, Math.min(100, alpha))).apply();
        AppLog.d(TAG, "副屏补盲悬浮窗透明度Настройки: " + alpha + "%");
    }

    /**
     * Получение副屏补盲悬浮窗透明度（0-100)
     * @return 透明度百分比，По умолчанию为100（完全不透明)
     */
    public int getSecondaryDisplayAlpha() {
        return prefs.getInt(KEY_SECONDARY_DISPLAY_ALPHA, 100);
    }

    public void setMainFloatingAspectRatioLocked(boolean locked) {
        prefs.edit().putBoolean(KEY_MAIN_FLOATING_ASPECT_RATIO_LOCKED, locked).apply();
    }

    public boolean isMainFloatingAspectRatioLocked() {
        return prefs.getBoolean(KEY_MAIN_FLOATING_ASPECT_RATIO_LOCKED, false);
    }

    public void setMainFloatingLongPressDragEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MAIN_FLOATING_LONG_PRESS_DRAG, enabled).apply();
    }

    public boolean isMainFloatingLongPressDragEnabled() {
        return prefs.getBoolean(KEY_MAIN_FLOATING_LONG_PRESS_DRAG, false);
    }

    public void setBlindSpotCorrectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BLIND_SPOT_CORRECTION_ENABLED, enabled).apply();
    }

    public boolean isBlindSpotCorrectionEnabled() {
        return prefs.getBoolean(KEY_BLIND_SPOT_CORRECTION_ENABLED, false);
    }

    public void setBlindSpotDisclaimerAccepted(boolean accepted) {
        prefs.edit().putBoolean(KEY_BLIND_SPOT_DISCLAIMER_ACCEPTED, accepted).apply();
    }

    public boolean isBlindSpotDisclaimerAccepted() {
        return prefs.getBoolean(KEY_BLIND_SPOT_DISCLAIMER_ACCEPTED, false);
    }

    private String getBlindSpotCorrectionKey(String cameraPos, String suffix) {
        return KEY_BLIND_SPOT_CORRECTION_PREFIX + cameraPos + "_" + suffix;
    }

    public void setBlindSpotCorrectionScaleX(String cameraPos, float scaleX) {
        prefs.edit().putFloat(getBlindSpotCorrectionKey(cameraPos, "scale_x"), scaleX).apply();
    }

    public void setBlindSpotCorrectionScaleY(String cameraPos, float scaleY) {
        prefs.edit().putFloat(getBlindSpotCorrectionKey(cameraPos, "scale_y"), scaleY).apply();
    }

    public void setBlindSpotCorrectionTranslateX(String cameraPos, float translateX) {
        prefs.edit().putFloat(getBlindSpotCorrectionKey(cameraPos, "translate_x"), translateX).apply();
    }

    public void setBlindSpotCorrectionTranslateY(String cameraPos, float translateY) {
        prefs.edit().putFloat(getBlindSpotCorrectionKey(cameraPos, "translate_y"), translateY).apply();
    }

    public float getBlindSpotCorrectionScaleX(String cameraPos) {
        return prefs.getFloat(getBlindSpotCorrectionKey(cameraPos, "scale_x"), 1.0f);
    }

    public float getBlindSpotCorrectionScaleY(String cameraPos) {
        return prefs.getFloat(getBlindSpotCorrectionKey(cameraPos, "scale_y"), 1.0f);
    }

    public float getBlindSpotCorrectionTranslateX(String cameraPos) {
        return prefs.getFloat(getBlindSpotCorrectionKey(cameraPos, "translate_x"), 0.0f);
    }

    public float getBlindSpotCorrectionTranslateY(String cameraPos) {
        return prefs.getFloat(getBlindSpotCorrectionKey(cameraPos, "translate_y"), 0.0f);
    }

    public void setBlindSpotCorrectionRotation(String cameraPos, int rotation) {
        prefs.edit().putInt(getBlindSpotCorrectionKey(cameraPos, "rotation"), rotation).apply();
    }

    public int getBlindSpotCorrectionRotation(String cameraPos) {
        // совместимость旧  float Хранилище，读取后转换
        try {
            return prefs.getInt(getBlindSpotCorrectionKey(cameraPos, "rotation"), 0);
        } catch (ClassCastException e) {
            // 旧版本存   float，读取并转换
            float old = prefs.getFloat(getBlindSpotCorrectionKey(cameraPos, "rotation"), 0.0f);
            int rounded = Math.round(old);
            // 规整 до  0/90/180/270
            if (rounded != 0 && rounded != 90 && rounded != 180 && rounded != 270) rounded = 0;
            setBlindSpotCorrectionRotation(cameraPos, rounded);
            return rounded;
        }
    }

    public void setBlindSpotCorrectionMirrorH(String cameraPos, boolean mirror) {
        prefs.edit().putBoolean(getBlindSpotCorrectionKey(cameraPos, "mirror_h"), mirror).apply();
    }

    public boolean getBlindSpotCorrectionMirrorH(String cameraPos) {
        return prefs.getBoolean(getBlindSpotCorrectionKey(cameraPos, "mirror_h"), false);
    }

    public void setBlindSpotCorrectionMirrorV(String cameraPos, boolean mirror) {
        prefs.edit().putBoolean(getBlindSpotCorrectionKey(cameraPos, "mirror_v"), mirror).apply();
    }

    public boolean getBlindSpotCorrectionMirrorV(String cameraPos) {
        return prefs.getBoolean(getBlindSpotCorrectionKey(cameraPos, "mirror_v"), false);
    }

    public void resetBlindSpotCorrection(String cameraPos) {
        prefs.edit()
                .putFloat(getBlindSpotCorrectionKey(cameraPos, "scale_x"), 1.0f)
                .putFloat(getBlindSpotCorrectionKey(cameraPos, "scale_y"), 1.0f)
                .putFloat(getBlindSpotCorrectionKey(cameraPos, "translate_x"), 0.0f)
                .putFloat(getBlindSpotCorrectionKey(cameraPos, "translate_y"), 0.0f)
                .putInt(getBlindSpotCorrectionKey(cameraPos, "rotation"), 0)
                .putBoolean(getBlindSpotCorrectionKey(cameraPos, "mirror_h"), false)
                .putBoolean(getBlindSpotCorrectionKey(cameraPos, "mirror_v"), false)
                .apply();
    }

    // ==================== 预览画面矫正конфигурация相Выкл方法 ====================

    /**
     * Настройки预览画面矫正ВклВыкл
     */
    public void setPreviewCorrectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PREVIEW_CORRECTION_ENABLED, enabled).apply();
        AppLog.d(TAG, "预览画面矫正Настройки: " + (enabled ? "Включить" : "Отключить"));
    }

    /**
     * Получение预览画面矫正ВклВыкл
     */
    public boolean isPreviewCorrectionEnabled() {
        return prefs.getBoolean(KEY_PREVIEW_CORRECTION_ENABLED, false);
    }

    private String getPreviewCorrectionKey(String cameraPos, String suffix) {
        return KEY_PREVIEW_CORRECTION_PREFIX + cameraPos + "_" + suffix;
    }

    public void setPreviewCorrectionScaleX(String cameraPos, float scaleX) {
        prefs.edit().putFloat(getPreviewCorrectionKey(cameraPos, "scale_x"), scaleX).apply();
    }

    public void setPreviewCorrectionScaleY(String cameraPos, float scaleY) {
        prefs.edit().putFloat(getPreviewCorrectionKey(cameraPos, "scale_y"), scaleY).apply();
    }

    public void setPreviewCorrectionTranslateX(String cameraPos, float translateX) {
        prefs.edit().putFloat(getPreviewCorrectionKey(cameraPos, "translate_x"), translateX).apply();
    }

    public void setPreviewCorrectionTranslateY(String cameraPos, float translateY) {
        prefs.edit().putFloat(getPreviewCorrectionKey(cameraPos, "translate_y"), translateY).apply();
    }

    public float getPreviewCorrectionScaleX(String cameraPos) {
        return prefs.getFloat(getPreviewCorrectionKey(cameraPos, "scale_x"), 1.0f);
    }

    public float getPreviewCorrectionScaleY(String cameraPos) {
        return prefs.getFloat(getPreviewCorrectionKey(cameraPos, "scale_y"), 1.0f);
    }

    public float getPreviewCorrectionTranslateX(String cameraPos) {
        return prefs.getFloat(getPreviewCorrectionKey(cameraPos, "translate_x"), 0.0f);
    }

    public float getPreviewCorrectionTranslateY(String cameraPos) {
        return prefs.getFloat(getPreviewCorrectionKey(cameraPos, "translate_y"), 0.0f);
    }

    /**
     * Сброс单 кам.Камера 预览矫正参数
     */
    public void resetPreviewCorrection(String cameraPos) {
        prefs.edit()
                .putFloat(getPreviewCorrectionKey(cameraPos, "scale_x"), 1.0f)
                .putFloat(getPreviewCorrectionKey(cameraPos, "scale_y"), 1.0f)
                .putFloat(getPreviewCorrectionKey(cameraPos, "translate_x"), 0.0f)
                .putFloat(getPreviewCorrectionKey(cameraPos, "translate_y"), 0.0f)
                .apply();
    }

    /**
     * Сброс所有Камера 预览矫正参数
     */
    public void resetAllPreviewCorrection() {
        resetPreviewCorrection("front");
        resetPreviewCorrection("back");
        resetPreviewCorrection("left");
        resetPreviewCorrection("right");
        AppLog.d(TAG, "所有预览画面矫正参数Сброс");
    }

    // ==================== 鱼眼矫正конфигурация相Выкл方法 ====================

    /**
     * Настройки鱼眼矫正ВклВыкл
     */
    public void setFisheyeCorrectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FISHEYE_CORRECTION_ENABLED, enabled).apply();
        AppLog.d(TAG, "鱼眼矫正Настройки: " + (enabled ? "Включить" : "Отключить"));
    }

    /**
     * Получение鱼眼矫正ВклВыкл
     */
    public boolean isFisheyeCorrectionEnabled() {
        return prefs.getBoolean(KEY_FISHEYE_CORRECTION_ENABLED, false);
    }

    private String getFisheyeCorrectionKey(String cameraPos, String suffix) {
        return KEY_FISHEYE_CORRECTION_PREFIX + cameraPos + "_" + suffix;
    }

    // --- K1 (主畸变系数) ---
    public void setFisheyeCorrectionK1(String cameraPos, float k1) {
        prefs.edit().putFloat(getFisheyeCorrectionKey(cameraPos, "k1"), k1).apply();
    }

    public float getFisheyeCorrectionK1(String cameraPos) {
        return prefs.getFloat(getFisheyeCorrectionKey(cameraPos, "k1"), 0.0f);
    }

    // --- K2 (二 раз畸变系数) ---
    public void setFisheyeCorrectionK2(String cameraPos, float k2) {
        prefs.edit().putFloat(getFisheyeCorrectionKey(cameraPos, "k2"), k2).apply();
    }

    public float getFisheyeCorrectionK2(String cameraPos) {
        return prefs.getFloat(getFisheyeCorrectionKey(cameraPos, "k2"), 0.0f);
    }

    // --- Zoom (矫正后缩放) ---
    public void setFisheyeCorrectionZoom(String cameraPos, float zoom) {
        prefs.edit().putFloat(getFisheyeCorrectionKey(cameraPos, "zoom"), zoom).apply();
    }

    public float getFisheyeCorrectionZoom(String cameraPos) {
        return prefs.getFloat(getFisheyeCorrectionKey(cameraPos, "zoom"), 1.0f);
    }

    // --- CenterX (畸变心X偏移) ---
    public void setFisheyeCorrectionCenterX(String cameraPos, float cx) {
        prefs.edit().putFloat(getFisheyeCorrectionKey(cameraPos, "center_x"), cx).apply();
    }

    public float getFisheyeCorrectionCenterX(String cameraPos) {
        return prefs.getFloat(getFisheyeCorrectionKey(cameraPos, "center_x"), 0.5f);
    }

    // --- CenterY (畸变心Y偏移) ---
    public void setFisheyeCorrectionCenterY(String cameraPos, float cy) {
        prefs.edit().putFloat(getFisheyeCorrectionKey(cameraPos, "center_y"), cy).apply();
    }

    public float getFisheyeCorrectionCenterY(String cameraPos) {
        return prefs.getFloat(getFisheyeCorrectionKey(cameraPos, "center_y"), 0.5f);
    }

    /**
     * Сброс单 кам.Камера 鱼眼矫正参数
     */
    public void resetFisheyeCorrection(String cameraPos) {
        prefs.edit()
                .putFloat(getFisheyeCorrectionKey(cameraPos, "k1"), 0.0f)
                .putFloat(getFisheyeCorrectionKey(cameraPos, "k2"), 0.0f)
                .putFloat(getFisheyeCorrectionKey(cameraPos, "zoom"), 1.0f)
                .putFloat(getFisheyeCorrectionKey(cameraPos, "center_x"), 0.5f)
                .putFloat(getFisheyeCorrectionKey(cameraPos, "center_y"), 0.5f)
                .apply();
    }

    /**
     * Сброс所有Камера 鱼眼矫正参数
     */
    public void resetAllFisheyeCorrection() {
        resetFisheyeCorrection("front");
        resetFisheyeCorrection("back");
        resetFisheyeCorrection("left");
        resetFisheyeCorrection("right");
        AppLog.d(TAG, "所有鱼眼矫正参数Сброс");
    }

    // ==================== 主屏悬浮窗конфигурация相Выкл方法 ====================

    /**
     * Настройки主屏悬浮窗ВклВыкл
     */
    public void setMainFloatingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MAIN_FLOATING_ENABLED, enabled).apply();
        AppLog.d(TAG, "主屏悬浮窗Настройки: " + (enabled ? "Включить" : "Отключить"));
    }

    public boolean isMainFloatingEnabled() {
        return prefs.getBoolean(KEY_MAIN_FLOATING_ENABLED, false);
    }

    /**
     * Настройки主屏悬浮窗显示 КамераПозиция
     */
    public void setMainFloatingCamera(String position) {
        prefs.edit().putString(KEY_MAIN_FLOATING_CAMERA, position).apply();
    }

    public String getMainFloatingCamera() {
        return prefs.getString(KEY_MAIN_FLOATING_CAMERA, "front");
    }

    /**
     * Настройки主屏悬浮窗Позиция и 大小
     */
    public void setMainFloatingBounds(int x, int y, int width, int height) {
        prefs.edit()
            .putInt(KEY_MAIN_FLOATING_X, x)
            .putInt(KEY_MAIN_FLOATING_Y, y)
            .putInt(KEY_MAIN_FLOATING_WIDTH, width)
            .putInt(KEY_MAIN_FLOATING_HEIGHT, height)
            .apply();
    }

    public int getMainFloatingX() {
        return prefs.getInt(KEY_MAIN_FLOATING_X, 100);
    }

    public int getMainFloatingY() {
        return prefs.getInt(KEY_MAIN_FLOATING_Y, 100);
    }

    public int getMainFloatingWidth() {
        return prefs.getInt(KEY_MAIN_FLOATING_WIDTH, 480);
    }

    public int getMainFloatingHeight() {
        return prefs.getInt(KEY_MAIN_FLOATING_HEIGHT, 320);
    }

    /**
     * Сброс主屏悬浮窗Позиция и 大小为По умолчанию值
     */
    public void resetMainFloatingBounds() {
        prefs.edit()
            .putInt(KEY_MAIN_FLOATING_X, 100)
            .putInt(KEY_MAIN_FLOATING_Y, 100)
            .putInt(KEY_MAIN_FLOATING_WIDTH, 480)
            .putInt(KEY_MAIN_FLOATING_HEIGHT, 320)
            .apply();
    }

    // ==================== 转 к 灯联动конфигурация相Выкл方法 ====================

    /**
     * Настройки转 к 灯联动ВклВыкл
     */
    public void setTurnSignalLinkageEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TURN_SIGNAL_LINKAGE_ENABLED, enabled).apply();
        AppLog.d(TAG, "转 к 灯联动Настройки: " + (enabled ? "Включить" : "Отключить"));
    }

    public boolean isTurnSignalLinkageEnabled() {
        return prefs.getBoolean(KEY_TURN_SIGNAL_LINKAGE_ENABLED, false);
    }

    /**
     * Настройки转 к 灯熄灭后 延迟消失时间（ сек.)
     */
    public void setTurnSignalTimeout(int seconds) {
        prefs.edit().putInt(KEY_TURN_SIGNAL_TIMEOUT, seconds).apply();
    }

   public int getTurnSignalTimeout() {
        return prefs.getInt(KEY_TURN_SIGNAL_TIMEOUT, 10);
    }

    /**
     * Настройки 否复用主屏悬浮窗
     */
    public void setTurnSignalReuseMainFloating(boolean reuse) {
        prefs.edit().putBoolean(KEY_TURN_SIGNAL_REUSE_MAIN_FLOATING, reuse).apply();
    }

    public boolean isTurnSignalReuseMainFloating() {
        return prefs.getBoolean(KEY_TURN_SIGNAL_REUSE_MAIN_FLOATING, true);
    }

    public void setTurnSignalCustomLeftTriggerLog(String keyword) {
        prefs.edit().putString(KEY_TURN_SIGNAL_CUSTOM_LEFT_TRIGGER_LOG, keyword).apply();
    }

    public String getTurnSignalCustomLeftTriggerLog() {
        return prefs.getString(
                KEY_TURN_SIGNAL_CUSTOM_LEFT_TRIGGER_LOG,
                "left front turn signal:1"
        );
    }

    public void setTurnSignalCustomRightTriggerLog(String keyword) {
        prefs.edit().putString(KEY_TURN_SIGNAL_CUSTOM_RIGHT_TRIGGER_LOG, keyword).apply();
    }

    public String getTurnSignalCustomRightTriggerLog() {
        return prefs.getString(
                KEY_TURN_SIGNAL_CUSTOM_RIGHT_TRIGGER_LOG,
                "right front turn signal:1"
        );
    }

    public String getTurnSignalLeftTriggerLog() {
        return getTurnSignalCustomLeftTriggerLog();
    }

    public String getTurnSignalRightTriggerLog() {
        return getTurnSignalCustomRightTriggerLog();
    }

    /**
     * Настройки转 к 灯触发режим
     * @param mode TRIGGER_MODE_LOGCAT или TRIGGER_MODE_CAR_API
     */
    public void setTurnSignalTriggerMode(String mode) {
        prefs.edit().putString(KEY_TURN_SIGNAL_TRIGGER_MODE, mode).apply();
        AppLog.d(TAG, "转 к 灯触发режим: " + mode);
    }

    /**
     * Получение转 к 灯触发режим
     */
    public String getTurnSignalTriggerMode() {
        return prefs.getString(KEY_TURN_SIGNAL_TRIGGER_MODE, TRIGGER_MODE_VHAL_GRPC);
    }

    /**
     *  否использование CarAPI 触发режим（совместимость性方法)
     */
    public boolean isCarApiTriggerMode() {
        String mode = getTurnSignalTriggerMode();
        return TRIGGER_MODE_VHAL_GRPC.equals(mode) || TRIGGER_MODE_CAR_API.equals(mode);
    }

    /**
     *  否использование车辆API 触发режим
     */
    public boolean isVhalGrpcTriggerMode() {
        return TRIGGER_MODE_VHAL_GRPC.equals(getTurnSignalTriggerMode());
    }

    /**
     *  否использование CarSignalManager API 触发режим
     */
    public boolean isCarSignalManagerTriggerMode() {
        return TRIGGER_MODE_CAR_SIGNAL_MANAGER.equals(getTurnSignalTriggerMode());
    }

    /**
     * Сохранить用户Выбрать 转 к 灯预设选项（用于Восстановление具体  RadioButton Выбрать)
     * @param presetName 预设名称，если "l6l7" или "boyue_l"
     */
    public void setTurnSignalPresetSelection(String presetName) {
        prefs.edit().putString(KEY_TURN_SIGNAL_PRESET_SELECTION, presetName).apply();
        AppLog.d(TAG, "Сохранить转 к 灯预设Выбрать: " + presetName);
    }

    /**
     * Получение用户Выбрать 转 к 灯预设选项
     * @return 预设名称，если "l6l7" или "boyue_l"
     */
    public String getTurnSignalPresetSelection() {
        return prefs.getString(KEY_TURN_SIGNAL_PRESET_SELECTION, "l6l7"); // По умолчанию返回 l6l7
    }

    // ==================== 车门联动конфигурация ====================
    
    /**
     * Настройки车门联动ВклВыкл（左右补盲)
     */
    public void setDoorLinkageEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DOOR_LINKAGE_ENABLED, enabled).apply();
        AppLog.d(TAG, "车门联动Настройки: " + (enabled ? "Включить" : "Отключить"));
    }

    public boolean isDoorLinkageEnabled() {
        return prefs.getBoolean(KEY_DOOR_LINKAGE_ENABLED, false);
    }

    /**
     * Настройки车门Закрыто后延迟消失时间
     */
    public void setDoorTimeout(int seconds) {
        prefs.edit().putInt(KEY_DOOR_TIMEOUT, seconds).apply();
    }

    public int getDoorTimeout() {
        return prefs.getInt(KEY_DOOR_TIMEOUT, 10); // По умолчанию10 сек.
    }

    /**
     * Сохранить用户Выбрать 车门联动预设选项（用于Восстановление具体  RadioButton Выбрать)
     * @param presetName 预设名称，если "l6l7" или "boyue_l"
     */
    public void setDoorPresetSelection(String presetName) {
        prefs.edit().putString(KEY_DOOR_PRESET_SELECTION, presetName).apply();
        AppLog.d(TAG, "Сохранить车门联动预设Выбрать: " + presetName);
    }

    /**
     * Получение用户Выбрать 车门联动预设选项
     * @return 预设名称，если "l6l7" или "boyue_l"
     */
    public String getDoorPresetSelection() {
        return prefs.getString(KEY_DOOR_PRESET_SELECTION, "l6l7"); // По умолчанию返回 l6l7
    }

    /**
     * Настройки车门联动 否复用主屏悬浮窗
     */
    public void setDoorReuseMainFloating(boolean reuse) {
        prefs.edit().putBoolean(KEY_DOOR_REUSE_MAIN_FLOATING, reuse).apply();
    }

    public boolean isDoorReuseMainFloating() {
        return prefs.getBoolean(KEY_DOOR_REUSE_MAIN_FLOATING, true); // По умолчанию复用
    }

    /**
     * Настройки车门联动副屏显示ВклВыкл
     */
    public void setDoorSecondaryDisplayEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DOOR_SECONDARY_DISPLAY_ENABLED, enabled).apply();
    }

    public boolean isDoorSecondaryDisplayEnabled() {
        return prefs.getBoolean(KEY_DOOR_SECONDARY_DISPLAY_ENABLED, false); // По умолчаниюЗакрыто
    }

    /**
     * Настройки独立补盲悬浮窗Позиция и 大小
     */
    public void setTurnSignalFloatingBounds(int x, int y, int width, int height) {
        prefs.edit()
            .putInt(KEY_TURN_SIGNAL_FLOATING_X, x)
            .putInt(KEY_TURN_SIGNAL_FLOATING_Y, y)
            .putInt(KEY_TURN_SIGNAL_FLOATING_WIDTH, width)
            .putInt(KEY_TURN_SIGNAL_FLOATING_HEIGHT, height)
            .apply();
    }

    public int getTurnSignalFloatingX() {
        return prefs.getInt(KEY_TURN_SIGNAL_FLOATING_X, 200);
    }

    public int getTurnSignalFloatingY() {
        return prefs.getInt(KEY_TURN_SIGNAL_FLOATING_Y, 200);
    }

    public int getTurnSignalFloatingWidth() {
        return prefs.getInt(KEY_TURN_SIGNAL_FLOATING_WIDTH, 640);
    }

    public int getTurnSignalFloatingHeight() {
        return prefs.getInt(KEY_TURN_SIGNAL_FLOATING_HEIGHT, 360);
    }

    /**
     * Настройки独立补盲悬浮窗Поворот 
     */
    public void setTurnSignalFloatingRotation(int rotation) {
        prefs.edit().putInt(KEY_TURN_SIGNAL_FLOATING_ROTATION, rotation).apply();
    }

    public int getTurnSignalFloatingRotation() {
        return prefs.getInt(KEY_TURN_SIGNAL_FLOATING_ROTATION, 0);
    }

    // ==================== 桌面悬浮模拟按钮конфигурация相Выкл方法 ====================

    public void setMockTurnSignalFloatingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MOCK_TURN_SIGNAL_FLOATING_ENABLED, enabled).apply();
    }

    public boolean isMockTurnSignalFloatingEnabled() {
        return prefs.getBoolean(KEY_MOCK_TURN_SIGNAL_FLOATING_ENABLED, false);
    }

    public void setMockTurnSignalFloatingPosition(int x, int y) {
        prefs.edit()
                .putInt(KEY_MOCK_TURN_SIGNAL_FLOATING_X, x)
                .putInt(KEY_MOCK_TURN_SIGNAL_FLOATING_Y, y)
                .apply();
    }

    public int getMockTurnSignalFloatingX() {
        return prefs.getInt(KEY_MOCK_TURN_SIGNAL_FLOATING_X, 200);
    }

    public int getMockTurnSignalFloatingY() {
        return prefs.getInt(KEY_MOCK_TURN_SIGNAL_FLOATING_Y, 200);
    }

    // ==================== 悬浮窗动效конфигурация ====================

    public void setFloatingWindowAnimationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FLOATING_WINDOW_ANIMATION_ENABLED, enabled).apply();
    }

    public boolean isFloatingWindowAnimationEnabled() {
        return prefs.getBoolean(KEY_FLOATING_WINDOW_ANIMATION_ENABLED, false);
    }

    public void setBlindSpotStatusBarStyle(int style) {
        prefs.edit().putInt(KEY_BLIND_SPOT_STATUS_BAR_STYLE, style).apply();
    }

    /**
     * @return 0=Закрыто, 1=序贯灯, 2=流光彗尾, 3=波纹扩散, 4=呼吸渐变填充, 5=箭头涟漪
     */
    public int getBlindSpotStatusBarStyle() {
        return prefs.getInt(KEY_BLIND_SPOT_STATUS_BAR_STYLE, 1);
    }

    public void setBlindSpotStatusBarColor(int color) {
        prefs.edit().putInt(KEY_BLIND_SPOT_STATUS_BAR_COLOR, color).apply();
    }

    /**
     * @return ARGB color for status bar effect. Default: amber 0xFFFFBF40
     */
    public int getBlindSpotStatusBarColor() {
        return prefs.getInt(KEY_BLIND_SPOT_STATUS_BAR_COLOR, 0xFFFFBF40);
    }

    /**
     * @param opacity 0-100, 0=完全透明, 100=完全不透明
     */
    public void setBlindSpotStatusBarBgOpacity(int opacity) {
        prefs.edit().putInt(KEY_BLIND_SPOT_STATUS_BAR_BG_OPACITY, opacity).apply();
    }

    /**
     * @return 0-100, default 31 (约 31% 不透明)
     */
    public int getBlindSpotStatusBarBgOpacity() {
        return prefs.getInt(KEY_BLIND_SPOT_STATUS_BAR_BG_OPACITY, 31);
    }

    // ==================== 时间角标конфигурация相Выкл方法 ====================
    
    /**
     * Настройки时间角标ВклВыкл
     * @param enabled true 表示 Сохранить Видео и Изображение添加时间角标
     */
    public void setTimestampWatermarkEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TIMESTAMP_WATERMARK_ENABLED, enabled).apply();
        AppLog.d(TAG, "时间角标Настройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение时间角标ВклВыклСтатус
     * @return true 表示Включить时间角标
     */
    public boolean isTimestampWatermarkEnabled() {
        // По умолчаниюЗакрыто时间角标
        return prefs.getBoolean(KEY_TIMESTAMP_WATERMARK_ENABLED, false);
    }
    
    // ==================== ЗаписьКамераВыбратьконфигурация相Выкл方法 ====================
    
    /**
     * Настройки某 шт.Камера 否参 и 主界面Запись
     * @param position Позиция（front/back/left/right)
     * @param enabled true 表示参 и Запись
     */
    public void setRecordingCameraEnabled(String position, boolean enabled) {
        String key;
        switch (position) {
            case "front":
                key = KEY_RECORDING_CAMERA_FRONT_ENABLED;
                break;
            case "back":
                key = KEY_RECORDING_CAMERA_BACK_ENABLED;
                break;
            case "left":
                key = KEY_RECORDING_CAMERA_LEFT_ENABLED;
                break;
            case "right":
                key = KEY_RECORDING_CAMERA_RIGHT_ENABLED;
                break;
            default:
                AppLog.w(TAG, "Неизвестно КамераПозиция: " + position);
                return;
        }
        prefs.edit().putBoolean(key, enabled).apply();
        AppLog.d(TAG, "ЗаписьКамераНастройки: " + position + " = " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение某 шт.Камера 否参 и 主界面Запись
     * @param position Позиция（front/back/left/right)
     * @return true 表示参 и Запись，По умолчанию为 true
     */
    public boolean isRecordingCameraEnabled(String position) {
        String key;
        switch (position) {
            case "front":
                key = KEY_RECORDING_CAMERA_FRONT_ENABLED;
                break;
            case "back":
                key = KEY_RECORDING_CAMERA_BACK_ENABLED;
                break;
            case "left":
                key = KEY_RECORDING_CAMERA_LEFT_ENABLED;
                break;
            case "right":
                key = KEY_RECORDING_CAMERA_RIGHT_ENABLED;
                break;
            default:
                return true;  // НеизвестноПозицияПо умолчаниюВключить
        }
        // По умолчаниюВключить（全选)
        return prefs.getBoolean(key, true);
    }
    
    /**
     * Получение所有ВключитьЗапись КамераПозиция集合
     * только返回Текущий车型конфигурациясуществует Камера
     * @return Включить КамераПозиция集合（если ["front", "back"])
     */
    public java.util.Set<String> getEnabledRecordingCameras() {
        java.util.Set<String> enabled = new java.util.HashSet<>();
        int cameraCount = getCameraCount();
        
        // 根据Камера数量判断哪些Позициясуществует
        if (cameraCount >= 1 && isRecordingCameraEnabled("front")) {
            enabled.add("front");
        }
        if (cameraCount >= 2 && isRecordingCameraEnabled("back")) {
            enabled.add("back");
        }
        if (cameraCount >= 4) {
            if (isRecordingCameraEnabled("left")) {
                enabled.add("left");
            }
            if (isRecordingCameraEnabled("right")) {
                enabled.add("right");
            }
        }
        
        // 安全проверка：Если 结果пусто，返回所有ДоступноКамера（防止无法Запись)
        if (enabled.isEmpty()) {
            AppLog.w(TAG, "没有Включить ЗаписьКамера，автоматическиВключить所有ДоступноКамера");
            if (cameraCount >= 1) enabled.add("front");
            if (cameraCount >= 2) enabled.add("back");
            if (cameraCount >= 4) {
                enabled.add("left");
                enabled.add("right");
            }
            // 同时Сбросконфигурация
            resetRecordingCameraSelection();
        }
        
        return enabled;
    }
    
    /**
     * СбросЗаписьКамераВыбрать为全选
     */
    public void resetRecordingCameraSelection() {
        prefs.edit()
            .putBoolean(KEY_RECORDING_CAMERA_FRONT_ENABLED, true)
            .putBoolean(KEY_RECORDING_CAMERA_BACK_ENABLED, true)
            .putBoolean(KEY_RECORDING_CAMERA_LEFT_ENABLED, true)
            .putBoolean(KEY_RECORDING_CAMERA_RIGHT_ENABLED, true)
            .apply();
        AppLog.d(TAG, "ЗаписьКамераВыбратьСброс为全选");
    }
    
    /**
     * Получение用于显示 Камера名称（用于ЗаписьКамераВыбрать等Настройки界面)
     * использованиеконфигурация 名称，Если пусто则返回"ПозицияN"
     * @param position Позиция（front/back/left/right)
     * @param index Позиция索引（1-4)
     * @return 显示名称
     */
    public String getRecordingCameraDisplayName(String position, int index) {
        String name = getCameraName(position);
        // Если 名称пустоилитолькопусто白，использованиеПозиция名称
        if (name == null || name.trim().isEmpty()) {
            return "Позиция" + index;
        }
        return name;
    }
    
    // ==================== 亮度/Шумоподавление调节конфигурация相Выкл方法 ====================
    
    /**
     * Настройки 否Включить亮度/Шумоподавление调节
     * @param enabled true 表示Включить
     */
    public void setImageAdjustEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_IMAGE_ADJUST_ENABLED, enabled).apply();
        AppLog.d(TAG, "亮度/Шумоподавление调节Настройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение 否Включить亮度/Шумоподавление调节
     * @return true 表示Включить
     */
    public boolean isImageAdjustEnabled() {
        return prefs.getBoolean(KEY_IMAGE_ADJUST_ENABLED, false);
    }
    
    /**
     * НастройкиЭкспозиция值
     * @param value Экспозиция值（范围取决于设备，通常 -12  до  +12)
     */
    public void setExposureCompensation(int value) {
        prefs.edit().putInt(KEY_EXPOSURE_COMPENSATION, value).apply();
        AppLog.d(TAG, "ЭкспозицияНастройки: " + value);
    }
    
    /**
     * ПолучениеЭкспозиция值
     * @return Экспозиция值，По умолчанию为 0
     */
    public int getExposureCompensation() {
        return prefs.getInt(KEY_EXPOSURE_COMPENSATION, 0);
    }
    
    /**
     * НастройкиБаланс белогорежим
     * @param mode Баланс белогорежим（AWB_MODE_* 常量)
     */
    public void setAwbMode(int mode) {
        prefs.edit().putInt(KEY_AWB_MODE, mode).apply();
        AppLog.d(TAG, "Баланс белогорежимНастройки: " + mode);
    }
    
    /**
     * ПолучениеБаланс белогорежим
     * @return Баланс белогорежим，По умолчанию为 AWB_MODE_DEFAULT（不Настройки)
     */
    public int getAwbMode() {
        return prefs.getInt(KEY_AWB_MODE, AWB_MODE_DEFAULT);
    }
    
    /**
     * НастройкиТональная компрессиярежим
     * @param mode Тональная компрессиярежим（TONEMAP_MODE_* 常量)
     */
    public void setTonemapMode(int mode) {
        prefs.edit().putInt(KEY_TONEMAP_MODE, mode).apply();
        AppLog.d(TAG, "Тональная компрессиярежимНастройки: " + mode);
    }
    
    /**
     * ПолучениеТональная компрессиярежим
     * @return Тональная компрессиярежим，По умолчанию为 TONEMAP_MODE_DEFAULT（不Настройки)
     */
    public int getTonemapMode() {
        return prefs.getInt(KEY_TONEMAP_MODE, TONEMAP_MODE_DEFAULT);
    }
    
    /**
     * НастройкиРезкостьрежим
     * @param mode Резкостьрежим（EDGE_MODE_* 常量)
     */
    public void setEdgeMode(int mode) {
        prefs.edit().putInt(KEY_EDGE_MODE, mode).apply();
        AppLog.d(TAG, "РезкостьрежимНастройки: " + mode);
    }
    
    /**
     * ПолучениеРезкостьрежим
     * @return Резкостьрежим，По умолчанию为 EDGE_MODE_DEFAULT（不Настройки)
     */
    public int getEdgeMode() {
        return prefs.getInt(KEY_EDGE_MODE, EDGE_MODE_DEFAULT);
    }
    
    /**
     * НастройкиШумоподавлениережим
     * @param mode Шумоподавлениережим（NOISE_REDUCTION_* 常量)
     */
    public void setNoiseReductionMode(int mode) {
        prefs.edit().putInt(KEY_NOISE_REDUCTION_MODE, mode).apply();
        AppLog.d(TAG, "ШумоподавлениережимНастройки: " + mode);
    }
    
    /**
     * ПолучениеШумоподавлениережим
     * @return Шумоподавлениережим，По умолчанию为 NOISE_REDUCTION_DEFAULT（不Настройки)
     */
    public int getNoiseReductionMode() {
        return prefs.getInt(KEY_NOISE_REDUCTION_MODE, NOISE_REDUCTION_DEFAULT);
    }
    
    /**
     * НастройкиЭффектырежим
     * @param mode Эффектырежим（EFFECT_MODE_* 常量)
     */
    public void setEffectMode(int mode) {
        prefs.edit().putInt(KEY_EFFECT_MODE, mode).apply();
        AppLog.d(TAG, "ЭффектырежимНастройки: " + mode);
    }
    
    /**
     * ПолучениеЭффектырежим
     * @return Эффектырежим，По умолчанию为 EFFECT_MODE_DEFAULT（不Настройки)
     */
    public int getEffectMode() {
        return prefs.getInt(KEY_EFFECT_MODE, EFFECT_MODE_DEFAULT);
    }
    
    /**
     * Настройки场景режим
     * @param mode 场景режим
     */
    public void setSceneMode(int mode) {
        prefs.edit().putInt(KEY_SCENE_MODE, mode).apply();
        AppLog.d(TAG, "场景режимНастройки: " + mode);
    }
    
    /**
     * Получение场景режим
     * @return 场景режим，По умолчанию为 -1（不Настройки)
     */
    public int getSceneMode() {
        return prefs.getInt(KEY_SCENE_MODE, -1);
    }
    
    /**
     * Сброс所有亮度/Шумоподавление调节参数为По умолчанию值
     */
    public void resetImageAdjustParams() {
        prefs.edit()
            .putInt(KEY_EXPOSURE_COMPENSATION, 0)
            .putInt(KEY_AWB_MODE, AWB_MODE_DEFAULT)
            .putInt(KEY_TONEMAP_MODE, TONEMAP_MODE_DEFAULT)
            .putInt(KEY_EDGE_MODE, EDGE_MODE_DEFAULT)
            .putInt(KEY_NOISE_REDUCTION_MODE, NOISE_REDUCTION_DEFAULT)
            .putInt(KEY_EFFECT_MODE, EFFECT_MODE_DEFAULT)
            .putInt(KEY_SCENE_MODE, -1)
            .apply();
        AppLog.d(TAG, "亮度/Шумоподавление调节参数Сброс为По умолчанию值");
    }
    
    /**
     * ПолучениеБаланс белогорежим 显示名称
     */
    public static String getAwbModeDisplayName(int mode) {
        switch (mode) {
            case AWB_MODE_DEFAULT: return "По умолчанию";
            case AWB_MODE_AUTO: return "Авто";
            case AWB_MODE_INCANDESCENT: return "Лампа накаливания";
            case AWB_MODE_FLUORESCENT: return "Люминесцентная";
            case AWB_MODE_WARM_FLUORESCENT: return "Тёплая люминесцентная";
            case AWB_MODE_DAYLIGHT: return "Солнечный свет";
            case AWB_MODE_CLOUDY_DAYLIGHT: return "Облачно";
            case AWB_MODE_TWILIGHT: return "Закат";
            case AWB_MODE_SHADE: return "Тень";
            default: return "Неизвестно";
        }
    }
    
    /**
     * ПолучениеТональная компрессиярежим 显示名称
     */
    public static String getTonemapModeDisplayName(int mode) {
        switch (mode) {
            case TONEMAP_MODE_DEFAULT: return "По умолчанию";
            case TONEMAP_MODE_CONTRAST_CURVE: return "Кривая контрастности";
            case TONEMAP_MODE_FAST: return "Быстрое";
            case TONEMAP_MODE_HIGH_QUALITY: return "Высокое качество";
            default: return "Неизвестно";
        }
    }
    
    /**
     * ПолучениеРезкостьрежим 显示名称
     */
    public static String getEdgeModeDisplayName(int mode) {
        switch (mode) {
            case EDGE_MODE_DEFAULT: return "По умолчанию";
            case EDGE_MODE_OFF: return "Выкл";
            case EDGE_MODE_FAST: return "Быстрое";
            case EDGE_MODE_HIGH_QUALITY: return "Высокое качество";
            default: return "Неизвестно";
        }
    }
    
    /**
     * ПолучениеШумоподавлениережим 显示名称
     */
    public static String getNoiseReductionModeDisplayName(int mode) {
        switch (mode) {
            case NOISE_REDUCTION_DEFAULT: return "По умолчанию";
            case NOISE_REDUCTION_OFF: return "Выкл";
            case NOISE_REDUCTION_FAST: return "Быстрое";
            case NOISE_REDUCTION_HIGH_QUALITY: return "Высокое качество";
            default: return "Неизвестно";
        }
    }
    
    /**
     * ПолучениеЭффектырежим 显示名称
     */
    public static String getEffectModeDisplayName(int mode) {
        switch (mode) {
            case EFFECT_MODE_DEFAULT: return "По умолчанию";
            case EFFECT_MODE_OFF: return "Выкл";
            case EFFECT_MODE_MONO: return "Чёрно-белый";
            case EFFECT_MODE_NEGATIVE: return "Негатив";
            case EFFECT_MODE_SOLARIZE: return "Передержка";
            case EFFECT_MODE_SEPIA: return "Ретро";
            case EFFECT_MODE_AQUA: return "Голубой";
            default: return "Неизвестно";
        }
    }
    
    // ==================== Своя модель自由操控конфигурация相Выкл方法 ====================
    
    /**
     * Настройки自由操控ВклВыкл
     * @param enabled true 表示Включить自由操控
     */
    public void setCustomFreeControlEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CUSTOM_FREE_CONTROL_ENABLED, enabled).apply();
        AppLog.d(TAG, "自由操控Настройки: " + (enabled ? "Включить" : "Отключить"));
    }
    
    /**
     * Получение自由操控ВклВыклСтатус
     * @return true 表示Включить自由操控
     */
    public boolean isCustomFreeControlEnabled() {
        return prefs.getBoolean(KEY_CUSTOM_FREE_CONTROL_ENABLED, false);
    }
    
    /**
     * Настройки按钮样式
     * @param style 按钮样式（BUTTON_STYLE_STANDARD / BUTTON_STYLE_MULTI)
     */
    public void setCustomButtonStyle(String style) {
        prefs.edit().putString(KEY_CUSTOM_BUTTON_STYLE, style).apply();
        AppLog.d(TAG, "按钮样式Настройки: " + style);
    }
    
    /**
     * Получение按钮样式
     * @return 按钮样式，По умолчанию为Стандартные кнопки
     */
    public String getCustomButtonStyle() {
        return prefs.getString(KEY_CUSTOM_BUTTON_STYLE, BUTTON_STYLE_STANDARD);
    }
    
    /**
     * Настройки按钮布局方 к 
     * @param orientation 方 к （BUTTON_ORIENTATION_HORIZONTAL / BUTTON_ORIENTATION_VERTICAL)
     */
    public void setCustomButtonOrientation(String orientation) {
        prefs.edit().putString(KEY_CUSTOM_BUTTON_ORIENTATION, orientation).apply();
        AppLog.d(TAG, "按钮布局方 к Настройки: " + orientation);
    }
    
    /**
     * Получение按钮布局方 к 
     * @return 布局方 к ，По умолчанию为Горизонтальная
     */
    public String getCustomButtonOrientation() {
        return prefs.getString(KEY_CUSTOM_BUTTON_ORIENTATION, BUTTON_ORIENTATION_HORIZONTAL);
    }
    
    /**
     * Сохранить自定义布局数据（JSON格式)
     * @param layoutDataJson 布局数据JSON字符串
     */
    public void setCustomLayoutData(String layoutDataJson) {
        prefs.edit().putString(KEY_CUSTOM_LAYOUT_DATA, layoutDataJson).apply();
        AppLog.d(TAG, "自定义布局数据Сохранить");
    }
    
    /**
     * Получение自定义布局数据
     * @return 布局数据JSON字符串，Если Не Настройки返回null
     */
    public String getCustomLayoutData() {
        return prefs.getString(KEY_CUSTOM_LAYOUT_DATA, null);
    }
    
    /**
     * очистка自定义布局数据
     */
    public void clearCustomLayoutData() {
        prefs.edit().remove(KEY_CUSTOM_LAYOUT_DATA).apply();
        AppLog.d(TAG, "自定义布局数据очистка");
    }
    
    /**
     * 根据Поворот 角度计算实际显示比例
     * @param width 原始宽度
     * @param height 原始Высокий度
     * @param rotation Поворот 角度 (0/90/180/270)
     * @return [displayWidth, displayHeight]
     */
    public static int[] calculateDisplayRatio(int width, int height, int rotation) {
        if (rotation == 90 || rotation == 270) {
            // Поворот 90°или270°时，宽Высокий互换
            return new int[]{height, width};
        }
        return new int[]{width, height};
    }
    
    /**
     * Получение按钮样式 显示名称
     */
    public static String getButtonStyleDisplayName(String style) {
        if (BUTTON_STYLE_MULTI.equals(style)) {
            return "Несколько кнопок";
        }
        return "Стандарт";
    }
    
    /**
     * Получение按钮方 к  显示名称
     */
    public static String getButtonOrientationDisplayName(String orientation) {
        if (BUTTON_ORIENTATION_VERTICAL.equals(orientation)) {
            return "Вертикальный";
        }
        return "Горизонтальный";
    }
    
    // ==================== 版本обновлениеконфигурация相Выкл方法 ====================
    
    /**
     * НастройкиобновлениеАдрес сервера
     * @param url Адрес сервера（если https://example.com/update/)
     */
    public void setUpdateServerUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            prefs.edit().remove(KEY_UPDATE_SERVER_URL).apply();
            AppLog.d(TAG, "очисткаобновлениеАдрес сервера");
        } else {
            prefs.edit().putString(KEY_UPDATE_SERVER_URL, url.trim()).apply();
            AppLog.d(TAG, "обновлениеАдрес сервераНастройки: " + url.trim());
        }
    }
    
    /**
     * ПолучениеобновлениеАдрес сервера
     * @return Адрес сервера，По умолчанию为官方обновлениеСервис器
     */
    public String getUpdateServerUrl() {
        String url = prefs.getString(KEY_UPDATE_SERVER_URL, DEFAULT_UPDATE_SERVER_URL);
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_UPDATE_SERVER_URL;
        }
        return url;
    }
    
    /**
     * проверка 否конфигурацияобновлениеСервис器
     * @return true 表示конфигурация
     */
    public boolean hasUpdateServerUrl() {
        return getUpdateServerUrl() != null;
    }

    // ==================== 全景影像避让конфигурация相Выкл方法 ====================

    /**
     * Настройки全景影像避让ВклВыкл
     */
    public void setAvmAvoidanceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AVM_AVOIDANCE_ENABLED, enabled).apply();
        AppLog.d(TAG, "全景影像避让Настройки: " + (enabled ? "Включить" : "Отключить"));
    }

    /**
     * Получение全景影像避让ВклВыклСтатус
     */
    public boolean isAvmAvoidanceEnabled() {
        return prefs.getBoolean(KEY_AVM_AVOIDANCE_ENABLED, false);
    }

    /**
     * Настройки全景影像避让 Activity名称
     */
    public void setAvmAvoidanceActivity(String activityName) {
        prefs.edit().putString(KEY_AVM_AVOIDANCE_ACTIVITY, activityName).apply();
        AppLog.d(TAG, "全景影像避让Activity: " + activityName);
    }

    /**
     * Получение全景影像避让 Activity名称
     */
    public String getAvmAvoidanceActivity() {
        return prefs.getString(KEY_AVM_AVOIDANCE_ACTIVITY, "com.geely.avm_app.AvmRenderActivity");
    }

    // ==================== 定制键唤醒конфигурация相Выкл方法 ====================

    /**
     * Настройки定制键唤醒ВклВыкл
     */
    public void setCustomKeyWakeupEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CUSTOM_KEY_WAKEUP_ENABLED, enabled).apply();
        AppLog.d(TAG, "定制键唤醒Настройки: " + (enabled ? "Включить" : "Отключить"));
    }

    /**
     * Получение定制键唤醒ВклВыклСтатус
     */
    public boolean isCustomKeyWakeupEnabled() {
        return prefs.getBoolean(KEY_CUSTOM_KEY_WAKEUP_ENABLED, false);
    }

    /**
     * Настройки速度阈值（ сек.速 m/s)
     */
    public void setCustomKeySpeedThreshold(float threshold) {
        prefs.edit().putFloat(KEY_CUSTOM_KEY_SPEED_THRESHOLD, threshold).apply();
        AppLog.d(TAG, "定制键速度阈值: " + threshold + " m/s");
    }

    /**
     * Получение速度阈值（ сек.速 m/s)，По умолчанию8.34
     */
    public float getCustomKeySpeedThreshold() {
        return prefs.getFloat(KEY_CUSTOM_KEY_SPEED_THRESHOLD, 8.34f);
    }

    /**
     * Настройки速度属性ID
     */
    public void setCustomKeySpeedPropId(int propId) {
        prefs.edit().putInt(KEY_CUSTOM_KEY_SPEED_PROP_ID, propId).apply();
    }

    /**
     * Получение速度属性ID，По умолчанию291504647
     */
    public int getCustomKeySpeedPropId() {
        return prefs.getInt(KEY_CUSTOM_KEY_SPEED_PROP_ID, 291504647);
    }

    /**
     * Настройки按钮属性ID
     */
    public void setCustomKeyButtonPropId(int propId) {
        prefs.edit().putInt(KEY_CUSTOM_KEY_BUTTON_PROP_ID, propId).apply();
    }

    /**
     * Получение按钮属性ID，По умолчанию557872183
     */
    public int getCustomKeyButtonPropId() {
        return prefs.getInt(KEY_CUSTOM_KEY_BUTTON_PROP_ID, 557872183);
    }

    // ==================== 前轮/后轮режим视图конфигурация相Выкл方法 ====================

    /**
     * Настройки前轮режим左视图参数
     */
    public void setFrontWheelLeftViewParams(int width, int height, int x, int y, int rotation) {
        prefs.edit()
                .putInt(KEY_FRONT_WHEEL_LEFT_WIDTH, width)
                .putInt(KEY_FRONT_WHEEL_LEFT_HEIGHT, height)
                .putInt(KEY_FRONT_WHEEL_LEFT_X, x)
                .putInt(KEY_FRONT_WHEEL_LEFT_Y, y)
                .putInt(KEY_FRONT_WHEEL_LEFT_ROTATION, rotation)
                .apply();
        AppLog.d(TAG, "前轮режим左视图参数Сохранить: " + width + "x" + height + " @(" + x + "," + y + ") Поворот " + rotation + "°");
    }

    /**
     * Получение前轮режим左视图宽度
     */
    public int getFrontWheelLeftWidth(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_LEFT_WIDTH, defaultValue);
    }

    /**
     * Получение前轮режим左视图Высокий度
     */
    public int getFrontWheelLeftHeight(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_LEFT_HEIGHT, defaultValue);
    }

    /**
     * Получение前轮режим左视图XПозиция
     */
    public int getFrontWheelLeftX(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_LEFT_X, defaultValue);
    }

    /**
     * Получение前轮режим左视图YПозиция
     */
    public int getFrontWheelLeftY(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_LEFT_Y, defaultValue);
    }

    /**
     * Получение前轮режим左视图Поворот 角度
     */
    public int getFrontWheelLeftRotation(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_LEFT_ROTATION, defaultValue);
    }

    /**
     * Настройки前轮режим右视图参数
     */
    public void setFrontWheelRightViewParams(int width, int height, int x, int y, int rotation) {
        prefs.edit()
                .putInt(KEY_FRONT_WHEEL_RIGHT_WIDTH, width)
                .putInt(KEY_FRONT_WHEEL_RIGHT_HEIGHT, height)
                .putInt(KEY_FRONT_WHEEL_RIGHT_X, x)
                .putInt(KEY_FRONT_WHEEL_RIGHT_Y, y)
                .putInt(KEY_FRONT_WHEEL_RIGHT_ROTATION, rotation)
                .apply();
        AppLog.d(TAG, "前轮режим右视图参数Сохранить: " + width + "x" + height + " @(" + x + "," + y + ") Поворот " + rotation + "°");
    }

    /**
     * Получение前轮режим右视图宽度
     */
    public int getFrontWheelRightWidth(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_RIGHT_WIDTH, defaultValue);
    }

    /**
     * Получение前轮режим右视图Высокий度
     */
    public int getFrontWheelRightHeight(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_RIGHT_HEIGHT, defaultValue);
    }

    /**
     * Получение前轮режим右视图XПозиция
     */
    public int getFrontWheelRightX(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_RIGHT_X, defaultValue);
    }

    /**
     * Получение前轮режим右视图YПозиция
     */
    public int getFrontWheelRightY(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_RIGHT_Y, defaultValue);
    }

    /**
     * Получение前轮режим右视图Поворот 角度
     */
    public int getFrontWheelRightRotation(int defaultValue) {
        return prefs.getInt(KEY_FRONT_WHEEL_RIGHT_ROTATION, defaultValue);
    }

    /**
     * Настройки后轮режим左视图参数
     */
    public void setRearWheelLeftViewParams(int width, int height, int x, int y, int rotation) {
        prefs.edit()
                .putInt(KEY_REAR_WHEEL_LEFT_WIDTH, width)
                .putInt(KEY_REAR_WHEEL_LEFT_HEIGHT, height)
                .putInt(KEY_REAR_WHEEL_LEFT_X, x)
                .putInt(KEY_REAR_WHEEL_LEFT_Y, y)
                .putInt(KEY_REAR_WHEEL_LEFT_ROTATION, rotation)
                .apply();
        AppLog.d(TAG, "后轮режим左视图参数Сохранить: " + width + "x" + height + " @(" + x + "," + y + ") Поворот " + rotation + "°");
    }

    /**
     * Получение后轮режим左视图宽度
     */
    public int getRearWheelLeftWidth(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_LEFT_WIDTH, defaultValue);
    }

    /**
     * Получение后轮режим左视图Высокий度
     */
    public int getRearWheelLeftHeight(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_LEFT_HEIGHT, defaultValue);
    }

    /**
     * Получение后轮режим左视图XПозиция
     */
    public int getRearWheelLeftX(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_LEFT_X, defaultValue);
    }

    /**
     * Получение后轮режим左视图YПозиция
     */
    public int getRearWheelLeftY(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_LEFT_Y, defaultValue);
    }

    /**
     * Получение后轮режим左视图Поворот 角度
     */
    public int getRearWheelLeftRotation(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_LEFT_ROTATION, defaultValue);
    }

    /**
     * Настройки后轮режим右视图参数
     */
    public void setRearWheelRightViewParams(int width, int height, int x, int y, int rotation) {
        prefs.edit()
                .putInt(KEY_REAR_WHEEL_RIGHT_WIDTH, width)
                .putInt(KEY_REAR_WHEEL_RIGHT_HEIGHT, height)
                .putInt(KEY_REAR_WHEEL_RIGHT_X, x)
                .putInt(KEY_REAR_WHEEL_RIGHT_Y, y)
                .putInt(KEY_REAR_WHEEL_RIGHT_ROTATION, rotation)
                .apply();
        AppLog.d(TAG, "后轮режим右视图参数Сохранить: " + width + "x" + height + " @(" + x + "," + y + ") Поворот " + rotation + "°");
    }

    /**
     * Получение后轮режим右视图宽度
     */
    public int getRearWheelRightWidth(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_RIGHT_WIDTH, defaultValue);
    }

    /**
     * Получение后轮режим右视图Высокий度
     */
    public int getRearWheelRightHeight(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_RIGHT_HEIGHT, defaultValue);
    }

    /**
     * Получение后轮режим右视图XПозиция
     */
    public int getRearWheelRightX(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_RIGHT_X, defaultValue);
    }

    /**
     * Получение后轮режим右视图YПозиция
     */
    public int getRearWheelRightY(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_RIGHT_Y, defaultValue);
    }

    /**
     * Получение后轮режим右视图Поворот 角度
     */
    public int getRearWheelRightRotation(int defaultValue) {
        return prefs.getInt(KEY_REAR_WHEEL_RIGHT_ROTATION, defaultValue);
    }

    /**
     * Настройки普通режим左视图参数
     */
    public void setNormalLeftViewParams(int width, int height, int x, int y, int rotation) {
        prefs.edit()
                .putInt(KEY_NORMAL_LEFT_WIDTH, width)
                .putInt(KEY_NORMAL_LEFT_HEIGHT, height)
                .putInt(KEY_NORMAL_LEFT_X, x)
                .putInt(KEY_NORMAL_LEFT_Y, y)
                .putInt(KEY_NORMAL_LEFT_ROTATION, rotation)
                .apply();
        AppLog.d(TAG, "普通режим左视图参数Сохранить: " + width + "x" + height + " @(" + x + "," + y + ") Поворот " + rotation + "°");
    }

    /**
     * Получение普通режим左视图宽度
     */
    public int getNormalLeftWidth(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_LEFT_WIDTH, defaultValue);
    }

    /**
     * Получение普通режим左视图Высокий度
     */
    public int getNormalLeftHeight(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_LEFT_HEIGHT, defaultValue);
    }

    /**
     * Получение普通режим左视图XПозиция
     */
    public int getNormalLeftX(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_LEFT_X, defaultValue);
    }

    /**
     * Получение普通режим左视图YПозиция
     */
    public int getNormalLeftY(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_LEFT_Y, defaultValue);
    }

    /**
     * Получение普通режим左视图Поворот 角度
     */
    public int getNormalLeftRotation(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_LEFT_ROTATION, defaultValue);
    }

    /**
     * Настройки普通режим右视图参数
     */
    public void setNormalRightViewParams(int width, int height, int x, int y, int rotation) {
        prefs.edit()
                .putInt(KEY_NORMAL_RIGHT_WIDTH, width)
                .putInt(KEY_NORMAL_RIGHT_HEIGHT, height)
                .putInt(KEY_NORMAL_RIGHT_X, x)
                .putInt(KEY_NORMAL_RIGHT_Y, y)
                .putInt(KEY_NORMAL_RIGHT_ROTATION, rotation)
                .apply();
        AppLog.d(TAG, "普通режим右视图参数Сохранить: " + width + "x" + height + " @(" + x + "," + y + ") Поворот " + rotation + "°");
    }

    /**
     * Получение普通режим右视图宽度
     */
    public int getNormalRightWidth(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_RIGHT_WIDTH, defaultValue);
    }

    /**
     * Получение普通режим右视图Высокий度
     */
    public int getNormalRightHeight(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_RIGHT_HEIGHT, defaultValue);
    }

    /**
     * Получение普通режим右视图XПозиция
     */
    public int getNormalRightX(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_RIGHT_X, defaultValue);
    }

    /**
     * Получение普通режим右视图YПозиция
     */
    public int getNormalRightY(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_RIGHT_Y, defaultValue);
    }

    /**
     * Получение普通режим右视图Поворот 角度
     */
    public int getNormalRightRotation(int defaultValue) {
        return prefs.getInt(KEY_NORMAL_RIGHT_ROTATION, defaultValue);
    }

    /**
     * Сброс前轮режим视图参数为По умолчанию值
     */
    public void resetFrontWheelViewParams() {
        setFrontWheelLeftViewParams(1120, 662, 10, 397, 270);
        setFrontWheelRightViewParams(1211, 662, -76, 502, 90);
        AppLog.d(TAG, "前轮режим视图参数Сброс为По умолчанию值");
    }

    /**
     * Сброс后轮режим视图参数为По умолчанию值
     */
    public void resetRearWheelViewParams() {
        setRearWheelLeftViewParams(1120, 662, 10, -624, 270);
        setRearWheelRightViewParams(1298, 662, -164, -702, 90);
        AppLog.d(TAG, "后轮режим视图参数Сброс为По умолчанию值");
    }

    /**
     * Сброс普通режим视图参数为По умолчанию值
     * 普通режим保持车辆控制区域可见，左右视图宽度相等
     */
    public void resetNormalViewParams(int defaultLeftWidth, int defaultLeftHeight, int defaultRightWidth, int defaultRightHeight, int containerHeight) {
        int halfHeight = (containerHeight - 20) / 2;
        int padding = 10;
        int vehicleControlWidth = 280;
        
        // 计算左右视图宽度，使其相等且右视图右边缘 и 后视图 齐
        int totalViewWidth = defaultLeftWidth + defaultRightWidth + vehicleControlWidth + padding * 4;
        //  от 传入 宽度反推容器宽度，这里简化处理
        int leftViewWidth = defaultLeftWidth;
        int rightViewWidth = defaultRightWidth;

        setNormalLeftViewParams(leftViewWidth, halfHeight, padding, padding * 2 + halfHeight, 0);
        setNormalRightViewParams(rightViewWidth, halfHeight, padding * 2 + leftViewWidth + vehicleControlWidth, padding * 2 + halfHeight, 0);
        AppLog.d(TAG, "普通режим视图参数Сброс为По умолчанию值");
    }
}
