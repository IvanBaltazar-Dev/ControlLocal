package com.controllocal.service.demanda;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.service.soporte.ValorLogico;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProyeccionComercialDemandaTest {
    private static final Set<String> ASCENSORES=Set.of("PROPIEDAD:ascensores");
    private static final ProyeccionComercialDemanda.Base BASE=new ProyeccionComercialDemanda.Base(5,"ALQUILER",List.of("O","D"),List.of("9","2"),null,"A",null,null);
    @Test void selectoresEconomicosYAliasNoAbrenUnSegundoVocabularioFisico() {
        for(String noPermitido:List.of("ENCARGO:precio_pedido","ascensores","PROPIEDAD:direccion"))
            assertThrows(ReglaNegocioException.class,()->ProyeccionComercialDemanda.exigirSelectores(Set.of(noPermitido)));
    }
    @Test void orAutorizadoSeConservaCompletoConSuExigenciaYPrioridad() {
        var c=new Criterio("criterio:ascensores","ascensores",Exigencia.PREFERENCIA,Prioridad.IMPORTANTE,List.of(numero(BigDecimal.ZERO),numero(new BigDecimal("5"))));
        var p=new ObjectMapper().valueToTree(ProyeccionComercialDemanda.campos(BASE,Set.of("criterios"),List.of(c),ASCENSORES));
        var crit=p.path("criterios").get(0);assertEquals("PREFERENCIA",crit.path("exigencia").asText());
        assertEquals("IMPORTANTE",crit.path("prioridad").asText());assertEquals(2,crit.path("alternativas").size());
        assertEquals(0,crit.path("alternativas").get(0).path("valor").path("numero").asInt());
        assertEquals(5,crit.path("alternativas").get(1).path("valor").path("numero").asInt());
    }
    @Test void orConUnaAlternativaNoAutorizadaNoSeTruncaAUnaCoincidenciaFavorable() {
        var monetaria=new Condicion("ENCARGO:precio_pedido",TipoDato.IMPORTE,OperadorCriterio.MAXIMO,
                ValorLogico.deImporte(new BigDecimal("3000"),"PEN"),null,false,new ContextoValor("PRECIO_ALQUILER",null,"PEN","MENSUAL","ALQUILER",null),meta());
        var c=new Criterio("criterio:mixto","mixto",Exigencia.OBLIGATORIO,null,List.of(numero(BigDecimal.ONE),monetaria));
        var p=new ObjectMapper().valueToTree(ProyeccionComercialDemanda.campos(BASE,Set.of("criterios"),List.of(c),ASCENSORES));
        assertEquals(0,p.path("criterios").size());
    }
    @Test void metadataPrivadaNoApareceEnLaRepresentacionCompartida() {
        var c=new Criterio("criterio:ascensores","ascensores",Exigencia.OBLIGATORIO,null,List.of(numero(BigDecimal.ONE)));
        var p=new ObjectMapper().valueToTree(ProyeccionComercialDemanda.campos(BASE,Set.of("criterios"),List.of(c),ASCENSORES));
        assertFalse(p.toString().contains("CAT:P:"));assertFalse(p.toString().contains("evidencia-privada"));assertFalse(p.toString().contains("fuente-privada"));
        assertFalse(p.path("criterios").get(0).path("alternativas").get(0).has("metadatos"));
    }
    @Test void configuracionPrivadaNoSeQuitaParaCambiarElSignificadoDelCriterio() {
        var a=numero(BigDecimal.ONE);var contexto=new ContextoValor(null,"u",null,null,"ALQUILER","UNIDAD:privada");
        var c=new Criterio("criterio:ascensores","ascensores",Exigencia.OBLIGATORIO,null,List.of(new Condicion(a.claveDato(),a.tipo(),a.operador(),a.valor(),null,false,contexto,a.metadatos())));
        assertFalse(ProyeccionComercialDemanda.permitido(c,ASCENSORES));
    }
    @Test void ordenDeConjuntosYVersionPrivadaNoCambianElSubsetQueNoLosConcede() {
        var otra=new ProyeccionComercialDemanda.Base(9,"ALQUILER",List.of("D","O"),List.of("2","9"),"Privado","A",null,null);
        assertEquals(ProyeccionComercialDemanda.campos(BASE,Set.of("operacion","tiposAdmitidos","zona"),List.of(),Set.of()),
                ProyeccionComercialDemanda.campos(otra,Set.of("operacion","tiposAdmitidos","zona"),List.of(),Set.of()));
    }
    @Test void listaNoConsultadaNoSeConvierteEnAusenciaDeTipos() {
        var sinTipos=new ProyeccionComercialDemanda.Base(1,"ALQUILER",null,null,null,"A",null,null);
        assertThrows(NullPointerException.class,()->ProyeccionComercialDemanda.campos(sinTipos,Set.of("tiposAdmitidos"),List.of(),Set.of()));
    }
    private static Condicion numero(BigDecimal n){return new Condicion("PROPIEDAD:ascensores",TipoDato.ENTERO,OperadorCriterio.IGUAL,
            ValorLogico.deNumero(n),null,false,new ContextoValor(null,"u",null,null,"ALQUILER",null,"JUR-TEST","CAT:P:20:3","Plan TEST"),meta());}
    private static MetadatosDato meta(){return new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:42:4",4L,"evidencia-privada","fuente-privada",null,null,null);}
}
