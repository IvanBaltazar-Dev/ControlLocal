package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.integracion.soporte.SesionesComercialesDePrueba;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.RequerimientoService.*;
import com.controllocal.service.GobiernoCatalogoCapturaService.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/** RF028–030: Core real, actores TEST actuales y sabotajes SQL con rollback. No acredita login por fixture. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=10")
class DemandaC3IntegrationTest {
    private static final Procedencia PROC=Procedencia.deLaPantalla();
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired RequerimientoService demandas;
    @Autowired FichaComercialService fichas;
    @Autowired GobiernoCatalogoCapturaService catalogos;
    @Autowired ActuacionesService actuaciones;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired ObjectMapper json;
    @MockitoSpyBean EventoDominioRepository eventos;
    private OrigenesDePrueba identidades;
    private OrigenesDePrueba.Equipo equipo;
    private Actor agente;
    private ContextoSesion sesion;

    @BeforeEach void identidadAislada() {
        // Se reutiliza únicamente el banco C1 de cuenta/pertenencia/supervisión.
        // No se invoca propiedad(): Demanda no necesita Propiedad ni Encargo.
        identidades=new OrigenesDePrueba(jdbc,gestor,captura,contextos);
        equipo=identidades.empresa();agente=equipo.agente();sesion=identidades.sesion(agente);
    }
    @AfterEach void retirarSabotaje(){reset(eventos);}

    @Test void contactoParcialSeCreaAtomicamenteSinDniClientePropiedadNiEncargoFicticios() {
        var alta=crear(contacto("Contacto parcial TEST",null));
        assertEquals("A",alta.estado());assertEquals(0,alta.version());assertEquals(agente.idRolOperativo(),alta.idResponsable());
        assertNull(alta.idCliente());assertNotNull(alta.idPersonaInteresada());assertNull(alta.moneda());assertNull(alta.presupuestoMin());assertNull(alta.presupuestoMax());
        assertEquals("999000001",jdbc.queryForObject("select telefono from persona where organizacion_id=? and id_persona=?",String.class,org(),alta.idPersonaInteresada()));
        assertNull(jdbc.queryForObject("select numero_documento from persona where organizacion_id=? and id_persona=?",String.class,org(),alta.idPersonaInteresada()));
        assertEquals(0,numero("select count(*) from persona_rol where organizacion_id=? and id_persona=?",org(),alta.idPersonaInteresada()));
        assertEquals(0,numero("select count(*) from detalle_cliente where organizacion_id=?",org()));
        assertEquals(0,numero("select count(*) from propiedad where organizacion_id=?",org()));
        assertEquals(0,numero("select count(*) from captacion where organizacion_id=?",org()));
        assertEquals(1,versiones(alta.id()));assertEquals(1,eventos("REGISTRAR_DEMANDA"));assertEquals(1,recibos("REGISTRAR_DEMANDA"));
        assertEquals(alta,demandas.consultar(alta.id(),0L,null,agente));
    }
    @Test void tiposSonAlternativasDeUnaDemandaYNoUnaSeleccionDelPrimerTipo() {
        var d=datos(null,List.of("DEPARTAMENTO","OFICINA"),null,null,"Alternativas TEST",Set.of());
        var alta=crear(d);assertEquals(Set.of("D","O"),Set.copyOf(alta.tiposInmueble()));assertNull(alta.tipoInmueble());
        var consulta=demandas.paraMatching(alta.id(),alta.version(),Modalidad.COMERCIAL,"D1-TEST",Instant.now(),agente);
        var tipo=consulta.criterios().stream().filter(c->c.alternativas().stream().anyMatch(a->a.claveDato().equals(com.controllocal.service.matching.ContratoMatching.TIPO_PROPIEDAD))).findFirst().orElseThrow();
        assertEquals(2,tipo.alternativas().size());assertEquals(Set.of("D","O"),tipo.alternativas().stream().map(a->a.valor().texto()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1,numero("select count(*) from requerimiento_cliente where organizacion_id=?",org()));
    }
    @Test void criterioConocidoCeroYCincoNoSeConfundenConFaltanteYNuncaEscribenEscalaresLegacy() {
        var cero=crear(datos(null,List.of("DEPARTAMENTO"),List.of(numeroPedido("IGUAL",BigDecimal.ZERO)),null,"Cero TEST",Set.of()));
        var cinco=crear(datos(null,List.of("DEPARTAMENTO"),List.of(numeroPedido("MINIMO",new BigDecimal("5"))),null,"Cinco TEST",Set.of()));
        assertEquals(EstadoDato.CONOCIDO,cero.criterios().getFirst().normalizado().alternativas().getFirst().metadatos().estado());
        assertEquals(0,cero.criterios().getFirst().valorNumero().compareTo(BigDecimal.ZERO));assertEquals(new BigDecimal("5"),cinco.criterios().getFirst().valorNumero());
        assertEquals(0,numero("select count(*) from requerimiento_criterio where organizacion_id=? and (operador is not null or peso is not null or valor_numero is not null or valor_texto is not null)",org()));
        assertEquals(0,numero("select count(*) from requerimiento_criterio_valor v join requerimiento_criterio c on c.id_criterio=v.id_criterio where c.organizacion_id=?",org()));
    }
    @Test void editarConVersionObservadaConservaCriterioYAntecedenteExactos() {
        var alta=crear(datos(null,List.of("DEPARTAMENTO"),List.of(numeroPedido("MINIMO",BigDecimal.ONE)),null,"Original TEST",Set.of()));
        String criterioOriginal=criterioJson(alta.id(),alta.version());long observada=alta.version();
        var editada=demandas.actualizar(alta.id(),observada,edicion("Corrección declarada TEST"),agente,sesion,clave(),PROC);
        assertEquals(observada+1,editada.version());assertEquals(criterioOriginal,criterioJson(alta.id(),editada.version()));
        assertEquals("Original TEST",demandas.consultar(alta.id(),observada,null,agente).observaciones());
        assertEquals("Corrección declarada TEST",demandas.buscar(alta.id(),agente).observaciones());
        assertEquals(2,versiones(alta.id()));assertEquals(1,eventos("EDITAR_DEMANDA"));
    }
    @Test void copiaOmitidaConservaDefinicionAnteriorExactaAunqueElCatalogoCambieDeVersion() {
        var sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);var gov=equipo.admin();var sidGov=sesiones.de(gov);
        var origen=catalogos.consultarDefinicion(definicion("ascensores").id(),null,gov,sidGov).datos();
        String claveDef="test_demanda_"+UUID.randomUUID().toString().replace("-","");
        var inicial=definicionPrivada(origen,claveDef,"Rótulo original TEST");var def=catalogos.crearDefinicion(inicial,gov,sidGov);
        var p=new CriterioPedido(claveDef,"MINIMO","I",null,BigDecimal.ONE,null,null,null,null,null,null,null,
                new ContextoValor(null,inicial.unidad(),null,null,"ALQUILER",null),new MetadatosDato(EstadoDato.CONOCIDO,null,def.referenciaCatalogo(),def.version(),"fuente-original-test","Declaración original TEST",hoy().minusDays(2),null,null),false,null);
        var alta=crear(datos(null,List.of("DEPARTAMENTO"),List.of(p),null,"Conservación definición TEST",Set.of()));
        String original=criterioJson(alta.id(),alta.version());long observada=alta.version();
        catalogos.versionarDefinicion(def.id(),def.version(),definicionPrivada(origen,claveDef,"Rótulo posterior TEST"),gov,sidGov);
        var editada=demandas.actualizar(alta.id(),observada,edicion("Otro campo TEST"),agente,sesion,clave(),PROC);
        assertEquals(original,criterioJson(alta.id(),editada.version()));assertEquals("Rótulo original TEST",editada.criterios().getFirst().rotulo());
        assertEquals(def.version(),editada.criterios().getFirst().normalizado().alternativas().getFirst().metadatos().versionDefinicion());
        assertEquals(hoy().minusDays(2),editada.criterios().getFirst().normalizado().alternativas().getFirst().metadatos().observadoEn());
    }
    @Test void importeDePresupuestoSinMonedaSeConservaSinConvertirseEnPresupuestoComparable() {
        var ref=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,new DatosContacto(null,null,null,null,"999000001",null));
        var d=new DatosRequerimiento(null,null,null,null,null,null,"ALQUILER",List.of("DEPARTAMENTO"),null,new BigDecimal("3000"),null,null,null,null,null,"Presupuesto parcial TEST",null,null,ref,null,null,"TOTAL_MENSUAL_OBLIGATORIO","MENSUAL",Set.of());
        var alta=crear(d);assertEquals(new BigDecimal("3000"),alta.presupuestoMax());assertNull(alta.moneda());assertNull(alta.presupuestoMin());
        var q=demandas.paraMatching(alta.id(),alta.version(),Modalidad.COMERCIAL,"D1-TEST",Instant.now(),agente);
        var a=q.criterios().stream().flatMap(c->c.alternativas().stream()).filter(c->c.claveDato().equals(com.controllocal.service.matching.ContratoMatching.TOTAL_MENSUAL_OBLIGATORIO)).findFirst().orElseThrow();
        assertEquals(EstadoDato.FALTANTE,a.metadatos().estado());assertEquals(Causa.MONEDA_NO_COMPARABLE,a.metadatos().causa());assertNull(a.valor().moneda());assertEquals(0,a.valor().numero().compareTo(new BigDecimal("3000")));
    }
    @Test void replayDeAltaTrasEdicionRestauraElOriginalSinOtraPersonaNiVersion() {
        var d=contacto("Pedido original TEST",null);String clave=clave();
        var original=demandas.crear(d,agente,sesion,clave,PROC);long versionObservada=original.version();
        String claveEdicion=clave();var actual=demandas.actualizar(original.id(),versionObservada,edicion("Pedido editado TEST"),agente,sesion,claveEdicion,PROC);
        var actualObservado=demandas.buscar(original.id(),agente);
        assertEquals(original,demandas.crear(d,agente,sesion,clave,PROC));assertEquals(original,demandas.consultarResultado(clave,agente).orElseThrow());
        assertEquals(actual,demandas.consultarResultado(claveEdicion,agente).orElseThrow());
        assertEquals(actualObservado,demandas.buscar(original.id(),agente));assertEquals(2,versiones(original.id()));
        assertEquals(1,numero("select count(*) from persona where organizacion_id=? and telefono='999000001'",org()));
        assertEquals(1,eventos("REGISTRAR_DEMANDA"));assertEquals(1,recibos("REGISTRAR_DEMANDA"));
    }
    @Test void claveReutilizadaConOtroPedidoNoConfirmaLoQueNoSePidio() {
        String k=clave();var original=demandas.crear(contacto("Pedido uno TEST",null),agente,sesion,k,PROC);
        var antes=demandas.buscar(original.id(),agente);
        assertThrows(ReglaNegocioException.class,()->demandas.crear(contacto("Pedido diferente TEST",null),agente,sesion,k,PROC));
        assertEquals(antes,demandas.buscar(original.id(),agente));assertEquals(original,demandas.consultarResultado(k,agente).orElseThrow());
        assertEquals(1,versiones(original.id()));assertEquals(1,eventos("REGISTRAR_DEMANDA"));
    }
    @Test void replayNoUsaElReciboParaSaltarLaRevocacionRealDeLaSesion() {
        String k=clave();var d=contacto("Pedido TEST",null);var alta=demandas.crear(d,agente,sesion,k,PROC);
        contextos.cerrar(sesion.referenciaSesion(),sesion.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->demandas.crear(d,agente,sesion,k,PROC));
        assertEquals(1,versiones(alta.id()));assertEquals(1,eventos("REGISTRAR_DEMANDA"));assertEquals(1,recibos("REGISTRAR_DEMANDA"));
    }
    @Test void dosAgentesDelMismoClienteConservanDemandasSeparadasEnLectorYFichaAgregada() {
        long cliente=clienteTest();var d=datos(cliente,List.of("DEPARTAMENTO"),null,null,"Demanda propia TEST",Set.of());
        var propia=demandas.crear(d,agente,sesion,clave(),PROC);
        var colega=equipo.colega();var ajena=demandas.crear(datos(cliente,List.of("OFICINA"),null,null,"Demanda colega TEST",Set.of()),colega,identidades.sesion(colega),clave(),PROC);
        assertEquals(List.of(propia.id()),demandas.listarPorCliente(cliente,agente).stream().map(FichaRequerimiento::id).toList());
        assertEquals(List.of(ajena.id()),demandas.listarPorCliente(cliente,colega).stream().map(FichaRequerimiento::id).toList());
        var ficha=fichas.fichaCliente(cliente,8,agente);var seccion=ficha.sections().get("requerimientos");
        assertNotNull(seccion);assertEquals(1,seccion.totalRecords());assertEquals(propia.id().toString(),seccion.items().getFirst().id());
        assertThrows(AccesoNoAutorizadoException.class,()->demandas.buscar(ajena.id(),agente));
    }
    @Test void niBrokerNiGobiernoNiOtroTenantObtienenLaFichaCompleta() {
        var alta=crear(contacto("Privada TEST",null));
        assertThrows(AccesoNoAutorizadoException.class,()->demandas.buscar(alta.id(),equipo.broker()));
        assertThrows(AccesoNoAutorizadoException.class,()->demandas.buscar(alta.id(),equipo.admin()));
        var otro=identidades.empresa();assertThrows(NoEncontradoException.class,()->demandas.buscar(alta.id(),otro.agente()));
        var minimo=tx().execute(t->demandas.contextoDisponibilidad(alta.id(),equipo.broker(),false));
        assertEquals(alta.id(),minimo.idDemanda());assertEquals(alta.version(),minimo.version());assertEquals(alta.idResponsable(),minimo.responsableId());
    }
    @Test void dosEdicionesDeLaMismaVersionProducenUnEfectoYUnConflicto() throws Exception {
        var alta=crear(contacto("Original CAS TEST",null));long observada=alta.version();
        var resultados=competir(List.of(
                ()->demandas.actualizar(alta.id(),observada,edicion("Edición A TEST"),agente,sesion,clave(),PROC),
                ()->demandas.actualizar(alta.id(),observada,edicion("Edición B TEST"),agente,sesion,clave(),PROC)));
        assertEquals(1,resultados.stream().filter(r->r.error()==null).count());
        var error=resultados.stream().map(Competencia::error).filter(Objects::nonNull).findFirst().orElseThrow();assertInstanceOf(ConflictoException.class,error);
        assertEquals(observada+1,demandas.buscar(alta.id(),agente).version());assertEquals(2,versiones(alta.id()));
        assertEquals(1,eventos("EDITAR_DEMANDA"));assertEquals(1,recibos("EDITAR_DEMANDA"));
    }
    @Test void altasConcurrentesDelMismoComandoCreanUnaSolaPersonaDemandaYRecibo() throws Exception {
        var d=contacto("Alta concurrente TEST",null);String k=clave();
        var resultados=competir(List.of(()->demandas.crear(d,agente,sesion,k,PROC),()->demandas.crear(d,agente,sesion,k,PROC)));
        assertTrue(resultados.stream().allMatch(r->r.error()==null));assertEquals(resultados.get(0).resultado(),resultados.get(1).resultado());
        assertEquals(1,numero("select count(*) from requerimiento_cliente where organizacion_id=?",org()));
        assertEquals(1,numero("select count(*) from persona where organizacion_id=? and telefono='999000001'",org()));
        assertEquals(1,eventos("REGISTRAR_DEMANDA"));assertEquals(1,recibos("REGISTRAR_DEMANDA"));
    }
    @Test void falloDelEventoReviertePersonaDemandaSnapshotCriteriosYRecibo() {
        long personas=numero("select count(*) from persona where organizacion_id=?",org());
        doThrow(new IllegalStateException("Sabotaje TEST evento Demanda")).when(eventos).saveAndFlush(argThat(e->e!=null&&"REGISTRAR_DEMANDA".equals(e.getTipo())));
        assertThrows(IllegalStateException.class,()->crear(datos(null,List.of("DEPARTAMENTO"),List.of(numeroPedido("MINIMO",BigDecimal.ONE)),null,"Rollback TEST",Set.of())));
        reset(eventos);assertEquals(personas,numero("select count(*) from persona where organizacion_id=?",org()));
        for(String tabla:List.of("requerimiento_cliente","requerimiento_version","requerimiento_criterio"))assertEquals(0,numero("select count(*) from "+tabla+" where organizacion_id=?",org()),tabla);
        assertEquals(0,eventos("REGISTRAR_DEMANDA"));assertEquals(0,recibos("REGISTRAR_DEMANDA"));
    }
    @Test void retiroDesdeRevisionExactaConservaHistoriaYSuReciboOriginal() {finPorActuacion(TipoFinDemanda.RETIRADA);}
    @Test void finalizacionDesdeRevisionExactaConservaHistoriaYSuReciboOriginal() {finPorActuacion(TipoFinDemanda.FINALIZADA);}
    @Test void propuestaDeFinQuedaObsoletaTrasEditarLaDemandaObservada() {
        var alta=crear(contacto("Antes TEST",null));long observada=alta.version();
        var propuesta=actuaciones.prepararFinDemanda(agente,new FinDemanda(alta.id(),observada,TipoFinDemanda.RETIRADA,hoy(),"Fin TEST"),sesion,PROC);
        var revision=actuaciones.revisar(agente,propuesta.referencia(),sesion);
        var editada=demandas.actualizar(alta.id(),observada,edicion("Cambió antes de confirmar TEST"),agente,sesion,clave(),PROC);
        var antesDeConfirmar=demandas.buscar(alta.id(),agente);assertEquals(editada.version(),antesDeConfirmar.version());
        assertThrows(ConflictoException.class,()->actuaciones.confirmar(agente,propuesta.referencia(),sesion,
                new ActuacionesService.Confirmacion(revision.propuesta().version(),revision.propuesta().huella(),revision.pruebaRevision(),clave()),PROC));
        assertEquals(antesDeConfirmar,demandas.buscar(alta.id(),agente));assertEquals(0,eventos("RETIRAR_O_FINALIZAR_DEMANDA"));
    }
    @Test void sqlNoPermiteFinConTipoOMotivoNullAunqueCasYLosDemasCamposSeanValidos() {
        var alta=crear(contacto("Contraprueba NULL TEST",null));
        var antes=demandas.buscar(alta.id(),agente);
        for(boolean faltaMotivo:List.of(true,false)) {
            var error=assertThrows(DataAccessException.class,()->tx().execute(t->{
                jdbc.update("update requerimiento_cliente set version=?,estado='R',fin_tipo=?,fin_fecha=?,fin_motivo=?,fin_registrado_en=? where organizacion_id=? and id_requerimiento=?",
                        alta.version()+1,faltaMotivo?"R":null,hoy(),faltaMotivo?null:"Motivo real TEST",OffsetDateTime.now(ZoneOffset.UTC),org(),alta.id());return null;
            }));
            assertEquals("23514",sqlState(error));assertTrue(mensajes(error).contains("ck_demanda_fin"),mensajes(error));
            assertEquals(antes,demandas.buscar(alta.id(),agente));assertEquals(1,versiones(alta.id()));assertEquals(0,recibos("RETIRAR_O_FINALIZAR_DEMANDA"));
        }
    }
    @Test void sqlSnapshotFinAusenteNoEsUnAntecedenteEquivalenteAlHechoRegistrado() {snapshotFinIncorrecto(true);}
    @Test void sqlSnapshotFinConOtroMotivoNoEsUnAntecedenteEquivalenteAlHechoRegistrado() {snapshotFinIncorrecto(false);}
    @Test void sqlOperandoConocidoDeFormaIncorrectaNoAprovechaUnknownDeCheck() throws Exception {
        var alta=crear(contacto("Forma SQL TEST",null));var def=definicion("ascensores");
        var pedido=numeroPedido("MINIMO",BigDecimal.ONE);var normalizado=new com.controllocal.service.matching.ContratoMatching.Criterio("criterio:ascensores","ascensores",Exigencia.OBLIGATORIO,null,List.of(
                new Condicion("PROPIEDAD:ascensores",com.controllocal.domain.inmueble.TipoDato.ENTERO,com.controllocal.domain.comercial.OperadorCriterio.MINIMO,
                        new com.controllocal.service.soporte.ValorLogico("no es número",null,null,null,null,null),null,false,
                        new ContextoValor(null,def.unidad(),null,null,"ALQUILER",null),pedido.metadatos())));
        String invalido=json.writeValueAsString(normalizado);
        var error=assertThrows(DataAccessException.class,()->tx().execute(t->{try{
            jdbc.update("insert into requerimiento_criterio(organizacion_id,id_requerimiento,version_demanda,clave,criterio_json,rotulo,unidad,tipo_dato) values(?,?,?,?,?,?,?,?)",
                    org(),alta.id(),alta.version(),"ascensores",invalido,def.rotulo(),def.unidad(),def.tipo());return null;
        }finally{t.setRollbackOnly();}}));
        assertEquals("23514",sqlState(error));assertTrue(mensajes(error).contains("forma tipada"),mensajes(error));
        assertEquals(0,numero("select count(*) from requerimiento_criterio where organizacion_id=? and id_requerimiento=?",org(),alta.id()));
    }
    @Test void sqlHistoriaYCriteriosSeConservanFrenteAUpdateYDelete() {
        var alta=crear(datos(null,List.of("DEPARTAMENTO"),List.of(numeroPedido("MINIMO",BigDecimal.ONE)),null,"Append-only TEST",Set.of()));
        String original=criterioJson(alta.id(),0);
        for(String sql:List.of("update requerimiento_version set snapshot_json='{}' where organizacion_id=? and id_requerimiento=?",
                "delete from requerimiento_version where organizacion_id=? and id_requerimiento=?",
                "update requerimiento_criterio set criterio_json='{}' where organizacion_id=? and id_requerimiento=?",
                "delete from requerimiento_criterio where organizacion_id=? and id_requerimiento=?")) {
            var error=assertThrows(DataAccessException.class,()->tx().execute(t->{try{jdbc.update(sql,org(),alta.id());return null;}finally{t.setRollbackOnly();}}));
            assertEquals("23514",sqlState(error));assertTrue(mensajes(error).contains("append-only"),mensajes(error));
        }
        assertEquals(original,criterioJson(alta.id(),0));assertEquals(1,versiones(alta.id()));
    }
    @Test void zonificacionRealSeLeeDelPerfilCerradoYNoDeUnTextoLibreODistritoInferido() {
        var perfil=perfilTest();var def=definicion("zonificacion");
        var distritos=List.of("Santiago de Surco");
        var contexto=new ContextoValor(null,null,null,null,"ALQUILER",null,perfil.datos().jurisdiccion(),perfil.referenciaCatalogo(),perfil.datos().versionFuente());
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,def.referencia(),def.version(),"documento-privado-test","Fuente declarada TEST",hoy(),null,null);
        var p=new CriterioPedido("zonificacion","IGUAL","I","Z-TEST",null,null,null,null,null,null,null,null,contexto,meta,false,null);
        var alta=crear(datos(null,List.of("TERRENO"),List.of(p),distritos,"Zonificación TEST",Set.of()));
        assertEquals(distritos,alta.distritos());
        var a=alta.criterios().getFirst().normalizado().alternativas().getFirst();
        assertEquals(EstadoDato.CONOCIDO,a.metadatos().estado());assertEquals("Z-TEST",a.valor().texto());
        assertEquals(def.referencia(),a.metadatos().referenciaDefinicion());assertEquals(perfil.referenciaCatalogo(),a.contexto().referenciaCatalogo());
        assertEquals(perfil.datos().versionFuente(),a.contexto().versionFuente());
        var sinContexto=new CriterioPedido("zonificacion","IGUAL","I","Z-TEST",null,null,null,null,null,null,null,null,null,meta,false,null);
        var pendiente=crear(datos(null,List.of("TERRENO"),List.of(sinContexto),distritos,"Sin jurisdicción TEST",Set.of()));
        assertEquals(distritos,pendiente.distritos());
        assertEquals(EstadoDato.FALTANTE,pendiente.criterios().getFirst().normalizado().alternativas().getFirst().metadatos().estado());
        assertNull(pendiente.criterios().getFirst().normalizado().alternativas().getFirst().contexto().jurisdiccion());
    }
    private void finPorActuacion(TipoFinDemanda tipo) {
        var alta=crear(contacto("Original antes del fin TEST",null));long observada=alta.version();
        var antesDeProponer=demandas.buscar(alta.id(),agente);
        var propuesta=actuaciones.prepararFinDemanda(agente,new FinDemanda(alta.id(),observada,tipo,hoy(),"Solicitud expresa TEST"),sesion,PROC);
        assertEquals("retirar_finalizar_demanda",propuesta.capacidad());assertEquals(antesDeProponer,demandas.buscar(alta.id(),agente));
        var revision=actuaciones.revisar(agente,propuesta.referencia(),sesion);
        var confirmacion=new ActuacionesService.Confirmacion(revision.propuesta().version(),revision.propuesta().huella(),revision.pruebaRevision(),clave());
        var r=actuaciones.confirmar(agente,propuesta.referencia(),sesion,confirmacion,PROC);
        assertEquals("EXITO",r.estado());var actual=demandas.buscar(alta.id(),agente);
        assertEquals(tipo==TipoFinDemanda.RETIRADA?"R":"F",actual.estado());assertEquals(observada+1,actual.version());
        assertEquals("Solicitud expresa TEST",actual.fin().motivo());assertEquals(hoy(),actual.fin().fechaDelHecho());
        assertEquals(alta,demandas.consultar(alta.id(),observada,null,agente));assertEquals(2,versiones(alta.id()));
        assertEquals(1,eventos("RETIRAR_O_FINALIZAR_DEMANDA"));assertEquals(1,recibos("RETIRAR_O_FINALIZAR_DEMANDA"));
        assertEquals(r,actuaciones.confirmar(agente,propuesta.referencia(),sesion,confirmacion,PROC));
        assertEquals(1,eventos("RETIRAR_O_FINALIZAR_DEMANDA"));assertEquals(2,versiones(alta.id()));
    }
    private void snapshotFinIncorrecto(boolean ausente) {
        var alta=crear(contacto("Snapshot fin TEST",null));String inicial=jdbc.queryForObject("select snapshot_json from requerimiento_version where organizacion_id=? and id_requerimiento=? and version=?",String.class,org(),alta.id(),alta.version());
        var antes=demandas.buscar(alta.id(),agente);
        var instante=Instant.now().truncatedTo(ChronoUnit.MICROS);var error=assertThrows(DataAccessException.class,()->tx().execute(t->{
            jdbc.update("update requerimiento_cliente set version=?,estado='R',fin_tipo='R',fin_fecha=?,fin_motivo='Motivo real TEST',fin_registrado_en=? where organizacion_id=? and id_requerimiento=?",
                    alta.version()+1,hoy(),OffsetDateTime.ofInstant(instante,ZoneOffset.UTC),org(),alta.id());
            try {
                var s=(ObjectNode)json.readTree(inicial);s.put("version",alta.version()+1);s.put("estado","R");
                if(ausente)s.putNull("fin");else s.set("fin",json.valueToTree(new FinDeclarado("R",hoy(),"Motivo distinto TEST",instante)));
                jdbc.update("insert into requerimiento_version(organizacion_id,id_requerimiento,version,id_rol_actor,registrado_en,snapshot_json,procedencia_json) select organizacion_id,id_requerimiento,version,id_rol_responsable,clock_timestamp(),?,procedencia_json from requerimiento_cliente where organizacion_id=? and id_requerimiento=?",
                        json.writeValueAsString(s),org(),alta.id());return null;
            }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
        }));
        assertEquals("23514",sqlState(error));assertTrue(mensajes(error).contains("snapshot nuevo"),mensajes(error));
        assertEquals(antes,demandas.buscar(alta.id(),agente));assertEquals(inicial,jdbc.queryForObject("select snapshot_json from requerimiento_version where organizacion_id=? and id_requerimiento=? and version=?",String.class,org(),alta.id(),alta.version()));
        assertEquals(1,versiones(alta.id()));assertEquals(0,recibos("RETIRAR_O_FINALIZAR_DEMANDA"));
    }
    private Perfil perfilTest() {
        var sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        var def=definicion("zonificacion");String juris="JUR-TEST-"+UUID.randomUUID().toString().substring(0,8);
        return catalogos.crearPerfil(new DatosPerfil(def.id(),juris,"Plan territorial TEST v1","Fuente TEST, sin autoridad territorial real",
                List.of(new OpcionDefinida("Z-TEST","Denominación TEST documentada",1,true))),equipo.admin(),sesiones.de(equipo.admin()));
    }
    private static DatosDefinicion definicionPrivada(DatosDefinicion base,String clave,String rotulo) {
        return new DatosDefinicion(clave,base.sujeto(),base.tipoDato(),base.unidad(),rotulo,base.ayuda(),base.familia(),base.orden(),base.valorMinimo(),base.valorMaximo(),base.longitudMaxima(),base.jurisdiccion(),"FUENTE-TEST-DEMANDA-V1","Definición privada declarada para la conservación TEST de "+base.clave(),base.aplicaciones(),base.opciones());
    }
    private long clienteTest() {return tx().execute(t->{
        long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Cliente compartido TEST C3') returning id_persona",Long.class,org());
        long rol=jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'CLIENTE',?) returning id_persona_rol",Long.class,org(),persona,hoy());
        jdbc.update("insert into detalle_cliente(organizacion_id,id_persona_rol) values(?,?)",org(),rol);return rol;
    });}
    private FichaRequerimiento crear(DatosRequerimiento d){return demandas.crear(d,agente,sesion,clave(),PROC);}
    private DatosRequerimiento contacto(String observaciones,List<CriterioPedido> cs){return datos(null,List.of("DEPARTAMENTO"),cs,null,observaciones,Set.of());}
    private DatosRequerimiento datos(Long cliente,List<String> tipos,List<CriterioPedido> cs,List<String> zonas,String observaciones,Set<String> retirar) {
        var contacto=cliente==null?new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,new DatosContacto(null,null,null,null,"999000001",null)):null;
        return new DatosRequerimiento(cliente,null,null,null,null,null,"ALQUILER",tipos,null,null,null,null,null,null,null,observaciones,zonas,cs,contacto,null,null,null,null,retirar);
    }
    private static DatosRequerimiento edicion(String texto) {
        return new DatosRequerimiento(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,texto,null,null,null,null,null,null,null,Set.of());
    }
    private CriterioPedido numeroPedido(String operador,BigDecimal valor) {
        var d=definicion("ascensores");return new CriterioPedido("ascensores",operador,"I",null,valor,null,null,null,null,null,null,null,
                new ContextoValor(null,d.unidad(),null,null,"ALQUILER",null),new MetadatosDato(EstadoDato.CONOCIDO,null,d.referencia(),d.version(),"pedido-evidencia-test","Declaración TEST",hoy(),null,null),false,null);
    }
    private record DefinicionDato(long id,long version,String rotulo,String unidad,String tipo){String referencia(){return "CAT:D:"+id+":"+version;}}
    private DefinicionDato definicion(String clave){return jdbc.queryForObject("select id_catalogo_atributo,version_definicion,rotulo,unidad,tipo_dato from catalogo_atributo where organizacion_id is null and clave=? and activo",
            (r,n)->new DefinicionDato(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5)),clave);}
    private String criterioJson(long id,long version){return jdbc.queryForObject("select criterio_json from requerimiento_criterio where organizacion_id=? and id_requerimiento=? and version_demanda=?",String.class,org(),id,version);}
    private long versiones(long id){return numero("select count(*) from requerimiento_version where organizacion_id=? and id_requerimiento=?",org(),id);}
    private long eventos(String tipo){return numero("select count(*) from evento_dominio where organizacion_id=? and tipo=?",org(),tipo);}
    private long recibos(String tipo){return numero("select count(*) from comando_idempotente where organizacion_id=? and tipo_comando=?",org(),tipo);}
    private long numero(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    private long org(){return agente.idOrganizacion();}
    private TransactionTemplate tx(){return new TransactionTemplate(gestor);}
    private static LocalDate hoy(){return OrigenesDePrueba.hoy();}
    private static String clave(){return "demanda-c3-test-"+UUID.randomUUID();}
    private record Competencia(FichaRequerimiento resultado,RuntimeException error) { }
    private static List<Competencia> competir(List<Callable<FichaRequerimiento>> tareas) throws Exception {
        var hilos=Executors.newFixedThreadPool(tareas.size());var listos=new CountDownLatch(tareas.size());var salida=new CountDownLatch(1);
        List<Future<Competencia>> futuros=new ArrayList<>();
        try {
            for(var tarea:tareas)futuros.add(hilos.submit(()->{listos.countDown();if(!salida.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Barrera TEST agotada");try{return new Competencia(tarea.call(),null);}catch(RuntimeException e){return new Competencia(null,e);}}));
            assertTrue(listos.await(5,TimeUnit.SECONDS));salida.countDown();var r=new ArrayList<Competencia>();for(var f:futuros)r.add(f.get(15,TimeUnit.SECONDS));return r;
        }finally{salida.countDown();futuros.forEach(f->f.cancel(true));hilos.shutdownNow();assertTrue(hilos.awaitTermination(3,TimeUnit.SECONDS));}
    }
    private static String sqlState(Throwable error){for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof SQLException e)return e.getSQLState();return "SIN_SQLSTATE";}
    private static String mensajes(Throwable error){var r=new StringBuilder();for(Throwable t=error;t!=null;t=t.getCause())r.append(t.getMessage()).append(' ');return r.toString();}
}
