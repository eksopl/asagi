package net.easymodo.asagi;


/*
 * Implements Board::Request classes
 */
public class Request {    
    public class Thread {
        private int threadNum;
        private int lastMod;
        
        public Thread(int threadNum) {
            this.threadNum = threadNum;
        }
        
        public int getThreadNum() {
            return this.threadNum;
        }
        
        public int getLastMod() {
            return lastMod;
        }

        public void setLastMod(int lastMod) {
            this.lastMod = lastMod;
        }
    }
    
    public class Page {
        private int pageNum;
        private int lastMod;
        
        public Page(int pageNum) {
            this.pageNum = pageNum;
        }
        
        public int getPageNum() {
            return this.pageNum;
        }

        public void setPageNum(int pageNum) {
            this.pageNum = pageNum;
        }

        public int getLastMod() {
            return lastMod;
        }

        public void setLastMod(int lastMod) {
            this.lastMod = lastMod;
        }
    }
    
    public static Request.Thread thread(int threadNum) {
        return new Request().new Thread(threadNum);
    }
    
    public static Request.Page page(int pageNum) {
        return new Request().new Page(pageNum);
    }
}
