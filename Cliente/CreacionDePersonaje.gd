extends Control

var puntos_actuales = 0
var rasgos_seleccionados = []
var rotando_personaje: bool = false
var modelo_personaje: Node3D
var camara_espejo: Camera3D

# Diccionario completo con las 9 opciones visuales
var datos_personaje = {
	"genero": 0, 
	"cuerpo": 0, 
	"pelo": 0, 
	"forma_ojos": 0,
	"altura": 1.0, 
	"musculatura": 1.0, 
	"edad": 0.5,
	"color_piel": "ffccaa", 
	"color_ojos": "ffffff"
}

"""
Estructura Avanzada de Rasgos (Estilo Project Zomboid)
Define los puntos que otorga/cuesta, una descripción de Lore, y qué otros
IDs de rasgos bloquea (mutuamente excluyentes).
"""
var base_rasgos = {
	"fuerte": {"nombre": "Fuerte", "puntos": -5, "desc": "Daño cuerpo a cuerpo incrementado y mayor capacidad de carga.", "excluye": ["debil"]},
	"debil": {"nombre": "Débil", "puntos": 5, "desc": "Reducción significativa de la fuerza física y el peso transportable.", "excluye": ["fuerte"]},
	"agil": {"nombre": "Ágil", "puntos": -3, "desc": "Mayor velocidad de movimiento y salto de obstáculos.", "excluye": ["torpe"]},
	"torpe": {"nombre": "Torpe", "puntos": 2, "desc": "Hace más ruido al moverse y tropieza ocasionalmente.", "excluye": ["agil"]},
	"miope": {"nombre": "Miope", "puntos": 4, "desc": "Rango de visión drásticamente reducido sin gafas.", "excluye": ["vista_lince"]},
	"vista_lince": {"nombre": "Ojos de Lince", "puntos": -4, "desc": "Campo visual ampliado, mejor detección de movimiento.", "excluye": ["miope"]},
	"suertudo": {"nombre": "Suertudo", "puntos": -4, "desc": "Mejor probabilidad de encontrar botín raro.", "excluye": ["gafe"]},
	"gafe": {"nombre": "Gafe", "puntos": 4, "desc": "Las cosas tienden a salir mal a tu alrededor.", "excluye": ["suertudo"]},
	"asmatico": {"nombre": "Asmático", "puntos": 5, "desc": "La resistencia se agota muy rápido al correr.", "excluye": ["atleta"]},
	"atleta": {"nombre": "Atleta", "puntos": -6, "desc": "Estamina casi inagotable y regeneración rápida.", "excluye": ["asmatico"]}
}

"""
_ready
Inicialización visual de opciones cosméticas y suscripción a la red global.
Configura las listas interactivas de rasgos, la carga del modelo 3D y la conexión al servidor.
"""
func _ready():
	_configurar_interfaz_cosmetica()
	
	var lista_disponibles = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos")
	var lista_elegidos = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaElegidos/ListaSeleccionados")
	
	# Configuración de las listas estilo Zomboid (Selección individual, doble clic para mover)
	if lista_disponibles:
		lista_disponibles.select_mode = ItemList.SELECT_SINGLE
		lista_disponibles.item_activated.connect(self._on_rasgo_disponible_activado)
		lista_disponibles.item_selected.connect(self._on_rasgo_hover)
		
	if lista_elegidos:
		lista_elegidos.select_mode = ItemList.SELECT_SINGLE
		lista_elegidos.item_activated.connect(self._on_rasgo_elegido_activado)
		lista_elegidos.item_selected.connect(self._on_rasgo_hover)
		
	_repoblar_listas_rasgos()
		
	var ruta_espejo = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ContenedorEspejo/Espejo3D")
	if ruta_espejo:
		if ruta_espejo.has_node("Jugador"): 
			modelo_personaje = ruta_espejo.get_node("Jugador")
		if ruta_espejo.has_node("Camera3D"): 
			camara_espejo = ruta_espejo.get_node("Camera3D")
			
	var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
	if btn_confirmar and not btn_confirmar.pressed.is_connected(self._on_btn_confirmar_pressed):
		btn_confirmar.pressed.connect(self._on_btn_confirmar_pressed)
	
	var btn_volver = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnVolver")
	if btn_volver: 
		btn_volver.pressed.connect(func(): get_tree().change_scene_to_file("res://SeleccionPersonaje.tscn"))
	
	RedGlobal.evento_creacion_personaje.connect(self._al_recibir_respuesta_creacion)
	
	_actualizar_datos("altura", 1.0)

