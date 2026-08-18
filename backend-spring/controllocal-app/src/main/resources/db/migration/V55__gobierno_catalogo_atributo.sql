-- =====================================================================
-- V55 - Gobierno del catalogo de atributos: que puede y que no puede hacer
--       una organizacion con el vocabulario comun.
--
-- QUE PROBLEMA CIERRA
-- V48 dejo el catalogo HIBRIDO -- filas del sistema (organizacion_id NULL)
-- + filas privadas de cada tenant -- y el indice unico
-- `uq_catalogo_atributo_clave` sobre `(COALESCE(organizacion_id,0), clave)`.
-- Ese indice impide dos `dormitorios` DENTRO del mismo ambito, pero no impide
-- lo que de verdad rompe el modelo:
--
--     BROX:    dormitorios -> NUMERO
--     Tenant:  dormitorios -> TEXTO     <-- hoy se puede escribir
--
-- Y ademas nada impedia a una organizacion BORRAR un atributo del sistema o
-- cambiarle el tipo con un UPDATE.
--
-- POR QUE IMPORTA, Y NO ES BUROCRACIA
-- El valor del catalogo es que dos propiedades de dos corredoras distintas
-- se puedan COMPARAR. En cuanto `dormitorios` significa un numero en una
-- organizacion y una cadena en otra, el matcher deja de poder cruzarlas y la
-- inteligencia agregada -- el moat -- deja de existir. El vocabulario comun
-- no es una convencion: es el activo.
--
-- LO QUE SIGUE SIENDO LIBRE
-- Una organizacion puede crear los atributos que quiera, con las claves que
-- quiera, mientras no pisen una clave del sistema. Son privados suyos,
-- declaran a que tipos de propiedad aplican y nadie mas los ve.
--
-- LAS CUATRO REGLAS, IMPUESTAS POR LA BASE
--   1. Un atributo de tenant NO puede usar una clave del sistema (sombra).
--   2. Un atributo del sistema NO se borra.
--   3. A un atributo del sistema no se le cambia clave, tipo de dato ni
--      su condicion de sistema.
--   4. Un atributo de tenant no puede convertirse en atributo del sistema.
--
-- La base y no solo el servicio, por la razon de siempre: la guarda da el
-- mensaje, el constraint da la garantia aunque alguien escriba por SQL.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Reglas 1 y 4: al ESCRIBIR una fila de organizacion.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION exigir_catalogo_no_sombrea_al_sistema()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    tipo_del_sistema varchar(10);
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
$$;

CREATE TRIGGER tg_catalogo_no_sombrea
    BEFORE INSERT OR UPDATE ON catalogo_atributo
    FOR EACH ROW
    EXECUTE FUNCTION exigir_catalogo_no_sombrea_al_sistema();

-- ---------------------------------------------------------------------
-- Reglas 2 y 3: lo del sistema es inmutable en lo que lo hace comparable.
--
-- `rotulo`, `orden` y `unidad` NO estan protegidos: son presentacion, y
-- cambiarlos no rompe la comparabilidad. `activo` tampoco, para poder retirar
-- un atributo del catalogo sin borrar los valores que ya se escribieron.
-- Lo que no se toca es la IDENTIDAD (`clave`), el CONTRATO (`tipo_dato`) y la
-- pertenencia (`del_sistema`, `organizacion_id`).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION proteger_catalogo_del_sistema()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.del_sistema THEN
            RAISE EXCEPTION
                'El atributo "%" es del catalogo comun y no se puede borrar. '
                'Para retirarlo de las preguntas, ponlo activo = false.', OLD.clave
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.del_sistema THEN
        IF NEW.clave IS DISTINCT FROM OLD.clave THEN
            RAISE EXCEPTION
                'La clave de un atributo del catalogo comun no cambia ("%" -> "%"): '
                'es el nombre por el que dos organizaciones comparan.', OLD.clave, NEW.clave
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.tipo_dato IS DISTINCT FROM OLD.tipo_dato THEN
            RAISE EXCEPTION
                'El tipo de "%" es % y no puede cambiar a %: los valores ya escritos '
                'dejarian de significar lo mismo.', OLD.clave, OLD.tipo_dato, NEW.tipo_dato
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.del_sistema IS DISTINCT FROM OLD.del_sistema
           OR NEW.organizacion_id IS DISTINCT FROM OLD.organizacion_id THEN
            RAISE EXCEPTION
                'Un atributo del catalogo comun no se puede apropiar ("%")', OLD.clave
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_catalogo_sistema_inmutable
    BEFORE UPDATE OR DELETE ON catalogo_atributo
    FOR EACH ROW
    EXECUTE FUNCTION proteger_catalogo_del_sistema();

-- ---------------------------------------------------------------------
-- Un atributo de tenant TIENE que declarar a que tipos aplica.
--
-- `aplica_todos = false` y cero filas en `catalogo_atributo_tipo` deja un
-- atributo que no se pregunta en ningun sitio y que el trigger de V48 rechaza
-- al intentar escribir su valor: existe en el catalogo y es inservible. Como
-- la declaracion vive en OTRA tabla, no se puede exigir con un CHECK; se
-- comprueba al escribir el valor, que es cuando duele.
--
-- Se implementa como funcion consultable para que el servicio pueda avisar
-- antes, en vez de dejar que el alta muera al final.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION atributo_sin_ambito(id_atributo bigint)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT NOT c.aplica_todos
       AND NOT EXISTS (SELECT 1 FROM catalogo_atributo_tipo t
                        WHERE t.id_catalogo_atributo = c.id_catalogo_atributo)
      FROM catalogo_atributo c
     WHERE c.id_catalogo_atributo = id_atributo;
$$;

COMMENT ON FUNCTION atributo_sin_ambito(bigint) IS
    'true si el atributo no aplica a ningun tipo de propiedad: existe y no se pregunta nunca.';

-- ---------------------------------------------------------------------
-- Evidencia.
-- ---------------------------------------------------------------------
-- OJO con los nombres de las variables: una llamada `del_sistema` hace
-- ambigua la referencia a la COLUMNA `del_sistema` dentro del mismo bloque, y
-- PostgreSQL aborta la migracion entera con "column reference is ambiguous".
-- Por eso las de aqui llevan nombres que ninguna columna usa.
DO $$
DECLARE
    comunes   bigint;
    sombras   bigint;
    huerfanos bigint;
BEGIN
    SELECT count(*) INTO comunes FROM catalogo_atributo WHERE del_sistema;

    SELECT count(*) INTO sombras
      FROM catalogo_atributo tenant
      JOIN catalogo_atributo sistema
        ON sistema.organizacion_id IS NULL AND sistema.clave = tenant.clave
     WHERE tenant.organizacion_id IS NOT NULL;

    SELECT count(*) INTO huerfanos
      FROM catalogo_atributo c
     WHERE atributo_sin_ambito(c.id_catalogo_atributo);

    IF sombras > 0 THEN
        RAISE EXCEPTION 'V55: % atributos de organizacion sombrean una clave del sistema', sombras;
    END IF;

    RAISE NOTICE 'V55: % atributos comunes protegidos; 0 sombras; % sin ambito declarado',
        comunes, huerfanos;
END $$;
