package com.kooo.evcam;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Locale;

/**
 * 内置ВидеоВоспр.器
 * поддержкаВоспр.、Пауза、进度控制等функция
 */
public class VideoPlayerActivity extends AppCompatActivity {
    private VideoView videoView;
    private TextView titleText;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private SeekBar seekBar;
    private Button btnPlayPause;
    private View controlsLayout;

    private Handler handler = new Handler();
    private boolean isPlaying = false;
    private boolean isDragging = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        videoView = findViewById(R.id.video_view);
        titleText = findViewById(R.id.video_title);
        currentTimeText = findViewById(R.id.current_time);
        totalTimeText = findViewById(R.id.total_time);
        seekBar = findViewById(R.id.seek_bar);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        controlsLayout = findViewById(R.id.controls_layout);
        View btnClose = findViewById(R.id.btn_close);

        // ПолучениеВидеоПуть
        String videoPath = getIntent().getStringExtra("video_path");
        if (videoPath == null || videoPath.isEmpty()) {
            Toast.makeText(this, "Неверный путь к видео", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            Toast.makeText(this, "Файл видео не найден", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Настройки标题
        titleText.setText(videoFile.getName());

        // загрузкаВидео
        loadVideo(videoFile);

        // Закрыто按钮
        btnClose.setOnClickListener(v -> finish());

        // Воспр./Пауза按钮
        btnPlayPause.setOnClickListener(v -> {
            if (isPlaying) {
                pauseVideo();
            } else {
                playVideo();
            }
        });

        // 进度条拖动
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTimeText.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isDragging = false;
                videoView.seekTo(seekBar.getProgress());
            }
        });

        // 点击Видео区域显示/隐藏控制栏
        videoView.setOnClickListener(v -> {
            if (controlsLayout.getVisibility() == View.VISIBLE) {
                controlsLayout.setVisibility(View.GONE);
            } else {
                controlsLayout.setVisibility(View.VISIBLE);
                // 3 сек.后автоматически隐藏
                handler.removeCallbacks(hideControlsRunnable);
                handler.postDelayed(hideControlsRunnable, 3000);
            }
        });
    }

    /**
     * загрузкаВидео
     */
    private void loadVideo(File videoFile) {
        try {
            Uri videoUri = Uri.fromFile(videoFile);
            videoView.setVideoURI(videoUri);

            // Видео准备завершение监听
            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    // 行车记录仪Видео没有声音，Настройки静音并放弃音频焦点
                    // 这样不会Пауза车机 Другое音频Воспр.（если音乐)
                    mp.setVolume(0f, 0f);
                    abandonAudioFocus();

                    int duration = videoView.getDuration();
                    seekBar.setMax(duration);
                    totalTimeText.setText(formatTime(duration));
                    currentTimeText.setText(formatTime(0));

                    // автоматическиВкл始Воспр.
                    playVideo();

                    // Вкл始обновление进度
                    updateProgress();
                }
            });

            // Воспр.завершение监听
            videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    isPlaying = false;
                    btnPlayPause.setText("Воспроизведение");
                    seekBar.setProgress(0);
                    currentTimeText.setText(formatTime(0));
                }
            });

            // Ошибка监听
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Toast.makeText(VideoPlayerActivity.this, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Не удалось загрузить видео: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Воспр.Видео
     */
    private void playVideo() {
        videoView.start();
        isPlaying = true;
        btnPlayPause.setText("Пауза");
    }

    /**
     * ПаузаВидео
     */
    private void pauseVideo() {
        videoView.pause();
        isPlaying = false;
        btnPlayPause.setText("Воспроизведение");
    }

    /**
     * обновлениеВоспр.进度
     */
    private void updateProgress() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlaying && !isDragging) {
                    int currentPosition = videoView.getCurrentPosition();
                    seekBar.setProgress(currentPosition);
                    currentTimeText.setText(formatTime(currentPosition));
                }
                if (isPlaying) {
                    handler.postDelayed(this, 100);
                }
            }
        }, 100);
    }

    /**
     * 格式化时间（毫 сек.转为 mm:ss)
     */
    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    /**
     * автоматически隐藏控制栏
     */
    private Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying) {
                controlsLayout.setVisibility(View.GONE);
            }
        }
    };

    /**
     * 放弃音频焦点，让ДругоеПриложение（если音乐Воспр.器)продолжитьВоспр.
     * 行车记录仪Видео没有声音，不необходимо抢占音频焦点
     */
    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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

    @Override
    protected void onPause() {
        super.onPause();
        if (isPlaying) {
            pauseVideo();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(hideControlsRunnable);
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }
}
