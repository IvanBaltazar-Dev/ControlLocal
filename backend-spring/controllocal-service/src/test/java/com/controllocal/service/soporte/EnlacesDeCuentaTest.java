package com.controllocal.service.soporte;

import com.controllocal.domain.seguridad.TokenAcceso;
import com.controllocal.persistence.repositorio.TokenAccesoRepository;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.Optional;
import static com.controllocal.service.soporte.NotificadorIdentidad.Proposito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnlacesDeCuentaTest {
    private final TokenAccesoRepository tokens=mock(TokenAccesoRepository.class);
    private final NotificadorIdentidad canal=mock(NotificadorIdentidad.class);
    private final Instant ahora=Instant.parse("2026-10-27T18:00:00Z");
    private final EnlacesDeCuenta enlaces=new EnlacesDeCuenta(tokens,canal,Clock.fixed(ahora,ZoneOffset.UTC));
    private static final String SECRETO="s".repeat(43);
    @Test void altaConservaSoloHashYEntregaSecretoExclusivamenteAlCanal() {
        enlaces.bloquearAlta("persona@example.test");enlaces.emitirAlta("persona@example.test");
        var fila=ArgumentCaptor.forClass(TokenAcceso.class);
        var envio=ArgumentCaptor.forClass(NotificadorIdentidad.TokenEmitido.class);
        verify(tokens).saveAndFlush(fila.capture());
        verify(canal).enviar(eq(Proposito.ALTA_CORREO),any(),envio.capture());
        assertNull(fila.getValue().getIdCuenta());assertEquals("ALTA_CORREO",fila.getValue().getTipo());
        assertEquals(Duration.ofMinutes(30),Duration.between(fila.getValue().getCreadoEn(),fila.getValue().getExpiraEn()));
        assertTrue(fila.getValue().getHashToken().equals(Idempotencia.huellaExacta(envio.getValue().token())));
        assertFalse(envio.getValue().toString().contains(envio.getValue().token()));
        verify(tokens).bloquearAltasDeCorreo("persona@example.test");
        verify(tokens).invalidarAltasVivas("persona@example.test",OffsetDateTime.ofInstant(ahora,ZoneOffset.UTC));
    }
    @Test void cambioYRecuperacionNoSeInvalidanMutuamente() {
        enlaces.emitir(Proposito.CAMBIO_CORREO,42,"nuevo@example.test",null);
        verify(tokens).invalidarVivosDe(42,"CAMBIO_CORREO",OffsetDateTime.ofInstant(ahora,ZoneOffset.UTC));
        verify(tokens,never()).invalidarVivosDe(eq(42L),eq("RECUPERACION"),any());
        var fila=ArgumentCaptor.forClass(TokenAcceso.class);verify(tokens).saveAndFlush(fila.capture());
        assertEquals(42,fila.getValue().getIdCuenta());assertEquals("nuevo@example.test",fila.getValue().getCorreoObjetivo());
    }
    @ParameterizedTest @EnumSource(Proposito.class)
    void unPropositoNoPuedeCanjearElEnlaceDeOtro(Proposito proposito) {
        var fila=fila();when(tokens.buscarPorHashYTipo(Idempotencia.huellaExacta(SECRETO),EnlacesDeCuenta.tipo(proposito)))
                .thenReturn(Optional.of(fila));
        assertSame(fila,enlaces.buscar(SECRETO,proposito));
        for(var otro:Proposito.values())if(otro!=proposito)
            assertThrows(ReglaNegocioException.class,()->enlaces.buscar(SECRETO,otro));
    }
    @Test void casDefinitivoNiegaEnlaceQueVencioMientrasEsperabaSinRenovarlo() {
        var fila=fila();when(tokens.consumirEnlace(7,"RECUPERACION")).thenReturn(0);
        assertThrows(ReglaNegocioException.class,()->enlaces.consumir(fila,Proposito.RECUPERACION));
        assertNull(fila.getUsadoEn());verify(tokens,never()).save(any());
    }
    @Test void referenciaMalformadaNoConsultaNiInterpretaTextoLibre() {
        for(String valor:new String[]{null,"", "true","token arbitrario"})
            assertThrows(ReglaNegocioException.class,()->enlaces.buscar(valor,Proposito.ALTA_CORREO));
        verifyNoInteractions(tokens,canal);
    }
    @Test void correoSeNormalizaSinPermitirInyeccionDeCabecera() {
        assertEquals("persona@example.test",EnlacesDeCuenta.correo("  Persona@Example.Test  "));
        for(String valor:new String[]{"","sin-arroba","a@@b","a@","@b","a@b\r\nBcc:x@y"})
            assertThrows(ReglaNegocioException.class,()->EnlacesDeCuenta.correo(valor));
        assertFalse(new NotificadorIdentidad.Destino(42L,null,"persona@example.test").toString().contains("persona@example.test"));
    }
    private TokenAcceso fila() {
        var t=new TokenAcceso();ReflectionTestUtils.setField(t,"id",7L);t.setCreadoEn(OffsetDateTime.ofInstant(ahora,ZoneOffset.UTC));
        t.setExpiraEn(t.getCreadoEn().plusMinutes(30));return t;
    }
}
