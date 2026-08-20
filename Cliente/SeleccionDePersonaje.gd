extends Control

var personaje_seleccionado: int = -1

"""
_ready
Inicialización de la interfaz de selección.
Limpia la lista visual, deshabilita el botón de entrar hasta seleccionar un avatar,
configura todos los botones de la interfaz (sesión, salir, crear y eliminar)
y se suscribe a las señales de RedGlobal para recibir la lista y los eventos de borrado.
"""
func _ready():
	var lista_ui = find_child("ListaPersonajes", true, false)
	if lista_ui:
		lista_ui.clear()
		if not lista_ui.item_selected.is_connected(self._on_lista_personajes_item_selected):
			lista_ui.item_selected.connect(self._on_lista_personajes_item_selected)
		
	# Configuración segura del botón de entrar al mundo
	var btn_entrar = find_child("BtnEntrarMundo", true, false)
	if not btn_entrar:
		btn_entrar = find_child("BtnEntrar", true, false)
	if btn_entrar:
		btn_entrar.disabled = true
		if not btn_entrar.pressed.is_connected(self._on_btn_entrar_mundo_pressed):
			btn_entrar.pressed.connect(self._on_btn_entrar_mundo_pressed)
			
	# Configuración del botón de crear personaje
	var btn_crear = find_child("BtnCrearNuevo", true, false)
	if btn_crear and not btn_crear.pressed.is_connected(self._on_btn_crear_nuevo_pressed):
		btn_crear.pressed.connect(self._on_btn_crear_nuevo_pressed)

	# Configuración del nuevo botón de eliminar personaje
	var btn_delete = find_child("BtnDelete", true, false)
	if btn_delete and not btn_delete.pressed.is_connected(self._on_btn_delete_pressed):
		btn_delete.pressed.connect(self._on_btn_delete_pressed)
	
	# Configuración del botón de cerrar sesión (soporta ButtonLogout o ButtonLog)
	var btn_cerrar_sesion = find_child("ButtonLogout", true, false)
	if not btn_cerrar_sesion:
		btn_cerrar_sesion = find_child("ButtonLog", true, false)
	if btn_cerrar_sesion and not btn_cerrar_sesion.pressed.is_connected(self._al_cerrar_sesion):
		btn_cerrar_sesion.pressed.connect(self._al_cerrar_sesion)
		
	# Configuración del botón de salir del juego
	var btn_salir = find_child("ButtonSalir", true, false)
	if btn_salir and not btn_salir.pressed.is_connected(self._al_salir):
		btn_salir.pressed.connect(self._al_salir)
		
	# Inicializamos los datos estéticos de la barra superior
	_actualizar_barra_superior()
	
	# Nos suscribimos a los eventos del Autoload de red
	RedGlobal.evento_personajes_recibidos.connect(self._cargar_lista_desde_servidor)
	RedGlobal.evento_eliminacion_personaje.connect(self._on_personaje_eliminado_res)
	
	# Solicitamos la lista al servidor mediante un paquete binario
	var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_PEDIR_PERSONAJES)
	RedGlobal.enviar_buffer(buffer)

