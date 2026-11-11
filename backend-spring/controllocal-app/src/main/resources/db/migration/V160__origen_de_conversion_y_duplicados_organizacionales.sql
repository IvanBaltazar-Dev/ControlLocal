-- Catálogo20.9, aprobación10/11/2026. No inferir DIRECTO u origen desde enlaces antiguos.
DROP INDEX uq_captacion_viva_por_operacion;
CREATE UNIQUE INDEX uq_captacion_viva_por_operacion
 ON captacion(organizacion_id,id_propiedad,motivo_operacion) WHERE estado IN ('P','A');
COMMENT ON INDEX uq_captacion_viva_por_operacion IS 'Un episodio vivo por organización, registro de propiedad y operación; sin reserva o señal entre organizaciones.';
ALTER TABLE captacion
 ADD COLUMN tipo_origen_prospeccion VARCHAR(20) NOT NULL DEFAULT 'DESCONOCIDO',
 ADD COLUMN id_prospeccion_origen BIGINT,
 ADD COLUMN origen_registrado_en TIMESTAMPTZ,
 ADD CONSTRAINT ck_c7_origen_encargo CHECK(tipo_origen_prospeccion IN ('DIRECTO','PROSPECCION','DESCONOCIDO')
  AND ((tipo_origen_prospeccion='PROSPECCION')=(id_prospeccion_origen IS NOT NULL))),
 ADD CONSTRAINT fk_c7_origen_prospeccion FOREIGN KEY(organizacion_id,id_prospeccion_origen)
  REFERENCES prospeccion(organizacion_id,id_prospeccion);
CREATE TABLE encargo_primera_habilitacion (
 organizacion_id BIGINT NOT NULL,id_captacion BIGINT NOT NULL,id_revision_encargo BIGINT NOT NULL,
 fecha_habilitacion DATE NOT NULL,registrada_en TIMESTAMPTZ NOT NULL,
 id_prospeccion_origen BIGINT,id_rol_responsable_prospeccion BIGINT,
 PRIMARY KEY(organizacion_id,id_captacion),
 FOREIGN KEY(organizacion_id,id_captacion,id_revision_encargo)
  REFERENCES revision_encargo(organizacion_id,id_captacion,id_revision_encargo),
 FOREIGN KEY(organizacion_id,id_prospeccion_origen) REFERENCES prospeccion(organizacion_id,id_prospeccion),
 FOREIGN KEY(organizacion_id,id_rol_responsable_prospeccion) REFERENCES detalle_agente(organizacion_id,id_persona_rol),
 CHECK((id_prospeccion_origen IS NULL)=(id_rol_responsable_prospeccion IS NULL))
);
CREATE INDEX ix_c7_conversion_prospeccion ON encargo_primera_habilitacion(organizacion_id,id_prospeccion_origen,fecha_habilitacion);
CREATE TABLE encargo_antecedente_prospeccion (
 organizacion_id BIGINT NOT NULL,id_captacion BIGINT NOT NULL,id_prospeccion BIGINT NOT NULL,
 id_rol_actor BIGINT NOT NULL,registrada_en TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(organizacion_id,id_captacion,id_prospeccion),
 FOREIGN KEY(organizacion_id,id_captacion) REFERENCES captacion(organizacion_id,id_captacion),
 FOREIGN KEY(organizacion_id,id_prospeccion) REFERENCES prospeccion(organizacion_id,id_prospeccion),
 FOREIGN KEY(organizacion_id,id_rol_actor) REFERENCES persona_rol(organizacion_id,id_persona_rol)
);
CREATE TRIGGER conservar_primera_habilitacion BEFORE UPDATE OR DELETE ON encargo_primera_habilitacion FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_primera_habilitacion_truncate BEFORE TRUNCATE ON encargo_primera_habilitacion FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_antecedente_prospeccion BEFORE UPDATE OR DELETE ON encargo_antecedente_prospeccion FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_antecedente_prospeccion_truncate BEFORE TRUNCATE ON encargo_antecedente_prospeccion FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();
