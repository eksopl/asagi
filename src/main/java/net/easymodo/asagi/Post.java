package net.easymodo.asagi;

public class Post {
    private int id;
    private int num;
    private int subnum;
    private int parent;
    private int date;
    private String preview;
    private int previewW;
    private int previewH;
    private int mediaId;
    private String media;
    private int mediaW;
    private int mediaH;
    private int mediaSize;
    private String mediaHash;
    private String origFilename;
    private boolean spoiler;
    private boolean deleted;
    private String capcode;
    private String email;
    private String name;
    private String trip;
    private String title;
    private String comment;
    private String delpass;
    private boolean sticky;
    
    private String link;
    private String type;
    private boolean omitted;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int getSubnum() {
        return subnum;
    }

    public void setSubnum(int subnum) {
        this.subnum = subnum;
    }

    public int getParent() {
        return parent;
    }

    public void setParent(int parent) {
        this.parent = parent;
    }

    public int getDate() {
        return date;
    }

    public void setDate(int date) {
        this.date = date;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public int getPreviewW() {
        return previewW;
    }

    public void setPreviewW(int previewW) {
        this.previewW = previewW;
    }

    public int getPreviewH() {
        return previewH;
    }

    public void setPreviewH(int previewH) {
        this.previewH = previewH;
    }
    
    public int getMediaId() {
    	return mediaId;
    }
    
    public void setMediaId(int mediaId) {
    	this.mediaId = mediaId;
    }

    public String getMedia() {
        return media;
    }

    public void setMedia(String media) {
        this.media = media;
    }

    public int getMediaW() {
        return mediaW;
    }

    public void setMediaW(int mediaW) {
        this.mediaW = mediaW;
    }

    public int getMediaH() {
        return mediaH;
    }

    public void setMediaH(int mediaH) {
        this.mediaH = mediaH;
    }

    public int getMediaSize() {
        return mediaSize;
    }

    public void setMediaSize(int mediaSize) {
        this.mediaSize = mediaSize;
    }

    public String getMediaHash() {
        return mediaHash;
    }

    public void setMediaHash(String mediaHash) {
        this.mediaHash = mediaHash;
    }

    public String getOrigFilename() {
        return origFilename;
    }

    public void setOrigFilename(String origFilename) {
        this.origFilename = origFilename;
    }

    public boolean isSpoiler() {
        return spoiler;
    }

    public void setSpoiler(boolean spoiler) {
        this.spoiler = spoiler;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getCapcode() {
        return capcode;
    }

    public void setCapcode(String capcode) {
        this.capcode = capcode;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTrip() {
        return trip;
    }

    public void setTrip(String trip) {
        this.trip = trip;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getDelpass() {
        return delpass;
    }

    public void setDelpass(String delpass) {
        this.delpass = delpass;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isOmitted() {
        return omitted;
    }

    public void setOmitted(boolean omitted) {
        this.omitted = omitted;
    }
}
