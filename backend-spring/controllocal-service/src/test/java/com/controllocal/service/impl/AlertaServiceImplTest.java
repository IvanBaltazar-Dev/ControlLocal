package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.HechoAsunto;
import com.controllocal.domain.persona.*;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.AlertaRepository.Cabecera;
import com.controllocal.service.*;
import com.controllocal.service.ProspeccionService.RevisionSeguimiento;
import com.controllocal.service.actividad.SeguimientoDeProspeccion;
import com.controllocal.service.actividad.SeguimientoDeProspeccion.Necesidad;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.service.soporte.FuentesAsuntos.*;
import com.controllocal.persistence.repositorio.FuentesAsuntosRepository.Recurso;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Permisos antes de mensaje/página/conteo, y R27 como fuente única de las presentaciones. */
class AlertaServiceImplTest {
    private final AlertaRepository repo=mock(AlertaRepository.class);
    private final Alcances alcances=mock(Alcances.class);
    private final SeguimientoDeProspeccion seguimiento=mock(SeguimientoDeProspeccion.class);
    private final FuentesAsuntos fuentes=mock(FuentesAsuntos.class);
    private final FuentesAsuntosRepository origenes=mock(FuentesAsuntosRepository.class);
    private final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    private final AutoridadAsuntos autoridad=mock(AutoridadAsuntos.class);
    private final AlertaServiceImpl service=new AlertaServiceImpl(repo,alcances,seguimiento,fuentes,origenes,contexto,autoridad);
    private final Actor actor=new Actor(1,2,30,"AGENTE");
    private static final String A="a".repeat(64),B="b".repeat(64);
    private static final Documentos JSON_C7=new Documentos(JsonMapper.builder().findAndAddModules().build());

    @BeforeEach void fuentesDePruebaDeclaradas(){
        MontajeAsuntosC7.fuentesDeclaradas(fuentes,autoridad,actor);
        when(origenes.decisionesActualesEncargo(anyLong(),anyCollection())).thenReturn(List.of());
        when(origenes.contextos(eq(1L),anyCollection())).thenAnswer(i->{
            Collection<Recurso> recursos=i.getArgument(1);var salida=new HashMap<Recurso,FuentesAsuntosRepository.Contexto>();
            for(var r:recursos)salida.put(r,new FuentesAsuntosRepository.Contexto(r,30L,30L,null,null,null));
            return Map.copyOf(salida);
        });
        when(repo.nombresDeAgentes(eq(1L),anyCollection())).thenAnswer(i->{
            Collection<Long> ids=i.getArgument(1);return ids.stream().map(id->(AlertaRepository.NombreAgente)new NombreDeclarado(id,"Valentina Mora")).toList();
        });
    }

