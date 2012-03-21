package net.easymodo.asagi.exception;

public class ContentStoreException extends Exception {
    private static final long serialVersionUID = 7485031790134945834L;

    public ContentStoreException(Throwable e) {
        super(e);
    }
    
    public ContentStoreException(String s) {
        super(s);
    }
    
    public ContentStoreException(String s, Throwable c) {
        super(s, c);
    }
}
