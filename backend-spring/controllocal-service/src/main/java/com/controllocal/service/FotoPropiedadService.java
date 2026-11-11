package com.controllocal.service;

import java.util.List;

/**
 * <b>La galeria de una PROPIEDAD: un solo productor para las dos puertas</b>
 * (WP-C).
 *
 * <h2>Que problema cierra</h2>
 * Las fotos existian desde la v1 y solo se podian subir por
 * {@code /locales/{id}/fotos}. La ficha universal —que es la que el SPA usa
 * desde E4— no las publicaba, asi que una propiedad registrada por el modelo
 * nuevo no tenia forma de tener imagenes, y la unica puerta para ponerselas
 * hablaba de «locales» sobre un modelo que ya no distingue el tipo.
 *
 * <p>La respuesta NO es una segunda implementacion colgada de
 * {@code /propiedades}. Dos codigos que escriben {@code foto_propiedad} son dos
 * topes, dos validaciones y dos autoridades, y la que se relaje primero es la
 * puerta por la que se entra. Asi que hay <b>un</b> servicio, y las dos rutas
 * entran por el.
 *
 * <h2>Los dos topes, que son distintos a proposito</h2>
 * <pre>
 *   POST /propiedades/{id}/fotos   ->  12
 *   POST /locales/{id}/fotos       ->   6   (el de la v1, conservado)
 * </pre>
 * No es una incoherencia disimulada: es la puerta heredada cumpliendo lo que
 * prometia. La consecuencia hay que decirla en vez de descubrirla: una
 * propiedad con 7 fotos subidas por la ruta universal <b>rechaza</b> la
 * septima por {@code /locales}, con el mensaje del tope de esa puerta. El tope
 * viaja como parametro y no como constante interna precisamente para que se
 * vea que hay dos y de donde sale cada uno.
 *
 * <h2>La autoridad es la de la ficha, no la de la foto</h2>
 * Escribir una foto es escribir la ficha del inmueble (P0-1), asi que decide
 * {@code AutoridadDePropiedad.exigirEdicion} sobre la propiedad <b>tomada</b>
 * (F2.10). Llegar por el id de una foto no puede ser una puerta mas barata que
 * llegar por el del inmueble: por eso {@link #eliminar} resuelve la propiedad y
 * le pregunta a ella.
 *
 * <h2>Y el binario no pasa por aqui</h2>
 * Este servicio guarda <b>la clave</b> que el almacen devolvio. El almacen vive
 * en la capa web ({@code AlmacenDocumentos}) porque es una frontera de
 * infraestructura, y las capas van {@code web -> service}: el servicio no puede
 * —ni debe— saber donde estan los bytes. Quien valida el base64, los magic
 * bytes y el tamano es la capa que recibe el archivo, y lo hace <b>una vez</b>
 * para las dos rutas.
 */
public interface FotoPropiedadService {

    /** El tope de la galeria universal. */
    int TOPE_UNIVERSAL = 12;

    /**
     * El tope que prometia {@code /locales/{id}/fotos} en la v1, conservado.
     * Se conserva porque el contrato de esa puerta no cambia en este corte, no
     * porque seis sea un numero mejor que doce.
     */
    int TOPE_DEL_LOCAL = 6;

    /**
     * Una foto de la galeria. {@code orden} empieza en <b>1</b>, y la foto con
     * orden 1 es la <b>portada</b> (V91).
     *
     * <p>El campo {@code clave} contiene la referencia UUID vigente del vínculo,
     * nunca la clave privada del proveedor. El binario se solicita autenticado
     * en {@code GET /documentos/contenido?clave=}, que revalida recurso y finalidad. Montar la URL aqui
     * obligaria al Core a saber por donde se le llega desde fuera —dominio,
     * prefijo, puerto—, que es configuracion del despliegue y no un hecho de la
     * propiedad.
     *
     * @param proveedor {@code DISCO} o {@code S3}, o {@code null} mientras la
     *                  foto no ha pasado por la capa que lo sabe. Lo rellena
     *                  {@link #servidaPor}
     */
    record UtilidadFoto(String estado,com.controllocal.service.documentos.EvaluacionTecnicaFoto.Evaluacion evaluacion,List<String> avisos){
        public UtilidadFoto{avisos=List.copyOf(avisos);}
    }
    record FotoDePropiedad(Long id, String clave, String nombreArchivo, int orden,
                           String proveedor,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) UtilidadFoto utilidad) {

        public FotoDePropiedad(Long id, String clave, String nombreArchivo, int orden) {
            this(id, clave, nombreArchivo, orden, null,null);
        }
        public FotoDePropiedad(Long id,String clave,String nombreArchivo,int orden,String proveedor){this(id,clave,nombreArchivo,orden,proveedor,null);}

        /** La misma foto declarando por que proveedor se sirve hoy. */
        public FotoDePropiedad servidaPor(String proveedor) {
            return new FotoDePropiedad(id, clave, nombreArchivo, orden, proveedor,utilidad);
        }
        public FotoDePropiedad conUtilidad(UtilidadFoto utilidad){return new FotoDePropiedad(id,clave,nombreArchivo,orden,proveedor,utilidad);}
    }

    /**
     * La galeria, en su orden, sólo dentro de la relación de lectura física
     * autorizada por Core. Fuera del alcance responde 404; administrar no abre
     * la cartera. Una galería autorizada sin fotos sí devuelve una lista vacía.
     */
    List<FotoDePropiedad> listar(long idPropiedad, Actor actor);

    /** Consulta interna C1 para proyecciones: una denegación esperada no es un fallo técnico. */
    boolean originalPermitidoParaLectura(long idPropiedad, java.util.UUID original, Actor actor);

    /** Misma autoridad/tope que el alta, bajo bloqueo, antes de escribir un binario. */
    String prepararCarga(long idPropiedad,int tope,Actor actor);

    /**
     * Registra la clave de una foto ya guardada en el almacen, al final de la
     * galeria.
     *
     * @param tope el maximo de esta puerta ({@link #TOPE_UNIVERSAL} o
     *             {@link #TOPE_DEL_LOCAL})
     */
    FotoDePropiedad agregar(long idPropiedad, String clave, String nombreArchivo,
                            int tope, Actor actor);

    /**
     * Retira una foto de la galería activa, conservando fila, referencia,
     * original y transición auditada. Devuelve false si no existe o ya está retirada.
     *
     * <p>Recoloca las siguientes: una galeria con un hueco en el orden no
     * tendria portada definida el dia que se borre la primera.
     */
    boolean eliminar(long idFoto, Actor actor);

    /**
     * <b>El orden que decide la persona.</b> Recibe los ids de la galeria
     * completa, en el orden deseado, y devuelve la galeria ya ordenada.
     *
     * <p>Exige <b>exactamente</b> el mismo conjunto de ids que tiene la
     * propiedad: ni uno de menos ni uno de mas ni repetidos. Aceptar un
     * subconjunto obligaria a inventar donde van los que faltan, y esa
     * invencion es la que deja una galeria con dos fotos en la posicion 3.
     */
    List<FotoDePropiedad> reordenar(long idPropiedad, List<Long> idsEnOrden, Actor actor);
}
