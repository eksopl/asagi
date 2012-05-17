package net.easymodo.asagi;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;

import org.apache.http.annotation.ThreadSafe;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentStoreException;
import net.easymodo.asagi.exception.DBConnectionException;
import net.easymodo.asagi.settings.BoardSettings;

@ThreadSafe
public abstract class SQL implements DB {
    protected String table = null;
    protected String charset = null;

    protected String connectionString = null;
    
    protected String tableCheckQuery = null;
    protected String insertQuery = null;
    protected String updateQuery = null;
    protected String updateDeletedQuery = null;
    protected String selectMediaQuery = null;
    protected String updateMediaQuery = null;
    protected String updatePreviewOpQuery = null;
    protected String updatePreviewReplyQuery = null;
    
    protected Connection conn = null;
    protected PreparedStatement tableChkStmt = null;
    protected PreparedStatement updateStmt = null;
    protected PreparedStatement insertStmt = null;
    protected PreparedStatement updateDeletedStmt = null;
    protected PreparedStatement selectMediaStmt = null;
    protected PreparedStatement updateMediaStmt = null;
    protected PreparedStatement updatePreviewOpStmt = null;
    protected PreparedStatement updatePreviewReplyStmt = null;
    
    protected String commonSqlRes = null;
    protected String boardSqlRes = null;
    protected String triggersSqlRes = null;
    
    private void reconnect() throws DBConnectionException {
        try {
            this.connect();
        } catch(SQLException e) {
            throw new DBConnectionException(e);
        }
    }
    
    private void connect() throws SQLException {
        conn = DriverManager.getConnection(connectionString);
        conn.setAutoCommit(false);
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        
        insertStmt = conn.prepareStatement(insertQuery);
        updateStmt = conn.prepareStatement(updateQuery);
        updateDeletedStmt = conn.prepareStatement(updateDeletedQuery);
        selectMediaStmt = conn.prepareStatement(selectMediaQuery);
        updateMediaStmt = conn.prepareStatement(updateMediaQuery);
        updatePreviewOpStmt = conn.prepareStatement(updatePreviewOpQuery);
        updatePreviewReplyStmt = conn.prepareStatement(updatePreviewReplyQuery);
    }
    
    public synchronized void init(String connStr, String path, BoardSettings info) throws BoardInitException {
        this.table = info.getTable();
        this.connectionString = connStr;
        
        this.commonSqlRes = "net/easymodo/asagi/sql/" + info.getEngine() + "/common.sql";
        this.boardSqlRes = "net/easymodo/asagi/sql/" + info.getEngine() + "/boards.sql";
        this.triggersSqlRes = "net/easymodo/asagi/sql/" + info.getEngine() + "/triggers.sql";

        if(this.insertQuery == null) {
            this.insertQuery = String.format(
                    "INSERT INTO %s" +
                    " (poster_ip, num, subnum, thread_num, op, timestamp, timestamp_expired, preview_orig, preview_w, preview_h, media_filename, " +
                    " media_w, media_h, media_size, media_hash, media_orig, spoiler, deleted, " +
                    " capcode, email, name, trip, title, comment, delpass, sticky, poster_hash, exif) " +
                    "  SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? " +
                    "  WHERE NOT EXISTS (SELECT 1 FROM %s WHERE num=? and subnum=?)", 
                    this.table, this.table);
        }
        this.updateQuery = 
                String.format("UPDATE %s SET comment = ?, deleted = ?, media_filename = COALESCE(?, media_filename)," +
                        "  sticky = (? OR sticky) WHERE num=? and subnum=?", table);
      
        this.updateDeletedQuery = String.format("UPDATE %s SET deleted = ? WHERE num = ? and subnum = ?", 
                this.table);
        this.selectMediaQuery = String.format("SELECT * FROM %s_images WHERE media_hash = ?", 
                this.table);
        this.updateMediaQuery = String.format("UPDATE %s_images SET media = ? WHERE media_hash = ?", 
               this.table);
        this.updatePreviewOpQuery = String.format("UPDATE %s_images SET preview_op = ? WHERE media_hash = ?", 
                this.table);
        this.updatePreviewReplyQuery = String.format("UPDATE %s_images SET preview_reply = ? WHERE media_hash = ?", 
                this.table);

        try {
            this.connect();
            tableChkStmt = conn.prepareStatement(tableCheckQuery);
            try {
                this.createTables();
            } catch(BoardInitException e) {
                conn.commit();
                throw e;
            } 
            tableChkStmt.close();
        } catch(SQLException e) {
            throw new BoardInitException(e);
        }   
    }
    
