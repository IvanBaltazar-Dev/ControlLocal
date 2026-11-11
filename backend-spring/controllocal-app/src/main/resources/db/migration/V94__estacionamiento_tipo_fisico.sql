-- =====================================================================
-- V94 - ESTACIONAMIENTO es un tipo fisico independiente
-- =====================================================================
--
-- E identifica una cochera/estacionamiento que se registra como cosa fisica
-- propia. No se crea en esta migracion una relacion con un departamento: el
-- esquema no tiene una FK propiedad-propiedad y esa relacion necesita una
-- decision funcional y juridica sobre conjunto, unidad y procedencia.
--
-- La identidad registral permanece en PROPIEDAD. partida_registral y
-- oficina_registral son anulables; NULL conserva "todavia no se sabe" y no
-- inventa una partida. La procedencia sigue siendo la que ya gobierna el alta
-- en Core.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. El vocabulario persistido, en una sola autoridad.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION tipos_de_propiedad()
RETURNS SETOF varchar(1)
LANGUAGE sql
IMMUTABLE
AS $$
    VALUES ('L'::varchar(1)), ('O'), ('D'), ('C'), ('T'), ('A'), ('E'), ('X');
$$;

COMMENT ON FUNCTION tipos_de_propiedad() IS
    'Los ocho codigos de tipo fisico de propiedad. E identifica ESTACIONAMIENTO y debe coincidir con los checks y la aplicabilidad.';

ALTER TABLE propiedad DROP CONSTRAINT IF EXISTS ck_propiedad_tipo;
ALTER TABLE propiedad
    ADD CONSTRAINT ck_propiedad_tipo
    CHECK (tipo_inmueble IN ('L', 'O', 'D', 'C', 'T', 'A', 'E', 'X'));

ALTER TABLE catalogo_atributo_tipo DROP CONSTRAINT IF EXISTS ck_catalogo_atributo_tipo;
ALTER TABLE catalogo_atributo_tipo
    ADD CONSTRAINT ck_catalogo_atributo_tipo
    CHECK (tipo_propiedad IN ('L', 'O', 'D', 'C', 'T', 'A', 'E', 'X'));

ALTER TABLE catalogo_atributo_operacion DROP CONSTRAINT IF EXISTS ck_catalogo_operacion_tipo;
ALTER TABLE catalogo_atributo_operacion
    ADD CONSTRAINT ck_catalogo_operacion_tipo
    CHECK (tipo_propiedad IN ('L', 'O', 'D', 'C', 'T', 'A', 'E', 'X'));

ALTER TABLE tipo_documento_requerido DROP CONSTRAINT IF EXISTS ck_tipo_documento_propiedad;
ALTER TABLE tipo_documento_requerido
    ADD CONSTRAINT ck_tipo_documento_propiedad
    CHECK (tipo_propiedad IS NULL
           OR tipo_propiedad IN ('L', 'O', 'D', 'C', 'T', 'A', 'E', 'X'));

-- Si una autoridad declaraba que aplicaba a todos, V86 ya debio dejar una
-- fila L. No continuar silenciosamente si el estado historico no cumple esa
-- precondicion: el E nuevo tiene que conservar la misma exigencia.
DO $$
DECLARE
    faltantes text;
BEGIN
    SELECT string_agg(c.clave, ', ' ORDER BY c.clave) INTO faltantes
      FROM catalogo_atributo c
     WHERE c.aplica_todos
       AND c.sujeto = 'PROPIEDAD'
       AND NOT EXISTS (
           SELECT 1
             FROM catalogo_atributo_tipo t
            WHERE t.id_catalogo_atributo = c.id_catalogo_atributo
              AND t.tipo_propiedad = 'L');
    IF faltantes IS NOT NULL THEN
        RAISE EXCEPTION 'V94: no se puede ampliar aplica_todos sin fila base L para %', faltantes;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 2. Aplicabilidad del catalogo existente.
-- ---------------------------------------------------------------------
-- Las claves que ya declaraban aplica_todos reciben E con la exigencia de su
-- fila L. Esto conserva ALT/OPC y evita convertir una clave obligatoria en
-- opcional por el mero hecho de introducir el nuevo tipo.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'E', base.requerido, base.exigencia
  FROM catalogo_atributo c
  JOIN catalogo_atributo_tipo base
    ON base.id_catalogo_atributo = c.id_catalogo_atributo
   AND base.tipo_propiedad = 'L'
 WHERE c.aplica_todos
   AND c.sujeto = 'PROPIEDAD'
   AND NOT EXISTS (
       SELECT 1
         FROM catalogo_atributo_tipo existente
        WHERE existente.id_catalogo_atributo = c.id_catalogo_atributo
          AND existente.tipo_propiedad = 'E');

