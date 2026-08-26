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
-- POR QUE NO SE ARREGLA ESCRIBIENDO `= 81`. El argumento original era cierto
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
-- ponlo activo = false" -- y esa NO baja el `count(*)`: se podrian desactivar las
-- 81 y un suelo sin filtro seguiria en verde.
--
-- LIMITE HONESTO, dicho y no escondido: con 81 claves sembradas, un suelo de 51
-- tolera 30 retiradas antes de ponerse rojo. No se sube corte a corte porque eso
-- reintroduce exactamente el censo que esta enmienda viene a quitar. Quien
-- vigila la salud de cada clave es la comprobacion siguiente, que no depende del
-- tamaño del catalogo.
SELECT pg_temp.comprobar('M2 no se retiraron claves del catalogo del sistema',
    (SELECT count(*) FILTER (WHERE activo) >= 51 FROM catalogo_atributo WHERE del_sistema));

-- Una clave sembrada sin decir a que aplica es invisible en todos los guiones y
-- nadie lo nota hasta echarla en falta: el alta no la pinta, el editor no la
-- ofrece y el dato simplemente no se captura. Se mira en la tabla que le toca
-- por sujeto -- `catalogo_atributo_tipo` para PROPIEDAD,
-- `catalogo_atributo_operacion` para ENCARGO --, que es la regla que la guarda
-- 2.5 de V78 vigila en la otra direccion.
SELECT pg_temp.comprobar('M2 ninguna clave del sistema se quedo sin aplicabilidad',
    NOT EXISTS (
        SELECT 1 FROM catalogo_atributo c
         WHERE c.del_sistema AND c.activo AND NOT c.aplica_todos
           AND ((c.sujeto = 'PROPIEDAD'
                 AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                                  WHERE t.id_catalogo_atributo = c.id_catalogo_atributo))
             OR (c.sujeto = 'ENCARGO'
                 AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_operacion o
                                  WHERE o.id_catalogo_atributo = c.id_catalogo_atributo)))));

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
SELECT pg_temp.comprobar('4P despues del cutover ningun hecho del inmueble sin linaje',
    NOT EXISTS (
        SELECT 1 FROM atributo_propiedad a
         WHERE a.fecha_creacion > frontera_de_linaje()
           AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                            WHERE r.organizacion_id = a.organizacion_id
                              AND r.sujeto = 'PROPIEDAD'
                              AND r.id_agregado = a.id_propiedad
                              AND r.clave = a.clave)));

SELECT pg_temp.comprobar('4P despues del cutover ninguna condicion del encargo sin linaje',
    NOT EXISTS (
        SELECT 1 FROM atributo_encargo a
         WHERE a.fecha_creacion > frontera_de_linaje()
           AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                            WHERE r.organizacion_id = a.organizacion_id
                              AND r.sujeto = 'ENCARGO'
                              AND r.id_agregado = a.id_captacion
                              AND r.clave = a.clave)));


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
SELECT pg_temp.comprobar('4P despues del cutover ninguna columna estructural sin linaje',
    NOT EXISTS (
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
                  AND r.id_agregado = p.id_propiedad)));

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
-- retirar la clave. Las 322 filas de `controllocal_repositorios` son RESIDUO
-- HISTORICO: sobre una base nueva --CI, otra maquina, un `docker volume rm`--
-- el universo es CERO, y esta comprobacion saldria verde sin haber mirado nada.
-- En `controllocal_dev` el universo ya es cero HOY.
--
-- Por eso la invariante viaja con el CONTROL POSITIVO que va justo debajo: no se
-- confia en encontrar legado, se construye el par exacto que el predicado tiene
-- que cazar y se deshace. Un cero que no se ha contrastado con un control
-- positivo no es un cero.
-- EL PREDICADO SE DEFINE UNA SOLA VEZ. La invariante y su control positivo tienen
-- que preguntar EXACTAMENTE lo mismo: si se escribieran dos veces, romper una
-- dejaria la otra intacta y el control dejaria de vigilar lo que dice vigilar.
CREATE OR REPLACE FUNCTION pg_temp.hay_legado_traducido_sin_linaje()
RETURNS boolean LANGUAGE sql AS $$
    SELECT EXISTS (
        SELECT 1
          FROM atributo_propiedad legado
          JOIN atributo_propiedad nuevo ON nuevo.id_propiedad = legado.id_propiedad
                                       AND nuevo.clave IN ('agua_desague', 'energia_electrica')
         WHERE legado.clave = 'servicios_disponibles'
           AND NOT EXISTS (SELECT 1 FROM rastro_valor_gobernado r
                            WHERE r.organizacion_id = nuevo.organizacion_id
                              AND r.sujeto = 'PROPIEDAD'
                              AND r.id_agregado = nuevo.id_propiedad
                              AND r.clave = nuevo.clave
                              AND (r.naturaleza IS NOT NULL
                                OR r.evidencia_ref IS NOT NULL
                                OR r.id_persona_rol IS NOT NULL)))
