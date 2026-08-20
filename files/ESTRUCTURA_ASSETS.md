# Reino Pixel MMO — Estructura de Assets y Convención de Nombres

Esta guía define **dónde colocar cada archivo** y **cómo nombrarlo** para que
`PersonajeBase.gd` los encuentre automáticamente sin modificar el código.

---

## Jerarquía de carpetas en `res://`

```
res://
├── Personajes/
│   ├── PersonajeBase.gd
│   ├── PersonajeBase.tscn
│   └── PersonajeNPC.gd         ← hereda de PersonajeBase, sin controles de input
│
└── Assets/
    └── Meshes/
        ├── Cuerpo/
        │   ├── CuerpoBase_M.tres   ← Malla masculina con BlendShapes
        │   └── CuerpoBase_F.tres   ← Malla femenina con BlendShapes
        │
        ├── Pelo/
        │   ├── Pelo_0_M.tres       ← Rapado / sin pelo masculino
        │   ├── Pelo_0_F.tres       ← Rapado / sin pelo femenino
        │   ├── Pelo_1_M.tres       ← Corto masculino
        │   ├── Pelo_1_F.tres       ← Corto femenino
        │   ├── Pelo_2_M.tres       ← Medio masculino
        │   ├── Pelo_2_F.tres       ← Medio femenino
        │   ├── Pelo_3_M.tres       ← Largo masculino (coleta)
        │   ├── Pelo_3_F.tres       ← Largo femenino
        │   ├── Pelo_4_M.tres       ← Mohicano / estilo especial
        │   └── Pelo_4_F.tres       ← Trenzas / estilo especial
        │
        ├── Cejas/
        │   ├── Cejas_0.tres        ← Cejas finas
        │   ├── Cejas_1.tres        ← Cejas normales
        │   └── Cejas_2.tres        ← Cejas gruesas / tupidas
        │
        └── Ropa/
            ├── Torso_0_M.tres      ← Ropa base masculina (camisa aldeano)
            ├── Torso_0_F.tres      ← Ropa base femenina (blusa aldeano)
            ├── Torso_1_M.tres      ← Armadura de cuero masculina
            ├── Torso_1_F.tres      ← Armadura de cuero femenina
            ├── Piernas_0_M.tres    ← Pantalón base masculino
            ├── Piernas_0_F.tres    ← Falda/pantalón base femenino
            ├── Piernas_1_M.tres    ← Pantalón de cuero
            ├── Piernas_1_F.tres    ← Pantalón de cuero
            ├── Pies_0_M.tres       ← Botas básicas masculinas
            ├── Pies_0_F.tres       ← Botas básicas femeninas
            ├── Pies_1_M.tres       ← Botas de aventurero
            └── Pies_1_F.tres       ← Botas de aventurero
```

---

## BlendShapes requeridos en Blender

Exporta estas **Shape Keys** en el objeto del cuerpo **en este orden exacto**:

| Índice | Nombre en Blender   | Efecto en juego                         |
|--------|---------------------|-----------------------------------------|
| 0      | `BS_Musculatura`    | 0.0 = delgado · 1.0 = muy musculado     |
| 1      | `BS_Grasa`          | 0.0 = sin grasa · 1.0 = robusto/gordo  |
| 2      | `BS_Edad`           | 0.0 = joven · 1.0 = mayor (arrugas)    |
| 3      | `BS_Femenino`       | 0.0 = neutro · 1.0 = silueta femenina  |
| 4      | `BS_Cuerpo_Alt`     | Variación corporal del desplegable      |

> **Tip:** Puedes tener UNA SOLA malla para ambos géneros usando `BS_Femenino`.
> Si prefieres dos mallas separadas (mayor calidad), el script ya lo gestiona
> cargando `CuerpoBase_M.tres` o `CuerpoBase_F.tres` según el género elegido.

---

## Exportar desde Blender

1. **Formato:** GLTF 2.0 (`.glb` recomendado, más compacto)
2. **Incluir:** Shape Keys ✅ · Skeleton ✅ · Animations ✅
3. En Godot: al importar el `.glb`, ve a **Import → Animation** y activa
   *"Import as Skeleton"* para que genere el `Skeleton3D` automáticamente.
4. Renombra el `Skeleton3D` generado a `"Esqueleto"` para que coincida
   con los `@onready` del script.

---

## Añadir nuevos estilos de pelo / ropa

Solo necesitas:
1. Modelar la nueva malla en Blender y exportarla siguiendo la convención de nombres.
2. Colocarla en la carpeta correspondiente dentro de `res://Assets/Meshes/`.
3. Añadir la nueva opción en el `OptionButton` de la pantalla de creación.

**No hace falta tocar el código** — el script construye la ruta dinámicamente.

---

## Estilos de pelo sugeridos para el desplegable (CreacionPersonaje)

```
Índice 0 → "Rapado"
Índice 1 → "Corto"
Índice 2 → "Medio"
Índice 3 → "Largo"
Índice 4 → "Especial"
```

## Tipos de cuerpo (desplegable "Cuerpo")

```
Índice 0 → "Ectomorfo"   (delgado, ligero)
Índice 1 → "Endomorfo"   (robusto, más volumen)
Índice 2 → "Mesomorfo"   (atlético, equilibrado)
```
