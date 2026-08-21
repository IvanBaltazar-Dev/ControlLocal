package com.kairos.conversacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.ClienteBrox.Capacidad;
import com.kairos.brox.ClienteBrox.EstadoCaptura;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Traza;
import com.kairos.conversacion.Kairos.Desenlace;
import com.kairos.conversacion.Kairos.Respuesta;
import com.kairos.conversacion.Kairos.Turno;
import com.kairos.interpretacion.Interprete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que el adaptador hace con lo que el interprete entendio.
 *
 * <h2>Dos dobles, y los dos importan</h2>
 * El <b>interprete</b> va simulado porque aqui no se prueba si "depa" es un
 * departamento —eso tiene sus propias pruebas— sino lo que se hace despues.
 *
 * <p>El <b>cliente de BROX</b> va simulado porque estas pruebas corren sin BROX
 * levantado, que es la mitad del argumento de haberlos separado: KAIROS se
 * desarrolla, se prueba y se despliega solo. Lo que estas pruebas comprueban es
 * que KAIROS <b>pide</b> lo correcto y no decide por su cuenta nada de lo que
 * BROX tiene que decidir.
 */
class KairosImplTest {

    private static final SesionBrox SESION = new SesionBrox("token-de-la-persona", 1L);

    /** Una capacidad que se ejecuta sola, tal como la declararia BROX. */
    private static Capacidad automatica(String nombre) {
        return new Capacidad(nombre, "GET /algo", List.of("AGENTE"), Capacidad.AUTO);
    }

    /** Una que BROX marca como "preparala y que la confirme una persona". */
    private static Capacidad confirmable(String nombre) {
        return new Capacidad(nombre, "POST /algo", List.of("AGENTE"), Capacidad.CONFIRMA);
    }

    private Interprete interprete;
    private ClienteBrox brox;
    private KairosImpl kairos;

    @BeforeEach
    void preparar() {
        interprete = mock(Interprete.class);
        brox = mock(ClienteBrox.class);
        kairos = new KairosImpl(interprete, brox, "KAIROS", "modelo-de-prueba", "1.0");
    }

    // ==================================================================
    // Trazabilidad: no se trabaja sobre lo que no se va a poder atribuir
    // ==================================================================

    @Test
    @DisplayName("un turno sin conversacion ni turno se rechaza ANTES de mirar la frase")
    void sinConversacionNoSeEmpieza() {
        IllegalArgumentException sinConversacion = assertThrows(IllegalArgumentException.class,
                () -> kairos.turno(new Turno(null, "t1", "m1", "registra un depa", null, false),
                        SESION));
        assertTrue(sinConversacion.getMessage().contains("conversacion"));

        assertThrows(IllegalArgumentException.class,
                () -> kairos.turno(new Turno("c1", null, "m1", "registra un depa", null, false),
                        SESION));

        verifyNoInteractions(interprete, brox);
    }

