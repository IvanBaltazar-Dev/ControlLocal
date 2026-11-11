package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.RequerimientoService.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** RF030: el fin de la búsqueda no finaliza intereses ni borra historia de Oportunidad. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class DemandaConOportunidadC3IntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.actuacion.ActuacionesService actuacionesFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.soporte.Documentos documentosFirmaTest;

    private static final Procedencia PROC=Procedencia.deLaPantalla();
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired GobiernoCatalogoCapturaService catalogos;
    @Autowired EncargoService encargos;
    @Autowired ClienteService clientes;
    @Autowired OportunidadService oportunidades;
    @Autowired RequerimientoService demandas;
    @Autowired ActuacionesService actuaciones;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired Documentos documentos;

    @Test void retirarConservaOportunidadAbiertaYAntecedenteConHistoria(){conservar(TipoFinDemanda.RETIRADA);}
    @Test void finalizarConservaOportunidadAbiertaYAntecedenteConHistoria(){conservar(TipoFinDemanda.FINALIZADA);}

    private void conservar(TipoFinDemanda tipo) {
        var identidades=new OrigenesDePrueba(jdbc,gestor,captura,contextos);var equipo=identidades.empresa();Actor actor=equipo.agente();
        var sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        sesiones.de(equipo.admin());ContextoSesion broker=sesiones.de(equipo.broker()),sesion=sesiones.de(actor);
        long propiedad=departamento(actor);
        new PreparacionPublicacionDePrueba(jdbc,propiedades,encargos,catalogos,contextos,cuentas,factores,cifrado,gestor, archivosFirmaTest, almacenFirmaTest, actuacionesFirmaTest, documentosFirmaTest).prepararDepartamento(propiedad,actor);
        var banco=new EncargosDePrueba(jdbc,encargos,gestor, archivosFirmaTest, almacenFirmaTest, actuacionesFirmaTest, documentosFirmaTest);
        var habilitado=banco.habilitar(new EncargosDePrueba.Caso(propiedad,OperacionInmobiliaria.ALQUILER,hoy(),hoy().plusMonths(6),false,
                EncargosDePrueba.alquiler(new BigDecimal("2700"),"PEN",BigDecimal.ONE,"Titular TEST","Agencia TEST"),
                new EncargosDePrueba.Actuante(actor,sesion),new EncargosDePrueba.Actuante(equipo.broker(),broker)));

        // Se declara un CLIENTE para este caso. No se convierte un contacto parcial en rol cliente.
        // Documento P identificado como TEST; no se inventa un DNI ni una identidad real.
        var cliente=clientes.registrar(new ClienteService.DatosCliente("N","P","TEST-"+UUID.randomUUID().toString().substring(0,16),
                "Cliente explícito TEST RF030","999000882",null,null,false,true,"A"),actor);
        var referencia=new Referencia(TipoReferencia.CLIENTE,cliente.id(),null,null,null);
        var datos=new DatosRequerimiento(null,null,null,null,null,null,"ALQUILER",List.of("DEPARTAMENTO"),null,null,null,
                null,null,null,null,"Búsqueda TEST que puede retirarse sin retirar intereses",null,List.of(),referencia,null,null,null,null,Set.of());
        var demanda=demandas.crear(datos,actor,sesion,clave(),PROC);
        assertEquals(cliente.id(),demanda.idCliente());
        var humanas=new ActuacionesHumanasC4DePrueba(actuaciones,documentos);
        var datosAnterior=new OportunidadService.DatosOportunidad(codigo("ANT"),habilitado.encargo(),
                encargos.consultar(habilitado.encargo(),actor).version(),referencia,
                "Interés anterior TEST por gestión directa",null,demanda.id(),demanda.version(),"DIRECTO",null,null,
                new OportunidadService.EvidenciaInteres("Manifestación anterior de interés TEST",null));
        var anterior=humanas.confirmar(actuaciones.prepararAperturaOportunidad(actor,datosAnterior,sesion,PROC),
                actor,sesion,OportunidadService.FichaOportunidad.class).datos();
        var noContinuidad=new OportunidadService.NoContinuidad(anterior.id(),anterior.version(),"O",
                "El primer interés TEST terminó; se conserva su antecedente",hoy());
        var cerrada=humanas.confirmar(actuaciones.prepararNoContinuidadOportunidad(actor,noContinuidad,sesion,PROC),
                actor,sesion,OportunidadService.FichaOportunidad.class).datos();
        var datosAbierta=new OportunidadService.DatosOportunidad(codigo("ACT"),habilitado.encargo(),
                encargos.consultar(habilitado.encargo(),actor).version(),referencia,
                "Nuevo interés TEST declarado expresamente por gestión directa",null,demanda.id(),demanda.version(),"DIRECTO",null,null,
                new OportunidadService.EvidenciaInteres("Nueva manifestación de interés TEST",null));
        var abierta=humanas.confirmar(actuaciones.prepararAperturaOportunidad(actor,datosAbierta,sesion,PROC),
                actor,sesion,OportunidadService.FichaOportunidad.class).datos();
        assertEquals("N",cerrada.estado());assertEquals("A",abierta.estado());
        assertEquals(demanda.id(),cerrada.idDemanda());assertEquals(demanda.id(),abierta.idDemanda());
        var antes=instantanea(actor,demanda.id());
        assertEquals(2,antes.oportunidades().size());assertEquals(1,antes.motivos().size());assertFalse(antes.historia().isEmpty());

        long versionObservada=demanda.version();
        var propuesta=actuaciones.prepararFinDemanda(actor,new FinDemanda(demanda.id(),versionObservada,tipo,hoy(),"Fin de la demanda declarado TEST"),sesion,PROC);
        var revision=actuaciones.revisar(actor,propuesta.referencia(),sesion);
        var confirmacion=new ActuacionesService.Confirmacion(revision.propuesta().version(),revision.propuesta().huella(),revision.pruebaRevision(),clave());
        var resultado=actuaciones.confirmar(actor,propuesta.referencia(),sesion,confirmacion,PROC);
        assertEquals("EXITO",resultado.estado(),()->"ref="+resultado.referencia()+", estado="+resultado.estado());
        var despues=demandas.buscar(demanda.id(),actor);assertEquals(tipo==TipoFinDemanda.RETIRADA?"R":"F",despues.estado());
        assertEquals(versionObservada+1,despues.version());assertEquals(demanda,demandas.consultar(demanda.id(),versionObservada,null,actor));
        assertEquals(antes,instantanea(actor,demanda.id()));
        assertEquals("A",oportunidades.obtener(abierta.id(),actor).estado());assertEquals("N",oportunidades.obtener(cerrada.id(),actor).estado());
        assertEquals(resultado,actuaciones.confirmar(actor,propuesta.referencia(),sesion,confirmacion,PROC));
        assertEquals(antes,instantanea(actor,demanda.id()));
    }
    private record Antecedentes(List<String> oportunidades,List<String> motivos,List<String> historia,List<String> encargos) { }
    private Antecedentes instantanea(Actor actor,long demanda) {
        // Ambas aperturas aportan Cliente y Demanda/version reales al protocolo de revisión.
        // La instantánea conserva además todos los antecedentes privados de esta organización TEST.
        var ids=jdbc.queryForList("select o.id_oportunidad from oportunidad_comercial o join requerimiento_cliente r on r.organizacion_id=o.organizacion_id and r.id_rol_cliente=o.id_rol_cliente where r.organizacion_id=? and r.id_requerimiento=? order by o.id_oportunidad",Long.class,actor.idOrganizacion(),demanda);
        assertEquals(2,ids.size());
        return new Antecedentes(jdbc.queryForList("select to_jsonb(o)::text from oportunidad_comercial o where o.organizacion_id=? order by o.id_oportunidad",String.class,actor.idOrganizacion()),
                jdbc.queryForList("select to_jsonb(m)::text from motivo_no_continuidad m where m.organizacion_id=? order by m.id_motivo",String.class,actor.idOrganizacion()),
                jdbc.queryForList("select to_jsonb(h)::text from historial_estado h where h.organizacion_id=? and h.entidad_tipo=? order by h.id_historial",String.class,actor.idOrganizacion(),OportunidadComercial.ENTIDAD_TIPO),
                jdbc.queryForList("select to_jsonb(c)::text from captacion c where c.organizacion_id=? order by c.id_captacion",String.class,actor.idOrganizacion()));
    }
    private long departamento(Actor actor) {
        long titular=new TransactionTemplate(gestor).execute(t->{long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Titular declarado TEST RF030') returning id_persona",Long.class,actor.idOrganizacion());
            return jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'PROPIETARIO',?) returning id_persona_rol",Long.class,actor.idOrganizacion(),persona,hoy());});
        return propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),PROC,null,"DEPARTAMENTO","VIVIENDA","Inmueble TEST con Encargo legítimo",
                new PropiedadUniversalService.Ubicacion("Dirección privada TEST "+UUID.randomUUID(),"Surco",null,null,null,"101",null,null,null),
                List.of(new PropiedadUniversalService.Titular(titular,new BigDecimal("100"),false)),List.of(),List.of(),null),actor).idPropiedad();
    }
    private static LocalDate hoy(){return OrigenesDePrueba.hoy();}
    private static String clave(){return "rf030-oportunidad-test-"+UUID.randomUUID();}
    private static String codigo(String prefijo){return "RF030-"+prefijo+"-"+UUID.randomUUID().toString().substring(0,8);}
}
