package plantillas;

import io.netty.channel.Channel;

/**
 * Clase JugadorServidor.
 * Mantiene el contexto de una sesión de red activa y autenticada.
 * Actúa como puente entre el Channel de Netty y la lógica de juego.
 */
public class JugadorServidor {
    
    private Channel conexion; // Ahora usamos Channel de Netty
    private String correo;
    private int idCuenta; 
    private Personaje personajeActivo;

    /**
     * Constructor de la sesión del jugador.
     * Actualizado para aceptar un Channel de Netty en lugar de un WebSocket.
     */
    public JugadorServidor(Channel conexion, String correo, int idCuenta) {
        this.conexion = conexion;
        this.correo = correo;
        this.idCuenta = idCuenta;
        this.personajeActivo = null; 
    }

    /* GETTERS Y SETTERS */
    public Channel getConexion() { return conexion; }
    public String getCorreo() { return correo; }
    public int getIdCuenta() { return idCuenta; }
    public Personaje getPersonajeActivo() { return personajeActivo; }
    public void setPersonajeActivo(Personaje personajeActivo) { this.personajeActivo = personajeActivo; }
}