package com.controllocal.persistence.busqueda;

/**
 * <b>Que se busca sobre la cartera de inmuebles, para cualquiera de sus dos
 * lecturas.</b>
 *
 * <p>Es la configuracion del {@link MotorBusquedaInmobiliaria}: lo que separa a
 * {@code GET /locales} de {@code GET /propiedades} son los valores de este
 * registro, no dos consultas distintas. Lo comun -tenant, texto libre, estado
 * legado, paginacion, conteo y orden por clave- lo resuelve el motor una sola
 * vez.
 *
 * <h2>Lo que NO valida</h2>
 * Este registro <b>no</b> comprueba vocabularios ni lanza excepciones de
 * negocio: vive en la capa de persistencia, que no puede ver
 * {@code ReglaNegocioException}. La validacion comun -y el 400 que produce- es
 * de {@code service/soporte/FiltrosDeListadoInmobiliario}, que es quien
 * construye estos criterios para los dos recursos. Aqui los valores llegan ya
 * normalizados: en blanco es {@code null}, y la pagina y el tamano ya vienen
 * acotados.
 *
 * @param idOrganizacion el tenant. Sale del actor, nunca de un parametro de la
 *        peticion, y viaja en TODAS las ramas de la busqueda.
 * @param texto          texto libre ya recortado, o {@code null} si no hay
 *        filtro. Es lo que decide si el motor usa el conjunto de candidatos.
 * @param estado         codigo legado {@code D}/{@code N}/{@code I}, o
 *        {@code null}. No es una columna: se deriva de {@code estado_registro} y
 *        {@code disponibilidad_comercial}.
 * @param tipoPropiedad  codigo de una letra ({@code L}/{@code O}/{@code D}/
 *        {@code C}/{@code T}/{@code A}/{@code X}), o {@code null}. Solo lo usa
 *        el listado universal: {@code /locales} no publica el tipo en su fila.
 * @param distrito       distrito exacto, sin distinguir mayusculas, o
 *        {@code null}. Solo el universal.
 * @param conVenta       exige un encargo de VENTA <b>vivo</b>. Solo el universal.
 * @param conAlquiler    exige un encargo de ALQUILER <b>vivo</b>. Solo el
 *        universal. Con los dos en {@code true} la propiedad tiene que tener
 *        las DOS: no es "alguna de las dos".
 * @param orden          sentido del orden por {@code id_propiedad}.
 * @param pagina         1-based, ya acotada a un minimo de 1.
 * @param tamano         filas por pagina, ya acotado al maximo del recurso.
 */
public record CriterioBusquedaInmobiliaria(
        long idOrganizacion,
        String texto,
        String estado,
        String tipoPropiedad,
        String distrito,
        boolean conVenta,
        boolean conAlquiler,
        OrdenDelListado orden,
        int pagina,
        int tamano) {

    public CriterioBusquedaInmobiliaria {
        if (orden == null) {
            throw new IllegalArgumentException("El criterio de busqueda necesita un orden.");
        }
        if (pagina < 1 || tamano < 1) {
            throw new IllegalArgumentException(
                    "El criterio de busqueda llega sin acotar: pagina=" + pagina + " tamano=" + tamano
                            + ". Acotar es responsabilidad de quien construye el criterio.");
        }
    }

    /** Si hay texto libre, y por tanto si hace falta el conjunto de candidatos. */
    public boolean tieneTexto() {
        return texto != null && !texto.isBlank();
    }

    /**
     * Si el criterio lleva filtros que <b>no</b> son ni el tenant ni el estado
     * ni el texto.
     *
     * <p>Importa para el plan: sin ellos la consulta del conjunto de candidatos
     * queda EXACTAMENTE con la forma que {@code /locales} lleva midiendo desde
     * RC-003 -sin volver a unir contra {@code propiedad}-, y esa forma no se
     * toca por el hecho de que otro recurso necesite mas filtros.
     */
    public boolean tieneFiltrosDeInmueble() {
        return tipoPropiedad != null || distrito != null || conVenta || conAlquiler;
    }

    /** Filas a saltar. La paginacion se resuelve en la base, nunca en memoria. */
    public int desplazamiento() {
        return (pagina - 1) * tamano;
    }
}
