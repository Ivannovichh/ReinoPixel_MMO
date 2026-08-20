package plantillas;

import red.Opcodes;
import red.PaqueteSalida;

/**
 * Entidad Personaje.
 * Representa el estado en memoria RAM de un avatar dentro del mundo del juego.
 * Actúa como la fuente de verdad espacial y de estadísticas básicas en el servidor,
 * procesando actualizaciones sin necesidad de consultar la base de datos continuamente.
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
     * Instancia los datos fundamentales del personaje tras ser recuperados
     * de la base de datos durante el proceso de selección de avatar.
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
     * actualizarPosicion
     * Método interno modificado en cada tick de red.
     * Sobrescribe las coordenadas espaciales actuales del personaje en la memoria RAM
     * cuando el servidor recibe un paquete de movimiento validado desde el cliente KCP.
     */
    public void actualizarPosicion(float nuevaX, float nuevaY, float nuevaZ) {
        this.posX = nuevaX;
        this.posY = nuevaY;
        this.posZ = nuevaZ;
    }

    /**
     * empaquetarPosicionBinaria
     * Método genérico de serialización ultrarrápida.
     * Construye un paquete de red inyectando el Opcode correspondiente, el ID 
     * único del personaje y sus tres coordenadas espaciales en un flujo de bytes puros,
     * dejándolo listo para su retransmisión al resto de jugadores del mapa.
     */
    public byte[] empaquetarPosicionBinaria() {
        PaqueteSalida paquete = new PaqueteSalida();
        paquete.escribirByte(Opcodes.S_ACTUALIZAR_POSICION);
        paquete.escribirInt(this.id);
        paquete.escribirFloat(this.posX);
        paquete.escribirFloat(this.posY);
        paquete.escribirFloat(this.posZ);
        return paquete.obtenerBytes();
    }

    /**
     * GETTERS Y SETTERS
     * Métodos de acceso estándar para la lectura y escritura de atributos 
     * encapsulados desde otros gestores del servidor.
     */
    public int getId() { return id; }
    public int getJugadorId() { return jugadorId; }
    public String getNombre() { return nombre; }
    
    public int getNivel() { return nivel; }
    public void setNivel(int nivel) { this.nivel = nivel; }
    
    public float getPosX() { return posX; }
    public float getPosY() { return posY; }
    public float getPosZ() { return posZ; }
}