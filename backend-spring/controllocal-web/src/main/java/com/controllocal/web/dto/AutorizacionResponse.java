package com.controllocal.web.dto;

import com.controllocal.service.soporte.Autorizaciones;
import com.controllocal.service.soporte.Fechas;

import java.time.LocalDateTime;

/**
 * Constancia de la autorizacion de datos personales (D-27) para la ficha de
 * cliente y la de propietario. <b>ADITIVO</b>: no existe en la v1, asi que no
 * toca el contrato congelado — es un endpoint nuevo, no un campo nuevo en una
 * respuesta existente.
 *
 * <p>Lo que publica es exactamente lo que el encargo pidio mostrar: si hay
 * autorizacion y en que estado, cuando se registro y quien la registro. El
 * <b>canal no sale</b>: desde que lo sella el backend vale siempre
 * {@code FORMULARIO_BROX}, y un dato constante no informa de nada.
 *
 * @param estado         VIGENTE | REVOCADA | CADUCADA | SIN_REGISTRO | NO_VIGENTE
 * @param registradaEn   {@code LocalDateTime} como el resto del cable (la BD
 *                       guarda TIMESTAMPTZ; {@link Fechas} traduce)
 * @param registradaPor  nombre del usuario interno; {@code null} si el evento
 *                       no lo guarda o el rol ya no existe
 * @param versionAviso   version citada por el evento
 * @param versionVigente version vigente hoy. Viajan las dos porque el numero
 *                       <b>solo aporta valor operativo cuando difieren</b>:
 *                       ahi dice que esa persona autorizo contra un aviso
 *                       anterior. Si coinciden, la pantalla no lo muestra.
 */
public record AutorizacionResponse(String estado, LocalDateTime registradaEn, String registradaPor,
                                   String versionAviso, String versionVigente) {

    public static AutorizacionResponse desde(Autorizaciones.Constancia constancia) {
        return new AutorizacionResponse(constancia.estado(),
                Fechas.local(constancia.registradaEn()), constancia.registradaPor(),
                constancia.versionAviso(), constancia.versionVigente());
    }
}
