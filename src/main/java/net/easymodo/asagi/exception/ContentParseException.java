package net.easymodo.asagi.exception;

public class ContentParseException extends Exception {
    private static final long serialVersionUID = -8123637445404320429L;

    public ContentParseException(Throwable e) {
        super(e);
    }

    public ContentParseException(String s) {
        super(s);
    }

    public ContentParseException(String s, Throwable c) {
        super(s, c);
    }
}
