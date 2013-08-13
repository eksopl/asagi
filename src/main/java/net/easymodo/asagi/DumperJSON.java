package net.easymodo.asagi;

import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentParseException;
import net.easymodo.asagi.exception.HttpGetException;
import net.easymodo.asagi.model.Page;
import net.easymodo.asagi.model.Topic;
import net.easymodo.asagi.settings.BoardSettings;
import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("UnusedDeclaration")
public class DumperJSON extends AbstractDumper {
    public DumperJSON(String boardName, Local topicLocalBoard, Local mediaLocalBoard, Board sourceBoard, boolean fullThumb, boolean fullMedia, int pageLimbo) {
        super(boardName, topicLocalBoard, mediaLocalBoard, sourceBoard, fullThumb, fullMedia, pageLimbo);
    }

    @Override
    public void initDumper(BoardSettings boardSettings) {
        super.initDumper(boardSettings);

        ThreadUtils.initThread(boardName, new BoardPoller(boardSettings.getRefreshDelay()), "Threadlist fetcher", 1);
    }

    private class BoardPoller implements Runnable {
        private final long wait;
        private String lastMod;

        BoardPoller(long wait) {
            this.wait = wait * 1000;
        }

        private void sleepRemaining(long startTime) {
            long left = this.wait - (DateTime.now().getMillis() - startTime);
            if(left > 0) {
                try { Thread.sleep(left); } catch(InterruptedException e) { }
            }
        }

        @Override
        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            while(true) {
                long startTime = DateTime.now().getMillis();
                Page threadList;
                try {
                    threadList = sourceBoard.getAllThreads(lastMod);
                } catch(HttpGetException e) {
                    if(e.getHttpStatus() == 304)
                        debug(TALK, ("threads.json: not modified"));
                    else
                        debug(WARN, "threads.json: " + e.getMessage());
                    sleepRemaining(startTime);
                    continue;
                } catch (ContentGetException e) {
                    debug(WARN, "Error getting thread list: " + e.getMessage());
                    sleepRemaining(startTime);
                    continue;
                } catch (ContentParseException e) {
                    debug(WARN, "Error parsing thread list: " + e.getMessage());
                    sleepRemaining(startTime);
                    continue;
                }

                lastMod = threadList.getLastMod();

                Map<Integer,Topic> threadMap = new HashMap<Integer, Topic>();
                for(Topic topic : threadList.getThreads()) {
                    threadMap.put(topic.getNum(), topic);
                }

                // Go over the old threads
                for(Topic oldTopic : topics.values()) {
                    oldTopic.lock.readLock().lock();
                    int oldTopicNum = oldTopic.getNum();
                    long oldTopicLastMod = oldTopic.getLastModTimestamp();
                    oldTopic.lock.readLock().unlock();

                    Topic newTopic = threadMap.remove(oldTopicNum);
                    if(newTopic != null) {
                        if(oldTopicLastMod < newTopic.getLastModTimestamp()) {
                            debug(TALK, "modified: " + oldTopicNum);
                            if(!newTopics.contains(newTopic.getNum()))
                                newTopics.add(newTopic.getNum());
                        }

                        oldTopic.lock.writeLock().lock();
                        oldTopic.setLastModTimestamp(newTopic.getLastModTimestamp());
                        oldTopic.setLastPage(newTopic.getLastPage());
                        oldTopic.lock.writeLock().unlock();
                    } else {
                        // baleeted topic
                        if(!newTopics.contains(oldTopicNum))
                            newTopics.add(oldTopicNum);
                    }

                }

                // These are new!
                for(Topic topic : threadMap.values()) {
                    topic.lock.writeLock().lock();
                    topics.put(topic.getNum(), topic);
                    if(!newTopics.contains(topic.getNum())) {
                        newTopics.add(topic.getNum());
                    }
                    topic.lock.writeLock().unlock();
                }

                debug(TALK, "threads.json update");

                sleepRemaining(startTime);
            }
        }
    }
}
