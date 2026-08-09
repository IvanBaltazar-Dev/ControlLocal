package com.controllocal.web.dto;

import com.controllocal.service.AgenteService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ficha individual del agente. <b>Extensión aditiva</b>: la v1 no tenía
 * {@code GET /agentes/{id}} y su pantalla de detalle solo mostraba los datos
 * personales, así que aquí no hay contrato congelado que respetar — pero sí la
 * convención de la casa: códigos de una letra tal cual, nulos omitidos por
 * Jackson y nombres del cable.
 *
 * <p>Responde de una vez lo que si no habría que reconstruir combinando páginas
 * de cuatro bandejas distintas, que además daría números falsos: cada listado
 * pagina, y contar sobre la página visible no es contar.
 */
public record AgenteFichaResponse(AgenteResponse agente,
                                  Supervision supervision,
                                  List<ConteoEstado> captaciones,
                                  List<ConteoEstado> oportunidades,
                                  List<ConteoEstado> solicitudes,
                                  long cierres,
                                  Comisiones comisiones,
                                  List<Cierre> ultimosCierres) {

    /** Supervisión vigente. Ausente si hoy nadie supervisa al agente. */
    public record Supervision(Long idBroker, String brokerNombre, String codigoBroker,
                              LocalDate fechaAsignacion, String motivo) {
    }

    public record ConteoEstado(String estado, String descripcion, long total) {
    }

    public record Importe(String moneda, BigDecimal monto) {
    }

    /**
     * Las cuatro magnitudes van SEPARADAS y por moneda porque responden
     * preguntas distintas: lo pactado no es lo cobrado, y lo que le toca al
     * agente no es lo que ya se le pagó. PEN y USD nunca se suman.
     */
    public record Comisiones(List<Importe> generada, List<Importe> cobrada,
                             List<Importe> pendienteCobro, List<Importe> asignadaAgente,
                             List<Importe> pagadaAgente, List<Importe> pendientePagoAgente) {
    }

    /** Cierre atribuido al agente por V27, no por la cadena solicitud→agente. */
    public record Cierre(Long idContrato, String codigoSolicitud, String direccionLocal,
                         String distrito, String clienteNombre, LocalDate fechaCierre,
                         String estadoContrato, BigDecimal rentaContractual, String moneda) {
    }

    public static AgenteFichaResponse desde(AgenteService.FichaCompletaAgente f) {
        return new AgenteFichaResponse(
                AgenteResponse.desde(f.agente()),
                f.supervision() == null ? null
                        : new Supervision(f.supervision().idBroker(),
                                f.supervision().brokerNombre(), f.supervision().codigoBroker(),
                                f.supervision().fechaAsignacion(), f.supervision().motivo()),
                conteos(f.captaciones()), conteos(f.oportunidades()), conteos(f.solicitudes()),
                f.cierres(),
                new Comisiones(
                        importes(f.comisiones().generada()),
                        importes(f.comisiones().cobrada()),
                        importes(f.comisiones().pendienteCobro()),
                        importes(f.comisiones().asignadaAgente()),
                        importes(f.comisiones().pagadaAgente()),
                        importes(f.comisiones().pendientePagoAgente())),
                f.ultimosCierres().stream()
                        .map(c -> new Cierre(c.idContrato(), c.codigoSolicitud(),
                                c.direccionLocal(), c.distrito(), c.clienteNombre(),
                                c.fechaCierre(), c.estadoContrato(), c.comisionGenerada(),
                                c.monedaComision()))
                        .toList());
    }

    private static List<ConteoEstado> conteos(List<AgenteService.ConteoEstado> filas) {
        return filas.stream()
                .map(c -> new ConteoEstado(c.estado(), c.descripcion(), c.total()))
                .toList();
    }

    private static List<Importe> importes(List<AgenteService.ImportePorMoneda> filas) {
        return filas.stream().map(i -> new Importe(i.moneda(), i.monto())).toList();
    }
}
