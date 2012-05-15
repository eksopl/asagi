package net.easymodo.asagi;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.annotation.ThreadSafe;

import com.google.common.io.ByteStreams;
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
    private DB db;
    
    static {
        if(Platform.isWindows()) {
          posix = null;
        } else {
          posix = (Posix)Native.loadLibrary("c", Posix.class);
        } 
    }
    
    public Local(String path, BoardSettings info, DB db) {
        this.path = path;
        this.db = db;
        
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
    public InputStream getMediaPreview(MediaPost h) {
        // Unimplemented
        throw new UnsupportedOperationException();
    }
    
    @Override
    public InputStream getMedia(MediaPost h) {
        // Unimplemented
        throw new UnsupportedOperationException();
    }
    
    public String[] getSubdirs(String filename) {
    	String subdir = filename.substring(0, 4);
    	String subdir2 = filename.substring(4, 6);
        
        return new String[] {subdir, subdir2};
    }
    
    public String getDir(String filename, int dirType) {
        String[] subdirs = getSubdirs(filename);
        
        if(dirType == DIR_THUMB) {
            return String.format("%s/thumb/%s/%s", this.path, subdirs[0], subdirs[1]);
        } else if(dirType == DIR_MEDIA) {
            return String.format("%s/image/%s/%s", this.path, subdirs[0], subdirs[1]);
        } else {
            return null;
        }
    }
    
    public String makeDir(String filename, int dirType) throws ContentStoreException {
        return this.makeDir(filename, this.path, dirType);
    }
    
    public String makeDir(String filename, String path, int dirType) throws ContentStoreException {
        String[] subdirs = this.getSubdirs(filename);
        
        String dir;
        if(dirType == DIR_THUMB) {
            dir = "thumb";
        } else if(dirType == DIR_MEDIA) {
            dir = "image";
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
                
        return this.getDir(filename, dirType);
    }
    
    public void insert(Topic topic) throws ContentStoreException {
        this.db.insert(topic);
    }
    
    public void markDeleted(int post) throws ContentStoreException {
        this.db.markDeleted(post);
    }
    
    public void insertMediaPreview(MediaPost h, Board source) throws ContentGetException, ContentStoreException {
        if(h.getPreviewFilename() == null) return;
        Media mediaRow = db.getMedia(h);
        if(mediaRow.getBanned() == 1) return;
        String filename = h.isOp() ?  mediaRow.getPreviewOp() : mediaRow.getPreviewReply();
        
        if(filename == null) return;
        
        String thumbDir = makeDir(filename, DIR_THUMB);
        
        // Construct the path and back down if the file already exists
        File thumbFile = new File(thumbDir + "/" + h.getPreviewFilename());
        if(thumbFile.exists()) return;
        
        InputStream inStream = source.getMediaPreview(h);
        
        OutputStream outFile = null;
        try {
            outFile = new BufferedOutputStream(new FileOutputStream(thumbFile));
        } catch(FileNotFoundException e) {
            throw new ContentStoreException(e);
        }
        
        try{
            ByteStreams.copy(inStream, outFile);
            inStream.close();
            
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
                inStream.close();
                outFile.close();
            } catch(IOException e) {
                throw new ContentStoreException(e);
            }
        }
    }
    
    public void insertMedia(MediaPost h, Board source) throws ContentGetException, ContentStoreException {
        if(h.getMediaFilename() == null) return;
        Media mediaRow = db.getMedia(h); 
        if(mediaRow.getBanned() == 1) return;
        String filename = mediaRow.getMedia();
        
        if(filename == null) return;
        
        // Preview filename is enough for us here, we just need the first part of the string
        String mediaDir = makeDir(filename, DIR_MEDIA);

        // Construct the path and back down if the file already exists
        File mediaFile = new File(mediaDir + "/" + filename);
        if(mediaFile.exists()) return;
        
        // Throws ContentGetException on failure
        InputStream inStream = source.getMedia(h);
        
        OutputStream outFile = null;
        try {
            outFile = new BufferedOutputStream(new FileOutputStream(mediaFile));
        } catch(FileNotFoundException e) {
            throw new ContentStoreException(e);
        }
        
        try{
            ByteStreams.copy(inStream, outFile);
            
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
                inStream.close();
                outFile.close();
            } catch(IOException e) {
                throw new ContentStoreException(e);
            }
        }        
    }
}
