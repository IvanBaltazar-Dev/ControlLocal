package com.controllocal.web.dto;

import com.controllocal.service.ContratoService;

import java.math.BigDecimal;
import java.util.List;

/**
 * KPI de la pantalla de cierres exitosos. Extension ADITIVA, misma razon que
 * {@code /locales/resumen}: los totales se calculan en la BASE.
 *
 * <p>Aqui la razon es mas fuerte todavia: el tope de pagina de
 * {@code /contratos} es 100, asi que una suma de comision hecha en el cliente
 * seria falsa en cuanto la corredora pase de 100 cierres — y no lo avisaria.
 *
 * <p>Cada monto viaja junto a su moneda y las sumas se agrupan por ella. Nunca
 * se mezclan PEN y USD ni se aplica una moneda por defecto en la pantalla.
 */
public record ResumenCierresResponse(long cierres, long cierresConLecturaFinanciera,long sinEvaluacion,
                                     List<ImporteMoneda> calculado,
                                     List<ImporteMoneda> devengadoAcreditado,
                                     List<ImporteMoneda> ingresosRealmenteRecibidos,
                                     List<ImporteMoneda> saldoInformativoSobreCalculo,
                                     List<String> distritosDisponibles,
                                     List<AgenteResumen> agentesDisponibles) {

    public record ImporteMoneda(String moneda, BigDecimal monto,long determinados,long pendientes) {
    }

    /** El filtro por agente se manda por id: dos agentes pueden llamarse igual. */
    public record AgenteResumen(long id, String nombre) {
    }

    public static ResumenCierresResponse desde(ContratoService.ResumenCierres r,
                                               List<String> distritos,
                                               List<ContratoService.AgenteConCierres> agentes) {
        return new ResumenCierresResponse(r.cierres(),r.cierresConLecturaFinanciera(),r.sinEvaluacion(),
                importes(r.calculado()),importes(r.devengadoAcreditado()),
                importes(r.ingresosRealmenteRecibidos()),importes(r.saldoInformativoSobreCalculo()),distritos,
                agentes.stream().map(a -> new AgenteResumen(a.id(), a.nombre())).toList());
    }

    private static List<ImporteMoneda> importes(List<ContratoService.ImportePorMoneda> valores) {
        return valores.stream().map(i -> new ImporteMoneda(i.moneda(),i.monto(),i.determinados(),i.pendientes())).toList();
    }
}
