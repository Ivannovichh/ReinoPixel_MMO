extends Control

var personaje_seleccionado = ""

"""
_ready
Vuelca los datos residuales e invoca la petición asíncrona de avatares al backend.
"""
func _ready():
	$PanelCentral/ListaPersonajes.clear()
	$PanelCentral/HBoxBotones/BtnEntrarMundo.disabled = true
	
	var btn_cerrar_sesion = get_node_or_null("ButtonLogout")
	if btn_cerrar_sesion: btn_cerrar_sesion.pressed.connect(func(): get_tree().change_scene_to_file("res://InterfazLogin.tscn"))
		
	var btn_salir = get_node_or_null("ButtonSalir")
	if btn_salir: btn_salir.pressed.connect(func(): get_tree().quit())
		
	RedGlobal.evento_personajes_recibidos.connect(self._cargar_lista_desde_servidor)
	
	var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_PEDIR_PERSONAJES)
	RedGlobal.enviar_buffer(buffer)

"""
_cargar_lista_desde_servidor
Desglosa el diccionario devuelto por el Autoload e instancia los items visuales.
"""
func _cargar_lista_desde_servidor(lista_personajes: Array):
	$PanelCentral/ListaPersonajes.clear()
	for p in lista_personajes:
		var texto_item = p["nombre"] + " (Nivel " + str(p["nivel"]) + ")"
		$PanelCentral/ListaPersonajes.add_item(texto_item)

"""
_on_lista_personajes_item_selected
Registra en memoria la decisión visual del jugador para autorizar la entrada.
"""
func _on_lista_personajes_item_selected(index: int):
	var texto_item = $PanelCentral/ListaPersonajes.get_item_text(index)
	personaje_seleccionado = texto_item.split(" ")[0]
	$PanelCentral/HBoxBotones/BtnEntrarMundo.disabled = false

"""
_on_btn_crear_nuevo_pressed
Abre el panel de diseño de entidad.
"""
func _on_btn_crear_nuevo_pressed():
	get_tree().change_scene_to_file("res://CreacionDePersonaje.tscn")

"""
_on_btn_entrar_mundo_pressed
Comienza la instancia global del mapa y su posterior control físico.
"""
func _on_btn_entrar_mundo_pressed():
	if personaje_seleccionado != "":
		get_tree().change_scene_to_file("res://Mundo.tscn")
