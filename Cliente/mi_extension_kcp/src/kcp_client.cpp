#include "kcp_client.h"

// Includes oficiales y correctos de godot-cpp para Godot 4
#include <godot_cpp/core/class_db.hpp>
#include <godot_cpp/core/defs.hpp> 
#include <godot_cpp/godot.hpp>
#include <godot_cpp/variant/utility_functions.hpp>
#include <godot_cpp/classes/time.hpp> // NUEVO: Obligatorio para precisión de reloj KCP

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
 * _bind_methods
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
 * KCPClient (Constructor)
 * Inicializa las estructuras de red nativas del sistema operativo (Winsock en Windows) 
 * y prepara los punteros a nulo para un arranque limpio.
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
    connected = false;
    kcp = nullptr;
}

/**
 * ~KCPClient (Destructor)
 * Se asegura de cerrar cualquier conexión activa y liberar los recursos 
 * del sistema operativo asignados al socket al destruir el nodo.
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
 * connect_to_host
 * Método genérico para establecer la conexión UDP/KCP con el servidor remoto.
 * Configura el socket en modo no bloqueante, enlaza el protocolo KCP en Fast Mode 
 * y ajusta la MTU exactamente a los estándares del servidor Java.
 */
bool KCPClient::connect_to_host(const String &p_host, int p_port, int p_conv) {
    disconnect_from_host();

    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
#ifdef _WIN32
    if (sockfd == INVALID_SOCKET) {
        UtilityFunctions::push_error("KCPClient: no se pudo crear el socket UDP.");
        return false;
    }
    u_long mode = 1; 
    ioctlsocket(sockfd, FIONBIO, &mode); // Modo No-Bloqueante
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

    // --- CONFIGURACIÓN ESTRICTA EMPAREJADA CON JAVA (FAST MODE) ---
    ikcp_nodelay(kcp, 1, 10, 2, 1);
    ikcp_wndsize(kcp, 256, 256); // Emparejado con el servidor Netty
    ikcp_setmtu(kcp, 1400);      // Emparejado con el servidor Netty
    // --------------------------------------------------------------

    connected = true;
    UtilityFunctions::print("KCPClient: C++ Enlazado a ", p_host, ":", p_port, " | CONV_ID: ", conv_id);
    return true;
}

/**
 * disconnect_from_host
 * Método interno encargado de desconectar del servidor, liberar la instancia 
 * de la máquina de estados KCP y cerrar el socket UDP.
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

bool KCPClient::is_connected_to_host() const {
    return connected;
}

// ---------------------------------------------------------------------
// Envío de Datos
// ---------------------------------------------------------------------

/**
 * udp_output
 * Método interno de callback requerido por KCP para enviar los paquetes binarios 
 * procesados directamente a través del socket UDP subyacente.
 */
int KCPClient::udp_output(const char *buf, int len, ikcpcb *kcp, void *user) {
    KCPClient *self = (KCPClient *)user;
    if (!self || !self->connected) return 0;
    
#ifdef _WIN32
    send(self->sockfd, buf, len, 0);
#else
    ::send(self->sockfd, buf, len, 0);
#endif
    return 0;
}

/**
 * send_packet
 * Método genérico expuesto a GDScript para encolar el envío de un flujo de bytes.
 * Cuenta con un seguro anti-crasheo que evita el envío de vectores vacíos.
 */
void KCPClient::send_packet(const PackedByteArray &p_data) {
    if (!connected || !kcp) return;
    if (p_data.size() == 0) return; // SEGURO: Evita que KCP colapse por arrays vacíos
    
    ikcp_send(kcp, (const char *)p_data.ptr(), p_data.size());
}

// ---------------------------------------------------------------------
// Ciclo de Actualización y Recepción
// ---------------------------------------------------------------------

/**
 * poll_socket
 * Método interno que lee de forma no bloqueante los paquetes UDP en crudo 
 * y los inyecta en el ensamblador KCP.
 */
void KCPClient::poll_socket() {
    if (!connected) return;

    char buf[2048];
    while (true) {
#ifdef _WIN32
        int n = recv(sockfd, buf, sizeof(buf), 0);
        if (n == SOCKET_ERROR) break; 
#else
        ssize_t n = ::recv(sockfd, buf, sizeof(buf), 0);
        if (n < 0) break; 
#endif
        if (n <= 0) break;
        
        ikcp_input(kcp, buf, (long)n);
    }
}

/**
 * drain_kcp_recv
 * Método interno que extrae de la cola de KCP los paquetes ya verificados, 
 * sin fragmentar y en orden, emitiéndolos hacia GDScript.
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
 * update
 * Método genérico ejecutado por fotograma (_process) desde Godot. 
 * Alimentado mediante el reloj global estricto del motor para garantizar un ping exacto.
 */
void KCPClient::update(double p_delta) {
    if (!connected || !kcp) return;

    poll_socket();

    // SOLUCIÓN AL BUG: Extraemos los milisegundos reales del reloj del motor, 
    // en lugar de depender de una variable estática vulnerable a tirones de FPS.
    uint32_t current_ms = Time::get_singleton()->get_ticks_msec();
    ikcp_update(kcp, current_ms);

    drain_kcp_recv();
}

// ---------------------------------------------------------------------
// Inicialización y Registro
// ---------------------------------------------------------------------
void initialize_kcp_types(ModuleInitializationLevel p_level) {
    if (p_level == MODULE_INITIALIZATION_LEVEL_SCENE) GDREGISTER_CLASS(KCPClient);
}

void uninitialize_kcp_types(ModuleInitializationLevel p_level) {}

extern "C" {
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