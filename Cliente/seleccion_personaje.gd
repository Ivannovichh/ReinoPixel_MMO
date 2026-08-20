extends Control

var personaje_seleccionado: int = -1

"""
_ready
Inicialización de la interfaz de selección.
Limpia la lista visual, deshabilita el botón de entrar hasta seleccionar un avatar,
configura los botones de sesión y se suscribe a la señal de RedGlobal para recibir la lista.
"""
func _ready():
	var lista_ui = $PanelCentral/ListaPersonajes
	lista_ui.clear()
	$PanelCentral/HBoxBotones/BtnEntrarMundo.disabled = true
	
	var btn_cerrar_sesion = get_node_or_null("ButtonLogout")
	if btn_cerrar_sesion:
		btn_cerrar_sesion.pressed.connect(self._al_cerrar_sesion)
		
	var btn_salir = get_node_or_null("ButtonSalir")
	if btn_salir:
		btn_salir.pressed.connect(func(): get_tree().quit())
		
	# Nos suscribimos al evento del Autoload que emite los personajes recibidos
	RedGlobal.evento_personajes_recibidos.connect(self._cargar_lista_desde_servidor)
	
	# Solicitamos la lista al servidor mediante un paquete binario
	var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_PEDIR_PERSONAJES)
	RedGlobal.enviar_buffer(buffer)

"""
_cargar_lista_desde_servidor
Busca el ItemList de forma segura por toda la escena, lo limpia 
y añade los personajes recibidos guardando su ID real como metadatos.
"""
func _cargar_lista_desde_servidor(lista_personajes: Array):
	# Buscamos el nodo ListaPersonajes de forma automática sin importar la ruta exacta
	var lista_ui = find_child("ListaPersonajes", true, false)
	
	if not lista_ui:
		print("¡ERROR CRÍTICO! No se encuentra el nodo ListaPersonajes en la escena.")
		return
		
	lista_ui.clear()
	
	for p in lista_personajes:
		var texto_item = p["nombre"] + " (Nivel " + str(p["nivel"]) + ")"
		var indice = lista_ui.add_item(texto_item)
		lista_ui.set_item_metadata(indice, p["id"])
		print("Personaje añadido a la UI con éxito: ", texto_item)

"""
_on_lista_personajes_item_selected
Gestión del evento de selección visual.
Extrae el ID real oculto del personaje seleccionado y desbloquea el botón para entrar al mundo.
"""
func _on_lista_personajes_item_selected(index: int):
	var lista_ui = find_child("ListaPersonajes", true, false)
	if lista_ui:
		var id_real_personaje = lista_ui.get_item_metadata(index)
		personaje_seleccionado = id_real_personaje
		
		var btn_entrar = find_child("BtnEntrarMundo", true, false)
		if btn_entrar:
			btn_entrar.disabled = false

"""
_al_cerrar_sesion
Ejecuta el protocolo de desconexión de red y devuelve al cliente a la pantalla inicial.
"""
func _al_cerrar_sesion():
	RedGlobal.desconectar_servidor()
	get_tree().change_scene_to_file("res://InterfazLogin.tscn")

"""
_on_btn_crear_nuevo_pressed
Transición hacia la pantalla de diseño de avatares.
"""
func _on_btn_crear_nuevo_pressed():
	get_tree().change_scene_to_file("res://CreacionDePersonaje.tscn")

"""
_on_btn_entrar_mundo_pressed
Confirmación final. Salta a la escena del mundo 3D con el personaje seleccionado.
"""
"""
_on_btn_entrar_mundo_pressed
Envía el ID del personaje elegido a Java y salta a la escena del mundo 3D.
"""
func _on_btn_entrar_mundo_pressed():
	if personaje_seleccionado != -1:
		# Creamos el paquete binario de selección
		var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_SELECCIONAR_PERSONAJE)
		buffer.put_32(personaje_seleccionado) # Mandamos el ID real de la BBDD
		RedGlobal.enviar_buffer(buffer)
		
		# Saltamos al mundo 3D
		get_tree().change_scene_to_file("res://Mundo.tscn")
