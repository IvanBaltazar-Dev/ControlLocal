package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.comercial.RequerimientoCliente;
import com.controllocal.domain.comercial.CriterioRequerimiento;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.RequerimientoService.*;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.demanda.NormalizadorCriteriosDemanda;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.service.colaboracion.ReferenciasConcedidasDePrueba;
import com.controllocal.service.soporte.ValorLogico;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** RF028–030/CAT-E18/20: permisos previos a ficha, fecha del hecho y resultados originales. */
@ExtendWith(MockitoExtension.class)
class RequerimientoServiceImplTest {
    private static final Actor AGENTE=new Actor(1,101,201,Actor.AGENTE);
    private static final Actor BROKER=new Actor(1,102,202,Actor.BROKER);
    private static final Actor ADMIN=new Actor(1,103,203,Actor.TENANT_ADMIN);
    // Es29UTC, pero sigue siendo28enLima: la fecha del hecho usa CoreAmerica/Lima.
    private static final Instant AHORA=Instant.parse("2026-10-29T02:15:00Z");
    private static final LocalDate HOY=LocalDate.of(2026,10,28);
    @Mock RequerimientoClienteRepository demandas;
    @Mock CriterioRequerimientoRepository criterios;
    @Mock HistoriaRequerimientoRepository historia;
    @Mock DistritoRepository distritos;
    @Mock InteresadosDeDemanda interesados;
    @Mock NormalizadorCriteriosDemanda normalizador;
    @Mock Alcances alcances;
    @Mock OrganizacionRepository organizaciones;
    @Mock ContextosService contextos;
    @Mock CuentaAccesoRepository cuentas;
    @Mock UsuarioOrganizacionRepository miembros;
    @Mock ComandoIdempotenteRepository registros;
    @Mock ComandosIdempotentes comandos;
    @Mock EventoDominioRepository eventos;
    @Mock EntityManager entidades;
    @Mock ProyeccionDemandaCompartibleRepository compartibles;
    private ObjectMapper json;
    private RequerimientoServiceImpl servicio;

