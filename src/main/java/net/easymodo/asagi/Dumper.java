package net.easymodo.asagi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;

import net.easymodo.asagi.settings.*;
import net.easymodo.asagi.exception.*;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class Dumper {
    private final String boardName;
    private final int debugLevel;
    
    private static final String SETTINGS_FILE = "./asagi.json";

    protected final int pageLimbo;
    protected final boolean fullMedia;
    protected final Local topicLocalBoard;
    protected final Local mediaLocalBoard;
    protected final Board sourceBoard;
    protected final ConcurrentHashMap<Integer,Topic> topics;
    protected final BlockingQueue<MediaPost> mediaPreviewUpdates;
    protected final BlockingQueue<MediaPost> mediaUpdates;
    protected final BlockingQueue<Topic> topicUpdates;
    protected final BlockingQueue<Integer> deletedPosts;
    protected final BlockingQueue<Integer> newTopics;

    public static final int ERROR = 1;
    public static final int WARN  = 2;
    public static final int TALK  = 3;
    public static final int INFO  = 4;
    
    public Dumper(String boardName, Local topicLocalBoard, Local mediaLocalBoard,
            Board sourceBoard, boolean fullMedia) {
        this.boardName = boardName;
        this.sourceBoard = sourceBoard;
        this.topicLocalBoard = topicLocalBoard;
        this.mediaLocalBoard = mediaLocalBoard;
        this.topics = new ConcurrentHashMap<Integer,Topic>();
        this.mediaPreviewUpdates = new LinkedBlockingQueue<MediaPost>();
        this.mediaUpdates = new LinkedBlockingQueue<MediaPost>();
        this.topicUpdates = new LinkedBlockingQueue<Topic>();
        this.deletedPosts = new LinkedBlockingQueue<Integer>();
        this.newTopics = new LinkedBlockingQueue<Integer>();
        this.fullMedia = fullMedia;
        this.debugLevel = TALK;
        this.pageLimbo = 13;
    }
    
    public void debug(int level, String ... args){
        String output = "[" +
                        boardName + " " +
                        topics.size() + " " +
                        newTopics.size() + " " +
                        topicUpdates.size() + " " +
                        mediaUpdates.size() + " " +
                        mediaPreviewUpdates.size() +
                        "] ";
                
        for(String arg : args)
            output = output.concat(arg);
        
        if (level <= debugLevel)
            System.out.println(output);
    }
    
   
    private boolean findDeleted(Topic oldTopic, Topic newTopic, boolean markDeleted) {
        boolean changed = false;
        
        if(oldTopic == null) return changed;
       
        List<Integer> oldPosts = new ArrayList<Integer>(oldTopic.getAllPosts());
        
        // Get the posts from the old thread not marked as deleted.
        // We have to do this, otherwise our math for omitted posts will be
        // wrong.
        /* for(Iterator<Integer> it = oldPosts.iterator(); it.hasNext();) {
            if(it.next().isDeleted())
                it.remove();
        }*/
                
        if(oldTopic.getAllPosts().isEmpty()) return changed;
        
        for(int i = 0; i < oldPosts.size(); i++) {
            int num = oldPosts.get(i);
            if(!newTopic.findPost(num)) {
                // We just found a possibly deleted post, but we haven't been
                // instructed to actually mark deleted posts.
                if(!markDeleted) return true;
                
                changed = true;
                oldTopic.getAllPosts().remove(num);
                deletedPosts.add(num);
                debug(TALK, num + " (post): deleted");
            }
            if(i == 0) i = newTopic.getOmPosts();
        }

        return changed;
    }
   
    
    public class ThumbFetcher implements Runnable {        
        @Override
        public void run() {
            while(true) {
                MediaPost mediaPrevPost = null;
                
                try {
                mediaPrevPost = mediaPreviewUpdates.take();
                } catch(InterruptedException e) { } 
                
                try {
                    mediaLocalBoard.insertMediaPreview(mediaPrevPost, sourceBoard);
				} catch(ContentGetException e) {
                    debug(ERROR, "Couldn't fetch preview of post " + 
                            mediaPrevPost.getNum() + ": " + e.getMessage());
                    continue;
                } catch(ContentStoreException e) {
                    debug(ERROR, "Couldn't save preview of post " + 
                            mediaPrevPost.getNum() + ": " + e.getMessage());
                }
            }
        }
    }
    
    public class MediaFetcher implements Runnable {    	
        @Override
        public void run() {
            while(true) {
                MediaPost mediaPost = null;
                
                try {
                    mediaPost = mediaUpdates.take();
                } catch(InterruptedException e) { }  
                
                try {
                    mediaLocalBoard.insertMedia(mediaPost, sourceBoard);
                } catch(ContentGetException e) {
                    debug(ERROR, "Couldn't fetch media of post " + 
                            mediaPost.getNum() + ": " + e.getMessage());
                    continue;
                } catch(ContentStoreException e) {
                    debug(ERROR, "Couldn't save media of post " + 
                            mediaPost.getNum() + ": " + e.getMessage());
                    continue;
                }
            } 
        }
    }
    
    public class TopicInserter implements Runnable {        
        @Override
        public void run() {
            while(true) {
                Topic newTopic = null;
                try {
                     newTopic = topicUpdates.take();
                } catch(InterruptedException e) { }
                
                newTopic.lock.writeLock().lock();
                
                try {
                    topicLocalBoard.insert(newTopic);
                } catch(ContentStoreException e) {
                    debug(ERROR, "Couldn't insert topic " + newTopic.getNum() +
                            ": " + e.getMessage());
                    
                    newTopic.lock.writeLock().unlock();
                    continue;
                }
                
                List<Post> posts = newTopic.getPosts();
                if(posts == null) {
                    newTopic.lock.writeLock().unlock();
                    return;
                }
                
                for(Post post : posts) {
                    try {
                        MediaPost mediaPost = new MediaPost(post.getNum(), post.getParent() == 0, 
                                post.getPreview(), post.getOrigFilename(), post.getMediaHash());

                        if(post.getPreview() != null) mediaPreviewUpdates.put(mediaPost);
                        if(post.getOrigFilename() != null && fullMedia) mediaUpdates.put(mediaPost);
                    } catch(InterruptedException e) { }
                }
                newTopic.purgePosts();
                newTopic.lock.writeLock().unlock();
            }
        } 
    }
    
    public class PostDeleter implements Runnable {     
        @Override
        public void run() {
            while(true) {
                int deletedPost = 0;
                
                try {
                    deletedPost = deletedPosts.take();
                } catch(InterruptedException e) { }  
                
                try {
                    topicLocalBoard.markDeleted(deletedPost);
                } catch(ContentStoreException e) {
                    debug(ERROR, "Couldn't update deleted status of post " + 
                            deletedPost + ": " + e.getMessage());
                    continue;
                }
            } 
        }
    }
    
    
    public class PageScanner implements Runnable {
        private final List<Integer> pageNos;
        private final long wait;
        private String[] pagesLastMods;
        
        PageScanner(long wait, List<Integer> pageNos) {
            this.wait = wait * 1000;
            this.pageNos = pageNos;
            this.pagesLastMods = new String[Collections.max(pageNos) + 1];
        }
        
        @Override
        public void run() {
            while(true) {
                long now = DateTime.now().getMillis();
                for(int pageNo : pageNos) {
                    String lastMod = pagesLastMods[pageNo];
                    Page page;
                    
                    long startTime = DateTime.now().getMillis();
                    
                    try {
                        page = sourceBoard.content(Request.page(pageNo, lastMod));
                    } catch(HttpGetException e) {
                        if(e.getHttpStatus() == 304)
                            debug(TALK, (pageNo == 0 ? "front page" : "page " + pageNo)
                                    + ": wasn't modified");
                        continue;
                    } catch(ContentGetException e) {
                        debug(WARN, pageNo + e.getMessage());
                        continue;
                    } catch(ContentParseException e) {
                        debug(ERROR, pageNo + e.getMessage());
                        continue;
                    }
                    
                    if(page == null) {
                        debug(WARN, (pageNo == 0 ? "front page" : "page " + pageNo)
                                + "had no threads");
                        continue;
                    }
                    
                    pagesLastMods[pageNo] = page.getLastMod();
                    
                    // debug(TALK, "got page " + pageNo);
                    
                    for(Topic newTopic : page.getThreads()) {
                        int num = newTopic.getNum();
                        
                        // If we never saw this topic, then we'll put it in the
                        // new topics queue, a TopicFetcher will take care of
                        // it.
                        if(!topics.containsKey(num)) { 
                            try { newTopics.put(num); } catch(InterruptedException e) {}
                            continue;
                        }
                        
                        // Otherwise we'll go ahead and try to update the
                        // topic with the posts we have from this index page.
                        Topic fullTopic = topics.get(num);
                        
                        // Perhaps we had extremely bad luck and a TopicFetcher
                        // just saw this thread 404 and got rid of it before we
                        // could grab it? Oh well.
                        if(fullTopic == null) continue;
                        
                        // Try to get the write lock for this topic.
                        fullTopic.lock.writeLock().lock();
                        
                        // Oh, forget it. A ThreadFetcher beat us to this one.
                        // (Or another PageScanner)
                        if(fullTopic.getLastHit() > startTime) { 
                            fullTopic.lock.writeLock().unlock();
                            continue; 
                        }
                        
                        // Update the last page where we saw this topic
                        fullTopic.setLastPage(pageNo);

                        int oldPosts = 0;
                        int newPosts = 0;
                        boolean mustRefresh = false;
                        
                        // We check for any posts that got deleted
                        // We have the write lock, so TopicFetchers can suck it.
                        if(findDeleted(fullTopic, newTopic, false)) {
                            // Pages cannot be trusted to not have posts missing.
                            // We need to force a refresh, it can't be helped.
                            // See GH-11. Sigh.
                            mustRefresh = true;
                            newPosts++;
                         }
                        
                        for(Post newPost : newTopic.getPosts()) {                            
                            // This post was already in topics map. Next post
                            if(fullTopic.findPost(newPost.getNum())) { oldPosts++; continue; }
                            
                            // Looks like it's new
                            fullTopic.addPost(newPost); newPosts++;
                            
                            // Comment too long. Click here to view the full text.
                            // This means we have to refresh the full thread
                            if(newPost.isOmitted()) mustRefresh = true;
                            
                            // We have to refresh to get the image filename, sadly
                            if(newPost.getMedia() != null) mustRefresh = true;
                        }
                        
                        // Update the time we last hit this thread
                        fullTopic.setLastHit(startTime);
                        
                        fullTopic.lock.writeLock().unlock();
                                                
                        //  No new posts
                        if(oldPosts != 0 && newPosts == 0) continue;
                        
                        debug(TALK, (pageNo == 0 ? "front page" : "page " + pageNo) + " update");
                        
                        newTopic.lock.readLock().lock();
                        // Push new posts/images/thumbs to their queues
                        topicUpdates.add(newTopic);
                        newTopic.lock.readLock().unlock();
                        
                        // And send the thread to the new threads queue if we were
                        // forced to refresh earlier or if the only old post we
                        // saw was the OP, as that means we're missing posts from inside the thread.
                        if(mustRefresh || oldPosts < 2) {
                            debug(TALK, num + ": must refresh");
                            try { newTopics.put(num); } catch(InterruptedException e) {}
                        }
                    }
                }
                
                long left = this.wait - (DateTime.now().getMillis() - now);
                if(left > 0) {
                    try { Thread.sleep(left); } catch(InterruptedException e) { }
                }
            }            
        }
    }
    
    public class TopicFetcher implements Runnable {        
        @Override
        public void run() {
            while(true) {
                int newTopic = 0;
                try {
                    newTopic = newTopics.take(); 
               } catch(InterruptedException e) { }
               
               String lastMod = null;
               
               Topic oldTopic = topics.get(newTopic);

               // If we already saw this topic before, acquire its read lock,
               // so we can get the last modification date (for I-M-S header)
               if(oldTopic != null) {
                   oldTopic.lock.readLock().lock();
                   lastMod = oldTopic.getLastMod();
                   oldTopic.lock.readLock().unlock();
               }
               
               long startTime = DateTime.now().getMillis();

               // Let's go get our updated topic, from the topic page
               Topic topic;
               try {
                   topic = sourceBoard.content(Request.thread(newTopic, lastMod));
               } catch(HttpGetException e) {
                   if(e.getHttpStatus() == 304) {
                       // If the old topic exists, update its lastHit timestamp
                       // The old topic should always exist at this point.
                       if(oldTopic != null) {
                           oldTopic.lock.writeLock().lock();
                           oldTopic.setLastHit(DateTime.now().getMillis());
                           oldTopic.setBusy(false);
                           oldTopic.lock.writeLock().unlock();
                       }
                       debug(TALK, newTopic + ": wasn't modified");
                       continue;
                   } else if(e.getHttpStatus() == 404) {
                       if(oldTopic != null) {
                           oldTopic.lock.writeLock().lock();
                           
                           // If we found the topic before the page limbo
                           // threshold, then it was forcefully deleted
                           if(oldTopic.getLastPage() < pageLimbo) {
                               if(oldTopic.getAllPosts().size() > 1) {
                                   int op = oldTopic.getAllPosts().iterator().next();
                                   try {
                                       deletedPosts.put(op);
                                   } catch(InterruptedException e1) { }
                               }
                               topicUpdates.add(oldTopic);
                               debug(TALK, newTopic + ": deleted (last seen on page " + oldTopic.getLastPage() + ")");
                           }
                           
                           // Goodbye, old topic.
                           topics.remove(newTopic);
                           oldTopic.lock.writeLock().unlock();
                           oldTopic = null;
                       }
                       continue;
                   } else {
                       // We got some funky error
                       debug(WARN, newTopic + ": got HTTP status" + e.getHttpStatus());
                       continue;
                   }
               } catch(ContentGetException e) {
                   // We got an even funkier, non-HTTP error
                   debug(WARN, newTopic + ": error: " + e.getMessage());
                   continue;
               } catch(ContentParseException e) {
                   debug(ERROR, newTopic + e.getMessage());
                   continue;
               }
               
               if(topic == null) { 
                   debug(WARN, newTopic + ": topic has no posts");
                   continue; 
               }
               
               topic.setLastHit(startTime);
               
               // We're about to make our rebuilt topic public
               topic.lock.readLock().lock();
               
               if(oldTopic != null) {
                   oldTopic.lock.writeLock().lock();
                   
                   // Beaten to the punch
                   if(oldTopic.getLastHit() > startTime) { 
                       oldTopic.lock.writeLock().unlock();
                       
                       // Throw this away now.
                       topic.lock.readLock().unlock();
                       topic = null;
                       continue;
                   }
                   
                   // Get the deleted posts from the old topic
                   // Update their status in the DB, too.
                   findDeleted(oldTopic, topic, true);
                   
                   // We don't really know at which page this thread is, so let
                   // us keep the last page a PageScanner saw this thread at.
                   topic.setLastPage(oldTopic.getLastPage());
                   
                   // Goodbye, old topic.
                   topics.put(newTopic, topic);
                   oldTopic.lock.writeLock().unlock();
               } else {
                   // Hello, new topic!
                   topics.put(newTopic, topic);
               }
               
               // We have a read lock, update it
               topicUpdates.add(topic);
               topic.lock.readLock().unlock();
               
               debug(TALK, newTopic + ": " + (oldTopic != null ? "updated" : "new"));
               oldTopic = null;
           }
        }
    }
    
    public class TopicRebuilder implements Runnable {
        private final long threadRefreshRate;
        
        public TopicRebuilder(int threadRefreshRate) {
            this.threadRefreshRate = threadRefreshRate * 60L * 1000L;
        }
        
        
        @Override
        public void run() {
            while(true) {
                for(Topic topic : topics.values()) {
                    try {
                        if(!topic.lock.writeLock().tryLock(1, TimeUnit.SECONDS)) continue;
                    } catch(InterruptedException e) { }
                    if(topic.isBusy()) { topic.lock.writeLock().unlock(); continue; }
                    
                    long deltaLastHit = DateTime.now().getMillis() - topic.getLastHit();
                    //debug(TALK, "deltaLastHit for " + topic.getNum() + ": " + deltaLastHit);
                    
                    if(deltaLastHit <= threadRefreshRate) { topic.lock.writeLock().unlock();  continue; }
                    
                    topic.setBusy(true);
                    try {
                        newTopics.put(topic.getNum());
                    } catch(InterruptedException e) { }
                    
                    topic.lock.writeLock().unlock();
                }
                try {
                    Thread.sleep(1000);
                } catch(InterruptedException e) { }
            }
        }
    }

    
    private static void spawnBoard(String boardName, Settings settings) throws BoardInitException {
        BoardSettings defSet = settings.getSettings().get("default");
        BoardSettings bSet = settings.getSettings().get(boardName);
        
        if(bSet.getEngine() == null)
            bSet.setEngine(defSet.getEngine());
        if(bSet.getDatabase() == null)
            bSet.setDatabase(defSet.getDatabase());
        if(bSet.getHost() == null)
            bSet.setHost(defSet.getHost());
        if(bSet.getUsername() == null)
            bSet.setUsername(defSet.getUsername());
        if(bSet.getPassword() == null)
            bSet.setPassword(defSet.getPassword());
        if(bSet.getPath() == null)
            bSet.setPath(defSet.getPath() + "/" + boardName + "/");
        if(bSet.getWebserverGroup() == null)
            bSet.setWebserverGroup(defSet.getWebserverGroup());
        if(bSet.getThumbThreads() == null)
            bSet.setThumbThreads(defSet.getThumbThreads());
        if(bSet.getMediaThreads() == null)
            bSet.setMediaThreads(defSet.getMediaThreads());
        if(bSet.getNewThreadsThreads() == null)
            bSet.setNewThreadsThreads(defSet.getNewThreadsThreads());
        if(bSet.getThreadRefreshRate() == null)
            bSet.setThreadRefreshRate(defSet.getThreadRefreshRate());
        if(bSet.getPageSettings() == null)
            bSet.setPageSettings(defSet.getPageSettings());
        
        if(bSet.getTable() == null)
            bSet.setTable(boardName);
        
        boolean fullMedia = (bSet.getMediaThreads() != 0);
        
        Yotsuba sourceBoard = new Yotsuba(boardName);

        // Get and init DB engine class through reflection
        String boardEngine = bSet.getEngine() == null ? "Mysql" : bSet.getEngine();
        bSet.setEngine(boardEngine);
        
        Class<?> sqlBoardClass;
        Constructor<?> boardCnst;
        
        // Init two DB objects: one for topic insertion and another
        // for media insertion
        Object topicDbObj;
        Object mediaDbObj;
        
        try {
            sqlBoardClass = Class.forName("net.easymodo.asagi." + boardEngine);
            boardCnst = sqlBoardClass.getConstructor(String.class, BoardSettings.class);
                       
            // For topics
            topicDbObj = boardCnst.newInstance(bSet.getPath(), bSet);
            
            // For media
            mediaDbObj = boardCnst.newInstance(bSet.getPath(), bSet);
        } catch(ClassNotFoundException e) {
            throw new BoardInitException("Could not find board engine for " + boardEngine);
        } catch(NoSuchMethodException e) {
            throw new BoardInitException("Error initializing board engine " + boardEngine);
        } catch(InstantiationException e) {
            throw new BoardInitException("Error initializing board engine " + boardEngine);
        } catch(IllegalAccessException e) {
            throw new BoardInitException("Error initializing board engine " + boardEngine);
        } catch(InvocationTargetException e) {
            if(e.getCause() instanceof BoardInitException)
                throw (BoardInitException)e.getCause();
            else if(e.getCause() instanceof RuntimeException)
                throw (RuntimeException)e.getCause();
            throw new BoardInitException("Error initializing board engine " + boardEngine);
        }
        
        // Making sure we got valid DB engines for post and media insertion
        DB topicDb = null;
        DB mediaDb = null;
        
        if(topicDbObj instanceof DB && mediaDbObj instanceof DB) {
            topicDb = (DB) topicDbObj;
            mediaDb = (DB) mediaDbObj;
        }

        if(topicDb == null || mediaDb == null) {
            throw new BoardInitException("Wrong engine specified for " + boardEngine);
        }
        
        Local topicLocalBoard = new Local(bSet.getPath(), bSet, topicDb);
        Local mediaLocalBoard = new Local(bSet.getPath(), bSet, mediaDb);
        
        Dumper dumper = new Dumper(boardName, topicLocalBoard, mediaLocalBoard, sourceBoard, fullMedia);
        
        for(int i = 0; i < bSet.getThumbThreads() ; i++) {
            Thread thumbFetcher = new Thread(dumper.new ThumbFetcher());
            thumbFetcher.setName("Thumb fetcher #" + i + " - " + boardName);
            thumbFetcher.start();
        }
        
        for(int i = 0; i < bSet.getMediaThreads() ; i++) {
            Thread mediaFetcher = new Thread(dumper.new MediaFetcher());
            mediaFetcher.setName("Media fetcher #" + i + " - " + boardName);
            mediaFetcher.start();
        }
        
        for(int i = 0; i < bSet.getNewThreadsThreads() ; i++) {
            Thread topicFetcher = new Thread(dumper.new TopicFetcher());
            topicFetcher.setName("Topic fetcher #" + i + " - " + boardName);
            topicFetcher.start();
        }
    
        for(PageSettings pageSet : bSet.getPageSettings()) {            
            Thread pageScanner = new Thread(dumper.new PageScanner(pageSet.getDelay(), pageSet.getPages()));
            pageScanner.setName("Page scanner " + pageSet.getPages().get(0) + " - " + boardName);
            pageScanner.start();
        }
        
        Thread topicInserter = new Thread(dumper.new TopicInserter());
        topicInserter.setName("Topic inserter" + " - " + boardName);
        topicInserter.start();
        
        Thread postDeleter = new Thread(dumper.new PostDeleter());
        postDeleter.setName("Post deleter" + " - " + boardName);
        postDeleter.start();
        
        Thread topicRebuilder = new Thread(dumper.new TopicRebuilder(bSet.getThreadRefreshRate()));
        topicRebuilder.setName("Topic rebuilder" + " - " + boardName);
        topicRebuilder.start();
    }
    
    public static void main(String[] args) {        
        Settings fullSettings;
        String settingsJson;
        Gson gson = new Gson();
        
        File settingsFile = new File(SETTINGS_FILE);

        try {
            settingsJson = Files.toString(settingsFile, Charsets.UTF_8);
        } catch(IOException e) {
            System.out.println("ERROR: Can't find settings file ("+ SETTINGS_FILE + ")");
            return;
        }
        
        try {
            fullSettings = gson.fromJson(settingsJson, Settings.class);
        } catch(JsonSyntaxException e) {
            System.out.println("ERROR: Settings file is malformed!");
            return;
        }
        
        for(String boardName : fullSettings.getSettings().keySet()) {
            if(boardName.equals("default")) continue;
            try {
                spawnBoard(boardName, fullSettings);
            } catch(BoardInitException e) {
                System.out.println("ERROR: Error creating database connection for /" + boardName + "/:");
                System.out.println("  " + e.getMessage());
            }
        }
    }
}
