package ua.reminder;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public class AlarmActivity extends Activity {
    int id;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable check=new Runnable() { public void run() { Reminders.Event e=Reminders.find(AlarmActivity.this,id); if(e==null || !e.ringing) finish(); else handler.postDelayed(this,1000); } };
    @Override protected void attachBaseContext(Context base) {
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(java.util.Locale.ENGLISH);
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if(Build.VERSION.SDK_INT>=27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        show(getIntent());
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); show(i); }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(check); handler.post(check); }
    @Override protected void onPause() { handler.removeCallbacks(check); super.onPause(); }
    void show(Intent i) {
        id=i.getIntExtra("id",-1); Reminders.Event e=Reminders.find(this,id); if(e==null || !e.ringing) { finish(); return; }
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(36,48,36,48); root.setBackgroundColor(Color.rgb(17,24,39));
        TextView label=new TextView(this); label.setText("EVENT REMINDER"); label.setTextColor(Color.rgb(165,180,252)); label.setTextSize(18); root.addView(label);
        TextView name=new TextView(this); name.setText(e.title); name.setTextColor(Color.WHITE); name.setTextSize(34); name.setGravity(Gravity.CENTER); name.setPadding(0,40,0,48); root.addView(name);
        Button done=new Button(this); done.setText("Done"); done.setOnClickListener(v->resolve(false)); root.addView(done,new LinearLayout.LayoutParams(-1,80));
        Button snooze=new Button(this); snooze.setText("Snooze for 5 minutes"); snooze.setOnClickListener(v->resolve(true)); root.addView(snooze,new LinearLayout.LayoutParams(-1,80)); setContentView(root);
    }
    void resolve(boolean snooze) {
        if(Reminders.finish(this,id,snooze)) finish();
        else new AlertDialog.Builder(this).setMessage("Unable to snooze. Enable exact alarms in the app settings.").setPositiveButton("OK",null).show();
    }
}
