package com.controllocal.web.controlador;

import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Contrato común Web/Mobile; revisión y confirmación quedan fuera de las herramientas del modelo. */
@RestController
@RequestMapping("actuaciones")
@PreAuthorize("hasAnyRole('AGENTE','BROKER')")
public class ActuacionesController {
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public ActuacionesController(ActuacionesService actuaciones,ProcedenciaDeCabeceras procedencias) {
        this.actuaciones=actuaciones;this.procedencias=procedencias;
    }
    @PostMapping("capturas/{id}/propuestas")
    @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("preparar_actuacion_captura")
    public ActuacionesService.Propuesta preparar(@PathVariable long id,HttpServletRequest request) {
        return actuaciones.prepararCaptura(SesionActual.actor(),id,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(request));
    }
    @GetMapping("{referencia}") @CapacidadReconocida("consultar_actuacion")
    public ActuacionesService.Resultado consultar(@PathVariable String referencia) {
        return actuaciones.consultar(SesionActual.actor(),referencia,ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @GetMapping("{referencia}/revision") @CapacidadReconocida("revisar_actuacion")
    public ActuacionesService.Revision revisar(@PathVariable String referencia) {
        return actuaciones.revisar(SesionActual.actor(),referencia,ContextoDeActuacion.de(SesionActual.sesion()));
    }
    @PostMapping("{referencia}/confirmacion")
    @CapacidadReconocida(value={"confirmar_captura","registrar_origen_fisico_unidad","retirar_finalizar_demanda",
            "invitar_colaboracion_encargo","solicitar_colaboracion","decidir_colaboracion","revocar_colaboracion","declarar_canal_colaboracion",
            "solicitar_participacion_network","decidir_participacion_network","desactivar_participacion_network",
            "abrir_oportunidad","rectificar_interaccion","registrar_no_continuidad_oportunidad","cancelar_visita",
            "presentar_oferta","registrar_contraoferta","registrar_aceptacion_oferta","registrar_rechazo_oferta","registrar_retirada_oferta",
            "decidir_captacion","presentar_expediente_operacion","evaluar_expediente_operacion",
            "informar_hito_contractual","confirmar_hito_contractual","rectificar_hito_contractual",
            "registrar_cobro","asignar_reparto_comision","autorizar_borrador_adenda",
            "reprogramar_gestion_cobro","concluir_gestion_cobro",
            "publicar_anuncio","actualizar_anuncio_visible","pausar_anuncio","reanudar_anuncio","finalizar_difusion_anuncio",
            "obtener_pieza_para_difusion"},porActuacion=true)
    public ActuacionesService.Resultado confirmar(@PathVariable String referencia,
            @RequestBody ActuacionesService.Confirmacion prueba,HttpServletRequest request) {
        return actuaciones.confirmar(SesionActual.actor(),referencia,ContextoDeActuacion.de(SesionActual.sesion()),
                prueba,procedencias.de(request));
    }
}
