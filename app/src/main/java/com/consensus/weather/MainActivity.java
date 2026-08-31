package com.consensus.weather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends Activity {

    private WebView web;
    private SwipeRefreshLayout swipe;
    private boolean firstResume = true;
    private static final int REQ_LOC = 42;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#151327"));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);      // app logic
        s.setDomStorageEnabled(true);      // localStorage: saved locations + slots
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient());
        // load the bundled app; live data is fetched from Open-Meteo over HTTPS
        web.loadUrl("file:///android_asset/index.html");

        // pull-to-refresh around the WebView
        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(Color.parseColor("#A78BFF"), Color.parseColor("#FF9E5A"));
        swipe.setProgressBackgroundColorSchemeColor(Color.parseColor("#241F3D"));
        swipe.addView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        swipe.setOnChildScrollUpCallback((parent, child) -> web.getScrollY() > 0);
        swipe.setOnRefreshListener(() -> {
            web.evaluateJavascript("window.refreshWeather && window.refreshWeather();", null);
            web.postDelayed(() -> {
                if (swipe.isRefreshing()) swipe.setRefreshing(false);
            }, 8000);
        });

        setContentView(swipe);
    }

    /** Bridge exposed to the page as window.AndroidBridge */
    private class Bridge {
        @JavascriptInterface
        public void onRefreshed() {
            runOnUiThread(() -> { if (swipe != null) swipe.setRefreshing(false); });
        }
        @JavascriptInterface
        public void requestLocation() {
            runOnUiThread(() -> ensureLocationPermission());
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOC) {
            boolean granted = false;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) fetchLocation();
            else jsError("Location permission denied");
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocation() {
        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { jsError("Location unavailable"); return; }

        // 1) use the best cached fix if we have one (instant)
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location loc = lm.getLastKnownLocation(p);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) best = loc;
            }
        } catch (SecurityException e) { jsError("Permission needed"); return; }
        if (best != null) { sendLocation(best); return; }

        // 2) otherwise request a single fresh update
        String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
                : (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null);
        if (provider == null) { jsError("Turn on location services"); return; }
        try {
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    try { lm.removeUpdates(this); } catch (Exception ignored) {}
                    sendLocation(loc);
                }
                @Override public void onStatusChanged(String p, int st, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            lm.requestLocationUpdates(provider, 0L, 0f, listener, getMainLooper());
            web.postDelayed(() -> {
                try { lm.removeUpdates(listener); } catch (Exception ignored) {}
            }, 15000);
        } catch (SecurityException e) {
            jsError("Permission needed");
        }
    }

    private void sendLocation(Location loc) {
        final String js = "window.onDeviceLocation && window.onDeviceLocation("
                + loc.getLatitude() + "," + loc.getLongitude() + ");";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    private void jsError(String msg) {
        final String js = "window.onLocationError && window.onLocationError('" + msg.replace("'", " ") + "');";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // skip the first resume (onCreate already loads + fetches),
        // then refresh whenever the app returns to the foreground
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (web != null) {
            web.evaluateJavascript("window.refreshWeather && window.refreshWeather();", null);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
