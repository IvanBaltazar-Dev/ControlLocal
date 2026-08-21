-- V67 · El piso tenia dos duenos, y el alta universal lo enseno preguntandolo dos veces
--
-- QUE PASABA
--
-- El concepto "en que piso esta la unidad" existia por dos caminos distintos:
--
--   catalogo_atributo.piso        destino = ATRIBUTO   -> atributo_propiedad
--   GuionRegistroPropiedad.pisoUnidad  (estructural)   -> propiedad.piso
--
-- Los dos aplicaban a los mismos tres tipos (local, oficina, departamento).
-- V48 creo el primero al llevarse al catalogo las columnas de subtipo, y nadie
-- retiro el segundo. D-E4-3 (V60-V62) reviso claves CONTRA columnas y no vio
-- esto, porque aqui las claves son DOS -- `piso` y `pisoUnidad` -- y cada una
-- declaraba una sola autoridad. El defecto no estaba dentro de una clave: era
-- que dos claves nombraban lo mismo.
--
-- Estuvo invisible mientras la pantalla de alta era el formulario de local
-- comercial, que dibujaba una sola de las dos. El alta universal las pinta
-- juntas porque no elige: pinta lo que el motor publica. Y aparecieron dos
-- campos "Piso" seguidos.
--
-- QUE HACE ESTA MIGRACION
--
-- Declara UNA autoridad, la que ya usaba el resto del sistema: la columna
-- `propiedad.piso`, que leen la ficha, el listado heredado y el detector de
-- duplicados. `piso` pasa a ESTRUCTURAL con concepto PISO, igual que METRAJE en
-- V60, y `EscritorEstructural` aprende a enrutarlo.
--
-- La clave del CONTRATO no se mueve: para el cliente sigue existiendo `piso`
-- con su rotulo y su aplicabilidad. Lo que cambia es donde vive el valor -- que
-- es exactamente la promesa de D-E4-3: la autoridad fisica cambia, el contrato
-- logico no.
--
-- La clave `pisoUnidad` deja de publicarse desde el guion. No hay dato que
-- migrar por ese lado: era la MISMA columna.

-- ------------------------------------------------------------------
-- 1. El catalogo admite un concepto estructural mas
-- ------------------------------------------------------------------

ALTER TABLE catalogo_atributo
    DROP CONSTRAINT IF EXISTS ck_catalogo_campo_estructural;

ALTER TABLE catalogo_atributo
    ADD CONSTRAINT ck_catalogo_campo_estructural
    CHECK (campo_estructural IS NULL OR campo_estructural IN ('METRAJE', 'PISO'));

-- ------------------------------------------------------------------
-- 2. Los valores que hubiera en la tabla de atributos vuelven a su columna
--
--    Se hace ANTES de cambiar el destino: mientras `piso` sea ATRIBUTO, la
--    fila es la autoridad y es de donde hay que leer. Al reves se copiaria
--    desde la columna, que es la que puede estar vacia.
--
--    Solo se rellena donde la columna NO tiene ya valor: si los dos caminos
--    escribieron, manda la columna, que es la que llevan leyendo la ficha y el
--    listado desde V4. Sobrescribirla cambiaria lo que hoy se ve en pantalla.
-- ------------------------------------------------------------------

UPDATE propiedad p
   SET piso = a.valor_texto
  FROM atributo_propiedad a
 WHERE a.id_propiedad = p.id_propiedad
   AND a.clave = 'piso'
   AND a.valor_texto IS NOT NULL
   AND btrim(a.valor_texto) <> ''
   AND (p.piso IS NULL OR btrim(p.piso) = '');

DELETE FROM atributo_propiedad WHERE clave = 'piso';

-- ------------------------------------------------------------------
-- 3. La autoridad queda declarada en el catalogo, que es donde se lee
-- ------------------------------------------------------------------

UPDATE catalogo_atributo
   SET destino = 'ESTRUCTURAL', campo_estructural = 'PISO'
 WHERE clave = 'piso';

COMMENT ON COLUMN propiedad.piso IS
    'Autoridad unica del concepto PISO desde V67. Se escribe por la clave de '
    'catalogo `piso`; `pisoUnidad` se retiro del guion de captura porque '
    'nombraba este mismo dato por un segundo camino.';
