import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List; 

public class ServidorPrincipal extends WebSocketServer {

    private HashMap<WebSocket, JugadorServidor> sesionesActivas = new HashMap<>();

    public ServidorPrincipal(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Cliente conectado desde: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        sesionesActivas.remove(conn);
        System.out.println("Conexión cerrada.");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        procesarPaquete(conn, message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        String texto = new String(message.array(), StandardCharsets.UTF_8);
        procesarPaquete(conn, texto);
    }

    /* 
     * procesarPaquete: Enruta las peticiones entrantes del cliente según su prefijo.
     * Identifica si el jugador intenta registrarse (REG), iniciar sesión (AUTH) o 
     * actualizar su posición (POS), derivando el texto al método correspondiente.
     */
    private void procesarPaquete(WebSocket conn, String message) {
        if (message.startsWith("REG:")) {
            manejarRegistro(conn, message);
        }
        else if (message.startsWith("AUTH:")) {
            manejarAutenticacion(conn, message);
        }
        else if (message.startsWith("PEDIR_PERSONAJES")) { 
            manejarPeticionPersonajes(conn);
        }
        else if (message.startsWith("CREAR_PERSONAJE:")) { 
            manejarCreacionPersonaje(conn, message);
        }
        else if (message.startsWith("POS:")) {
            String coordenadas = message.substring(4);
            JugadorServidor jugador = sesionesActivas.get(conn);
            String usuario = (jugador != null) ? jugador.getCorreo() : "Desconocido";
            System.out.println("Jugador (" + usuario + ") en coords: " + coordenadas);
        }
    }

    /* 
     * manejarRegistro: Extrae las credenciales del paquete recibido y solicita 
     * la creación de la cuenta en PostgreSQL mediante el GestorAutenticacion.
     * Responde al cliente con un OK si la inserción tiene éxito, o con un ERROR 
     * si el correo ya estaba registrado previamente.
     */
    private void manejarRegistro(WebSocket conn, String message) {
        String[] partes = message.split(":");
        if (partes.length >= 3) {
            String correo = partes[1].trim();
            String password = partes[2].trim();
            
            boolean registrado = GestorAutenticacion.registrarJugador(correo, password);
            
            if (registrado) {
                System.out.println("¡Usuario registrado en BBDD con éxito: " + correo + "!");
                conn.send("REG_OK");
            } else {
                System.out.println("Fallo al registrar (posible duplicado): " + correo);
                conn.send("REG_ERROR: El correo ya existe o fallo en la nube");
            }
        }
    }

    /* 
     * manejarAutenticacion: Extrae los datos de inicio de sesión y verifica 
     * su validez contra la base de datos de PostgreSQL. Si las credenciales 
     * coinciden, vincula la sesión activa al correo y otorga el acceso.
     */
    private void manejarAutenticacion(WebSocket conn, String message) {
        String[] partes = message.split(":");
        if (partes.length >= 3) {
            String correo = partes[1].trim();
            String password = partes[2].trim();
            
            boolean autenticado = GestorAutenticacion.autenticarJugador(correo, password);
            
            if (autenticado) {
             
                JugadorServidor nuevoJugador = new JugadorServidor(conn, correo, 1);
                sesionesActivas.put(conn, nuevoJugador);
                
                System.out.println("¡Login exitoso en BBDD para: " + correo + "!");
                conn.send("AUTH_OK");
            } else {
                System.out.println("Fallo de autenticación para: " + correo);
                conn.send("AUTH_ERROR: Usuario no encontrado o credenciales inválidas");
            }
        }
    }

    /* 
     * AÑADIDO - manejarPeticionPersonajes: 
     * Consulta la BBDD a través del GestorPersonajes para obtener la lista de 
     * avatares del jugador y se los envía a Godot formateados como un string.
     */
    private void manejarPeticionPersonajes(WebSocket conn) {
        JugadorServidor jugador = sesionesActivas.get(conn);
        if (jugador != null) {
            List<Personaje> lista = GestorPersonajes.cargarPersonajesDeJugador(jugador.getIdCuenta());
            
            StringBuilder respuesta = new StringBuilder("LISTA_PERSONAJES:");
            for (Personaje p : lista) {
                respuesta.append(p.getNombre()).append(",").append(p.getNivel()).append(";");
            }
            conn.send(respuesta.toString());
        }
    }

    /* 
     * Extrae el nombre deseado para el nuevo avatar y solicita su inserción 
     * en PostgreSQL. Devuelve confirmación al cliente y refresca la lista.
     */
    private void manejarCreacionPersonaje(WebSocket conn, String message) {
        JugadorServidor jugador = sesionesActivas.get(conn);
        if (jugador != null) {
            String nombrePersonaje = message.split(":")[1].trim();
            boolean creado = GestorPersonajes.crearPersonaje(jugador.getIdCuenta(), nombrePersonaje);
            
            if (creado) {
                conn.send("CREAR_OK");
                manejarPeticionPersonajes(conn); // Refresca la lista automáticamente
            } else {
                conn.send("CREAR_ERROR: El nombre ya existe");
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor iniciado y escuchando conexiones...");
    }

    /* 
     * main: Punto de entrada de la aplicación. Configura el puerto de escucha 
     * (priorizando las variables de entorno para su despliegue en Railway) y 
     * arranca el servidor WebSocket.
     */
    public static void main(String[] args) {
        int puerto = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            puerto = Integer.parseInt(portEnv);
        }

        new ServidorPrincipal(new InetSocketAddress("0.0.0.0", puerto)).start();
    }
}