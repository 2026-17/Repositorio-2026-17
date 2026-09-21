/*
===============================================================================
 SIGAC - DATOS DE PRUEBA PARA LA MATRICULA DEL ESTUDIANTE (SOLO DESARROLLO)
===============================================================================

 NO ejecutar en produccion. Carga un catalogo academico pequeno para poder
 probar la Historia 2 (matricular cursos previamente autorizados) contra la
 base de datos real, sin datos quemados en la aplicacion.

 Es idempotente: se puede ejecutar varias veces sin duplicar registros.

 QUE CREA
 --------
   - 3 programas con un plan de estudio vigente cada uno: Guitarra, Teoria
     musical y Piano.
   - Cursos, malla curricular, prerrequisitos y un correquisito.
   - Un periodo academico en estado MATRICULA (solo si no existe ya uno).
   - 2 profesores y los grupos del periodo con cupos y horarios.
   - Un grupo sin profesor ni horario, igual a los que crea la HU-028.
   - La inscripcion de UN estudiante existente a los planes de Guitarra y
     Teoria (NO a Piano: sirve para comprobar que no puede matricular cursos
     de un plan en el que no esta inscrito).

 COMO USARLO
 -----------
   1. Ejecute este archivo: crea los planes, cursos y grupos de prueba.
   2. Registre un estudiante desde el sistema (Administrativo > Estudiantes >
      Registrar) y elija sus planes en el formulario: ya aparecen los de prueba.
      Eso tambien crea su usuario con rol ESTUDIANTE.
   3. Desde Administrativo, inicie y apruebe la solicitud de matricula del
      estudiante para el periodo. Despues el estudiante ya puede matricular.

   Alternativa para un estudiante YA registrado: cambie el valor de
   @numero_identificacion_estudiante (mas abajo) por su numero de
   identificacion y ejecute el archivo; lo inscribe a los planes de Guitarra
   y Teoria.
===============================================================================
*/

USE sigac_db;

SET NAMES utf8mb4;

-- >>> CAMBIAR: numero de identificacion de un estudiante ya registrado <<<
SET @numero_identificacion_estudiante = '000000000';

-- -----------------------------------------------------------------------------
-- Programas y planes de estudio
-- -----------------------------------------------------------------------------

INSERT IGNORE INTO programas (codigo, nombre, descripcion) VALUES
('PRG-GUITARRA', 'Guitarra', 'Ruta formativa de guitarra (datos de prueba)'),
('PRG-TEORIA',   'Teoria musical', 'Ruta formativa de teoria y lenguaje musical (datos de prueba)'),
('PRG-PIANO',    'Piano', 'Ruta formativa de piano (datos de prueba)');

INSERT IGNORE INTO planes_estudio
    (id_programa, codigo, nombre, version, fecha_vigencia_desde, vigente)
SELECT pr.id_programa, x.plan, x.nombre, '1.0', '2026-01-01', TRUE
FROM (
         SELECT 'PRG-GUITARRA' AS programa, 'PLAN-GUI-2026' AS plan, 'Plan de Guitarra 2026' AS nombre
         UNION ALL SELECT 'PRG-TEORIA', 'PLAN-TEO-2026', 'Plan de Teoria musical 2026'
         UNION ALL SELECT 'PRG-PIANO',  'PLAN-PIA-2026', 'Plan de Piano 2026'
     ) AS x
         INNER JOIN programas AS pr ON pr.codigo = x.programa;

-- -----------------------------------------------------------------------------
-- Cursos y malla curricular
-- -----------------------------------------------------------------------------

INSERT IGNORE INTO cursos (codigo, nombre, descripcion, horas_totales, activo) VALUES
('GUI-101', 'Guitarra I',            'Iniciacion a la guitarra', 32, TRUE),
('GUI-102', 'Guitarra II',           'Tecnica basica de guitarra', 32, TRUE),
('GUI-103', 'Guitarra III',          'Repertorio intermedio de guitarra', 32, TRUE),
('GUI-104', 'Lectura para guitarra', 'Lectura musical aplicada; se lleva junto con Guitarra II', 16, TRUE),
('TEO-101', 'Teoria musical I',      'Fundamentos de teoria musical', 32, TRUE),
('TEO-102', 'Teoria musical II',     'Intervalos, escalas y acordes', 32, TRUE),
('PIA-101', 'Piano I',               'Iniciacion al piano', 32, TRUE);

