package com.controllocal.web.controlador;

import com.controllocal.service.ComisionService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto;
import com.controllocal.service.formalizacion.HitosConfirmados.ReferenciaContextoEconomico;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** RF062–064: consulta separada de propuesta humana; ninguna ruta registra fondos directamente. */
@RestController @RequestMapping("comisiones")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class ComisionesController {
    private final ComisionService comisiones;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public ComisionesController(ComisionService comisiones,ActuacionesService actuaciones,ProcedenciaDeCabeceras procedencias) {
        this.comisiones=comisiones;this.actuaciones=actuaciones;this.procedencias=procedencias;
    }
    @GetMapping @CapacidadReconocida("consultar_comision")
    public ComisionService.FichaComision consultar(@RequestParam long encargo,@RequestParam long revisionPacto,
            @RequestParam(required=false) Long contexto) {
        return comisiones.consultar(new ReferenciaPacto(encargo,revisionPacto),
                contexto==null?null:new ReferenciaContextoEconomico(contexto),SesionActual.actor());
    }
    @PostMapping("movimientos/propuestas") @CapacidadReconocida("registrar_cobro")
    public ActuacionesService.Propuesta movimiento(@RequestBody ComisionService.DatosCobro datos,HttpServletRequest r) {
        return actuaciones.prepararMovimientoComision(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("repartos/propuestas") @CapacidadReconocida("asignar_reparto_comision")
    public ActuacionesService.Propuesta reparto(@RequestBody ComisionService.DatosReparto datos,HttpServletRequest r) {
        return actuaciones.prepararRepartoComision(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("seguimientos/propuestas") @CapacidadReconocida("reprogramar_gestion_cobro")
    public ActuacionesService.Propuesta reprogramar(@RequestBody ComisionService.DatosReprogramacion datos,HttpServletRequest r) {
        return actuaciones.prepararReprogramacionCobro(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    @PostMapping("conclusiones/propuestas") @CapacidadReconocida("concluir_gestion_cobro")
    public ActuacionesService.Propuesta concluir(@RequestBody ComisionService.DatosConclusion datos,HttpServletRequest r) {
        return actuaciones.prepararConclusionCobro(SesionActual.actor(),datos,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
}
