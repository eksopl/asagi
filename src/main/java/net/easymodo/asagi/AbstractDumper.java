package net.easymodo.asagi;

import net.easymodo.asagi.exception.*;
import net.easymodo.asagi.model.MediaPost;
import net.easymodo.asagi.model.Post;
import net.easymodo.asagi.model.Topic;
import net.easymodo.asagi.settings.BoardSettings;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractDumper {
    protected final String boardName;
    private final int debugLevel;
    private BufferedWriter debugOut = Asagi.getDebugOut();

    private final int pageLimbo;
    private final boolean fullMedia;
    private final Local topicLocalBoard;
    private final Local mediaLocalBoard;
    private final BlockingQueue<MediaPost> mediaPreviewUpdates;
    private final BlockingQueue<MediaPost> mediaUpdates;
    private final BlockingQueue<Integer> deletedPosts;

    protected final BlockingQueue<Topic> topicUpdates;
    protected final Board sourceBoard;
    protected final ConcurrentHashMap<Integer,Topic> topics;
    protected final BlockingQueue<Integer> newTopics;

    public static final int ERROR = 1;
    public static final int WARN  = 2;
    public static final int TALK  = 3;
    public static final int INFO  = 4;


    public AbstractDumper(String boardName, Local topicLocalBoard, Local mediaLocalBoard,
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

    public void initDumper(BoardSettings boardSettings) {
        ThreadUtils.initThread(boardName, new ThumbFetcher(), "Thumb fetcher", boardSettings.getThumbThreads());
        ThreadUtils.initThread(boardName, new MediaFetcher(), "Media fetcher", boardSettings.getMediaThreads());
        ThreadUtils.initThread(boardName, new TopicFetcher(), "Topic fetcher", boardSettings.getNewThreadsThreads());
        ThreadUtils.initThread(boardName, new TopicInserter(), "Topic inserter", 1);
        ThreadUtils.initThread(boardName, new PostDeleter(), "Post deleter", 1);
    }


    protected boolean findDeleted(Topic oldTopic, Topic newTopic, boolean markDeleted) {
        boolean changed = false;

        if(oldTopic == null) return changed;

        List<Integer> oldPosts = new ArrayList<Integer>(oldTopic.getAllPosts());

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


    protected class ThumbFetcher implements Runnable {
        @Override
        @SuppressWarnings("InfiniteLoopStatement")
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

    protected class MediaFetcher implements Runnable {
        @Override
        @SuppressWarnings("InfiniteLoopStatement")
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

    protected class TopicInserter implements Runnable {
        @Override
        @SuppressWarnings("InfiniteLoopStatement")
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
                        MediaPost mediaPost = new MediaPost(post.getNum(), post.getThreadNum(), post.isOp(),
                                post.getPreviewOrig(), post.getMediaOrig(), post.getMediaHash());

                        if(post.getPreviewOrig() != null) {
                            if(!mediaPreviewUpdates.contains(mediaPost))
                                mediaPreviewUpdates.put(mediaPost);
                        }
                        if(post.getMediaOrig() != null && fullMedia) {
                            if(!mediaUpdates.contains(mediaPost))
                                mediaUpdates.put(mediaPost);
                        }
                    } catch(InterruptedException e) { }
                }
                newTopic.purgePosts();
                newTopic.lock.writeLock().unlock();
            }
        }
    }

    protected class PostDeleter implements Runnable {
        @Override
        @SuppressWarnings("InfiniteLoopStatement")
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

    protected class TopicFetcher implements Runnable {
        private void pingTopic(Topic topic) {
            if(topic == null) return;

            topic.lock.writeLock().lock();
            topic.setLastHit(DateTime.now().getMillis());
            topic.setBusy(false);
            topic.lock.writeLock().unlock();
        }

        @Override
        @SuppressWarnings("InfiniteLoopStatement")
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
                   oldTopic.lock.readLock().lock();
                   lastMod = oldTopic.getLastMod();
                   oldTopic.lock.readLock().unlock();
               }

               long startTime = DateTime.now().getMillis();

               // Let's go get our updated topic, from the topic page
               Topic topic;
               try {
                   topic = sourceBoard.getThread(newTopic, lastMod);
               } catch(HttpGetException e) {
                   if(e.getHttpStatus() == 304) {
                       // If the old topic exists, update its lastHit timestamp
                       // The old topic should always exist at this point.
                       pingTopic(oldTopic);
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
                       }
                       continue;
                   } else {
                       // We got some funky error
                       pingTopic(oldTopic);
                       debug(WARN, newTopic + ": error: " + e.getMessage());
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
                   oldTopic.lock.writeLock().lock();

                   // Beaten to the punch (how?)
                   if(oldTopic.getLastHit() > startTime) {
                       debug(ERROR, "Concurrency issue updating topic " + oldTopic.getNum());
                       oldTopic.lock.writeLock().unlock();

                       // Throw this away now.
                       topic.lock.readLock().unlock();
                       continue;
                   }

                   // Get the deleted posts from the old topic
                   // Update their status in the DB, too.
                   findDeleted(oldTopic, topic, true);

                   // We don't really know at which page this thread is, so let
                   // us keep the last page a PageScanner (or BoardPoller) saw this thread at.
                   topic.setLastPage(oldTopic.getLastPage());

                   // If the old topic has a lastMod timestamp from a BoardPoller, keep it
                   topic.setLastModTimestamp(oldTopic.getLastModTimestamp());

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
           }
        }
    }
}