    @Test void emitirConservaLosDatosDelEmisorYNoLosConvierteEnRecontacto() {
        var causa=causa("SOLICITUD_ALQUILER",5,30);var origen=causa.origen();
        when(fuentes.resolver(eq(causa.recurso()),eq(causa.finalidad()),eq(origen),eq(actor))).thenReturn(causa);
        service.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_EVALUADA,Alerta.INFO,"SOLICITUD_ALQUILER",5L,30L,"Mensaje conocido",origen),actor);
        verify(repo).crearCausaC7(eq(1L),eq(Alerta.SOLICITUD_EVALUADA),eq(Alerta.INFO),eq("SOLICITUD_ALQUILER"),eq(5L),eq(30L),
                eq("Mensaje conocido"),eq(huella(causa)),eq(JSON_C7.objetoCanonico(causa)),isNull());
        verify(repo,never()).crearRecontacto(anyLong(),anyLong(),anyLong(),anyString());verify(repo,never()).save(any());
    }
    @Test void datosIncompletosDelEmisorNoFabricanAviso() {
        assertThrows(ReglaNegocioException.class,()->service.emitir(null,actor));
        assertThrows(ReglaNegocioException.class,()->service.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_EVALUADA,Alerta.INFO,"SOLICITUD_ALQUILER",5L,null,"m",Origen.de("EVALUACION_SOLICITUD",1)),actor));
        assertThrows(ReglaNegocioException.class,()->service.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_EVALUADA,Alerta.INFO,"SOLICITUD_ALQUILER",0L,30L,"m",Origen.de("EVALUACION_SOLICITUD",1)),actor));
        assertThrows(ReglaNegocioException.class,()->service.emitir(new AlertaService.DatosAlerta(Alerta.SOLICITUD_EVALUADA,Alerta.INFO,"SOLICITUD_ALQUILER",5L,30L,"m",null),actor));
        assertThrows(ReglaNegocioException.class,()->service.emitir(new AlertaService.DatosAlerta(Alerta.SIN_AVANCE,Alerta.INFO,"SOLICITUD_ALQUILER",5L,30L,"m",Origen.de("EVALUACION_SOLICITUD",1)),actor));
        verifyNoInteractions(repo,fuentes,autoridad);
    }
    @Test void filtraRecontactoAntesDeHidratarContarYPaginar() {
        alcance();
        when(seguimiento.necesidadesVencidas(actor)).thenReturn(List.of(necesidad(11,A,30,true)));
        when(repo.cabecerasActivas(eq(1L),eq(false),anyCollection())).thenReturn(List.of(
                cabecera(4,11,30,A,Alerta.ACTIVA,true),cabecera(5,12,30,B,Alerta.ACTIVA,true)));
        var visible=alerta("PROSPECCION",11);visible.setTipo(Alerta.SIN_RESPUESTA);visible.setMarcaCausa(A);
        when(repo.buscarAutorizadas(eq(1L),eq(List.of(4L)),any())).thenReturn(new PageImpl<>(List.of(visible)));
        var resultado=service.listar(1,20,actor);
        assertEquals(1,resultado.total());assertEquals(1,resultado.items().size());
        var orden=inOrder(seguimiento,repo);orden.verify(repo).cabecerasActivas(eq(1L),eq(false),anyCollection());
        orden.verify(seguimiento).necesidadesVencidas(actor);
        orden.verify(repo).buscarAutorizadas(eq(1L),eq(List.of(4L)),any());
    }
    @Test void unRecontactoSinPermisoNoLlegaAlConteoNiAlMensaje() {
        alcance();when(repo.cabecerasActivas(eq(1L),eq(false),anyCollection()))
                .thenReturn(List.of(cabecera(4,11,30,A,Alerta.ACTIVA,true)));
        assertTrue(service.listar(1,20,actor).items().isEmpty());
        verify(repo,never()).buscarAutorizadas(anyLong(),anyCollection(),any());
    }
    @Test void paginaYNombreActualDeLosOtrosAvisosConservanElContrato() {
        alcance();when(repo.cabecerasActivas(eq(1L),eq(false),anyCollection()))
                .thenReturn(List.of(cabecera(4,5,30,null,Alerta.ACTIVA,false)));
        var almacenada=spy(alerta("SOLICITUD_ALQUILER",5));var recurso=new Recurso("SOLICITUD_ALQUILER",5);
        var original=almacenada.getAgente();
        doThrow(new AssertionError("La entrega no materializa al agente histórico ni su persona")).when(almacenada).getAgente();
        when(repo.buscarAutorizadas(eq(1L),eq(List.of(4L)),any())).thenReturn(new PageImpl<>(List.of(almacenada)));
        when(origenes.contextos(eq(1L),anyCollection())).thenReturn(Map.of(recurso,new FuentesAsuntosRepository.Contexto(recurso,33L,null,null,null,null)));
        when(repo.nombresDeAgentes(eq(1L),eq(Set.of(33L)))).thenReturn(List.of(new NombreDeclarado(33L,"Responsable actual TEST")));
        var resultado=service.listar(0,9999,actor);var ficha=resultado.items().getFirst();
        assertEquals("Responsable actual TEST",ficha.agenteNombre());assertEquals(33L,ficha.idAgente());assertEquals("solicitud-detail/5",ficha.ruta());
        assertEquals(30L,original.getId());verify(origenes,times(2)).contextos(eq(1L),anyCollection());
        var pagina=org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repo).buscarAutorizadas(eq(1L),eq(List.of(4L)),pagina.capture());
        assertEquals(0,pagina.getValue().getPageNumber());assertEquals(100,pagina.getValue().getPageSize());
        verify(seguimiento).necesidadesActuales(actor);
        verify(seguimiento).marcasActuales(actor);
    }
    @Test void rutasNoSustituidasNoSeInventan() {
        // Obligación de representación, sin fabricar un productor para INMUEBLE.
        for(String entidad:List.of("INMUEBLE","CAPTACION")) {
            assertNull(ReflectionTestUtils.invokeMethod(service,"ruta",entidad,5L));
        }
        verifyNoInteractions(repo,fuentes,autoridad);
    }
    @Test void falloDeLaConciliacionNoSeDevuelveComoCampanaVacia(){
        when(seguimiento.necesidadesActuales(actor)).thenThrow(new IllegalStateException("Fallo técnico TEST"));
        assertThrows(IllegalStateException.class,()->service.listar(1,20,actor));
        verify(repo,never()).buscarAutorizadas(anyLong(),anyCollection(),any(Pageable.class));
    }
    @Test void brokerSinEquipoRecibeVacioSinBuscarMensajes() {
        var broker=new Actor(1,3,40,"BROKER");when(alcances.de(broker)).thenReturn(new Alcances.Alcance(1,false,List.of()));
        assertTrue(service.listar(1,20,broker).items().isEmpty());verifyNoInteractions(repo,seguimiento);
    }
    @Test void atenderReautorizaAntesDeHidratar() {
        alcance();when(repo.cabecera(1,4)).thenReturn(Optional.of(cabecera(4,11,30,A,Alerta.ACTIVA,true)));
        when(seguimiento.necesidadesVencidas(actor)).thenReturn(List.of(necesidad(11,A,30,true)));
        when(repo.atenderSiActiva(1,4,A,null)).thenReturn(1);
        assertTrue(service.atender(4,actor));
        var orden=inOrder(seguimiento,repo);orden.verify(repo).cabecera(1,4);
        orden.verify(seguimiento).necesidadesVencidas(actor);orden.verify(repo).atenderSiActiva(1,4,A,null);
        orden.verify(seguimiento).necesidadesVencidas(actor);
        verify(repo,never()).buscarAutorizadas(anyLong(),anyCollection(),any());verify(repo,never()).save(any());
    }
    @Test void yaAtendidaDentroDelAmbitoDevuelveFalseSinHidratar() {
        alcance();when(repo.cabecera(1,4)).thenReturn(Optional.of(cabecera(4,5,30,null,Alerta.ATENDIDA,false)));
        assertFalse(service.atender(4,actor));verify(repo,never()).buscarAutorizadas(anyLong(),anyCollection(),any());
        verify(repo,never()).atenderSiActiva(anyLong(),anyLong(),nullable(String.class),nullable(String.class));
    }
    @Test void otroAgenteNoObtieneFalseComoOraculoDeExistencia() {
        alcance();when(repo.cabecera(1,4)).thenReturn(Optional.of(cabecera(4,5,99,null,Alerta.ATENDIDA,false)));
        when(autoridad.legibles(anyCollection(),eq(actor))).thenReturn(Set.of());
        assertThrows(NoEncontradoException.class,()->service.atender(4,actor));
        verify(fuentes,never()).leer(anyString());verify(repo,never()).atenderSiActiva(anyLong(),anyLong(),nullable(String.class),nullable(String.class));
        verify(repo,never()).buscarAutorizadas(anyLong(),anyCollection(),any());
    }
    @Test void otroTenantONoEncontradaNoHidrata() {
        alcance();when(repo.cabecera(1,4)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class,()->service.atender(4,actor));verify(repo,never()).buscarAutorizadas(anyLong(),anyCollection(),any());
        verify(repo,never()).atenderSiActiva(anyLong(),anyLong(),nullable(String.class),nullable(String.class));
    }
    @Test void revocacionDeContactoImpideAtenderAntesDeLeerMensaje() {
        alcance();when(repo.cabecera(1,4)).thenReturn(Optional.of(cabecera(4,11,30,A,Alerta.ACTIVA,true)));
        assertThrows(NoEncontradoException.class,()->service.atender(4,actor));verify(repo,never()).buscarAutorizadas(anyLong(),anyCollection(),any());
        verify(repo,never()).atenderSiActiva(anyLong(),anyLong(),nullable(String.class),nullable(String.class));
    }
    @Test void causaCanceladaOAtendidaNoSeRegeneraYLaNuevaNoSeSuprime() {
        var nueva=necesidad(11,B,30,true);
        when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(nueva));
        when(seguimiento.marcasActuales(actor)).thenReturn(Set.of(B));
        when(seguimiento.necesidadesVencidas(actor)).thenReturn(List.of(nueva));
        when(repo.cabecerasRecontacto(eq(1L),anyCollection())).thenReturn(List.of(cabecera(4,11,30,A,Alerta.ATENDIDA,true)));
        when(repo.crearRecontacto(1,11,30,B)).thenReturn(1,0);
        assertEquals(1,service.sincronizarRecontacto(actor));assertEquals(0,service.sincronizarRecontacto(actor));
        verify(repo,never()).retirarRecontactoSustituido(anyLong(),anyLong(),anyString());
        // La exclusión mutua real de INSERT+índice se demuestra en PostgreSQL por Root.
    }
    @Test void mismaMarcaYaAtendidaConservaSuRegistroPorLaUnicidadEnTodosLosEstados() {
        var n=necesidad(11,A,30,true);
        when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(n));when(seguimiento.marcasActuales(actor)).thenReturn(Set.of(A));
        when(seguimiento.necesidadesVencidas(actor)).thenReturn(List.of(n));
        when(repo.cabecerasRecontacto(eq(1L),anyCollection())).thenReturn(List.of(cabecera(4,11,30,A,Alerta.ATENDIDA,true)));
        when(repo.crearRecontacto(1,11,30,A)).thenReturn(0);
        assertEquals(0,service.sincronizarRecontacto(actor));verify(repo,never()).save(any());
    }
    @Test void dosDestinatariosConMismoRecursoMantienenDosMarcasSinElegirUna() {
        var a=necesidad(11,A,33,true);var b=necesidad(11,B,33,true);
        when(seguimiento.necesidadesVencidas(actor)).thenReturn(List.of(a,b));
        when(repo.crearRecontacto(1,11,33,A)).thenReturn(1);when(repo.crearRecontacto(1,11,33,B)).thenReturn(1);
        assertEquals(2,service.sincronizarRecontacto(actor));
        verify(repo,never()).crearRecontacto(eq(1L),eq(11L),eq(30L),anyString());
    }
    @Test void fechaPlanificadaSinIntentoNoAcreditaSinRespuesta() {
        when(seguimiento.necesidadesVencidas(actor)).thenReturn(List.of(necesidad(11,A,30,false)));
        assertEquals(0,service.sincronizarRecontacto(actor));verify(repo,never()).crearRecontacto(anyLong(),anyLong(),anyLong(),anyString());
    }
    @Test void posponerORestringirLaMismaCausaNoEsResolverNiCrearOtroAviso() {
        var n=necesidad(11,A,30,true);when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(n));
        when(seguimiento.marcasActuales(actor)).thenReturn(Set.of(A));
        when(repo.cabecerasRecontacto(eq(1L),anyCollection())).thenReturn(List.of(cabecera(4,11,30,A,Alerta.ACTIVA,true)));
        assertEquals(0,service.sincronizarRecontacto(actor));
        verify(repo,never()).retirarRecontactoSustituido(anyLong(),anyLong(),anyString());
        verify(repo,never()).crearRecontacto(anyLong(),anyLong(),anyLong(),anyString());
    }
    @Test void unaCausaAntiguaSeRetiraSoloConContextoActualAcreditado() {
        var n=necesidad(11,B,30,true);when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(n));
        when(seguimiento.marcasActuales(actor)).thenReturn(Set.of(B));
        when(repo.cabecerasRecontacto(eq(1L),anyCollection())).thenReturn(List.of(cabecera(4,11,30,A,Alerta.ACTIVA,true)));
        service.sincronizarRecontacto(actor);verify(repo).retirarRecontactoSustituido(1,4,A);
        verify(repo,never()).buscarAutorizadas(anyLong(),anyCollection(),any());
    }
    @Test void revocacionOEncargoFueraDeLaPoblacionNoFingenResolucion() {
        when(seguimiento.marcasActuales(actor)).thenReturn(Set.of(A));
        assertEquals(0,service.sincronizarRecontacto(actor));
        verify(repo,never()).cabecerasRecontacto(anyLong(),anyCollection());
        verify(repo,never()).retirarRecontactoSustituido(anyLong(),anyLong(),anyString());
    }
    @Test void reasignacionCambiaSoloPresentacionSinRenovarFechasNiCausa() {
        var n=necesidad(11,A,33,true);when(seguimiento.necesidadesActuales(actor)).thenReturn(List.of(n));
        when(seguimiento.marcasActuales(actor)).thenReturn(Set.of(A));
        when(repo.cabecerasRecontacto(eq(1L),anyCollection())).thenReturn(List.of(cabecera(4,11,30,A,Alerta.ACTIVA,true)));
        service.sincronizarRecontacto(actor);verify(repo).actualizarResponsableRecontacto(1,4,A,33);
        verify(repo,never()).retirarRecontactoSustituido(anyLong(),anyLong(),anyString());verify(repo,never()).save(any());
    }
    @Test void unFalloTecnicoNoEsCeroAvisosNiListaVacia() {
        alcance();when(repo.cabecerasActivas(eq(1L),eq(false),anyCollection())).thenReturn(List.of(cabecera(4,11,30,A,Alerta.ACTIVA,true)));
        when(seguimiento.necesidadesVencidas(actor)).thenThrow(new IllegalStateException("fallo R27"));
        assertThrows(IllegalStateException.class,()->service.listar(1,20,actor));
        assertThrows(IllegalStateException.class,()->service.sincronizarRecontacto(actor));
    }
    @Test void tenantAdminNoObtieneOperacionPorGobernar() {
        var admin=new Actor(1,3,40,"TENANT_ADMIN");
        assertThrows(AccesoNoAutorizadoException.class,()->service.listar(1,20,admin));
        assertThrows(AccesoNoAutorizadoException.class,()->service.atender(4,admin));
        assertThrows(AccesoNoAutorizadoException.class,()->service.sincronizarRecontacto(admin));
        verifyNoInteractions(repo,seguimiento,alcances);
    }

    private void alcance(){when(alcances.de(actor)).thenReturn(new Alcances.Alcance(1,false,List.of(30L)));}
    private static Necesidad necesidad(long prospeccion,String marca,long responsable,boolean intento) {
        var r=new RevisionSeguimiento(marca.equals(A)?101:102,2,prospeccion,"Propietario o representante declarado","CADENCIA",
                intento?LocalDate.of(2026,10,28):null,1,LocalDate.of(2026,10,30),"CADENCIA",null,null,false,false,false,null);
        return new Necesidad(r,marca,responsable);
    }
    private static Cabecera cabecera(long id,long entidad,long responsable,String marca,String estado,boolean r27) {
        var causa=r27?null:causa("SOLICITUD_ALQUILER",entidad,responsable);
        return new CabeceraDePrueba(id,r27?Alerta.SIN_RESPUESTA:Alerta.SOLICITUD_EVALUADA,
                r27?"PROSPECCION":"SOLICITUD_ALQUILER",entidad,responsable,marca,estado,
                causa==null?null:huella(causa),causa==null?null:JSON_C7.objetoCanonico(causa),causa==null?null:"VIGENTE");
    }
    private record CabeceraDePrueba(Long id,String tipo,String entidadTipo,Long entidadId,
                                    Long idAgente,String marcaCausa,String estado,String huellaCausaC7,String fuenteCausaC7,String estadoCausaC7) implements Cabecera {
        public Long getId(){return id;} public String getTipo(){return tipo;}
        public String getEntidadTipo(){return entidadTipo;} public Long getEntidadId(){return entidadId;}
        public Long getIdAgente(){return idAgente;} public String getMarcaCausa(){return marcaCausa;}
        public String getEstado(){return estado;}
        public String getHuellaCausaC7(){return huellaCausaC7;}public String getFuenteCausaC7(){return fuenteCausaC7;}
        public String getEstadoCausaC7(){return estadoCausaC7;}
    }
    private record NombreDeclarado(Long id,String nombre) implements AlertaRepository.NombreAgente{
        public Long getId(){return id;}public String getNombre(){return nombre;}
    }
    private static Causa causa(String entidad,long id,long responsable){
        return new Causa(1,new Recurso(entidad,id),Alerta.SOLICITUD_EVALUADA,new HechoAsunto("EVALUACION_SOLICITUD",100000+id,null),
                Origen.de("EVALUACION_SOLICITUD",100000+id),Instant.parse("2026-11-04T14:00:00Z"),null,responsable,null);
    }
    private static String huella(Causa causa){return Idempotencia.huellaExacta(JSON_C7.objetoCanonico(Map.of("organizacion",1L,"recurso",causa.recurso(),"hecho",causa.hecho(),"finalidad",causa.finalidad())));}
    private static Alerta alerta(String entidad,long id) {
        var a=new Alerta();a.setOrganizacionId(1L);a.setEntidadTipo(entidad);a.setEntidadId(id);a.setTipo(Alerta.SOLICITUD_EVALUADA);
        if(!"PROSPECCION".equals(entidad)){var causa=causa(entidad,id,30);a.setHuellaCausaC7(huella(causa));a.setFuenteCausaC7(JSON_C7.objetoCanonico(causa));a.setEstadoCausaC7("VIGENTE");}
        a.setSeveridad(Alerta.MEDIA);a.setMensaje("Mensaje");a.setAgente(detalle());a.nacer();ReflectionTestUtils.setField(a,"id",4L);return a;
    }
    private static DetalleAgente detalle() {
        var persona=new Persona();persona.setNombresORazonSocial("Valentina Mora");var rol=new PersonaRol();rol.setPersona(persona);rol.setTipoRol(TipoRol.AGENTE);
        var d=new DetalleAgente();d.setOrganizacionId(1L);d.setRol(rol);ReflectionTestUtils.setField(d,"id",30L);return d;
    }
}
