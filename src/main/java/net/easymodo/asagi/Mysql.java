package net.easymodo.asagi;

import org.apache.http.annotation.ThreadSafe;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.settings.BoardSettings;

@ThreadSafe
public class Mysql extends SQL {    
    public Mysql(String path, BoardSettings info) throws BoardInitException {
        String dbName = info.getDatabase();
        String dbHost = info.getHost();
        String dbUsername = info.getUsername();
        String dbPassword = info.getPassword();
        
        String extraArgs = "rewriteBatchedStatements=true&allowMultiQueries=true";
        
        String connStr = String.format("jdbc:mysql://%s/%s?user=%s&password=%s&%s",
                dbHost, dbName, dbUsername, dbPassword, extraArgs);
        
        // TODO: Let user specify charset
        this.charset = "utf8mb4";
        this.insertQuery = String.format(
                "INSERT INTO %s" +
                " (id, num, subnum, parent, timestamp, preview, preview_w, preview_h, media, " +
                " media_w, media_h, media_size, media_hash, orig_filename, spoiler, deleted, " +
                " capcode, email, name, trip, title, comment, delpass, sticky) " +
                "  SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? FROM DUAL " +
                "  WHERE NOT EXISTS (SELECT 1 FROM %s WHERE num=? and subnum=?)", 
                info.getTable(), info.getTable());
        this.tableCheckQuery = "SHOW TABLES LIKE ?";
        
        this.init(connStr, path, info);
    }
}
 