"""
_configurar_interfaz_cosmetica
Vincula todos los elementos de la interfaz estética al diccionario de estado del personaje.
"""
func _configurar_interfaz_cosmetica():
	var ruta_base = "MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/"
	
	var d_genero = get_node_or_null(ruta_base + "DropGenero")
	if d_genero: d_genero.item_selected.connect(func(idx): _actualizar_datos("genero", idx))
	
	var d_cuerpo = get_node_or_null(ruta_base + "DropCuerpo")
	if d_cuerpo: d_cuerpo.item_selected.connect(func(idx): _actualizar_datos("cuerpo", idx))
	
	var d_pelo = get_node_or_null(ruta_base + "DropPelo")
	if d_pelo: d_pelo.item_selected.connect(func(idx): _actualizar_datos("pelo", idx))
		
	var d_ojos = get_node_or_null(ruta_base + "DropOjosForma")
	if d_ojos: d_ojos.item_selected.connect(func(idx): _actualizar_datos("forma_ojos", idx))
		
	var c_piel = get_node_or_null(ruta_base + "ColorPiel")
	if c_piel: c_piel.color_changed.connect(func(color): _actualizar_datos("color_piel", color.to_html(false)))
		
	var c_ojos = get_node_or_null(ruta_base + "ColorOjos")
	if c_ojos: c_ojos.color_changed.connect(func(color): _actualizar_datos("color_ojos", color.to_html(false)))

	var s_altura = get_node_or_null(ruta_base + "SliderAltura")
	if s_altura: s_altura.value_changed.connect(func(val): _actualizar_datos("altura", val))
		
	var s_musculo = get_node_or_null(ruta_base + "SliderMusculatura")
	if s_musculo: s_musculo.value_changed.connect(func(val): _actualizar_datos("musculatura", val))
		
	var s_edad = get_node_or_null(ruta_base + "SliderEdad")
	if s_edad: s_edad.value_changed.connect(func(val): _actualizar_datos("edad", val))

"""
_actualizar_datos
Registra el cambio estético y delega la responsabilidad visual al modelo 3D.
"""
func _actualizar_datos(clave: String, valor):
	datos_personaje[clave] = valor
	if modelo_personaje and modelo_personaje.has_method("aplicar_personalizacion"):
		modelo_personaje.aplicar_personalizacion(datos_personaje)

"""
_repoblar_listas_rasgos
Motor principal del sistema de selección. Recalcula puntos y renderiza ambas listas.
Aplica bloqueos visuales y lógicos a los rasgos incompatibles con los seleccionados.
"""
func _repoblar_listas_rasgos():
	var lista_disponibles = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos")
	var lista_elegidos = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaElegidos/ListaSeleccionados")
	var label_puntos = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaElegidos/LabelPuntos")
	var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
	
	if not lista_disponibles or not lista_elegidos: return
	
	lista_disponibles.clear()
	lista_elegidos.clear()
	puntos_actuales = 0
	
	# Pre-calcular bloqueos según lo seleccionado
	var bloqueados = []
	for id_rasgo in rasgos_seleccionados:
		puntos_actuales += base_rasgos[id_rasgo]["puntos"]
		var excluye = base_rasgos[id_rasgo].get("excluye", [])
		for ex_id in excluye:
			if not bloqueados.has(ex_id): bloqueados.append(ex_id)
			
	# Poblar Lista Elegidos
	for id_rasgo in rasgos_seleccionados:
		var r = base_rasgos[id_rasgo]
		var str_puntos = "+" + str(r["puntos"]) if r["puntos"] > 0 else str(r["puntos"])
		var texto = r["nombre"] + " (" + str_puntos + ")"
		var index = lista_elegidos.add_item(texto)
		lista_elegidos.set_item_metadata(index, id_rasgo)
		
		# Color verde para los que dan puntos (negativos), rojo para los buenos (cuestan)
		if r["puntos"] > 0: lista_elegidos.set_item_custom_fg_color(index, Color.LIGHT_GREEN)
		else: lista_elegidos.set_item_custom_fg_color(index, Color.LIGHT_CORAL)
		
	# Poblar Lista Disponibles
	for id_rasgo in base_rasgos.keys():
		if not rasgos_seleccionados.has(id_rasgo):
			var r = base_rasgos[id_rasgo]
			var str_puntos = "+" + str(r["puntos"]) if r["puntos"] > 0 else str(r["puntos"])
			var texto = r["nombre"] + " (" + str_puntos + ")"
			var index = lista_disponibles.add_item(texto)
			lista_disponibles.set_item_metadata(index, id_rasgo)
			
			if bloqueados.has(id_rasgo):
				lista_disponibles.set_item_disabled(index, true)
				lista_disponibles.set_item_custom_fg_color(index, Color.DIM_GRAY)
			else:
				if r["puntos"] > 0: lista_disponibles.set_item_custom_fg_color(index, Color.LIGHT_GREEN)
				else: lista_disponibles.set_item_custom_fg_color(index, Color.LIGHT_CORAL)
				
	# Actualizar UI final
	if label_puntos:
		label_puntos.text = "Puntos para gastar: " + str(puntos_actuales)
		label_puntos.modulate = Color.GREEN if puntos_actuales >= 0 else Color.RED
		
	if btn_confirmar: 
		btn_confirmar.disabled = (puntos_actuales < 0)
		
	# Limpiamos descripciones al refrescar
	_actualizar_descripcion_rasgo("")

