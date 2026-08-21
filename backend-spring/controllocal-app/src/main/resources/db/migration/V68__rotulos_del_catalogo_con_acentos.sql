-- V68 · Los rotulos del catalogo son texto que LEE una persona
--
-- `catalogo_atributo.rotulo` y `unidad` no son vocabulario tecnico: son lo que
-- aparece escrito encima de un campo en la pantalla de alta y lo que KAIROS
-- dira en voz alta. Se sembraron sin acentos en V48 --como el resto del codigo
-- fuente, que los evita a proposito-- y ahi el criterio no aplica: nadie
-- escribe "Banos" ni "Antiguedad" en un formulario que va a ver un broker.
--
-- Estuvo invisible porque la pantalla de alta anterior no los usaba: dibujaba
-- sus propias etiquetas, escritas a mano en Angular. El alta universal pinta el
-- rotulo que el catalogo declara, asi que ahora se leen tal cual estan.
--
-- La CLAVE no se toca. `banos` sigue siendo `banos`: es un identificador, viaja
-- por el cable y lo comparan el matcher y los indices. Lo que cambia es como se
-- llama de cara a quien lo lee, que es justo lo que un rotulo es.
--
-- `anios` se corrige ademas porque no es una unidad: es "años" mal escrito.

UPDATE catalogo_atributo SET rotulo = 'Antigüedad'              WHERE clave = 'antiguedad_anios'  AND organizacion_id IS NULL;
UPDATE catalogo_atributo SET rotulo = 'Área de terreno'         WHERE clave = 'area_terreno'      AND organizacion_id IS NULL;
UPDATE catalogo_atributo SET rotulo = 'Baños'                   WHERE clave = 'banos'             AND organizacion_id IS NULL;
UPDATE catalogo_atributo SET rotulo = 'Carga eléctrica'         WHERE clave = 'carga_electrica_kw' AND organizacion_id IS NULL;
UPDATE catalogo_atributo SET rotulo = 'Pisos de la edificación' WHERE clave = 'pisos_edificacion' AND organizacion_id IS NULL;
UPDATE catalogo_atributo SET rotulo = 'Zonificación'            WHERE clave = 'zonificacion'      AND organizacion_id IS NULL;

UPDATE catalogo_atributo SET unidad = 'años' WHERE unidad = 'anios';
