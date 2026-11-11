package com.controllocal.web.asistencia;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.ErrorBrox;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.brox.Traza;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.capacidades.AdaptadoresDeCapacidad;
import com.controllocal.service.capacidades.CapacidadesService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.web.almacen.GaleriaDeFotos;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClienteBroxInternoTest {
    private final CapacidadesService capacidades = mock(CapacidadesService.class);
    private final AdaptadoresDeCapacidad adaptadores = mock(AdaptadoresDeCapacidad.class);
    private final MotorDeCaptura motor = mock(MotorDeCaptura.class);
    private final PropiedadUniversalService propiedades = mock(PropiedadUniversalService.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final PropietarioService propietarios = mock(PropietarioService.class);
    private final CoincidenciaService coincidencias = mock(CoincidenciaService.class);
    private final FocoService focos = mock(FocoService.class);
    private final ActuacionesService actuaciones = mock(ActuacionesService.class);
    private final ComandosAutomaticosService automaticos = mock(ComandosAutomaticosService.class);
    private final RevalidacionDeSesion sesiones = mock(RevalidacionDeSesion.class);
    private final OportunidadService oportunidades = mock(OportunidadService.class);
    private final PublicacionService publicaciones=mock(PublicacionService.class);
    private final com.controllocal.service.documentos.PiezasComercialesService piezas=mock(com.controllocal.service.documentos.PiezasComercialesService.class);
    private final com.controllocal.service.documentos.FotosComercialesService fotosComerciales=mock(com.controllocal.service.documentos.FotosComercialesService.class);
    private final com.controllocal.service.materialia.MaterialIaService materialIa=mock(com.controllocal.service.materialia.MaterialIaService.class);
    private final com.controllocal.service.actuacion.IdentidadComandoAutomatico identidades=
            new com.controllocal.service.actuacion.IdentidadComandoAutomatico(
                    new com.controllocal.service.soporte.Documentos(new ObjectMapper().findAndRegisterModules()),new ObjectMapper().findAndRegisterModules());
    private final ClienteBroxInterno puerto = new ClienteBroxInterno(capacidades, adaptadores, motor,
            propiedades, clientes, propietarios, coincidencias, focos, actuaciones, automaticos,
            mock(GaleriaDeFotos.class), new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES), sesiones, oportunidades, mock(VisitaService.class),mock(com.controllocal.service.InteraccionService.class),mock(com.controllocal.service.ProspeccionService.class),publicaciones,identidades,piezas,fotosComerciales,materialIa,
            mock(com.controllocal.service.actividad.CompromisosDeSeguimiento.class),mock(com.controllocal.service.actividad.InformesC7.class),mock(com.controllocal.service.actividad.ProgramasPersonales.class));
    private static final Actor ACTOR = new Actor(1, 11, 21, Actor.AGENTE);
    private static final Traza TRAZA = new Traza("API", "KAIROS", "determinista", "1",
            "conversacion", "turno", "mensaje", "prepara", "preparar_actuacion_captura");
    private SesionBrox sesion;
    private final ReportePropietarioService reportesPropietario=mock(ReportePropietarioService.class);
    private final MetaComercialService metasComerciales=mock(MetaComercialService.class);
    private final RendimientoComercialService rendimientoComercial=mock(RendimientoComercialService.class);
    private final com.controllocal.service.actividad.InformesC7 informes=mock(com.controllocal.service.actividad.InformesC7.class);
    private ContextosService.SesionAccesoDTO coreActual;

    @BeforeEach void preparar() {
        org.springframework.test.util.ReflectionTestUtils.setField(puerto,"reportesPropietario",reportesPropietario);
        org.springframework.test.util.ReflectionTestUtils.setField(puerto,"metasComerciales",metasComerciales);
        org.springframework.test.util.ReflectionTestUtils.setField(puerto,"rendimientoComercial",rendimientoComercial);
        org.springframework.test.util.ReflectionTestUtils.setField(puerto,"informesC7",informes);
        coreActual=new com.controllocal.service.ContextosService.SesionAccesoDTO("A".repeat(43),1,
                new com.controllocal.service.ContextosService.IdentidadAutenticada(99,"fixture","fixture@cuentas.example.test","Fixture"),
                new com.controllocal.service.ContextosService.Contexto("a".repeat(64),1,11,21,"AGENTE",null,"Prueba"),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),Instant.now().plusSeconds(1800).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                com.controllocal.service.EstadoDeAcceso.SIN_RESTRICCIONES);
        var principal=new SesionDeRequest(new TokenService("").de(coreActual),coreActual);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_AGENTE")));
        var contexto = ContextoDeActuacion.de(principal);
        sesion = new SesionBrox(1, 11, 21, "AGENTE", contexto.identidad(), contexto.expiraEnEpochSeconds());
        when(adaptadores.de(anyString(), eq(ACTOR)))
                .thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true, true, true));
        when(sesiones.exigirVigente()).thenReturn(ACTOR);
    }

    @AfterEach void cerrarSesion() { SecurityContextHolder.clearContext(); }

    @Test void informeConservaFiltrosYAlcancesSinTrasladarReglasAlCliente(){
        var hoy=java.time.LocalDate.of(2026,11,10);
        var salida=new com.controllocal.service.actividad.InformesC7.Informe("TEST53",
                com.controllocal.service.planes.DerechosDeInformesC7.Composicion.DIAGNOSTICO_RESPONSABLE,
                "PRIME","EQUIPO",hoy,hoy,Instant.parse("2026-11-11T00:00:00Z"),List.of(21L),
                Map.of("alcances",Map.of("mediciones",Map.of("rotulo","Todas las operaciones","cobertura","Filtros locales no aplican"))),List.of(),List.of(),List.of());
        when(informes.preparar(any(),eq(ACTOR))).thenReturn(salida);
        var filtros=Map.of("operacion","VENTA","responsable",21,"tipoActividad","LLAMADA",
                "situacion",Map.of("recurso","OPORTUNIDAD","codigo","A"),"hechoEconomico","COBRO_EN_PERIODO");
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREPARAR_INFORME_COMERCIAL,
                Map.of("consulta",Map.of("composicion","DIAGNOSTICO_RESPONSABLE","desde",hoy.toString(),"hasta",hoy.toString(),"filtros",filtros,"operacionCohorte","ALQUILER")),TRAZA);
        var cap=ArgumentCaptor.forClass(com.controllocal.service.actividad.InformesC7.Consulta.class);
        verify(informes).preparar(cap.capture(),eq(ACTOR));assertEquals("VENTA",cap.getValue().filtros().operacion());
        assertEquals("ALQUILER",cap.getValue().operacionCohorte());assertEquals("OPORTUNIDAD",cap.getValue().filtros().situacion().recurso());
        assertEquals("A",cap.getValue().filtros().situacion().codigo());assertEquals(21L,cap.getValue().filtros().responsable());
        assertNotNull(r.datos().get("bloques"));assertTrue(r.datos().toString().contains("Filtros locales no aplican"));
        assertNull(r.propuesta());verifyNoInteractions(automaticos,actuaciones);
    }
    @Test void filtroNoAceptaOrganizacionOcultaAunqueElMapperPermitaCamposDesconocidos(){
        var datos=Map.of("composicion","OPERACION_ACTUAL","filtros",Map.of("organizacion",99));
        var e=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREPARAR_INFORME_COMERCIAL,Map.of("consulta",datos),TRAZA));
        assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,e.tipo());verifyNoInteractions(informes);
    }
    @Test void informeDistingueDeshabilitacionPermisoYFalloTecnico(){
        var op=ClienteBrox.OperacionComercial.PREPARAR_INFORME_COMERCIAL;String nombre=op.name().toLowerCase(java.util.Locale.ROOT);
        when(adaptadores.de(nombre,ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,true,false));
        assertEquals(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,op,Map.of(),TRAZA)).tipo());
        when(adaptadores.de(nombre,ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,false,true));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,op,Map.of(),TRAZA)).tipo());
        when(adaptadores.de(nombre,ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,true,true));
        when(informes.preparar(any(),eq(ACTOR))).thenThrow(new IllegalStateException("Fallo sintético TEST53"));
        assertEquals(ErrorBrox.Tipo.FALLO_TECNICO,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,op,Map.of("consulta",Map.of("composicion","OPERACION_ACTUAL")),TRAZA)).tipo());
    }



    @ParameterizedTest @ValueSource(strings={"proponer_meta","preparar_reporte_propietario","registrar_comunicacion_reporte_realizada"})
    void comandosPropiosUsanIdentidadComunSinDisfrazarseDeUnEjecutorQueNoLosAdmite(String capacidad){
        var esperado=identidades.clavePara(capacidad,ACTOR,new ComandosAutomaticosService.CorrelacionAutomatico("conversacion","turno","mensaje"));
        String actual=org.springframework.test.util.ReflectionTestUtils.invokeMethod(puerto,"claveAutomatica",capacidad,ACTOR,TRAZA);assertEquals(esperado,actual);assertTrue(actual.matches("[a-f0-9]{64}"));verifyNoInteractions(automaticos);
    }
    @Test void recuperacionMetaConsultaElReciboSinProponerNiDecidirOtraVez(){
        var clave="a".repeat(64);when(metasComerciales.resultado("proponer_meta",clave,ACTOR)).thenReturn(java.util.Optional.of(new MetaComercialService.Resultado(List.of())));
        var r=puerto.consultarActuacion(sesion,"auto:proponer_meta:"+clave,TRAZA);assertEquals("EXITO",r.estado());assertEquals(List.of(),r.comercial().datos().get("metas"));verify(metasComerciales).resultado("proponer_meta",clave,ACTOR);verifyNoMoreInteractions(metasComerciales);verifyNoInteractions(automaticos,actuaciones);
    }
    @Test void metasAutorizadasVaciasNoSeConviertenEnErrorNiMetasCero(){
        when(metasComerciales.del("2026-11",ACTOR)).thenReturn(List.of());
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.CONSULTAR_METAS_COMERCIALES,Map.of("mes","2026-11"),TRAZA);
        assertEquals(List.of(),r.datos().get("metas"));assertNull(r.propuesta());verify(metasComerciales).del("2026-11",ACTOR);verifyNoInteractions(actuaciones,automaticos);
    }
    @Test void establecerMetaDesdeKairosSoloPreparaRevisionExacta(){
        var p=new ActuacionesService.Propuesta("meta-revisable","establecer_meta",0,"h",Long.MAX_VALUE,Map.of());
        when(actuaciones.prepararEstablecimientoMetas(eq(ACTOR),any(),eq(ActuacionesService.ContextoSesion.de(coreActual)),any())).thenReturn(p);
        var asignacion=Map.of("idRolAgente",21,"kpi","OPORTUNIDAD_APERTURA","operacion","VENTA","versionEsperada",0,"valor",8,"motivo","Meta explícita para este período");
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.ESTABLECER_META,Map.of("metas",Map.of("mes","2026-11","asignaciones",List.of(asignacion))),TRAZA);
        assertNotNull(r.propuesta());assertNull(r.datos());assertNull(r.automatico());var cap=ArgumentCaptor.forClass(MetaComercialService.Establecer.class);
        verify(actuaciones).prepararEstablecimientoMetas(eq(ACTOR),cap.capture(),eq(ActuacionesService.ContextoSesion.de(coreActual)),any());assertEquals(0L,cap.getValue().asignaciones().getFirst().versionEsperada());assertNull(cap.getValue().asignaciones().getFirst().calendario());verifyNoInteractions(metasComerciales,automaticos);
    }
    @Test void decidirMetaNoEjecutaNiUsaLaSesionDeOtroActor(){
        var p=new ActuacionesService.Propuesta("decision-meta","decidir_propuesta_meta",0,"h",Long.MAX_VALUE,Map.of());
        when(actuaciones.prepararDecisionMeta(eq(ACTOR),any(),eq(ActuacionesService.ContextoSesion.de(coreActual)),any())).thenReturn(p);
        var d=Map.of("propuesta",9,"versionEsperada",1,"decision","OBSERVAR","motivo","Falta precisar el calendario acordado");
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.DECIDIR_PROPUESTA_META,Map.of("decisionMeta",d),TRAZA);
        assertNotNull(r.propuesta());var cap=ArgumentCaptor.forClass(MetaComercialService.Decision.class);verify(actuaciones).prepararDecisionMeta(eq(ACTOR),cap.capture(),eq(ActuacionesService.ContextoSesion.de(coreActual)),any());assertEquals(MetaComercialService.TipoDecision.OBSERVAR,cap.getValue().decision());assertNull(cap.getValue().valorAprobado());verifyNoInteractions(metasComerciales,automaticos);
    }
    @Test void propuestaPropiaConservaSidYClaveSinCambiarUnaMetaDirectamente(){
        when(metasComerciales.proponer(eq("2026-11"),any(),eq(ACTOR),eq(ActuacionesService.ContextoSesion.de(coreActual)),anyString(),any())).thenReturn(new MetaComercialService.Resultado(List.of()));
        var d=Map.of("kpi","OPORTUNIDAD_APERTURA","operacion","VENTA","versionMetaEsperada",0,"valor",8,"motivo","Propuesta propia para este período");
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PROPONER_META,Map.of("mes","2026-11","propuestaMeta",d),TRAZA);
        assertEquals(List.of(),r.datos().get("metas"));assertNull(r.propuesta());verify(metasComerciales).proponer(eq("2026-11"),any(),eq(ACTOR),eq(ActuacionesService.ContextoSesion.de(coreActual)),anyString(),any());verifyNoInteractions(actuaciones,automaticos);
    }
    @Test void datosOcultosDeMetasSeRechazanAntesDePrepararUnaDecision(){
        var datos=Map.of("mes","2026-11","asignaciones",List.of(),"brokerSuplantado",99);
        var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.ESTABLECER_META,Map.of("metas",datos),TRAZA));
        assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,error.tipo());verifyNoInteractions(metasComerciales,actuaciones,automaticos);
    }
    @ParameterizedTest @ValueSource(strings={"DEFINICION_DE_METAS","CONSULTAR_METAS_COMERCIALES","CONSULTAR_PROPUESTAS_METAS","CONSULTAR_PROPUESTA_META","PROPONER_META","ESTABLECER_META","DECIDIR_PROPUESTA_META","CONSULTAR_AVANCE_METAS","CONSULTAR_PULSO_EQUIPO"})
    void puertasMetaNoConfundenDeshabilitacionConPermisoNiResultadoVacio(String nombre){
        var op=ClienteBrox.OperacionComercial.valueOf(nombre);when(adaptadores.de(op.name().toLowerCase(java.util.Locale.ROOT),ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,true,false));
        assertEquals(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,op,Map.of(),TRAZA)).tipo());
        when(adaptadores.de(op.name().toLowerCase(java.util.Locale.ROOT),ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,false,true));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,op,Map.of(),TRAZA)).tipo());verifyNoInteractions(metasComerciales,rendimientoComercial,actuaciones,automaticos);
    }
    @Test void reportesSinResultadosDevuelveVacioRealSinCrearComunicaciones(){
        when(reportesPropietario.listar(9,1,20,ACTOR)).thenReturn(List.of());
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.CONSULTAR_REPORTES_PROPIETARIO,Map.of("idEncargo",9),TRAZA);
        assertEquals(List.of(),r.datos().get("reportes"));assertNull(r.propuesta());assertNull(r.automatico());
        verify(reportesPropietario).listar(9,1,20,ACTOR);verifyNoMoreInteractions(reportesPropietario);verifyNoInteractions(actuaciones,automaticos);
    }
    @Test void obtenerReportePideLaVersionExactaSinComunicarNiGenerarOtraVersion(){
        var id=java.util.UUID.randomUUID();var f=new ReportePropietarioService.Archivo(java.util.UUID.randomUUID(),"reporte.pdf","application/pdf","a".repeat(64));
        when(reportesPropietario.obtenerArchivo(new ReportePropietarioService.Referencia(id,3L),ACTOR,ActuacionesService.ContextoSesion.de(coreActual))).thenReturn(f);
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.OBTENER_REPORTE_PROPIETARIO,Map.of("reporte",Map.of("referencia",id.toString(),"version",3)),TRAZA);
        assertEquals(f.referencia().toString(),r.datos().get("referencia"));assertNull(r.propuesta());
        verify(reportesPropietario).obtenerArchivo(new ReportePropietarioService.Referencia(id,3L),ACTOR,ActuacionesService.ContextoSesion.de(coreActual));verifyNoMoreInteractions(reportesPropietario);verifyNoInteractions(automaticos,actuaciones);
    }
    @Test void rectificarComunicacionDesdeKairosSoloPreparaLaRevisionHumana(){
        var ref=java.util.UUID.randomUUID();var propuesta=new ActuacionesService.Propuesta("revisar-comunicacion","rectificar_comunicacion_reporte",0,"h",Long.MAX_VALUE,Map.of("comunicacion",ref.toString()));
        when(actuaciones.prepararRectificacionComunicacionReporte(eq(ACTOR),any(),eq(ActuacionesService.ContextoSesion.de(coreActual)),any())).thenReturn(propuesta);
        var d=Map.<String,Object>of("comunicacion",ref.toString(),"versionEsperada",4,"canal","CORREO_ELECTRONICO","motivo","Precisar el canal real");
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.RECTIFICAR_COMUNICACION_REPORTE,Map.of("rectificacion",d),TRAZA);
        assertNotNull(r.propuesta());assertEquals("revisar-comunicacion",r.propuesta().referencia());assertNull(r.datos());assertNull(r.automatico());
        var captor=ArgumentCaptor.forClass(ReportePropietarioService.Rectificacion.class);verify(actuaciones).prepararRectificacionComunicacionReporte(eq(ACTOR),captor.capture(),eq(ActuacionesService.ContextoSesion.de(coreActual)),any());assertEquals(ref,captor.getValue().comunicacion());assertEquals(4L,captor.getValue().versionEsperada());
        verifyNoMoreInteractions(actuaciones);verifyNoInteractions(reportesPropietario,automaticos);
    }
    @ParameterizedTest @ValueSource(strings={"CONSULTAR_REPORTES_PROPIETARIO","CONSULTAR_AVANCE_REPORTE","CONSULTAR_ANTECEDENTE_REPORTE","PREPARAR_REPORTE_PROPIETARIO","PREVISUALIZAR_REPORTE_PROPIETARIO","OBTENER_REPORTE_PROPIETARIO","CONSULTAR_COMUNICACIONES_REPORTE","REGISTRAR_COMUNICACION_REPORTE_REALIZADA","RECTIFICAR_COMUNICACION_REPORTE"})
    void puertasReporteDistinguenDeshabilitacionDePermisoInsuficienteAntesDelCaso(String nombre){
        var op=ClienteBrox.OperacionComercial.valueOf(nombre);String cap=nombre.toLowerCase(java.util.Locale.ROOT);
        when(adaptadores.de(cap,ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,true,false));
        assertEquals(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,op,Map.of(),TRAZA)).tipo());
        when(adaptadores.de(cap,ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,false,true));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,op,Map.of(),TRAZA)).tipo());verifyNoInteractions(reportesPropietario,automaticos,actuaciones);
    }
    @ParameterizedTest @ValueSource(strings={"CONTADOR","VERSION_FRACCIONARIA"})
    void noAceptaContadoresDelClienteNiTruncaVersionesDeReporte(String caso){
        Map<String,Object> datos="CONTADOR".equals(caso)?Map.of("idEncargo",9,"datosReporte",Map.of("consultasReportadas",900)):Map.of("reporte",Map.of("referencia",java.util.UUID.randomUUID().toString(),"version",new java.math.BigDecimal("0.9")));
        var op="CONTADOR".equals(caso)?ClienteBrox.OperacionComercial.PREPARAR_REPORTE_PROPIETARIO:ClienteBrox.OperacionComercial.OBTENER_REPORTE_PROPIETARIO;
        assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,op,datos,TRAZA)).tipo());verifyNoInteractions(reportesPropietario,automaticos,actuaciones);
    }

    @Test void prepararMaterialConservaInstruccionOpcionalYSoloUsaElCoordinador(){
        var id=java.util.UUID.randomUUID();var tipo=com.controllocal.service.materialia.MaterialIaService.Accion.REDACTAR_FICHA;
        when(materialIa.preparar(any(),eq(ACTOR),any())).thenAnswer(i->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return new com.controllocal.service.materialia.MaterialIaService.Preparacion(id,tipo,
                    new com.controllocal.service.materialia.MaterialIaService.Recurso("PROPIEDAD","7"),"FUENTE_1",Map.of("utilidad","Resumen local"),java.util.List.of("TRATAMIENTO_PENDIENTE"));
        });
        String instruccion="Destaca la iluminación; no agregues hechos no registrados.";
        var datos=new java.util.LinkedHashMap<String,Object>(Map.of("operacion",id.toString(),"accion",tipo.name(),"recurso",Map.of("tipo","PROPIEDAD","referencia","7"),"instruccion",instruccion));
        var resultado=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREPARAR_MATERIAL_IA,datos,TRAZA);
        assertEquals("FUENTE_1",resultado.datos().get("versionOrigen"));assertNull(resultado.propuesta());assertNull(resultado.automatico());
        var captor=ArgumentCaptor.forClass(com.controllocal.service.materialia.MaterialIaService.Solicitud.class);
        verify(materialIa).preparar(captor.capture(),eq(ACTOR),eq(ActuacionesService.ContextoSesion.de(coreActual)));
        assertEquals(instruccion,captor.getValue().instruccion());verifyNoInteractions(automaticos,actuaciones);
    }
    @Test void solicitudMaterialNoIntroduceConfirmacionEconomicaNiOtroLedger(){
        var id=java.util.UUID.randomUUID();
        when(materialIa.solicitar(any(),eq(ACTOR),any())).thenReturn(new com.controllocal.service.materialia.MaterialIaService.Resultado(id,"DISPONIBLE","Texto privado",null,java.util.List.of()));
        var resultado=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.SOLICITAR_MATERIAL_IA,
                Map.of("operacion",id.toString(),"accion","REDACTAR_FICHA","recurso",Map.of("tipo","ENCARGO","referencia","7"),"versionOrigen","FUENTE_1"),TRAZA);
        assertEquals("DISPONIBLE",resultado.datos().get("estado"));assertNull(resultado.propuesta());assertNull(resultado.automatico());
        verifyNoInteractions(automaticos,actuaciones);verify(materialIa).solicitar(any(),eq(ACTOR),eq(ActuacionesService.ContextoSesion.de(coreActual)));
    }
    @Test void materialDeshabilitadoYFalloTecnicoNoSeDevuelvenComoVacio(){
        var datos=Map.<String,Object>of("operacion",java.util.UUID.randomUUID().toString(),"accion","REDACTAR_FICHA","recurso",Map.of("tipo","PROPIEDAD","referencia","7"),"versionOrigen","FUENTE_1");
        when(materialIa.solicitar(any(),any(),any())).thenThrow(new com.controllocal.service.excepcion.CapacidadDeshabilitadaException());
        var deshabilitada=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.SOLICITAR_MATERIAL_IA,datos,TRAZA));
        assertEquals(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,deshabilitada.tipo());
        doThrow(new IllegalStateException("Fallo técnico TEST")).when(materialIa).solicitar(any(),any(),any());
        var tecnico=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.SOLICITAR_MATERIAL_IA,datos,TRAZA));
        assertEquals(ErrorBrox.Tipo.FALLO_TECNICO,tecnico.tipo());
    }
    @Test void consultarMaterialLlamaLaLecturaConSuSesionSinSolicitarOtraGeneracion(){
        var id=java.util.UUID.randomUUID();when(materialIa.consultar(eq(id),eq(ACTOR),any())).thenReturn(
                new com.controllocal.service.materialia.MaterialIaService.Resultado(id,"DISPONIBLE",null,java.util.UUID.randomUUID(),java.util.List.of()));
        var resultado=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.CONSULTAR_MATERIAL_IA,Map.of("operacion",id.toString()),TRAZA);
        assertEquals("DISPONIBLE",resultado.datos().get("estado"));verify(materialIa).consultar(id,ACTOR,ActuacionesService.ContextoSesion.de(coreActual));
        verify(materialIa,never()).solicitar(any(),any(),any());verify(materialIa,never()).disponibilidad(any());verifyNoInteractions(automaticos,actuaciones);
    }
    @Test void sdkNoAceptaEvidenciaOProveedorDeclaradosPorElModelo(){
        var datos=new java.util.LinkedHashMap<String,Object>(Map.of("operacion",java.util.UUID.randomUUID().toString(),"accion","REDACTAR_FICHA","recurso",Map.of("tipo","PROPIEDAD","referencia","7"),"acreditada",true));
        var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREPARAR_MATERIAL_IA,datos,TRAZA));
        assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,error.tipo());verifyNoInteractions(materialIa,automaticos,actuaciones);
    }

    @ParameterizedTest @ValueSource(strings={"PUBLICAR_ANUNCIO","ACTUALIZAR_ANUNCIO_VISIBLE","PAUSAR_ANUNCIO","REANUDAR_ANUNCIO","FINALIZAR_DIFUSION_ANUNCIO"})
    void publicacionSensiblePreparaExactamenteSinConfirmar(String nombre){
        var op=ClienteBrox.OperacionComercial.valueOf(nombre);String cap=nombre.toLowerCase(java.util.Locale.ROOT);
        var propuesta=new ActuacionesService.Propuesta("revision-publicacion",cap,1,"hash",Long.MAX_VALUE,Map.of("publicacion",81));
        when(actuaciones.prepararPublicacion(eq(cap),eq(ACTOR),any(),any(),any())).thenReturn(propuesta);
        var entrada=new java.util.LinkedHashMap<String,Object>(Map.of("encargo",21,"versionEncargo",7,"publicacion",81,"versionPublicacion",3));
        if(op==ClienteBrox.OperacionComercial.PAUSAR_ANUNCIO||op==ClienteBrox.OperacionComercial.FINALIZAR_DIFUSION_ANUNCIO)
            entrada.put("motivo","Reducir exposición por indicación comercial");
        var respuesta=puerto.operarComercial(sesion,op,entrada,TRAZA.invocando(cap));
        assertEquals("revision-publicacion",respuesta.propuesta().referencia());assertNull(respuesta.datos());assertNull(respuesta.automatico());
        var datos=ArgumentCaptor.forClass(PublicacionService.DatosCambio.class);
        verify(actuaciones).prepararPublicacion(eq(cap),eq(ACTOR),datos.capture(),eq(ActuacionesService.ContextoSesion.de(coreActual)),any());
        assertEquals(21,datos.getValue().encargo());assertEquals(7,datos.getValue().versionEncargo());
        assertEquals(81,datos.getValue().publicacion());assertEquals(3,datos.getValue().versionPublicacion());
        assertNull(datos.getValue().huellaContexto());
        verifyNoMoreInteractions(actuaciones);verifyNoInteractions(publicaciones,automaticos);
    }
    @Test void borradorPublicacionDevuelveLaReferenciaDurableSinUsarOtroLedger(){
        var ficha=fichaAnuncio();
        when(publicaciones.crearBorradorAnuncio(any(),eq(ACTOR),any(),anyString(),any())).thenReturn(ficha);
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.CREAR_BORRADOR_ANUNCIO,
                Map.of("encargo",21,"versionEncargo",7),TRAZA.invocando("crear_borrador_anuncio"));
        String clave=identidades.clavePara("crear_borrador_anuncio",ACTOR,new ComandosAutomaticosService.CorrelacionAutomatico("conversacion","turno","mensaje"));
        assertEquals("auto:crear_borrador_anuncio:"+clave,r.automatico().referencia());assertNull(r.propuesta());
        assertEquals(81L,((Number)r.datos().get("id")).longValue());assertEquals("B",r.datos().get("estado"));
        verify(publicaciones).crearBorradorAnuncio(argThat(d->d.encargo()==21&&d.versionEncargo()==7&&d.contenido()==null),eq(ACTOR),any(),eq(clave),any());
        verifyNoInteractions(actuaciones,automaticos);
    }
    @Test void respuestaPerdidaDeBorradorConservaReferenciaYRecuperaSinReemitir(){
        when(publicaciones.editarBorradorAnuncio(any(),eq(ACTOR),any(),anyString(),any())).thenThrow(new IllegalStateException("Detalle SQL privado"));
        var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.EDITAR_BORRADOR_ANUNCIO,
                Map.of("encargo",21,"versionEncargo",7,"publicacion",81,"versionPublicacion",3),TRAZA.invocando("editar_borrador_anuncio")));
        assertEquals("RESULTADO_INCIERTO",error.codigo());assertFalse(error.getMessage().contains("SQL"));
        String clave=error.operacion().referencia().split(":")[2];
        when(publicaciones.consultarResultado("editar_borrador_anuncio",clave,ACTOR)).thenReturn(java.util.Optional.of(fichaAnuncio()));
        var recuperado=puerto.consultarActuacion(sesion,error.operacion().referencia(),TRAZA);
        assertEquals("EXITO",recuperado.estado());assertNotNull(recuperado.comercial());
        verify(publicaciones,times(1)).editarBorradorAnuncio(any(),eq(ACTOR),any(),eq(clave),any());
        verify(publicaciones).consultarResultado("editar_borrador_anuncio",clave,ACTOR);verifyNoInteractions(automaticos);
    }
    @Test void lecturaVaciaDeAnunciosNoOcultaUnFalloTecnico(){
        when(publicaciones.listarDeEncargo(21,ACTOR)).thenReturn(List.of());
        assertEquals(List.of(),puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.CONSULTAR_PUBLICACIONES,
                Map.of("encargo",21),TRAZA).datos().get("publicaciones"));
        when(publicaciones.listarDeEncargo(21,ACTOR)).thenThrow(new IllegalStateException("Consulta privada"));
        assertEquals(ErrorBrox.Tipo.FALLO_TECNICO,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,
                ClienteBrox.OperacionComercial.CONSULTAR_PUBLICACIONES,Map.of("encargo",21),TRAZA)).tipo());
    }
    @Test void publicacionDenegadaYDeshabilitadaConservanErroresDistintos(){
        when(adaptadores.de("crear_borrador_anuncio",ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,false,true));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,
                ClienteBrox.OperacionComercial.CREAR_BORRADOR_ANUNCIO,Map.of("encargo",21,"versionEncargo",7),TRAZA)).tipo());
        when(adaptadores.de("crear_borrador_anuncio",ACTOR)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,true,false));
        assertEquals(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,
                ClienteBrox.OperacionComercial.CREAR_BORRADOR_ANUNCIO,Map.of("encargo",21,"versionEncargo",7),TRAZA)).tipo());
        verifyNoInteractions(publicaciones,actuaciones,automaticos);
    }
    @Test void camposAntiguosOPruebaHumanaNoSeDescartanSilenciosamente(){
        for(String campo:List.of("estado","pruebaRevision","eventoHumano")){
            var datos=new java.util.LinkedHashMap<String,Object>(Map.of("encargo",21,"versionEncargo",7,"publicacion",81,"versionPublicacion",3));
            datos.put(campo,"no-autorizado");
            var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PUBLICAR_ANUNCIO,datos,TRAZA));
            assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,error.tipo());
        }
        verifyNoInteractions(publicaciones,actuaciones);
    }
    @Test void obtenerPiezaPreparaRevisionSinDescargarNiAcreditarDifusion(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(actuaciones.prepararEntregaPieza(eq(ACTOR),any(),any(),any())).thenReturn(
                new ActuacionesService.Propuesta("revision-pieza","obtener_pieza_para_difusion",1,"hash",Long.MAX_VALUE,Map.of("pieza",id.toString())));
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.OBTENER_PIEZA_PARA_DIFUSION,
                Map.of("pieza",id.toString(),"versionContenido",1,"huellaOrigen","fuente-observada"),TRAZA);
        assertEquals("revision-pieza",r.propuesta().referencia());assertNull(r.datos());assertNull(r.automatico());
        verify(actuaciones).prepararEntregaPieza(eq(ACTOR),argThat(d->d.pieza().equals(id)&&d.versionContenido()==1&&"fuente-observada".equals(d.huellaOrigen())),any(),any());
        verifyNoInteractions(piezas,fotosComerciales);verifyNoMoreInteractions(actuaciones);
    }
    @Test void prepararPiezaPrivadaConservaCeroExplicitoYSinContactoPersonalPorDefecto(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.preparar(any(),eq(ACTOR),any(),anyString(),any())).thenReturn(estadoPieza(id));
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREPARAR_PIEZA_COMERCIAL,
                Map.of("encargo",21,"versionEncargo",0,"versionFisica",0,"versionGaleria","galeria-observada","formato","PDF_A4"),TRAZA);
        assertNull(r.propuesta());assertNotNull(r.automatico());assertEquals(id.toString(),r.datos().get("pieza"));
        verify(piezas).preparar(argThat(d->d.encargo()==21&&d.versionEncargo()==0&&d.versionFisica()==0&&d.contacto()==null&&d.fotos().isEmpty()),eq(ACTOR),any(),anyString(),any());
        verifyNoInteractions(automaticos,actuaciones,fotosComerciales);
    }
    @Test void seleccionDeUnidadDeComposicionSeConservaSinInventarla(){
        when(piezas.consultarFotosPermitidas(eq(21L),nullable(Long.class),eq(ACTOR))).thenReturn(
                new com.controllocal.service.documentos.SeleccionFotosContextualesService.Observacion(
                        new com.controllocal.service.documentos.SeleccionFotosContextualesService.Contexto("PROPIEDAD",32),List.of(),"galeria-observada"));
        puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA,
                Map.of("encargo",21,"propiedadFicha",32),TRAZA);
        verify(piezas).consultarFotosPermitidas(21,32L,ACTOR);
        puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA,
                Map.of("encargo",21),TRAZA);
        verify(piezas).consultarFotosPermitidas(21,null,ACTOR);
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.preparar(any(),eq(ACTOR),any(),anyString(),any())).thenReturn(estadoPieza(id));
        puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREPARAR_PIEZA_COMERCIAL,
                Map.of("encargo",21,"versionEncargo",0,"versionFisica",0,"versionGaleria","observada","formato","PDF_A4","propiedadFicha",32),TRAZA);
        verify(piezas).preparar(argThat(d->Long.valueOf(32).equals(d.propiedadFicha())),eq(ACTOR),any(),anyString(),any());
        verifyNoInteractions(automaticos,actuaciones,fotosComerciales);
    }
    @Test void versionesPrimitivasFaltantesONulasNoSeConviertenEnCasCero(){
        var entradas=List.of(
                Map.entry(ClienteBrox.OperacionComercial.PUBLICAR_ANUNCIO,Map.<String,Object>of("encargo",21,"versionEncargo",0,"publicacion",81,"versionPublicacion",0)),
                Map.entry(ClienteBrox.OperacionComercial.CREAR_BORRADOR_ANUNCIO,Map.<String,Object>of("encargo",21,"versionEncargo",0)),
                Map.entry(ClienteBrox.OperacionComercial.PREPARAR_PIEZA_COMERCIAL,Map.<String,Object>of("encargo",21,"versionEncargo",0,"versionFisica",0,"versionGaleria","observada","formato","PDF_A4")),
                Map.entry(ClienteBrox.OperacionComercial.OBTENER_PIEZA_PARA_DIFUSION,Map.<String,Object>of("pieza","b288f0f5-f542-41f0-a493-4e3e1a750954","versionContenido",1,"huellaOrigen","observada")),
                Map.entry(ClienteBrox.OperacionComercial.PREVISUALIZAR_PIEZA_COMERCIAL,Map.<String,Object>of("pieza","b288f0f5-f542-41f0-a493-4e3e1a750954","versionContenido",1)));
        for(var entrada:entradas)for(String campo:entrada.getValue().keySet()){
            if(!List.of("encargo","versionEncargo","versionFisica","publicacion","versionPublicacion","versionContenido").contains(campo))continue;
            for(boolean nulo:List.of(false,true)){
                var datos=new java.util.LinkedHashMap<String,Object>(entrada.getValue());
                if(nulo)datos.put(campo,null);else datos.remove(campo);
                var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,entrada.getKey(),datos,TRAZA),entrada.getKey()+"/"+campo+"/"+nulo);
                assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,error.tipo());
            }
        }
        verifyNoInteractions(publicaciones,piezas,actuaciones,automaticos);
    }
    @Test void previsualizacionRetornaReferenciaSinBytesYLaRecuperacionNoVuelveAGenerar(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        var archivo=java.util.UUID.fromString("55e44a66-9239-49e0-aafa-4c20ed30bb17");
        when(piezas.previsualizar(eq(id),eq(1L),eq(ACTOR),any(),anyString(),any())).thenReturn(
                new com.controllocal.service.documentos.PiezasComercialesService.Archivo(archivo,id,1,"REVISION_PRIVADA","pieza.pdf","application/pdf","hash-binario"));
        var r=puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREVISUALIZAR_PIEZA_COMERCIAL,
                Map.of("pieza",id.toString(),"versionContenido",1),TRAZA);
        assertEquals(archivo.toString(),r.datos().get("referencia"));assertFalse(r.datos().containsKey("contenido"));
        String clave=r.automatico().referencia().split(":")[2];
        when(piezas.consultarResultadoGeneracion("previsualizar_pieza_comercial",clave,ACTOR)).thenReturn(java.util.Optional.of(r.datos()));
        assertEquals("EXITO",puerto.consultarActuacion(sesion,r.automatico().referencia(),TRAZA).estado());
        verify(piezas,times(1)).previsualizar(eq(id),eq(1L),eq(ACTOR),any(),eq(clave),any());
        when(piezas.consultarResultadoGeneracion("previsualizar_pieza_comercial",clave,ACTOR)).thenThrow(new AccesoNoAutorizadoException());
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,assertThrows(ErrorBrox.class,()->puerto.consultarActuacion(sesion,r.automatico().referencia(),TRAZA)).tipo());
        verify(piezas,times(1)).previsualizar(eq(id),eq(1L),eq(ACTOR),any(),eq(clave),any());
    }
    @Test void referenciaDeArchivoOUrlNoSeInterpretaComoUnaFotoDelRecurso(){
        for(String invalida:List.of("file:///privado.jpg","https://example.invalid/foto","1-1-1-1-1")){
            var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.EVALUAR_FOTO_LOCAL,
                    Map.of("propiedad",21,"original",invalida),TRAZA));
            assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,error.tipo());
        }
        verifyNoInteractions(fotosComerciales,piezas);
    }
    @Test void falloDeComposicionNoSeConvierteEnArchivoDisponible(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.previsualizar(eq(id),eq(1L),eq(ACTOR),any(),anyString(),any())).thenThrow(new IllegalStateException("Fallo interno del renderer"));
        var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREVISUALIZAR_PIEZA_COMERCIAL,
                Map.of("pieza",id.toString(),"versionContenido",1),TRAZA));
        assertEquals(ErrorBrox.Tipo.FALLO_TECNICO,error.tipo());assertEquals("RESULTADO_INCIERTO",error.codigo());
        assertTrue(error.operacion().referencia().startsWith("auto:previsualizar_pieza_comercial:"));
        verify(piezas,times(1)).previsualizar(eq(id),eq(1L),eq(ACTOR),any(),anyString(),any());
    }
    @Test void composicionNoConfiguradaNoSeConvierteEnFalloTecnicoNiArchivoVacio(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.previsualizar(eq(id),eq(1L),eq(ACTOR),any(),anyString(),any()))
                .thenThrow(new com.controllocal.service.excepcion.CapacidadDeshabilitadaException());
        var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.PREVISUALIZAR_PIEZA_COMERCIAL,
                Map.of("pieza",id.toString(),"versionContenido",1),TRAZA));
        assertEquals(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,error.tipo());assertEquals("CAPACIDAD_DESHABILITADA",error.codigo());
        assertNull(error.operacion());verifyNoInteractions(automaticos,actuaciones);
    }
    @Test void obtenerPiezaSinCompositorNoProducePropuestaNiLlamaOtraVia(){
        when(actuaciones.prepararEntregaPieza(eq(ACTOR),any(),any(),any()))
                .thenThrow(new com.controllocal.service.excepcion.CapacidadDeshabilitadaException());
        var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,ClienteBrox.OperacionComercial.OBTENER_PIEZA_PARA_DIFUSION,
                Map.of("pieza","b288f0f5-f542-41f0-a493-4e3e1a750954","versionContenido",1,"huellaOrigen","fuente-observada"),TRAZA));
        assertEquals(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,error.tipo());assertNull(error.operacion());
        verifyNoInteractions(piezas,automaticos);
    }
    private static com.controllocal.service.documentos.PiezasComercialesService.Estado estadoPieza(java.util.UUID id){
        return new com.controllocal.service.documentos.PiezasComercialesService.Estado(id,1,21,"BROX-FICHA","V08",
                com.controllocal.service.documentos.PiezasComercialesService.Formato.PDF_A4,"PENDIENTE",false,List.of(),"fuente-observada",null,null);
    }
    private static PublicacionService.FichaPublicacion fichaAnuncio(){return new PublicacionService.FichaPublicacion(
            81L,21L,null,null,null,null,"Precio de venta","B","Borrador",null,null,null,null,1,7,4,null,null,null);}

    @Test void consultaOportunidadSinCorteDejaLaEleccionTemporalEnCore() {
        when(oportunidades.consultar(10,OportunidadService.Seccion.RESUMEN,null,ACTOR))
                .thenReturn(new OportunidadService.ConsultaOportunidad(null,OportunidadService.Seccion.RESUMEN,
                        Instant.parse("2026-10-30T12:00:00Z"),null,null,null,null));
        var resultado=puerto.operarComercial(sesion,com.controllocal.asistencia.brox.ClienteBrox.OperacionComercial.CONSULTAR_OPORTUNIDAD,
                Map.of("idOportunidad",10,"seccion","RESUMEN"),TRAZA);
        assertEquals("RESUMEN",resultado.datos().get("seccion"));
        verify(oportunidades).consultar(10,OportunidadService.Seccion.RESUMEN,null,ACTOR);
    }
    @Test void tipoRetiradoNoSeDescartaNiSeConvierteEnCobroDesdeLaAsistencia(){
        for(String tipo:List.of("P","R","C")){
            var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,
                    ClienteBrox.OperacionComercial.REGISTRAR_COBRO,
                    Map.of("comision",7,"versionEsperada",2,"monto",5000,"moneda","PEN","fecha","2026-10-31","tipo",tipo),TRAZA));
            assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,error.tipo());assertEquals(400,error.estadoHttp());
        }
        verifyNoInteractions(actuaciones,automaticos);
    }
    @Test void corteOSeccionInvalidosSonSolicitudRechazadaYNoFalloTecnico() {
        for(var datos:List.<Map<String,Object>>of(Map.of("idOportunidad",10),
                Map.of("idOportunidad",10,"seccion","INVENTADA"),
                Map.of("idOportunidad",10,"seccion","RESUMEN","corte","no-es-instante"),
                Map.of("idOportunidad",10,"seccion","RESUMEN","corte",12))) {
            var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,
                    com.controllocal.asistencia.brox.ClienteBrox.OperacionComercial.CONSULTAR_OPORTUNIDAD,datos,TRAZA));
            assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,error.tipo());
        }
        verifyNoInteractions(oportunidades);
    }
    @Test void unDefectoTemporalInternoDeCoreNoSeOcultaComoEntradaInvalida() {
        when(oportunidades.consultar(10,OportunidadService.Seccion.RESUMEN,null,ACTOR))
                .thenThrow(new java.time.DateTimeException("Defecto interno"));
        var error=assertThrows(ErrorBrox.class,()->puerto.operarComercial(sesion,
                com.controllocal.asistencia.brox.ClienteBrox.OperacionComercial.CONSULTAR_OPORTUNIDAD,
                Map.of("idOportunidad",10,"seccion","RESUMEN"),TRAZA));
        assertEquals(ErrorBrox.Tipo.FALLO_TECNICO,error.tipo());
        assertFalse(error.getMessage().contains("Defecto interno"));
    }

    @Test void explicarTransportaElParDemandaOfertaDelRecorridoSinBuscarOtroGrupo() {
        var evaluacion=new CoincidenciaService.Coincidencia("oferta-estable",4,CoincidenciaService.Capa.L1,
                com.controllocal.service.matching.ContratoMatching.Categoria.COMERCIAL_CONFIRMADA,
                com.controllocal.service.matching.ContratoMatching.Conclusion.CUMPLE,
                com.controllocal.service.matching.ContratoMatching.Conclusion.CUMPLE,
                3,0,3,List.of(),Map.of("ENCARGO:operacion","ALQUILER"),
                com.controllocal.service.matching.ContratoMatching.CoberturaCriterios.COMPLETA,null);
        when(coincidencias.explicar(eq("recorrido-opaco"),eq("demanda-del-grupo-elegido"),eq("oferta-estable"),eq(ACTOR),any()))
                .thenReturn(evaluacion);
        var resultado=puerto.explicarCoincidencia(sesion,"recorrido-opaco","demanda-del-grupo-elegido","oferta-estable",TRAZA);
        assertEquals("oferta-estable",resultado.referencia());assertEquals(3,resultado.s());
        verify(coincidencias).explicar(eq("recorrido-opaco"),eq("demanda-del-grupo-elegido"),eq("oferta-estable"),eq(ACTOR),any());
        verify(coincidencias,never()).consultar(any(),any(),any());
    }

    @Test void unaBusquedaHaceUnaConsultaPaginadaSinPrelecturaDeCapacidadesODeFicha() {
        when(propiedades.listar(any(), eq(ACTOR))).thenReturn(Pagina.vacia());
        assertEquals(List.of(), puerto.buscarPropiedades(sesion, "Surco", TRAZA));
        var filtro = ArgumentCaptor.forClass(PropiedadUniversalService.FiltrosPropiedad.class);
        verify(propiedades).listar(filtro.capture(), eq(ACTOR));
        assertEquals("Surco", filtro.getValue().texto());
        assertEquals(1, filtro.getValue().pagina());
        assertEquals(10, filtro.getValue().tamano());
        verifyNoMoreInteractions(propiedades);
        verifyNoInteractions(capacidades);
    }

    @Test void laExploracionYElExcedenteSeTransportanTipadosSinReescribirElHOriginal() {
        var categoria=com.controllocal.service.matching.ContratoMatching.Categoria.INCOMPATIBLE;
        var cumple=com.controllocal.service.matching.ContratoMatching.Conclusion.CUMPLE;
        var incumple=com.controllocal.service.matching.ContratoMatching.Conclusion.INCUMPLE;
        var limite=new java.math.BigDecimal("110000.50");
        var exploracion=new CoincidenciaService.ExploracionPrecio(CoincidenciaService.ModoExploracion.EXPLORAR,limite,"USD");
        var alternativa=new CoincidenciaService.AlternativaPrecio(new java.math.BigDecimal("110000"),new java.math.BigDecimal("100000"),
                "USD","PRECIO_VENTA",null,new java.math.BigDecimal("10000"),new java.math.BigDecimal("10"));
        var candidato=new CoincidenciaService.Coincidencia("oferta-estable",4,CoincidenciaService.Capa.L0,categoria,cumple,incumple,3,0,3,
                List.of(),Map.of("ENCARGO:operacion","VENTA"),com.controllocal.service.matching.ContratoMatching.CoberturaCriterios.COMPLETA,alternativa);
        var seleccion=new CoincidenciaService.Seleccion(CoincidenciaService.Origen.DEMANDA,47,2,CoincidenciaService.TipoVersion.DEMANDA,
                com.controllocal.service.matching.ContratoMatching.Modalidad.COMERCIAL,java.util.Set.of(CoincidenciaService.Capa.L0),categoria,20,exploracion);
        when(coincidencias.consultar(any(),eq(ACTOR),any())).thenReturn(new CoincidenciaService.Coincidencias(CoincidenciaService.EstadoResultado.DISPONIBLE,
                seleccion,Instant.parse("2026-10-30T17:00:00Z"),"D1-D4-R3","recorrido",List.of(new CoincidenciaService.Grupo("demanda-grupo",2,List.of(candidato))),
                1L,CoincidenciaService.Cobertura.COMPLETA,java.util.Set.of(),null));
        var entrada=new ClienteBrox.ConsultaCoincidencias("DEMANDA",47L,2L,"DEMANDA","COMERCIAL",java.util.Set.of("L0"),"INCOMPATIBLE",20,
                new ClienteBrox.ExploracionPrecio(ClienteBrox.ModoExploracion.EXPLORAR,limite,"USD"));
        var salida=puerto.coincidencias(sesion,entrada,TRAZA);
        var enviada=ArgumentCaptor.forClass(CoincidenciaService.Consulta.class);verify(coincidencias).consultar(enviada.capture(),eq(ACTOR),any());
        assertEquals(exploracion,enviada.getValue().exploracion());assertEquals(java.util.Set.of(CoincidenciaService.Capa.L0),enviada.getValue().capas());
        assertEquals(entrada.exploracion(),salida.seleccion().exploracion());
        var visible=salida.grupos().getFirst().candidatos().getFirst();
        assertEquals("INCOMPATIBLE",visible.categoria());assertEquals(incumple.codigo(),visible.h());
        assertEquals(new ClienteBrox.AlternativaPrecio(alternativa.precio(),alternativa.maximoOriginal(),"USD","PRECIO_VENTA",null,
                alternativa.excesoAbsoluto(),alternativa.excesoPorcentaje()),visible.alternativaPrecio());
        verifyNoInteractions(propiedades,clientes,propietarios);
    }

    @Test void laIdentidadDelContextoNoPuedeCambiarElActorDelRequest() {
        var ajeno = new SesionBrox(2, 11, 21, "AGENTE", sesion.identidadSesion(), sesion.expiraEnEpochSeconds());
        var error = assertThrows(ErrorBrox.class, () -> puerto.buscarClientes(ajeno, "Ana", TRAZA));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE, error.tipo());
        verifyNoInteractions(clientes, adaptadores);
    }

    @Test void otraSesionDelMismoActorNoReutilizaElContextoAnterior() {
        var vieja = new SesionBrox(1, 11, 21, "AGENTE", "otra-sesion", sesion.expiraEnEpochSeconds());
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,
                assertThrows(ErrorBrox.class, () -> puerto.buscarClientes(vieja, "Ana", TRAZA)).tipo());
        verifyNoInteractions(clientes);
    }

    @Test void permisoInsuficienteNoSeConvierteEnDeshabilitacionOVacio() {
        when(adaptadores.de("buscar_persona", ACTOR))
                .thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true, false, false));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,
                assertThrows(ErrorBrox.class, () -> puerto.buscarClientes(sesion, "Ana", TRAZA)).tipo());
        verifyNoInteractions(clientes);
    }

    @Test void capacidadDeshabilitadaNoEjecutaElCaso() {
        when(adaptadores.de("buscar_persona", ACTOR))
                .thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true, true, false));
        assertEquals(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,
                assertThrows(ErrorBrox.class, () -> puerto.buscarClientes(sesion, "Ana", TRAZA)).tipo());
        verifyNoInteractions(clientes);
    }

    @Test void elServicioSigueAplicandoAutoridadAunqueElDescriptorEsteHabilitado() {
        when(clientes.listar(any(), eq(ACTOR))).thenThrow(new AccesoNoAutorizadoException());
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,
                assertThrows(ErrorBrox.class, () -> puerto.buscarClientes(sesion, "Ana", TRAZA)).tipo());
    }

    @Test void unFalloDeServicioNoSePresentaComoResultadoVacioNiFiltraLaCausa() {
        when(clientes.listar(any(), eq(ACTOR))).thenThrow(new IllegalStateException("SQL privado"));
        var error = assertThrows(ErrorBrox.class, () -> puerto.buscarClientes(sesion, "Ana", TRAZA));
        assertEquals(ErrorBrox.Tipo.FALLO_TECNICO, error.tipo());
        assertFalse(error.getMessage().contains("SQL"));
    }

    @Test void laDefinicionSeReutilizaDelMismoMotorConLaOperacionSolicitada() {
        when(motor.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "CASA", "VENTA", "JURISDICCION-TEST", ACTOR))
                .thenReturn(new MotorDeCaptura.DefinicionCaptura(MotorDeCaptura.REGISTRAR_PROPIEDAD,
                        "CASA", List.of("VENTA"), List.of(), List.of(), List.of()));
        assertEquals(List.of("VENTA"), puerto.definicionDe(sesion, "CASA", "VENTA", "JURISDICCION-TEST", TRAZA).operaciones());
        verify(motor).definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, "CASA", "VENTA", "JURISDICCION-TEST", ACTOR);
        verifyNoMoreInteractions(motor);
    }

    @Test void prepararConservaProcedenciaYNoObtieneLaPruebaHumana() {
        var contexto = ContextoDeActuacion.de(com.controllocal.web.seguridad.SesionActual.sesion());
        when(actuaciones.prepararCaptura(eq(ACTOR), eq(7L), eq(contexto), any(), any(), isNull()))
                .thenReturn(new ActuacionesService.Propuesta("referencia", "confirmar_captura", 1,
                        "huella", contexto.expiraEnEpochSeconds(), Map.of("idBorrador", 7L)));
        assertEquals("referencia", puerto.prepararCaptura(sesion, 7L, TRAZA, null).referencia());
        var procedencia = ArgumentCaptor.forClass(com.controllocal.service.soporte.Procedencia.class);
        verify(actuaciones).prepararCaptura(eq(ACTOR), eq(7L), eq(contexto), procedencia.capture(), any(), isNull());
        assertEquals("KAIROS", procedencia.getValue().agente());
        assertEquals("turno", procedencia.getValue().turnoId());
        assertEquals("preparar_actuacion_captura", procedencia.getValue().herramienta());
        verifyNoMoreInteractions(actuaciones);
        verifyNoInteractions(motor);
    }

    @Test void prepararEntregaElSnapshotObservadoConVersionContextoYMetadatosSinReleerlo() {
        var contextoSesion = ContextoDeActuacion.de(com.controllocal.web.seguridad.SesionActual.sesion());
        var conocido = Map.<String, Object>of("tipoPropiedad", "DEPARTAMENTO", "distrito", "Surco");
        var contexto = Map.<String, Object>of("objetoOfrecido", "Unidad TEST 501", "jurisdiccion", "JURISDICCION-TEST");
        var metadatos = Map.<String, Object>of("distrito", Map.of("estado", "CONOCIDO", "evidenciaRef", "documento-test"));
        var observado = new ClienteBrox.EstadoCaptura(7L, "CAP-7", "REGISTRAR_PROPIEDAD", "E", "API", conocido,
                List.of(), null, true, null, null, null, 4, contexto, metadatos, Map.of(), Map.of(), List.of());
        when(actuaciones.prepararCaptura(eq(ACTOR), eq(7L), eq(contextoSesion), any(), any(), anyMap()))
                .thenReturn(new ActuacionesService.Propuesta("referencia", "confirmar_captura", 1,
                        "huella", contextoSesion.expiraEnEpochSeconds(), Map.of("idBorrador", 7L)));
        puerto.prepararCaptura(sesion, 7, TRAZA, observado);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> snapshot = ArgumentCaptor.forClass(Map.class);
        verify(actuaciones).prepararCaptura(eq(ACTOR), eq(7L), eq(contextoSesion), any(), any(), snapshot.capture());
        assertEquals(Map.of("datos", conocido, "version", 4, "contexto", contexto, "metadatos", metadatos), snapshot.getValue());
        verifyNoInteractions(motor);
    }

    @ParameterizedTest @ValueSource(strings = {"EXITO", "RECHAZADO", "EN_VERIFICACION", "PREPARADA"})
    void consultaElResultadoOriginalSinReemitirEscritura(String estado) {
        var contexto = ContextoDeActuacion.de(com.controllocal.web.seguridad.SesionActual.sesion());
        when(actuaciones.consultar(ACTOR, "original", contexto))
                .thenReturn(new ActuacionesService.Resultado("original", estado, null,
                        "EXITO".equals(estado) ? Map.of("idPropiedad", 42L) : null, null));
        assertEquals(estado, puerto.consultarActuacion(sesion, "original", TRAZA).estado());
        verify(actuaciones).consultar(ACTOR, "original", contexto);
        verifyNoMoreInteractions(actuaciones);
        verifyNoInteractions(motor);
    }

    @Test void avanceAutomaticoConservaCorrelacionCompletaYReferenciaCore() {
        var traza = new Traza("API", "KAIROS", "determinista", "1", "c".repeat(80),
                "t".repeat(80), "m".repeat(150), "registra", "abrir_sesion_captura");
        var estado = new MotorDeCaptura.EstadoCaptura(7L, "CAP-7", "REGISTRAR_PROPIEDAD", "E", "API", Map.of("distrito", "SURCO"), List.of("direccion"), null, false, null, null, null, 4, null, Map.of(), null, null, List.of());
        when(automaticos.avanzarCaptura(any(), eq(ACTOR), any(), any()))
                .thenReturn(new ComandosAutomaticosService.ResultadoAutomatico<>("a".repeat(64),
                        "abrir_sesion_captura", 1, estado));
        var respuesta = puerto.avanzarCaptura(sesion, "REGISTRAR_PROPIEDAD", null, null, Map.of(), traza);
        assertEquals(7L, respuesta.resultado().idBorrador());
        assertEquals("auto:abrir_sesion_captura:" + "a".repeat(64), respuesta.operacion().referencia());
        var correlacion = ArgumentCaptor.forClass(ComandosAutomaticosService.CorrelacionAutomatico.class);
        var avance = ArgumentCaptor.forClass(MotorDeCaptura.Avance.class);
        verify(automaticos).avanzarCaptura(avance.capture(), eq(ACTOR), correlacion.capture(), any());
        assertNull(avance.getValue().idBorrador());
        assertNull(avance.getValue().versionEsperada());
        assertEquals(traza.conversacionId(), correlacion.getValue().conversacionId());
        assertEquals(traza.turnoId(), correlacion.getValue().turnoId());
        assertEquals(traza.mensajeId(), correlacion.getValue().mensajeId());
        verifyNoInteractions(motor);
    }

    @Test void respuestaAutomaticaConservaLaVersionRevisadaYTipaLosDatosSinConsultarOtraVersion() {
        var estado = new MotorDeCaptura.EstadoCaptura(7L, "CAP-7", "REGISTRAR_PROPIEDAD", "E", "API",
                Map.of("ascensores", "5"), List.of("direccion"), null, false, null, null, null,
                5, null, Map.of(), null, null, List.of());
        when(automaticos.avanzarCaptura(any(), eq(ACTOR), any(), any()))
                .thenReturn(new ComandosAutomaticosService.ResultadoAutomatico<>("c".repeat(64), "responder_captura", 1, estado));
        var respuesta = puerto.avanzarCaptura(sesion, "REGISTRAR_PROPIEDAD", 7L, 4, Map.of("ascensores", "5"), TRAZA);
        var avance = ArgumentCaptor.forClass(MotorDeCaptura.Avance.class);
        verify(automaticos).avanzarCaptura(avance.capture(), eq(ACTOR), any(), any());
        assertEquals(7L, avance.getValue().idBorrador());
        assertEquals(4, avance.getValue().versionEsperada());
        assertEquals("5", avance.getValue().respuestas().get("ascensores").valor());
        assertEquals(com.controllocal.service.captura.SuficienciaInmueble.EstadoDato.CONOCIDO,
                avance.getValue().respuestas().get("ascensores").estado());
        assertEquals(5, respuesta.resultado().version());
        verifyNoInteractions(motor);
    }

    @Test void falloDespuesDeEmitirConservaReferenciaSinReintentarNiExponerCausa() {
        when(automaticos.clavePara(eq("abrir_sesion_captura"), eq(ACTOR), any())).thenReturn("b".repeat(64));
        when(automaticos.avanzarCaptura(any(), eq(ACTOR), any(), any()))
                .thenThrow(new IllegalStateException("SQL privado"));
        var error = assertThrows(ErrorBrox.class, () -> puerto.avanzarCaptura(sesion, "REGISTRAR_PROPIEDAD", null, null, Map.of(), TRAZA));
        assertEquals("RESULTADO_INCIERTO", error.codigo());
        assertEquals("auto:abrir_sesion_captura:" + "b".repeat(64), error.operacion().referencia());
        assertFalse(error.getMessage().contains("SQL"));
        verify(automaticos, times(1)).avanzarCaptura(any(), eq(ACTOR), any(), any());
        verifyNoInteractions(motor);
    }

    @Test void permisoAntesDeEmitirNoSeConvierteEnIncertidumbre() {
        when(adaptadores.de("abrir_sesion_captura", ACTOR))
                .thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true, false, true));
        var error = assertThrows(ErrorBrox.class, () -> puerto.avanzarCaptura(sesion, "REGISTRAR_PROPIEDAD", null, null, Map.of(), TRAZA));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE, error.tipo());
        assertNull(error.operacion());
        verifyNoInteractions(automaticos, motor);
    }

    @Test void resultadoNoLocalizadoNoAfirmaRollbackNiReemite() {
        String clave = "c".repeat(64);
        when(automaticos.consultarCaptura(ACTOR, "abrir_sesion_captura", clave))
                .thenReturn(java.util.Optional.empty());
        var respuesta = puerto.consultarActuacion(sesion, "auto:abrir_sesion_captura:" + clave, TRAZA);
        assertEquals("EN_VERIFICACION", respuesta.estado());
        assertEquals("RESULTADO_NO_LOCALIZADO", respuesta.motivo());
        assertNull(respuesta.resultado());
        verify(automaticos).consultarCaptura(ACTOR, "abrir_sesion_captura", clave);
        verifyNoMoreInteractions(automaticos);
        verifyNoInteractions(motor, actuaciones);
    }

    @Test void consultaAutomaticaRechazaCapacidadOReferenciaArbitraria() {
        for (String referencia : List.of("auto:sql:" + "c".repeat(64), "auto:registrar_propietario:clave")) {
            assertEquals(ErrorBrox.Tipo.SOLICITUD_RECHAZADA,
                    assertThrows(ErrorBrox.class, () -> puerto.consultarActuacion(sesion, referencia, TRAZA)).tipo());
        }
        verifyNoInteractions(automaticos, motor, actuaciones);
    }

    @Test void consultasSucesivasRevalidanYLaRevocacionNoDevuelveVacio() {
        when(propiedades.listar(any(),eq(ACTOR))).thenReturn(Pagina.vacia());
        when(sesiones.exigirVigente()).thenReturn(ACTOR,ACTOR).thenThrow(new CredencialesInvalidasException());
        assertEquals(List.of(),puerto.buscarPropiedades(sesion,"Surco",TRAZA));
        var error=assertThrows(ErrorBrox.class,()->puerto.buscarPropiedades(sesion,"Surco",TRAZA));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,error.tipo());assertNull(error.operacion());
        verify(propiedades,times(1)).listar(any(),eq(ACTOR));
        verify(sesiones,times(3)).exigirVigente();
    }

    @Test void revocacionDuranteLecturaDeniegaEntregaDelResultadoYaCargado() {
        when(propiedades.listar(any(),eq(ACTOR))).thenReturn(Pagina.vacia());
        when(sesiones.exigirVigente()).thenReturn(ACTOR).thenThrow(new CredencialesInvalidasException());
        var error=assertThrows(ErrorBrox.class,()->puerto.buscarPropiedades(sesion,"Surco",TRAZA));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,error.tipo());
        verify(propiedades,times(1)).listar(any(),eq(ACTOR));
    }

    @Test void volverAlMismoActorEnOtraRevisionNoReutilizaElContextoAntiguo() {
        var actual=new ContextosService.SesionAccesoDTO(coreActual.referencia(),3,coreActual.identidad(),coreActual.activo(),
                coreActual.emitidaEn(),coreActual.venceEn(),coreActual.restricciones());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SesionDeRequest(new TokenService("").de(actual),actual),null,AuthorityUtils.createAuthorityList("ROLE_AGENTE")));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,
                assertThrows(ErrorBrox.class,()->puerto.buscarClientes(sesion,"Ana",TRAZA)).tipo());
        verifyNoInteractions(clientes,adaptadores);
    }

    @Test void revalidacionNoPuedePrestarElActorDeOtroTenantAlContextoAnterior() {
        when(sesiones.exigirVigente()).thenReturn(new Actor(2,11,21,Actor.AGENTE));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,
                assertThrows(ErrorBrox.class,()->puerto.buscarClientes(sesion,"Ana",TRAZA)).tipo());
        verifyNoInteractions(clientes,adaptadores);
    }

    @Test void falloTecnicoAlRevalidarDespuesDeLeerNoSeConvierteEnVacioOPermiso() {
        when(propiedades.listar(any(),eq(ACTOR))).thenReturn(Pagina.vacia());
        when(sesiones.exigirVigente()).thenReturn(ACTOR).thenThrow(new IllegalStateException("SQL privado"));
        var error=assertThrows(ErrorBrox.class,()->puerto.buscarPropiedades(sesion,"Surco",TRAZA));
        assertEquals(ErrorBrox.Tipo.FALLO_TECNICO,error.tipo());assertFalse(error.getMessage().contains("SQL"));
    }

    @Test void revocarTrasAutoConservaReferenciaConsultableSinAfirmarQueElEfectoNoOcurrio() {
        var estado=new MotorDeCaptura.EstadoCaptura(7L, "CAP-7", "REGISTRAR_PROPIEDAD", "E", "API", Map.of(), List.of(), null, false, null, null, null, 4, null, Map.of(), null, null, List.of());
        when(automaticos.avanzarCaptura(any(), eq(ACTOR), any(), any()))
                .thenReturn(new ComandosAutomaticosService.ResultadoAutomatico<>("a".repeat(64),"abrir_sesion_captura",1,estado));
        when(sesiones.exigirVigente()).thenReturn(ACTOR).thenThrow(new CredencialesInvalidasException());
        var error=assertThrows(ErrorBrox.class,()->puerto.avanzarCaptura(sesion, "REGISTRAR_PROPIEDAD", null, null, Map.of(), TRAZA));
        assertEquals(ErrorBrox.Tipo.PERMISO_INSUFICIENTE,error.tipo());assertNotNull(error.operacion());
        assertEquals("auto:abrir_sesion_captura:"+"a".repeat(64),error.operacion().referencia());
        verify(automaticos,times(1)).avanzarCaptura(any(), eq(ACTOR), any(), any());verifyNoInteractions(motor);
    }
}

