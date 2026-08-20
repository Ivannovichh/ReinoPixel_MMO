#include "kcp_client.h"

// Includes oficiales y correctos de godot-cpp para Godot 4
#include <godot_cpp/core/class_db.hpp>
#include <godot_cpp/core/defs.hpp> // Aquí es donde vive GDE_EXPORT realmente
#include <godot_cpp/godot.hpp>
#include <godot_cpp/variant/utility_functions.hpp>

#include <cstring>

#ifdef _WIN32
    #include <ws2tcpip.h>
    #pragma comment(lib, "ws2_32.lib")
#else
    #include <arpa/inet.h>
    #include <unistd.h>
    #include <fcntl.h>
    #include <errno.h>
#endif

using namespace godot;

// ---------------------------------------------------------------------
// Setup y Enlace de Métodos
// ---------------------------------------------------------------------

/**
 * Método genérico encargado de registrar en el motor de Godot todos los métodos,
 * propiedades y señales accesibles desde GDScript para la clase KCPClient.
 */
void KCPClient::_bind_methods() {
    ClassDB::bind_method(D_METHOD("connect_to_host", "host", "port", "conv"), &KCPClient::connect_to_host);
    ClassDB::bind_method(D_METHOD("disconnect_from_host"), &KCPClient::disconnect_from_host);
    ClassDB::bind_method(D_METHOD("send_packet", "data"), &KCPClient::send_packet);
    ClassDB::bind_method(D_METHOD("update", "delta"), &KCPClient::update);
    ClassDB::bind_method(D_METHOD("is_connected_to_host"), &KCPClient::is_connected_to_host);

    ADD_SIGNAL(MethodInfo("packet_received", PropertyInfo(Variant::PACKED_BYTE_ARRAY, "data")));
    ADD_SIGNAL(MethodInfo("connection_failed"));
}

/**
 * Constructor interno de la clase. Inicializa las estructuras de red 
 * nativas del sistema operativo (Winsock en Windows) y prepara los punteros a nulo.
 */
KCPClient::KCPClient() {
#ifdef _WIN32
    sockfd = INVALID_SOCKET;
    WSADATA wsa_data;
    WSAStartup(MAKEWORD(2, 2), &wsa_data);
#else
    sockfd = -1;
#endif
    memset(&server_addr, 0, sizeof(server_addr));
}

/**
 * Destructor interno de la clase. Se asegura de cerrar cualquier conexión activa
 * y liberar los recursos del sistema operativo asignados al socket.
 */
KCPClient::~KCPClient() {
    disconnect_from_host();
#ifdef _WIN32
    WSACleanup();
#endif
}

// ---------------------------------------------------------------------
// Gestión de Conexión
// ---------------------------------------------------------------------

/**
 * Método genérico para establecer la conexión UDP/KCP con el servidor remoto.
 * Configura el socket en modo no bloqueante, resuelve la IP y arranca la instancia de KCP.
 */
bool KCPClient::connect_to_host(const String &p_host, int p_port, int p_conv) {
    disconnect_from_host();

    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
#ifdef _WIN32
    if (sockfd == INVALID_SOCKET) {
        UtilityFunctions::push_error("KCPClient: no se pudo crear el socket UDP.");
        return false;
    }
    u_long mode = 1; // non-blocking
    ioctlsocket(sockfd, FIONBIO, &mode);
#else
    if (sockfd < 0) {
        UtilityFunctions::push_error("KCPClient: no se pudo crear el socket UDP.");
        return false;
    }
    int flags = fcntl(sockfd, F_GETFL, 0);
    fcntl(sockfd, F_SETFL, flags | O_NONBLOCK);
#endif

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons((unsigned short)p_port);

    CharString host_utf8 = p_host.utf8();
    if (inet_pton(AF_INET, host_utf8.get_data(), &server_addr.sin_addr) != 1) {
        UtilityFunctions::push_error("KCPClient: host inválido: " + p_host);
        disconnect_from_host();
        return false;
    }

    if (::connect(sockfd, (struct sockaddr *)&server_addr, sizeof(server_addr)) != 0) {
        UtilityFunctions::push_error("KCPClient: fallo al asociar destino UDP.");
        disconnect_from_host();
        return false;
    }

    conv_id = (uint32_t)p_conv;
    kcp = ikcp_create(conv_id, this);
    if (!kcp) {
        UtilityFunctions::push_error("KCPClient: ikcp_create falló.");
        disconnect_from_host();
        return false;
    }
    kcp->output = &KCPClient::udp_output;

    ikcp_nodelay(kcp, 1, 10, 2, 1);
    ikcp_wndsize(kcp, 128, 128);
    ikcp_setmtu(kcp, 512);

    connected = true;
    return true;
}

/**
 * Método interno encargado de desconectar del servidor, liberar la instancia 
 * de la máquina de estados KCP y cerrar de forma segura el descriptor del socket.
 */
