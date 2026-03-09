package com.kooo.evcam.playback;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.VideoView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 多 кам.Видео同步Воспр.управление器
 * поддержка1-4 кам.Видео同时Воспр.，并保持同步
 */
public class MultiVideoPlayerManager {
    private static final String TAG = "MultiVideoPlayerManager";

    /** Поддерживаемые 倍速 */
    public static final float[] SPEED_OPTIONS = {0.5f, 1.0f, 1.5f, 2.0f};
    
    private final Context context;
    private final Handler handler;

    /** 各Позиция VideoView */
    private VideoView videoFront;
    private VideoView videoBack;
    private VideoView videoLeft;
    private VideoView videoRight;
    private VideoView videoSingle;  // 单 кам.режим用

    /** 各Позиция MediaPlayer引用（用于倍速控制) */
    private final Map<String, MediaPlayer> mediaPlayers = new HashMap<>();

    /** Текущийзагрузка Видео групп */
    private VideoGroup currentGroup;

    /** Воспр.Статус */
    private boolean isPlaying = false;
    private boolean isPrepared = false;
    private int preparedCount = 0;
    private int totalVideos = 0;
    private boolean isStopping = false;

    /** Текущий倍速 */
    private float currentSpeed = 1.0f;
    private int currentSpeedIndex = 1;  // По умолчанию1.0x

    /**  否单 кам.режим */
    private boolean isSingleMode = false;
    private String singleModePosition = VideoGroup.POSITION_FRONT;

    /** Видео时长（毫 сек.) */
    private int duration = 0;

    /** Воспр.Статус监听器 */
    private OnPlaybackListener playbackListener;

    public interface OnPlaybackListener {
        void onPrepared(int duration);
        void onProgressUpdate(int currentPosition);
        void onPlaybackStateChanged(boolean isPlaying);
        void onCompletion();
        void onError(String message);
        /** 单 кам.Видео准备好时回调（用于控制 UI 显示) */
        default void onSingleVideoPrepared() {}
    }

