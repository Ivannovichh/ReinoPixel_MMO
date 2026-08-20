import basededatos.ConexionBBDD;
import gestores.GestorAutenticacion;
import gestores.GestorPersonajes; // Se usará en la siguiente fase de personajes
import plantillas.JugadorServidor;
import red.Opcodes;
import red.PaqueteEntrada;
import red.PaqueteSalida;
import red.kcp.Kcp;
import red.kcp.KcpSession;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ServidorPrincipal
 * Clase núcleo del backend. Gestiona el bucle de red UDP nativo, mantiene el
 * registro de sesiones KCP (una por cliente) y enruta los opcodes binarios
 * una vez que KCP ha reensamblado el paquete de aplicación real.
 *
 * IMPORTANTE: el cliente (mi_extension_kcp) envía los datos a través de KCP,
 * que envuelve cada segmento en una cabecera de protocolo de 24 bytes antes
 * de mandarlo por UDP. Este servidor NO puede leer los datagramas UDP en
 * crudo como si fueran el paquete de aplicación: primero hay que pasarlos
 * por una sesión KCP (ver {@link KcpSession}) para reensamblarlos.
 */
public class ServidorPrincipal {
    private static final ConcurrentHashMap<InetSocketAddress, JugadorServidor> sesionesActivas = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<InetSocketAddress, KcpSession> sesionesKcp = new ConcurrentHashMap<>();

