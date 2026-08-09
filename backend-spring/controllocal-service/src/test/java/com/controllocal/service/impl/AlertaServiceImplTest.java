package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.Prospeccion;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.AlertaRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService.DatosAlerta;
import com.controllocal.service.AlertaService.FichaAlerta;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda la campana (F6). El service es corto a proposito —la sustancia son las
 * once emisiones repartidas por el flujo—, pero tiene cuatro comportamientos
 * que hay que fijar porque son cable real y se "arreglan" con facilidad:
 *
 * <ol>
 *   <li>{@code emitir} se salta la emision <b>en silencio</b> si falta el agente
 *       o la entidad: la v1 tampoco rompe la operacion de negocio por un aviso;</li>
 *   <li>{@code atender} distingue <b>404 de false</b> mirando el tenant (D-F6-3):
 *       "no la alcanzo" y "ya estaba atendida" no son lo mismo;</li>
 *   <li>{@code INMUEBLE} y {@code CAPTACION} viajan con <b>ruta null</b> —caen en
 *       el {@code default} del switch de la v1— y su alerta se muestra sin
 *       enlace (D-F6-4). No es un olvido que tapar;</li>
 *   <li>un BROKER sin equipo ve la campana <b>vacia</b>, no un 403.</li>
 * </ol>
 */
class AlertaServiceImplTest {

    private static final long ORG = 1L;
    private static final long ROL_AGENTE = 30L;

    private final AlertaRepository alertas = mock(AlertaRepository.class);
    private final ProspeccionRepository prospecciones = mock(ProspeccionRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final Alcances alcances = mock(Alcances.class);

    private final AlertaServiceImpl service =
            new AlertaServiceImpl(alertas, prospecciones, agentes, alcances);

    private final Actor agente = new Actor(ORG, 3L, ROL_AGENTE, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");

    // ------------------------------------------------------------------
    // Emision
    // ------------------------------------------------------------------

    @Test
    void emitirSeSaltaLaEmisionEnSILENCIOSiFaltaElAgenteOLaEntidad() {
        // Un aviso que no se puede atar a nadie no debe tumbar la operacion de
        // negocio que lo dispara (misma decision que la v1).
        service.emitir(null, agente);
        service.emitir(datos(null, 5L), agente);
        service.emitir(datos(ROL_AGENTE, null), agente);
        service.emitir(datos(ROL_AGENTE, 0L), agente);
        service.emitir(datos(ROL_AGENTE, -1L), agente);

        verifyNoInteractions(alertas);
        verifyNoInteractions(agentes);
    }

    @Test
    void emitirEstampaElTenantDelACTORYDejaLaAlertaActiva() {
        when(agentes.getReferenceById(ROL_AGENTE)).thenReturn(detalleAgente("Valentina Mora"));

        service.emitir(new DatosAlerta(Alerta.SOLICITUD_REENVIADA, Alerta.MEDIA,
                "SOLICITUD_ALQUILER", 5L, ROL_AGENTE, "Revisa la solicitud."), agente);

        Alerta guardada = alertaGuardada();
        assertEquals(ORG, guardada.getOrganizacionId());
        assertEquals(Alerta.SOLICITUD_REENVIADA, guardada.getTipo());
        assertEquals(Alerta.MEDIA, guardada.getSeveridad());
        assertEquals("SOLICITUD_ALQUILER", guardada.getEntidadTipo());
        assertEquals(5L, guardada.getEntidadId());
        assertEquals(Alerta.ACTIVA, guardada.getEstado());
        assertEquals("Revisa la solicitud.", guardada.getMensaje());
    }

    // ------------------------------------------------------------------
    // Lectura y alcance
    // ------------------------------------------------------------------

    @Test
    void unBrokerSinEquipoVeLaCampanaVACIANoUn403() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of()));

        Pagina<FichaAlerta> pagina = service.listar(1, 20, broker);

