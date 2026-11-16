package ua.reminder;
import java.time.*;

public class RecurrenceTest {
    static int checks;
    static long at(String s,ZoneId zone) { return LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli(); }
    static void expect(String label,long actual,long expected) {
        checks++; if(actual!=expected) throw new AssertionError(label+": expected "+Instant.ofEpochMilli(expected)+", got "+Instant.ofEpochMilli(actual));
    }
    public static void main(String[] args) {
        ZoneId utc=ZoneId.of("UTC"); long hour=3_600_000L;
        long daily=at("2026-11-16T10:00",utc);
        expect("daily exact boundary",Recurrence.next("daily",daily,hour,1,daily,utc),at("2026-11-17T10:00",utc));
        expect("daily before first",Recurrence.next("daily",daily,hour,1,daily-1,utc),daily);
        long start=at("2026-11-16T11:00",utc), cursor=start-1;
        for(int i=0;i<5;i++) { cursor=Recurrence.next("series",start,hour,5,cursor,utc); expect("five hourly reminders "+i,cursor,start+i*hour); }
        expect("series next morning",Recurrence.next("series",start,hour,5,cursor,utc),at("2026-11-17T11:00",utc));
        expect("late completion skips missed slots",Recurrence.next("series",start,hour,5,at("2026-11-16T13:35",utc),utc),at("2026-11-16T14:00",utc));
        long night=at("2026-11-16T23:00",utc);
        expect("series across midnight",Recurrence.next("series",night,hour,5,at("2026-11-17T00:30",utc),utc),at("2026-11-17T01:00",utc));
        expect("night series resets",Recurrence.next("series",night,hour,5,at("2026-11-17T03:00",utc),utc),at("2026-11-17T23:00",utc));
        expect("custom 37-minute cadence",Recurrence.next("interval",start,37*60_000L,1,start+100*60_000L,utc),start+111*60_000L);
        expect("snooze/completion does not drift cadence",Recurrence.next("interval",start,hour,1,start+65*60_000L,utc),start+2*hour);
        expect("one-time exhausted",Recurrence.next("once",start,hour,1,start,utc),0);
        expect("long downtime",Recurrence.next("daily",daily,hour,1,at("2030-01-01T12:00",utc),utc),at("2030-01-02T10:00",utc));
        ZoneId ny=ZoneId.of("America/New_York");
        long spring=at("2026-03-07T10:00",ny);
        expect("daily preserves local time over spring DST",Recurrence.next("daily",spring,hour,1,spring,ny),at("2026-03-08T10:00",ny));
        long fall=at("2026-10-31T10:00",ny);
        expect("daily preserves local time over autumn DST",Recurrence.next("daily",fall,hour,1,fall,ny),at("2026-11-01T10:00",ny));
        long gap=at("2026-03-07T02:30",ny);
        expect("nonexistent local time moves forward",Recurrence.next("daily",gap,hour,1,gap,ny),at("2026-03-08T03:30",ny));
        expect("daily returns after DST gap",Recurrence.next("daily",gap,hour,1,at("2026-03-08T03:30",ny),ny),at("2026-03-09T02:30",ny));
        expect("future series excludes previous day tail",Recurrence.next("series",night,hour,5,night-24*hour,utc),night);
        try { Recurrence.next("series",start,hour,25,start,utc); throw new AssertionError("Invalid series accepted"); }
        catch(IllegalArgumentException expected) { checks++; }
        System.out.println("Passed "+checks+" recurrence checks");
    }
}
