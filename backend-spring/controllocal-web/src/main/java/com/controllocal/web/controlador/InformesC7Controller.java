package com.controllocal.web.controlador;

import com.controllocal.service.actividad.InformesC7;
import com.controllocal.service.planes.DerechosDeInformesC7;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** CAT-E29/directriz5.3. La composición contratada no amplía la población autorizada. */
@RestController @RequestMapping("indicadores")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class InformesC7Controller {
    private final InformesC7 informes;
    public InformesC7Controller(InformesC7 i){informes=i;}
    @GetMapping("informes/derechos") @CapacidadReconocida("consultar_derechos_informes")
    public DerechosDeInformesC7.Derechos derechos(){return informes.derechos(SesionActual.actor());}
    @PostMapping("informes") @CapacidadReconocida("preparar_informe_comercial")
    public InformesC7.Informe preparar(@RequestBody InformesC7.Consulta q){return informes.preparar(q,SesionActual.actor());}
    @GetMapping("mediciones") @CapacidadReconocida("consultar_mediciones_individuales")
    public com.controllocal.service.actividad.MedicionesC7.Lectura mediciones(
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta){return informes.consultarMediciones(desde,hasta,SesionActual.actor());}
}
