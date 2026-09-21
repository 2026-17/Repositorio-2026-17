/*
===============================================================================
 SIGAC - MIGRACION: INSCRIPCION DE ESTUDIANTES A PLANES DE ESTUDIO
===============================================================================

 Para bases de datos que YA existen (no hace falta en una instalacion limpia
 con sigac_db.sql, que ya incluye esta tabla).

 Historia 2 (matricular cursos autorizados): un estudiante solo puede matricular
 cursos de los planes de estudio en los que esta inscrito. Un estudiante puede
 estar inscrito en varios planes a la vez.

 Es idempotente: se puede ejecutar mas de una vez sin duplicar ni borrar datos.
===============================================================================
*/

USE sigac_db;

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
