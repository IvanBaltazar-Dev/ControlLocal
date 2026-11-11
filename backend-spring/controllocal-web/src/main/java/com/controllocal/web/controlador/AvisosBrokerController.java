package com.controllocal.web.controlador;

import com.controllocal.service.Pagina;
import com.controllocal.service.soporte.NotificacionesBrokerC7;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** NB: confirmación transaccional del hecho, entrega mínima y atención privada, sin envío externo. */
@RestController @RequestMapping("broker/avisos") @PreAuthorize("hasRole('BROKER')")
public class AvisosBrokerController {
    private final NotificacionesBrokerC7 avisos;
    public AvisosBrokerController(NotificacionesBrokerC7 avisos){this.avisos=avisos;}
    @GetMapping @CapacidadReconocida("consultar_avisos_broker")
    public Pagina<NotificacionesBrokerC7.Aviso> listar(@RequestParam(defaultValue="1") int pagina,
            @RequestParam(defaultValue="20") int tamano,@RequestParam(defaultValue="false") boolean historia){
        return avisos.listar(pagina,tamano,historia,SesionActual.actor());
    }
    @PostMapping("{evento}/atender") @CapacidadReconocida("atender_aviso_broker")
    public boolean atender(@PathVariable long evento){return avisos.atender(evento,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()));}
}