$$;

SELECT pg_temp.comprobar('5A ningun inmueble con legado recibio un servicio sin que nadie lo afirmara',
    NOT pg_temp.hay_legado_traducido_sin_linaje(),
    'un valor de servicio sobre un legado ambiguo sin nadie que lo declare ni acta que lo reparta');

-- CONTROL DE COBERTURA DE LA 91, y por que hace falta (auditoria del 2026-08-25).
--
-- La 91 es un NOT EXISTS sobre un JOIN de tres tablas. Si su universo esta
-- vacio sale VERDE sin haber mirado nada, y su universo esta vacio en
-- `controllocal_dev` --0 filas de `servicios_disponibles`-- y lo estara en
-- cualquier base recien creada, porque desde este corte NADIE escribe esa clave
-- por la ruta normal: el trigger la rechaza con 23503 por estar retirada.
--
-- Esto no se arregla contando filas de legado y exigiendo que sean mas de cero:
-- eso volveria a atar el gate al RESIDUO de una base concreta, que es justo el
-- defecto. Se arregla construyendo el caso: se siembra el par exacto que el
-- predicado tiene que cazar --un legado ambiguo y un servicio nuevo que nadie
-- declaro-- y se exige que LO ENCUENTRE. Si un filtro de mas, un join mal puesto
-- o un `AND` invertido dejara el predicado ciego, esto sale ROJO aunque la 91
-- siga verde.
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
-- comprobacion 76 de 4.P: sembrarlo con `now()` fabricaria un dato imposible y
-- envenenaria otro gate desde este. El legado es, por definicion, anterior al
-- mecanismo de linaje. Aqui el savepoint lo deshace todo y la 76 va antes en el
-- script, asi que hoy daria igual -- se hace igualmente para que el control siga
-- siendo correcto si alguien reordena.
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
    ELSE 'FALLO - el predicado de la 91 no caza una traduccion sin linaje: su verde no significa nada'
  END AS v_legado_ctrl \gset
ROLLBACK TO SAVEPOINT legado_5a;

INSERT INTO resultado (prueba, veredicto, nota)
VALUES ('5A CONTROL el predicado del legado caza una traduccion sin linaje',
        :'v_legado_ctrl',
        format('legado realmente presente en esta base: %s filas', :n_legado_5a));

-- Y la clave tiene que haber vuelto a su sitio. El control positivo la reactiva
-- para poder escribir; si el `ROLLBACK TO` no la devolviera a `activo = false`,
-- el gate habria REABIERTO la puerta que 5A cerro -- y las comprobaciones que
-- vienen despues correrian sobre un catalogo que el gate mismo altero.
SELECT pg_temp.comprobar('5A CONTROL y el savepoint devolvio servicios_disponibles a retirada',
    EXISTS (SELECT 1 FROM catalogo_atributo
             WHERE clave = 'servicios_disponibles' AND organizacion_id IS NULL
               AND NOT activo));

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
-- de la 92 a proposito -- si alguien lo cambia sale ROJA, que es el sentido
-- correcto del error: nunca verde por no encontrar lo que buscaba.
SELECT pg_temp.comprobar('INFORME el control del legado imprime su universo',
    EXISTS (SELECT 1 FROM resultado
             WHERE prueba LIKE '5A CONTROL el predicado del legado%' AND nota IS NOT NULL),
    'el control del legado no dejo dicho cuantas filas de legado hay en esta base');

-- La segunda: la alineacion. El informe ya no puede cortar a nadie --pasa de
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
