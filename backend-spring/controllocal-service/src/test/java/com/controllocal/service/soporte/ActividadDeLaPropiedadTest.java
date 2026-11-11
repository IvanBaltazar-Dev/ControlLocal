package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.CatalogoInteracciones;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActividadDeLaPropiedadTest {
    private final Actor actor = new Actor(1, 2, 3, "AGENTE");
    private final OportunidadComercialRepository oportunidades = mock(OportunidadComercialRepository.class);
    private final VisitaRepository visitas = mock(VisitaRepository.class);
    private final InteraccionService interacciones = mock(InteraccionService.class);
    private final SolicitudAlquilerRepository expedientes = mock(SolicitudAlquilerRepository.class);
    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);
    private final ActividadDeLaPropiedad actividad = new ActividadDeLaPropiedad(
            oportunidades, visitas, interacciones, expedientes, contratos);

    @Test
    void unaInteraccionDirectaSeVeAntesDelPrimerEncargoSinInventarTitularNiFecha() {
        FichaInteraccion ficha = ficha(List.of(new VinculoFicha("PROPIEDAD", 100, null)), null);
        when(interacciones.porPropiedad(100, actor, FinalidadLectura.FICHA_PROPIEDAD))
                .thenReturn(List.of(ficha));

        var resultado = actividad.de(actor, 100, List.of());

        assertEquals(1, resultado.interacciones().size());
        var hecho = resultado.interacciones().getFirst();
        assertEquals("Llamada · Teléfono", hecho.titulo());
        assertEquals("Llamada perdida", hecho.estadoRotulo());
        assertNull(hecho.fecha());
        assertNull(hecho.idEncargo());
        assertNull(hecho.operacion());
        assertEquals(ficha.vinculos(), hecho.vinculosInteraccion());
        verifyNoInteractions(oportunidades, visitas, expedientes, contratos);
    }

    @Test
    void unOrigenUnicoConservaEncargoYOperacionSinReleerSuContacto() {
        FichaInteraccion ficha = ficha(List.of(new VinculoFicha("PROPIEDAD", 100, null),
                new VinculoFicha("ENCARGO", 9, null)), LocalDate.of(2026, 7, 1));
        when(interacciones.porPropiedad(100, actor, FinalidadLectura.FICHA_PROPIEDAD))
                .thenReturn(List.of(ficha));

        var hecho = actividad.de(actor, 100, List.of(encargo(9, "VENTA"))).interacciones().getFirst();

        assertEquals(9L, hecho.idEncargo());
        assertEquals("VENTA", hecho.operacion());
        assertEquals("Venta", hecho.operacionRotulo());
        assertEquals(LocalDate.of(2026, 7, 1), hecho.fecha());
        verify(interacciones).porPropiedad(100, actor, FinalidadLectura.FICHA_PROPIEDAD);
    }

    @Test
    void variosOrigenesConservanUnHechoYNoSeReducenAUnEncargoArbitrario() {
        FichaInteraccion ficha = ficha(List.of(new VinculoFicha("PROPIEDAD", 100, null),
                new VinculoFicha("ENCARGO", 9, null), new VinculoFicha("ENCARGO", 10, null)), null);
        when(interacciones.porPropiedad(100, actor, FinalidadLectura.FICHA_PROPIEDAD))
                .thenReturn(List.of(ficha));

        var resultado = actividad.de(actor, 100,
                List.of(encargo(9, "VENTA"), encargo(10, "ALQUILER")));

        assertEquals(1, resultado.interacciones().size());
        var hecho = resultado.interacciones().getFirst();
        assertEquals(701L, hecho.id());
        assertNull(hecho.idEncargo());
        assertNull(hecho.operacion());
        assertEquals(ficha.vinculos(), hecho.vinculosInteraccion());
    }

    @Test
    void historiaLegadaConservaNotaYTranscripcionSinInventarResultadoNiFechaDelHecho() {
        var origenes = List.of(new VinculoActividad(TipoContexto.PROPIEDAD, 100L, null, null));
        var datos = new DatosInteraccion(origenes, null, null, null, null, null,
                List.of(), null, null, "Nota original", List.of(), null, null, "Historia", null);
        var ficha = new FichaInteraccion(701, 0, 3, null, Instant.parse("2026-07-10T16:00:00Z"), datos,
                List.of(new VinculoFicha("PROPIEDAD", 100, null)), null, null,
                "CONTACTADO", "L", "Agente", List.of(), null,
                new DatosLegados("CAPTACION", "Transcripción original", java.time.OffsetDateTime.parse("2026-07-01T10:00:00-05:00")));
        when(interacciones.porPropiedad(100, actor, FinalidadLectura.FICHA_PROPIEDAD)).thenReturn(List.of(ficha));

        var resultado = actividad.de(actor, 100, List.of());

        assertEquals(1, resultado.interacciones().size());
        var hecho = resultado.interacciones().getFirst();
        assertTrue(hecho.detalle().contains("Nota original"));
        assertTrue(hecho.detalle().contains("Transcripción original"));
        assertNull(hecho.fecha());
        assertEquals("CONTACTADO", hecho.estadoRotulo());
    }

    @Test
    void permisoDenegadoNoSeConvierteEnActividadVacia() {
        when(interacciones.porPropiedad(100, actor, FinalidadLectura.FICHA_PROPIEDAD))
                .thenThrow(new AccesoNoAutorizadoException());

        assertThrows(AccesoNoAutorizadoException.class, () -> actividad.de(actor, 100, List.of()));
    }

    @Test
    void falloTecnicoNoSeConvierteEnAusenciaDeHistoria() {
        when(interacciones.porPropiedad(100, actor, FinalidadLectura.FICHA_PROPIEDAD))
                .thenThrow(new IllegalStateException("fallo técnico"));

        assertThrows(IllegalStateException.class, () -> actividad.de(actor, 100, List.of()));
    }

    @Test
    void gobiernoNoConsultaActividadComercialNiConEncargosVacios() {
        Actor admin = new Actor(1, 8, 9, "TENANT_ADMIN");

        assertThrows(AccesoNoAutorizadoException.class, () -> actividad.de(admin, 100, List.of()));
        verifyNoInteractions(interacciones, oportunidades, visitas, expedientes, contratos);
    }

    private static Captacion encargo(long id, String operacion) {
        Captacion encargo = new Captacion();
        ReflectionTestUtils.setField(encargo, "id", id);
        encargo.setMotivoOperacion(operacion);
        return encargo;
    }

    private static FichaInteraccion ficha(List<VinculoFicha> vinculos, LocalDate fecha) {
        var origenes = vinculos.stream().map(v -> new VinculoActividad(
                TipoContexto.valueOf(v.tipo()), v.referencia(), v.versionReferencia(), null)).toList();
        var destinatario = new ParticipanteDeclarado(null, "Propietario declarado", "Titular buscado", null, null);
        var datos = new DatosInteraccion(origenes, destinatario, null,
                TipoActividad.LLAMADA, CanalComunicacion.TELEFONO, null,
                List.of(new EventoDeclarado("intento", EventoComunicacion.LLAMADA_ENTRANTE, null)),
                ResultadoInteraccion.SIN_RESPUESTA, null, "Resumen permitido", List.of(),
                null, null, "Hecho declarado", null);
        return new FichaInteraccion(701, 2, 3, fecha, Instant.parse("2026-07-10T16:00:00Z"), datos,
                vinculos, CatalogoInteracciones.VERSION, null, null, null, "Agente", List.of(), "Llamada perdida",null);
    }
}
