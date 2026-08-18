extends Node3D

# -------------------------------------------------------------------------
# VARIABLES DE RED Y ESTADO
# -------------------------------------------------------------------------
var socket := WebSocketPeer.new()
var identificador_usuario = ""
var password_usuario = ""
var tipo_accion = ""
var intentando_conexion = false
var autenticado = false
var cargando = false
var progreso_carga := 0.0

# -------------------------------------------------------------------------
# NODOS DE INTERFAZ
# -------------------------------------------------------------------------
@onready var interfaz_login = $InterfazLogin
@onready var interfaz_registrar = $InterfazRegistrar

# --- Controles del Login ---
@onready var input_login_correo = $InterfazLogin/CenterContainer/CajaVertical/LineEditCorreo
@onready var input_login_pass = $InterfazLogin/CenterContainer/CajaVertical/LineEditPassword
@onready var boton_login = $InterfazLogin/CenterContainer/CajaVertical/ButtonLogin
@onready var lbl_error_login = $InterfazLogin/CenterContainer/CajaVertical/LabelError

@onready var boton_ir_registro = $InterfazLogin/ButtonRegistrar 
@onready var barra_progreso_login = $InterfazLogin/ProgressBar
@onready var btn_ojo_login = $InterfazLogin/PasswordEye 
@onready var boton_cerrar_login = $InterfazLogin/ButtonCerrar

# --- Controles del Registro ---
@onready var input_reg_correo = $InterfazRegistrar/CenterContainer/CajaVertical/LineEditCorreo
@onready var input_reg_pass = $InterfazRegistrar/CenterContainer/CajaVertical/LineEditPassword
@onready var input_reg_repetir = $InterfazRegistrar/CenterContainer/CajaVertical/LineEditConfPassword
@onready var boton_registrar = $InterfazRegistrar/CenterContainer/CajaVertical/ButtonRegistrar
@onready var boton_volver_login = $InterfazRegistrar/CenterContainer/CajaVertical/ButtonVolverALogin
@onready var lbl_error_reg = $InterfazRegistrar/CenterContainer/CajaVertical/LabelError

@onready var btn_ojo_pass = $InterfazRegistrar/PasswordEye
@onready var btn_ojo_rep = $InterfazRegistrar/PasswordEye2
@onready var boton_cerrar_reg = $InterfazRegistrar/ButtonCerrar

var icono_ojo_abierto = preload("res://ojo.png")
var icono_ojo_cerrado = preload("res://ojo-cerrado.png")

# -------------------------------------------------------------------------
# CONFIGURACIÓN INICIAL
# -------------------------------------------------------------------------

# _ready: Inicializa la visibilidad de los paneles, establece las referencias iniciales de 
# seguridad (ocultar contraseñas e iconos de los ojos), conecta todos los eventos de los botones 
# (incluyendo los de navegación y cierre del juego) y establece la conexión por WebSocket.
func _ready():
	Input.set_mouse_mode(Input.MOUSE_MODE_VISIBLE)
	
	interfaz_login.visible = true
	interfaz_registrar.visible = false
	
	input_login_pass.secret = true
	input_reg_pass.secret = true
	input_reg_repetir.secret = true
	
	# CORRECCIÓN: Asignamos el icono asumiendo que son nodos Button normales
	if btn_ojo_login: btn_ojo_login.icon = icono_ojo_cerrado
	if btn_ojo_pass: btn_ojo_pass.icon = icono_ojo_cerrado
	if btn_ojo_rep: btn_ojo_rep.icon = icono_ojo_cerrado
	
	boton_login.pressed.connect(self._al_pulsar_login)
	boton_ir_registro.pressed.connect(self._al_cambiar_a_registro)
	boton_registrar.pressed.connect(self._al_pulsar_registro)
	
	# Conexiones de los botones añadidos
	if boton_volver_login: boton_volver_login.pressed.connect(self._al_volver_login)
	if boton_cerrar_login: boton_cerrar_login.pressed.connect(self._al_pulsar_cerrar)
	if boton_cerrar_reg: boton_cerrar_reg.pressed.connect(self._al_pulsar_cerrar)
	
	if btn_ojo_login: btn_ojo_login.pressed.connect(self._alternar_ojo_login)
	if btn_ojo_pass: btn_ojo_pass.pressed.connect(self._alternar_ojo_pass)
	if btn_ojo_rep: btn_ojo_rep.pressed.connect(self._alternar_ojo_rep)
	
	if lbl_error_reg: lbl_error_reg.text = ""
	if lbl_error_login: lbl_error_login.text = ""
	
	socket.connect_to_url("wss://reinopixelmmo-production.up.railway.app")
	
	if barra_progreso_login:
		barra_progreso_login.hide()
		barra_progreso_login.value = 0.0

