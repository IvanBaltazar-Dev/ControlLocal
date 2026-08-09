package com.controllocal.service.impl;

import com.controllocal.domain.comercial.ComisionLiquidacion;
import com.controllocal.domain.comercial.ComisionMovimiento;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.persistence.repositorio.ComisionLiquidacionRepository;
import com.controllocal.persistence.repositorio.ComisionMovimientoRepository;
import com.controllocal.persistence.repositorio.HistorialEstadoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.ComisionService.FichaComision;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Transiciones;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * <b>Caracterizacion del ciclo economico de la comision.</b>
 *
 * <p>{@link ComisionServiceImplTest} blinda los MENSAJES del cable congelado
 * (los dos gates del broker, calcados de la v1). Esta clase es otra cosa: fija
 * el COMPORTAMIENTO ECONOMICO observable del recorrido completo
 *
 * <pre>
 *   contrato -&gt; liquidacion -&gt; movimientos -&gt; saldo -&gt; estado derivado
 *            -&gt; cobro -&gt; anulacion
 * </pre>
 *
 * escenario por escenario —sin cobros, cobro parcial, cobro total, anulacion
 * antes de cobrar, anulacion tras cobro parcial, operacion repetida— antes de
 * tocar una linea de {@code ComisionServiceImpl}. Lo que estas pruebas afirman
 * es lo que el sistema HACE HOY, no lo que deberia hacer: cuando las dos cosas
 * no coinciden, la prueba lo dice en su nombre y en su comentario.
 *
 * <p><b>El invariante que vertebra el archivo</b> es
 * {@code montoCobrado + saldoCobro == montoBruto}: es la unica forma de que
 * «cobrado» y «pendiente» sumen la comision generada y de que el KPI economico
 * cuadre. {@link Defectos} reune los caminos que hoy lo rompen o que aceptan un
 * movimiento imposible, cada uno con el mecanismo exacto por el que ocurre.
 */
class ComisionCicloEconomicoTest {

    private static final long ORG = 1L;
    private static final long CONTRATO = 40L;
    private static final long LIQUIDACION = 60L;
    private static final String PEN = "PEN";

    private final ComisionLiquidacionRepository comisiones = mock(ComisionLiquidacionRepository.class);
    private final ComisionMovimientoRepository movimientos = mock(ComisionMovimientoRepository.class);
    private final HistorialEstadoRepository historial = mock(HistorialEstadoRepository.class);
    private final AlertaService alertas = mock(AlertaService.class);

    /** Espejo en memoria de comision_movimiento: los saldos se derivan de aqui. */
    private final List<ComisionMovimiento> registrados = new ArrayList<>();

    private final ComisionServiceImpl service = new ComisionServiceImpl(
            comisiones, movimientos, new Transiciones(historial), alertas);

    private final Actor broker = new Actor(ORG, 2L, 20L, Actor.BROKER);

    ComisionCicloEconomicoTest() {
        when(movimientos.save(any(ComisionMovimiento.class))).thenAnswer(invocacion -> {
            ComisionMovimiento guardado = invocacion.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", (long) registrados.size() + 1L);
            registrados.add(guardado);
            return guardado;
        });
        when(movimientos.findByOrganizacionIdAndLiquidacionIdOrderByFechaAscIdAsc(ORG, LIQUIDACION))
                .thenAnswer(invocacion -> List.copyOf(registrados));
        // Espejo de `uq_movimiento_idempotencia`: en la BD la unicidad la
        // impone el indice; aqui se resuelve buscando en la lista.
        when(movimientos.findByOrganizacionIdAndClaveIdempotencia(eq(ORG), anyString()))
                .thenAnswer(invocacion -> {
                    String clave = invocacion.getArgument(1);
                    return registrados.stream()
                            .filter(m -> clave.equals(m.getClaveIdempotencia()))
                            .findFirst();
                });
    }

    // ==================================================================
    // 1. Sin cobros
    // ==================================================================

    @Nested
    @DisplayName("sin cobros: la liquidacion nace PENDIENTE y todo el bruto esta por cobrar")
    class SinCobros {

        @Test
        void elSaldoInicialEsLaComisionBrutaCompleta() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", null);

            FichaComision ficha = leer();

