-- =========================================================
-- Script SQL de esquema del sistema ControlLocal
-- Motor de base de datos: MySQL 8.0.36
-- Contiene solo tablas, restricciones e indices.
-- Los INSERT estan en database/dml.
-- Las plantillas de transacciones estan en database/tx.
-- =========================================================

USE controllocal;

-- =========================================================
-- 1) Tabla persona
-- Contiene los datos generales de identificación y contacto.
-- Sirve como base para usuarios internos, propietarios y clientes.
-- =========================================================
CREATE TABLE persona (
    id_persona BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_persona CHAR(1) NOT NULL,
    tipo_documento CHAR(1) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL UNIQUE,
    nombres_o_razon_social VARCHAR(150) NOT NULL,
    telefono VARCHAR(20),
    correo VARCHAR(150) UNIQUE,
    estado CHAR(1) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_persona_tipo_persona CHECK (
        tipo_persona IN ('N', 'J')
    ),
    CONSTRAINT ck_persona_tipo_documento CHECK (
        tipo_documento IN ('D', 'R', 'C', 'P')
    ),
    CONSTRAINT ck_persona_estado CHECK (
        estado IN ('A', 'I')
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
    estado_administrativo CHAR(1) NOT NULL,
    rol CHAR(1) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuario_persona
        FOREIGN KEY (id_persona) REFERENCES persona(id_persona),
    CONSTRAINT ck_usuario_estado_administrativo CHECK (
        estado_administrativo IN ('A', 'I')
    ),
    CONSTRAINT ck_usuario_rol CHECK (
        rol IN ('B', 'A')
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
    zona VARCHAR(100),
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
    estado_operativo CHAR(1) NOT NULL,
    CONSTRAINT fk_agente_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario_interno(id_usuario)
        ON DELETE CASCADE,
    CONSTRAINT ck_agente_estado_operativo CHECK (
        estado_operativo IN ('D', 'L', 'N')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 5) Tabla de asignacion broker-agente
-- Define que agentes supervisa cada broker normal.
-- Un agente solo puede tener un broker supervisor activo.
-- La asignacion nace al registrar un agente propio y puede cambiar por intervencion administrativa.
-- El broker administrador conserva visibilidad global por regla de negocio.
-- =========================================================
CREATE TABLE broker_agente (
    id_broker_agente BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_broker BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    fecha_asignacion DATE NOT NULL,
    fecha_fin DATE NULL,
    motivo TEXT NOT NULL,
    estado CHAR(1) NOT NULL,
    id_agente_activo BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN estado = 'A' THEN id_agente
            ELSE NULL
        END
    ) STORED,
    CONSTRAINT fk_broker_agente_broker
        FOREIGN KEY (id_broker) REFERENCES broker(id_broker),
    CONSTRAINT fk_broker_agente_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_broker_agente_estado CHECK (
        estado IN ('A', 'I')
    ),
    CONSTRAINT ck_broker_agente_fechas CHECK (
        fecha_fin IS NULL OR fecha_fin >= fecha_asignacion
    ),
    CONSTRAINT uq_broker_agente_activo UNIQUE (id_agente_activo)
) ENGINE=InnoDB;

-- =========================================================
-- 6) Tabla de propietarios
-- Un propietario es una persona vinculada al negocio inmobiliario.
-- =========================================================
CREATE TABLE propietario (
    id_propietario BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_persona BIGINT NOT NULL UNIQUE,
    CONSTRAINT fk_propietario_persona
        FOREIGN KEY (id_persona) REFERENCES persona(id_persona)
) ENGINE=InnoDB;

-- =========================================================
-- 7) Tabla de locales comerciales
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
    estado CHAR(1) NOT NULL,
    id_propietario BIGINT NOT NULL,
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_propietario
        FOREIGN KEY (id_propietario) REFERENCES propietario(id_propietario),
    CONSTRAINT ck_local_estado CHECK (
        estado IN ('D', 'N', 'I')
    ),
    CONSTRAINT ck_local_metraje CHECK (
        metraje > 0
    ),
    CONSTRAINT ck_local_precio CHECK (
        precio_referencial >= 0
    )
) ENGINE=InnoDB;

