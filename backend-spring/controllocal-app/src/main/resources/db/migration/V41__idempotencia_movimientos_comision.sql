-- =====================================================================
-- Idempotencia de los comandos monetarios de comision.
--
-- QUE PROBLEMA RESUELVE. `POST /contratos/{id}/comision/movimientos` no tenia
-- forma de distinguir un REINTENTO de un segundo abono legitimo, asi que un
-- doble clic o un reenvio por timeout cobraba dos veces. La suite
-- `e2e-comision-movimientos` lo dejaba fijado como defecto conocido.
--
-- POR QUE NO SE DEDUPLICA POR CONTENIDO. `(tipo, monto, moneda, fecha,
-- liquidacion)` NO identifica un comando: dos abonos de 300 el mismo dia son
-- perfectamente legitimos y deduplicarlos perderia dinero real. La identidad
-- la aporta QUIEN LLAMA, con una clave explicita por operacion:
--
--   * el SPA genera un UUID al iniciar la operacion;
--   * si reintenta ESA operacion, reenvia la MISMA clave;
--   * una operacion nueva estrena clave.
--
-- POR QUE UN INDICE UNICO Y NO UN `exists()`. Un `exists()` previo deja una
-- ventana entre la comprobacion y el INSERT: dos peticiones simultaneas la
-- atraviesan las dos y cobran dos veces. El indice es el guardian real y
-- ademas sobrevive a varias instancias del API; la lectura previa solo sirve
-- para responder rapido y con un mensaje util en el caso normal.
--
-- PARCIAL, `WHERE clave_idempotencia IS NOT NULL`. Mientras el contrato legado
-- siga congelado la cabecera es OPCIONAL: los movimientos sin clave conviven
-- sin colisionar entre si. El SPA nuevo la manda siempre.
--
-- LA HUELLA distingue "reintento" de "reutilizacion indebida de la clave":
-- misma clave + misma huella devuelve el resultado original; misma clave +
-- huella distinta es un 409, porque el cliente esta reusando una clave para
-- otra cosa y eso casi siempre es un error suyo.
-- =====================================================================

ALTER TABLE comision_movimiento
    ADD COLUMN clave_idempotencia VARCHAR(64),
    ADD COLUMN huella_comando     VARCHAR(64);

ALTER TABLE comision_movimiento
    ADD CONSTRAINT ck_movimiento_idempotencia CHECK (
        (clave_idempotencia IS NULL AND huella_comando IS NULL)
        OR (btrim(clave_idempotencia) <> '' AND huella_comando IS NOT NULL));

-- El tenant va delante: la clave la elige el cliente y solo tiene que ser
-- unica DENTRO de su organizacion.
CREATE UNIQUE INDEX uq_movimiento_idempotencia
    ON comision_movimiento (organizacion_id, clave_idempotencia)
    WHERE clave_idempotencia IS NOT NULL;

COMMENT ON COLUMN comision_movimiento.clave_idempotencia IS
    'Idempotency-Key del comando que creo la fila. Unica por organizacion.';
COMMENT ON COLUMN comision_movimiento.huella_comando IS
    'SHA-256 del comando. Misma clave con huella distinta = 409.';
