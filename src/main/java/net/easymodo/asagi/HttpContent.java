package net.easymodo.asagi;

import org.apache.http.HttpResponse;

public class HttpContent {
    private HttpResponse httpResponse;
    private byte[] content;
    
    public HttpContent( byte[] content, HttpResponse httpResponse) {
        this.content = content;
        this.httpResponse = httpResponse;
    }
    
    public void setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }
    
    public HttpResponse getHttpResponse() {
        return httpResponse;
    }
    
    public void setContent( byte[] content) {
        this.content = content;
    }
    
    public  byte[] getContent() {
        return content;
    }
}
