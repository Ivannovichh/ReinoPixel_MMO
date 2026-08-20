import basededatos.ConexionBBDD;
import gestores.GestorAutenticacion;
import gestores.GestorPersonajes;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ServidorPrincipal
 * Clase núcleo del backend. Gestiona el bucle de red UDP nativo, mantiene el
 * registro de sesiones KCP (una por cliente) y enruta los opcodes binarios
 * una vez que KCP ha reensamblado el paquete de aplicación real.
 */
public class ServidorPrincipal {
    private static final ConcurrentHashMap<InetSocketAddress, JugadorServidor> sesionesActivas = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<InetSocketAddress, KcpSession> sesionesKcp = new ConcurrentHashMap<>();

    // Programador de tareas en segundo plano independiente de los hilos de red
    private static final ScheduledExecutorService programadorTareas = Executors.newSingleThreadScheduledExecutor();

    // Tiempo máximo sin recibir tráfico de un cliente antes de liberar su sesión KCP.
    private static final long TIMEOUT_SESION_MS = 600_000;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(ConexionBBDD::cerrarPool));
        
        // Arrancamos el reloj de auto-guardado masivo al encender el servidor
        iniciarAutoGuardado();
        
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
            programadorTareas.shutdown();
        }
    }

    /**
     * iniciarAutoGuardado
     * Tarea programada (Cron) que se ejecuta en segundo plano. Itera sobre el mapa 
     * concurrente de jugadores activos y delega el guardado SQL asíncrono de sus 
     * posiciones a la base de datos de forma periódica para evitar rollbacks.
     */
    private static void iniciarAutoGuardado() {
        programadorTareas.scheduleAtFixedRate(() -> {
            System.out.println("SISTEMA: Ejecutando auto-guardado masivo de posiciones...");
            for (JugadorServidor jugador : sesionesActivas.values()) {
                if (jugador.getPersonajeActivo() != null) {
                    GestorPersonajes.guardarPosicionPersonaje(jugador.getPersonajeActivo());
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    public static class ManejadorNettyUDP extends SimpleChannelInboundHandler<DatagramPacket> {

        /**
         * channelActive
         * En cuanto el canal UDP está listo, arrancamos un latido periódico cada
         * 10ms (mismo intervalo que configura el cliente en ikcp_nodelay) para
         * que cada sesión KCP procese ACKs y retransmisiones pendientes.
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
                        InetSocketAddress sender = entry.getKey();
                        System.out.println("KCP: sesión inactiva liberada -> " + sender);
                        
                        // --- GUARDADO FINAL POR DESCONEXIÓN ---
                        JugadorServidor jugador = sesionesActivas.remove(sender);
                        if (jugador != null && jugador.getPersonajeActivo() != null) {
                            GestorPersonajes.guardarPosicionPersonaje(jugador.getPersonajeActivo());
                            System.out.println("Posición final guardada en BBDD para: " + jugador.getPersonajeActivo().getNombre());
                        }
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

            KcpSession sesion = sesionesKcp.computeIfAbsent(sender, addr -> {
                if (bytes.length < 4) return null; // datagrama demasiado corto
                int conv = Kcp.peekConv(bytes, 0);
                System.out.println("KCP: nueva sesión para " + addr + " (conv=" + conv + ")");
                return new KcpSession(conv, ctx.channel(), addr);
            });
            if (sesion == null) return;

            sesion.feed(bytes);

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
            
            // Ocultamos temporalmente el log de Opcode 6 para que no inunde la consola
            if (op != Opcodes.C_MOVER_PERSONAJE) {
                System.out.println("DEBUG: Opcode detectado: " + op);
            }
            
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
                
                case Opcodes.C_ELIMINAR_PERSONAJE:
                    manejarEliminacionPersonaje(sesion, sender, p);
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
     * Ahora espera recibir el ID real de la base de datos.
     */
    private static void manejarAutenticacion(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        try {
            String corr = p.leerString();
            String pass = p.leerString();
            
            GestorAutenticacion.autenticarJugador(corr, pass).thenAccept(cuentaId -> {
                if (cuentaId > 0) {
                    sesionesActivas.put(sender, new JugadorServidor(null, corr, cuentaId));
                    System.out.println("¡Login exitoso para: " + corr + " | ID de Cuenta: " + cuentaId + "!");
                    
                    PaqueteSalida ps = new PaqueteSalida();
                    ps.escribirByte(Opcodes.S_LOGIN_OK);
                    enviar(sesion, ps);
                } else {
                    System.out.println("Login fallido para: " + corr);
                    PaqueteSalida ps = new PaqueteSalida();
                    ps.escribirByte(Opcodes.S_LOGIN_ERROR);
                    enviar(sesion, ps);
                }
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
     * manejarCreacionPersonaje
     * Método interno para procesar la creación de un nuevo avatar en el mundo.
     * Lee secuencialmente el nombre y los 9 parámetros cosméticos enviados 
     * por Godot. Delega la inserción a la base de datos de forma asíncrona.
     */
    private static void manejarCreacionPersonaje(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        try {
            // 1. Lectura estricta: Mismo orden en que Godot hace los put_32 / put_float
            String nombrePersonaje = p.leerString();
            int genero = p.leerInt();
            int cuerpo = p.leerInt();
            int pelo = p.leerInt();
            int formaOjos = p.leerInt();
            float altura = p.leerFloat();
            float musculatura = p.leerFloat();
            float edad = p.leerFloat();
            String colorPiel = p.leerString();
            String colorOjos = p.leerString();
            
            // 2. Identificamos al propietario
            JugadorServidor jugador = sesionesActivas.get(sender);
            
            if (jugador != null) {
                int cuentaId = jugador.getIdCuenta(); 
                
                // 3. Enviamos a la Base de Datos todos los parámetros cosméticos
                GestorPersonajes.crearPersonaje(cuentaId, nombrePersonaje, genero, cuerpo, pelo, 
                                                formaOjos, altura, musculatura, edad, colorPiel, colorOjos)
                    .thenAccept(exito -> {
                        System.out.println("Creación de personaje '" + nombrePersonaje + "': " + (exito ? "EXITO" : "FALLO"));
                        
                        // 4. Respondemos a Godot
                        PaqueteSalida ps = new PaqueteSalida();
                        ps.escribirByte(Opcodes.S_CREAR_PERSONAJE_RES);
                        ps.escribirByte(exito ? 1 : 0);
                        enviar(sesion, ps);
                });
            }
        } catch (Exception e) {
            System.err.println("Error procesando creación de personaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * manejarPeticionPersonajes
     * Método interno encargado de procesar la solicitud de la lista de personajes.
     * Empaqueta el nombre, estadísticas y los 9 atributos estéticos en estricto orden Big Endian.
     */
    private static void manejarPeticionPersonajes(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        System.out.println("Petición recibida: El cliente " + sender + " solicita su lista de personajes.");
        
        JugadorServidor jugador = sesionesActivas.get(sender);
        
        if (jugador != null) {
            GestorPersonajes.cargarPersonajesDeJugador(jugador.getIdCuenta()).thenAccept(lista -> {
                PaqueteSalida ps = new PaqueteSalida();
                ps.escribirByte(Opcodes.S_LISTA_PERSONAJES);
                
                ps.escribirInt(lista.size());
                
                // Iteramos y serializamos todos los datos, incluyendo la estética
                for (plantillas.Personaje pers : lista) {
                    // Datos Fundamentales
                    ps.escribirInt(pers.getId());             
                    ps.escribirInt(pers.getJugadorId());      
                    ps.escribirString(pers.getNombre());      
                    ps.escribirInt(pers.getNivel());          
                    ps.escribirFloat(pers.getPosX());         
                    ps.escribirFloat(pers.getPosY());         
                    ps.escribirFloat(pers.getPosZ());         
                    
                    // Datos Cosméticos
                    ps.escribirInt(pers.getGenero());
                    ps.escribirInt(pers.getCuerpo());
                    ps.escribirInt(pers.getPelo());
                    ps.escribirInt(pers.getFormaOjos());
                    ps.escribirFloat(pers.getAltura());
                    ps.escribirFloat(pers.getMusculatura());
                    ps.escribirFloat(pers.getEdad());
                    ps.escribirString(pers.getColorPiel());
                    ps.escribirString(pers.getColorOjos());
                }
                
                enviar(sesion, ps);
                System.out.println("Lista enviada con éxito: " + lista.size() + " personajes.");
            });
        }
    }

   /**
     * manejarSeleccionPersonaje
     * Recibe el identificador único del avatar elegido por el cliente.
     */
    private static void manejarSeleccionPersonaje(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        try {
            int personajeId = p.leerInt(); 
            JugadorServidor jugador = sesionesActivas.get(sender);
            
            if (jugador != null) {
                System.out.println("Petición recibida: El cliente " + sender + " ha seleccionado el personaje ID: " + personajeId);
                
                GestorPersonajes.obtenerPersonajePorId(personajeId).thenAccept(personaje -> {
                    if (personaje != null) {
                        jugador.setPersonajeActivo(personaje);
                        System.out.println("Personaje '" + personaje.getNombre() + "' cargado exitosamente en la RAM del servidor.");
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error procesando selección de personaje: " + e.getMessage());
        }
    }

    /**
     * manejarMovimiento
     * Captura las coordenadas espaciales continuas (X, Y, Z) enviadas por el motor físico del cliente.
     */
    private static void manejarMovimiento(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        try {
            float x = p.leerFloat();
            float y = p.leerFloat();
            float z = p.leerFloat();
            
            JugadorServidor jugador = sesionesActivas.get(sender);
            
            if (jugador != null && jugador.getPersonajeActivo() != null) {
                jugador.getPersonajeActivo().actualizarPosicion(x, y, z);
            }
        } catch (Exception e) {
            // Silenciado intencionalmente
        }
    }

    /**
     * manejarEliminacionPersonaje
     * Extrae el ID del personaje a eliminar, valida la sesión activa del jugador 
     * y ejecuta el borrado de forma asíncrona en la base de datos de manera segura.
     */
    private static void manejarEliminacionPersonaje(KcpSession sesion, InetSocketAddress sender, PaqueteEntrada p) {
        try {
            int personajeId = p.leerInt();
            JugadorServidor jugador = sesionesActivas.get(sender);
            
            if (jugador != null) {
                System.out.println("Petición recibida: El cliente " + sender + " quiere borrar el personaje ID: " + personajeId);
                
                GestorPersonajes.eliminarPersonaje(jugador.getIdCuenta(), personajeId).thenAccept(exito -> {
                    System.out.println("Eliminación de personaje ID " + personajeId + ": " + (exito ? "EXITO" : "FALLO"));
                    
                    PaqueteSalida ps = new PaqueteSalida();
                    ps.escribirByte(Opcodes.S_ELIMINAR_PERSONAJE_RES);
                    ps.escribirByte(exito ? 1 : 0);
                    enviar(sesion, ps);
                });
            }
        } catch (Exception e) {
            System.err.println("Error procesando eliminación de personaje: " + e.getMessage());
        }
    }

    /**
     * enviar
     * Encola el paquete de salida a través de la sesión KCP del destinatario.
     */
    private static void enviar(KcpSession sesion, PaqueteSalida ps) {
        sesion.send(ps.obtenerBytes());
    }
}