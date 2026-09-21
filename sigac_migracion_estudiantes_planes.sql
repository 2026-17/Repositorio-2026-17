/*
===============================================================================
 SIGAC - MIGRACION: INSCRIPCION DE ESTUDIANTES A PLANES DE ESTUDIO
===============================================================================

 Para bases de datos que YA existen (no hace falta en una instalacion limpia
 con sigac_db.sql, que ya incluye esta tabla).

 Historia 2 (matricular cursos autorizados): un estudiante solo puede matricular
 cursos de los planes de estudio en los que esta inscrito. Un estudiante puede
 estar inscrito en varios planes a la vez.

 COMO EJECUTARLO EN MYSQL WORKBENCH
 ----------------------------------
   1. Conectese a la base y abra este archivo (File > Open SQL Script).
   2. Ejecute SOLO la SECCION 1 (crea la tabla). Es lo unico obligatorio.
   3. Ejecute la SECCION 2 segun lo que necesite (inscribir estudiantes).
   4. Use la SECCION 3 para verificar.

 La SECCION 1 es idempotente: se puede ejecutar mas de una vez sin duplicar ni
 borrar datos. No modifica ninguna tabla existente.
===============================================================================
*/

USE sigac_db;

-- =============================================================================
-- SECCION 1 (OBLIGATORIA): crear la tabla
-- =============================================================================

CREATE TABLE IF NOT EXISTS estudiantes_planes_estudio (
                                                          id_estudiante_plan              INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                          id_estudiante                   INT UNSIGNED NOT NULL,
                                                          id_plan_estudio                 INT UNSIGNED NOT NULL,
                                                          fecha_inscripcion               DATE NOT NULL DEFAULT (CURRENT_DATE),
    activo                          BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion  INT UNSIGNED NULL,
    creado_en                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_estudiantes_planes_estudio PRIMARY KEY (id_estudiante_plan),
    CONSTRAINT uq_estudiantes_planes_estudio
    UNIQUE (id_estudiante, id_plan_estudio),
    CONSTRAINT fk_estudiantes_planes_estudiantes
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_estudiantes_planes_planes
    FOREIGN KEY (id_plan_estudio) REFERENCES planes_estudio (id_plan_estudio)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_estudiantes_planes_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_estudiantes_planes_plan (id_plan_estudio, activo)
    ) ENGINE=InnoDB COMMENT='Planes de estudio en los que esta inscrito cada estudiante; limita los cursos que puede matricular';

-- =============================================================================
-- SECCION 2 (OPCIONAL): inscribir estudiantes en planes
--
-- Los estudiantes NUEVOS eligen sus planes en el formulario de registro (es
-- obligatorio) y pueden cambiarlos al editar al estudiante. Esta seccion sirve
-- para los estudiantes YA registrados antes de ese cambio, que no tienen plan:
-- sin inscripcion, el estudiante NO ve cursos para matricular.
--
-- Elija UNA de las opciones, reemplace el valor entre < > y ejecute solo esa
-- sentencia. Antes, consulte los codigos de plan y los estudiantes disponibles:
-- =============================================================================

-- SELECT id_plan_estudio, codigo, nombre, vigente FROM planes_estudio;
-- SELECT id_estudiante, numero_identificacion, nombres, primer_apellido, activo FROM estudiantes;

-- OPCION A: inscribir a UN estudiante en TODOS los planes vigentes.
-- INSERT IGNORE INTO estudiantes_planes_estudio
--     (id_estudiante, id_plan_estudio, fecha_inscripcion, activo)
-- SELECT e.id_estudiante, p.id_plan_estudio, CURRENT_DATE, TRUE
-- FROM estudiantes AS e
--          CROSS JOIN planes_estudio AS p
-- WHERE e.numero_identificacion = '<NUMERO_DE_IDENTIFICACION>'
--   AND p.vigente = TRUE;

-- OPCION B: inscribir a TODOS los estudiantes activos en UN plan concreto.
-- INSERT IGNORE INTO estudiantes_planes_estudio
--     (id_estudiante, id_plan_estudio, fecha_inscripcion, activo)
-- SELECT e.id_estudiante, p.id_plan_estudio, CURRENT_DATE, TRUE
-- FROM estudiantes AS e
--          CROSS JOIN planes_estudio AS p
-- WHERE e.activo = TRUE
--   AND p.codigo = '<CODIGO_DEL_PLAN>';

-- Para dar de baja una inscripcion sin borrarla:
-- UPDATE estudiantes_planes_estudio SET activo = FALSE
-- WHERE id_estudiante = <ID_ESTUDIANTE> AND id_plan_estudio = <ID_PLAN>;

-- =============================================================================
-- SECCION 3: verificacion
-- =============================================================================

-- La tabla existe:
-- SHOW CREATE TABLE estudiantes_planes_estudio;

-- Quien esta inscrito en que plan:
-- SELECT e.numero_identificacion,
--        CONCAT_WS(' ', e.nombres, e.primer_apellido) AS estudiante,
--        p.codigo AS plan, ep.fecha_inscripcion, ep.activo
-- FROM estudiantes_planes_estudio AS ep
--          INNER JOIN estudiantes AS e ON e.id_estudiante = ep.id_estudiante
--          INNER JOIN planes_estudio AS p ON p.id_plan_estudio = ep.id_plan_estudio
-- ORDER BY e.primer_apellido, p.codigo;

-- Requisitos para que un estudiante pueda matricular (deben cumplirse todos):
--   1. Existe un periodo en estado MATRICULA:
-- SELECT id_periodo, codigo, nombre, estado FROM periodos_academicos WHERE estado = 'MATRICULA';
--   2. Su autorizacion de ese periodo esta AUTORIZADA (la registra el administrativo):
-- SELECT a.id_autorizacion, e.numero_identificacion, ea.codigo AS estado
-- FROM autorizaciones_matricula AS a
--          INNER JOIN estudiantes AS e ON e.id_estudiante = a.id_estudiante
--          INNER JOIN estados_autorizaciones_matricula AS ea
--                     ON ea.id_estado_autorizacion = a.id_estado_autorizacion;
--   3. Esta inscrito en un plan (consulta anterior) y ese plan tiene cursos con
--      grupo en el periodo (los grupos se crean junto con el curso, HU-028).
