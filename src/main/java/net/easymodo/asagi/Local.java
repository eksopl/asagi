package net.easymodo.asagi;

import java.io.File;

public class Local extends Board {
    @Override
    public byte[] getMediaPreview(Post h) {
        // later
        return new byte[1];
    }
    
    public String makeDirs(int num) {
        // TODO
        return "";
    }
    
    public int insertMediaPreview(Post h, Board source) {
        String thumbDir = makeDirs(h.getParent() == 0 ? h.getNum() : h.getParent());
        
        if(h.getPreview() == null) return 0;
        File f = new File(thumbDir + "/" + h.getPreview());
        if(!f.exists()) return 1;
        
        return 1;
    }    
}
