package net.easymodo.asagi;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentStoreException;
import net.easymodo.asagi.settings.BoardSettings;

public interface DB {
    public void init(String connStr, String path, BoardSettings info) throws BoardInitException;
    public void insert(Topic topic) throws ContentStoreException;
    public Media getMedia(Post post) throws ContentGetException;
}
