package com.consensus.weather;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app. NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Iterator;

public class WeatherNotifier extends BroadcastReceiver {
    static final String CH_DAILY = "daily_weather";
    static final String CH_SEVERE = "severe_weather";
    static final String PREFS = "cw_notif";
    static final int SEVERE_REQ = 200;
    static final long SEVERE_INTERVAL = 3 * 60 * 60 * 1000L; // check every ~3 h
    static final String[] MODELS = {
        "ecmwf_ifs025","icon_seamless","gfs_seamless","metno_seamless",
        "meteofrance_seamless","jma_seamless","gem_seamless"
    };

    static SharedPreferences prefs(Context c){ return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static boolean isEnabled(Context c){ return prefs(c).getBoolean("enabled", true); }
    static boolean severeOn(Context c){ return prefs(c).getBoolean("severe", true); }
    public static void saveLocation(Context c, double lat, double lon){
        prefs(c).edit().putFloat("lat",(float)lat).putFloat("lon",(float)lon).apply();
    }
    public static void setNotifStrings(Context c, String json){
        prefs(c).edit().putString("strings", json).apply();
    }
    // legacy single on/off toggle still supported
    public static void setEnabled(Context c, boolean on){
        prefs(c).edit().putBoolean("enabled", on).apply();
        scheduleAll(c);
    }
    // full config from the web UI: {enabled, times:["07:00",...], severe}
    public static void setNotifConfig(Context c, String json){
        try{
            JSONObject o = new JSONObject(json);
            boolean en = o.optBoolean("enabled", true);
            boolean sev = o.optBoolean("severe", true);
            JSONArray arr = o.optJSONArray("times");
            StringBuilder csv = new StringBuilder();
            if(arr != null){
                for(int i=0;i<arr.length() && i<3;i++){ if(csv.length()>0) csv.append(","); csv.append(arr.optString(i)); }
            }
            if(csv.length()==0) csv.append("07:00,19:00");
            prefs(c).edit().putBoolean("enabled",en).putBoolean("severe",sev).putString("times",csv.toString()).apply();
            scheduleAll(c);
        }catch(Exception e){}
    }

    static int safeInt(String s, int def){ try{ return Integer.parseInt(s.trim()); }catch(Exception e){ return def; } }

    // ---- scheduling ----
    public static void scheduleAll(Context c){
        cancelAll(c);
        if(!isEnabled(c)) return;
        String[] times = prefs(c).getString("times","07:00,19:00").split(",");
        for(int i=0;i<times.length && i<3;i++){
            String[] hm = times[i].split(":");
            scheduleDaily(c, i, safeInt(hm.length>0?hm[0]:"7",7), safeInt(hm.length>1?hm[1]:"0",0));
        }
        if(severeOn(c)) scheduleSevere(c);
    }
    static void scheduleDaily(Context c, int slot, int hour, int min){
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, hour); t.set(Calendar.MINUTE, min); t.set(Calendar.SECOND, 0);
        if(t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR, 1);
        Intent i = new Intent(c, WeatherNotifier.class).putExtra("type","daily").putExtra("slot",slot).putExtra("hour",hour);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pending(c, 100+slot, i));
    }
    static void scheduleSevere(Context c){
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, WeatherNotifier.class).putExtra("type","severe");
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+15*60*1000L, SEVERE_INTERVAL, pending(c, SEVERE_REQ, i));
    }
    static void cancelAll(Context c){
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        for(int s=0;s<3;s++) am.cancel(pending(c, 100+s, new Intent(c, WeatherNotifier.class).putExtra("type","daily").putExtra("slot",s)));
        am.cancel(pending(c, SEVERE_REQ, new Intent(c, WeatherNotifier.class).putExtra("type","severe")));
    }
    static PendingIntent pending(Context c, int req, Intent i){
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, req, i, flags);
    }

    public static void createChannel(Context c){
        if(Build.VERSION.SDK_INT >= 26){
            NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel d = new NotificationChannel(CH_DAILY, "Daily weather", NotificationManager.IMPORTANCE_DEFAULT);
            d.setDescription("Scheduled weather summaries");
            nm.createNotificationChannel(d);
            NotificationChannel s = new NotificationChannel(CH_SEVERE, "Severe weather", NotificationManager.IMPORTANCE_HIGH);
            s.setDescription("Storm and severe-weather alerts");
            nm.createNotificationChannel(s);
        }
    }

    @Override public void onReceive(final Context ctx, Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){ scheduleAll(ctx); return; }
        final String type = intent.getStringExtra("type");
        final int hour = intent.getIntExtra("hour", 8);
        if(!isEnabled(ctx)) return;
        if("severe".equals(type) && !severeOn(ctx)) return;
        final PendingResult pr = goAsync();
        new Thread(new Runnable(){ public void run(){
            try{ if("severe".equals(type)) doSevere(ctx); else doDaily(ctx, hour); }
            catch(Exception e){} finally{ pr.finish(); }
        }}).start();
    }

    // ---- daily summary (7-model consensus) ----
    static void doDaily(Context c, int hour) throws Exception {
        SharedPreferences p = prefs(c);
        double lat = p.getFloat("lat", Float.NaN), lon = p.getFloat("lon", Float.NaN);
        if(Double.isNaN(lat) || Double.isNaN(lon)) return;
        StringBuilder models = new StringBuilder();
        for(int i=0;i<MODELS.length;i++){ if(i>0) models.append(","); models.append(MODELS[i]); }
        String url = "https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon
            + "&daily=temperature_2m_max,temperature_2m_min,weather_code&models="+models
            + "&past_days=1&forecast_days=2&timezone=auto";
        JSONObject daily = new JSONObject(httpGet(url)).getJSONObject("daily");
        int idx = hour >= 16 ? 2 : 1;   // evening previews tomorrow; otherwise today
        int ref = idx - 1;
        double hi = avgDaily(daily, "temperature_2m_max", idx);
        double lo = avgDaily(daily, "temperature_2m_min", idx);
        double hiRef = avgDaily(daily, "temperature_2m_max", ref);
        int code = firstCode(daily, "weather_code", idx);
        if(Double.isNaN(hi)) return;

        JSONObject S = loadStrings(c);
        String unit = S.optString("unit","C"), lang = S.optString("lang","en");
        String city = reverseGeocode(lat, lon, lang);
        String cond = condText(S, code);
        double diff = hi - hiRef;
        String cmp = diff >= 1.5 ? S.optString("warmer","a bit warmer than yesterday")
                   : diff <= -1.5 ? S.optString("colder","a bit colder than yesterday")
                   : S.optString("same","about the same as yesterday");
        String title = S.optString("title","Weather in {city}").replace("{city}", city);
        String body  = fmtTemp(hi, unit) + "\u00B0 " + cond + "  \u2193" + fmtTemp(lo, unit) + "\u00B0\n" + cap(cmp);
        postNotification(c, CH_DAILY, 1, title, body, false);
    }

    // ---- severe weather watch ----
    static void doSevere(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        double lat = p.getFloat("lat", Float. NaN), lon = p.getFloat("lon", Float.NaN);
        if(Double.isNaN(lat) || Double.isNaN(lon)) return;
        String url = "https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon
            + "&hourly=weather_code&forecast_days=1&timezone=auto";
        JSONObject h = new JSONObject(httpGet(url)).getJSONObject("hourly");
        JSONArray codes = h.getJSONArray("weather_code"), tarr = h.getJSONArray("time");
        Calendar now = Calendar.getInstance();
        String stamp = String.format("%04d-%02d-%02dT%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH)+1,
                now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.HOUR_OF_DAY));
        int start = 0;
        for(int i=0;i<tarr.length();i++){ if(tarr.optString(i).startsWith(stamp)){ start=i; break; } }
        int sev = -1;
        for(int i=start;i<Math.min(start+12, codes.length());i++){ int wc = codes.optInt(i,0); if(isSevere(wc)){ sev = wc; break; } }
        if(sev < 0) return;
        String today = stamp.substring(0,10);
        if(today.equals(p.getString("sevDay",""))) return;   // only once per day
        p.edit().putString("sevDay", today).apply();

        JSONObject S = loadStrings(c);
        String lang = S.optString("lang","en");
        String city = reverseGeocode(lat, lon, lang);
        String cond = condText(S, sev);
        String title = cond + " \u00B7 " + city;
        String body = S.optString("severe","Severe weather expected");
        postNotification(c, CH_SEVERE, 2, title, body, true);
    }
    static boolean isSevere(int wc){
        return wc==95||wc==96||wc==99||wc==65||wc==67||wc==75||wc==77||wc==82||wc==86;
    }

    // ---- helpers ----
    static double avgDaily(JSONObject daily, String base, int idx){
        double sum=0; int n=0;
        for(Iterator<String> it=daily.keys(); it.hasNext();){
            String k = it.next();
            if(k.startsWith(base)){
                JSONArray a = daily.optJSONArray(k);
                if(a!=null && idx>=0 && idx<a.length() && !a.isNull(idx)){ sum += a.optDouble(idx); n++; }
            }
        }
        return n>0 ? sum/n : Double.NaN;
    }
    static int firstCode(JSONObject daily, String base, int idx){
        for(Iterator<String> it=daily.keys(); it.hasNext();){
            String k = it.next();
            if(k.startsWith(base)){
                JSONArray a = daily.optJSONArray(k);
                if(a!=null && idx>=0 && idx<a.length() && !a.isNull(idx)) return a.optInt(idx,0);
            }
        }
        return 0;
    }
    static JSONObject loadStrings(Context c){
        try{ String s = prefs(c).getString("strings", null); if(s!=null) return new JSONObject(s); }catch(Exception e){}
        return new JSONObject();
    }
    static String condText(JSONObject S, int code){
        String key = condKey(code); String def = enCond(key);
        JSONObject cond = S.optJSONObject("cond");
        return cond!=null ? cond.optString(key, def) : def;
    }
    static String condKey(int code){
        if(code==0) return "clear_day";
        if(code==1||code==2) return "pcloudy";
        if(code==3) return "overcast";
        if(code==45||code==48) return "fog";
        if((code>=71&&code<=77)||(code>=85&&code<=86)) return "snow";
        if(code>=95) return "thunder";
        if((code>=51&&code<=67)||(code>=80&&code<=82)) return "rain";
        return "cloudy";
    }
    static String enCond(String k){
        switch(k){
            case "clear_day": return "Sunny";      case "clear_night": return "Clear";
            case "pcloudy": return "Partly cloudy";case "cloudy": return "Cloudy";
            case "overcast": return "Overcast";    case "fog": return "Fog";
            case "rain": return "Rain";            case "snow": return "Snow";
            case "thunder": return "Thunderstorms";
        }
        return "";
    }
    static int fmtTemp(double celsius, String unit){ return (int)Math.round("F".equals(unit)?celsius*9/5+32:celsius); }
    static String cap(String s){ return (s==null||s.isEmpty())?s:Character.toUpperCase(s.charAt(0))+s.substring(1); }
    static String reverseGeocode(double lat, double lon, String lang){
        try{
            JSONObject j = new JSONObject(httpGet("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude="+lat+"&longitude="+lon+"&localityLanguage="+lang));
            String city = j.optString("city",""); if(city.isEmpty()) city = j.optString("locality","");
            if(city.isEmpty()) city = j.optString("principalSubdivision",""); return city;
        }catch(Exception e){ return ""; }
    }
    static String httpGet(String urlStr) throws Exception {
        HttpURLConnection cn = (HttpURLConnection)new URL(urlStr).openConnection();
        cn.setConnectTimeout(10000); cn.setReadTimeout(12000); cn.setRequestProperty("User-Agent","ConsensusWeather");
        BufferedReader br = new BufferedReader(new InputStreamReader(cn.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while((line=br.readLine())!=null) sb.append(line);
        br.close(); cn.disconnect(); return sb.toString();
    }
    static void postNotification(Context c, String channel, int id, String title, String body, boolean high){
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true)
            .setPriority(high?NotificationCompat.PRIORITY_HIGH:NotificationCompat.PRIORITY_DEFAULT);
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if(open!=null){
            int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
            b.setContentIntent(PendingIntent.getActivity(c, 300+id, open, f));
        }
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build());
    }
}
