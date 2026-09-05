package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.PrecioPropiedad;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.PrecioPropiedadRepository;
import com.controllocal.persistence.repositorio.PropiedadRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PrecioLocalService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AutoridadDePropiedad;
import com.controllocal.service.soporte.OperacionDelEncargo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * <b>La puerta MANUAL de la serie economica no escribe lo que produce otro</b>
 * (WP-A, D-N-2).
 *
 * <p>{@code POST /locales/{id}/precios} aceptaba los siete codigos del CHECK
 * porque dos de ellos —{@code O} ofertado y {@code C} cerrado— no los producia
 * nadie. Desde V90 los produce alguien, con su evidencia: {@code O} sale de una
 * vuelta de la mesa y viaja con {@code id_oferta}; {@code C} sale de un contrato
 * de alquiler o de un cierre de venta. Teclearlos aqui seria la <b>segunda
 * manera</b> de escribir el mismo hecho, y la segunda siempre se olvida de algo:
 * el {@code O} manual llega sin mesa, sin ronda y con {@code id_oferta} nulo,
 * indistinguible de una fila anterior a V90.
 *
 * <p>El resto del vocabulario sigue entrando por aqui. En particular
 * {@code P}: un anuncio publicado fuera de BROX es un hecho real que alguien
 * tiene que poder registrar, y ahi la mano no compite con ningun productor.
 */
class PrecioLocalServiceImplTest {

    private final PrecioPropiedadRepository precios = mock(PrecioPropiedadRepository.class);
    private final PropiedadRepository propiedades = mock(PropiedadRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final OperacionDelEncargo operaciones = mock(OperacionDelEncargo.class);
    private final AutoridadDePropiedad autoridad = mock(AutoridadDePropiedad.class);

    private final PrecioLocalServiceImpl servicio = new PrecioLocalServiceImpl(
            precios, propiedades, captaciones, operaciones, autoridad);

    private final Actor agente = new Actor(1L, 3L, 33L, Actor.AGENTE);

    @Test
    @DisplayName("un hito OFERTADO no se teclea: nace de una vuelta de la mesa")
    void elHitoOfertadoNoEntraPorLaPuertaManual() {
        ReglaNegocioException fallo = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(7L, datos(PrecioPropiedad.HITO_OFERTADO), agente));

        assertTrue(fallo.getMessage().contains("mesa de negociacion"),
                "el mensaje tiene que nombrar la puerta correcta, no limitarse a negar: " + fallo.getMessage());
        // Y no llega a tocar nada: ni la propiedad, ni el encargo, ni la serie.
        verifyNoInteractions(precios, propiedades, captaciones, operaciones, autoridad);
    }

    @Test
    @DisplayName("un hito CERRADO tampoco: nace de un contrato o de un cierre de venta")
    void elHitoDeCierreNoEntraPorLaPuertaManual() {
        ReglaNegocioException fallo = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrar(7L, datos(PrecioPropiedad.HITO_CIERRE), agente));

        assertTrue(fallo.getMessage().contains("cierre de venta"), fallo.getMessage());
        verifyNoInteractions(precios, propiedades, captaciones, operaciones, autoridad);
    }

    /**
     * La otra mitad de la regla, y la que impide que el arreglo se convierta en
     * un candado: lo que SI es de esta puerta sigue entrando. Se comprueba
     * llegando mas lejos que la guarda —el fallo ya no habla de hitos—, sin
     * montar el escenario entero de una propiedad con encargo.
     */
    @Test
    @DisplayName("autorizado y publicado siguen entrando por aqui")
    void loQueSiEsDeEstaPuertaSigueEntrando() {
        for (String hito : PrecioPropiedad.HITOS_PEDIDOS) {
            RuntimeException fallo = assertThrows(RuntimeException.class,
                    () -> servicio.registrar(7L, datos(hito), agente));
            assertTrue(!fallo.getMessage().contains("no se registra a mano"),
                    "el hito " + hito + " no lo produce nadie mas: tiene que poder registrarse. "
                            + fallo.getMessage());
        }
    }

    private static PrecioLocalService.DatosPrecio datos(String hito) {
        return new PrecioLocalService.DatosPrecio(hito, "USD", new BigDecimal("350000"),
                LocalDate.now(), "VENTA");
    }
}
