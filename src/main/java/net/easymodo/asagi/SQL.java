package net.easymodo.asagi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.http.annotation.ThreadSafe;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentStoreException;
import net.easymodo.asagi.settings.BoardSettings;

@ThreadSafe
public abstract class SQL implements DB {
    protected String table;
    protected Connection conn = null;
    protected String insertQuery = null;
    protected String updateQuery = null;
    
    private PreparedStatement updateStmt = null;
    private PreparedStatement insertStmt = null;
    private PreparedStatement selectMediaStmt = null;
        
    public synchronized void init(String connStr, String path, BoardSettings info) throws BoardInitException {
        this.table = info.getTable();

        if(this.insertQuery == null) {
            this.insertQuery = String.format(
                    "INSERT INTO %s" +
                    " (id, num, subnum, parent, timestamp, preview, preview_w, preview_h, media, " +
                    " media_w, media_h, media_size, media_hash, media_filename, spoiler, deleted, " +
                    " capcode, email, name, trip, title, comment, delpass, sticky) " +
                    "  SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? " +
                    "  WHERE NOT EXISTS (SELECT 1 FROM %s WHERE num=? and subnum=?)", 
                    this.table, this.table);
        }
        this.updateQuery = 
                String.format("UPDATE %s SET comment = ?, deleted = ?, media = COALESCE(?, media)," +
                        "  sticky = (? OR sticky) WHERE num=? and subnum=?", table);
        
        String selectMediaQuery = String.format("SELECT * FROM %s_images WHERE media_hash = ?", 
                table);
        
        try {
            conn = DriverManager.getConnection(connStr);
            conn.setAutoCommit(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            
            this.createTables();
            
            insertStmt = conn.prepareStatement(insertQuery);
            updateStmt = conn.prepareStatement(updateQuery);
            selectMediaStmt = conn.prepareStatement(selectMediaQuery);
       } catch (SQLException e) {
            throw new BoardInitException(e);
        }
    }
    
    public abstract void createTables() throws BoardInitException, SQLException;
    
    public synchronized void insert(Topic topic) throws ContentStoreException {    
        try{
            for(Post post : topic.getPosts()) {
                int c = 1;
                updateStmt.setString(c++, post.getComment());
                updateStmt.setBoolean(c++, post.isDeleted());
                updateStmt.setString(c++,post.getMedia());
                updateStmt.setBoolean(c++, post.isSticky());
                updateStmt.setInt(c++, post.getNum());
                updateStmt.setInt(c++, post.getSubnum());
                updateStmt.addBatch();

                c = 1;
                insertStmt.setInt(c++, post.getId());
                insertStmt.setInt(c++, post.getNum());
                insertStmt.setInt(c++, post.getSubnum());
                insertStmt.setInt(c++, post.getParent());
                insertStmt.setInt(c++, post.getDate());
                insertStmt.setString(c++, post.getPreview());
                insertStmt.setInt(c++, post.getPreviewW());
                insertStmt.setInt(c++, post.getPreviewH());
                insertStmt.setString(c++,post.getMedia());
                insertStmt.setInt(c++, post.getMediaW());
                insertStmt.setInt(c++, post.getMediaH());
                insertStmt.setInt(c++, post.getMediaSize());
                insertStmt.setString(c++, post.getMediaHash());
                insertStmt.setString(c++, post.getMediaFilename());
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
                
                insertStmt.setInt(c++, post.getNum());
                insertStmt.setInt(c++, post.getSubnum());
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
            updateStmt.executeBatch();
            // conn.commit();
        } catch(SQLException e) {
            // MySQL acts stupid with autocommit off
            // I don't even fucking know
            /* try {
                conn.rollback();
            } catch(SQLException e1) {
                e1.setNextException(e);
                throw new ContentStoreException(e1);
            } */
            throw new ContentStoreException(e);
        }
    }
    
    public synchronized Media getMedia(Post post) throws ContentGetException {
        Media media = null;
        ResultSet mediaRs = null;
        
        try {
            selectMediaStmt.setString(1, post.getMediaHash());
            mediaRs = selectMediaStmt.executeQuery();
        } catch(SQLException e) {
            throw new ContentGetException(e);
        }
         
        try {
            if(mediaRs.first()) {
                media = new Media(
                        mediaRs.getInt("id"),
                        mediaRs.getString("media_hash"), 
                        mediaRs.getString("media_filename"),
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
            // Somehow, we got ahead of the post insertion. We'll get it next time
            throw new ContentGetException("Media hash " + post.getMediaHash() + " not found in media DB table");
        }
        
        return media;
    }
}
