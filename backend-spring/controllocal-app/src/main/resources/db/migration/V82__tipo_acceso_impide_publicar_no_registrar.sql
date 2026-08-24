-- =====================================================================
-- V82 - `tipo_acceso` impide PUBLICAR, no REGISTRAR
--
-- QUE CORRIGE
-- V81 sembro `tipo_acceso` con exigencia `ALT` en `L`. La decision de fondo del
-- titular era y sigue siendo la misma -- un local no se anuncia sin decir como se
-- entra --, pero `ALT` en este modelo significa DOS cosas a la vez y solo una de
-- ellas era la que se pidio:
--
--     Exigencia.bloqueaAlta()          -> solo ALT
--     Exigencia.bloqueaPublicacion()   -> ALT y PUB
--
-- Con `ALT`, un LOCAL sin `tipo_acceso` NO SE PODIA NI REGISTRAR. Eso choca con
-- lo que V75 y V76 establecieron a proposito: registrar no es encargar, y BROX
-- conoce legitimamente inmuebles que todavia no gestiona o de los que aun no
-- sabe toda la profundidad comercial. Un local avistado desde la calle o
-- reportado por telefono no podia entrar en el registro maestro.
--
-- LA SEMANTICA QUE QUEDA, para un LOCAL sin `tipo_acceso`:
--
--     registrar .................. SI
--     editar ..................... SI
--     conservarse como conocido .. SI
--     servir para inteligencia ... SI
--     PUBLICAR ................... NO
--
-- ESTO NO FUERZA EL MODELO: EL MODELO YA LO DISTINGUIA. V72 construyo los tres
-- niveles exactamente para esto, y el javadoc de
-- `CatalogoAtributo.esRequeridoPara` lo dice con todas las letras: "se pregunta
-- asi y NO comparando contra un nivel: basta que un consumidor lea 'lo que no sea
-- OPC' para que el alta empiece a exigir de golpe todo lo que solo debia exigir
-- el anuncio". Y `AtributoPropiedadRepository` mantiene DOS consultas separadas
-- --`clavesObligatoriasQueFaltan` filtra ALT, `clavesQueImpidenPublicar` filtra
-- ALT y PUB-- precisamente para que una no acabe respondiendo la otra.
--
-- Por eso esta correccion es UNA FILA y no un rediseno: la clave vuelve al nivel
-- que el modelo tenia previsto para su caso. NO SE TOCA NI UNA LINEA DE JAVA.
--
-- LAS DOS COLUMNAS, EN LA MISMA SENTENCIA. `requerido` es espejo exacto de
-- `exigencia = 'ALT'` desde V72, y el guard 2.4 de V78 lo comprueba sobre TODO el
-- catalogo. Cambiar solo `exigencia` dejaria ese guard roto en la siguiente
-- migracion que lo corriera.
--
-- LO QUE ESTA MIGRACION NO HACE, y no por olvido:
--   * NO modifica V81. Esta cerrada y auditada, y queda byte por byte intacta.
--   * NO rellena `tipo_acceso` en los 21 locales. Sigue siendo la regla que hace
--     aceptable el bloqueo: se desbloquea el hecho verificado, no el relleno.
--   * NO infiere ningun historico.
--   * NO cambia ninguna otra exigencia, vocabulario, opcion ni aplicabilidad.
--   * NO toca `exigirPublicable` ni ninguna consulta de gobierno.
--
-- LA PUBLICABILIDAD NO SE MUEVE, Y ESO ES LA PRUEBA. Antes: 5 de 26 publicables,
-- 21 locales bloqueados. Despues: exactamente los mismos. Esta correccion no
-- busca hacerlos publicables -- busca poder REGISTRAR un local sin conocer
-- todavia el dato. Que el censo de publicabilidad no cambie es lo que demuestra
-- que el cambio hizo lo suyo y solo eso.
--
-- CONSECUENCIA MEDIDA, que no es un defecto y queda escrita:
-- `tipo_acceso` desaparece de `PropiedadResponse.atributosQueFaltan`, que se
-- alimenta solo de ALT. Lo sigue nombrando `EncargoResponse.faltanParaPublicar`,
-- que mira ALT y PUB y vive dentro del encargo. Traducido a la cartera: los 7
-- locales CON encargo vivo siguen avisando; los 14 sin encargo dejan de avisarlo
-- -- y sin encargo no se puede publicar, asi que no se pierde ninguna barrera,
-- solo el aviso. Si el titular quiere que los 14 tambien avisen, eso es
-- superficie nueva y corte propio.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. La foto de lo que hay, para poder afirmar que NO se movio nada mas.
--
-- Sin ella, "ninguna otra fila cambio de exigencia" seria una suposicion: un
-- recuento final cuadra igual si una fila baja y otra sube. Se compara el
-- CONJUNTO, no el total.
--
-- Se retira al final con un DROP explicito y no con ON COMMIT DROP, para que
-- sobreviva tanto si Flyway envuelve la migracion en una transaccion como si no
-- (leccion de V78).
-- ---------------------------------------------------------------------
CREATE TEMP TABLE valores_antes AS
SELECT count(*) AS n FROM atributo_propiedad WHERE clave = 'tipo_acceso';

