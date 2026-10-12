-- =====================================================================
--  SIGAC - Base de datos del sistema de gestión académica
--  Motor: MySQL 8
-- =====================================================================
--  Las tablas están ordenadas para que cada una se cree DESPUÉS de las
--  tablas a las que hace referencia (primero catálogos, después las
--  tablas principales y al final las que dependen de varias).
-- =====================================================================

-- ¡OJO! Descomentar esta línea BORRA la base completa con todos sus datos.
-- DROP DATABASE IF EXISTS sigac_db;

CREATE DATABASE IF NOT EXISTS sigac_db CHARACTER SET utf8mb4;
USE sigac_db;


-- =====================================================================
--  1. USUARIOS Y SEGURIDAD
-- =====================================================================

-- Roles generales del sistema; cada usuario posee uno
CREATE TABLE roles (
   id_rol         SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
   codigo         VARCHAR(40)  NOT NULL,
   nombre         VARCHAR(80)  NOT NULL,
   descripcion    VARCHAR(255),
   activo         BOOLEAN      NOT NULL DEFAULT TRUE,
   creado_en      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
   actualizado_en DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
   PRIMARY KEY (id_rol),
   CONSTRAINT uq_roles_codigo UNIQUE (codigo),
   CONSTRAINT uq_roles_nombre UNIQUE (nombre)
);

-- Catálogo de estados de las cuentas de usuario
CREATE TABLE estados_usuarios (
  id_estado_usuario SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  codigo            VARCHAR(30)  NOT NULL,
  nombre            VARCHAR(60)  NOT NULL,
  descripcion       VARCHAR(255),
  activo            BOOLEAN      NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_estado_usuario),
  CONSTRAINT uq_estados_usuarios_codigo UNIQUE (codigo),
  CONSTRAINT uq_estados_usuarios_nombre UNIQUE (nombre)
);

-- Cuentas para ingresar al sistema; nunca guarda contraseñas en texto plano
CREATE TABLE usuarios (
  id_usuario                 INT UNSIGNED      NOT NULL AUTO_INCREMENT,
  id_rol                     SMALLINT UNSIGNED NOT NULL,
  id_estado_usuario          SMALLINT UNSIGNED NOT NULL,
  nombre                     VARCHAR(150) NOT NULL DEFAULT '',
  cedula                     VARCHAR(40),
  correo                     VARCHAR(254) NOT NULL,
  hash_contrasena            VARCHAR(255) NOT NULL,
  requiere_cambio_contrasena BOOLEAN      NOT NULL DEFAULT TRUE,
  intentos_fallidos          TINYINT UNSIGNED NOT NULL DEFAULT 0,
  bloqueado_hasta            DATETIME,
  ultimo_acceso              DATETIME,
  creado_en                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_usuario),
  CONSTRAINT uq_usuarios_correo UNIQUE (correo),
  CONSTRAINT uq_usuarios_cedula UNIQUE (cedula),
  CONSTRAINT fk_usuarios_roles   FOREIGN KEY (id_rol)            REFERENCES roles (id_rol),
  CONSTRAINT fk_usuarios_estados FOREIGN KEY (id_estado_usuario) REFERENCES estados_usuarios (id_estado_usuario)
);

-- JWT invalidados al cerrar sesión; se pueden borrar cuando expira_en < NOW()
CREATE TABLE tokens_revocados (
  id_token_revocado INT UNSIGNED NOT NULL AUTO_INCREMENT,
  jti               VARCHAR(36)  NOT NULL,
  id_usuario        INT UNSIGNED NOT NULL,
  expira_en         DATETIME     NOT NULL,
  revocado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_token_revocado),
  CONSTRAINT uq_tokens_revocados_jti UNIQUE (jti),
  INDEX idx_tokens_revocados_expira (expira_en),
  CONSTRAINT fk_tokens_revocados_usuarios FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario)
);

-- Códigos de un solo uso enviados por correo (recuperar o cambiar contraseña).
-- Se guarda el hash del código, nunca el código en texto plano.
-- LimpiezaCodigosVerificacionJob borra los que vencieron hace más de un día
-- (no antes: se usan para limitar cuántos códigos se piden por hora)
CREATE TABLE codigos_verificacion (
      id_codigo_verificacion BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
      id_usuario             INT UNSIGNED     NOT NULL,
      proposito              VARCHAR(20)      NOT NULL,
      hash_codigo            VARCHAR(255)     NOT NULL,
      intentos               TINYINT UNSIGNED NOT NULL DEFAULT 0,
      expira_en              DATETIME         NOT NULL,
      usado_en               DATETIME,
      creado_en              DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id_codigo_verificacion),
      INDEX idx_codigos_verificacion_usuario (id_usuario, proposito, creado_en),
      INDEX idx_codigos_verificacion_expira  (expira_en),
      CONSTRAINT fk_codigos_verificacion_usuarios FOREIGN KEY (id_usuario) REFERENCES usuarios
          (id_usuario) ON DELETE CASCADE,
      CONSTRAINT ck_codigos_verificacion_proposito CHECK (proposito IN ('RECUPERACION', 'CAMBIO'))
);


-- Historial automático de cambios en algunas tablas académicas
CREATE TABLE bitacoras (
   id_bitacora      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
   id_usuario       INT UNSIGNED,
   tabla_afectada   VARCHAR(64)     NOT NULL,
   id_registro      BIGINT UNSIGNED NOT NULL,
   accion           VARCHAR(10)     NOT NULL,
   datos_anteriores JSON,
   datos_nuevos     JSON,
   fecha_evento     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
   PRIMARY KEY (id_bitacora),
   INDEX idx_bitacoras_registro      (tabla_afectada, id_registro, fecha_evento),
   INDEX idx_bitacoras_usuario_fecha (id_usuario, fecha_evento),
   CONSTRAINT fk_bitacoras_usuarios FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
   CONSTRAINT ck_bitacoras_accion CHECK (accion IN ('INSERT', 'UPDATE', 'DELETE'))
);


-- =====================================================================
--  2. CATÁLOGOS (listas de opciones para los formularios)
-- =====================================================================

-- Tipos de identificación (cédula, DIMEX, pasaporte...)
CREATE TABLE tipos_identificacion (
  id_tipo_identificacion SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  codigo                 VARCHAR(20) NOT NULL,
  nombre                 VARCHAR(60) NOT NULL,
  activo                 BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_tipo_identificacion),
  CONSTRAINT uq_tipos_identificacion_codigo UNIQUE (codigo),
  CONSTRAINT uq_tipos_identificacion_nombre UNIQUE (nombre)
);

-- División territorial de Costa Rica para la dirección de las personas.
-- Los datos (provincias, cantones y distritos) se cargan aparte.
CREATE TABLE IF NOT EXISTS provincia (
  id     TINYINT     NOT NULL PRIMARY KEY,
  nombre VARCHAR(20) NOT NULL
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS canton (
  codigo       SMALLINT    NOT NULL PRIMARY KEY,
  provincia_id TINYINT     NOT NULL,
  nombre       VARCHAR(50) NOT NULL,
  CONSTRAINT fk_canton_provincia FOREIGN KEY (provincia_id) REFERENCES provincia (id)
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS distrito (
  codigo        INT         NOT NULL PRIMARY KEY,
  canton_codigo SMALLINT    NOT NULL,
  nombre        VARCHAR(60) NOT NULL,
  CONSTRAINT fk_distrito_canton FOREIGN KEY (canton_codigo) REFERENCES canton (codigo)
) DEFAULT CHARSET = utf8mb4;

-- Centros educativos (escuelas/colegios) de donde vienen los estudiantes
CREATE TABLE centros_educativos (
  id_centro_educativo INT UNSIGNED NOT NULL AUTO_INCREMENT,
  codigo              VARCHAR(30),
  nombre              VARCHAR(180) NOT NULL,
  tipo                VARCHAR(50),
  provincia           VARCHAR(80),
  canton              VARCHAR(80),
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  creado_en           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_centro_educativo),
  CONSTRAINT uq_centros_educativos_nombre UNIQUE (nombre),
  CONSTRAINT uq_centros_educativos_codigo UNIQUE (codigo)
);

-- Grados académicos para el formulario de profesores
CREATE TABLE grados_academicos (
   id_grado_academico SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
   codigo             VARCHAR(30) NOT NULL,
   nombre             VARCHAR(60) NOT NULL,
   activo             BOOLEAN     NOT NULL DEFAULT TRUE,
   PRIMARY KEY (id_grado_academico),
   CONSTRAINT uq_grados_academicos_codigo UNIQUE (codigo),
   CONSTRAINT uq_grados_academicos_nombre UNIQUE (nombre)
);

-- Tipos de archivos que se pueden adjuntar al expediente
CREATE TABLE tipos_documentos (
  id_tipo_documento SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  codigo            VARCHAR(40)  NOT NULL,
  nombre            VARCHAR(100) NOT NULL,
  descripcion       VARCHAR(500),
  activo            BOOLEAN      NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_tipo_documento),
  CONSTRAINT uq_tipos_documentos_codigo UNIQUE (codigo),
  CONSTRAINT uq_tipos_documentos_nombre UNIQUE (nombre)
);

