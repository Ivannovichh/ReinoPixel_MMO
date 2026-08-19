import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.net.URI;
import java.net.URISyntaxException;

public class ConexionBBDD {

    public static Connection obtenerConexion() throws SQLException {
        // Depuración: Imprimimos para ver qué hay disponible en el entorno
        //System.out.println("--- COMPROBANDO VARIABLES DE ENTORNO ---");
        //System.out.println("DATABASE_URL presente: " + (System.getenv("DATABASE_URL") != null));
        //System.out.println("PGHOST presente: " + (System.getenv("PGHOST") != null));
        //System.out.println("----------------------------------------");

        // 1. Intentar con DATABASE_URL (La más común en Railway)
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isEmpty()) {
            try {
                URI dbUri = new URI(databaseUrl);
                String username = dbUri.getUserInfo().split(":")[0];
                String password = dbUri.getUserInfo().split(":")[1];
                String dbHost = dbUri.getHost();
                int dbPort = dbUri.getPort();
                String dbPath = dbUri.getPath();

                String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + (dbPort == -1 ? 5432 : dbPort) + dbPath;
                return DriverManager.getConnection(jdbcUrl, username, password);
            } catch (Exception e) {
                System.err.println("Fallo al parsear DATABASE_URL: " + e.getMessage());
            }
        }

        // 2. Intentar con variables estándar de PostgreSQL (PGHOST, etc.)
        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String dbName = System.getenv("PGDATABASE");
        String user = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");

        if (host != null && user != null && password != null) {
            String url = "jdbc:postgresql://" + host + ":" + (port == null ? "5432" : port) + "/" + (dbName == null ? "railway" : dbName);
            return DriverManager.getConnection(url, user, password);
        }

        // Si llega aquí, es que ninguna variable existe en Railway
        throw new SQLException("Error crítico: Las variables de entorno de la base de datos no están configuradas en Railway. Enlaza la BBDD al servicio Java en el panel de Railway.");
    }

    public static void main(String[] args) {
        try (Connection conexion = obtenerConexion()) {
            System.out.println("¡Conexión exitosa!");
        } catch (SQLException e) {
            System.err.println("FALLO: " + e.getMessage());
        }
    }
}