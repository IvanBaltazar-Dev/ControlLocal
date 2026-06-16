-- Separa la asistencia de la visita de su resultado comercial.
-- N = No realizada. Cancelada se conserva para citas anuladas previamente.

ALTER TABLE visita
    DROP CHECK ck_visita_estado;

ALTER TABLE visita
    ADD CONSTRAINT ck_visita_estado
        CHECK (estado IN ('P', 'G', 'C', 'N', 'R'));
