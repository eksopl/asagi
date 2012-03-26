package net.easymodo.asagi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.ThreadSafe;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

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
        
        String threadParsePatternString =
            "(?: <a \\s href=\"([^\"]*/src/(\\d+\\.\\w+))\"[^>]*>[^<]*</a> \\s*"+
            "       \\- \\s* \\((Spoiler \\s Image, \\s)?([\\d\\sGMKB\\.]+)\\, \\s (\\d+)x(\\d+)(?:, \\s* <span \\s title=\"([^\"]*)\">[^<]*</span>)?\\) \\s*"+
            "       </span> \\s* "+
            "       (?:"+
            "           <br>\\s*<a[^>]*><img \\s+ src=\\S* \\s+ border=\\S* \\s+ align=\\S* \\s+ (?:width=\"?(\\d+)\"? \\s height=\"?(\\d+)\"?)? [^>]*? md5=\"?([\\w\\d\\=\\+\\/]+)\"? [^>]*? ></a> \\s*"+
            "           |"+
            "           <a[^>]*><span \\s class=\"tn_thread\"[^>]*>Thumbnail \\s unavailable</span></a>"+
            "       )"+
            "       |"+
            "       <img [^>]* alt=\"File \\s deleted\\.\" [^>]* > \\s*"+
            "   )?"+
            "   <a[^>]*></a> \\s*"+
            "   <input \\s type=checkbox \\s name=\"(\\d+)\"[^>]*><span \\s class=\"filetitle\">(?>(.*?)</span>) \\s*"+
            "   <span \\s class=\"postername\">(?:<span [^>]*>)?(?:<a \\s href=\"mailto:([^\"]*)\"[^>]*>)?([^<]*?)(?:</a>)?(?:</span>)?</span>"+
            "   (?: \\s* <span \\s class=\"postertrip\">(?:<span [^>]*>)?([a-zA-Z0-9\\.\\+/\\!]+)(?:</a>)?(?:</span>)?</span>)?"+
            "   (?: \\s* <span \\s class=\"commentpostername\"><span [^>]*>\\#\\# \\s (.?)[^<]*</span>(?:</a>)?</span>)?"+
            "   (?: \\s* <span \\s class=\"posteruid\">\\(ID: \\s (?: <span [^>]*>(.)[^)]* | ([^)]*))\\)</span>)?"+
            "   \\s* (?:<span \\s class=\"posttime\">)?([^>]*)(?:</span>)? \\s*"+
            "   <span[^>]*> (?> .*?</a>.*?</a>) \\s* (?:<img [^>]* alt=\"(sticky)\">)? (?> .*?</span>) \\s* "+
            "   <blockquote>(?>(.*?)(<span \\s class=\"abbr\">(?:.*?))?</blockquote>)"+
            "   (?:<span \\s class=\"oldpost\">[^<]*</span><br> \\s*)?"+
            "   (?:<span \\s class=\"omittedposts\">(\\d+).*?(\\d+)?.*?</span>)?";
        threadParsePattern = Pattern.compile(threadParsePatternString, Pattern.COMMENTS | Pattern.DOTALL);
        
        String postParsePatternString = 
            "<td \\s id=\"(\\d+)\"[^>]*> \\s*" +
            "<input[^>]*><span \\s class=\"replytitle\">(?>(.*?)</span>) \\s*"+
            "<span \\s class=\"commentpostername\">(?:<a \\s href=\"mailto:([^\"]*)\"[^>]*>)?(?:<span [^>]*>)?([^<]*?)(?:</span>)?(?:</a>)?</span>"+
            "(?: \\s* <span \\s class=\"postertrip\">(?:<span [^>]*>)?([a-zA-Z0-9\\.\\+/\\!]+)(?:</a>)?(?:</span>)?</span>)?"+
            "(?: \\s* <span \\s class=\"commentpostername\"><span [^>]*>\\#\\# \\s (.?)[^<]*</span>(?:</a>)?</span>)?"+
            "(?: \\s* <span \\s class=\"posteruid\">\\(ID: \\s (?: <span [^>]*>(.)[^)]* | ([^)]*))\\)</span>)?"+
            "\\s* (?:<span \\s class=\"posttime\">)?([^<]*)(?:</span>)? \\s*"+
            "(?>.*?</span>) \\s*"+
            "(?: <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; \\s*"+
            "    <span \\s class=\"filesize\">File [^>]*"+
            "    <a \\s href=\"([^\"]*/src/(\\d+\\.\\w+))\"[^>]*>[^<]*</a> \\s*"+
            "    \\- \\s* \\((Spoiler \\s Image,)?([\\d\\sGMKB\\.]+)\\, \\s (\\d+)x(\\d+)(?:, \\s* <span \\s title=\"([^\"]*)\">[^<]*</span>)?\\)"+
            "    </span> \\s*"+
            "    (?: <br>\\s*<a[^>]*><img \\s+ src=\\S* \\s+ border=\\S* \\s+ align=\\S* \\s+ "+
            "        (?:width=(\\d+) \\s height=(\\d+))? [^>]*? md5=\"?([\\w\\d\\=\\+\\/]+)\"? [^>]*? ></a> \\s*"+
            "        | <a[^>]*><span \\s class=\"tn_reply\"[^>]*>Thumbnail \\s unavailable</span></a> )"+
            "    | <br> \\s* <img [^>]* alt=\"File \\s deleted\\.\" [^>]* > \\s* )?"+
            "<blockquote>(?>(.*?)(<span \\s class=\"abbr\">(?:.*?))?</blockquote>)"+
            "</td></tr></table>";
        postParsePattern = Pattern.compile(postParsePatternString, Pattern.COMMENTS | Pattern.DOTALL);
        
        String threadGetPatternString = 
            "    ((?:" +
            "        (?:" +
            "            <span \\s class=\"filesize\">.*?" +
            "            |" +
            "            <img \\s src=\"[^\"]*\" \\s alt=\"File \\s deleted\\.\">.*?" +
            "        )?" +
            "        (<a \\s name=\"[^\"]*\"></a> \\s* <input [^>]*><span \\s class=\"filetitle\">)" +
            "        (?>.*?</blockquote>)" +
            "        (?:<span \\s class=\"oldpost\">[^<]*</span><br> \\s*)?" +
            "        (?:<span \\s class=\"omittedposts\">[^<]*</span>)?)" +
            "    |" +
            "    (?:<table><tr><td \\s nowrap \\s class=\"doubledash\">(?>.*?</blockquote></td></tr></table>)))";
            
        threadGetPattern = Pattern.compile(threadGetPatternString, Pattern.COMMENTS | Pattern.DOTALL);
        
        String postGetPatternString =
            "    ((?:" +
            "        (?:" +
            "            <span \\s class=\"filesize\">.*?" +
            "            |" +
            "            <img \\s src=\"[^\"]*\" \\s alt=\"File \\s deleted\\.\">.*?" +
            "        )?" +
            "        (<a \\s name=\"[^\"]*\"></a> \\s* <input [^>]*><span \\s class=\"filetitle\">)" +
            "        (?>.*?</blockquote>)" +
            "        (?:<span \\s class=\"oldpost\">[^<]*</span><br> \\s*)?" +
            "        (?:<span \\s class=\"omittedposts\">[^<]*</span>)?" +
            "    )" +
            "    |" +
            "    (?:<table><tr><td \\s nowrap \\s class=\"doubledash\">(?>.*?</blockquote></td></tr></table>)))";
            
        postGetPattern = Pattern.compile(postGetPatternString, Pattern.COMMENTS | Pattern.DOTALL);
    }
    
    private final Map<String,String> boardLinks;
    
    public Yotsuba(String boardName) {
        boardLinks = Yotsuba.getBoardLinks(boardName);
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
            Pattern.compile("(\\d+)/(\\d+)/(\\d+) \\(\\w+\\) (\\d+):(\\d+)(?:(\\d+))?", Pattern.COMMENTS);
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
            String comment, boolean omitted, int parent) throws ContentParseException 
    {
        String type = "";
        String media = null;
        String preview = null;
        String md5 = null;
        
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
                media = filename;
            } else {
                media = number + "." + type;
            }
            
            preview = number + "s.jpg";
          
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
        post.setMedia(media);
        post.setMediaHash(md5);
        post.setMediaFilename(mediaFilename);
        post.setMediaSize(mediaSize);
        post.setMediaW(width);
        post.setMediaH(height);
        post.setPreview(preview);
        post.setPreviewW(twidth);
        post.setPreviewH(theight);
        post.setNum(num);
        post.setParent(parent);
        post.setTitle(this.cleanSimple(title));
        post.setEmail(email);
        post.setName(this.cleanSimple(name));
        post.setTrip(trip);
        post.setDate(timeStamp);
        post.setComment(this.doClean(comment));
        post.setSpoiler(spoiler);
        post.setDeleted(false);
        post.setSticky(sticky);
        post.setCapcode(capcode);
        post.setOmitted(omitted);
        
        return post;
    }    
    
    @Override
    public byte[] getMediaPreview(Post h) throws ContentGetException {
        if(h.getPreview() == null)
            return null;
        
        byte[] data = this.wget(this.boardLinks.get("previewLink") + "/thumb/"
                + h.getPreview() + "?" + System.currentTimeMillis()).getContent();
        
        return data;
    }
    
    @Override
    public byte[] getMedia(Post h) throws ContentGetException {
        if(h.getLink() == null)
            return null;
        
        byte[] data = this.wget(h.getLink() + "?" + System.currentTimeMillis()).getContent();
        
        return data;
    }
    

    public Topic parseThread(String text) throws ContentParseException {
        Matcher mat = threadParsePattern.matcher(text);
        
        if(!mat.find()) {
            throw new ContentParseException("Could not parse thread (thread regex failed)");
        }
        
        String link = mat.group(1);
        String mediaFilename = mat.group(2);
        boolean spoiler = (mat.group(3) != null);
        String filesize = mat.group(4);
        int width = (mat.group(5) != null) ? Integer.parseInt(mat.group(5)) : 0;
        int height = (mat.group(6) != null) ? Integer.parseInt(mat.group(6)) : 0;
        String filename = mat.group(7);
        int tWidth = (mat.group(8) != null) ? Integer.parseInt(mat.group(8)) : 0;
        int tHeight = (mat.group(9) != null) ? Integer.parseInt(mat.group(9)): 0;
        String md5Base64 = mat.group(10);
        int num = Integer.parseInt(mat.group(11));
        String title = mat.group(12);
        String email = mat.group(13);
        String name = mat.group(14);
        String trip = mat.group(15);
        String capcode = (mat.group(16) == null) ? mat.group(17) : mat.group(16);
        //String uid = mat.group(18);
        String date = mat.group(19);
        boolean sticky = (mat.group(20) != null);
        String comment = mat.group(21);
        boolean omitted = (mat.group(22) != null);
        int omPosts = (mat.group(23) != null) ? Integer.parseInt(mat.group(23)) : 0;
        int omImages = (mat.group(24) != null) ? Integer.parseInt(mat.group(24)) : 0;
        
        Topic thread = new Topic(num, omPosts, omImages);
        Post op = this.newYotsubaPost(link, mediaFilename, spoiler, filesize, width, height, filename, tWidth, 
                tHeight, md5Base64, num, title, email, name, trip, capcode, date, sticky, comment, omitted, 0);
        thread.addPost(op);
        
        return thread;
    }
    
    public Post parsePost(String text, int parent) throws ContentParseException {
        Matcher mat = postParsePattern.matcher(text);
        
        if(!mat.find()) {
            throw new ContentParseException("Could not parse post (post regex failed)");
        }        
        int num = Integer.parseInt(mat.group(1));
        String title = mat.group(2);
        String email = mat.group(3);
        String name = mat.group(4);
        String trip = mat.group(5);
        String capcode = (mat.group(6) == null) ? mat.group(7) : mat.group(6);
        //String uid = mat.group(8);
        String date = mat.group(9);
        String link = mat.group(10);
        String media_filename = mat.group(11);
        boolean spoiler = (mat.group(12) != null);
        String filesize = mat.group(13);
        int width = (mat.group(14) != null) ? Integer.parseInt(mat.group(14)) : 0;
        int height = (mat.group(15) != null) ? Integer.parseInt(mat.group(15)) : 0;
        String filename = mat.group(16);
        int twidth = (mat.group(17) != null) ? Integer.parseInt(mat.group(17)) : 0;
        int theight =  (mat.group(18) != null) ? Integer.parseInt(mat.group(18)) : 0;
        String md5base64 = mat.group(19);
        String comment = mat.group(20);
        boolean omitted = (mat.group(21) != null);
        boolean sticky = false;
       
        Post post = this.newYotsubaPost(link, media_filename, spoiler, filesize, width, height, filename, twidth, 
                theight, md5base64, num, title, email, name, trip, capcode, date, sticky, comment, omitted, parent);
        
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
    
    public static Map<String,String> getBoardLinks(String boardName) {
        Map<String,String> boardInfo = new HashMap<String,String>();
        boardInfo.put("link", "http://boards.4chan.org/" + boardName);
        boardInfo.put("imgLink", "http://images.4chan.org/" + boardName);
        boardInfo.put("previewLink", "http://0.thumbs.4chan.org/" + boardName);
        boardInfo.put("html", "http://boards.4chan.org/" + boardName + "/");
        return Collections.unmodifiableMap(boardInfo);
    }
    
    /*
    public static void main(String[] args) throws IOException {
        Yotsuba yot = new Yotsuba("jp");
        java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        
        String text = new String();
        String newString = "";
        while((newString = stdin.readLine()) != null) {
            text += newString;
        }
        // System.out.println("your mom");
        yot.parsePost(text, 0);
        //yot.parseThread(text);
    }
    */


}
