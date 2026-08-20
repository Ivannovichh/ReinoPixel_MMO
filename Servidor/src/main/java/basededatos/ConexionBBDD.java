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
     * Se ejecuta de forma automática al arrancar el servidor Java. 
     * Configura el enrutamiento directo a la base de datos local y establece las 
     * optimizaciones de HikariCP (límites de conexiones simultáneas y caché de sentencias 
     * preparadas) para agilizar las operaciones recurrentes.
     * Si la conexión falla, aborta la ejecución del servidor por seguridad.
     */
    static {
        HikariConfig config = new HikariConfig();
        
        try {
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/reinopixel_db");
            config.setUsername("postgres");
            config.setPassword("ADMIN"); 
            
            config.setMaximumPoolSize(20);
            config.setMinimumIdle(5);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(10000);
            
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            System.out.println("BBDD: HikariCP conectado con éxito a PostgreSQL Local.");
            
        } catch (Exception e) {
            System.err.println("FALLO CRÍTICO: No se pudo enlazar la BBDD local. Revisa que PostgreSQL esté encendido y la contraseña sea correcta.");
            e.printStackTrace(); 
            System.exit(1); 
        }
    }

    /**
     * obtenerConexion
     * Método genérico que proporciona una conexión activa extraída de la piscina en memoria.
     * Al estar gestionada por Hikari, el tiempo de entrega es prácticamente instantáneo.
     */
    public static Connection obtenerConexion() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * cerrarPool
     * Método interno de apagado.
     * Finaliza la comunicación con el servidor PostgreSQL liberando los recursos de red
     * de manera controlada para evitar conexiones huérfanas o fugas de memoria al cerrar la app.
     */
    public static void cerrarPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("BBDD: Pool de conexiones cerrado limpiamente.");
        }
    }
}