CREATE TEMP TABLE exigencia_antes AS
SELECT t.id_catalogo_atributo, c.clave, c.organizacion_id,
       t.tipo_propiedad, t.exigencia, t.requerido
  FROM catalogo_atributo_tipo t
  JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo;

-- ---------------------------------------------------------------------
-- 1. El cambio. Una fila, dos columnas, una sentencia.
--
-- Por CLAVE y nunca por id literal: los identificadores los genera la secuencia
-- y no son los mismos en dev, en pruebas y en produccion.
-- ---------------------------------------------------------------------
UPDATE catalogo_atributo_tipo t
   SET exigencia = 'PUB',
       requerido = false
  FROM catalogo_atributo c
 WHERE c.id_catalogo_atributo = t.id_catalogo_atributo
   AND c.organizacion_id IS NULL
   AND c.clave = 'tipo_acceso'
   AND t.tipo_propiedad = 'L';

-- ---------------------------------------------------------------------
-- 2. Las guardas.
--
-- Comprueban el estado resultante. Las cifras literales -- 235, 224, 10 -- lo son
-- porque son el censo EXACTO que esta correccion se compromete a no mover, no el
-- tamano de nada que crezca con el uso.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    fila            RECORD;
    pub_de_mas      TEXT;
    alt_mal         TEXT;
    alt_inesperadas TEXT;
    movidas         TEXT;
    total_tipo      INT;
    total_opc       INT;
    total_alt       INT;
    con_valor       BIGINT;
