package net.easymodo.asagi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import net.easymodo.asagi.settings.*;
import net.easymodo.asagi.exception.*;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class Dumper {
    private final String boardName;
    private final int debugLevel;

    private static final String DEBUG_FILE = "./debug.log";
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

    private static BufferedWriter debugOut;

    public Dumper(String boardName, Local topicLocalBoard, Local mediaLocalBoard,
            Board sourceBoard, boolean fullMedia, int pageLimbo) {
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
        this.pageLimbo = pageLimbo;
    }

    public void debug(int level, String ... args){
        String preOutput = "[" +
                        boardName + " " +
                        topics.size() + " " +
                        newTopics.size() + " " +
                        topicUpdates.size() + " " +
                        mediaUpdates.size() + " " +
                        mediaPreviewUpdates.size() +
                        "] ";

        String output = "";

        for(String arg : args)
            output = output.concat(arg);

        if (level <= debugLevel) {
            System.out.println(preOutput + output);
        }
        if(debugOut != null && level == ERROR) {
            LocalTime time = new LocalTime();

            try {
                debugOut.write("["+time.getHourOfDay()+":"+
                        time.getMinuteOfHour()+":"+time.getSecondOfMinute()+"]");
                debugOut.write("["+boardName+"] ");
                debugOut.write(output + '\n');
                debugOut.flush();
            } catch(IOException e) {
                System.err.println("WARN: Cannot write to debug file");
            }
        }
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
                MediaPost mediaPrevPost;

                try {
                    mediaPrevPost = mediaPreviewUpdates.take();
                } catch(InterruptedException e) { continue; }

                try {
                    mediaLocalBoard.insertMediaPreview(mediaPrevPost, sourceBoard);
				} catch(ContentGetException e) {
                    debug(ERROR, "Couldn't fetch preview of post " +
                            mediaPrevPost.getNum() + ": " + e.getMessage());
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
                MediaPost mediaPost;

                try {
                    mediaPost = mediaUpdates.take();
                } catch(InterruptedException e) { continue; }

                try {
                    mediaLocalBoard.insertMedia(mediaPost, sourceBoard);
                } catch(ContentGetException e) {
                    debug(ERROR, "Couldn't fetch media of post " +
                            mediaPost.getNum() + ": " + e.getMessage());
                } catch(ContentStoreException e) {
                    debug(ERROR, "Couldn't save media of post " +
                            mediaPost.getNum() + ": " + e.getMessage());
                }
            }
        }
    }

    public class TopicInserter implements Runnable {
        @Override
        public void run() {
            while(true) {
                Topic newTopic;
                try {
                     newTopic = topicUpdates.take();
                } catch(InterruptedException e) { continue; }

                newTopic.lock.writeLock().lock();

                try {
                    topicLocalBoard.insert(newTopic);
                } catch(ContentStoreException e) {
                    debug(ERROR, "Couldn't insert topic " + newTopic.getNum() +
                            ": " + e.getMessage());
                    newTopic.lock.writeLock().unlock();
                    continue;
                } catch(DBConnectionException e) {
                    debug(ERROR, "Database connection error while inserting topic: " + newTopic.getNum()
                            + ". Lost connection to database, can't reconnect. Reason: "
                            + e.getMessage());
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
                        MediaPost mediaPost = new MediaPost(post.getNum(), post.isOp(),
                                post.getPreviewOrig(), post.getMediaOrig(), post.getMediaHash());

                        if(post.getPreviewOrig() != null) mediaPreviewUpdates.put(mediaPost);
                        if(post.getMediaOrig() != null && fullMedia) mediaUpdates.put(mediaPost);
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
                int deletedPost;

                try {
                    deletedPost = deletedPosts.take();
                } catch(InterruptedException e) { continue; }

                try {
                    topicLocalBoard.markDeleted(deletedPost);
                } catch(ContentStoreException e) {
                    debug(ERROR, "Couldn't update deleted status of post " +
                            deletedPost + ": " + e.getMessage());
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
                        else
                            debug(WARN, "page " + pageNo + ": " + e.getMessage());
                        continue;
                    } catch(ContentGetException e) {
                        debug(WARN, "page " + pageNo + ": " + e.getMessage());
                        continue;
                    } catch(ContentParseException e) {
                        debug(WARN, "page " + pageNo + ": " + e.getMessage());
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
                        synchronized(newTopics) {
                            if(newTopics.contains(num)) continue;
                        }
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

                        for(Iterator<Post> it = newTopic.getPosts().iterator(); it.hasNext();) {
                            Post newPost = it.next();

                            // This post was already in topics map. Next post
                            if(fullTopic.findPost(newPost.getNum())) {
                                if(newPost.isOmitted()) it.remove();
                                oldPosts++;
                                continue;
                            }

                            // Looks like it's new
                            // Add the post's num to the full topic, we'll
                            // update it for real with newTopic.
                            fullTopic.addPost(newPost.getNum()); newPosts++;

                            // Comment too long. Click here to view the full text.
                            // This means we have to refresh the full thread
                            if(newPost.isOmitted()) mustRefresh = true;
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
        private void pingTopic(Topic topic) {
            if(topic == null) return;

            topic.setLastHit(DateTime.now().getMillis());
            topic.setBusy(false);
            topic.lock.writeLock().unlock();
        }

        @Override
        public void run() {
            while(true) {
               int newTopic;
               try {
                    newTopic = newTopics.take();
               } catch(InterruptedException e) { continue; }

               String lastMod = null;

               Topic oldTopic = topics.get(newTopic);

               // If we already saw this topic before, acquire its lock
               if(oldTopic != null) {
                   oldTopic.lock.writeLock().lock();
                   lastMod = oldTopic.getLastMod();
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
                       pingTopic(oldTopic);
                       debug(TALK, newTopic + ": wasn't modified");
                       continue;
                   } else if(e.getHttpStatus() == 404) {
                       if(oldTopic != null) {
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
                       pingTopic(oldTopic);
                       debug(WARN, newTopic + ": got HTTP status " + e.getHttpStatus());
                       continue;
                   }
               } catch(ContentGetException e) {
                   // We got an even funkier, non-HTTP error
                   pingTopic(oldTopic);
                   debug(WARN, newTopic + ": error: " + e.getMessage());
                   continue;
               } catch(ContentParseException e) {
                   pingTopic(oldTopic);
                   debug(ERROR, newTopic + ": " + e.getMessage());
                   continue;
               }

               if(topic == null) {
                   pingTopic(oldTopic);
                   debug(WARN, newTopic + ": topic has no posts");
                   continue;
               }

               topic.setLastHit(startTime);

               // We're about to make our rebuilt topic public
               topic.lock.writeLock().lock();

               if(oldTopic != null) {
                   // Beaten to the punch (how?)
                   if(oldTopic.getLastHit() > startTime) {
                       debug(ERROR, "Concurrency issue updating topic " + oldTopic.getNum());
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
               topic.lock.writeLock().unlock();

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
                    } catch(InterruptedException e) { continue; }
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

    public static class DumperUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            System.err.print("Exception in thread: \"" + t.getName() + "\" ");
            e.printStackTrace();
            if(e instanceof OutOfMemoryError) {
                System.err.println("Terminating due to out of memory error. Raise VM max heap size? (-Xmx)");
            } else {
                System.err.println("Terminating dumper due to unexpected exception.");
                System.err.println("Please report this issue if you believe it is a bug.");
            }
            System.exit(-1);
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
        if(bSet.getCharset() == null)
            bSet.setCharset(defSet.getCharset());
        if(bSet.getPath() == null)
            bSet.setPath(defSet.getPath() + "/" + boardName + "/");
        if(bSet.getUseOldDirectoryStructure() == null)
            bSet.setUseOldDirectoryStructure(defSet.getUseOldDirectoryStructure());
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
        if(bSet.getDeletedThreadsThresholdPage() == null)
            bSet.setDeletedThreadsThresholdPage(defSet.getDeletedThreadsThresholdPage());

        if(bSet.getTable() == null)
            bSet.setTable(boardName);

        if(bSet.getUseOldDirectoryStructure() == null)
            bSet.setUseOldDirectoryStructure(false);

        int pageLimbo = bSet.getDeletedThreadsThresholdPage();
        boolean fullMedia = (bSet.getMediaThreads() != 0);

        //Yotsuba sourceBoard = new Yotsuba(boardName);
        YotsubaJSON sourceBoard = new YotsubaJSON(boardName);

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

        Dumper dumper = new Dumper(boardName, topicLocalBoard, mediaLocalBoard, sourceBoard, fullMedia, pageLimbo);
        Thread.UncaughtExceptionHandler exHandler = new Dumper.DumperUncaughtExceptionHandler();

        for(int i = 0; i < bSet.getThumbThreads() ; i++) {
            Thread thumbFetcher = new Thread(dumper.new ThumbFetcher());
            thumbFetcher.setName("Thumb fetcher #" + i + " - " + boardName);
            thumbFetcher.setUncaughtExceptionHandler(exHandler);
            thumbFetcher.start();
        }

        for(int i = 0; i < bSet.getMediaThreads() ; i++) {
            Thread mediaFetcher = new Thread(dumper.new MediaFetcher());
            mediaFetcher.setName("Media fetcher #" + i + " - " + boardName);
            mediaFetcher.setUncaughtExceptionHandler(exHandler);
            mediaFetcher.start();
        }

        for(int i = 0; i < bSet.getNewThreadsThreads() ; i++) {
            Thread topicFetcher = new Thread(dumper.new TopicFetcher());
            topicFetcher.setName("Topic fetcher #" + i + " - " + boardName);
            topicFetcher.setUncaughtExceptionHandler(exHandler);
            topicFetcher.start();
        }

        for(PageSettings pageSet : bSet.getPageSettings()) {
            Thread pageScanner = new Thread(dumper.new PageScanner(pageSet.getDelay(), pageSet.getPages()));
            pageScanner.setName("Page scanner " + pageSet.getPages().get(0) + " - " + boardName);
            pageScanner.setUncaughtExceptionHandler(exHandler);
            pageScanner.start();
        }

        Thread topicInserter = new Thread(dumper.new TopicInserter());
        topicInserter.setName("Topic inserter" + " - " + boardName);
        topicInserter.setUncaughtExceptionHandler(exHandler);
        topicInserter.start();

        Thread postDeleter = new Thread(dumper.new PostDeleter());
        postDeleter.setName("Post deleter" + " - " + boardName);
        postDeleter.setUncaughtExceptionHandler(exHandler);
        postDeleter.start();

        Thread topicRebuilder = new Thread(dumper.new TopicRebuilder(bSet.getThreadRefreshRate()));
        topicRebuilder.setName("Topic rebuilder" + " - " + boardName);
        topicRebuilder.setUncaughtExceptionHandler(exHandler);
        topicRebuilder.start();
    }

    public static void main(String[] args) {
        Settings fullSettings;
        String settingsJson;
        Gson gson = new Gson();
        String settingsFileName = SETTINGS_FILE;

        for(int i = 0; i < args.length; ++i) {
            if(args[i].equals("--config") && ++i < args.length) {
                settingsFileName = args[i];
            }
        }

        File debugFile = new File(DEBUG_FILE);
        try {
            debugOut = new BufferedWriter(Files.newWriterSupplier(debugFile, Charsets.UTF_8, true).getOutput());
        } catch(IOException e1) {
            System.err.println("WARN: Cannot write to debug file");
            debugOut = null;
        }

        BufferedReader settingsReader;
        if(settingsFileName.equals("-")) {
            settingsReader = new BufferedReader(new InputStreamReader(System.in, Charsets.UTF_8));
        } else {
            File settingsFile = new File(settingsFileName);
            try {
                settingsReader = Files.newReader(settingsFile, Charsets.UTF_8);
            } catch(FileNotFoundException e) {
                System.err.println("ERROR: Can't find settings file ("+ settingsFile + ")");
                return;
            }
        }

        try {
            settingsJson = CharStreams.toString(settingsReader);
        } catch(IOException e) {
            System.err.println("ERROR: Error while reading settings file");
            return;
        }

        try {
            fullSettings = gson.fromJson(settingsJson, Settings.class);
        } catch(JsonSyntaxException e) {
            System.err.println("ERROR: Settings file is malformed!");
            return;
        }

        for(String boardName : fullSettings.getSettings().keySet()) {
            if(boardName.equals("default")) continue;
            try {
                spawnBoard(boardName, fullSettings);
            } catch(BoardInitException e) {
                System.err.println("ERROR: Error initializing dumper for /" + boardName + "/:");
                System.err.println("  " + e.getMessage());
            }
        }
    }
}
