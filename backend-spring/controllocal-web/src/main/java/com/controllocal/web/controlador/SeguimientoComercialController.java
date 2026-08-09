package com.controllocal.web.controlador;

import com.controllocal.service.SeguimientoComercialService;
import com.controllocal.web.dto.SeguimientoComercialPageResponse;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato CONGELADO E4 del {@code SeguimientoComercialRest} Jakarta.
 *
 * <p>Cada filtro acepta hasta cuatro nombres —los {@code __eq}/{@code __contains}
 * de la convencion de filtros data-driven del legado, en ingles y en espanol, mas
 * el nombre corto— y <b>gana el primero no vacio</b>. No es cosmetico: la
 * pantalla Blazor manda unos y los enlaces profundos mandan otros, asi que
 * retirar un alias rompe una navegacion viva.
 *
 * <p>El tamano de pagina tiene <b>techo 8</b>, no solo defecto 8: pedir 50
 * devuelve 8.
 */
@RestController
@RequestMapping("seguimiento-comercial")
public class SeguimientoComercialController {

    private final SeguimientoComercialService seguimiento;

    public SeguimientoComercialController(SeguimientoComercialService seguimiento) {
        this.seguimiento = seguimiento;
    }

    @GetMapping
    public SeguimientoComercialPageResponse listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String agente,
            @RequestParam(required = false) String propietario,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String distrito,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano,
            @RequestParam(name = "process__eq", required = false) String processEq,
            @RequestParam(name = "proceso__eq", required = false) String procesoEq,
            @RequestParam(name = "query__contains", required = false) String queryContains,
            @RequestParam(name = "q__contains", required = false) String qContains,
            @RequestParam(name = "busqueda__contains", required = false) String busquedaContains,
            @RequestParam(name = "agent__eq", required = false) String agentEq,
            @RequestParam(name = "agente__eq", required = false) String agenteEq,
            @RequestParam(name = "owner__eq", required = false) String ownerEq,
            @RequestParam(name = "propietario__eq", required = false) String propietarioEq,
            @RequestParam(name = "state__eq", required = false) String stateEq,
            @RequestParam(name = "estado__eq", required = false) String estadoEq,
            @RequestParam(name = "district__eq", required = false) String districtEq,
            @RequestParam(name = "distrito__eq", required = false) String distritoEq,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        var filtros = new SeguimientoComercialService.Filtros(
                primero(processEq, procesoEq, tipo, SeguimientoComercialService.TODOS),
                primero(queryContains, qContains, busquedaContains, q),
                primero(agentEq, agenteEq, agente),
                primero(ownerEq, propietarioEq, propietario),
                primero(stateEq, estadoEq, estado),
                primero(districtEq, distritoEq, distrito),
                page != null ? page : (pagina != null ? pagina : 1),
                pageSize != null ? pageSize
                        : (tamano != null ? tamano : SeguimientoComercialService.TAMANO_MAXIMO));
        return SeguimientoComercialPageResponse.desde(
                seguimiento.listar(filtros, SesionActual.actor()));
    }

    /** Primer alias con valor; cadena vacia si ninguno vino (= sin filtro). */
    private static String primero(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return "";
    }
}
