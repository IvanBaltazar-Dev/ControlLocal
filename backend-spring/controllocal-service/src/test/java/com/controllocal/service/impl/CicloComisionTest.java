package com.controllocal.service.impl;

import com.controllocal.domain.comercial.ComisionLiquidacion;
import com.controllocal.domain.comercial.ComisionMovimiento;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.persistence.repositorio.ComisionLiquidacionRepository;
import com.controllocal.persistence.repositorio.ComisionMovimientoRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.ComisionService.FichaComision;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <b>El ciclo economico de la comision es una maquina alimentada por el saldo
 * de movimientos, y esto lo demuestra.</b>
 *
 * <p>Hermana de {@code CicloContratoTest}: aquella fija el grafo juridico del
 * contrato, esta fija el ECONOMICO de la liquidacion. La diferencia es que el
 * estado de la comision no lo elige quien llama —{@code P/R/C} se DERIVAN del
 * saldo de {@code comision_movimiento}— y solo {@code A} es una decision
 * expresa del broker. Leer la letra almacenada sin mirar los movimientos no
 * dice la situacion economica real; por eso cada caso de abajo comprueba las
 * dos cosas a la vez: el codigo de estado y el saldo del que sale.
 *
 * <pre>
 *   P  cobrado = 0                    R  0 &lt; cobrado &lt; bruto
 *   C  cobrado = bruto                A  anulacion expresa desde P o R
 * </pre>
 *
 * <p>Los seis escenarios exigidos por el subbloque 7.2 —sin cobros, cobro
 * parcial, cobro total, anulacion antes de cobrar, anulacion tras cobro
 * parcial y operacion repetida— estan cada uno en su prueba.
 */
class CicloComisionTest {

    private static final long ORG = 1L;
    private static final long CONTRATO = 40L;
    private static final long LIQUIDACION = 60L;
    private static final BigDecimal BRUTA = new BigDecimal("450.00");
    private static final BigDecimal PARTE_AGENTE = new BigDecimal("180.00");

    private final ComisionLiquidacionRepository comisiones = mock(ComisionLiquidacionRepository.class);
    private final ComisionMovimientoRepository movimientos = mock(ComisionMovimientoRepository.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);
    private final List<ComisionMovimiento> registrados = new ArrayList<>();

    private final ComisionServiceImpl service = new ComisionServiceImpl(
            comisiones, movimientos, new Transiciones(historial), mock(AlertaService.class));

    private final Actor broker = new Actor(ORG, 2L, 20L, Actor.BROKER);

    @BeforeEach
    void prepararAlmacenDeMovimientos() {
        registrados.clear();
        when(movimientos.save(any(ComisionMovimiento.class))).thenAnswer(inv -> {
            ComisionMovimiento guardado = inv.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", (long) registrados.size() + 1L);
            registrados.add(guardado);
            return guardado;
        });
        when(movimientos.findByOrganizacionIdAndLiquidacionIdOrderByFechaAscIdAsc(ORG, LIQUIDACION))
                .thenAnswer(inv -> List.copyOf(registrados));
    }

    // ------------------------------------------------------------------
    // Los seis escenarios del saldo
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("el estado sale del saldo, no de la letra almacenada")
    class EstadoDerivado {

        @Test
        @DisplayName("sin cobros: PENDIENTE y el saldo es la bruta integra")
        void sinCobros() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            FichaComision ficha = service.porContrato(CONTRATO, broker).orElseThrow();

            assertEquals(ComisionLiquidacion.PENDIENTE, ficha.estado());
            assertEquals(BigDecimal.ZERO, ficha.montoCobrado());
            assertEquals(BRUTA, ficha.saldoCobro());
        }

        @Test
        @DisplayName("cobro parcial: PARCIAL y el saldo baja lo cobrado")
        void cobroParcial() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            FichaComision ficha = cobrar("200.00");

