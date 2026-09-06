package com.consensus.weather;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

public class WeatherNotifier extends BroadcastReceiver {
    static final String CHANNEL_ID = "daily_weather";
    static final String ACTION_NOTIFY = "com.consensus.weather.NOTIFY";
    static final String PREFS = "cw_notif";
    static final int SLOT_MORNING = 1, SLOT_EVENING = 2;
    static final int HOUR_MORNING = 8, HOUR_EVENING = 19;

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action != null && (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action))) {
            scheduleAll(ctx);
            return;
        }
        final int slot = intent != null ? intent.getIntExtra("slot", SLOT_MORNING) : SLOT_MORNING;
        scheduleSlot(ctx, slot); // re-arm the same slot for tomorrow
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try { doWork(ctx, slot); } catch (Throwable ignored) {} finally { pr.finish(); }
            }
        }).start();
    }

    // ---------- scheduling ----------
    static void scheduleAll(Context ctx) {
        scheduleSlot(ctx, SLOT_MORNING);
        scheduleSlot(ctx, SLOT_EVENING);
    }

    static void scheduleSlot(Context ctx, int slot) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int hour = slot == SLOT_EVENING ? HOUR_EVENING : HOUR_MORNING;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        PendingIntent pi = slotPending(ctx, slot);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            }
        } catch (Exception ignored) {}
    }

    private static PendingIntent slotPending(Context ctx, int slot) {
        Intent i = new Intent(ctx, WeatherNotifier.class);
        i.setAction(ACTION_NOTIFY);
        i.putExtra("slot", slot);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build. VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, slot, i, flags);
    }

    static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Daily weather",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Morning and evening weather summaries");
            nm.createNotificationChannel(ch);
        }
    }

    static void saveLocation(Context ctx, double lat, double lon) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString("lat", String.valueOf(lat)).putString("lon", String.valueOf(lon)).apply();
    }

    // ---------- work ----------
    private void doWork(Context ctx, int slot) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String slat = p.getString("lat", null), slon = p.getString("lon", null);
        if (slat == null || slon == null) return; // no location captured yet
        double lat = Double.parseDouble(slat), lon = Double.parseDouble(slon);

        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=auto&past_days=1&forecast_days=2";
        JSONObject j = new JSONObject(httpGet(url, 6000));
        JSONObject cur = j.optJSONObject("current");
        JSONObject daily = j.optJSONObject("daily");
        if (daily == null) return;
        JSONArray hi = daily.getJSONArray("temperature_2m_max");
        JSONArray lo = daily.getJSONArray("temperature_2m_min");
        JSONArray code = daily.getJSONArray("weather_code");
        JSONArray pop = daily.optJSONArray("precipitation_probability_max");
        // indices: 0 = yesterday, 1 = today, 2 = tomorrow
        int idx = slot == SLOT_EVENING ? 2 : 1;
        if (idx >= hi.length()) idx = hi.length() - 1;
        int prevIdx = Math.max(0, idx - 1);

        int dHi = (int) Math.round(hi.getDouble(idx));
        int dLo = (int) Math.round(lo.getDouble(idx));
        int pHi = (int) Math.round(hi.getDouble(prevIdx));
        int wc = code.getInt(idx);
        int pr = (pop != null && idx < pop.length() && !pop.isNull(idx)) ? pop.getInt(idx) : -1;
        int nowT = (cur != null && cur.has("temperature_2m")) ? (int) Math.round(cur.getDouble("temperature_2m")) : dHi;
        String cond = condWord(wc);
        String place = reverseGeocode(lat, lon);

        String when = slot == SLOT_EVENING ? "tomorrow" : "today";
        String ref = slot == SLOT_EVENING ? "today" : "yesterday";
        int diff = dHi - pHi;
        String cmp;
        if (diff >= 4) cmp = " Much warmer than " + ref + ".";
        else if (diff >= 1) cmp = " A bit warmer than " + ref + ".";
        else if (diff <= -4) cmp = " Much colder than " + ref + ".";
        else if (diff <= -1) cmp = " A bit colder than " + ref + ".";
        else cmp = " About the same as " + ref + ".";

        String rain = "";
        if ((wc >= 71 && wc <= 77) || wc == 85 || wc == 86) rain = " Snow likely.";
        else if (pr >= 50 || (wc >= 51 && wc <= 67) || (wc >= 80 && wc <= 82) || wc >= 95) rain = " Rain likely.";

        String title = headline(dHi) + (slot == SLOT_EVENING ? " tomorrow" : " today");
        String body = nowT + "° " + cond + " in " + place + " " + when
                + ", high " + dHi + "°, low " + dLo + "°." + cmp + rain + " Tap to view.";
        postNotification(ctx, slot, title, body);
    }

    private static String headline(int hi) {
        if (hi < 3) return "Cold";
        if (hi < 12) return "Cool";
        if (hi < 20) return "Mild";
        if (hi < 27) return "Warm";
        return "Hot";
    }

    private static String condWord(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mainly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Overcast";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Rain showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorms";
        return "Mixed";
    }

    private static String reverseGeocode(double lat, double lon) {
        try {
            String u = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=" + lat
                    + "&longitude=" + lon + "&localityLanguage=en";
            JSONObject j = new JSONObject(httpGet(u, 4000));
            String city = j.optString("city", "");
            if (city.isEmpty()) city = j.optString("locality", "");
            if (city.isEmpty()) city = j.optString("principalSubdivision", "");
            if (!city.isEmpty()) return city;
        } catch (Exception ignored) {}
        return "your area";
    }

    private static String httpGet(String urlStr, int timeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("Accept", "application/json");
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        conn.disconnect();
        return sb.toString();
    }

    private static void postNotification(Context ctx, int slot, String title, String body) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentPI = PendingIntent.getActivity(ctx, 100 + slot, open, piFlags);

        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(ctx, CHANNEL_ID)
                : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.ic_launcher)
         .setContentTitle(title)
         .setContentText(body)
         .setStyle(new Notification.BigTextStyle().bigText(body))
         .setAutoCancel(true)
         .setContentIntent(contentPI)
         .setWhen(System.currentTimeMillis());
        nm.notify(slot, b.build());
    }
}
