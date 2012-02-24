package net.easymodo.asagi;


/*
 * Implements Board::Request classes
 */
public class Request {
    public class Thread {
        private int threadNum;
        
        public Thread(int threadNum) {
            this.threadNum = threadNum;
        }
        
        public int getThreadNum() {
            return this.threadNum;
        }
        
    }
    
    public class Page {
        private int pageNum;
        
        public Page(int pageNum) {
            this.pageNum = pageNum;
        }
        
        public int getPageNum() {
            return this.pageNum;
        }
    }
}
