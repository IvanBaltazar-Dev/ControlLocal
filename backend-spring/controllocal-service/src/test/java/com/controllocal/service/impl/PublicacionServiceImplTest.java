package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.PublicacionService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.encargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.publicacion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.PublicacionService.*;

/** RF021–027 en unidad. PG/HTTP prueban la autoridad y transacción real, no estos dobles. */
class PublicacionServiceImplTest {
    final PublicacionRepository anuncios=mock(PublicacionRepository.class);
    final HistoriaPublicacionRepository historia=mock(HistoriaPublicacionRepository.class);
    final PrecioPropiedadRepository precios=mock(PrecioPropiedadRepository.class);
    final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    final AtributosGobernados fisico=mock(AtributosGobernados.class);
    final AtributosDeEncargo condiciones=mock(AtributosDeEncargo.class);
    final AccesoEncargo acceso=mock(AccesoEncargo.class);
    final EncargoService ciclo=mock(EncargoService.class);
    final ContextosDeActividad contextos=mock(ContextosDeActividad.class);
    final ComandoIdempotenteRepository recibos=mock(ComandoIdempotenteRepository.class);
    final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
    final EntityManager entidades=mock(EntityManager.class);
    final com.controllocal.service.documentos.SeleccionInmueblePieza seleccionPieza=mock(com.controllocal.service.documentos.SeleccionInmueblePieza.class);
    final HistoriadorPublicacion escritor=new HistoriadorPublicacion(anuncios,historia,precios,eventos,documentos,contextos);
    final PublicacionServiceImpl servicio=new PublicacionServiceImpl(anuncios,historia,fisico,condiciones,acceso,ciclo,
            contextos,new ComandosIdempotentes(recibos),recibos,escritor,documentos,entidades,seleccionPieza);
    final Actor agente=new Actor(1L,20L,30L,Actor.AGENTE);
    final ContextoSesion sesion=new ContextoSesion("TEST",Long.MAX_VALUE,"TEST-SID",1);
    final Captacion encargo=new Captacion();
    final Map<Long,Publicacion> guardados=new LinkedHashMap<>();
    final Map<String,ComandoIdempotente> comandos=new HashMap<>();
    final AtomicLong ids=new AtomicLong(100);
    @BeforeEach void base(){
        Propiedad p=new Propiedad();ReflectionTestUtils.setField(p,"id",10L);p.setOrganizacionId(1L);p.setTipoInmueble("L");
        DetalleAgente responsable=new DetalleAgente();ReflectionTestUtils.setField(responsable,"id",30L);
        ReflectionTestUtils.setField(encargo,"id",40L);encargo.setOrganizacionId(1L);encargo.setPropiedad(p);
        encargo.setAgente(responsable);encargo.setMotivoOperacion("A");encargo.transicionarA(Captacion.APROBADO);
        var pacto=new com.controllocal.domain.comercial.CondicionEconomicaCaptacion();pacto.setTipoOperacion("A");
        pacto.setImporteReferencia(new BigDecimal("2500"));pacto.setMonedaReferencia("PEN");encargo.setCondicionEconomica(pacto);
        when(acceso.leer(40L,agente)).thenReturn(encargo);when(acceso.iniciar(eq(agente),eq(sesion),eq(40L))).thenReturn(new AccesoEncargo.Inicio(30L));
        when(acceso.escribir(eq(40L),eq(agente),any())).thenReturn(encargo);
        doAnswer(i->{Actor a=i.getArgument(0);if(a==null||!a.esAgente())throw new AccesoNoAutorizadoException();return null;}).when(contextos).exigirAgente(any());
        doAnswer(i->{Actor a=i.getArgument(1);Captacion c=i.getArgument(0);if(!a.esAgente()||!Objects.equals(a.idRolOperativo(),c.getAgente().getId()))throw new AccesoNoAutorizadoException();return null;}).when(acceso).exigirResponsable(any(),any());
        when(contextos.ahora()).thenReturn(Instant.parse("2026-10-31T18:00:00Z"));when(contextos.hoy()).thenReturn(LocalDate.of(2026,10,31));
        when(ciclo.consultarVigencia(eq(40L),any(),eq(agente))).thenReturn(new EncargoService.Vigencia(true,false,false,true,List.of(),LocalDate.of(2026,10,31)));
        when(historia.fuentes(1,10,40)).thenReturn("{\"version\":1}");
        when(historia.versionComposicion(anyLong(),isNull())).thenReturn(null);
        when(fisico.rotulosDe(anyLong(),anyString(),anyList())).thenAnswer(i->i.getArgument(2));
        when(condiciones.rotulosDe(anyLong(),any(),anyList())).thenAnswer(i->i.getArgument(2));
        when(anuncios.saveAndFlush(any())).thenAnswer(i->{Publicacion a=i.getArgument(0);if(a.getId()==null)ReflectionTestUtils.setField(a,"id",ids.incrementAndGet());guardados.put(a.getId(),a);return a;});
        when(anuncios.findByOrganizacionIdAndId(eq(1L),anyLong())).thenAnswer(i->Optional.ofNullable(guardados.get(i.getArgument(1))));
        when(anuncios.bloquear(eq(1L),eq(40L),anyLong())).thenAnswer(i->Optional.ofNullable(guardados.get(i.getArgument(2))));
        when(anuncios.deEncargo(1,40)).thenAnswer(i->List.copyOf(guardados.values()));
        when(precios.save(any())).thenAnswer(i->{var precio=(com.controllocal.domain.inmueble.PrecioPropiedad)i.getArgument(0);ReflectionTestUtils.setField(precio,"id",ids.incrementAndGet());return precio;});
        when(recibos.findByOrganizacionIdAndClaveIdempotencia(anyLong(),anyString())).thenAnswer(i->Optional.ofNullable(comandos.get(i.getArgument(0)+":"+i.getArgument(1))));
        when(recibos.save(any())).thenAnswer(i->{ComandoIdempotente c=i.getArgument(0);comandos.put(c.getOrganizacionId()+":"+c.getClaveIdempotencia(),c);return c;});
    }
    DatosContenido datos(String importe,String moneda){return new DatosContenido("WEB_PROPIA",null,importe==null?null:new BigDecimal(importe),moneda,null,null);}
    DatosBorrador nuevo(DatosContenido d){return new DatosBorrador(40,encargo.getVersion(),null,null,d);}
    FichaPublicacion crear(DatosContenido d){return servicio.crearBorradorAnuncio(nuevo(d),agente,sesion,UUID.randomUUID().toString(),Procedencia.deLaPantalla());}
    DatosCambio preparar(String cap,FichaPublicacion f,DatosContenido contenido){return servicio.validarActuacion(cap,new DatosCambio(40,encargo.getVersion(),f.id(),f.version(),contenido,null,null,(PAUSAR.equals(cap)||FINALIZAR.equals(cap))?"Motivo explícito TEST":null),agente);}
    FichaPublicacion ejecutar(String cap,DatosCambio d,String clave){return switch(cap){
        case PUBLICAR->servicio.publicarAnuncio(d,agente,sesion,clave,Procedencia.deLaPantalla());
        case ACTUALIZAR_VISIBLE->servicio.actualizarAnuncioVisible(d,agente,sesion,clave,Procedencia.deLaPantalla());
        case PAUSAR->servicio.pausarAnuncio(d,agente,sesion,clave,Procedencia.deLaPantalla());
        case REANUDAR->servicio.reanudarAnuncio(d,agente,sesion,clave,Procedencia.deLaPantalla());
        case FINALIZAR->servicio.finalizarDifusionAnuncio(d,agente,sesion,clave,Procedencia.deLaPantalla());
        default->throw new IllegalArgumentException();};}
    FichaPublicacion actuar(String cap,FichaPublicacion f){return ejecutar(cap,preparar(cap,f,null),UUID.randomUUID().toString());}
    FichaPublicacion publicada(){return actuar(PUBLICAR,crear(datos("2500","PEN")));}

