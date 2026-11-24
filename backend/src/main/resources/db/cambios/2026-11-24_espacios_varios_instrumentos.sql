-- =====================================================================
--  SIGAC - Cambio 2026-11-24: espacios de "cualquier instrumento del
--  profesor"
--  Motor: MySQL 8.0.19 o superior (usa CHECK y ALTER TABLE ... DROP CHECK)
-- =====================================================================
--  Algunos profesores dan dos instrumentos (por ejemplo Violín y Viola) y
--  cada hora suya la puede usar un estudiante de cualquiera de los dos.
--
--  ORDEN: se ejecuta DESPUÉS de 2026-11-19_espacios_instrumento.sql y
--  ANTES de desplegar el backend que crea espacios sin instrumento.
--  El backend actual sigue funcionando con este cambio.
--
--  CÓMO CORRERLO EN WORKBENCH
--  1. Seleccione la base correcta (doble clic en ella, o USE sigac_db;).
--  2. Ejecute todo con el rayo sin cursor (Ctrl + Shift + Enter).
--  3. Revise la verificación del final.
--
--  START TRANSACTION NO APLICA: en MySQL cada ALTER TABLE (y cada CREATE
--  o DROP TRIGGER) hace commit automático y no se puede deshacer con
--  ROLLBACK. Por eso cada paso revisa primero si ya está hecho y el
--  script se puede correr varias veces sin error ('DO 0' = no hacer nada).
--
--  Qué hace:
--    a) grupos: ck_grupos_tipo_datos deja que un ESPACIO_INSTRUMENTO
--       tenga id_instrumento NULL = "cualquier instrumento de la
--       especialidad del profesor". Se mantiene id_curso NULL y cupo 1.
--    b) matriculas_detalles.id_instrumento: el instrumento que el
--       estudiante cursa en ese espacio (con llave foránea a
--       instrumentos), y se rellena en las matrículas existentes.
--    c) El trigger de requisitos lee el instrumento del detalle.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. Comprobación: el cambio 2026-11-19 debe estar aplicado
-- ---------------------------------------------------------------------
SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND COLUMN_NAME = 'id_instrumento') = 1,
    'Correcto: la base tiene los espacios de instrumento.',
    'DETÉNGASE: falta 2026-11-19_espacios_instrumento.sql o la base no es la correcta.'
) AS comprobacion;

-- ---------------------------------------------------------------------
-- a) grupos: un espacio puede no tener instrumento
-- ---------------------------------------------------------------------

-- a.1) Quitar la regla anterior (la que exigía id_instrumento). Se
--      reconoce porque menciona id_instrumento; la nueva no lo menciona.
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND CONSTRAINT_NAME = 'ck_grupos_tipo_datos'
        AND CHECK_CLAUSE LIKE '%id_instrumento%') > 0,
    'ALTER TABLE grupos DROP CHECK ck_grupos_tipo_datos',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- a.2) Regla nueva
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND CONSTRAINT_NAME = 'ck_grupos_tipo_datos') = 0,
    'ALTER TABLE grupos ADD CONSTRAINT ck_grupos_tipo_datos CHECK (
        (tipo_grupo = ''CURSO'' AND id_curso IS NOT NULL)
     OR (tipo_grupo = ''ESPACIO_INSTRUMENTO'' AND id_curso IS NULL AND cupo_maximo = 1))',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- b) matriculas_detalles.id_instrumento
-- ---------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND COLUMN_NAME = 'id_instrumento') = 0,
    'ALTER TABLE matriculas_detalles ADD COLUMN id_instrumento SMALLINT UNSIGNED NULL AFTER id_curso',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND INDEX_NAME = 'idx_matriculas_detalles_instrumento') = 0,
    'ALTER TABLE matriculas_detalles ADD INDEX idx_matriculas_detalles_instrumento (id_instrumento)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND CONSTRAINT_NAME = 'fk_matriculas_detalles_instrumentos'
        AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE matriculas_detalles ADD CONSTRAINT fk_matriculas_detalles_instrumentos FOREIGN KEY (id_instrumento) REFERENCES instrumentos (id_instrumento)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- b.1) Rellenar las matrículas existentes en espacios con el instrumento
