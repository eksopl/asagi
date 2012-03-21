package net.easymodo.asagi.exception;

public class ContentGetException extends Exception {
    private static final long serialVersionUID = 1141146319059000362L;

    public ContentGetException(Throwable e) {
        super(e);
    }
    
    public ContentGetException(String s) {
        super(s);
    }
    
    public ContentGetException(String s, Throwable c) {
        super(s, c);
    }
}
