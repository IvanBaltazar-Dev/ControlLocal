-- C2, catálogo18–19/MAT aprobado26–27oct2026. Conservar concepto y unidad;
-- no se cambian claves, valores ni superficies mediante un cambio de rótulo.
UPDATE catalogo_atributo SET rotulo='Área construida' WHERE organizacion_id IS NULL AND clave='metraje_construido';
UPDATE catalogo_atributo SET rotulo='Área total' WHERE organizacion_id IS NULL AND clave='metraje_total';
UPDATE catalogo_atributo SET rotulo='Área arrendable' WHERE organizacion_id IS NULL AND clave='metraje_arrendable';
INSERT INTO catalogo_atributo_tipo(id_catalogo_atributo,tipo_propiedad,requerido,exigencia)
 SELECT id_catalogo_atributo,'D',false,'OPC' FROM catalogo_atributo
 WHERE organizacion_id IS NULL AND clave='zonificacion'
 AND NOT EXISTS(SELECT 1 FROM catalogo_atributo_tipo t WHERE t.id_catalogo_atributo=catalogo_atributo.id_catalogo_atributo AND t.tipo_propiedad='D');
-- D admite aportación voluntaria de zonificación, no pregunta rutinaria ni mínimo.
-- Uso no declarado permanece desconocido; el tipo físico no acredita su uso.
ALTER TABLE propiedad ALTER COLUMN uso DROP NOT NULL;
ALTER TABLE propiedad ALTER COLUMN uso DROP DEFAULT;
