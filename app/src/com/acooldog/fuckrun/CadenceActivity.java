package com.acooldog.fuckrun;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.acooldog.fuckrun.data.AppPrefs;
import com.acooldog.fuckrun.sim.SimController;

public class CadenceActivity extends Activity {
    private static final int REQ_OVERLAY = 21;

    private AppPrefs prefs;
    private Switch sw;
    private SeekBar seekMin, seekMax;
    private TextView tvMin, tvMax;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.acooldog.fuckrun.util.Theme.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadence);
        com.acooldog.fuckrun.util.Ui.ambient(this);
        prefs = new AppPrefs(this);
        sw = findViewById(R.id.switch_cadence);
        seekMin = findViewById(R.id.seek_min);
        seekMax = findViewById(R.id.seek_max);
        tvMin = findViewById(R.id.tv_min);
        tvMax = findViewById(R.id.tv_max);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_float).setOnClickListener(v -> toggleFloat());

        sw.setChecked(prefs.cadenceOn());
        seekMin.setProgress(Math.round(prefs.cadenceMin()) - 140);
        seekMax.setProgress(Math.round(prefs.cadenceMax()) - 140);
        render();

        sw.setOnCheckedChangeListener((b, checked) -> {
            prefs.setCadenceOn(checked);
            SimController.refresh(this);
        });

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (binding) return;
                int min = 140 + seekMin.getProgress();
                int max = 140 + seekMax.getProgress();
                if (max < min + 2) {
                    if (seekBar == seekMin) min = max - 2;
                    else max = min + 2;
                }
                prefs.setCadenceRange(min, max);
                binding = true;
                seekMin.setProgress(Math.round(prefs.cadenceMin()) - 140);
                seekMax.setProgress(Math.round(prefs.cadenceMax()) - 140);
                binding = false;
                render();
                SimController.refresh(CadenceActivity.this);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        seekMin.setOnSeekBarChangeListener(l);
        seekMax.setOnSeekBarChangeListener(l);
    }

    private void render() {
        tvMin.setText("最低步频 " + Math.round(prefs.cadenceMin()) + " 步/分");
        tvMax.setText("最高步频 " + Math.round(prefs.cadenceMax()) + " 步/分");
    }

    private void toggleFloat() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            return;
        }
        startForegroundService(new Intent(this, FloatingService.class));
        Toast.makeText(this, "已启用悬浮开关", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY && Settings.canDrawOverlays(this)) toggleFloat();
    }
}
