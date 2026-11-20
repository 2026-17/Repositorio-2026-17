        -- =====================================================================
        --  SIGAC - Cambio 2026-11-17: tipo de estudiante (REGULAR / INCLUSION)
        --  Motor: MySQL 8
        -- =====================================================================
        --  ORDEN: se ejecuta DESPUÉS de sigac_db.sql, sobre una base que ya
        --  tiene el esquema creado (local o Railway).
        --
        --  Antes de ejecutarlo, seleccione la base correcta (no se fija aquí
        --  porque en Railway se llama distinto que en local):
        --      USE sigac_db;      -- local
        --      USE railway;       -- Railway (o el nombre que tenga)
        --
        --  Qué hace:
        --    a) Crea el catálogo tipos_estudiante.
        --    b) Inserta REGULAR e INCLUSION.
        --    c) Agrega estudiantes.id_tipo_estudiante: primero NULL, luego deja
        --       a todos los estudiantes existentes como REGULAR y por último la
        --       vuelve NOT NULL, con llave foránea al catálogo.
        --    d) Inserta el tipo de beca INCLUSION (beca permanente del 100%).
        --
        --  Se puede ejecutar varias veces sin error: cada paso revisa primero
        --  si ya está hecho.
        --
        --  El tipo de estudiante es una categoría administrativa: no tiene
        --  relación con estudiantes.condiciones_relevantes ni guarda
        --  diagnósticos.
        -- =====================================================================

        SET NAMES utf8mb4;

        -- ---------------------------------------------------------------------
        -- a) Catálogo de tipos de estudiante (mismo estilo que tipos_becas)
        -- ---------------------------------------------------------------------
        CREATE TABLE IF NOT EXISTS tipos_estudiante (
            id_tipo_estudiante SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
            codigo             VARCHAR(30)  NOT NULL,
            nombre             VARCHAR(100) NOT NULL,
            descripcion        VARCHAR(500),
            activo             BOOLEAN      NOT NULL DEFAULT TRUE,
            PRIMARY KEY (id_tipo_estudiante),
            CONSTRAINT uq_tipos_estudiante_codigo UNIQUE (codigo),
            CONSTRAINT uq_tipos_estudiante_nombre UNIQUE (nombre)
        );

        -- ---------------------------------------------------------------------
        -- b) Tipos REGULAR e INCLUSION (solo si no existen)
        -- ---------------------------------------------------------------------
        INSERT INTO tipos_estudiante (codigo, nombre, descripcion, activo)
        SELECT 'REGULAR', 'Regular', 'Estudiante regular.', TRUE
        WHERE NOT EXISTS (SELECT 1 FROM tipos_estudiante WHERE codigo = 'REGULAR');

        INSERT INTO tipos_estudiante (codigo, nombre, descripcion, activo)
        SELECT 'INCLUSION', 'Inclusión',
               'Estudiante de inclusión (incluye Taller de Inclusión). Tiene beca permanente del 100%.', TRUE
        WHERE NOT EXISTS (SELECT 1 FROM tipos_estudiante WHERE codigo = 'INCLUSION');

        SET @id_regular := (SELECT id_tipo_estudiante FROM tipos_estudiante WHERE codigo = 'REGULAR');

        -- ---------------------------------------------------------------------
        -- c) Columna estudiantes.id_tipo_estudiante
        --    MySQL 8 no tiene "ADD COLUMN IF NOT EXISTS", por eso cada ALTER se
        --    arma como texto y solo se ejecuta si hace falta ('DO 0' = no hacer nada).
        -- ---------------------------------------------------------------------

        -- c.1) Agregar la columna como NULL
        SET @sql := IF(
            (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'estudiantes'
                AND COLUMN_NAME = 'id_tipo_estudiante') = 0,
            'ALTER TABLE estudiantes ADD COLUMN id_tipo_estudiante SMALLINT UNSIGNED NULL AFTER id_tipo_identificacion',
            'DO 0');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

        -- c.2) Todos los estudiantes existentes quedan como REGULAR
        --      ("id_estudiante > 0" permite ejecutarlo con el modo seguro de
        --      MySQL Workbench activado, que exige usar la llave en el WHERE).
        UPDATE estudiantes
           SET id_tipo_estudiante = @id_regular
         WHERE id_tipo_estudiante IS NULL
           AND id_estudiante > 0;

        -- c.3) Volverla NOT NULL (solo si todavía admite NULL).
        --      El valor por defecto es REGULAR, para que otros scripts que
        --      insertan estudiantes sin indicar el tipo (datos de prueba, etc.)
        --      sigan funcionando. El backend siempre envía el tipo.
        SET @sql := IF(
            (SELECT IS_NULLABLE FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'estudiantes'
                AND COLUMN_NAME = 'id_tipo_estudiante') = 'YES',
            CONCAT('ALTER TABLE estudiantes MODIFY id_tipo_estudiante SMALLINT UNSIGNED NOT NULL DEFAULT ', @id_regular),
            'DO 0');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

        -- c.4) Llave foránea al catálogo (MySQL crea el índice automáticamente)
        SET @sql := IF(
            (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
              WHERE CONSTRAINT_SCHEMA = DATABASE()
                AND TABLE_NAME = 'estudiantes'
                AND CONSTRAINT_NAME = 'fk_estudiantes_tipos_estudiante'
                AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
            'ALTER TABLE estudiantes ADD CONSTRAINT fk_estudiantes_tipos_estudiante FOREIGN KEY (id_tipo_estudiante) REFERENCES tipos_estudiante (id_tipo_estudiante)',
            'DO 0');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

        -- ---------------------------------------------------------------------
        -- d) Tipo de beca INCLUSION (solo si no existe)
        --    La beca permanente de cada estudiante de inclusión la crea el
        --    backend: tipo INCLUSION, 100%, sin período (id_periodo NULL),
        --    sin fecha de fin y activa.
        -- ---------------------------------------------------------------------
        INSERT INTO tipos_becas (codigo, nombre, descripcion, activo)
        SELECT 'INCLUSION', 'Inclusión',
               'Beca permanente del 100% para estudiantes de inclusión. Se asigna automáticamente según el tipo de estudiante.', TRUE
        WHERE NOT EXISTS (SELECT 1 FROM tipos_becas WHERE codigo = 'INCLUSION');

        -- ---------------------------------------------------------------------
        -- Verificación rápida (opcional)
        -- ---------------------------------------------------------------------
        -- SELECT * FROM tipos_estudiante;
        -- SELECT * FROM tipos_becas WHERE codigo = 'INCLUSION';
        -- SELECT id_tipo_estudiante, COUNT(*) FROM estudiantes GROUP BY id_tipo_estudiante;
