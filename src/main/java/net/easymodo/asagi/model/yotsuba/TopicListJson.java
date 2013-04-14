package net.easymodo.asagi.model.yotsuba;


public class TopicListJson {
    public class Topic {
        private int no;
        private long lastModified;

        public int getNo() {
            return no;
        }

        public void setNo(int no) {
            this.no = no;
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }
    }

    public class Page {
        private int page;
        private Topic[] threads;

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public Topic[] getThreads() {
            return threads;
        }

        public void setThreads(Topic[] threads) {
            this.threads = threads;
        }
    }
}
