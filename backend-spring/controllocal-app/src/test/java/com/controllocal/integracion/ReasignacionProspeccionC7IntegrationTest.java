package com.controllocal.integracion;

import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;

import com.controllocal.service.*;
import com.controllocal.service.actividad.ReasignacionDeProspeccion;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class ReasignacionProspeccionC7IntegrationTest extends OrigenConversionC7IntegrationTest {
    @org.springframework.test.context.DynamicPropertySource static void baseEspecifica(org.springframework.test.context.DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    ReasignacionDeProspeccion casoGobierno(){return contexto.getBean(ReasignacionDeProspeccion.class);}
    ReasignacionDeProspeccion.Datos datos(long id,Actor a,long nuevo){var p=casoGobierno().consultar(id,a);return new ReasignacionDeProspeccion.Datos(id,p.version(),p.responsable(),nuevo,"Continuidad de la misma gestión TEST");}
    @Test void conservaIdentidadFechasEHistorialSinTransferirElEncargoNiReiniciarCadencia(){
        var c=caso();var p=prospectar(c,c.agente());var seguimiento=prospecciones.seguimientos(p.id(),c.agente());
        var broker=c.equipo().broker();var d=datos(p.id(),broker,c.equipo().colega().idRolOperativo());String k=clave();
        var original=casoGobierno().reasignar(d,broker,b.sesiones.de(broker),k,procedencia());
        assertEquals(original,casoGobierno().reasignar(d,broker,b.sesiones.de(broker),k,procedencia()));
        assertEquals(p.id(),original.id());assertEquals(d.versionEsperada()+1,original.version());
        var nuevo=prospecciones.obtener(p.id(),c.equipo().colega());assertEquals(p.estado(),nuevo.estado());assertEquals(p.fechaContacto(),nuevo.fechaContacto());assertEquals(p.fechaRecontacto(),nuevo.fechaRecontacto());assertEquals(p.idCaptacion(),nuevo.idCaptacion());
        assertEquals(seguimiento,prospecciones.seguimientos(p.id(),c.equipo().colega()));
        assertThrows(AccesoNoAutorizadoException.class,()->prospecciones.obtener(p.id(),c.agente()));
        assertEquals(c.agente().idRolOperativo(),b.encargos.consultar(c.encargo(),c.agente()).responsable());
        assertEquals(1,b.jdbc.queryForObject("select count(*) from evento_dominio where entidad_tipo='PROSPECCION' and entidad_id=? and tipo='PROSPECCION_REASIGNADA'",Long.class,p.id()));
    }
    @Test void agenteNoReasignaYVersionAntiguaNoMueveLaGestionDeNuevo(){
        var c=caso();var p=prospectar(c,c.agente());var broker=c.equipo().broker();var d=datos(p.id(),broker,c.equipo().colega().idRolOperativo());
        assertThrows(AccesoNoAutorizadoException.class,()->casoGobierno().reasignar(d,c.agente(),c.sesion(),clave(),procedencia()));
        casoGobierno().reasignar(d,broker,b.sesiones.de(broker),clave(),procedencia());
        assertThrows(ConflictoException.class,()->casoGobierno().reasignar(d,broker,b.sesiones.de(broker),clave(),procedencia()));
    }
    @Test void rollbackYDosTransaccionesNoDuplicanLaReasignacion()throws Exception{
        var c=caso();var p=prospectar(c,c.agente());var broker=c.equipo().broker();var sid=b.sesiones.de(broker);var d=datos(p.id(),broker,c.equipo().colega().idRolOperativo());
        new TransactionTemplate(gestor).executeWithoutResult(tx->{casoGobierno().reasignar(d,broker,sid,clave(),procedencia());tx.setRollbackOnly();});
        assertEquals(d.versionEsperada(),casoGobierno().consultar(p.id(),broker).version());
        var ex=Executors.newFixedThreadPool(2);var puerta=new CountDownLatch(1);String k=clave();
        try{List<Future<ReasignacionDeProspeccion.Resultado>> fs=new ArrayList<>();for(int i=0;i<2;i++)fs.add(ex.submit(()->{puerta.await();return casoGobierno().reasignar(d,broker,sid,k,procedencia());}));puerta.countDown();assertEquals(fs.get(0).get(30,TimeUnit.SECONDS),fs.get(1).get(30,TimeUnit.SECONDS));}finally{ex.shutdownNow();}
        assertEquals(d.versionEsperada()+1,casoGobierno().consultar(p.id(),broker).version());
    }
    @Test void adminGobiernaSinAbrirExpedienteYOtraOrganizacionNoObtieneLaReferencia(){
        var c=caso();var p=prospectar(c,c.agente());var admin=c.equipo().admin();
        assertThrows(AccesoNoAutorizadoException.class,()->prospecciones.obtener(p.id(),admin));
        var d=datos(p.id(),admin,c.equipo().colega().idRolOperativo());casoGobierno().reasignar(d,admin,b.sesiones.de(admin),clave(),procedencia());
        var ajena=caso();assertThrows(NoEncontradoException.class,()->casoGobierno().consultar(p.id(),ajena.equipo().broker()));
        assertThrows(AccesoNoAutorizadoException.class,()->prospecciones.obtener(p.id(),admin));
    }
}
