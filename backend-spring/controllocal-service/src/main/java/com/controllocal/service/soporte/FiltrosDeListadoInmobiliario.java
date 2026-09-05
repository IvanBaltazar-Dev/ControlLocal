package com.controllocal.service.soporte;

import com.controllocal.persistence.busqueda.CriterioBusquedaInmobiliaria;
import com.controllocal.persistence.busqueda.OrdenDelListado;
import com.controllocal.service.excepcion.ReglaNegocioException;

import java.util.Locale;
import java.util.Set;

/**
 * <b>La UNICA autoridad sobre lo que un listado inmobiliario acepta como
 * filtro.</b>
 *
 * <p>Los dos listados de la cartera -{@code GET /locales} y
 * {@code GET /propiedades}- comparten tenant, texto libre, estado legado y
 * paginacion. Hasta el 2026-09-02 cada uno los normalizaba por su cuenta, y por
 * eso los dos tenian el mismo agujero: {@code estado=ACTIVO} -o cualquier
 * palabra- no era un error, era una pagina vacia. Un filtro que el servidor no
 * entiende y contesta 200 le dice al cliente "no hay nada que enseñar" cuando
 * lo que pasa es que la pregunta estaba mal escrita.
 *
 * <p>Arreglarlo en un solo recurso habria dejado la asimetria al reves. La regla
 * es transversal, asi que vive una vez y la usan los dos.
 *
 * <h2>Que valida y que no</h2>
 * Valida lo <b>comun</b>: el vocabulario del estado y el acotado de la
 * paginacion. No valida lo especifico de cada recurso -el tipo de propiedad y
 * las operaciones son del universal, y los valida su servicio con el
 * vocabulario del dominio-, porque no son filtros compartidos.
 */
public final class FiltrosDeListadoInmobiliario {

    /**
     * El vocabulario legado del estado. <b>No es una columna</b>: se deriva de
     * {@code estado_registro} y {@code disponibilidad_comercial}, y por eso no
     * hay ningun CHECK de la base que lo defienda. Lo defiende esto.
     */
    private static final Set<String> ESTADOS = Set.of("D", "N", "I");

    private FiltrosDeListadoInmobiliario() {
    }

    /**
     * El criterio de {@code GET /locales}: sin filtros de inmueble y con el
     * orden ascendente que publica su contrato.
     *
     * <p><b>El texto busca lo mismo que en el universal</b>, rubro incluido: las
     * tres ramas son del motor y ninguna pertenece a un recurso. Lo unico que
     * distingue a este criterio es lo que <b>no</b> lleva -tipo, distrito y
     * operaciones no existen en este contrato- y el sentido del orden.
     */
    public static CriterioBusquedaInmobiliaria deLocales(long idOrganizacion, String texto,
                                                         String estado, int pagina, int tamano,
                                                         int maximoPorPagina) {
        return new CriterioBusquedaInmobiliaria(idOrganizacion, textoNormalizado(texto),
                estadoValidado(estado), null, null, false, false,
                OrdenDelListado.ASCENDENTE, paginaAcotada(pagina), tamanoAcotado(tamano, maximoPorPagina));
    }

    /**
     * El criterio del listado universal: los filtros propios del modelo
     * universal y el orden descendente que publica su contrato.
     *
     * <p><b>El rubro entra tambien aqui</b> (C0-a, 2026-09-02). Desde V71
     * {@code rubro_permitido} es un atributo gobernado de PROPIEDAD, no un campo
     * del local: dejarlo fuera del listado universal hacia invisible por su
     * rubro a un almacen o a una oficina en el unico listado que el producto
     * usa. Que a CASA, DEPARTAMENTO, TERRENO y OTRO no les aplique no es motivo
     * para quitar la rama -esas propiedades no producen candidatos por ella-,
     * igual que nadie quita la rama del propietario porque haya propiedades sin
     * representante.
     *
     * @param tipoPropiedad codigo de una letra YA validado por el vocabulario
     *        del dominio, o {@code null}.
     */
    public static CriterioBusquedaInmobiliaria dePropiedades(long idOrganizacion, String texto,
                                                             String estado, String tipoPropiedad,
                                                             String distrito, boolean conVenta,
                                                             boolean conAlquiler, int pagina,
                                                             int tamano, int maximoPorPagina) {
        return new CriterioBusquedaInmobiliaria(idOrganizacion, textoNormalizado(texto),
                estadoValidado(estado), tipoPropiedad, textoNormalizado(distrito),
                conVenta, conAlquiler, OrdenDelListado.DESCENDENTE,
                paginaAcotada(pagina), tamanoAcotado(tamano, maximoPorPagina));
    }

    /**
     * El estado, o el 400 que dice que esa palabra no existe.
     *
     * <p>El mensaje nombra el vocabulario entero a proposito: quien se equivoca
     * escribiendo un filtro necesita saber cuales hay, no que lo que escribio
     * estaba mal.
     */
    public static String estadoValidado(String estado) {
        String limpio = textoNormalizado(estado);
        if (limpio == null) {
            return null;
        }
        String codigo = limpio.toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(codigo)) {
            throw new ReglaNegocioException(
                    "Estado de listado desconocido: \"" + estado + "\". Son tres: D (disponible), "
                            + "N (no disponible) e I (inactiva).");
        }
        return codigo;
    }

    /** En blanco es "sin filtro", no "filtro vacio". */
    public static String textoNormalizado(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /**
     * La pagina y el tamano se ACOTAN, no se rechazan.
     *
     * <p>Es lo que los dos recursos llevan haciendo desde que existen -pedir la
     * pagina 0 devuelve la primera, pedir 1.000 filas devuelve el maximo- y no
     * se cambia aqui: un 400 nuevo en una llamada que hoy responde 200 romperia
     * clientes por un numero fuera de rango, que es distinto de una palabra que
     * no existe.
     */
    public static int paginaAcotada(int pagina) {
        return Math.max(1, pagina);
    }

    public static int tamanoAcotado(int tamano, int maximoPorPagina) {
        return Math.max(1, Math.min(maximoPorPagina, tamano));
    }
}
