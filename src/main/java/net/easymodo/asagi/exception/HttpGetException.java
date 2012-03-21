package net.easymodo.asagi.exception;

public class HttpGetException extends ContentGetException {
    private static final long serialVersionUID = -5103283207373620298L;
    private final int httpStatus;
    
    public HttpGetException(Throwable e) {
        super(e);
        this.httpStatus = 0;
    }
    
    public HttpGetException(String s, int httpStatus) {
        super("HTTP error: " + s, null);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
