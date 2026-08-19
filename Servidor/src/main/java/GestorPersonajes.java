import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * GestorPersonajes.
 * Se encarga de toda la comunicación con la base de datos PostgreSQL
 * relacionada con la gestión del ciclo de vida de los personajes.
 * Aísla las consultas SQL del resto de la lógica del servidor.
 */
public class GestorPersonajes {

    /**
     * Crea un nuevo personaje en la base de datos vinculado a una cuenta de jugador.
     * Realiza un INSERT con los datos básicos y coordenadas iniciales seguras (0,0,0).
     * Retorna verdadero si la operación se completó sin errores en la BBDD.
     */
    public static boolean crearPersonaje(int jugadorId, String nombrePersonaje) {
        String sql = "INSERT INTO personajes (jugador_id, nombre, nivel, pos_x, pos_y, pos_z) VALUES (?, ?, 1, 0.0, 0.0, 0.0)";

        try (Connection conn = ConexionBBDD.obtenerConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, jugadorId);
            pstmt.setString(2, nombrePersonaje);
            pstmt.executeUpdate();
            
            return true;

        } catch (SQLException e) {
            System.err.println("Error crítico al crear personaje en BBDD: " + e.getMessage());
            return false;
        }
    }

    /**
     * Recupera todos los personajes asociados a una cuenta de jugador específica.
     * Realiza un SELECT filtrando por el ID de la cuenta, instancia los objetos 
     * Personaje en la memoria RAM y los devuelve empaquetados en una lista.
     * Fundamental para enviar la lista de selección de personajes a Godot.
     */
    public static List<Personaje> cargarPersonajesDeJugador(int jugadorId) {
        List<Personaje> listaPersonajes = new ArrayList<>();
        String sql = "SELECT * FROM personajes WHERE jugador_id = ?";

        try (Connection conn = ConexionBBDD.obtenerConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, jugadorId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Personaje p = new Personaje(
                    rs.getInt("id"),
                    rs.getInt("jugador_id"),
                    rs.getString("nombre"),
                    rs.getInt("nivel"),
                    rs.getFloat("pos_x"),
                    rs.getFloat("pos_y"),
                    rs.getFloat("pos_z")
                );
                listaPersonajes.add(p);
            }

        } catch (SQLException e) {
            System.err.println("Error crítico al cargar personajes desde BBDD: " + e.getMessage());
        }

        return listaPersonajes;
    }
    
    /**
     * Guarda la posición actual de un personaje en la base de datos.
     * Útil para persistir las coordenadas cuando el jugador se desconecta,
     * permitiendo que aparezca exactamente en el mismo sitio al volver a entrar.
     */
    public static void guardarPosicionPersonaje(Personaje personaje) {
        String sql = "UPDATE personajes SET pos_x = ?, pos_y = ?, pos_z = ? WHERE id = ?";
        
        try (Connection conn = ConexionBBDD.obtenerConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setFloat(1, personaje.getPosX());
            pstmt.setFloat(2, personaje.getPosY());
            pstmt.setFloat(3, personaje.getPosZ());
            pstmt.setInt(4, personaje.getId());
            
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error al guardar posición del personaje: " + e.getMessage());
        }
    }
}