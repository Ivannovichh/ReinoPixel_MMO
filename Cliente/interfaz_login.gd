extends Control

# Variables de estado del sistema
var socket := WebSocketPeer.new()
var identificador_usuario = ""
var password_usuario = ""
var tipo_accion = ""
var intentando_conexion = false
var autenticado = false
var cargando = false
var progreso_carga := 0.0

# Referencias a los nodos de la interfaz según tu jerarquía actual
@onready var input_login_correo = $CenterContainer/CajaVertical/LineEditCorreo
@onready var input_login_pass = $CenterContainer/CajaVertical/LineEditPassword
@onready var boton_login = $CenterContainer/CajaVertical/ButtonLogin
@onready var lbl_error_login = $CenterContainer/CajaVertical/LabelError

@onready var barra_progreso_login = $ProgressBar
@onready var btn_ojo_login = $PasswordEye
@onready var boton_cerrar_login = $ButtonCerrar
@onready var boton_ir_registro = $ButtonRegistrar

# Recursos
var icono_ojo_abierto = preload("res://ojo.png")
var icono_ojo_cerrado = preload("res://ojo-cerrado.png")

"""
_ready
Configuración inicial de la interfaz. 
Conecta las señales de los botones y establece la conexión WebSocket inicial con Railway.
"""
func _ready():
	input_login_pass.secret = true
	
	boton_login.pressed.connect(self._al_pulsar_login)
	boton_cerrar_login.pressed.connect(func(): get_tree().quit())
	btn_ojo_login.pressed.connect(self._alternar_ojo_login)
	
	# Conexión del botón para cambiar a la escena de Registro
	boton_ir_registro.pressed.connect(self._al_ir_a_registro)
	
	socket.connect_to_url("wss://reinopixelmmo-production.up.railway.app")
	barra_progreso_login.hide()

"""
_alternar_ojo_login
Alterna la visibilidad de la contraseña en el campo de texto.
"""
func _alternar_ojo_login():
	input_login_pass.secret = not input_login_pass.secret
	btn_ojo_login.icon = icono_ojo_cerrado if input_login_pass.secret else icono_ojo_abierto

"""
_al_pulsar_login
Valida los campos y dispara la lógica de conexión.
"""
func _al_pulsar_login():
	identificador_usuario = input_login_correo.text.strip_edges()
	password_usuario = input_login_pass.text
	tipo_accion = "AUTH"
	
	if identificador_usuario == "" or password_usuario == "":
		lbl_error_login.text = "Rellena el correo y la contraseña."
		return
		
	_iniciar_proceso_red()

"""
_iniciar_proceso_red
Prepara los estados para la carga y deshabilita los controles durante la espera.
"""
func _iniciar_proceso_red():
	intentando_conexion = true
	cargando = true
	progreso_carga = 15.0
	
	input_login_correo.editable = false
	input_login_pass.editable = false
	boton_login.disabled = true
	
	barra_progreso_login.show()
	barra_progreso_login.value = progreso_carga

"""
_al_ir_a_registro
Cambia la escena actual a la pantalla de registro.
"""
func _al_ir_a_registro():
	get_tree().change_scene_to_file("res://InterfazRegistrar.tscn")

"""
_process
Mantiene el socket activo y gestiona las respuestas del servidor en Railway.
"""
func _process(delta):
	socket.poll()
	var estado = socket.get_ready_state()
	
	# Animación de carga
	if cargando and not autenticado:
		progreso_carga = min(progreso_carga + (delta * 50.0), 90.0)
		barra_progreso_login.value = progreso_carga
	
	# Envío de datos al abrir conexión
	if intentando_conexion and estado == WebSocketPeer.STATE_OPEN:
		var paquete = tipo_accion + ":" + identificador_usuario + ":" + password_usuario
		socket.put_packet(paquete.to_utf8_buffer())
		intentando_conexion = false
		
	# Procesamiento de respuesta
	if estado == WebSocketPeer.STATE_OPEN:
		while socket.get_available_packet_count() > 0:
			var respuesta = socket.get_packet().get_string_from_utf8()
			
			if respuesta.begins_with("AUTH_OK"):
				autenticado = true
				barra_progreso_login.value = 100.0
				
				await get_tree().create_timer(0.4).timeout
				
				# Transición a la pantalla de selección de personajes
				get_tree().change_scene_to_file("res://SeleccionPersonaje.tscn")
				
			elif respuesta.begins_with("AUTH_ERROR"):
				cargando = false
				barra_progreso_login.hide()
				input_login_correo.editable = true
				input_login_pass.editable = true
				boton_login.disabled = false
				lbl_error_login.text = "Error: Usuario o contraseña incorrectos."
