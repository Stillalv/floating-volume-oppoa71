package com.volumefloat;

import android.animation.ValueAnimator;
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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

public class FloatingVolumeService extends Service {

    private WindowManager mWindowManager;
    private View mFloatingWidget;
    private WindowManager.LayoutParams paramsWidget;

    private View mRemoveTargetView;
    private WindowManager.LayoutParams paramsRemoveTarget;
    private FrameLayout removeCircle;

    private AudioManager audioManager;
    private Vibrator vibrator;

    private FrameLayout bubbleWrapper;
    private ImageView iconMainBubble;
    private LinearLayout menuExpanded;
    private FrameLayout btnMuteToggle, btnVolDown, btnVolUp, btnShowSlider, btnCloseMenu;
    private ImageView iconMute;
    private TextView tvVolumeLevel;

    private boolean isExpanded = false;
    private boolean isOverRemoveTarget = false;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private int touchSlop = 0;

    private static final String CHANNEL_ID = "FloatingVolumeChannel";
    private static final int NOTIFICATION_ID = 101;
    public static boolean isRunning = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        if (touchSlop < 20) touchSlop = 30;

        calculateScreenDimensions();

        // 1. Inisialisasi Floating Widget
        initFloatingWidget();

        // 2. Inisialisasi Drop Target 'X' di Bawah
        initRemoveTargetView();

        // 3. Setup Touch & Drag Listener
        setupTouchAndDrag();

