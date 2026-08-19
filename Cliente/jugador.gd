extends CharacterBody3D

const SPEED = 5.0
const JUMP_VELOCITY = 4.5
const SENSITIVITY = 0.0015

@onready var spring_arm = $SpringArm3D 

var gravity = ProjectSettings.get_setting("physics/3d/default_gravity")
var puede_moverse = false
var tiempo_envio = 0.0
const INTERVALO_ENVIO = 0.1 

"""
_unhandled_input
Gobernante de la visión. Controla la cámara tipo tercera persona bloqueada.
"""
func _unhandled_input(event):
	if not puede_moverse: return

	if event is InputEventMouseMotion:
		rotate_y(-event.relative.x * SENSITIVITY)
		spring_arm.rotate_x(-event.relative.y * SENSITIVITY)
		spring_arm.rotation.x = clamp(spring_arm.rotation.x, -PI/4, PI/4)

"""
_physics_process
Procesador de mecánicas físicas e inyector de coordenadas al servidor.
Inserta vectores espaciales puros al buffer para optimizar la carga del ancho de banda.
"""
func _physics_process(delta):
	if not puede_moverse: return

	if not is_on_floor():
		velocity.y -= gravity * delta

	if Input.is_action_just_pressed("saltar") and is_on_floor():
		velocity.y = JUMP_VELOCITY

	var input_dir = Input.get_vector("mover_izquierda", "mover_derecha", "mover_adelante", "mover_atras")
	var direction = (transform.basis * Vector3(input_dir.x, 0, input_dir.y)).normalized()
	
	if direction:
		velocity.x = direction.x * SPEED
		velocity.z = direction.z * SPEED
	else:
		velocity.x = move_toward(velocity.x, 0, SPEED)
		velocity.z = move_toward(velocity.z, 0, SPEED)

	if Input.is_action_just_pressed("ui_cancel"): get_tree().quit()

	move_and_slide()

	tiempo_envio += delta
	if tiempo_envio >= INTERVALO_ENVIO:
		tiempo_envio = 0.0
		var buffer = RedGlobal.crear_buffer_salida(RedGlobal.C_MOVER_PERSONAJE)
		buffer.put_float(global_position.x)
		buffer.put_float(global_position.y)
		buffer.put_float(global_position.z)
		RedGlobal.enviar_buffer(buffer)

"""
habilitar_movimiento
Interruptor lógico invocado tras completarse los tiempos de carga en el mundo virtual.
"""
func habilitar_movimiento():
	puede_moverse = true
