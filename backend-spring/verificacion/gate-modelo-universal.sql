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

BEGIN;

CREATE TEMP TABLE resultado (n serial, prueba text, veredicto text);

CREATE OR REPLACE FUNCTION pg_temp.comprobar(p_prueba text, p_condicion boolean, p_detalle text DEFAULT NULL)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO resultado (prueba, veredicto)
    VALUES (p_prueba, CASE WHEN p_condicion THEN 'OK' ELSE 'FALLO' || COALESCE(' - ' || p_detalle, '') END);
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
-- Veredicto
-- =====================================================================
-- Se recupera la salida: a partir de aqui si queremos ver lo que sale.
\o
\echo ''
SELECT lpad(n::text, 3) || '  ' || rpad(prueba, 62) || veredicto AS "GATE DEL MODELO UNIVERSAL"
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
