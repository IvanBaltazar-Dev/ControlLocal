package com.controllocal.rest.almacen;

import java.util.Optional;

/**
 * Contrato del almacen de documentos del expediente. Tiene dos implementaciones,
 * {@link AlmacenLocal} (disco) y {@link AlmacenS3} (bucket de objetos), y un selector
 * hibrido en {@link Almacenes} que elige una segun la configuracion y la conectividad.
 *
 * La "clave" es el identificador opaco del archivo dentro del almacen
 * ({@code carpeta/uuid_nombre.ext}); es lo que se persiste en {@code ruta_archivo}.
 */
public interface AlmacenDocumentos {

    ArchivoGuardado guardar(String carpeta, String nombreArchivo, byte[] contenido, String contentType);

    Optional<ArchivoDescargado> abrir(String clave);

    boolean eliminar(String clave);

    EstadoAlmacen verificarConexion();

    String proveedor();

    /** Resultado de subir un archivo: clave opaca, nombre saneado y tamano en bytes. */
    record ArchivoGuardado(String clave, String nombre, long tamano) {
    }

    /** Contenido descargado con su content-type derivado de la extension. */
    record ArchivoDescargado(byte[] contenido, String contentType, String nombre) {
    }

    /** Estado de conectividad del almacen, para el indicador de la pantalla de documentos. */
    record EstadoAlmacen(String proveedor, boolean conectado, String detalle) {
        public static EstadoAlmacen ok(String proveedor, String detalle) {
            return new EstadoAlmacen(proveedor, true, detalle);
        }

        public static EstadoAlmacen falla(String proveedor, String detalle) {
            return new EstadoAlmacen(proveedor, false, detalle);
        }
    }
}
