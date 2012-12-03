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
    private static final Pattern postParsePattern1;
    private static final Pattern postParsePattern2;
    private static final Pattern postGetPattern;

    private static final Pattern numPattern;
    private static final Pattern titlePattern;
    private static final Pattern datePattern;
    private static final Pattern emailPattern;
    private static final Pattern commentPattern;
    private static final Pattern stickyPattern;
    private static final Pattern omittedPattern;
    private static final Pattern oldCapPattern;

    private static final Pattern omPostsPattern;
    private static final Pattern omImagesPattern;

    static {
        Map<String, Integer> sizeMuls = new HashMap<String,Integer>();
        sizeMuls.put("B", 1);
        sizeMuls.put("KB", 1024);
        sizeMuls.put("MB", 1024*1024);
        sizeMultipliers = Collections.unmodifiableMap(sizeMuls);

        String postParsePatternString1;
        String postParsePatternString2;
        String postGetPatternString;
        try {
            postParsePatternString1 = Resources.toString(Resources.getResource("net/easymodo/asagi/defs/Yotsuba/post_parse_1.regex"), Charsets.UTF_8);
            postParsePatternString2 = Resources.toString(Resources.getResource("net/easymodo/asagi/defs/Yotsuba/post_parse_2.regex"), Charsets.UTF_8);
            postGetPatternString = Resources.toString(Resources.getResource("net/easymodo/asagi/defs/Yotsuba/post_get.regex"), Charsets.UTF_8);
        } catch(IOException e) {
            throw new RuntimeException(e);
        } catch(IllegalArgumentException e) {
            throw new RuntimeException(e);
        }

        postParsePattern1 = Pattern.compile(postParsePatternString1, Pattern.COMMENTS | Pattern.DOTALL);
        postParsePattern2 = Pattern.compile(postParsePatternString2, Pattern.COMMENTS | Pattern.DOTALL);
        postGetPattern = Pattern.compile(postGetPatternString, Pattern.COMMENTS | Pattern.DOTALL);

        String numPatString = "<div \\s id=\"p(\\d+)\" \\s class=\"post \\s [^\"]*\">";
        String titlePatString = "<span \\s class=\"subject\">([^<]*)</span>";
        String emailPatString = "<a \\s href=\"mailto:([^\"]*)\" \\s class=\"useremail\">";
        String datePatString = "<span \\s class=\"dateTime\" \\s data-utc=\"([0-9]+)\">([^<]*)</span>";
        String commentPatString = "<blockquote \\s class=\"postMessage\" [^>]*>(.*?)</blockquote>";
        String stickyPatString = "<img \\s src=\"[^\"]*\" \\s alt=\"Sticky\" \\s title=\"Sticky\"[^>]*>";
        String omittedPatString = "<span \\s class=\"abbr\">Comment \\s too \\s long";
        String oldCapPatString = "<span \\s class=\"commentpostername\"><span [^>]*>## \\s (.)[^<]*</span></span>";

        String omPostsPatString = "<span \\s class=\"info\">\\s*<strong>(\\d*) \\s posts \\s omitted";
        String omImagesPatString = "<em>\\((\\d*) \\s have \\s images\\)</em>";

        numPattern = Pattern.compile(numPatString, Pattern.COMMENTS | Pattern.DOTALL);
        titlePattern = Pattern.compile(titlePatString, Pattern.COMMENTS | Pattern.DOTALL);
        emailPattern = Pattern.compile(emailPatString, Pattern.COMMENTS | Pattern.DOTALL);
        datePattern = Pattern.compile(datePatString, Pattern.COMMENTS | Pattern.DOTALL);
        commentPattern = Pattern.compile(commentPatString, Pattern.COMMENTS | Pattern.DOTALL);
        stickyPattern = Pattern.compile(stickyPatString, Pattern.COMMENTS | Pattern.DOTALL);
        omittedPattern = Pattern.compile(omittedPatString, Pattern.COMMENTS | Pattern.DOTALL);
        oldCapPattern = Pattern.compile(oldCapPatString, Pattern.COMMENTS | Pattern.DOTALL);


        omPostsPattern = Pattern.compile(omPostsPatString, Pattern.COMMENTS | Pattern.DOTALL);
        omImagesPattern = Pattern.compile(omImagesPatString, Pattern.COMMENTS | Pattern.DOTALL);
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

    public String cleanLink(String text) {
        return super.doCleanLink(super.doClean(text));
    }

    public String doClean(String text) {
        if(text == null) return null;

        // SOPA spoilers
        //text = text.replaceAll("<span class=\"spoiler\"[^>]*>(.*?)</spoiler>(</span>)?", "$1");

        // Admin-Mod-Dev quotelinks
        text = text.replaceAll("<span class=\"capcodeReplies\"><span class=\"smaller\"><span class=\"bold\">(?:Administrator|Moderator|Developer) Repl(?:y|ies):</span>.*?</span><br></span>", "");
        // Non-public tags
        text = text.replaceAll("\\[(banned|moot)\\]", "[$1:lit]");
        // Comment too long, also EXIF tag toggle
        text = text.replaceAll("<span class=\"abbr\">.*?</span>", "");
        // Banned/Warned text
        text = text.replaceAll("<(?:b|strong) style=\"color:\\s*red;\">(.*?)</(?:b|strong)>", "[banned]$1[/banned]");
        // moot text
        text = text.replaceAll("<div style=\"padding: 5px;margin-left: \\.5em;border-color: #faa;border: 2px dashed rgba\\(255,0,0,\\.1\\);border-radius: 2px\">(.*?)</div>", "[moot]$1[/moot]");
        // bold text
        text = text.replaceAll("<(?:b|strong)>(.*?)</(?:b|strong)>", "[b]$1[/b]");
        // > implying I'm quoting someone
        text = text.replaceAll("<font class=\"unkfunc\">(.*?)</font>", "$1");
        text = text.replaceAll("<span class=\"quote\">(.*?)</span>", "$1");
        // Dead Quotes
        text = text.replaceAll("<span class=\"deadlink\">(.*?)</span>", "$1");
        text = text.replaceAll("<span class=\"quote deadlink\">(.*?)</span>", "$1");
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

    public int parseDate(int dateUtc) {
        DateTime dtDate = new DateTime(dateUtc * 1000L);
        DateTime dtEst = dtDate.withZone(DateTimeZone.forID("America/New_York"));
        return (int) (dtEst.withZoneRetainFields(DateTimeZone.UTC).getMillis() / 1000);
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

    public Post newYotsubaPost(String link, String mediaOrig, boolean spoiler,
            String filesize, int width, int height, String filename, int tWidth,
            int tHeight, String md5, int num, String title, String email,
            String name, String trip, String capcode, int dateUtc, boolean sticky,
            String comment, boolean omitted, int threadNum, String posterHash, String posterCountry) throws ContentParseException
    {
        String type = "";
        String previewOrig = null;

        // TODO: add the following variables
        String exif = null;
        int timeStampExpired = 0;

        if(threadNum == 0) threadNum = num;
        boolean op = (threadNum == num);

        if(name.equals("")) name = null;
        if(comment.equals("")) comment = null;
        if(title.equals("")) title = null;
        if(posterCountry != null && (posterCountry.equals("XX") || posterCountry.equals("A1"))) posterCountry = null;

        if(link != null) {
            Pattern pat = Pattern.compile("/src/(\\d+)\\.(\\w+)");
            Matcher mat = pat.matcher(link);
            if(mat.find()) {
                String number = mat.group(1);
                type = mat.group(2);

                filename = (filename != null) ? filename : (number + "." + type);
                if(mediaOrig == null) mediaOrig = number + "." + type;
                previewOrig = number + "s.jpg";
            }
        }

        if(spoiler) {
            tWidth = 0;
            tHeight = 0;
        }

        int timeStamp;
        int mediaSize;
        try {
            timeStamp = this.parseDate(dateUtc);
            mediaSize = this.parseFilesize(filesize);
        } catch(IllegalArgumentException e) {
            throw new ContentParseException("Could not create post " + num , e);
        }

        Post post = new Post();
        post.setLink(link);
        post.setType(type);
        post.setMediaOrig(mediaOrig);
        post.setMediaHash(md5);
        post.setMediaFilename(filename);
        post.setMediaSize(mediaSize);
        post.setMediaW(width);
        post.setMediaH(height);
        post.setPreviewOrig(previewOrig);
        post.setPreviewW(tWidth);
        post.setPreviewH(tHeight);
        post.setExif(exif);
        post.setNum(num);
        post.setThreadNum(threadNum);
        post.setOp(op);
        post.setTitle(this.cleanSimple(title));
        post.setEmail(this.cleanLink(email));
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
        post.setPosterCountry(posterCountry);
        post.setOmitted(omitted);

        return post;
    }

    @Override
    public InputStream getMediaPreview(MediaPost h) throws ContentGetException {
        if(h.getPreview() == null)
            return null;

        InputStream inStream;
        try {
            inStream = this.wget(this.boardLinks.get("previewLink") + "/thumb/"
                + h.getPreview()).getEntity().getContent();
        } catch(IOException e) {
            throw new ContentGetException(e);
        }

        return inStream;
    }

    @Override
    public InputStream getMedia(MediaPost h) throws ContentGetException {
        if(h.getMedia() == null)
            return null;

        InputStream inStream;
        try {
            inStream = this.wget(this.boardLinks.get("imgLink") + "/src/"
                + h.getMedia()).getEntity().getContent();
        } catch(IOException e) {
            throw new ContentGetException(e);
        }

        return inStream;
    }


    public Topic parseThread(String text) throws ContentParseException {
        int omPosts = 0;
        Matcher mat = omPostsPattern.matcher(text);
        if(mat.find()) omPosts = Integer.parseInt(mat.group(1));

        int omImages = 0;
        mat = omImagesPattern.matcher(text);
        if(mat.find()) omImages = Integer.parseInt(mat.group(1));

        Post op = this.parsePost(text, 0);
        Topic thread = new Topic(op.getNum(), omPosts, omImages);
        thread.addPost(op);

        return thread;
    }


    @SuppressWarnings("RedundantStringConstructorCall")
    public Post parsePost(String text, int threadNum) throws ContentParseException {
        // Java's substring methods actually just return a new string that
        // points to the original string.
        // In our case, Java will keep the entire page HTML on its heap until
        // it can garbage collect all of the substrings returned by the matchers.
        // This is very wasteful when it comes to memory, so throughout this
        // method, we will be forcing the creation of new strings for all
        // string regex matches through the String constructor.
        // Software like FindBugs will complain we're pointlessly calling the
        // String constructor, but in this case, we know exactly what we're doing.

        Matcher mat = numPattern.matcher(text);
        if(!mat.find()) {
            throw new ContentParseException("Could not parse thread (post num regex failed)");
        }
        int num = Integer.parseInt(mat.group(1));

        mat = titlePattern.matcher(text);
        if(!mat.find()) {
            throw new ContentParseException("Could not parse thread (post title regex failed)");
        }
        String title = new String(mat.group(1));

        String email = null;
        mat = emailPattern.matcher(text);
        if(mat.find()) {
            email = new String(mat.group(1));
        }

        mat = postParsePattern1.matcher(text);
        if(!mat.find()) {
            throw new ContentParseException("Could not parse thread (post info block regex failed)");
        }
        String name    = new String(mat.group(1));
        String trip    = (mat.group(2) != null) ? new String(mat.group(2)) : null;
        String capcode = (mat.group(3) != null) ? new String(mat.group(3)) : null;
        String uid     = mat.group(4);
        String country = (mat.group(5) != null) ? new String(mat.group(5)) : null;


         mat = oldCapPattern.matcher(text);
         if(mat.find()) {
             capcode =  new String(mat.group(1));
         }

         mat = datePattern.matcher(text);
         if(!mat.find()) {
             throw new ContentParseException("Could not parse thread (post timestamp regex failed)");
         }
         int dateUtc = Integer.parseInt(mat.group(1));

         mat = postParsePattern2.matcher(text);
         String link = null;
         boolean spoiler = false;
         String fileSize = null;
         int width = 0;
         int height = 0;
         String fileName = null;
         String md5b64 = null;
         int tHeight = 0;
         int tWidth = 0;
         if(mat.find()) {
             link     = (mat.group(2) != null) ? new String(mat.group(2)) : null;
             spoiler  = (mat.group(3) != null);
             fileSize = (mat.group(4) != null) ? new String(mat.group(4)) : null;
             width    = (mat.group(5) != null) ? Integer.parseInt(mat.group(5)) : 0;
             height   = (mat.group(6) != null) ? Integer.parseInt(mat.group(6)) : 0;
             fileName = (mat.group(7) == null) ?
                           ((mat.group(1) != null) ? new String(mat.group(1)) : null) :
                           new String(mat.group(7));
             md5b64   = (mat.group(8) != null) ? new String(mat.group(8)) : null;
             tHeight  = (mat.group(9) != null) ? Integer.parseInt(mat.group(9)) : 0;
             tWidth   = (mat.group(10) != null) ? Integer.parseInt(mat.group(10)) : 0;
         }

         mat = commentPattern.matcher(text);
         if(!mat.find()) {
             throw new ContentParseException("Could not parse thread (post comment regex failed)");
         }
         String comment  = new String(mat.group(1));

         boolean sticky = false;
         mat = stickyPattern.matcher(text);
         if(mat.find()) sticky  = true;

         boolean omitted = false;
         mat = omittedPattern.matcher(text);
         if(mat.find()) omitted  = true;

        return this.newYotsubaPost(link, null, spoiler, fileSize, width,
                height, fileName, tWidth, tHeight, md5b64, num, title, email,
                name, trip, capcode, dateUtc, sticky, comment, omitted, threadNum, uid, country);
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

        Matcher mat = postGetPattern.matcher(pageText);

        while(mat.find()) {
            String text = mat.group(1);
            String type = mat.group(2);

            if(type.equals("opContainer")) {
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
            if(type.equals("opContainer")) {
                if(t == null) {
                    t = this.parseThread(text);
                    t.setLastMod(newLastMod);
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

        return t;
    }
}
