-- =====================================================================
--  SIGAC - Cambio 2026-11-25: cursos "a coordinar en Dirección"
--  Motor: MySQL 8.0
-- =====================================================================
--  Algunos cursos (por ejemplo Música de Cámara I a VI) no tienen un
--  horario ni un profesor fijos: el estudiante los matricula como
--  cualquier curso y después la Dirección Académica, estudiante por
--  estudiante, le asigna (o no) un profesor y anota cómo lo cumplirá.
--
--  La decisión es POR CURSO: solo los cursos con a_coordinar = TRUE
--  pueden tener grupos sin horario y sin profesor. Los demás cursos,
--  los espacios de instrumento y las agrupaciones NO cambian.
--
--  ORDEN
--  Se ejecuta DESPUÉS de sigac_db.sql y de los cambios anteriores de esta
--  carpeta, y ANTES de desplegar el backend con cursos a coordinar. El
--  backend actual sigue funcionando con este cambio (las columnas nuevas
--  tienen valor por defecto o aceptan NULL).
--
--  CÓMO CORRERLO EN WORKBENCH
--  1. Saque un respaldo de la base (sobre todo en Railway).
--  2. Seleccione la base correcta: doble clic en ella en el panel
--     "Schemas" (o ejecute USE sigac_db; / USE railway;).
--  3. File > Open SQL Script > este archivo.
--  4. Ejecute TODO con el rayo sin cursor (Ctrl + Shift + Enter).
--  5. Revise el resultado "comprobacion" del paso 0 y las consultas de
--     verificación del final (cada una abre una pestaña de resultados).
--
--  ALTER TABLE NO SE DESHACE CON ROLLBACK: en MySQL cada ALTER TABLE hace
--  commit implícito (automático), aunque haya un START TRANSACTION. Por
--  eso el script no usa transacciones: cada paso revisa primero si ya está
--  hecho y se puede correr varias veces sin error ('DO 0' = no hacer
--  nada; MySQL 8 no tiene "ADD COLUMN IF NOT EXISTS").
--
--  Qué hace:
--    a) cursos.a_coordinar y marca MC-I a MC-VI.
--    b) matriculas_detalles.id_profesor_asignado (FK a profesores, con
--       índice) y matriculas_detalles.notas_coordinacion.
--
--  TRIGGERS (revisados, no cambian)
--  - trg_matriculas_detalles_bi_validar_periodo: solo usa id_matricula e
--    id_grupo.
--  - trg_matriculas_detalles_bi_validar_requisitos (versión de
--    2026-11-24_espacios_varios_instrumentos.sql): usa id_matricula,
--    id_grupo, id_curso e id_instrumento. No lee horarios_grupos ni
--    grupos.id_profesor, así que un grupo sin horario o sin profesor se
--    matricula igual, con sus prerrequisitos y correquisitos de siempre.
--  - Ninguna regla CHECK de grupos exige id_profesor ni horarios.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. Comprobación: la base debe tener las tablas de matrícula
-- ---------------------------------------------------------------------
SELECT IF(
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME IN ('cursos', 'matriculas_detalles', 'profesores')) = 3,
    'Correcto: la base tiene cursos, matriculas_detalles y profesores.',
    'DETÉNGASE: la base no es la correcta (faltan tablas).'
) AS comprobacion;

-- ---------------------------------------------------------------------
-- a) cursos.a_coordinar
-- ---------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos'
        AND COLUMN_NAME = 'a_coordinar') = 0,
    'ALTER TABLE cursos ADD COLUMN a_coordinar BOOLEAN NOT NULL DEFAULT FALSE
         COMMENT ''Horario y profesor se coordinan en Dirección''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- a.1) Música de Cámara I a VI. "id_curso > 0" es para el modo seguro de
--      Workbench. Revise antes los códigos reales:
--        SELECT id_curso, codigo, nombre FROM cursos WHERE codigo LIKE 'MC-%';
UPDATE cursos
   SET a_coordinar = TRUE
 WHERE id_curso > 0
   AND a_coordinar = FALSE
   AND codigo IN ('MC-I', 'MC-II', 'MC-III', 'MC-IV', 'MC-V', 'MC-VI');

-- ---------------------------------------------------------------------
-- b) matriculas_detalles
-- ---------------------------------------------------------------------

-- b.1) Profesor que Dirección le asigna a ESE estudiante en ese curso.
--      NULL = sin asignar. Mismo tipo que grupos.id_profesor.
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND COLUMN_NAME = 'id_profesor_asignado') = 0,
    'ALTER TABLE matriculas_detalles ADD COLUMN id_profesor_asignado INT UNSIGNED NULL
         COMMENT ''Profesor que Dirección asigna a este estudiante (cursos a coordinar)''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND INDEX_NAME = 'idx_matriculas_detalles_profesor_asignado') = 0,
    'ALTER TABLE matriculas_detalles ADD INDEX idx_matriculas_detalles_profesor_asignado (id_profesor_asignado)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND CONSTRAINT_NAME = 'fk_matriculas_detalles_profesor_asignado'
        AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE matriculas_detalles ADD CONSTRAINT fk_matriculas_detalles_profesor_asignado
         FOREIGN KEY (id_profesor_asignado) REFERENCES profesores (id_profesor) ON DELETE SET NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- b.2) Cómo lo cumplirá: en qué agrupación, a quién acompaña, con
--      quiénes, acuerdos de horario.
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND COLUMN_NAME = 'notas_coordinacion') = 0,
    'ALTER TABLE matriculas_detalles ADD COLUMN notas_coordinacion VARCHAR(500) NULL
         COMMENT ''Cómo cumplirá el curso a coordinar (agrupación, acompañamiento, compañeros, horario)''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
-- 1. Deben salir los 6 cursos de Música de Cámara (si existen en la base):
SELECT id_curso, codigo, nombre, activo, a_coordinar
  FROM cursos
 WHERE a_coordinar = TRUE
 ORDER BY codigo;

-- 2. Columnas nuevas (tipo, si acepta NULL, valor por defecto y comentario):
SELECT TABLE_NAME AS tabla, COLUMN_NAME AS columna, COLUMN_TYPE AS tipo,
       IS_NULLABLE AS acepta_null, COLUMN_DEFAULT AS por_defecto, COLUMN_COMMENT AS comentario
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'cursos' AND COLUMN_NAME = 'a_coordinar')
     OR (TABLE_NAME = 'matriculas_detalles'
         AND COLUMN_NAME IN ('id_profesor_asignado', 'notas_coordinacion')))
 ORDER BY TABLE_NAME, COLUMN_NAME;

-- 3. Debe salir la llave foránea con ON DELETE SET NULL:
SELECT CONSTRAINT_NAME AS llave, REFERENCED_TABLE_NAME AS tabla_referida, DELETE_RULE AS al_borrar
  FROM information_schema.REFERENTIAL_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
   AND CONSTRAINT_NAME = 'fk_matriculas_detalles_profesor_asignado';
