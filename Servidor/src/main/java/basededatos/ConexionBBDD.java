package basededatos;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.net.URI;

/**
 * Clase ConexionBBDD.
 * Motor principal de persistencia de datos configurado para concurrencia masiva.
 * Inicializa y administra un pool de conexiones pre-abiertas hacia PostgreSQL 
 * para garantizar latencia cero en las peticiones concurrentes del servidor.
 */
public class ConexionBBDD {

    private static HikariDataSource dataSource;

    /**
     * Bloque estático de inicialización.
     * Configura los parámetros de acceso a la base de datos y los límites 
     * de la piscina de conexiones (HikariCP) al arrancar la máquina virtual de Java.
     */
    static {
        HikariConfig config = new HikariConfig();
        
        try {
            String databaseUrl = System.getenv("DATABASE_URL");
            
            if (databaseUrl != null && !databaseUrl.isEmpty()) {
                URI dbUri = new URI(databaseUrl);
                String username = dbUri.getUserInfo().split(":")[0];
                String password = dbUri.getUserInfo().split(":")[1];
                String jdbcUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + (dbUri.getPort() == -1 ? 5432 : dbUri.getPort()) + dbUri.getPath();
                
                config.setJdbcUrl(jdbcUrl);
                config.setUsername(username);
                config.setPassword(password);
            } else {
                String host = System.getenv("PGHOST");
                String port = System.getenv("PGPORT");
                String dbName = System.getenv("PGDATABASE");
                
                config.setJdbcUrl("jdbc:postgresql://" + host + ":" + (port == null ? "5432" : port) + "/" + (dbName == null ? "railway" : dbName));
                config.setUsername(System.getenv("PGUSER"));
                config.setPassword(System.getenv("PGPASSWORD"));
            }

            config.setMaximumPoolSize(20);
            config.setMinimumIdle(5);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(10000);
            
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            
        } catch (Exception e) {
            System.err.println("FALLO CRITICO: No se pudo enlazar la BBDD.");
        }
    }

    /**
     * obtenerConexion
     * Proporciona una conexión activa extraída de la piscina en memoria.
     */
    public static Connection obtenerConexion() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * cerrarPool
     * Finaliza la comunicación con el servidor PostgreSQL liberando los recursos de red.
     */
    public static void cerrarPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}