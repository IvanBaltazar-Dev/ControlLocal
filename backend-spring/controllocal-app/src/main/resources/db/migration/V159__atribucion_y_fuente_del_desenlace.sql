-- CAT-E29 §20.8: actor, responsable histórico y fuente del hecho son datos distintos.
-- No se reconstruyen atribuciones anteriores a partir de responsables actuales.
ALTER TABLE revision_hito_contractual ADD CONSTRAINT uq_c7_revision_hito_org
 UNIQUE(organizacion_id,id_revision_hito);
ALTER TABLE historial_estado
 ADD COLUMN id_rol_responsable BIGINT,
 ADD COLUMN id_revision_hito BIGINT,
 ADD CONSTRAINT fk_c7_historial_responsable FOREIGN KEY(organizacion_id,id_rol_responsable)
  REFERENCES detalle_agente(organizacion_id,id_persona_rol),
 ADD CONSTRAINT fk_c7_historial_hito FOREIGN KEY(organizacion_id,id_revision_hito)
  REFERENCES revision_hito_contractual(organizacion_id,id_revision_hito),
 ADD CONSTRAINT ck_c7_historial_fuente_cierre CHECK(id_revision_hito IS NULL OR
  (entidad_tipo='OPORTUNIDAD' AND estado_nuevo='F'));
