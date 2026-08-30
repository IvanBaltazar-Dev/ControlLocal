-- =====================================================================
-- V88 - El ALTA declara su responsable, y lo deja en el expediente
-- =====================================================================
--
-- QUE ANADE. `V87` creo `propiedad.id_rol_responsable` y el rastro de
-- traspasos, pero el responsable que fija el ALTA no dejaba fila: la
-- columna aparecia poblada y el expediente no decia de donde salio. Un
-- valor de autoridad sin acto que lo explique es exactamente lo que este
-- P0 vino a quitar.
--
-- LA DECISION (titular, 2026-08-30). Cuando un agente registra una
-- propiedad REALMENTE NUEVA, queda como su responsable inicial Y el alta
-- escribe su fila append-only, trazada como originada por el ALTA.
--
-- EL LIMITE, que es la mitad importante. Esto vale UNICA Y
-- EXCLUSIVAMENTE cuando nace una fila de `propiedad`. Que otro agente
-- vuelva a captar una propiedad existente, abra un ENCARGO nuevo, la
-- retome o la vuelva a trabajar NO lo convierte en responsable. Una
-- propiedad que ya existe solo cambia de responsable por TRASPASO
-- autorizado por BROKER; y una propiedad historica FALTANTE sigue
-- FALTANTE hasta que un BROKER asigne.
--
-- POR QUE UNA COLUMNA `origen` Y NO DEDUCIRLO. Porque no se puede
-- deducir. La tentacion es leer "sin predecesor" como "esto fue el
-- alta", y es falso: la PRIMERA asignacion de una propiedad FALTANTE
-- tampoco tiene predecesor. Medido en `controllocal_repositorios` el
-- 2026-08-30, antes de escribir una linea: de 63 filas, **12 no tienen
-- anterior** y las 63 son de BROKER, es decir traspasos. Deducir el
-- origen de ese NULL habria clasificado 12 traspasos como altas.
--
-- ADITIVA. No toca ninguna migracion aplicada -- `V87` incluida -- ni
-- ninguna fila de `propiedad`. NO rellena ningun responsable: las 26
-- propiedades de `dev` y las 13.078 de pruebas siguen FALTANTE.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. De donde sale esta asignacion.
--
-- El respaldo de las filas que ya existen no es una suposicion: las 63
-- las escribio `AutoridadDePropiedad.asignar`, que es el unico escritor
-- de la tabla (gate `unSoloEscritorDelRastroDeTraspasos`) y que rechaza a
-- un AGENTE. El CHECK de `V87` ya solo admitia BROKER y TENANT_ADMIN, asi
-- que "todo lo anterior es TRASPASO" es una lectura del esquema, no del
-- caso frecuente.
-- ---------------------------------------------------------------------
ALTER TABLE asignacion_responsable_propiedad
    ADD COLUMN origen VARCHAR(8) NOT NULL DEFAULT 'TRASPASO';

-- Y se le quita el DEFAULT: a partir de aqui el origen se DECLARA. Con
-- defecto, una insercion que lo olvidara quedaria archivada como traspaso
-- sin que nadie lo notara -- la misma razon por la que `motivo_operacion`
-- perdio el suyo en V4x.
ALTER TABLE asignacion_responsable_propiedad
    ALTER COLUMN origen DROP DEFAULT;

ALTER TABLE asignacion_responsable_propiedad
    ADD CONSTRAINT ck_asignacion_resp_origen
    CHECK (origen IN ('ALTA', 'TRASPASO'));


-- ---------------------------------------------------------------------
-- 2. Cada origen tiene su firma, y no se mezclan.
--
-- Se sustituye el CHECK de banda de `V87` por uno que dice las dos
-- formas legitimas enteras:
--
--   ALTA      la firma el AGENTE que registra, y NO tiene predecesor
--             -- no hay a quien desplazar: la fila acaba de nacer.
--   TRASPASO  lo firma el gobierno (BROKER o TENANT_ADMIN), y puede
--             tener predecesor o no (la primera asignacion de una
--             propiedad FALTANTE no lo tiene).
--
-- Escrito asi, la base impide por si sola la confusion que el titular
-- marco como limite critico: un AGENTE no puede firmar un TRASPASO.
-- ---------------------------------------------------------------------
ALTER TABLE asignacion_responsable_propiedad
    DROP CONSTRAINT ck_asignacion_resp_banda;

ALTER TABLE asignacion_responsable_propiedad
    ADD CONSTRAINT ck_asignacion_resp_banda
    CHECK (
        (origen = 'ALTA'
             AND tipo_rol_actor = 'AGENTE'
             AND id_rol_responsable_anterior IS NULL)
        OR
        (origen = 'TRASPASO'
             AND tipo_rol_actor IN ('BROKER', 'TENANT_ADMIN'))
    );


-- ---------------------------------------------------------------------
-- 3. UNA sola alta por propiedad. La invariante, en el esquema.
--
-- Este indice es la mitad estructural del limite critico: "detectar o
-- reutilizar una propiedad existente jamas debe ejecutar el alta del
-- responsable". Un comentario en el codigo lo pide; este indice lo
-- IMPIDE -- un segundo `origen = 'ALTA'` sobre la misma propiedad no
-- entra, venga del canal que venga y lo escriba quien lo escriba.
--
-- Parcial a proposito: los TRASPASOS son muchos por propiedad, y ese es
-- justamente el punto de que la tabla sea append-only.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX uq_asignacion_alta_por_propiedad
    ON asignacion_responsable_propiedad (id_propiedad)
    WHERE origen = 'ALTA';


COMMENT ON COLUMN asignacion_responsable_propiedad.origen IS
    'De donde sale esta asignacion. ALTA = la fijo el agente que registro '
    'una propiedad NUEVA (una sola por propiedad, garantizado por '
    'uq_asignacion_alta_por_propiedad). TRASPASO = la decidio un BROKER o '
    'el gobierno del tenant. No se deduce de id_rol_responsable_anterior: '
    'la primera asignacion de una propiedad FALTANTE tampoco tiene '
    'predecesor y es un TRASPASO.';