    public MultiVideoPlayerManager(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * НастройкиVideoView引用
     */
    public void setVideoViews(VideoView front, VideoView back, VideoView left, VideoView right, VideoView single) {
        this.videoFront = front;
        this.videoBack = back;
        this.videoLeft = left;
        this.videoRight = right;
        this.videoSingle = single;
    }

    /**
     * НастройкиВоспр.监听器
     */
    public void setPlaybackListener(OnPlaybackListener listener) {
        this.playbackListener = listener;
    }

    /**
     * загрузкаВидео групп
     */
    public void loadVideoGroup(VideoGroup group) {
        // ОстановкаТекущийВоспр.
        stopAll();

        this.currentGroup = group;
        this.isPrepared = false;
        this.preparedCount = 0;
        this.totalVideos = 0;
        this.duration = 0;
        this.mediaPlayers.clear();

        if (group == null) {
            return;
        }

        // 统计要загрузка Видео数量
        if (group.hasVideo(VideoGroup.POSITION_FRONT)) totalVideos++;
        if (group.hasVideo(VideoGroup.POSITION_BACK)) totalVideos++;
        if (group.hasVideo(VideoGroup.POSITION_LEFT)) totalVideos++;
        if (group.hasVideo(VideoGroup.POSITION_RIGHT)) totalVideos++;

        if (totalVideos == 0) {
            if (playbackListener != null) {
                playbackListener.onError("No video files in this group");
            }
            return;
        }

        // загрузка各ПозицияВидео
        loadVideoIfExists(VideoGroup.POSITION_FRONT, group.getFrontVideo(), videoFront);
        loadVideoIfExists(VideoGroup.POSITION_BACK, group.getBackVideo(), videoBack);
        loadVideoIfExists(VideoGroup.POSITION_LEFT, group.getLeftVideo(), videoLeft);
        loadVideoIfExists(VideoGroup.POSITION_RIGHT, group.getRightVideo(), videoRight);
        
        // Если  单 кам.режим，такжезагрузка单 кам.Видео
        if (isSingleMode && videoSingle != null) {
            loadSingleModeVideo(0, false);
        }
    }

    /**
     * загрузка单 шт.Видео до VideoView
     */
    private void loadVideoIfExists(String position, File videoFile, VideoView videoView) {
        if (videoFile == null || !videoFile.exists() || videoView == null) {
            return;
        }

        try {
            Uri uri = Uri.fromFile(videoFile);
            videoView.setVideoURI(uri);

            videoView.setOnPreparedListener(mp -> {
                Log.d(TAG, "Video prepared: " + position);
                mediaPlayers.put(position, mp);

                // 行车记录仪Видео没有声音，Настройки静音
                mp.setVolume(0f, 0f);

                // 记录最长时长
                int videoDuration = mp.getDuration();
                if (videoDuration > duration) {
                    duration = videoDuration;
                }

                // Настройки倍速
                setMediaPlayerSpeed(mp, currentSpeed);

                preparedCount++;
                checkAllPrepared();
            });

            videoView.setOnCompletionListener(mp -> {
                // 所有ВидеоВоспр.завершение
                isPlaying = false;
                if (playbackListener != null) {
                    playbackListener.onPlaybackStateChanged(false);
                    playbackListener.onCompletion();
                }
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                if (!isStopping) {
                    Log.w(TAG, "Video error: " + position + ", what=" + what + ", extra=" + extra);
                    if (playbackListener != null) {
                        playbackListener.onError("Video error: " + position + ", what=" + what + ", extra=" + extra);
                    }
                }
                return true;
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to load video: " + position, e);
        }
    }

    /**
     * проверка 否所有Видеовсе准备好
     */
    private void checkAllPrepared() {
        if (preparedCount >= totalVideos) {
            isPrepared = true;
            Log.d(TAG, "All videos prepared, duration=" + duration);
            
            // 放弃音频焦点，让ДругоеПриложение（если音乐Воспр.器)продолжитьВоспр.
            abandonAudioFocus();
            
            if (playbackListener != null) {
                playbackListener.onPrepared(duration);
            }
            // автоматическиВкл始Воспр.
            play();
        }
    }

    /**
     * Вкл始Воспр.
     */
    public void play() {
        if (!isPrepared) {
            return;
        }

        isPlaying = true;

        if (isSingleMode) {
            // 单 кам.режимВоспр. videoSingle（用户看 до  Видео)
            if (videoSingle != null) {
                videoSingle.start();
            }
        } else {
            // 多 кам.режимВоспр.所有
            if (videoFront != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_FRONT)) {
                videoFront.start();
            }
            if (videoBack != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_BACK)) {
                videoBack.start();
            }
            if (videoLeft != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_LEFT)) {
                videoLeft.start();
            }
            if (videoRight != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_RIGHT)) {
                videoRight.start();
            }
        }

        if (playbackListener != null) {
            playbackListener.onPlaybackStateChanged(true);
        }

