import org.java_websocket.WebSocket;

/**
 * Clase JugadorServidor.
 * Representa la sesión activa de una cuenta de usuario en el servidor.
 * Mantiene la referencia a su socket de conexión y almacena en memoria 
 * el avatar (Personaje) que está controlando en el mundo en ese momento.
 */
public class JugadorServidor {
    
    private WebSocket conexion;
    private String correo;
    private int idCuenta; // El ID numérico de la tabla 'jugadores' en PostgreSQL
    private Personaje personajeActivo;

    /**
     * Constructor de la sesión del jugador.
     * Se invoca justo después de que el usuario hace un login exitoso (AUTH_OK).
     * Inicialmente, el personaje activo es nulo porque el jugador está en la 
     * pantalla de selección y aún no ha entrado al mundo 3D.
     */
    public JugadorServidor(WebSocket conexion, String correo, int idCuenta) {
        this.conexion = conexion;
        this.correo = correo;
        this.idCuenta = idCuenta;
        this.personajeActivo = null; 
    }

    /* 
     * ==========================================
     * GETTERS Y SETTERS
     * ==========================================
     */
    public WebSocket getConexion() {
        return conexion;
    }

    public String getCorreo() {
        return correo;
    }

    public int getIdCuenta() {
        return idCuenta;
    }

    public Personaje getPersonajeActivo() {
        return personajeActivo;
    }

    public void setPersonajeActivo(Personaje personajeActivo) {
        this.personajeActivo = personajeActivo;
    }
}