package net.easymodo.asagi;

public class MediaPost {
    private int num;
    private boolean op;
    private String previewFilename;
    private String mediaFilename;
    private String mediaHash;

    public MediaPost(int num, boolean op, String previewFilename, String mediaFilename, String mediaHash) {
        this.num = num;
        this.op = op;
        this.previewFilename = previewFilename;
        this.mediaFilename = mediaFilename;
        this.mediaHash = mediaHash;
    }
    
    public int getNum() {
        return num;
    }
    public void setNum(int num) {
        this.num = num;
    }
    public String getPreviewFilename() {
        return previewFilename;
    }
    public void setPreviewFilename(String previewFilename) {
        this.previewFilename = previewFilename;
    }
    public String getMediaFilename() {
        return mediaFilename;
    }
    public void setMediaFilename(String mediaFilename) {
        this.mediaFilename = mediaFilename;
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
