package net.easymodo.asagi.model.yotsuba;

@SuppressWarnings("UnusedDeclaration")
public class PageJson {
    private TopicJson[] threads;

    public TopicJson[] getThreads() {
        return threads;
    }

    public void setThreads(TopicJson[] threads) {
        this.threads = threads;
    }
}
