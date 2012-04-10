package net.easymodo.asagi;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.http.annotation.ThreadSafe;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.settings.BoardSettings;

@ThreadSafe
public class Mysql extends Local implements SQL {
    private final String dbName;
    private final String dbHost;
    private final String dbUsername;
    private final String dbPassword;
    private final String table;
    private final String charset;
    private final String extraArgs;
    
    private Connection conn = null;
    private PreparedStatement insertStmt = null;
    private PreparedStatement selectMediaStmt = null;

    public Mysql(String path, BoardSettings info) throws BoardInitException {
        super(path, info);
        this.dbName = info.getDatabase();
        this.dbHost = info.getHost();
        this.dbUsername = info.getUsername();
        this.dbPassword = info.getPassword();
        this.table = info.getTable();
        this.charset = "utf8mb4";
        this.extraArgs = "rewriteBatchedStatements=true&allowMultiQueries=true";
      
        String connStr = String.format("jdbc:mysql://%s/%s?user=%s&password=%s&%s",
                this.dbHost, this.dbName, this.dbUsername, this.dbPassword, this.extraArgs);
        
        String insertQuery = String.format("INSERT INTO %s VALUES " +
                "(NULL,0,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" +
                "ON DUPLICATE KEY UPDATE comment = VALUES(comment), deleted = VALUES(deleted)," +
                "media = COALESCE(VALUES(media), media), sticky = (VALUES(sticky) OR sticky)", this.table);
       
        String selectMediaQuery = String.format("SELECT * FROM %s_images WHERE media_hash = ?", 
        		this.table);
        
        try {
            conn = DriverManager.getConnection(connStr);
            conn.setAutoCommit(false);
            
            this.createTables();
        
            insertStmt = conn.prepareStatement(insertQuery);
            selectMediaStmt = conn.prepareStatement(selectMediaQuery);
        } catch (SQLException e) {
            throw new BoardInitException(e);
        }
    }
    
    public synchronized void createTables() throws BoardInitException, SQLException {
        // Check if the table for this board has already been created
        PreparedStatement pst = conn.prepareStatement("SHOW TABLES LIKE ?");
        try {
           pst.setString(1, this.table);
           ResultSet res = pst.executeQuery();
           
           if(res.isBeforeFirst())
               return;
        } finally {
            pst.close();
        }
        
        // Query to create tables common to all boards
        String commonSql;
        try {
            commonSql = Resources.toString(Resources.getResource("common.sql"), Charsets.UTF_8);
        } catch(IOException e) {
            throw new BoardInitException(e);
        } catch(IllegalArgumentException e) {
            throw new BoardInitException(e);
        }
        
        // Query to create all tables for this board
        String boardSql;
        try {
            boardSql = Resources.toString(Resources.getResource("boards.sql"), Charsets.UTF_8);
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
            triggersSql = Resources.toString(Resources.getResource("triggers.sql"), Charsets.UTF_8);
            triggersSql = triggersSql.replaceAll("%%BOARD%%", table);
            triggersSql = triggersSql.replaceAll("%%CHARSET%%", charset);
        } catch(IOException e) {
            throw new BoardInitException(e);
        } catch(IllegalArgumentException e) {
            throw new BoardInitException(e);
        }
        
        Statement st = conn.createStatement();
        try {
            st.executeUpdate(commonSql);
            st.executeUpdate(boardSql);
            st.executeUpdate(triggersSql);
        } finally {
            st.close();
        }
    }
    
    public synchronized void insert(Topic topic) throws SQLException {    
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
    
    public synchronized Media getMediaRow(Post post) throws SQLException, ContentGetException {
    	selectMediaStmt.setString(1, post.getMediaHash());
    	ResultSet media = selectMediaStmt.executeQuery();

    	if(media.first())
    	{
    		return new Media(
    				media.getInt("id"),
    				media.getString("media_hash"), 
    				media.getString("media_filename"),
    				media.getString("preview_op"),
    				media.getString("preview_reply"),
    				media.getInt("total"),
    				media.getInt("banned")
    			);
    	}
    	else
    	{
    		// It shouldn't happen that the row is not found, but I wouldn't trust MySQL
    		throw new ContentGetException("Media row not found for row inserted in post database");
    	}
    }
}
 
