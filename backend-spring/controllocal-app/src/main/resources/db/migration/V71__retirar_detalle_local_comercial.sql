-- =====================================================================
-- V71 - Corte 0A: se retira la ultima tabla espejo.
--
-- QUE PROBLEMA CIERRA
-- `detalle_local_comercial` guarda tres conceptos que el catalogo ya declara
-- como atributos gobernados: `rubro_permitido`, `apto_licencia_funcionamiento`
-- y `carga_electrica_kw`. Son la ultima doble verdad que quedaba despues de
-- V60-V62, y la unica que ademas era una tabla POR TIPO -- la forma que el
-- modelo universal vino a eliminar.
--
-- POR QUE NO BASTA CON UN DROP
-- V48 hizo el backfill y desde entonces NADIE reconcilia: `PUT /locales`
-- escribia solo el espejo, `PUT /propiedades/{id}` escribe solo el atributo.
-- Medido el 2026-08-21:
--
--   controllocal_dev            0 divergencias (21 filas espejo, 21 atributos)
--   controllocal_repositorios  63 rubros solo en el espejo, 65 solo en atributo
--
-- El cero de dev es SUERTE, no construccion: significa "nadie ha editado un
-- rubro desde V48". En cuanto las dos puertas se usan, el hueco aparece. Un
-- DROP sin backfill se llevaria por delante esos rubros -- en la base de
-- pruebas, 63 de ellos, uno concreto: la propiedad 2172, "Cafeteria".
--
-- QUE NO HACE: ARBITRAR
-- Esta migracion NO elige entre dos valores distintos del mismo concepto. Si
-- encuentra una sola divergencia, se detiene. Rellena donde falta y nada mas:
-- el atributo es la autoridad declarada, y el espejo solo puede aportar lo que
-- la autoridad todavia no tiene.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. LA GUARDA DE DIVERGENCIA. Va PRIMERO, antes de tocar una sola fila.
--
-- Se para en seco ante un solo desacuerdo, y a proposito: elegir un ganador
-- seria decidir en una migracion -- sin ver los datos, sin poder preguntar y
-- sin dejar rastro de la decision -- cual de las dos verdades era la buena. Ese
-- es exactamente el tipo de arbitraje callado que el Corte 0A viene a impedir.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    divergentes INTEGER;
    detalle     TEXT;
BEGIN
    SELECT count(*), string_agg(linea, E'\n  ' ORDER BY linea)
      INTO divergentes, detalle
      FROM (
        SELECT 'propiedad ' || d.id_propiedad || ' rubro_permitido: espejo="'
               || d.rubro_permitido || '" atributo="' || a.valor_texto || '"' AS linea
          FROM detalle_local_comercial d
          JOIN atributo_propiedad a ON a.id_propiedad = d.id_propiedad
                                   AND a.clave = 'rubro_permitido'
         WHERE nullif(trim(d.rubro_permitido), '') IS DISTINCT FROM a.valor_texto
        UNION ALL
        SELECT 'propiedad ' || d.id_propiedad || ' apto_licencia_funcionamiento: espejo='
               || d.apto_licencia_funcionamiento || ' atributo=' || a.valor_booleano
          FROM detalle_local_comercial d
          JOIN atributo_propiedad a ON a.id_propiedad = d.id_propiedad
                                   AND a.clave = 'apto_licencia_funcionamiento'
         WHERE d.apto_licencia_funcionamiento IS NOT NULL
           AND a.valor_booleano IS NOT NULL
           AND d.apto_licencia_funcionamiento IS DISTINCT FROM a.valor_booleano
        UNION ALL
        SELECT 'propiedad ' || d.id_propiedad || ' carga_electrica_kw: espejo='
               || d.carga_electrica_kw || ' atributo=' || a.valor_numero
          FROM detalle_local_comercial d
          JOIN atributo_propiedad a ON a.id_propiedad = d.id_propiedad
                                   AND a.clave = 'carga_electrica_kw'
         WHERE d.carga_electrica_kw IS NOT NULL
           AND a.valor_numero IS NOT NULL
           AND d.carga_electrica_kw <> a.valor_numero
      ) AS d(linea);

    IF divergentes > 0 THEN
        RAISE EXCEPTION E'V71 se detiene: % dato(s) valen cosas distintas en la tabla espejo y en su atributo.\n  %\nV71 NO arbitra cual gana. Concilia estos casos a mano y vuelve a migrar.',
            divergentes, detalle;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 2. Backfill espejo -> autoridad. Solo donde falta.
--
-- `where not exists` y no `on conflict`: "no lo pises si ya esta" es la regla
-- que se quiere, y asi se lee tal cual.
-- ---------------------------------------------------------------------
INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
SELECT d.organizacion_id, d.id_propiedad, 'rubro_permitido', trim(d.rubro_permitido)
  FROM detalle_local_comercial d
 WHERE nullif(trim(d.rubro_permitido), '') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                    WHERE a.id_propiedad = d.id_propiedad
                      AND a.clave = 'rubro_permitido');

INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_booleano)
SELECT d.organizacion_id, d.id_propiedad, 'apto_licencia_funcionamiento',
       d.apto_licencia_funcionamiento
  FROM detalle_local_comercial d
 WHERE d.apto_licencia_funcionamiento IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                    WHERE a.id_propiedad = d.id_propiedad
                      AND a.clave = 'apto_licencia_funcionamiento');

INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero)
SELECT d.organizacion_id, d.id_propiedad, 'carga_electrica_kw', d.carga_electrica_kw
  FROM detalle_local_comercial d
 WHERE d.carga_electrica_kw IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                    WHERE a.id_propiedad = d.id_propiedad
                      AND a.clave = 'carga_electrica_kw');

-- ---------------------------------------------------------------------
-- 3. El invariante de rango se muda ANTES de que su CHECK desaparezca.
--
-- `ck_detalle_local_carga` decia `carga_electrica_kw >= 0`. Es la MISMA
-- situacion que V62: al retirar la columna, el CHECK se va con ella y el
-- atributo que la sustituye no valida rango por si mismo -- su trigger
-- comprueba el TIPO del valor, no cuanto vale. Sin esta linea, "carga >= 0"
-- pasaria a ser "carga cualquier cosa" en silencio: no se pierde un dato, se
-- pierde una regla, que es peor porque no se nota hasta que entra el primero
-- negativo.
--
-- `catalogo_atributo.valor_minimo` es donde vive esa regla desde V62.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo
   SET valor_minimo = 0
 WHERE clave = 'carga_electrica_kw' AND del_sistema AND valor_minimo IS NULL;

-- ---------------------------------------------------------------------
-- 4. La reconciliacion, otra vez, con el backfill ya hecho.
--
-- Dos guardas y no una: la de arriba mira que nadie DISCREPE, esta mira que
-- nadie se quede FUERA. Un valor que el backfill no alcanzara -- por un
-- trigger, por un tipo al que la clave no aplique, por lo que sea -- se lo
-- llevaria el DROP, y el fallo no se veria hasta que un broker abriera una
-- ficha vacia dentro de un mes.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    huerfanos INTEGER;
BEGIN
    SELECT count(*) INTO huerfanos
      FROM detalle_local_comercial d
     WHERE (nullif(trim(d.rubro_permitido), '') IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                             WHERE a.id_propiedad = d.id_propiedad
                               AND a.clave = 'rubro_permitido'))
        OR (d.apto_licencia_funcionamiento IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                             WHERE a.id_propiedad = d.id_propiedad
                               AND a.clave = 'apto_licencia_funcionamiento'))
        OR (d.carga_electrica_kw IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                             WHERE a.id_propiedad = d.id_propiedad
                               AND a.clave = 'carga_electrica_kw'));

    IF huerfanos > 0 THEN
        RAISE EXCEPTION
            'V71 se detiene: % fila(s) de detalle_local_comercial tienen un valor que NO llego a atributo_propiedad. El DROP las perderia.',
            huerfanos;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 5. El indice de busqueda se muda con el dato.
--
-- V23 creo `ix_detalle_local_rubro_trgm` porque la busqueda de /locales mira
-- `lower(rubro_permitido)` con LIKE '%...%', y sin trigramas eso es un barrido.
-- El DROP se lleva el indice; si el equivalente no viaja en ESTA migracion la
-- consulta sigue devolviendo lo mismo y solo se vuelve lenta -- y una suite que
-- afirma p95 fallaria por tiempo, que es como este repositorio ya perdio una
-- regresion entera creyendola cosa de la maquina.
--
-- Parcial por `clave` a proposito: sirve a la busqueda de rubro, no a todo
-- `valor_texto`. El predicado literal `clave = 'rubro_permitido'` tiene que
-- aparecer en la consulta para que el planificador lo use, y aparece.
-- ---------------------------------------------------------------------
CREATE INDEX ix_atributo_rubro_trgm
    ON atributo_propiedad USING gin (lower(valor_texto) gin_trgm_ops)
 WHERE clave = 'rubro_permitido';

COMMENT ON INDEX ix_atributo_rubro_trgm IS
    'Busqueda por rubro con LIKE. Sustituye a ix_detalle_local_rubro_trgm (V23).';

-- ---------------------------------------------------------------------
-- 6. Fuera la tabla espejo.
--
-- QUE MAS SE VA CON ELLA, revisado uno a uno:
--
--   detalle_local_comercial_pkey (id_propiedad)
--       -> `uq_atributo_propiedad_clave (id_propiedad, clave)` da la misma
--          garantia: un valor por propiedad y concepto.
--   ix_detalle_local_comercial_organizacion
--       -> `ix_atributo_propiedad_organizacion`, ya existe.
--   ix_detalle_local_rubro_trgm            -> sustituido arriba.
--   ck_detalle_local_carga (carga >= 0)    -> mudado arriba a valor_minimo.
--   FK a propiedad y a organizacion
--       -> `atributo_propiedad` tiene las suyas, y ademas el trigger
--          `tg_atributo_gobernado`, que el espejo no tenia.
--
-- Y DOS QUE NO SE CONSERVAN, dicho aqui para que no desaparezcan sin más:
--
--   rubro_permitido NOT NULL. Era el invariante de una tabla POR TIPO: si
--       existe la fila, hay rubro. El catalogo declara `requerido = false`
--       para L, O y A desde V48, o sea que el sistema lleva desde entonces
--       con dos opiniones contrarias sobre lo mismo. V71 lo resuelve a favor
--       de la autoridad declarada, que es lo que dice D-E4-3, y NO cambia la
--       exigencia: subirla es material del Corte 0B, no de una contencion.
--
--   VARCHAR(120) en el rubro. `valor_texto` es TEXT y el catalogo no sabe
--       declarar longitud maxima todavia. Queda anotado como evidencia para
--       0B, junto al techo numerico: hoy ninguna clave puede decir cuanto
--       mide su valor.
-- ---------------------------------------------------------------------
DROP TABLE detalle_local_comercial;
