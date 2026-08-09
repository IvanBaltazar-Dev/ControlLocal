package com.controllocal.web.controlador;

import com.controllocal.service.IndicadorService;
import com.controllocal.web.dto.AvanceComercialResponse;
import com.controllocal.web.dto.IndicadoresResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato CONGELADO E4 del {@code IndicadoresRest} Jakarta.
 *
 * <p><b>Sin gate de rol</b>, igual que la v1: los tres roles entran y cada uno
 * recibe su alcance —el ADMIN el tenant, el BROKER su equipo, el AGENTE lo
 * suyo—. Lo que cambia por rol es el contenido y el {@code ambito}, no el
 * acceso.
 *
 * <p>{@code GET /indicadores/reporte/pdf} de la v1 <b>no se porta</b>, y ya no
 * es un diferido: los reportes PDF quedaron FUERA DEL ALCANCE de la migracion
 * (D-F5-1, {@code docs/ai/decision-reportes-pdf-fuera-de-alcance.md}). El JSON
 * que lo alimentaba es este mismo {@code /resumen}, asi que cuando se diseñe la
 * nueva pagina de reportes no hara falta consulta nueva.
 */
@RestController
@RequestMapping("indicadores")
public class IndicadoresController {

    private final IndicadorService indicadores;

    public IndicadoresController(IndicadorService indicadores) {
        this.indicadores = indicadores;
    }

    /**
     * Un solo GET alimenta tarjetas, graficas, embudo, desempeno y los pills del
     * menu. {@code periodo} acepta 7d/15d/1m/3m/1y y sus sinonimos; cualquier
     * otro valor —ausente incluido— cae en 6 meses.
     */
    @GetMapping("resumen")
    public IndicadoresResponse resumen(@RequestParam(required = false) String periodo) {
        return IndicadoresResponse.desde(indicadores.resumen(periodo, SesionActual.actor()));
    }

    /** RF-017: avance comercial por propiedad. Es acumulado, no acepta periodo. */
    @GetMapping("avance")
    public AvanceComercialResponse avance() {
        return AvanceComercialResponse.desde(indicadores.avance(SesionActual.actor()));
    }
}
