-- =====================================================================
-- V61 - Consolidar la autoridad: mover los valores a donde D-E4-3 dijo.
--
-- V60 DECLARO la autoridad y no movio un solo valor. Esta la aplica, en dos
-- direcciones opuestas y por eso en un solo sitio:
--
--   metraje  ESTRUCTURAL  -> se BORRA su copia de atributo_propiedad
--   los seis ATRIBUTO     -> se RELLENA lo que falte desde su columna espejo
--
-- LAS COLUMNAS ESPEJO NO SE TOCAN
-- Ni se ponen a NULL ni se borran aqui. Los lectores viejos —listado y ficha—
-- siguen usandolas hasta el paso 7 de D-E4-3, y vaciarlas ahora seria romper
-- esas pantallas a proposito antes de haber migrado sus lecturas. Lo que ya no
-- ocurre es que alguien las ESCRIBA: eso lo corto el paso 4.
--
-- LA GUARDA QUE PUEDE PARAR LA MIGRACION
-- Antes de borrar una sola copia de metraje_total se exige que columna y
-- atributo digan lo mismo en todas las filas. Medido el 2026-08-18: 23 iguales
-- y 0 divergentes. Si en el despliegue apareciera una divergencia, esta
-- migracion FALLA en vez de elegir un ganador -- porque elegirlo es una
-- decision de negocio y no le toca a un script.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1 · metraje: recuperar lo que solo viva en el atributo, y luego borrar.
--
-- El orden importa. Si alguna fila tuviera el valor SOLO en el atributo
-- -- porque el alta universal lo escribio antes de que el paso 4 cortara la
-- fuga -- borrar primero perderia el dato. Se sube al campo canonico y
-- despues se retira la copia.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    divergentes  bigint;
    recuperados  bigint;
    borrados     bigint;
BEGIN
    SELECT count(*) INTO divergentes
      FROM propiedad p
      JOIN atributo_propiedad a
        ON a.id_propiedad = p.id_propiedad AND a.clave = 'metraje_total'
     WHERE p.metraje IS NOT NULL
       AND a.valor_numero IS NOT NULL
       AND a.valor_numero <> p.metraje;

    IF divergentes > 0 THEN
        RAISE EXCEPTION
            'V61: % propiedades con metraje distinto en columna y atributo. '
            'Elegir cual gana es una decision de negocio: resuelvelas antes de migrar.',
            divergentes;
    END IF;

    UPDATE propiedad p
       SET metraje = a.valor_numero
      FROM atributo_propiedad a
     WHERE a.id_propiedad = p.id_propiedad
       AND a.clave = 'metraje_total'
       AND a.valor_numero IS NOT NULL
       AND p.metraje IS NULL;
    GET DIAGNOSTICS recuperados = ROW_COUNT;

    DELETE FROM atributo_propiedad WHERE clave = 'metraje_total';
    GET DIAGNOSTICS borrados = ROW_COUNT;

    RAISE NOTICE 'V61: metraje consolidado; % recuperados al campo canonico, % copias retiradas',
        recuperados, borrados;
END $$;

-- ---------------------------------------------------------------------
-- 2 · Los seis gobernados: backfill de lo historico que solo este en columna.
--
-- Idempotente por el NOT EXISTS: una reejecucion deja 0 filas. Y no se copia
-- en sentido contrario -- atributo -> columna -- porque la autoridad ya es el
-- atributo: rellenar la columna espejo seria reabrir la doble escritura que
-- el paso 4 acaba de cerrar.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    insertados bigint;
    total      bigint := 0;
BEGIN
    -- Numericos.
    INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_numero)
    SELECT p.organizacion_id, p.id_propiedad, v.clave, v.valor
      FROM propiedad p
      CROSS JOIN LATERAL (VALUES
            ('ambientes',            p.ambientes::numeric),
            ('frente',               p.frente),
            ('cuota_mantenimiento',  p.cuota_mantenimiento),
            ('estacionamientos',     p.numero_estacionamientos::numeric),
            ('antiguedad_anios',     p.antiguedad_anios::numeric)
           ) AS v(clave, valor)
     WHERE v.valor IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_propiedad = p.id_propiedad AND a.clave = v.clave);
    GET DIAGNOSTICS insertados = ROW_COUNT;
    total := total + insertados;

    -- Texto.
    INSERT INTO atributo_propiedad (organizacion_id, id_propiedad, clave, valor_texto)
    SELECT p.organizacion_id, p.id_propiedad, 'zonificacion', p.zonificacion
      FROM propiedad p
     WHERE p.zonificacion IS NOT NULL AND btrim(p.zonificacion) <> ''
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_propiedad = p.id_propiedad AND a.clave = 'zonificacion');
    GET DIAGNOSTICS insertados = ROW_COUNT;
    total := total + insertados;

    RAISE NOTICE 'V61: % valores historicos subidos de columna espejo a atributo gobernado', total;
END $$;

-- ---------------------------------------------------------------------
-- 3 · Evidencia: cero copias de metraje y ningun hueco en los seis.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    copias  bigint;
    huecos  bigint;
BEGIN
    SELECT count(*) INTO copias FROM atributo_propiedad WHERE clave = 'metraje_total';
    IF copias > 0 THEN
        RAISE EXCEPTION 'V61: quedan % copias de metraje_total; su autoridad es el campo canonico', copias;
    END IF;

    SELECT count(*) INTO huecos
      FROM propiedad p
      CROSS JOIN LATERAL (VALUES
            ('ambientes',            p.ambientes::numeric),
            ('frente',               p.frente),
            ('cuota_mantenimiento',  p.cuota_mantenimiento),
            ('estacionamientos',     p.numero_estacionamientos::numeric),
            ('antiguedad_anios',     p.antiguedad_anios::numeric)
           ) AS v(clave, valor)
     WHERE v.valor IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                        WHERE a.id_propiedad = p.id_propiedad AND a.clave = v.clave);

    IF huecos > 0 THEN
        RAISE EXCEPTION 'V61: % valores siguen solo en su columna espejo', huecos;
    END IF;

    RAISE NOTICE 'V61: autoridad consolidada; 0 copias de metraje y 0 huecos en los gobernados';
END $$;
