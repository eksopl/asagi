package net.easymodo.asagi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.annotation.ThreadSafe;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import net.easymodo.asagi.exception.*;

@ThreadSafe
public class Yotsuba extends WWW {
    private static final Map<String, Integer> sizeMultipliers;
    private static final Pattern threadParsePattern;
    private static final Pattern postParsePattern;
    private static final Pattern threadGetPattern;
    private static final Pattern postGetPattern;
    
    static {
        Map<String, Integer> sizeMuls = new HashMap<String,Integer>();
        sizeMuls.put("B", 1);
        sizeMuls.put("KB", 1024);
        sizeMuls.put("MB", 1024*1024);
        sizeMultipliers = Collections.unmodifiableMap(sizeMuls);
        
        String threadParsePatternString = null; 
        String postParsePatternString = null;
        String threadGetPatternString = null;
        String postGetPatternString = null;
        try {
            threadParsePatternString = Resources.toString(Resources.getResource("net/easymodo/asagi/defs/Yotsuba/thread_parse.regex"), Charsets.UTF_8);
            postParsePatternString = Resources.toString(Resources.getResource("net/easymodo/asagi/defs/Yotsuba/post_parse.regex"), Charsets.UTF_8);
            threadGetPatternString = Resources.toString(Resources.getResource("net/easymodo/asagi/defs/Yotsuba/thread_get.regex"), Charsets.UTF_8);            
            postGetPatternString = Resources.toString(Resources.getResource("net/easymodo/asagi/defs/Yotsuba/post_get.regex"), Charsets.UTF_8);                        
        } catch(IOException e) {
            throw new RuntimeException(e);
        } catch(IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
        
        threadParsePattern = Pattern.compile(threadParsePatternString, Pattern.COMMENTS | Pattern.DOTALL);
        postParsePattern = Pattern.compile(postParsePatternString, Pattern.COMMENTS | Pattern.DOTALL);
        threadGetPattern = Pattern.compile(threadGetPatternString, Pattern.COMMENTS | Pattern.DOTALL);
        postGetPattern = Pattern.compile(postGetPatternString, Pattern.COMMENTS | Pattern.DOTALL);
    }
    
    private final Map<String,String> boardLinks;
    
    public Yotsuba(String boardName) {
        boardLinks = Yotsuba.getBoardLinks(boardName);
    }
    
    private static Map<String,String> getBoardLinks(String boardName) {
        Map<String,String> boardInfo = new HashMap<String,String>();
        boardInfo.put("link", "http://boards.4chan.org/" + boardName);
        boardInfo.put("imgLink", "http://images.4chan.org/" + boardName);
        boardInfo.put("previewLink", "http://0.thumbs.4chan.org/" + boardName);
        boardInfo.put("html", "http://boards.4chan.org/" + boardName + "/");
        return Collections.unmodifiableMap(boardInfo);
    }
    
    public String cleanSimple(String text) {
        return super.doClean(text);
    }
    
    public String doClean(String text) {
        if(text == null) return null;
        
        // SOPA spoilers
        text = text.replaceAll("<span class=\"spoiler\"[^>]*>(.*?)</spoiler>(</span>)?", "$1");
        // Non-public tags
        text = text.replaceAll("\\[(banned|moot)\\]", "[$1:lit]");
        // Comment too long, etc
        text = text.replaceAll("<span class=\"abbr\">.*?</span>", "");
        // Banned text
        text = text.replaceAll("<b style=\"color:red;\">(.*?)</b>", "[banned]$1[/banned]");
        // moot text
        text = text.replaceAll("<div style=\"padding: 5px;margin-left: \\.5em;border-color: #faa;border: 2px dashed rgba\\(255,0,0,\\.1\\);border-radius: 2px\">(.*?)</div>", "[moot]$1[/moot]");
        // bold text
        text = text.replaceAll("<b>(.*?)</b>", "[b]$1[/b]");
        // > implying I'm quoting someone
        text = text.replaceAll("<font class=\"unkfunc\">(.*?)</font>", "$1");
        // Links
        text = text.replaceAll("<a[^>]*>(.*?)</a>", "$1");
        // Spoilers (start)
        text = text.replaceAll("<span class=\"spoiler\"[^>]*>", "[spoiler]");
        // Spoilers (end)
        text = text.replaceAll("</span>", "[/spoiler]");
        // Newlines
        text = text.replaceAll("<br\\s*/?>", "\n");

        return this.cleanSimple(text);
    }

    public int parseDate(String date) {
        Pattern pat = 
            Pattern.compile("(\\d+)/(\\d+)/(\\d+)\\s*\\(\\w+\\)\\s*(\\d+):(\\d+)(?::(\\d+))?", Pattern.COMMENTS);
        Matcher mat = pat.matcher(date);
        
        if(!mat.find())
            throw new IllegalArgumentException("Malformed date string");
        
        int mon = Integer.parseInt(mat.group(1));
        int mday = Integer.parseInt(mat.group(2));
        int year = Integer.parseInt(mat.group(3));
        int hour = Integer.parseInt(mat.group(4));
        int min = Integer.parseInt(mat.group(5));
        int sec = (mat.group(6) != null) ? Integer.parseInt(mat.group(6)) : 0;
        
        // Don't forget to change this in 88 years from now
        DateTime dtDate = new DateTime(year + 2000, mon, mday, hour, min, sec, DateTimeZone.UTC);
        return (int) (dtDate.getMillis() / 1000);
    }
    
    public int parseFilesize(String text) {
        if(text == null) return 0;
        
        Pattern pat = Pattern.compile("([\\.\\d]+) \\s (.*)", Pattern.COMMENTS);
        Matcher mat = pat.matcher(text);
        
        if(!mat.find())
            throw new IllegalArgumentException("Malformed filesize string");
        
        float v = Float.parseFloat(mat.group(1));
        String m = mat.group(2);
        
        return (int) (v * sizeMultipliers.get(m));
    }
    
    public Post newYotsubaPost(String link, String mediaFilename, boolean spoiler,
            String filesize, int width, int height, String filename, int twidth,
            int theight, String md5base64, int num, String title, String email,
            String name, String trip, String capcode, String date, boolean sticky, 
            String posterHash, String comment, boolean omitted, int threadNum) throws ContentParseException 
    {
        String type = "";
        String mediaOrig = null;
        String previewOrig = null;
        String md5 = null;
        
        // TODO add the following variables
        String exif = null;
        int timeStampExpired = 0;
        
        boolean op = threadNum == num;

        if(name.equals("")) name = null;
        if(comment.equals("")) comment = null;
        if(title.equals("")) title = null;
        
        if(link != null) {
            Pattern pat = Pattern.compile("/src/(\\d+)\\.(\\w+)");
            Matcher mat = pat.matcher(link);
            mat.find();
            
            String number = mat.group(1);
            type = mat.group(2);
            
            if(filename != null) {
                mediaOrig = filename;
            } else {
                mediaOrig = number + "." + type;
            }
            
            previewOrig = number + "s.jpg";
          
            md5 = md5base64;
        }
        
        int timeStamp;
        int mediaSize;
        try {
            timeStamp = this.parseDate(date);
            mediaSize = this.parseFilesize(filesize);
        } catch(IllegalArgumentException e) {
            throw new ContentParseException("Could not create post " + num , e);
        }
        
        Post post = new Post();
        post.setLink(link);
        post.setType(type);
        post.setMediaOrig(mediaOrig);
        post.setMediaHash(md5);
        post.setMediaFilename(mediaFilename);
        post.setMediaSize(mediaSize);
        post.setMediaW(width);
        post.setMediaH(height);
        post.setPreviewOrig(previewOrig);
        post.setPreviewW(twidth);
        post.setPreviewH(theight);
        post.setExif(exif);
        post.setNum(num);
        post.setThreadNum(threadNum);
        post.setOp(op);
        post.setTitle(this.cleanSimple(title));
        post.setEmail(email);
        post.setName(this.cleanSimple(name));
        post.setTrip(trip);
        post.setDate(timeStamp);
        post.setDateExpired(timeStampExpired);
        post.setComment(this.doClean(comment));
        post.setSpoiler(spoiler);
        post.setDeleted(false);
        post.setSticky(sticky);
        post.setCapcode(capcode);
        post.setPosterHash(posterHash);
        post.setOmitted(omitted);
        
        return post;
    }    
    
    @Override
    public InputStream getMediaPreview(MediaPost h) throws ContentGetException {
        if(h.getPreviewFilename() == null)
            return null;
        
        InputStream inStream = null;
        try {
            inStream = this.wget(this.boardLinks.get("previewLink") + "/thumb/"
                + h.getPreviewFilename() + "?" + System.currentTimeMillis()).getEntity().getContent();
        } catch(IOException e) {
            throw new ContentGetException(e);
        }
        
        return inStream;
    }
    
    @Override
    public InputStream getMedia(MediaPost h) throws ContentGetException {
        if(h.getMediaFilename() == null)
            return null;
        
        InputStream inStream = null;
        try {
            inStream = this.wget(this.boardLinks.get("imgLink") + "/src/"
                + h.getMediaFilename() + "?" + System.currentTimeMillis()).getEntity().getContent();
        } catch(IOException e) {
            throw new ContentGetException(e);
        }
                
        return inStream;
    }
    

    public Topic parseThread(String text) throws ContentParseException {
        Matcher mat = threadParsePattern.matcher(text);
        
        if(!mat.find()) {
            throw new ContentParseException("Could not parse thread (thread regex failed)");
        }
        
        // Java's substring methods actually just return a new string that
        // points to the original string.
        // In our case, Java will keep the entire page HTML on its heap until
        // it can collect all of the substring.
        // This is very wasteful when it comes to memory, so we force creating
        // new strings for the regex matches through the String constructor.
        String link       = (mat.group(1) != null) ? new String(mat.group(1)) : null;
        String mediaFn    = (mat.group(2) != null) ?  new String(mat.group(2)) : null;
        boolean spoiler   = (mat.group(3) != null);
        String fileSize   = (mat.group(4) != null) ? new String(mat.group(4)) : null;
        int width         = (mat.group(5) != null) ? Integer.parseInt(mat.group(5)) : 0;
        int height        = (mat.group(6) != null) ? Integer.parseInt(mat.group(6)) : 0;
        String fileName   = (mat.group(7) != null) ? new String(mat.group(7)) : null;
        int tWidth        = (mat.group(8) != null) ? Integer.parseInt(mat.group(8)) : 0;
        int tHeight       = (mat.group(9) != null) ? Integer.parseInt(mat.group(9)): 0;
        String md5b64     = (mat.group(10) != null) ? new String(mat.group(10)) : null;
        int num           = Integer.parseInt(mat.group(11));
        String title      = new String(mat.group(12));
        String email      = (mat.group(13) != null) ? new String(mat.group(13)) : null;
        String name       = new String(mat.group(14));
        String trip       = (mat.group(15) != null) ? new String(mat.group(15)) : null;
        String capcode    = (mat.group(16) == null) ? 
                          	((mat.group(17) != null) ? new String(mat.group(17)) : null) : 
                          	new String(mat.group(16));
        String posterHash = (mat.group(18) != null) ? new String(mat.group(18)) : null;
        String date       = (mat.group(19) != null) ? new String(mat.group(19)) : null;
        boolean sticky    = (mat.group(20) != null);
        String comment    = new String(mat.group(21));
        boolean omitted   = (mat.group(22) != null);
        int omPosts       = (mat.group(23) != null) ? Integer.parseInt(mat.group(23)) : 0;
        int omImages      = (mat.group(24) != null) ? Integer.parseInt(mat.group(24)) : 0;
        
        Topic thread = new Topic(num, omPosts, omImages);
        Post op = this.newYotsubaPost(link, mediaFn, spoiler, fileSize, width, height, fileName, tWidth, 
                tHeight, md5b64, num, title, email, name, trip, capcode, date, sticky, posterHash, comment, omitted, 0);
        thread.addPost(op);
        
        return thread;
    }
    
    public Post parsePost(String text, int threadNum) throws ContentParseException {
        Matcher mat = postParsePattern.matcher(text);
        
        if(!mat.find()) {
            throw new ContentParseException("Could not parse post (post regex failed)");
        }        
        int num           = Integer.parseInt(mat.group(1));
        String title      = new String(mat.group(2));
        String email      = (mat.group(3) != null) ? new String(mat.group(3)) : null;
        String name       = new String(mat.group(4));
        String trip       = (mat.group(5) != null) ? new String(mat.group(5)) : null;
        String capcode    = (mat.group(6) == null) ? 
                            ((mat.group(7) != null) ? new String(mat.group(7)) : null) : 
                            new String(mat.group(6));
        String posterHash = (mat.group(8) != null) ? new String(mat.group(8)) : null;
        String date       = (mat.group(9) != null) ? new String(mat.group(9)) : null;
        String link       = (mat.group(10) != null) ? new String(mat.group(10)) : null;
        String mediaFn    = (mat.group(11) != null) ? new String(mat.group(11)) : null;
        boolean spoiler   = (mat.group(12) != null);
        String fileSize   = (mat.group(13) != null) ? new String(mat.group(13)) : null;
        int width         = (mat.group(14) != null) ? Integer.parseInt(mat.group(14)) : 0;
        int height        = (mat.group(15) != null) ? Integer.parseInt(mat.group(15)) : 0;
        String fileName   = (mat.group(16) != null) ? new String(mat.group(16)) : null;
        int tWidth        = (mat.group(17) != null) ? Integer.parseInt(mat.group(17)) : 0;
        int tHeight       = (mat.group(18) != null) ? Integer.parseInt(mat.group(18)) : 0;
        String md5b64     = (mat.group(19) != null) ? new String(mat.group(19)) : null;
        String comment    = new String(mat.group(20));
        boolean omitted   = (mat.group(21) != null);
        boolean sticky    = false;
       
        Post post = this.newYotsubaPost(link, mediaFn, spoiler, fileSize, width, height, fileName, tWidth, 
                tHeight, md5b64, num, title, email, name, trip, capcode, date, sticky, posterHash, comment, omitted, threadNum);
        
        return post;
    }
    
    public String linkPage(int pageNum) {
        if(pageNum == 0) {
            return this.boardLinks.get("link") + "/";
        } else {
            return this.boardLinks.get("link") + "/" + pageNum;
        }
    }
    
    public String linkThread(int thread) {
        if(thread != 0) {
            return this.boardLinks.get("link") + "/res/" + thread;
        } else {
            return this.linkPage(0);
        }
    }
    
    @Override
    public Page getPage(int pageNum, String lastMod) throws ContentGetException, ContentParseException {
        String[] wgetReply = this.wgetText(this.linkPage(pageNum), lastMod);
        String pageText = wgetReply[0];
        String newLastMod = wgetReply[1];

        Page p = new Page(pageNum);
        Topic t = null;
        
        Matcher mat = threadGetPattern.matcher(pageText);
                
        while(mat.find()) {
            String text = mat.group(1);
            String type = mat.group(2);
            
            if(type != null) {
                t = this.parseThread(text);
                p.addThread(t);
            } else {
                if(t != null) t.addPost(this.parsePost(text, t.getNum()));
            }
        }
        
        p.setLastMod(newLastMod);
        return p;
    }
    
    @Override
    public Topic getThread(int threadNum, String lastMod) throws ContentGetException, ContentParseException {
        String[] wgetReply = this.wgetText(this.linkThread(threadNum), lastMod);
        String threadText = wgetReply[0];
        String newLastMod = wgetReply[1];
        
        Topic t = null;
        
        Matcher mat = postGetPattern.matcher(threadText);
        
        while(mat.find()) {
            String text = mat.group(1);
            String type = mat.group(2);
            if(type != null) {
                if(t == null) {
                    t = this.parseThread(text);
                } else {
                    throw new ContentParseException("Two OP posts in thread in " + threadNum);
                }
            } else {
                if(t != null) {
                    t.addPost(this.parsePost(text, t.getNum()));
                } else {
                    throw new ContentParseException("Thread without OP post in " + threadNum);
                }
            }
        }
        
        t.setLastMod(newLastMod);
        return t;
    }
}
