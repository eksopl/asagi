package net.easymodo.asagi;

import java.util.ArrayList;
import java.util.List;

public class Page {
    private int num;
    private List<Topic> threads;
    
    public Page(int num) {
        this.num = num;
        this.threads = new ArrayList<Topic>();
    }
    
    public int getNum() {
        return num;
    }
    
    public void setNum(int num) {
        this.num = num;
    }
    
    public List<Topic> getThreads() {
        return threads;
    }
    
    public void setThreads(List<Topic> threads) {
        this.threads = threads;
    }
    
    public void addThread(Topic thread) {
      threads.add(thread);
    }
}
