package com.controllocal.web.controlador;
import com.controllocal.service.ReportePropietarioService;
import com.controllocal.service.ReportePropietarioService.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;

/** RF078/079: obtener no comunica; rectificar utiliza revisión humana exacta. */
@RestController @PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class ReportesPropietarioController {
    private final ReportePropietarioService reportes;private final ActuacionesService actuaciones;private final ProcedenciaDeCabeceras procedencias;
    public ReportesPropietarioController(ReportePropietarioService r,ActuacionesService a,ProcedenciaDeCabeceras p){reportes=r;actuaciones=a;procedencias=p;}
    @GetMapping("captaciones/{id}/reportes-propietario") @CapacidadReconocida("consultar_reportes_propietario")
    public List<CabeceraReporte> listar(@PathVariable long id,@RequestParam(defaultValue="1")int pagina,@RequestParam(defaultValue="20")int tamano){return reportes.listar(id,pagina,tamano,SesionActual.actor());}
    @GetMapping("captaciones/{id}/reportes-propietario/avance") @CapacidadReconocida("consultar_avance_reporte")
    public VistaPrevia avance(@PathVariable long id,@RequestParam LocalDate desde,@RequestParam LocalDate hasta){return reportes.preview(id,desde,hasta,SesionActual.actor());}
    @PostMapping("captaciones/{id}/reportes-propietario") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("preparar_reporte_propietario")
    public FichaReporte preparar(@PathVariable long id,@RequestBody DatosReporte datos,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){return reportes.preparar(id,datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r));}
    @GetMapping("reportes-propietario/{referencia}/versiones/{version}") @CapacidadReconocida("previsualizar_reporte_propietario")
    public FichaReporte preview(@PathVariable UUID referencia,@PathVariable long version){return reportes.consultar(new Referencia(referencia,version),SesionActual.actor());}
    @PostMapping("reportes-propietario/{referencia}/versiones/{version}/archivo") @CapacidadReconocida("obtener_reporte_propietario")
    public Archivo archivo(@PathVariable UUID referencia,@PathVariable long version){return reportes.obtenerArchivo(new Referencia(referencia,version),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));}
    @GetMapping("reportes-propietario/antecedentes/{id}") @CapacidadReconocida("consultar_antecedente_reporte")
    public AntecedenteReporte antecedente(@PathVariable long id){return reportes.consultarAntecedente(id,SesionActual.actor());}
    @GetMapping("captaciones/{id}/reportes-propietario/comunicaciones") @CapacidadReconocida("consultar_comunicaciones_reporte")
    public List<HistorialComunicacion> comunicaciones(@PathVariable long id,@RequestParam(defaultValue="1")int pagina,@RequestParam(defaultValue="20")int tamano){return reportes.comunicaciones(id,pagina,tamano,SesionActual.actor());}
    @PostMapping("reportes-propietario/comunicaciones") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_comunicacion_reporte_realizada")
    public Comunicacion registrar(@RequestBody DatosComunicacion datos,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){return reportes.registrarComunicacion(datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r));}
    @PostMapping("reportes-propietario/comunicaciones/{referencia}/rectificacion/propuestas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("rectificar_comunicacion_reporte")
    public ActuacionesService.Propuesta rectificar(@PathVariable UUID referencia,@RequestBody Rectificacion datos,HttpServletRequest r){
        if(!referencia.equals(datos.comunicacion()))throw new ReglaNegocioException("La comunicación de la ruta debe coincidir con la rectificación.");
        return actuaciones.prepararRectificacionComunicacionReporte(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));}
}
