package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Alerta;
import com.controllocal.domain.comercial.ComisionLiquidacion;
import com.controllocal.domain.comercial.ComisionMovimiento;
import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.persistence.repositorio.ComisionLiquidacionRepository;
import com.controllocal.persistence.repositorio.ComisionMovimientoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AlertaService;
import com.controllocal.service.ComisionService;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Transiciones;
import com.controllocal.service.soporte.CondicionesEconomicas;
import com.controllocal.service.soporte.CalculadoraComision;
import com.controllocal.service.soporte.Idempotencia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reglas y mensajes calcados de {@code ComisionLiquidacionBusinessLogicImpl}
 * + los dos gates de comision de {@code ContratosRest} (contrato congelado
 * F4 §5).
 *
 * <p>La comision es Transicionable, asi que PENDIENTE -&gt; COBRADA|ANULADA
 * pasa por {@link Transiciones} y queda auditada; la v1 movia el estado a
 * mano.
 *
 * <p>Dos decisiones del dominio economico vigente:
 * <ul>
 *   <li>la comision hereda la moneda de la renta final del contrato; no existe
 *       una moneda fija ni una conversion implicita;</li>
 *   <li>no hay reparto automatico 50/50: la liquidacion nace con la bruta y
 *       {@code montoAgente}/{@code montoEmpresa} en NULL hasta que el broker
 *       decide.</li>
 * </ul>
 */
@Service
public class ComisionServiceImpl implements ComisionService {

    private final ComisionLiquidacionRepository comisiones;
    private final ComisionMovimientoRepository movimientos;
    private final Transiciones transiciones;
    private final AlertaService alertas;

    @Autowired
    public ComisionServiceImpl(ComisionLiquidacionRepository comisiones,
                               ComisionMovimientoRepository movimientos,
                               Transiciones transiciones, AlertaService alertas) {
        this.comisiones = comisiones;
        this.movimientos = movimientos;
        this.transiciones = transiciones;
        this.alertas = alertas;
    }

    /** Constructor conservado para tests de la frontera congelada. */
    public ComisionServiceImpl(ComisionLiquidacionRepository comisiones, Transiciones transiciones,
                               AlertaService alertas) {
        this(comisiones, null, transiciones, alertas);
    }

    @Override
    @Transactional
    public FichaComision crearPendiente(ContratoAlquiler contrato, BigDecimal comisionPactada,
                                        BigDecimal renta, String moneda, Actor actor) {
        BigDecimal porcentaje = CondicionesEconomicas.comisionPactada(comisionPactada);
        CondicionEconomicaCaptacion condicion = new CondicionEconomicaCaptacion();
        condicion.setTipoOperacion(CondicionEconomicaCaptacion.ARRENDAMIENTO);
        condicion.setTipoComision(CondicionEconomicaCaptacion.EQUIVALENTE_MENSUALIDADES);
        condicion.setBaseCalculo(CondicionEconomicaCaptacion.RENTA_MENSUAL);
        condicion.setValorComision(porcentaje.divide(BigDecimal.valueOf(100)));
        condicion.setMonedaReferencia(moneda);
        condicion.setMonedaComision(moneda);
        condicion.setTratamientoIgv(CondicionEconomicaCaptacion.IGV_NO_APLICA);
        return crearPendienteNormalizada(contrato, condicion, renta, moneda, actor);
    }

