package com.controllocal.web.almacen;

import java.util.Optional;
import java.util.UUID;

/**
 * Almacen de binarios (fotos, documentos del expediente). La clave es un
 * identificador opaco no adivinable (tipo capability): quien la tiene puede
 * leer el archivo por GET /documentos/contenido.
 *
 * <p><b>Esta interfaz es la unica frontera del almacenamiento</b>, y se
 * conserva a proposito: hoy la implementa {@link AlmacenDisco} y manana la
 * implementara la variante S3 (Bloque 8, antes de E5). Nada fuera de este
 * paquete puede saber donde viven los bytes.
 *
 * <p><b>Preparacion para S3 ya aplicada</b>: las claves nuevas se prefijan con
 * la organizacion via {@link #carpetaDeTenant}. Sirve igual en disco (un
 * subdirectorio por tenant) que en un bucket privado (un prefijo por tenant,
 * que es donde se apoyan las politicas de aislamiento). Se hace ahora, y no al
 * migrar, porque cambiar el formato de clave <b>despues</b> de mover los
 * binarios obligaria a moverlos dos veces.
 */
public interface AlmacenDocumentos {

    /** Raiz de todas las claves nuevas. Un solo sitio donde vive el formato. */
    String PREFIJO_TENANT = "tenant/";

    /**
     * Carpeta de una organizacion: {@code tenant/{organizacionId}/{carpeta}}.
     *
     * <p>Las claves <b>anteriores</b> a esta preparacion no llevan prefijo y
     * <b>se siguen leyendo tal cual</b>: la clave guardada en PostgreSQL es la
     * fuente de verdad y {@link #abrir(String)} la resuelve como venga. No hay
     * migracion de binarios en este bloque, a proposito — se hara una sola vez,
     * junto con el paso a S3.
     */
    static String carpetaDeTenant(long idOrganizacion, String carpeta) {
        return PREFIJO_TENANT + idOrganizacion + "/" + carpeta;
    }

    /**
     * Clave nueva: {@code carpeta/uuid8-nombre_saneado}.
     *
     * <p>Vive aqui, y no en cada implementacion, porque <b>las dos tienen que
     * generar exactamente la misma forma</b>. Si el disco y S3 construyeran
     * claves distintas, la migracion de binarios dejaria de ser una copia y
     * pasaria a ser una traduccion — con su tabla de equivalencias y su
     * oportunidad de perder archivos.
     */
    static String claveNueva(String carpeta, String nombreArchivo) {
        String saneado = (nombreArchivo == null ? "" : nombreArchivo)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        return carpeta + "/" + UUID.randomUUID().toString().substring(0, 8) + "-" + saneado;
    }

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

    record ArchivoGuardado(String clave, String nombre) {
    }

    record ArchivoDescargado(byte[] contenido, String nombre, String contentType) {
    }

    /** Identificador del backend de almacenamiento (informativo en el cable): DISCO | S3. */
    String proveedor();

    ArchivoGuardado guardar(String carpeta, String nombreArchivo, byte[] contenido, String contentType);

    /**
     * Escribe en una clave <b>exacta</b>, sobrescribiendo si ya existe.
     *
     * <p>Existe para <b>migrar binarios entre proveedores</b> y no tiene otro
     * uso legitimo: {@link #guardar} acuña una clave nueva cada vez, que es lo
     * correcto para un archivo nuevo y justo lo que impide copiar uno
     * existente — la clave ya esta guardada en PostgreSQL y es la fuente de
     * verdad, asi que cambiarla al mover los bytes romperia cada fila que la
     * referencia.
     *
     * <p><b>No lo llame desde un caso de uso.</b> Aceptar una clave de fuera
     * es aceptar que quien la elige decida donde cae el archivo; para eso
     * estan las carpetas de {@link #carpetaDeTenant}.
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
