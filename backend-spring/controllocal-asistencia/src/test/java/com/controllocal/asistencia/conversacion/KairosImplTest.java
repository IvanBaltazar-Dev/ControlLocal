package com.controllocal.asistencia.conversacion;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.ErrorBrox;
import com.controllocal.asistencia.brox.ClienteBrox.Capacidad;
import com.controllocal.asistencia.brox.ClienteBrox.CoincidenciaComercial;
import com.controllocal.asistencia.brox.ClienteBrox.CoincidenciasComerciales;
import com.controllocal.asistencia.brox.ClienteBrox.EstadoCaptura;

import com.controllocal.asistencia.brox.ClienteBrox.ReferenciaAutomatico;
import com.controllocal.asistencia.brox.ClienteBrox.ResultadoAutomatico;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.brox.Traza;
import com.controllocal.asistencia.conversacion.Kairos.Desenlace;
import com.controllocal.asistencia.conversacion.Kairos.Respuesta;
import com.controllocal.asistencia.conversacion.Kairos.Turno;
import com.controllocal.asistencia.herramientas.Herramientas;
import com.controllocal.asistencia.interpretacion.Interprete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
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
 * <p>El <b>puerto interno de BROX</b> va simulado para probar la coordinación
 * dentro de Core sin levantar su infraestructura. Lo que estas pruebas comprueban es
 * que KAIROS <b>pide</b> lo correcto y no decide por su cuenta nada de lo que
 * BROX tiene que decidir.
 */
class KairosImplTest {

    private static final SesionBrox SESION = new SesionBrox(1, 1, 1, "AGENTE", "sesion-controlada", Long.MAX_VALUE);

    /** Una capacidad que se ejecuta sola, tal como la declararia BROX. */
    private static Capacidad automatica(String nombre) {
        return descriptor(nombre, Capacidad.AUTO, "HABILITADA");
    }

    /** Una que BROX marca como "preparala y que la confirme una persona". */
    private static Capacidad confirmable(String nombre) {
        return descriptor(nombre, Capacidad.CONFIRMA, "HABILITADA");
    }

    private static Capacidad descriptor(String id, String interaccion, String estado) {
        return new Capacidad(id,"PRUEBA","LECTURA",interaccion,Map.of("type","object","required",List.of()),2,estado,null,
                new ClienteBrox.ContextoCapacidad(1,2,"AGENTE",null,null,null,null,null, null));
    }

    private Map<String,Object> interaccionC4(String tipo,long id) {
        return Map.of("vinculos",List.of(Map.of("tipo",tipo,"referencia",id,"versionEsperada",4L)),
                "interlocutor",Map.of("tipoDeclarado","Informante","nombre","Interlocutor TEST"),
                "momento",Map.of("fecha","2026-10-29"),"tipo","LLAMADA","canal","TELEFONO",
                "resultado","INTERCAMBIO_EFECTIVO");
    }
    private void prepararInteraccionC4(Map<String,Object> datos,List<String> resultados) {
        seEntiende(Accion.REGISTRAR_INTERACCION,Map.of("datosComerciales",
                com.controllocal.asistencia.interpretacion.ArgumentosComerciales.serializar(datos)));
        var esquema=Map.<String,Object>of("required",List.of("vinculos"),
                "properties",Map.of("resultado",Map.of("enum",resultados)));
        hayCapacidades(new Capacidad("registrar_interaccion","INTERACCION","ESCRITURA","AUTO",esquema,2,"HABILITADA",null,null));
    }
    private Interprete interprete;
    private ClienteBrox brox;
    private KairosImpl kairos;

    @BeforeEach
    void preparar() {
        interprete = mock(Interprete.class);
        brox = mock(ClienteBrox.class);
        when(brox.prepararCaptura(any(), anyLong(), any(), any())).thenReturn(
                new ClienteBrox.PropuestaActuacion("act-7", "confirmar_captura", 1, "huella-7",
                        Long.MAX_VALUE, Map.of("idBorrador", 7L)));
        when(brox.capacidades(any(),any())).thenReturn(Herramientas.implementadas().stream()
                .map(h -> automatica(h.nombre())).toList());
        kairos = new KairosImpl(interprete, brox, "KAIROS", "modelo-de-prueba", "1.0");
    }

    // ==================================================================
    // Trazabilidad: no se trabaja sobre lo que no se va a poder atribuir
    // ==================================================================

    @Test
    @DisplayName("un turno sin conversacion ni turno se rechaza ANTES de mirar la frase")
    void sinConversacionNoSeEmpieza() {
        IllegalArgumentException sinConversacion = assertThrows(IllegalArgumentException.class,
                () -> kairos.turno(new Turno("API", null, "t1", "m1", "registra un depa", null, null, null), SESION));
        assertTrue(sinConversacion.getMessage().contains("conversación"));

        assertThrows(IllegalArgumentException.class,
                () -> kairos.turno(new Turno("API", "c1", null, "m1", "registra un depa", null, null, null), SESION));

        verifyNoInteractions(interprete, brox);
    }

