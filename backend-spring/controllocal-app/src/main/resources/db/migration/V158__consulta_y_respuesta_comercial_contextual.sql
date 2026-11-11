-- Catálogo20.8 aprobado09/11. La consulta y su respuesta son declaraciones de la
-- comunicación canónica, sin otra identidad ni equivalencias con estados antiguos.
-- Sólo se incorpora la versión nueva; se conservan todas las guardas V123/V157.
ALTER TABLE interaccion_comercial DROP CONSTRAINT ck_interaccion_contrato;
ALTER TABLE interaccion_comercial ADD CONSTRAINT ck_interaccion_contrato CHECK(COALESCE(
 (version_diccionario IS NULL AND canal_contacto IN('L','W','E','P','R','T','O') AND (
  (contexto='OPORTUNIDAD' AND id_oportunidad IS NOT NULL AND id_prospeccion IS NULL AND id_captacion IS NULL AND id_rol_cliente IS NULL)
  OR (contexto='PROSPECCION' AND id_prospeccion IS NOT NULL AND id_oportunidad IS NULL AND id_captacion IS NULL AND id_rol_cliente IS NULL)
  OR (contexto='CAPTACION' AND id_captacion IS NOT NULL AND id_oportunidad IS NULL AND id_prospeccion IS NULL AND id_rol_cliente IS NULL)
  OR (contexto='CLIENTE' AND id_rol_cliente IS NOT NULL AND id_oportunidad IS NULL AND id_prospeccion IS NULL AND id_captacion IS NULL)))
 OR (version_diccionario IN('C4-COMUNICACION-20261030-1','C4-COMUNICACION-20261109-1','C4-COMUNICACION-20261110-1') AND id_rol_autor IS NOT NULL
  AND registrado_en IS NOT NULL AND contexto_declarado_json IS NOT NULL AND autorizacion_json IS NOT NULL AND procedencia_json IS NOT NULL
  AND contexto IS NULL AND canal_contacto IS NULL AND resultado IS NULL
  AND id_oportunidad IS NULL AND id_prospeccion IS NULL AND id_captacion IS NULL AND id_rol_cliente IS NULL
  AND contacto_tipo IS NULL AND contacto_nombre IS NULL AND contacto_telefono IS NULL),FALSE));
