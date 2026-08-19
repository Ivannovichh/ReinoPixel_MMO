extends Control

@onready var input_reg_correo = $CenterContainer/CajaVertical/LineEditCorreo
@onready var input_reg_pass = $CenterContainer/CajaVertical/LineEditPassword
@onready var input_reg_repetir = $CenterContainer/CajaVertical/LineEditConfPassword
@onready var boton_registrar = $CenterContainer/CajaVertical/ButtonRegistrar
@onready var boton_volver_login = $CenterContainer/CajaVertical/ButtonVolverALogin
@onready var lbl_error_reg = $CenterContainer/CajaVertical/LabelError
@onready var btn_ojo_pass = $PasswordEye
@onready var btn_ojo_rep = $PasswordEye2
@onready var boton_cerrar_reg = $ButtonCerrar

var icono_ojo_abierto = preload("res://ojo.png")
var icono_ojo_cerrado = preload("res://ojo-cerrado.png")

"""
_ready
Asignación de métodos a los botones interactivos y enlace al gestor de red.
"""
func _ready():
	input_reg_pass.secret = true
	input_reg_repetir.secret = true
	
	if btn_ojo_pass: btn_ojo_pass.icon = icono_ojo_cerrado
	if btn_ojo_rep: btn_ojo_rep.icon = icono_ojo_cerrado
	
	boton_registrar.pressed.connect(self._al_pulsar_registro)
	if boton_volver_login: boton_volver_login.pressed.connect(func(): get_tree().change_scene_to_file("res://InterfazLogin.tscn"))
	if boton_cerrar_reg: boton_cerrar_reg.pressed.connect(func(): get_tree().quit())
	
	if btn_ojo_pass: btn_ojo_pass.pressed.connect(func(): _alternar_ojo(input_reg_pass, btn_ojo_pass))
	if btn_ojo_rep: btn_ojo_rep.pressed.connect(func(): _alternar_ojo(input_reg_repetir, btn_ojo_rep))
	
	if lbl_error_reg: lbl_error_reg.text = ""
	
	RedGlobal.evento_registro.connect(self._al_recibir_respuesta_registro)

"""
_alternar_ojo
Permite visualizar momentáneamente las contraseñas introducidas.
"""
func _alternar_ojo(input_node, btn_node):
	input_node.secret = not input_node.secret
	btn_node.icon = icono_ojo_cerrado if input_node.secret else icono_ojo_abierto

"""
_al_pulsar_registro
Filtra los datos por reglas de negocio básicas antes de enviarlos comprimidos al servidor.
"""
func _al_pulsar_registro():
	var correo = input_reg_correo.text.strip_edges()
	var pass1 = input_reg_pass.text
	var pass2 = input_reg_repetir.text
	
	if correo == "" or not "@" in correo or not "." in correo:
		lbl_error_reg.text = "Introduce un correo válido."
		return
	if pass1.length() < 6:
		lbl_error_reg.text = "La contraseña debe tener mínimo 6 caracteres."
		return
	if pass1 != pass2:
		lbl_error_reg.text = "Las contraseñas no coinciden."
		return
		
	if lbl_error_reg: lbl_error_reg.text = "Registrando en la nube..."
	_cambiar_estado_controles(false)
	
	var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_REGISTRO)
	RedGlobal.escribir_string_en_buffer(buffer, correo)
	RedGlobal.escribir_string_en_buffer(buffer, pass1)
	RedGlobal.enviar_buffer(buffer)

"""
_cambiar_estado_controles
Bloquea los campos para evitar la duplicidad de peticiones asíncronas.
"""
func _cambiar_estado_controles(activo: bool):
	input_reg_correo.editable = activo
	input_reg_pass.editable = activo
	input_reg_repetir.editable = activo
	boton_registrar.disabled = not activo
	if boton_volver_login: boton_volver_login.disabled = not activo

"""
_al_recibir_respuesta_registro
Valida la creación de la cuenta devolviendo el control de la escena.
"""
func _al_recibir_respuesta_registro(exito: bool):
	_cambiar_estado_controles(true)
	if exito:
		if lbl_error_reg: lbl_error_reg.text = "¡Registro exitoso! Redirigiendo..."
		await get_tree().create_timer(1.5).timeout
		get_tree().change_scene_to_file("res://InterfazLogin.tscn")
	else:
		if lbl_error_reg: lbl_error_reg.text = "Error: El correo ya existe o falló la nube."
