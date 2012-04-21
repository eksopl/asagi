package net.easymodo.asagi;

import java.io.InputStream;

import net.easymodo.asagi.exception.*;

public abstract class Board {
    public abstract InputStream getMediaPreview(MediaPost h) throws ContentGetException;
    public abstract InputStream getMedia(MediaPost h) throws ContentGetException;
    public abstract Page getPage(int pageNum, String lastMod) throws ContentGetException, ContentParseException;
    public abstract Topic getThread(int threadNum, String lastMod) throws ContentGetException, ContentParseException;
    
    public Page content(Request.Page pageReq) throws ContentGetException, ContentParseException {
        return this.getPage(pageReq.getPageNum(), pageReq.getLastMod());
    }
    
    public Topic content(Request.Thread threadReq) throws ContentGetException, ContentParseException {
        return this.getThread(threadReq.getThreadNum(), threadReq.getLastMod());
    }
}
