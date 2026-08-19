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
Configuración inicial de la interfaz de usuario y enlaces 3D.
Prepara los menús desplegables de la pestaña de apariencia inyectando sus opciones 
por defecto, incluyendo género, cuerpo, pelo y forma de ojos, y configura la lista 
de rasgos para permitir selección múltiple. Adicionalmente, busca las referencias 
de cámara y modelo dentro del Viewport para habilitar su control manual, y fuerza 
una primera actualización visual del balance de puntos.
"""
func _ready():
	var drop_genero = $MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/DropGenero
	drop_genero.add_item("Masculino", 0)
	drop_genero.add_item("Femenino", 1)
	drop_genero.add_item("Otro", 2)
	
	var drop_cuerpo = $MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/DropCuerpo
	drop_cuerpo.add_item("Delgado", 0)
	drop_cuerpo.add_item("Atlético", 1)
	drop_cuerpo.add_item("Robusto", 2)
	
	var drop_pelo = $MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/DropPelo
	drop_pelo.add_item("Calvo", 0)
	drop_pelo.add_item("Corto", 1)
	drop_pelo.add_item("Melena", 2)
	
	var drop_ojos = $MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/DropOjosForma
	drop_ojos.add_item("Almendrados", 0)
	drop_ojos.add_item("Rasgados", 1)
	drop_ojos.add_item("Redondos", 2)
	
	var lista_disponibles = $MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos
	lista_disponibles.select_mode = ItemList.SELECT_MULTI
	
	for rasgo in base_rasgos.keys():
		lista_disponibles.add_item(rasgo)
		
	var ruta_espejo = $MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ContenedorEspejo/Espejo3D
	if ruta_espejo.has_node("Jugador"):
		modelo_personaje = ruta_espejo.get_node("Jugador")
	if ruta_espejo.has_node("Camera3D"):
		camara_espejo = ruta_espejo.get_node("Camera3D")
		
	actualizar_interfaz()

"""
actualizar_interfaz
Evaluador del estado del balance de puntos del personaje.
Actualiza los marcadores visuales reconstruyendo la lista de los rasgos elegidos 
y modificando el color del contador. Dependiendo de si el saldo total de puntos 
es positivo o negativo, habilita o deshabilita el botón de confirmación final.
"""
func actualizar_interfaz():
	var label_puntos = $MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaElegidos/LabelPuntos
	var btn_confirmar = $MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar
	var lista_elegidos = $MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaElegidos/ListaSeleccionados
	
	label_puntos.text = "Puntos para gastar: " + str(puntos_actuales)
	
	lista_elegidos.clear()
	for rasgo in rasgos_seleccionados:
		lista_elegidos.add_item(rasgo)
	
	if puntos_actuales >= 0:
		label_puntos.modulate = Color.GREEN
		btn_confirmar.disabled = false
	else:
		label_puntos.modulate = Color.RED
		btn_confirmar.disabled = true

"""
_on_lista_rasgos_multi_selected
Interprete de selecciones en la lista de rasgos.
Recalcula desde cero el estado interno cada vez que el usuario modifica su selección.
Itera sobre los elementos marcados en la interfaz visual para extraer sus nombres, 
suma el valor correspondiente desde la base de datos y lanza el refresco visual.
"""
func _on_lista_rasgos_multi_selected(_index: int, _selected: bool):
	puntos_actuales = 0
	rasgos_seleccionados.clear()
	
	var lista_disponibles = $MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos
	var items_activos = lista_disponibles.get_selected_items()
	
	for idx in items_activos:
		var nombre_rasgo = lista_disponibles.get_item_text(idx)
		rasgos_seleccionados.append(nombre_rasgo)
		puntos_actuales += base_rasgos[nombre_rasgo]
		
	actualizar_interfaz()

"""
_on_contenedor_espejo_gui_input
Gestor de manipulación espacial a través del ratón.
Escucha eventos de input exclusivamente sobre el visor del modelo 3D. Controla 
el desplazamiento de la cámara en el eje Z mediante la rueda del ratón y aplica 
rotaciones al modelo en el eje Y cuando se arrastra manteniendo el botón derecho.
"""
func _on_contenedor_espejo_gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_RIGHT:
			rotando_personaje = event.pressed
			
		if event.button_index == MOUSE_BUTTON_WHEEL_UP and event.pressed:
			if camara_espejo and camara_espejo.position.z > 1.0: 
				camara_espejo.position.z -= 0.2
				
		if event.button_index == MOUSE_BUTTON_WHEEL_DOWN and event.pressed:
			if camara_espejo and camara_espejo.position.z < 4.0: 
				camara_espejo.position.z += 0.2

	if event is InputEventMouseMotion and rotando_personaje:
		if modelo_personaje:
			modelo_personaje.rotation_degrees.y += event.relative.x * 0.5

"""
_on_btn_confirmar_pressed
Recolector y validador del paquete de creación de personaje.
Extrae todos los valores actuales de los elementos de la interfaz (desplegables, 
deslizadores, selectores de color de piel/ojos y campos de texto). Si las validaciones 
básicas son correctas, imprime el compendio de datos listo para ser empaquetado por red.
"""
func _on_btn_confirmar_pressed():
	var lista_opciones = $MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones
	var input_nombre = $MarginContainer/DistribucionGlobal/BarraInferior/InputNombre
	
	var nombre_elegido = input_nombre.text.strip_edges()
	var genero_id = lista_opciones.get_node("DropGenero").get_selected_id()
	var cuerpo_id = lista_opciones.get_node("DropCuerpo").get_selected_id()
	var pelo_id = lista_opciones.get_node("DropPelo").get_selected_id()
	var forma_ojos_id = lista_opciones.get_node("DropOjosForma").get_selected_id()
	
	var color_piel = lista_opciones.get_node("ColorPiel").color.to_html()
	var color_ojos = lista_opciones.get_node("ColorOjos").color.to_html()
	
	var altura_val = lista_opciones.get_node("SliderAltura").value
	var musculo_val = lista_opciones.get_node("SliderMusculo").value
	var edad_val = lista_opciones.get_node("SliderEdad").value
	
	if nombre_elegido == "":
		print("Error: Ponle un nombre a tu personaje")
		return
		
	if puntos_actuales >= 0:
		print("--- PERSONAJE CREADO ---")
		print("Nombre: ", nombre_elegido)
		print("Apariencia: Género [", genero_id, "] Cuerpo [", cuerpo_id, "] Pelo [", pelo_id, "]")
		print("Ojos: Forma [", forma_ojos_id, "] Color Hex [", color_ojos, "]")
		print("Físico: Altura [", altura_val, "] Músculo [", musculo_val, "] Edad [", edad_val, "]")
		print("Color Piel Hex: ", color_piel)
		print("Rasgos equipados: ", rasgos_seleccionados)
		
		# Regresa a la pantalla de selección de personajes tras crear el avatar con éxito
		get_tree().change_scene_to_file("res://SeleccionPersonaje.tscn")
