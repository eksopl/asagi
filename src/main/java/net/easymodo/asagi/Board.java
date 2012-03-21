package net.easymodo.asagi;

import net.easymodo.asagi.exception.*;

public abstract class Board {
    public abstract byte[] getMediaPreview(Post h) throws ContentGetException;
    public abstract byte[] getMedia(Post h) throws ContentGetException;
    public abstract Page getPage(int pageNum, String lastMod) throws ContentGetException, ContentParseException;
    public abstract Topic getThread(int threadNum, String lastMod) throws ContentGetException, ContentParseException;
    
    public Page content(Request.Page pageReq) throws ContentGetException, ContentParseException {
        return this.getPage(pageReq.getPageNum(), pageReq.getLastMod());
    }
    
    public Topic content(Request.Thread threadReq) throws ContentGetException, ContentParseException {
        return this.getThread(threadReq.getThreadNum(), threadReq.getLastMod());
    }
}
