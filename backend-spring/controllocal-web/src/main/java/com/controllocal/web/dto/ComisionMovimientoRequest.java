package com.controllocal.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ComisionMovimientoRequest(String tipo, BigDecimal monto, String moneda,
                                        LocalDate fecha, String formaPago, String observacion) {
}
