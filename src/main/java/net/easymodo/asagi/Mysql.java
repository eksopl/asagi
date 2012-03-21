package net.easymodo.asagi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.annotation.concurrent.ThreadSafe;

import net.easymodo.asagi.settings.BoardSettings;

@ThreadSafe
public class Mysql extends Local {
    private final String dbName;
    private final String dbHost;
    private final String dbUsername;
    private final String dbPassword;
    private final String table;
    private final String extraArgs = "rewriteBatchedStatements=true";
    
    private Connection conn = null;
    private PreparedStatement insertStmt = null;

    public Mysql(String path, BoardSettings info) throws SQLException {
        super(path, info);
        this.dbName = info.getDatabase();
        this.dbHost = info.getHost();
        this.dbUsername = info.getUsername();
        this.dbPassword = info.getPassword();
        this.table = info.getTable();
      
        String connStr = String.format("jdbc:mysql://%s/%s?user=%s&password=%s&%s",
                this.dbHost, this.dbName, this.dbUsername, this.dbPassword, this.extraArgs);
                
        conn = DriverManager.getConnection(connStr);
        conn.setAutoCommit(false);
        
        String query = "INSERT INTO " + this.table + " VALUES " +
            "(NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" +
            "ON DUPLICATE KEY UPDATE comment = VALUES(comment), deleted = VALUES(deleted)," +
            "media = COALESCE(VALUES(media), media), sticky = (VALUES(sticky) || sticky)";
  
        insertStmt = conn.prepareStatement(query);
    }
    
    public synchronized void insert(Topic topic) throws SQLException{    
        try{
            for(Post post : topic.getPosts()) {
                int c = 1;
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
                insertStmt.addBatch();
            }
                
            insertStmt.executeBatch();
            conn.commit();
        } catch(SQLException e) {
            conn.rollback();
            throw e;
        }
    }
}
 
