package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.soporte.ValorLogico;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static com.controllocal.service.matching.ContratoMatching.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextoTerritorialMatchingTest {
    private final MotorMatchingDeterminista motor=new MotorMatchingDeterminista();
    private final Instant corte=Instant.parse("2026-10-29T08:00:00Z");
    private static ContextoValor territorial(String jurisdiccion,String perfil,String version) {
        return new ContextoValor(null,null,null,null,"VENTA",null,jurisdiccion,perfil,version);
    }
    private ResultadoCondicion evaluar(ContextoValor pedido,ContextoValor oferta,String codigo,EstadoDato estado) {
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:12:4",4L,null,null,null,null,null);
        var condicion=new Condicion(ZONIFICACION,TipoDato.TEXTO,OperadorCriterio.IGUAL,
                ValorLogico.deTexto("RDM"),null,false,pedido,meta);
        var criterio=new Criterio("zonificacion",ZONIFICACION,Exigencia.OBLIGATORIO,null,List.of(condicion));
        var dato=new Dato(ZONIFICACION,TipoDato.TEXTO,ValorLogico.deTexto(codigo),null,false,oferta,
                new MetadatosDato(estado,null,"CAT:D:12:4",4L,null,null,null,null,null));
        return motor.evaluarCriterio(criterio,List.of(dato),corte);
    }
    @Test void mismoContextoYCodigoConfirma() {
        assertEquals(Conclusion.CUMPLE,evaluar(territorial("J1","CAT:P:1:2","F1"),territorial("J1","CAT:P:1:2","F1"),"RDM",EstadoDato.CONOCIDO).conclusion());
    }
    @Test void codigoIgualOtraJurisdiccionNoSeCompara() {
        assertEquals(Causa.CONTEXTO_TERRITORIAL_INCOMPATIBLE,evaluar(territorial("J1","CAT:P:1:2","F1"),territorial("J2","CAT:P:9:2","F1"),"RDM",EstadoDato.CONOCIDO).causa());
    }
    @Test void versionFuenteDiferenteNoSeReinterpreta() {
        assertEquals(Causa.CONTEXTO_TERRITORIAL_INCOMPATIBLE,evaluar(territorial("J1","CAT:P:1:2","F1"),territorial("J1","CAT:P:1:3","F2"),"RDM",EstadoDato.CONOCIDO).causa());
    }
    @Test void datoL2NoNecesitaDivulgarPerfilPrivado() {
        assertEquals(Conclusion.CUMPLE,evaluar(territorial("J1","CAT:P:1:2","F1"),territorial("J1",null,"F1"),"RDM",EstadoDato.CONOCIDO).conclusion());
    }
    @Test void diferentesPerfilesPrivadosNoSonDiferentesNormasPorSuPk() {
        assertEquals(Conclusion.CUMPLE,evaluar(territorial("J1","CAT:P:1:2","F1"),territorial("J1","CAT:P:99:8","F1"),"RDM",EstadoDato.CONOCIDO).conclusion());
    }
    @Test void constructorNoTerritorialNoAcreditaZonificacion() {
        var sin=new ContextoValor(null,null,null,null,"VENTA",null);
        assertEquals(Causa.CONTEXTO_TERRITORIAL_INCOMPATIBLE,evaluar(sin,sin,"RDM",EstadoDato.CONOCIDO).causa());
    }
    @Test void codigoDistintoEnMismoContextoIncumpleSinFuzzy() {
        assertEquals(Conclusion.INCUMPLE,evaluar(territorial("J1","CAT:P:1:2","F1"),territorial("J1",null,"F1"),"RDB",EstadoDato.CONOCIDO).conclusion());
    }
    @Test void fuenteTerritorialNoRellenaUnDatoFaltante() {
        assertEquals(Conclusion.NO_DETERMINADO,evaluar(territorial("J1","CAT:P:1:2","F1"),territorial("J1",null,"F1"),null,EstadoDato.FALTANTE).conclusion());
    }
}