-- =========================================================
-- 8) Tabla de captaciones
-- Relaciona local comercial y agente inmobiliario.
-- El broker revisor se registra cuando revisa la captacion.
-- El alcance de brokers normales se determina con broker_agente.
-- =========================================================
CREATE TABLE captacion (
    id_captacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_captacion VARCHAR(20) NOT NULL UNIQUE,
    fecha_captacion DATE NOT NULL,
    fecha_inicio_vigencia DATE,
    fecha_fin_vigencia DATE,
    comision_pactada DECIMAL(10,2) NOT NULL,
    observaciones TEXT,
    estado CHAR(1) NOT NULL,
    fecha_revision DATETIME NULL,
    observacion_revision TEXT,
    id_local BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    id_broker_revisor BIGINT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    id_local_activo BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN estado = 'A' THEN id_local
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
        estado IN ('P', 'O', 'R', 'A', 'C', 'V')
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
-- 9) Tabla de clientes interesados
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
-- 10) Tabla de oportunidades comerciales
-- Nace cuando un cliente interesado se asocia a una captacion activa.
-- Permite trazabilidad aunque nunca se genere una solicitud formal.
-- =========================================================
CREATE TABLE oportunidad_comercial (
    id_oportunidad BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_oportunidad VARCHAR(20) NOT NULL UNIQUE,
    fecha_registro DATETIME NOT NULL,
    estado CHAR(1) NOT NULL,
    fecha_actualizacion_estado DATETIME NULL,
    motivo_cierre VARCHAR(150),
    observaciones TEXT,
    id_cliente BIGINT NOT NULL,
    id_captacion BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    clave_oportunidad_abierta VARCHAR(60) GENERATED ALWAYS AS (
        CASE
            WHEN estado = 'A' THEN CONCAT(id_cliente, '-', id_captacion)
            ELSE NULL
        END
    ) STORED,
    fecha_cierre DATETIME NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_oportunidad_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente_interesado(id_cliente),
    CONSTRAINT fk_oportunidad_captacion
        FOREIGN KEY (id_captacion) REFERENCES captacion(id_captacion),
    CONSTRAINT fk_oportunidad_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_oportunidad_estado CHECK (
        estado IN ('A', 'S', 'N', 'F', 'X')
    ),
    CONSTRAINT uq_oportunidad_abierta_cliente_captacion UNIQUE (clave_oportunidad_abierta)
) ENGINE=InnoDB;

