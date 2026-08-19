extends Control

# Variables de red y estado para el proceso de registro
var socket := WebSocketPeer.new()
var identificador_usuario = ""
var password_usuario = ""
var intentando_conexion = false
var cargando = false

# Referencias a los nodos de la interfaz según la jerarquía actual
@onready var input_reg_correo = $CenterContainer/CajaVertical/LineEditCorreo
@onready var input_reg_pass = $CenterContainer/CajaVertical/LineEditPassword
@onready var input_reg_repetir = $CenterContainer/CajaVertical/LineEditConfPassword
@onready var boton_registrar = $CenterContainer/CajaVertical/ButtonRegistrar
@onready var boton_volver_login = $CenterContainer/CajaVertical/ButtonVolverALogin
@onready var lbl_error_reg = $CenterContainer/CajaVertical/LabelError

@onready var btn_ojo_pass = $PasswordEye
@onready var btn_ojo_rep = $PasswordEye2
@onready var boton_cerrar_reg = $ButtonCerrar

# Recursos de iconos para alternar la visibilidad de las contraseñas
var icono_ojo_abierto = preload("res://ojo.png")
var icono_ojo_cerrado = preload("res://ojo-cerrado.png")

"""
_ready
Configuración inicial de la interfaz de registro.
Oculta por defecto los caracteres sensibles de las contraseñas, asigna los iconos 
iniciales a los botones de visualización y conecta todas las señales de interacción 
con los botones y la red WebSocket hacia Railway.
"""
func _ready():
	input_reg_pass.secret = true
	input_reg_repetir.secret = true
	
	if btn_ojo_pass: btn_ojo_pass.icon = icono_ojo_cerrado
	if btn_ojo_rep: btn_ojo_rep.icon = icono_ojo_cerrado
	
	boton_registrar.pressed.connect(self._al_pulsar_registro)
	if boton_volver_login: boton_volver_login.pressed.connect(self._al_volver_login)
	if boton_cerrar_reg: boton_cerrar_reg.pressed.connect(func(): get_tree().quit())
	
	if btn_ojo_pass: btn_ojo_pass.pressed.connect(self._alternar_ojo_pass)
	if btn_ojo_rep: btn_ojo_rep.pressed.connect(self._alternar_ojo_rep)
	
	if lbl_error_reg: lbl_error_reg.text = ""
	
	socket.connect_to_url("wss://reinopixelmmo-production.up.railway.app")

"""
_al_volver_login
Gestor de navegación hacia atrás.
Realiza el cambio de escena para regresar de manera limpia a la pantalla de inicio de sesión.
"""
func _al_volver_login():
	get_tree().change_scene_to_file("res://InterfazLogin.tscn")

"""
_alternar_ojo_pass
Alterna la visibilidad en texto plano o oculto de la primera contraseña de registro
y actualiza el icono correspondiente del botón.
"""
func _alternar_ojo_pass():
	input_reg_pass.secret = not input_reg_pass.secret
	if btn_ojo_pass:
		btn_ojo_pass.icon = icono_ojo_cerrado if input_reg_pass.secret else icono_ojo_abierto

"""
_alternar_ojo_rep
Alterna la visibilidad en texto plano o oculto de la contraseña de confirmación
y actualiza el icono correspondiente del botón.
"""
func _alternar_ojo_rep():
	input_reg_repetir.secret = not input_reg_repetir.secret
	if btn_ojo_rep:
		btn_ojo_rep.icon = icono_ojo_cerrado if input_reg_repetir.secret else icono_ojo_abierto

"""
_al_pulsar_registro
Valida de forma exhaustiva los campos del formulario (formato de correo, longitud 
mínima de clave y coincidencia entre ambas contraseñas) antes de iniciar el flujo de red.
"""
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
	
	if lbl_error_reg: lbl_error_reg.text = "Registrando en la nube..."
	_iniciar_proceso_red()

"""
_iniciar_proceso_red
Bloquea temporalmente los controles del formulario para evitar múltiples envíos 
simultáneos y marca los indicadores de estado para conectar con el servidor.
"""
func _iniciar_proceso_red():
	intentando_conexion = true
	cargando = true
	_cambiar_estado_controles(false)

"""
_cambiar_estado_controles
Habilita o deshabilita los elementos interactivos de la interfaz durante 
las transacciones de red para asegurar la integridad de los datos.
"""
func _cambiar_estado_controles(activo: bool):
	input_reg_correo.editable = activo
	input_reg_pass.editable = activo
	input_reg_repetir.editable = activo
	boton_registrar.disabled = not activo
	if boton_volver_login: boton_volver_login.disabled = not activo
	if boton_cerrar_reg: boton_cerrar_reg.disabled = not activo

"""
_process
Mantiene la escucha activa del canal WebSocket, gestiona el envío de la petición 
de registro una vez abierta la conexión y procesa las respuestas del backend en Railway.
"""
func _process(_delta):
	socket.poll()
	var estado = socket.get_ready_state()
	
	if intentando_conexion and estado == WebSocketPeer.STATE_OPEN:
		var paquete = "REG:" + identificador_usuario + ":" + password_usuario
		socket.put_packet(paquete.to_utf8_buffer())
		intentando_conexion = false
		
	if estado == WebSocketPeer.STATE_OPEN:
		while socket.get_available_packet_count() > 0:
			var respuesta = socket.get_packet().get_string_from_utf8()
			
			if respuesta.begins_with("REG_OK"):
				cargando = false
				_cambiar_estado_controles(true)
				
				if lbl_error_reg: 
					lbl_error_reg.text = "¡Registro exitoso! Redirigiendo..."
				
				await get_tree().create_timer(1.5).timeout
				_al_volver_login()
				
			elif respuesta.begins_with("REG_ERROR"):
				cargando = false
				_cambiar_estado_controles(true)
				if lbl_error_reg: 
					lbl_error_reg.text = "Error: El correo ya existe o falló la nube."
