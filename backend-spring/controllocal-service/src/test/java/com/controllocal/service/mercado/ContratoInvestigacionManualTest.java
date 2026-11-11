package com.controllocal.service.mercado;
import org.junit.jupiter.api.Test;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.mercado.ContratoInvestigacionManual.*;

/** Datos sintéticos: la estructura no demuestra ejecución real, permiso de fuente ni calibración. */
class ContratoInvestigacionManualTest {
    final Instant inicio=Instant.parse("2026-11-11T14:00:00Z"),fin=inicio.plusSeconds(600),registro=fin.plusSeconds(60);
    final Set<Etapa> etapas=Set.of(Etapa.BUSQUEDA,Etapa.DEPURACION,Etapa.REVISION,Etapa.ENTREGA);
    Map<Etapa,String> acreditacion(){return etapas.stream().collect(java.util.stream.Collectors.toMap(e->e,e->"TEST evidencia etapa"));}
    Plan plan(){return new Plan("TEST","Departamento/segmento declarado","Sin referencias vigentes",null,
        List.of(new FuentePrevista(Fuente.URBANIA,"TEST_F1","Vía TEST no habilitada real","Sólo datos TEST","TEST permiso simulado")),
        "Entrega o límite TEST",Duration.ofMinutes(10),etapas,2,"Informe estructurado",fin.plusSeconds(3600),"REGLA_TEST","TEST_V1");}
    Ronda ronda(Cierre cierre,Resultado resultado){
        var tiempos=new ArrayList<Tiempo>();int i=0;for(var e:new TreeSet<>(etapas)){var desde=inicio.plusSeconds(100L*i++);tiempos.add(new Tiempo(e,desde,desde.plusSeconds(60),TipoTiempo.ACTIVO,"Evidencia TEST"));tiempos.add(new Tiempo(e,desde.plusSeconds(60),desde.plusSeconds(100),TipoTiempo.ESPERA,"Evidencia TEST"));}
        return new Ronda(UUID.randomUUID(),1,cierre,resultado,inicio,fin,"Ejecución IA TEST, no llamada real",tiempos,
            List.of(new Consulta(UUID.randomUUID(),Fuente.URBANIA,"TEST_F1","Consulta TEST","Fuentes/consulta TEST",inicio.plusSeconds(20),true,false)),List.of(),
            "Informe sin comparables válidos","Cobertura TEST revisada; sin precisión empírica",new Revision("REV_TEST",17,fin,"Revisión TEST"),acreditacion());
    }
    Ronda conConsultas(Ronda r,List<Consulta> cs){return new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),r.tiempos(),cs,r.referencias(),r.informe(),r.limitaciones(),r.revision(),r.etapasAcreditadas());}
    @Test void rondaCompletaSinComparablesConservaUnidadYEvidenciaSinInventarExito(){
        var r=ronda(Cierre.COMPLETA,Resultado.SIN_EVIDENCIA);var x=comprobar(plan(),List.of(r),registro);
        assertEquals(List.of(r.identidad()),x.rondasCompletas());assertEquals(0,x.inmueblesUnicos());assertEquals(Duration.ofSeconds(240),x.activo());assertEquals(Duration.ofSeconds(160),x.espera());
    }
    @Test void interrumpidaOFallidaOInciertaNoSeAcreditaNiSeProrratea(){
        for(var c:List.of(Cierre.INTERRUMPIDA,Cierre.FALLIDA,Cierre.INCIERTA)){var r=ronda(c,Resultado.INCIDENCIA_TECNICA);var x=comprobar(plan(),List.of(r),registro);assertTrue(x.rondasCompletas().isEmpty());assertEquals(List.of(r.identidad()),x.pendientesDeAcreditacion());}
    }
    @Test void correccionOReenvioNoCreaOtraRondaEnLaMismaEntrega(){var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);assertThrows(ReglaNegocioException.class,()->comprobar(plan(),List.of(r,r),registro));}
    @Test void falloDeConsultaNoSePresentaComoBusquedaValidaSinHallazgos(){var r=ronda(Cierre.COMPLETA,Resultado.SIN_EVIDENCIA);var c=r.consultas().getFirst();var e=new Consulta(c.identidad(),c.fuente(),c.versionPermiso(),c.consulta(),c.referenciaEvidencia(),c.realizadaEn(),true,true);assertThrows(ReglaNegocioException.class,()->comprobar(plan(),List.of(conConsultas(r,List.of(e))),registro));}
    @Test void unaFuenteCandidataSinSuVersionPermitidaNoEquivaleAPermiso(){var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);var c=r.consultas().getFirst();var e=new Consulta(c.identidad(),Fuente.REMAX_PERU,"TEST_F1",c.consulta(),c.referenciaEvidencia(),c.realizadaEn(),true,false);assertThrows(ReglaNegocioException.class,()->comprobar(plan(),List.of(conConsultas(r,List.of(e))),registro));}
    @Test void unaDeclaracionDeIaSinRevisionNoAcreditaTrabajo(){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);var sin=new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),r.tiempos(),r.consultas(),r.referencias(),r.informe(),r.limitaciones(),null,r.etapasAcreditadas());
        assertThrows(ReglaNegocioException.class,()->comprobar(plan(),List.of(sin),registro));
    }
    @Test void dobleConteoDeTiempoNoSeConvierteEnEsfuerzoMedido(){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);var ts=new ArrayList<>(r.tiempos());ts.add(ts.getFirst());var otra=new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),ts,r.consultas(),r.referencias(),r.informe(),r.limitaciones(),r.revision(),r.etapasAcreditadas());assertThrows(ReglaNegocioException.class,()->comprobar(plan(),List.of(otra),registro));
    }
    @Test void esperarUnaFuenteNoImpideTrabajarOtraEtapaSinDuplicarTiempoActivo(){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);var ts=new ArrayList<>(r.tiempos());ts.add(new Tiempo(Etapa.BUSQUEDA,inicio.plusSeconds(90),inicio.plusSeconds(120),TipoTiempo.ESPERA,"Espera de otra consulta TEST"));
        var otra=new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),ts,r.consultas(),r.referencias(),r.informe(),r.limitaciones(),r.revision(),r.etapasAcreditadas());var x=comprobar(plan(),List.of(otra),registro);
        assertEquals(Duration.ofSeconds(240),x.activo());assertEquals(Duration.ofSeconds(190),x.espera());
    }
    @Test void plazoComunicadoNoSeConfundeConTiempoActivoNiFechaDeRegistro(){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);assertEquals(Duration.ofSeconds(240),comprobar(plan(),List.of(r),registro.plusSeconds(86400)).activo());
        assertThrows(ReglaNegocioException.class,()->comprobar(plan(),List.of(r),inicio.plusSeconds(30)));
    }
    @Test void repetirLaMismaConsultaNoAcreditaOtraRondaConUnNumeroDistinto(){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);var copia=new Ronda(UUID.randomUUID(),2,r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),r.tiempos(),r.consultas(),r.referencias(),r.informe(),r.limitaciones(),r.revision(),r.etapasAcreditadas());
        assertThrows(ReglaNegocioException.class,()->comprobar(plan(),List.of(r,copia),registro));
    }
    @Test void consultarSoloLaBaseAprobadaNoSeAcreditaComoInvestigacionNueva(){
        var p=plan();var gratis=new Plan(p.version(),p.alcance(),p.carencia(),p.base(),List.of(new FuentePrevista(Fuente.BROX_APROBADA,"TEST_BASE","Core determinista","Referencias autorizadas","TEST")),p.condicionParada(),p.limiteActivoPorRonda(),p.etapas(),p.rondas(),p.entrega(),p.plazoComunicado(),p.regla(),p.versionRegla());
        var r=ronda(Cierre.COMPLETA,Resultado.SIN_EVIDENCIA);var c=new Consulta(UUID.randomUUID(),Fuente.BROX_APROBADA,"TEST_BASE","Consultar referencias","TEST",inicio.plusSeconds(30),true,false);
        assertThrows(ReglaNegocioException.class,()->comprobar(gratis,List.of(conConsultas(r,List.of(c))),registro));
    }
    Plan conBcrp(){var p=plan();return new Plan(p.version(),p.alcance(),p.carencia(),p.base(),List.of(new FuentePrevista(Fuente.BCRP,"TEST_F1","Vía TEST","Contexto agregado","TEST")),p.condicionParada(),p.limiteActivoPorRonda(),p.etapas(),p.rondas(),p.entrega(),p.plazoComunicado(),p.regla(),p.versionRegla());}
    Ronda conReferenciaBcrp(boolean paraCalculo){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);var consulta=new Consulta(UUID.randomUUID(),Fuente.BCRP,"TEST_F1","Contexto TEST","Fuente TEST",inicio.plusSeconds(20),true,false);
        var ctx=new CalculoPrecioMercado.Contexto(com.controllocal.domain.inmueble.OperacionInmobiliaria.VENTA,"DEPARTAMENTO","TEST","PEN",null,CalculoPrecioMercado.Naturaleza.OFERTA,"AREA_CONSTRUIDA");
        var c=new CalculoPrecioMercado.Comparable("INMUEBLE_TEST","REF_TEST",ctx,null,null,null,null,"TEST",List.of(),null,null);
        var ref=new Referencia("REF_TEST","INMUEBLE_TEST","Identidad TEST",Fuente.BCRP,"AGREGADO_TEST","TEST_F1",new FechaFuente(inicio.atZone(ZoneId.of("America/Lima")).toLocalDate(),inicio),inicio.plusSeconds(30),paraCalculo,paraCalculo?c:null,paraCalculo?null:"Sólo contexto agregado",null,ConceptoImporte.PRECIO_TOTAL,"TEST importe");
        return new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),r.tiempos(),List.of(consulta),List.of(ref),r.informe(),r.limitaciones(),r.revision(),r.etapasAcreditadas());
    }
    @Test void bcrpNoSeConvierteEnUnComparableIndividualPorTenerUnImporte(){assertThrows(ReglaNegocioException.class,()->comprobar(conBcrp(),List.of(conReferenciaBcrp(true)),registro));}
    @Test void bcrpPuedeConservarContextoSinMultiplicarInmueblesDeLaMuestra(){var x=comprobar(conBcrp(),List.of(conReferenciaBcrp(false)),registro);assertEquals(0,x.inmueblesUnicos());assertEquals(1,x.rondasCompletas().size());}
    @Test void entregarNoPermiteAutoasignarseLaRevisionCompetente(){assertThrows(ReglaNegocioException.class,()->examinarEntrega(plan(),List.of(ronda(Cierre.COMPLETA,Resultado.PARCIAL)),registro));}
    @Test void entregaCompletaSinRevisionQuedaPendienteSinAcreditarCargo(){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);var propuesta=new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),r.tiempos(),r.consultas(),r.referencias(),r.informe(),r.limitaciones(),null,r.etapasAcreditadas());
        var x=examinarEntrega(plan(),List.of(propuesta),registro);assertTrue(x.rondasCompletas().isEmpty());assertEquals(List.of(r.identidad()),x.pendientesDeAcreditacion());
    }
    @Test void revisarDespuesNoCambiaLaFechaEnQueElOperadorTermino(){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);var revisada=new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),r.tiempos(),r.consultas(),r.referencias(),r.informe(),r.limitaciones(),new Revision("REV_TEST_POSTERIOR",17,fin.plusSeconds(30),"Revisión posterior TEST"),r.etapasAcreditadas());
        assertEquals(List.of(r.identidad()),comprobar(plan(),List.of(revisada),registro).rondasCompletas());assertEquals(fin,revisada.terminadaEn());
    }
    @Test void esfuerzoNoMedidoNoSeFabricaComoCeroNiSeUsaComoTarifaPorMinuto(){
        var r=ronda(Cierre.COMPLETA,Resultado.SIN_EVIDENCIA);
        var sin=new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),null,r.consultas(),r.referencias(),r.informe(),r.limitaciones(),r.revision(),r.etapasAcreditadas());
        var x=comprobar(plan(),List.of(sin),registro);assertEquals(1,x.rondasCompletas().size());assertNull(x.activo());assertNull(x.espera());
    }
    @Test void tiemposNoSustituyenEvidenciaDeEtapasCumplidas(){
        var r=ronda(Cierre.COMPLETA,Resultado.PARCIAL);
        var sin=new Ronda(r.identidad(),r.numero(),r.cierre(),r.resultado(),r.iniciadaEn(),r.terminadaEn(),r.ejecucionAsistidaIa(),r.tiempos(),r.consultas(),r.referencias(),r.informe(),r.limitaciones(),r.revision(),Map.of());
        assertThrows(ReglaNegocioException.class,()->comprobar(plan(),List.of(sin),registro));
    }
    @Test void fechaConPrecisionDeDiaNoInventaUnaHora(){
        var f=new FechaFuente(LocalDate.of(2026,11,10),null);assertNull(f.instante());
        assertThrows(ReglaNegocioException.class,()->new FechaFuente(LocalDate.of(2026,11,9),inicio));
    }

}
