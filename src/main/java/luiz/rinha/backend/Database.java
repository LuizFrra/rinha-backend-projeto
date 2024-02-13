package luiz.rinha.backend;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.util.function.Function;

public class Database {
    static private HikariDataSource ds;

    static private HikariConfig config;

    static {
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        config = new HikariConfig();
        config.setUsername("rinha");
        config.setPassword("rinha");
        String host = System.getenv("DB_HOST");
        config.setJdbcUrl("jdbc:postgresql://" + host + ":5432/rinha");
        config.setMaximumPoolSize(8);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        ds = new HikariDataSource(config);
    }

    public static <T> T execute(Function<Connection, T> callback) {
        try (Connection conn = ds.getConnection()) {
            return callback.apply(conn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
