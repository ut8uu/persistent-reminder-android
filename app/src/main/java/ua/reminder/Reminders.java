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
        String wallStart; String mode=Recurrence.ONCE; long start, interval=3_600_000L, snoozeTime; int count=1;
        boolean repeats() { return !Recurrence.ONCE.equals(mode); }
        long nextAfter(long after) {
            java.time.ZoneId zone=java.time.ZoneId.systemDefault();
            long anchor=Recurrence.INTERVAL.equals(mode) ? start : java.time.LocalDateTime.parse(wallStart).atZone(zone).toInstant().toEpochMilli();
            return Recurrence.next(mode,anchor,interval,count,after,zone);
        }
        String summary() {
            String clock=java.time.LocalDateTime.parse(wallStart).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm",Locale.ENGLISH));
            if(Recurrence.DAILY.equals(mode)) return "Every day at "+clock;
            String gap=interval%86_400_000L==0 ? interval/86_400_000L+" day(s)" : interval%3_600_000L==0 ? interval/3_600_000L+" hour(s)" : interval/60_000L+" minute(s)";
            if(Recurrence.SERIES.equals(mode)) return "Every day from "+clock+": "+count+" times, every "+gap;
            return repeats() ? "Every "+gap : "One-time event";
        }
        Event(int id, String title, long time, boolean ringing) { this.id=id; this.title=title; this.time=time; this.ringing=ringing; this.start=time; this.wallStart=java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(time),java.time.ZoneId.systemDefault()).toString(); }
    }
    static synchronized List<Event> all(Context c) {
        List<Event> events = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences("events",0).getString("items","[]"));
            for(int i=0;i<a.length();i++) { JSONObject o=a.getJSONObject(i); Event e=new Event(o.getInt("id"),o.getString("title"),o.getLong("time"),o.optBoolean("ringing"));
                e.wallStart=o.optString("wallStart",e.wallStart); e.mode=o.optString("mode",Recurrence.ONCE); e.start=o.optLong("start",e.time); e.interval=o.optLong("interval",3_600_000L); e.count=o.optInt("count",1); e.snoozeTime=o.optLong("snoozeTime",0); events.add(e); }
        } catch(JSONException e) { throw new IllegalStateException("Cannot read saved events",e); }
        events.sort(Comparator.comparingLong(e -> e.time)); return events;
    }
    static Event find(Context c,int id) { for(Event e:all(c)) if(e.id==id) return e; return null; }
    static synchronized void save(Context c,Event event) {
        List<Event> events=all(c); events.removeIf(e -> e.id==event.id); events.add(event); write(c,events);
    }
    static void write(Context c,List<Event> events) {
        JSONArray a=new JSONArray();
        try { for(Event e:events) a.put(new JSONObject().put("id",e.id).put("title",e.title).put("time",e.time).put("ringing",e.ringing).put("wallStart",e.wallStart).put("mode",e.mode).put("start",e.start).put("interval",e.interval).put("count",e.count).put("snoozeTime",e.snoozeTime)); }
        catch(JSONException ex) { throw new IllegalStateException(ex); }
        if(!c.getSharedPreferences("events",0).edit().putString("items",a.toString()).commit()) throw new IllegalStateException("Cannot save events");
    }
    static int nextId(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences("events",0);
        int id=p.getInt("next",1); p.edit().putInt("next",id+1).commit(); return id;
    }
    static NotificationManager nm(Context c) { return c.getSystemService(NotificationManager.class); }
    static void channel(Context c) {
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Event alarms",NotificationManager.IMPORTANCE_HIGH);
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
    static void scheduleAt(Context c,Event e,long when,PendingIntent target) {
        AlarmManager alarms=c.getSystemService(AlarmManager.class);
        if(Build.VERSION.SDK_INT>=31 && !alarms.canScheduleExactAlarms()) throw new SecurityException("Exact alarm permission required");
        Intent view=new Intent(c,MainActivity.class);
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(Math.max(when,System.currentTimeMillis()+1000),PendingIntent.getActivity(c,e.id,view,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)),target);
    }
    static void schedule(Context c,Event e) { if(e.time>0) scheduleAt(c,e,e.time,alarmIntent(c,e.id)); }
    static void scheduleSnooze(Context c,Event e) { if(e.snoozeTime>0) scheduleAt(c,e,e.snoozeTime,action(c,e.id,"snoozeRing")); }
    static void trigger(Context c,Event e,boolean snoozed) {
        long now=System.currentTimeMillis();
        if(snoozed) { if(e.snoozeTime==0 || e.snoozeTime>now+1500) return; }
        else {
            if(e.time==0 || e.time>now+1500) return;
            e.time=e.repeats() ? e.nextAfter(now+1500) : 0;
            save(c,e);
            try { schedule(c,e); } catch(SecurityException ignored) { /* Setup can restore permission later. */ }
        }
        // A regular occurrence supersedes a pending snooze; overdue occurrences coalesce.
        e.snoozeTime=0; c.getSystemService(AlarmManager.class).cancel(action(c,e.id,"snoozeRing"));
        save(c,e); ring(c,e);
    }
    static PendingIntent action(Context c,int id,String action) { return PendingIntent.getBroadcast(c,id,new Intent(c,AlarmReceiver.class).setAction(action).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    static void ring(Context c,Event e) {
        channel(c);
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        e.ringing=true; save(c,e);
        PendingIntent screen=PendingIntent.getActivity(c,e.id,new Intent(c,AlarmActivity.class).putExtra("id",e.id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(ua.reminder.R.drawable.ic_alarm).setContentTitle(e.title).setContentText("Your event is due").setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setContentIntent(screen).setFullScreenIntent(screen,true)
            .addAction(new Notification.Action.Builder(null,"Done",action(c,e.id,"done")).build())
            .addAction(new Notification.Action.Builder(null,"Snooze 5 min",action(c,e.id,"snooze")).build()).build();
        n.flags |= Notification.FLAG_INSISTENT; nm(c).notify(e.id,n);
    }
    static synchronized boolean finish(Context c,int id,boolean snooze) {
        Event e=find(c,id); if(e==null) return true;
        if(snooze) {
            e.snoozeTime=System.currentTimeMillis()+5*60_000L;
            try { scheduleSnooze(c,e); } catch(SecurityException ex) { return false; }
            e.ringing=false; save(c,e);
        } else if(e.repeats()) {
            e.ringing=false; e.snoozeTime=0; save(c,e);
            c.getSystemService(AlarmManager.class).cancel(action(c,id,"snoozeRing"));
        } else { delete(c,id); return true; }
        nm(c).cancel(id); return true;
    }
    static synchronized void delete(Context c,int id) {
        List<Event> events=all(c); events.removeIf(e -> e.id==id); write(c,events);
        c.getSystemService(AlarmManager.class).cancel(alarmIntent(c,id));
        c.getSystemService(AlarmManager.class).cancel(action(c,id,"snoozeRing"));
        nm(c).cancel(id);
    }
    static void restore(Context c) {
        for(Event e:all(c)) {
            try {
                if(e.repeats()) {
                    if(e.time>System.currentTimeMillis()) e.time=e.nextAfter(System.currentTimeMillis());
                    save(c,e);
                }
                schedule(c,e); scheduleSnooze(c,e);
                if(e.ringing) ring(c,e);
            } catch(SecurityException ignored) { /* Main screen explains missing permissions. */ }
        }
    }
}
