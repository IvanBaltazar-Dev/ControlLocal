package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.service.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaHito;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.domain.comercial.TipoHitoContractual.CONTRATO_ALQUILER;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;

/** SQL/codec financiero reales. Las filas históricas técnicas no simulan una firma ni un cobro Core. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class LecturasFinancierasC5IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired AgenteService agentes;
    @Autowired SeguimientoComercialService seguimiento;
    @Autowired ContratoAlquilerRepository contratos;
    @Autowired PlatformTransactionManager gestor;
    FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void limpiar(){EvidenciaC4DePrueba.limpiar();}
    record Renta(FormalizacionesDePrueba.Caso caso,Expediente expediente,Hito firmado,long contrato){ }
    Renta renta(OrigenesDePrueba.Equipo equipo,String importe){
        var condicion=new EncargoService.Condiciones(new BigDecimal(importe),"PEN","MENSUAL","E","R",BigDecimal.ONE,"PEN","N",null,
                "CONTRATO_ALQUILER","PROPIETARIO_TEST","AGENCIA_TEST","CONTRATO_ALQUILER","Pacto explícito TEST agregado",List.of(),null,
                new EjecucionPacto(new CondicionDevengo(CONTRATO_ALQUILER),new CondicionExigibilidad(CONTRATO_ALQUILER,null,null,null,null)));
        var c=b.nuevoEnEquipo(equipo,"ALQUILER",condicion);var r=b.listo(c,CONTRATO_ALQUILER,hoy(),b.alquiler(c,new BigDecimal(importe),"PEN"),false);
        var h=b.confirmar(r);var contrato=b.jdbc.queryForObject("select id_contrato_alquiler from expediente_operacion where organizacion_id=? and id_expediente=?",Long.class,c.agente().idOrganizacion(),r.expediente().id());
        assertNotNull(contrato);return new Renta(c,r.expediente(),h,contrato);
    }
    Hito rectificar(Renta r,String importe,String moneda){
        var finalNuevo=b.alquiler(r.caso(),new BigDecimal(importe),moneda);var e=b.aprobar(r.caso(),r.expediente(),finalNuevo);
        var d=new DatosRectificacionHito(new ReferenciaHito(r.firmado().id(),r.firmado().revisionConfirmada()),r.firmado().version(),e.version(),hoy(),
                "Corrección acreditada TEST para lectura financiera",finalNuevo.documentos(),finalNuevo);
        var p=b.actuaciones.prepararRectificacionHito(r.caso().broker(),d,b.sesiones.de(r.caso().broker()),procedencia());
        return b.humanas.confirmar(p,r.caso().broker(),b.sesiones.de(r.caso().broker()),Hito.class).datos();
    }
    AgenteService.ComisionesAgente lectura(Renta r){return agentes.ficha(r.caso().agente().idRolOperativo(),r.caso().broker()).comisiones();}
    AgenteService.ImportePorMoneda moneda(List<AgenteService.ImportePorMoneda> valores,String moneda){
        return valores.stream().filter(v->Objects.equals(v.moneda(),moneda)).findFirst().orElseThrow(()->new AssertionError("Falta fila de moneda/cobertura "+moneda));
    }
    void monto(String esperado,BigDecimal real){assertNotNull(real);assertEquals(0,new BigDecimal(esperado).compareTo(real));}

    @Test void saldoSeCalculaPorComisionAntesDeSumarSinCompensarSobrepagadoConOtroPendiente(){
        var a=renta(null,"3000");var otra=renta(a.caso().comercial().equipo(),"2000");
        b.cobrar(a.caso(),b.prepararCobro(a.caso(),a.expediente(),new BigDecimal("2000"),hoy(),clave()));rectificar(a,"1500","PEN");
        var f=lectura(a);monto("3500",moneda(f.generada(),"PEN").monto());monto("2000",moneda(f.cobrada(),"PEN").monto());
        monto("2000",moneda(f.pendienteCobro(),"PEN").monto());assertEquals(2L,moneda(f.pendienteCobro(),"PEN").determinados());assertEquals(0L,moneda(f.pendienteCobro(),"PEN").pendientes());
        assertEquals(a.caso().agente(),otra.caso().agente());
        assertNull(b.jdbc.queryForObject("select id_solicitud from contrato_alquiler where id_contrato_alquiler=?",Long.class,a.contrato()));
        var filas=seguimiento.listar(new SeguimientoComercialService.Filtros("Cierre","","","","","",1,8),a.caso().broker()).items();
        assertEquals(2,filas.size());assertTrue(filas.stream().allMatch(v->"PEN".equals(v.moneda())));
        assertTrue(filas.stream().anyMatch(v->new BigDecimal(v.monto()).compareTo(new BigDecimal("1500"))==0));
        assertTrue(filas.stream().anyMatch(v->new BigDecimal(v.monto()).compareTo(new BigDecimal("2000"))==0));
    }
    @Test void ultimaEvaluacionJsonPrevaleceSobreCacheMutableSinAlterarSuHistoria(){
        var a=renta(null,"3000");rectificar(a,"1500","PEN");var f=b.comision(a.caso(),a.expediente());
        new TransactionTemplate(gestor).executeWithoutResult(s->{try{
            assertEquals(1,b.jdbc.update("update comision_liquidacion set monto_bruto=9999 where organizacion_id=? and id_comision_liquidacion=?",a.caso().agente().idOrganizacion(),f.id()));
            monto("1500",moneda(lectura(a).generada(),"PEN").monto());
        }finally{s.setRollbackOnly();}});
        monto("1500",b.comision(a.caso(),a.expediente()).calculo().cuantia().importe());
    }
    @Test void repartoUsaJsonDeSuEvaluacionBaseYMonedaOriginalTrasRectificar(){
        var a=renta(null,"3000");var f=b.comision(a.caso(),a.expediente());
        var p=b.actuaciones.prepararRepartoComision(a.caso().broker(),new ComisionService.DatosReparto(f.id(),f.version(),new BigDecimal("1800"),"Reparto TEST"),b.sesiones.de(a.caso().broker()),procedencia());
        b.humanas.confirmar(p,a.caso().broker(),b.sesiones.de(a.caso().broker()),ComisionService.ResultadoReparto.class);
        var antes=b.comision(a.caso(),a.expediente()).reparto();rectificar(a,"1500","USD");
        var despues=b.comision(a.caso(),a.expediente());assertEquals(antes,despues.reparto());assertEquals("PEN",despues.reparto().moneda());assertNotNull(despues.reparto().evaluacionBase());
        monto("1800",moneda(lectura(a).asignadaAgente(),"PEN").monto());assertNull(despues.calculo().cuantia().importe());
    }
    @Test void coberturaDesconocidaYMonedaNullNoSeConviertenEnCeroNiSeTomanDelCache(){
        var a=renta(null,"3000");var conocida=renta(a.caso().comercial().equipo(),"2000");rectificar(a,"1500","USD");
        var real=b.comision(a.caso(),a.expediente());assertNull(real.calculo().cuantia().importe());assertEquals("PEN",real.calculo().cuantia().moneda());
        assertTrue(real.calculo().faltantes().contains("MONEDA_BASE_FINAL_NO_COMPARABLE"));
        new TransactionTemplate(gestor).executeWithoutResult(s->{try{
            long legado=contratoHistorico(a,"A",hoy());comisionHistorica(a,legado,"9999","PEN");
            var f=lectura(a);var pen=moneda(f.generada(),"PEN");monto("2000",pen.monto());assertEquals(1L,pen.determinados());assertEquals(1L,pen.pendientes());
            var saldo=moneda(f.pendienteCobro(),"PEN");monto("2000",saldo.monto());assertEquals(1L,saldo.determinados());assertEquals(1L,saldo.pendientes());
            var desconocida=moneda(f.generada(),null);assertNull(desconocida.monto());assertEquals(0L,desconocida.determinados());assertEquals(1L,desconocida.pendientes());
            var saldoDesconocido=moneda(f.pendienteCobro(),null);assertNull(saldoDesconocido.monto());assertEquals(0L,saldoDesconocido.determinados());assertEquals(1L,saldoDesconocido.pendientes());
        }finally{s.setRollbackOnly();}});
        assertEquals(a.caso().agente(),conocida.caso().agente());
    }
    @Test void preparacionYAnuladoSinFechaNoIngresanPeroAnuladoConFechaConservaFondos(){
        var a=renta(null,"3000");
        var segunda=renta(a.caso().comercial().equipo(),"3000");
        var tercera=renta(a.caso().comercial().equipo(),"3000");
        new TransactionTemplate(gestor).executeWithoutResult(s->{try{
            long p=comisionHistorica(a,contratoHistorico(a,"P",null),"1000","PEN");movimientoHistorico(a,p,"C","100");
            long sinFecha=comisionHistorica(segunda,contratoHistorico(segunda,"A",null),"1000","PEN");movimientoHistorico(segunda,sinFecha,"C","200");
            long conFecha=comisionHistorica(tercera,contratoHistorico(tercera,"A",hoy()),"1000","PEN");movimientoHistorico(tercera,conFecha,"C","300");
            monto("300",moneda(lectura(a).cobrada(),"PEN").monto());
        }finally{s.setRollbackOnly();}});
    }
    @Test void raizConNetoNegativoNoSeOcultaPorOtraPositivaOQuedaBloqueadaPorSql(){
        var a=renta(null,"3000");
        var segunda=renta(a.caso().comercial().equipo(),"3000");
        new TransactionTemplate(gestor).executeWithoutResult(s->{try{
            long uno=comisionHistorica(a,contratoHistorico(a,"A",hoy()),"1000","PEN");movimientoHistorico(a,uno,"C","100");
            b.jdbc.execute((ConnectionCallback<Void>)conexion->{
            var punto=conexion.setSavepoint();
            try{movimientoHistorico(a,uno,"R","200");}
            catch(DataIntegrityViolationException bloqueada){
                conexion.rollback(punto);SQLException sql=sql(bloqueada);
                assertEquals("23514",sql.getSQLState(),"Sólo un CHECK/trigger de integridad acredita el bloqueo del dato adversarial.");
                EvidenciaC4DePrueba.estado("LEGADO_FINANCIERO_NEGATIVO",a.caso().broker(),"BLOQUEADO_SQL_23514");return null;
            }finally{conexion.releaseSavepoint(punto);}
            long dos=comisionHistorica(segunda,contratoHistorico(segunda,"A",hoy()),"1000","PEN");movimientoHistorico(segunda,dos,"C","500");
            var fila=contratos.movimientosDeAgente(a.caso().agente().idOrganizacion(),a.caso().agente().idRolOperativo()).stream().filter(v->"PEN".equals(v.getMoneda())).findFirst().orElseThrow();
            monto("400",fila.getMontoCobrado());assertEquals(Boolean.TRUE,fila.getAnomalia());
            assertThrows(IllegalStateException.class,()->lectura(a));
            return null;
            });
        }finally{s.setRollbackOnly();}});
    }

    /** Una muestra ctxNULL por raíz real TEST; no crea otra raíz ni varios sucesores del mismo contrato. */
    long contratoHistorico(Renta base,String estado,LocalDate fecha){
        return b.jdbc.queryForObject("""
            insert into contrato_alquiler(organizacion_id,id_oportunidad,id_solicitud,fecha_cierre,estado_contrato,
               fecha_inicio_contrato,fecha_fin_contrato,renta_contractual,moneda,id_contrato_anterior,fecha_efectiva_estado,
               id_rol_agente_cierre,id_rol_broker_cierre,id_captacion,id_propiedad,id_rol_cliente,incidencias)
            select organizacion_id,id_oportunidad,id_solicitud,?::date,?,fecha_inicio_contrato,fecha_fin_contrato,renta_contractual,
               moneda,id_contrato_alquiler,?::date,id_rol_agente_cierre,id_rol_broker_cierre,id_captacion,id_propiedad,id_rol_cliente,
               'HISTORIA TECNICA TEST ctxNULL, sin declaracion de nuevo hito'
              from contrato_alquiler where organizacion_id=? and id_contrato_alquiler=? returning id_contrato_alquiler
            """,Long.class,fecha,estado,hoy(),base.caso().agente().idOrganizacion(),base.contrato());
    }
    long comisionHistorica(Renta base,long contrato,String cache,String moneda){
        return b.jdbc.queryForObject("""
            insert into comision_liquidacion(organizacion_id,id_contrato_alquiler,monto_bruto,moneda,estado)
            values(?,?,?,?,null) returning id_comision_liquidacion
            """,Long.class,base.caso().agente().idOrganizacion(),contrato,new BigDecimal(cache),moneda);
    }
    void movimientoHistorico(Renta base,long comision,String tipo,String monto){
        b.jdbc.update("""
            insert into comision_movimiento(organizacion_id,id_comision_liquidacion,tipo,monto,moneda,fecha,forma_pago,observacion)
            values(?,?,?,?,'PEN',?,'TRANSFERENCIA','HISTORIA TECNICA TEST ctxNULL; no accion de cobro nueva')
            """,base.caso().agente().idOrganizacion(),comision,tipo,new BigDecimal(monto),hoy());
    }
    SQLException sql(Throwable error){for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof SQLException s)return s;throw new AssertionError("No se conserva SQLState de la guarda.",error);}
}
