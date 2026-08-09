package com.controllocal.service.soporte;

import com.controllocal.domain.consentimiento.AutorizacionTratamientoEvento;
import com.controllocal.domain.consentimiento.AvisoPrivacidadVersion;
import com.controllocal.domain.consentimiento.EvidenciaAutorizacion;
import com.controllocal.persistence.repositorio.AutorizacionTratamientoEventoRepository;
import com.controllocal.persistence.repositorio.AvisoPrivacidadVersionRepository;
import com.controllocal.persistence.repositorio.EvidenciaAutorizacionRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-27 — autorizacion de datos personales: una sola vez, en el alta.
 * <p>
 * Cubre las cuatro conductas exigidas: alta autorizada, rechazo sin
 * persistencia, versionado del aviso y revocacion.
 */
class AutorizacionesTest {

    private static final long ORG = 1L;
    private static final long PERSONA = 77L;
    private static final long ROL_ACTOR = 28L;

    private final AutorizacionTratamientoEventoRepository eventos =
            mock(AutorizacionTratamientoEventoRepository.class);
    private final EvidenciaAutorizacionRepository evidencias =
            mock(EvidenciaAutorizacionRepository.class);
    private final AvisoPrivacidadVersionRepository avisos =
            mock(AvisoPrivacidadVersionRepository.class);
    private final PersonaRolRepository roles = mock(PersonaRolRepository.class);

    private final Autorizaciones autorizaciones =
            new Autorizaciones(eventos, evidencias, avisos, roles);
    private final Actor agente = new Actor(ORG, 3L, ROL_ACTOR, "AGENTE");