-- La identidad registral y las cargas pertenecen a cualquier propiedad
-- inscribible, incluida una cochera independiente. No se hace obligatorio
-- conocerlas al registrar: la ausencia conserva el estado FALTANTE.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'E', false, 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL
   AND c.sujeto = 'PROPIEDAD'
   AND c.clave IN ('partida_registral', 'oficina_registral', 'cargas_gravamenes')
   AND NOT EXISTS (
       SELECT 1
         FROM catalogo_atributo_tipo existente
        WHERE existente.id_catalogo_atributo = c.id_catalogo_atributo
          AND existente.tipo_propiedad = 'E');

-- El estado de ocupacion y su condicion de entrega tambien describen una
-- cochera fisica. Se amplia solo este par transversal; no se copian condiciones
-- comerciales de departamentos, locales u oficinas por parecido de nombres.
INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad, requerido, exigencia)
SELECT c.id_catalogo_atributo, 'E', false, 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL
   AND c.sujeto = 'PROPIEDAD'
   AND c.clave = 'estado_ocupacion'
   AND NOT EXISTS (
       SELECT 1
         FROM catalogo_atributo_tipo existente
        WHERE existente.id_catalogo_atributo = c.id_catalogo_atributo
          AND existente.tipo_propiedad = 'E');

INSERT INTO catalogo_atributo_operacion (id_catalogo_atributo, tipo_propiedad,
                                         tipo_operacion, exigencia)
SELECT c.id_catalogo_atributo, 'E', 'V', 'OPC'
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL
   AND c.sujeto = 'ENCARGO'
   AND c.clave = 'entrega_desocupado'
   AND NOT EXISTS (
       SELECT 1
         FROM catalogo_atributo_operacion existente
        WHERE existente.id_catalogo_atributo = c.id_catalogo_atributo
          AND existente.tipo_propiedad = 'E'
          AND existente.tipo_operacion = 'V');

-- ---------------------------------------------------------------------
-- 3. La lista y sus checks no pueden divergir.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    fila record;
    codigo varchar(1);
    definicion text;
BEGIN
    FOR fila IN
        SELECT * FROM (VALUES
            ('propiedad', 'ck_propiedad_tipo'),
            ('catalogo_atributo_tipo', 'ck_catalogo_atributo_tipo'),
            ('catalogo_atributo_operacion', 'ck_catalogo_operacion_tipo'),
            ('tipo_documento_requerido', 'ck_tipo_documento_propiedad')
        ) AS checks(tabla, nombre)
    LOOP
        SELECT pg_get_constraintdef(con.oid) INTO definicion
          FROM pg_constraint con
          JOIN pg_class rel ON rel.oid = con.conrelid
         WHERE con.conname = fila.nombre
           AND rel.relname = fila.tabla;
        IF definicion IS NULL THEN
            RAISE EXCEPTION 'V94: falta % en %', fila.nombre, fila.tabla;
        END IF;

        FOR codigo IN SELECT * FROM tipos_de_propiedad() LOOP
            IF position('''' || codigo || '''' IN definicion) = 0 THEN
                RAISE EXCEPTION 'V94: % no admite el tipo %: %', fila.nombre, codigo, definicion;
            END IF;
        END LOOP;
    END LOOP;

    SELECT string_agg(DISTINCT p.tipo_inmueble, ', ') INTO definicion
      FROM propiedad p
     WHERE p.tipo_inmueble NOT IN (SELECT * FROM tipos_de_propiedad());
    IF definicion IS NOT NULL THEN
        RAISE EXCEPTION 'V94: propiedad contiene tipos desconocidos: %', definicion;
    END IF;

    SELECT string_agg(DISTINCT t.tipo_propiedad, ', ') INTO definicion
      FROM catalogo_atributo_tipo t
     WHERE t.tipo_propiedad NOT IN (SELECT * FROM tipos_de_propiedad());
    IF definicion IS NOT NULL THEN
        RAISE EXCEPTION 'V94: aplicabilidad contiene tipos desconocidos: %', definicion;
    END IF;

    SELECT string_agg(DISTINCT o.tipo_propiedad, ', ') INTO definicion
      FROM catalogo_atributo_operacion o
     WHERE o.tipo_propiedad NOT IN (SELECT * FROM tipos_de_propiedad());
    IF definicion IS NOT NULL THEN
        RAISE EXCEPTION 'V94: aplicabilidad de encargo contiene tipos desconocidos: %', definicion;
    END IF;

    SELECT string_agg(DISTINCT d.tipo_propiedad, ', ') INTO definicion
      FROM tipo_documento_requerido d
     WHERE d.tipo_propiedad IS NOT NULL
       AND d.tipo_propiedad NOT IN (SELECT * FROM tipos_de_propiedad());
    IF definicion IS NOT NULL THEN
        RAISE EXCEPTION 'V94: documentos requeridos contienen tipos desconocidos: %', definicion;
    END IF;
END $$;
