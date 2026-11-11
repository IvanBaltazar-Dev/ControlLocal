package com.controllocal.web.almacen;

import com.controllocal.service.Actor;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.documentos.ArchivosFoto;
import com.controllocal.service.excepcion.*;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.service.planes.ClasificadorArchivosExistentesB0;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Mismo almacén privado y comprobación posterior a I/O que DocumentosController. */
@Component
public class ArchivosFotoContextuales implements ArchivosFoto {
    private final AlmacenDocumentos almacen;private final AccesoArchivoService accesos;
    private final RevalidacionDeSesion sesiones;
    private final SalidaBinariaPersistente salidas;
    private final CuotaArchivosB0 cuota;private final ClasificadorArchivosExistentesB0 clasificador;
    public ArchivosFotoContextuales(AlmacenDocumentos almacen,AccesoArchivoService accesos,RevalidacionDeSesion sesiones,SalidaBinariaPersistente salidas,CuotaArchivosB0 cuota,ClasificadorArchivosExistentesB0 clasificador){
        this.almacen=almacen;this.accesos=accesos;this.sesiones=sesiones;this.salidas=salidas;
        this.cuota=cuota;this.clasificador=clasificador;
    }
    public byte[] original(UUID ref,Actor actor){
        vigente(actor);var permiso=accesos.resolver(ref.toString(),actor);
        if(!"FOTO_PROPIEDAD".equals(permiso.tipo()))throw new NoEncontradoException("Fotografía");
        byte[] bytes=leer(permiso.claveProveedor());vigente(actor);accesos.revalidar(permiso,actor);
        accesos.verificarContenido(permiso,bytes);return bytes;
    }
    public String guardarCopia(long propiedad,UUID original,String hash,byte[] bytes,Actor actor){
        vigente(actor);
        var sid=ContextoDeActuacion.de(SesionActual.sesion());
        var c=new CuotaArchivosB0.Contexto(clasificador.preparar(ClasificadorArchivosExistentesB0.COPIA,original.toString(),"COPIA_TECNICA_COMERCIAL",actor,sid),actor,sid);
        var operacion=UUID.nameUUIDFromBytes(("COPIA-FOTO-1\u0000"+actor.idOrganizacion()+"\u0000"+original+"\u0000"+hash).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var guardado=salidas.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(actor.idOrganizacion()),operacion,"FOTO-SALIDA:"+propiedad+":"+original+":"+hash,"png","image/png",bytes,cuota.admision(c));
        vigente(actor);cuota.confirmar(c,guardado);return guardado.clave();
    }
    public byte[] leerCopia(String clave,Actor actor){vigente(actor);byte[] bytes=leer(clave);vigente(actor);return bytes;}
    private byte[] leer(String clave){return almacen.abrir(clave).orElseThrow(()->new NoEncontradoException("Archivo")).contenido();}
    private void vigente(Actor actor){if(!actor.equals(sesiones.exigirVigente()))throw new AccesoNoAutorizadoException();}
}
