package net.easymodo.asagi.posix;

import com.sun.jna.Library;

public interface Posix extends Library {
    public int chmod(String path, int mode);
    public int chown(String filename, int owner, int group);
    public Group getgrnam(String groupName);
}