# -------------------------------------------------------------------------
# NAVEGACIÓN Y CONTROL DEL SISTEMA
# -------------------------------------------------------------------------

# _al_cambiar_a_registro: Alterna la vista activa ocultando la pantalla de inicio de sesión 
# y mostrando el formulario de registro, además de limpiar los mensajes de error previos.
func _al_cambiar_a_registro():
	interfaz_login.visible = false
	interfaz_registrar.visible = true
	if lbl_error_reg: lbl_error_reg.text = ""

# _al_volver_login: Regresa a la vista principal de inicio de sesión y oculta la interfaz 
# de registro, limpiando cualquier mensaje de error.
func _al_volver_login():
	interfaz_registrar.visible = false
	interfaz_login.visible = true
	if lbl_error_login: lbl_error_login.text = ""

# _al_pulsar_cerrar: Finaliza la ejecución de la aplicación cerrando la ventana 
# y terminando los procesos activos del juego de forma segura.
func _al_pulsar_cerrar():
	get_tree().quit()

# -------------------------------------------------------------------------
# CONTROLADORES DE VISIBILIDAD DE CONTRASEÑA
# -------------------------------------------------------------------------

# _alternar_ojo_login: Alterna el modo texto plano/oculto del campo de contraseña del login 
# y actualiza la propiedad 'icon' del botón para dar feedback visual al usuario.
func _alternar_ojo_login():
	input_login_pass.secret = not input_login_pass.secret
	if btn_ojo_login:
		btn_ojo_login.icon = icono_ojo_cerrado if input_login_pass.secret else icono_ojo_abierto

# _alternar_ojo_pass: Alterna el modo texto plano/oculto de la primera contraseña de registro
# y modifica el icono del botón.
func _alternar_ojo_pass():
	input_reg_pass.secret = not input_reg_pass.secret
	if btn_ojo_pass:
		btn_ojo_pass.icon = icono_ojo_cerrado if input_reg_pass.secret else icono_ojo_abierto

# _alternar_ojo_rep: Alterna el modo texto plano/oculto de la contraseña de repetición
# y modifica el icono del botón.
func _alternar_ojo_rep():
	input_reg_repetir.secret = not input_reg_repetir.secret
	if btn_ojo_rep:
		btn_ojo_rep.icon = icono_ojo_cerrado if input_reg_repetir.secret else icono_ojo_abierto

# -------------------------------------------------------------------------
# GESTIÓN DE PETICIONES
# -------------------------------------------------------------------------

# _al_pulsar_login: Extrae los datos del formulario de inicio de sesión, valida que 
# no estén vacíos y desencadena el proceso de conexión con el servidor.
func _al_pulsar_login():
	identificador_usuario = input_login_correo.text.strip_edges()
	password_usuario = input_login_pass.text
	tipo_accion = "AUTH"
	
	if lbl_error_login: lbl_error_login.text = ""
	
	if identificador_usuario == "" or password_usuario == "":
		if lbl_error_login: lbl_error_login.text = "Rellena el correo y la contraseña."
		return
		
	_iniciar_proceso_red()

# _al_pulsar_registro: Extrae y realiza validaciones exhaustivas (formato de correo, 
# longitud mínima y coincidencia de contraseñas) antes de solicitar la creación de la cuenta.
func _al_pulsar_registro():
	var correo = input_reg_correo.text.strip_edges()
	var pass1 = input_reg_pass.text
	var pass2 = input_reg_repetir.text
	
	if lbl_error_reg: lbl_error_reg.text = ""
	
	if correo == "" or not "@" in correo or not "." in correo:
		lbl_error_reg.text = "Introduce un correo válido."
		return
		
	if pass1.length() < 6:
		lbl_error_reg.text = "La contraseña debe tener mínimo 6 caracteres."
		return
		
	if pass1 != pass2:
		lbl_error_reg.text = "Las contraseñas no coinciden."
		return
		
	identificador_usuario = correo
	password_usuario = pass1
	tipo_accion = "REG"
	
	if lbl_error_reg: lbl_error_reg.text = "Registrando en la nube..."
	_iniciar_proceso_red()

