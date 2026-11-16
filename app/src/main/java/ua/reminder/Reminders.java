package ua.reminder;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import org.json.*;
import java.util.*;

final class Reminders {
    static final String CHANNEL = "alarm_v1";
    static class Event {
        int id; String title; long time; boolean ringing;
        Event(int id, String title, long time, boolean ringing) { this.id=id; this.title=title; this.time=time; this.ringing=ringing; }
    }
    static synchronized List<Event> all(Context c) {
        List<Event> events = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences("events",0).getString("items","[]"));
            for(int i=0;i<a.length();i++) { JSONObject o=a.getJSONObject(i); events.add(new Event(o.getInt("id"),o.getString("title"),o.getLong("time"),o.optBoolean("ringing"))); }
        } catch(JSONException e) { throw new IllegalStateException("Cannot read saved events",e); }
        events.sort(Comparator.comparingLong(e -> e.time)); return events;
    }
    static Event find(Context c,int id) { for(Event e:all(c)) if(e.id==id) return e; return null; }
    static synchronized void save(Context c,Event event) {
        List<Event> events=all(c); events.removeIf(e -> e.id==event.id); events.add(event); write(c,events);
    }
    static void write(Context c,List<Event> events) {
        JSONArray a=new JSONArray();
        try { for(Event e:events) a.put(new JSONObject().put("id",e.id).put("title",e.title).put("time",e.time).put("ringing",e.ringing)); }
        catch(JSONException ex) { throw new IllegalStateException(ex); }
        if(!c.getSharedPreferences("events",0).edit().putString("items",a.toString()).commit()) throw new IllegalStateException("Cannot save events");
    }
    static int nextId(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences("events",0);
        int id=p.getInt("next",1); p.edit().putInt("next",id+1).commit(); return id;
    }
    static NotificationManager nm(Context c) { return c.getSystemService(NotificationManager.class); }
    static void channel(Context c) {
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Будильники подій",NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        ch.enableVibration(true); ch.setVibrationPattern(new long[]{0,700,400,700}); ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); nm(c).createNotificationChannel(ch);
    }
    static boolean ready(Context c) {
        channel(c);
        NotificationChannel ch=nm(c).getNotificationChannel(CHANNEL);
        return nm(c).areNotificationsEnabled() && ch.getImportance()>=NotificationManager.IMPORTANCE_HIGH &&
            (Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms()) &&
            (Build.VERSION.SDK_INT<34 || nm(c).canUseFullScreenIntent());
    }
    static PendingIntent alarmIntent(Context c,int id) { return PendingIntent.getBroadcast(c,id,new Intent(c,AlarmReceiver.class).setAction("ring").putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    static void schedule(Context c,Event e) {
        Intent view=new Intent(c,MainActivity.class);
        c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(Math.max(e.time,System.currentTimeMillis()+1000),PendingIntent.getActivity(c,e.id,view,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)),alarmIntent(c,e.id));
    }
    static PendingIntent action(Context c,int id,String action) { return PendingIntent.getBroadcast(c,id,new Intent(c,AlarmReceiver.class).setAction(action).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    static void ring(Context c,Event e) {
        channel(c); e.ringing=true; save(c,e);
        PendingIntent screen=PendingIntent.getActivity(c,e.id,new Intent(c,AlarmActivity.class).putExtra("id",e.id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(ua.reminder.R.drawable.ic_alarm).setContentTitle(e.title).setContentText("Час події настав").setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setContentIntent(screen).setFullScreenIntent(screen,true)
            .addAction(new Notification.Action.Builder(null,"Виконано",action(c,e.id,"done")).build())
            .addAction(new Notification.Action.Builder(null,"Ще 5 хв",action(c,e.id,"snooze")).build()).build();
        n.flags |= Notification.FLAG_INSISTENT; nm(c).notify(e.id,n);
    }
    static synchronized boolean finish(Context c,int id,boolean snooze) {
        Event e=find(c,id); if(e==null) return true;
        if(snooze) {
            e.time=System.currentTimeMillis()+5*60_000L; e.ringing=false;
            try { schedule(c,e); } catch(SecurityException ex) { return false; }
            save(c,e);
        } else { List<Event> events=all(c); events.removeIf(x -> x.id==id); write(c,events); }
        if(!snooze) c.getSystemService(AlarmManager.class).cancel(alarmIntent(c,id));
        nm(c).cancel(id); c.sendBroadcast(new Intent("ua.reminder.UPDATED").setPackage(c.getPackageName())); return true;
    }
}
