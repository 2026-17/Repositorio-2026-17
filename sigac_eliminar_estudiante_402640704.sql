-- =====================================================================
--  SIGAC - Eliminar el estudiante con identificación 402640704 (PRODUCCIÓN)
-- =====================================================================
--  Borra al estudiante y TODO su historial. No se puede deshacer
--  (salvo restaurando un respaldo). Si solo se quiere que deje de estar
--  activo, usar "Dar de baja" en la pantalla de Estudiantes (HU-009),
--  que conserva el historial.
--
--  Qué se borra:
--    - matrículas, cursos matriculados, notas y asistencias
--    - autorizaciones de matrícula y mensualidades
--    - documentos (solo el registro; ver paso 1.4 para los archivos)
--    - becas, instrumentos, planes de estudio y alertas
--    - encargados que queden sin ningún otro estudiante a cargo
--    - la ficha del estudiante
--
--  Qué NO se borra:
--    - su cuenta de usuario (si tiene): queda sin estudiante ligado.
--      Para borrarla usar sigac_eliminar_usuario_402640704.sql.
--    - los archivos físicos (PDF y foto de perfil) en el almacenamiento.
--
--  Antes de ejecutarlo: hacer un respaldo de la base (dump).
--  Ejecutar por pasos y revisar los resultados del paso 1 antes del 2.
--
--  No subir este archivo al repositorio: contiene una identificación.
-- =====================================================================

USE sigac_db;

SET @identificacion = '402640704';


-- =====================================================================
--  1. VERIFICACIÓN (solo consultas, no cambian nada)
-- =====================================================================

-- 1.1 Debe aparecer exactamente 1 fila: el estudiante que se va a eliminar.
--     Se ignoran guiones y espacios por si se guardó con formato.
SELECT e.id_estudiante, e.numero_identificacion,
       CONCAT_WS(' ', e.nombres, e.primer_apellido, e.segundo_apellido) AS nombre,
       e.activo, e.id_usuario, u.correo AS correo_cuenta
FROM estudiantes e
         LEFT JOIN usuarios u ON u.id_usuario = e.id_usuario
WHERE REPLACE(REPLACE(e.numero_identificacion, '-', ''), ' ', '') = @identificacion;

SET @id_estudiante = (
    SELECT id_estudiante
    FROM estudiantes
    WHERE REPLACE(REPLACE(numero_identificacion, '-', ''), ' ', '') = @identificacion
);

-- 1.2 Cuánto historial se va a borrar
SELECT
    (SELECT COUNT(*) FROM autorizaciones_matricula WHERE id_estudiante = @id_estudiante) AS autorizaciones,
    (SELECT COUNT(*) FROM matriculas m
         JOIN autorizaciones_matricula a ON a.id_autorizacion = m.id_autorizacion
     WHERE a.id_estudiante = @id_estudiante)                                            AS matriculas,
    (SELECT COUNT(*) FROM calificaciones c
         JOIN matriculas_detalles md     ON md.id_detalle_matricula = c.id_detalle_matricula
         JOIN matriculas m               ON m.id_matricula = md.id_matricula
         JOIN autorizaciones_matricula a ON a.id_autorizacion = m.id_autorizacion
     WHERE a.id_estudiante = @id_estudiante)                                            AS calificaciones,
    (SELECT COUNT(*) FROM mensualidades_estudiantes WHERE id_estudiante = @id_estudiante) AS mensualidades,
    (SELECT COUNT(*) FROM documentos_estudiantes    WHERE id_estudiante = @id_estudiante) AS documentos,
    (SELECT COUNT(*) FROM becas                     WHERE id_estudiante = @id_estudiante) AS becas;

-- 1.3 Encargados ligados. Los que tengan otros_estudiantes = 0 se borran;
--     los demás se conservan porque tienen otros estudiantes a cargo.
SELECT en.id_encargado,
       CONCAT_WS(' ', en.nombres, en.primer_apellido, en.segundo_apellido) AS encargado,
       ee.parentesco,
       (SELECT COUNT(*) FROM estudiantes_encargados otros
        WHERE otros.id_encargado = en.id_encargado
          AND otros.id_estudiante <> @id_estudiante) AS otros_estudiantes
FROM estudiantes_encargados ee
         JOIN encargados en ON en.id_encargado = ee.id_encargado
WHERE ee.id_estudiante = @id_estudiante;

-- 1.4 Archivos físicos del estudiante. La base solo guarda la ruta:
--     anotar estas rutas para borrar los archivos a mano después.
SELECT 'DOCUMENTO' AS tipo, ubicacion_archivo AS ruta
FROM documentos_estudiantes
WHERE id_estudiante = @id_estudiante
UNION ALL
SELECT 'FOTO_PERFIL', foto_perfil_ruta
FROM estudiantes
WHERE id_estudiante = @id_estudiante
  AND foto_perfil_ruta IS NOT NULL;


-- =====================================================================
--  2. ELIMINACIÓN
-- =====================================================================
--  Todo va dentro de una transacción: si algo falla, ejecutar ROLLBACK;
--  y no queda ningún cambio a medias.
--
--  El orden importa: las matrículas, autorizaciones y mensualidades
--  apuntan a la autorización o a documentos del estudiante, así que
--  se borran antes que él. El resto se borra solo (ON DELETE CASCADE)
--  al borrar la ficha del estudiante.
--
--  Workbench trae activado el "safe update mode", que rechaza los DELETE
--  que filtran con subconsultas (Error 1175). Se desactiva solo en esta
--  conexión y se vuelve a activar al final.

SET SQL_SAFE_UPDATES = 0;

START TRANSACTION;

-- Se guardan los encargados ligados antes de que se borre la relación
DROP TEMPORARY TABLE IF EXISTS tmp_encargados_estudiante;
CREATE TEMPORARY TABLE tmp_encargados_estudiante AS
SELECT id_encargado
FROM estudiantes_encargados
WHERE id_estudiante = @id_estudiante;

-- Matrículas (en cascada: cursos matriculados, notas y asistencias)
DELETE FROM matriculas
WHERE id_autorizacion IN (SELECT id_autorizacion
                          FROM autorizaciones_matricula
                          WHERE id_estudiante = @id_estudiante);

DELETE FROM autorizaciones_matricula WHERE id_estudiante = @id_estudiante;

DELETE FROM mensualidades_estudiantes WHERE id_estudiante = @id_estudiante;

-- Ficha del estudiante (en cascada: documentos, becas, instrumentos,
-- planes, alertas y la relación con sus encargados)
DELETE FROM estudiantes WHERE id_estudiante = @id_estudiante;

-- Encargados que quedaron sin ningún estudiante a cargo
DELETE FROM encargados
WHERE id_encargado IN (SELECT id_encargado FROM tmp_encargados_estudiante)
  AND id_encargado NOT IN (SELECT id_encargado FROM estudiantes_encargados);

DROP TEMPORARY TABLE tmp_encargados_estudiante;

-- Debe dar 0: el estudiante ya no existe
SELECT COUNT(*) AS estudiantes_con_esa_identificacion
FROM estudiantes
WHERE REPLACE(REPLACE(numero_identificacion, '-', ''), ' ', '') = @identificacion;

COMMIT;

SET SQL_SAFE_UPDATES = 1;
