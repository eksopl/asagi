package net.easymodo.asagi;

public class Media {
	private int id;
    private String mediaHash;
    private String mediaFilename;
    private String previewOp;
    private String previewReply;
    private int total;
    private int banned;
    
    public Media(int id, String mediaHash, String mediaFilename, 
    		String previewOp, String previewReply, int total, int banned) {
    	setId(id);
    	setMediaHash(mediaHash);
    	setMediaFilename(mediaFilename);
    	setPreviewOp(previewOp);
    	setPreviewReply(previewReply);
    	setTotal(total);
    	setBanned(banned);
    }
    
    public Media() {
    	
    }
    
    public int getId() {
    	return id;
    }
    
    public void setId(int id) {
    	this.id = id;
    }
    
    public String getMediaHash() {
    	return mediaHash;
    }
    
    public void setMediaHash(String mediaHash) {
    	this.mediaHash = mediaHash;
    }
    
    public String getMediaFilename() {
    	return mediaFilename;
    }
    
    public void setMediaFilename(String mediaFilename) {
    	this.mediaFilename = mediaFilename;
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
    
    public boolean getBanned() {
    	return banned == 1;
    }
    
    public void setBanned(int banned) {
    	this.banned = banned;
    }
}