        assertEquals(0, pagina.total());
        assertTrue(pagina.items().isEmpty());
        verifyNoInteractions(alertas);
    }

    @Test
    void elTamanoSeAcotaEntre1Y100YLaPaginaEs1Based() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(alertas.buscarConAgente(anyLong(), anyBoolean(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        service.listar(0, 9999, agente);
        verify(alertas).buscarConAgente(anyLong(), anyBoolean(), anyCollection(), pageable.capture());

        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(0, pageable.getValue().getPageNumber());
    }

    @Test
    void laFichaLlevaElNombreDelAgenteYLaRutaDeNavegacion() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(alertas.buscarConAgente(anyLong(), anyBoolean(), anyCollection(), any(Pageable.class)))
                .thenReturn(paginaCon(alerta("SOLICITUD_ALQUILER", 5L)));

        FichaAlerta ficha = service.listar(1, 20, agente).items().get(0);

        assertEquals(ROL_AGENTE, ficha.idAgente());
        assertEquals("Valentina Mora", ficha.agenteNombre());
        assertEquals("solicitud-detail/5", ficha.ruta());
    }

    @Test
    void INMUEBLEyCAPTACIONviajanConRutaNULLYEsoESElCable() {
        // D-F6-4: caen en el default del switch de la v1, asi que esas alertas
        // se muestran SIN enlace. Si este test empieza a fallar, alguien
        // "completo" el switch y rompio la paridad.
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(alertas.buscarConAgente(anyLong(), anyBoolean(), anyCollection(), any(Pageable.class)))
                .thenReturn(paginaCon(alerta("INMUEBLE", 8L), alerta("CAPTACION", 9L)));

        List<FichaAlerta> fichas = service.listar(1, 20, agente).items();

        assertNull(fichas.get(0).ruta());
        assertNull(fichas.get(1).ruta());
    }

    @Test
    void unaEntidadSinIdValidoTampocoTieneRuta() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(alertas.buscarConAgente(anyLong(), anyBoolean(), anyCollection(), any(Pageable.class)))
                .thenReturn(paginaCon(alerta("OPORTUNIDAD", 0L)));

        assertNull(service.listar(1, 20, agente).items().get(0).ruta());
    }

    // ------------------------------------------------------------------
    // Atender: 404 vs false (D-F6-3)
    // ------------------------------------------------------------------

    @Test
    void atenderUnaAlertaVisibleLaMarcaYDevuelveTrue() {
        Alerta activa = alerta("SOLICITUD_ALQUILER", 5L);
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(alertas.buscarVisible(eq(ORG), eq(4L), eq(false), anyCollection()))
                .thenReturn(Optional.of(activa));

        assertTrue(service.atender(4L, agente));
        assertEquals(Alerta.ATENDIDA, activa.getEstado());
        verify(alertas).save(activa);
    }

    @Test
    void unaAlertaYaAtendidaDevuelveFALSENoUn404() {
        // buscarVisible solo trae ACTIVAs, asi que una ya atendida cae al
        // orElseGet; existe dentro del tenant, luego es "false", no 404.
        Alerta yaAtendida = alerta("SOLICITUD_ALQUILER", 5L);
        yaAtendida.atender();
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(alertas.buscarVisible(eq(ORG), eq(4L), eq(false), anyCollection()))
                .thenReturn(Optional.empty());
        when(alertas.findById(4L)).thenReturn(Optional.of(yaAtendida));

        assertFalse(service.atender(4L, agente));
        verify(alertas, never()).save(any(Alerta.class));
    }

    @Test
    void unaAlertaDeOTRAOrganizacionResponde404NoFalse() {
        // La frontera de tenant se comprueba a mano aqui porque findById no la
        // lleva: sin este filtro, una alerta ajena respondería "false" y
        // filtraría su existencia.
        Alerta ajena = alerta("SOLICITUD_ALQUILER", 5L);
        ajena.setOrganizacionId(99L);
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(alertas.buscarVisible(eq(ORG), eq(4L), eq(false), anyCollection()))
                .thenReturn(Optional.empty());
        when(alertas.findById(4L)).thenReturn(Optional.of(ajena));

        assertThrows(NoEncontradoException.class, () -> service.atender(4L, agente));
    }

    @Test
    void unaAlertaInexistenteResponde404() {
        when(alcances.de(agente)).thenReturn(new Alcance(ORG, false, List.of(ROL_AGENTE)));
        when(alertas.buscarVisible(eq(ORG), eq(4L), eq(false), anyCollection()))
                .thenReturn(Optional.empty());
        when(alertas.findById(4L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.atender(4L, agente));
    }

    @Test
    void unBrokerSinEquipoNoPuedeAtenderNada() {
        when(alcances.de(broker)).thenReturn(new Alcance(ORG, false, List.of()));

        assertThrows(NoEncontradoException.class, () -> service.atender(4L, broker));
        verifyNoInteractions(alertas);
    }

    // ------------------------------------------------------------------
    // Barrido de recontacto
    // ------------------------------------------------------------------

    @Test
    void elBarridoEsDelTENANTEnteroNoDelAlcanceDeQuienConsulta() {
        // La v1 recorre TODAS las prospecciones por recontactar sin mirar quien
        // abrio la campana; lo unico que se le anade es la frontera de tenant.
        when(prospecciones.recontactables(eq(ORG), eq(true), eq(List.of(-1L)),
                any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.sincronizarRecontacto(agente);

        verify(prospecciones).recontactables(eq(ORG), eq(true), eq(List.of(-1L)),
                eq(LocalDate.now().minusDays(7)), any(Pageable.class));
        verify(alcances, never()).de(any());
    }

    @Test
    void noSeDuplicaUnaAlertaDeRecontactoYaActiva() {
        Prospeccion conAlerta = prospeccion(11L, "PRO-0001");
        Prospeccion sinAlerta = prospeccion(12L, "PRO-0002");
        when(prospecciones.recontactables(anyLong(), anyBoolean(), anyCollection(),
                any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conAlerta, sinAlerta)));
        when(alertas.existeActivaDe(ORG, "PROSPECCION", 11L, Alerta.SIN_RESPUESTA)).thenReturn(true);
        when(agentes.getReferenceById(ROL_AGENTE)).thenReturn(detalleAgente("Valentina Mora"));

        assertEquals(1, service.sincronizarRecontacto(agente));
        verify(alertas, times(1)).save(any(Alerta.class));
        assertEquals("Recontacta o evalua descartar la prospeccion PRO-0002.",
                alertaGuardada().getMensaje());
    }

    @Test
    void unaProspeccionSinAgenteSeSaltaSinRomperElBarrido() {
        Prospeccion huerfana = prospeccion(11L, "PRO-0001");
        huerfana.setAgente(null);
        when(prospecciones.recontactables(anyLong(), anyBoolean(), anyCollection(),
                any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(huerfana)));

        assertEquals(0, service.sincronizarRecontacto(agente));
        verify(alertas, never()).existeActivaDe(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    void sinCodigoDeProspeccionElMensajeCaeAlIdConAlmohadilla() {
        Prospeccion sinCodigo = prospeccion(11L, "  ");
        when(prospecciones.recontactables(anyLong(), anyBoolean(), anyCollection(),
                any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sinCodigo)));
        when(agentes.getReferenceById(ROL_AGENTE)).thenReturn(detalleAgente("Valentina Mora"));

        service.sincronizarRecontacto(agente);

        assertEquals("Recontacta o evalua descartar la prospeccion #11.",
                alertaGuardada().getMensaje());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static DatosAlerta datos(Long idRolAgente, Long entidadId) {
        return new DatosAlerta(Alerta.SIN_AVANCE, Alerta.INFO, "PROSPECCION", entidadId,
                idRolAgente, "mensaje");
    }

    private static Alerta alerta(String entidadTipo, Long entidadId) {
        Alerta alerta = new Alerta();
        alerta.setOrganizacionId(ORG);
        alerta.setTipo(Alerta.SIN_AVANCE);
        alerta.setSeveridad(Alerta.MEDIA);
        alerta.setEntidadTipo(entidadTipo);
        alerta.setEntidadId(entidadId);
        alerta.setAgente(detalleAgente("Valentina Mora"));
        alerta.setMensaje("mensaje");
        alerta.nacer();
        ReflectionTestUtils.setField(alerta, "id", 4L);
        return alerta;
    }

    private static Prospeccion prospeccion(long id, String codigo) {
        Prospeccion prospeccion = new Prospeccion();
        prospeccion.setOrganizacionId(ORG);
        prospeccion.setCodigoProspeccion(codigo);
        prospeccion.setAgente(detalleAgente("Valentina Mora"));
        new Transiciones(mock(HistorialEstadoRepository.class))
                .iniciar(prospeccion, Prospeccion.EN_SEGUIMIENTO);
        ReflectionTestUtils.setField(prospeccion, "id", id);
        return prospeccion;
    }

    private static DetalleAgente detalleAgente(String nombre) {
        Persona persona = new Persona();
        persona.setNombresORazonSocial(nombre);
        PersonaRol rol = new PersonaRol();
        rol.setPersona(persona);
        rol.setTipoRol(TipoRol.AGENTE);

        DetalleAgente detalle = new DetalleAgente();
        detalle.setOrganizacionId(ORG);
        detalle.setRol(rol);
        detalle.setCodigoAgente("AGE-001");
        ReflectionTestUtils.setField(detalle, "id", ROL_AGENTE);
        return detalle;
    }

    private static Page<Alerta> paginaCon(Alerta... alertas) {
        return new PageImpl<>(List.of(alertas));
    }

    private Alerta alertaGuardada() {
        ArgumentCaptor<Alerta> guardada = ArgumentCaptor.forClass(Alerta.class);
        verify(alertas, atLeastOnce()).save(guardada.capture());
        return guardada.getValue();
    }
}
