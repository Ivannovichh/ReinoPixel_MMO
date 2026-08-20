#ifndef KCP_CLIENT_H
#define KCP_CLIENT_H

#include <godot_cpp/classes/ref_counted.hpp>
#include <godot_cpp/variant/packed_byte_array.hpp>
#include <godot_cpp/variant/string.hpp>

extern "C" {
#include "ikcp.h"
}

#ifdef _WIN32
    #include <winsock2.h>
    typedef SOCKET socket_t;
#else
    #include <netinet/in.h>
    typedef int socket_t;
#endif

namespace godot {

class KCPClient : public RefCounted {
    GDCLASS(KCPClient, RefCounted)

private:
    ikcpcb *kcp = nullptr;
    socket_t sockfd;
    struct sockaddr_in server_addr;
    bool connected = false;
    uint32_t conv_id = 0;

    // Callback que KCP invoca cuando necesita enviar bytes crudos por la red.
    static int udp_output(const char *buf, int len, ikcpcb *kcp, void *user);

    // Lee todos los datagramas pendientes del socket (no bloqueante) y los
    // entrega a KCP vía ikcp_input.
    void poll_socket();

    // Extrae de KCP todos los mensajes ya reensamblados y emite la señal.
    void drain_kcp_recv();

protected:
    static void _bind_methods();

public:
    KCPClient();
    ~KCPClient();

    // Crea el socket UDP, lo pone en modo no bloqueante y arma el ikcpcb.
    // p_conv debe coincidir con el "conversation id" que espera el servidor Java.
    bool connect_to_host(const String &p_host, int p_port, int p_conv);

    void disconnect_from_host();

    // Encola datos para envío fiable a través de KCP (no envía red directamente).
    void send_packet(const PackedByteArray &p_data);

    // Debe llamarse cada frame (ej. desde _process). Avanza el reloj de KCP,
    // hace poll de la red y emite packet_received por cada mensaje completo.
    void update(double p_delta);

    bool is_connected_to_host() const;
};

}

#endif // KCP_CLIENT_H