void KCPClient::disconnect_from_host() {
    if (kcp) {
        ikcp_release(kcp);
        kcp = nullptr;
    }
#ifdef _WIN32
    if (sockfd != INVALID_SOCKET) {
        closesocket(sockfd);
        sockfd = INVALID_SOCKET;
    }
#else
    if (sockfd >= 0) {
        close(sockfd);
        sockfd = -1;
    }
#endif
    connected = false;
}

/**
 * Devuelve un valor booleano indicando si el cliente se encuentra conectado activamente.
 */
bool KCPClient::is_connected_to_host() const {
    return connected;
}

// ---------------------------------------------------------------------
// Envío de Datos
// ---------------------------------------------------------------------

/**
 * Método interno de callback requerido por KCP para enviar los paquetes binarios 
 * procesados directamente a través del socket UDP subyacente.
 */
int KCPClient::udp_output(const char *buf, int len, ikcpcb *kcp, void *user) {
    KCPClient *self = (KCPClient *)user;
    if (!self || !self->connected) {
        return 0;
    }
#ifdef _WIN32
    send(self->sockfd, buf, len, 0);
#else
    ::send(self->sockfd, buf, len, 0);
#endif
    return 0;
}

/**
 * Método genérico expuesto a GDScript para encolar el envío de un flujo de bytes 
 * a través del protocolo KCP.
 */
void KCPClient::send_packet(const PackedByteArray &p_data) {
    if (!connected || !kcp) {
        UtilityFunctions::push_warning("KCPClient: send_packet llamado sin conexión activa.");
        return;
    }
    ikcp_send(kcp, (const char *)p_data.ptr(), p_data.size());
}

// ---------------------------------------------------------------------
// Ciclo de Actualización y Recepción
// ---------------------------------------------------------------------

/**
 * Método interno que lee de forma no bloqueante los paquetes UDP que llegan del servidor 
 * y los inyecta en la máquina de recepción de KCP mediante `ikcp_input`.
 */
void KCPClient::poll_socket() {
    if (!connected) {
        return;
    }

    char buf[2048];
    while (true) {
#ifdef _WIN32
        int n = recv(sockfd, buf, sizeof(buf), 0);
        if (n == SOCKET_ERROR) {
            break; 
        }
#else
        ssize_t n = ::recv(sockfd, buf, sizeof(buf), 0);
        if (n < 0) {
            break; 
        }
#endif
        if (n <= 0) {
            break;
        }
        ikcp_input(kcp, buf, (long)n);
    }
}

/**
 * Método interno que extrae de la cola de KCP los paquetes ya ensamblados 
 * y emite la señal correspondiente hacia GDScript para que el juego los procese.
 */
void KCPClient::drain_kcp_recv() {
    char buf[65536];
    int n;
    while ((n = ikcp_recv(kcp, buf, sizeof(buf))) > 0) {
        PackedByteArray data;
        data.resize(n);
        memcpy(data.ptrw(), buf, n);
        emit_signal("packet_received", data);
    }
}

/**
 * Método genérico ejecutado por fotograma desde Godot. Actualiza la recepción de sockets, 
 * avanza el reloj interno de KCP y procesa los reenvíos pendientes.
 */
void KCPClient::update(double p_delta) {
    if (!connected || !kcp) {
        return;
    }

    poll_socket();

    static uint32_t clock_ms = 0;
    clock_ms += (uint32_t)(p_delta * 1000.0);
    ikcp_update(kcp, clock_ms);

    drain_kcp_recv();
}

// ---------------------------------------------------------------------
// Inicialización y Registro de la GDExtension para Godot 4
// ---------------------------------------------------------------------

/**
 * Método interno de inicialización del módulo de la librería. 
 * Registra formalmente la clase KCPClient en la base de datos de clases del motor.
 */
void initialize_kcp_types(ModuleInitializationLevel p_level) {
    if (p_level == MODULE_INITIALIZATION_LEVEL_SCENE) {
        GDREGISTER_CLASS(KCPClient);
    }
}

/**
 * Método interno de limpieza del módulo cuando Godot descarga la librería.
 */
void uninitialize_kcp_types(ModuleInitializationLevel p_level) {
    if (p_level == MODULE_INITIALIZATION_LEVEL_SCENE) {
        // Limpieza de tipos si fuese necesario
    }
}

// Punto de entrada principal enlazado con el archivo .gdextension (entry_symbol = "gde_init")
extern "C" {
// Solo usamos GDE_EXPORT (sin inventos) para exportar la función limpia al compilador de Windows
GDExtensionBool GDE_EXPORT gde_init(
    GDExtensionInterfaceGetProcAddress p_get_proc_address,
    GDExtensionClassLibraryPtr p_library,
    GDExtensionInitialization *r_initialization
) {
    GDExtensionBinding::InitObject init_obj(p_get_proc_address, p_library, r_initialization);
    init_obj.register_initializer(initialize_kcp_types);
    init_obj.register_terminator(uninitialize_kcp_types);
    init_obj.set_minimum_library_initialization_level(MODULE_INITIALIZATION_LEVEL_SCENE);
    return init_obj.init();
}
}