package com.controllocal.model.comercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.comercial.enums.FuenteOrigen;
import com.controllocal.model.comercial.enums.EstadoVisita;
import com.controllocal.model.comercial.enums.ObjecionVisita;
import com.controllocal.model.comercial.enums.OpinionPrecio;
import com.controllocal.model.comercial.enums.OperacionRequerimiento;
import com.controllocal.model.comercial.enums.ProximaAccionVisita;
import com.controllocal.model.comercial.enums.ResultadoInteraccion;
import com.controllocal.model.comercial.enums.TipoDocumentoSolicitud;
import com.controllocal.model.inmueble.Distrito;
import com.controllocal.model.inmueble.LocalComercial;

class ModeloOperativoTest {

    @Test
    void oportunidadInicializaAtribucionSinRomperElFlujoAnterior() {
        OportunidadComercial oportunidad = new OportunidadComercial();

        oportunidad.abrir();

        assertEquals(EstadoOportunidadComercial.ABIERTA, oportunidad.getEstado());
        assertEquals(FuenteOrigen.OTRO, oportunidad.getFuenteOrigen());
        assertNotNull(oportunidad.getFechaRegistro());
        assertEquals(oportunidad.getFechaRegistro(), oportunidad.getFechaPrimeraConsulta());
    }

    @Test
    @SuppressWarnings("deprecation")
    void documentoLegacySeConvierteAlCatalogo() {
        DocumentoSolicitud documento = new DocumentoSolicitud();

        documento.setTipoDocumento(TipoDocumentoSolicitud.FICHA_RUC);

        assertNotNull(documento.getTipoDocumentoRequerido());
        assertEquals(2L, documento.getTipoDocumentoRequerido().getIdTipoDocumentoRequerido());
        assertEquals(TipoDocumentoSolicitud.FICHA_RUC, documento.getTipoDocumento());
    }

    @Test
    void requerimientoCopiaLaListaDeDistritos() {
        RequerimientoCliente requerimiento = new RequerimientoCliente();
        Distrito distrito = new Distrito(1L, "Miraflores", "Lima", true);
        List<Distrito> origen = new java.util.ArrayList<>(List.of(distrito));

        requerimiento.setDistritos(origen);
        origen.clear();

        assertEquals(1, requerimiento.getDistritos().size());
    }

    @Test
    void inmuebleYaNoContieneEstadoDePublicacion() {
        assertNull(buscarCampo(LocalComercial.class, "estadoPublicacion"));
        assertNotNull(buscarCampo(LocalComercial.class, "frente"));
    }

    @Test
    void operacionComercialSoloAdmiteAlquiler() {
        Captacion captacion = new Captacion();

        assertEquals(OperacionRequerimiento.ALQUILER, captacion.getMotivoOperacion());
        assertThrows(IllegalArgumentException.class,
                () -> OperacionRequerimiento.fromCodigo("C"));
    }

    @Test
    void visitaDebeMarcarseRealizadaAntesDeRegistrarResultado() {
        Visita visita = new Visita();
        visita.programar();

        assertThrows(IllegalStateException.class,
                () -> visita.registrarResultado(ResultadoInteraccion.INTERESADO));

        visita.marcarRealizada();
        visita.registrarResultado(ResultadoInteraccion.INTERESADO);

        assertEquals(EstadoVisita.REALIZADA, visita.getEstado());
        assertEquals(ResultadoInteraccion.INTERESADO, visita.getResultado());
        assertEquals(false, visita.esModificable());
        assertEquals(false, visita.admiteResultado());
    }

    @Test
    void visitaNoRealizadaNoAdmiteResultado() {
        Visita visita = new Visita();
        visita.programar();

        visita.marcarNoRealizada("El cliente no asistio");

        assertEquals(EstadoVisita.NO_REALIZADA, visita.getEstado());
        assertThrows(IllegalStateException.class,
                () -> visita.registrarResultado(ResultadoInteraccion.DESCARTADO));
    }

    @Test
    void visitaCanceladaONoRealizadaLimpiaDesenlaceComercial() {
        Visita cancelada = visitaConDesenlace();
        cancelada.cancelar("Cliente cancelo");

        assertEquals(EstadoVisita.CANCELADA, cancelada.getEstado());
        assertNull(cancelada.getResultado());
        assertNull(cancelada.getNivelInteres());
        assertNull(cancelada.getObjecionPrincipal());
        assertNull(cancelada.getOpinionPrecio());
        assertNull(cancelada.getProximaAccion());

        Visita noRealizada = visitaConDesenlace();
        noRealizada.marcarNoRealizada("No asistio");

        assertEquals(EstadoVisita.NO_REALIZADA, noRealizada.getEstado());
        assertNull(noRealizada.getResultado());
        assertNull(noRealizada.getNivelInteres());
        assertNull(noRealizada.getObjecionPrincipal());
        assertNull(noRealizada.getOpinionPrecio());
        assertNull(noRealizada.getProximaAccion());
    }

    @Test
    void descarteEliminaNivelDeInteres() {
        Visita visita = new Visita();
        visita.programar();
        visita.marcarRealizada();
        visita.setNivelInteres(4);

        visita.registrarResultado(ResultadoInteraccion.DESCARTADO);

        assertNull(visita.getNivelInteres());
    }

    private static java.lang.reflect.Field buscarCampo(Class<?> tipo, String nombre) {
        try {
            return tipo.getDeclaredField(nombre);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Visita visitaConDesenlace() {
        Visita visita = new Visita();
        visita.programar();
        visita.setResultado(ResultadoInteraccion.INTERESADO);
        visita.setNivelInteres(4);
        visita.setObjecionPrincipal(ObjecionVisita.PRECIO);
        visita.setOpinionPrecio(OpinionPrecio.ALTO);
        visita.setProximaAccion(ProximaAccionVisita.SEGUIMIENTO);
        return visita;
    }
}
