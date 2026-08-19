
 -- Creación de la tabla principal de jugadores.
 -- Define la estructura relacional para almacenar de forma segura las credenciales 
 -- de los usuarios que se registran en el MMO.
 -- Utiliza un identificador autoincremental como clave primaria y aplica una 
 -- restricción de unicidad (UNIQUE) en la columna del correo para asegurar 
 -- que no existan cuentas duplicadas en el sistema.

CREATE TABLE jugadores (
    id SERIAL PRIMARY KEY,
    correo VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

/*
 * Creación de la tabla de tokens de recuperación.
 * Almacena códigos temporales generados por el servidor para verificar 
 * la identidad del jugador cuando solicita un cambio de contraseña.
 * Vincula el código al identificador del jugador y establece una fecha 
 * de expiración para invalidar peticiones antiguas por seguridad.
 */
CREATE TABLE tokens_recuperacion (
    id SERIAL PRIMARY KEY,
    jugador_id INTEGER REFERENCES jugadores(id) ON DELETE CASCADE,
    codigo VARCHAR(50) UNIQUE NOT NULL,
    fecha_expiracion TIMESTAMP NOT NULL
);

CREATE TABLE personajes (
    id SERIAL PRIMARY KEY,
    -- Clave foránea: Vincula el personaje a la cuenta. Si se borra la cuenta, se borra el personaje.
    jugador_id INTEGER REFERENCES jugadores(id) ON DELETE CASCADE,
    nombre VARCHAR(50) UNIQUE NOT NULL,
    nivel INTEGER DEFAULT 1,
    -- Coordenadas espaciales para el mundo 3D (o 2D si ignoras la Z)
    pos_x REAL DEFAULT 0.0,
    pos_y REAL DEFAULT 0.0,
    pos_z REAL DEFAULT 0.0,
    ultima_conexion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);