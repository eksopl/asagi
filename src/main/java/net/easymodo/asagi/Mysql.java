package net.easymodo.asagi;

import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.settings.BoardSettings;
import org.apache.http.annotation.ThreadSafe;

import java.sql.SQLException;
import java.sql.Statement;

@ThreadSafe
@SuppressWarnings("UnusedDeclaration")
public class Mysql extends SQL {
    public Mysql(String path, BoardSettings info) throws BoardInitException {
        String dbName = info.getDatabase();
        String dbHost = info.getHost();
        String dbUsername = info.getUsername();
        String dbPassword = info.getPassword();
        String dbCharset = info.getCharset();

        String extraArgs = "rewriteBatchedStatements=true&allowMultiQueries=true&autoReconnect=true";

        String connStr = String.format("jdbc:mysql://%s/%s?user=%s&password=%s&%s",
                dbHost, dbName, dbUsername, dbPassword, extraArgs);

        this.charset = dbCharset;

        this.insertQuery = String.format(
                "INSERT INTO \"%s\"" +
                "  (poster_ip, num, subnum, thread_num, op, timestamp, preview_orig, preview_w, preview_h, " +
                "  media_filename, media_w, media_h, media_size, media_hash, media_orig, spoiler, deleted, " +
                "  capcode, email, name, trip, title, comment, delpass, sticky, poster_hash, poster_country, exif) " +
                "    SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? FROM DUAL " +
                "    WHERE NOT EXISTS (SELECT 1 FROM \"%s\" WHERE num = ? AND subnum = ?)" +
                "      AND NOT EXISTS (SELECT 1 FROM \"%s_deleted\" WHERE num = ? AND subnum = ?)",
                info.getTable(), info.getTable(), info.getTable());
        this.tableCheckQuery = "SHOW TABLES LIKE ?";

        this.init(connStr, path, info);
    }

    protected synchronized void postConnect() throws SQLException {
        String query = "SET sql_mode='ANSI'";
        Statement stmt = conn.createStatement();
        stmt.execute(query);
    }
}

