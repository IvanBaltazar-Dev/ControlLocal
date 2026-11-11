-- RCA-C3-CONSUMIDORES-322: Network ya entrega UUID canónica; R3 conserva la
-- referencia exacta. No cambia productor, motor, desempate, permiso ni historia.
-- V120 aplicada se conserva; sólo se corrige la frontera de formato del CHECK.
ALTER TABLE matching_referencia DROP CONSTRAINT ck_matching_referencia_opaca;

ALTER TABLE matching_referencia ADD CONSTRAINT ck_matching_referencia_opaca CHECK (
    (referencia_candidato IS NULL
     OR referencia_candidato ~ '^[a-f0-9]{64}$'
     OR (capa = 'L2' AND tipo_recurso = 'NETWORK'
         AND referencia_recurso IS NOT NULL
         AND referencia_candidato = referencia_recurso
         AND referencia_candidato ~ '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$')) IS TRUE
);

-- IS TRUE impide que UNKNOWN acepte una rama UUID sin recurso. NOT NULL del
-- recurso, FKs, índices, capa/tipo, inmutabilidad y retirada monotónica siguen.
-- NULL legado sigue permitido por el CHECK; tg_matching_referencia_r3 continúa
-- exigiendo referencia no nula para recorridos R3. No se reescriben filas.
