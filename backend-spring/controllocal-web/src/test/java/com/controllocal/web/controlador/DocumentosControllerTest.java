package com.controllocal.web.controlador;

import com.controllocal.service.Actor;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.archivo.AccesoArchivoService.ReferenciaAutorizada;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.web.almacen.AlmacenDocumentos;
import com.controllocal.web.almacen.AlmacenException;
import com.controllocal.web.http.ErrorAlmacenException;
import com.controllocal.web.http.RecursoNoEncontradoException;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentosControllerTest {
    private final AlmacenDocumentos almacen = mock(AlmacenDocumentos.class);
    private final AccesoArchivoService acceso = mock(AccesoArchivoService.class);
    private final RevalidacionDeSesion sesiones = mock(RevalidacionDeSesion.class);
    private final DocumentosController controller = new DocumentosController(almacen, acceso, sesiones);
    private static final Actor ACTOR = new Actor(1, 2, 3, Actor.AGENTE);
    private static final String REF = UUID.randomUUID().toString();
    private final ReferenciaAutorizada referencia = new ReferenciaAutorizada(UUID.fromString(REF),
            "DOCUMENTO_SOLICITUD", 4, 5, "CONSULTA_EXPEDIENTE", "tenant/1/SOL/privado.pdf", null,null);

    @BeforeEach void preparar() {
        when(sesiones.exigirVigente()).thenReturn(ACTOR);
        when(acceso.resolver(REF, ACTOR)).thenReturn(referencia);
        when(almacen.abrir(referencia.claveProveedor())).thenReturn(Optional.of(
                new AlmacenDocumentos.ArchivoDescargado(new byte[]{1, 2}, "privado.pdf", "application/pdf")));
    }

    @Test void revalidaSesionYRecursoAntesYDespuesDeCargarBytesSinCachePublico() {
        var respuesta = controller.contenido(REF);
        assertArrayEquals(new byte[]{1, 2}, respuesta.getBody());
        assertTrue(respuesta.getHeaders().getCacheControl().contains("no-store"));
        assertTrue(respuesta.getHeaders().getCacheControl().contains("private"));
        assertEquals("nosniff", respuesta.getHeaders().getFirst("X-Content-Type-Options"));
        var orden = inOrder(sesiones, acceso, almacen);
        orden.verify(sesiones).exigirVigente(); orden.verify(acceso).resolver(REF, ACTOR);
        orden.verify(almacen).abrir(referencia.claveProveedor()); orden.verify(sesiones).exigirVigente();
        orden.verify(acceso).revalidar(referencia, ACTOR);
        orden.verify(acceso).verificarContenido(eq(referencia),eq(new byte[]{1,2}));
        verify(almacen, never()).abrir(REF);
    }

    @Test void permisoInsuficienteNoLeeElProveedor() {
        when(acceso.resolver(REF, ACTOR)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class, () -> controller.contenido(REF));
        verifyNoInteractions(almacen);
    }

    @Test void ausenciaDeReferenciaNoSeInterpretaComoAccesoPorClave() {
        when(acceso.resolver(REF, ACTOR)).thenThrow(new NoEncontradoException("Archivo"));
        assertThrows(NoEncontradoException.class, () -> controller.contenido(REF));
        verifyNoInteractions(almacen);
    }

    @Test void revocacionDeSesionMientrasCargaNoEntregaElResultado() {
        when(sesiones.exigirVigente()).thenReturn(ACTOR).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class, () -> controller.contenido(REF));
        verify(acceso, never()).revalidar(referencia, ACTOR);
    }

    @Test void cambioDeAmbitoMientrasCargaNoEntregaBytesDeLaAutorizacionAnterior() {
        doThrow(new AccesoNoAutorizadoException()).when(acceso).revalidar(referencia, ACTOR);
        assertThrows(AccesoNoAutorizadoException.class, () -> controller.contenido(REF));
        verify(almacen).abrir(referencia.claveProveedor());
    }

    @Test void binarioAusenteNoSeSustituyeConUnArchivoFicticio() {
        when(almacen.abrir(referencia.claveProveedor())).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> controller.contenido(REF));
    }

    @Test void falloTecnicoNoSeConvierteEnAusenciaNiExponeLaClavePrivada() {
        when(almacen.abrir(referencia.claveProveedor())).thenThrow(new AlmacenException("ruta privada de fixture", null));
        var error = assertThrows(ErrorAlmacenException.class, () -> controller.contenido(REF));
        assertFalse(error.getMessage().contains("tenant/"));
        assertFalse(error.getMessage().contains("fixture"));
    }

    @Test void originalConIntegridadFallidaNoSeEntregaComoVersionValida() {
        when(acceso.verificarContenido(eq(referencia),any())).thenThrow(new IllegalStateException("integridad del original"));
        assertThrows(IllegalStateException.class,()->controller.contenido(REF));
        verify(acceso).revalidar(referencia,ACTOR);
    }

    @Test void archivoActivoSeConservaComoDescargaSinEjecutarloEnElOrigenDeCore() {
        when(almacen.abrir(referencia.claveProveedor())).thenReturn(java.util.Optional.of(
                new AlmacenDocumentos.ArchivoDescargado("<html>evidencia aportada</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8),"original.html","text/html")));
        var respuesta=controller.contenido(REF);
        assertTrue(respuesta.getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));
        assertEquals("sandbox; default-src 'none'",respuesta.getHeaders().getFirst("Content-Security-Policy"));
        assertTrue(respuesta.getHeaders().getCacheControl().contains("no-store"));
        assertNotNull(respuesta.getBody());verify(acceso).revalidar(referencia,ACTOR);
    }
}