    @Test void borradorVacioNoExigePubNiFechaNiHito(){var f=crear(null);assertEquals("B",f.estado());assertNull(f.fechaPublicacion());assertNull(f.importePublicado());verifyNoInteractions(precios,ciclo);}
    @Test void editarBorradorIncompletoSiguePrivado(){var b=crear(null);var r=servicio.editarBorradorAnuncio(new DatosBorrador(40,0,b.id(),b.version(),datos(null,null)),agente,sesion,"EDITAR",Procedencia.deLaPantalla());assertEquals("B",r.estado());assertEquals(2,r.version());assertNull(r.fechaPublicacion());verifyNoInteractions(precios,ciclo);}
    @Test void omitirElContenidoDeEdicionNoBorraElBorrador(){var b=crear(datos("2500","PEN"));assertThrows(ReglaNegocioException.class,()->servicio.editarBorradorAnuncio(new DatosBorrador(40,0,b.id(),b.version(),null),agente,sesion,"OMITIDO",Procedencia.deLaPantalla()));assertEquals(new BigDecimal("2500.00"),guardados.get(b.id()).getImportePublicado());}
    @Test void precioNoViajaSinMonedaAlPublicar(){var b=crear(datos("2500",null));assertThrows(ReglaNegocioException.class,()->preparar(PUBLICAR,b,null));verifyNoInteractions(precios);}
    @Test void publicarReutilizaElRotuloTecnicoMostradoEnRevision(){var b=crear(datos("2500","PEN"));var d=preparar(PUBLICAR,b,null);assertEquals("Publicacion 10",d.contenido().tituloAnuncio());assertNotNull(d.huellaContexto());var f=ejecutar(PUBLICAR,d,"P1");assertEquals("P",f.estado());assertEquals("Publicacion 10",f.tituloAnuncio());assertNotNull(f.fechaPublicacion());}
    @Test void publicarDejaHitoEnElEncargoYOperacionCorrectos(){publicada();var valor=org.mockito.ArgumentCaptor.forClass(com.controllocal.domain.inmueble.PrecioPropiedad.class);verify(precios).save(valor.capture());assertEquals(40L,valor.getValue().getIdCaptacion());assertEquals("P",valor.getValue().getHito());assertEquals("PEN",valor.getValue().getMoneda());assertEquals(new BigDecimal("2500.00"),valor.getValue().getMonto());}
    @Test void versionYOutboxEnlazanLaIdentidadDelPrecioRealGuardado(){var f=publicada();var precio=org.mockito.ArgumentCaptor.forClass(com.controllocal.domain.inmueble.PrecioPropiedad.class);verify(precios).save(precio.capture());String ref="\"precioRegistradoEnEstaVersion\":"+precio.getValue().getId();verify(historia).guardar(eq(1L),eq(f.id()),eq(f.version()),eq(30L),eq(PUBLICAR),any(),argThat(json->json.contains(ref)),anyString(),anyString(),isNull());verify(eventos,atLeastOnce()).save(argThat(evento->evento.getCargaUtil().contains(ref)));}
    @Test void fechaDeBorradorLegadoNoSeTomaPorExposicionPrevia(){var b=crear(datos("2500","PEN"));guardados.get(b.id()).setFechaPublicacion(Instant.parse("2026-01-01T12:00:00Z").atOffset(ZoneOffset.UTC));var p=actuar(PUBLICAR,b);assertEquals(LocalDateTime.of(2026,10,31,13,0),p.fechaPublicacion());verify(precios).save(any());}
    @Test void pausaConservaExposicionYNoExigePub(){var p=publicada();clearInvocations(ciclo,precios);doThrow(new ReglaNegocioException("Dato ahora faltante")).when(ciclo).exigirHabilitado(40,agente);var s=actuar(PAUSAR,p);assertEquals("S",s.estado());assertEquals(p.fechaPublicacion(),s.fechaPublicacion());verifyNoInteractions(ciclo,precios);}
    @Test void cierreNoExigePubNiCierraElEncargo(){var p=publicada();clearInvocations(ciclo,precios);var c=actuar(FINALIZAR,p);assertEquals("C",c.estado());assertNotNull(c.fechaBaja());assertEquals(Captacion.APROBADO,encargo.estadoActual());verifyNoInteractions(ciclo,precios);}
    @Test void reanudarEsElMismoAnuncioYSinOtroPrecio(){var p=publicada();var s=actuar(PAUSAR,p);clearInvocations(precios);var r=actuar(REANUDAR,s);assertEquals(p.id(),r.id());assertEquals("P",r.estado());assertEquals(p.fechaPublicacion(),r.fechaPublicacion());verifyNoInteractions(precios);}
    @Test void reanudarRevalidaPubActual(){var s=actuar(PAUSAR,publicada());when(ciclo.consultarVigencia(eq(40L),any(),eq(agente))).thenReturn(new EncargoService.Vigencia(false,true,false,false,List.of("Encargo vencido"),LocalDate.of(2026,10,31)));assertThrows(ReglaNegocioException.class,()->preparar(REANUDAR,s,null));assertEquals("S",guardados.get(s.id()).getEstado());}
    @Test void cerrarUnBorradorNoInventaUnaExposicion(){var c=actuar(FINALIZAR,crear(null));assertEquals("C",c.estado());assertNull(c.fechaPublicacion());verifyNoInteractions(precios);}
    @Test void hitoContractualCierraConHistoriaSinExigirEdicionFisica(){var p=publicada();clearInvocations(historia,precios,ciclo);var broker=new Actor(1L,50L,60L,Actor.BROKER);escritor.cerrarPorHito(encargo,broker);assertEquals("C",guardados.get(p.id()).getEstado());verify(historia).guardar(eq(1L),eq(p.id()),eq(p.version()+1),eq(60L),eq("CIERRE_POR_HITO_CONFIRMADO"),any(),anyString(),anyString(),isNull(),eq("Consecuencia del hito contractual confirmado."));verifyNoInteractions(precios,ciclo);}
    @Test void hitoContractualNoReescribeUnaDifusionYaCerrada(){var p=actuar(FINALIZAR,publicada());clearInvocations(historia,precios,eventos,anuncios);escritor.cerrarPorHito(encargo,agente);assertEquals(p.version(),guardados.get(p.id()).getVersionAnuncio().longValue());verify(anuncios,never()).saveAndFlush(any());verifyNoInteractions(historia,precios,eventos);}
    @Test void noSeReabreUnaDifusionCerrada(){var c=actuar(FINALIZAR,publicada());assertThrows(ConflictoException.class,()->preparar(REANUDAR,c,null));assertThrows(ConflictoException.class,()->preparar(PUBLICAR,c,null));}
    @Test void editarVisibleConPrecioIgualNoDuplicaHito(){var p=publicada();clearInvocations(precios);var d=new DatosContenido("WEB_PROPIA",null,new BigDecimal("2500"),"PEN","Título corregido",null);var r=ejecutar(ACTUALIZAR_VISIBLE,preparar(ACTUALIZAR_VISIBLE,p,d),"TITULO");assertEquals("Título corregido",r.tituloAnuncio());verifyNoInteractions(precios);}
    @Test void nuevoPrecioDejaOtroHitoSinCambiarPacto(){var p=publicada();clearInvocations(precios);var r=ejecutar(ACTUALIZAR_VISIBLE,preparar(ACTUALIZAR_VISIBLE,p,datos("2600","PEN")),"PRECIO");assertEquals(new BigDecimal("2600.00"),r.importePublicado());verify(precios).save(any());assertEquals(new BigDecimal("2500"),encargo.getCondicionEconomica().getImporteReferencia());assertEquals("PEN",encargo.getCondicionEconomica().getMonedaReferencia());}
    @Test void precioDeComposicionNoSeImputaAlFisicoPrimario(){ReflectionTestUtils.setField(encargo,"idComposicionActual",900L);var comp=new EncargoService.Composicion(900,40,3,List.of(new EncargoService.Unidad(10,null,null,"Unidad TEST"),new EncargoService.Unidad(11,null,null,"Unidad TEST")),new BigDecimal("2500"),"PEN","Conjunto documentado TEST");when(ciclo.consultarComposicion(40,null,agente)).thenReturn(comp);when(historia.versionComposicion(1L,900L)).thenReturn(3L);var f=publicada();assertEquals(900L,f.composicion());assertEquals(3L,f.versionComposicion());verifyNoInteractions(precios);var e=servicio.evaluarPublicabilidad(40,"WEB_PROPIA",agente);assertEquals(3L,e.versionesOrigen().get("composicion"));verify(historia,atLeastOnce()).guardar(eq(1L),anyLong(),anyLong(),eq(30L),anyString(),any(),argThat(json->json.contains("\"composicion\":900")&&json.contains("\"versionComposicion\":3")),anyString(),any(),any());}
    @Test void cambiarSoloMonedaEsOtroHechoEconomico(){var p=publicada();clearInvocations(precios);ejecutar(ACTUALIZAR_VISIBLE,preparar(ACTUALIZAR_VISIBLE,p,datos("2500","USD")),"MONEDA");verify(precios).save(any());}
    @Test void unReintentoNoDuplicaAnuncioVersionNiHito(){var b=crear(datos("2500","PEN"));var d=preparar(PUBLICAR,b,null);var uno=ejecutar(PUBLICAR,d,"MISMA");clearInvocations(anuncios,precios,historia,eventos);var dos=ejecutar(PUBLICAR,d,"MISMA");assertEquals(uno,dos);verifyNoInteractions(precios,eventos);verify(anuncios,never()).saveAndFlush(any());verify(historia,never()).guardar(anyLong(),anyLong(),anyLong(),anyLong(),anyString(),any(),anyString(),anyString(),any(),any());}
    @Test void repetirBorradorDevuelveElMismoResultado(){var d=nuevo(null);var b=servicio.crearBorradorAnuncio(d,agente,sesion,"BORRADOR",Procedencia.deLaPantalla());var otra=servicio.crearBorradorAnuncio(d,agente,sesion,"BORRADOR",Procedencia.deLaPantalla());assertEquals(b,otra);assertEquals(1,guardados.size());}
    @Test void unaClaveNoPuedeCambiarElContenido(){servicio.crearBorradorAnuncio(nuevo(null),agente,sesion,"COLISION",Procedencia.deLaPantalla());assertThrows(ReglaNegocioException.class,()->servicio.crearBorradorAnuncio(nuevo(datos("2500","PEN")),agente,sesion,"COLISION",Procedencia.deLaPantalla()));}
    @Test void unaVersionViejaNoSobrescribeElAnuncio(){var p=publicada();var r=actuar(PAUSAR,p);assertThrows(ConflictoException.class,()->preparar(PAUSAR,p,null));assertEquals("S",r.estado());}
    @Test void cambiarLaFuenteDespuesDePrepararImpidePublicar(){var b=crear(datos("2500","PEN"));var d=preparar(PUBLICAR,b,null);when(historia.fuentes(1,10,40)).thenReturn("{\"version\":2}");assertThrows(ConflictoException.class,()->ejecutar(PUBLICAR,d,"FUENTE"));assertEquals("B",guardados.get(b.id()).getEstado());}
    @Test void encargoDeOtroResponsableNoSeOpera(){var b=crear(datos("2500","PEN"));var otro=new DetalleAgente();ReflectionTestUtils.setField(otro,"id",99L);encargo.setAgente(otro);assertThrows(AccesoNoAutorizadoException.class,()->preparar(PUBLICAR,b,null));}
    @Test void replayReautorizaAlResponsableActual(){var b=crear(datos("2500","PEN"));var d=preparar(PUBLICAR,b,null);ejecutar(PUBLICAR,d,"REPLAY");var otro=new DetalleAgente();ReflectionTestUtils.setField(otro,"id",99L);encargo.setAgente(otro);assertThrows(AccesoNoAutorizadoException.class,()->ejecutar(PUBLICAR,d,"REPLAY"));}
    @Test void noSeConfundeElAnuncioDeOtroEncargo(){var b=crear(null);guardados.get(b.id()).setIdEncargo(99L);assertThrows(NoEncontradoException.class,()->servicio.obtener(40,b.id(),agente));}
    @Test void estadoExternoNoSeDeduceDelLocal(){var p=publicada();assertNull(p.estadoExterno());assertNull(actuar(PAUSAR,p).estadoExterno());}
    @Test void estadoExternoRequiereFechaYFuente(){var b=crear(datos("2500","PEN"));var d=new DatosCambio(40,0,b.id(),b.version(),null,new EstadoExterno("VISIBLE",null,"Declaración TEST"),null,null);assertThrows(ReglaNegocioException.class,()->servicio.validarActuacion(PUBLICAR,d,agente));}
    @Test void hechoExternoConservaFechaPropia(){var b=crear(datos("2500","PEN"));var fecha=Instant.parse("2026-10-30T18:00:00Z");var d=servicio.validarActuacion(PUBLICAR,new DatosCambio(40,0,b.id(),b.version(),null,new EstadoExterno("VISIBLE",fecha,"Constancia TEST"),null,null),agente);var p=ejecutar(PUBLICAR,d,"EXTERNO");assertEquals(fecha,p.estadoExterno().fechaEfectiva());assertEquals("Constancia TEST",p.estadoExterno().procedencia());}
    @Test void evaluacionExplicaSinConcederPublicacion(){when(fisico.faltantesDePropiedadParaPublicar(eq(1L),any())).thenReturn(List.of("superficie"));var e=servicio.evaluarPublicabilidad(40,"WEB_PROPIA",agente);assertFalse(e.cumple());assertEquals(List.of("superficie"),e.faltantesFicha());assertNotNull(e.huellaContexto());assertTrue(guardados.isEmpty());}
    @Test void piezaEvaluaLaFichaElegidaYNoLaPrimaria(){
        var otra=new Propiedad();ReflectionTestUtils.setField(otra,"id",11L);otra.setOrganizacionId(1L);otra.setTipoInmueble("T");
        var comp=new EncargoService.Composicion(900,40,1,List.of(new EncargoService.Unidad(11,null,null,"Origen TEST")),new BigDecimal("2500"),"PEN","Conjunto TEST");
        ReflectionTestUtils.setField(encargo,"idComposicionActual",900L);
        when(seleccionPieza.elegir(encargo,11L,agente)).thenReturn(new com.controllocal.service.documentos.SeleccionInmueblePieza.Seleccion(otra,comp,true));
        when(fisico.faltantesDePropiedadParaPublicar(1,otra)).thenReturn(List.of("zonificación"));
        assertThrows(ReglaNegocioException.class,()->servicio.exigirPublicabilidadDePieza(40,11,"WEB_PROPIA",agente));
        verify(fisico).faltantesDePropiedadParaPublicar(1,otra);verify(fisico,never()).faltantesDePropiedadParaPublicar(1,encargo.getPropiedad());
        verifyNoInteractions(precios);
    }
    @Test void piezaConservaVersionDelMiembroYDelConjuntoSinImputarPrecio(){
        var otra=new Propiedad();ReflectionTestUtils.setField(otra,"id",11L);otra.setOrganizacionId(1L);otra.setTipoInmueble("L");
        var comp=new EncargoService.Composicion(900,40,1,List.of(new EncargoService.Unidad(11,null,null,"Origen TEST")),new BigDecimal("2500"),"PEN","Conjunto TEST");
        ReflectionTestUtils.setField(encargo,"idComposicionActual",900L);
        when(seleccionPieza.elegir(encargo,11L,agente)).thenReturn(new com.controllocal.service.documentos.SeleccionInmueblePieza.Seleccion(otra,comp,true));
        var e=servicio.exigirPublicabilidadDePieza(40,11,"WEB_PROPIA",agente);assertEquals(11,e.propiedad());assertEquals(900L,e.composicion());assertEquals(1L,e.versionComposicion());
        verify(historia).fuentes(1,11,40);verifyNoInteractions(precios);
    }
    @Test void piezaNoEvitaLaAutorizacionDelSujeto(){when(seleccionPieza.elegir(encargo,99L,agente)).thenThrow(new AccesoNoAutorizadoException());assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirPublicabilidadDePieza(40,99,"WEB_PROPIA",agente));verifyNoInteractions(precios);}
    @Test void vigenciaInsuficienteDevuelveMotivoSinExcepcionDeGuarda(){when(ciclo.consultarVigencia(eq(40L),any(),eq(agente))).thenReturn(new EncargoService.Vigencia(true,false,false,false,List.of("Aprobación pendiente"),LocalDate.of(2026,10,31)));var e=servicio.evaluarPublicabilidad(40,"WEB_PROPIA",agente);assertFalse(e.cumple());assertTrue(e.motivos().stream().anyMatch(m->m.contains("Aprobación pendiente")));verify(ciclo,never()).exigirHabilitado(anyLong(),any());}
    @Test void urlNoAceptaJavascriptNiCredenciales(){for(String url:List.of("javascript:alert(1)","https://user:pass@example.invalid/a"))assertThrows(ReglaNegocioException.class,()->crear(new DatosContenido("WEB_PROPIA",url,null,null,null,null)));}
    @Test void noSeRedondeaUnImporteSilenciosamente(){assertThrows(ReglaNegocioException.class,()->crear(datos("2500.001","PEN")));}
    @Test void codigoMonedaSeConservaSinSuponerlo(){var b=crear(datos(null,null));assertNull(b.moneda());assertThrows(ReglaNegocioException.class,()->crear(datos("2500","ZZZ")));}
    @Test void faltaClaveNoTieneEfectos(){assertThrows(ReglaNegocioException.class,()->servicio.crearBorradorAnuncio(nuevo(null),agente,sesion,null,Procedencia.deLaPantalla()));assertTrue(guardados.isEmpty());}
    @Test void versionEncargoAusenteONulaNoSeConvierteEnCero(){for(String json:List.of("{\"encargo\":40}","{\"encargo\":40,\"versionEncargo\":null}"))assertThrows(IllegalStateException.class,()->documentos.comoObjeto(json,DatosBorrador.class));var dto=documentos.comoObjeto("{\"encargo\":40,\"versionEncargo\":0}",DatosBorrador.class);assertEquals(0,dto.versionEncargo());}
    @Test void confirmacionExigeIdentidadYVersionesExplicitas(){for(String json:List.of("{\"encargo\":40,\"versionEncargo\":0,\"publicacion\":101}","{\"encargo\":40,\"versionEncargo\":null,\"publicacion\":101,\"versionPublicacion\":1}"))assertThrows(IllegalStateException.class,()->documentos.comoObjeto(json,DatosCambio.class));var dto=documentos.comoObjeto("{\"encargo\":40,\"versionEncargo\":0,\"publicacion\":101,\"versionPublicacion\":1}",DatosCambio.class);assertEquals(0,dto.versionEncargo());}
    @Test void noHayUnSelectorGenericoQueDevuelvaUnaPublicacionABorrador(){var nombres=Arrays.stream(PublicacionService.class.getMethods()).map(java.lang.reflect.Method::getName).toList();assertFalse(nombres.contains("cambiarEstado"));assertFalse(nombres.contains("crearEnEncargo"));assertFalse(nombres.contains("actualizar"));}
}

