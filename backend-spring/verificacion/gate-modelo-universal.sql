-- =====================================================================
-- GATE DEL MODELO UNIVERSAL, CONTRA LA BASE REAL (D-E4-1, V46-V52)
--
--   docker exec -i controllocal-postgres-v2 \
--       psql -U controllocal -d controllocal_dev -v ON_ERROR_STOP=1 \
--            -f /tmp/gate-modelo-universal.sql
--
-- No comprueba que las tablas existan -- eso ya lo diria Flyway. Comprueba que
-- las INVARIANTES del contrato se cumplen de verdad, incluidas las que solo se
-- pueden probar intentando romperlas: mete datos malos y exige que la base los
-- rechace.
--
-- Todo ocurre dentro de una transaccion que se deshace al final, asi que se
-- puede correr contra la base de desarrollo sin ensuciarla.
-- =====================================================================

\set ON_ERROR_STOP on
\timing off

-- Ancho de la columna del nombre en el informe. Es UNA sola cifra a proposito:
-- la usan el informe y la comprobacion que vigila que ningun nombre lo pase,
-- asi que no pueden separarse (auditoria del 2026-08-25, N10).
\set ANCHO_PRUEBA 78

BEGIN;

-- `nota` lleva la CIFRA que una comprobacion mide de paso y su nombre no puede
-- cargar sin estorbar: por ejemplo el tamano real del universo que la
-- comprobacion acaba de mirar. Existe porque meterla en el nombre no funciono
-- --el informe la cortaba y la cifra no se imprimia nunca-- y un artefacto que
-- afirma informar sin informar es peor que uno que calla.
CREATE TEMP TABLE resultado (n serial, prueba text, veredicto text, nota text);

CREATE OR REPLACE FUNCTION pg_temp.comprobar(p_prueba text, p_condicion boolean, p_detalle text DEFAULT NULL,
                                             p_nota text DEFAULT NULL)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO resultado (prueba, veredicto, nota)
    VALUES (p_prueba, CASE WHEN p_condicion THEN 'OK' ELSE 'FALLO' || COALESCE(' - ' || p_detalle, '') END, p_nota);
END $$;

-- Ejecuta algo que DEBE fallar. Si no falla, la invariante no existe.
CREATE OR REPLACE FUNCTION pg_temp.debe_rechazar(p_prueba text, p_sql text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO resultado (prueba, veredicto) VALUES (p_prueba, pg_temp.rechaza(p_sql));
END $$;

-- La misma comprobacion devolviendo el veredicto en vez de anotarlo. Hace falta
-- para lo que se prueba DENTRO de un savepoint: al deshacerlo se irian tambien
-- las filas de `resultado`, y las tres pruebas que se pierden asi son
-- justamente las tres que deciden el modelo. Se capturan con \gset -- que vive
-- en el cliente y sobrevive al rollback -- y se anotan despues.
-- Un `UPDATE` que no encuentra ninguna fila no dispara ningun trigger y
-- termina sin error: leido como "lo acepto" acusa a una invariante que nadie
-- llego a probar, y leido al reves seria peor -- daria por buena una guarda
-- inexistente. Se distingue (V76): cero filas afectadas es un defecto DE LA
-- PRUEBA, y lo dice con esas palabras.
CREATE OR REPLACE FUNCTION pg_temp.rechaza(p_sql text)
RETURNS text LANGUAGE plpgsql AS $$
DECLARE
    tocadas bigint;
BEGIN
    EXECUTE p_sql;
    GET DIAGNOSTICS tocadas = ROW_COUNT;
    IF tocadas = 0 THEN
        RETURN 'FALLO - la prueba no toco ninguna fila: no probo nada';
    END IF;
    RETURN 'FALLO - lo acepto';
EXCEPTION WHEN others THEN
    RETURN 'OK';
END $$;

CREATE OR REPLACE FUNCTION pg_temp.veredicto(p_condicion boolean)
RETURNS text LANGUAGE sql AS $$ SELECT CASE WHEN $1 THEN 'OK' ELSE 'FALLO' END $$;

-- Silencio hasta el informe: cada comprobacion devolveria una fila vacia.
\o /dev/null

-- Datos de apoyo: una organizacion, un propietario y una propiedad reales.
--
-- La propiedad de apoyo se elige CON TITULAR a proposito (V76). Desde que
-- `id_rol_propietario` admite NULL -- una propiedad puede conocerse sin saber
-- de quien es --, `min(id_propiedad)` podia devolver una sin titular y las
-- pruebas de titularidad que vienen despues fallarian por el dato elegido y no
-- por la invariante. `min(id_rol_propietario)` ya ignora los NULL de suyo.
CREATE TEMP TABLE ctx AS
SELECT (SELECT min(organizacion_id) FROM propiedad)                              AS org,
       (SELECT min(id_propiedad)    FROM propiedad
         WHERE id_rol_propietario IS NOT NULL)                                   AS prop,
       (SELECT min(id_rol_propietario) FROM propiedad)                           AS titular,
       (SELECT min(pr.id_persona_rol) FROM persona_rol pr
         WHERE pr.tipo_rol = 'PROPIETARIO'
           AND pr.id_persona_rol <> (SELECT min(id_rol_propietario) FROM propiedad)) AS otro_titular;

-- =====================================================================
-- M0 - PostGIS
-- =====================================================================
SELECT pg_temp.comprobar('M0 la extension postgis esta instalada',
    EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis'));

SELECT pg_temp.comprobar('M0 propiedad.ubicacion es geography',
    EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name = 'propiedad' AND column_name = 'ubicacion' AND udt_name = 'geography'));

SELECT pg_temp.comprobar('M0 hay indice GiST sobre la ubicacion',
    EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'propiedad' AND indexname = 'ix_propiedad_ubicacion'));

SELECT pg_temp.comprobar('M0 hay indice GiST por organizacion y ubicacion',
    EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'propiedad' AND indexname = 'ix_propiedad_org_ubicacion'));

-- El trigger, en los dos sentidos.
UPDATE propiedad SET geo_lat = -12.1211000, geo_long = -77.0300000
 WHERE id_propiedad = (SELECT prop FROM ctx);

SELECT pg_temp.comprobar('M0 lat/long escritas construyen el punto',
    (SELECT ubicacion IS NOT NULL FROM propiedad WHERE id_propiedad = (SELECT prop FROM ctx)));

SELECT pg_temp.comprobar('M0 y el punto cae donde dicen las coordenadas',
    (SELECT round(ST_Y(ubicacion::geometry)::numeric, 4) = -12.1211
        AND round(ST_X(ubicacion::geometry)::numeric, 4) = -77.0300
       FROM propiedad WHERE id_propiedad = (SELECT prop FROM ctx)));

UPDATE propiedad
   SET ubicacion = ST_SetSRID(ST_MakePoint(-77.0282, -12.0995), 4326)::geography
 WHERE id_propiedad = (SELECT prop FROM ctx);

SELECT pg_temp.comprobar('M0 escribir el punto rellena lat/long para el cable',
    (SELECT round(geo_lat, 4) = -12.0995 AND round(geo_long, 4) = -77.0282
       FROM propiedad WHERE id_propiedad = (SELECT prop FROM ctx)));

-- La consulta que justifica toda la migracion: buscar por cercania en METROS.
SELECT pg_temp.comprobar('M0 se puede buscar por cercania en metros',
    (SELECT count(*) >= 1 FROM propiedad
      WHERE ubicacion IS NOT NULL
        AND ST_DWithin(ubicacion, ST_SetSRID(ST_MakePoint(-77.0282, -12.0995), 4326)::geography, 500)));

-- =====================================================================
-- M1 - Titularidad
-- =====================================================================
-- DEROGADA EN V76, y no por comodidad: era falsa como invariante del REGISTRO.
-- Se puede conocer un inmueble sin saber de quien es -- BROX conoce inmuebles
-- que no gestiona --, y obligar a declararlo obligaria a inventarlo. De hecho ya
-- no describia la base: 67 de 1465 propiedades no tenian titularidad vigente y
-- esta comprobacion llevaba tiempo en fallo sin que nadie la mirara.
--
-- La exigencia no desaparece: se muda al ENCARGO, que es donde sigue siendo
-- cierta. Aqui se comprueba en su forma nueva.
SELECT pg_temp.comprobar('M1 ningun encargo vivo cuelga de una propiedad sin titular',
    NOT EXISTS (SELECT 1 FROM captacion c
                 WHERE c.estado IN ('P', 'O', 'A')
                   AND NOT EXISTS (SELECT 1 FROM titularidad_propiedad t
                                    WHERE t.id_propiedad = c.id_propiedad
                                      AND t.vigente_hasta IS NULL)));

SELECT pg_temp.comprobar('M1 las cuotas vigentes suman 100 en todas',
    NOT EXISTS (SELECT 1 FROM titularidad_propiedad
                 WHERE vigente_hasta IS NULL
                 GROUP BY id_propiedad HAVING sum(cuota) <> 100));

-- «Exactamente uno» se comprueba sobre las propiedades QUE TIENEN titularidad:
-- el GROUP BY ya las acota, asi que una propiedad sin ninguna no entra y no
-- falsea el resultado.
SELECT pg_temp.comprobar('M1 hay exactamente un representante por propiedad con titular',
    NOT EXISTS (SELECT 1 FROM titularidad_propiedad
                 WHERE vigente_hasta IS NULL AND es_representante
                 GROUP BY id_propiedad HAVING count(*) <> 1));

-- Copropiedad: tres titulares que suman 100 tienen que entrar.
SAVEPOINT copro;
UPDATE titularidad_propiedad SET cuota = 50
 WHERE id_propiedad = (SELECT prop FROM ctx) AND vigente_hasta IS NULL;
INSERT INTO titularidad_propiedad (organizacion_id, id_propiedad, id_rol_propietario, cuota, es_representante, vigente_desde)
SELECT org, prop, otro_titular, 50, false, CURRENT_DATE FROM ctx WHERE otro_titular IS NOT NULL;

SELECT pg_temp.veredicto(
    (SELECT COALESCE(sum(cuota), 100) = 100 AND count(*) >= 2 FROM titularidad_propiedad
      WHERE id_propiedad = (SELECT prop FROM ctx) AND vigente_hasta IS NULL)) AS v_copro \gset
ROLLBACK TO SAVEPOINT copro;
INSERT INTO resultado (prueba, veredicto) VALUES ('M1 admite copropiedad de dos titulares que suman 100', :'v_copro');

-- Y rechaza la que no suma.
SELECT pg_temp.debe_rechazar('M1 rechaza cuotas que no suman 100', format($f$
    DO $d$ BEGIN
        INSERT INTO titularidad_propiedad
            (organizacion_id, id_propiedad, id_rol_propietario, cuota, es_representante, vigente_desde)
        VALUES (%s, %s, %s, 30, false, CURRENT_DATE);
    END $d$;
    SET CONSTRAINTS ALL IMMEDIATE;
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx), (SELECT COALESCE(otro_titular, titular) FROM ctx)));

SELECT pg_temp.debe_rechazar('M1 rechaza dos representantes vigentes', format($f$
    INSERT INTO titularidad_propiedad
        (organizacion_id, id_propiedad, id_rol_propietario, cuota, es_representante, vigente_desde)
    VALUES (%s, %s, %s, 100, true, CURRENT_DATE)
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx), (SELECT COALESCE(otro_titular, titular) FROM ctx)));

SELECT pg_temp.debe_rechazar('M1 rechaza una cuota mayor que 100', format($f$
    INSERT INTO titularidad_propiedad
        (organizacion_id, id_propiedad, id_rol_propietario, cuota, es_representante, vigente_desde)
    VALUES (%s, %s, %s, 140, false, CURRENT_DATE)
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx), (SELECT COALESCE(otro_titular, titular) FROM ctx)));

-- =====================================================================
-- M2 - Atributos gobernados
-- =====================================================================
-- EL CENSO QUE SE ROMPIA AL AVANZAR (corregido en el Corte 3.a).
--
-- Aqui habia un censo exacto: `count(*) = 25 FROM catalogo_atributo WHERE
-- del_sistema`, con este argumento escrito al lado -- "aqui la cifra exacta SI
-- vale: el catalogo del sistema es una constante del producto, no cartera que
-- crece con el uso".
--
-- DESDE CUANDO ESTABA ROJO. El censo se actualizo por ultima vez en `a07a594`
-- (V76), de 19 a 25. Despues: V77 sembro veinte condiciones del ENCARGO, V79
-- anadio seis y V80 anade treinta de vivienda. El 2026-08-24, antes de tocar
-- nada, la base decia 51 -- 25 de PROPIEDAD y 26 de ENCARGO -- contra un `= 25`
-- que llevaba rojo desde V77. Sobrevivio a TRES cortes cerrados y auditados
-- porque nadie ejecutaba este fichero; por eso el Corte 3.a lo mete ademas en
-- `Verificar-Cierre.ps1`. Que PROPIEDAD valga exactamente 25 es la coincidencia
-- que hacia que el numero siguiera pareciendo correcto al leerlo.
--
-- POR QUE NO SE ARREGLA ESCRIBIENDO EL CENSO EXACTO -- que el dia que se
-- escribio esto eran 81 claves y hoy son otras. El argumento original era cierto
-- cuando el catalogo estaba congelado y dejo de serlo: el bloque 3e entero es un
-- programa cuyo proposito explicito es hacerlo crecer, corte a corte
-- (V74 -> V77 -> V79 -> V80 -> cortes 4-7). Con eso el numero mide el avance del
-- roadmap y no una invariante: se pone rojo CADA VEZ que el producto avanza
-- segun lo planeado. Es el mismo modo de fallo que este fichero ya diagnostico
-- mas abajo al convertir dos cifras hermanas en suelos -- "un gate que se rompe
-- al usar el producto deja de leerse" --, solo que aqui se rompio al CONSTRUIR
-- el producto.
--
-- POR QUE ESTO NO RELAJA EL GATE. El censo se sustituye por DOS comprobaciones y
-- el conjunto queda mas fuerte:
--   * un SUELO de claves ACTIVAS, que caza lo unico que un numero puede cazar y
--     que si es una invariante del producto: que alguien RETIRE claves del
--     sistema. El suelo es 51 -- lo medido el 2026-08-24, antes de V80 -- y no
--     se sube con cada corte, porque su trabajo es detectar retirada, no contar
--     avance.
--   * la invariante que si importa y que hasta hoy NO existia: ninguna clave del
--     sistema activa se queda sin aplicabilidad. Esa se rompe cuando alguien
--     siembra mal -- que es el fallo real que este bloque debia atrapar -- y no
--     se rompe cuando el producto avanza.
--
-- EL `FILTER (WHERE activo)` NO ES DECORACION, Y SIN EL ESTA COMPROBACION NO
-- CAZA NADA (enmienda del Corte 3.a). Borrar una clave del sistema ya lo impide
-- `proteger_catalogo_del_sistema()` con un `restrict_violation`, asi que por esa
-- via el suelo no aporta. La retirada que el sistema SI permite es la que el
-- propio mensaje de ese trigger recomienda -- "para retirarlo de las preguntas,
-- ponlo activo = false" -- y esa NO baja el `count(*)`: se podrian desactivar
-- TODAS y un suelo sin filtro seguiria en verde.
--
-- LIMITE HONESTO, dicho y no escondido: el suelo tolera tantas retiradas como
-- claves activas haya POR ENCIMA de 51, y ese margen CRECE con cada corte que
-- siembra. Es decir: cuanto mas rico es el catalogo, mas flojo es este suelo.
-- Se acepta a proposito, porque subirlo corte a corte reintroduce exactamente el
-- censo que esta enmienda vino a quitar -- un gate que se pone rojo al USAR el
-- producto deja de leerse. Quien vigila la salud de cada clave es la
-- comprobacion siguiente, que no depende del tamaño del catalogo.
--
-- EL MARGEN NO SE ESCRIBE AQUI COMO CIFRA, Y ESA ES LA ENMIENDA DE 5B. Este
-- comentario decia "con 81 claves sembradas, un suelo de 51 tolera 30 retiradas",
-- y las dos cifras caducaron sin que nada avisara: cuando 5B las midio ya eran
-- 140 activas y 89 de margen. Un comentario con un numero envejece a mentira en
-- silencio, asi que el numero pasa a la columna `nota`, donde lo MIDE el propio
-- gate en cada corrida y no hay que mantenerlo a mano. Misma regla que
-- "5A CONTROL el predicado del legado caza una traduccion sin linaje", que
-- tambien declara en `nota` el universo que acaba de mirar.
--
-- (Esta frase citaba DOS comprobaciones POR POSICION, y tenia dos defectos en
-- cinco caracteres: la posicion se desplaza sola en cuanto alguien intercala una
-- comprobacion --que es lo que hizo 5B-- y ademas la segunda que nombraba ni
-- siquiera lleva `nota`, asi que la cita era falsa. Desde 5B se cita por NOMBRE,
-- y desde la septima ronda NINGUNA cita lleva numero: tampoco las historicas,
-- porque un numero citado dentro de una correccion se lee como una cita.)
SELECT pg_temp.comprobar('M2 no se retiraron claves del catalogo del sistema',
    (SELECT count(*) FILTER (WHERE activo) >= 51 FROM catalogo_atributo WHERE del_sistema),
    'alguien retiro claves del sistema por debajo del suelo',
    (SELECT format('activas: %s, suelo: 51, margen: %s retiradas',
                   count(*) FILTER (WHERE activo),
                   count(*) FILTER (WHERE activo) - 51)
       FROM catalogo_atributo WHERE del_sistema));

-- Una clave sembrada sin decir a que aplica es invisible en todos los guiones y
-- nadie lo nota hasta echarla en falta: el alta no la pinta, el editor no la
-- ofrece y el dato simplemente no se captura. Se mira en la tabla que le toca
-- por sujeto -- `catalogo_atributo_tipo` para PROPIEDAD,
-- `catalogo_atributo_operacion` para ENCARGO --, que es la regla que la guarda
-- 2.5 de V78 vigila en la otra direccion.
--
-- Llevaba delante un `AND NOT c.aplica_todos` que perdonaba a la clave que
-- pusiera el campo. V86 le quito la autoridad al campo: una clave sin filas no
-- aplica a nada por mucho que lo diga, asi que la excepcion pasaba a perdonar
-- justo el caso roto.
SELECT pg_temp.comprobar('M2 ninguna clave del sistema se quedo sin aplicabilidad',
    NOT EXISTS (
        SELECT 1 FROM catalogo_atributo c
         WHERE c.del_sistema AND c.activo
           AND ((c.sujeto = 'PROPIEDAD'
                 AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                                  WHERE t.id_catalogo_atributo = c.id_catalogo_atributo))
             OR (c.sujeto = 'ENCARGO'
                 AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                                  WHERE o.id_catalogo_atributo = c.id_catalogo_atributo)))));

