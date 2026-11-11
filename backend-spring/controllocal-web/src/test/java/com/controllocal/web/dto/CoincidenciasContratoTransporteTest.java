package com.controllocal.web.dto;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.CoincidenciaService.*;
import com.controllocal.service.matching.ContratoMatching.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Contrato JSON real Core→DTO público→SDK; no ejecuta matching, puente C4 ni HTTP global. */
class CoincidenciasContratoTransporteTest {
    private static final Instant CORTE=Instant.parse("2026-10-30T17:00:00Z");
    private static final String REGLA="D1-D4-20261018-C3-R3";
    private static final Map<String,Object> V1=Map.of(
            "ENCARGO:operacion",Map.of("valor","VENTA"),
            "PROPIEDAD:tipo_propiedad",Map.of("valor","D"));
    private static final Set<String> INTERNOS=Set.of("tenant","tenantId","organizacionId","idOrganizacion",
            "ambitoOrigen","candidato","claveCandidato","idEncargo","idPropiedad","evaluacion","original");
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();

    @Test void descubrirConservaHOriginalYDecimalesSinInventarLimite()throws Exception {
        var core=resultado(new ExploracionPrecio(ModoExploracion.DESCUBRIR,null,null),alternativa(),Conclusion.INCUMPLE,Categoria.INCOMPATIBLE);
        var dto=CoincidenciasResponse.desde(core);var bytes=json.writeValueAsBytes(dto);
        var sdk=json.readValue(bytes,ClienteBrox.CoincidenciasComerciales.class);
        assertEquals(ClienteBrox.ModoExploracion.DESCUBRIR,sdk.seleccion().exploracion().modo());
        assertNull(sdk.seleccion().exploracion().limite());assertNull(sdk.seleccion().exploracion().moneda());
        var recibida=sdk.grupos().getFirst().candidatos().getFirst();
        assertEquals(Conclusion.INCUMPLE.codigo(),recibida.h());assertEquals("INCOMPATIBLE",recibida.categoria());
        assertEquals(Conclusion.CUMPLE.codigo(),recibida.g());
        assertEquals("OBLIGATORIO",recibida.razones().getFirst().exigencia());
        assertEquals(Conclusion.INCUMPLE.codigo(),recibida.razones().getFirst().resultado());
        comprobarImportes(core.grupos().getFirst().candidatos().getFirst().alternativaPrecio(),recibida.alternativaPrecio());
        comprobarContextoYPrivacidad(core,sdk,json.readTree(bytes));
    }

    @Test void explorarTransportaLimiteYMonedaExactosSinCambiarElMaximoOriginal()throws Exception {
        var limite=new BigDecimal("120000.93");
        var core=resultado(new ExploracionPrecio(ModoExploracion.EXPLORAR,limite,"USD"),alternativa(),Conclusion.INCUMPLE,Categoria.INCOMPATIBLE);
        var dto=CoincidenciasResponse.desde(core);var bytes=json.writeValueAsBytes(dto);
        var sdk=json.readValue(bytes,ClienteBrox.CoincidenciasComerciales.class);
        assertEquals(ClienteBrox.ModoExploracion.EXPLORAR,sdk.seleccion().exploracion().modo());
        decimal(limite,sdk.seleccion().exploracion().limite());assertEquals("USD",sdk.seleccion().exploracion().moneda());
        var recibida=sdk.grupos().getFirst().candidatos().getFirst();
        assertEquals(Conclusion.INCUMPLE.codigo(),recibida.h());assertEquals("INCOMPATIBLE",recibida.categoria());
        decimal(new BigDecimal("100000.01"),recibida.alternativaPrecio().maximoOriginal());
        comprobarImportes(core.grupos().getFirst().candidatos().getFirst().alternativaPrecio(),recibida.alternativaPrecio());
        comprobarContextoYPrivacidad(core,sdk,json.readTree(bytes));
    }

    @Test void consultaOrdinariaConservaLosCamposNuevosNulosYSusResultadosOriginales()throws Exception {
        var core=resultado(null,null,Conclusion.CUMPLE,Categoria.COMERCIAL_CONFIRMADA);
        var dto=CoincidenciasResponse.desde(core);var bytes=json.writeValueAsBytes(dto);
        var sdk=json.readValue(bytes,ClienteBrox.CoincidenciasComerciales.class);var arbol=json.readTree(bytes);
        assertNull(dto.seleccion().exploracion());assertNull(sdk.seleccion().exploracion());
        assertTrue(arbol.path("seleccion").path("exploracion").isNull());
        var recibida=sdk.grupos().getFirst().candidatos().getFirst();
        assertNull(recibida.alternativaPrecio());
        assertTrue(arbol.path("grupos").get(0).path("candidatos").get(0).path("alternativaPrecio").isNull());
        assertEquals(Conclusion.CUMPLE.codigo(),recibida.h());assertEquals("COMERCIAL_CONFIRMADA",recibida.categoria());
        assertEquals(Conclusion.CUMPLE.codigo(),recibida.razones().getFirst().resultado());
        comprobarContextoYPrivacidad(core,sdk,arbol);
    }

