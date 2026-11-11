-- =====================================================================
-- V91 - La retirada dicha entera, la galeria universal y VENDIDO
-- =====================================================================
--
-- QUE ANADE, en cuatro bloques independientes que comparten una idea:
-- un dato que deja de preguntarse no puede degradar lo que ya se sabe,
-- y una puerta nueva no puede ser mas barata que la que ya existia.
--
--   1. `catalogo_atributo.fecha_retiro` y `.sustituida_por`: la ficha
--      dice CUANDO se retiro una clave y POR QUE clave se sustituyo,
--      en vez de decir solo «se retiro» (N16).
--   2. La guarda que faltaba del multivalor retirado: `exigir_opcion_
--      gobernada` caza el INSERT y el UPDATE y NO ve el DELETE, asi que
--      a una `LISTA_MULTIPLE` retirada se le podian QUITAR opciones
--      (N22).
--   3. `exigir_catalogo_no_sombrea_al_sistema` pasa a ser BIDIRECCIONAL:
--      sembrar una clave del sistema falla si un tenant ya la tiene
--      (N25).
--   4. `DisponibilidadComercial` gana `V` = VENDIDO, y la galeria de
--      fotos se normaliza a `orden` 1-based porque desde este corte la
--      portada es «la foto con orden 1» y se puede reordenar.
--
-- ADITIVA. No toca ninguna migracion aplicada. No borra una sola fila
-- de datos: los dos UPDATE que lleva reordenan `foto_propiedad.orden`
-- -- 0 filas medidas en las dos bases el 2026-09-05 -- y no tocan
-- ninguna clave, ningun valor y ningun binario.
--
-- LO QUE NO RELLENA, y es deliberado. `fecha_retiro` queda **NULL** en
-- todas las claves ya retiradas: 1 en `controllocal_dev`
-- (`servicios_disponibles`, retirada por V84) y 3.712 en
-- `controllocal_repositorios` (residuo de fixtures). El dia en que se
-- retiraron no consta en ninguna columna; deducirlo de
-- `flyway_schema_history.installed_on` seria fechar la MIGRACION, no la
-- retirada, y en una base restaurada o reconstruida esa fecha es otra.
-- Un dato que no se sabe se declara FALTANTE. Desde aqui lo estampa el
-- trigger del bloque 1, asi que la SIGUIENTE retirada si tiene fecha.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. La retirada dicha entera: cuando, y a favor de que
--
-- Hasta aqui la ficha decia «Ya no se pregunta: «Servicios disponibles»
-- se retiro del catalogo». Es cierto y es la mitad: quien lo lee no
-- sabe si fue ayer o hace dos anos, ni si hay otra clave que ocupo su
-- sitio. Las dos preguntas se responden con dato del catalogo, que es
-- donde vive la decision, y no con una frase escrita en el cliente.
--
-- POR QUE UN TRIGGER Y NO «QUE LO PONGA LA MIGRACION QUE RETIRE».
-- Porque retirar una clave es un `UPDATE ... SET activo = false` de una
-- linea, y la linea que se olvide de la fecha no la ve nadie: la clave
-- queda retirada y sin fecha, indistinguible de las legadas. El trigger
-- lo estampa siempre, venga de una migracion o de SQL directo.
--
-- Y al reves tambien: reactivar una clave BORRA la fecha. «Retirada el
-- 5 de septiembre» sobre una clave que hoy se pregunta seria falso.
-- ---------------------------------------------------------------------

ALTER TABLE catalogo_atributo
    ADD COLUMN fecha_retiro   TIMESTAMPTZ,
    ADD COLUMN sustituida_por VARCHAR(60);

COMMENT ON COLUMN catalogo_atributo.fecha_retiro IS
    'Cuando dejo de preguntarse esta clave. NULL = sigue viva, o se retiro '
    'antes de V91 y la fecha no consta (FALTANTE, nunca deducida). Lo estampa '
    'tg_catalogo_fecha_de_retiro, no la mano de quien retira.';

