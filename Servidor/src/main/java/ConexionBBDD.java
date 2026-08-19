import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.net.URI;
import java.net.URISyntaxException;

public class ConexionBBDD {

    /*
     * obtenerConexion: Método principal encargado de establecer el puente con la base de datos.
     * Soporta tanto la variable global DATABASE_URL de Railway como las variables individuales (PGHOST, etc.).
     */
    public static Connection obtenerConexion() throws SQLException {
        // 1. Intentar leer la URL completa de Railway (DATABASE_URL)
        String databaseUrl = System.getenv("DATABASE_URL");
        
        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            try {
                URI dbUri = new URI(databaseUrl);
                String username = dbUri.getUserInfo().split(":")[0];
                String password = dbUri.getUserInfo().split(":")[1];
                String dbHost = dbUri.getHost();
                int dbPort = dbUri.getPort();
                String dbPath = dbUri.getPath(); // Incluye la barra '/' al inicio (ej: /railway)

                String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + (dbPort == -1 ? 5432 : dbPort) + dbPath;
                return DriverManager.getConnection(jdbcUrl, username, password);
                
            } catch (URISyntaxException | ArrayIndexOutOfBoundsException | NullPointerException e) {
                System.err.println("Error procesando DATABASE_URL, intentando variables individuales: " + e.getMessage());
            }
        }

        // 2. Método alternativo: leer variables individuales (PGHOST, PGUSER, etc.)
        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String dbName = System.getenv("PGDATABASE");
        String user = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");

        if (host == null || user == null || password == null) {
            throw new SQLException("Error crítico: Las variables de entorno de la base de datos no están configuradas en Railway.");
        }

        String url = "jdbc:postgresql://" + host + ":" + (port == null ? "5432" : port) + "/" + dbName;
        return DriverManager.getConnection(url, user, password);
    }

    public static void main(String[] args) {
        System.out.println("Iniciando prueba de conexión a la base de datos...");
        try (Connection conexion = obtenerConexion()) {
            System.out.println("¡ÉXITO ABSOLUTO! Conexión a PostgreSQL en Railway establecida correctamente.");
        } catch (SQLException e) {
            System.err.println("FALLO EN LA CONEXIÓN: " + e.getMessage());
        }
    }
}