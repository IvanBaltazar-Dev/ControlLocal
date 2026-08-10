package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PublicacionRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PublicacionService.DatosPublicacion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E0.2 — la renta que el mercado VE queda en el historico.
 *
 * <p>Antes, {@code renta_publicada} mutaba en su sitio desde tres productores y
 * el valor anterior se perdia. El hito {@code U} no cubre esto: ese es el precio
 * que el propietario autoriza en privado, y la elasticidad se mide contra el que
 * el mercado vio.
 */
class PublicacionServiceImplTest {

    private final PublicacionRepository publicaciones = mock(PublicacionRepository.class);
    private final PrecioPropiedadRepository precios = mock(PrecioPropiedadRepository.class);

    private final PublicacionServiceImpl service =
            new PublicacionServiceImpl(publicaciones, precios);

    private static final long ORG = 1L;
    private static final long PROPIEDAD = 7L;

    private final Actor agente = new Actor(ORG, 3L, 30L, "AGENTE");

    private static DatosPublicacion datos(BigDecimal renta, String moneda, String estado) {
        return new DatosPublicacion(Publicacion.CANAL_WEB_PROPIA, null, renta, moneda,
                null, null, estado);
    }

    private static Publicacion publicacion(String estado, BigDecimal renta, String moneda) {
        Publicacion p = new Publicacion();
        p.setOrganizacionId(ORG);
        p.setIdPropiedad(PROPIEDAD);
        p.setCanal(Publicacion.CANAL_WEB_PROPIA);
        p.setVersionAnuncio(1);
        p.setRentaPublicada(renta);
        p.setMoneda(moneda);
        p.setEstado(estado);
        p.setFechaPublicacion(OffsetDateTime.now());
        return p;
    }

    private static PrecioPropiedad hitoPublicado(BigDecimal monto, String moneda) {
        PrecioPropiedad hito = new PrecioPropiedad();
        hito.setOrganizacionId(ORG);
        hito.setIdPropiedad(PROPIEDAD);
        hito.setHito(PrecioPropiedad.HITO_PUBLICADO);
        hito.setMonto(monto);
        hito.setMoneda(moneda);
        hito.setFecha(LocalDate.now());
        return hito;
    }

    private ArgumentCaptor<PrecioPropiedad> hitosGuardados() {
        ArgumentCaptor<PrecioPropiedad> captor = ArgumentCaptor.forClass(PrecioPropiedad.class);
        verify(precios).save(captor.capture());
        return captor;
    }

    @Test
    void publicarUnaRentaDejaSuHito() {
        service.crear(PROPIEDAD, datos(new BigDecimal("5200.00"), "PEN",
                Publicacion.ESTADO_PUBLICADO), agente);

        PrecioPropiedad hito = hitosGuardados().getValue();
        assertEquals(PrecioPropiedad.HITO_PUBLICADO, hito.getHito());
        assertEquals(new BigDecimal("5200.00"), hito.getMonto());
        assertEquals("PEN", hito.getMoneda());
        assertEquals(PROPIEDAD, hito.getIdPropiedad());
        // El tenant sale de la publicacion, no del actor: la misma regla que la
        // auditoria de estados.
        assertEquals(ORG, hito.getOrganizacionId());
        assertEquals(LocalDate.now(), hito.getFecha());
    }

    /**
     * Un borrador no lo ve nadie. Anotar su renta como "publicada" meteria en la
     * serie precios que nunca existieron para el mercado, y la elasticidad se
     * calcularia contra cifras imaginarias.
     */
    @Test
    void unBorradorNoDejaHitoPorqueElMercadoNoLoVe() {
        service.crear(PROPIEDAD, datos(new BigDecimal("5200.00"), "PEN",
                Publicacion.ESTADO_BORRADOR), agente);

        verify(precios, never()).save(any());
    }

