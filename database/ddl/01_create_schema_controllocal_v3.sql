-- =========================================================
-- Script SQL actualizado del sistema ControlLocal
-- Motor de base de datos: MySQL 8.0.36
-- =========================================================

DROP DATABASE IF EXISTS controllocal;

CREATE DATABASE controllocal
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE controllocal;

-- =========================================================
-- 1) Tabla persona
-- Contiene los datos generales de identificación y contacto.
-- Sirve como base para usuarios internos, propietarios y clientes.
-- =========================================================
CREATE TABLE persona (
    id_persona BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_persona VARCHAR(20) NOT NULL,
    tipo_documento VARCHAR(20) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL UNIQUE,
    nombres_o_razon_social VARCHAR(150) NOT NULL,
    telefono VARCHAR(20),
    correo VARCHAR(150),
    estado VARCHAR(20) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_persona_tipo_persona CHECK (
        tipo_persona IN ('NATURAL', 'JURIDICA')
    ),
    CONSTRAINT ck_persona_estado CHECK (
        estado IN ('ACTIVO', 'INACTIVO')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 2) Tabla de usuarios internos
-- Representa únicamente a quienes acceden al sistema.
-- Se asocia a persona para reutilizar datos generales.
-- =========================================================
CREATE TABLE usuario_interno (
    id_usuario BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_persona BIGINT NOT NULL UNIQUE,
    nombre_usuario VARCHAR(60) NOT NULL UNIQUE,
    contrasena_hash VARCHAR(255) NOT NULL,
    estado_administrativo VARCHAR(20) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuario_persona
        FOREIGN KEY (id_persona) REFERENCES persona(id_persona),
    CONSTRAINT ck_usuario_estado_administrativo CHECK (
        estado_administrativo IN ('ACTIVO', 'INACTIVO')
    ),
    CONSTRAINT ck_usuario_rol CHECK (
        rol IN ('BROKER', 'AGENTE')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 3) Tabla de brokers
-- Especialización de usuario_interno.
-- id_broker es independiente de id_usuario.
-- =========================================================
CREATE TABLE broker (
    id_broker BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario BIGINT NOT NULL UNIQUE,
    codigo_broker VARCHAR(20) NOT NULL UNIQUE,
    fecha_designacion DATE NOT NULL,
    es_administrador BOOLEAN NOT NULL DEFAULT FALSE,
    broker_admin_unico TINYINT GENERATED ALWAYS AS (
        CASE
            WHEN es_administrador THEN 1
            ELSE NULL
        END
    ) STORED,
    CONSTRAINT fk_broker_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario_interno(id_usuario)
        ON DELETE CASCADE,
    CONSTRAINT ck_broker_es_administrador CHECK (
        es_administrador IN (0, 1)
    ),
    CONSTRAINT uq_broker_admin_unico UNIQUE (broker_admin_unico)
) ENGINE=InnoDB;

-- =========================================================
-- 4) Tabla de agentes inmobiliarios
-- Especialización de usuario_interno.
-- id_agente es independiente de id_usuario.
-- =========================================================
CREATE TABLE agente_inmobiliario (
    id_agente BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario BIGINT NOT NULL UNIQUE,
    codigo_agente VARCHAR(20) NOT NULL UNIQUE,
    zona_asignada VARCHAR(100),
    fecha_ingreso DATE NOT NULL,
    estado_operativo VARCHAR(20) NOT NULL,
    CONSTRAINT fk_agente_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario_interno(id_usuario)
        ON DELETE CASCADE,
    CONSTRAINT ck_agente_estado_operativo CHECK (
        estado_operativo IN ('DISPONIBLE', 'LICENCIA', 'NO_DISPONIBLE')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 5) Tabla de propietarios
-- Un propietario es una persona vinculada al negocio inmobiliario.
-- =========================================================
CREATE TABLE propietario (
    id_propietario BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_persona BIGINT NOT NULL UNIQUE,
    CONSTRAINT fk_propietario_persona
        FOREIGN KEY (id_persona) REFERENCES persona(id_persona)
) ENGINE=InnoDB;

-- =========================================================
-- 6) Tabla de locales comerciales
-- Cada local pertenece a un propietario.
-- =========================================================
CREATE TABLE local_comercial (
    id_local BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_local VARCHAR(20) NOT NULL UNIQUE,
    direccion VARCHAR(200) NOT NULL,
    distrito VARCHAR(100) NOT NULL,
    metraje DECIMAL(10,2) NOT NULL,
    precio_referencial DECIMAL(12,2) NOT NULL,
    rubro_permitido VARCHAR(120) NOT NULL,
    descripcion TEXT,
    estado VARCHAR(20) NOT NULL,
    id_propietario BIGINT NOT NULL,
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_propietario
        FOREIGN KEY (id_propietario) REFERENCES propietario(id_propietario),
    CONSTRAINT ck_local_estado CHECK (
        estado IN ('DISPONIBLE', 'NO_DISPONIBLE', 'INACTIVO')
    ),
    CONSTRAINT ck_local_metraje CHECK (
        metraje > 0
    ),
    CONSTRAINT ck_local_precio CHECK (
        precio_referencial >= 0
    )
) ENGINE=InnoDB;

-- =========================================================
-- 7) Tabla de captaciones
-- Relaciona local comercial, agente inmobiliario y broker.
-- =========================================================
CREATE TABLE captacion (
    id_captacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_captacion VARCHAR(20) NOT NULL UNIQUE,
    fecha_captacion DATE NOT NULL,
    fecha_inicio_vigencia DATE,
    fecha_fin_vigencia DATE,
    comision_pactada DECIMAL(10,2) NOT NULL,
    observaciones TEXT,
    estado VARCHAR(25) NOT NULL,
    fecha_revision DATETIME NULL,
    observacion_revision TEXT,
    id_local BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    id_broker_revisor BIGINT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    id_local_activo BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN estado = 'ACTIVA' THEN id_local
            ELSE NULL
        END
    ) STORED,
    CONSTRAINT fk_captacion_local
        FOREIGN KEY (id_local) REFERENCES local_comercial(id_local),
    CONSTRAINT fk_captacion_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT fk_captacion_broker
        FOREIGN KEY (id_broker_revisor) REFERENCES broker(id_broker),
    CONSTRAINT ck_captacion_estado CHECK (
        estado IN ('PENDIENTE_REVISION', 'OBSERVADA', 'RECHAZADA', 'ACTIVA', 'CERRADA', 'VENCIDA')
    ),
    CONSTRAINT ck_captacion_comision CHECK (
        comision_pactada >= 0
    ),
    CONSTRAINT ck_captacion_fechas CHECK (
        fecha_fin_vigencia IS NULL
        OR fecha_inicio_vigencia IS NULL
        OR fecha_fin_vigencia >= fecha_inicio_vigencia
    ),
    CONSTRAINT uq_captacion_activa_por_local UNIQUE (id_local_activo)
) ENGINE=InnoDB;

