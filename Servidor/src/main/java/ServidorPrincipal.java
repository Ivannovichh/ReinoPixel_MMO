/*
 * Importación de las herramientas internas del proyecto distribuidas en paquetes.
 * Permite al núcleo de red acceder a la base de datos, entidades y gestores lógicos.
 */
import basededatos.ConexionBBDD;
import gestores.GestorAutenticacion;
import gestores.GestorPersonajes;
import plantillas.JugadorServidor;
import plantillas.Personaje;
import red.Opcodes;
import red.PaqueteEntrada;
import red.PaqueteSalida;

// Importaciones de librerías externas y Java
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;


/**
 * Clase ServidorPrincipal.
 * Núcleo del backend de red. Gestiona las conexiones entrantes a través de WebSockets
 * y enruta los paquetes de datos binarios (0s y 1s) hacia los gestores asíncronos.
 * Adaptado para soportar alto rendimiento y bajo consumo de ancho de banda.
 */
public class ServidorPrincipal extends WebSocketServer {

    private HashMap<WebSocket, JugadorServidor> sesionesActivas = new HashMap<>();

    /**
     * Constructor del servidor.
     * Define la dirección IP y el puerto de escucha utilizando la red subyacente.
     */
    public ServidorPrincipal(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Conexión abierta silenciosamente para mantener los logs limpios.
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        sesionesActivas.remove(conn);
    }

    /**
     * onMessage (Texto Plano)
     * Como hemos migrado a binario puro, las peticiones de texto plano se ignoran
     * intencionalmente. Esto evita que clientes desactualizados o intentos de 
     * inyección saturen el procesamiento del servidor.
     */
    @Override
    public void onMessage(WebSocket conn, String message) {
        // Vacío por diseño.
    }

