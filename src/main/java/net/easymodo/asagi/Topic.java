package net.easymodo.asagi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Topic {
    private int num;
    private int omposts;
    private int omimages;
    private String lastMod;
    private List<Post> posts;
    private long lastHit;
    public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public Topic(int num, int omposts, int omimages) {
        this.num = num;
        this.omposts = omposts;
        this.omimages = omimages;
        this.posts = new ArrayList<Post>();
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int getOmposts() {
        return omposts;
    }

    public void setOmposts(int omposts) {
        this.omposts = omposts;
    }

    public int getOmimages() {
        return omimages;
    }

    public void setOmimages(int omimages) {
        this.omimages = omimages;
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
}