    @Test
    @DisplayName("canal, agente, modelo, conversacion, turno, mensaje y HERRAMIENTA llegan a BROX")
    void laTrazaViajaHastaBrox() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("tipoPropiedad", "DEPARTAMENTO"));
        hayCapacidades(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.ABRIR_SESION_CAPTURA.nombre(),
                        estado(7L, List.of("operacion"))));

        kairos.turno(new Turno("WHATSAPP", "conv-9", "turno-3", "wamid-77", "registra un depa", null, null, null), SESION);

        ArgumentCaptor<Traza> traza = ArgumentCaptor.forClass(Traza.class);
        verify(brox).avanzarCaptura(eq(SESION), anyString(), any(), any(), any(), traza.capture());
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
    }

    @Test
    @DisplayName("ninguna llamada sale sin nombre: tambien las lecturas de contrato lo llevan")
    void ningunaLlamadaSaleSinNombre() {
        prepararInteraccionC4(Map.of("canal","TELEFONO"),List.of("INTERCAMBIO_EFECTIVO"));
        kairos.turno(turno("anota llamada"),SESION);
        ArgumentCaptor<Traza> t=ArgumentCaptor.forClass(Traza.class);
        verify(brox).capacidades(eq(SESION),t.capture());assertEquals("consultar_capacidades",t.getValue().herramienta());
        verify(brox,never()).operarComercial(any(),any(),any(),any());
    }

    @Test
    @DisplayName("el canal no se inventa: sin declararlo es API, no WhatsApp")
    void elCanalNoSeInventa() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(automatica(Accion.REGISTRAR_PROPIEDAD.capacidad()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.ABRIR_SESION_CAPTURA.nombre(),
                        estado(7L, List.of("direccion"))));

        kairos.turno(new Turno(null, "c1", "t1", "m1", "registra un depa", null, null, null), SESION);

        ArgumentCaptor<Traza> traza = ArgumentCaptor.forClass(Traza.class);
        verify(brox).avanzarCaptura(any(), anyString(), any(), any(), any(), traza.capture());
        assertEquals(Traza.CANAL_API, traza.getValue().canal(),
                "hubo una version que declaraba WHATSAPP en todos los turnos y el rastro de BROX "
                        + "quedaba diciendo que una peticion de la API venia de mensajeria");
    }

    @Test
    @DisplayName("la clave de idempotencia es el mensaje del canal, no un UUID nuevo")
    void laConsultaConservaLaReferenciaOriginalYNoVuelveARegistrar() {
        when(brox.consultarActuacion(any(), eq("original"), any())).thenReturn(
                new ClienteBrox.ResultadoActuacion("original", "EXITO", null,
                        Map.of("idPropiedad", 42L), null, null));
        var r = kairos.turno(new Turno("API", "c1", "t1", "m1", null, null, "original", null), SESION);
        assertEquals(Desenlace.RESPONDIDO, r.desenlace());
        assertEquals(42L, r.resultado().actuacion().resultado().get("idPropiedad"));
        verify(brox).consultarActuacion(eq(SESION), eq("original"), any());
        verifyNoMoreInteractions(brox);
        verifyNoInteractions(interprete);
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

        assertEquals(Desenlace.CAPACIDAD_DESHABILITADA, respuesta.desenlace());
        assertEquals(Kairos.CAPACIDAD_NO_DISPONIBLE, respuesta.motivo());
        verify(brox).capacidades(eq(SESION), any());
        verifyNoMoreInteractions(brox);
    }

    @Test
    @DisplayName("un catálogo autorizado vacío no activa una política local de respaldo")
    void catalogoVacioNoHabilitaNingunaHerramienta() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        when(brox.capacidades(any(), any())).thenReturn(List.of());
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.ABRIR_SESION_CAPTURA.nombre(),
                        estado(7L, List.of("direccion"))));

        Respuesta respuesta = kairos.turno(turno("registra un depa"), SESION);

        assertEquals(Desenlace.CAPACIDAD_DESHABILITADA, respuesta.desenlace());
        verify(brox,never()).avanzarCaptura(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("una capacidad HUMANO no la ejecuta un agente, aunque el turno venga confirmado")
    void loQueEsHumanoNoLoHaceUnAgente() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        // El turno trae borrador, asi que la puerta es responder_captura: es la
        // capacidad que este turno invoca y por tanto la que tiene que declarar
        // BROX como HUMANO para que la prueba diga lo que dice su nombre.
        hayCapacidades(descriptor(Herramientas.RESPONDER_CAPTURA.nombre(), Capacidad.HUMANO, "HABILITADA"));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t1", "m1", "registralo ya", 7L, null, 4), SESION);

        assertEquals(Desenlace.SOLO_HUMANO, respuesta.desenlace());
        assertEquals(Kairos.RESERVADA_A_UNA_PERSONA, respuesta.motivo());
        verify(brox, never()).avanzarCaptura(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("abrir y responder son DOS capacidades: publicar una no concede la otra")
    void abrirNoConcedeResponder() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("dormitorios", "3"));
        hayCapacidades(automatica(Herramientas.ABRIR_SESION_CAPTURA.nombre()));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "tiene 3 dormitorios", 7L, null, 4), SESION);

        assertEquals(Desenlace.CAPACIDAD_DESHABILITADA, respuesta.desenlace(),
                "las dos van por POST /captura y aun asi el permiso no se hereda: deducir que "
                        + "quien abre puede responder seria conceder una capacidad que nadie "
                        + "declaro, y el fallo de conceder de mas se paga con una escritura");
        assertEquals(Kairos.CAPACIDAD_NO_DISPONIBLE, respuesta.motivo());
        verify(brox, never()).avanzarCaptura(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("una confirmación no soportada por el puerto no emite AUTO ni una propuesta local")
    void confirmarExigeUnProtocoloDePreparacionReal() {
        seEntiende(Accion.REGISTRAR_INTERACCION,
                Map.of("datosComerciales",com.controllocal.asistencia.interpretacion.ArgumentosComerciales.serializar(
                        interaccionC4("OPORTUNIDAD",7))));
        hayCapacidades(confirmable(Accion.REGISTRAR_INTERACCION.capacidad()));
        Respuesta respuesta = kairos.turno(turno("anota la llamada"), SESION);

        assertEquals(Desenlace.FALLO_TECNICO, respuesta.desenlace());
        assertEquals("CONTRATO_CORE_INCOMPATIBLE", respuesta.motivo());
        assertFalse(respuesta.confirmaUnaPersona());
        verify(brox, never()).operarComercial(any(), eq(ClienteBrox.OperacionComercial.REGISTRAR_INTERACCION), any(), any());
    }

    @Test
    @DisplayName("BROX NO puede apagar la confirmacion de lo sensible: AUTO no ejecuta un alta")
    void broxNoPuedeApagarLoSensible() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(automatica(Accion.REGISTRAR_PROPIEDAD.capacidad()),
                automatica(Herramientas.PREPARAR_ACTUACION_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.ABRIR_SESION_CAPTURA.nombre(), estadoCompleto(7L)));

        Respuesta respuesta = kairos.turno(turno("registra el depa"), SESION);

        assertEquals(Desenlace.PROPUESTA, respuesta.desenlace(),
                "confirmar_captura crea el registro físico con sus relaciones acreditadas: si una capacidad AUTO "
                        + "pudiera apagar su confirmacion, un error de configuracion del Core "
                        + "bastaria para que una conversacion diera de alta inmuebles sola");
        assertEquals(Kairos.CONFIRMA_UNA_PERSONA, respuesta.motivo());
        verify(brox).prepararCaptura(eq(SESION), eq(7L), any(), eq(estadoCompleto(7L)));
        assertEquals("act-7", respuesta.resultado().actuacion().referencia());
    }

    @Test
    @DisplayName("ejecutar el alta es OTRA capacidad: si BROX no la ofrece, no se intenta")
    void ejecutarElAltaEsOtraCapacidad() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        // Se publica responder_captura para que el turno LLEGUE a la ejecucion:
        // sin ella el rechazo seria el de la puerta y esta prueba daria verde
        // sin haber comprobado nunca lo que dice comprobar.
        hayCapacidades(automatica(Herramientas.RESPONDER_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.RESPONDER_CAPTURA.nombre(), estadoCompleto(7L)));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "si, registralo", 7L, null, 4), SESION);

        assertEquals(Desenlace.CAPACIDAD_DESHABILITADA, respuesta.desenlace(),
                "abrir una captura es TODOS y ejecutarla es AGENTE: son dos filas de la matriz");
        verify(brox).avanzarCaptura(any(), anyString(), eq(7L), eq(4), any(), any());
        verify(brox, never()).prepararCaptura(any(), anyLong(), any(), any());
    }

    // ==================================================================
    // Lo sensible se propone; ejecutar exige un turno aparte
    // ==================================================================

    @Test
    @DisplayName("con todo completo el turno PROPONE; no ejecuta")
    void completoNoEsEjecutado() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(confirmable(Accion.REGISTRAR_PROPIEDAD.capacidad()),
                confirmable(Herramientas.PREPARAR_ACTUACION_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.ABRIR_SESION_CAPTURA.nombre(), estadoCompleto(7L)));

        Respuesta respuesta = kairos.turno(turno("registra el depa"), SESION);

        assertEquals(Desenlace.PROPUESTA, respuesta.desenlace());
        assertEquals(Kairos.CONFIRMA_UNA_PERSONA, respuesta.motivo());
        assertTrue(respuesta.confirmaUnaPersona());
        verify(brox).prepararCaptura(eq(SESION), eq(7L), any(), eq(estadoCompleto(7L)));
        assertEquals("act-7", respuesta.resultado().actuacion().referencia());
    }

    @Test
    @DisplayName("un sí en conversación prepara; sólo la superficie humana puede confirmar")
    void unSiConversacionalSoloPreparaLaRevisionHumana() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(automatica(Herramientas.RESPONDER_CAPTURA.nombre()),
                automatica(Herramientas.PREPARAR_ACTUACION_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.RESPONDER_CAPTURA.nombre(), estadoCompleto(7L)));
        var respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "sí, regístralo", 7L, null, 4), SESION);
        assertEquals(Desenlace.PROPUESTA, respuesta.desenlace());
        assertEquals("PREPARADA", respuesta.resultado().actuacion().estado());
        assertNull(respuesta.resultado().ejecucion());
        verify(brox).prepararCaptura(eq(SESION), eq(7L), any(), eq(estadoCompleto(7L)));
    }

    @Test
    void pedirConfirmarUnaCapturaSoloPreparaSinActualizarLosDatos() {
        seEntiende(Accion.PREPARAR_CAPTURA, Map.of());
        when(brox.captura(any(), eq(7L), any())).thenReturn(estadoCompleto(7L));

        var respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "confirmar", 7L, null, 4), SESION);

        assertEquals(Desenlace.PROPUESTA, respuesta.desenlace());
        assertEquals("PREPARADA", respuesta.resultado().actuacion().estado());
        assertNull(respuesta.resultado().ejecucion());
        verify(brox).prepararCaptura(eq(SESION), eq(7L), any(), eq(estadoCompleto(7L)));
        verify(brox, never()).avanzarCaptura(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void pedirConfirmarNoSupleLosFaltantesNiEligeUnaCaptura() {
        seEntiende(Accion.PREPARAR_CAPTURA, Map.of());
        var sinContexto = kairos.turno(turno("confirmar"), SESION);
        assertEquals(List.of("idBorrador"), sinContexto.falta());
        when(brox.captura(any(), eq(7L), any()))
                .thenReturn(estado(7L, List.of("direccion")));

        var incompleta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "confirmar", 7L, null, 4), SESION);

        assertEquals(Desenlace.PREGUNTA, incompleta.desenlace());
        assertEquals(List.of("direccion"), incompleta.falta());
        verify(brox, never()).prepararCaptura(any(), anyLong(), any(), any());
        verify(brox, never()).avanzarCaptura(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("al continuar un borrador la herramienta es responder_captura, no abrirla")
    void continuarUnBorradorEsResponder() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("dormitorios", "3"));
        hayCapacidades(automatica(Herramientas.RESPONDER_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.RESPONDER_CAPTURA.nombre(),
                        estado(7L, List.of("direccion"))));

        kairos.turno(new Turno("API", "c1", "t2", "m2", "tiene 3 dormitorios", 7L, null, 4), SESION);

        ArgumentCaptor<Traza> traza = ArgumentCaptor.forClass(Traza.class);
        verify(brox).avanzarCaptura(any(), anyString(), eq(7L), eq(4), any(), traza.capture());
        assertEquals("responder_captura", traza.getValue().herramienta(),
                "abrir y responder son la misma ruta y dos herramientas: el rastro tiene que "
                        + "poder decir cual de las dos fue");
    }

    @Test
    @DisplayName("un dato sin verbo de alta se lee en el contexto del borrador")
    void datoSinVerboDeAltaContinuaElBorrador() {
        when(interprete.leer(anyString(), any(), any()))
                .thenReturn(Interprete.Lectura.nada(Interprete.Lectura.SIN_ACCION));
        when(interprete.leerEnCaptura(anyString(), eq(7L), any(), any()))
                .thenReturn(new Interprete.Lectura(Accion.REGISTRAR_PROPIEDAD,
                        Map.of("dormitorios", "3"), List.of(), null));
        hayCapacidades(automatica(Herramientas.RESPONDER_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.RESPONDER_CAPTURA.nombre(),
                        estado(7L, List.of("direccion"))));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "tiene 3 dormitorios", 7L, null, 4), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        verify(interprete).leerEnCaptura(anyString(), eq(7L), eq(SESION), any());
        verify(brox).avanzarCaptura(eq(SESION), anyString(), eq(7L), eq(4), eq(Map.of("dormitorios", "3")), any());
    }

    @Test
    @DisplayName("confirmar con datos faltantes NO ejecuta: la confirmacion no completa nada")
    void confirmarNoRellena() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of());
        hayCapacidades(confirmable(Herramientas.RESPONDER_CAPTURA.nombre()),
                confirmable(Herramientas.PREPARAR_ACTUACION_CAPTURA.nombre()));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.RESPONDER_CAPTURA.nombre(),
                        estado(7L, List.of("operacion", "titulares"))));

        Respuesta respuesta = kairos.turno(
                new Turno("API", "c1", "t2", "m2", "si, registralo", 7L, null, 4), SESION);

        assertEquals(Desenlace.PREGUNTA, respuesta.desenlace());
        assertEquals(List.of("operacion", "titulares"), respuesta.falta());
        verify(brox, never()).prepararCaptura(any(), anyLong(), any(), any());
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
        when(brox.recuperarPropietario(any(), any(), any())).thenReturn(Optional.empty());
        when(brox.buscarPropietarios(any(), anyString(), any()))
                .thenReturn(List.of(new ClienteBrox.Persona(5L, "Ana Torres", "D", "40506070",
                        null)));

        Respuesta respuesta = kairos.turno(turno("registra al propietario Torres 40506070"),
                SESION);

        assertEquals(Desenlace.RESPONDIDO, respuesta.desenlace());
        assertEquals(Kairos.YA_EXISTE, respuesta.motivo());
        assertEquals(5L, respuesta.resultado().persona().id());
        assertNull(respuesta.resultado().automatico());
        verify(brox, times(2)).recuperarPropietario(eq(SESION), any(), any());
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
        when(brox.recuperarPropietario(any(), any(), any())).thenReturn(Optional.empty());
        when(brox.buscarPropietarios(any(), anyString(), any())).thenReturn(List.of());
        when(brox.registrarPropietario(any(), any(), any()))
                .thenReturn(automatico(Herramientas.REGISTRAR_PROPIETARIO.nombre(),
                        new ClienteBrox.Persona(8L, "Inversiones SAC", "R", "20505060708", null)));

        kairos.turno(turno("registra a Inversiones SAC 20505060708"), SESION);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> alta = ArgumentCaptor.forClass(Map.class);
        verify(brox).registrarPropietario(eq(SESION), alta.capture(), any());
        assertEquals("R", alta.getValue().get("tipoDocumento"));
        assertEquals("J", alta.getValue().get("tipoPersona"));
    }

    // ==================================================================
    // La interaccion no adivina de que cuelga ni como termino
    // ==================================================================

    @Test
    @DisplayName("sin expediente del que colgar, no se registra")
    void interaccionSinExpediente() {
        prepararInteraccionC4(Map.of("canal","TELEFONO"),List.of("INTERCAMBIO_EFECTIVO"));
        var r=kairos.turno(turno("anota la llamada"),SESION);
        assertEquals(Desenlace.PREGUNTA,r.desenlace());assertTrue(r.falta().contains("vinculos"));
        verify(brox,never()).operarComercial(any(),any(),any(),any());
    }

    @Test
    @DisplayName("el id viaja con el nombre que BROX espera: idOportunidad, no idEntidad")
    void elIdViajaConElNombreDelContrato() {
        var datos=interaccionC4("OPORTUNIDAD",7);prepararInteraccionC4(datos,List.of("INTERCAMBIO_EFECTIVO"));
        when(brox.operarComercial(any(),any(),any(),any())).thenReturn(new ClienteBrox.ResultadoComercial(Map.of("id",3L),null,null));
        var r=kairos.turno(turno("anota la llamada de oportunidad7"),SESION);
        assertEquals(Desenlace.EJECUTADO,r.desenlace());
        verify(brox).operarComercial(eq(SESION),eq(ClienteBrox.OperacionComercial.REGISTRAR_INTERACCION),eq(datos),any());
        assertFalse(datos.containsKey("idEntidad"));
    }

    @Test
    @DisplayName("cada contexto tiene su campo: una prospeccion no viaja como oportunidad")
    void cadaContextoLlevaSuCampo() {
        var datos=interaccionC4("PROSPECCION",3);prepararInteraccionC4(datos,List.of("INTERCAMBIO_EFECTIVO"));
        when(brox.operarComercial(any(),any(),any(),any())).thenReturn(new ClienteBrox.ResultadoComercial(Map.of("id",4L),null,null));
        kairos.turno(turno("anota la llamada de prospección3"),SESION);
        verify(brox).operarComercial(eq(SESION),eq(ClienteBrox.OperacionComercial.REGISTRAR_INTERACCION),eq(datos),any());
        assertEquals("PROSPECCION",((Map<?,?>)((List<?>)datos.get("vinculos")).getFirst()).get("tipo"));
    }

    @Test
    @DisplayName("los resultados que se ofrecen los da BROX, no una lista de KAIROS")
    void losResultadosLosDaBrox() {
        var datos=new java.util.HashMap<String,Object>(interaccionC4("PROSPECCION",3));datos.put("resultado","CONTACTADO");
        prepararInteraccionC4(datos,List.of("INTERCAMBIO_EFECTIVO","SIN_RESPUESTA"));
        var r=kairos.turno(turno("anota la llamada"),SESION);
        assertEquals(Desenlace.PREGUNTA,r.desenlace());assertEquals(List.of("resultado"),r.falta());
        assertEquals(List.of("INTERCAMBIO_EFECTIVO","SIN_RESPUESTA"),r.opciones());
        verify(brox,never()).operarComercial(any(),any(),any(),any());
    }

    @Test
    @DisplayName("un resultado que ese contexto no admite se vuelve a preguntar")
    void elResultadoDeOtroContextoNoCuela() {
        var datos=new java.util.HashMap<String,Object>(interaccionC4("PROSPECCION",3));datos.put("resultado","VISITA_AGENDADA");
        prepararInteraccionC4(datos,List.of("INTERCAMBIO_EFECTIVO","SIN_RESPUESTA"));
        var r=kairos.turno(turno("anota llamada"),SESION);
        assertEquals(Desenlace.PREGUNTA,r.desenlace());assertTrue(r.falta().contains("resultado"));
        verify(brox,never()).operarComercial(any(),any(),any(),any());
    }

    @Test
    @DisplayName("un esquema incompatible no se sustituye por un vocabulario local")
    void esquemaIncompatibleNoSeConvierteEnListaVacia() {
        prepararInteraccionC4(interaccionC4("PROPIEDAD",9),List.of("INTERCAMBIO_EFECTIVO"));
        hayCapacidades(new Capacidad("registrar_interaccion","INTERACCION","ESCRITURA","AUTO",
                Map.of("type","object"),2,"HABILITADA",null,null));
        var r=kairos.turno(turno("anota whatsapp"),SESION);
        assertEquals(Desenlace.FALLO_TECNICO,r.desenlace());
        assertEquals("CONTRATO_CORE_INCOMPATIBLE",r.motivo());
        verify(brox,never()).operarComercial(any(),any(),any(),any());
    }

    // ==================================================================
    // Matching: que encaja, y por que
    // ==================================================================

    @Test void buscarExigeContextoVersionadoSinElegirDemandaPorCliente(){
        seEntiende(Accion.BUSCAR_COINCIDENCIAS,Map.of());
        hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        var r=kairos.turno(turno("coincidencias"),SESION);
        assertEquals(Desenlace.PREGUNTA,r.desenlace());
        assertEquals(List.of("origen","idOrigen","versionOrigen","tipoVersion","modalidad","capas","categoria"),r.falta());
        verify(brox,never()).coincidencias(any(),any(),any());
    }
    @Test void transportaResultadoCoreSinRecalcularAptitud(){
        seEntiende(Accion.BUSCAR_COINCIDENCIAS,consultaMatching());
        hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        var resultado=unaCoincidencia();when(brox.coincidencias(any(),any(),any())).thenReturn(resultado);
        var r=kairos.turno(turno("coincidencias demanda47"),SESION);
        assertEquals(Desenlace.RESPONDIDO,r.desenlace());assertSame(resultado,r.resultado().matching());
        verify(brox).coincidencias(eq(SESION),argThat(q->q.idOrigen()==47L&&q.versionOrigen()==2L&&q.tamano()==null&&q.capas().equals(java.util.Set.of("L0"))&&q.exploracion()==null),any());
    }
    @Test void descubrirConservaLaSeleccionExplicitaSinElegirLimite() {
        var datos=new java.util.HashMap<>(consultaMatching());datos.put("categoria","INCOMPATIBLE");datos.put("modoExploracion","DESCUBRIR");
        seEntiende(Accion.BUSCAR_COINCIDENCIAS,datos);hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        when(brox.coincidencias(any(),any(),any())).thenReturn(unaCoincidencia());
        assertEquals(Desenlace.RESPONDIDO,kairos.turno(turno("descubrir escalones"),SESION).desenlace());
        verify(brox).coincidencias(eq(SESION),argThat(q->q.capas().equals(java.util.Set.of("L0"))&&q.categoria().equals("INCOMPATIBLE")
                &&q.exploracion().equals(new ClienteBrox.ExploracionPrecio(ClienteBrox.ModoExploracion.DESCUBRIR,null,null))),any());
    }
    @Test void explorarTransportaElLimiteSinCalcularNiSuponerUnaTolerancia() {
        var datos=new java.util.HashMap<>(consultaMatching());datos.putAll(Map.of("categoria","INCOMPATIBLE","modoExploracion","EXPLORAR",
                "limiteExploracion","110000.50","monedaExploracion","USD"));
        seEntiende(Accion.BUSCAR_COINCIDENCIAS,datos);hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        var resultado=unaCoincidencia();when(brox.coincidencias(any(),any(),any())).thenReturn(resultado);
        var respuesta=kairos.turno(turno("explorar límite declarado"),SESION);assertSame(resultado,respuesta.resultado().matching());
        verify(brox).coincidencias(eq(SESION),argThat(q->q.capas().equals(java.util.Set.of("L0"))&&q.exploracion().equals(
                new ClienteBrox.ExploracionPrecio(ClienteBrox.ModoExploracion.EXPLORAR,new java.math.BigDecimal("110000.50"),"USD"))),any());
    }
    @Test void explorarSinLimiteOMonedaPreguntaSinBuscar() {
        var datos=new java.util.HashMap<>(consultaMatching());datos.put("modoExploracion","EXPLORAR");
        seEntiende(Accion.BUSCAR_COINCIDENCIAS,datos);hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        var respuesta=kairos.turno(turno("explorar"),SESION);assertEquals(Desenlace.PREGUNTA,respuesta.desenlace());
        assertEquals(List.of("limiteExploracion","monedaExploracion"),respuesta.falta());verify(brox,never()).coincidencias(any(),any(),any());
    }
    @Test void unLimiteSinModoNoActivaExploracionPorInferencia() {
        var datos=new java.util.HashMap<>(consultaMatching());datos.putAll(Map.of("limiteExploracion","110000","monedaExploracion","USD"));
        seEntiende(Accion.BUSCAR_COINCIDENCIAS,datos);hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        var respuesta=kairos.turno(turno("límite"),SESION);assertEquals(Desenlace.PREGUNTA,respuesta.desenlace());
        assertEquals(List.of("modoExploracion"),respuesta.falta());verify(brox,never()).coincidencias(any(),any(),any());
    }
    @Test void resultadoVacioNoAmpliaCapasNiPresupuesto() {
        seEntiende(Accion.BUSCAR_COINCIDENCIAS,consultaMatching());hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        when(brox.coincidencias(any(),any(),any())).thenReturn(new CoincidenciasComerciales("DISPONIBLE",null,null,"D1-D4-R3","cursor",
                List.of(),0L,"COMPLETA",java.util.Set.of(),null));
        var respuesta=kairos.turno(turno("coincidencias"),SESION);assertEquals(Desenlace.RESPONDIDO,respuesta.desenlace());
        verify(brox,times(1)).coincidencias(eq(SESION),argThat(q->q.exploracion()==null&&q.capas().equals(java.util.Set.of("L0"))),any());
        verify(brox,never()).operarComercial(any(),any(),any(),any());
    }
    @Test void resultadoAusenteEsFalloTecnicoYNuncaVacio(){
        seEntiende(Accion.BUSCAR_COINCIDENCIAS,consultaMatching());
        hayCapacidades(automatica(Accion.BUSCAR_COINCIDENCIAS.capacidad()));
        when(brox.coincidencias(any(),any(),any())).thenReturn(null);
        assertEquals(Desenlace.FALLO_TECNICO,kairos.turno(turno("coincidencias"),SESION).desenlace());
    }
    @Test void explicarUsaCursorYReferenciaSinRepetirBusqueda(){
        var grupo=unaCoincidencia().grupos().getFirst();var candidato=grupo.candidatos().getFirst();
        seEntiende(Accion.EXPLICAR_MATCH,Map.of("cursor","cursor-opaco","referenciaDemanda",grupo.referenciaDemanda(),"referencia",candidato.referencia()));
        hayCapacidades(automatica(Accion.EXPLICAR_MATCH.capacidad()));
        when(brox.explicarCoincidencia(any(),anyString(),anyString(),anyString(),any())).thenReturn(candidato);
        var r=kairos.turno(turno("explica coincidencia"),SESION);
        assertEquals(Desenlace.RESPONDIDO,r.desenlace());assertSame(candidato,r.resultado().explicacion());
        verify(brox).explicarCoincidencia(eq(SESION),eq("cursor-opaco"),eq(grupo.referenciaDemanda()),eq(candidato.referencia()),any());
        verify(brox,never()).coincidencias(any(),any(),any());
    }
    @Test void explicarSinCursorNoAdivinaRecorridoNiSeleccionaPrimeraCoincidencia(){
        seEntiende(Accion.EXPLICAR_MATCH,Map.of("referenciaDemanda","grupo-opaco","referencia","candidato-opaco"));
        hayCapacidades(automatica(Accion.EXPLICAR_MATCH.capacidad()));
        var r=kairos.turno(turno("explica coincidencia"),SESION);
        assertEquals(Desenlace.PREGUNTA,r.desenlace());assertEquals(List.of("cursor"),r.falta());
        verify(brox,never()).explicarCoincidencia(any(),anyString(),anyString(),anyString(),any());
    }
    @Test void explicarSinGrupoPideLaBusquedaDelResultadoSinElegirLaPrimera(){
        seEntiende(Accion.EXPLICAR_MATCH,Map.of("cursor","cursor-opaco","referencia","candidato-opaco"));
        hayCapacidades(automatica(Accion.EXPLICAR_MATCH.capacidad()));
        var r=kairos.turno(turno("explica coincidencia"),SESION);
        assertEquals(Desenlace.PREGUNTA,r.desenlace());assertEquals(List.of("referenciaDemanda"),r.falta());
        verify(brox,never()).explicarCoincidencia(any(),anyString(),anyString(),anyString(),any());
        verify(brox,never()).coincidencias(any(),any(),any());
    }
    @Test void continuarTransportaContextoCambiadoSinInventarCeroResultados(){
        seEntiende(Accion.CONTINUAR_COINCIDENCIAS,Map.of("cursor","cursor-opaco"));
        hayCapacidades(automatica(Accion.CONTINUAR_COINCIDENCIAS.capacidad()));
        var rCore=new CoincidenciasComerciales("CONTEXTO_CAMBIADO",null,null,"D1-D4",null,List.of(),null,"LIMITADA",java.util.Set.of(),null);
        when(brox.continuarCoincidencias(any(),anyString(),any())).thenReturn(rCore);
        var r=kairos.turno(turno("continua coincidencias"),SESION);
        assertSame(rCore,r.resultado().matching());assertNull(r.motivo());
        verify(brox).continuarCoincidencias(eq(SESION),eq("cursor-opaco"),any());
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
                7, List.of(), List.of(), List.of());
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
                        List.of(),List.of()));

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
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any()))
                .thenReturn(automatico(Herramientas.ABRIR_SESION_CAPTURA.nombre(),
                        estado(7L, List.of("operacion"))));

        Respuesta respuesta = kairos.turno(turno("registra un depa"), SESION);

        assertEquals(Kairos.FALTAN_DATOS, respuesta.motivo());
        assertFalse(respuesta.motivo().contains(" "),
                "un motivo con espacios es una frase; la frase la compone quien habla, con su "
                        + "modelo y su tono, y no puede venir cocinada desde aqui");
        assertNotNull(respuesta.resultado().captura());
    }

    // ==================================================================

    @ParameterizedTest
    @EnumSource(ErrorBrox.Tipo.class)
    void falloDeConsultaNoSeComunicaComoAusenciaDeClientes(ErrorBrox.Tipo tipo) {
        seEntiende(Accion.CONSULTAR_CLIENTE,Map.of("texto","Rosa"));
        when(brox.buscarClientes(any(),eq("Rosa"),any())).thenThrow(new ErrorBrox(tipo,tipo.name(),503));
        var respuesta=kairos.turno(turno("busca a Rosa"),SESION);
        var esperado=switch(tipo) {
            case PERMISO_INSUFICIENTE -> Desenlace.SIN_PERMISO;
            case CAPACIDAD_DESHABILITADA -> Desenlace.CAPACIDAD_DESHABILITADA;
            case FALLO_TECNICO -> Desenlace.FALLO_TECNICO;
            case SOLICITUD_RECHAZADA -> Desenlace.RECHAZADO;
        };
        assertEquals(esperado,respuesta.desenlace());
        assertEquals(tipo.name(),respuesta.motivo());
        assertFalse(respuesta.confirmaUnaPersona());
        verify(brox).capacidades(any(),any());
        verify(brox).buscarClientes(any(),eq("Rosa"),any());
        verifyNoMoreInteractions(brox);
    }

    @Test void transporteInterrumpidoNoInventaUnResultadoComercialNiReintenta() {
        seEntiende(Accion.CONSULTAR_CLIENTE,Map.of("texto","Rosa"));
        when(brox.buscarClientes(any(),eq("Rosa"),any())).thenThrow(
                new ErrorBrox(ErrorBrox.Tipo.FALLO_TECNICO, "FALLO_TECNICO", 500));
        var respuesta=kairos.turno(turno("busca a Rosa"),SESION);
        assertEquals(Desenlace.FALLO_TECNICO,respuesta.desenlace());
        verify(brox).capacidades(any(),any());
        verify(brox).buscarClientes(any(),eq("Rosa"),any());
        verifyNoMoreInteractions(brox);
    }

    @Test void unaActuacionInciertaSeConsultaSinRepetirEscritura() {
        when(brox.consultarActuacion(any(), eq("ref-original"), any())).thenReturn(
                new ClienteBrox.ResultadoActuacion("ref-original", "EN_VERIFICACION", null, null, null, null));
        var r = kairos.turno(new Turno("API", "c1", "t1", "m1", null, null, "ref-original", null), SESION);
        assertEquals(Desenlace.RESULTADO_INCIERTO, r.desenlace());
        assertEquals("ref-original", r.resultado().actuacion().referencia());
        assertNull(r.resultado().ejecucion());
        verify(brox).consultarActuacion(eq(SESION), eq("ref-original"), any());
        verifyNoMoreInteractions(brox);
        verifyNoInteractions(interprete);
    }

    @Test
    void unAvanceAutomaticoConservaSuReferenciaAlPrepararLaRevision() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("tipoPropiedad", "DEPARTAMENTO"));
        var avance = automatico(Herramientas.ABRIR_SESION_CAPTURA.nombre(), estadoCompleto(7L));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any())).thenReturn(avance);

        var respuesta = kairos.turno(turno("registra el departamento"), SESION);

        assertEquals(Desenlace.PROPUESTA, respuesta.desenlace());
        assertSame(avance.operacion(), respuesta.resultado().automatico());
        assertSame(avance.resultado(), respuesta.resultado().captura());
        assertEquals("act-7", respuesta.resultado().actuacion().referencia());
        assertNull(respuesta.resultado().ejecucion());
        verify(brox).avanzarCaptura(eq(SESION), anyString(), any(), any(), eq(Map.of("tipoPropiedad", "DEPARTAMENTO")), any());
        verify(brox).prepararCaptura(eq(SESION), eq(7L), any(), same(avance.resultado()));
    }

    @Test
    void unFalloDePreparacionConservaElAvanceAutomaticoYaConfirmado() {
        seEntiende(Accion.REGISTRAR_PROPIEDAD, Map.of("tipoPropiedad", "DEPARTAMENTO"));
        var avance = automatico(Herramientas.ABRIR_SESION_CAPTURA.nombre(), estadoCompleto(7L));
        when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any())).thenReturn(avance);
        when(brox.prepararCaptura(eq(SESION), eq(7L), any(), same(avance.resultado())))
                .thenThrow(new ErrorBrox(ErrorBrox.Tipo.FALLO_TECNICO, "FALLO_PREPARACION", 503));

        var respuesta = kairos.turno(turno("registra el departamento"), SESION);

        assertEquals(Desenlace.FALLO_TECNICO, respuesta.desenlace());
        assertEquals("FALLO_PREPARACION", respuesta.motivo());
        assertSame(avance.resultado(), respuesta.resultado().captura());
        assertSame(avance.operacion(), respuesta.resultado().automatico());
        assertNull(respuesta.resultado().actuacion());
        assertNull(respuesta.resultado().ejecucion());
        assertFalse(respuesta.confirmaUnaPersona());
        verify(brox).capacidades(eq(SESION), any());
        verify(brox).avanzarCaptura(eq(SESION), anyString(), any(), any(), any(), any());
        verify(brox).prepararCaptura(eq(SESION), eq(7L), any(), same(avance.resultado()));
        verifyNoMoreInteractions(brox);
    }

    @Test
    void unAltaAutomaticaDePropietarioDevuelvePersonaYReferenciaOriginales() {
        seEntiende(Accion.REGISTRAR_PROPIETARIO,
                Map.of("numeroDocumento", "40506070", "nombre", "Ana Torres"));
        when(brox.recuperarPropietario(any(), any(), any())).thenReturn(Optional.empty());
        when(brox.buscarPropietarios(any(), anyString(), any())).thenReturn(List.of());
        var creado = automatico(Herramientas.REGISTRAR_PROPIETARIO.nombre(),
                new ClienteBrox.Persona(8L, "Ana Torres", "D", "40506070", "999111222"));
        when(brox.registrarPropietario(any(), any(), any())).thenReturn(creado);

        var respuesta = kairos.turno(turno("registra a Ana Torres 40506070"), SESION);

        assertEquals(Desenlace.EJECUTADO, respuesta.desenlace());
        assertSame(creado.resultado(), respuesta.resultado().persona());
        assertSame(creado.operacion(), respuesta.resultado().automatico());
        assertFalse(respuesta.confirmaUnaPersona());
        verify(brox).registrarPropietario(eq(SESION), eq(Map.of("numeroDocumento", "40506070",
                "nombre", "Ana Torres", "tipoPersona", "N", "tipoDocumento", "D")), any());
    }

    @Test
    void unaInteraccionAutomaticaDevuelveHechoYReferenciaOriginales() {
        prepararInteraccionC4(interaccionC4("PROSPECCION",3),List.of("INTERCAMBIO_EFECTIVO"));
        var ref=new ReferenciaAutomatico("auto-original","registrar_interaccion",1);
        var creada=new ClienteBrox.ResultadoComercial(Map.of("id",4L),null,ref);
        when(brox.operarComercial(any(),any(),any(),any())).thenReturn(creada);
        var r=kairos.turno(turno("anota contacto"),SESION);
        assertEquals(Desenlace.EJECUTADO,r.desenlace());assertSame(creada,r.resultado().comercial());
        assertSame(ref,r.resultado().automatico());assertFalse(r.confirmaUnaPersona());
        verify(brox).operarComercial(eq(SESION),eq(ClienteBrox.OperacionComercial.REGISTRAR_INTERACCION),any(),any());
    }

    @ParameterizedTest
    @EnumSource(value = Accion.class,
            names = {"REGISTRAR_PROPIEDAD", "REGISTRAR_PROPIETARIO", "REGISTRAR_INTERACCION"})
    void unAutomaticoInciertoConservaReferenciaSinDependientesNiReintento(Accion accion) {
        var referencia = new ReferenciaAutomatico("auto-original", accion.capacidad(), 1);
        var error = new ErrorBrox(ErrorBrox.Tipo.FALLO_TECNICO, "RESULTADO_EN_VERIFICACION", 503,
                referencia);
        switch (accion) {
            case REGISTRAR_PROPIEDAD -> {
                seEntiende(accion, Map.of("tipoPropiedad", "DEPARTAMENTO"));
                when(brox.avanzarCaptura(any(), anyString(), any(), any(), any(), any())).thenThrow(error);
            }
            case REGISTRAR_PROPIETARIO -> {
                seEntiende(accion, Map.of("numeroDocumento", "40506070", "nombre", "Ana Torres"));
                when(brox.recuperarPropietario(any(), any(), any())).thenReturn(Optional.empty());
                when(brox.buscarPropietarios(any(), anyString(), any())).thenReturn(List.of());
                when(brox.registrarPropietario(any(), any(), any())).thenThrow(error);
            }
            case REGISTRAR_INTERACCION -> {
                prepararInteraccionC4(interaccionC4("PROSPECCION",3),List.of("INTERCAMBIO_EFECTIVO"));
                when(brox.operarComercial(any(),any(),any(),any())).thenThrow(error);
            }
            default -> throw new AssertionError("acción fuera del contrato AUTO probado");
        }

        var respuesta = kairos.turno(turno("actuación automática autorizada"), SESION);

        assertEquals(Desenlace.RESULTADO_INCIERTO, respuesta.desenlace());
        assertEquals("RESULTADO_EN_VERIFICACION", respuesta.motivo());
        assertSame(referencia, respuesta.resultado().automatico());
        assertNull(respuesta.resultado().captura());
        assertNull(respuesta.resultado().persona());
        assertNull(respuesta.resultado().comercial());
        assertNull(respuesta.resultado().actuacion());
        assertNull(respuesta.resultado().ejecucion());
        assertFalse(respuesta.confirmaUnaPersona());
        verify(brox).capacidades(eq(SESION), any());
        switch (accion) {
            case REGISTRAR_PROPIEDAD -> verify(brox).avanzarCaptura(eq(SESION), anyString(), any(), any(), any(), any());
            case REGISTRAR_PROPIETARIO -> {
                verify(brox).recuperarPropietario(eq(SESION), any(), any());
                verify(brox).buscarPropietarios(eq(SESION), eq("40506070"), any());
                verify(brox).registrarPropietario(eq(SESION), any(), any());
            }
            case REGISTRAR_INTERACCION -> {
                verify(brox).operarComercial(eq(SESION),eq(ClienteBrox.OperacionComercial.REGISTRAR_INTERACCION),any(),any());
            }
            default -> throw new AssertionError("acción fuera del contrato AUTO probado");
        }
        verifyNoMoreInteractions(brox);
    }

    @Test
    void consultarUnAutomaticoPorReferenciaNoInterpretaNiRepiteLaOrdenOriginal() {
        var original = new ClienteBrox.ResultadoActuacion("auto-original", "EXITO", null,
                Map.of("idPropietario", 8L), null, null);
        when(brox.consultarActuacion(eq(SESION), eq("auto-original"), any())).thenReturn(original);

        var respuesta = kairos.turno(new Turno("API", "c1", "t2", "m2", "vuelve a registrar a Ana Torres 40506070", 7L, "auto-original", 4), SESION);

        assertEquals(Desenlace.RESPONDIDO, respuesta.desenlace());
        assertSame(original, respuesta.resultado().actuacion());
        assertEquals(8L, respuesta.resultado().actuacion().resultado().get("idPropietario"));
        verify(brox).consultarActuacion(eq(SESION), eq("auto-original"), any());
        verifyNoMoreInteractions(brox);
        verifyNoInteractions(interprete);
    }

    @Test
    void laConsultaC4DevuelveElMismoCuerpoComercialSinVolverAlSlotAntiguo() {
        var referencia=new ClienteBrox.ReferenciaAutomatico("auto:registrar_interaccion:opaque","registrar_interaccion",1);
        var comercial=new ClienteBrox.ResultadoComercial(Map.of("id",17L,"version",3L,
                "declaracion",Map.of("numero",new java.math.BigDecimal("120.125"),"confirmacion","POR_CONFIRMAR")),null,referencia);
        var original=new ClienteBrox.ResultadoActuacion(referencia.referencia(),"EXITO",null,null,null,comercial);
        when(brox.consultarActuacion(eq(SESION),eq(referencia.referencia()),any())).thenReturn(original);

        var respuesta=kairos.turno(new Turno("API","c1","t2","m2","registra otra interacción",null,
                referencia.referencia(),null),SESION);

        assertEquals(Desenlace.RESPONDIDO,respuesta.desenlace());
        assertSame(comercial,respuesta.resultado().comercial());
        assertSame(referencia,respuesta.resultado().automatico());
        assertNull(respuesta.resultado().actuacion().resultado());
        verify(brox).consultarActuacion(eq(SESION),eq(referencia.referencia()),any());
        verifyNoMoreInteractions(brox);verifyNoInteractions(interprete);
    }

    @Test
    void elRotuloDelEsquemaEsAyudaYElArgumentoConservaElValorCore() {
        var datos=new java.util.HashMap<String,Object>(interaccionC4("PROSPECCION",3));
        datos.put("resultado","CONTACTADO");
        prepararInteraccionC4(datos,List.of("INTERCAMBIO_EFECTIVO","SIN_RESPUESTA"));
        var esquema=Map.<String,Object>of("required",List.of("vinculos"),"properties",Map.of("resultado",Map.of(
                "enum",List.of("INTERCAMBIO_EFECTIVO","SIN_RESPUESTA"),"oneOf",List.of(
                        Map.of("const","INTERCAMBIO_EFECTIVO","title","Intercambio efectivo"),
                        Map.of("const","SIN_RESPUESTA","title","Sin respuesta")))));
        hayCapacidades(new Capacidad("registrar_interaccion","INTERACCION","ESCRITURA","AUTO",esquema,2,"HABILITADA",null,null));
        var pregunta=kairos.turno(turno("anota llamada"),SESION);
        assertEquals(List.of("Intercambio efectivo","Sin respuesta"),pregunta.opciones());
        verify(brox,never()).operarComercial(any(),any(),any(),any());

        datos.put("resultado","INTERCAMBIO_EFECTIVO");
        seEntiende(Accion.REGISTRAR_INTERACCION,Map.of("datosComerciales",
                com.controllocal.asistencia.interpretacion.ArgumentosComerciales.serializar(datos)));
        when(brox.operarComercial(any(),any(),any(),any())).thenReturn(new ClienteBrox.ResultadoComercial(Map.of("id",17L),null,null));
        assertEquals(Desenlace.EJECUTADO,kairos.turno(turno("anota llamada"),SESION).desenlace());
        verify(brox).operarComercial(eq(SESION),eq(ClienteBrox.OperacionComercial.REGISTRAR_INTERACCION),
                argThat(d->"INTERCAMBIO_EFECTIVO".equals(d.get("resultado"))),any());
    }

    @Test
    void unReplayDePropietarioRecuperaElOriginalAntesDeBuscarORegistrar() {
        seEntiende(Accion.REGISTRAR_PROPIETARIO,
                Map.of("numeroDocumento", "40506070", "nombre", "Ana Torres"));
        var original = automatico(Herramientas.REGISTRAR_PROPIETARIO.nombre(),
                new ClienteBrox.Persona(8L, "Ana Torres", "D", "40506070", "999111222"));
        when(brox.recuperarPropietario(any(), any(), any())).thenReturn(Optional.of(original));

        var respuesta = kairos.turno(turno("registra a Ana Torres 40506070"), SESION);

        assertEquals(Desenlace.EJECUTADO, respuesta.desenlace());
        assertNull(respuesta.motivo());
        assertSame(original.resultado(), respuesta.resultado().persona());
        assertSame(original.operacion(), respuesta.resultado().automatico());
        InOrder orden = inOrder(brox);
        orden.verify(brox).capacidades(eq(SESION), any());
        orden.verify(brox).recuperarPropietario(eq(SESION), eq(Map.of("numeroDocumento", "40506070",
                "nombre", "Ana Torres", "tipoPersona", "N", "tipoDocumento", "D")), any());
        verifyNoMoreInteractions(brox);
    }

    @Test
    void unaSegundaRecuperacionCierraLaCarreraAntesDeResponderYaExiste() {
        seEntiende(Accion.REGISTRAR_PROPIETARIO,
                Map.of("numeroDocumento", "40506070", "nombre", "Ana Torres"));
        var original = automatico(Herramientas.REGISTRAR_PROPIETARIO.nombre(),
                new ClienteBrox.Persona(8L, "Ana Torres", "D", "40506070", "999111222"));
        when(brox.recuperarPropietario(any(), any(), any()))
                .thenReturn(Optional.empty(), Optional.of(original));
        when(brox.buscarPropietarios(any(), eq("40506070"), any())).thenReturn(List.of(
                new ClienteBrox.Persona(8L, "Ana Torres (actualizada)", "D", "40506070", null)));

        var respuesta = kairos.turno(turno("registra a Ana Torres 40506070"), SESION);

        assertEquals(Desenlace.EJECUTADO, respuesta.desenlace());
        assertNull(respuesta.motivo(), "recuperar el comando propio no se comunica como YA_EXISTE");
        assertSame(original.resultado(), respuesta.resultado().persona());
        assertSame(original.operacion(), respuesta.resultado().automatico());
        InOrder orden = inOrder(brox);
        orden.verify(brox).capacidades(eq(SESION), any());
        orden.verify(brox).recuperarPropietario(eq(SESION), any(), any());
        orden.verify(brox).buscarPropietarios(eq(SESION), eq("40506070"), any());
        orden.verify(brox).recuperarPropietario(eq(SESION), any(), any());
        verifyNoMoreInteractions(brox);
    }

    private static <T> ResultadoAutomatico<T> automatico(String capacidad, T resultado) {
        return new ResultadoAutomatico<>(new ReferenciaAutomatico("auto-" + capacidad, capacidad, 1),
                resultado);
    }

    private void seEntiende(Accion accion, Map<String, String> datos) {
        when(interprete.leer(anyString(), any(), any()))
                .thenReturn(new Interprete.Lectura(accion, datos, List.of(), null));
    }

    private void hayCapacidades(Capacidad... capacidades) {
        when(brox.capacidades(any(), any())).thenReturn(List.of(capacidades));
    }

    private static Turno turno(String texto) {
        return new Turno("API", "c1", "t1", "m1", texto, null, null, null);
    }

    private static EstadoCaptura estado(Long id, List<String> faltante) {
        ClienteBrox.Pregunta siguiente = faltante.isEmpty() ? null
                : new ClienteBrox.Pregunta(faltante.get(0), faltante.get(0), "COMUN", null, "TEXTO", "TEXTO", null, null, "ALT", true, null, 0, null, null);
        return new EstadoCaptura(id, "CAP-00001", "REGISTRAR_PROPIEDAD", "E", "API", Map.of(), faltante,
                siguiente, false, null, null, null, 4, Map.of(), Map.of(), Map.of("estado", "DISPONIBLE"), Map.of(), List.of());
    }

    private static EstadoCaptura estadoCompleto(Long id) {
        return new EstadoCaptura(id, "CAP-00001", "REGISTRAR_PROPIEDAD", "E", "API", Map.of(), List.of(),
                null, true, null, null, null, 4, Map.of(), Map.of(), Map.of("estado", "DISPONIBLE"), Map.of(), List.of());
    }

    private static Map<String,String> consultaMatching(){return Map.of("origen","DEMANDA","idOrigen","47","versionOrigen","2","tipoVersion","DEMANDA","modalidad","COMERCIAL","capas","L0","categoria","COMERCIAL_CONFIRMADA");}
    private static CoincidenciasComerciales unaCoincidencia(){
        var razon=new ClienteBrox.RazonCoincidencia("criterio-opaco","PROPIEDAD:dormitorios","PREFERENCIA",3,(short)1,null);
        var c=new CoincidenciaComercial("candidato-opaco",1,"L0","COMERCIAL_CONFIRMADA",(short)1,(short)1,3,0,3,List.of(razon),Map.of(),"COMPLETA",null);
        return new CoincidenciasComerciales("DISPONIBLE",null,java.time.Instant.parse("2026-10-29T00:00:00Z"),"D1-D4","cursor-opaco",List.of(new ClienteBrox.GrupoCoincidencias("demanda-opaca",2,List.of(c))),1L,"COMPLETA",java.util.Set.of(),null);
    }

    private static ClienteBrox.Asunto asunto(String id, String tipo) {
        return new ClienteBrox.Asunto(id, tipo, "SOLICITUD", 9L, "SOL-0009", "descripcion", "ALTA",
                3, true, "solicitudes/9", "lleva 3 dias esperando",null);
    }
}



