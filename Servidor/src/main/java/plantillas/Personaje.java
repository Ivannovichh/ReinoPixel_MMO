package plantillas;

import red.Opcodes;
import red.PaqueteSalida;
import java.util.List;

/**
 * Entidad Personaje.
 * Representa el estado en memoria RAM de un avatar dentro del mundo del juego.
 * Actúa como la fuente de verdad espacial, de estadísticas, estética y ahora de
 * rasgos (traits), procesando actualizaciones sin consultar la BBDD continuamente.
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
    
    // --- Atributos de Lore/Jugabilidad ---
    // Almacena los IDs de los rasgos (ej: ["fuerte", "agil", "miope"])
    private List<String> rasgos;

    /**
     * Constructor principal de la entidad.
     * Instancia todos los datos fundamentales, estéticos y la lista de rasgos
     * tras ser recuperados de la base de datos.
     */
    public Personaje(int id, int jugadorId, String nombre, int nivel, float posX, float posY, float posZ,
                     int genero, int cuerpo, int pelo, int formaOjos, float altura, float musculatura, 
                     float edad, String colorPiel, String colorOjos, List<String> rasgos) {
                         
        this.id = id;
        this.jugadorId = jugadorId;
        this.nombre = nombre;
        this.nivel = nivel;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        
        this.genero = genero;
        this.cuerpo = cuerpo;
        this.pelo = pelo;
        this.formaOjos = formaOjos;
        this.altura = altura;
        this.musculatura = musculatura;
        this.edad = edad;
        this.colorPiel = colorPiel;
        this.colorOjos = colorOjos;
        this.rasgos = rasgos;
    }

    /**
     * actualizarPosicion
     * Sobrescribe las coordenadas espaciales actuales del personaje en la memoria RAM
     * cuando el servidor recibe un paquete de movimiento validado.
     */
    public void actualizarPosicion(float nuevaX, float nuevaY, float nuevaZ) {
        this.posX = nuevaX;
        this.posY = nuevaY;
        this.posZ = nuevaZ;
    }

    /**
     * empaquetarPosicionBinaria
     * Construye un paquete de red con el Opcode, el ID único y las coordenadas
     * espaciales, dejándolo listo para su retransmisión al resto de jugadores.
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

    // ==========================================
    //          GETTERS Y SETTERS BÁSICOS
    // ==========================================
    
    public int getId() { return id; }
    public int getJugadorId() { return jugadorId; }
    public String getNombre() { return nombre; }
    
    public int getNivel() { return nivel; }
    public void setNivel(int nivel) { this.nivel = nivel; }
    
    public float getPosX() { return posX; }
    public float getPosY() { return posY; }
    public float getPosZ() { return posZ; }
    
    // ==========================================
    //          GETTERS ESTÉTICOS Y RASGOS
    // ==========================================
    
    public int getGenero() { return genero; }
    public int getCuerpo() { return cuerpo; }
    public int getPelo() { return pelo; }
    public int getFormaOjos() { return formaOjos; }
    public float getAltura() { return altura; }
    public float getMusculatura() { return musculatura; }
    public float getEdad() { return edad; }
    public String getColorPiel() { return colorPiel; }
    public String getColorOjos() { return colorOjos; }
    
    public List<String> getRasgos() { return rasgos; }
}