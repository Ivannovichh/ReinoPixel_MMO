extends Node3D

"""
PersonajeBase.gd
Plantilla central de personalización dinámica para Reino Pixel MMO.
Gestiona la apariencia completa del avatar: cuerpo, pelo, ropa, colores y morfología.
Compatible con la pantalla de CreacionPersonaje.gd y con el mundo del juego.

Estructura de nodos esperada en la escena (.tscn):
PersonajeBase (Node3D)
├── Esqueleto (Skeleton3D)
│   ├── CuerpoBase (MeshInstance3D)       ← Malla con BlendShapes: musculatura, edad, grasa
│   ├── Slot_Pelo (MeshInstance3D)         ← Intercambiable por índice
│   ├── Slot_Cejas (MeshInstance3D)        ← Intercambiable por índice
│   ├── Slot_Ojos (MeshInstance3D)         ← Color dinámico
│   ├── Slot_Ropa_Torso (MeshInstance3D)   ← Equipamiento / ropa base
│   ├── Slot_Ropa_Piernas (MeshInstance3D)
│   ├── Slot_Ropa_Pies (MeshInstance3D)
│   ├── Slot_Accesorio (MeshInstance3D)    ← Reservado para ítems
│   └── Slot_Barba (MeshInstance3D)        ← Solo masculino (índice 0 = oculto)
├── AnimationPlayer
└── CollisionShape3D (si se usa en juego)

BLENDSHAPES esperados en CuerpoBase (orden en Blender → Godot):
  Índice 0: Musculatura   (0.0 = delgado, 1.0 = musculado)
  Índice 1: Grasa         (0.0 = sin grasa, 1.0 = robusto)
  Índice 2: Edad          (0.0 = joven, 1.0 = mayor)
  Índice 3: Femenino      (0.0 = neutro/masc, 1.0 = formas femeninas)
  Índice 4: Cuerpo_Alt    (variación corporal secundaria del desplegable "cuerpo")
"""

# ---------------------------------------------------------------------------
# ÍNDICES DE BLENDSHAPES — ajusta si cambias el orden en Blender
# ---------------------------------------------------------------------------
const BS_MUSCULATURA  := 0
const BS_GRASA        := 1
const BS_EDAD         := 2
const BS_FEMENINO     := 3
const BS_CUERPO_ALT   := 4

# ---------------------------------------------------------------------------
# RUTAS DE ASSETS — adapta a tu estructura de carpetas en res://
# ---------------------------------------------------------------------------
const RUTA_PELO       := "res://Assets/Meshes/Pelo/Pelo_%d_%s.tres"
	# Ejemplo: Pelo_0_M.tres (estilo 0, masculino), Pelo_2_F.tres (estilo 2, femenino)
const RUTA_CEJAS      := "res://Assets/Meshes/Cejas/Cejas_%d.tres"
const RUTA_CUERPO_M   := "res://Assets/Meshes/Cuerpo/CuerpoBase_M.tres"
const RUTA_CUERPO_F   := "res://Assets/Meshes/Cuerpo/CuerpoBase_F.tres"
const RUTA_ROPA_TORSO := "res://Assets/Meshes/Ropa/Torso_%d_%s.tres"
const RUTA_ROPA_PIERNAS := "res://Assets/Meshes/Ropa/Piernas_%d_%s.tres"
const RUTA_ROPA_PIES  := "res://Assets/Meshes/Ropa/Pies_%d_%s.tres"

# ---------------------------------------------------------------------------
# REFERENCIAS A NODOS (se asignan en _ready)
# ---------------------------------------------------------------------------
@onready var cuerpo_base:       MeshInstance3D = $Esqueleto/CuerpoBase
@onready var slot_pelo:         MeshInstance3D = $Esqueleto/Slot_Pelo
@onready var slot_cejas:        MeshInstance3D = $Esqueleto/Slot_Cejas
@onready var slot_ojos:         MeshInstance3D = $Esqueleto/Slot_Ojos
@onready var slot_ropa_torso:   MeshInstance3D = $Esqueleto/Slot_Ropa_Torso
@onready var slot_ropa_piernas: MeshInstance3D = $Esqueleto/Slot_Ropa_Piernas
@onready var slot_ropa_pies:    MeshInstance3D = $Esqueleto/Slot_Ropa_Pies
@onready var slot_barba:        MeshInstance3D = $Esqueleto/Slot_Barba

