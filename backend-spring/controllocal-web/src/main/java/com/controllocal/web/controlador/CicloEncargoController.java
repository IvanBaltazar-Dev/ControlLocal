package com.controllocal.web.controlador;

import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;

/** Transporte de AC058–077; las reglas, versiones y transacciones pertenecen al único Core. */
@RestController
@RequestMapping("encargos")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class CicloEncargoController {
    private final EncargoService encargos;
    private final ActuacionesService actuaciones;
    public CicloEncargoController(EncargoService encargos,ActuacionesService actuaciones) {
        this.encargos=encargos;this.actuaciones=actuaciones;
    }
    private static ContextoSesion sesion() { return ContextoSesion.de(SesionActual.sesion().acceso()); }
    private static Procedencia origen(String canal) { return Procedencia.deCabecera(canal); }

    @GetMapping("{id}") @CapacidadReconocida("consultar_encargo")
    public EncargoService.Ficha consultar(@PathVariable long id) { return encargos.consultar(id,SesionActual.actor()); }
    @GetMapping("{id}/vigencia") @CapacidadReconocida("consultar_vigencia_encargo")
    public EncargoService.Vigencia vigencia(@PathVariable long id,@RequestParam(required=false) LocalDate corte) {
        return encargos.consultarVigencia(id,corte,SesionActual.actor());
    }
    @PostMapping("{id}/adendas") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("proponer_adenda_encargo")
    public EncargoService.Adenda proponerAdenda(@PathVariable long id,@RequestBody EncargoService.DatosAdenda datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.proponerAdenda(id,datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @GetMapping("{id}/adendas/{propuesta}") @CapacidadReconocida("consultar_adenda_encargo")
    public EncargoService.Adenda adenda(@PathVariable long id,@PathVariable long propuesta) {
        return encargos.consultarAdenda(new EncargoService.ReferenciaAdenda(id,propuesta),SesionActual.actor());
    }
    @PostMapping("{id}/adendas/{propuesta}/decisiones/propuestas") @CapacidadReconocida("autorizar_borrador_adenda")
    public ActuacionesService.Propuesta decidirAdenda(@PathVariable long id,@PathVariable long propuesta,
            @RequestBody EncargoService.DecisionBorradorAdenda datos,@RequestHeader(value="X-Origen",required=false) String canal) {
        return actuaciones.prepararDecisionBorradorAdenda(SesionActual.actor(),
                new EncargoService.ReferenciaAdenda(id,propuesta),datos,sesion(),origen(canal));
    }
    @PostMapping("documentos") @CapacidadReconocida("preparar_documento_encargo")
    public EncargoService.Documento preparar(@RequestBody EncargoService.Preparacion datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.prepararDocumento(datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @PostMapping("{id}/documentos/regenerar") @CapacidadReconocida("regenerar_documento_encargo")
    public EncargoService.Documento regenerar(@PathVariable long id,@RequestBody EncargoService.Preparacion datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.regenerarDocumento(id,datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @GetMapping("documentos/{id}") @CapacidadReconocida("previsualizar_documento_encargo")
    public EncargoService.Documento documento(@PathVariable long id) { return encargos.consultarDocumento(id,SesionActual.actor()); }
    /** Toda entrega de bytes atraviesa el único A04, incluida su segunda autorización. */
    @GetMapping("documentos/{id}/contenido") @CapacidadReconocida("obtener_documento_encargo")
    public ResponseEntity<Void> obtenerDocumento(@PathVariable long id) {
        var d=encargos.consultarDocumento(id,SesionActual.actor());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).cacheControl(CacheControl.noStore().cachePrivate())
                .location(URI.create("/documentos/contenido?clave="+d.referencia())).build();
    }
    @PostMapping(value="archivos/cargas",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("cargar_archivo_contextual")
    public EncargoService.CargaTemporal cargar(@RequestParam(required=false) Long encargo,@RequestParam(required=false) Long documento,
            @RequestParam String finalidad,@RequestParam long versionDestino,@RequestPart("archivo") org.springframework.web.multipart.MultipartFile archivo,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal)throws java.io.IOException{
        return encargos.cargarArchivo(new EncargoService.CargaArchivo(encargo,documento,finalidad,archivo.getOriginalFilename(),
                archivo.getContentType(),archivo.getBytes(),versionDestino),SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @PostMapping("archivos") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("adjuntar_reutilizar_documento")
    public EncargoService.ArchivoRegistrado evidencia(@RequestBody EncargoService.VinculoArchivo datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.adjuntarOReutilizar(datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @PostMapping("{id}/peticiones") @CapacidadReconocida("presentar_peticion_encargo")
    public EncargoService.Peticion presentar(@PathVariable long id,@RequestBody EncargoService.Presentacion datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.presentar(id,datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @GetMapping("peticiones/{id}") @CapacidadReconocida("consultar_peticion_encargo")
    public EncargoService.Peticion peticion(@PathVariable long id) { return encargos.consultarPeticion(id,SesionActual.actor()); }
    public record Reenvio(@com.fasterxml.jackson.annotation.JsonProperty(value="versionPeticion",required=true)
                         @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL) long versionPeticion,EncargoService.Presentacion presentacion) { }
    @PostMapping("peticiones/{id}/reenviar") @CapacidadReconocida("reenviar_peticion_encargo")
    public EncargoService.Peticion reenviar(@PathVariable long id,@RequestBody Reenvio datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.corregirYReenviar(id,datos.versionPeticion(),datos.presentacion(),SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @PostMapping("peticiones/{id}/decision") @CapacidadReconocida("decidir_captacion")
    public ActuacionesService.Propuesta decidir(@PathVariable long id,@RequestBody EncargoService.Decision datos,
            @RequestHeader(value="X-Origen",required=false) String canal) {
        return actuaciones.prepararDecisionEncargo(SesionActual.actor(),id,datos,sesion(),origen(canal));
    }
    @PutMapping("{id}/condiciones") @CapacidadReconocida("editar_condiciones_encargo")
    public EncargoService.Ficha editar(@PathVariable long id,@RequestBody EncargoService.Edicion datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.editar(id,datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @GetMapping("{id}/composicion") @CapacidadReconocida("consultar_composicion_encargo")
    public EncargoService.Composicion composicion(@PathVariable long id,@RequestParam(required=false) Long versionComposicion) {
        return encargos.consultarComposicion(id,versionComposicion,SesionActual.actor());
    }
    @PutMapping("{id}/composicion") @CapacidadReconocida("declarar_composicion_encargo")
    public EncargoService.Composicion declarar(@PathVariable long id,@RequestBody EncargoService.DatosComposicion datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.declararComposicion(id,datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
    public record SolicitarFin(EncargoService.DatosFin datos,Long solicitudObservada,Long versionSolicitud) { }
    @PostMapping("{id}/solicitudes-fin") @CapacidadReconocida("solicitar_fin_encargo")
    public EncargoService.SolicitudFin solicitar(@PathVariable long id,@RequestBody SolicitarFin datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.solicitarFin(id,datos.datos(),datos.solicitudObservada(),datos.versionSolicitud(),SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @GetMapping("solicitudes-fin/{id}") @CapacidadReconocida("consultar_solicitud_fin_encargo")
    public EncargoService.SolicitudFin solicitud(@PathVariable long id) { return encargos.consultarSolicitudFin(id,SesionActual.actor()); }
    public record Cancelacion(@com.fasterxml.jackson.annotation.JsonProperty(value="version",required=true)
                              @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL) long version,String motivo) { }
    @PostMapping("solicitudes-fin/{id}/cancelar") @CapacidadReconocida("cancelar_solicitud_fin_encargo")
    public EncargoService.SolicitudFin cancelar(@PathVariable long id,@RequestBody Cancelacion datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.cancelarSolicitudFin(id,datos.version(),datos.motivo(),SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @PostMapping("solicitudes-fin/{id}/decision") @CapacidadReconocida("decidir_solicitud_fin_encargo")
    public EncargoService.SolicitudFin decidirFin(@PathVariable long id,@RequestBody EncargoService.Decision datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.decidirSolicitudFin(id,datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
    @PostMapping("{id}/fin") @CapacidadReconocida("cerrar_encargo")
    public EncargoService.Ficha finalizar(@PathVariable long id,@RequestBody EncargoService.DatosFin datos,
            @RequestHeader("Idempotency-Key") String clave,@RequestHeader(value="X-Origen",required=false) String canal) {
        return encargos.finalizar(id,datos,SesionActual.actor(),sesion(),clave,origen(canal));
    }
}
