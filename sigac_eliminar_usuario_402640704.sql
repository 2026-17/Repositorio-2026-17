-- =====================================================================
--  SIGAC - Eliminar el usuario con cédula 402640704 (PRODUCCIÓN)
-- =====================================================================
--  Elimina SOLO la cuenta de acceso (tabla usuarios). Si la cédula es
--  de un estudiante o profesor, su ficha NO se borra: queda sin cuenta
--  (id_usuario = NULL) y se le puede crear una nueva más adelante.
--
--  Qué pasa con lo relacionado al usuario:
--    - tokens_revocados (sesiones cerradas): se borran en este script.
--    - codigos_verificacion: se borran solos (ON DELETE CASCADE).
--    - bitacoras y columnas id_usuario_ultima_modificacion, id_usuario_carga,
--      id_usuario_asigna: quedan en NULL (se pierde quién hizo el cambio).
--    - mensualidades confirmadas, autorizaciones revisadas o alertas
--      atendidas por este usuario: IMPIDEN borrarlo (ver paso 1.3).
--
--  Antes de ejecutarlo: hacer un respaldo de la base (dump).
--  Ejecutar por pasos y revisar los resultados del paso 1 antes del 2.
--
--  No subir este archivo al repositorio: contiene una cédula.
-- =====================================================================

USE sigac_db;

SET @cedula = '402640704';


-- =====================================================================
--  1. VERIFICACIÓN (solo consultas, no cambian nada)
-- =====================================================================

-- 1.1 Debe aparecer exactamente 1 fila: el usuario que se va a eliminar.
--     Se ignoran guiones y espacios por si la cédula se guardó con formato.
SELECT u.id_usuario, u.cedula, u.nombre, u.correo, r.codigo AS rol, eu.codigo AS estado
FROM usuarios u
         JOIN roles r             ON r.id_rol = u.id_rol
         JOIN estados_usuarios eu ON eu.id_estado_usuario = u.id_estado_usuario
WHERE REPLACE(REPLACE(u.cedula, '-', ''), ' ', '') = @cedula;

SET @id_usuario = (
    SELECT id_usuario
    FROM usuarios
    WHERE REPLACE(REPLACE(cedula, '-', ''), ' ', '') = @cedula
);

-- 1.2 Fichas de estudiante o profesor ligadas a esta cuenta (quedarán sin cuenta)
SELECT 'ESTUDIANTE' AS tipo, id_estudiante AS id,
       CONCAT_WS(' ', nombres, primer_apellido, segundo_apellido) AS nombre
FROM estudiantes
WHERE id_usuario = @id_usuario
UNION ALL
SELECT 'PROFESOR', id_profesor,
       CONCAT_WS(' ', nombres, primer_apellido, segundo_apellido)
FROM profesores
WHERE id_usuario = @id_usuario;

-- 1.3 Registros que IMPIDEN borrar el usuario. Todos deben estar en 0.
--     Si alguno es mayor que 0, NO ejecutar el paso 2: en ese caso es
--     mejor desactivar la cuenta (cambiar su id_estado_usuario) en vez
--     de borrarla, para no perder quién confirmó o revisó esos datos.
SELECT
    (SELECT COUNT(*) FROM mensualidades_estudiantes WHERE id_usuario_confirma = @id_usuario) AS mensualidades_confirmadas,
    (SELECT COUNT(*) FROM autorizaciones_matricula  WHERE id_usuario_revisa   = @id_usuario) AS autorizaciones_revisadas,
    (SELECT COUNT(*) FROM alertas_academicas        WHERE id_usuario_atiende  = @id_usuario) AS alertas_atendidas;


-- =====================================================================
--  2. ELIMINACIÓN
-- =====================================================================
--  Todo va dentro de una transacción: si algo falla, ejecutar ROLLBACK;
--  y no queda ningún cambio a medias.
--
--  Workbench trae activado el "safe update mode", que puede rechazar
--  estos DELETE (Error 1175). Se desactiva solo en esta conexión y se
--  vuelve a activar al final.

SET SQL_SAFE_UPDATES = 0;

START TRANSACTION;

DELETE FROM tokens_revocados WHERE id_usuario = @id_usuario;

DELETE FROM usuarios WHERE id_usuario = @id_usuario;

-- Debe dar 0: el usuario ya no existe
SELECT COUNT(*) AS usuarios_con_esa_cedula
FROM usuarios
WHERE REPLACE(REPLACE(cedula, '-', ''), ' ', '') = @cedula;

COMMIT;

SET SQL_SAFE_UPDATES = 1;
