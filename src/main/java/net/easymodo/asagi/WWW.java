package net.easymodo.asagi;

import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.HttpGetException;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
    private static final Timer SLEEP_TIMER = new Timer();

    private static class Timer {
        long timer = 0;

        private long getTimer() {
            return timer;
        }

        private void setTimer(long timer) {
            this.timer = timer;
        }
    }

    static {
    	HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(params, 5000);
        HttpConnectionParams.setConnectionTimeout(params, 5000);
        params.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);

        PoolingClientConnectionManager pccm = new PoolingClientConnectionManager();
        pccm.setDefaultMaxPerRoute(100);
        pccm.setMaxTotal(300);
        httpClient = new DecompressingHttpClient(new DefaultHttpClient(pccm, params));
    }

    private HttpResponse wget(String link, String referer, boolean throttle) throws HttpGetException {
        return wget(link, referer, "", throttle);
    }

    private HttpResponse wget(String link, String referer, String lastMod, boolean throttle) throws HttpGetException {
        HttpGet req = new HttpGet(link);
        req.setHeader("Referer", referer);
        if(lastMod != null) req.setHeader("If-Modified-Since", lastMod);

        int statusCode = 0;
        HttpResponse res = null;

        boolean isDone = false;

        while(!isDone) {
            try {
                if(throttle) {
                    while(res == null) {
                        boolean okToGo = false;
                        long timer;
                        synchronized(SLEEP_TIMER) {
                            timer = SLEEP_TIMER.getTimer();
                            long now = System.currentTimeMillis();
                            if(timer == 0 || (now - timer) > 1000) {
                                okToGo = true;
                                SLEEP_TIMER.setTimer(now);
                            }
                        }
                        if(okToGo) {
                            res = httpClient.execute(req);
                        } else {
                            try {
                                Thread.sleep(System.currentTimeMillis() - timer);
                            } catch (InterruptedException e) {
                                // w
                            }
                        }
                    }
                } else {
                    try {
                        res = httpClient.execute(req);
                    } catch(ConnectionPoolTimeoutException e) {
                        try {
                            req.reset();
                            Thread.sleep(5000);
                            continue;
                        } catch (InterruptedException e1) {
                            continue;
                        }
                    }
                }
                statusCode = res.getStatusLine().getStatusCode();
                isDone = true;
            } catch(ClientProtocolException e) {
                req.reset();
                throw new HttpGetException(e);
            } catch(IOException e) {
                req.reset();
                throw new HttpGetException(e);
            }
        }

        if(statusCode != 200) {
            req.reset();
            throw new HttpGetException(res.getStatusLine().getReasonPhrase(), statusCode);
        }

        return res;
    }

    public InputStream wget(String link) throws HttpGetException {
        try {
            return this.wget(link, "", false).getEntity().getContent();
        } catch (IOException e) {
            throw new HttpGetException(e);
        }
    }


    public String[] wgetText(String link, String lastMod, boolean throttle) throws ContentGetException {
        // Throws ContentGetException on failure
        HttpResponse httpResponse = this.wget(link, "", lastMod, throttle);

        Header[] newLastModHead = httpResponse.getHeaders("Last-Modified");
        String newLastMod = null;
        if(newLastModHead.length > 0)
            newLastMod = newLastModHead[0].getValue();

        String pageText;
        try {
            pageText = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
        } catch(UnsupportedEncodingException e) {
            throw new ContentGetException("Unsupported encoding in HTTP response");
        } catch(IOException e) {
            throw new HttpGetException(e);
        } finally {
            try {
                // deal with the rest of this bullshit
                EntityUtils.consume(httpResponse.getEntity());
            } catch (IOException e) {
                // nope don't even care
            }
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
        } catch(UnsupportedEncodingException e) { throw new AssertionError("le broken JVM face"); }

        return link;
    }
}
