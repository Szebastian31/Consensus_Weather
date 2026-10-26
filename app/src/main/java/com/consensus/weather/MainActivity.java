package com.consensus.weather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String NIGHT = "#151327";
    private WebView web;
    private SwipeRefreshLayout swipe;
    private boolean firstResume = true;
    // status and navigation bar heights in CSS pixels, handed to the page as --sat / --sab
    private int insetTop, insetBottom;
    private static final int REQ_LOC = 42;
    private static final int REQ_NOTIF = 43;

    @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If the app crashed last time, show what happened instead of crashing again, so it can be sent on.
        // Saved cities aren't touched: they're in the WebView's storage, not here.
        final File crash = new File(getFilesDir(), "crash.txt");
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((th, e) -> {
            try (FileOutputStream o = new FileOutputStream(crash)) {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw, true));
                o.write((reportHead() + sw).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
            if (prev != null) prev.uncaughtException(th, e);
        });
        if (crash.exists()) { showCrash(crash); return; }

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(NIGHT));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            // links to other sites (the iOS page, Open-Meteo…) open in the browser, not inside the app
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pushInsets();
            }

            // If the page's renderer dies, Android closes the whole app unless it's handled here, and no Java
            // error is raised, so the crash screen would never see it. Write it down and show it instead.
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                try (FileOutputStream o = new FileOutputStream(crash)) {
                    String why = detail.didCrash() ? "The WebView's page renderer crashed." : "The system stopped the WebView's page renderer (usually to free memory).";
                    String wv = "";
                    try { android.content.pm.PackageInfo p = Build.VERSION.SDK_INT >= 26 ? WebView.getCurrentWebViewPackage() : null; if (p != null) wv = "\nWebView: " + p.packageName + " " + p.versionName; } catch (Exception ignored) {}
                    o.write((reportHead() + why + wv + "\n").getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
                if (web == view && web != null) {
                    try { ((ViewGroup) web.getParent()).removeView(web); } catch (Exception ignored) {}
                    web.destroy();
                    web = null;
                }
                recreate();
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");

        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(Color.parseColor("#A78BFF"), Color.parseColor("#FF9E5A"));
        swipe.setProgressBackgroundColorSchemeColor(Color.parseColor("#241F3D"));
        swipe.addView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipe.setOnChildScrollUpCallback((parent, child) -> web != null && web.getScrollY() > 0);
        swipe.setOnRefreshListener(() -> {
            if (web == null) return;
            web.evaluateJavascript("window.refreshWeather && window.refreshWeather();", null);
            web.postDelayed(() -> { if (swipe.isRefreshing()) swipe.setRefreshing(false); }, 8000);
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor(NIGHT));
        root.addView(swipe, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        // only after setContentView: ColorOS crashes if the bars are touched before the window has its decor view
        try { edgeToEdge(); } catch (Exception ignored) {}

        // the page draws under the bars and pads itself; only the keyboard shrinks the WebView
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            try {
                int top, bottom, ime;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets bars = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = bars.top; bottom = bars.bottom;
                    ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
                } else {
                    top = ins.getSystemWindowInsetTop();
                    bottom = ins.getStableInsetBottom();
                    ime = Math.max(0, ins.getSystemWindowInsetBottom() - bottom);
                }
                v.setPadding(0, 0, 0, ime);
                float d = getResources().getDisplayMetrics().density;
                insetTop = Math.round(top / d);
                insetBottom = ime > 0 ? 0 : Math.round(bottom / d);
                pushInsets();
            } catch (Exception ignored) {}
            return ins;
        });

        // Android 16 no longer calls onBackPressed for apps targeting it; back arrives here instead
        if (Build.VERSION.SDK_INT >= 33) try {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        } catch (Exception ignored) {}

        // daily weather notifications (morning + evening)
        WeatherNotifier.createChannel(this);
        WeatherNotifier.scheduleAll(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @SuppressWarnings("deprecation")
    private void edgeToEdge() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28)
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        applyBars();
    }

    /** Light status and navigation bar icons: the sky is always dark enough. */
    @SuppressWarnings("deprecation")
    private void applyBars() {
        try {
            View decor = getWindow().getDecorView();
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = decor.getWindowInsetsController();
                if (c == null) return;
                int both = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(0, both);
            } else {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        } catch (Exception ignored) {}
    }

    private void pushInsets() {
        if (web == null) return;
        web.evaluateJavascript("(function(s){s.setProperty('--sat','" + insetTop + "px');s.setProperty('--sab','" + insetBottom + "px')})(document.documentElement.style)", null);
    }

    private String reportHead() {
        return "Consensus Weather " + appVersion() + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n";
    }

    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    /** What went wrong last time, with Share (to send it on) and Try again. Plain Android views only, nothing that could fail the same way. */
    private void showCrash(File crash) {
        String text;
        try { text = new String(java.nio.file.Files.readAllBytes(crash.toPath()), StandardCharsets.UTF_8); }
        catch (Exception e) { text = "(the error report couldn't be read)"; }
        final String report = text;
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * d);

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.parseColor(NIGHT));
        box.setPadding(pad, Math.round(56 * d), pad, Math.round(32 * d));

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("Consensus Weather stopped last time");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        box.addView(title);

        android.widget.TextView lead = new android.widget.TextView(this);
        lead.setText("Your saved cities are safe. Tap Share and send this to whoever is fixing the app, then Try again.");
        lead.setTextColor(Color.parseColor("#C9BEE8"));
        lead.setTextSize(15);
        lead.setPadding(0, Math.round(8 * d), 0, Math.round(14 * d));
        box.addView(lead);

        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        android.widget.Button share = new android.widget.Button(this);
        share.setText("Share");
        share.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report);
            try { startActivity(Intent.createChooser(i, "Share")); } catch (Exception ignored) {}
        });
        android.widget.Button again = new android.widget.Button(this);
        again.setText("Try again");
        again.setOnClickListener(v -> { crash.delete(); recreate(); });
        row.addView(share);
        row.addView(again);
        box.addView(row);

        android.widget.TextView log = new android.widget.TextView(this);
        log.setText(report);
        log.setTextColor(Color.parseColor("#E6E0F5"));
        log.setTextSize(11);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        log.setPadding(0, Math.round(14 * d), 0, 0);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(log);
        box.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(box);
    }

    private class Bridge {
        @JavascriptInterface public void onRefreshed() {
            runOnUiThread(() -> { if (swipe != null) swipe.setRefreshing(false); });
        }
        @JavascriptInterface public void setRefreshEnabled(final boolean en) {
            runOnUiThread(() -> { if (swipe != null) swipe.setEnabled(en); });
        }
        @JavascriptInterface public void requestLocation() {
            runOnUiThread(() -> ensureLocationPermission());
        }
        @JavascriptInterface public void setNotifications(final boolean on) {
            runOnUiThread(() -> WeatherNotifier.setEnabled(MainActivity.this, on));
        }
        @JavascriptInterface public void setNotifStrings(final String json) {
            runOnUiThread(() -> WeatherNotifier.setNotifStrings(MainActivity.this, json));
        }
        @JavascriptInterface public void setNotifConfig(final String json) {
            runOnUiThread(() -> WeatherNotifier.setNotifConfig(MainActivity.this, json));
        }
    }

    private void ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        if (rc == REQ_LOC) {
            boolean granted = false;
            for (int g : r) if (g == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) fetchLocation(); else jsError("Location permission denied");
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocation() {
        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { jsError("Location unavailable"); return; }
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location loc = lm.getLastKnownLocation(p);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) best = loc;
            }
        } catch (SecurityException e) { jsError("Permission needed"); return; }
        if (best != null) { sendLocation(best); return; }
        String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
                : (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null);
        if (provider == null) { jsError("Turn on location services"); return; }
        try {
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    try { lm.removeUpdates(this); } catch (Exception ig) {}
                    sendLocation(loc);
                }
                @Override public void onStatusChanged(String p, int st, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            lm.requestLocationUpdates(provider, 0L, 0f, listener, getMainLooper());
            if (web != null) web.postDelayed(() -> { try { lm.removeUpdates(listener); } catch (Exception ig) {} }, 15000);
        } catch (SecurityException e) { jsError("Permission needed"); }
    }

    private void sendLocation(Location loc) {
        WeatherNotifier.saveLocation(this, loc.getLatitude(), loc.getLongitude());
        final String js = "window.onDeviceLocation && window.onDeviceLocation("
                + loc.getLatitude() + "," + loc.getLongitude() + ");";
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }
    private void jsError(String msg) {
        final String js = "window.onLocationError && window.onLocationError('" + msg.replace("'", " ") + "');";
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web == null) return;
        web.onResume();
        if (firstResume) { firstResume = false; return; }
        web.evaluateJavascript("window.refreshWeather && window.refreshWeather();", null);
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    /** Back (Android 15 and older) */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goBack();
    }

    /** Lets the page close a sheet or the menu first; otherwise the app goes to the background. */
    private void goBack() {
        if (web == null) { finish(); return; }
        web.evaluateJavascript("window.cwBack?cwBack():false", v -> {
            if ("true".equals(v)) return;
            if (web != null && web.canGoBack()) web.goBack();
            else moveTaskToBack(true);
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
