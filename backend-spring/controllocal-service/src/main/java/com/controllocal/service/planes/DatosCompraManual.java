package com.controllocal.service.planes;

import com.controllocal.domain.planes.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** CAT-E38/directriz6.4: contrato comercial, sin tokens, tarifas o costos de IA. */
public final class DatosCompraManual {
    private DatosCompraManual(){}
    public enum Estado { PENDIENTE, EN_VERIFICACION, ACREDITADA, CON_INCIDENCIA }
    public record Seleccion(ComprasVerificadasB0.Tipo tipo,PlanBrox plan,PaqueteBoost boost,int brokersExtra){}
    public record Terminos(Seleccion producto,String versionOferta,Instant corte,UUID cicloEsperado,Long versionCicloEsperada,
            BigDecimal periodoNuevoUsd,BigDecimal abonoTemporalUsd,BigDecimal totalUsd,String moneda,
            Instant inicioProgramado,BigDecimal siguienteMensualidadUsd,String reglaInicio){}
    public record Confirmacion(UUID comando,long version){}
    /** Fecha conocida sin hora no se transforma en medianoche ni en un vencimiento ficticio. */
    public record FechaPago(LocalDate fecha,Instant instante,String procedencia){
        public void validar(Instant ahora){
            if(fecha==null||fecha.isAfter(ahora.atZone(CalendarioComercial.ZONA).toLocalDate())
                    ||procedencia==null||procedencia.isBlank()||procedencia.length()>1000
                    ||instante!=null&&(instante.isAfter(ahora)||!fecha.equals(instante.atZone(CalendarioComercial.ZONA).toLocalDate())))
                throw new ReglaNegocioException("Conserva la fecha real del pago y su procedencia, sin inventar una hora.");
        }
        public Instant objetivoVerificacion(){return instante==null?null:instante.plus(Duration.ofHours(24));}
    }
    public record PagoInformado(FechaPago fecha,String referenciaInformada){}
    public record Verificacion(FechaPago fecha,String origen,String referencia,BigDecimal importe,String moneda,String evidencia){}
    public record Fondos(Verificacion verificacion,long cuenta,UUID concesion,Instant verificadosEn){}
    public record Evento(String tipo,Instant registradaEn,String estado){}
    public record FechaPagoComercial(LocalDate fecha,Instant instante,Instant objetivoVerificacion){}
    public record Compra(UUID referencia,long version,Terminos terminos,Estado estado,boolean consentida,
            Instant registradaEn,FechaPagoComercial pago,Instant verificadaEn,String incidencia,List<Evento> historial){}
    public record Apertura(Compra compra,String enlaceWhatsApp,String mensaje){}
    public record Producto(String codigo,String nombre,BigDecimal precioUsd,String moneda,Long creditosBoost,Integer almacenamientoGb){}
    public record DetalleFinanciero(Compra compra,PagoInformado pagoInformado,Fondos fondos){}
}