-- V86 - NINGUNA clave puede depender EXCLUSIVAMENTE de `aplica_todos`.
--
-- Es la comprobacion en estado de reposo del invariante que
-- `tg_aplica_todos_respaldado` vigila al escribir: el campo resume las filas y
-- no puede contradecirlas. Mide TODAS las claves, del sistema y de tenant, y
-- declara en `nota` el universo que acaba de mirar -- un "0 encontradas" sin
-- decir sobre cuantas no es una medicion.
SELECT pg_temp.comprobar('M2 ninguna clave depende exclusivamente de aplica_todos',
    NOT EXISTS (
        SELECT 1 FROM catalogo_atributo c
         WHERE c.aplica_todos
           AND (c.sujeto <> 'PROPIEDAD'
             OR EXISTS (SELECT 1 FROM tipos_de_propiedad() AS t(tipo)
                         WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo x
                                            WHERE x.id_catalogo_atributo = c.id_catalogo_atributo
                                              AND x.tipo_propiedad = t.tipo)))),
    'hay claves cuyo `aplica_todos` no esta respaldado por sus siete filas: el campo estaria decidiendo',
    (SELECT format('%s claves con `aplica_todos` sobre %s del catalogo; sin respaldo: %s',
                   count(*) FILTER (WHERE aplica_todos), count(*),
                   count(*) FILTER (WHERE aplica_todos AND (sujeto <> 'PROPIEDAD'
                       OR EXISTS (SELECT 1 FROM tipos_de_propiedad() AS t(tipo)
                                   WHERE NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo x
                                                      WHERE x.id_catalogo_atributo = c.id_catalogo_atributo
                                                        AND x.tipo_propiedad = t.tipo)))))
       FROM catalogo_atributo c));

-- Y ningun cuerpo PL/pgSQL vuelve a consultarlo como autoridad. Es la leccion
-- de V40/V44: una conversion que no llega al cuerpo de una funcion deja el
-- esquema correcto, el build verde y la aplicacion rota, porque ni javac ni
-- Hibernate leen un `prosrc`. Se excluye por nombre la unica funcion que SI
-- tiene que nombrarlo: la guarda que lo vigila.
SELECT pg_temp.comprobar('M2 ninguna funcion consulta aplica_todos como autoridad',
    NOT EXISTS (
        SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
         WHERE n.nspname = 'public' AND p.prosrc LIKE '%aplica_todos%'
           AND p.proname <> 'exigir_que_las_filas_respalden_aplica_todos'),
    'un cuerpo PL/pgSQL sigue cortocircuitando por `aplica_todos`',
    (SELECT format('funciones que lo nombran: %s',
                   coalesce(string_agg(p.proname, ', ' ORDER BY p.proname), 'ninguna'))
       FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
      WHERE n.nspname = 'public' AND p.prosrc LIKE '%aplica_todos%'));

SELECT pg_temp.comprobar('M2 los atributos del sistema no tienen organizacion',
    NOT EXISTS (SELECT 1 FROM catalogo_atributo WHERE del_sistema AND organizacion_id IS NOT NULL));

SELECT pg_temp.comprobar('M2 dormitorios solo aplica a departamento y casa',
    (SELECT array_agg(t.tipo_propiedad ORDER BY t.tipo_propiedad) = ARRAY['C','D']::varchar[]
       FROM catalogo_atributo c JOIN catalogo_atributo_tipo t USING (id_catalogo_atributo)
      WHERE c.clave = 'dormitorios'));

SELECT pg_temp.comprobar('M2 metraje_total es obligatorio en los siete tipos',
    (SELECT count(*) = 7 FROM catalogo_atributo c JOIN catalogo_atributo_tipo t USING (id_catalogo_atributo)
      WHERE c.clave = 'metraje_total' AND t.requerido));

SELECT pg_temp.comprobar('M2 se migraron los valores que ya existian',
    (SELECT count(*) > 0 FROM atributo_propiedad));

-- Reescrita en V76, porque comprobaba justo lo contrario de lo que decidio
-- D-E4-3. Pedia que cada propiedad tuviera una COPIA del metraje en
-- `atributo_propiedad`, y V61 borro todas esas copias a proposito: la
-- autoridad del metraje es la columna canonica. El gate llevaba en rojo desde
-- entonces contra la decision que lo superaba.
--
-- Lo que hoy significa "ninguna propiedad perdio su metraje" son dos cosas:
-- que el dato esta en su unico sitio, y que la copia no ha vuelto.
SELECT pg_temp.comprobar('M2 ninguna propiedad perdio su metraje',
    NOT EXISTS (SELECT 1 FROM propiedad p WHERE p.metraje IS NULL));

SELECT pg_temp.comprobar('M2 el metraje no volvio a duplicarse como atributo',
    (SELECT count(*) = 0 FROM atributo_propiedad WHERE clave = 'metraje_total'));

