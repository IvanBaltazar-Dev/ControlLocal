package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.colaboracion.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.colaboracion.*;
import com.controllocal.service.encargo.*;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.controllocal.domain.colaboracion.LadoColaboracion.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

/** Core/C2 reales, sin mock de fuente, permisos ni financiación externa. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class InvitacionEncargoC7IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired PlatformTransactionManager gestor;
    @Autowired ColaboracionService servicio;
    FormalizacionesDePrueba b;
    ActividadNegociacionDePrueba.Caso origen;
    OrigenesDePrueba.Equipo invitado;
    AlcanceColaboracion alcance;
    String solicitud;
    long demandasAntes,encargosAntes;
    @BeforeEach void iniciar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);
        origen=b.negociacion.mesa("VENTA",new BigDecimal("100000"),"PEN",false);
        var banco=new EncargosDePrueba(b.jdbc,b.encargos,gestor,contexto.getBean(com.controllocal.service.archivo.AccesoArchivoService.class),
                contexto.getBean(com.controllocal.web.almacen.AlmacenDocumentos.class),b.actuaciones,contexto.getBean(com.controllocal.service.soporte.Documentos.class));
        banco.habilitarExistente(origen.encargo(),new EncargosDePrueba.Caso(origen.propiedad(),OperacionInmobiliaria.VENTA,hoy(),hoy().plusMonths(6),true,
                EncargosDePrueba.venta(new BigDecimal("100000"),"PEN",new BigDecimal("0.03"),"PROPIETARIO_TEST","AGENCIA_TEST"),
                new EncargosDePrueba.Actuante(origen.agente(),origen.sesion()),new EncargosDePrueba.Actuante(origen.equipo().broker(),b.sesiones.de(origen.equipo().broker()))));
        invitado=new OrigenesDePrueba(b.jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class)).empresa();
        // Identidades declaradas explícitamente en cuentas técnicas TEST; no se infieren del nombre civil de Persona.
        for(var a:List.of(origen.agente(),invitado.agente()))b.jdbc.update("update cuenta_acceso set nombre_profesional_declarado=? where id_cuenta_acceso=(select id_usuario from usuario_organizacion where organizacion_id=? and id_persona=?)","Nombre profesional declarado TEST",a.idOrganizacion(),a.idPersona());
        alcance=new AlcanceColaboracion(Set.of(ENCARGO,INVITADO),Set.of("CONSULTAR"),Map.of(
                ENCARGO,Set.of("ENCARGO:operacion","ENCARGO:precio_pedido","nombreProfesional","inmobiliaria","canalComercial"),
                INVITADO,Set.of("nombreProfesional","inmobiliaria","canalComercial")),"RED_COLABORATIVA",Instant.now().minusSeconds(1),Instant.now().plusSeconds(3600));
        demandasAntes=b.jdbc.queryForObject("select count(*) from requerimiento_cliente",Long.class);
        encargosAntes=b.jdbc.queryForObject("select count(*) from captacion",Long.class);
        var ficha=b.encargos.consultar(origen.encargo(),origen.agente());
        long revision=b.jdbc.queryForObject("select id_revision_aplicable from captacion where id_captacion=?",Long.class,origen.encargo());
        var d=new ColaboracionService.DatosInvitacion(origen.encargo(),ficha.version(),invitado.agente().idOrganizacion(),invitado.agente().idRolOperativo(),
                new PactosDeEncargo.ReferenciaPacto(origen.encargo(),revision),"Declaración TEST: instrumento permite colaboración",alcance);
        var p=b.actuaciones.prepararInvitacionColaboracion(origen.agente(),d,origen.sesion(),procedencia());
        solicitud=b.humanas.confirmar(p,origen.agente(),origen.sesion(),ColaboracionService.Resultado.class).datos().solicitud();
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    Actor decisor(LadoColaboracion lado){return lado==ENCARGO?origen.equipo().broker():invitado.broker();}
    ColaboracionService.Resultado decidir(LadoColaboracion lado){
        var a=decisor(lado);var m=servicio.consultarMetadatos(solicitud,a);
        var d=new ColaboracionService.DatosDecision(solicitud,lado,ColaboracionService.Decision.ACEPTAR,alcance,"Aprobación TEST expresa",m.version(),m.versionAlcance(),m.parteActual().version());
        var s=b.sesiones.de(a);var p=b.actuaciones.prepararDecisionColaboracion(a,d,s,procedencia());
        return b.humanas.confirmar(p,a,s,ColaboracionService.Resultado.class).datos();
    }
    @Test void origenApruebaAntesDeEntregarInvitacionYNoSeFabricaDemanda(){
        assertTrue(servicio.invitacionesOriginadas(origen.equipo().broker(),1,100).stream().anyMatch(x->x.solicitud().equals(solicitud)));
        assertTrue(servicio.invitacionesOriginadas(origen.equipo().colega(),1,100).isEmpty());
        assertTrue(servicio.invitacionesOriginadas(invitado.broker(),1,100).isEmpty());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.invitacionesOriginadas(invitado.admin(),1,100));
        assertTrue(servicio.invitacionesRecibidas(invitado.agente(),1,100).isEmpty());
        assertThrows(NoEncontradoException.class,()->servicio.consultarAcuerdo(solicitud,invitado.agente()));
        decidir(ENCARGO);
        var lista=servicio.invitacionesRecibidas(invitado.agente(),1,100);
        assertEquals(1,lista.size());assertEquals(solicitud,lista.getFirst().solicitud());assertNull(lista.getFirst().datosConcedidos());
        assertTrue(servicio.invitacionesRecibidas(invitado.colega(),1,100).isEmpty());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarAcuerdo(solicitud,invitado.colega()));
        assertEquals(demandasAntes,b.jdbc.queryForObject("select count(*) from requerimiento_cliente",Long.class));
        assertEquals(encargosAntes,b.jdbc.queryForObject("select count(*) from captacion",Long.class));
        assertNull(b.jdbc.queryForObject("select id_requerimiento from solicitud_colaboracion where referencia=?::uuid",Long.class,solicitud));
    }
    @Test void dosAprobacionesRevelanSoloCamposElegidosYRevocarDetieneSiguienteLectura(){
        declararCanal(ENCARGO);decidir(ENCARGO);declararCanal(INVITADO);assertFalse(servicio.consultarAcuerdo(solicitud,invitado.agente()).efectiva());
        decidir(INVITADO);var c=servicio.consultarAcuerdo(solicitud,invitado.agente());assertTrue(c.efectiva());assertNotNull(c.datosConcedidos());
        assertEquals(Set.of("nombreProfesional","inmobiliaria","canalComercial"),c.datosConcedidos().get(INVITADO).keySet());
        assertEquals(alcance.campos().get(ENCARGO),c.datosConcedidos().get(ENCARGO).keySet());
        assertNull(servicio.consultarAcuerdo(solicitud,invitado.broker()).datosConcedidos());
        var a=invitado.broker();var m=servicio.consultarMetadatos(solicitud,a);var s=b.sesiones.de(a);
        var d=new ColaboracionService.DatosRevocacion(m.concesion(),INVITADO,m.alcanceConcedido(),"Retiro TEST",m.versionConcesion(),m.versionAlcance(),m.parteActual().version());
        var p=b.actuaciones.prepararRevocacionColaboracion(a,d,s,procedencia());b.humanas.confirmar(p,a,s,ColaboracionService.Resultado.class);
        var retirada=servicio.consultarAcuerdo(solicitud,origen.agente());assertFalse(retirada.efectiva());assertNull(retirada.datosConcedidos());
        assertNotNull(retirada.revocadaEn());assertEquals(2,retirada.decisiones().size());
    }
    @Test void agenteNoApruebaNiSeAdmitenCamposPrivadosOUnLadoFicticio(){
        decidir(ENCARGO);var m=servicio.consultarMetadatos(solicitud,invitado.agente());
        var d=new ColaboracionService.DatosDecision(solicitud,INVITADO,ColaboracionService.Decision.ACEPTAR,alcance,"Sin autoridad TEST",m.version(),m.versionAlcance(),m.parteActual().version());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarDecision(d,invitado.agente()));
        var falso=new ColaboracionService.DatosDecision(solicitud,DEMANDA,ColaboracionService.Decision.ACEPTAR,alcance,"No existe demanda",m.version(),m.versionAlcance(),m.parteActual().version());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarDecision(falso,invitado.broker()));
        var privado=new AlcanceColaboracion(alcance.destinatarios(),alcance.acciones(),Map.of(ENCARGO,Set.of("propietario"),INVITADO,Set.of("presupuesto")),alcance.finalidad(),alcance.desde(),alcance.hasta());
        assertThrows(ReglaNegocioException.class,()->servicio.validarDecision(new ColaboracionService.DatosDecision(solicitud,INVITADO,ColaboracionService.Decision.ACEPTAR,privado,"Campo privado",m.version(),m.versionAlcance(),m.parteActual().version()),invitado.broker()));
    }
    void declararCanal(LadoColaboracion lado){
        var a=lado==ENCARGO?origen.agente():invitado.agente();var m=servicio.consultarMetadatos(solicitud,a);var sid=b.sesiones.de(a);
        var d=new CanalesColaboracionService.DatosDeclaracion(solicitud,lado,m.version(),m.versionAlcance(),m.parteActual().version(),"Canal comercial TEST autorizado para esta colaboración");
        var p=b.actuaciones.prepararCanalColaboracion(a,d,sid,procedencia());b.humanas.confirmar(p,a,sid,CanalesColaboracionService.Declaracion.class);
    }
    @Test void nombreNoDeclaradoNoSeInfiereDelNombreCivil(){
        b.jdbc.update("update cuenta_acceso set nombre_profesional_declarado=null where id_cuenta_acceso=(select id_usuario from usuario_organizacion where organizacion_id=? and id_persona=?)",invitado.agente().idOrganizacion(),invitado.agente().idPersona());
        decidir(ENCARGO);decidir(INVITADO);var c=servicio.consultarAcuerdo(solicitud,invitado.agente());assertTrue(c.efectiva());assertFalse(c.datosConcedidos().get(INVITADO).containsKey("nombreProfesional"));assertFalse(c.datosConcedidos().get(INVITADO).containsKey("canalComercial"));
    }
    @Test void mismaConfirmacionConcurrenteCreaUnaConcesionYLaAutoridadRevocadaNoEntregaDatos()throws Exception{
        decidir(ENCARGO);var a=invitado.broker();var m=servicio.consultarMetadatos(solicitud,a);var sid=b.sesiones.de(a);
        var d=new ColaboracionService.DatosDecision(solicitud,INVITADO,ColaboracionService.Decision.ACEPTAR,alcance,"Aceptación TEST concurrente",m.version(),m.versionAlcance(),m.parteActual().version());
        var p=b.actuaciones.prepararDecisionColaboracion(a,d,sid,procedencia());var r=b.actuaciones.revisar(a,p.referencia(),sid);
        var prueba=new com.controllocal.service.actuacion.ActuacionesService.Confirmacion(r.propuesta().version(),r.propuesta().huella(),r.pruebaRevision(),"TEST-INVI-"+UUID.randomUUID());
        var ex=java.util.concurrent.Executors.newFixedThreadPool(2);var puerta=new java.util.concurrent.CountDownLatch(1);
        try{var uno=ex.submit(()->{puerta.await();return b.actuaciones.confirmar(a,p.referencia(),sid,prueba,procedencia());});var dos=ex.submit(()->{puerta.await();return b.actuaciones.confirmar(a,p.referencia(),sid,prueba,procedencia());});puerta.countDown();assertEquals(uno.get(30,java.util.concurrent.TimeUnit.SECONDS).resultado(),dos.get(30,java.util.concurrent.TimeUnit.SECONDS).resultado());}finally{ex.shutdownNow();}
        assertTrue(servicio.consultarAcuerdo(solicitud,invitado.agente()).efectiva());
        assertEquals(1,b.jdbc.queryForObject("select count(*) from concesion_colaboracion c join solicitud_colaboracion s using(id_solicitud_colaboracion) where s.referencia=?::uuid",Long.class,solicitud));
        b.jdbc.update("update usuario_organizacion set estado='I' where organizacion_id=? and id_persona=?",invitado.agente().idOrganizacion(),invitado.agente().idPersona());
        assertThrows(NoEncontradoException.class,()->servicio.consultarAcuerdo(solicitud,invitado.agente()));
    }
}
