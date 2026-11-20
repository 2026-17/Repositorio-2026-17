-- =====================================================================
--  SIGAC - Cambio 2026-11-19: espacios de instrumento y avance por
--  instrumento
--  Motor: MySQL 8.0.16 o superior (usa CHECK)
-- =====================================================================
--  ORDEN: se ejecuta DESPUÉS de sigac_db.sql y de los cambios anteriores
--  de db/cambios/ (2026-11-17_tipo_estudiante_inclusion.sql) y de
--  migracion_correquisitos_mutuos.sql (este script reemplaza el trigger
--  de requisitos que dejó esa migración, conservando su marca
--  @sigac_correquisitos_validados).
--
--  Antes de ejecutarlo, seleccione la base correcta:
--      USE sigac_db;      -- local
--      USE railway;       -- Railway (o el nombre que tenga)
--
--  spring.jpa.hibernate.ddl-auto está en "none": este script es la
--  única forma de crear estas columnas y tablas.
--
--  Se puede ejecutar varias veces sin error: cada paso revisa primero
--  si ya está hecho (MySQL 8 no tiene "ADD COLUMN IF NOT EXISTS", por eso
--  los ALTER se arman como texto y se ejecutan solo si hace falta;
--  'DO 0' = no hacer nada).
--
--  Qué hace:
--    a) cursos.es_curso_instrumento
--    b) grupos: tipo_grupo, id_instrumento, id_curso pasa a aceptar NULL,
--       reglas (CHECK) e índice para buscar espacios
--    c) matriculas_detalles.id_curso (nivel que se cursa en un espacio)
--    d) estudiantes_instrumentos.es_principal = "instrumento del plan"
--    e) tabla cursos_reconocidos
--    f) trigger de requisitos de la matrícula, ahora por instrumento
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- a) cursos.es_curso_instrumento
--    Los cursos Instrumento I a XIV no se ofrecen en grupos normales:
--    se cursan en los espacios de instrumento de cada profesor.
-- ---------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'cursos'
        AND COLUMN_NAME = 'es_curso_instrumento') = 0,
    'ALTER TABLE cursos ADD COLUMN es_curso_instrumento BOOLEAN NOT NULL DEFAULT FALSE AFTER activo',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- a.1) Marcar Instrumento I a XIV. NO se ejecuta solo: revise primero
--      los datos reales con esta consulta:
--
--   SELECT c.id_curso, c.codigo, c.nombre, c.activo, mc.id_plan_estudio, mc.nivel
--     FROM cursos c
--     LEFT JOIN malla_curricular mc ON mc.id_curso = c.id_curso AND mc.activo = TRUE
--    WHERE c.nombre LIKE 'Instrumento%' OR c.codigo LIKE '%-INS-%'
--    ORDER BY mc.id_plan_estudio, mc.nivel;
--
--   Opción 1 (recomendada): por nombre exacto "Instrumento I" ... "Instrumento XIV".
--   UPDATE cursos
--      SET es_curso_instrumento = TRUE
--    WHERE id_curso > 0
--      AND nombre REGEXP '^Instrumento (I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII|XIII|XIV)$';
--
--   Opción 2: por código (si todos siguen el patrón XXX-INS-<romano>).
--   UPDATE cursos
--      SET es_curso_instrumento = TRUE
--    WHERE id_curso > 0
--      AND codigo REGEXP '-INS-(I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII|XIII|XIV)$';

-- ---------------------------------------------------------------------
-- b) grupos
-- ---------------------------------------------------------------------

