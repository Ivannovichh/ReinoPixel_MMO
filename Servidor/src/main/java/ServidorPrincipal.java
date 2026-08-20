import basededatos.ConexionBBDD;
import gestores.GestorAutenticacion;
import gestores.GestorPersonajes; // Se usará en la siguiente fase de personajes
import plantillas.JugadorServidor;
import red.Opcodes;
import red.PaqueteEntrada;
import red.PaqueteSalida;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ServidorPrincipal
 * Clase núcleo del backend. Gestiona el bucle de red UDP nativo,
 * mantiene el registro de sesiones y enruta los opcodes binarios.
 */
public class ServidorPrincipal {
    private static ConcurrentHashMap<InetSocketAddress, JugadorServidor> sesionesActivas = new ConcurrentHashMap<>();

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

            System.out.println("Servidor UDP Nativo iniciado en el puerto 8080...");
            b.bind(8080).sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    public static class ManejadorNettyUDP extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            ByteBuf msg = packet.content();
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            if (bytes.length > 0) {
                procesarPaqueteBinario(ctx, packet.sender(), new PaqueteEntrada(bytes));
            }
        }
    }

    /**
     * procesarPaqueteBinario
     * Lee el opcode inicial y deriva los datos al gestor correspondiente.
     */
    private static void procesarPaqueteBinario(ChannelHandlerContext ctx, InetSocketAddress sender, PaqueteEntrada p) {
        try {
            byte op = p.leerByte();
            System.out.println("DEBUG: Opcode detectado: " + op);
            
            switch (op) {
                case Opcodes.C_LOGIN:
                    try {
                        // Vamos a ver los bytes restantes antes de leer el string
                        System.out.println("DEBUG BYTES RESTANTES: " + p.getBuffer().remaining());
                        
                        String corr = p.leerString();
                        System.out.println("Correo leído en bruto: [" + corr + "]");
                        
                        String pass = p.leerString();
                        System.out.println("Pass leída en bruto: [" + pass + "]");
                        
                        GestorAutenticacion.autenticarJugador(corr, pass).thenAccept(auth -> {
                            if (auth) {
                                sesionesActivas.put(sender, new JugadorServidor(null, corr, 1));
                                System.out.println("¡Login exitoso para: " + corr + "!");
                            } else {
                                System.out.println("Login fallido para: " + corr);
                            }
                            
                            PaqueteSalida ps = new PaqueteSalida();
                            ps.escribirByte(auth ? Opcodes.S_LOGIN_OK : Opcodes.S_LOGIN_ERROR);
                            enviar(ctx, sender, ps);
                        });
                    } catch (Exception e) {
                        System.err.println("Error procesando login: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                    
                case Opcodes.C_REGISTRO:
                    String regCorr = p.leerString();
                    String regPass = p.leerString();
                    GestorAutenticacion.registrarJugador(regCorr, regPass).thenAccept(res -> {
                        PaqueteSalida ps = new PaqueteSalida();
                        ps.escribirByte(res ? Opcodes.S_REGISTRO_OK : Opcodes.S_REGISTRO_ERROR);
                        enviar(ctx, sender, ps);
                    });
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

    private static void enviar(ChannelHandlerContext ctx, InetSocketAddress recipient, PaqueteSalida ps) {
        byte[] bytes = ps.obtenerBytes();
        ByteBuf buf = ctx.alloc().buffer(bytes.length);
        buf.writeBytes(bytes);
        ctx.writeAndFlush(new DatagramPacket(buf, recipient));
    }
}