package net.easymodo.asagi;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final boolean useOldDirectoryStructure;
    private final int webGroupId;
        
    private final static int DIR_THUMB = 1;
    private final static int DIR_MEDIA = 2;
    
    private final static Posix posix;
    private DB db;
    
    private final static Pattern oldDirectoryMatchingPattern = Pattern.compile("(\\d+?)(\\d{2})\\d{0,3}$");
    
    static {        
        if(Platform.isWindows()) {
          posix = null;
        } else {
          posix = (Posix)Native.loadLibrary("c", Posix.class);
        } 
    }
    
    public Local(String path, BoardSettings info, DB db) {
        this.path = path;
        this.useOldDirectoryStructure = info.getUseOldDirectoryStructure();
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
    
    public String[] getSubdirs(MediaPost h) {
        Matcher mat = oldDirectoryMatchingPattern.matcher(Integer.toString(h.getNum()));
        mat.find();
            
        String subdir = String.format("%04d", Integer.parseInt(mat.group(1)));
        String subdir2 = String.format("%02d", Integer.parseInt(mat.group(2)));
        
        return new String[] {subdir, subdir2};
    }
    
    public String getDir(String[] subdirs, int dirType) {
        if(dirType == DIR_THUMB) {
            return String.format("%s/thumb/%s/%s", this.path, subdirs[0], subdirs[1]);
        } else if(dirType == DIR_MEDIA) {
            return String.format("%s/image/%s/%s", this.path, subdirs[0], subdirs[1]);
        } else {
            return null;
        }
    }
    
    public String makeDir(String filename, int dirType) throws ContentStoreException {
        String[] subdirs = this.getSubdirs(filename);
        return this.makeDir(subdirs, this.path, dirType);
    }
    
    public String makeDir(MediaPost h, int dirType) throws ContentStoreException {
        String[] subdirs = this.getSubdirs(h);
        return this.makeDir(subdirs, this.path, dirType);
    }
    
    public String makeDir(String[] subdirs, String path, int dirType) throws ContentStoreException {
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
                
        return this.getDir(subdirs, dirType);
    }
    
    public void insert(Topic topic) throws ContentStoreException, DBConnectionException {
        this.db.insert(topic);
    }
    
    public void markDeleted(int post) throws ContentStoreException {
        try{
            this.db.markDeleted(post);
        } catch(DBConnectionException e) {
            throw new ContentStoreException("Lost connection to database, can't reconnect", e);
        }
    }
    
    public void insertMediaPreview(MediaPost h, Board source) throws ContentGetException, ContentStoreException {
        this.insertMedia(h, source, true);
    }
    
    public void insertMedia(MediaPost h, Board source) throws ContentGetException, ContentStoreException {
        this.insertMedia(h, source, false);
    }
    
    public void insertMedia(MediaPost h, Board source, boolean preview) throws ContentGetException, ContentStoreException {
        // Post has no media
        if((preview && h.getPreview() == null) || (!preview && h.getMedia() == null))
            return;
        
        Media mediaRow = null;
        try {
            mediaRow = db.getMedia(h);
        } catch(DBConnectionException e) { 
            throw new ContentStoreException("Lost connection to database, can't reconnect", e);
        }
        
        // Media is banned from archiving
        if(mediaRow.getBanned() == 1) return;
        
        // Get the proper filename for the file type we're outputting
        String filename;
        if(this.useOldDirectoryStructure)
            filename =  preview ? h.getPreview() : h.getMedia();
        else
            filename = preview ? (h.isOp() ?  mediaRow.getPreviewOp() : mediaRow.getPreviewReply()) :
                mediaRow.getMedia();
        
        if(filename == null) return;
        
        // Create the dir structure (if necessary) and return the path to where we're outputting our file
        // Filename is enough for us here, we just need the first part of the string
        String outputDir;
        if(this.useOldDirectoryStructure)
            outputDir = makeDir(h, preview ? DIR_THUMB : DIR_MEDIA);
        else
            outputDir = makeDir(filename, preview ? DIR_THUMB : DIR_MEDIA);

        // Construct the path and back down if the file already exists
        File outputFile = new File(outputDir + "/" + filename);
        if(outputFile.exists()) return;
        
        // Open a temp file for writing
        String tempFilePath = outputDir + "/" + filename + ".tmp";
        File tempFile = new File(tempFilePath);
        if(tempFile.exists()) return;
        
        // Throws ContentGetException on failure
        InputStream inStream = preview ? source.getMediaPreview(h) : source.getMedia(h);
        
        OutputStream outFile = null;
        try {
            outFile = new BufferedOutputStream(new FileOutputStream(tempFile));
     
            // Copy the network input stream to our local file
            // In case the connection is cut off or something similar happens, an IOException
            // will be thrown.
            ByteStreams.copy(inStream, outFile);
            
        } catch(FileNotFoundException e) {
            throw new ContentStoreException("The temp file we just created wasn't there!! (BUG: RACE CONDITION)", e);
        } catch(IOException e) {
            if(!tempFile.delete())
                throw new ContentStoreException("Additionally, temporary file " + tempFilePath + "could not be deleted.", e);
            throw new ContentStoreException("IOException in file download", e);
        } finally {
            try {
                if(outFile != null) outFile.close();
                inStream.close();
            } catch(IOException e) {
                throw new ContentStoreException("IOException trying to close streams after file download", e);
            }
        }
            
        // Move the temporary file into place
        if(!tempFile.renameTo(outputFile))
            throw new ContentStoreException("Unable to move temporary file " + tempFilePath + " into place");
        
        try {    
            if(this.webGroupId != 0) {
                posix.chmod(outputFile.getCanonicalPath(), 0664);
                posix.chown(outputFile.getCanonicalPath(), -1, this.webGroupId);
            }
        } catch(IOException e) {
            throw new ContentStoreException("IOException trying to get filename for output file (nice broken filesystem you have there)", e);
        }
    }
}

