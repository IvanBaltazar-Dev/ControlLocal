package com.controllocal.web.controlador;

import com.controllocal.service.InteraccionService;
import com.controllocal.service.actividad.CatalogoInteracciones;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.ResultadoAutomatico;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.dto.InteraccionResponse;
import com.controllocal.web.http.PageResponse;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.controllocal.service.actividad.CatalogoInteracciones.*;

/** C4: una identidad, vínculos autorizados y rectificación conservando historia. */
@RestController @RequestMapping("interacciones")
public class InteraccionesController {
    private final InteraccionService interacciones;
    private final ComandosAutomaticosService comandos;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public InteraccionesController(InteraccionService i,ComandosAutomaticosService c,ActuacionesService a,ProcedenciaDeCabeceras p) {
        interacciones=i; comandos=c; actuaciones=a; procedencias=p;
    }
    @GetMapping("definicion") @CapacidadReconocida("definicion_de_interacciones")
    public CatalogoInteracciones.Definicion definicion() { return interacciones.definicion(SesionActual.actor()); }
    @GetMapping
    public PageResponse<InteraccionResponse> listar(@RequestParam(defaultValue="1") int pagina,@RequestParam(defaultValue="50") int tamano,
            @RequestParam(required=false) InteraccionService.TipoContexto contexto,@RequestParam(required=false) Long referencia,
            @RequestParam(required=false) TipoActividad tipo,@RequestParam(required=false) ResultadoInteraccion resultado,
            @RequestParam(required=false) CanalComunicacion canal,@RequestParam(required=false) String q) {
        var p=interacciones.listar(new InteraccionService.FiltrosInteraccion(contexto,referencia,tipo,resultado,canal,q,pagina,tamano),SesionActual.actor());
        return new PageResponse<>(p.items().stream().map(InteraccionResponse::desde).toList(),p.total(),Math.max(1,pagina),Math.max(1,Math.min(100,tamano)));
    }
    @GetMapping("{id}")
    public InteraccionResponse obtener(@PathVariable long id) { return InteraccionResponse.desde(interacciones.obtener(id,SesionActual.actor())); }
    @GetMapping("{id}/historia")
    public List<InteraccionService.Antecedente> historia(@PathVariable long id) {return interacciones.historia(id,SesionActual.actor());}
    @PostMapping @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_interaccion")
    public ResultadoAutomatico<InteraccionService.FichaInteraccion> registrar(@RequestBody InteraccionService.DatosInteraccion datos,
            @RequestHeader("Idempotency-Key") String clave,HttpServletRequest r) {
        return comandos.registrarInteraccion(datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),ContextoDeActuacion.correlacionDirecta(clave),procedencias.de(r));
    }
    @PostMapping("{id}/rectificacion/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("rectificar_interaccion")
    public ActuacionesService.Propuesta rectificar(@PathVariable long id,@RequestBody InteraccionService.Rectificacion datos,HttpServletRequest r) {
        if(id!=datos.idInteraccion())throw new ReglaNegocioException("La interacción debe coincidir con la ruta.");
        return actuaciones.prepararRectificacionInteraccion(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("{id}/vinculos") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("vincular_interaccion")
    public ResultadoAutomatico<InteraccionService.FichaInteraccion> vincular(@PathVariable long id,@RequestBody InteraccionService.AdicionVinculo datos,
            @RequestHeader("Idempotency-Key") String clave,HttpServletRequest r) {
        return comandos.vincularInteraccion(id,datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),ContextoDeActuacion.correlacionDirecta(clave),procedencias.de(r));
    }
    @PostMapping("{id}/observaciones") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("anotar_observacion_interaccion")
    public ResultadoAutomatico<InteraccionService.FichaInteraccion> observar(@PathVariable long id,@RequestBody InteraccionService.ObservacionPosterior datos,
            @RequestHeader("Idempotency-Key") String clave,HttpServletRequest r) {
        return comandos.anotarObservacionInteraccion(id,datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),ContextoDeActuacion.correlacionDirecta(clave),procedencias.de(r));
    }
}
