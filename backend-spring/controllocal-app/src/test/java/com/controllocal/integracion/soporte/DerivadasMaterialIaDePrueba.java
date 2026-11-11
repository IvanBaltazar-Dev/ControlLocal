package com.controllocal.integracion.soporte;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.MaterialIaService.Solicitud;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Factory TEST para C1/E44/B0/144/145 reales. No es bean, seed ni proveedor productivo.
 * Únicos procesador y QC son sintéticos sin red. Avisos/legitimación y AlmacenMap los
 * declara el consumidor; FuenteCore y Autoridad conservan sus guardas de producción.
 * Construir/configurar antes de iniciar hilos. Nunca envolver generar en una TX padre.
 */
public final class DerivadasMaterialIaDePrueba {
    private static final String PERFIL="DERIVADA_CORE_REAL_TEST",PROVEEDOR="DERIVADA_TEST_SIN_RED";
    private static final String PROPIEDAD_ENV="derivadas-material-ia-fixture-test";
    /** Capacidad finita exclusivamente del banco de memoria; no es calibración ni oferta. */
    private static final long CAPACIDAD_FISICA_TEST=16_000_000;
    private final ApplicationContext contexto;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PlanesFinanciadosDePrueba planes;
    private final RegistroMaterialIaRepository registro;
    private final FuentesMaterialIaCore fuente;
    private final EvidenciasTratamientoMaterialIa evidencias;
    private final MaterialIaServiceImpl coordinador;
    private final java.util.function.Function<RegistroMaterialIaRepository,MaterialIaServiceImpl> fabrica;
    private final String version,namespace;
    private final byte[] salida;

    public record Caso(Solicitud solicitud,Snapshot snapshot,Resultado resultado,byte[] salida,UUID archivo) {
        public Caso { salida=salida.clone(); }
        @Override public byte[] salida(){return salida.clone();}
    }