INSERT IGNORE INTO malla_curricular
    (id_plan_estudio, id_curso, nivel, orden_en_nivel, obligatorio, activo)
SELECT p.id_plan_estudio, c.id_curso, m.nivel, m.orden, TRUE, TRUE
FROM (
         SELECT 'PLAN-GUI-2026' AS plan, 'GUI-101' AS curso, 1 AS nivel, 1 AS orden
         UNION ALL SELECT 'PLAN-GUI-2026', 'GUI-102', 2, 1
         UNION ALL SELECT 'PLAN-GUI-2026', 'GUI-104', 2, 2
         UNION ALL SELECT 'PLAN-GUI-2026', 'GUI-103', 3, 1
         UNION ALL SELECT 'PLAN-TEO-2026', 'TEO-101', 1, 1
         UNION ALL SELECT 'PLAN-TEO-2026', 'TEO-102', 2, 1
         UNION ALL SELECT 'PLAN-PIA-2026', 'PIA-101', 1, 1
     ) AS m
         INNER JOIN planes_estudio AS p ON p.codigo = m.plan
         INNER JOIN cursos AS c ON c.codigo = m.curso;

-- Prerrequisitos (deben estar aprobados) y un correquisito (se lleva junto).
INSERT IGNORE INTO requisitos_cursos
    (id_plan_estudio, id_curso, id_curso_requerido, id_tipo_requisito, nota_minima, activo)
SELECT p.id_plan_estudio, c.id_curso, r.id_curso, t.id_tipo_requisito, x.nota, TRUE
FROM (
         SELECT 'PLAN-GUI-2026' AS plan, 'GUI-102' AS curso, 'GUI-101' AS requerido,
                'PRERREQUISITO' AS tipo, 70.00 AS nota
         UNION ALL SELECT 'PLAN-GUI-2026', 'GUI-103', 'GUI-102', 'PRERREQUISITO', 70.00
         UNION ALL SELECT 'PLAN-GUI-2026', 'GUI-104', 'GUI-102', 'CORREQUISITO', NULL
         UNION ALL SELECT 'PLAN-TEO-2026', 'TEO-102', 'TEO-101', 'PRERREQUISITO', 70.00
     ) AS x
         INNER JOIN planes_estudio AS p ON p.codigo = x.plan
         INNER JOIN cursos AS c ON c.codigo = x.curso
         INNER JOIN cursos AS r ON r.codigo = x.requerido
         INNER JOIN tipos_requisitos_cursos AS t ON t.codigo = x.tipo;

-- -----------------------------------------------------------------------------
-- Periodo academico en etapa de matricula
-- Solo se crea si no existe ya uno, para no duplicar el periodo vigente.
-- -----------------------------------------------------------------------------

INSERT INTO periodos_academicos
(codigo, nombre, fecha_inicio, fecha_fin,
 fecha_inicio_matricula, fecha_fin_matricula, estado)
SELECT 'PRUEBA-2026-II', 'II Ciclo 2026 (prueba)', '2026-08-03', '2026-11-27',
       '2026-07-01', '2026-12-31', 'MATRICULA'
WHERE NOT EXISTS (SELECT 1 FROM periodos_academicos WHERE estado = 'MATRICULA');

-- Periodo vigente: el mismo criterio que usa la aplicacion.
SET @id_periodo = (
    SELECT id_periodo
    FROM periodos_academicos
    WHERE estado = 'MATRICULA'
    ORDER BY fecha_inicio DESC
    LIMIT 1
);

-- -----------------------------------------------------------------------------
-- Profesores
-- -----------------------------------------------------------------------------

INSERT IGNORE INTO profesores
    (id_tipo_identificacion, numero_identificacion, nombres,
     primer_apellido, segundo_apellido, fecha_ingreso, activo)
SELECT ti.id_tipo_identificacion, x.numero, x.nombres, x.apellido1, x.apellido2,
       '2025-01-15', TRUE
