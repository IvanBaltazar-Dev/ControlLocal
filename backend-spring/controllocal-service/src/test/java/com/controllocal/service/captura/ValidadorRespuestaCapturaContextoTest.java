package com.controllocal.service.captura;

import com.controllocal.service.captura.SuficienciaInmueble.*;
import com.controllocal.service.captura.MotorDeCaptura.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidadorRespuestaCapturaContextoTest {
    private final ValidadorRespuestaCaptura validador=new ValidadorRespuestaCaptura(null,null,null,null);
    private static final String CAMPO="contexto.mantenimiento";
    private Contexto contexto(Aplicabilidad valor){return new Contexto("D","VIVIENDA","501","VENTA",Map.of("mantenimiento",valor),Set.of());}
    private MetadatoRespuesta sustento(){return new MetadatoRespuesta(EstadoDato.CONOCIDO,"declaracion original",null,null,null,null,false);}
    @Test void cambiarAplicabilidadSinNuevaRespuestaNoReutilizaElSustentoAnterior(){
        var metas=new LinkedHashMap<>(Map.of(CAMPO,sustento()));
        var resultado=validador.reconciliarContexto(contexto(Aplicabilidad.APLICA),contexto(Aplicabilidad.NO_APLICA),
                contexto(Aplicabilidad.NO_APLICA),Map.of(),metas);
        assertEquals(Aplicabilidad.NO_APLICA,resultado.aplicabilidad().get("mantenimiento"));
        assertNull(metas.get(CAMPO).evidenciaRef());
        var evaluacion=new SuficienciaInmueble().evaluar(Accion.CONFIRMAR_ENCARGO,resultado,Map.of());
        assertTrue(evaluacion.faltantes().stream().anyMatch(f->CAMPO.equals(f.clave())&&f.motivo()==Motivo.NO_APLICABLE_SIN_EVIDENCIA));
    }
    @Test void respuestaYContextoContrariosSeRechazan(){
        assertThrows(ReglaNegocioException.class,()->validador.reconciliarContexto(contexto(Aplicabilidad.APLICA),contexto(Aplicabilidad.NO_APLICA),
                contexto(Aplicabilidad.NO_APLICA),Map.of(CAMPO,Respuesta.conocida("APLICA")),new LinkedHashMap<>()));
    }
    @Test void retirarRespuestaRetiraAplicabilidadSinConvertirlaEnNoAplicable(){
        var resultado=validador.reconciliarContexto(contexto(Aplicabilidad.NO_APLICA),contexto(Aplicabilidad.NO_APLICA),null,
                Map.of(CAMPO,new Respuesta(null,EstadoDato.FALTANTE,null,null,null,null,null,true)),new LinkedHashMap<>());
        assertFalse(resultado.aplicabilidad().containsKey("mantenimiento"));
    }
    @Test void respuestaCorregidaConservaSuNuevoSustento(){
        var metas=new LinkedHashMap<>(Map.of(CAMPO,new MetadatoRespuesta(EstadoDato.NO_APLICABLE,"nueva declaracion",null,null,null,null,false)));
        var resultado=validador.reconciliarContexto(contexto(Aplicabilidad.APLICA),contexto(Aplicabilidad.APLICA),null,
                Map.of(CAMPO,new Respuesta(null,EstadoDato.NO_APLICABLE,"nueva declaracion",null,null,null,null,false)),metas);
        assertEquals(Aplicabilidad.NO_APLICA,resultado.aplicabilidad().get("mantenimiento"));
        assertEquals("nueva declaracion",metas.get(CAMPO).evidenciaRef());
    }
    @Test void contextoSinCambioConservaElSustento(){
        var metas=new LinkedHashMap<>(Map.of(CAMPO,sustento()));
        validador.reconciliarContexto(contexto(Aplicabilidad.NO_APLICA),contexto(Aplicabilidad.NO_APLICA),contexto(Aplicabilidad.NO_APLICA),Map.of(),metas);
        assertEquals(sustento(),metas.get(CAMPO));
    }
    @Test void contextoDesconocidoOValorFueraDeVocabularioNoSeInventan(){
        assertThrows(ReglaNegocioException.class,()->ValidadorRespuestaCaptura.valorAplicabilidad("contexto.inventado","APLICA"));
        assertThrows(ReglaNegocioException.class,()->ValidadorRespuestaCaptura.valorAplicabilidad(CAMPO,"otro"));
    }

    @Test void prestacionesSonUnConjuntoSinCambiosPorOrdenDeEnumeracion() throws Exception {
        var primero=new Contexto("D","VIVIENDA","501","VENTA",Map.of(),new LinkedHashSet<>(List.of("piso","ascensores")));
        var segundo=new Contexto("D","VIVIENDA","501","VENTA",Map.of(),new LinkedHashSet<>(List.of("ascensores","piso")));
        assertEquals(primero,segundo);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(json.writeValueAsString(primero),json.writeValueAsString(segundo));
        assertEquals(List.of("ascensores","piso"),new ArrayList<>(primero.prestacionesOfrecidas()));
        assertThrows(UnsupportedOperationException.class,()->primero.prestacionesOfrecidas().add("inventada"));
    }
}
