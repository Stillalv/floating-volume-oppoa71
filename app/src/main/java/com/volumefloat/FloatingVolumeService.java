package com.volumefloat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

public class FloatingVolumeService extends Service {

    private WindowManager mWindowManager;
    private View mFloatingWidget;
    private WindowManager.LayoutParams params;
    private AudioManager audioManager;

    private ImageView iconMain;
    private LinearLayout menuExpanded;
    private ImageView btnVolUp, btnVolDown, btnShowSlider, btnClose;
    private boolean isExpanded = false;

    private static final String CHANNEL_ID = "FloatingVolumeChannel";
    private static final int NOTIFICATION_ID = 101;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mFloatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null);

        // Kompatibilitas Window Type untuk Android 7.1 (Oppo A71) dan versi lebih baru
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 200;

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (mWindowManager != null) {
            mWindowManager.addView(mFloatingWidget, params);
        }

        initViews();
        setupTouchListener();
        startAsForegroundService();
    }

    private void initViews() {
        iconMain = mFloatingWidget.findViewById(R.id.icon_main_bubble);
        menuExpanded = mFloatingWidget.findViewById(R.id.layout_expanded_menu);
        btnVolUp = mFloatingWidget.findViewById(R.id.btn_volume_up);
        btnVolDown = mFloatingWidget.findViewById(R.id.btn_volume_down);
        btnShowSlider = mFloatingWidget.findViewById(R.id.btn_show_slider);
        btnClose = mFloatingWidget.findViewById(R.id.btn_close_menu);

        // Volume Up (+)
        btnVolUp.setOnClickListener(v -> {
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            }
        });

        // Volume Down (-)
        btnVolDown.setOnClickListener(v -> {
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            }
        });

        // Tampilkan Slider Bawaan Android
        btnShowSlider.setOnClickListener(v -> {
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
            }
        });

        // Tutup expand menu
        btnClose.setOnClickListener(v -> toggleMenu(false));
    }

    private void toggleMenu(boolean expand) {
        isExpanded = expand;
        if (isExpanded) {
            menuExpanded.setVisibility(View.VISIBLE);
        } else {
            menuExpanded.setVisibility(View.GONE);
        }
    }

    private void setupTouchListener() {
        mFloatingWidget.findViewById(R.id.root_container).setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private static final int CLICK_ACTION_THRESHOLD = 15;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        if (mWindowManager != null) {
                            mWindowManager.updateViewLayout(mFloatingWidget, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        float deltaX = Math.abs(event.getRawX() - initialTouchX);
                        float deltaY = Math.abs(event.getRawY() - initialTouchY);

                        // Deteksi Sekali Tekan (Single Tap)
                        if (deltaX < CLICK_ACTION_THRESHOLD && deltaY < CLICK_ACTION_THRESHOLD) {
                            onBubbleSingleClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // Aksi ketika tombol bulat ditekan sekali
    private void onBubbleSingleClick() {
        if (!isExpanded) {
            // LANGSUNG MUNCULKAN SLIDER VOLUME BAWAAN SISTEM DI LAYAR
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
            }
            // Munculkan opsi expand mini
            toggleMenu(true);
        } else {
            toggleMenu(false);
        }
    }

    private void startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Volume Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Floating Volume Active")
                .setContentText("Sentuh bubble untuk atur volume HP")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingWidget != null && mWindowManager != null) {
            mWindowManager.removeView(mFloatingWidget);
        }
    }
}
