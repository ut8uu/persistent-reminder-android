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
    @Override protected void onResume() { super.onResume(); Reminders.restore(this); render(); }
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
            text(e.title,22); text(e.summary(),16);
            if(e.ringing) text("Reminder active",16);
            if(e.snoozeTime>0) text("Snoozed until: "+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.ENGLISH).format(new Date(e.snoozeTime)),16);
            if(e.time>0) text("Next: "+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.ENGLISH).format(new Date(e.time)),16);
            if(e.ringing) button("Open reminder",()->startActivity(new Intent(this,AlarmActivity.class).putExtra("id",e.id)));
            button("Delete",()->new AlertDialog.Builder(this).setMessage("Delete “"+e.title+"” and all future reminders?").setPositiveButton("Delete",(d,w)->{Reminders.delete(this,e.id); render();}).setNegativeButton("Cancel",null).show());
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
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(32,8,32,8);
        EditText title=new EditText(this); title.setHint("Event title"); title.setSingleLine(true); title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)}); form.addView(title);
        Spinner mode=new Spinner(this); mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"One time","Every day","Custom interval","Daily series"})); form.addView(mode);
        TextView hint=new TextView(this); hint.setText("Daily series example: 5 times, every 1 hour, starting at 11:00. Choose the start date and time next."); form.addView(hint);
        TextView gapLabel=new TextView(this); gapLabel.setText("Repeat every"); form.addView(gapLabel);
        EditText gap=new EditText(this); gap.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); gap.setText("1"); form.addView(gap);
        Spinner unit=new Spinner(this); unit.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Minutes","Hours","Days"})); unit.setSelection(1); form.addView(unit);
        TextView countLabel=new TextView(this); countLabel.setText("Reminders per day"); form.addView(countLabel);
        EditText count=new EditText(this); count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); count.setText("5"); form.addView(count);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id) {
                int intervalVisibility=position>=2 ? View.VISIBLE : View.GONE;
                gapLabel.setVisibility(intervalVisibility); gap.setVisibility(intervalVisibility); unit.setVisibility(intervalVisibility);
                countLabel.setVisibility(position==3 ? View.VISIBLE : View.GONE); count.setVisibility(position==3 ? View.VISIBLE : View.GONE); hint.setVisibility(position==3 ? View.VISIBLE : View.GONE);
            }
        });
        ScrollView scroll=new ScrollView(this); scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("New event").setView(scroll).setPositiveButton("Choose start date",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=title.getText().toString().trim(); if(name.isEmpty()) { title.setError("Enter an event title"); return; }
            int selected=mode.getSelectedItemPosition();
            String repeatMode=new String[]{Recurrence.ONCE,Recurrence.DAILY,Recurrence.INTERVAL,Recurrence.SERIES}[selected];
            long every=3_600_000L; int times=1;
            if(selected>=2) {
                try {
                    long value=Long.parseLong(gap.getText().toString());
                    every=Math.multiplyExact(value,new long[]{60_000L,3_600_000L,86_400_000L}[unit.getSelectedItemPosition()]);
                    if(value<1 || every>3_153_600_000_000L) throw new IllegalArgumentException();
                } catch(IllegalArgumentException | ArithmeticException ex) { gap.setError("Enter a positive interval up to 36,500 days"); return; }
            }
            if(selected==3) {
                try { times=Integer.parseInt(count.getText().toString()); }
                catch(NumberFormatException ex) { count.setError("Enter a number from 1 to 1,440"); return; }
                if(times<1 || times>1440 || (times>1 && every>86_399_999L/(times-1))) { count.setError("Use 1–1,440 reminders spanning less than 24 hours"); return; }
            }
            final long interval=every; final int dailyCount=times;
            dialog.dismiss(); Calendar time=Calendar.getInstance();
            new DatePickerDialog(this,(date,y,m,day)->{
                time.set(y,m,day);
                new TimePickerDialog(this,(picker,h,min)->{
                    time.set(Calendar.HOUR_OF_DAY,h); time.set(Calendar.MINUTE,min); time.set(Calendar.SECOND,0); time.set(Calendar.MILLISECOND,0);
                    if(Recurrence.ONCE.equals(repeatMode) && time.getTimeInMillis()<=System.currentTimeMillis()) { Toast.makeText(this,"Choose a time in the future",Toast.LENGTH_LONG).show(); return; }
                    Reminders.Event e=new Reminders.Event(Reminders.nextId(this),name,time.getTimeInMillis(),false);
                    e.mode=repeatMode; e.interval=interval; e.count=dailyCount;
                    if(e.repeats()) e.time=e.nextAfter(System.currentTimeMillis());
                    Reminders.save(this,e);
                    try { Reminders.schedule(this,e); } catch(SecurityException ex) { Reminders.delete(this,e.id); Toast.makeText(this,"Exact alarm permission is required",Toast.LENGTH_LONG).show(); }
                    render();
                },time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE),true).show();
            },time.get(Calendar.YEAR),time.get(Calendar.MONTH),time.get(Calendar.DAY_OF_MONTH)).show();
        })); dialog.show();
    }
}