# Estado interno del personaje (espejo del diccionario de CreacionPersonaje)
var _datos_actuales: Dictionary = {}

# Caché de materiales para no recrearlos en cada frame
var _mat_piel:  StandardMaterial3D = null
var _mat_ojos:  StandardMaterial3D = null
var _mat_pelo:  StandardMaterial3D = null


# ---------------------------------------------------------------------------
# INICIALIZACIÓN
# ---------------------------------------------------------------------------

func _ready() -> void:
	_inicializar_materiales()
	# Aplicar datos por defecto si nadie los ha asignado aún
	if _datos_actuales.is_empty():
		aplicar_personalizacion(_datos_por_defecto())


"""
_datos_por_defecto
Devuelve el estado cosmético inicial (masculino, nivel 1, aspecto neutro).
Útil tanto para vista previa como para instanciar NPCs sin personalizar.
"""
func _datos_por_defecto() -> Dictionary:
	return {
		"genero":      0,
		"cuerpo":      0,
		"pelo":        0,
		"forma_ojos":  0,
		"altura":      1.0,
		"musculatura": 0.3,
		"edad":        0.1,
		"color_piel":  "ffccaa",
		"color_ojos":  "4a90d9",
		"color_pelo":  "3b2314",
		"ropa_torso":  0,
		"ropa_piernas":0,
		"ropa_pies":   0,
	}


"""
_inicializar_materiales
Crea una sola vez los materiales reutilizables para piel, ojos y pelo.
Usa surface_override_material para no modificar el recurso original de la malla.
"""
func _inicializar_materiales() -> void:
	_mat_piel = StandardMaterial3D.new()
	_mat_piel.roughness = 0.85
	_mat_piel.metallic = 0.0

	_mat_ojos = StandardMaterial3D.new()
	_mat_ojos.roughness = 0.1
	_mat_ojos.metallic = 0.05

	_mat_pelo = StandardMaterial3D.new()
	_mat_pelo.roughness = 0.9
	_mat_pelo.metallic = 0.0


# ---------------------------------------------------------------------------
# API PÚBLICA — llamada desde CreacionPersonaje.gd y desde el juego
# ---------------------------------------------------------------------------

"""
aplicar_personalizacion
Punto de entrada principal. Recibe el diccionario completo de datos del personaje
y delega cada categoría a su método especializado. Se llama en tiempo real
desde la pantalla de creación y una sola vez al entrar al mundo del juego.
"""
func aplicar_personalizacion(datos: Dictionary) -> void:
	_datos_actuales = datos.duplicate()

	_aplicar_genero(datos.get("genero", 0))
	_aplicar_altura(datos.get("altura", 1.0))
	_aplicar_morfologia(
		datos.get("musculatura", 0.3),
		datos.get("edad", 0.1),
		datos.get("cuerpo", 0)
	)
	_aplicar_color_piel(datos.get("color_piel", "ffccaa"))
	_aplicar_pelo(datos.get("pelo", 0), datos.get("genero", 0), datos.get("color_pelo", "3b2314"))
	_aplicar_color_ojos(datos.get("color_ojos", "4a90d9"))
	_aplicar_ropa(
		datos.get("ropa_torso", 0),
		datos.get("ropa_piernas", 0),
		datos.get("ropa_pies", 0),
		datos.get("genero", 0)
	)


# ---------------------------------------------------------------------------
# MÉTODOS INTERNOS DE PERSONALIZACIÓN
# ---------------------------------------------------------------------------

