package com.acooldog.fuckrun;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.acooldog.fuckrun.data.AppPrefs;
import com.acooldog.fuckrun.data.RouteStore;
import com.acooldog.fuckrun.model.Route;
import com.acooldog.fuckrun.sim.SimController;
import com.acooldog.fuckrun.util.Palette;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private TextView count;
    private RouteStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.acooldog.fuckrun.util.Theme.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        com.acooldog.fuckrun.util.Ui.ambient(this);
        com.acooldog.fuckrun.util.Ui.playIntro(this, findViewById(R.id.logo),
                savedInstanceState == null && !getIntent().getBooleanExtra("skip_intro", false));
        store = new RouteStore(this);
        list = findViewById(R.id.route_list);
        status = findViewById(R.id.tv_hook_status);
        count = findViewById(R.id.tv_route_count);

        findViewById(R.id.btn_new_route).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DrawRouteActivity.class));
            }
        });
        findViewById(R.id.btn_cadence).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CadenceActivity.class));
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        findViewById(R.id.card_ads).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAds();
            }
        });
        renderAds();
    }

    private void toggleAds() {
        AppPrefs p = new AppPrefs(this);
        boolean next = !p.adBlock();
        p.setAdBlock(next);
        SimController.refresh(this);
        renderAds();
        Toast.makeText(this, next
                ? "已开启去广告，重启步道乐跑后生效"
                : "已关闭去广告，重启步道乐跑后恢复广告", Toast.LENGTH_SHORT).show();
    }

    private void renderAds() {
        boolean on = new AppPrefs(this).adBlock();
        TextView state = (TextView) findViewById(R.id.tv_ads_state);
        state.setText(on ? "已开启" : "已关闭");
        state.setTextColor(on ? Palette.of(this).green : Palette.of(this).textSub);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderRoutes();
        updateStatus();
        renderAds();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void updateStatus() {
        long seen = new AppPrefs(this).hookTime();
        boolean connected = System.currentTimeMillis() - seen < 6000;
        status.setText(connected ? "模块已连接" : "等待模块");
        status.setTextColor(connected ? Palette.of(this).green : Palette.of(this).textSub);
    }

    private void renderRoutes() {
        List<Route> routes = store.list();
        count.setText(routes.isEmpty() ? "还没有路线" : routes.size() + " 条路线");
        list.removeAllViews();
        for (final Route r : routes) {
            list.addView(card(r));
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
            list.addView(spacer);
        }
    }

    private View card(final Route r) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.card_bg);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView name = new TextView(this);
        name.setText(r.name);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Palette.of(this).text);
        box.addView(name);

        TextView meta = new TextView(this);
        String when = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                .format(new Date(r.updatedAt == 0 ? System.currentTimeMillis() : r.updatedAt));
        meta.setText(String.format(Locale.CHINA, "%d 个点 · %.0f m · %s",
                r.points.size(), r.distance(), when));
        meta.setTextSize(11);
        meta.setTextColor(Palette.of(this).textSub);
        meta.setPadding(0, dp(4), 0, 0);
        box.addView(meta);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(10), 0, 0);

        actions.addView(smallButton("查看", Palette.of(this).primary, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSimulation(r);
            }
        }));
        actions.addView(smallButton("删除", Palette.of(this).red, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(r);
            }
        }));
        box.addView(actions);

        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSimulation(r);
            }
        });
        return box;
    }

    private TextView smallButton(String text, int color, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(color);
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.of(this).chipBg);
        bg.setCornerRadius(dp(999));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void openSimulation(Route r) {
        new AppPrefs(this).setLastRouteId(r.id);
        Intent i = new Intent(this, SimulationActivity.class);
        i.putExtra("route_id", r.id);
        startActivity(i);
    }

    private void confirmDelete(final Route r) {
        new AlertDialog.Builder(this)
                .setTitle("删除路线")
                .setMessage("确定删除「" + r.name + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    store.delete(r.id);
                    renderRoutes();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
