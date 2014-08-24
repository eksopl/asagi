package net.easymodo.asagi;


import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class DateUtils {
    public static final DateTimeZone NYC_TIMEZONE = DateTimeZone.forID("America/New_York");

    public static long adjustTimestampEpoch(long dateUtc, DateTimeZone dateTimeZone) {
        DateTime dtDate = new DateTime(dateUtc * 1000L);
        DateTime dtEst = dtDate.withZone(dateTimeZone);
        return (dtEst.withZoneRetainFields(DateTimeZone.UTC).getMillis() / 1000);
    }
}
