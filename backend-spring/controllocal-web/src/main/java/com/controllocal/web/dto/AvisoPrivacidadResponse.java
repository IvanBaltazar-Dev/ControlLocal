package com.controllocal.web.dto;

import java.time.OffsetDateTime;

/**
 * Version vigente del aviso de privacidad. NO forma parte del contrato
 * congelado: es aditivo de la v2 (D-27), porque la v1 no tiene aviso.
 *
 * @param version        el identificador que queda citado en cada autorizacion
 * @param vigenteDesde   desde cuando rige
 * @param cambioMaterial si esta version cambio el tratamiento de fondo. Cuando
 *                       es true, las autorizaciones otorgadas contra versiones
 *                       anteriores dejan de estar vigentes y se vuelven a pedir
 * @param contenido      el texto que se muestra y que se guarda como evidencia
 */
public record AvisoPrivacidadResponse(String version, OffsetDateTime vigenteDesde,
                                      boolean cambioMaterial, String contenido) {
}
