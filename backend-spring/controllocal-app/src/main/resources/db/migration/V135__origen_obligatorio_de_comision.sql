-- C5 conserva la identidad del contexto para venta/alquiler y el contrato de
-- las filas históricas. Retirar el NOT NULL del alquiler no admite raíces
-- sin ninguno de esos orígenes; no modifica ni acredita datos anteriores.
ALTER TABLE comision_liquidacion
 ADD CONSTRAINT ck_c5_comision_origen CHECK (
   id_contexto_economico IS NOT NULL OR id_contrato_alquiler IS NOT NULL
 );
