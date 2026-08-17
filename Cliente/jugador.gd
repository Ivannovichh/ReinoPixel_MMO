extends CharacterBody3D

const SPEED = 5.0
const JUMP_VELOCITY = 4.5
const SENSITIVITY = 0.0015

@onready var spring_arm = $SpringArm3D 

var gravity = ProjectSettings.get_setting("physics/3d/default_gravity")
var puede_moverse = false

var tiempo_envio = 0.0
const INTERVALO_ENVIO = 0.1 

# -------------------------------------------------------------------------
# _unhandled_input(event)
# Captura los eventos de entrada del hardware (ratón, teclado) que no han 
# sido consumidos por la interfaz de usuario.
# Su función aquí es leer el movimiento del ratón para rotar al personaje 
# sobre el eje Y y orbitar el brazo de la cámara (SpringArm3D) en el eje X,
# pero solo si el jugador ya se ha conectado (puede_moverse).
# -------------------------------------------------------------------------
func _unhandled_input(event):
	if not puede_moverse:
		return

	if event is InputEventMouseMotion:
		rotate_y(-event.relative.x * SENSITIVITY)
		spring_arm.rotate_x(-event.relative.y * SENSITIVITY)
		spring_arm.rotation.x = clamp(spring_arm.rotation.x, -PI/4, PI/4)


# -------------------------------------------------------------------------
# _physics_process(delta)
# Es el bucle físico del motor que se ejecuta constantemente en cada frame.
# Se encarga de aplicar la gravedad constante, procesar el salto, calcular los 
# vectores de dirección basándose en las teclas pulsadas y mover al personaje.
# Además, gestiona un temporizador (tiempo_envio) para empaquetar y enviar 
# periódicamente las coordenadas del jugador al servidor Java mediante el socket.
# -------------------------------------------------------------------------
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
				print("Enviando paquete: ", mensaje_pos)


# -------------------------------------------------------------------------
# habilitar_movimiento()
# Cambia el estado lógico interno del personaje. 
# Es un método público diseñado para ser llamado desde otros scripts 
# (como el cliente de red) una vez que el menú de login desaparece.
# -------------------------------------------------------------------------
func habilitar_movimiento():
	puede_moverse = true
