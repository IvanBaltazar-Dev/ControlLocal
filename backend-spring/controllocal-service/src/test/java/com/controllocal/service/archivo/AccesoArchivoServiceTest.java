package com.controllocal.service.archivo;

import com.controllocal.persistence.query.ReferenciaArchivo;
import com.controllocal.persistence.repositorio.ReferenciaArchivoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PerfilService;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.encargo.AccesoArchivoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.AccesoSolicitud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccesoArchivoServiceTest {
    private static final Actor AGENTE = new Actor(10, 20, 30, Actor.AGENTE);
    private static final UUID REF = UUID.randomUUID();
    private final ReferenciaArchivoRepository referencias = mock(ReferenciaArchivoRepository.class);
    private final AccesoSolicitud solicitudes = mock(AccesoSolicitud.class);
    private final AutoridadDePropiedad propiedades = mock(AutoridadDePropiedad.class);
    private final PerfilService perfiles = mock(PerfilService.class);
    private final AccesoArchivoEncargo encargos=mock(AccesoArchivoEncargo.class);
    private final AccesoArchivoService servicio = new AccesoArchivoService(referencias, solicitudes, propiedades, perfiles,encargos);

    @BeforeEach void sinVinculos() {
        when(referencias.actuales(anyLong(), any())).thenReturn(List.of());
        when(propiedades.puedeLeer(eq(AGENTE),anyLong())).thenReturn(true);
    }

    private ReferenciaArchivo referencia(String tipo, long id, long recurso, String clave) {
        var r = mock(ReferenciaArchivo.class);
        when(r.getReferencia()).thenReturn(REF); when(r.getTipo()).thenReturn(tipo);
        when(r.getIdReferencia()).thenReturn(id); when(r.getIdRecurso()).thenReturn(recurso);
        when(r.getClaveProveedor()).thenReturn(clave);
        when(r.getEstado()).thenReturn("A");
        return r;
    }

    private void actuales(ReferenciaArchivo... actuales) {
        when(referencias.actuales(10,REF)).thenReturn(List.of(actuales));
    }

    @Test void documentoUsaLaSolicitudActualSinTratarElSelectorComoPermiso() {
        var r = referencia("DOCUMENTO_SOLICITUD", 50, 60, "tenant/10/SOL/privado.pdf");
        actuales(r);
        var acceso = servicio.resolver(REF.toString(), AGENTE);
        assertEquals("CONSULTA_EXPEDIENTE", acceso.finalidad());
        assertEquals("tenant/10/SOL/privado.pdf", acceso.claveProveedor());
        verify(solicitudes).exigirConsultaActual(60, AGENTE);
        verifyNoInteractions(propiedades, perfiles);
    }

    @Test void fotoUsaLaLecturaCanonicaLigeraSinLeerLaFichaCompleta() {
        actuales(referencia("FOTO_PROPIEDAD", 50, 60, "foto.png"));
        assertEquals("CONSULTA_GALERIA", servicio.resolver(REF.toString(), AGENTE).finalidad());
        verify(propiedades).puedeLeer(AGENTE,60);
        verifyNoMoreInteractions(propiedades);
        verifyNoInteractions(solicitudes, perfiles);
    }

    @Test void perfilExigeLaReferenciaActualDeLaMismaPersonaContextual() {
        actuales(referencia("FOTO_PERFIL", 20, 20, "perfil.png"));
        when(perfiles.obtener(AGENTE)).thenReturn(new PerfilService.FichaPerfil("Nombre", null, null, REF.toString()));
        assertEquals("PERFIL_PROPIO", servicio.resolver(REF.toString(), AGENTE).finalidad());
        verifyNoInteractions(propiedades, solicitudes);
    }

    @Test void claveDeProveedorNoEsUnSelectorAdmitido() {
        assertThrows(ReglaNegocioException.class, () -> servicio.resolver("tenant/10/SOL/privado.pdf", AGENTE));
        verifyNoInteractions(referencias, propiedades, solicitudes, perfiles);
    }

    @Test void vinculoAusenteEs404YNoUnaRespuestaComercialVacia() {
        assertThrows(NoEncontradoException.class, () -> servicio.resolver(REF.toString(), AGENTE));
        verifyNoInteractions(propiedades, solicitudes, perfiles);
    }

    @Test void referenciaAmbiguaFallaAntesDeElegirUnVinculoLegible() {
        actuales(
                referencia("FOTO_PROPIEDAD", 50, 60, "foto.png"),
                referencia("DOCUMENTO_SOLICITUD", 70, 80, "privado.pdf"));
        assertThrows(ConflictoException.class, () -> servicio.resolver(REF.toString(), AGENTE));
        verifyNoInteractions(propiedades, solicitudes, perfiles);
    }

    @Test void colisionConPerfilAjenoNoEligeElDocumentoQuePodriaLeer() {
        actuales(
                referencia("DOCUMENTO_SOLICITUD", 50, 60, "documento.pdf"),
                referencia("FOTO_PERFIL", 21, 21, "perfil-ajeno.png"));
        assertThrows(ConflictoException.class, () -> servicio.resolver(REF.toString(), AGENTE));
        verifyNoInteractions(propiedades, solicitudes, perfiles);
    }

    @Test void administrarNoConcedeLecturaDeExpedienteOFotoComercial() {
        var admin = new Actor(10, 20, 31, Actor.TENANT_ADMIN);
        for (String tipo : List.of("DOCUMENTO_SOLICITUD", "FOTO_PROPIEDAD", "ARCHIVO_ENCARGO", "CARGA_TEMPORAL")) {
            actuales(referencia(tipo, 50, 60, "privado.pdf"));
            assertThrows(AccesoNoAutorizadoException.class, () -> servicio.resolver(REF.toString(), admin));
        }
        verifyNoInteractions(propiedades, solicitudes, perfiles);
    }

    @Test void otroPerfilNoSeAutorizaPorLaReferenciaConocida() {
        actuales(referencia("FOTO_PERFIL", 21, 21, "perfil.png"));
        assertThrows(NoEncontradoException.class, () -> servicio.resolver(REF.toString(), AGENTE));
        verifyNoInteractions(perfiles);
    }

    @Test void fotoDePerfilAnteriorSeConservaComoNoLocalizada() {
        actuales(referencia("FOTO_PERFIL", 20, 20, "perfil.png"));
        when(perfiles.obtener(AGENTE)).thenReturn(new PerfilService.FichaPerfil("Nombre", null, null, UUID.randomUUID().toString()));
        assertThrows(NoEncontradoException.class, () -> servicio.resolver(REF.toString(), AGENTE));
    }

    @Test void revalidarNoReutilizaElPermisoAnterior() {
        actuales(referencia("DOCUMENTO_SOLICITUD", 50, 60, "privado.pdf"));
        var autorizado = servicio.resolver(REF.toString(), AGENTE);
        doThrow(new AccesoNoAutorizadoException()).when(solicitudes).exigirConsultaActual(60, AGENTE);
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.revalidar(autorizado, AGENTE));
        verify(referencias, times(2)).actuales(10, REF);
    }

    @Test void cambiarVinculoOClaveMientrasSeCargabanBytesImpideEntregarlos() {
        actuales(referencia("FOTO_PROPIEDAD", 50, 60, "original.png"));
        var autorizado = servicio.resolver(REF.toString(), AGENTE);
        actuales(referencia("FOTO_PROPIEDAD", 50, 61, "otra.png"));
        assertThrows(ConflictoException.class, () -> servicio.revalidar(autorizado, AGENTE));
    }

    @Test void falloDeRepositorioNoSeConvierteEnAusencia() {
        when(referencias.actuales(10, REF)).thenThrow(new IllegalStateException("fixture de infraestructura"));
        assertThrows(IllegalStateException.class, () -> servicio.resolver(REF.toString(), AGENTE));
    }

    @Test void fotoRetiradaCuentaParaCardinalidadPeroNoSeEntregaDesdeGaleriaActiva() {
        var foto = referencia("FOTO_PROPIEDAD",50,60,"original.png");
        when(foto.getEstado()).thenReturn("I");
        actuales(foto);
        assertThrows(NoEncontradoException.class,()->servicio.resolver(REF.toString(),AGENTE));
        actuales(foto,referencia("DOCUMENTO_SOLICITUD",70,80,"doc.pdf"));
        assertThrows(ConflictoException.class,()->servicio.resolver(REF.toString(),AGENTE));
        verifyNoInteractions(propiedades,solicitudes,perfiles);
    }

    @Test void galeriaReautorizaEscalarSinEntidadJpaAnterior() {
        actuales(referencia("FOTO_PROPIEDAD",50,60,"original.png"));
        var permitido=servicio.resolver(REF.toString(),AGENTE);
        when(propiedades.puedeLeer(AGENTE,60)).thenReturn(false);
        assertThrows(NoEncontradoException.class,()->servicio.revalidar(permitido,AGENTE));
        verify(propiedades,times(2)).puedeLeer(AGENTE,60);
    }

    private com.controllocal.service.encargo.EncargoService.ArchivoLectura carga(long version) {
        return new com.controllocal.service.encargo.EncargoService.ArchivoLectura(REF,50,60L,null,30,
                "ACEPTACION_ENCARGO","tenant/10/carga/original.pdf","original.pdf","application/pdf","a".repeat(64),version);
    }
    @Test void cargaTemporalSeAutorizaSinDeclararlaVinculoConfirmado() {
        var temporal=referencia("CARGA_TEMPORAL",50,60,"tenant/10/carga/original.pdf");
        when(temporal.getEstado()).thenReturn("T");actuales(temporal);
        when(encargos.autorizarCarga(REF,AGENTE)).thenReturn(carga(0));
        var acceso=servicio.resolver(REF.toString(),AGENTE);
        assertEquals("CARGA_TEMPORAL",acceso.tipo());assertEquals("ACEPTACION_ENCARGO",acceso.finalidad());
        assertNotNull(acceso.huellaVinculo());assertEquals("a".repeat(64),acceso.huellaContenido());verify(encargos).autorizarCarga(REF,AGENTE);
        verify(encargos,never()).autorizar(any(),any());
    }
    @Test void cambioDeVersionDeLaCargaDuranteIoNoEntregaLaInstantaneaAnterior() {
        actuales(referencia("CARGA_TEMPORAL",50,60,"tenant/10/carga/original.pdf"));
        when(encargos.autorizarCarga(REF,AGENTE)).thenReturn(carga(0),carga(1));
        var antes=servicio.resolver(REF.toString(),AGENTE);
        assertThrows(ConflictoException.class,()->servicio.revalidar(antes,AGENTE));
        verify(encargos,times(2)).autorizarCarga(REF,AGENTE);
    }
    @Test void unaColisionTemporalConVinculoConfirmadoNoEligeElQuePodriaLeer() {
        actuales(referencia("CARGA_TEMPORAL",50,60,"carga.pdf"),referencia("ARCHIVO_ENCARGO",70,60,"vinculado.pdf"));
        assertThrows(ConflictoException.class,()->servicio.resolver(REF.toString(),AGENTE));
        verifyNoInteractions(encargos,solicitudes,propiedades,perfiles);
    }

    private AccesoArchivoService.ReferenciaAutorizada referenciaConHuella(String huella) {
        return new AccesoArchivoService.ReferenciaAutorizada(REF,"CARGA_TEMPORAL",50,60,"ACEPTACION_ENCARGO","privado","vinculo",huella);
    }
    @Test void integridadConocidaRechazaBytesCambiadosSinDeclararOtraVersion() {
        String esperada="2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        var ref=referenciaConHuella(esperada);
        assertEquals(esperada,servicio.verificarContenido(ref,"hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IllegalStateException.class,()->servicio.verificarContenido(ref,"alterado".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        verifyNoInteractions(referencias,encargos,propiedades,solicitudes,perfiles);
    }
    @Test void huellaNoRegistradaNoSeInventaComoPruebaHistorica() {
        var ref=referenciaConHuella(null);String actual=servicio.verificarContenido(ref,new byte[]{1,2,3});
        assertEquals(64,actual.length());assertNull(ref.huellaContenido());
        assertThrows(IllegalStateException.class,()->servicio.verificarContenido(ref,null));
    }
}
