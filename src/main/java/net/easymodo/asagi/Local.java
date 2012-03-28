package net.easymodo.asagi;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.annotation.ThreadSafe;

import com.sun.jna.Native;
import com.sun.jna.Platform;

import net.easymodo.asagi.settings.BoardSettings;
import net.easymodo.asagi.exception.*;
import net.easymodo.asagi.posix.*;

@ThreadSafe
public class Local extends Board {
    private final String path;
    private final int webGroupId;
    
    private final static int DIR_THUMB = 1;
    private final static int DIR_MEDIA = 2;
    
    private final static Posix posix;
    
    static {
        if(Platform.isWindows()) {
          posix = null;
        } else {
          posix = (Posix)Native.loadLibrary("c", Posix.class);
        } 
    }
    
    public Local(String path, BoardSettings info) {
        this.path = path;
        
        // getgrnam is thread-safe on sensible OSes, but it's not thread safe
        // on most ones.
        // Performing the gid lookup in the constructor and calling chmod and
        // chown from the C library (which are reentrant functions) keeps this
        // class thread-safe.
        String webServerGroup = info.getWebserverGroup();
        if(webServerGroup != null && posix != null) {
            Group group = posix.getgrnam(webServerGroup);
            if(group == null)
                webGroupId = 0;
            else
                webGroupId = (int)group.getGid();
        } else {
            webGroupId = 0;
        }
    }
    
    @Override
    public Page getPage(int pageNum, String lastMod) {
        // Unimplemented
        throw new UnsupportedOperationException();
    }
    
    @Override
    public Topic getThread(int threadNum, String lastMod) {
        // Unimplemented
        throw new UnsupportedOperationException();
    }  
    
    @Override
    public byte[] getMediaPreview(Post h) {
        // Unimplemented
        throw new UnsupportedOperationException();
    }
    
    @Override
    public byte[] getMedia(Post h) {
        // Unimplemented
        throw new UnsupportedOperationException();
    }
    
    public String[] getSubdirs(int num) {
        String patString = "(\\d+?)(\\d{2})\\d{0,3}$";      
        Pattern pat = Pattern.compile(patString);
        Matcher mat = pat.matcher(Integer.toString(num));
        mat.find();
        String subdir = String.format("%04d", Integer.parseInt(mat.group(1)));
        String subdir2 = String.format("%02d", Integer.parseInt(mat.group(2)));
        
        return new String[] {subdir, subdir2};
    }
    
    public String getDir(int num, int dirType) {
        String[] subdirs = getSubdirs(num);
        
        if(dirType == DIR_THUMB) {
            return String.format("%s/thumb/%s/%s", this.path, subdirs[0], subdirs[1]);
        } else if(dirType == DIR_MEDIA) {
            return String.format("%s/img/%s/%s", this.path, subdirs[0], subdirs[1]);
        } else {
            return null;
        }
    }
    
    public String makeDir(int num, int dirType) throws ContentStoreException {
        return this.makeDir(num, this.path, dirType);
    }
    
    public String makeDir(int num, String path, int dirType) throws ContentStoreException {
        String[] subdirs = this.getSubdirs(num);
        
        String dir;
        if(dirType == DIR_THUMB) {
            dir = "thumb";
        } else if(dirType == DIR_MEDIA) {
            dir = "img";
        } else {
            return null;
        }
        
        String subDir = String.format("%s/%s/%s", this.path, dir, subdirs[0]);
        String subDir2 = String.format("%s/%s/%s/%s", this.path, dir, subdirs[0], subdirs[1]);
        File subDir2File =  new File(subDir2);
        
        synchronized(this) {
            if(!subDir2File.exists())
                if(!subDir2File.mkdirs())
                    throw new ContentStoreException("Could not create dirs at path " + subDir2);
            
            if(this.webGroupId != 0) {
                posix.chmod(subDir, 0775);
                posix.chmod(subDir2, 0775);
                posix.chown(subDir, -1, this.webGroupId);
                posix.chown(subDir2, -1, this.webGroupId);
            }
        }
                
        return this.getDir(num, dirType);
    }
    
    public int insertMediaPreview(Post h, Board source) throws ContentGetException, ContentStoreException {
        String thumbDir = makeDir(h.getParent() == 0 ? h.getNum() : h.getParent(), DIR_THUMB);
        
        if(h.getPreview() == null) return 0;
        
        // Construct the path and back down if the file already exists
        File thumbFile = new File(thumbDir + "/" + h.getPreview());
        if(thumbFile.exists()) return 1;
        
        byte[] data = source.getMediaPreview(h);
        
        OutputStream outFile = null;
        try {
            outFile = new BufferedOutputStream(new FileOutputStream(thumbFile));
        } catch(FileNotFoundException e) {
            throw new ContentStoreException(e);
        }
        
        try{
            outFile.write(data);
            
            if(this.webGroupId != 0) {
                posix.chmod(thumbFile.getCanonicalPath(), 0664);
                posix.chown(thumbFile.getCanonicalPath(), -1, this.webGroupId);
            }
        } catch(FileNotFoundException e) {
            throw new ContentStoreException(e);
        } catch(IOException e) {
            throw new ContentStoreException(e);
        } finally {
            try {
                outFile.close();
            } catch(IOException e) {
                throw new ContentStoreException(e);
            }
        }
        
        return 1;
    }
    
    public int insertMedia(Post h, Board source) throws ContentGetException, ContentStoreException {
        String mediaDir = makeDir(h.getParent() == 0 ? h.getNum() : h.getParent(), DIR_MEDIA);
        
        if(h.getMediaFilename() == null) return 0;
        
        // Construct the path and back down if the file already exists
        File mediaFile = new File(mediaDir + "/" + h.getMediaFilename());
        if(mediaFile.exists()) return 1;
        
        // Throws ContentGetException on failure
        byte[] data = source.getMedia(h);
        
        OutputStream outFile = null;
        try {
            outFile = new BufferedOutputStream(new FileOutputStream(mediaFile));
        } catch(FileNotFoundException e) {
            throw new ContentStoreException(e);
        }
        
        try{
            outFile.write(data);
            
            if(this.webGroupId != 0) {
                posix.chmod(mediaFile.getCanonicalPath(), 0664);
                posix.chown(mediaFile.getCanonicalPath(), -1, this.webGroupId);
            }
        } catch(FileNotFoundException e) {
            throw new ContentStoreException(e);
        } catch(IOException e) {
            throw new ContentStoreException(e);
        } finally {
            try {
                outFile.close();
            } catch(IOException e) {
                throw new ContentStoreException(e);
            }
        }
        
        return 1;
    }
}