SELECT pg_temp.debe_rechazar('M2 rechaza una clave que no esta en el catalogo', format($f$
    INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
    VALUES (%s, %s, 'clave_inventada', 'x')
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

SELECT pg_temp.debe_rechazar('M2 rechaza un atributo que no aplica a ese tipo', format($f$
    INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero)
    VALUES (%s, %s, 'dormitorios', 3)
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

SELECT pg_temp.debe_rechazar('M2 rechaza el valor en la columna equivocada', format($f$
    INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
    VALUES (%s, %s, 'carga_electrica_kw', 'mucha')
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

SELECT pg_temp.debe_rechazar('M2 rechaza dos valores a la vez', format($f$
    INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto, valor_numero)
    VALUES (%s, %s, 'rubro_permitido', 'x', 1)
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

-- =====================================================================
-- M3 - Historico economico por encargo
-- =====================================================================
SELECT pg_temp.comprobar('M3 todo hito declara su operacion',
    NOT EXISTS (SELECT 1 FROM precio_propiedad WHERE operacion IS NULL));

SELECT pg_temp.comprobar('M3 la operacion del hito es la de su encargo',
    NOT EXISTS (SELECT 1 FROM precio_propiedad pp JOIN captacion c USING (id_captacion)
                 WHERE pp.operacion <> c.motivo_operacion));

SELECT pg_temp.debe_rechazar('M3 rechaza un hito con operacion distinta a la de su encargo', format($f$
    INSERT INTO precio_propiedad (organizacion_id, id_propiedad, id_captacion, hito, moneda, monto, fecha, operacion)
    SELECT %s, c.id_propiedad, c.id_captacion, 'U', 'USD', 1000, CURRENT_DATE,
           CASE c.motivo_operacion WHEN 'A' THEN 'V' ELSE 'A' END
      FROM captacion c LIMIT 1
$f$, (SELECT org FROM ctx)));

-- =====================================================================
-- M4 - La operacion vive en el encargo
-- =====================================================================
SELECT pg_temp.comprobar('M4 motivo_operacion ya no tiene DEFAULT',
    (SELECT column_default IS NULL FROM information_schema.columns
      WHERE table_name = 'captacion' AND column_name = 'motivo_operacion'));

SELECT pg_temp.comprobar('M4 existe el indice de un encargo vivo por operacion',
    EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_captacion_viva_por_operacion'));

SELECT pg_temp.comprobar('M4 ninguna propiedad tiene dos encargos vivos de la misma operacion',
    NOT EXISTS (SELECT 1 FROM captacion WHERE estado IN ('P','O','A')
                 GROUP BY id_propiedad, motivo_operacion HAVING count(*) > 1));

-- El caso que decide el modelo: venta y alquiler a la vez sobre la misma propiedad.
SAVEPOINT dos_encargos;
DO $$
DECLARE
    c   captacion%ROWTYPE;
    ce  bigint;
BEGIN
    -- Una captacion viva cuya propiedad NO tenga ya la otra operacion abierta.
    -- Sin esa condicion el gate elegia cualquiera y, en cuanto la cartera tuvo
    -- una propiedad en venta y alquiler a la vez -- justo lo que este bloque
    -- existe para demostrar que se puede --, el INSERT chocaba contra
    -- `uq_captacion_viva_por_operacion` y el gate moria antes del informe.
    SELECT * INTO c FROM captacion cap
     WHERE cap.estado IN ('P','O','A')
       AND NOT EXISTS (SELECT 1 FROM captacion otra
                        WHERE otra.id_propiedad = cap.id_propiedad
                          AND otra.estado IN ('P','O','A')
                          AND otra.motivo_operacion <> cap.motivo_operacion)
     ORDER BY cap.id_captacion
     LIMIT 1;

    INSERT INTO condicion_economica_captacion
        (organizacion_id, tipo_operacion, importe_referencia, moneda_referencia,
         tipo_comision, base_calculo, valor_comision, tratamiento_igv)
    VALUES (c.organizacion_id,
            CASE c.motivo_operacion WHEN 'A' THEN 'V' ELSE 'A' END,
            180000, 'USD', 'P', 'R', 3, 'I')
    RETURNING id_condicion_economica INTO ce;

    INSERT INTO captacion
        (organizacion_id, codigo_captacion, fecha_captacion, estado, id_propiedad,
         id_rol_agente, motivo_operacion, id_condicion_economica,
         fecha_inicio_encargo, fecha_fin_encargo)
    VALUES (c.organizacion_id, 'GATE-DUAL-1', CURRENT_DATE, 'P', c.id_propiedad,
            c.id_rol_agente,
            CASE c.motivo_operacion WHEN 'A' THEN 'V' ELSE 'A' END, ce,
            CURRENT_DATE, CURRENT_DATE + 180);
END $$;

SELECT pg_temp.veredicto(
    (SELECT count(DISTINCT motivo_operacion) = 2 FROM captacion
      WHERE estado IN ('P','O','A')
        AND id_propiedad = (SELECT id_propiedad FROM captacion WHERE codigo_captacion = 'GATE-DUAL-1'))) AS v_dual \gset

-- Y rechaza el duplicado de la MISMA operacion.
SELECT pg_temp.rechaza($f$
    INSERT INTO captacion
        (organizacion_id, codigo_captacion, fecha_captacion, estado, id_propiedad,
         id_rol_agente, motivo_operacion, fecha_inicio_encargo, fecha_fin_encargo)
    SELECT organizacion_id, 'GATE-DUAL-2', CURRENT_DATE, 'P', id_propiedad,
           id_rol_agente, motivo_operacion, CURRENT_DATE, CURRENT_DATE + 180
      FROM captacion WHERE codigo_captacion = 'GATE-DUAL-1'
$f$) AS v_dup \gset

ROLLBACK TO SAVEPOINT dos_encargos;
INSERT INTO resultado (prueba, veredicto) VALUES
    ('M4 admite venta y alquiler vivos sobre la misma propiedad', :'v_dual'),
    ('M4 rechaza dos encargos vivos de la misma operacion',       :'v_dup');

SELECT pg_temp.debe_rechazar('M4 rechaza una condicion economica de otra operacion', $f$
    UPDATE captacion SET motivo_operacion = CASE motivo_operacion WHEN 'A' THEN 'V' ELSE 'A' END
     WHERE id_condicion_economica IS NOT NULL
$f$);

-- =====================================================================
-- M5 - Expediente con tipo
-- =====================================================================
SELECT pg_temp.comprobar('M5 el expediente declara su tipo',
    EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name = 'solicitud_alquiler' AND column_name = 'tipo'));

SELECT pg_temp.comprobar('M5 el tipo no tiene DEFAULT: se deriva del encargo',
    (SELECT column_default IS NULL FROM information_schema.columns
      WHERE table_name = 'solicitud_alquiler' AND column_name = 'tipo'));

SELECT pg_temp.comprobar('M5 todo expediente existente es de alquiler',
    NOT EXISTS (SELECT 1 FROM solicitud_alquiler WHERE tipo <> 'A'));

SELECT pg_temp.comprobar('M5 el catalogo ya admite documentos de compraventa',
    (SELECT count(*) >= 8 FROM tipo_documento_requerido WHERE tipo_operacion = 'V'));

SELECT pg_temp.comprobar('M5 el plano perimetrico solo se pide para terreno',
    (SELECT tipo_propiedad = 'T' FROM tipo_documento_requerido
      WHERE tipo_documento = 'PLANO_PERIMETRICO'));

SELECT pg_temp.debe_rechazar('M5 rechaza condiciones de compraventa en expediente de alquiler', format($f$
    INSERT INTO condicion_compraventa (id_solicitud, organizacion_id, forma_pago_saldo)
    SELECT id_solicitud, %s, 'C' FROM solicitud_alquiler WHERE tipo = 'A' LIMIT 1
$f$, (SELECT org FROM ctx)));

SELECT pg_temp.debe_rechazar('M5 rechaza garantia en un expediente de compraventa', $f$
    UPDATE solicitud_alquiler SET tipo = 'V' WHERE meses_garantia IS NOT NULL
$f$);

-- =====================================================================
-- Outbox
-- =====================================================================
SELECT pg_temp.comprobar('OUT existe el outbox de eventos',
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'evento_dominio'));

-- Corregida en V76. Preguntaba por `ck_evento_origen`, una restriccion que no
-- existe con ese nombre: la subconsulta devolvia NULL y el gate lo contaba
-- como FALLO. La que existe es `ck_evento_canal`, y lo que de verdad hay que
-- comprobar es que el outbox pueda decir POR DONDE entro el hecho -- un evento
-- que llega por WhatsApp no es lo mismo que uno tecleado en la SPA.
SELECT pg_temp.comprobar('OUT el outbox distingue el canal por el que entro el hecho',
    (SELECT pg_get_constraintdef(oid) LIKE '%WHATSAPP%'
       FROM pg_constraint WHERE conname = 'ck_evento_canal'));

SELECT pg_temp.comprobar('OUT el outbox sabe que agente y que modelo lo produjo',
    (SELECT count(*) = 3 FROM information_schema.columns
      WHERE table_name = 'evento_dominio'
        AND column_name IN ('agente', 'agente_modelo', 'agente_modelo_version')));

SELECT pg_temp.comprobar('OUT hay indice parcial de lo no proyectado',
    EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ix_evento_dominio_pendiente'));

-- =====================================================================
-- Gobierno del catalogo (V55) y encargo por operacion (V58)
--
-- Estas siete se anaden en la tanda de la Propiedad Universal. Las dos
-- ultimas existen por un fallo real: V50 creyo admitir venta y alquiler
-- simultaneos y dejo en pie `uq_captacion_activa_por_local`, que no
-- distingue operacion. Funcionaba con los dos encargos PENDIENTES -- que es
-- como se verifico -- y fallaba al aprobar el segundo.
-- =====================================================================
SELECT pg_temp.comprobar('GOB una organizacion no puede sombrear una clave del sistema',
    EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'tg_catalogo_no_sombrea'));

SELECT pg_temp.comprobar('GOB un atributo del sistema es inmutable en clave y tipo',
    EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'tg_catalogo_sistema_inmutable'));

SELECT pg_temp.comprobar('GOB ninguna fila de tenant sombrea hoy una clave comun',
    (SELECT count(*) = 0
       FROM catalogo_atributo tenant
       JOIN catalogo_atributo sistema
         ON sistema.organizacion_id IS NULL AND sistema.clave = tenant.clave
      WHERE tenant.organizacion_id IS NOT NULL));

SELECT pg_temp.comprobar('TIPO propiedad admite los SIETE tipos, incluido el almacen',
    (SELECT pg_get_constraintdef(oid) LIKE '%''A''%'
       FROM pg_constraint WHERE conname = 'ck_propiedad_tipo'));

SELECT pg_temp.comprobar('OP el indice viejo de una activa por local ya NO bloquea',
    NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_captacion_activa_por_local'));

SELECT pg_temp.comprobar('OP la invariante vigente es un encargo vivo por operacion',
    EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_captacion_viva_por_operacion'));

SELECT pg_temp.comprobar('IDEM un comando no se puede repetir dentro de una organizacion',
    EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'uq_comando_idempotente'));

-- =====================================================================
-- V76 - La propiedad como activo de dato
-- =====================================================================
-- Una Propiedad representa un inmueble CONOCIDO por BROX, no necesariamente
-- una oferta gestionada por BROX. Lo que sigue comprueba las dos mitades de
-- esa frase: que se puede conocer sin relacion comercial, y que ningun hecho
-- comercial nace sin la relacion que lo autoriza.

SELECT pg_temp.comprobar('V76 una propiedad puede no tener titular conocido',
    (SELECT is_nullable = 'YES' FROM information_schema.columns
      WHERE table_name = 'propiedad' AND column_name = 'id_rol_propietario'));

SELECT pg_temp.comprobar('V76 pero no puede tener media titularidad',
    EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_propiedad_titular_completo'));

-- El par se mantiene coherente por trigger, no a mano: quitar el titular
-- limpia tambien su tipo de rol, y ponerlo lo rellena. No se prueba con un
-- `debe_rechazar` porque quitar los dos a la vez es LEGITIMO -- es justamente
-- lo que hace una propiedad sin dueno conocido.
SELECT pg_temp.comprobar('V76 el tipo de rol lo mantiene coherente un trigger',
    EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'tg_propiedad_tipo_rol'));

SELECT pg_temp.comprobar('V76 ninguna propiedad tiene media titularidad',
    NOT EXISTS (SELECT 1 FROM propiedad
                 WHERE (id_rol_propietario IS NULL) <> (tipo_rol_propietario IS NULL)));

SELECT pg_temp.comprobar('V76 toda propiedad declara como llego a conocerse',
    NOT EXISTS (SELECT 1 FROM propiedad WHERE origen_incorporacion IS NULL));

SELECT pg_temp.comprobar('V76 la procedencia solo admite el vocabulario cerrado',
    (SELECT pg_get_constraintdef(oid) LIKE '%OBSERVACION%'
       FROM pg_constraint WHERE conname = 'ck_propiedad_origen_incorporacion'));

SELECT pg_temp.comprobar('V76 existe la serie de lo observado en el mercado',
    EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'observacion_mercado'));

-- Lo que se vio no se corrige ni se borra: si el dato estaba mal, se anota otro.
-- Se siembra una observacion dentro del savepoint: sin fila que tocar, el
-- UPDATE no dispara el trigger y la prueba no probaria nada.
SAVEPOINT observada;

INSERT INTO observacion_mercado
    (organizacion_id, id_propiedad, fecha_observada, operacion, importe, moneda,
     fuente, detalle, id_rol_actor)
SELECT org, prop, CURRENT_DATE, 'V', 190000, 'USD', 'GATE', 'sembrada por el gate', titular
  FROM ctx;

SELECT pg_temp.rechaza($f$
    UPDATE observacion_mercado SET importe = 1 WHERE fuente = 'GATE'
$f$) AS v_obs_upd \gset

SELECT pg_temp.rechaza($f$
    DELETE FROM observacion_mercado WHERE fuente = 'GATE'
$f$) AS v_obs_del \gset

ROLLBACK TO SAVEPOINT observada;
INSERT INTO resultado (prueba, veredicto) VALUES
    ('V76 una observacion de mercado no se puede editar', :'v_obs_upd'),
    ('V76 una observacion de mercado no se puede borrar', :'v_obs_del');

-- La frontera, dicha desde la base: un precio es un hecho comercial y exige el
-- encargo que lo autorizo. Sin el, lo que hay es una observacion.
SELECT pg_temp.debe_rechazar('V76 un hito de precio exige el encargo que lo autoriza', format($f$
    INSERT INTO precio_propiedad
        (organizacion_id, id_propiedad, id_captacion, hito, importe, moneda, vigente_desde)
    VALUES (%s, %s, NULL, 'I', 1000, 'PEN', CURRENT_DATE)
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

-- =====================================================================
-- 4.P - La procedencia del DATO, no la del acto (V83, D-4P-1)
--
-- Lo que se comprueba aqui es lo que ningun test de Java puede ver: que la
-- forma del linaje la sostiene la BASE, y que la cartera real esta del lado
-- correcto de la frontera de garantia.
-- =====================================================================
SELECT pg_temp.comprobar('4P existen las dos tablas del linaje',
    (SELECT count(*) = 2 FROM information_schema.tables
      WHERE table_name IN ('rastro_valor_gobernado', 'rastro_valor_opcion')));

-- LA DECISION CENTRAL DEL MODELO, comprobada al reves: si el linaje colgara del
-- id de la fila vigente, borrar un atributo se llevaria por delante su historia
-- y una clave ESTRUCTURAL --que no crea fila-- no podria tener ninguna. Que NO
-- haya FK a las tablas de valor es lo que hace posible las cinco superficies.
SELECT pg_temp.comprobar('4P el linaje NO cuelga del id de la fila vigente',
    NOT EXISTS (SELECT 1 FROM pg_constraint c
                 WHERE c.conrelid = 'rastro_valor_gobernado'::regclass
                   AND c.contype = 'f'
                   AND c.confrelid IN ('atributo_propiedad'::regclass,
                                       'atributo_encargo'::regclass)));

SELECT pg_temp.comprobar('4P el linaje se direcciona por la clave logica',
    EXISTS (SELECT 1 FROM pg_indexes
             WHERE tablename = 'rastro_valor_gobernado'
               AND indexdef LIKE '%organizacion_id, sujeto, id_agregado, clave%'));

-- Una fila de apoyo para las pruebas destructivas. Todo esto se deshace en el
-- ROLLBACK final, igual que el resto del gate.
INSERT INTO rastro_valor_gobernado
    (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto, canal)
VALUES ((SELECT org FROM ctx), 'PROPIEDAD', (SELECT prop FROM ctx),
        'gate_4p', 'ALTA', 'valor de prueba', 'SISTEMA');

SELECT pg_temp.debe_rechazar('4P el linaje no se puede corregir', $f$
    UPDATE rastro_valor_gobernado SET valor_texto = 'otra cosa' WHERE clave = 'gate_4p'
$f$);

SELECT pg_temp.debe_rechazar('4P el linaje no se puede borrar', $f$
    DELETE FROM rastro_valor_gobernado WHERE clave = 'gate_4p'
$f$);

-- NULL no es una cuarta naturaleza, y por eso DESCONOCIDO no existe: colapsaria
-- "no consta como se supo" con "se supo por una inferencia", que son cosas
-- distintas.
SELECT pg_temp.debe_rechazar('4P no hay una cuarta naturaleza', format($f$
    INSERT INTO rastro_valor_gobernado
        (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto, naturaleza)
    VALUES (%s, 'PROPIEDAD', %s, 'gate_4p', 'ALTA', 'x', 'DESCONOCIDO')
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

-- Una inferencia sin autor no se puede revisar ni retirar el dia que el modelo
-- resulte estar equivocado, y en silencio se convierte en un hecho confirmado.
SELECT pg_temp.debe_rechazar('4P un INFERIDO sin autor, modelo, version ni confianza', format($f$
    INSERT INTO rastro_valor_gobernado
        (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto, naturaleza)
    VALUES (%s, 'PROPIEDAD', %s, 'gate_4p', 'ALTA', 'x', 'INFERIDO')
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

SELECT pg_temp.debe_rechazar('4P un ALTA no puede haber hallado un valor', format($f$
    INSERT INTO rastro_valor_gobernado
        (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto, hallado_texto)
    VALUES (%s, 'PROPIEDAD', %s, 'gate_4p', 'ALTA', 'x', 'lo que habia')
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

SELECT pg_temp.debe_rechazar('4P una RETIRADA no deja valor vigente', format($f$
    INSERT INTO rastro_valor_gobernado
        (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto)
    VALUES (%s, 'PROPIEDAD', %s, 'gate_4p', 'RETIRADA', 'x')
$f$, (SELECT org FROM ctx), (SELECT prop FROM ctx)));

-- LA GENESIS NO INVENTA COMO SE CONOCIO EL HECHO. La procedencia OPERACIONAL de
-- una fila historica puede ser demostrable; su naturaleza, casi nunca.
SELECT pg_temp.comprobar('4P ninguna genesis declara naturaleza',
    NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado
                 WHERE registrado_en < frontera_de_linaje()
                   AND naturaleza IS NOT NULL));

-- LA FRONTERA DE GARANTIA, explicita y consultable: es lo que permite decir de
-- que lado cae cada fila sin dejarlo al criterio de quien consulte.
SELECT pg_temp.comprobar('4P la frontera del cutover existe y ya paso',
    frontera_de_linaje() <= now());

-- Y el otro lado de la frontera: DESPUES del cutover, un valor gobernado sin
-- linaje ES UN DEFECTO. Antes puede haberlo y no lo es -- las 70 filas que V48
-- copio de las columnas legadas se quedaron sin genesis a proposito.
--
-- Y el motivo no es que no se sepa CUALES son: la particion por correlacion las
-- separa sola (0 eventos -> 70, 1 evento -> 6, dos o mas -> ninguna), y su fecha
-- es su propio `fecha_creacion`, el mismo criterio que se aplico a las 6. Lo que
-- no se puede demostrar de ellas es su CANAL: estampar SISTEMA afirmaria que el
-- valor lo origino el sistema, y V48 no lo origino -- lo TRANSCRIBIO de una
-- columna cuyo autor no consta. Una genesis con canal inventado es exactamente
-- lo que la decision congelada prohibe.
-- LAS TRES DE ESTA FAMILIA VIAJAN CON SU CONTROL POSITIVO (auditoria del
-- 2026-08-25, N14; reparado en D0 el 2026-08-30).
--
-- Las tres son `NOT EXISTS`. Sobre una base recien creada -- y sobre
-- `controllocal_dev`, que es contra la que `Verificar-Cierre.ps1` pasa el gate --
-- su universo es CERO: medido el 2026-08-30, `atributo_propiedad` no tiene
-- ninguna fila posterior a la frontera, `atributo_encargo` no tiene ninguna fila
-- y ninguna propiedad se registro despues del cutover. Las tres salian verdes
-- SIN MIRAR NINGUNA FILA, que es exactamente la ceguera que ya se corrigio en la
-- familia del legado de 5A.
--
-- No se arregla exigiendo que el universo sea mayor que cero: eso ataria el gate
-- al RESIDUO de una base concreta, que es el defecto de al lado. Se arregla
-- construyendo el caso: cada predicado se define UNA SOLA VEZ como funcion, la
-- invariante pregunta si hay alguna infractora y el control siembra una y exige
-- que LA ENCUENTRE. Si un filtro de mas o un `AND` invertido dejara el predicado
-- ciego, el control sale ROJO aunque la invariante siga verde.
--
-- Y el tamano REAL del universo se declara en la columna `nota`, para que un
-- (0 filas, verde) quede dicho y no pase por medicion.
CREATE OR REPLACE FUNCTION pg_temp.hay_hecho_sin_linaje()
RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (
        SELECT 1 FROM atributo_propiedad a
         WHERE a.fecha_creacion > frontera_de_linaje()
           AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                            WHERE r.organizacion_id = a.organizacion_id
                              AND r.sujeto = 'PROPIEDAD'
                              AND r.id_agregado = a.id_propiedad
                              AND r.clave = a.clave))
$$;

SELECT count(*) AS n_hecho_4p FROM atributo_propiedad
 WHERE fecha_creacion > frontera_de_linaje() \gset

SELECT pg_temp.comprobar('4P despues del cutover ningun hecho del inmueble sin linaje',
    NOT pg_temp.hay_hecho_sin_linaje(),
    'un hecho escrito despues del cutover sin nadie que lo declare',
    format('hechos del inmueble posteriores al cutover en esta base: %s filas', :n_hecho_4p));

-- EL CONTROL. Se siembra sobre una CASA que NO tenga rastro de `area_terreno`
-- --si lo tuviera, el predicado no deberia cazarla y el control mediria el dato
-- elegido en vez de la invariante-- y por una puerta que NO hay que forzar:
-- `area_terreno` sigue aplicando a `C` (D-7 solo la retiro de `T`), asi que la
-- fila entra sola y con `fecha_creacion` de hoy, que es posterior al cutover.
-- 0 = no hay ninguna, y entonces el control no probo nada y lo dice.
SELECT COALESCE((SELECT min(p.id_propiedad) FROM propiedad p
                  WHERE p.tipo_inmueble = 'C'
                    AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                                     WHERE r.sujeto = 'PROPIEDAD'
                                       AND r.id_agregado = p.id_propiedad
                                       AND r.clave = 'area_terreno')), 0) AS casa_4p \gset

SAVEPOINT hecho_4p;
DELETE FROM atributo_propiedad WHERE id_propiedad = :casa_4p AND clave = 'area_terreno';
INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero)
SELECT organizacion_id, id_propiedad, 'area_terreno', 321
  FROM propiedad WHERE id_propiedad = :casa_4p;

SELECT CASE
    WHEN :casa_4p = 0
        THEN 'FALLO - no hay ninguna CASA sin rastro de area_terreno: el control no probo nada'
    WHEN NOT EXISTS (SELECT 1 FROM atributo_propiedad
                      WHERE id_propiedad = :casa_4p AND clave = 'area_terreno')
        THEN 'FALLO - no se pudo sembrar el hecho sin linaje: el control no probo nada'
    WHEN pg_temp.hay_hecho_sin_linaje() THEN 'OK'
    ELSE 'FALLO - el predicado no caza un hecho posterior al cutover sin linaje'
  END AS v_hecho_4p \gset
ROLLBACK TO SAVEPOINT hecho_4p;

INSERT INTO resultado (prueba, veredicto)
VALUES ('4P CONTROL el predicado caza un hecho del inmueble sin linaje', :'v_hecho_4p');

CREATE OR REPLACE FUNCTION pg_temp.hay_condicion_sin_linaje()
RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (
        SELECT 1 FROM atributo_encargo a
         WHERE a.fecha_creacion > frontera_de_linaje()
           AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                            WHERE r.organizacion_id = a.organizacion_id
                              AND r.sujeto = 'ENCARGO'
                              AND r.id_agregado = a.id_captacion
                              AND r.clave = a.clave))
$$;

SELECT count(*) AS n_condicion_4p FROM atributo_encargo
 WHERE fecha_creacion > frontera_de_linaje() \gset

SELECT pg_temp.comprobar('4P despues del cutover ninguna condicion del encargo sin linaje',
    NOT pg_temp.hay_condicion_sin_linaje(),
    'una condicion pactada despues del cutover sin nadie que la declare',
    format('condiciones del encargo posteriores al cutover en esta base: %s filas',
           :n_condicion_4p));

-- EL SUJETO SE ELIGE, NO SE SUPONE. Hace falta un encargo vivo y una clave del
-- ENCARGO que APLIQUE a su par (tipo de inmueble, operacion) -- si no aplicara,
-- `exigir_atributo_de_encargo` la rechazaria y el control diria que no pudo
-- sembrar-- , que no tenga ya fila y que no tenga rastro. Se busca en la base en
-- vez de escribir un par a mano: un par escrito aqui envejece con el seed.
CREATE TEMP TABLE ctx_condicion_4p AS
SELECT cap.id_captacion, cap.organizacion_id, c.clave
  FROM captacion cap
  JOIN propiedad p ON p.id_propiedad = cap.id_propiedad
  JOIN catalogo_atributo_operacion o ON o.tipo_propiedad = p.tipo_inmueble
                                    AND o.tipo_operacion = cap.motivo_operacion
  JOIN catalogo_atributo c ON c.id_catalogo_atributo = o.id_catalogo_atributo
 WHERE c.organizacion_id IS NULL AND c.activo AND c.sujeto = 'ENCARGO'
   AND c.tipo_dato = 'BOOLEANO'
   AND NOT EXISTS (SELECT 1 FROM atributo_encargo a
                    WHERE a.id_captacion = cap.id_captacion AND a.clave = c.clave)
   AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                    WHERE r.sujeto = 'ENCARGO' AND r.id_agregado = cap.id_captacion
                      AND r.clave = c.clave)
 ORDER BY cap.id_captacion, c.clave LIMIT 1;

SELECT COALESCE((SELECT id_captacion FROM ctx_condicion_4p), 0) AS encargo_4p,
       COALESCE((SELECT clave FROM ctx_condicion_4p), '(ninguna)') AS clave_4p \gset

SAVEPOINT condicion_4p;
INSERT INTO atributo_encargo (organizacion_id, id_captacion, clave, valor_booleano)
SELECT organizacion_id, id_captacion, clave, true FROM ctx_condicion_4p;

SELECT CASE
    WHEN :encargo_4p = 0
        THEN 'FALLO - no hay ningun encargo con una clave BOOLEANA libre: el control no probo nada'
    WHEN NOT EXISTS (SELECT 1 FROM atributo_encargo
                      WHERE id_captacion = :encargo_4p AND clave = :'clave_4p')
        THEN 'FALLO - no se pudo sembrar la condicion sin linaje: el control no probo nada'
    WHEN pg_temp.hay_condicion_sin_linaje() THEN 'OK'
    ELSE 'FALLO - el predicado no caza una condicion posterior al cutover sin linaje'
  END AS v_condicion_4p \gset
ROLLBACK TO SAVEPOINT condicion_4p;

INSERT INTO resultado (prueba, veredicto)
VALUES ('4P CONTROL el predicado caza una condicion del encargo sin linaje',
        :'v_condicion_4p');


-- LA QUINTA SUPERFICIE, VIGILADA TAMBIEN AQUI (segunda vuelta de 4.P).
--
-- Las dos comprobaciones de arriba miran `atributo_propiedad` y
-- `atributo_encargo`, y una clave ESTRUCTURAL **por definicion NO CREA FILA
-- ahi**: su autoridad es una columna de `propiedad`. Con solo esas dos, un
-- valor gobernado escrito por fuera del enrutador --como hacia
-- `ubicacion.piso`-- pasaba el gate en verde. Un agujero que el gate no ve es
-- peor que el agujero.
--
-- Se mide sobre las propiedades REGISTRADAS despues del cutover: en esas, un
-- valor en la columna solo puede haberse escrito despues, asi que tiene que
-- tener linaje. Para las anteriores no se puede afirmar nada -- y no se afirma.
CREATE OR REPLACE FUNCTION pg_temp.hay_estructural_sin_linaje()
RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (
        SELECT 1
          FROM propiedad p
          CROSS JOIN LATERAL (VALUES ('METRAJE',           p.metraje::text),
                                     ('PISO',              p.piso),
                                     ('PARTIDA_REGISTRAL', p.partida_registral),
                                     ('OFICINA_REGISTRAL', p.oficina_registral)) AS e(campo, valor)
         WHERE p.fecha_registro > frontera_de_linaje()
           AND e.valor IS NOT NULL
           AND NOT EXISTS (
               SELECT 1
                 FROM rastro_valor_gobernado r
                 JOIN catalogo_atributo c
                   ON c.clave = r.clave
                  AND c.campo_estructural = e.campo
                  AND (c.organizacion_id IS NULL OR c.organizacion_id = r.organizacion_id)
                WHERE r.organizacion_id = p.organizacion_id
                  AND r.sujeto = 'PROPIEDAD'
                  AND r.id_agregado = p.id_propiedad))
$$;

SELECT count(*) AS n_estructural_4p FROM propiedad
 WHERE fecha_registro > frontera_de_linaje() \gset

SELECT pg_temp.comprobar('4P despues del cutover ninguna columna estructural sin linaje',
    NOT pg_temp.hay_estructural_sin_linaje(),
    'una columna canonica escrita despues del cutover sin nadie que la declare',
    format('propiedades registradas despues del cutover en esta base: %s', :n_estructural_4p));

-- EL CONTROL DE LA QUINTA SUPERFICIE. Aqui no se puede sembrar una columna
-- estructural nueva sin arrastrar media base -- una `propiedad` tiene NOT NULLs,
-- claves ajenas y tres triggers --, asi que el caso se construye al reves: se
-- busca una propiedad a la que YA le falta el rastro de una de las cuatro
-- columnas y a la que solo le falta estar del lado malo de la frontera, y se la
-- pasa. Lo unico que el control fabrica es la FECHA, que es justamente la
-- coordenada que decide si la fila es defecto o legado.
CREATE TEMP TABLE ctx_estructural_4p AS
SELECT p.id_propiedad, e.campo
  FROM propiedad p
  CROSS JOIN LATERAL (VALUES ('METRAJE',           p.metraje::text),
                             ('PISO',              p.piso),
                             ('PARTIDA_REGISTRAL', p.partida_registral),
                             ('OFICINA_REGISTRAL', p.oficina_registral)) AS e(campo, valor)
 WHERE p.fecha_registro <= frontera_de_linaje()
   AND e.valor IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM rastro_valor_gobernado r
         JOIN catalogo_atributo c ON c.clave = r.clave AND c.campo_estructural = e.campo
                                 AND (c.organizacion_id IS NULL
                                   OR c.organizacion_id = r.organizacion_id)
        WHERE r.organizacion_id = p.organizacion_id AND r.sujeto = 'PROPIEDAD'
          AND r.id_agregado = p.id_propiedad)
 ORDER BY p.id_propiedad, e.campo LIMIT 1;

SELECT COALESCE((SELECT id_propiedad FROM ctx_estructural_4p), 0) AS prop_estructural_4p \gset

SAVEPOINT estructural_4p;
UPDATE propiedad SET fecha_registro = now() WHERE id_propiedad = :prop_estructural_4p;

SELECT CASE
    WHEN :prop_estructural_4p = 0
        THEN 'FALLO - no hay ninguna propiedad anterior al cutover sin rastro de una columna: el control no probo nada'
    WHEN NOT EXISTS (SELECT 1 FROM propiedad
                      WHERE id_propiedad = :prop_estructural_4p
                        AND fecha_registro > frontera_de_linaje())
        THEN 'FALLO - no se pudo pasar la propiedad al lado malo de la frontera: el control no probo nada'
    WHEN pg_temp.hay_estructural_sin_linaje() THEN 'OK'
    ELSE 'FALLO - el predicado no caza una columna estructural posterior al cutover sin linaje'
  END AS v_estructural_4p \gset
ROLLBACK TO SAVEPOINT estructural_4p;

INSERT INTO resultado (prueba, veredicto)
VALUES ('4P CONTROL el predicado caza una columna estructural sin linaje',
        :'v_estructural_4p');

-- LA COHERENCIA TEMPORAL DEL DATO, que hasta D0 no miraba ningun gate.
--
-- Un valor no puede haber nacido ANTES que la propiedad de la que cuelga. No es
-- una sutileza de fechas: la frontera del linaje se decide con esas mismas dos
-- columnas, asi que una fila con la linea de tiempo rota se coloca sola del lado
-- del legado -- y sale del universo de las tres comprobaciones de arriba sin que
-- nada lo diga. Es exactamente lo que hizo un fixture hasta que `N13` lo corrigio
-- (auditoria del 2026-08-25) y lo que dejo cuatro filas imposibles en
-- `controllocal_repositorios` hasta el saneamiento de D0 (2026-08-30,
-- `verificacion/sanear-residuo-de-pruebas.sql`).
--
-- Esta SI tiene universo de verdad en las dos bases -- son todas las filas de
-- `atributo_propiedad` --, y aun asi viaja con su control: lo que se vigila es un
-- predicado, y un predicado que nadie ha visto cazar nada no esta probado.
CREATE OR REPLACE FUNCTION pg_temp.hay_valor_anterior_a_su_propiedad()
RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (
        SELECT 1 FROM atributo_propiedad a
          JOIN propiedad p ON p.id_propiedad = a.id_propiedad
         WHERE a.fecha_creacion < p.fecha_registro)
$$;

SELECT count(*) AS n_temporal_4p FROM atributo_propiedad \gset

SELECT pg_temp.comprobar('4P ningun valor del inmueble nace antes que su propia propiedad',
    NOT pg_temp.hay_valor_anterior_a_su_propiedad(),
    'un valor anterior a su propia propiedad cae del lado del legado sin serlo',
    format('valores del inmueble contrastados en esta base: %s filas', :n_temporal_4p));

SAVEPOINT temporal_4p;
DELETE FROM atributo_propiedad WHERE id_propiedad = :casa_4p AND clave = 'area_terreno';
INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero, fecha_creacion)
SELECT organizacion_id, id_propiedad, 'area_terreno', 321, fecha_registro - interval '1 day'
  FROM propiedad WHERE id_propiedad = :casa_4p;

SELECT CASE
    WHEN :casa_4p = 0
        THEN 'FALLO - no hay ninguna CASA utilizable: el control no probo nada'
    WHEN NOT EXISTS (SELECT 1 FROM atributo_propiedad
                      WHERE id_propiedad = :casa_4p AND clave = 'area_terreno')
        THEN 'FALLO - no se pudo sembrar el valor imposible: el control no probo nada'
    WHEN pg_temp.hay_valor_anterior_a_su_propiedad() THEN 'OK'
    ELSE 'FALLO - el predicado no caza un valor anterior a su propia propiedad'
  END AS v_temporal_4p \gset
ROLLBACK TO SAVEPOINT temporal_4p;

INSERT INTO resultado (prueba, veredicto)
VALUES ('4P CONTROL el predicado caza un valor anterior a su propiedad',
        :'v_temporal_4p');

-- CONTROL DE COBERTURA, y es lo que de verdad protege. La comprobacion de
-- arriba nombra cuatro columnas a mano; si manana el catalogo declara un quinto
-- campo canonico y nadie la actualiza, seguiria en verde vigilando cuatro de
-- cinco -- que es EXACTAMENTE como paso desapercibido el agujero de `piso`: el
-- inventario barrio las cuatro TABLAS de valor y no barrio nunca las cuatro
-- COLUMNAS estructurales.
SELECT pg_temp.comprobar('4P la frontera vigila TODOS los campos canonicos declarados',
    (SELECT coalesce(array_agg(DISTINCT campo_estructural ORDER BY campo_estructural),
                     ARRAY[]::varchar[])
       FROM catalogo_atributo
      WHERE destino = 'ESTRUCTURAL' AND activo)
    = ARRAY['METRAJE', 'OFICINA_REGISTRAL', 'PARTIDA_REGISTRAL', 'PISO']::varchar[],
    'el catalogo declara campos canonicos que la comprobacion de frontera no mira');
-- Los dos valores rescatados de la `descripcion` historica no quedan como filas
-- sin genealogia: consta DE DONDE se copio el texto -- que es comprobable -- y
-- NO consta quien lo origino, que nadie sabe.
-- Se compara contra el numero de valores que existen, y no contra si mismo: un
-- `count = count FILTER` da 0 = 0 en una base donde esos valores no estan, y un
-- verde que no ha mirado nada no es un verde.
SELECT pg_temp.comprobar('4P las transcripciones documentadas nombran su fuente',
    (SELECT count(*) FROM rastro_valor_gobernado r
       JOIN propiedad p ON p.id_propiedad = r.id_agregado
                       AND p.organizacion_id = r.organizacion_id
      WHERE r.sujeto = 'PROPIEDAD' AND r.clave = 'tipo_acceso'
        AND p.codigo IN ('LOC-D001', 'LOC-0002')
        AND r.evidencia_ref LIKE '%propiedad.descripcion%'
        AND r.naturaleza IS NULL)
    =
    (SELECT count(*) FROM atributo_propiedad a
       JOIN propiedad p ON p.id_propiedad = a.id_propiedad
      WHERE a.clave = 'tipo_acceso' AND p.codigo IN ('LOC-D001', 'LOC-0002')),
    'una transcripcion documentada perdio su fuente, o gano una naturaleza inventada');

-- =====================================================================
-- CORTE 5 · 5A - la ocupacion y los servicios, con vocabulario (V84)
--
-- Lo que se comprueba aqui es lo que ningun test de Java mira con esta forma:
-- que la CARTERA REAL esta del lado correcto del catalogo, y que la ultima LISTA
-- muda no puede volver por otra puerta.
-- =====================================================================

-- EL PAR, EN LOS DOS SENTIDOS. Que la clave exista no basta: el hecho tiene que
-- llegar a TODOS los tipos donde su condicion se pacta, o quedaria un tipo en el
-- que el pacto es el unico sitio donde cabe el hecho -- y un pacto muere con su
-- encargo. Se mide el hueco Y la cobertura, porque solo con el hueco esto
-- saldria verde el dia que `entrega_desocupado` desapareciera del catalogo: cero
-- descubiertos sobre un universo vacio.
--
-- LAS TRES MIRAN EL MISMO UNIVERSO: el catalogo DEL SISTEMA. La tercera ya lo
-- filtraba y las dos primeras no, y esa asimetria tenia consecuencia: las dos
-- primeras habrian salido verdes sobre una clave que solo existe en un tenant.
--
-- HASTA DONDE LLEGA EL MOTIVO, MEDIDO (correccion del 2026-08-25). Este
-- comentario decia antes que el sombreado lo produce "una organizacion que
-- declare su propia `estado_ocupacion`", a secas. La base NO permite eso a
-- secas: `exigir_catalogo_no_sombrea_al_sistema` (V55) rechaza la fila del
-- tenant --«una organizacion no puede redefinirla»-- en cuanto la del sistema
-- existe. El filtro sigue siendo correcto y barato, pero su motivo es mas
-- estrecho:
--
--   * ALCANZABLE, en un orden historico concreto: el trigger solo mira en UNA
--     direccion. Sale por `RETURN NEW` cuando `NEW.organizacion_id IS NULL`, asi
--     que una migracion que siembra la clave del sistema NO comprueba si algun
--     tenant ya la tenia. Un tenant que hubiera declarado `estado_ocupacion`
--     antes de `V84` conserva su fila, y sin este filtro taparia el hueco.
--   * NO ALCANZABLE, y conviene decirlo para no defender lo que no se defiende:
--     una SEGUNDA fila del SISTEMA con la misma clave. La impide
--     `uq_catalogo_atributo_clave` (V48), que es UNICO sobre
--     `(COALESCE(organizacion_id, 0), clave)`. Ese caso no existe, y
--     `organizacion_id IS NULL` no seria quien lo evitara.
SELECT pg_temp.comprobar('5A el hecho de la ocupacion llega donde se pacta su condicion',
    NOT EXISTS (
        SELECT 1
          FROM catalogo_atributo cond
          JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = cond.id_catalogo_atributo
          JOIN catalogo_atributo hecho ON hecho.clave = 'estado_ocupacion' AND hecho.activo
                                      AND hecho.organizacion_id IS NULL
         WHERE cond.clave = 'entrega_desocupado' AND cond.activo
           AND cond.organizacion_id IS NULL
           AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                            WHERE t.id_catalogo_atributo = hecho.id_catalogo_atributo
                              AND t.tipo_propiedad = o.tipo_propiedad)));

SELECT pg_temp.comprobar('5A y el par esta cubierto en los SIETE tipos, no en cero',
    (SELECT count(DISTINCT o.tipo_propiedad) = 7
       FROM catalogo_atributo cond
       JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = cond.id_catalogo_atributo
       JOIN catalogo_atributo hecho ON hecho.clave = 'estado_ocupacion' AND hecho.activo
                                   AND hecho.organizacion_id IS NULL
       JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = hecho.id_catalogo_atributo
                                    AND t.tipo_propiedad = o.tipo_propiedad
      WHERE cond.clave = 'entrega_desocupado' AND cond.activo
        AND cond.organizacion_id IS NULL));

SELECT pg_temp.comprobar('5A estado_ocupacion aplica EXACTAMENTE a los siete tipos',
    (SELECT array_agg(t.tipo_propiedad ORDER BY t.tipo_propiedad)
            = ARRAY['A','C','D','L','O','T','X']::varchar[]
       FROM catalogo_atributo c JOIN catalogo_atributo_tipo t USING (id_catalogo_atributo)
      WHERE c.clave = 'estado_ocupacion' AND c.organizacion_id IS NULL));

-- LA RETIRADA, POR SUS DOS MITADES. Ninguna sirve sin la otra: la clave sin
-- reemplazo deja un agujero de captura, y el reemplazo sin la retirada deja dos
-- sitios para el mismo hecho.
SELECT pg_temp.comprobar('5A servicios_disponibles esta retirada y NO borrada',
    EXISTS (SELECT 1 FROM catalogo_atributo
             WHERE clave = 'servicios_disponibles' AND organizacion_id IS NULL
               AND del_sistema AND NOT activo));

SELECT pg_temp.comprobar('5A sus dos reemplazos estan activos y con vocabulario',
    (SELECT count(*) = 2
       FROM catalogo_atributo c
      WHERE c.organizacion_id IS NULL AND c.activo
        AND c.clave IN ('agua_desague', 'energia_electrica')
        AND c.tipo_dato = 'LISTA'
        AND (SELECT count(*) FROM catalogo_atributo_opcion o
              WHERE o.id_catalogo_atributo = c.id_catalogo_atributo AND o.activo) = 3));

SELECT pg_temp.comprobar('5A los dos servicios impiden PUBLICAR un terreno',
    (SELECT count(*) = 2
       FROM catalogo_atributo c JOIN catalogo_atributo_tipo t USING (id_catalogo_atributo)
      WHERE c.organizacion_id IS NULL AND c.clave IN ('agua_desague', 'energia_electrica')
        AND t.tipo_propiedad = 'T' AND t.exigencia = 'PUB' AND NOT t.requerido));

-- LA GUARDA QUE 5A DEJA PUESTA, y la razon de que la retirada sea una solucion
-- legitima y no un rodeo: la palabra es ACTIVA. Una clave retirada no se
-- pregunta, asi que no puede nacer muda. Cubre los dos sujetos y los dos ambitos
-- --sistema y tenant--, porque una organizacion puede declarar las suyas.
SELECT pg_temp.comprobar('5A ninguna LISTA activa se quedo sin vocabulario',
    NOT EXISTS (
        SELECT 1 FROM catalogo_atributo c
         WHERE c.activo AND c.tipo_dato IN ('LISTA', 'LISTA_MULTIPLE')
           AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                            WHERE o.id_catalogo_atributo = c.id_catalogo_atributo
                              AND o.activo)),
    'una LISTA sin opciones se degrada a TEXTO en el motor de captura y el trigger acepta cualquier cadena');

-- `gas` CONSERVA SU CONCEPTO Y GANA UNA OPCION (D-2). Se comprueban las dos
-- mitades: que la opcion esta, y que la clave no se convirtio en otra cosa por
-- el camino -- ni de tipo, ni de aplicabilidad, ni extendida a X.
SELECT pg_temp.comprobar('5A gas distingue la red de la calle del papel de la concesionaria',
    (SELECT count(*) = 2 FROM catalogo_atributo c
       JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
      WHERE c.organizacion_id IS NULL AND c.clave = 'gas'
        AND o.valor IN ('RED_EN_LA_VIA', 'CON_FACTIBILIDAD_APROBADA') AND o.activo));

SELECT pg_temp.comprobar('5A y gas no cambio de concepto: sigue LISTA y no llego a X',
    (SELECT c.tipo_dato = 'LISTA'
            AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                             WHERE t.id_catalogo_atributo = c.id_catalogo_atributo
                               AND t.tipo_propiedad = 'X')
       FROM catalogo_atributo c
      WHERE c.organizacion_id IS NULL AND c.clave = 'gas'));

-- EL ESPEJO, sobre TODO el catalogo. Es el guard 2.4 de V78, que hasta hoy solo
-- corria dentro de las migraciones: se rompe cuando alguien cambia UNA de las
-- dos columnas, y eso puede pasar por SQL directo sin que ninguna migracion lo
-- vea.
SELECT pg_temp.comprobar('5A requerido sigue siendo espejo exacto de exigencia = ALT',
    NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t WHERE t.requerido <> (t.exigencia = 'ALT')));

