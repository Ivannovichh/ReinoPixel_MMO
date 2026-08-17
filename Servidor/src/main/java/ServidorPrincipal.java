import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class ServidorPrincipal extends WebSocketServer {

    private HashMap<WebSocket, String> sesionesActivas = new HashMap<>();

    public ServidorPrincipal(InetSocketAddress address) {
        super(address);
        inicializarFirebase();
    }

    /* 
     * inicializarFirebase: Arranca la conexión con la nube usando el archivo JSON de credenciales.
     */
    private void inicializarFirebase() {
        try {

            File file = new File("./Servidor/claves.json");
            FileInputStream serviceAccount = new FileInputStream(file);

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("¡Firebase inicializado y conectado a la nube con éxito!");
            }
        } catch (IOException e) {
            System.out.println("Error crítico al inicializar Firebase. Revisa el archivo JSON.");
            e.printStackTrace();
        }
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
        String texto = new String(message.array(), java.nio.charset.StandardCharsets.UTF_8);
        procesarPaquete(conn, texto);
    }

    /* 
     * procesarPaquete: Enruta las peticiones de Registro (REG:) y Login (AUTH:) hacia Firebase.
     */
    private void procesarPaquete(WebSocket conn, String message) {
        if (message.startsWith("REG:")) {
            manejarRegistro(conn, message);
        }
        else if (message.startsWith("AUTH:")) {
            manejarAutenticacion(conn, message);
        }
        else if (message.startsWith("POS:")) {
            String coordenadas = message.substring(4);
            String usuario = sesionesActivas.getOrDefault(conn, "Desconocido");
            System.out.println("Jugador (" + usuario + ") en coords: " + coordenadas);
        }
    }

    /* 
     * manejarRegistro: Crea un usuario nuevo directamente en la nube de Firebase Auth 
     * utilizando UserRecord.CreateRequest de forma correcta.
     */
    private void manejarRegistro(WebSocket conn, String message) {
        String[] partes = message.split(":");
        if (partes.length >= 3) {
            String correo = partes[1].trim();
            String password = partes[2].trim();
            
            try {
                // Corrección: Se usa UserRecord.CreateRequest en lugar de FirebaseAuth.CreateRequest
                UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(correo)
                    .setPassword(password);
                
                UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);
                System.out.println("¡Usuario registrado en Firebase con éxito: " + userRecord.getEmail() + "!");
                conn.send("REG_OK");
                
            } catch (Exception e) {
                System.out.println("Fallo al registrar usuario en la nube: " + e.getMessage());
                conn.send("REG_ERROR: " + e.getMessage());
            }
        }
    }

    /* 
     * manejarAutenticacion: Valida la existencia del usuario en la nube de Firebase.
     */
    private void manejarAutenticacion(WebSocket conn, String message) {
        String[] partes = message.split(":");
        if (partes.length >= 3) {
            String correo = partes[1].trim();
            // Eliminamos la variable password no usada o la dejamos documentada si se prefiere,
            // aquí consultamos directamente el registro por correo en Firebase Auth.
            
            try {
                UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(correo);
                
                if (userRecord != null) {
                    sesionesActivas.put(conn, correo);
                    System.out.println("¡Login exitoso en la nube para: " + correo + "!");
                    conn.send("AUTH_OK");
                }
            } catch (Exception e) {
                System.out.println("Fallo de autenticación en la nube para: " + correo);
                conn.send("AUTH_ERROR: Usuario no encontrado o credenciales inválidas");
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor Cloud de Firebase iniciado en el puerto 8080.");
    }

    public static void main(String[] args) {
        new ServidorPrincipal(new InetSocketAddress("localhost", 8080)).start();
    }
}