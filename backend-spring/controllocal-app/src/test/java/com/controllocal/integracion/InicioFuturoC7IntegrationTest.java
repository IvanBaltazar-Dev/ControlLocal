package com.controllocal.integracion;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.encargo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.AopTestUtils;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fecha efectiva del inicio separada de la observación; mismo decisor y evidencia C2. */
@TestPropertySource(properties="controllocal.encargo.observador-inicios.habilitado=false")
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class InicioFuturoC7IntegrationTest extends OrigenConversionC7IntegrationTest {
    @org.springframework.test.context.DynamicPropertySource static void baseEspecifica(org.springframework.test.context.DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    EncargoServiceImpl servicio;
    LocalDate fecha;
    @BeforeEach void relojReal(){servicio=contexto.getBean(EncargoServiceImpl.class);fecha=hoy();}
    @AfterEach void restaurar(){ReflectionTestUtils.setField((Object)AopTestUtils.getUltimateTargetObject(servicio),"reloj",Clock.system(ZoneId.of("America/Lima")));}
    void reloj(LocalDate d){ReflectionTestUtils.setField((Object)AopTestUtils.getUltimateTargetObject(servicio),"reloj",Clock.fixed(d.atTime(12,0).atZone(ZoneId.of("America/Lima")).toInstant(),ZoneId.of("America/Lima")));}
    ActividadNegociacionDePrueba.Caso futuro(){
        var c=caso();var p=prospectar(c,c.agente());prospecciones.marcarCaptado(p.id(),c.encargo(),null,c.agente());
        var presentada=banco().presentarExistente(c.encargo(),condiciones(c,OperacionInmobiliaria.VENTA,fecha.plusDays(2),true));
        var f=Objects.requireNonNull(presentada.peticion().firma());var broker=c.equipo().broker();var sid=b.sesiones.de(broker);
        var datos=new EncargoService.Decision("APROBAR",presentada.peticion().version(),b.encargos.consultar(c.encargo(),broker).version(),"Inicio futuro TEST",new EncargoService.RevisionManuscrita(f.revisionPresentacion(),f.huellaManifestacion(),true));
        var propuesta=b.actuaciones.prepararDecisionEncargo(broker,presentada.peticion().id(),datos,sid,procedencia());b.humanas.confirmar(propuesta,broker,sid,EncargoService.Peticion.class);
        assertEquals(0,prueba(c.encargo()));assertFalse(b.encargos.consultarVigencia(c.encargo(),fecha,c.agente()).habilitado());return c;
    }
    @Test void noAnticipaYRecuperaInicioAcreditadoTrasCaidaSinInventarFechaDeRegistro(){
        var c=futuro();assertFalse(servicio.observarInicioAprobado(c.agente().idOrganizacion(),c.encargo()));
        reloj(fecha.plusDays(4));
        assertTrue(servicio.iniciosPendientes(0).stream().anyMatch(x->x.getOrganizacion()==c.agente().idOrganizacion()&&x.getEncargo()==c.encargo()));
        assertTrue(servicio.iniciosPendientes(c.encargo()).stream().allMatch(x->x.getEncargo()>c.encargo()));
        new com.controllocal.app.arranque.ObservadorDeInicios(servicio).observar();
        assertFalse(servicio.observarInicioAprobado(c.agente().idOrganizacion(),c.encargo()));
        assertEquals(fecha.plusDays(2),b.jdbc.queryForObject("select fecha_habilitacion from encargo_primera_habilitacion where id_captacion=?",LocalDate.class,c.encargo()));
        assertEquals(fecha.plusDays(4),b.jdbc.queryForObject("select (registrada_en at time zone 'America/Lima')::date from encargo_primera_habilitacion where id_captacion=?",LocalDate.class,c.encargo()));
        assertEquals(1,prueba(c.encargo()));
    }
    @Test void dosObservadoresConcurrentesConservanUnSoloHecho()throws Exception{
        var c=futuro();reloj(fecha.plusDays(2));var ex=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try{List<Future<Boolean>> fs=new ArrayList<>();for(int i=0;i<2;i++)fs.add(ex.submit(()->{inicio.await();return servicio.observarInicioAprobado(c.agente().idOrganizacion(),c.encargo());}));inicio.countDown();int n=0;for(var f:fs)if(f.get(30,TimeUnit.SECONDS))n++;assertEquals(1,n);assertEquals(1,prueba(c.encargo()));}finally{ex.shutdownNow();}
    }
    @Test void fuentesCambiadasNoAcreditanUnInicioHistoricoTrasCaida(){
        var c=futuro();
        // Cambio explícito de metadato TEST posterior; no falsea vigencia ni registra una habilitación.
        b.jdbc.update("update persona set fecha_actualizacion=clock_timestamp() where organizacion_id=? and id_persona=?",c.agente().idOrganizacion(),c.agente().idPersona());
        reloj(fecha.plusDays(4));assertFalse(servicio.observarInicioAprobado(c.agente().idOrganizacion(),c.encargo()));assertEquals(0,prueba(c.encargo()));
    }

    void reasignarEn(ActividadNegociacionDePrueba.Caso c,LocalDate dia){
        var g=contexto.getBean(com.controllocal.service.actividad.ReasignacionDeProspeccion.class);
        Object target=AopTestUtils.getUltimateTargetObject(g);
        ReflectionTestUtils.setField(target,"reloj",Clock.fixed(dia.atTime(12,0).atZone(ZoneId.of("America/Lima")).toInstant(),ZoneId.of("America/Lima")));
        try{long p=b.encargos.consultar(c.encargo(),c.agente()).origen().prospeccion();var broker=c.equipo().broker();var actual=g.consultar(p,broker);
            g.reasignar(new com.controllocal.service.actividad.ReasignacionDeProspeccion.Datos(p,actual.version(),actual.responsable(),c.equipo().colega().idRolOperativo(),"Continuidad TEST antes/después del inicio"),broker,b.sesiones.de(broker),clave(),procedencia());
        }finally{ReflectionTestUtils.setField(target,"reloj",Clock.system(ZoneId.of("America/Lima")));}
    }
    long responsableHistorico(long encargo){return b.jdbc.queryForObject("select id_rol_responsable_prospeccion from encargo_primera_habilitacion where id_captacion=?",Long.class,encargo);}
    @Test void reasignarAntesDelInicioAtribuyeUnaVezAlResponsableDeEseInicio(){
        var c=futuro();reasignarEn(c,fecha.plusDays(1));reloj(fecha.plusDays(4));
        assertTrue(servicio.observarInicioAprobado(c.agente().idOrganizacion(),c.encargo()));assertEquals(c.equipo().colega().idRolOperativo(),responsableHistorico(c.encargo()));
        assertEquals(c.agente().idRolOperativo(),b.encargos.consultar(c.encargo(),c.agente()).responsable());assertEquals(1,prueba(c.encargo()));
    }
    @Test void reasignarDespuesDelInicioNoTrasladaLaConversionHistoricaDuranteUnaCaida(){
        var c=futuro();reasignarEn(c,fecha.plusDays(3));reloj(fecha.plusDays(4));
        assertTrue(servicio.observarInicioAprobado(c.agente().idOrganizacion(),c.encargo()));assertEquals(c.agente().idRolOperativo(),responsableHistorico(c.encargo()));
        assertFalse(servicio.observarInicioAprobado(c.agente().idOrganizacion(),c.encargo()));assertEquals(1,prueba(c.encargo()));
    }
}
