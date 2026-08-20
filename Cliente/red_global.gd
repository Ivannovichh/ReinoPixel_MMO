extends Node

"""
RedGlobal
Singleton (Autoload) encargado de gestionar el cliente de red nativo (KCPClient). 
Centraliza el enrutamiento, mantiene el latido UDP y convierte variables a 
paquetes de binario puro (bytes) usando el estándar Big Endian para comunicarse 
con el servidor Java.
"""

# Señales de interfaz
signal evento_login(exito: bool)
signal evento_registro(exito: bool)
signal evento_personajes_recibidos(lista: Array)
signal evento_creacion_personaje(exito: bool)

# Opcodes de salida (Godot -> Java)
const C_LOGIN : int = 1
const C_REGISTRO : int = 2
const C_PEDIR_PERSONAJES : int = 3
const C_CREAR_PERSONAJE : int = 4
const C_SELECCIONAR_PERSONAJE : int = 5
const C_MOVER_PERSONAJE : int = 6

# Opcodes de entrada (Java -> Godot)
const S_LOGIN_OK : int = 10
const S_LOGIN_ERROR : int = 11
const S_REGISTRO_OK : int = 12
const S_REGISTRO_ERROR : int = 13
const S_LISTA_PERSONAJES : int = 14
const S_CREAR_PERSONAJE_RES : int = 15
const S_ACTUALIZAR_POSICION : int = 16

# Cliente KCP (GDExtension nativa compilada en C++)
var kcp_client = null
var ip_servidor := "127.0.0.1" 
var puerto_servidor := 8080
var conv_id := 1 # Identificador de conversación único para esta sesión KCP

"""
_ready
Método genérico de inicialización. Instancia el objeto KCPClient desde 
la base de datos de Godot y conecta sus señales de recepción nativas a 
los métodos de procesamiento locales de GDScript.
"""
func _ready():
	if ClassDB.class_exists("KCPClient"):
		kcp_client = ClassDB.instantiate("KCPClient")
		# Conectamos la señal que emite C++ con nuestra función de lectura
		kcp_client.connect("packet_received", Callable(self, "_on_packet_received"))
		print("RedGlobal: KCPClient nativo instanciado correctamente.")
	else:
		print("ERROR CRÍTICO: La clase nativa KCPClient no fue encontrada en Godot.")

"""
conectar_al_servidor
Establece el túnel UDP de comunicación principal con el backend si no existe 
uno previo, arrancando la máquina de estados KCP.
"""
func conectar_al_servidor():
	if kcp_client and not kcp_client.is_connected_to_host():
		var exito = kcp_client.connect_to_host(ip_servidor, puerto_servidor, conv_id)
		if exito:
			print("RedGlobal: Conexión KCP iniciada hacia ", ip_servidor, ":", str(puerto_servidor))
		else:
			print("RedGlobal: Falló el intento de conexión KCP.")

"""
desconectar_servidor
Cierra la conexión activa limpiamente y libera el socket del sistema operativo.
Ideal para cuando el usuario decide cerrar sesión y volver a la pantalla de Login.
"""
func desconectar_servidor():
	if kcp_client and kcp_client.is_connected_to_host():
		kcp_client.disconnect_from_host()
		print("RedGlobal: Desconectado del servidor.")

"""
_process
Mantiene el latido del cliente KCP. Llama al método C++ encargado de despachar 
paquetes y enviar los ACKs de confirmación correspondientes.
"""
func _process(delta: float):
	if kcp_client:
		kcp_client.update(delta)

"""
_on_packet_received
Método interno que actúa como puente. Se dispara automáticamente cuando el 
cliente KCP ensambla un paquete UDP completo. Delega la lectura de bytes al procesador.
"""
func _on_packet_received(data: PackedByteArray):
	_procesar_paquete_entrante(data)

"""
_procesar_paquete_entrante
Lee el identificador (Opcode) del flujo binario en Big Endian e invoca 
las señales pertinentes para notificar a la interfaz de usuario o actualizar entidades.
"""
func _procesar_paquete_entrante(paquete_bytes: PackedByteArray):
	var buffer = StreamPeerBuffer.new()
	buffer.big_endian = true
	buffer.data_array = paquete_bytes
	
	# Seguridad: evitamos leer si el paquete llega vacío o corrupto
	if buffer.get_size() < 1:
		return
		
	var opcode = buffer.get_8()
	
	match opcode:
		S_LOGIN_OK: evento_login.emit(true)
		S_LOGIN_ERROR: evento_login.emit(false)
		S_REGISTRO_OK: evento_registro.emit(true)
		S_REGISTRO_ERROR: evento_registro.emit(false)
		S_CREAR_PERSONAJE_RES:
			var exito = buffer.get_8() == 1 
			evento_creacion_personaje.emit(exito)
		S_LISTA_PERSONAJES:
			print("¡Paquete S_LISTA_PERSONAJES recibido por KCP!")
			var cantidad = buffer.get_32() 
			print("Cantidad de personajes declarada: ", cantidad)
			var lista = []
			
			for i in range(cantidad):
				var p_id = buffer.get_32()
				var p_jugador_id = buffer.get_32()
				var p_nombre = _leer_string_de_buffer(buffer)
				var p_nivel = buffer.get_32()
				var p_x = buffer.get_float()
				var p_y = buffer.get_float()
				var p_z = buffer.get_float()
				
				print("Cargando personaje: ", p_nombre)
				lista.append({
					"id": p_id,
					"jugador_id": p_jugador_id,
					"nombre": p_nombre,
					"nivel": p_nivel,
					"pos_x": p_x,
					"pos_y": p_y,
					"pos_z": p_z
				})
				
			evento_personajes_recibidos.emit(lista)

"""
crear_buffer_salida
Reserva un espacio en memoria e inyecta el identificador (Opcode) de la acción
configurado en Big Endian para asegurar la compatibilidad con el servidor Java.
"""
func crear_buffer_salida(opcode: int) -> StreamPeerBuffer:
	var buffer = StreamPeerBuffer.new()
	buffer.big_endian = true
	buffer.put_8(opcode)
	return buffer

"""
escribir_string_en_buffer
Transforma una cadena de texto en bytes UTF-8 y le antepone su longitud (16 bits).
"""
func escribir_string_en_buffer(buffer: StreamPeerBuffer, texto: String):
	var bytes_texto = texto.to_utf8_buffer()
	buffer.put_16(bytes_texto.size()) # Escribe 2 bytes para la longitud exacta
	buffer.put_data(bytes_texto)     # Escribe los bytes del texto sin nulos añadidos

"""
_leer_string_de_buffer
Extrae la longitud de un texto inminente y decodifica la cantidad exacta de bytes.
"""
func _leer_string_de_buffer(buffer: StreamPeerBuffer) -> String:
	var longitud = buffer.get_16()
	return buffer.get_utf8_string(longitud)

"""
enviar_buffer
Empuja la totalidad del flujo de datos de forma ultra-rápida a través 
de la librería KCP nativa compilada en C++.
"""
func enviar_buffer(buffer: StreamPeerBuffer):
	if kcp_client and kcp_client.is_connected_to_host():
		# Nos aseguramos de rebobinar el buffer al inicio absoluto antes de extraer los datos
		buffer.seek(0)
		var datos_limpios = buffer.get_data(buffer.get_size())[1] # Extrae todo el array desde el inicio real
		kcp_client.send_packet(datos_limpios)
