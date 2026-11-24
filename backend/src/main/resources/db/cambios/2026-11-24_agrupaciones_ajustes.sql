-- =====================================================================
--  SIGAC - Cambio 2026-11-24 (ajustes): quiénes pueden matricular una
--  agrupación, edades de los cursos y orden de selección
--  Motor: MySQL 8.0.16 o superior (usa CHECK y funciones de ventana)
-- =====================================================================
--  ORDEN: se ejecuta DESPUÉS de db/cambios/2026-11-24_agrupaciones.sql
--  (necesita la tabla agrupaciones). Ese script NO se modifica porque ya
--  pudo haberse corrido. Para la regla "solo inclusión" (D6) la base
--  también debe tener db/cambios/2026-11-17_tipo_estudiante_inclusion.sql.
--
--  CÓMO CORRERLO EN WORKBENCH
--  1. Saque un respaldo de la base (sobre todo en Railway).
--  2. Seleccione la base correcta: doble clic en ella en el panel
--     "Schemas" (o ejecute USE sigac_db; / USE railway;).
--  3. File > Open SQL Script > este archivo.
--  4. Ejecute TODO con el rayo sin cursor (Ctrl + Shift + Enter).
--  5. Revise el resultado "comprobacion" del paso 0 y las consultas de
--     verificación del final.
--
--  ALTER TABLE NO SE DESHACE CON ROLLBACK: en MySQL cada ALTER TABLE hace
--  commit automático. Por eso no se usa START TRANSACTION: cada paso
--  revisa primero si ya está hecho y el script se puede correr varias
--  veces sin error ('DO 0' = no hacer nada).
--
--  Qué hace:
--    a) agrupaciones: solo_inclusion, edad_minima, edad_maxima y la regla
--       de que la máxima no sea menor que la mínima.
--    b) cursos: edad_minima y edad_maxima (solo si faltan; sigac_db.sql
--       ya las trae) y es_curso_agrupacion, marcado en los códigos CI-% y
--       CORO-% (Conjunto Instrumental y Coro: se cursan en agrupaciones,
--       no en grupos normales).
--    c) matriculas_detalles.orden_seleccion: orden en que el estudiante
--       eligió sus agrupaciones (regla D4: la SEGUNDA se califica como
--       coro cuando no lleva ninguna coral). Se rellena en las matrículas
--       que ya existen, por el orden en que se guardaron.
--  Las edades son en años cumplidos a la fecha de inicio del período.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. Comprobación: el script de agrupaciones debe estar aplicado
-- ---------------------------------------------------------------------
SELECT IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agrupaciones') = 1,
    'Correcto: la base tiene las agrupaciones.',
    'DETÉNGASE: falta 2026-11-24_agrupaciones.sql o la base no es la correcta.'
) AS comprobacion;

-- ---------------------------------------------------------------------
-- a) agrupaciones: quiénes pueden matricularla
-- ---------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agrupaciones'
        AND COLUMN_NAME = 'solo_inclusion') = 0,
    'ALTER TABLE agrupaciones ADD COLUMN solo_inclusion BOOLEAN NOT NULL DEFAULT FALSE AFTER activa',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agrupaciones'
        AND COLUMN_NAME = 'edad_minima') = 0,
    'ALTER TABLE agrupaciones ADD COLUMN edad_minima TINYINT UNSIGNED NULL AFTER solo_inclusion',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agrupaciones'
        AND COLUMN_NAME = 'edad_maxima') = 0,
    'ALTER TABLE agrupaciones ADD COLUMN edad_maxima TINYINT UNSIGNED NULL AFTER edad_minima',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'agrupaciones'
        AND CONSTRAINT_NAME = 'ck_agrupaciones_edades') = 0,
    'ALTER TABLE agrupaciones ADD CONSTRAINT ck_agrupaciones_edades CHECK (edad_minima IS NULL OR edad_maxima IS NULL OR edad_maxima >= edad_minima)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- b) cursos
-- ---------------------------------------------------------------------

-- b.1) Edades (sigac_db.sql ya las crea; aquí solo por si la base es anterior)
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos'
        AND COLUMN_NAME = 'edad_minima') = 0,
    'ALTER TABLE cursos ADD COLUMN edad_minima TINYINT UNSIGNED NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos'
        AND COLUMN_NAME = 'edad_maxima') = 0,
    'ALTER TABLE cursos ADD COLUMN edad_maxima TINYINT UNSIGNED NULL AFTER edad_minima',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- b.2) es_curso_agrupacion: Conjunto Instrumental y Coro se cursan en
--      agrupaciones; no se ofrecen como cursos normales.
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos'
        AND COLUMN_NAME = 'es_curso_agrupacion') = 0,
    'ALTER TABLE cursos ADD COLUMN es_curso_agrupacion BOOLEAN NOT NULL DEFAULT FALSE',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- b.3) Marcar los códigos CI-% y CORO-%. "id_curso > 0" es para el modo
--      seguro de Workbench. Revise antes cuáles son:
--        SELECT id_curso, codigo, nombre FROM cursos
--         WHERE codigo LIKE 'CI-%' OR codigo LIKE 'CORO-%' ORDER BY codigo;
UPDATE cursos
   SET es_curso_agrupacion = TRUE
 WHERE id_curso > 0
   AND es_curso_agrupacion = FALSE
   AND (codigo LIKE 'CI-%' OR codigo LIKE 'CORO-%');

-- ---------------------------------------------------------------------
-- c) matriculas_detalles.orden_seleccion
-- ---------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND COLUMN_NAME = 'orden_seleccion') = 0,
    'ALTER TABLE matriculas_detalles ADD COLUMN orden_seleccion SMALLINT UNSIGNED NULL AFTER id_curso',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- c.1) Rellenar las agrupaciones ya matriculadas: 1, 2, 3... por matrícula,
--      en el orden en que se guardaron (id_detalle_matricula).
UPDATE matriculas_detalles md
    JOIN (
        SELECT d.id_detalle_matricula,
               ROW_NUMBER() OVER (PARTITION BY d.id_matricula ORDER BY d.id_detalle_matricula) AS orden
          FROM matriculas_detalles d
          JOIN grupos g ON g.id_grupo = d.id_grupo
         WHERE g.tipo_grupo = 'AGRUPACION'
    ) o ON o.id_detalle_matricula = md.id_detalle_matricula
   SET md.orden_seleccion = o.orden
 WHERE md.id_detalle_matricula > 0
   AND md.orden_seleccion IS NULL;

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
-- 1. Deben salir solo_inclusion, edad_minima y edad_maxima:
SHOW COLUMNS FROM agrupaciones WHERE Field IN ('solo_inclusion', 'edad_minima', 'edad_maxima');

-- 2. Deben salir edad_minima, edad_maxima y es_curso_agrupacion:
SHOW COLUMNS FROM cursos WHERE Field IN ('edad_minima', 'edad_maxima', 'es_curso_agrupacion');

-- 3. Cursos marcados como de agrupación (CI-% y CORO-%):
SELECT codigo, nombre FROM cursos WHERE es_curso_agrupacion = TRUE ORDER BY codigo;

-- 4. Debe salir 0: agrupaciones matriculadas sin orden de selección.
SELECT COUNT(*) AS agrupaciones_sin_orden
  FROM matriculas_detalles md
  JOIN grupos g ON g.id_grupo = md.id_grupo
 WHERE g.tipo_grupo = 'AGRUPACION'
   AND md.orden_seleccion IS NULL;
