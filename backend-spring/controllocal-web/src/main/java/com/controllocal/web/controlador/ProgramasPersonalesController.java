package com.controllocal.web.controlador;

import com.controllocal.service.actividad.ProgramasPersonales;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/** CAT-E41: configuración humana expresa y bandeja privada; no efectúa contactos externos. */
@RestController @RequestMapping("programas-personales") @PreAuthorize("hasRole('AGENTE')")
public class ProgramasPersonalesController {
    private final ProgramasPersonales servicio;
    private final ProcedenciaDeCabeceras procedencias;
    public ProgramasPersonalesController(ProgramasPersonales s,ProcedenciaDeCabeceras p){servicio=s;procedencias=p;}
    @GetMapping @CapacidadReconocida("consultar_programas_personales")
    public List<ProgramasPersonales.Vista> listar(@RequestParam(defaultValue="1")int pagina,@RequestParam(defaultValue="20")int tamano){return servicio.listar(pagina,tamano,SesionActual.actor());}
    @GetMapping("{referencia}") @CapacidadReconocida("consultar_programa_personal")
    public ProgramasPersonales.Vista consultar(@PathVariable UUID referencia){return servicio.consultar(referencia,SesionActual.actor());}
    @PostMapping @CapacidadReconocida("configurar_programa_personal")
    public ProgramasPersonales.Vista configurar(@RequestBody ProgramasPersonales.DatosConfiguracion datos,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){return servicio.configurar(datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r));}
    @PostMapping("{referencia}/estado") @CapacidadReconocida("cambiar_estado_programa_personal")
    public ProgramasPersonales.Vista estado(@PathVariable UUID referencia,@RequestBody ProgramasPersonales.CambioEstado datos,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){return servicio.cambiarEstado(referencia,datos,SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r));}
    @GetMapping("{referencia}/ocurrencias") @CapacidadReconocida("consultar_ocurrencias_programa_personal")
    public List<ProgramasPersonales.Ocurrencia> ocurrencias(@PathVariable UUID referencia,@RequestParam(defaultValue="1")int pagina,@RequestParam(defaultValue="20")int tamano){return servicio.ocurrencias(referencia,pagina,tamano,SesionActual.actor());}
    @GetMapping("{referencia}/ocurrencias/{ocurrencia}") @CapacidadReconocida("consultar_resultado_programa_personal")
    public ProgramasPersonales.Ocurrencia resultado(@PathVariable UUID referencia,@PathVariable UUID ocurrencia){return servicio.resultado(referencia,ocurrencia,SesionActual.actor());}
}
