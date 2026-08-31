-- =====================================================================
-- SANEAMIENTO DEL RESIDUO QUE DEJARON LAS PRUEBAS EN LA BASE DE PRUEBAS
-- (D0, 2026-08-30 -- fichas N27 y N13 de `docs/ai/pendientes-brox.md`)
--
--   docker exec -i controllocal-postgres-v2 \
--       psql -U controllocal -d controllocal_repositorios -v ON_ERROR_STOP=1 \
--       < backend-spring/verificacion/sanear-residuo-de-pruebas.sql
--
-- ESTO NO ES UNA MIGRACION Y NO TOCA EL ESQUEMA. Repara DATOS de
-- `controllocal_repositorios` --la base contra la que corre la integracion--
-- que ninguna escritura de hoy puede producir y que hacen MENTIR a dos
-- comprobaciones del gate. Sobre `controllocal_dev` es un no-op: alli el
-- universo de las dos es cero, medido el 2026-08-30.
--
-- POR QUE HACE FALTA. Un rojo de residuo se confunde con un rojo de defecto, y
-- entonces el gate deja de servir para medir sobre la unica base que tiene
-- datos. Eso es lo que se paga aqui.
--
-- ES IDEMPOTENTE: la segunda pasada no encuentra nada y no cambia ninguna fila.
-- Y TERMINA EN ROJO si despues de actuar queda alguna infractora, para que
-- nadie de por saneado lo que no lo esta.
--
-- LO QUE **NO** HACE, Y ES DELIBERADO:
--   * no inventa linaje. Escribir un rastro que diga «esto lo declaro alguien»
--     seria fabricar la procedencia que no existe, que es justo lo prohibido;
--   * no borra ninguna fila de valor. Lo unico que se retira es un valor que el
--     catalogo NO ADMITE (ver bloque 1);
--   * no toca `controllocal_dev` de forma distinta: se puede correr en las dos.
-- =====================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

\echo ''
\echo '== ANTES =='
SELECT current_database() AS base, frontera_de_linaje() AS frontera;

-- ---------------------------------------------------------------------
-- BLOQUE 1 - `propiedad.piso` escrito sin linaje despues del cutover
--
-- QUE ES. 54 propiedades registradas el 2026-08-25 entre las 10:12 y las 11:14
-- UTC --es decir, por las corridas de 5A-- con `piso = '4'` y sin ningun rastro
-- de `piso`. Las escribio el productor que ponia `ubicacion.piso` por fuera del
-- enrutador de autoridad, el agujero que 4.P cerro en su tercera vuelta.
--
-- POR QUE SE DECLARA FALTANTE Y NO SE LE FABRICA UN RASTRO. Dos razones, y la
-- segunda es la que decide:
--
--   1. Su procedencia no se conoce ni se puede reconstruir. Un dato que no se
--      sabe se declara FALTANTE; rellenarlo con el caso frecuente esta
--      prohibido.
--   2. EL CATALOGO NO ADMITE ESE VALOR. `piso` aplica a `D`, `L` y `O` --
--      departamento, local y oficina-- y las 54 filas son de tipo `A`, `C`, `T`
--      y `X`: 12 almacenes, 18 casas, 12 terrenos y 12 mas. Un TERRENO con
--      «piso 4» no es un dato de procedencia dudosa: es un valor que hoy
--      ninguna puerta acepta --`AtributosGobernados.exigirQueAplique` lo
--      rechaza en el alta y en la edicion-- y que nunca debio existir.
--
-- Por eso retirarlo no pierde conocimiento acumulado y no necesita reemplazo:
-- no habia nada que conservar, y el mecanismo que lo produjo ya no existe.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE residuo_piso AS
SELECT p.id_propiedad, p.codigo, p.tipo_inmueble, p.piso, p.fecha_registro
  FROM propiedad p
 WHERE p.fecha_registro > frontera_de_linaje()
   AND p.piso IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM rastro_valor_gobernado r
         JOIN catalogo_atributo c ON c.clave = r.clave
                                 AND c.campo_estructural = 'PISO'
                                 AND (c.organizacion_id IS NULL
                                   OR c.organizacion_id = r.organizacion_id)
        WHERE r.organizacion_id = p.organizacion_id
          AND r.sujeto = 'PROPIEDAD'
          AND r.id_agregado = p.id_propiedad);

SELECT tipo_inmueble, piso, count(*) AS filas,
       min(fecha_registro) AS desde, max(fecha_registro) AS hasta
  FROM residuo_piso GROUP BY tipo_inmueble, piso ORDER BY tipo_inmueble;

SELECT count(*) AS piso_sin_linaje_antes FROM residuo_piso;

UPDATE propiedad SET piso = NULL
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_piso);

