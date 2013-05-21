package net.easymodo.asagi;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.settings.BoardSettings;

import org.apache.http.annotation.ThreadSafe;

@ThreadSafe
@SuppressWarnings("UnusedDeclaration")
public class Pgsql extends SQL {

    public Pgsql(String path, BoardSettings info) throws BoardInitException {
        String dbName = info.getDatabase();
        String dbHost = info.getHost();
        String dbUsername = info.getUsername();
        String dbPassword = info.getPassword();

        String connStr = String.format("jdbc:postgresql://%s/%s?user=%s&password=%s",
                dbHost, dbName, dbUsername, dbPassword);

        this.charset = "UTF8";
        this.tableCheckQuery = "SELECT * FROM pg_tables WHERE tablename = ?";

        this.init(connStr, path, info);
    }
}
