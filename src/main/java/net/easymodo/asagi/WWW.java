package net.easymodo.asagi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;

import net.easymodo.asagi.exception.*;


/**
 * This class extends the abstract class Board.
 * It provides basic functionality to fetch things over HTTP.
 * Boards that work over WWW should extend from this class.
 *
 * Fuuka notes:
 * Equivalent to: Board::WWW
 *
 * Implementation notes:
 * Uses Apache HttpComponents to provide functionality similar to Perl's LWP.
 **/
@ThreadSafe
public abstract class WWW extends Board {
    private static HttpClient httpClient;

    static {
    	HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(params, 20000);
        HttpConnectionParams.setConnectionTimeout(params, 20000);
        params.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);

        PoolingClientConnectionManager pccm = new PoolingClientConnectionManager();
        pccm.setDefaultMaxPerRoute(20);
        pccm.setMaxTotal(100);
        httpClient = new DecompressingHttpClient(new DefaultHttpClient(pccm, params));
    }

    public HttpResponse wget(String link) throws HttpGetException {
        return this.wget(link, "");
    }

    public HttpResponse wget(String link, String referer) throws HttpGetException {
        return wget(link, referer, "");
    }

    public HttpResponse wget(String link, String referer, String lastMod) throws HttpGetException {
        HttpGet req = new HttpGet(link);
        req.setHeader("Referer", referer);
        if(lastMod != null) req.setHeader("If-Modified-Since", lastMod);

        int statusCode;
        int maxTries = 3;
        HttpEntity entity;
        HttpResponse res;

        try {
            do {
                res = httpClient.execute(req);
                statusCode = res.getStatusLine().getStatusCode();
            } while(--maxTries > 0 && (statusCode == 500 || statusCode == 503));
        } catch(ClientProtocolException e) {
            throw new HttpGetException(e);
        } catch(IOException e) {
            throw new HttpGetException(e);
        } finally {
            req.releaseConnection();
        }

        entity = res.getEntity();

        if(statusCode != 200) {
            try {
                // Consume the rest of the HTTP response
                // (closes underlying stream, etc)
                EntityUtils.consume(entity);
            } catch(IOException e) {
                throw new HttpGetException(e);
            }
            throw new HttpGetException(res.getStatusLine().getReasonPhrase(), statusCode);
        }

        return res;
    }

    public String[] wgetText(String link, String lastMod) throws ContentGetException {
        // Throws ContentGetException on failure
        HttpResponse httpResponse = this.wget(link, "", lastMod);

        Header[] newLastModHead = httpResponse.getHeaders("Last-Modified");
        String newLastMod = null;
        if(newLastModHead.length > 0)
            newLastMod = newLastModHead[0].getValue();

        String pageText = null;
        try {
            pageText = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
        } catch(UnsupportedEncodingException e) {
            throw new ContentGetException("Unsupported encoding in HTTP response");
        } catch(IOException e) {
            throw new HttpGetException(e);
        }

        return new String[] {pageText, newLastMod};
    }

    public String doClean(String text) {
        if(text == null) return null;

        // Replaces &#dddd; HTML entities with the proper Unicode character
        Matcher htmlEscapeMatcher = Pattern.compile("&#(\\d+);").matcher(text);
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

    public String doCleanLink(String link) {
        if(link == null) return null;

        try {
            link = URLDecoder.decode(link, "UTF-8");
        } catch(UnsupportedEncodingException e) {}

        return link;
    }
}
