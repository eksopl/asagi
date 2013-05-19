package net.easymodo.asagi.model;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class DeletePost {
    private final int num;
    private final int date;

    public DeletePost(int num, long date) {
        this.num = num;
        this.date = parseDate(date);
    }

    public int getNum() {
        return num;
    }

    public int getDate() {
        return date;
    }

    public int parseDate(long date) {
        DateTime dtDate = new DateTime(date);
        DateTime dtEst = dtDate.withZone(DateTimeZone.forID("America/New_York"));
        return (int) (dtEst.withZoneRetainFields(DateTimeZone.UTC).getMillis() / 1000);
    }
}
