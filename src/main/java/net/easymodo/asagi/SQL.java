package net.easymodo.asagi;

import java.sql.SQLException;

import net.easymodo.asagi.exception.ContentGetException;

public interface SQL {
    public void insert(Topic topic) throws SQLException;
    public Media getMediaRow(Post post) throws SQLException, ContentGetException;
}
