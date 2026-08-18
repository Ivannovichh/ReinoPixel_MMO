import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

public class GestorAutenticacion {

    /*
     * registrarJugador: Gestiona la creación de una nueva cuenta de usuario.
     * Antes de interactuar con la base de datos, toma la contraseña en texto plano recibida 
     * desde Godot y la transforma en un "hash" indescifrable utilizando el algoritmo BCrypt.
     * Posteriormente, inserta el correo y la contraseña encriptada de forma segura mediante 
     * una consulta preparada para prevenir inyecciones SQL. Si el correo ya existe, PostgreSQL 
     * rechazará la inserción devolviendo false.
     */
    public static boolean registrarJugador(String correo, String passwordTextoPlano) {
        String sql = "INSERT INTO jugadores (correo, password) VALUES (?, ?)";
        
        // Encriptamos la contraseña con una "sal" aleatoria (salt)
        String passwordEncriptada = BCrypt.hashpw(passwordTextoPlano, BCrypt.gensalt());
        
        try (Connection conn = ConexionBBDD.obtenerConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, correo);
            pstmt.setString(2, passwordEncriptada);
            
            pstmt.executeUpdate();
            return true;
            
        } catch (SQLException e) {
            System.err.println("Fallo en el registro (posible correo duplicado): " + e.getMessage());
            return false;
        }
    }

    /*
     * autenticarJugador: Valida la identidad del usuario durante el inicio de sesión.
     * Busca en la base de datos el correo introducido. Si lo encuentra, extrae el "hash" 
     * encriptado que se guardó durante el registro. Utiliza BCrypt para comparar matemáticamente 
     * la contraseña enviada desde Godot con el hash almacenado, sin llegar a desencriptar 
     * el dato original. Si el resultado matemático coincide, autoriza el acceso.
     */
    public static boolean autenticarJugador(String correo, String passwordTextoPlano) {
        String sql = "SELECT password FROM jugadores WHERE correo = ?";
        
        try (Connection conn = ConexionBBDD.obtenerConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, correo);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String hashAlmacenado = rs.getString("password");
                    
                    // Comparamos el texto plano con el hash de la base de datos
                    return BCrypt.checkpw(passwordTextoPlano, hashAlmacenado);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error de base de datos durante la autenticación: " + e.getMessage());
        }
        
        return false;
    }
}