-- Tipos de beca disponibles
CREATE TABLE tipos_becas (
     id_tipo_beca SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
     codigo       VARCHAR(30)  NOT NULL,
     nombre       VARCHAR(100) NOT NULL,
     descripcion  VARCHAR(500),
     activo       BOOLEAN      NOT NULL DEFAULT TRUE,
     PRIMARY KEY (id_tipo_beca),
     CONSTRAINT uq_tipos_becas_codigo UNIQUE (codigo),
     CONSTRAINT uq_tipos_becas_nombre UNIQUE (nombre)
);

-- Motivos de alerta académica o de seguimiento
CREATE TABLE tipos_alertas (
   id_tipo_alerta SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
   codigo         VARCHAR(40)  NOT NULL,
   nombre         VARCHAR(100) NOT NULL,
   descripcion    VARCHAR(500),
   activo         BOOLEAN      NOT NULL DEFAULT TRUE,
   PRIMARY KEY (id_tipo_alerta),
   CONSTRAINT uq_tipos_alertas_codigo UNIQUE (codigo),
   CONSTRAINT uq_tipos_alertas_nombre UNIQUE (nombre)
);

-- Opciones para registrar la asistencia (presente, ausente, tardía...)
CREATE TABLE tipos_asistencia (
  id_tipo_asistencia SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  codigo             VARCHAR(35) NOT NULL,
  nombre             VARCHAR(80) NOT NULL,
  descripcion        VARCHAR(255),
  activo             BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_tipo_asistencia),
  CONSTRAINT uq_tipos_asistencia_codigo UNIQUE (codigo),
  CONSTRAINT uq_tipos_asistencia_nombre UNIQUE (nombre)
);

-- Tipo de relación entre cursos: requisito o correquisito
CREATE TABLE tipos_requisitos_cursos (
     id_tipo_requisito SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
     codigo            VARCHAR(30) NOT NULL,
     nombre            VARCHAR(80) NOT NULL,
     descripcion       VARCHAR(255),
     activo            BOOLEAN     NOT NULL DEFAULT TRUE,
     PRIMARY KEY (id_tipo_requisito),
     CONSTRAINT uq_tipos_requisitos_codigo UNIQUE (codigo),
     CONSTRAINT uq_tipos_requisitos_nombre UNIQUE (nombre)
);

-- Resultado del estudiante en un curso (aprobado, reprobado...)
CREATE TABLE estados_academicos (
    id_estado_academico SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo              VARCHAR(30) NOT NULL,
    nombre              VARCHAR(80) NOT NULL,
    descripcion         VARCHAR(255),
    activo              BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id_estado_academico),
    CONSTRAINT uq_estados_academicos_codigo UNIQUE (codigo),
    CONSTRAINT uq_estados_academicos_nombre UNIQUE (nombre)
);

-- Estados del trámite de matrícula
CREATE TABLE estados_matriculas (
    id_estado_matricula SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo              VARCHAR(30) NOT NULL,
    nombre              VARCHAR(80) NOT NULL,
    descripcion         VARCHAR(255),
    activo              BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id_estado_matricula),
    CONSTRAINT uq_estados_matriculas_codigo UNIQUE (codigo),
    CONSTRAINT uq_estados_matriculas_nombre UNIQUE (nombre)
);

-- Estados de la revisión administrativa previa a la matrícula
CREATE TABLE estados_autorizaciones_matricula (
  id_estado_autorizacion SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  codigo                 VARCHAR(30) NOT NULL,
  nombre                 VARCHAR(80) NOT NULL,
  descripcion            VARCHAR(255),
  activo                 BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_estado_autorizacion),
  CONSTRAINT uq_estados_autorizaciones_codigo UNIQUE (codigo),
  CONSTRAINT uq_estados_autorizaciones_nombre UNIQUE (nombre)
);

-- Tipos de instrumento que se enseñan (no son instrumentos físicos)
CREATE TABLE instrumentos (
      id_instrumento SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
      codigo         VARCHAR(30)  NOT NULL,
      nombre         VARCHAR(100) NOT NULL,
      descripcion    VARCHAR(255),
      activo         BOOLEAN      NOT NULL DEFAULT TRUE,
      creado_en      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id_instrumento),
      CONSTRAINT uq_instrumentos_codigo UNIQUE (codigo),
      CONSTRAINT uq_instrumentos_nombre UNIQUE (nombre)
);


-- =====================================================================
--  3. ESTRUCTURA ACADÉMICA (programas, planes, cursos, periodos)
-- =====================================================================

-- Carreras o programas que ofrece la escuela
CREATE TABLE programas (
   id_programa                    INT UNSIGNED NOT NULL AUTO_INCREMENT,
   codigo                         VARCHAR(30)  NOT NULL,
   nombre                         VARCHAR(150) NOT NULL,
   descripcion                    TEXT,
   activo                         BOOLEAN      NOT NULL DEFAULT TRUE,
   id_usuario_ultima_modificacion INT UNSIGNED,
   creado_en                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
   actualizado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
   PRIMARY KEY (id_programa),
   CONSTRAINT uq_programas_codigo UNIQUE (codigo),
   CONSTRAINT uq_programas_nombre UNIQUE (nombre),
   CONSTRAINT fk_programas_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
       REFERENCES usuarios (id_usuario) ON DELETE SET NULL
);

-- Versiones del plan de estudios de cada programa
CREATE TABLE planes_estudio (
    id_plan_estudio                INT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_programa                    INT UNSIGNED NOT NULL,
    codigo                         VARCHAR(40)  NOT NULL,
    nombre                         VARCHAR(160) NOT NULL,
    version                        VARCHAR(20)  NOT NULL,
    fecha_vigencia_desde           DATE         NOT NULL,
    fecha_vigencia_hasta           DATE,
    nota_minima_aprobacion         DECIMAL(5,2) NOT NULL DEFAULT 70.00,
    vigente                        BOOLEAN      NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion INT UNSIGNED,
    creado_en                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_plan_estudio),
    CONSTRAINT uq_planes_estudio_codigo           UNIQUE (codigo),
    CONSTRAINT uq_planes_estudio_programa_version UNIQUE (id_programa, version),
    CONSTRAINT fk_planes_estudio_programas FOREIGN KEY (id_programa) REFERENCES programas (id_programa),
    CONSTRAINT fk_planes_estudio_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
        REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT ck_planes_estudio_fechas CHECK (fecha_vigencia_hasta IS NULL OR fecha_vigencia_hasta >= fecha_vigencia_desde),
    CONSTRAINT ck_planes_estudio_nota   CHECK (nota_minima_aprobacion BETWEEN 0 AND 100)
);