-- LO QUE EL LEGADO NO PUEDE HABER GANADO. Un inmueble cuyo unico dato de
-- servicios era una cadena ambigua no puede aparecer con el hecho ya declarado
-- por el sistema: eso seria haber traducido "tiene agua" a CONECTADO, que es
-- inventar por el caso frecuente justo la distincion que el campo viejo no sabia
-- hacer. Lo ambiguo permanece FALTANTE.
--
-- EL PREDICADO QUIERE DECIR "NADIE DECLARO ESTE HECHO", Y NO SE APROXIMA CON EL
-- CANAL. La primera version exigia un rastro con `canal <> 'SISTEMA'`, y eso
-- PROHIBIA el unico mecanismo autorizado para mover legado: el reparto del
-- bloque 5 de V84 escribe `canal = 'SISTEMA'` con su `evidencia_ref`, asi que
-- esta comprobacion salia verde SOLO mientras el acta no resolviera ninguna
-- cadena -- y se habria puesto roja el dia que resolviera una, es decir, por
-- comportarse bien. La auditoria del 2026-08-25 lo reprodujo.
--
-- Lo que distingue una traduccion clandestina de un reparto legitimo no es el
-- canal --los dos son escrituras del Core-- sino el LINAJE: el reparto deja su
-- fila en `rastro_valor_gobernado` nombrando el acta en `evidencia_ref`, y una
-- declaracion de una persona deja la suya con su naturaleza. Un valor que
-- aparece sobre un legado ambiguo SIN NINGUN rastro no lo ha afirmado nadie:
-- eso es lo prohibido.
--
-- SE ESCRIBE COMO INVARIANTE Y NUNCA COMO LA CIFRA 0 de filas legadas. Aqui
-- habia una segunda frase que era FALSA y la auditoria del 2026-08-25 la midio:
-- decia que "en la base de integracion un fixture las escribe en cada corrida".
-- No lo hacia. El unico productor de `servicios_disponibles` era el fixture de
-- `ConservacionDeLaEdicionIntegrationTest`, y este mismo corte lo retiro al
-- retirar la clave. Las filas que quedan en `controllocal_repositorios` son
-- RESIDUO HISTORICO: sobre una base nueva --CI, otra maquina, un
-- `docker volume rm`-- el universo es CERO, y esta comprobacion saldria verde
-- sin haber mirado nada. En `controllocal_dev` el universo ya es cero HOY.
--
-- EL TAMANO DE ESE RESIDUO NO SE ESCRIBE AQUI COMO CIFRA, y es la misma regla
-- que manda V84: una cifra caduca en cuanto corre una suite --el fixture
-- `sembrarLegadoAmbiguo` deja dos filas por corrida--, asi que un comentario
-- con un numero envejece a mentira sin que nada lo avise. El numero de verdad
-- lo MIDE y lo IMPRIME el CONTROL POSITIVO de aqui abajo --la comprobacion
-- "5A CONTROL el predicado del legado caza una traduccion sin linaje"-- en su
-- columna `nota` ("legado realmente presente en esta base: N filas"): ahi esta
-- siempre al dia y no hay que mantenerlo a mano.
--
-- Por eso la invariante viaja con el CONTROL POSITIVO que va justo debajo: no se
-- confia en encontrar legado, se construye el par exacto que el predicado tiene
-- que cazar y se deshace. Un cero que no se ha contrastado con un control
-- positivo no es un cero.
-- EL RASTRO TIENE QUE SER DEL VALOR VIGENTE, Y NO DE CUALQUIERA ANTERIOR
-- (auditoria del 2026-08-25, N8; reparado en D0 el 2026-08-30).
--
-- La primera version se conformaba con que EXISTIERA algun rastro con autoria
-- sobre esa clave. Eso deja pasar el caso que la comprobacion existe para
-- prohibir: alguien declara `agua_desague = FACTIBILIDAD_APROBADA` --y deja su
-- rastro--, y despues otro camino PISA ese valor con `CONECTADO` sin dejar
-- ninguno. El valor vigente no lo ha afirmado nadie, pero el rastro viejo seguia
-- valiendo como coartada: la comprobacion contaba el hecho como declarado y el
-- inmueble con legado ambiguo aparecia con el servicio resuelto.
--
-- Se correlaciona, entonces, RASTRO y VALOR VIGENTE: tiene que existir un rastro
-- con autoria QUE HABLE DEL VALOR QUE HOY ESTA ESCRITO. Es lo que el Core hace
-- por la puerta normal --`LinajeDelValor` anota `escrito.texto()` en cada
-- escritura, alta y edicion--, asi que un historial legitimo lo cumple: la ultima
-- edicion deja su rastro con el valor que deja puesto. Lo que deja de cumplirlo
-- es exactamente la escritura clandestina.
--
-- EL PREDICADO SE DEFINE UNA SOLA VEZ. La invariante y sus controles tienen
-- que preguntar EXACTAMENTE lo mismo: si se escribieran dos veces, romper una
-- dejaria la otra intacta y el control dejaria de vigilar lo que dice vigilar.
-- El parametro NO es una segunda version del predicado: acota el SUJETO para que
-- el control pueda afirmar algo sobre el inmueble que acaba de sembrar sin que la
-- respuesta dependa del residuo del resto de la base. Con NULL --el que usa la
-- invariante-- mira la base entera.
CREATE OR REPLACE FUNCTION pg_temp.hay_legado_traducido_sin_linaje(
        p_id_propiedad bigint DEFAULT NULL)
RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (
        SELECT 1
          FROM atributo_propiedad legado
          JOIN atributo_propiedad nuevo ON nuevo.id_propiedad = legado.id_propiedad
                                       AND nuevo.clave IN ('agua_desague', 'energia_electrica')
         WHERE legado.clave = 'servicios_disponibles'
           AND (p_id_propiedad IS NULL OR legado.id_propiedad = p_id_propiedad)
           AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                            WHERE r.organizacion_id = nuevo.organizacion_id
                              AND r.sujeto = 'PROPIEDAD'
                              AND r.id_agregado = nuevo.id_propiedad
                              AND r.clave = nuevo.clave
                              AND r.valor_texto IS NOT DISTINCT FROM nuevo.valor_texto
                              AND (r.naturaleza IS NOT NULL
                                OR r.evidencia_ref IS NOT NULL
                                OR r.id_persona_rol IS NOT NULL)))
$$;

SELECT count(*) AS n_pares_5a FROM atributo_propiedad legado
  JOIN atributo_propiedad nuevo ON nuevo.id_propiedad = legado.id_propiedad
                               AND nuevo.clave IN ('agua_desague', 'energia_electrica')
 WHERE legado.clave = 'servicios_disponibles' \gset

