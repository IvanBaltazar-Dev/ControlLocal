-- La disponibilidad comercial es una maquina distinta del estado A/I del
-- registro. Su historial usa un tipo propio para que D/R/A/T nunca se mezcle
-- semanticamente con los codigos del registro de propiedad.
INSERT INTO entidad_tipo (codigo, descripcion, auditable, referenciable)
VALUES ('DISPONIBILIDAD_PROPIEDAD', 'Disponibilidad comercial de propiedad', TRUE, FALSE);
