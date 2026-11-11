package com.controllocal.service.impl;

import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.OportunidadService.*;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.excepcion.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** RF040/042/050: interacción/visita/negociación no fabrican una apertura o no continuidad. */
class OportunidadServiceImplTest {
    ActividadDePrueba h;OportunidadServiceImpl servicio;
    DetalleAgenteRepository agentes;MotivoNoContinuidadRepository motivos;PublicacionRepository publicaciones;
    InteresadosDeDemanda interesados;RequerimientoService demandas;CoincidenciaService matching;
    InteraccionService interacciones;VisitaService visitas;OfertaService ofertas;
    final Map<Long,Map<Long,String>> historia=new HashMap<>();
    @BeforeEach void montar(){
        h=new ActividadDePrueba();agentes=mock(DetalleAgenteRepository.class);motivos=mock(MotivoNoContinuidadRepository.class);
        publicaciones=mock(PublicacionRepository.class);interesados=mock(InteresadosDeDemanda.class);demandas=mock(RequerimientoService.class);
        matching=mock(CoincidenciaService.class);interacciones=mock(InteraccionService.class);visitas=mock(VisitaService.class);ofertas=mock(OfertaService.class);
        servicio=new OportunidadServiceImpl(h.oportunidades,motivos,h.captaciones,agentes,h.alcances,h.transiciones,mock(PlanDeConsulta.class),h.acceso,ofertas,
            h.contextos,interesados,h.encargos,demandas,matching,interacciones,visitas,h.recibosActividad,h.documentos,h.entidades,publicaciones);
        when(agentes.findById(1L)).thenReturn(Optional.of(h.detalle));
        when(interesados.validar(any(),any())).thenAnswer(i->i.getArgument(0));
        when(interesados.resolver(any(),any(),any())).thenReturn(new InteresadosDeDemanda.Interesado(h.interesado,null,"{\"idPersona\":100}"));
        when(h.oportunidades.siguienteNumero(1)).thenReturn(11L);
        when(h.oportunidades.existeAbiertaDelInteresado(anyLong(),anyLong(),anyLong())).thenAnswer(i->
            h.mesas.values().stream().anyMatch(o->o.getCaptacion().getId().equals(i.getArgument(1))&&o.getPersonaInteresada().getId().equals(i.getArgument(2))&&o.estaAbierta()));
        when(h.oportunidades.saveAndFlush(any())).thenAnswer(i->{var o=i.<OportunidadComercial>getArgument(0);
            if(o.getId()==null)ActividadDePrueba.id(o,11);else ActividadDePrueba.version(o,o.getVersion()+1);
            h.mesas.put(o.getId(),o);return o;});
        doAnswer(i->{historia.computeIfAbsent(i.getArgument(1),k->new TreeMap<>()).put(i.getArgument(2),i.getArgument(6));return null;})
            .when(h.oportunidades).historizar(anyLong(),anyLong(),anyLong(),any(),anyString(),nullable(String.class),anyString());
        when(h.oportunidades.fichaVersion(anyLong(),anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(historia.getOrDefault(i.<Long>getArgument(1),Map.of()).get(i.<Long>getArgument(2))));
    }
    InteresadosDeDemanda.Referencia contacto(){return new InteresadosDeDemanda.Referencia(InteresadosDeDemanda.TipoReferencia.CONTACTO_NUEVO,null,null,null,
        new InteresadosDeDemanda.DatosContacto(null,null,null,"Interesado TEST",null,null));}
    DatosOportunidad datos(){return new DatosOportunidad(null,20,0L,contacto(),null,null,null,null,"DIRECTO",null,null,new EvidenciaInteres("Interés declarado TEST",null));}
    FichaOportunidad abrir(){h.mesas.clear();return servicio.registrar(datos(),h.agente,h.sid,"apertura",h.procedencia);}
    NoContinuidad noContinua(){return new NoContinuidad(10,0L,"P","Motivo TEST",LocalDate.of(2026,10,30));}
    @Test void aperturaProgresivaNoFabricaClienteNiExigeEncargoAprobado(){
        var f=abrir();assertEquals(Captacion.EN_PREPARACION,h.cap.estadoActual());assertNull(f.idCliente());assertEquals(100,f.interesado().idPersona());assertEquals(OportunidadComercial.ABIERTA,f.estado());
        assertEquals("DIRECTO",f.origen());assertNotNull(f.procedencia());assertEquals(0,f.version());assertEquals(1,h.mesas.size());
    }
    @Test void aperturaSiempreEsExplicitaYLaCoincidenciaNoSeEjecutaPorDefecto(){
        abrir();verifyNoInteractions(matching);verify(interesados).resolver(any(),eq(h.agente),eq(h.procedencia));
    }
    @Test void validarNoCreaUnaPersonaNiUnaOportunidad(){
        servicio.validarApertura(datos(),h.agente);verify(interesados).validar(any(),eq(h.agente));verify(interesados,never()).resolver(any(),any(),any());verify(h.oportunidades,never()).saveAndFlush(any());
    }
    @Test void idEncargoAusenteNoSeTransformaEnUnaCaptacionFicticia(){
        var d=datos();assertThrows(ReglaNegocioException.class,()->servicio.validarApertura(new DatosOportunidad(null,0,0L,d.interesado(),null,null,null,null,"DIRECTO",null,null,d.evidenciaInteres()),h.agente));
    }
    @Test void versionAusenteNoEsCeroImplicito(){
        var d=datos();assertThrows(ReglaNegocioException.class,()->servicio.validarApertura(new DatosOportunidad(null,20,null,d.interesado(),null,null,null,null,"DIRECTO",null,null,d.evidenciaInteres()),h.agente));
    }
    @Test void evidenciaDelInteresNoSeInventa(){
        var d=datos();assertThrows(ReglaNegocioException.class,()->servicio.validarApertura(new DatosOportunidad(null,20,0L,d.interesado(),null,null,null,null,"DIRECTO",null,null,null),h.agente));
    }
    @Test void versionActualEsObservadaNoUnDefaultDelServidor(){
        ActividadDePrueba.version(h.cap,1);assertThrows(ConflictoException.class,()->servicio.validarApertura(datos(),h.agente));
    }
    @Test void niBrokerNiGobiernoSeHacenPasarPorElAgente(){
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarApertura(datos(),h.broker));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarApertura(datos(),h.gobierno));
    }
    @Test void encargoAjenoNoSeApropiaPorConocerSuId(){
        when(h.encargos.consultar(20,h.agente)).thenReturn(h.fichaEncargo(99));assertThrows(AccesoNoAutorizadoException.class,()->servicio.validarApertura(datos(),h.agente));
    }
    @Test void unaAbiertaDelMismoInteresadoYEncargoNoSeDuplica(){
        assertThrows(ConflictoException.class,()->servicio.registrar(datos(),h.agente,h.sid,"otra-apertura",h.procedencia));assertEquals(1,h.mesas.size());
    }
    @Test void guardaDeDuplicadoConservaAOriginalSinExtenderAS(){
        h.transiciones.aplicar(h.mesas.get(10L),10,OportunidadComercial.SOLICITUD_CREADA,h.agente,"Antecedente TEST");
        assertFalse(h.oportunidades.existeAbiertaDelInteresado(1,20,100));
    }
    @Test void publicacionDeOtroEncargoNoSeDeclaraComoOrigen(){
        var p=new Publicacion();p.setIdEncargo(99L);when(publicaciones.findByOrganizacionIdAndId(1,700L)).thenReturn(Optional.of(p));var d=datos();
        assertThrows(ReglaNegocioException.class,()->servicio.validarApertura(new DatosOportunidad(null,20,0L,d.interesado(),null,700L,null,null,"DIRECTO",null,null,d.evidenciaInteres()),h.agente));
    }
    @Test void unaCoincidenciaExplicitaRevalidaElOrigenSinCrearMatchNuevo(){
        var d=datos();var m=new DatosOportunidad(null,20,0L,d.interesado(),null,null,null,null,"COINCIDENCIA","consulta-test","candidato-test",d.evidenciaInteres());
        servicio.validarApertura(m,h.agente);verify(matching).verificarSeleccionParaApertura("consulta-test","candidato-test",20,null,null,h.agente);
        verifyNoMoreInteractions(matching);
    }
    @Test void mismaClaveDevuelveLaAperturaOriginalSinCrearOtraPersona(){
        var a=abrir();var b=servicio.registrar(datos(),h.agente,h.sid,"apertura",h.procedencia);assertEquals(a,b);
        verify(interesados,times(1)).resolver(any(),any(),any());assertEquals(1,h.mesas.size());
    }
    @Test void sesionRevocadaNoEntregaReciboNiCreaOtraPersona(){
        abrir();doThrow(new AccesoNoAutorizadoException()).when(h.sesiones).exigirContexto(anyString(),anyLong(),any());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.registrar(datos(),h.agente,h.sid,"apertura",h.procedencia));verify(interesados,times(1)).resolver(any(),any(),any());
    }
    @Test void mismaClaveOtroContenidoConflicta(){
        abrir();var d=datos();
        assertThrows(ConflictoException.class,()->servicio.registrar(new DatosOportunidad("OTRO",20,0L,d.interesado(),null,null,null,null,"DIRECTO",null,null,d.evidenciaInteres()),h.agente,h.sid,"apertura",h.procedencia));
    }
    @Test void noContinuidadTieneFechaMotivoVersionPropiosYNoTocaOfertas(){
        var f=servicio.noContinuidad(noContinua(),h.agente,h.sid,"no-continua",h.procedencia);assertEquals(OportunidadComercial.NO_CONTINUA,f.estado());assertEquals(LocalDate.of(2026,10,30),f.fechaNoContinuidad());
        assertEquals(1,f.version());assertNotNull(f.procedencia());verifyNoInteractions(ofertas,interacciones,visitas);
        assertEquals(2,historia.get(10L).size());
        assertEquals(OportunidadComercial.ABIERTA,h.documentos.comoObjeto(historia.get(10L).get(0L),FichaOportunidad.class).estado());
    }
    @Test void noContinuidadObsoletaNoModifica(){
        ActividadDePrueba.version(h.mesas.get(10L),1);assertThrows(ConflictoException.class,()->servicio.validarNoContinuidad(noContinua(),h.agente));
    }
    @Test void razonNoContinuidadEsGobernada(){
        assertThrows(ReglaNegocioException.class,()->servicio.validarNoContinuidad(new NoContinuidad(10,0L,"INVENTADA",null,LocalDate.of(2026,10,30)),h.agente));
    }
    @Test void motivoDeNoContinuidadNoSeTomaDeUnaVisitaORechazo(){
        servicio.validarNoContinuidad(noContinua(),h.agente);verifyNoInteractions(visitas,ofertas,interacciones);
    }
    @Test void consultaResumenNoCargaHistoriasPrivadasDeOtrasSecciones(){
        var r=servicio.consultar(10,Seccion.RESUMEN,null,h.agente);assertNull(r.antecedentes());assertNull(r.interacciones());assertNull(r.visitas());assertNull(r.acuerdo());assertNull(r.oportunidad().ofertas());
        verifyNoInteractions(ofertas,interacciones,visitas);
    }
    @Test void cadaSeccionConsumeSuProyeccionPropietariaSinReunionDuplicada(){
        when(interacciones.porOportunidad(eq(10L),any(),eq(h.agente))).thenReturn(List.of());
        var r=servicio.consultar(10,Seccion.ACTIVIDAD,null,h.agente);assertNotNull(r.interacciones());assertNull(r.visitas());verifyNoInteractions(visitas,ofertas);
    }
    @Test void negociacionIncluyeAcuerdoSoloEnSuSeccion(){
        when(ofertas.consultarAcuerdoAceptado(eq(10L),any(),eq(h.agente))).thenReturn(Optional.empty());
        var r=servicio.consultar(10,Seccion.NEGOCIACION,null,h.agente);assertNull(r.acuerdo());verify(ofertas).consultarAcuerdoAceptado(eq(10L),any(),eq(h.agente));
    }
    @Test void corteSinHistoriaNoSeReconstruyeDesdeLaFilaActual(){
        when(h.oportunidades.fichaAlCorte(anyLong(),anyLong(),any())).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class,()->servicio.consultar(10,Seccion.RESUMEN,Instant.parse("2020-01-01T00:00:00Z"),h.agente));
    }
    @Test void leerPropuestaNoExigeElCasDeUnaNuevaEscrituraNiCreaPersona(){
        ActividadDePrueba.version(h.cap,1);assertDoesNotThrow(()->servicio.exigirLecturaApertura(datos(),h.agente));
        verify(interesados).exigirLectura(any(),isNull(),eq(h.agente));verify(interesados,never()).resolver(any(),any(),any());
    }
    @Test void registroFallidoNoPersisteExito(){
        h.mesas.clear();doThrow(new IllegalStateException("DB TEST")).when(h.oportunidades).saveAndFlush(any());
        assertThrows(IllegalStateException.class,()->servicio.registrar(datos(),h.agente,h.sid,"fallo",h.procedencia));assertTrue(h.ejecutados.isEmpty());verifyNoInteractions(h.eventos);
    }
    @Test void listaCargaFichasEnLoteSinMesaPorCadaFila(){
        var pagina=new PageImpl<>(List.of(h.mesas.get(10L)),PageRequest.of(0,20),1);
        when(h.oportunidades.buscar(anyLong(),anyBoolean(),anyBoolean(),anyCollection(),isNull(),isNull(),isNull(),any())).thenReturn(pagina);
        when(h.oportunidades.buscarFichaPorIds(1,List.of(10L))).thenReturn(List.of(h.mesas.get(10L)));
        var r=servicio.listar(1,20,null,null,null,null,h.agente);assertEquals(1,r.total());assertEquals(1,r.items().size());assertNull(r.items().getFirst().ofertas());
        verify(h.oportunidades,times(1)).buscarFichaPorIds(1,List.of(10L));verifyNoInteractions(ofertas);
    }
}
