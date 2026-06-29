-- =========================================================
-- Script SQL de esquema del sistema ControlLocal
-- Motor de base de datos: MySQL 8.0.36
-- Contiene solo tablas, restricciones e indices.
-- No contiene migraciones ni datos: representa el modelo final vigente.
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
    consentimiento_uso_dato BOOLEAN NULL,
    -- Foto de perfil: clave opaca del archivo en el almacen (NULL si aun no sube foto).
    foto_clave VARCHAR(200) NULL,
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
-- 6-bis) Catalogo de distritos
-- Lista cerrada de distritos donde opera la corredora.
-- =========================================================
CREATE TABLE distrito (
    id_distrito BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    provincia VARCHAR(100) NOT NULL DEFAULT 'Lima',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =========================================================
-- 7) Tabla de locales comerciales
-- Cada local pertenece a un propietario. distrito conserva el nombre visible
-- e id_distrito enlaza el catalogo cerrado.
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
    tipo_inmueble CHAR(1) NULL,
    uso CHAR(1) NULL,
    ambientes INT NULL,
    antiguedad_anios INT NULL,
    zona_urbanizacion VARCHAR(150) NULL,
    geo_lat DECIMAL(10,7) NULL,
    geo_long DECIMAL(10,7) NULL,
    frente DECIMAL(8,2) NULL,
    zonificacion VARCHAR(40) NULL,
    apto_licencia_funcionamiento BOOLEAN NULL,
    carga_electrica_kw DECIMAL(8,2) NULL,
    numero_estacionamientos INT NULL,
    cuota_mantenimiento DECIMAL(10,2) NULL,
    id_distrito BIGINT NULL,
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_propietario
        FOREIGN KEY (id_propietario) REFERENCES propietario(id_propietario),
    CONSTRAINT fk_local_distrito
        FOREIGN KEY (id_distrito) REFERENCES distrito(id_distrito),
    CONSTRAINT ck_local_estado CHECK (
        estado IN ('D', 'N', 'I')
    ),
    CONSTRAINT ck_local_metraje CHECK (
        metraje > 0
    ),
    CONSTRAINT ck_local_precio CHECK (
        precio_referencial >= 0
    ),
    CONSTRAINT ck_local_tipo_inmueble CHECK (
        tipo_inmueble IS NULL OR tipo_inmueble IN ('L', 'O', 'D', 'C', 'T', 'X')
    ),
    CONSTRAINT ck_local_uso CHECK (
        uso IS NULL OR uso IN ('C', 'V', 'I', 'M')
    ),
    CONSTRAINT ck_local_ambientes CHECK (
        ambientes IS NULL OR ambientes > 0
    ),
    CONSTRAINT ck_local_antiguedad CHECK (
        antiguedad_anios IS NULL OR antiguedad_anios >= 0
    ),
    CONSTRAINT ck_local_frente CHECK (frente IS NULL OR frente >= 0),
    CONSTRAINT ck_local_carga_electrica CHECK (
        carga_electrica_kw IS NULL OR carga_electrica_kw >= 0
    ),
    CONSTRAINT ck_local_estacionamientos CHECK (
        numero_estacionamientos IS NULL OR numero_estacionamientos >= 0
    ),
    CONSTRAINT ck_local_mantenimiento CHECK (
        cuota_mantenimiento IS NULL OR cuota_mantenimiento >= 0
    )
) ENGINE=InnoDB;

-- =========================================================
-- 7-ter) Galeria de fotos del local
-- El binario vive en el almacen de archivos; aqui solo la clave opaca.
-- =========================================================
CREATE TABLE foto_local (
    id_foto         BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_local        BIGINT NOT NULL,
    clave           VARCHAR(200) NOT NULL,
    nombre_archivo  VARCHAR(150) NOT NULL,
    orden           INT NOT NULL DEFAULT 0,
    fecha_registro  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_foto_local_local
        FOREIGN KEY (id_local) REFERENCES local_comercial(id_local),
    INDEX idx_foto_local_local (id_local)
) ENGINE=InnoDB;

