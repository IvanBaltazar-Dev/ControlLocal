package com.controllocal.web.controlador;

import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.network.NetworkService;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

/** Participación propia: preparar o consultar. El efecto se confirma en Actuaciones. */
@RestController
@RequestMapping("network")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class NetworkController {
    private final NetworkService network;
    private final com.controllocal.service.network.AvisosNetworkService avisos;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public NetworkController(NetworkService network,ActuacionesService actuaciones,ProcedenciaDeCabeceras procedencias,
            com.controllocal.service.network.AvisosNetworkService avisos) {
        this.network=network;this.actuaciones=actuaciones;this.procedencias=procedencias;this.avisos=avisos;
    }
    @GetMapping("avisos") @CapacidadReconocida("consultar_avisos_network")
    public com.controllocal.service.Pagina<com.controllocal.service.network.AvisosNetworkService.Aviso> avisos(
            @RequestParam(required=false) Integer pagina,@RequestParam(required=false) Integer tamano) {
        return avisos.consultar(pagina,tamano,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @GetMapping("encargos/{id}") @CapacidadReconocida("consultar_participacion_network")
    public NetworkService.Vista consultar(@PathVariable long id) {return network.consultar(id,SesionActual.actor());}
    @GetMapping("encargos/{id}/previsualizacion") @CapacidadReconocida("previsualizar_participacion_network")
    public NetworkService.Preparacion previsualizar(@PathVariable long id,@RequestParam Set<String> campos) {
        return network.previsualizar(id,campos,SesionActual.actor());
    }
    @GetMapping("solicitudes/{referencia}") @CapacidadReconocida("consultar_solicitud_network")
    public NetworkService.SolicitudVista consultarSolicitud(@PathVariable String referencia) {
        return network.consultarSolicitud(referencia,SesionActual.actor());
    }
    @PostMapping("solicitudes/propuestas") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("solicitar_participacion_network")
    public ActuacionesService.Propuesta solicitar(@RequestBody NetworkService.DatosSolicitud datos,HttpServletRequest r) {
        return actuaciones.prepararSolicitudNetwork(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("decisiones/propuestas") @CapacidadReconocida("decidir_participacion_network")
    public ActuacionesService.Propuesta decidir(@RequestBody NetworkService.DatosDecision datos,HttpServletRequest r) {
        return actuaciones.prepararDecisionNetwork(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("desactivaciones/propuestas") @PreAuthorize("hasRole('BROKER')")
    @CapacidadReconocida("desactivar_participacion_network")
    public ActuacionesService.Propuesta desactivar(@RequestBody NetworkService.DatosDesactivacion datos,HttpServletRequest r) {
        return actuaciones.prepararDesactivacionNetwork(SesionActual.actor(),datos,ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
}
