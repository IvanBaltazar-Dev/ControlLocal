package com.controllocal.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Casos de uso del requerimiento de cliente (perfil de busqueda). Los records
 * espejan el contrato CONGELADO (Dtos.RequerimientoRequest/Response de la v1).
 *
 * <p>Solo los requerimientos en estado ACTIVO alimentan el matching de cartera:
 * pausar uno saca al cliente de las recomendaciones sin perder su historial.
 *
 * <p>OJO con el cable (§1 del contrato): {@code estado} y {@code tipoInmueble}
 * viajan con el NOMBRE del enum, no con el codigo de 1 caracter del resto del
 * dominio. {@code distritos} viaja como lista de NOMBRES.
 */
public interface RequerimientoService {

    /** Espejo de RequerimientoRequest. */
    record DatosRequerimiento(Long idCliente, String rubro, String tipoInmueble, BigDecimal rentaMin,
                              BigDecimal rentaMax, String moneda, BigDecimal metrajeMin, BigDecimal metrajeMax,
                              BigDecimal frenteMinimo, String estado, String observaciones,
                              List<String> distritos) {
    }

    /** Espejo de RequerimientoResponse. */
    record FichaRequerimiento(Long id, Long idCliente, String rubro, String tipoInmueble, BigDecimal rentaMin,
                              BigDecimal rentaMax, String moneda, BigDecimal metrajeMin, BigDecimal metrajeMax,
                              BigDecimal frenteMinimo, String estado, String observaciones,
                              List<String> distritos, LocalDateTime fechaCreacion,
                              LocalDateTime fechaActualizacion) {
    }

    List<FichaRequerimiento> listarPorCliente(long idCliente, Actor actor);

    FichaRequerimiento crear(DatosRequerimiento datos, Actor actor);

    /** Si el request no trae idCliente se conserva el cliente actual (cable v1). */
    FichaRequerimiento actualizar(long id, DatosRequerimiento datos, Actor actor);

    FichaRequerimiento cambiarEstado(long id, String estado, Actor actor);
}
