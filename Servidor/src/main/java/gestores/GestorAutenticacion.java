package gestores;

import basededatos.ConexionBBDD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import org.mindrot.jbcrypt.BCrypt;

/**
 * GestorAutenticacion.
 * Aísla la lógica de seguridad y acceso de cuentas de usuario.
 * Garantiza que las consultas de validación e inserción operen de forma asíncrona,
 * impidiendo que posibles micro-bloqueos en la base de datos frenen el flujo de red.
 */
public class GestorAutenticacion {

    /**
     * registrarJugador
     * Método genérico asíncrono que toma las credenciales puras enviadas por el cliente, 
     * genera un cifrado seguro e irreversible mediante BCrypt y ordena su almacenamiento 
     * en PostgreSQL utilizando el pool de conexiones.
     */
    public static CompletableFuture<Boolean> registrarJugador(String correo, String passwordTextoPlano) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO jugadores (correo, password) VALUES (?, ?)";
            String passwordEncriptada = BCrypt.hashpw(passwordTextoPlano, BCrypt.gensalt());
            
            try (Connection conn = ConexionBBDD.obtenerConexion();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, correo);
                pstmt.setString(2, passwordEncriptada);
                pstmt.executeUpdate();
                return true;
                
            } catch (SQLException e) {
                System.err.println("Fallo al registrar jugador (posible correo duplicado): " + e.getMessage());
                return false; 
            }
        });
    }

    /**
     * autenticarJugador
     * Método genérico asíncrono que recupera el ID y el hash cifrado de la cuenta solicitada.
     * Ejecuta una comparación matemática de seguridad en segundo plano para aprobar 
     * o denegar el acceso. Devuelve el ID real de la base de datos si es exitoso, 
     * o -1 si las credenciales son incorrectas o no existe el usuario.
     */
    public static CompletableFuture<Integer> autenticarJugador(String correo, String passwordTextoPlano) {
        return CompletableFuture.supplyAsync(() -> {
            // Modificamos la consulta para extraer también el ID numérico único del jugador
            String sql = "SELECT id, password FROM jugadores WHERE correo = ?";
            
            try (Connection conn = ConexionBBDD.obtenerConexion();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, correo);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String hashAlmacenado = rs.getString("password");
                        
                        // Si la contraseña coincide matemáticamente, devolvemos el ID real de la BBDD
                        if (BCrypt.checkpw(passwordTextoPlano, hashAlmacenado)) {
                            return rs.getInt("id");
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("Fallo al autenticar jugador: " + e.getMessage());
                return -1;
            }
            
            // Si la cuenta no existe o la contraseña es incorrecta, devolvemos -1 por seguridad
            return -1;
        });
    }
}