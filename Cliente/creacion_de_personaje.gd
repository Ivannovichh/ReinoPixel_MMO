extends Control

var puntos_actuales = 0
var rasgos_seleccionados = []
var rotando_personaje: bool = false
var modelo_personaje: Node3D
var camara_espejo: Camera3D

var base_rasgos = {
	"Fuerte (Cuesta 5)": -5,
	"Ágil (Cuesta 3)": -3,
	"Miope (Da 4)": 4,
	"Asmático (Da 5)": 5,
	"Suertudo (Cuesta 4)": -4,
	"Torpe (Da 2)": 2
}

"""
_ready
Inicialización visual de opciones cosméticas y suscripción a la red global.
"""
func _ready():
	var drop_genero = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/DropGenero")
	if drop_genero:
		drop_genero.add_item("Masculino", 0)
		drop_genero.add_item("Femenino", 1)
		drop_genero.add_item("Otro", 2)
	
	var drop_cuerpo = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/DropCuerpo")
	if drop_cuerpo:
		drop_cuerpo.add_item("Delgado", 0)
		drop_cuerpo.add_item("Atlético", 1)
		drop_cuerpo.add_item("Robusto", 2)
	
	var drop_pelo = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/DropPelo")
	if drop_pelo:
		drop_pelo.add_item("Calvo", 0)
		drop_pelo.add_item("Corto", 1)
		drop_pelo.add_item("Melena", 2)
	
	var drop_ojos = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/DropOjosForma")
	if drop_ojos:
		drop_ojos.add_item("Almendrados", 0)
		drop_ojos.add_item("Rasgados", 1)
		drop_ojos.add_item("Redondos", 2)
	
	var lista_disponibles = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos")
	if lista_disponibles:
		lista_disponibles.select_mode = ItemList.SELECT_MULTI
		for rasgo in base_rasgos.keys():
			lista_disponibles.add_item(rasgo)
		
	var ruta_espejo = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ContenedorEspejo/Espejo3D")
	if ruta_espejo:
		if ruta_espejo.has_node("Jugador"): modelo_personaje = ruta_espejo.get_node("Jugador")
		if ruta_espejo.has_node("Camera3D"): camara_espejo = ruta_espejo.get_node("Camera3D")
			
	var btn_volver = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnVolver")
	if btn_volver: btn_volver.pressed.connect(func(): get_tree().change_scene_to_file("res://SeleccionPersonaje.tscn"))
		
	var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
	if btn_confirmar: btn_confirmar.pressed.connect(self._on_btn_confirmar_pressed)
		
	RedGlobal.evento_creacion_personaje.connect(self._al_recibir_respuesta_creacion)
	actualizar_interfaz()

"""
actualizar_interfaz
Pinta los puntos restantes en función de los rasgos equipados y bloquea el botón 
si el saldo es negativo.
"""
func actualizar_interfaz():
	var label_puntos = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaElegidos/LabelPuntos")
	var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
	var lista_elegidos = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaElegidos/ListaSeleccionados")
	
	if label_puntos:
		label_puntos.text = "Puntos para gastar: " + str(puntos_actuales)
		label_puntos.modulate = Color.GREEN if puntos_actuales >= 0 else Color.RED
			
	if lista_elegidos:
		lista_elegidos.clear()
		for rasgo in rasgos_seleccionados: lista_elegidos.add_item(rasgo)
	
	if btn_confirmar: btn_confirmar.disabled = (puntos_actuales < 0)

"""
_on_lista_rasgos_multi_selected
Actualiza el cálculo matemático total al interaccionar con el panel de rasgos.
"""
func _on_lista_rasgos_multi_selected(_index: int, _selected: bool):
	puntos_actuales = 0
	rasgos_seleccionados.clear()
	
	var lista_disponibles = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos")
	if lista_disponibles:
		var items_activos = lista_disponibles.get_selected_items()
		for idx in items_activos:
			var nombre_rasgo = lista_disponibles.get_item_text(idx)
			rasgos_seleccionados.append(nombre_rasgo)
			puntos_actuales += base_rasgos[nombre_rasgo]
		
	actualizar_interfaz()

"""
_on_contenedor_espejo_gui_input
Controlador orbital de cámara y rotación manual del modelo 3D en pantalla.
"""
func _on_contenedor_espejo_gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_RIGHT: rotando_personaje = event.pressed
		if event.button_index == MOUSE_BUTTON_WHEEL_UP and event.pressed:
			if camara_espejo and camara_espejo.position.z > 1.0: camara_espejo.position.z -= 0.2
		if event.button_index == MOUSE_BUTTON_WHEEL_DOWN and event.pressed:
			if camara_espejo and camara_espejo.position.z < 4.0: camara_espejo.position.z += 0.2

	if event is InputEventMouseMotion and rotando_personaje:
		if modelo_personaje: modelo_personaje.rotation_degrees.y += event.relative.x * 0.5

"""
_on_btn_confirmar_pressed
Construye un paquete de bytes con el nombre propuesto y delega la validación final a Java.
"""
func _on_btn_confirmar_pressed():
	var input_nombre = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/InputNombre")
	if not input_nombre: return
		
	var nombre_elegido = input_nombre.text.strip_edges()
	if nombre_elegido == "" or puntos_actuales < 0: return
		
	var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
	if btn_confirmar: btn_confirmar.disabled = true
		
	var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_CREAR_PERSONAJE)
	RedGlobal.escribir_string_en_buffer(buffer, nombre_elegido)
	RedGlobal.enviar_buffer(buffer)

"""
_al_recibir_respuesta_creacion
Callback de red. Salta al selector si la creación persistió con éxito en BBDD.
"""
func _al_recibir_respuesta_creacion(exito: bool):
	if exito:
		get_tree().change_scene_to_file("res://SeleccionPersonaje.tscn")
	else:
		var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
		if btn_confirmar: btn_confirmar.disabled = false
