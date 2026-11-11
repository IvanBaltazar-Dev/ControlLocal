-- CAT18, MAT03 y D6-07/TR07: revisión agrupada propia. No otro reloj D2,
-- planificador Radar o notificación. La fecha original de detección se conserva.
ALTER TABLE borrador_captura ADD COLUMN revision_pospuesta_hasta date;
ALTER TABLE borrador_captura ADD COLUMN motivo_posposicion varchar(500);
ALTER TABLE borrador_captura ADD CONSTRAINT ck_captura_posposicion_completa CHECK(
 (revision_pospuesta_hasta IS NULL AND motivo_posposicion IS NULL)
 OR (revision_pospuesta_hasta IS NOT NULL AND nullif(btrim(motivo_posposicion),'') IS NOT NULL));
