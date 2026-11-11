package com.controllocal.web.controlador;

import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.documentos.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @PreAuthorize("hasRole('AGENTE')")
public class PiezasController {
    private final PiezasComercialesService piezas;private final FotosComercialesService fotos;
    private final ActuacionesService actuaciones;private final ProcedenciaDeCabeceras procedencias;
    public PiezasController(PiezasComercialesService piezas,FotosComercialesService fotos,ActuacionesService actuaciones,ProcedenciaDeCabeceras procedencias){
        this.piezas=piezas;this.fotos=fotos;this.actuaciones=actuaciones;this.procedencias=procedencias;
    }
    @GetMapping("encargos/{idEncargo}/piezas/plantillas") @CapacidadReconocida("consultar_plantillas_y_formatos_pieza")
    public Map<String,Object> plantillas(@PathVariable long idEncargo){return piezas.consultarPlantillasYFormatos(idEncargo,SesionActual.actor());}
    @GetMapping("encargos/{idEncargo}/piezas/fotos") @CapacidadReconocida("consultar_fotos_permitidas_para_pieza")
    public SeleccionFotosContextualesService.Observacion fotos(@PathVariable long idEncargo,@RequestParam(required=false)Long propiedadFicha){return piezas.consultarFotosPermitidas(idEncargo,propiedadFicha,SesionActual.actor());}
    @GetMapping("piezas/{pieza}") @CapacidadReconocida("consultar_estado_pieza")
    public PiezasComercialesService.Estado estado(@PathVariable UUID pieza){return piezas.consultarEstado(pieza,SesionActual.actor());}
    @PostMapping("encargos/{idEncargo}/piezas") @CapacidadReconocida("preparar_pieza_comercial")
    public PiezasComercialesService.Estado preparar(@PathVariable long idEncargo,@RequestBody PiezasComercialesService.Preparacion datos,
            @RequestHeader("Idempotency-Key")String clave,HttpServletRequest request){
        if(datos.encargo()!=idEncargo)throw new ReglaNegocioException("El Encargo de la ruta debe coincidir con la preparación.");
        return piezas.preparar(datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(request));
    }
    public record Vista(@com.fasterxml.jackson.annotation.JsonProperty(value="versionContenido",required=true)
            @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL) long versionContenido) { }
    @PostMapping("piezas/{pieza}/previsualizacion") @CapacidadReconocida("previsualizar_pieza_comercial")
    public PiezasComercialesService.Archivo previsualizar(@PathVariable UUID pieza,@RequestBody Vista datos,
            @RequestHeader("Idempotency-Key")String clave,HttpServletRequest request){
        return piezas.previsualizar(pieza,datos.versionContenido(),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(request));
    }
    @PostMapping("piezas/{pieza}/regeneracion") @CapacidadReconocida("regenerar_pieza_comercial")
    public PiezasComercialesService.Estado regenerar(@PathVariable UUID pieza,@RequestBody PiezasComercialesService.Regeneracion datos,
            @RequestHeader("Idempotency-Key")String clave,HttpServletRequest request){
        if(!pieza.equals(datos.pieza()))throw new ReglaNegocioException("La pieza de la ruta debe coincidir con la versión que regeneras.");
        return piezas.regenerar(datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(request));
    }
    @PostMapping("piezas/{pieza}/difusion/preparar") @CapacidadReconocida("obtener_pieza_para_difusion")
    public ActuacionesService.Propuesta obtener(@PathVariable UUID pieza,@RequestBody PiezasComercialesService.Entrega datos,HttpServletRequest request){
        if(!pieza.equals(datos.pieza()))throw new ReglaNegocioException("La pieza de la ruta debe coincidir con la que obtienes.");
        return actuaciones.prepararEntregaPieza(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(request));
    }
    @GetMapping("piezas/archivos/{archivo}") @CapacidadReconocida("descargar_archivo_pieza")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID archivo){
        var d=piezas.descargar(archivo,SesionActual.actor());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.archivo().contentType())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(d.archivo().nombre()).build().toString())
                .body(d.contenido());
    }
    @GetMapping("propiedades/{idPropiedad}/fotos/{referencia}/evaluacion") @CapacidadReconocida("evaluar_foto_local")
    public EvaluacionTecnicaFoto.Evaluacion evaluar(@PathVariable long idPropiedad,@PathVariable UUID referencia){
        return fotos.evaluar(idPropiedad,referencia,SesionActual.actor());
    }
}
