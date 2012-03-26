package net.easymodo.asagi;


/*
 * Implements Board::Request classes
 */
public class Request {    
    public static class Thread {
        private int threadNum;
        private String lastMod;
        
        public Thread(int threadNum) {
            this.threadNum = threadNum;
        }
        
        public Thread(int threadNum, String lastMod) {
            this.threadNum = threadNum;
            this.lastMod = lastMod;
        }
        
        public int getThreadNum() {
            return this.threadNum;
        }
        
        public String getLastMod() {
            return lastMod;
        }

        public void setLastMod(String lastMod) {
            this.lastMod = lastMod;
        }
    }
    
    public static class Page {
        private int pageNum;
        private String lastMod;
        
        public Page(int pageNum) {
            this.pageNum = pageNum;
        }
        
        public Page(int pageNum, String lastMod) {
            this.pageNum = pageNum;
            this.lastMod = lastMod;
        }
        
        public int getPageNum() {
            return this.pageNum;
        }

        public void setPageNum(int pageNum) {
            this.pageNum = pageNum;
        }

        public String getLastMod() {
            return lastMod;
        }

        public void setLastMod(String lastMod) {
            this.lastMod = lastMod;
        }
    }
    
    public static Request.Thread thread(int threadNum) {
        return new Thread(threadNum);
    }
    
    public static Request.Thread thread(int threadNum, String lastMod) {
        return new Thread(threadNum, lastMod);
    }
    
    public static Request.Page page(int pageNum) {
        return new Page(pageNum);
    }
    
    public static Request.Page page(int pageNum, String lastMod) {
        return new Page(pageNum, lastMod);
    }
}
