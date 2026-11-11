package com.controllocal.web.controlador;
import com.controllocal.service.RequerimientoService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.dto.RequerimientoRequest;
import com.controllocal.web.dto.RequerimientoResponse;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** RF028–030: ficha propia, comandos directos durables y fin mediante Actuaciones. */
@RestController
@RequestMapping("requerimientos")
public class RequerimientosController {
    private final RequerimientoService requerimientos;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public RequerimientosController(RequerimientoService requerimientos,ActuacionesService actuaciones,
                                     ProcedenciaDeCabeceras procedencias){
        this.requerimientos=requerimientos;this.actuaciones=actuaciones;this.procedencias=procedencias;
    }
    @GetMapping("cliente/{idCliente}") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("consultar_demanda")
    public List<RequerimientoResponse> listarPorCliente(@PathVariable long idCliente){
        return requerimientos.listarPorCliente(idCliente,SesionActual.actor()).stream().map(RequerimientoResponse::desde).toList();
    }
    @GetMapping("{id}") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("consultar_demanda")
    public RequerimientoResponse buscar(@PathVariable long id,@RequestParam(required=false) Long version,
                                        @RequestParam(required=false) Instant corte){
        return RequerimientoResponse.desde(requerimientos.consultar(id,version,corte,SesionActual.actor()));
    }
    @PostMapping @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("registrar_demanda")
    public ResponseEntity<RequerimientoResponse> crear(@RequestBody RequerimientoRequest dto,HttpServletRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(RequerimientoResponse.desde(requerimientos.crear(
            dto.aDatos(),SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),
            request.getHeader("Idempotency-Key"),procedencias.de(request))));
    }
    @PutMapping("{id}") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida("editar_demanda")
    public RequerimientoResponse actualizar(@PathVariable long id,@RequestBody RequerimientoRequest dto,HttpServletRequest request){
        if(dto.versionEsperada()==null)throw new ReglaNegocioException("Se requiere la versión observada de la demanda.");
        return RequerimientoResponse.desde(requerimientos.actualizar(id,dto.versionEsperada(),dto.aDatos(),
            SesionActual.actor(),ContextoDeActuacion.de(SesionActual.sesion()),request.getHeader("Idempotency-Key"),procedencias.de(request)));
    }
    public record FinPedido(Long versionEsperada,RequerimientoService.TipoFinDemanda tipo,LocalDate fechaDelHecho,String motivo) { }
    @PostMapping("{id}/fin/propuestas") @PreAuthorize("hasRole('AGENTE')")
    @CapacidadReconocida("retirar_finalizar_demanda")
    public ActuacionesService.Propuesta prepararFin(@PathVariable long id,@RequestBody FinPedido dto,HttpServletRequest request){
        if(dto.versionEsperada()==null)throw new ReglaNegocioException("Se requiere la versión observada de la demanda.");
        return actuaciones.prepararFinDemanda(SesionActual.actor(),new RequerimientoService.FinDemanda(id,
            dto.versionEsperada(),dto.tipo(),dto.fechaDelHecho(),dto.motivo()),
            ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(request));
    }
}
