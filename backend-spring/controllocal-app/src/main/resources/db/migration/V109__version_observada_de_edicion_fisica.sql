-- C2/K1, RF013 / B2.3, AC053/055, CAT-E05. Sólo control técnico del físico.
-- Los hechos de Encargo/disponibilidad conservan su propia autoridad y versión.
ALTER TABLE propiedad ADD COLUMN version_fisica bigint NOT NULL DEFAULT 0;
ALTER TABLE propiedad ADD CONSTRAINT ck_propiedad_version_fisica CHECK (version_fisica >= 0);
COMMENT ON COLUMN propiedad.version_fisica IS
 'Revisión física observada; se compara y avanza en el mismo caso autorizado bajo bloqueo de propiedad. No versiona pactos comerciales.';
