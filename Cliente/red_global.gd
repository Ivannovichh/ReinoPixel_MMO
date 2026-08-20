extends Node

"""
RedGlobal
Singleton (Autoload) encargado de mantener la conexión WebSocket viva a través de todas 
las escenas del juego. Centraliza el enrutamiento y la conversión de variables a 
paquetes de binario puro (bytes) usando el estándar Big Endian.
"""

signal evento_login(exito: bool)
signal evento_registro(exito: bool)
signal evento_personajes_recibidos(lista: Array)
signal evento_creacion_personaje(exito: bool)

const C_LOGIN : int = 1
const C_REGISTRO : int = 2
const C_PEDIR_PERSONAJES : int = 3
const C_CREAR_PERSONAJE : int = 4
const C_SELECCIONAR_PERSONAJE : int = 5
const C_MOVER_PERSONAJE : int = 6

const S_LOGIN_OK : int = 10
const S_LOGIN_ERROR : int = 11
const S_REGISTRO_OK : int = 12
const S_REGISTRO_ERROR : int = 13
const S_LISTA_PERSONAJES : int = 14
const S_CREAR_PERSONAJE_RES : int = 15
const S_ACTUALIZAR_POSICION : int = 16

var socket := WebSocketPeer.new()
var url_servidor := "wss://reinopixelmmo-production.up.railway.app"
var estado_conexion := WebSocketPeer.STATE_CLOSED

"""
conectar_al_servidor
Establece el túnel de comunicación principal con el backend si no existe uno previo.
"""
func conectar_al_servidor():
	if socket.get_ready_state() == WebSocketPeer.STATE_CLOSED:
		socket.connect_to_url(url_servidor)

"""
desconectar_servidor
Cierra la conexión activa de WebSocket limpiamente y reinicia el estado interno.
Ideal para cuando el usuario decides cerrar sesión y volver a la pantalla de Login.
"""
func desconectar_servidor():
	if estado_conexion != WebSocketPeer.STATE_CLOSED:
		socket.close()

"""
_process
Mantiene el latido del socket activo. Captura el flujo de bytes entrante y 
lo deriva al método de procesamiento sin congelar el hilo principal de Godot.
"""
func _process(_delta: float):
	socket.poll()
	estado_conexion = socket.get_ready_state()
	
	if estado_conexion == WebSocketPeer.STATE_OPEN:
		while socket.get_available_packet_count() > 0:
			var paquete_bytes = socket.get_packet()
			_procesar_paquete_entrante(paquete_bytes)

"""
_procesar_paquete_entrante
Lee el identificador (Opcode) del flujo binario en Big Endian e invoca 
las señales pertinentes para notificar a la interfaz de usuario.
"""
func _procesar_paquete_entrante(paquete_bytes: PackedByteArray):
	var buffer = StreamPeerBuffer.new()
	buffer.big_endian = true
	buffer.data_array = paquete_bytes
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
			print("¡Paquete S_LISTA_PERSONAJES recibido de Java!")
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
	buffer.put_16(bytes_texto.size())
	buffer.put_data(bytes_texto)

"""
_leer_string_de_buffer
Extrae la longitud de un texto inminente y decodifica la cantidad exacta de bytes.
"""
func _leer_string_de_buffer(buffer: StreamPeerBuffer) -> String:
	var longitud = buffer.get_16()
	return buffer.get_utf8_string(longitud)

"""
enviar_buffer
Empuja la totalidad del flujo de datos a través de la red hacia el backend.
"""
func enviar_buffer(buffer: StreamPeerBuffer):
	if estado_conexion == WebSocketPeer.STATE_OPEN:
		socket.put_packet(buffer.data_array)
