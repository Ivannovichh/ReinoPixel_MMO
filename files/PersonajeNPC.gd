extends "res://Personajes/PersonajeBase.gd"

"""
PersonajeNPC.gd
Extensión de PersonajeBase para NPCs y otros jugadores vistos en el mundo.
No tiene controles de input propios — recibe posición y datos del servidor.
Añade el nombre flotante sobre la cabeza y gestiona la interpolación de movimiento.
"""

# Nombre que aparece sobre la cabeza del personaje
@onready var label_nombre: Label3D = $LabelNombre

# Velocidad de interpolación para suavizar el movimiento recibido por red
const VELOCIDAD_LERP := 12.0

# Posición objetivo recibida del servidor
var _posicion_objetivo: Vector3 = Vector3.ZERO
var _interpolando: bool = false


func _ready() -> void:
	super._ready()  # Llama al _ready de PersonajeBase
	_posicion_objetivo = global_position


func _physics_process(delta: float) -> void:
	if _interpolando:
		global_position = global_position.lerp(_posicion_objetivo, delta * VELOCIDAD_LERP)
		if global_position.distance_to(_posicion_objetivo) < 0.01:
			global_position = _posicion_objetivo
			_interpolando = false


"""
actualizar_desde_red
Recibe los datos de posición y cosméticos del servidor y los aplica.
Llamado por el GestorMundo al procesar paquetes S_SPAWN_PERSONAJE o S_ACTUALIZAR_POSICION.
"""
func actualizar_desde_red(datos_red: Dictionary) -> void:
	# Posición
	if datos_red.has("pos_x") and datos_red.has("pos_y") and datos_red.has("pos_z"):
		_posicion_objetivo = Vector3(datos_red["pos_x"], datos_red["pos_y"], datos_red["pos_z"])
		_interpolando = true

	# Nombre flotante
	if datos_red.has("nombre") and label_nombre:
		label_nombre.text = datos_red["nombre"]

	# Apariencia (solo si viene el diccionario completo, en el spawn inicial)
	if datos_red.has("color_piel"):
		aplicar_personalizacion(datos_red)


"""
reproducir_animacion
Activa una animación por nombre si el AnimationPlayer la tiene.
"""
func reproducir_animacion(nombre: String) -> void:
	var anim_player: AnimationPlayer = get_node_or_null("AnimationPlayer")
	if anim_player and anim_player.has_animation(nombre):
		anim_player.play(nombre)