-- Catálogo general de cursos
CREATE TABLE cursos (
    id_curso                       INT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo                         VARCHAR(30)  NOT NULL,
    nombre                         VARCHAR(160) NOT NULL,
    descripcion                    TEXT,
    horas_totales                  SMALLINT UNSIGNED,
    creditos                       DECIMAL(4,1),
    activo                         BOOLEAN      NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion INT UNSIGNED,
    creado_en                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    edad_minima                    TINYINT UNSIGNED,
    edad_maxima                    TINYINT UNSIGNED,
    PRIMARY KEY (id_curso),
    CONSTRAINT uq_cursos_codigo UNIQUE (codigo),
    INDEX idx_cursos_nombre (nombre),
    INDEX idx_cursos_activo (activo),
    CONSTRAINT fk_cursos_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
        REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT ck_cursos_creditos CHECK (creditos IS NULL OR creditos >= 0),
    CONSTRAINT ck_cursos_horas    CHECK (horas_totales IS NULL OR horas_totales > 0)
);

-- En qué nivel y posición va cada curso dentro de un plan
CREATE TABLE malla_curricular (
      id_malla                       INT UNSIGNED      NOT NULL AUTO_INCREMENT,
      id_plan_estudio                INT UNSIGNED      NOT NULL,
      id_curso                       INT UNSIGNED      NOT NULL,
      nivel                          SMALLINT UNSIGNED NOT NULL,
      orden_en_nivel                 SMALLINT UNSIGNED NOT NULL,
      obligatorio                    BOOLEAN NOT NULL DEFAULT TRUE,
      activo                         BOOLEAN NOT NULL DEFAULT TRUE,
      id_usuario_ultima_modificacion INT UNSIGNED,
      creado_en                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      actualizado_en                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id_malla),
      CONSTRAINT uq_malla_plan_curso    UNIQUE (id_plan_estudio, id_curso),
      CONSTRAINT uq_malla_plan_posicion UNIQUE (id_plan_estudio, nivel, orden_en_nivel),
      INDEX idx_malla_nivel (id_plan_estudio, nivel, orden_en_nivel),
      CONSTRAINT fk_malla_planes_estudio FOREIGN KEY (id_plan_estudio) REFERENCES planes_estudio (id_plan_estudio) ON DELETE CASCADE,
      CONSTRAINT fk_malla_cursos         FOREIGN KEY (id_curso)        REFERENCES cursos (id_curso),
      CONSTRAINT fk_malla_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
          REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
      CONSTRAINT ck_malla_nivel CHECK (nivel > 0),
      CONSTRAINT ck_malla_orden CHECK (orden_en_nivel > 0)
);

-- Requisitos y correquisitos de cada curso dentro de un plan
CREATE TABLE requisitos_cursos (
   id_requisito                   INT UNSIGNED      NOT NULL AUTO_INCREMENT,
   id_plan_estudio                INT UNSIGNED      NOT NULL,
   id_curso                       INT UNSIGNED      NOT NULL,
   id_curso_requerido             INT UNSIGNED      NOT NULL,
   id_tipo_requisito              SMALLINT UNSIGNED NOT NULL,
   nota_minima                    DECIMAL(5,2),
   activo                         BOOLEAN  NOT NULL DEFAULT TRUE,
   id_usuario_ultima_modificacion INT UNSIGNED,
   creado_en                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
   actualizado_en                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
   PRIMARY KEY (id_requisito),
   CONSTRAINT uq_requisitos_cursos UNIQUE (id_plan_estudio, id_curso, id_curso_requerido, id_tipo_requisito),
   CONSTRAINT fk_requisitos_planes_estudio FOREIGN KEY (id_plan_estudio)    REFERENCES planes_estudio (id_plan_estudio) ON DELETE CASCADE,
   CONSTRAINT fk_requisitos_curso          FOREIGN KEY (id_curso)           REFERENCES cursos (id_curso) ON DELETE CASCADE,
   CONSTRAINT fk_requisitos_curso_requerido FOREIGN KEY (id_curso_requerido) REFERENCES cursos (id_curso) ON DELETE CASCADE,
   CONSTRAINT fk_requisitos_tipo           FOREIGN KEY (id_tipo_requisito)  REFERENCES tipos_requisitos_cursos (id_tipo_requisito),
   CONSTRAINT fk_requisitos_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
       REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
   CONSTRAINT ck_requisitos_cursos_distintos CHECK (id_curso <> id_curso_requerido),
   CONSTRAINT ck_requisitos_cursos_nota      CHECK (nota_minima IS NULL OR nota_minima BETWEEN 0 AND 100)
);

-- Ciclos lectivos (periodos académicos)
CREATE TABLE periodos_academicos (
    id_periodo                     INT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo                         VARCHAR(30)  NOT NULL,
    nombre                         VARCHAR(100) NOT NULL,
    fecha_inicio                   DATE         NOT NULL,
    fecha_fin                      DATE         NOT NULL,
    fecha_inicio_matricula         DATE,
    fecha_fin_matricula            DATE,
    estado                         VARCHAR(20)  NOT NULL DEFAULT 'PLANIFICADO',
    id_usuario_ultima_modificacion INT UNSIGNED,
    creado_en                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_periodo),
    CONSTRAINT uq_periodos_academicos_codigo UNIQUE (codigo),
    CONSTRAINT uq_periodos_academicos_nombre UNIQUE (nombre),
    INDEX idx_periodos_academicos_fechas (fecha_inicio, fecha_fin),
    CONSTRAINT fk_periodos_academicos_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
    REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT ck_periodos_academicos_estado
    CHECK (estado IN ('PLANIFICADO', 'MATRICULA', 'EN_CURSO', 'CERRADO', 'CANCELADO')),
    CONSTRAINT ck_periodos_academicos_fechas CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT ck_periodos_academicos_matricula
    CHECK (fecha_inicio_matricula IS NULL OR fecha_fin_matricula IS NULL OR fecha_fin_matricula >= fecha_inicio_matricula)
);


-- =====================================================================
--  4. PERSONAS (profesores, estudiantes, encargados)
-- =====================================================================

-- Datos de los profesores
CREATE TABLE profesores (
    id_profesor                    INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    id_usuario                     INT UNSIGNED,
    id_tipo_identificacion         SMALLINT UNSIGNED NOT NULL,
    numero_identificacion          VARCHAR(40)  NOT NULL,
    nombres                        VARCHAR(100) NOT NULL,
    primer_apellido                VARCHAR(80)  NOT NULL,
    segundo_apellido               VARCHAR(80),
    telefono                       VARCHAR(30),
    correo                         VARCHAR(254),
    codigo_distrito                INT,
    -- Señas exactas; provincia y cantón se deducen del distrito
    direccion                      VARCHAR(500),
    grado_academico                VARCHAR(120),
    especialidad                   VARCHAR(180),
    empleador                      VARCHAR(180),
    clase_puesto                   VARCHAR(255) NOT NULL
                                                         DEFAULT 'Profesor de Enseñanza Técnica Profesional(III y IV Ciclos, Enseñanza Especial y Escuela Laboratorio) (G. de E)',
    fecha_ingreso                  DATE         NOT NULL,
    fecha_salida                   DATE,
    activo                         BOOLEAN      NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion INT UNSIGNED,
    creado_en                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_profesor),
    CONSTRAINT uq_profesores_identificacion UNIQUE (id_tipo_identificacion, numero_identificacion),
    CONSTRAINT uq_profesores_usuario        UNIQUE (id_usuario),
    INDEX idx_profesores_nombre (primer_apellido, segundo_apellido, nombres),
    INDEX idx_profesores_activo (activo),
    CONSTRAINT fk_profesores_usuarios FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT fk_profesores_distrito FOREIGN KEY (codigo_distrito) REFERENCES distrito (codigo),
    CONSTRAINT fk_profesores_tipos_identificacion FOREIGN KEY (id_tipo_identificacion)
        REFERENCES tipos_identificacion (id_tipo_identificacion),
    CONSTRAINT fk_profesores_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
        REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT ck_profesores_fechas CHECK (fecha_salida IS NULL OR fecha_salida >= fecha_ingreso)
);

