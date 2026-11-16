package ua.reminder;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root;
    @Override protected void attachBaseContext(Context base) {
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(java.util.Locale.ENGLISH);
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    @Override public void onCreate(Bundle b) { super.onCreate(b); Reminders.channel(this); }
    @Override protected void onResume() { super.onResume(); render(); }
    TextView text(String s,int size) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setPadding(0,14,0,14); root.addView(t); return t; }
    void button(String s,Runnable r) { Button b=new Button(this); b.setText(s); b.setOnClickListener(v->r.run()); root.addView(b); }
    void render() {
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,48,32,32); scroll.addView(root); setContentView(scroll);
        text("Persistent Reminder",32); text("Events that need your attention",16);
        if(!Reminders.ready(this)) { text("Enable the required permissions so alarms can ring and appear full screen.",16); button("Set up permissions",this::permissions); }
        button("＋ Add event",this::add);
        java.util.List<Reminders.Event> events=Reminders.all(this);
        if(events.isEmpty()) text("No events yet. Add your first reminder.",18);
        for(Reminders.Event e:events) {
            text(e.title,22); text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.ENGLISH).format(new Date(e.time))+(e.ringing?" · Due now":""),16);
            if(e.ringing) button("Open reminder",()->startActivity(new Intent(this,AlarmActivity.class).putExtra("id",e.id)));
            button("Delete",()->new AlertDialog.Builder(this).setMessage("Delete “"+e.title+"”?").setPositiveButton("Delete",(d,w)->{Reminders.finish(this,e.id,false); render();}).setNegativeButton("Cancel",null).show());
        }
    }
    void permissions() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1); return; }
        Intent i;
        if(Build.VERSION.SDK_INT>=31 && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));
        else if(Build.VERSION.SDK_INT>=34 && !Reminders.nm(this).canUseFullScreenIntent()) i=new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName()));
        else i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,Reminders.CHANNEL);
        startActivity(i);
    }
    void add() {
        if(!Reminders.ready(this)) { permissions(); return; }
        EditText title=new EditText(this); title.setHint("For example: Doctor’s appointment"); title.setSingleLine(true); title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("New event").setView(title).setPositiveButton("Choose date",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=title.getText().toString().trim(); if(name.isEmpty()) { title.setError("Enter an event title"); return; }
            dialog.dismiss(); Calendar time=Calendar.getInstance();
            new DatePickerDialog(this,(date,y,m,day)->{
                time.set(y,m,day);
                new TimePickerDialog(this,(picker,h,min)->{
                    time.set(Calendar.HOUR_OF_DAY,h); time.set(Calendar.MINUTE,min); time.set(Calendar.SECOND,0); time.set(Calendar.MILLISECOND,0);
                    if(time.getTimeInMillis()<=System.currentTimeMillis()) { Toast.makeText(this,"Choose a time in the future",Toast.LENGTH_LONG).show(); return; }
                    Reminders.Event e=new Reminders.Event(Reminders.nextId(this),name,time.getTimeInMillis(),false);
                    Reminders.save(this,e);
                    try { Reminders.schedule(this,e); } catch(SecurityException ex) { Reminders.finish(this,e.id,false); Toast.makeText(this,"Exact alarm permission is required",Toast.LENGTH_LONG).show(); }
                    render();
                },time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE),true).show();
            },time.get(Calendar.YEAR),time.get(Calendar.MONTH),time.get(Calendar.DAY_OF_MONTH)).show();
        })); dialog.show();
    }
}
