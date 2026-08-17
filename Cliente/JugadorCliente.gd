extends Node3D

var socket := WebSocketPeer.new()
var identificador_usuario = ""
var password_usuario = ""
var tipo_accion = ""
var intentando_conexion = false
var autenticado = false
var cargando = false
var progreso_carga := 0.0

@onready var interfaz = $InterfazLogin
@onready var input_correo = $InterfazLogin/CenterContainer/CajaVertical/LineEditCorreo
@onready var input_password = $InterfazLogin/CenterContainer/CajaVertical/LineEditPassword
@onready var boton_login = $InterfazLogin/CenterContainer/CajaVertical/ButtonLogin
@onready var boton_registrar = $InterfazLogin/CenterContainer/CajaVertical/ButtonRegistrar
@onready var barra_progreso = $InterfazLogin/CenterContainer/CajaVertical/ProgressBar
@onready var caja_vertical = $InterfazLogin/CenterContainer/CajaVertical

func _ready():
	Input.set_mouse_mode(Input.MOUSE_MODE_VISIBLE)
	
	# Vinculamos los botones a las acciones de login y registro
	boton_login.pressed.connect(self._al_pulsar_login)
	boton_registrar.pressed.connect(self._al_pulsar_registro)
	
	socket.connect_to_url("ws://localhost:8080")
	
	if barra_progreso:
		barra_progreso.hide()
		barra_progreso.value = 0.0

# -------------------------------------------------------------------------
# _al_pulsar_login / _al_pulsar_registro: Preparan los datos para la red
# -------------------------------------------------------------------------
func _al_pulsar_login():
	_iniciar_peticion("AUTH")

func _al_pulsar_registro():
	_iniciar_peticion("REG")

func _iniciar_peticion(accion: String):
	identificador_usuario = input_correo.text.strip_edges()
	password_usuario = input_password.text.strip_edges()
	tipo_accion = accion
	
	if identificador_usuario != "" and password_usuario != "":
		intentando_conexion = true
		cargando = true
		progreso_carga = 15.0
		
		# Deshabilitamos textos y botones para bloquear la interfaz durante la carga
		_cambiar_estado_controles(false)
		
		if barra_progreso:
			barra_progreso.show()
			barra_progreso.value = progreso_carga
			
		print("Enviando petición de " + accion + " a la nube...")

# -------------------------------------------------------------------------
# _cambiar_estado_controles: Método genérico para bloquear o desbloquear la UI
# -------------------------------------------------------------------------
func _cambiar_estado_controles(activo: bool):
	input_correo.editable = activo
	input_password.editable = activo
	boton_login.disabled = not activo
	boton_registrar.disabled = not activo

# -------------------------------------------------------------------------
# _process: Gestiona la red y actualiza la barra de progreso de forma fluida
# -------------------------------------------------------------------------
func _process(delta):
	socket.poll()
	var estado = socket.get_ready_state()
	
	if cargando and not autenticado:
		progreso_carga = min(progreso_carga + (delta * 50.0), 90.0)
		if barra_progreso:
			barra_progreso.value = progreso_carga
	
	if intentando_conexion and estado == WebSocketPeer.STATE_OPEN:
		var paquete = tipo_accion + ":" + identificador_usuario + ":" + password_usuario
		socket.put_packet(paquete.to_utf8_buffer())
		intentando_conexion = false
		
	if estado == WebSocketPeer.STATE_OPEN:
		while socket.get_available_packet_count() > 0:
			var respuesta = socket.get_packet().get_string_from_utf8()
			print("Servidor dice: ", respuesta)
			
			if respuesta.begins_with("AUTH_OK"):
				autenticado = true
				cargando = false
				
				if barra_progreso:
					barra_progreso.value = 100.0
				
				print("¡Acceso concedido! Entrando al mundo...")
				await get_tree().create_timer(0.4).timeout
				
				interfaz.hide()
				Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)
				
				var jugador_nodo = get_node_or_null("Jugador")
				if jugador_nodo and jugador_nodo.has_method("habilitar_movimiento"):
					jugador_nodo.habilitar_movimiento()
					
			elif respuesta.begins_with("REG_OK"):
				cargando = false
				# Volvemos a habilitar los controles si el registro fue exitoso
				_cambiar_estado_controles(true)
				if barra_progreso:
					barra_progreso.hide()
				print("¡Registro completado con éxito! Ya puedes iniciar sesión.")
				
			elif respuesta.begins_with("AUTH_ERROR") or respuesta.begins_with("REG_ERROR"):
				cargando = false
				# Volvemos a habilitar los controles si hay un fallo para reintentar
				_cambiar_estado_controles(true)
				if barra_progreso:
					barra_progreso.hide()
				print("Error en la operación: ", respuesta)