-- Información académica y de contacto de los estudiantes
CREATE TABLE estudiantes (
     id_estudiante                  INT UNSIGNED      NOT NULL AUTO_INCREMENT,
     id_usuario                     INT UNSIGNED,
     id_tipo_identificacion         SMALLINT UNSIGNED NOT NULL,
     numero_identificacion          VARCHAR(40)  NOT NULL,
     nombres                        VARCHAR(100) NOT NULL,
     primer_apellido                VARCHAR(80)  NOT NULL,
     segundo_apellido               VARCHAR(80),
     fecha_nacimiento               DATE,
     genero                         VARCHAR(30),
     telefono                       VARCHAR(30),
     correo                         VARCHAR(254),
     codigo_distrito                INT,
     -- Señas exactas; provincia y cantón se deducen del distrito
     direccion                      VARCHAR(500),
     nivel_escolar                  VARCHAR(80),
     id_centro_educativo            INT UNSIGNED,
     centro_educativo               VARCHAR(180),
     condiciones_relevantes         TEXT,
     fecha_ingreso                  DATE         NOT NULL,
     fecha_egreso                   DATE,
     activo                         BOOLEAN      NOT NULL DEFAULT TRUE,
     id_usuario_ultima_modificacion INT UNSIGNED,
     creado_en                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
     actualizado_en                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     foto_perfil_ruta               VARCHAR(700),
     foto_perfil_nombre             VARCHAR(255),
     foto_perfil_mime               VARCHAR(50),
     foto_perfil_tamano_bytes       INT UNSIGNED,
     foto_perfil_actualizada_en     DATETIME,
     motivo_baja                    VARCHAR(500),
     fecha_baja                     DATETIME,
     PRIMARY KEY (id_estudiante),
     CONSTRAINT uq_estudiantes_identificacion UNIQUE (id_tipo_identificacion, numero_identificacion),
     CONSTRAINT uq_estudiantes_usuario        UNIQUE (id_usuario),
     INDEX idx_estudiantes_nombre (primer_apellido, segundo_apellido, nombres),
     INDEX idx_estudiantes_activo (activo),
     CONSTRAINT fk_estudiantes_usuarios FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
     CONSTRAINT fk_estudiantes_distrito FOREIGN KEY (codigo_distrito) REFERENCES distrito (codigo),
     CONSTRAINT fk_estudiantes_tipos_identificacion FOREIGN KEY (id_tipo_identificacion)
         REFERENCES tipos_identificacion (id_tipo_identificacion),
     CONSTRAINT fk_estudiantes_centros FOREIGN KEY (id_centro_educativo)
         REFERENCES centros_educativos (id_centro_educativo) ON DELETE SET NULL,
     CONSTRAINT fk_estudiantes_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
         REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
     CONSTRAINT ck_estudiantes_fechas CHECK (fecha_egreso IS NULL OR fecha_egreso >= fecha_ingreso),
     CONSTRAINT ck_estudiantes_foto_mime
         CHECK (foto_perfil_mime IS NULL OR foto_perfil_mime IN ('image/jpeg', 'image/png', 'image/webp')),
    -- 5242880 bytes = 5 MB
     CONSTRAINT ck_estudiantes_foto_tamano CHECK (foto_perfil_tamano_bytes IS NULL OR foto_perfil_tamano_bytes <= 5242880)
);

-- Padres, madres, tutores u otros encargados
CREATE TABLE encargados (
    id_encargado           INT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_tipo_identificacion SMALLINT UNSIGNED,
    numero_identificacion  VARCHAR(40),
    nombres                VARCHAR(100) NOT NULL,
    primer_apellido        VARCHAR(80)  NOT NULL,
    segundo_apellido       VARCHAR(80),
    telefono_principal     VARCHAR(30)  NOT NULL,
    telefono_secundario    VARCHAR(30),
    correo                 VARCHAR(254),
    ocupacion              VARCHAR(120),
    estado_civil           VARCHAR(40),
    direccion              VARCHAR(500),
    activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_encargado),
    CONSTRAINT uq_encargados_identificacion UNIQUE (id_tipo_identificacion, numero_identificacion),
    INDEX idx_encargados_nombre (primer_apellido, segundo_apellido, nombres),
    CONSTRAINT fk_encargados_tipos_identificacion FOREIGN KEY (id_tipo_identificacion)
        REFERENCES tipos_identificacion (id_tipo_identificacion)
);

-- Relación muchos a muchos: un estudiante puede tener varios encargados
CREATE TABLE estudiantes_encargados (
    id_estudiante            INT UNSIGNED NOT NULL,
    id_encargado             INT UNSIGNED NOT NULL,
    parentesco               VARCHAR(30)  NOT NULL,
    es_responsable_principal BOOLEAN      NOT NULL DEFAULT FALSE,
    es_contacto_emergencia   BOOLEAN      NOT NULL DEFAULT FALSE,
    autorizado_retiro        BOOLEAN      NOT NULL DEFAULT FALSE,
    observaciones            VARCHAR(255),
    creado_en                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_estudiante, id_encargado),
    CONSTRAINT fk_estudiantes_encargados_estudiante FOREIGN KEY (id_estudiante)
        REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
    CONSTRAINT fk_estudiantes_encargados_encargado FOREIGN KEY (id_encargado)
        REFERENCES encargados (id_encargado),
    CONSTRAINT ck_estudiantes_encargados_parentesco
        CHECK (parentesco IN ('MADRE', 'PADRE', 'TUTOR', 'FAMILIAR', 'OTRO'))
);

-- Instrumentos que estudia cada estudiante y su historial
CREATE TABLE estudiantes_instrumentos (
  id_estudiante_instrumento INT UNSIGNED      NOT NULL AUTO_INCREMENT,
  id_estudiante             INT UNSIGNED      NOT NULL,
  id_instrumento            SMALLINT UNSIGNED NOT NULL,
  id_profesor               INT UNSIGNED,
  fecha_inicio              DATE     NOT NULL,
  fecha_fin                 DATE,
  observaciones             VARCHAR(500),
  creado_en                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  es_principal              BOOLEAN  NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id_estudiante_instrumento),
  CONSTRAINT uq_estudiantes_instrumentos_periodo UNIQUE (id_estudiante, id_instrumento, fecha_inicio),
  INDEX idx_estudiantes_instrumentos_actual (id_estudiante, fecha_fin),
  CONSTRAINT fk_estudiantes_instrumentos_estudiante FOREIGN KEY (id_estudiante)
      REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
  CONSTRAINT fk_estudiantes_instrumentos_instrumento FOREIGN KEY (id_instrumento)
      REFERENCES instrumentos (id_instrumento),
  CONSTRAINT fk_estudiantes_instrumentos_profesor FOREIGN KEY (id_profesor)
      REFERENCES profesores (id_profesor) ON DELETE SET NULL,
  CONSTRAINT ck_estudiantes_instrumentos_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);

-- Planes de estudio en los que está inscrito cada estudiante
-- (limita los cursos que puede matricular)
CREATE TABLE estudiantes_planes_estudio (
    id_estudiante_plan             INT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_estudiante                  INT UNSIGNED NOT NULL,
    id_plan_estudio                INT UNSIGNED NOT NULL,
    fecha_inscripcion              DATE     NOT NULL DEFAULT (CURRENT_DATE),
    activo                         BOOLEAN  NOT NULL DEFAULT TRUE,
    id_usuario_ultima_modificacion INT UNSIGNED,
    creado_en                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_estudiante_plan),
    CONSTRAINT uq_estudiantes_planes_estudio UNIQUE (id_estudiante, id_plan_estudio),
    INDEX idx_estudiantes_planes_plan (id_plan_estudio, activo),
    CONSTRAINT fk_estudiantes_planes_estudiantes FOREIGN KEY (id_estudiante)
        REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
    CONSTRAINT fk_estudiantes_planes_planes FOREIGN KEY (id_plan_estudio)
        REFERENCES planes_estudio (id_plan_estudio),
    CONSTRAINT fk_estudiantes_planes_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion)
        REFERENCES usuarios (id_usuario) ON DELETE SET NULL
);


-- =====================================================================
--  5. EXPEDIENTE, BECAS Y PAGOS
-- =====================================================================

