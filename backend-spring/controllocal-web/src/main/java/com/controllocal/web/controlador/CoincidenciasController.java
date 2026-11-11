package com.controllocal.web.controlador;

import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.CoincidenciaService.*;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.dto.CoincidenciaResponse;
import com.controllocal.web.dto.CoincidenciasResponse;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Set;

/** AC091–094/099/149. La versión y el origen son explícitos; no escoge una demanda por cliente. */
@RestController
@RequestMapping("coincidencias")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class CoincidenciasController {
    private final CoincidenciaService servicio;
    public CoincidenciasController(CoincidenciaService servicio){this.servicio=servicio;}
    @GetMapping
    @CapacidadReconocida("buscar_coincidencias")
    public CoincidenciasResponse consultar(@RequestParam Origen origen,@RequestParam Long idOrigen,
            @RequestParam Long versionOrigen,@RequestParam TipoVersion tipoVersion,@RequestParam Modalidad modalidad,
            @RequestParam Set<Capa> capas,@RequestParam Categoria categoria,@RequestParam(required=false) Integer tamano,
            @RequestParam(required=false) ModoExploracion modoExploracion,
            @RequestParam(required=false) BigDecimal limiteExploracion,@RequestParam(required=false) String monedaExploracion) {
        ExploracionPrecio exploracion=null;
        if(modoExploracion!=null||limiteExploracion!=null||monedaExploracion!=null) {
            if(modoExploracion==null)throw new com.controllocal.service.excepcion.ReglaNegocioException(
                    "Indica el modo de exploración para utilizar su límite o moneda.");
            exploracion=new ExploracionPrecio(modoExploracion,limiteExploracion,monedaExploracion);
        }
        return CoincidenciasResponse.desde(servicio.consultar(new CoincidenciaService.Consulta(origen,idOrigen,versionOrigen,
                tipoVersion,modalidad,capas,categoria,tamano,exploracion),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion())));
    }
    @GetMapping("continuacion/{cursor}")
    @CapacidadReconocida("continuar_coincidencias")
    public CoincidenciasResponse continuar(@PathVariable String cursor) {
        return CoincidenciasResponse.desde(servicio.continuar(cursor,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion())));
    }
    @GetMapping("explicaciones/{cursor}/{referenciaDemanda}/{referencia}")
    @CapacidadReconocida("explicar_match")
    public CoincidenciaResponse explicar(@PathVariable String cursor,@PathVariable String referenciaDemanda,@PathVariable String referencia) {
        return CoincidenciaResponse.desde(servicio.explicar(cursor,referenciaDemanda,referencia,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion())));
    }
}
