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
Captura los eventos de entrada del hardware (ratón, teclado) que no han 
sido consumidos por la interfaz de usuario. Su función es leer el movimiento 
del ratón para rotar al personaje sobre el eje Y y orbitar el brazo de la cámara 
(SpringArm3D) en el eje X, solo si el movimiento está habilitado.
"""
func _unhandled_input(event):
	if not puede_moverse:
		return

	if event is InputEventMouseMotion:
		rotate_y(-event.relative.x * SENSITIVITY)
		spring_arm.rotate_x(-event.relative.y * SENSITIVITY)
		spring_arm.rotation.x = clamp(spring_arm.rotation.x, -PI/4, PI/4)

"""
_physics_process
Bucle físico del motor ejecutado en cada frame. Aplica la gravedad constante, 
procesa el salto, calcula los vectores de movimiento y desplaza al personaje. 
Gestiona un temporizador (tiempo_envio) para empaquetar y transmitir periódicamente 
las coordenadas globales del jugador al backend Java mediante el websocket.
"""
func _physics_process(delta):
	if not puede_moverse:
		return

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

	if Input.is_action_just_pressed("ui_cancel"):
		get_tree().quit()

	move_and_slide()

	tiempo_envio += delta
	if tiempo_envio >= INTERVALO_ENVIO:
		tiempo_envio = 0.0
		
		var cliente_red = get_parent()
		if cliente_red and "socket" in cliente_red:
			if cliente_red.socket.get_ready_state() == WebSocketPeer.STATE_OPEN:
				var mensaje_pos = "POS:" + str(global_position.x) + "," + str(global_position.y) + "," + str(global_position.z)
				cliente_red.socket.put_packet(mensaje_pos.to_utf8_buffer())

"""
habilitar_movimiento
Método de control de estado interno. Permite cambiar el booleano `puede_moverse` 
a verdadero para liberar los Controles del jugador una vez superada la fase de menús.
"""
func habilitar_movimiento():
	puede_moverse = true