-- Datos de los archivos subidos (el archivo en sí se guarda fuera de la BD)
CREATE TABLE documentos_estudiantes (
    id_documento      BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    id_estudiante     INT UNSIGNED      NOT NULL,
    id_tipo_documento SMALLINT UNSIGNED NOT NULL,
    nombre_original   VARCHAR(255)      NOT NULL,
    nombre_almacenado VARCHAR(255)      NOT NULL,
    ubicacion_archivo VARCHAR(700)      NOT NULL,
    tipo_mime         VARCHAR(120)      NOT NULL,
    tamano_bytes      BIGINT UNSIGNED   NOT NULL,
-- El hash solo tiene caracteres hexadecimales: se guarda en ASCII (1 byte por
-- carácter en lugar de hasta 4 en utf8mb4) y se compara exacto (ascii_bin)
    sha256            CHAR(64) CHARACTER SET ascii COLLATE ascii_bin,
    fecha_emision     DATE,
    fecha_vencimiento DATE,
    es_actual         BOOLEAN           NOT NULL DEFAULT TRUE,
    id_usuario_carga  INT UNSIGNED,
    cargado_en        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_documento),
-- Esta combinación única permite que otras tablas verifiquen que el
-- documento pertenece al mismo estudiante (ver mensualidades y autorizaciones)
    CONSTRAINT uq_documentos_id_estudiante UNIQUE (id_documento, id_estudiante),
    INDEX idx_documentos_estudiantes_tipo   (id_estudiante, id_tipo_documento, es_actual),
    INDEX idx_documentos_estudiantes_sha256 (sha256),
    CONSTRAINT fk_documentos_estudiantes_estudiante FOREIGN KEY (id_estudiante)
        REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
    CONSTRAINT fk_documentos_estudiantes_tipo FOREIGN KEY (id_tipo_documento)
        REFERENCES tipos_documentos (id_tipo_documento),
    CONSTRAINT fk_documentos_estudiantes_usuario FOREIGN KEY (id_usuario_carga)
        REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT ck_documentos_estudiantes_fechas
        CHECK (fecha_emision IS NULL OR fecha_vencimiento IS NULL OR fecha_vencimiento >= fecha_emision),
-- El hash SHA-256 son 64 caracteres hexadecimales
    CONSTRAINT ck_documentos_estudiantes_sha256 CHECK (sha256 IS NULL OR sha256 REGEXP '^[0-9A-Fa-f]{64}$'),
    CONSTRAINT ck_documentos_estudiantes_tamano CHECK (tamano_bytes > 0)
);

-- Becas asignadas a los estudiantes
CREATE TABLE becas (
   id_beca           INT UNSIGNED      NOT NULL AUTO_INCREMENT,
   id_estudiante     INT UNSIGNED      NOT NULL,
   id_periodo        INT UNSIGNED,
   id_tipo_beca      SMALLINT UNSIGNED NOT NULL,
   porcentaje        DECIMAL(5,2)      NOT NULL,
   fecha_inicio      DATE              NOT NULL,
   fecha_fin         DATE,
   motivo            VARCHAR(1000),
   activa            BOOLEAN           NOT NULL DEFAULT TRUE,
   id_usuario_asigna INT UNSIGNED,
   creado_en         DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
   actualizado_en    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
   PRIMARY KEY (id_beca),
   INDEX idx_becas_estudiante_vigencia (id_estudiante, activa, fecha_inicio, fecha_fin),
   CONSTRAINT fk_becas_estudiantes FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
   CONSTRAINT fk_becas_periodos    FOREIGN KEY (id_periodo)    REFERENCES periodos_academicos (id_periodo) ON DELETE SET NULL,
   CONSTRAINT fk_becas_tipos       FOREIGN KEY (id_tipo_beca)  REFERENCES tipos_becas (id_tipo_beca),
   CONSTRAINT fk_becas_usuario_asigna FOREIGN KEY (id_usuario_asigna) REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
   CONSTRAINT ck_becas_fechas     CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
   CONSTRAINT ck_becas_porcentaje CHECK (porcentaje BETWEEN 0 AND 100)
);

-- Control de mensualidades: se registra el comprobante y quién lo confirma
-- (el sistema no procesa dinero)
CREATE TABLE mensualidades_estudiantes (
   id_mensualidad           BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
   id_estudiante            INT UNSIGNED      NOT NULL,
   id_periodo               INT UNSIGNED,
   anio                     SMALLINT UNSIGNED NOT NULL,
   mes                      TINYINT UNSIGNED  NOT NULL,
   monto                    DECIMAL(10,2),
   fecha_pago               DATE,
   id_documento_comprobante BIGINT UNSIGNED,
   pagada                   BOOLEAN  NOT NULL DEFAULT FALSE,
   fecha_confirmacion       DATETIME,
   id_usuario_confirma      INT UNSIGNED,
   observaciones            VARCHAR(1000),
   creado_en                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
   actualizado_en           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
   PRIMARY KEY (id_mensualidad),
   CONSTRAINT uq_mensualidades_estudiante_mes UNIQUE (id_estudiante, anio, mes),
   CONSTRAINT uq_mensualidades_comprobante    UNIQUE (id_documento_comprobante),
   INDEX idx_mensualidades_periodo    (anio, mes, pagada),
   INDEX idx_mensualidades_id_periodo (id_periodo),
   CONSTRAINT fk_mensualidades_estudiante FOREIGN KEY (id_estudiante)
       REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
   CONSTRAINT fk_mensualidades_periodo FOREIGN KEY (id_periodo)
       REFERENCES periodos_academicos (id_periodo) ON DELETE SET NULL,
-- El comprobante debe ser un documento del MISMO estudiante
   CONSTRAINT fk_mensualidades_documento_estudiante FOREIGN KEY (id_documento_comprobante, id_estudiante)
       REFERENCES documentos_estudiantes (id_documento, id_estudiante),
   CONSTRAINT fk_mensualidades_usuario_confirma FOREIGN KEY (id_usuario_confirma)
       REFERENCES usuarios (id_usuario),
   CONSTRAINT ck_mensualidades_anio  CHECK (anio BETWEEN 2000 AND 2200),
   CONSTRAINT ck_mensualidades_mes   CHECK (mes BETWEEN 1 AND 12),
   CONSTRAINT ck_mensualidades_monto CHECK (monto IS NULL OR monto >= 0),
-- Si está pagada, tienen que estar todos los datos del pago
   CONSTRAINT ck_mensualidades_confirmacion CHECK (
       pagada = FALSE
           OR (id_documento_comprobante IS NOT NULL
           AND fecha_confirmacion IS NOT NULL
           AND id_usuario_confirma IS NOT NULL
           AND monto IS NOT NULL
           AND fecha_pago IS NOT NULL)
       )
);


-- =====================================================================
--  6. MATRÍCULA
-- =====================================================================

-- Revisión administrativa que habilita al estudiante antes de matricular
CREATE TABLE autorizaciones_matricula (
  id_autorizacion                    BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  id_estudiante                      INT UNSIGNED      NOT NULL,
  id_periodo                         INT UNSIGNED      NOT NULL,
  id_estado_autorizacion             SMALLINT UNSIGNED NOT NULL,
  id_documento_comprobante_matricula BIGINT UNSIGNED,
  mensualidades_al_dia               BOOLEAN  NOT NULL DEFAULT FALSE,
  pago_matricula_confirmado          BOOLEAN  NOT NULL DEFAULT FALSE,
  documentacion_completa             BOOLEAN  NOT NULL DEFAULT FALSE,
  estudiante_activo                  BOOLEAN  NOT NULL DEFAULT FALSE,
  datos_completos                    BOOLEAN  NOT NULL DEFAULT FALSE,
  id_usuario_revisa                  INT UNSIGNED,
  fecha_revision                     DATETIME,
  observaciones                      VARCHAR(1500),
  creado_en                          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_autorizacion),
  CONSTRAINT uq_autorizaciones_estudiante_periodo UNIQUE (id_estudiante, id_periodo),
  CONSTRAINT uq_autorizaciones_comprobante        UNIQUE (id_documento_comprobante_matricula),
  INDEX idx_autorizaciones_revision (id_periodo, id_estado_autorizacion, fecha_revision),
  CONSTRAINT fk_autorizaciones_estudiantes FOREIGN KEY (id_estudiante) REFERENCES estudiantes (id_estudiante),
  CONSTRAINT fk_autorizaciones_periodos    FOREIGN KEY (id_periodo)    REFERENCES periodos_academicos (id_periodo),
  CONSTRAINT fk_autorizaciones_estados     FOREIGN KEY (id_estado_autorizacion)
      REFERENCES estados_autorizaciones_matricula (id_estado_autorizacion),
