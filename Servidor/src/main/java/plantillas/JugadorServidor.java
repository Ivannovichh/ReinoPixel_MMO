package plantillas;

import io.netty.channel.Channel;

/**
 * Clase JugadorServidor.
 * Mantiene el contexto de una sesión de red activa y autenticada en la memoria.
 * Actúa como puente vinculante entre el canal físico de Netty (UDP/KCP) y la lógica 
 * interna del juego, enlazando la conexión con los datos de la base de datos.
 */
public class JugadorServidor {
    
    private Channel conexion; 
    private String correo;
    private int idCuenta; 
    private Personaje personajeActivo;

    /**
     * Constructor principal de la sesión.
     * Se invoca automáticamente cuando un usuario supera con éxito la fase
     * de validación (Login). Inicializa la conexión de Netty y establece 
     * el estado inicial del jugador sin ningún avatar cargado en el mundo.
     */
    public JugadorServidor(Channel conexion, String correo, int idCuenta) {
        this.conexion = conexion;
        this.correo = correo;
        this.idCuenta = idCuenta;
        this.personajeActivo = null; 
    }

    /**
     * GETTERS Y SETTERS
     * Métodos de acceso estándar para recuperar la vía de comunicación del cliente,
     * sus identificadores de cuenta y gestionar la asignación del avatar actual 
     * que está controlando en el mundo 3D.
     */
    public Channel getConexion() { return conexion; }
    public String getCorreo() { return correo; }
    public int getIdCuenta() { return idCuenta; }
    
    public Personaje getPersonajeActivo() { return personajeActivo; }
    public void setPersonajeActivo(Personaje personajeActivo) { this.personajeActivo = personajeActivo; }
}