COMMENT ON COLUMN catalogo_atributo.sustituida_por IS
    'La clave que ocupo su sitio, si la hubo. NULL = se retiro sin sustituta, '
    'que es distinto de «no se sabe»: una sustituta se declara al retirar. No '
    'lleva FK -- la unicidad de clave es por (organizacion, clave) -- y quien '
    'la lee publica el rotulo si puede resolverlo y la clave tal cual si no.';

CREATE OR REPLACE FUNCTION estampar_fecha_de_retiro() RETURNS trigger AS $$
BEGIN
    IF OLD.activo AND NOT NEW.activo AND NEW.fecha_retiro IS NULL THEN
        NEW.fecha_retiro := now();
    END IF;
    IF NOT OLD.activo AND NEW.activo THEN
        -- Vuelve a preguntarse: ya no esta retirada, y decir desde cuando lo
        -- estuvo seria una afirmacion sobre el presente que es falsa.
        NEW.fecha_retiro := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_catalogo_fecha_de_retiro
    BEFORE UPDATE ON catalogo_atributo
    FOR EACH ROW EXECUTE FUNCTION estampar_fecha_de_retiro();


-- ---------------------------------------------------------------------
-- 2. La guarda que faltaba: a una LISTA_MULTIPLE retirada tampoco se le
--    QUITAN opciones (N22)
--
-- `exigir_opcion_gobernada` es BEFORE INSERT OR UPDATE y exige
-- `c.activo = true`. Cubre exactamente la mitad del hecho: anadir una
-- opcion a una clave retirada se rechaza y quitarla no. Y quitar es la
-- otra mitad de editar una lista -- de hecho es la irreversible.
--
-- LA DISTINCION QUE ESTA GUARDA TIENE QUE HACER, y es toda la
-- dificultad: `atributo_propiedad_opcion` cuelga de `atributo_propiedad`
-- con ON DELETE CASCADE. Cuando se retira el VALOR entero -- se borra el
-- ancla -- las opciones se van con el, y eso es legitimo y no puede
-- bloquearse: seria impedir borrar el dato entero por proteger sus
-- partes.
--
-- Se distingue mirando si el ancla SIGUE AHI. En una cascada, PostgreSQL
-- borra el padre y despues dispara el DELETE de los hijos, asi que
-- dentro de este trigger el ancla ya no existe. Con el ancla viva, este
-- DELETE es una edicion que quita opciones -- y esa es la que la clave
-- retirada no admite.
--
-- HOY NO HAY DATO AFECTADO EN `dev`: 0 claves LISTA_MULTIPLE retiradas
-- y 0 filas en `atributo_propiedad_opcion` (medido 2026-09-05). En
-- `controllocal_repositorios` si -- 784 claves `zz_*` retiradas y 888
-- opciones colgando de ellas --, y es residuo de fixtures en una base de
-- pruebas: se dice para que no se descubra como sorpresa.
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION exigir_opcion_de_clave_viva_al_borrar() RETURNS trigger AS $$
DECLARE
    cat record;
BEGIN
    -- Sin ancla no hay edicion: es la cascada del borrado del valor entero.
    IF NOT EXISTS (SELECT 1 FROM atributo_propiedad a
                    WHERE a.id_atributo_propiedad = OLD.id_atributo_propiedad) THEN
        RETURN OLD;
    END IF;

    SELECT c.* INTO cat
      FROM atributo_propiedad a
      JOIN catalogo_atributo c ON c.clave = a.clave
                              AND (c.organizacion_id = a.organizacion_id
                                   OR c.organizacion_id IS NULL)
     WHERE a.id_atributo_propiedad = OLD.id_atributo_propiedad
     ORDER BY c.organizacion_id NULLS LAST
     LIMIT 1;

    -- Una clave que el catalogo no conoce no la gobierna esta regla: la
    -- gobierna `exigir_atributo_gobernado`, que no la habria dejado entrar.
    IF NOT FOUND THEN
        RETURN OLD;
    END IF;

    IF NOT cat.activo THEN
        RAISE EXCEPTION
            'El atributo "%" se retiro del catalogo: su valor se conserva y se lee, '
            'pero ya no admite que se le quiten opciones. Retirar la pregunta no '
            'retira el dato, y tampoco autoriza a recortarlo.', cat.clave
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_opcion_de_clave_viva_al_borrar
    BEFORE DELETE ON atributo_propiedad_opcion
    FOR EACH ROW EXECUTE FUNCTION exigir_opcion_de_clave_viva_al_borrar();