-- =========================================================
-- 7-bis) Publicaciones del local
-- Una publicacion representa una version concreta de un anuncio.
-- =========================================================
CREATE TABLE publicacion (
    id_publicacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_local BIGINT NOT NULL,
    canal VARCHAR(30) NOT NULL,
    url_publicacion VARCHAR(500) NULL,
    version_anuncio INT NOT NULL DEFAULT 1,
    titulo_anuncio VARCHAR(200) NOT NULL,
    renta_publicada DECIMAL(12,2) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    inversion_pauta DECIMAL(12,2) NULL,
    codigo_origen VARCHAR(50) NOT NULL,
    fecha_publicacion DATETIME NOT NULL,
    fecha_baja DATETIME NULL,
    estado VARCHAR(20) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_publicacion_local
        FOREIGN KEY (id_local) REFERENCES local_comercial(id_local),
    CONSTRAINT ck_publicacion_canal CHECK (
        canal IN ('URBANIA', 'ADONDEVIVIR', 'PROPERATI', 'NEXO_INMOBILIARIO',
                  'FACEBOOK', 'MARKETPLACE', 'INSTAGRAM', 'WHATSAPP',
                  'WEB_PROPIA', 'REFERIDO', 'OTRO')
    ),
    CONSTRAINT ck_publicacion_moneda CHECK (moneda IN ('PEN', 'USD')),
    CONSTRAINT ck_publicacion_estado CHECK (estado IN ('B', 'P', 'S', 'C')),
    CONSTRAINT ck_publicacion_montos CHECK (
        renta_publicada >= 0 AND (inversion_pauta IS NULL OR inversion_pauta >= 0)
    ),
    INDEX idx_publicacion_local (id_local),
    INDEX idx_publicacion_estado (estado),
    INDEX idx_publicacion_codigo_origen (codigo_origen)
) ENGINE=InnoDB;

