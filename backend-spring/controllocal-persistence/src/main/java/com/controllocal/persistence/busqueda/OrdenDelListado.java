package com.controllocal.persistence.busqueda;

/**
 * El sentido del orden por clave primaria de un listado inmobiliario.
 *
 * <p>Es una <b>configuracion del recurso</b>, no una estrategia distinta:
 * {@code /locales} nacio ordenando ascendente y {@code /propiedades} descendente
 * -lo ultimo captado primero-, y los dos ordenan por {@code id_propiedad}, que
 * es unica. El orden es total y estable en ambos casos, asi que ninguna fila se
 * cuela o se pierde entre paginas.
 *
 * <p>Vive aqui, y no como un {@code boolean descendente}, porque el sentido del
 * orden es parte del contrato publicado de cada recurso y un booleano suelto en
 * la firma se lee mal en el sitio de la llamada.
 */
public enum OrdenDelListado {

    /** Del id mas bajo al mas alto. El de {@code GET /locales}. */
    ASCENDENTE("asc"),

    /** Del id mas alto al mas bajo: lo ultimo registrado primero. El de {@code GET /propiedades}. */
    DESCENDENTE("desc");

    private final String sql;

    OrdenDelListado(String sql) {
        this.sql = sql;
    }

    /**
     * El literal que va detras del {@code order by}.
     *
     * <p>Es un valor de este enum y nunca texto del llamante: la clausula se
     * concatena en el SQL -no puede ir como parametro- y por eso lo unico que
     * puede llegar ahi son estas dos constantes.
     */
    public String sql() {
        return sql;
    }
}