    @BeforeEach
    void avisoVigentePorDefecto() {
        when(avisos.findFirstByVigenteHastaIsNull())
                .thenReturn(Optional.of(aviso("1.0", OffsetDateTime.now().minusDays(30), false)));
        when(avisos.ultimoCambioMaterial()).thenReturn(Optional.empty());
        when(evidencias.save(any(EvidenciaAutorizacion.class))).thenAnswer(i -> i.getArgument(0));
        when(eventos.save(any(AutorizacionTratamientoEvento.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------
    // 1. Alta autorizada
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("alta autorizada")
    class AltaAutorizada {

        @Test
        @DisplayName("el backend rellena tenant, actor, fecha, canal, version y base; el usuario solo marco la casilla")
        void registraTodoLoAutomatico() {
            autorizaciones.registrarEnAlta(PERSONA, true, agente);

            ArgumentCaptor<AutorizacionTratamientoEvento> captor =
                    ArgumentCaptor.forClass(AutorizacionTratamientoEvento.class);
            verify(eventos).save(captor.capture());
            AutorizacionTratamientoEvento evento = captor.getValue();

            assertEquals(ORG, evento.getOrganizacionId());
            assertEquals(PERSONA, evento.getIdPersona());
            assertEquals(Autorizaciones.OPERACION_BROX, evento.getFinalidadCodigo());
            assertEquals(AutorizacionTratamientoEvento.OTORGADO, evento.getEvento());
            assertEquals(AutorizacionTratamientoEvento.BASE_CONSENTIMIENTO, evento.getBaseJuridica());
            assertEquals("1.0", evento.getVersionAviso(), "cita la version vigente del aviso");
            assertEquals(ROL_ACTOR, evento.getRegistradaPor(), "queda quien la registro");
            assertNotNull(evento.getOcurridoEn(), "la fecha la pone el servidor");
            assertNull(evento.getMotivoRevocacion());
        }

        @Test
        @DisplayName("el canal lo sella el backend: no se pregunta y la columna nunca queda vacia")
        void sellaElCanalTecnicoSinPreguntarlo() {
            autorizaciones.registrarEnAlta(PERSONA, true, agente);

            ArgumentCaptor<EvidenciaAutorizacion> captor =
                    ArgumentCaptor.forClass(EvidenciaAutorizacion.class);
            verify(evidencias).save(captor.capture());

            // La columna es NOT NULL y hoy solo hay un camino de entrada: el
            // formulario. El agente no elige nada de esto.
            assertEquals(Autorizaciones.CANAL_FORMULARIO, captor.getValue().getCanal());
            assertNotNull(captor.getValue().getTextoMostrado(), "queda el aviso que vio la persona");
        }

        @Test
        @DisplayName("una sola autorizacion cubre los cinco ambitos: no se escriben cinco filas")
        void unaSolaFilaParaLosCincoAmbitos() {
            autorizaciones.registrarEnAlta(PERSONA, true, agente);

            verify(eventos).save(any(AutorizacionTratamientoEvento.class));
        }
    }

    // ------------------------------------------------------------------
    // 2. Rechazo sin persistencia
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("rechazo sin persistencia")
    class Rechazo {

        @Test
        @DisplayName("sin la casilla marcada NO se escribe ni el evento ni la evidencia")
        void sinAutorizacionNoEscribeNada() {
            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> autorizaciones.registrarEnAlta(PERSONA, false, agente));

            assertTrue(error.getMessage().contains("autorizacion"), error.getMessage());
            // Lo importante no es el mensaje: es que no queda rastro. Una fila
            // "esta persona no autorizo" seria justo el dato prohibido.
            verify(eventos, never()).save(any());
            verify(evidencias, never()).save(any());
        }

        @Test
        @DisplayName("la casilla ausente (null) se trata como NO autorizado")
        void nullEsNoAutorizado() {
            assertThrows(ReglaNegocioException.class,
                    () -> autorizaciones.registrarEnAlta(PERSONA, null, agente));
            verify(eventos, never()).save(any());
        }

        @Test
        @DisplayName("la casilla es lo unico que puede tumbar el alta: ya no hay canal que validar")
        void laCasillaEsElUnicoMotivoDeRechazo() {
            // Antes se rechazaba tambien por canal vacio o inventado. Ese canal
            // ya no lo elige nadie, asi que no queda ninguna otra forma de que
            // la autorizacion falle por lo que el usuario escribio.
            autorizaciones.registrarEnAlta(PERSONA, true, agente);

            verify(eventos).save(any(AutorizacionTratamientoEvento.class));
            verify(evidencias).save(any(EvidenciaAutorizacion.class));
        }
    }

    // ------------------------------------------------------------------
    // 3. Versionado del aviso
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("versionado del aviso")
    class Versionado {

        @Test
        @DisplayName("sin cambios materiales, lo autorizado sigue vigente")
        void sinCambioMaterialSigueVigente() {
            daPorUltimoEvento(otorgado("1.0"));

            assertTrue(autorizaciones.estaAutorizada(PERSONA, ORG));
        }

        @Test
        @DisplayName("un cambio MATERIAL caduca lo autorizado contra versiones anteriores")
        void cambioMaterialCaducaLoAnterior() {
            OffsetDateTime hace30 = OffsetDateTime.now().minusDays(30);
            AvisoPrivacidadVersion v20 = aviso("2.0", OffsetDateTime.now().minusDays(1), true);
            when(avisos.ultimoCambioMaterial()).thenReturn(Optional.of(v20));
            when(avisos.findAll()).thenReturn(List.of(aviso("1.0", hace30, false), v20));
            daPorUltimoEvento(otorgado("1.0"));

            assertFalse(autorizaciones.estaAutorizada(PERSONA, ORG),
                    "hay que volver a pedirla: el aviso cambio de fondo");
        }

        @Test
        @DisplayName("quien ya autorizo contra la version material sigue vigente")
        void autorizadoContraLaVersionMaterialSigueVigente() {
            AvisoPrivacidadVersion v20 = aviso("2.0", OffsetDateTime.now().minusDays(1), true);
            when(avisos.ultimoCambioMaterial()).thenReturn(Optional.of(v20));
            daPorUltimoEvento(otorgado("2.0"));

            assertTrue(autorizaciones.estaAutorizada(PERSONA, ORG));
        }

        @Test
        @DisplayName("una correccion de redaccion (no material) NO molesta a nadie")
        void cambioNoMaterialNoCaducaNada() {
            // Publicar la 1.1 sin cambio material deja ultimoCambioMaterial vacio.
            when(avisos.ultimoCambioMaterial()).thenReturn(Optional.empty());
            daPorUltimoEvento(otorgado("1.0"));

            assertTrue(autorizaciones.estaAutorizada(PERSONA, ORG),
                    "un retoque de texto no puede obligar a repreguntar a toda la cartera");
        }

        @Test
        @DisplayName("sin ninguna version vigente el sistema se planta en vez de registrar a ciegas")
        void sinAvisoVigenteFalla() {
            when(avisos.findFirstByVigenteHastaIsNull()).thenReturn(Optional.empty());

            assertThrows(IllegalStateException.class,
                    () -> autorizaciones.registrarEnAlta(PERSONA, true, agente));
        }
    }

    // ------------------------------------------------------------------
    // 4. Revocacion
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("revocacion")
    class Revocacion {

        @Test
        @DisplayName("revocar AGREGA un evento; no borra ni modifica el otorgamiento")
        void revocarAgregaEventoYNoBorra() {
            daPorUltimoEvento(otorgado("1.0"));

            autorizaciones.revocar(PERSONA, "La persona lo pidio por telefono", agente);

            ArgumentCaptor<AutorizacionTratamientoEvento> captor =
                    ArgumentCaptor.forClass(AutorizacionTratamientoEvento.class);
            verify(eventos).save(captor.capture());
            AutorizacionTratamientoEvento evento = captor.getValue();

            assertEquals(AutorizacionTratamientoEvento.REVOCADO, evento.getEvento());
            assertEquals("La persona lo pidio por telefono", evento.getMotivoRevocacion());
            assertEquals(ROL_ACTOR, evento.getRegistradaPor());
            // Append-only: nada de delete ni de update sobre lo anterior.
            verify(eventos, never()).delete(any());
            verify(eventos, never()).deleteAll();
        }

        @Test
        @DisplayName("tras revocar, la autorizacion deja de estar vigente")
        void trasRevocarNoEstaVigente() {
            AutorizacionTratamientoEvento revocado = new AutorizacionTratamientoEvento();
            revocado.setEvento(AutorizacionTratamientoEvento.REVOCADO);
            revocado.setVersionAviso("1.0");
            daPorUltimoEvento(revocado);

            assertFalse(autorizaciones.estaAutorizada(PERSONA, ORG));
        }

        @Test
        @DisplayName("el motivo es obligatorio")
        void elMotivoEsObligatorio() {
            daPorUltimoEvento(otorgado("1.0"));

            assertEquals("El motivo de la revocacion es obligatorio.",
                    assertThrows(ReglaNegocioException.class,
                            () -> autorizaciones.revocar(PERSONA, "  ", agente)).getMessage());
            verify(eventos, never()).save(any());
        }

        @Test
        @DisplayName("no se puede revocar lo que nunca se autorizo")
        void noSePuedeRevocarLoQueNoExiste() {
            when(eventos.ultimoEvento(ORG, PERSONA, Autorizaciones.OPERACION_BROX))
                    .thenReturn(Optional.empty());

            assertThrows(ReglaNegocioException.class,
                    () -> autorizaciones.revocar(PERSONA, "motivo", agente));
            verify(eventos, never()).save(any());
        }

        @Test
        @DisplayName("volver a otorgar (REOTORGADO) devuelve la vigencia")
        void reotorgarDevuelveLaVigencia() {
            AutorizacionTratamientoEvento reotorgado = new AutorizacionTratamientoEvento();
            reotorgado.setEvento(AutorizacionTratamientoEvento.REOTORGADO);
            reotorgado.setVersionAviso("1.0");
            daPorUltimoEvento(reotorgado);

            assertTrue(autorizaciones.estaAutorizada(PERSONA, ORG));
        }
    }

    @Test
    @DisplayName("sin ningun evento, la persona NO esta autorizada")
    void sinEventosNoEstaAutorizada() {
        when(eventos.ultimoEvento(ORG, PERSONA, Autorizaciones.OPERACION_BROX))
                .thenReturn(Optional.empty());

        assertFalse(autorizaciones.estaAutorizada(PERSONA, ORG));
    }

    // ------------------------------------------------------------------
    // 5. Constancia para la ficha
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("constancia para la ficha")
    class Constancia {

        @Test
        @DisplayName("lo vigente sale con fecha y con el NOMBRE de quien la registro")
        void vigenteConFechaYNombre() {
            OffsetDateTime cuando = OffsetDateTime.now().minusDays(3);
            AutorizacionTratamientoEvento evento = otorgado("1.0");
            evento.setOcurridoEn(cuando);
            evento.setRegistradaPor(ROL_ACTOR);
            daPorUltimoEvento(evento);
            when(roles.nombreDelTitular(ROL_ACTOR, ORG)).thenReturn(Optional.of("Valeria Mora"));

            Autorizaciones.Constancia constancia = autorizaciones.constancia(PERSONA, ORG);

            assertEquals(Autorizaciones.Constancia.VIGENTE, constancia.estado());
            assertEquals(cuando, constancia.registradaEn());
            // La ficha muestra un nombre; la columna guarda un id de persona_rol.
            assertEquals("Valeria Mora", constancia.registradaPor());
            assertEquals("1.0", constancia.versionAviso());
            assertEquals("1.0", constancia.versionVigente());
        }

        @Test
        @DisplayName("una persona anterior a D-27 no es un error: es SIN_REGISTRO")
        void personaAnteriorADiecisieteEsSinRegistro() {
            when(eventos.ultimoEvento(ORG, PERSONA, Autorizaciones.OPERACION_BROX))
                    .thenReturn(Optional.empty());

            Autorizaciones.Constancia constancia = autorizaciones.constancia(PERSONA, ORG);

            // Las personas dadas de alta antes del bloque no tienen evento. La
            // ficha tiene que poder decirlo sin inventar una autorizacion.
            assertEquals(Autorizaciones.Constancia.SIN_REGISTRO, constancia.estado());
            assertNull(constancia.registradaEn());
            assertNull(constancia.registradaPor());
            assertEquals("1.0", constancia.versionVigente(), "la vigente sale siempre");
        }

        @Test
        @DisplayName("revocada se distingue de sin registro: hay fecha y hay actor")
        void revocadaSeDistingueDeSinRegistro() {
            AutorizacionTratamientoEvento revocado = new AutorizacionTratamientoEvento();
            revocado.setEvento(AutorizacionTratamientoEvento.REVOCADO);
            revocado.setVersionAviso("1.0");
            revocado.setOcurridoEn(OffsetDateTime.now());
            daPorUltimoEvento(revocado);

            assertEquals(Autorizaciones.Constancia.REVOCADA,
                    autorizaciones.constancia(PERSONA, ORG).estado());
        }

        @Test
        @DisplayName("un cambio MATERIAL del aviso la deja CADUCADA, no vigente")
        void cambioMaterialLaDejaCaducada() {
            OffsetDateTime antigua = OffsetDateTime.now().minusDays(90);
            when(avisos.findFirstByVigenteHastaIsNull())
                    .thenReturn(Optional.of(aviso("2.0", OffsetDateTime.now().minusDays(1), true)));
            when(avisos.ultimoCambioMaterial())
                    .thenReturn(Optional.of(aviso("2.0", OffsetDateTime.now().minusDays(1), true)));
            when(avisos.findAll()).thenReturn(List.of(aviso("1.0", antigua, false)));
            AutorizacionTratamientoEvento evento = otorgado("1.0");
            evento.setOcurridoEn(antigua);
            daPorUltimoEvento(evento);

            Autorizaciones.Constancia constancia = autorizaciones.constancia(PERSONA, ORG);

            assertEquals(Autorizaciones.Constancia.CADUCADA, constancia.estado());
            // Las dos versiones viajan: es lo que deja a la pantalla decidir si
            // el numero aporta algo. Aqui difieren, y por eso aporta.
            assertEquals("1.0", constancia.versionAviso());
            assertEquals("2.0", constancia.versionVigente());
        }

        @Test
        @DisplayName("si el rol que la registro ya no existe, el nombre queda nulo y no revienta")
        void rolDesaparecidoNoRompeLaFicha() {
            AutorizacionTratamientoEvento evento = otorgado("1.0");
            evento.setOcurridoEn(OffsetDateTime.now());
            evento.setRegistradaPor(999L);
            daPorUltimoEvento(evento);
            when(roles.nombreDelTitular(999L, ORG)).thenReturn(Optional.empty());

            Autorizaciones.Constancia constancia = autorizaciones.constancia(PERSONA, ORG);

            assertEquals(Autorizaciones.Constancia.VIGENTE, constancia.estado());
            assertNull(constancia.registradaPor());
        }

        @Test
        @DisplayName("el nombre se busca DENTRO del tenant: no se cruza con otra organizacion")
        void elNombreSeBuscaDentroDelTenant() {
            AutorizacionTratamientoEvento evento = otorgado("1.0");
            evento.setOcurridoEn(OffsetDateTime.now());
            evento.setRegistradaPor(ROL_ACTOR);
            daPorUltimoEvento(evento);
            when(roles.nombreDelTitular(ROL_ACTOR, ORG)).thenReturn(Optional.of("Valeria Mora"));

            autorizaciones.constancia(PERSONA, ORG);

            // La organizacion entra en la consulta: sin ella, un id de rol de
            // otra corredora resolveria a un nombre que no toca ver.
            verify(roles).nombreDelTitular(ROL_ACTOR, ORG);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void daPorUltimoEvento(AutorizacionTratamientoEvento evento) {
        when(eventos.ultimoEvento(ORG, PERSONA, Autorizaciones.OPERACION_BROX))
                .thenReturn(Optional.of(evento));
    }

    private static AutorizacionTratamientoEvento otorgado(String versionAviso) {
        AutorizacionTratamientoEvento evento = new AutorizacionTratamientoEvento();
        evento.setEvento(AutorizacionTratamientoEvento.OTORGADO);
        evento.setVersionAviso(versionAviso);
        return evento;
    }

    /**
     * {@code vigenteDesde} y {@code version} no tienen setter publico —el aviso
     * lo publica una migracion, no el codigo—, asi que el fixture los coloca por
     * reflexion en vez de abrir la entidad solo para los tests.
     */
    private static AvisoPrivacidadVersion aviso(String version, OffsetDateTime desde, boolean material) {
        AvisoPrivacidadVersion aviso = new AvisoPrivacidadVersion();
        aviso.setVersion(version);
        aviso.setContenido("Aviso de privacidad " + version);
        aviso.setContenidoHash("hash-" + version);
        aviso.setCambioMaterial(material);
        escribirCampo(aviso, "vigenteDesde", desde);
        return aviso;
    }

    private static void escribirCampo(Object destino, String campo, Object valor) {
        try {
            Field f = destino.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(destino, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo preparar el fixture: " + campo, e);
        }
    }
}
