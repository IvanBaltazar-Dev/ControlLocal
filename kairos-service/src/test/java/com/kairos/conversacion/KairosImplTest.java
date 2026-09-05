package com.kairos.conversacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.ClienteBrox.Capacidad;
import com.kairos.brox.ClienteBrox.CoincidenciaDeCartera;
import com.kairos.brox.ClienteBrox.CoincidenciasDeCartera;
import com.kairos.brox.ClienteBrox.EstadoCaptura;
import com.kairos.brox.ClienteBrox.OrigenDeCoincidencias;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Traza;
import com.kairos.conversacion.Kairos.Desenlace;
import com.kairos.conversacion.Kairos.Respuesta;
import com.kairos.conversacion.Kairos.Turno;
import com.kairos.herramientas.Herramientas;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                () -> kairos.turno(new Turno("API", null, "t1", "m1", "registra un depa", null,
                        false), SESION));
        assertTrue(sinConversacion.getMessage().contains("conversacion"));

        assertThrows(IllegalArgumentException.class,
                () -> kairos.turno(new Turno("API", "c1", null, "m1", "registra un depa", null,
                        false), SESION));

        verifyNoInteractions(interprete, brox);
    }

    @Test
    @DisplayName("canal, agente, modelo, conversacion, turno, mensaje y HERRAMIENTA llegan a BROX")
    void laTrazaViajaHastaBrox() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("tipoPropiedad", "DEPARTAMENTO"));
        hayCapacidades(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estado(7L, List.of("operacion")));

        kairos.turno(new Turno("WHATSAPP", "conv-9", "turno-3", "wamid-77", "registra un depa",
                null, false), SESION);

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
        assertEquals("abrir_sesion_captura", traza.getValue().herramienta(),
                "el rastro tiene que decir QUE se invoco, no solo quien lo pidio (D-K-1 §5)");
        assertTrue(traza.getValue().cabeceras().containsKey("X-Herramienta"));
    }

    @Test
    @DisplayName("ninguna llamada sale sin nombre: tambien las lecturas de contrato lo llevan")
    void ningunaLlamadaSaleSinNombre() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "OPORTUNIDAD", "idEntidad", "7", "canalContacto", "L"));
        hayCapacidades(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), anyString(), any())).thenReturn(List.of());

        kairos.turno(turno("anota la llamada de la oportunidad 7"), SESION);

        ArgumentCaptor<Traza> deLasCapacidades = ArgumentCaptor.forClass(Traza.class);
        verify(brox).capacidades(eq(SESION), deLasCapacidades.capture());
        assertEquals("consultar_capacidades", deLasCapacidades.getValue().herramienta(),
                "se pregunta en TODOS los turnos y viajaba sin nombre: una llamada que sale "
                        + "siempre es la ultima que se puede permitir salir anonima");

        ArgumentCaptor<Traza> delVocabulario = ArgumentCaptor.forClass(Traza.class);
        verify(brox).resultadosDeInteraccion(eq(SESION), anyString(), delVocabulario.capture());
        assertEquals("resultados_de_interaccion", delVocabulario.getValue().herramienta(),
                "es una LECTURA de vocabulario y viajaba firmada como registrar_interaccion: un "
                        + "rastro que llama alta a una consulta responde mal a la unica pregunta "
                        + "para la que existe");
        verify(brox, never()).registrarInteraccion(any(), any(), any());
    }

    @Test
    @DisplayName("el canal no se inventa: sin declararlo es API, no WhatsApp")
    void elCanalNoSeInventa() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(automatica(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estado(7L, List.of("direccion")));

        kairos.turno(new Turno(null, "c1", "t1", "m1", "registra un depa", null, false), SESION);

        ArgumentCaptor<Traza> traza = ArgumentCaptor.forClass(Traza.class);
        verify(brox).avanzarCaptura(any(), anyString(), any(), any(), traza.capture());
        assertEquals(Traza.CANAL_API, traza.getValue().canal(),
                "hubo una version que declaraba WHATSAPP en todos los turnos y el rastro de BROX "
                        + "quedaba diciendo que una peticion de la API venia de mensajeria");
    }

    @Test
    @DisplayName("la clave de idempotencia es el mensaje del canal, no un UUID nuevo")
    void laIdempotenciaSaleDelMensaje() {
        Traza traza = new Traza("WHATSAPP", "KAIROS", null, null, "c1", "t1", "wamid-77", "hola",
                null);
        assertEquals("wamid-77", traza.claveIdempotencia(),
                "un webhook reenviado trae el mismo identificador: es lo que impide que el mismo "
                        + "mensaje registre dos ofertas");

        Traza sinMensaje = new Traza("API", "KAIROS", null, null, "c1", "t1", null, "hola", null);
        assertEquals("c1:t1", sinMensaje.claveIdempotencia(),
                "sin mensaje se cae al turno, que tambien es unico; inventar un UUID haria que "
                        + "cada reintento trajera una clave distinta y la idempotencia no valdria");
    }

    // ==================================================================
    // Permisos y autonomia: las decide BROX
    // ==================================================================

    @Test
    @DisplayName("si BROX publica capacidades y no ofrece esta, no se intenta la llamada")
    void sinCapacidadNoSeIntenta() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(automatica(Accion.CONSULTAR_PROPIEDAD.capacidad()));

        Respuesta respuesta = kairos.turno(turno("registra un depa"), SESION);

        assertEquals(Desenlace.SIN_PERMISO, respuesta.desenlace());
        assertEquals(Kairos.CAPACIDAD_NO_DISPONIBLE, respuesta.motivo());
        verify(brox).capacidades(eq(SESION), any());
        verifyNoMoreInteractions(brox);
    }

    @Test
    @DisplayName("sin catalogo publicado manda el de KAIROS, y decide BROX con su 403")
    void sinCatalogoPublicadoMandaElCatalogoDeKairos() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        when(brox.capacidades(any(), any())).thenReturn(List.of());
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estado(7L, List.of("direccion")));

        Respuesta respuesta = kairos.turno(turno("registra un depa"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace(),
                "GET /capacidades todavia no existe en el Core: leer su ausencia como una negativa "
                        + "dejaria a KAIROS sin poder atender un solo turno");
        verify(brox).avanzarCaptura(any(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("una capacidad HUMANO no la ejecuta un agente, aunque el turno venga confirmado")
    void loQueEsHumanoNoLoHaceUnAgente() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        // El turno trae borrador, asi que la puerta es responder_captura: es la
        // capacidad que este turno invoca y por tanto la que tiene que declarar
        // BROX como HUMANO para que la prueba diga lo que dice su nombre.
        hayCapacidades(new Capacidad(Herramientas.RESPONDER_CAPTURA.nombre(), "POST /algo",
                List.of("AGENTE"), Capacidad.HUMANO));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t1", "m1", "registralo ya", 7L, true), SESION);

        assertEquals(Desenlace.SOLO_HUMANO, respuesta.desenlace());
        assertEquals(Kairos.RESERVADA_A_UNA_PERSONA, respuesta.motivo());
        verify(brox, never()).avanzarCaptura(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("abrir y responder son DOS capacidades: publicar una no concede la otra")
    void abrirNoConcedeResponder() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("dormitorios", "3"));
        hayCapacidades(automatica(Herramientas.ABRIR_SESION_CAPTURA.nombre()));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "tiene 3 dormitorios", 7L, false), SESION);

        assertEquals(Desenlace.SIN_PERMISO, respuesta.desenlace(),
                "las dos van por POST /captura y aun asi el permiso no se hereda: deducir que "
                        + "quien abre puede responder seria conceder una capacidad que nadie "
                        + "declaro, y el fallo de conceder de mas se paga con una escritura");
        assertEquals(Kairos.CAPACIDAD_NO_DISPONIBLE, respuesta.motivo());
        verify(brox, never()).avanzarCaptura(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("BROX puede EXIGIR confirmacion donde el catalogo no la exige")
    void broxPuedeExigirConfirmacionDeMas() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "OPORTUNIDAD", "idEntidad", "7", "canalContacto", "L",
                        "resultado", "CONTACTADO"));
        hayCapacidades(confirmable(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), anyString(), any()))
                .thenReturn(List.of("CONTACTADO"));

        Respuesta respuesta = kairos.turno(turno("anota la llamada"), SESION);

        assertEquals(Desenlace.PROPUESTA, respuesta.desenlace(),
                "registrar_interaccion es AVISA en el catalogo, pero la autonomia la declara BROX "
                        + "y aqui pidio confirmacion");
        verify(brox, never()).registrarInteraccion(any(), any(), any());
    }

    @Test
    @DisplayName("BROX NO puede apagar la confirmacion de lo sensible: AUTO no ejecuta un alta")
    void broxNoPuedeApagarLoSensible() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(automatica(Accion.REGISTRAR_PROPIEDAD.capacidad()),
                automatica(Herramientas.CONFIRMAR_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estadoCompleto(7L));

        Respuesta respuesta = kairos.turno(turno("registra el depa"), SESION);

        assertEquals(Desenlace.PROPUESTA, respuesta.desenlace(),
                "confirmar_captura crea propiedad, titularidad y encargos: si una capacidad AUTO "
                        + "pudiera apagar su confirmacion, un error de configuracion del Core "
                        + "bastaria para que una conversacion diera de alta inmuebles sola");
        assertEquals(Kairos.CONFIRMA_UNA_PERSONA, respuesta.motivo());
        verify(brox, never()).ejecutarCaptura(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("ejecutar el alta es OTRA capacidad: si BROX no la ofrece, no se intenta")
    void ejecutarElAltaEsOtraCapacidad() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        // Se publica responder_captura para que el turno LLEGUE a la ejecucion:
        // sin ella el rechazo seria el de la puerta y esta prueba daria verde
        // sin haber comprobado nunca lo que dice comprobar.
        hayCapacidades(automatica(Herramientas.RESPONDER_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estadoCompleto(7L));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "si, registralo", 7L, true), SESION);

        assertEquals(Desenlace.SIN_PERMISO, respuesta.desenlace(),
                "abrir una captura es TODOS y ejecutarla es AGENTE: son dos filas de la matriz");
        verify(brox).avanzarCaptura(any(), anyString(), eq(7L), any(), any());
        verify(brox, never()).ejecutarCaptura(any(), anyLong(), any(), any());
    }

    // ==================================================================
    // Lo sensible se propone; ejecutar exige un turno aparte
    // ==================================================================

    @Test
    @DisplayName("con todo completo el turno PROPONE; no ejecuta")
    void completoNoEsEjecutado() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()),
                confirmable(Herramientas.CONFIRMAR_CAPTURA.nombre()));
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
        hayCapacidades(confirmable(Herramientas.RESPONDER_CAPTURA.nombre()),
                confirmable(Herramientas.CONFIRMAR_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estadoCompleto(7L));
        when(brox.captura(any(), anyLong(), any())).thenReturn(estadoCompleto(7L));
        when(brox.ejecutarCaptura(any(), anyLong(), any(), any()))
                .thenReturn(new ClienteBrox.Ejecucion(7L, 42L, "PROP-0042", List.of(9L), false));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "si, registralo", 7L, true), SESION);

        assertEquals(Desenlace.EJECUTADO, respuesta.desenlace());
        assertEquals(42L, respuesta.resultado().ejecucion().idPropiedad());
        verify(brox).ejecutarCaptura(eq(SESION), eq(7L), eq("m2"), any());
    }

    @Test
    @DisplayName("al continuar un borrador la herramienta es responder_captura, no abrirla")
    void continuarUnBorradorEsResponder() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("dormitorios", "3"));
        hayCapacidades(automatica(Herramientas.RESPONDER_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estado(7L, List.of("direccion")));

        kairos.turno(new Turno("API", "c1", "t2", "m2", "tiene 3 dormitorios", 7L, false), SESION);

        ArgumentCaptor<Traza> traza = ArgumentCaptor.forClass(Traza.class);
        verify(brox).avanzarCaptura(any(), anyString(), eq(7L), any(), traza.capture());
        assertEquals("responder_captura", traza.getValue().herramienta(),
                "abrir y responder son la misma ruta y dos herramientas: el rastro tiene que "
                        + "poder decir cual de las dos fue");
    }

    @Test
    @DisplayName("confirmar con datos faltantes NO ejecuta: la confirmacion no completa nada")
    void confirmarNoRellena() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(confirmable(Herramientas.RESPONDER_CAPTURA.nombre()),
                confirmable(Herramientas.CONFIRMAR_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any()))
                .thenReturn(estado(7L, List.of("operacion", "titulares")));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "si, registralo", 7L, true), SESION);

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
        hayCapacidades(automatica(Accion.REGISTRAR_PROPIETARIO.capacidad()));
        when(brox.buscarPropietarios(any(), anyString(), any()))
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
        hayCapacidades(automatica(Accion.REGISTRAR_PROPIETARIO.capacidad()));

        Respuesta respuesta = kairos.turno(turno("registra al propietario Torres"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertEquals(List.of("numeroDocumento"), respuesta.falta());
        verify(brox, never()).registrarPropietario(any(), any(), any());
        verify(brox, never()).buscarPropietarios(any(), anyString(), any());
    }

    @Test
    @DisplayName("once digitos es un RUC y una persona juridica; lo dice la longitud")
    void elRucEsPersonaJuridica() {
        seEntiende(Accion.REGISTRAR_PROPIETARIO,
                Map.of("numeroDocumento", "20505060708", "nombre", "Inversiones SAC"));
        hayCapacidades(automatica(Accion.REGISTRAR_PROPIETARIO.capacidad()));
        when(brox.buscarPropietarios(any(), anyString(), any())).thenReturn(List.of());
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
        hayCapacidades(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));

        Respuesta respuesta = kairos.turno(turno("anota la llamada"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertTrue(respuesta.falta().contains("contexto"));
        verify(brox, never()).registrarInteraccion(any(), any(), any());
    }

    @Test
    @DisplayName("el id viaja con el nombre que BROX espera: idOportunidad, no idEntidad")
    void elIdViajaConElNombreDelContrato() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "OPORTUNIDAD", "idEntidad", "7", "canalContacto", "L",
                        "resultado", "CONTACTADO", "observaciones", "hablamos del precio"));
        hayCapacidades(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), eq("OPORTUNIDAD"), any()))
                .thenReturn(List.of("CONTACTADO"));
        when(brox.registrarInteraccion(any(), any(), any()))
                .thenReturn(new ClienteBrox.Interaccion(3L, "OPORTUNIDAD", "CONTACTADO", "L"));

        Respuesta respuesta = kairos.turno(turno("anota la llamada de la oportunidad 7"), SESION);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cuerpo = ArgumentCaptor.forClass(Map.class);
        verify(brox).registrarInteraccion(eq(SESION), cuerpo.capture(), any());
        assertEquals(Desenlace.EJECUTADO, respuesta.desenlace());
        assertEquals(7L, cuerpo.getValue().get("idOportunidad"),
                "el cuerpo de POST /interacciones tiene cuatro campos, uno por contexto, y "
                        + "ninguno se llama idEntidad: mandando ese nombre, BROX respondia "
                        + "«Falta el id» y ninguna interaccion de KAIROS llegaba a escribirse");
        assertFalse(cuerpo.getValue().containsKey("idEntidad"));
        assertEquals("OPORTUNIDAD", cuerpo.getValue().get("contexto"));
    }

    @Test
    @DisplayName("cada contexto tiene su campo: una prospeccion no viaja como oportunidad")
    void cadaContextoLlevaSuCampo() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "PROSPECCION", "idEntidad", "3", "canalContacto", "L",
                        "resultado", "CONTACTADO"));
        hayCapacidades(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), anyString(), any()))
                .thenReturn(List.of("CONTACTADO"));

        kairos.turno(turno("anota la llamada de la prospeccion 3"), SESION);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cuerpo = ArgumentCaptor.forClass(Map.class);
        verify(brox).registrarInteraccion(any(), cuerpo.capture(), any());
        assertEquals(3L, cuerpo.getValue().get("idProspeccion"));
        assertFalse(cuerpo.getValue().containsKey("idOportunidad"));
    }

    @Test
    @DisplayName("los resultados que se ofrecen los da BROX, no una lista de KAIROS")
    void losResultadosLosDaBrox() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "PROSPECCION", "idEntidad", "3", "canalContacto", "L"));
        hayCapacidades(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), eq("PROSPECCION"), any()))
                .thenReturn(List.of("ACEPTA_CAPTAR", "CONTACTADO", "NO_ACEPTA"));

        Respuesta respuesta = kairos.turno(turno("anota la llamada de la prospeccion 3"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertEquals(List.of("resultado"), respuesta.falta());
        assertEquals(List.of("ACEPTA_CAPTAR", "CONTACTADO", "NO_ACEPTA"), respuesta.opciones());
        verify(brox).resultadosDeInteraccion(eq(SESION), eq("PROSPECCION"), any());
    }

    @Test
    @DisplayName("un resultado que ese contexto no admite se vuelve a preguntar")
    void elResultadoDeOtroContextoNoCuela() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "PROSPECCION", "idEntidad", "3", "canalContacto", "L",
                        "resultado", "VISITA_AGENDADA"));
        hayCapacidades(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), anyString(), any()))
                .thenReturn(List.of("ACEPTA_CAPTAR", "CONTACTADO"));

        Respuesta respuesta = kairos.turno(turno("anota la llamada"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        verify(brox, never()).registrarInteraccion(any(), any(), any());
    }

    @Test
    @DisplayName("sin vocabulario publicado se dice, y lo que la frase dijo lo valida BROX")
    void sinVocabularioPublicadoDecideBrox() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "CLIENTE", "idEntidad", "9", "canalContacto", "W"));
        hayCapacidades(automatica(Accion.REGISTRAR_INTERACCION.capacidad()));
        when(brox.resultadosDeInteraccion(any(), anyString(), any())).thenReturn(List.of());

        Respuesta sinResultado = kairos.turno(turno("anota el whatsapp del cliente 9"), SESION);
        assertEquals(Desenlace.PREGUNTA, sinResultado.desenlace());
        assertEquals(Kairos.VOCABULARIO_NO_PUBLICADO, sinResultado.motivo(),
                "GET /interacciones/resultados choca con /interacciones/{id} y responde 400: la "
                        + "lista vacia es «no publicado», no «no hay resultados»");
        assertTrue(sinResultado.opciones().isEmpty(),
                "y no se rellena con una lista escrita en KAIROS");

        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("contexto", "CLIENTE", "idEntidad", "9", "canalContacto", "W",
                        "resultado", "CONTACTADO"));
        when(brox.registrarInteraccion(any(), any(), any()))
                .thenReturn(new ClienteBrox.Interaccion(4L, "CLIENTE", "CONTACTADO", "W"));

        Respuesta conResultado = kairos.turno(turno("anota el whatsapp del cliente 9"), SESION);
        assertEquals(Desenlace.EJECUTADO, conResultado.desenlace(),
                "con la allow-list sin publicar, quien decide si el resultado vale es BROX");
    }

    // ==================================================================
    // Matching: que encaja, y por que
    // ==================================================================

    @Test
    @DisplayName("buscar coincidencias sin origen no busca: no se elige el cliente por nadie")
    void buscarCoincidenciasNecesitaOrigen() {
        seEntiende(Accion.BUSCAR_COINCIDENCIAS, Map.of());
        hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));

        Respuesta respuesta = kairos.turno(turno("muestrame las coincidencias"), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertEquals(List.of("origen", "idOrigen"), respuesta.falta());
        assertEquals(List.of("CLIENTE", "CAPTACION", "PROSPECCION"), respuesta.opciones());
        verify(brox, never()).coincidencias(any(), any(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("las coincidencias llegan con su explicacion, y el puntaje no se recalcula")
    void lasCoincidenciasLleganExplicadas() {
        seEntiende(Accion.BUSCAR_COINCIDENCIAS, Map.of("origen", "CLIENTE", "idOrigen", "47"));
        hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        when(brox.coincidencias(any(), any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(unaCoincidencia(80, List.of("Distrito: Miraflores", "Renta: 2500"),
                        List.of("Area: pide 120 y tiene 90"), null));

        Respuesta respuesta = kairos.turno(turno("que le encaja al cliente 47"), SESION);

        assertEquals(Desenlace.RESPONDIDO, respuesta.desenlace());
        verify(brox).coincidencias(eq(SESION), eq(OrigenDeCoincidencias.CLIENTE), eq("47"),
                anyInt(), anyInt(), any());
        ClienteBrox.Explicacion explicacion =
                respuesta.resultado().matching().items().get(0).explicacion();
        assertEquals(80, explicacion.puntaje(), "el puntaje es el del Core, no uno propio");
        assertEquals(2, explicacion.cumplidos());
        assertEquals(3, explicacion.evaluados());
    }

    @Test
    @DisplayName("porConfirmar tolera que el Core no lo mande todavia, y lo cuenta cuando llega")
    void laExplicacionToleraElTercerCubo() {
        ClienteBrox.Explicacion hoy = ClienteBrox.Explicacion.de(75, List.of("a", "b", "c"),
                List.of("d"), null);
        assertEquals(3, hoy.cumplidos());
        assertEquals(4, hoy.evaluados());
        assertTrue(hoy.porConfirmar().isEmpty(),
                "ausente y vacio significan lo mismo: no hay criterio pendiente");

        ClienteBrox.Explicacion manana = ClienteBrox.Explicacion.de(75, List.of("a", "b", "c", "d"),
                List.of(), List.of("potencia electrica"));
        assertEquals(4, manana.cumplidos());
        assertEquals(5, manana.evaluados(),
                "«cumple 4 de 5; falta confirmar la potencia electrica»: el que falta confirmar "
                        + "cuenta entre los evaluados, o el 4 de 5 seria un 4 de 4");
        assertEquals(List.of("potencia electrica"), manana.porConfirmar());
    }

    @Test
    @DisplayName("explicar un match NO vuelve a evaluar: es la misma lectura, filtrada")
    void explicarNoVuelveAEvaluar() {
        seEntiende(Accion.EXPLICAR_MATCH,
                Map.of("origen", "CLIENTE", "idOrigen", "47", "codigo", "PROP-12"));
        hayCapacidades(automatica(Accion.EXPLICAR_MATCH.capacidad()));
        when(brox.coincidencias(any(), any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(unaCoincidencia(60, List.of("Distrito: Miraflores"),
                        List.of("Rubro: no coincide"), List.of("Potencia electrica")));

        Respuesta respuesta = kairos.turno(turno("por que encaja PROP-12 con el cliente 47"),
                SESION);

        assertEquals(Desenlace.RESPONDIDO, respuesta.desenlace());
        verify(brox, times(1)).coincidencias(any(), any(), anyString(), anyInt(), anyInt(), any());
        assertEquals(1, respuesta.resultado().explicacion().cumplidos());
        assertEquals(3, respuesta.resultado().explicacion().evaluados());
        assertEquals(List.of("Potencia electrica"), respuesta.resultado().explicacion()
                .porConfirmar());
    }

    @Test
    @DisplayName("si la coincidencia nombrada no esta, se dicen las que si y se pregunta")
    void explicarUnaQueNoEsta() {
        seEntiende(Accion.EXPLICAR_MATCH,
                Map.of("origen", "CLIENTE", "idOrigen", "47", "codigo", "PROP-99"));
        hayCapacidades(automatica(Accion.EXPLICAR_MATCH.capacidad()));
        when(brox.coincidencias(any(), any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(unaCoincidencia(60, List.of("Distrito: Miraflores"), List.of(), null));

        Respuesta respuesta = kairos.turno(turno("por que encaja PROP-99 con el cliente 47"),
                SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertEquals(Kairos.COINCIDENCIA_NO_ENCONTRADA, respuesta.motivo());
        assertEquals(List.of("PROP-12"), respuesta.opciones());
    }

    // ==================================================================
    // El foco del Inicio
    // ==================================================================

    @Test
    @DisplayName("el foco llega como lo dio BROX: ni se filtra ni se reordena")
    void elFocoNoSeReordena() {
        seEntiende(Accion.QUE_RESOLVER_HOY, Map.of());
        hayCapacidades(automatica(Accion.QUE_RESOLVER_HOY.capacidad()));
        ClienteBrox.Foco foco = new ClienteBrox.Foco("Mi actividad", 2,
                List.of(asunto("1", "SOLICITUD_POR_EVALUAR"), asunto("2", "RECONTACTO_VENCIDO")),
                7, List.of(), List.of());
        when(brox.foco(any(), any())).thenReturn(foco);

        Respuesta respuesta = kairos.turno(turno("que resuelvo hoy"), SESION);

        assertEquals(Desenlace.RESPONDIDO, respuesta.desenlace());
        assertEquals(List.of("SOLICITUD_POR_EVALUAR", "RECONTACTO_VENCIDO"),
                respuesta.resultado().foco().bandeja().stream().map(ClienteBrox.Asunto::tipo)
                        .toList(),
                "la politica de despacho es del Core; reordenar aqui daria un foco distinto del "
                        + "que esa misma persona ve al abrir la pantalla");
        assertEquals(7, respuesta.resultado().foco().totalBandeja());
    }

    @Test
    @DisplayName("nada que resolver es una respuesta, con su codigo")
    void focoVacio() {
        seEntiende(Accion.QUE_RESOLVER_HOY, Map.of());
        hayCapacidades(automatica(Accion.QUE_RESOLVER_HOY.capacidad()));
        when(brox.foco(any(), any()))
                .thenReturn(new ClienteBrox.Foco("Mi actividad", 0, List.of(), 0, List.of(),
                        List.of()));

        Respuesta respuesta = kairos.turno(turno("que resuelvo hoy"), SESION);

        assertEquals(Desenlace.RESPONDIDO, respuesta.desenlace());
        assertEquals(Kairos.NADA_QUE_RESOLVER, respuesta.motivo(),
                "«hoy no hay nada» y «no pude mirarlo» no son lo mismo");
    }

    // ==================================================================
    // No entender es una respuesta, no un fallo
    // ==================================================================

    @Test
    @DisplayName("sin accion reconocida no se llama a BROX para nada")
    void sinAccionNoSeLlamaANadie() {
        when(interprete.leer(anyString(), any(), any()))
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
        hayCapacidades(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()));
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
        when(interprete.leer(anyString(), any(), any()))
                .thenReturn(new Interprete.Lectura(accion, datos, List.of(), null));
    }

    private void hayCapacidades(Capacidad... capacidades) {
        when(brox.capacidades(any(), any())).thenReturn(List.of(capacidades));
    }

    private static Turno turno(String texto) {
        return new Turno("API", "c1", "t1", "m1", texto, null, false);
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

    private static CoincidenciasDeCartera unaCoincidencia(int puntaje, List<String> cumple,
                                                          List<String> noCumple,
                                                          List<String> porConfirmar) {
        return new CoincidenciasDeCartera("cliente:47", 1, 1, 6, List.of(
                new CoincidenciaDeCartera("PROPIEDAD", 12L, "PROP-12", "Local en Miraflores",
                        "120 m²", "Miraflores", "S/ 2500", "120", "8", puntaje, cumple, noCumple,
                        porConfirmar, 47L, null, "")));
    }

    private static ClienteBrox.Asunto asunto(String id, String tipo) {
        return new ClienteBrox.Asunto(id, tipo, "SOLICITUD", 9L, "SOL-0009", "descripcion", "ALTA",
                3, true, "solicitudes/9", "lleva 3 dias esperando");
    }
}
