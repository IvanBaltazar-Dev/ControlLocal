package com.controllocal.web.controlador;

import com.controllocal.service.PublicacionService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.dto.PublicacionDtos.PublicacionResponse;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.controllocal.service.PublicacionService.*;

/** Un Encargo y su anuncio en cada URL. Los efectos sensibles usan la revisión canónica. */
@RestController
@RequestMapping("encargos/{idEncargo}/publicaciones")
public class EncargosController {
    private final PublicacionService publicaciones;
    private final ActuacionesService actuaciones;
    private final ProcedenciaDeCabeceras procedencias;
    public EncargosController(PublicacionService publicaciones,ActuacionesService actuaciones,ProcedenciaDeCabeceras procedencias){
        this.publicaciones=publicaciones;this.actuaciones=actuaciones;this.procedencias=procedencias;
    }
    @GetMapping @CapacidadReconocida("consultar_publicaciones")
    public List<PublicacionResponse> listar(@PathVariable long idEncargo){return publicaciones.listarDeEncargo(idEncargo,SesionActual.actor())
            .stream().map(PublicacionResponse::desde).toList();}
    @GetMapping("publicabilidad") @CapacidadReconocida("evaluar_publicabilidad")
    public EvaluacionPublicabilidad evaluar(@PathVariable long idEncargo,@RequestParam(required=false)String canal){
        return publicaciones.evaluarPublicabilidad(idEncargo,canal,SesionActual.actor());
    }
    @GetMapping("{idPublicacion}") @CapacidadReconocida("consultar_publicacion")
    public PublicacionResponse obtener(@PathVariable long idEncargo,@PathVariable long idPublicacion){return PublicacionResponse.desde(publicaciones.obtener(idEncargo,idPublicacion,SesionActual.actor()));}
    @GetMapping("{idPublicacion}/historia") @CapacidadReconocida("consultar_historia_publicacion")
    public List<VersionPublicada> historia(@PathVariable long idEncargo,@PathVariable long idPublicacion){return publicaciones.consultarHistoria(idEncargo,idPublicacion,SesionActual.actor());}

    @PostMapping("borradores") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida(CREAR_BORRADOR)
    public PublicacionResponse crear(@PathVariable long idEncargo,@RequestBody DatosBorrador datos,
            @RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){
        vinculo(idEncargo,null,datos.encargo(),datos.publicacion());
        return PublicacionResponse.desde(publicaciones.crearBorradorAnuncio(datos,SesionActual.actor(),
                ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r)));
    }
    @PutMapping("{idPublicacion}/borrador") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida(EDITAR_BORRADOR)
    public PublicacionResponse editar(@PathVariable long idEncargo,@PathVariable long idPublicacion,
            @RequestBody DatosBorrador datos,@RequestHeader("Idempotency-Key")String clave,HttpServletRequest r){
        vinculo(idEncargo,idPublicacion,datos.encargo(),datos.publicacion());
        return PublicacionResponse.desde(publicaciones.editarBorradorAnuncio(datos,SesionActual.actor(),
                ContextoDeActuacion.de(SesionActual.sesion()),clave,procedencias.de(r)));
    }
    @PostMapping("{idPublicacion}/publicacion/preparacion") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida(PUBLICAR)
    public ActuacionesService.Propuesta publicar(@PathVariable long idEncargo,@PathVariable long idPublicacion,@RequestBody DatosCambio d,HttpServletRequest r){return preparar(PUBLICAR,idEncargo,idPublicacion,d,r);}
    @PostMapping("{idPublicacion}/actualizacion-visible/preparacion") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida(ACTUALIZAR_VISIBLE)
    public ActuacionesService.Propuesta actualizar(@PathVariable long idEncargo,@PathVariable long idPublicacion,@RequestBody DatosCambio d,HttpServletRequest r){return preparar(ACTUALIZAR_VISIBLE,idEncargo,idPublicacion,d,r);}
    @PostMapping("{idPublicacion}/pausa/preparacion") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida(PAUSAR)
    public ActuacionesService.Propuesta pausar(@PathVariable long idEncargo,@PathVariable long idPublicacion,@RequestBody DatosCambio d,HttpServletRequest r){return preparar(PAUSAR,idEncargo,idPublicacion,d,r);}
    @PostMapping("{idPublicacion}/reanudacion/preparacion") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida(REANUDAR)
    public ActuacionesService.Propuesta reanudar(@PathVariable long idEncargo,@PathVariable long idPublicacion,@RequestBody DatosCambio d,HttpServletRequest r){return preparar(REANUDAR,idEncargo,idPublicacion,d,r);}
    @PostMapping("{idPublicacion}/fin-difusion/preparacion") @PreAuthorize("hasRole('AGENTE')") @CapacidadReconocida(FINALIZAR)
    public ActuacionesService.Propuesta finalizar(@PathVariable long idEncargo,@PathVariable long idPublicacion,@RequestBody DatosCambio d,HttpServletRequest r){return preparar(FINALIZAR,idEncargo,idPublicacion,d,r);}
    private ActuacionesService.Propuesta preparar(String cap,long e,long p,DatosCambio d,HttpServletRequest r){
        vinculo(e,p,d.encargo(),d.publicacion());return actuaciones.prepararPublicacion(cap,SesionActual.actor(),d,
                ContextoDeActuacion.de(SesionActual.sesion()),procedencias.de(r));
    }
    private static void vinculo(long urlEncargo,Long urlAnuncio,long encargo,Long anuncio){
        if(urlEncargo!=encargo||!java.util.Objects.equals(urlAnuncio,anuncio))throw new ReglaNegocioException("El cuerpo debe corresponder al Encargo y anuncio de la URL.");
    }
}
