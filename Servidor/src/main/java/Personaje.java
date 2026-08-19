
/**
 * Entidad Personaje.
 * Representa el estado en memoria RAM de un avatar dentro del mundo del juego.
 * Mantiene la información sincronizada y gestiona las actualizaciones de posición 
 * recibidas desde los clientes (Godot) para evitar saturar PostgreSQL con consultas 
 * continuas cada vez que alguien da un paso.
 */
public class Personaje {

    private int id;
    private int jugadorId;
    private String nombre;
    private int nivel;
    private float posX;
    private float posY;
    private float posZ;

    /**
     * Constructor principal de la entidad.
     * Instancia un nuevo personaje en la memoria del servidor. Generalmente 
     * se invoca tras realizar un SELECT a la base de datos cuando el jugador 
     * selecciona su avatar para entrar al mundo.
     */
    public Personaje(int id, int jugadorId, String nombre, int nivel, float posX, float posY, float posZ) {
        this.id = id;
        this.jugadorId = jugadorId;
        this.nombre = nombre;
        this.nivel = nivel;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    /**
     * Actualiza las coordenadas espaciales del personaje de forma eficiente.
     * Este método será llamado por el gestor de WebSockets cada vez que el 
     * cliente envíe un paquete de movimiento (ej. paquete POS). Sobrescribe 
     * la ubicación anterior para mantener al servidor como autoridad del estado actual.
     */
    public void actualizarPosicion(float nuevaX, float nuevaY, float nuevaZ) {
        this.posX = nuevaX;
        this.posY = nuevaY;
        this.posZ = nuevaZ;
    }

    /**
     * Empaqueta la posición del jugador en un formato de texto ligero.
     * Este método facilita la creación de cadenas (strings) que luego se 
     * enviarán por WebSocket (broadcast) a los demás jugadores cercanos para 
     * que vean a este personaje moverse en sus pantallas.
     */
    public String obtenerPaquetePosicion() {
        return "POS:" + this.id + ":" + this.posX + ":" + this.posY + ":" + this.posZ;
    }

    /* 
     * ==========================================
     * GETTERS Y SETTERS
     * Métodos de acceso estándar para recuperar o  
     * modificar los atributos desde otras clases.
     * ==========================================
     */

    public int getId() {
        return id;
    }

    public int getJugadorId() {
        return jugadorId;
    }

    public String getNombre() {
        return nombre;
    }

    public int getNivel() {
        return nivel;
    }

    public void setNivel(int nivel) {
        this.nivel = nivel;
    }

    public float getPosX() {
        return posX;
    }

    public float getPosY() {
        return posY;
    }

    public float getPosZ() {
        return posZ;
    }
}