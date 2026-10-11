/*
 * HU-014 - Asignación de profesores a cursos y grupos.
 *
 * Ejecutar UNA SOLA VEZ sobre una base SIGAC ya existente, después de
 * seleccionar la base de datos correcta con USE sigac_db;.
 * En instalaciones nuevas, sigac_db.sql ya contiene estos cambios.
 */
USE sigac_db;

ALTER TABLE grupos
    ADD COLUMN id_usuario_ultima_modificacion INT UNSIGNED NULL,
    ADD CONSTRAINT fk_grupos_usuario_modifica
        FOREIGN KEY (id_usuario_ultima_modificacion)
        REFERENCES usuarios (id_usuario) ON DELETE SET NULL;

DELIMITER $$

CREATE TRIGGER trg_grupos_ai_bitacora AFTER INSERT ON grupos FOR EACH ROW
BEGIN
    INSERT INTO bitacoras
    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
    VALUES
        (NEW.id_usuario_ultima_modificacion, 'grupos', NEW.id_grupo, 'INSERT', NULL,
         JSON_OBJECT('id_periodo', NEW.id_periodo, 'id_curso', NEW.id_curso,
                     'id_profesor', NEW.id_profesor, 'codigo', NEW.codigo,
                     'estado', NEW.estado));
END$$

CREATE TRIGGER trg_grupos_au_bitacora AFTER UPDATE ON grupos FOR EACH ROW
BEGIN
    IF NOT (OLD.id_profesor <=> NEW.id_profesor)
       OR NOT (OLD.estado <=> NEW.estado) THEN
        INSERT INTO bitacoras
        (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
        VALUES
            (NEW.id_usuario_ultima_modificacion, 'grupos', NEW.id_grupo, 'UPDATE',
             JSON_OBJECT('id_periodo', OLD.id_periodo, 'id_curso', OLD.id_curso,
                         'id_profesor', OLD.id_profesor, 'codigo', OLD.codigo,
                         'estado', OLD.estado),
             JSON_OBJECT('id_periodo', NEW.id_periodo, 'id_curso', NEW.id_curso,
                         'id_profesor', NEW.id_profesor, 'codigo', NEW.codigo,
                         'estado', NEW.estado));
    END IF;
END$$

CREATE TRIGGER trg_grupos_ad_bitacora AFTER DELETE ON grupos FOR EACH ROW
BEGIN
    INSERT INTO bitacoras
    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
    VALUES
        (OLD.id_usuario_ultima_modificacion, 'grupos', OLD.id_grupo, 'DELETE',
         JSON_OBJECT('id_periodo', OLD.id_periodo, 'id_curso', OLD.id_curso,
                     'id_profesor', OLD.id_profesor, 'codigo', OLD.codigo,
                     'estado', OLD.estado), NULL);
END$$

DELIMITER ;