-- =========================================================
-- 7-ter) Historico de precios del local
-- Una fila por hito o cambio; el precio CERRADO real vive aqui.
-- =========================================================
CREATE TABLE precio_local (
    id_precio BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_local BIGINT NOT NULL,
    hito CHAR(1) NOT NULL,
    moneda CHAR(3) NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    fecha DATE NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_precio_local
        FOREIGN KEY (id_local) REFERENCES local_comercial(id_local),
    CONSTRAINT ck_precio_hito CHECK (
        hito IN ('E', 'R', 'U', 'P', 'O', 'A', 'C')
    ),
    CONSTRAINT ck_precio_moneda CHECK (
        moneda IN ('PEN', 'USD')
    ),
    CONSTRAINT ck_precio_monto CHECK (
        monto >= 0
    ),
    INDEX ix_precio_local (id_local, fecha)
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
    motivo_operacion CHAR(1) NOT NULL DEFAULT 'A',
    urgencia INT NULL,
    exclusividad BOOLEAN NULL,
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
    CONSTRAINT ck_captacion_motivo_operacion CHECK (
        motivo_operacion = 'A'
    ),
    CONSTRAINT ck_captacion_urgencia CHECK (
        urgencia IS NULL OR (urgencia BETWEEN 1 AND 5)
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
    consentimiento_contacto BOOLEAN NULL,
    consentimiento_uso_dato BOOLEAN NULL,
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
    id_publicacion_origen BIGINT NULL,
    fuente_origen VARCHAR(30) NOT NULL DEFAULT 'OTRO',
    codigo_origen_capturado VARCHAR(50) NULL,
    fecha_primera_consulta DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    CONSTRAINT fk_oportunidad_publicacion
        FOREIGN KEY (id_publicacion_origen) REFERENCES publicacion(id_publicacion),
    CONSTRAINT ck_oportunidad_estado CHECK (
        estado IN ('A', 'S', 'N', 'F', 'X')
    ),
    CONSTRAINT ck_oportunidad_fuente CHECK (
        fuente_origen IN ('PORTAL', 'REDES_SOCIALES', 'WHATSAPP', 'LLAMADA_DIRECTA',
                          'REFERIDO', 'CARTERA_PROPIA', 'WEB_PROPIA', 'OTRO')
    ),
    CONSTRAINT uq_oportunidad_abierta_cliente_captacion UNIQUE (clave_oportunidad_abierta),
    INDEX idx_oportunidad_publicacion (id_publicacion_origen),
    INDEX idx_oportunidad_fuente (fuente_origen)
) ENGINE=InnoDB;

-- =========================================================
-- 11) Tabla de interacciones comerciales
-- Se relaciona con una unica entidad operativa: oportunidad, prospeccion,
-- captacion o cliente interesado. Una fila no puede mezclar contextos.
-- =========================================================
CREATE TABLE interaccion_comercial (
    id_interaccion BIGINT AUTO_INCREMENT PRIMARY KEY,
    contexto VARCHAR(30) NOT NULL DEFAULT 'OPORTUNIDAD',
    fecha_hora DATETIME NOT NULL,
    canal_contacto CHAR(1) NOT NULL,
    observaciones TEXT,
    resultado VARCHAR(30) NOT NULL,
    id_oportunidad BIGINT NULL,
    id_prospeccion BIGINT NULL,
    id_captacion BIGINT NULL,
    id_cliente BIGINT NULL,
    id_agente BIGINT NOT NULL,
    transcripcion_nota TEXT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interaccion_oportunidad
        FOREIGN KEY (id_oportunidad) REFERENCES oportunidad_comercial(id_oportunidad),
    CONSTRAINT fk_interaccion_captacion
        FOREIGN KEY (id_captacion) REFERENCES captacion(id_captacion),
    CONSTRAINT fk_interaccion_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente_interesado(id_cliente),
    CONSTRAINT fk_interaccion_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_interaccion_canal CHECK (
        canal_contacto IN ('L', 'W', 'E', 'P', 'R', 'T', 'O')
    ),
    CONSTRAINT ck_interaccion_contexto CHECK (
        contexto IN ('OPORTUNIDAD', 'PROSPECCION', 'CAPTACION', 'CLIENTE')
    ),
    CONSTRAINT ck_interaccion_resultado CHECK (
        (contexto = 'PROSPECCION' AND resultado IN ('CONTACTADO', 'REUNION_AGENDADA', 'PROPUESTA_ENVIADA', 'ACEPTA_CAPTAR', 'NO_ACEPTA', 'RECONTACTAR'))
        OR (contexto = 'CAPTACION' AND resultado IN ('DOCS_SOLICITADOS', 'CONDICIONES_AJUSTADAS', 'PUBLICACION_COORDINADA', 'PROPIETARIO_OBSERVA', 'LISTO_PARA_PUBLICAR', 'PAUSAR_GESTION'))
        OR (contexto = 'OPORTUNIDAD' AND resultado IN ('INTERESADO', 'VISITA_AGENDADA', 'OFERTA_SOLICITADA', 'NEGOCIANDO', 'NO_INTERESADO', 'DESCARTADO'))
        OR (contexto = 'CLIENTE' AND resultado IN ('BUSQUEDA_LEVANTADA', 'PROPUESTA_ENVIADA', 'REQUIERE_OPCIONES', 'NO_RESPONDE', 'SEGUIMIENTO', 'DESCARTADO'))
    ),
    CONSTRAINT ck_interaccion_entidad CHECK (
        (contexto = 'OPORTUNIDAD' AND id_oportunidad IS NOT NULL AND id_prospeccion IS NULL AND id_captacion IS NULL AND id_cliente IS NULL)
        OR (contexto = 'PROSPECCION' AND id_prospeccion IS NOT NULL AND id_oportunidad IS NULL AND id_captacion IS NULL AND id_cliente IS NULL)
        OR (contexto = 'CAPTACION' AND id_captacion IS NOT NULL AND id_oportunidad IS NULL AND id_prospeccion IS NULL AND id_cliente IS NULL)
        OR (contexto = 'CLIENTE' AND id_cliente IS NOT NULL AND id_oportunidad IS NULL AND id_prospeccion IS NULL AND id_captacion IS NULL)
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
    nivel_interes INT NULL,
    objecion_principal CHAR(1) NULL,
    opinion_precio CHAR(1) NULL,
    proxima_accion CHAR(1) NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_visita_oportunidad
        FOREIGN KEY (id_oportunidad) REFERENCES oportunidad_comercial(id_oportunidad),
    CONSTRAINT fk_visita_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_visita_estado CHECK (
        estado IN ('P', 'G', 'C', 'N', 'R')
    ),
    CONSTRAINT ck_visita_resultado CHECK (
        resultado IS NULL OR resultado IN ('P', 'I', 'N', 'S', 'D')
    ),
    CONSTRAINT ck_visita_nivel_interes CHECK (
        nivel_interes IS NULL OR (nivel_interes BETWEEN 1 AND 5)
    ),
    CONSTRAINT ck_visita_objecion CHECK (
        objecion_principal IS NULL OR objecion_principal IN ('P', 'U', 'E', 'C', 'O')
    ),
    CONSTRAINT ck_visita_opinion_precio CHECK (
        opinion_precio IS NULL OR opinion_precio IN ('A', 'J', 'B')
    ),
    CONSTRAINT ck_visita_proxima_accion CHECK (
        proxima_accion IS NULL OR proxima_accion IN ('V', 'O', 'S', 'D')
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
    -- Ultima accion de seguimiento con el propietario; desde el dia 8 sin accion genera alerta.
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
        fecha_recontacto IS NULL OR estado IN ('C', 'R', 'E', 'S')
    )
) ENGINE=InnoDB;

ALTER TABLE interaccion_comercial
    ADD CONSTRAINT fk_interaccion_prospeccion
        FOREIGN KEY (id_prospeccion) REFERENCES prospeccion(id_prospeccion);

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
    fecha_vigencia_oferta DATE NULL,
    -- Condiciones del trato que el broker evalua y que el contrato hereda al cerrar.
    plazo_contrato_meses INT NULL,
    fecha_inicio_contrato DATE NULL,
    forma_pago VARCHAR(20) NULL,
    meses_garantia INT NULL,
    meses_adelanto INT NULL,
    id_oportunidad BIGINT NOT NULL UNIQUE,
    id_agente BIGINT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_solicitud_oportunidad
        FOREIGN KEY (id_oportunidad) REFERENCES oportunidad_comercial(id_oportunidad),
    CONSTRAINT fk_solicitud_agente
        FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    -- Estados: G=registrada, E=en evaluacion, O=observada, A=aprobada, R=rechazada,
    -- D=descartada, C=cerrada (su alquiler se concreto en contrato).
    CONSTRAINT ck_solicitud_estado CHECK (
        estado IN ('G', 'E', 'O', 'A', 'R', 'D', 'C')
    ),
    CONSTRAINT ck_solicitud_monto CHECK (
        monto_propuesto > 0
    ),
    CONSTRAINT ck_solicitud_forma_pago CHECK (
        forma_pago IS NULL OR forma_pago IN ('TRANSFERENCIA', 'DEPOSITO_BANCARIO', 'EFECTIVO', 'CHEQUE', 'OTRO')
    ),
    CONSTRAINT ck_solicitud_plazo CHECK (plazo_contrato_meses IS NULL OR plazo_contrato_meses > 0),
    CONSTRAINT ck_solicitud_garantia CHECK (meses_garantia IS NULL OR meses_garantia >= 0),
    CONSTRAINT ck_solicitud_adelanto CHECK (meses_adelanto IS NULL OR meses_adelanto >= 0)
) ENGINE=InnoDB;

