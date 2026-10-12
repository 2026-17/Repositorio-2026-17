-- =====================================================================
--  Migración: tabla de notificaciones del estudiante (HU-012)
--  Para bases que ya existen (local o Railway). Si la tabla ya está
--  creada no hace nada, así que se puede correr más de una vez.
--  Requiere que ya existan estudiantes y autorizaciones_matricula.
-- =====================================================================
USE sigac_db;

-- Avisos al estudiante sobre su solicitud de matrícula (aprobada o rechazada)
CREATE TABLE IF NOT EXISTS notificaciones_estudiantes (
  id_notificacion    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_estudiante      INT UNSIGNED    NOT NULL,
  id_autorizacion    BIGINT UNSIGNED,
  tipo               VARCHAR(40)     NOT NULL,
  asunto             VARCHAR(160)    NOT NULL,
  mensaje            VARCHAR(1500)   NOT NULL,
  leida              BOOLEAN         NOT NULL DEFAULT FALSE,
  fecha_notificacion DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_lectura      DATETIME,
  PRIMARY KEY (id_notificacion),
-- Una sola notificación por solicitud de matrícula
  CONSTRAINT uq_notificaciones_autorizacion UNIQUE (id_autorizacion),
-- "Mis notificaciones": las del estudiante, de la más reciente a la más antigua
  INDEX idx_notificaciones_estudiante_fecha (id_estudiante, fecha_notificacion),
  CONSTRAINT fk_notificaciones_estudiante FOREIGN KEY (id_estudiante)
      REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
  CONSTRAINT fk_notificaciones_autorizacion FOREIGN KEY (id_autorizacion)
      REFERENCES autorizaciones_matricula (id_autorizacion) ON DELETE CASCADE
);
