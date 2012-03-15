package net.easymodo.asagi;

public class ContentGetException extends Exception {
    private static final long serialVersionUID = -7579965193297805312L;

    ContentGetException(Throwable e) {
        super(e);
    }
    
    ContentGetException(String s) {
        super(s);
    }
    
    ContentGetException(String s, Throwable c) {
        super(s, c);
    }
}