--      del espacio (hasta hoy todos los espacios tienen uno).
--      "id_detalle_matricula > 0" es para el modo seguro de Workbench.
UPDATE matriculas_detalles md
    JOIN grupos g ON g.id_grupo = md.id_grupo
   SET md.id_instrumento = g.id_instrumento
 WHERE md.id_detalle_matricula > 0
   AND g.tipo_grupo = 'ESPACIO_INSTRUMENTO'
   AND g.id_instrumento IS NOT NULL
   AND md.id_instrumento IS NULL;

-- ---------------------------------------------------------------------
-- c) Trigger de requisitos de la matrícula
--    Igual al de 2026-11-19, con dos cambios:
--    - El instrumento es COALESCE(md.id_instrumento, g.id_instrumento),
--      también en lo ya aprobado: en un espacio de varios instrumentos
--      el instrumento sale del detalle.
--    - Un espacio sin instrumento exige que el detalle lo indique.
--    Se conserva @sigac_correquisitos_validados.
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_matriculas_detalles_bi_validar_requisitos;

DELIMITER $$

CREATE TRIGGER trg_matriculas_detalles_bi_validar_requisitos BEFORE INSERT ON matriculas_detalles FOR EACH ROW
BEGIN
    DECLARE v_estudiante  INT UNSIGNED DEFAULT NULL;
    DECLARE v_plan        INT UNSIGNED DEFAULT NULL;
    DECLARE v_curso       INT UNSIGNED DEFAULT NULL;
    DECLARE v_tipo        VARCHAR(30)  DEFAULT NULL;
    DECLARE v_instrumento SMALLINT UNSIGNED DEFAULT NULL;

    SELECT a.id_estudiante, m.id_plan_estudio
    INTO v_estudiante, v_plan
    FROM matriculas AS m
             INNER JOIN autorizaciones_matricula AS a
                        ON a.id_autorizacion = m.id_autorizacion
    WHERE m.id_matricula = NEW.id_matricula
    LIMIT 1;

    SELECT COALESCE(NEW.id_curso, g.id_curso), g.tipo_grupo, COALESCE(NEW.id_instrumento, g.id_instrumento)
    INTO v_curso, v_tipo, v_instrumento
    FROM grupos AS g
    WHERE g.id_grupo = NEW.id_grupo
    LIMIT 1;

    IF v_tipo = 'ESPACIO_INSTRUMENTO' AND NEW.id_curso IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Un espacio de instrumento debe indicar el nivel (curso) que se matricula';
    END IF;

    IF v_tipo = 'ESPACIO_INSTRUMENTO' AND v_instrumento IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Un espacio de varios instrumentos debe indicar el instrumento que se matricula';
    END IF;

    -- Prerrequisitos: el curso requerido debe estar aprobado (calificación
    -- APROBADA con la nota mínima, si aplica) o reconocido.
    IF EXISTS (
        SELECT 1
        FROM requisitos_cursos AS rc
                 INNER JOIN tipos_requisitos_cursos AS trc
                            ON trc.id_tipo_requisito = rc.id_tipo_requisito
                 INNER JOIN cursos AS creq
                            ON creq.id_curso = rc.id_curso_requerido
        WHERE rc.id_plan_estudio = v_plan
          AND rc.id_curso = v_curso
          AND rc.activo = TRUE
          AND trc.codigo = 'PRERREQUISITO'
          AND NOT EXISTS (
            SELECT 1
            FROM matriculas_detalles AS md2
                     INNER JOIN matriculas AS m2
                                ON m2.id_matricula = md2.id_matricula
                     INNER JOIN autorizaciones_matricula AS a2
                                ON a2.id_autorizacion = m2.id_autorizacion
                     INNER JOIN grupos AS g2
                                ON g2.id_grupo = md2.id_grupo
                     INNER JOIN calificaciones AS c2
                                ON c2.id_detalle_matricula = md2.id_detalle_matricula
                     INNER JOIN estados_academicos AS ea2
                                ON ea2.id_estado_academico = c2.id_estado_academico
            WHERE a2.id_estudiante = v_estudiante
              AND COALESCE(md2.id_curso, g2.id_curso) = rc.id_curso_requerido
              AND ea2.codigo = 'APROBADO'
              AND (rc.nota_minima IS NULL OR c2.nota_final >= rc.nota_minima)
              AND (v_instrumento IS NULL OR creq.es_curso_instrumento = FALSE
                   OR COALESCE(md2.id_instrumento, g2.id_instrumento) = v_instrumento)
          )
          AND NOT EXISTS (
            SELECT 1
            FROM cursos_reconocidos AS cr
            WHERE cr.id_estudiante = v_estudiante
              AND cr.id_curso = rc.id_curso_requerido
              AND (v_instrumento IS NULL OR creq.es_curso_instrumento = FALSE
                   OR cr.id_instrumento = v_instrumento)
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El estudiante no cumple los prerrequisitos del curso';
    END IF;

    -- Correquisitos: aprobado o reconocido antes, o matriculado en la
    -- misma matrícula. Se omite si el backend ya los validó con la
    -- selección completa (@sigac_correquisitos_validados = 1).
    IF @sigac_correquisitos_validados IS NULL AND EXISTS (
        SELECT 1
        FROM requisitos_cursos AS rc
                 INNER JOIN tipos_requisitos_cursos AS trc
                            ON trc.id_tipo_requisito = rc.id_tipo_requisito
                 INNER JOIN cursos AS creq
                            ON creq.id_curso = rc.id_curso_requerido
        WHERE rc.id_plan_estudio = v_plan
          AND rc.id_curso = v_curso
          AND rc.activo = TRUE
          AND trc.codigo = 'CORREQUISITO'
          AND NOT EXISTS (
            SELECT 1
            FROM matriculas_detalles AS md2
                     INNER JOIN matriculas AS m2
                                ON m2.id_matricula = md2.id_matricula
                     INNER JOIN autorizaciones_matricula AS a2
                                ON a2.id_autorizacion = m2.id_autorizacion
                     INNER JOIN grupos AS g2
                                ON g2.id_grupo = md2.id_grupo
                     INNER JOIN calificaciones AS c2
                                ON c2.id_detalle_matricula = md2.id_detalle_matricula
                     INNER JOIN estados_academicos AS ea2
                                ON ea2.id_estado_academico = c2.id_estado_academico
            WHERE a2.id_estudiante = v_estudiante
              AND COALESCE(md2.id_curso, g2.id_curso) = rc.id_curso_requerido
              AND ea2.codigo = 'APROBADO'
              AND (rc.nota_minima IS NULL OR c2.nota_final >= rc.nota_minima)
              AND (v_instrumento IS NULL OR creq.es_curso_instrumento = FALSE
                   OR COALESCE(md2.id_instrumento, g2.id_instrumento) = v_instrumento)
          )
          AND NOT EXISTS (
            SELECT 1
            FROM cursos_reconocidos AS cr
            WHERE cr.id_estudiante = v_estudiante
              AND cr.id_curso = rc.id_curso_requerido
              AND (v_instrumento IS NULL OR creq.es_curso_instrumento = FALSE
                   OR cr.id_instrumento = v_instrumento)
          )
          AND NOT EXISTS (
            SELECT 1
            FROM matriculas_detalles AS md3
                     INNER JOIN grupos AS g3
                                ON g3.id_grupo = md3.id_grupo
            WHERE md3.id_matricula = NEW.id_matricula
              AND COALESCE(md3.id_curso, g3.id_curso) = rc.id_curso_requerido
              AND md3.retirado = FALSE
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El estudiante no cumple los correquisitos del curso';
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- Verificación
-- ---------------------------------------------------------------------
-- 1. La regla nueva NO debe mencionar id_instrumento:
SELECT CONSTRAINT_NAME AS regla, CHECK_CLAUSE AS condicion
  FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = DATABASE()
   AND CONSTRAINT_NAME = 'ck_grupos_tipo_datos';

-- 2. Debe salir la columna id_instrumento (smallint unsigned, YES):
SHOW COLUMNS FROM matriculas_detalles LIKE 'id_instrumento';

-- 3. Debe salir 0: matrículas en espacios sin instrumento.
SELECT COUNT(*) AS detalles_de_espacios_sin_instrumento
  FROM matriculas_detalles md
  JOIN grupos g ON g.id_grupo = md.id_grupo
 WHERE g.tipo_grupo = 'ESPACIO_INSTRUMENTO'
   AND md.id_instrumento IS NULL;

-- 4. Debe salir 1 fila con el trigger:
SHOW TRIGGERS WHERE `Trigger` = 'trg_matriculas_detalles_bi_validar_requisitos';
