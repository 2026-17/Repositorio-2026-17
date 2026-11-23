-- =====================================================================
--  SIGAC - Cambio 2026-11-23: tabla contactos_emergencia
--  Motor: MySQL 8.0.16 o superior
-- =====================================================================
--  La tabla ya existe en Railway y la usa la entidad ContactoEmergencia,
--  pero faltaba en los scripts del repositorio. Este script es para quien
--  tenga una base sin ella. Con IF NOT EXISTS no modifica una tabla que
--  ya exista, así que en Railway no hace ningún cambio.
--
--  Antes de ejecutarlo, seleccione la base correcta.
-- =====================================================================

CREATE TABLE IF NOT EXISTS contactos_emergencia (
    id_contacto_emergencia INT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_estudiante          INT UNSIGNED NOT NULL,
    id_encargado           INT UNSIGNED NULL,
    nombre                 VARCHAR(180) NOT NULL,
    telefono               VARCHAR(30)  NOT NULL,
    relacion               VARCHAR(80)  NOT NULL,
    activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id_contacto_emergencia),
    INDEX idx_contactos_emergencia_estudiante (id_estudiante, activo),
    CONSTRAINT fk_contactos_emergencia_estudiante FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante),
    CONSTRAINT fk_contactos_emergencia_encargado FOREIGN KEY (id_encargado) REFERENCES encargados (id_encargado)
);
