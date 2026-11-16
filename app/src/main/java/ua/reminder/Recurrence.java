package ua.reminder;

import java.time.*;

/** Calendar repeats use local wall-clock time; interval repeats use elapsed milliseconds. */
final class Recurrence {
    static final String ONCE="once", INTERVAL="interval", DAILY="daily", SERIES="series";

    static long next(String mode,long start,long interval,int count,long after,ZoneId zone) {
        if(ONCE.equals(mode)) return start>after ? start : 0;
        if(INTERVAL.equals(mode)) {
            if(interval<=0) throw new IllegalArgumentException("Interval must be positive");
            if(start>after) return start;
            long steps=Math.addExact(Math.subtractExact(after,start)/interval,1);
            return Math.addExact(start,Math.multiplyExact(steps,interval));
        }
        if(!DAILY.equals(mode) && !SERIES.equals(mode)) throw new IllegalArgumentException("Unknown repeat mode");
        int slots=DAILY.equals(mode) ? 1 : count;
        if(slots<1 || slots>1440 || (slots>1 && (interval<60_000 || interval>86_399_999L/(slots-1))))
            throw new IllegalArgumentException("Daily series must fit within 24 hours");
        ZonedDateTime origin=Instant.ofEpochMilli(start).atZone(zone);
        LocalDate date=Instant.ofEpochMilli(Math.max(start,after)).atZone(zone).toLocalDate();
        if(SERIES.equals(mode)) date=date.minusDays(1);
        // A skipped civil date (for example Pacific/Apia) is handled by scanning forward.
        for(int d=0;d<4;d++,date=date.plusDays(1)) {
            long best=Long.MAX_VALUE;
            for(int i=0;i<slots;i++) {
                long candidate=date.atTime(origin.toLocalTime()).plusNanos(interval*i*1_000_000L).atZone(zone).toInstant().toEpochMilli();
                if(candidate>=start && candidate>after && candidate<best) best=candidate;
            }
            if(best!=Long.MAX_VALUE) return best;
        }
        throw new IllegalStateException("Cannot find next occurrence");
    }
}
