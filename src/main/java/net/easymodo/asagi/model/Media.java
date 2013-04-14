package net.easymodo.asagi.model;

public class Media {
	private int mediaId;
    private String mediaHash;
    private String media;
    private String previewOp;
    private String previewReply;
    private int total;
    private int banned;
    
    public Media(int mediaId, String mediaHash, String media, 
            String previewOp, String previewReply, int total, int banned) {
    	setMediaId(mediaId);
    	setMediaHash(mediaHash);
    	setMedia(media);
    	setPreviewOp(previewOp);
    	setPreviewReply(previewReply);
    	setTotal(total);
    	setBanned(banned);
    }
    
    public int getMediaId() {
    	return mediaId;
    }
    
    public void setMediaId(int mediaId) {
    	this.mediaId = mediaId;
    }
    
    public String getMediaHash() {
    	return mediaHash;
    }
    
    public void setMediaHash(String mediaHash) {
    	this.mediaHash = mediaHash;
    }
    
    public String getMedia() {
    	return media;
    }
    
    public void setMedia(String media) {
    	this.media = media;
    }
    
    public String getPreviewOp() {
    	return previewOp;
    }
    
    public void setPreviewOp(String previewOp) {
    	this.previewOp = previewOp;
    }
    
    public String getPreviewReply() {
    	return previewReply;
    }
    
    public void setPreviewReply(String previewReply) {
    	this.previewReply = previewReply;
    }
    
    public int getTotal() {
    	return total;
    }
    
    public void setTotal(int total) {
    	this.total = total;
    }
    
    public int getBanned() {
    	return banned;
    }
    
    public void setBanned(int banned) {
    	this.banned = banned;
    }
}
