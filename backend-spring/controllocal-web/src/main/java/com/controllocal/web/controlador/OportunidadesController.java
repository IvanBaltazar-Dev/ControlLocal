package com.controllocal.web.controlador;

import com.controllocal.service.OportunidadService;
import com.controllocal.service.Pagina;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.dto.OportunidadResponse;
import com.controllocal.web.dto.ResumenOportunidadesResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

/** RF040/042/050: apertura y no continuidad se confirman mediante revisión exacta. */
@RestController @RequestMapping("oportunidades")
public class OportunidadesController {
    private final OportunidadService oportunidades;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public OportunidadesController(OportunidadService o,ActuacionesService a,ProcedenciaDeCabeceras p) {
        oportunidades=o; actuaciones=a; procedencias=p;
    }
    @GetMapping
    public PageResponse<OportunidadResponse> listar(@RequestParam(defaultValue="1") int pagina,
            @RequestParam(defaultValue="10") int tamano,@RequestParam(required=false) Long idCaptacion,
            @RequestParam(required=false) Long idCliente,@RequestParam(required=false) String estado,
            @RequestParam(required=false) String query) {
        Pagina<OportunidadService.FichaOportunidad> p=oportunidades.listar(pagina,tamano,idCaptacion,idCliente,estado,query,SesionActual.actor());
        return new PageResponse<>(p.items().stream().map(OportunidadResponse::desde).toList(),p.total(),Math.max(1,pagina),Math.max(1,Math.min(100,tamano)));
    }
    @GetMapping("resumen")
    public ResumenOportunidadesResponse resumen(@RequestParam(required=false) Long idCaptacion,
            @RequestParam(required=false) Long idCliente,@RequestParam(required=false) String query) {
        return ResumenOportunidadesResponse.desde(oportunidades.resumen(idCaptacion,idCliente,query,SesionActual.actor()));
    }
    @GetMapping("{id}")
    public OportunidadResponse obtener(@PathVariable long id) {
        return OportunidadResponse.desde(oportunidades.obtener(id,SesionActual.actor()));
    }
    @GetMapping("{id}/secciones/{seccion}") @CapacidadReconocida("consultar_oportunidad")
    public OportunidadService.ConsultaOportunidad consultar(@PathVariable long id,
            @PathVariable OportunidadService.Seccion seccion,@RequestParam(required=false) Instant corte) {
        return oportunidades.consultar(id,seccion,corte,SesionActual.actor());
    }
    @PostMapping("propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("abrir_oportunidad")
    public ActuacionesService.Propuesta registrar(@RequestBody OportunidadService.DatosOportunidad datos,HttpServletRequest r) {
        return actuaciones.prepararAperturaOportunidad(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("{id}/no-continuidad/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_no_continuidad_oportunidad")
    public ActuacionesService.Propuesta noContinuidad(@PathVariable long id,@RequestBody OportunidadService.NoContinuidad datos,HttpServletRequest r) {
        if(id!=datos.idOportunidad())throw new ReglaNegocioException("La oportunidad debe coincidir con la ruta.");
        return actuaciones.prepararNoContinuidadOportunidad(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
}
