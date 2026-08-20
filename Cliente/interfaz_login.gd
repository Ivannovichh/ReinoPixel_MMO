extends Control

var cargando = false
var progreso_carga := 0.0
var simulacion_autenticacion = false

@onready var input_login_correo = $CenterContainer/CajaVertical/LineEditCorreo
@onready var input_login_pass = $CenterContainer/CajaVertical/LineEditPassword
@onready var boton_login = $CenterContainer/CajaVertical/ButtonLogin
@onready var lbl_error_login = $CenterContainer/CajaVertical/LabelError
@onready var barra_progreso_login = $ProgressBar
@onready var btn_ojo_login = $PasswordEye
@onready var boton_cerrar_login = $ButtonCerrar
@onready var boton_ir_registro = $ButtonRegistrar

var icono_ojo_abierto = preload("res://ojo.png")
var icono_ojo_cerrado = preload("res://ojo-cerrado.png")

"""
_ready
Configuración de eventos visuales y conexión de señales hacia el Autoload de red.
"""
func _ready():
	input_login_pass.secret = true
	
	boton_login.pressed.connect(self._al_pulsar_login)
	boton_cerrar_login.pressed.connect(func(): get_tree().quit())
	btn_ojo_login.pressed.connect(self._alternar_ojo_login)
	boton_ir_registro.pressed.connect(func(): get_tree().change_scene_to_file("res://InterfazRegistrar.tscn"))
	
	var boton_ayuda = get_node_or_null("ButtonAyuda")
	if boton_ayuda:
		boton_ayuda.pressed.connect(func(): OS.shell_open("https://reinopixelweb-production.up.railway.app/"))
	
	RedGlobal.evento_login.connect(self._al_recibir_respuesta_login)
	RedGlobal.conectar_al_servidor()
	barra_progreso_login.hide()

"""
_alternar_ojo_login
Conmuta la visibilidad de los caracteres ocultos por seguridad.
"""
func _alternar_ojo_login():
	input_login_pass.secret = not input_login_pass.secret
	btn_ojo_login.icon = icono_ojo_cerrado if input_login_pass.secret else icono_ojo_abierto

"""
_al_pulsar_login
Empaqueta y transmite las credenciales de la cuenta en formato binario puro.
"""
func _al_pulsar_login():
	var correo = input_login_correo.text.strip_edges()
	var pass_text = input_login_pass.text
	
	# --- AÑADE ESTO PARA VER QUÉ ESTÁS ENVIANDO ---
	print("Enviando correo desde UI: '", correo, "' | Contraseña: '", pass_text, "'")
	# ---------------------------------------------
	
	if correo == "" or pass_text == "":
		lbl_error_login.text = "Rellena el correo y la contraseña."
		return
		
	cargando = true
	progreso_carga = 15.0
	input_login_correo.editable = false
	input_login_pass.editable = false
	boton_login.disabled = true
	barra_progreso_login.show()
	barra_progreso_login.value = progreso_carga
	
	var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_LOGIN)
	RedGlobal.escribir_string_en_buffer(buffer, correo)
	RedGlobal.escribir_string_en_buffer(buffer, pass_text)
	RedGlobal.enviar_buffer(buffer)

"""
_process
Maneja en exclusiva la interpolación visual de la barra de carga.
"""
func _process(delta):
	if cargando and not simulacion_autenticacion:
		progreso_carga = min(progreso_carga + (delta * 50.0), 90.0)
		barra_progreso_login.value = progreso_carga

"""
_al_recibir_respuesta_login
Callback de red encargado de liberar la UI o cambiar a la escena de juego.
"""
func _al_recibir_respuesta_login(exito: bool):
	if exito:
		simulacion_autenticacion = true
		barra_progreso_login.value = 100.0
		await get_tree().create_timer(0.4).timeout
		get_tree().change_scene_to_file("res://SeleccionPersonaje.tscn")
	else:
		cargando = false
		simulacion_autenticacion = false
		barra_progreso_login.hide()
		input_login_correo.editable = true
		input_login_pass.editable = true
		boton_login.disabled = false
		lbl_error_login.text = "Error: Usuario o contraseña incorrectos."
