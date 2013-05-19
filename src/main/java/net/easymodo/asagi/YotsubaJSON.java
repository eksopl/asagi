package net.easymodo.asagi;

import com.google.gson.*;
import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentParseException;
import net.easymodo.asagi.model.MediaPost;
import net.easymodo.asagi.model.Page;
import net.easymodo.asagi.model.Post;
import net.easymodo.asagi.model.Topic;
import net.easymodo.asagi.model.yotsuba.PageJson;
import net.easymodo.asagi.model.yotsuba.PostJson;
import net.easymodo.asagi.model.yotsuba.TopicJson;
import net.easymodo.asagi.model.yotsuba.TopicListJson;
import net.easymodo.asagi.settings.BoardSettings;
import org.apache.http.annotation.ThreadSafe;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ThreadSafe
public class YotsubaJSON extends WWW {
    private static final Gson GSON = new GsonBuilder().
            registerTypeAdapter(boolean.class, new BooleanTypeConverter()).
            setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private static final Pattern exifPattern;
    private static final Pattern exifDataPattern;

    static {
        String exifPatString = "<table \\s class=\"exif\"[^>]*>(.*)</table>";
        String exifDataPatString = "<tr><td>(.*?)</td><td>(.*?)</td></tr>";

        exifPattern = Pattern.compile(exifPatString, Pattern.COMMENTS | Pattern.DOTALL);
        exifDataPattern = Pattern.compile(exifDataPatString, Pattern.COMMENTS | Pattern.DOTALL);
    }

    private final Map<String,String> boardLinks;

    public YotsubaJSON(String boardName, BoardSettings settings) {
        boardLinks = YotsubaJSON.getBoardLinks(boardName);
        this.throttleAPI = settings.getThrottleAPI();
        this.throttleURL = settings.getThrottleURL();
        this.throttleMillisec = settings.getThrottleMillisec();
    }

    private static Map<String,String> getBoardLinks(String boardName) {
        Map<String,String> boardInfo = new HashMap<String,String>();
        boardInfo.put("link", "http://api.4chan.org/" + boardName);
        boardInfo.put("html", "http://boards.4chan.org/" + boardName + "/");
        boardInfo.put("imgLink", "http://images.4chan.org/" + boardName);
        boardInfo.put("previewLink", "http://0.thumbs.4chan.org/" + boardName);
        return Collections.unmodifiableMap(boardInfo);
    }


    private static class BooleanTypeConverter implements JsonSerializer<Boolean>, JsonDeserializer<Boolean> {
        @Override
        public JsonElement serialize(Boolean src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(src ? 1 : 0);
        }

