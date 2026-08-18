import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexionBBDD {

    /*
     * obtenerConexion: Método principal encargado de establecer el puente con la base de datos de forma segura.
     * En lugar de tener las credenciales expuestas en el código fuente, este método lee las variables de entorno 
     * (PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD) que Railway inyecta automáticamente de forma nativa.
     * 
     * Si el servidor se está ejecutando en local y no encuentra las variables, lanzará un error indicando 
     * que faltan por configurar en el entorno de desarrollo.
     * 
     * Retorna un objeto Connection activo para que el resto del sistema interactúe con PostgreSQL.
     */
    public static Connection obtenerConexion() throws SQLException {
        // Obtenemos los valores de seguridad directamente del entorno del sistema operativo o plataforma cloud
        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String dbName = System.getenv("PGDATABASE");
        String user = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");

        // Validación interna de seguridad para evitar intentos de conexión nulos
        if (host == null || user == null || password == null) {
            throw new SQLException("Error crítico: Las variables de entorno de la base de datos no están configuradas.");
        }

        // Construimos la URL de conexión con el formato exacto que exige el driver JDBC de PostgreSQL
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;

        // Establecemos y retornamos la conexión autenticada
        return DriverManager.getConnection(url, user, password);
    }

    /*
     * main: Método de ejecución independiente para realizar un test rápido de conexión.
     * Al ejecutar este archivo directamente desde el editor, invocará obtenerConexion() 
     * e imprimirá por consola un mensaje de éxito o el error exacto devuelto por PostgreSQL, 
     * validando así la configuración de red y credenciales.
     */
    public static void main(String[] args) {
        System.out.println("Iniciando prueba de conexión a la base de datos...");
        try (Connection conexion = obtenerConexion()) {
            System.out.println("¡ÉXITO ABSOLUTO! Conexión a PostgreSQL en Railway establecida correctamente.");
        } catch (SQLException e) {
            System.err.println("FALLO EN LA CONEXIÓN: " + e.getMessage());
        }
    }

}