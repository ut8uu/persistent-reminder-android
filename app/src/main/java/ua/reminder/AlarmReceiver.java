package ua.reminder;
import android.content.*;
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        int id=i.getIntExtra("id",-1); Reminders.Event e=Reminders.find(c,id); if(e==null) return;
        if("done".equals(i.getAction())) Reminders.finish(c,id,false);
        else if("snooze".equals(i.getAction())) Reminders.finish(c,id,true);
        else if(!e.ringing && e.time<=System.currentTimeMillis()+1500) Reminders.ring(c,e);
    }
}
