extends Control

var personaje_seleccionado = ""

"""
_ready
Inicialización de la interfaz de selección.
Se ejecuta automáticamente al cargar la escena. Su función es limpiar 
cualquier dato residual en la lista visual y solicitar al servidor backend 
la información actualizada de los personajes de esta cuenta.
"""
func _ready():
	$PanelCentral/ListaPersonajes.clear()
	$PanelCentral/HBoxBotones/BtnEntrarMundo.disabled = true
	
	var btn_cerrar_sesion = get_node_or_null("ButtonLogout")
	if btn_cerrar_sesion:
		btn_cerrar_sesion.pressed.connect(self._al_cerrar_sesion)
		
	var btn_salir = get_node_or_null("ButtonSalir")
	if btn_salir:
		btn_salir.pressed.connect(func(): get_tree().quit())
		

"""
cargar_lista_desde_servidor
Procesa la respuesta del servidor y actualiza la UI.
Recibe la cadena de texto cruda generada por Java (formato: "LISTA_PERSONAJES:Goku,1;Vegeta,2;"), 
la desglosa aislando nombres y niveles, y rellena el nodo ItemList.
"""
func cargar_lista_desde_servidor(datos_crudos: String):
	$PanelCentral/ListaPersonajes.clear()
	
	var contenido = datos_crudos.replace("LISTA_PERSONAJES:", "")
	var personajes = contenido.split(";")
	
	for p in personajes:
		if p != "":
			var datos = p.split(",")
			var nombre = datos[0]
			var nivel = datos[1]
			$PanelCentral/ListaPersonajes.add_item(nombre + " (Nivel " + nivel + ")")

"""
_on_lista_personajes_item_selected
Gestión del evento de selección visual.
Se dispara cuando el usuario hace clic sobre un elemento del ItemList.
Extrae el nombre del personaje seleccionado, lo guarda en memoria y 
desbloquea el botón principal para permitir la entrada al mundo.
"""
func _on_lista_personajes_item_selected(index: int):
	var texto_item = $PanelCentral/ListaPersonajes.get_item_text(index)
	personaje_seleccionado = texto_item.split(" ")[0]
	$PanelCentral/HBoxBotones/BtnEntrarMundo.disabled = false

"""
_on_btn_crear_nuevo_pressed
Transición a la fase de diseño.
Se ejecuta al pulsar el botón de creación. Abandona la escena actual 
y carga la pantalla de personalización de apariencia y rasgos.
"""
func _on_btn_crear_nuevo_pressed():
	get_tree().change_scene_to_file("res://CreacionDePersonaje.tscn")

"""
_on_btn_entrar_mundo_pressed
Confirmación final de entrada al servidor.
Verifica que haya un personaje en memoria y notifica al servidor backend 
qué entidad debe instanciar y asignar a la sesión actual del jugador.
"""
func _on_btn_entrar_mundo_pressed():
	if personaje_seleccionado != "":
		# RedGlobal.enviar_paquete("ENTRAR_MUNDO:" + personaje_seleccionado)
		get_tree().change_scene_to_file("res://Mundo.tscn")
