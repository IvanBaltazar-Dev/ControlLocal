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

/** Aprobación L1 29/10: el alcance parcial no afirma compatibilidad económica por omisión. */
class CoberturaParcialMatchingTest {
    private final MotorMatchingDeterminista motor=new MotorMatchingDeterminista();
    private final Instant corte=Instant.parse("2026-10-29T12:00:00Z");
    private final ContextoValor contexto=new ContextoValor(null,null,null,null,null,null);
    private final MetadatosDato conocido=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
    private Consulta consulta(List<Criterio> criterios){return new Consulta("demanda",1,Modalidad.COMERCIAL,criterios,corte,"D1-D4",CoberturaCriterios.PARCIAL);}
    private Oferta oferta(List<Dato> datos){return new Oferta("oferta",new ClaveCandidato("org","encargo","unidad"),1,true,
            Conclusion.CUMPLE,new ConfirmacionDisponibilidad(corte,false,"confirmacion"),datos);}
    private Criterio criterio(String clave,Exigencia exigencia){return new Criterio(clave,clave,exigencia,
            exigencia==Exigencia.PREFERENCIA?Prioridad.NORMAL:null,List.of(new Condicion(clave,TipoDato.BOOLEANO,
            OperadorCriterio.VERDADERO,ValorLogico.deBooleano(true),null,false,contexto,conocido)));}
    private Dato dato(String clave,boolean valor){return new Dato(clave,TipoDato.BOOLEANO,ValorLogico.deBooleano(valor),null,false,contexto,conocido);}
    @Test void alcanceParcialSinCondicionesVisiblesNoEquivaleACompatibilidadCompleta() {
        var e=motor.evaluar(consulta(List.of()),oferta(List.of()));
        assertEquals(Conclusion.NO_DETERMINADO,e.h());assertEquals(Categoria.OBLIGATORIAS_PENDIENTES,e.categoria());
        assertEquals(CoberturaCriterios.PARCIAL,e.coberturaCriterios());assertTrue(e.criterios().isEmpty());
        assertEquals(0,e.w()); // No se inventa ni contabiliza un criterio financiero oculto.
    }
    @Test void fisicosFavorablesYPreferenciasNoConfirmanLoNoEvaluado() {
        var e=motor.evaluar(consulta(List.of(criterio("acceso",Exigencia.OBLIGATORIO),criterio("ascensor",Exigencia.PREFERENCIA))),
                oferta(List.of(dato("acceso",true),dato("ascensor",true))));
        assertEquals(Conclusion.NO_DETERMINADO,e.h());assertEquals(2,e.s());assertEquals(2,e.w());assertEquals(0,e.u());
    }
    @Test void incumplimientoVisibleNoSeOcultaPorElAlcanceParcial() {
        var e=motor.evaluar(consulta(List.of(criterio("acceso",Exigencia.OBLIGATORIO))),oferta(List.of(dato("acceso",false))));
        assertEquals(Conclusion.INCUMPLE,e.h());assertEquals(Categoria.INCOMPATIBLE,e.categoria());
    }
    @Test void consultaCompletaExistenteConservaSuSemantica() {
        var q=new Consulta("demanda",1,Modalidad.COMERCIAL,List.of(),corte,"D1-D4");
        var e=motor.evaluar(q,oferta(List.of()));
        assertEquals(CoberturaCriterios.COMPLETA,q.coberturaCriterios());assertEquals(Conclusion.CUMPLE,e.h());
    }
}