-- =========================================================
-- 11) Tabla de interacciones comerciales
-- Se relaciona con oportunidad y agente que realiza la interaccion.
-- =========================================================
CREATE TABLE interaccion_comercial (
    id_interaccion BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_hora DATETIME NOT NULL,
    canal_contacto CHAR(1) NOT NULL,
    observaciones TEXT,
    resultado CHAR(1) NOT NULL,
    id_oportunidad BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interaccion_oportunidad
        FOREIGN KEY (id_oportunidad) REFERENCES oportunidad_comercial(id_oportunidad),
    CONSTRAINT fk_interaccion_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_interaccion_canal CHECK (
        canal_contacto IN ('L', 'W', 'E', 'P', 'O')
    ),
    CONSTRAINT ck_interaccion_resultado CHECK (
        resultado IN ('P', 'I', 'N', 'S', 'D')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 12) Tabla de visitas
-- Se relaciona con oportunidad y agente que ejecuta la visita.
-- =========================================================
CREATE TABLE visita (
    id_visita BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_visita DATE NOT NULL,
    hora_visita TIME NOT NULL,
    observaciones TEXT,
    estado CHAR(1) NOT NULL,
    -- Desenlace comercial de la visita. Comparte el dominio de
    -- interaccion_comercial.resultado para responder de forma uniforme
    -- "¿debemos darle seguimiento?". Es NULL mientras la visita no se realiza.
    resultado CHAR(1) NULL,
    id_oportunidad BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_visita_oportunidad
        FOREIGN KEY (id_oportunidad) REFERENCES oportunidad_comercial(id_oportunidad),
    CONSTRAINT fk_visita_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_visita_estado CHECK (
        estado IN ('P', 'G', 'C', 'R')
    ),
    CONSTRAINT ck_visita_resultado CHECK (
        resultado IS NULL OR resultado IN ('P', 'I', 'N', 'S', 'D')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 12-bis) Tabla de prospecciones (pre-captacion)
-- Embudo del agente persiguiendo al propietario para captar el local.
-- Espejo, del lado de la oferta, de oportunidad_comercial.
-- Las fechas de cada hito sirven como historial de interacciones con el dueno.
-- Al aceptar la propuesta nace una captacion (id_captacion).
-- =========================================================
CREATE TABLE prospeccion (
    id_prospeccion BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_prospeccion VARCHAR(20) NOT NULL UNIQUE,
    fecha_registro DATETIME NOT NULL,
    estado CHAR(1) NOT NULL,
    resultado_propuesta CHAR(1) NULL,
    fecha_contacto DATE NULL,
    fecha_reunion DATE NULL,
    fecha_propuesta DATE NULL,
    -- "Por ahora no": recontactar en un lapso no mayor a 15 dias.
    fecha_recontacto DATE NULL,
    observaciones TEXT,
    id_local BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    id_captacion BIGINT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_prospeccion_local
        FOREIGN KEY (id_local) REFERENCES local_comercial(id_local),
    CONSTRAINT fk_prospeccion_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT fk_prospeccion_captacion
        FOREIGN KEY (id_captacion) REFERENCES captacion(id_captacion),
    CONSTRAINT ck_prospeccion_estado CHECK (
        estado IN ('P', 'C', 'R', 'E', 'S', 'T', 'D')
    ),
    CONSTRAINT ck_prospeccion_resultado CHECK (
        resultado_propuesta IS NULL OR resultado_propuesta IN ('P', 'A', 'R', 'S')
    ),
    CONSTRAINT ck_prospeccion_recontacto CHECK (
        fecha_recontacto IS NULL OR estado = 'S'
    )
) ENGINE=InnoDB;

-- =========================================================
-- 13) Tabla de solicitudes de alquiler
-- Formaliza una oportunidad comercial.
-- =========================================================
CREATE TABLE solicitud_alquiler (
    id_solicitud BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_solicitud VARCHAR(20) NOT NULL UNIQUE,
    fecha_registro DATE NOT NULL,
    monto_propuesto DECIMAL(12,2) NOT NULL,
    plazo_tentativo VARCHAR(50),
    observaciones TEXT,
    estado CHAR(1) NOT NULL,
    fecha_actualizacion_estado DATETIME NULL,
    id_oportunidad BIGINT NOT NULL UNIQUE,
    id_agente BIGINT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_solicitud_oportunidad
        FOREIGN KEY (id_oportunidad) REFERENCES oportunidad_comercial(id_oportunidad),
    CONSTRAINT fk_solicitud_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_solicitud_estado CHECK (
        estado IN ('G', 'E', 'O', 'A', 'R', 'D')
    ),
    CONSTRAINT ck_solicitud_monto CHECK (
        monto_propuesto > 0
    )
) ENGINE=InnoDB;

-- =========================================================
-- 14) Tabla de documentos de solicitud
-- Cada documento pertenece a una solicitud.
-- =========================================================
CREATE TABLE documento_solicitud (
    id_documento BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_documento CHAR(1) NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(255),
    fecha_entrega DATETIME NOT NULL,
    resultado_revision CHAR(1),
    observaciones TEXT,
    estado CHAR(1) NOT NULL,
    id_solicitud BIGINT NOT NULL,
    CONSTRAINT fk_documento_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES solicitud_alquiler(id_solicitud)
        ON DELETE CASCADE,
    CONSTRAINT ck_documento_estado CHECK (
        estado IN ('R', 'O', 'V')
    ),
    CONSTRAINT ck_documento_tipo CHECK (
        tipo_documento IN ('I', 'R', 'V', 'P', 'E', 'G', 'D', 'O')
    ),
    CONSTRAINT ck_documento_revision CHECK (
        resultado_revision IS NULL
        OR resultado_revision IN ('P', 'C', 'O')
    )
) ENGINE=InnoDB;