"""
_aplicar_genero
Cambia la malla base del cuerpo entre masculina y femenina.
También activa el BlendShape de silueta femenina para suavizar la transición.
"""
func _aplicar_genero(genero: int) -> void:
	if not cuerpo_base:
		return

	var sufijo := "M" if genero == 0 else "F"
	var ruta := RUTA_CUERPO_M if genero == 0 else RUTA_CUERPO_F

	if ResourceLoader.exists(ruta):
		cuerpo_base.mesh = load(ruta)
		_reasignar_materiales_cuerpo()

	# BlendShape de silueta femenina
	_set_blendshape(cuerpo_base, BS_FEMENINO, 1.0 if genero == 1 else 0.0)

	# La barba solo es visible en masculino
	if slot_barba:
		slot_barba.visible = (genero == 0)


"""
_aplicar_altura
Escala el personaje en Y manteniendo X y Z originales para no deformar.
El slider va de 0.85 (muy bajo) a 1.15 (muy alto), con 1.0 como media.
"""
func _aplicar_altura(altura: float) -> void:
	self.scale.y = clampf(altura, 0.85, 1.15)


"""
_aplicar_morfologia
Controla los tres BlendShapes que definen la complexión corporal:
- musculatura: delgado ↔ musculado
- edad: joven ↔ mayor (arrugas, postura)
- cuerpo: variación alternativa (segundo set de formas)
"""
func _aplicar_morfologia(musculatura: float, edad: float, cuerpo: int) -> void:
	if not cuerpo_base:
		return

	_set_blendshape(cuerpo_base, BS_MUSCULATURA, musculatura)
	_set_blendshape(cuerpo_base, BS_EDAD, edad)

	# El desplegable "cuerpo" activa formas alternativas
	match cuerpo:
		0: # Ectomorfo / estándar
			_set_blendshape(cuerpo_base, BS_GRASA, 0.0)
			_set_blendshape(cuerpo_base, BS_CUERPO_ALT, 0.0)
		1: # Endomorfo / robusto
			_set_blendshape(cuerpo_base, BS_GRASA, 0.7)
			_set_blendshape(cuerpo_base, BS_CUERPO_ALT, 0.3)
		2: # Mesomorfo / atlético
			_set_blendshape(cuerpo_base, BS_GRASA, 0.0)
			_set_blendshape(cuerpo_base, BS_CUERPO_ALT, 0.6)
		_:
			pass


"""
_aplicar_color_piel
Actualiza el material de la superficie 0 del cuerpo con el color hexadecimal elegido.
Usa el material cacheado para evitar GC innecesario en tiempo real.
"""
func _aplicar_color_piel(hex: String) -> void:
	if not cuerpo_base or not _mat_piel:
		return

	var color := Color("#" + hex) if not hex.begins_with("#") else Color(hex)
	_mat_piel.albedo_color = color
	cuerpo_base.set_surface_override_material(0, _mat_piel)


"""
_aplicar_pelo
Carga la malla de pelo correspondiente al estilo e índice de género.
También aplica el color elegido como tinte sobre el material base.
"""
func _aplicar_pelo(estilo: int, genero: int, color_hex: String) -> void:
	if not slot_pelo:
		return

	var sufijo := "M" if genero == 0 else "F"
	var ruta := RUTA_PELO % [estilo, sufijo]

	if ResourceLoader.exists(ruta):
		slot_pelo.mesh = load(ruta)
		slot_pelo.visible = true
	else:
		# Estilo 0 con género alternativo: intentar la versión genérica
		var ruta_fallback := "res://Assets/Meshes/Pelo/Pelo_%d.tres" % estilo
		if ResourceLoader.exists(ruta_fallback):
			slot_pelo.mesh = load(ruta_fallback)
			slot_pelo.visible = true
		else:
			slot_pelo.visible = false  # Sin pelo / rapado
			return

	# Tinte de color
	var color := Color("#" + color_hex) if not color_hex.begins_with("#") else Color(color_hex)
	_mat_pelo.albedo_color = color
	slot_pelo.set_surface_override_material(0, _mat_pelo)


