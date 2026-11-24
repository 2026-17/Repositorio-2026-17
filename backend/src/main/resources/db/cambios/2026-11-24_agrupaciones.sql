-- =====================================================================
--  SIGAC - Cambio 2026-11-24: agrupaciones (orquestas, bandas y coros)
--  Motor: MySQL 8.0.19 o superior (usa CHECK y ALTER TABLE ... DROP CHECK)
-- =====================================================================
--  Una agrupación es un grupo con horario fijo (Orquesta, Banda, Coro de
--  niños...) donde tocan juntos estudiantes de distintos niveles:
--    - INSTRUMENTAL: cuenta para Conjunto Instrumental I a XII.
--    - CORAL: cuenta para Coro I a XIV.
--  El avance es POR AGRUPACIÓN. El nivel de cada estudiante lo calcula el
--  backend y queda en matriculas_detalles.id_curso (igual que en los
--  espacios de instrumento); el grupo no tiene curso.
--
--  ORDEN
--  Se ejecuta DESPUÉS de:
--    1. sigac_db.sql
--    2. db/cambios/2026-11-19_espacios_instrumento.sql
--    3. db/cambios/2026-11-24_espacios_varios_instrumentos.sql (si se va a
--       aplicar; si se corre después de este, hay que volver a correr este
--       script, porque aquel reemplaza la regla ck_grupos_tipo_datos).
--  y ANTES de desplegar el backend con agrupaciones. El backend actual
--  sigue funcionando con este cambio.
--
--  CÓMO CORRERLO EN WORKBENCH
--  1. Abra la conexión y seleccione la base correcta: doble clic en ella
--     en el panel "Schemas" (o ejecute USE sigac_db; / USE railway;).
--  2. Abra este archivo (File > Open SQL Script).
--  3. Ejecute TODO el script con el rayo sin cursor (Ctrl + Shift + Enter).
--     No use el rayo con cursor: solo ejecuta una sentencia.
--  4. Revise el resultado "comprobacion" del paso 0 y las consultas de
--     verificación del final (cada una abre una pestaña de resultados).
--
--  ALTER TABLE NO SE DESHACE CON ROLLBACK: en MySQL cada ALTER TABLE y
--  cada CREATE TABLE hace commit automático. Por eso el script no usa
--  START TRANSACTION: cada paso revisa primero si ya está hecho y se
--  puede correr varias veces sin error (MySQL 8 no tiene
--  "ADD COLUMN IF NOT EXISTS"; los ALTER se arman como texto y se
--  ejecutan solo si hace falta; 'DO 0' = no hacer nada).
--  Antes de correrlo en Railway, saque un respaldo de la base.
--
--  Qué hace:
--    a) tabla agrupaciones (catálogo)
--    b) tabla agrupaciones_instrumentos (instrumentos permitidos; sin
--       filas = acepta cualquier instrumento)
--    c) grupos: tipo 'AGRUPACION', columna id_agrupacion, reglas e índice
--    d) tabla grupos_profesores (varios profesores, uno responsable)
--    e) cursos_reconocidos.id_agrupacion y su llave única
--  El trigger de requisitos de la matrícula NO cambia: ya toma el curso
--  de matriculas_detalles.id_curso cuando el detalle lo trae.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. Comprobación: los espacios de instrumento deben estar aplicados
-- ---------------------------------------------------------------------
SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND COLUMN_NAME = 'tipo_grupo') = 1
    AND
    (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos_reconocidos') = 1,
    'Correcto: la base tiene los espacios de instrumento.',
    'DETÉNGASE: falta 2026-11-19_espacios_instrumento.sql o la base no es la correcta.'
) AS comprobacion;

-- ---------------------------------------------------------------------
-- a) agrupaciones: catálogo (Orquesta, Banda, Coro de niños...)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agrupaciones (
    id_agrupacion                  INT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo                         VARCHAR(20)  NOT NULL,
    nombre                         VARCHAR(100) NOT NULL,
    tipo                           VARCHAR(20)  NOT NULL,
    descripcion                    VARCHAR(500) NULL,
    activa                         BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    id_usuario_ultima_modificacion INT UNSIGNED NULL,
    PRIMARY KEY (id_agrupacion),
    CONSTRAINT uq_agrupaciones_codigo UNIQUE (codigo),
    CONSTRAINT uq_agrupaciones_nombre UNIQUE (nombre),
    CONSTRAINT fk_agrupaciones_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
        REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT ck_agrupaciones_tipo CHECK (tipo IN ('INSTRUMENTAL', 'CORAL'))
);