    public DerivadasMaterialIaDePrueba(ApplicationContext contexto,JdbcTemplate jdbc,PlatformTransactionManager gestor){
        fueraDeSql();this.contexto=contexto;this.jdbc=jdbc;this.tx=new TransactionTemplate(gestor);
        this.planes=new PlanesFinanciadosDePrueba(jdbc,gestor);this.version=UUID.randomUUID().toString();
        this.namespace=contexto.getBean(AlmacenDocumentos.class).identidadTecnica();
        if(namespace==null||!namespace.matches("[0-9a-f]{64}"))throw new IllegalStateException("Configura el namespace real del AlmacenMap TEST antes de construir el banco.");
        if(contexto.getBeansOfType(FuentesMaterialIa.class).size()!=1)throw new IllegalStateException("El banco requiere la FuenteCore única, sin una Fuente mock adicional.");
        this.fuente=contexto.getBean(FuentesMaterialIaCore.class);
        this.evidencias=contexto.getBean(EvidenciasTratamientoMaterialIa.class);
        this.registro=contexto.getBean(RegistroMaterialIaRepository.class);
        this.salida=imagen();
        var entorno=(ConfigurableEnvironment)contexto.getEnvironment();
        entorno.getPropertySources().addFirst(new MapPropertySource(PROPIEDAD_ENV,Map.of(
                "controllocal.ia.material.mejorar_foto.perfil",PERFIL,
                "controllocal.ia.material.mejorar_foto.version",version)));
        configurarPerfilTest();
        var documentos=contexto.getBean(Documentos.class);var financiero=contexto.getBean(PresupuestoIaRepository.class);
        var perfiles=contexto.getBean(PerfilesMaterialIa.class);var autoridad=contexto.getBean(AutoridadMaterialIa.class);
        // Gasto habilitado sólo en este objeto TEST y para el proveedor local declarado abajo.
        // No cambia la bandera real del bean ni el Environment productivo de gasto.
        var presupuesto=new PresupuestoIaService(financiero,contexto.getBean(ContextoPresupuestoB0.class),
                List.of(autoridad),documentos,new DerechosDePlanB0(financiero,documentos),true);
        ProveedorMaterialIa proveedor=new ProveedorMaterialIa(){
            public String identificador(){return PROVEEDOR;}
            public boolean atiende(Accion a,String p,String v){return a==Accion.MEJORAR_FOTO&&PERFIL.equals(p)&&version.equals(v);}
            public Salida ejecutar(Peticion p,java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> observador){
                fueraDeSql();if(p.accion()!=Accion.MEJORAR_FOTO||p.fotoAutorizada()==null)throw new IllegalStateException("El procesador TEST requiere foto autorizada real.");
                var medicion=new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),PROVEEDOR,"SINTETICO_NO_MODELO_REAL","TEST-1",
                        List.of(new Medicion("unidades_test","u",BigDecimal.ONE,null)),
                        List.of(new Tarifa("unidades_test","u",new BigDecimal("0.1"),BigDecimal.ONE)),null,"TEST-SIN-RED:"+p.claveExterna())));
                return new Salida(null,"image/png",salida,"PROCESO_SINTETICO_NO_FIDELIDAD_REAL","TEST-1",medicion);
            }
        };
        ControlesMaterialIa control=new ControlesMaterialIa(){
            public boolean atiende(Accion a,String p,String v){return proveedor.atiende(a,p,v);}
            public Control evaluar(Solicitud s,Snapshot f,ProveedorMaterialIa.Salida r){
                fueraDeSql();return new Control(Decision.APROBADA,"QC_SINTETICO_NO_ACEPTACION_IA",Instant.now(),
                        Arrays.stream(Aspecto.values()).map(a->new Senal(a,EstadoSenal.EVALUADA,false,
                                "TEST SINTETICO: NO CONTROL REAL DE CALIDAD/FIDELIDAD","TEST-QC:"+s.operacion()+":"+a)).toList());
            }
        };
        this.fabrica=repositorio->new MaterialIaServiceImpl(repositorio,presupuesto,contexto.getBean(ContextoPresupuestoB0.class),autoridad,perfiles,
                documentos,List.of(proveedor),List.of(control),List.of(contexto.getBean(ResultadosMaterialIa.class)),gestor);
        this.coordinador=fabrica.apply(registro);
    }

    /** AvisosTEST debe declarar exactamente este destino y legitimación de los actores/recursos de su banco. */
    public DestinoTratamiento destino(){return new DestinoTratamiento(PROVEEDOR,PERFIL,version);}

    /** Prerrequisito antes de subir el original real por GaleriaDeFotos, sin dependencia circular de generar. */
    public void prepararAlmacenamiento(Actor actor){
        fueraDeSql();planes.asegurarTeam(actor.idOrganizacion());
        tx.executeWithoutResult(t->{
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM b0_capacidad_fisica_actual WHERE namespace_hash=?)",Boolean.class,namespace)))return;
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM intento_salida_binaria WHERE almacen_huella=?)",Boolean.class,namespace)))
                throw new IllegalStateException("Prepara la política física TEST con el mapa vacío ANTES de subir originales; no se inventa ocupación cero sobre archivos previos.");
            Instant ahora=Instant.now();var desde=utc(ahora.minusSeconds(3600));var hasta=utc(ahora.plus(Duration.ofDays(1)));
            jdbc.update("""
                INSERT INTO b0_politica_capacidad_fisica(namespace_hash,version,capacidad_bytes,ocupacion_conciliada_bytes,conciliada_en,
                 evidencia_calibracion,evidencia_aprobacion,evidencia_financiacion,evidencia_conciliacion,desde,hasta,habilitada)
                 VALUES(?,?,?,0,?,'TEST NO CALIBRACION','TEST NO APROBACION PRODUCTIVA','TEST SIN FINANCIACION REAL','TEST MAPA INICIAL VACIO',?,?,true)
                """,namespace,version,CAPACIDAD_FISICA_TEST,desde,desde,hasta);
            jdbc.update("INSERT INTO b0_capacidad_fisica_actual(namespace_hash,version) VALUES(?,?)",namespace,version);
        });
    }

    public Caso generar(long propiedad,UUID original,Actor actor,ContextoSesion sid){
        return generarCon(coordinador,propiedad,original,actor,sid,false);
    }

    /**
     * Integración144/146 de una fuente histórica SINTÉTICA: inserta la primera MEDIDA
     * con fecha TEST, nunca UPDATE. Si el original/job se creó hoy, NO demuestra una
     * cadena cronológica de generación envejecida ni que realmente transcurrieran7d.
     * La evidencia del evento registra expresamente esa limitación. Sólo banco TEST.
     */
    public Caso generarConObservacionHistoricaTEST(long propiedad,UUID original,Actor actor,ContextoSesion sid,Instant observadaEnTest){
        fueraDeSql();if(observadaEnTest==null||!observadaEnTest.isBefore(Instant.now()))throw new IllegalArgumentException("Indica una fecha histórica exclusivamente TEST.");
        var temporalTest=new RegistroMaterialIaRepository(jdbc,contexto.getBean(com.controllocal.persistence.archivo.DiarioBinarioTecnico.class)){
            @Override public long evidencia(Operacion o,String estado,String evidencia){
                if(!"MEDIDA".equals(estado))return super.evidencia(o,estado,evidencia);
                if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='MEDIDA')",Boolean.class,o.organizacion(),o.id())))
                    throw new IllegalStateException("El fixture sólo inserta la primera MEDIDA; no cambia otra fecha ni historia.");
                String datos=contexto.getBean(Documentos.class).objetoCanonico(Map.of("origen","TEST_SIMULACION_TIEMPO_TRANSCURRIDO",
                        "observadaEnTest",observadaEnTest,"cronologiaCompletaGeneracion",false,"evidenciaOriginal",evidencia));
                return jdbc.queryForObject("INSERT INTO material_ia_evento(organizacion_id,id_operacion,estado,evidencia,registrada_en) VALUES(?,?,?,?,?) RETURNING id_evento",Long.class,
                        o.organizacion(),o.id(),estado,datos,utc(observadaEnTest));
            }
        };
        return generarCon(fabrica.apply(temporalTest),propiedad,original,actor,sid,true);
    }

    private Caso generarCon(MaterialIaServiceImpl ejecutor,long propiedad,UUID original,Actor actor,ContextoSesion sid,boolean tiempoHistoricoTest){
        fueraDeSql();prepararAlmacenamiento(actor);
        var inicial=new Solicitud(UUID.randomUUID(),Accion.MEJORAR_FOTO,new MaterialIaService.Recurso("PROPIEDAD",Long.toString(propiedad)),null,original,null);
        var aviso=fuente.presentarTratamiento(inicial,actor,sid);
        evidencias.capturar(new EvidenciasTratamientoMaterialIa.Captura(UUID.randomUUID(),aviso.referencia(),aviso.versionCobertura(),
                EvidenciasTratamientoMaterialIa.Decision.ACEPTAR),actor,sid,Procedencia.deLaPantalla());
        var preparada=ejecutor.preparar(inicial,actor,sid);
        var solicitud=new Solicitud(inicial.operacion(),inicial.accion(),inicial.recurso(),preparada.versionOrigen(),original,null);
        var resultado=ejecutor.solicitar(solicitud,actor,sid);
        if(!"DISPONIBLE".equals(resultado.estado())&&!(tiempoHistoricoTest&&"RESULTADO_NO_DISPONIBLE".equals(resultado.estado())))throw new IllegalStateException("El banco no conservó una derivada privada controlada.");
        UUID archivo=jdbc.queryForObject("SELECT archivo_ref FROM derivada_material_ia WHERE organizacion_id=? AND id_operacion=?",UUID.class,actor.idOrganizacion(),solicitud.operacion());
        if(resultado.archivo()!=null&&!resultado.archivo().equals(archivo))throw new IllegalStateException("La entrega no coincide con el vínculo durable TEST.");
        var snapshot=contexto.getBean(Documentos.class).comoObjeto(registro.obtener(actor.idOrganizacion(),solicitud.operacion()).orElseThrow().snapshot(),Snapshot.class);
        return new Caso(solicitud,snapshot,resultado,salida,archivo);
    }

    private void configurarPerfilTest(){
        tx.executeWithoutResult(t->{
            Instant ahora=Instant.now();var desde=utc(ahora.minusSeconds(3600));var hasta=utc(ahora.plus(Duration.ofDays(1)));UUID fondo=UUID.randomUUID();
            jdbc.update("INSERT INTO b0_fondo_tecnico(id_fondo,moneda,financiado,desde,hasta,evidencia_financiacion,habilitado) VALUES(?,'USD',100,?,?,'TEST SIN FONDOS REALES',true)",fondo,desde,hasta);
            jdbc.update("""
                INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,
                 limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado)
                 VALUES(?,?,?,1,'[{"clase":"unidades_test","unidad":"u","importeUsd":1,"porCantidad":1}]'::jsonb,4,1,1,
                  'TEST NO CALIBRACION','TEST NO APROBACION OPERATIVA',true)
                """,PERFIL,version,fondo);
            jdbc.update("INSERT INTO b0_perfil_actual(perfil,version) VALUES(?,?) ON CONFLICT(perfil) DO UPDATE SET version=excluded.version",PERFIL,version);
            jdbc.update("INSERT INTO b0_limite_salida_ia(perfil,version_perfil,maximo_bytes,evidencia_calibracion,evidencia_aprobacion,desde,hasta,habilitado) VALUES(?,?,?,'TEST SALIDA PNG MEDIDA NO CALIBRACION','TEST NO APROBACION PRODUCTIVA',?,?,true)",PERFIL,version,salida.length,desde,hasta);
        });
    }
    private static byte[] imagen(){
        var foto=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);for(int x=0;x<32;x++)for(int y=0;y<32;y++)foto.setRGB(x,y,0xff4422);
        try{var out=new ByteArrayOutputStream();if(!javax.imageio.ImageIO.write(foto,"png",out))throw new IllegalStateException("No hay codificador PNG TEST.");return out.toByteArray();}
        catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
    private static void fueraDeSql(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("El banco no suspende una transacción padre para generar.");}
}
