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
 * Encapsula la creación, carga y persistencia de las coordenadas y atributos estéticos 
 * de cada entidad interactuando con el pool de conexiones de PostgreSQL.
 */
public class GestorPersonajes {

    /**
     * crearPersonaje
     * Método genérico asíncrono que inserta un nuevo avatar en la base de datos.
     * Le asigna las estadísticas base (Nivel 1), las coordenadas iniciales y 
     * todos los atributos cosméticos elegidos en la interfaz. 
     * Devuelve una promesa booleana indicando el éxito.
     */
    public static CompletableFuture<Boolean> crearPersonaje(int jugadorId, String nombrePersonaje,
            int genero, int cuerpo, int pelo, int formaOjos, float altura, 
            float musculatura, float edad, String colorPiel, String colorOjos) {
                
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO personajes (jugador_id, nombre, nivel, pos_x, pos_y, pos_z, " +
                         "genero, cuerpo, pelo, forma_ojos, altura, musculatura, edad, color_piel, color_ojos) " +
                         "VALUES (?, ?, 1, 0.0, 0.0, 0.0, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = ConexionBBDD.obtenerConexion();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, jugadorId);
                pstmt.setString(2, nombrePersonaje);
                pstmt.setInt(3, genero);
                pstmt.setInt(4, cuerpo);
                pstmt.setInt(5, pelo);
                pstmt.setInt(6, formaOjos);
                pstmt.setFloat(7, altura);
                pstmt.setFloat(8, musculatura);
                pstmt.setFloat(9, edad);
                pstmt.setString(10, colorPiel);
                pstmt.setString(11, colorOjos);
                
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
     * inyectando ahora también la configuración estética.
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
                        rs.getFloat("pos_z"),
                        rs.getInt("genero"),
                        rs.getInt("cuerpo"),
                        rs.getInt("pelo"),
                        rs.getInt("forma_ojos"),
                        rs.getFloat("altura"),
                        rs.getFloat("musculatura"),
                        rs.getFloat("edad"),
                        rs.getString("color_piel"),
                        rs.getString("color_ojos")
                    ));
                }
            } catch (SQLException e) {
                System.err.println("Fallo al cargar la lista de personajes: " + e.getMessage());
            }
            return listaPersonajes;
        });
    }
    
    /**
     * guardarPosicionPersonaje
     * Método interno de baja frecuencia.
     * Actualiza el estado persistente del personaje en el mundo de la base de datos.
     * Está implementado como un proceso "fire-and-forget" que no interrumpe el flujo
     * del juego, ideal para auto-guardados periódicos.
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
    
    /**
     * obtenerPersonajePorId
     * Método genérico asíncrono que busca una entidad específica en la base de datos 
     * utilizando su identificador único (Primary Key). Transforma la fila SQL
     * en un objeto Java en memoria (Plantilla Personaje) para ser manipulado por el servidor.
     */
    public static CompletableFuture<Personaje> obtenerPersonajePorId(int personajeId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM personajes WHERE id = ?";
            
            try (Connection conn = ConexionBBDD.obtenerConexion();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, personajeId);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new Personaje(
                            rs.getInt("id"), 
                            rs.getInt("jugador_id"), 
                            rs.getString("nombre"),
                            rs.getInt("nivel"), 
                            rs.getFloat("pos_x"), 
                            rs.getFloat("pos_y"), 
                            rs.getFloat("pos_z"),
                            rs.getInt("genero"),
                            rs.getInt("cuerpo"),
                            rs.getInt("pelo"),
                            rs.getInt("forma_ojos"),
                            rs.getFloat("altura"),
                            rs.getFloat("musculatura"),
                            rs.getFloat("edad"),
                            rs.getString("color_piel"),
                            rs.getString("color_ojos")
                        );
                    }
                }
            } catch (SQLException e) {
                System.err.println("Fallo al obtener el personaje: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * eliminarPersonaje
     * Método asíncrono que borra permanentemente un avatar de la base de datos 
     * utilizando su identificador único. Valida estrictamente que el personaje 
     * pertenezca al ID de la cuenta solicitante para garantizar la seguridad.
     */
    public static CompletableFuture<Boolean> eliminarPersonaje(int jugadorId, int personajeId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM personajes WHERE id = ? AND jugador_id = ?";

            try (Connection conn = ConexionBBDD.obtenerConexion();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, personajeId);
                pstmt.setInt(2, jugadorId);
                int filasAfectadas = pstmt.executeUpdate();
                return filasAfectadas > 0;

            } catch (SQLException e) {
                System.err.println("Fallo al eliminar personaje: " + e.getMessage());
                return false;
            }
        });
    }
}