-- ---------------------------------------------------------------------
-- b) agrupaciones_instrumentos: instrumentos permitidos
--    Si una agrupación no tiene filas aquí, acepta cualquier instrumento
--    (por ejemplo, un coro).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agrupaciones_instrumentos (
    id_agrupacion  INT UNSIGNED      NOT NULL,
    id_instrumento SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (id_agrupacion, id_instrumento),
    INDEX idx_agrupaciones_instrumentos_instrumento (id_instrumento),
    CONSTRAINT fk_agrupaciones_instrumentos_agrupaciones FOREIGN KEY (id_agrupacion)
        REFERENCES agrupaciones (id_agrupacion) ON DELETE CASCADE,
    CONSTRAINT fk_agrupaciones_instrumentos_instrumentos FOREIGN KEY (id_instrumento)
        REFERENCES instrumentos (id_instrumento)
);

-- ---------------------------------------------------------------------
-- c) grupos
-- ---------------------------------------------------------------------

-- c.1) id_agrupacion (solo en los grupos de tipo AGRUPACION)
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND COLUMN_NAME = 'id_agrupacion') = 0,
    'ALTER TABLE grupos ADD COLUMN id_agrupacion INT UNSIGNED NULL AFTER id_instrumento',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Sin ON DELETE: una agrupación con grupos no se puede borrar (se desactiva).
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND CONSTRAINT_NAME = 'fk_grupos_agrupaciones'
        AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE grupos ADD CONSTRAINT fk_grupos_agrupaciones FOREIGN KEY (id_agrupacion) REFERENCES agrupaciones (id_agrupacion)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- c.2) Índice para listar las agrupaciones de un período
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND INDEX_NAME = 'idx_grupos_periodo_agrupacion') = 0,
    'ALTER TABLE grupos ADD INDEX idx_grupos_periodo_agrupacion (id_periodo, id_agrupacion)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- c.3) ck_grupos_tipo acepta 'AGRUPACION'.
--      Se quita la regla vieja solo si todavía no menciona AGRUPACION.
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND CONSTRAINT_NAME = 'ck_grupos_tipo'
        AND CHECK_CLAUSE NOT LIKE '%AGRUPACION%') > 0,
    'ALTER TABLE grupos DROP CHECK ck_grupos_tipo',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND CONSTRAINT_NAME = 'ck_grupos_tipo') = 0,
    'ALTER TABLE grupos ADD CONSTRAINT ck_grupos_tipo CHECK (tipo_grupo IN (''CURSO'', ''ESPACIO_INSTRUMENTO'', ''AGRUPACION''))',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- c.4) ck_grupos_tipo_datos: se le agrega "OR tipo_grupo = 'AGRUPACION'".
