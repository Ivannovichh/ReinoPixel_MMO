"""
CreacionPersonaje_mejoras.gd
PARCHE DE AMPLIACIÓN — no reemplaza tu CreacionPersonaje.gd original.
Añade las claves que faltan en datos_personaje para conectar con PersonajeBase.gd.

INSTRUCCIONES:
1. Abre tu CreacionPersonaje.gd original.
2. Aplica los cambios marcados con ">>> AÑADIR" en los lugares indicados.
"""

# ─────────────────────────────────────────────────────────────────────────────
# >>> AÑADIR en datos_personaje (ampliar el diccionario existente):
# ─────────────────────────────────────────────────────────────────────────────

var datos_personaje = {
	# --- YA LOS TIENES ---
	"genero":      0,
	"cuerpo":      0,
	"pelo":        0,
	"forma_ojos":  0,
	"altura":      1.0,
	"musculatura": 0.3,   # Cambiar valor inicial de 1.0 → 0.3 (más natural)
	"edad":        0.1,   # Cambiar valor inicial de 0.5 → 0.1 (personaje joven)
	"color_piel":  "ffccaa",
	"color_ojos":  "4a90d9",

	# >>> AÑADIR ESTAS CLAVES NUEVAS:
	"color_pelo":   "3b2314",  # Castaño oscuro por defecto
	"ropa_torso":   0,         # Ropa base aldeano
	"ropa_piernas": 0,
	"ropa_pies":    0,
}


# ─────────────────────────────────────────────────────────────────────────────
# >>> AÑADIR en _configurar_interfaz_cosmetica() — al final del método:
# ─────────────────────────────────────────────────────────────────────────────

func _configurar_nuevos_controles():
	var ruta_base = "MarginContainer/DistribucionGlobal/MenuPestanas/Apariencia/ColumnaAspecto/MarginContainer/ListaOpciones/"

	# Selector de color de pelo (añade un ColorPickerButton llamado "ColorPelo" en el editor)
	var c_pelo = get_node_or_null(ruta_base + "ColorPelo")
	if c_pelo:
		c_pelo.color_changed.connect(func(color): _actualizar_datos("color_pelo", color.to_html(false)))

	# Desplegables de ropa inicial (OptionButton "DropRopaTorso", "DropRopaPiernas", "DropRopaPies")
	var d_torso = get_node_or_null(ruta_base + "DropRopaTorso")
	if d_torso:
		d_torso.item_selected.connect(func(idx): _actualizar_datos("ropa_torso", idx))

	var d_piernas = get_node_or_null(ruta_base + "DropRopaPiernas")
	if d_piernas:
		d_piernas.item_selected.connect(func(idx): _actualizar_datos("ropa_piernas", idx))

	var d_pies = get_node_or_null(ruta_base + "DropRopaPies")
	if d_pies:
		d_pies.item_selected.connect(func(idx): _actualizar_datos("ropa_pies", idx))


# ─────────────────────────────────────────────────────────────────────────────
# >>> MODIFICAR _on_btn_confirmar_pressed() — añadir los nuevos campos al buffer:
# ─────────────────────────────────────────────────────────────────────────────

func _enviar_campos_adicionales(buffer) -> void:
	"""
	Llama a esto DENTRO de _on_btn_confirmar_pressed, justo después de
	escribir color_ojos en el buffer, antes de enviar.
	"""
	RedGlobal.escribir_string_en_buffer(buffer, datos_personaje["color_pelo"])
	buffer.put_32(datos_personaje["ropa_torso"])
	buffer.put_32(datos_personaje["ropa_piernas"])
	buffer.put_32(datos_personaje["ropa_pies"])


# ─────────────────────────────────────────────────────────────────────────────
# VALORES SUGERIDOS PARA LOS OPTIONBUTTON DE PELO (añadir en el editor):
# ─────────────────────────────────────────────────────────────────────────────
# DropPelo:
#   Item 0: "Rapado"
#   Item 1: "Corto"
#   Item 2: "Medio"
#   Item 3: "Largo"
#   Item 4: "Especial"
#
# DropCuerpo:
#   Item 0: "Ectomorfo (Delgado)"
#   Item 1: "Endomorfo (Robusto)"
#   Item 2: "Mesomorfo (Atlético)"
#
# DropRopaTorso:
#   Item 0: "Ropa de Aldeano"
#   Item 1: "Armadura de Cuero"
#
# DropRopaPiernas:
#   Item 0: "Pantalón Básico"
#   Item 1: "Pantalón de Cuero"
#
# DropRopaPies:
#   Item 0: "Botas Básicas"
#   Item 1: "Botas de Aventurero"
# ─────────────────────────────────────────────────────────────────────────────