SELECT pg_temp.comprobar('5A ningun inmueble con legado recibio un servicio sin que nadie lo afirmara',
    NOT pg_temp.hay_legado_traducido_sin_linaje(),
    'un valor de servicio sobre un legado ambiguo sin nadie que lo declare ni acta que lo reparta',
    format('pares legado+servicio contrastados en esta base: %s', :n_pares_5a));

-- CONTROL DE COBERTURA DE LA INVARIANTE DE ARRIBA -- «5A ningun inmueble con
-- legado recibio un servicio sin que nadie lo afirmara» --, y por que hace falta
-- (auditoria del 2026-08-25). Se nombra y no se numera: el orden del informe lo
-- decide el fichero, y una comprobacion nueva mas arriba corre los numeros.
--
-- Es un NOT EXISTS sobre un JOIN de tres tablas. Si su universo esta
-- vacio sale VERDE sin haber mirado nada, y su universo esta vacio en
-- `controllocal_dev` --0 filas de `servicios_disponibles`-- y lo estara en
-- cualquier base recien creada, porque desde este corte NADIE escribe esa clave
-- por la ruta normal: la clave esta retirada y por la API la rechaza el Core en
-- Java --`AtributosGobernados.definicionDe` -> `CatalogoAtributoRepository.porClave`,
-- cuyo JPQL lleva `and c.activo = true`--, que sale con 400 sin llegar a
-- ejecutar el trigger. Esta linea decia que la rechazaba el trigger con 23503 y
-- era falsa por partida triple; se corrigio el 2026-08-26.
--
-- Esto no se arregla contando filas de legado y exigiendo que sean mas de cero:
-- eso volveria a atar el gate al RESIDUO de una base concreta, que es justo el
-- defecto. Se arregla construyendo el caso: se siembra el par exacto que el
-- predicado tiene que cazar --un legado ambiguo y un servicio nuevo que nadie
-- declaro-- y se exige que LO ENCUENTRE. Si un filtro de mas, un join mal puesto
-- o un `AND` invertido dejara el predicado ciego, esto sale ROJO aunque la
-- invariante siga verde.
--
-- SE ESCRIBE EL LEGADO SALTANDOSE LA PUERTA, Y SE DICE. `servicios_disponibles`
-- esta `activo = false`, asi que `exigir_atributo_gobernado` rechaza tambien el
-- INSERT directo: no lo encuentra en el catalogo. Para sembrarlo hay que
-- reactivar la clave, escribir y volver a retirarla, todo DENTRO del savepoint
-- --ninguna otra sesion ve la clave activa, y el `ROLLBACK TO` la deja como
-- estaba--. Que un control positivo necesite saltarse una guarda no es una
-- licencia: es la prueba de que la guarda esta puesta.
--
-- El universo REAL de esta base se declara en la columna `nota` del informe,
-- para que un (0 filas, verde) quede dicho y no pase por medicion. Iba en el
-- NOMBRE de la comprobacion y ahi no se leia: el informe cortaba a 62, el nombre
-- media 115 y la cifra iba en la cola cortada -- no se imprimio nunca (auditoria
-- del 2026-08-25, N10). Como el script termina en ROLLBACK, la tabla `resultado`
-- tampoco se podia consultar despues: la cifra era, literalmente, inobservable.
SELECT count(*) AS n_legado_5a FROM atributo_propiedad WHERE clave = 'servicios_disponibles' \gset

-- Un terreno --`servicios_disponibles` y `agua_desague` solo aplican a `T`-- sin
-- rastro previo de `agua_desague`: con rastro, el predicado no deberia cazarlo y
-- el control mediria el dato elegido en vez de la invariante. 0 = no hay
-- ninguno, y entonces el control no probo nada y lo dice.
SELECT COALESCE((SELECT min(p.id_propiedad) FROM propiedad p
                  WHERE p.tipo_inmueble = 'T'
                    AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                                     WHERE r.sujeto = 'PROPIEDAD'
                                       AND r.id_agregado = p.id_propiedad
                                       AND r.clave = 'agua_desague')), 0) AS terreno_5a \gset

SAVEPOINT legado_5a;
DELETE FROM atributo_propiedad
 WHERE id_propiedad = :terreno_5a AND clave IN ('servicios_disponibles', 'agua_desague');
UPDATE catalogo_atributo SET activo = true
 WHERE clave = 'servicios_disponibles' AND organizacion_id IS NULL;
-- LAS DOS FILAS NACEN ANTES DE LA FRONTERA DEL LINAJE, y no es un detalle. Un
-- valor posterior al cutover sin rastro es un defecto REAL que caza la
-- «4P despues del cutover ningun hecho del inmueble sin linaje»: sembrarlo con
-- `now()` fabricaria un dato imposible y
-- envenenaria otro gate desde este. El legado es, por definicion, anterior al
-- mecanismo de linaje. Aqui el savepoint lo deshace todo y esa comprobacion va
-- antes en el script, asi que hoy daria igual -- se hace igualmente para que el
-- control siga siendo correcto si alguien reordena.
INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto, fecha_creacion)
SELECT organizacion_id, id_propiedad, 'servicios_disponibles', 'Agua, luz y desague',
       frontera_de_linaje() - interval '1 day'
  FROM propiedad WHERE id_propiedad = :terreno_5a;
UPDATE catalogo_atributo SET activo = false
 WHERE clave = 'servicios_disponibles' AND organizacion_id IS NULL;
INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto, fecha_creacion)
SELECT organizacion_id, id_propiedad, 'agua_desague', 'CONECTADO',
       frontera_de_linaje() - interval '1 day'
  FROM propiedad WHERE id_propiedad = :terreno_5a;

SELECT CASE
    WHEN :terreno_5a = 0
        THEN 'FALLO - no hay ninguna propiedad de tipo T utilizable: el control no probo nada'
    WHEN NOT EXISTS (SELECT 1 FROM atributo_propiedad
                      WHERE id_propiedad = :terreno_5a AND clave = 'servicios_disponibles')
        THEN 'FALLO - no se pudo sembrar el legado: el control no probo nada'
    WHEN pg_temp.hay_legado_traducido_sin_linaje()
        THEN 'OK'
    ELSE 'FALLO - el predicado del legado no caza una traduccion sin linaje: su verde no significa nada'
  END AS v_legado_ctrl \gset
ROLLBACK TO SAVEPOINT legado_5a;

INSERT INTO resultado (prueba, veredicto, nota)
VALUES ('5A CONTROL el predicado del legado caza una traduccion sin linaje',
        :'v_legado_ctrl',
        format('legado realmente presente en esta base: %s filas', :n_legado_5a));

-- LA CORRELACION, EN LAS DOS DIRECCIONES (D0, 2026-08-30).
--
-- El control de arriba prueba el caso facil: NINGUN rastro. El que motivo `N8`
-- es el dificil, y es el unico que distingue el predicado nuevo del viejo: SI
-- hay rastro con autoria, pero habla de OTRO valor. Con el predicado anterior
-- ese caso salia declarado; con este tiene que cazarse.
--
-- Y VA CON SU MITAD NEGATIVA, que es lo que impide "arreglarlo" de mas: un
-- predicado que cazara siempre tambien pasaria la primera mitad, y entonces la
-- comprobacion 5A estaria prohibiendo el reparto legitimo en vez de la
-- traduccion clandestina. Asi que despues se anade el rastro DEL valor vigente y
-- se exige que el predicado DEJE DE CAZARLO.
--
-- Las dos miran SOLO el inmueble sembrado --el parametro del predicado-- porque
-- la respuesta sobre la base entera depende del residuo que traiga cada base, y
-- un control cuyo veredicto depende del residuo no es un control.
SAVEPOINT correlacion_5a;
DELETE FROM atributo_propiedad
 WHERE id_propiedad = :terreno_5a AND clave IN ('servicios_disponibles', 'agua_desague');
UPDATE catalogo_atributo SET activo = true
 WHERE clave = 'servicios_disponibles' AND organizacion_id IS NULL;
INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto, fecha_creacion)
SELECT organizacion_id, id_propiedad, 'servicios_disponibles', 'Agua, luz y desague',
       frontera_de_linaje() - interval '1 day'
  FROM propiedad WHERE id_propiedad = :terreno_5a;
UPDATE catalogo_atributo SET activo = false
 WHERE clave = 'servicios_disponibles' AND organizacion_id IS NULL;
INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto, fecha_creacion)
SELECT organizacion_id, id_propiedad, 'agua_desague', 'CONECTADO',
       frontera_de_linaje() - interval '1 day'
  FROM propiedad WHERE id_propiedad = :terreno_5a;

-- El rastro con autoria, pero de un valor que YA NO es el vigente.
INSERT INTO rastro_valor_gobernado
    (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto, canal, naturaleza)
SELECT organizacion_id, 'PROPIEDAD', id_propiedad, 'agua_desague', 'ALTA',
       'FACTIBILIDAD_APROBADA', 'SISTEMA', 'DECLARADO'
  FROM propiedad WHERE id_propiedad = :terreno_5a;

SELECT CASE
    WHEN :terreno_5a = 0
        THEN 'FALLO - no hay ninguna propiedad de tipo T utilizable: el control no probo nada'
    WHEN NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado
                      WHERE id_agregado = :terreno_5a AND clave = 'agua_desague'
                        AND valor_texto = 'FACTIBILIDAD_APROBADA')
        THEN 'FALLO - no se pudo sembrar el rastro descolocado: el control no probo nada'
    WHEN pg_temp.hay_legado_traducido_sin_linaje(:terreno_5a) THEN 'OK'
    ELSE 'FALLO - un rastro de OTRO valor sigue valiendo como coartada del vigente'
  END AS v_corr_otro \gset

-- Y ahora el rastro DEL valor vigente: el predicado tiene que soltarlo.
INSERT INTO rastro_valor_gobernado
    (organizacion_id, sujeto, id_agregado, clave, verbo, valor_texto, canal, naturaleza)
SELECT organizacion_id, 'PROPIEDAD', id_propiedad, 'agua_desague', 'EDICION',
       'CONECTADO', 'SISTEMA', 'DECLARADO'
  FROM propiedad WHERE id_propiedad = :terreno_5a;

SELECT CASE
    WHEN :terreno_5a = 0
        THEN 'FALLO - no hay ninguna propiedad de tipo T utilizable: el control no probo nada'
    WHEN NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado
                      WHERE id_agregado = :terreno_5a AND clave = 'agua_desague'
                        AND valor_texto = 'CONECTADO')
        THEN 'FALLO - no se pudo sembrar el rastro del valor vigente: el control no probo nada'
    WHEN NOT pg_temp.hay_legado_traducido_sin_linaje(:terreno_5a) THEN 'OK'
    ELSE 'FALLO - el predicado caza un valor que SI declaro alguien: prohibiria el reparto legitimo'
  END AS v_corr_vigente \gset
ROLLBACK TO SAVEPOINT correlacion_5a;

INSERT INTO resultado (prueba, veredicto) VALUES
    ('5A CONTROL un rastro de OTRO valor no declara el valor vigente', :'v_corr_otro'),
    ('5A CONTROL y el rastro del valor vigente si lo declara', :'v_corr_vigente');

-- Y la clave tiene que haber vuelto a su sitio. El control positivo la reactiva
-- para poder escribir; si el `ROLLBACK TO` no la devolviera a `activo = false`,
-- el gate habria REABIERTO la puerta que 5A cerro -- y las comprobaciones que
-- vienen despues correrian sobre un catalogo que el gate mismo altero.
SELECT pg_temp.comprobar('5A CONTROL y el savepoint devolvio servicios_disponibles a retirada',
    EXISTS (SELECT 1 FROM catalogo_atributo
             WHERE clave = 'servicios_disponibles' AND organizacion_id IS NULL
               AND NOT activo));

-- =====================================================================
-- CORTE 5 - 5B: el suelo y sus parametros urbanisticos (V85)
--
-- Lo que se comprueba aqui es lo que ningun test de Java mira con esta forma:
-- que la puerta de escritura de la base RECHAZA de verdad lo que el catalogo
-- dice que ya no aplica, y que la unica PUB que el titular autorizo es la unica
-- que hay.
-- =====================================================================

-- LAS 18, CON SU FORMA Y SU EXIGENCIA, en una sola comparacion de CONJUNTO. Se
-- mide asi y no contando 18 porque diecisiete claves y una repetida darian
-- dieciocho igual, y la unidad --lo unico que separa un `area_libre_minima` en
-- por ciento de uno en metros-- no aparece en ningun recuento.
SELECT pg_temp.comprobar('5B las 18 claves del suelo estan con su forma y su exigencia',
    (SELECT string_agg(c.clave || '=' || c.tipo_dato || coalesce(':' || c.unidad, '')
                       || '/' || t.tipo_propiedad || '=' || t.exigencia,
                       ' ' ORDER BY c.clave, t.tipo_propiedad)
       FROM catalogo_atributo c
       JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
      WHERE c.organizacion_id IS NULL AND c.activo AND c.clave IN (
            'condicion_terreno', 'situacion_registral', 'fondo', 'posicion_en_manzana',
            'topografia', 'altura_normativa_pisos', 'coeficiente_edificacion',
            'area_libre_minima', 'retiro_municipal', 'usos_compatibles',
            'certificado_parametros_vigente', 'lote_minimo_normativo', 'tipo_via_acceso',
            'estado_via', 'edificacion_existente', 'cercado', 'restriccion_arqueologica',
            'zona_de_riesgo'))
    = 'altura_normativa_pisos=ENTERO:pisos/C=OPC altura_normativa_pisos=ENTERO:pisos/T=OPC '
      'area_libre_minima=DECIMAL:%/T=OPC cercado=BOOLEANO/T=OPC '
      'certificado_parametros_vigente=BOOLEANO/T=OPC coeficiente_edificacion=DECIMAL/T=OPC '
      'condicion_terreno=LISTA/T=PUB edificacion_existente=DECIMAL:m²/T=OPC '
      'estado_via=LISTA/A=OPC estado_via=LISTA/T=OPC fondo=DECIMAL:m/C=OPC fondo=DECIMAL:m/T=OPC '
      'lote_minimo_normativo=DECIMAL:m²/T=OPC posicion_en_manzana=LISTA/C=OPC '
      'posicion_en_manzana=LISTA/T=OPC restriccion_arqueologica=LISTA/T=OPC '
      'retiro_municipal=DECIMAL:m/T=OPC situacion_registral=LISTA/C=OPC '
      'situacion_registral=LISTA/T=OPC tipo_via_acceso=LISTA/A=OPC tipo_via_acceso=LISTA/L=OPC '
      'tipo_via_acceso=LISTA/T=OPC topografia=LISTA/T=OPC usos_compatibles=TEXTO/T=OPC '
      'zona_de_riesgo=BOOLEANO/C=OPC zona_de_riesgo=BOOLEANO/T=OPC',
    'el catalogo del suelo no quedo como lo congelo el encargo');

-- Y LA COTA DE DOMINIO, QUE LA COMPROBACION DE ARRIBA NO MIRA. Va aparte a
-- proposito: aquella compara forma, unidad y exigencia --lo que decide DONDE
-- aparece la clave-- y esta compara lo que decide QUE VALOR se admite dentro.
-- Son dos preguntas distintas y separarlas hace que el rojo diga cual de las dos
-- se rompio.
--
-- No son cotas de frecuencia, son definiciones (auditoria del 2026-08-29, H2):
--
--   * `area_libre_minima` va de 0 a 100 porque se escribe EN POR CIENTO. Sin el
--     techo, un 0,30 se guarda como 0,30 % y nadie lo ve; con el, sigue
--     entrando, pero el 130 que delata la confusion al reves no.
--   * `altura_normativa_pisos` admite 0 --suelo no edificable-- y no 1: cero
--     pisos es una respuesta, no un dato ausente.
--   * `usos_compatibles` acota en 500 caracteres porque es la unica TEXTO libre
--     del corte y `valor_texto` es TEXT, que no acota nada (V71).
--
-- LAS APLICAN TRES SITIOS, Y LOS TRES LAS LEEN DE ESTA MISMA FILA: en Java,
-- `ConversionDeValores.enRango` y `enLongitud` --por las dos puertas, alta y
-- edicion-- y, detras, `exigir_atributo_gobernado`, que es la red para quien
-- entre por SQL directo (leido en `pg_proc.prosrc`: levanta `check_violation`).
-- Y ademas VIAJAN: `MotorDeCapturaImpl.conRestricciones` las manda al cliente
-- como `Restricciones`, y con las cuatro en `null` esa pregunta sale SIN
-- restricciones.
--
-- Por eso borrarlas no rompe nada visible: nadie da error, las tres guardas
-- dejan de exigir a la vez y el formulario deja de acotar. Esto es lo que
-- impide ese borrado.
--
-- EL NOMBRE DICE OCHO, Y NO DIECIOCHO, PORQUE OCHO SON LAS QUE LLEVAN COTA
-- (auditoria del 2026-08-30). Se llamaba «5B las 18 del suelo llevan la cota de
-- dominio que las define» y se leia como un censo: parecia afirmar que las 18
-- llevan cota. Medido el 2026-08-30 sobre la lista esperada de aqui abajo: 8 la
-- llevan --`altura_normativa_pisos`, `area_libre_minima`, `coeficiente_edificacion`,
-- `edificacion_existente`, `fondo`, `lote_minimo_normativo`, `retiro_municipal` y
-- `usos_compatibles`-- y las otras 10 valen `-..-/-`, que es "ninguna". La
-- comparacion sigue siendo sobre las 18 A PROPOSITO --las diez sin cota tambien
-- se vigilan: ponerle un minimo inventado a `topografia` la pondria en rojo--,
-- pero el nombre ya no promete un censo que no hace.
SELECT pg_temp.comprobar('5B ocho claves del suelo llevan cota de dominio y las otras diez ninguna',
    (SELECT string_agg(c.clave || '='
                       || coalesce(trim_scale(c.valor_minimo)::text, '-') || '..'
                       || coalesce(trim_scale(c.valor_maximo)::text, '-') || '/'
                       || coalesce(c.longitud_maxima::text, '-'), ' ' ORDER BY c.clave)
       FROM catalogo_atributo c
      WHERE c.organizacion_id IS NULL AND c.activo AND c.clave IN (
            'condicion_terreno', 'situacion_registral', 'fondo', 'posicion_en_manzana',
            'topografia', 'altura_normativa_pisos', 'coeficiente_edificacion',
            'area_libre_minima', 'retiro_municipal', 'usos_compatibles',
            'certificado_parametros_vigente', 'lote_minimo_normativo', 'tipo_via_acceso',
            'estado_via', 'edificacion_existente', 'cercado', 'restriccion_arqueologica',
            'zona_de_riesgo'))
    = (SELECT string_agg(v.clave || '=' || v.cota, ' ' ORDER BY v.clave) FROM (VALUES
        ('altura_normativa_pisos', '0..-/-'),
        ('area_libre_minima', '0..100/-'),
        ('cercado', '-..-/-'),
        ('certificado_parametros_vigente', '-..-/-'),
        ('coeficiente_edificacion', '0..-/-'),
        ('condicion_terreno', '-..-/-'),
        ('edificacion_existente', '0..-/-'),
        ('estado_via', '-..-/-'),
        ('fondo', '0..-/-'),
        ('lote_minimo_normativo', '0..-/-'),
        ('posicion_en_manzana', '-..-/-'),
        ('restriccion_arqueologica', '-..-/-'),
        ('retiro_municipal', '0..-/-'),
        ('situacion_registral', '-..-/-'),
        ('tipo_via_acceso', '-..-/-'),
        ('topografia', '-..-/-'),
        ('usos_compatibles', '-..-/500'),
        ('zona_de_riesgo', '-..-/-')
      ) AS v(clave, cota)),
    'una cota borrada o inventada no da error: deja de exigir, o exige de mas, y el catalogo sigue pareciendo entero');