BEGIN
    -- 2.1 La fila objetivo quedo exactamente como se decidio. Si el UPDATE no
    --     hubiera encontrado su clave, no habria tocado nada y la migracion
    --     terminaria "bien".
    SELECT t.exigencia, t.requerido INTO fila
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND c.clave = 'tipo_acceso' AND t.tipo_propiedad = 'L';
    IF fila IS NULL THEN
        RAISE EXCEPTION 'V82: no existe la fila tipo_acceso/L que esta migracion venia a corregir.';
    END IF;
    IF fila.exigencia <> 'PUB' OR fila.requerido THEN
        RAISE EXCEPTION 'V82: tipo_acceso/L quedo en % / requerido=%, y se esperaba PUB / false.',
            fila.exigencia, fila.requerido;
    END IF;

    -- 2.2 Y es la UNICA PUB del catalogo del sistema. Una PUB de mas seria un
    --     bloqueo de publicacion que nadie decidio.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ') INTO pub_de_mas
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND t.exigencia = 'PUB'
       AND NOT (c.clave = 'tipo_acceso' AND t.tipo_propiedad = 'L');
    IF pub_de_mas IS NOT NULL THEN
        RAISE EXCEPTION 'V82: aparecieron filas PUB que esta correccion no promueve: %', pub_de_mas;
    END IF;

    -- 2.3 Quedan DIEZ ALT, y son las diez heredadas -- las que ya existian antes
    --     del Corte 4 y ninguna de las cuales exige salir a mirar. Se comprueba
    --     el CONJUNTO, no el numero: bajar una y subir otra da el mismo total.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ' ORDER BY c.clave, t.tipo_propiedad)
      INTO alt_inesperadas
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND t.exigencia = 'ALT'
       AND NOT ((c.clave = 'metraje_total' AND t.tipo_propiedad IN ('A','C','D','L','O','T','X'))
             OR (c.clave = 'dormitorios'   AND t.tipo_propiedad IN ('C','D'))
             OR (c.clave = 'zonificacion'  AND t.tipo_propiedad = 'T'));
    IF alt_inesperadas IS NOT NULL THEN
        RAISE EXCEPTION 'V82: hay filas ALT que no son las diez heredadas: %', alt_inesperadas;
    END IF;

    SELECT count(*) INTO total_alt
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND t.exigencia = 'ALT';
    IF total_alt <> 10 THEN
        RAISE EXCEPTION 'V82: se esperaban 10 filas ALT y hay %', total_alt;
    END IF;

    -- 2.4 `requerido` sigue siendo espejo exacto de `exigencia = ALT` en TODO el
    --     catalogo. Guard 2.4 de V78: es justo lo que se rompe si alguien cambia
    --     una sola de las dos columnas.
    SELECT string_agg(c.clave || '/' || t.tipo_propiedad, ', ') INTO alt_mal
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE t.requerido <> (t.exigencia = 'ALT');
    IF alt_mal IS NOT NULL THEN
        RAISE EXCEPTION 'V82: requerido y exigencia divergen en: %', alt_mal;
    END IF;

    -- 2.5 NINGUNA OTRA FILA SE MOVIO. Se compara contra la foto del bloque 0,
    --     fila a fila, excluyendo la unica que esta migracion viene a cambiar.
    --     Un recuento suelto no serviria: cuadra igual si una baja y otra sube.
    SELECT string_agg(a.clave || '/' || a.tipo_propiedad
                      || ': ' || a.exigencia || ' -> ' || t.exigencia, ', ')
      INTO movidas
      FROM exigencia_antes a
      JOIN catalogo_atributo_tipo t
        ON t.id_catalogo_atributo = a.id_catalogo_atributo
       AND t.tipo_propiedad = a.tipo_propiedad
     WHERE NOT (a.organizacion_id IS NULL
                AND a.clave = 'tipo_acceso' AND a.tipo_propiedad = 'L')
       AND (t.exigencia IS DISTINCT FROM a.exigencia
         OR t.requerido IS DISTINCT FROM a.requerido);
    IF movidas IS NOT NULL THEN
        RAISE EXCEPTION 'V82: esta correccion movio filas que no debia tocar: %', movidas;
    END IF;

    -- Y no se perdio ni aparecio ninguna fila de aplicabilidad.
    IF (SELECT count(*) FROM exigencia_antes) <> (SELECT count(*) FROM catalogo_atributo_tipo) THEN
        RAISE EXCEPTION 'V82: cambio el numero de filas de aplicabilidad: % antes, % ahora.',
            (SELECT count(*) FROM exigencia_antes), (SELECT count(*) FROM catalogo_atributo_tipo);
    END IF;

    -- 2.6 El censo DEL CATALOGO DEL SISTEMA, que esta correccion se comprometio
    --     a no mover.
    --
    --     El filtro `organizacion_id IS NULL` NO es decoracion: la tabla entera
    --     incluye las claves que cada organizacion define para si, y en
    --     `controllocal_repositorios` -- la base de integracion -- las suites han
    --     dejado 4361 filas de tenant. Un censo sin filtrar mide el uso del
    --     producto y no la invariante, y esta migracion se cayo ahi antes de
    --     llevar el filtro. Las cifras del sistema si son identicas en las dos
    --     bases, que es lo que las hace afirmables.
    SELECT count(*) INTO total_tipo
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL;
    IF total_tipo <> 235 THEN
        RAISE EXCEPTION 'V82: se esperaban 235 filas de aplicabilidad del sistema y hay %', total_tipo;
    END IF;

    SELECT count(*) INTO total_opc
      FROM catalogo_atributo_tipo t
      JOIN catalogo_atributo c ON c.id_catalogo_atributo = t.id_catalogo_atributo
     WHERE c.organizacion_id IS NULL AND t.exigencia = 'OPC' AND NOT t.requerido;
    IF total_opc <> 224 THEN
        RAISE EXCEPTION 'V82: se esperaban 224 filas OPC/false del sistema y hay %', total_opc;
    END IF;

    -- 2.7 Esta correccion NO ESCRIBE NI UN VALOR de `tipo_acceso`. Los locales
    --     que no lo tienen siguen sin tenerlo: lo que recuperan es poder EXISTIR
    --     sin el, no poder anunciarse sin el.
    --
    --     Se afirma que el numero NO CAMBIA, y no que sea cero. Cero es cierto en
    --     la cartera de desarrollo, pero en la base de integracion las suites han
    --     registrado 125 locales CON el dato -- y eso es legitimo: un fixture que
    --     publica tiene que declararlo. "No lo he tocado" es la invariante;
    --     "no hay ninguno" era una foto de una sola base.
    SELECT count(*) INTO con_valor FROM atributo_propiedad WHERE clave = 'tipo_acceso';
    IF con_valor <> (SELECT n FROM valores_antes) THEN
        RAISE EXCEPTION
            'V82: los valores de tipo_acceso pasaron de % a %. Esta correccion no escribe ninguno.',
            (SELECT n FROM valores_antes), con_valor;
    END IF;

    RAISE NOTICE 'V82: tipo_acceso/L pasa de ALT a PUB (requerido false). ALT quedan 10 (las heredadas), PUB 1, OPC 224, total 235, y los valores de tipo_acceso quedan como estaban.';
END $$;

DROP TABLE exigencia_antes;
DROP TABLE valores_antes;