-- ---------------------------------------------------------------------
-- 3. El sombreado, en las DOS direcciones (N25)
--
-- `exigir_catalogo_no_sombrea_al_sistema` sale por `RETURN NEW` en
-- cuanto `NEW.organizacion_id IS NULL`, asi que sembrar una clave del
-- sistema no mira si algun tenant ya la tenia. La convivencia es
-- posible -- `uq_catalogo_atributo_clave` es unico sobre
-- (COALESCE(organizacion_id,0), clave) -- y `exigir_atributo_gobernado`
-- resuelve con `ORDER BY c.organizacion_id NULLS LAST LIMIT 1`: ganaria
-- la del tenant. O sea que la fila del sistema entraria y quedaria
-- sombreada por la que ya estaba.
--
-- Primero se MIDE y solo despues se instala la guarda: si hoy hubiera
-- una colision, ponerla haria fallar la SIGUIENTE migracion que siembre
-- y nadie sabria por que. Medido el 2026-09-05: 0 en `controllocal_dev`
-- y 0 en `controllocal_repositorios`.
-- ---------------------------------------------------------------------

DO $$
DECLARE
    colisiones bigint;
BEGIN
    SELECT count(*) INTO colisiones
      FROM catalogo_atributo t
      JOIN catalogo_atributo s ON s.clave = t.clave AND s.organizacion_id IS NULL
     WHERE t.organizacion_id IS NOT NULL;

    IF colisiones > 0 THEN
        RAISE EXCEPTION
            'Hay % clave(s) de tenant que sombrean una clave del sistema. La guarda '
            'bidireccional de este bloque las habria impedido; instalarla ahora '
            'dejaria la base con un sombreado que nadie declaro y que la lectura '
            'resuelve a favor del tenant. Resuelvelas -- renombrando la del tenant o '
            'adoptando la comun -- antes de aplicar V91.', colisiones
            USING ERRCODE = 'check_violation';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION exigir_catalogo_no_sombrea_al_sistema() RETURNS trigger AS $$
DECLARE
    tipo_del_sistema varchar(10);
    tenants_que_ya_la_tienen bigint;
BEGIN
    -- Regla 4: nadie se declara "del sistema" desde un tenant. Las filas del
    -- sistema las pone una migracion, que es la unica que escribe con
    -- organizacion_id NULL.
    IF NEW.del_sistema AND NEW.organizacion_id IS NOT NULL THEN
        RAISE EXCEPTION
            'Un atributo de una organizacion no puede declararse del sistema'
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.organizacion_id IS NULL THEN
        -- LA DIRECCION QUE FALTABA (N25). Sembrar una clave comun cuando un
        -- tenant ya la tiene no produce un error: produce DOS definiciones de
        -- la misma clave conviviendo, y la lectura se queda con la del tenant.
        -- La fila del sistema entraria para no gobernar nada.
        SELECT count(*) INTO tenants_que_ya_la_tienen
          FROM catalogo_atributo
         WHERE organizacion_id IS NOT NULL
           AND clave = NEW.clave
           AND (TG_OP = 'INSERT' OR id_catalogo_atributo <> NEW.id_catalogo_atributo);

        IF tenants_que_ya_la_tienen > 0 THEN
            RAISE EXCEPTION
                'La clave "%" ya existe en % organizacion(es): sembrarla como clave '
                'comun dejaria dos definiciones conviviendo, y la lectura resuelve a '
                'favor del tenant -- o sea que la del sistema no gobernaria nada. '
                'Resuelvelo con esas organizaciones antes de sembrarla.',
                NEW.clave, tenants_que_ya_la_tienen
                USING ERRCODE = 'unique_violation';
        END IF;
        RETURN NEW;
    END IF;

    -- Regla 1: la clave del sistema no se sombrea. Ni con el mismo tipo ni
    -- con otro: aunque coincidieran hoy, dos definiciones de la misma clave
    -- divergen en cuanto una de las dos cambie.
    SELECT tipo_dato INTO tipo_del_sistema
      FROM catalogo_atributo
     WHERE organizacion_id IS NULL AND clave = NEW.clave;

    IF FOUND THEN
        RAISE EXCEPTION
            'La clave "%" es del catalogo comun (tipo %): una organizacion no puede '
            'redefinirla. Usa esa clave tal cual, o elige un nombre propio.',
            NEW.clave, tipo_del_sistema
            USING ERRCODE = 'unique_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------
