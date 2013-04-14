package net.easymodo.asagi.model;

public class MediaPost {
    private int num;
    private boolean op;
    private String preview;
    private String media;
    private String mediaHash;

    public MediaPost(int num, boolean op, String preview, String media, String mediaHash) {
        this.num = num;
        this.op = op;
        this.preview = preview;
        this.media = media;
        this.mediaHash = mediaHash;
    }
    
    public int getNum() {
        return num;
    }
    public void setNum(int num) {
        this.num = num;
    }
    public String getPreview() {
        return preview;
    }
    public void setPreview(String preview) {
        this.preview = preview;
    }
    public String getMedia() {
        return media;
    }
    public void setMedia(String media) {
        this.media = media;
    }
    public String getMediaHash() {
        return mediaHash;
    }
    public void setMediaHash(String mediaHash) {
        this.mediaHash = mediaHash;
    }
    public boolean isOp() {
        return op;
    }
    public void setOp(boolean op) {
        this.op = op;
    }
}