    @BeforeEach void preparar() {
        json=new ObjectMapper().findAndRegisterModules();
        servicio=new RequerimientoServiceImpl(demandas,criterios,historia,distritos,interesados,
                normalizador,alcances,organizaciones,contextos,cuentas,miembros,registros,comandos,eventos,
                new Documentos(json),json,entidades,compartibles,Clock.fixed(AHORA,ZoneOffset.UTC));
    }
    @Test void gobiernoNoPrecargaNiConsultaLaFichaDeDemanda() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.buscar(501,ADMIN));
        verifyNoInteractions(demandas,criterios,historia);
    }
    @Test void brokerNoObtieneFichaCompletaAunquePuedaSupervisarElLadoComercial() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.buscar(501,BROKER));
        verifyNoInteractions(demandas,alcances,criterios,historia);
    }
    @Test void pertenenciaRevocadaNoSeConvierteEnListaVacia() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.listarPorCliente(301,AGENTE));
        verify(demandas,never()).delClienteYResponsable(anyLong(),anyLong(),anyLong());
        verifyNoInteractions(criterios,historia);
    }
    @Test void elMismoTenantNoDaFichaDeLaDemandaDeOtroResponsable() {
        activo(AGENTE);cabecera(501,299L,4,"A");
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.buscar(501,AGENTE));
        sinFichaPrivada();
    }
    @Test void antecedenteTecnicoSinResponsableNoAdquiereAutoridadPorTenerVersionCero() {
        activo(AGENTE);cabecera(501,null,0,"A");
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.buscar(501,AGENTE));
        sinFichaPrivada();
    }
    @Test void referenciaAjenaSeBuscaSoloEnElTenantActualAntesDeCualquierDatoPrivado() {
        var otro=new Actor(2,101,201,Actor.AGENTE);activo(otro);
        when(demandas.contexto(2,501)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class,()->servicio.buscar(501,otro));
        verify(demandas,never()).contexto(1,501);sinFichaPrivada();
    }
    @Test void historiaPropiaConservaSnapshotOriginalAunqueElEstadoActualYaSeaTerminal() throws Exception {
        activo(AGENTE);cabecera(501,201L,5,"F");
        var original=ficha(501,1,"A","Pedido original TEST");
        when(historia.version(1,501,1)).thenReturn(Optional.of(json.writeValueAsString(original)));
        var resultado=servicio.consultar(501,1L,null,AGENTE);
        assertEquals(1,resultado.version());assertEquals("A",resultado.estado());
        assertEquals("Pedido original TEST",resultado.observaciones());
        verify(demandas,never()).buscarFicha(anyLong(),anyLong());verifyNoInteractions(criterios,eventos);
    }
    @Test void selectorHistoricoNoAdmiteVersionYCorteSimultaneos() {
        activo(AGENTE);cabecera(501,201L,5,"A");
        assertThrows(ReglaNegocioException.class,()->servicio.consultar(501,1L,AHORA,AGENTE));
        verifyNoInteractions(historia,criterios);verify(demandas,never()).buscarFicha(anyLong(),anyLong());
    }
    @Test void editarOperacionSinCriteriosExpresosValidaLosConservadosAntesDePersistir() throws Exception {
        var preparada=edicionPreparada();
        doThrow(new ReglaNegocioException("Declara los criterios del contexto nuevo")).when(normalizador)
                .validarContextoConservado(anyList(),eq(AGENTE),eq(Set.of("D")),eq("VENTA"));
        assertThrows(ReglaNegocioException.class,()->servicio.actualizar(501,4,
                edicion("VENTA",null,null,null),AGENTE,preparada.sesion(),"edicion-operacion",Procedencia.deLaPantalla()));
        verify(normalizador).validarContextoConservado(anyList(),eq(AGENTE),eq(Set.of("D")),eq("VENTA"));
        verify(demandas,never()).saveAndFlush(any());verifyNoInteractions(historia,eventos);
        verify(criterios,never()).save(any());
    }
    @Test void editarTiposSinCriteriosNoConservaAscensoresDeEdificioComoSiAplicaranATerreno() throws Exception {
        var preparada=edicionPreparada();
        doThrow(new ReglaNegocioException("Declara los criterios del contexto nuevo")).when(normalizador)
                .validarContextoConservado(anyList(),eq(AGENTE),eq(Set.of("T")),eq("ALQUILER"));
        assertThrows(ReglaNegocioException.class,()->servicio.actualizar(501,4,
                edicion(null,List.of("TERRENO"),null,null),AGENTE,preparada.sesion(),"edicion-tipo",Procedencia.deLaPantalla()));
        verify(normalizador).validarContextoConservado(anyList(),eq(AGENTE),eq(Set.of("T")),eq("ALQUILER"));
        verify(demandas,never()).saveAndFlush(any());verifyNoInteractions(historia,eventos);verify(criterios,never()).save(any());
    }
    @Test void editarTextoSinCambiarContextoConservaCriterioLiteralYAntecedenteSinRenormalizar() throws Exception {
        var preparada=edicionPreparada();incrementarVersionAlGuardar();
        var resultado=servicio.actualizar(501,4,edicion(null,null,null,"Nota corregida TEST"),
                AGENTE,preparada.sesion(),"edicion-nota",Procedencia.deLaPantalla());
        assertEquals(5,resultado.version());assertEquals("Nota corregida TEST",resultado.observaciones());
        verifyNoInteractions(normalizador);
        var guardado=org.mockito.ArgumentCaptor.forClass(CriterioRequerimiento.class);
        verify(criterios).save(guardado.capture());assertEquals(preparada.criterioJson(),guardado.getValue().getCriterioJson());
        assertEquals(5,guardado.getValue().getVersionDemanda());
        verify(historia).anexar(eq(1L),eq(501L),eq(5L),eq(201L),eq(AHORA),anyString(),anyString());
    }
    @Test void cambioDeTiposCompatibleValidaYConservaElMismoJsonSinOtraFuente() throws Exception {
        var preparada=edicionPreparada();incrementarVersionAlGuardar();
        var resultado=servicio.actualizar(501,4,edicion(null,List.of("DEPARTAMENTO","CASA"),null,null),
                AGENTE,preparada.sesion(),"edicion-tipos-compatibles",Procedencia.deLaPantalla());
        assertEquals(Set.of("D","C"),Set.copyOf(resultado.tiposInmueble()));
        verify(normalizador).validarContextoConservado(anyList(),eq(AGENTE),eq(Set.of("D","C")),eq("ALQUILER"));
        var guardado=org.mockito.ArgumentCaptor.forClass(CriterioRequerimiento.class);
        verify(criterios).save(guardado.capture());assertEquals(preparada.criterioJson(),guardado.getValue().getCriterioJson());
    }
    @Test void criteriosExpresamenteVaciosPermitenRetirarLaRestriccionAlCambiarElTipo() throws Exception {
        var preparada=edicionPreparada();incrementarVersionAlGuardar();
        when(normalizador.normalizar(eq(List.of()),eq(AGENTE),eq(Set.of("T")),eq("ALQUILER"))).thenReturn(List.of());
        var resultado=servicio.actualizar(501,4,edicion(null,List.of("TERRENO"),List.of(),null),
                AGENTE,preparada.sesion(),"edicion-tipo-explicita",Procedencia.deLaPantalla());
        assertTrue(resultado.criterios().isEmpty());assertEquals(List.of("T"),resultado.tiposInmueble());
        verify(normalizador,never()).validarContextoConservado(anyList(),any(),anySet(),anyString());
        verify(criterios,never()).save(any());
    }
    @Test void validarFinNormalizaElMotivoSinProducirElEfecto() {
        activo(AGENTE);cabecera(501,201L,4,"A");
        var entrada=new FinDemanda(501,4,TipoFinDemanda.RETIRADA,HOY,"  Solicitud expresa TEST  ");
        var validado=servicio.validarFin(entrada,AGENTE);
        assertEquals("Solicitud expresa TEST",validado.motivo());assertEquals(HOY,validado.fechaDelHecho());
        assertEquals(TipoFinDemanda.RETIRADA,validado.tipo());assertEquals(4,validado.versionEsperada());
        sinEfectos();
    }
    @Test void fechaPasadaDelHechoSeConservaSinSustituirlaPorFechaDeRegistro() {
        activo(AGENTE);cabecera(501,201L,4,"A");
        var fecha=HOY.minusDays(7);
        var fin=servicio.validarFin(new FinDemanda(501,4,TipoFinDemanda.FINALIZADA,fecha,"Hecho ya ocurrido TEST"),AGENTE);
        assertEquals(fecha,fin.fechaDelHecho());assertEquals(TipoFinDemanda.FINALIZADA,fin.tipo());sinEfectos();
    }
    @Test void diaUtcPosteriorNoPermiteRegistrarUnFinFuturoEnLima() {
        activo(AGENTE);
        assertThrows(ReglaNegocioException.class,()->servicio.validarFin(new FinDemanda(501,4,TipoFinDemanda.RETIRADA,HOY.plusDays(1),"TEST"),AGENTE));
        verify(demandas,never()).contexto(anyLong(),anyLong());sinEfectos();
    }
    @Test void motivoAusenteNoSeFabricaConElTipoDeFin() {
        activo(AGENTE);
        assertThrows(ReglaNegocioException.class,()->servicio.validarFin(new FinDemanda(501,4,TipoFinDemanda.FINALIZADA,HOY,null),AGENTE));
        verify(demandas,never()).contexto(anyLong(),anyLong());sinEfectos();
    }
    @Test void versionDeFinObservadaObsoletaEsConflictoSinLecturaParaInventarOtra() {
        activo(AGENTE);cabecera(501,201L,5,"A");
        assertThrows(ConflictoException.class,()->servicio.validarFin(new FinDemanda(501,4,TipoFinDemanda.RETIRADA,HOY,"TEST"),AGENTE));
        sinFichaPrivada();sinEfectos();
    }
    @Test void finExistenteNoSeReabreNiSeReemplazaConOtraFinalizacion() {
        activo(AGENTE);cabecera(501,201L,5,"R");
        assertThrows(ReglaNegocioException.class,()->servicio.validarFin(new FinDemanda(501,5,TipoFinDemanda.FINALIZADA,HOY,"TEST"),AGENTE));
        sinFichaPrivada();sinEfectos();
    }
    @Test void brokerLeeSoloMetadataDelLadoSupervisadoSinClienteNiCriterios() {
        activo(BROKER);cabecera(501,201L,4,"A");when(alcances.alcanza(BROKER,201L)).thenReturn(true);
        var c=servicio.contextoDisponibilidad(501,BROKER,false);
        assertEquals(501,c.idDemanda());assertEquals(4,c.version());assertEquals(201,c.responsableId());
        assertEquals(1,c.organizacionId());assertEquals("ALQUILER",c.operacion().name());sinFichaPrivada();
    }
    @Test void brokerNoConfirmaDisponibilidadNiAdquiereElLockDeEdicionPropia() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.contextoDisponibilidad(501,BROKER,true));
        verifyNoInteractions(demandas,criterios,alcances,entidades);
    }
    @Test void brokerSinSupervisionNoLeeElRestoDeLaDemanda() {
        activo(BROKER);cabecera(501,299L,4,"A");
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.contextoDisponibilidad(501,BROKER,false));
        sinFichaPrivada();
    }
    @Test void batchDeMatchingRecibeSoloLosResponsablesAutorizadosYElCorteCoreComun() {
        activo(BROKER);when(alcances.supervisados(1,202)).thenReturn(List.of(201L));
        when(demandas.referenciasVigentes(1,List.of(201L),HOY)).thenReturn(List.of(new Cabecera(501L,201L,901L,4,"A","A",HOY.minusDays(1),HOY.plusDays(10))));
        var refs=servicio.demandasParaMatching(BROKER,AHORA);
        assertEquals(List.of(new ReferenciaDemanda(501,4,201,"A",HOY.minusDays(1),HOY.plusDays(10))),refs);
        verify(demandas).referenciasVigentes(1,List.of(201L),HOY);sinFichaPrivada();
    }
    @Test void supervisionVaciaNoConsultaDemandasDeTodoElTenantParaFiltrarDespues() {
        activo(BROKER);when(alcances.supervisados(1,202)).thenReturn(List.of());
        assertTrue(servicio.demandasParaMatching(BROKER,AHORA).isEmpty());
        verify(demandas,never()).referenciasVigentes(anyLong(),anyList(),any());sinFichaPrivada();
    }
    @Test void colaboracionNoConcedePresupuestoPrivadoPorPedirUnCampoAdicional() {
        activo(BROKER);cabeceraColaboracion(501,201,4,"A");when(alcances.alcanza(BROKER,201L)).thenReturn(true);
        assertThrows(ReglaNegocioException.class,()->servicio.proyectarCamposColaboracion(501,4,Set.of("presupuesto"),Set.of(),BROKER));
        sinFichaPrivada();
    }
    @Test void brokerNoSolicitaColaboracionUsandoDemandaDeSuAgente() {
        activo(BROKER);cabeceraColaboracion(501,201,4,"A");when(alcances.alcanza(BROKER,201L)).thenReturn(true);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.autoridadParaColaboracion(501,4,AccionColaboracion.SOLICITAR,BROKER));
        sinFichaPrivada();verifyNoInteractions(organizaciones);
    }
    @Test void proyeccionTerritorialConservaSignificadoPublicoSinFiltrarPerfilNiEvidenciaPrivados() throws Exception {
        activo(BROKER);cabeceraColaboracion(501,201,4,"A");when(alcances.alcanza(BROKER,201L)).thenReturn(true);
        var contexto=new ContextoValor(null,null,null,null,"ALQUILER",null,"JUR-TEST","CAT:P:20:3","Plan público TEST v1");
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:42:4",4L,"evidencia-privada-test","fuente-privada-test",HOY,null,null);
        var normalizado=new Criterio("criterio:zonificacion","zonificacion",Exigencia.OBLIGATORIO,null,List.of(
                new Condicion("PROPIEDAD:zonificacion",TipoDato.TEXTO,OperadorCriterio.IGUAL,
                        new ValorLogico("RDM",null,null,null,null,null),null,false,contexto,meta)));
        // Defensa adicional del mapper: incluso una fila de prueba con metadata privada no la emite.
        when(compartibles.criterios(1,501,4,Set.of("PROPIEDAD:zonificacion"))).thenReturn(List.of(json.writeValueAsString(normalizado)));
        var resultado=servicio.proyectarCamposColaboracion(501,4,Set.of("criterios"),Set.of("PROPIEDAD:zonificacion"),BROKER);
        var emitido=json.valueToTree(resultado);var alternativa=emitido.path("criterios").get(0).path("alternativas").get(0);
        assertEquals("JUR-TEST",alternativa.path("jurisdiccion").asText());assertEquals("Plan público TEST v1",alternativa.path("versionFuente").asText());
        assertEquals("RDM",alternativa.path("valor").path("texto").asText());
        assertFalse(emitido.toString().contains("CAT:P:"));assertFalse(emitido.toString().contains("CAT:D:"));
        assertFalse(emitido.toString().contains("evidencia-privada-test"));assertFalse(emitido.toString().contains("fuente-privada-test"));
        assertFalse(alternativa.has("referenciaCatalogo"));assertFalse(alternativa.has("metadatos"));
        verify(demandas,never()).buscarFicha(anyLong(),anyLong());verifyNoInteractions(criterios);
    }
    @Test void consultarReciboDeFinRestauraOriginalSinExigirDeNuevoCasNiEstadoAbierto() throws Exception {
        activo(AGENTE);cabecera(501,201L,5,"F");
        var original=new ResultadoFinDemanda(501,3,"R",TipoFinDemanda.RETIRADA,HOY.minusDays(2),"Hecho original TEST",AHORA.minusSeconds(3600),801);
        var recibo=mock(ComandoIdempotente.class);when(recibo.getResultado()).thenReturn(json.writeValueAsString(original));
        when(comandos.consultarResultado(AGENTE,"TEST-fin-original","RETIRAR_O_FINALIZAR_DEMANDA")).thenReturn(Optional.of(recibo));
        var restaurado=servicio.consultarResultadoFin("TEST-fin-original",AGENTE).orElseThrow();
        assertEquals(original,restaurado);assertEquals(3,restaurado.version());assertEquals("R",restaurado.estado());
        verify(demandas,never()).buscarFicha(anyLong(),anyLong());verify(demandas,never()).bloquear(anyLong(),anyLong());
        verifyNoInteractions(criterios,historia,eventos,entidades,cuentas,miembros);
    }
    @Test void verificadorUsaReceptorRealYDevuelveMetadataHashSinLeerClienteNiFichaAjena() throws Exception {
        var receptor=new Actor(2,111,211,Actor.AGENTE);activo(receptor);
        var campos=Set.of("criterios");var selectores=Set.of("PROPIEDAD:ascensores");
        var prueba=ReferenciasConcedidasDePrueba.demanda(1,501,201,0,campos,selectores);
        cabeceraColaboracion(501,201,5,"A");when(compartibles.criterios(1,501,5,selectores)).thenReturn(List.of(json.writeValueAsString(criterioFisico(1))));
        var verificacion=servicio.verificarCamposColaboracion(prueba,receptor);
        assertEquals(5,verificacion.versionActual());assertEquals(501,verificacion.idDemanda());assertTrue(verificacion.huellaSubset().matches("[a-f0-9]{64}"));
        assertFalse(json.writeValueAsString(verificacion).contains("alternativas"));
        verify(demandas,never()).contexto(anyLong(),anyLong());verify(demandas,never()).buscarFicha(anyLong(),anyLong());
        verifyNoInteractions(alcances,criterios,historia,interesados);
        verify(demandas,times(2)).contextoOperativoActual(2,111,211,Actor.AGENTE,HOY);
    }
    @Test void versionPrivadaNuevaNoCambiaElHashDelMismoSubsetConcedido() throws Exception {
        var receptor=new Actor(2,111,211,Actor.AGENTE);activo(receptor);var selectores=Set.of("PROPIEDAD:ascensores");
        var prueba=ReferenciasConcedidasDePrueba.demanda(1,501,201,0,Set.of("criterios"),selectores);
        var version=new AtomicReference<>(baseCompartible(5,"A"));
        when(compartibles.metadatosConcedidos(eq(1L),eq(501L),anySet(),eq(HOY))).thenAnswer(i->Optional.of(version.get()));
        when(compartibles.criterios(eq(1L),eq(501L),anyLong(),eq(selectores))).thenReturn(List.of(json.writeValueAsString(criterioFisico(1))));
        var anterior=servicio.verificarCamposColaboracion(prueba,receptor);version.set(baseCompartible(6,"A"));
        var actual=servicio.verificarCamposColaboracion(prueba,receptor);
        assertEquals(anterior.huellaSubset(),actual.huellaSubset());assertEquals(5,anterior.versionActual());assertEquals(6,actual.versionActual());
    }
    @Test void cambioFisicoMaterialProduceOtroHashSinEntregarSuValorAlReceptor() throws Exception {
        var receptor=new Actor(2,111,211,Actor.AGENTE);activo(receptor);var selectores=Set.of("PROPIEDAD:ascensores");
        var prueba=ReferenciasConcedidasDePrueba.demanda(1,501,201,0,Set.of("criterios"),selectores);cabeceraColaboracion(501,201,5,"A");
        when(compartibles.criterios(1,501,5,selectores)).thenReturn(List.of(json.writeValueAsString(criterioFisico(1))),List.of(json.writeValueAsString(criterioFisico(2))));
        assertNotEquals(servicio.verificarCamposColaboracion(prueba,receptor).huellaSubset(),servicio.verificarCamposColaboracion(prueba,receptor).huellaSubset());
    }
    @Test void retiroDelRecursoConcedidoSeDeniegaAntesDeRecuperarSuJson() {
        var receptor=new Actor(2,111,211,Actor.AGENTE);activo(receptor);cabeceraColaboracion(501,201,5,"R");
        var prueba=ReferenciasConcedidasDePrueba.demanda(1,501,201,0,Set.of("criterios"),Set.of("PROPIEDAD:ascensores"));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.verificarCamposColaboracion(prueba,receptor));
        verify(compartibles,never()).criterios(anyLong(),anyLong(),anyLong(),anySet());
    }
    @Test void falloTecnicoDelLectorReducidoNoDevuelveHashVacioNiFalsaRevocacion() {
        var receptor=new Actor(2,111,211,Actor.AGENTE);activo(receptor);
        var prueba=ReferenciasConcedidasDePrueba.demanda(1,501,201,0,Set.of("criterios"),Set.of("PROPIEDAD:ascensores"));
        when(compartibles.metadatosConcedidos(eq(1L),eq(501L),anySet(),eq(HOY))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("TEST SQL falló"));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,()->servicio.verificarCamposColaboracion(prueba,receptor));
        verify(compartibles,never()).criterios(anyLong(),anyLong(),anyLong(),anySet());
    }
    private void activo(Actor actor) {
        when(demandas.contextoOperativoActual(actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),HOY)).thenReturn(true);
    }
    private record EdicionPreparada(com.controllocal.service.actuacion.ActuacionesService.ContextoSesion sesion,String criterioJson) { }
    private EdicionPreparada edicionPreparada() throws Exception {
        activo(AGENTE);cabecera(501,201L,4,"A");
        var acceso=new ContextosService.SesionAccesoDTO("sesion-edicion-test",1,
                new ContextosService.IdentidadAutenticada(77,"test","test@example.invalid","TEST"),
                new ContextosService.Contexto("CTX",1,101,201,Actor.AGENTE,"EMPRESARIAL","TEST"),
                AHORA.minusSeconds(60),AHORA.plusSeconds(1800),new EstadoDeAcceso(null,false,false,Actor.AGENTE));
        var sesion=com.controllocal.service.actuacion.ActuacionesService.ContextoSesion.de(acceso);
        when(miembros.cuentasActivasDePersona(1,101)).thenReturn(List.of(77L));
        when(cuentas.bloquearCambioAcceso(77)).thenReturn(Optional.of(new CuentaAcceso()));
        when(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),AGENTE)).thenReturn(acceso);
        var r=new RequerimientoCliente();ReflectionTestUtils.setField(r,"id",501L);ReflectionTestUtils.setField(r,"version",4L);
        r.setOrganizacionId(1L);r.setIdRolResponsable(201L);r.setIdPersonaInteresada(901L);
        r.setInteresadoContextoJson("{\"tipo\":\"CONTACTO_NUEVO\"}");r.setOperacion("A");r.setTipos(new LinkedHashSet<>(List.of("D")));
        when(demandas.bloquear(1,501)).thenReturn(Optional.of(r));
        var c=new CriterioRequerimiento();c.setClave("ascensores");c.setRotulo("Ascensores");c.setUnidad("u");c.setTipoDatoObservado("ENTERO");
        String original=json.writeValueAsString(criterioFisico(1));c.setCriterioJson(original);
        when(criterios.deVersion(1,501,4)).thenReturn(List.of(c));
        return new EdicionPreparada(sesion,original);
    }
    private void incrementarVersionAlGuardar() {
        doAnswer(i->{RequerimientoCliente r=i.getArgument(0);ReflectionTestUtils.setField(r,"version",r.getVersion()+1);return r;})
                .when(demandas).saveAndFlush(any());
    }
    private static DatosRequerimiento edicion(String operacion,List<String> tipos,List<CriterioPedido> criterios,String nota) {
        return new DatosRequerimiento(null,null,null,null,null,null,operacion,tipos,null,null,null,null,null,
                null,null,nota,null,criterios,null,null,null,null,null,Set.of());
    }
    private void cabecera(long id,Long responsable,long version,String estado) {
        when(demandas.contexto(1,id)).thenReturn(Optional.of(new Cabecera(id,responsable,901L,version,estado,"A",null,null)));
    }
    private void cabeceraColaboracion(long id,long responsable,long version,String estado) {
        when(compartibles.metadatosConcedidos(eq(1L),eq(id),anySet(),eq(HOY))).thenReturn(Optional.of(
                new ProyeccionDemandaCompartibleRepository.Base(id,version,responsable,"A",estado,null,null,null,null,null,null)));
    }
    private static ProyeccionDemandaCompartibleRepository.Base baseCompartible(long version,String estado) {
        return new ProyeccionDemandaCompartibleRepository.Base(501,version,201,"A",estado,null,null,null,null,null,null);
    }
    private static Criterio criterioFisico(int valor) {
        return new Criterio("criterio:ascensores","ascensores",Exigencia.OBLIGATORIO,null,List.of(new Condicion("PROPIEDAD:ascensores",TipoDato.ENTERO,
                OperadorCriterio.MINIMO,ValorLogico.deNumero(java.math.BigDecimal.valueOf(valor)),null,false,new ContextoValor(null,null,null,null,"ALQUILER",null),
                new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null))));
    }
    private void sinFichaPrivada() {
        verify(demandas,never()).buscarFicha(anyLong(),anyLong());verify(demandas,never()).bloquear(anyLong(),anyLong());
        verifyNoInteractions(criterios,historia,interesados,normalizador,distritos);
    }
    private void sinEfectos() {
        verify(demandas,never()).saveAndFlush(any());verifyNoInteractions(eventos,comandos,registros,entidades,interesados,normalizador,cuentas,miembros);
    }
    private static FichaRequerimiento ficha(long id,long version,String estado,String observaciones) {
        return new FichaRequerimiento(id,null,null,"DEPARTAMENTO",null,null,null,"ALQUILER",List.of("D"),null,null,null,null,null,null,null,estado,observaciones,List.of(),List.of(),null,null,version,201L,901L,null,null,null,null,Map.of("tipo","CONTACTO_NUEVO"),null);
    }
    private record Cabecera(Long id,Long responsable,Long persona,long version,String estado,String operacion,LocalDate desde,LocalDate hasta)
            implements RequerimientoClienteRepository.ContextoDemanda {
        public Long getId(){return id;}public Long getResponsable(){return responsable;}public Long getPersonaInteresada(){return persona;}
        public long getVersion(){return version;}public String getEstado(){return estado;}public String getOperacion(){return operacion;}
        public LocalDate getVigenteDesde(){return desde;}public LocalDate getVigenteHasta(){return hasta;}
    }
}
