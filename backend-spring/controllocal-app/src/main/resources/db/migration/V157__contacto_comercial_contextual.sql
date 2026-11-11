-- Catálogo20.7 aprobado09/11. Admite la nueva versión sin traducir ni retirar la anterior.
-- Se conservan íntegros los requisitos de ambas ramas del contrato original V123.
ALTER TABLE interaccion_comercial DROP CONSTRAINT ck_interaccion_contrato;
ALTER TABLE interaccion_comercial ADD CONSTRAINT ck_interaccion_contrato CHECK(COALESCE(
 (version_diccionario IS NULL AND canal_contacto IN('L','W','E','P','R','T','O') AND (
  (contexto='OPORTUNIDAD' AND id_oportunidad IS NOT NULL AND id_prospeccion IS NULL AND id_captacion IS NULL AND id_rol_cliente IS NULL)
  OR (contexto='PROSPECCION' AND id_prospeccion IS NOT NULL AND id_oportunidad IS NULL AND id_captacion IS NULL AND id_rol_cliente IS NULL)
  OR (contexto='CAPTACION' AND id_captacion IS NOT NULL AND id_oportunidad IS NULL AND id_prospeccion IS NULL AND id_rol_cliente IS NULL)
  OR (contexto='CLIENTE' AND id_rol_cliente IS NOT NULL AND id_oportunidad IS NULL AND id_prospeccion IS NULL AND id_captacion IS NULL)))
 OR (version_diccionario IN('C4-COMUNICACION-20261030-1','C4-COMUNICACION-20261109-1') AND id_rol_autor IS NOT NULL
  AND registrado_en IS NOT NULL AND contexto_declarado_json IS NOT NULL AND autorizacion_json IS NOT NULL AND procedencia_json IS NOT NULL
  AND contexto IS NULL AND canal_contacto IS NULL AND resultado IS NULL
  AND id_oportunidad IS NULL AND id_prospeccion IS NULL AND id_captacion IS NULL AND id_rol_cliente IS NULL
  AND contacto_tipo IS NULL AND contacto_nombre IS NULL AND contacto_telefono IS NULL),FALSE));
