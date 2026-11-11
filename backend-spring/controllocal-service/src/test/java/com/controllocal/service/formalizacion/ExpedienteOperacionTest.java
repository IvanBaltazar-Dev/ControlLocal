package com.controllocal.service.formalizacion;

import com.controllocal.domain.comercial.*;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

/** Invariantes propias, sin fingir que estas pruebas prueban JPA/rollback/concurrencia. */
class ExpedienteOperacionTest {
    @Test void reenvioCambiaSoloPunterosSinInventarOtroContextoNiOtraSolicitud(){
        var e=ExpedienteOperacion.abrir(1,10,20,30,null,null,OffsetDateTime.parse("2026-10-31T15:00:00Z"));
        e.presentar(100,30,40L,1L);e.revisar(101,true,true);
        e.presentar(102,31,40L,1L);
        assertEquals(10,e.getEncargo());assertEquals(20,e.getOportunidad());assertEquals(31,e.getRevisionPacto());
        assertNull(e.getSolicitudAlquiler());assertNull(e.getRevisionAplicable());assertNull(e.getRevisionFuente());
        assertEquals(101L,e.getRevisionEvaluada());
    }
    @Test void observarNoConservaUnaBaseAnteriorComoSiSiguieraAprobada(){
        var e=ExpedienteOperacion.abrir(1,10,20,30,50L,null,OffsetDateTime.parse("2026-10-31T15:00:00Z"));
        e.revisar(100,true,true);e.revisar(101,false,false);
        assertNull(e.getRevisionAplicable());assertNull(e.getRevisionFuente());assertEquals(101L,e.getRevisionEvaluada());
    }
    @Test void aprobarSinNuevaCifraNoHeredaElPrecioDeOtraRevision(){
        var e=ExpedienteOperacion.abrir(1,10,20,30,null,null,OffsetDateTime.parse("2026-10-31T15:00:00Z"));
        e.revisar(100,true,true);e.revisar(101,true,false);
        assertEquals(101L,e.getRevisionAplicable());assertNull(e.getRevisionFuente());
    }
    @Test void segundoCierreDiferenteNoReemplazaLaIdentidadEconomica(){
        var e=ExpedienteOperacion.abrir(1,10,20,30,null,null,OffsetDateTime.parse("2026-10-31T15:00:00Z"));
        e.vincularContratos(null,70L);e.vincularContratos(null,70L);e.vincularContratos(null,null);
        assertThrows(IllegalStateException.class,()->e.vincularContratos(null,71L));assertEquals(70L,e.getContratoVenta());
    }
    @Test void minutaYEscrituraConservanContextoYLaRectificacionConservaHito(){
        var minuta=HitoContractual.abrir(1,50,TipoHitoContractual.MINUTA);
        var escritura=HitoContractual.abrir(1,50,TipoHitoContractual.ESCRITURA);
        minuta.informar(100);minuta.confirmar(101);minuta.confirmar(102);
        assertEquals(minuta.getExpediente(),escritura.getExpediente());assertEquals(100L,minuta.getRevisionInformada());assertEquals(102L,minuta.getRevisionConfirmada());
    }
    @Test void renovacionRealConservaElPadreYSeleccionExplicitaNoCreaOtroContexto(){
        var e=ExpedienteOperacion.abrir(1,10,20,30,50L,60L,OffsetDateTime.parse("2026-10-31T15:00:00Z"));
        e.seleccionarContratoPreparado(70);e.presentar(100,30,null,null);e.revisar(101,true,true);
        e.seleccionarContratoPreparado(71);e.presentar(102,30,null,null);
        assertEquals(60L,e.getContratoOrigen());assertEquals(71L,e.getContratoAlquiler());assertNull(e.getRevisionAplicable());
        assertEquals(20,e.getOportunidad());assertEquals(50L,e.getSolicitudAlquiler());
    }
}