    @Test
    @DisplayName("canal, agente, modelo, conversacion, turno y mensaje llegan hasta BROX")
    void laTrazaViajaHastaBrox() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("tipoPropiedad", "DEPARTAMENTO"));
        hayCapacidad(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estado(7L, List.of("operacion")));

        kairos.turno(new Turno("conv-9", "turno-3", "wamid-77", "registra un depa", null, false),
                SESION);

        ArgumentCaptor<Traza> traza = ArgumentCaptor.forClass(Traza.class);
        verify(brox).avanzarCaptura(eq(SESION), anyString(), any(), any(), traza.capture());
        assertEquals("WHATSAPP", traza.getValue().canal());
        assertEquals("KAIROS", traza.getValue().agente());
        assertEquals("modelo-de-prueba", traza.getValue().modelo());
        assertEquals("1.0", traza.getValue().modeloVersion());
        assertEquals("conv-9", traza.getValue().conversacionId());
        assertEquals("turno-3", traza.getValue().turnoId());
        assertEquals("wamid-77", traza.getValue().mensajeId());
        assertEquals("registra un depa", traza.getValue().peticion());
    }

    @Test
    @DisplayName("la clave de idempotencia es el mensaje del canal, no un UUID nuevo")
    void laIdempotenciaSaleDelMensaje() {
        Traza traza = new Traza("WHATSAPP", "KAIROS", null, null, "c1", "t1", "wamid-77", "hola");
        assertEquals("wamid-77", traza.claveIdempotencia(),
                "un webhook reenviado trae el mismo identificador: es lo que impide que el mismo "
                        + "mensaje registre dos ofertas");

        Traza sinMensaje = new Traza("API", "KAIROS", null, null, "c1", "t1", null, "hola");
        assertEquals("c1:t1", sinMensaje.claveIdempotencia(),
                "sin mensaje se cae al turno, que tambien es unico; inventar un UUID haria que "
                        + "cada reintento trajera una clave distinta y la idempotencia no valdria");
    }

    // ==================================================================
    // Permisos y autonomia: las decide BROX
    // ==================================================================

    @Test
    @DisplayName("si BROX no ofrece la capacidad, no se intenta la llamada")
    void sinCapacidadNoSeIntenta() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        when(brox.capacidades(any())).thenReturn(List.of(
                automatica(Accion.CONSULTAR_PROPIEDAD.capacidad())));

        Respuesta respuesta = kairos.turno(turno("registra un depa"), SESION);

        assertEquals(Desenlace.SIN_PERMISO, respuesta.desenlace());
        assertEquals(Kairos.CAPACIDAD_NO_DISPONIBLE, respuesta.motivo());
        verify(brox).capacidades(SESION);
        verifyNoMoreInteractions(brox);
    }

    @Test
    @DisplayName("una capacidad HUMANO no la ejecuta un agente, aunque el turno venga confirmado")
    void loQueEsHumanoNoLoHaceUnAgente() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidad(new Capacidad(Accion.REGISTRAR_PROPIEDAD.capacidad(), "POST /algo",
                List.of("AGENTE"), Capacidad.HUMANO));

        Respuesta respuesta = kairos.turno(
                new Turno("c1", "t1", "m1", "registralo ya", 7L, true), SESION);

        assertEquals(Desenlace.SOLO_HUMANO, respuesta.desenlace());
        assertEquals(Kairos.RESERVADA_A_UNA_PERSONA, respuesta.motivo());
        verify(brox, never()).avanzarCaptura(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("si BROX la declara AUTO, se ejecuta sin pedir confirmacion")
    void loQueBroxDeclaraAutoSeEjecuta() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidad(automatica(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estadoCompleto(7L));
        when(brox.captura(any(), anyLong())).thenReturn(estadoCompleto(7L));
        when(brox.ejecutarCaptura(any(), anyLong(), any(), any()))
                .thenReturn(new ClienteBrox.Ejecucion(7L, 42L, "PROP-0042", List.of(9L), false));

        Respuesta respuesta = kairos.turno(turno("registra el depa"), SESION);

        assertEquals(Desenlace.EJECUTADO, respuesta.desenlace(),
                "la autonomia la declara la operacion de BROX, no una constante de KAIROS");
    }

    // ==================================================================
    // Lo que BROX marca CONFIRMA se propone, no se ejecuta
    // ==================================================================

    @Test
    @DisplayName("con todo completo el turno PROPONE; no ejecuta")
    void completoNoEsEjecutado() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidad(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estadoCompleto(7L));

        Respuesta respuesta = kairos.turno(turno("registra el depa"), SESION);

        assertEquals(Desenlace.PROPUESTA, respuesta.desenlace());
        assertEquals(Kairos.CONFIRMA_UNA_PERSONA, respuesta.motivo());
        assertTrue(respuesta.confirmaUnaPersona());
        verify(brox, never()).ejecutarCaptura(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("el turno siguiente, con confirmado, si ejecuta")
    void confirmadoEjecuta() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidad(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estadoCompleto(7L));
        when(brox.captura(any(), anyLong())).thenReturn(estadoCompleto(7L));
        when(brox.ejecutarCaptura(any(), anyLong(), any(), any()))
                .thenReturn(new ClienteBrox.Ejecucion(7L, 42L, "PROP-0042", List.of(9L), false));

        Respuesta respuesta = kairos.turno(
                new Turno("c1", "t2", "m2", "si, registralo", 7L, true), SESION);

        assertEquals(Desenlace.EJECUTADO, respuesta.desenlace());
        assertEquals(42L, respuesta.resultado().ejecucion().idPropiedad());
        verify(brox).ejecutarCaptura(eq(SESION), eq(7L), eq("m2"), any());
    }

    @Test
    @DisplayName("confirmar con datos faltantes NO ejecuta: la confirmacion no completa nada")
    void confirmarNoRellena() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidad(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estado(7L, List.of("operacion", "titulares")));

        Respuesta respuesta = kairos.turno(
                new Turno("c1", "t2", "m2", "si, registralo", 7L, true), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertEquals(List.of("operacion", "titulares"), respuesta.falta());
        verify(brox, never()).ejecutarCaptura(any(), anyLong(), any(), any());
    }

    // ==================================================================
    // Buscar antes de crear
    // ==================================================================

    @Test
    @DisplayName("un propietario que ya existe se devuelve, no se duplica")
    void noSeDuplicaUnPropietario() {
        seEntiende(Accion.REGISTRAR_PROPIETARIO,
                Map.of("numeroDocumento", "40506070", "nombre", "Torres"));
        hayCapacidad(automatica(Accion.REGISTRAR_PROPIETARIO.capacidad()));
        when(brox.buscarPropietarios(any(), anyString()))
                .thenReturn(List.of(new ClienteBrox.Persona(5L, "Ana Torres", "DNI", "40506070",
                        null)));

        Respuesta respuesta = kairos.turno(turno("registra al propietario Torres 40506070"),
                SESION);

        assertEquals(Desenlace.RESPONDIDO, respuesta.desenlace());
        assertEquals(Kairos.YA_EXISTE, respuesta.motivo());
        assertEquals(5L, respuesta.resultado().persona().id());
        verify(brox, never()).registrarPropietario(any(), any(), any());
    }

    @Test
    @DisplayName("sin documento no se crea nadie: se pregunta")
    void sinDocumentoNoSeCrea() {
        seEntiende(Accion.REGISTRAR_PROPIETARIO, Map.of("nombre", "Torres"));
        hayCapacidad(automatica(Accion.REGISTRAR_PROPIETARIO.capacidad()));

        Respuesta respuesta = kairos.turno(turno("registra al propietario Torres"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertEquals(List.of("numeroDocumento"), respuesta.falta());
        verify(brox, never()).registrarPropietario(any(), any(), any());
        verify(brox, never()).buscarPropietarios(any(), anyString());
    }

    @Test
    @DisplayName("once digitos es un RUC y una persona juridica; lo dice la longitud")
    void elRucEsPersonaJuridica() {
        seEntiende(Accion.REGISTRAR_PROPIETARIO,
                Map.of("numeroDocumento", "20505060708", "nombre", "Inversiones SAC"));
        hayCapacidad(automatica(Accion.REGISTRAR_PROPIETARIO.capacidad()));
        when(brox.buscarPropietarios(any(), anyString())).thenReturn(List.of());
        when(brox.registrarPropietario(any(), any(), any()))
                .thenReturn(new ClienteBrox.Persona(8L, "Inversiones SAC", "RUC", "20505060708",
                        null));

        kairos.turno(turno("registra a Inversiones SAC 20505060708"), SESION);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> alta = ArgumentCaptor.forClass(Map.class);
        verify(brox).registrarPropietario(eq(SESION), alta.capture(), any());
        assertEquals("RUC", alta.getValue().get("tipoDocumento"));
        assertEquals("J", alta.getValue().get("tipoPersona"));
    }

    // ==================================================================
    // La interaccion no adivina de que cuelga ni como termino
    // ==================================================================

    @Test
    @DisplayName("sin expediente del que colgar, no se registra")
    void interaccionSinExpediente() {
        seEntiende(Accion.REGISTRAR_INTERACCION, Map.of("canalContacto", "L"));
        hayCapacidad(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));

        Respuesta respuesta = kairos.turno(turno("anota la llamada"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertTrue(respuesta.falta().contains("contexto"));
        verify(brox, never()).registrarInteraccion(any(), any(), any());
    }

    @Test
    @DisplayName("los resultados que se ofrecen los da BROX, no una lista de KAIROS")
    void losResultadosLosDaBrox() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "PROSPECCION", "idEntidad", "3", "canalContacto", "L"));
        hayCapacidad(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), eq("PROSPECCION")))
                .thenReturn(List.of("ACEPTA_CAPTAR", "CONTACTADO", "NO_ACEPTA"));

        Respuesta respuesta = kairos.turno(turno("anota la llamada de la prospeccion 3"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertEquals(List.of("resultado"), respuesta.falta());
        assertEquals(List.of("ACEPTA_CAPTAR", "CONTACTADO", "NO_ACEPTA"), respuesta.opciones());
        verify(brox).resultadosDeInteraccion(SESION, "PROSPECCION");
    }

    @Test
    @DisplayName("un resultado que ese contexto no admite se vuelve a preguntar")
    void elResultadoDeOtroContextoNoCuela() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "PROSPECCION", "idEntidad", "3", "canalContacto", "L",
                        "resultado", "VISITA_AGENDADA"));
        hayCapacidad(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), anyString()))
                .thenReturn(List.of("ACEPTA_CAPTAR", "CONTACTADO"));

        Respuesta respuesta = kairos.turno(turno("anota la llamada"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        verify(brox, never()).registrarInteraccion(any(), any(), any());
    }

    // ==================================================================
    // No entender es una respuesta, no un fallo
    // ==================================================================

    @Test
    @DisplayName("sin accion reconocida no se llama a BROX para nada")
    void sinAccionNoSeLlamaANadie() {
        when(interprete.leer(anyString(), any()))
                .thenReturn(Interprete.Lectura.nada(Interprete.Lectura.SIN_ACCION));

        Respuesta respuesta = kairos.turno(turno("hola que tal"), SESION);

        assertEquals(Desenlace.NO_ENTENDIDO, respuesta.desenlace());
        assertEquals(Kairos.SIN_ACCION_RECONOCIDA, respuesta.motivo());
        verifyNoInteractions(brox);
    }

    @Test
    @DisplayName("la respuesta trae codigos y datos, nunca frases compuestas aqui")
    void laRespuestaNoTraeFrases() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("tipoPropiedad", "DEPARTAMENTO"));
        hayCapacidad(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estado(7L, List.of("operacion")));

        Respuesta respuesta = kairos.turno(turno("registra un depa"), SESION);

        assertEquals(Kairos.FALTAN_DATOS, respuesta.motivo());
        assertFalse(respuesta.motivo().contains(" "),
                "un motivo con espacios es una frase; la frase la compone quien habla, con su "
                        + "modelo y su tono, y no puede venir cocinada desde aqui");
        assertNotNull(respuesta.resultado().captura());
    }

    // ==================================================================

    private void seEntiende(Accion accion, Map<String, String> datos) {
        when(interprete.leer(anyString(), any()))
                .thenReturn(new Interprete.Lectura(accion, datos, List.of(), null));
    }

    private void hayCapacidad(Capacidad capacidad) {
        when(brox.capacidades(any())).thenReturn(List.of(capacidad));
    }

    private static Turno turno(String texto) {
        return new Turno("c1", "t1", "m1", texto, null, false);
    }

    private static EstadoCaptura estado(Long id, List<String> faltante) {
        ClienteBrox.Pregunta siguiente = faltante.isEmpty() ? null
                : new ClienteBrox.Pregunta(faltante.get(0), faltante.get(0), "COMUN", null,
                        "TEXTO", "TEXTO", null, null, "ALT", true, null, 0);
        return new EstadoCaptura(id, "CAP-00001", "REGISTRAR_PROPIEDAD", "E", Map.of(), faltante,
                siguiente, false, null);
    }

    private static EstadoCaptura estadoCompleto(Long id) {
        return new EstadoCaptura(id, "CAP-00001", "REGISTRAR_PROPIEDAD", "E", Map.of(), List.of(),
                null, true, null);
    }
}
