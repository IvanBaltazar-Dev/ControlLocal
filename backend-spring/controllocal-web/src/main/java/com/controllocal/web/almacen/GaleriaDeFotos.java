package com.controllocal.web.almacen;

import com.controllocal.service.Actor;
import com.controllocal.service.FotoPropiedadService;
import com.controllocal.service.FotoPropiedadService.FotoDePropiedad;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.web.http.RecursoNoEncontradoException;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.service.planes.ClasificadorArchivosExistentesB0;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

/** Valida el original una vez, enlaza con cuota Core y entrega evaluación local después del commit. */
@Component
public class GaleriaDeFotos {

    /** 5 MB. El mismo de la v1: no se relaja al abrir la puerta nueva. */
    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    private final FotoPropiedadService fotos;
    private final AlmacenDocumentos almacen;
    private final RevalidacionDeSesion sesiones;
    private final SalidaBinariaPersistente salidas;private final CuotaArchivosB0 cuota;
    private final ClasificadorArchivosExistentesB0 clasificador;private final EvaluacionTecnicaFoto evaluador;
    private final TransactionTemplate tx;

    public GaleriaDeFotos(FotoPropiedadService fotos, AlmacenDocumentos almacen, RevalidacionDeSesion sesiones,
            SalidaBinariaPersistente salidas,CuotaArchivosB0 cuota,ClasificadorArchivosExistentesB0 clasificador,
            EvaluacionTecnicaFoto evaluador,PlatformTransactionManager gestor) {
        this.fotos = fotos;
        this.almacen = almacen;
        this.sesiones = sesiones;
        this.salidas=salidas;this.cuota=cuota;this.clasificador=clasificador;this.evaluador=evaluador;this.tx=new TransactionTemplate(gestor);
    }

    /** El proveedor que sirve los binarios hoy: {@code DISCO} o {@code S3}. */
    public String proveedor() {
        return almacen.proveedor();
    }

    /** La galeria de una propiedad, con el proveedor ya declarado. */
    public List<FotoDePropiedad> listar(long idPropiedad, Actor actor) {
        String proveedor = almacen.proveedor();
        return fotos.listar(idPropiedad, actor).stream()
                .map(foto -> foto.servidaPor(proveedor))
                .toList();
    }

    /**
     * Valida la imagen, la guarda en el almacen y registra su fila.
     *
     * @param tope    el maximo de ESTA puerta
     */
    public FotoDePropiedad agregar(long idPropiedad, String nombreArchivo, String contenidoBase64,
                                   int tope, Actor actor) {
        exigirActorVigente(actor);
        byte[] contenido = imagenValidada(nombreArchivo, contenidoBase64);
        String contentType = contentTypeImagen(nombreArchivo);

        var foto=tx.execute(t->{
            var sid=ContextoDeActuacion.de(SesionActual.sesion());
            String finalidad=switch(tope){case FotoPropiedadService.TOPE_UNIVERSAL->"GALERIA_UNIVERSAL";case FotoPropiedadService.TOPE_DEL_LOCAL->"GALERIA_LOCAL";default->throw new ReglaNegocioException("La puerta de la galería no tiene un tope definido.");};
            var c=new CuotaArchivosB0.Contexto(clasificador.preparar(ClasificadorArchivosExistentesB0.FOTO,Long.toString(idPropiedad),finalidad,actor,sid),actor,sid);
            var guardado=salidas.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(actor.idOrganizacion()),java.util.UUID.randomUUID(),"FOTO-ORIGINAL:"+idPropiedad+":"+finalidad,
                    "image/png".equals(contentType)?"png":"jpg",contentType,contenido,cuota.admision(c));
            exigirActorVigente(actor);
            var registrada=fotos.agregar(idPropiedad,guardado.clave(),nombreArchivo,tope,actor)
                    .servidaPor(almacen.proveedor());
            cuota.confirmar(c,guardado);return registrada;
        });
        // La recompensa no forma parte del commit del original. Un fallo local no revierte el hecho.
        try{return foto.conUtilidad(new FotoPropiedadService.UtilidadFoto("EVALUADA",evaluador.evaluar(contenido).evaluacion(),List.of()));}
        catch(RuntimeException fallo){
            return foto.conUtilidad(new FotoPropiedadService.UtilidadFoto("NO_DISPONIBLE",null,List.of("El original quedó guardado. Puedes volver a solicitar su evaluación técnica.")));
        }
    }

    private void exigirActorVigente(Actor actor) {
        if (!actor.equals(sesiones.exigirVigente())) throw new AccesoNoAutorizadoException();
    }

    /** Retira de la galería activa y conserva el original privado y su antecedente. */
    public void eliminar(long idFoto, Actor actor) {
        if (!fotos.eliminar(idFoto, actor)) throw new RecursoNoEncontradoException("Foto");
    }

    /** El orden que decide la persona. No toca el almacen. */
    public List<FotoDePropiedad> reordenar(long idPropiedad, List<Long> idsEnOrden, Actor actor) {
        String proveedor = almacen.proveedor();
        return fotos.reordenar(idPropiedad, idsEnOrden, actor).stream()
                .map(foto -> foto.servidaPor(proveedor))
                .toList();
    }

    // ------------------------------------------------------------------

    /**
     * <b>Que se acepta como imagen</b>, en un solo sitio.
     *
     * <p>La extension dice el {@code content-type} y la <b>firma binaria</b>
     * dice la verdad: un ejecutable renombrado a {@code .png} pasa la primera y
     * no la segunda. Comprobar solo la extension es como se sube cualquier cosa
     * a un almacen que despues se sirve por URL.
     */
    private static byte[] imagenValidada(String nombreArchivo, String contenidoBase64) {
        if (nombreArchivo == null || nombreArchivo.isBlank()
                || contenidoBase64 == null || contenidoBase64.isBlank()) {
            throw new ReglaNegocioException("La foto es obligatoria.");
        }
        String contentType = contentTypeImagen(nombreArchivo);
        if (contentType == null) {
            throw new ReglaNegocioException("Solo se permiten imagenes PNG o JPG.");
        }
        byte[] contenido;
        try {
            contenido = Base64.getDecoder().decode(contenidoBase64);
        } catch (IllegalArgumentException error) {
            throw new ReglaNegocioException("El contenido de la imagen (base64) es invalido.");
        }
        if (contenido.length == 0) {
            throw new ReglaNegocioException("La imagen esta vacia.");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new ReglaNegocioException(
                    "La imagen supera el maximo de " + (TAMANO_MAXIMO / 1024 / 1024) + " MB.");
        }
        if (!firmaImagenValida(contenido, contentType)) {
            throw new ReglaNegocioException("El archivo no es una imagen PNG o JPG valida.");
        }
        return contenido;
    }

    private static String contentTypeImagen(String nombre) {
        String n = nombre == null ? "" : nombre.toLowerCase();
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return null;
    }

    /** Firma binaria: PNG = 89 50 4E 47 ; JPG = FF D8 FF. */
    private static boolean firmaImagenValida(byte[] c, String contentType) {
        if (c == null || c.length < 4) {
            return false;
        }
        if ("image/png".equals(contentType)) {
            return (c[0] & 0xFF) == 0x89 && (c[1] & 0xFF) == 0x50
                    && (c[2] & 0xFF) == 0x4E && (c[3] & 0xFF) == 0x47;
        }
        if ("image/jpeg".equals(contentType)) {
            return (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
        }
        return false;
    }
}
