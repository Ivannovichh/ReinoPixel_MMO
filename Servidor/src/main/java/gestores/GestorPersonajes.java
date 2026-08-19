package gestores;

import basededatos.ConexionBBDD;
import plantillas.Personaje;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GestorPersonajes.
 * Procesa las transacciones de los avatares del juego en hilos independientes.
 * Encapsula la creación, carga y persistencia de las coordenadas de cada entidad.
 */
public class GestorPersonajes {

    /**
     * crearPersonaje
     * Registra un nuevo avatar asociado a un jugador con estadísticas base
     * y coordenadas iniciales, delegando la carga a la piscina de conexiones.
     */
    public static CompletableFuture<Boolean> crearPersonaje(int jugadorId, String nombrePersonaje) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO personajes (jugador_id, nombre, nivel, pos_x, pos_y, pos_z) VALUES (?, ?, 1, 0.0, 0.0, 0.0)";

            try (Connection conn = ConexionBBDD.obtenerConexion();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, jugadorId);
                pstmt.setString(2, nombrePersonaje);
                pstmt.executeUpdate();
                return true;

            } catch (SQLException e) {
                return false;
            }
        });
    }

    /**
     * cargarPersonajesDeJugador
     * Recopila el listado completo de avatares vinculados a una cuenta.
     * Retorna una promesa que contendrá la lista empaquetada lista para ser procesada
     * y enviada de vuelta al cliente de Godot.
     */
    public static CompletableFuture<List<Personaje>> cargarPersonajesDeJugador(int jugadorId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Personaje> listaPersonajes = new ArrayList<>();
            String sql = "SELECT * FROM personajes WHERE jugador_id = ?";

            try (Connection conn = ConexionBBDD.obtenerConexion();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, jugadorId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    listaPersonajes.add(new Personaje(
                        rs.getInt("id"), rs.getInt("jugador_id"), rs.getString("nombre"),
                        rs.getInt("nivel"), rs.getFloat("pos_x"), rs.getFloat("pos_y"), rs.getFloat("pos_z")
                    ));
                }
            } catch (SQLException e) {
                return listaPersonajes; 
            }
            return listaPersonajes;
        });
    }
    
    /**
     * guardarPosicionPersonaje
     * Actualiza el estado persistente del personaje en el mundo físico.
     * Implementado como un proceso "fire-and-forget" que no requiere respuesta.
     */
    public static CompletableFuture<Void> guardarPosicionPersonaje(Personaje personaje) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE personajes SET pos_x = ?, pos_y = ?, pos_z = ? WHERE id = ?";
            
            try (Connection conn = ConexionBBDD.obtenerConexion();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setFloat(1, personaje.getPosX());
                pstmt.setFloat(2, personaje.getPosY());
                pstmt.setFloat(3, personaje.getPosZ());
                pstmt.setInt(4, personaje.getId());
                pstmt.executeUpdate();

            } catch (SQLException e) {
                // Silenciado intencionalmente para evitar sobrecarga de logs
            }
        });
    }
}