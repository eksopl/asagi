package net.easymodo.asagi;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.*;
import net.easymodo.asagi.model.MediaPost;
import net.easymodo.asagi.model.Page;
import net.easymodo.asagi.model.Post;
import net.easymodo.asagi.model.Topic;
import net.easymodo.asagi.settings.BoardSettings;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.apache.http.annotation.ThreadSafe;

import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentParseException;
import net.easymodo.asagi.model.yotsuba.*;

@ThreadSafe
public class YotsubaJSON extends Yotsuba {
    public YotsubaJSON(String boardName, BoardSettings settings) {
        boardLinks = YotsubaJSON.getBoardLinks(boardName);
        this.throttleAPI = settings.getThrottleAPI();
        this.throttleURL = settings.getThrottleURL();
        this.throttleMillisec = settings.getThrottleMillisec();
    }

    private static Map<String,String> getBoardLinks(String boardName) {
        Map<String,String> boardInfo = new HashMap<String,String>();
        boardInfo.put("link", "http://a.4cdn.org/" + boardName);
        boardInfo.put("html", "http://boards.4chan.org/" + boardName + "/");
        boardInfo.put("imageLink", "http://i.4cdn.org/" + boardName);
        boardInfo.put("thumbLink", "http://1.t.4cdn.org/" + boardName);
        return Collections.unmodifiableMap(boardInfo);
    }

    private String linkPage(int pageNum) {
        return this.boardLinks.get("link") + "/" + pageNum + ".json";
    }

    private String linkThread(int thread) {
        if (thread != 0) {
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
            throw new ContentGetException("API returned invalid JSON", ex.getCause());
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
            throw new ContentGetException("API returned invalid JSON", ex.getCause());
        }

        if (topicJson == null) {
            throw new ContentParseException("topicJSON returned null for " + threadNum);
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

    public Page getAllThreads(String lastMod) throws ContentGetException, ContentParseException {
        String[] wgetReply = this.wgetText(this.linkThreads(), lastMod);
        String threadsText = wgetReply[0];
        String newLastMod = wgetReply[1];

        Page threadList = new Page(-1);
        threadList.setLastMod(newLastMod);

        TopicListJson.Page[] topicsJson;
        try {
            topicsJson = GSON.fromJson(threadsText, TopicListJson.Page[].class);
        } catch (JsonSyntaxException ex) {
            throw new ContentGetException("API returned invalid JSON", ex.getCause());
        }

        if (topicsJson == null) {
            throw new ContentParseException("topicsJSON returned null");
        }

        for (TopicListJson.Page page : topicsJson) {
            for (TopicListJson.Topic topic : page.getThreads()) {
                Topic t = new Topic(topic.getNo(), 0, 0);
                t.setLastModTimestamp(topic.getLastModified() > Integer.MAX_VALUE ? 0 : (int) topic.getLastModified());
                t.setLastPage(page.getPage());
                threadList.addThread(t);
            }
        }

        return threadList;
    }

    private Topic makeThreadFromJson(PostJson pj) throws ContentParseException {
        if (pj.getNo() == 0) {
            throw new ContentParseException("Could not parse thread (thread post num missing and could not be zero)");
        }

        Topic t = new Topic(pj.getNo(), pj.getOmittedPosts(), pj.getOmittedImages());

        t.addPost(this.makePostFromJson(pj));
        return t;
    }

    private Post makePostFromJson(PostJson pj) throws ContentParseException {
        if (pj.getNo() == 0) {
            throw new ContentParseException("Could not parse post (post num missing and could not be zero)");
        }
        if (pj.getTime() == 0) {
            throw new ContentParseException("Could not parse post (post timestamp missing and could not be zero)");
        }

        Post p = new Post();

        if (pj.getFilename() != null) {
            p.setMediaFilename(pj.getFilename() + pj.getExt());
            p.setMediaOrig(pj.getTim() + pj.getExt());
            p.setPreviewOrig(pj.getTim() + "s.jpg");
        }

        String capcode = pj.getCapcode();
        if (capcode != null) capcode = capcode.substring(0, 1).toUpperCase();

        String posterHash = pj.getId();
        if (posterHash != null && posterHash.equals("Developer")) posterHash = "Dev";

        String posterCountry = pj.getCountry();
        if (posterCountry != null && (posterCountry.equals("XX") || posterCountry.equals("A1"))) posterCountry = null;

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
        p.setDate(this.parseDate(pj.getTime()));
        p.setDateExpired(0);
        p.setComment(this.doClean(pj.getCom()));
        p.setSpoiler(pj.isSpoiler());
        p.setDeleted(false);
        p.setSticky(pj.isSticky());
        p.setClosed(pj.isClosed());
        p.setCapcode(capcode);
        p.setPosterHash(posterHash);
        p.setPosterCountry(posterCountry);
        p.setExif(this.cleanSimple(this.parseMeta(pj.getCom())));

        return p;
    }
}
