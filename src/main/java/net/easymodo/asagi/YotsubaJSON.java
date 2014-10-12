package net.easymodo.asagi;

import com.google.gson.JsonSyntaxException;
import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentParseException;
import net.easymodo.asagi.model.Page;
import net.easymodo.asagi.model.Post;
import net.easymodo.asagi.model.Topic;
import net.easymodo.asagi.model.yotsuba.PageJson;
import net.easymodo.asagi.model.yotsuba.PostJson;
import net.easymodo.asagi.model.yotsuba.TopicJson;
import net.easymodo.asagi.model.yotsuba.TopicListJson;
import net.easymodo.asagi.settings.BoardSettings;
import net.easymodo.asagi.settings.SiteSettings;
import org.apache.http.annotation.ThreadSafe;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ThreadSafe
public class YotsubaJSON extends YotsubaAbstract {

    public YotsubaJSON(String boardName, SiteSettings siteSettings, BoardSettings boardSettings) {
        super(YotsubaJSON.getBoardLinks(boardName, siteSettings));
        this.adjustTimestamp = boardSettings.getAdjustTimestamp();
        this.throttleAPI = boardSettings.getThrottleAPI();
        this.throttleURL = boardSettings.getThrottleURL();
        this.throttleMillisec = boardSettings.getThrottleMillisec();
    }

    private static Map<String,String> getBoardLinks(String boardName, SiteSettings siteSettings) {
        Map<String,String> boardInfo = new HashMap<String,String>();
        boardInfo.put("link", siteSettings.getLink() + "/" + boardName);
        boardInfo.put("imageLink", siteSettings.getImageLink() + "/" + boardName);
        boardInfo.put("thumbLink", siteSettings.getThumbLink() + "/" + boardName);
        return Collections.unmodifiableMap(boardInfo);
    }

    private String linkPage(int pageNum) {
        return this.boardLinks.get("link") + "/" + pageNum + ".json";
    }

    private String linkThread(int thread) {
        if(thread != 0) {
            return this.boardLinks.get("link") + "/thread/" + thread + ".json";
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
        } catch(JsonSyntaxException ex) {
            throw new ContentGetException("API returned invalid JSON", ex);
        }

        Page p = new Page(pageNum);
        Topic t = null;

        for(TopicJson tj : pageJson.getThreads()) {
            for(PostJson pj : tj.getPosts()) {
                if(pj.getResto() == 0) {
                    t = this.makeThreadFromJson(pj);
                    p.addThread(t);
                } else {
                    if(t != null) t.addPost(this.makePostFromJson(pj));
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
        } catch(JsonSyntaxException ex) {
            throw new ContentGetException("API returned invalid JSON", ex);
        }

        if(topicJson == null) {
            throw new ContentParseException("API returned empty JSON in " + threadNum);
        }

        for(PostJson pj : topicJson.getPosts()) {
            if(pj.getResto() == 0) {
                if(t == null) {
                    t = this.makeThreadFromJson(pj);
                    t.setLastMod(newLastMod);
                } else {
                    throw new ContentParseException("Two OP posts in thread in " + threadNum);
                }
            } else {
                if(t != null) {
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
        } catch(JsonSyntaxException ex) {
            throw new ContentGetException("API returned invalid JSON", ex);
        }

        if(topicsJson == null) {
            throw new ContentParseException("API returned empty JSON in threads.json");
        }

        for(TopicListJson.Page page : topicsJson) {
            for(TopicListJson.Topic topic : page.getThreads()) {
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
        p.setDate(this.adjustTimestamp ? DateUtils.adjustTimestampEpoch(pj.getTime(), DateUtils.NYC_TIMEZONE) : pj.getTime());
        p.setComment(this.doClean(pj.getCom()));
        p.setSpoiler(pj.isSpoiler());
        p.setDeleted(false);
        p.setSticky(pj.isSticky());
        p.setClosed(pj.isClosed() && !pj.isArchived());
        p.setArchived(pj.isArchived());
        p.setCapcode(capcode);
        p.setPosterHash(posterHash);
        p.setPosterCountry(posterCountry);
        p.setExif(this.cleanSimple(this.parseMeta(pj.getCom())));

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
}
