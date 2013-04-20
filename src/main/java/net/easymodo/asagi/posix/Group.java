package net.easymodo.asagi.posix;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("UnusedDeclaration")
public class Group extends Structure {
    public String name;
    public String password;
    public int gid;
    public Pointer mem;
    
    public Group() {}

    public String getName() {
        return name;
    }
    
    public String getPassword() {
        return password;
    }
    
    public long getGid() {
        return gid;
    }
    
    public String[] getMembers() {
        // Don't really care about this
        // Can be implemented later if it's ever needed
        throw new UnsupportedOperationException();
   }

    @Override
    protected List getFieldOrder() {
        return Arrays.asList("name", "password", "gid", "mem");
    }
}
