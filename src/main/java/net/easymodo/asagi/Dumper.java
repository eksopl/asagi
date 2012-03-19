package net.easymodo.asagi;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Dumper {
    private final Local localBoard;
    private final ConcurrentHashMap<Integer,Topic> topics;
    private final BlockingQueue<Post> mediaPreviewUpdates;
    private final BlockingQueue<Post> mediaUpdates;
    private final BlockingQueue<Topic> topicUpdates;
    private final BlockingQueue<Integer> newTopics;
    
    private final int debugLevel = 100;
    
    public static final int ERROR = 1;
    public static final int WARN  = 2;
    public static final int TALK  = 3;

    public Dumper(Local localBoard) {
        this.localBoard = localBoard;
        this.topics = new ConcurrentHashMap<Integer,Topic>();
        this.mediaPreviewUpdates = new LinkedBlockingQueue<Post>();
        this.mediaUpdates = new LinkedBlockingQueue<Post>();
        this.topicUpdates = new LinkedBlockingQueue<Topic>();
        this.newTopics = new LinkedBlockingQueue<Integer>();
    }
    
    public void debug(int level, String ... args){
        String output = "[" +
                        topics.size() + " " +
                        newTopics.size() + " " +
                        topicUpdates.size() + " " +
                        mediaUpdates.size() + " " +
                        mediaPreviewUpdates.size() + " " +
                        "] ";
                
        for(String arg : args)
            output = output.concat(arg);
        
        if (level <= debugLevel)
            System.out.println(output);
    }

 
    private void updateTopic(Topic topic) {
        List<Post> posts = topic.getPosts();
        if(posts == null) return;
        
        for(Post post : posts) {
            try {
                if(post.getPreview() != null) mediaPreviewUpdates.put(post);
                if(post.getMediaFilename() != null) mediaUpdates.put(post);
            } catch(InterruptedException e) { }
        }
        
        topicUpdates.add(topic);
    }
    
   
    private boolean markDeleted(Topic oldTopic, Topic newTopic) {
        boolean changed = false;
        
        if(oldTopic == null) return changed;
       
        List<Post> oldPosts = new ArrayList<Post>(oldTopic.getPosts());
        
        // Get the posts from the old thread not marked as deleted.
        // We have to do this, otherwise our math for omitted posts will be
        // wrong.
        for(Iterator<Post> it = oldPosts.iterator(); it.hasNext();) {
            if(it.next().isDeleted())
                it.remove();
        }
                
        if(oldPosts.isEmpty()) return changed;
        
        for(int i = 0; i < oldPosts.size(); i++) {
            Post post = oldPosts.get(i);
            if(!post.isDeleted() && newTopic.findPost(post.getNum()) == null) {
                changed = true;
                post.setDeleted(true);
                newTopic.addPost(post);
                debug(TALK, post.getNum() + " (post): deleted");
                debug(TALK, post.getNum() + " oldPosts.size():" + oldPosts.size());
                debug(TALK, post.getNum() + " newTopic.getOmPosts(): " + newTopic.getOmPosts());
            }
            if(i == 0) i = newTopic.getOmPosts();
        }
        
        return changed;
    }
   
    
    public class ThumbFetcher implements Runnable {
        private Board sourceBoard;
        
        public ThumbFetcher(Board sourceBoard) {
            this.sourceBoard = sourceBoard;
        }
        
        @Override
        public void run() {
            while(true) {
                Post mediaPrevPost = null;
                
                try {
                mediaPrevPost = mediaPreviewUpdates.take();
                } catch(InterruptedException e) { }  
                
                try {
                    localBoard.insertMediaPreview(mediaPrevPost, sourceBoard);
                } catch(ContentGetException e) {
                    debug(ERROR, "Couldn't fetch preview of post " + 
                            mediaPrevPost.getNum() + ": " + e.getMessage());
                    continue;
                }
            }
        }
    }
    
    public class MediaFetcher implements Runnable {
        private Board sourceBoard;
        
        public MediaFetcher(Board sourceBoard) {
            this.sourceBoard = sourceBoard;
        }
        
        @Override
        public void run() {
            while(true) {
                Post mediaPost = null;
                
                try {
                    mediaPost = mediaUpdates.take();
                } catch(InterruptedException e) { }  
                
                try {
                    localBoard.insertMedia(mediaPost, sourceBoard);
                } catch(ContentGetException e) {
                    debug(ERROR, "Couldn't fetch media of post " + 
                            mediaPost.getNum() + ": " + e.getMessage());
                    continue;
                }
            } 
        }
    }
    
    public class TopicInserter implements Runnable {
        private Mysql sqlBoard;
        
        public TopicInserter(Mysql sqlBoard) {
            this.sqlBoard = sqlBoard;
        }
        
        @Override
        public void run() {
            while(true) {
                Topic newTopic = null;
                try {
                     newTopic = topicUpdates.take();
                } catch(InterruptedException e) { }
                
                newTopic.lock.readLock().lock();
                
                try {
                    sqlBoard.insert(newTopic);
                } catch(SQLException e) {
                    debug(ERROR, "Couldn't insert topic " + newTopic.getNum() +
                            ": " + e.getMessage());
                    continue;
                } finally {
                    newTopic.lock.readLock().unlock();
                }
            }
        } 
    }
    
    public class PageScanner implements Runnable {
        private final List<Integer> pageNos;
        private final long wait;
        private String[] pagesLastMods;
        private Board sourceBoard;
        
        PageScanner(Board sourceBoard, List<Integer> pageNos, long wait) {
            this.pageNos = pageNos;
            this.wait = wait;
            this.sourceBoard = sourceBoard;
            this.pagesLastMods = new String[Collections.max(pageNos) + 1];
        }
        
        @Override
        public void run() {
            while(true) {
                long now = System.currentTimeMillis();
                for(int pageNo : pageNos) {
                    String lastMod = pagesLastMods[pageNo];
                    Page page;
                    
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

                        int oldPosts = 0;
                        int newPosts = 0;
                        boolean mustRefresh = false;
                        
                        // We check for any posts that got deleted
                        // We have the write lock, so TopicFetchers can suck it.
                        if(markDeleted(fullTopic, newTopic)) mustRefresh = true;
                        
                        for(Post newPost : newTopic.getPosts()) {
                            // Get the same post from the previous encountered thread
                            Post oldPost = fullTopic.findPost(newPost.getNum());
                            
                            // Comment too long. Click here to view the full text.
                            // This means we have to refresh the full thread
                            if(newPost.isOmitted()) mustRefresh = true;
                            
                            // This post was already in topics map. Next post
                            if(oldPost != null) { oldPosts++; continue; }
                            
                            // Looks like it's new
                            fullTopic.addPost(newPost); newPosts++;
                            
                            // We have to refresh to get the image filename, sadly
                            if(newPost.getMedia() != null) mustRefresh = true;
                        }
                        
                        // Update the time we last hit this thread
                        fullTopic.setLastHit(now);
                        
                        fullTopic.lock.writeLock().unlock();
                                                
                        //  No new posts
                        if(oldPosts != 0 && newPosts == 0) continue;
                        
                        debug(TALK, (pageNo == 0 ? "front page" : "page " + pageNo) + " update");
                        
                        newTopic.lock.readLock().lock();
                        // Push new posts/images/thumbs to their queues
                        updateTopic(newTopic);
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
                
                long left = this.wait - (System.currentTimeMillis() - now);
                if(left > 0) {
                    try { Thread.sleep(left); } catch(InterruptedException e) { debug(TALK, "interrupted"); }
                }
            }            
        }
    }
    
    public class TopicFetcher implements Runnable {
        private Board sourceBoard;
        
        public TopicFetcher(Board sourceBoard) {
            this.sourceBoard = sourceBoard;
        }
        
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
                           oldTopic.setLastHit(System.currentTimeMillis());
                           oldTopic.setBusy(false);
                           oldTopic.lock.writeLock().unlock();
                       }
                       debug(TALK, newTopic + ": wasn't modified");
                       continue;
                   } else if(e.getHttpStatus() == 404) {
                       // TODO: 404 here
                       
                       debug(TALK, newTopic + ": deleted");
                       
                       // Goodbye, old topic.
                       oldTopic.lock.writeLock().lock();
                       topics.remove(newTopic);
                       oldTopic.lock.writeLock().unlock();
                       oldTopic = null;
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
               }
               
               if(topic == null) { debug(WARN, newTopic + ": topic without posts?"); continue; }
               
               topic.setLastHit(System.currentTimeMillis());
               
               // We're about to make our rebuilt topic public
               topic.lock.readLock().lock();
               
               if(oldTopic != null) {
                   // Get the deleted posts from the old topic
                   // Also, mark them as such
                   oldTopic.lock.writeLock().lock();
                   markDeleted(oldTopic, topic);
                   
                   // Goodbye, old topic.
                   topics.remove(newTopic);
                   topics.put(newTopic, topic);
                   oldTopic.lock.writeLock().unlock();
                   oldTopic = null;
               } else {
                   // Hello, new topic!
                   topics.put(newTopic, topic);
               }
               
               // We have a read lock, update it
               updateTopic(topic);
               topic.lock.readLock().unlock();
               
               debug(TALK, newTopic + ": " + (oldTopic != null ? "updated" : "new"));
           }
        }
    }
    
    public class TopicRebuilder implements Runnable {
        private final long threadRefreshRate;
        
        public TopicRebuilder(int threadRefreshRate) {
            this.threadRefreshRate = threadRefreshRate * 60 * 1000;
        }
        
        
        @Override
        public void run() {
            while(true) {
                for(Topic topic : topics.values()) {
                    try {
                        if(!topic.lock.writeLock().tryLock(1, TimeUnit.SECONDS)) continue;
                    } catch(InterruptedException e) { }
                    if(topic.isBusy()) { topic.lock.writeLock().unlock(); continue; }
                    
                    long deltaLastHit = System.currentTimeMillis() - topic.getLastHit();
                    //debug(TALK, "deltaLastHit for " + topic.getNum() + ": " + deltaLastHit);
                    
                    if(deltaLastHit <= threadRefreshRate) { topic.lock.writeLock().unlock();  continue; }
                    
                    topic.setBusy(true);
                    try {
                        newTopics.put(topic.getNum());
                    } catch(InterruptedException e) { }
                    
                    topic.lock.writeLock().unlock();
                }
                try {
                    Thread.sleep(2000);
                } catch(InterruptedException e) { }
            }
        }
    }

    
    public static void main(String[] args) {
        // TODO: Finish main method
        // Settings parsing, proper thread launching, etc.
        
        // String boardName = args[1];
        
        Map<String, String> settings = new HashMap<String, String>();
        settings.put("database", "archive");
        settings.put("host", "localhost");
        settings.put("name", "root");
        settings.put("password", "1234");
        settings.put("table", "jpjp");
        settings.put("fullMedia", "true");
        String path = "/Users/eksi/asagi/jp/";
        String boardName = "jp";
        List<int[]> pages = new ArrayList<int[]>();
        int[] pages1 = {30*1000, 0, 1, 2};
        int[] pages2 = {30*1000, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        pages.add(pages1);
        pages.add(pages2);
        
        int thumbThreads = 5;
        int mediaThreads = 5;
        int newThreadsThreads = 5;
        int threadRefreshRate = 1;
        
        Local localBoard = new Local(path, settings);
        Mysql sqlBoard = null;
        try {
            sqlBoard = new Mysql(path, settings);
        } catch(SQLException e) {
            e.printStackTrace();
        }
        
        Dumper dumper = new Dumper(localBoard);
        
        for(int i = 0; i < thumbThreads ; i++) {
            Thread thumbFetcher = new Thread(dumper.new ThumbFetcher(new Yotsuba(boardName)));
            thumbFetcher.setName("Thumb fetcher #" + i);
            thumbFetcher.start();
        }
        
        for(int i = 0; i < mediaThreads ; i++) {
            Thread mediaFetcher = new Thread(dumper.new MediaFetcher(new Yotsuba(boardName)));
            mediaFetcher.setName("Media fetcher #" + i);
            mediaFetcher.start();
        }
        
        for(int i = 0; i < newThreadsThreads ; i++) {
            Thread topicFetcher = new Thread(dumper.new TopicFetcher(new Yotsuba(boardName)));
            topicFetcher.setName("Topic fetcher #" + i);
            topicFetcher.start();
        }
    
        for(int[] page : pages) {
            List<Integer> pageNos = new ArrayList<Integer>();
            for(int i = 1; i < page.length; i++) {
                pageNos.add(page[i]);
            }
            Thread pageScanner = new Thread(dumper.new PageScanner(new Yotsuba(boardName), pageNos, page[0]));
            pageScanner.setName("Page scanner " + page[1]);
            pageScanner.start();
        }
        
        Thread topicInserter = new Thread(dumper.new TopicInserter(sqlBoard));
        topicInserter.setName("Topic inserter");
        topicInserter.start();
        
        Thread topicRebuilder = new Thread(dumper.new TopicRebuilder(threadRefreshRate));
        topicRebuilder.setName("Topic rebuilder");
        topicRebuilder.start();
    }
}
