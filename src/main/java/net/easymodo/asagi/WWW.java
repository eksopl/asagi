package net.easymodo.asagi;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

/*
 * This class extends the abstract class Board.
 * It provides basic functionality to fetch things over HTTP.
 * Boards that work over WWW should extend from this class.
 * 
 * Fuuka notes:
 * Equivalent to: Board::WWW
 * 
 * Implementation notes:
 * Uses Apache HttpComponents to provide the same functionality as Perl's LWP. 
 */
public abstract class WWW extends Board {
    private static HttpClient httpClient;
    static {
        HttpClient hc = new ContentEncodingHttpClient();
        ClientConnectionManager ccm = hc.getConnectionManager();
        HttpParams params = hc.getParams();
        
        ThreadSafeClientConnManager tsccm 
            = new ThreadSafeClientConnManager(ccm.getSchemeRegistry());
        tsccm.setDefaultMaxPerRoute(8);
        tsccm.setMaxTotal(50);
        httpClient = new ContentEncodingHttpClient(tsccm, params);
    }

    public WWW() {
    //    httpClient = new ContentEncodingHttpClient();
    }
    
    public HttpContent wget(String link) throws HttpGetException {
        return this.wget(link, "");
    }

    public HttpContent wget(String link, String referer) throws HttpGetException {
        return wget(link, referer, "");
    }
    
    public synchronized HttpContent wget(String link, String referer, String lastMod) throws HttpGetException {
        HttpGet req = new HttpGet(link);
        req.setHeader("Referer", referer);
        if(lastMod != null) req.setHeader("If-Modified-Since", lastMod);

        int statusCode = 0;
        int maxTries = 3;
        HttpEntity entity;
        HttpResponse res;
        
        try {
            do {
                res = httpClient.execute(req);
                statusCode = res.getStatusLine().getStatusCode();
            } while(--maxTries > 0 && statusCode == 500);
        } catch(ClientProtocolException e) {
            throw new HttpGetException(e);
        } catch(IOException e) {
            throw new HttpGetException(e);
        }
        
        if(statusCode != 200) {
            throw new HttpGetException(res.getStatusLine().getReasonPhrase(), statusCode);
        }
        
        entity = res.getEntity();

        // It figures that I only find out about this method after I'm done
        // writing low level InputStream reading code, with a large comment
        // rambling about how much HttpClient sucks compared to LWP.
        byte[] content;
        try {
            content = EntityUtils.toByteArray(entity);
        } catch(IOException e) {
            throw new HttpGetException(e);
        }
        
        return new HttpContent(content, res);
    }
    
    public String doClean(String text) {
        // Replaces &#dddd; HTML entities with the proper Unicode character
        Matcher htmlEscapeMatcher = Pattern.compile("&\\#(\\d+);").matcher(text);
        StringBuffer textSb = new StringBuffer();
        while(htmlEscapeMatcher.find()) {
            String escape = (char) Integer.parseInt(htmlEscapeMatcher.group(1)) + "";
            htmlEscapeMatcher.appendReplacement(textSb, escape);
        }
        htmlEscapeMatcher.appendTail(textSb);
        text = textSb.toString();
        
        // Replaces some other HTML entities
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&quot;", "\"");
        text = text.replaceAll("&amp;", "&");
        
        // Trims whitespace at the beginning and end of lines
        text = text.replaceAll("\\s*$", "");
        text = text.replaceAll("^\\s*$", "");
        
        return text;
    }

}