-- =========================================================
-- 14) Catalogo de documentos requeridos
-- =========================================================
CREATE TABLE tipo_documento_requerido (
    id_tipo_documento_requerido BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_operacion CHAR(1) NOT NULL,
    tipo_documento VARCHAR(60) NOT NULL,
    obligatorio BOOLEAN NOT NULL,
    activo BOOLEAN NOT NULL,
    descripcion VARCHAR(200) NULL,
    CONSTRAINT ck_tipo_documento_operacion CHECK (tipo_operacion = 'A'),
    CONSTRAINT uq_tipo_documento_operacion UNIQUE (tipo_operacion, tipo_documento)
) ENGINE=InnoDB;

-- =========================================================
-- 14-bis) Tabla de documentos de solicitud
-- Cada documento pertenece a una solicitud.
-- =========================================================
CREATE TABLE documento_solicitud (
    id_documento BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_tipo_documento_requerido BIGINT NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(255),
    fecha_entrega DATETIME NOT NULL,
    resultado_revision CHAR(1),
    observaciones TEXT,
    estado CHAR(1) NOT NULL,
    id_solicitud BIGINT NOT NULL,
    CONSTRAINT fk_documento_tipo_requerido
        FOREIGN KEY (id_tipo_documento_requerido)
        REFERENCES tipo_documento_requerido(id_tipo_documento_requerido),
    CONSTRAINT fk_documento_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES solicitud_alquiler(id_solicitud)
        ON DELETE CASCADE,
    CONSTRAINT ck_documento_estado CHECK (
        estado IN ('R', 'O', 'V')
    ),
    CONSTRAINT ck_documento_revision CHECK (
        resultado_revision IS NULL
        OR resultado_revision IN ('P', 'C', 'O')
    ),
    INDEX idx_documento_tipo_requerido (id_tipo_documento_requerido)
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
CREATE INDEX idx_captacion_codigo_estado ON captacion(codigo_captacion, estado);

CREATE INDEX idx_cliente_persona ON cliente_interesado(id_persona);

CREATE INDEX idx_oportunidad_cliente ON oportunidad_comercial(id_cliente);
CREATE INDEX idx_oportunidad_captacion ON oportunidad_comercial(id_captacion);
CREATE INDEX idx_oportunidad_agente ON oportunidad_comercial(id_agente);
CREATE INDEX idx_oportunidad_estado ON oportunidad_comercial(estado);
CREATE INDEX idx_oportunidad_captacion_estado_fecha ON oportunidad_comercial(id_captacion, estado, fecha_registro);
CREATE INDEX idx_oportunidad_agente_estado_fecha ON oportunidad_comercial(id_agente, estado, fecha_registro);

CREATE INDEX idx_interaccion_oportunidad ON interaccion_comercial(id_oportunidad);
CREATE INDEX idx_interaccion_prospeccion ON interaccion_comercial(id_prospeccion);
CREATE INDEX idx_interaccion_captacion ON interaccion_comercial(id_captacion);
CREATE INDEX idx_interaccion_cliente ON interaccion_comercial(id_cliente);
CREATE INDEX idx_interaccion_contexto ON interaccion_comercial(contexto);
CREATE INDEX idx_interaccion_agente ON interaccion_comercial(id_agente);
CREATE INDEX idx_interaccion_fecha ON interaccion_comercial(fecha_hora);
CREATE INDEX idx_interaccion_contexto_oportunidad_fecha ON interaccion_comercial(contexto, id_oportunidad, fecha_hora);
CREATE INDEX idx_interaccion_contexto_prospeccion_fecha ON interaccion_comercial(contexto, id_prospeccion, fecha_hora);
CREATE INDEX idx_interaccion_contexto_captacion_fecha ON interaccion_comercial(contexto, id_captacion, fecha_hora);
CREATE INDEX idx_interaccion_contexto_cliente_fecha ON interaccion_comercial(contexto, id_cliente, fecha_hora);

CREATE INDEX idx_visita_oportunidad ON visita(id_oportunidad);
CREATE INDEX idx_visita_agente ON visita(id_agente);
CREATE INDEX idx_visita_estado ON visita(estado);
CREATE INDEX idx_visita_oportunidad_fecha ON visita(id_oportunidad, fecha_visita, hora_visita);

CREATE INDEX idx_prospeccion_local ON prospeccion(id_local);
CREATE INDEX idx_prospeccion_agente ON prospeccion(id_agente);
CREATE INDEX idx_prospeccion_estado ON prospeccion(estado);
CREATE INDEX idx_prospeccion_recontacto ON prospeccion(fecha_recontacto);
CREATE INDEX idx_prospeccion_captacion ON prospeccion(id_captacion);
CREATE INDEX idx_prospeccion_estado_recontacto ON prospeccion(estado, fecha_recontacto);
CREATE INDEX idx_prospeccion_agente_estado_recontacto ON prospeccion(id_agente, estado, fecha_recontacto);
CREATE INDEX idx_prospeccion_local_estado ON prospeccion(id_local, estado);

CREATE INDEX idx_solicitud_oportunidad ON solicitud_alquiler(id_oportunidad);
CREATE INDEX idx_solicitud_agente ON solicitud_alquiler(id_agente);
CREATE INDEX idx_solicitud_estado ON solicitud_alquiler(estado);
CREATE INDEX idx_solicitud_oportunidad_estado_fecha ON solicitud_alquiler(id_oportunidad, estado, fecha_registro);

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

-- =========================================================
-- 19) Cierre, seguimiento y gestion operativa
-- =========================================================

