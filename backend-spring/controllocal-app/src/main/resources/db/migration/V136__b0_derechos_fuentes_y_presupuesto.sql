-- B0, plan6/directriz5–8.9/CAT-E38. Sin semillas de perfiles, dinero o usuarios.
-- Presupuesto técnico/perfiles: datos privados de plataforma, NO catálogo público.
CREATE TABLE b0_fondo_tecnico (
 id_fondo uuid PRIMARY KEY, moneda varchar(3) NOT NULL CHECK(moneda='USD'),
 financiado numeric(38,12) NOT NULL CHECK(financiado>=0),
 retenido numeric(38,12) NOT NULL DEFAULT 0 CHECK(retenido>=0),
 liquidado numeric(38,12) NOT NULL DEFAULT 0 CHECK(liquidado>=0),
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL,
 evidencia_financiacion text NOT NULL CHECK(length(trim(evidencia_financiacion))>0),habilitado boolean NOT NULL DEFAULT false,
 version bigint NOT NULL DEFAULT 0,
 CHECK(hasta>desde),CHECK(retenido+liquidado<=financiado)
);
CREATE TABLE b0_perfil_operativo (
 perfil varchar(60) NOT NULL,version varchar(80) NOT NULL,
 id_fondo uuid NOT NULL REFERENCES b0_fondo_tecnico(id_fondo),
 normalizador numeric(38,12) NOT NULL CHECK(normalizador>0),tarifas_referencia jsonb NOT NULL,
 maximo_creditos numeric(38,12) NOT NULL CHECK(maximo_creditos>0),
 maximo_tecnico_usd numeric(38,12) NOT NULL CHECK(maximo_tecnico_usd>0),
 limite_intentos integer NOT NULL CHECK(limite_intentos>0),
 evidencia_calibracion text NOT NULL CHECK(length(trim(evidencia_calibracion))>0),evidencia_aprobacion text NOT NULL CHECK(length(trim(evidencia_aprobacion))>0),
 habilitado boolean NOT NULL DEFAULT false,PRIMARY KEY(perfil,version)
);
CREATE TABLE b0_ciclo (
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),id_ciclo uuid NOT NULL,
 plan varchar(20) NOT NULL CHECK(plan IN('ONE','ONE_PRO','TEAM','GROWTH','SCALE','PRIME')),
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL,
 dia_aniversario integer NOT NULL CHECK(dia_aniversario BETWEEN 1 AND 31),
 importe_financiado_usd numeric(12,2) NOT NULL CHECK(importe_financiado_usd>=0),
 orden_verificada uuid NOT NULL,evidencia_pago text NOT NULL,
 revocado boolean NOT NULL DEFAULT false,registrado_en timestamptz NOT NULL,
 cerrado_en timestamptz,brokers_extra integer NOT NULL DEFAULT 0 CHECK(brokers_extra>=0),version bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(organizacion_id,id_ciclo),UNIQUE(organizacion_id,orden_verificada),UNIQUE(organizacion_id,desde),CHECK(hasta>desde),
 CHECK(cerrado_en IS NULL OR cerrado_en BETWEEN desde AND hasta)
);
CREATE TABLE b0_perfil_actual (
 perfil varchar(60) PRIMARY KEY,version varchar(80) NOT NULL,
 FOREIGN KEY(perfil,version) REFERENCES b0_perfil_operativo(perfil,version)
);
CREATE INDEX ix_b0_ciclo_vigente ON b0_ciclo(organizacion_id,desde,hasta);
CREATE TABLE b0_acreditacion_pago (
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),id_pedido uuid NOT NULL,
 proveedor varchar(100) NOT NULL,transaccion varchar(200) NOT NULL,
 huella varchar(64) NOT NULL,contenido jsonb NOT NULL,registrada_en timestamptz NOT NULL,
 PRIMARY KEY(organizacion_id,id_pedido),UNIQUE(proveedor,transaccion)
);
CREATE TABLE b0_linea_financiada (
 organizacion_id bigint NOT NULL,id_linea uuid NOT NULL,id_ciclo uuid NOT NULL,id_pedido uuid NOT NULL,
 concepto varchar(20) NOT NULL CHECK(concepto IN('PLAN','BROKER_EXTRA')),
 financiado_usd numeric(12,2) NOT NULL CHECK(financiado_usd>=0),
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL,
 PRIMARY KEY(organizacion_id,id_linea),CHECK(hasta>desde),
 FOREIGN KEY(organizacion_id,id_ciclo) REFERENCES b0_ciclo(organizacion_id,id_ciclo),
 FOREIGN KEY(organizacion_id,id_pedido) REFERENCES b0_acreditacion_pago(organizacion_id,id_pedido)
);
CREATE TABLE b0_fuente_capacidad (
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),id_fuente uuid NOT NULL,
 id_ciclo uuid,origen varchar(30) NOT NULL CHECK(origen IN('MENSUAL_AGENTE','MENSUAL_BROKER','RESERVA','RESERVA_ASIGNADA','BOOST')),
 id_persona_destinataria bigint,plaza integer,origen_verificado varchar(200) NOT NULL,
 inicial numeric(38,12) NOT NULL CHECK(inicial>=0),retenido numeric(38,12) NOT NULL DEFAULT 0 CHECK(retenido>=0),
 liquidado numeric(38,12) NOT NULL DEFAULT 0 CHECK(liquidado>=0),
 desde timestamptz NOT NULL,vence timestamptz NOT NULL,registrado_en timestamptz NOT NULL,
 id_fuente_origen uuid,
 PRIMARY KEY(organizacion_id,id_fuente),
 FOREIGN KEY(organizacion_id,id_ciclo) REFERENCES b0_ciclo(organizacion_id,id_ciclo),
 FOREIGN KEY(organizacion_id,id_persona_destinataria) REFERENCES persona(organizacion_id,id_persona),
 FOREIGN KEY(organizacion_id,id_fuente_origen) REFERENCES b0_fuente_capacidad(organizacion_id,id_fuente),
 UNIQUE(organizacion_id,origen_verificado),CHECK(vence>desde),CHECK(retenido+liquidado<=inicial),
 CHECK((origen='BOOST' AND id_ciclo IS NULL) OR (origen<>'BOOST' AND id_ciclo IS NOT NULL)),
 CHECK(origen NOT IN('MENSUAL_BROKER','RESERVA','BOOST') OR id_persona_destinataria IS NULL),
 CHECK(origen<>'RESERVA_ASIGNADA' OR (id_persona_destinataria IS NOT NULL AND id_fuente_origen IS NOT NULL))
);
CREATE UNIQUE INDEX uk_b0_plaza_ciclo ON b0_fuente_capacidad(organizacion_id,id_ciclo,plaza) WHERE origen='MENSUAL_AGENTE';
CREATE UNIQUE INDEX uk_b0_persona_ciclo ON b0_fuente_capacidad(organizacion_id,id_ciclo,id_persona_destinataria)
 WHERE origen='MENSUAL_AGENTE' AND id_persona_destinataria IS NOT NULL;
