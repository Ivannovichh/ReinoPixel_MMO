package red.kcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;

/**
 * KcpSession
 * Representa la sesión KCP fiable asociada a un cliente UDP concreto (una por
 * dirección remota). Envuelve la máquina de estados {@link Kcp} y actúa de
 * puente hacia el canal UDP real de Netty: alimenta los datagramas crudos
 * entrantes, entrega los paquetes de aplicación ya reensamblados y traduce
 * los envíos de vuelta en datagramas KCP correctamente formados.
 *
 * La configuración (noDelay/wndSize/mtu) DEBE coincidir exactamente con la
 * usada en el cliente C++ (mi_extension_kcp/src/kcp_client.cpp) para que
 * ambos extremos calculen el mismo RTO y ritmo de reenvío.
 */
public class KcpSession implements Kcp.KcpOutput {

    private final Kcp kcp;
    private final Channel channel;
    private final InetSocketAddress remoteAddress;
    private volatile long ultimoPaqueteMs;

    public KcpSession(int conv, Channel channel, InetSocketAddress remoteAddress) {
        this.channel = channel;
        this.remoteAddress = remoteAddress;
        this.kcp = new Kcp(conv, this);

        // --- Debe ser IDÉNTICO a la configuración del cliente C++ ---
        kcp.noDelay(1, 10, 2, 1);
        kcp.wndSize(256, 256);
        kcp.setMtu(1400);
        // --------------------------------------------------------------

        long now = System.currentTimeMillis();
        this.ultimoPaqueteMs = now;
        kcp.update((int) now);
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public long getUltimoPaqueteMs() {
        return ultimoPaqueteMs;
    }

    /** Alimenta la máquina KCP con un datagrama UDP crudo recién llegado. */
    public void feed(byte[] rawData) {
        ultimoPaqueteMs = System.currentTimeMillis();
        kcp.input(rawData);
    }

    /** Extrae, si existe, el siguiente paquete de aplicación ya reensamblado. */
    public byte[] poll() {
        return kcp.recv();
    }

    /**
     * Encola un paquete de aplicación para envío fiable y fuerza su despacho inmediato.
     *
     * IMPORTANTE: los callbacks de autenticación (CompletableFuture.thenAccept) se
     * ejecutan en un hilo del ForkJoinPool común, NO en el EventLoop de Netty que
     * procesa feed()/tick(). Como Kcp no es thread-safe, forzamos aquí que la
     * mutación real del estado KCP siempre ocurra en el EventLoop del canal,
     * igual que hace Netty internamente con channel.writeAndFlush().
     */
    public void send(byte[] appData) {
        if (channel.eventLoop().inEventLoop()) {
            enviarInterno(appData);
        } else {
            channel.eventLoop().execute(() -> enviarInterno(appData));
        }
    }

    private void enviarInterno(byte[] appData) {
        kcp.send(appData);
        long now = System.currentTimeMillis();
        kcp.update((int) now);
        kcp.flush();
    }

    /** Llamado periódicamente (cada ~10ms) para procesar ACKs y retransmisiones pendientes. */
    public void tick(long nowMs) {
        kcp.update((int) nowMs);
    }

    /** true si esta sesión lleva demasiado tiempo sin recibir tráfico (candidata a limpieza). */
    public boolean estaInactiva(long nowMs, long timeoutMs) {
        return (nowMs - ultimoPaqueteMs) > timeoutMs;
    }

    @Override
    public void output(byte[] data, int size, Kcp kcp) {
        if (!channel.isActive()) return;
        ByteBuf buf = channel.alloc().buffer(size);
        buf.writeBytes(data, 0, size);
        channel.writeAndFlush(new DatagramPacket(buf, remoteAddress));
    }
}
