package com.controllocal.service.materialia;

import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.persistence.materialia.*;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository.Operacion;
import com.controllocal.persistence.materialia.DerivadasMaterialIaRepository.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository.Intento;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.materialia.ProveedorMaterialIa.Salida;
import com.controllocal.service.materialia.DerivadasMaterialIaService.*;
import com.controllocal.service.materialia.ResultadosMaterialIa.*;
import com.controllocal.service.planes.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Unidad de selección/retención y guardas; los repos doblados NO acreditan SQL, dinero ni commit. */
class DerivadasMaterialIaServiceTest {
    final UUID duenoBinario=UUID.randomUUID();
    final RegistroMaterialIaRepository jobs=mock(RegistroMaterialIaRepository.class);
    final DerivadasMaterialIaRepository repo=mock(DerivadasMaterialIaRepository.class);
    final ContextoPresupuestoB0 contexto=mock(ContextoPresupuestoB0.class);
    final AutoridadDePropiedad propiedades=mock(AutoridadDePropiedad.class);
    final Propiedad inmueble=mock(Propiedad.class);
    final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    final Actor actor=new Actor(7,11,13,Actor.AGENTE),nuevoActor=new Actor(7,21,23,Actor.AGENTE);
    final ContextoSesion sid=new ContextoSesion("s".repeat(64),Instant.now().plusSeconds(300).getEpochSecond(),"SID-TEST",1);
    final UUID original=UUID.randomUUID();final String namespace="b".repeat(64);
    final AtomicReference<Instant> reloj=new AtomicReference<>(Instant.now().minusSeconds(2));
    final Snapshot f=new Snapshot("VERSION-TEST","a".repeat(64),Map.of(),"TEST",Map.of(),List.of(),new EvidenciaTratamiento(EstadoEvidencia.ACREDITADA,"TEST",List.of("FOTO_TEST"),"TEST","TEST","TEST","TEST","TEST","TEST"));
    final Salida r=new Salida(null,"image/png",new byte[]{1,2,3},"PROCESO_SINTETICO","1",null);
    final Control qc=new Control(Decision.APROBADA,"QC_SINTETICO",Instant.now().minusSeconds(1),Arrays.stream(Aspecto.values()).map(a->new Senal(a,EstadoSenal.EVALUADA,false,"TEST NO CALIDAD REAL","TEST")).toList());
    final Map<UUID,Operacion> operaciones=new HashMap<>();final Map<UUID,Vinculo> vinculos=new HashMap<>();final Map<UUID,Slot> slots=new HashMap<>();
    final Map<UUID,Original> selecciones=new HashMap<>();final Map<String,Gestion> gestiones=new HashMap<>();final Map<UUID,Retiro> retiros=new HashMap<>();
    final List<Evento> eventos=new ArrayList<>();final AtomicLong ids=new AtomicLong();
    Solicitud s;DerivadasMaterialIaService servicio;
    @BeforeEach void iniciar(){
        TransactionSynchronizationManager.setActualTransactionActive(true);s=solicitud(UUID.randomUUID());ponerJob(s,"CONTROLADA",qc,null);slots.put(s.operacion(),slot(s.operacion(),"GENERADA",false,false,reloj.get().minusSeconds(60)));
        selecciones.put(original,new Original(original,0,0,null,null));
        when(jobs.obtener(anyLong(),any())).thenAnswer(i->(long)i.getArgument(0)==7?Optional.ofNullable(operaciones.get(i.getArgument(1))):Optional.empty());
        when(jobs.custodia(eq(7L),any())).thenAnswer(i->{UUID job=i.getArgument(1);return operaciones.containsKey(job)
                ?Optional.of(new RegistroMaterialIaRepository.Custodia(7,job,UUID.nameUUIDFromBytes(("ARCHIVO_TEST:"+job).getBytes(java.nio.charset.StandardCharsets.UTF_8)),DerivadasMaterialIaService.operacionIo(7,job),1,"VERIFICADA",1)):Optional.empty();});
        when(repo.foto(eq(7L),any())).thenAnswer(i->Optional.of(new Foto(31,41,i.getArgument(1))));when(repo.fotoHistorica(eq(7L),eq(31L),any())).thenReturn(Optional.of(41L));
        when(propiedades.puedeLeer(any(),eq(31L))).thenReturn(true);when(propiedades.buscarParaLectura(any(),eq(31L))).thenReturn(Optional.of(inmueble));
        when(repo.poseeLock(anyLong())).thenReturn(true);when(repo.ahora()).thenAnswer(i->reloj.get());
        when(repo.obtener(eq(7L),any())).thenAnswer(i->Optional.ofNullable(vinculos.get(i.getArgument(1))));doAnswer(i->{Vinculo v=i.getArgument(0);vinculos.put(v.job(),v);return null;}).when(repo).insertar(any());
        when(repo.obtenerGestion(eq(7L),any())).thenAnswer(i->Optional.ofNullable(vinculos.get(i.getArgument(1))));
        when(repo.slot(eq(7L),any())).thenAnswer(i->Optional.ofNullable(slots.get(i.getArgument(1))));when(repo.slots(eq(7L),any())).thenAnswer(i->slots.values().stream().filter(x->x.original().equals(i.getArgument(1))).toList());
        when(repo.seleccion(eq(7L),any())).thenAnswer(i->Optional.ofNullable(selecciones.get(i.getArgument(1))));
        when(repo.seleccionada(eq(7L),any())).thenAnswer(i->selecciones.values().stream().anyMatch(x->Objects.equals(x.job(),i.getArgument(1))));
        doAnswer(i->{Foto foto=i.getArgument(1);selecciones.putIfAbsent(foto.original(),new Original(foto.original(),0,0,null,null));return null;}).when(repo).asegurarOriginal(anyLong(),any());
        when(repo.cambiarSeleccion(eq(7L),any(),anyLong(),nullable(UUID.class),nullable(UUID.class))).thenAnswer(i->{UUID o=i.getArgument(1);Original a=selecciones.get(o);if(a.revision()!=(long)i.getArgument(2))return 0;selecciones.put(o,new Original(o,a.revision()+1,a.revisionMaterial()+1,i.getArgument(3),i.getArgument(4)));return 1;});
        when(repo.actualizarSlot(any(),anyString(),anyBoolean(),nullable(Long.class),nullable(Instant.class),nullable(Instant.class),nullable(Instant.class),anyString())).thenAnswer(i->{Slot a=i.getArgument(0);Slot actual=slots.get(a.job());if(actual.revision()!=a.revision())return 0;slots.put(a.job(),new Slot(7,a.job(),a.original(),a.revision()+1,i.getArgument(1),i.getArgument(2),i.getArgument(3),i.getArgument(4),i.getArgument(5),i.getArgument(6),i.getArgument(7),a.protegida(),namespace));return 1;});
        doAnswer(i->{UUID j=i.getArgument(1);slots.put(j,new Slot(7,j,i.getArgument(2),0,"RESERVADA",false,null,null,null,null,"ADMISION",false,i.getArgument(3)));return null;}).when(repo).reservarSlot(anyLong(),any(),any(),anyString());
        when(repo.generacion(eq(7L),any())).thenAnswer(i->Optional.of(new Generacion(1,reloj.get().minusSeconds(60))));
        when(repo.intento(eq(7L),any(),eq(1))).thenAnswer(i->operaciones.values().stream().filter(j->DerivadasMaterialIaService.operacionIo(7,j.id()).equals(i.getArgument(1))).findFirst().map(this::intento));
        when(repo.gestion(eq(7L),anyString())).thenAnswer(i->Optional.ofNullable(gestiones.get(i.getArgument(1))));doAnswer(i->{gestiones.put(i.getArgument(1),new Gestion(i.getArgument(2),i.getArgument(3),i.getArgument(4),i.getArgument(5)));return null;}).when(repo).gestionar(anyLong(),anyString(),anyString(),anyString(),anyLong(),anyLong());
        when(repo.evento(anyLong(),any(),anyLong(),anyString(),anyString(),anyString())).thenAnswer(i->{Evento e=new Evento(ids.incrementAndGet(),i.getArgument(0),i.getArgument(1),i.getArgument(3),i.getArgument(4),i.getArgument(5),reloj.get());eventos.add(e);return e;});
        when(repo.ultimoEvento(eq(7L),any(),anyString())).thenAnswer(i->eventos.stream().filter(e->e.job().equals(i.getArgument(1))&&e.causa().equals(i.getArgument(2))).reduce((a,b)->b));
        when(repo.retiro(eq(7L),any())).thenAnswer(i->Optional.ofNullable(retiros.get(i.getArgument(1))));doAnswer(i->{Retiro v=i.getArgument(0);retiros.put(v.job(),v);return null;}).when(repo).iniciarRetiro(any());
        servicio=new DerivadasMaterialIaService(jobs,repo,contexto,propiedades,json);
    }
    @AfterEach void terminar(){TransactionSynchronizationManager.clear();}
    Solicitud solicitud(UUID job){return new Solicitud(job,Accion.MEJORAR_FOTO,new Recurso("PROPIEDAD","31"),"VERSION-TEST",original,"TEST");}
    void ponerJob(Solicitud solicitud,String estado,Control control,UUID archivo){String fuente=json.objetoCanonico(f);operaciones.put(solicitud.operacion(),new Operacion(7,solicitud.operacion(),11,13,Actor.AGENTE,sid.identidad(),Accion.MEJORAR_FOTO.name(),"p".repeat(64),json.objetoCanonico(solicitud),fuente,Idempotencia.huellaExacta(fuente),"PERFIL_TEST","1","c".repeat(64),estado,estado.equals("INTENCION")?null:"{}",estado.equals("INTENCION")?null:json.objetoCanonico(MaterialIaServiceImpl.detalle(r)),control==null?null:json.objetoCanonico(control),archivo==null?null:json.objetoCanonico(new MaterialIaServiceImpl.Entregado(null,archivo)),null,1));}
    Slot slot(UUID job,String estado,boolean guardada,boolean protegida,Instant desde){return new Slot(7,job,original,1,estado,guardada,1L,desde,desde,desde.plus(Duration.ofDays(7)),"TEST",protegida,namespace);}
    Intento intento(Operacion j){String h=EvaluacionTecnicaFoto.huella(r.foto());UUID io=DerivadasMaterialIaService.operacionIo(7,j.id());return new Intento(1,7L,io,1,"MATERIAL-IA:7:"+j.id()+":"+j.huellaSnapshot()+":"+h,"tenant/7/"+io+".png","png","image/png",h,3,"ESCRITO_VERIFICADO",false,reloj.get(),null,namespace,duenoBinario,null);}
    ObjetoVerificado objeto(UUID job){var i=intento(operaciones.get(job));return new ObjetoVerificado(1,i.almacenHuella(),i.clave(),i.huella(),i.bytes());}
    UUID disponible(){var g=servicio.guardar(s,f,r,qc,actor,sid,p->objeto(s.operacion()));ponerJob(s,"DISPONIBLE",qc,g.archivo());return g.archivo();}
    @ParameterizedTest @ValueSource(strings={"AUSENTE","PREPARADA","OTRO_IO","OTRO_NUMERO"})
    void noPromueveSinLaMismaRecepcionVerificada(String caso){
        var c=jobs.custodia(7,s.operacion()).orElseThrow();
        when(jobs.custodia(7,s.operacion())).thenReturn(caso.equals("AUSENTE")?Optional.empty():Optional.of(new RegistroMaterialIaRepository.Custodia(7,s.operacion(),c.archivo(),
                caso.equals("OTRO_IO")?UUID.randomUUID():c.operacionIo(),caso.equals("OTRO_NUMERO")?2:1,caso.equals("PREPARADA")?"PREPARADA":"VERIFICADA",1)));
        assertThrows(ConflictoException.class,()->servicio.guardar(s,f,r,qc,actor,sid,p->objeto(s.operacion())));verify(repo,never()).insertar(any());
    }
    @Test void archivoControladoSeVinculaUnaVezYClasificaDerivadaIaComputable(){
        var plan=new AtomicReference<PlanSalida>();var g=servicio.guardar(s,f,r,qc,actor,sid,p->{plan.set(p);return objeto(s.operacion());});
        var c=servicio.clasificar(new UsoAlmacenamientoB0.Solicitud(UUID.randomUUID(),plan.get().recursoCuota(),3,EvaluacionTecnicaFoto.huella(r.foto()),null),actor);
        assertEquals(CategoriaAlmacenamiento.DERIVADA_IA,c.categoria());assertTrue(c.categoria().computa());assertEquals(original,vinculos.get(s.operacion()).original());
        ponerJob(s,"DISPONIBLE",qc,g.archivo());assertEquals(g.archivo(),servicio.autorizar(s.operacion(),g.archivo(),actor,sid).archivo());verify(repo).insertar(any());
    }
    @ParameterizedTest @ValueSource(strings={"CONTROL","SALIDA","FUENTE"})
    void noConservaContenidoNiControlDeOtraFuente(String caso){
        var fuente=caso.equals("FUENTE")?new Snapshot("OTRA",f.huellaOrigen(),f.datosPermitidos(),f.instruccionPermitida(),f.utilidadLocal(),f.pendientesLocales(),f.evidencia()):f;
        var salida=caso.equals("SALIDA")?new Salida(null,"image/png",new byte[]{9},r.proceso(),r.versionProceso(),null):r;
        var control=caso.equals("CONTROL")?new Control(Decision.OBSERVADA,qc.version(),qc.evaluadaEn(),qc.senales()):qc;
        assertThrows(ConflictoException.class,()->servicio.guardar(s,fuente,salida,control,actor,sid,p->{fail("No I/O incompatible");return objeto(s.operacion());}));verify(repo,never()).insertar(any());
    }
    @ParameterizedTest @ValueSource(strings={"HASH","NAMESPACE","BYTES"})
    void callbackNoSustituyeObjetoVerificado(String caso){var o=objeto(s.operacion());assertThrows(ConflictoException.class,()->servicio.guardar(s,f,r,qc,actor,sid,p->new ObjetoVerificado(1,caso.equals("NAMESPACE")?"d".repeat(64):o.namespaceHash(),o.clavePrivada(),caso.equals("HASH")?"e".repeat(64):o.huella(),caso.equals("BYTES")?4:3)));}
    @Test void controlIncompletoNuncaLlegaAlEscritor(){ponerJob(s,"CONTROLADA",new Control(Decision.APROBADA,"TEST",reloj.get(),List.of()),null);assertThrows(IllegalStateException.class,()->servicio.guardar(s,f,r,qc,actor,sid,p->{fail("QC obligatorio");return objeto(s.operacion());}));}
    @Test void metadataHistoricaNoExigeBinarioDisponiblePeroDescargaSi(){
        UUID archivo=disponible();Slot a=slots.get(s.operacion());slots.put(s.operacion(),slot(s.operacion(),"RETIRADA",false,false,a.temporalDesde()));
        assertEquals(archivo,servicio.autorizarArchivo(s.operacion(),archivo,actor).archivo());assertThrows(NoEncontradoException.class,()->servicio.autorizar(s.operacion(),archivo,actor,sid));
    }
    @Test void artefactoDelRecursoNoHeredaJobPrivadoYGuardarNuevoSigueAutorEstricto(){
        UUID archivo=disponible();assertEquals(archivo,servicio.autorizarArchivo(s.operacion(),archivo,nuevoActor).archivo());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.guardar(s,f,r,qc,nuevoActor,sid,p->objeto(s.operacion())));
    }
    @Test void originalRevocadoNoEntregaNiMetadata(){UUID archivo=disponible();when(repo.foto(7,original)).thenReturn(Optional.empty());assertThrows(NoEncontradoException.class,()->servicio.autorizarArchivo(s.operacion(),archivo,actor));}
    @Test void generacionProvieneDePrimeraMedidaYConsultaNoRenueva(){
        slots.put(s.operacion(),new Slot(7,s.operacion(),original,0,"RESERVADA",false,null,null,null,null,"ADMISION",false,namespace));
        servicio.registrarGeneracion(7,s.operacion());var a=slots.get(s.operacion());assertEquals(reloj.get().minusSeconds(60),a.generadaEn());assertEquals(a.generadaEn().plus(Duration.ofDays(7)),a.venceEn());
        servicio.registrarGeneracion(7,s.operacion());assertEquals(a,slots.get(s.operacion()));assertEquals("GENERACION_OBSERVADA_CORE",eventos.getFirst().causa());
    }
    @Test void originalRevisionCeroNoFabricaDecisionHumana(){
        var a=servicio.seleccionActual(original,actor,sid);assertEquals(0,a.revision());assertNull(a.operacion());assertNull(a.archivo());verify(repo,never()).asegurarOriginal(anyLong(),any());assertTrue(gestiones.isEmpty());
        var b=servicio.seleccionar(original,null,null,0,"seguir-original",actor,sid,l->fail("Original no necesita bytes derivados"));assertEquals(a,b);
        verify(repo,never()).eventoSeleccion(anyLong(),any(),anyLong(),any(),any(),any(),any(),anyLong(),anyLong(),anyString(),anyString(),anyLong(),any());
    }
    @Test void seleccionUnicaRevisableNoGuardaPorElegirYReinicioSoloAlDejarUso(){
        UUID archivo=disponible();var a=servicio.seleccionar(original,s.operacion(),archivo,0,"elegir",actor,sid,l->{});Instant generada=slots.get(s.operacion()).generadaEn();
        assertEquals(1,a.revision());assertFalse(servicio.version(s.operacion(),actor).guardada());assertNull(servicio.version(s.operacion(),actor).venceEn());
        reloj.set(reloj.get().plus(Duration.ofDays(3)));var b=servicio.seleccionar(original,null,null,1,"no-usar",actor,sid,l->fail("No usar no lee bytes"));
        assertEquals(2,b.revision());assertNull(b.operacion());var v=servicio.version(s.operacion(),actor);assertEquals(generada,v.generadoEn());assertEquals(reloj.get(),v.temporalDesde());assertEquals(reloj.get().plus(Duration.ofDays(7)),v.venceEn());
        reloj.set(reloj.get().plusSeconds(50));assertEquals(v.venceEn(),servicio.version(s.operacion(),actor).venceEn());
        assertEquals(a,servicio.seleccionar(original,s.operacion(),archivo,0,"elegir",actor,sid,l->fail("Replay no reelige")));assertEquals(2,servicio.seleccionActual(original,actor,sid).revision());
    }
    @Test void casYClaveNoReinterpretanOtraSeleccion(){
        UUID archivo=disponible();servicio.seleccionar(original,s.operacion(),archivo,0,"misma",actor,sid,l->{});
        assertThrows(ConflictoException.class,()->servicio.seleccionar(original,null,null,1,"misma",actor,sid,l->{}));
        assertThrows(ConflictoException.class,()->servicio.seleccionar(original,null,null,0,"otra",actor,sid,l->{}));assertEquals(1,selecciones.get(original).revision());
    }
    @ParameterizedTest @ValueSource(strings={"RESERVADA","RETIRADA_PENDIENTE"})
    void slotsReservadosYRetiradasNoSeLiberanPorCaducar(String estado){
        UUID otro=UUID.randomUUID();slots.put(otro,slot(otro,estado,false,false,reloj.get().minus(Duration.ofDays(10))));
        var nueva=solicitud(UUID.randomUUID());ponerJob(nueva,"INTENCION",null,null);
        assertThrows(ConflictoException.class,()->servicio.previsionar(nueva,f,actor,sid,namespace));verify(repo,never()).reservarSlot(anyLong(),eq(nueva.operacion()),any(),anyString());
    }
    @Test void desbordeAlVolverOriginalNoAplicaSeleccionParcialNiBorraVersiones(){
        UUID archivo=disponible();servicio.seleccionar(original,s.operacion(),archivo,0,"usar",actor,sid,l->{});
        for(int i=0;i<2;i++){UUID j=UUID.randomUUID();slots.put(j,slot(j,"GENERADA",false,false,reloj.get()));}
        assertThrows(ConflictoException.class,()->servicio.seleccionar(original,null,null,1,"volver-original",actor,sid,l->{}));assertEquals(s.operacion(),selecciones.get(original).job());assertEquals(1,selecciones.get(original).revision());
        assertEquals(3,slots.size());verify(repo,never()).iniciarRetiro(any());
    }
    @Test void guardarAlternativaTieneCasPropioYNoCambiaSeleccionNiFechaGeneracion(){
        UUID archivo=disponible();Version antes=servicio.version(s.operacion(),actor);var v=servicio.guardarAlternativa(s.operacion(),archivo,antes.revision(),"guardar",actor,sid,l->{});
        assertTrue(v.guardada());assertEquals(antes.revision()+1,v.revision());assertEquals(antes.generadoEn(),v.generadoEn());assertNull(v.venceEn());assertEquals(0,servicio.seleccionActual(original,actor,sid).revision());
        assertEquals(v,servicio.guardarAlternativa(s.operacion(),archivo,antes.revision(),"guardar",actor,sid,l->fail("Replay sin I/O")));
    }
    @Test void vencimientoExactoDeniegaElegirYNoCobraNiRenueva(){
        UUID archivo=disponible();reloj.set(slots.get(s.operacion()).venceEn());assertFalse(servicio.version(s.operacion(),actor).disponible());assertEquals("VENCIDA",servicio.version(s.operacion(),actor).estadoGestion());
        assertThrows(NoEncontradoException.class,()->servicio.seleccionar(original,s.operacion(),archivo,0,"tarde",actor,sid,l->{}));assertEquals(0,servicio.seleccionActual(original,actor,sid).revision());
    }
    @ParameterizedTest @ValueSource(strings={"SELECCIONADA","GUARDADA","PROTEGIDA"})
    void descartarTemporalNoRetiraUsosProtegidos(String caso){
        UUID archivo=disponible();Slot a=slots.get(s.operacion());if(caso.equals("SELECCIONADA"))servicio.seleccionar(original,s.operacion(),archivo,0,"usar",actor,sid,l->{});
        else slots.put(s.operacion(),slot(s.operacion(),"DISPONIBLE",caso.equals("GUARDADA"),caso.equals("PROTEGIDA"),a.temporalDesde()));
        long revision=slots.get(s.operacion()).revision();assertThrows(ConflictoException.class,()->servicio.descartarTemporal(s.operacion(),archivo,revision,"retirar",actor,sid));assertTrue(retiros.isEmpty());
    }
    @Test void retirarAlternativaExpresaConservaFenceYNoAnunciaCuotaLiberada(){
        UUID archivo=disponible();Slot a=slots.get(s.operacion());slots.put(s.operacion(),slot(s.operacion(),"DISPONIBLE",true,false,a.temporalDesde()));
        var plan=servicio.retirarAlternativa(s.operacion(),archivo,slots.get(s.operacion()).revision(),"retirar-alternativa",actor,sid);
        assertEquals("RETIRADA_ALTERNATIVA",plan.causa());assertEquals("INTENCION",plan.estado());var v=servicio.version(s.operacion(),actor);assertEquals("RETIRADA_PENDIENTE",v.estadoGestion());assertFalse(v.disponible());assertTrue(v.guardada());
        assertThrows(NoEncontradoException.class,()->servicio.autorizar(s.operacion(),archivo,actor,sid));assertEquals(archivo,servicio.autorizarArchivo(s.operacion(),archivo,actor).archivo());
    }
    @Test void negativaDeGestionActualNoIniciaRetirada(){UUID archivo=disponible();when(propiedades.buscarParaLectura(actor,31)).thenReturn(Optional.empty());assertThrows(NoEncontradoException.class,()->servicio.descartarTemporal(s.operacion(),archivo,slots.get(s.operacion()).revision(),"sin-permiso",actor,sid));assertTrue(retiros.isEmpty());}
    @Test void mandatoWriteYLocksRealesSeExigenParaNuevaComposicion(){
        TransactionSynchronizationManager.setActualTransactionActive(false);assertThrows(IllegalStateException.class,()->servicio.bloquearSelecciones(List.of(original),actor));
        TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);assertThrows(IllegalStateException.class,()->servicio.bloquearSelecciones(List.of(original),actor));
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);when(repo.poseeLock(anyLong())).thenReturn(false);assertThrows(IllegalStateException.class,()->servicio.exigirMismasSelecciones(List.of(new Seleccion(original,0,null,null)),actor));
    }
    @Test void consultaOriginalNoTomaLockYConjuntoWriteBloqueaOriginalAntesJob(){
        UUID archivo=disponible();servicio.seleccionar(original,s.operacion(),archivo,0,"usar",actor,sid,l->{});clearInvocations(jobs);
        assertEquals(1,servicio.consultarSelecciones(List.of(original),actor).getFirst().revision());verify(jobs,never()).bloquear(anyLong());
        servicio.bloquearSelecciones(List.of(original),actor);var orden=inOrder(jobs);orden.verify(jobs).bloquear(llave("BROX-MATERIAL-IA-ORIGINAL",original));orden.verify(jobs).bloquear(llave("BROX-MATERIAL-IA-LOCK",s.operacion()));
    }
    @Test void terminalSinWriterLiberaSoloMediantePruebaDurableYNoPideUsuario(){
        when(jobs.custodia(7,s.operacion())).thenReturn(Optional.empty());
        ponerJob(s,"RECHAZADA",new Control(Decision.RECHAZADA,qc.version(),qc.evaluadaEn(),qc.senales()),null);var prueba=new AtomicReference<UsoAlmacenamientoB0.NoAlmacenadoComprobado>();
        servicio.resolverSinArchivo(7,s.operacion(),prueba::set);assertEquals("NO_ALMACENADA",slots.get(s.operacion()).estado());assertEquals(s.operacion(),prueba.get().job());assertTrue(prueba.get().referenciaEvidencia().startsWith("MATERIAL-IA-NO-ALMACENADO:"));verifyNoInteractions(contexto);
    }
    @Test void falloResultadoReevaluableNoFabricaNoAlmacenamiento(){ponerJob(s,"FALLO_RESULTADO",null,null);servicio.resolverSinArchivo(7,s.operacion(),p->fail("No prueba terminal"));assertEquals("GENERADA",slots.get(s.operacion()).estado());assertTrue(eventos.isEmpty());verifyNoInteractions(contexto);}
    @Test void avisoAgrupaEpisodioSinRenovarYUnaCausaDesconocidaNoSeDeclaraFinalizada(){
        disponible();when(repo.originalesConSlots(7)).thenReturn(List.of(original));
        reloj.set(slots.get(s.operacion()).venceEn().minusSeconds(3600));
        var grupo=servicio.grupos(actor,v->true).getFirst();assertEquals(original,grupo.original());assertEquals(1,grupo.versiones().size());
        assertEquals(grupo.vencePrimeroEn().minus(Duration.ofHours(24)),grupo.avisoEn());
        assertTrue(servicio.grupos(actor,v->false).isEmpty());
        assertEquals(EstadoCausaCaducidad.NO_EVALUABLE,servicio.estadoCausaCaducidad(7,original,grupo.huellaCausa()));
        assertEquals(grupo.vencePrimeroEn(),slots.get(s.operacion()).venceEn());
    }
    @Test void avisoCapturaDenegacionPropiaAntesDeInvocarPredicadoExterno(){
        disponible();when(repo.originalesConSlots(7)).thenReturn(List.of(original));reloj.set(slots.get(s.operacion()).venceEn().minusSeconds(3600));
        when(propiedades.buscarParaLectura(actor,31)).thenReturn(Optional.of(inmueble),Optional.empty());
        assertTrue(servicio.grupos(actor,l->{fail("La denegación propia no atraviesa otra frontera transaccional");return true;}).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"TECNICO","AUTORIZACION_INESPERADA"})
    void avisoNoConvierteErrorDelPredicadoTipadoEnGrupoVacio(String caso){
        disponible();when(repo.originalesConSlots(7)).thenReturn(List.of(original));reloj.set(slots.get(s.operacion()).venceEn().minusSeconds(3600));
        RuntimeException fallo=caso.equals("TECNICO")?new IllegalStateException("TEST fuente técnica falló"):new AccesoNoAutorizadoException();
        assertSame(fallo,assertThrows(RuntimeException.class,()->servicio.grupos(actor,l->{throw fallo;})));
    }
    @Test void clasificadorNoLeeJobDeOtraOrganizacionAntesDeNegar(){
        var recurso=new UsoAlmacenamientoB0.Recurso(DerivadasMaterialIaService.TIPO,"8:"+s.operacion(),DerivadasMaterialIaService.FINALIDAD,"a".repeat(64));
        clearInvocations(jobs);assertThrows(AccesoNoAutorizadoException.class,()->servicio.clasificar(new UsoAlmacenamientoB0.Solicitud(UUID.randomUUID(),recurso,3,"a".repeat(64),null),actor));
        verify(jobs,never()).obtener(anyLong(),any());
    }
    record EpisodioTest(UUID operacion,long revision,Instant temporalDesde,Instant venceEn) { }
    @ParameterizedTest @ValueSource(strings={"GUARDADA","PROTEGIDA","RETIRADA","NUEVA_VIGENTE"})
    void trazaHistoricaConMiembroYaRetiradoNoFabricaHechoNuevoActual(String caso){
        disponible();UUID otro=UUID.randomUUID();var b=solicitud(otro);ponerJob(b,"CONTROLADA",qc,null);slots.put(otro,slot(otro,"GENERADA",false,false,reloj.get().minusSeconds(60)));
        var gb=servicio.guardar(b,f,r,qc,actor,sid,p->objeto(otro));ponerJob(b,"DISPONIBLE",qc,gb.archivo());
        var miembros=new ArrayList<MiembroCausa>();var episodios=new ArrayList<EpisodioTest>();
        for(UUID job:List.of(s.operacion(),otro)){
            var slot=slots.get(job);var evento=eventos.stream().filter(e->e.job().equals(job)).reduce((a,c)->c).orElseThrow();
            when(repo.evento(7,evento.id())).thenReturn(Optional.of(evento));
            miembros.add(new MiembroCausa(job,slot.revision(),evento.id(),slot.temporalDesde(),slot.venceEn()));episodios.add(new EpisodioTest(job,slot.revision(),slot.temporalDesde(),slot.venceEn()));
        }
        String a=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("miembros",List.of(episodios.getFirst()))));
        String ab=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("miembros",episodios.stream().sorted(Comparator.comparing(e->e.operacion().toString())).toList())));
        when(repo.causa(7,original,a)).thenReturn(List.of(miembros.getFirst()));when(repo.causa(7,original,ab)).thenReturn(List.copyOf(miembros));
        var viejo=slots.get(otro);slots.put(otro,new Slot(7,otro,original,viejo.revision()+1,caso.equals("RETIRADA")?"RETIRADA":"DISPONIBLE",caso.equals("GUARDADA"),viejo.eventoGeneracion(),viejo.generadaEn(),viejo.temporalDesde(),viejo.venceEn(),"TEST",caso.equals("PROTEGIDA"),namespace));
        assertEquals(EstadoCausaCaducidad.VIGENTE,servicio.estadoCausaCaducidad(7,original,ab));
        assertEquals(caso.equals("NUEVA_VIGENTE")?EstadoCausaCaducidad.SUSTITUIDA:EstadoCausaCaducidad.VIGENTE,servicio.estadoCausaCaducidad(7,original,a,ab));
    }
    private long llave(String prefijo,UUID id){return Long.parseUnsignedLong(Idempotencia.huellaExacta(prefijo+"\u0000"+7+"\u0000"+id).substring(0,16),16);}
}