-- LA EXIGENCIA, QUE ES LA DECISION QUE MUEVE EL MERCADO (D-1, D-3). Cada `PUB`
-- del catalogo del sistema la decidio el titular una por una, y por eso se
-- enumeran: una quinta seria una puerta de publicacion que nadie abrio. La
-- auditoria propone catorce mas y ninguna esta autorizada.
SELECT pg_temp.comprobar('5B el catalogo del sistema tiene CUATRO PUB, decididas una a una',
    (SELECT array_agg(c.clave || '/' || t.tipo_propiedad
                      ORDER BY c.clave || '/' || t.tipo_propiedad)
       FROM catalogo_atributo c
       JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
      WHERE c.organizacion_id IS NULL AND c.activo AND t.exigencia = 'PUB')
    = ARRAY['agua_desague/T', 'condicion_terreno/T', 'energia_electrica/T', 'tipo_acceso/L'],
    'una PUB de mas o de menos cambia quien puede publicar, y eso no lo decide un corte');

-- Y NINGUNA de las 18 es `ALT`: D-3 baja `condicion_terreno` a `PUB`
-- precisamente porque `ALT` bloquea TAMBIEN el alta, y un agente tiene que poder
-- registrar un terreno cuya condicion todavia no ha confirmado.
SELECT pg_temp.comprobar('5B ninguna clave del suelo impide REGISTRAR un terreno',
    NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                  JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
                 WHERE c.organizacion_id IS NULL AND t.exigencia = 'ALT'
                   AND c.clave IN ('condicion_terreno', 'situacion_registral', 'fondo',
                        'posicion_en_manzana', 'topografia', 'altura_normativa_pisos',
                        'coeficiente_edificacion', 'area_libre_minima', 'retiro_municipal',
                        'usos_compatibles', 'certificado_parametros_vigente',
                        'lote_minimo_normativo', 'tipo_via_acceso', 'estado_via',
                        'edificacion_existente', 'cercado', 'restriccion_arqueologica',
                        'zona_de_riesgo')));

-- LA UNIDAD ES PARTE DE LA DEFINICION, Y UN CATALOGO CON DOS GRAFIAS PARA LA
-- MISMA UNIDAD NO COMPARA NADA. `m2` y `m²` se leen igual y NO son la misma
-- cadena: un consumidor que agrupe por unidad --KAIROS lo hace-- veria dos
-- grupos donde hay uno.
--
-- LA CIFRA VA EN LA COLUMNA `nota`, NO EN EL NOMBRE NI EN UN COMENTARIO. El
-- javadoc de `SueloYParametrosUrbanisticosIntegrationTest` decia "once claves"
-- y este corte lo movio a 13 sembrando dos: una cifra escrita a mano sobre algo
-- que el propio corte mueve envejece a mentira sin que nada avise. Lo que se
-- comprueba es la INVARIANTE --cero grafias sin superindice--; lo que se
-- informa, medido en cada corrida, es cuantas la usan.
-- EL PREDICADO CUBRE EL CONCEPTO, NO UNA CADENA. La primera version comparaba
-- `unidad = 'm2'` mientras su comentario declaraba la invariante como "cero
-- grafias sin superindice": sembrando `M2`, `mt2` o `m^2` seguia VERDE. Lo que
-- se vigila es que la unidad de SUPERFICIE tenga UNA sola grafia, asi que el
-- predicado se escribe como lo que dice: cualquier unidad que se PAREZCA a
-- metros cuadrados y NO sea exactamente `m²`.
--
-- `~*` es la comparacion insensible a mayusculas de Postgres, y el patron cubre
-- las variantes que se escriben de verdad: m2, M2, mt2, m^2, m 2, mts2, m**2.
-- No pretende ser exhaustivo -- ninguna lista lo es -- pero cubre el espacio que
-- un humano teclea, y el sabotaje se valida con TRES grafias, no con una.
--
-- EL NOMBRE DICE LO QUE MIDE. Decia "ninguna clave del catalogo" y filtraba
-- `organizacion_id IS NULL`, que es el catalogo DEL SISTEMA: una clave de tenant
-- con `M2` habria pasado y el nombre habria mentido.
SELECT pg_temp.comprobar('5B la superficie tiene UNA grafia en el catalogo del sistema',
    NOT EXISTS (SELECT 1 FROM catalogo_atributo
                 WHERE organizacion_id IS NULL
                   AND unidad IS NOT NULL
                   AND unidad <> 'm²'
                   AND unidad ~* '^m[[:space:]]*(t|ts)?[[:space:]]*(\^|\*\*)?[[:space:]]*2$'),
    'dos grafias para la misma unidad parten en dos un grupo que es uno solo',
    (SELECT format('unidad de superficie: %s claves con m² y %s con otra grafia',
                   count(*) FILTER (WHERE unidad = 'm²'),
                   count(*) FILTER (WHERE unidad IS NOT NULL AND unidad <> 'm²'
                        AND unidad ~* '^m[[:space:]]*(t|ts)?[[:space:]]*(\^|\*\*)?[[:space:]]*2$'))
       FROM catalogo_atributo WHERE organizacion_id IS NULL));

-- LAS SIETE LISTA, CON SU VOCABULARIO COMPLETO. La guarda generica de 5A dice
-- que ninguna LISTA activa esta muda; esta dice ademas CUANTAS opciones tiene
-- cada una, que es lo que caza media siembra.
SELECT pg_temp.comprobar('5B las siete LISTA del suelo tienen su vocabulario entero',
    (SELECT string_agg(c.clave || '=' || (SELECT count(*) FROM catalogo_atributo_opcion o
                                           WHERE o.id_catalogo_atributo = c.id_catalogo_atributo
                                             AND o.activo),
                       ' ' ORDER BY c.clave)
       FROM catalogo_atributo c
      WHERE c.organizacion_id IS NULL AND c.activo
        AND c.clave IN ('condicion_terreno', 'situacion_registral', 'posicion_en_manzana',
                        'topografia', 'tipo_via_acceso', 'estado_via', 'restriccion_arqueologica'))
    = 'condicion_terreno=4 estado_via=3 posicion_en_manzana=5 restriccion_arqueologica=4 '
      'situacion_registral=3 tipo_via_acceso=5 topografia=5');

-- =====================================================================
-- LO QUE SE EXIGE DE **TODA** CLAVE APLICABLE A `T`, Y NO DE UNA LISTA DE 18
-- (auditoria del 2026-08-30, N34; reparado en D0)
-- =====================================================================
-- Las seis comprobaciones de arriba vigilan el suelo POR LISTA BLANCA: enumeran
-- las 18 que escribio V85 y comparan lo que hay DE ESAS 18. Eso protege bien lo
-- que protege --si una cambia de forma, de cota, de rotulo, de ayuda o de
-- vocabulario, sale roja-- pero deja un hueco medido el 2026-08-30: una clave
-- DIECINUEVE del sistema aplicable a `T` no aparece en ninguna comparacion y
-- pasaba el gate entero en verde (141 -> 142 claves del sistema, 33 -> 34
-- aplicables a `T`, y el gate seguia sin una sola roja).
--
-- NO SE CIERRA CON UN CENSO, Y ESA ES LA MITAD IMPORTANTE. Escribir aqui «las
-- aplicables a T son exactamente estas 33» pondria el gate en rojo el dia que un
-- corte posterior anada legitimamente una clave del suelo: es la enfermedad que
-- costo una tanda entera en `M2` --«el censo que se rompia al avanzar»,
-- `verificacion/evidencia/2026-08-24-el-censo-que-se-rompia-al-avanzar.md`-- y
-- que el propio 5B deshizo. Un gate que se rompe al avanzar deja de leerse.
--
-- Se cierra DERIVANDO DEL CONTRATO: hay propiedades que toda clave del catalogo
-- del sistema tiene que cumplir por ser una clave, no por ser una de las 18, y
-- eso se comprueba sobre el conjunto entero, sea de 33 o de 40. Las tres que se
-- exigen aqui son las que hoy se cumplen sin excepcion en las dos bases y cuya
-- ausencia no da error en ninguna parte:
--
--   * `rotulo` NO VACIO. La columna es NOT NULL, pero la cadena vacia entra: una
--     clave sin rotulo llega al motor de captura y el agente ve la clave desnuda.
--   * TODA OPCION ACTIVA CON SU ROTULO. Lo mismo, una capa mas abajo. La guarda
--     de 5A vigila que una LISTA no se quede muda; nada vigilaba que sus opciones
--     se puedan LEER. Medido: 0 opciones sin rotulo en las dos bases.
--   * UNA CLAVE EN POR CIENTO LLEVA SU COTA 0..100. Es la doctrina que V85
--     escribio para `area_libre_minima` --sin techo, un 0,30 se guarda como
--     0,30 % y nadie lo ve; con el, el 130 que delata la confusion al reves no
--     entra-- dicha como regla del catalogo y no como caso particular. Medido:
--     `%` es la unidad de UNA sola clave del sistema en las dos bases, y la lleva.
--
-- Y LA CIFRA VA EN LA `nota`, que es lo que hace VISIBLE el crecimiento sin
-- congelarlo: una clave anadida mueve el numero que imprime el informe. Bien
-- formada, pasa --como debe--; mal formada, cae aqui.
--
-- «APLICABLE A T» SE PREGUNTA POR LAS FILAS, QUE SON LA UNICA AUTORIDAD.
-- La primera version de esta comprobacion hacia `JOIN catalogo_atributo_tipo`
-- con `tipo_propiedad = 'T'`; la segunda anadio `aplica_todos` porque entonces
-- eran DOS las formas de aplicar, y una comprobacion que mira una sola de las
-- dos puertas repite en su predicado el mismo hueco que vino a cerrar.
--
-- Desde `V86` vuelve a ser UNA. El campo dejo de decidir --se lo quito a las dos
-- consultas del repositorio, a los dos `aplicaA` del dominio y a tres cuerpos
-- PL/pgSQL-- y se quedo como resumen, con una guarda que impide ponerlo sin sus
-- siete filas. Preguntar por las filas ya es preguntar por todo, y seguir
-- nombrando el campo aqui seria mantener viva la segunda autoridad justo en el
-- gate que la vigila.
--
-- El conjunto no cambia: las tres claves que lo llevan --`metraje_total`,
-- `estacionamientos` y `antiguedad_anios`-- tienen sus siete filas, y `V86` se
-- aseguro de que ninguna clave pudiera quedarse sin ellas.
CREATE OR REPLACE FUNCTION pg_temp.aplica_al_tipo(p_id bigint, p_tipo varchar)
RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                    WHERE t.id_catalogo_atributo = p_id
                      AND t.tipo_propiedad = p_tipo)
$$;

SELECT count(*) AS n_claves_t
  FROM catalogo_atributo c
 WHERE c.organizacion_id IS NULL AND c.activo
   AND pg_temp.aplica_al_tipo(c.id_catalogo_atributo, 'T') \gset

SELECT pg_temp.comprobar('5B toda clave del sistema aplicable a T cumple el contrato',
    NOT EXISTS (
        SELECT 1 FROM catalogo_atributo c
         WHERE c.organizacion_id IS NULL AND c.activo
           AND pg_temp.aplica_al_tipo(c.id_catalogo_atributo, 'T')
           AND (c.rotulo IS NULL OR btrim(c.rotulo) = ''
             OR (c.unidad = '%' AND (c.valor_minimo IS DISTINCT FROM 0
                                  OR c.valor_maximo IS DISTINCT FROM 100))
             OR EXISTS (SELECT 1 FROM catalogo_atributo_opcion o
                         WHERE o.id_catalogo_atributo = c.id_catalogo_atributo
                           AND o.activo
                           AND (o.rotulo IS NULL OR btrim(o.rotulo) = '')))),
    'una clave aplicable a T entro en el catalogo sin lo que se le exige a cualquiera',
    format('claves del sistema aplicables a T en esta base: %s (por sus filas por tipo, unica autoridad desde V86)', :n_claves_t));

-- =====================================================================
-- LO QUE LEE UNA PERSONA (bloque 3 de V85), que hasta la ronda 8 no lo
-- comprobaba nada -- ni la migracion ni el gate (auditoria del 2026-08-29, H1).
-- =====================================================================
-- Los pasos 1, 2, 4 y 5 de V85 llevan su asercion dentro del `DO $$` y su
-- comprobacion aqui. El paso 3 --18 rotulos, 18 ayudas, 7 rotulos de opcion
-- y 2 unidades, que son sus TRES pasadas: ver el desglose de mas abajo, en el
-- comentario de los rotulos de opcion--
-- no llevaba ninguna: un `UPDATE` que ponga «Topografia» sin
-- acento pasaba el gate entero en verde. Y no es hipotetico: el rotulo de
-- `certificado_itse/EN_TRAMITE` sigue siendo «En tramite» desde V81 --Corte 4--
-- mientras la `EN_TRAMITE` que 5B siembra en `restriccion_arqueologica` si
-- lleva tilde, y D-BASE-4 (`m2` en `area_minima_arrendable`, V77) tardo CUATRO
-- cortes en verse.
--
-- SON 18 ROTULOS, Y ESTE COMENTARIO DIJO 17 UNA RONDA ENTERA (auditoria del
-- 2026-08-30). No habia lectura que diera 17: medido el 2026-08-30 contando las
-- ternas del paso 3 de V85 (`:545-585`), son 18 `(clave, rotulo, ayuda)` sin
-- repetir, y ninguno de los recuentos vecinos vale 17 tampoco -- 18 alcanzados,
-- 12 CAMBIADOS (los otros seis nacen ya identicos en el bloque 1: `fondo`,
-- `cercado`, `retiro_municipal`, `usos_compatibles`, `altura_normativa_pisos` y
-- `zona_de_riesgo`) y 12 ACENTUADOS, que resultan ser los mismos 12. El 17 salio
-- de contar a ojo, y el propio fichero se contradecia tres lineas mas abajo, en
-- el nombre de la comprobacion y en su lista de 18 pares. Es la regla de la 8.ª
-- ronda aplicada a quien la escribio: LOS PASOS DE UNA MIGRACION SE RECORREN UNO
-- A UNO CONTRA EL FICHERO, no se cuentan de memoria; y una cifra que aparece dos
-- veces en el mismo artefacto se contrasta contra la otra antes de darla por
-- buena.
--
-- ES UNA COMPARACION DE CONJUNTO, no un recuento: contar 18 rotulos no
-- distingue «Topografia» de «Topografía», que es exactamente el defecto.
--
-- SE ESCRIBE LO ESPERADO COMO UNA LISTA DE PARES, no como una cadena pegada:
-- el rojo lo lee alguien que tiene que decidir si el rotulo cambio a proposito,
-- y para eso hay que poder ver de cual se trata.
--
-- SI, EL TEXTO VIVE DOS VECES --en V85 y aqui-- Y ES DELIBERADO. V85 esta
-- aplicada en las dos bases y es inmutable; lo que puede reescribir estos
-- rotulos es un corte POSTERIOR, y contra eso una migracion no protege: solo
-- protege algo que se ejecute en cada cierre.
SELECT pg_temp.comprobar('5B los 18 rotulos del suelo son los que lee una persona',
    (SELECT string_agg(c.clave || '=' || coalesce(c.rotulo, '(sin rotulo)'), ' ' ORDER BY c.clave)
       FROM catalogo_atributo c
      WHERE c.organizacion_id IS NULL AND c.activo AND c.clave IN (
            'condicion_terreno', 'situacion_registral', 'fondo', 'posicion_en_manzana',
            'topografia', 'altura_normativa_pisos', 'coeficiente_edificacion',
            'area_libre_minima', 'retiro_municipal', 'usos_compatibles',
            'certificado_parametros_vigente', 'lote_minimo_normativo', 'tipo_via_acceso',
            'estado_via', 'edificacion_existente', 'cercado', 'restriccion_arqueologica',
            'zona_de_riesgo'))
    = (SELECT string_agg(v.clave || '=' || v.rotulo, ' ' ORDER BY v.clave) FROM (VALUES
        ('altura_normativa_pisos', 'Altura normativa'),
        ('area_libre_minima', 'Área libre mínima'),
        ('cercado', 'Cercado o amurallado'),
        ('certificado_parametros_vigente', 'Certificado de parámetros'),
        ('coeficiente_edificacion', 'Coeficiente de edificación'),
        ('condicion_terreno', 'Condición del terreno'),
        ('edificacion_existente', 'Edificación existente'),
        ('estado_via', 'Estado de la vía'),
        ('fondo', 'Fondo'),
        ('lote_minimo_normativo', 'Lote mínimo normativo'),
        ('posicion_en_manzana', 'Posición en la manzana'),
        ('restriccion_arqueologica', 'Restricción arqueológica (CIRA)'),
        ('retiro_municipal', 'Retiro municipal'),
        ('situacion_registral', 'Situación registral'),
        ('tipo_via_acceso', 'Tipo de vía del frente'),
        ('topografia', 'Topografía'),
        ('usos_compatibles', 'Usos compatibles'),
        ('zona_de_riesgo', 'Zona de riesgo declarada')
      ) AS v(clave, rotulo)),
    'un rotulo del suelo dejo de ser el que escribio V85');