-- El comprobante debe ser un documento del MISMO estudiante
  CONSTRAINT fk_autorizaciones_comprobante_estudiante
      FOREIGN KEY (id_documento_comprobante_matricula, id_estudiante)
          REFERENCES documentos_estudiantes (id_documento, id_estudiante),
  CONSTRAINT fk_autorizaciones_usuario_revisa FOREIGN KEY (id_usuario_revisa) REFERENCES usuarios (id_usuario)
);

-- Avisos al estudiante sobre su solicitud de matrícula (aprobada o rechazada)
CREATE TABLE notificaciones_estudiantes (
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

-- La matrícula solo se crea después de que la autorización fue aprobada
CREATE TABLE matriculas (
    id_matricula        INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    id_autorizacion     BIGINT UNSIGNED   NOT NULL,
    id_plan_estudio     INT UNSIGNED      NOT NULL,
    id_estado_matricula SMALLINT UNSIGNED NOT NULL,
    fecha_matricula     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones       VARCHAR(1000),
    creado_en           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_matricula),
    CONSTRAINT uq_matriculas_autorizacion UNIQUE (id_autorizacion),
    INDEX idx_matriculas_estado (id_estado_matricula, fecha_matricula),
    CONSTRAINT fk_matriculas_autorizaciones FOREIGN KEY (id_autorizacion)
    REFERENCES autorizaciones_matricula (id_autorizacion),
    CONSTRAINT fk_matriculas_planes_estudio FOREIGN KEY (id_plan_estudio)
    REFERENCES planes_estudio (id_plan_estudio),
    CONSTRAINT fk_matriculas_estados FOREIGN KEY (id_estado_matricula)
    REFERENCES estados_matriculas (id_estado_matricula)
);

-- Oferta concreta de un curso en un periodo (un "grupo")
CREATE TABLE grupos (
    id_grupo       INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    id_periodo     INT UNSIGNED      NOT NULL,
    id_curso       INT UNSIGNED      NOT NULL,
    id_profesor    INT UNSIGNED,
    id_usuario_ultima_modificacion INT UNSIGNED,
    codigo         VARCHAR(40)       NOT NULL,
    cupo_maximo    SMALLINT UNSIGNED NOT NULL,
    modalidad      VARCHAR(20)       NOT NULL DEFAULT 'PRESENCIAL',
    aula           VARCHAR(80),
    fecha_inicio   DATE,
    fecha_fin      DATE,
    estado         VARCHAR(20)       NOT NULL DEFAULT 'PLANIFICADO',
    creado_en      DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_grupo),
    CONSTRAINT uq_grupos_periodo_codigo UNIQUE (id_periodo, codigo),
    INDEX idx_grupos_curso_periodo    (id_curso, id_periodo),
    INDEX idx_grupos_profesor_periodo (id_profesor, id_periodo),
    CONSTRAINT fk_grupos_periodos   FOREIGN KEY (id_periodo)  REFERENCES periodos_academicos (id_periodo),
    CONSTRAINT fk_grupos_cursos     FOREIGN KEY (id_curso)    REFERENCES cursos (id_curso),
    CONSTRAINT fk_grupos_profesores FOREIGN KEY (id_profesor) REFERENCES profesores (id_profesor) ON DELETE SET NULL,
    CONSTRAINT fk_grupos_usuario_modifica FOREIGN KEY (id_usuario_ultima_modificacion) REFERENCES usuarios (id_usuario) ON DELETE SET NULL,
    CONSTRAINT ck_grupos_cupo      CHECK (cupo_maximo > 0),
    CONSTRAINT ck_grupos_estado    CHECK (estado IN ('PLANIFICADO', 'PUBLICADO', 'EN_CURSO', 'CERRADO', 'CANCELADO')),
    CONSTRAINT ck_grupos_fechas    CHECK (fecha_inicio IS NULL OR fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    CONSTRAINT ck_grupos_modalidad CHECK (modalidad IN ('PRESENCIAL', 'VIRTUAL', 'HIBRIDA'))
);

-- Horario semanal de cada grupo (1 = lunes ... 7 = domingo)
CREATE TABLE horarios_grupos (
     id_horario  INT UNSIGNED     NOT NULL AUTO_INCREMENT,
     id_grupo    INT UNSIGNED     NOT NULL,
     dia_semana  TINYINT UNSIGNED NOT NULL,
     hora_inicio TIME             NOT NULL,
     hora_fin    TIME             NOT NULL,
     aula        VARCHAR(80),
     PRIMARY KEY (id_horario),
     CONSTRAINT uq_horarios_grupos UNIQUE (id_grupo, dia_semana, hora_inicio),
     CONSTRAINT fk_horarios_grupos_grupos FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo) ON DELETE CASCADE,
     CONSTRAINT ck_horarios_grupos_dia   CHECK (dia_semana BETWEEN 1 AND 7),
     CONSTRAINT ck_horarios_grupos_horas CHECK (hora_fin > hora_inicio)
);

-- Cursos (grupos) incluidos en cada matrícula
CREATE TABLE matriculas_detalles (
     id_detalle_matricula INT UNSIGNED NOT NULL AUTO_INCREMENT,
     id_matricula         INT UNSIGNED NOT NULL,
     id_grupo             INT UNSIGNED NOT NULL,
     retirado             BOOLEAN      NOT NULL DEFAULT FALSE,
     fecha_retiro         DATE,
     motivo_retiro        VARCHAR(500),
     creado_en            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
     actualizado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     PRIMARY KEY (id_detalle_matricula),
     CONSTRAINT uq_matriculas_detalles_grupo UNIQUE (id_matricula, id_grupo),
     INDEX idx_matriculas_detalles_grupo (id_grupo, retirado),
     CONSTRAINT fk_matriculas_detalles_matriculas FOREIGN KEY (id_matricula)
         REFERENCES matriculas (id_matricula) ON DELETE CASCADE,
     CONSTRAINT fk_matriculas_detalles_grupos FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo),
-- Si se retiró, debe tener fecha de retiro
     CONSTRAINT ck_matriculas_detalles_retiro CHECK (retirado = FALSE OR fecha_retiro IS NOT NULL)
);


-- =====================================================================
--  7. SEGUIMIENTO DE CLASES
-- =====================================================================

-- Cada clase (sesión) que se da en un grupo
CREATE TABLE sesiones_grupos (
     id_sesion          INT UNSIGNED      NOT NULL AUTO_INCREMENT,
     id_grupo           INT UNSIGNED      NOT NULL,
     numero_sesion      SMALLINT UNSIGNED NOT NULL,
     fecha              DATE              NOT NULL,
     hora_inicio        TIME,
     hora_fin           TIME,
     tema               VARCHAR(500),
     cancelada          BOOLEAN           NOT NULL DEFAULT FALSE,
     motivo_cancelacion VARCHAR(500),
     creado_en          DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
     PRIMARY KEY (id_sesion),
     CONSTRAINT uq_sesiones_grupos_numero UNIQUE (id_grupo, numero_sesion),
     INDEX idx_sesiones_grupos_fecha (id_grupo, fecha),
     CONSTRAINT fk_sesiones_grupos_grupos FOREIGN KEY (id_grupo) REFERENCES grupos (id_grupo) ON DELETE CASCADE,
     CONSTRAINT ck_sesiones_grupos_cancelacion CHECK (cancelada = FALSE OR motivo_cancelacion IS NOT NULL),
     CONSTRAINT ck_sesiones_grupos_horas  CHECK (hora_inicio IS NULL OR hora_fin IS NULL OR hora_fin > hora_inicio),
     CONSTRAINT ck_sesiones_grupos_numero CHECK (numero_sesion > 0)
);

