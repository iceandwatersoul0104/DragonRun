package com.acooldog.fuckrun;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.acooldog.fuckrun.data.RouteStore;
import com.acooldog.fuckrun.model.Route;
import com.acooldog.fuckrun.model.RoutePoint;
import com.acooldog.fuckrun.util.Geo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 在地图上点选绘制路线。地图显示 GCJ-02，存储为 WGS84。 */
public class DrawRouteActivity extends Activity {
    private WebView web;
    private TextView stats;
    private final List<RoutePoint> points = new ArrayList<>();
    private boolean pageReady;
    private RouteStore store;
    private double distance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.acooldog.fuckrun.util.Theme.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draw);
        com.acooldog.fuckrun.util.Ui.ambient(this);
        store = new RouteStore(this);
        stats = findViewById(R.id.tv_stats);
        web = findViewById(R.id.map);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Bridge");
        web.loadUrl("file:///android_asset/map.html");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                centerToKnown();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_undo).setOnClickListener(v -> undo());
        findViewById(R.id.btn_clear).setOnClickListener(v -> clearAll());
        findViewById(R.id.btn_center).setOnClickListener(v -> centerToKnown());
        findViewById(R.id.btn_save).setOnClickListener(v -> askName());
        updateStats();
    }

    private void centerToKnown() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
            return;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) {
                double[] g = Geo.wgs84ToGcj02(best.getLatitude(), best.getLongitude());
                js("setCamera(" + g[0] + "," + g[1] + ",17)");
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 7 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) centerToKnown();
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    private void undo() {
        if (points.isEmpty()) return;
        points.remove(points.size() - 1);
        js("undoPoint()");
        updateStats();
    }

    private void clearAll() {
        points.clear();
        js("clearPoints()");
        updateStats();
    }

    private void updateStats() {
        double d = 0;
        for (int i = 1; i < points.size(); i++) {
            RoutePoint a = points.get(i - 1), b = points.get(i);
            d += Geo.haversine(a.lat, a.lng, b.lat, b.lng);
        }
        distance = d;
        stats.setText(String.format(Locale.CHINA, "%d 个点 · %.0f m", points.size(), d));
    }

    private void askName() {
        if (points.size() < 2) {
            Toast.makeText(this, "至少需要 2 个点", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        String def = "路线 " + new SimpleDateFormat("MMdd_HHmm", Locale.CHINA).format(new Date());
        et.setText(def);
        et.setSelection(def.length());
        new AlertDialog.Builder(this)
                .setTitle("保存路线")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> save(et.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void save(String name) {
        if (name.isEmpty()) name = "未命名路线";
        Route r = new Route();
        r.id = "r" + System.currentTimeMillis();
        r.name = name;
        r.points.addAll(points);
        if (store.save(r)) {
            Toast.makeText(this, "已保存：" + name, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (!points.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("放弃绘制？")
                    .setMessage("当前路线尚未保存")
                    .setPositiveButton("放弃", (d, w) -> finish())
                    .setNegativeButton("继续", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void onMapClick(final double gcjLat, final double gcjLng) {
            runOnUiThread(() -> {
                double[] w = Geo.gcj02ToWgs84(gcjLat, gcjLng);
                points.add(new RoutePoint(w[0], w[1], 0));
                js("addPoint(" + gcjLat + "," + gcjLng + ")");
                updateStats();
            });
        }

        @JavascriptInterface
        public void onPoints(int count, int meters) {
            runOnUiThread(() -> {
                if (points.size() != count) return;
                stats.setText(String.format(Locale.CHINA, "%d 个点 · %d m", count, meters));
            });
        }
    }
}
