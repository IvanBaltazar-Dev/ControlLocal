package com.controllocal.web.almacen;

import com.controllocal.service.Actor;
import com.controllocal.service.documentos.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.service.planes.ClasificadorArchivosExistentesB0;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ArchivosPiezaPrivados implements ArchivosPieza {
    private final AlmacenDocumentos almacen;private final RevalidacionDeSesion sesiones;private final SalidaBinariaPersistente salidas;
    private final CuotaArchivosB0 cuota;private final ClasificadorArchivosExistentesB0 clasificador;
    private final com.controllocal.service.materialia.ResultadosMaterialIa resultados;
    public ArchivosPiezaPrivados(AlmacenDocumentos almacen,RevalidacionDeSesion sesiones,SalidaBinariaPersistente salidas,CuotaArchivosB0 cuota,ClasificadorArchivosExistentesB0 clasificador,
            com.controllocal.service.materialia.ResultadosMaterialIa resultados){this.almacen=almacen;this.sesiones=sesiones;this.salidas=salidas;this.cuota=cuota;this.clasificador=clasificador;this.resultados=resultados;}
    public java.util.Optional<Guardado> recuperar(UUID pieza,String finalidad,String huellaOrigen,Actor actor){
        vigente(actor);var c=contexto(pieza,finalidad,actor);var r=salidas.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(actor.idOrganizacion()),operacion(pieza,finalidad,actor),fuente(huellaOrigen,finalidad),cuota.admision(c));vigente(actor);
        if(r.estado()==SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION)
            throw new ConflictoException("El archivo sigue en verificación; no se repetirá su escritura a ciegas.");
        if(r.guardado()==null)return java.util.Optional.empty();
        cuota.confirmar(c,r.guardado());return java.util.Optional.of(new Guardado(r.guardado().clave(),r.guardado().huella()));
    }
    public Guardado guardar(UUID pieza,CompositorPieza.Binario binario,String finalidad,String huellaOrigen,Actor actor){
        vigente(actor);
        var c=contexto(pieza,finalidad,actor);
        var g=salidas.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(actor.idOrganizacion()),operacion(pieza,finalidad,actor),fuente(huellaOrigen,finalidad),
                binario.extension(),binario.contentType(),binario.contenido(),cuota.admision(c));vigente(actor);cuota.confirmar(c,g);
        return new Guardado(g.clave(),g.huella());
    }
    public byte[] abrir(String clave,Actor actor){
        vigente(actor);var b=almacen.abrir(clave).orElseThrow(()->new NoEncontradoException("Pieza"));
        vigente(actor);return b.contenido();
    }
    public byte[] fotoDerivada(UUID operacion,UUID archivo,Actor actor){
        vigente(actor);
        var contenido=resultados.leerParaPieza(operacion,archivo,actor,ContextoDeActuacion.de(SesionActual.sesion()));
        vigente(actor);return contenido.bytes();
    }
    private void vigente(Actor actor){if(!actor.equals(sesiones.exigirVigente()))throw new AccesoNoAutorizadoException();}
    private CuotaArchivosB0.Contexto contexto(UUID pieza,String finalidad,Actor actor){
        var sid=ContextoDeActuacion.de(SesionActual.sesion());return new CuotaArchivosB0.Contexto(clasificador.preparar(ClasificadorArchivosExistentesB0.PIEZA,pieza.toString(),finalidad,actor,sid),actor,sid);
    }
    private static UUID operacion(UUID pieza,String finalidad,Actor actor){return UUID.nameUUIDFromBytes(("PIEZA-SALIDA-1\u0000"+actor.idOrganizacion()+"\u0000"+pieza+"\u0000"+finalidad).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String fuente(String huella,String finalidad){return "PIEZA-V08:"+finalidad+":"+huella;}
}
