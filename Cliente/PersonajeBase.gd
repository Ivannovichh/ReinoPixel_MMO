extends Node3D

"""
PersonajeBase
Lógica central de personalización dinámica.
"""

@onready var slot_pelo = $Slot_Pelo
@onready var slot_armadura = $Slot_Armadura
@onready var cuerpo_base = $CuerpoBase

func aplicar_personalizacion(datos: Dictionary):
	print("Aplicando datos visuales al modelo: ", datos)
	
	# 1. Aplicar Sliders (Transformaciones y BlendShapes)
	if datos.has("altura"):
		self.scale.y = datos["altura"]
		
	if datos.has("musculatura") and cuerpo_base:
		# Ejemplo: Si usas BlendShapes en Blender
		# cuerpo_base.set_blend_shape_value(0, datos["musculatura"])
		pass
		
	# 2. Aplicar Desplegables (Mallas)
	if datos.has("pelo") and slot_pelo:
		var path_pelo = "res://Assets/Meshes/Pelo_" + str(datos["pelo"]) + ".res"
		if ResourceLoader.exists(path_pelo):
			slot_pelo.mesh = load(path_pelo)
			
	# 3. Aplicar Colores (Materiales)
	if datos.has("color_piel") and cuerpo_base:
		var mat_piel = StandardMaterial3D.new()
		mat_piel.albedo_color = Color(datos["color_piel"])
		cuerpo_base.set_surface_override_material(0, mat_piel)
		
	if datos.has("color_ojos") and has_node("SlotOjos"):
		var mat_ojos = StandardMaterial3D.new()
		mat_ojos.albedo_color = Color(datos["color_ojos"])
		$SlotOjos.set_surface_override_material(0, mat_ojos)
