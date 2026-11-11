package com.controllocal.web.almacen;

import java.util.Optional;

/**
 * Almacen de binarios (fotos, documentos del expediente). La clave es un
 * identificador técnico privado del proveedor. No es una capability ni se
 * entrega al cliente: GET /documentos/contenido usa una referencia Core y
 * comprueba de nuevo sesión, recurso y finalidad antes de entregar bytes.
 *
 * <p><b>Esta interfaz es la unica frontera del almacenamiento</b>; la
 * implementan {@link AlmacenDisco} y {@link AlmacenS3}. Nada fuera de este
 * paquete puede saber donde viven los bytes.
 *
 * <p>Core preasigna la clave privada con organizacion e intento durable antes
 * de escribir. La salida comercial usa {@link SalidaBinariaPersistente} con
 * admision B0; el proveedor no acuna otra clave ni decide permisos o cuotas.
 * Las referencias existentes se conservan al copiar entre proveedores.
 */
public interface AlmacenDocumentos {

    /**
     * Content-type con el que se <b>sirve</b> un binario, deducido de la clave.
     *
     * <p><b>No usa {@link NombresArchivo#contentType}, y la diferencia importa.</b>
     * Aquel mapea ademas {@code .csv} a {@code text/csv} y lo consume
     * {@code SolicitudesController} al validar subidas; este es el que acaba en
     * la cabecera de {@code GET /documentos/contenido}. Unificarlos cambiaria
     * el tipo servido de los CSV ya almacenados, que es justo lo que un cambio
     * de almacenamiento no puede hacer. Se unifican al descongelar el contrato.
     *
     * <p>Que las dos implementaciones lo compartan es el requisito de verdad:
     * el mismo archivo debe servirse igual venga del disco o del bucket, o la
     * migracion seria observable desde el navegador.
     */
    static String contentTypeDe(String clave) {
        String c = clave == null ? "" : clave.toLowerCase();
        if (c.endsWith(".png")) {
            return "image/png";
        }
        if (c.endsWith(".jpg") || c.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (c.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    /** Ultimo segmento de la clave: el nombre con el que se descarga. */
    static String nombreDe(String clave) {
        if (clave == null || clave.isBlank()) {
            return "";
        }
        int barra = clave.lastIndexOf('/');
        return barra >= 0 ? clave.substring(barra + 1) : clave;
    }

    record ArchivoDescargado(byte[] contenido, String nombre, String contentType) {
    }

    /** Identificador del backend de almacenamiento (informativo en el cable): DISCO | S3. */
    String proveedor();

    /** Huella privada y estable del namespace real; no credencial, ruta pública ni permiso. */
    String identidadTecnica();

    static String huellaIdentidad(String proveedor,String namespace) {
        if(proveedor==null||proveedor.isBlank()||namespace==null||namespace.isBlank())
            throw new AlmacenException("No se pudo identificar el almacén privado.",null);
        try {
            var bytes=("BROX-ALMACEN\u0000"+proveedor+"\u0000"+namespace).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch(java.security.NoSuchAlgorithmException imposible) {
            throw new AlmacenException("No se pudo identificar el almacén privado.",null);
        }
    }

    /**
     * Escribe en una clave <b>exacta</b>, sobrescribiendo si ya existe.
     *
     * <p>Uso técnico para migrar binarios o para la salida durable RF080 cuyo
     * intento ya ha preasignado una clave Core. La clave conservada en
     * PostgreSQL no se traduce al mover bytes entre proveedores.
     *
     * <p><b>No lo llame directamente desde un caso de uso.</b> Las escrituras
     * comerciales pasan por la salida durable y su admision B0. Nunca se
     * admite una ruta elegida por el cliente como clave de almacenamiento.
     *
     * <p>Sobrescribir es deliberado: hace la migracion <b>idempotente</b>.
     * Repetirla tras un corte de red reescribe los mismos bytes en la misma
     * clave en vez de duplicar objetos con nombres nuevos.
     */
    void guardarEnClave(String clave, byte[] contenido, String contentType);

    Optional<ArchivoDescargado> abrir(String clave);

    /** Borrado best-effort: no falla si el binario ya no existe. */
    void eliminar(String clave);

    /**
     * Todas las claves que este almacen contiene.
     *
     * <p>Existe para la <b>conciliacion clave&#8596;objeto</b>: cruzar lo que
     * PostgreSQL referencia con lo que el almacen guarda descubre las dos
     * averias que nadie ve de otro modo — una fila que apunta a un binario que
     * ya no esta (el usuario ve un hueco) y un binario que ya no referencia
     * nadie (ocupa y, si contiene datos personales, no deberia seguir ahi).
     *
     * <p>Vive en la frontera y no en la herramienta porque listar exige saber
     * <b>donde</b> estan los bytes, que es justo lo que esta interfaz existe
     * para no contarle a nadie.
     *
     * <p><b>Es una operacion cara y de mantenimiento</b>: recorre el arbol
     * entero o pagina el bucket completo. No la llame desde un caso de uso.
     */
    java.util.Set<String> listarClaves();
}
