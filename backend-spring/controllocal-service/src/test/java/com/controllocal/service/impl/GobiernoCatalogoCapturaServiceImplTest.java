package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.GobiernoCatalogoCapturaService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GobiernoCatalogoCapturaServiceImplTest {
    private final CatalogoAtributoRepository definiciones=mock(CatalogoAtributoRepository.class);
    private final PerfilCatalogoOpcionesRepository perfiles=mock(PerfilCatalogoOpcionesRepository.class);
    private final RevisionCatalogoGobernadoRepository revisiones=mock(RevisionCatalogoGobernadoRepository.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final EntityManager em=mock(EntityManager.class);
    private final GobiernoCatalogoCapturaServiceImpl servicio=new GobiernoCatalogoCapturaServiceImpl(
            definiciones,perfiles,revisiones,eventos,contextos,em,new ObjectMapper().findAndRegisterModules());
    private final Actor gov=new Actor(7,30,41,Actor.TENANT_ADMIN);
    private final ContextosService.SesionAccesoDTO acceso=new ContextosService.SesionAccesoDTO("s".repeat(43),5,
            new ContextosService.IdentidadAutenticada(2,"cuenta","cuenta@example.invalid",null),
            new ContextosService.Contexto("ctx",7,30,41,Actor.TENANT_ADMIN,"EMPRESARIAL","Fixture"),
            Instant.parse("2026-10-28T10:00:00Z"),Instant.parse("2026-10-28T10:30:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);
    private final ContextoSesion sesion=ContextoSesion.de(acceso);
    private final CatalogoAtributoRepository.DefinicionActual base=mock(CatalogoAtributoRepository.DefinicionActual.class);
    private final PerfilCatalogoOpcionesRepository.PerfilActual perfil=mock(PerfilCatalogoOpcionesRepository.PerfilActual.class);

    @BeforeEach void preparar() {
        when(contextos.exigirContexto(sesion.referenciaSesion(),5,gov)).thenReturn(acceso);
        when(base.getId()).thenReturn(10L);when(base.getClave()).thenReturn("zonificacion");
        when(base.getSujeto()).thenReturn("PROPIEDAD");when(base.getTipoDato()).thenReturn("TEXTO");
        when(definiciones.bloquearActualParaCaptura(7,"zonificacion")).thenReturn(Optional.of(base));
        when(perfil.getId()).thenReturn(20L);when(perfil.getVersion()).thenReturn(3L);
        when(perfil.getJurisdiccion()).thenReturn("JURISDICCION-TEST");
        when(perfil.getVersionFuente()).thenReturn("Fuente de prueba v1");when(perfil.getFuente()).thenReturn("Fuente ficticia del test");
        when(perfiles.bloquearActuales(7,10,"JURISDICCION-TEST")).thenReturn(List.of(perfil));
        var opcionVigente=opcion("COD-TEST","Denominacion ficticia");
        when(perfiles.opcionesActuales(20)).thenReturn(List.of(opcionVigente));
    }
    @AfterEach void limpiar(){TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);}
    private static CatalogoAtributoRepository.OpcionActual opcion(String codigo,String rotulo){
        var o=mock(CatalogoAtributoRepository.OpcionActual.class);when(o.getCodigo()).thenReturn(codigo);when(o.getRotulo()).thenReturn(rotulo);return o;
    }
    @Test void zonificacionTexToSeResuelveConPerfilRealSinCambiarLaBase() {
        var r=servicio.validarOpcion(7,"zonificacion","COD-TEST",null,"JURISDICCION-TEST");
        assertTrue(r.valida());assertEquals("CAT:P:20:3",r.referenciaCatalogo());assertEquals("Denominacion ficticia",r.rotulo());
        verify(definiciones,never()).save(any());verify(definiciones,never()).opcionesActuales(anyLong());
    }
    @Test void metadataTerritorialProcedeDelMismoLookupAutorizadoSinUnaSegundaConsulta() {
        var r=servicio.opcionesVigentes(7,"zonificacion","JURISDICCION-TEST");
        assertEquals("JURISDICCION-TEST",r.jurisdiccion());assertEquals("Fuente de prueba v1",r.versionFuente());
        assertEquals("CAT:P:20:3",r.referenciaCatalogo());assertEquals(3L,r.versionDefinicion());
        verify(perfiles,times(1)).bloquearActuales(7,10,"JURISDICCION-TEST");
        verify(perfiles,never()).findByOrganizacionIdAndId(any(),any());
    }
    @Test void constructorNoTerritorialNoFabricaJurisdiccionNiVersionDeFuente() {
        var r=new OpcionesCatalogo("CAT:D:10:4",4L,List.of(new OpcionCatalogo("L1","Lista")),null);
        assertNull(r.jurisdiccion());assertNull(r.versionFuente());
    }
    @Test void sinJurisdiccionNoSeInfierePorCodigoNiSeOfreceTextoLibre() {
        var r=servicio.opcionesVigentes(7,"zonificacion",null);
        assertEquals("JURISDICCION_FALTANTE",r.motivo());assertTrue(r.opciones().isEmpty());verifyNoInteractions(perfiles);
    }
    @Test void perfilAjenoNoPuedeValidarsePorUnaReferenciaQuePareceCorrecta() {
        when(perfiles.bloquearActuales(8,10,"JURISDICCION-TEST")).thenReturn(List.of());
        when(definiciones.bloquearActualParaCaptura(8,"zonificacion")).thenReturn(Optional.of(base));
        assertEquals("PERFIL_NO_DISPONIBLE",servicio.validarOpcion(8,"zonificacion","COD-TEST","CAT:P:20:3","JURISDICCION-TEST").motivo());
        verify(perfiles,never()).opcionesActuales(anyLong());
    }
    @Test void versionViejaNoCaeEnElPerfilActual() {
        var r=servicio.validarOpcion(7,"zonificacion","COD-TEST","CAT:P:20:2","JURISDICCION-TEST");
        assertFalse(r.valida());assertEquals("REFERENCIA_NO_VIGENTE",r.motivo());
    }
    @Test void referenciaDeOtraDefinicionNoSeAceptaAunqueElCodigoCoincida() {
        assertEquals("REFERENCIA_NO_VIGENTE",servicio.validarOpcion(7,"zonificacion","COD-TEST","CAT:D:10:0","JURISDICCION-TEST").motivo());
    }
    @Test void codigoInventadoYOtroNoSeAceptan() {
        for(String valor:List.of("OTRO","codigo inventado",""))assertEquals("OPCION_NO_VIGENTE",servicio.validarOpcion(7,"zonificacion",valor,null,"JURISDICCION-TEST").motivo());
    }
    @Test void opcionRetiradaNoApareceEnLasOpcionesVigentes() {
        when(perfiles.opcionesActuales(20)).thenReturn(List.of());
        assertEquals("CATALOGO_SIN_OPCIONES",servicio.validarOpcion(7,"zonificacion","COD-TEST",null,"JURISDICCION-TEST").motivo());
    }
    @Test void dosPerfilesNoSeResuelvenEscogiendoElPrimero() {
        when(perfiles.bloquearActuales(7,10,"JURISDICCION-TEST")).thenReturn(List.of(perfil,perfil));
        assertEquals("PERFIL_AMBIGUO",servicio.opcionesVigentes(7,"zonificacion","JURISDICCION-TEST").motivo());
        verify(perfiles,never()).opcionesActuales(anyLong());
    }
    @Test void fuenteAusenteEsIncidenciaDeDefinicionNoCodigoValido() {
        when(perfil.getFuente()).thenReturn(" ");
        assertEquals("FUENTE_DEFINICION_FALTANTE",servicio.opcionesVigentes(7,"zonificacion","JURISDICCION-TEST").motivo());
    }
    @Test void falloTecnicoNoSeConfundeConCatalogoVacio() {
        when(perfiles.opcionesActuales(20)).thenThrow(new IllegalStateException("Fallo tecnico de prueba"));
        assertThrows(IllegalStateException.class,()->servicio.opcionesVigentes(7,"zonificacion","JURISDICCION-TEST"));
    }
    @Test void otraClaveTextoNoSeConvierteEnCatalogo() {
        when(base.getClave()).thenReturn("observacion");
        assertEquals("DEFINICION_NO_ES_LISTA",servicio.opcionesVigentes(7,"zonificacion","JURISDICCION-TEST").motivo());
    }
    @Test void listaOrdinariaSinPerfilUsaSuVocabularioGobernado() {
        when(base.getClave()).thenReturn("lista");when(base.getTipoDato()).thenReturn("LISTA");when(base.getVersion()).thenReturn(4L);
        var lista=opcion("L1","Lista");
        when(definiciones.opcionesActuales(10)).thenReturn(List.of(lista));
        var r=servicio.opcionesVigentes(7,"zonificacion",null);
        assertEquals("CAT:D:10:4",r.referenciaCatalogo());assertEquals("L1",r.opciones().getFirst().codigo());
        assertNull(r.jurisdiccion());assertNull(r.versionFuente());
    }
    @Test void lecturaNoIntentaCandadoEnTransaccionReadOnly() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        when(definiciones.actualParaCaptura(7,"zonificacion")).thenReturn(Optional.of(base));
        when(perfiles.actuales(7,10,"JURISDICCION-TEST")).thenReturn(List.of(perfil));
        assertTrue(servicio.validarOpcion(7,"zonificacion","COD-TEST",null,"JURISDICCION-TEST").valida());
        verify(definiciones,never()).bloquearActualParaCaptura(anyLong(),anyString());verify(perfiles,never()).bloquearActuales(anyLong(),anyLong(),anyString());
    }
    @Test void versionSeReconsultaEnCadaActuacionSinCacheDeDecision() {
        assertTrue(servicio.validarOpcion(7,"zonificacion","COD-TEST","CAT:P:20:3","JURISDICCION-TEST").valida());
        when(perfil.getVersion()).thenReturn(4L);
        assertFalse(servicio.validarOpcion(7,"zonificacion","COD-TEST","CAT:P:20:3","JURISDICCION-TEST").valida());
        verify(definiciones,times(2)).bloquearActualParaCaptura(7,"zonificacion");
    }
    @Test void brokerNoConsultaGobiernoNiPreleeDatos() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(new Actor(7,30,40,Actor.BROKER),sesion));
        verifyNoInteractions(contextos,definiciones,perfiles,revisiones,eventos,em);
    }
    @Test void contextoCambiadoNoAutorizaUnaVersion() {
        when(contextos.exigirContexto(sesion.referenciaSesion(),5,gov)).thenThrow(new CredencialesInvalidasException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.crearPerfil(datosPerfil(),gov,sesion));
        verifyNoInteractions(definiciones,perfiles,revisiones,eventos,em);
    }
    @Test void mismaRevisionConRestriccionSobrevenidaNoOpera() {
        when(contextos.exigirContexto(sesion.referenciaSesion(),5,gov)).thenReturn(new ContextosService.SesionAccesoDTO(acceso.referencia(),5,acceso.identidad(),acceso.activo(),
                acceso.emitidaEn(),acceso.venceEn(),new EstadoDeAcceso(null,false,true,Actor.TENANT_ADMIN)));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(gov,sesion));verifyNoInteractions(definiciones,perfiles);
    }
    @Test void baseComunNoSeVersionaPorAdministrarUnTenant() {
        var c=definicion(false);when(definiciones.visiblePorId(7,10)).thenReturn(Optional.of(c));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.versionarDefinicion(10,0,datosDefinicion("Rotulo"),gov,sesion));
        verifyNoInteractions(revisiones,eventos);verify(em,never()).refresh(any(),any(jakarta.persistence.LockModeType.class));
    }
    @Test void idDeOtroTenantEsNoEncontradoSinLeerSuSnapshot() {
        assertThrows(NoEncontradoException.class,()->servicio.consultarDefinicion(99,0L,gov,sesion));
        verifyNoInteractions(revisiones,eventos);
    }
    @Test void versionOmitidaNoSeAsumeCero() {
        var c=definicion(true);when(definiciones.visiblePorId(7,10)).thenReturn(Optional.of(c));
        assertThrows(ReglaNegocioException.class,()->servicio.retirarDefinicion(10,-1,null,gov,sesion));verifyNoInteractions(revisiones,eventos);
    }
    @Test void versionObsoletaNoEscribeSnapshotNiEvento() {
        var c=definicion(true);ReflectionTestUtils.setField(c,"versionDefinicion",3L);when(definiciones.visiblePorId(7,10)).thenReturn(Optional.of(c));
        assertThrows(ConflictoException.class,()->servicio.retirarDefinicion(10,2,null,gov,sesion));verifyNoInteractions(revisiones,eventos);
    }
    @Test void correccionDeRotuloConservaCodigoYSnapshotPrevio() {
        var c=definicion(true);when(definiciones.visiblePorId(7,10)).thenReturn(Optional.of(c));
        when(definiciones.saveAndFlush(c)).thenAnswer(i->{ReflectionTestUtils.setField(c,"versionDefinicion",1L);return c;});
        var r=servicio.versionarDefinicion(10,0,datosDefinicion("Rotulo corregido"),gov,sesion);
        assertEquals(1,r.version());assertEquals("Rotulo corregido",r.datos().rotulo());
        verify(revisiones).save(argThat(v->v.getVersionDefinicion()==0&&v.getSnapshotJson().contains("Rotulo anterior")&&v.getIdRolActor()==41));
        verify(eventos).save(argThat(e->"DEFINICION_CATALOGO_VERSIONADA".equals(e.getTipo())));
    }
    @Test void retiroOpcionalConservaDatoYLecturaSinImponerSustituta() {
        var c=definicion(true);when(definiciones.visiblePorId(7,10)).thenReturn(Optional.of(c));
        when(definiciones.saveAndFlush(c)).thenAnswer(i->{ReflectionTestUtils.setField(c,"versionDefinicion",1L);return c;});
        var r=servicio.retirarDefinicion(10,0,null,gov,sesion);
        assertFalse(r.activa());assertNotNull(r.retiradaEn());assertEquals("extension_test",servicio.consultarDefinicion(10,null,gov,sesion).datos().clave());
        verify(definiciones,never()).delete(any());verify(revisiones).save(any());
    }
    @Test void obligacionDeclaradaNoSeRetiraSinCobertura() {
        var c=definicion(true);c.getAplicaciones().clear();c.getAplicaciones().add(new AplicacionAtributo("T",Exigencia.PUB));
        when(definiciones.visiblePorId(7,10)).thenReturn(Optional.of(c));
        assertThrows(ConflictoException.class,()->servicio.retirarDefinicion(10,0,null,gov,sesion));assertTrue(c.isActivo());verifyNoInteractions(revisiones,eventos);
    }
    @Test void perfilDeZonificacionSeSubordinaALaMismaClaveComunSinSombra() {
        var c=definicion(false);c.setClave("zonificacion");when(definiciones.visiblePorId(7,10)).thenReturn(Optional.of(c));
        when(perfiles.bloquearActuales(7,10,"JURISDICCION-TEST")).thenReturn(List.of());
        when(perfiles.saveAndFlush(any())).thenAnswer(i->{var p=i.getArgument(0,PerfilCatalogoOpciones.class);ReflectionTestUtils.setField(p,"id",20L);return p;});
        var r=servicio.crearPerfil(datosPerfil(),gov,sesion);
        assertEquals("CAT:P:20:0",r.referenciaCatalogo());assertEquals(10,r.datos().idDefinicion());verify(definiciones,never()).save(any());
    }
    private DatosPerfil datosPerfil(){return new DatosPerfil(10,"JURISDICCION-TEST","Version test","Fuente ficticia",List.of(new OpcionDefinida("COD-TEST","Denominacion ficticia",1,true)));}
    private DatosDefinicion datosDefinicion(String rotulo){return new DatosDefinicion("extension_test","PROPIEDAD","TEXTO",null,rotulo,null,null,100,null,null,null,null,
            "v1-test","Fuente ficticia",List.of(new Aplicacion("T",null,"OPC")),List.of());}
    private CatalogoAtributo definicion(boolean privada){
        var c=new CatalogoAtributo();ReflectionTestUtils.setField(c,"id",10L);c.setOrganizacionId(privada?7L:null);c.setDelSistema(!privada);
        c.setClave("extension_test");c.setTipoDato("TEXTO");c.setRotulo("Rotulo anterior");c.setVersionFuente("v1-test");c.setFuenteDefinicion("Fuente ficticia");
        c.getAplicaciones().add(new AplicacionAtributo("T",Exigencia.OPC));return c;
    }
}