-- b.1) tipo_grupo: CURSO (grupo normal) o ESPACIO_INSTRUMENTO
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND COLUMN_NAME = 'tipo_grupo') = 0,
    'ALTER TABLE grupos ADD COLUMN tipo_grupo VARCHAR(30) NOT NULL DEFAULT ''CURSO'' AFTER id_periodo',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- b.2) id_instrumento (obligatorio solo en los espacios)
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND COLUMN_NAME = 'id_instrumento') = 0,
    'ALTER TABLE grupos ADD COLUMN id_instrumento SMALLINT UNSIGNED NULL AFTER id_curso',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND CONSTRAINT_NAME = 'fk_grupos_instrumentos'
        AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE grupos ADD CONSTRAINT fk_grupos_instrumentos FOREIGN KEY (id_instrumento) REFERENCES instrumentos (id_instrumento)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- b.3) id_curso acepta NULL (un espacio no tiene curso: el nivel lo
--      trae el estudiante y queda en matriculas_detalles.id_curso)
SET @sql := IF(
    (SELECT IS_NULLABLE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND COLUMN_NAME = 'id_curso') = 'NO',
    'ALTER TABLE grupos MODIFY id_curso INT UNSIGNED NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- b.4) Reglas por tipo
--      Nota: id_profesor NO va en el CHECK porque fk_grupos_profesores
--      tiene ON DELETE SET NULL, y MySQL no permite CHECK sobre columnas
--      con acciones de llave foránea (error 3823). Que un espacio tenga
--      profesor lo valida el backend (EspacioInstrumentoService).
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND CONSTRAINT_NAME = 'ck_grupos_tipo') = 0,
    'ALTER TABLE grupos ADD CONSTRAINT ck_grupos_tipo CHECK (tipo_grupo IN (''CURSO'', ''ESPACIO_INSTRUMENTO''))',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND CONSTRAINT_NAME = 'ck_grupos_tipo_datos') = 0,
    'ALTER TABLE grupos ADD CONSTRAINT ck_grupos_tipo_datos CHECK (
        (tipo_grupo = ''CURSO'' AND id_curso IS NOT NULL)
     OR (tipo_grupo = ''ESPACIO_INSTRUMENTO'' AND id_curso IS NULL
         AND id_instrumento IS NOT NULL AND cupo_maximo = 1))',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- b.5) Índice para la agenda y la matrícula: espacios de un profesor e
--      instrumento en un período
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'grupos'
        AND INDEX_NAME = 'idx_grupos_espacios') = 0,
    'ALTER TABLE grupos ADD INDEX idx_grupos_espacios (id_periodo, id_profesor, id_instrumento)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- c) matriculas_detalles.id_curso
--    En un espacio de instrumento es el nivel que el estudiante cursa
--    (Instrumento I..XIV). En un grupo normal queda NULL y el curso es
--    el del grupo. El trigger de requisitos (punto f) exige que venga
--    cuando el grupo es un espacio.
-- ---------------------------------------------------------------------
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND COLUMN_NAME = 'id_curso') = 0,
    'ALTER TABLE matriculas_detalles ADD COLUMN id_curso INT UNSIGNED NULL AFTER id_grupo',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND INDEX_NAME = 'idx_matriculas_detalles_curso') = 0,
    'ALTER TABLE matriculas_detalles ADD INDEX idx_matriculas_detalles_curso (id_curso)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = DATABASE()
        AND TABLE_NAME = 'matriculas_detalles'
        AND CONSTRAINT_NAME = 'fk_matriculas_detalles_cursos'
        AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE matriculas_detalles ADD CONSTRAINT fk_matriculas_detalles_cursos FOREIGN KEY (id_curso) REFERENCES cursos (id_curso)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- d) estudiantes_instrumentos.es_principal ahora significa "instrumento
--    del plan" (el que cuenta para graduarse). Cada estudiante con
--    instrumentos vigentes debe tener exactamente uno.
-- ---------------------------------------------------------------------

-- d.1) Si tiene varios vigentes marcados, se conserva el más antiguo
UPDATE estudiantes_instrumentos ei
    JOIN (
        SELECT p.id_estudiante, MIN(p.id_estudiante_instrumento) AS id_conservar
          FROM estudiantes_instrumentos p
         WHERE p.fecha_fin IS NULL
           AND p.es_principal = TRUE
         GROUP BY p.id_estudiante
        HAVING COUNT(*) > 1
    ) d ON d.id_estudiante = ei.id_estudiante
   SET ei.es_principal = FALSE
 WHERE ei.id_estudiante_instrumento > 0
   AND ei.fecha_fin IS NULL
   AND ei.es_principal = TRUE
   AND ei.id_estudiante_instrumento <> d.id_conservar;

-- d.2) Quien no tiene ninguno: el instrumento vigente más antiguo
--      (menor fecha de inicio; si empatan, el de menor id)
UPDATE estudiantes_instrumentos ei
    JOIN (
        SELECT v.id_estudiante, MIN(v.id_estudiante_instrumento) AS id_elegido
          FROM estudiantes_instrumentos v
          JOIN (
                SELECT x.id_estudiante, MIN(x.fecha_inicio) AS primera
                  FROM estudiantes_instrumentos x
                 WHERE x.fecha_fin IS NULL
                 GROUP BY x.id_estudiante
               ) f ON f.id_estudiante = v.id_estudiante AND f.primera = v.fecha_inicio
         WHERE v.fecha_fin IS NULL
           AND NOT EXISTS (
                SELECT 1 FROM estudiantes_instrumentos p
                 WHERE p.id_estudiante = v.id_estudiante
                   AND p.fecha_fin IS NULL
                   AND p.es_principal = TRUE)
         GROUP BY v.id_estudiante
    ) e ON e.id_elegido = ei.id_estudiante_instrumento
   SET ei.es_principal = TRUE
 WHERE ei.id_estudiante_instrumento > 0;

