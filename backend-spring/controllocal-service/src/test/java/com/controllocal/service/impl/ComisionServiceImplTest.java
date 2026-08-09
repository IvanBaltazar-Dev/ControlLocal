package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.HistorialEstado;
import com.controllocal.domain.comercial.ComisionLiquidacion;
import com.controllocal.domain.comercial.ComisionMovimiento;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.persistence.repositorio.ComisionLiquidacionRepository;
import com.controllocal.persistence.repositorio.ComisionMovimientoRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.ComisionService.FichaComision;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Blinda la aritmetica de la comision y los mensajes de los dos gates del
 * broker (ContratosRest + ComisionLiquidacionBusinessLogicImpl).
 *
 * <p>Blinda que la moneda se herede de la renta y que no haya reparto
 * automatico: la liquidacion nace con la bruta y el neto en NULL.
 */
class ComisionServiceImplTest {

    private static final long ORG = 1L;
    private static final long CONTRATO = 40L;

    private final ComisionLiquidacionRepository comisiones = mock(ComisionLiquidacionRepository.class);
    private final ComisionMovimientoRepository movimientos = mock(ComisionMovimientoRepository.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);
    private final List<ComisionMovimiento> movimientosGuardados = new ArrayList<>();

    private final ComisionServiceImpl service = new ComisionServiceImpl(
            comisiones, movimientos, new Transiciones(historial), mock(AlertaService.class));

    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");

    ComisionServiceImplTest() {
        when(movimientos.save(any(ComisionMovimiento.class))).thenAnswer(inv -> {
            ComisionMovimiento guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", (long) movimientosGuardados.size() + 1L);
            movimientosGuardados.add(guardado);
            return guardado;
        });
        when(movimientos.findByOrganizacionIdAndLiquidacionIdOrderByFechaAscIdAsc(ORG, 60L))
                .thenAnswer(inv -> List.copyOf(movimientosGuardados));
    }

    // ------------------------------------------------------------------
    // Alta con el contrato (efecto 2 de la cascada)
    // ------------------------------------------------------------------

    @Test
    void cienPorCientoEquivaleAUnaRentaYConservaSuMoneda() {
        when(comisiones.save(any(ComisionLiquidacion.class))).thenAnswer(inv -> {
            ComisionLiquidacion guardada = inv.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 60L);
            return guardada;
        });

        // 100 % de 9000 = una renta completa.
        FichaComision ficha = service.crearPendiente(
                contrato(), new BigDecimal("100.00"), new BigDecimal("9000.00"), "PEN", broker);

        assertEquals(new BigDecimal("9000.00"), ficha.monto());
        assertEquals("PEN", ficha.moneda());
        assertEquals(ComisionLiquidacion.PENDIENTE, ficha.estado());
        assertNull(ficha.montoAgente());
        assertNull(ficha.montoEmpresa());
        assertEquals(CONTRATO, ficha.idContrato());
        // Nacer no es transicionar.
        verifyNoInteractions(historial);