"""
_cargar_lista_desde_servidor
Busca el ItemList de forma segura por toda la escena, lo limpia 
y añade los personajes recibidos guardando su ID real como metadatos.
"""
func _cargar_lista_desde_servidor(lista_personajes: Array):
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
_actualizar_espejo_3d
Carga la plantilla base y delega la aplicación de la estética a PersonajeBase.
"""
func _actualizar_espejo_3d(datos_completos: Dictionary):
	var pivote = find_child("PivoteModelo", true, false)
	if not pivote: return
		
	for hijo in pivote.get_children():
		hijo.queue_free()
		
	var ruta_modelo = "res://Escenas/PersonajeBase.tscn" 
	
	if ResourceLoader.exists(ruta_modelo):
		var escena = load(ruta_modelo)
		var instancia = escena.instantiate()
		
		if instancia.has_method("aplicar_personalizacion"):
			instancia.aplicar_personalizacion(datos_completos)
			
		pivote.add_child(instancia)
	else:
		var malla = MeshInstance3D.new()
		malla.mesh = CapsuleMesh.new()
		pivote.add_child(malla)

"""
_on_lista_personajes_item_selected
Reemplaza la función antigua. Pasa el diccionario completo de red al espejo 3D.
"""
func _on_lista_personajes_item_selected(index: int):
	var lista_ui = find_child("ListaPersonajes", true, false)
	if lista_ui:
		# Aquí debes asegurarte de que _cargar_lista_desde_servidor 
		# haya guardado todo el Diccionario 'p' en el metadata, no solo p["id"]
		var datos = lista_ui.get_item_metadata(index)
		personaje_seleccionado = datos["id"]
		
		var btn_entrar = find_child("BtnEntrarMundo", true, false)
		if not btn_entrar: btn_entrar = find_child("BtnEntrar", true, false)
		if btn_entrar: btn_entrar.disabled = false
			
		_actualizar_espejo_3d(datos)

"""
_actualizar_barra_superior
Carga la información decorativa de la cuenta en la franja superior.
"""
func _actualizar_barra_superior():
	var txt_usu = find_child("TxtUsuario", true, false)
	var txt_niv = find_child("TxtNivelCuenta", true, false)
	var txt_mon = find_child("TxtMonedas", true, false)
	
	if txt_usu: txt_usu.text = "Usuario: JotaMynds"
	if txt_niv: txt_niv.text = "Nivel de Cuenta: 1"
	if txt_mon: txt_mon.text = "🪙 1500 Monedas"

"""
_al_cerrar_sesion
Ejecuta el protocolo de desconexión de red y devuelve al cliente a la pantalla inicial.
"""
func _al_cerrar_sesion():
	RedGlobal.desconectar_servidor()
	get_tree().change_scene_to_file("res://InterfazLogin.tscn")

"""
_al_salir
Cierra la aplicación por completo.
"""
func _al_salir():
	get_tree().quit()

"""
_on_btn_crear_nuevo_pressed
Transición hacia la pantalla de diseño de avatares.
"""
func _on_btn_crear_nuevo_pressed():
	get_tree().change_scene_to_file("res://CreacionDePersonaje.tscn")

"""
_on_btn_delete_pressed
Empaqueta el ID del personaje seleccionado y lo envía al servidor Java
para proceder a su borrado permanente de la base de datos.
"""
func _on_btn_delete_pressed():
	if personaje_seleccionado != -1:
		print("Solicitud de eliminación para el personaje ID: ", personaje_seleccionado)
		var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_ELIMINAR_PERSONAJE)
		buffer.put_32(personaje_seleccionado)
		RedGlobal.enviar_buffer(buffer)

"""
_on_personaje_eliminado_res
Procesa la respuesta asíncrona del servidor tras intentar borrar el avatar.
Si el borrado es exitoso, resetea la selección, limpia el visor 3D y recarga la lista.
"""
func _on_personaje_eliminado_res(exito: bool):
	if exito:
		print("¡Personaje eliminado con éxito de la BBDD!")
		personaje_seleccionado = -1
		
		var btn_entrar = find_child("BtnEntrarMundo", true, false)
		if not btn_entrar: btn_entrar = find_child("BtnEntrar", true, false)
		if btn_entrar: btn_entrar.disabled = true
		
		var pivote = find_child("PivoteModelo", true, false)
		if pivote:
			for hijo in pivote.get_children():
				hijo.queue_free()
				
		var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_PEDIR_PERSONAJES)
		RedGlobal.enviar_buffer(buffer)
	else:
		print("Error: El servidor denegó la eliminación del personaje.")

"""
_on_btn_entrar_mundo_pressed
Envía el ID del personaje elegido a Java y salta a la escena del mundo 3D.
"""
func _on_btn_entrar_mundo_pressed():
	if personaje_seleccionado != -1:
		var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_SELECCIONAR_PERSONAJE)
		buffer.put_32(personaje_seleccionado)
		RedGlobal.enviar_buffer(buffer)
		
		get_tree().change_scene_to_file("res://Mundo.tscn")