# _iniciar_proceso_red: Modifica los estados internos para iniciar la espera del servidor, 
# desactiva la interfaz de usuario para evitar duplicidades de peticiones y muestra la barra de progreso.
func _iniciar_proceso_red():
	intentando_conexion = true
	cargando = true
	progreso_carga = 15.0
	
	_cambiar_estado_controles(false)
	
	if barra_progreso_login:
		barra_progreso_login.show()
		barra_progreso_login.value = progreso_carga
		
	print("Enviando petición de " + tipo_accion + " a la nube...")

# _cambiar_estado_controles: Habilita o deshabilita los controles de los formularios de 
# ambas pantallas en función de si hay una transacción de red en curso activa.
func _cambiar_estado_controles(activo: bool):
	input_login_correo.editable = activo
	input_login_pass.editable = activo
	boton_login.disabled = not activo
	boton_ir_registro.disabled = not activo
	if boton_cerrar_login: boton_cerrar_login.disabled = not activo
	
	input_reg_correo.editable = activo
	input_reg_pass.editable = activo
	input_reg_repetir.editable = activo
	boton_registrar.disabled = not activo
	if boton_volver_login: boton_volver_login.disabled = not activo
	if boton_cerrar_reg: boton_cerrar_reg.disabled = not activo

# -------------------------------------------------------------------------
# PROCESAMIENTO DE RED
# -------------------------------------------------------------------------

# _process: Mantiene activa la escucha del socket, anima el llenado progresivo 
# de la barra de carga y gestiona las respuestas de éxito o error devueltas por el servidor de Java.
func _process(delta):
	socket.poll()
	var estado = socket.get_ready_state()
	
	if cargando and not autenticado:
		progreso_carga = min(progreso_carga + (delta * 50.0), 90.0)
		if barra_progreso_login:
			barra_progreso_login.value = progreso_carga
	
	if intentando_conexion and estado == WebSocketPeer.STATE_OPEN:
		var paquete = tipo_accion + ":" + identificador_usuario + ":" + password_usuario
		socket.put_packet(paquete.to_utf8_buffer())
		intentando_conexion = false
		
	if estado == WebSocketPeer.STATE_OPEN:
		while socket.get_available_packet_count() > 0:
			var respuesta = socket.get_packet().get_string_from_utf8()
			
			if respuesta.begins_with("AUTH_OK"):
				autenticado = true
				cargando = false
				
				if barra_progreso_login:
					barra_progreso_login.value = 100.0
				
				await get_tree().create_timer(0.4).timeout
				
				if barra_progreso_login: barra_progreso_login.hide()
				interfaz_login.hide()
				interfaz_registrar.hide()
				Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)
				
				var jugador_nodo = get_node_or_null("Jugador")
				if jugador_nodo and jugador_nodo.has_method("habilitar_movimiento"):
					jugador_nodo.habilitar_movimiento()
					
			elif respuesta.begins_with("REG_OK"):
				cargando = false
				_cambiar_estado_controles(true)
				if barra_progreso_login: barra_progreso_login.hide()
				
				if lbl_error_reg: lbl_error_reg.text = "¡Registro exitoso! Inicia sesión."
				
				await get_tree().create_timer(1.5).timeout
				_al_volver_login()
				
			elif respuesta.begins_with("AUTH_ERROR") or respuesta.begins_with("REG_ERROR"):
				cargando = false
				_cambiar_estado_controles(true)
				if barra_progreso_login: barra_progreso_login.hide()
				
				if tipo_accion == "REG" and lbl_error_reg:
					lbl_error_reg.text = "Error: El correo ya existe o falló la nube."
				elif tipo_accion == "AUTH" and lbl_error_login:
					lbl_error_login.text = "Error: Usuario o contraseña incorrectos."
