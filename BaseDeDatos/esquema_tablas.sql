-- Creación de la tabla de usuarios para el sistema de login y registro
CREATE TABLE IF NOT EXISTS usuarios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    identificador TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL
);