        @Override
        public Boolean deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                return (json.getAsInt() != 0);
            } catch(ClassCastException e) {
                // fourchan api can't make up its mind about this
                return json.getAsBoolean();
            }
        }
    }

    @Override
    public InputStream getMediaPreview(MediaPost h) throws ContentGetException {
        if(h.getPreview() == null)
            return null;

    return this.wget(this.boardLinks.get("previewLink") + "/thumb/"
                    + h.getPreview());
    }

    @Override
    public InputStream getMedia(MediaPost h) throws ContentGetException {
        if(h.getMedia() == null)
            return null;

        return this.wget(this.boardLinks.get("imgLink") + "/src/" + h.getMedia());
    }

    private String linkPage(int pageNum) {
        return this.boardLinks.get("link") + "/" + pageNum + ".json";
    }

    private String linkThread(int thread) {
        if(thread != 0) {
            return this.boardLinks.get("link") + "/res/" + thread + ".json";
        } else {
            return this.linkPage(0);
        }
    }

    private String linkThreads() {
        return this.boardLinks.get("link") + "/" + "threads.json";
    }

    @Override
    public Page getPage(int pageNum, String lastMod) throws ContentGetException, ContentParseException {
        String[] wgetReply = this.wgetText(this.linkPage(pageNum), lastMod);
        String pageText = wgetReply[0];
        String newLastMod = wgetReply[1];

        PageJson pageJson;
        try {
            pageJson = GSON.fromJson(pageText, PageJson.class);
        } catch (JsonSyntaxException ex) {
            throw new ContentGetException("API returned invalid JSON", ex);
        }

        Page p = new Page(pageNum);
        Topic t = null;

        for (TopicJson tj : pageJson.getThreads()) {
            for (PostJson pj : tj.getPosts()) {
                if (pj.getResto() == 0) {
                    t = this.makeThreadFromJson(pj);
                    p.addThread(t);
                } else {
                    if (t != null) t.addPost(this.makePostFromJson(pj));
                }
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

        TopicJson topicJson;
        try {
            topicJson = GSON.fromJson(threadText, TopicJson.class);
        } catch (JsonSyntaxException ex) {
            throw new ContentGetException("API returned invalid JSON", ex);
        }

        for (PostJson pj : topicJson.getPosts()) {
            if (pj.getResto() == 0) {
                if (t == null) {
                    t = this.makeThreadFromJson(pj);
                    t.setLastMod(newLastMod);
                } else {
                    throw new ContentParseException("Two OP posts in thread in " + threadNum);
                }
            } else {
                if (t != null) {
                    t.addPost(this.makePostFromJson(pj));
                } else {
                    throw new ContentParseException("Thread without OP post in " + threadNum);
                }
            }
        }

        return t;
    }

    public Page getAllThreads(String lastMod) throws ContentGetException {
        String[] wgetReply = this.wgetText(this.linkThreads(), lastMod);
        String threadsText = wgetReply[0];
        String newLastMod = wgetReply[1];

        Page threadList = new Page(-1);
        threadList.setLastMod(newLastMod);

        TopicListJson.Page[] topicsJson;
        try {
            topicsJson = GSON.fromJson(threadsText, TopicListJson.Page[].class);
        } catch (JsonSyntaxException ex) {
            throw new ContentGetException("API returned invalid JSON", ex);
        }

        for (TopicListJson.Page page : topicsJson) {
            for (TopicListJson.Topic topic : page.getThreads()) {
                Topic t = new Topic(topic.getNo(), 0, 0);
                t.setLastModTimestamp(topic.getLastModified());
                t.setLastPage(page.getPage());
                threadList.addThread(t);
            }
        }

        return threadList;
    }

    private Post makePostFromJson(PostJson pj) throws ContentParseException {
        if(pj.getNo() == 0) {
            throw new ContentParseException("Could not parse post (post num missing and could not be zero)");
        }
        if(pj.getTime() == 0) {
            throw new ContentParseException("Could not parse post (post timestamp missing and could not be zero)");
        }

        Post p = new Post();

        if(pj.getFilename() != null) {
            p.setMediaFilename(pj.getFilename() + pj.getExt());
            p.setMediaOrig(pj.getTim() + pj.getExt());
            p.setPreviewOrig(pj.getTim() + "s.jpg");
        }

        String capcode = pj.getCapcode();
        if(capcode != null) capcode = capcode.substring(0, 1).toUpperCase();

        String posterHash = pj.getId();
        if(posterHash != null && posterHash.equals("Developer")) posterHash = "Dev";

        String posterCountry = pj.getCountry();
        if(posterCountry != null && (posterCountry.equals("XX") || posterCountry.equals("A1"))) posterCountry = null;

        p.setType(pj.getExt());
        p.setMediaHash(pj.getMd5());
        p.setMediaSize(pj.getFsize());
        p.setMediaW(pj.getW());
        p.setMediaH(pj.getH());
        p.setPreviewW(pj.getTnW());
        p.setPreviewH(pj.getTnH());
        p.setNum(pj.getNo());
        p.setThreadNum(pj.getResto() == 0 ? pj.getNo() : pj.getResto());
        p.setOp(pj.getResto() == 0);
        p.setTitle(this.cleanSimple(pj.getSub()));
        p.setEmail(pj.getEmail());
        p.setName(this.cleanSimple(pj.getName()));
        p.setTrip(pj.getTrip());
        p.setDate(DateUtils.adjustTimestampEpoch(pj.getTime(), DateUtils.NYC_TIMEZONE));
        p.setComment(this.doClean(pj.getCom()));
        p.setSpoiler(pj.isSpoiler());
        p.setDeleted(false);
        p.setSticky(pj.isSticky());
        p.setCapcode(capcode);
        p.setPosterHash(posterHash);
        p.setPosterCountry(posterCountry);
        p.setExif(this.cleanSimple(this.parseExif(pj.getCom())));

        return p;
    }

    private Topic makeThreadFromJson(PostJson pj) throws ContentParseException {
        if(pj.getNo() == 0) {
            throw new ContentParseException("Could not parse thread (thread post num missing and could not be zero)");
        }

        Topic t = new Topic(pj.getNo(), pj.getOmittedPosts(), pj.getOmittedImages());

        t.addPost(this.makePostFromJson(pj));
        return t;
    }

    public String cleanSimple(String text) {
        return super.doClean(text);
    }

    public String doClean(String text) {
        if(text == null) return null;

        // SOPA spoilers
        //text = text.replaceAll("<span class=\"spoiler\"[^>]*>(.*?)</spoiler>(</span>)?", "$1");

        // Admin-Mod-Dev quotelinks
        text = text.replaceAll("<span class=\"capcodeReplies\"><span style=\"font-size: smaller;\"><span style=\"font-weight: bold;\">(?:Administrator|Moderator|Developer) Repl(?:y|ies):</span>.*?</span><br></span>", "");
        // Non-public tags
        text = text.replaceAll("\\[(banned|moot)]", "[$1:lit]");
        // Comment too long, also EXIF tag toggle
        text = text.replaceAll("<span class=\"abbr\">.*?</span>", "");
        // EXIF data
        text = text.replaceAll("<table class=\"exif\"[^>]*>.*?</table>", "");
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
        // Old Spoilers (start)
        text = text.replaceAll("<span class=\"spoiler\"[^>]*>", "[spoiler]");
        // Old Spoilers (end)
        text = text.replaceAll("</span>", "[/spoiler]");
        // Spoilers (start)
        text = text.replaceAll("<s>", "[spoiler]");
        // Spoilers (end)
        text = text.replaceAll("</s>", "[/spoiler]");
        // Newlines
        text = text.replaceAll("<br\\s*/?>", "\n");
        // WBR
        text = text.replaceAll("<wbr>", "");

        // empty after EXIF stripped
        if(text == "") return null;

        return this.cleanSimple(text);
    }

    public String parseExif(String text) {
        if(text == null) return null;

        Matcher exif = exifPattern.matcher(text);

        if(exif.find()) {
            String data = exif.group(1);
            data = data.replaceAll("<tr><td colspan=\"2\"></td></tr><tr>", "");

            try {
                JSONObject exifJson = new JSONObject();
                Matcher exifData = exifDataPattern.matcher(data);

                while(exifData.find()) {
                    String key = exifData.group(1);
                    String val = exifData.group(2);

                    exifJson.put(key, val);
                }

                if(exifJson.length() > 0)
                    return exifJson.toString();
            } catch(JSONException e) {
                // nothing
            }
        }

        return null;
    }
}
