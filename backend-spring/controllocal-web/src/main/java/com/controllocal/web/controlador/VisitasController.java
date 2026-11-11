package com.controllocal.web.controlador;

import com.controllocal.service.Pagina;
import com.controllocal.service.VisitaService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.ResultadoAutomatico;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.dto.VisitaResponse;
import com.controllocal.web.dto.ResumenVisitasResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

/** RF043–045: agenda, ocurrencia real y cancelación tienen casos y recibos propios. */
@RestController @RequestMapping("visitas")
public class VisitasController {
    private final VisitaService visitas;
    private final ComandosAutomaticosService comandos;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public VisitasController(VisitaService v,ComandosAutomaticosService c,ActuacionesService a,ProcedenciaDeCabeceras p) {
        visitas=v; comandos=c; actuaciones=a; procedencias=p;
    }
    @GetMapping
    public PageResponse<VisitaResponse> listar(@RequestParam(defaultValue="1") int pagina,@RequestParam(defaultValue="10") int tamano,
            @RequestParam(required=false) Long idOportunidad,@RequestParam(required=false) String estado,
            @RequestParam(required=false) String distrito,@RequestParam(required=false) String query) {
        return pagina(visitas.listar(new VisitaService.FiltrosVisita(idOportunidad,estado,distrito,query,pagina,tamano),SesionActual.actor()),pagina,tamano);
    }
    @GetMapping("resumen")
    public ResumenVisitasResponse resumen(@RequestParam(required=false) Long idOportunidad,@RequestParam(required=false) String query) {
        return ResumenVisitasResponse.desde(visitas.resumen(new VisitaService.FiltrosVisita(idOportunidad,null,null,query,1,1),SesionActual.actor()));
    }
    @GetMapping("agenda") @CapacidadReconocida("consultar_agenda_visitas")
    public PageResponse<VisitaResponse> agenda(@RequestParam LocalDate desde,@RequestParam LocalDate hasta,
            @RequestParam(required=false) Long idOportunidad,@RequestParam(required=false) String estado,
            @RequestParam(defaultValue="1") int pagina,@RequestParam(defaultValue="10") int tamano) {
        return pagina(visitas.agenda(new VisitaService.ConsultaAgenda(desde,hasta,idOportunidad,estado,pagina,tamano),SesionActual.actor()),pagina,tamano);
    }
    @GetMapping("proximas")
    public PageResponse<VisitaResponse> proximas(@RequestParam(defaultValue="8") int tamano) {
        return pagina(visitas.proximas(tamano,SesionActual.actor()),1,Math.min(Math.max(1,tamano),8));
    }
    @GetMapping("mes")
    public PageResponse<VisitaResponse> mes(@RequestParam int anio,@RequestParam int mes) {
        var p=visitas.mes(anio,mes,SesionActual.actor()); return pagina(p,1,Math.max(p.items().size(),1));
    }
    @GetMapping("{id}")
    public VisitaResponse obtener(@PathVariable long id) { return VisitaResponse.desde(visitas.obtener(id,SesionActual.actor())); }
    @GetMapping("{id}/historia")
    public List<VisitaService.Antecedente> historia(@PathVariable long id) {return visitas.historia(id,SesionActual.actor());}
    @PostMapping @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("programar_visita")
    public ResultadoAutomatico<VisitaService.FichaVisita> programar(@RequestBody VisitaService.DatosVisita datos,
            @RequestHeader("Idempotency-Key") String clave,HttpServletRequest r) {
        return comandos.programarVisita(datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),ContextoDeActuacion.correlacionDirecta(clave),procedencias.de(r));
    }
    @PutMapping("{id}/programacion") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("reprogramar_visita")
    public ResultadoAutomatico<VisitaService.FichaVisita> reprogramar(@PathVariable long id,@RequestBody VisitaService.Reprogramacion datos,
            @RequestHeader("Idempotency-Key") String clave,HttpServletRequest r) {
        if(id!=datos.idVisita())throw new ReglaNegocioException("La visita debe coincidir con la ruta.");
        return comandos.reprogramarVisita(datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),ContextoDeActuacion.correlacionDirecta(clave),procedencias.de(r));
    }
    @PostMapping("{id}/cancelacion/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("cancelar_visita")
    public ActuacionesService.Propuesta cancelar(@PathVariable long id,@RequestBody VisitaService.Cancelacion datos,HttpServletRequest r) {
        if(id!=datos.idVisita())throw new ReglaNegocioException("La visita debe coincidir con la ruta.");
        return actuaciones.prepararCancelacionVisita(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("{id}/resultado") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_resultado_visita")
    public ResultadoAutomatico<VisitaService.FichaVisita> resultado(@PathVariable long id,@RequestBody VisitaService.DesenlaceVisita datos,
            @RequestHeader("Idempotency-Key") String clave,HttpServletRequest r) {
        return comandos.registrarResultadoVisita(id,datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),ContextoDeActuacion.correlacionDirecta(clave),procedencias.de(r));
    }
    private static PageResponse<VisitaResponse> pagina(Pagina<VisitaService.FichaVisita> p,int pagina,int tamano) {
        return new PageResponse<>(p.items().stream().map(VisitaResponse::desde).toList(),p.total(),Math.max(1,pagina),Math.max(1,Math.min(100,tamano)));
    }
}
