package com.controllocal.bl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.ComisionLiquidacion;
import com.controllocal.model.comercial.enums.EstadoComision;
import com.controllocal.model.comercial.enums.FormaPago;

/**
 * Liquidacion de comision de un contrato (tabla comision_liquidacion). La creacion
 * ocurre dentro del cierre del alquiler (ContratoAlquilerBusinessLogic) solo con el
 * monto bruto y estado PENDIENTE, sin reparto. Esta capa cubre las dos acciones de
 * gobierno del broker supervisor: define el monto del agente (Etapa 2) y registra el
 * cobro (Cobrada/Anulada). El administrador no opera comisiones (solo lectura).
 */
public interface ComisionLiquidacionBusinessLogic {

    List<ComisionLiquidacion> listarPorContrato(Long idContrato);

    /**
     * Todas las liquidaciones (orden por id). Pensado para vistas de lista que cruzan
     * muchos contratos con su comision en memoria, evitando una consulta por contrato.
     */
    List<ComisionLiquidacion> listarTodos();

    Optional<ComisionLiquidacion> buscarPorId(Long idComision);

    /**
     * El broker supervisor define el monto real del agente; el sistema calcula el
     * monto de la empresa (bruto - agente). Solo se permite mientras la liquidacion
     * sigue PENDIENTE (aun no cobrada ni anulada).
     */
    ComisionLiquidacion asignarMontoAgente(Long idComision, BigDecimal montoAgente);

    /**
     * El broker supervisor registra el desenlace del cobro: COBRADA (requiere
     * fecha de cobro y forma de pago) o ANULADA. PARCIAL solo se conserva como
     * historico hasta tener abonos detallados.
     */
    ComisionLiquidacion registrarCobro(Long idComision, EstadoComision estado,
            LocalDate fechaCobro, FormaPago formaPago);
}