    // Tiempo máximo sin recibir tráfico de un cliente antes de liberar su sesión KCP.
    private static final long TIMEOUT_SESION_MS = 600_000;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(ConexionBBDD::cerrarPool));
        
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioDatagramChannel.class)
             .handler(new ChannelInitializer<NioDatagramChannel>() {
                 @Override
                 protected void initChannel(NioDatagramChannel ch) {
                     ch.pipeline().addLast(new ManejadorNettyUDP());
                 }
             });

            System.out.println("Servidor UDP Nativo (KCP) iniciado en el puerto 8080...");
            b.bind(8080).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    public static class ManejadorNettyUDP extends SimpleChannelInboundHandler<DatagramPacket> {

        /**
         * channelActive
         * En cuanto el canal UDP está listo, arrancamos un latido periódico cada
         * 10ms (mismo intervalo que configura el cliente en ikcp_nodelay) para
         * que cada sesión KCP procese ACKs y retransmisiones pendientes, igual
         * que hace el cliente en su _process()/update().
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                for (KcpSession sesion : sesionesKcp.values()) {
                    sesion.tick(now);
                }
                sesionesKcp.entrySet().removeIf(entry -> {
                    boolean inactiva = entry.getValue().estaInactiva(now, TIMEOUT_SESION_MS);
                    if (inactiva) {
                        System.out.println("KCP: sesión inactiva liberada -> " + entry.getKey());
                    }
                    return inactiva;
                });
            }, 10, 10, TimeUnit.MILLISECONDS);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            ByteBuf msg = packet.content();
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            if (bytes.length == 0) return;

            InetSocketAddress sender = packet.sender();

            // Cada datagrama UDP entrante es un paquete de PROTOCOLO KCP, no el
            // paquete de aplicación. Hay que meterlo en la sesión KCP del cliente
            // para que reensamble (con orden, ACKs y reintentos) el paquete real.
            KcpSession sesion = sesionesKcp.computeIfAbsent(sender, addr -> {
                if (bytes.length < 4) return null; // datagrama demasiado corto para tener conv
                int conv = Kcp.peekConv(bytes, 0);
                System.out.println("KCP: nueva sesión para " + addr + " (conv=" + conv + ")");
                return new KcpSession(conv, ctx.channel(), addr);
            });
            if (sesion == null) return;

            sesion.feed(bytes);

            // Puede haber más de un paquete de aplicación listo tras un solo datagrama
            byte[] appPacket;
            while ((appPacket = sesion.poll()) != null) {
                procesarPaqueteBinario(sesion, sender, new PaqueteEntrada(appPacket));
            }
        }
    }

    /**
     * procesarPaqueteBinario
     * Lee el opcode inicial (del paquete de aplicación YA reensamblado por KCP)
     * y deriva los datos al gestor correspondiente invocando su método específico.
     */
    private static void procesarPaqueteBinario(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        try {
            byte op = p.leerByte();
            System.out.println("DEBUG: Opcode detectado: " + op);
            
            switch (op) {
                case Opcodes.C_LOGIN:
                    manejarAutenticacion(sesion, sender, p);
                    break;
                    
                case Opcodes.C_REGISTRO:
                    manejarRegistro(sesion, sender, p);
                    break;
                    
                case Opcodes.C_PEDIR_PERSONAJES:
                    manejarPeticionPersonajes(sesion, sender, p);
                    break;
                    
                case Opcodes.C_CREAR_PERSONAJE:
                    manejarCreacionPersonaje(sesion, sender, p);
                    break;
                    
                case Opcodes.C_SELECCIONAR_PERSONAJE:
                    manejarSeleccionPersonaje(sesion, sender, p);
                    break;
                    
                case Opcodes.C_MOVER_PERSONAJE:
                    manejarMovimiento(sesion, sender, p);
                    break;
                    
                default:
                    System.out.println("Opcode desconocido recibido: " + op);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error procesando paquete de " + sender + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * manejarAutenticacion
     * Método interno específico para procesar los intentos de inicio de sesión.
     * Extrae las credenciales del búfer y delega la comprobación a la base de datos.
     */
    private static void manejarAutenticacion(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        try {
            String corr = p.leerString();
            String pass = p.leerString();
            
            GestorAutenticacion.autenticarJugador(corr, pass).thenAccept(auth -> {
                if (auth) {
                    sesionesActivas.put(sender, new JugadorServidor(null, corr, 1));
                    System.out.println("¡Login exitoso para: " + corr + "!");
                } else {
                    System.out.println("Login fallido para: " + corr);
                }
                
                PaqueteSalida ps = new PaqueteSalida();
                ps.escribirByte(auth ? Opcodes.S_LOGIN_OK : Opcodes.S_LOGIN_ERROR);
                enviar(sesion, ps);
            });
        } catch (Exception e) {
            System.err.println("Error procesando login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * manejarRegistro
     * Método interno específico para procesar nuevas altas de usuarios.
     * Extrae los datos y los inserta de forma segura en PostgreSQL.
     */
    private static void manejarRegistro(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        String regCorr = p.leerString();
        String regPass = p.leerString();
        GestorAutenticacion.registrarJugador(regCorr, regPass).thenAccept(res -> {
            PaqueteSalida ps = new PaqueteSalida();
            ps.escribirByte(res ? Opcodes.S_REGISTRO_OK : Opcodes.S_REGISTRO_ERROR);
            enviar(sesion, ps);
        });
    }

    /**
     * manejarPeticionPersonajes
     * Método interno encargado de procesar la solicitud de la lista de personajes.
     */
    private static void manejarPeticionPersonajes(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        System.out.println("Petición recibida: El cliente " + sender + " solicita su lista de personajes.");
        // TODO: Implementar lógica de BBDD
    }

    /**
     * manejarCreacionPersonaje
     * Método interno para procesar la creación de un nuevo avatar en el mundo.
     */
    private static void manejarCreacionPersonaje(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        System.out.println("Petición recibida: El cliente " + sender + " intenta crear un nuevo personaje.");
        // TODO: Implementar creación en BBDD
    }

    /**
     * manejarSeleccionPersonaje
     * Método interno que procesa la decisión final del jugador antes de entrar al mundo.
     */
    private static void manejarSeleccionPersonaje(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        System.out.println("Petición recibida: El cliente " + sender + " ha seleccionado un personaje.");
        // TODO: Preparar estado del jugador en el mapa
    }

    /**
     * manejarMovimiento
     * Método interno diseñado para gestionar las actualizaciones de posición del cliente.
     */
    private static void manejarMovimiento(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        System.out.println("DEBUG: Paquete de movimiento interceptado de " + sender);
        // TODO: Procesar X, Y, Z
    }

    /**
     * enviar
     * Encola el paquete de salida a través de la sesión KCP del destinatario
     * (en vez de escribir el DatagramPacket directamente), para que viaje con
     * las mismas garantías de fiabilidad y orden que usa el cliente.
     */
    private static void enviar(KcpSession sesion, PaqueteSalida ps) {
        sesion.send(ps.obtenerBytes());
    }
}