-- 4a. VENDIDO entra en el vocabulario de la disponibilidad
--
-- `CierreVentaServiceImpl` marcaba `RETIRADO` -- «retirado del mercado» --
-- porque era lo mas fuerte que el vocabulario sabia decir con verdad, y
-- lo dejo escrito como decision pendiente. Es cierto y es debil: no
-- distingue un inmueble vendido de uno que el propietario saco del
-- mercado, y son dos hechos distintos con dos futuros distintos.
--
-- ALQUILADO no se anade: ya existe (`A`, desde V17) y lo produce
-- `ContratoServiceImpl.cerrarLocal`. Medido antes de escribir.
--
-- VENDIDO es terminal en la maquina de estados de la capa service: no
-- se declara ninguna transicion de salida porque no hay operacion que
-- la produzca, y declarar una transicion sin productor seria vocabulario
-- que nadie escribe. Si una venta se anula, eso es un hecho nuevo con su
-- propio alcance.
-- ---------------------------------------------------------------------

ALTER TABLE propiedad DROP CONSTRAINT ck_propiedad_disponibilidad;
ALTER TABLE propiedad
    ADD CONSTRAINT ck_propiedad_disponibilidad
    CHECK (disponibilidad_comercial IN ('D', 'R', 'A', 'T', 'V'));

COMMENT ON COLUMN propiedad.disponibilidad_comercial IS
    'D disponible, R reservado, A alquilado, T retirado del mercado, V vendido. '
    'NULL = no hay encargo vivo que declare oferta (V75). V lo escribe el cierre '
    'de venta (V91); T sigue siendo la retirada por decision del propietario o del '
    'fin de un contrato.';


-- ---------------------------------------------------------------------
-- 4b. La galeria: `orden` empieza en 1 y la portada es la foto 1
--
-- Hasta aqui `orden` se asignaba como el numero de fotos que ya habia,
-- o sea 0 para la primera, y nadie podia reordenarla: la portada era
-- «la primera por (orden, id_foto)» y punto. Con `PUT
-- /propiedades/{id}/fotos/orden` la posicion pasa a ser un dato que el
-- usuario decide, y «la portada es la foto con orden 1» es una frase que
-- se puede decir sin explicar un desfase.
--
-- Se normaliza lo existente antes de exigirlo. Medido el 2026-09-05: 0
-- filas en `controllocal_dev` y 0 en `controllocal_repositorios`, asi
-- que hoy el UPDATE no toca nada -- se escribe igualmente porque una
-- base restaurada o la del titular pueden traer filas, y entonces la
-- unica forma de que el CHECK no las rechace es haberlas ordenado.
-- ---------------------------------------------------------------------

UPDATE foto_propiedad f
   SET orden = n.posicion
  FROM (SELECT id_foto,
               row_number() OVER (PARTITION BY id_propiedad ORDER BY orden, id_foto) AS posicion
          FROM foto_propiedad) n
 WHERE n.id_foto = f.id_foto
   AND f.orden IS DISTINCT FROM n.posicion;

ALTER TABLE foto_propiedad
    ADD CONSTRAINT ck_foto_propiedad_orden CHECK (orden >= 1);

COMMENT ON COLUMN foto_propiedad.orden IS
    'Posicion en la galeria, empezando en 1. La foto con orden 1 es la PORTADA. '
    'La escribe FotoPropiedadServiceImpl -- al anadir, al reordenar y al cerrar '
    'el hueco que deja una eliminada -- y no admite huecos ni ceros (V91).';
