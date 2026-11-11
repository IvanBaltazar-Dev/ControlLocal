package com.controllocal.service.encargo;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.comercial.*;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.domain.inmueble.*;
import com.controllocal.domain.organizacion.Organizacion;
import com.controllocal.domain.persona.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.controllocal.service.encargo.EncargoService.*;
import static com.controllocal.service.captura.SuficienciaInmueble.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reglas del ciclo y snapshots. La durabilidad/CAS/rollback se demuestran aparte contra PostgreSQL. */
class EncargoServiceImplTest {
    private static final LocalDate HOY=LocalDate.of(2026,10,28);
    private final Actor agente=new Actor(1,10,30,Actor.AGENTE),broker=new Actor(1,11,23,Actor.BROKER),admin=new Actor(1,12,40,Actor.TENANT_ADMIN);
    private final CaptacionRepository caps=mock(CaptacionRepository.class);
    private final PropiedadRepository propiedades=mock(PropiedadRepository.class);
    private final DetalleAgenteRepository agentes=mock(DetalleAgenteRepository.class);
    private final DocumentoEncargoRepository docs=mock(DocumentoEncargoRepository.class);
    private final ArchivoEncargoRepository archivos=mock(ArchivoEncargoRepository.class);
    private final CargaArchivoContextualRepository cargas=mock(CargaArchivoContextualRepository.class);
    private final com.controllocal.service.archivo.AccesoArchivoService a04=mock(com.controllocal.service.archivo.AccesoArchivoService.class);
    private final PeticionRevisionEncargoRepository pets=mock(PeticionRevisionEncargoRepository.class);
    private final SolicitudFinalizacionEncargoRepository fins=mock(SolicitudFinalizacionEncargoRepository.class);
    private final RevisionEncargoRepository revisiones=mock(RevisionEncargoRepository.class);
    private final ComposicionEncargoRepository composiciones=mock(ComposicionEncargoRepository.class);
    private final EventoDominioRepository eventos=mock(EventoDominioRepository.class);
    private final PrecioPropiedadRepository precios=mock(PrecioPropiedadRepository.class);
    private final ComandoIdempotenteRepository registros=mock(ComandoIdempotenteRepository.class);
    private final TitularidadPropiedadRepository titulos=mock(TitularidadPropiedadRepository.class);
    private final LectorPorAutoridad lector=mock(LectorPorAutoridad.class);
    private final AtributosDeEncargo atributos=mock(AtributosDeEncargo.class);
    private final AtributosGobernados atributosFisicos=mock(AtributosGobernados.class);
    private final SuficienciaRegistrada fisica=mock(SuficienciaRegistrada.class);
    private final AutoridadDePropiedad autoridadFisica=mock(AutoridadDePropiedad.class);
    private final Alcances alcances=mock(Alcances.class);
    private final OrganizacionRepository organizaciones=mock(OrganizacionRepository.class);
    private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final DocumentosEncargo binarios=mock(DocumentosEncargo.class);
    private final EntityManager em=mock(EntityManager.class);
    private final ApplicationEventPublisher publicadorCambios=mock(ApplicationEventPublisher.class);
    private final ObjectMapper mapper=JsonMapper.builder().findAndAddModules().build();
    private final Documentos json=new Documentos(mapper);
    private final Map<Long,DocumentoEncargo> documentos=new HashMap<>();
    private final Map<UUID,ArchivoEncargo> ficheros=new HashMap<>();
    private final Map<UUID,CargaArchivoContextual> originales=new HashMap<>();
    private final Map<String,byte[]> blobs=new HashMap<>();
    private final Map<String,DocumentosEncargo.Guardado> metadatosBlob=new HashMap<>();
    private final Map<Long,PeticionRevisionEncargo> peticiones=new HashMap<>();
    private final Map<Long,SolicitudFinalizacionEncargo> solicitudes=new HashMap<>();
    private final Map<Long,RevisionEncargo> historia=new HashMap<>();
    private final Map<Long,ComposicionEncargo> composicionesGuardadas=new HashMap<>();
    private final Map<String,ComandoIdempotente> comandos=new HashMap<>();
    private final List<PrecioPropiedad> serie=new ArrayList<>();
    private final Map<String,Dato> datos=new TreeMap<>();
    private final AtomicLong ids=new AtomicLong(100);
    private final Transiciones estados=new Transiciones(mock(HistorialEstadoRepository.class));
    private final com.controllocal.service.actividad.SeguimientoDeProspeccion seguimientos=mock(com.controllocal.service.actividad.SeguimientoDeProspeccion.class);
    private final AccesoEncargo acceso=new AccesoEncargo(caps,alcances,organizaciones,miembros,cuentas,contextos);
    private final ConstanciasDeFirmaDeEncargo constancias=new ConstanciasDeFirmaDeEncargo(archivos,revisiones,docs,a04,acceso,json,mapper);
    private final PactosDeEncargo pactos=new PactosDeEncargoImpl(acceso,revisiones,docs,constancias,json,mapper);
    private final EncargoServiceImpl servicio=new EncargoServiceImpl(caps,propiedades,agentes,docs,archivos,cargas,a04,pets,fins,revisiones,
            composiciones,eventos,precios,registros,new ComandosIdempotentes(registros),acceso,new ReglasDeResponsableEncargo(docs),
            autoridadFisica,new TitularParaEncargar(titulos),fisica,new SuficienciaInmueble(),new CondicionesDeEncargo(mapper),
            atributosFisicos,new CondicionesDinamicasEncargo(atributos,lector),estados,json,mapper,binarios,em,
            Clock.fixed(Instant.parse("2026-10-28T17:00:00Z"),ZoneId.of("America/Lima")),publicadorCambios,seguimientos,
            constancias,pactos);
    private Propiedad p;private Captacion c;private Contexto contexto;

