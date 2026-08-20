extends OmniLight3D

"""
EfectoFuegoProfesional
Ajusta la intensidad de la luz mediante ruido procedural (FastNoiseLite)
pero con límites estrictos (clamp) para evitar el parpadeo epiléptico.
El objetivo es una atmósfera sutil, acogedora y cinematográfica.
"""

# Configuración base de la energía (ajusta esto en el Inspector)
@export var energia_base: float = 1.0
# Cuánto parpadea (valor sutil, p.ej: 0.1 o 0.2)
@export var intensidad_flicker: float = 0.2 
# Velocidad lenta para un ambiente relajado
@export var velocidad_flicker: float = 2.0  

# Límites de seguridad para que la luz nunca sea demasiado brillante ni demasiado oscura
@export var min_energy: float = 0.8
@export var max_energy: float = 1.2

var noise = FastNoiseLite.new()
var tiempo: float = 0.0

func _ready():
	# Iniciamos el generador de ruido
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX
	noise.frequency = 0.5 # Frecuencia baja para cambios suaves

func _process(delta: float):
	# Avanzamos el tiempo de forma muy pausada
	tiempo += delta * velocidad_flicker
	
	# Obtenemos el valor de ruido
	var valor_ruido = noise.get_noise_1d(tiempo)
	
	# Calculamos la energía objetivo
	var energia_objetivo = energia_base + (valor_ruido * intensidad_flicker)
	
	# CLAMP: Esta es la clave profesional. 
	# Obligamos a la luz a mantenerse dentro de unos límites agradables a la vista.
	# Esto elimina los saltos bruscos que causan fatiga visual.
	light_energy = clamp(energia_objetivo, min_energy, max_energy)