-- =========================================================
-- 15) Tabla de evaluación de solicitudes
-- Conserva el historial de evaluaciones de una solicitud.
-- =========================================================
CREATE TABLE evaluacion_solicitud (
    id_evaluacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_evaluacion DATETIME NOT NULL,
    resultado CHAR(1) NOT NULL,
    observaciones TEXT,
    responsable_evaluacion BIGINT NOT NULL,
    tipo_evaluacion CHAR(1) NOT NULL,
    id_solicitud BIGINT NOT NULL,
    id_solicitud_final BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN tipo_evaluacion = 'F' THEN id_solicitud
            ELSE NULL
        END
    ) STORED,
    CONSTRAINT fk_evaluacion_broker
        FOREIGN KEY (responsable_evaluacion) REFERENCES broker(id_broker),
    CONSTRAINT fk_evaluacion_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES solicitud_alquiler(id_solicitud),
    CONSTRAINT ck_evaluacion_resultado CHECK (
        resultado IN ('A', 'R', 'O')
    ),
    CONSTRAINT ck_tipo_evaluacion CHECK (
        tipo_evaluacion IN ('P', 'O', 'F')
    ),
    CONSTRAINT uq_evaluacion_final_por_solicitud UNIQUE (id_solicitud_final)
) ENGINE=InnoDB;

-- =========================================================
-- 16) Tabla de reasignacion de captaciones
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
-- 16-bis) Historial de reasignacion de agentes entre brokers
-- Traza el evento (broker anterior -> broker nuevo) autorizado por el broker
-- administrador. La supervision vigente se mantiene en broker_agente; esta
-- tabla solo conserva el historico del cambio (analoga a reasignacion_captacion).
-- id_broker_anterior es NULL cuando es la primera asignacion del agente.
-- =========================================================
CREATE TABLE reasignacion_agente_broker (
    id_reasignacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_cambio DATETIME NOT NULL,
    motivo TEXT NOT NULL,
    id_agente BIGINT NOT NULL,
    id_broker_anterior BIGINT NULL,
    id_broker_nuevo BIGINT NOT NULL,
    id_broker_administrador BIGINT NOT NULL,
    CONSTRAINT fk_reasignacion_ab_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT fk_reasignacion_ab_broker_anterior
        FOREIGN KEY (id_broker_anterior) REFERENCES broker(id_broker),
    CONSTRAINT fk_reasignacion_ab_broker_nuevo
        FOREIGN KEY (id_broker_nuevo) REFERENCES broker(id_broker),
    CONSTRAINT fk_reasignacion_ab_broker_admin
        FOREIGN KEY (id_broker_administrador) REFERENCES broker(id_broker),
    CONSTRAINT ck_reasignacion_ab_brokers CHECK (
        id_broker_anterior IS NULL OR id_broker_anterior <> id_broker_nuevo
    )
) ENGINE=InnoDB;

-- =========================================================
-- 17) Tabla de motivos de no continuidad
-- Cierra la oportunidad cuando el cliente no continua.
-- Opcionalmente indica si el origen fue una interaccion, visita o solicitud.
-- =========================================================
CREATE TABLE motivo_no_continuidad (
    id_motivo_no_continuidad BIGINT AUTO_INCREMENT PRIMARY KEY,
    fecha_hora DATETIME NOT NULL,
    razon_principal CHAR(1) NOT NULL,
    observaciones TEXT,
    id_agente BIGINT NOT NULL,
    id_oportunidad BIGINT NOT NULL,
    id_interaccion BIGINT NULL,
    id_visita BIGINT NULL,
    id_solicitud BIGINT NULL,
    CONSTRAINT fk_motivo_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT fk_motivo_oportunidad
        FOREIGN KEY (id_oportunidad) REFERENCES oportunidad_comercial(id_oportunidad),
    CONSTRAINT fk_motivo_interaccion
        FOREIGN KEY (id_interaccion) REFERENCES interaccion_comercial(id_interaccion)
        ON DELETE CASCADE,
    CONSTRAINT fk_motivo_visita
        FOREIGN KEY (id_visita) REFERENCES visita(id_visita)
        ON DELETE CASCADE,
    CONSTRAINT fk_motivo_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES solicitud_alquiler(id_solicitud)
        ON DELETE CASCADE,
    CONSTRAINT ck_motivo_razon CHECK (
        razon_principal IN ('P', 'U', 'C', 'L', 'N', 'E', 'O')
    ),
    CONSTRAINT ck_motivo_referencia_unica CHECK (
        (CASE WHEN id_interaccion IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN id_visita IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN id_solicitud IS NOT NULL THEN 1 ELSE 0 END) <= 1
    )
) ENGINE=InnoDB;