-- Asistencia de cada estudiante a cada sesión
CREATE TABLE asistencias (
     id_asistencia        BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
     id_sesion            INT UNSIGNED      NOT NULL,
     id_detalle_matricula INT UNSIGNED      NOT NULL,
     id_tipo_asistencia   SMALLINT UNSIGNED NOT NULL,
     id_profesor_registra INT UNSIGNED,
     minutos_tardia       SMALLINT UNSIGNED NOT NULL DEFAULT 0,
     justificacion        VARCHAR(1000),
     registrado_en        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     actualizado_en       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     PRIMARY KEY (id_asistencia),
     CONSTRAINT uq_asistencias_sesion_estudiante UNIQUE (id_sesion, id_detalle_matricula),
     INDEX idx_asistencias_detalle (id_detalle_matricula, id_tipo_asistencia),
     CONSTRAINT fk_asistencias_sesiones FOREIGN KEY (id_sesion)
         REFERENCES sesiones_grupos (id_sesion) ON DELETE CASCADE,
     CONSTRAINT fk_asistencias_detalles FOREIGN KEY (id_detalle_matricula)
         REFERENCES matriculas_detalles (id_detalle_matricula) ON DELETE CASCADE,
     CONSTRAINT fk_asistencias_tipos FOREIGN KEY (id_tipo_asistencia)
         REFERENCES tipos_asistencia (id_tipo_asistencia),
     CONSTRAINT fk_asistencias_profesores FOREIGN KEY (id_profesor_registra)
         REFERENCES profesores (id_profesor) ON DELETE SET NULL
);

-- Avance del contenido impartido en cada grupo
CREATE TABLE avances_curriculares (
  id_avance            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_grupo             INT UNSIGNED    NOT NULL,
  id_profesor_registra INT UNSIGNED,
  fecha                DATE            NOT NULL,
  tema_desarrollado    VARCHAR(500)    NOT NULL,
  porcentaje_avance    DECIMAL(5,2),
  observaciones        VARCHAR(1000),
  creado_en            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_avance),
  INDEX idx_avances_curriculares_grupo_fecha (id_grupo, fecha),
  CONSTRAINT fk_avances_curriculares_grupos FOREIGN KEY (id_grupo)
      REFERENCES grupos (id_grupo) ON DELETE CASCADE,
  CONSTRAINT fk_avances_curriculares_profesores FOREIGN KEY (id_profesor_registra)
      REFERENCES profesores (id_profesor) ON DELETE SET NULL,
  CONSTRAINT ck_avances_curriculares_porcentaje
      CHECK (porcentaje_avance IS NULL OR porcentaje_avance BETWEEN 0 AND 100)
);

-- Nota final de cada estudiante en un grupo
CREATE TABLE calificaciones (
    id_calificacion        INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    id_detalle_matricula   INT UNSIGNED      NOT NULL,
    id_estado_academico    SMALLINT UNSIGNED NOT NULL,
    id_profesor_registra   INT UNSIGNED,
    nota_final             DECIMAL(5,2),
    valoracion_cualitativa VARCHAR(500),
    publicada              BOOLEAN  NOT NULL DEFAULT FALSE,
    fecha_registro         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_calificacion),
    CONSTRAINT uq_calificaciones_detalle UNIQUE (id_detalle_matricula),
    CONSTRAINT fk_calificaciones_detalles FOREIGN KEY (id_detalle_matricula)
        REFERENCES matriculas_detalles (id_detalle_matricula) ON DELETE CASCADE,
    CONSTRAINT fk_calificaciones_estados FOREIGN KEY (id_estado_academico)
        REFERENCES estados_academicos (id_estado_academico),
    CONSTRAINT fk_calificaciones_profesores FOREIGN KEY (id_profesor_registra)
        REFERENCES profesores (id_profesor) ON DELETE SET NULL,
    CONSTRAINT ck_calificaciones_nota  CHECK (nota_final IS NULL OR nota_final BETWEEN 0 AND 100),
-- Debe tener al menos una nota numérica o una valoración escrita
    CONSTRAINT ck_calificaciones_valor CHECK (nota_final IS NOT NULL OR valoracion_cualitativa IS NOT NULL)
);

-- Alertas para dar seguimiento a estudiantes
CREATE TABLE alertas_academicas (
    id_alerta            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    id_estudiante        INT UNSIGNED      NOT NULL,
    id_detalle_matricula INT UNSIGNED,
    id_tipo_alerta       SMALLINT UNSIGNED NOT NULL,
    fecha_alerta         DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detalle              VARCHAR(1500)     NOT NULL,
    atendida             BOOLEAN           NOT NULL DEFAULT FALSE,
    fecha_atencion       DATETIME,
    id_usuario_atiende   INT UNSIGNED,
    observacion_atencion VARCHAR(1500),
    PRIMARY KEY (id_alerta),
    INDEX idx_alertas_academicas_pendientes (id_estudiante, atendida, fecha_alerta),
    CONSTRAINT fk_alertas_academicas_estudiantes FOREIGN KEY (id_estudiante)
        REFERENCES estudiantes (id_estudiante) ON DELETE CASCADE,
    CONSTRAINT fk_alertas_academicas_detalles FOREIGN KEY (id_detalle_matricula)
        REFERENCES matriculas_detalles (id_detalle_matricula) ON DELETE SET NULL,
    CONSTRAINT fk_alertas_academicas_tipos FOREIGN KEY (id_tipo_alerta)
        REFERENCES tipos_alertas (id_tipo_alerta),
    CONSTRAINT fk_alertas_academicas_usuario FOREIGN KEY (id_usuario_atiende)
        REFERENCES usuarios (id_usuario),
-- Si ya se atendió, debe saberse cuándo y quién la atendió
    CONSTRAINT ck_alertas_academicas_atencion
        CHECK (atendida = FALSE OR (fecha_atencion IS NOT NULL AND id_usuario_atiende IS NOT NULL))
);


-- =====================================================================
--  8. VISTAS (consultas guardadas para facilitar reportes)
-- =====================================================================

-- Autorizaciones de matrícula con nombres en vez de solo IDs
CREATE VIEW vw_autorizaciones_matricula AS
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
    a.estudiante_activo,
    a.datos_completos,
    (ea.codigo = 'AUTORIZADA') AS puede_matricular,
    a.id_documento_comprobante_matricula,
    de.nombre_original AS comprobante_matricula,
    a.fecha_revision,
    u.correo AS revisado_por,
    a.observaciones
FROM autorizaciones_matricula a
         JOIN estudiantes e                       ON e.id_estudiante = a.id_estudiante
         JOIN periodos_academicos pa              ON pa.id_periodo = a.id_periodo
         JOIN estados_autorizaciones_matricula ea ON ea.id_estado_autorizacion = a.id_estado_autorizacion
         LEFT JOIN documentos_estudiantes de      ON de.id_documento = a.id_documento_comprobante_matricula
         LEFT JOIN usuarios u                     ON u.id_usuario = a.id_usuario_revisa;

-- Instrumentos de cada estudiante, indicando si es el actual
CREATE VIEW vw_estudiantes_instrumentos AS
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
FROM estudiantes_instrumentos ei
         JOIN estudiantes e  ON e.id_estudiante = ei.id_estudiante
         JOIN instrumentos i ON i.id_instrumento = ei.id_instrumento;

-- Malla curricular completa: programa -> plan -> nivel -> curso
CREATE VIEW vw_malla_curricular AS
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
FROM malla_curricular mc
         JOIN planes_estudio pe ON pe.id_plan_estudio = mc.id_plan_estudio
         JOIN programas p       ON p.id_programa = pe.id_programa
         JOIN cursos c          ON c.id_curso = mc.id_curso;

-- Mensualidades con el nombre del estudiante y estado legible
CREATE VIEW vw_mensualidades_estudiantes AS
SELECT
    me.id_mensualidad,
    me.id_estudiante,
    CONCAT_WS(' ', e.nombres, e.primer_apellido, e.segundo_apellido) AS estudiante,
    me.anio,
    me.mes,
    CASE WHEN me.pagada THEN 'PAGADA' ELSE 'PENDIENTE' END AS estado,
    me.id_documento_comprobante,
    de.nombre_original AS comprobante,
    me.fecha_confirmacion,
    me.id_usuario_confirma,
    me.observaciones
