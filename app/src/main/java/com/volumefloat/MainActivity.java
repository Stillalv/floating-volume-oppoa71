package com.volumefloat;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ_CODE = 2001;

    private ImageView iconPermissionStatus;
    private TextView tvPermissionTitle;
    private TextView badgePermissionStatus;
    private Button btnGrantPermission;

    private ImageView iconMasterPower;
    private TextView tvServiceStatusDesc;
    private Button btnToggleService;

    private TextView tvTestVolumePercent;
    private SeekBar seekbarTestVolume;

    private AudioManager audioManager;
    private VolumeObserver volumeObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        initViews();
        setupListeners();
        setupVolumeSync();
    }

    private void initViews() {
        iconPermissionStatus = findViewById(R.id.icon_permission_status);
        tvPermissionTitle = findViewById(R.id.tv_permission_title);
        badgePermissionStatus = findViewById(R.id.badge_permission_status);
        btnGrantPermission = findViewById(R.id.btn_grant_permission);

        iconMasterPower = findViewById(R.id.icon_master_power);
        tvServiceStatusDesc = findViewById(R.id.tv_service_status_desc);
        btnToggleService = findViewById(R.id.btn_toggle_service);

        tvTestVolumePercent = findViewById(R.id.tv_test_volume_percent);
        seekbarTestVolume = findViewById(R.id.seekbar_test_volume);
    }

    private void setupListeners() {
        btnGrantPermission.setOnClickListener(v -> requestOverlayPermission());

        btnToggleService.setOnClickListener(v -> {
            if (!checkOverlayPermission()) {
                Toast.makeText(this, "Silakan beri izin tampilan melayang terlebih dahulu", Toast.LENGTH_SHORT).show();
                requestOverlayPermission();
                return;
            }

            Intent serviceIntent = new Intent(this, FloatingVolumeService.class);
            if (!FloatingVolumeService.isRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "Widget aktif! Tekan bubble untuk atur volume", Toast.LENGTH_SHORT).show();
            } else {
                stopService(serviceIntent);
                Toast.makeText(this, "Widget dinonaktifkan", Toast.LENGTH_SHORT).show();
            }
            updateServiceStateUI();
        });

        seekbarTestVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && audioManager != null) {
                    int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int targetVol = (int) (((float) progress / 100) * max);
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI);
                    tvTestVolumePercent.setText(progress + "%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupVolumeSync() {
        updateCurrentVolumeSlider();

        // Listen perubahan volume dari luar / tombol HP
        volumeObserver = new VolumeObserver(new Handler(Looper.getMainLooper()));
        getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver
        );
    }

    private void updateCurrentVolumeSlider() {
        if (audioManager == null) return;
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int percent = (int) (((float) current / max) * 100);
        seekbarTestVolume.setProgress(percent);
        tvTestVolumePercent.setText(percent + "%");
    }

    private class VolumeObserver extends ContentObserver {
        public VolumeObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateCurrentVolumeSlider();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionUI();
        updateServiceStateUI();
        updateCurrentVolumeSlider();
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, "Pilih 'Floating Volume' lalu aktifkan izinnya", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        }
    }

    private void updatePermissionUI() {
        boolean hasPermission = checkOverlayPermission();
        if (hasPermission) {
            iconPermissionStatus.setImageResource(R.drawable.ic_lucide_shield_check);
            badgePermissionStatus.setText("Sudah Aktif ✓");
            badgePermissionStatus.setTextColor(0xFF10B981); // Green
            btnGrantPermission.setVisibility(View.GONE);
        } else {
            iconPermissionStatus.setImageResource(R.drawable.ic_lucide_shield_alert);
            badgePermissionStatus.setText("Belum Aktif");
            badgePermissionStatus.setTextColor(0xFFF59E0B); // Amber
            btnGrantPermission.setVisibility(View.VISIBLE);
        }
    }

    private void updateServiceStateUI() {
        if (FloatingVolumeService.isRunning) {
            iconMasterPower.setColorFilter(0xFF10B981); // Green
            tvServiceStatusDesc.setText("Status: AKTIF (Bubble ada di layar)");
            tvServiceStatusDesc.setTextColor(0xFF38BDF8);
            btnToggleService.setText("Matikan Widget Melayang");
            btnToggleService.setBackgroundColor(0xFFEF4444); // Danger Red
        } else {
            iconMasterPower.setColorFilter(0xFF64748B); // Muted
            tvServiceStatusDesc.setText("Status: Nonaktif");
            tvServiceStatusDesc.setTextColor(0xFF94A3B8);
            btnToggleService.setText("Aktifkan Widget Melayang");
            btnToggleService.setBackgroundColor(0xFF0284C7); // Primary Blue
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (volumeObserver != null) {
            getContentResolver().unregisterContentObserver(volumeObserver);
        }
    }
}
