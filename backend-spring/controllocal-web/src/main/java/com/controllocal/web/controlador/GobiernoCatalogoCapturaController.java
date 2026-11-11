package com.controllocal.web.controlador;

import com.controllocal.service.GobiernoCatalogoCapturaService;
import com.controllocal.service.GobiernoCatalogoCapturaService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.web.capacidades.CapacidadReconocida;
import com.controllocal.web.seguridad.SesionActual;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** AC044–046. Organización y atribución proceden del contexto vigente, nunca del cuerpo. */
@RestController
@RequestMapping("catalogo-gobernado")
public class GobiernoCatalogoCapturaController {
    private final GobiernoCatalogoCapturaService servicio;
    public GobiernoCatalogoCapturaController(GobiernoCatalogoCapturaService servicio){this.servicio=servicio;}
    public record VersionDefinicion(Long version,DatosDefinicion datos) { }
    public record VersionPerfil(Long version,DatosPerfil datos) { }
    public record RetiroDefinicion(Long version,String sustituidaPor) { }
    public record RetiroPerfil(Long version) { }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("consultar_catalogo_gobernado")
    public ResponseEntity<CatalogoGobernado> consultar(){return privada(servicio.consultar(SesionActual.actor(),sesion()));}

    @GetMapping("definiciones/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("consultar_catalogo_gobernado")
    public ResponseEntity<Definicion> definicion(@PathVariable long id){return privada(servicio.consultarDefinicion(id,null,SesionActual.actor(),sesion()));}

    @GetMapping("definiciones/{id}/versiones/{version}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("consultar_catalogo_gobernado")
    public ResponseEntity<Definicion> versionDefinicion(@PathVariable long id,@PathVariable long version){return privada(servicio.consultarDefinicion(id,version,SesionActual.actor(),sesion()));}

    @GetMapping("perfiles/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("consultar_catalogo_gobernado")
    public ResponseEntity<Perfil> perfil(@PathVariable long id){return privada(servicio.consultarPerfil(id,null,SesionActual.actor(),sesion()));}

    @GetMapping("perfiles/{id}/versiones/{version}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("consultar_catalogo_gobernado")
    public ResponseEntity<Perfil> versionPerfil(@PathVariable long id,@PathVariable long version){return privada(servicio.consultarPerfil(id,version,SesionActual.actor(),sesion()));}

    @PostMapping("definiciones")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("versionar_definicion_gobernada")
    public ResponseEntity<Definicion> crearDefinicion(@RequestBody(required=false) DatosDefinicion datos){return privada(servicio.crearDefinicion(datos,SesionActual.actor(),sesion()));}

    @PutMapping("definiciones/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("versionar_definicion_gobernada")
    public ResponseEntity<Definicion> versionarDefinicion(@PathVariable long id,@RequestBody(required=false) VersionDefinicion datos){
        return privada(servicio.versionarDefinicion(id,version(datos==null?null:datos.version()),datos==null?null:datos.datos(),SesionActual.actor(),sesion()));
    }
    @PostMapping("definiciones/{id}/retirar")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("retirar_definicion_gobernada")
    public ResponseEntity<Definicion> retirarDefinicion(@PathVariable long id,@RequestBody(required=false) RetiroDefinicion datos){
        return privada(servicio.retirarDefinicion(id,version(datos==null?null:datos.version()),datos==null?null:datos.sustituidaPor(),SesionActual.actor(),sesion()));
    }
    @PostMapping("perfiles")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("versionar_definicion_gobernada")
    public ResponseEntity<Perfil> crearPerfil(@RequestBody(required=false) DatosPerfil datos){return privada(servicio.crearPerfil(datos,SesionActual.actor(),sesion()));}

    @PutMapping("perfiles/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("versionar_definicion_gobernada")
    public ResponseEntity<Perfil> versionarPerfil(@PathVariable long id,@RequestBody(required=false) VersionPerfil datos){
        return privada(servicio.versionarPerfil(id,version(datos==null?null:datos.version()),datos==null?null:datos.datos(),SesionActual.actor(),sesion()));
    }
    @PostMapping("perfiles/{id}/retirar")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @CapacidadReconocida("retirar_definicion_gobernada")
    public ResponseEntity<Perfil> retirarPerfil(@PathVariable long id,@RequestBody(required=false) RetiroPerfil datos){
        return privada(servicio.retirarPerfil(id,version(datos==null?null:datos.version()),SesionActual.actor(),sesion()));
    }
    private static long version(Long valor){return valor==null?-1:valor;}
    private static ContextoSesion sesion(){return ContextoSesion.de(SesionActual.sesion().acceso());}
    private static <T> ResponseEntity<T> privada(T cuerpo){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);}
}
