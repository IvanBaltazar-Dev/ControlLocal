package com.controllocal.web.controlador;

import com.controllocal.service.IndicadorService;
import com.controllocal.service.MetaComercialService;
import com.controllocal.service.RendimientoComercialService;
import com.controllocal.web.dto.AvanceComercialResponse;
import com.controllocal.web.dto.IndicadoresResponse;
import com.controllocal.web.dto.DecisionMetaRequest;
import com.controllocal.web.dto.MetaResponse;
import com.controllocal.web.dto.MetasRequest;
import com.controllocal.web.dto.PropuestaMetaRequest;
import com.controllocal.web.dto.PropuestaResponse;
import com.controllocal.web.dto.RendimientoResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    private final RendimientoComercialService rendimiento;
    private final MetaComercialService metas;

    public IndicadoresController(IndicadorService indicadores,
                                 RendimientoComercialService rendimiento,
                                 MetaComercialService metas) {
        this.indicadores = indicadores;
        this.rendimiento = rendimiento;
        this.metas = metas;
    }

    /**
     * Un solo GET alimenta tarjetas, graficas, embudo, desempeno y los pills del
     * menu. {@code periodo} acepta 7d/15d/1m/3m/1y y sus sinonimos; cualquier
     * otro valor —ausente incluido— cae en 6 meses.
     *
     * <p><b>Dos parametros porque son dos periodos.</b> {@code periodo} es la
     * ventana movil de siempre y gobierna series y agregados. {@code mes}
     * ({@code AAAA-MM}, por defecto el actual) es el mes de calendario contra el
     * que se miden las metas y el ritmo, y no puede ser el mismo parametro:
     * {@code metaEsperadaAHoy} sobre una ventana movil es tautologica, porque
     * los dias transcurridos serian siempre los totales. Un solo nombre para las
     * dos cosas es como se llega a que nadie sepa que mide un numero.
     */
    @GetMapping("resumen")
    public IndicadoresResponse resumen(@RequestParam(required = false) String periodo,
                                       @RequestParam(required = false) String mes) {
        var actor = SesionActual.actor();
        return IndicadoresResponse.desde(indicadores.resumen(periodo, actor),
                RendimientoResponse.desde(rendimiento.del(mes, actor)));
    }

    /**
     * Las metas del mes que el actor alcanza, <b>incluidas las que no están
     * fijadas</b>, con su propuesta viva y su historial de revisiones.
     *
     * <p><b>Sin gate de rol, porque los tres entran y ven cosas distintas</b>: el
     * agente las suyas, el broker las de su equipo, el administrador las de su
     * organización. Ver quién no tiene meta es lo que permite arreglarlo — es la
     * cobertura incompleta que deja al equipo sin semáforo.
     */
    @GetMapping("metas")
    public List<MetaResponse> metas(@RequestParam(required = false) String mes) {
        return metas.del(mes, SesionActual.actor()).stream().map(MetaResponse::desde).toList();
    }

    /**
     * Fija o revisa metas. <b>Solo el broker.</b>
     *
     * <p>Un agente no fija la suya: si pudiera, el indicador sería manipulable
     * —voy al 60 %, bajo la meta y vuelvo a verde—. Y un administrador tampoco,
     * porque <b>administrar usuarios no es dirigir producción</b>: es la misma
     * frontera entre gobernar y operar que el resto del sistema respeta. Un
     * administrador que además dirija comercialmente lo hace con su rol de
     * broker.
     *
     * <p>Tampoco existe «meta del equipo»: la del equipo es la suma de las de sus
     * agentes, así que se reparte por agente o no se fija.
     *
     * <p>Cada asignación exige <b>motivo</b>, y queda en la serie de revisiones:
     * sin eso, dentro de tres meses la base diría que la meta siempre fue 6.
     */
    @PutMapping("metas")
    @PreAuthorize("hasRole('BROKER')")
    public List<MetaResponse> fijarMetas(@RequestBody MetasRequest dto) {
        return metas.fijar(dto == null ? null : dto.mes(),
                        dto == null ? List.of() : dto.aDatos(), SesionActual.actor())
                .stream().map(MetaResponse::desde).toList();
    }

    /**
     * El agente pide un ajuste de <b>su</b> meta. No la cambia: queda en espera.
     *
     * <p>Es la mitad de la política que impide manipular el indicador sin
     * volverlo inmutable, que tampoco sirve: hay vacaciones, altas a mitad de
     * mes, cambios de cartera y bajas.
     */
    @PostMapping("metas/propuestas")
    @PreAuthorize("hasRole('AGENTE')")
    public List<MetaResponse> proponerMeta(@RequestBody PropuestaMetaRequest dto) {
        return metas.proponer(dto == null ? null : dto.mes(),
                        dto == null ? null : dto.aDatos(), SesionActual.actor())
                .stream().map(MetaResponse::desde).toList();
    }

    /** Lo que espera una decisión del broker sobre las metas de su equipo. */
    @GetMapping("metas/propuestas")
    @PreAuthorize("hasAnyRole('BROKER', 'TENANT_ADMIN')")
    public List<PropuestaResponse> propuestasDeMeta() {
        return metas.propuestasPendientes(SesionActual.actor()).stream()
                .map(PropuestaResponse::desde)
                .toList();
    }

    /**
     * El broker acepta o rechaza una propuesta.
     *
     * <p>El motivo se exige en los dos casos: rechazar sin explicar deja al
     * agente con un «no» del que no puede aprender, y deja un hueco en el
     * historial justo donde hubo una decisión.
     */
    @PostMapping("metas/propuestas/{id}/decision")
    @PreAuthorize("hasRole('BROKER')")
    public List<MetaResponse> decidirPropuesta(@PathVariable long id,
                                               @RequestBody DecisionMetaRequest dto) {
        return metas.resolver(id, dto != null && dto.acepta(), dto == null ? null : dto.motivo(),
                        SesionActual.actor())
                .stream().map(MetaResponse::desde).toList();
    }

    /** RF-017: avance comercial por propiedad. Es acumulado, no acepta periodo. */
    @GetMapping("avance")
    public AvanceComercialResponse avance() {
        return AvanceComercialResponse.desde(indicadores.avance(SesionActual.actor()));
    }
}
