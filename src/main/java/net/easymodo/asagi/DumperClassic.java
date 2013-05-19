package net.easymodo.asagi;

import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentParseException;
import net.easymodo.asagi.exception.HttpGetException;
import net.easymodo.asagi.model.Page;
import net.easymodo.asagi.model.Post;
import net.easymodo.asagi.model.Topic;
import net.easymodo.asagi.settings.BoardSettings;
import net.easymodo.asagi.settings.PageSettings;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnusedDeclaration")
public class DumperClassic extends AbstractDumper {
    public DumperClassic(String boardName, Local topicLocalBoard, Local mediaLocalBoard, Board sourceBoard, boolean fullMedia, int pageLimbo) {
        super(boardName, topicLocalBoard, mediaLocalBoard, sourceBoard, fullMedia, pageLimbo);
    }

    public void initDumper(BoardSettings boardSettings) {
        super.initDumper(boardSettings);

        ThreadUtils.initThread(boardName, new TopicRebuilder(boardSettings.getThreadRefreshRate()), "Topic rebuilder", 1);
        for (PageSettings pageSet : boardSettings.getPageSettings()) {
            Thread pageScanner = new Thread(new PageScanner(pageSet.getDelay(), pageSet.getPages()));
            pageScanner.setName("Page scanner " + pageSet.getPages().get(0) + " - " + boardName);
            pageScanner.setUncaughtExceptionHandler(ThreadUtils.UNCAUGHT_EXCEPTION_HANDLER);
            pageScanner.start();
        }
    }

    private class PageScanner implements Runnable {
        private final List<Integer> pageNos;
        private final long wait;
        private String[] pagesLastMods;

        PageScanner(long wait, List<Integer> pageNos) {
            this.wait = wait * 1000;
            this.pageNos = pageNos;
            this.pagesLastMods = new String[Collections.max(pageNos) + 1];
        }

        @Override
        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            while (true) {
                long startTime = DateTime.now().getMillis();
                for (int pageNo : pageNos) {
                    String lastMod = pagesLastMods[pageNo];
                    Page page;

                    long pageStartTime = DateTime.now().getMillis();

                    try {
                        page = sourceBoard.getPage(pageNo, lastMod);
                    } catch (HttpGetException e) {
                        if (e.getHttpStatus() == 304)
                            debug(TALK, (pageNo == 0 ? "front page" : "page " + pageNo)
                                    + ": wasn't modified");
                        else
                            debug(WARN, "page " + pageNo + ": " + e.getMessage());
                        continue;
                    } catch (ContentGetException e) {
                        debug(WARN, "page " + pageNo + ": " + e.getMessage());
                        continue;
                    } catch (ContentParseException e) {
                        debug(WARN, "page " + pageNo + ": " + e.getMessage());
                        continue;
                    }

                    if (page == null) {
                        debug(WARN, (pageNo == 0 ? "front page" : "page " + pageNo)
                                + "had no threads");
                        continue;
                    }

                    pagesLastMods[pageNo] = page.getLastMod();

                    debug(INFO, "got page " + pageNo);

                    for (Topic newTopic : page.getThreads()) {
                        int num = newTopic.getNum();

                        // If we never saw this topic, then we'll put it in the
                        // new topics queue, a TopicFetcher will take care of
                        // it.
                        synchronized(newTopics) {
                            if (newTopics.contains(num)) continue;
                        }
                        if (!topics.containsKey(num)) {
                            try { newTopics.put(num); } catch (InterruptedException e) {}
                            continue;
                        }

                        // Otherwise we'll go ahead and try to update the
                        // topic with the posts we have from this index page.
                        Topic fullTopic = topics.get(num);

                        // Perhaps we had extremely bad luck and a TopicFetcher
                        // just saw this thread 404 and got rid of it before we
                        // could grab it? Oh well.
                        if (fullTopic == null) continue;

                        // Try to get the write lock for this topic.
                        fullTopic.lock.writeLock().lock();

                        // Oh, forget it. A ThreadFetcher beat us to this one.
                        // (Or another PageScanner)
                        if (fullTopic.getLastHit() > pageStartTime) {
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
                        if (findDeleted(fullTopic, newTopic, false)) {
                            // Pages cannot be trusted to not have posts missing.
                            // We need to force a refresh, it can't be helped.
                            // See GH-11. Sigh.
                            mustRefresh = true;
                            newPosts++;
                        }

                        for (Iterator<Post> it = newTopic.getPosts().iterator(); it.hasNext();) {
                            Post newPost = it.next();

                            // This post was already in topics map. Next post
                            if (fullTopic.findPost(newPost.getNum())) {
                                if (newPost.isOmitted()) it.remove();
                                oldPosts++;
                                continue;
                            }

                            // Looks like it's new
                            // Add the post's num to the full topic, we'll
                            // update it for real with newTopic.
                            fullTopic.addPost(newPost.getNum()); newPosts++;

                            // Comment too long. Click here to view the full text.
                            // This means we have to refresh the full thread
                            if (newPost.isOmitted()) mustRefresh = true;
                        }

                        // Update the time we last hit this thread
                        fullTopic.setLastHit(pageStartTime);

                        fullTopic.lock.writeLock().unlock();

                        //  No new posts
                        if (oldPosts != 0 && newPosts == 0) continue;

                        debug(TALK, (pageNo == 0 ? "front page" : "page " + pageNo) + " update");

                        newTopic.lock.readLock().lock();
                        // Push new posts/images/thumbs to their queues
                        topicUpdates.add(newTopic);
                        newTopic.lock.readLock().unlock();

                        // And send the thread to the new threads queue if we were
                        // forced to refresh earlier or if the only old post we
                        // saw was the OP, as that means we're missing posts from inside the thread.
                        if (mustRefresh || oldPosts < 2) {
                            debug(TALK, num + ": must refresh");
                            try { newTopics.put(num); } catch (InterruptedException e) {}
                        }
                    }
                }

                long left = this.wait - (DateTime.now().getMillis() - startTime);
                if (left > 0) {
                    try { Thread.sleep(left); } catch (InterruptedException e) { }
                }
            }
        }
    }

    private class TopicRebuilder implements Runnable {
        private final long threadRefreshRate;

        public TopicRebuilder(int threadRefreshRate) {
            this.threadRefreshRate = threadRefreshRate * 60L * 1000L;
        }


        @Override
        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            while (true) {
                for (Topic topic : topics.values()) {
                    try {
                        if (!topic.lock.writeLock().tryLock(1, TimeUnit.SECONDS)) continue;
                    } catch (InterruptedException e) { continue; }
                    if (topic.isBusy()) { topic.lock.writeLock().unlock(); continue; }

                    long deltaLastHit = DateTime.now().getMillis() - topic.getLastHit();
                    debug(INFO, "deltaLastHit for " + topic.getNum() + ": " + deltaLastHit);

                    if (deltaLastHit <= threadRefreshRate) { topic.lock.writeLock().unlock();  continue; }

                    topic.setBusy(true);
                    try {
                        newTopics.put(topic.getNum());
                    } catch (InterruptedException e) { }

                    topic.lock.writeLock().unlock();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) { }
            }
        }
    }
}
