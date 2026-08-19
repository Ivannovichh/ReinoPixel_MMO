extends Node3D

"""
_ready
Inicialización del entorno del mundo 3D. 
Al estar la red delegada completamente al Autoload (RedGlobal), esta clase 
únicamente se encarga de capturar el ratón para la visión periférica 
y habilitar los controles físicos del avatar instanciado.
"""
func _ready():
	Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)
	
	var jugador_nodo = get_node_or_null("Jugador")
	if jugador_nodo and jugador_nodo.has_method("habilitar_movimiento"):
		jugador_nodo.habilitar_movimiento()
