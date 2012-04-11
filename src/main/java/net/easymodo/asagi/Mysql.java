package net.easymodo.asagi;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.http.annotation.ThreadSafe;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.settings.BoardSettings;

@ThreadSafe
public class Mysql extends SQL {
    private final String charset;
    
    public Mysql(String path, BoardSettings info) throws BoardInitException {
        String dbName = info.getDatabase();
        String dbHost = info.getHost();
        String dbUsername = info.getUsername();
        String dbPassword = info.getPassword();
        
        // TODO: Let user specify charset
        this.charset = "utf8mb4";
        String extraArgs = "rewriteBatchedStatements=true&allowMultiQueries=true";
        
        String connStr = String.format("jdbc:mysql://%s/%s?user=%s&password=%s&%s",
                dbHost, dbName, dbUsername, dbPassword, extraArgs);
        
        this.table = info.getTable();
        this.insertQuery = String.format(
                "INSERT INTO %s" +
                " (id, num, subnum, parent, timestamp, preview, preview_w, preview_h, media, " +
                " media_w, media_h, media_size, media_hash, media_filename, spoiler, deleted, " +
                " capcode, email, name, trip, title, comment, delpass, sticky) " +
                "  SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? FROM DUAL " +
                "  WHERE NOT EXISTS (SELECT 1 FROM %s WHERE num=? and subnum=?)", 
                this.table, this.table);
        this.init(connStr, path, info);
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
            commonSql = Resources.toString(Resources.getResource("net/easymodo/asagi/sql/Mysql/common.sql"), Charsets.UTF_8);
        } catch(IOException e) {
            throw new BoardInitException(e);
        } catch(IllegalArgumentException e) {
            throw new BoardInitException(e);
        }
        
        // Query to create all tables for this board
        String boardSql;
        try {
            boardSql = Resources.toString(Resources.getResource("net/easymodo/asagi/sql/Mysql/boards.sql"), Charsets.UTF_8);
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
            triggersSql = Resources.toString(Resources.getResource("net/easymodo/asagi/sql/Mysql/triggers.sql"), Charsets.UTF_8);
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
}
 
