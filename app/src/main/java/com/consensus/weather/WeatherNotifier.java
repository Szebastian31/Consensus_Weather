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
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Iterator;

public class WeatherNotifier extends BroadcastReceiver {
    static final String CHANNEL_ID = "daily_weather";
    static final String PREFS = "cw_notif";
    static final int SLOT_MORNING = 1, SLOT_EVENING = 2;
    static final int HOUR_MORNING = 8, HOUR_EVENING = 19;
    static final String[] MODELS = {
        "ecmwf_ifs025","icon_seamless","gfs_seamless","metno_seamless",
        "meteofrance_seamless","jma_seamless","gem_seamless"
    };

    static SharedPreferences prefs(Context c){ return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static boolean isEnabled(Context c){ return prefs(c).getBoolean("enabled", true); }
    public static void setEnabled(Context c, boolean on){
        prefs(c).edit().putBoolean("enabled", on).apply();
        if(on) scheduleAll(c); else cancelAll(c);
    }
    public static void saveLocation(Context c, double lat, double lon){
        prefs(c).edit().putFloat("lat",(float)lat).putFloat("lon",(float)lon).apply();
    }
    public static void setNotifStrings(Context c, String json){
        prefs(c).edit().putString("strings", json).apply();
    }

    public static void scheduleAll(Context c){
        if(!isEnabled(c)) return;
        scheduleSlot(c, SLOT_MORNING, HOUR_MORNING);
        scheduleSlot(c, SLOT_EVENING, HOUR_EVENING);
    }
    static void scheduleSlot(Context c, int slot, int hour){
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, hour); t.set(Calendar.MINUTE,0); t.set(Calendar.SECOND,0);
        if(t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_YEAR,1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), slotPending(c, slot));
    }
    static void cancelAll(Context c){
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(slotPending(c, SLOT_MORNING));
        am.cancel(slotPending(c, SLOT_EVENING));
    }
    static PendingIntent slotPending(Context c, int slot){
        Intent i = new Intent(c, WeatherNotifier.class).putExtra("slot", slot);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION. SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 100+slot, i, flags);
    }

    public static void createChannel(Context c){
        if(Build.VERSION.SDK_INT >= 26){
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Daily weather", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Morning and evening weather summary");
            ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override public void onReceive(final Context ctx, Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){ scheduleAll(ctx); return; }
        final int slot = intent.getIntExtra("slot", SLOT_MORNING);
        scheduleSlot(ctx, slot, slot==SLOT_MORNING? HOUR_MORNING:HOUR_EVENING); // reschedule next day
        if(!isEnabled(ctx)) return;
        final PendingResult pr = goAsync();
        new Thread(new Runnable(){ public void run(){
            try{ doWork(ctx, slot); } catch(Exception e){} finally{ pr.finish(); }
        }}).start();
    }

    static void doWork(Context c, int slot) throws Exception {
        SharedPreferences p = prefs(c);
        double lat = p.getFloat("lat", Float.NaN);
        double lon = p.getFloat("lon", Float.NaN);
        if(Double.isNaN(lat) || Double.isNaN(lon)) return;

        StringBuilder models = new StringBuilder();
        for(int i=0;i<MODELS.length;i++){ if(i>0) models.append(","); models.append(MODELS[i]); }
        String url = "https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon
            + "&daily=temperature_2m_max,temperature_2m_min,weather_code"
            + "&models="+models + "&past_days=1&forecast_days=2&timezone=auto";
        JSONObject daily = new JSONObject(httpGet(url)).getJSONObject("daily");

        int idx = (slot==SLOT_EVENING) ? 2 : 1;  // evening previews tomorrow; morning = today
        int ref = idx - 1;                        // compare to previous day
        double hi    = avgDaily(daily, "temperature_2m_max", idx);
        double lo    = avgDaily(daily, "temperature_2m_min", idx);
        double hiRef = avgDaily(daily, "temperature_2m_max", ref);
        int code     = firstCode(daily, "weather_code", idx);
        if(Double.isNaN(hi)) return;

        JSONObject S = loadStrings(c);
        String unit = S.optString("unit","C");
        String lang = S.optString("lang","en");
        String city = reverseGeocode(lat, lon, lang);

        String cond = condText(S, code);
        double diff = hi - hiRef;
        String cmp = diff >= 1.5 ? S.optString("warmer","a bit warmer than yesterday")
                   : diff <= -1.5 ? S.optString("colder","a bit colder than yesterday")
                   : S.optString("same","about the same as yesterday");

        String title = S.optString("title","Weather in {city}").replace("{city}", city);
        String body  = fmtTemp(hi, unit) + "\u00B0 " + cond + "  \u2193" + fmtTemp(lo, unit) + "\u00B0\n" + cap(cmp);
        postNotification(c, slot, title, body);
    }

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
        String key = condKey(code);
        JSONObject cond = S.optJSONObject("cond");
        String def = enCond(key);
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
            case "clear_day": return "Sunny";
            case "clear_night": return "Clear";
            case "pcloudy": return "Partly cloudy";
            case "cloudy": return "Cloudy";
            case "overcast": return "Overcast";
            case "fog": return "Fog";
            case "rain": return "Rain";
            case "snow": return "Snow";
            case "thunder": return "Thunderstorms";
        }
        return "";
    }
    static int fmtTemp(double celsius, String unit){
        double v = "F".equals(unit) ? celsius*9/5+32 : celsius;
        return (int)Math.round(v);
    }
    static String cap(String s){ return (s==null||s.isEmpty())?s:Character.toUpperCase(s.charAt(0))+s.substring(1); }

    static String reverseGeocode(double lat, double lon, String lang){
        try{
            String u = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude="+lat+"&longitude="+lon+"&localityLanguage="+lang;
            JSONObject j = new JSONObject(httpGet(u));
            String city = j.optString("city","");
            if(city.isEmpty()) city = j.optString("locality","");
            if(city.isEmpty()) city = j.optString("principalSubdivision","");
            return city;
        }catch(Exception e){ return ""; }
    }
    static String httpGet(String urlStr) throws Exception {
        HttpURLConnection cn = (HttpURLConnection)new URL(urlStr).openConnection();
        cn.setConnectTimeout(10000); cn.setReadTimeout(12000);
        cn.setRequestProperty("User-Agent","ConsensusWeather");
        BufferedReader br = new BufferedReader(new InputStreamReader(cn.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while((line=br.readLine())!=null) sb.append(line);
        br.close(); cn.disconnect();
        return sb.toString();
    }
    static void postNotification(Context c, int slot, String title, String body){
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if(open!=null){
            int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
            b.setContentIntent(PendingIntent.getActivity(c, 200+slot, open, f));
        }
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(slot, b.build());
    }
}