--      La parte de CURSO y ESPACIO_INSTRUMENTO se conserva tal como está
--      en la base (la de 2026-11-19 o la de espacios de varios
--      instrumentos), por eso se lee su texto actual y se reutiliza.
--      OJO: MySQL 8 devuelve CHECK_CLAUSE con las comillas escapadas
--      (_utf8mb4\'CURSO\'); si se usa tal cual, el ALTER da error 1064.
--      Por eso se le quitan las barras invertidas (CHAR(92) = '\').
SET @clausula := (SELECT REPLACE(CHECK_CLAUSE, CHAR(92), '')
                    FROM information_schema.CHECK_CONSTRAINTS
                   WHERE CONSTRAINT_SCHEMA = DATABASE()
                     AND CONSTRAINT_NAME = 'ck_grupos_tipo_datos'
                   LIMIT 1);

SET @sql := IF(
    @clausula IS NOT NULL AND @clausula NOT LIKE '%AGRUPACION%',
    'ALTER TABLE grupos DROP CHECK ck_grupos_tipo_datos',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    @clausula IS NOT NULL AND @clausula NOT LIKE '%AGRUPACION%',
    CONCAT('ALTER TABLE grupos ADD CONSTRAINT ck_grupos_tipo_datos CHECK ((',
           @clausula, ') OR (tipo_grupo = ''AGRUPACION''))'),
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Si una corrida anterior se interrumpió entre el DROP y el ADD (por
-- ejemplo, con el error 1064 de la versión anterior de este script), la
-- regla no existe: se crea completa. La parte de los espacios depende de
-- la base:
--   - con 2026-11-24_espacios_varios_instrumentos.sql aplicado (existe
--     matriculas_detalles.id_instrumento): el espacio puede no tener
--     instrumento;
--   - si no: la regla de 2026-11-19 (el espacio exige id_instrumento).
SET @varios := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'matriculas_detalles'
                   AND COLUMN_NAME = 'id_instrumento');

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND CONSTRAINT_NAME = 'ck_grupos_tipo_datos') = 0,
    IF(@varios > 0,
       'ALTER TABLE grupos ADD CONSTRAINT ck_grupos_tipo_datos CHECK (
           (tipo_grupo = ''CURSO'' AND id_curso IS NOT NULL)
        OR (tipo_grupo = ''ESPACIO_INSTRUMENTO'' AND id_curso IS NULL AND cupo_maximo = 1)
        OR (tipo_grupo = ''AGRUPACION''))',
       'ALTER TABLE grupos ADD CONSTRAINT ck_grupos_tipo_datos CHECK (
           (tipo_grupo = ''CURSO'' AND id_curso IS NOT NULL)
        OR (tipo_grupo = ''ESPACIO_INSTRUMENTO'' AND id_curso IS NULL
            AND id_instrumento IS NOT NULL AND cupo_maximo = 1)
        OR (tipo_grupo = ''AGRUPACION''))'),
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- c.5) Reglas propias de las agrupaciones, en una regla aparte para no
--      mezclarlas con las de los espacios:
--      - AGRUPACION: id_agrupacion obligatorio, sin curso, sin
--        instrumento y con cupo mayor que 0.
--      - Los demás tipos no llevan id_agrupacion.
--      id_profesor NO va en el CHECK (fk_grupos_profesores tiene ON DELETE
--      SET NULL, error 3823); que se publique con responsable lo valida
--      el backend (AgrupacionService).
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND CONSTRAINT_NAME = 'ck_grupos_agrupacion_datos') = 0,
    'ALTER TABLE grupos ADD CONSTRAINT ck_grupos_agrupacion_datos CHECK (
        (tipo_grupo = ''AGRUPACION'' AND id_agrupacion IS NOT NULL AND id_curso IS NULL
         AND id_instrumento IS NULL AND cupo_maximo > 0)
     OR (tipo_grupo <> ''AGRUPACION'' AND id_agrupacion IS NULL))',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- d) grupos_profesores: profesores de un grupo de agrupación
--    - Un solo responsable por grupo (lo valida el backend).
--    - grupos.id_profesor se mantiene y es igual al responsable, para no
--      romper las pantallas actuales (horario del profesor, asignación,
--      choques de horario).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grupos_profesores (
    id_grupo       INT UNSIGNED NOT NULL,
    id_profesor    INT UNSIGNED NOT NULL,
    es_responsable BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_grupo, id_profesor),
    INDEX idx_grupos_profesores_profesor (id_profesor),
    CONSTRAINT fk_grupos_profesores_grupos FOREIGN KEY (id_grupo)
        REFERENCES grupos (id_grupo) ON DELETE CASCADE,
    CONSTRAINT fk_grupos_profesores_profesores FOREIGN KEY (id_profesor)
        REFERENCES profesores (id_profesor) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- e) cursos_reconocidos.id_agrupacion
