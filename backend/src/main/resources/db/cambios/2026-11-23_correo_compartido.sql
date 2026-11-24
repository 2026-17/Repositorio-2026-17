-- =====================================================================
--  SIGAC - Cambio 2026-11-23: varias cuentas pueden compartir un correo
--  Motor: MySQL 8.0
-- =====================================================================
--  La llave única de una cuenta es la CÉDULA. Este script quita el UNIQUE
--  del correo y deja un índice normal para que las búsquedas sigan rápidas.
--  La restricción de la cédula (uq_usuarios_cedula) NO se toca.
--
--  ORDEN: ejecútelo ANTES de desplegar el backend que permite correos
--  repetidos. El backend actual sigue funcionando con este cambio.
--
--  CÓMO CORRERLO EN WORKBENCH
--  1. Seleccione la base correcta (doble clic en sigac_db).
--  2. Ejecute todo con el rayo sin cursor (Ctrl + Shift + Enter).
--  3. Revise la última consulta, explicada al final de este archivo.
--
--  START TRANSACTION NO APLICA: en MySQL cada ALTER TABLE hace commit
--  automático y no se puede revertir con ROLLBACK. Por eso el script
--  crea primero el índice nuevo y después quita el anterior.
--  Se puede correr varias veces: si un paso ya está hecho, solo lo avisa.
--
--  PARA DEVOLVERLO (solo si ninguna cuenta comparte correo todavía):
--    ALTER TABLE usuarios ADD CONSTRAINT uq_usuarios_correo UNIQUE (correo);
--    ALTER TABLE usuarios DROP INDEX idx_usuarios_correo;
-- =====================================================================

-- 1. Crear el índice normal, si no existe
SET @tabla_usuarios := (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'usuarios'
);
SET @hay_indice := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'usuarios'
      AND index_name = 'idx_usuarios_correo'
);
SET @sql := IF(@tabla_usuarios = 0,
    'SELECT ''No se encontró la tabla usuarios: seleccione la base correcta.'' AS aviso',
    IF(@hay_indice = 0,
        'ALTER TABLE usuarios ADD INDEX idx_usuarios_correo (correo)',
        'SELECT ''El índice idx_usuarios_correo ya existía.'' AS aviso'));
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

-- 2. Quitar la restricción única del correo, si existe
SET @hay_unico := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'usuarios'
      AND index_name = 'uq_usuarios_correo'
);
SET @sql := IF(@hay_unico > 0,
    'ALTER TABLE usuarios DROP INDEX uq_usuarios_correo',
    'SELECT ''La restricción uq_usuarios_correo ya no existe.'' AS aviso');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

-- 3. Verificación
SELECT index_name   AS indice,
       column_name  AS columna,
       IF(non_unique = 0, 'ÚNICO', 'normal') AS tipo
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'usuarios'
  AND column_name IN ('correo', 'cedula')
ORDER BY column_name, index_name;

-- RESULTADO ESPERADO (2 filas):
--   uq_usuarios_cedula   | cedula | ÚNICO
--   idx_usuarios_correo  | correo | normal
-- Si aparece otra fila ÚNICO en la columna correo, la base tiene una
-- restricción con otro nombre: NO despliegue el backend todavía.
