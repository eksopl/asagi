package net.easymodo.asagi.exception;

public class BoardInitException extends Exception {
    private static final long serialVersionUID = -1306639121908017480L;

    public BoardInitException(Throwable e) {
        super(e);
    }
    
    public BoardInitException(String s) {
        super(s);
    }
    
    public BoardInitException(String s, Throwable c) {
        super(s, c);
    }
}
