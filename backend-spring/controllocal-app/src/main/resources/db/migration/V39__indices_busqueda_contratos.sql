-- =====================================================================
-- Entrada indexada a contrato_alquiler desde las ramas de busqueda
-- (RC-003, §5 del contrato de listados).
--
-- La bandeja de cierres busca texto en CUATRO tablas: la direccion de la
-- propiedad, el codigo de la captacion, el de la oportunidad y el nombre
-- del cliente. Las cuatro ya tienen su indice trigrama; lo que faltaba era
-- poder VOLVER al contrato desde cada acierto sin recorrer la tabla.
--
-- Medido sobre un banco de 100.000 contratos (EXPLAIN ANALYZE, BUFFERS):
--
--   termino      candidatos   OR cruzado    ramas+UNION indexadas
--   hitodir              20      257,8 ms                  1,28 ms
--   hitoop               20      450,2 ms                  1,16 ms
--   hitocap              20      269,0 ms                  2,91 ms
--   hitocli             500      309,2 ms                 32,04 ms
--   'contrato 1'     55.338      856,0 ms                603,21 ms
--
-- El OR cruzado no podia usar NINGUNO de los cuatro indices: PostgreSQL no
-- combina trigramas de tablas distintas dentro de un mismo OR y los degrada
-- a `Join Filter`, con Seq Scan paralelo sobre las cuatro tablas grandes
-- (`Rows Removed by Join Filter: 49990` por worker para devolver 20 filas).
--
-- Con la ultima columna el plan no tiene ni un Seq Scan. La ganancia se
-- estrecha cuando el termino deja de ser selectivo (ultima fila): ahi hay que
-- tocar media tabla de todas formas y lo que queda es el coste real del
-- trabajo, no el del barrido. Nunca es peor.
--
-- ix_contrato_captacion (V-anterior) ya cubre la rama de la captacion.
-- uq_contrato_raiz_oportunidad NO sirve para la rama de la oportunidad: es
-- parcial (`where id_contrato_anterior is null`) y deja fuera las renovaciones.
-- =====================================================================

CREATE INDEX ix_contrato_propiedad
    ON contrato_alquiler (organizacion_id, id_propiedad);

CREATE INDEX ix_contrato_cliente
    ON contrato_alquiler (organizacion_id, id_rol_cliente);

CREATE INDEX ix_contrato_oportunidad
    ON contrato_alquiler (organizacion_id, id_oportunidad);

COMMENT ON INDEX ix_contrato_propiedad IS
    'Rama de busqueda por direccion de la propiedad (RC-003).';
COMMENT ON INDEX ix_contrato_cliente IS
    'Rama de busqueda por nombre del cliente (RC-003).';
COMMENT ON INDEX ix_contrato_oportunidad IS
    'Rama de busqueda por codigo de oportunidad, incluidas renovaciones (RC-003).';
