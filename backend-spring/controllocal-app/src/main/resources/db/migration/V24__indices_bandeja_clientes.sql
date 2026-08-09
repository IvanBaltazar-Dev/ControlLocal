-- V24 - Indices de la bandeja de clientes (GET /clientes y /clientes/resumen).
--
-- La pantalla de clientes deja de descargar la cartera y filtrar en memoria: el
-- texto, el tipo de persona, el rubro y el estado bajan al servidor. El texto
-- busca en nombre o razon social, numero de documento y rubro comercial, que
-- viven en DOS tablas (persona y detalle_cliente), asi que se resuelve por
-- conjunto de candidatos —una rama por tabla, unidas con UNION— igual que el
-- listado de locales (contrato-listados-paginados.md §5).
--
-- Ese patron solo rinde si CADA rama tiene su indice. V11 ya dejo el trigrama
-- de lower(persona.nombres_o_razon_social); faltan los otros dos campos
-- buscables. Se indexa la EXPRESION lower(columna) porque es lo que compara la
-- consulta, igual que en V11 y V23.
--
-- El cuarto indice es del recorrido, no de la busqueda: la bandeja ordena por
-- id_persona_rol DESC dentro del tenant, y los filtros de estado y tipo de
-- persona se resuelven sobre la persona del rol.
--
-- No toca datos ni esquema: solo indices. Es reversible con DROP INDEX.

CREATE INDEX ix_persona_documento_trgm
    ON persona USING gin (lower(numero_documento) gin_trgm_ops);

CREATE INDEX ix_detalle_cliente_rubro_trgm
    ON detalle_cliente USING gin (lower(rubro_comercial) gin_trgm_ops);

CREATE INDEX ix_detalle_cliente_org_id
    ON detalle_cliente (organizacion_id, id_persona_rol DESC);
