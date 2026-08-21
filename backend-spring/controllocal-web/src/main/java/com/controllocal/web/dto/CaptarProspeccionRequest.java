package com.controllocal.web.dto;

import com.controllocal.service.ProspeccionService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * <b>Lo que se pacta cuando el propietario acepta</b> (V75).
 *
 * <p>Este endpoint es donde <b>nace el Encargo</b> desde el 2026-08-21: la
 * propiedad puede existir sin estar encargada, y el embudo dice
 * {@code propietario -> prospeccion -> ENCARGO -> publicacion}.
 *
 * <p>Por eso el cuerpo crecio. Antes solo llevaba {@code comisionPactada} y el
 * servidor rellenaba el resto: la operacion siempre ALQUILER, el importe copiado
 * de {@code propiedad.precio_referencial}. Con eso, una propiedad captada para
 * VENDERSE nacia con un encargo de alquiler y su historico economico entero bajo
 * la operacion equivocada — sin excepcion y sin aviso.
 *
 * <p><b>La operacion no tiene defecto</b>, y el importe tampoco se hereda: es
 * lo que el propietario acaba de aceptar, y una propiedad que solo se estaba
 * prospectando no tiene precio del que tirar.
 *
 * @param operacion       {@code "VENTA"} o {@code "ALQUILER"}, con palabras
 * @param importe         precio de venta o renta mensual, segun la operacion
 * @param comisionPactada porcentaje; 100 es una mensualidad en un alquiler
 * @param tipoComision    opcional; por defecto el que la operacion implica
 * @param baseCalculo     opcional; renta mensual en alquiler, precio en venta
 */
public record CaptarProspeccionRequest(String operacion, BigDecimal importe, String moneda,
                                       BigDecimal comisionPactada, String tipoComision,
                                       String baseCalculo, String tratamientoIgv,
                                       Boolean exclusividad, LocalDate inicioEncargo,
                                       LocalDate finEncargo) {

    public ProspeccionService.DatosCaptura aDatos() {
        return new ProspeccionService.DatosCaptura(operacion, importe, moneda, comisionPactada,
                tipoComision, baseCalculo, tratamientoIgv, exclusividad, inicioEncargo,
                finEncargo);
    }
}
