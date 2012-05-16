package net.easymodo.asagi.exception;

public class DBConnectionException extends Exception {
    private static final long serialVersionUID = 4232939679949004443L;

    public DBConnectionException(Throwable e) {
        super(e);
    }
    
    public DBConnectionException(String s) {
        super(s);
    }
    
    public DBConnectionException(String s, Throwable c) {
        super(s, c);
    }
}