-- LAS AYUDAS, IGUAL Y POR LA MISMA RAZON. No son decoracion: tres de ellas son
-- lo unico que separa dos lecturas incompatibles del mismo campo --el por ciento
-- de `area_libre_minima`, el 0 medido de `edificacion_existente` frente al
-- blanco, y `tipo_via_acceso` frente a `via_de_acceso`--. Vaciarlas o
-- reescribirlas no rompe ninguna escritura: rompe al agente que lee el
-- formulario, y eso ningun test lo nota.
SELECT pg_temp.comprobar('5B las 18 ayudas del suelo siguen enteras y con su acento',
    (SELECT string_agg(c.clave || '=' || coalesce(c.ayuda, '(sin ayuda)'), ' ' ORDER BY c.clave)
       FROM catalogo_atributo c
      WHERE c.organizacion_id IS NULL AND c.activo AND c.clave IN (
            'condicion_terreno', 'situacion_registral', 'fondo', 'posicion_en_manzana',
            'topografia', 'altura_normativa_pisos', 'coeficiente_edificacion',
            'area_libre_minima', 'retiro_municipal', 'usos_compatibles',
            'certificado_parametros_vigente', 'lote_minimo_normativo', 'tipo_via_acceso',
            'estado_via', 'edificacion_existente', 'cercado', 'restriccion_arqueologica',
            'zona_de_riesgo'))
    = (SELECT string_agg(v.clave || '=' || v.ayuda, ' ' ORDER BY v.clave) FROM (VALUES
        ('altura_normativa_pisos',
         'Cuántos pisos permite la norma en este lote. No sale de la zonificación sola: depende también de la vía. Cero pisos es una respuesta válida en suelo no edificable; dejarlo en blanco es «no consta».'),
        ('area_libre_minima',
         'Porcentaje del lote que la norma obliga a dejar sin techar. Se escribe en por ciento: 30, no 0,30.'),
        ('cercado',
         'Si el lote tiene cerco perimétrico.'),
        ('certificado_parametros_vigente',
         'Si hay certificado de parámetros urbanísticos vigente. Distingue lo que dijo el propietario de lo que está certificado.'),
        ('coeficiente_edificacion',
         'Cuántas veces el área del lote se puede construir. Multiplicado por el área da el área vendible.'),
        ('condicion_terreno',
         'En qué situación urbanística está el suelo. No se deduce mirándolo: un lote puede parecer urbano y estar todavía en proceso de habilitación.'),
        ('edificacion_existente',
         'Metros construidos que hoy hay sobre el terreno. Declarar 0 es una medida —está vacío—; dejarlo en blanco es «no consta».'),
        ('estado_via',
         'Cómo está la superficie de esa vía. Una nave a la que se llega por trocha no la usa un tráiler.'),
        ('fondo',
         'La medida del lote en profundidad, desde el frente. 200 m² de 8 × 25 y de 20 × 10 sirven para cosas distintas.'),
        ('lote_minimo_normativo',
         'Superficie mínima que la norma admite al subdividir. Es el hecho sobre el que se pacta si el titular acepta vender fraccionado.'),
        ('posicion_en_manzana',
         'Cuántos frentes tiene el lote. «Esquina» es la posición, no el número de frentes.'),
        ('restriccion_arqueologica',
         'Si el suelo necesita CIRA y en qué estado está. No se deduce del distrito: depende del polígono. «Requerido, no iniciado» no es «no aplica».'),
        ('retiro_municipal',
         'Metros que hay que dejar libres desde el límite de propiedad. Reduce el área construible aunque el lote sea grande.'),
        ('situacion_registral',
         'Cómo está inscrito el inmueble. «En saneamiento» es un trámite abierto, distinto de no estar inscrito.'),
        ('tipo_via_acceso',
         'De qué clase es la vía a la que da el frente. No es lo mismo que «Vía principal de acceso», que dice cuál es: aquí va avenida, calle, pasaje, carretera o trocha.'),
        ('topografia',
         'El relieve del terreno respecto de la vía. Una pendiente pronunciada o un lote bajo el nivel de la vía encarecen la obra antes de empezar.'),
        ('usos_compatibles',
         'Los usos que el certificado de parámetros admite además del principal. Es una línea distinta de la zonificación en ese mismo certificado.'),
        ('zona_de_riesgo',
         'Si una autoridad declaró el suelo en zona de riesgo. Es una declaración, no una impresión del agente.')
      ) AS v(clave, ayuda)),
    'una ayuda del suelo se vacio o dejo de decir lo que V85 escribio');

-- LOS ROTULOS DE OPCION, LAS 29 Y NO SOLO LAS SIETE QUE V85 ACENTUA. La razon
-- es el modo de fallo del propio `UPDATE`: casa por `(clave, valor)`, asi que un
-- `valor` mal tecleado --`BAJO_NIVEL_VIA` por `BAJO_NIVEL_DE_VIA`-- afecta CERO
-- filas, no da error, y deja esa opcion con el rotulo sin acento que la sembro
-- el bloque 1. Comprobar solo las siete acentuadas no lo cazaria si el fallo es
-- justamente que una de ellas no se acentuo; comparar las 29 si, porque la que
-- se quedo atras aparece en el conjunto con su grafia vieja.
--
-- LAS TRES PASADAS, ENUMERADAS ENTERAS (correccion del 2026-08-30). Aqui ponia
-- "las tres pasadas del bloque 3 ... 18 rotulos, 18 ayudas y 7 opciones": dice
-- TRES y enumera DOS, porque 18 y 18 son las filas de una MISMA pasada contadas
-- por columna. Recontado contra el fuente de `V85`, las tres son:
--
--   1. `V85:545-585`  UPDATE catalogo_atributo SET rotulo, ayuda  -> 18 filas
--   2. `V85:587-602`  UPDATE catalogo_atributo_opcion SET rotulo  ->  7 filas
--   3. `V85:608-611`  UPDATE catalogo_atributo SET unidad = 'm²'  ->  2 filas
--                     (`edificacion_existente` y `lote_minimo_normativo`)
--
-- Ninguna a cero, comparando el fuente de V85 contra las dos bases.
--
-- LA TERCERA NO SE QUEDABA SIN VIGILANCIA: lo que faltaba era la enumeracion,
-- no la guarda. La unidad de esas dos claves viaja dentro de la comprobacion
-- «5B las 18 claves del suelo estan con su forma y su exigencia», que compara
-- `tipo_dato:unidad` y cuyo esperado lleva `edificacion_existente=DECIMAL:m²`
-- y `lote_minimo_normativo=DECIMAL:m²`; y el conjunto entero lo cuenta
-- «5B la superficie tiene UNA grafia en el catalogo del sistema». Por eso esta
-- correccion es de texto y no toca ningun SELECT.
SELECT pg_temp.comprobar('5B las 29 opciones del suelo se leen como las escribio el corte',
    (SELECT string_agg(c.clave || '/' || o.valor || '=' || o.rotulo, ' '
                       ORDER BY c.clave, o.valor)
       FROM catalogo_atributo c
       JOIN catalogo_atributo_opcion o ON o.id_catalogo_atributo = c.id_catalogo_atributo
      WHERE c.organizacion_id IS NULL AND c.activo AND o.activo
        AND c.clave IN ('condicion_terreno', 'situacion_registral', 'posicion_en_manzana',
                        'topografia', 'tipo_via_acceso', 'estado_via', 'restriccion_arqueologica'))
    = (SELECT string_agg(v.clave || '/' || v.valor || '=' || v.rotulo, ' '
                         ORDER BY v.clave, v.valor) FROM (VALUES
        ('condicion_terreno', 'URBANO_HABILITADO', 'Urbano habilitado'),
        ('condicion_terreno', 'EN_PROCESO_DE_HABILITACION', 'En proceso de habilitación'),
        ('condicion_terreno', 'RUSTICO_ERIAZO', 'Rústico o eriazo'),
        ('condicion_terreno', 'ZONA_INFORMAL_SIN_HABILITAR', 'Zona informal sin habilitar'),
        ('estado_via', 'ASFALTADA', 'Asfaltada'),
        ('estado_via', 'AFIRMADA', 'Afirmada'),
        ('estado_via', 'SIN_AFIRMAR', 'Sin afirmar'),
        ('posicion_en_manzana', 'UN_FRENTE', 'Un frente'),
        ('posicion_en_manzana', 'DOS_FRENTES', 'Dos frentes'),
        ('posicion_en_manzana', 'TRES_FRENTES', 'Tres frentes'),
        ('posicion_en_manzana', 'CUATRO_FRENTES', 'Cuatro frentes'),
        ('posicion_en_manzana', 'ESQUINA', 'Esquina'),
        ('restriccion_arqueologica', 'NO_APLICA', 'No aplica'),
        ('restriccion_arqueologica', 'CIRA_OBTENIDO', 'CIRA obtenido'),
        ('restriccion_arqueologica', 'EN_TRAMITE', 'En trámite'),
        ('restriccion_arqueologica', 'REQUERIDO_NO_INICIADO', 'Requerido, no iniciado'),
        ('situacion_registral', 'INSCRITO_EN_SUNARP', 'Inscrito en SUNARP'),
        ('situacion_registral', 'EN_SANEAMIENTO', 'En saneamiento'),
        ('situacion_registral', 'NO_INSCRITO_SOLO_POSESION', 'No inscrito, sólo posesión'),
        ('tipo_via_acceso', 'AVENIDA', 'Avenida'),
        ('tipo_via_acceso', 'CALLE_O_JIRON', 'Calle o jirón'),
        ('tipo_via_acceso', 'PASAJE', 'Pasaje'),
        ('tipo_via_acceso', 'CARRETERA', 'Carretera'),
        ('tipo_via_acceso', 'TROCHA_O_SIN_VIA', 'Trocha o sin vía'),
        ('topografia', 'PLANO', 'Plano'),
        ('topografia', 'PENDIENTE_LEVE', 'Pendiente leve'),
        ('topografia', 'PENDIENTE_PRONUNCIADA', 'Pendiente pronunciada'),
        ('topografia', 'BAJO_NIVEL_DE_VIA', 'Bajo el nivel de la vía'),
        ('topografia', 'ACCIDENTADO', 'Accidentado')
      ) AS v(clave, valor, rotulo)),
    'una opcion del suelo se lee distinto de como la escribio V85');

-- D-7, LAS DOS MITADES. Ninguna sirve sin la otra: retirarla de los tres tipos
-- perderia la superficie del lote de una casa, y no retirarla de ninguno dejaria
-- dos claves para la misma verdad en un terreno.
SELECT pg_temp.comprobar('5B area_terreno conserva A y C, y perdio T',
    (SELECT array_agg(t.tipo_propiedad ORDER BY t.tipo_propiedad)
       FROM catalogo_atributo c
       JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
      WHERE c.clave = 'area_terreno' AND c.organizacion_id IS NULL)
    = ARRAY['A','C']::varchar[],
    'metraje_total es la superficie canonica de un TERRENO, y de una CASA no');

SELECT pg_temp.comprobar('5B y la CLAVE area_terreno sigue viva: se retiro su aplicabilidad',
    EXISTS (SELECT 1 FROM catalogo_atributo
             WHERE clave = 'area_terreno' AND organizacion_id IS NULL
               AND activo AND del_sistema AND tipo_dato = 'DECIMAL'));

-- LA PUERTA, PROBADA INTENTANDO ROMPERLA, que es para lo que existe este gate.
-- El catalogo puede decir lo que quiera: lo que importa es si la BASE lo hace
-- cumplir. `exigir_atributo_gobernado` rechaza una clave que no aplica al tipo
-- con `check_violation`, y aqui se comprueba escribiendo de verdad.
--
-- Se necesita un TERRENO y una CASA reales. Si no hubiera de alguno, la prueba
-- lo dice en vez de salir verde: `pg_temp.rechaza` distingue "cero filas
-- tocadas" de "lo acepto", y el `SELECT` de abajo distingue "no hay sujeto".
SELECT COALESCE((SELECT min(id_propiedad) FROM propiedad WHERE tipo_inmueble = 'T'), 0) AS terreno_5b \gset
SELECT COALESCE((SELECT min(id_propiedad) FROM propiedad WHERE tipo_inmueble = 'C'), 0) AS casa_5b \gset

SELECT pg_temp.comprobar('5B CONTROL hay un TERRENO y una CASA con los que probar la puerta',
    :terreno_5b <> 0 AND :casa_5b <> 0,
    'sin los dos sujetos las dos pruebas de abajo saldrian verdes sin escribir nada');

-- El estado del dato ANTES de tocarlo, para poder afirmar despues que el
-- savepoint lo devolvio. Se captura con `\gset` --que vive en el cliente-- y no
-- en una TEMP, que el `ROLLBACK TO` se llevaria junto con lo que mide.
--
-- SE COMPARA EL VALOR, NO EL RECUENTO (auditoria del 2026-08-30, N35; reparado
-- en D0). Esto contaba `count(*)` antes y despues, asi que un rollback que NO
-- repusiera pero dejara el mismo numero de filas con OTRO valor pasaba en verde:
-- el bloque de aqui abajo borra el `area_terreno` de los dos sujetos y escribe
-- 300 sobre la CASA, de modo que sustituir el `ROLLBACK TO SAVEPOINT` por un
-- `RELEASE` --que es el defecto exacto que esta comprobacion protege-- dejaba
-- «1 filas antes, 1 despues» y el 250 de la cartera convertido en 300. Medido el
-- 2026-08-30 sobre `controllocal_repositorios`, donde ese sujeto existe.
--
-- La huella es `id=valor` por sujeto, ordenada, asi que un valor cambiado, un
-- sujeto perdido y un sujeto ganado se ven los tres. El mecanismo no cambia --la
-- misma pareja de `\gset`, que sigue sobreviviendo al rollback--; cambia lo que
-- se compara.
CREATE OR REPLACE FUNCTION pg_temp.huella_area_5b(p_terreno bigint, p_casa bigint)
RETURNS text LANGUAGE sql AS $$
    SELECT COALESCE(
        (SELECT string_agg(a.id_propiedad || '='
                           || COALESCE(trim_scale(a.valor_numero)::text, '(sin valor)'),
                           ' ' ORDER BY a.id_propiedad)
           FROM atributo_propiedad a
          WHERE a.id_propiedad IN (p_terreno, p_casa) AND a.clave = 'area_terreno'),
        '(ninguna fila)')
$$;

SELECT pg_temp.huella_area_5b(:terreno_5b, :casa_5b) AS area_5b_antes \gset

-- LOS DOS VEREDICTOS SE CAPTURAN CON `\gset` Y SE ANOTAN DESPUES DEL ROLLBACK.
-- `pg_temp.debe_rechazar` no sirve aqui: escribe su fila en `resultado`, que es
-- una TEMP de esta misma transaccion, y el `ROLLBACK TO SAVEPOINT` se la
-- llevaria por delante -- la prueba desapareceria del informe sin que nada lo
-- dijera. `\gset` vive en el cliente y sobrevive al rollback. Es la misma
-- leccion que la cabecera de este fichero deja escrita para el bloque 5A.
--
-- Y NO BASTA CON QUE FALLE: el `DELETE` previo existe porque
-- `uq_atributo_propiedad_clave` deja UNA fila por (propiedad, clave), asi que
-- sobre un terreno que ya tuviera un `area_terreno` conservado --los que V85
-- declara DISCREPANTES-- el INSERT fallaria por el UNIQUE y esta prueba saldria
-- verde sin haber tocado la invariante. Se comprueba ademas el SQLSTATE, y son
-- DOS codigos distintos: la aplicabilidad rechaza con `check_violation`
-- (23514), que es el unico que vale como prueba; el UNIQUE habria rechazado con
-- `unique_violation` (23505), y ese fallo NO demuestra nada. Por eso no basta
-- con exigir que el INSERT falle: hay que exigir CON QUE codigo falla.
SAVEPOINT area_5b;
DELETE FROM atributo_propiedad
 WHERE id_propiedad IN (:terreno_5b, :casa_5b) AND clave = 'area_terreno';

DO $area$
DECLARE estado text; mensaje text;
BEGIN
    BEGIN
        INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero)
        SELECT organizacion_id, id_propiedad, 'area_terreno', 500
          FROM propiedad WHERE id_propiedad = (SELECT min(id_propiedad) FROM propiedad
                                                WHERE tipo_inmueble = 'T');
        CREATE TEMP TABLE v5b_terreno AS SELECT 'FALLO - lo acepto'::text AS v;
        RETURN;
    EXCEPTION WHEN others THEN
        GET STACKED DIAGNOSTICS estado = RETURNED_SQLSTATE, mensaje = MESSAGE_TEXT;
    END;
    CREATE TEMP TABLE v5b_terreno AS
    SELECT CASE WHEN estado = '23514' AND mensaje LIKE '%no aplica%' THEN 'OK'
                ELSE 'FALLO - rechazado por ' || estado || ': ' || mensaje END AS v;
END $area$;

SELECT v AS v_area_terreno FROM v5b_terreno \gset
DROP TABLE v5b_terreno;

-- Y LA MITAD SIMETRICA, que es la que impide "arreglar" D-7 de mas: sobre una
-- CASA la misma escritura tiene que ENTRAR. Sin ella, retirar la clave de los
-- tres tipos pasaria este gate entero.
--
-- VA DENTRO DE UN `EXCEPTION WHEN others` COMO SU GEMELA, y no lo estaba
-- (auditoria del 2026-08-29, H6). La asimetria era esta: la mitad que espera
-- RECHAZO capturaba el error y lo convertia en veredicto; la que espera
-- ACEPTACION lo dejaba salir. Con `ON_ERROR_STOP` eso mata el script ANTES del
-- informe, asi que el sabotaje "retirar tambien area_terreno/C" ponia el cierre
-- en rojo --correcto-- pero sin imprimir una sola de las comprobaciones: el
-- diagnostico no decia QUE se habia roto. Un gate que se cae no informa; uno que
-- anota FALLO, si.
DO $casa$
DECLARE estado text; mensaje text; casa bigint;
BEGIN
    SELECT min(id_propiedad) INTO casa FROM propiedad WHERE tipo_inmueble = 'C';
    BEGIN
        INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero)
        SELECT organizacion_id, id_propiedad, 'area_terreno', 300
          FROM propiedad WHERE id_propiedad = casa;
    EXCEPTION WHEN others THEN
        GET STACKED DIAGNOSTICS estado = RETURNED_SQLSTATE, mensaje = MESSAGE_TEXT;
        CREATE TEMP TABLE v5b_casa AS
        SELECT ('FALLO - la rechazo con ' || estado || ': ' || mensaje)::text AS v;
        RETURN;
    END;
    CREATE TEMP TABLE v5b_casa AS
    SELECT CASE WHEN EXISTS (SELECT 1 FROM atributo_propiedad
                              WHERE id_propiedad = casa AND clave = 'area_terreno'
                                AND valor_numero = 300) THEN 'OK'
                ELSE 'FALLO - no la rechazo, pero tampoco la escribio' END::text AS v;
END $casa$;

SELECT v AS v_area_casa FROM v5b_casa \gset
DROP TABLE v5b_casa;
ROLLBACK TO SAVEPOINT area_5b;

INSERT INTO resultado (prueba, veredicto) VALUES
    ('5B la base RECHAZA area_terreno sobre un TERRENO', :'v_area_terreno'),
    ('5B y la ACEPTA sobre una CASA, donde no duplica ninguna verdad', :'v_area_casa');

-- Y EL `ROLLBACK TO` DEVOLVIO EL DATO A SU SITIO. Este bloque BORRA valores de
-- la cartera real para poder escribir, y el gate corre contra
-- `controllocal_dev`: si el savepoint no los repusiera, la comprobacion habria
-- destruido lo que dice vigilar. La cifra de antes viene del `\gset`, que
-- sobrevive al rollback; compararla con una TEMP no serviria, porque la TEMP se
-- iria con el.
--
-- Y EL DESPUES SE MIDE, NO SE SUPONE (auditoria del 2026-08-30). La `nota` decia
-- «%s filas antes y despues» con la cifra del ANTES puesta dos veces: en verde
-- era cierta por casualidad --si el predicado pasa, las dos cifras coinciden--
-- pero en el unico caso para el que existe esta comprobacion, el rollback que no
-- repone, la nota habria seguido afirmando que el despues era igual al antes. Se
-- captura el DESPUES en su propio `\gset`, se comparan las dos huellas y la nota
-- imprime LAS DOS. Ambas salen de una consulta a la tabla, no de una hipotesis.
SELECT pg_temp.huella_area_5b(:terreno_5b, :casa_5b) AS area_5b_despues \gset