    /** El instante en que un borrador se publica ES la primera vez que se ve. */
    @Test
    void publicarUnBorradorDejaElHitoEnEseInstante() {
        when(publicaciones.findById(11L))
                .thenReturn(Optional.of(publicacion(Publicacion.ESTADO_BORRADOR,
                        new BigDecimal("4800.00"), "USD")));

        service.cambiarEstado(11L, Publicacion.ESTADO_PUBLICADO);

        PrecioPropiedad hito = hitosGuardados().getValue();
        assertEquals(new BigDecimal("4800.00"), hito.getMonto());
        assertEquals("USD", hito.getMoneda());
    }

    /**
     * El caso que obliga a deduplicar: {@code LocalComercialServiceImpl} llama a
     * {@code sincronizar} en TODA edicion de local, cambie o no el precio. Sin
     * este filtro, editar el metraje escribiria un hito de renta.
     */
    @Test
    void sincronizarSinCambioEconomicoNoDuplicaElHito() {
        when(publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(PROPIEDAD))
                .thenReturn(List.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("5200.00"), "PEN")));
        when(precios.findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                PROPIEDAD, PrecioPropiedad.HITO_PUBLICADO))
                // Mismo importe con OTRA escala: 5200 y 5200.00 son el mismo
                // precio. Con equals de BigDecimal esto duplicaria el hito.
                .thenReturn(Optional.of(hitoPublicado(new BigDecimal("5200"), "PEN")));

        service.sincronizar(PROPIEDAD, "LOC-0100", new BigDecimal("5200.00"), "PEN",
                Publicacion.ESTADO_PUBLICADO, agente);

        verify(precios, never()).save(any());
    }

    @Test
    void sincronizarConRentaNuevaDejaOtroHito() {
        when(publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(PROPIEDAD))
                .thenReturn(List.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("5200.00"), "PEN")));
        when(precios.findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                PROPIEDAD, PrecioPropiedad.HITO_PUBLICADO))
                .thenReturn(Optional.of(hitoPublicado(new BigDecimal("5200.00"), "PEN")));

        service.sincronizar(PROPIEDAD, "LOC-0100", new BigDecimal("4900.00"), "PEN",
                Publicacion.ESTADO_PUBLICADO, agente);

        assertEquals(new BigDecimal("4900.00"), hitosGuardados().getValue().getMonto());
    }

    /**
     * Mismo numero, otra moneda, es otro precio. En una cartera donde el 60 % de
     * las rentas se cotiza en dolares, tratarlo como "sin cambio" arruinaria la
     * serie.
     */
    @Test
    void cambiarSoloLaMonedaCuentaComoRentaNueva() {
        when(publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(PROPIEDAD))
                .thenReturn(List.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("1500.00"), "PEN")));
        when(precios.findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
                PROPIEDAD, PrecioPropiedad.HITO_PUBLICADO))
                .thenReturn(Optional.of(hitoPublicado(new BigDecimal("1500.00"), "PEN")));

        service.sincronizar(PROPIEDAD, "LOC-0100", new BigDecimal("1500.00"), "USD",
                Publicacion.ESTADO_PUBLICADO, agente);

        PrecioPropiedad hito = hitosGuardados().getValue();
        assertEquals("USD", hito.getMoneda());
        assertEquals(new BigDecimal("1500.00"), hito.getMonto());
    }

    /** Un local en borrador sin publicacion previa no crea nada: tampoco hito. */
    @Test
    void sincronizarUnBorradorSinPublicacionPreviaNoEscribeNada() {
        when(publicaciones.findByIdPropiedadOrderByFechaPublicacionDesc(anyLong()))
                .thenReturn(List.of());

        service.sincronizar(PROPIEDAD, "LOC-0100", new BigDecimal("5200.00"), "PEN",
                Publicacion.ESTADO_BORRADOR, agente);

        verify(publicaciones, never()).save(any());
        verify(precios, never()).save(any());
    }

    @Test
    void editarLaRentaDeUnAnuncioPublicadoDejaSuHito() {
        when(publicaciones.findById(eq(11L)))
                .thenReturn(Optional.of(publicacion(Publicacion.ESTADO_PUBLICADO,
                        new BigDecimal("5200.00"), "PEN")));

        service.actualizar(11L, datos(new BigDecimal("4700.00"), "PEN", null));

        assertEquals(new BigDecimal("4700.00"), hitosGuardados().getValue().getMonto());
    }
}
