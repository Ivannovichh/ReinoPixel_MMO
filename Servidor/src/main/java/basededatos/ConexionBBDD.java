package basededatos;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

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
     * Configura los parámetros de acceso a la base de datos local (localhost) 
     * y los límites de la piscina de conexiones (HikariCP) al arrancar el servidor.
     */
    static {
        HikariConfig config = new HikariConfig();
        
        try {
            // Configuración directa para PostgreSQL en entorno local (PGAdmin)
            // Asegúrate de cambiar 'reinopixel_db' y la contraseña por los tuyos reales
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/reinopixel_db");
            config.setUsername("postgres");
            config.setPassword("ADMIN"); // <-- CAMBIA ESTO por tu contraseña de PGAdmin
            
            // Configuración del Pool de conexiones para MMORPG
            config.setMaximumPoolSize(20);
            config.setMinimumIdle(5);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(10000);
            
            // Optimizaciones de caché para consultas recurrentes (Selects/Updates)
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            System.out.println("BBDD: HikariCP conectado con éxito a PostgreSQL Local.");
            
        } catch (Exception e) {
            System.err.println("FALLO CRITICO: No se pudo enlazar la BBDD local.");
            e.printStackTrace(); // Imprime el error exacto por si falla la contraseña
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
            System.out.println("BBDD: Pool de conexiones cerrado limpiamente.");
        }
    }
}