SELECT pg_temp.comprobar('5B CONTROL el savepoint repuso el area_terreno que borro',
    :'area_5b_despues' = :'area_5b_antes',
    'el gate borro valores de la cartera y no los devolvio',
    format('area_terreno de los dos sujetos: [%s] antes, [%s] despues',
           :'area_5b_antes', :'area_5b_despues'));

-- EL CONTROL DE ESA COMPROBACION, porque su universo es CERO en la base del
-- cierre (auditoria del 2026-08-30, N32; reparado en D0).
--
-- Medido el 2026-08-30: `controllocal_dev` no tiene ninguna fila de
-- `area_terreno` sobre los dos sujetos que esta comprobacion elige, asi que alli
-- las dos huellas valen «(ninguna fila)» y coinciden pase lo que pase. Un verde
-- sobre un universo vacio no es una medicion, y por eso la comprobacion viaja
-- con un control que se FABRICA el universo: escribe un `area_terreno` sobre la
-- CASA --donde la clave sigue aplicando, asi que no hay ninguna puerta que
-- forzar--, toma la huella, cambia el VALOR sin tocar el numero de filas y exige
-- que la huella lo note. Es el defecto exacto que `N35` describio: mismo
-- recuento, otro valor.
--
-- Se comprueban las dos mitades: que el recuento NO habria visto nada --si lo
-- viera, esta comprobacion no probaria lo que dice-- y que la huella SI.
SAVEPOINT huella_5b;
DELETE FROM atributo_propiedad WHERE id_propiedad = :casa_5b AND clave = 'area_terreno';
INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero)
SELECT organizacion_id, id_propiedad, 'area_terreno', 111
  FROM propiedad WHERE id_propiedad = :casa_5b;

DO $huella$
DECLARE huella_a text; huella_b text; filas_a bigint; filas_b bigint; casa bigint;
BEGIN
    SELECT min(id_propiedad) INTO casa FROM propiedad WHERE tipo_inmueble = 'C';

    huella_a := pg_temp.huella_area_5b(casa, casa);
    SELECT count(*) INTO filas_a FROM atributo_propiedad
     WHERE id_propiedad = casa AND clave = 'area_terreno';

    UPDATE atributo_propiedad SET valor_numero = 222
     WHERE id_propiedad = casa AND clave = 'area_terreno';

    huella_b := pg_temp.huella_area_5b(casa, casa);
    SELECT count(*) INTO filas_b FROM atributo_propiedad
     WHERE id_propiedad = casa AND clave = 'area_terreno';

    CREATE TEMP TABLE v5b_huella AS
    SELECT CASE
        WHEN filas_a <> 1 THEN 'FALLO - no se pudo sembrar el area_terreno: el control no probo nada'
        WHEN filas_a <> filas_b
            THEN 'FALLO - el caso no reproduce el defecto: el recuento tambien cambio'
        WHEN huella_a <> huella_b THEN 'OK'
        ELSE 'FALLO - la huella no distingue dos valores distintos: compara lo mismo que un count'
      END::text AS v;
END $huella$;

SELECT v AS v_huella_5b FROM v5b_huella \gset
DROP TABLE v5b_huella;
ROLLBACK TO SAVEPOINT huella_5b;

INSERT INTO resultado (prueba, veredicto)
VALUES ('5B CONTROL la huella del savepoint distingue valores, no recuentos', :'v_huella_5b');

-- LA INVARIANTE DEL DATO DE D-7, Y POR QUE NO ES "CERO FILAS".
--
-- V85 no borra todo `area_terreno` de los terrenos: retira SOLO lo que COINCIDIA
-- con `metraje_total` --ahi el dato esta entero en su columna canonica y no se
-- pierde nada-- y CONSERVA lo que discrepaba, porque no se sabe cual de las dos
-- superficies es la correcta y elegir una seria inventar. Asi que "0 filas" NO
-- es la invariante: la invariante es que **ninguna fila superviviente repite el
-- metraje**, porque las que lo repetian se fueron y la puerta esta cerrada para
-- las nuevas.
--
-- SU UNIVERSO ES CERO EN UNA BASE NUEVA, y se dice en vez de disimularlo:
-- ninguna migracion escribe valores de `area_terreno` --medido, solo V48 la
-- define-- y desde 5B ninguna puerta la admite sobre un terreno, asi que sobre
-- una base recien creada esto sale verde sin mirar nada. Por eso viaja con su
-- CONTROL POSITIVO: se siembra el par exacto que el predicado tiene que cazar y
-- se exige que LO ENCUENTRE. El tamano real del universo se declara en la
-- columna `nota`, para que un (0 filas, verde) quede dicho y no pase por
-- medicion.
CREATE OR REPLACE FUNCTION pg_temp.hay_area_que_repite_el_metraje()
RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (
        SELECT 1 FROM atributo_propiedad a
          JOIN propiedad p ON p.id_propiedad = a.id_propiedad
         WHERE a.clave = 'area_terreno' AND p.tipo_inmueble = 'T'
           AND a.valor_numero = p.metraje)
$$;

SELECT count(*) AS n_area_5b FROM atributo_propiedad a
  JOIN propiedad p ON p.id_propiedad = a.id_propiedad
 WHERE a.clave = 'area_terreno' AND p.tipo_inmueble = 'T' \gset

SELECT pg_temp.comprobar('5B ningun area_terreno de un TERRENO repite su metraje canonico',
    NOT pg_temp.hay_area_que_repite_el_metraje(),
    'lo que coincidia se retiro en V85; lo que sobrevive discrepa y se conserva a proposito',
    format('area_terreno sobre terrenos en esta base: %s filas', :n_area_5b));

-- EL CONTROL POSITIVO. Se escribe saltandose la puerta --hay que reabrir la
-- aplicabilidad para poder escribir, todo dentro del savepoint-- y eso NO es
-- una licencia: es la prueba de que la puerta esta puesta. El `ROLLBACK TO` la
-- deja como estaba, y la comprobacion de despues lo verifica.
--
-- LA SIEMBRA VA DENTRO DE UN `EXCEPTION WHEN others` (auditoria del 2026-08-29,
-- H6). Reabrir la aplicabilidad es un `INSERT` en `catalogo_atributo_tipo`, y si
-- la fila `area_terreno/T` YA existe --que es exactamente el sabotaje que
-- deshace D-7-- choca contra la clave primaria. Sin capturarlo, `ON_ERROR_STOP`
-- mataba el script en esta linea: el cierre veia rojo, pero ninguna de las
-- comprobaciones llegaba a imprimirse y el informe no decia cual habia caido.
-- Capturado, el control anota FALLO diciendo POR QUE no pudo sembrar, y las
-- demas filas --incluidas las dos que el sabotaje pone en rojo de verdad-- se
-- imprimen.
SAVEPOINT repite_5b;
DO $repite$
DECLARE estado text; mensaje text; terreno bigint;
BEGIN
    SELECT min(id_propiedad) INTO terreno FROM propiedad WHERE tipo_inmueble = 'T';
    IF terreno IS NULL THEN
        CREATE TEMP TABLE v5b_repite AS
        SELECT 'FALLO - no hay ningun TERRENO utilizable: el control no probo nada'::text AS v;
        RETURN;
    END IF;
    BEGIN
        INSERT INTO catalogo_atributo_tipo (id_catalogo_atributo, tipo_propiedad,
                                            requerido, exigencia)
        SELECT id_catalogo_atributo, 'T', false, 'OPC' FROM catalogo_atributo
         WHERE clave = 'area_terreno' AND organizacion_id IS NULL;
        DELETE FROM atributo_propiedad WHERE id_propiedad = terreno AND clave = 'area_terreno';
        INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero)
        SELECT organizacion_id, id_propiedad, 'area_terreno', metraje
          FROM propiedad WHERE id_propiedad = terreno;
    EXCEPTION WHEN others THEN
        GET STACKED DIAGNOSTICS estado = RETURNED_SQLSTATE, mensaje = MESSAGE_TEXT;
        CREATE TEMP TABLE v5b_repite AS
        SELECT ('FALLO - no se pudo sembrar el duplicado (' || estado || '): ' || mensaje)::text AS v;
        RETURN;
    END;
    CREATE TEMP TABLE v5b_repite AS
    SELECT CASE
        WHEN NOT EXISTS (SELECT 1 FROM atributo_propiedad
                          WHERE id_propiedad = terreno AND clave = 'area_terreno')
            THEN 'FALLO - no se pudo sembrar el duplicado: el control no probo nada'
        WHEN pg_temp.hay_area_que_repite_el_metraje() THEN 'OK'
        ELSE 'FALLO - el predicado no caza un area_terreno que repite el metraje'
      END::text AS v;
END $repite$;

SELECT v AS v_repite FROM v5b_repite \gset
DROP TABLE v5b_repite;
ROLLBACK TO SAVEPOINT repite_5b;

INSERT INTO resultado (prueba, veredicto)
VALUES ('5B CONTROL el predicado caza un area_terreno que repite el metraje', :'v_repite');

SELECT pg_temp.comprobar('5B CONTROL y el savepoint volvio a cerrar la puerta de T',
    NOT EXISTS (SELECT 1 FROM catalogo_atributo c
                  JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = c.id_catalogo_atributo
                 WHERE c.clave = 'area_terreno' AND c.organizacion_id IS NULL
                   AND t.tipo_propiedad = 'T'),
    'la puerta de area_terreno/T quedo abierta: o nunca se cerro, o el rollback del control no la deshizo');

-- EL PAR `lote_minimo_normativo` / `acepta_venta_fraccionada`, en las dos
-- direcciones. Es la guarda 2.2 de V78, y la razon por la que esta clave estaba
-- esperando a 5B: la condicion se pacta desde V77 y el hecho no existia, asi que
-- el pacto era el unico sitio donde cabia -- y un pacto muere con su encargo.
SELECT pg_temp.comprobar('5B el lote minimo llega donde se pacta vender fraccionado',
    NOT EXISTS (
        SELECT 1
          FROM catalogo_atributo cond
          JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = cond.id_catalogo_atributo
          JOIN catalogo_atributo hecho ON hecho.clave = 'lote_minimo_normativo' AND hecho.activo
                                      AND hecho.organizacion_id IS NULL
         WHERE cond.clave = 'acepta_venta_fraccionada' AND cond.activo
           AND cond.organizacion_id IS NULL
           AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                            WHERE t.id_catalogo_atributo = hecho.id_catalogo_atributo
                              AND t.tipo_propiedad = o.tipo_propiedad)));

SELECT pg_temp.comprobar('5B y ese par esta cubierto en algun tipo, no en cero',
    (SELECT count(DISTINCT o.tipo_propiedad) >= 1
       FROM catalogo_atributo cond
       JOIN catalogo_atributo_operacion o ON o.id_catalogo_atributo = cond.id_catalogo_atributo
       JOIN catalogo_atributo hecho ON hecho.clave = 'lote_minimo_normativo' AND hecho.activo
                                   AND hecho.organizacion_id IS NULL
       JOIN catalogo_atributo_tipo t ON t.id_catalogo_atributo = hecho.id_catalogo_atributo
                                    AND t.tipo_propiedad = o.tipo_propiedad
      WHERE cond.clave = 'acepta_venta_fraccionada' AND cond.activo
        AND cond.organizacion_id IS NULL));

-- LAS TRES CLAVES DE LA VIA CONVIVEN Y VAN CONTIGUAS. `via_de_acceso` dice CUAL
-- es la via, `tipo_via_acceso` de que CLASE es y `estado_via` como esta: no es
-- la duplicidad de `area_terreno`/`metraje_total`, donde las dos claves
-- nombraban el mismo numero. Separarlas en el `orden` las pondria en dos
-- pantallas y un agente rellenaria una creyendo que es la otra.
SELECT pg_temp.comprobar('5B las tres claves de la via viven juntas y ninguna sustituye a otra',
    (SELECT array_agg(clave ORDER BY orden) FROM catalogo_atributo
      WHERE organizacion_id IS NULL AND activo
        AND clave IN ('via_de_acceso', 'tipo_via_acceso', 'estado_via'))
    = ARRAY['via_de_acceso', 'tipo_via_acceso', 'estado_via']::varchar[]
    AND (SELECT max(orden) - min(orden) FROM catalogo_atributo
          WHERE organizacion_id IS NULL
            AND clave IN ('via_de_acceso', 'tipo_via_acceso', 'estado_via')) <= 10);

-- =====================================================================
-- Lo que NO se ha roto
-- =====================================================================
SELECT pg_temp.comprobar('SIN ROMPER las columnas del cable siguen existiendo',
    (SELECT count(*) = 4 FROM information_schema.columns
      WHERE table_name = 'propiedad'
        AND column_name IN ('precio_referencial', 'moneda_referencial', 'id_rol_propietario', 'disponibilidad_comercial')));

-- V71 retiro la tabla espejo. La comprobacion cambia de sentido y con eso gana:
-- ya no vigila que el espejo siga ahi, vigila que al retirarlo no se perdiera
-- ningun rubro. Los 21 que tenia la cartera tienen que estar, ahora como valor
-- gobernado.
--
-- Las dos cifras son SUELOS, no censos (corregido en V76). Escritas como
-- `= 21` median el tamano de la cartera y no la invariante: en cuanto alguien
-- registraba una propiedad -- que es el uso normal del sistema -- el gate se
-- ponia rojo sin que nada se hubiera roto. Un gate que se rompe al usar el
-- producto deja de leerse, y ese es el modo de fallo que importa.
SELECT pg_temp.comprobar('SIN ROMPER detalle_local_comercial ya no existe',
    (SELECT count(*) = 0 FROM information_schema.tables
      WHERE table_name = 'detalle_local_comercial'));

SELECT pg_temp.comprobar('SIN ROMPER los 21 rubros sobrevivieron a la retirada',
    (SELECT count(*) >= 21 FROM atributo_propiedad WHERE clave = 'rubro_permitido'));

SELECT pg_temp.comprobar('SIN ROMPER la busqueda por rubro conserva su indice',
    (SELECT count(*) = 1 FROM pg_indexes
      WHERE indexname = 'ix_atributo_rubro_trgm'));

SELECT pg_temp.comprobar('SIN ROMPER no se perdio ninguna propiedad',
    (SELECT count(*) >= 21 FROM propiedad));

-- =====================================================================
-- El informe tiene que caber, o el informe miente
-- =====================================================================
-- LAS DOS VAN LAS ULTIMAS a proposito: miran las filas ya escritas, asi que una
-- comprobacion nueva va ARRIBA de ellas. Existen porque durante 5A siete nombres
-- salieron cortados sin que nada lo dijera, y uno de ellos perdia justo la cifra
-- que declaraba su propio universo (auditoria del 2026-08-25, N10).
--
-- La primera: el universo del control del legado tiene que LLEGAR al informe.
-- Que el nombre no se corte no basta si la cifra no viaja. Se apoya en el nombre
-- del control del legado a proposito -- si alguien lo cambia sale ROJA, que es el sentido
-- correcto del error: nunca verde por no encontrar lo que buscaba.
SELECT pg_temp.comprobar('INFORME el control del legado imprime su universo',
    EXISTS (SELECT 1 FROM resultado
             WHERE prueba LIKE '5A CONTROL el predicado del legado%' AND nota IS NOT NULL),
    'el control del legado no dejo dicho cuantas filas de legado hay en esta base');

-- LA SEGUNDA: LOS NOMBRES QUE OTROS ARTEFACTOS CITAN (D0, 2026-08-30, N31).
--
-- Una migracion APLICADA no se edita, asi que la cabecera de `V85` es un
-- SNAPSHOT FECHADO: lo que dijo el 2026-08-29 se queda escrito tal cual, aunque
-- deje de ser cierto. Eso obliga a una regla, y esta comprobacion es su mitad
-- ejecutable: NINGUNA VERDAD VIVA PUEDE DEPENDER DE UNA CABECERA. Lo que hay que
-- poder seguir --una guarda, una cifra-- vive aqui, donde se ejecuta en cada
-- cierre; en la cabecera vive solo el porque, que no caduca.
--
-- `V85` cito su guarda por NUMERO DE LINEA («gate:883») y el puntero murio en
-- cuanto el fichero crecio: el 2026-08-29 esa linea era el cierre de un predicado
-- que no tiene nada que ver. Citar por NOMBRE tampoco basta por si solo --un
-- corte puede renombrar la guarda-- asi que el nombre se ancla: los que otros
-- artefactos citan se enumeran aqui y renombrarlos sale ROJO, que es el sentido
-- correcto del error. Nunca verde por no encontrar lo que buscaba.
SELECT pg_temp.comprobar('INFORME las guardas que otros artefactos citan siguen ahi',
    (SELECT count(*) FROM resultado WHERE prueba IN (
        '5A requerido sigue siendo espejo exacto de exigencia = ALT',
        '5A ningun inmueble con legado recibio un servicio sin que nadie lo afirmara',
        '5A CONTROL el predicado del legado caza una traduccion sin linaje',
        '4P despues del cutover ningun hecho del inmueble sin linaje',
        '4P despues del cutover ninguna columna estructural sin linaje',
        '5B CONTROL y el savepoint volvio a cerrar la puerta de T')) = 6,
    'una guarda que una migracion aplicada o un javadoc cita por su nombre cambio de nombre');

-- La tercera: la alineacion. El informe ya no puede cortar a nadie --pasa de
-- `rpad`, que TRUNCA, a rellenar sin cortar--, asi que un nombre largo saldria
-- desalineado, nunca mutilado; esta comprobacion evita incluso eso, y comparte
-- la cifra del ancho con el informe para que no puedan separarse.
SELECT pg_temp.comprobar('INFORME ningun nombre de comprobacion se sale del ancho',
    NOT EXISTS (SELECT 1 FROM resultado WHERE length(prueba) > :ANCHO_PRUEBA - 2),
    'un nombre no cabe en el ancho del informe: acortalo o pasa la cifra a la columna nota',
    format('el mas largo mide %s de %s', (SELECT max(length(prueba)) FROM resultado), :ANCHO_PRUEBA - 2));

-- =====================================================================
-- Veredicto
-- =====================================================================
-- Se recupera la salida: a partir de aqui si queremos ver lo que sale.
\o
\echo ''
-- Se rellena sin `rpad` A PROPOSITO: `rpad` corta lo que no cabe y asi es como
-- el gate estuvo afirmando cosas que no imprimia. Un nombre mas largo que el
-- ancho ahora desalinea la fila --se ve-- en vez de perder su cola en silencio.
SELECT lpad(n::text, 3) || '  ' || prueba
       || repeat(' ', greatest(2, :ANCHO_PRUEBA - length(prueba)))
       || veredicto || COALESCE('   ' || nota, '') AS "GATE DEL MODELO UNIVERSAL"
  FROM resultado ORDER BY n;

\echo ''
SELECT count(*) FILTER (WHERE veredicto = 'OK')   AS "en verde",
       count(*) FILTER (WHERE veredicto <> 'OK')  AS "en rojo",
       count(*)                                   AS total
  FROM resultado;

DO $$
DECLARE fallos bigint;
BEGIN
    SELECT count(*) INTO fallos FROM resultado WHERE veredicto <> 'OK';
    IF fallos > 0 THEN
        RAISE EXCEPTION 'GATE EN ROJO: % comprobaciones fallaron', fallos;
    END IF;
END $$;

ROLLBACK;
