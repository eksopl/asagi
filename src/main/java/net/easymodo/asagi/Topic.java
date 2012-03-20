package net.easymodo.asagi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Topic {
    private int num;
    private int omPosts;
    private int omImages;
    private String lastMod;
    private List<Post> posts;
    private int lastPage;
    private long lastHit;
    private boolean busy;
    public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public Topic(int num, int omPosts, int omImages) {
        this.num = num;
        this.omPosts = omPosts;
        this.omImages = omImages;
        this.posts = new ArrayList<Post>();
        this.lastPage = 0;
        this.lastHit = 0;
        this.busy = false;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int getOmPosts() {
        return omPosts;
    }

    public void setOmPosts(int omPosts) {
        this.omPosts = omPosts;
    }

    public int getOmImages() {
        return omImages;
    }

    public void setOmImages(int omImages) {
        this.omImages = omImages;
    }

    public List<Post> getPosts() {
        return posts;
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts;
    }
    
    public void addPost(Post post) {
        this.posts.add(post);
    }
    
    public Post findPost(int num) {
        for(Post post : this.posts) {
            if(post.getNum() == num)
                return post;
        }
        return null;
    }

    public void setLastMod(String lastMod) {
        this.lastMod = lastMod;
    }

    public String getLastMod() {
        return lastMod;
    }
    
    public void setLastHit(long lastHit) {
        this.lastHit = lastHit;
    }

    public long getLastHit() {
        return lastHit;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public boolean isBusy() {
        return busy;
    }

    public int getLastPage() {
        return lastPage;
    }

    public void setLastPage(int lastPage) {
        this.lastPage = lastPage;
    }
}
