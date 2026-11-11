package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.integracion.soporte.OrigenesDePrueba.Equipo;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.*;
import com.controllocal.service.UnidadRelacionadaService.DatosOrigen;
import com.controllocal.service.UnidadRelacionadaService.ResultadoOrigen;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.service.soporte.NotificadorIdentidad;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/** RF014/CAT-E07: Core real y contrapruebas SQL reversibles, no acreditación HTTP o de login por fixture. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="spring.datasource.hikari.maximum-pool-size=10")
class OrigenFisicoIntegrationTest {
    private static final Procedencia PANTALLA=Procedencia.deLaPantalla();
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    @DynamicPropertySource static void datos(DynamicPropertyRegistry propiedades) { BaseDeDatosDePruebas.registrar(propiedades); }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired UnidadRelacionadaService origen;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired ProspeccionService prospecciones;
    @Autowired CuentaService cuentasPersonales;
    @MockitoBean NotificadorIdentidad canalIdentidad;
    @MockitoSpyBean EventoDominioRepository eventos;
    private OrigenesDePrueba fixtures;
    private Equipo equipo;
    private Actor agente;
    private ContextoSesion sesion;
    private long matriz,unidad;

    @BeforeEach void fuentePropia() {
        fixtures=new OrigenesDePrueba(jdbc,gestor,captura,contextos);
        equipo=fixtures.empresa();agente=equipo.agente();sesion=fixtures.sesion(agente);
        matriz=fixtures.propiedad(agente,"Matriz");unidad=fixtures.propiedad(agente,"Unidad");
    }
    @AfterEach void restaurarSabotaje(){reset(eventos);}

    @Test void independienteAutenticadoRegistraOrigenEnSuEspacioSinBrokerNiGobiernoFicticios() {
        String marca=java.util.UUID.randomUUID().toString().replace("-","");
        String correo="independiente-"+marca+"@example.invalid";
        var entrega=new java.util.concurrent.atomic.AtomicReference<NotificadorIdentidad.TokenEmitido>();
        when(canalIdentidad.entregaAlTitular()).thenReturn(true);
        doAnswer(i->{entrega.set(i.getArgument(2));return null;}).when(canalIdentidad)
                .enviar(eq(NotificadorIdentidad.Proposito.ALTA_CORREO),argThat(d->correo.equals(d.correoPropio())),any());
        cuentasPersonales.solicitarAlta(correo);
        assertNotNull(entrega.get(),"El transporte TEST debe recibir la prueba de correo sin envío externo.");
        cuentasPersonales.confirmarAlta(entrega.get().token(),"independiente-"+marca,"Profesional individual TEST RF014",
                "Frase propia de prueba RF014 individual".toCharArray());
        long cuenta=jdbc.queryForObject("select id_cuenta_acceso from cuenta_acceso where correo_propio=?",Long.class,correo);
        // Sólo el espacio/pertenencia son hechos técnicos del montaje, no un onboarding de producto.
        Actor independiente=new TransactionTemplate(gestor).execute(tx->{
            long espacio=jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,'INDIVIDUAL','A') returning id_organizacion",
                    Long.class,"RF14-I-"+marca.substring(0,15),"Espacio individual TEST RF014");
            long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Profesional individual TEST RF014') returning id_persona",Long.class,espacio);
            long rol=jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'AGENTE',?) returning id_persona_rol",Long.class,espacio,persona,OrigenesDePrueba.hoy());
            jdbc.update("insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) values(?,?,?,?, 'D')",espacio,rol,"RF14-I-"+rol,OrigenesDePrueba.hoy());
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'AGENTE',false,'A')",espacio,cuenta,persona);
            return new Actor(espacio,persona,rol,Actor.AGENTE);
        });
        var identidad=contextos.iniciarAcceso(correo,"Frase propia de prueba RF014 individual".toCharArray(),false).sesion();
        assertNotNull(identidad);assertNull(identidad.activo());assertEquals(cuenta,identidad.identidad().idCuenta());
        var opciones=contextos.contextos(identidad.referencia(),identidad.revision());assertEquals(1,opciones.size());
        var eleccion=opciones.stream().filter(c->c.idOrganizacion()==independiente.idOrganizacion()
                &&c.idPersona()==independiente.idPersona()&&c.idRolOperativo()==independiente.idRolOperativo()
                &&Actor.AGENTE.equals(c.rol())).findFirst().orElseThrow();
        assertEquals("INDIVIDUAL",eleccion.modalidad());
        var seleccionada=contextos.seleccionar(identidad.referencia(),identidad.revision(),eleccion.referencia());
        assertFalse(seleccionada.restricciones().debeCambiarContrasena());assertFalse(seleccionada.restricciones().debeEnrolarMfa());
        var sesionIndividual=ContextoSesion.de(seleccionada);
        long m=fixtures.propiedad(independiente,"Matriz individual"),u=fixtures.propiedad(independiente,"Unidad individual");
        var datos=fixtures.datos(m,u,independiente,origen);String clave=clave();
        var r=origen.registrarOrigen(datos,independiente,sesionIndividual,clave,PANTALLA);
        assertEquals(datos.versionMatrizEsperada()+1,r.versionMatriz());assertEquals(datos.versionUnidadEsperada()+1,r.versionUnidad());
        assertEquals(EstadoDato.FALTANTE,r.estadoSituacion());
        assertEquals(1,origen.consultarOrigen(m,null,independiente).size());assertEquals(1,origen.consultarOrigen(u,null,independiente).size());
        assertEquals(r,origen.registrarOrigen(datos,independiente,sesionIndividual,clave,PANTALLA));
        assertEquals(r,origen.consultarResultadoOrigen(clave,independiente).orElseThrow());
        assertEquals(cuenta,cuentasPersonales.consultarPropia(seleccionada.referencia(),seleccionada.revision()).idCuenta());
        long espacio=independiente.idOrganizacion();
        assertEquals(0,numero("select count(*) from persona_rol where organizacion_id=? and tipo_rol in ('BROKER','ADMIN')",espacio));
        assertEquals(0,numero("select count(*) from detalle_broker where organizacion_id=?",espacio));
        assertEquals(0,numero("select count(*) from supervision_agente where organizacion_id=?",espacio));
        assertEquals(0,numero("select count(*) from captacion where organizacion_id=?",espacio));
        assertEquals(1,numero("select count(*) from unidad_relacionada where organizacion_id=? and tipo_relacion='O'",espacio));
    }

    @Test void registraSituacionDeclaradaSinCrearPartidaTitularEncargoOPrecioAutorizado() {
        var d=datos(matriz,unidad,agente);
        var r=registrar(new DatosOrigen(d.idMatriz(),d.idUnidad(),d.vigenteDesde(),null,
                "Según documento TEST: división en trámite",d.fuente(),d.versionMatrizEsperada(),d.versionUnidadEsperada()));
        assertEquals("ORIGEN_MATRIZ_UNIDAD",r.relacion());assertEquals(EstadoDato.CONOCIDO,r.estadoSituacion());
        assertEquals("Según documento TEST: división en trámite",r.situacionRegistral());
        assertEquals(1,numero("select count(*) from unidad_relacionada where organizacion_id=? and tipo_relacion='O'",org()));
        assertEquals(0,numero("select count(*) from propiedad where organizacion_id=? and partida_registral is not null",org()));
        assertEquals(0,numero("select count(*) from titularidad_propiedad where organizacion_id=?",org()));
        assertEquals(0,numero("select count(*) from captacion where organizacion_id=?",org()));
        assertEquals(0,numero("select count(*) from precio_propiedad where organizacion_id=? and hito='U'",org()));
        assertEquals(1,origen.consultarOrigen(matriz,null,agente).size());
        assertEquals(matriz,origen.consultarOrigen(unidad,null,agente).getFirst().idMatriz());
    }

    @Test void desconocimientoConservaFaltanteFuenteProcedenciaEHistoriaDeIdentidades() {
        var r=registrar(datos(matriz,unidad,agente));
        assertNull(r.situacionRegistral());assertEquals(EstadoDato.FALTANTE,r.estadoSituacion());
        var fila=jdbc.queryForMap("select * from unidad_relacionada where id_unidad_relacionada=?",r.idRelacion());
        assertEquals(agente.idRolOperativo(),((Number)fila.get("id_rol_actor")).longValue());
        assertTrue(fila.get("procedencia").toString().contains("SPA"));
        assertEquals(r.fuente(),fila.get("fuente"));assertNotNull(fila.get("registrado_en"));
        assertEquals(1,eventosOrigen());assertEquals(1,recibos());
        assertEquals("O",fila.get("tipo_relacion"));
    }

    @Test void versionaAmbosExtremosSinCambiarSusDatosFisicosNiMezclarRevisionComercial() {
        var antesM=fisico(matriz);var antesU=fisico(unidad);var d=datos(matriz,unidad,agente);
        var r=registrar(d);
        assertEquals(d.versionMatrizEsperada()+1,r.versionMatriz());assertEquals(d.versionUnidadEsperada()+1,r.versionUnidad());
        assertEquals(antesM,fisico(matriz));assertEquals(antesU,fisico(unidad));
        assertEquals(r.versionMatriz(),version(matriz));assertEquals(r.versionUnidad(),version(unidad));
        var ficha=origen.consultarOrigen(matriz,null,agente).getFirst();
        assertEquals(d.versionMatrizEsperada(),ficha.versionMatrizObservada());assertEquals(d.versionUnidadEsperada(),ficha.versionUnidadObservada());
    }

    @Test void corteDistingueFechaDelHechoFechaDeRegistroYFinInclusivoEnLima() {
        var d=datos(matriz,unidad,agente);LocalDate hoy=OrigenesDePrueba.hoy();
        var r=registrar(new DatosOrigen(matriz,unidad,hoy,hoy,null,d.fuente(),d.versionMatrizEsperada(),d.versionUnidadEsperada()));
        assertEquals(0,origen.consultarOrigen(matriz,r.registradoEn().minusNanos(1000),agente).size());
        assertEquals(1,origen.consultarOrigen(matriz,r.registradoEn(),agente).size());
        Instant diaSiguiente=hoy.plusDays(1).atStartOfDay(LIMA).toInstant();
        assertEquals(0,origen.consultarOrigen(matriz,diaSiguiente,agente).size());
        assertEquals(1,origen.consultarOrigen(matriz,null,agente).size());
        assertEquals(hoy,r.vigenteDesde());assertEquals(hoy,r.vigenteHasta());
    }

    @Test void fechaHistoricaDeclaradaNoFabricaConocimientoAnteriorAlRegistro() {
        var d=datos(matriz,unidad,agente);LocalDate hecho=OrigenesDePrueba.hoy().minusDays(30);
        var r=registrar(new DatosOrigen(matriz,unidad,hecho,hecho.plusDays(2),"Declaración histórica TEST",d.fuente(),d.versionMatrizEsperada(),d.versionUnidadEsperada()));
        assertTrue(r.registradoEn().atZone(LIMA).toLocalDate().isAfter(r.vigenteHasta()));
        assertEquals(List.of(),origen.consultarOrigen(matriz,hecho.plusDays(1).atTime(12,0).atZone(LIMA).toInstant(),agente));
        assertEquals(1,origen.consultarOrigen(matriz,null,agente).size());
    }

    @Test void replayYConciliacionDevuelvenElReciboOriginalDespuesDeUnaEdicionPosterior() {
        var d=datos(matriz,unidad,agente);String clave=clave();
        var original=origen.registrarOrigen(d,agente,sesion,clave,PANTALLA);
        String recibo=jdbc.queryForObject("select resultado from comando_idempotente where organizacion_id=? and idempotency_key=?",String.class,org(),clave);
        propiedades.editar(matriz,new PropiedadUniversalService.ComandoEdicion(clave(),PANTALLA,
                "Descripción física posterior TEST",null,null,null,null,null).conVersion(original.versionMatriz()),agente);
        long actual=version(matriz);
        assertEquals(original,origen.registrarOrigen(d,agente,sesion,clave,PANTALLA));
        assertEquals(original,origen.consultarResultadoOrigen(clave,agente).orElseThrow());
        assertEquals(actual,version(matriz));assertTrue(actual>original.versionMatriz());
        assertEquals(recibo,jdbc.queryForObject("select resultado from comando_idempotente where organizacion_id=? and idempotency_key=?",String.class,org(),clave));
        assertEquals(1,eventosOrigen());assertEquals(1,relacionesO());
    }

    @Test void laMismaClaveConFuenteDistintaNoConfirmaOtroContenido() {
        var d=datos(matriz,unidad,agente);String clave=clave();origen.registrarOrigen(d,agente,sesion,clave,PANTALLA);
        assertThrows(ReglaNegocioException.class,()->origen.registrarOrigen(new DatosOrigen(matriz,unidad,d.vigenteDesde(),null,null,
                "Fuente distinta TEST",d.versionMatrizEsperada(),d.versionUnidadEsperada()),agente,sesion,clave,PANTALLA));
        assertEquals(1,relacionesO());assertEquals(1,eventosOrigen());
    }

    @Test void versionObsoletaDeUnExtremoNoAvanzaElOtroNiProduceVinculo() {
        var d=datos(matriz,unidad,agente);
        propiedades.editar(unidad,new PropiedadUniversalService.ComandoEdicion(clave(),PANTALLA,
                "Cambio físico antes de confirmar TEST",null,null,null,null,null).conVersion(d.versionUnidadEsperada()),agente);
        long vm=version(matriz),vu=version(unidad);
        assertThrows(ConflictoException.class,()->registrar(d));
        assertEquals(vm,version(matriz));assertEquals(vu,version(unidad));assertEquals(0,relacionesO());assertEquals(0,eventosOrigen());
    }

    @Test void lecturaDeAmbosNoConcedeResponsabilidadFisicaParaRegistrar() {
        for(long id:List.of(matriz,unidad)) prospecciones.registrar(new ProspeccionService.DatosProspeccion(id,"Seguimiento TEST del colega", null),equipo.colega());
        assertNotNull(propiedades.consultar(matriz,equipo.colega()));assertNotNull(propiedades.consultar(unidad,equipo.colega()));
        assertThrows(AccesoNoAutorizadoException.class,()->origen.capacidadSobreOrigen(matriz,unidad,equipo.colega()));
        var d=new DatosOrigen(matriz,unidad,OrigenesDePrueba.hoy(),null,null,"Fuente TEST",version(matriz),version(unidad));
        assertThrows(AccesoNoAutorizadoException.class,()->origen.registrarOrigen(d,equipo.colega(),fixtures.sesion(equipo.colega()),clave(),PANTALLA));
        assertEquals(0,relacionesO());
    }

    @Test void matrizVisibleNoDescubreLaUnidadOcultaYSuReplayAnteriorTambienSeRevoca() {
        var d=datos(matriz,unidad,agente);String clave=clave();origen.registrarOrigen(d,agente,sesion,clave,PANTALLA);
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(matriz,"Prospección TEST del colega sólo en matriz", null),equipo.colega());
        assertNotNull(propiedades.consultar(matriz,equipo.colega()));
        assertThrows(NoEncontradoException.class,()->propiedades.consultar(unidad,equipo.colega()));
        assertEquals(List.of(),origen.consultarOrigen(matriz,null,equipo.colega()));
        assertThrows(NoEncontradoException.class,()->origen.registrarOrigen(d,agente,sesion,clave,PANTALLA));
        assertThrows(NoEncontradoException.class,()->origen.consultarResultadoOrigen(clave,agente));
        assertEquals(1,relacionesO());assertEquals(1,eventosOrigen());
    }

    @Test void extremosDeOtroTenantNoSeVinculanNiFiltranHistoria() {
        var vecino=fixtures.empresa();long ajena=fixtures.propiedad(vecino.agente(),"Unidad de otro tenant");
        var d=new DatosOrigen(matriz,ajena,OrigenesDePrueba.hoy(),null,null,"Fuente TEST",version(matriz),version(ajena));
        assertThrows(NoEncontradoException.class,()->registrar(d));
        assertThrows(NoEncontradoException.class,()->origen.consultarOrigen(matriz,null,vecino.agente()));
        assertEquals(0,relacionesO());assertEquals(0,eventosOrigen());
    }

    @Test void brokerLeePorSuEquipoPeroNoRegistraYGobiernoNoLeeNiRegistra() {
        var d=datos(matriz,unidad,agente);registrar(d);
        assertEquals(1,origen.consultarOrigen(matriz,null,equipo.broker()).size());
        assertThrows(AccesoNoAutorizadoException.class,()->origen.consultarOrigen(matriz,null,equipo.admin()));
        for(Actor otro:List.of(equipo.broker(),equipo.admin())) {
            assertThrows(AccesoNoAutorizadoException.class,()->origen.registrarOrigen(d,otro,sesion,clave(),PANTALLA));
            assertThrows(AccesoNoAutorizadoException.class,()->origen.capacidadSobreOrigen(matriz,unidad,otro));
        }
        assertEquals(1,relacionesO());assertEquals(1,eventosOrigen());
    }

    @Test void unaSesionRevocadaNoProduceOrigenAunqueLasVersionesNoCambien() {
        var d=datos(matriz,unidad,agente);long vm=version(matriz),vu=version(unidad);
        contextos.cerrar(sesion.referenciaSesion(),sesion.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->registrar(d));
        assertEquals(vm,version(matriz));assertEquals(vu,version(unidad));assertEquals(0,relacionesO());assertEquals(0,recibos());
    }

    @Test void selfYParSolapadoSeRechazanPeroElMismoParConIntervalosDisjuntosSeConserva() {
        long vm=version(matriz);
        assertThrows(ReglaNegocioException.class,()->registrar(new DatosOrigen(matriz,matriz,OrigenesDePrueba.hoy(),null,null,"Fuente TEST",vm,vm)));
        LocalDate dia=OrigenesDePrueba.hoy().minusDays(10);
        registrar(conFechas(datos(matriz,unidad,agente),dia,dia.plusDays(1)));
        var solapada=conFechas(datos(matriz,unidad,agente),dia.plusDays(1),dia.plusDays(2));
        assertThrows(ConflictoException.class,()->registrar(solapada));
        registrar(conFechas(datos(matriz,unidad,agente),dia.plusDays(2),dia.plusDays(3)));
        assertEquals(2,relacionesO());assertEquals(2,origen.consultarOrigen(matriz,null,agente).size());
    }

    @Test void cicloDeTresIdentidadesEsRechazadoSinMutarLasVersionesRechazadas() {
        long tercera=fixtures.propiedad(agente,"Tercera unidad");
        registrar(datos(matriz,unidad,agente));registrar(datos(unidad,tercera,agente));
        var cierre=datos(tercera,matriz,agente);long v3=version(tercera),vm=version(matriz);
        assertThrows(ReglaNegocioException.class,()->registrar(cierre));
        assertEquals(v3,version(tercera));assertEquals(vm,version(matriz));assertEquals(2,relacionesO());assertFalse(hayCiclo());
    }

    @Test void finalizarVigenciasNoPermiteConvertirUnaUnidadEnSuPropioAncestroHistorico() {
        long tercera=fixtures.propiedad(agente,"Tercera histórica");LocalDate dia=OrigenesDePrueba.hoy().minusDays(30);
        registrar(conFechas(datos(matriz,unidad,agente),dia,dia));
        registrar(conFechas(datos(unidad,tercera,agente),dia.plusDays(10),dia.plusDays(10)));
        assertThrows(ReglaNegocioException.class,()->registrar(conFechas(datos(tercera,matriz,agente),dia.plusDays(20),dia.plusDays(20))));
        assertEquals(2,relacionesO());assertFalse(hayCiclo());
    }

    @Test void asociacionesAICNoSeReinterpretanNiSuplantanLaPartidaQueProtegeI() {
        // Contramontaje técnico V96, sin afirmar negocio, aprobación o procedencia desconocidos.
        jdbc.update("insert into unidad_relacionada(organizacion_id,id_propiedad_principal,id_propiedad_relacionada,tipo_relacion,vigente_desde) values(?,?,?,'A',?)",
                org(),matriz,unidad,OrigenesDePrueba.hoy());
        registrar(datos(matriz,unidad,agente));
        long independiente=fixtures.propiedad(agente,"Unidad independiente V96 TEST");
        propiedades.editar(independiente,new PropiedadUniversalService.ComandoEdicion(clave(),PANTALLA,null,null,null,
                List.of(new PropiedadUniversalService.ValorAtributo("partida_registral","PARTIDA-TEST-RF014")),null,null)
                .conVersion(version(independiente)),agente);
        jdbc.update("insert into unidad_relacionada(organizacion_id,id_propiedad_principal,id_propiedad_relacionada,tipo_relacion,vigente_desde) values(?,?,?,'I',?)",
                org(),matriz,independiente,OrigenesDePrueba.hoy());
        var legado=origen.deLaPropiedad(matriz,agente);
        assertEquals(Set.of("A","I","O"),legado.stream().map(UnidadRelacionadaService.FichaUnidadRelacionada::tipoRelacion).collect(java.util.stream.Collectors.toSet()));
        assertTrue(legado.stream().anyMatch(r->"O".equals(r.tipoRelacion())&&r.tipoRelacionRotulo().contains("Origen")));
        assertEquals(1,origen.consultarOrigen(matriz,null,agente).size());
        var retiro=new PropiedadUniversalService.ComandoEdicion(clave(),PANTALLA,null,null,null,null,null,List.of("partida_registral"))
                .conVersion(version(independiente)).conMotivoRetiro("Contraprueba TEST de la protección I de V96");
        var rechazo=assertThrows(RuntimeException.class,()->propiedades.editar(independiente,retiro,agente));
        assertTrue(mensajes(rechazo).toLowerCase().contains("partida"));
        assertEquals("PARTIDA-TEST-RF014",jdbc.queryForObject("select partida_registral from propiedad where id_propiedad=?",String.class,independiente));
    }

    @Test void falloDelEventoRevierteVinculoDosCasReciboYRevisionDelCoordinador() {
        var d=datos(matriz,unidad,agente);long vm=version(matriz),vu=version(unidad),coordinacion=revisionGrafo();String clave=clave();
        doThrow(new IllegalStateException("Sabotaje TEST al persistir evento RF014")).when(eventos)
                .saveAndFlush(argThat(e->e!=null&&"ORIGEN_FISICO_REGISTRADO".equals(e.getTipo())));
        assertThrows(IllegalStateException.class,()->origen.registrarOrigen(d,agente,sesion,clave,PANTALLA));
        reset(eventos);
        assertEquals(vm,version(matriz));assertEquals(vu,version(unidad));assertEquals(coordinacion,revisionGrafo());
        assertEquals(0,relacionesO());assertEquals(0,eventosOrigen());
        assertEquals(0,numero("select count(*) from comando_idempotente where organizacion_id=? and idempotency_key=?",org(),clave));
        assertTrue(origen.consultarResultadoOrigen(clave,agente).isEmpty());
    }

    @Test void sqlDirectoNoEludeSelfSolapadoTenantNiConservacionDeOrigen() {
        var d=datos(matriz,unidad,agente);var original=registrar(d);
        rechazoSql(insertSql(),org(),matriz,matriz,d.vigenteDesde(),null,d.versionMatrizEsperada(),d.versionMatrizEsperada(),agente.idRolOperativo());
        rechazoSql(insertSql(),org(),matriz,unidad,d.vigenteDesde(),null,d.versionMatrizEsperada(),d.versionUnidadEsperada(),agente.idRolOperativo());
        var vecino=fixtures.empresa();long ajena=fixtures.propiedad(vecino.agente(),"Contraprueba FK ajena");
        rechazoSql(insertSql(),org(),matriz,ajena,d.vigenteDesde(),null,version(matriz),version(ajena),agente.idRolOperativo());
        rechazoSql("update unidad_relacionada set fuente='Cambio que no conserva la declaración' where id_unidad_relacionada=?",original.idRelacion());
        rechazoSql("delete from unidad_relacionada where id_unidad_relacionada=?",original.idRelacion());
        assertEquals(1,relacionesO());assertEquals(1,eventosOrigen());assertEquals(original.fuente(),origen.consultarOrigen(matriz,null,agente).getFirst().fuente());
    }

    @Test void dosTransaccionesConLaMismaClaveProducenUnVinculoUnaTrazaYDosRecibosIguales() throws Exception {
        var d=datos(matriz,unidad,agente);String clave=clave();
        var resultados=competir(List.of(
                ()->transaccion(()->origen.registrarOrigen(d,agente,sesion,clave,PANTALLA)),
                ()->transaccion(()->origen.registrarOrigen(d,agente,sesion,clave,PANTALLA))));
        assertTrue(resultados.stream().allMatch(r->r.error()==null),resultados.toString());
        assertEquals(resultados.get(0).resultado(),resultados.get(1).resultado());
        assertEquals(1,relacionesO());assertEquals(1,eventosOrigen());assertEquals(1,recibos());assertEquals(1,revisionGrafo());
        assertEquals(d.versionMatrizEsperada()+1,version(matriz));assertEquals(d.versionUnidadEsperada()+1,version(unidad));
    }

    @Test void dosCuentasYAristasDisjuntasNoPuedenCerrarUnCicloConcurrente() throws Exception {
        var g=grafoParaCarrera();
        var resultados=competir(List.of(
                ()->transaccion(()->origen.registrarOrigen(g.bc(),agente,sesion,clave(),PANTALLA)),
                ()->transaccion(()->origen.registrarOrigen(g.da(),equipo.colega(),g.sesionColega(),clave(),PANTALLA))));
        assertEquals(1,resultados.stream().filter(r->r.error()==null).count(),resultados.toString());
        var fallo=resultados.stream().filter(r->r.error()!=null).findFirst().orElseThrow().error();
        assertTrue(mensajes(fallo).toLowerCase().contains("ciclo")||mensajes(fallo).toLowerCase().contains("circular"),mensajes(fallo));
        assertEquals(3,relacionesO());assertEquals(3,eventosOrigen());assertEquals(3,revisionGrafo());assertFalse(hayCiclo());
        assertEquals(3,recibos());
    }

    @Test void repeatableReadObsoletoNoEludeElGrafoDespuesDeEsperarOtroCommit() throws Exception {
        var g=grafoParaCarrera();long revision=revisionGrafo();
        CountDownLatch snapshot=new CountDownLatch(1),ganadora=new CountDownLatch(1);
        var hilo=Executors.newSingleThreadExecutor();
        var futura=hilo.submit(()->{
            var rr=tx();rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            try {
                return rr.execute(estado->{
                    limitesSql();
                    assertEquals(2,relacionesO());assertEquals(revision,revisionGrafo());
                    snapshot.countDown();esperar(ganadora);
                    try {insertarSql(g.da(),equipo.colega());return "ACEPTADO_SIN_GUARDA";}
                    finally {estado.setRollbackOnly();} // Nunca persiste una contraprueba que el producto permitiera por error.
                });
            } catch(RuntimeException rechazo) { return sqlState(rechazo)+":"+mensajes(rechazo); }
        });
        try {
            assertTrue(snapshot.await(5,TimeUnit.SECONDS),"No se estableció la instantánea RR antes del ganador.");
            var ganador=origen.registrarOrigen(g.bc(),agente,sesion,clave(),PANTALLA);
            assertNotNull(ganador.idRelacion());ganadora.countDown();
            String rechazo=futura.get(10,TimeUnit.SECONDS);
            assertFalse(rechazo.startsWith("ACEPTADO_SIN_GUARDA"),rechazo);
            assertTrue(rechazo.startsWith("40001:")||(rechazo.startsWith("23514:")
                    &&(rechazo.toLowerCase().contains("circular")||rechazo.toLowerCase().contains("ciclo"))),rechazo);
            assertEquals(3,relacionesO());assertEquals(revision+1,revisionGrafo());assertFalse(hayCiclo());
            assertEquals(3,eventosOrigen());assertEquals(3,recibos());
        } finally {
            ganadora.countDown();futura.cancel(true);hilo.shutdownNow();assertTrue(hilo.awaitTermination(3,TimeUnit.SECONDS));
        }
    }

    private record Grafo(DatosOrigen bc,DatosOrigen da,ContextoSesion sesionColega) { }
    private Grafo grafoParaCarrera() {
        long c=fixtures.propiedad(agente,"Grafo C"),d=fixtures.propiedad(agente,"Grafo D");
        registrar(datos(matriz,unidad,agente));registrar(datos(c,d,agente));
        for(long id:List.of(matriz,d)) {
            prospecciones.registrar(new ProspeccionService.DatosProspeccion(id,"Competencia física explícita TEST del colega", null),equipo.colega());
            propiedades.asignarResponsable(id,equipo.colega().idRolOperativo(),"Transferencia física TEST para carrera de cuentas distintas",
                    PropiedadUniversalService.ResponsableObservado.de(agente.idRolOperativo()),equipo.broker());
        }
        assertEquals(equipo.colega().idRolOperativo(),jdbc.queryForObject("select id_rol_responsable from propiedad where id_propiedad=?",Long.class,matriz));
        assertNotEquals(cuenta(agente),cuenta(equipo.colega()),"La carrera no puede quedar serializada sólo por una cuenta compartida.");
        return new Grafo(datos(unidad,c,agente),datos(d,matriz,equipo.colega()),fixtures.sesion(equipo.colega()));
    }
    private long cuenta(Actor a) {return jdbc.queryForObject("select id_usuario from usuario_organizacion where organizacion_id=? and id_persona=? and estado='A'",Long.class,a.idOrganizacion(),a.idPersona());}
    private long org(){return agente.idOrganizacion();}
    private DatosOrigen datos(long m,long u,Actor a){return fixtures.datos(m,u,a,origen);}
    private ResultadoOrigen registrar(DatosOrigen d){return origen.registrarOrigen(d,agente,sesion,clave(),PANTALLA);}
    private static String clave(){return OrigenesDePrueba.clave();}
    private static DatosOrigen conFechas(DatosOrigen d,LocalDate desde,LocalDate hasta){return new DatosOrigen(d.idMatriz(),d.idUnidad(),desde,hasta,d.situacionRegistral(),d.fuente(),d.versionMatrizEsperada(),d.versionUnidadEsperada());}
    private long version(long id){return jdbc.queryForObject("select version_fisica from propiedad where id_propiedad=?",Long.class,id);}
    private String fisico(long id){return jdbc.queryForObject("select (to_jsonb(p)-'version_fisica')::text from propiedad p where id_propiedad=?",String.class,id);}
    private int relacionesO(){return numero("select count(*) from unidad_relacionada where organizacion_id=? and tipo_relacion='O'",org());}
    private int eventosOrigen(){return numero("select count(*) from evento_dominio where organizacion_id=? and tipo='ORIGEN_FISICO_REGISTRADO'",org());}
    private int recibos(){return numero("select count(*) from comando_idempotente where organizacion_id=? and tipo_comando='REGISTRAR_ORIGEN_FISICO_UNIDAD'",org());}
    private int numero(String sql,Object... args){return jdbc.queryForObject(sql,Integer.class,args);}
    private long revisionGrafo(){Long n=jdbc.queryForObject("select coalesce((select revision from coordinacion_origen_fisico where organizacion_id=?),0)",Long.class,org());return n==null?0:n;}
    private boolean hayCiclo(){return Boolean.TRUE.equals(jdbc.queryForObject("""
            with recursive camino(origen,destino) as (
              select id_propiedad_principal,id_propiedad_relacionada from unidad_relacionada where organizacion_id=? and tipo_relacion='O'
              union
              select c.origen,u.id_propiedad_relacionada from camino c join unidad_relacionada u on u.id_propiedad_principal=c.destino
               where u.organizacion_id=? and u.tipo_relacion='O'
            ) select exists(select 1 from camino where origen=destino)
            """,Boolean.class,org(),org()));}
    private TransactionTemplate tx(){var t=new TransactionTemplate(gestor);t.setTimeout(12);return t;}
    private void limitesSql(){jdbc.execute("SET LOCAL lock_timeout='8s'");jdbc.execute("SET LOCAL statement_timeout='9s'");}
    private ResultadoOrigen transaccion(Callable<ResultadoOrigen> efecto){return tx().execute(t->{limitesSql();try{return efecto.call();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}});}
    private void rechazoSql(String sql,Object... args){assertThrows(DataAccessException.class,()->tx().execute(t->{try{jdbc.update(sql,args);return null;}finally{t.setRollbackOnly();}}));}
    private static String insertSql(){return """
            insert into unidad_relacionada(organizacion_id,id_propiedad_principal,id_propiedad_relacionada,tipo_relacion,
              vigente_desde,vigente_hasta,fuente,procedencia,version_matriz_observada,version_unidad_observada,id_rol_actor,registrado_en)
            values(?,?,?,'O',?,?,'CONTRAPRUEBA SQL TEST','{}',?,?,?,clock_timestamp())
            """;}
    private void insertarSql(DatosOrigen d,Actor a){jdbc.update(insertSql(),a.idOrganizacion(),d.idMatriz(),d.idUnidad(),d.vigenteDesde(),d.vigenteHasta(),d.versionMatrizEsperada(),d.versionUnidadEsperada(),a.idRolOperativo());}
    private static void esperar(CountDownLatch latch){try{if(!latch.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Barrera TEST agotada");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private record ResultadoCompetencia(ResultadoOrigen resultado,RuntimeException error) { }
    private static List<ResultadoCompetencia> competir(List<Callable<ResultadoOrigen>> tareas) throws Exception {
        var pool=Executors.newFixedThreadPool(tareas.size());CountDownLatch listos=new CountDownLatch(tareas.size()),salida=new CountDownLatch(1);
        var futuros=new ArrayList<java.util.concurrent.Future<ResultadoCompetencia>>();
        try {
            for(var tarea:tareas)futuros.add(pool.submit(()->{listos.countDown();esperar(salida);try{return new ResultadoCompetencia(tarea.call(),null);}catch(RuntimeException e){return new ResultadoCompetencia(null,e);}}));
            assertTrue(listos.await(5,TimeUnit.SECONDS));salida.countDown();
            List<ResultadoCompetencia> r=new ArrayList<>();for(var f:futuros)r.add(f.get(10,TimeUnit.SECONDS));return r;
        } finally {salida.countDown();futuros.forEach(f->f.cancel(true));pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
    }
    private static String sqlState(Throwable fallo){for(Throwable t=fallo;t!=null;t=t.getCause())if(t instanceof SQLException s)return s.getSQLState();return "NO_SQLSTATE";}
    private static String mensajes(Throwable fallo){StringBuilder b=new StringBuilder();for(Throwable t=fallo;t!=null;t=t.getCause())b.append(t.getMessage()).append(' ');return b.toString();}
}
