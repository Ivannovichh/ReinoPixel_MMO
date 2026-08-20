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
Se encarga de poblar las listas, capturar las referencias del visor 3D,
conectar los botones principales y forzar un primer refresco de la interfaz.
"""
func _ready():
	_configurar_interfaz_cosmetica()
	
	var lista_disponibles = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos")
	if lista_disponibles:
		lista_disponibles.select_mode = ItemList.SELECT_MULTI
		if not lista_disponibles.multi_selected.is_connected(self._on_lista_rasgos_multi_selected):
			lista_disponibles.multi_selected.connect(self._on_lista_rasgos_multi_selected)
		for rasgo in base_rasgos.keys(): 
			lista_disponibles.add_item(rasgo)
		
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
	
	actualizar_interfaz()
	_actualizar_datos("altura", 1.0)

"""
_configurar_interfaz_cosmetica
Método interno que vincula todos los elementos de la interfaz (desplegables, 
selectores de color y controles deslizantes) con el diccionario de estado del personaje.
Cualquier interacción del jugador actualizará el valor correspondiente.
"""
func _configurar_interfaz_cosmetica():
	var ruta_base = "MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/"
	
	var d_genero = get_node_or_null(ruta_base + "DropGenero")
	if d_genero: 
		d_genero.item_selected.connect(func(idx): _actualizar_datos("genero", idx))
	
	var d_cuerpo = get_node_or_null(ruta_base + "DropCuerpo")
	if d_cuerpo: 
		d_cuerpo.item_selected.connect(func(idx): _actualizar_datos("cuerpo", idx))
	
	var d_pelo = get_node_or_null(ruta_base + "DropPelo")
	if d_pelo: 
		d_pelo.item_selected.connect(func(idx): _actualizar_datos("pelo", idx))
		
	var d_ojos = get_node_or_null(ruta_base + "DropOjosForma")
	if d_ojos: 
		d_ojos.item_selected.connect(func(idx): _actualizar_datos("forma_ojos", idx))
		
	var c_piel = get_node_or_null(ruta_base + "ColorPiel")
	if c_piel: 
		c_piel.color_changed.connect(func(color): _actualizar_datos("color_piel", color.to_html(false)))
		
	var c_ojos = get_node_or_null(ruta_base + "ColorOjos")
	if c_ojos: 
		c_ojos.color_changed.connect(func(color): _actualizar_datos("color_ojos", color.to_html(false)))

	var s_altura = get_node_or_null(ruta_base + "SliderAltura")
	if s_altura: 
		s_altura.value_changed.connect(func(val): _actualizar_datos("altura", val))
		
	var s_musculo = get_node_or_null(ruta_base + "SliderMusculatura")
	if s_musculo: 
		s_musculo.value_changed.connect(func(val): _actualizar_datos("musculatura", val))
		
	var s_edad = get_node_or_null(ruta_base + "SliderEdad")
	if s_edad: 
		s_edad.value_changed.connect(func(val): _actualizar_datos("edad", val))

"""
_actualizar_datos
Método interno que registra en el diccionario la clave modificada y su nuevo valor.
Inmediatamente después, delega la responsabilidad visual al modelo 3D para que 
refleje los cambios en tiempo real.
"""
func _actualizar_datos(clave: String, valor):
	datos_personaje[clave] = valor
	if modelo_personaje and modelo_personaje.has_method("aplicar_personalizacion"):
		modelo_personaje.aplicar_personalizacion(datos_personaje)

"""
actualizar_interfaz
Calcula y pinta los puntos restantes en función de los rasgos equipados.
Bloquea el botón de confirmación si el jugador excede el límite de puntos.
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
		for rasgo in rasgos_seleccionados: 
			lista_elegidos.add_item(rasgo)
	
	if btn_confirmar: 
		btn_confirmar.disabled = (puntos_actuales < 0)

"""
_on_lista_rasgos_multi_selected
Actualiza el cálculo matemático total al interaccionar con el panel de rasgos.
Lee los elementos seleccionados y suma o resta su valor a los puntos actuales.
"""
func _on_lista_rasgos_multi_selected(_index: int, _selected: bool):
	puntos_actuales = 0
	rasgos_seleccionados.clear()
	
	var lista_disponibles = get_node_or_null("MarginContainer/DistribucionGlobal/MenuPestanas/Rasgos/ColumnaDisponibles/ListaRasgos")
	if lista_disponibles:
		for idx in lista_disponibles.get_selected_items():
			var nombre_rasgo = lista_disponibles.get_item_text(idx)
			rasgos_seleccionados.append(nombre_rasgo)
			puntos_actuales += base_rasgos[nombre_rasgo]
			
	actualizar_interfaz()

"""
_on_contenedor_espejo_gui_input
Controlador orbital de cámara y rotación manual del modelo 3D en pantalla.
Permite hacer zoom con la rueda del ratón y rotar el avatar arrastrando el clic derecho.
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
Construye un paquete de bytes con el nombre propuesto y todos los valores 
de configuración estética. Delega la creación y validación final al servidor Java.
"""
func _on_btn_confirmar_pressed():
	var input_nombre = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/InputNombre")
	if not input_nombre: return
		
	var nombre_elegido = input_nombre.text.strip_edges()
	if nombre_elegido == "" or puntos_actuales < 0: return
		
	var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
	if btn_confirmar: 
		btn_confirmar.disabled = true
		
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
	
	RedGlobal.enviar_buffer(buffer)

"""
_al_recibir_respuesta_creacion
Callback de red. Devuelve a la escena del selector de personajes si 
la creación persistió con éxito en la base de datos.
"""
func _al_recibir_respuesta_creacion(exito: bool):
	if exito:
		get_tree().change_scene_to_file("res://SeleccionPersonaje.tscn")
	else:
		var btn_confirmar = get_node_or_null("MarginContainer/DistribucionGlobal/BarraInferior/BtnConfirmar")
		if btn_confirmar: 
			btn_confirmar.disabled = false
