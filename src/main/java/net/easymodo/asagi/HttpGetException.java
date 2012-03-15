package net.easymodo.asagi;

public class HttpGetException extends ContentGetException {
    private static final long serialVersionUID = 3389302394457094498L;
    private final int httpStatus;
    
    HttpGetException(Throwable e) {
        super(e);
        this.httpStatus = 0;
    }
    
    HttpGetException(String s, int httpStatus) {
        super("HTTP error: " + s, null);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