CREATE UNIQUE INDEX uk_b0_bolsa_ciclo ON b0_fuente_capacidad(organizacion_id,id_ciclo,origen)
 WHERE origen IN('MENSUAL_BROKER','RESERVA');
CREATE INDEX ix_b0_fuentes_disponibles ON b0_fuente_capacidad(organizacion_id,vence,id_fuente);
CREATE TABLE b0_plaza (
 organizacion_id bigint NOT NULL,id_ciclo uuid NOT NULL,tipo varchar(10) NOT NULL CHECK(tipo IN('AGENTE','BROKER')),
 numero integer NOT NULL CHECK(numero>0),id_persona bigint,id_fuente uuid NOT NULL,
 PRIMARY KEY(organizacion_id,id_ciclo,tipo,numero),UNIQUE(organizacion_id,id_ciclo,tipo,id_persona),
 FOREIGN KEY(organizacion_id,id_ciclo) REFERENCES b0_ciclo(organizacion_id,id_ciclo),
 FOREIGN KEY(organizacion_id,id_persona) REFERENCES persona(organizacion_id,id_persona),
 FOREIGN KEY(organizacion_id,id_fuente) REFERENCES b0_fuente_capacidad(organizacion_id,id_fuente)
);
CREATE TABLE b0_operacion_ia (
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),id_operacion uuid NOT NULL,
 id_persona bigint NOT NULL,id_rol bigint NOT NULL,
 perfil varchar(60) NOT NULL,version_perfil varchar(80) NOT NULL,
 recurso_tipo varchar(60) NOT NULL,recurso_id varchar(100) NOT NULL,finalidad varchar(100) NOT NULL,
 huella_origen varchar(64) NOT NULL,huella_peticion varchar(64) NOT NULL,
 estado varchar(20) NOT NULL CHECK(estado IN('RESERVADA','EN_PROCESO','INCIERTA','LIQUIDADA','CANCELADA')),
 maximo_creditos numeric(38,12) NOT NULL CHECK(maximo_creditos>0),
 maximo_tecnico_usd numeric(38,12) NOT NULL CHECK(maximo_tecnico_usd>0),
 id_fondo uuid NOT NULL REFERENCES b0_fondo_tecnico(id_fondo),
 admitida_en timestamptz NOT NULL,iniciada_en timestamptz,liquidada_en timestamptz,
 huella_resultado varchar(64),creditos_liquidados numeric(38,12),costo_tecnico_usd numeric(38,12),
 version bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(organizacion_id,id_operacion),
 FOREIGN KEY(organizacion_id,id_persona) REFERENCES persona(organizacion_id,id_persona),
 FOREIGN KEY(organizacion_id,id_rol) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 FOREIGN KEY(perfil,version_perfil) REFERENCES b0_perfil_operativo(perfil,version),
 CHECK(creditos_liquidados IS NULL OR creditos_liquidados BETWEEN 0 AND maximo_creditos),
 CHECK(costo_tecnico_usd IS NULL OR costo_tecnico_usd BETWEEN 0 AND maximo_tecnico_usd),
 CHECK((estado IN('LIQUIDADA','CANCELADA'))=(liquidada_en IS NOT NULL))
);
CREATE TABLE b0_imputacion (
 organizacion_id bigint NOT NULL,id_operacion uuid NOT NULL,id_fuente uuid NOT NULL,
 retenido numeric(38,12) NOT NULL CHECK(retenido>0),liquidado numeric(38,12),
 orden integer NOT NULL CHECK(orden>0),
 PRIMARY KEY(organizacion_id,id_operacion,id_fuente),UNIQUE(organizacion_id,id_operacion,orden),
 FOREIGN KEY(organizacion_id,id_operacion) REFERENCES b0_operacion_ia(organizacion_id,id_operacion),
 FOREIGN KEY(organizacion_id,id_fuente) REFERENCES b0_fuente_capacidad(organizacion_id,id_fuente),
 CHECK(liquidado IS NULL OR liquidado BETWEEN 0 AND retenido)
);
CREATE TABLE b0_medicion_tecnica (
 organizacion_id bigint NOT NULL,id_operacion uuid NOT NULL,id_medicion uuid NOT NULL,
 proveedor varchar(100) NOT NULL,modelo varchar(150) NOT NULL,version_tarifa varchar(100) NOT NULL,
 unidades_originales jsonb NOT NULL,tarifas_originales jsonb NOT NULL,
 costo_usd numeric(38,12) NOT NULL CHECK(costo_usd>=0),
 estado_costo varchar(20) NOT NULL CHECK(estado_costo IN('CALCULADO','CONFIRMADO')),
 referencia_fuente text NOT NULL,huella varchar(64) NOT NULL,huella_intento varchar(64) NOT NULL UNIQUE,registrada_en timestamptz NOT NULL,
 PRIMARY KEY(organizacion_id,id_operacion,id_medicion),
 FOREIGN KEY(organizacion_id,id_operacion) REFERENCES b0_operacion_ia(organizacion_id,id_operacion)
);
CREATE TABLE b0_antecedente (
 id_antecedente bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 id_operacion uuid,hecho varchar(60) NOT NULL,contenido jsonb NOT NULL,registrado_en timestamptz NOT NULL,
 FOREIGN KEY(organizacion_id,id_operacion) REFERENCES b0_operacion_ia(organizacion_id,id_operacion)
);
-- GB comercial decimal aprobado31Oct; el margen esencial sigue sin semillas inventadas.
CREATE TABLE b0_politica_almacenamiento (
 version varchar(80) NOT NULL,plan varchar(20) NOT NULL CHECK(plan IN('FREE','ONE','ONE_PRO','TEAM','GROWTH','SCALE','PRIME')),
 margen_esencial_bytes bigint CHECK(margen_esencial_bytes>=0),
 evidencia_aprobacion text NOT NULL,evidencia_margen_financiado text,
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL,habilitada boolean NOT NULL DEFAULT false,
 PRIMARY KEY(version,plan),CHECK(hasta>desde),
 CHECK(COALESCE(margen_esencial_bytes,0)=0 OR (evidencia_margen_financiado IS NOT NULL AND length(trim(evidencia_margen_financiado))>0))
);
CREATE TABLE b0_uso_almacenamiento (
 organizacion_id bigint PRIMARY KEY REFERENCES organizacion(id_organizacion),
 confirmados bigint NOT NULL DEFAULT 0 CHECK(confirmados>=0),reservados bigint NOT NULL DEFAULT 0 CHECK(reservados>=0),
 version bigint NOT NULL DEFAULT 0
);
CREATE TABLE b0_politica_almacenamiento_actual (
 plan varchar(20) PRIMARY KEY,version varchar(80) NOT NULL,
 FOREIGN KEY(version,plan) REFERENCES b0_politica_almacenamiento(version,plan)
);
CREATE TABLE b0_objeto_almacenamiento (
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),huella_identidad varchar(64) NOT NULL,
 huella_contenido varchar(64) NOT NULL,bytes bigint NOT NULL CHECK(bytes>=0),categoria varchar(30) NOT NULL,
 referencia_comprobacion text NOT NULL,confirmado_en timestamptz NOT NULL,
 PRIMARY KEY(organizacion_id,huella_identidad)
);
CREATE TABLE b0_reserva_almacenamiento (
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),id_reserva uuid NOT NULL,
 id_persona bigint NOT NULL,id_rol bigint NOT NULL,huella_peticion varchar(64) NOT NULL,
 recurso_tipo varchar(60) NOT NULL,recurso_id varchar(100) NOT NULL,finalidad varchar(100) NOT NULL,huella_origen varchar(64) NOT NULL,
 categoria varchar(30) NOT NULL,necesidad varchar(40) NOT NULL,bytes bigint NOT NULL CHECK(bytes>=0),huella_contenido varchar(64) NOT NULL,
 bytes_retenidos bigint NOT NULL CHECK(bytes_retenidos>=0),version_politica varchar(80),contexto_limite jsonb NOT NULL DEFAULT '{}'::jsonb,
 estado varchar(20) NOT NULL CHECK(estado IN('RESERVADA','ALMACENANDO','INCIERTA','CONFIRMADA','CANCELADA')),
 huella_objeto varchar(64),creada_en timestamptz NOT NULL,terminada_en timestamptz,
 PRIMARY KEY(organizacion_id,id_reserva),
 FOREIGN KEY(organizacion_id,id_persona) REFERENCES persona(organizacion_id,id_persona),
 FOREIGN KEY(organizacion_id,id_rol) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CHECK((estado IN('CONFIRMADA','CANCELADA'))=(terminada_en IS NOT NULL)),
 CHECK(estado<>'CONFIRMADA' OR huella_objeto IS NOT NULL)
);
CREATE FUNCTION b0_conservar_antecedente() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'B0 conserva antecedentes inmutables'; END $$;
CREATE TRIGGER tg_b0_medicion_inmutable BEFORE UPDATE OR DELETE ON b0_medicion_tecnica
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER tg_b0_antecedente_inmutable BEFORE UPDATE OR DELETE ON b0_antecedente
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER tg_b0_pago_inmutable BEFORE UPDATE OR DELETE ON b0_acreditacion_pago
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER tg_b0_linea_inmutable BEFORE UPDATE OR DELETE ON b0_linea_financiada
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER tg_b0_perfil_inmutable BEFORE UPDATE OR DELETE ON b0_perfil_operativo
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER tg_b0_politica_almacen_inmutable BEFORE UPDATE OR DELETE ON b0_politica_almacenamiento
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER tg_b0_objeto_almacen_inmutable BEFORE UPDATE OR DELETE ON b0_objeto_almacenamiento
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
