package ua.reminder;
import android.content.*;
public class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        for(Reminders.Event e:Reminders.all(c)) {
            try { if(e.ringing) Reminders.ring(c,e); else Reminders.schedule(c,e); }
            catch(SecurityException ignored) { /* Main screen explains the missing alarm permission. */ }
        }
    }
}
