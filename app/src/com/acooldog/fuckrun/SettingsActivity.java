package com.acooldog.fuckrun;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.acooldog.fuckrun.data.AppPrefs;
import com.acooldog.fuckrun.sim.SimController;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private AppPrefs prefs;
    private TextView status;
    private Switch swRoot, swMock, swLog, swAdBlock;
    private EditText etSat, etHdop, etAcc;
    private TextView tvTheme;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.acooldog.fuckrun.util.Theme.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        com.acooldog.fuckrun.util.Ui.ambient(this);
        prefs = new AppPrefs(this);
        status = findViewById(R.id.tv_status);
        swRoot = findViewById(R.id.sw_root);
        swMock = findViewById(R.id.sw_mock);
        swLog = findViewById(R.id.sw_log);
        swAdBlock = findViewById(R.id.sw_adblock);
        etSat = findViewById(R.id.et_satellites);
        etHdop = findViewById(R.id.et_hdop);
        etAcc = findViewById(R.id.et_accuracy);

        swRoot.setChecked(prefs.antiRoot());
        swMock.setChecked(prefs.antiMock());
        swLog.setChecked(prefs.silentLog());
        swAdBlock.setChecked(prefs.adBlock());
        tvTheme = findViewById(R.id.tv_theme);
        renderTheme();
        findViewById(R.id.btn_theme_blue).setOnClickListener(v -> applyTheme(com.acooldog.fuckrun.util.Theme.BLUE));
        findViewById(R.id.btn_theme_red).setOnClickListener(v -> applyTheme(com.acooldog.fuckrun.util.Theme.RED));
        etSat.setText(String.valueOf(prefs.satellites()));
        etHdop.setText(String.format(Locale.CHINA, "%.1f", prefs.hdop()));
        etAcc.setText(String.format(Locale.CHINA, "%.1f", prefs.accuracy()));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_lsposed).setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
            if (i == null) {
                Toast.makeText(this, "未找到 LSPosed 管理器", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(i);
            }
        });
        findViewById(R.id.btn_target).setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.lptiyu.tanke");
            if (i == null) {
                Toast.makeText(this, "未安装步道乐跑", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(i);
            }
        });
        findViewById(R.id.btn_save_params).setOnClickListener(v -> saveParams());

        swRoot.setOnCheckedChangeListener((b, c) -> {
            prefs.setAntiRoot(c);
            SimController.refresh(this);
        });
        swMock.setOnCheckedChangeListener((b, c) -> {
            prefs.setAntiMock(c);
            SimController.refresh(this);
        });
        swLog.setOnCheckedChangeListener((b, c) -> {
            prefs.setSilentLog(c);
            SimController.refresh(this);
        });
        swAdBlock.setOnCheckedChangeListener((b, c) -> {
            prefs.setAdBlock(c);
            SimController.refresh(this);
            Toast.makeText(this, c ? "已开启去广告，重启步道乐跑生效" : "已关闭去广告", Toast.LENGTH_SHORT).show();
        });
    }

    private void renderTheme() {
        boolean red = com.acooldog.fuckrun.util.Theme.isRed(this);
        tvTheme.setText(red ? "当前：红黑 · 红色龙焰" : "当前：蓝白 · 蓝色龙焰");
        tvTheme.setTextColor(com.acooldog.fuckrun.util.Palette.of(this).primary);
    }

    private void applyTheme(String name) {
        if (name.equals(prefs.theme())) {
            Toast.makeText(this, "已经是这个配色了", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.setTheme(name);
        Toast.makeText(this, com.acooldog.fuckrun.util.Theme.RED.equals(name)
                ? "已切换到红黑配色" : "已切换到蓝白配色", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("skip_intro", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void saveParams() {
        try {
            int sat = Integer.parseInt(etSat.getText().toString().trim());
            float hdop = Float.parseFloat(etHdop.getText().toString().trim());
            float acc = Float.parseFloat(etAcc.getText().toString().trim());
            prefs.setSignal(sat, hdop, acc);
            etSat.setText(String.valueOf(prefs.satellites()));
            etHdop.setText(String.format(Locale.CHINA, "%.1f", prefs.hdop()));
            etAcc.setText(String.format(Locale.CHINA, "%.1f", prefs.accuracy()));
            SimController.refresh(this);
            Toast.makeText(this, "参数已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshStatus() {
        long seen = prefs.hookTime();
        long ago = System.currentTimeMillis() - seen;
        if (seen == 0) {
            status.setText("尚未连接目标进程\n请确认：LSPosed 已启用本模块，作用域勾选「步道乐跑」");
            status.setTextColor(com.acooldog.fuckrun.util.Palette.of(this).textSub);
        } else if (ago < 6000) {
            status.setText("模块在线 · 目标进程已注入\n最近心跳 " + (ago / 1000) + " 秒前");
            status.setTextColor(com.acooldog.fuckrun.util.Palette.of(this).green);
        } else {
            status.setText("模块心跳已停止（" + (ago / 1000) + " 秒前）\n请重新打开步道乐跑");
            status.setTextColor(com.acooldog.fuckrun.util.Palette.of(this).red);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
}
