-- =====================================================================
-- V33: backfill de gobierno — usuario_organizacion pasa a ser la fuente
--      de verdad de la banda (D-S0-8, H-14).
--
-- Bloque 5. Va DESPUES de V32 (que admite el tipo ADMIN) y ANTES de V34
-- (que exige >= 1 administrador por organizacion, invariante que solo se
-- puede imponer cuando ya se cumple).
--
-- No se crea ninguna persona nueva y no se borra nada: el administrador
-- actual conserva su persona, su credencial, su nombre de usuario, su rol
-- de BROKER y su detalle_broker (D-S0-10). Lo unico que gana es un
-- persona_rol de gobierno y una banda explicita.
--
-- ---------------------------------------------------------------------
-- EL BACKFILL DE V6 ESTABA ROTO, Y ESTA MIGRACION LO REPARA
-- ---------------------------------------------------------------------
-- V6 (lineas 161-173) poblo usuario_organizacion asi:
--
--     LEFT JOIN detalle_broker db ON db.id_persona_rol = cu.id_persona_rol
--     CASE WHEN db.es_administrador THEN 'ADMIN'
--          WHEN db.id_persona_rol IS NOT NULL THEN 'BROKER'
--          ELSE 'AGENTE' END
--
-- Ese join no puede casar NUNCA. `cu.id_persona_rol` es el rol
-- USUARIO_INTERNO (ck_credencial_tipo_rol lo fuerza) y
-- `db.id_persona_rol` es el rol BROKER (ck_detalle_broker_tipo_rol lo
-- fuerza); son filas distintas de persona_rol, y uq_persona_rol_id_tipo
-- impide que un mismo id sea las dos cosas. Con el LEFT JOIN siempre
-- vacio, el CASE cae siempre al ELSE: **toda la tabla quedo con
-- rol = 'AGENTE'**, administrador y brokers incluidos.
--
-- Nadie lo detecto porque nadie la leia (H-14: "existe, esta poblada y el
-- codigo NO la usa"). Por eso este backfill no puede limitarse a voltear
-- los administradores: tiene que **reconstruir las tres bandas** desde la
-- fuente real, que es el rol operativo vigente de la MISMA PERSONA.
--
-- ---------------------------------------------------------------------
-- POR QUE NO SE RETIRA uq_broker_admin_unico AQUI
-- ---------------------------------------------------------------------
-- El plan pedia varios administradores por organizacion (§2.5) y eso YA
-- queda satisfecho: a partir de ahora un TENANT_ADMIN se define por su
-- membresia y su persona_rol de gobierno, y **no necesita tocar el
-- booleano**. El indice unico solo limita cuantos brokers cargan el flag
-- heredado, que es lo que lee GlassFish; retirarlo antes del corte no
-- habilita nada y desprotege a la v1. Muere con la columna, en V36.
--
-- Asimetria aceptada durante la convivencia: un segundo TENANT_ADMIN
-- gobierna en el backend v2 y para GlassFish es un usuario normal. Es
-- coherente con el resto del bloque — la v1 no se entera de nada de esto
-- (Plan S0 §3.4) — y desaparece en el corte.
-- =====================================================================

-- ---------------------------------------------------------------------
-- V33.1  Un persona_rol de tipo ADMIN por cada administrador actual
--
-- Es el rol que dara el `idDominio` del token (R2) y el que permite que
-- manana exista un administrador que no sea broker. Se crea sobre la
-- MISMA persona: no hay altas.
-- ---------------------------------------------------------------------
INSERT INTO persona_rol (organizacion_id, id_persona, tipo_rol, vigencia_desde)
SELECT rol_broker.organizacion_id, rol_broker.id_persona, 'ADMIN', CURRENT_DATE
FROM detalle_broker db
JOIN persona_rol rol_broker ON rol_broker.id_persona_rol = db.id_persona_rol
WHERE db.es_administrador
  AND rol_broker.vigencia_hasta IS NULL
  -- uq_persona_rol_vigente admite un solo rol vigente de cada tipo por
  -- persona; reejecutar esta migracion sobre una base ya migrada no debe
  -- reventar.
  AND NOT EXISTS (
      SELECT 1 FROM persona_rol ya
      WHERE ya.id_persona = rol_broker.id_persona
        AND ya.tipo_rol = 'ADMIN'
        AND ya.vigencia_hasta IS NULL
  );

-- ---------------------------------------------------------------------
-- V33.2  Reconstruir la banda de CADA membresia
--
-- `id_usuario` es el persona_rol del USUARIO_INTERNO (la cuenta); de ahi
-- se salta a la persona y se mira su rol operativo vigente. El orden del
-- CASE es el mismo que aplica el login: gobierno manda sobre supervision,
-- y supervision sobre operacion.
--
-- El ELSE 'AGENTE' no es un descarte perezoso: una credencial sin rol
-- operativo vigente no puede entrar (resolverIdentidad lanza), asi que
-- esa fila no decide ningun acceso.
-- ---------------------------------------------------------------------
UPDATE usuario_organizacion uo
SET rol = CASE
        WHEN EXISTS (
            SELECT 1
            FROM persona_rol rb
            JOIN detalle_broker db ON db.id_persona_rol = rb.id_persona_rol
            WHERE rb.id_persona = cuenta.id_persona
              AND rb.tipo_rol = 'BROKER'
              AND rb.vigencia_hasta IS NULL
              AND db.es_administrador
        ) THEN 'TENANT_ADMIN'
        WHEN EXISTS (
            SELECT 1
            FROM persona_rol rb
            WHERE rb.id_persona = cuenta.id_persona
              AND rb.tipo_rol = 'BROKER'
              AND rb.vigencia_hasta IS NULL
        ) THEN 'BROKER'
        ELSE 'AGENTE'
    END
FROM persona_rol cuenta
WHERE cuenta.id_persona_rol = uo.id_usuario
  AND cuenta.organizacion_id = uo.organizacion_id
  AND cuenta.tipo_rol = 'USUARIO_INTERNO';

-- ---------------------------------------------------------------------
-- V33.3  Fijar el vocabulario de la banda
--
-- Ahora que la columna decide accesos, deja de ser texto libre. PLATFORM_
-- ADMIN se admite desde ya aunque su mecanismo (concesion_acceso_tenant)
-- llegue despues: reservar el valor cuesta cero y evita otra migracion.
-- El valor 'ADMIN' de V6 NO se admite — despues de V33.2 no queda
-- ninguna fila con el, y dejarlo abierto conservaria justo la ambiguedad
-- que este bloque cierra.
-- ---------------------------------------------------------------------
ALTER TABLE usuario_organizacion ADD CONSTRAINT ck_usuario_org_rol CHECK (
    rol IN ('AGENTE', 'BROKER', 'TENANT_ADMIN', 'PLATFORM_ADMIN')
);

COMMENT ON COLUMN usuario_organizacion.rol IS
    'Banda AUTORITATIVA del usuario en el tenant (D-S0-8). Sustituye a la '
    'derivacion desde detalle_broker.es_administrador, que confundia el rol '
    'operativo con el gobierno. TENANT_ADMIN gobierna la organizacion y NO '
    'hereda la semantica comercial de BROKER (D-S0-7).';

-- ---------------------------------------------------------------------
-- V33.4  Comprobacion: ninguna organizacion con cuentas se queda sin
--        gobierno.
--
-- Es la precondicion de V34. Falla aqui, con el nombre de la
-- organizacion, en vez de dejar que reviente el trigger sin contexto.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    huerfanas TEXT;
BEGIN
    SELECT string_agg(o.id_organizacion::TEXT, ', ')
      INTO huerfanas
      FROM organizacion o
     WHERE EXISTS (SELECT 1 FROM usuario_organizacion uo
                    WHERE uo.organizacion_id = o.id_organizacion
                      AND uo.estado = 'A')
       AND NOT EXISTS (SELECT 1 FROM usuario_organizacion uo
                        WHERE uo.organizacion_id = o.id_organizacion
                          AND uo.estado = 'A'
                          AND uo.rol = 'TENANT_ADMIN');

    IF huerfanas IS NOT NULL THEN
        RAISE EXCEPTION 'V33: organizacion(es) % con cuentas activas y sin '
                        'ningun TENANT_ADMIN. El backfill no encontro ningun '
                        'detalle_broker.es_administrador vigente en ellas; '
                        'hay que designar el administrador antes de aplicar '
                        'el invariante de V34.', huerfanas;
    END IF;
END $$;
