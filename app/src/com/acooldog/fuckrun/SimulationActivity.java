package com.acooldog.fuckrun;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.acooldog.fuckrun.data.AppPrefs;
import com.acooldog.fuckrun.data.RouteStore;
import com.acooldog.fuckrun.model.Route;
import com.acooldog.fuckrun.sim.Frame;
import com.acooldog.fuckrun.sim.SimConfig;
import com.acooldog.fuckrun.sim.SimController;

import java.util.Locale;

public class SimulationActivity extends Activity {
    private Route route;
    private TextView name, meta, hook, speedValue, cadenceValue;
    private TextView liveDistance, liveSpeed, liveTime, liveDetail;
    private RadioGroup mode;
    private View groupSpeed, groupCadence;
    private SeekBar seekSpeed, seekCadence;
    private EditText loops;
    private CompoundButton cbDyn, cbPath, cbAlt, cbNmea;
    private View btnStart, btnStop;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateLive();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.acooldog.fuckrun.util.Theme.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simulation);
        com.acooldog.fuckrun.util.Ui.ambient(this);

        String id = getIntent().getStringExtra("route_id");
        if (id == null || id.isEmpty()) id = new AppPrefs(this).lastRouteId();
        route = new RouteStore(this).get(id);
        if (route == null || route.points.size() < 2) {
            Toast.makeText(this, "路线不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        name = findViewById(R.id.tv_route_name);
        meta = findViewById(R.id.tv_route_meta);
        hook = findViewById(R.id.tv_hook_state);
        mode = findViewById(R.id.radio_mode);
        groupSpeed = findViewById(R.id.group_speed);
        groupCadence = findViewById(R.id.group_cadence);
        speedValue = findViewById(R.id.tv_speed_value);
        cadenceValue = findViewById(R.id.tv_cadence_value);
        seekSpeed = findViewById(R.id.seek_speed);
        seekCadence = findViewById(R.id.seek_cadence);
        loops = findViewById(R.id.et_loops);
        cbDyn = findViewById(R.id.cb_dyn);
        cbPath = findViewById(R.id.cb_path);
        cbAlt = findViewById(R.id.cb_alt);
        cbNmea = findViewById(R.id.cb_nmea);
        liveDistance = findViewById(R.id.tv_live_distance);
        liveSpeed = findViewById(R.id.tv_live_speed);
        liveTime = findViewById(R.id.tv_live_time);
        liveDetail = findViewById(R.id.tv_live_detail);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);

        name.setText(route.name);
        meta.setText(String.format(Locale.CHINA, "%d 个点 · %.0f m",
                route.points.size(), route.distance()));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_open_target).setOnClickListener(v -> openTarget());

        mode.setOnCheckedChangeListener((g, checkedId) -> {
            boolean speed = checkedId == R.id.radio_speed;
            groupSpeed.setVisibility(speed ? View.VISIBLE : View.GONE);
            groupCadence.setVisibility(speed ? View.GONE : View.VISIBLE);
        });

        seekSpeed.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                speedValue.setText(String.format(Locale.CHINA, "%.1f km/h", (p + 20) / 10.0));
            }
        });
        seekCadence.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                cadenceValue.setText(String.format(Locale.CHINA, "%d 步/分", 140 + p));
            }
        });
        speedValue.setText(String.format(Locale.CHINA, "%.1f km/h", (seekSpeed.getProgress() + 20) / 10.0));
        cadenceValue.setText(String.format(Locale.CHINA, "%d 步/分", 140 + seekCadence.getProgress()));

        android.util.Log.i("FuckRun/UI", "bind start=" + btnStart + " clickable=" + btnStart.isClickable()
                + " vis=" + btnStart.getVisibility());
        btnStart.setOnClickListener(v -> start());
        btnStop.setOnClickListener(v -> stop());
        setRunningUi(SimController.isRunning());
    }

    private abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private void setRunningUi(boolean running) {
        btnStart.setVisibility(running ? View.GONE : View.VISIBLE);
        btnStop.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    private void start() {
        android.util.Log.i("FuckRun/UI", "start clicked");
        SimConfig c = new SimConfig();
        c.mode = mode.getCheckedRadioButtonId() == R.id.radio_cadence
                ? SimConfig.MODE_CADENCE : SimConfig.MODE_SPEED;
        c.speedMps = (seekSpeed.getProgress() + 20) / 10.0 / 3.6;
        c.cadenceSpm = 140 + seekCadence.getProgress();
        try {
            c.loopCount = Math.max(1, Integer.parseInt(loops.getText().toString()));
        } catch (Exception e) {
            c.loopCount = 1;
        }
        c.dynamicIntensity = cbDyn.isChecked();
        c.pathVariation = cbPath.isChecked();
        c.altitudeVariation = cbAlt.isChecked();
        c.nmeaEnabled = cbNmea.isChecked();
        AppPrefs p = new AppPrefs(this);
        c.satellites = p.satellites();
        c.hdop = p.hdop();
        c.accuracy = p.accuracy();
        c.seed = System.currentTimeMillis() % 100000L;

        android.util.Log.i("FuckRun/UI", "calling SimController.start route=" + route.name + " pts=" + route.points.size());
        SimController.start(this, route, c);
        android.util.Log.i("FuckRun/UI", "start returned");
        setRunningUi(true);
        handler.post(tick);
        Toast.makeText(this, "已开始模拟，切到步道乐跑即可", Toast.LENGTH_SHORT).show();
    }

    private void stop() {
        SimController.stop(this);
        handler.removeCallbacks(tick);
        setRunningUi(false);
        resetLive();
    }

    private void updateLive() {
        long seen = new AppPrefs(this).hookTime();
        boolean connected = System.currentTimeMillis() - seen < 6000;
        hook.setText(connected ? "模块已连接 · 注入生效" : "等待步道乐跑进程…");
        hook.setTextColor(connected ? com.acooldog.fuckrun.util.Palette.of(this).green : com.acooldog.fuckrun.util.Palette.of(this).textSub);

        if (!SimController.isRunning()) {
            setRunningUi(false);
            return;
        }
        Frame f = SimController.frame();
        if (f == null) return;
        double total = SimController.totalDistance();
        long elapsed = SimController.startedAt() == 0 ? 0
                : android.os.SystemClock.elapsedRealtime() - SimController.startedAt();
        liveDistance.setText(String.format(Locale.CHINA, "%.0f / %.0f m", f.distance, total));
        liveSpeed.setText(String.format(Locale.CHINA, "%.1f km/h", f.speed * 3.6));
        liveTime.setText(fmt(elapsed));
        liveDetail.setText(String.format(Locale.CHINA, "纬度 %.5f  经度 %.5f  海拔 %.1f m  方位 %.0f°",
                f.lat, f.lng, f.altitude, f.bearing));
        if (f.finished) {
            Toast.makeText(this, "路线已完成", Toast.LENGTH_SHORT).show();
            stop();
        }
    }

    private void resetLive() {
        liveDistance.setText("0 / 0 m");
        liveSpeed.setText("0.0 km/h");
        liveTime.setText("00:00");
        liveDetail.setText("尚未开始");
    }

    private String fmt(long ms) {
        long s = ms / 1000;
        return String.format(Locale.CHINA, "%02d:%02d", s / 60, s % 60);
    }

    private void openTarget() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.lptiyu.tanke");
        if (i == null) {
            Toast.makeText(this, "未安装步道乐跑", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setRunningUi(SimController.isRunning());
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
}
