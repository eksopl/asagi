package net.easymodo.asagi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.http.annotation.ThreadSafe;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.settings.BoardSettings;

@ThreadSafe
public class Pgsql extends Local implements SQL {
    private final String dbName;
    private final String dbHost;
    private final String dbUsername;
    private final String dbPassword;
    private final String table;
    
    private Connection conn = null;
    private PreparedStatement updateStmt = null;
    private PreparedStatement insertStmt = null;
    private PreparedStatement selectMediaStmt = null;

    
    public Pgsql(String path, BoardSettings info) throws BoardInitException {
        super(path, info);
        
        this.dbName = info.getDatabase();
        this.dbHost = info.getHost();
        this.dbUsername = info.getUsername();
        this.dbPassword = info.getPassword();
        this.table = info.getTable();
        
        String connStr = String.format("jdbc:postgresql://%s/%s?user=%s&password=%s",
                this.dbHost, this.dbName, this.dbUsername, this.dbPassword);
        
        String insertQuery = String.format(
                "INSERT INTO %s" +
                " (id, num, subnum, parent, timestamp, preview, preview_w, preview_h, media, " +
                " media_w, media_h, media_size, media_hash, media_filename, spoiler, deleted, " +
                " capcode, email, name, trip, title, comment, delpass, sticky) " +
                "  SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?" +
                "  WHERE NOT EXISTS (SELECT 1 FROM %s WHERE num=? and subnum=?)", 
                this.table, this.table);
        String updateQuery = 
                String.format("UPDATE %s SET comment = ?, deleted = ?, media = COALESCE(?, media)," +
                        "  sticky = (? OR sticky) WHERE num=? and subnum=?", this.table);
        
        String selectMediaQuery = String.format("SELECT * FROM %s_images WHERE media_hash = ?", 
        		this.table);
  
        try {
            conn = DriverManager.getConnection(connStr);
            conn.setAutoCommit(false);
            
            insertStmt = conn.prepareStatement(insertQuery);
            updateStmt = conn.prepareStatement(updateQuery);
            selectMediaStmt = conn.prepareStatement(selectMediaQuery);
       } catch (SQLException e) {
            throw new BoardInitException(e);
        }
    }
    
    public synchronized void insert(Topic topic) throws SQLException {    
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