-- Contrato minimo: solo formaliza el vinculo y el cierre. Las condiciones del trato
-- (renta, plazo, fecha de inicio, forma de pago, garantia, adelanto) viven en
-- solicitud_alquiler; la comision vive en comision_liquidacion. No se duplican aqui.
CREATE TABLE contrato_alquiler (
    id_contrato_alquiler BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_oportunidad BIGINT NOT NULL UNIQUE,
    id_solicitud BIGINT NULL UNIQUE,
    fecha_cierre DATE NOT NULL,
    estado_contrato VARCHAR(20) NOT NULL,
    incidencias TEXT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_contrato_oportunidad FOREIGN KEY (id_oportunidad) REFERENCES oportunidad_comercial(id_oportunidad),
    CONSTRAINT fk_contrato_solicitud FOREIGN KEY (id_solicitud) REFERENCES solicitud_alquiler(id_solicitud),
    CONSTRAINT ck_contrato_estado CHECK (
        estado_contrato IN ('P', 'D', 'V', 'R', 'F', 'S', 'A')
    )
) ENGINE=InnoDB;

CREATE TABLE historial_estado (
    id_historial_estado BIGINT AUTO_INCREMENT PRIMARY KEY,
    entidad_tipo VARCHAR(30) NOT NULL,
    entidad_id BIGINT NOT NULL,
    estado_anterior VARCHAR(40) NULL,
    estado_nuevo VARCHAR(40) NOT NULL,
    id_usuario BIGINT NOT NULL,
    fecha_evento DATETIME NOT NULL,
    observacion VARCHAR(500) NULL,
    CONSTRAINT fk_historial_usuario FOREIGN KEY (id_usuario) REFERENCES usuario_interno(id_usuario),
    CONSTRAINT ck_historial_tipo_entidad CHECK (
        entidad_tipo IN ('PROSPECCION', 'CAPTACION', 'OPORTUNIDAD', 'INTERACCION',
                         'VISITA', 'SOLICITUD_ALQUILER', 'INMUEBLE', 'PUBLICACION',
                         'CONTRATO_ALQUILER')
    ),
    INDEX idx_historial_entidad (entidad_tipo, entidad_id),
    INDEX idx_historial_fecha (fecha_evento)
) ENGINE=InnoDB;