    @BeforeEach void montaje() {
        var persona=new Persona();persona.setOrganizacionId(1L);persona.setNombresORazonSocial("Ana Agente");id(persona,10);
        var rol=new PersonaRol();rol.setOrganizacionId(1L);rol.setPersona(persona);id(rol,30);
        var a=new DetalleAgente();a.setOrganizacionId(1L);a.setRol(rol);a.setCodigoAgente("AGT-30");id(a,30);
        p=new Propiedad();p.setOrganizacionId(1L);p.setCodigo("D-C2");p.setTipoInmueble("D");p.setUso("RESIDENCIAL");
        p.setDireccion("Av. Perú 123");p.setDistrito("Surco");p.setInteriorUnidad("302");id(p,7);
        c=new Captacion();c.setOrganizacionId(1L);c.setCodigoCaptacion("ENC-C2");c.setAgente(a);c.setPropiedad(p);c.setMotivoOperacion("A");
        c.setFechaCaptacion(HOY);c.setFechaInicioVigencia(HOY);c.setFechaFinVigencia(HOY.plusMonths(6));c.setExclusividad(false);id(c,9);
        c.setCondicionEconomica(new CondicionesDeEncargo(mapper).nueva(1,OperacionInmobiliaria.ALQUILER,pacto()));estados.iniciar(c,Captacion.EN_PREPARACION);
        contexto=new Contexto("D","RESIDENCIAL","Departamento302","ALQUILER",Map.of(UNIDAD_DE_EDIFICIO,Aplicabilidad.APLICA,MANTENIMIENTO,Aplicabilidad.NO_APLICA),Set.of());
        datos.put("direccion",Dato.conocido(p.getDireccion()));datos.put("distrito",Dato.conocido(p.getDistrito()));datos.put("interiorUnidad",Dato.conocido("302"));
        datos.put("metraje_total",new Dato(new BigDecimal("90"),EstadoDato.CONOCIDO,"Plano aportado",AREA_TOTAL,null));
        for(var e:Map.of("dormitorios",2,"banos",2,"piso",3,"ascensores",1).entrySet())datos.put(e.getKey(),Dato.conocido(e.getValue()));
        datos.put("contexto."+MANTENIMIENTO,new Dato(null,EstadoDato.NO_APLICABLE,"Declaración de las partes",null,null));
        when(fisica.contexto(eq(p),any(),isNull())).thenReturn(contexto);when(fisica.datos(p)).thenAnswer(i->new TreeMap<>(datos));
        when(fisica.validarDatos(anyLong(),any(),anyMap())).thenAnswer(i->i.getArgument(2));
        when(alcances.alcanza(any(),anyLong())).thenAnswer(i->{Actor x=i.getArgument(0);return x.esBroker()||x.esAgente()&&x.idRolOperativo()==c.getAgente().getId();});
        when(alcances.de(any())).thenAnswer(i->{Actor x=i.getArgument(0);return new Alcances.Alcance(x.idOrganizacion(),false,
                x.esBroker()?List.of(30L):x.esAgente()?List.of(x.idRolOperativo()):List.of());});
        when(miembros.cuentasActivasDePersona(anyLong(),anyLong())).thenAnswer(i->List.of(100L+i.getArgument(1,Long.class)));
        when(cuentas.bloquearCambioAcceso(anyLong())).thenReturn(Optional.of(new CuentaAcceso()));
        when(contextos.exigirContexto(anyString(),anyLong(),any())).thenAnswer(i->sesion(i.getArgument(2)));
        when(caps.buscarFicha(1,9)).thenReturn(Optional.of(c));when(caps.bloquearParaEscritura(1,9)).thenReturn(Optional.of(c));
        var actual=mock(CaptacionRepository.ContextoActualEncargo.class);when(actual.getResponsable()).thenReturn(30L);when(actual.getCuenta()).thenReturn(110L);
        when(actual.getPropiedad()).thenReturn(7L);when(actual.getVersion()).thenAnswer(i->c.getVersion());when(caps.contextoActual(1,9)).thenReturn(Optional.of(actual));
        when(caps.bloquearInmuebleParaRevision(1,7)).thenReturn(Optional.of(7L));when(caps.bloquearResponsableParaRevision(1,30)).thenReturn(Optional.of(30L));
        when(propiedades.findById(7L)).thenReturn(Optional.of(p));when(propiedades.bloquearParaEscritura(1,7)).thenReturn(Optional.of(p));
        when(autoridadFisica.buscarParaLectura(agente,7)).thenReturn(Optional.of(p));when(agentes.findById(30L)).thenReturn(Optional.of(a));
        var org=new Organizacion();org.setNombre("Agencia de prueba");org.setEstado("A");org.setModalidad("EMPRESARIAL");when(organizaciones.findById(1L)).thenReturn(Optional.of(org));
        when(lector.deEncargo(anyLong())).thenReturn(ValoresGobernados.vacio());when(atributosFisicos.definicionesParaLeer(anyLong(),anyString(),anyCollection())).thenReturn(Map.of());
        var t=mock(TitularidadPropiedadRepository.ParteActual.class);when(t.getId()).thenReturn(80L);when(t.getPersona()).thenReturn(90L);when(t.getNombre()).thenReturn("María Propietaria");when(t.getCuota()).thenReturn(new BigDecimal("100"));
        when(titulos.partesActuales(1,7,HOY)).thenReturn(List.of(t));when(titulos.bloquearIdentidades(eq(1L),anyCollection())).thenAnswer(i->new ArrayList<>(i.getArgument(1,Collection.class)));
        when(docs.save(any())).thenAnswer(i->{var x=i.getArgument(0,DocumentoEncargo.class);id(x,ids.incrementAndGet());documentos.put(x.getId(),x);return x;});
        when(docs.saveAndFlush(any())).thenAnswer(i->docs.save(i.getArgument(0,DocumentoEncargo.class)));
        when(docs.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(documentos.get(i.getArgument(1))));
        when(docs.bloquear(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(documentos.get(i.getArgument(1))));
        when(docs.contextoActual(anyLong(),anyLong())).thenAnswer(i->{var x=documentos.get(i.getArgument(1));if(x==null)return Optional.empty();var v=mock(DocumentoEncargoRepository.ContextoActual.class);when(v.getEncargo()).thenReturn(x.getIdEncargo());when(v.getAutor()).thenReturn(x.getIdAutor());when(v.getVersion()).thenReturn(x.getVersion());return Optional.of(v);});
        when(archivos.save(any())).thenAnswer(i->{var x=i.getArgument(0,ArchivoEncargo.class);id(x,ids.incrementAndGet());ficheros.put(x.getArchivoRef(),x);return x;});
        when(archivos.findAllByOrganizacionIdAndArchivoRefIn(anyLong(),anyCollection())).thenAnswer(i->evidencias(i.getArgument(0),i.getArgument(1)));
        when(archivos.bloquear(anyLong(),anyCollection())).thenAnswer(i->evidencias(i.getArgument(0),i.getArgument(1)));
        when(archivos.findByOrganizacionIdAndArchivoRef(anyLong(),any())).thenAnswer(i->Optional.ofNullable(ficheros.get(i.getArgument(1))));
        when(archivos.actual(anyLong(),any())).thenAnswer(i->{
            var x=ficheros.get(i.getArgument(1));if(x==null || !Objects.equals(x.getOrganizacionId(),i.getArgument(0,Long.class)))return Optional.empty();
            var v=mock(ArchivoEncargoRepository.ReferenciaActual.class);
            when(v.getId()).thenReturn(x.getId());when(v.getArchivoRef()).thenReturn(x.getArchivoRef());
            when(v.getEncargo()).thenReturn(x.getIdEncargo());when(v.getDocumento()).thenReturn(x.getIdDocumento());
            when(v.getAutor()).thenReturn(x.getIdAutor());when(v.getFinalidad()).thenReturn(x.getFinalidad());
            when(v.getHuella()).thenReturn(x.getHuella());when(v.getVersion()).thenReturn(x.getVersion());return Optional.of(v);
        });
        when(cargas.save(any())).thenAnswer(i->{var x=i.getArgument(0,CargaArchivoContextual.class);id(x,ids.incrementAndGet());originales.put(x.getReferencia(),x);return x;});
        when(cargas.findByOrganizacionIdAndReferencia(anyLong(),any())).thenAnswer(i->Optional.ofNullable(originales.get(i.getArgument(1))));
        when(a04.resolver(anyString(),any())).thenAnswer(i->{
            UUID ref=UUID.fromString(i.getArgument(0));Actor actor=i.getArgument(1);var carga=originales.get(ref);
            if(carga!=null){
                if(actor.idOrganizacion()!=carga.getOrganizacionId())throw new NoEncontradoException("Original");
                if(actor.idRolOperativo()!=carga.getAutor())throw new AccesoNoAutorizadoException();
                long recurso=carga.getEncargo()!=null?carga.getEncargo():carga.getDocumento()!=null?carga.getDocumento():carga.getId();
                return new com.controllocal.service.archivo.AccesoArchivoService.ReferenciaAutorizada(ref,"CARGA_TEMPORAL",carga.getId(),recurso,carga.getFinalidad(),carga.getClave(),Long.toString(carga.getVersion()),carga.getHuella());
            }
            var archivo=ficheros.get(ref);if(archivo==null)throw new NoEncontradoException("Original");
            acceso.exigirLectura(archivo.getIdEncargo(),actor);
            return new com.controllocal.service.archivo.AccesoArchivoService.ReferenciaAutorizada(ref,"ARCHIVO_ENCARGO",archivo.getId(),archivo.getIdEncargo(),archivo.getFinalidad(),archivo.getClaveProveedor(),Long.toString(archivo.getVersion()),archivo.getHuella());
        });
        doCallRealMethod().when(a04).verificarContenido(any(),any());
        when(pets.saveAndFlush(any())).thenAnswer(i->{var x=i.getArgument(0,PeticionRevisionEncargo.class);if(x.getId()==null)id(x,ids.incrementAndGet());peticiones.put(x.getId(),x);return x;});
        when(pets.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(peticiones.get(i.getArgument(1))));
        when(pets.bloquear(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(peticiones.get(i.getArgument(1))));
        when(pets.encargoDe(anyLong(),anyLong())).thenAnswer(i->peticiones.containsKey(i.getArgument(1))?Optional.of(9L):Optional.empty());
        when(fins.saveAndFlush(any())).thenAnswer(i->{var x=i.getArgument(0,SolicitudFinalizacionEncargo.class);if(x.getId()==null)id(x,ids.incrementAndGet());solicitudes.put(x.getId(),x);return x;});
        when(fins.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(solicitudes.get(i.getArgument(1))));
        when(fins.bloquear(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(solicitudes.get(i.getArgument(1))));
        when(fins.encargoDe(anyLong(),anyLong())).thenAnswer(i->solicitudes.containsKey(i.getArgument(1))?Optional.of(9L):Optional.empty());
        when(revisiones.save(any())).thenAnswer(i->{var x=i.getArgument(0,RevisionEncargo.class);id(x,ids.incrementAndGet());historia.put(x.getId(),x);return x;});
        when(revisiones.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(historia.get(i.getArgument(1))));
        when(revisiones.ultimaDeAdenda(anyLong(),anyLong(),anyLong())).thenAnswer(i->historia.values().stream()
                .filter(r->r.getOrganizacionId()==i.getArgument(0,Long.class)&&Objects.equals(r.getIdEncargo(),i.getArgument(1,Long.class)))
                .filter(r->{if(Objects.equals(r.getId(),i.getArgument(2,Long.class)))return true;
                    Object valor=json.comoMapa(r.getSnapshot()).get("adenda");
                    return valor instanceof Map<?,?> m&&m.get("propuesta") instanceof Number n&&n.longValue()==i.getArgument(2,Long.class);})
                .max(Comparator.comparing(RevisionEncargo::getId)));
        when(revisiones.findAllByOrganizacionIdAndIdEncargoOrderByIdAsc(anyLong(),anyLong())).thenAnswer(i->historia.values().stream()
                .filter(r->r.getOrganizacionId()==i.getArgument(0,Long.class)&&Objects.equals(r.getIdEncargo(),i.getArgument(1,Long.class)))
                .sorted(Comparator.comparing(RevisionEncargo::getId)).toList());
        when(composiciones.save(any())).thenAnswer(i->{var x=i.getArgument(0,ComposicionEncargo.class);id(x,ids.incrementAndGet());composicionesGuardadas.put(x.getId(),x);return x;});
        when(composiciones.findByOrganizacionIdAndId(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(composicionesGuardadas.get(i.getArgument(1))));
        when(composiciones.findByOrganizacionIdAndIdEncargoAndVersionContenido(anyLong(),anyLong(),anyLong())).thenAnswer(i->composicionesGuardadas.values().stream()
                .filter(x->x.getOrganizacionId()==i.getArgument(0,Long.class)&&x.getIdEncargo().equals(i.getArgument(1,Long.class))&&x.getVersionContenido()==i.getArgument(2,Long.class)).findFirst());
        when(registros.findByOrganizacionIdAndClaveIdempotencia(anyLong(),anyString())).thenAnswer(i->Optional.ofNullable(comandos.get(i.getArgument(1))));
        when(registros.save(any())).thenAnswer(i->{var x=i.getArgument(0,ComandoIdempotente.class);comandos.put(x.getClaveIdempotencia(),x);return x;});
        when(precios.save(any())).thenAnswer(i->{var x=i.getArgument(0,PrecioPropiedad.class);id(x,ids.incrementAndGet());serie.add(x);return x;});
        when(precios.findFirstByIdCaptacionAndHitoOrderByFechaDescIdDesc(anyLong(),anyString())).thenAnswer(i->serie.isEmpty()?Optional.empty():Optional.of(serie.getLast()));
        when(binarios.generar(any(DocumentosEncargo.Origen.class),any())).thenReturn(new DocumentosEncargo.Guardado("privado/encargo.pdf","encargo.pdf","application/pdf","a".repeat(64)));
        when(binarios.guardar(any(DocumentosEncargo.Origen.class),anyString(),anyString(),any())).thenAnswer(i->{
            String clave="privado/"+UUID.randomUUID();byte[] contenido=i.getArgument(3);blobs.put(clave,contenido.clone());
            var b=new DocumentosEncargo.Guardado(clave,i.getArgument(1),i.getArgument(2),digest(contenido));metadatosBlob.put(clave,b);return b;
        });
        when(binarios.abrir(anyString())).thenAnswer(i->{String clave=i.getArgument(0);var meta=metadatosBlob.get(clave);return meta==null?Optional.empty():Optional.of(new DocumentosEncargo.Binario(blobs.get(clave),meta.nombre(),meta.contentType()));});
        when(caps.saveAndFlush(any())).thenAnswer(i->{var x=i.getArgument(0,Captacion.class);if(x.getId()==null)id(x,ids.incrementAndGet());return x;});
    }

    @Test void abrirPreparacionNoExigeTitularNiInventaPactoVigenciaODisponibilidad() {
        var r=servicio.abrir(new DatosApertura(7L,30L,"ENC-PARCIAL",OperacionInmobiliaria.ALQUILER,null,null,null,null,null,origen(),List.of()),agente);
        assertEquals("EN_PREPARACION",r.estado());assertNull(p.getDisponibilidadComercial());verifyNoInteractions(titulos,precios);assertTrue(serie.isEmpty());
    }
    @Test void editarUnaCondicionDinamicaIncrementaLaMismaVersionMaterialYConservaSuProcedencia(){
        var d=new CatalogoAtributo();d.setClave("garantia_meses");d.setRotulo("Meses de garantía");d.setTipoDato("DECIMAL");d.setUnidad("meses");d.setSujeto(Sujeto.ENCARGO);d.setActivo(true);
        when(atributos.definicionesDe(eq(1L),any(Comercializacion.class))).thenReturn(Map.of(d.getClave(),d));
        var nuevos=ValoresGobernados.constructor().con(d.getClave(),ValorLogico.deNumero(new BigDecimal("3"))).construir();
        when(lector.deEncargo(9)).thenReturn(ValoresGobernados.vacio(),nuevos);
        var entrada=new PropiedadUniversalService.ValorAtributo(d.getClave(),"3",null,null,"DECLARADO",null,HOY.minusDays(1),"Pacto TEST",null);
        servicio.editar(9,new Edicion(0,null,null,null,null,null,"Actualizar garantía declarada",List.of(entrada),List.of()),agente,prueba(agente),"condicion-dinamica",origen());
        assertEquals(2,c.getVersionContenido());assertEquals(pacto().precioPedido(),c.getCondicionEconomica().getImporteReferencia());
        var recibido=org.mockito.ArgumentCaptor.forClass(ValorEntrante.class);verify(atributos).escribir(eq(agente),eq(new Comercializacion(9,"D","A")),recibido.capture());assertEquals("Pacto TEST",recibido.getValue().procedencia().evidenciaRef());assertEquals(HOY.minusDays(1),recibido.getValue().procedencia().observadoEn());
        assertTrue(serie.isEmpty());
    }
    @Test void casObsoletoNoEscribeCondicionesDinamicasNiCreaUnRecibo(){
        assertThrows(ConflictoException.class,()->servicio.editar(9,new Edicion(1,null,null,null,null,null,"Versión obsoleta",List.of(new PropiedadUniversalService.ValorAtributo("garantia_meses","3")),List.of()),agente,prueba(agente),"condicion-obsoleta",origen()));
        verify(atributos,never()).escribir(any(),any(),any());verify(registros,never()).save(any());assertEquals(1,c.getVersionContenido());
    }
    @Test void condicionesDinamicasFirmadasUsanLaMismaProteccionQueElPacto(){
        presentar(preparada());clearInvocations(atributos);
        assertThrows(ReglaNegocioException.class,()->servicio.editar(9,new Edicion(0,null,null,null,null,null,"Cambiar términos aceptados",List.of(new PropiedadUniversalService.ValorAtributo("garantia_meses","3")),List.of()),agente,prueba(agente),"dinamica-firmada",origen()));
        verify(atributos,never()).escribir(any(),any(),any());verify(atributos,never()).retirar(any(),any(),anyString(),any());assertEquals(1,c.getVersionContenido());
    }
    @Test void prepararPdfAntesDelInmuebleConservaFaltantesSinCrearEpisodio() {
        var d=servicio.prepararDocumento(new Preparacion(null,null,null,null,List.of(),OperacionInmobiliaria.ALQUILER,null,null,null,null,null,null,null),agente,prueba(agente),"pdf-parcial",origen());
        assertNull(d.encargo());assertTrue(d.faltantes().contains("PARTES_Y_REPRESENTACION"));assertTrue(d.faltantes().contains("DATOS_DEL_INMUEBLE"));
        verify(caps,never()).saveAndFlush(any());assertFalse(c.isTerminosAceptados());
    }
    @Test void prepararSobreEncargoExistenteExigeSuVersionSinDesreferenciarNull() {
        assertThrows(ReglaNegocioException.class,()->servicio.prepararDocumento(new Preparacion(9L,null,7L,null,List.of(),OperacionInmobiliaria.ALQUILER,null,null,null,null,null,null,null),agente,prueba(agente),"pdf-sin-version",origen()));
        verify(binarios,never()).generar(any(DocumentosEncargo.Origen.class),any());assertTrue(documentos.isEmpty());
    }
    @Test void generarNoAceptaYPresentarNoApruebaLaDecisionCompetenteEsSeparada() {
        var preparada=preparada();assertFalse(c.isTerminosAceptados());assertNull(p.getDisponibilidadComercial());
        var pet=presentar(preparada);assertEquals("PENDIENTE",pet.estado());assertTrue(c.isTerminosAceptados());assertEquals(Captacion.EN_PREPARACION,c.estadoActual());assertTrue(serie.isEmpty());
        aprobar(pet,"aprobar");assertEquals(Captacion.APROBADO,c.estadoActual());assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());assertEquals(1,serie.size());assertEquals("D",p.getDisponibilidadComercial());
    }
    @Test void repetirDecisionExactaDevuelveResultadoOriginalSinDuplicarHitoHistoria() {
        var pet=presentar(preparada());var aprobada=aprobar(pet,"misma-decision");int anteriores=historia.size();
        assertEquals(aprobada,aprobar(pet,"misma-decision"));assertEquals(anteriores,historia.size());assertEquals(1,serie.size());
    }
    @Test void otraEntradaConLaMismaClaveNoReutilizaLaAutorizacionAnterior() {
        var pet=presentar(preparada());aprobar(pet,"misma-decision");
        assertThrows(ConflictoException.class,()->servicio.decidir(pet.id(),new Decision("RECHAZAR",pet.version(),c.getVersion(),"Otra decisión",null),broker,prueba(broker),"misma-decision",origen()));assertEquals(1,serie.size());
    }
    @Test void observarConservaEpisodioYReenviarConservaIdentidadYAntecedentes() {
        var preparada=preparada();var pet=presentar(preparada);
        var observada=servicio.decidir(pet.id(),new Decision("OBSERVAR",pet.version(),c.getVersion(),"Precisar sustento de representación",null),broker,prueba(broker),"observar",origen());
        assertEquals("OBSERVADA",observada.estado());assertEquals("Precisar sustento de representación",observada.observaciones());assertEquals(Captacion.EN_PREPARACION,c.estadoActual());
        var reenviada=servicio.corregirYReenviar(pet.id(),observada.version(),presentacion(preparada),agente,prueba(agente),"reenviar",origen());
        assertEquals(pet.id(),reenviada.id());assertEquals("PENDIENTE",reenviada.estado());assertEquals(1,peticiones.size());assertEquals(3,historia.size());
    }
    @Test void rechazarNoRescindeElEpisodioNiReactivaLaMismaPeticion() {
        var preparada=preparada();var pet=presentar(preparada);
        servicio.decidir(pet.id(),new Decision("RECHAZAR",pet.version(),c.getVersion(),"No acreditado",null),broker,prueba(broker),"rechazar",origen());
        assertEquals(Captacion.EN_PREPARACION,c.estadoActual());
        assertThrows(ReglaNegocioException.class,()->servicio.corregirYReenviar(pet.id(),0,presentacion(preparada),agente,prueba(agente),"reabrir-rechazada",origen()));
        var nueva=servicio.presentar(9,presentacion(preparada),agente,prueba(agente),"nueva-peticion",origen());assertNotEquals(pet.id(),nueva.id());assertEquals(2,peticiones.size());
    }
    @Test void noDecideGobiernoNiUnResponsableEmpresarialPorElBroker() {
        var pet=presentar(preparada());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.decidir(pet.id(),new Decision("APROBAR",0,0,null,null),admin,prueba(admin),"gob",origen()));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.decidir(pet.id(),new Decision("APROBAR",0,0,null,null),agente,prueba(agente),"auto-aprobar",origen()));assertTrue(serie.isEmpty());
    }
    @Test void independienteDecidePorAutoridadPropiaSinBrokerFicticio() {
        organizaciones.findById(1L).orElseThrow().setModalidad("INDIVIDUAL");var pet=presentar(preparada());
        servicio.decidir(pet.id(),new Decision("APROBAR",pet.version(),c.getVersion(),"Confirmación propia explícita",revision(pet)),agente,prueba(agente),"independiente",origen());assertEquals(Captacion.APROBADO,c.estadoActual());
    }
    @Test void atributoOrdinarioSuficienteNoObligaAdendaNiExtiendeLaRevisionAUnFaltante() {
        aprobar(presentar(preparada()),"aprobar");datos.put("ascensores",Dato.conocido(2));
        assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());datos.put("ascensores",Dato.faltante());
        assertFalse(servicio.consultarVigencia(9,HOY,agente).habilitado());
    }
    @Test void cambiarAreaDelObjetoNoQuedaCubiertoPorDocumentoOAprobacionAnterior() {
        aprobar(presentar(preparada()),"aprobar");datos.put("metraje_total",new Dato(new BigDecimal("95"),EstadoDato.CONOCIDO,"Otro plano",AREA_TOTAL,null));
        assertFalse(servicio.consultarVigencia(9,HOY,agente).habilitado());
        assertThrows(ReglaNegocioException.class,()->servicio.regenerarDocumento(9,datosPreparacion(),agente,prueba(agente),"reemplazar-firmado",origen()));
    }
    @Test void noSobrescribePrecioFirmadoMedianteEdicionOrdinaria() {
        aprobar(presentar(preparada()),"aprobar");
        assertThrows(ReglaNegocioException.class,()->servicio.editar(9,new Edicion(0,null,null,null,null,pacto(),"Cambiar firmado",null,null),agente,prueba(agente),"editar-firmado",origen()));assertEquals(1,serie.size());
    }
    @Test void aprobarNoPisaUnaDisponibilidadReservadaNiReiniciaLaVigencia() {
        estados.aplicarDisponibilidad(p,7,com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial.RESERVADO,agente,"Reserva explícita del montaje");
        aprobar(presentar(preparada()),"aprobar");assertEquals("R",p.getDisponibilidadComercial());assertEquals(HOY,c.getFechaInicioVigencia());assertEquals(HOY.plusMonths(6),c.getFechaFinVigencia());
    }
    @Test void fechaEfectivaFuturaNoCreaSolicitudNiFin() {
        var d=new DatosFin("RESCISION",HOY.plusDays(1),"Acuerdo futuro",0,List.of());
        assertThrows(ReglaNegocioException.class,()->servicio.solicitarFin(9,d,null,null,agente,prueba(agente),"futuro",origen()));
        assertThrows(ReglaNegocioException.class,()->servicio.finalizar(9,d,broker,prueba(broker),"fin-futuro",origen()));assertTrue(solicitudes.isEmpty());assertNull(c.getFechaCierre());
    }
    @Test void finPasadoConservaFechaDeHechoDistintaDelRegistro() {
        UUID evidencia=evidencia(ArchivoEncargo.FIN,null).referencia();
        servicio.finalizar(9,new DatosFin("RESCISION",HOY.minusDays(2),"Rescisión documentada",0,List.of(evidencia)),broker,prueba(broker),"rescindir",origen());
        assertEquals(Captacion.RESCINDIDO,c.estadoActual());assertEquals(HOY.minusDays(2),c.getFechaCierre());assertTrue(historia.values().stream().allMatch(r->r.getRegistradaEn().toLocalDate().equals(HOY)));
    }
    @Test void solicitudObservadaSeReenviaConMismaIdentidadYLuegoSeAplicaFinAtomico() {
        UUID evidencia=evidencia(ArchivoEncargo.FIN,null).referencia();var fin=new DatosFin("FINALIZACION",HOY,"Fin documentado",0,List.of(evidencia));
        var s=servicio.solicitarFin(9,fin,null,null,agente,prueba(agente),"solicitar",origen());
        var o=servicio.decidirSolicitudFin(s.id(),new Decision("OBSERVAR",s.version(),0,"Precisar el motivo",null),broker,prueba(broker),"observar-fin",origen());assertNull(c.getFechaCierre());
        var r=servicio.solicitarFin(9,fin,s.id(),o.version(),agente,prueba(agente),"reenviar-fin",origen());assertEquals(s.id(),r.id());
        var a=servicio.decidirSolicitudFin(s.id(),new Decision("APROBAR",r.version(),0,"Comprobado",null),broker,prueba(broker),"aprobar-fin",origen());
        assertEquals("APROBADA",a.estado());assertEquals(Captacion.FINALIZADO,c.estadoActual());assertEquals(HOY,c.getFechaCierre());
    }
    @Test void cancelarSolicitudNoFinalizaNiPermiteCancelarLaYaDecidida() {
        UUID e=evidencia(ArchivoEncargo.FIN,null).referencia();var d=new DatosFin("FINALIZACION",HOY,"Motivo",0,List.of(e));
        var s=servicio.solicitarFin(9,d,null,null,agente,prueba(agente),"solicitar",origen());
        var cancelada=servicio.cancelarSolicitudFin(s.id(),s.version(),"Continuar el encargo",agente,prueba(agente),"cancelar",origen());
        assertEquals("CANCELADA",cancelada.estado());assertNull(c.getFechaCierre());assertThrows(ReglaNegocioException.class,()->servicio.cancelarSolicitudFin(s.id(),0,"Otra vez",agente,prueba(agente),"cancelar-otra",origen()));
    }
    @Test void pdfExplicaElConceptoComercialSinCodigosNiImportesFuturos() {
        preparada();var visual=org.mockito.ArgumentCaptor.forClass(DocumentosEncargo.Contenido.class);verify(binarios).generar(argThat(o->o.actor().idOrganizacion()==1L),visual.capture());
        String texto=visual.getValue().campos().stream().map(x->x.rotulo()+": "+x.valor()).collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(texto.contains("Interior / unidad: 302"));assertTrue(texto.contains("1 mensualidad(es)"));assertTrue(texto.contains("IGV incluido"));
        assertFalse(texto.lines().anyMatch(linea->linea.equals("Tipo de comisión: E")||linea.equals("Base pactada: R")));
        assertFalse(texto.contains("privado/"));
    }
    @Test void composicionConDesgloseParcialSeConsultaSinProrratearNiCambiarElPrecioDelEncargo() {
        prepararUnidadesAutorizadas();
        var primeraEntrada=new DatosComposicion(List.of(new Unidad(7,null,null,null),
                new Unidad(8,new BigDecimal("700"),"PEN","Desglose declarado TEST")),
                new BigDecimal("2500"),"PEN","Oferta conjunta TEST",c.getVersion());
        var primera=servicio.declararComposicion(9,primeraEntrada,agente,prueba(agente),"composicion-1",origen());
        String original=composicionesGuardadas.get(primera.id()).getUnidades();
        assertNull(primera.unidades().getFirst().importeDeclarado(),"El total no se reparte entre unidades sin declaración");
        assertEquals(new BigDecimal("700"),primera.unidades().getLast().importeDeclarado());
        assertEquals(primera,servicio.consultarComposicion(9,null,agente));
        var segunda=servicio.declararComposicion(9,new DatosComposicion(List.of(new Unidad(7,null,null,null)),
                null,null,"Composición nueva TEST sin importe total conocido",c.getVersion()),agente,prueba(agente),"composicion-2",origen());
        assertNotEquals(primera.id(),segunda.id());assertTrue(segunda.version()>primera.version());
        assertEquals(segunda,servicio.consultarComposicion(9,null,agente));assertNull(segunda.totalDeclarado());
        assertEquals(primera,servicio.consultarComposicion(9,primera.version(),agente),"La consulta histórica conserva la composición solicitada");
        assertThrows(NoEncontradoException.class,()->servicio.consultarComposicion(9,9999L,agente),"Una versión inexistente no devuelve la vigente");
        assertEquals(original,composicionesGuardadas.get(primera.id()).getUnidades());
        assertEquals(primera,servicio.declararComposicion(9,primeraEntrada,agente,prueba(agente),"composicion-1",origen()),"El recibo original no se reemplaza por la composición vigente");
        assertEquals(2,composicionesGuardadas.size());assertEquals(pacto().precioPedido(),servicio.consultar(9,agente).condiciones().precioPedido());
        assertTrue(serie.isEmpty(),"Declarar unidades no autoriza precio ni operación");
    }
    @Test void composicionNoConcedeUnidadesAjenasNiModificaUnaVersionAceptada() {
        prepararUnidadesAutorizadas();
        var datos=new DatosComposicion(List.of(new Unidad(7,null,null,null)),null,null,"Objeto declarado TEST",c.getVersion());
        when(propiedades.buscarVariasParaLectura(eq(1L),anyCollection(),anyString())).thenReturn(List.of());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.declararComposicion(9,datos,agente,prueba(agente),"unidad-ajena",origen()));
        assertTrue(composicionesGuardadas.isEmpty());assertFalse(comandos.containsKey("unidad-ajena"));
        prepararUnidadesAutorizadas();aprobar(presentar(preparada()),"aceptar-antes-componer");
        assertThrows(ReglaNegocioException.class,()->servicio.declararComposicion(9,datos,agente,prueba(agente),"composicion-firmada",origen()));
        assertTrue(composicionesGuardadas.isEmpty());assertEquals(1,serie.size());
    }
    @Test void composicionConservaImportesYMonedasNormalizadasEnTotalDesgloseYRecibo() throws Exception {
        prepararUnidadesAutorizadas();
        var entrada=new DatosComposicion(List.of(new Unidad(7,new BigDecimal("700.25")," pen ","Desglose expreso TEST"),
                new Unidad(8,null,null,null)),new BigDecimal("2500.50")," pen ","Total expreso TEST",c.getVersion());
        var salida=servicio.declararComposicion(9,entrada,agente,prueba(agente),"monedas-normalizadas",origen());
        assertEquals("PEN",salida.moneda());assertEquals("PEN",salida.unidades().getFirst().moneda());
        assertEquals(0,new BigDecimal("700.25").compareTo(salida.unidades().getFirst().importeDeclarado()));
        assertNull(salida.unidades().getLast().importeDeclarado());
        var guardada=composicionesGuardadas.get(salida.id());assertEquals("PEN",guardada.getMoneda());
        assertEquals("PEN",mapper.readTree(guardada.getUnidades()).get(0).get("moneda").asText());
        assertEquals(salida,servicio.declararComposicion(9,entrada,agente,prueba(agente),"monedas-normalizadas",origen()));
    }
    @Test void propuestaDeOtraSeleccionNoHeredaPrecioNiTotalDeLaComposicionAnterior() throws Exception {
        prepararUnidadesAutorizadas();
        var base=servicio.declararComposicion(9,new DatosComposicion(List.of(new Unidad(7,null,null,null),new Unidad(8,null,null,null)),
                new BigDecimal("2500"),"PEN","Paquete de origen TEST",c.getVersion()),agente,prueba(agente),"base-documento",origen());
        var partes=datosPreparacion().partes();
        var documento=servicio.prepararDocumento(new Preparacion(9L,c.getVersion(),7L,null,partes,OperacionInmobiliaria.ALQUILER,
                List.of(new Unidad(7,null,null,null)),null,null,null,null,null,null),agente,prueba(agente),"propuesta-otra-seleccion",origen());
        var contenido=mapper.readTree(documentos.get(documento.id()).getContenido());
        assertTrue(contenido.get("totalComposicion").isNull());assertTrue(contenido.get("monedaComposicion").isNull());
        assertTrue(contenido.get("condiciones").isNull(),"El precio y pacto del paquete no se atribuyen a la nueva selección");
        assertFalse(documento.faltantes().isEmpty(),"El PDF privado explica lo todavía no declarado sin impedir prepararlo");
        assertEquals(base,servicio.consultarComposicion(9,null,agente));assertEquals(1,composicionesGuardadas.size());
        assertEquals(0,new BigDecimal("2500").compareTo(servicio.consultar(9,agente).condiciones().precioPedido()));
        var condicionesPropuestas=new Condiciones(new BigDecimal("1600"),"PEN","MENSUAL","E","R",BigDecimal.ONE,"PEN","I",null,
                "Firma del contrato","Propietario","Agencia","A la firma","Propuesta explícita para la unidad7",List.of(),null,null);
        var explicito=servicio.prepararDocumento(new Preparacion(9L,c.getVersion(),7L,null,partes,OperacionInmobiliaria.ALQUILER,
                List.of(new Unidad(7,null,null,null)),condicionesPropuestas,null,null,null,null,null),agente,prueba(agente),"precio-propio-propuesta",origen());
        var propuesto=mapper.readTree(documentos.get(explicito.id()).getContenido());
        assertEquals(0,new BigDecimal("1600").compareTo(propuesto.get("condiciones").get("precioPedido").decimalValue()));
        assertTrue(propuesto.get("totalComposicion").isNull());assertEquals(base,servicio.consultarComposicion(9,null,agente));
        assertTrue(serie.isEmpty());assertFalse(c.isTerminosAceptados());
    }
    @Test void explicitarElMismoObjetoImplicitoConservaSuPactoSinFabricarComposicion() throws Exception {
        prepararUnidadesAutorizadas();
        var documento=servicio.prepararDocumento(new Preparacion(9L,c.getVersion(),7L,null,datosPreparacion().partes(),OperacionInmobiliaria.ALQUILER,
                List.of(new Unidad(7,null,null,"Referencia explícita al mismo inmueble TEST")),null,null,null,null,null,null),
                agente,prueba(agente),"mismo-objeto-implicito",origen());
        var contenido=mapper.readTree(documentos.get(documento.id()).getContenido());
        assertEquals(0,new BigDecimal("2500").compareTo(contenido.get("condiciones").get("precioPedido").decimalValue()));
        assertTrue(contenido.get("totalComposicion").isNull());assertNull(c.getIdComposicionActual());
        assertTrue(composicionesGuardadas.isEmpty());assertTrue(serie.isEmpty());
    }
    @Test void replayDeComposicionReautorizaSusUnidadesOriginalesAunqueLaActualSigaLegible() {
        prepararUnidadesAutorizadas();
        var primeraEntrada=new DatosComposicion(List.of(new Unidad(7,null,null,null),new Unidad(8,null,null,null)),
                new BigDecimal("2500"),"PEN","Conjunto inicial TEST",c.getVersion());
        var original=servicio.declararComposicion(9,primeraEntrada,agente,prueba(agente),"conjunto-original",origen());
        var segundaEntrada=new DatosComposicion(List.of(new Unidad(7,null,null,null)),null,null,"Conjunto actual TEST",c.getVersion());
        var vigente=servicio.declararComposicion(9,segundaEntrada,agente,prueba(agente),"conjunto-actual",origen());
        when(propiedades.buscarVariasParaLectura(eq(1L),anyCollection(),anyString())).thenReturn(List.of(p));
        assertEquals(vigente,servicio.consultarComposicion(9,null,agente),"El padre y las unidades actuales siguen autorizados");
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarComposicion(9,original.version(),agente),"Consultar una versión previa tampoco recupera permisos sobre sus unidades");
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.declararComposicion(9,primeraEntrada,agente,prueba(agente),"conjunto-original",origen()));
        assertEquals(vigente,servicio.declararComposicion(9,segundaEntrada,agente,prueba(agente),"conjunto-actual",origen()));
        assertEquals(2,composicionesGuardadas.size());assertTrue(serie.isEmpty());
    }
    @Test void regenerarDocumentoNoAceptadoConservaOriginalYNoActivaNiApruebaLaNuevaVersion() {
        var primera=preparada();var original=documentos.get(primera.documento().id());
        String contenido=original.getContenido();UUID referencia=original.getArchivoRef();
        var nuevoPacto=new Condiciones(new BigDecimal("2600"),"PEN","MENSUAL","E","R",BigDecimal.ONE,"PEN","I",null,
                "Firma del contrato","Propietario","Agencia","A la firma","Nueva indicación TEST",List.of(),null,null);
        servicio.editar(9,new Edicion(c.getVersion(),null,null,null,null,nuevoPacto,"Corrección del precio pedido no firmado",null,null),
                agente,prueba(agente),"corregir-antes-regenerar",origen());
        var entrada=new Preparacion(9L,c.getVersion(),7L,null,primera.partes(),OperacionInmobiliaria.ALQUILER,null,null,null,null,null,null,null);
        var nueva=servicio.regenerarDocumento(9,entrada,agente,prueba(agente),"regenerar",origen());
        assertNotEquals(primera.documento().id(),nueva.id());assertNotEquals(referencia,nueva.referencia());
        assertEquals(contenido,original.getContenido());assertEquals(referencia,original.getArchivoRef());
        assertNull(c.getIdDocumentoActual(),"Preparar otro PDF no acredita una presentación");assertTrue(peticiones.isEmpty());
        assertFalse(c.isTerminosAceptados());assertTrue(serie.isEmpty());
        assertEquals(nueva,servicio.regenerarDocumento(9,entrada,agente,prueba(agente),"regenerar",origen()));assertEquals(2,documentos.size());
    }
    @Test void regenerarMismoContenidoAceptadoNoSustituyeLaVersionPresentadaNiSuEvidencia() {
        var primera=preparada();var peticion=presentar(primera);String contenido=documentos.get(primera.documento().id()).getContenido();
        var entrada=new Preparacion(9L,c.getVersion(),7L,null,primera.partes(),OperacionInmobiliaria.ALQUILER,null,null,null,null,null,null,null);
        var nueva=servicio.regenerarDocumento(9,entrada,agente,prueba(agente),"regenerar-mismo-contenido",origen());
        assertNotEquals(primera.documento().id(),nueva.id());assertNotEquals(primera.documento().referencia(),nueva.referencia());
        assertEquals(primera.documento().huella(),nueva.huella());
        assertEquals(contenido,documentos.get(primera.documento().id()).getContenido());
        assertEquals(primera.documento().id(),c.getIdDocumentoActual());assertEquals(primera.documento().id(),peticiones.get(peticion.id()).getIdDocumento());
        assertTrue(c.isTerminosAceptados());assertEquals(PeticionRevisionEncargo.PENDIENTE,peticiones.get(peticion.id()).estadoActual());
        assertTrue(serie.isEmpty(),"Otro PDF no acredita la revisión aprobatoria ni otro precio autorizado");
    }
    @Test void solicitudDeFinSeConsultaEnSuAmbitoSinConvertirLaLecturaEnDecision() {
        UUID ref=evidencia(ArchivoEncargo.FIN,null).referencia();
        var solicitud=servicio.solicitarFin(9,new DatosFin("FINALIZACION",HOY,"Hecho declarado TEST",c.getVersion(),List.of(ref)),
                null,null,agente,prueba(agente),"solicitud-para-consultar",origen());
        assertEquals(solicitud,servicio.consultarSolicitudFin(solicitud.id(),agente));
        assertThrows(NoEncontradoException.class,()->servicio.consultarSolicitudFin(solicitud.id(),new Actor(2,10,30,Actor.AGENTE)));
        assertNull(c.getFechaCierre());assertEquals("PENDIENTE",servicio.consultarSolicitudFin(solicitud.id(),agente).estado());
    }
    private void prepararUnidadesAutorizadas(){
        var otra=new Propiedad();otra.setOrganizacionId(1L);otra.setTipoInmueble("D");otra.setInteriorUnidad("303");otra.setDireccion("Av. Perú 123");id(otra,8);
        when(autoridadFisica.alcanceDeLectura(agente)).thenReturn(new Alcances.Alcance(1,false,List.of(30L)));
        when(propiedades.buscarVariasParaLectura(eq(1L),anyCollection(),anyString())).thenAnswer(i->{
            Collection<Long> solicitadas=i.getArgument(1);return List.of(p,otra).stream().filter(x->solicitadas.contains(x.getId())).toList();
        });
    }
    @Test void huellaIgnoraOrdenDelConjuntoYConservaOrdenDeclaradoDeLasListas() {
        var entrada1=Map.of("prestaciones",new LinkedHashSet<>(List.of("ascensores","piso")),"unidades",List.of(7L,8L));
        var entrada2=Map.of("prestaciones",new LinkedHashSet<>(List.of("piso","ascensores")),"unidades",List.of(7L,8L));
        for(String algoritmo:List.of("huella","huellaMaterialAdenda")) {
            String h1=ReflectionTestUtils.invokeMethod(servicio,algoritmo,entrada1),h2=ReflectionTestUtils.invokeMethod(servicio,algoritmo,entrada2);
            assertEquals(h1,h2,algoritmo);
            assertNotEquals(h1,ReflectionTestUtils.invokeMethod(servicio,algoritmo,Map.of("prestaciones",Set.of("piso","ascensores"),"unidades",List.of(8L,7L))));
        }
    }
    @Test void cargaTecnicaNoVinculaNiAcreditaAceptacion() {
        var carga=carga();assertNotNull(carga.referencia());assertEquals(1,originales.size());assertTrue(ficheros.isEmpty());
        assertFalse(c.isTerminosAceptados());assertNull(c.getIdPeticionActual());assertTrue(peticiones.isEmpty());
    }
    @Test void reusarConservaOriginalYFinalidadDeDestinoSinRecopiarBytes() {
        var carga=carga();var nuevo=servicio.adjuntarOReutilizar(vinculo(carga),agente,prueba(agente),"vinculo",origen());
        assertNotEquals(carga.referencia(),nuevo.referencia());var archivo=ficheros.get(nuevo.referencia());
        assertEquals(carga.referencia(),archivo.getOrigenReferencia());assertEquals("CARGA_TEMPORAL",archivo.getOrigenTipo());
        assertEquals(originales.get(carga.referencia()).getClave(),archivo.getClaveProveedor());
        verify(binarios,times(1)).guardar(any(DocumentosEncargo.Origen.class),anyString(),anyString(),any());verify(binarios,times(1)).abrir(anyString());
        assertFalse(c.isTerminosAceptados());assertTrue(peticiones.isEmpty());
    }
    @Test void replayDeVinculoReautorizaOrigenYNoVuelveALeerOCopiar() {
        var carga=carga();var d=vinculo(carga);var primero=servicio.adjuntarOReutilizar(d,agente,prueba(agente),"vinculo",origen());
        assertEquals(primero,servicio.adjuntarOReutilizar(d,agente,prueba(agente),"vinculo",origen()));
        assertEquals(1,ficheros.size());verify(binarios,times(1)).abrir(anyString());verify(a04,times(2)).resolver(carga.referencia().toString(),agente);
        when(a04.resolver(carga.referencia().toString(),agente)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.adjuntarOReutilizar(d,agente,prueba(agente),"vinculo",origen()));
    }
    @Test void almacenAlteradoNoSeAceptaComoOtraVersionDelOriginal() {
        var carga=carga();blobs.put(originales.get(carga.referencia()).getClave(),new byte[]{4,5,6});
        assertThrows(IllegalStateException.class,()->servicio.adjuntarOReutilizar(vinculo(carga),agente,prueba(agente),"alterado",origen()));
        assertTrue(ficheros.isEmpty());assertFalse(comandos.containsKey("alterado"));assertEquals(1,originales.size());
    }
    @Test void revocacionDuranteLaLecturaNoDejaVinculoNiRecibo() {
        var carga=carga();doThrow(new AccesoNoAutorizadoException()).when(a04).revalidar(any(),eq(agente));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.adjuntarOReutilizar(vinculo(carga),agente,prueba(agente),"revocado",origen()));
        assertTrue(ficheros.isEmpty());assertFalse(comandos.containsKey("revocado"));assertEquals(1,originales.size());
    }
    @Test void versionDestinoObsoletaNoVinculaElOriginalANuevoContenido() {
        var carga=carga();ReflectionTestUtils.setField(c,"version",1L);
        assertThrows(ConflictoException.class,()->servicio.adjuntarOReutilizar(new VinculoArchivo(carga.referencia(),9L,null,ArchivoEncargo.TITULARIDAD,0),agente,prueba(agente),"obsoleto",origen()));
        assertTrue(ficheros.isEmpty());verify(binarios,never()).abrir(anyString());
    }
    @Test void gobiernoNoCargaNiVinculaYLaClaveNoEsUnaConcesion() {
        var carga=carga();assertThrows(AccesoNoAutorizadoException.class,()->servicio.cargarArchivo(new CargaArchivo(9L,null,ArchivoEncargo.FIN,"dato.txt","text/plain",new byte[]{1},0),admin,prueba(admin),"carga-gob",origen()));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.adjuntarOReutilizar(vinculo(carga),admin,prueba(admin),"vinculo-gob",origen()));assertTrue(ficheros.isEmpty());
    }
    @Test void firmaExigeCuatroDatosYNoConfundeContactoConFirmante() {
        var preparada=preparada();var correcta=presentacion(preparada);var ref=correcta.firmas().getFirst().documentoFirmado();
        var invalidas=List.of(new FirmaManuscrita(null,HOY,ref,"Instrumento"),
                new FirmaManuscrita(new Firmante(80,999),HOY,ref,"Contacto sin legitimación"),
                new FirmaManuscrita(new Firmante(999,90),HOY,ref,"Otra titularidad"),
                new FirmaManuscrita(new Firmante(80,90),null,ref,"Fecha desconocida"),
                new FirmaManuscrita(new Firmante(80,90),HOY.plusDays(1),ref,"Fecha futura"),
                new FirmaManuscrita(new Firmante(80,90),HOY,null,"Sin instrumento"),
                new FirmaManuscrita(new Firmante(80,90),HOY,ref," "));
        for(var firma:invalidas)assertThrows(ReglaNegocioException.class,()->servicio.presentar(9,
                new Presentacion(correcta.documento(),c.getVersion(),correcta.partes(),List.of(firma),null,null),agente,prueba(agente),clave(),origen()));
        assertThrows(ReglaNegocioException.class,()->servicio.presentar(9,
                new Presentacion(correcta.documento(),c.getVersion(),correcta.partes(),null,null,null),agente,prueba(agente),clave(),origen()));
        assertTrue(peticiones.isEmpty());assertFalse(c.isTerminosAceptados());assertTrue(serie.isEmpty());
    }
    @Test void firmaPuedeSerAnteriorALaPreparacionSinInferirLaFechaDeRegistro() {
        var preparada=preparada();var entrada=presentacion(preparada);var f=entrada.firmas().getFirst();
        var fecha=HOY.minusDays(4);
        var pet=servicio.presentar(9,new Presentacion(entrada.documento(),c.getVersion(),entrada.partes(),
                List.of(new FirmaManuscrita(f.firmante(),fecha,f.documentoFirmado(),f.descripcion())),null,null),agente,prueba(agente),clave(),origen());
        assertEquals(fecha,pet.firma().firmas().getFirst().fechaFirma());assertEquals("DOCUMENTO_FIRMADO",pet.firma().modalidad());
        assertNotEquals(documentos.get(entrada.documento()).getHuella(),pet.firma().archivos().getFirst().huella());
        assertEquals(Captacion.EN_PREPARACION,c.estadoActual());
    }
    @Test void representanteRealComparteInstrumentoConElTitularSinDuplicarBinario() {
        var identidad=mock(TitularidadPropiedadRepository.IdentidadParte.class);when(identidad.getId()).thenReturn(91L);when(identidad.getNombre()).thenReturn("Representante TEST");
        when(titulos.identidades(eq(1L),anyCollection())).thenReturn(List.of(identidad));
        UUID titulo=evidencia(ArchivoEncargo.TITULARIDAD,null).referencia(),poder=evidencia(ArchivoEncargo.REPRESENTACION,null).referencia();
        var parte=new Parte(80L,90L,new BigDecimal("100"),titulo,91L,poder,"Firma de este Encargo",HOY.minusDays(3),HOY.plusDays(2),"María Propietaria","Representante TEST");
        var doc=servicio.prepararDocumento(new Preparacion(9L,c.getVersion(),7L,null,List.of(parte),OperacionInmobiliaria.ALQUILER,null,null,null,null,null,null,null),agente,prueba(agente),clave(),origen());
        UUID ref=evidencia(ArchivoEncargo.ACEPTACION,doc.id()).referencia();
        var firmas=List.of(new FirmaManuscrita(new Firmante(80,90),HOY,ref,"Titular firmante"),
                new FirmaManuscrita(new Firmante(80,91),HOY.minusDays(1),ref,"Representante firmante"));
        var pet=servicio.presentar(9,new Presentacion(doc.id(),c.getVersion(),List.of(parte),firmas,null,null),agente,prueba(agente),clave(),origen());
        assertEquals(2,pet.firma().firmas().size());assertEquals(List.of(ref),pet.evidencias());assertEquals(1,pet.firma().archivos().size());
    }
    @Test void aprobarExigeRevisionDeLaManifestacionExactaYValidarNoProduceEfecto() {
        var pet=presentar(preparada());int anteriores=historia.size();
        for(var revision:Arrays.asList(null,new RevisionManuscrita(pet.firma().revisionPresentacion(),pet.firma().huellaManifestacion(),false)))
            assertThrows(ReglaNegocioException.class,()->servicio.validarDecision(pet.id(),new Decision("APROBAR",pet.version(),c.getVersion(),null,revision),broker));
        assertThrows(ConflictoException.class,()->servicio.validarDecision(pet.id(),new Decision("APROBAR",pet.version(),c.getVersion(),null,
                new RevisionManuscrita(pet.firma().revisionPresentacion(),"0".repeat(64),true)),broker));
        var datos=new Decision("APROBAR",pet.version(),c.getVersion(),null,revision(pet));
        assertEquals(datos,servicio.validarDecision(pet.id(),datos,broker));assertEquals(anteriores,historia.size());
        assertEquals(Captacion.EN_PREPARACION,c.estadoActual());assertTrue(serie.isEmpty());
    }
    @Test void lecturaYHabilitacionNoAbrenBinariosBajoLaTransaccionComercial() {
        var aprobada=aprobar(presentar(preparada()),"aprobar-sin-io");clearInvocations(binarios);
        assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());assertEquals(aprobada,servicio.consultarPeticion(aprobada.id(),broker));
        assertEquals(aprobada,servicio.consultarResultadoDecision("aprobar-sin-io",broker).orElseThrow());verifyNoInteractions(binarios);
    }
    @Test void integridadL1CompruebaConstanciaSinConcederLecturaDeLosInstrumentosDelCompanero() {
        aprobar(presentar(preparada()),"aprobar-para-l1");var companero=new Actor(1,13,31,Actor.AGENTE);
        doThrow(new AccesoNoAutorizadoException()).when(a04).resolver(anyString(),eq(companero));clearInvocations(a04,binarios);
        var aptitud=servicio.habilitacionParaRecomendacionInterna(9,HOY,companero);
        assertTrue(aptitud.habilitado());assertEquals(30,aptitud.responsable());
        verify(a04,never()).resolver(anyString(),eq(companero));verifyNoInteractions(binarios);
    }
    @Test void aprobacionAntiguaSinConstanciaSigueLegiblePeroNoHabilitaPorNombreOTipoDelArchivo() {
        var presentada=presentar(preparada());var pet=peticiones.get(presentada.id());
        var antigua=RevisionEncargo.registrar(1,9,pet.getId(),null,c.getVersionContenido(),broker.idRolOperativo(),"APROBAR",null,
                json.objetoCanonico(Map.of("documento",pet.getIdDocumento(),"cobertura",pet.getHuellaCobertura())),pet.getHuellaCobertura(),HOY.atStartOfDay().atOffset(ZoneOffset.ofHours(-5)));
        id(antigua,ids.incrementAndGet());historia.put(antigua.getId(),antigua);pet.registrarRevision(antigua.getId());
        pet.transicionarA(PeticionRevisionEncargo.APROBADA);c.registrarRevisionAplicable(antigua.getId());c.transicionarA(Captacion.APROBADO);
        var leida=servicio.consultarPeticion(pet.getId(),agente);assertEquals("APROBADA",leida.estado());assertNull(leida.firma());
        var vigencia=servicio.consultarVigencia(9,HOY,agente);assertFalse(vigencia.habilitado());assertTrue(vigencia.faltantes().contains("FIRMA_MANUSCRITA_NO_ACREDITADA"));
        assertEquals(Captacion.APROBADO,c.estadoActual());assertTrue(serie.isEmpty());
    }
    @Test void observarUnAntecedenteSinManuscritaConservaReferenciasDeEstaActuacion() {
        var antigua=presentacionLegada();var datos=new Decision("OBSERVAR",antigua.version(),c.getVersion(),"Aportar documento manuscrito",null);
        assertEquals(datos,servicio.validarDecision(antigua.id(),datos,broker));
        var resultado=servicio.decidir(antigua.id(),datos,broker,prueba(broker),"observar-legado",origen());
        assertEquals("OBSERVADA",resultado.estado());assertNull(resultado.firma());
        var sobre=json.comoMapa(comandos.get("observar-legado").getResultado());assertNotNull(sobre.get("referenciasPeticion"));
        assertEquals(resultado,servicio.consultarResultadoDecision("observar-legado",broker).orElseThrow());
        var archivo=ficheros.get(resultado.evidencias().getFirst());ReflectionTestUtils.setField(archivo,"version",archivo.getVersion()+1);
        assertThrows(ConflictoException.class,()->servicio.consultarResultadoDecision("observar-legado",broker));
        assertEquals(Captacion.EN_PREPARACION,c.estadoActual());assertTrue(serie.isEmpty());
    }
    @Test void reciboAntiguoSinVersionesFallaCerradoSinBorrarNiSuplantarPorDatosActuales() {
        var antigua=presentacionLegada();var datos=new Decision("OBSERVAR",antigua.version(),c.getVersion(),"Revisar antecedente",null);
        servicio.decidir(antigua.id(),datos,broker,prueba(broker),"recibo-antiguo",origen());
        var recibo=comandos.get("recibo-antiguo");var sobre=json.comoMapa(recibo.getResultado());sobre.remove("referenciasPeticion");
        String legado=json.objetoCanonico(sobre);ReflectionTestUtils.setField(recibo,"resultado",legado);
        var fallo=assertThrows(ReglaNegocioException.class,()->servicio.consultarResultadoDecision("recibo-antiguo",broker));
        assertTrue(fallo.getMessage().contains("versiones originales"));assertEquals(legado,recibo.getResultado());
        assertEquals("OBSERVADA",servicio.consultarPeticion(antigua.id(),agente).estado());
        when(alcances.alcanza(broker,30L)).thenReturn(false);
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(1L,false,List.of()));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarResultadoDecision("recibo-antiguo",broker));
        assertTrue(serie.isEmpty());
    }
    @Test void replayReautorizaLaVersionBinariaOriginalAunqueLaPeticionTengaOtraFirma() {
        var preparada=preparada();var entrada=presentacion(preparada);
        var original=servicio.presentar(9,entrada,agente,prueba(agente),"original-firma",origen());
        servicio.decidir(original.id(),new Decision("OBSERVAR",original.version(),c.getVersion(),"Otro sustento",null),broker,prueba(broker),"observar-original",origen());
        var nueva=servicio.corregirYReenviar(original.id(),original.version(),presentacion(preparada),agente,prueba(agente),"nueva-firma",origen());
        assertNotEquals(original.firma().huellaManifestacion(),nueva.firma().huellaManifestacion());
        assertEquals(original,servicio.presentar(9,entrada,agente,prueba(agente),"original-firma",origen()));
        var archivo=ficheros.get(original.firma().archivos().getFirst().referencia());ReflectionTestUtils.setField(archivo,"version",archivo.getVersion()+1);
        assertEquals(nueva,servicio.consultarPeticion(nueva.id(),agente));
        assertThrows(ConflictoException.class,()->servicio.presentar(9,entrada,agente,prueba(agente),"original-firma",origen()));
        assertEquals(1,peticiones.size());assertTrue(serie.isEmpty());
    }
    @Test void leerUnaDecisionViejaUsaSuPresentacionOriginalSinExigirCasActual() {
        var preparada=preparada();var original=presentar(preparada);
        var datos=new Decision("OBSERVAR",original.version(),c.getVersion(),"Precisar instrumento",null);
        servicio.decidir(original.id(),datos,broker,prueba(broker),"decision-vieja",origen());
        servicio.corregirYReenviar(original.id(),original.version(),presentacion(preparada),agente,prueba(agente),"firma-cambiada",origen());
        ReflectionTestUtils.setField(peticiones.get(original.id()),"version",original.version()+3);
        assertDoesNotThrow(()->servicio.exigirLecturaDecision(original.id(),datos,broker));
        var archivo=ficheros.get(original.firma().archivos().getFirst().referencia());ReflectionTestUtils.setField(archivo,"version",archivo.getVersion()+1);
        assertThrows(ConflictoException.class,()->servicio.exigirLecturaDecision(original.id(),datos,broker));
    }
    @Test void revocarLecturaDelInstrumentoNoDevuelveElReciboGuardado() {
        var aprobada=aprobar(presentar(preparada()),"decision-privada");UUID ref=aprobada.firma().archivos().getFirst().referencia();
        doThrow(new AccesoNoAutorizadoException()).when(a04).resolver(ref.toString(),broker);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarResultadoDecision("decision-privada",broker));
        assertEquals(1,serie.size());assertEquals(Captacion.APROBADO,c.estadoActual());
    }
    @Test void laDecisionEfectoraExigeLaTransaccionDelDispatchCanonico() throws Exception {
        var metodo=EncargoServiceImpl.class.getMethod("decidir",long.class,Decision.class,Actor.class,ContextoSesion.class,String.class,Procedencia.class);
        assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,
                metodo.getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation());
    }
    @Test void pactoSeLeeDesdeLaAprobacionExactaConSnapshotDePeticionAunPendiente() {
        var base=baseFirmada(broker);var r=historia.get(base.pacto().revisionAprobatoria());
        assertEquals("PENDIENTE",((Map<?,?>)json.comoMapa(r.getSnapshot()).get("peticion")).get("estado"));
        var firmado=pactos.consultarPactoFirmado(base.pacto(),agente);
        assertEquals(base.pacto(),firmado.referencia());assertEquals(base.peticion().documento(),firmado.acreditacion().documento());
        assertEquals(base.peticion().id(),firmado.acreditacion().peticion());assertEquals(BigDecimal.ONE,firmado.regla().valorComision());
        assertEquals("R",firmado.regla().baseCalculo());assertNull(firmado.regla().ejecucion());
    }
    @Test void primerHitoDeAdendaNoGeneraUnPactoNiConmutaDocumentoCondicionOPrecio() {
        var base=baseFirmada(broker);long docAnterior=c.getIdDocumentoActual();var condicion=c.getCondicionEconomica();
        var propuesta=proponerCambio(base,"2800","2");verify(binarios,times(1)).generar(any(DocumentosEncargo.Origen.class),any());
        assertNull(propuesta.documento());assertEquals(1,serie.size());
        var borrador=autorizar(propuesta,broker);assertNotNull(borrador.documento());
        assertEquals(docAnterior,c.getIdDocumentoActual());assertSame(condicion,c.getCondicionEconomica());
        assertEquals(base.pacto().revisionAprobatoria(),c.getIdRevisionAplicable());assertEquals(1,serie.size());
        assertThrows(ReglaNegocioException.class,()->pactos.consultarPactoFirmado(new PactosDeEncargo.ReferenciaPacto(9,borrador.revisionAutorizacionBorrador()),broker));
        assertEquals(BigDecimal.ONE,pactos.consultarPactoFirmado(base.pacto(),broker).regla().valorComision());
    }
    @Test void noHayPdfDeAdendaSinAutorizacionNiSePresentaOtroDocumentoComoSuCandidata() {
        var base=baseFirmada(broker);var propuesta=proponerCambio(base,"2800","2");
        assertThrows(ConflictoException.class,()->servicio.presentar(9,new Presentacion(base.peticion().documento(),c.getVersion(),base.preparada().partes(),
                base.peticion().firma().firmas(),null,propuesta.referencia()),agente,prueba(agente),clave(),origen()));
        verify(binarios,times(1)).generar(any(DocumentosEncargo.Origen.class),any());assertEquals(base.peticion().id(),c.getIdPeticionActual());
    }
    @Test void agenteCorporativoYTenantAdminNoAutorizanElBorradorComercial() {
        var propuesta=proponerCambio(baseFirmada(broker),"2800","2");
        assertThrows(AccesoNoAutorizadoException.class,()->autorizar(propuesta,agente));
        assertThrows(AccesoNoAutorizadoException.class,()->autorizar(propuesta,admin));
        verify(binarios,times(1)).generar(any(DocumentosEncargo.Origen.class),any());assertNull(servicio.consultarAdenda(propuesta.referencia(),agente).documento());
    }
    @Test void segundaDecisionActivaNuevaVersionYPreservaPactoDocumentoYUAnteriores() {
        var base=baseFirmada(broker);String contenidoAnterior=documentos.get(base.peticion().documento()).getContenido();
        var borrador=autorizar(proponerCambio(base,"2800","2"),broker);var pendiente=presentarCandidata(borrador);
        assertEquals(base.peticion().id(),c.getIdPeticionActual());assertEquals(base.peticion().documento(),c.getIdDocumentoActual());
        assertEquals(1,c.getVersionContenido());assertEquals("PENDIENTE",pendiente.estado());assertEquals(borrador.referencia(),pendiente.adenda());
        var aprobada=aprobar(pendiente,clave());var refNueva=new PactosDeEncargo.ReferenciaPacto(9,c.getIdRevisionAplicable());
        assertEquals(2,c.getVersionContenido());assertEquals(aprobada.id(),c.getIdPeticionActual());assertEquals(borrador.documento(),c.getIdDocumentoActual());
        assertEquals(new BigDecimal("2"),pactos.consultarPactoFirmado(refNueva,broker).regla().valorComision());
        assertEquals(BigDecimal.ONE,pactos.consultarPactoFirmado(base.pacto(),broker).regla().valorComision());
        assertEquals(contenidoAnterior,documentos.get(base.peticion().documento()).getContenido());
        assertEquals(2,serie.size());assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());
    }
    @Test void dosCandidatasNoActivanDosInstrumentosSobreLaMismaBase() {
        var base=baseFirmada(broker);var a=autorizar(proponerCambio(base,"2800","2"),broker);
        var b=autorizar(proponerCambio(base,"2900","3"),broker);var pa=presentarCandidata(a);var pb=presentarCandidata(b);
        aprobar(pa,clave());long revision=c.getIdRevisionAplicable();
        assertThrows(ConflictoException.class,()->aprobar(pb,clave()));
        assertEquals(revision,c.getIdRevisionAplicable());assertEquals(a.documento(),c.getIdDocumentoActual());assertEquals(2,serie.size());
    }
    @Test void observarORechazarLaCandidataNoRetiraElInstrumentoVigente() {
        var base=baseFirmada(broker);var borrador=autorizar(proponerCambio(base,"2800","2"),broker);var p=presentarCandidata(borrador);
        var observada=servicio.decidir(p.id(),new Decision("OBSERVAR",p.version(),c.getVersion(),"Revisar soporte",null),broker,prueba(broker),clave(),origen());
        assertEquals("OBSERVADA",observada.estado());assertEquals(base.peticion().documento(),c.getIdDocumentoActual());assertEquals(1,serie.size());
        assertEquals(base.pacto().revisionAprobatoria(),c.getIdRevisionAplicable());
    }
    @Test void cambiarUnDatoMaterialDespuesDeAutorizarNoLoSustituyeDebajoDeLaFirma() {
        var base=baseFirmada(broker);var borrador=autorizar(proponerCambio(base,"2800","2"),broker);
        p.setDireccion("Otra dirección real");datos.put("direccion",Dato.conocido(p.getDireccion()));
        assertThrows(ConflictoException.class,()->presentarCandidata(borrador));
        assertEquals(base.peticion().documento(),c.getIdDocumentoActual());assertEquals(1,serie.size());
    }
    @Test void independienteEjerceLosDosActosExplicitosSinBrokerFicticio() {
        organizaciones.findById(1L).orElseThrow().setModalidad("INDIVIDUAL");var base=baseFirmada(agente);
        var propuesta=proponerCambio(base,"2800","2");assertNull(propuesta.revisionAutorizacionBorrador());
        var borrador=autorizar(propuesta,agente);assertEquals(base.peticion().documento(),c.getIdDocumentoActual());
        var pendiente=presentarCandidata(borrador);
        var resultado=servicio.decidir(pendiente.id(),new Decision("APROBAR",pendiente.version(),c.getVersion(),"Activación propia expresa",revision(pendiente)),
                agente,prueba(agente),clave(),origen());
        assertEquals("APROBADA",resultado.estado());assertEquals(borrador.documento(),c.getIdDocumentoActual());
        assertEquals(agente.idRolOperativo(),historia.get(borrador.revisionAutorizacionBorrador()).getIdActor());
        assertEquals(agente.idRolOperativo(),historia.get(c.getIdRevisionAplicable()).getIdActor());
    }
    @Test void noSeDevuelveUnPactoOriginalAlRevocarseSuDocumentoFirmado() {
        var base=baseFirmada(broker);UUID ref=base.peticion().firma().archivos().getFirst().referencia();
        doThrow(new AccesoNoAutorizadoException()).when(a04).resolver(ref.toString(),broker);
        assertThrows(AccesoNoAutorizadoException.class,()->pactos.consultarPactoFirmado(base.pacto(),broker));
        assertEquals(1,serie.size());
    }
    @Test void laPrimeraDecisionEfectoraDeAdendaTambienExigeElDispatchTransaccional() throws Exception {
        var m=EncargoServiceImpl.class.getMethod("autorizarBorradorAdenda",ReferenciaAdenda.class,DecisionBorradorAdenda.class,Actor.class,ContextoSesion.class,String.class,Procedencia.class);
        assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,m.getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation());
        assertNull(EncargoServiceImpl.class.getMethod("proponerAdenda",long.class,DatosAdenda.class,Actor.class,ContextoSesion.class,String.class,Procedencia.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
    @Test void leerPactoDeOtroTenantDeniegaAntesDeBuscarLaRevisionPrivada() {
        var base=baseFirmada(broker);var ajeno=new Actor(2,11,23,Actor.BROKER);
        assertThrows(NoEncontradoException.class,()->pactos.consultarPactoFirmado(base.pacto(),ajeno));
        verify(revisiones,never()).findByOrganizacionIdAndId(2,base.pacto().revisionAprobatoria());
    }
    @Test void elPactoSigueLegibleTrasElFinSinConfundirLecturaConNuevaComercializacion() {
        var base=baseFirmada(broker);c.cerrar(HOY,"M","Fin documentado");c.transicionarA(Captacion.FINALIZADO);
        assertEquals(base.pacto(),pactos.consultarPactoFirmado(base.pacto(),broker).referencia());
        assertFalse(servicio.consultarVigencia(9,HOY,broker).habilitado());
    }
    @Test void borrarLaConstanciaHistoricaNoConvierteLaAprobacionEnUnPactoAcreditado() {
        var base=baseFirmada(broker);var r=historia.get(base.pacto().revisionAprobatoria());var s=json.comoMapa(r.getSnapshot());s.remove("revisionPresentacionFirma");
        var legado=RevisionEncargo.registrar(1,9,r.getIdPeticion(),null,r.getVersionEncargo(),r.getIdActor(),"APROBAR",null,json.objetoCanonico(s),r.getHuellaCobertura(),r.getRegistradaEn());
        id(legado,r.getId());historia.put(legado.getId(),legado);
        assertThrows(ReglaNegocioException.class,()->pactos.consultarPactoFirmado(base.pacto(),broker));assertEquals(1,serie.size());
    }
    @Test void elInstrumentoFuturoPuedeGuardarseYPresentarsePeroNoConmutaHoyPorInferencia() {
        var base=baseFirmada(broker);var propuesta=proponerCambio(base,"2800","2");var original=datosAdendaParaPrueba(propuesta);
        var futura=servicio.proponerAdenda(9,new DatosAdenda(base.pacto(),c.getVersion(),propuesta.propuesta(),original.motivo(),original.alcance(),HOY.plusDays(5),null,propuesta.referencia()),
                agente,prueba(agente),clave(),origen());
        var pendiente=presentarCandidata(autorizar(futura,broker));
        assertThrows(ReglaNegocioException.class,()->aprobar(pendiente,clave()));
        assertEquals(base.peticion().documento(),c.getIdDocumentoActual());assertEquals(1,c.getVersionContenido());assertEquals(1,serie.size());
    }
    @Test void camposOpcionalesNulosNoCambianLaHuellaMaterialAnterior() {
        var d=pacto();Map<String,Object> anterior=new LinkedHashMap<>();
        anterior.put("precioPedido",d.precioPedido());anterior.put("monedaPrecio",d.monedaPrecio());anterior.put("periodoPrecio",d.periodoPrecio());
        anterior.put("tipoComision",d.tipoComision());anterior.put("baseCalculo",d.baseCalculo());anterior.put("valorComision",d.valorComision());
        anterior.put("monedaComision",d.monedaComision());anterior.put("tratamientoIgv",d.tratamientoIgv());anterior.put("motivoSinComision",d.motivoSinComision());
        anterior.put("hitoDevengo",d.hitoDevengo());anterior.put("parteObligada",d.parteObligada());anterior.put("beneficiario",d.beneficiario());
        anterior.put("exigibilidad",d.exigibilidad());anterior.put("procedencia",d.procedencia());anterior.put("tramos",d.tramos());anterior.put("composicionMensual",null);
        String huellaAnterior=ReflectionTestUtils.invokeMethod(servicio,"huella",Map.of("condiciones",anterior));
        String huellaActual=ReflectionTestUtils.invokeMethod(servicio,"huella",Map.of("condiciones",d));
        assertEquals(huellaAnterior,huellaActual);
    }
    @Test void condicionesEstructuradasSeMuestranAntesDeFirmarConRotulosHumanos() {
        var d=pacto();var e=new PactosDeEncargo.EjecucionPacto(new PactosDeEncargo.CondicionDevengo(TipoHitoContractual.CONTRATO_ALQUILER),
                new PactosDeEncargo.CondicionExigibilidad(null,HOY.plusDays(5),null,null,null));
        c.setCondicionEconomica(new CondicionesDeEncargo(mapper).nueva(1,OperacionInmobiliaria.ALQUILER,new Condiciones(d.precioPedido(),d.monedaPrecio(),d.periodoPrecio(),d.tipoComision(),d.baseCalculo(),
                d.valorComision(),d.monedaComision(),d.tratamientoIgv(),d.motivoSinComision(),d.hitoDevengo(),d.parteObligada(),d.beneficiario(),d.exigibilidad(),d.procedencia(),d.tramos(),null,e)));
        preparada();var captor=org.mockito.ArgumentCaptor.forClass(DocumentosEncargo.Contenido.class);verify(binarios).generar(argThat(o->o.actor().idOrganizacion()==1L),captor.capture());
        var campos=captor.getValue().campos();assertTrue(campos.stream().anyMatch(x->"Pacto · condición de devengo".equals(x.rotulo())&&"Contrato de alquiler".equals(x.valor())));
        assertTrue(campos.stream().anyMatch(x->"Pacto · fecha expresa de exigibilidad".equals(x.rotulo())&&HOY.plusDays(5).toString().equals(x.valor())));
        assertFalse(campos.stream().anyMatch(x->"CONTRATO_ALQUILER".equals(x.valor())));
    }
    @Test void elCambioExternoDeUnaListaNoCambiaElBorradorYaCapturado() {
        var p=datosPreparacion();var partes=new ArrayList<>(p.partes());
        var copia=new Preparacion(p.encargo(),p.versionEncargo(),p.idPropiedad(),p.fisicos(),partes,p.operacion(),p.unidades(),p.condiciones(),p.desde(),p.hasta(),p.exclusividad(),p.plantillaVersion(),p.condicionesDinamicas());
        partes.clear();assertEquals(1,copia.partes().size());assertThrows(UnsupportedOperationException.class,()->copia.partes().clear());
    }
    @Test void unaVigenciaReutilizaLecturaFisicaYComercialSinCambiarCoberturaFirmada() {
        var base=baseFirmada(broker);String instrumento=documentos.get(base.peticion().documento()).getContenido();
        var antecedentes=historia.values().stream().collect(java.util.stream.Collectors.toMap(RevisionEncargo::getId,RevisionEncargo::getSnapshot));
        clearInvocations(fisica,lector,binarios);
        assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());
        verify(fisica,times(1)).datos(p);verify(lector,times(1)).deEncargo(9);verifyNoInteractions(binarios);
        assertEquals(instrumento,documentos.get(base.peticion().documento()).getContenido());
        assertEquals(antecedentes,historia.values().stream().collect(java.util.stream.Collectors.toMap(RevisionEncargo::getId,RevisionEncargo::getSnapshot)));
    }
    @Test void otraInvocacionDeVigenciaVeElFaltanteFisicoSinReutilizarLaLecturaAnterior() {
        var base=baseFirmada(broker);String instrumento=documentos.get(base.peticion().documento()).getContenido();
        clearInvocations(fisica,lector);
        assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());datos.remove("piso");
        var area=datos.get("metraje_total");datos.put("metraje_total",new Dato(new BigDecimal("91"),area.estado(),area.evidenciaRef(),area.concepto(),area.referenciaCatalogo()));
        var nueva=servicio.consultarVigencia(9,HOY,agente);
        assertFalse(nueva.habilitado());assertTrue(nueva.faltantes().contains("piso"));
        assertTrue(nueva.faltantes().contains("VERSION_REVISADA_CAMBIO"));
        verify(fisica,times(2)).datos(p);verify(lector,times(2)).deEncargo(9);
        assertEquals(Captacion.APROBADO,c.estadoActual());assertEquals(instrumento,documentos.get(base.peticion().documento()).getContenido());
    }
    @Test void otraInvocacionDeVigenciaVeCondicionesComercialesNuevasSinHeredarLaCobertura() {
        var base=baseFirmada(broker);String instrumento=documentos.get(base.peticion().documento()).getContenido();
        clearInvocations(fisica,lector);
        assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());
        var nuevas=ValoresGobernados.constructor().con("garantia_meses",ValorLogico.deNumero(new BigDecimal("2"))).construir();
        when(lector.deEncargo(9)).thenReturn(nuevas);
        var actual=servicio.consultarVigencia(9,HOY,agente);
        assertFalse(actual.habilitado());assertTrue(actual.faltantes().contains("VERSION_REVISADA_CAMBIO"));
        verify(fisica,times(2)).datos(p);verify(lector,times(2)).deEncargo(9);
        assertEquals(instrumento,documentos.get(base.peticion().documento()).getContenido());
    }
    @Test void adendaVigenteReutilizaLecturaLocalYDetectaUnaAlteracionPosterior() {
        var base=baseFirmada(broker);var candidata=autorizar(proponerCambio(base,"2800","2"),broker);
        var activa=aprobar(presentarCandidata(candidata),clave());
        String instrumento=documentos.get(activa.documento()).getContenido();long revision=c.getIdRevisionAplicable();
        clearInvocations(fisica,lector,binarios);
        assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());
        verify(fisica,times(1)).datos(p);verify(lector,times(1)).deEncargo(9);
        var area=datos.get("metraje_total");datos.put("metraje_total",new Dato(new BigDecimal("95"),area.estado(),area.evidenciaRef(),area.concepto(),area.referenciaCatalogo()));
        var alterada=servicio.consultarVigencia(9,HOY,agente);
        assertFalse(alterada.habilitado());assertTrue(alterada.faltantes().contains("VERSION_REVISADA_CAMBIO"));
        verify(fisica,times(2)).datos(p);verify(lector,times(2)).deEncargo(9);verifyNoInteractions(binarios);
        assertEquals(revision,c.getIdRevisionAplicable().longValue());assertEquals(instrumento,documentos.get(activa.documento()).getContenido());
        assertEquals(BigDecimal.ONE,pactos.consultarPactoFirmado(base.pacto(),broker).regla().valorComision());
    }
    @Test void composicionConservaConsultaDeSusUnidadesSinTratarLaSecundariaComoPrimaria() {
        prepararUnidadesAutorizadas();
        when(autoridadFisica.alcanceDeLectura(broker)).thenReturn(new Alcances.Alcance(1,false,List.of(30L)));
        servicio.declararComposicion(9,new DatosComposicion(List.of(new Unidad(7,null,null,null),new Unidad(8,null,null,null)),
                new BigDecimal("2500"),"PEN","Composición TEST explícita",c.getVersion()),agente,prueba(agente),clave(),origen());
        baseFirmada(broker);clearInvocations(fisica,lector,propiedades,binarios);
        assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());
        verify(fisica,times(1)).datos(p);verify(fisica,never()).datos(argThat(x->x!=null&&x.getId()==8L));
        verify(lector,times(1)).deEncargo(9);
        verify(propiedades,atLeastOnce()).buscarVariasParaLectura(eq(1L),argThat(ids->ids.contains(7L)&&ids.contains(8L)),anyString());
        when(propiedades.buscarVariasParaLectura(eq(1L),anyCollection(),anyString())).thenReturn(List.of(p));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarVigencia(9,HOY,agente));verifyNoInteractions(binarios);
    }
    @Test void lecturaLocalConservaCondicionRetiradaConRotuloUnidadEImporteEnSuMoneda() {
        var d=new CatalogoAtributo();d.setClave("precio_estacionamiento_adicional");d.setRotulo("Precio por cochera adicional");
        d.setTipoDato("IMPORTE");d.setUnidad("moneda");d.setSujeto(Sujeto.ENCARGO);d.setDestino("ATRIBUTO");d.setActivo(false);
        var valores=ValoresGobernados.constructor().con(d.getClave(),ValorLogico.deImporte(new BigDecimal("350.00"),"PEN")).construir();
        when(lector.deEncargo(9)).thenReturn(valores);
        when(atributos.definicionesParaLeer(eq(1L),any(Comercializacion.class),anyCollection())).thenReturn(Map.of(d.getClave(),d));
        var base=baseFirmada(broker);String instrumento=documentos.get(base.peticion().documento()).getContenido();
        var adicionales=(Map<?,?>)json.comoMapa(instrumento).get("condicionesAdicionales");var conservado=(Map<?,?>)adicionales.get(d.getClave());
        assertEquals("Precio por cochera adicional",conservado.get("rotulo"));assertEquals("IMPORTE",conservado.get("tipoDato"));assertEquals("moneda",conservado.get("unidad"));
        var importe=(Map<?,?>)conservado.get("valor");assertEquals("PEN",importe.get("moneda"));assertEquals(0,new BigDecimal("350.00").compareTo(new BigDecimal(importe.get("numero").toString())));
        clearInvocations(fisica,lector);assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());
        verify(fisica,times(1)).datos(p);verify(lector,times(1)).deEncargo(9);
        assertEquals(instrumento,documentos.get(base.peticion().documento()).getContenido());
    }
    @Test void lecturaDeVigenciaNoSeAplicaAOtraOrganizacionEncargoPropiedadOperacionComposicionOVersion() {
        Object leidos=ReflectionTestUtils.invokeMethod(servicio,"leerVigencia",c);
        List<java.util.function.Consumer<Captacion>> cambios=List.of(x->x.setOrganizacionId(2L),x->id(x,10),
                x->{var otra=new Propiedad();otra.setOrganizacionId(1L);id(otra,8);x.setPropiedad(otra);},
                x->x.setMotivoOperacion("V"),x->x.declararComposicion(999L),
                x->ReflectionTestUtils.setField(x,"versionContenido",c.getVersionContenido()+1));
        for(var cambiar:cambios){
            var otra=new Captacion();otra.setOrganizacionId(1L);id(otra,9);otra.setPropiedad(p);otra.setMotivoOperacion("A");
            ReflectionTestUtils.setField(otra,"versionContenido",c.getVersionContenido());cambiar.accept(otra);
            assertThrows(IllegalStateException.class,()->ReflectionTestUtils.invokeMethod(servicio,"faltantes",otra,List.of(),agente,leidos));
        }
    }
    @Test void lecturaAnteriorNoPermiteReutilizarUnaFirmaCuyoAccesoFueRevocado() {
        var base=baseFirmada(broker);assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());
        UUID ref=base.peticion().firma().archivos().getFirst().referencia();
        doThrow(new AccesoNoAutorizadoException()).when(a04).resolver(ref.toString(),agente);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarVigencia(9,HOY,agente));
        assertEquals(Captacion.APROBADO,c.estadoActual());assertEquals(base.peticion().documento(),c.getIdDocumentoActual());
    }

    @Test void unFaltanteDeSuficienciaNoSeConfundeConUnCambioDelContenidoContractualFirmado() {
        var base=baseFirmada(broker);String instrumento=documentos.get(base.peticion().documento()).getContenido();
        clearInvocations(fisica,lector);assertTrue(servicio.consultarVigencia(9,HOY,agente).habilitado());
        datos.remove("piso");var actual=servicio.consultarVigencia(9,HOY,agente);
        assertFalse(actual.habilitado());assertEquals(Set.of("piso"),new HashSet<>(actual.faltantes()));
        verify(fisica,times(2)).datos(p);verify(lector,times(2)).deEncargo(9);
        assertEquals(Captacion.APROBADO,c.estadoActual());assertEquals(instrumento,documentos.get(base.peticion().documento()).getContenido());
    }

    private DatosAdenda datosAdendaParaPrueba(Adenda a) {
        return mapper.convertValue(json.comoMapa(historia.get(a.referencia().propuesta()).getSnapshot()).get("datosAdenda"),DatosAdenda.class);
    }
    private record BaseFirmada(Preparada preparada,Peticion peticion,PactosDeEncargo.ReferenciaPacto pacto) { }
    private BaseFirmada baseFirmada(Actor decisor) {
        var preparada=preparada();var pet=presentar(preparada);
        var aprobada=servicio.decidir(pet.id(),new Decision("APROBAR",pet.version(),c.getVersion(),"Revisión competente expresa",revision(pet)),decisor,prueba(decisor),clave(),origen());
        return new BaseFirmada(preparada,aprobada,new PactosDeEncargo.ReferenciaPacto(9,c.getIdRevisionAplicable()));
    }
    private Adenda proponerCambio(BaseFirmada base,String precio,String valorComision) {
        var p=pacto();var nueva=new Condiciones(new BigDecimal(precio),p.monedaPrecio(),p.periodoPrecio(),p.tipoComision(),p.baseCalculo(),new BigDecimal(valorComision),
                p.monedaComision(),p.tratamientoIgv(),p.motivoSinComision(),p.hitoDevengo(),p.parteObligada(),p.beneficiario(),p.exigibilidad(),p.procedencia(),p.tramos(),null,null);
        var propuesta=new Preparacion(9L,c.getVersion(),7L,null,base.preparada().partes(),OperacionInmobiliaria.ALQUILER,null,nueva,HOY,HOY.plusMonths(6),false,null,null);
        return servicio.proponerAdenda(9,new DatosAdenda(base.pacto(),c.getVersion(),propuesta,"Cambio pactado del precio y comisión","Condiciones comerciales del Encargo",HOY,null,null),
                agente,prueba(agente),clave(),origen());
    }
    private Adenda autorizar(Adenda a,Actor decisor) {
        return servicio.autorizarBorradorAdenda(a.referencia(),new DecisionBorradorAdenda("APROBAR",a.ultimaRevision(),c.getVersion(),a.huellaPropuesta(),"Borrador revisado"),
                decisor,prueba(decisor),clave(),origen());
    }
    private Peticion presentarCandidata(Adenda a) {
        var firma=new FirmaManuscrita(new Firmante(80,90),HOY,evidencia(ArchivoEncargo.ACEPTACION,a.documento()).referencia(),"Adenda manuscrita de ensayo");
        return servicio.presentar(9,new Presentacion(a.documento(),c.getVersion(),a.propuesta().partes(),List.of(firma),null,a.referencia()),agente,prueba(agente),clave(),origen());
    }
    private CargaTemporal carga(){return servicio.cargarArchivo(new CargaArchivo(9L,null,ArchivoEncargo.TITULARIDAD,"original.txt","text/plain",new byte[]{1,2,3},c.getVersion()),agente,prueba(agente),clave(),origen());}
    private Peticion presentacionLegada(){
        var actual=presentar(preparada());var p=peticiones.get(actual.id());var snapshot=json.comoMapa(historia.get(p.getIdUltimaRevision()).getSnapshot());snapshot.remove("firmaManuscrita");
        var antigua=RevisionEncargo.registrar(1,9,p.getId(),null,c.getVersionContenido(),agente.idRolOperativo(),"PRESENTAR",null,
                json.objetoCanonico(snapshot),p.getHuellaCobertura(),HOY.atStartOfDay().atOffset(ZoneOffset.ofHours(-5)));
        id(antigua,ids.incrementAndGet());historia.put(antigua.getId(),antigua);p.registrarRevision(antigua.getId());
        return servicio.consultarPeticion(p.getId(),agente);
    }
    private VinculoArchivo vinculo(CargaTemporal carga){return new VinculoArchivo(carga.referencia(),9L,null,ArchivoEncargo.TITULARIDAD,c.getVersion());}
    private record Preparada(Documento documento,List<Parte> partes) { }
    private Preparada preparada() {
        UUID titulo=evidencia(ArchivoEncargo.TITULARIDAD,null).referencia();var partes=List.of(new Parte(80L,90L,new BigDecimal("100"),titulo,null,null,null,null,null,"María Propietaria",null));
        var d=servicio.prepararDocumento(new Preparacion(9L,0L,7L,null,partes,OperacionInmobiliaria.ALQUILER,null,null,null,null,null,null,null),agente,prueba(agente),clave(),origen());assertTrue(d.faltantes().isEmpty(),()->d.faltantes().toString());
        return new Preparada(d,partes);
    }
    private Preparacion datosPreparacion(){return new Preparacion(9L,0L,7L,null,List.of(new Parte(80L,90L,new BigDecimal("100"),UUID.randomUUID(),null,null,null,null,null,"María Propietaria",null)),OperacionInmobiliaria.ALQUILER,null,null,null,null,null,null,null);}
    private Presentacion presentacion(Preparada p){return new Presentacion(p.documento().id(),c.getVersion(),p.partes(),
            List.of(new FirmaManuscrita(new Firmante(80,90),HOY,evidencia(ArchivoEncargo.ACEPTACION,p.documento().id()).referencia(),"Instrumento manuscrito de ensayo")),"Sustento revisado",null);}
    private Peticion presentar(Preparada p){return servicio.presentar(9,presentacion(p),agente,prueba(agente),clave(),origen());}
    private RevisionManuscrita revision(Peticion p){return new RevisionManuscrita(p.firma().revisionPresentacion(),p.firma().huellaManifestacion(),true);}
    private Peticion aprobar(Peticion p,String clave){return servicio.decidir(p.id(),new Decision("APROBAR",p.version(),c.getVersion(),"Aprobación competente",revision(p)),broker,prueba(broker),clave,origen());}
    private ArchivoRegistrado evidencia(String finalidad,Long documento){
        boolean manuscrito=ArchivoEncargo.ACEPTACION.equals(finalidad);
        var carga=servicio.cargarArchivo(new CargaArchivo(9L,documento,finalidad,manuscrito?"instrumento-manuscrito.pdf":"sustento.txt",manuscrito?"application/pdf":"text/plain",
                (manuscrito?"%PDF-1.4\nInstrumento manuscrito representado por el puerto de ensayo\n%%EOF":"Sustento TEST").getBytes(java.nio.charset.StandardCharsets.UTF_8),c.getVersion()),agente,prueba(agente),clave(),origen());
        return servicio.adjuntarOReutilizar(new VinculoArchivo(carga.referencia(),9L,documento,finalidad,c.getVersion()),agente,prueba(agente),clave(),origen());
    }
    private List<ArchivoEncargo> evidencias(long org,Collection<UUID> refs){return refs.stream().map(ficheros::get).filter(Objects::nonNull).filter(a->a.getOrganizacionId()==org).toList();}
    private static Condiciones pacto(){return new Condiciones(new BigDecimal("2500"),"PEN","MENSUAL","E","R",BigDecimal.ONE,"PEN","I",null,"Firma del contrato","Propietario","Agencia","A la firma","Documento aportado por partes",List.of(),null,null);}
    private static String clave(){return "C2-"+UUID.randomUUID();}private static Procedencia origen(){return Procedencia.deCabecera("API");}
    private static void id(Object entidad,long id){ReflectionTestUtils.setField(entidad,"id",id);}
    private static String digest(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new AssertionError(e);}}
    private static ContextoSesion prueba(Actor a){return ContextoSesion.de(sesion(a));}
    private static ContextosService.SesionAccesoDTO sesion(Actor a){return new ContextosService.SesionAccesoDTO("sesion-"+a.idPersona(),1,
            new ContextosService.IdentidadAutenticada(100+a.idPersona(),"fixture","fixture@example.test","Fixture"),
            new ContextosService.Contexto("ctx-c2",a.idOrganizacion(),a.idPersona(),a.idRolOperativo(),a.rolEfectivo(),"EMPRESARIAL","Agencia de prueba"),
            Instant.parse("2026-10-28T00:00:00Z"),Instant.parse("2026-10-29T00:00:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);}
}
