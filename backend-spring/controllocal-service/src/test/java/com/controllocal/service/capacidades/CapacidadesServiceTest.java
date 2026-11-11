package com.controllocal.service.capacidades;

import com.controllocal.domain.captura.BorradorCaptura;
import com.controllocal.service.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.capacidades.CapacidadesService.*;

class CapacidadesServiceTest {
    private final Actor agente = new Actor(1, 20, 30, Actor.AGENTE);
    private final RegistroDeCapacidades registro = new RegistroDeCapacidades("");
    private final AdaptadoresDeCapacidad adaptadores = mock(AdaptadoresDeCapacidad.class);
    private final PropiedadUniversalService propiedades = mock(PropiedadUniversalService.class);
    private final CaptacionService encargos = mock(CaptacionService.class);
    private final EncargoService ciclo = mock(EncargoService.class);
    private final UnidadRelacionadaService origenFisico = mock(UnidadRelacionadaService.class);
    private final MotorDeCaptura captura = mock(MotorDeCaptura.class);
    private final CoincidenciaService coincidencias = mock(CoincidenciaService.class);
    private final ProspeccionService prospecciones = mock(ProspeccionService.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final OportunidadService oportunidades = mock(OportunidadService.class);
    private final SolicitudService solicitudes = mock(SolicitudService.class);
    private final ContratoService contratos = mock(ContratoService.class);
    private final InteraccionService interacciones = mock(InteraccionService.class);
    private final OfertaService ofertas = mock(OfertaService.class);
    private final RequerimientoService demandas = mock(RequerimientoService.class);
    private final com.controllocal.service.colaboracion.ColaboracionService colaboraciones = mock(com.controllocal.service.colaboracion.ColaboracionService.class);
    private final com.controllocal.service.network.NetworkService network = mock(com.controllocal.service.network.NetworkService.class);
    private final com.controllocal.service.actuacion.ActuacionesService actuaciones =
            mock(com.controllocal.service.actuacion.ActuacionesService.class);
    private CapacidadesServiceImpl servicio;
    private final com.controllocal.service.encargo.AccesoEncargo autoridadEncargos=
            mock(com.controllocal.service.encargo.AccesoEncargo.class);
    private final com.controllocal.service.documentos.PiezasComercialesService piezas=
            mock(com.controllocal.service.documentos.PiezasComercialesService.class);
    private final com.controllocal.service.materialia.MaterialIaService materialIa=
            mock(com.controllocal.service.materialia.MaterialIaService.class);
    private final com.controllocal.service.materialia.ResultadosMaterialIa resultadosMaterial=
            mock(com.controllocal.service.materialia.ResultadosMaterialIa.class);

    @Test void metasDistinguenAutoridadEmpresarialEIndependienteSinLeerPropuestas(){
        var q=new Consulta("establecer_meta",null,null,null,null,null,null);
        assertEquals(Estado.PERMISO_INSUFICIENTE,servicio.consultar(q,agente).capacidades().getFirst().estado());
        assertEquals(Estado.HABILITADA,servicio.consultar(new Consulta("proponer_meta",null,null,null,null,null,null),agente).capacidades().getFirst().estado());
        when(metas.puedeDecidir(agente)).thenReturn(true);
        assertEquals(Estado.HABILITADA,servicio.consultar(q,agente).capacidades().getFirst().estado());
        assertEquals(Estado.PERMISO_INSUFICIENTE,servicio.consultar(new Consulta("proponer_meta",null,null,null,null,null,null),agente).capacidades().getFirst().estado());
        verify(metas,times(4)).puedeDecidir(agente);verifyNoMoreInteractions(metas);
    }
    @Test void propuestaMetaResueltaOSustituidaNoOfreceDecisionAunqueConserveLectura(){
        var broker=new Actor(1,21,31,Actor.BROKER);
        var q=new Consulta("decidir_propuesta_meta","PROPUESTA_META","9",null,null,null,null);
        when(metas.capacidadSobrePropuesta(9,broker)).thenReturn(new MetaComercialService.CapacidadMeta(9,0,30,"E",null,true));
        assertEquals(Estado.HABILITADA,servicio.consultar(q,broker).capacidades().getFirst().estado());
        when(metas.capacidadSobrePropuesta(9,broker)).thenReturn(new MetaComercialService.CapacidadMeta(9,1,30,"E",10L,true));
        assertEquals("PROPUESTA_NO_PENDIENTE",servicio.consultar(q,broker).capacidades().getFirst().motivo());
        when(metas.capacidadSobrePropuesta(9,broker)).thenReturn(new MetaComercialService.CapacidadMeta(9,1,30,"O",null,true));
        assertEquals("PROPUESTA_NO_PENDIENTE",servicio.consultar(q,broker).capacidades().getFirst().motivo());
        when(metas.puedeDecidir(broker)).thenReturn(false);
        assertEquals(Estado.PERMISO_INSUFICIENTE,servicio.consultar(q,broker).capacidades().getFirst().estado());
        verify(metas,never()).consultarPropuesta(anyLong(),any());
    }
    @Test void derechosP0SeReutilizanSoloDentroDeLaPeticionYSeRevalidanEnLaSiguiente(){
        var ids=java.util.Set.of("configurar_programa_personal","cambiar_estado_programa_personal","consultar_resultado_programa_personal");
        var primera=servicio.consultar(Consulta.general(),agente).capacidades().stream().filter(d->ids.contains(d.idCore())).toList();
        assertEquals(3,primera.size());assertTrue(primera.stream().allMatch(d->"PLAN_PRO_REQUERIDO".equals(d.motivo())));
        verify(derechosInformes,times(1)).actuales(eq(agente),any());
        when(derechosInformes.actuales(eq(agente),any())).thenReturn(new com.controllocal.service.planes.DerechosDeInformesC7.Derechos(
                com.controllocal.domain.planes.PlanBrox.ONE_PRO,java.util.Set.of(com.controllocal.service.planes.DerechosDeInformesC7.Composicion.OPERACION_ACTUAL),true,java.util.UUID.randomUUID(),1));
        var segunda=servicio.consultar(Consulta.general(),agente).capacidades().stream().filter(d->ids.contains(d.idCore())).toList();
        assertTrue(segunda.stream().allMatch(d->d.estado()==Estado.HABILITADA));
        verify(derechosInformes,times(2)).actuales(eq(agente),any());
        verifyNoInteractions(propiedades,encargos,prospecciones,clientes,oportunidades,solicitudes,contratos,captura,coincidencias,interacciones,demandas);
    }

    @Test void materialLocalNoDependeDeQueIaPuedaGastar(){
        var preparar=servicio.consultar(new CapacidadesService.Consulta("preparar_material_ia","PROPIEDAD","5",null,null,null,null),agente).capacidades().getFirst();
        var solicitar=servicio.consultar(new CapacidadesService.Consulta("solicitar_material_ia",null,null,null,null,null,null),agente).capacidades().getFirst();
        assertEquals(CapacidadesService.Estado.HABILITADA,preparar.estado());
        assertEquals(CapacidadesService.Estado.DESHABILITADA,solicitar.estado());
        assertEquals("MATERIAL_IA_NO_DISPONIBLE",solicitar.motivo());
        assertFalse(((java.util.List<?>)preparar.esquema().get("required")).contains("instruccion"));
    }
    @Test void consultarMaterialAnteriorNoSeBloqueaPorConfiguracionDeGasto(){
        String id=java.util.UUID.randomUUID().toString();
        var ficha=servicio.consultar(new CapacidadesService.Consulta("consultar_material_ia","MATERIAL_IA",id,null,null,null,null),agente).capacidades().getFirst();
        assertEquals(CapacidadesService.Estado.HABILITADA,ficha.estado());
        verify(materialIa).exigirLectura(java.util.UUID.fromString(id),agente);
        verify(materialIa,never()).disponibilidad(any());
    }
    @Test void materialAjenoSeDescribeComoPermisoInsuficiente(){
        String id=java.util.UUID.randomUUID().toString();doThrow(new com.controllocal.service.excepcion.AccesoNoAutorizadoException()).when(materialIa).exigirLectura(any(),eq(agente));
        var ficha=servicio.consultar(new CapacidadesService.Consulta("consultar_material_ia","MATERIAL_IA",id,null,null,null,null),agente).capacidades().getFirst();
        assertEquals(CapacidadesService.Estado.PERMISO_INSUFICIENTE,ficha.estado());
    }
    @Test void normalizaRecursoMaterialConSuUuidSinCambiarLaAutoridad(){
        String id=java.util.UUID.randomUUID().toString();
        var ficha=servicio.consultar(new Consulta("consultar_material_ia"," material_ia "," "+id+" ",null,null,null,null),agente).capacidades().getFirst();
        assertEquals(Estado.HABILITADA,ficha.estado());assertEquals("MATERIAL_IA",ficha.contexto().recurso());assertEquals(id,ficha.contexto().referencia());
        verify(materialIa).exigirLectura(java.util.UUID.fromString(id),agente);
    }
    @Test void materialNoAceptaReferenciaNumericaNiUnRecursoInventado(){
        assertThrows(ReglaNegocioException.class,()->servicio.consultar(new Consulta("consultar_material_ia","MATERIAL_IA","123",null,null,null,null),agente));
        assertThrows(ReglaNegocioException.class,()->servicio.consultar(new Consulta("consultar_material_ia","RECURSO_INVENTADO",java.util.UUID.randomUUID().toString(),null,null,null,null),agente));
        verifyNoInteractions(materialIa);
    }
    @Test void privacidadYSeleccionSonHumanasYSinInputsDeAcreditacionPositiva(){
        var captura=registro.de("registrar_decision_tratamiento_ia");var eleccion=registro.de("seleccionar_foto_material_ia");
        assertEquals(CapacidadesService.Interaccion.HUMANO,captura.interaccion());
        assertEquals(CapacidadesService.Interaccion.HUMANO,eleccion.interaccion());
        assertEquals(java.util.Set.of("evento","referenciaPresentacion","versionCobertura","decision"),((java.util.Map<?,?>)captura.esquema().get("properties")).keySet());
    }
    @Test void fotoOriginalSinSeleccionValidaRecursoUnaVezSinAbrirJobPrivado(){
        var original=java.util.UUID.randomUUID();when(resultadosMaterial.consultarSelecciones(List.of(original),agente))
                .thenReturn(List.of(new com.controllocal.service.materialia.ResultadosMaterialIa.Seleccion(original,0,null,null)));
        var opciones=servicio.consultar(new Consulta(null," material_ia_foto ",original.toString(),null,null,null,null),agente).capacidades();
        assertEquals(java.util.Set.of("consultar_seleccion_material_ia","consultar_versiones_material_ia","seleccionar_foto_material_ia"),
                opciones.stream().map(DescriptorCapacidad::idCore).collect(java.util.stream.Collectors.toSet()));
        assertTrue(opciones.stream().allMatch(d->d.estado()==Estado.HABILITADA&&d.interaccion()==Interaccion.HUMANO));
        verify(resultadosMaterial,times(1)).consultarSelecciones(List.of(original),agente);verifyNoInteractions(materialIa);
    }
    @Test void fotoAjenaConUuidValidoNoSeHabilitaPorNoTenerSeleccion(){
        var original=java.util.UUID.randomUUID();doThrow(new AccesoNoAutorizadoException()).when(resultadosMaterial).consultarSelecciones(List.of(original),agente);
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("seleccionar_foto_material_ia","MATERIAL_IA_FOTO",original.toString(),null,null,null).estado());
        verifyNoInteractions(materialIa);
    }
    @Test void artefactoPermitidoYJobPrivadoUsanLectoresYCachesDistintos(){
        var job=java.util.UUID.randomUUID();doThrow(new AccesoNoAutorizadoException()).when(materialIa).exigirLectura(job,agente);
        when(resultadosMaterial.version(job,agente)).thenReturn(new com.controllocal.service.materialia.ResultadosMaterialIa.Version(job,java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),2,java.time.Instant.EPOCH,java.time.Instant.EPOCH,java.time.Instant.EPOCH.plusSeconds(604800),false,false,true,"DISPONIBLE"));
        var opciones=servicio.consultar(new Consulta(null,"MATERIAL_IA",job.toString(),null,null,null,null),agente).capacidades();
        assertEquals(5,opciones.size());assertEquals(Estado.PERMISO_INSUFICIENTE,opciones.stream().filter(d->d.idCore().equals("consultar_material_ia")).findFirst().orElseThrow().estado());
        assertTrue(opciones.stream().filter(d->!d.idCore().equals("consultar_material_ia")).allMatch(d->d.estado()==Estado.HABILITADA));
        verify(materialIa,times(1)).exigirLectura(job,agente);verify(resultadosMaterial,times(1)).version(job,agente);verify(materialIa,never()).disponibilidad(any());
    }
    @Test void originalRequiereDestinoExplicitoYNoQuedanEleccionesPorJob(){
        var esquema=registro.de("seleccionar_foto_material_ia").esquema();
        assertEquals(java.util.Set.of("original","operacion","archivo","revisionEsperada"),java.util.Set.copyOf((List<?>)esquema.get("required")));
        assertEquals(2,((List<?>)esquema.get("oneOf")).size());
        assertThrows(NoEncontradoException.class,()->registro.de("consultar_revision_material_ia"));
        assertThrows(NoEncontradoException.class,()->registro.de("decidir_resultado_material_ia"));
        assertThrows(ReglaNegocioException.class,()->servicio.consultar(new Consulta("seleccionar_foto_material_ia","MATERIAL_IA_FOTO","5",null,null,null,null),agente));
        verifyNoInteractions(materialIa,resultadosMaterial);
    }
    @Test void piezaSeleccionaDerivadaSoloConIdentidadYRevisionCerradas(){
        var propiedades=(java.util.Map<?,?>)registro.de("preparar_pieza_comercial").esquema().get("properties");
        var fotos=(java.util.Map<?,?>)propiedades.get("fotos");var item=(java.util.Map<?,?>)fotos.get("items");
        var campos=(java.util.Map<?,?>)item.get("properties");var derivada=(java.util.Map<?,?>)campos.get("derivada");
        assertEquals(java.util.Set.of("original","ambiente","rotulo","derivada"),campos.keySet());
        assertEquals(java.util.List.of("original"),item.get("required"));
        assertEquals(java.util.List.of("operacion","archivo","versionSeleccion"),derivada.get("required"));
        assertEquals(false,derivada.get("additionalProperties"));
    }

    private final com.controllocal.service.actividad.ReasignacionDeProspeccion gobiernoProspeccion=mock(com.controllocal.service.actividad.ReasignacionDeProspeccion.class);
    private final com.controllocal.service.planes.DerechosDeInformesC7 derechosInformes=mock(com.controllocal.service.planes.DerechosDeInformesC7.class);
    private final MetaComercialService metas=mock(MetaComercialService.class);
    @BeforeEach void preparar() {
        when(metas.puedeDecidir(any())).thenAnswer(i->((Actor)i.getArgument(0)).esBroker());
        when(derechosInformes.actuales(any(),any())).thenReturn(new com.controllocal.service.planes.DerechosDeInformesC7.Derechos(com.controllocal.domain.planes.PlanBrox.FREE,java.util.Set.of(com.controllocal.service.planes.DerechosDeInformesC7.Composicion.OPERACION_ACTUAL),false,null,0));
        when(piezas.composicionHabilitada()).thenReturn(true);
        when(materialIa.disponibilidad(any())).thenReturn(new com.controllocal.service.materialia.MaterialIaService.Disponibilidad(true,false,"MATERIAL_IA_NO_DISPONIBLE"));
        servicio = new CapacidadesServiceImpl(registro, adaptadores, propiedades, encargos,
                prospecciones, clientes, oportunidades, solicitudes, contratos, captura, coincidencias,
                interacciones, ofertas, actuaciones, ciclo, origenFisico,
                demandas,colaboraciones,network,mock(org.springframework.transaction.PlatformTransactionManager.class),mock(VisitaService.class),autoridadEncargos,
                piezas,materialIa,resultadosMaterial,gobiernoProspeccion,derechosInformes,metas);
        when(adaptadores.de(anyString(), any())).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,true,true));
        when(propiedades.capacidadSobre(5L, null, agente)).thenReturn(vista("A",true,true,true));
        when(actuaciones.capacidadSobre(anyString(),any())).thenReturn(new com.controllocal.service.actuacion.ActuacionesService.CapacidadSobreActuacion("confirmar_captura","P",null,null));
    }

    private PropiedadUniversalService.CapacidadSobrePropiedad vista(String etapa, boolean propia,
                                                                  boolean conocida, boolean editable) {
        return new PropiedadUniversalService.CapacidadSobrePropiedad(5L,"DEPARTAMENTO",etapa,propia,conocida,editable);
    }
    private DescriptorCapacidad consultar(String id, String recurso, String referencia, String operacion, String atributo, String etapa) {
        return servicio.consultar(new Consulta(id,recurso,referencia,operacion,atributo,etapa, null),agente).capacidades().getFirst();
    }

    @Test void publicacionRequiereResponsableActualSinConfundirLecturaConEdicion(){
        var ficha=mock(EncargoService.Ficha.class);
        when(ficha.estado()).thenReturn("A");when(ficha.operacion()).thenReturn("VENTA");
        when(ficha.responsable()).thenReturn(99L);when(ciclo.consultar(6,agente)).thenReturn(ficha);
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("crear_borrador_anuncio","ENCARGO","6",null,null,null).estado());
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("publicar_anuncio","ENCARGO","6",null,null,null).estado());
        assertEquals(Estado.HABILITADA,consultar("consultar_publicaciones","ENCARGO","6",null,null,null).estado());
        when(ficha.responsable()).thenReturn(agente.idRolOperativo());
        assertEquals(Estado.HABILITADA,consultar("crear_borrador_anuncio","ENCARGO","6",null,null,null).estado());
        verifyNoInteractions(encargos,propiedades);
    }
    @Test void publicacionMantieneEsquemaTipadoYNoConservaElSetterAntiguo(){
        assertThrows(com.controllocal.service.excepcion.NoEncontradoException.class,()->registro.de("publicar_encargo"));
        var publicacion=registro.de("publicar_anuncio");
        assertEquals(Interaccion.CONFIRMA,publicacion.interaccion());
        assertEquals(List.of("encargo","versionEncargo","publicacion","versionPublicacion"),publicacion.esquema().get("required"));
        var campos=(java.util.Map<?,?>)publicacion.esquema().get("properties");
        assertFalse(campos.containsKey("estado"));assertFalse(campos.containsKey("pruebaRevision"));
        assertEquals("integer",((java.util.Map<?,?>)campos.get("versionPublicacion")).get("type"));
        assertEquals(java.util.List.of("object","null"),((java.util.Map<?,?>)campos.get("contenido")).get("type"));
        var borrador=registro.de("crear_borrador_anuncio");
        assertEquals(Interaccion.AVISA,borrador.interaccion());
        assertEquals(List.of("encargo","versionEncargo"),borrador.esquema().get("required"));
    }
    @Test void piezasNoConcedenLaFirmaComercialPorSupervisionNiInventanReferenciaNumerica(){
        var ficha=mock(EncargoService.Ficha.class);when(ficha.estado()).thenReturn("P");
        when(ficha.operacion()).thenReturn("VENTA");when(ficha.responsable()).thenReturn(99L);
        when(ciclo.consultar(6,agente)).thenReturn(ficha);
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("consultar_plantillas_y_formatos_pieza","ENCARGO","6",null,null,null).estado());
        when(ficha.responsable()).thenReturn(agente.idRolOperativo());
        assertEquals(Estado.HABILITADA,consultar("preparar_pieza_comercial","ENCARGO","6",null,null,null).estado());
        assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,
                ()->consultar("consultar_estado_pieza","PIEZA","81",null,null,null));
        verifyNoInteractions(piezas);
    }
    @Test void elDescriptorDeTemaMantieneLaBaseSinAdoptarLaPropuestaComercialPorPlan(){
        var campos=(java.util.Map<?,?>)registro.de("preparar_pieza_comercial").esquema().get("properties");
        assertEquals(List.of("marfil","claro"),((java.util.Map<?,?>)campos.get("tema")).get("enum"));
        var regenerar=(java.util.Map<?,?>)registro.de("regenerar_pieza_comercial").esquema().get("properties");
        var preparacion=(java.util.Map<?,?>)((java.util.Map<?,?>)regenerar.get("preparacion")).get("properties");
        assertEquals(campos.get("tema"),preparacion.get("tema"));
    }
    @Test void consultaEstadoPiezaUsaLaMismaPuertaActualSinRegenerarNiEntregar(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.consultarEstado(id,agente)).thenReturn(new com.controllocal.service.documentos.PiezasComercialesService.Estado(
                id,1,6,"BROX-FICHA","V08",com.controllocal.service.documentos.PiezasComercialesService.Formato.PDF_A4,
                "PENDIENTE",false,List.of(),"hash",null,null));
        var descriptor=consultar("consultar_estado_pieza","PIEZA",id.toString(),null,null,null);
        assertEquals(Estado.HABILITADA,descriptor.estado());assertEquals("PENDIENTE",descriptor.contexto().etapa());
        verify(piezas).consultarEstado(id,agente);verifyNoMoreInteractions(piezas);
    }
    @Test void versionDesactualizadaNoSeDifundePeroPermiteRevisionPrivadaHistorica(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.consultarEstado(id,agente)).thenReturn(new com.controllocal.service.documentos.PiezasComercialesService.Estado(
                id,1,6,"BROX-FICHA","V08",com.controllocal.service.documentos.PiezasComercialesService.Formato.PDF_A4,
                "DISPONIBLE",true,List.of("ORIGEN_MODIFICADO"),"hash",id,null));
        var d=consultar("obtener_pieza_para_difusion","PIEZA",id.toString(),null,null,null);
        assertEquals(Estado.DESHABILITADA,d.estado());assertEquals("PIEZA_DESACTUALIZADA",d.motivo());
        assertEquals(Estado.HABILITADA,consultar("previsualizar_pieza_comercial","PIEZA",id.toString(),null,null,null).estado());
        assertEquals(Estado.HABILITADA,consultar("descargar_archivo_pieza","PIEZA",id.toString(),null,null,null).estado());
        assertEquals(Estado.HABILITADA,consultar("consultar_estado_pieza","PIEZA",id.toString(),null,null,null).estado());
        assertEquals(Estado.HABILITADA,consultar("regenerar_pieza_comercial","PIEZA",id.toString(),null,null,null).estado());
    }
    @Test void configuracionVaciaDeshabilitaSoloComposicionNecesariaYSeLeeUnaVezPorPeticion(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.composicionHabilitada()).thenReturn(false);
        when(piezas.consultarEstado(id,agente)).thenReturn(new com.controllocal.service.documentos.PiezasComercialesService.Estado(
                id,1,6,"BROX-FICHA","V08",com.controllocal.service.documentos.PiezasComercialesService.Formato.PDF_A4,
                "PENDIENTE",false,List.of(),"hash",null,null));
        var catalogo=servicio.consultar(new Consulta(null,"PIEZA",id.toString(),null,null,null,null),agente).capacidades();
        for(String capacidad:List.of("previsualizar_pieza_comercial","obtener_pieza_para_difusion")){
            var d=catalogo.stream().filter(c->capacidad.equals(c.idCore())).findFirst().orElseThrow();
            assertEquals(Estado.DESHABILITADA,d.estado());assertEquals("COMPOSICION_NO_CONFIGURADA",d.motivo());
        }
        assertEquals(Estado.HABILITADA,catalogo.stream().filter(c->"consultar_estado_pieza".equals(c.idCore())).findFirst().orElseThrow().estado());
        assertEquals(Estado.HABILITADA,catalogo.stream().filter(c->"regenerar_pieza_comercial".equals(c.idCore())).findFirst().orElseThrow().estado());
        verify(piezas,times(1)).composicionHabilitada();verify(piezas,times(1)).consultarEstado(id,agente);
    }
    @Test void archivosYaCompuestosConservanEntregaAutorizadaSinNecesitarCompositor(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.composicionHabilitada()).thenReturn(false);
        when(piezas.consultarEstado(id,agente)).thenReturn(new com.controllocal.service.documentos.PiezasComercialesService.Estado(
                id,1,6,"BROX-FICHA","V08",com.controllocal.service.documentos.PiezasComercialesService.Formato.PDF_A4,
                "DISPONIBLE",false,List.of(),"hash",java.util.UUID.randomUUID(),java.util.UUID.randomUUID()));
        for(String capacidad:List.of("previsualizar_pieza_comercial","obtener_pieza_para_difusion","descargar_archivo_pieza"))
            assertEquals(Estado.HABILITADA,consultar(capacidad,"PIEZA",id.toString(),null,null,null).estado());
        verify(piezas,never()).composicionHabilitada();
    }
    @Test void falloTecnicoDeLaDisponibilidadNoSePublicaComoConfiguracionApagada(){
        var id=java.util.UUID.fromString("b288f0f5-f542-41f0-a493-4e3e1a750954");
        when(piezas.consultarEstado(id,agente)).thenReturn(new com.controllocal.service.documentos.PiezasComercialesService.Estado(
                id,1,6,"BROX-FICHA","V08",com.controllocal.service.documentos.PiezasComercialesService.Formato.PDF_A4,
                "PENDIENTE",false,List.of(),"hash",null,null));
        when(piezas.composicionHabilitada()).thenThrow(new IllegalStateException("TEST configuración incompatible"));
        assertThrows(IllegalStateException.class,()->consultar("previsualizar_pieza_comercial","PIEZA",id.toString(),null,null,null));
    }

    @Test void gobiernoRecibePermisoInsuficienteAntesDePedirContextoComercial() {
        Actor gobierno = new Actor(1, 20, 40, Actor.TENANT_ADMIN);
        var habilitadas = java.util.Set.of("consultar_capacidades", "catalogo_de_captura", "definicion_de_interacciones","definicion_de_metas",
                "consultar_catalogo_gobernado", "versionar_definicion_gobernada", "retirar_definicion_gobernada");
        var requierenEncargo = java.util.Set.of("reasignar_encargo", "candidatos_reasignacion_encargo","consultar_reasignacion_prospeccion","reasignar_prospeccion");

        var catalogo = servicio.consultar(Consulta.general(), gobierno);

        for (var descriptor : catalogo.capacidades()) {
            assertEquals(habilitadas.contains(descriptor.idCore()) ? Estado.HABILITADA
                            : requierenEncargo.contains(descriptor.idCore()) ? Estado.CONTEXTO_REQUERIDO : Estado.PERMISO_INSUFICIENTE,
                    descriptor.estado(), descriptor.idCore());
        }
        verifyNoInteractions(propiedades, encargos, prospecciones, clientes, oportunidades,
                solicitudes, contratos, captura, coincidencias, interacciones, ofertas, actuaciones, ciclo);
    }

    @ParameterizedTest @ValueSource(strings={"buscar_propiedades", "buscar_persona", "buscar_propietarios", "que_resolver_hoy"})
    void gobiernoNoRecibeUnaCapacidadComercialHabilitadaPorFaltaDePreAuthorize(String id) {
        Actor gobierno = new Actor(1, 20, 40, Actor.TENANT_ADMIN);

        var descriptor = servicio.consultar(new Consulta(id, null, null, null, null, null, null), gobierno)
                .capacidades().getFirst();

        assertEquals(Estado.PERMISO_INSUFICIENTE, descriptor.estado());
        assertEquals("PERMISO_INSUFICIENTE", descriptor.motivo());
        verifyNoInteractions(propiedades, encargos, prospecciones, clientes, oportunidades,
                solicitudes, contratos, captura, coincidencias, interacciones, ofertas, actuaciones, ciclo);
    }

    @ParameterizedTest @ValueSource(strings={"PROPIEDAD", "CLIENTE", "ACTUACION", "CAPTURA", "PETICION_ENCARGO", "SOLICITUD_FIN_ENCARGO", "DOCUMENTO_ENCARGO"})
    void gobiernoConReferenciaComercialNoProvocaPrelecturas(String recurso) {
        Actor gobierno = new Actor(1, 20, 40, Actor.TENANT_ADMIN);

        var descriptores = servicio.consultar(new Consulta(null, recurso, "9", null, null, null, null), gobierno)
                .capacidades();

        assertFalse(descriptores.isEmpty());
        assertTrue(descriptores.stream().allMatch(d -> d.estado() == Estado.PERMISO_INSUFICIENTE));
        verifyNoInteractions(propiedades, encargos, prospecciones, clientes, oportunidades,
                solicitudes, contratos, captura, coincidencias, interacciones, ofertas, actuaciones, ciclo);
    }

    @ParameterizedTest @ValueSource(strings={"presentar_expediente_operacion","evaluar_expediente_operacion",
            "informar_hito_contractual","confirmar_hito_contractual","rectificar_hito_contractual","registrar_cobro",
            "asignar_reparto_comision","autorizar_borrador_adenda","reprogramar_gestion_cobro","concluir_gestion_cobro"})
    void gobiernoNoPreleeNiDecideUnaActuacionComercialC5(String capacidad) {
        Actor gobierno=new Actor(1,20,40,Actor.TENANT_ADMIN);
        var lista=servicio.consultar(new Consulta(capacidad,"ENCARGO","9",null,null,null,null),gobierno).capacidades();
        assertEquals(1,lista.size());assertEquals(Estado.PERMISO_INSUFICIENTE,lista.getFirst().estado());
        verifyNoInteractions(propiedades,encargos,prospecciones,clientes,oportunidades,solicitudes,contratos,
                captura,coincidencias,interacciones,ofertas,actuaciones,ciclo,autoridadEncargos);
    }

    @ParameterizedTest @ValueSource(strings={Actor.AGENTE, Actor.BROKER})
    void losOperadoresConservanLosDescriptoresGeneralesSinEjecutarCasos(String banda) {
        Actor operador = new Actor(1, 20, 30, banda);
        for (String id : List.of("buscar_propiedades", "buscar_persona", "buscar_propietarios", "que_resolver_hoy")) {
            assertEquals(Estado.HABILITADA, servicio.consultar(new Consulta(id, null, null, null, null, null, null), operador)
                    .capacidades().getFirst().estado(), id);
        }
        verifyNoInteractions(propiedades, encargos, prospecciones, clientes, oportunidades,
                solicitudes, contratos, captura, coincidencias, interacciones, ofertas, actuaciones, ciclo);
    }

    @Test void laConsultaGeneralNoLeeCarteraNiCalculaMatching() {
        var respuesta = servicio.consultar(Consulta.general(),agente);
        assertEquals(2,respuesta.version());
        assertFalse(respuesta.capacidades().isEmpty());
        assertTrue(respuesta.capacidades().stream().anyMatch(d -> d.estado()==Estado.CONTEXTO_REQUERIDO));
        verifyNoInteractions(propiedades,encargos,captura,coincidencias);
    }

    @Test void elContextoPublicaSolamenteElActorResueltoEnServidor() {
        var d = consultar("estado_de_propiedad","PROPIEDAD","5",null,null,null);
        assertEquals(Estado.HABILITADA,d.estado());
        assertEquals(1,d.contexto().organizacionId());
        assertEquals(20,d.contexto().personaId());
        assertEquals("A",d.contexto().etapa());
        assertNull(d.contexto().operacion());
    }

    @Test void pedirUnaOperacionNoLaInventaEnLaPropiedad() {
        var d = consultar("estado_de_propiedad","PROPIEDAD","5","VENTA",null,null);
        assertEquals(Estado.DESHABILITADA,d.estado());
        assertEquals("OPERACION_NO_APLICABLE",d.motivo());
        assertNull(d.contexto().operacion());
    }

    @Test void laEtapaSolicitadaNoReescribeLaReal() {
        var d = consultar("editar_propiedad","PROPIEDAD","5",null,null,"INVENTADA");
        assertEquals("ETAPA_CAMBIADA",d.motivo());
        assertEquals("A",d.contexto().etapa());
    }

    @Test void unaEtapaCambiadaSeReleeEnLaSiguientePeticion() {
        assertEquals(Estado.HABILITADA,consultar("editar_propiedad","PROPIEDAD","5",null,null,"A").estado());
        when(propiedades.capacidadSobre(5L,null,agente)).thenReturn(vista("R",true,true,true));
        assertEquals("ETAPA_CAMBIADA",consultar("editar_propiedad","PROPIEDAD","5",null,null,"A").motivo());
        verify(propiedades,times(2)).capacidadSobre(5L,null,agente);
    }

    @Test void perderResponsabilidadImpideOfrecerLaEdicion() {
        when(propiedades.capacidadSobre(5L,null,agente)).thenReturn(vista("A",false,true,true));
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("editar_propiedad","PROPIEDAD","5",null,null,null).estado());
    }

    @Test void elAtributoRetiradoSeLeePeroNoSeOfreceEditar() {
        when(propiedades.capacidadSobre(5L,"servicios_disponibles",agente)).thenReturn(vista("A",true,true,false));
        assertEquals(Estado.HABILITADA,consultar("estado_de_propiedad","PROPIEDAD","5",null,"servicios_disponibles",null).estado());
        assertEquals("ATRIBUTO_NO_EDITABLE",consultar("editar_propiedad","PROPIEDAD","5",null,"servicios_disponibles",null).motivo());
    }

    @Test void unAtributoInexistenteNoSeCompletaPorIntuicion() {
        when(propiedades.capacidadSobre(5L,"inventado",agente)).thenReturn(vista("A",true,false,false));
        assertEquals("ATRIBUTO_NO_APLICABLE",consultar("editar_propiedad","PROPIEDAD","5",null,"inventado",null).motivo());
    }

    @Test void cadaRecursoSeLeeUnaVezPorConsultaSinFichaCompleta() {
        var lista = servicio.consultar(new Consulta(null,"PROPIEDAD","5",null,null,null, null),agente).capacidades();
        assertEquals(java.util.Set.of("estado_de_propiedad", "editar_propiedad", "registrar_observacion_mercado",
                        "consultar_fotos_contextuales", "seleccionar_fotos_contextuales", "evaluar_foto_local",
                        "consultar_origen_fisico_unidad", "registrar_origen_fisico_unidad", "buscar_coincidencias", "registrar_interaccion",
                        "preparar_material_ia","solicitar_material_ia","presentar_tratamiento_material_ia"),
                lista.stream().map(DescriptorCapacidad::idCore).collect(java.util.stream.Collectors.toSet()));
        var origen=lista.stream().filter(d->"registrar_origen_fisico_unidad".equals(d.idCore())).findFirst().orElseThrow();
        assertEquals(Estado.CONTEXTO_REQUERIDO,origen.estado());
        assertEquals(Estado.HABILITADA,lista.stream().filter(d->"consultar_origen_fisico_unidad".equals(d.idCore())).findFirst().orElseThrow().estado());
        verify(propiedades,times(1)).capacidadSobre(5L,null,agente);
        verify(propiedades,never()).consultar(anyLong(),any());
        verifyNoInteractions(origenFisico);
    }

    @Test void elPermisoDenegadoNoRevelaNiCargaLaFila() {
        when(adaptadores.de("editar_propiedad",agente)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,false,true));
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("editar_propiedad","PROPIEDAD","999",null,null,null).estado());
        verifyNoInteractions(propiedades);
    }

    @Test void recursoFueraDeTenantConservaNoEncontradoNoListaVacia() {
        when(propiedades.capacidadSobre(5L,null,agente)).thenThrow(new NoEncontradoException("Propiedad"));
        assertThrows(NoEncontradoException.class,()->consultar("estado_de_propiedad","PROPIEDAD","5",null,null,null));
    }

    @Test void unFalloDeRepositorioNoEsUnaCapacidadDeshabilitada() {
        when(propiedades.capacidadSobre(5L,null,agente)).thenThrow(new IllegalStateException("Fallo SQL privado"));
        assertThrows(IllegalStateException.class,()->consultar("estado_de_propiedad","PROPIEDAD","5",null,null,null));
    }

    @Test void unaGuardaDenegadaSeDistingueDeUnAdaptadorDeshabilitado() {
        when(propiedades.capacidadSobre(5L,null,agente)).thenThrow(new AccesoNoAutorizadoException());
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("estado_de_propiedad","PROPIEDAD","5",null,null,null).estado());
        when(adaptadores.de("estado_de_propiedad",agente)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true,true,false));
        assertEquals(Estado.DESHABILITADA,consultar("estado_de_propiedad","PROPIEDAD","5",null,null,null).estado());
    }

    @Test void elDescriptorNoReconocidoNoHabilitaOtroAdaptador() {
        when(adaptadores.de("buscar_persona",agente)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(false,false,false));
        assertEquals("ADAPTADOR_NO_RECONOCIDO",consultar("buscar_persona",null,null,null,null,null).motivo());
        assertThrows(NoEncontradoException.class,()->consultar("http_sql_inventado",null,null,null,null,null));
    }

    @Test void borradorEjecutadoPermiteRecuperarElResultadoSinRepetirLaEscritura() {
        var b=new MotorDeCaptura.CapacidadSobreCaptura(BorradorCaptura.EJECUTADO,false,true,false,List.of(),null);
        when(captura.capacidadSobre(7,null,null,agente)).thenReturn(b);
        assertEquals(Estado.HABILITADA,consultar("resultado_de_captura","CAPTURA","7",null,null,null).estado());
        assertEquals("CAPTURA_NO_EDITABLE",consultar("preparar_actuacion_captura","CAPTURA","7",null,null,null).motivo());
        assertEquals("CAPTURA_NO_EDITABLE",consultar("responder_captura","CAPTURA","7",null,null,null).motivo());
        verify(captura,never()).ejecutar(anyLong(),any(),any(),any(),any());
    }

    @Test void capturaIncompletaNoSePresentaComoListaParaConfirmar() {
        var b=new MotorDeCaptura.CapacidadSobreCaptura(BorradorCaptura.EN_CURSO,true,false,false,List.of(),null);
        when(captura.capacidadSobre(7,null,null,agente)).thenReturn(b);
        assertEquals("CAPTURA_INCOMPLETA",consultar("preparar_actuacion_captura","CAPTURA","7",null,null,null).motivo());
    }

    @ParameterizedTest @ValueSource(strings={"-1","0","abc","9999999999999999999999"})
    void referenciaInvalidaEsUnaSolicitudInvalida(String id) {
        assertThrows(ReglaNegocioException.class,()->consultar("estado_de_propiedad","PROPIEDAD",id,null,null,null));
    }

    @Test void configuracionDesconocidaDetieneArranqueEnLugarDeInferirUnaCapacidad() {
        assertThrows(IllegalArgumentException.class,()->new RegistroDeCapacidades("inventada"));
        assertThrows(CapacidadDeshabilitadaException.class,()->new RegistroDeCapacidades("buscar_persona").exigirHabilitadas(List.of("buscar_persona")));
    }

    @Test void cadaDescriptorDelEjemploCompartidoEsLaSerializacionRealDelContratoCore() throws Exception {
        java.nio.file.Path raiz=java.nio.file.Path.of("").toAbsolutePath();
        while(raiz!=null && !java.nio.file.Files.isDirectory(raiz.resolve("backend-spring")))raiz=raiz.getParent();
        assertNotNull(raiz);
        var json=new com.fasterxml.jackson.databind.ObjectMapper()
                .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        var esperado=json.readTree(java.nio.file.Files.readString(raiz.resolve(
                "backend-spring/controllocal-app/src/test/resources/contrato-capacidades-v2.json")));
        assertEquals(VERSION, esperado.path("version").intValue());
        assertEquals(java.util.Set.of("buscar_propiedades", "cargar_archivo_contextual",
                        "adjuntar_reutilizar_documento", "consultar_fotos_contextuales", "seleccionar_fotos_contextuales"),
                java.util.stream.StreamSupport.stream(esperado.path("capacidades").spliterator(), false)
                        .map(d -> d.path("idCore").textValue()).collect(java.util.stream.Collectors.toSet()));
        for (var descriptorEsperado : esperado.path("capacidades")) {
            var actual=servicio.consultar(new Consulta(descriptorEsperado.path("idCore").textValue(),
                    null,null,null,null,null, null),agente);
            assertEquals(VERSION, actual.version());
            assertEquals(1, actual.capacidades().size());
            // Cada ejemplo procede de su consulta real; no se inventa una API multiselección.
            assertEquals(descriptorEsperado,
                    json.readTree(json.writeValueAsString(actual)).path("capacidades").get(0));
        }
        verifyNoInteractions(propiedades, encargos, ciclo, captura, coincidencias);
    }

    @Test void capturaConDosOperacionesNoEscogeUnaParaUnImporteAmbiguo() {
        when(captura.capacidadSobre(7,null,"importe",agente)).thenReturn(
                new MotorDeCaptura.CapacidadSobreCaptura("E",true,false,false,List.of("VENTA","ALQUILER"),"OPERACION_REQUERIDA"));
        var d=consultar("responder_captura","CAPTURA","7",null,"importe",null);
        assertEquals(Estado.CONTEXTO_REQUERIDO,d.estado());
        assertNull(d.contexto().operacion());
    }

    @Test void esquemasDePreparacionConservanLosTiposYCamposDeLosAdaptadores() {
        var editar=(java.util.Map<?,?>)registro.de("editar_propiedad").esquema().get("properties");
        assertEquals("array",((java.util.Map<?,?>)editar.get("atributos")).get("type"));
        var matching=(java.util.Map<?,?>)registro.de("buscar_coincidencias").esquema().get("properties");
        assertEquals("integer",((java.util.Map<?,?>)matching.get("idOrigen")).get("type"));
        var evaluacion=(java.util.Map<?,?>)registro.de("evaluar_solicitud").esquema().get("properties");
        assertTrue(evaluacion.containsKey("resultado"));
        assertFalse(evaluacion.containsKey("decision"));
    }

    @Test void cadaEncargoConservaSuOperacionAunqueCompartaPropiedad() {
        var venta=mock(CaptacionService.FichaCaptacion.class);
        var alquiler=mock(CaptacionService.FichaCaptacion.class);
        when(venta.tipoOperacion()).thenReturn("V");when(venta.estado()).thenReturn("A");
        when(alquiler.tipoOperacion()).thenReturn("A");when(alquiler.estado()).thenReturn("A");
        when(encargos.obtener(6,agente)).thenReturn(venta);when(encargos.obtener(7,agente)).thenReturn(alquiler);
        assertEquals("VENTA",consultar("buscar_coincidencias","ENCARGO","6","VENTA",null,null).contexto().operacion());
        assertEquals("ALQUILER",consultar("buscar_coincidencias","ENCARGO","7","ALQUILER",null,null).contexto().operacion());
        assertEquals("OPERACION_NO_APLICABLE",consultar("buscar_coincidencias","ENCARGO","6","ALQUILER",null,null).motivo());
    }

    @Test void nuevaFichaDeEncargoEnVentaConservaOperacionYRechazaConsultaDeAlquiler() {
        var ficha = mock(EncargoService.Ficha.class);
        when(ficha.estado()).thenReturn("A");
        when(ficha.operacion()).thenReturn("VENTA");
        when(ciclo.consultar(6, agente)).thenReturn(ficha);
        var venta = consultar("consultar_encargo", "ENCARGO", "6", "VENTA", null, null);
        assertEquals(Estado.HABILITADA, venta.estado());
        assertEquals("VENTA", venta.contexto().operacion());
        var alquiler = consultar("consultar_encargo", "ENCARGO", "6", "ALQUILER", null, null);
        assertEquals(Estado.DESHABILITADA, alquiler.estado());
        assertEquals("OPERACION_NO_APLICABLE", alquiler.motivo());
        assertEquals("VENTA", alquiler.contexto().operacion());
        verify(ciclo, times(2)).consultar(6, agente);
        verifyNoInteractions(encargos, propiedades);
    }

    @Test void gobiernoReasignaConProyeccionMinimaSinRecibirCondicionesNiHistoriaComercial() {
        Actor gobierno = new Actor(1, 20, 40, Actor.TENANT_ADMIN);
        when(encargos.consultarReasignable(9, gobierno)).thenReturn(
                new CaptacionService.EncargoReasignable(9L, "TEST-9", "A", "Referencia TEST", 31L, "Profesional TEST", 4));
        var descriptores = servicio.consultar(new Consulta(null, "ENCARGO", "9", null, null, null, null), gobierno).capacidades();
        var habilitadas = descriptores.stream().filter(d -> d.estado() == Estado.HABILITADA)
                .map(DescriptorCapacidad::idCore).collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of("reasignar_encargo", "candidatos_reasignacion_encargo"), habilitadas);
        assertTrue(descriptores.stream().filter(d -> !habilitadas.contains(d.idCore()))
                .allMatch(d -> d.estado() == Estado.PERMISO_INSUFICIENTE));
        verify(encargos, times(1)).consultarReasignable(9, gobierno);
        verifyNoMoreInteractions(encargos);
        verifyNoInteractions(ciclo, propiedades, prospecciones, clientes, oportunidades, solicitudes,
                contratos, captura, coincidencias, interacciones, ofertas, actuaciones);
    }

    @Test void unaPuertaNoAutorizadaDePeticionNoCargaElExpediente() {
        when(adaptadores.de("decidir_captacion", agente))
                .thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true, false, true));
        assertEquals(Estado.PERMISO_INSUFICIENTE,
                consultar("decidir_captacion", "PETICION_ENCARGO", "9", null, null, null).estado());
        verifyNoInteractions(ciclo, encargos, propiedades);
    }

    @Test void peticionYFinalizacionUsanLasFacultadesResueltasParaElRecurso() {
        var peticion = mock(EncargoService.Peticion.class);
        when(peticion.estado()).thenReturn("O");
        when(peticion.facultades()).thenReturn(new EncargoService.FacultadesPeticion(true, false));
        when(ciclo.consultarPeticion(9, agente)).thenReturn(peticion);
        assertEquals(Estado.HABILITADA, consultar("reenviar_peticion_encargo", "PETICION_ENCARGO", "9", null, null, null).estado());
        assertEquals("PETICION_NO_DECIDIBLE", consultar("decidir_captacion", "PETICION_ENCARGO", "9", null, null, null).motivo());

        var solicitud = mock(EncargoService.SolicitudFin.class);
        when(solicitud.estado()).thenReturn("P");
        when(solicitud.facultades()).thenReturn(new EncargoService.FacultadesSolicitudFin(true, false, false));
        when(ciclo.consultarSolicitudFin(10, agente)).thenReturn(solicitud);
        assertEquals(Estado.HABILITADA, consultar("cancelar_solicitud_fin_encargo", "SOLICITUD_FIN_ENCARGO", "10", null, null, null).estado());
        assertEquals("SOLICITUD_NO_DECIDIBLE", consultar("decidir_solicitud_fin_encargo", "SOLICITUD_FIN_ENCARGO", "10", null, null, null).motivo());
        verifyNoInteractions(encargos, propiedades);
    }

    @Test void documentoSeAutorizaUnaVezSinLeerLaFichaComercialCompleta() {
        when(ciclo.consultarDocumento(12, agente)).thenReturn(mock(EncargoService.Documento.class));
        var descriptores = servicio.consultar(new Consulta(null, "DOCUMENTO_ENCARGO", "12", null, null, null, null), agente).capacidades();
        assertEquals(java.util.Set.of("previsualizar_documento_encargo", "obtener_documento_encargo",
                        "cargar_archivo_contextual", "adjuntar_reutilizar_documento"),
                descriptores.stream().map(DescriptorCapacidad::idCore).collect(java.util.stream.Collectors.toSet()));
        assertTrue(descriptores.stream().allMatch(d -> d.estado() == Estado.HABILITADA));
        verify(ciclo, times(1)).consultarDocumento(12, agente);
        verifyNoInteractions(encargos, propiedades);
    }

    @Test void c2PublicaSusOperacionesAprobadasSinReemplazarSuContratoPorUnConteo() {
        var esperadas = java.util.Set.of("descartar_captura", "posponer_revision_captura", "consultar_catalogo_gobernado",
                "versionar_definicion_gobernada", "retirar_definicion_gobernada", "consultar_encargo", "consultar_vigencia_encargo",
                "preparar_documento_encargo", "regenerar_documento_encargo", "previsualizar_documento_encargo", "obtener_documento_encargo",
                "cargar_archivo_contextual", "adjuntar_reutilizar_documento", "consultar_fotos_contextuales", "seleccionar_fotos_contextuales",
                "registrar_observacion_mercado",
                "presentar_peticion_encargo", "consultar_peticion_encargo", "reenviar_peticion_encargo",
                "editar_condiciones_encargo", "consultar_composicion_encargo", "declarar_composicion_encargo", "solicitar_fin_encargo",
                "consultar_solicitud_fin_encargo", "cancelar_solicitud_fin_encargo", "decidir_solicitud_fin_encargo",
                "reasignar_encargo", "candidatos_reasignacion_encargo");
        var publicadas = registro.todas().stream().map(RegistroDeCapacidades.Definicion::idCore).collect(java.util.stream.Collectors.toSet());
        assertTrue(publicadas.containsAll(esperadas), "Plan C2 AC043–077 y AC118–121 conservan sus operaciones y puertas.");
        assertFalse(publicadas.contains("adjuntar_evidencia_encargo"));
        assertThrows(NoEncontradoException.class, () -> registro.de("adjuntar_evidencia_encargo"));
        var respuestas = (java.util.Map<?, ?>) ((java.util.Map<?, ?>) registro.de("responder_captura").esquema().get("properties"));
        assertTrue(respuestas.containsKey("respuestas"));
        assertTrue(respuestas.containsKey("contexto"));
        assertEquals("integer", ((java.util.Map<?, ?>) respuestas.get("versionEsperada")).get("type"));
        assertFalse(respuestas.containsKey("datos"), "La captura pública usa respuestas con estado y procedencia.");
        var composicion=(java.util.Map<?,?>)registro.de("consultar_composicion_encargo").esquema().get("properties");
        assertEquals("integer",((java.util.Map<?,?>)composicion.get("versionComposicion")).get("type"),
                "AC076 permite observar una versión histórica; omitirla consulta la vigente sin conceder su lectura");
    }

    @ParameterizedTest @ValueSource(strings={"cargar_archivo_contextual", "adjuntar_reutilizar_documento",
            "consultar_fotos_contextuales", "seleccionar_fotos_contextuales"})
    void rf052NoSeHabilitaSinUnContextoRealNiPreleeUnaCartera(String id) {
        var descriptor = consultar(id, null, null, null, null, null);
        assertEquals(Estado.CONTEXTO_REQUERIDO, descriptor.estado());
        assertEquals("CONTEXTO_REQUERIDO", descriptor.motivo());
        verifyNoInteractions(propiedades, encargos, ciclo, captura, coincidencias, solicitudes, clientes);
    }

    @ParameterizedTest @ValueSource(strings={"cargar_archivo_contextual", "adjuntar_reutilizar_documento",
            "consultar_fotos_contextuales", "seleccionar_fotos_contextuales"})
    void rf052RechazaUnDestinoIncompatibleSinConsultarElCliente(String id) {
        var descriptor = consultar(id, "CLIENTE", "9", null, null, null);
        assertEquals(Estado.DESHABILITADA, descriptor.estado());
        assertEquals("CONTEXTO_NO_APLICABLE", descriptor.motivo());
        verifyNoInteractions(propiedades, encargos, ciclo, clientes);
    }

    @ParameterizedTest @ValueSource(strings={"cargar_archivo_contextual", "adjuntar_reutilizar_documento",
            "consultar_fotos_contextuales", "seleccionar_fotos_contextuales"})
    void rf052PuertaDenegadaNoLeeElRecursoAunqueSeConozcaSuReferencia(String id) {
        when(adaptadores.de(id, agente)).thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true, false, true));
        String recurso = id.contains("fotos") ? "PROPIEDAD" : "ENCARGO";
        assertEquals(Estado.PERMISO_INSUFICIENTE, consultar(id, recurso, "999", null, null, null).estado());
        verifyNoInteractions(propiedades, encargos, ciclo);
    }

    @ParameterizedTest @ValueSource(strings={"cargar_archivo_contextual", "adjuntar_reutilizar_documento"})
    void archivoDeEncargoSeOfreceAlAgenteResponsableAunqueNoPuedaEditarCondiciones(String id) {
        when(ciclo.consultar(6, agente)).thenReturn(encargoConResponsable(agente.idRolOperativo()));
        var descriptor = consultar(id, "ENCARGO", "6", null, null, null);
        assertEquals(Estado.HABILITADA, descriptor.estado());
        assertEquals("VENTA", descriptor.contexto().operacion());
        verify(ciclo).consultar(6, agente);
        verifyNoMoreInteractions(ciclo);
        verifyNoInteractions(propiedades, encargos);
    }

    @ParameterizedTest @ValueSource(strings={"cargar_archivo_contextual", "adjuntar_reutilizar_documento"})
    void leerEncargoAjenoNoOfreceCargaOVinculoConSuResponsabilidad(String id) {
        when(ciclo.consultar(6, agente)).thenReturn(encargoConResponsable(31L));
        assertEquals(Estado.PERMISO_INSUFICIENTE, consultar(id, "ENCARGO", "6", null, null, null).estado());
        verify(ciclo).consultar(6, agente);
        verifyNoInteractions(propiedades, encargos);
    }

    @ParameterizedTest @ValueSource(strings={"cargar_archivo_contextual", "adjuntar_reutilizar_documento"})
    void supervisionDeBrokerNoEquivaleAOperarLaCargaDelResponsable(String id) {
        Actor broker = new Actor(1, 21, 40, Actor.BROKER);
        when(ciclo.consultar(6, broker)).thenReturn(encargoConResponsable(broker.idRolOperativo()));
        var descriptor = servicio.consultar(new Consulta(id, "ENCARGO", "6", null, null, null, null), broker)
                .capacidades().getFirst();
        assertEquals(Estado.PERMISO_INSUFICIENTE, descriptor.estado());
        verify(ciclo).consultar(6, broker);
        verifyNoInteractions(propiedades, encargos);
    }

    @ParameterizedTest @ValueSource(strings={"cargar_archivo_contextual", "adjuntar_reutilizar_documento"})
    void recursoDeArchivoFueraDeAlcanceNoSeSustituyePorUnDescriptorVacio(String id) {
        when(ciclo.consultar(6, agente)).thenThrow(new NoEncontradoException("Encargo"));
        assertThrows(NoEncontradoException.class, () -> consultar(id, "ENCARGO", "6", null, null, null));
        verifyNoInteractions(propiedades, encargos);
    }

    @ParameterizedTest @ValueSource(strings={"consultar_fotos_contextuales", "seleccionar_fotos_contextuales"})
    void seleccionPrivadaUsaLecturaFisicaVigenteSinExigirEdicionDeLaPropiedad(String id) {
        when(propiedades.capacidadSobre(5L, null, agente)).thenReturn(vista("A", false, true, false));
        var descriptor = consultar(id, "PROPIEDAD", "5", null, null, null);
        assertEquals(Estado.HABILITADA, descriptor.estado());
        assertNull(descriptor.contexto().operacion());
        verify(propiedades).capacidadSobre(5L, null, agente);
        verifyNoMoreInteractions(propiedades);
        verifyNoInteractions(ciclo, encargos);
    }

    @Test void brokerConsultaFotosEnSuAmbitoPeroUnaPuertaDeSeleccionDenegadaNoPreleeOtraPropiedad() {
        Actor broker = new Actor(1, 21, 40, Actor.BROKER);
        when(propiedades.capacidadSobre(5L, null, broker)).thenReturn(vista("A", false, true, false));
        assertEquals(Estado.HABILITADA, servicio.consultar(new Consulta("consultar_fotos_contextuales",
                "PROPIEDAD", "5", null, null, null, null), broker).capacidades().getFirst().estado());
        when(adaptadores.de("seleccionar_fotos_contextuales", broker))
                .thenReturn(new AdaptadoresDeCapacidad.Disponibilidad(true, false, true));
        assertEquals(Estado.PERMISO_INSUFICIENTE, servicio.consultar(new Consulta("seleccionar_fotos_contextuales",
                "PROPIEDAD", "999", null, null, null, null), broker).capacidades().getFirst().estado());
        verify(propiedades).capacidadSobre(5L, null, broker);
        verifyNoMoreInteractions(propiedades);
    }

    @ParameterizedTest @ValueSource(strings={"consultar_fotos_contextuales", "seleccionar_fotos_contextuales"})
    void revocarLecturaDeFotosImpideOfrecerLaCapacidadEnLaSiguienteConsulta(String id) {
        assertEquals(Estado.HABILITADA, consultar(id, "PROPIEDAD", "5", null, null, null).estado());
        when(propiedades.capacidadSobre(5L, null, agente)).thenThrow(new NoEncontradoException("Propiedad"));
        assertThrows(NoEncontradoException.class, () -> consultar(id, "PROPIEDAD", "5", null, null, null));
        verify(propiedades, times(2)).capacidadSobre(5L, null, agente);
    }

    @Test void losEsquemasRf052ConservanBinarioSeguroReferenciasVersionadasYDestino() {
        var carga = registro.de("cargar_archivo_contextual");
        assertEquals("ENCARGO", carga.familia());
        assertEquals(Efecto.BORRADOR, carga.efecto());
        assertEquals(Interaccion.HUMANO, carga.interaccion());
        assertEquals(java.util.Set.of("ENCARGO", "DOCUMENTO_ENCARGO"), carga.recursos());
        var entradasCarga = (java.util.Map<?, ?>) carga.esquema().get("properties");
        assertEquals(java.util.Set.of("encargo", "documento", "finalidad", "versionDestino", "archivo"), entradasCarga.keySet());
        assertEquals("binary", ((java.util.Map<?, ?>) entradasCarga.get("archivo")).get("format"));
        assertFalse(entradasCarga.containsKey("contenidoBase64"));

        var adjunto = registro.de("adjuntar_reutilizar_documento");
        assertEquals(Efecto.ESCRITURA, adjunto.efecto());
        assertEquals(Interaccion.HUMANO, adjunto.interaccion());
        assertEquals(carga.recursos(), adjunto.recursos());
        var entradasAdjunto = (java.util.Map<?, ?>) adjunto.esquema().get("properties");
        assertEquals(java.util.Set.of("referenciaOrigen", "encargo", "documento", "finalidad", "versionDestino"), entradasAdjunto.keySet());
        assertEquals("uuid", ((java.util.Map<?, ?>) entradasAdjunto.get("referenciaOrigen")).get("format"));
        assertEquals("integer", ((java.util.Map<?, ?>) entradasAdjunto.get("versionDestino")).get("type"));

        var fotos = registro.de("consultar_fotos_contextuales");
        assertEquals("DOCUMENTOS", fotos.familia());
        assertEquals(Efecto.LECTURA, fotos.efecto());
        assertEquals(Interaccion.AUTO, fotos.interaccion());
        assertEquals(java.util.Set.of("PROPIEDAD"), fotos.recursos());
        var seleccion = registro.de("seleccionar_fotos_contextuales");
        assertEquals("DOCUMENTOS", seleccion.familia());
        assertEquals(Efecto.BORRADOR, seleccion.efecto());
        assertEquals(Interaccion.HUMANO, seleccion.interaccion());
        assertEquals(fotos.recursos(), seleccion.recursos());
        var entradasSeleccion = (java.util.Map<?, ?>) seleccion.esquema().get("properties");
        assertEquals(java.util.Set.of("id", "fotosVersionadas", "finalidad", "versionObservada"), entradasSeleccion.keySet());
        var referencias = (java.util.Map<?, ?>) entradasSeleccion.get("fotosVersionadas");
        assertEquals("array", referencias.get("type"));
        assertEquals("uuid", ((java.util.Map<?, ?>) referencias.get("items")).get("format"));
        assertEquals("string", ((java.util.Map<?, ?>) entradasSeleccion.get("versionObservada")).get("type"));
    }

    private EncargoService.Ficha encargoConResponsable(Long responsable) {
        return new EncargoService.Ficha(6, "TEST-6", "A", 4, responsable, null, null,
                null, null, null, null, null,
                new EncargoService.Facultades(false, false, false, false, false, false, false), 5L, "VENTA", java.util.List.of());
    }

    @ParameterizedTest @ValueSource(strings={"evaluar_expediente_operacion","confirmar_hito_contractual",
            "rectificar_hito_contractual","registrar_cobro","asignar_reparto_comision","autorizar_borrador_adenda",
            "reprogramar_gestion_cobro","concluir_gestion_cobro"})
    void decisionC5UsaAutoridadContextualYNoLaLecturaDelEncargo(String id) {
        when(ciclo.consultar(6,agente)).thenReturn(encargoConResponsable(agente.idRolOperativo()));
        when(autoridadEncargos.puedeDecidir(6,agente)).thenReturn(false);
        var sinPermiso=consultar(id,"ENCARGO","6",null,null,null);
        assertEquals(Estado.PERMISO_INSUFICIENTE,sinPermiso.estado());
        when(autoridadEncargos.puedeDecidir(6,agente)).thenReturn(true);
        assertEquals(Estado.HABILITADA,consultar(id,"ENCARGO","6",null,null,null).estado());
        verify(autoridadEncargos,times(2)).puedeDecidir(6,agente);
    }

    @ParameterizedTest @CsvSource({"seleccionar_fotos_contextuales,fotosVersionadas", "registrar_observacion_mercado,fuente"})
    void campoDocumentalSeValidaEnSuEsquemaSinBuscarloComoAtributoFisico(String id, String campo) {
        var descriptor = consultar(id, "PROPIEDAD", "5", null, campo, null);
        assertEquals(Estado.HABILITADA, descriptor.estado());
        assertEquals(campo, descriptor.contexto().atributo());
        verify(propiedades).capacidadSobre(5L, null, agente);
        verifyNoMoreInteractions(propiedades);
        verifyNoInteractions(ciclo, encargos);
    }

    @ParameterizedTest @ValueSource(strings={"seleccionar_fotos_contextuales", "registrar_observacion_mercado"})
    void campoAjenoAlEsquemaDocumentalSeDeshabilitaSinConsultarUnaDefinicionFisica(String id) {
        var descriptor = consultar(id, "PROPIEDAD", "5", null, "precioReservado", null);
        assertEquals(Estado.DESHABILITADA, descriptor.estado());
        assertEquals("ATRIBUTO_NO_APLICABLE", descriptor.motivo());
        verify(propiedades).capacidadSobre(5L, null, agente);
        verifyNoMoreInteractions(propiedades);
        verifyNoInteractions(ciclo, encargos);
    }
    @Test void origenFisicoRequiereAmbasReferenciasAntesDeConsultarSuGuardia() {
        var d=consultar("registrar_origen_fisico_unidad","PROPIEDAD","5",null,null,null);
        assertEquals(Estado.CONTEXTO_REQUERIDO,d.estado());verifyNoInteractions(origenFisico);
    }
    @Test void origenFisicoDescribeLosDosExtremosConLaUnicaGuardiaCore() {
        when(origenFisico.capacidadSobreOrigen(5,7,agente)).thenReturn(new UnidadRelacionadaService.ContextoOrigen(5,7,0,3));
        var d=servicio.consultar(new Consulta("registrar_origen_fisico_unidad","PROPIEDAD","5",null,null,null,"7"),agente).capacidades().getFirst();
        assertEquals(Estado.HABILITADA,d.estado());assertEquals("7",d.contexto().referenciaRelacionada());
        assertEquals(Interaccion.CONFIRMA,d.interaccion());verify(origenFisico).capacidadSobreOrigen(5,7,agente);
        verifyNoInteractions(propiedades,ciclo,encargos);
    }
    @Test void permisoDenegadoDelSegundoExtremoNoSeConvierteEnFaltaDeContexto() {
        when(origenFisico.capacidadSobreOrigen(5,7,agente)).thenThrow(new AccesoNoAutorizadoException());
        var d=servicio.consultar(new Consulta("registrar_origen_fisico_unidad","PROPIEDAD","5",null,null,null,"7"),agente).capacidades().getFirst();
        assertEquals(Estado.PERMISO_INSUFICIENTE,d.estado());
    }
    @Test void unFalloTecnicoDeOrigenNoSeOcultaComoResultadoVacio() {
        when(origenFisico.capacidadSobreOrigen(5,7,agente)).thenThrow(new IllegalStateException("fallo técnico TEST"));
        assertThrows(IllegalStateException.class,()->servicio.consultar(new Consulta("registrar_origen_fisico_unidad","PROPIEDAD","5",null,null,null,"7"),agente));
    }
    @Test void laCapacidadDeLecturaNoCargaLaHistoriaParaDescribirLaPuerta() {
        assertEquals(Estado.HABILITADA,consultar("consultar_origen_fisico_unidad","PROPIEDAD","5",null,null,null).estado());
        verify(propiedades).capacidadSobre(5,null,agente);verifyNoInteractions(origenFisico);
    }
    @Test void unaPropuestaOrigenNoHabilitaConfirmarCaptura() {
        when(actuaciones.capacidadSobre("abc",agente)).thenReturn(new com.controllocal.service.actuacion.ActuacionesService.CapacidadSobreActuacion("registrar_origen_fisico_unidad","P",null,null));
        var otra=consultar("confirmar_captura","ACTUACION","abc",null,null,null);
        assertEquals(Estado.DESHABILITADA,otra.estado());assertEquals("EFECTO_DE_ACTUACION_NO_APLICABLE",otra.motivo());
        assertEquals(Estado.HABILITADA,consultar("registrar_origen_fisico_unidad","ACTUACION","abc",null,null,null).estado());
    }
    @Test void leerUnaActuacionNoExigeElPermisoFisicoDeRevisarlaOConfirmarla() {
        when(actuaciones.capacidadSobre("abc",agente)).thenReturn(new com.controllocal.service.actuacion.ActuacionesService.CapacidadSobreActuacion(
                "registrar_origen_fisico_unidad","P","PERMISO_INSUFICIENTE","PERMISO_INSUFICIENTE"));
        assertEquals(Estado.HABILITADA,consultar("consultar_actuacion","ACTUACION","abc",null,null,null).estado());
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("revisar_actuacion","ACTUACION","abc",null,null,null).estado());
        assertEquals(Estado.PERMISO_INSUFICIENTE,consultar("registrar_origen_fisico_unidad","ACTUACION","abc",null,null,null).estado());
    }
    @Test void unCambioMaterialInvalidaLaConfirmacionSinCerrarLaLecturaDelOriginal() {
        when(actuaciones.capacidadSobre("abc",agente)).thenReturn(new com.controllocal.service.actuacion.ActuacionesService.CapacidadSobreActuacion(
                "registrar_origen_fisico_unidad","P","CONTEXTO_CAMBIADO","CONTEXTO_CAMBIADO"));
        assertEquals(Estado.HABILITADA,consultar("consultar_actuacion","ACTUACION","abc",null,null,null).estado());
        assertEquals("CONTEXTO_CAMBIADO",consultar("registrar_origen_fisico_unidad","ACTUACION","abc",null,null,null).motivo());
    }
    @Test void decisionNetworkSinAutoridadNoSeConfundeConSolicitudAusente() {
        var n=mock(com.controllocal.service.network.NetworkService.Capacidad.class);
        when(n.estado()).thenReturn("ACTIVO");when(n.estadoSolicitud()).thenReturn("PENDIENTE");
        when(network.capacidadSobreSolicitud("ref",agente)).thenReturn(n);
        var r=consultar("decidir_participacion_network","SOLICITUD_NETWORK","ref",null,null,null);
        assertEquals(Estado.PERMISO_INSUFICIENTE,r.estado());
        verify(network).capacidadSobreSolicitud("ref",agente);
        verify(network,never()).consultarSolicitud(anyString(),any());
        verify(network,never()).previsualizar(anyLong(),any(),any());
    }
    @Test void decisionNetworkConAutoridadPeroEstadoNoAdmitidoEsDeshabilitada() {
        var n=mock(com.controllocal.service.network.NetworkService.Capacidad.class);
        when(n.estado()).thenReturn("INACTIVO");when(n.autoridadDecidir()).thenReturn(true);
        when(network.capacidadSobreSolicitud("ref",agente)).thenReturn(n);
        assertEquals(Estado.DESHABILITADA,consultar("decidir_participacion_network","SOLICITUD_NETWORK","ref",null,null,null).estado());
    }
    @Test void defectoTecnicoDeNetworkNoSeConvierteEnDenegacionOVacio() {
        when(network.capacidadSobreSolicitud("ref",agente)).thenThrow(new IllegalStateException("Contrato inválido"));
        assertThrows(IllegalStateException.class,()->consultar("decidir_participacion_network","SOLICITUD_NETWORK","ref",null,null,null));
    }
    @Test void unaActuacionComparteLaProyeccionEntreLecturaRevisionYConfirmacion() {
        when(actuaciones.capacidadSobre("abc",agente)).thenReturn(new com.controllocal.service.actuacion.ActuacionesService.CapacidadSobreActuacion(
                "registrar_origen_fisico_unidad","P",null,null));
        var lista=servicio.consultar(new Consulta(null,"ACTUACION","abc",null,null,null,null),agente).capacidades();
        assertEquals(java.util.Set.of("consultar_actuacion","revisar_actuacion","confirmar_captura","registrar_origen_fisico_unidad",
                        "retirar_finalizar_demanda","solicitar_colaboracion","invitar_colaboracion_encargo","decidir_colaboracion","revocar_colaboracion",
                        "declarar_canal_colaboracion","solicitar_participacion_network","decidir_participacion_network","desactivar_participacion_network",
                        "establecer_meta","decidir_propuesta_meta","rectificar_comunicacion_reporte","rectificar_interaccion","abrir_oportunidad","registrar_no_continuidad_oportunidad","cancelar_visita",
                        "presentar_oferta","registrar_contraoferta","registrar_aceptacion_oferta","registrar_rechazo_oferta","registrar_retirada_oferta",
                        "presentar_expediente_operacion","evaluar_expediente_operacion","informar_hito_contractual",
                        "confirmar_hito_contractual","rectificar_hito_contractual","registrar_cobro","asignar_reparto_comision",
                        "autorizar_borrador_adenda","reprogramar_gestion_cobro","concluir_gestion_cobro",
                        "publicar_anuncio","actualizar_anuncio_visible","pausar_anuncio","reanudar_anuncio","finalizar_difusion_anuncio",
                        "obtener_pieza_para_difusion"),
                lista.stream().map(DescriptorCapacidad::idCore).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.Set.of("consultar_actuacion","revisar_actuacion","registrar_origen_fisico_unidad"),
                lista.stream().filter(d->d.estado()==Estado.HABILITADA).map(DescriptorCapacidad::idCore).collect(java.util.stream.Collectors.toSet()));
        assertTrue(lista.stream().filter(d->!java.util.Set.of("consultar_actuacion","revisar_actuacion","registrar_origen_fisico_unidad","establecer_meta","decidir_propuesta_meta").contains(d.idCore()))
                .allMatch(d->d.estado()==Estado.DESHABILITADA));
        assertTrue(lista.stream().filter(d->java.util.Set.of("establecer_meta","decidir_propuesta_meta").contains(d.idCore()))
                .allMatch(d->d.estado()==Estado.PERMISO_INSUFICIENTE));
        assertEquals(Estado.DESHABILITADA,lista.stream().filter(d->"confirmar_captura".equals(d.idCore())).findFirst().orElseThrow().estado());
        verify(actuaciones,times(1)).capacidadSobre("abc",agente);
        verify(actuaciones,never()).capacidadDe(anyString(),any());
        verifyNoInteractions(origenFisico,propiedades,ciclo,captura);
    }

    @Test void gobiernoDeProspeccionLeeSoloMetadatosDelCasoAutorizado(){
        var admin=new Actor(1,20,40,Actor.TENANT_ADMIN);
        when(gobiernoProspeccion.consultar(8,admin)).thenReturn(new com.controllocal.service.actividad.ReasignacionDeProspeccion.Contexto(8,3,50));
        var c=servicio.consultar(new Consulta("consultar_reasignacion_prospeccion","PROSPECCION","8",null,null,null,null),admin);
        assertEquals(Estado.HABILITADA,c.capacidades().getFirst().estado());verify(gobiernoProspeccion).consultar(8,admin);
        verifyNoInteractions(prospecciones,propiedades,encargos,ciclo);
    }

    @Test void invitacionConsultaVigenciaCanonicaSinAnticiparEncargoFuturo(){
        var f=mock(EncargoService.Ficha.class);when(f.estado()).thenReturn("A");when(f.operacion()).thenReturn("VENTA");when(f.responsable()).thenReturn(agente.idRolOperativo());
        when(f.vigencia()).thenReturn(new EncargoService.Vigencia(false,false,false,false,List.of("VIGENCIA_ACTUAL"),java.time.LocalDate.now()));when(ciclo.consultar(8,agente)).thenReturn(f);
        var d=servicio.consultar(new Consulta("invitar_colaboracion_encargo","ENCARGO","8",null,null,null,null),agente).capacidades().getFirst();
        assertEquals(Estado.DESHABILITADA,d.estado());assertEquals("ENCARGO_NO_HABILITADO",d.motivo());verifyNoInteractions(encargos);
    }
}