-- ---------------------------------------------------------------------
-- BLOQUE 2 - legado fechado ANTES que su propia propiedad
--
-- QUE ES. Cuatro filas de `servicios_disponibles` --PROP-8681, PROP-8682,
-- PROP-8771 y PROP-8772-- con `fecha_creacion` = frontera - 1 dia sobre
-- propiedades registradas el 2026-08-26. Las dejo la version del fixture
-- `sembrarLegadoAmbiguo` anterior a `N13`, que envejecia el atributo y se
-- olvidaba de envejecer la propiedad.
--
-- COMO SE REPARA, Y POR QUE ASI. No se borra la fila: el legado es el dato y
-- retirarlo perderia lo unico que esas propiedades tienen. Lo que esta mal es la
-- fecha de la PROPIEDAD, y se le pone la que el productor corregido le pone hoy
-- a todas las que crea -- `frontera_de_linaje() - 2 dias`, un dia antes que su
-- legado--. No se inventa nada nuevo: se le aplica al residuo la misma regla que
-- ya gobierna a las que nacen ahora, y la linea de tiempo vuelve a ser
-- propiedad -> legado -> frontera.
--
-- LO QUE ESO NO PUEDE TAPAR, MEDIDO. Envejecer una propiedad la saca del
-- universo de «4P despues del cutover ninguna columna estructural sin linaje».
-- Las cuatro son TERRENOS con `piso` nulo y sin ninguna columna canonica
-- infractora, asi que no ocultan ningun defecto; el bloque 3 lo comprueba en vez
-- de afirmarlo.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE residuo_fecha AS
SELECT DISTINCT p.id_propiedad, p.codigo, p.fecha_registro, a.fecha_creacion
  FROM atributo_propiedad a
  JOIN propiedad p ON p.id_propiedad = a.id_propiedad
 WHERE a.fecha_creacion < p.fecha_registro;

SELECT codigo, fecha_creacion AS nacio_el_valor, fecha_registro AS nacio_la_propiedad
  FROM residuo_fecha ORDER BY codigo;

SELECT count(*) AS propiedades_con_fecha_imposible_antes FROM residuo_fecha;

UPDATE propiedad SET fecha_registro = frontera_de_linaje() - interval '2 days'
 WHERE id_propiedad IN (SELECT id_propiedad FROM residuo_fecha);

-- ---------------------------------------------------------------------
-- BLOQUE 3 - y se comprueba, con los MISMOS predicados del gate
-- ---------------------------------------------------------------------
\echo ''
\echo '== DESPUES =='
DO $comprobar$
DECLARE
    piso_sin_linaje bigint;
    fecha_imposible bigint;
    tapadas         bigint;
BEGIN
    SELECT count(*) INTO piso_sin_linaje
      FROM propiedad p
      CROSS JOIN LATERAL (VALUES ('METRAJE',           p.metraje::text),
                                 ('PISO',              p.piso),
                                 ('PARTIDA_REGISTRAL', p.partida_registral),
                                 ('OFICINA_REGISTRAL', p.oficina_registral)) AS e(campo, valor)
     WHERE p.fecha_registro > frontera_de_linaje()
       AND e.valor IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM rastro_valor_gobernado r
             JOIN catalogo_atributo c ON c.clave = r.clave
                                     AND c.campo_estructural = e.campo
                                     AND (c.organizacion_id IS NULL
                                       OR c.organizacion_id = r.organizacion_id)
            WHERE r.organizacion_id = p.organizacion_id
              AND r.sujeto = 'PROPIEDAD'
              AND r.id_agregado = p.id_propiedad);

    SELECT count(*) INTO fecha_imposible
      FROM atributo_propiedad a JOIN propiedad p ON p.id_propiedad = a.id_propiedad
     WHERE a.fecha_creacion < p.fecha_registro;

    -- Que envejecer las cuatro no haya sacado del universo a ninguna infractora.
    SELECT count(*) INTO tapadas
      FROM propiedad p
      CROSS JOIN LATERAL (VALUES ('METRAJE',           p.metraje::text),
                                 ('PISO',              p.piso),
                                 ('PARTIDA_REGISTRAL', p.partida_registral),
                                 ('OFICINA_REGISTRAL', p.oficina_registral)) AS e(campo, valor)
     WHERE p.id_propiedad IN (SELECT id_propiedad FROM residuo_fecha)
       AND e.valor IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM rastro_valor_gobernado r
             JOIN catalogo_atributo c ON c.clave = r.clave
                                     AND c.campo_estructural = e.campo
                                     AND (c.organizacion_id IS NULL
                                       OR c.organizacion_id = r.organizacion_id)
            WHERE r.organizacion_id = p.organizacion_id
              AND r.sujeto = 'PROPIEDAD'
              AND r.id_agregado = p.id_propiedad);

    RAISE NOTICE 'columnas estructurales sin linaje despues del cutover: %', piso_sin_linaje;
    RAISE NOTICE 'valores anteriores a su propia propiedad: %', fecha_imposible;
    RAISE NOTICE 'infractoras que el envejecimiento podria haber tapado: %', tapadas;

    IF piso_sin_linaje > 0 OR fecha_imposible > 0 OR tapadas > 0 THEN
        RAISE EXCEPTION 'SANEAMIENTO INCOMPLETO: % estructurales sin linaje, % valores imposibles, % tapadas',
            piso_sin_linaje, fecha_imposible, tapadas;
    END IF;
END $comprobar$;

DROP TABLE residuo_piso;
DROP TABLE residuo_fecha;

COMMIT;
