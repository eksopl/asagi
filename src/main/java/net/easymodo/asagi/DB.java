package net.easymodo.asagi;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentStoreException;
import net.easymodo.asagi.exception.DBConnectionException;
import net.easymodo.asagi.model.DeletePost;
import net.easymodo.asagi.model.Media;
import net.easymodo.asagi.model.MediaPost;
import net.easymodo.asagi.model.Topic;
import net.easymodo.asagi.settings.BoardSettings;

public interface DB {
    void init(String connStr, String path, BoardSettings info) throws BoardInitException;
    void insert(Topic topic) throws ContentStoreException, DBConnectionException;
    Media getMedia(MediaPost h) throws ContentGetException, ContentStoreException, DBConnectionException;
    void markDeleted(DeletePost post) throws ContentStoreException, DBConnectionException;
}
