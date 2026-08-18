-- =====================================================================
-- V63 - Una coincidencia de cartera deja de ser una tarea (E2.3).
--
-- El septimo disparador de la bandeja creaba una tarea PROPONER_OPORTUNIDAD por
-- cada coincidencia encontrada. Desde E2.3 esas coincidencias viven en
-- `hallazgos[]`, que se CALCULA al pedirlo y no guarda nada: un hallazgo es una
-- lectura del estado de hoy, no un hecho ocurrido.
--
-- QUE HACER CON LAS QUE YA ESTAN ESCRITAS
-- Se retiran, y se retiran como CANCELADAS, no como COMPLETADAS.
--
-- La diferencia no es cosmetica. `REQUERIMIENTO` esta en ENTIDADES_AUTO, asi que
-- al dejar de derivarse el propio servicio las habria marcado COMPLETADA en la
-- siguiente lectura de la bandeja -- y eso seria falso: nadie las completo,
-- nadie propuso esas oportunidades. El historico diria que el agente resolvio 22
-- asuntos que en realidad se le retiraron de la mesa.
--
-- CANCELADA ('A') ademas BLOQUEA la recreacion para siempre (regla de la bandeja,
-- seccion 5.2), que aqui es exactamente lo que se quiere: no van a volver a
-- derivarse nunca, porque el disparador ya no existe.
--
-- LAS CERRADAS NO SE TOCAN
-- Una PROPONER_OPORTUNIDAD que alguien completo de verdad -- porque propuso la
-- oportunidad -- es historia cierta y se queda como esta. Solo se retiran las
-- que siguen abiertas.
-- =====================================================================

DO $$
DECLARE
    retiradas bigint;
    abiertas  bigint;
BEGIN
    SELECT count(*) INTO abiertas
      FROM tarea
     WHERE tipo = 'PROPONER_OPORTUNIDAD' AND estado IN ('P', 'E');

    UPDATE tarea
       SET estado = 'A',
           fecha_actualizacion = now()
     WHERE tipo = 'PROPONER_OPORTUNIDAD'
       AND estado IN ('P', 'E');
    GET DIAGNOSTICS retiradas = ROW_COUNT;

    IF retiradas <> abiertas THEN
        RAISE EXCEPTION 'V63: se esperaban % tareas por retirar y se retiraron %',
            abiertas, retiradas;
    END IF;

    RAISE NOTICE 'V63: % coincidencias retiradas de la bandeja; ahora son hallazgos', retiradas;
END $$;

-- ---------------------------------------------------------------------
-- Evidencia: ninguna coincidencia sigue abierta como tarea.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    quedan bigint;
BEGIN
    SELECT count(*) INTO quedan
      FROM tarea
     WHERE tipo = 'PROPONER_OPORTUNIDAD' AND estado IN ('P', 'E');

    IF quedan > 0 THEN
        RAISE EXCEPTION 'V63: quedan % coincidencias abiertas como tarea', quedan;
    END IF;
END $$;
