/*
===============================================================================
 SIGAC - SISTEMA DE GESTION ACADEMICA
 Script completo y simplificado para MySQL 8.4+
===============================================================================

 PROPOSITO
 ---------
 Este archivo crea una base de datos relacional para administrar:
   - usuarios y roles;
   - estudiantes, profesores y encargados;
   - instrumentos que estudia cada estudiante;
   - programas, planes de estudio, cursos y malla curricular;
   - periodos, grupos, autorizacion previa, matriculas, notas y asistencia;
   - becas, documentos, comprobantes y confirmacion de mensualidades;
   - alertas academicas;
   - bitacora automatica mediante triggers.

 DECISIONES IMPORTANTES DEL ALCANCE
 ----------------------------------
 1. NO existe inventario de instrumentos, prestamos, bodegas, activos,
    mantenimiento ni devoluciones.
 2. La tabla estudiantes_instrumentos indica que instrumento estudia una
    persona. Un estudiante puede estudiar varios instrumentos.
 3. El sistema NO procesa pagos. Guarda comprobantes como documentos y permite
    confirmar administrativamente mensualidades y el pago de matricula.
 4. Antes de matricular, el estudiante necesita una autorizacion administrativa
    para el periodo. Sin esa autorizacion, la base rechaza la matricula.
 5. Cada usuario posee un solo rol. Se omiten permisos granulares para que el
    modelo sea comprensible y apropiado para el alcance universitario.
 6. La bitacora registra INSERT, UPDATE y DELETE solamente en estudiantes,
    profesores, programas, planes de estudio, cursos, malla y requisitos.
 7. Los archivos no se guardan como BLOB. Se almacena su ubicacion privada y
    sus metadatos; el archivo fisico debe vivir en almacenamiento protegido.

 RECOMENDACION DE EJECUCION
 -------------------------
 Ejecutar el archivo completo con una cuenta que pueda crear bases, tablas,
 vistas y triggers. Si Railway ya proporciona una base con otro nombre,
 cambie "sigac" en las primeras instrucciones o quite CREATE DATABASE/USE.

 El script usa CREATE TABLE IF NOT EXISTS para evitar borrar informacion.
 Para una instalacion limpia, ejecutarlo sobre una base vacia.
===============================================================================
*/

CREATE DATABASE IF NOT EXISTS sigac_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE sigac_db;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- =============================================================================
-- 1. SEGURIDAD Y ACCESO
-- =============================================================================