-- =========================================================
-- 18) Indices de apoyo
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

CREATE INDEX idx_broker_agente_broker ON broker_agente(id_broker);
CREATE INDEX idx_broker_agente_agente ON broker_agente(id_agente);
CREATE INDEX idx_broker_agente_estado ON broker_agente(estado);

CREATE INDEX idx_propietario_persona ON propietario(id_persona);

CREATE INDEX idx_local_propietario ON local_comercial(id_propietario);

CREATE INDEX idx_captacion_local ON captacion(id_local);
CREATE INDEX idx_captacion_agente ON captacion(id_agente);
CREATE INDEX idx_captacion_broker ON captacion(id_broker_revisor);
CREATE INDEX idx_captacion_estado ON captacion(estado);

CREATE INDEX idx_cliente_persona ON cliente_interesado(id_persona);

CREATE INDEX idx_oportunidad_cliente ON oportunidad_comercial(id_cliente);
CREATE INDEX idx_oportunidad_captacion ON oportunidad_comercial(id_captacion);
CREATE INDEX idx_oportunidad_agente ON oportunidad_comercial(id_agente);
CREATE INDEX idx_oportunidad_estado ON oportunidad_comercial(estado);

CREATE INDEX idx_interaccion_oportunidad ON interaccion_comercial(id_oportunidad);
CREATE INDEX idx_interaccion_agente ON interaccion_comercial(id_agente);
CREATE INDEX idx_interaccion_fecha ON interaccion_comercial(fecha_hora);

CREATE INDEX idx_visita_oportunidad ON visita(id_oportunidad);
CREATE INDEX idx_visita_agente ON visita(id_agente);
CREATE INDEX idx_visita_estado ON visita(estado);

CREATE INDEX idx_prospeccion_local ON prospeccion(id_local);
CREATE INDEX idx_prospeccion_agente ON prospeccion(id_agente);
CREATE INDEX idx_prospeccion_estado ON prospeccion(estado);
CREATE INDEX idx_prospeccion_recontacto ON prospeccion(fecha_recontacto);

CREATE INDEX idx_solicitud_oportunidad ON solicitud_alquiler(id_oportunidad);
CREATE INDEX idx_solicitud_agente ON solicitud_alquiler(id_agente);
CREATE INDEX idx_solicitud_estado ON solicitud_alquiler(estado);

CREATE INDEX idx_documento_solicitud ON documento_solicitud(id_solicitud);

CREATE INDEX idx_evaluacion_solicitud ON evaluacion_solicitud(id_solicitud);
CREATE INDEX idx_evaluacion_broker ON evaluacion_solicitud(responsable_evaluacion);
CREATE INDEX idx_evaluacion_tipo ON evaluacion_solicitud(tipo_evaluacion);

CREATE INDEX idx_reasignacion_captacion ON reasignacion_captacion(id_captacion);

CREATE INDEX idx_motivo_agente ON motivo_no_continuidad(id_agente);
CREATE INDEX idx_motivo_oportunidad ON motivo_no_continuidad(id_oportunidad);
CREATE INDEX idx_motivo_interaccion ON motivo_no_continuidad(id_interaccion);
CREATE INDEX idx_motivo_visita ON motivo_no_continuidad(id_visita);
CREATE INDEX idx_motivo_solicitud ON motivo_no_continuidad(id_solicitud);
