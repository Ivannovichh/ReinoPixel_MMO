package plantillas;

import red.Opcodes;
import red.PaqueteSalida;

/**
 * Entidad Personaje.
 * Representa el estado en memoria RAM de un avatar dentro del mundo del juego.
 * Actúa como la fuente de verdad espacial, de estadísticas y ahora de configuración 
 * estética en el servidor, procesando actualizaciones sin consultar la BBDD continuamente.
 */
public class Personaje {

    // --- Atributos Fundamentales ---
    private int id;
    private int jugadorId;
    private String nombre;
    private int nivel;
    
    // --- Coordenadas Espaciales ---
    private float posX;
    private float posY;
    private float posZ;
    
    // --- Atributos Estéticos y Cosméticos ---
    private int genero;
    private int cuerpo;
    private int pelo;
    private int formaOjos;
    private float altura;
    private float musculatura;
    private float edad;
    private String colorPiel;
    private String colorOjos;

    /**
     * Constructor principal de la entidad.
     * Instancia los datos fundamentales del personaje junto con todos sus
     * modificadores cosméticos tras ser recuperados de la base de datos.
     */
    public Personaje(int id, int jugadorId, String nombre, int nivel, float posX, float posY, float posZ,
                     int genero, int cuerpo, int pelo, int formaOjos, float altura, float musculatura, 
                     float edad, String colorPiel, String colorOjos) {
                         
        this.id = id;
        this.jugadorId = jugadorId;
        this.nombre = nombre;
        this.nivel = nivel;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        
        // Inicialización de la estética
        this.genero = genero;
        this.cuerpo = cuerpo;
        this.pelo = pelo;
        this.formaOjos = formaOjos;
        this.altura = altura;
        this.musculatura = musculatura;
        this.edad = edad;
        this.colorPiel = colorPiel;
        this.colorOjos = colorOjos;
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
    
    // Getters Estéticos
    public int getGenero() { return genero; }
    public int getCuerpo() { return cuerpo; }
    public int getPelo() { return pelo; }
    public int getFormaOjos() { return formaOjos; }
    public float getAltura() { return altura; }
    public float getMusculatura() { return musculatura; }
    public float getEdad() { return edad; }
    public String getColorPiel() { return colorPiel; }
    public String getColorOjos() { return colorOjos; }
}