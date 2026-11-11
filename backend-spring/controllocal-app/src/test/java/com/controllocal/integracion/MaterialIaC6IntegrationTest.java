package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.openai.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.ResultadoFacturable;
import com.controllocal.service.presupuesto.PresupuestoIaService.MedicionFacturable;
import com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaInterna;
import com.controllocal.service.presupuesto.PresupuestoIaService.ComprobacionNoProcesamiento;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.controllocal.web.almacen.*;
import com.controllocal.web.seguridad.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** PG/T4 del coordinador y B0 reales con procesador/QC SINTÉTICOS. No calibra ni acepta inferencia IA. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=2")
class MaterialIaC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired ApplicationContext contexto;
    @Autowired RegistroMaterialIaRepository registro;
    @Autowired PresupuestoIaRepository financiero;
    @Autowired Documentos json;
    @Autowired DataSource datos;
    @MockitoBean AlmacenDocumentos almacen;
    @MockitoBean AvisosTratamientoMaterialIa avisos;
    final Map<String,AlmacenDocumentos.ArchivoDescargado> objetos=new ConcurrentHashMap<>();
    AtomicInteger puts;Runnable alGuardar,alAbrir;String namespace;
    OrigenesDePrueba.Equipo equipo;ContextoSesion sesion;TransactionTemplate tx;
    MaterialIaServiceImpl servicio;PresupuestoIaService b0;AutoridadMaterialIa autoridad;PerfilesMaterialIa perfiles;
    UUID fondo,ciclo;String version;Instant ahora;boolean permiso,acreditada,fallaArchivo,fresca;int intentosPerfil;
    AtomicInteger llamadas,controlesEjecutados,archivos,fuentesPreparadas,lecturasArchivo;Decision decision;Function<Peticion,Salida> ejecucion;
    Function<Control,Control> controlRecibido;String idAdaptador;
    String instruccionPermitida;boolean transferenciaPermitida;Runnable antesDeTransferencia;
    AtomicInteger verificacionesTransferencia;Map<UUID,DestinoTratamiento> destinos;
    final FuentesMaterialIa fuente=new FuentesMaterialIa(){
        public Snapshot preparar(Solicitud s,Actor a,ContextoSesion sid){
            fuentesPreparadas.incrementAndGet();
            acceso(a);return new Snapshot("ORIGEN-TEST-1","a".repeat(64),Map.of("tipo","TERRENO","areaTotal","100"),
                    instruccionPermitida,Map.of("resultadoLocal","Preparación determinista TEST"),acreditada?List.of():List.of("TRATAMIENTO_IA_PENDIENTE"),
                    new EvidenciaTratamiento(acreditada?EstadoEvidencia.ACREDITADA:EstadoEvidencia.PENDIENTE,
                            "MATERIAL_INMOBILIARIO_TEST",List.of("DATOS_SINTETICOS"),"TEST-1","PROCESADOR_TEST_SIN_RED",
                            "ACEPTADA_TEST","PRUEBA_SINTETICA","SIN_TERCEROS_REALES_TEST","TEST-1"));
        }
        public void exigirTransferencia(Solicitud s,Snapshot f,DestinoTratamiento destino,Actor a,ContextoSesion sid){
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"E44 se comprueba sin TX padre");
            verificacionesTransferencia.incrementAndGet();destinos.put(s.operacion(),destino);antesDeTransferencia.run();revalidar(s,f,a);
            if(!transferenciaPermitida||!"PROCESADOR_TEST_SIN_RED".equals(destino.proveedor())
                    ||!"MATERIAL_IA_TEST".equals(destino.perfil())||!version.equals(destino.versionPerfil()))throw new CapacidadDeshabilitadaException();
        }
        public void revalidar(Solicitud s,Snapshot f,Actor a){acceso(a);
            if(!fresca)throw new ConflictoException("TEST otro dato físico cambió");
            if(!"ORIGEN-TEST-1".equals(f.versionOrigen()))throw new ConflictoException("Fuente TEST cambió");
            if(!acreditada)throw new CapacidadDeshabilitadaException();
        }
        public void exigirLecturaHistorica(Solicitud s,Snapshot f,Actor a){acceso(a);if(!acreditada)throw new CapacidadDeshabilitadaException();}
        public void exigirLecturaArtefacto(Solicitud s,Snapshot f,Actor a){exigirLecturaHistorica(s,f,a);}
        public EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto evaluarLecturaArtefacto(Solicitud s,Snapshot f,Actor a){
            try{exigirLecturaArtefacto(s,f,a);return EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.PERMITIDO;}
            catch(AccesoNoAutorizadoException|NoEncontradoException denegada){return EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.PERMISO_INSUFICIENTE;}
            catch(CapacidadDeshabilitadaException pendiente){return EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.CAPACIDAD_DESHABILITADA;}
        }
        public byte[] fotoParaProcesar(Solicitud s,Snapshot f,Actor a,ContextoSesion sid){revalidar(s,f,a);return new byte[]{1,2,3};}
        void acceso(Actor a){if(!permiso||equipo==null||!equipo.agente().equals(a))throw new AccesoNoAutorizadoException();}
    };
    final ProveedorMaterialIa proveedor=new ProveedorMaterialIa(){
        public String identificador(){return idAdaptador;}
        public boolean atiende(Accion a,String p,String v){return "MATERIAL_IA_TEST".equals(p)&&version.equals(v);}
        public Salida ejecutar(Peticion p,java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> observador){
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Procesador fuera de toda TX SQL");
            assertEquals(Set.of("tipo","areaTotal"),p.datosPermitidos().keySet(),"Sin prueba jurídica, contacto, expediente ni otro campo del job");
            UUID operacion=jdbc.queryForObject("SELECT id_operacion FROM material_ia_operacion WHERE clave_externa=?",UUID.class,p.claveExterna());
            assertEquals("EN_PROCESO",jdbc.queryForObject("SELECT estado FROM b0_operacion_ia WHERE organizacion_id=? AND id_operacion=?",String.class,equipo.agente().idOrganizacion(),operacion));
            assertEquals("RECLAMADA",jdbc.queryForObject("SELECT estado FROM material_ia_operacion WHERE organizacion_id=? AND id_operacion=?",String.class,equipo.agente().idOrganizacion(),operacion));
            var snapshot=json.comoObjeto(registro.obtener(equipo.agente().idOrganizacion(),operacion).orElseThrow().snapshot(),Snapshot.class);
            assertEquals(snapshot.instruccionPermitida(),p.instruccion(),"Sólo instrucción minimizada sellada, nunca fallback al texto libre");
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='DESTINO_VERIFICADO'",Integer.class,equipo.agente().idOrganizacion(),operacion),"La verificación está comprometida antes de I/O");
            llamadas.incrementAndGet();return ejecucion.apply(p);
        }
    };
    final ControlesMaterialIa control=new ControlesMaterialIa(){
        public boolean atiende(Accion a,String p,String v){return "MATERIAL_IA_TEST".equals(p)&&version.equals(v);}
        public Control evaluar(Solicitud s,Snapshot f,Salida r){controlesEjecutados.incrementAndGet();return controlRecibido.apply(controlSintetico(s));}
    };
    /** Sólo los casos del coordinador usan este doble: NO acredita slots, previsión145 ni almacenamiento. */
    final ResultadosMaterialIa almacenamiento=mock(ResultadosMaterialIa.class);
    @BeforeEach void preparar(TestInfo info){
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var pool=assertInstanceOf(com.zaxxer.hikari.HikariDataSource.class,datos);
        assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections(),"El caso previo debe cerrar sus actores/TX antes de cambiar la topología TEST");
        pool.setMaximumPoolSize(2);pool.getHikariPoolMXBean().softEvictConnections();
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);
        puts=new AtomicInteger();alGuardar=()->{};alAbrir=()->{};namespace=AlmacenDocumentos.huellaIdentidad("TEST","material-ia-"+UUID.randomUUID());
        when(almacen.proveedor()).thenReturn("TEST");when(almacen.identidadTecnica()).thenAnswer(i->namespace);
        when(almacen.abrir(anyString())).thenAnswer(i->{alAbrir.run();return Optional.ofNullable(objetos.get((String)i.getArgument(0)));});
        doAnswer(i->{alGuardar.run();puts.incrementAndGet();String clave=i.getArgument(0);byte[] contenido=i.getArgument(1);
            objetos.put(clave,new AlmacenDocumentos.ArchivoDescargado(contenido.clone(),"TEST.png",i.getArgument(2)));return null;
        }).when(almacen).guardarEnClave(anyString(),any(),anyString());
        doAnswer(i->{objetos.remove((String)i.getArgument(0));return null;}).when(almacen).eliminar(anyString());
        var origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        equipo=origenes.empresa();sesion=origenes.sesion(equipo.agente());
        permiso=true;acreditada=true;fallaArchivo=false;fresca=true;llamadas=new AtomicInteger();controlesEjecutados=new AtomicInteger();archivos=new AtomicInteger();fuentesPreparadas=new AtomicInteger();
        lecturasArchivo=new AtomicInteger();
        decision=Decision.APROBADA;ahora=Instant.now();fondo=UUID.randomUUID();ciclo=UUID.randomUUID();version=UUID.randomUUID().toString();
        intentosPerfil=info.getTags().contains("QC_FACTURABLE_TEST")?2:1;
        controlRecibido=Function.identity();idAdaptador="PROCESADOR_TEST_SIN_RED";
        instruccionPermitida="Instrucción inmobiliaria minimizada TEST";transferenciaPermitida=true;antesDeTransferencia=()->{};
        verificacionesTransferencia=new AtomicInteger();destinos=new ConcurrentHashMap<>();
        when(almacenamiento.guardar(any(),any(),any(),any(),any(),any())).thenAnswer(i->{
            Solicitud s=i.getArgument(0);Salida r=i.getArgument(2);assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("LIQUIDADA",estadoB0(s));archivos.incrementAndGet();if(fallaArchivo)throw new IllegalStateException("TEST fallo posterior a consumo real sintético");
            return new ResultadosMaterialIa.Guardado(UUID.nameUUIDFromBytes(s.operacion().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),com.controllocal.service.documentos.EvaluacionTecnicaFoto.huella(r.foto()));
        });
        doAnswer(i->{lecturasArchivo.incrementAndGet();fuente.exigirLecturaHistorica(i.getArgument(0),i.getArgument(1),i.getArgument(3));return null;}).when(almacenamiento).exigirLectura(any(),any(),any(),any(),any());
        // Doble explícito PG+mapa de bytes: no conserva Salida/medición en RAM para recovery.
        // No demuestra journal/cuota/disco: esos casos usan resultadosReales y la suite Disco.
        doAnswer(i->{long org=i.getArgument(0);UUID job=i.getArgument(1);String clave=i.getArgument(2);Salida r=i.getArgument(3);
            var o=registro.obtener(org,job).orElseThrow();assertEquals(o.claveExterna(),clave);assertNotNull(o.medicion());
            if(r.foto()!=null)objetos.put("RECIBIDO_TEST:"+org+":"+job,new AlmacenDocumentos.ArchivoDescargado(r.foto().clone(),"TEST.png",r.contentType()));
            return null;
        }).when(almacenamiento).recibirResultado(anyLong(),any(),anyString(),any());
        when(almacenamiento.recuperarRecibido(anyLong(),any(),anyString())).thenAnswer(i->{long org=i.getArgument(0);UUID job=i.getArgument(1);
            var o=registro.obtener(org,job).orElseThrow();assertEquals(o.claveExterna(),i.getArgument(2));
            var d=json.comoObjeto(o.salida(),MaterialIaServiceImpl.DetalleSalida.class);
            var b=Optional.ofNullable(objetos.get("RECIBIDO_TEST:"+org+":"+job)).orElseThrow(()->new NoEncontradoException("Recepción TEST"));
            assertEquals(d.huellaFoto(),com.controllocal.service.documentos.EvaluacionTecnicaFoto.huella(b.contenido()));
            return new Salida(d.texto(),d.contentType(),b.contenido(),d.proceso(),d.versionProceso(),json.comoObjeto(o.medicion(),ResultadoFacturable.class));
        });
        when(almacenamiento.previsionar(any(),any(),any(),any())).thenAnswer(i->UsoAlmacenamientoB0.referenciaPrevision(((Actor)i.getArgument(2)).idOrganizacion(),((Solicitud)i.getArgument(0)).operacion()));
        // Sólo este doble del coordinador: no acredita una previsión ni una cuota reales.
        when(almacenamiento.limiteRecepcionParaInvocar(any(),any())).thenAnswer(i->new UsoAlmacenamientoB0.LimiteRecepcion(
                UsoAlmacenamientoB0.referenciaPrevision(((Actor)i.getArgument(1)).idOrganizacion(),i.getArgument(0)),
                1000000,"LIMITE_COORDINADOR_TEST","FUENTE_COORDINADOR_TEST",namespace));
        when(almacenamiento.version(any(),any())).thenAnswer(i->{UUID job=i.getArgument(0);Actor a=i.getArgument(1);var j=registro.obtener(a.idOrganizacion(),job).orElseThrow();var s=json.comoObjeto(j.solicitud(),Solicitud.class);
            return new ResultadosMaterialIa.Version(job,s.fotoOriginal(),UUID.nameUUIDFromBytes(job.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),0,ahora,ahora,ahora.plus(Duration.ofDays(7)),false,false,true,"DISPONIBLE");});
        var env=new MockEnvironment();for(Accion a:Accion.values()){
            String pref="controllocal.ia.material."+a.name().toLowerCase(Locale.ROOT)+".";
            env.setProperty(pref+"perfil","MATERIAL_IA_TEST");env.setProperty(pref+"version",version);
        }
        perfiles=new PerfilesMaterialIa(env);autoridad=new AutoridadMaterialIa(registro,json,List.of(fuente),perfiles);
        b0=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,
                new DerechosDePlanB0(financiero,json),true);
        servicio=crear(b0,perfiles,List.of(proveedor),List.of(control));
        ejecucion=p->salida(p.accion());financiacion();
    }
    @AfterEach void terminar(){SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    MaterialIaServiceImpl crear(PresupuestoIaService presupuesto,PerfilesMaterialIa configuracion,List<ProveedorMaterialIa> ps,List<ControlesMaterialIa> cs){
        return new MaterialIaServiceImpl(registro,presupuesto,contexto.getBean(ContextoPresupuestoB0.class),autoridad,configuracion,json,
                ps,cs,List.of(almacenamiento),gestor);
    }
    Solicitud solicitud(Accion a){return new Solicitud(UUID.randomUUID(),a,new Recurso("PROPIEDAD","123"),"ORIGEN-TEST-1",
            a==Accion.REDACTAR_FICHA?null:UUID.randomUUID(),"Actuación inmobiliaria sintética TEST");}
    Resultado ejecutar(Solicitud s){return servicio.solicitar(s,equipo.agente(),sesion);}
    String evidenciaDestino(Solicitud s,String estado){return jdbc.queryForObject("SELECT evidencia FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado=?",String.class,equipo.agente().idOrganizacion(),s.operacion(),estado);}
    String estadoB0(Solicitud s){return financiero.operacion(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow().estado();}
    String estado(Solicitud s){return registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow().estado();}
    Salida salida(Accion a){return new Salida(a==Accion.MEJORAR_FOTO?null:"Borrador TEST de hechos conocidos",
            a==Accion.MEJORAR_FOTO?"image/png":null,a==Accion.MEJORAR_FOTO?new byte[]{4,5,6}:null,"PROCESADOR_TEST","1",
            new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),"TEST_SIN_RED","SINTETICO","1",
                    List.of(new Medicion("unidades_test","u",BigDecimal.ONE,null)),List.of(new Tarifa("unidades_test","u",new BigDecimal("0.1"),BigDecimal.ONE)),
                    null,"TEST-INTENTO-"+UUID.randomUUID()))));}
    Control controlSintetico(Solicitud s){
        var senales=Arrays.stream(Aspecto.values()).map(a->{
            boolean noAplica=s.accion()==Accion.REDACTAR_FICHA&&a==Aspecto.CALIDAD_VISUAL
                    ||s.accion()==Accion.ANALIZAR_FOTO&&a==Aspecto.PERDIDA_RELEVANTE;
            return new Senal(a,noAplica?EstadoSenal.NO_APLICA:EstadoSenal.EVALUADA,false,
                    noAplica?"TEST modalidad sin transformación visual del original":"TEST SINTETICO NO ACREDITA FIDELIDAD REAL",
                    "TEST-SINTETICO-"+s.operacion()+"-"+a);
        }).toList();
        return new Control(decision,"QC-SINTETICO-2",Instant.now(),senales);
    }
    Control conSenal(Control c,Aspecto aspecto,EstadoSenal estado,boolean bloqueante,String observacion,String evidencia){
        return new Control(c.decision(),c.version(),c.evaluadaEn(),c.senales().stream()
                .map(s->s.aspecto()==aspecto?new Senal(aspecto,estado,bloqueante,observacion,evidencia):s).toList());
    }
    void financiacion(){tx.executeWithoutResult(t->{
        var a=equipo.agente();UUID origen=UUID.randomUUID();
        jdbc.update("INSERT INTO b0_fondo_tecnico(id_fondo,moneda,financiado,desde,hasta,evidencia_financiacion,habilitado) VALUES (?,'USD',100,?,?,'TEST SIN FONDOS REALES',true)",fondo,utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)));
        jdbc.update("INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado) VALUES ('MATERIAL_IA_TEST',?,?,1,'[{\"clase\":\"unidades_test\",\"unidad\":\"u\",\"importeUsd\":1,\"porCantidad\":1}]'::jsonb,4,1,?,'TEST NO CALIBRACION','TEST NO APROBACION OPERATIVA',true)",version,fondo,intentosPerfil);
        jdbc.update("INSERT INTO b0_perfil_actual(perfil,version) VALUES ('MATERIAL_IA_TEST',?) ON CONFLICT(perfil) DO UPDATE SET version=excluded.version",version);
        jdbc.update("INSERT INTO b0_limite_salida_ia(perfil,version_perfil,maximo_bytes,evidencia_calibracion,evidencia_aprobacion,desde,hasta,habilitado) VALUES('MATERIAL_IA_TEST',?,1000000,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',?,?,true)",version,utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)));
        jdbc.update("""
            INSERT INTO b0_politica_capacidad_fisica(namespace_hash,version,capacidad_bytes,ocupacion_conciliada_bytes,conciliada_en,
             evidencia_calibracion,evidencia_aprobacion,evidencia_financiacion,evidencia_conciliacion,desde,hasta,habilitada)
             VALUES(?,?,16000000,0,?,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA','TEST SIN FINANCIACION REAL','TEST MAPA VACIO',?,?,true)
            """,namespace,version,utc(ahora.minusSeconds(3600)),utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)));
        jdbc.update("INSERT INTO b0_capacidad_fisica_actual(namespace_hash,version) VALUES(?,?)",namespace,version);
        jdbc.update("INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en) VALUES (?,?,'TEAM',?,?,1,75,?,'TEST NO PAGO REAL',?)",a.idOrganizacion(),ciclo,utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)),UUID.randomUUID(),utc(ahora));
        jdbc.update("INSERT INTO b0_fuente_capacidad(organizacion_id,id_fuente,id_ciclo,origen,id_persona_destinataria,plaza,origen_verificado,inicial,desde,vence,registrado_en) VALUES (?,?,?,'MENSUAL_AGENTE',?,1,?,10,?,?,?)",a.idOrganizacion(),origen,ciclo,a.idPersona(),UUID.randomUUID(),utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)),utc(ahora));
        jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_persona,id_fuente) VALUES (?,?,'AGENTE',1,?,?)",a.idOrganizacion(),ciclo,a.idPersona(),origen);
    });}
    static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}

    /**
     * Montaje I/O real: hasta dos TX de actor más las TX cortas independientes del
     * journal139 requieren pool4. El inspector T4 usa conexión separada, fuera del
     * pool. Este cambio sucede antes de subir original/abrir TX o hilos; los dos
     * T4 del coordinador sin almacenamiento no llaman este montaje y conservan2.
     */
    Solicitud derivadaReal()throws Exception{
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var pool=assertInstanceOf(com.zaxxer.hikari.HikariDataSource.class,datos);
        assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections(),"Configurar journal139 antes de abrir actores/hilos");
        pool.getHikariPoolMXBean().softEvictConnections();pool.setMaximumPoolSize(4);
        var a=equipo.agente();var acceso=tx.execute(t->contexto.getBean(ContextosService.class).exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),a));
        var request=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(request,null,List.of()));
        long propiedad=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).propiedad(a,"Derivada TEST");
        byte[] original=imagen(0x0000ff),derivada=imagen(0xff0000);
        var foto=contexto.getBean(GaleriaDeFotos.class).agregar(propiedad,"original.png",Base64.getEncoder().encodeToString(original),FotoPropiedadService.TOPE_UNIVERSAL,a);
        var real=resultadosReales();servicio=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,
                List.of(proveedor),List.of(control),List.of(real),gestor);
        ejecucion=p->new Salida(null,"image/png",derivada,"PROCESADOR_TEST","1",salida(Accion.MEJORAR_FOTO).medicion());
        return new Solicitud(UUID.randomUUID(),Accion.MEJORAR_FOTO,new Recurso("PROPIEDAD",Long.toString(propiedad)),"ORIGEN-TEST-1",UUID.fromString(foto.clave()),"Mejora sintética TEST");
    }
    ResultadosMaterialIaAlmacen resultadosReales(){return new ResultadosMaterialIaAlmacen(contexto.getBean(DerivadasMaterialIaService.class),fuente,
            contexto.getBean(SalidaBinariaPersistente.class),contexto.getBean(CuotaArchivosB0.class),almacen,contexto.getBean(UsoAlmacenamientoB0.class),b0);}
    static byte[] imagen(int color)throws Exception{
        var imagen=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int x=0;x<32;x++)for(int y=0;y<32;y++)imagen.setRGB(x,y,color);
        var salida=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(imagen,"png",salida);return salida.toByteArray();
    }
    long uso(){return jdbc.queryForObject("SELECT confirmados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,equipo.agente().idOrganizacion());}
    String claveDerivada(Solicitud s){return jdbc.queryForObject("SELECT i.clave_proveedor FROM derivada_material_ia d JOIN intento_salida_binaria i ON i.organizacion_id=d.organizacion_id AND i.operacion=d.operacion_io AND i.numero=d.numero_io WHERE d.organizacion_id=? AND d.id_operacion=?",String.class,equipo.agente().idOrganizacion(),s.operacion());}
    /** Fuente jurídica SINTÉTICA sólo para este recurso/actor; presentar/capturar y la autorización C1 siguen reales. */
    DerivadasMaterialIaDePrueba bancoFuenteReal(Solicitud foto){
        var banco=new DerivadasMaterialIaDePrueba(contexto,jdbc,gestor);
        avisosFuenteReal(foto,Set.of(banco.destino()));return banco;
    }
    void avisosFuenteReal(Solicitud foto,Set<DestinoTratamiento> destinosPermitidos){
        var a=equipo.agente();
        when(avisos.vigente(any(),eq(a))).thenAnswer(i->{EvidenciasTratamientoMaterialIa.AmbitoTratamiento ambito=i.getArgument(0);
            if(!foto.recurso().equals(ambito.solicitud().recurso())||!foto.fotoOriginal().equals(ambito.solicitud().fotoOriginal()))return Optional.empty();
            String texto="CONDICIONES SINTETICAS TEST SIN PROVEEDOR O LEGITIMACION PRODUCTIVOS";
            return Optional.of(new AvisosTratamientoMaterialIa.Contenido("AVISO-TEST:"+foto.fotoOriginal(),1,texto,Set.of(Accion.MEJORAR_FOTO),ambito.categorias(),"LEGAL_TEST_SIN_RED","TEST-1",texto,"FUENTE_TEST",Idempotencia.huellaExacta(texto),destinosPermitidos));
        });
        org.mockito.stubbing.Answer<Optional<AvisosTratamientoMaterialIa.Legitimacion>> legitimacion=i->{
            EvidenciasTratamientoMaterialIa.AmbitoTratamiento ambito=i.getArgument(0);AvisosTratamientoMaterialIa.Contenido aviso=i.getArgument(1);Actor actual=i.getArgument(2);
            if(!a.equals(actual)||!foto.recurso().equals(ambito.solicitud().recurso())||!foto.fotoOriginal().equals(ambito.solicitud().fotoOriginal()))return Optional.empty();
            return Optional.of(new AvisosTratamientoMaterialIa.Legitimacion("LEGITIMACION_TEST:"+foto.fotoOriginal(),"FUENTE_SINTETICA_TEST","e".repeat(64),foto.recurso(),Set.of(Accion.MEJORAR_FOTO),ambito.categorias(),aviso.proveedorLegal(),aviso.condicionesVersion()));
        };
        when(avisos.legitimacionActual(any(),any(),eq(a))).thenAnswer(legitimacion);
        when(avisos.legitimacionUsoArtefacto(any(),any(),eq(a))).thenAnswer(legitimacion);
    }
    void historialInmutable(org.junit.jupiter.api.function.Executable ejecutar){
        var fallo=assertThrows(org.springframework.dao.DataAccessException.class,ejecutar);
        var sql=assertInstanceOf(java.sql.SQLException.class,fallo.getMostSpecificCause());assertEquals("23514",sql.getSQLState());
        assertTrue(sql.getMessage().contains("La derivada conserva vínculo y linaje"));
    }
    void guarda148(String nombre,org.junit.jupiter.api.function.Executable ejecutar){
        var fallo=assertThrows(org.springframework.dao.DataAccessException.class,ejecutar);
        var sql=assertInstanceOf(org.postgresql.util.PSQLException.class,fallo.getMostSpecificCause());assertEquals("23514",sql.getSQLState());
        var diagnostico=sql.getServerErrorMessage();assertNotNull(diagnostico);assertEquals(nombre,diagnostico.getConstraint());
    }
    void cerrarActoresTest(ExecutorService actores,Collection<? extends Future<?>> tareas,Runnable liberar)throws InterruptedException{
        // Liberar barreras ANTES de esperar; Future.cancel no sustituye el bound SQL TEST.
        liberar.run();for(var tarea:tareas)tarea.cancel(true);actores.shutdownNow();
        assertTrue(actores.awaitTermination(15,TimeUnit.SECONDS),"Los actores TEST deben terminar antes del siguiente caso");
    }
    Solicitud inciertaParaEtapasSqlTest(){var s=solicitud(Accion.REDACTAR_FICHA);ejecucion=p->{throw new ProcesamientoIncierto();};assertThrows(ProcesamientoIncierto.class,()->ejecutar(s));return s;}
    record EtapaSql(Solicitud solicitud,ReferenciaInterna referencia,String clave,String proveedor){}
    EtapaSql etapaSqlTest(String proveedor){
        var s=inciertaParaEtapasSqlTest();var ref=new ReferenciaInterna(equipo.agente().idOrganizacion(),s.operacion());
        String clave=PresupuestoIaService.claveDeEtapa(ref,"QC_SQL_TEST");
        tx.executeWithoutResult(t->{b0.programarEtapa(ref,"QC_SQL_TEST",proveedor,"a".repeat(64));
            assertEquals(1,jdbc.update("UPDATE b0_etapa_operacion_ia SET estado='RECLAMADA',reclamada_en=clock_timestamp() WHERE organizacion_id=? AND id_operacion=? AND clave=?",ref.organizacion(),ref.operacion(),clave));});
        return new EtapaSql(s,ref,clave,proveedor);
    }
    void resolverMedidaSqlTest(EtapaSql e,String ids){jdbc.update("UPDATE b0_etapa_operacion_ia SET estado='MEDIDA',resultado_hash=?,mediciones=CAST(? AS jsonb),resuelta_en=clock_timestamp() WHERE organizacion_id=? AND id_operacion=? AND clave=?","b".repeat(64),ids,e.referencia().organizacion(),e.referencia().operacion(),e.clave());}
    UUID medicionSqlDeEtapaTest(EtapaSql e){return medicionSqlDeEtapaTest(e,UUID.fromString("abcdefab-1234-4567-89ab-abcdefabcdef"));}
    UUID medicionSqlDeEtapaTest(EtapaSql e,UUID id){
        tx.executeWithoutResult(t->assertTrue(financiero.medicion(e.referencia().organizacion(),e.referencia().operacion(),id,e.proveedor(),"QC_SQL_SINTETICO","1","[]","[]",new BigDecimal("0.1"),"CONFIRMADO","SQL_TEST_"+UUID.randomUUID(),"c".repeat(64),Idempotencia.huellaExacta(UUID.randomUUID().toString()),Instant.now(),e.clave())));return id;}
    @ParameterizedTest @ValueSource(strings={"SQL_NULL","JSON_NULL","OBJETO","VACIO"})
    void sql148MedidaRechazaAusenciaDeIdsPorGuardaNombrada(String caso){
        var e=etapaSqlTest("QC_SQL_TEST");String ids=switch(caso){case "SQL_NULL"->null;case "JSON_NULL"->"null";case "OBJETO"->"{}";default->"[]";};
        guarda148("ck_b0_etapa_ia_medida",()->tx.executeWithoutResult(t->resolverMedidaSqlTest(e,ids)));
        assertTrue(financiero.etapasPendientes(e.referencia().organizacion(),e.referencia().operacion()));
    }
    @ParameterizedTest @ValueSource(strings={"INEXISTENTE","GEN_MISMO_PROVEEDOR","OTRO_JOB","OTRO_PROVEEDOR"})
    void sql148MedidaExigeIdRealDelMismoJobProveedorYClaim(String caso){
        var e=etapaSqlTest(caso.equals("OTRO_PROVEEDOR")?"OTRO_QC_TEST":"TEST_SIN_RED");UUID id=UUID.randomUUID();
        if(!caso.equals("INEXISTENTE")){
            var ref=caso.equals("OTRO_JOB")?new ReferenciaInterna(equipo.agente().idOrganizacion(),inciertaParaEtapasSqlTest().operacion()):e.referencia();
            var m=salida(Accion.REDACTAR_FICHA).medicion();tx.executeWithoutResult(t->b0.registrarMediciones(ref,m));id=m.mediciones().getFirst().id();
        }
        String ids=json.lista(List.of(id));guarda148("ck_b0_etapa_ia_vinculo",()->tx.executeWithoutResult(t->resolverMedidaSqlTest(e,ids)));
        assertTrue(financiero.etapasPendientes(e.referencia().organizacion(),e.referencia().operacion()));
    }
    @ParameterizedTest @ValueSource(strings={"ORGANIZACION","OPERACION","PROVEEDOR"})
    void sql148OrigenDeMedicionExigeElClaimRealDeSuOrganizacionOperacionYProveedor(String caso){
        var e=etapaSqlTest("QC_SQL_TEST");
        long org=caso.equals("ORGANIZACION")?new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).empresa().agente().idOrganizacion():e.referencia().organizacion();
        UUID operacion=caso.equals("OPERACION")?inciertaParaEtapasSqlTest().operacion():e.referencia().operacion();
        String proveedor=caso.equals("PROVEEDOR")?"PROVEEDOR_AJENO_TEST":e.proveedor();
        // El repositorio no inventa el padre comercial de una organización ajena.
        if(caso.equals("ORGANIZACION")){
            assertFalse(Boolean.TRUE.equals(tx.execute(t->financiero.medicion(org,operacion,UUID.randomUUID(),proveedor,
                "SQL_SINTETICO","1","[]","[]",new BigDecimal("0.1"),"CONFIRMADO","ORIGEN_TEST_"+UUID.randomUUID(),"d".repeat(64),Idempotencia.huellaExacta(UUID.randomUUID().toString()),Instant.now(),e.clave()))));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,org,operacion));
        }
        // Ataque SQL completo salvo el atributo alterado: padre/etapaA reales, ámbito o proveedor falsos.
        guarda148("ck_b0_medicion_origen_etapa",()->tx.executeWithoutResult(t->jdbc.update("""
            INSERT INTO b0_medicion_tecnica(organizacion_id,id_operacion,id_operacion_b0,id_medicion,proveedor,modelo,version_tarifa,
             unidades_originales,tarifas_originales,costo_usd,estado_costo,referencia_fuente,huella,huella_intento,registrada_en,etapa_clave)
            VALUES(?,?,b0_resolver_operacion(?,?),?,?,'SQL_SINTETICO','1','[]'::jsonb,'[]'::jsonb,0.1,'CONFIRMADO',?,
             'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',?,clock_timestamp(),?)
            """,org,operacion,e.referencia().organizacion(),e.referencia().operacion(),UUID.randomUUID(),proveedor,
                "ORIGEN_TEST_"+UUID.randomUUID(),Idempotencia.huellaExacta(UUID.randomUUID().toString()),e.clave())));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,e.referencia().organizacion(),e.referencia().operacion()));
    }
    @Test void sql148UuidMayusculaYMinusculaSonUnaIdentidadNoDosMediciones(){
        var e=etapaSqlTest("QC_SQL_TEST");UUID id=medicionSqlDeEtapaTest(e);String ids=json.lista(List.of(id.toString(),id.toString().toUpperCase(Locale.ROOT)));
        guarda148("ck_b0_etapa_ia_vinculo",()->tx.executeWithoutResult(t->resolverMedidaSqlTest(e,ids)));
        assertEquals("RECLAMADA",financiero.etapa(e.referencia().organizacion(),e.referencia().operacion(),e.clave()).orElseThrow().estado());
    }
    @ParameterizedTest @ValueSource(strings={"SIN_GUIONES","NUMERO_JSON","INVALIDO","NULL_JSON"})
    void medidaExigeStringsUuidConFormatoExplicitoAntesDeNormalizar(String caso){
        var e=etapaSqlTest("QC_SQL_TEST");UUID id=medicionSqlDeEtapaTest(e,UUID.fromString("12345678-1234-4567-8912-123456789012"));
        String compacto=id.toString().replace("-","");
        String ids=switch(caso){case "SIN_GUIONES"->json.lista(List.of(compacto));case "NUMERO_JSON"->"["+compacto+"]";
            case "INVALIDO"->"[\"no-es-un-uuid\"]";default->"[null]";};
        guarda148("ck_b0_etapa_ia_vinculo",()->tx.executeWithoutResult(t->resolverMedidaSqlTest(e,ids)));
        assertEquals("RECLAMADA",financiero.etapa(e.referencia().organizacion(),e.referencia().operacion(),e.clave()).orElseThrow().estado());
    }
    @ParameterizedTest @ValueSource(strings={"NaN","Infinity","-Infinity"})
    void sql148ImporteNativoNoAdmiteValoresNoFinitos(String importe){
        var s=inciertaParaEtapasSqlTest();String guarda=importe.equals("-Infinity")?"b0_medicion_tecnica_costo_usd_check":"ck_b0_medicion_costo_nativo_finito";
        guarda148(guarda,()->tx.executeWithoutResult(t->jdbc.update("""
            INSERT INTO b0_medicion_tecnica(organizacion_id,id_operacion,id_operacion_b0,id_medicion,proveedor,modelo,version_tarifa,
             unidades_originales,tarifas_originales,costo_usd,estado_costo,referencia_fuente,huella,huella_intento,registrada_en)
            VALUES(?,?,b0_resolver_operacion(?,?),?,'SQL_TEST_SIN_RED','SINTETICO','1','[]'::jsonb,'[]'::jsonb,CAST(? AS numeric),'CONFIRMADO',?,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',?,clock_timestamp())
            """,equipo.agente().idOrganizacion(),s.operacion(),equipo.agente().idOrganizacion(),s.operacion(),UUID.randomUUID(),importe,"TEST_"+UUID.randomUUID(),Idempotencia.huellaExacta(UUID.randomUUID().toString()))));
    }
    @Test @Tag("QC_FACTURABLE_TEST") void importesConfirmadosNativosNoRedondeanFragmentosYSoloElTotalMantiene38_12(){
        var s=inciertaParaEtapasSqlTest();var ref=new ReferenciaInterna(equipo.agente().idOrganizacion(),s.operacion());
        var a=salida(Accion.REDACTAR_FICHA).medicion().mediciones().getFirst();var b=salida(Accion.REDACTAR_FICHA).medicion().mediciones().getFirst();
        var nativo=new BigDecimal("0.0000000000006");var m=new ResultadoFacturable(List.of(
                new MedicionFacturable(a.id(),a.proveedor(),a.modelo(),a.versionTarifa(),a.unidades(),a.tarifasReales(),nativo,a.referenciaFuente()),
                new MedicionFacturable(b.id(),b.proveedor(),b.modelo(),b.versionTarifa(),b.unidades(),b.tarifasReales(),nativo,b.referenciaFuente())));
        tx.executeWithoutResult(t->b0.registrarMediciones(ref,m));
        for(var v:jdbc.queryForList("SELECT costo_usd FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",BigDecimal.class,ref.organizacion(),ref.operacion()))assertEquals(0,nativo.compareTo(v));
        assertEquals("LIQUIDADA",tx.execute(t->b0.conciliar(ref,m)).estado());
        assertEquals(0,new BigDecimal("0.000000000001").compareTo(jdbc.queryForObject("SELECT costo_tecnico_usd FROM b0_operacion_ia WHERE organizacion_id=? AND id_operacion=?",BigDecimal.class,ref.organizacion(),ref.operacion())));
        assertEquals(38,jdbc.queryForObject("SELECT numeric_precision FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='b0_operacion_ia' AND column_name='costo_tecnico_usd'",Integer.class));
        assertEquals(12,jdbc.queryForObject("SELECT numeric_scale FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='b0_operacion_ia' AND column_name='costo_tecnico_usd'",Integer.class));
    }
    @Test void custodiaEsVisibleEnOtraTxAntesDePutSinEsperarFilasDelPadre()throws Exception{
        var s=derivadaReal();long org=equipo.agente().idOrganizacion();var observada=new AtomicBoolean();
        var inspector=Executors.newSingleThreadExecutor();var lecturas=new CopyOnWriteArrayList<Future<?>>();
        try{
            alGuardar=()->{
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());long pidPadre=jdbc.queryForObject("SELECT pg_backend_pid()",Long.class);
                long txPadre=jdbc.queryForObject("SELECT txid_current()",Long.class);
                try{var lectura=inspector.submit(()->tx.executeWithoutResult(t->{
                    jdbc.execute("SET LOCAL statement_timeout='5s'");
                    assertNotEquals(pidPadre,jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
                    var fila=jdbc.queryForMap("""
                        SELECT c.estado custodia,c.xmin::text::bigint tx_custodia,i.estado intento,j.estado job
                        FROM custodia_resultado_material_ia c JOIN intento_salida_binaria i
                         ON i.organizacion_id=c.organizacion_id AND i.operacion=c.operacion_io AND i.numero=c.numero_io
                        JOIN material_ia_operacion j ON j.organizacion_id=c.organizacion_id AND j.id_operacion=c.id_operacion
                        WHERE c.organizacion_id=? AND c.id_operacion=?
                        """,org,s.operacion());
                    assertEquals("PREPARADA",fila.get("custodia"));assertEquals("PREPARADO",fila.get("intento"));assertEquals("CONCILIADA",fila.get("job"));
                    assertNotEquals(txPadre,((Number)fila.get("tx_custodia")).longValue());assertEquals(0,controlesEjecutados.get());observada.set(true);
                }));lecturas.add(lectura);lectura.get(10,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError("Custodia/ancestros deben estar comprometidos antes del PUT con padre activo",e);}
            };
            assertEquals("DISPONIBLE",ejecutar(s).estado());
        }finally{alGuardar=()->{};cerrarActoresTest(inspector,lecturas,()->{});}
        assertTrue(observada.get());assertEquals(1,llamadas.get());assertEquals(1,controlesEjecutados.get());
        assertEquals("VERIFICADA",registro.custodia(org,s.operacion()).orElseThrow().estado());
    }
    @ParameterizedTest @ValueSource(ints={1,2})
    void recepcionesConTodosLosPadresEnPoolPrincipalUsanCarril139SinPedirConexionPrincipalHija(int padres)throws Exception{
        var primera=derivadaReal();var solicitudes=new ArrayList<Solicitud>();solicitudes.add(primera);
        if(padres==2){var foto=contexto.getBean(GaleriaDeFotos.class).agregar(Long.parseLong(primera.recurso().referencia()),"segunda-original.png",Base64.getEncoder().encodeToString(imagen(0x00ff00)),FotoPropiedadService.TOPE_UNIVERSAL,equipo.agente());
            solicitudes.add(new Solicitud(UUID.randomUUID(),primera.accion(),primera.recurso(),primera.versionOrigen(),UUID.fromString(foto.clave()),null));}
        var pool=assertInstanceOf(com.zaxxer.hikari.HikariDataSource.class,datos);assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());
        int maxAnterior=pool.getMaximumPoolSize(),minAnterior=pool.getMinimumIdle(),queryAnterior=jdbc.getQueryTimeout();long esperaAnterior=pool.getConnectionTimeout();
        var preparados=new CountDownLatch(padres);var comprobados=new CountDownLatch(padres);var pids=ConcurrentHashMap.<Long>newKeySet();int antesPut=puts.get();
        var actores=Executors.newFixedThreadPool(padres);var tareas=new ArrayList<Future<Resultado>>();
        try{
        pool.setMinimumIdle(padres);pool.setMaximumPoolSize(padres);pool.setConnectionTimeout(1500);pool.getHikariPoolMXBean().softEvictConnections();jdbc.setQueryTimeout(12);
        var mx=pool.getHikariPoolMXBean();long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<limite&&(mx.getTotalConnections()!=padres||mx.getActiveConnections()!=0||mx.getIdleConnections()!=padres))Thread.sleep(20);
        assertEquals(padres,mx.getTotalConnections());assertEquals(0,mx.getActiveConnections());assertEquals(padres,mx.getIdleConnections());
        var diario=contexto.getBean(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class);
        var seguimiento=new com.controllocal.service.archivo.SeguimientoSalidaBinaria(contexto.getBean(com.controllocal.persistence.repositorio.IntentoSalidaBinariaRepository.class),diario){
            @Override public Preparado preparar(com.controllocal.service.archivo.PropietarioBinario org,UUID op,String fuente,String ns,String extension,String mime,String hash,long bytes){
                var p=super.preparar(org,op,fuente,ns,extension,mime,hash,bytes);assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                pids.add(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));preparados.countDown();
                try{assertTrue(preparados.await(10,TimeUnit.SECONDS),"Todos los padres conservan una conexión ANTES de custodiar");
                    assertEquals(padres,mx.getTotalConnections());assertEquals(padres,mx.getActiveConnections());assertEquals(0,mx.getIdleConnections());
                    comprobados.countDown();assertTrue(comprobados.await(10,TimeUnit.SECONDS),"Ningún padre sale antes de verificar todos los snapshots del pool");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return p;
            }
        };
        var puente=new SalidaBinariaPersistente(almacen,seguimiento);
        var salidaReal=new ResultadosMaterialIaAlmacen(contexto.getBean(DerivadasMaterialIaService.class),fuente,puente,contexto.getBean(CuotaArchivosB0.class),almacen,contexto.getBean(UsoAlmacenamientoB0.class),b0);
        servicio=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(proveedor),List.of(control),List.of(salidaReal),gestor);
            for(var s:solicitudes)tareas.add(actores.submit(()->ejecutar(s)));
            for(var tarea:tareas)assertEquals("DISPONIBLE",tarea.get(30,TimeUnit.SECONDS).estado());
        }finally{try{cerrarActoresTest(actores,tareas,()->{while(preparados.getCount()>0)preparados.countDown();while(comprobados.getCount()>0)comprobados.countDown();});}
            finally{jdbc.setQueryTimeout(queryAnterior);pool.setConnectionTimeout(esperaAnterior);pool.setMaximumPoolSize(maxAnterior);pool.setMinimumIdle(minAnterior);}}
        assertEquals(padres,pids.size());assertEquals(padres,llamadas.get());assertEquals(antesPut+padres,puts.get());
        for(var s:solicitudes)assertEquals("VERIFICADA",registro.custodia(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow().estado());
    }
    @Test void insercionSqlPendienteYConciliacionCoreCompartenFenceAntesDeCerrar()throws Exception{
        var s=inciertaParaEtapasSqlTest();var ref=new ReferenciaInterna(equipo.agente().idOrganizacion(),s.operacion());var gen=salida(s.accion()).medicion();tx.executeWithoutResult(t->b0.registrarMediciones(ref,gen));
        var pool=assertInstanceOf(com.zaxxer.hikari.HikariDataSource.class,datos);assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());int maxAnterior=pool.getMaximumPoolSize();pool.getHikariPoolMXBean().softEvictConnections();pool.setMaximumPoolSize(3);
        var insertada=new CountDownLatch(1);var liberar=new CountDownLatch(1);var entraCierre=new CountDownLatch(1);var pidInserta=new AtomicLong();var pidCierra=new AtomicLong();
        var actores=Executors.newFixedThreadPool(2);var tareas=new ArrayList<Future<?>>();
        try{
            var a=actores.submit(()->tx.executeWithoutResult(t->{jdbc.execute("SET LOCAL statement_timeout='12s'");pidInserta.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
                jdbc.update("INSERT INTO b0_etapa_operacion_ia(organizacion_id,id_operacion,id_operacion_b0,etapa,clave,proveedor,huella_peticion,estado) VALUES(?,?,b0_resolver_operacion(?,?),'QC_SQL_TEST',?,'QC_SQL_TEST',?,'PENDIENTE')",ref.organizacion(),ref.operacion(),ref.organizacion(),ref.operacion(),PresupuestoIaService.claveDeEtapa(ref,"QC_SQL_TEST"),"a".repeat(64));insertada.countDown();
                try{assertTrue(liberar.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}));
            tareas.add(a);assertTrue(insertada.await(10,TimeUnit.SECONDS));var b=actores.submit(()->{
                try{tx.executeWithoutResult(t->{jdbc.execute("SET LOCAL statement_timeout='12s'");pidCierra.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));entraCierre.countDown();b0.conciliar(ref,gen);});return false;}
                catch(ConflictoException esperado){return true;}
            });
            tareas.add(b);assertTrue(entraCierre.await(10,TimeUnit.SECONDS));boolean bloqueado=false;long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<fin){if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))",Boolean.class,(int)pidInserta.get(),(int)pidCierra.get()))){bloqueado=true;break;}Thread.sleep(20);}
            assertTrue(bloqueado,"El cierre espera la fila padre de la etapa no comprometida (inspector PG independiente)");assertNotEquals(pidInserta.get(),pidCierra.get());
            liberar.countDown();a.get(10,TimeUnit.SECONDS);assertTrue(b.get(10,TimeUnit.SECONDS),"Core debe observar la etapa ya comprometida y denegar liquidar");
        }finally{try{cerrarActoresTest(actores,tareas,liberar::countDown);}finally{pool.setMaximumPoolSize(maxAnterior);}}
        assertEquals("INCIERTA",estadoB0(s));assertTrue(financiero.etapasPendientes(ref.organizacion(),ref.operacion()));assertEquals(1,llamadas.get());
    }
    @Test void cierreCoreNoComprometidoImpideInsertarEtapaSqlYTrasCommitRechazaPadreCerrado()throws Exception{
        var s=inciertaParaEtapasSqlTest();var ref=new ReferenciaInterna(equipo.agente().idOrganizacion(),s.operacion());var gen=salida(s.accion()).medicion();tx.executeWithoutResult(t->b0.registrarMediciones(ref,gen));
        var pool=assertInstanceOf(com.zaxxer.hikari.HikariDataSource.class,datos);assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());int maxAnterior=pool.getMaximumPoolSize();pool.getHikariPoolMXBean().softEvictConnections();pool.setMaximumPoolSize(3);
        var cerradaSinCommit=new CountDownLatch(1);var liberar=new CountDownLatch(1);var entraInsercion=new CountDownLatch(1);var pidCierra=new AtomicLong();var pidInserta=new AtomicLong();
        var actores=Executors.newFixedThreadPool(2);var tareas=new ArrayList<Future<?>>();
        try{
            var a=actores.submit(()->tx.executeWithoutResult(t->{jdbc.execute("SET LOCAL statement_timeout='12s'");pidCierra.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
                assertEquals("LIQUIDADA",b0.conciliar(ref,gen).estado());cerradaSinCommit.countDown();
                try{assertTrue(liberar.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}));
            tareas.add(a);assertTrue(cerradaSinCommit.await(10,TimeUnit.SECONDS));
            var b=actores.submit(()->{
                var fallo=assertThrows(org.springframework.dao.DataAccessException.class,()->tx.executeWithoutResult(t->{jdbc.execute("SET LOCAL statement_timeout='12s'");pidInserta.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));entraInsercion.countDown();
                    jdbc.update("INSERT INTO b0_etapa_operacion_ia(organizacion_id,id_operacion,id_operacion_b0,etapa,clave,proveedor,huella_peticion,estado) VALUES(?,?,b0_resolver_operacion(?,?),'QC_SQL_TEST',?,'QC_SQL_TEST',?,'PENDIENTE')",ref.organizacion(),ref.operacion(),ref.organizacion(),ref.operacion(),PresupuestoIaService.claveDeEtapa(ref,"QC_SQL_TEST"),"a".repeat(64));}));
                var sql=assertInstanceOf(org.postgresql.util.PSQLException.class,fallo.getMostSpecificCause());assertEquals("23514",sql.getSQLState());
                var diagnostico=sql.getServerErrorMessage();assertNotNull(diagnostico);
                assertEquals("La operación cerrada no admite programar o reclamar etapas",diagnostico.getMessage());assertNotNull(diagnostico.getWhere());assertTrue(diagnostico.getWhere().contains("exigir_conjunto_etapas_ia"));
            });
            tareas.add(b);assertTrue(entraInsercion.await(10,TimeUnit.SECONDS));boolean bloqueado=false;long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<fin){if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT ?=ANY(pg_blocking_pids(?))",Boolean.class,(int)pidCierra.get(),(int)pidInserta.get()))){bloqueado=true;break;}Thread.sleep(20);}
            assertTrue(bloqueado,"INSERT espera el padre cerrado aún sin commit, observado desde una tercera conexión");assertNotEquals(pidCierra.get(),pidInserta.get());
            liberar.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }finally{try{cerrarActoresTest(actores,tareas,liberar::countDown);}finally{pool.setMaximumPoolSize(maxAnterior);}}
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_etapa_operacion_ia WHERE organizacion_id=? AND id_operacion=?",Integer.class,ref.organizacion(),ref.operacion()));assertEquals(1,llamadas.get());
    }
    void metadataInmutable(org.junit.jupiter.api.function.Executable ejecutar){
        var fallo=assertThrows(org.springframework.dao.DataAccessException.class,ejecutar);
        var sql=assertInstanceOf(java.sql.SQLException.class,fallo.getMostSpecificCause());assertEquals("23514",sql.getSQLState());
        assertTrue(sql.getMessage().contains("evidencia mínima"));
    }

    /** Proveedor/parser reales; sólo HTTP, avisos/legitimación y dinero son TEST, sin inferencia ni pago. */
    @ParameterizedTest @ValueSource(booleans={false,true})
    void adaptadorConUsoConocidoSinImagenCierraSoloLoNoInvocadoYLiberaPrevision(boolean facturable)throws Exception{
        var banco=prepararSinImagen(facturable);var s=banco.solicitud();var fuenteReal=banco.fuente();var snapshot=banco.snapshot();
        byte[] original=banco.original();long org=equipo.agente().idOrganizacion();var a=equipo.agente();
        long usados=uso();int escritos=puts.get();
        // La primera respuesta puede informar el fallo o devolver la consulta; no se exige un nuevo código de error.
        try{ejecutar(s);}catch(RuntimeException falloEsperado){assertNotNull(falloEsperado.getMessage());}
        assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());assertEquals(escritos,puts.get());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=? AND estado_costo='CALCULADO'",Integer.class,org,s.operacion()));
        var reserva=UsoAlmacenamientoB0.referenciaPrevision(org,s.operacion()).reserva();
        assertAll(
            ()->assertEquals("FALLO_RESULTADO",estado(s)),
            ()->assertEquals("LIQUIDADA",estadoB0(s)),
            ()->assertEquals("CANCELADA",jdbc.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND id_reserva=?",String.class,org,reserva)),
            ()->assertEquals("NO_ALMACENADA",jdbc.queryForObject("SELECT estado FROM slot_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",String.class,org,s.operacion())),
            ()->{if(facturable)assertEquals("NO_INVOCADA",estadoEtapa(s));});
        assertEquals(usados,uso());assertArrayEquals(original,fuenteReal.fotoParaProcesar(s,snapshot,a,sesion));
        assertEquals("RESULTADO_NO_DISPONIBLE",ejecutar(s).estado());assertEquals(1,llamadas.get());assertEquals(escritos,puts.get());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND hecho='PREVISION_IA_NO_ALMACENADA'",Integer.class,org));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='QC_CLAIM'",Integer.class,org,s.operacion()));
    }
    record BancoSinImagen(Solicitud solicitud,FuentesMaterialIaCore fuente,Snapshot snapshot,byte[] original,
            ProveedorMaterialIa proveedor,ControlesMaterialIa control,ResultadosMaterialIaAlmacen resultados){}
    BancoSinImagen prepararSinImagen(boolean facturable)throws Exception{
        var foto=derivadaReal();var a=equipo.agente();long org=a.idOrganizacion();
        // Otra versión TEST explícita, sin reescribir el perfil ya registrado.
        version=UUID.randomUUID().toString();var nativas=List.of(
                new Tarifa("TEXTO_NATIVO_TEST","token",BigDecimal.ONE,new BigDecimal("1000000")),
                new Tarifa("IMAGEN_NATIVA_TEST","token",BigDecimal.ONE,new BigDecimal("1000000")),
                new Tarifa("SALIDA_NATIVA_TEST","token",BigDecimal.ONE,new BigDecimal("1000000")));
        var referencia=new ArrayList<>(nativas);referencia.add(new Tarifa("unidades_test","u",BigDecimal.ONE,BigDecimal.ONE));
        tx.executeWithoutResult(t->{
            jdbc.update("INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado) VALUES('MATERIAL_IA_TEST',?,?,1,?::jsonb,4,1,?,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',true)",version,fondo,json.lista(referencia),facturable?2:1);
            jdbc.update("UPDATE b0_perfil_actual SET version=? WHERE perfil='MATERIAL_IA_TEST'",version);
            jdbc.update("INSERT INTO b0_limite_salida_ia(perfil,version_perfil,maximo_bytes,evidencia_calibracion,evidencia_aprobacion,desde,hasta,habilitado) VALUES('MATERIAL_IA_TEST',?,1000000,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',?,?,true)",version,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(86400)));
        });
        var env=new MockEnvironment().withProperty("controllocal.ia.material.mejorar_foto.perfil","MATERIAL_IA_TEST")
                .withProperty("controllocal.ia.material.mejorar_foto.version",version);
        perfiles=new PerfilesMaterialIa(env);var fuenteReal=contexto.getBean(FuentesMaterialIaCore.class);
        autoridad=new AutoridadMaterialIa(registro,json,List.of(fuenteReal),perfiles);
        b0=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,
                new DerechosDePlanB0(financiero,json),true);
        var configuracion=new ConfiguracionEdicionOpenAi("MATERIAL_IA_TEST",version,"PROCESO_TEST",
                new ConfiguracionEdicionOpenAi.Parametros("medium","png",null,"opaque",1024,1024,1048576),
                new ConfiguracionEdicionOpenAi.Envoltura("ENV_TEST","a".repeat(64),1000000,1000000,16),
                new ConfiguracionEdicionOpenAi.ContratoUsage("USAGE_TEST","EVIDENCIA_SINTETICA_TEST","TARIFA_TEST",
                        "TEXTO_NATIVO_TEST","IMAGEN_NATIVA_TEST","SALIDA_NATIVA_TEST",nativas),Duration.ofSeconds(2),Duration.ofSeconds(2));
        byte[] respuesta=json.objetoCanonico(Map.of("data",List.of(Map.of("b64_json",Base64.getEncoder().encodeToString(new byte[]{1,2,3}))),
                "usage",Map.of("input_tokens",30,"input_tokens_details",Map.of("text_tokens",10,"image_tokens",20),"output_tokens",40,"total_tokens",70),
                "output_format","png","size","1024x1024")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var adaptador=new ProveedorEdicionOpenAi(configuracion,()->"CLAVE_TEST_NO_REAL",(peticion,maximo,total,contextoIntento,observador)->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());llamadas.incrementAndGet();
            assertEquals(ProveedorEdicionOpenAi.DESTINO,peticion.uri());assertTrue(respuesta.length<=maximo);
            return new TransporteOpenAi.Respuesta(200,java.net.http.HttpHeaders.of(Map.of("Content-Type",List.of("application/json"),
                    "x-request-id",List.of("req_TEST_"+foto.operacion())),(k,v)->true),respuesta);
        });
        ControlesMaterialIa qc=facturable?controlFacturableTest(r->{throw new AssertionError("QC sin fotografía no puede invocarse");}):control;
        var destinosTest=new HashSet<DestinoTratamiento>();destinosTest.add(new DestinoTratamiento(adaptador.identificador(),"MATERIAL_IA_TEST",version));
        if(facturable)destinosTest.add(((Facturable)qc).destino("MATERIAL_IA_TEST",version));
        avisosFuenteReal(foto,Set.copyOf(destinosTest));
        var reales=new ResultadosMaterialIaAlmacen(contexto.getBean(DerivadasMaterialIaService.class),fuenteReal,
                contexto.getBean(SalidaBinariaPersistente.class),contexto.getBean(CuotaArchivosB0.class),almacen,contexto.getBean(UsoAlmacenamientoB0.class),b0);
        servicio=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(adaptador),List.of(qc),List.of(reales),gestor);
        var inicial=new Solicitud(foto.operacion(),foto.accion(),foto.recurso(),null,foto.fotoOriginal(),null);
        var aviso=fuenteReal.presentarTratamiento(inicial,a,sesion);
        contexto.getBean(EvidenciasTratamientoMaterialIa.class).capturar(new EvidenciasTratamientoMaterialIa.Captura(UUID.randomUUID(),aviso.referencia(),aviso.versionCobertura(),
                EvidenciasTratamientoMaterialIa.Decision.ACEPTAR),a,sesion,Procedencia.deLaPantalla());
        var preparada=servicio.preparar(inicial,a,sesion);
        var s=new Solicitud(inicial.operacion(),inicial.accion(),inicial.recurso(),preparada.versionOrigen(),inicial.fotoOriginal(),null);
        var snapshot=fuenteReal.preparar(s,a,sesion);byte[] original=fuenteReal.fotoParaProcesar(s,snapshot,a,sesion);
        return new BancoSinImagen(s,fuenteReal,snapshot,original,adaptador,qc,reales);
    }
    MaterialIaServiceImpl receptorSinImagen(BancoSinImagen banco,RegistroMaterialIaRepository repo,ResultadosMaterialIa resultados){
        return new MaterialIaServiceImpl(repo,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,
                List.of(banco.proveedor()),List.of(banco.control()),List.of(resultados),gestor);
    }
    @Test void fotoAusenteRecuperaDespuesDeLiquidarAntesDeLiberarSinOtraInferencia()throws Exception{
        var banco=prepararSinImagen(true);var s=banco.solicitud();long org=equipo.agente().idOrganizacion();
        long usados=uso();int escritos=puts.get();var interrumpido=spy(banco.resultados());
        doThrow(new IllegalStateException("TEST interrupción después de liquidar y antes de liberar previsión"))
                .when(interrumpido).resolverSinArchivo(anyLong(),any(),anyString());
        servicio=receptorSinImagen(banco,registro,interrumpido);
        assertThrows(IllegalStateException.class,()->ejecutar(s));assertEquals("LIQUIDADA",estadoB0(s));assertEquals("NO_INVOCADA",estadoEtapa(s));
        assertEquals("FALLO_RESULTADO",estado(s));assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());
        var reserva=UsoAlmacenamientoB0.referenciaPrevision(org,s.operacion()).reserva();
        assertEquals("RESERVADA",jdbc.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND id_reserva=?",String.class,org,reserva));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND hecho='PREVISION_IA_NO_ALMACENADA'",Integer.class,org));
        // Objetos nuevos; la recuperación se reconstruye desde PG, no de una Salida guardada en el servicio.
        var recuperado=new ResultadosMaterialIaAlmacen(contexto.getBean(DerivadasMaterialIaService.class),banco.fuente(),
                contexto.getBean(SalidaBinariaPersistente.class),contexto.getBean(CuotaArchivosB0.class),almacen,contexto.getBean(UsoAlmacenamientoB0.class),b0);
        servicio=receptorSinImagen(banco,registro,recuperado);
        assertEquals("RESULTADO_NO_DISPONIBLE",ejecutar(s).estado());assertEquals("RESULTADO_NO_DISPONIBLE",ejecutar(s).estado());
        assertEquals("CANCELADA",jdbc.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND id_reserva=?",String.class,org,reserva));
        assertEquals("NO_ALMACENADA",jdbc.queryForObject("SELECT estado FROM slot_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",String.class,org,s.operacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND hecho='PREVISION_IA_NO_ALMACENADA'",Integer.class,org));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,org,s.operacion()));
        assertEquals(usados,uso());assertEquals(escritos,puts.get());assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());
        assertArrayEquals(banco.original(),banco.fuente().fotoParaProcesar(s,banco.snapshot(),equipo.agente(),sesion));
    }
    @Test void callbacksConcurrentesDeFotoAusenteConservanUnConsumoUnaLiberacionYNingunQc()throws Exception{
        var banco=prepararSinImagen(true);var s=banco.solicitud();long org=equipo.agente().idOrganizacion();
        long usados=uso();int escritos=puts.get();var salida=new AtomicReference<Salida>();
        servicio=spy(servicio);
        doAnswer(i->{salida.set(i.getArgument(3));throw new IllegalStateException("TEST respuesta externa antes de entregar el callback");})
                .when(servicio).registrarResultadoVerificado(anyLong(),any(),anyString(),any());
        assertThrows(IllegalStateException.class,()->ejecutar(s));assertEquals("RECLAMADA",estado(s));assertEquals(1,llamadas.get());assertNotNull(salida.get());
        assertNull(salida.get().foto());assertTrue(financiero.idsMediciones(org,s.operacion()).isEmpty());
        var barrera=new CyclicBarrier(2);var pids=ConcurrentHashMap.<Integer>newKeySet();var hilos=ConcurrentHashMap.<Long>newKeySet();
        var repoConcurrente=new RegistroMaterialIaRepository(jdbc,contexto.getBean(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class)){
            @Override public void bloquear(long clave){
                if(hilos.add(Thread.currentThread().threadId())){
                    pids.add(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                    try{barrera.await(10,TimeUnit.SECONDS);}catch(Exception fallo){throw new IllegalStateException("TEST actores simultáneos",fallo);}
                }
                super.bloquear(clave);
            }
        };
        var receptor=receptorSinImagen(banco,repoConcurrente,banco.resultados());String clave=registro.obtener(org,s.operacion()).orElseThrow().claveExterna();
        var actores=Executors.newFixedThreadPool(2);List<Future<?>> tareas=new ArrayList<>();
        try{
            for(int n=0;n<2;n++)tareas.add(actores.submit(()->receptor.registrarResultadoVerificado(org,s.operacion(),clave,salida.get())));
            for(var tarea:tareas)tarea.get(25,TimeUnit.SECONDS);
        }finally{cerrarActoresTest(actores,tareas,()->{});}
        assertEquals(2,pids.size(),"Dos transacciones simultáneas usan conexiones PG distintas antes del fence Core");
        servicio=receptorSinImagen(banco,registro,banco.resultados());assertEquals("RESULTADO_NO_DISPONIBLE",ejecutar(s).estado());
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals("NO_INVOCADA",estadoEtapa(s));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='SALIDA_SIN_BINARIO'",Integer.class,org,s.operacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,org,s.operacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND hecho='PREVISION_IA_NO_ALMACENADA'",Integer.class,org));
        assertEquals("NO_ALMACENADA",jdbc.queryForObject("SELECT estado FROM slot_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",String.class,org,s.operacion()));
        assertEquals(usados,uso());assertEquals(escritos,puts.get());assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());
    }
    /** HTTP/avisos/fondos TEST; FuenteCore/E44/coordinador/B0 reales. No demuestra IA ni QC reales. */
    @ParameterizedTest @Tag("QC_FACTURABLE_TEST")
    @ValueSource(strings={"REDACTAR_FICHA:REFUSAL","REDACTAR_FICHA:INCOMPLETE","ANALIZAR_FOTO:REFUSAL","ANALIZAR_FOTO:INCOMPLETE",
        "REDACTAR_FICHA:REFUSAL:RECUPERACION","ANALIZAR_FOTO:INCOMPLETE:RECUPERACION",
        "REDACTAR_FICHA:NULO","ANALIZAR_FOTO:NULO"})
    void respuestaTextoSinUtilidadConUsoConocidoNoDejaQcNuncaInvocadoPendiente(String caso)throws Exception{
        String[] partes=caso.split(":");Accion accion=Accion.valueOf(partes[0]);
        var foto=derivadaReal();var a=equipo.agente();long org=a.idOrganizacion();
        version=UUID.randomUUID().toString();
        String[] clases={"ENTRADA_NATIVE_TEST","CACHE_NATIVE_TEST","WRITE30_NATIVE_TEST","WRITE12_NATIVE_TEST","SALIDA_NATIVE_TEST","RAZONAMIENTO_NATIVE_TEST"};
        var nativas=Arrays.stream(clases).map(c->new Tarifa(c,"token",BigDecimal.ONE,new BigDecimal("1000000"))).toList();
        var referencia=new ArrayList<>(nativas);referencia.add(new Tarifa("unidades_test","u",BigDecimal.ONE,BigDecimal.ONE));
        tx.executeWithoutResult(t->{
            jdbc.update("INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado) VALUES('MATERIAL_IA_TEST',?,?,1,?::jsonb,4,1,2,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',true)",version,fondo,json.lista(referencia));
            jdbc.update("UPDATE b0_perfil_actual SET version=? WHERE perfil='MATERIAL_IA_TEST'",version);
        });
        String prefijo="controllocal.ia.material."+accion.name().toLowerCase(Locale.ROOT)+".";
        perfiles=new PerfilesMaterialIa(new MockEnvironment().withProperty(prefijo+"perfil","MATERIAL_IA_TEST").withProperty(prefijo+"version",version));
        var fuenteReal=contexto.getBean(FuentesMaterialIaCore.class);autoridad=new AutoridadMaterialIa(registro,json,List.of(fuenteReal),perfiles);
        b0=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,new DerechosDePlanB0(financiero,json),true);
        var ruta=new ConfiguracionRespuestasOpenAi.Ruta(accion,"MATERIAL_IA_TEST",version,"PROCESO_NATIVE_TEST",
            new ConfiguracionRespuestasOpenAi.Parametros("low",accion==Accion.ANALIZAR_FOTO?"auto":null,50),
            new ConfiguracionRespuestasOpenAi.Envoltura("ENV_TEST","a".repeat(64),100000,1000000,accion==Accion.ANALIZAR_FOTO?1000000:0,2000000,100000,10000,30),
            new ConfiguracionRespuestasOpenAi.ContratoUsage("USAGE_TEST","EVIDENCIA_SINTETICA_TEST","TARIFA_TEST",clases[0],clases[1],clases[2],clases[3],clases[4],clases[5],nativas));
        var cfg=new ConfiguracionRespuestasOpenAi(List.of(ruta),Duration.ofSeconds(2),Duration.ofSeconds(2));
        var body=new LinkedHashMap<String,Object>();body.put("id","resp_TEST_"+foto.operacion());body.put("model",ruta.modelo());body.put("service_tier","default");
        body.put("status",partes[1].equals("INCOMPLETE")?"incomplete":"completed");
        body.put("usage",Map.of("input_tokens",30,"output_tokens",40,"total_tokens",70,
            "input_tokens_details",Map.of("cached_tokens",0,"cache_write_tokens",0),"output_tokens_details",Map.of("reasoning_tokens",0)));
        Map<String,String> contenido=partes[1].equals("NULO")
            ?Map.of("type","output_text","text",json.objetoCanonico(Map.of("texto","detalle"+(char)0+"TEST")))
            :Map.of("type","refusal","refusal","TEST SIN RESPUESTA UTIL");
        body.put("output",List.of(Map.of("type","message","role","assistant","status","completed","content",List.of(contenido))));
        byte[] respuesta=json.objetoCanonico(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var adaptador=new ProveedorRespuestasOpenAi(cfg,()->"CLAVE_TEST_NO_REAL",(peticion,maximo,total,contextoIntento,observador)->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());llamadas.incrementAndGet();
            assertEquals(ProveedorRespuestasOpenAi.DESTINO,peticion.uri());assertTrue(respuesta.length<=maximo);
            return new TransporteOpenAi.Respuesta(200,java.net.http.HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(k,v)->true),respuesta);
        });
        var qc=controlFacturableTest(r->{throw new AssertionError("QC sin texto utilizable no puede invocarse");});
        var destinosPermitidos=Set.of(new DestinoTratamiento(adaptador.identificador(),"MATERIAL_IA_TEST",version),qc.destino("MATERIAL_IA_TEST",version));
        UUID originalFoto=accion==Accion.ANALIZAR_FOTO?foto.fotoOriginal():null;
        when(avisos.vigente(any(),eq(a))).thenAnswer(i->{EvidenciasTratamientoMaterialIa.AmbitoTratamiento ambito=i.getArgument(0);
            if(ambito.solicitud().accion()!=accion||!foto.recurso().equals(ambito.solicitud().recurso())||!Objects.equals(originalFoto,ambito.solicitud().fotoOriginal()))return Optional.empty();
            String texto="AVISO SINTETICO TEST SIN AUTORIZACION NI PROVEEDOR REAL";
            return Optional.of(new AvisosTratamientoMaterialIa.Contenido("AVISO-TEXTO-TEST:"+foto.operacion(),1,texto,Set.of(accion),ambito.categorias(),"LEGAL_TEST_SIN_RED","TEST-1",texto,"FUENTE_TEST",Idempotencia.huellaExacta(texto),destinosPermitidos));
        });
        org.mockito.stubbing.Answer<Optional<AvisosTratamientoMaterialIa.Legitimacion>> legit=i->{
            EvidenciasTratamientoMaterialIa.AmbitoTratamiento ambito=i.getArgument(0);AvisosTratamientoMaterialIa.Contenido aviso=i.getArgument(1);Actor actual=i.getArgument(2);
            if(!a.equals(actual)||ambito.solicitud().accion()!=accion||!foto.recurso().equals(ambito.solicitud().recurso())||!Objects.equals(originalFoto,ambito.solicitud().fotoOriginal()))return Optional.empty();
            return Optional.of(new AvisosTratamientoMaterialIa.Legitimacion("LEGITIMACION-TEXTO-TEST:"+foto.operacion(),"FUENTE_SINTETICA_TEST","e".repeat(64),foto.recurso(),Set.of(accion),ambito.categorias(),aviso.proveedorLegal(),aviso.condicionesVersion()));
        };
        when(avisos.legitimacionActual(any(),any(),eq(a))).thenAnswer(legit);when(avisos.legitimacionUsoArtefacto(any(),any(),eq(a))).thenAnswer(legit);
        var presupuestoNormal=b0;boolean recuperar=partes.length>2;var interrumpir=new AtomicBoolean(recuperar);
        if(recuperar){
            b0=spy(b0);doAnswer(i->{
                if(interrumpir.compareAndSet(true,false))throw new IllegalStateException("TEST interrupción antes del CAS de QC nunca reclamado");
                return i.callRealMethod();
            }).when(b0).acreditarEtapaNoReclamada(any(),eq(ControlesMaterialIa.ETAPA_FINANCIERA),any());
        }
        var reales=new ResultadosMaterialIaAlmacen(contexto.getBean(DerivadasMaterialIaService.class),fuenteReal,contexto.getBean(SalidaBinariaPersistente.class),contexto.getBean(CuotaArchivosB0.class),almacen,contexto.getBean(UsoAlmacenamientoB0.class),b0);
        servicio=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(adaptador),List.of(qc),List.of(reales),gestor);
        var inicial=new Solicitud(foto.operacion(),accion,foto.recurso(),null,originalFoto,null);var aviso=fuenteReal.presentarTratamiento(inicial,a,sesion);
        contexto.getBean(EvidenciasTratamientoMaterialIa.class).capturar(new EvidenciasTratamientoMaterialIa.Captura(UUID.randomUUID(),aviso.referencia(),aviso.versionCobertura(),EvidenciasTratamientoMaterialIa.Decision.ACEPTAR),a,sesion,Procedencia.deLaPantalla());
        var preparada=servicio.preparar(inicial,a,sesion);var s=new Solicitud(inicial.operacion(),accion,inicial.recurso(),preparada.versionOrigen(),originalFoto,null);
        int escritos=puts.get();try{ejecutar(s);}catch(RuntimeException falloEsperado){assertNotNull(falloEsperado.getMessage());}
        if(recuperar){
            assertFalse(interrumpir.get(),"Debe alcanzarse el punto de interrupción previsto, no otro fallo.");
            assertEquals("FALLO_RESULTADO",estado(s));assertEquals("EN_PROCESO",estadoB0(s));assertEquals("PENDIENTE",estadoEtapa(s));
            // Nuevos objetos desde PG; no simula otro proceso ni otra JVM.
            autoridad=new AutoridadMaterialIa(registro,json,List.of(fuenteReal),perfiles);
            servicio=new MaterialIaServiceImpl(registro,presupuestoNormal,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(adaptador),List.of(qc),List.of(reales),gestor);
            assertEquals("RESULTADO_NO_DISPONIBLE",ejecutar(s).estado());
        }
        assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());assertEquals(escritos,puts.get());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=? AND estado_costo='CALCULADO'",Integer.class,org,s.operacion()));
        assertAll(()->assertEquals("FALLO_RESULTADO",estado(s)),()->assertEquals("LIQUIDADA",estadoB0(s)),()->assertEquals("NO_INVOCADA",estadoEtapa(s)));
        assertEquals("RESULTADO_NO_DISPONIBLE",ejecutar(s).estado());assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='QC_CLAIM'",Integer.class,org,s.operacion()));
    }
    @Test void falloRecepcionDeFotoConocidaNoAutorizaLiberarPorEstadoGenerico()throws Exception{
        var s=derivadaReal();long org=equipo.agente().idOrganizacion();var reales=spy(resultadosReales());
        doThrow(new IllegalStateException("TEST recepción interrumpida antes de su writer")).when(reales).recibirResultado(anyLong(),any(),anyString(),any());
        servicio=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(proveedor),List.of(control),List.of(reales),gestor);
        assertThrows(IllegalStateException.class,()->ejecutar(s));assertEquals("FALLO_RESULTADO",estado(s));
        assertTrue(registro.ultimoHecho(org,s.operacion(),"SALIDA_SIN_BINARIO").isEmpty());
        reales.resolverSinArchivo(org,s.operacion(),"FALLO_RESULTADO");
        assertEquals("RESERVADA",jdbc.queryForObject("SELECT estado FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND id_reserva=?",String.class,org,UsoAlmacenamientoB0.referenciaPrevision(org,s.operacion()).reserva()));
        assertEquals("GENERADA",jdbc.queryForObject("SELECT estado FROM slot_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",String.class,org,s.operacion()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND hecho='PREVISION_IA_NO_ALMACENADA'",Integer.class,org));
    }
    @Test void derivadaDurableComputaDerivadaIaUnaVezYDescargaSinClave()throws Exception{
        var s=derivadaReal();long antes=uso();int escritos=puts.get();var r=ejecutar(s);var real=resultadosReales();
        byte[] esperado=imagen(0xff0000);
        assertEquals("DISPONIBLE",r.estado());assertArrayEquals(esperado,real.descargar(s.operacion(),r.archivo(),equipo.agente(),sesion).bytes());
        assertEquals(antes+esperado.length,uso());assertEquals(escritos+1,puts.get());assertEquals(r,ejecutar(s));assertEquals(1,llamadas.get());
        var entrega=real.descargar(s.operacion(),r.archivo(),equipo.agente(),sesion);byte[] copia=entrega.bytes();copia[0]^=1;assertArrayEquals(esperado,entrega.bytes());
        String publico=json.objetoCanonico(entrega);assertFalse(publico.contains(claveDerivada(s)));assertFalse(publico.contains(namespace));assertFalse(publico.contains("bytes"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND categoria='DERIVADA_IA' AND estado='CONFIRMADA'",Integer.class,equipo.agente().idOrganizacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM foto_propiedad WHERE organizacion_id=? AND id_propiedad=?",Integer.class,equipo.agente().idOrganizacion(),Long.parseLong(s.recurso().referencia())));
    }
    @Test void dosJobsDeMismoOriginalConservanVariantesYCuotaIndependientes()throws Exception{
        var s=derivadaReal();long antes=uso();var a=ejecutar(s);
        var otro=new Solicitud(UUID.randomUUID(),s.accion(),s.recurso(),s.versionOrigen(),s.fotoOriginal(),s.instruccion());var b=ejecutar(otro);
        assertNotEquals(a.archivo(),b.archivo());assertNotEquals(claveDerivada(s),claveDerivada(otro));assertEquals(2,llamadas.get());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        assertEquals(antes+2L*imagen(0xff0000).length,uso());
    }
    @Test void dosClaimsConcurrentesReservanLosDosSlotsYElTerceroNoConsumeNiBorra()throws Exception{
        var primera=derivadaReal();var segunda=new Solicitud(UUID.randomUUID(),primera.accion(),primera.recurso(),primera.versionOrigen(),primera.fotoOriginal(),null);
        var tercera=new Solicitud(UUID.randomUUID(),primera.accion(),primera.recurso(),primera.versionOrigen(),primera.fotoOriginal(),null);
        var dentro=new CountDownLatch(2);var terminar=new CountDownLatch(1);var devolver=ejecucion;
        ejecucion=p->{dentro.countDown();esperar(terminar,"No se liberaron los dos proveedores TEST");return devolver.apply(p);};
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->ejecutar(primera));var b=pool.submit(()->ejecutar(segunda));
            try{
                esperar(dentro,"Los dos claims deben estar comprometidos y fuera de SQL");
                assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM slot_derivada_material_ia WHERE organizacion_id=? AND original_ref=? AND estado='RESERVADA'",Integer.class,equipo.agente().idOrganizacion(),primera.fotoOriginal()));
                var exceso=assertThrows(ConflictoException.class,()->ejecutar(tercera));assertTrue(exceso.getMessage().contains("dos temporales"));
                assertTrue(financiero.operacion(equipo.agente().idOrganizacion(),tercera.operacion()).isEmpty());assertEquals(2,llamadas.get());
                assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=? AND estado='A'",Integer.class,equipo.agente().idOrganizacion(),primera.fotoOriginal()));
            }finally{terminar.countDown();}
            assertEquals("DISPONIBLE",a.get(30,TimeUnit.SECONDS).estado());assertEquals("DISPONIBLE",b.get(30,TimeUnit.SECONDS).estado());
        }finally{terminar.countDown();}
        assertEquals(2,llamadas.get());assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
    }
    @ParameterizedTest @ValueSource(strings={"OBSERVADA","RECHAZADA"})
    void qcNoEntregableConRecepcionRealRetieneCuotaHastaAusenciaSinFingirNoWriter(String valor)throws Exception{
        var s=derivadaReal();long antes=uso();int escritos=puts.get();decision=Decision.valueOf(valor);
        assertEquals("REQUIERE_REVISION",ejecutar(s).estado());assertEquals("LIQUIDADA",estadoB0(s));assertEquals(1,llamadas.get());assertEquals(escritos+1,puts.get());
        assertEquals("GENERADA",jdbc.queryForObject("SELECT estado FROM slot_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",String.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertEquals(antes+imagen(0xff0000).length,uso());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertFalse(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM retencion_material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND causa='NO_ALMACENADO_COMPROBADO')",Boolean.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertEquals(0L,jdbc.queryForObject("SELECT reservados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,equipo.agente().idOrganizacion()));
        var real=resultadosReales();var v=real.version(s.operacion(),equipo.agente());assertFalse(v.disponible());
        var denegada=assertThrows(ConflictoException.class,()->real.descargar(s.operacion(),v.archivo(),equipo.agente(),sesion));
        assertEquals("El job no conserva mejora controlada y conciliada.",denegada.getMessage());
        var retirada=real.descartarTemporal(s.operacion(),v.archivo(),v.revision(),"rechazo-qc-"+UUID.randomUUID(),equipo.agente(),sesion);
        assertEquals("RETIRADA_VERIFICADA",retirada.estadoGestion());assertEquals(antes,uso());assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));
    }
    @Test void tercerTemporalTrasDosDisponiblesNoSeAdmiteYGuardarAlternativaNoDuplicaCuota()throws Exception{
        var s=derivadaReal();var primero=ejecutar(s);var otro=new Solicitud(UUID.randomUUID(),s.accion(),s.recurso(),s.versionOrigen(),s.fotoOriginal(),null);ejecutar(otro);
        var tercero=new Solicitud(UUID.randomUUID(),s.accion(),s.recurso(),s.versionOrigen(),s.fotoOriginal(),null);assertThrows(ConflictoException.class,()->ejecutar(tercero));
        var real=resultadosReales();long antes=uso();int escritos=puts.get();var v=real.version(s.operacion(),equipo.agente());
        var guardada=real.guardarAlternativa(s.operacion(),primero.archivo(),v.revision(),"guardar-"+UUID.randomUUID(),equipo.agente(),sesion);
        assertTrue(guardada.guardada());assertNull(guardada.venceEn());assertEquals(antes,uso());assertEquals(escritos,puts.get());
        assertEquals("DISPONIBLE",ejecutar(tercero).estado());assertEquals(3,llamadas.get());assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
    }
    @ParameterizedTest @ValueSource(strings={"BYTES","MIME","NAMESPACE","FALTA","REVOCACION"})
    void descargaRechazaAlteracionAusenciaOCoberturaRevocadaSinRevertirGasto(String caso)throws Exception{
        var s=derivadaReal();var r=ejecutar(s);long antes=uso();String clave=claveDerivada(s);var b=objetos.get(clave);
        switch(caso){case "BYTES"->objetos.put(clave,new AlmacenDocumentos.ArchivoDescargado(new byte[]{0},"TEST","image/png"));
            case "MIME"->objetos.put(clave,new AlmacenDocumentos.ArchivoDescargado(b.contenido(),"TEST","image/jpeg"));
            case "NAMESPACE"->namespace="b".repeat(64);case "FALTA"->objetos.remove(clave);case "REVOCACION"->alAbrir=()->acreditada=false;}
        assertThrows(RuntimeException.class,()->resultadosReales().descargar(s.operacion(),r.archivo(),equipo.agente(),sesion));
        assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));assertEquals("DISPONIBLE",estado(s));assertEquals(antes,uso());
        assertNotNull(registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow().control());
    }
    @Test void descargaNoAdmiteArchivoDeOtroJobNiActorSinAutoridadActualSobreRecurso()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();
        assertThrows(NoEncontradoException.class,()->real.descargar(s.operacion(),UUID.randomUUID(),equipo.agente(),sesion));
        var colega=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).sesion(equipo.colega());
        assertThrows(NoEncontradoException.class,()->real.descargar(s.operacion(),r.archivo(),equipo.colega(),colega));assertEquals(1,llamadas.get());
    }
    @Test void rollbackDeVinculoConservaMedicionYRecuperaSinOtroProveedor()throws Exception{
        var s=derivadaReal();long antes=uso();
        alGuardar=()->TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void beforeCommit(boolean readOnly){throw new IllegalStateException("TEST rollback del vínculo");}});
        assertThrows(RuntimeException.class,()->ejecutar(s));assertEquals("LIQUIDADA",estadoB0(s));assertEquals("FALLO_RESULTADO",estado(s));assertEquals(antes,uso());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        alGuardar=()->{};var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();
        var r=servicio.recuperarResultadoDurable(o.organizacion(),o.id(),equipo.agente(),sesion);
        assertEquals("DISPONIBLE",r.estado());assertEquals(1,llamadas.get());assertEquals(antes+imagen(0xff0000).length,uso());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND categoria='DERIVADA_IA' AND estado='CONFIRMADA'",Integer.class,o.organizacion()));
    }
    @Test void vinculoDurableNoSeSobrescribeNiSeElimina()throws Exception{
        var s=derivadaReal();ejecutar(s);long org=equipo.agente().idOrganizacion();
        historialInmutable(()->jdbc.update("UPDATE derivada_material_ia SET archivo_ref=? WHERE organizacion_id=? AND id_operacion=?",UUID.randomUUID(),org,s.operacion()));
        historialInmutable(()->jdbc.update("DELETE FROM derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",org,s.operacion()));
        historialInmutable(()->tx.executeWithoutResult(t->{try{jdbc.execute("TRUNCATE derivada_material_ia CASCADE");}finally{t.setRollbackOnly();}}));
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals(1,llamadas.get());
    }
    @Test void eleccionHumanaRevisableNoReprocesaNiCambiaArchivoOCobro()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();long usados=uso();int escritos=puts.get();
        var inicial=real.seleccionActual(s.fotoOriginal(),equipo.agente(),sesion);assertNull(inicial.operacion());assertEquals(0,inicial.revision());assertEquals(s.fotoOriginal(),inicial.original());
        String a="eleccion-"+UUID.randomUUID(),b="eleccion-"+UUID.randomUUID(),c="eleccion-"+UUID.randomUUID();
        var conservada=real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),0,a,equipo.agente(),sesion);
        assertEquals(s.operacion(),conservada.operacion());assertEquals(1,conservada.revision());
        assertEquals(1,tx.execute(t->real.bloquearSelecciones(List.of(s.fotoOriginal()),equipo.agente())).getFirst().revision());
        var soloLectura=new TransactionTemplate(gestor);soloLectura.setReadOnly(true);
        String identidad=Idempotencia.huellaExacta("BROX-MATERIAL-IA-LOCK\u0000"+equipo.agente().idOrganizacion()+"\u0000"+s.operacion());
        long lock=Long.parseUnsignedLong(identidad.substring(0,16),16);
        try(var inspector=Executors.newSingleThreadExecutor()){
            soloLectura.executeWithoutResult(t->{
                assertEquals(1,real.consultarSelecciones(List.of(s.fotoOriginal()),equipo.agente()).getFirst().revision());
                assertThrows(IllegalStateException.class,()->real.bloquearSelecciones(List.of(s.fotoOriginal()),equipo.agente()));
                try{assertTrue(Boolean.TRUE.equals(inspector.submit(()->tx.execute(x->jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,lock))).get(10,TimeUnit.SECONDS)));}
                catch(Exception fallo){throw new AssertionError(fallo);}
            });
        }
        var noUsar=real.seleccionar(s.fotoOriginal(),null,null,1,b,equipo.agente(),sesion);
        assertNull(noUsar.operacion());assertEquals(2,noUsar.revision());
        assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->{real.bloquearSelecciones(List.of(s.fotoOriginal()),equipo.agente());real.exigirMismasSelecciones(List.of(conservada),equipo.agente());}));
        var archivo=real.exigirArchivo(s.operacion(),r.archivo(),equipo.agente());assertNotNull(archivo.originalRegistradoEn());assertEquals(s.fotoOriginal(),archivo.original());
        assertEquals(r.archivo(),archivo.archivo());assertNotNull(real.descargar(s.operacion(),r.archivo(),equipo.agente(),sesion));
        var recuperada=real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),2,c,equipo.agente(),sesion);
        assertEquals(3,recuperada.revision());assertEquals(r.archivo(),recuperada.archivo());
        assertEquals(conservada,real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),0,a,equipo.agente(),sesion));
        assertEquals(3,real.seleccionActual(s.fotoOriginal(),equipo.agente(),sesion).revision());
        assertThrows(ConflictoException.class,()->real.seleccionar(s.fotoOriginal(),null,null,0,a,equipo.agente(),sesion));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM seleccion_material_ia_evento WHERE organizacion_id=? AND original_ref=?",Integer.class,equipo.agente().idOrganizacion(),s.fotoOriginal()));
        assertEquals(usados,uso());assertEquals(escritos,puts.get());assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));
        metadataInmutable(()->jdbc.update("DELETE FROM seleccion_material_ia_evento WHERE organizacion_id=?",equipo.agente().idOrganizacion()));
        metadataInmutable(()->tx.executeWithoutResult(t->{try{jdbc.execute("TRUNCATE seleccion_material_ia_evento CASCADE");}finally{t.setRollbackOnly();}}));
    }
    @Test void noUsarNoExigeTransferenciaPositivaNiLeeBytesTrasRevocacion()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();long antes=uso();
        real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),0,"seleccion-"+UUID.randomUUID(),equipo.agente(),sesion);acreditada=false;
        assertThrows(CapacidadDeshabilitadaException.class,()->real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),1,"revocada-"+UUID.randomUUID(),equipo.agente(),sesion));
        assertEquals(1,jdbc.queryForObject("SELECT revision FROM seleccion_material_ia WHERE organizacion_id=? AND original_ref=?",Long.class,equipo.agente().idOrganizacion(),s.fotoOriginal()));clearInvocations(almacen);
        var noUsar=real.seleccionar(s.fotoOriginal(),null,null,1,"no-usar-"+UUID.randomUUID(),equipo.agente(),sesion);assertNull(noUsar.operacion());assertEquals(2,noUsar.revision());
        verifyNoInteractions(almacen);assertEquals(antes,uso());assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));
    }
    @ParameterizedTest @ValueSource(strings={"TEMPORAL","GUARDADA"})
    void retiradaExpresaVerificadaLiberaUnaVezSinReembolsoNiAlterarOriginal(String tipo)throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();var a=equipo.agente();
        var v=real.version(s.operacion(),a);if(tipo.equals("GUARDADA"))v=real.guardarAlternativa(s.operacion(),r.archivo(),v.revision(),"guardar-"+UUID.randomUUID(),a,sesion);
        long antes=uso();long revision=v.revision();String clave="retirar-"+UUID.randomUUID();String privada=claveDerivada(s);clearInvocations(almacen);
        if(tipo.equals("GUARDADA"))assertThrows(ConflictoException.class,()->real.descartarTemporal(s.operacion(),r.archivo(),revision,"no-temporal",a,sesion));
        var retirada=tipo.equals("GUARDADA")?real.retirarAlternativa(s.operacion(),r.archivo(),revision,clave,a,sesion):real.descartarTemporal(s.operacion(),r.archivo(),revision,clave,a,sesion);
        assertEquals("RETIRADA_VERIFICADA",retirada.estadoGestion());assertFalse(retirada.disponible());assertFalse(objetos.containsKey(privada));assertEquals(antes-imagen(0xff0000).length,uso());
        var repetida=tipo.equals("GUARDADA")?real.retirarAlternativa(s.operacion(),r.archivo(),revision,clave,a,sesion):real.descartarTemporal(s.operacion(),r.archivo(),revision,clave,a,sesion);
        assertEquals(retirada,repetida);verify(almacen).eliminar(privada);assertEquals("LIQUIDADA",estadoB0(s));assertEquals(1,llamadas.get());
        assertThrows(NoEncontradoException.class,()->real.descargar(s.operacion(),r.archivo(),a,sesion));assertEquals(r.archivo(),real.exigirArchivo(s.operacion(),r.archivo(),a).archivo());
        assertEquals("RESULTADO_NO_DISPONIBLE",servicio.consultar(s.operacion(),a,sesion).estado());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=? AND estado='A'",Integer.class,a.idOrganizacion(),s.fotoOriginal()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND hecho='DERIVADA_IA_RETIRADA_VERIFICADA'",Integer.class,a.idOrganizacion()));
    }
    @Test void rollbackDespuesDeleteMantieneCuotaYSlotHastaRecuperarLaMismaAusencia()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();var a=equipo.agente();long antes=uso();var v=real.version(s.operacion(),a);String clave="retiro-rollback-"+UUID.randomUUID(),privada=claveDerivada(s);
        CuotaArchivosB0 cuotaReal=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(contexto.getBean(CuotaArchivosB0.class));
        var cuota=spy(cuotaReal);
        doThrow(new IllegalStateException("TEST rollback SQL después DELETE")).when(cuota).liberarRetiradaVerificada(any(),any());
        var fallido=new ResultadosMaterialIaAlmacen(contexto.getBean(DerivadasMaterialIaService.class),fuente,contexto.getBean(SalidaBinariaPersistente.class),cuota,almacen,contexto.getBean(UsoAlmacenamientoB0.class),b0);
        clearInvocations(almacen);assertThrows(IllegalStateException.class,()->fallido.descartarTemporal(s.operacion(),r.archivo(),v.revision(),clave,a,sesion));
        assertFalse(objetos.containsKey(privada));assertEquals(antes,uso());assertEquals("RETIRADA_PENDIENTE",real.version(s.operacion(),a).estadoGestion());
        assertEquals("INTENCION",jdbc.queryForObject("SELECT estado FROM retirada_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",String.class,a.idOrganizacion(),s.operacion()));
        assertEquals("RETIRADA_VERIFICADA",real.descartarTemporal(s.operacion(),r.archivo(),v.revision(),clave,a,sesion).estadoGestion());
        assertEquals(antes-imagen(0xff0000).length,uso());verify(almacen).eliminar(privada);assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));
    }
    @Test void seleccionadaNoSeRetiraYUnArchivoOriginalNoEsUnaAlternativa()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();var a=equipo.agente();real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),0,"usar",a,sesion);var v=real.version(s.operacion(),a);long antes=uso();
        assertThrows(ConflictoException.class,()->real.descartarTemporal(s.operacion(),r.archivo(),v.revision(),"retirar-en-uso",a,sesion));
        assertThrows(NoEncontradoException.class,()->real.retirarAlternativa(s.operacion(),s.fotoOriginal(),v.revision(),"no-original",a,sesion));
        assertEquals(antes,uso());assertArrayEquals(imagen(0xff0000),real.descargar(s.operacion(),r.archivo(),a,sesion).bytes());assertEquals(1,llamadas.get());
    }
    @ParameterizedTest @ValueSource(strings={"SID","NAMESPACE"})
    void denegacionFotoPostclaimAntesProveedorResuelvePrevisionSinWriterNiMedicion(String causa)throws Exception{
        var s=derivadaReal();long antes=uso();int escritos=puts.get();String namespacePrevio=namespace;
        if(causa.equals("SID")){
            var presupuesto=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,new DerechosDePlanB0(financiero,json),true){
                @Override public boolean iniciar(PresupuestoIaService.Solicitud p,Actor a,ContextoSesion sid){boolean claim=super.iniciar(p,a,sid);if(claim)contexto.getBean(ContextosService.class).cerrar(sid.referenciaSesion(),sid.revisionContexto());return claim;}
            };
            servicio=new MaterialIaServiceImpl(registro,presupuesto,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(proveedor),List.of(control),List.of(resultadosReales()),gestor);
        }else antesDeTransferencia=()->namespace="e".repeat(64);
        Class<? extends Throwable> esperada=causa.equals("SID")?AccesoNoAutorizadoException.class:ConflictoException.class;
        var denegacion=assertThrows(esperada,()->ejecutar(s));
        assertEquals(0,denegacion.getSuppressed().length,()->{
            var texto=new java.io.StringWriter();denegacion.printStackTrace(new java.io.PrintWriter(texto));
            return "La denegación esperada conserva una avería suprimida de cancelación/liberación:\n"+texto;
        });
        assertEquals("CANCELADA",estadoB0(s));assertEquals("CANCELADA",estado(s));assertEquals(0,llamadas.get());assertEquals(escritos,puts.get());assertEquals(antes,uso());
        assertEquals("NO_ALMACENADA",jdbc.queryForObject("SELECT estado FROM slot_derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",String.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertEquals(0L,jdbc.queryForObject("SELECT reservados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,equipo.agente().idOrganizacion()));
        assertTrue(financiero.idsMediciones(equipo.agente().idOrganizacion(),s.operacion()).isEmpty());
        namespace=namespacePrevio;antesDeTransferencia=()->{};if(causa.equals("SID"))sesion=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).sesion(equipo.agente());
        assertEquals("NO_PROCESADA",ejecutar(s).estado());assertEquals(0,llamadas.get());
    }
    @Test void fuenteHistoricaTestAgrupaDosTemporalesYNoReabreUnaCausaSoloPorUnaBajadaDelGrupo()throws Exception{
        var foto=derivadaReal();var banco=bancoFuenteReal(foto);var a=equipo.agente();long propiedad=Long.parseLong(foto.recurso().referencia());
        var primero=banco.generarConObservacionHistoricaTEST(propiedad,foto.fotoOriginal(),a,sesion,Instant.now().minus(Duration.ofDays(6)).minus(Duration.ofHours(23)));
        var segundo=banco.generarConObservacionHistoricaTEST(propiedad,foto.fotoOriginal(),a,sesion,Instant.now().minus(Duration.ofDays(6)).minus(Duration.ofHours(22)));
        var real=contexto.getBean(ResultadosMaterialIa.class);var dos=real.avisosDeCaducidad(a).getFirst();assertEquals(2,dos.versiones().size());
        assertTrue(Boolean.TRUE.equals(tx.execute(t->real.registrarCausaCaducidad(a.idOrganizacion(),dos))));
        var v=real.version(segundo.solicitud().operacion(),a);long antes=uso();real.guardarAlternativa(v.operacion(),v.archivo(),v.revision(),"guardar-sin-caducidad",a,sesion);
        var uno=real.avisosDeCaducidad(a).getFirst();assertEquals(1,uno.versiones().size());assertEquals(primero.solicitud().operacion(),uno.versiones().getFirst().operacion());
        assertTrue(Boolean.TRUE.equals(tx.execute(t->real.registrarCausaCaducidad(a.idOrganizacion(),uno))));assertNotEquals(dos.huellaCausa(),uno.huellaCausa());
        assertEquals(ResultadosMaterialIa.EstadoCausaCaducidad.VIGENTE,real.estadoCausaCaducidad(a.idOrganizacion(),foto.fotoOriginal(),dos.huellaCausa(),uno.huellaCausa()));
        assertEquals(ResultadosMaterialIa.EstadoCausaCaducidad.VIGENTE,real.estadoCausaCaducidad(a.idOrganizacion(),foto.fotoOriginal(),uno.huellaCausa(),dos.huellaCausa()));
        assertEquals(antes,uso());assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=?",Integer.class,a.idOrganizacion()));
        metadataInmutable(()->jdbc.update("DELETE FROM causa_caducidad_material_ia WHERE organizacion_id=?",a.idOrganizacion()));
    }
    @Test void fuenteHistoricaTestVencidaDeniegaBytesSinCronYRetiraSoloConAusenciaVerificada()throws Exception{
        var foto=derivadaReal();var banco=bancoFuenteReal(foto);var a=equipo.agente();long propiedad=Long.parseLong(foto.recurso().referencia());
        var caso=banco.generarConObservacionHistoricaTEST(propiedad,foto.fotoOriginal(),a,sesion,Instant.now().minus(Duration.ofDays(8)));
        var real=contexto.getBean(ResultadosMaterialIa.class);var v=real.version(caso.solicitud().operacion(),a);long antes=uso();int escritos=puts.get();
        assertEquals("RESULTADO_NO_DISPONIBLE",caso.resultado().estado());assertEquals("VENCIDA",v.estadoGestion());
        assertThrows(NoEncontradoException.class,()->real.descargar(v.operacion(),v.archivo(),a,sesion));
        assertThrows(NoEncontradoException.class,()->real.seleccionar(foto.fotoOriginal(),v.operacion(),v.archivo(),0,"no-resucitar-vencida",a,sesion));
        assertEquals(antes,uso());assertEquals(0L,real.seleccionActual(foto.fotoOriginal(),a,sesion).revision());
        var lote=real.candidatosConservacion(namespace,null,10);assertTrue(lote.candidatos().stream().anyMatch(c->c.operacion().equals(v.operacion())));
        real.conciliarCaducidad(a.idOrganizacion(),v.operacion());assertEquals("RETIRADA_VERIFICADA",real.version(v.operacion(),a).estadoGestion());
        assertEquals(antes-caso.salida().length,uso());assertEquals(escritos,puts.get());real.conciliarCaducidad(a.idOrganizacion(),v.operacion());assertEquals(antes-caso.salida().length,uso());
        assertEquals(v.archivo(),real.exigirArchivo(v.operacion(),v.archivo(),a).archivo());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=?",Integer.class,a.idOrganizacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=? AND estado='A'",Integer.class,a.idOrganizacion(),foto.fotoOriginal()));
    }
    @Test void confirmacionExigePropietarioYRecepcionRealPermitePromoverSinDuplicarCuota()throws Exception{
        var s=derivadaReal();var interrumpido=spy(resultadosReales());
        doThrow(new IllegalStateException("TEST antes del escritor con previsión ya comprometida")).when(interrumpido).guardar(any(),any(),any(),any(),any(),any());
        servicio=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(proveedor),List.of(control),List.of(interrumpido),gestor);
        assertThrows(IllegalStateException.class,()->ejecutar(s));long antes=uso();var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();
        var f=json.comoObjeto(o.snapshot(),Snapshot.class);var salida=resultadosReales().recuperarRecibido(o.organizacion(),o.id(),o.claveExterna());var qc=json.comoObjeto(o.control(),Control.class);
        var core=contexto.getBean(DerivadasMaterialIaService.class);var cuota=contexto.getBean(CuotaArchivosB0.class);var bridge=contexto.getBean(SalidaBinariaPersistente.class);
        var confirmacion=contexto.getBean(com.controllocal.service.planes.ConfirmacionAlmacenamientoB0.class);var ref=UsoAlmacenamientoB0.referenciaPrevision(o.organizacion(),o.id());
        var sinPropietario=assertThrows(ConflictoException.class,()->tx.executeWithoutResult(t->confirmacion.confirmar(ref,
                new UsoAlmacenamientoB0.ComprobacionObjeto(namespace+":SIN-PROPIETARIO-TEST",salida.foto().length,com.controllocal.service.documentos.EvaluacionTecnicaFoto.huella(salida.foto()),"TEST"),"SIN-PROPIETARIO-TEST")));
        assertTrue(sinPropietario.getMessage().contains("vínculo autorizado"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=?",Integer.class,o.organizacion()));
        int antesPut=puts.get();var promovida=core.guardar(s,f,salida,qc,equipo.agente(),sesion,p->{
            var c=new CuotaArchivosB0.Contexto(p.recursoCuota(),equipo.agente(),sesion);
            var prevision=UsoAlmacenamientoB0.referenciaPrevision(p.organizacion(),s.operacion());
            var g=bridge.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(p.organizacion()),p.operacionIo(),p.fuente(),p.extension(),p.contentType(),salida.foto(),cuota.admision(c,prevision));
            contexto.getBean(com.controllocal.service.planes.ConfirmacionAlmacenamientoB0.class).confirmar(
                    prevision,
                    new com.controllocal.service.planes.UsoAlmacenamientoB0.ComprobacionObjeto(g.namespaceHash()+":"+g.clave(),g.bytes(),g.huella(),"TEST-OBJETO-VERIFICADO"),g.clave());
            return new DerivadasMaterialIaService.ObjetoVerificado(g.numeroIntento(),g.namespaceHash(),g.clave(),g.huella(),g.bytes());
        });
        assertEquals(registro.custodia(o.organizacion(),o.id()).orElseThrow().archivo(),promovida.archivo());assertEquals(antesPut,puts.get());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=?",Integer.class,o.organizacion()));
        assertEquals(antes,uso());assertEquals("LIQUIDADA",estadoB0(s));assertNotNull(registro.obtener(o.organizacion(),o.id()).orElseThrow().control());assertEquals(1,llamadas.get());
    }
    @Test void lecturaInternaDePiezaSeUneALaTransaccionRealSinOtraGeneracion()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();
        assertThrows(IllegalStateException.class,()->real.leerParaPieza(s.operacion(),r.archivo(),equipo.agente(),sesion));
        var antes=new AtomicLong();var durante=new AtomicLong();
        var b=tx.execute(t->{antes.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
            alAbrir=()->durante.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
            try{assertThrows(IllegalStateException.class,()->real.descargar(s.operacion(),r.archivo(),equipo.agente(),sesion));
                return real.leerParaPieza(s.operacion(),r.archivo(),equipo.agente(),sesion);
            }finally{alAbrir=()->{};}
        });
        assertNotNull(b);assertArrayEquals(imagen(0xff0000),b.bytes());assertEquals(antes.get(),durante.get());assertEquals(1,llamadas.get());
    }
    @Test void dosEleccionesConcurrentesSobreMismaRevisionConservanUnSoloEvento()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var inicio=new CountDownLatch(1);var real=resultadosReales();
        Callable<Boolean> decidir=()->{assertTrue(inicio.await(10,TimeUnit.SECONDS));try{
            real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),0,"concurrente-"+UUID.randomUUID(),equipo.agente(),sesion);return true;
        }catch(ConflictoException conflicto){return false;}};
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(decidir);var b=pool.submit(decidir);inicio.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));}
        assertEquals(1,real.seleccionActual(s.fotoOriginal(),equipo.agente(),sesion).revision());assertEquals(1,llamadas.get());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM seleccion_material_ia_evento WHERE organizacion_id=? AND original_ref=?",Integer.class,equipo.agente().idOrganizacion(),s.fotoOriginal()));
    }
    @Test void recuperacionesConcurrentesDelMismoArchivoNoDuplicanPutVinculoOCuota()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();long usados=uso();int escritos=puts.get();
        var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();var f=json.comoObjeto(o.snapshot(),Snapshot.class);
        var qc=json.comoObjeto(o.control(),Control.class);var salida=real.recuperarRecibido(o.organizacion(),o.id(),o.claveExterna());var inicio=new CountDownLatch(1);
        Callable<ResultadosMaterialIa.Guardado> recuperar=()->{assertTrue(inicio.await(10,TimeUnit.SECONDS));return real.guardar(s,f,salida,qc,equipo.agente(),sesion);};
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(recuperar);var b=pool.submit(recuperar);inicio.countDown();
            assertEquals(r.archivo(),a.get(30,TimeUnit.SECONDS).archivo());assertEquals(r.archivo(),b.get(30,TimeUnit.SECONDS).archivo());}
        assertEquals(escritos,puts.get());assertEquals(usados,uso());assertEquals(1,llamadas.get());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",Integer.class,o.organizacion(),o.id()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND categoria='DERIVADA_IA' AND estado='CONFIRMADA'",Integer.class,o.organizacion()));
    }
    @Test void otraFotoNoOcultaDerivadoExistenteParaNuevaPiezaNiPermiteOtraInferenciaObsoleta()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();var actor=equipo.agente();
        real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),0,"seleccion-test-"+UUID.randomUUID(),actor,sesion);
        // Foto B y la fuente de Core son reales; proveedor/QC y cobertura positiva del job siguen siendo TEST.
        var fuenteCore=contexto.getBean(FuentesMaterialIaCore.class);var preparada=fuenteCore.preparar(s,actor,sesion);
        var otra=contexto.getBean(GaleriaDeFotos.class).agregar(Long.parseLong(s.recurso().referencia()),"foto-b.png",
                Base64.getEncoder().encodeToString(imagen(0x00ff00)),FotoPropiedadService.TOPE_UNIVERSAL,actor);
        assertNotEquals(s.fotoOriginal(),UUID.fromString(otra.clave()));
        assertThrows(ConflictoException.class,()->fuenteCore.revalidar(s,preparada,actor));
        fuenteCore.exigirLecturaHistorica(s,preparada,actor);
        long antes=uso();int escritos=puts.get();byte[] derivada=imagen(0xff0000);fresca=false;
        assertEquals(r,servicio.consultar(s.operacion(),equipo.agente(),sesion));assertEquals(r,ejecutar(s));servicio.exigirLectura(s.operacion(),equipo.agente());
        assertArrayEquals(imagen(0xff0000),real.descargar(s.operacion(),r.archivo(),equipo.agente(),sesion).bytes());
        assertEquals(r.archivo(),real.exigirArchivo(s.operacion(),r.archivo(),equipo.agente()).archivo());
        var diagnostico=new TransactionTemplate(gestor);diagnostico.setReadOnly(true);
        assertEquals(r.archivo(),diagnostico.execute(t->real.consultarSelecciones(List.of(s.fotoOriginal()),actor)).getFirst().archivo());
        var contenido=tx.execute(t->{
            var seleccion=real.bloquearSelecciones(List.of(s.fotoOriginal()),actor).getFirst();
            assertEquals(s.fotoOriginal(),seleccion.original());
            assertEquals(com.controllocal.service.documentos.EvaluacionTecnicaFoto.huella(derivada),real.exigirArchivo(s.operacion(),r.archivo(),actor).huella());
            return real.leerParaPieza(s.operacion(),r.archivo(),actor,sesion);
        });
        assertNotNull(contenido);assertArrayEquals(imagen(0xff0000),contenido.bytes());
        var nueva=new Solicitud(UUID.randomUUID(),s.accion(),s.recurso(),s.versionOrigen(),s.fotoOriginal(),s.instruccion());
        assertThrows(ConflictoException.class,()->ejecutar(nueva));
        assertEquals(1,llamadas.get());assertEquals(escritos,puts.get());assertEquals(antes,uso());assertEquals("LIQUIDADA",estadoB0(s));
        acreditada=false;assertThrows(CapacidadDeshabilitadaException.class,()->real.descargar(s.operacion(),r.archivo(),equipo.agente(),sesion));
    }
    @ParameterizedTest @ValueSource(strings={"ORIGINAL","E44","PERMISO"})
    void reusoParaPiezaDeniegaOriginalOCoberturaOPermisoRevocados(String causa)throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var real=resultadosReales();var actor=equipo.agente();
        real.seleccionar(s.fotoOriginal(),s.operacion(),r.archivo(),0,"seleccion-test-"+UUID.randomUUID(),actor,sesion);
        Class<? extends RuntimeException> tipo;
        switch(causa){
            case "ORIGINAL"->{
                var galeria=contexto.getBean(GaleriaDeFotos.class);long propiedad=Long.parseLong(s.recurso().referencia());
                long idFoto=galeria.listar(propiedad,actor).stream().filter(f->s.fotoOriginal().toString().equals(f.clave())).findFirst().orElseThrow().id();
                galeria.eliminar(idFoto,actor);tipo=NoEncontradoException.class;
            }
            case "E44"->{acreditada=false;tipo=CapacidadDeshabilitadaException.class;}
            default->{permiso=false;tipo=AccesoNoAutorizadoException.class;}
        }
        long antes=uso();int escritos=puts.get();clearInvocations(almacen);
        assertThrows(tipo,()->tx.execute(t->real.bloquearSelecciones(List.of(s.fotoOriginal()),actor)));
        assertThrows(tipo,()->tx.execute(t->real.leerParaPieza(s.operacion(),r.archivo(),actor,sesion)));
        assertThrows(tipo,()->real.consultarSelecciones(List.of(s.fotoOriginal()),actor));
        verify(almacen,never()).abrir(anyString());assertEquals(1,llamadas.get());assertEquals(escritos,puts.get());
        assertEquals(antes,uso());assertEquals("LIQUIDADA",estadoB0(s));
    }
    @Test void consultaHistoricaDeIntencionNoSeConvierteEnAdmisionDeFuenteObsoleta(){
        var interrumpido=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,new DerechosDePlanB0(financiero,json),true){
            @Override public PresupuestoIaService.Admision admitir(PresupuestoIaService.Solicitud s,Actor a,ContextoSesion sid){throw new IllegalStateException("TEST antes de reserva");}
        };
        servicio=crear(interrumpido,perfiles,List.of(proveedor),List.of(control));var s=solicitud(Accion.REDACTAR_FICHA);
        assertThrows(IllegalStateException.class,()->ejecutar(s));fresca=false;
        assertEquals("PENDIENTE",servicio.consultar(s.operacion(),equipo.agente(),sesion).estado());servicio.exigirLectura(s.operacion(),equipo.agente());
        assertThrows(ConflictoException.class,()->ejecutar(s));assertEquals(0,llamadas.get());assertEquals("INTENCION",estado(s));
        assertTrue(financiero.operacion(equipo.agente().idOrganizacion(),s.operacion()).isEmpty());
    }
    @Test void derivadaDeOtroTenantNoSeLeeNiResuelvePorUnaReferenciaConocida()throws Exception{
        var s=derivadaReal();var r=ejecutar(s);var origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        var otro=origenes.empresa();var sid=origenes.sesion(otro.agente());var real=resultadosReales();clearInvocations(almacen);
        assertThrows(NoEncontradoException.class,()->real.descargar(s.operacion(),r.archivo(),otro.agente(),sid));
        assertThrows(NoEncontradoException.class,()->real.exigirArchivo(s.operacion(),r.archivo(),otro.agente()));
        assertThrows(NoEncontradoException.class,()->real.seleccionActual(s.fotoOriginal(),otro.agente(),sid));
        verifyNoInteractions(almacen);assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));
    }

    @Test void preparacionLocalDevuelveUtilidadSinPerfilConsentimientoNiReserva(){
        acreditada=false;var s=solicitud(Accion.MEJORAR_FOTO);
        var local=servicio.preparar(new Solicitud(s.operacion(),s.accion(),s.recurso(),null,s.fotoOriginal(),s.instruccion()),equipo.agente(),sesion);
        assertEquals("ORIGEN-TEST-1",local.versionOrigen());assertFalse(local.utilidadLocal().isEmpty());assertFalse(local.pendientes().isEmpty());
        assertTrue(registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).isEmpty());assertEquals(0,llamadas.get());
    }
    @ParameterizedTest @ValueSource(strings={"sinConsentimiento","sinPerfil","sinProveedor","sinQc","gastoCerrado","free","sinFondo"})
    void ningunaPuertaAusenteInvocaProcesador(String falta){
        var s=solicitud(Accion.REDACTAR_FICHA);
        switch(falta){
            case "sinConsentimiento" -> acreditada=false;
            case "sinPerfil" -> servicio=crear(b0,new PerfilesMaterialIa(new MockEnvironment()),List.of(proveedor),List.of(control));
            case "sinProveedor" -> servicio=crear(b0,perfiles,List.of(),List.of(control));
            case "sinQc" -> servicio=crear(b0,perfiles,List.of(proveedor),List.of());
            case "gastoCerrado" -> servicio=crear(new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,new DerechosDePlanB0(financiero,json),false),perfiles,List.of(proveedor),List.of(control));
            // Free carece de ciclo pagado vigente; el esquema no admite inventar un ciclo FREE.
            case "free" -> jdbc.update("UPDATE b0_ciclo SET revocado=true WHERE organizacion_id=?",equipo.agente().idOrganizacion());
            case "sinFondo" -> jdbc.update("UPDATE b0_fondo_tecnico SET habilitado=false WHERE id_fondo=?",fondo);
        }
        assertThrows(CapacidadDeshabilitadaException.class,()->ejecutar(s));
        assertEquals(0,llamadas.get());assertEquals(0,controlesEjecutados.get());
        assertEquals(0,new BigDecimal("0").compareTo(jdbc.queryForObject("SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo)));
    }
    @Test void commitsRealesPrecedenAlProcesadorYReplayNoDuplicaConsumoNiQc(){
        var s=solicitud(Accion.REDACTAR_FICHA);var r=ejecutar(s);
        assertEquals("DISPONIBLE",r.estado());assertNotNull(r.texto());assertEquals("LIQUIDADA",estadoB0(s));
        assertEquals(r,ejecutar(s));assertEquals(r,servicio.consultar(s.operacion(),equipo.agente(),sesion));
        assertEquals(1,llamadas.get());assertEquals(1,controlesEjecutados.get());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        String publico=json.objetoCanonico(r);for(String secreto:List.of("MATERIAL_IA_TEST","PROCESADOR_TEST","modelo","tarifa","costo","proveedor","legitimacion"))assertFalse(publico.contains(secreto),secreto);
    }
    @Test void contextoRevocadoDuranteProcesamientoNoBorraMedicionNiLiquidacion(){
        var s=solicitud(Accion.REDACTAR_FICHA);ejecucion=p->{permiso=false;return salida(p.accion());};
        assertThrows(AccesoNoAutorizadoException.class,()->ejecutar(s));
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals("FALLO_RESULTADO",estado(s));assertEquals(1,controlesEjecutados.get());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(s.operacion(),equipo.agente(),sesion));
        assertEquals(1,llamadas.get());
    }
    /** QC de pago SINTÉTICO; las mediciones se escriben en B0 real, no hay red ni calibración. */
    Facturable controlFacturableTest(Function<Salida,EvaluacionFacturable> respuesta){
        var externo=new Facturable(){
            public boolean atiende(Accion a,String p,String v){return "MATERIAL_IA_TEST".equals(p)&&version.equals(v);}
            public DestinoTratamiento destino(String p,String v){return new DestinoTratamiento("PROCESADOR_TEST_SIN_RED",p,v);}
            public EvaluacionFacturable evaluarFacturable(PeticionControl peticion,java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> observador){
                var r=peticion.salida();String clave=peticion.claveEtapa();
                UUID operacion=jdbc.queryForObject("SELECT id_operacion FROM b0_etapa_operacion_ia WHERE organizacion_id=? AND clave=?",UUID.class,equipo.agente().idOrganizacion(),clave);
                if(peticion.accion()==Accion.REDACTAR_FICHA)assertNull(peticion.originalAutorizado());
                else{assertNotNull(peticion.originalAutorizado());assertTrue(peticion.originalAutorizado().length>0);}
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());controlesEjecutados.incrementAndGet();
                assertEquals("EN_PROCESO",jdbc.queryForObject("SELECT estado FROM b0_operacion_ia WHERE organizacion_id=? AND id_operacion=?",String.class,equipo.agente().idOrganizacion(),operacion));
                assertEquals("RECLAMADA",jdbc.queryForObject("SELECT estado FROM b0_etapa_operacion_ia WHERE organizacion_id=? AND clave=?",String.class,equipo.agente().idOrganizacion(),clave));
                assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),operacion));
                assertEquals(PresupuestoIaService.claveDeEtapa(new ReferenciaInterna(equipo.agente().idOrganizacion(),operacion),ControlesMaterialIa.ETAPA_FINANCIERA),clave);
                return respuesta.apply(r);
            }
        };
        servicio=crear(b0,perfiles,List.of(proveedor),List.of(externo));
        return externo;
    }
    ResultadoFacturable medicionQcTest(){return new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),"PROCESADOR_TEST_SIN_RED","QC_SINTETICO","1",
            List.of(new Medicion("unidades_test","u",BigDecimal.ONE,null)),List.of(new Tarifa("unidades_test","u",new BigDecimal("0.1"),BigDecimal.ONE)),null,"QC-TEST-"+UUID.randomUUID())));}
    String estadoEtapa(Solicitud s){return jdbc.queryForObject("SELECT estado FROM b0_etapa_operacion_ia WHERE organizacion_id=? AND id_operacion=? AND etapa=?",String.class,equipo.agente().idOrganizacion(),s.operacion(),ControlesMaterialIa.ETAPA_FINANCIERA);}
    /** Adaptador Main y PG/B0 reales; transporte, fuente, dinero y rúbrica siguen siendo TEST. */
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({
            "REDACTAR_FICHA,APROBADA", "ANALIZAR_FOTO,APROBADA", "MEJORAR_FOTO,APROBADA",
            "REDACTAR_FICHA,JSON_INVALIDO", "MEJORAR_FOTO,JSON_INVALIDO",
            "REDACTAR_FICHA,NULO", "MEJORAR_FOTO,NULO",
            "REDACTAR_FICHA,USAGE_AUSENTE", "MEJORAR_FOTO,USAGE_AUSENTE",
            "MEJORAR_FOTO,NO_EVALUABLE", "MEJORAR_FOTO,BLOQUEANTE", "MEJORAR_FOTO,COPIA_FALLA"})
    @Tag("QC_FACTURABLE_TEST")
    void qcNativoSimuladoIntegraEtapaMedicionYReplay(String accion,String caso)throws Exception{
        Accion a=Accion.valueOf(accion);var s=solicitud(a);var post=new AtomicInteger();
        byte[] original=imagen(0x0000ff),derivada=imagen(0xff0000);
        var nativas=java.util.stream.Stream.of("ENTRADA_QC_TEST","LECTURA_QC_TEST","ESCRITURA30_QC_TEST",
                "ESCRITURA12_QC_TEST","SALIDA_QC_TEST","RAZONAMIENTO_QC_TEST")
                .map(c->new Tarifa(c,"token",BigDecimal.ONE,new BigDecimal("1000000"))).toList();
        var referencia=new ArrayList<>(nativas);referencia.add(new Tarifa("unidades_test","u",BigDecimal.ONE,BigDecimal.ONE));
        version=UUID.randomUUID().toString();
        String prefijoQc="controllocal.ia.material."+a.name().toLowerCase(Locale.ROOT)+".";
        perfiles=new PerfilesMaterialIa(new MockEnvironment().withProperty(prefijoQc+"perfil","MATERIAL_IA_TEST").withProperty(prefijoQc+"version",version));
        tx.executeWithoutResult(t->{
            jdbc.update("INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado) VALUES('MATERIAL_IA_TEST',?,?,1,?::jsonb,4,1,2,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',true)",version,fondo,json.lista(referencia));
            jdbc.update("UPDATE b0_perfil_actual SET version=? WHERE perfil='MATERIAL_IA_TEST'",version);
            jdbc.update("INSERT INTO b0_limite_salida_ia(perfil,version_perfil,maximo_bytes,evidencia_calibracion,evidencia_aprobacion,desde,hasta,habilitado) VALUES('MATERIAL_IA_TEST',?,1000000,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',?,?,true)",version,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(86400)));
        });
        var datosQc=new FuentesMaterialIa(){
            public Snapshot preparar(Solicitud q,Actor actor,ContextoSesion sid){return fuente.preparar(q,actor,sid);}
            public void revalidar(Solicitud q,Snapshot f,Actor actor){fuente.revalidar(q,f,actor);}
            public void exigirLecturaHistorica(Solicitud q,Snapshot f,Actor actor){fuente.exigirLecturaHistorica(q,f,actor);}
            public void exigirLecturaArtefacto(Solicitud q,Snapshot f,Actor actor){fuente.exigirLecturaArtefacto(q,f,actor);}
            public EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto evaluarLecturaArtefacto(Solicitud q,Snapshot f,Actor actor){return fuente.evaluarLecturaArtefacto(q,f,actor);}
            public byte[] fotoParaProcesar(Solicitud q,Snapshot f,Actor actor,ContextoSesion sid){
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());fuente.revalidar(q,f,actor);
                return new com.controllocal.service.documentos.EvaluacionTecnicaFoto(1_000_000).evaluar(original).contenido();
            }
            public void exigirTransferencia(Solicitud q,Snapshot f,DestinoTratamiento destino,Actor actor,ContextoSesion sid){
                if(!ControlQcOpenAi.IDENTIFICADOR.equals(destino.proveedor())){fuente.exigirTransferencia(q,f,destino,actor,sid);return;}
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());verificacionesTransferencia.incrementAndGet();
                antesDeTransferencia.run();fuente.revalidar(q,f,actor);
                if(!transferenciaPermitida||!"MATERIAL_IA_TEST".equals(destino.perfil())||!version.equals(destino.versionPerfil()))
                    throw new CapacidadDeshabilitadaException();
            }
        };
        autoridad=new AutoridadMaterialIa(registro,json,List.of(datosQc),perfiles);
        b0=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,new DerechosDePlanB0(financiero,json),true);
        String rubrica="RUBRICA_SINTETICA_TEST_NO_CALIBRADA";
        var ruta=new ConfiguracionQcOpenAi.Ruta(a,"MATERIAL_IA_TEST",version,"QC_TEST","RUBRICA_TEST",
                Idempotencia.huellaExacta(rubrica),"FUENTE_TEST_NO_OPERATIVA",rubrica,
                new ConfiguracionRespuestasOpenAi.Parametros("low",a==Accion.REDACTAR_FICHA?null:"auto",100),
                new ConfiguracionRespuestasOpenAi.Envoltura("ENV_TEST","a".repeat(64),2000,2000,a==Accion.REDACTAR_FICHA?0:10000,50000,20000,5000,30),
                new ConfiguracionRespuestasOpenAi.ContratoUsage("USAGE_TEST","EVIDENCIA_TEST","TARIFA_TEST",
                        "ENTRADA_QC_TEST","LECTURA_QC_TEST","ESCRITURA30_QC_TEST","ESCRITURA12_QC_TEST","SALIDA_QC_TEST","RAZONAMIENTO_QC_TEST",nativas),
                new ConfiguracionQcOpenAi.Cache("implicit","30m","POLITICA_TEST"),a==Accion.MEJORAR_FOTO?0:3000);
        var controlJson=new LinkedHashMap<String,Object>();controlJson.put("decision","APROBADA");
        var senales=new ArrayList<Map<String,Object>>();
        for(var x:controlSintetico(s).senales()){
            var senal=new LinkedHashMap<String,Object>();senal.put("aspecto",x.aspecto().name());senal.put("estado",x.estado().name());
            senal.put("hallazgoBloqueante",false);senal.put("observacion","JUICIO_SINTETICO_TEST");senales.add(senal);
        }
        controlJson.put("senales",senales);
        if(caso.equals("NULO"))senales.getFirst().put("observacion","detalle"+(char)0+"TEST");
        if(caso.equals("NO_EVALUABLE"))senales.get(1).put("estado","NO_EVALUABLE");
        if(caso.equals("BLOQUEANTE"))senales.get(1).put("hallazgoBloqueante",true);
        var respuesta=new LinkedHashMap<String,Object>();respuesta.put("id","resp_TEST_"+s.operacion());respuesta.put("model","gpt-6.1-sol");
        respuesta.put("status","completed");respuesta.put("service_tier","default");
        if(!caso.equals("USAGE_AUSENTE"))respuesta.put("usage",Map.of("input_tokens",10,"output_tokens",5,"total_tokens",15,
                "input_tokens_details",Map.of("cached_tokens",0,"cache_write_tokens",0),"output_tokens_details",Map.of("reasoning_tokens",0)));
        respuesta.put("output",List.of(Map.of("type","message","role","assistant","status","completed","content",
                List.of(Map.of("type","output_text","text",caso.equals("JSON_INVALIDO")?"{":json.objetoCanonico(controlJson))))));
        byte[] cuerpo=json.objetoCanonico(respuesta).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var evaluador=new com.controllocal.service.documentos.EvaluacionTecnicaFoto(1_000_000){
            @Override public synchronized Copia evaluar(byte[] bytes){
                if(caso.equals("COPIA_FALLA"))throw new IllegalStateException("FALLO_LOCAL_TEST");return super.evaluar(bytes);
            }
        };
        var qc=new ControlQcOpenAi(new ConfiguracionQcOpenAi(List.of(ruta),Duration.ofSeconds(1),Duration.ofSeconds(2)),()->"CLAVE_TEST_NO_REAL",
                (peticion,maximo,total,contextoIntento,observador)->{
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());post.incrementAndGet();
                    assertEquals("EN_PROCESO",estadoB0(s));assertEquals("RECLAMADA",estadoEtapa(s));
                    assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
                    return new TransporteOpenAi.Respuesta(200,java.net.http.HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(k,v)->true),cuerpo);
                },evaluador);
        if(a==Accion.MEJORAR_FOTO)ejecucion=p->new Salida(null,"image/png",derivada,"PROCESADOR_TEST","1",salida(a).medicion());
        servicio=crear(b0,perfiles,List.of(proveedor),List.of(qc));
        if(caso.equals("APROBADA"))assertEquals("DISPONIBLE",ejecutar(s).estado());
        else if(caso.equals("USAGE_AUSENTE"))assertThrows(ProcesamientoIncierto.class,()->ejecutar(s));
        else if(caso.equals("COPIA_FALLA"))assertThrows(NoIniciadoComprobado.class,()->ejecutar(s));
        else assertThrows(IllegalStateException.class,()->ejecutar(s));
        boolean incierta=caso.equals("USAGE_AUSENTE"),noInvocada=caso.equals("COPIA_FALLA");
        assertEquals(incierta?"INCIERTA":"LIQUIDADA",estadoB0(s));
        assertEquals(incierta?"INCIERTA":noInvocada?"NO_INVOCADA":"MEDIDA",estadoEtapa(s));
        assertEquals(incierta||noInvocada?1:2,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        if(a==Accion.MEJORAR_FOTO&&!caso.equals("APROBADA")&&!incierta){
            // La recuperación binaria vuelve a la respuesta durable inválida o a su NO_INVOCADA; nunca la aprueba.
            if(noInvocada)assertThrows(CapacidadDeshabilitadaException.class,()->ejecutar(s));
            else assertThrows(IllegalStateException.class,()->ejecutar(s));
            assertEquals("RESULTADO_NO_DISPONIBLE",servicio.consultar(s.operacion(),equipo.agente(),sesion).estado());
        }else assertEquals(caso.equals("APROBADA")?"DISPONIBLE":incierta?"EN_VERIFICACION":"RESULTADO_NO_DISPONIBLE",ejecutar(s).estado());
        assertEquals(1,llamadas.get());assertEquals(noInvocada?0:1,post.get());
        assertEquals(caso.equals("APROBADA")&&a==Accion.MEJORAR_FOTO?1:0,archivos.get());
        assertArrayEquals(imagen(0x0000ff),original);assertArrayEquals(imagen(0xff0000),derivada);
    }
    @Test @Tag("QC_FACTURABLE_TEST") void qcFacturableConservaDosMedicionesYCierraElMismoConjuntoUnaVez(){
        var s=solicitud(Accion.MEJORAR_FOTO);controlFacturableTest(r->new EvaluacionFacturable(controlSintetico(s),medicionQcTest()));
        var resultado=ejecutar(s);assertEquals("DISPONIBLE",resultado.estado());assertEquals("LIQUIDADA",estadoB0(s));assertEquals("MEDIDA",estadoEtapa(s));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=? AND etapa_clave IS NOT NULL",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND id_operacion=? AND hecho='LIQUIDADA'",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertEquals(resultado,ejecutar(s));assertEquals(1,llamadas.get());assertEquals(1,controlesEjecutados.get());
    }
    @Test @Tag("QC_FACTURABLE_TEST") void qcInvocadoInciertoNoLiberaNiReclamaOtraInferenciaAlRecuperar(){
        var s=solicitud(Accion.MEJORAR_FOTO);controlFacturableTest(r->{throw new ProcesamientoIncierto();});
        assertThrows(ProcesamientoIncierto.class,()->ejecutar(s));assertEquals("INCIERTA",estadoB0(s));assertEquals("INCIERTA",estadoEtapa(s));
        assertEquals("EN_VERIFICACION",ejecutar(s).estado());assertEquals("EN_VERIFICACION",servicio.recuperarResultadoDurable(equipo.agente().idOrganizacion(),s.operacion(),equipo.agente(),sesion).estado());
        assertEquals(1,llamadas.get());assertEquals(1,controlesEjecutados.get());assertTrue(jdbc.queryForObject("SELECT retenido>0 FROM b0_fondo_tecnico WHERE id_fondo=?",Boolean.class,fondo));
    }
    @Test @Tag("QC_FACTURABLE_TEST") void revocacionTrasClaimQcCierraNoInvocadaDefinitivaSinMedicionCeroNiSegundoQc(){
        var s=solicitud(Accion.MEJORAR_FOTO);controlFacturableTest(r->{fail("No debe invocar QC revocado");return null;});
        antesDeTransferencia=()->{if(verificacionesTransferencia.get()==2)permiso=false;};
        assertThrows(AccesoNoAutorizadoException.class,()->ejecutar(s));assertEquals("NO_INVOCADA",estadoEtapa(s));assertEquals("LIQUIDADA",estadoB0(s));
        permiso=true;antesDeTransferencia=()->{};assertThrows(CapacidadDeshabilitadaException.class,()->ejecutar(s));
        assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
    }
    @ParameterizedTest @ValueSource(strings={"PENDIENTE","RECLAMADA","INCIERTA"}) @Tag("QC_FACTURABLE_TEST")
    void retiradaRealSoloCierraQcNuncaReclamadaYNoLiberaObligacionEnCurso(String fase)throws Exception{
        var s=derivadaReal();var entroQc=new CountDownLatch(1);var terminarQc=new CountDownLatch(1);
        var externo=controlFacturableTest(r->{entroQc.countDown();
            if(fase.equals("RECLAMADA"))try{assertTrue(terminarQc.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
            throw new ProcesamientoIncierto();
        });
        var real=resultadosReales();var corte=spy(real);
        doAnswer(i->{i.callRealMethod();throw new IllegalStateException("TEST recepción comprometida, QC todavía PENDIENTE");}).when(corte).recibirResultado(anyLong(),any(),anyString(),any());
        servicio=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(proveedor),List.of(externo),List.of(corte),gestor);
        assertThrows(IllegalStateException.class,()->ejecutar(s));assertEquals("PENDIENTE",estadoEtapa(s));assertEquals(0,controlesEjecutados.get());
        var continuacion=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,List.of(proveedor),List.of(externo),List.of(real),gestor);
        var v=real.version(s.operacion(),equipo.agente());long antesRetirar=uso();
        var actorQc=Executors.newSingleThreadExecutor();var tareasQc=new ArrayList<Future<?>>();
        try{
            Future<?> enCurso=null;
            if(fase.equals("RECLAMADA")){
                enCurso=actorQc.submit(()->assertThrows(ProcesamientoIncierto.class,()->continuacion.recuperarResultadoDurable(equipo.agente().idOrganizacion(),s.operacion(),equipo.agente(),sesion)));
                tareasQc.add(enCurso);
                assertTrue(entroQc.await(10,TimeUnit.SECONDS));assertEquals("RECLAMADA",estadoEtapa(s));
            }else if(fase.equals("INCIERTA")){
                assertThrows(ProcesamientoIncierto.class,()->continuacion.recuperarResultadoDurable(equipo.agente().idOrganizacion(),s.operacion(),equipo.agente(),sesion));assertEquals("INCIERTA",estadoEtapa(s));
            }
            var retirada=real.descartarTemporal(s.operacion(),v.archivo(),v.revision(),"retirar-qc-"+UUID.randomUUID(),equipo.agente(),sesion);
            assertEquals("RETIRADA_VERIFICADA",retirada.estadoGestion());assertEquals(antesRetirar-imagen(0xff0000).length,uso());
            if(fase.equals("PENDIENTE")){
                assertEquals("NO_INVOCADA",estadoEtapa(s));assertEquals("LIQUIDADA",estadoB0(s));assertEquals(0,controlesEjecutados.get());
            }else{
                assertEquals(fase,estadoEtapa(s));assertNotEquals("LIQUIDADA",estadoB0(s));
                assertTrue(jdbc.queryForObject("SELECT retenido>0 FROM b0_fondo_tecnico WHERE id_fondo=?",Boolean.class,fondo));
            }
            terminarQc.countDown();if(enCurso!=null)enCurso.get(10,TimeUnit.SECONDS);
            if(!fase.equals("PENDIENTE")){
                assertEquals("INCIERTA",estadoEtapa(s));assertEquals("INCIERTA",estadoB0(s));
                assertTrue(jdbc.queryForObject("SELECT retenido>0 FROM b0_fondo_tecnico WHERE id_fondo=?",Boolean.class,fondo));
                assertEquals(antesRetirar-imagen(0xff0000).length,uso(),"La respuesta tardía no cambia la retirada física ya verificada");
            }
        }finally{cerrarActoresTest(actorQc,tareasQc,terminarQc::countDown);}
        assertEquals(1,llamadas.get());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
    }
    @Test void falloDeArchivoConservaGastoYRecuperaMismaSalidaSinOtroProcesador(){
        var s=solicitud(Accion.MEJORAR_FOTO);Salida medida=salida(s.accion());ejecucion=p->medida;fallaArchivo=true;
        assertThrows(IllegalStateException.class,()->ejecutar(s));assertEquals("LIQUIDADA",estadoB0(s));assertEquals("FALLO_RESULTADO",estado(s));
        fallaArchivo=false;var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();
        var r=servicio.recuperarResultadoDurable(o.organizacion(),o.id(),equipo.agente(),sesion);
        assertEquals("DISPONIBLE",r.estado());assertNotNull(r.archivo());assertEquals(1,llamadas.get());assertEquals(1,controlesEjecutados.get());
    }
    @ParameterizedTest @ValueSource(strings={"OBSERVADA","RECHAZADA"})
    void cadaResultadoSeControlaYUnaObservacionNoSeEntregaNiSePresumeGratuita(String valor){
        decision=Decision.valueOf(valor);var s=solicitud(Accion.MEJORAR_FOTO);var r=ejecutar(s);
        assertEquals("REQUIERE_REVISION",r.estado());assertNull(r.archivo());assertNull(r.texto());
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals(1,controlesEjecutados.get());assertEquals(0,archivos.get());
    }
    @Test void timeoutRetieneMismaOperacionYNoReemiteTrasRecrearCoordinador(){
        var s=solicitud(Accion.REDACTAR_FICHA);ejecucion=p->{throw new ProcesamientoIncierto();};
        assertThrows(ProcesamientoIncierto.class,()->ejecutar(s));assertEquals("INCIERTA",estadoB0(s));
        servicio=crear(b0,perfiles,List.of(proveedor),List.of(control));assertEquals("EN_VERIFICACION",ejecutar(s).estado());
        assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());
        assertTrue(jdbc.queryForObject("SELECT retenido>0 FROM b0_fondo_tecnico WHERE id_fondo=?",Boolean.class,fondo));
    }
    @Test void noProcesamientoAcreditadoDespuesDelClaimCancelaSinFabricarMedicionNiOtroIntento(){
        var s=solicitud(Accion.REDACTAR_FICHA);
        ejecucion=p->{throw new NoIniciadoComprobado(new ComprobacionNoProcesamiento(p.claveExterna(),"PROCESADOR_TEST_SIN_RED",
                "INTENTO-TEST-"+s.operacion(),"EVIDENCIA-TEST-SIN-PROCESAMIENTO-NI-CARGO","e".repeat(64),Instant.now()));};
        assertEquals("NO_PROCESADA",ejecutar(s).estado());assertEquals("CANCELADA",estadoB0(s));assertEquals("CANCELADA",estado(s));
        assertEquals("NO_PROCESADA",ejecutar(s).estado());assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT retenido+liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo)));
    }
    @Test void instruccionYDestinoSelladosSeVerificanYComprometenSinExponerPruebaPrivada(){
        var inicial=solicitud(Accion.REDACTAR_FICHA);var s=new Solicitud(inicial.operacion(),inicial.accion(),inicial.recurso(),inicial.versionOrigen(),null,
                "PETICION_LIBRE_TEST_NO_ENVIAR_CONTACTO_NI_EXPEDIENTE");
        var r=ejecutar(s);assertEquals("DISPONIBLE",r.estado());
        assertEquals(new DestinoTratamiento(idAdaptador,"MATERIAL_IA_TEST",version),destinos.get(s.operacion()));
        var evento=json.comoMapa(evidenciaDestino(s,"DESTINO_VERIFICADO"));assertEquals("VERIFICADO",evento.get("resultadoVerificacion"));
        assertEquals("PREVIA_A_INVOCACION",evento.get("fase"));assertEquals(PresupuestoIaService.claveDeIntento(new ReferenciaInterna(equipo.agente().idOrganizacion(),s.operacion())),evento.get("claveIntento"));
        String evidencia=evidenciaDestino(s,"DESTINO_VERIFICADO");
        for(String privado:List.of(s.instruccion(),instruccionPermitida,"datosPermitidos","legitimacionTerceros","PRUEBA_SINTETICA"))assertFalse(evidencia.contains(privado));
        String publico=json.objetoCanonico(r);for(String privado:List.of(idAdaptador,"MATERIAL_IA_TEST",version,"DESTINO_VERIFICADO","PRUEBA_SINTETICA"))assertFalse(publico.contains(privado));
        assertEquals(1,verificacionesTransferencia.get());assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));
    }
    @Test void destinoRechazadoTrasClaimNoInvocaNiFabricaConsumoYReplayNoReemite(){
        transferenciaPermitida=false;var s=solicitud(Accion.REDACTAR_FICHA);
        assertThrows(CapacidadDeshabilitadaException.class,()->ejecutar(s));
        assertEquals("CANCELADA",estadoB0(s));assertEquals("CANCELADA",estado(s));assertEquals(0,llamadas.get());
        assertEquals("DENEGADO",json.comoMapa(evidenciaDestino(s,"DESTINO_DENEGADO")).get("resultadoVerificacion"));
        assertEquals("NO_PROCESADA",ejecutar(s).estado());assertEquals(1,verificacionesTransferencia.get());assertEquals(0,llamadas.get());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT retenido+liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo)));
    }
    @Test void proveedorCambiadoDespuesDelClaimSeDeniegaAntesDeTransferencia(){
        b0=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,new DerechosDePlanB0(financiero,json),true){
            @Override public boolean iniciar(PresupuestoIaService.Solicitud s,Actor a,ContextoSesion sid){
                boolean inicio=super.iniciar(s,a,sid);if(inicio)idAdaptador="OTRO_DESTINO_TEST_NO_ACREDITADO";return inicio;
            }
        };
        servicio=crear(b0,perfiles,List.of(proveedor),List.of(control));var s=solicitud(Accion.REDACTAR_FICHA);
        assertThrows(ConflictoException.class,()->ejecutar(s));assertEquals("CANCELADA",estadoB0(s));assertEquals(0,llamadas.get());
        assertEquals(0,verificacionesTransferencia.get());assertTrue(evidenciaDestino(s,"DESTINO_DENEGADO").contains("OTRO_DESTINO_TEST_NO_ACREDITADO"));
        assertEquals("NO_PROCESADA",ejecutar(s).estado());assertEquals(0,llamadas.get());
    }
    @Test void revocacionEnLaUltimaGuardaE44ImpideInvocacionYConservaCancelacionAcreditada(){
        antesDeTransferencia=()->acreditada=false;var s=solicitud(Accion.REDACTAR_FICHA);
        assertThrows(CapacidadDeshabilitadaException.class,()->ejecutar(s));assertEquals("CANCELADA",estadoB0(s));assertEquals("CANCELADA",estado(s));
        assertEquals(1,verificacionesTransferencia.get());assertEquals(0,llamadas.get());assertNotNull(evidenciaDestino(s,"DESTINO_DENEGADO"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        acreditada=true;antesDeTransferencia=()->{};assertEquals("NO_PROCESADA",ejecutar(s).estado());assertEquals(0,llamadas.get());
    }
    @ParameterizedTest @ValueSource(strings={"NULA","VACIA"})
    void instruccionPermitidaAusenteNoUsaTextoLibreComoFallback(String caso){
        instruccionPermitida=caso.equals("NULA")?null:" ";var s=solicitud(Accion.REDACTAR_FICHA);
        assertFalse(servicio.preparar(s,equipo.agente(),sesion).utilidadLocal().isEmpty());
        assertThrows(IllegalStateException.class,()->ejecutar(s));assertEquals("CANCELADA",estadoB0(s));assertEquals(0,llamadas.get());
        assertEquals(0,verificacionesTransferencia.get());assertNotNull(evidenciaDestino(s,"DESTINO_DENEGADO"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
    }
    @Test void destinoUsaPerfilYVersionDeLaIntencionAunqueCambieConfiguracionActual(){
        var original=b0;
        b0=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,new DerechosDePlanB0(financiero,json),true){
            @Override public PresupuestoIaService.Admision admitir(PresupuestoIaService.Solicitud s,Actor a,ContextoSesion sid){throw new IllegalStateException("TEST interrupción tras intención, antes de reserva");}
        };
        servicio=crear(b0,perfiles,List.of(proveedor),List.of(control));var s=solicitud(Accion.REDACTAR_FICHA);
        assertThrows(IllegalStateException.class,()->ejecutar(s));assertEquals("INTENCION",estado(s));assertEquals(0,llamadas.get());
        var configuracion=new MockEnvironment();for(Accion accion:Accion.values()){
            String pref="controllocal.ia.material."+accion.name().toLowerCase(Locale.ROOT)+".";
            configuracion.setProperty(pref+"perfil","OTRO_PERFIL_TEST_CERRADO");configuracion.setProperty(pref+"version","OTRA_VERSION_TEST");
        }
        servicio=crear(original,new PerfilesMaterialIa(configuracion),List.of(proveedor),List.of(control));
        assertEquals("DISPONIBLE",ejecutar(s).estado());assertEquals(new DestinoTratamiento(idAdaptador,"MATERIAL_IA_TEST",version),destinos.get(s.operacion()));
        assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));assertTrue(evidenciaDestino(s,"DESTINO_VERIFICADO").contains(version));
    }
    @ParameterizedTest @EnumSource(Accion.class)
    void cadaModalidadConservaCincoSenalesYNoInventaUnaRevisionHumana(Accion accion){
        var s=solicitud(accion);var entregado=ejecutar(s);assertEquals("DISPONIBLE",entregado.estado());
        if(accion==Accion.MEJORAR_FOTO){assertNotNull(entregado.archivo());assertTrue(lecturasArchivo.get()>0);}
        else{assertNull(entregado.archivo());assertFalse(entregado.texto().isBlank());}
        var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();
        var qc=json.comoObjeto(o.control(),Control.class);
        assertEquals(5,qc.senales().size());assertNotNull(qc.evaluadaEn());
        assertEquals(accion==Accion.MEJORAR_FOTO?0L:1L,qc.senales().stream().filter(x->x.estado()==EstadoSenal.NO_APLICA).count());
        for(var senal:qc.senales()){assertNotNull(senal.referenciaEvidencia());assertFalse(senal.observacion().isBlank());}
        assertFalse(o.control().contains("administrador"));assertFalse(o.control().contains("revisionHumana"));
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals(1,llamadas.get());
    }
    @ParameterizedTest @ValueSource(strings={"sin_control","sin_senales","vacias","incompletas","duplicadas",
            "sin_fecha","fecha_futura","sin_version","sin_observacion","sin_evidencia","no_evaluable","no_aplica_indebido","bloqueante"})
    void controlIncompletoOIncompatibleConAprobacionSeConservaSinEntregarNiRevertirGasto(String caso){
        controlRecibido=c->switch(caso){
            case "sin_control" -> null;
            case "sin_senales" -> new Control(c.decision(),c.version(),c.evaluadaEn(),null);
            case "vacias" -> new Control(c.decision(),c.version(),c.evaluadaEn(),List.of());
            case "incompletas" -> new Control(c.decision(),c.version(),c.evaluadaEn(),c.senales().subList(0,4));
            case "duplicadas" -> new Control(c.decision(),c.version(),c.evaluadaEn(),List.of(c.senales().getFirst(),c.senales().getFirst()));
            case "sin_fecha" -> new Control(c.decision(),c.version(),null,c.senales());
            case "fecha_futura" -> new Control(c.decision(),c.version(),Instant.now().plusSeconds(3600),c.senales());
            case "sin_version" -> new Control(c.decision()," ",c.evaluadaEn(),c.senales());
            case "sin_observacion" -> conSenal(c,Aspecto.FIDELIDAD,EstadoSenal.EVALUADA,false," ","TEST-EVIDENCIA");
            case "sin_evidencia" -> conSenal(c,Aspecto.FIDELIDAD,EstadoSenal.EVALUADA,false,"TEST falta evidencia",null);
            case "no_evaluable" -> conSenal(c,Aspecto.FIDELIDAD,EstadoSenal.NO_EVALUABLE,false,"TEST falta evidencia",null);
            case "no_aplica_indebido" -> conSenal(c,Aspecto.FIDELIDAD,EstadoSenal.NO_APLICA,false,"TEST motivo no habilita excepción","TEST-EVIDENCIA");
            case "bloqueante" -> conSenal(c,Aspecto.ALTERACION_INVENCION,EstadoSenal.EVALUADA,true,"TEST hallazgo bloqueante","TEST-EVIDENCIA");
            default -> throw new AssertionError(caso);
        };
        var s=solicitud(Accion.MEJORAR_FOTO);assertThrows(IllegalStateException.class,()->ejecutar(s));
        var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals("FALLO_RESULTADO",o.estado());
        assertNotNull(o.medicion());assertNotNull(o.salida());assertNull(o.control());assertNull(o.resultado());
        var evento=json.comoMapa(jdbc.queryForObject("SELECT evidencia FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='CONTROL_INVALIDO'",String.class,o.organizacion(),o.id()));
        assertEquals(caso.equals("sin_control")?"NO_RECIBIDO":"RECIBIDO",evento.get("estadoControl"));
        if(caso.equals("sin_control"))assertFalse(evento.containsKey("controlRecibido"),"Ausencia explícita sin fabricar señales");
        else assertNotNull(evento.get("controlRecibido"),"Se conserva el control original que sí retornó");
        assertEquals("RESULTADO_NO_DISPONIBLE",servicio.consultar(s.operacion(),equipo.agente(),sesion).estado());
        assertFalse(servicio.preparar(s,equipo.agente(),sesion).utilidadLocal().isEmpty(),"Se conserva utilidad ordinaria sin aprobar la derivada");
        assertEquals(1,llamadas.get());assertEquals(0,archivos.get());
    }
    @Test void excepcionDelControlConservaAusenciaExplicitaSinRevertirElConsumo(){
        controlRecibido=c->{throw new IllegalStateException("TEST control no retornó");};
        var s=solicitud(Accion.MEJORAR_FOTO);assertThrows(IllegalStateException.class,()->ejecutar(s));
        var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();
        var evento=json.comoMapa(jdbc.queryForObject("SELECT evidencia FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='CONTROL_SIN_CONFIRMAR'",String.class,o.organizacion(),o.id()));
        assertEquals("NO_RECIBIDO",evento.get("estadoControl"));assertFalse(evento.containsKey("controlRecibido"));
        assertEquals("FALLO_RESULTADO",o.estado());assertEquals("LIQUIDADA",estadoB0(s));assertNotNull(o.medicion());
        assertNull(o.control());assertNull(o.resultado());assertEquals(1,llamadas.get());assertEquals(0,archivos.get());
    }
    @ParameterizedTest @ValueSource(strings={"OBSERVADA","RECHAZADA"})
    void faltaDeEvidenciaSeConservaComoNoEvaluableSinAprobarNiBloquearGestionOrdinaria(String estado){
        decision=Decision.valueOf(estado);
        controlRecibido=c->conSenal(c,Aspecto.FIDELIDAD,EstadoSenal.NO_EVALUABLE,false,"TEST evidencia no disponible",null);
        var s=solicitud(Accion.MEJORAR_FOTO);assertEquals("REQUIERE_REVISION",ejecutar(s).estado());
        var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();
        assertEquals(estado,o.estado());assertNotNull(o.control());assertNull(o.resultado());assertEquals("LIQUIDADA",estadoB0(s));
        assertEquals(0,archivos.get());assertFalse(servicio.preparar(s,equipo.agente(),sesion).utilidadLocal().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"proceso_nulo","proceso_vacio","version_nula","version_vacia"})
    void metadatosDeProcesoFaltantesNoOcultanElConsumoOriginal(String caso){
        var s=solicitud(Accion.MEJORAR_FOTO);var original=salida(s.accion());
        var incompleta=new Salida(original.texto(),original.contentType(),original.foto(),
                caso.equals("proceso_nulo")?null:caso.equals("proceso_vacio")?" ":original.proceso(),
                caso.equals("version_nula")?null:caso.equals("version_vacia")?" ":original.versionProceso(),original.medicion());
        ejecucion=p->incompleta;assertThrows(IllegalStateException.class,()->ejecutar(s));
        var o=registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).orElseThrow();
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals("FALLO_RESULTADO",o.estado());assertNotNull(o.medicion());
        var conservada=json.comoObjeto(o.salida(),MaterialIaServiceImpl.DetalleSalida.class);
        assertEquals(incompleta.proceso(),conservada.proceso());assertEquals(incompleta.versionProceso(),conservada.versionProceso());
        assertEquals(1,llamadas.get());assertEquals(0,controlesEjecutados.get());assertEquals(0,archivos.get());
    }
    @ParameterizedTest @ValueSource(strings={"VACIA","EXCESIVA"})
    void identidadDelAdaptadorSeCompruebaAntesDeReservarOReclamar(String caso){
        idAdaptador=caso.equals("VACIA")?" ":"x".repeat(101);var s=solicitud(Accion.REDACTAR_FICHA);
        assertThrows(IllegalStateException.class,()->ejecutar(s));
        assertTrue(financiero.operacion(equipo.agente().idOrganizacion(),s.operacion()).isEmpty());assertEquals(0,llamadas.get());
    }
    @Test void revocacionDeSidTrasClaimSinInvocarConservaPruebaRealCancelaYNoReemite(){
        var sesiones=contexto.getBean(ContextosService.class);
        b0=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,
                new DerechosDePlanB0(financiero,json),true){
            @Override public boolean iniciar(PresupuestoIaService.Solicitud s,Actor actor,ContextoSesion sid){
                boolean inicio=super.iniciar(s,actor,sid);
                if(inicio)sesiones.cerrar(sid.referenciaSesion(),sid.revisionContexto());
                return inicio;
            }
        };
        servicio=crear(b0,perfiles,List.of(proveedor),List.of(control));var s=solicitud(Accion.REDACTAR_FICHA);
        assertThrows(AccesoNoAutorizadoException.class,()->ejecutar(s));
        assertEquals("CANCELADA",estadoB0(s));assertEquals("CANCELADA",estado(s));assertEquals(0,llamadas.get());
        assertEquals(0,controlesEjecutados.get());assertEquals(0,archivos.get());long org=equipo.agente().idOrganizacion();
        var prueba=json.comoObjeto(jdbc.queryForObject("SELECT contenido::text FROM b0_antecedente WHERE organizacion_id=? AND id_operacion=? AND hecho='NO_PROCESAMIENTO_COMPROBADO'",String.class,org,s.operacion()),ComprobacionNoProcesamiento.class);
        long idEvento=Long.parseLong(prueba.referenciaEvidencia().substring("material_ia_evento:".length()));
        String hecho=jdbc.queryForObject("SELECT evidencia FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND id_evento=? AND estado='NO_INVOCACION_PREVIA'",String.class,org,s.operacion(),idEvento);
        assertEquals(Idempotencia.huellaExacta(hecho),prueba.huellaEvidencia());assertEquals(idAdaptador,prueba.proveedor());
        assertEquals(PresupuestoIaService.claveDeIntento(new ReferenciaInterna(org,s.operacion())),prueba.claveIntento());
        assertEquals(prueba.claveIntento(),prueba.referenciaIntento());assertTrue(hecho.contains("COORDINADOR_CORE"));assertTrue(hecho.contains("NO_INVOCADO"));
        assertTrue(hecho.contains(version));assertFalse(prueba.comprobadaEn().isAfter(Instant.now()));
        assertFalse(prueba.comprobadaEn().isBefore(financiero.inicio(org,s.operacion()).orElseThrow()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=? AND id_operacion=?",Integer.class,org,s.operacion()));
        assertEquals(0,BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT retenido+liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",BigDecimal.class,fondo)));
        sesion=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),sesiones).sesion(equipo.agente());
        assertEquals("NO_PROCESADA",ejecutar(s).estado());assertEquals(0,llamadas.get());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='NO_INVOCACION_PREVIA'",Integer.class,org,s.operacion()));
    }
    @Test void pruebaYCancelacionSonAtomicasSiB0NoPuedeConfirmarLaCancelacion(){
        var sesiones=contexto.getBean(ContextosService.class);
        b0=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,
                new DerechosDePlanB0(financiero,json),true){
            @Override public boolean iniciar(PresupuestoIaService.Solicitud s,Actor actor,ContextoSesion sid){
                boolean inicio=super.iniciar(s,actor,sid);if(inicio)sesiones.cerrar(sid.referenciaSesion(),sid.revisionContexto());return inicio;
            }
            @Override public PresupuestoIaService.Admision cancelarNoProcesada(PresupuestoIaService.ReferenciaFinanciada ref,ComprobacionNoProcesamiento prueba){
                throw new IllegalStateException("TEST B0 no confirmó la cancelación");
            }
        };
        servicio=crear(b0,perfiles,List.of(proveedor),List.of(control));var s=solicitud(Accion.REDACTAR_FICHA);
        var fallo=assertThrows(AccesoNoAutorizadoException.class,()->ejecutar(s));assertEquals(1,fallo.getSuppressed().length);
        assertEquals("INCIERTA",estadoB0(s));assertEquals("INCIERTA",estado(s));assertEquals(0,llamadas.get());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='NO_INVOCACION_PREVIA'",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertTrue(jdbc.queryForObject("SELECT retenido>0 FROM b0_fondo_tecnico WHERE id_fondo=?",Boolean.class,fondo));
    }
    @Test void timeoutDespuesDeEntrarAlProveedorConSidRevocadoNoAcreditaNoInvocacion(){
        ejecucion=p->{contexto.getBean(ContextosService.class).cerrar(sesion.referenciaSesion(),sesion.revisionContexto());throw new ProcesamientoIncierto();};
        var s=solicitud(Accion.REDACTAR_FICHA);assertThrows(ProcesamientoIncierto.class,()->ejecutar(s));
        assertEquals("INCIERTA",estadoB0(s));assertEquals("INCIERTA",estado(s));assertEquals(1,llamadas.get());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='NO_INVOCACION_PREVIA'",Integer.class,equipo.agente().idOrganizacion(),s.operacion()));
        assertTrue(jdbc.queryForObject("SELECT retenido>0 FROM b0_fondo_tecnico WHERE id_fondo=?",Boolean.class,fondo));
        sesion=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).sesion(equipo.agente());
        assertEquals("EN_VERIFICACION",ejecutar(s).estado());assertEquals(1,llamadas.get());
    }
    @ParameterizedTest @ValueSource(strings={"OPERACION","TENANT"})
    void pruebaDeOtroIntentoOTenantNoCancelaElClaimOriginal(String caso){
        var s=solicitud(Accion.REDACTAR_FICHA);long org=equipo.agente().idOrganizacion();
        long otraOrg=caso.equals("TENANT")?new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).empresa().agente().idOrganizacion():org;
        var ajena=new ReferenciaInterna(otraOrg,caso.equals("OPERACION")?UUID.randomUUID():s.operacion());
        ejecucion=p->{throw new NoIniciadoComprobado(new ComprobacionNoProcesamiento(PresupuestoIaService.claveDeIntento(ajena),idAdaptador,
                "OTRO-INTENTO-TEST","EVIDENCIA-OTRO-INTENTO-TEST","e".repeat(64),Instant.now()));};
        assertThrows(ConflictoException.class,()->ejecutar(s));assertEquals("INCIERTA",estadoB0(s));assertEquals("INCIERTA",estado(s));
        assertEquals(1,llamadas.get());assertTrue(jdbc.queryForObject("SELECT retenido>0 FROM b0_fondo_tecnico WHERE id_fondo=?",Boolean.class,fondo));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND id_operacion=? AND hecho='NO_PROCESAMIENTO_COMPROBADO'",Integer.class,org,s.operacion()));
    }
    @ParameterizedTest @ValueSource(strings={"CONTROL","PROCESO","FOTO_SIN_ARCHIVO","REDACCION_SIN_TEXTO","REDACCION_TEXTO_VACIO",
            "ANALISIS_SIN_TEXTO","ANALISIS_TEXTO_VACIO","REDACCION_CON_ARCHIVO","ANALISIS_CON_ARCHIVO"})
    void consultarDisponiblePersistidoNoSaltaLaVerificacionDeMetadatos(String caso){
        Accion accion=caso.equals("FOTO_SIN_ARCHIVO")?Accion.MEJORAR_FOTO:caso.startsWith("ANALISIS")?Accion.ANALIZAR_FOTO:Accion.REDACTAR_FICHA;
        var s=solicitud(accion);ejecucion=p->{throw new ProcesamientoIncierto();};
        assertThrows(ProcesamientoIncierto.class,()->ejecutar(s));var original=salida(s.accion());
        String texto=caso.endsWith("SIN_TEXTO")?null:caso.endsWith("TEXTO_VACIO")?" ":original.texto();
        var recibida=new Salida(texto,original.contentType(),original.foto(),caso.equals("PROCESO")?null:original.proceso(),original.versionProceso(),original.medicion());
        long org=equipo.agente().idOrganizacion();var ref=new ReferenciaInterna(org,s.operacion());String clave=PresupuestoIaService.claveDeIntento(ref);
        servicio.registrarResultadoVerificado(org,s.operacion(),clave,recibida);
        // Simula el JSON histórico incompleto que el esquema permite conservar; no se retira ninguna guarda SQL.
        tx.executeWithoutResult(t->{var o=registro.obtener(org,s.operacion()).orElseThrow();
            var qc=caso.equals("CONTROL")?new Control(Decision.APROBADA,"TEST-ANTERIOR",Instant.now(),List.of()):controlSintetico(s);
            registro.controlar(o,json.objetoCanonico(qc),"CONTROLADA");
            registro.resultado(registro.obtener(org,s.operacion()).orElseThrow(),json.objetoCanonico(new MaterialIaServiceImpl.Entregado(recibida.texto(),caso.endsWith("CON_ARCHIVO")?UUID.randomUUID():null)));
        });
        int eventos=jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=?",Integer.class,org,s.operacion());
        assertThrows(IllegalStateException.class,()->servicio.consultar(s.operacion(),equipo.agente(),sesion));
        assertThrows(IllegalStateException.class,()->servicio.recuperarResultadoDurable(org,s.operacion(),equipo.agente(),sesion));
        assertEquals(eventos,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=?",Integer.class,org,s.operacion()));
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals(1,llamadas.get());assertEquals(0,archivos.get());assertEquals(0,lecturasArchivo.get());
        var conservada=registro.obtener(org,s.operacion()).orElseThrow();assertNotNull(conservada.medicion());assertNotNull(conservada.salida());assertNotNull(conservada.resultado());
    }
    @Test void rollbackPosteriorDelInvocanteNoRevierteElConsumoYaConfirmadoFueraDeSuTransaccion(){
        var s=solicitud(Accion.REDACTAR_FICHA);assertEquals("DISPONIBLE",ejecutar(s).estado());
        assertThrows(IllegalStateException.class,()->tx.execute(t->{assertEquals("LIQUIDADA",estadoB0(s));throw new IllegalStateException("TEST rollback posterior del invocante");}));
        assertEquals("LIQUIDADA",estadoB0(s));assertEquals("DISPONIBLE",estado(s));assertEquals(1,llamadas.get());
    }
    @Test void entradaYCallbacksDesdePadreConLocksSeDenieganAntesDeTodoEfecto()throws Exception{
        var s=solicitud(Accion.REDACTAR_FICHA);long lock=UUID.randomUUID().getMostSignificantBits();
        var padre=new AtomicLong();var observador=new AtomicLong();var txPadre=new AtomicLong();var txObservador=new AtomicLong();
        try(var inspector=Executors.newSingleThreadExecutor()){
            tx.executeWithoutResult(t->{
                padre.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
                txPadre.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
                jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?)",Integer.class,lock);
                assertThrows(IllegalStateException.class,()->servicio.preparar(s,equipo.agente(),sesion));
                assertThrows(IllegalStateException.class,()->ejecutar(s));
                assertThrows(IllegalStateException.class,()->servicio.consultar(s.operacion(),equipo.agente(),sesion));
                var salida=salida(s.accion());String clave=PresupuestoIaService.claveDeIntento(new ReferenciaInterna(equipo.agente().idOrganizacion(),s.operacion()));
                assertThrows(IllegalStateException.class,()->servicio.registrarResultadoVerificado(equipo.agente().idOrganizacion(),s.operacion(),clave,salida));
                assertThrows(IllegalStateException.class,()->servicio.recuperarResultadoDurable(equipo.agente().idOrganizacion(),s.operacion(),equipo.agente(),sesion));
                try{assertFalse(Boolean.TRUE.equals(inspector.submit(()->tx.execute(b->{
                    observador.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
                    txObservador.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
                    return jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,lock);
                })).get(10,TimeUnit.SECONDS)));}catch(Exception e){throw new AssertionError(e);}
                assertNotEquals(padre.get(),observador.get(),"La prueba del lock usa dos transacciones físicas simultáneas");
            });
        }
        assertTrue(Boolean.TRUE.equals(tx.execute(t->jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,lock))));
        assertEquals(0,fuentesPreparadas.get());assertEquals(0,llamadas.get());assertEquals(0,controlesEjecutados.get());
        assertTrue(registro.obtener(equipo.agente().idOrganizacion(),s.operacion()).isEmpty());
        assertTrue(financiero.operacion(equipo.agente().idOrganizacion(),s.operacion()).isEmpty());
        EvidenciaC4DePrueba.transaccion("PADRE_RECHAZADO",padre.get(),txPadre.get());EvidenciaC4DePrueba.transaccion("OBSERVADOR_LOCK",observador.get(),txObservador.get());
    }
    /** Contención REAL de Cuenta/J; la contraparte es técnica, no una decisión humana/Piezas ni dos SID. */
    @ParameterizedTest @ValueSource(strings={"ADMISION","CLAIM"})
    void admisionYClaimEsperanCuentaSinPoseerJobNiDuplicarProveedor(String fase)throws Exception{
        var s=solicitud(Accion.REDACTAR_FICHA);var actor=equipo.agente();boolean admision=fase.equals("ADMISION");
        var detenido=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),List.of(autoridad),json,new DerechosDePlanB0(financiero,json),true){
            @Override public PresupuestoIaService.Admision admitir(PresupuestoIaService.Solicitud p,Actor a,ContextoSesion sid){
                if(admision)throw new IllegalStateException("TEST preparar INTENCION");return super.admitir(p,a,sid);
            }
            @Override public boolean iniciar(PresupuestoIaService.Solicitud p,Actor a,ContextoSesion sid){throw new IllegalStateException("TEST preparar RESERVADA");}
        };
        servicio=crear(detenido,perfiles,List.of(proveedor),List.of(control));
        var preparacion=assertThrows(IllegalStateException.class,()->ejecutar(s));
        assertEquals(admision?"TEST preparar INTENCION":"TEST preparar RESERVADA",preparacion.getMessage());
        assertEquals(admision?"INTENCION":"RESERVADA",estado(s));assertEquals(0,llamadas.get());
        var cuentas=contexto.getBean(com.controllocal.persistence.repositorio.CuentaAccesoRepository.class);
        long cuenta=Objects.requireNonNull(tx.execute(t->contexto.getBean(com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository.class)
                .cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona()).getFirst()));
        String identidad=Idempotencia.huellaExacta("BROX-MATERIAL-IA-LOCK\u0000"+actor.idOrganizacion()+"\u0000"+s.operacion());
        long lock=Long.parseUnsignedLong(identidad.substring(0,16),16),clase=(lock>>>32)&0xffffffffL,objeto=lock&0xffffffffL;
        var titularListo=new CountDownLatch(1);var pedirCuenta=new CountDownLatch(1);var cuentaTomada=new CountDownLatch(1);
        var continuarTitular=new CountDownLatch(1);var jobTomado=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        var tomarJob=new AtomicBoolean();var ordenTx=new AtomicInteger();
        var pidTitular=new AtomicLong();var txTitular=new AtomicLong();var pidSolicitante=new AtomicLong();var txSolicitante=new AtomicLong();
        var observado=new PlatformTransactionManager(){
            @Override public TransactionStatus getTransaction(TransactionDefinition definicion){
                var status=gestor.getTransaction(definicion);
                try{
                    if(ordenTx.incrementAndGet()==(admision?2:3)){
                        // La TX inicial contexto() ya terminó; se observa precisamente admisión/claim.
                        pidSolicitante.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
                        txSolicitante.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
                        jdbc.execute("SET LOCAL lock_timeout='10s'");pedirCuenta.countDown();
                        esperar(cuentaTomada,"La contraparte debe ocupar Cuenta antes de continuar la TX observada");
                    }
                    return status;
                }catch(RuntimeException|Error fallo){gestor.rollback(status);throw fallo;}
            }
            @Override public void commit(TransactionStatus status){gestor.commit(status);}
            @Override public void rollback(TransactionStatus status){gestor.rollback(status);}
        };
        servicio=new MaterialIaServiceImpl(registro,b0,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,json,
                List.of(proveedor),List.of(control),List.of(almacenamiento),observado);
        var hikari=assertInstanceOf(com.zaxxer.hikari.HikariDataSource.class,datos);assertEquals(2,hikari.getMaximumPoolSize());
        var pool=Executors.newFixedThreadPool(2);
        // Dos conexiones SUT ocupadas; sólo el inspector de pg_locks usa una conexión read-only adicional.
        try(var conexion=java.sql.DriverManager.getConnection(hikari.getJdbcUrl(),hikari.getUsername(),hikari.getPassword())){
            conexion.setReadOnly(true);
            var inspector=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(conexion,true));
            var titular=pool.submit(()->tx.executeWithoutResult(t->{
                pidTitular.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));txTitular.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
                jdbc.execute("SET LOCAL lock_timeout='10s'");titularListo.countDown();esperar(pedirCuenta,"No comenzó la fase observada");
                cuentas.bloquearCambioAcceso(cuenta).orElseThrow();cuentaTomada.countDown();
                esperar(continuarTitular,"No terminó la inspección de espera por Cuenta");
                if(tomarJob.get()){registro.bloquear(lock);jobTomado.countDown();}
                esperar(liberar,"No se liberó la contraparte técnica");
            }));
            esperar(titularListo,"La contraparte debe abrirse antes de solicitar");
            var solicitante=pool.submit(()->ejecutar(s));esperar(cuentaTomada,"La Cuenta no quedó ocupada");
            boolean esperaCuenta=false;long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            while(System.nanoTime()<limite){
                esperaCuenta=Boolean.TRUE.equals(inspector.queryForObject("SELECT EXISTS(SELECT 1 FROM unnest(pg_blocking_pids(?::integer)) AS b(pid) WHERE pid=?::integer)",
                        Boolean.class,pidSolicitante.get(),pidTitular.get()));
                if(esperaCuenta)break;TimeUnit.MILLISECONDS.sleep(25);
            }
            assertTrue(esperaCuenta,"La TX de "+fase+" debe esperar a la contraparte que posee Cuenta");
            assertNotEquals(pidTitular.get(),pidSolicitante.get());assertNotEquals(txTitular.get(),txSolicitante.get());
            String consultaJob="SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND pid=? AND classid::bigint=? AND objid::bigint=? AND objsubid=1";
            assertEquals(0L,inspector.queryForObject(consultaJob,Long.class,pidSolicitante.get(),clase,objeto),"No debe poseer ni esperar J antes de obtener Cuenta");
            assertTrue(inspector.queryForObject("SELECT count(*) FROM pg_locks WHERE pid=? AND NOT granted AND locktype IN ('transactionid','tuple')",Long.class,pidSolicitante.get())>0);
            tomarJob.set(true);continuarTitular.countDown();esperar(jobTomado,"Cuenta→J debe progresar mientras el solicitante espera Cuenta");
            assertEquals(1L,inspector.queryForObject(consultaJob+" AND granted",Long.class,pidTitular.get(),clase,objeto));
            assertEquals(0L,inspector.queryForObject(consultaJob,Long.class,pidSolicitante.get(),clase,objeto));
            liberar.countDown();titular.get(15,TimeUnit.SECONDS);var resultado=solicitante.get(20,TimeUnit.SECONDS);
            assertEquals("DISPONIBLE",resultado.estado());assertEquals(resultado,ejecutar(s));assertEquals(1,llamadas.get());assertEquals("LIQUIDADA",estadoB0(s));
            assertEquals(1,financiero.idsMediciones(actor.idOrganizacion(),s.operacion()).size());
            EvidenciaC4DePrueba.transaccion("CUENTA_J_CONTRAPARTE_TECNICA_"+fase,pidTitular.get(),txTitular.get());
            EvidenciaC4DePrueba.transaccion("SOLICITANTE_ESPERA_CUENTA_SIN_J_"+fase,pidSolicitante.get(),txSolicitante.get());
        }finally{
            pedirCuenta.countDown();continuarTitular.countDown();liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));
        }
    }
    private static void esperar(CountDownLatch señal,String mensaje){
        try{assertTrue(señal.await(15,TimeUnit.SECONDS),mensaje);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(mensaje,e);}
    }
    @Test void dosJobsConPoolDosLiberanConexionesYLocksAntesDeEsperarAlProcesador()throws Exception{
        var hikari=assertInstanceOf(com.zaxxer.hikari.HikariDataSource.class,datos);assertEquals(2,hikari.getMaximumPoolSize());
        var uno=solicitud(Accion.REDACTAR_FICHA);var dos=solicitud(Accion.REDACTAR_FICHA);
        long lock=UUID.randomUUID().getMostSignificantBits();var pidPreparacion=new AtomicLong();var txPreparacion=new AtomicLong();
        var pidInspector=new AtomicLong();var txInspector=new AtomicLong();
        tx.executeWithoutResult(t->{pidPreparacion.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));
            txPreparacion.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
            jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?)",Integer.class,lock);});
        var dentro=new CountDownLatch(2);var terminar=new CountDownLatch(1);
        ejecucion=p->{dentro.countDown();try{assertTrue(terminar.await(20,TimeUnit.SECONDS));}
            catch(InterruptedException e){throw new AssertionError(e);}return salida(p.accion());};
        var pool=Executors.newFixedThreadPool(2);
        try{
            var primera=pool.submit(()->ejecutar(uno));var segunda=pool.submit(()->ejecutar(dos));
            assertTrue(dentro.await(20,TimeUnit.SECONDS),"Los dos jobs llegan a I/O con sólo dos conexiones SQL");
            assertEquals(0,hikari.getHikariPoolMXBean().getActiveConnections(),"Ningún invocante retiene conexión durante el procesador");
            tx.executeWithoutResult(t->{
                pidInspector.set(jdbc.queryForObject("SELECT pg_backend_pid()",Long.class));txInspector.set(jdbc.queryForObject("SELECT txid_current()",Long.class));
                jdbc.execute("SET LOCAL lock_timeout='2s'");
                assertTrue(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,lock));
                assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE organizacion_id=? AND estado='EN_PROCESO'",Integer.class,equipo.agente().idOrganizacion()));
                for(var s:List.of(uno,dos)){
                    String h=Idempotencia.huellaExacta("BROX-MATERIAL-IA-LOCK\u0000"+equipo.agente().idOrganizacion()+"\u0000"+s.operacion());
                    assertTrue(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,Long.parseUnsignedLong(h.substring(0,16),16)));
                }
            });
            terminar.countDown();assertEquals("DISPONIBLE",primera.get(30,TimeUnit.SECONDS).estado());assertEquals("DISPONIBLE",segunda.get(30,TimeUnit.SECONDS).estado());
        }finally{terminar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
        assertEquals(2,llamadas.get());assertEquals(2,controlesEjecutados.get());
        assertNotEquals(txPreparacion.get(),txInspector.get(),"La inspección usa otra transacción posterior al commit de preparación");
        EvidenciaC4DePrueba.transaccion("PREPARACION_CONFIRMADA",pidPreparacion.get(),txPreparacion.get());
        EvidenciaC4DePrueba.transaccion("INSPECCION_DURANTE_PROCESADOR",pidInspector.get(),txInspector.get());
    }
    @Test void mismoUuidNoCambiaContenidoNiActorYOtroTenantNoObtieneEstado(){
        var s=solicitud(Accion.REDACTAR_FICHA);ejecutar(s);
        assertThrows(ConflictoException.class,()->ejecutar(new Solicitud(s.operacion(),s.accion(),s.recurso(),s.versionOrigen(),null,"Otra instrucción TEST")));
        var colega=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).sesion(equipo.colega());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(s.operacion(),equipo.colega(),colega));
        var ajeno=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));var otro=ajeno.empresa();
        assertThrows(NoEncontradoException.class,()->servicio.consultar(s.operacion(),otro.agente(),ajeno.sesion(otro.agente())));
        assertEquals(1,llamadas.get());
    }
    @Test void laInvocacionConcurrenteObservaClaimComprometidoSinOtroProcesadorNiSqlLocks()throws Exception{
        var s=solicitud(Accion.REDACTAR_FICHA);var dentro=new CountDownLatch(1);var terminar=new CountDownLatch(1);
        ejecucion=p->{
            String h=Idempotencia.huellaExacta("BROX-MATERIAL-IA-LOCK\u0000"+equipo.agente().idOrganizacion()+"\u0000"+s.operacion());
            assertTrue(Boolean.TRUE.equals(tx.execute(t->jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,Long.parseUnsignedLong(h.substring(0,16),16)))));
            dentro.countDown();try{assertTrue(terminar.await(20,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return salida(p.accion());
        };
        try(var pool=Executors.newSingleThreadExecutor()){
            var primera=pool.submit(()->ejecutar(s));assertTrue(dentro.await(20,TimeUnit.SECONDS));
            assertEquals("EN_VERIFICACION",ejecutar(s).estado());terminar.countDown();assertEquals("DISPONIBLE",primera.get(30,TimeUnit.SECONDS).estado());
        }finally{terminar.countDown();}
        assertEquals(1,llamadas.get());
    }
    @Test void intencionMedicionYDiarioNoPuedenSobrescribirseNiEliminarse(){
        var s=solicitud(Accion.REDACTAR_FICHA);ejecutar(s);long org=equipo.agente().idOrganizacion();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE material_ia_operacion SET solicitud='{}' WHERE organizacion_id=?",org));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE material_ia_operacion SET medicion='{}' WHERE organizacion_id=?",org));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM material_ia_evento WHERE organizacion_id=?",org));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM material_ia_operacion WHERE organizacion_id=?",org));
    }
}
