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
    @Override public void onCreate(Bundle b) { super.onCreate(b); Reminders.channel(this); }
    @Override protected void onResume() { super.onResume(); render(); }
    TextView text(String s,int size) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setPadding(0,14,0,14); root.addView(t); return t; }
    void button(String s,Runnable r) { Button b=new Button(this); b.setText(s); b.setOnClickListener(v->r.run()); root.addView(b); }
    void render() {
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,48,32,32); scroll.addView(root); setContentView(scroll);
        text("Нагадай",32); text("Події, які потребують твоєї уваги",16);
        if(!Reminders.ready(this)) { text("Налаштуй дозволи, щоб будильник міг спрацювати й відкритися на весь екран.",16); button("Налаштувати дозволи",this::permissions); }
        button("＋ Додати подію",this::add);
        java.util.List<Reminders.Event> events=Reminders.all(this);
        if(events.isEmpty()) text("Поки немає подій. Додай перше нагадування.",18);
        for(Reminders.Event e:events) {
            text(e.title,22); text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(e.time))+(e.ringing?" · Час настав":""),16);
            if(e.ringing) button("Відкрити нагадування",()->startActivity(new Intent(this,AlarmActivity.class).putExtra("id",e.id)));
            button("Видалити",()->new AlertDialog.Builder(this).setMessage("Видалити «"+e.title+"»?").setPositiveButton("Видалити",(d,w)->{Reminders.finish(this,e.id,false); render();}).setNegativeButton("Скасувати",null).show());
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
        EditText title=new EditText(this); title.setHint("Наприклад: Зустріч із лікарем"); title.setSingleLine(true); title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Нова подія").setView(title).setPositiveButton("Обрати дату",null).setNegativeButton("Скасувати",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=title.getText().toString().trim(); if(name.isEmpty()) { title.setError("Введи назву події"); return; }
            dialog.dismiss(); Calendar time=Calendar.getInstance();
            new DatePickerDialog(this,(date,y,m,day)->{
                time.set(y,m,day);
                new TimePickerDialog(this,(picker,h,min)->{
                    time.set(Calendar.HOUR_OF_DAY,h); time.set(Calendar.MINUTE,min); time.set(Calendar.SECOND,0); time.set(Calendar.MILLISECOND,0);
                    if(time.getTimeInMillis()<=System.currentTimeMillis()) { Toast.makeText(this,"Обери час у майбутньому",Toast.LENGTH_LONG).show(); return; }
                    Reminders.Event e=new Reminders.Event(Reminders.nextId(this),name,time.getTimeInMillis(),false);
                    Reminders.save(this,e);
                    try { Reminders.schedule(this,e); } catch(SecurityException ex) { Reminders.finish(this,e.id,false); Toast.makeText(this,"Потрібен дозвіл на точні будильники",Toast.LENGTH_LONG).show(); }
                    render();
                },time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE),true).show();
            },time.get(Calendar.YEAR),time.get(Calendar.MONTH),time.get(Calendar.DAY_OF_MONTH)).show();
        })); dialog.show();
    }
}
