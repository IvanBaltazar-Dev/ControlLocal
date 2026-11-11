package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.soporte.ValorLogico;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static com.controllocal.service.matching.ContratoMatching.*;
import static org.junit.jupiter.api.Assertions.*;

class ElegibilidadActualMatchingTest {
    private static final Instant CORTE=Instant.parse("2026-10-29T12:00:00Z");
    private final MotorMatchingDeterminista motor=new MotorMatchingDeterminista();
    private Consulta consulta(){return new Consulta("demanda:1",1,Modalidad.COMERCIAL,List.of(),CORTE,"D1-D4");}
    private Oferta oferta(Instant t0,boolean acceso){return new Oferta("oferta",new ClaveCandidato("org","oferta","unidad"),1,
            acceso,Conclusion.CUMPLE,new ConfirmacionDisponibilidad(t0,false,"hecho"),List.of());}
    @Test void reconfirmacionPosteriorAlCorteNoSeInventaComoFechaOriginalNiSeRechazaComoFuturaAlEntregar() {
        var actual=CORTE.plusSeconds(120);var oferta=oferta(CORTE.plusSeconds(60),true);
        assertEquals(Conclusion.NO_DETERMINADO,motor.evaluar(consulta(),oferta).g());
        var resultado=motor.evaluar(consulta(),oferta,actual);
        assertEquals(Conclusion.CUMPLE,resultado.g());assertEquals(CORTE,resultado.corte());
    }
    @Test void caducidadActualNoQuedaCongeladaPorElCorteDeNavegacion() {
        var oferta=oferta(CORTE.minus(Duration.ofDays(37)).plusSeconds(60),true);
        assertEquals(Conclusion.CUMPLE,motor.evaluar(consulta(),oferta).g());
        assertEquals(Causa.CONFIRMACION_NO_VIGENTE,motor.evaluar(consulta(),oferta,CORTE.plusSeconds(120)).disponibilidad().causa());
    }
    @Test void datoPosteriorNoSeVuelveConocidoRetrospectivamentePorActualizarElRelojD2() {
        var contexto=new ContextoValor(null,null,null,null,null,"unidad");
        var conocido=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var condicion=new Condicion("ascensor",TipoDato.BOOLEANO,OperadorCriterio.VERDADERO,ValorLogico.deBooleano(true),null,false,
                new ContextoValor(null,null,null,null,null,null),conocido);
        var q=new Consulta("demanda:1",1,Modalidad.COMERCIAL,List.of(new Criterio("ascensor","ascensor",Exigencia.OBLIGATORIO,null,List.of(condicion))),CORTE,"D1-D4");
        var dato=new Dato("ascensor",TipoDato.BOOLEANO,ValorLogico.deBooleano(true),null,false,contexto,
                new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,CORTE.plusSeconds(60),null));
        var oferta=new Oferta("oferta",new ClaveCandidato("org","oferta","unidad"),1,true,Conclusion.CUMPLE,
                new ConfirmacionDisponibilidad(CORTE.plusSeconds(60),false,"hecho"),List.of(dato));
        var r=motor.evaluar(q,oferta,CORTE.plusSeconds(120));
        assertEquals(Conclusion.CUMPLE,r.g());assertEquals(Conclusion.NO_DETERMINADO,r.h());
        assertEquals(Causa.VIGENCIA_NO_ACREDITADA,r.criterios().getFirst().resultado().causa());
    }
    @Test void confirmacionFuturaInclusoAlInstanteActualSigueIndeterminada() {
        assertEquals(Conclusion.NO_DETERMINADO,motor.evaluar(consulta(),oferta(CORTE.plusSeconds(121),true),CORTE.plusSeconds(120)).g());
    }
    @Test void revalidarTiempoNoAmpliaAcceso() {
        assertThrows(AccesoNoAutorizadoException.class,()->motor.evaluar(consulta(),oferta(CORTE,false),CORTE.plusSeconds(120)));
    }
}
