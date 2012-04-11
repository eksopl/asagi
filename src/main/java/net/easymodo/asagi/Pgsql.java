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
public class Pgsql extends SQL {
    
    public Pgsql(String path, BoardSettings info) throws BoardInitException {        
        String dbName = info.getDatabase();
        String dbHost = info.getHost();
        String dbUsername = info.getUsername();
        String dbPassword = info.getPassword();
        
        String connStr = String.format("jdbc:postgresql://%s/%s?user=%s&password=%s",
                dbHost, dbName, dbUsername, dbPassword);
        
        this.init(connStr, path, info);
  
    }
    
    public synchronized void createTables() throws BoardInitException, SQLException {
        PreparedStatement pst = conn.prepareStatement("SELECT * FROM pg_tables WHERE tablename=?");
        ResultSet res = null;
        String commonSql = null;
        
        // Check if common stuff has already been created
        try {
            pst.setString(1, "index_counters");
            res = pst.executeQuery();
    
            if(!res.isBeforeFirst()) {
                // Query to create tables common to all boards
                try {
                    commonSql = Resources.toString(Resources.getResource("net/easymodo/asagi/sql/Pgsql/common.sql"), Charsets.UTF_8);
                } catch(IOException e) {
                    throw new BoardInitException(e);
                } catch(IllegalArgumentException e) {
                    throw new BoardInitException(e);
                }
            }
        } finally {
            pst.close();
            conn.commit();
        }
        
        pst = conn.prepareStatement("SELECT * FROM pg_tables WHERE tablename=?");
        // Check if the tables for this board have already been created too
        // Bail out if yes
        try {
            pst.setString(1, this.table);
            res = pst.executeQuery();
            if(res.isBeforeFirst())
                return;
        } finally {
            pst.close();
            conn.commit();
        }
       
        // Query to create all tables for this board
        String boardSql;
        try {
            boardSql = Resources.toString(Resources.getResource("net/easymodo/asagi/sql/Pgsql/boards.sql"), Charsets.UTF_8);
            boardSql = boardSql.replaceAll("%%BOARD%%", table);
        } catch(IOException e) {
            throw new BoardInitException(e);
        } catch(IllegalArgumentException e) {
            throw new BoardInitException(e);
        }

        // Query to create or replace triggers and procedures for this board
        String triggersSql;
        try {
            triggersSql = Resources.toString(Resources.getResource("net/easymodo/asagi/sql/Pgsql/triggers.sql"), Charsets.UTF_8);
            triggersSql = triggersSql.replaceAll("%%BOARD%%", table);
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
}