        // 4. Jalankan sebagai Foreground Service
        startAsForegroundService();
    }

    private void calculateScreenDimensions() {
        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    private int getOverlayWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    private void initFloatingWidget() {
        mFloatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null);

        paramsWidget = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        paramsWidget.gravity = Gravity.TOP | Gravity.START;
        paramsWidget.x = dpToPx(16);
        paramsWidget.y = screenHeight / 3;

        mWindowManager.addView(mFloatingWidget, paramsWidget);

        bubbleWrapper = mFloatingWidget.findViewById(R.id.bubble_wrapper);
        iconMainBubble = mFloatingWidget.findViewById(R.id.icon_main_bubble);
        menuExpanded = mFloatingWidget.findViewById(R.id.layout_expanded_menu);

        btnMuteToggle = mFloatingWidget.findViewById(R.id.btn_mute_toggle);
        iconMute = mFloatingWidget.findViewById(R.id.icon_mute);
        btnVolDown = mFloatingWidget.findViewById(R.id.btn_volume_down);
        tvVolumeLevel = mFloatingWidget.findViewById(R.id.tv_volume_level);
        btnVolUp = mFloatingWidget.findViewById(R.id.btn_volume_up);
        btnShowSlider = mFloatingWidget.findViewById(R.id.btn_show_slider);
        btnCloseMenu = mFloatingWidget.findViewById(R.id.btn_close_menu);

        updateVolumeDisplay();

        // Tombol Volume Turun (-)
        btnVolDown.setOnClickListener(v -> {
            triggerHaptic();
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                updateVolumeDisplay();
            }
        });

        // Tombol Volume Naik (+)
        btnVolUp.setOnClickListener(v -> {
            triggerHaptic();
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                updateVolumeDisplay();
            }
        });

        // Tombol Mute / Unmute
        btnMuteToggle.setOnClickListener(v -> {
            triggerHaptic();
            if (audioManager != null) {
                int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (currentVol > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                } else {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, maxVol / 2), 0);
                }
                updateVolumeDisplay();
            }
        });

        // Tombol Show Full Native Slider
        btnShowSlider.setOnClickListener(v -> {
            triggerHaptic();
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
            }
        });

        // Tombol Tutup Mini Menu
        btnCloseMenu.setOnClickListener(v -> {
            triggerHaptic();
            toggleMenu(false);
        });
    }

    private void initRemoveTargetView() {
        mRemoveTargetView = LayoutInflater.from(this).inflate(R.layout.floating_remove_target_layout, null);
        removeCircle = mRemoveTargetView.findViewById(R.id.remove_target_circle);

        paramsRemoveTarget = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        paramsRemoveTarget.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        paramsRemoveTarget.y = dpToPx(60);

        mRemoveTargetView.setVisibility(View.GONE);
        mWindowManager.addView(mRemoveTargetView, paramsRemoveTarget);
    }

    private void updateVolumeDisplay() {
        if (audioManager == null || tvVolumeLevel == null) return;
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int percent = (int) (((float) current / Math.max(1, max)) * 100);
        tvVolumeLevel.setText(percent + "%");

        if (current == 0) {
            iconMute.setImageResource(R.drawable.ic_lucide_volume_2);
            iconMute.setColorFilter(0xFF10B981); // Hijau untuk unmute
        } else {
            iconMute.setImageResource(R.drawable.ic_lucide_volume_x);
            iconMute.setColorFilter(0xFFEF4444); // Merah untuk mute
        }
    }

    private void toggleMenu(boolean expand) {
        isExpanded = expand;
        if (isExpanded) {
            updateVolumeDisplay();
            menuExpanded.setVisibility(View.VISIBLE);

            // Perhitungan lebar eksplisit di DP agar tidak terpotong di Android 7 / ColorOS
            int totalExpandedWidth = dpToPx(56 + 8 + 260); // bubble + margin + expanded bar

            if (paramsWidget.x + totalExpandedWidth > screenWidth - dpToPx(16)) {
                paramsWidget.x = Math.max(dpToPx(16), screenWidth - totalExpandedWidth - dpToPx(16));
            }
            if (paramsWidget.x < dpToPx(16)) {
                paramsWidget.x = dpToPx(16);
            }

            paramsWidget.width = WindowManager.LayoutParams.WRAP_CONTENT;
            if (mWindowManager != null && mFloatingWidget != null) {
                mFloatingWidget.requestLayout();
                mWindowManager.updateViewLayout(mFloatingWidget, paramsWidget);
            }
        } else {
            menuExpanded.setVisibility(View.GONE);
            paramsWidget.width = WindowManager.LayoutParams.WRAP_CONTENT;
            if (mWindowManager != null && mFloatingWidget != null) {
                mFloatingWidget.requestLayout();
                mWindowManager.updateViewLayout(mFloatingWidget, paramsWidget);
            }
            snapToEdge();
        }
    }

    private void setupTouchAndDrag() {
        bubbleWrapper.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchStartTime = 0;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartTime = System.currentTimeMillis();
                        initialX = paramsWidget.x;
                        initialY = paramsWidget.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        isOverRemoveTarget = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        double distance = Math.hypot(deltaX, deltaY);

                        if (distance > touchSlop * 1.5) {
                            if (!isDragging) {
                                isDragging = true;
                                mRemoveTargetView.setVisibility(View.VISIBLE);
                                if (isExpanded) {
                                    isExpanded = false;
                                    menuExpanded.setVisibility(View.GONE);
                                }
                            }

                            paramsWidget.x = (int) (initialX + deltaX);
                            paramsWidget.y = (int) (initialY + deltaY);
                            if (mWindowManager != null) {
                                mWindowManager.updateViewLayout(mFloatingWidget, paramsWidget);
                            }

                            checkRemoveTargetCollision(event.getRawX(), event.getRawY());
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        mRemoveTargetView.setVisibility(View.GONE);
                        long pressDuration = System.currentTimeMillis() - touchStartTime;
                        double totalMovement = Math.hypot(event.getRawX() - initialTouchX, event.getRawY() - initialTouchY);

                        if (isDragging) {
                            if (isOverRemoveTarget) {
                                triggerHapticLong();
                                Toast.makeText(FloatingVolumeService.this, "Widget volume dinonaktifkan", Toast.LENGTH_SHORT).show();
                                stopSelf();
                                return true;
                            } else {
                                snapToEdge();
                            }
                        } else {
                            // JIKA GERAKAN KECIL ATAU TEKAN SINGKAT -> PASTI SINGLE TAP!
                            if (pressDuration < 500 || totalMovement <= touchSlop * 2) {
                                triggerHaptic();
                                toggleMenu(!isExpanded);
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void checkRemoveTargetCollision(float rawX, float rawY) {
        int targetYThreshold = screenHeight - dpToPx(150);
        int targetXCenter = screenWidth / 2;
        int targetXThreshold = dpToPx(80);

        if (rawY > targetYThreshold && Math.abs(rawX - targetXCenter) < targetXThreshold) {
            if (!isOverRemoveTarget) {
                isOverRemoveTarget = true;
                removeCircle.setBackgroundResource(R.drawable.bg_remove_target_active);
                triggerHaptic();
            }
        } else {
            if (isOverRemoveTarget) {
                isOverRemoveTarget = false;
                removeCircle.setBackgroundResource(R.drawable.bg_remove_target);
            }
        }
    }

    private void snapToEdge() {
        if (isExpanded) return; // Jangan snap saat menu sedang terbuka
        int midX = screenWidth / 2;
        int targetX = (paramsWidget.x >= midX) ? (screenWidth - dpToPx(56 + 16)) : dpToPx(16);

        ValueAnimator animator = ValueAnimator.ofInt(paramsWidget.x, targetX);
        animator.setDuration(180);
        animator.addUpdateListener(animation -> {
            paramsWidget.x = (int) animation.getAnimatedValue();
            if (mFloatingWidget != null && mWindowManager != null) {
                mWindowManager.updateViewLayout(mFloatingWidget, paramsWidget);
            }
        });
        animator.start();
    }

    private void triggerHaptic() {
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(30);
        }
    }

    private void triggerHapticLong() {
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(80);
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
                .setContentTitle("Floating Volume Aktif")
                .setContentText("Tap bubble untuk kontrol volume, atau tahan & tarik ke X untuk tutup")
                .setSmallIcon(R.drawable.ic_lucide_volume_2)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (mFloatingWidget != null && mWindowManager != null) {
            mWindowManager.removeView(mFloatingWidget);
        }
        if (mRemoveTargetView != null && mWindowManager != null) {
            mWindowManager.removeView(mRemoveTargetView);
        }
    }
}