CREATE TABLE requerimiento_cliente (
    id_requerimiento BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_cliente BIGINT NOT NULL,
    rubro VARCHAR(80) NOT NULL,
    tipo_inmueble VARCHAR(30) NULL,
    renta_min DECIMAL(12,2) NULL,
    renta_max DECIMAL(12,2) NULL,
    moneda VARCHAR(10) NOT NULL,
    metraje_min DECIMAL(10,2) NULL,
    metraje_max DECIMAL(10,2) NULL,
    frente_minimo DECIMAL(8,2) NULL,
    estado VARCHAR(20) NOT NULL,
    observaciones VARCHAR(500) NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_requerimiento_cliente FOREIGN KEY (id_cliente) REFERENCES cliente_interesado(id_cliente),
    CONSTRAINT ck_requerimiento_tipo CHECK (
        tipo_inmueble IS NULL OR tipo_inmueble IN ('LOCAL_COMERCIAL', 'OFICINA',
            'DEPOSITO_ALMACEN', 'STAND_MODULO', 'TERRENO_COMERCIAL', 'OTRO')
    ),
    CONSTRAINT ck_requerimiento_moneda CHECK (moneda IN ('PEN', 'USD')),
    CONSTRAINT ck_requerimiento_estado CHECK (estado IN ('ACTIVO', 'PAUSADO', 'CERRADO')),
    CONSTRAINT ck_requerimiento_renta CHECK (
        (renta_min IS NULL OR renta_min >= 0)
        AND (renta_max IS NULL OR renta_max >= 0)
        AND (renta_min IS NULL OR renta_max IS NULL OR renta_max >= renta_min)
    ),
    CONSTRAINT ck_requerimiento_metraje CHECK (
        (metraje_min IS NULL OR metraje_min >= 0)
        AND (metraje_max IS NULL OR metraje_max >= 0)
        AND (metraje_min IS NULL OR metraje_max IS NULL OR metraje_max >= metraje_min)
    ),
    INDEX idx_requerimiento_cliente (id_cliente),
    INDEX idx_requerimiento_estado (estado)
) ENGINE=InnoDB;