CREATE TABLE IF NOT EXISTS roles (
                                     id_rol              SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                     codigo              VARCHAR(40) NOT NULL,
    nombre              VARCHAR(80) NOT NULL,
    descripcion         VARCHAR(255) NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_roles PRIMARY KEY (id_rol),
    CONSTRAINT uq_roles_codigo UNIQUE (codigo),
    CONSTRAINT uq_roles_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Roles generales del sistema; cada usuario posee uno';

CREATE TABLE IF NOT EXISTS estados_usuarios (
                                                id_estado_usuario   SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                codigo              VARCHAR(30) NOT NULL,
    nombre              VARCHAR(60) NOT NULL,
    descripcion         VARCHAR(255) NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_estados_usuarios PRIMARY KEY (id_estado_usuario),
    CONSTRAINT uq_estados_usuarios_codigo UNIQUE (codigo),
    CONSTRAINT uq_estados_usuarios_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Catalogo de estados de las cuentas de usuario';

CREATE TABLE IF NOT EXISTS usuarios (
                                        id_usuario                  INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                        id_rol                      SMALLINT UNSIGNED NOT NULL,
                                        id_estado_usuario           SMALLINT UNSIGNED NOT NULL,
                                        cedula                      VARCHAR(40) NULL,
    nombre                      VARCHAR(150) NOT NULL,
    correo                      VARCHAR(254) NOT NULL,
    hash_contrasena             VARCHAR(255) NOT NULL,
    requiere_cambio_contrasena  BOOLEAN NOT NULL DEFAULT TRUE,
    intentos_fallidos           TINYINT UNSIGNED NOT NULL DEFAULT 0,
    bloqueado_hasta             DATETIME(6) NULL,
    ultimo_acceso               DATETIME(6) NULL,
    creado_en                   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_usuarios PRIMARY KEY (id_usuario),
    CONSTRAINT uq_usuarios_cedula UNIQUE (cedula),
    CONSTRAINT uq_usuarios_correo UNIQUE (correo),

    CONSTRAINT fk_usuarios_roles
    FOREIGN KEY (id_rol) REFERENCES roles (id_rol)
    ON UPDATE RESTRICT ON DELETE RESTRICT,

    CONSTRAINT fk_usuarios_estados
    FOREIGN KEY (id_estado_usuario)
    REFERENCES estados_usuarios (id_estado_usuario)
    ON UPDATE RESTRICT ON DELETE RESTRICT
    ) ENGINE=InnoDB
    COMMENT='Cuentas para ingresar al sistema; nunca almacena contrasenas en texto plano';

CREATE TABLE IF NOT EXISTS tokens_revocados (
                                                id_token_revocado INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                jti               VARCHAR(36) NOT NULL,
    id_usuario        INT UNSIGNED NOT NULL,
    expira_en         DATETIME(6) NOT NULL,
    revocado_en       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_tokens_revocados
    PRIMARY KEY (id_token_revocado),

    CONSTRAINT uq_tokens_revocados_jti
    UNIQUE (jti),

    CONSTRAINT fk_tokens_revocados_usuario
    FOREIGN KEY (id_usuario)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT
    ON DELETE CASCADE,

    INDEX idx_tokens_revocados_expira_en (expira_en)
    ) ENGINE=InnoDB
    COMMENT='JWT revocados hasta su fecha de expiracion';

-- =============================================================================
-- 2. PERSONAS Y CATALOGOS PARA FORMULARIOS
-- =============================================================================

CREATE TABLE IF NOT EXISTS tipos_identificacion (
                                                    id_tipo_identificacion  SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                    codigo                  VARCHAR(20) NOT NULL,
    nombre                  VARCHAR(60) NOT NULL,
    activo                  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tipos_identificacion PRIMARY KEY (id_tipo_identificacion),
    CONSTRAINT uq_tipos_identificacion_codigo UNIQUE (codigo),
    CONSTRAINT uq_tipos_identificacion_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Opciones de identificacion para listas del frontend';

CREATE TABLE IF NOT EXISTS centros_educativos (
                                                  id_centro_educativo     INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                  codigo                  VARCHAR(30) NULL,
    nombre                  VARCHAR(180) NOT NULL,
    tipo                    VARCHAR(50) NULL,
    provincia               VARCHAR(80) NULL,
    canton                  VARCHAR(80) NULL,
    activo                  BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_centros_educativos PRIMARY KEY (id_centro_educativo),
    CONSTRAINT uq_centros_educativos_codigo UNIQUE (codigo),
    CONSTRAINT uq_centros_educativos_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Centros educativos disponibles para seleccionar en formularios';

CREATE TABLE IF NOT EXISTS estudiantes (
                                           id_estudiante                   INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                           id_usuario                      INT UNSIGNED NULL,
                                           id_tipo_identificacion          SMALLINT UNSIGNED NOT NULL,
                                           numero_identificacion           VARCHAR(40) NOT NULL,
    nombres                         VARCHAR(100) NOT NULL,
    primer_apellido                 VARCHAR(80) NOT NULL,
    segundo_apellido                VARCHAR(80) NULL,
    fecha_nacimiento                DATE NULL,
    genero                          VARCHAR(30) NULL,
    telefono                        VARCHAR(30) NULL,
    correo                          VARCHAR(254) NULL,
    direccion                       VARCHAR(500) NULL,
    nivel_escolar                   VARCHAR(80) NULL,
    id_centro_educativo             INT UNSIGNED NULL,
    condiciones_relevantes          TEXT NULL,
    fecha_ingreso                   DATE NOT NULL,
    fecha_egreso                    DATE NULL,
    activo                          BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion  INT UNSIGNED NULL,
    creado_en                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_estudiantes PRIMARY KEY (id_estudiante),
    CONSTRAINT uq_estudiantes_usuario UNIQUE (id_usuario),
    CONSTRAINT uq_estudiantes_identificacion
    UNIQUE (id_tipo_identificacion, numero_identificacion),
    CONSTRAINT ck_estudiantes_fechas
    CHECK (fecha_egreso IS NULL OR fecha_egreso >= fecha_ingreso),
    CONSTRAINT fk_estudiantes_usuarios
    FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_estudiantes_tipos_identificacion
    FOREIGN KEY (id_tipo_identificacion)
    REFERENCES tipos_identificacion (id_tipo_identificacion)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_estudiantes_centros
    FOREIGN KEY (id_centro_educativo)
    REFERENCES centros_educativos (id_centro_educativo)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_estudiantes_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_estudiantes_nombre (primer_apellido, segundo_apellido, nombres),
    INDEX idx_estudiantes_activo (activo)
    ) ENGINE=InnoDB COMMENT='Informacion academica y de contacto de estudiantes';

CREATE TABLE IF NOT EXISTS profesores (
                                          id_profesor                     INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                          id_usuario                      INT UNSIGNED NULL,
                                          id_tipo_identificacion          SMALLINT UNSIGNED NOT NULL,
                                          numero_identificacion           VARCHAR(40) NOT NULL,
    nombres                         VARCHAR(100) NOT NULL,
    primer_apellido                 VARCHAR(80) NOT NULL,
    segundo_apellido                VARCHAR(80) NULL,
    telefono                        VARCHAR(30) NULL,
    correo                          VARCHAR(254) NULL,
    direccion                       VARCHAR(500) NULL,
    grado_academico                 VARCHAR(120) NULL,
    especialidad                    VARCHAR(180) NULL,
    empleador                       VARCHAR(180) NULL,
    fecha_ingreso                   DATE NOT NULL,
    fecha_salida                    DATE NULL,
    activo                          BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion  INT UNSIGNED NULL,
    creado_en                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_profesores PRIMARY KEY (id_profesor),
    CONSTRAINT uq_profesores_usuario UNIQUE (id_usuario),
    CONSTRAINT uq_profesores_identificacion
    UNIQUE (id_tipo_identificacion, numero_identificacion),
    CONSTRAINT ck_profesores_fechas
    CHECK (fecha_salida IS NULL OR fecha_salida >= fecha_ingreso),
    CONSTRAINT fk_profesores_usuarios
    FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_profesores_tipos_identificacion
    FOREIGN KEY (id_tipo_identificacion)
    REFERENCES tipos_identificacion (id_tipo_identificacion)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_profesores_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_profesores_nombre (primer_apellido, segundo_apellido, nombres),
    INDEX idx_profesores_activo (activo)
    ) ENGINE=InnoDB COMMENT='Datos de los profesores de la institucion';

CREATE TABLE IF NOT EXISTS encargados (
                                          id_encargado                INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                          id_tipo_identificacion      SMALLINT UNSIGNED NOT NULL,
                                          numero_identificacion       VARCHAR(40) NOT NULL,
    nombres                     VARCHAR(100) NOT NULL,
    primer_apellido             VARCHAR(80) NOT NULL,
    segundo_apellido            VARCHAR(80) NULL,
    telefono_principal          VARCHAR(30) NOT NULL,
    telefono_secundario         VARCHAR(30) NULL,
    correo                      VARCHAR(254) NULL,
    ocupacion                   VARCHAR(120) NULL,
    estado_civil                VARCHAR(40) NULL,
    direccion                   VARCHAR(500) NULL,
    activo                      BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en                   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_encargados PRIMARY KEY (id_encargado),

    CONSTRAINT uq_encargados_identificacion
    UNIQUE (id_tipo_identificacion, numero_identificacion),

    CONSTRAINT fk_encargados_tipos_identificacion
    FOREIGN KEY (id_tipo_identificacion)
    REFERENCES tipos_identificacion (id_tipo_identificacion)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

    INDEX idx_encargados_nombre
    (primer_apellido, segundo_apellido, nombres)
) ENGINE=InnoDB
COMMENT='Padres, madres, tutores u otros encargados de estudiantes';

CREATE TABLE IF NOT EXISTS estudiantes_encargados (
                                                      id_estudiante               INT UNSIGNED NOT NULL,
                                                      id_encargado                INT UNSIGNED NOT NULL,
                                                      parentesco                  VARCHAR(30) NOT NULL,
    es_responsable_principal    BOOLEAN NOT NULL DEFAULT FALSE,
    es_contacto_emergencia      BOOLEAN NOT NULL DEFAULT FALSE,
    autorizado_retiro           BOOLEAN NOT NULL DEFAULT FALSE,
    observaciones               VARCHAR(255) NULL,
    creado_en                   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_estudiantes_encargados
    PRIMARY KEY (id_estudiante, id_encargado),
    CONSTRAINT ck_estudiantes_encargados_parentesco
    CHECK (parentesco IN ('MADRE', 'PADRE', 'TUTOR', 'FAMILIAR', 'OTRO')),
    CONSTRAINT fk_estudiantes_encargados_estudiante
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_estudiantes_encargados_encargado
    FOREIGN KEY (id_encargado) REFERENCES encargados (id_encargado)
    ON UPDATE RESTRICT ON DELETE RESTRICT
    ) ENGINE=InnoDB COMMENT='Relacion de estudiantes con uno o varios encargados';

CREATE TABLE IF NOT EXISTS contactos_emergencia (
                                                    id_contacto_emergencia   INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                    id_estudiante            INT UNSIGNED NOT NULL,
                                                    id_encargado             INT UNSIGNED NULL,
                                                    nombre                   VARCHAR(180) NOT NULL,
    telefono                 VARCHAR(30) NOT NULL,
    relacion                 VARCHAR(80) NOT NULL,
    activo                   BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_contactos_emergencia
    PRIMARY KEY (id_contacto_emergencia),

    CONSTRAINT fk_contactos_emergencia_estudiante
    FOREIGN KEY (id_estudiante)
    REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT
    ON DELETE CASCADE,

    CONSTRAINT fk_contactos_emergencia_encargado
    FOREIGN KEY (id_encargado)
    REFERENCES encargados (id_encargado)
    ON UPDATE RESTRICT
    ON DELETE SET NULL,

    INDEX idx_contactos_emergencia_estudiante
(id_estudiante, activo),

    INDEX idx_contactos_emergencia_encargado
(id_encargado, activo)
    ) ENGINE=InnoDB
    COMMENT='Contactos de emergencia asociados al expediente del estudiante y opcionalmente a un encargado';

-- =============================================================================
-- 3. INSTRUMENTOS ESTUDIADOS (NO INVENTARIO NI PRESTAMOS)
-- =============================================================================

CREATE TABLE IF NOT EXISTS instrumentos (
                                            id_instrumento      SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                            codigo              VARCHAR(30) NOT NULL,
    nombre              VARCHAR(100) NOT NULL,
    descripcion         VARCHAR(255) NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_instrumentos PRIMARY KEY (id_instrumento),
    CONSTRAINT uq_instrumentos_codigo UNIQUE (codigo),
    CONSTRAINT uq_instrumentos_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Catalogo academico de tipos de instrumento; no representa unidades fisicas';

CREATE TABLE IF NOT EXISTS estudiantes_instrumentos (
                                                        id_estudiante_instrumento   INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                        id_estudiante               INT UNSIGNED NOT NULL,
                                                        id_instrumento              SMALLINT UNSIGNED NOT NULL,
                                                        fecha_inicio                DATE NOT NULL,
                                                        fecha_fin                   DATE NULL,
                                                        es_principal                BOOLEAN NOT NULL DEFAULT FALSE,
                                                        observaciones               VARCHAR(500) NULL,
    creado_en                   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_estudiantes_instrumentos
    PRIMARY KEY (id_estudiante_instrumento),
    CONSTRAINT uq_estudiantes_instrumentos_periodo
    UNIQUE (id_estudiante, id_instrumento, fecha_inicio),
    CONSTRAINT ck_estudiantes_instrumentos_fechas
    CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    CONSTRAINT fk_estudiantes_instrumentos_estudiante
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_estudiantes_instrumentos_instrumento
    FOREIGN KEY (id_instrumento) REFERENCES instrumentos (id_instrumento)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_estudiantes_instrumentos_actual
(id_estudiante, fecha_fin, es_principal)
    ) ENGINE=InnoDB COMMENT='Instrumentos que estudia cada estudiante y su historial';

-- =============================================================================
-- 4. PROGRAMAS, CURSOS Y MALLA CURRICULAR
-- =============================================================================

CREATE TABLE IF NOT EXISTS programas (
                                         id_programa                     INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                         codigo                          VARCHAR(30) NOT NULL,
    nombre                          VARCHAR(150) NOT NULL,
    descripcion                     TEXT NULL,
    activo                          BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion  INT UNSIGNED NULL,
    creado_en                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_programas PRIMARY KEY (id_programa),
    CONSTRAINT uq_programas_codigo UNIQUE (codigo),
    CONSTRAINT uq_programas_nombre UNIQUE (nombre),
    CONSTRAINT fk_programas_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL
    ) ENGINE=InnoDB COMMENT='Carreras, programas o rutas formativas ofrecidas';

CREATE TABLE IF NOT EXISTS planes_estudio (
                                              id_plan_estudio                 INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                              id_programa                     INT UNSIGNED NOT NULL,
                                              codigo                          VARCHAR(40) NOT NULL,
    nombre                          VARCHAR(160) NOT NULL,
    version                         VARCHAR(20) NOT NULL,
    fecha_vigencia_desde            DATE NOT NULL,
    fecha_vigencia_hasta            DATE NULL,
    nota_minima_aprobacion          DECIMAL(5,2) NOT NULL DEFAULT 70.00,
    vigente                         BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion  INT UNSIGNED NULL,
    creado_en                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_planes_estudio PRIMARY KEY (id_plan_estudio),
    CONSTRAINT uq_planes_estudio_codigo UNIQUE (codigo),
    CONSTRAINT uq_planes_estudio_programa_version UNIQUE (id_programa, version),
    CONSTRAINT ck_planes_estudio_fechas
    CHECK (fecha_vigencia_hasta IS NULL
           OR fecha_vigencia_hasta >= fecha_vigencia_desde),
    CONSTRAINT ck_planes_estudio_nota
    CHECK (nota_minima_aprobacion BETWEEN 0 AND 100),
    CONSTRAINT fk_planes_estudio_programas
    FOREIGN KEY (id_programa) REFERENCES programas (id_programa)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_planes_estudio_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL
    ) ENGINE=InnoDB COMMENT='Versiones de los planes de estudio de cada programa';

CREATE TABLE IF NOT EXISTS cursos (
                                      id_curso                        INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                      codigo                          VARCHAR(30) NOT NULL,
    nombre                          VARCHAR(160) NOT NULL,
    descripcion                     TEXT NULL,
    horas_totales                   SMALLINT UNSIGNED NULL,
    creditos                        DECIMAL(4,1) NULL,
    activo                          BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion  INT UNSIGNED NULL,
    creado_en                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_cursos PRIMARY KEY (id_curso),
    CONSTRAINT uq_cursos_codigo UNIQUE (codigo),
    CONSTRAINT ck_cursos_horas
    CHECK (horas_totales IS NULL OR horas_totales > 0),
    CONSTRAINT ck_cursos_creditos
    CHECK (creditos IS NULL OR creditos >= 0),
    CONSTRAINT fk_cursos_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_cursos_nombre (nombre),
    INDEX idx_cursos_activo (activo)
    ) ENGINE=InnoDB COMMENT='Catalogo general de cursos';

CREATE TABLE IF NOT EXISTS malla_curricular (
                                                id_malla                        INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                id_plan_estudio                 INT UNSIGNED NOT NULL,
                                                id_curso                        INT UNSIGNED NOT NULL,
                                                nivel                           SMALLINT UNSIGNED NOT NULL,
                                                orden_en_nivel                  SMALLINT UNSIGNED NOT NULL,
                                                obligatorio                     BOOLEAN NOT NULL DEFAULT TRUE,
                                                activo                          BOOLEAN NOT NULL DEFAULT TRUE,
                                                id_usuario_ultima_modificacion  INT UNSIGNED NULL,
                                                creado_en                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_malla_curricular PRIMARY KEY (id_malla),
    CONSTRAINT uq_malla_plan_curso UNIQUE (id_plan_estudio, id_curso),
    CONSTRAINT uq_malla_plan_posicion
    UNIQUE (id_plan_estudio, nivel, orden_en_nivel),
    CONSTRAINT ck_malla_nivel CHECK (nivel > 0),
    CONSTRAINT ck_malla_orden CHECK (orden_en_nivel > 0),
    CONSTRAINT fk_malla_planes_estudio
    FOREIGN KEY (id_plan_estudio)
    REFERENCES planes_estudio (id_plan_estudio)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_malla_cursos
    FOREIGN KEY (id_curso) REFERENCES cursos (id_curso)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_malla_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_malla_nivel (id_plan_estudio, nivel, orden_en_nivel)
    ) ENGINE=InnoDB COMMENT='Ubicacion de los cursos dentro de cada plan y nivel';

CREATE TABLE IF NOT EXISTS tipos_requisitos_cursos (
                                                       id_tipo_requisito   SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                       codigo              VARCHAR(30) NOT NULL,
    nombre              VARCHAR(80) NOT NULL,
    descripcion         VARCHAR(255) NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tipos_requisitos_cursos PRIMARY KEY (id_tipo_requisito),
    CONSTRAINT uq_tipos_requisitos_codigo UNIQUE (codigo),
    CONSTRAINT uq_tipos_requisitos_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Tipos de relacion entre cursos: requisito o correquisito';

CREATE TABLE IF NOT EXISTS requisitos_cursos (
                                                 id_requisito                    INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                 id_plan_estudio                 INT UNSIGNED NOT NULL,
                                                 id_curso                        INT UNSIGNED NOT NULL,
                                                 id_curso_requerido              INT UNSIGNED NOT NULL,
                                                 id_tipo_requisito               SMALLINT UNSIGNED NOT NULL,
                                                 nota_minima                     DECIMAL(5,2) NULL,
    activo                          BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion  INT UNSIGNED NULL,
    creado_en                       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_requisitos_cursos PRIMARY KEY (id_requisito),
    CONSTRAINT uq_requisitos_cursos
    UNIQUE (id_plan_estudio, id_curso, id_curso_requerido, id_tipo_requisito),
    CONSTRAINT ck_requisitos_cursos_distintos
    CHECK (id_curso <> id_curso_requerido),
    CONSTRAINT ck_requisitos_cursos_nota
    CHECK (nota_minima IS NULL OR nota_minima BETWEEN 0 AND 100),
    CONSTRAINT fk_requisitos_planes_estudio
    FOREIGN KEY (id_plan_estudio)
    REFERENCES planes_estudio (id_plan_estudio)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_requisitos_curso
    FOREIGN KEY (id_curso) REFERENCES cursos (id_curso)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_requisitos_curso_requerido
    FOREIGN KEY (id_curso_requerido) REFERENCES cursos (id_curso)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_requisitos_tipo
    FOREIGN KEY (id_tipo_requisito)
    REFERENCES tipos_requisitos_cursos (id_tipo_requisito)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_requisitos_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL
    ) ENGINE=InnoDB COMMENT='Prerrequisitos y correquisitos definidos dentro de cada plan';

-- =============================================================================
-- 5. PERIODOS, GRUPOS Y HORARIOS
-- =============================================================================

CREATE TABLE IF NOT EXISTS periodos_academicos (
                                                   id_periodo                 INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                   codigo                     VARCHAR(30) NOT NULL,
    nombre                     VARCHAR(100) NOT NULL,
    fecha_inicio               DATE NOT NULL,
    fecha_fin                  DATE NOT NULL,
    fecha_inicio_matricula     DATE NULL,
    fecha_fin_matricula        DATE NULL,
    estado                     VARCHAR(20) NOT NULL DEFAULT 'PLANIFICADO',
    id_usuario_ultima_modificacion  INT UNSIGNED NULL,
    creado_en                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_periodos_academicos PRIMARY KEY (id_periodo),
    CONSTRAINT uq_periodos_academicos_codigo UNIQUE (codigo),
    CONSTRAINT uq_periodos_academicos_nombre UNIQUE (nombre),
    CONSTRAINT ck_periodos_academicos_fechas
    CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT ck_periodos_academicos_matricula
    CHECK (fecha_inicio_matricula IS NULL
           OR fecha_fin_matricula IS NULL
           OR fecha_fin_matricula >= fecha_inicio_matricula),
    CONSTRAINT ck_periodos_academicos_estado
    CHECK (estado IN ('PLANIFICADO', 'MATRICULA', 'EN_CURSO',
           'CERRADO', 'CANCELADO')),
    CONSTRAINT fk_periodos_academicos_usuario_modifica
    FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_periodos_academicos_fechas (fecha_inicio, fecha_fin)
    ) ENGINE=InnoDB COMMENT='Ciclos lectivos o periodos academicos';

CREATE TABLE IF NOT EXISTS grupos (
                                      id_grupo               INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                      id_periodo             INT UNSIGNED NOT NULL,
                                      id_curso               INT UNSIGNED NOT NULL,
                                      id_profesor            INT UNSIGNED NULL,
                                      codigo                 VARCHAR(40) NOT NULL,
    cupo_maximo            SMALLINT UNSIGNED NOT NULL,
    modalidad              VARCHAR(20) NOT NULL DEFAULT 'PRESENCIAL',
    aula                    VARCHAR(80) NULL,
    fecha_inicio           DATE NULL,
    fecha_fin              DATE NULL,
    estado                 VARCHAR(20) NOT NULL DEFAULT 'PLANIFICADO',
    creado_en              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_grupos PRIMARY KEY (id_grupo),
    CONSTRAINT uq_grupos_periodo_codigo UNIQUE (id_periodo, codigo),
    CONSTRAINT ck_grupos_cupo CHECK (cupo_maximo > 0),
    CONSTRAINT ck_grupos_fechas
    CHECK (fecha_inicio IS NULL OR fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    CONSTRAINT ck_grupos_modalidad
    CHECK (modalidad IN ('PRESENCIAL', 'VIRTUAL', 'HIBRIDA')),
    CONSTRAINT ck_grupos_estado
    CHECK (estado IN ('PLANIFICADO', 'PUBLICADO', 'EN_CURSO',
           'CERRADO', 'CANCELADO')),
    CONSTRAINT fk_grupos_periodos
    FOREIGN KEY (id_periodo) REFERENCES periodos_academicos (id_periodo)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_grupos_cursos
    FOREIGN KEY (id_curso) REFERENCES cursos (id_curso)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_grupos_profesores
    FOREIGN KEY (id_profesor) REFERENCES profesores (id_profesor)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_grupos_curso_periodo (id_curso, id_periodo),
    INDEX idx_grupos_profesor_periodo (id_profesor, id_periodo)
    ) ENGINE=InnoDB COMMENT='Oferta concreta de un curso durante un periodo';

CREATE TABLE IF NOT EXISTS horarios_grupos (
                                               id_horario         INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                               id_grupo           INT UNSIGNED NOT NULL,
                                               dia_semana         TINYINT UNSIGNED NOT NULL,
                                               hora_inicio        TIME NOT NULL,
                                               hora_fin           TIME NOT NULL,
                                               aula               VARCHAR(80) NULL,
    CONSTRAINT pk_horarios_grupos PRIMARY KEY (id_horario),
    CONSTRAINT uq_horarios_grupos
    UNIQUE (id_grupo, dia_semana, hora_inicio),
    CONSTRAINT ck_horarios_grupos_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_horarios_grupos_horas CHECK (hora_fin > hora_inicio),
    CONSTRAINT fk_horarios_grupos_grupos
    FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo)
    ON UPDATE RESTRICT ON DELETE CASCADE
    ) ENGINE=InnoDB COMMENT='Horarios semanales; 1=lunes y 7=domingo';

-- =============================================================================
-- 6. DOCUMENTOS DEL EXPEDIENTE
-- =============================================================================

CREATE TABLE IF NOT EXISTS tipos_documentos (
                                                id_tipo_documento   SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                codigo              VARCHAR(40) NOT NULL,
    nombre              VARCHAR(100) NOT NULL,
    descripcion         VARCHAR(500) NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tipos_documentos PRIMARY KEY (id_tipo_documento),
    CONSTRAINT uq_tipos_documentos_codigo UNIQUE (codigo),
    CONSTRAINT uq_tipos_documentos_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Tipos de archivos que pueden adjuntarse al expediente';

CREATE TABLE IF NOT EXISTS documentos_estudiantes (
                                                      id_documento          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                      id_estudiante         INT UNSIGNED NOT NULL,
                                                      id_tipo_documento     SMALLINT UNSIGNED NOT NULL,
                                                      nombre_original       VARCHAR(255) NOT NULL,
    nombre_almacenado     VARCHAR(255) NOT NULL,
    ubicacion_archivo     VARCHAR(700) NOT NULL,
    tipo_mime             VARCHAR(120) NOT NULL,
    tamano_bytes          BIGINT UNSIGNED NOT NULL,
    sha256                CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    fecha_emision         DATE NULL,
    fecha_vencimiento     DATE NULL,
    es_actual             BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_carga      INT UNSIGNED NULL,
    cargado_en            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_documentos_estudiantes PRIMARY KEY (id_documento),
    CONSTRAINT uq_documentos_id_estudiante UNIQUE (id_documento, id_estudiante),
    CONSTRAINT ck_documentos_estudiantes_tamano CHECK (tamano_bytes > 0),
    CONSTRAINT ck_documentos_estudiantes_fechas
    CHECK (fecha_emision IS NULL OR fecha_vencimiento IS NULL
           OR fecha_vencimiento >= fecha_emision),
    CONSTRAINT ck_documentos_estudiantes_sha256
    CHECK (sha256 IS NULL OR sha256 REGEXP '^[0-9A-Fa-f]{64}$'),
    CONSTRAINT fk_documentos_estudiantes_estudiante
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_documentos_estudiantes_tipo
    FOREIGN KEY (id_tipo_documento)
    REFERENCES tipos_documentos (id_tipo_documento)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_documentos_estudiantes_usuario
    FOREIGN KEY (id_usuario_carga) REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_documentos_estudiantes_tipo
(id_estudiante, id_tipo_documento, es_actual),
    INDEX idx_documentos_estudiantes_sha256 (sha256)
    ) ENGINE=InnoDB COMMENT='Metadatos y ubicacion privada de documentos; no guarda el archivo binario';

-- =============================================================================
-- 7. AUTORIZACION PREVIA Y MATRICULA
-- =============================================================================

CREATE TABLE IF NOT EXISTS estados_autorizaciones_matricula (
                                                                id_estado_autorizacion  SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                                codigo                  VARCHAR(30) NOT NULL,
    nombre                  VARCHAR(80) NOT NULL,
    descripcion             VARCHAR(255) NULL,
    activo                  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_estados_autorizaciones PRIMARY KEY (id_estado_autorizacion),
    CONSTRAINT uq_estados_autorizaciones_codigo UNIQUE (codigo),
    CONSTRAINT uq_estados_autorizaciones_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Estados de la revision administrativa previa a la matricula';

CREATE TABLE IF NOT EXISTS autorizaciones_matricula (
                                                        id_autorizacion                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                        id_estudiante                       INT UNSIGNED NOT NULL,
                                                        id_periodo                          INT UNSIGNED NOT NULL,
                                                        id_estado_autorizacion              SMALLINT UNSIGNED NOT NULL,
                                                        id_documento_comprobante_matricula  BIGINT UNSIGNED NULL,
                                                        mensualidades_al_dia                BOOLEAN NOT NULL DEFAULT FALSE,
                                                        pago_matricula_confirmado           BOOLEAN NOT NULL DEFAULT FALSE,
                                                        documentacion_completa              BOOLEAN NOT NULL DEFAULT FALSE,
                                                        id_usuario_revisa                   INT UNSIGNED NULL,
                                                        fecha_revision                      DATETIME(6) NULL,
    observaciones                       VARCHAR(1500) NULL,
    creado_en                           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en                      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_autorizaciones_matricula PRIMARY KEY (id_autorizacion),
    CONSTRAINT uq_autorizaciones_estudiante_periodo
    UNIQUE (id_estudiante, id_periodo),
    CONSTRAINT uq_autorizaciones_comprobante
    UNIQUE (id_documento_comprobante_matricula),
    CONSTRAINT fk_autorizaciones_estudiantes
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_autorizaciones_periodos
    FOREIGN KEY (id_periodo) REFERENCES periodos_academicos (id_periodo)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_autorizaciones_estados
    FOREIGN KEY (id_estado_autorizacion)
    REFERENCES estados_autorizaciones_matricula (id_estado_autorizacion)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_autorizaciones_comprobante_estudiante
    FOREIGN KEY (id_documento_comprobante_matricula, id_estudiante)
    REFERENCES documentos_estudiantes (id_documento, id_estudiante)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_autorizaciones_usuario_revisa
    FOREIGN KEY (id_usuario_revisa) REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_autorizaciones_revision
(id_periodo, id_estado_autorizacion, fecha_revision)
    ) ENGINE=InnoDB COMMENT='Revision administrativa que habilita al estudiante antes de matricular';

CREATE TABLE IF NOT EXISTS estados_matriculas (
                                                  id_estado_matricula   SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                  codigo                VARCHAR(30) NOT NULL,
    nombre                VARCHAR(80) NOT NULL,
    descripcion           VARCHAR(255) NULL,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_estados_matriculas PRIMARY KEY (id_estado_matricula),
    CONSTRAINT uq_estados_matriculas_codigo UNIQUE (codigo),
    CONSTRAINT uq_estados_matriculas_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Estados disponibles para el tramite de matricula';

CREATE TABLE IF NOT EXISTS matriculas (
                                          id_matricula            INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                          id_autorizacion         BIGINT UNSIGNED NOT NULL,
                                          id_plan_estudio         INT UNSIGNED NOT NULL,
                                          id_estado_matricula     SMALLINT UNSIGNED NOT NULL,
                                          fecha_matricula         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    observaciones           VARCHAR(1000) NULL,
    creado_en               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_matriculas PRIMARY KEY (id_matricula),
    CONSTRAINT uq_matriculas_autorizacion UNIQUE (id_autorizacion),
    CONSTRAINT fk_matriculas_autorizaciones
    FOREIGN KEY (id_autorizacion)
    REFERENCES autorizaciones_matricula (id_autorizacion)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_matriculas_planes_estudio
    FOREIGN KEY (id_plan_estudio)
    REFERENCES planes_estudio (id_plan_estudio)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_matriculas_estados
    FOREIGN KEY (id_estado_matricula)
    REFERENCES estados_matriculas (id_estado_matricula)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_matriculas_estado (id_estado_matricula, fecha_matricula)
    ) ENGINE=InnoDB COMMENT='Matricula creada unicamente despues de la autorizacion administrativa';

CREATE TABLE IF NOT EXISTS matriculas_detalles (
                                                   id_detalle_matricula    INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                   id_matricula            INT UNSIGNED NOT NULL,
                                                   id_grupo                INT UNSIGNED NOT NULL,
                                                   retirado                BOOLEAN NOT NULL DEFAULT FALSE,
                                                   fecha_retiro            DATE NULL,
                                                   motivo_retiro           VARCHAR(500) NULL,
    creado_en               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_matriculas_detalles PRIMARY KEY (id_detalle_matricula),
    CONSTRAINT uq_matriculas_detalles_grupo UNIQUE (id_matricula, id_grupo),
    CONSTRAINT ck_matriculas_detalles_retiro
    CHECK (retirado = FALSE OR fecha_retiro IS NOT NULL),
    CONSTRAINT fk_matriculas_detalles_matriculas
    FOREIGN KEY (id_matricula) REFERENCES matriculas (id_matricula)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_matriculas_detalles_grupos
    FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_matriculas_detalles_grupo (id_grupo, retirado)
    ) ENGINE=InnoDB COMMENT='Grupos o cursos incluidos en cada matricula';

-- =============================================================================
-- 8. CALIFICACIONES, ASISTENCIA Y AVANCE CURRICULAR
-- =============================================================================

CREATE TABLE IF NOT EXISTS estados_academicos (
                                                  id_estado_academico   SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                  codigo                VARCHAR(30) NOT NULL,
    nombre                VARCHAR(80) NOT NULL,
    descripcion           VARCHAR(255) NULL,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_estados_academicos PRIMARY KEY (id_estado_academico),
    CONSTRAINT uq_estados_academicos_codigo UNIQUE (codigo),
    CONSTRAINT uq_estados_academicos_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Resultado o situacion academica del estudiante en un curso';

CREATE TABLE IF NOT EXISTS calificaciones (
                                              id_calificacion          INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                              id_detalle_matricula     INT UNSIGNED NOT NULL,
                                              id_estado_academico      SMALLINT UNSIGNED NOT NULL,
                                              id_profesor_registra     INT UNSIGNED NULL,
                                              nota_final               DECIMAL(5,2) NULL,
    valoracion_cualitativa   VARCHAR(500) NULL,
    publicada                BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_registro           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_calificaciones PRIMARY KEY (id_calificacion),
    CONSTRAINT uq_calificaciones_detalle UNIQUE (id_detalle_matricula),
    CONSTRAINT ck_calificaciones_nota
    CHECK (nota_final IS NULL OR nota_final BETWEEN 0 AND 100),
    CONSTRAINT ck_calificaciones_valor
    CHECK (nota_final IS NOT NULL OR valoracion_cualitativa IS NOT NULL),
    CONSTRAINT fk_calificaciones_detalles
    FOREIGN KEY (id_detalle_matricula)
    REFERENCES matriculas_detalles (id_detalle_matricula)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_calificaciones_estados
    FOREIGN KEY (id_estado_academico)
    REFERENCES estados_academicos (id_estado_academico)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_calificaciones_profesores
    FOREIGN KEY (id_profesor_registra) REFERENCES profesores (id_profesor)
    ON UPDATE RESTRICT ON DELETE SET NULL
    ) ENGINE=InnoDB COMMENT='Calificacion final de cada estudiante en un grupo';

CREATE TABLE IF NOT EXISTS sesiones_grupos (
                                               id_sesion          INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                               id_grupo           INT UNSIGNED NOT NULL,
                                               numero_sesion      SMALLINT UNSIGNED NOT NULL,
                                               fecha              DATE NOT NULL,
                                               hora_inicio        TIME NULL,
                                               hora_fin           TIME NULL,
                                               tema               VARCHAR(500) NULL,
    cancelada          BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_cancelacion VARCHAR(500) NULL,
    creado_en          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_sesiones_grupos PRIMARY KEY (id_sesion),
    CONSTRAINT uq_sesiones_grupos_numero UNIQUE (id_grupo, numero_sesion),
    CONSTRAINT ck_sesiones_grupos_numero CHECK (numero_sesion > 0),
    CONSTRAINT ck_sesiones_grupos_horas
    CHECK (hora_inicio IS NULL OR hora_fin IS NULL OR hora_fin > hora_inicio),
    CONSTRAINT ck_sesiones_grupos_cancelacion
    CHECK (cancelada = FALSE OR motivo_cancelacion IS NOT NULL),
    CONSTRAINT fk_sesiones_grupos_grupos
    FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    INDEX idx_sesiones_grupos_fecha (id_grupo, fecha)
    ) ENGINE=InnoDB COMMENT='Clases o sesiones realizadas para cada grupo';

CREATE TABLE IF NOT EXISTS tipos_asistencia (
                                                id_tipo_asistencia    SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                codigo                VARCHAR(35) NOT NULL,
    nombre                VARCHAR(80) NOT NULL,
    descripcion           VARCHAR(255) NULL,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tipos_asistencia PRIMARY KEY (id_tipo_asistencia),
    CONSTRAINT uq_tipos_asistencia_codigo UNIQUE (codigo),
    CONSTRAINT uq_tipos_asistencia_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Opciones para registrar la asistencia';

CREATE TABLE IF NOT EXISTS asistencias (
                                           id_asistencia          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                           id_sesion              INT UNSIGNED NOT NULL,
                                           id_detalle_matricula   INT UNSIGNED NOT NULL,
                                           id_tipo_asistencia     SMALLINT UNSIGNED NOT NULL,
                                           id_profesor_registra   INT UNSIGNED NULL,
                                           minutos_tardia         SMALLINT UNSIGNED NOT NULL DEFAULT 0,
                                           justificacion          VARCHAR(1000) NULL,
    registrado_en          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_asistencias PRIMARY KEY (id_asistencia),
    CONSTRAINT uq_asistencias_sesion_estudiante
    UNIQUE (id_sesion, id_detalle_matricula),
    CONSTRAINT fk_asistencias_sesiones
    FOREIGN KEY (id_sesion) REFERENCES sesiones_grupos (id_sesion)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_asistencias_detalles
    FOREIGN KEY (id_detalle_matricula)
    REFERENCES matriculas_detalles (id_detalle_matricula)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_asistencias_tipos
    FOREIGN KEY (id_tipo_asistencia)
    REFERENCES tipos_asistencia (id_tipo_asistencia)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_asistencias_profesores
    FOREIGN KEY (id_profesor_registra) REFERENCES profesores (id_profesor)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_asistencias_detalle (id_detalle_matricula, id_tipo_asistencia)
    ) ENGINE=InnoDB COMMENT='Asistencia de cada estudiante a cada sesion';

CREATE TABLE IF NOT EXISTS avances_curriculares (
                                                    id_avance             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                    id_grupo              INT UNSIGNED NOT NULL,
                                                    id_profesor_registra  INT UNSIGNED NULL,
                                                    fecha                 DATE NOT NULL,
                                                    tema_desarrollado     VARCHAR(500) NOT NULL,
    porcentaje_avance     DECIMAL(5,2) NULL,
    observaciones         VARCHAR(1000) NULL,
    creado_en             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_avances_curriculares PRIMARY KEY (id_avance),
    CONSTRAINT ck_avances_curriculares_porcentaje
    CHECK (porcentaje_avance IS NULL OR porcentaje_avance BETWEEN 0 AND 100),
    CONSTRAINT fk_avances_curriculares_grupos
    FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_avances_curriculares_profesores
    FOREIGN KEY (id_profesor_registra) REFERENCES profesores (id_profesor)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_avances_curriculares_grupo_fecha (id_grupo, fecha)
    ) ENGINE=InnoDB COMMENT='Seguimiento del contenido impartido y avance de cada grupo';

-- =============================================================================
-- 9. BECAS
-- =============================================================================

CREATE TABLE IF NOT EXISTS tipos_becas (
                                           id_tipo_beca       SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                           codigo             VARCHAR(30) NOT NULL,
    nombre             VARCHAR(100) NOT NULL,
    descripcion        VARCHAR(500) NULL,
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tipos_becas PRIMARY KEY (id_tipo_beca),
    CONSTRAINT uq_tipos_becas_codigo UNIQUE (codigo),
    CONSTRAINT uq_tipos_becas_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Tipos de beca disponibles para seleccionar';

CREATE TABLE IF NOT EXISTS becas (
                                     id_beca                INT UNSIGNED NOT NULL AUTO_INCREMENT,
                                     id_estudiante          INT UNSIGNED NOT NULL,
                                     id_periodo             INT UNSIGNED NULL,
                                     id_tipo_beca           SMALLINT UNSIGNED NOT NULL,
                                     porcentaje             DECIMAL(5,2) NOT NULL,
    fecha_inicio           DATE NOT NULL,
    fecha_fin              DATE NULL,
    motivo                 VARCHAR(1000) NULL,
    activa                 BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario_asigna      INT UNSIGNED NULL,
    creado_en              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_becas PRIMARY KEY (id_beca),
    CONSTRAINT ck_becas_porcentaje CHECK (porcentaje BETWEEN 0 AND 100),
    CONSTRAINT ck_becas_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    CONSTRAINT fk_becas_estudiantes
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_becas_periodos
    FOREIGN KEY (id_periodo) REFERENCES periodos_academicos (id_periodo)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_becas_tipos
    FOREIGN KEY (id_tipo_beca) REFERENCES tipos_becas (id_tipo_beca)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_becas_usuario_asigna
    FOREIGN KEY (id_usuario_asigna) REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_becas_estudiante_vigencia (id_estudiante, activa, fecha_inicio, fecha_fin)
    ) ENGINE=InnoDB COMMENT='Becas asignadas a los estudiantes';

-- =============================================================================
-- 10. CONFIRMACION DE MENSUALIDADES
-- =============================================================================

CREATE TABLE IF NOT EXISTS mensualidades_estudiantes (
                                                         id_mensualidad               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                         id_estudiante                INT UNSIGNED NOT NULL,
                                                         anio                         SMALLINT UNSIGNED NOT NULL,
                                                         mes                          TINYINT UNSIGNED NOT NULL,
                                                         id_documento_comprobante     BIGINT UNSIGNED NULL,
                                                         pagada                       BOOLEAN NOT NULL DEFAULT FALSE,
                                                         fecha_confirmacion           DATETIME(6) NULL,
    id_usuario_confirma          INT UNSIGNED NULL,
    observaciones                VARCHAR(1000) NULL,
    creado_en                    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_mensualidades_estudiantes PRIMARY KEY (id_mensualidad),
    CONSTRAINT uq_mensualidades_estudiante_mes UNIQUE (id_estudiante, anio, mes),
    CONSTRAINT uq_mensualidades_comprobante UNIQUE (id_documento_comprobante),
    CONSTRAINT ck_mensualidades_anio CHECK (anio BETWEEN 2000 AND 2200),
    CONSTRAINT ck_mensualidades_mes CHECK (mes BETWEEN 1 AND 12),
    CONSTRAINT ck_mensualidades_confirmacion
    CHECK (pagada = FALSE
           OR (id_documento_comprobante IS NOT NULL
           AND fecha_confirmacion IS NOT NULL
           AND id_usuario_confirma IS NOT NULL)),
    CONSTRAINT fk_mensualidades_estudiante
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_mensualidades_documento_estudiante
    FOREIGN KEY (id_documento_comprobante, id_estudiante)
    REFERENCES documentos_estudiantes (id_documento, id_estudiante)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_mensualidades_usuario_confirma
    FOREIGN KEY (id_usuario_confirma) REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_mensualidades_periodo (anio, mes, pagada)
    ) ENGINE=InnoDB COMMENT='Control mensual: comprobante recibido y confirmacion; no procesa dinero';

-- =============================================================================
-- 11. ALERTAS ACADEMICAS
-- =============================================================================

CREATE TABLE IF NOT EXISTS tipos_alertas (
                                             id_tipo_alerta     SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                             codigo             VARCHAR(40) NOT NULL,
    nombre             VARCHAR(100) NOT NULL,
    descripcion        VARCHAR(500) NULL,
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_tipos_alertas PRIMARY KEY (id_tipo_alerta),
    CONSTRAINT uq_tipos_alertas_codigo UNIQUE (codigo),
    CONSTRAINT uq_tipos_alertas_nombre UNIQUE (nombre)
    ) ENGINE=InnoDB COMMENT='Motivos de alerta academica o de seguimiento';

CREATE TABLE IF NOT EXISTS alertas_academicas (
                                                  id_alerta               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                  id_estudiante           INT UNSIGNED NOT NULL,
                                                  id_detalle_matricula    INT UNSIGNED NULL,
                                                  id_tipo_alerta          SMALLINT UNSIGNED NOT NULL,
                                                  fecha_alerta            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    detalle                 VARCHAR(1500) NOT NULL,
    atendida                BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_atencion          DATETIME(6) NULL,
    id_usuario_atiende      INT UNSIGNED NULL,
    observacion_atencion    VARCHAR(1500) NULL,
    CONSTRAINT pk_alertas_academicas PRIMARY KEY (id_alerta),
    CONSTRAINT ck_alertas_academicas_atencion
    CHECK (atendida = FALSE
           OR (fecha_atencion IS NOT NULL AND id_usuario_atiende IS NOT NULL)),
    CONSTRAINT fk_alertas_academicas_estudiantes
    FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante)
    ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_alertas_academicas_detalles
    FOREIGN KEY (id_detalle_matricula)
    REFERENCES matriculas_detalles (id_detalle_matricula)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT fk_alertas_academicas_tipos
    FOREIGN KEY (id_tipo_alerta) REFERENCES tipos_alertas (id_tipo_alerta)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_alertas_academicas_usuario
    FOREIGN KEY (id_usuario_atiende) REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX idx_alertas_academicas_pendientes (id_estudiante, atendida, fecha_alerta)
    ) ENGINE=InnoDB COMMENT='Alertas para seguimiento de estudiantes';

-- =============================================================================
-- 11.1 NOTIFICACIONES A ESTUDIANTES
-- =============================================================================

CREATE TABLE IF NOT EXISTS notificaciones_estudiantes (
                                                          id_notificacion      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                                          id_estudiante        INT UNSIGNED NOT NULL,
                                                          id_autorizacion      BIGINT UNSIGNED NULL,
                                                          tipo                 VARCHAR(40) NOT NULL,
                                                          asunto               VARCHAR(160) NOT NULL,
                                                          mensaje              VARCHAR(1500) NOT NULL,
                                                          leida                BOOLEAN NOT NULL DEFAULT FALSE,
                                                          fecha_notificacion   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                          fecha_lectura        DATETIME(6) NULL,

                                                          CONSTRAINT pk_notificaciones_estudiantes
                                                              PRIMARY KEY (id_notificacion),

                                                          CONSTRAINT uq_notificaciones_autorizacion
                                                              UNIQUE (id_autorizacion),

                                                          CONSTRAINT ck_notificaciones_tipo
                                                              CHECK (
                                                                  tipo IN (
                                                                           'MATRICULA_APROBADA',
                                                                           'MATRICULA_RECHAZADA'
                                                                      )
                                                                  ),

                                                          CONSTRAINT ck_notificaciones_lectura
                                                              CHECK (
                                                                  leida = FALSE
                                                                      OR fecha_lectura IS NOT NULL
                                                                  ),

                                                          CONSTRAINT fk_notificaciones_estudiante
                                                              FOREIGN KEY (id_estudiante)
                                                                  REFERENCES estudiantes (id_estudiante)
                                                                  ON UPDATE RESTRICT
                                                                  ON DELETE CASCADE,

                                                          CONSTRAINT fk_notificaciones_autorizacion
                                                              FOREIGN KEY (id_autorizacion)
                                                                  REFERENCES autorizaciones_matricula (id_autorizacion)
                                                                  ON UPDATE RESTRICT
                                                                  ON DELETE CASCADE,

                                                          INDEX idx_notificaciones_pendientes (
                                                                                               id_estudiante,
                                                                                               leida,
                                                                                               fecha_notificacion
                                                              )
) ENGINE=InnoDB
    COMMENT='Notificaciones internas dirigidas a estudiantes';

-- =============================================================================
-- 12. BITACORA DE CAMBIOS
-- =============================================================================

CREATE TABLE IF NOT EXISTS bitacoras (
                                         id_bitacora         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                         id_usuario          INT UNSIGNED NULL,
                                         tabla_afectada      VARCHAR(64) NOT NULL,
    id_registro         BIGINT UNSIGNED NOT NULL,
    accion              VARCHAR(10) NOT NULL,
    datos_anteriores    JSON NULL,
    datos_nuevos        JSON NULL,
    fecha_evento        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_bitacoras PRIMARY KEY (id_bitacora),
    CONSTRAINT ck_bitacoras_accion
    CHECK (accion IN ('INSERT', 'UPDATE', 'DELETE')),
    CONSTRAINT fk_bitacoras_usuarios
    FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario)
    ON UPDATE RESTRICT ON DELETE SET NULL,
    INDEX idx_bitacoras_registro
(tabla_afectada, id_registro, fecha_evento),
    INDEX idx_bitacoras_usuario_fecha (id_usuario, fecha_evento)
    ) ENGINE=InnoDB COMMENT='Historial automatico de cambios en entidades academicas seleccionadas';

-- =============================================================================
-- 13. DATOS INICIALES DE LOS CATALOGOS
-- INSERT IGNORE permite volver a ejecutar esta seccion sin duplicar codigos.
-- =============================================================================

INSERT IGNORE INTO roles (codigo, nombre, descripcion) VALUES
('ADMINISTRADOR', 'Administrador del sistema', 'Administra usuarios, catalogos y configuracion general'),
('DIRECTOR_ACADEMICO', 'Director academico', 'Supervisa programas, malla y actividad academica'),
('ADMINISTRATIVO', 'Personal administrativo', 'Gestiona estudiantes, documentos y matriculas'),
('PROFESOR', 'Profesor', 'Consulta grupos y registra notas, asistencia y avances'),
('JUNTA_DIRECTIVA', 'Junta directiva', 'Consulta reportes autorizados'),
('ESTUDIANTE', 'Estudiante', 'Consulta su informacion academica');

INSERT IGNORE INTO estados_usuarios (codigo, nombre, descripcion) VALUES
('ACTIVO', 'Activo', 'La cuenta puede utilizarse normalmente'),
('INACTIVO', 'Inactivo', 'La cuenta fue deshabilitada administrativamente'),
('BLOQUEADO', 'Bloqueado', 'La cuenta esta bloqueada temporal o permanentemente'),
('PENDIENTE', 'Pendiente', 'La cuenta aun debe completar su activacion');

INSERT IGNORE INTO tipos_identificacion (codigo, nombre) VALUES
('CEDULA', 'Cedula de identidad'),
('DIMEX', 'DIMEX'),
('PASAPORTE', 'Pasaporte'),
('OTRO', 'Otro documento');

INSERT IGNORE INTO instrumentos (codigo, nombre) VALUES
('PIANO', 'Piano'),
('GUITARRA', 'Guitarra'),
('VIOLIN', 'Violin'),
('VIOLONCHELO', 'Violonchelo'),
('FLAUTA', 'Flauta'),
('CLARINETE', 'Clarinete'),
('SAXOFON', 'Saxofon'),
('TROMPETA', 'Trompeta'),
('PERCUSION', 'Percusion');

INSERT IGNORE INTO tipos_requisitos_cursos (codigo, nombre, descripcion) VALUES
('PRERREQUISITO', 'Prerrequisito', 'Debe aprobarse antes de llevar el curso'),
('CORREQUISITO', 'Correquisito', 'Debe cursarse antes o durante el mismo periodo');

INSERT IGNORE INTO estados_autorizaciones_matricula
    (codigo, nombre, descripcion) VALUES
('PENDIENTE', 'Pendiente de revision', 'El personal administrativo debe revisar los requisitos'),
('AUTORIZADA', 'Autorizada', 'El estudiante puede realizar la matricula del periodo'),
('RECHAZADA', 'Rechazada', 'El estudiante aun no cumple los requisitos'),
('ANULADA', 'Anulada', 'Una autorizacion anterior fue anulada administrativamente');

INSERT IGNORE INTO estados_matriculas (codigo, nombre, descripcion) VALUES
('BORRADOR', 'Borrador', 'El estudiante esta seleccionando sus grupos'),
('REGISTRADA', 'Registrada', 'La matricula fue completada por un estudiante autorizado'),
('CANCELADA', 'Cancelada', 'La matricula fue anulada'),
('FINALIZADA', 'Finalizada', 'El periodo asociado a la matricula concluyo');

INSERT IGNORE INTO estados_academicos (codigo, nombre, descripcion) VALUES
('PENDIENTE', 'Pendiente', 'Todavia no existe una calificacion final'),
('EN_CURSO', 'En curso', 'El estudiante se encuentra cursando'),
('APROBADO', 'Aprobado', 'El curso fue aprobado'),
('REPROBADO', 'Reprobado', 'El curso no fue aprobado'),
('EQUIPARADO', 'Equiparado', 'El curso fue reconocido por equiparacion'),
('RETIRADO', 'Retirado', 'El estudiante se retiro del curso');

INSERT IGNORE INTO tipos_asistencia (codigo, nombre) VALUES
('PRESENTE', 'Presente'),
('TARDIA', 'Llegada tardia'),
('AUSENCIA_JUSTIFICADA', 'Ausencia justificada'),
('AUSENCIA_INJUSTIFICADA', 'Ausencia injustificada');

INSERT IGNORE INTO tipos_becas (codigo, nombre) VALUES
('SOCIOECONOMICA', 'Beca socioeconomica'),
('TALENTO', 'Beca por talento'),
('CONVENIO', 'Beca por convenio'),
('OTRA', 'Otra beca');

INSERT IGNORE INTO tipos_documentos (codigo, nombre, descripcion) VALUES
('IDENTIFICACION', 'Documento de identificacion', 'Copia del documento de identidad'),
('POLIZA', 'Poliza', 'Poliza o comprobante de seguro'),
('COMPROBANTE_MENSUALIDAD', 'Comprobante de mensualidad', 'Archivo aportado para validar una mensualidad'),
('COMPROBANTE_MATRICULA', 'Comprobante de pago de matricula', 'Archivo revisado antes de autorizar la matricula'),
('EQUIPARACION', 'Documento de equiparacion', 'Respaldo de un curso equiparado'),
('OTRO', 'Otro documento', 'Otro archivo del expediente estudiantil');

INSERT IGNORE INTO tipos_alertas (codigo, nombre) VALUES
('BAJO_RENDIMIENTO', 'Bajo rendimiento academico'),
('AUSENCIAS', 'Ausencias frecuentes'),
('RIESGO_DESERCION', 'Riesgo de desercion'),
('SEGUIMIENTO', 'Seguimiento general');

-- Los centros educativos son propios de la institucion y se cargan, por ejemplo:
-- INSERT INTO centros_educativos (codigo, nombre, tipo, provincia, canton)
-- VALUES ('CE-001', 'Nombre del centro educativo', 'COLEGIO', 'San Jose', 'Central');

-- =============================================================================
-- 14. TRIGGERS DE BITACORA Y VALIDACION
-- Se auditan solo las entidades solicitadas. Cada tabla tiene un trigger para
-- insertar, actualizar y eliminar. El id_usuario_ultima_modificacion debe ser
-- enviado por la aplicacion antes de guardar el cambio.
-- =============================================================================

DELIMITER $$

-- -----------------------------------------------------------------------------
-- Validacion 1: una autorizacion solo puede pasar a AUTORIZADA cuando la
-- revision administrativa esta completa.
-- -----------------------------------------------------------------------------

DROP TRIGGER IF EXISTS trg_autorizaciones_bi_validar$$
CREATE TRIGGER trg_autorizaciones_bi_validar
    BEFORE INSERT ON autorizaciones_matricula
    FOR EACH ROW
BEGIN
    DECLARE v_estado VARCHAR(30) DEFAULT NULL;
    DECLARE v_tipo_documento VARCHAR(40) DEFAULT NULL;

    SELECT codigo
    INTO v_estado
    FROM estados_autorizaciones_matricula
    WHERE id_estado_autorizacion = NEW.id_estado_autorizacion
        LIMIT 1;

    IF v_estado = 'AUTORIZADA' THEN
        IF NEW.mensualidades_al_dia = FALSE
           OR NEW.pago_matricula_confirmado = FALSE
           OR NEW.documentacion_completa = FALSE
           OR NEW.id_documento_comprobante_matricula IS NULL
           OR NEW.id_usuario_revisa IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'No se puede autorizar: la revision administrativa esta incompleta';
END IF;

SELECT td.codigo
INTO v_tipo_documento
FROM documentos_estudiantes AS de
         INNER JOIN tipos_documentos AS td
                    ON td.id_tipo_documento = de.id_tipo_documento
WHERE de.id_documento = NEW.id_documento_comprobante_matricula
  AND de.id_estudiante = NEW.id_estudiante
    LIMIT 1;

IF v_tipo_documento IS NULL
           OR v_tipo_documento <> 'COMPROBANTE_MATRICULA' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'El documento debe ser un comprobante de matricula del estudiante';
END IF;

        SET NEW.fecha_revision = COALESCE(NEW.fecha_revision, CURRENT_TIMESTAMP(6));
END IF;
END$$

DROP TRIGGER IF EXISTS trg_autorizaciones_bu_validar$$
CREATE TRIGGER trg_autorizaciones_bu_validar
    BEFORE UPDATE ON autorizaciones_matricula
    FOR EACH ROW
BEGIN
    DECLARE v_estado VARCHAR(30) DEFAULT NULL;
    DECLARE v_tipo_documento VARCHAR(40) DEFAULT NULL;

    SELECT codigo
    INTO v_estado
    FROM estados_autorizaciones_matricula
    WHERE id_estado_autorizacion = NEW.id_estado_autorizacion
        LIMIT 1;

    IF v_estado = 'AUTORIZADA' THEN
        IF NEW.mensualidades_al_dia = FALSE
           OR NEW.pago_matricula_confirmado = FALSE
           OR NEW.documentacion_completa = FALSE
           OR NEW.id_documento_comprobante_matricula IS NULL
           OR NEW.id_usuario_revisa IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'No se puede autorizar: la revision administrativa esta incompleta';
END IF;

SELECT td.codigo
INTO v_tipo_documento
FROM documentos_estudiantes AS de
         INNER JOIN tipos_documentos AS td
                    ON td.id_tipo_documento = de.id_tipo_documento
WHERE de.id_documento = NEW.id_documento_comprobante_matricula
  AND de.id_estudiante = NEW.id_estudiante
    LIMIT 1;

IF v_tipo_documento IS NULL
           OR v_tipo_documento <> 'COMPROBANTE_MATRICULA' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'El documento debe ser un comprobante de matricula del estudiante';
END IF;

        SET NEW.fecha_revision = COALESCE(NEW.fecha_revision, CURRENT_TIMESTAMP(6));
END IF;
END$$

-- -----------------------------------------------------------------------------
-- Validacion 2: no se crea una matricula sin autorizacion previa vigente.
-- -----------------------------------------------------------------------------

DROP TRIGGER IF EXISTS trg_matriculas_bi_validar_autorizacion$$
CREATE TRIGGER trg_matriculas_bi_validar_autorizacion
    BEFORE INSERT ON matriculas
    FOR EACH ROW
BEGIN
    DECLARE v_estado VARCHAR(30) DEFAULT NULL;

    SELECT e.codigo
    INTO v_estado
    FROM autorizaciones_matricula AS a
             INNER JOIN estados_autorizaciones_matricula AS e
                        ON e.id_estado_autorizacion = a.id_estado_autorizacion
    WHERE a.id_autorizacion = NEW.id_autorizacion
        LIMIT 1;

    IF v_estado IS NULL OR v_estado <> 'AUTORIZADA' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El estudiante no posee una autorizacion valida para matricular';
END IF;
END$$

-- -----------------------------------------------------------------------------
-- Validacion 3: el grupo elegido debe pertenecer al mismo periodo autorizado.
-- -----------------------------------------------------------------------------

DROP TRIGGER IF EXISTS trg_matriculas_detalles_bi_validar_periodo$$
CREATE TRIGGER trg_matriculas_detalles_bi_validar_periodo
    BEFORE INSERT ON matriculas_detalles
    FOR EACH ROW
BEGIN
    DECLARE v_periodo_autorizado INT UNSIGNED DEFAULT NULL;
    DECLARE v_periodo_grupo INT UNSIGNED DEFAULT NULL;

    SELECT a.id_periodo
    INTO v_periodo_autorizado
    FROM matriculas AS m
             INNER JOIN autorizaciones_matricula AS a
                        ON a.id_autorizacion = m.id_autorizacion
    WHERE m.id_matricula = NEW.id_matricula
        LIMIT 1;

    SELECT id_periodo
    INTO v_periodo_grupo
    FROM grupos
    WHERE id_grupo = NEW.id_grupo
        LIMIT 1;

    IF v_periodo_autorizado IS NULL
       OR v_periodo_grupo IS NULL
       OR v_periodo_autorizado <> v_periodo_grupo THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El grupo no pertenece al periodo autorizado para la matricula';
END IF;
END$$

DROP TRIGGER IF EXISTS trg_estudiantes_ai_bitacora$$
CREATE TRIGGER trg_estudiantes_ai_bitacora
    AFTER INSERT ON estudiantes
    FOR EACH ROW
BEGIN
    INSERT INTO bitacoras
    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
    VALUES
        (NEW.id_usuario_ultima_modificacion, 'estudiantes', NEW.id_estudiante, 'INSERT', NULL,
         JSON_OBJECT(
                 'id_estudiante', NEW.id_estudiante,
                 'numero_identificacion', NEW.numero_identificacion,
                 'nombres', NEW.nombres,
                 'primer_apellido', NEW.primer_apellido,
                 'segundo_apellido', NEW.segundo_apellido,
                 'id_centro_educativo', NEW.id_centro_educativo,
                 'activo', NEW.activo
         ));
    END$$

    DROP TRIGGER IF EXISTS trg_estudiantes_au_bitacora$$
    CREATE TRIGGER trg_estudiantes_au_bitacora
        AFTER UPDATE ON estudiantes
        FOR EACH ROW
    BEGIN
        INSERT INTO bitacoras
        (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
        VALUES
            (NEW.id_usuario_ultima_modificacion, 'estudiantes', NEW.id_estudiante, 'UPDATE',
             JSON_OBJECT(
                     'numero_identificacion', OLD.numero_identificacion,
                     'nombres', OLD.nombres,
                     'primer_apellido', OLD.primer_apellido,
                     'segundo_apellido', OLD.segundo_apellido,
                     'telefono', OLD.telefono,
                     'correo', OLD.correo,
                     'id_centro_educativo', OLD.id_centro_educativo,
                     'activo', OLD.activo
             ),
             JSON_OBJECT(
                     'numero_identificacion', NEW.numero_identificacion,
                     'nombres', NEW.nombres,
                     'primer_apellido', NEW.primer_apellido,
                     'segundo_apellido', NEW.segundo_apellido,
                     'telefono', NEW.telefono,
                     'correo', NEW.correo,
                     'id_centro_educativo', NEW.id_centro_educativo,
                     'activo', NEW.activo
             ));
        END$$

        DROP TRIGGER IF EXISTS trg_estudiantes_ad_bitacora$$
        CREATE TRIGGER trg_estudiantes_ad_bitacora
            AFTER DELETE ON estudiantes
            FOR EACH ROW
        BEGIN
            INSERT INTO bitacoras
            (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
            VALUES
                (OLD.id_usuario_ultima_modificacion, 'estudiantes', OLD.id_estudiante, 'DELETE',
                 JSON_OBJECT(
                         'id_estudiante', OLD.id_estudiante,
                         'numero_identificacion', OLD.numero_identificacion,
                         'nombres', OLD.nombres,
                         'primer_apellido', OLD.primer_apellido,
                         'segundo_apellido', OLD.segundo_apellido,
                         'activo', OLD.activo
                 ), NULL);
            END$$

            DROP TRIGGER IF EXISTS trg_profesores_ai_bitacora$$
            CREATE TRIGGER trg_profesores_ai_bitacora
                AFTER INSERT ON profesores
                FOR EACH ROW
            BEGIN
                INSERT INTO bitacoras
                (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                VALUES
                    (NEW.id_usuario_ultima_modificacion, 'profesores', NEW.id_profesor, 'INSERT', NULL,
                     JSON_OBJECT(
                             'id_profesor', NEW.id_profesor,
                             'numero_identificacion', NEW.numero_identificacion,
                             'nombres', NEW.nombres,
                             'primer_apellido', NEW.primer_apellido,
                             'segundo_apellido', NEW.segundo_apellido,
                             'especialidad', NEW.especialidad,
                             'activo', NEW.activo
                     ));
                END$$

                DROP TRIGGER IF EXISTS trg_profesores_au_bitacora$$
                CREATE TRIGGER trg_profesores_au_bitacora
                    AFTER UPDATE ON profesores
                    FOR EACH ROW
                BEGIN
                    INSERT INTO bitacoras
                    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                    VALUES
                        (NEW.id_usuario_ultima_modificacion, 'profesores', NEW.id_profesor, 'UPDATE',
                         JSON_OBJECT(
                                 'numero_identificacion', OLD.numero_identificacion,
                                 'nombres', OLD.nombres,
                                 'primer_apellido', OLD.primer_apellido,
                                 'segundo_apellido', OLD.segundo_apellido,
                                 'telefono', OLD.telefono,
                                 'correo', OLD.correo,
                                 'grado_academico', OLD.grado_academico,
                                 'especialidad', OLD.especialidad,
                                 'activo', OLD.activo
                         ),
                         JSON_OBJECT(
                                 'numero_identificacion', NEW.numero_identificacion,
                                 'nombres', NEW.nombres,
                                 'primer_apellido', NEW.primer_apellido,
                                 'segundo_apellido', NEW.segundo_apellido,
                                 'telefono', NEW.telefono,
                                 'correo', NEW.correo,
                                 'grado_academico', NEW.grado_academico,
                                 'especialidad', NEW.especialidad,
                                 'activo', NEW.activo
                         ));
                    END$$

                    DROP TRIGGER IF EXISTS trg_profesores_ad_bitacora$$
                    CREATE TRIGGER trg_profesores_ad_bitacora
                        AFTER DELETE ON profesores
                        FOR EACH ROW
                    BEGIN
                        INSERT INTO bitacoras
                        (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                        VALUES
                            (OLD.id_usuario_ultima_modificacion, 'profesores', OLD.id_profesor, 'DELETE',
                             JSON_OBJECT(
                                     'id_profesor', OLD.id_profesor,
                                     'numero_identificacion', OLD.numero_identificacion,
                                     'nombres', OLD.nombres,
                                     'primer_apellido', OLD.primer_apellido,
                                     'segundo_apellido', OLD.segundo_apellido,
                                     'especialidad', OLD.especialidad,
                                     'activo', OLD.activo
                             ), NULL);
                        END$$

                        DROP TRIGGER IF EXISTS trg_programas_ai_bitacora$$
                        CREATE TRIGGER trg_programas_ai_bitacora
                            AFTER INSERT ON programas
                            FOR EACH ROW
                        BEGIN
                            INSERT INTO bitacoras
                            (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                            VALUES
                                (NEW.id_usuario_ultima_modificacion, 'programas', NEW.id_programa, 'INSERT', NULL,
                                 JSON_OBJECT('codigo', NEW.codigo, 'nombre', NEW.nombre,
                                             'descripcion', NEW.descripcion, 'activo', NEW.activo));
                            END$$

                            DROP TRIGGER IF EXISTS trg_programas_au_bitacora$$
                            CREATE TRIGGER trg_programas_au_bitacora
                                AFTER UPDATE ON programas
                                FOR EACH ROW
                            BEGIN
                                INSERT INTO bitacoras
                                (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                VALUES
                                    (NEW.id_usuario_ultima_modificacion, 'programas', NEW.id_programa, 'UPDATE',
                                     JSON_OBJECT('codigo', OLD.codigo, 'nombre', OLD.nombre,
                                                 'descripcion', OLD.descripcion, 'activo', OLD.activo),
                                     JSON_OBJECT('codigo', NEW.codigo, 'nombre', NEW.nombre,
                                                 'descripcion', NEW.descripcion, 'activo', NEW.activo));
                                END$$

                                DROP TRIGGER IF EXISTS trg_programas_ad_bitacora$$
                                CREATE TRIGGER trg_programas_ad_bitacora
                                    AFTER DELETE ON programas
                                    FOR EACH ROW
                                BEGIN
                                    INSERT INTO bitacoras
                                    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                    VALUES
                                        (OLD.id_usuario_ultima_modificacion, 'programas', OLD.id_programa, 'DELETE',
                                         JSON_OBJECT('codigo', OLD.codigo, 'nombre', OLD.nombre,
                                                     'descripcion', OLD.descripcion, 'activo', OLD.activo), NULL);
                                    END$$

                                    DROP TRIGGER IF EXISTS trg_planes_estudio_ai_bitacora$$
                                    CREATE TRIGGER trg_planes_estudio_ai_bitacora
                                        AFTER INSERT ON planes_estudio
                                        FOR EACH ROW
                                    BEGIN
                                        INSERT INTO bitacoras
                                        (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                        VALUES
                                            (NEW.id_usuario_ultima_modificacion, 'planes_estudio', NEW.id_plan_estudio, 'INSERT', NULL,
                                             JSON_OBJECT(
                                                     'id_programa', NEW.id_programa,
                                                     'codigo', NEW.codigo,
                                                     'nombre', NEW.nombre,
                                                     'version', NEW.version,
                                                     'fecha_vigencia_desde', NEW.fecha_vigencia_desde,
                                                     'fecha_vigencia_hasta', NEW.fecha_vigencia_hasta,
                                                     'nota_minima_aprobacion', NEW.nota_minima_aprobacion,
                                                     'vigente', NEW.vigente
                                             ));
                                        END$$

                                        DROP TRIGGER IF EXISTS trg_planes_estudio_au_bitacora$$
                                        CREATE TRIGGER trg_planes_estudio_au_bitacora
                                            AFTER UPDATE ON planes_estudio
                                            FOR EACH ROW
                                        BEGIN
                                            INSERT INTO bitacoras
                                            (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                            VALUES
                                                (NEW.id_usuario_ultima_modificacion, 'planes_estudio', NEW.id_plan_estudio, 'UPDATE',
                                                 JSON_OBJECT(
                                                         'id_programa', OLD.id_programa,
                                                         'codigo', OLD.codigo,
                                                         'nombre', OLD.nombre,
                                                         'version', OLD.version,
                                                         'fecha_vigencia_desde', OLD.fecha_vigencia_desde,
                                                         'fecha_vigencia_hasta', OLD.fecha_vigencia_hasta,
                                                         'nota_minima_aprobacion', OLD.nota_minima_aprobacion,
                                                         'vigente', OLD.vigente
                                                 ),
                                                 JSON_OBJECT(
                                                         'id_programa', NEW.id_programa,
                                                         'codigo', NEW.codigo,
                                                         'nombre', NEW.nombre,
                                                         'version', NEW.version,
                                                         'fecha_vigencia_desde', NEW.fecha_vigencia_desde,
                                                         'fecha_vigencia_hasta', NEW.fecha_vigencia_hasta,
                                                         'nota_minima_aprobacion', NEW.nota_minima_aprobacion,
                                                         'vigente', NEW.vigente
                                                 ));
                                            END$$

                                            DROP TRIGGER IF EXISTS trg_planes_estudio_ad_bitacora$$
                                            CREATE TRIGGER trg_planes_estudio_ad_bitacora
                                                AFTER DELETE ON planes_estudio
                                                FOR EACH ROW
                                            BEGIN
                                                INSERT INTO bitacoras
                                                (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                VALUES
                                                    (OLD.id_usuario_ultima_modificacion, 'planes_estudio', OLD.id_plan_estudio, 'DELETE',
                                                     JSON_OBJECT(
                                                             'id_programa', OLD.id_programa,
                                                             'codigo', OLD.codigo,
                                                             'nombre', OLD.nombre,
                                                             'version', OLD.version,
                                                             'vigente', OLD.vigente
                                                     ), NULL);
                                                END$$

                                                DROP TRIGGER IF EXISTS trg_cursos_ai_bitacora$$
                                                CREATE TRIGGER trg_cursos_ai_bitacora
                                                    AFTER INSERT ON cursos
                                                    FOR EACH ROW
                                                BEGIN
                                                    INSERT INTO bitacoras
                                                    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                    VALUES
                                                        (NEW.id_usuario_ultima_modificacion, 'cursos', NEW.id_curso, 'INSERT', NULL,
                                                         JSON_OBJECT(
                                                                 'codigo', NEW.codigo,
                                                                 'nombre', NEW.nombre,
                                                                 'descripcion', NEW.descripcion,
                                                                 'horas_totales', NEW.horas_totales,
                                                                 'creditos', NEW.creditos,
                                                                 'activo', NEW.activo
                                                         ));
                                                    END$$

                                                    DROP TRIGGER IF EXISTS trg_cursos_au_bitacora$$
                                                    CREATE TRIGGER trg_cursos_au_bitacora
                                                        AFTER UPDATE ON cursos
                                                        FOR EACH ROW
                                                    BEGIN
                                                        INSERT INTO bitacoras
                                                        (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                        VALUES
                                                            (NEW.id_usuario_ultima_modificacion, 'cursos', NEW.id_curso, 'UPDATE',
                                                             JSON_OBJECT(
                                                                     'codigo', OLD.codigo,
                                                                     'nombre', OLD.nombre,
                                                                     'descripcion', OLD.descripcion,
                                                                     'horas_totales', OLD.horas_totales,
                                                                     'creditos', OLD.creditos,
                                                                     'activo', OLD.activo
                                                             ),
                                                             JSON_OBJECT(
                                                                     'codigo', NEW.codigo,
                                                                     'nombre', NEW.nombre,
                                                                     'descripcion', NEW.descripcion,
                                                                     'horas_totales', NEW.horas_totales,
                                                                     'creditos', NEW.creditos,
                                                                     'activo', NEW.activo
                                                             ));
                                                        END$$

                                                        DROP TRIGGER IF EXISTS trg_cursos_ad_bitacora$$
                                                        CREATE TRIGGER trg_cursos_ad_bitacora
                                                            AFTER DELETE ON cursos
                                                            FOR EACH ROW
                                                        BEGIN
                                                            INSERT INTO bitacoras
                                                            (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                            VALUES
                                                                (OLD.id_usuario_ultima_modificacion, 'cursos', OLD.id_curso, 'DELETE',
                                                                 JSON_OBJECT(
                                                                         'codigo', OLD.codigo,
                                                                         'nombre', OLD.nombre,
                                                                         'descripcion', OLD.descripcion,
                                                                         'horas_totales', OLD.horas_totales,
                                                                         'creditos', OLD.creditos,
                                                                         'activo', OLD.activo
                                                                 ), NULL);
                                                            END$$

DROP TRIGGER IF EXISTS trg_periodos_academicos_ai_bitacora$$
CREATE TRIGGER trg_periodos_academicos_ai_bitacora
    AFTER INSERT ON periodos_academicos
    FOR EACH ROW
BEGIN
    INSERT INTO bitacoras
    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
    VALUES
        (NEW.id_usuario_ultima_modificacion, 'periodos_academicos', NEW.id_periodo, 'INSERT', NULL,
         JSON_OBJECT(
                 'codigo', NEW.codigo,
                 'nombre', NEW.nombre,
                 'fecha_inicio', NEW.fecha_inicio,
                 'fecha_fin', NEW.fecha_fin,
                 'fecha_inicio_matricula', NEW.fecha_inicio_matricula,
                 'fecha_fin_matricula', NEW.fecha_fin_matricula,
                 'estado', NEW.estado
         ));
    END$$

DROP TRIGGER IF EXISTS trg_periodos_academicos_au_bitacora$$
CREATE TRIGGER trg_periodos_academicos_au_bitacora
    AFTER UPDATE ON periodos_academicos
    FOR EACH ROW
BEGIN
    INSERT INTO bitacoras
    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
    VALUES
        (NEW.id_usuario_ultima_modificacion, 'periodos_academicos', NEW.id_periodo, 'UPDATE',
         JSON_OBJECT(
                 'codigo', OLD.codigo,
                 'nombre', OLD.nombre,
                 'fecha_inicio', OLD.fecha_inicio,
                 'fecha_fin', OLD.fecha_fin,
                 'fecha_inicio_matricula', OLD.fecha_inicio_matricula,
                 'fecha_fin_matricula', OLD.fecha_fin_matricula,
                 'estado', OLD.estado
         ),
         JSON_OBJECT(
                 'codigo', NEW.codigo,
                 'nombre', NEW.nombre,
                 'fecha_inicio', NEW.fecha_inicio,
                 'fecha_fin', NEW.fecha_fin,
                 'fecha_inicio_matricula', NEW.fecha_inicio_matricula,
                 'fecha_fin_matricula', NEW.fecha_fin_matricula,
                 'estado', NEW.estado
         ));
    END$$

DROP TRIGGER IF EXISTS trg_periodos_academicos_ad_bitacora$$
CREATE TRIGGER trg_periodos_academicos_ad_bitacora
    AFTER DELETE ON periodos_academicos
    FOR EACH ROW
BEGIN
    INSERT INTO bitacoras
    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
    VALUES
        (OLD.id_usuario_ultima_modificacion, 'periodos_academicos', OLD.id_periodo, 'DELETE',
         JSON_OBJECT(
                 'codigo', OLD.codigo,
                 'nombre', OLD.nombre,
                 'fecha_inicio', OLD.fecha_inicio,
                 'fecha_fin', OLD.fecha_fin,
                 'fecha_inicio_matricula', OLD.fecha_inicio_matricula,
                 'fecha_fin_matricula', OLD.fecha_fin_matricula,
                 'estado', OLD.estado
         ), NULL);
    END$$

                                                            DROP TRIGGER IF EXISTS trg_malla_curricular_ai_bitacora$$
                                                            CREATE TRIGGER trg_malla_curricular_ai_bitacora
                                                                AFTER INSERT ON malla_curricular
                                                                FOR EACH ROW
                                                            BEGIN
                                                                INSERT INTO bitacoras
                                                                (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                                VALUES
                                                                    (NEW.id_usuario_ultima_modificacion, 'malla_curricular', NEW.id_malla, 'INSERT', NULL,
                                                                     JSON_OBJECT(
                                                                             'id_plan_estudio', NEW.id_plan_estudio,
                                                                             'id_curso', NEW.id_curso,
                                                                             'nivel', NEW.nivel,
                                                                             'orden_en_nivel', NEW.orden_en_nivel,
                                                                             'obligatorio', NEW.obligatorio,
                                                                             'activo', NEW.activo
                                                                     ));
                                                                END$$

                                                                DROP TRIGGER IF EXISTS trg_malla_curricular_au_bitacora$$
                                                                CREATE TRIGGER trg_malla_curricular_au_bitacora
                                                                    AFTER UPDATE ON malla_curricular
                                                                    FOR EACH ROW
                                                                BEGIN
                                                                    INSERT INTO bitacoras
                                                                    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                                    VALUES
                                                                        (NEW.id_usuario_ultima_modificacion, 'malla_curricular', NEW.id_malla, 'UPDATE',
                                                                         JSON_OBJECT(
                                                                                 'id_plan_estudio', OLD.id_plan_estudio,
                                                                                 'id_curso', OLD.id_curso,
                                                                                 'nivel', OLD.nivel,
                                                                                 'orden_en_nivel', OLD.orden_en_nivel,
                                                                                 'obligatorio', OLD.obligatorio,
                                                                                 'activo', OLD.activo
                                                                         ),
                                                                         JSON_OBJECT(
                                                                                 'id_plan_estudio', NEW.id_plan_estudio,
                                                                                 'id_curso', NEW.id_curso,
                                                                                 'nivel', NEW.nivel,
                                                                                 'orden_en_nivel', NEW.orden_en_nivel,
                                                                                 'obligatorio', NEW.obligatorio,
                                                                                 'activo', NEW.activo
                                                                         ));
                                                                    END$$

                                                                    DROP TRIGGER IF EXISTS trg_malla_curricular_ad_bitacora$$
                                                                    CREATE TRIGGER trg_malla_curricular_ad_bitacora
                                                                        AFTER DELETE ON malla_curricular
                                                                        FOR EACH ROW
                                                                    BEGIN
                                                                        INSERT INTO bitacoras
                                                                        (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                                        VALUES
                                                                            (OLD.id_usuario_ultima_modificacion, 'malla_curricular', OLD.id_malla, 'DELETE',
                                                                             JSON_OBJECT(
                                                                                     'id_plan_estudio', OLD.id_plan_estudio,
                                                                                     'id_curso', OLD.id_curso,
                                                                                     'nivel', OLD.nivel,
                                                                                     'orden_en_nivel', OLD.orden_en_nivel,
                                                                                     'obligatorio', OLD.obligatorio,
                                                                                     'activo', OLD.activo
                                                                             ), NULL);
                                                                        END$$

                                                                        DROP TRIGGER IF EXISTS trg_requisitos_cursos_ai_bitacora$$
                                                                        CREATE TRIGGER trg_requisitos_cursos_ai_bitacora
                                                                            AFTER INSERT ON requisitos_cursos
                                                                            FOR EACH ROW
                                                                        BEGIN
                                                                            INSERT INTO bitacoras
                                                                            (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                                            VALUES
                                                                                (NEW.id_usuario_ultima_modificacion, 'requisitos_cursos', NEW.id_requisito, 'INSERT', NULL,
                                                                                 JSON_OBJECT(
                                                                                         'id_plan_estudio', NEW.id_plan_estudio,
                                                                                         'id_curso', NEW.id_curso,
                                                                                         'id_curso_requerido', NEW.id_curso_requerido,
                                                                                         'id_tipo_requisito', NEW.id_tipo_requisito,
                                                                                         'nota_minima', NEW.nota_minima,
                                                                                         'activo', NEW.activo
                                                                                 ));
                                                                            END$$

                                                                            DROP TRIGGER IF EXISTS trg_requisitos_cursos_au_bitacora$$
                                                                            CREATE TRIGGER trg_requisitos_cursos_au_bitacora
                                                                                AFTER UPDATE ON requisitos_cursos
                                                                                FOR EACH ROW
                                                                            BEGIN
                                                                                INSERT INTO bitacoras
                                                                                (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                                                VALUES
                                                                                    (NEW.id_usuario_ultima_modificacion, 'requisitos_cursos', NEW.id_requisito, 'UPDATE',
                                                                                     JSON_OBJECT(
                                                                                             'id_plan_estudio', OLD.id_plan_estudio,
                                                                                             'id_curso', OLD.id_curso,
                                                                                             'id_curso_requerido', OLD.id_curso_requerido,
                                                                                             'id_tipo_requisito', OLD.id_tipo_requisito,
                                                                                             'nota_minima', OLD.nota_minima,
                                                                                             'activo', OLD.activo
                                                                                     ),
                                                                                     JSON_OBJECT(
                                                                                             'id_plan_estudio', NEW.id_plan_estudio,
                                                                                             'id_curso', NEW.id_curso,
                                                                                             'id_curso_requerido', NEW.id_curso_requerido,
                                                                                             'id_tipo_requisito', NEW.id_tipo_requisito,
                                                                                             'nota_minima', NEW.nota_minima,
                                                                                             'activo', NEW.activo
                                                                                     ));
                                                                                END$$

                                                                                DROP TRIGGER IF EXISTS trg_requisitos_cursos_ad_bitacora$$
                                                                                CREATE TRIGGER trg_requisitos_cursos_ad_bitacora
                                                                                    AFTER DELETE ON requisitos_cursos
                                                                                    FOR EACH ROW
                                                                                BEGIN
                                                                                    INSERT INTO bitacoras
                                                                                    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
                                                                                    VALUES
                                                                                        (OLD.id_usuario_ultima_modificacion, 'requisitos_cursos', OLD.id_requisito, 'DELETE',
                                                                                         JSON_OBJECT(
                                                                                                 'id_plan_estudio', OLD.id_plan_estudio,
                                                                                                 'id_curso', OLD.id_curso,
                                                                                                 'id_curso_requerido', OLD.id_curso_requerido,
                                                                                                 'id_tipo_requisito', OLD.id_tipo_requisito,
                                                                                                 'nota_minima', OLD.nota_minima,
                                                                                                 'activo', OLD.activo
                                                                                         ), NULL);
                                                                                    END$$

                                                                                    DELIMITER ;

-- =============================================================================
-- 15. VISTAS DE APOYO PARA CONSULTAS FRECUENTES
-- =============================================================================

CREATE OR REPLACE VIEW vw_estudiantes_instrumentos AS
                                                                                    SELECT
                                                                                        ei.id_estudiante_instrumento,
                                                                                        e.id_estudiante,
                                                                                        CONCAT_WS(' ', e.nombres, e.primer_apellido, e.segundo_apellido) AS estudiante,
                                                                                        i.id_instrumento,
                                                                                        i.codigo AS codigo_instrumento,
                                                                                        i.nombre AS instrumento,
                                                                                        ei.fecha_inicio,
                                                                                        ei.fecha_fin,
                                                                                        ei.es_principal,
                                                                                        CASE WHEN ei.fecha_fin IS NULL THEN 'ACTUAL' ELSE 'FINALIZADO' END AS estado
                                                                                    FROM estudiantes_instrumentos AS ei
                                                                                             INNER JOIN estudiantes AS e ON e.id_estudiante = ei.id_estudiante
                                                                                             INNER JOIN instrumentos AS i ON i.id_instrumento = ei.id_instrumento;

                                                                                    CREATE OR REPLACE VIEW vw_malla_curricular AS
                                                                                    SELECT
                                                                                        p.id_programa,
                                                                                        p.codigo AS codigo_programa,
                                                                                        p.nombre AS programa,
                                                                                        pe.id_plan_estudio,
                                                                                        pe.codigo AS codigo_plan,
                                                                                        pe.version,
                                                                                        mc.nivel,
                                                                                        mc.orden_en_nivel,
                                                                                        c.id_curso,
                                                                                        c.codigo AS codigo_curso,
                                                                                        c.nombre AS curso,
                                                                                        mc.obligatorio,
                                                                                        mc.activo
                                                                                    FROM malla_curricular AS mc
                                                                                             INNER JOIN planes_estudio AS pe ON pe.id_plan_estudio = mc.id_plan_estudio
                                                                                             INNER JOIN programas AS p ON p.id_programa = pe.id_programa
                                                                                             INNER JOIN cursos AS c ON c.id_curso = mc.id_curso;

                                                                                    CREATE OR REPLACE VIEW vw_autorizaciones_matricula AS
                                                                                    SELECT
                                                                                        a.id_autorizacion,
                                                                                        a.id_estudiante,
                                                                                        CONCAT_WS(' ', e.nombres, e.primer_apellido, e.segundo_apellido) AS estudiante,
                                                                                        a.id_periodo,
                                                                                        pa.codigo AS codigo_periodo,
                                                                                        pa.nombre AS periodo,
                                                                                        ea.codigo AS estado,
                                                                                        a.mensualidades_al_dia,
                                                                                        a.pago_matricula_confirmado,
                                                                                        a.documentacion_completa,
                                                                                        CASE WHEN ea.codigo = 'AUTORIZADA' THEN TRUE ELSE FALSE END AS puede_matricular,
                                                                                        a.id_documento_comprobante_matricula,
                                                                                        de.nombre_original AS comprobante_matricula,
                                                                                        a.fecha_revision,
                                                                                        u.correo AS revisado_por,
                                                                                        a.observaciones
                                                                                    FROM autorizaciones_matricula AS a
                                                                                             INNER JOIN estudiantes AS e ON e.id_estudiante = a.id_estudiante
                                                                                             INNER JOIN periodos_academicos AS pa ON pa.id_periodo = a.id_periodo
                                                                                             INNER JOIN estados_autorizaciones_matricula AS ea
                                                                                                        ON ea.id_estado_autorizacion = a.id_estado_autorizacion
                                                                                             LEFT JOIN documentos_estudiantes AS de
                                                                                                       ON de.id_documento = a.id_documento_comprobante_matricula
                                                                                             LEFT JOIN usuarios AS u ON u.id_usuario = a.id_usuario_revisa;

                                                                                    CREATE OR REPLACE VIEW vw_mensualidades_estudiantes AS
                                                                                    SELECT
                                                                                        me.id_mensualidad,
                                                                                        me.id_estudiante,
                                                                                        CONCAT_WS(' ', e.nombres, e.primer_apellido, e.segundo_apellido) AS estudiante,
                                                                                        me.anio,
                                                                                        me.mes,
                                                                                        CASE WHEN me.pagada = TRUE THEN 'PAGADA' ELSE 'PENDIENTE' END AS estado,
                                                                                        me.id_documento_comprobante,
                                                                                        de.nombre_original AS comprobante,
                                                                                        me.fecha_confirmacion,
                                                                                        me.id_usuario_confirma,
                                                                                        me.observaciones
                                                                                    FROM mensualidades_estudiantes AS me
                                                                                             INNER JOIN estudiantes AS e ON e.id_estudiante = me.id_estudiante
                                                                                             LEFT JOIN documentos_estudiantes AS de
                                                                                                       ON de.id_documento = me.id_documento_comprobante;

                                                                                    ALTER TABLE estudiantes
                                                                                        ADD COLUMN foto_perfil_ruta VARCHAR(700) NULL,
    ADD COLUMN foto_perfil_nombre VARCHAR(255) NULL,
    ADD COLUMN foto_perfil_mime VARCHAR(50) NULL,
    ADD COLUMN foto_perfil_tamano_bytes INT UNSIGNED NULL,
    ADD COLUMN foto_perfil_actualizada_en DATETIME(6) NULL,
    ADD CONSTRAINT ck_estudiantes_foto_mime
        CHECK (
            foto_perfil_mime IS NULL
            OR foto_perfil_mime IN (
                'image/jpeg',
                'image/png',
                'image/webp'
            )
        ),
    ADD CONSTRAINT ck_estudiantes_foto_tamano
        CHECK (
            foto_perfil_tamano_bytes IS NULL
            OR foto_perfil_tamano_bytes <= 5242880
        );


-- =============================================================================
-- FIN DEL SCRIPT
-- Resumen: 39 tablas, 21 triggers de auditoria, 4 triggers de validacion
--          y 4 vistas de consulta.
-- =============================================================================
