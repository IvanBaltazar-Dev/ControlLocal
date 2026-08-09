-- =====================================================================
-- Vinculo inequivoco entre la tarea de revision y el contrato que la origino.
--
-- POR QUE HACE FALTA. `REVISION_INMUEBLE` se crea al finalizar o rescindir un
-- contrato, pero se guarda como (entidad_tipo='INMUEBLE', entidad_id=propiedad):
-- desde la tarea no hay forma de saber QUE contrato la produjo. Resolverla al
-- revisar exigiria buscar "alguna tarea abierta de este inmueble", que es
-- adivinar: si el local tuvo dos contratos sucesivos, la revision del segundo
-- podria cerrar la tarea del primero.
--
-- La columna es NULLABLE porque solo la usan las tareas con contrato origen;
-- las otras nueve clases de tarea no lo tienen y no se les inventa uno.
-- =====================================================================

ALTER TABLE tarea ADD COLUMN id_contrato_origen BIGINT;

ALTER TABLE tarea
    ADD CONSTRAINT fk_tarea_contrato_origen_org
        FOREIGN KEY (organizacion_id, id_contrato_origen)
        REFERENCES contrato_alquiler (organizacion_id, id_contrato_alquiler);

-- Resolver la revision de un contrato es un acceso por clave, no un barrido.
CREATE INDEX ix_tarea_contrato_origen
    ON tarea (organizacion_id, id_contrato_origen)
    WHERE id_contrato_origen IS NOT NULL;

COMMENT ON COLUMN tarea.id_contrato_origen IS
    'Contrato que origino la tarea. Hoy solo lo llevan las REVISION_INMUEBLE '
    'de un contrato finalizado, rescindido o anulado tras formalizar.';
