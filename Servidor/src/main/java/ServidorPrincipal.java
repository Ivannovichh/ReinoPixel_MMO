
import basededatos.ConexionBBDD;
import gestores.GestorAutenticacion;
import gestores.GestorPersonajes;
import plantillas.JugadorServidor;
import plantillas.Personaje;
import red.Opcodes;
import red.PaqueteEntrada;
import red.PaqueteSalida;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.jpower.kcp.netty.UkcpServerChannel;
import io.jpower.kcp.netty.UkcpChannel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ServidorPrincipal
 * Clase núcleo del backend. Inicializa el motor de red Netty configurado con el protocolo KCP (UDP).
 * Mantiene el registro global de las sesiones activas y enruta los paquetes binarios entrantes.
 */
public class ServidorPrincipal {

    // Mapa concurrente y seguro para hilos que enlaza un canal de red con la sesión del jugador
    private static ConcurrentHashMap<Channel, JugadorServidor> sesionesActivas = new ConcurrentHashMap<>();

    /**
     * main
     * Método genérico de arranque de la aplicación Java.
     * Engancha el cierre seguro de la base de datos y levanta el bucle de eventos UDP/KCP.
     */
    public static void main(String[] args) {
        // Garantizamos que al apagar el servidor (Ctrl+C), el Pool de PostgreSQL se cierre limpio
        Runtime.getRuntime().addShutdownHook(new Thread(ConexionBBDD::cerrarPool));

        int puerto = 8080; // Puerto fijo para entorno local
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(UkcpServerChannel.class)
             .handler(new ChannelInitializer<UkcpChannel>() {
                 @Override
                 protected void initChannel(UkcpChannel ch) {
                     ch.pipeline().addLast(new ManejadorNetty());
                 }
             });

            System.out.println("Servidor KCP/Netty definitivo iniciado en el puerto UDP " + puerto + "...");
            
            // Bloquea el hilo principal manteniendo el servidor encendido
            b.bind(puerto).sync().channel().closeFuture().sync();
            
        } catch (InterruptedException e) {
            System.err.println("Error crítico en el bucle de red: " + e.getMessage());
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    // ---------------------------------------------------------------------
    // Manejador de Eventos de Red (Netty)
    // ---------------------------------------------------------------------

    /**
     * ManejadorNetty
     * Clase interna encargada de interceptar los eventos de conexión, desconexión
     * y llegada de bytes puros a través de los canales KCP.
     */
    public static class ManejadorNetty extends SimpleChannelInboundHandler<ByteBuf> {
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) { 
            sesionesActivas.remove(ctx.channel()); 
            System.out.println("Un cliente KCP se ha desconectado.");
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            procesarPaqueteBinario(ctx.channel(), new PaqueteEntrada(bytes));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { 
            System.err.println("Excepción en el canal KCP: " + cause.getMessage());
            ctx.close(); 
        }
    }

    // ---------------------------------------------------------------------
    // Enrutador Principal
    // ---------------------------------------------------------------------

    /**
     * procesarPaqueteBinario
     * Método interno de enrutamiento. Lee el primer byte (Opcode) del paquete
     * en formato Big Endian y deriva la carga útil al método lógico correspondiente.
     */
    private static void procesarPaqueteBinario(Channel ch, PaqueteEntrada p) {
        byte op = p.leerByte();
        
        switch (op) {
            case Opcodes.C_REGISTRO: 
                manejarRegistroBinario(ch, p); 
                break;
            case Opcodes.C_LOGIN: 
                manejarAutenticacionBinario(ch, p); 
                break;
            case Opcodes.C_PEDIR_PERSONAJES: 
                manejarPeticionPersonajesBinario(ch); 
                break;
            case Opcodes.C_CREAR_PERSONAJE: 
                manejarCreacionPersonajeBinario(ch, p); 
                break;
            case Opcodes.C_SELECCIONAR_PERSONAJE: 
                manejarSeleccionPersonajeBinario(ch, p); 
                break;
            case Opcodes.C_MOVER_PERSONAJE: 
                manejarMovimientoBinario(ch, p); 
                break;
            default:
                System.out.println("Opcode desconocido recibido: " + op);
                break;
        }
    }

    // ---------------------------------------------------------------------
    // Métodos Lógicos de Respuesta
    // ---------------------------------------------------------------------

    /**
     * manejarRegistroBinario
     * Método interno que procesa la petición de creación de una nueva cuenta.
     * Consulta a la base de datos de forma asíncrona y devuelve el resultado al cliente.
     */
    private static void manejarRegistroBinario(Channel ch, PaqueteEntrada p) {
        String correo = p.leerString();
        String password = p.leerString();
        
        GestorAutenticacion.registrarJugador(correo, password).thenAccept(res -> {
            PaqueteSalida ps = new PaqueteSalida();
            ps.escribirByte(res ? Opcodes.S_REGISTRO_OK : Opcodes.S_REGISTRO_ERROR);
            enviar(ch, ps);
        });
    }

    /**
     * manejarAutenticacionBinario
     * Método interno que valida las credenciales (BCrypt). Si son correctas, 
     * instancia el jugador en el mapa de sesiones del servidor.
     */
    private static void manejarAutenticacionBinario(Channel ch, PaqueteEntrada p) {
        String corr = p.leerString();
        String pass = p.leerString();
        
        GestorAutenticacion.autenticarJugador(corr, pass).thenAccept(auth -> {
            if (auth) {
                // Se asume un ID de cuenta temporal (1) hasta que se conecte con el DAO real
                sesionesActivas.put(ch, new JugadorServidor(ch, corr, 1));
            }
            PaqueteSalida ps = new PaqueteSalida();
            ps.escribirByte(auth ? Opcodes.S_LOGIN_OK : Opcodes.S_LOGIN_ERROR);
            enviar(ch, ps);
        });
    }

    /**
     * manejarPeticionPersonajesBinario
     * Método interno que extrae la lista de personajes asociados a la cuenta 
     * activa y los empaqueta en bytes para el menú de selección de Godot.
     */
    private static void manejarPeticionPersonajesBinario(Channel ch) {
        JugadorServidor j = sesionesActivas.get(ch);
        if (j != null) {
            GestorPersonajes.cargarPersonajesDeJugador(j.getIdCuenta()).thenAccept(lista -> {
                PaqueteSalida ps = new PaqueteSalida();
                ps.escribirByte(Opcodes.S_LISTA_PERSONAJES);
                ps.escribirInt(lista.size());
                
                for (Personaje p : lista) {
                    ps.escribirInt(p.getId()); 
                    ps.escribirInt(p.getJugadorId()); 
                    ps.escribirString(p.getNombre());
                    ps.escribirInt(p.getNivel()); 
                    ps.escribirFloat(p.getPosX()); 
                    ps.escribirFloat(p.getPosY()); 
                    ps.escribirFloat(p.getPosZ());
                }
                enviar(ch, ps);
            });
        }
    }

    /**
     * manejarCreacionPersonajeBinario
     * Método interno que inserta un nuevo avatar en la base de datos.
     * Si la creación es exitosa, automáticamente reenvía la lista actualizada al cliente.
     */
    private static void manejarCreacionPersonajeBinario(Channel ch, PaqueteEntrada p) {
        JugadorServidor j = sesionesActivas.get(ch);
        if (j != null) {
            GestorPersonajes.crearPersonaje(j.getIdCuenta(), p.leerString()).thenAccept(res -> {
                PaqueteSalida ps = new PaqueteSalida();
                ps.escribirByte(Opcodes.S_CREAR_PERSONAJE_RES);
                ps.escribirByte(res ? 1 : 0);
                enviar(ch, ps);
                
                if (res) {
                    manejarPeticionPersonajesBinario(ch);
                }
            });
        }
    }

    /**
     * manejarSeleccionPersonajeBinario
     * Método interno que enlaza el personaje elegido en la UI de Godot
     * con la sesión de red activa en el servidor para instanciarlo en el mundo.
     */
    private static void manejarSeleccionPersonajeBinario(Channel ch, PaqueteEntrada p) {
        JugadorServidor j = sesionesActivas.get(ch);
        if (j != null) {
            int id = p.leerInt();
            GestorPersonajes.cargarPersonajesDeJugador(j.getIdCuenta()).thenAccept(lista -> {
                for (Personaje per : lista) {
                    if (per.getId() == id) {
                        j.setPersonajeActivo(per);
                        break;
                    }
                }
            });
        }
    }

    /**
     * manejarMovimientoBinario
     * Método interno de altísima frecuencia. Recibe las coordenadas espaciales
     * desde el cliente KCP y actualiza la posición del avatar en memoria.
     */
    private static void manejarMovimientoBinario(Channel ch, PaqueteEntrada p) {
        JugadorServidor j = sesionesActivas.get(ch);
        if (j != null && j.getPersonajeActivo() != null) {
            j.getPersonajeActivo().actualizarPosicion(p.leerFloat(), p.leerFloat(), p.leerFloat());
        }
    }

    // ---------------------------------------------------------------------
    // Envío de Datos
    // ---------------------------------------------------------------------

    /**
     * enviar
     * Método interno final de red. Transforma la abstracción del PaqueteSalida
     * en un ByteBuf de Netty y lo empuja hacia el socket UDP/KCP del cliente.
     */
    private static void enviar(Channel ch, PaqueteSalida ps) {
        byte[] bytes = ps.obtenerBytes();
        ByteBuf buf = ch.alloc().buffer(bytes.length);
        buf.writeBytes(bytes);
        ch.writeAndFlush(buf);
    }
}