-- ---------------------------------------------------------------------
-- e) cursos_reconocidos: avance que no viene de una calificación de
--    SIGAC (certificaciones anteriores o prueba de ubicación).
--    La llenará después la importación de las certificaciones 2025;
--    el backend la usa ya para el "nivel de ingreso".
--
--    En MySQL, un UNIQUE con NULL deja repetir filas (dos NULL son
--    distintos). Para que (estudiante, curso, instrumento) no se repita
--    tampoco en cursos que no son de instrumento, la llave única usa la
--    columna calculada id_instrumento_clave = COALESCE(id_instrumento, 0).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cursos_reconocidos (
    id_curso_reconocido INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    id_estudiante       INT UNSIGNED      NOT NULL,
    id_curso            INT UNSIGNED      NOT NULL,
    id_instrumento      SMALLINT UNSIGNED NULL,
    id_instrumento_clave SMALLINT UNSIGNED AS (COALESCE(id_instrumento, 0)) STORED,
    condicion           VARCHAR(20)       NOT NULL,
    origen              VARCHAR(20)       NOT NULL,
    fecha               DATE              NOT NULL,
    motivo              VARCHAR(500),
    id_usuario_registra INT UNSIGNED,
    creado_en           DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_curso_reconocido),
    CONSTRAINT uq_cursos_reconocidos UNIQUE (id_estudiante, id_curso, id_instrumento_clave),
    INDEX idx_cursos_reconocidos_instrumento (id_estudiante, id_instrumento),
    CONSTRAINT fk_cursos_reconocidos_estudiantes FOREIGN KEY (id_estudiante)
        REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
    CONSTRAINT fk_cursos_reconocidos_cursos FOREIGN KEY (id_curso)
        REFERENCES cursos (id_curso),
    CONSTRAINT fk_cursos_reconocidos_instrumentos FOREIGN KEY (id_instrumento)
        REFERENCES instrumentos (id_instrumento),
    CONSTRAINT fk_cursos_reconocidos_usuarios FOREIGN KEY (id_usuario_registra)
        REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT ck_cursos_reconocidos_condicion CHECK (condicion IN ('APROBADO', 'CONVALIDADO')),
    CONSTRAINT ck_cursos_reconocidos_origen    CHECK (origen IN ('CERTIFICACION', 'NIVEL_INGRESO'))
);

-- ---------------------------------------------------------------------
-- f) Trigger de requisitos de la matrícula
--    Cambios respecto de migracion_correquisitos_mutuos.sql:
--    - El curso del detalle es COALESCE(md.id_curso, g.id_curso): en un
--      espacio de instrumento sale del detalle.
--    - Un espacio sin id_curso se rechaza.
--    - Un curso aprobado también puede venir de cursos_reconocidos.
--    - Si el curso requerido es de instrumento y lo que se matricula es
--      un espacio, el requerido debe estar aprobado EN ESE instrumento
--      (aprobar Instrumento V en piano no habilita Instrumento VI en
--      guitarra).
--    - Se conserva @sigac_correquisitos_validados.
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

    SELECT COALESCE(NEW.id_curso, g.id_curso), g.tipo_grupo, g.id_instrumento
    INTO v_curso, v_tipo, v_instrumento
    FROM grupos AS g
    WHERE g.id_grupo = NEW.id_grupo
    LIMIT 1;

    IF v_tipo = 'ESPACIO_INSTRUMENTO' AND NEW.id_curso IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Un espacio de instrumento debe indicar el nivel (curso) que se matricula';
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
                   OR g2.id_instrumento = v_instrumento)
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
                   OR g2.id_instrumento = v_instrumento)
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
-- Verificación rápida (opcional)
-- ---------------------------------------------------------------------
-- SHOW COLUMNS FROM grupos LIKE 'tipo_grupo';
-- SHOW COLUMNS FROM matriculas_detalles LIKE 'id_curso';
-- SELECT id_curso, codigo, nombre FROM cursos WHERE es_curso_instrumento = TRUE;
-- SELECT id_estudiante, SUM(es_principal) AS del_plan
--   FROM estudiantes_instrumentos WHERE fecha_fin IS NULL
--  GROUP BY id_estudiante HAVING del_plan <> 1;   -- debe salir vacío
-- SHOW CREATE TABLE cursos_reconocidos;
