package com.controllocal.web.controlador;

import com.controllocal.service.materialia.MaterialIaService;
import com.controllocal.service.materialia.ResultadosMaterialIa;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa;
import com.controllocal.service.materialia.FuentesMaterialIaCore;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.dto.MaterialIaDtos;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Entradas sin transacción envolvente: el coordinador conserva identidad, idempotencia y sus puertas reales. */
@RestController
@PreAuthorize("hasRole('AGENTE')")
public class MaterialIaController {
    private final MaterialIaService material;
    private final ResultadosMaterialIa resultados;
    private final FuentesMaterialIaCore fuentes;
    private final EvidenciasTratamientoMaterialIa evidencias;
    private final ProcedenciaDeCabeceras procedencias;
    public MaterialIaController(MaterialIaService material,ResultadosMaterialIa resultados,FuentesMaterialIaCore fuentes,
            EvidenciasTratamientoMaterialIa evidencias,ProcedenciaDeCabeceras procedencias){
        this.material=material;this.resultados=resultados;this.fuentes=fuentes;this.evidencias=evidencias;this.procedencias=procedencias;
    }

    @PostMapping("/material-ia/preparaciones") @CapacidadReconocida("preparar_material_ia")
    public MaterialIaService.Preparacion preparar(@RequestBody MaterialIaDtos.Solicitud datos){
        return material.preparar(datos.aCore(),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @PostMapping("/material-ia/solicitudes") @CapacidadReconocida("solicitar_material_ia")
    public MaterialIaService.Resultado solicitar(@RequestBody MaterialIaDtos.Solicitud datos){
        return material.solicitar(datos.aCore(),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @GetMapping("/material-ia/{operacion}") @CapacidadReconocida("consultar_material_ia")
    public MaterialIaService.Resultado consultar(@PathVariable UUID operacion){
        return material.consultar(operacion,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @GetMapping("/material-ia/{operacion}/archivos/{archivo}") @CapacidadReconocida("descargar_archivo_material_ia")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID operacion,@PathVariable UUID archivo){
        var contenido=resultados.descargar(operacion,archivo,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contenido.contentType())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(contenido.nombre()).build().toString())
                .body(contenido.bytes());
    }
    @GetMapping("/material-ia/fotografias/{original}/seleccion") @CapacidadReconocida("consultar_seleccion_material_ia")
    public ResultadosMaterialIa.Seleccion seleccion(@PathVariable UUID original){
        return resultados.seleccionActual(original,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @GetMapping("/material-ia/fotografias/{original}/versiones") @CapacidadReconocida("consultar_versiones_material_ia")
    public List<ResultadosMaterialIa.Version> versiones(@PathVariable UUID original){
        return resultados.versiones(original,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @PostMapping("/material-ia/fotografias/{original}/seleccion") @CapacidadReconocida("seleccionar_foto_material_ia")
    public ResultadosMaterialIa.Seleccion seleccionar(@PathVariable UUID original,@RequestBody MaterialIaDtos.SeleccionFoto datos,
            @RequestHeader("Idempotency-Key")String clave){
        return resultados.seleccionar(original,datos.operacion(),datos.archivo(),datos.revisionEsperada(),clave,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @PostMapping("/material-ia/{operacion}/archivos/{archivo}/alternativa") @CapacidadReconocida("guardar_alternativa_material_ia")
    public ResultadosMaterialIa.Version guardarAlternativa(@PathVariable UUID operacion,@PathVariable UUID archivo,
            @RequestBody MaterialIaDtos.GestionVersion datos,@RequestHeader("Idempotency-Key")String clave){
        return resultados.guardarAlternativa(operacion,archivo,datos.revisionEsperada(),clave,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @PostMapping("/material-ia/{operacion}/archivos/{archivo}/descarte") @CapacidadReconocida("descartar_temporal_material_ia")
    public ResultadosMaterialIa.Version descartarTemporal(@PathVariable UUID operacion,@PathVariable UUID archivo,
            @RequestBody MaterialIaDtos.GestionVersion datos,@RequestHeader("Idempotency-Key")String clave){
        return resultados.descartarTemporal(operacion,archivo,datos.revisionEsperada(),clave,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @PostMapping("/material-ia/{operacion}/archivos/{archivo}/retirada-alternativa") @CapacidadReconocida("eliminar_alternativa_material_ia")
    public ResultadosMaterialIa.Version retirarAlternativa(@PathVariable UUID operacion,@PathVariable UUID archivo,
            @RequestBody MaterialIaDtos.GestionVersion datos,@RequestHeader("Idempotency-Key")String clave){
        return resultados.retirarAlternativa(operacion,archivo,datos.revisionEsperada(),clave,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @PostMapping("/material-ia/tratamiento/presentaciones") @CapacidadReconocida("presentar_tratamiento_material_ia")
    public EvidenciasTratamientoMaterialIa.AvisoParaDecision presentarTratamiento(@RequestBody MaterialIaDtos.Solicitud datos){
        return fuentes.presentarTratamiento(datos.aCore(),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @PostMapping("/material-ia/tratamiento/decisiones") @CapacidadReconocida("registrar_decision_tratamiento_ia")
    public EvidenciasTratamientoMaterialIa.DecisionRegistrada decidirTratamiento(@RequestBody MaterialIaDtos.CapturaTratamiento datos,HttpServletRequest request){
        return evidencias.capturar(datos.aCore(),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(request));
    }
}