FROM (
         SELECT 'PRUEBA-PROF-001' AS numero, 'Marcela' AS nombres,
                'Vargas' AS apellido1, 'Jimenez' AS apellido2
         UNION ALL SELECT 'PRUEBA-PROF-002', 'Andres', 'Rojas', 'Mora'
     ) AS x
         INNER JOIN tipos_identificacion AS ti ON ti.codigo = 'CEDULA';

-- -----------------------------------------------------------------------------
-- Grupos del periodo
-- GUI-103-A y TEO-101-A se solapan a proposito (lunes 08:00-09:30) para
-- probar el choque de horarios. TEO-102-A no tiene profesor ni horario.
-- PIA-101-A pertenece a un plan en el que el estudiante NO esta inscrito.
-- -----------------------------------------------------------------------------

INSERT IGNORE INTO grupos
    (id_periodo, id_curso, id_profesor, codigo, cupo_maximo, modalidad, aula, estado)
SELECT @id_periodo, c.id_curso, pr.id_profesor, g.grupo, g.cupo,
       'PRESENCIAL', g.aula, g.estado
FROM (
         SELECT 'GUI-101' AS curso, 'GUI-101-A' AS grupo, 'PRUEBA-PROF-001' AS profesor,
                12 AS cupo, 'Aula 1' AS aula, 'PUBLICADO' AS estado
         UNION ALL SELECT 'GUI-102', 'GUI-102-A', 'PRUEBA-PROF-001', 10, 'Aula 1', 'PUBLICADO'
         UNION ALL SELECT 'GUI-103', 'GUI-103-A', 'PRUEBA-PROF-001', 10, 'Aula 2', 'PUBLICADO'
         UNION ALL SELECT 'GUI-104', 'GUI-104-A', 'PRUEBA-PROF-002', 10, 'Aula 3', 'PUBLICADO'
         UNION ALL SELECT 'TEO-101', 'TEO-101-A', 'PRUEBA-PROF-002', 15, 'Aula 4', 'PUBLICADO'
         UNION ALL SELECT 'TEO-102', 'TEO-102-A', NULL, 15, NULL, 'PLANIFICADO'
         UNION ALL SELECT 'PIA-101', 'PIA-101-A', 'PRUEBA-PROF-002', 8, 'Aula 5', 'PUBLICADO'
     ) AS g
         INNER JOIN cursos AS c ON c.codigo = g.curso
         LEFT JOIN profesores AS pr ON pr.numero_identificacion = g.profesor
WHERE @id_periodo IS NOT NULL;

INSERT IGNORE INTO horarios_grupos (id_grupo, dia_semana, hora_inicio, hora_fin, aula)
SELECT g.id_grupo, h.dia, h.inicio, h.fin, h.aula
FROM (
         SELECT 'GUI-101-A' AS grupo, 1 AS dia, '08:00:00' AS inicio, '09:30:00' AS fin, 'Aula 1' AS aula
         UNION ALL SELECT 'GUI-101-A', 3, '08:00:00', '09:30:00', 'Aula 1'
         UNION ALL SELECT 'GUI-102-A', 2, '10:00:00', '11:30:00', 'Aula 1'
         UNION ALL SELECT 'GUI-103-A', 1, '08:00:00', '09:30:00', 'Aula 2'
         UNION ALL SELECT 'GUI-104-A', 4, '14:00:00', '15:00:00', 'Aula 3'
         UNION ALL SELECT 'TEO-101-A', 1, '08:00:00', '09:30:00', 'Aula 4'
         UNION ALL SELECT 'PIA-101-A', 5, '09:00:00', '10:30:00', 'Aula 5'
     ) AS h
         INNER JOIN grupos AS g ON g.codigo = h.grupo AND g.id_periodo = @id_periodo;

-- -----------------------------------------------------------------------------
-- Inscripcion del estudiante a los planes de Guitarra y Teoria (no Piano)
-- -----------------------------------------------------------------------------

INSERT IGNORE INTO estudiantes_planes_estudio
    (id_estudiante, id_plan_estudio, fecha_inscripcion, activo)
SELECT e.id_estudiante, p.id_plan_estudio, CURRENT_DATE, TRUE
FROM estudiantes AS e
         INNER JOIN planes_estudio AS p ON p.codigo IN ('PLAN-GUI-2026', 'PLAN-TEO-2026')
WHERE e.numero_identificacion = @numero_identificacion_estudiante;
