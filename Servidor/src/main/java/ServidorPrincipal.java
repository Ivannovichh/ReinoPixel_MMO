import basededatos.ConexionBBDD;
import gestores.GestorAutenticacion;
import gestores.GestorPersonajes;
import plantillas.JugadorServidor;
import plantillas.Personaje;
import red.Opcodes;
import red.PaqueteEntrada;
import red.PaqueteSalida;

import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.buffer.ByteBuf;
import java.util.concurrent.ConcurrentHashMap;

// Importaciones de KCP
import io.jpower.kcp.netty.UkcpServerChannel;
import io.jpower.kcp.netty.UkcpChannel;
import io.netty.bootstrap.Bootstrap;

public class ServidorPrincipal {

    private static ConcurrentHashMap<Channel, JugadorServidor> sesionesActivas = new ConcurrentHashMap<>();

    public static void main(String[] args) {
    Runtime.getRuntime().addShutdownHook(new Thread(ConexionBBDD::cerrarPool));

    int puerto = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
    
    EventLoopGroup group = new NioEventLoopGroup();

    try {
        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(UkcpServerChannel.class) // Usamos UkcpServerChannel
         .handler(new ChannelInitializer<UkcpChannel>() { // Usamos UkcpChannel
             @Override
             protected void initChannel(UkcpChannel ch) {
                 ch.pipeline().addLast(new ManejadorNetty());
             }
         });

        System.out.println("Servidor KCP/Netty definitivo iniciado en puerto " + puerto + "...");
        b.bind(puerto).sync().channel().closeFuture().sync();
    } catch (InterruptedException e) {
        e.printStackTrace();
    } finally {
        group.shutdownGracefully();
    }
}

    public static class ManejadorNetty extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        public void channelInactive(ChannelHandlerContext ctx) { sesionesActivas.remove(ctx.channel()); }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            procesarPaqueteBinario(ctx.channel(), new PaqueteEntrada(bytes));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { ctx.close(); }
    }

    private static void procesarPaqueteBinario(Channel ch, PaqueteEntrada p) {
        byte op = p.leerByte();
        switch (op) {
            case Opcodes.C_REGISTRO: manejarRegistroBinario(ch, p); break;
            case Opcodes.C_LOGIN: manejarAutenticacionBinario(ch, p); break;
            case Opcodes.C_PEDIR_PERSONAJES: manejarPeticionPersonajesBinario(ch); break;
            case Opcodes.C_CREAR_PERSONAJE: manejarCreacionPersonajeBinario(ch, p); break;
            case Opcodes.C_SELECCIONAR_PERSONAJE: manejarSeleccionPersonajeBinario(ch, p); break;
            case Opcodes.C_MOVER_PERSONAJE: manejarMovimientoBinario(ch, p); break;
        }
    }

    // --- MÉTODOS DE LÓGICA ---

    private static void manejarRegistroBinario(Channel ch, PaqueteEntrada p) {
        GestorAutenticacion.registrarJugador(p.leerString(), p.leerString()).thenAccept(res -> {
            PaqueteSalida ps = new PaqueteSalida();
            ps.escribirByte(res ? Opcodes.S_REGISTRO_OK : Opcodes.S_REGISTRO_ERROR);
            enviar(ch, ps);
        });
    }

    private static void manejarAutenticacionBinario(Channel ch, PaqueteEntrada p) {
        String corr = p.leerString();
        String pass = p.leerString();
        GestorAutenticacion.autenticarJugador(corr, pass).thenAccept(auth -> {
            if (auth) sesionesActivas.put(ch, new JugadorServidor(ch, corr, 1));
            PaqueteSalida ps = new PaqueteSalida();
            ps.escribirByte(auth ? Opcodes.S_LOGIN_OK : Opcodes.S_LOGIN_ERROR);
            enviar(ch, ps);
        });
    }

    private static void manejarPeticionPersonajesBinario(Channel ch) {
        JugadorServidor j = sesionesActivas.get(ch);
        if (j != null) {
            GestorPersonajes.cargarPersonajesDeJugador(j.getIdCuenta()).thenAccept(lista -> {
                PaqueteSalida ps = new PaqueteSalida();
                ps.escribirByte(Opcodes.S_LISTA_PERSONAJES);
                ps.escribirInt(lista.size());
                for (Personaje p : lista) {
                    ps.escribirInt(p.getId()); ps.escribirInt(p.getJugadorId()); ps.escribirString(p.getNombre());
                    ps.escribirInt(p.getNivel()); ps.escribirFloat(p.getPosX()); ps.escribirFloat(p.getPosY()); ps.escribirFloat(p.getPosZ());
                }
                enviar(ch, ps);
            });
        }
    }

    private static void manejarCreacionPersonajeBinario(Channel ch, PaqueteEntrada p) {
        JugadorServidor j = sesionesActivas.get(ch);
        if (j != null) {
            GestorPersonajes.crearPersonaje(j.getIdCuenta(), p.leerString()).thenAccept(res -> {
                PaqueteSalida ps = new PaqueteSalida();
                ps.escribirByte(Opcodes.S_CREAR_PERSONAJE_RES);
                ps.escribirByte(res ? 1 : 0);
                enviar(ch, ps);
                if (res) manejarPeticionPersonajesBinario(ch);
            });
        }
    }

    private static void manejarSeleccionPersonajeBinario(Channel ch, PaqueteEntrada p) {
        JugadorServidor j = sesionesActivas.get(ch);
        if (j != null) {
            int id = p.leerInt();
            GestorPersonajes.cargarPersonajesDeJugador(j.getIdCuenta()).thenAccept(lista -> {
                for (Personaje per : lista) if (per.getId() == id) j.setPersonajeActivo(per);
            });
        }
    }

    private static void manejarMovimientoBinario(Channel ch, PaqueteEntrada p) {
        JugadorServidor j = sesionesActivas.get(ch);
        if (j != null && j.getPersonajeActivo() != null) j.getPersonajeActivo().actualizarPosicion(p.leerFloat(), p.leerFloat(), p.leerFloat());
    }

    private static void enviar(Channel ch, PaqueteSalida ps) {
        byte[] bytes = ps.obtenerBytes();
        ByteBuf buf = ch.alloc().buffer(bytes.length);
        buf.writeBytes(bytes);
        ch.writeAndFlush(buf);
    }
}