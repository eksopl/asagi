package net.easymodo.asagi;

import java.sql.SQLException;
import org.apache.http.annotation.ThreadSafe;

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
        // TODO: Not implemented
        return;
    }
}
