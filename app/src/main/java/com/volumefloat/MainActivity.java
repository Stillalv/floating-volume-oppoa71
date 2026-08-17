package com.volumefloat;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private Button btnToggleService;
    private TextView tvStatus;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggleService = findViewById(R.id.btn_toggle);
        tvStatus = findViewById(R.id.tv_status);

        btnToggleService.setOnClickListener(v -> {
            if (checkOverlayPermission()) {
                toggleFloatingService();
            } else {
                requestOverlayPermission();
            }
        });
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain' untuk Oppo A71 Anda", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        }
    }

    private void toggleFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingVolumeService.class);
        if (!isServiceRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            isServiceRunning = true;
            btnToggleService.setText("Matikan Tombol Volume Melayang");
            btnToggleService.setBackgroundColor(0xFFE53935); // Red
            tvStatus.setText("Status: AKTIF (Bubble sudah muncul di layar)");
            Toast.makeText(this, "Floating Volume aktif! Coba tekan bubble di layar", Toast.LENGTH_SHORT).show();
        } else {
            stopService(serviceIntent);
            isServiceRunning = false;
            btnToggleService.setText("Aktifkan Tombol Volume Melayang");
            btnToggleService.setBackgroundColor(0xFF1E88E5); // Blue
            tvStatus.setText("Status: Nonaktif");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (checkOverlayPermission()) {
                toggleFloatingService();
            } else {
                Toast.makeText(this, "Izin ditolak. Aplikasi butuh izin overlay untuk menampilkan tombol di layar.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
