package com.controllocal.web.dto;

import java.time.LocalDate;

/**
 * Contrato CONGELADO: espejo de Dtos.ComisionCobroRequest de la v1. El broker
 * supervisor registra el desenlace del cobro: C = cobrada (con fecha y forma
 * de pago) o A = anulada. El estado conserva el codigo de un caracter del
 * contrato REST; la forma de pago sigue siendo texto.
 */
public record ComisionCobroRequest(String estado, LocalDate fechaCobro, String formaPago) {
}