    private static CoincidenciaService.Coincidencias resultado(ExploracionPrecio exploracion,AlternativaPrecio alternativa,
                                                               Conclusion h,Categoria categoria) {
        var seleccion=new Seleccion(Origen.DEMANDA,47,2,TipoVersion.DEMANDA,Modalidad.COMERCIAL,Set.of(Capa.L2),categoria,20,exploracion);
        var razon=new Razon("base:presupuesto","base:presupuesto",Exigencia.OBLIGATORIO,0,h,null);
        var candidato=new Coincidencia("a".repeat(64),4,Capa.L2,categoria,Conclusion.CUMPLE,h,3,0,3,
                List.of(razon),V1,CoberturaCriterios.COMPLETA,alternativa);
        return new CoincidenciaService.Coincidencias(EstadoResultado.DISPONIBLE,seleccion,CORTE,REGLA,"K".repeat(43),
                List.of(new Grupo("d".repeat(64),2,List.of(candidato))),1L,Cobertura.COMPLETA,Set.of(),null);
    }
    private static AlternativaPrecio alternativa() {
        return new AlternativaPrecio(new BigDecimal("110000.37"),new BigDecimal("100000.01"),"USD","PRECIO_VENTA",null,
                new BigDecimal("10000.36"),new BigDecimal("10.00035900"));
    }
    private static void comprobarImportes(AlternativaPrecio origen,ClienteBrox.AlternativaPrecio recibida) {
        assertNotNull(recibida);decimal(origen.precio(),recibida.precio());decimal(origen.maximoOriginal(),recibida.maximoOriginal());
        decimal(origen.excesoAbsoluto(),recibida.excesoAbsoluto());decimal(origen.excesoPorcentaje(),recibida.excesoPorcentaje());
        assertEquals(origen.moneda(),recibida.moneda());assertEquals(origen.concepto(),recibida.concepto());
        assertNull(recibida.periodo());
    }
    private static void comprobarContextoYPrivacidad(CoincidenciaService.Coincidencias origen,
                                                     ClienteBrox.CoincidenciasComerciales sdk,JsonNode arbol) {
        assertEquals(origen.estado().name(),sdk.estado());assertEquals(CORTE,sdk.corte());assertEquals(REGLA,sdk.versionRegla());
        assertEquals(origen.referenciaConsulta(),sdk.referenciaConsulta());assertEquals(origen.total(),sdk.total());
        assertEquals("COMPLETA",sdk.cobertura());assertEquals(Set.of(),sdk.capasNoDisponibles());assertNull(sdk.continuacion());
        assertEquals("DEMANDA",sdk.seleccion().origen());assertEquals(47L,sdk.seleccion().idOrigen());
        assertEquals(2L,sdk.seleccion().versionOrigen());assertEquals("COMERCIAL",sdk.seleccion().modalidad());assertEquals(Set.of("L2"),sdk.seleccion().capas());
        var grupo=sdk.grupos().getFirst();assertEquals(origen.grupos().getFirst().referenciaDemanda(),grupo.referenciaDemanda());assertEquals(2,grupo.versionDemanda());
        var recibida=grupo.candidatos().getFirst();assertEquals("a".repeat(64),recibida.referencia());assertEquals(4,recibida.version());
        assertEquals("L2",recibida.capa());assertEquals(3,recibida.s());assertEquals(0,recibida.u());assertEquals(3,recibida.w());
        assertEquals("COMPLETA",recibida.coberturaCriterios());assertEquals(V1,recibida.datosPermitidos());
        sinIdentificadoresInternos(arbol);
    }
    private static void sinIdentificadoresInternos(JsonNode nodo) {
        if(nodo.isObject())nodo.fields().forEachRemaining(campo->{
            assertFalse(INTERNOS.contains(campo.getKey()),"Se serializó un identificador o contenedor interno: "+campo.getKey());
            sinIdentificadoresInternos(campo.getValue());
        });
        else if(nodo.isArray())nodo.forEach(CoincidenciasContratoTransporteTest::sinIdentificadoresInternos);
    }
    private static void decimal(BigDecimal esperado,BigDecimal actual) {
        assertNotNull(actual);assertEquals(0,esperado.compareTo(actual),"El transporte cambió el valor decimal.");
    }
}
