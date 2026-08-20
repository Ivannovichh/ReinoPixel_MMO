package red;

/**
 * Clase Opcodes.
 * Diccionario estático que define los identificadores numéricos de cada acción.
 * Sustituye a las cadenas de texto por un único byte,
 * acelerando drásticamente el enrutamiento de paquetes en el servidor KCP.
 */
public class Opcodes {
    
    // Paquetes del Cliente (Godot) hacia el Servidor (Java)
    public static final byte C_LOGIN = 1;
    public static final byte C_REGISTRO = 2;
    public static final byte C_PEDIR_PERSONAJES = 3;
    public static final byte C_CREAR_PERSONAJE = 4;
    public static final byte C_SELECCIONAR_PERSONAJE = 5;
    public static final byte C_MOVER_PERSONAJE = 6;
    public static final byte C_ELIMINAR_PERSONAJE = 7;

    // Paquetes del Servidor (Java) hacia el Cliente (Godot)
    public static final byte S_LOGIN_OK = 10;
    public static final byte S_LOGIN_ERROR = 11;
    public static final byte S_REGISTRO_OK = 12;
    public static final byte S_REGISTRO_ERROR = 13;
    public static final byte S_LISTA_PERSONAJES = 14;
    public static final byte S_CREAR_PERSONAJE_RES = 15;
    public static final byte S_ACTUALIZAR_POSICION = 16;
    public static final byte S_ELIMINAR_PERSONAJE_RES = 17;
}