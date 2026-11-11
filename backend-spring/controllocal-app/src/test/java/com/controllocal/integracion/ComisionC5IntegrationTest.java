package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.comercial.TipoHitoContractual;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaContextoEconomico;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaHito;
import com.controllocal.service.formalizacion.FormalizacionService.*;
import com.controllocal.service.matching.ContratoMatching.TratamientoMensual;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.domain.comercial.TipoHitoContractual.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;

/** Core/PG reales y puerta humana. No prueba autenticación HTTP ni autenticidad documental externa. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class ComisionC5IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    FormalizacionesDePrueba b;
    @BeforeEach void iniciar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);}
    @AfterEach void finalizar(){EvidenciaC4DePrueba.limpiar();}

    static EncargoService.Condiciones fija(TipoHitoContractual devengo,TipoHitoContractual exigibilidad,List<EncargoService.Tramo> tramos){
        return new EncargoService.Condiciones(new BigDecimal("100000"),"PEN",null,"F","N",new BigDecimal("15000"),"PEN","N",null,
                devengo.name(),"PROPIETARIO_TEST","AGENCIA_TEST",exigibilidad.name(),"Pacto explícito TEST",tramos,null,
                ejecucion(devengo,new CondicionExigibilidad(exigibilidad,null,null,null,null)));
    }
    static EjecucionPacto ejecucion(TipoHitoContractual d,CondicionExigibilidad e){return new EjecucionPacto(new CondicionDevengo(d),e);}
    void monto(String esperado,BigDecimal actual){assertNotNull(actual);assertEquals(0,new BigDecimal(esperado).compareTo(actual));}
    BigDecimal ingreso(ComisionService.FichaComision f,String moneda){return f.cobro().ingresosRecibidos().stream().filter(d->moneda.equals(d.moneda())).findFirst().orElseThrow(()->new AssertionError("Falta ingreso realmente recibido en "+moneda)).importe();}
    int movimientos(long org,long comision){return b.jdbc.queryForObject("select count(*) from comision_movimiento where organizacion_id=? and id_comision_liquidacion=?",Integer.class,org,comision);}
    FormalizacionesDePrueba.HitoRevisado cierreFijo(){
        var c=b.nuevo("VENTA",fija(ESCRITURA,ESCRITURA,List.of()));
        return b.listo(c,ESCRITURA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);
    }

    @Test @DisplayName("C5-01: pago único pactado disponible, sin hito ni dinero inventados")
    void pagoUnicoSinHitoNoDevengaNiCreaTramos(){
        var c=b.nuevo("VENTA",fija(ESCRITURA,ESCRITURA,List.of()));
        var f=b.comisiones.consultar(c.pacto(),null,c.broker());
        assertNull(f.id());assertNull(f.contexto());monto("15000",f.calculo().cuantia().importe());
        assertEquals("PENDIENTE",f.devengo().estado());assertNull(f.devengo().cuantiaAcreditada().importe());
        assertEquals("SIN_COBROS",f.cobro().estado());assertTrue(f.cobro().ingresosRecibidos().isEmpty());assertTrue(f.tramos().isEmpty());
        assertEquals(0,b.jdbc.queryForObject("select count(*) from expediente_operacion where organizacion_id=?",Integer.class,c.agente().idOrganizacion()));
    }
    @Test @DisplayName("C5-02: escritura confirmada por broker, base C5 sin oferta ficticia, sin cobro")
    void hitoRealDevengaSinFabricarCobro(){
        var r=cierreFijo();var h=b.confirmar(r);var f=b.comision(r.caso(),r.expediente());
        assertNotNull(h.revisionConfirmada());assertEquals("DEVENGADO",f.devengo().estado());monto("15000",f.devengo().cuantiaAcreditada().importe());
        assertEquals("SIN_COBROS",f.cobro().estado());assertEquals(0,movimientos(r.caso().agente().idOrganizacion(),f.id()));assertTrue(f.tramos().isEmpty());
        assertEquals("REVISION_C5",f.fuenteFinal().tipo());
        assertEquals(0,b.jdbc.queryForObject("select count(*) from oferta where organizacion_id=?",Integer.class,r.caso().agente().idOrganizacion()));
    }
    @Test @DisplayName("C7: resumen distingue devengo, ingreso real, corte y autorización")
    void resumenC7ConservaElCobroRealSinConvertirPendientesEnIngreso(){
        var r=cierreFijo();b.confirmar(r);var c=r.caso();var corte=java.time.Instant.now();
        var inicial=b.comisiones.resumenDePeriodo(hoy().withDayOfMonth(1),hoy(),corte,null,c.broker());
        assertTrue(inicial.cobradoEnPeriodo().isEmpty());monto("15000",inicial.devengadoActual().getFirst().importe());
        b.cobrar(c,b.prepararCobro(c,r.expediente(),new BigDecimal("4000"),hoy(),clave()));
        var actual=b.comisiones.resumenDePeriodo(hoy().withDayOfMonth(1),hoy(),java.time.Instant.now(),null,c.broker());
        assertEquals(1,actual.cobradoEnPeriodo().size());var ingreso=actual.cobradoEnPeriodo().getFirst();assertEquals("VENTA",ingreso.operacion());assertEquals("PEN",ingreso.moneda());monto("4000",ingreso.importe());monto("15000",actual.calculadoActual().getFirst().importe());
        assertTrue(b.comisiones.resumenDePeriodo(hoy().withDayOfMonth(1),hoy(),corte,null,c.broker()).cobradoEnPeriodo().isEmpty());
        var otra=b.nuevo("VENTA",fija(ESCRITURA,ESCRITURA,List.of()));assertEquals(0,b.comisiones.resumenDePeriodo(hoy().withDayOfMonth(1),hoy(),java.time.Instant.now(),null,otra.broker()).casos());
        var gobierno=new Actor(c.broker().idOrganizacion(),c.broker().idPersona(),c.broker().idRolOperativo(),"TENANT_ADMIN");assertThrows(AccesoNoAutorizadoException.class,()->b.comisiones.resumenDePeriodo(hoy().withDayOfMonth(1),hoy(),java.time.Instant.now(),null,gobierno));
        var ar=contexto.getBean(com.controllocal.persistence.repositorio.ActividadInformeC7Repository.class);var ahora=java.time.Instant.now();
        var trayectoria=ar.trayectorias(c.agente().idOrganizacion(),List.of(c.agente().idRolOperativo()),false,ahora);assertEquals(1,trayectoria.size());assertEquals(c.agente().idRolOperativo(),trayectoria.getFirst().responsableApertura());
        var cohortes=com.controllocal.service.actividad.CohortesDeInforme.componer(trayectoria,contexto.getBean(com.controllocal.service.actividad.DesenlacesDeOportunidad.class).leer(c.broker(),ahora),new com.controllocal.service.actividad.IndicadoresIndividuales.Ventana(hoy().withDayOfMonth(1),hoy(),ahora));
        assertEquals(1,cohortes.getFirst().cierresContractuales());assertEquals(0,cohortes.getFirst().conVisita());assertEquals(1,cohortes.getFirst().casos());assertTrue(ar.trayectorias(otra.agente().idOrganizacion(),List.of(otra.agente().idRolOperativo()),false,ahora).stream().allMatch(t->t.encargo()==otra.encargo()));
    }
    @Test @DisplayName("C7: la oportunidad ajena no entra por ser responsable de su Encargo")
    void metadatosC7AplicanElAmbitoCanonicoAntesDeContar(){
        var c=b.negociacion.mesa("VENTA",new BigDecimal("100000"),"PEN",false);var ar=contexto.getBean(com.controllocal.persistence.repositorio.ActividadInformeC7Repository.class);
        // Estado sintético de autorización divergente. No prueba una reasignación comercial ni altera el historial.
        b.jdbc.update("update oportunidad_comercial set id_rol_agente=?,version=version+1 where organizacion_id=? and id_oportunidad=?",c.equipo().colega().idRolOperativo(),c.agente().idOrganizacion(),c.oportunidad());
        assertThrows(AccesoNoAutorizadoException.class,()->b.oportunidades.obtener(c.oportunidad(),c.agente()));
        assertNotNull(b.oportunidades.obtener(c.oportunidad(),c.equipo().broker()));var roles=List.of(c.agente().idRolOperativo());var ahora=java.time.Instant.now();
        assertTrue(ar.trayectorias(c.agente().idOrganizacion(),roles,false,ahora).isEmpty());
        assertTrue(ar.fuentesPropias(c.agente().idOrganizacion(),roles,false).stream().noneMatch(f->"OPORTUNIDAD".equals(f.tipo())));
        assertEquals(0,ar.casos(c.agente().idOrganizacion(),roles,false,ahora).stream().filter(x->x.tipo().equals("OPORTUNIDAD")&&java.util.Set.of("A","S").contains(x.situacion())).count());
        var historica=ar.trayectorias(c.agente().idOrganizacion(),roles,true,ahora).getFirst();assertEquals(c.agente().idRolOperativo(),historica.responsableApertura());assertEquals(c.equipo().colega().idRolOperativo(),historica.responsableActual());
        var informe=contexto.getBean(com.controllocal.service.actividad.InformesC7.class).preparar(new com.controllocal.service.actividad.InformesC7.Consulta(com.controllocal.service.planes.DerechosDeInformesC7.Composicion.OPERACION_ACTUAL,null,null,null,null),c.agente());
        assertEquals(0,((com.controllocal.service.actividad.InformesC7.OperacionActual)informe.bloques().get("operacionActual")).oportunidadesAbiertas());
    }
    @Test @DisplayName("C5-03: dos depósitos para un único pago conservan una comisión y cero tramos sintéticos")
    void dosDepositosSonDosMovimientosNoDosTramos(){
        var r=cierreFijo();b.confirmar(r);var inicial=b.comision(r.caso(),r.expediente());
        var propuesta=b.prepararCobro(r.caso(),r.expediente(),new BigDecimal("4000"),hoy(),clave());
        assertEquals(0,movimientos(r.caso().agente().idOrganizacion(),inicial.id()));assertTrue(b.comision(r.caso(),r.expediente()).cobro().ingresosRecibidos().isEmpty());
        var uno=b.cobrar(r.caso(),propuesta);
        var dos=b.cobrar(r.caso(),b.prepararCobro(r.caso(),r.expediente(),new BigDecimal("11000"),hoy(),clave()));
        assertNotEquals(uno.datos().movimiento(),dos.datos().movimiento());
        var finalizada=b.comision(r.caso(),r.expediente());assertEquals(inicial.id(),finalizada.id());assertTrue(finalizada.tramos().isEmpty());
        monto("15000",ingreso(finalizada,"PEN"));monto("0",finalizada.cobro().saldoInformativoSobreCalculo().importe());
        assertEquals(2,movimientos(r.caso().agente().idOrganizacion(),finalizada.id()));assertNull(uno.datos().tramo());assertNull(dos.datos().tramo());
    }
    @Test @DisplayName("C5-04: dos pagos pactados no adelantan el devengo único de escritura")
    void calendarioFraccionadoNoEsDevengoFraccionado(){
        var tramos=List.of(new EncargoService.Tramo("MINUTA",new BigDecimal("5000"),null,"PEN","MINUTA",ejecucion(ESCRITURA,new CondicionExigibilidad(MINUTA,null,null,null,null))),
                new EncargoService.Tramo("ESCRITURA",new BigDecimal("10000"),null,"PEN","ESCRITURA",ejecucion(ESCRITURA,new CondicionExigibilidad(ESCRITURA,null,null,null,null))));
        var c=b.nuevo("VENTA",fija(ESCRITURA,ESCRITURA,tramos));var acuerdo=b.venta(c,new BigDecimal("100000"),"PEN",null);
        var r=b.listo(c,MINUTA,hoy(),acuerdo,false);b.confirmar(r);var antes=b.comision(c,r.expediente());
        assertEquals("PENDIENTE",antes.devengo().estado());assertNull(antes.devengo().cuantiaAcreditada().importe());assertEquals(2,antes.tramos().size());
        assertTrue(antes.tramos().stream().allMatch(t->"PENDIENTE".equals(t.devengo().estado())));assertEquals(0,movimientos(c.agente().idOrganizacion(),antes.id()));
        var escritura=b.informar(c,r.expediente(),ESCRITURA,hoy());b.confirmar(new FormalizacionesDePrueba.HitoRevisado(c,r.expediente(),escritura,acuerdo));
        var despues=b.comision(c,r.expediente());assertEquals(antes.id(),despues.id());assertEquals("DEVENGADO",despues.devengo().estado());monto("15000",despues.devengo().cuantiaAcreditada().importe());
    }
    @Test @DisplayName("C5-05: cobro posterior al devengo y anterior a exigibilidad es ingreso recibido")
    void devengoYExigibilidadSonDistintos(){
        var base=fija(MINUTA,ESCRITURA,List.of());
        var condiciones=new EncargoService.Condiciones(base.precioPedido(),base.monedaPrecio(),base.periodoPrecio(),base.tipoComision(),base.baseCalculo(),base.valorComision(),base.monedaComision(),
                base.tratamientoIgv(),base.motivoSinComision(),base.hitoDevengo(),base.parteObligada(),base.beneficiario(),"Fecha expresa TEST",base.procedencia(),base.tramos(),null,
                ejecucion(MINUTA,new CondicionExigibilidad(null,hoy().plusDays(5),null,null,null)));
        var c=b.nuevo("VENTA",condiciones);var r=b.listo(c,MINUTA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);b.confirmar(r);
        var f=b.comision(c,r.expediente());assertEquals("DEVENGADO",f.devengo().estado());assertEquals(Boolean.FALSE,f.exigibilidad().exigible());
        b.cobrar(c,b.prepararCobro(c,r.expediente(),new BigDecimal("3000"),hoy(),clave()));var despues=b.comision(c,r.expediente());
        monto("3000",ingreso(despues,"PEN"));monto("15000",despues.calculo().cuantia().importe());assertEquals(Boolean.FALSE,despues.exigibilidad().exigible());
    }
    @Test @DisplayName("C5-06: sin devengo no existe anticipo ordinario de comisión")
    void predevengoNoSeGuardaComoCobro(){
        var c=b.nuevo("VENTA",fija(ESCRITURA,ESCRITURA,List.of()));var r=b.listo(c,MINUTA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);b.confirmar(r);
        var f=b.comision(c,r.expediente());assertNotNull(f.id());assertEquals("PENDIENTE",f.devengo().estado());
        var fallo=assertThrows(ReglaNegocioException.class,()->b.prepararCobro(c,r.expediente(),BigDecimal.ONE,hoy(),clave()));
        assertTrue(fallo.getMessage().toLowerCase(Locale.ROOT).contains("deveng"));assertEquals(0,movimientos(c.agente().idOrganizacion(),f.id()));
    }
    @Test @DisplayName("C5-07: base final desconocida no es cero ni precio de anuncio")
    void porcentajeSinBaseConservaPendiente(){
        var f=fija(MINUTA,MINUTA,List.of());var condiciones=new EncargoService.Condiciones(f.precioPedido(),"PEN",null,"P","V",new BigDecimal("0.03"),"PEN","N",null,
                "MINUTA",f.parteObligada(),f.beneficiario(),"MINUTA",f.procedencia(),List.of(),null,ejecucion(MINUTA,new CondicionExigibilidad(MINUTA,null,null,null,null)));
        var c=b.nuevo("VENTA",condiciones);var r=b.listo(c,MINUTA,hoy(),null,false);b.confirmar(r);var calculo=b.comision(c,r.expediente());
        assertNull(calculo.calculo().cuantia().importe());assertFalse(calculo.calculo().faltantes().isEmpty());
        // Especificación8.2: el hito acredita el derecho; su cuantificación sigue pendiente.
        assertEquals("DEVENGADO",calculo.devengo().estado());assertFalse(calculo.devengo().hechos().isEmpty());
        assertNull(calculo.devengo().cuantiaAcreditada().importe());
        assertThrows(ReglaNegocioException.class,()->b.prepararCobro(c,r.expediente(),BigDecimal.ONE,hoy(),clave()));
        assertEquals(0,movimientos(c.agente().idOrganizacion(),calculo.id()));
    }
    @Test @DisplayName("C5-08: acuse perdido tras commit recupera mismo cobro y otro tenant no lo usa")
    void replayRealPostCommitYTenantAislado(){
        var r=cierreFijo();b.confirmar(r);var p=b.prepararCobro(r.caso(),r.expediente(),new BigDecimal("1000"),hoy(),clave());
        var confirmado=b.cobrar(r.caso(),p); // El cliente puede perder este retorno DESPUÉS del commit real Core.
        var repetido=b.actuaciones.confirmar(r.caso().broker(),p.referencia(),b.sesiones.de(r.caso().broker()),confirmado.confirmacion(),procedencia());
        assertEquals("EXITO",repetido.estado());assertEquals(confirmado.datos().movimiento(),((Number)repetido.resultado().get("movimiento")).longValue());
        var f=b.comision(r.caso(),r.expediente());assertEquals(1,movimientos(r.caso().agente().idOrganizacion(),f.id()));monto("1000",ingreso(f,"PEN"));
        var otro=b.nuevo("VENTA",fija(ESCRITURA,ESCRITURA,List.of()));
        RuntimeException denegada=assertThrows(RuntimeException.class,()->b.comisiones.consultar(r.caso().pacto(),new ReferenciaContextoEconomico(r.expediente().id()),otro.broker()));
        assertTrue(denegada instanceof AccesoNoAutorizadoException||denegada instanceof NoEncontradoException,()->"No es una negativa de autoridad: "+denegada.getClass());
        assertEquals(1,movimientos(r.caso().agente().idOrganizacion(),f.id()));
    }
    @Test @DisplayName("C5-09: comisión firmada sólo cambia por los dos actos competentes de adenda")
    void comisionFirmadaNoSeCambiaPorEdicionOrdinaria(){
        var c=b.nuevo("VENTA",fija(ESCRITURA,ESCRITURA,List.of()));var actual=b.encargos.consultar(c.encargo(),c.agente());var f=actual.condiciones();
        var cambio=new EncargoService.Condiciones(f.precioPedido(),f.monedaPrecio(),f.periodoPrecio(),f.tipoComision(),f.baseCalculo(),new BigDecimal("1000"),f.monedaComision(),f.tratamientoIgv(),
                f.motivoSinComision(),f.hitoDevengo(),f.parteObligada(),f.beneficiario(),f.exigibilidad(),f.procedencia(),f.tramos(),f.composicionMensual(),f.ejecucion());
        assertThrows(ReglaNegocioException.class,()->b.encargos.editar(c.encargo(),new EncargoService.Edicion(actual.version(),actual.desde(),actual.hasta(),false,null,cambio,"Cambio ordinario prohibido TEST",null,null),
                c.agente(),b.sesiones.de(c.agente()),clave(),procedencia()));
        monto("15000",b.pactos.consultarPactoFirmado(c.pacto(),c.broker()).regla().valorComision());
        // Sólo lectura del montaje propio TEST: recupera las partes exactas del PDF Core.
        b.encargos.consultarDocumento(actual.documentoActual(),c.agente());
        var doc=contexto.getBean(com.controllocal.persistence.repositorio.DocumentoEncargoRepository.class)
                .findByOrganizacionIdAndId(c.agente().idOrganizacion(),actual.documentoActual()).orElseThrow();
        var json=contexto.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        var contenido=contexto.getBean(com.controllocal.service.soporte.Documentos.class).comoMapa(doc.getContenido());
        var partes=json.convertValue(contenido.get("partesVersionadas"),new com.fasterxml.jackson.core.type.TypeReference<List<EncargoService.Parte>>(){});
        assertFalse(partes.isEmpty());
        var preparacion=new EncargoService.Preparacion(c.encargo(),actual.version(),actual.idPropiedad(),null,partes,
                com.controllocal.domain.inmueble.OperacionInmobiliaria.VENTA,null,cambio,actual.desde(),actual.hasta(),null,null,null);
        var candidata=b.encargos.proponerAdenda(c.encargo(),new EncargoService.DatosAdenda(c.pacto(),actual.version(),preparacion,
                "Comisión corregida por acuerdo TEST","Comisión del Encargo",hoy(),null,null),c.agente(),b.sesiones.de(c.agente()),clave(),procedencia());
        var d=new EncargoService.DecisionBorradorAdenda("APROBAR",candidata.ultimaRevision(),actual.version(),candidata.huellaPropuesta(),"Borrador revisado TEST");
        assertThrows(AccesoNoAutorizadoException.class,()->b.actuaciones.prepararDecisionBorradorAdenda(c.agente(),candidata.referencia(),d,b.sesiones.de(c.agente()),procedencia()));
        var p=b.actuaciones.prepararDecisionBorradorAdenda(c.broker(),candidata.referencia(),d,b.sesiones.de(c.broker()),procedencia());
        var autorizada=b.humanas.confirmar(p,c.broker(),b.sesiones.de(c.broker()),EncargoService.Adenda.class).datos();
        assertNotNull(autorizada.documento());assertNull(autorizada.revisionActivacion());
        assertEquals(actual.documentoActual(),b.encargos.consultar(c.encargo(),c.agente()).documentoActual());
        monto("15000",b.encargos.consultar(c.encargo(),c.agente()).condiciones().valorComision());
        var documento=b.encargos.consultarDocumento(autorizada.documento(),c.agente());
        var productor=new FirmasManuscritasDePrueba(b.encargos,contexto.getBean(com.controllocal.service.archivo.AccesoArchivoService.class),
                contexto.getBean(com.controllocal.web.almacen.AlmacenDocumentos.class));
        var firmas=productor.aportar(c.encargo(),documento,partes.stream().map(t->new EncargoService.Firmante(t.titularidad(),t.persona())).toList(),
                hoy(),c.agente(),b.sesiones.de(c.agente()));
        var pet=b.encargos.presentar(c.encargo(),new EncargoService.Presentacion(documento.id(),b.encargos.consultar(c.encargo(),c.agente()).version(),
                partes,firmas,null,candidata.referencia()),c.agente(),b.sesiones.de(c.agente()),clave(),procedencia());
        var firma=Objects.requireNonNull(pet.firma());
        var decision=new EncargoService.Decision("APROBAR",pet.version(),b.encargos.consultar(c.encargo(),c.broker()).version(),"Instrumento revisado TEST",
                new EncargoService.RevisionManuscrita(firma.revisionPresentacion(),firma.huellaManifestacion(),true));
        var segunda=b.actuaciones.prepararDecisionEncargo(c.broker(),pet.id(),decision,b.sesiones.de(c.broker()),procedencia());
        b.humanas.confirmar(segunda,c.broker(),b.sesiones.de(c.broker()),EncargoService.Peticion.class);
        var activa=b.encargos.consultarAdenda(candidata.referencia(),c.broker());assertNotNull(activa.revisionActivacion());
        monto("1000",b.pactos.consultarPactoFirmado(new ReferenciaPacto(c.encargo(),activa.revisionActivacion()),c.broker()).regla().valorComision());
        monto("15000",b.pactos.consultarPactoFirmado(c.pacto(),c.broker()).regla().valorComision());
    }
    @Test void porcentajeFraccionarioRedondeaHalfUpDosSinAlterarBase(){
        var base=fija(ESCRITURA,ESCRITURA,List.of());var condiciones=new EncargoService.Condiciones(base.precioPedido(),"PEN",null,"P","V",new BigDecimal("0.03"),"PEN","N",null,
                "ESCRITURA",base.parteObligada(),base.beneficiario(),"ESCRITURA",base.procedencia(),List.of(),null,base.ejecucion());
        var c=b.nuevo("VENTA",condiciones);var r=b.listo(c,ESCRITURA,hoy(),b.venta(c,new BigDecimal("94000.50"),"PEN",null),false);b.confirmar(r);
        monto("2820.02",b.comision(c,r.expediente()).calculo().cuantia().importe());
    }
    @Test void equivalenteUsaRentaContractualYNoTotalMensualConMantenimiento(){
        var composicion=new EncargoService.ComposicionMensual(true,"Fuente mensual TEST",List.of(new EncargoService.ImporteMensual("MANTENIMIENTO_TEST","MANTENIMIENTO",new BigDecimal("200"),null,false,"PEN","MENSUAL",TratamientoMensual.EXIGIBLE,null,"Documento TEST",null,null)));
        var condiciones=new EncargoService.Condiciones(new BigDecimal("2500"),"PEN","MENSUAL","E","R",new BigDecimal("1.5"),"PEN","N",null,
                "CONTRATO_ALQUILER","PROPIETARIO_TEST","AGENCIA_TEST","CONTRATO_ALQUILER","Pacto alquiler TEST",List.of(),composicion,
                ejecucion(CONTRATO_ALQUILER,new CondicionExigibilidad(CONTRATO_ALQUILER,null,null,null,null)));
        var c=b.nuevo("ALQUILER",condiciones);var r=b.listo(c,CONTRATO_ALQUILER,hoy(),b.alquiler(c,new BigDecimal("2500"),"PEN"),false);b.confirmar(r);
        monto("3750",b.comision(c,r.expediente()).calculo().cuantia().importe());assertNotNull(b.encargos.consultar(c.encargo(),c.broker()).condiciones().composicionMensual());
    }
    @Test void gobiernoNoConsultaDineroYAgenteNoRecibeNetosInternos(){
        var r=cierreFijo();b.confirmar(r);var ctx=new ReferenciaContextoEconomico(r.expediente().id());
        assertThrows(AccesoNoAutorizadoException.class,()->b.comisiones.consultar(r.caso().pacto(),ctx,r.caso().comercial().equipo().admin()));
        var f=b.comisiones.consultar(r.caso().pacto(),ctx,r.caso().agente());
        assertNull(f.reparto().agente());assertNull(f.reparto().empresa());assertNull(f.reparto().pagadoAgente());assertNull(f.reparto().saldoPagoAgente());
        assertNull(f.reparto().evaluacionBase());assertNull(f.reparto().moneda());
    }
    @Test @DisplayName("CG-02/07: reprogramación con motivo e historia, sin cambiar exigibilidad ni repetir el efecto")
    void reprogramacionConservaPactoExigibilidadYReciboHistorico(){
        var r=cierreFijo();b.confirmar(r);var antes=b.comision(r.caso(),r.expediente());
        assertThrows(ReglaNegocioException.class,()->b.prepararReprogramacion(r.caso(),r.expediente(),hoy().plusDays(3)," "));
        var p1=b.prepararReprogramacion(r.caso(),r.expediente(),hoy().plusDays(3),"Esperar respuesta TEST");var uno=b.gestionar(r.caso(),p1);
        assertEquals(hoy().plusDays(3),uno.datos().gestion().proximaRevision());assertFalse(uno.datos().gestion().concluida());
        b.gestionar(r.caso(),b.prepararReprogramacion(r.caso(),r.expediente(),hoy().plusDays(7),"Nueva fecha acordada TEST"));
        var actual=b.comision(r.caso(),r.expediente());assertEquals(hoy().plusDays(7),actual.gestion().proximaRevision());
        assertEquals(antes.exigibilidad(),actual.exigibilidad());assertEquals(antes.calendario(),actual.calendario());assertEquals(antes.pacto(),actual.pacto());
        var replay=b.actuaciones.confirmar(r.caso().broker(),p1.referencia(),b.sesiones.de(r.caso().broker()),uno.confirmacion(),procedencia());
        assertEquals("EXITO",replay.estado());var mapper=contexto.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        var original=mapper.convertValue(replay.resultado(),ComisionService.ResultadoGestion.class);
        assertEquals(uno.datos(),original);assertEquals(hoy().plusDays(7),b.comision(r.caso(),r.expediente()).gestion().proximaRevision());
        assertEquals(0,movimientos(r.caso().agente().idOrganizacion(),actual.id()));
    }
    @Test @DisplayName("CG-03/04/09: concluir con parcial no condona; otro cobro no reabre automáticamente")
    void concluirConRecibidoConservaSaldoInformativoYPuedeRecibirOtroCobro(){
        var r=cierreFijo();b.confirmar(r);b.cobrar(r.caso(),b.prepararCobro(r.caso(),r.expediente(),new BigDecimal("2000"),hoy(),clave()));
        assertThrows(ReglaNegocioException.class,()->b.prepararConclusion(r.caso(),r.expediente()," "));
        var cerrada=b.gestionar(r.caso(),b.prepararConclusion(r.caso(),r.expediente(),"Dar por terminada esta gestión TEST"));
        assertTrue(cerrada.datos().gestion().concluida());assertEquals("Dar por terminada esta gestión TEST",cerrada.datos().gestion().motivo());
        var f=b.comision(r.caso(),r.expediente());monto("15000",f.calculo().cuantia().importe());monto("2000",ingreso(f,"PEN"));
        monto("13000",f.cobro().saldoInformativoSobreCalculo().importe());monto("15000",b.pactos.consultarPactoFirmado(r.caso().pacto(),r.caso().broker()).regla().valorComision());
        b.cobrar(r.caso(),b.prepararCobro(r.caso(),r.expediente(),new BigDecimal("1000"),hoy(),clave()));
        var despues=b.comision(r.caso(),r.expediente());assertTrue(despues.gestion().concluida());monto("3000",ingreso(despues,"PEN"));
        monto("12000",despues.cobro().saldoInformativoSobreCalculo().importe());assertEquals(2,movimientos(r.caso().agente().idOrganizacion(),despues.id()));
    }
    @Test @DisplayName("CG-05: rectificación bajo lo recibido conserva hechos y reparto históricos, sin reversos")
    void rectificarDebajoDeRecibidoNoBorraFondosNiFabricaDevolucion(){rectificarConFondos("50000","1500","0");}
    @Test @DisplayName("CG-05: rectificación al alza conserva ingresos reales y no los completa con pendientes")
    void rectificarPorEncimaDeRecibidoNoFabricaIngreso(){rectificarConFondos("150000","4500","2500");}
    void rectificarConFondos(String precioNuevo,String comisionNueva,String saldoInformativo){
        var base=fija(ESCRITURA,ESCRITURA,List.of());var condiciones=new EncargoService.Condiciones(base.precioPedido(),"PEN",null,"P","V",new BigDecimal("0.03"),"PEN","N",null,
                "ESCRITURA",base.parteObligada(),base.beneficiario(),"ESCRITURA",base.procedencia(),List.of(),null,base.ejecucion());
        var c=b.nuevo("VENTA",condiciones);var r=b.listo(c,ESCRITURA,hoy(),b.venta(c,new BigDecimal("100000"),"PEN",null),false);var firmado=b.confirmar(r);
        var f=b.comision(c,r.expediente());var reparto=b.actuaciones.prepararRepartoComision(c.broker(),new ComisionService.DatosReparto(f.id(),f.version(),new BigDecimal("1800"),"Reparto explícito TEST"),b.sesiones.de(c.broker()),procedencia());
        b.humanas.confirmar(reparto,c.broker(),b.sesiones.de(c.broker()),ComisionService.ResultadoReparto.class);
        var cobro=b.cobrar(c,b.prepararCobro(c,r.expediente(),new BigDecimal("2000"),hoy(),clave()));var anterior=b.comision(c,r.expediente());
        monto("1800",anterior.reparto().agente());monto("1200",anterior.reparto().empresa());assertNotNull(anterior.reparto().evaluacionBase());assertEquals("PEN",anterior.reparto().moneda());
        var nuevo=b.venta(c,new BigDecimal(precioNuevo),"PEN",null);var revisado=b.aprobar(c,r.expediente(),nuevo);
        var actual=b.formalizacion.consultar(revisado.id(),c.broker());
        var datos=new DatosRectificacionHito(new ReferenciaHito(firmado.id(),firmado.revisionConfirmada()),firmado.version(),actual.version(),hoy(),
                "Corrección documentada del precio TEST",nuevo.documentos(),nuevo);
        var propuesta=b.actuaciones.prepararRectificacionHito(c.broker(),datos,b.sesiones.de(c.broker()),procedencia());
        var rectificado=b.humanas.confirmar(propuesta,c.broker(),b.sesiones.de(c.broker()),Hito.class).datos();
        assertEquals(firmado.id(),rectificado.id());assertNotEquals(firmado.revisionConfirmada(),rectificado.revisionConfirmada());
        var despues=b.comision(c,r.expediente());assertEquals(anterior.id(),despues.id());monto(comisionNueva,despues.calculo().cuantia().importe());monto("2000",ingreso(despues,"PEN"));
        monto(saldoInformativo,despues.cobro().saldoInformativoSobreCalculo().importe());
        assertEquals(anterior.reparto(),despues.reparto(),"La procedencia del reparto anterior conserva su evaluación y moneda.");
        assertEquals(1,movimientos(c.agente().idOrganizacion(),despues.id()));
        assertEquals(cobro.datos().movimiento(),b.jdbc.queryForObject("select id_comision_movimiento from comision_movimiento where organizacion_id=? and id_comision_liquidacion=?",Long.class,c.agente().idOrganizacion(),despues.id()));
        assertEquals("C",b.jdbc.queryForObject("select tipo from comision_movimiento where organizacion_id=? and id_comision_liquidacion=?",String.class,c.agente().idOrganizacion(),despues.id()));
        assertEquals(2,b.jdbc.queryForObject("select count(*) from revision_hito_contractual where organizacion_id=? and id_hito=? and tipo_registro in ('CONFIRMACION','RECTIFICACION')",Integer.class,c.agente().idOrganizacion(),firmado.id()));
        assertEquals(1,b.jdbc.queryForObject("select count(*) from contrato_compraventa where organizacion_id=? and id_captacion=?",Integer.class,c.agente().idOrganizacion(),c.encargo()));
    }
    @Test @DisplayName("CG-06: el binding Core rechaza tipo P/R anterior, no lo convierte en cobro")
    void tipoLegacyNoDesapareceEnBindingCore(){
        var mapper=contexto.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        for(String tipo:List.of("P","R"))assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                ()->mapper.readValue("{\"comision\":1,\"versionEsperada\":1,\"tipo\":\""+tipo+"\",\"monto\":1,\"moneda\":\"PEN\"}",ComisionService.DatosCobro.class));
        // Prueba de binding: HTTP y puerto interno tienen sus pruebas específicas del owner Root.
    }
    @Test @DisplayName("CG-08: un colega sin alcance no consulta o modifica la gestión asignada")
    void seguimientoAjenoNoObtieneAutoridadPorPertenecerAlTenant(){
        var r=cierreFijo();b.confirmar(r);var f=b.comision(r.caso(),r.expediente());var ajeno=r.caso().comercial().equipo().colega();
        assertThrows(AccesoNoAutorizadoException.class,()->b.actuaciones.prepararConclusionCobro(ajeno,
                new ComisionService.DatosConclusion(f.id(),f.version(),"Intento ajeno TEST"),b.sesiones.de(ajeno),procedencia()));
        assertFalse(b.comision(r.caso(),r.expediente()).gestion().concluida());
    }
}
