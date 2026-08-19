extends Node3D

# Variables de red y estado de la conexión principal
var socket := WebSocketPeer.new()

"""
_ready
Inicialización del entorno del mundo 3D. 
Configura el modo del ratón y establece de manera automática la conexión 
por WebSocket con el servidor backend en la nube.
"""
func _ready():
	Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)
	socket.connect_to_url("wss://reinopixelmmo-production.up.railway.app")
	
	# Habilitamos el movimiento del jugador directamente en esta escena de prueba del mundo
	var jugador_nodo = get_node_or_null("Jugador")
	if jugador_nodo and jugador_nodo.has_method("habilitar_movimiento"):
		jugador_nodo.habilitar_movimiento()

"""
_process
Mantiene la comunicación de red activa procesando los paquetes entrantes y 
salientes del socket de WebSocket en cada frame del juego.
"""
func _process(_delta):
	socket.poll()
	var estado = socket.get_ready_state()
	
	if estado == WebSocketPeer.STATE_OPEN:
		while socket.get_available_packet_count() > 0:
			var respuesta = socket.get_packet().get_string_from_utf8()
			# Aquí procesaremos las respuestas generales del servidor para el mundo
			print("Mensaje del servidor: ", respuesta)
