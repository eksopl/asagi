package net.easymodo.asagi;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

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
    
    /*
    private boolean markDeleted(Topic oldTopic, Topic newTopic) {
        boolean changed = false;
        
        List<Post> deletedPosts = new ArrayList<Post>(oldTopic.getPosts());
        
        for(Iterator<Post> it = deletedPosts.iterator(); it.hasNext();) {
            if(it.next().isDeleted())
                it.remove();
        }
        
        // TODO: Finish implementation
        
        return changed;
    }
    */
    
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
                
                debug(TALK, "Inserting topic " + newTopic.getNum());
                
                try {
                    sqlBoard.insert(newTopic);
                } catch(SQLException e) {
                    debug(ERROR, "Couldn't insert topic " + newTopic.getNum() +
                            ": " + e.getMessage());
                    continue;
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
            // TODO: Finish thread fetcher
            while(true) {
                int newTopic = 0;
                try {
                    newTopic = newTopics.take(); 
               } catch(InterruptedException e) { }
               
               // long gettime = System.currentTimeMillis();
               
               // TODO: Lastmod here
               Topic topic;
               try {
                   topic = sourceBoard.content(Request.thread(newTopic));
               } catch(HttpGetException e) {
                   // TODO: Not Modified here
                   if(e.getHttpStatus() == 304) {
                       debug(TALK, "Not modified");
                   }
                   // TODO: 404 here
                   if(e.getHttpStatus() == 404) {
                       debug(TALK, "Not found");
                   }
                   continue;
               } catch(ContentGetException e) {
                   debug(WARN, "error:" + e.getMessage());
                   continue;
               }
               
               if(topic == null) continue;
               topics.put(newTopic, topic);
               
               updateTopic(topic);
           }
        }
    }
    
    public class PageScanner implements Runnable {
        private final List<Integer> pageNos;
        private final long wait;
        private Board sourceBoard;
        
        PageScanner(Board sourceBoard, List<Integer> pageNos, long wait) {
            this.pageNos = pageNos;
            this.wait = wait;
            this.sourceBoard = sourceBoard;
        }
        
        @Override
        public void run() {
            while(true) {
                long now = System.currentTimeMillis();
                for(int pageNo : pageNos) {                    
                    Page page;
                    try {
                        page = sourceBoard.content(Request.page(pageNo));
                    } catch(ContentGetException e) {
                        debug(WARN, pageNo + e.getMessage());
                        continue;
                    }
                    
                    debug(TALK, "got page " + pageNo);
                    
                    for(Topic newTopic : page.getThreads()) {
                        int num = newTopic.getNum();
                        
                        // If we haven't seen this thread before, push it 
                        // into the new threads queue and skip it
                        if(!topics.containsKey(num)) { 
                            try { newTopics.put(num); } catch(InterruptedException e) {}
                            continue;
                        }
                        
                        Topic oldTopic = topics.get(num);
                        
                        int oldPosts = 0;
                        int newPosts = 0;
                        boolean mustRefresh = false;
                        
                        // We check for any posts that got deleted
                        // markDeleted(oldTopic, newTopic);
                        
                        for(Post newPost : newTopic.getPosts()) {
                            // Get the same post from the previous encountered thread
                            Post oldPost = oldTopic.findPost(newPost.getNum());
                            
                            // Comment too long. Click here to view the full text.
                            // This means we have to refresh the full thread
                            if(newPost.isOmitted()) mustRefresh = true;
                            
                            // This post was already in topics map. Next post
                            if(oldPost != null) { oldPosts++; continue; }
                            
                            // Looks like it's new
                            oldTopic.addPost(newPost); newPosts++;
                            
                            // We have to refresh to get the image filename, sadly
                            if(newPost.getMedia() != null) { 
                                //debug(TALK, "New post " + newPost.getNum() + " has media, need refresh");
                                mustRefresh = true;
                            }
                        }
                        
                        // Update the time we last hit this thread
                        oldTopic.setLastMod(now);
                        
                        //  No new posts
                        if(oldPosts != 0 && newPosts == 0) continue;
                        
                        debug(TALK, pageNo + " update");
                        
                        // Push new posts/images/thumbs to their queues
                        updateTopic(newTopic);
                        
                        // And send the thread to the new threads queue if we were
                        // forced to refresh earlier or if the only old post we
                        // saw was the OP, as that means we're missing posts from inside the thread.
                        if(mustRefresh || oldPosts < 2) {
                            //debug(TALK, "Must refresh thread " + num);
                            try { newTopics.put(num); } catch(InterruptedException e) {}
                        }
                    }
                }
                
                long left = this.wait - (System.currentTimeMillis() - now);
                if(left > 0) {
                    try { Thread.sleep(left); } catch(InterruptedException e) { }
                }
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
        int[] pages1 = {030*1000, 1, 2};
        int[] pages2 = {10*60*1000, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        pages.add(pages1);
        pages.add(pages2);
        
        int thumbThreads = 5;
        int mediaThreads = 5;
        int newThreadsThreads = 5;
        // int threadRefreshRate = 60;
        
        Local localBoard = new Local(path, settings);
        Mysql sqlBoard = null;
        try {
            sqlBoard = new Mysql(path, settings);
        } catch(SQLException e) {
            e.printStackTrace();
        }
        
        Dumper dumper = new Dumper(localBoard);
        
        for(int i = 0; i < thumbThreads ; i++) {
            ThumbFetcher thumbFetcher = dumper.new ThumbFetcher(new Yotsuba(boardName));
            new Thread(thumbFetcher).start();
        }
        
        for(int i = 0; i < mediaThreads ; i++) {
            MediaFetcher mediaFetcher = dumper.new MediaFetcher(new Yotsuba(boardName));
            new Thread(mediaFetcher).start();
        }
        
        for(int i = 0; i < newThreadsThreads ; i++) {
            TopicFetcher topicFetcher = dumper.new TopicFetcher(new Yotsuba(boardName));
            new Thread(topicFetcher).start();
        }
    
        for(int[] page : pages) {
            List<Integer> pageNos = new ArrayList<Integer>();
            for(int i = 1; i < page.length; i++) {
                pageNos.add(page[i]);
            }
            PageScanner pageScanner = dumper.new PageScanner(new Yotsuba(boardName), pageNos, page[0]);
            new Thread(pageScanner).start();
        }
        
        TopicInserter topicInserter = dumper.new TopicInserter(sqlBoard);
        new Thread(topicInserter).start();
    }
}
