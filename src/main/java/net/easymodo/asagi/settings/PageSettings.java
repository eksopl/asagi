package net.easymodo.asagi.settings;

import java.util.List;

public class PageSettings {
    private int delay;
    private List<Integer> pages;
    
    public int getDelay() {
        return delay;
    }
    public void setDelay(int delay) {
        this.delay = delay;
    }
    public List<Integer> getPages() {
        return pages;
    }
    public void setPages(List<Integer> pages) {
        this.pages = pages;
    }
}
