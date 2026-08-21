-- V69 · Las unidades del catálogo, tal como se leen en una ficha
--
-- Las descubrió la ficha universal al pintarlas por primera vez. Mientras nadie
-- las enseñaba se podía vivir con ellas; en cuanto salen a pantalla dicen cosas
-- que no son.
--
-- 1. `cuota_mantenimiento` tenía unidad = 'moneda', y la ficha escribía
--    «450 moneda». No es una unidad: es un IMPORTE, y su moneda depende de la
--    propiedad, no del catálogo. Una cuota en soles y otra en dólares comparten
--    la clave y no comparten unidad, así que el catálogo no puede declararla.
--    Se deja en NULL — la ficha escribe «450», que es exactamente lo que se
--    sabe.
--
-- 2. `m2` se escribía tal cual junto a la cifra: «120 m2». El metro cuadrado se
--    escribe m², y la ficha no puede arreglarlo sin volver a tener una tabla de
--    traducción en el cliente — que es justo lo que D-A-1 §6 prohíbe. Se
--    corrige donde vive.
--
-- Mismo criterio que V68, que puso los acentos en los rótulos: el catálogo es
-- el dueño del texto, y el texto tiene que estar bien escrito EN el catálogo.

UPDATE catalogo_atributo
   SET unidad = NULL
 WHERE unidad = 'moneda';

UPDATE catalogo_atributo
   SET unidad = 'm²'
 WHERE unidad = 'm2';
