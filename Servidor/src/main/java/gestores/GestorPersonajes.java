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
 * Encapsula la creación, carga y persistencia de las coordenadas de cada entidad
 * interactuando con el pool de conexiones de PostgreSQL.
 */
public class GestorPersonajes {

    /**
     * crearPersonaje
     * Método genérico asíncrono que inserta un nuevo avatar en la base de datos.
     * Le asigna las estadísticas base (Nivel 1) y las coordenadas iniciales 
     * de aparición (Spawn point). Devuelve una promesa booleana indicando el éxito.
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
                System.err.println("Fallo al crear personaje: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * cargarPersonajesDeJugador
     * Método genérico asíncrono que recopila el listado completo de avatares 
     * vinculados a la cuenta de un usuario recién autenticado.
     * Transforma las filas SQL en objetos Java instanciables (Plantillas) 
     * listos para ser empaquetados en Big Endian y enviados al cliente.
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
                        rs.getInt("id"), 
                        rs.getInt("jugador_id"), 
                        rs.getString("nombre"),
                        rs.getInt("nivel"), 
                        rs.getFloat("pos_x"), 
                        rs.getFloat("pos_y"), 
                        rs.getFloat("pos_z")
                    ));
                }
            } catch (SQLException e) {
                System.err.println("Fallo al cargar la lista de personajes: " + e.getMessage());
                return listaPersonajes; 
            }
            return listaPersonajes;
        });
    }
    
    /**
     * guardarPosicionPersonaje
     * Método interno de baja frecuencia.
     * Actualiza el estado persistente del personaje en el mundo de la base de datos.
     * Está implementado como un proceso "fire-and-forget" que no interrumpe el flujo
     * del juego, ideal para auto-guardados periódicos (ej: cada 5 minutos) en lugar 
     * de guardarse en cada tick de movimiento.
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
                // Silenciado intencionalmente para evitar sobrecarga de logs en los auto-guards
            }
        });
    }
}