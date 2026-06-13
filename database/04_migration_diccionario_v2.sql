-- =========================================================
-- Migracion Diccionario de Datos v2 (P1 + ajustes de bajo esfuerzo)
-- Motor: MySQL 8.0.36
-- Regla: ningun cambio rompe lo existente; todo es aditivo
-- (columnas opcionales, tablas nuevas y ampliacion de dominios).
-- Ejecutar sobre una base creada con 01_create_schema_controllocal_v3.sql.
-- =========================================================

USE controllocal;

-- =========================================================
-- 1) Catalogo de distritos (P1)
-- Lista cerrada de distritos donde opera la corredora. Se siembra con los
-- valores ya usados en local_comercial.distrito (texto libre actual).
-- =========================================================
CREATE TABLE distrito (
    id_distrito BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    provincia VARCHAR(100) NOT NULL DEFAULT 'Lima',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT INTO distrito (nombre)
SELECT DISTINCT TRIM(distrito)
FROM local_comercial
WHERE distrito IS NOT NULL AND TRIM(distrito) <> '';

-- =========================================================
-- 2) Atributos del inmueble + publicacion + geolocalizacion (P1)
-- local_comercial.distrito (VARCHAR) se conserva; id_distrito enlaza el catalogo.
-- =========================================================
ALTER TABLE local_comercial
    ADD COLUMN tipo_inmueble CHAR(1) NULL AFTER id_propietario,
    ADD COLUMN uso CHAR(1) NULL AFTER tipo_inmueble,
    ADD COLUMN ambientes INT NULL AFTER uso,
    ADD COLUMN antiguedad_anios INT NULL AFTER ambientes,
    ADD COLUMN zona_urbanizacion VARCHAR(150) NULL AFTER antiguedad_anios,
    ADD COLUMN geo_lat DECIMAL(10,7) NULL AFTER zona_urbanizacion,
    ADD COLUMN geo_long DECIMAL(10,7) NULL AFTER geo_lat,
    ADD COLUMN estado_publicacion CHAR(1) NULL AFTER geo_long,
    ADD COLUMN id_distrito BIGINT NULL AFTER estado_publicacion,
    ADD CONSTRAINT fk_local_distrito FOREIGN KEY (id_distrito) REFERENCES distrito(id_distrito),
    ADD CONSTRAINT ck_local_tipo_inmueble CHECK (
        tipo_inmueble IS NULL OR tipo_inmueble IN ('L', 'O', 'D', 'C', 'T', 'X')
    ),
    ADD CONSTRAINT ck_local_uso CHECK (
        uso IS NULL OR uso IN ('C', 'V', 'I', 'M')
    ),
    ADD CONSTRAINT ck_local_ambientes CHECK (
        ambientes IS NULL OR ambientes > 0
    ),
    ADD CONSTRAINT ck_local_antiguedad CHECK (
        antiguedad_anios IS NULL OR antiguedad_anios >= 0
    ),
    ADD CONSTRAINT ck_local_estado_publicacion CHECK (
        estado_publicacion IS NULL OR estado_publicacion IN ('B', 'P', 'S', 'C')
    );

UPDATE local_comercial lc
INNER JOIN distrito d ON d.nombre = TRIM(lc.distrito)
SET lc.id_distrito = d.id_distrito;

-- =========================================================
-- 3) Historico de precios del local (P1)
-- Una fila por hito o cambio. El precio CERRADO es obligatorio al finalizar
-- exitosa una operacion (regla de negocio en la capa BL).
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
-- 4) Consentimientos (P1)
-- =========================================================
ALTER TABLE persona
    ADD COLUMN consentimiento_uso_dato BOOLEAN NULL AFTER estado;

ALTER TABLE cliente_interesado
    ADD COLUMN consentimiento_contacto BOOLEAN NULL AFTER rubro_comercial,
    ADD COLUMN consentimiento_uso_dato BOOLEAN NULL AFTER consentimiento_contacto;

-- =========================================================
-- 5) Canal de contacto: +REUNION (R) +PORTAL (T) y transcripcion (P2 bajo)
-- =========================================================
ALTER TABLE interaccion_comercial
    DROP CONSTRAINT ck_interaccion_canal;

ALTER TABLE interaccion_comercial
    ADD CONSTRAINT ck_interaccion_canal CHECK (
        canal_contacto IN ('L', 'W', 'E', 'P', 'R', 'T', 'O')
    ),
    ADD COLUMN transcripcion_nota TEXT NULL AFTER id_agente;

-- =========================================================
-- 6) Atributos cualitativos de la visita (P2 bajo)
-- =========================================================
ALTER TABLE visita
    ADD COLUMN nivel_interes INT NULL AFTER id_agente,
    ADD COLUMN objecion_principal CHAR(1) NULL AFTER nivel_interes,
    ADD COLUMN opinion_precio CHAR(1) NULL AFTER objecion_principal,
    ADD COLUMN proxima_accion CHAR(1) NULL AFTER opinion_precio,
    ADD CONSTRAINT ck_visita_nivel_interes CHECK (
        nivel_interes IS NULL OR (nivel_interes BETWEEN 1 AND 5)
    ),
    ADD CONSTRAINT ck_visita_objecion CHECK (
        objecion_principal IS NULL OR objecion_principal IN ('P', 'U', 'E', 'C', 'O')
    ),
    ADD CONSTRAINT ck_visita_opinion_precio CHECK (
        opinion_precio IS NULL OR opinion_precio IN ('A', 'J', 'B')
    ),
    ADD CONSTRAINT ck_visita_proxima_accion CHECK (
        proxima_accion IS NULL OR proxima_accion IN ('V', 'O', 'S', 'D')
    );

-- =========================================================
-- 7) Condiciones del encargo de captacion (P2 bajo)
-- =========================================================
ALTER TABLE captacion
    ADD COLUMN motivo_operacion CHAR(1) NULL AFTER id_broker_revisor,
    ADD COLUMN urgencia INT NULL AFTER motivo_operacion,
    ADD COLUMN exclusividad BOOLEAN NULL AFTER urgencia,
    ADD CONSTRAINT ck_captacion_motivo_operacion CHECK (
        motivo_operacion IS NULL OR motivo_operacion IN ('A', 'C')
    ),
    ADD CONSTRAINT ck_captacion_urgencia CHECK (
        urgencia IS NULL OR (urgencia BETWEEN 1 AND 5)
    );

-- =========================================================
-- Nota: el desenlace de la oportunidad (F -> cerrada_favorable, X -> caida)
-- es DERIVADO del estado y se calcula en la capa de modelo
-- (OportunidadComercial.getDesenlace); no se persiste.
-- =========================================================
