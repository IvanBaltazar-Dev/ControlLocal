package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.web.dto.*;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController @RequestMapping("indicadores")
public class IndicadoresController {
    private final IndicadorService indicadores;private final RendimientoComercialService rendimiento;private final MetaComercialService metas;
    private final ActuacionesService actuaciones;private final ProcedenciaDeCabeceras procedencias;
    public IndicadoresController(IndicadorService i,RendimientoComercialService r,MetaComercialService m,ActuacionesService a,ProcedenciaDeCabeceras p){indicadores=i;rendimiento=r;metas=m;actuaciones=a;procedencias=p;}
    @GetMapping("resumen") public IndicadoresResponse resumen(@RequestParam(required=false)String periodo,@RequestParam(required=false)String mes){var a=SesionActual.actor();return IndicadoresResponse.desde(indicadores.resumen(periodo,a),RendimientoResponse.desde(rendimiento.del(mes,a)));}
    @GetMapping("metas/definicion") @CapacidadReconocida("definicion_de_metas")
    public MetaComercialService.Definicion definicion(){return metas.definicion(SesionActual.actor());}
    @GetMapping("metas") @PreAuthorize("hasAnyRole('AGENTE','BROKER')") @CapacidadReconocida("consultar_metas_comerciales")
    public List<MetaComercialService.MetaDeAgente> metas(@RequestParam(required=false)String mes){return metas.del(mes,SesionActual.actor());}
    @PostMapping("metas/establecimiento/propuestas") @PreAuthorize("hasAnyRole('AGENTE','BROKER')") @CapacidadReconocida("establecer_meta")
    public ActuacionesService.Propuesta fijarMetas(@RequestBody MetaComercialService.Establecer datos,HttpServletRequest r){return actuaciones.prepararEstablecimientoMetas(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));}
    public record NuevaPropuesta(String mes,MetaComercialService.Propuesta propuesta){@com.fasterxml.jackson.annotation.JsonAnySetter public void desconocido(String k,Object v){throw new com.controllocal.service.excepcion.ReglaNegocioException("Campo de propuesta de meta no admitido.");}}
    @PostMapping("metas/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("proponer_meta")
    public MetaComercialService.Resultado proponerMeta(@RequestBody NuevaPropuesta d,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){return metas.proponer(d==null?null:d.mes(),d==null?null:d.propuesta(),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r));}
    @GetMapping("metas/propuestas") @PreAuthorize("hasAnyRole('AGENTE','BROKER')") @CapacidadReconocida("consultar_propuestas_metas")
    public List<MetaComercialService.PropuestaMeta> propuestas(){return metas.propuestasPendientes(SesionActual.actor());}
    @GetMapping("metas/propuestas/{id}") @PreAuthorize("hasAnyRole('AGENTE','BROKER')") @CapacidadReconocida("consultar_propuesta_meta")
    public MetaComercialService.PropuestaMeta propuesta(@PathVariable long id){return metas.consultarPropuesta(id,SesionActual.actor());}
    @PostMapping("metas/decisiones/propuestas") @PreAuthorize("hasAnyRole('AGENTE','BROKER')") @CapacidadReconocida("decidir_propuesta_meta")
    public ActuacionesService.Propuesta decidir(@RequestBody MetaComercialService.Decision d,HttpServletRequest r){return actuaciones.prepararDecisionMeta(SesionActual.actor(),d,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));}
    @GetMapping("metas/rendimiento") @PreAuthorize("hasAnyRole('AGENTE','BROKER')") @CapacidadReconocida("consultar_avance_metas")
    public RendimientoComercialService.Rendimiento rendimiento(@RequestParam(required=false)String mes){return rendimiento.del(mes,SesionActual.actor());}
    @GetMapping("metas/pulso") @PreAuthorize("hasRole('BROKER')") @CapacidadReconocida("consultar_pulso_equipo")
    public RendimientoComercialService.Pulso pulso(@RequestParam(required=false)String mes){return rendimiento.pulso(mes,SesionActual.actor());}
    @GetMapping("avance") public AvanceComercialResponse avance(){return AvanceComercialResponse.desde(indicadores.avance(SesionActual.actor()));}
}
