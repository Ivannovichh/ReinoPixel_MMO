package plantillas;

import org.java_websocket.WebSocket;

/**
 * Clase JugadorServidor.
 * Mantiene el contexto de una sesión de red activa y autenticada.
 * Actúa como puente entre la conexión cruda del WebSocket y la lógica de juego,
 * almacenando las credenciales de la cuenta y el avatar que se está controlando.
 */
public class JugadorServidor {
    
    private WebSocket conexion;
    private String correo;
    private int idCuenta; 
    private Personaje personajeActivo;

    /**
     * Constructor de la sesión del jugador.
     * Se inicializa inmediatamente después de que el gestor de autenticación
     * valida las credenciales. El personaje activo comienza nulo hasta que 
     * el cliente selecciona uno explícitamente.
     */
    public JugadorServidor(WebSocket conexion, String correo, int idCuenta) {
        this.conexion = conexion;
        this.correo = correo;
        this.idCuenta = idCuenta;
        this.personajeActivo = null; 
    }

    /* GETTERS Y SETTERS ESTÁNDAR */
    public WebSocket getConexion() { return conexion; }
    public String getCorreo() { return correo; }
    public int getIdCuenta() { return idCuenta; }
    public Personaje getPersonajeActivo() { return personajeActivo; }
    public void setPersonajeActivo(Personaje personajeActivo) { this.personajeActivo = personajeActivo; }
}