--    Lo reconocido de la carga inicial puede venir con la agrupación en
--    que se aprobó (Orquesta IV) o sin ella (NULL = solo el nivel de la
--    familia, que sirve de punto de partida en cualquier agrupación).
--    La llave única pasa a incluir la agrupación, para que el mismo curso
--    pueda estar reconocido en dos agrupaciones distintas. Igual que con
--    el instrumento, se usa una columna calculada (COALESCE(..., 0))
--    porque en un UNIQUE dos NULL cuentan como distintos.
-- ---------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos_reconocidos'
        AND COLUMN_NAME = 'id_agrupacion') = 0,
    'ALTER TABLE cursos_reconocidos ADD COLUMN id_agrupacion INT UNSIGNED NULL AFTER id_instrumento',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos_reconocidos'
        AND COLUMN_NAME = 'id_agrupacion_clave') = 0,
    'ALTER TABLE cursos_reconocidos ADD COLUMN id_agrupacion_clave INT UNSIGNED AS (COALESCE(id_agrupacion, 0)) STORED AFTER id_agrupacion',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos_reconocidos'
        AND CONSTRAINT_NAME = 'fk_cursos_reconocidos_agrupaciones'
        AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE cursos_reconocidos ADD CONSTRAINT fk_cursos_reconocidos_agrupaciones FOREIGN KEY (id_agrupacion) REFERENCES agrupaciones (id_agrupacion)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos_reconocidos'
        AND INDEX_NAME = 'idx_cursos_reconocidos_agrupacion') = 0,
    'ALTER TABLE cursos_reconocidos ADD INDEX idx_cursos_reconocidos_agrupacion (id_estudiante, id_agrupacion)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- e.1) Llave única nueva primero y luego se quita la vieja (así la tabla
--      nunca queda sin llave única).
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos_reconocidos'
        AND INDEX_NAME = 'uq_cursos_reconocidos_agrupacion') = 0,
    'ALTER TABLE cursos_reconocidos ADD CONSTRAINT uq_cursos_reconocidos_agrupacion UNIQUE (id_estudiante, id_curso, id_instrumento_clave, id_agrupacion_clave)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos_reconocidos'
        AND INDEX_NAME = 'uq_cursos_reconocidos') > 0,
    'ALTER TABLE cursos_reconocidos DROP INDEX uq_cursos_reconocidos',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- Datos de ejemplo (NO se ejecutan solos). Revise primero los códigos
-- reales de los cursos de cada familia:
--
--   SELECT id_curso, codigo, nombre, activo FROM cursos
--    WHERE codigo LIKE 'CI-%' OR codigo LIKE 'CORO-%'
--    ORDER BY codigo;
--
-- El backend busca los niveles por código: CI-I ... CI-XII y
-- CORO-1, CORO-II ... CORO-XIV (ver FamiliaAgrupacion.java).
--
--   INSERT INTO agrupaciones (codigo, nombre, tipo) VALUES
--     ('ORQ', 'Orquesta', 'INSTRUMENTAL'),
--     ('BANDA', 'Banda', 'INSTRUMENTAL'),
--     ('CORO-NIN', 'Coro de niños', 'CORAL');
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
-- 1. Deben salir 3 tablas: agrupaciones, agrupaciones_instrumentos y
--    grupos_profesores.
SELECT TABLE_NAME AS tabla
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('agrupaciones', 'agrupaciones_instrumentos', 'grupos_profesores');

-- 2. Las tres reglas de grupos deben mencionar AGRUPACION:
SELECT CONSTRAINT_NAME AS regla, CHECK_CLAUSE AS condicion
  FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
   AND CONSTRAINT_NAME IN ('ck_grupos_tipo', 'ck_grupos_tipo_datos', 'ck_grupos_agrupacion_datos');

-- 3. Deben salir las columnas id_agrupacion (int unsigned, YES):
SHOW COLUMNS FROM grupos LIKE 'id_agrupacion';
SHOW COLUMNS FROM cursos_reconocidos LIKE 'id_agrupacion%';

-- 4. Debe salir solo uq_cursos_reconocidos_agrupacion (la vieja ya no):
SELECT DISTINCT INDEX_NAME AS llave_unica
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'cursos_reconocidos'
   AND NON_UNIQUE = 0
   AND INDEX_NAME <> 'PRIMARY';
