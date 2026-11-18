-- =====================================================================
-- Migracion: permitir correquisitos mutuos en la matricula
-- Fecha: 2026-11-18
--
-- Problema: el trigger trg_matriculas_detalles_bi_validar_requisitos
-- revisa los correquisitos fila por fila. Si dos cursos se exigen
-- mutuamente (A exige B y B exige A), el primero en insertarse nunca
-- encuentra al otro y la matricula se rechaza siempre.
--
-- Solucion: el backend ya valida los correquisitos con la seleccion
-- completa. Antes de insertar marca @sigac_correquisitos_validados = 1
-- y el trigger omite esa revision. Sin la marca (por ejemplo, un INSERT
-- manual) el trigger valida igual que antes. Los prerrequisitos se
-- siguen validando siempre.
--
-- Es seguro aplicarla antes de desplegar el backend: mientras nadie
-- marque la variable, el comportamiento es identico al anterior.
-- Se puede ejecutar mas de una vez.
-- =====================================================================

DROP TRIGGER IF EXISTS trg_matriculas_detalles_bi_validar_requisitos;

DELIMITER $$

CREATE TRIGGER trg_matriculas_detalles_bi_validar_requisitos BEFORE INSERT ON matriculas_detalles FOR EACH ROW
BEGIN
    DECLARE v_estudiante INT UNSIGNED DEFAULT NULL;
    DECLARE v_plan INT UNSIGNED DEFAULT NULL;
    DECLARE v_curso INT UNSIGNED DEFAULT NULL;

    SELECT a.id_estudiante, m.id_plan_estudio
    INTO v_estudiante, v_plan
    FROM matriculas AS m
             INNER JOIN autorizaciones_matricula AS a
                        ON a.id_autorizacion = m.id_autorizacion
    WHERE m.id_matricula = NEW.id_matricula
    LIMIT 1;

    SELECT id_curso
    INTO v_curso
    FROM grupos
    WHERE id_grupo = NEW.id_grupo
    LIMIT 1;

    -- Prerrequisitos: debe existir una calificacion APROBADA
    -- del curso requerido, cumpliendo la nota minima si aplica.
    IF EXISTS (
        SELECT 1
        FROM requisitos_cursos AS rc
                 INNER JOIN tipos_requisitos_cursos AS trc
                            ON trc.id_tipo_requisito = rc.id_tipo_requisito
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
              AND g2.id_curso = rc.id_curso_requerido
              AND ea2.codigo = 'APROBADO'
              AND (rc.nota_minima IS NULL OR c2.nota_final >= rc.nota_minima)
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El estudiante no cumple los prerrequisitos del curso';
    END IF;

    -- Correquisitos: valido si ya lo aprobo antes, o si lo esta
    -- matriculando en la misma matricula (mismo periodo).
    -- Si el backend ya los valido con la seleccion completa, marca
    -- @sigac_correquisitos_validados = 1 y esta revision se omite: fila
    -- por fila es imposible aceptar correquisitos mutuos (A exige B y
    -- B exige A), porque el primero en insertarse nunca encuentra al otro.
    IF @sigac_correquisitos_validados IS NULL AND EXISTS (
        SELECT 1
        FROM requisitos_cursos AS rc
                 INNER JOIN tipos_requisitos_cursos AS trc
                            ON trc.id_tipo_requisito = rc.id_tipo_requisito
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
              AND g2.id_curso = rc.id_curso_requerido
              AND ea2.codigo = 'APROBADO'
              AND (rc.nota_minima IS NULL OR c2.nota_final >= rc.nota_minima)
        )
          AND NOT EXISTS (
            SELECT 1
            FROM matriculas_detalles AS md3
                     INNER JOIN grupos AS g3
                                ON g3.id_grupo = md3.id_grupo
            WHERE md3.id_matricula = NEW.id_matricula
              AND g3.id_curso = rc.id_curso_requerido
              AND md3.retirado = FALSE
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El estudiante no cumple los correquisitos del curso';
    END IF;
END$$

DELIMITER ;