"""
_aplicar_color_ojos
Actualiza el material de los ojos con el color hexadecimal del selector.
"""
func _aplicar_color_ojos(hex: String) -> void:
	if not slot_ojos or not _mat_ojos:
		return

	var color := Color("#" + hex) if not hex.begins_with("#") else Color(hex)
	_mat_ojos.albedo_color = color
	slot_ojos.set_surface_override_material(0, _mat_ojos)


"""
_aplicar_ropa
Carga las mallas de torso, piernas y pies según el índice de ropa elegido.
El índice 0 siempre es la ropa base de inicio (ropa de aldeano / por defecto).
"""
func _aplicar_ropa(torso: int, piernas: int, pies: int, genero: int) -> void:
	var sufijo := "M" if genero == 0 else "F"

	_cargar_malla_slot(slot_ropa_torso,   RUTA_ROPA_TORSO   % [torso,   sufijo])
	_cargar_malla_slot(slot_ropa_piernas, RUTA_ROPA_PIERNAS % [piernas, sufijo])
	_cargar_malla_slot(slot_ropa_pies,    RUTA_ROPA_PIES    % [pies,    sufijo])


# ---------------------------------------------------------------------------
# MÉTODOS DE UTILIDAD
# ---------------------------------------------------------------------------

"""
_set_blendshape
Asigna de forma segura el valor de un BlendShape a un MeshInstance3D.
Comprueba que el índice exista antes de modificarlo.
"""
func _set_blendshape(mesh_inst: MeshInstance3D, indice: int, valor: float) -> void:
	if not mesh_inst or not mesh_inst.mesh:
		return
	if indice < mesh_inst.mesh.get_blend_shape_count():
		mesh_inst.set_blend_shape_value(indice, clampf(valor, 0.0, 1.0))


"""
_cargar_malla_slot
Carga una malla en un slot dado. Si no existe el recurso, oculta el slot
en lugar de lanzar un error que rompería la escena.
"""
func _cargar_malla_slot(slot: MeshInstance3D, ruta: String) -> void:
	if not slot:
		return
	if ResourceLoader.exists(ruta):
		slot.mesh = load(ruta)
		slot.visible = true
	else:
		slot.visible = false


"""
_reasignar_materiales_cuerpo
Cuando se cambia la malla base (masculino ↔ femenino), los override materials
se limpian. Este método los vuelve a aplicar con los colores actuales.
"""
func _reasignar_materiales_cuerpo() -> void:
	if _datos_actuales.has("color_piel"):
		_aplicar_color_piel(_datos_actuales["color_piel"])


# ---------------------------------------------------------------------------
# API PARA EL JUEGO (fuera de la pantalla de creación)
# ---------------------------------------------------------------------------

"""
equipar_item
Punto de entrada para que el GestorInventario equipe una pieza de equipamiento.
Slot válidos: "torso", "piernas", "pies", "pelo", "accesorio"
"""
func equipar_item(slot_nombre: String, ruta_mesh: String) -> void:
	var slot_map := {
		"torso":    slot_ropa_torso,
		"piernas":  slot_ropa_piernas,
		"pies":     slot_ropa_pies,
		"pelo":     slot_pelo,
	}
	if slot_map.has(slot_nombre):
		_cargar_malla_slot(slot_map[slot_nombre], ruta_mesh)


"""
desequipar_item
Oculta el slot especificado (desequipa sin cargar la ropa base).
"""
func desequipar_item(slot_nombre: String) -> void:
	var slot_map := {
		"torso":    slot_ropa_torso,
		"piernas":  slot_ropa_piernas,
		"pies":     slot_ropa_pies,
		"pelo":     slot_pelo,
		"accesorio": slot_ropa_torso,  # redirige al accesorio si lo usas separado
	}
	if slot_map.has(slot_nombre):
		var s: MeshInstance3D = slot_map[slot_nombre]
		if s:
			s.visible = false


"""
obtener_datos_actuales
Devuelve una copia del diccionario cosmético actual.
Útil para serializar el estado al servidor o guardar en local.
"""
func obtener_datos_actuales() -> Dictionary:
	return _datos_actuales.duplicate()
