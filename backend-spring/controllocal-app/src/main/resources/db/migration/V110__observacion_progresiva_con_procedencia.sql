-- RF013 B2.5 / AC054 y CAT-E09: el importe observado es opcional, nunca un precio autorizado.
alter table observacion_mercado alter column importe drop not null, alter column moneda drop not null;
alter table observacion_mercado add constraint ck_observacion_dinero_completo check(
    (importe is null and moneda is null) or
    (importe is not null and moneda is not null and importe>=0 and moneda in ('PEN','USD')));
alter table observacion_mercado add column procedencia text;
alter table observacion_mercado add constraint ck_observacion_procedencia_json check(
    procedencia is null or jsonb_typeof(procedencia::jsonb)='object');
-- Las filas sin procedencia histórica no reciben un origen retrospectivo inventado.
alter table observacion_mercado drop constraint ck_observacion_fecha;
alter table observacion_mercado add constraint ck_observacion_fecha check(
    fecha_observada <= (statement_timestamp() at time zone 'America/Lima')::date);