"""
_on_rasgo_disponible_activado
Se dispara al hacer doble clic en un rasgo de la lista izquierda. Lo mueve a la derecha.
"""
func _on_rasgo_disponible_activado(index: int):
	var lista_disponibles = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos")
	if lista_disponibles and not lista_disponibles.is_item_disabled(index):
		var id_rasgo = lista_disponibles.get_item_metadata(index)
		rasgos_seleccionados.append(id_rasgo)
		_repoblar_listas_rasgos()

"""
_on_rasgo_elegido_activado
Se dispara al hacer doble clic en un rasgo de la lista derecha. Lo devuelve a la izquierda.
"""
func _on_rasgo_elegido_activado(index: int):
	var lista_elegidos = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaElegidos/ListaSeleccionados")
	if lista_elegidos:
		var id_rasgo = lista_elegidos.get_item_metadata(index)
		rasgos_seleccionados.erase(id_rasgo)
		_repoblar_listas_rasgos()

"""
_on_rasgo_hover
Muestra la descripción narrativa del rasgo seleccionado en un panel de texto.
"""
func _on_rasgo_hover(index: int):
	# Detectamos quién emitió la señal para buscar el metadata correcto
	var item_list = get_viewport().gui_get_focus_owner() as ItemList
	if item_list:
		var id_rasgo = item_list.get_item_metadata(index)
		var desc = base_rasgos[id_rasgo]["desc"]
		_actualizar_descripcion_rasgo(desc)

"""
_actualizar_descripcion_rasgo
Pinta el texto de lore en el RichTextLabel / Label de la interfaz.
"""
func _actualizar_descripcion_rasgo(texto: String):
	# NOTA: Debes crear un nodo Label o RichTextLabel en la interfaz para mostrar esto.
	var label_desc = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ContenedorDescripcion/LabelDesc")
	if label_desc: label_desc.text = texto

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
Construye el paquete de red con el nombre, configuración estética y los IDs de los 
rasgos seleccionados. Delega la creación en la BBDD al servidor Java.
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
	
	buffer.put_32(datos_personaje["genero"])
	buffer.put_32(datos_personaje["cuerpo"])
	buffer.put_32(datos_personaje["pelo"])
	buffer.put_32(datos_personaje["forma_ojos"])
	buffer.put_float(datos_personaje["altura"])
	buffer.put_float(datos_personaje["musculatura"])
	buffer.put_float(datos_personaje["edad"])
	RedGlobal.escribir_string_en_buffer(buffer, datos_personaje["color_piel"])
	RedGlobal.escribir_string_en_buffer(buffer, datos_personaje["color_ojos"])
	
	# EMPAQUETADO DE RASGOS: Mandamos primero cuántos hay y luego los enviamos uno a uno.
	# En java usaremos una tabla relacional 'personajes_rasgos' o un campo JSON.
	buffer.put_32(rasgos_seleccionados.size())
	for id_rasgo in rasgos_seleccionados:
		RedGlobal.escribir_string_en_buffer(buffer, id_rasgo)
	
	RedGlobal.enviar_buffer(buffer)

"""
_al_recibir_respuesta_creacion
Callback de red que evalúa la respuesta del servidor tras intentar crear el personaje.
"""
func _al_recibir_respuesta_creacion(exito: bool):
	if exito:
		get_tree().change_scene_to_file("res://SeleccionPersonaje.tscn")
	else:
		var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
		if btn_confirmar: btn_confirmar.disabled = false