-- =========================================================
-- 8) Tabla de clientes interesados
-- Un cliente interesado es una persona vinculada al proceso comercial.
-- =========================================================
CREATE TABLE cliente_interesado (
    id_cliente BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_persona BIGINT NOT NULL UNIQUE,
    rubro_comercial VARCHAR(120),
    CONSTRAINT fk_cliente_persona
        FOREIGN KEY (id_persona) REFERENCES persona(id_persona)
) ENGINE=InnoDB;

-- =========================================================
-- 9) Tabla de interacciones comerciales
-- Se relaciona con cliente, captación y agente.
-- =========================================================
CREATE TABLE interaccion_comercial (
    id_interaccion BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_hora DATETIME NOT NULL,
    canal_contacto VARCHAR(30) NOT NULL,
    observaciones TEXT,
    resultado VARCHAR(40) NOT NULL,
    id_cliente BIGINT NOT NULL,
    id_captacion BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interaccion_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente_interesado(id_cliente),
    CONSTRAINT fk_interaccion_captacion
        FOREIGN KEY (id_captacion) REFERENCES captacion(id_captacion),
    CONSTRAINT fk_interaccion_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_interaccion_canal CHECK (
        canal_contacto IN ('LLAMADA', 'WHATSAPP', 'EMAIL', 'PRESENCIAL', 'OTRO')
    ),
    CONSTRAINT ck_interaccion_resultado CHECK (
        resultado IN ('PENDIENTE', 'INTERESADO', 'NO_INTERESADO', 'SEGUIMIENTO', 'DESCARTADO')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 10) Tabla de visitas
-- Se relaciona con cliente, captación y agente.
-- =========================================================
CREATE TABLE visita (
    id_visita BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_visita DATE NOT NULL,
    hora_visita TIME NOT NULL,
    observaciones TEXT,
    estado VARCHAR(20) NOT NULL,
    resultado TEXT,
    id_cliente BIGINT NOT NULL,
    id_captacion BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_visita_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente_interesado(id_cliente),
    CONSTRAINT fk_visita_captacion
        FOREIGN KEY (id_captacion) REFERENCES captacion(id_captacion),
    CONSTRAINT fk_visita_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_visita_estado CHECK (
        estado IN ('PROGRAMADA', 'REPROGRAMADA', 'CANCELADA', 'REALIZADA')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 11) Tabla de solicitudes de alquiler
-- Se relaciona con cliente, captación y agente.
-- =========================================================
CREATE TABLE solicitud_alquiler (
    id_solicitud BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_solicitud VARCHAR(20) NOT NULL UNIQUE,
    fecha_registro DATE NOT NULL,
    monto_propuesto DECIMAL(12,2) NOT NULL,
    plazo_tentativo VARCHAR(50),
    observaciones TEXT,
    estado VARCHAR(25) NOT NULL,
    fecha_actualizacion_estado DATETIME NULL,
    id_cliente BIGINT NOT NULL,
    id_captacion BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_solicitud_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente_interesado(id_cliente),
    CONSTRAINT fk_solicitud_captacion
        FOREIGN KEY (id_captacion) REFERENCES captacion(id_captacion),
    CONSTRAINT fk_solicitud_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_solicitud_estado CHECK (
        estado IN ('REGISTRADA', 'EN_REVISION', 'OBSERVADA', 'APROBADA', 'RECHAZADA', 'DESISTIDA')
    ),
    CONSTRAINT ck_solicitud_monto CHECK (
        monto_propuesto > 0
    )
) ENGINE=InnoDB;

-- =========================================================
-- 12) Tabla de documentos de solicitud
-- Cada documento pertenece a una solicitud.
-- =========================================================
CREATE TABLE documento_solicitud (
    id_documento BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_documento VARCHAR(50) NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(255),
    fecha_entrega DATETIME NOT NULL,
    resultado_revision VARCHAR(25),
    observaciones TEXT,
    estado VARCHAR(20) NOT NULL,
    id_solicitud BIGINT NOT NULL,
    CONSTRAINT fk_documento_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES solicitud_alquiler(id_solicitud)
        ON DELETE CASCADE,
    CONSTRAINT ck_documento_estado CHECK (
        estado IN ('REGISTRADO', 'OBSERVADO', 'VALIDADO')
    ),
    CONSTRAINT ck_documento_revision CHECK (
        resultado_revision IS NULL
        OR resultado_revision IN ('PENDIENTE', 'CONFORME', 'OBSERVADO')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 13) Tabla de evaluación de solicitudes
-- Conserva el historial de evaluaciones de una solicitud.
-- =========================================================
CREATE TABLE evaluacion_solicitud (
    id_evaluacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_evaluacion DATETIME NOT NULL,
    resultado VARCHAR(20) NOT NULL,
    observaciones TEXT,
    responsable_evaluacion BIGINT NOT NULL,
    tipo_evaluacion VARCHAR(20) NOT NULL,
    id_solicitud BIGINT NOT NULL,
    CONSTRAINT fk_evaluacion_broker
        FOREIGN KEY (responsable_evaluacion) REFERENCES broker(id_broker),
    CONSTRAINT fk_evaluacion_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES solicitud_alquiler(id_solicitud)
        ON DELETE CASCADE,
    CONSTRAINT ck_evaluacion_resultado CHECK (
        resultado IN ('APROBADA', 'RECHAZADA', 'OBSERVADA')
    ),
    CONSTRAINT ck_tipo_evaluacion CHECK (
        tipo_evaluacion IN ('PRELIMINAR', 'OBSERVACION', 'FINAL')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 14) Tabla de reasignación de captaciones
-- Guarda el historial de reasignación de una captación.
-- =========================================================
CREATE TABLE reasignacion_captacion (
    id_reasignacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_cambio DATETIME NOT NULL,
    motivo TEXT NOT NULL,
    id_captacion BIGINT NOT NULL,
    id_agente_anterior BIGINT NOT NULL,
    id_agente_nuevo BIGINT NOT NULL,
    id_broker BIGINT NOT NULL,
    CONSTRAINT fk_reasignacion_captacion
        FOREIGN KEY (id_captacion) REFERENCES captacion(id_captacion)
        ON DELETE CASCADE,
    CONSTRAINT fk_reasignacion_agente_anterior
        FOREIGN KEY (id_agente_anterior) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT fk_reasignacion_agente_nuevo
        FOREIGN KEY (id_agente_nuevo) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT fk_reasignacion_broker
        FOREIGN KEY (id_broker) REFERENCES broker(id_broker),
    CONSTRAINT ck_reasignacion_agentes CHECK (
        id_agente_anterior <> id_agente_nuevo
    )
) ENGINE=InnoDB;

-- =========================================================
-- 15) Tabla de motivos de no continuidad
-- Se asocia a una interacción, visita o solicitud.
-- =========================================================
CREATE TABLE motivo_no_continuidad (
    id_motivo_no_continuidad BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_hora DATETIME NOT NULL,
    razon_principal VARCHAR(150) NOT NULL,
    observaciones TEXT,
    id_agente BIGINT NOT NULL,
    id_interaccion BIGINT NULL,
    id_visita BIGINT NULL,
    id_solicitud BIGINT NULL,
    CONSTRAINT fk_motivo_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT fk_motivo_interaccion
        FOREIGN KEY (id_interaccion) REFERENCES interaccion_comercial(id_interaccion)
        ON DELETE CASCADE,
    CONSTRAINT fk_motivo_visita
        FOREIGN KEY (id_visita) REFERENCES visita(id_visita)
        ON DELETE CASCADE,
    CONSTRAINT fk_motivo_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES solicitud_alquiler(id_solicitud)
        ON DELETE CASCADE,
    CONSTRAINT ck_motivo_referencia_unica CHECK (
        (CASE WHEN id_interaccion IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN id_visita IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN id_solicitud IS NOT NULL THEN 1 ELSE 0 END) = 1
    )
) ENGINE=InnoDB;

-- =========================================================
-- 16) Índices de apoyo
-- Mejoran el rendimiento de búsqueda y relación.
-- =========================================================

CREATE INDEX idx_persona_documento ON persona(tipo_documento, numero_documento);
CREATE INDEX idx_persona_estado ON persona(estado);

CREATE INDEX idx_usuario_persona ON usuario_interno(id_persona);
CREATE INDEX idx_usuario_estado ON usuario_interno(estado_administrativo);
CREATE INDEX idx_usuario_rol ON usuario_interno(rol);

CREATE INDEX idx_broker_usuario ON broker(id_usuario);

CREATE INDEX idx_agente_usuario ON agente_inmobiliario(id_usuario);
CREATE INDEX idx_agente_estado_operativo ON agente_inmobiliario(estado_operativo);

CREATE INDEX idx_propietario_persona ON propietario(id_persona);

CREATE INDEX idx_local_propietario ON local_comercial(id_propietario);

CREATE INDEX idx_captacion_local ON captacion(id_local);
CREATE INDEX idx_captacion_agente ON captacion(id_agente);
CREATE INDEX idx_captacion_broker ON captacion(id_broker_revisor);
CREATE INDEX idx_captacion_estado ON captacion(estado);

CREATE INDEX idx_cliente_persona ON cliente_interesado(id_persona);

CREATE INDEX idx_interaccion_cliente ON interaccion_comercial(id_cliente);
CREATE INDEX idx_interaccion_captacion ON interaccion_comercial(id_captacion);
CREATE INDEX idx_interaccion_agente ON interaccion_comercial(id_agente);
CREATE INDEX idx_interaccion_fecha ON interaccion_comercial(fecha_hora);

CREATE INDEX idx_visita_cliente ON visita(id_cliente);
CREATE INDEX idx_visita_captacion ON visita(id_captacion);
CREATE INDEX idx_visita_agente ON visita(id_agente);
CREATE INDEX idx_visita_estado ON visita(estado);

CREATE INDEX idx_solicitud_cliente ON solicitud_alquiler(id_cliente);
CREATE INDEX idx_solicitud_captacion ON solicitud_alquiler(id_captacion);
CREATE INDEX idx_solicitud_agente ON solicitud_alquiler(id_agente);
CREATE INDEX idx_solicitud_estado ON solicitud_alquiler(estado);

CREATE INDEX idx_documento_solicitud ON documento_solicitud(id_solicitud);

CREATE INDEX idx_evaluacion_solicitud ON evaluacion_solicitud(id_solicitud);
CREATE INDEX idx_evaluacion_broker ON evaluacion_solicitud(responsable_evaluacion);
CREATE INDEX idx_evaluacion_tipo ON evaluacion_solicitud(tipo_evaluacion);

CREATE INDEX idx_reasignacion_captacion ON reasignacion_captacion(id_captacion);

CREATE INDEX idx_motivo_agente ON motivo_no_continuidad(id_agente);
CREATE INDEX idx_motivo_interaccion ON motivo_no_continuidad(id_interaccion);
CREATE INDEX idx_motivo_visita ON motivo_no_continuidad(id_visita);
CREATE INDEX idx_motivo_solicitud ON motivo_no_continuidad(id_solicitud);

-- =========================================================
-- 17) Registro inicial del broker administrador
-- Se crea una persona, luego un usuario interno y finalmente el broker.
-- =========================================================

INSERT INTO persona (
    tipo_persona,
    tipo_documento,
    numero_documento,
    nombres_o_razon_social,
    telefono,
    correo,
    estado
) VALUES (
    'NATURAL',
    'DNI',
    '00000000',
    'Broker Principal',
    '999999999',
    'broker@controllocal.com',
    'ACTIVO'
);

SET @id_persona_broker_admin = LAST_INSERT_ID();

INSERT INTO usuario_interno (
    id_persona,
    nombre_usuario,
    contrasena_hash,
    estado_administrativo,
    rol
) VALUES (
    @id_persona_broker_admin,
    'brokeradmin',
    'HASH_TEMPORAL',
    'ACTIVO',
    'BROKER'
);

SET @id_usuario_broker_admin = LAST_INSERT_ID();

INSERT INTO broker (
    id_usuario,
    codigo_broker,
    fecha_designacion,
    es_administrador
) VALUES (
    @id_usuario_broker_admin,
    'BRK-ADM-001',
    CURRENT_DATE,
    TRUE
);