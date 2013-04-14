package net.easymodo.asagi.posix;

import com.sun.jna.Library;

public interface Posix extends Library {
    int chmod(String path, int mode);
    int chown(String filename, int owner, int group);
    Group getgrnam(String groupName);
}