    @Override
    @Transactional
    public FichaComision crearPendienteNormalizada(ContratoAlquiler contrato,
                                                   CondicionEconomicaCaptacion condicion,
                                                   BigDecimal renta, String moneda, Actor actor) {
        String monedaRenta = CondicionesEconomicas.moneda(moneda, "de la renta final");
        ComisionLiquidacion comision = new ComisionLiquidacion();
        comision.setOrganizacionId(contrato.getOrganizacionId());
        comision.setContrato(contrato);
        comision.setMonto(CalculadoraComision.calcular(condicion, renta,
                CondicionEconomicaCaptacion.PRECIO_VENTA.equals(condicion.getBaseCalculo())
                        ? condicion.getImporteReferencia() : null));
        comision.setMoneda(CondicionEconomicaCaptacion.MONTO_FIJO.equals(condicion.getTipoComision())
                ? CondicionesEconomicas.moneda(condicion.getMonedaComision(), "de la comision fija")
                : monedaRenta);
        // montoAgente / montoEmpresa / fechaCobro / formaPago quedan NULL a
        // proposito: los define el broker supervisor mas adelante.
        transiciones.iniciar(comision, ComisionLiquidacion.PENDIENTE);
        return ficha(comisiones.save(comision), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FichaComision> porContrato(long idContrato, Actor actor) {
        return comisiones.porContrato(actor.idOrganizacion(), idContrato)
                .map(c -> ficha(c, movimientos(c, actor)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, FichaComision> porContratos(Collection<Long> idsContrato, Actor actor) {
        List<Long> ids = idsContrato == null ? List.of() : idsContrato.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, FichaComision> porContrato = new HashMap<>();
        for (ComisionLiquidacion comision : comisiones.porContratos(actor.idOrganizacion(), ids)) {
            FichaComision ficha = ficha(comision, movimientos(comision, actor));
            if (ficha.idContrato() != null) {
                porContrato.putIfAbsent(ficha.idContrato(), ficha);
            }
        }
        return porContrato;
    }

    @Override
    @Transactional
    public FichaComision asignarMontoAgente(long idContrato, BigDecimal montoAgente, Actor actor) {
        // "Indica el monto del agente." es el gate del REST y lo aplica
        // ContratoService antes de llegar aqui; esta es la regla de la BL.
        if (montoAgente == null || montoAgente.signum() < 0) {
            throw new ReglaNegocioException("El monto del agente debe ser cero o positivo.");
        }
        ComisionLiquidacion comision = cargar(idContrato, actor);
        // Se reparte mientras QUEDE SALDO por cobrar, no solo en PENDIENTE.
        // Atarlo a PENDIENTE dejaba sin salida a toda comision con un abono
        // parcial: pasaba a PARCIAL, el reparto se cerraba para siempre y el
        // pago al agente quedaba bloqueado por "Asigna primero la parte del
        // agente". El mensaje conserva la letra de la v1 —donde PARCIAL no
        // tenia productor y este camino era inalcanzable—, asi que el cable
        // congelado no cambia para ningun caso que la v1 pudiera alcanzar.
        if (!comision.estaPendiente()) {
            throw new ReglaNegocioException(
                    "Solo se puede asignar el monto del agente mientras la comision esta Pendiente.");
        }
        BigDecimal bruta = comision.getMonto() != null ? comision.getMonto() : BigDecimal.ZERO;
        if (montoAgente.compareTo(bruta) > 0) {
            throw new ReglaNegocioException("El monto del agente no puede superar la comision bruta.");
        }
        // Rebajar el reparto por debajo de lo ya pagado convertiria en deuda
        // del agente un dinero que ya salio: el saldo se quedaria en cero por
        // el clamp y el sobrepago desapareceria de la vista.
        BigDecimal pagado = saldos(comision, movimientos(comision, actor)).pagadoAgente();
        if (montoAgente.compareTo(pagado) < 0) {
            throw new ReglaNegocioException(
                    "El monto del agente no puede ser menor que lo ya pagado al agente.");
        }
        // "Primera" se decide ANTES de asignar: en los reajustes posteriores el
        // agente ya fue avisado y la v1 no repite el aviso.
        boolean primeraAsignacion = comision.getMontoAgente() == null;
        comision.asignarMontoAgente(montoAgente);
        if (primeraAsignacion) {
            avisarAlAgente(comision, Alerta.COMISION_ASIGNADA, actor,
                    codigo -> "Tu comision de la operacion " + codigo + " esta lista para cobro.");
        }
        return ficha(comisiones.save(comision), movimientos(comision, actor));
    }

    @Override
    @Transactional
    public FichaComision registrarCobro(long idContrato, String estado, LocalDate fechaCobro,
                                        String formaPago, Actor actor) {
        // Orden del cable: la liquidacion se busca ANTES de parsear el cuerpo,
        // asi que un contrato sin liquidacion responde 404 aunque el estado
        // que mandaron fuera invalido.
        ComisionLiquidacion comision = cargar(idContrato, actor);
        String destino = estadoDeCobro(estado);
        String pago = formaPago(formaPago);
        if (!comision.estaPendiente()) {
            throw new ReglaNegocioException(
                    "La comision ya tiene un cobro registrado (Cobrada o Anulada).");
        }
        if (ComisionLiquidacion.COBRADA.equals(destino)) {
            if (comision.getMontoAgente() == null) {
                throw new ReglaNegocioException(
                        "Antes de cobrar, el broker supervisor debe asignar el monto del agente.");
            }
            if (fechaCobro == null) {
                throw new ReglaNegocioException("Registra la fecha de cobro.");
            }
            if (fechaCobro.isAfter(LocalDate.now())) {
                throw new ReglaNegocioException("La fecha de cobro no puede ser futura.");
            }
            if (pago == null) {
                throw new ReglaNegocioException("Registra la forma de pago.");
            }
            BigDecimal pendiente = saldos(comision, movimientos(comision, actor)).saldoCobro();
            // COMISION SIN IMPORTE. Una captacion sin comision es legal
            // (`valor_comision = 0` con motivo expreso, `ck_condicion_sin_comision`)
            // y produce una liquidacion de bruto 0. Cobrarla no puede emitir un
            // movimiento de importe 0: `ck_movimiento_monto CHECK (monto > 0)`
            // lo rechazaria y eso sale por el cable como un 500.
            //
            // El constraint tiene razon —un movimiento de cero no es evidencia
            // de nada— asi que la decision se toma AQUI: no hay nada que
            // cobrar, no se escribe movimiento, y la liquidacion pasa a COBRADA
            // igual, porque su saldo ya estaba a cero. Saldos, KPI e historial
            // quedan como estaban.
            if (pendiente.signum() > 0) {
                registrarMovimientoInterno(comision, ComisionMovimiento.COBRO, pendiente,
                        comision.getMoneda(), fechaCobro, pago, "Cobro total", null, null, actor);
            }
        } else {
            // ANULADA no inventa un cobro. Los movimientos previos permanecen
            // como evidencia historica y se excluyen de KPI por el estado.
        }
        transiciones.aplicar(comision, comision.getId(), destino, actor,
                ComisionLiquidacion.COBRADA.equals(destino)
                        ? "Comision cobrada (" + pago + ")."
                        : "Comision anulada.");
        if (ComisionLiquidacion.COBRADA.equals(destino)) {
            avisarAlAgente(comision, Alerta.COMISION_COBRADA, actor,
                    codigo -> "Tu comision de la operacion " + codigo + " fue cobrada.");
        }
        return ficha(comisiones.save(comision), movimientos(comision, actor));
    }

    @Override
    @Transactional
    public FichaComision registrarMovimiento(long idContrato, String tipo, BigDecimal monto,
                                              String moneda, LocalDate fecha, String formaPago,
                                              String observacion, String claveIdempotencia,
                                              Actor actor) {
        String clave = Idempotencia.normalizar(claveIdempotencia);
        String huella = Idempotencia.huella(idContrato, tipo, monto, moneda, fecha, formaPago,
                observacion);
        // CAMINO NORMAL del reintento: si la clave ya creo un movimiento, se
        // devuelve el resultado de aquel SIN insertar nada. La carrera de dos
        // peticiones simultaneas no la cubre esta lectura sino el indice unico
        // `uq_movimiento_idempotencia`; ver el catch de mas abajo.
        if (clave != null) {
            Optional<ComisionMovimiento> previo = movimientos == null ? Optional.empty()
                    : movimientos.findByOrganizacionIdAndClaveIdempotencia(
                            actor.idOrganizacion(), clave);
            if (previo.isPresent()) {
                return repetido(previo.get(), huella, idContrato, actor);
            }
        }
        ComisionLiquidacion comision = cargar(idContrato, actor);
        if (ComisionLiquidacion.ANULADA.equals(comision.estadoActual())) {
            throw new ReglaNegocioException("Una comision anulada no admite movimientos.");
        }
        String codigo = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        // El AJUSTE se retira como comando: ver `ComisionService.TIPOS_ACEPTADOS`.
        if (ComisionMovimiento.AJUSTE.equals(codigo)) {
            throw new ReglaNegocioException(
                    "El ajuste no es una operacion monetaria valida: no hay una regla economica"
                            + " que defina que saldo modifica.");
        }
        if (!ComisionService.TIPOS_ACEPTADOS.contains(codigo)) {
            throw new ReglaNegocioException("Tipo de movimiento de comision invalido.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException("El monto del movimiento debe ser mayor que cero.");
        }
        String monedaMovimiento = CondicionesEconomicas.moneda(moneda, "del movimiento");
        if (!monedaMovimiento.equals(comision.getMoneda())) {
            throw new ReglaNegocioException("La moneda del movimiento debe coincidir con la liquidacion.");
        }
        if (fecha == null || fecha.isAfter(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha del movimiento es obligatoria y no puede ser futura.");
        }
        Saldos saldo = saldos(comision, movimientos(comision, actor));
        if (ComisionMovimiento.COBRO.equals(codigo) && monto.compareTo(saldo.saldoCobro()) > 0) {
            throw new ReglaNegocioException("El cobro no puede superar el saldo pendiente.");
        }
        // Una reversion devuelve dinero YA cobrado; sin este tope entraba una
        // sin cobro previo, el neto se iba a negativo y —como el saldo se
        // calcula bruto - cobrado— el saldo cobrable superaba la bruta. A
        // partir de ahi se podia cobrar mas que la comision pactada, con el
        // clamp .max(ZERO) mostrando un cobrado de cero que ocultaba el hueco.
        if (ComisionMovimiento.REVERSION.equals(codigo)
                && monto.compareTo(saldo.montoCobrado()) > 0) {
            throw new ReglaNegocioException("La reversion no puede superar lo cobrado.");
        }
        if (ComisionMovimiento.PAGO_AGENTE.equals(codigo)) {
            if (comision.getMontoAgente() == null) {
                throw new ReglaNegocioException("Asigna primero la parte del agente.");
            }
            if (monto.compareTo(saldo.saldoPagoAgente()) > 0) {
                throw new ReglaNegocioException("El pago al agente no puede superar su saldo pendiente.");
            }
        }
        registrarMovimientoInterno(comision, codigo, monto, monedaMovimiento, fecha,
                formaPago(formaPago), observacion, clave, huella, actor);
        Saldos actualizado = saldos(comision, movimientos(comision, actor));
        String destino = actualizado.montoCobrado().signum() == 0 ? ComisionLiquidacion.PENDIENTE
                : actualizado.saldoCobro().signum() == 0 ? ComisionLiquidacion.COBRADA
                : ComisionLiquidacion.PARCIAL;
        transiciones.aplicar(comision, comision.getId(), destino, actor,
                "Movimiento de comision " + codigo + ".");
        return ficha(comisiones.save(comision), movimientos(comision, actor));
    }

    @Override
    @Transactional
    public void anularPorContratoAnulado(long idContrato, Actor actor) {
        Optional<ComisionLiquidacion> encontrada =
                comisiones.porContrato(actor.idOrganizacion(), idContrato);
        if (encontrada.isEmpty()) {
            return;
        }
        ComisionLiquidacion comision = encontrada.get();
        if (ComisionLiquidacion.COBRADA.equals(comision.estadoActual())) {
            throw new ReglaNegocioException(
                    "No se puede anular un contrato cuya comision ya fue cobrada.");
        }
        if (ComisionLiquidacion.ANULADA.equals(comision.estadoActual())) {
            return;
        }
        // Los movimientos previos NO se borran: un abono parcial ya recibido
        // sigue siendo evidencia economica. Lo que se anula es lo que queda.
        transiciones.aplicar(comision, comision.getId(), ComisionLiquidacion.ANULADA, actor,
                "Contrato anulado.");
        comisiones.save(comision);
    }

    /**
     * Un comando ya ejecutado con esta misma clave. Si la huella coincide es el
     * MISMO comando —un reintento— y se devuelve su resultado; si no coincide,
     * el cliente esta reutilizando la clave para otra cosa y eso es un 409, no
     * un exito silencioso.
     */
    private FichaComision repetido(ComisionMovimiento previo, String huella, long idContrato,
                                   Actor actor) {
        if (!huella.equals(previo.getHuellaComando())) {
            throw new ConflictoException(
                    "La clave de idempotencia ya se uso para otro movimiento de comision.");
        }
        ComisionLiquidacion comision = cargar(idContrato, actor);
        return ficha(comision, movimientos(comision, actor));
    }

    private void registrarMovimientoInterno(ComisionLiquidacion comision, String tipo,
                                             BigDecimal monto, String moneda, LocalDate fecha,
                                             String formaPago, String observacion,
                                             String clave, String huella, Actor actor) {
        if (movimientos == null) return;
        ComisionMovimiento movimiento = new ComisionMovimiento();
        movimiento.setClaveIdempotencia(clave);
        movimiento.setHuellaComando(clave == null ? null : huella);
        movimiento.setOrganizacionId(comision.getOrganizacionId());
        movimiento.setLiquidacion(comision);
        movimiento.setTipo(tipo);
        movimiento.setMonto(monto);
        movimiento.setMoneda(moneda);
        movimiento.setFecha(fecha);
        movimiento.setFormaPago(formaPago);
        movimiento.setIdUsuario(actor == null ? null : actor.idPersona());
        movimiento.setRolUsuario(actor == null ? null : actor.tipoRolOperativo());
        movimiento.setObservacion(observacion == null || observacion.isBlank() ? null : observacion.trim());
        movimientos.save(movimiento);
    }

    // ------------------------------------------------------------------

    /**
     * Los dos avisos de comision al agente (§4 del contrato F6, puntos 10 y 11).
     * La "operacion" que nombra el mensaje es el CODIGO DE LA SOLICITUD, y el
     * destinatario es el agente de esa solicitud —no quien dispara la accion,
     * que es el broker—.
     *
     * <p><b>Ninguno de los dos expone el monto neto</b>: es una regla de
     * privacidad del cable, no una casualidad de redaccion. El agente ve que su
     * comision esta lista o cobrada, no cuanto le toca.
     */
    private void avisarAlAgente(ComisionLiquidacion comision, String tipo, Actor actor,
                                java.util.function.Function<String, String> mensaje) {
        ContratoAlquiler contrato = comision.getContrato();
        if (contrato == null || contrato.getSolicitud() == null) {
            return;
        }
        var solicitud = contrato.getSolicitud();
        Long idAgente = solicitud.getAgente() != null ? solicitud.getAgente().getId() : null;
        String codigo = solicitud.getCodigoSolicitud() != null ? solicitud.getCodigoSolicitud() : "";
        alertas.emitir(new AlertaService.DatosAlerta(tipo, Alerta.INFO, "CONTRATO_ALQUILER",
                contrato.getId(), idAgente, mensaje.apply(codigo)), actor);
    }

    /** 404 con el recurso exacto del cable: "Liquidacion de comision no encontrado.". */
    private ComisionLiquidacion cargar(long idContrato, Actor actor) {
        return comisiones.porContrato(actor.idOrganizacion(), idContrato)
                .orElseThrow(() -> new NoEncontradoException("Liquidacion de comision"));
    }

    /**
     * El cobro solo admite COBRADA o ANULADA; el cable distingue "no es un
     * estado de comision" de "es un estado, pero no de cobro".
     */
    private static String estadoDeCobro(String estado) {
        String codigo = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ComisionLiquidacion.ESTADOS.contains(codigo)) {
            throw new ReglaNegocioException("Estado de cobro invalido.");
        }
        if (!ComisionLiquidacion.ESTADOS_DE_COBRO.contains(codigo)) {
            throw new ReglaNegocioException("El cobro solo admite los estados Cobrada o Anulada.");
        }
        return codigo;
    }

    /** Opcional: solo se exige cuando el desenlace es COBRADA. */
    private static String formaPago(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String codigo = valor.trim().toUpperCase(Locale.ROOT);
        if (!ComisionLiquidacion.FORMAS_PAGO.contains(codigo)) {
            throw new ReglaNegocioException("Forma de pago invalida.");
        }
        return codigo;
    }

    private List<ComisionMovimiento> movimientos(ComisionLiquidacion c, Actor actor) {
        return movimientos == null || c.getId() == null ? List.of()
                : movimientos.findByOrganizacionIdAndLiquidacionIdOrderByFechaAscIdAsc(
                        actor.idOrganizacion(), c.getId());
    }

    private record Saldos(BigDecimal montoCobrado, BigDecimal saldoCobro,
                          BigDecimal pagadoAgente, BigDecimal saldoPagoAgente,
                          LocalDate ultimaFechaCobro, String ultimaFormaPago) { }

    private static Saldos saldos(ComisionLiquidacion c, List<ComisionMovimiento> movimientos) {
        BigDecimal cobrado = BigDecimal.ZERO;
        BigDecimal pagado = BigDecimal.ZERO;
        LocalDate fecha = null;
        String forma = null;
        for (ComisionMovimiento m : movimientos) {
            BigDecimal signo = ComisionMovimiento.REVERSION.equals(m.getTipo())
                    ? m.getMonto().negate() : m.getMonto();
            if (ComisionMovimiento.COBRO.equals(m.getTipo()) || ComisionMovimiento.REVERSION.equals(m.getTipo())) {
                cobrado = cobrado.add(signo);
                if (ComisionMovimiento.COBRO.equals(m.getTipo())) { fecha = m.getFecha(); forma = m.getFormaPago(); }
            } else if (ComisionMovimiento.PAGO_AGENTE.equals(m.getTipo())) {
                pagado = pagado.add(m.getMonto());
            }
        }
        BigDecimal bruto = c.getMonto() == null ? BigDecimal.ZERO : c.getMonto();
        BigDecimal parteAgente = c.getMontoAgente() == null ? BigDecimal.ZERO : c.getMontoAgente();
        return new Saldos(cobrado.max(BigDecimal.ZERO), bruto.subtract(cobrado).max(BigDecimal.ZERO),
                pagado.max(BigDecimal.ZERO), parteAgente.subtract(pagado).max(BigDecimal.ZERO), fecha, forma);
    }

    private static FichaComision ficha(ComisionLiquidacion c, List<ComisionMovimiento> movimientos) {
        Saldos saldos = saldos(c, movimientos);
        return new FichaComision(
                c.getId(),
                c.getContrato() != null ? c.getContrato().getId() : null,
                c.getMonto(), c.getMoneda(), c.getMontoAgente(), c.getMontoEmpresa(),
                saldos.ultimaFechaCobro(), saldos.ultimaFormaPago(), c.estadoActual(),
                saldos.montoCobrado(), saldos.saldoCobro(), saldos.pagadoAgente(),
                saldos.saldoPagoAgente());
    }
}