    public synchronized void createTables() throws BoardInitException, SQLException {
        ResultSet res = null;
        String commonSql = null;
        
        // Check if common stuff has already been created
        tableChkStmt.setString(1, "index_counters");
        res = tableChkStmt.executeQuery();
        try {
            if(!res.isBeforeFirst()) {
                // Query to create tables common to all boards
                try {
                    commonSql = Resources.toString(Resources.getResource(commonSqlRes), Charsets.UTF_8);
                } catch(IOException e) {
                    throw new BoardInitException(e);
                } catch(IllegalArgumentException e) {
                    throw new BoardInitException(e);
                }
            }
        } finally {
            res.close();
        }
        conn.commit();

        // Check if the tables for this board have already been created too
        // Bail out if yes
        tableChkStmt.setString(1, this.table);
        res = tableChkStmt.executeQuery();
        try {
            if(res.isBeforeFirst()) {
                conn.commit();
                return;
            }
        } finally {
            res.close();
        }
        conn.commit();

        // Query to create all tables for this board
        String boardSql;
        try {
            boardSql = Resources.toString(Resources.getResource(boardSqlRes), Charsets.UTF_8);
            boardSql = boardSql.replaceAll("%%BOARD%%", table);
            boardSql = boardSql.replaceAll("%%CHARSET%%", charset);
        } catch(IOException e) {
            throw new BoardInitException(e);
        } catch(IllegalArgumentException e) {
            throw new BoardInitException(e);
        }

        // Query to create or replace triggers and procedures for this board
        String triggersSql;
        try {
            triggersSql = Resources.toString(Resources.getResource(triggersSqlRes), Charsets.UTF_8);
            triggersSql = triggersSql.replaceAll("%%BOARD%%", table);
            triggersSql = triggersSql.replaceAll("%%CHARSET%%", charset);
        } catch(IOException e) {
            throw new BoardInitException(e);
        } catch(IllegalArgumentException e) {
            throw new BoardInitException(e);
        }
        
        Statement st = conn.createStatement();
        try {
            if(commonSql != null) 
                st.executeUpdate(commonSql);
            st.executeUpdate(boardSql);
            st.executeUpdate(triggersSql);
            conn.commit();
        } finally {
            st.close();
        }
    }
    
