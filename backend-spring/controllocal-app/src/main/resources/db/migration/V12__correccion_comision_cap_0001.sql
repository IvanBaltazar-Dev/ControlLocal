-- V12 - Correccion de DATO de la captacion demo CAP-0001.
--
-- Que estaba mal: V5 sembro CAP-0001 con comision_pactada = 4250.00, un valor
-- pensado como si el campo fuera un IMPORTE en soles. No lo es.
--
-- En el contrato vigente, `comision_pactada` es un PORCENTAJE que se aplica
-- sobre la RENTA MENSUAL. Lo fija ComisionServiceImpl.bruta():
--
--     comision = renta * comision_pactada / 100
--
-- Con 4250.00 la captacion demo declaraba una comision del 4250 % de la renta
-- —42,5 veces el alquiler—, y la ficha de propiedad lo mostraba tal cual, que
-- es lo correcto: el dato estaba mal, no la pantalla.
--
-- El valor correcto: la comision pactada de CAP-0001 equivale a UN MES
-- completo de alquiler, y bajo este contrato un mes de renta es 100.00.
--
--     50.00 -> medio mes de alquiler
--    100.00 -> un mes de alquiler
--    150.00 -> un mes y medio de alquiler
--
-- Alcance: SOLO la fila de CAP-0001 del tenant de legado. Se localiza por su
-- codigo, que es el identificador estable del negocio —el id autonumerico
-- depende del orden de insercion y no es fiable entre entornos—, pero el
-- codigo NO basta por si solo: desde V6 la unicidad es
-- (organizacion_id, codigo_captacion), asi que otro tenant puede tener su
-- propio CAP-0001 y no hay por que tocarlo. De ahi el filtro por organizacion.
--
-- No se edita V5 —una migracion ya aplicada no se toca— ni se altera el
-- esquema: esto es correccion de datos.
--
-- Idempotente: si el valor ya es 100.00, el UPDATE no afecta ninguna fila. Se
-- acota ademas por el valor anterior para no pisar una comision que alguien
-- haya corregido a mano en un entorno vivo.

UPDATE captacion c
   SET comision_pactada    = 100.00,
       fecha_actualizacion = NOW()
  FROM organizacion o
 WHERE c.organizacion_id  = o.id_organizacion
   AND o.codigo           = 'BROX_LEGACY'
   AND c.codigo_captacion = 'CAP-0001'
   AND c.comision_pactada = 4250.00;
