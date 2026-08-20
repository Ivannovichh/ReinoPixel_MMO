extends Node3D

"""
_ready
Inicialización del entorno del mundo 3D. 
Delega la conexión KCP inicial al Autoload (RedGlobal), captura el ratón 
para la visión periférica y habilita los controles físicos del avatar.
"""
func _ready():
	Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)
	
	# Nos aseguramos de arrancar la conexión UDP a través de KCP
	RedGlobal.conectar_al_servidor()
	
	var jugador_nodo = get_node_or_null("Jugador")
	if jugador_nodo and jugador_nodo.has_method("habilitar_movimiento"):
		jugador_nodo.habilitar_movimiento()