            assertEquals(ComisionLiquidacion.PARCIAL, ficha.estado());
            assertEquals(new BigDecimal("200.00"), ficha.montoCobrado());
            assertEquals(new BigDecimal("250.00"), ficha.saldoCobro());
        }

        @Test
        @DisplayName("cobro total en dos abonos: COBRADA cuando el saldo llega a cero")
        void cobroTotal() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            assertEquals(ComisionLiquidacion.PARCIAL, cobrar("200.00").estado());
            FichaComision ficha = cobrar("250.00");

            assertEquals(ComisionLiquidacion.COBRADA, ficha.estado());
            assertEquals(BRUTA, ficha.montoCobrado());
            assertEquals(new BigDecimal("0.00"), ficha.saldoCobro());
        }

        @Test
        @DisplayName("revertir todo lo cobrado devuelve la liquidacion a PENDIENTE")
        void reversionDesdeParcial() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);
            cobrar("200.00");

            FichaComision ficha = movimiento(ComisionMovimiento.REVERSION, "200.00");

            assertEquals(ComisionLiquidacion.PENDIENTE, ficha.estado());
            assertEquals(new BigDecimal("0.00"), ficha.montoCobrado());
            assertEquals(BRUTA, ficha.saldoCobro());
        }
    }

    @Nested
    @DisplayName("anulacion: decision expresa, nunca derivada")
    class Anulacion {

        @Test
        @DisplayName("anulacion antes de cobrar: A y sin datos de cobro")
        void antesDeCobrar() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            FichaComision ficha = service.registrarCobro(
                    CONTRATO, ComisionLiquidacion.ANULADA, LocalDate.now(), "EFECTIVO", broker);

            assertEquals(ComisionLiquidacion.ANULADA, ficha.estado());
            assertEquals(BigDecimal.ZERO, ficha.montoCobrado());
            assertTrue(registrados.isEmpty(), "anular no inventa un movimiento de cobro");
        }

        @Test
        @DisplayName("anulacion tras cobro parcial: permitida, y el dinero cobrado sigue siendo evidencia")
        void trasCobroParcial() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);
            cobrar("200.00");

            FichaComision ficha = service.registrarCobro(
                    CONTRATO, ComisionLiquidacion.ANULADA, LocalDate.now(), null, broker);

            // R -> A esta en el grafo a proposito: se anula lo que queda por
            // cobrar, no lo ya cobrado. El movimiento previo NO se borra.
            assertEquals(ComisionLiquidacion.ANULADA, ficha.estado());
            assertEquals(new BigDecimal("200.00"), ficha.montoCobrado());
            assertEquals(1, registrados.size());
        }

        @Test
        @DisplayName("una comision cobrada por completo ya no se puede anular")
        void trasCobroTotal() {
            liquidacion(ComisionLiquidacion.COBRADA, BRUTA, PARTE_AGENTE);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrarCobro(CONTRATO, ComisionLiquidacion.ANULADA,
                            LocalDate.now(), null, broker));
            assertEquals("La comision ya tiene un cobro registrado (Cobrada o Anulada).",
                    error.getMessage());
        }

        @Test
        @DisplayName("una comision anulada no admite ningun movimiento posterior")
        void anuladaNoAdmiteMovimientos() {
            liquidacion(ComisionLiquidacion.ANULADA, BRUTA, PARTE_AGENTE);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.COBRO, "10.00"));
            assertEquals("Una comision anulada no admite movimientos.", error.getMessage());
            assertTrue(registrados.isEmpty());
        }
    }

    @Nested
    @DisplayName("operacion repetida: nunca un 200 silencioso")
    class OperacionRepetida {

        @Test
        @DisplayName("cobrar dos veces: el segundo intento es un error explicito")
        void cobroRepetido() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);
            service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                    LocalDate.now(), "EFECTIVO", broker);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                            LocalDate.now(), "EFECTIVO", broker));
            assertEquals("La comision ya tiene un cobro registrado (Cobrada o Anulada).",
                    error.getMessage());
            assertEquals(1, registrados.size(), "el segundo intento no duplica el movimiento");
        }

        @Test
        @DisplayName("reenviar el mismo abono agota el saldo y el siguiente se rechaza")
        void abonoRepetido() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);
            cobrar("225.00");
            cobrar("225.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> cobrar("225.00"));
            assertEquals("El cobro no puede superar el saldo pendiente.", error.getMessage());
            assertEquals(2, registrados.size());
        }

        @Test
        @DisplayName("reenviar el mismo pago al agente se corta en su saldo")
        void pagoAgenteRepetido() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);
            movimiento(ComisionMovimiento.PAGO_AGENTE, "180.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.PAGO_AGENTE, "180.00"));
            assertEquals("El pago al agente no puede superar su saldo pendiente.", error.getMessage());
            assertEquals(1, registrados.size());
        }
    }

    // ------------------------------------------------------------------
    // Invariantes economicos
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("invariantes del dinero")
    class Invariantes {

        @Test
        @DisplayName("no se cobra mas que la bruta")
        void nuncaMasQueLaBruta() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> cobrar("450.01"));
            assertEquals("El cobro no puede superar el saldo pendiente.", error.getMessage());
        }

        @Test
        @DisplayName("la moneda del movimiento no puede diferir de la liquidacion")
        void monedaAtada() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrarMovimiento(CONTRATO, ComisionMovimiento.COBRO,
                            new BigDecimal("100.00"), "USD", LocalDate.now(), "EFECTIVO", null, null, broker));
            assertEquals("La moneda del movimiento debe coincidir con la liquidacion.",
                    error.getMessage());
        }

        @Test
        @DisplayName("no se paga al agente antes de que el broker reparta")
        void pagoAgenteExigeReparto() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, null);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.PAGO_AGENTE, "10.00"));
            assertEquals("Asigna primero la parte del agente.", error.getMessage());
        }

        @Test
        @DisplayName("una reversion no puede devolver mas de lo cobrado")
        void reversionAcotadaPorLoCobrado() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);
            cobrar("100.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.REVERSION, "100.01"));
            assertEquals("La reversion no puede superar lo cobrado.", error.getMessage());
        }

        @Test
        @DisplayName("sin cobros no hay nada que revertir: el saldo nunca supera la bruta")
        void reversionSinCobroPrevio() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.REVERSION, "500.00"));
            assertEquals("La reversion no puede superar lo cobrado.", error.getMessage());

            FichaComision ficha = service.porContrato(CONTRATO, broker).orElseThrow();
            assertEquals(BRUTA, ficha.saldoCobro());
        }

        /**
         * El ajuste se admitia, se persistia como evidencia economica y no
         * movia ningun saldo. Sin una regla que diga que modifica —signo, tope,
         * efecto en {@code P/R/C}, reversion, KPI— no es una operacion
         * monetaria, asi que deja de aceptarse como comando.
         */
        @Test
        @DisplayName("el ajuste no es un comando monetario: 400 y sin fila")
        void ajusteRetirado() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.AJUSTE, "50.00"));

            assertTrue(error.getMessage().startsWith("El ajuste no es una operacion monetaria"),
                    error.getMessage());
            FichaComision ficha = service.porContrato(CONTRATO, broker).orElseThrow();
            assertEquals(ComisionLiquidacion.PENDIENTE, ficha.estado());
            assertEquals(BRUTA, ficha.saldoCobro());
        }
    }

    @Nested
    @DisplayName("reparto agente/empresa")
    class Reparto {

        @Test
        @DisplayName("un cobro parcial no congela el reparto todavia sin asignar")
        void repartoTrasCobroParcial() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, null);
            cobrar("200.00");

            FichaComision ficha = service.asignarMontoAgente(CONTRATO, PARTE_AGENTE, broker);

            assertEquals(PARTE_AGENTE, ficha.montoAgente());
            assertEquals(new BigDecimal("270.00"), ficha.montoEmpresa());
            assertEquals(ComisionLiquidacion.PARCIAL, ficha.estado());
        }

        @Test
        @DisplayName("el reparto no se toca cuando la comision ya esta resuelta")
        void repartoCerradoTrasResolver() {
            liquidacion(ComisionLiquidacion.COBRADA, BRUTA, PARTE_AGENTE);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.asignarMontoAgente(CONTRATO, new BigDecimal("100.00"), broker));
            // Mensaje congelado de la v1. Su letra dice "Pendiente" porque alli
            // PARCIAL no tenia productor; el gate real es "queda saldo por
            // cobrar", que para la v1 significaba exactamente lo mismo.
            assertEquals("Solo se puede asignar el monto del agente mientras la comision esta Pendiente.",
                    error.getMessage());
        }

        @Test
        @DisplayName("el reparto no puede quedar por debajo de lo ya pagado al agente")
        void repartoNoBajaDeLoPagado() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);
            movimiento(ComisionMovimiento.PAGO_AGENTE, "150.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.asignarMontoAgente(CONTRATO, new BigDecimal("100.00"), broker));
            assertEquals("El monto del agente no puede ser menor que lo ya pagado al agente.",
                    error.getMessage());
        }
    }

    @Nested
    @DisplayName("cascada del contrato anulado")
    class ContratoAnulado {

        @Test
        @DisplayName("anular el contrato arrastra la comision pendiente")
        void arrastraLaPendiente() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);

            service.anularPorContratoAnulado(CONTRATO, broker);

            assertEquals(ComisionLiquidacion.ANULADA,
                    service.porContrato(CONTRATO, broker).orElseThrow().estado());
        }

        @Test
        @DisplayName("arrastra tambien la parcial y conserva el abono como evidencia")
        void arrastraLaParcial() {
            liquidacion(ComisionLiquidacion.PENDIENTE, BRUTA, PARTE_AGENTE);
            cobrar("200.00");

            service.anularPorContratoAnulado(CONTRATO, broker);

            FichaComision ficha = service.porContrato(CONTRATO, broker).orElseThrow();
            assertEquals(ComisionLiquidacion.ANULADA, ficha.estado());
            assertEquals(new BigDecimal("200.00"), ficha.montoCobrado());
            assertEquals(1, registrados.size());
        }

        @Test
        @DisplayName("con la comision ya cobrada, la anulacion del contrato se rechaza")
        void rechazaSiYaSeCobro() {
            liquidacion(ComisionLiquidacion.COBRADA, BRUTA, PARTE_AGENTE);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.anularPorContratoAnulado(CONTRATO, broker));
            assertEquals("No se puede anular un contrato cuya comision ya fue cobrada.",
                    error.getMessage());
        }

        @Test
        @DisplayName("un contrato sin liquidacion se anula sin ruido")
        void sinLiquidacionNoHaceNada() {
            when(comisiones.porContrato(ORG, CONTRATO)).thenReturn(Optional.empty());

            service.anularPorContratoAnulado(CONTRATO, broker);
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private FichaComision cobrar(String monto) {
        return movimiento(ComisionMovimiento.COBRO, monto);
    }

    private FichaComision movimiento(String tipo, String monto) {
        return service.registrarMovimiento(CONTRATO, tipo, new BigDecimal(monto), "PEN",
                LocalDate.now(), "TRANSFERENCIA", null, null, broker);
    }

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
        ReflectionTestUtils.setField(comision, "id", LIQUIDACION);
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
}
