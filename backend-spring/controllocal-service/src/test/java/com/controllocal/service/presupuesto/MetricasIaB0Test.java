package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.MetricasIaB0Repository;
import com.controllocal.persistence.presupuesto.MetricasIaB0Repository.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sólo fixtures de contratos/lectura. No concede permisos ni usa proveedor o dinero reales. */
class MetricasIaB0Test {
    final AtribucionesPlataforma autoridad=mock(AtribucionesPlataforma.class);
    final MetricasIaB0Repository repo=mock(MetricasIaB0Repository.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final Documentos json=new Documentos(mapper);
    final MetricasIaB0 servicio=new MetricasIaB0(autoridad,repo,json);
    final UUID plan=UUID.randomUUID(),caso=UUID.randomUUID(),comando=UUID.randomUUID(),fondo=UUID.randomUUID(),grant=UUID.randomUUID();
    final SesionPlataforma sesion=new SesionPlataforma("SID_TEST",7,grant);
    final Instant desde=Instant.parse("2026-11-01T00:00:00Z"),hasta=desde.plusSeconds(60);
    final Actual ops=new Actual(9,grant,plan,"VERSION_TEST","EVALUACION_TEST",null,null);
    final Actual fin=new Actual(9,grant,plan,"VERSION_TEST","EVALUACION_TEST",caso,fondo);
    @BeforeEach void preparar(){
        when(autoridad.resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.CONSULTAR_ESTADO,Campo.METRICA_TECNICA)).thenReturn(ops);
        when(autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.LEER_LEDGER,Campo.LEDGER)).thenReturn(fin);
        when(repo.tecnica(any())).thenReturn(fila("[]"));
    }
    TecnicaFila fila(String observaciones){return new TecnicaFila(hasta.plusSeconds(30),1,1,2,observaciones.equals("[]")?0:1,2,
        "[{\"estado\":\"INCIERTA\",\"operaciones\":1}]","[]","[]",observaciones);}
    String observaciones(ObservacionHttpIa... datos){var filas=new ArrayList<Map<String,Object>>();
        for(var o:datos)filas.add(Map.of("operacion",comando,"contenido",json.objetoCanonico(o),"registradaEn",hasta.plusSeconds(1)));
        return json.lista(filas);
    }
    ObservacionHttpIa observacion(long nanos,ObservacionHttpIa.Resultado r,Integer status){
        return new ObservacionHttpIa(ObservacionHttpIa.ESQUEMA,"a".repeat(64),"PROVEEDOR_TEST","MODELO_SOLICITADO_TEST","VERSION_TEST",desde, nanos,r,status,true);
    }
    @Test void ausenciaDeObservacionNoSeConvierteEnLatenciaCeroNiLlamadaEfectiva(){
        var r=servicio.tecnica(sesion,desde,hasta);
        assertNull(r.latencia());assertEquals(1,r.cobertura().operacionesSinObservacionValida());
        assertEquals(0,r.cobertura().observacionesValidas());assertEquals(2,r.cobertura().medicionesConservadas());
        verify(repo).auditar(eq(9L),eq(grant),argThat(a->a.caso()==null&&a.fondo()==null),eq("TECNICA"),isNull());
    }
    @Test void ceroRealObservadoEsDistintoDeAusenciaY404CompletoEsErrorHttpNoExito(){
        when(repo.tecnica(any())).thenReturn(fila(observaciones(observacion(0,ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA,404))));
        var r=servicio.tecnica(sesion,desde,hasta);
        assertNotNull(r.latencia());assertEquals(0,r.latencia().mediaNanos().signum());assertEquals(0L,r.latencia().p95Nanos().longValue());
        assertEquals(1,r.cobertura().erroresHttp());assertEquals(1,r.cobertura().enviosIntentados());
        assertEquals(1,r.cobertura().observacionesTardiasIncluidas());assertTrue(r.periodo().criterio().contains("COHORTE_ADMITIDA"));
    }
    @Test void timeoutConCabecerasConservaSuCategoriaSinAfirmarCeroLlamadas(){
        when(repo.tecnica(any())).thenReturn(fila(observaciones(observacion(30,ObservacionHttpIa.Resultado.TIMEOUT,503))));
        var r=servicio.tecnica(sesion,desde,hasta);assertEquals(ObservacionHttpIa.Resultado.TIMEOUT,r.http().getFirst().resultado());
        assertEquals(503,r.http().getFirst().estadoHttp());assertEquals(1,r.cobertura().enviosIntentados());assertEquals(1,r.cobertura().erroresHttp());
    }
    @Test void observacionSinPrimitivosNoInventaDuracionCeroOEnvioFalse(){
        String incompleta="{\"esquema\":\"HTTP-IA-1\",\"claveIntento\":\""+"a".repeat(64)+"\",\"proveedor\":\"TEST\",\"modelo\":\"TEST\",\"versionConfiguracion\":\"TEST\",\"iniciadaEn\":\"2026-11-01T00:00:00Z\",\"resultado\":\"DESCONOCIDO\"}";
        when(repo.tecnica(any())).thenReturn(fila(json.lista(List.of(Map.of("operacion",comando,"contenido",incompleta,"registradaEn",hasta)))));
        var r=servicio.tecnica(sesion,desde,hasta);assertNull(r.latencia());assertEquals(1,r.cobertura().observacionesNoInterpretables());
        assertEquals(1,r.cobertura().operacionesSinObservacionValida());
    }
    @Test void replayExactoNoDuplicaYObservacionContradictoriaNoSeleccionaLaPrimera(){
        var o=observacion(10,ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA,200);
        when(repo.tecnica(any())).thenReturn(fila(observaciones(o,o)));
        var r=servicio.tecnica(sesion,desde,hasta);assertEquals(1,r.cobertura().observacionesValidas());assertEquals(1,r.cobertura().observacionesRepetidas());
        when(repo.tecnica(any())).thenReturn(fila(observaciones(o,observacion(20,ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA,200))));
        r=servicio.tecnica(sesion,desde,hasta);assertNull(r.latencia());assertEquals(1,r.cobertura().observacionesContradictorias());
    }
    @Test void opsNoExponeIdentidadesCantidadesCostosNiContenidoAunqueRepoContengaUnaCantidad()throws Exception{
        String unidades="[{\"proveedor\":\"TEST\",\"modelo\":\"TEST\",\"versionMedicion\":\"V_TEST\",\"clase\":\"entrada\",\"unidad\":\"token\",\"subconjuntoDe\":null,\"cantidadOriginal\":987654321,\"mediciones\":1}]";
        when(repo.tecnica(any())).thenReturn(new TecnicaFila(hasta,1,1,1,1,0,"[]",unidades,"[]",observaciones(observacion(10,ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA,200))));
        var r=servicio.tecnica(sesion,desde,hasta);var arbol=mapper.valueToTree(r);String texto=arbol.toString();
        assertNull(r.unidadesOriginales().getFirst().cantidadOriginal());assertTrue(r.disponibilidadCantidades().startsWith("NO_HABILITADAS"));
        for(String prohibido:List.of(plan.toString(),caso.toString(),comando.toString(),fondo.toString(),grant.toString(),"987654321","costoUsd","tarifasOriginales","claveIntento","contenido"))assertFalse(texto.contains(prohibido),prohibido);
    }
    @Test void denegacionDeCampoORolPrecedeCualquierRepositorio(){
        when(autoridad.resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.CONSULTAR_ESTADO,Campo.METRICA_TECNICA)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.tecnica(sesion,desde,hasta));verifyNoInteractions(repo);
    }
    @Test void financieraExigeCasoDelGrantYFondoAntesDeLeer(){
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.financiera(sesion,UUID.randomUUID(),comando,desde,hasta));verifyNoInteractions(repo);
        when(autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.LEER_LEDGER,Campo.LEDGER)).thenReturn(new Actual(9,grant,plan,"VERSION_TEST","EVALUACION_TEST",null,null));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.financiera(sesion,caso,comando,desde,hasta));verifyNoInteractions(repo);
    }
    @Test void revalidaDespuesDeAuditarYNoEntregaSiLaConcesionCambio(){
        when(autoridad.resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.CONSULTAR_ESTADO,Campo.METRICA_TECNICA)).thenReturn(ops).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.tecnica(sesion,desde,hasta));
        var orden=inOrder(autoridad,repo);orden.verify(autoridad).resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.CONSULTAR_ESTADO,Campo.METRICA_TECNICA);
        orden.verify(repo).tecnica(any());orden.verify(repo).auditar(anyLong(),any(),any(),eq("TECNICA"),isNull());
        orden.verify(autoridad).resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.CONSULTAR_ESTADO,Campo.METRICA_TECNICA);
    }
    @Test void averiaOAuditoriaFallidaNoDevuelveUnResumenVacio(){
        when(repo.tecnica(any())).thenThrow(new IllegalStateException("TEST DB"));assertThrows(IllegalStateException.class,()->servicio.tecnica(sesion,desde,hasta));
        doReturn(fila("[]")).when(repo).tecnica(any());doThrow(new IllegalStateException("TEST AUDITORIA")).when(repo).auditar(anyLong(),any(),any(),anyString(),any());
        assertThrows(IllegalStateException.class,()->servicio.tecnica(sesion,desde,hasta));
    }
    @Test void periodoInvalidoNoConsultaNiAutoriza(){
        assertThrows(ReglaNegocioException.class,()->servicio.tecnica(sesion,hasta,desde));verifyNoInteractions(autoridad,repo);
    }
    @Test void proyeccionSinMedicionNoPresentaCostoFinalCero(){
        when(repo.financiera(any(),eq(comando))).thenReturn(Optional.of(new FinancieraFila(hasta,caso,comando,"EN_PROCESO",desde,desde,
            BigDecimal.ONE,null,"[]","[]","[]",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0L,"[]")));
        var r=servicio.financiera(sesion,caso,comando,desde,hasta);assertNull(r.valoracionTecnica().efectivoUsd());
        assertTrue(r.valoracionTecnica().cobertura().startsWith("SIN_MEDICION_NATIVA"));
    }
    @Test void financieraConservaPrecisionYEstadoSinInventarCostoPorExito(){
        var id=UUID.randomUUID();var unidad=Map.of("clase","u","unidad","token","cantidad",new BigDecimal("1.000000000001"));
        var tarifa=Map.of("clase","u","unidad","token","importeUsd",new BigDecimal("0.000000000001"),"porCantidad",BigDecimal.ONE);
        var m=new LinkedHashMap<String,Object>();m.put("id",id);m.put("proveedor","TEST");m.put("modelo","TEST");m.put("versionTarifa","V_TEST");
        m.put("unidadesOriginales",json.lista(List.of(unidad)));m.put("tarifasOriginales",json.lista(List.of(tarifa)));m.put("costoUsd",new BigDecimal("0.000000000001"));
        m.put("estadoCosto","CALCULADO");m.put("referenciaFuente","CORRELACION_TEST_NO_FACTURA");m.put("registradaEn",desde);
        when(repo.financiera(any(),eq(comando))).thenReturn(Optional.of(new FinancieraFila(hasta,caso,comando,"LIQUIDADA",desde,desde,BigDecimal.ONE,new BigDecimal("0.000000000001"),json.lista(List.of(m)),"[]","[]",null,null,null,null,"[]")));
        var r=servicio.financiera(sesion,caso,comando,desde,hasta);assertEquals("CALCULADO",r.mediciones().getFirst().estadoCosto());
        assertEquals(new BigDecimal("1.000000000001"),r.mediciones().getFirst().unidadesOriginales().getFirst().cantidad());assertNull(r.costoPorTareaCorrecta());
        verify(repo).auditar(eq(9L),eq(grant),argThat(a->caso.equals(a.caso())&&fondo.equals(a.fondo())),eq("FINANCIERA"),eq(comando));
    }
}
