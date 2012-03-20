package net.easymodo.asagi;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.easymodo.asagi.settings.BoardSettings;

public class Local extends Board {
    private final String path;
    private final boolean fullMedia;
    
    public Local(String path, BoardSettings info) {
        this.path = path;
        this.fullMedia = info.getFullMedia();
    }
    
    @Override
    public Page getPage(int pageNum, String lastMod) {
        // TODO Unimplemented
        return null;
    }
    
    @Override
    public Topic getThread(int threadNum, String lastMod) {
        // TODO Unimplemented
        return null;
    }  
    
    @Override
    public byte[] getMediaPreview(Post h) {
     // TODO Unimplemented
        return null;
    }
    
    @Override
    public byte[] getMedia(Post h) {
     // TODO Unimplemented
        return null;
    }
    
    public String[] getSubdirs(String num) {
        String patString = "(\\d+?)(\\d{2})\\d{0,3}$";      
        Pattern pat = Pattern.compile(patString);
        Matcher mat = pat.matcher(num);
        mat.find();
        String subdir = String.format("%04d", Integer.parseInt(mat.group(1)));
        String subdir2 = String.format("%02d", Integer.parseInt(mat.group(2)));
        return new String[] {subdir, subdir2};
    }
    
    public String[] getDirs(String num) {
        String[] subdirs = getSubdirs(num);
        String thumbDir = String.format("%s/thumb/%s/%s", this.path, subdirs[0], subdirs[1]);
        String mediaDir = String.format("%s/img/%s/%s", this.path, subdirs[0], subdirs[1]);

        return new String[] {thumbDir, mediaDir};
    }
    
    public String[] makeDirs(int num) {
        return this.makeDirs(num, this.path);
    }
    
    public String[] makeDirs(int num, String path) {
        String[] subdirs = this.getSubdirs(new Integer(num).toString());
        
        File thumbSubDirFile = new File(String.format("%s/thumb/%s", this.path, subdirs[0]));
        File thumbSubDir2File =  new File(String.format("%s/thumb/%s/%s", this.path, subdirs[0], subdirs[1]));
        thumbSubDir2File.mkdirs();
        // TODO: chmod + chown
        if(this.fullMedia) {
            File imgSubDirFile = new File(String.format("%s/img/%s", this.path, subdirs[0]));
            File imgSubDir2File =  new File(String.format("%s/img/%s/%s", this.path, subdirs[0], subdirs[1]));
            imgSubDir2File.mkdirs();
            // TODO: chmod + chown
        }
        
        return this.getDirs(new Integer(num).toString());
    }
    
    public int insertMediaPreview(Post h, Board source) throws ContentGetException {
        String[] mediaDirs = makeDirs(h.getParent() == 0 ? h.getNum() : h.getParent());
        String thumbDir = mediaDirs[0];
        
        if(h.getPreview() == null) return 0;
        
        // Construct the path and back down if the file already exists
        File thumbFile = new File(thumbDir + "/" + h.getPreview());
        if(thumbFile.exists()) return 1;
        
        byte[] data = source.getMediaPreview(h);
        
        try {
            OutputStream outFile = new BufferedOutputStream(new FileOutputStream(thumbFile));
            outFile.write(data);
            outFile.close();
            // TODO: chown and chmod that shit yo
        } catch(FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch(IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return 1;
    }
    
    public int insertMedia(Post h, Board source) throws ContentGetException {
        String[] mediaDirs = makeDirs(h.getParent() == 0 ? h.getNum() : h.getParent());
        String mediaDir = mediaDirs[1];
        
        if(h.getMediaFilename() == null) return 0;
        
        // Construct the path and back down if the file already exists
        File mediaFile = new File(mediaDir + "/" + h.getMediaFilename());
        if(mediaFile.exists()) return 1;
        
        // Throws ContentGetException on failure
        byte[] data = source.getMedia(h);
        
        try {
            OutputStream outFile = new BufferedOutputStream(new FileOutputStream(mediaFile));
            outFile.write(data);
            outFile.close();
            // TODO: chown and chmod that shit yo
        } catch(FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch(IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return 1;
    }
}