CREATE TABLE requerimiento_distrito (
    id_requerimiento BIGINT NOT NULL,
    id_distrito BIGINT NOT NULL,
    PRIMARY KEY (id_requerimiento, id_distrito),
    CONSTRAINT fk_requerimiento_distrito_requerimiento
        FOREIGN KEY (id_requerimiento) REFERENCES requerimiento_cliente(id_requerimiento) ON DELETE CASCADE,
    CONSTRAINT fk_requerimiento_distrito_distrito
        FOREIGN KEY (id_distrito) REFERENCES distrito(id_distrito)
) ENGINE=InnoDB;

CREATE TABLE tarea (
    id_tarea BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo VARCHAR(30) NOT NULL,
    entidad_tipo VARCHAR(30) NOT NULL,
    entidad_id BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    fecha_programada DATETIME NOT NULL,
    fecha_recordatorio DATETIME NULL,
    fecha_completada DATETIME NULL,
    estado VARCHAR(20) NOT NULL,
    prioridad VARCHAR(10) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tarea_agente FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_tarea_tipo CHECK (
        tipo IN ('SEGUIMIENTO', 'LLAMADA', 'VISITA', 'ENVIO_INFO', 'RECONTACTO',
                 'REPORTE_PROPIETARIO', 'ENVIAR_REVISION', 'SUBIR_DOCUMENTOS',
                 'REGISTRAR_CAPTACION', 'REGISTRAR_INTERACCION', 'PROPONER_OPORTUNIDAD', 'OTRO')
    ),
    CONSTRAINT ck_tarea_tipo_entidad CHECK (
        entidad_tipo IN ('PROSPECCION', 'CAPTACION', 'OPORTUNIDAD', 'INTERACCION',
                         'VISITA', 'SOLICITUD_ALQUILER', 'INMUEBLE', 'PUBLICACION',
                         'CONTRATO_ALQUILER', 'CLIENTE_INTERESADO', 'PROPIETARIO', 'REQUERIMIENTO')
    ),
    CONSTRAINT ck_tarea_estado CHECK (estado IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADA', 'VENCIDA', 'CANCELADA')),
    CONSTRAINT ck_tarea_prioridad CHECK (prioridad IN ('BAJA', 'MEDIA', 'ALTA')),
    INDEX idx_tarea_agente_estado (id_agente, estado),
    INDEX idx_tarea_programada (fecha_programada),
    INDEX idx_tarea_entidad (entidad_tipo, entidad_id)
) ENGINE=InnoDB;

CREATE TABLE comision_liquidacion (
    id_comision_liquidacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_contrato_alquiler BIGINT NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    monto_agente DECIMAL(12,2) NULL,
    monto_empresa DECIMAL(12,2) NULL,
    fecha_cobro DATE NULL,
    forma_pago VARCHAR(20) NULL,
    estado VARCHAR(20) NOT NULL,
    CONSTRAINT fk_comision_contrato FOREIGN KEY (id_contrato_alquiler) REFERENCES contrato_alquiler(id_contrato_alquiler),
    CONSTRAINT ck_comision_moneda CHECK (moneda IN ('PEN', 'USD')),
    CONSTRAINT ck_comision_estado CHECK (estado IN ('PENDIENTE', 'PARCIAL', 'COBRADA', 'ANULADA')),
    CONSTRAINT ck_comision_forma_pago CHECK (
        forma_pago IS NULL OR forma_pago IN ('TRANSFERENCIA', 'DEPOSITO_BANCARIO', 'EFECTIVO', 'CHEQUE', 'OTRO')
    ),
    CONSTRAINT ck_comision_montos CHECK (
        monto >= 0 AND (monto_agente IS NULL OR monto_agente >= 0)
        AND (monto_empresa IS NULL OR monto_empresa >= 0)
    ),
    INDEX idx_comision_contrato (id_contrato_alquiler),
    INDEX idx_comision_estado (estado)
) ENGINE=InnoDB;

CREATE TABLE reporte_propietario (
    id_reporte_propietario BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_captacion BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    fecha_reporte DATE NOT NULL,
    periodo_inicio DATE NULL,
    periodo_fin DATE NULL,
    consultas_reportadas INT NOT NULL,
    visitas_reportadas INT NOT NULL,
    objeciones_frecuentes VARCHAR(500) NULL,
    ajustes_recomendados VARCHAR(500) NULL,
    canal_envio CHAR(1) NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reporte_captacion FOREIGN KEY (id_captacion) REFERENCES captacion(id_captacion),
    CONSTRAINT fk_reporte_agente FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_reporte_canal CHECK (canal_envio IN ('L', 'W', 'E', 'P', 'R', 'T', 'O')),
    CONSTRAINT ck_reporte_cantidades CHECK (consultas_reportadas >= 0 AND visitas_reportadas >= 0),
    CONSTRAINT ck_reporte_periodo CHECK (periodo_fin IS NULL OR periodo_inicio IS NULL OR periodo_fin >= periodo_inicio),
    INDEX idx_reporte_captacion_fecha (id_captacion, fecha_reporte)
) ENGINE=InnoDB;

CREATE TABLE alerta (
    id_alerta BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo VARCHAR(30) NOT NULL,
    severidad VARCHAR(10) NOT NULL,
    entidad_tipo VARCHAR(30) NOT NULL,
    entidad_id BIGINT NOT NULL,
    id_agente BIGINT NOT NULL,
    mensaje VARCHAR(300) NOT NULL,
    estado VARCHAR(15) NOT NULL,
    fecha_generacion DATETIME NOT NULL,
    fecha_resolucion DATETIME NULL,
    CONSTRAINT fk_alerta_agente FOREIGN KEY (id_agente) REFERENCES agente_inmobiliario(id_agente),
    CONSTRAINT ck_alerta_tipo CHECK (
        tipo IN ('SIN_RESPUESTA', 'SIN_AVANCE', 'OFERTA_POR_VENCER',
                 'CONTRATO_POR_VENCER', 'VISITA_PROXIMA', 'CAPTACION_VENCIDA',
                 'SOLICITUD_REENVIADA', 'SOLICITUD_EVALUADA',
                 -- Avisos reales del flujo comercial (agente <-> broker): cambios de
                 -- documento/captacion/oportunidad que el backend persiste como alerta.
                 'SOLICITUD_DOCUMENTO', 'SOLICITUD_DOCUMENTO_REVISADO',
                 'CAPTACION_CREADA', 'CAPTACION_REVISADA',
                 'CAPTACION_CERRADA', 'OPORTUNIDAD_CERRADA',
                 -- Etapa 3: avisos de comision al agente (sin exponer el monto neto).
                 'COMISION_ASIGNADA', 'COMISION_COBRADA')
    ),
    CONSTRAINT ck_alerta_severidad CHECK (severidad IN ('INFO', 'MEDIA', 'ALTA')),
    CONSTRAINT ck_alerta_tipo_entidad CHECK (
        entidad_tipo IN ('PROSPECCION', 'CAPTACION', 'OPORTUNIDAD', 'INTERACCION',
                         'VISITA', 'SOLICITUD_ALQUILER', 'INMUEBLE', 'PUBLICACION',
                         'CONTRATO_ALQUILER')
    ),
    CONSTRAINT ck_alerta_estado CHECK (estado IN ('ACTIVA', 'ATENDIDA', 'DESCARTADA')),
    INDEX idx_alerta_agente_estado (id_agente, estado),
    INDEX idx_alerta_entidad (entidad_tipo, entidad_id)
) ENGINE=InnoDB;
