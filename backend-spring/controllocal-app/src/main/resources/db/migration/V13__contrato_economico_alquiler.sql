-- Estabilizacion transversal del contrato economico de alquiler.
--
-- No se asigna una moneda por defecto: una renta sin moneda es incompleta y
-- elegir PEN o USD en nombre del usuario corromperia el significado del dato.
-- Las restricciones NOT VALID se aplican a toda escritura nueva, pero dejan
-- los registros historicos ambiguos sin convertir hasta que su origen pueda
-- demostrarse. V14 sanea por separado solo los seeds/fixtures probados.

ALTER TABLE propiedad
    ADD COLUMN moneda_referencial VARCHAR(3);

ALTER TABLE solicitud_alquiler
    ADD COLUMN moneda VARCHAR(3);

ALTER TABLE captacion
    DROP CONSTRAINT ck_captacion_comision;

ALTER TABLE captacion
    ADD CONSTRAINT ck_captacion_comision_porcentaje
    CHECK (comision_pactada >= 0 AND comision_pactada <= 200)
    NOT VALID;

ALTER TABLE propiedad
    ADD CONSTRAINT ck_propiedad_moneda_referencial
    CHECK (moneda_referencial IS NOT NULL AND moneda_referencial IN ('PEN', 'USD'))
    NOT VALID;

ALTER TABLE solicitud_alquiler
    ADD CONSTRAINT ck_solicitud_moneda
    CHECK (moneda IS NOT NULL AND moneda IN ('PEN', 'USD'))
    NOT VALID;

COMMENT ON COLUMN propiedad.moneda_referencial IS
    'Moneda obligatoria de precio_referencial para nuevas escrituras (PEN/USD).';
COMMENT ON COLUMN solicitud_alquiler.moneda IS
    'Moneda obligatoria de monto_propuesto; la heredan contrato, hito C y comision.';
COMMENT ON COLUMN captacion.comision_pactada IS
    'Porcentaje sobre una renta mensual, permitido de 0 a 200.';
