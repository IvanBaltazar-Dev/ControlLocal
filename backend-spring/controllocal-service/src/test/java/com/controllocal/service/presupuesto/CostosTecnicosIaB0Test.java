package com.controllocal.service.presupuesto;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Aritmética contractual y disponibilidad; no pruebaSQL/financiación o proveedor real. */
class CostosTecnicosIaB0Test {
    final UUID op=UUID.randomUUID();
    BigDecimal n(String x){return new BigDecimal(x);}
    LineaCosto linea(String original,String confirmado){return new LineaCosto(op,"EN_PROCESO",n("1"),null,UUID.randomUUID(),n(original),confirmado==null?null:n(confirmado),confirmado==null?null:UUID.randomUUID());}
    @Test void agregaNativosAntesDeCuantizarUnaVez(){
        assertEquals(n("0.000000000001"),CostosTecnicosIaB0.total(List.of(linea("0.0000000000006",null),linea("0.0000000000006",null))));
    }
    @Test void confirmacionParcialDeConjuntoSustituyeSoloMedicionCubierta(){
        var a=linea("0.10","0.08");var b=linea("0.20",null);
        assertEquals(n("0.280000000000"),CostosTecnicosIaB0.total(List.of(a,b)));
        assertEquals(n("0.330000000000"),CostosTecnicosIaB0.total(List.of(a,b,linea("0.05",null))));
    }
    @Test void ceroConfirmadoEsUnValorYAusenciaConservaElCalculado(){
        assertEquals(n("0.100000000000"),CostosTecnicosIaB0.total(List.of(linea("0.10",null))));
        assertEquals(n("0.000000000000"),CostosTecnicosIaB0.total(List.of(linea("0.10","0"))));
    }
    @Test void abiertaConservaMaximoYCerradaSoloDiferenciaNoImputada(){
        assertEquals(n("0.2"),CostosTecnicosIaB0.pendiente("EN_PROCESO",n("1"),n("1.2"),n("0")));
        assertEquals(n("0.1"),CostosTecnicosIaB0.pendiente("LIQUIDADA",n("1"),n("0.3"),n("0.2")));
        assertEquals(0,CostosTecnicosIaB0.pendiente("LIQUIDADA",n("1"),n("0.1"),n("0.2")).signum());
    }
    @Test void deudaPositivaRestaDisponibleSinConvertirNegativoPendienteEnSaldo(){
        var repo=mock(PresupuestoIaRepository.class);var fondo=UUID.randomUUID();
        var costos=new CostosTecnicosIaB0(repo,new Documentos(new ObjectMapper()));
        when(repo.pendienteCostoFondo(fondo)).thenReturn(n("0.02"));
        var f=new Fondo(fondo,n("1.1"),n("0"),n("1"),Instant.EPOCH,Instant.MAX,true);
        assertThrows(CapacidadDeshabilitadaException.class,()->costos.exigirDisponible(f,n("0.09")));
        assertDoesNotThrow(()->costos.exigirDisponible(f,n("0.08")));
        // Aunque el efectivo pudiera bajar, hasta aplicar el agregado sigue imputado1.
        when(repo.pendienteCostoFondo(fondo)).thenReturn(BigDecimal.ZERO);
        assertThrows(CapacidadDeshabilitadaException.class,()->costos.exigirDisponible(f,n("0.11")));
    }
    @Test void nativoAusenteNoSeConvierteEnCostoCero(){
        var sinCosto=new LineaCosto(op,"EN_PROCESO",n("1"),null,UUID.randomUUID(),null,null,null);
        assertThrows(NullPointerException.class,()->CostosTecnicosIaB0.total(List.of(sinCosto)));
    }
}
