package com.ahmed.netfare;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "netfare_prefs";
    public static final String KEY_COMMISSION = "commission_percent";

    private TextView statusText;
    private EditText commissionInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        commissionInput = findViewById(R.id.commissionInput);
        Button saveButton = findViewById(R.id.saveButton);
        Button accessibilityButton = findViewById(R.id.accessibilityButton);
        Button overlayButton = findViewById(R.id.overlayButton);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float savedCommission = prefs.getFloat(KEY_COMMISSION, 15f);
        commissionInput.setText(String.valueOf(savedCommission));

        saveButton.setOnClickListener(v -> {
            String value = commissionInput.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                Toast.makeText(this, "دخل نسبة العمولة الأول", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                float commission = Float.parseFloat(value);
                prefs.edit().putFloat(KEY_COMMISSION, commission).apply();
                Toast.makeText(this, "تم الحفظ: " + commission + "%", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "رقم غير صحيح", Toast.LENGTH_SHORT).show();
            }
        });

        accessibilityButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        overlayButton.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, "صلاحية الظهور فوق التطبيقات مفعلة بالفعل", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean overlayGranted = Settings.canDrawOverlays(this);
        statusText.setText(overlayGranted
                ? getString(R.string.status_on)
                : getString(R.string.status_off));
    }
}
