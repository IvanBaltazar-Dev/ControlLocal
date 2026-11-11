package com.controllocal.service.impl;

import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.ClienteService;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** RF003: contacto parcial real, sin inventar Cliente/DNI ni reutilizar Persona por tenant. */
@ExtendWith(MockitoExtension.class)
class InteresadosDeDemandaImplTest {
    private static final Actor AGENTE=new Actor(1,101,201,Actor.AGENTE);
    private static final Procedencia PROC=Procedencia.deLaPantalla();
    @Mock ClienteService lector;
    @Mock DetalleClienteRepository clientes;
    @Mock PersonaRepository personas;
    @Mock RequerimientoClienteRepository demandas;
    @Mock HistoriaRequerimientoRepository historia;
    @Mock com.controllocal.service.soporte.Alcances alcances;
    private InteresadosDeDemandaImpl servicio;
    private Documentos documentos;
    @BeforeEach void preparar(){documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
        servicio=new InteresadosDeDemandaImpl(lector,clientes,personas,demandas,documentos,historia,alcances);}

    @Test void telefonoConocidoBastaSinFabricarDocumentoNiRolCliente() {
        when(personas.saveAndFlush(any(Persona.class))).thenAnswer(i->{Persona p=i.getArgument(0);ReflectionTestUtils.setField(p,"id",901L);return p;});
        var ref=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,
                new DatosContacto(null,null,null,null,"999000001",null));
        var interesado=servicio.resolver(ref,AGENTE,PROC);
        assertNull(interesado.cliente());assertNull(interesado.persona().getNumeroDocumento());
        assertNull(interesado.persona().getTipoDocumento());assertNull(interesado.persona().getNombresORazonSocial());
        assertEquals("999000001",interesado.persona().getTelefono());assertEquals(1L,interesado.persona().getOrganizacionId());
        assertEquals("CONTACTO_NUEVO",documentos.comoMapa(interesado.contextoJson()).get("tipo"));
        verifyNoInteractions(lector,clientes,demandas);
    }
    @Test void contactoSinNingunDatoConocidoNoCreaUnaPersonaParaRellenarCampos() {
        var ref=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,new DatosContacto(null,null,null,null,null,null));
        assertThrows(ReglaNegocioException.class,()->servicio.resolver(ref,AGENTE,PROC));
        verifyNoInteractions(personas,clientes,lector,demandas);
    }
    @Test void clienteExistentePasaPrimeroPorSuLectorCanonico() {
        var persona=persona(901);var rol=new PersonaRol();rol.setPersona(persona);rol.setOrganizacionId(1L);rol.setTipoRol(TipoRol.CLIENTE);ReflectionTestUtils.setField(rol,"id",301L);
        var cliente=new DetalleCliente();cliente.setRol(rol);cliente.setOrganizacionId(1L);ReflectionTestUtils.setField(cliente,"id",301L);
        when(clientes.buscarFicha(1,301)).thenReturn(Optional.of(cliente));
        var interesado=servicio.resolver(new Referencia(TipoReferencia.CLIENTE,301L,null,null,null),AGENTE,PROC);
        assertSame(persona,interesado.persona());assertSame(cliente,interesado.cliente());
        var orden=inOrder(lector,clientes);orden.verify(lector).obtener(301,AGENTE);orden.verify(clientes).buscarFicha(1,301);
        verifyNoInteractions(personas,demandas);
    }
    @Test void rechazoDelLectorClienteNoAbreUnaConsultaDesnudaAlRepositorio() {
        when(lector.obtener(301,AGENTE)).thenThrow(new NoEncontradoException("Cliente"));
        assertThrows(NoEncontradoException.class,()->servicio.resolver(new Referencia(TipoReferencia.CLIENTE,301L,null,null,null),AGENTE,PROC));
        verifyNoInteractions(clientes,personas,demandas);
    }
    @Test void personaExistenteSeReutilizaDesdeUnaDemandaPropiaYVersionConocida() {
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(201L,4,901L)));
        var persona=persona(901);when(personas.findByOrganizacionIdAndId(1L,901L)).thenReturn(Optional.of(persona));
        var resultado=servicio.resolver(new Referencia(TipoReferencia.DEMANDA_ORIGEN,null,501L,4L,null),AGENTE,PROC);
        assertSame(persona,resultado.persona());assertNull(resultado.cliente());
        verify(personas,never()).saveAndFlush(any());verifyNoInteractions(lector,clientes);
    }
    @Test void mismaOrganizacionNoAutorizaLaDemandaDeOtroAgenteComoFuente() {
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(202L,4,901L)));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.resolver(new Referencia(TipoReferencia.DEMANDA_ORIGEN,null,501L,4L,null),AGENTE,PROC));
        verifyNoInteractions(personas,lector,clientes);
    }
    @Test void referenciaObsoletaNoSeActualizaConsultandoUnaVersionParaOcultarLaPrecondicion() {
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(201L,5,901L)));
        assertThrows(ConflictoException.class,()->servicio.resolver(new Referencia(TipoReferencia.DEMANDA_ORIGEN,null,501L,4L,null),AGENTE,PROC));
        verifyNoInteractions(personas,lector,clientes);
    }
    @Test void brokerNoRegistraContactosDesdeElPuertoInternoDeDemanda() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.resolver(new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,
                new DatosContacto(null,null,null,"Contacto TEST",null,null)),new Actor(1,102,202,Actor.BROKER),PROC));
        verifyNoInteractions(personas,lector,clientes,demandas);
    }
    @Test void lecturaDeVersionConocidaNoExigeCasDeEscrituraSiLaPersonaOriginalSigueSiendoLaMisma(){
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(201L,5,901L)));
        when(alcances.alcanza(AGENTE,201L)).thenReturn(true);
        when(historia.contactoOriginal(1,501,4)).thenReturn(Optional.of(new HistoriaRequerimientoRepository.ContactoOriginal(501L,4L,901L)));
        var r=servicio.consultarLectura(origen(),901L,AGENTE);assertTrue(r.autorizada());assertEquals(901L,r.persona());
        verify(historia,never()).version(anyLong(),anyLong(),anyLong());verifyNoInteractions(personas,clientes,lector);
    }
    @Test void cambiarPersonaNoAutorizaElSnapshotDeLaAnteriorNiMarcaUnFalloTecnico(){
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(201L,5,902L)));
        when(alcances.alcanza(AGENTE,201L)).thenReturn(true);
        when(historia.contactoOriginal(1,501,4)).thenReturn(Optional.of(new HistoriaRequerimientoRepository.ContactoOriginal(501L,4L,901L)));
        assertFalse(servicio.consultarLectura(origen(),901L,AGENTE).autorizada());
        assertThrows(NoEncontradoException.class,()->servicio.exigirLectura(origen(),901L,AGENTE));
        verifyNoInteractions(personas,clientes,lector);
    }
    @Test void personaOriginalNullNoSeRellenaConLaActualAunqueLaVersionSeaIgual(){
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(201L,4,902L)));
        when(alcances.alcanza(AGENTE,201L)).thenReturn(true);
        when(historia.contactoOriginal(1,501,4)).thenReturn(Optional.of(new HistoriaRequerimientoRepository.ContactoOriginal(501L,4L,null)));
        assertFalse(servicio.consultarLectura(origen(),null,AGENTE).autorizada());verifyNoInteractions(personas);
    }
    @Test void versionHistoricaAusenteNoSeRecreaDesdeLaFichaActual(){
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(201L,4,901L)));
        when(alcances.alcanza(AGENTE,201L)).thenReturn(true);when(historia.contactoOriginal(1,501,4)).thenReturn(Optional.empty());
        assertFalse(servicio.consultarLectura(origen(),null,AGENTE).autorizada());verifyNoInteractions(personas);
    }
    @Test void recursoFueraDelAlcanceNoPermiteNiConsultarLaMetadataHistorica(){
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(202L,4,901L)));
        assertFalse(servicio.consultarLectura(origen(),null,AGENTE).autorizada());verifyNoInteractions(historia,personas);
    }
    @Test void identidadHistoricaIncompatibleEsErrorDeIntegridadNoPoblacionVacia(){
        when(demandas.contexto(1,501)).thenReturn(Optional.of(new Cabecera(201L,4,901L)));
        when(alcances.alcanza(AGENTE,201L)).thenReturn(true);
        when(historia.contactoOriginal(1,501,4)).thenReturn(Optional.of(new HistoriaRequerimientoRepository.ContactoOriginal(999L,4L,901L)));
        assertThrows(IllegalStateException.class,()->servicio.consultarLectura(origen(),null,AGENTE));
    }
    @Test void filtrarClienteNoInvocaSuFichaPersonalNiLanzaLaDenegacionAlProxy(){
        when(lector.personaLegible(301,AGENTE)).thenReturn(Optional.empty());
        assertFalse(servicio.consultarLectura(new Referencia(TipoReferencia.CLIENTE,301L,null,null,null),null,AGENTE).autorizada());
        verify(lector,never()).obtener(anyLong(),any());verifyNoInteractions(clientes,personas);
    }
    private static Referencia origen(){return new Referencia(TipoReferencia.DEMANDA_ORIGEN,null,501L,4L,null);}
    private static Persona persona(long id){var p=new Persona();p.setOrganizacionId(1L);ReflectionTestUtils.setField(p,"id",id);return p;}
    private record Cabecera(Long responsable,long version,Long persona) implements RequerimientoClienteRepository.ContextoDemanda {
        public Long getId(){return 501L;}public Long getResponsable(){return responsable;}
        public Long getPersonaInteresada(){return persona;}public long getVersion(){return version;}
        public String getEstado(){return "A";}public String getOperacion(){return "A";}
        public LocalDate getVigenteDesde(){return null;}public LocalDate getVigenteHasta(){return null;}
    }
}