    /**
     * onMessage (Binario)
     * Punto de entrada principal para toda la comunicación de alta velocidad.
     * Transforma el ByteBuffer nativo en nuestra herramienta PaqueteEntrada 
     * para extraer las variables de forma secuencial.
     */
    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        PaqueteEntrada paquete = new PaqueteEntrada(message.array());
        procesarPaqueteBinario(conn, paquete);
    }

    /**
     * procesarPaqueteBinario
     * Enrutador central. Extrae el primer byte (Opcode) del flujo de datos
     * y determina qué acción solicita el cliente (Godot) basándose en el diccionario.
     */
    private void procesarPaqueteBinario(WebSocket conn, PaqueteEntrada paquete) {
        byte opcode = paquete.leerByte();

        switch (opcode) {
            case Opcodes.C_REGISTRO:
                manejarRegistroBinario(conn, paquete);
                break;
            case Opcodes.C_LOGIN:
                manejarAutenticacionBinario(conn, paquete);
                break;
            case Opcodes.C_PEDIR_PERSONAJES:
                manejarPeticionPersonajesBinario(conn);
                break;
            case Opcodes.C_CREAR_PERSONAJE:
                manejarCreacionPersonajeBinario(conn, paquete);
                break;
            case Opcodes.C_SELECCIONAR_PERSONAJE:
                manejarSeleccionPersonajeBinario(conn, paquete);
                break;
            case Opcodes.C_MOVER_PERSONAJE:
                manejarMovimientoBinario(conn, paquete);
                break;
            default:
                // Ignorar opcodes desconocidos por seguridad.
                break;
        }
    }

    /**
     * manejarRegistroBinario
     * Extrae el correo y contraseña del paquete secuencial y ejecuta el registro 
     * en BBDD de forma asíncrona. Construye y envía un paquete binario de respuesta.
     */
    private void manejarRegistroBinario(WebSocket conn, PaqueteEntrada paquete) {
        String correo = paquete.leerString();
        String password = paquete.leerString();
        
        GestorAutenticacion.registrarJugador(correo, password).thenAccept(registrado -> {
            PaqueteSalida respuesta = new PaqueteSalida();
            if (registrado) {
                respuesta.escribirByte(Opcodes.S_REGISTRO_OK);
            } else {
                respuesta.escribirByte(Opcodes.S_REGISTRO_ERROR);
            }
            conn.send(respuesta.obtenerBytes());
        });
    }

    /**
     * manejarAutenticacionBinario
     * Verifica credenciales en segundo plano. Si son correctas, vincula la conexión
     * a una instancia de JugadorServidor en la memoria RAM y autoriza la entrada.
     */
    private void manejarAutenticacionBinario(WebSocket conn, PaqueteEntrada paquete) {
        String correo = paquete.leerString();
        String password = paquete.leerString();
        
        GestorAutenticacion.autenticarJugador(correo, password).thenAccept(autenticado -> {
            PaqueteSalida respuesta = new PaqueteSalida();
            if (autenticado) {
                JugadorServidor nuevoJugador = new JugadorServidor(conn, correo, 1);
                sesionesActivas.put(conn, nuevoJugador);
                respuesta.escribirByte(Opcodes.S_LOGIN_OK);
            } else {
                respuesta.escribirByte(Opcodes.S_LOGIN_ERROR);
            }
            conn.send(respuesta.obtenerBytes());
        });
    }

    /**
     * manejarPeticionPersonajesBinario
     * Solicita la lista de personajes y la empaqueta dinámicamente en binario.
     * Estructura: [Opcode] [Cantidad] -> Bucle( [Id] [JugadorId] [Nombre] [Nivel] [PosX] [PosY] [PosZ] )
     */
    private void manejarPeticionPersonajesBinario(WebSocket conn) {
        JugadorServidor jugador = sesionesActivas.get(conn);
        
        if (jugador != null) {
            System.out.println("[RED] Petición de personajes para cuenta ID: " + jugador.getIdCuenta());
            
            GestorPersonajes.cargarPersonajesDeJugador(jugador.getIdCuenta()).thenAccept(lista -> {
                System.out.println("[BBDD] Personajes encontrados en tabla: " + lista.size());
                
                PaqueteSalida respuesta = new PaqueteSalida();
                respuesta.escribirByte(Opcodes.S_LISTA_PERSONAJES);
                respuesta.escribirInt(lista.size()); 
                
                for (Personaje p : lista) {
                    respuesta.escribirInt(p.getId());
                    respuesta.escribirInt(p.getJugadorId());
                    respuesta.escribirString(p.getNombre());
                    respuesta.escribirInt(p.getNivel());
                    respuesta.escribirFloat(p.getPosX());
                    respuesta.escribirFloat(p.getPosY());
                    respuesta.escribirFloat(p.getPosZ());
                }
                conn.send(respuesta.obtenerBytes());
                System.out.println("[RED] Paquete S_LISTA_PERSONAJES enviado a Godot.");
            });
        } else {
            System.err.println("[ERROR] ¡La conexión no tiene sesión activa (jugador es null)!");
        }
    }
    
    /**
     * manejarCreacionPersonajeBinario
     * Extrae el nombre deseado del flujo binario, crea el personaje y devuelve 
     * un estado (1 para éxito, 0 para error). Si tiene éxito, solicita el refresco automático.
     */
    private void manejarCreacionPersonajeBinario(WebSocket conn, PaqueteEntrada paquete) {
        JugadorServidor jugador = sesionesActivas.get(conn);
        if (jugador != null) {
            String nombrePersonaje = paquete.leerString();
            
            GestorPersonajes.crearPersonaje(jugador.getIdCuenta(), nombrePersonaje).thenAccept(creado -> {
                PaqueteSalida respuesta = new PaqueteSalida();
                respuesta.escribirByte(Opcodes.S_CREAR_PERSONAJE_RES);
                respuesta.escribirByte(creado ? 1 : 0);
                conn.send(respuesta.obtenerBytes());
                
                if (creado) {
                    manejarPeticionPersonajesBinario(conn);
                }
            });
        }
    }

    /**
     * manejarSeleccionPersonajeBinario
     * Recibe el ID del personaje seleccionado por el usuario y lo asigna
     * como el avatar activo en la RAM del servidor para las interacciones del mundo 3D.
     */
    private void manejarSeleccionPersonajeBinario(WebSocket conn, PaqueteEntrada paquete) {
        JugadorServidor jugador = sesionesActivas.get(conn);
        if (jugador != null) {
            int idPersonajeElegido = paquete.leerInt();
            
            GestorPersonajes.cargarPersonajesDeJugador(jugador.getIdCuenta()).thenAccept(lista -> {
                for (Personaje p : lista) {
                    if (p.getId() == idPersonajeElegido) {
                        jugador.setPersonajeActivo(p);
                        System.out.println("[MUNDO] Personaje activo asignado: " + p.getNombre() + " (ID: " + p.getId() + ")");
                        break;
                    }
                }
            });
        }
    }

    /**
     * manejarMovimientoBinario
     * Función ultra rápida que actualiza en memoria las coordenadas espaciales.
     * Extrae los 3 floats (X, Y, Z) y sobrescribe los valores sin tocar la BBDD.
     */
    private void manejarMovimientoBinario(WebSocket conn, PaqueteEntrada paquete) {
        JugadorServidor jugador = sesionesActivas.get(conn);
        if (jugador != null && jugador.getPersonajeActivo() != null) {
            float x = paquete.leerFloat();
            float y = paquete.leerFloat();
            float z = paquete.leerFloat();
            
            jugador.getPersonajeActivo().actualizarPosicion(x, y, z);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // Previene volcados de memoria por desconexiones forzadas del cliente
    }

    @Override
    public void onStart() {
        System.out.println("Servidor Binario iniciado y esperando conexiones...");
    }

    /**
     * main
     * Punto de arranque de la aplicación.
     * Incorpora un gancho de apagado (Shutdown Hook) para asegurar el cierre 
     * del pool de base de datos en caso de reinicio del servicio en la nube.
     */
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConexionBBDD.cerrarPool();
        }));

        int puerto = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            puerto = Integer.parseInt(portEnv);
        }

        new ServidorPrincipal(new InetSocketAddress("0.0.0.0", puerto)).start();
    }
}