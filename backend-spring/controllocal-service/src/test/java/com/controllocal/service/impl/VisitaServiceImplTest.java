package com.controllocal.service.impl;

import com.controllocal.domain.comercial.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.VisitaService.*;
import com.controllocal.service.excepcion.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** RF043–045. Las visitas no se duplican como reuniones ni deciden no continuidad. */
class VisitaServiceImplTest {
    ActividadDePrueba h;VisitaRepository visitas;VisitaServiceImpl servicio;
    final Map<Long,Visita> filas=new HashMap<>();final Map<Long,Map<Long,String>> historia=new HashMap<>();
    @BeforeEach void montar(){
        h=new ActividadDePrueba();visitas=mock(VisitaRepository.class);
        servicio=new VisitaServiceImpl(visitas,h.alcances,h.transiciones,mock(PlanDeConsulta.class),h.acceso,h.contextos,
            h.entidades,h.documentos,h.recibosActividad);
        var v=new Visita();v.setOrganizacionId(1L);ActividadDePrueba.id(v,50);v.setOportunidad(h.mesas.get(10L));v.setAgente(h.detalle);
        v.setFechaVisita(LocalDate.of(2026,11,1));v.setHoraVisita(LocalTime.NOON);h.transiciones.iniciar(v,Visita.PROGRAMADA);filas.put(50L,v);
        when(visitas.buscarFicha(anyLong(),anyLong())).thenAnswer(i->i.<Long>getArgument(0)==1L?Optional.ofNullable(filas.get(i.<Long>getArgument(1))):Optional.empty());
        when(visitas.bloquear(anyLong(),anyLong())).thenAnswer(i->i.<Long>getArgument(0)==1L?Optional.ofNullable(filas.get(i.<Long>getArgument(1))):Optional.empty());
        when(visitas.oportunidadDe(anyLong(),anyLong())).thenAnswer(i->i.<Long>getArgument(0)==1L?Optional.ofNullable(filas.get(i.<Long>getArgument(1))).map(vv->vv.getOportunidad().getId()):Optional.empty());
        when(visitas.saveAndFlush(any())).thenAnswer(i->{var vv=i.<Visita>getArgument(0);
            if(vv.getId()==null)ActividadDePrueba.id(vv,51);else ActividadDePrueba.version(vv,vv.getVersion()+1);filas.put(vv.getId(),vv);return vv;});
        doAnswer(i->{historia.computeIfAbsent(i.getArgument(1),k->new TreeMap<>()).put(i.getArgument(3),i.getArgument(7));return null;})
            .when(visitas).historizar(anyLong(),anyLong(),anyLong(),anyLong(),any(),anyString(),nullable(String.class),anyString());
        when(visitas.fichaVersion(anyLong(),anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(historia.getOrDefault(i.<Long>getArgument(1),Map.of()).get(i.<Long>getArgument(2))));
    }
    DatosVisita datos(){return new DatosVisita(10,0L,LocalDate.of(2026,11,1),LocalTime.NOON,"Agenda TEST",
        List.of(new ParticipanteConocido("Interesado conocido TEST","Declaración TEST")),"Lugar declarado TEST");}
    DesenlaceVisita resultado(long version,Ocurrencia ocurrencia,String motivo){return new DesenlaceVisita(version,ocurrencia,LocalDate.of(2026,10,30),
        "Resultado observado TEST",null,null,null,null,null,motivo);}
    FichaVisita realizada(){return servicio.registrarResultado(50,resultado(0,Ocurrencia.REALIZADA,null),h.agente,h.sid,h.procedencia);}
    @Test void programarConservaUnaIdentidadYParticipantesDeclaradosSinAfirmarOcurrencia(){
        var r=servicio.programar(datos(),h.agente,h.sid,h.procedencia);assertEquals(Visita.PROGRAMADA,r.estado());assertNull(r.resultado());assertNull(r.fechaHecho());
        assertEquals("Lugar declarado TEST",r.lugarDeclarado());assertEquals(datos().participantes(),r.participantes());assertEquals(0,r.version());assertNotNull(r.procedencia());
        assertNotNull(r.registradoEn());verifyNoInteractions(h.eventos);assertEquals(OportunidadComercial.ABIERTA,h.mesas.get(10L).estadoActual());
    }
    @Test void programarNoRequiereClienteFicticioNiEncargoHabilitado(){
        var r=servicio.programar(datos(),h.agente,h.sid,h.procedencia);assertNull(r.idCliente());assertEquals("Interesado TEST",r.clienteNombre());assertEquals(Captacion.EN_PREPARACION,h.cap.estadoActual());
    }
    @Test void validarNoProgramaNiRegistraUnaReunion(){
        servicio.validarProgramacion(datos(),h.agente);verify(visitas,never()).saveAndFlush(any());verifyNoInteractions(h.eventos);
    }
    @Test void datosVersionFechaHoraDebenSerExplicitos(){
        assertThrows(ReglaNegocioException.class,()->servicio.validarProgramacion(null,h.agente));
        assertThrows(ReglaNegocioException.class,()->servicio.validarProgramacion(new DatosVisita(10,null,LocalDate.of(2026,11,1),LocalTime.NOON,null,List.of(),null),h.agente));
        assertThrows(ReglaNegocioException.class,()->servicio.validarProgramacion(new DatosVisita(10,0L,null,LocalTime.NOON,null,List.of(),null),h.agente));
    }
    @Test void unaVersionDeContextoViejaNoEsReinterpretada(){
        ActividadDePrueba.version(h.mesas.get(10L),1);assertThrows(ConflictoException.class,()->servicio.validarProgramacion(datos(),h.agente));
    }
    @Test void noSeProgramaParaOportunidadCerradaPorInferencia(){
        h.transiciones.aplicar(h.mesas.get(10L),10,OportunidadComercial.NO_CONTINUA,h.agente,"TEST");
        assertThrows(ReglaNegocioException.class,()->servicio.validarProgramacion(datos(),h.agente));
    }
    @Test void brokerYGobiernoNoProgramanComoSiFueranElAgente(){
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarProgramacion(datos(),h.broker));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarProgramacion(datos(),h.gobierno));
    }
    @Test void reprogramarConservaIdentidadYElAntecedente(){
        var r=servicio.reprogramar(new Reprogramacion(50,0L,LocalDate.of(2026,11,2),LocalTime.of(14,0),"Acuerdo de otra hora TEST"),h.agente,h.sid,h.procedencia);
        assertEquals(50,r.id());assertEquals(Visita.REPROGRAMADA,r.estado());assertEquals(LocalDate.of(2026,11,2),r.fechaVisita());assertEquals(1,r.version());
        assertEquals(2,historia.get(50L).size());assertEquals(Visita.PROGRAMADA,h.documentos.comoObjeto(historia.get(50L).get(0L),FichaVisita.class).estado());verifyNoInteractions(h.eventos);
    }
    @Test void laSegundaReprogramacionDelMismoCasConflicta(){
        var d=new Reprogramacion(50,0L,LocalDate.of(2026,11,2),LocalTime.NOON,"TEST");servicio.reprogramar(d,h.agente,h.sid,h.procedencia);
        assertThrows(ConflictoException.class,()->servicio.reprogramar(d,h.agente,h.sid,h.procedencia));
    }
    @Test void motivoDeReprogramacionNoSeInventa(){
        assertThrows(ReglaNegocioException.class,()->servicio.validarReprogramacion(new Reprogramacion(50,0L,LocalDate.of(2026,11,2),LocalTime.NOON,null),h.agente));
    }
    @Test void cancelarEsActoSeparadoConFechaYMotivo(){
        var r=servicio.cancelar(new Cancelacion(50,0L,"Propietario pidió cancelar TEST",LocalDate.of(2026,10,30)),h.agente,h.sid,"cancelar",h.procedencia);
        assertEquals(Visita.CANCELADA,r.estado());assertNull(r.resultado());assertEquals(LocalDate.of(2026,10,30),r.fechaHecho());assertEquals(OportunidadComercial.ABIERTA,h.mesas.get(10L).estadoActual());
        verify(h.eventos,times(1)).saveAndFlush(any());
    }
    @Test void cancelarConMismaClaveNoCreaOtroEventoNiVersion(){
        var d=new Cancelacion(50,0L,"TEST",LocalDate.of(2026,10,30));var a=servicio.cancelar(d,h.agente,h.sid,"cancelar",h.procedencia);
        assertEquals(a,servicio.cancelar(d,h.agente,h.sid,"cancelar",h.procedencia));assertEquals(1,filas.get(50L).getVersion());verify(h.eventos,times(1)).saveAndFlush(any());
    }
    @Test void cancelarConOtraEntradaMismaClaveNoHaceOtroEfecto(){
        servicio.cancelar(new Cancelacion(50,0L,"TEST",LocalDate.of(2026,10,30)),h.agente,h.sid,"cancelar",h.procedencia);
        assertThrows(ConflictoException.class,()->servicio.cancelar(new Cancelacion(50,0L,"OTRO",LocalDate.of(2026,10,30)),h.agente,h.sid,"cancelar",h.procedencia));
    }
    @Test void resultadoNoSeInfierePorLaFechaPasada(){
        filas.get(50L).setFechaVisita(LocalDate.of(2020,1,1));assertEquals(Visita.PROGRAMADA,servicio.obtener(50,h.agente).estado());assertNull(servicio.obtener(50,h.agente).resultado());
    }
    @Test void ocurrenciaRealizadaEsDeclaradaYConservaFechaHechoRegistroSeparados(){
        var r=realizada();assertEquals(Visita.REALIZADA,r.estado());assertEquals(LocalDate.of(2026,10,30),r.fechaHecho());assertNotNull(r.registradoEn());assertEquals(1,r.version());verifyNoInteractions(h.eventos);
    }
    @Test void noRealizadaConservaResultadoContextualSinFingirOpinionesDeVisita(){
        var r=servicio.registrarResultado(50,resultado(0,Ocurrencia.NO_REALIZADA,null),h.agente,h.sid,h.procedencia);
        assertEquals(Visita.NO_REALIZADA,r.estado());assertEquals("Resultado observado TEST",r.resultado());assertNull(r.nivelInteres());assertNull(r.opinionPrecio());
    }
    @Test void resultadoQueDiceNoContinuidadNoCierraLaOportunidad(){
        var d=new DesenlaceVisita(0L,Ocurrencia.REALIZADA,LocalDate.of(2026,10,30),"El interesado indicó que no continúa",null,null,null,null,null,null);
        servicio.registrarResultado(50,d,h.agente,h.sid,h.procedencia);assertEquals(OportunidadComercial.ABIERTA,h.mesas.get(10L).estadoActual());
        verify(h.oportunidades,never()).saveAndFlush(any());
    }
    @Test void opinionesDeVisitaNoRealizadaNoSeFabrican(){
        var d=new DesenlaceVisita(0L,Ocurrencia.NO_REALIZADA,LocalDate.of(2026,10,30),"TEST",null,4,null,null,null,null);
        assertThrows(ReglaNegocioException.class,()->servicio.validarResultado(50,d,h.agente));
    }
    @Test void resultadoNoUsaElCatalogoComunicativoComoUnEnumDeVisita(){
        var d=new DesenlaceVisita(0L,Ocurrencia.REALIZADA,LocalDate.of(2026,10,30),"Observación de visita con detalle contextual y más de treinta caracteres",null,4,"P","A","V",null);
        assertEquals(d.resultado(),servicio.validarResultado(50,d,h.agente).resultado());
    }
    @Test void rectificarConservaElResultadoOriginal(){
        var a=realizada();var d=new DesenlaceVisita(1L,Ocurrencia.REALIZADA,LocalDate.of(2026,10,30),"Resultado corregido TEST",null,null,null,null,null,"Se corrige declaración TEST");
        var b=servicio.registrarResultado(50,d,h.agente,h.sid,h.procedencia);assertEquals(2,b.version());assertEquals("Resultado observado TEST",servicio.obtenerVersion(50,1,h.agente).resultado());
        assertEquals("Resultado corregido TEST",b.resultado());assertEquals(a,servicio.obtenerVersion(50,1,h.agente));
    }
    @Test void rectificarUnaOcurrenciaExigeMotivoYNoReabreAgenda(){
        realizada();assertThrows(ReglaNegocioException.class,()->servicio.validarResultado(50,resultado(1,Ocurrencia.NO_REALIZADA,null),h.agente));
        var r=servicio.registrarResultado(50,resultado(1,Ocurrencia.NO_REALIZADA,"Declaración anterior equivocada TEST"),h.agente,h.sid,h.procedencia);
        assertEquals(Visita.NO_REALIZADA,r.estado());assertEquals(Visita.REALIZADA,servicio.obtenerVersion(50,1,h.agente).estado());
    }
    @Test void unaVisitaConOcurrenciaNoSeReprogramaComoSiSiguieraPendiente(){
        realizada();assertThrows(ReglaNegocioException.class,()->servicio.validarReprogramacion(new Reprogramacion(50,1L,LocalDate.of(2026,11,2),LocalTime.NOON,"TEST"),h.agente));
    }
    @Test void unaCanceladaNoSeMarcaRealizadaPorOtraPuerta(){
        servicio.cancelar(new Cancelacion(50,0L,"TEST",LocalDate.of(2026,10,30)),h.agente,h.sid,"cancelar",h.procedencia);
        assertThrows(ReglaNegocioException.class,()->servicio.validarResultado(50,resultado(1,Ocurrencia.REALIZADA,null),h.agente));
    }
    @Test void ocurrenciaFechaYVersionAusentesNoSonCerosODefaults(){
        assertThrows(ReglaNegocioException.class,()->servicio.validarResultado(50,new DesenlaceVisita(null,Ocurrencia.REALIZADA,LocalDate.of(2026,10,30),"TEST",null,null,null,null,null,null),h.agente));
        assertThrows(ReglaNegocioException.class,()->servicio.validarResultado(50,new DesenlaceVisita(0L,null,LocalDate.of(2026,10,30),"TEST",null,null,null,null,null,null),h.agente));
    }
    @Test void reciboDeCancelacionSeReautorizaConLaSesionActual(){
        var d=new Cancelacion(50,0L,"TEST",LocalDate.of(2026,10,30));servicio.cancelar(d,h.agente,h.sid,"cancelar",h.procedencia);
        doThrow(new AccesoNoAutorizadoException()).when(h.sesiones).exigirContexto(anyString(),anyLong(),any());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.cancelar(d,h.agente,h.sid,"cancelar",h.procedencia));verify(h.eventos,times(1)).saveAndFlush(any());
    }
    @Test void obtenerVersionDevuelveParticipantesYLugarOriginalesNoLosActuales(){
        var a=servicio.programar(datos(),h.agente,h.sid,h.procedencia);
        // Alteración técnica de la fila más reciente como doble de persistencia: no afirma otra API.
        filas.get(a.id()).setLugarDeclarado("Otro lugar actual TEST");
        filas.get(a.id()).setParticipantesJson(h.documentos.lista(List.of(new ParticipanteConocido("Otro participante TEST",null))));
        var original=servicio.obtenerVersion(a.id(),a.version(),h.agente);
        assertEquals(a,original);assertEquals("Lugar declarado TEST",original.lugarDeclarado());assertEquals(datos().participantes(),original.participantes());
    }
    @Test void unPadreActualPermitidoNoAutorizaSnapshotDeOtroEncargo(){
        var a=servicio.programar(datos(),h.agente,h.sid,h.procedencia);
        var json=h.documentos.comoMapa(h.documentos.objetoCanonico(a));var alterado=new LinkedHashMap<String,Object>(json);alterado.put("idCaptacion",999L);
        historia.get(a.id()).put(a.version(),h.documentos.objetoCanonico(alterado));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.obtenerVersion(a.id(),a.version(),h.agente));
    }
    @Test void quienPierdeAccesoNoRecibeSnapshotOriginal(){
        var a=servicio.programar(datos(),h.agente,h.sid,h.procedencia);
        when(h.oportunidades.contextoOperativoActual(anyLong(),anyLong(),anyLong(),anyString(),any())).thenReturn(false);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.obtenerVersion(a.id(),a.version(),h.agente));
    }
    @Test void versionHistoricaInexistenteNoSeReemplazaPorLaActual(){
        assertThrows(NoEncontradoException.class,()->servicio.obtenerVersion(50,99,h.agente));
    }
    @Test void gobiernoNoConsultaVisitasPorAdministrar(){
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.obtener(50,h.gobierno));
    }
    @Test void consultaAgendaNoDeclaraQueLasVisitasSeRealizaron(){
        when(visitas.agenda(anyLong(),anyBoolean(),anyBoolean(),anyCollection(),isNull(),isNull(),isNull(),any(),any(),any()))
            .thenReturn(new PageImpl<>(List.of(filas.get(50L)),PageRequest.of(0,20),1));
        when(visitas.buscarFichaPorIds(1,List.of(50L))).thenReturn(List.of(filas.get(50L)));
        var r=servicio.agenda(new ConsultaAgenda(LocalDate.of(2026,10,1),LocalDate.of(2026,11,30),null,null,1,20),h.agente);
        assertEquals(1,r.total());assertEquals(Visita.PROGRAMADA,r.items().getFirst().estado());verify(visitas,never()).saveAndFlush(any());
    }
}