    public synchronized void insert(Topic topic) throws ContentStoreException, DBConnectionException {
        while(true) { try {
            for(Post post : topic.getPosts()) {
                int c = 1;
                updateStmt.setString(c++, post.getComment());
                updateStmt.setBoolean(c++, post.isDeleted());
                updateStmt.setString(c++,post.getMediaFilename());
                updateStmt.setBoolean(c++, post.isSticky());
                updateStmt.setInt(c++, post.getNum());
                updateStmt.setInt(c++, post.getSubnum());
                updateStmt.addBatch();

                c = 1;
                insertStmt.setInt(c++, post.getPosterIp());
                insertStmt.setInt(c++, post.getNum());
                insertStmt.setInt(c++, post.getSubnum());
                insertStmt.setInt(c++, post.getThreadNum());
                insertStmt.setBoolean(c++, post.isOp());
                insertStmt.setInt(c++, post.getDate());
                insertStmt.setInt(c++, post.getDateExpired());
                insertStmt.setString(c++, post.getPreviewOrig());
                insertStmt.setInt(c++, post.getPreviewW());
                insertStmt.setInt(c++, post.getPreviewH());
                insertStmt.setString(c++,post.getMediaFilename());
                insertStmt.setInt(c++, post.getMediaW());
                insertStmt.setInt(c++, post.getMediaH());
                insertStmt.setInt(c++, post.getMediaSize());
                insertStmt.setString(c++, post.getMediaHash());
                insertStmt.setString(c++, post.getMediaOrig());
                insertStmt.setBoolean(c++, post.isSpoiler());
                insertStmt.setBoolean(c++, post.isDeleted());
                insertStmt.setString(c++, (post.getCapcode() != null) ? post.getCapcode() : "N");
                insertStmt.setString(c++, post.getEmail());
                insertStmt.setString(c++, post.getName());
                insertStmt.setString(c++, post.getTrip());
                insertStmt.setString(c++, post.getTitle());
                insertStmt.setString(c++, post.getComment());
                insertStmt.setString(c++, post.getDelpass());
                insertStmt.setBoolean(c++, post.isSticky());
                insertStmt.setString(c++, post.getPosterHash());
                insertStmt.setString(c++, post.getExif());
                
                insertStmt.setInt(c++, post.getNum());
                insertStmt.setInt(c++, post.getSubnum());
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
            updateStmt.executeBatch();
            conn.commit();
            break;
        } catch(SQLException e) {
            if(e.getCause() instanceof SQLRecoverableException) {
                this.reconnect();
                continue;
            }
            
            try {
                conn.rollback();
            } catch(SQLException e1) {
                e1.setNextException(e);
                throw new ContentStoreException(e1);
            }
            throw new ContentStoreException(e);
        }}
    }
    
    public synchronized void markDeleted(int post) throws ContentStoreException, DBConnectionException {    
        while(true) { try {
            updateDeletedStmt.setBoolean(1, true);
            updateDeletedStmt.setInt(2, post);
            updateDeletedStmt.setInt(3, 0);
            updateDeletedStmt.execute();
            conn.commit();
            break;
        } catch(SQLRecoverableException e) {
            this.reconnect();
            continue;
        } catch(SQLException e) {
            try {
                conn.rollback();
            } catch(SQLException e1) {
                e1.setNextException(e);
                throw new ContentStoreException(e1);
            }
            throw new ContentStoreException(e);
        } } 
    }
    
    public synchronized Media getMedia(MediaPost post) throws ContentGetException, ContentStoreException, DBConnectionException {
        Media media = null;
        ResultSet mediaRs = null;
        
        // Get the media row from the _images table associated with this image
        while(true) {
            try {
                selectMediaStmt.setString(1, post.getMediaHash());
                mediaRs = selectMediaStmt.executeQuery();
                conn.commit();
                break;
            } catch(SQLRecoverableException e) {
                this.reconnect();
                continue;
            } catch(SQLException e) {             
                try {
                    conn.rollback();
                    mediaRs.close();
                } catch(SQLException e1) {
                    e.setNextException(e1);
                }
                throw new ContentGetException(e);
            }
        }
        
        try {
            if(mediaRs.next()) {
                media = new Media(
                    mediaRs.getInt("media_id"),
                    mediaRs.getString("media_hash"), 
                    mediaRs.getString("media"),
                    mediaRs.getString("preview_op"),
                    mediaRs.getString("preview_reply"),
                    mediaRs.getInt("total"),
                    mediaRs.getInt("banned"));
            }
        } catch(SQLException e) {
            throw new ContentGetException(e);
        } finally {
            try {
                mediaRs.close();
            } catch(SQLException e) {
                throw new ContentGetException(e);
            }
        }
        
        if(media == null) {
            // Somehow, we got ahead of the post insertion. Oh well, we'll get it next time.
            // Getting here means something isn't right with our transaction isolation mode
            // (Or maybe the DB we're using just sucks)
            throw new ContentGetException("Media hash " + post.getMediaHash() + " not found in media DB table");
        }
        
        boolean mediaUpdate = media.getMedia() == null;
        boolean previewOpUpdate = media.getPreviewOp() == null && post.isOp();
        boolean previewReplyUpdate = media.getPreviewReply() == null && !post.isOp();
        
        // Update media row in _images table when any of its entries are null and we actually have it
        if(mediaUpdate || previewOpUpdate || previewReplyUpdate) {
        	try {
        	    if(mediaUpdate) {
        	        updateMediaStmt.setString(1, post.getMedia());
                    updateMediaStmt.setString(2, post.getMediaHash());
                    updateMediaStmt.executeUpdate();
        	    }
        	    if(previewOpUpdate) {
                    updatePreviewOpStmt.setString(1, post.getPreview());
                    updatePreviewOpStmt.setString(2, post.getMediaHash());
                    updatePreviewOpStmt.executeUpdate();
        	    }
        	    if(previewReplyUpdate) {
                    updatePreviewReplyStmt.setString(1, post.getPreview());
                    updatePreviewReplyStmt.setString(2, post.getMediaHash());
                    updatePreviewReplyStmt.executeUpdate();
        	    }
                conn.commit();
            } catch(SQLException e) {
                try {
                    conn.rollback();
                } catch(SQLException e1) {
                    e.setNextException(e1);
                }
                throw new ContentStoreException(e);
            }
        }
        
        return media;
    }
}
