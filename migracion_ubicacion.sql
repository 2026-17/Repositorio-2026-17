-- =====================================================================
--  Migración: dirección con provincia / cantón / distrito (catálogos)
--  Para bases que ya existen (local o Railway). Se corre UNA sola vez,
--  después de tener cargadas las tablas provincia, canton y distrito.
-- =====================================================================
USE sigac_db;

-- Estudiantes: distrito de residencia (la columna direccion queda como señas exactas)
ALTER TABLE estudiantes
  ADD COLUMN codigo_distrito INT NULL AFTER correo,
  ADD CONSTRAINT fk_estudiantes_distrito FOREIGN KEY (codigo_distrito) REFERENCES distrito (codigo);

-- Profesores: igual que estudiantes, pero opcional
ALTER TABLE profesores
  ADD COLUMN codigo_distrito INT NULL AFTER correo,
  ADD CONSTRAINT fk_profesores_distrito FOREIGN KEY (codigo_distrito) REFERENCES distrito (codigo);

-- Los registros viejos quedan con codigo_distrito en NULL y conservan su
-- dirección en texto libre. Al editarlos, el formulario pedirá el distrito.
