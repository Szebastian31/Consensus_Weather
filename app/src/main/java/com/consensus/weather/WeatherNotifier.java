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

    // ---- daily summary (consensus over the next 14 h) ----
    static void doDaily(Context c, int hour) throws Exception {
        SharedPreferences p = prefs(c);
        double lat = p.getFloat("lat", Float.NaN), lon = p.getFloat("lon", Float.NaN);
        if(Double.isNaN(lat) || Double.isNaN(lon)) return;

        StringBuilder models = new StringBuilder();
        for(int i=0;i<MODELS.length;i++){ if(i>0) models.append(","); models.append(MODELS[i]); }

        // hourly for the 14 h window + daily max (past_days=1) for the yesterday comparison
        String url = "https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon
            + "&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m,wind_direction_10m"
            + "&daily=temperature_2m_max&models="+models
            + "&past_days=1&forecast_days=2&timezone=auto";
        JSONObject root = new JSONObject(httpGet(url));
        JSONObject H = root.getJSONObject("hourly");
        JSONArray tarr = H.getJSONArray("time");

        // locate the current hour in the timeline
        Calendar now = Calendar.getInstance();
        String stamp = String.format("%04d-%02d-%02dT%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH)+1,
                now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.HOUR_OF_DAY));
        int start = -1;
        for(int i=0;i<tarr.length();i++){ if(tarr.optString(i).startsWith(stamp)){ start=i; break; } }
        if(start < 0) start = 0;
        int end = Math.min(start+14, tarr.length());
        if(end <= start) return;

        // consensus across the next 14 h
        double hi = Double.NEGATIVE_INFINITY, lo = Double.POSITIVE_INFINITY;
        double popMax = 0; boolean havePop = false;
        double windMax = 0; int windDirAtMax = 0;
        int rainFromHr = -1;
        int headCode = 0; // most significant WMO code seen (higher code ≈ more notable)
        for(int i=start;i<end;i++){
            double t = avgDaily(H, "temperature_2m", i);
            if(!Double.isNaN(t)){ if(t>hi) hi=t; if(t<lo) lo=t; }
            double pp = avgDaily(H, "precipitation_probability", i);
            if(!Double.isNaN(pp)){ havePop=true; if(pp>popMax) popMax=pp; if(rainFromHr<0 && pp>=50) rainFromHr=i-start; }
            double w = avgDaily(H, "wind_speed_10m", i);
            if(!Double.isNaN(w) && w>windMax){ windMax=w; windDirAtMax=firstCode(H,"wind_direction_10m",i); }
            int wc = worstCodeAt(H, i);
            if(wc>headCode) headCode=wc;
        }
        if(hi == Double.NEGATIVE_INFINITY) return;

        JSONObject D = root.optJSONObject("daily");
        double hiRef = D!=null ? avgDaily(D, "temperature_2m_max", 0) : Double.NaN; // idx 0 = yesterday

        JSONObject S = loadStrings(c);
        String unit = S.optString("unit","C");
        double windFactor = S.optDouble("windFactor", 1);
        int    windDec    = S.optInt("windDec", 0);
        String windUnit   = S.optString("windUnit","km/h");
        String cond = condText(S, headCode);

        // title = report name (respects single vs multi schedule, as before)
        int count = prefs(c).getString("times","07:00,19:00").split(",").length;
        boolean single   = count <= 1;
        boolean tomorrow = single ? (hour >= 18) : (hour >= 16);
        String title;
        if(single)         title = tomorrow ? S.optString("repN","Tomorrow's Report") : S.optString("repD","Today's Report");
        else if(hour < 12) title = S.optString("repM","Morning Report");
        else if(hour < 17) title = S.optString("repA","Afternoon Report");
        else               title = S.optString("repE","Evening Report");

        // concise body: headline · rain timing · wind · vs yesterday
        java.util.ArrayList<String> parts = new java.util.ArrayList<String>();
        parts.add(cap(cond) + " " + fmtTemp(hi, unit) + "\u00B0/" + fmtTemp(lo, unit) + "\u00B0");

        if(rainFromHr == 0)      parts.add(S.optString("rainNow","rain now"));
        else if(rainFromHr > 0)  parts.add(S.optString("rainFrom","rain from ~{h}h").replace("{h}", String.valueOf(rainFromHr)));
        else if(havePop && popMax >= 30)
            parts.add(S.optString("popShort","{p}% chance of rain").replace("{p}", String.valueOf((int)Math.round(popMax))));

        if(windMax >= 25){
            String wv = windDec > 0
                ? String.format(java.util.Locale.US, "%."+windDec+"f", windMax*windFactor)
                : String.valueOf(Math.round(windMax*windFactor));
            parts.add(S.optString("windShort","wind {d} {s} {u}")
                .replace("{d}", compass(windDirAtMax)).replace("{s}", wv).replace("{u}", windUnit));
        }

        if(!Double.isNaN(hiRef)){
            double diff = hi - hiRef;
            if(diff >= 1.5)       parts.add(S.optString("warmer","a bit warmer than yesterday"));
            else if(diff <= -1.5) parts.add(S.optString("colder","a bit colder than yesterday"));
        }

        StringBuilder body = new StringBuilder();
        for(int i=0;i<parts.size();i++){ if(i>0) body.append(" \u00B7 "); body.append(parts.get(i)); }
        postNotification(c, CH_DAILY, 1, title, body.toString(), false);
    }

    // most significant WMO code across all models at one hour
    static int worstCodeAt(JSONObject h, int idx){
        int worst = 0;
        for(Iterator<String> it=h.keys(); it.hasNext();){
            String k = it.next();
            if(k.startsWith("weather_code")){
                JSONArray a = h.optJSONArray(k);
                if(a!=null && idx>=0 && idx<a.length() && !a.isNull(idx)){
                    int v = a.optInt(idx,0);
                    if(v>worst) worst=v;
                }
            }
        }
        return worst;
    }

    static String compass(int deg){
        String[] pts = {"N","NE","E","SE","S","SW","W","NW"};
        return pts[(int)Math.round((((deg%360)+360)%360)/45.0)%8];
    }

    // ---- severe weather watch ----
    static void doSevere(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        double lat = p.getFloat("lat", Float.NaN), lon = p.getFloat("lon", Float.NaN);
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