FROM mensualidades_estudiantes me
         JOIN estudiantes e                  ON e.id_estudiante = me.id_estudiante
         LEFT JOIN documentos_estudiantes de ON de.id_documento = me.id_documento_comprobante;

-- =====================================================================
--  9. TRIGGERS (código que MySQL ejecuta solo, antes o después de un
--     INSERT, UPDATE o DELETE en una tabla)
-- =====================================================================
--  Dentro de un trigger hay varias instrucciones terminadas en ";".
--  Para que MySQL no crea que el trigger termina en el primer ";",
--  se cambia temporalmente el separador a "$$" con DELIMITER.
--
--  NEW = la fila como va a quedar (en INSERT y UPDATE)
--  OLD = la fila como estaba antes (en UPDATE y DELETE)
--  SIGNAL SQLSTATE '45000' = cancela la operación y devuelve un error
--                            con el mensaje indicado.
-- =====================================================================

DELIMITER $$

-- ---------------------------------------------------------------------
--  9.1 Validaciones del proceso de matrícula
-- ---------------------------------------------------------------------

-- Una autorización solo puede quedar AUTORIZADA si la revisión está completa
-- y el comprobante adjunto es un comprobante de matrícula del mismo estudiante.
-- Se hace igual al insertar (bi) y al actualizar (bu).
CREATE TRIGGER trg_autorizaciones_bi_validar BEFORE INSERT ON autorizaciones_matricula FOR EACH ROW
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
            OR NEW.estudiante_activo = FALSE
            OR NEW.datos_completos = FALSE
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

        SET NEW.fecha_revision = COALESCE(NEW.fecha_revision, CURRENT_TIMESTAMP);
    END IF;
END$$

CREATE TRIGGER trg_autorizaciones_bu_validar BEFORE UPDATE ON autorizaciones_matricula FOR EACH ROW
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
            OR NEW.estudiante_activo = FALSE
            OR NEW.datos_completos = FALSE
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

        SET NEW.fecha_revision = COALESCE(NEW.fecha_revision, CURRENT_TIMESTAMP);
    END IF;
END$$

-- No se puede crear una matrícula si la autorización no está AUTORIZADA
CREATE TRIGGER trg_matriculas_bi_validar_autorizacion BEFORE INSERT ON matriculas FOR EACH ROW
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

-- El grupo que se matricula debe ser del mismo periodo que la autorización
CREATE TRIGGER trg_matriculas_detalles_bi_validar_periodo BEFORE INSERT ON matriculas_detalles FOR EACH ROW
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

-- Revisa prerrequisitos y correquisitos antes de matricular un curso
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
    IF EXISTS (
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

-- ---------------------------------------------------------------------
--  9.2 Bitácora: guardan en la tabla bitacoras qué cambió, quién lo
--      cambió y los datos antes/después (en formato JSON).
--      Nombres: ai = after insert, au = after update, ad = after delete
-- ---------------------------------------------------------------------

-- Bitácora de cursos
CREATE TRIGGER trg_cursos_ai_bitacora AFTER INSERT ON cursos FOR EACH ROW
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

CREATE TRIGGER trg_cursos_au_bitacora AFTER UPDATE ON cursos FOR EACH ROW
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

CREATE TRIGGER trg_cursos_ad_bitacora AFTER DELETE ON cursos FOR EACH ROW
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

-- Bitácora de estudiantes
CREATE TRIGGER trg_estudiantes_ai_bitacora AFTER INSERT ON estudiantes FOR EACH ROW
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

CREATE TRIGGER trg_estudiantes_au_bitacora AFTER UPDATE ON estudiantes FOR EACH ROW
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
                 'activo', OLD.activo,
                 'motivo_baja', OLD.motivo_baja,
                 'fecha_baja', OLD.fecha_baja
         ),
         JSON_OBJECT(
                 'numero_identificacion', NEW.numero_identificacion,
                 'nombres', NEW.nombres,
                 'primer_apellido', NEW.primer_apellido,
                 'segundo_apellido', NEW.segundo_apellido,
                 'telefono', NEW.telefono,
                 'correo', NEW.correo,
                 'id_centro_educativo', NEW.id_centro_educativo,
                 'activo', NEW.activo,
                 'motivo_baja', NEW.motivo_baja,
                 'fecha_baja', NEW.fecha_baja
         ));
END$$

CREATE TRIGGER trg_estudiantes_ad_bitacora AFTER DELETE ON estudiantes FOR EACH ROW
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

-- Bitácora de malla_curricular
CREATE TRIGGER trg_malla_curricular_ai_bitacora AFTER INSERT ON malla_curricular FOR EACH ROW
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

CREATE TRIGGER trg_malla_curricular_au_bitacora AFTER UPDATE ON malla_curricular FOR EACH ROW
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

CREATE TRIGGER trg_malla_curricular_ad_bitacora AFTER DELETE ON malla_curricular FOR EACH ROW
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

-- Bitácora de periodos_academicos
CREATE TRIGGER trg_periodos_academicos_ai_bitacora AFTER INSERT ON periodos_academicos FOR EACH ROW
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

CREATE TRIGGER trg_periodos_academicos_au_bitacora AFTER UPDATE ON periodos_academicos FOR EACH ROW
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

CREATE TRIGGER trg_periodos_academicos_ad_bitacora AFTER DELETE ON periodos_academicos FOR EACH ROW
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

-- Bitácora de planes_estudio
CREATE TRIGGER trg_planes_estudio_ai_bitacora AFTER INSERT ON planes_estudio FOR EACH ROW
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

CREATE TRIGGER trg_planes_estudio_au_bitacora AFTER UPDATE ON planes_estudio FOR EACH ROW
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

CREATE TRIGGER trg_planes_estudio_ad_bitacora AFTER DELETE ON planes_estudio FOR EACH ROW
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

-- Bitácora de profesores
CREATE TRIGGER trg_profesores_ai_bitacora AFTER INSERT ON profesores FOR EACH ROW
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

CREATE TRIGGER trg_profesores_au_bitacora AFTER UPDATE ON profesores FOR EACH ROW
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

CREATE TRIGGER trg_profesores_ad_bitacora AFTER DELETE ON profesores FOR EACH ROW
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

-- Bitácora de programas
CREATE TRIGGER trg_programas_ai_bitacora AFTER INSERT ON programas FOR EACH ROW
BEGIN
    INSERT INTO bitacoras
    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
    VALUES
        (NEW.id_usuario_ultima_modificacion, 'programas', NEW.id_programa, 'INSERT', NULL,
         JSON_OBJECT('codigo', NEW.codigo, 'nombre', NEW.nombre,
                     'descripcion', NEW.descripcion, 'activo', NEW.activo));
END$$

CREATE TRIGGER trg_programas_au_bitacora AFTER UPDATE ON programas FOR EACH ROW
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

CREATE TRIGGER trg_programas_ad_bitacora AFTER DELETE ON programas FOR EACH ROW
BEGIN
    INSERT INTO bitacoras
    (id_usuario, tabla_afectada, id_registro, accion, datos_anteriores, datos_nuevos)
    VALUES
        (OLD.id_usuario_ultima_modificacion, 'programas', OLD.id_programa, 'DELETE',
         JSON_OBJECT('codigo', OLD.codigo, 'nombre', OLD.nombre,
                     'descripcion', OLD.descripcion, 'activo', OLD.activo), NULL);
END$$

-- Bitácora de requisitos_cursos
CREATE TRIGGER trg_requisitos_cursos_ai_bitacora AFTER INSERT ON requisitos_cursos FOR EACH ROW
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

CREATE TRIGGER trg_requisitos_cursos_au_bitacora AFTER UPDATE ON requisitos_cursos FOR EACH ROW
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

CREATE TRIGGER trg_requisitos_cursos_ad_bitacora AFTER DELETE ON requisitos_cursos FOR EACH ROW
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


-- HU-014: historial de asignaciones de profesores a grupos.
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
