package net.easymodo.asagi.model;

public class DeletedPost {
    private final int num;
    private final long date;

    public DeletedPost(int num, long date) {
        this.num = num;
        this.date = date;
    }

    public int getNum() {
        return num;
    }

    public long getDate() {
        return date;
    }
}