        ArgumentCaptor<ComisionLiquidacion> guardada = ArgumentCaptor.forClass(ComisionLiquidacion.class);
        verify(comisiones).save(guardada.capture());
        assertEquals(ORG, guardada.getValue().getOrganizacionId());
    }

    @Test
    void laBrutaRedondeaADosDecimales() {
        when(comisiones.save(any(ComisionLiquidacion.class))).thenAnswer(inv -> inv.getArgument(0));

        // 3.5 % de 1234.55 = 43.20925 -> 43.21 (HALF_UP)
        assertEquals(new BigDecimal("43.21"), service.crearPendiente(
                contrato(), new BigDecimal("3.50"), new BigDecimal("1234.55"), "USD", broker).monto());
    }

    @Test
    void rechazaPorcentajesAusentesOImposibles() {
        assertThrows(ReglaNegocioException.class, () -> service.crearPendiente(
                contrato(), null, new BigDecimal("9000.00"), "PEN", broker));
        assertThrows(ReglaNegocioException.class, () -> service.crearPendiente(
                contrato(), new BigDecimal("200.01"), new BigDecimal("9000.00"), "PEN", broker));
    }

    // ------------------------------------------------------------------
    // Gate 1: asignar el monto del agente
    // ------------------------------------------------------------------

    @Test
    void sinLiquidacionResponde404ConElRecursoDelCable() {
        when(comisiones.porContrato(ORG, CONTRATO)).thenReturn(Optional.empty());

        NoEncontradoException error = assertThrows(NoEncontradoException.class,
                () -> service.asignarMontoAgente(CONTRATO, new BigDecimal("100"), broker));
        assertEquals("Liquidacion de comision no encontrado.", error.getMessage());
    }

    @Test
    void elMontoDelAgenteNoPuedeSerNegativo() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.asignarMontoAgente(CONTRATO, new BigDecimal("-1"), broker));
        assertEquals("El monto del agente debe ser cero o positivo.", error.getMessage());
        verifyNoInteractions(comisiones);
    }

    @Test
    void elMontoDelAgenteNoPuedeSuperarLaBruta() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), null);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.asignarMontoAgente(CONTRATO, new BigDecimal("450.01"), broker));
        assertEquals("El monto del agente no puede superar la comision bruta.", error.getMessage());
    }

    @Test
    void soloSeAsignaMientrasLaComisionEstaPendiente() {
        liquidacion(ComisionLiquidacion.COBRADA, new BigDecimal("450.00"), new BigDecimal("200.00"));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.asignarMontoAgente(CONTRATO, new BigDecimal("100.00"), broker));
        assertEquals("Solo se puede asignar el monto del agente mientras la comision esta Pendiente.",
                error.getMessage());
    }

    @Test
    void asignarElMontoDelAgenteCalculaSoloElDeLaEmpresa() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), null);

        FichaComision ficha = service.asignarMontoAgente(CONTRATO, new BigDecimal("180.00"), broker);

        assertEquals(new BigDecimal("180.00"), ficha.montoAgente());
        assertEquals(new BigDecimal("270.00"), ficha.montoEmpresa());
        // Asignar el reparto no cambia el estado: sigue PENDIENTE hasta el cobro.
        assertEquals(ComisionLiquidacion.PENDIENTE, ficha.estado());
        verifyNoInteractions(historial);
    }

    // ------------------------------------------------------------------
    // Gate 2: registrar el cobro
    // ------------------------------------------------------------------

    @Test
    void unEstadoDeCobroDesconocidoRespondeElMensajeV1() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), new BigDecimal("180.00"));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobro(CONTRATO, "PAGADA", LocalDate.now(), "EFECTIVO", broker));
        assertEquals("Estado de cobro invalido.", error.getMessage());
    }

    @Test
    void unEstadoValidoQueNoEsDeCobroTieneSuPropioMensaje() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), new BigDecimal("180.00"));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobro(CONTRATO, "R", LocalDate.now(), "EFECTIVO", broker));
        assertEquals("El cobro solo admite los estados Cobrada o Anulada.", error.getMessage());
    }

    @Test
    void elCobroNormalizaLaCajaDelEstadoYDeLaFormaDePago() {
        // El contrato normalizado conserva trim/caja, pero transporta el codigo.
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), new BigDecimal("180.00"));

        FichaComision ficha = service.registrarCobro(
                CONTRATO, " c ", LocalDate.now(), " transferencia ", broker);

        assertEquals(ComisionLiquidacion.COBRADA, ficha.estado());
        assertEquals("TRANSFERENCIA", ficha.formaPago());
    }

    @Test
    void unaFormaDePagoDesconocidaRespondeElMensajeV1() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), new BigDecimal("180.00"));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobro(CONTRATO, "C", LocalDate.now(), "YAPE", broker));
        assertEquals("Forma de pago invalida.", error.getMessage());
    }

    @Test
    void noSeCobraAntesDeRepartir() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), null);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobro(CONTRATO, "C", LocalDate.now(), "EFECTIVO", broker));
        assertEquals("Antes de cobrar, el broker supervisor debe asignar el monto del agente.",
                error.getMessage());
    }

    @Test
    void cobrarExigeFechaNoFuturaYFormaDePago() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), new BigDecimal("180.00"));

        ReglaNegocioException sinFecha = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobro(CONTRATO, "C", null, "EFECTIVO", broker));
        assertEquals("Registra la fecha de cobro.", sinFecha.getMessage());

        ReglaNegocioException futura = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobro(CONTRATO, "C", LocalDate.now().plusDays(1),
                        "EFECTIVO", broker));
        assertEquals("La fecha de cobro no puede ser futura.", futura.getMessage());

        ReglaNegocioException sinForma = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobro(CONTRATO, "C", LocalDate.now(), null, broker));
        assertEquals("Registra la forma de pago.", sinForma.getMessage());
    }

    @Test
    void cobrarGuardaLosDatosDelCobroYAudita() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), new BigDecimal("180.00"));
        LocalDate hoy = LocalDate.now();

        FichaComision ficha = service.registrarCobro(CONTRATO, "C", hoy, "TRANSFERENCIA", broker);

        assertEquals(ComisionLiquidacion.COBRADA, ficha.estado());
        assertEquals(hoy, ficha.fechaCobro());
        assertEquals("TRANSFERENCIA", ficha.formaPago());

        HistorialEstado evento = eventoAuditado();
        assertEquals("COMISION_LIQUIDACION", evento.getEntidadTipo());
        assertEquals(ComisionLiquidacion.PENDIENTE, evento.getEstadoAnterior());
        assertEquals(ComisionLiquidacion.COBRADA, evento.getEstadoNuevo());
        assertEquals("BROKER", evento.getTipoRolActor());
    }

    @Test
    void anularNoConservaDatosDeCobro() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"), new BigDecimal("180.00"));

        FichaComision ficha = service.registrarCobro(
                CONTRATO, "A", LocalDate.now(), "EFECTIVO", broker);

        assertEquals(ComisionLiquidacion.ANULADA, ficha.estado());
        assertNull(ficha.fechaCobro());
        assertNull(ficha.formaPago());
        assertEquals(ComisionLiquidacion.ANULADA, eventoAuditado().getEstadoNuevo());
    }

    @Test
    void unaComisionYaResueltaNoAdmiteOtroCobro() {
        liquidacion(ComisionLiquidacion.ANULADA, new BigDecimal("450.00"), new BigDecimal("180.00"));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.registrarCobro(CONTRATO, "C", LocalDate.now(), "EFECTIVO", broker));
        assertEquals("La comision ya tiene un cobro registrado (Cobrada o Anulada).",
                error.getMessage());
    }

    @Test
    void cobroParcialDejaEvidenciaYSaldoSinMarcarCobroTotal() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"),
                new BigDecimal("180.00"));

        FichaComision ficha = service.registrarMovimiento(CONTRATO, ComisionMovimiento.COBRO,
                new BigDecimal("200.00"), "PEN", LocalDate.now(),
                "TRANSFERENCIA", "Primer abono", null, broker);

        assertEquals(ComisionLiquidacion.PARCIAL, ficha.estado());
        assertEquals(new BigDecimal("200.00"), ficha.montoCobrado());
        assertEquals(new BigDecimal("250.00"), ficha.saldoCobro());
        assertEquals(1, movimientosGuardados.size());
        assertEquals(ComisionMovimiento.COBRO, movimientosGuardados.getFirst().getTipo());
    }

    @Test
    void pagosAlAgenteSonIndependientesDelCobroYPuedenSerParciales() {
        liquidacion(ComisionLiquidacion.PENDIENTE, new BigDecimal("450.00"),
                new BigDecimal("180.00"));

        FichaComision parcial = service.registrarMovimiento(CONTRATO,
                ComisionMovimiento.PAGO_AGENTE, new BigDecimal("80.00"), "PEN",
                LocalDate.now(), "TRANSFERENCIA", "Adelanto", null, broker);
        FichaComision total = service.registrarMovimiento(CONTRATO,
                ComisionMovimiento.PAGO_AGENTE, new BigDecimal("100.00"), "PEN",
                LocalDate.now(), "TRANSFERENCIA", "Saldo", null, broker);

        assertEquals(BigDecimal.ZERO, parcial.montoCobrado());
        assertEquals(new BigDecimal("80.00"), parcial.montoPagadoAgente());
        assertEquals(new BigDecimal("100.00"), parcial.saldoPagoAgente());
        assertEquals(new BigDecimal("180.00"), total.montoPagadoAgente());
        assertEquals(new BigDecimal("0.00"), total.saldoPagoAgente());
        assertEquals(ComisionLiquidacion.PENDIENTE, total.estado());
        assertEquals(List.of(ComisionMovimiento.PAGO_AGENTE, ComisionMovimiento.PAGO_AGENTE),
                movimientosGuardados.stream().map(ComisionMovimiento::getTipo).toList());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void liquidacion(String estado, BigDecimal bruta, BigDecimal montoAgente) {
        ComisionLiquidacion comision = new ComisionLiquidacion();
        comision.setOrganizacionId(ORG);
        comision.setContrato(contrato());
        comision.setMonto(bruta);
        comision.setMoneda("PEN");
        if (montoAgente != null) {
            comision.asignarMontoAgente(montoAgente);
        }
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(comision, estado);
        ReflectionTestUtils.setField(comision, "id", 60L);
        when(comisiones.porContrato(ORG, CONTRATO)).thenReturn(Optional.of(comision));
        when(comisiones.save(any(ComisionLiquidacion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ContratoAlquiler contrato() {
        ContratoAlquiler contrato = new ContratoAlquiler();
        contrato.setOrganizacionId(ORG);
        contrato.setFechaCierre(LocalDate.now());
        new Transiciones(mock(HistorialEstadoRepository.class))
                .iniciar(contrato, ContratoAlquiler.VIGENTE);
        ReflectionTestUtils.setField(contrato, "id", CONTRATO);
        return contrato;
    }

    private HistorialEstado eventoAuditado() {
        ArgumentCaptor<HistorialEstado> evento = ArgumentCaptor.forClass(HistorialEstado.class);
        verify(historial).save(evento.capture());
        return evento.getValue();
    }
}