        // Вкл始обновление进度
        startProgressUpdate();
    }

    /**
     * ПаузаВоспр.
     */
    public void pause() {
        isPlaying = false;

        if (videoFront != null) videoFront.pause();
        if (videoBack != null) videoBack.pause();
        if (videoLeft != null) videoLeft.pause();
        if (videoRight != null) videoRight.pause();
        if (videoSingle != null) videoSingle.pause();

        if (playbackListener != null) {
            playbackListener.onPlaybackStateChanged(false);
        }
    }

    /**
     * 切换Воспр./Пауза
     */
    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }

    /**
     * Остановка所有Воспр.
     */
    public void stopAll() {
        isPlaying = false;
        isPrepared = false;
        handler.removeCallbacksAndMessages(null);
        isStopping = true;

        try {
            if (videoFront != null) videoFront.stopPlayback();
            if (videoBack != null) videoBack.stopPlayback();
            if (videoLeft != null) videoLeft.stopPlayback();
            if (videoRight != null) videoRight.stopPlayback();
            if (videoSingle != null) videoSingle.stopPlayback();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping playback", e);
        }

        mediaPlayers.clear();
        isStopping = false;
    }

    /**
     * 跳转 до 指定Позиция
     */
    public void seekTo(int position) {
        if (!isPrepared) return;

        if (isSingleMode) {
            // 单 кам.режим：операция videoSingle（用户看 до  Видео)
            if (videoSingle != null) {
                videoSingle.seekTo(position);
            }
        } else {
            if (videoFront != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_FRONT)) {
                videoFront.seekTo(position);
            }
            if (videoBack != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_BACK)) {
                videoBack.seekTo(position);
            }
            if (videoLeft != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_LEFT)) {
                videoLeft.seekTo(position);
            }
            if (videoRight != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_RIGHT)) {
                videoRight.seekTo(position);
            }
        }
    }

    /**
     * ПолучениеТекущийВоспр.Позиция
     */
    public int getCurrentPosition() {
        // 返回ТекущийВоспр.Видео Позиция
        if (isSingleMode && videoSingle != null) {
            // 单 кам.режим优先 от  videoSingle ПолучениеПозиция
            try {
                int pos = videoSingle.getCurrentPosition();
                if (pos > 0) {
                    return pos;
                }
            } catch (Exception e) {
                // videoSingle 可能Не 准备好，попытка от 四宫格Получение
            }
            // 后备： от 四宫格 应Позиция ВидеоПолучение
            VideoView sourceVideo = getSingleModeVideoView();
            if (sourceVideo != null) {
                try {
                    return sourceVideo.getCurrentPosition();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        
        // 多 кам.режимили后备： от 四宫格Получение
        if (videoFront != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_FRONT)) {
            try {
                return videoFront.getCurrentPosition();
            } catch (Exception e) { /* ignore */ }
        }
        if (videoBack != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_BACK)) {
            try {
                return videoBack.getCurrentPosition();
            } catch (Exception e) { /* ignore */ }
        }
        if (videoLeft != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_LEFT)) {
            try {
                return videoLeft.getCurrentPosition();
            } catch (Exception e) { /* ignore */ }
        }
        if (videoRight != null && currentGroup != null && currentGroup.hasVideo(VideoGroup.POSITION_RIGHT)) {
            try {
                return videoRight.getCurrentPosition();
            } catch (Exception e) { /* ignore */ }
        }
        return 0;
    }

    /**
     * ПолучениеВидео总时长
     */
    public int getDuration() {
        return duration;
    }

    /**
     *  否Выполняется Воспр.
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * 循环切换倍速
     */
    public float cycleSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % SPEED_OPTIONS.length;
        currentSpeed = SPEED_OPTIONS[currentSpeedIndex];
        
        // Приложение新倍速 до 所有Воспр.器
        for (MediaPlayer mp : mediaPlayers.values()) {
            setMediaPlayerSpeed(mp, currentSpeed);
        }
        
        return currentSpeed;
    }

    /**
     * Настройки倍速
     */
    public void setSpeed(float speed) {
        currentSpeed = speed;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            if (Math.abs(SPEED_OPTIONS[i] - speed) < 0.01) {
                currentSpeedIndex = i;
                break;
            }
        }
        
        for (MediaPlayer mp : mediaPlayers.values()) {
            setMediaPlayerSpeed(mp, currentSpeed);
        }
    }

    /**
     * ПолучениеТекущий倍速
     */
    public float getCurrentSpeed() {
        return currentSpeed;
    }

    /**
     * НастройкиMediaPlayer Воспр.速度
     */
    private void setMediaPlayerSpeed(MediaPlayer mp, float speed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed));
            } catch (Exception e) {
                Log.e(TAG, "Failed to set playback speed", e);
            }
        }
    }

    /**
     * 放弃音频焦点，让ДругоеПриложение（если音乐Воспр.器)продолжитьВоспр.
     * 行车记录仪Видео没有声音，不необходимо抢占音频焦点
     */
    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+ использование新  AudioFocusRequest API
                AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .build())
                        .build();
                audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                // 旧版本 API
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    /**
     * Настройки单 кам./多 кам.режим
     */
    public void setSingleMode(boolean singleMode, String position) {
        // 先СохранитьТекущийВоспр.Позиция и Статус
        int savedPosition = 0;
        boolean wasPlaying = isPlaying;
        
        if (isPrepared && currentGroup != null) {
            savedPosition = getCurrentPosition();
        }
        
        this.isSingleMode = singleMode;
        if (position != null) {
            this.singleModePosition = position;
        }

        // Если 准备好，необходимо重新同步
        if (isPrepared && currentGroup != null) {
            if (singleMode) {
                // 切换 до 单 кам.：先Пауза多 кам.Видео
                if (videoFront != null) videoFront.pause();
                if (videoBack != null) videoBack.pause();
                if (videoLeft != null) videoLeft.pause();
                if (videoRight != null) videoRight.pause();
                // 将源Видео 内容显示 до 单 кам.VideoView
                loadSingleModeVideo(savedPosition, wasPlaying);
            } else {
                // 切换回多 кам.：Пауза单 кам.Видео
                if (videoSingle != null) {
                    videoSingle.pause();
                }
                // 直接 seek  до Сохранить Позиция
                seekTo(savedPosition);
                if (wasPlaying) {
                    play();
                } else {
                    // 确保所有ВидеовсеПауза
                    if (videoFront != null) videoFront.pause();
                    if (videoBack != null) videoBack.pause();
                    if (videoLeft != null) videoLeft.pause();
                    if (videoRight != null) videoRight.pause();
                    isPlaying = false;
                    if (playbackListener != null) {
                        playbackListener.onPlaybackStateChanged(false);
                    }
                }
            }
        }
    }

    /**
     * загрузка单 кам.режимВидео
     */
    private void loadSingleModeVideo(int seekPosition, boolean autoPlay) {
        if (currentGroup == null || videoSingle == null) return;

        File videoFile = currentGroup.getVideoFile(singleModePosition);
        if (videoFile != null && videoFile.exists()) {
            try {
                Uri uri = Uri.fromFile(videoFile);
                videoSingle.setVideoURI(uri);
                videoSingle.setOnPreparedListener(mp -> {
                    mediaPlayers.put("single", mp);
                    // 行车记录仪Видео没有声音，Настройки静音
                    mp.setVolume(0f, 0f);
                    setMediaPlayerSpeed(mp, currentSpeed);
                    // 放弃音频焦点
                    abandonAudioFocus();
                    // Видео准备好后再 seek  и Воспр.
                    mp.seekTo(seekPosition);
                    
                    // Уведомление UI 单 кам.Видео准备好（可以显示画面)
                    if (playbackListener != null) {
                        playbackListener.onSingleVideoPrepared();
                    }
                    
                    if (autoPlay) {
                        mp.start();
                        isPlaying = true;
                        startProgressUpdate();
                        // Уведомление UI обновление按钮Статус
                        if (playbackListener != null) {
                            playbackListener.onPlaybackStateChanged(true);
                        }
                    } else {
                        // 确保ВидеоПауза（某些设备 seek 后会автоматическиВоспр.)
                        mp.pause();
                        isPlaying = false;
                        // Уведомление UI обновление按钮Статус
                        if (playbackListener != null) {
                            playbackListener.onPlaybackStateChanged(false);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load single mode video", e);
            }
        }
    }

    /**
     * Получение单 кам.режим 应 VideoView
     */
    private VideoView getSingleModeVideoView() {
        switch (singleModePosition) {
            case VideoGroup.POSITION_FRONT:
                return videoFront;
            case VideoGroup.POSITION_BACK:
                return videoBack;
            case VideoGroup.POSITION_LEFT:
                return videoLeft;
            case VideoGroup.POSITION_RIGHT:
                return videoRight;
            default:
                return videoFront;
        }
    }

    /**
     *  否 单 кам.режим
     */
    public boolean isSingleMode() {
        return isSingleMode;
    }

    /**
     * Получение单 кам.режим Позиция
     */
    public String getSingleModePosition() {
        return singleModePosition;
    }

    /**
     * обновление单 кам.режим Позиция（不触发Видеозагрузка，толькообновлениеСтатус)
     */
    public void updateSingleModePosition(boolean singleMode, String position) {
        this.isSingleMode = singleMode;
        if (position != null) {
            this.singleModePosition = position;
        }
    }

    /**
     * Вкл始进度обновление
     */
    private void startProgressUpdate() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlaying && playbackListener != null) {
                    int position = getCurrentPosition();
                    playbackListener.onProgressUpdate(position);
                }
                if (isPlaying) {
                    handler.postDelayed(this, 200);
                }
            }
        }, 200);
    }

    /**
     * 释放资源
     */
    public void release() {
        stopAll();
        mediaPlayers.clear();
        playbackListener = null;
    }

    /**
     * проверка指定Позиция 否有Видео
     */
    public boolean hasVideo(String position) {
        return currentGroup != null && currentGroup.hasVideo(position);
    }

    /**
     * ПолучениеТекущийВидео групп
     */
    public VideoGroup getCurrentGroup() {
        return currentGroup;
    }
}