            assertEquals(ComisionLiquidacion.PENDIENTE, ficha.estado());
            assertEquals(BigDecimal.ZERO, ficha.montoCobrado());
            assertEquals(new BigDecimal("450.00"), ficha.saldoCobro());
            assertCuadra(ficha);
        }

        @Test
        void asignarElRepartoNoMueveNiUnCentimoDeCaja() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", null);

            FichaComision ficha = service.asignarMontoAgente(CONTRATO, new BigDecimal("180.00"), broker);

            // El reparto es una DECISION, no un hecho economico: no hay
            // movimiento y el estado sigue PENDIENTE.
            assertEquals(ComisionLiquidacion.PENDIENTE, ficha.estado());
            assertEquals(List.of(), registrados);
            assertEquals(BigDecimal.ZERO, ficha.montoCobrado());
            assertEquals(new BigDecimal("180.00"), ficha.saldoPagoAgente());
            assertCuadra(ficha);
        }
    }

    // ==================================================================
    // 2. Cobro parcial
    // ==================================================================

    @Nested
    @DisplayName("cobro parcial: el estado R lo produce el saldo, no una eleccion del broker")
    class CobroParcial {

        @Test
        void unAbonoDejaLaComisionEnParcialConSuSaldo() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            FichaComision ficha = cobrar("200.00");

            assertEquals(ComisionLiquidacion.PARCIAL, ficha.estado());
            assertEquals(new BigDecimal("200.00"), ficha.montoCobrado());
            assertEquals(new BigDecimal("250.00"), ficha.saldoCobro());
            assertCuadra(ficha);
        }

        @Test
        void variosAbonosAcumulanYSiguenEnParcial() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            cobrar("200.00");
            FichaComision ficha = cobrar("100.00");

            // R -> R: Transiciones ignora la transicion al mismo estado, asi
            // que el segundo abono se registra sin volver a auditar el estado.
            assertEquals(ComisionLiquidacion.PARCIAL, ficha.estado());
            assertEquals(new BigDecimal("300.00"), ficha.montoCobrado());
            assertEquals(new BigDecimal("150.00"), ficha.saldoCobro());
            assertEquals(2, registrados.size());
            assertCuadra(ficha);
        }

        @Test
        void elUltimoAbonoQueAgotaElSaldoCierraLaComision() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            cobrar("200.00");
            FichaComision ficha = cobrar("250.00");

            assertEquals(ComisionLiquidacion.COBRADA, ficha.estado());
            assertEquals(new BigDecimal("450.00"), ficha.montoCobrado());
            assertEquals(new BigDecimal("0.00"), ficha.saldoCobro());
            assertCuadra(ficha);
        }

        @Test
        void nadieCobraMasQueElSaldoPendiente() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            cobrar("200.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> cobrar("250.01"));
            assertEquals("El cobro no puede superar el saldo pendiente.", error.getMessage());
            assertEquals(1, registrados.size());
        }

        @Test
        void elPagoAlAgenteNoPuedeSuperarSuParte() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.PAGO_AGENTE, "180.01"));
            assertEquals("El pago al agente no puede superar su saldo pendiente.", error.getMessage());
        }

        @Test
        void unaMonedaDistintaDeLaLiquidacionNoEntra() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrarMovimiento(CONTRATO, ComisionMovimiento.COBRO,
                            new BigDecimal("100.00"), "USD", LocalDate.now(), "EFECTIVO", null, null, broker));
            assertEquals("La moneda del movimiento debe coincidir con la liquidacion.",
                    error.getMessage());
        }
    }

    // ==================================================================
    // 3. Cobro total
    // ==================================================================

    @Nested
    @DisplayName("cobro total: el gate del broker deja el movimiento que lo respalda")
    class CobroTotal {

        @Test
        void elGateDeCobroEmiteUnMovimientoPorTodoElBruto() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            FichaComision ficha = service.registrarCobro(
                    CONTRATO, ComisionLiquidacion.COBRADA, LocalDate.now(), "TRANSFERENCIA", broker);

            assertEquals(ComisionLiquidacion.COBRADA, ficha.estado());
            assertEquals(1, registrados.size());
            assertEquals(ComisionMovimiento.COBRO, registrados.getFirst().getTipo());
            assertEquals(new BigDecimal("450.00"), registrados.getFirst().getMonto());
            assertCuadra(ficha);
        }

        @Test
        void sobreUnParcialElGateSoloCobraElSaldoYNoDuplica() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            cobrar("200.00");

            FichaComision ficha = service.registrarCobro(
                    CONTRATO, ComisionLiquidacion.COBRADA, LocalDate.now(), "EFECTIVO", broker);

            assertEquals(ComisionLiquidacion.COBRADA, ficha.estado());
            assertEquals(new BigDecimal("450.00"), ficha.montoCobrado());
            assertEquals(new BigDecimal("250.00"), registrados.get(1).getMonto());
            assertCuadra(ficha);
        }

        @Test
        void unaComisionCobradaNoAdmiteMasMovimientosDeCobro() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                    LocalDate.now(), "EFECTIVO", broker);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> cobrar("10.00"));
            assertEquals("El cobro no puede superar el saldo pendiente.", error.getMessage());
        }

        @Test
        void pagarAlAgenteSigueSiendoPosibleDespuesDeCobrar() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                    LocalDate.now(), "EFECTIVO", broker);

            // El pago al agente es la otra pata del ciclo y no cierra con el
            // cobro: C describe lo que entro, no lo que ya se repartio.
            FichaComision ficha = movimiento(ComisionMovimiento.PAGO_AGENTE, "180.00");

            assertEquals(ComisionLiquidacion.COBRADA, ficha.estado());
            assertEquals(new BigDecimal("180.00"), ficha.montoPagadoAgente());
            assertEquals(new BigDecimal("0.00"), ficha.saldoPagoAgente());
        }
    }

    // ==================================================================
    // 4. Anulacion antes de cobrar
    // ==================================================================

    @Nested
    @DisplayName("anulacion antes de cobrar: A es el desenlace expreso de una comision sin caja")
    class AnulacionAntesDeCobrar {

        @Test
        void anularSinMovimientosDejaLaComisionEnAYSinEvidencia() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            FichaComision ficha = service.registrarCobro(
                    CONTRATO, ComisionLiquidacion.ANULADA, LocalDate.now(), null, broker);

            assertEquals(ComisionLiquidacion.ANULADA, ficha.estado());
            assertEquals(List.of(), registrados);
            assertEquals(BigDecimal.ZERO, ficha.montoCobrado());
        }

        @Test
        void anularNoExigeRepartoPrevioNiFechaNiFormaDePago() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", null);

            FichaComision ficha = service.registrarCobro(
                    CONTRATO, ComisionLiquidacion.ANULADA, null, null, broker);

            assertEquals(ComisionLiquidacion.ANULADA, ficha.estado());
        }

        @Test
        void despuesDeAnularNoEntraNingunMovimiento() {
            liquidacion(ComisionLiquidacion.ANULADA, "450.00", "180.00");

            for (String tipo : List.of(ComisionMovimiento.COBRO, ComisionMovimiento.PAGO_AGENTE,
                    ComisionMovimiento.AJUSTE, ComisionMovimiento.REVERSION)) {
                ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                        () -> movimiento(tipo, "10.00"));
                assertEquals("Una comision anulada no admite movimientos.", error.getMessage());
            }
            assertEquals(List.of(), registrados);
        }

        @Test
        void despuesDeAnularTampocoSeReabreElCobro() {
            liquidacion(ComisionLiquidacion.ANULADA, "450.00", "180.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                            LocalDate.now(), "EFECTIVO", broker));
            assertEquals("La comision ya tiene un cobro registrado (Cobrada o Anulada).",
                    error.getMessage());
        }

        @Test
        void unaComisionCobradaYaNoSePuedeAnular() {
            liquidacion(ComisionLiquidacion.COBRADA, "450.00", "180.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrarCobro(CONTRATO, ComisionLiquidacion.ANULADA,
                            null, null, broker));
            assertEquals("La comision ya tiene un cobro registrado (Cobrada o Anulada).",
                    error.getMessage());
        }
    }

    // ==================================================================
    // 5. Anulacion tras cobro parcial
    // ==================================================================

    @Nested
    @DisplayName("anulacion tras cobro parcial: el dinero cobrado sobrevive, el KPI no lo ve")
    class AnulacionTrasCobroParcial {

        @Test
        void seAnulaUnaComisionQueYaTieneCajaYLosMovimientosPermanecen() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            cobrar("200.00");

            FichaComision ficha = service.registrarCobro(
                    CONTRATO, ComisionLiquidacion.ANULADA, null, null, broker);

            assertEquals(ComisionLiquidacion.ANULADA, ficha.estado());
            // La evidencia NO se borra: el abono de 200 sigue en la tabla y la
            // ficha lo sigue publicando.
            assertEquals(1, registrados.size());
            assertEquals(new BigDecimal("200.00"), ficha.montoCobrado());
            assertCuadra(ficha);
        }

        @Test
        void anularTrasCobrarDejaUnaComisionConCajaFueraDeTodoKpi() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            cobrar("200.00");
            service.registrarCobro(CONTRATO, ComisionLiquidacion.ANULADA, null, null, broker);

            // Los tres KPI economicos filtran por `com.estado <> 'A'`
            // (KpiComisionQueryTest lo blinda), asi que estos 200 cobrados
            // desaparecen de generado, cobrado y pagado a la vez. Es
            // coherente con "anulada no cuenta", pero significa que la caja
            // real de la corredora y la suma de los KPI pueden diferir: la
            // conciliacion tiene que mirar comision_movimiento, no el KPI.
            assertEquals(ComisionLiquidacion.ANULADA, leer().estado());
            assertEquals(new BigDecimal("200.00"), leer().montoCobrado());
        }
    }

    // ==================================================================
    // 6. Operacion repetida
    // ==================================================================

    @Nested
    @DisplayName("operacion repetida: que se rechaza, que es idempotente y que se duplica")
    class OperacionRepetida {

        @Test
        void asignarDosVecesReajustaElRepartoYAvisaAlAgenteUnaSolaVez() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", null);

            service.asignarMontoAgente(CONTRATO, new BigDecimal("180.00"), broker);
            FichaComision ficha = service.asignarMontoAgente(CONTRATO, new BigDecimal("200.00"), broker);

            assertEquals(new BigDecimal("200.00"), ficha.montoAgente());
            assertEquals(new BigDecimal("250.00"), ficha.montoEmpresa());
            verify(alertas, times(1)).emitir(any(), any());
        }

        @Test
        void cobrarDosVecesFallaLaSegunda() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                    LocalDate.now(), "EFECTIVO", broker);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                            LocalDate.now(), "EFECTIVO", broker));
            assertEquals("La comision ya tiene un cobro registrado (Cobrada o Anulada).",
                    error.getMessage());
            assertEquals(1, registrados.size());
        }

        @Test
        void anularDosVecesFallaLaSegunda() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            service.registrarCobro(CONTRATO, ComisionLiquidacion.ANULADA, null, null, broker);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> service.registrarCobro(CONTRATO, ComisionLiquidacion.ANULADA,
                            null, null, broker));
            assertEquals("La comision ya tiene un cobro registrado (Cobrada o Anulada).",
                    error.getMessage());
        }

        /**
         * Con {@code Idempotency-Key}, reenviar el MISMO comando devuelve el
         * resultado original y NO inserta otra fila. Es el caso del doble clic
         * y el del reintento por timeout.
         */
        @Test
        void conClaveDeIdempotenciaElReintentoNoDuplicaElDinero() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            FichaComision primera = conClave("k-1", ComisionMovimiento.COBRO, "200.00");
            FichaComision reintento = conClave("k-1", ComisionMovimiento.COBRO, "200.00");

            assertEquals(1, registrados.size(), "el reintento no puede crear una segunda fila");
            assertEquals(primera.montoCobrado(), reintento.montoCobrado());
            assertEquals(new BigDecimal("200.00"), reintento.montoCobrado());
            assertCuadra(reintento);
        }

        /** Dos operaciones legitimas iguales, con claves distintas: dos filas. */
        @Test
        void dosOperacionesLegitimasConClavesDistintasSiSuman() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            conClave("k-1", ComisionMovimiento.COBRO, "200.00");
            FichaComision segunda = conClave("k-2", ComisionMovimiento.COBRO, "200.00");

            assertEquals(2, registrados.size());
            assertEquals(new BigDecimal("400.00"), segunda.montoCobrado());
            assertCuadra(segunda);
        }

        /** La clave identifica UNA operacion: reusarla para otra es un 409. */
        @Test
        void laMismaClaveConOtroComandoEsConflicto() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            conClave("k-1", ComisionMovimiento.COBRO, "200.00");

            ConflictoException error = assertThrows(ConflictoException.class,
                    () -> conClave("k-1", ComisionMovimiento.COBRO, "300.00"));

            assertEquals("La clave de idempotencia ya se uso para otro movimiento de comision.",
                    error.getMessage());
            assertEquals(1, registrados.size());
        }

        @Test
        void reenviarElMISMOmovimientoLoDuplicaYRespondeDoscientos() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            cobrar("100.00");
            FichaComision ficha = cobrar("100.00");

            // Un movimiento es un HECHO, y dos abonos iguales el mismo dia son
            // legitimos: no hay clave de idempotencia y el service no puede
            // distinguir el reintento del segundo abono. Queda documentado
            // porque un doble clic en la UI produce exactamente esto.
            assertEquals(2, registrados.size());
            assertEquals(new BigDecimal("200.00"), ficha.montoCobrado());
            assertCuadra(ficha);
        }
    }

    // ==================================================================
    // 7. Defectos demostrados
    // ==================================================================

    @Nested
    @DisplayName("DEFECTOS: comportamiento actual que no cuadra con el modelo documentado")
    class Defectos {

        /**
         * D-1 quedo CORREGIDO mientras se escribia esta clase: una reversion
         * sin cobro previo dejaba {@code saldoCobro} por encima del bruto
         * —{@code saldos()} clampa {@code montoCobrado} a cero pero calculaba
         * el saldo con el acumulado sin clampar— y desde ahi se podia cobrar
         * mas que la comision pactada. El tope vive ahora en
         * {@code registrarMovimiento} y lo blindan
         * {@code CicloComisionTest.reversionSinCobroPrevio} y
         * {@code reversionAcotadaPorLoCobrado}, asi que aqui no se duplica.
         */

        /**
         * D-2 RESUELTO. El tipo {@code A} pasaba todas las validaciones, se
         * persistia y no participaba en ningun saldo: un 200 que no cambiaba
         * nada. Se retiro del comando porque no existe regla aprobada que diga
         * que saldo modifica, con que signo, contra que tope, como afecta a
         * {@code P/R/C}, como se revierte ni como entra en KPI. Ahora es un
         * 400 explicito y no deja fila.
         */
        @Test
        void elAjusteYaNoEsUnComandoMonetarioValido() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.AJUSTE, "300.00"));

            assertTrue(error.getMessage().startsWith("El ajuste no es una operacion monetaria"),
                    error.getMessage());
            assertEquals(List.of(), registrados, "un comando rechazado no deja evidencia");
        }

        /**
         * D-3 RESUELTO. Una captacion sin comision (valor 0 con motivo expreso,
         * admitido por {@code ck_condicion_sin_comision}) produce una
         * liquidacion de bruto 0. Al cobrarla, el gate emitia un movimiento de
         * importe 0 que {@code ck_movimiento_monto CHECK (monto > 0)} rechaza,
         * y eso salia por el cable como un 500.
         *
         * <p>El constraint tiene razon —un movimiento de cero no es evidencia
         * de nada—, asi que la decision se tomo en negocio: no hay nada que
         * cobrar, no se escribe movimiento, y la liquidacion pasa a COBRADA
         * igual porque su saldo ya estaba a cero.
         */
        @Test
        void cobrarUnaComisionDeImporteCeroNoEmiteMovimientoYCierraIgual() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "0.00", "0.00");

            FichaComision ficha = service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                    LocalDate.now(), "EFECTIVO", broker);

            assertEquals(ComisionLiquidacion.COBRADA, ficha.estado());
            assertEquals(List.of(), registrados, "un cobro de cero no es evidencia economica");
            verify(movimientos, never()).save(any(ComisionMovimiento.class));
            assertEquals(BigDecimal.ZERO, ficha.montoCobrado());
            assertCuadra(ficha);
        }

        /**
         * D-4. Revertir sobre una comision ya COBRADA se rechaza, pero el
         * mensaje que llega al broker es el de la maquina de estados
         * ({@code C -> R}), no una regla de negocio comprensible. Ademas
         * significa que un cobro registrado por error ya no se puede deshacer.
         */
        @Test
        void revertirUnaComisionCobradaFallaConElMensajeDeLaMaquinaDeEstados() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            service.registrarCobro(CONTRATO, ComisionLiquidacion.COBRADA,
                    LocalDate.now(), "EFECTIVO", broker);

            ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                    () -> movimiento(ComisionMovimiento.REVERSION, "100.00"));
            assertEquals("Transicion no permitida para COMISION_LIQUIDACION: C -> R.",
                    error.getMessage());
        }

        /**
         * D-5. {@code R} solo revierte COBROS. Un pago al agente registrado por
         * error no tiene contrapartida: intentar revertirlo descuadra el cobro
         * y deja intacto lo pagado.
         */
        @Test
        void noExisteReversionDelPagoAlAgente() {
            liquidacion(ComisionLiquidacion.PENDIENTE, "450.00", "180.00");
            cobrar("300.00");
            movimiento(ComisionMovimiento.PAGO_AGENTE, "180.00");

            FichaComision ficha = movimiento(ComisionMovimiento.REVERSION, "180.00");

            // Lo pagado al agente no baja; lo que baja es lo cobrado.
            assertEquals(new BigDecimal("180.00"), ficha.montoPagadoAgente());
            assertEquals(new BigDecimal("120.00"), ficha.montoCobrado());
        }
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    /** El invariante economico: lo cobrado mas lo pendiente es el bruto. */
    private static void assertCuadra(FichaComision ficha) {
        assertEquals(0, ficha.montoCobrado().add(ficha.saldoCobro()).compareTo(ficha.monto()),
                "cobrado " + ficha.montoCobrado() + " + saldo " + ficha.saldoCobro()
                        + " deberia ser el bruto " + ficha.monto());
    }

    private FichaComision leer() {
        return service.porContrato(CONTRATO, broker).orElseThrow();
    }

    private FichaComision cobrar(String monto) {
        return movimiento(ComisionMovimiento.COBRO, monto);
    }

    private FichaComision movimiento(String tipo, String monto) {
        return service.registrarMovimiento(CONTRATO, tipo, new BigDecimal(monto), PEN,
                LocalDate.now(), "TRANSFERENCIA", null, null, broker);
    }

    /** El mismo comando, con la clave de idempotencia que trae la cabecera. */
    private FichaComision conClave(String clave, String tipo, String monto) {
        return service.registrarMovimiento(CONTRATO, tipo, new BigDecimal(monto), PEN,
                LocalDate.now(), "TRANSFERENCIA", null, clave, broker);
    }

    private void liquidacion(String estado, String bruta, String montoAgente) {
        ComisionLiquidacion comision = new ComisionLiquidacion();
        comision.setOrganizacionId(ORG);
        comision.setContrato(contrato());
        comision.setMonto(new BigDecimal(bruta));
        comision.setMoneda(PEN);
        if (montoAgente != null) {
            comision.asignarMontoAgente(new BigDecimal(montoAgente));
        }
        new Transiciones(mock(HistorialEstadoRepository.class)).iniciar(comision, estado);
        ReflectionTestUtils.setField(comision, "id", LIQUIDACION);
        when(comisiones.porContrato(ORG, CONTRATO)).thenReturn(Optional.of(comision));
        when(comisiones.save(any(ComisionLiquidacion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Con solicitud: los dos avisos al agente salen de ella (codigo y
     * destinatario), y sin ella {@code avisarAlAgente} sale antes de emitir.
     */
    private static ContratoAlquiler contrato() {
        SolicitudAlquiler solicitud = new SolicitudAlquiler();
        solicitud.setOrganizacionId(ORG);
        solicitud.setCodigoSolicitud("SOL-0001");

        ContratoAlquiler contrato = new ContratoAlquiler();
        contrato.setOrganizacionId(ORG);
        contrato.setFechaCierre(LocalDate.now());
        contrato.setSolicitud(solicitud);
        new Transiciones(mock(HistorialEstadoRepository.class))
                .iniciar(contrato, ContratoAlquiler.VIGENTE);
        ReflectionTestUtils.setField(contrato, "id", CONTRATO);
        return contrato;
    }
}
